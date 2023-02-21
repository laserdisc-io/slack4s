package io.laserdisc.slack4s.slack

import cats.effect.{Async, Sync}
import cats.implicits._
import com.google.gson.{FieldNamingPolicy, Gson, GsonBuilder}
import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import com.slack.api.model.Attachment.VideoHtml
import com.slack.api.model.block.composition.TextObject
import com.slack.api.model.block.element.{BlockElement, RichTextElement}
import com.slack.api.model.block.{ContextBlockElement, LayoutBlock}
import com.slack.api.model.event.MessageChangedEvent.PreviousMessage
import com.slack.api.util.json._
import io.circe.parser._
import io.circe.{Decoder, Encoder}
import org.http4s._
import org.http4s.circe.jsonEncoderOf
import org.http4s.FormDataDecoder._

import scala.util.Try

package object internal {

  /* The message classes that the slack SDK provides are intended for use with lombok & Gson. Rather
   * than build an entire family of circe codecs by hand, we delegate to Gson and use the gson factory
   * classes that are available in the slack SDK library. */
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
  implicit def responseAcceptedDecoder[F[_]: Async]: EntityDecoder[F, SlackResponseAccepted] =
    EntityDecoder.text.map {
      case "ok" => SlackResponseAccepted()
      case unexpected =>
        throw new IllegalArgumentException(s"Expected 'ok' in body, got $unexpected")
    }

  implicit val slashCommandFormDecoder: FormDataDecoder[SlashCommandPayload] =
    (
      field[String]("token"),
      field[String]("team_id"),
      field[String]("team_domain"),
      fieldOptional[String]("enterprise_id"),
      fieldOptional[String]("enterprise_name"),
      field[String]("api_app_id"),
      field[String]("channel_id"),
      field[String]("channel_name"),
      field[String]("user_id"),
      field[String]("user_name"),
      field[String]("command"),
      field[String]("text"),
      field[String]("response_url"),
      field[String]("trigger_id"),
      field[Boolean]("is_enterprise_install")
    ).mapN {
      case (
            token,
            teamId,
            teamDomain,
            enterpriseId,
            enterpriseName,
            apiAppId,
            channelId,
            channelName,
            userId,
            userName,
            command,
            text,
            responseUrl,
            triggerId,
            isEnterpriseInstall
          ) =>
        val slashCommandPayload: SlashCommandPayload = new SlashCommandPayload()
        slashCommandPayload.setToken(token)
        slashCommandPayload.setTeamId(teamId)
        slashCommandPayload.setTeamDomain(teamDomain)
        slashCommandPayload.setEnterpriseId(enterpriseId.getOrElse(""))
        slashCommandPayload.setEnterpriseName(enterpriseName.getOrElse(""))
        slashCommandPayload.setApiAppId(apiAppId)
        slashCommandPayload.setChannelId(channelId)
        slashCommandPayload.setChannelName(channelName)
        slashCommandPayload.setUserId(userId)
        slashCommandPayload.setUserName(userName)
        slashCommandPayload.setCommand(command)
        slashCommandPayload.setText(text)
        slashCommandPayload.setResponseUrl(responseUrl)
        slashCommandPayload.setTriggerId(triggerId)
        slashCommandPayload.setEnterpriseInstall(isEnterpriseInstall)
        slashCommandPayload
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
    Decoder.instanceTry(h => Try(h.focus.get).map(json => gson.fromJson(json.noSpaces, classOf[ChatPostMessageRequest])))

  implicit def postMsgReqHttp4sEncoder[F[_]: Sync]: EntityEncoder[F, ChatPostMessageRequest] =
    jsonEncoderOf[F, ChatPostMessageRequest]
}
