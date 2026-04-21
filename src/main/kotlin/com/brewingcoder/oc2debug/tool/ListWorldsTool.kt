package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.client.Minecraft

/**
 * List all single-player worlds (saves/) along with display name, last-played
 * timestamp, gamemode, and whether commands (cheats) are enabled.
 *
 * Returns:
 *   { "worlds": [ { "id":"MyWorld", "name":"My World", "last_played":1714000000000,
 *                   "gamemode":"CREATIVE", "hardcore":false, "commands":true }, ... ] }
 */
object ListWorldsTool : Tool(
    name = "list_worlds",
    description = "List all single-player worlds (saves directory). Returns id, display name, last played, gamemode, hardcore flag.",
    inputSchemaJson = """{"type":"object","properties":{}}""",
) {
    override fun invoke(args: JsonObject): String {
        val mc = Minecraft.getInstance()
        // findLevelCandidates() is sync file I/O; loadLevelSummaries() runs on background threads
        val candidates = mc.levelSource.findLevelCandidates()
        val summaries = mc.levelSource.loadLevelSummaries(candidates).get()

        val sb = StringBuilder("[")
        var first = true
        for (s in summaries) {
            if (!first) sb.append(",")
            first = false
            val id   = s.levelId.replace("\"", "\\\"")
            val name = s.levelName.replace("\"", "\\\"")
            val gm   = s.gameMode.getName().uppercase()
            sb.append(
                """{"id":"$id","name":"$name","last_played":${s.lastPlayed},""" +
                """"gamemode":"$gm","hardcore":${s.isHardcore},"commands":${s.hasCommands()}}"""
            )
        }
        sb.append("]")
        return """{"worlds":$sb}"""
    }
}
