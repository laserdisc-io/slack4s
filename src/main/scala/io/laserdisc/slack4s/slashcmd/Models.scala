package io.laserdisc.slack4s.slashcmd

import eu.timepit.refined.auto._
import com.slack.api.methods.request.chat.ChatPostMessageRequest

case class Command[F[_]](
  handler: F[ChatPostMessageRequest],
  respondImmediately: Boolean = false,
  logToken: LogToken = "NA"
)

sealed trait AuthError                                                      extends Throwable
case class MissingHeader(headerName: String)                                extends AuthError
case class BadSignature(timestamp: String, body: String, signature: String) extends AuthError

case class SlackUser(teamId: String, userId: String)
