package examples

import cats.effect.{ IO, IOApp }
import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import com.slack.api.model.block.LayoutBlock
import eu.timepit.refined.auto._
import io.circe.generic.auto._
import io.laserdisc.slack4s.slack._
import io.laserdisc.slack4s.slashcmd._
import org.http4s.Method.GET
import org.http4s.Uri.unsafeFromString
import org.http4s._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder

import java.time.Instant
import scala.concurrent.ExecutionContext.global

object ExampleSlashCommandApp extends IOApp.Simple {

  val secret: SigningSecret = "in the real world you wouldn't hardcode secrets...right?"

  override def run: IO[Unit] =
    SlashCommandBotBuilder[IO](secret)
      .withCommandMapper(testCommandMapper)
      .serveF

  def testCommandMapper: CommandMapper[IO] = { (payload: SlashCommandPayload) =>
    payload.getText.trim match {
      case "" =>
        Command(
          handler = IO.pure(slackMessage(headerSection("Please provide a search term!"))),
          responseType = Immediate
        )
      case searchTerm =>
        Command(
          handler = querySpaceNews(searchTerm).map {
            case Seq() =>
              slackMessage(
                headerSection(s"No results for: $searchTerm")
              )
            case articles =>
              slackMessage(
                headerSection(s"Space news results for: $searchTerm")
                  +: articles.flatMap(formatNewsArticle)
              )
          },
          responseType = Delayed
        )
    }
  }

  def formatNewsArticle(article: SpaceNewsArticle): Seq[LayoutBlock] =
    Seq(
      markdownWithImgSection(
        markdown = s"*<${article.url}|${article.title}>*\n${article.summary}",
        imageUrl = URL.unsafeFrom(article.imageUrl),
        imageAlt = s"Image for ${article.title}"
      ),
      contextSection(
        markdownElement(s"*Via ${article.newsSite}* - _last updated: ${article.updatedAt}_")
      ),
      dividerSection
    )

  def querySpaceNews(word: String): IO[List[SpaceNewsArticle]] =
    BlazeClientBuilder[IO](global).resource.use {
      _.fetchAs[List[SpaceNewsArticle]](
        Request[IO](
          GET,
          unsafeFromString(s"https://api.spaceflightnewsapi.net/v3/articles")
            .withQueryParam("_limit", "3")
            .withQueryParam("title_contains", word)
        )
      )
    }

  case class SpaceNewsArticle(
    title: String,
    url: String,
    imageUrl: String,
    newsSite: String,
    summary: String,
    updatedAt: Instant
  )

}
