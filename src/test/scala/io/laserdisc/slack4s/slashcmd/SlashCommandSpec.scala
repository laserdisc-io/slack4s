package io.laserdisc.slack4s.slashcmd

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.slack.api.app_backend.SlackSignature
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import eu.timepit.refined.auto._
import io.laserdisc.slack4s.slack.internal.SlackAPIClient
import io.laserdisc.slack4s.slashcmd.internal.CommandRunner
import org.http4s.Method.POST
import org.http4s._
import org.http4s.client.dsl.io._
import org.http4s.implicits.http4sLiteralsSyntax
import org.typelevel.ci.CIString

import scala.concurrent.duration.DurationInt

trait SlashCommandSpec {

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

    // subset of the SlashPayloadCommand payload fields
    val payload = UrlForm(
      "text"         -> text,
      "response_url" -> responseURL,
      "team_id"      -> userID,
      "user_id"      -> teamID
    )

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

  def runApp(
    commandMapper: CommandMapper[IO],
    request: Request[IO]
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
      _ <- runner.processBGCommandQueue.take(1).interruptAfter(1.seconds).compile.drain

      // collect the invocations
      apiCalls <- mockAPIClient.getRespondInvocations

    } yield (response, apiCalls)

  }.unsafeRunSync()

}
