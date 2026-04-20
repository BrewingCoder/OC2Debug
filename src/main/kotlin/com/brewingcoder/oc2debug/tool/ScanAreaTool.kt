package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.BuiltInRegistries

/**
 * Like [ScanChunkTool] but aggregates across an N×N chunk area centered on
 * the origin chunk. Designed for cross-dimension ore-distribution surveys —
 * scan a region in the overworld, scan the same-shape region in another
 * dimension, compare the histograms.
 *
 * Args:
 *   - `from`            (object {x,y,z}, optional)  — center; defaults to player
 *   - `chunk_radius`    (int, optional, default 1)  — Chebyshev radius. r=1 → 3×3 (9 chunks);
 *                       r=2 → 5×5 (25 chunks); r=3 → 7×7 (49 chunks). Bigger = slower.
 *   - `y_min`           (int, optional, default level minBuildHeight)
 *   - `y_max`           (int, optional, default `from.y - 1`)
 *   - `exclude_pattern` (string, optional regex)  — skip matching block ids
 *   - `air`             (bool, optional, default false)  — include `*_air` types
 *
 * Returns JSON:
 *   `{"center_chunk":{"x":CX,"z":CZ},"chunks_scanned":N,"y_range":[YMIN,YMAX],"total":N,"blocks":{...}}`
 *
 * Performance scales with `chunk_radius²`. r=1 is ~300k getBlockState calls;
 * a few hundred ms server-thread. r=3 (49 chunks) can push 1.5M calls — heavy.
 */
object ScanAreaTool : Tool(
    name = "scan_area",
    description = "N×N chunk histogram of block types. Bigger sample size than scan_chunk; great for cross-dimension comparisons.",
    inputSchemaJson = """
        {"type":"object","properties":{
          "from":{"type":"object","properties":{
            "x":{"type":"integer"},"y":{"type":"integer"},"z":{"type":"integer"}
          }},
          "chunk_radius":{"type":"integer"},
          "y_min":{"type":"integer"},
          "y_max":{"type":"integer"},
          "exclude_pattern":{"type":"string"},
          "air":{"type":"boolean"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val excludeRe = if (args.has("exclude_pattern")) Regex(args.strReq("exclude_pattern")) else null
        val includeAir = if (args.has("air")) args.get("air").asBoolean else false
        val radius = args.intOr("chunk_radius", 1).coerceIn(0, 6)

        return onServer { server ->
            val player = server.playerList.players.firstOrNull()
                ?: return@onServer """{"error":"no player loaded"}"""
            val level = player.serverLevel()

            val origin: BlockPos = if (args.has("from")) {
                val f = args.getAsJsonObject("from")
                BlockPos(f.intReq("x"), f.intReq("y"), f.intReq("z"))
            } else {
                player.blockPosition()
            }
            val yMin = args.intOr("y_min", level.minBuildHeight)
            val yMax = args.intOr("y_max", origin.y - 1).coerceAtMost(level.maxBuildHeight - 1)

            val centerCX = SectionPos.blockToSectionCoord(origin.x)
            val centerCZ = SectionPos.blockToSectionCoord(origin.z)

            val counts = HashMap<String, Int>()
            var total = 0
            var chunksScanned = 0
            val pos = BlockPos.MutableBlockPos()
            for (dcx in -radius..radius) {
                for (dcz in -radius..radius) {
                    val chunk = level.getChunk(centerCX + dcx, centerCZ + dcz)
                    chunksScanned++
                    val baseX = chunk.pos.x * 16
                    val baseZ = chunk.pos.z * 16
                    for (dx in 0 until 16) {
                        for (dz in 0 until 16) {
                            for (y in yMin..yMax) {
                                pos.set(baseX + dx, y, baseZ + dz)
                                val state = chunk.getBlockState(pos)
                                val id = BuiltInRegistries.BLOCK.getKey(state.block).toString()
                                if (!includeAir && (id == "minecraft:air" || id == "minecraft:cave_air" || id == "minecraft:void_air")) continue
                                if (excludeRe != null && excludeRe.containsMatchIn(id)) continue
                                counts[id] = (counts[id] ?: 0) + 1
                                total++
                            }
                        }
                    }
                }
            }

            val sorted = counts.entries.sortedByDescending { it.value }
            val sb = StringBuilder()
            sb.append("{\"center_chunk\":{\"x\":").append(centerCX).append(",\"z\":").append(centerCZ).append("},")
            sb.append("\"chunks_scanned\":").append(chunksScanned).append(",")
            sb.append("\"y_range\":[").append(yMin).append(",").append(yMax).append("],")
            sb.append("\"total\":").append(total).append(",")
            sb.append("\"blocks\":{")
            for ((i, e) in sorted.withIndex()) {
                if (i > 0) sb.append(",")
                sb.append("\"").append(esc(e.key)).append("\":").append(e.value)
            }
            sb.append("}}")
            sb.toString()
        }
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
