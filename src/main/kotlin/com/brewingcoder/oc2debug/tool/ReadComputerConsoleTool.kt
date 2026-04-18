package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject

/**
 * Return the recent script-output ring buffer for an OC2 Computer. Saves the
 * tester from opening every computer's GUI to confirm a script ran or read
 * its output. Reflective into `com.brewingcoder.oc2.diag.ServerLoadedComputers`.
 *
 * Args:
 *   - `id` (int, required) — computer id from [ListComputersTool]
 *   - `tail` (int, optional) — only the last N lines (default: all retained)
 *
 * Returns JSON:
 * ```
 * {"id": 0, "lines": ["line1", "line2", ...]}
 * ```
 *
 * If the computer isn't loaded, returns `{"id": 0, "lines": null}`.
 */
object ReadComputerConsoleTool : Tool(
    name = "read_computer_console",
    description = "Read the recent script-output ring buffer for the OC2 Computer with the given id.",
    inputSchemaJson = """
        {"type":"object","required":["id"],"properties":{
          "id":{"type":"integer"},
          "tail":{"type":"integer"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val id = args.intReq("id")
        val tail = if (args.has("tail")) args.get("tail").asInt else 0
        return onServer { _ ->
            val target = try {
                Class.forName(REGISTRY_CLASS).getField("INSTANCE").get(null)
            } catch (_: Throwable) {
                return@onServer """{"id":$id,"lines":null,"error":"OC2 not loaded"}"""
            }
            @Suppress("UNCHECKED_CAST")
            val raw = target.javaClass.getMethod("consoleOf", Int::class.javaPrimitiveType)
                .invoke(target, id) as List<String>?
            if (raw == null) return@onServer """{"id":$id,"lines":null}"""
            val lines = if (tail > 0 && raw.size > tail) raw.takeLast(tail) else raw
            buildString {
                append("{\"id\":").append(id).append(",\"lines\":[")
                for ((i, line) in lines.withIndex()) {
                    if (i > 0) append(",")
                    append("\"").append(esc(line)).append("\"")
                }
                append("]}")
            }
        }
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    private const val REGISTRY_CLASS = "com.brewingcoder.oc2.diag.ServerLoadedComputers"
}
