package io.laserdisc.slack4s.slashcmd.internal
import cats.data.{Kleisli, OptionT}
import cats.effect.{Async, Sync}
import cats.implicits._
import com.slack.api.app_backend.SlackSignature
import com.slack.api.app_backend.SlackSignature.HeaderNames._
import com.slack.api.app_backend.SlackSignature.Verifier
import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import io.laserdisc.slack4s.slack.internal._
import io.laserdisc.slack4s.slashcmd._
import org.http4s._
import org.http4s.server.AuthMiddleware
import org.typelevel.ci.CIString
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.http4s.FormDataDecoder.formEntityDecoder

object SignatureValidator {

  type Validated[T] = Either[AuthError, T]

  private[this] def forbidden[F[_]: Sync]: AuthedRoutes[String, F] =
    Kleisli(_ => OptionT.pure(Response[F](Status.Unauthorized)))

  def withValidSignature[F[_]: Async](
      signingSecret: String
  ): AuthMiddleware[F, SlackUser] = {

    val logger = Slf4jLogger.getLogger[F]

    val slackSignatureVerifier = new Verifier(
      new SlackSignature.Generator(signingSecret)
    )

    def getRequiredHeader(req: Request[F], header: String): F[String] =
      Sync[F].fromOption(
        req.headers.get(CIString(header)).map(_.head.value),
        MissingHeader(header)
      )

    def validateSignature(request: Request[F]): F[Either[String, SlackUser]] =
      for {
        ts       <- getRequiredHeader(request, X_SLACK_REQUEST_TIMESTAMP)
        sig      <- getRequiredHeader(request, X_SLACK_SIGNATURE)
        bodyText <- request.as[String]
        payload  <- request.attemptAs[SlashCommandPayload].value
        res <-
          payload match {
            case Left(error) =>
              logger
                .error(s"SIG-VALIDATION-FAIL: ${error.message}") *> MissingPayloadField(error.message).toString.asLeft[SlackUser].pure[F]
            case Right(slashCommandPayload) =>
              if (slackSignatureVerifier.isValid(ts, bodyText, sig))
                SlackUser(slashCommandPayload.getTeamId, slashCommandPayload.getUserId).asRight[String].pure[F]
              else BadSignature(ts, bodyText, sig).toString.asLeft[SlackUser].pure[F]
          }
        _ <- logger.info(s"SIG-VALIDATION result:$res sig:$sig, ts:$ts, body:$bodyText")
      } yield res

    AuthMiddleware(authUser = Kleisli(validateSignature), onFailure = forbidden)

  }
}
