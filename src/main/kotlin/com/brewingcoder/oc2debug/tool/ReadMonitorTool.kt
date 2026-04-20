package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * Read the master monitor's text buffer. Critical for "what does the monitor
 * THINK it should display?" debugging — the screenshot shows what the renderer
 * draws, this shows what the BE buffer actually contains.
 *
 * Args:
 *   - `x`, `y`, `z` (int, required) — any block in the group; resolves to master.
 *
 * Returns:
 *   ```json
 *   {
 *     "master": {"x":-831,"y":81,"z":-745},
 *     "channel": "Reactor",
 *     "groupBlocks": [4, 3],
 *     "size": [80, 27],
 *     "cursor": [0, 6],
 *     "lines": [
 *       "table: 6fd4f756                                ...",
 *       "table: 6fd4f756                                ..."
 *     ]
 *   }
 *   ```
 */
object ReadMonitorTool : Tool(
    name = "read_monitor",
    description = "Dump the master monitor BE's text buffer at (x,y,z) — shows what the BE thinks should display.",
    inputSchemaJson = """
        {"type":"object","required":["x","y","z"],"properties":{
          "x":{"type":"integer"},"y":{"type":"integer"},"z":{"type":"integer"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val x = args.intReq("x"); val y = args.intReq("y"); val z = args.intReq("z")
        return onServer { server ->
            val player = server.playerList.players.firstOrNull()
                ?: return@onServer """{"error":"no player loaded"}"""
            val level = player.serverLevel()
            val be = level.getBlockEntity(BlockPos(x, y, z))
                ?: return@onServer """{"error":"no block entity at ($x,$y,$z)"}"""

            // Reflective access — oc2-debug doesn't depend on OC2 directly, so we
            // can't import MonitorBlockEntity. Pull what we need by field name.
            val cls = be.javaClass
            if (cls.name != "com.brewingcoder.oc2.block.MonitorBlockEntity") {
                return@onServer """{"error":"not an OC2 monitor: ${esc(cls.name)}"}"""
            }
            // Resolve master if this BE is a slave.
            val isMaster = field(be, "isMaster") as? Boolean ?: false
            val master: Any = if (isMaster) be else {
                val mp = field(be, "masterPos") as? BlockPos
                    ?: return@onServer """{"error":"slave with no masterPos"}"""
                level.getBlockEntity(mp)
                    ?: return@onServer """{"error":"master BE not loaded at $mp"}"""
            }
            val mPos = (master as BlockEntity).blockPos
            val channel = field(master, "channelId") as? String ?: "?"
            val gw = field(master, "groupBlocksWide") as? Int ?: 0
            val gh = field(master, "groupBlocksTall") as? Int ?: 0
            val cur = field(master, "cursorRow") as? Int ?: -1
            val curCol = field(master, "cursorCol") as? Int ?: -1
            val buffer = field(master, "buffer") as? Array<*>

            val sb = StringBuilder()
            sb.append("{\"master\":{\"x\":${mPos.x},\"y\":${mPos.y},\"z\":${mPos.z}},")
            sb.append("\"channel\":\"${esc(channel)}\",")
            sb.append("\"groupBlocks\":[$gw,$gh],")
            val totalCols = if (buffer != null && buffer.isNotEmpty()) (buffer[0] as CharArray).size else 0
            val totalRows = buffer?.size ?: 0
            sb.append("\"size\":[$totalCols,$totalRows],")
            sb.append("\"cursor\":[$curCol,$cur],")
            sb.append("\"lines\":[")
            if (buffer != null) {
                for ((i, row) in buffer.withIndex()) {
                    if (i > 0) sb.append(",")
                    val text = String(row as CharArray)
                    sb.append("\"").append(esc(text)).append("\"")
                }
            }
            sb.append("]}")
            sb.toString()
        }
    }

    private fun field(target: Any, name: String): Any? {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            try {
                val f = cls.getDeclaredField(name)
                f.isAccessible = true
                return f.get(target)
            } catch (_: NoSuchFieldException) { cls = cls.superclass }
        }
        return null
    }

    private fun esc(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
