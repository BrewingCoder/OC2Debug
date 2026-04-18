package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject

/**
 * Enumerate every loaded OC2 Computer block. Reflective lookup into
 * `com.brewingcoder.oc2.diag.ServerLoadedComputers` — no hard dependency
 * on OC2, so this tool degrades to an empty list when OC2 isn't present.
 *
 * Returns JSON array:
 * ```
 * [{"id":0,"dimension":"minecraft:overworld","x":94,"y":72,"z":189,"channelId":"default"}, ...]
 * ```
 *
 * Use this before [ReadComputerConsoleTool] to find the id you want.
 */
object ListComputersTool : Tool(
    name = "list_computers",
    description = "List every currently-loaded OC2 Computer (id, dimension, position, wifi channel).",
    inputSchemaJson = """{"type":"object","properties":{},"additionalProperties":false}""",
) {
    override fun invoke(args: JsonObject): String = onServer { _ ->
        val cls = try {
            Class.forName(REGISTRY_CLASS).getField("INSTANCE").get(null)
        } catch (_: Throwable) {
            return@onServer "[]"
        }
        @Suppress("UNCHECKED_CAST")
        val list = cls.javaClass.getMethod("list").invoke(cls) as List<Any>
        buildString {
            append("[")
            for ((i, info) in list.withIndex()) {
                if (i > 0) append(",")
                append("{")
                append("\"id\":").append(field(info, "getId"))
                append(",\"dimension\":\"").append(esc(field(info, "getDimension"))).append("\"")
                append(",\"x\":").append(field(info, "getX"))
                append(",\"y\":").append(field(info, "getY"))
                append(",\"z\":").append(field(info, "getZ"))
                append(",\"channelId\":\"").append(esc(field(info, "getChannelId"))).append("\"")
                append("}")
            }
            append("]")
        }
    }

    private fun field(target: Any, getter: String): String =
        target.javaClass.getMethod(getter).invoke(target).toString()

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private const val REGISTRY_CLASS = "com.brewingcoder.oc2.diag.ServerLoadedComputers"
}
