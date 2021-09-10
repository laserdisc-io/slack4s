package io.laserdisc.slack4s.slashcmd

import com.slack.api.methods.request.chat.ChatPostMessageRequest
import eu.timepit.refined.auto._

case class Command[F[_]](
  handler: F[ChatPostMessageRequest],
  responseType: ResponseType,
  logId: LogToken = "NA"
)

sealed trait AuthError extends Throwable
case class MissingHeader(headerName: String) extends AuthError {
  override def getMessage: String = s"Missing header: $headerName"
}
case class BadSignature(timestamp: String, body: String, signature: String) extends AuthError {
  override def getMessage: String =
    s"Bad signature:$signature, for timestamp:$timestamp and body:$body"
}

case class SlackUser(teamId: String, userId: String)

sealed trait ResponseType
case object Immediate                                  extends ResponseType
case object Delayed                                    extends ResponseType
case class DelayedWithMsg(msg: ChatPostMessageRequest) extends ResponseType
