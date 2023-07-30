package io.laserdisc.slack4s

import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import com.slack.api.model.block.*
import com.slack.api.model.block.composition.*
import com.slack.api.model.block.element.*
import io.laserdisc.slack4s.slashcmd.URL

import scala.jdk.CollectionConverters.*
import scala.util.matching.Regex

package object slack {

  def slackMessage(head: LayoutBlock, tail: LayoutBlock*): ChatPostMessageRequest =
    slackMessage(head +: tail)

  def slackMessage(blocks: Seq[LayoutBlock]): ChatPostMessageRequest =
    ChatPostMessageRequest
      .builder()
      .blocks(blocks.asJava)
      .build()

  def headerSection(text: String): LayoutBlock =
    HeaderBlock
      .builder()
      .text(textElement(text))
      .build()

  def textSection(text: String): LayoutBlock =
    SectionBlock
      .builder()
      .text(textElement(text))
      .build()

  def markdownSection(markdown: String): LayoutBlock =
    SectionBlock
      .builder()
      .text(markdownElement(markdown))
      .build()

  def markdownSection(markdown: String, fieldsHead: String, fieldsTail: String*): LayoutBlock =
    markdownSection(markdown, fieldsHead +: fieldsTail)

  def markdownSection(markdown: String, fields: Seq[String]): LayoutBlock =
    SectionBlock
      .builder()
      .text(markdownElement(markdown))
      .fields(fields.map[TextObject](markdownElement).asJava)
      .build()

  def markdownWithImgSection(
      markdown: String,
      imageUrl: URL,
      imageAlt: String
  ): LayoutBlock =
    markdownWithImgSection(markdown, imageUrl, imageAlt, Seq.empty)

  def markdownWithImgSection(
      markdown: String,
      imageUrl: URL,
      imageAlt: String,
      fieldsHead: String,
      fieldsTail: String*
  ): LayoutBlock =
    markdownWithImgSection(markdown, imageUrl, imageAlt, fieldsHead +: fieldsTail)

  def markdownWithImgSection(
      markdown: String,
      imageUrl: URL,
      imageAlt: String,
      fields: Seq[String]
  ): LayoutBlock = {

    val builder = SectionBlock
      .builder()
      .text(markdownElement(markdown))
      .accessory(imageElement(imageUrl.value, imageAlt))

    if (fields.isEmpty) {
      builder.build()
    } else {
      builder.fields(fields.map[TextObject](markdownElement).asJava).build()
    }

  }

  def contextSection(head: ContextBlockElement, tail: ContextBlockElement*): LayoutBlock =
    contextSection(head +: tail)

  def contextSection(elems: Seq[ContextBlockElement]): LayoutBlock =
    ContextBlock
      .builder()
      .elements(elems.asJava)
      .build()

  def dividerSection: LayoutBlock = DividerBlock.builder().build()

  def imageBlock(url: String, altText: String): ImageBlock =
    ImageBlock
      .builder()
      .imageUrl(url)
      .altText(altText)
      .build()

  def imageElement(url: String, altText: String): ImageElement =
    ImageElement
      .builder()
      .imageUrl(url)
      .altText(altText)
      .build()

  def markdownElement(markdown: String): MarkdownTextObject =
    MarkdownTextObject.builder().text(markdown).build()

  def textElement(text: String): PlainTextObject =
    PlainTextObject.builder().text(text).emoji(true).build()

  implicit class SlashCommandPayloadOps(val p: SlashCommandPayload) extends AnyVal {

    /** A hack to get a (relatively) unique & short request ID using triggerID Trigger IDs are a period-separated sequence of alphanumerics
      * of which the third appears distinctly random. We'll try just using the last few chars, and see if it's random _enough_ to
      * differentiate a slackbot's requests
      */
    def requestId: String =
      Option(p.getTriggerId)
        .map(_.trim.takeRight(8))
        .filter(_.nonEmpty)
        .getOrElse("n/a")

    /** Load the sanitized payload text, where all characters not matching the regex are dropped
      * @param regex
      *   Match 'safe' characters. Default is alphanumeric, dashes and spaces: `"[^A-Za-z0-9\\-\\s]"`
      */
    def sanitizedText(regex: Regex = "[^A-Za-z0-9\\-\\s]".r): String =
      Option(p.getText)
        .map(_.trim)
        .map(_.replaceAll(regex.regex, ""))
        .getOrElse("")
  }

}
