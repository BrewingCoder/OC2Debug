package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.BuiltInRegistries

/**
 * Histogram of block types in a single chunk, between [y_min] and [y_max].
 * One round trip; iterates all 16×16×(y_max-y_min+1) positions inside the
 * server's chunk and aggregates by namespaced block id.
 *
 * Args:
 *   - `from`           (object {x,y,z}, optional)  — origin; defaults to the player
 *   - `y_min`          (int, optional, default -64) — inclusive
 *   - `y_max`          (int, optional, default `from.y - 1`)  — inclusive (i.e. "below me")
 *   - `exclude_pattern` (string, optional regex)  — skip blocks whose id matches.
 *     Example: `"stone|deepslate|granite|diorite|andesite|tuff|cobblestone|gravel|dirt"`
 *     filters out the boring stuff to surface ore-tier finds.
 *   - `air`            (bool, optional, default false) — include `minecraft:air` and `cave_air`
 *
 * Returns JSON:
 *   `{"chunk":{"x":CX,"z":CZ},"y_range":[YMIN,YMAX],"total":N,"blocks":{"<id>":COUNT,...}}`
 *
 * Performance: ~16*16*128 = ~33k getBlockState calls. Single server-thread
 * round trip, ~10-50ms typical.
 */
object ScanChunkTool : Tool(
    name = "scan_chunk",
    description = "Histogram of block types in the player's (or specified) chunk. Filter via regex; great for ore/material surveys.",
    inputSchemaJson = """
        {"type":"object","properties":{
          "from":{"type":"object","properties":{
            "x":{"type":"integer"},"y":{"type":"integer"},"z":{"type":"integer"}
          }},
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

            val chunk = level.getChunk(SectionPos.blockToSectionCoord(origin.x), SectionPos.blockToSectionCoord(origin.z))
            val cx = chunk.pos.x
            val cz = chunk.pos.z
            val baseX = cx * 16
            val baseZ = cz * 16

            val counts = HashMap<String, Int>()
            var total = 0
            val pos = BlockPos.MutableBlockPos()
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

            // Sort blocks by count desc + serialize.
            val sorted = counts.entries.sortedByDescending { it.value }
            val sb = StringBuilder()
            sb.append("{\"chunk\":{\"x\":").append(cx).append(",\"z\":").append(cz).append("},")
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
