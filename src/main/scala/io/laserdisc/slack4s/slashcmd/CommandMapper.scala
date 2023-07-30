package io.laserdisc.slack4s.slashcmd

import cats.effect.Sync
import cats.implicits.*
import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import io.laserdisc.slack4s.internal.ProjectRepo
import io.laserdisc.slack4s.slack.canned.*
import org.typelevel.log4cats.slf4j.Slf4jLogger.getLogger

object CommandMapper {

  // $COVERAGE-OFF$
  def default[F[_]: Sync]: CommandMapper[F] =
    (payload: SlashCommandPayload) =>
      Sync[F].delay {
        Command(
          handler = getLogger
            .warn(
              s"Responding with default message; To configure your own processor, see $ProjectRepo"
            )
            .as(helloFromSlack4s(payload)),
          responseType = Immediate,
          logId = LogToken.unsafeFrom("GETTING-STARTED")
        )
      }
  // $COVERAGE-ON$

}
