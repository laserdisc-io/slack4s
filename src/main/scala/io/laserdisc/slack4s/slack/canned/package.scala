package io.laserdisc.slack4s.slack

import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import io.laserdisc.slack4s.internal.ProjectRepo

package object canned {

  // $COVERAGE-OFF$
  def somethingWentWrong(payload: SlashCommandPayload): ChatPostMessageRequest =
    slackMessage(
      dividerSection,
      markdownSection(
        s":warning: Sorry, something went wrong responding to `${payload.getText}`. Please try again later."
      ),
      contextSection(
        markdownElement(
          s"For further information, reach out to the administrator and reference request ID ${payload.requestId}"
        )
      )
    )

  def helloFromSlack4s(payload: SlashCommandPayload): ChatPostMessageRequest =
    slackMessage(
      headerSection("Hello from slack4s!"),
      markdownSection(
        s"""This is a placeholder message, in response to your input `${payload.getText}`.
           |Visit the <$ProjectRepo|the slack4s homepage> for more information on how to write a custom handler.""".stripMargin
      )
    )
  // $COVERAGE-ON$
}
