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
 *   - `biome`        (string, required)  — `"minecraft:plains"`, `"biomesoplenty:lush_grassland"`, etc.
 *   - `radius`       (int, optional, default 6400)  — search radius in blocks
 *   - `from`         (object {x,y,z}, optional)  — search origin; defaults to the player
 *   - `deep_search`  (bool, optional, default false) — also search underground vertically (slower)
 *
 * Returns JSON:
 *   `{"found":true, "x":..., "y":..., "z":..., "biome":"<id>"}` on hit
 *   `{"found":false, "biome":"<id>", "radius":N}` if not within radius
 *   `{"error":"<reason>"}` if the biome id is unknown / no player loaded
 *
 * Cost: default mode samples every 32 blocks horizontally, once vertically (surface biomes
 * only). deep_search adds 6 vertical levels. 6400 radius ≈ Nature's Compass default;
 * returns in a few seconds at that radius.
 */
object FindBiomeTool : Tool(
    name = "find_biome",
    description = "Find the nearest position with a matching biome. Like Nature's Compass — for the AI.",
    inputSchemaJson = """
        {"type":"object","required":["biome"],"properties":{
          "biome":{"type":"string"},
          "radius":{"type":"integer"},
          "deep_search":{"type":"boolean"},
          "from":{"type":"object","properties":{
            "x":{"type":"integer"},"y":{"type":"integer"},"z":{"type":"integer"}
          }}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val biomeIdStr = args.strReq("biome")
        val radius = args.intOr("radius", 6400)
        val deepSearch = args.has("deep_search") && args.get("deep_search").asBoolean

        // Validate biome id and capture origin synchronously on the server thread,
        // then hand the long-running search off to an async job so the HTTP request
        // returns immediately instead of timing out.
        data class SearchParams(
            val level: net.minecraft.server.level.ServerLevel,
            val origin: BlockPos,
            val targetHolder: Holder<Biome>,
            val deepSearch: Boolean,
        )

        val params = try {
            onServer { server ->
                val player = server.playerList.players.firstOrNull()
                    ?: error("no player loaded")
                val level = player.serverLevel()
                val biomeRl = ResourceLocation.tryParse(biomeIdStr)
                    ?: error("invalid biome id: $biomeIdStr")
                val biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME)
                val biomeKey = biomeRegistry.getResourceKey(
                    biomeRegistry.get(biomeRl) ?: error("unknown biome: $biomeIdStr")
                ).orElseThrow()
                val origin: BlockPos = if (args.has("from")) {
                    val from = args.getAsJsonObject("from")
                    BlockPos(from.intReq("x"), from.intReq("y"), from.intReq("z"))
                } else {
                    player.blockPosition()
                }
                SearchParams(level, origin, biomeRegistry.getHolderOrThrow(biomeKey), deepSearch)
            }
        } catch (e: Exception) {
            val msg = e.message?.replace("\"", "\\\"") ?: "error"
            return """{"error":"$msg"}"""
        }

        val jobId = AsyncJobRegistry.submit {
            // Surface biomes (the common case) only vary horizontally — one vertical
            // sample per column is enough and makes the search ~6x faster.
            // deep_search adds vertical resolution for cave/underground biomes.
            val vStep = if (params.deepSearch) 64 else 384
            val pair = params.level.findClosestBiome3d(
                { holder: Holder<Biome> -> holder == params.targetHolder },
                params.origin,
                radius,
                32, vStep,
            )
            if (pair == null) {
                """{"found":false,"biome":"${esc(biomeIdStr)}","radius":$radius}"""
            } else {
                val pos = pair.first
                """{"found":true,"x":${pos.x},"y":${pos.y},"z":${pos.z},"biome":"${esc(biomeIdStr)}"}"""
            }
        }
        return """{"status":"searching","job_id":"$jobId","biome":"${esc(biomeIdStr)}","radius":$radius,"deep_search":$deepSearch}"""
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
