package io.laserdisc.slack4s.slack.internal

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import io.circe.Json
import io.laserdisc.slack4s.slack.*
import io.laserdisc.slack4s.slashcmd.URL
import munit.FunSuite
import org.http4s.Response
import org.http4s.circe.jsonDecoder
import org.http4s.client.Client
import org.http4s.implicits.*

class SlackAPIClientTest extends FunSuite {

  val MadeUpCallbackURI = uri"https://localhost/madeup/callback/url"

  val ImageInMsgURL = "http://foo.com/foo.png"

  test("SlackAPIClientImpl should post request to slack") {

    val mockHTTPClient = Client.apply[IO] { req =>
      Resource.eval {
        IO {
          // a POST to the provided callback should have been made
          assertEquals(req.method, org.http4s.Method.POST)
          assertEquals(req.uri, MadeUpCallbackURI)

          /* we're not testing the whole set of message codecs here, but we do want to ensure that
           * the codecs are being invoked.  We purposefully choose a nested value at particular
           * location that also ensures that slack's preferred JSON style (snake_case) is used. */
          val json = req.as[Json].unsafeRunSync()

          val firstImgURL = json.hcursor.downField("blocks").downArray.downField("accessory").downField("image_url").as[String].toOption

          assertEquals(
            firstImgURL,
            Some(ImageInMsgURL),
            s"expected URL not found in request payload at blocks[0].accessory.image_url"
          )

          // the slack API responds with 200 and body "ok" when a message is accepted, so let's emulate that
          Response[IO](body = fs2.Stream.emits("ok".getBytes("UTF-8")))
        }
      }
    }

    // if any of our assertions in the mock client fail, this `respond` invocation will throw
    SlackAPIClientImpl[IO](mockHTTPClient)
      .respond(
        MadeUpCallbackURI.toString(),
        slackMessage(
          markdownWithImgSection("yo", URL.unsafeFrom(ImageInMsgURL), "alt", Seq())
        )
      )
      .unsafeRunSync()

  }

}
