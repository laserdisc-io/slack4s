package io.laserdisc.slack4s.slack

import cats.effect.Sync
import cats.implicits._
import com.google.gson.{FieldNamingPolicy, Gson, GsonBuilder}
import com.slack.api.app_backend.slash_commands.SlashCommandPayloadParser
import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor}
import io.circe.parser._
import org.http4s.circe.jsonEncoderOf
import org.http4s.{DecodeResult, _}

package object internal {

  private[this] val gson: Gson = {
    val gsonBuilder = new GsonBuilder
    gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    gsonBuilder.create()
  }

  // slack responds with a body of just "ok" on success
  implicit def responseAcceptedDecoder[F[_]: Sync]: EntityDecoder[F, SlackResponseAccepted] =
    EntityDecoder.text.map {
      case "ok" => SlackResponseAccepted()
      case unexpected =>
        throw new IllegalArgumentException(s"Expected 'ok' in body, got $unexpected")
    }

  implicit def commandPayloadDecoder[F[_]: Sync]: EntityDecoder[F, SlashCommandPayload] =
    new EntityDecoder[F, SlashCommandPayload] {

      private[this] val mediaType = MediaType.application.`x-www-form-urlencoded`

      private[this] val parser = new SlashCommandPayloadParser()

      override def decode(m: Media[F], strict: Boolean): DecodeResult[F, SlashCommandPayload] =
        DecodeResult.success(
          m.as[String]
            .map { str =>
              Option(parser.parse(str)) match {
                case Some(v) => v
                case None =>
                  throw new IllegalArgumentException(
                    s"SlashCommandPayload could not be parsed from: $str"
                  )
              }
            }
        )

      override def consumes: Set[MediaRange] = Set(mediaType)
    }

  implicit val postMsgReqCirceEncoder: Encoder[ChatPostMessageRequest] =
    (msg: ChatPostMessageRequest) => {
      // TODO: find a way to get circe to encode this lombok javabean without gson
      val asStr = gson.toJson(msg)
      parse(asStr) match {
        case Left(pf) => throw pf
        case Right(v) => v
      }
    }




  implicit def postMsgReqHttp4sEncoder[F[_]: Sync]: EntityEncoder[F, ChatPostMessageRequest] =
    jsonEncoderOf[F, ChatPostMessageRequest]

}
