package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject

/**
 * Drop a script (or any text) into an OC2 Computer's filesystem. Saves the
 * tester from `cp`-ing into the world save directory. Reflective into
 * `com.brewingcoder.oc2.diag.ServerLoadedComputers`.
 *
 * Args:
 *   - `id`      (int, required)    — computer id
 *   - `path`    (string, required) — target path within the computer's mount root, e.g. `"inv_test.lua"`
 *   - `content` (string, required) — file contents, UTF-8
 *
 * Returns JSON: `{"id":0, "path":"inv_test.lua", "bytes":123}` on success;
 * `{"error":"..."}` on failure (computer not loaded, mount full, etc.).
 */
object WriteComputerFileTool : Tool(
    name = "write_computer_file",
    description = "Write [content] to [path] inside an OC2 Computer's filesystem (relative to mount root).",
    inputSchemaJson = """
        {"type":"object","required":["id","path","content"],"properties":{
          "id":{"type":"integer"},
          "path":{"type":"string"},
          "content":{"type":"string"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val id = args.intReq("id")
        val path = args.strReq("path")
        val content = args.strReq("content")
        return onServer { _ ->
            val target = try {
                Class.forName(REGISTRY_CLASS).getField("INSTANCE").get(null)
            } catch (_: Throwable) {
                return@onServer """{"error":"OC2 not loaded"}"""
            }
            try {
                target.javaClass.getMethod(
                    "writeFile",
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    String::class.java,
                ).invoke(target, id, path, content)
                """{"id":$id,"path":"${esc(path)}","bytes":${content.toByteArray(Charsets.UTF_8).size}}"""
            } catch (e: Throwable) {
                val cause = e.cause ?: e
                """{"error":"${esc(cause.message ?: cause::class.java.simpleName)}"}"""
            }
        }
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private const val REGISTRY_CLASS = "com.brewingcoder.oc2.diag.ServerLoadedComputers"
}
