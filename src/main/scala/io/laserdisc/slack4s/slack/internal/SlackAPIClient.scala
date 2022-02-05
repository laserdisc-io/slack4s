package io.laserdisc.slack4s.slack.internal

import cats.effect.{Async, Ref, Resource}
import cats.implicits._
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}
import org.typelevel.log4cats.slf4j.Slf4jLogger

object SlackAPIClient {

  def resource[F[_]: Async]: Resource[F, SlackAPIClientImpl[F]] =
    for {
      httpClient <- BlazeClientBuilder[F].resource
      client = SlackAPIClientImpl(httpClient)
    } yield client

  def mock[F[_]: Async]: F[MockSlackAPIClient[F]] =
    for {
      ref <- Ref.of(List[(String, ChatPostMessageRequest)]())
    } yield MockSlackAPIClient(ref)

}

trait SlackAPIClient[F[_]] {
  def respond(url: String, input: ChatPostMessageRequest): F[Unit]
}

case class SlackResponseAccepted()

case class SlackAPIClientImpl[F[_]: Async](httpClient: Client[F]) extends SlackAPIClient[F] {

  private[this] val logger = Slf4jLogger.getLogger[F]

  override def respond(url: String, input: ChatPostMessageRequest): F[Unit] =
    for {
      _ <- logger.debug(s"SLACK-RESPOND-REQ url:$url input:$input")
      res <- httpClient.expect[SlackResponseAccepted](
        Request[F](
          Method.POST,
          uri = Uri.unsafeFromString(url)
        ).withEntity(input)
      )
      _ <- logger.debug(s"SLACK-RESPOND-RES $res")
    } yield ()

}

case class MockSlackAPIClient[F[_]: Async](
    private val respondInvocations: Ref[F, List[(String, ChatPostMessageRequest)]]
) extends SlackAPIClient[F] {
  override def respond(url: String, input: ChatPostMessageRequest): F[Unit] =
    respondInvocations.update(f => f :+ ((url, input)))

  def getRespondInvocations: F[List[(String, ChatPostMessageRequest)]] = respondInvocations.get
}
