package io.laserdisc.slack4s.slashcmd.internal

import cats.effect.Async
import cats.effect.std.Queue
import cats.implicits.*
import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import fs2.Stream
import io.circe.syntax.*
import io.laserdisc.slack4s.slack.*
import io.laserdisc.slack4s.slack.canned.*
import io.laserdisc.slack4s.slack.internal.*
import io.laserdisc.slack4s.slashcmd.*
import org.http4s.FormDataDecoder.formEntityDecoder
import org.http4s.*
import org.typelevel.log4cats.slf4j.Slf4jLogger

object CommandRunner {

  def apply[F[_]: Async](
      slack: SlackAPIClient[F],
      mapper: CommandMapper[F]
  ): F[CommandRunner[F]] =
    Queue
      .unbounded[F, (SlashCommandPayload, Command[F])]
      .map(new CommandRunnerImpl[F](slack, mapper, _))
}

sealed trait CommandRunner[F[_]] {
  def processRequest(ar: AuthedRequest[F, SlackUser]): F[Response[F]]
  def processBGCommandQueue: fs2.Stream[F, Unit]
}

class CommandRunnerImpl[F[_]: Async](
    slack: SlackAPIClient[F],
    mapper: CommandMapper[F],
    queue: Queue[F, (SlashCommandPayload, Command[F])]
) extends CommandRunner[F] {

  private val logger = Slf4jLogger.getLogger

  // log but otherwise ignore failures to send messages to slack
  private def respond(
      payload: SlashCommandPayload,
      command: Command[F],
      response: ChatPostMessageRequest
  ): F[Unit] =
    slack
      .respond(payload.getResponseUrl, response)
      .handleErrorWith { err =>
        logger.error(err)(
          s"SLACK-RESPOND-FAIL command:${command.logId} payload:'${payload.getText}' error:$err triedToSend:${response.asJson.noSpaces}"
        ) >>
          respondSomethingWentWrong(payload)
      }

  private def respondSomethingWentWrong(payload: SlashCommandPayload): F[Unit] = {

    val response = somethingWentWrong(payload)

    slack.respond(payload.getResponseUrl, response).handleErrorWith { e =>
      // if sending the 'somethingWentWrong' message failed, at this point we just can only log
      logger.error(e)(s"Failed to send 'somethingWentWrong' message: ${response.asJson.noSpaces}")
    }
  }

  override def processBGCommandQueue: fs2.Stream[F, Unit] =
    Stream
      .fromQueueUnterminated(queue)
      .evalMap { case (payload, cmd) =>
        cmd.handler.attempt.flatMap {
          case Left(e) =>
            logger.error(e)(
              s"CMD-BG-FAIL cmdId:${cmd.logId} reqId=${payload.requestId} e:${e.getMessage}"
            ) >> respondSomethingWentWrong(payload)

          case Right(res) =>
            logger.info(
              s"CMD-BG-SUCCESS cmdId:${cmd.logId} reqId=${payload.requestId} message:${res.getBlocks}"
            ) >> respond(payload, cmd, res)
        }
      }

  def execute(payload: SlashCommandPayload, cmd: Command[F]): F[Response[F]] =
    cmd.responseType match {
      case Immediate =>
        cmd.handler.map(Response(Status.Ok).withEntity(_))

      case Delayed =>
        queue
          .offer((payload, cmd))
          .as(Response(Status.Ok))

      case DelayedWithMsg(msg) =>
        queue
          .offer((payload, cmd))
          .as(Response(Status.Ok).withEntity(msg))

    }

  override def processRequest(ar: AuthedRequest[F, SlackUser]): F[Response[F]] =
    ar.req.decode[SlashCommandPayload] { payload =>
      for {
        _   <- logger.info(s"PARSE-CMD cmdId=${payload.requestId}  payload:'$payload'")
        cmd <- mapper(payload)
        _ <- logger.info(
          s"COMMAND-SELECT cmdId:${cmd.logId} reqId=${payload.requestId} user:${payload.getUserName}(${payload.getUserId}) text:'${payload.getText}' responseType:${cmd.responseType}"
        )
        res <- execute(payload, cmd)
      } yield res
    }
}
