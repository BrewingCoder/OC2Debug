package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject

/**
 * Set creative-flight speed by writing directly to the server player's
 * PlayerAbilities and syncing to the client. Works in creative mode.
 *
 * Args:
 *   speed (number, required) — absolute speed value. Default creative = 0.05.
 *                              Use multiplier * 0.05 for relative (e.g. 2x = 0.10).
 *
 * Returns: { "flying_speed": N }
 */
object SetFlySpeedTool : Tool(
    name = "set_fly_speed",
    description = "Set creative-flight speed (default=0.05). Pass 0.10 for 2x, 0.20 for 4x, etc.",
    inputSchemaJson = """
        {"type":"object","required":["speed"],"properties":{
          "speed":{"type":"number","description":"Absolute speed value (default creative = 0.05)"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val speed = args.get("speed").asFloat.coerceIn(0.001f, 1.0f)

        return onServer { server ->
            val player = server.playerList.players.firstOrNull()
                ?: error("No player loaded")
            player.abilities.flyingSpeed = speed
            player.onUpdateAbilities()  // syncs to client
            """{"flying_speed":$speed}"""
        }
    }
}
