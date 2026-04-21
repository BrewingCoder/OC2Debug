package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.client.Minecraft

/**
 * Instantly set the player's look direction (yaw + pitch).
 *
 * Args:
 *   yaw   (number, required) — horizontal rotation in degrees; 0=south, 90=west, 180/−180=north, −90=east
 *   pitch (number, required) — vertical tilt in degrees; −90=straight up, 0=horizon, 90=straight down
 *
 * Returns: { "yaw":N, "pitch":N }
 */
object SetLookTool : Tool(
    name = "set_look",
    description = "Set the player's look direction (yaw, pitch in degrees). yaw: 0=south, -90=east; pitch: -90=up, 90=down.",
    inputSchemaJson = """
        {"type":"object","required":["yaw","pitch"],"properties":{
          "yaw":{"type":"number","description":"Horizontal degrees: 0=south,90=west,180=north,-90=east"},
          "pitch":{"type":"number","description":"Vertical degrees: -90=up, 0=horizon, 90=down"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val yaw = args.get("yaw").asFloat
        val pitch = args.get("pitch").asFloat.coerceIn(-90f, 90f)

        return onClient {
            val player = Minecraft.getInstance().player
                ?: error("No local player — load a world first")
            player.yRot = yaw
            player.xRot = pitch
            // Sync "previous" rotation so interpolation doesn't swing through a bad arc
            player.yRotO = yaw
            player.xRotO = pitch
            """{"yaw":$yaw,"pitch":$pitch}"""
        }
    }
}
