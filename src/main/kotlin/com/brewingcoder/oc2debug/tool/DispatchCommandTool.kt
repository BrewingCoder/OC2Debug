package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject

/**
 * Run a vanilla command as the server console. Catch-all for things we
 * haven't built dedicated tools for: /tp, /give, /time set day, /gamemode,
 * /effect, /weather, etc.
 *
 * Args:
 *   command (string, required) — without the leading "/"
 *
 * Returns: { "result": <command return int> } or error.
 */
object DispatchCommandTool : Tool(
    name = "dispatch_command",
    description = "Execute a vanilla server command (without leading slash). E.g. 'tp @s 0 64 0', 'give @s oc2:computer'.",
    inputSchemaJson = """
        {"type":"object","required":["command"],"properties":{
          "command":{"type":"string"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val cmd = args.strReq("command").trimStart('/')
        return onServer { server ->
            val source = server.createCommandSourceStack().withPermission(4) // op level
            val parseResults = server.commands.dispatcher.parse(cmd, source)
            val result = server.commands.dispatcher.execute(parseResults)
            "{\"command\":\"$cmd\",\"result\":$result}"
        }
    }
}
