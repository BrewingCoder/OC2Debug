package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject

/**
 * Execute one shell command on an OC2 Computer's diagnostic shell. Pairs with
 * [WriteComputerFileTool] for the full "drop a script + run it" loop.
 *
 * Note: scripts spawned via `run` are async. This tool returns the immediate
 * shell output (e.g., `started 'foo.lua' (pid=N)`). To see actual script
 * output, wait a beat then call [ReadComputerConsoleTool].
 *
 * Args:
 *   - `id`      (int, required)
 *   - `command` (string, required) — full shell line, e.g. `"run inv_test.lua"`
 *
 * Returns JSON: `{"id":0,"lines":["...","..."]}` or `{"error":"..."}`.
 */
object RunComputerCommandTool : Tool(
    name = "run_computer_command",
    description = "Run a shell command on an OC2 Computer; returns the synchronous output. Async script output streams to read_computer_console.",
    inputSchemaJson = """
        {"type":"object","required":["id","command"],"properties":{
          "id":{"type":"integer"},
          "command":{"type":"string"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val id = args.intReq("id")
        val cmd = args.strReq("command")
        return onServer { _ ->
            val target = try {
                Class.forName(REGISTRY_CLASS).getField("INSTANCE").get(null)
            } catch (_: Throwable) {
                return@onServer """{"error":"OC2 not loaded"}"""
            }
            try {
                @Suppress("UNCHECKED_CAST")
                val lines = target.javaClass.getMethod(
                    "executeCommand",
                    Int::class.javaPrimitiveType,
                    String::class.java,
                ).invoke(target, id, cmd) as List<String>
                buildString {
                    append("{\"id\":").append(id).append(",\"lines\":[")
                    for ((i, line) in lines.withIndex()) {
                        if (i > 0) append(",")
                        append("\"").append(esc(line)).append("\"")
                    }
                    append("]}")
                }
            } catch (e: Throwable) {
                val cause = e.cause ?: e
                """{"error":"${esc(cause.message ?: cause::class.java.simpleName)}"}"""
            }
        }
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    private const val REGISTRY_CLASS = "com.brewingcoder.oc2.diag.ServerLoadedComputers"
}
