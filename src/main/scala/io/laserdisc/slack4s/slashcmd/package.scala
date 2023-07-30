package io.laserdisc.slack4s

import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import eu.timepit.refined.api.{Refined, RefinedTypeOps}
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.string.{MatchesRegex, Url}
import eu.timepit.refined.types.string.NonEmptyString
import monix.newtypes.{BuildFailure, NewtypeValidated}

package object slashcmd {

  type EmailAddress = EmailAddress.Type

  object EmailAddress extends NewtypeValidated[String] {
    def apply(v: String): Either[BuildFailure[Type], Type] =
      if (v.contains("@"))
        Right(unsafeCoerce(v))
      else
        Left(BuildFailure("missing @"))
  }

  val k = EmailAddress("yo hoo")

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
}
