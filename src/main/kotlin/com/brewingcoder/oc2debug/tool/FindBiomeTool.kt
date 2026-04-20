package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.biome.Biome

/**
 * Nature's Compass for the AI agent. Finds the nearest position whose biome
 * matches the requested namespaced id.
 *
 * Args:
 *   - `biome`   (string, required)  — `"minecraft:plains"`, `"biomesoplenty:lush_grassland"`, etc.
 *   - `radius`  (int, optional, default 6400)  — search radius in blocks
 *   - `from`    (object {x,y,z}, optional)  — search origin; defaults to the player
 *
 * Returns JSON:
 *   `{"found":true, "x":..., "y":..., "z":..., "biome":"<id>"}` on hit
 *   `{"found":false, "biome":"<id>", "radius":N}` if not within radius
 *   `{"error":"<reason>"}` if the biome id is unknown / no player loaded
 *
 * Cost: scales with radius — vanilla samples every 32 blocks horizontally + 32
 * vertically. 6400 ≈ Nature's Compass default; usually returns within a few
 * hundred ms.
 */
object FindBiomeTool : Tool(
    name = "find_biome",
    description = "Find the nearest position with a matching biome. Like Nature's Compass — for the AI.",
    inputSchemaJson = """
        {"type":"object","required":["biome"],"properties":{
          "biome":{"type":"string"},
          "radius":{"type":"integer"},
          "from":{"type":"object","properties":{
            "x":{"type":"integer"},"y":{"type":"integer"},"z":{"type":"integer"}
          }}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val biomeIdStr = args.strReq("biome")
        val radius = args.intOr("radius", 6400)

        return onServer { server ->
            val player = server.playerList.players.firstOrNull()
                ?: return@onServer """{"error":"no player loaded"}"""
            val level = (player.serverLevel())

            // Resolve the requested biome id into a Holder<Biome> for the predicate.
            val biomeRl = ResourceLocation.tryParse(biomeIdStr)
                ?: return@onServer """{"error":"invalid biome id: $biomeIdStr"}"""
            val biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME)
            val biomeKey = biomeRegistry.getResourceKey(biomeRegistry.get(biomeRl) ?: run {
                return@onServer """{"error":"unknown biome: $biomeIdStr"}"""
            }).orElseThrow()
            val targetHolder = biomeRegistry.getHolderOrThrow(biomeKey)

            // Origin: explicit `from` if given, else player's current block pos.
            val origin: BlockPos = if (args.has("from")) {
                val from = args.getAsJsonObject("from")
                BlockPos(from.intReq("x"), from.intReq("y"), from.intReq("z"))
            } else {
                player.blockPosition()
            }

            // findClosestBiome3d signature: (predicate, origin, radius, horizontalStep, verticalStep)
            val pair = level.findClosestBiome3d(
                { holder: Holder<Biome> -> holder == targetHolder },
                origin,
                radius,
                32, 64,
            )
            if (pair == null) {
                """{"found":false,"biome":"${esc(biomeIdStr)}","radius":$radius}"""
            } else {
                val pos = pair.first
                """{"found":true,"x":${pos.x},"y":${pos.y},"z":${pos.z},"biome":"${esc(biomeIdStr)}"}"""
            }
        }
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
