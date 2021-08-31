package io.laserdisc.slack4s.slashcmd.internal

import cats.effect.Concurrent
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import fs2.concurrent.Queue
import io.circe.syntax._
import io.laserdisc.slack4s.slack._
import io.laserdisc.slack4s.slack.canned._
import io.laserdisc.slack4s.slack.internal._
import io.laserdisc.slack4s.slashcmd._
import org.http4s._
import org.typelevel.log4cats.slf4j.Slf4jLogger

object CommandRunner {

  def apply[F[_]: Concurrent](
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

class CommandRunnerImpl[F[_]: Concurrent](
  slack: SlackAPIClient[F],
  mapper: CommandMapper[F],
  queue: Queue[F, (SlashCommandPayload, Command[F])]
) extends CommandRunner[F] {

  private[this] val logger = Slf4jLogger.getLogger

  // log but otherwise ignore failures to send messages to slack
  private[this] def respond(
    payload: SlashCommandPayload,
    command: Command[F],
    response: ChatPostMessageRequest
  ): F[Unit] =
    slack
      .respond(payload.getResponseUrl, response)
      .handleErrorWith { err =>
        logger.error(err)(
          s"SLACK-RESPOND-FAIL command:${command.logToken} payload:'${payload.getText}' error:$err triedToSend:${response.asJson.noSpaces}"
        ) >>
          respondSomethingWentWrong(payload)
      }

  private[this] def respondSomethingWentWrong(payload: SlashCommandPayload): F[Unit] = {

    val response = somethingWentWrong(payload)

    slack.respond(payload.getResponseUrl, response).handleErrorWith { e =>
      // if sending the 'somethingWentWrong' message failed, at this point we just can only log
      logger.error(e)(s"Failed to send 'somethingWentWrong' message: ${response.asJson.noSpaces}")
    }
  }

  override def processBGCommandQueue: fs2.Stream[F, Unit] =
    queue.dequeue
      .evalMap {
        case (payload, cmd) =>
          cmd.handler.attempt.flatMap {
            case Left(e) =>
              logger.error(e)(
                s"CMD-BG-FAIL cmd:${cmd.logToken} reqId=${payload.requestId} e:${e.getMessage}"
              ) >> respondSomethingWentWrong(payload)

            case Right(res) =>
              logger.info(
                s"CMD-BG-SUCCESS cmd:${cmd.logToken} reqId=${payload.requestId} message:${res.getBlocks}"
              ) >> respond(payload, cmd, res)
          }
      }

  def executeNow(cmd: Command[F]): F[Response[F]] =
    cmd.handler.map(Response(Status.Ok).withEntity(_))

  def executeLater(payload: SlashCommandPayload, cmd: Command[F]): F[Response[F]] =
    queue.enqueue1((payload, cmd)).as(Response(Status.Ok).withEntity(warnSlow))

  override def processRequest(ar: AuthedRequest[F, SlackUser]): F[Response[F]] =
    ar.req.decode[SlashCommandPayload] { payload =>
      for {
        _   <- logger.info(s"PARSE-CMD reqId=${payload.requestId}  payload:'$payload'")
        cmd = mapper(payload)
        _ <- logger.info(
              s"COMMAND-SELECT cmd:${cmd.logToken} reqId=${payload.requestId} user:${payload.getUserName}(${payload.getUserId}) text:'${payload.getText}' respondImmediately:${cmd.respondInline}"
            )
        res <- if (cmd.respondInline) executeNow(cmd) else executeLater(payload, cmd)
      } yield res
    }

}
