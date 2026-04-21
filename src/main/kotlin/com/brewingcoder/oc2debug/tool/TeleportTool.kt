package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.portal.DimensionTransition
import net.minecraft.world.phys.Vec3

/**
 * Teleport the (first) player to a position. Same-dimension uses
 * `Player.teleportTo(x,y,z)`; cross-dimension uses `DimensionTransition`.
 *
 * Args:
 *   - `x`, `z` (numbers, required) — target XZ position.
 *   - `y` (number, optional) — explicit Y. Omit (or pass null) to auto-surface.
 *   - `surface` (boolean, optional, default true) — when true and y is omitted,
 *     scans downward from y=320 to find the first solid block and lands 2 above it.
 *   - `dimension` (string, optional) — e.g. `"minecraft:overworld"`.
 *
 * Returns JSON with the actual landing position, or `{"error":"..."}`.
 */
object TeleportTool : Tool(
    name = "teleport",
    description = "Teleport the player to (x, y, z). Omit y to auto-land on the surface.",
    inputSchemaJson = """
        {"type":"object","required":["x","z"],"properties":{
          "x":{"type":"number"}, "y":{"type":"number"}, "z":{"type":"number"},
          "surface":{"type":"boolean"},
          "dimension":{"type":"string"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val x = args.get("x").asDouble
        val z = args.get("z").asDouble
        val explicitY = if (args.has("y") && !args.get("y").isJsonNull) args.get("y").asDouble else null
        val autoSurface = if (args.has("surface")) args.get("surface").asBoolean else (explicitY == null)
        val targetDim = if (args.has("dimension")) args.strReq("dimension") else null

        return onServer { server ->
            val player: ServerPlayer = server.playerList.players.firstOrNull()
                ?: return@onServer """{"error":"no player loaded"}"""

            val destLevel: ServerLevel? = if (targetDim == null || targetDim == player.serverLevel().dimension().location().toString()) {
                null
            } else {
                val rl = ResourceLocation.tryParse(targetDim)
                    ?: return@onServer """{"error":"invalid dimension id: ${esc(targetDim)}"}"""
                val key = ResourceKey.create(Registries.DIMENSION, rl) as ResourceKey<Level>
                server.getLevel(key) ?: return@onServer """{"error":"dimension not loaded: ${esc(targetDim)}"}"""
            }

            val level = destLevel ?: player.serverLevel()

            val y: Double = if (autoSurface) {
                findSurface(level, x.toInt(), z.toInt(), explicitY?.toInt() ?: 320)
            } else {
                explicitY ?: 64.0
            }

            if (destLevel != null) {
                val transition = DimensionTransition(
                    destLevel, Vec3(x, y, z), Vec3.ZERO,
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

    // Scans downward from startY to find the first solid block; returns 2 above it.
    private fun findSurface(level: ServerLevel, x: Int, z: Int, startY: Int = 320): Double {
        var prevAir = true
        for (y in startY downTo -60) {
            val block = level.getBlockState(BlockPos(x, y, z))
            val isAir = block.isAir
            if (prevAir && !isAir) return (y + 2).toDouble()
            prevAir = isAir
        }
        return 64.0  // fallback to sea level
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
