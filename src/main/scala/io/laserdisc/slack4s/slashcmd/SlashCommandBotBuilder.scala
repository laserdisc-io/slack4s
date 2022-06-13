package io.laserdisc.slack4s.slashcmd

import cats.effect._
import cats.implicits._
import eu.timepit.refined.auto._
import io.laserdisc.slack4s.slack.internal.SlackAPIClient
import io.laserdisc.slack4s.slashcmd.SlashCommandBotBuilder.Defaults
import io.laserdisc.slack4s.slashcmd.internal.SignatureValidator._
import io.laserdisc.slack4s.slashcmd.internal._
import org.http4s._
import org.http4s.blaze.server._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.{Router, ServiceErrorHandler}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import slack4s.BuildInfo

object SlashCommandBotBuilder {

  object Defaults {
    val BindPort: BindPort         = 8080
    val BindAddress: BindAddress   = "0.0.0.0"
    val EndpointRoot: EndpointRoot = "/"
  }

  def apply[F[_]: Async](signingSecret: SigningSecret): SlashCommandBotBuilder[F] =
    new SlashCommandBotBuilder[F](signingSecret = signingSecret)
}

class SlashCommandBotBuilder[F[_]: Async] private[slashcmd] (
    signingSecret: SigningSecret,
    bindPort: BindPort = Defaults.BindPort,
    bindAddress: BindAddress = Defaults.BindAddress,
    endpointRoot: EndpointRoot = Defaults.EndpointRoot,
    commandParser: Option[CommandMapper[F]] = None,
    http4sBuilder: BlazeServerBuilder[F] => BlazeServerBuilder[F] = (b: BlazeServerBuilder[F]) => b
) {
  type Self = SlashCommandBotBuilder[F]

  private[this] val logger: Logger[F] = Slf4jLogger.getLogger[F]

  private[this] val dsl = Http4sDsl[F]
  import dsl._

  private[this] def copy(
      signingSecret: SigningSecret = signingSecret,
      bindPort: BindPort = bindPort,
      bindAddress: BindAddress = bindAddress,
      endpointRoot: EndpointRoot = endpointRoot,
      commandParser: Option[CommandMapper[F]] = commandParser,
      http4sBuilder: BlazeServerBuilder[F] => BlazeServerBuilder[F] = http4sBuilder
  ): Self =
    new SlashCommandBotBuilder(
      signingSecret = signingSecret,
      bindPort = bindPort,
      bindAddress = bindAddress,
      endpointRoot = endpointRoot,
      commandParser = commandParser,
      http4sBuilder = http4sBuilder
    )

  def withBindOptions(port: BindPort, address: BindAddress = "0.0.0.0"): Self =
    copy(bindPort = port, bindAddress = address)

  def withEndpointRoot(root: EndpointRoot): Self =
    copy(endpointRoot = root)

  def withHttp4sBuilder(http4sBuilder: BlazeServerBuilder[F] => BlazeServerBuilder[F]): Self =
    copy(http4sBuilder = http4sBuilder)

  def withCommandMapper(commandParser: CommandMapper[F]): Self =
    copy(commandParser = Some(commandParser))

  final def serveStream: fs2.Stream[F, Unit] =
    fs2.Stream
      .resource(SlackAPIClient.resource[F])
      .evalMap(slackApiClient => CommandRunner[F](slackApiClient, commandParser.getOrElse(CommandMapper.default[F])))
      .flatMap { cmdRunner =>
        cmdRunner.processBGCommandQueue
          .concurrently(
            http4sBuilder(
              BlazeServerBuilder[F]
                .bindHttp(bindPort, bindAddress)
                .withBanner(Banner)
                .withHttpApp(buildHttpApp(cmdRunner))
                .withServiceErrorHandler(errorHandler)
            ).serve
          )
      }

  def serve: F[Unit] = serveStream.compile.drain

  final val Banner = {
    val msg = s"Starting slack4s v${BuildInfo.version}"
    Seq(msg.map(_ => '-'), msg, msg.map(_ => '-'))
  }

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
      s"${endpointRoot.value}healthCheck" -> HttpRoutes.of[F] { case GET -> Root =>
        Ok.apply(s"OK")
      },
      s"${endpointRoot.value}slack" -> withValidSignature(signingSecret).apply(
        AuthedRoutes.of[SlackUser, F] { case req @ POST -> Root / "slashCmd" as _ =>
          cmdRunner.processRequest(req)
        }
      )
    ).orNotFound

}
