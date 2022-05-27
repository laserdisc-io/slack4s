package io.laserdisc.slack4s.slack

import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import munit.FunSuite

class SlashCommandPayloadOpsSpec extends FunSuite {

  test("SlashCommandPayloadOps should properly sanitize input text") {

    assertEquals(mkCmd("hello there").sanitizedText(), "hello there")
    assertEquals(mkCmd("pt *234234*").sanitizedText(), "pt 234234")
    assertEquals(mkCmd("pt `234234`").sanitizedText(), "pt 234234")
    assertEquals(mkCmd("pt `234234`   ").sanitizedText(), "pt 234234")
    assertEquals(mkCmd("  pt 234234  ").sanitizedText(), "pt 234234")

  }

  def mkCmd(txt: String): SlashCommandPayload = {
    // the slack SDK forces mutability..
    val cmd = new SlashCommandPayload()
    cmd.setText(txt)
    cmd
  }

}
