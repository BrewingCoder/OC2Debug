package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.client.Minecraft

/**
 * Hold one or more movement/action keys for a fixed number of game ticks.
 * While the tool is executing the player will move, jump, sprint, etc. as if
 * those keys were physically held.
 *
 * Args:
 *   forward  (bool, optional)
 *   back     (bool, optional)
 *   left     (bool, optional)
 *   right    (bool, optional)
 *   jump     (bool, optional)
 *   sprint   (bool, optional)
 *   attack   (bool, optional) — hold left-click
 *   use      (bool, optional) — hold right-click
 *   ticks    (int, required)  — how long to hold; max 600 (30 s)
 *
 * Returns: { "ticks":N, "keys":["forward",...] }
 */
object SendInputTool : Tool(
    name = "send_input",
    description = "Hold movement/action keys (forward/back/left/right/jump/sprint/attack/use) for N ticks. Use with set_look to navigate the player.",
    inputSchemaJson = """
        {"type":"object","required":["ticks"],"properties":{
          "forward":{"type":"boolean"}, "back":{"type":"boolean"},
          "left":{"type":"boolean"},    "right":{"type":"boolean"},
          "jump":{"type":"boolean"},    "sprint":{"type":"boolean"},
          "attack":{"type":"boolean"},  "use":{"type":"boolean"},
          "ticks":{"type":"integer","minimum":1,"maximum":600}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val ticks = args.intReq("ticks").coerceIn(1, 600)

        val forward = args.has("forward") && args.get("forward").asBoolean
        val back    = args.has("back")    && args.get("back").asBoolean
        val left    = args.has("left")    && args.get("left").asBoolean
        val right   = args.has("right")   && args.get("right").asBoolean
        val jump    = args.has("jump")    && args.get("jump").asBoolean
        val sprint  = args.has("sprint")  && args.get("sprint").asBoolean
        val attack  = args.has("attack")  && args.get("attack").asBoolean
        val use     = args.has("use")     && args.get("use").asBoolean

        val mc = Minecraft.getInstance()
        mc.level ?: error("No level loaded — enter a world first")

        val held = mutableListOf<String>()

        // Engage keys (from HTTP thread — boolean writes are JVM-atomic for primitives)
        mc.options.apply {
            if (forward) { keyUp.setDown(true);     held += "forward" }
            if (back)    { keyDown.setDown(true);   held += "back" }
            if (left)    { keyLeft.setDown(true);   held += "left" }
            if (right)   { keyRight.setDown(true);  held += "right" }
            if (jump)    { keyJump.setDown(true);   held += "jump" }
            if (sprint)  { keySprint.setDown(true); held += "sprint" }
            if (attack)  { keyAttack.setDown(true); held += "attack" }
            if (use)     { keyUse.setDown(true);    held += "use" }
        }

        try {
            // Wait for N game ticks (same approach as WaitTicksTool)
            val startTick = mc.level?.gameTime ?: 0L
            val deadline = startTick + ticks
            while (true) {
                val current = Minecraft.getInstance().level?.gameTime ?: break
                if (current >= deadline) break
                Thread.sleep(10)
            }
        } finally {
            // Always release keys — even if the tool call is interrupted
            mc.options.apply {
                if (forward) keyUp.setDown(false)
                if (back)    keyDown.setDown(false)
                if (left)    keyLeft.setDown(false)
                if (right)   keyRight.setDown(false)
                if (jump)    keyJump.setDown(false)
                if (sprint)  keySprint.setDown(false)
                if (attack)  keyAttack.setDown(false)
                if (use)     keyUse.setDown(false)
            }
        }

        val keysJson = held.joinToString(",") { "\"$it\"" }
        return """{"ticks":$ticks,"keys":[$keysJson]}"""
    }
}
