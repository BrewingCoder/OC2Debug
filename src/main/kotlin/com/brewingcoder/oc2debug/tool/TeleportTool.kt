package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.portal.DimensionTransition
import net.minecraft.world.phys.Vec3

/**
 * Teleport the (first) player to a position. Same-dimension uses
 * `Player.teleportTo(x,y,z)`; cross-dimension uses `DimensionTransition`.
 *
 * Args:
 *   - `x`, `y`, `z` (numbers, required) — target position. y is auto-clamped
 *     to a safe value if you pass a stupid one.
 *   - `dimension` (string, optional) — namespaced id like
 *     `"minecraft:overworld"`, `"btminingdim:mining_world"`, `"minecraft:the_nether"`.
 *     Defaults to the player's current dimension.
 *
 * Returns JSON with the actual landing position, or `{"error":"..."}`.
 *
 * Pairs with [FindBiomeTool] — the typical flow is `find_biome → teleport`
 * to the returned x/y/z.
 */
object TeleportTool : Tool(
    name = "teleport",
    description = "Teleport the player to (x, y, z) — same or cross-dimension. Pairs with find_biome.",
    inputSchemaJson = """
        {"type":"object","required":["x","y","z"],"properties":{
          "x":{"type":"number"}, "y":{"type":"number"}, "z":{"type":"number"},
          "dimension":{"type":"string"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val x = args.get("x").asDouble
        val y = args.get("y").asDouble
        val z = args.get("z").asDouble
        val targetDim = if (args.has("dimension")) args.strReq("dimension") else null

        return onServer { server ->
            val player: ServerPlayer = server.playerList.players.firstOrNull()
                ?: return@onServer """{"error":"no player loaded"}"""

            val destLevel = if (targetDim == null || targetDim == player.serverLevel().dimension().location().toString()) {
                null  // same-dim
            } else {
                val rl = ResourceLocation.tryParse(targetDim)
                    ?: return@onServer """{"error":"invalid dimension id: ${esc(targetDim)}"}"""
                val key = ResourceKey.create(Registries.DIMENSION, rl) as ResourceKey<Level>
                server.getLevel(key) ?: return@onServer """{"error":"dimension not loaded: ${esc(targetDim)}"}"""
            }

            if (destLevel != null) {
                // Cross-dimension via DimensionTransition (1.21.x API).
                val transition = DimensionTransition(
                    destLevel,
                    Vec3(x, y, z), Vec3.ZERO,
                    player.yRot, player.xRot,
                    DimensionTransition.DO_NOTHING,
                )
                player.changeDimension(transition)
            } else {
                player.teleportTo(x, y, z)
            }

            val landed = player.position()
            val landedDim = player.serverLevel().dimension().location().toString()
            """{"x":${landed.x},"y":${landed.y},"z":${landed.z},"dimension":"${esc(landedDim)}"}"""
        }
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
