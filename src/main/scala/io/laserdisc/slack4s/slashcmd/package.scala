package io.laserdisc.slack4s

import cats.data.Validated
import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import eu.timepit.refined.api.{Refined, RefinedTypeOps}
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.string.{MatchesRegex, Url}
import eu.timepit.refined.types.string.NonEmptyString

package object slashcmd {

  final type SigningSecret = NonEmptyString
  final type LogToken      = String Refined MatchesRegex["[A-Za-z0-9\\-\\_]+"]
  final type URL           = String Refined Url
  final type BindPort      = Int Refined Positive
  final type BindAddress   = NonEmptyString
  final type EndpointRoot  = NonEmptyString

  object SigningSecret extends RefinedTypeOps[SigningSecret, String]
  object LogToken      extends RefinedTypeOps[LogToken, String]
  object URL           extends RefinedTypeOps[URL, String]
  object BindPort      extends RefinedTypeOps[BindPort, Int]
  object BindAddress   extends RefinedTypeOps[BindAddress, String]
  object EndpointRoot  extends RefinedTypeOps[EndpointRoot, String]

  type CommandMapper[F[_]] = SlashCommandPayload => F[Command[F]]

  implicit def validCommand(commandPayload: SlashCommandPayload): Validated[PayloadError, SlashCommandPayload] =
    if (commandPayload.getCommand == null) Validated.invalid(MissingPayloadField("Command field was null"))
    else Validated.valid(commandPayload)

}
