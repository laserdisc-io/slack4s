package io.laserdisc.slack4s.slashcmd

import cats.effect.IO
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import io.circe.Decoder
import org.http4s.Method.POST
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.dsl.io._
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{ AuthedRequest, Response, Uri, UrlForm }

package object internal {

  implicit class ResponseOps(val resp: Response[IO]) extends AnyVal {

    def asPostMessageReq: ChatPostMessageRequest = {
      import io.laserdisc.slack4s.slack.internal.postMsgReqCirceDecoder
      unsafeAs[ChatPostMessageRequest]
    }

    def unsafeAs[T](implicit dec: Decoder[T]): T =
      resp.as[T].unsafeRunSync()(cats.effect.unsafe.implicits.global)

  }

  def mkAuthedSlashCommandRequest(
    commandText: String,
    responseUrl: Uri
  ): AuthedRequest[IO, SlackUser] =
    AuthedRequest(
      SlackUser("teamId", "userId"),
      POST(
        // we don't use the whole SlashPayloadCommand payload, just define the fields we care about
        UrlForm(
          "text"         -> commandText,
          "response_url" -> responseUrl.toString
        ),
        uri"http://fake-slash-handler-endpoint"
      )
    )

}
