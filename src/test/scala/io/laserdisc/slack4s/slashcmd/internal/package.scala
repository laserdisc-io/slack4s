package io.laserdisc.slack4s.slashcmd

import cats.effect.IO
import org.http4s.Method.POST
import org.http4s.client.dsl.io._
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{ AuthedRequest, Uri, UrlForm }

package object internal {

  def mkAuthedSlashCommandRequest(
    commandText: String,
    responseUrl: Uri
  ): AuthedRequest[IO, SlackUser] =
    AuthedRequest(
      SlackUser("teamId", "userId"),
      POST(
        // we don't use the whole SlashPayloadCommand payload, just define the fields we care about
        UrlForm(
          "text"        -> commandText,
          "responseUrl" -> responseUrl.toString
        ),
        uri"http://does-not-matter"
      )
    )

}
