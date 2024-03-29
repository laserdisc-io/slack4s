package examples

import cats.effect.{IO, IOApp}
import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import com.slack.api.model.block.LayoutBlock
import io.circe.{Decoder, HCursor}
//import io.circe.generic.auto.*
import io.laserdisc.slack4s.slack.*
import io.laserdisc.slack4s.slashcmd.*
import org.http4s.*
import org.http4s.Method.GET
import org.http4s.Uri.unsafeFromString
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.ember.client.EmberClientBuilder

import java.time.Instant

object SpaceNewsExample extends IOApp.Simple {

  val secret: SigningSecret = SigningSecret.unsafeFrom("7e162b0fd1bf1ca4537afa4246368c2c") // not a real secret

  override def run: IO[Unit] =
    SlashCommandBotBuilder[IO](secret)
      .withCommandMapper(mapper)
      .serve

  def mapper: CommandMapper[IO] = { (payload: SlashCommandPayload) =>
    payload.getText.trim match {
      case "" =>
        IO(
          Command(
            handler = IO.pure(slackMessage(headerSection("Please provide a search term!"))),
            responseType = Immediate
          )
        )
      case searchTerm =>
        IO(
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
    EmberClientBuilder.default[IO].build.use {
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

  implicit val dec: Decoder[SpaceNewsArticle] = (c: HCursor) =>
    for {
      a       <- c.downField("title").as[String]
      u       <- c.downField("url").as[String]
      i       <- c.downField("imageUrl").as[String]
      n       <- c.downField("newsSite").as[String]
      s       <- c.downField("summary").as[String]
      updated <- c.downField("updatedAt").as[Instant]
    } yield new SpaceNewsArticle(a, u, i, n, s, updated)
}
