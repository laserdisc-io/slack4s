package io.laserdisc.slack4s.slashcmd.internal

import cats.effect.IO
import com.slack.api.app_backend.SlackSignature
import com.slack.api.app_backend.SlackSignature.HeaderNames.{X_SLACK_REQUEST_TIMESTAMP, X_SLACK_SIGNATURE}
import io.laserdisc.slack4s.slack._
import io.laserdisc.slack4s.slashcmd._
import org.http4s.Method.{GET, POST}
import org.http4s.{Charset, Header, Headers, Status, UrlForm}
import org.http4s.client.dsl.io._
import org.http4s.implicits.http4sLiteralsSyntax
import org.typelevel.ci.CIString

class SignatureValidatorTest extends SlashCommandSuite {

  test("not require any authentication to load health check endpoint") {

    val (response, _) = testSlashCmdService(defaultMapper, GET(uri"/healthCheck"))

    assertEquals(response.status, Status.Ok)
    assertEquals(response.as[String].unsafeRunSync(), "OK")
  }

  test("control case - HTTP 200 when valid signature is provided") {

    val payload     = UrlForm("text" -> "whatever")
    val signatureTS = System.currentTimeMillis().toString
    val signature = new SlackSignature.Generator(DefaultTestSigningSecret.value)
      .generate(signatureTS, UrlForm.encodeString(Charset.`UTF-8`)(payload))

    val req = POST(
      payload,
      uri"http://domain-does-not-matter.com/slack/slashCmd",
      Headers(
        Header.Raw(CIString(X_SLACK_SIGNATURE), signature),
        Header.Raw(CIString(X_SLACK_REQUEST_TIMESTAMP), signatureTS)
      )
    )

    val (response, _) = testSlashCmdService(defaultMapper, req)
    assertEquals(response.status, Status.Ok)

  }

  test("HTTP 401 if an incorrect signature is provided") {

    val payload     = UrlForm("text" -> "whatever")
    val signatureTS = System.currentTimeMillis().toString

    val req = POST(
      payload,
      uri"http://domain-does-not-matter.com/slack/slashCmd",
      Headers(
        Header.Raw(CIString(X_SLACK_SIGNATURE), "this is not the right signature!"),
        Header.Raw(CIString(X_SLACK_REQUEST_TIMESTAMP), signatureTS)
      )
    )

    val (response, _) = testSlashCmdService(defaultMapper, req)
    assertEquals(response.status, Status.Unauthorized)
  }

  test("HTTP 401 if the timestamp header value was not the same value used in signature generation") {

    val payload     = UrlForm("text" -> "whatever")
    val signatureTS = System.currentTimeMillis().toString
    val signature = new SlackSignature.Generator(DefaultTestSigningSecret.value)
      .generate(signatureTS, UrlForm.encodeString(Charset.`UTF-8`)(payload))

    val req = POST(
      payload,
      uri"http://domain-does-not-matter.com/slack/slashCmd",
      Headers(
        Header.Raw(CIString(X_SLACK_SIGNATURE), signature),
        Header.Raw(CIString(X_SLACK_REQUEST_TIMESTAMP), "1631227000000")
      )
    )

    val (response, _) = testSlashCmdService(defaultMapper, req)
    assertEquals(response.status, Status.Unauthorized)

  }

  test("throw if signature is not provided") {

    val payload     = UrlForm("text" -> "whatever")
    val signatureTS = System.currentTimeMillis().toString

    val req = POST(
      payload,
      uri"http://domain-does-not-matter.com/slack/slashCmd",
      Headers(
        Header.Raw(CIString(X_SLACK_REQUEST_TIMESTAMP), signatureTS)
      )
    )

    interceptMessage[MissingHeader](s"Missing header: $X_SLACK_SIGNATURE") {
      testSlashCmdService(defaultMapper, req)
    }

  }

  test("throw if timestamp is not provided") {

    val payload     = UrlForm("text" -> "whatever")
    val signatureTS = System.currentTimeMillis().toString
    val signature = new SlackSignature.Generator(DefaultTestSigningSecret.value)
      .generate(signatureTS, UrlForm.encodeString(Charset.`UTF-8`)(payload))

    val req = POST(
      payload,
      uri"http://domain-does-not-matter.com/slack/slashCmd",
      Headers(
        Header.Raw(CIString(X_SLACK_SIGNATURE), signature)
      )
    )

    interceptMessage[MissingHeader](s"Missing header: $X_SLACK_REQUEST_TIMESTAMP") {
      testSlashCmdService(defaultMapper, req)
    }

  }

  lazy val defaultMapper: CommandMapper[IO] = payload =>
    Command(
      handler = IO.pure(slackMessage(textSection(s"hello ${payload.getText}"))),
      responseType = Immediate
    )
}
