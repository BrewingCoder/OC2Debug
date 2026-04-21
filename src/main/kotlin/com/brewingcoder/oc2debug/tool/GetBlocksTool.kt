package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries

/**
 * Dump the full block map of a bounding box as a positional array.
 * Unlike [ScanAreaTool] (which returns a histogram), this returns every
 * block's position — the complete voxel data needed to understand or
 * replicate an existing structure.
 *
 * Args:
 *   - `x1`,`y1`,`z1`, `x2`,`y2`,`z2`  (int, required) — region corners (any order)
 *   - `exclude_air`   (bool, optional, default true)  — skip air blocks
 *   - `include_state` (bool, optional, default false) — include blockstate properties
 *   - `max_results`   (int, optional, default 10 000) — cap to avoid huge payloads
 *
 * Returns JSON:
 *   `{"bounds":{"x1":...},"count":N,"blocks":[{"x":X,"y":Y,"z":Z,"block":"mod:name"[,"state":{...}]},...]}`
 *   `"truncated":true` is added at the top level if the cap was hit.
 */
object GetBlocksTool : Tool(
    name = "get_blocks",
    description = "Dump the full positional block map of a bounding box as [{x,y,z,block},...]. Use to read an existing structure completely before extending, replicating, or analysing it.",
    inputSchemaJson = """
        {"type":"object","required":["x1","y1","z1","x2","y2","z2"],"properties":{
          "x1":{"type":"integer"},"y1":{"type":"integer"},"z1":{"type":"integer"},
          "x2":{"type":"integer"},"y2":{"type":"integer"},"z2":{"type":"integer"},
          "exclude_air":{"type":"boolean"},
          "include_state":{"type":"boolean"},
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

        val excludeAir = !args.has("exclude_air") || args.get("exclude_air").asBoolean
        val includeState = args.has("include_state") && args.get("include_state").asBoolean
        val maxResults = args.intOr("max_results", 10_000).coerceIn(1, 100_000)

        return onServer { server ->
            val player = server.playerList.players.firstOrNull()
                ?: return@onServer """{"error":"no player loaded"}"""
            val level = player.serverLevel()

            val sb = StringBuilder()
            sb.append("{\"bounds\":{\"x1\":").append(x1).append(",\"y1\":").append(y1).append(",\"z1\":").append(z1)
            sb.append(",\"x2\":").append(x2).append(",\"y2\":").append(y2).append(",\"z2\":").append(z2).append("},")

            var count = 0
            var truncated = false
            val pos = BlockPos.MutableBlockPos()
            val blocksSb = StringBuilder("[")

            outer@ for (x in x1..x2) {
                for (y in y1..y2) {
                    for (z in z1..z2) {
                        pos.set(x, y, z)
                        val state = level.getBlockState(pos)
                        val id = BuiltInRegistries.BLOCK.getKey(state.block).toString()

                        if (excludeAir && isAir(id)) continue

                        if (count >= maxResults) {
                            truncated = true
                            break@outer
                        }
                        if (count > 0) blocksSb.append(",")
                        blocksSb.append("{\"x\":").append(x)
                            .append(",\"y\":").append(y)
                            .append(",\"z\":").append(z)
                            .append(",\"block\":\"").append(esc(id)).append("\"")

                        if (includeState && !state.values.isEmpty()) {
                            blocksSb.append(",\"state\":{")
                            var first = true
                            @Suppress("UNCHECKED_CAST")
                            for ((prop, v) in state.values.entries) {
                                if (!first) blocksSb.append(",")
                                val propName = prop.name
                                val valName = (prop as net.minecraft.world.level.block.state.properties.Property<Comparable<Any>>)
                                    .getName(v as Comparable<Any>)
                                blocksSb.append("\"").append(esc(propName)).append("\":\"").append(esc(valName)).append("\"")
                                first = false
                            }
                            blocksSb.append("}")
                        }

                        blocksSb.append("}")
                        count++
                    }
                }
            }

            blocksSb.append("]")
            sb.append("\"count\":").append(count).append(",")
            if (truncated) sb.append("\"truncated\":true,")
            sb.append("\"blocks\":").append(blocksSb)
            sb.append("}")
            sb.toString()
        }
    }

    private fun isAir(id: String) = id == "minecraft:air" || id == "minecraft:cave_air" || id == "minecraft:void_air"
    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
