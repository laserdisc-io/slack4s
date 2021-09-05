package io.laserdisc.slack4s.slack

import cats.effect.Sync
import cats.implicits._
import com.google.gson.{ FieldNamingPolicy, Gson, GsonBuilder }
import com.slack.api.app_backend.slash_commands.SlashCommandPayloadParser
import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import com.slack.api.model.Attachment.VideoHtml
import com.slack.api.model.block.composition.TextObject
import com.slack.api.model.block.element.{ BlockElement, RichTextElement }
import com.slack.api.model.block.{ ContextBlockElement, LayoutBlock }
import com.slack.api.model.event.MessageChangedEvent.PreviousMessage
import com.slack.api.util.json._
import io.circe.parser._
import io.circe.{ Decoder, Encoder }
import org.http4s._
import org.http4s.circe.jsonEncoderOf

import scala.util.Try

package object internal {

  // TODO: find a way to get circe to work with lombok javabeans without needing gson
  private[this] val gson: Gson = {
    val gsonBuilder = new GsonBuilder
    gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    Map(
      classOf[BlockElement]        -> new GsonBlockElementFactory,
      classOf[ContextBlockElement] -> new GsonContextBlockElementFactory,
      classOf[LayoutBlock]         -> new GsonLayoutBlockFactory,
      classOf[VideoHtml]           -> new GsonMessageAttachmentVideoHtmlFactory,
      classOf[PreviousMessage]     -> new GsonMessageChangedEventPreviousMessageFactory,
      classOf[RichTextElement]     -> new GsonRichTextElementFactory,
      classOf[TextObject]          -> new GsonTextObjectFactory
    ).foreach { case (clazz, adapter) => gsonBuilder.registerTypeAdapter(clazz, adapter) }

    gsonBuilder.registerTypeAdapterFactory(new UnknownPropertyDetectionAdapterFactory)
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
    Encoder.instance { msg =>
      val asStr = gson.toJson(msg)
      parse(asStr) match {
        case Left(pf) => throw pf
        case Right(v) => v
      }
    }

  implicit val postMsgReqCirceDecoder: Decoder[ChatPostMessageRequest] =
    Decoder.instanceTry(h =>
      Try(h.focus.get).map(json => gson.fromJson(json.noSpaces, classOf[ChatPostMessageRequest]))
    )

  implicit def postMsgReqHttp4sEncoder[F[_]: Sync]: EntityEncoder[F, ChatPostMessageRequest] =
    jsonEncoderOf[F, ChatPostMessageRequest]

}
