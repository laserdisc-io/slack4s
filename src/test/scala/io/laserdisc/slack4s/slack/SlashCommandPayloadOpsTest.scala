package io.laserdisc.slack4s.slack

import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import munit.FunSuite

class SlashCommandPayloadOpsTest extends FunSuite {

  def mkPayload(triggerId: String): SlashCommandPayload = {
    val payload = new SlashCommandPayload
    payload.setTriggerId(triggerId)
    payload
  }

  test("requestId should be built correctly") {

    assertEquals(mkPayload("13345224609.738474920.8088930838d88f008e0").requestId, "88f008e0")
    assertEquals(mkPayload("333").requestId, "333")
    assertEquals(mkPayload("").requestId, "n/a")
    assertEquals(mkPayload(null).requestId, "n/a")

  }

}
