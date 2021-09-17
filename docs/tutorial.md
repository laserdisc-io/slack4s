# Space News!
## A slack4s tutorial

In this tutorial, we're going to configure, and run `Space News`, a slack slash command bot (source code @ [SpaceNewsExample.scala](../src/test/scala/examples/SpaceNewsExample.scala)) against a local development service.

We'll make use of https://www.spaceflightnewsapi.net/ - a simple, open API offering a GET endpoint for querying space news articles.

We will deploy a `/spacenews` slash command that:
* takes a search term argument, e.g `/spacenews nasa`
* queries `GET https://api.spaceflightnewsapi.net/v3/articles?_limit=3&title_contains=nasa`
* formats the results in a pretty list (see the end of this tutorial for the finished product)

![a working command](https://user-images.githubusercontent.com/885049/133719900-9336c55a-b3d3-4900-bcf7-0547a77e6159.png)

For a full guide on configuring slack applications, [see the slack docs](https://api.slack.com/start/distributing#single_workspace_apps).  This tutorial shows just enough configuration to get the command working against a locally running service. 

## Steps

### Setup local forwarding

We're going to use [ngrok](https://ngrok.com/) to quickly create a tunnel to the local app (which we'll start later).  

Install `ngrok` and run a tunnel to the default port that slack4s uses:

```
❯ ngrok http 8080

Session Status                online
Web Interface                 http://127.0.0.1:4040
Forwarding                    http://2846-173-61-91-146.ngrok.io -> http://localhost:8080
Forwarding                    https://2846-173-61-91-146.ngrok.io -> http://localhost:8080
```

**Make a note** of the https `Forwarding` address,  `https://2846-173-61-91-146.ngrok.io`, we'll need this later.

### Create The Slack App

* Authenticate to your slack workspace
* Visit [the app admin page](https://api.slack.com/apps), and click '**Create New App**'
* Click `From an app manifest` when asked how you wish to create your app
  * Manifests are a recently added (at the time of writing) beta feature and will save us lots of clicking around.
* Choose your dev workspace (apps are developed in a workspace, then deployed to that or other workspaces).
* Paste the following YAML into the editor that appears, after updating the `url` to `https://2846-173-61-91-146.ngrok.io`, with `/slack/slashCmd` suffixed (the URL we copied from earlier).
    ```yaml
    _metadata:
      major_version: 1
      minor_version: 1
    display_information:
      name: Space News!
      description: Space News App
      background_color: "#080f06"
    features:
      bot_user:
        display_name: spacenews
        always_online: false
      slash_commands:
        - command: /spacenews
          url: https://2846-173-61-91-146.ngrok.io/slack/slashCmd
          description: Query space news by topic keywords 
          usage_hint: nasa
          should_escape: false
    oauth_config:
      scopes:
        bot:
          - commands
    settings:
      org_deploy_enabled: false
      socket_mode_enabled: false
      token_rotation_enabled: false
    ```
  
* Click **Create** on the final dialog. 
* Bookmark the page you are on now, so you can easily access this app's configuration later.

### Install The Slack App

The app will exist now, but not be available until you _install_ it to your workspace.  On the app config screen, you'll find the option to install the app.

![install application](https://user-images.githubusercontent.com/885049/133716089-8f3e8c37-a737-4119-a98c-a6ebf5120084.png)

You will be prompted to accept the permission "Add shortcuts and/or slash commands that people can use" for your workspace.  

Once you accept, test that the slash command is installed in your workspace by typing `/spacenews foo`:

![failed command](https://user-images.githubusercontent.com/885049/133716307-f0c5eadf-e78d-4e93-848e-2f3e52907a31.png)

It fails because our service isn't running yet, but it does confirm that the slash command is installed! 

If you look at your `ngrok` terminal, you'll see the  connection attempt, proving that slack is attempting to access the correct URL.

```
HTTP Requests
-------------
POST /slack/slashCmd           502 Bad Gateway
```

### Run our service!

First, let's grab the signing secret.  Visit your application configuration page, and under `Basic Information` -> `App Credentials`, click `Show` and copy the value for **Signing Secret**  

![signing secret](https://user-images.githubusercontent.com/885049/133716713-a6ef2d27-3d11-4d1d-ad48-0bf8a9c94698.png)

Following the instructions on the [main README](../README.md), let's create a slack bot with this secret:

```scala
import cats.effect.{IO, IOApp}
import eu.timepit.refined.auto._
import io.laserdisc.slack4s.slashcmd._

object MySlackBot extends IOApp.Simple {

  // please don't hardcode secrets, this is just a demo
  val secret: SigningSecret = "7e16-----redacted------68c2c"  
  
  override def run: IO[Unit] = SlashCommandBotBuilder[IO](secret).serve

}
```

**Run the App!** By default, it will bind to localhost:8080, which is what we set `ngrok` up to proxy earlier.   

Note: The library uses log4cats-slf4j for logging, so add something like logback to the classpath if you want log output.

```
2021-09-16 22:56:39,920 INFO o.h.b.c.n.NIO1SocketServerGroup [io-compute-6] Service bound to address /0:0:0:0:0:0:0:0:8080
2021-09-16 22:56:39,926 INFO o.h.b.s.BlazeServerBuilder [io-compute-6]
----------------------------------------------------------
Starting slack4s v0.0.0+21-40742d0b+20210910-2234-SNAPSHOT
----------------------------------------------------------
2021-09-16 22:56:39,945 INFO o.h.b.s.BlazeServerBuilder [io-compute-6] http4s v0.23.3 on blaze v0.15.2 started at http://[::]:8080/
```

Now when you try and access the bot `/spacenews nasa`, you should see the default response:

![Space News with default slack4s response](https://user-images.githubusercontent.com/885049/133717409-3d851e68-57d1-4889-92d1-a0f1e6e972fc.png)

If you're still getting `dispatch_failed` errors:
* Ensure your slack app configuration has the correct URL defined.  
* If you don't see entries appearing on your `ngrok` terminal, then requests aren't being sent from slack to the right URL.
* Verify that your slack signing secret has been correctly copied (you'll see http 401 on `ngrok`, and also log errors)
* Once the URL is correct, the service log output is where you'll get help (you'll need a logging implementation on your classpath).

### Implement the API call

We're going to use a simple [http4s](https://http4s.org/) client to make the API call, and [circe](https://circe.github.io/circe/) to decode the result.

```scala
import io.circe.generic.auto._
import org.http4s.Method.GET
import org.http4s.Uri.unsafeFromString
import org.http4s._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder

// our simple model for representing the result
case class SpaceNewsArticle(
   title: String,
   url: String,
   imageUrl: String,
   newsSite: String,
   summary: String,
   updatedAt: Instant
)

// perform a GET on the API, deocding the results into a list of our model above
def querySpaceNews(word: String): IO[List[SpaceNewsArticle]] =
  BlazeClientBuilder[IO](global).resource.use { // will create a client for each invocation, but this is just a demo
    _.fetchAs[List[SpaceNewsArticle]](
      Request[IO](
        GET,
        unsafeFromString(s"https://api.spaceflightnewsapi.net/v3/articles")
            .withQueryParam("_limit", "3")
            .withQueryParam("title_contains", word)
      )
    )
  }

```

Next, let's write a helper function for building a slack SDK `LayoutBlock` for an individual `SpaceNewsArticle` result.

See the official [Slack Block Kit Builder](https://app.slack.com/block-kit-builder/) to learn about the various layout blocks available,
as well as an interactive tool for quickly prototyping layouts.

```scala

// helper functions for building the various block types in the slack LayoutBlock SDK
import io.laserdisc.slack4s.slack._

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

```

Now it is time to implement the `CommandMapper[F]` - a type alias for `SlashCommandPayload -> Command[F]` that will map the 
payload that slack4s decodes for you into the handler for the that input.  See [README.md](../README.md) for more detail.

In our case, there's no complex parsing or pattern matching.  We'll just ensure that _something_ was entered, and
blindly pass it to the API call.  :warning: this is just a simple demo - Please ensure you carefully sanitize all input that 
the user will pass you. 

```scala

  def mapper: CommandMapper[IO] = { (payload: SlashCommandPayload) =>
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

```

Notice that: 
* In the second `case`, we're invoking `querySpaceNews` which returns an `IO` for later evaluation.  
* We then format any results we get using our `formatNewsArticle` helper
* We specify that our response is `Delayed`, meaning the `IO` for the result will be evaluated in a background queue, in case the API is slower than slack's limit of 3 seconds for an inline response.
* See the [scaladoc](../src/main/scala/io/laserdisc/slack4s/slashcmd/Models.scala) on `Command` and `ResponseType` for more detail. 

### Test your mapper!

Finally, hook your new mapper up to the builder.

```scala
    SlashCommandBotBuilder[IO](secret)
        .withCommandMapper(mapper)
        .serve
```

We now have a fully functioning slash command handler! See [SpaceNewsExample.scala](../src/test/scala/examples/SpaceNewsExample.scala) for the complete code.

Restart your service.  

Invoke `/spacenews nasa` and after a second or two, you should see:

![a working command](https://user-images.githubusercontent.com/885049/133719900-9336c55a-b3d3-4900-bcf7-0547a77e6159.png)


