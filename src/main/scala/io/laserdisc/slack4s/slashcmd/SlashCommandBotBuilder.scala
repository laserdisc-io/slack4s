package io.laserdisc.slack4s.slashcmd

import cats.effect.{ ConcurrentEffect, _ }
import cats.implicits._
import eu.timepit.refined.auto._
import io.laserdisc.slack4s.internal._
import io.laserdisc.slack4s.slack.internal.SlackAPIClient
import io.laserdisc.slack4s.slashcmd.SlashCommandBotBuilder.Defaults
import io.laserdisc.slack4s.slashcmd.internal.SignatureValidator._
import io.laserdisc.slack4s.slashcmd.internal._
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.{ Router, ServiceErrorHandler }
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import slack4s.BuildInfo

import scala.concurrent.ExecutionContext

object SlashCommandBotBuilder {

  object Defaults {
    val ExecutionCtx: ExecutionContext = mkCachedThreadPool("CmdHandler")
    val BindPort: BindPort             = 8080
    val BindAddress: BindAddress       = "0.0.0.0"

  }

  def apply[F[_]](signingSecret: SigningSecret)(
    implicit f: ConcurrentEffect[F],
    t: Timer[F]
  ): SlashCommandBotBuilder[F] =
    new SlashCommandBotBuilder[F](signingSecret = signingSecret)

}

class SlashCommandBotBuilder[F[_]: ConcurrentEffect: Timer] private (
  signingSecret: SigningSecret,
  ec: ExecutionContext = Defaults.ExecutionCtx,
  bindPort: BindPort = Defaults.BindPort,
  bindAddress: BindAddress = Defaults.BindAddress,
  commandParser: Option[CommandMapper[F]] = None,
  http4sBuilder: BlazeServerBuilder[F] => BlazeServerBuilder[F] = (b: BlazeServerBuilder[F]) => b
) {
  type Self = SlashCommandBotBuilder[F]

  private[this] val logger: Logger[F] = Slf4jLogger.getLogger[F]

  private[this] val dsl = Http4sDsl[F]
  import dsl._

  private[this] def copy(
    ec: ExecutionContext = ec,
    signingSecret: SigningSecret = signingSecret,
    bindPort: BindPort = bindPort,
    bindAddress: BindAddress = bindAddress,
    commandParser: Option[CommandMapper[F]] = commandParser,
    http4sBuilder: BlazeServerBuilder[F] => BlazeServerBuilder[F] = http4sBuilder
  ): Self =
    new SlashCommandBotBuilder(
      ec = ec,
      signingSecret = signingSecret,
      bindPort = bindPort,
      bindAddress = bindAddress,
      commandParser = commandParser,
      http4sBuilder = http4sBuilder
    )

  def withBindOptions(port: BindPort, address: BindAddress = "0.0.0.0"): Self =
    copy(bindPort = port, bindAddress = address)

  def withHttp4sBuilder(http4sBuilder: BlazeServerBuilder[F] => BlazeServerBuilder[F]): Self =
    copy(http4sBuilder = http4sBuilder)

  def withCommandMapper(commandParser: CommandMapper[F]): Self =
    copy(commandParser = Some(commandParser))

  final def serve: fs2.Stream[F, ExitCode] =
    fs2.Stream
      .resource(SlackAPIClient.resource[F])
      .evalMap(slackApiClient =>
        CommandRunner[F](slackApiClient, commandParser.getOrElse(CommandMapper.default[F]))
      )
      .flatMap { cmdRunner =>
        cmdRunner.processBGCommandQueue
          .concurrently(
            http4sBuilder(
              BlazeServerBuilder[F](ec)
                .bindHttp(bindPort, bindAddress)
                .withBanner(Banner)
                .withHttpApp(buildHttpApp(cmdRunner))
                .withServiceErrorHandler(errorHandler)
            ).serve
          )
          .as(ExitCode.Success)
      }

  def serveF: F[ExitCode] = serve.compile.lastOrError

  final val Banner = {
    val msg = s"Starting slack4s v${BuildInfo.version}"
    Seq(msg.map(_ => '-'), msg, msg.map(_ => '-'))
  }

  private[this] object RouteNames {
    val HEALTHCHECK = "/healthCheck"
    val SLACK       = "/slack"
  }

  private[this] val SLACK_SLASH_COMMAND = "slashCmd"

  def errorHandler: ServiceErrorHandler[F] =
    req => {
      case ex: AuthError =>
        // log the error, but don't expose exception details to http
        logger
          .warn(s"""ERROR-AUTH $ex addr:${req.remoteAddr.getOrElse("<unknown>")}""")
          .as(Response(Status.Unauthorized))

      case ex: Throwable =>
        logger
          .error(ex)(s"""ERROR-UNHANDLED $ex addr:${req.remoteAddr.getOrElse("<unknown>")}""")
          .flatMap(_ => InternalServerError("Something went wrong, see the logs."))
    }

  def buildHttpApp(cmdRunner: CommandRunner[F]): HttpApp[F] =
    Router(
      RouteNames.HEALTHCHECK -> HttpRoutes.of[F] {
        case GET -> Root =>
          Ok(s"All OK")
      },
      RouteNames.SLACK -> withValidSignature(signingSecret).apply(
        AuthedRoutes.of[SlackUser, F] {
          case req @ POST -> Root / SLACK_SLASH_COMMAND as _ =>
            cmdRunner.processRequest(req)
        }
      )
    ).orNotFound

}
