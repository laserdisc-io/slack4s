package io.laserdisc.slack4s.slashcmd.internal

import cats.effect.IO
import io.laserdisc.slack4s.slack.*
import io.laserdisc.slack4s.slashcmd.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.{Response, Status}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class CommandRunnerTest extends SlashCommandSuite {

  // when testing the bg running in these scenarios, take 1 element if produced, else timeout after a second
  val waitForCallbacks: Option[(Int, FiniteDuration)] = Some((1, 1.second))

  test(
    "when response type == Immediate, runner should deliver HTTP 200 with command handler output"
  ) {

    val (response, callbacks) = testSlashCmdService(
      payload =>
        IO(
          Command(
            handler = IO.pure(slackMessage(textSection(s"hello ${payload.getText}"))),
            responseType = Immediate
          )
        ),
      signedSlashCmdRequest(text = "lenny"),
      waitForCallbacks
    )

    assertEquals(response.status, Status.Ok)
    assertEquals(response.asPostMessageReq, slackMessage(textSection("hello lenny")))
    assertEquals(callbacks, Nil, "did not expect background invocations to the slack API")

  }

  test(
    "when response type == Delayed, runner should deliver empty HTTP 200, with the command response in an API client callback"
  ) {

    val CallbackURL = "http://localhost/some-callback-uri/1111"

    val (response, callbacks) = testSlashCmdService(
      payload =>
        IO(
          Command(
            handler = IO.pure(slackMessage(headerSection(s"--- ${payload.getText} ---"))),
            responseType = Delayed
          )
        ),
      signedSlashCmdRequest(text = "foo bar", responseURL = CallbackURL),
      waitForCallbacks
    )

    assertEquals(response.status, Status.Ok)
    assert(isEmpty(response), "expected an empty response body")
    assertEquals(
      callbacks,
      List(CallbackURL -> slackMessage(headerSection("--- foo bar ---")))
    )

  }

  test(
    "when response type == DelayedWithMsg, response should be HTTP 200 with delay msg, and command response in an API client callback"
  ) {

    val CallbackURL = "http://localhost/some-callback-uri/222"

    val DelayMessage = slackMessage(markdownSection("* this will take a while.. *"))

    val (response, callbacks) = testSlashCmdService(
      payload =>
        IO(
          Command(
            handler = IO.pure(slackMessage(markdownSection(s"you sent: ${payload.getText}"))),
            responseType = DelayedWithMsg(DelayMessage)
          )
        ),
      signedSlashCmdRequest(text = "woof", responseURL = CallbackURL),
      waitForCallbacks
    )

    assertEquals(response.status, Status.Ok)
    assertEquals(response.asPostMessageReq, DelayMessage)
    assertEquals(
      callbacks,
      List(CallbackURL -> slackMessage(markdownSection("you sent: woof")))
    )

  }

  def getBodyText(response: Response[IO]): String = response.as[String].unsafeRunSync()

  def isEmpty(response: Response[IO]): Boolean =
    response.body.compile.toVector.unsafeRunSync().isEmpty

}
