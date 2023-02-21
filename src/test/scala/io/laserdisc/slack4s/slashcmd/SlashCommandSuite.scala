package io.laserdisc.slack4s.slashcmd

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.slack.api.app_backend.SlackSignature
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import eu.timepit.refined.auto._
import io.laserdisc.slack4s.slack.internal.SlackAPIClient
import io.laserdisc.slack4s.slashcmd.internal.CommandRunner
import munit.FunSuite
import org.http4s.Method.POST
import org.http4s._
import org.http4s.client.dsl.io._
import org.http4s.implicits.http4sLiteralsSyntax
import org.typelevel.ci.CIString

import scala.concurrent.duration.FiniteDuration

trait SlashCommandSuite extends FunSuite {

  val DefaultTestSigningSecret: SigningSecret = "aabbbcccdddeeefff111222333444555666"
  val DefaultResponseUrl: String              = "http://localhost:1234/not/a/real/callback"
  val DefaultTeamID: String                   = "testSlackTeamID"
  val DefaultUserID: String                   = "testSlackUserID"

  implicit lazy val runtime: IORuntime = cats.effect.unsafe.implicits.global

  def signedSlashCmdRequest(
      text: String,
      signingSecret: SigningSecret = DefaultTestSigningSecret,
      currentTimeMS: Long = System.currentTimeMillis(),
      responseURL: String = DefaultResponseUrl,
      userID: String = DefaultUserID,
      teamID: String = DefaultTeamID
  ): Request[IO] = {

    val payload = testPayloadForm(text = text, responseUrl = responseURL, userId = userID, teamId = teamID)

    val signatureTS = currentTimeMS.toString
    val signature = new SlackSignature.Generator(signingSecret.value)
      .generate(signatureTS, UrlForm.encodeString(Charset.`UTF-8`)(payload))

    POST(
      payload,
      uri"http://fake-slash-handler-endpoint/slack/slashCmd",
      Headers(
        Header.Raw(CIString(SlackSignature.HeaderNames.X_SLACK_SIGNATURE), signature),
        Header.Raw(CIString(SlackSignature.HeaderNames.X_SLACK_REQUEST_TIMESTAMP), signatureTS)
      )
    )

  }

  def testSlashCmdService(
      commandMapper: CommandMapper[IO],
      request: Request[IO],
      waitForCallbacks: Option[(Int, FiniteDuration)] = None
  ): (Response[IO], List[(String, ChatPostMessageRequest)]) = {

    for {
      // build the runner with a mock slack client so we can verify calls back to slack
      mockAPIClient <- SlackAPIClient.mock[IO]
      runner        <- CommandRunner[IO](mockAPIClient, commandMapper)

      httpApp = new SlashCommandBotBuilder[IO](DefaultTestSigningSecret).buildHttpApp(runner)

      // authorized slash command requests will be routed to processRequest
      response <- httpApp.run(request)

      // The app starts `runner.processBGCommandQueue` in parallel to the http service,
      // so we run it briefly to capture any background invocations to the slack API as well
      _ <- waitForCallbacks.fold(IO.unit) { case (takeN, duration) =>
        runner.processBGCommandQueue.take(takeN).interruptAfter(duration).compile.drain
      }

      // collect the invocations
      apiCalls <- mockAPIClient.getRespondInvocations

    } yield (response, apiCalls)

  }.unsafeRunSync()

  def testPayloadForm(text: String, responseUrl: String, userId: String, teamId: String): UrlForm =
    UrlForm(
      "token"                 -> "token",
      "api_app_id"            -> "apiAppId",
      "team_id"               -> teamId,
      "team_domain"           -> "teamDomain",
      "channel_id"            -> "channelId",
      "channel_name"          -> "channelName",
      "user_id"               -> userId,
      "user_name"             -> "userName",
      "command"               -> "command",
      "text"                  -> text,
      "response_url"          -> responseUrl,
      "trigger_id"            -> "triggerId",
      "is_enterprise_install" -> false.toString
    )

}
