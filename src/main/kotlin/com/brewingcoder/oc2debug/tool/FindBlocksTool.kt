package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries

/**
 * Scan a bounding box and return the position of every block matching the
 * supplied filter.  Designed for "where are the edges of this structure I
 * laid out?" and similar spatial queries.
 *
 * Args:
 *   - `x1`,`y1`,`z1`, `x2`,`y2`,`z2`  (int, required) — region corners (any order)
 *   - `block_types`  (array of string, optional) — if supplied, only return blocks
 *       whose ID contains at least one of these substrings (case-insensitive).
 *       Omit to return ALL non-air blocks.
 *   - `include_air`  (bool, optional, default false) — include air in results
 *   - `max_results`  (int, optional, default 10 000) — cap to avoid huge payloads
 *
 * Returns JSON array:
 *   `[{"x":X,"y":Y,"z":Z,"block":"mod:name"},...]`
 *   with a trailing `{"truncated":true}` object if the cap was hit.
 *
 * For large regions the scan runs async; the response is returned synchronously
 * only because multiblock structures are typically small (< 10k blocks).
 */
object FindBlocksTool : Tool(
    name = "find_blocks",
    description = "Scan a bounding box and return positions of blocks matching a type filter. Returns [{x,y,z,block},...]. Use to locate existing structure edges/corners before completing or extending a build.",
    inputSchemaJson = """
        {"type":"object","required":["x1","y1","z1","x2","y2","z2"],"properties":{
          "x1":{"type":"integer"},"y1":{"type":"integer"},"z1":{"type":"integer"},
          "x2":{"type":"integer"},"y2":{"type":"integer"},"z2":{"type":"integer"},
          "block_types":{"type":"array","items":{"type":"string"}},
          "include_air":{"type":"boolean"},
          "max_results":{"type":"integer"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val x1 = minOf(args.intReq("x1"), args.intReq("x2"))
        val x2 = maxOf(args.intReq("x1"), args.intReq("x2"))
        val y1 = minOf(args.intReq("y1"), args.intReq("y2"))
        val y2 = maxOf(args.intReq("y1"), args.intReq("y2"))
        val z1 = minOf(args.intReq("z1"), args.intReq("z2"))
        val z2 = maxOf(args.intReq("z1"), args.intReq("z2"))

        val filters: List<String> = if (args.has("block_types")) {
            args.getAsJsonArray("block_types").map { it.asString.lowercase() }
        } else emptyList()

        val includeAir = args.has("include_air") && args.get("include_air").asBoolean
        val maxResults = args.intOr("max_results", 10_000).coerceIn(1, 100_000)

        return onServer { server ->
            val player = server.playerList.players.firstOrNull()
                ?: return@onServer """{"error":"no player loaded"}"""
            val level = player.serverLevel()

            val sb = StringBuilder("[")
            var count = 0
            var truncated = false
            val pos = BlockPos.MutableBlockPos()

            outer@ for (x in x1..x2) {
                for (y in y1..y2) {
                    for (z in z1..z2) {
                        pos.set(x, y, z)
                        val state = level.getBlockState(pos)
                        val id = BuiltInRegistries.BLOCK.getKey(state.block).toString()

                        if (!includeAir && isAir(id)) continue
                        if (filters.isNotEmpty() && filters.none { id.contains(it) }) continue

                        if (count >= maxResults) {
                            truncated = true
                            break@outer
                        }
                        if (count > 0) sb.append(",")
                        sb.append("{\"x\":").append(x)
                            .append(",\"y\":").append(y)
                            .append(",\"z\":").append(z)
                            .append(",\"block\":\"").append(esc(id)).append("\"}")
                        count++
                    }
                }
            }

            if (truncated) {
                if (count > 0) sb.append(",")
                sb.append("{\"truncated\":true,\"max_results\":").append(maxResults).append("}")
            }
            sb.append("]")
            sb.toString()
        }
    }

    private fun isAir(id: String) = id == "minecraft:air" || id == "minecraft:cave_air" || id == "minecraft:void_air"
    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
