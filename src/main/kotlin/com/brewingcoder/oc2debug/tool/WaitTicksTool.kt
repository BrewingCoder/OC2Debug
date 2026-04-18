package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.client.Minecraft

/**
 * Block until N game ticks have elapsed. Useful for sequencing:
 *   set_block → wait_ticks(2) → screenshot.
 *
 * Args:
 *   ticks (int, required) — number of game ticks to wait
 *
 * Returns: { "waited": N }
 */
object WaitTicksTool : Tool(
    name = "wait_ticks",
    description = "Block until N game ticks have elapsed (20 ticks = 1 second).",
    inputSchemaJson = """
        {"type":"object","required":["ticks"],"properties":{
          "ticks":{"type":"integer","minimum":1,"maximum":1200}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val ticks = args.intReq("ticks").coerceIn(1, 1200)
        val level = Minecraft.getInstance().level ?: error("No level loaded")
        val startTick = level.gameTime
        val deadline = startTick + ticks
        // Poll on the HTTP thread (NOT the MC thread — that would block the game)
        while (true) {
            val current = Minecraft.getInstance().level?.gameTime ?: break
            if (current >= deadline) break
            Thread.sleep(10)
        }
        return "{\"waited\":$ticks,\"start_tick\":$startTick,\"end_tick\":${Minecraft.getInstance().level?.gameTime}}"
    }
}
