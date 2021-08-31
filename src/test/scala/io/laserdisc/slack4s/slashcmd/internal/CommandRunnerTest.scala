package io.laserdisc.slack4s.slashcmd.internal

import cats.effect.{ ContextShift, IO, Timer }
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.laserdisc.slack4s.slack._
import io.laserdisc.slack4s.slack.internal.SlackAPIClient
import io.laserdisc.slack4s.slashcmd._
import munit.FunSuite
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{ AuthedRequest, EntityDecoder, Response, Status }

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.DurationInt

class CommandRunnerTest extends FunSuite {

  val ResponseURL = uri"http://localhost/some-callback-uri"

  def mkResponse(txt: String): ChatPostMessageRequest = slackMessage(headerSection(s"hello $txt"))

  test("should deliver response inline") {

    val (response, callbacks) = runCommandRunner(
      payload => Command(handler = IO.pure(mkResponse(payload.getText)), respondInline = false),
      request = mkAuthedSlashCommandRequest(commandText = "foobar", ResponseURL)
    )

    assertEquals(response.status, Status.Ok)
    assertEquals(callbacks, Nil, "did not expect background invocations to the slack API")

  }

  def runCommandRunner(
    commandMapper: CommandMapper[IO],
    request: AuthedRequest[IO, SlackUser]
  ): (Response[IO], List[(String, ChatPostMessageRequest)]) = {

    implicit val cs: ContextShift[IO] = IO.contextShift(global)
    implicit val timer: Timer[IO]     = IO.timer(global)

    for {
      // build the runner with a mock slack client so we can verify calls back to slack
      mockAPIClient <- SlackAPIClient.mock[IO]
      runner        <- CommandRunner[IO](mockAPIClient, commandMapper)

      // the slack call (once authorized) will be routed to processRequest
      response <- runner.processRequest(request)

      // SlashCommandBotBuilder starts `processBGCommandQueue` in parallel to the http service
      // so we run it briefly to capture any background invocations to the slack API as well
      _ <- runner.processBGCommandQueue.interruptAfter(1.second).compile.drain

      // collect the invocations
      apiCalls <- mockAPIClient.getRespondInvocations
    } yield (response, apiCalls)

  }.unsafeRunSync()

}
