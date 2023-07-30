package io.laserdisc.slack4s.slashcmd

import cats.effect.IO
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import io.circe.Decoder
import org.http4s.Response
import org.http4s.circe.CirceEntityCodec.*

package object internal {

  implicit class ResponseOps(val resp: Response[IO]) extends AnyVal {

    def asPostMessageReq: ChatPostMessageRequest = {
      import io.laserdisc.slack4s.slack.internal.postMsgReqCirceDecoder
      unsafeAs[ChatPostMessageRequest]
    }

    def unsafeAs[T](implicit dec: Decoder[T]): T =
      resp.as[T].unsafeRunSync()(cats.effect.unsafe.implicits.global)

  }

}
