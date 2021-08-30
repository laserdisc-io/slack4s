package io.laserdisc.slack4s.slashcmd

import cats.effect.Sync
import cats.implicits._
import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import eu.timepit.refined.auto._
import io.laserdisc.slack4s.internal.ProjectRepo
import io.laserdisc.slack4s.slack.canned._
import org.typelevel.log4cats.slf4j.Slf4jLogger.getLogger

object CommandMapper {

  def default[F[_]: Sync]: CommandMapper[F] =
    (payload: SlashCommandPayload) =>
      Command(
        handler = getLogger
          .warn(
            s"Responding with default message; To configure your own processor, see $ProjectRepo"
          )
          .as(helloFromSlack4s(payload)),
        respondImmediately = true,
        logToken = "GETTING-STARTED"
      )

}
