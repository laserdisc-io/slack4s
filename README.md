# slack4s

![CI](https://github.com/laserdisc-io/slack4s/actions/workflows/ci.yaml/badge.svg) 
[![codecov](https://codecov.io/gh/laserdisc-io/slack4s/branch/main/graph/badge.svg?token=BEDHQ818EI)](https://codecov.io/gh/laserdisc-io/slack4s)
[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/laserdisc-io/slack4s)](https://github.com/laserdisc-io/slack4s/releases)

A pure functional library for easily building slack bots, built on [cats 3.x](https://typelevel.org/cats/), [http4s](https://http4s.org/) and the [Slack Java SDK](https://github.com/slackapi/java-slack-sdk/).  

Right now, [slash commands](https://api.slack.com/interactivity/slash-commands) are supported (contributions welcome!).   This library takes care of 

* Interacting with Slack's API
* Encoding/decoding payloads
* Verifying signature validity
* Handling background callbacks for longer running commands

Simply provide your business logic and you've got a deployable app!

## Slash Command Handler Quickstart

Add the following dependency:

```sbt
libraryDependencies += "io.laserdisc" %% "slack4s" % latestVersion
```

In this example, our [slash command](https://api.slack.com/interactivity/slash-commands) handler will take the form of a persistent HTTP service.  

With your [signing secret](https://api.slack.com/authentication/verifying-requests-from-slack#about) in hand, getting a skeleton handler up and running is as simple as:

```scala
import cats.effect.{IO, IOApp}
import eu.timepit.refined.auto._
import io.laserdisc.slack4s.slashcmd._

object MySlackBot extends IOApp.Simple {

  val secret: SigningSecret = "your-signing-secret" // demo purposes - please don't hardcode secrets  
  
  override def run: IO[Unit] = SlashCommandBotBuilder[IO](secret).serve

}

```

Set your slash command's **Request URL** to 

```
https://{your-base-url}/slack/slashCmd
```


Issue your slash command, e.g. `/slack4s foo` and you should get the default response:

![default response screenshot with placeholder message](https://user-images.githubusercontent.com/885049/133712091-82037415-8f72-4fcf-b1ae-4942c4f47f95.png)

The builder has some more useful functions if you need to customize your deployment further:

```scala
SlashCommandBotBuilder[IO](secret)
  .withCommandMapper(testCommandMapper)                   // your mapper impl, see next section
  .withBindOptions(port = 9999, address = "192.168.0.1")  // by default, binds to 0.0.0.0:8080
  .withHttp4sBuilder{                                    
    // offer the chance to customize http4s' BlazeServerBuilder used under the hood 
    // USE WITH CAUTION; it overrides any settings set by slack4s     
    _.withIdleTimeout(10.seconds)
     .withMaxConnections(512)
  }
  .serve
```

If your hosting (e.g. k8s, AWS ECS) needs a health check endpoint, use: 

```
https://{your-base-url}/healthCheck
```

## Implementing Your Mapper Logic

Create an implementation of `CommandMapper[F]` and pass it to the builder as follows e.g.:

```scala

val myMapper: CommandMapper[F] = .. // see the next section

SlashCommandBotBuilder[IO](secret)
  .withCommandMapper(myMapper)
  .serve
```

`CommandMapper[F]` is a type alias for: 

```scala
SlashCommandPayload => Command[F]
```

Your implementation of this function defines your business logic.  The input is the slash command request from Slack. The output defines how to respond to that request.

Slack4s will only invoke this function if the incoming request's slack signature has been validated against your signing secret.

### `SlashCommandPayload`
 
* This [Java class provided by Slack's Java SDK](https://github.com/slackapi/java-slack-sdk/blob/main/slack-app-backend/src/main/java/com/slack/api/app_backend/slash_commands/payload/SlashCommandPayload.java) (a dependency of this library) models the incoming request.
* Of primary interest is the `text` field, containing the arguments to you `/command` as provided by the user.
* Other fields provide contextual information about the call, such as `userId`, `channelName`, etc.

:warning: Slack4s' validation of the request signature only proves that the request originated from slack.  You still need to apply your own level of (dis)trust to the `text` value, as it comes verbatim from the user.  

Not only should you sanitize this input appropriately, you'll also have to handle/strip any formatting it contains.  Slack users tend to copy and paste slack text into commands - don't be surprised to get formatted input, e.g. `*homer simpson*` instead of `homer simpson`.

### `Command[F]`    

This is the description of how - _and when_ - to handle the user's request. The scaladoc on [Command[F]](src/main/scala/io/laserdisc/slack4s/slashcmd/Models.scala) should have all the information you need, but at a high level, It defines the following fields:

* **handler**: `F[ChatPostMessageRequest]`  
   * The effect to evaluate in response to the input.
   * [ChatPostMessageRequest](https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-client/src/main/java/com/slack/api/methods/request/chat/ChatPostMessageRequest.java) is the Slack API Java model representing the response message. 
      * Importing `io.laserdisc.slack4s.slack._` gives you a bunch of convenience functions to build this reponse object (e.g. `slackMessage(...)`, `markdownWithImgSection(...)`, etc.)
      * You can explore with the possible response structures by playing with the excellent [Slack Block Kit Builder](https://api.slack.com/tools/block-kit-builder).
* **responseType**: `ResponseType`
   * Whether to respond immediately, or process the information in a background queue and [post to a callback URL](https://api.slack.com/interactivity/slash-commands#responding_to_commands).  
   * See the [scaladoc for ResponseType](src/main/scala/io/laserdisc/slack4s/slashcmd/Models.scala) for all the options here.
* **logId**: `LogToken` 
  * `"NA"` by default, this token used in slack4s's logs when processing this particular command (useful for log filtering)


## Example
* See [src/](src/test/scala/examples/SpaceNewsExample.scala) for a working example of a slash command handler.

## Tutorial
* See [docs/tutorial.md](docs/tutorial.md) for a walkthrough of configuring and running the example. 
