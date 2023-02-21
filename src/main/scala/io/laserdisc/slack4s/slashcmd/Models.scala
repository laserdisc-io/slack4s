package io.laserdisc.slack4s.slashcmd

import com.slack.api.methods.request.chat.ChatPostMessageRequest
import eu.timepit.refined.auto._

/** The description of a Command - an effect to be evaluated, providing a response (along with instructions on how to deliver the response.
  *
  * @param handler
  *   An effect which returns a `ChatPostMessageRequest`, the Slack SDK payload representing a response. For information on (and a really
  *   useful utility for experiementing with) slack responses, see the slack block kit builder -
  *   https://api.slack.com/tools/block-kit-builder
  * @param responseType
  *   Whether this command is evaluated immediately and response returned, or processed in the background, and returned later. See
  *   `ResponseType` for more. Defaults to [[Delayed]]
  * @param logId
  *   By default `NA`, this is simply a (alphanumeric, dashes and underscores only) string which will be used in log messages, useful for
  *   filtering logs for particular command type output.
  */
case class Command[F[_]](
    handler: F[ChatPostMessageRequest],
    responseType: ResponseType = Delayed,
    logId: LogToken = "NA"
)

/** Used by the http4s middleware when building a validated `AuthedRequest`
  * @param teamId
  *   The teamId from the `SlashCommandPayload`
  * @param userId
  *   The userId from the `SlashCommandPayload`
  */
case class SlackUser(teamId: String, userId: String)

sealed trait AuthError extends Throwable
case class MissingHeader(headerName: String) extends AuthError {
  override def getMessage: String = s"Missing header: $headerName"
}

sealed trait PayloadError extends Throwable
case class MissingPayloadField(error: String) extends PayloadError {
  override def getMessage: String = s"Missing payload field error: $error"
}
case class BadSignature(timestamp: String, body: String, signature: String) extends AuthError {
  override def getMessage: String =
    s"Bad signature:$signature, for timestamp:$timestamp and body:$body"
}

/** See https://api.slack.com/interactivity/slash-commands#responding_to_commands
  *
  * In short, you need to decide whether your command's effect (and this application's service hosting) will return a response safely
  * **under 3 seconds**.
  *
  * If so, your output can be delivered as the response to the incoming slash command request (e.g. if the user types an invalid command)
  *
  * If not, your response can be processed in a background queue, and returned to the user via the webhook URL in the slash command payload.
  *
  * Slack4s takes care of the work of immediate vs background processing - all you need to do is specify which mode of response this
  * particular command definition requires.
  */
sealed trait ResponseType

/** A command specifying this is all but guaranteed to respond in < 3 seconds
  */
case object Immediate extends ResponseType

/** A longer running command (maybe it's performing some IO, or the underlying hosting might be slow (e.g. a cold AWS lambda), so process
  * the command in a background queue and respond when ready.
  */
case object Delayed extends ResponseType

/** Same as [[Delayed]], but offers a way to respond to the user _immediately_ with an interim message
  * @param msg
  *   The intermediate message - e.g. "This might take a little while, please wait..."
  */
case class DelayedWithMsg(msg: ChatPostMessageRequest) extends ResponseType
