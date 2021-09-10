package io.laserdisc.slack4s.slack

import diffson._
import diffson.circe._
import diffson.jsonpatch.JsonPatch
import diffson.jsonpatch.simplediff._
import io.circe.parser._
import io.circe.syntax._
import io.laserdisc.slack4s.slack.internal.postMsgReqCirceEncoder
import io.laserdisc.slack4s.slashcmd.URL
import munit.FunSuite

class SlackMessageBuildersTest extends FunSuite {

  test("slackMessage builder helpers should generate valid JSON") {

    val msg = slackMessage(
      headerSection("test header"),
      textSection("test text"),
      dividerSection,
      markdownSection("*test markdown*", "field1", "field2", "field3"),
      markdownWithImgSection(
        "*test markdown with img*",
        URL.unsafeFrom("http://nowhere"),
        "test image alt",
        "imgField1",
        "imgField2",
        "imgField3"
      ),
      contextSection(
        textElement("context text")
      )
    )

    val actual = msg.asJson(postMsgReqCirceEncoder)

    val expected =
      parse("""{
        |  "link_names" : false,
        |  "blocks" : [
        |    {
        |      "type" : "header",
        |      "text" : {
        |        "type" : "plain_text",
        |        "text" : "test header",
        |        "emoji" : true
        |      }
        |    },
        |    {
        |      "type" : "section",
        |      "text" : {
        |        "type" : "plain_text",
        |        "text" : "test text",
        |        "emoji" : true
        |      }
        |    },
        |    {
        |      "type" : "divider"
        |    },
        |    {
        |      "type" : "section",
        |      "text" : {
        |        "type" : "mrkdwn",
        |        "text" : "*test markdown*"
        |      },
        |      "fields" : [
        |        {
        |          "type" : "mrkdwn",
        |          "text" : "field1"
        |        },
        |        {
        |          "type" : "mrkdwn",
        |          "text" : "field2"
        |        },
        |        {
        |          "type" : "mrkdwn",
        |          "text" : "field3"
        |        }
        |      ]
        |    },
        |    {
        |      "type" : "section",
        |      "text" : {
        |        "type" : "mrkdwn",
        |        "text" : "*test markdown with img*"
        |      },
        |      "fields" : [
        |        {
        |          "type" : "mrkdwn",
        |          "text" : "imgField1"
        |        },
        |        {
        |          "type" : "mrkdwn",
        |          "text" : "imgField2"
        |        },
        |        {
        |          "type" : "mrkdwn",
        |          "text" : "imgField3"
        |        }
        |      ],
        |      "accessory" : {
        |        "type" : "image",
        |        "image_url" : "http://nowhere",
        |        "alt_text" : "test image alt"
        |      }
        |    },
        |    {
        |      "type" : "context",
        |      "elements" : [
        |        {
        |          "type" : "plain_text",
        |          "text" : "context text",
        |          "emoji" : true
        |        }
        |      ]
        |    }
        |  ],
        |  "unfurl_links" : false,
        |  "unfurl_media" : false,
        |  "mrkdwn" : true,
        |  "reply_broadcast" : false
        |}
        |""".stripMargin) match {
        case Left(err) => throw err
        case Right(v)  => v
      }

    assertEquals(diff(actual, expected), JsonPatch(List.empty))

  }

}
