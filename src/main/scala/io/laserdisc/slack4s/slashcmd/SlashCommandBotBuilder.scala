package io.laserdisc.slack4s.slashcmd

import cats.effect._
import cats.implicits._
import com.comcast.ip4s.{IpAddress, IpLiteralSyntax, Port}
import eu.timepit.refined.auto._
import io.laserdisc.slack4s.slack.internal.SlackAPIClient
import io.laserdisc.slack4s.slashcmd.SlashCommandBotBuilder.Defaults
import io.laserdisc.slack4s.slashcmd.internal.SignatureValidator._
import io.laserdisc.slack4s.slashcmd.internal._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.{Router, Server}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object SlashCommandBotBuilder {

  object Defaults {
    val BindPort: Port             = port"8080"
    val BindAddress: IpAddress     = ipv4"0.0.0.0"
    val EndpointRoot: EndpointRoot = "/"
  }

  def apply[F[_]: Async](signingSecret: SigningSecret): SlashCommandBotBuilder[F] =
    new SlashCommandBotBuilder[F](signingSecret = signingSecret)
}

class SlashCommandBotBuilder[F[_]: Async] private[slashcmd] (
    signingSecret: SigningSecret,
    bindPort: Port = Defaults.BindPort,
    bindAddress: IpAddress = Defaults.BindAddress,
    endpointRoot: EndpointRoot = Defaults.EndpointRoot,
    commandParser: Option[CommandMapper[F]] = None,
    additionalRoutes: Option[HttpRoutes[F]] = None,
    http4sBuilder: EmberServerBuilder[F] => EmberServerBuilder[F] = (b: EmberServerBuilder[F]) => b
) {
  type Self = SlashCommandBotBuilder[F]

  private[this] val logger: Logger[F] = Slf4jLogger.getLogger[F]

  private[this] val dsl = Http4sDsl[F]
  import dsl._

  private[this] def copy(
      signingSecret: SigningSecret = signingSecret,
      bindPort: Port = bindPort,
      bindAddress: IpAddress = bindAddress,
      endpointRoot: EndpointRoot = endpointRoot,
      commandParser: Option[CommandMapper[F]] = commandParser,
      additionalRoutes: Option[HttpRoutes[F]] = additionalRoutes,
      http4sBuilder: EmberServerBuilder[F] => EmberServerBuilder[F] = http4sBuilder
  ): Self =
    new SlashCommandBotBuilder(
      signingSecret = signingSecret,
      bindPort = bindPort,
      bindAddress = bindAddress,
      endpointRoot = endpointRoot,
      commandParser = commandParser,
      additionalRoutes = additionalRoutes,
      http4sBuilder = http4sBuilder
    )

  def withBindOptions(port: Port, address: IpAddress = Defaults.BindAddress): Self =
    copy(bindPort = port, bindAddress = address)

  def withEndpointRoot(root: EndpointRoot): Self =
    copy(endpointRoot = root)

  def withAdditionalRoutes(routes: HttpRoutes[F]): Self =
    copy(additionalRoutes = Some(routes))

  def withHttp4sBuilder(http4sBuilder: EmberServerBuilder[F] => EmberServerBuilder[F]): Self =
    copy(http4sBuilder = http4sBuilder)

  def withCommandMapper(commandParser: CommandMapper[F]): Self =
    copy(commandParser = Some(commandParser))

  final def serve: F[Nothing] = build.useForever

  final def build: Resource[F, Server] = {

    def mkHttpService(cmdRunner: CommandRunner[F]): Resource[F, Server] =
      http4sBuilder(
        EmberServerBuilder
          .default[F]
          .withHost(bindAddress)
          .withPort(bindPort)
          .withHttpApp(buildHttpApp(cmdRunner, additionalRoutes))
          .withErrorHandler(errorHandler)
      ).build

    for {
      client    <- SlackAPIClient.resource[F]
      cmdRunner <- Resource.eval(CommandRunner[F](client, commandParser.getOrElse(CommandMapper.default[F])))
      svc         = mkHttpService(cmdRunner)
      bgProcessor = Resource.eval(cmdRunner.processBGCommandQueue.compile.drain)
      both <- Resource.both(svc, bgProcessor)
    } yield both._1

  }

  def run: F[Nothing] = build.useForever

  def errorHandler: PartialFunction[Throwable, F[Response[F]]] = {
    case ex: AuthError =>
      // log the error, but don't expose exception details to http
      logger
        .warn(ex)(s"""ERROR-AUTH $ex""")
        .as(Response(Status.Unauthorized))

    case ex: Throwable =>
      logger
        .error(ex)(s"""ERROR-UNHANDLED $ex""")
        .flatMap(_ => InternalServerError("Something went wrong, see the logs."))
  }

  def buildHttpApp(cmdRunner: CommandRunner[F], additionalRoutes: Option[HttpRoutes[F]] = None): HttpApp[F] = {

    val botRoutes = Router(
      s"${endpointRoot.value}healthCheck" -> HttpRoutes.of[F] { case GET -> Root =>
        Ok.apply(s"OK")
      },
      s"${endpointRoot.value}slack" -> withValidSignature(signingSecret).apply(
        AuthedRoutes.of[SlackUser, F] { case req @ POST -> Root / "slashCmd" as _ =>
          cmdRunner.processRequest(req)
        }
      )
    )

    (botRoutes <+> additionalRoutes.getOrElse(HttpRoutes.empty[F])).orNotFound
  }

}
