package io.laserdisc.slack4s

import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import eu.timepit.refined.api.{Refined, RefinedTypeOps}
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.string.{MatchesRegex, Url}

package object slashcmd {

  final type SigningSecret = String Refined NonEmpty
  final type LogToken      = String Refined MatchesRegex["[A-Za-z0-9\\-\\_]+"]
  final type URL           = String Refined Url
  final type BindPort      = Int Refined Positive
  final type BindAddress   = String Refined NonEmpty

  object SigningSecret extends RefinedTypeOps[SigningSecret, String]
  object LogToken      extends RefinedTypeOps[LogToken, String]
  object URL           extends RefinedTypeOps[URL, String]
  object BindPort      extends RefinedTypeOps[BindPort, Int]
  object BindAddress   extends RefinedTypeOps[BindAddress, String]

  type CommandMapper[F[_]] = SlashCommandPayload => Command[F]

}
