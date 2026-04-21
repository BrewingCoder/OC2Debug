package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.client.Minecraft

/**
 * Omnibus snapshot of "what is the game doing right now" — first call for any
 * debug session. Returns ALL non-image state in one shot so callers don't have
 * to chain multiple tools or (worse) screenshot to learn state.
 *
 * Returns JSON like:
 * {
 *   "in_world": true,
 *   "screen": "ComputerScreen",     # null when no Screen open
 *   "tick": 12345,
 *   "dimension": "minecraft:overworld",  # null when no level
 *   "player": {                          # null when no player
 *     "pos": [0.5, 64.0, 0.5],
 *     "look": [0.0, 0.0],                # yaw, pitch
 *     "gamemode": "CREATIVE",
 *     "health": 20.0
 *   },
 *   "integrated_server": true,
 *   "fps": 60,
 *   "window": [1900, 1280]
 * }
 */
object GameStateTool : Tool(
    name = "game_state",
    description = "Single JSON snapshot of game state (in-world flag, current Screen, tick, dimension, player pos/gamemode/health, FPS, window size). Use this BEFORE reaching for a screenshot when you just need to know state.",
    inputSchemaJson = """{"type":"object","properties":{}}""",
) {
    override fun invoke(args: JsonObject): String {
        return onClient {
            val mc = Minecraft.getInstance()
            val level = mc.level
            val player = mc.player
            val screen = mc.screen?.javaClass?.simpleName

            val playerJson: String = if (player != null) {
                """{"pos":[${player.x},${player.y},${player.z}],""" +
                """"look":[${player.yRot},${player.xRot}],""" +
                """"gamemode":"${mc.gameMode?.playerMode?.name ?: "UNKNOWN"}",""" +
                """"health":${player.health}}"""
            } else "null"

            val dim = level?.dimension()?.location()?.toString()

            val oc2Build = System.getProperty("oc2.buildId", "unknown")
            buildString {
                append("{")
                append("\"in_world\":${level != null},")
                append("\"screen\":${screen?.let { "\"$it\"" } ?: "null"},")
                append("\"tick\":${level?.gameTime ?: -1L},")
                append("\"dimension\":${dim?.let { "\"$it\"" } ?: "null"},")
                append("\"player\":$playerJson,")
                append("\"integrated_server\":${mc.singleplayerServer != null},")
                append("\"fps\":${mc.fps},")
                append("\"window\":[${mc.window.guiScaledWidth},${mc.window.guiScaledHeight}],")
                append("\"oc2_build\":\"$oc2Build\"")
                append("}")
            }
        }
    }
}
