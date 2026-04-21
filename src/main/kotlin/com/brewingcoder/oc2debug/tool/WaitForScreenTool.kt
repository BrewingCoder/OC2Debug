package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.client.Minecraft

/**
 * Block until the current screen matches the expected state.
 * The primary use case is sequencing after load_world, save_and_quit, or
 * click_ui_button — each of which triggers an async screen transition.
 *
 * Args:
 *   screen      (string|null, optional) — simple class name to wait for
 *               (e.g. "TitleScreen", "SelectWorldScreen"), OR omit/null
 *               to wait until NO screen is open (i.e. fully in-game).
 *   timeout_ms  (int, optional, default 30000)
 *
 * Returns:
 *   { "ok":true, "screen":"TitleScreen", "elapsed_ms":N }
 *   { "error":"timeout", "wanted":"TitleScreen", "actual":"PauseScreen", "elapsed_ms":N }
 *
 * Common values:
 *   null                  — fully in-game (no GUI screen open)
 *   "TitleScreen"         — main menu
 *   "SelectWorldScreen"   — world list
 *   "CreateWorldScreen"   — new world dialog
 *   "GenericDirtMessageScreen" — loading/progress overlay
 */
object WaitForScreenTool : Tool(
    name = "wait_for_screen",
    description = "Block until the open Screen matches the given class name (or null = no screen / fully in-game). Use after load_world, save_and_quit, or click_ui_button.",
    inputSchemaJson = """
        {"type":"object","properties":{
          "screen":{"type":["string","null"],
                    "description":"Screen class simpleName to wait for, or null to wait until fully in-game"},
          "timeout_ms":{"type":"integer","default":30000}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val targetScreen: String? = if (args.has("screen") && !args.get("screen").isJsonNull)
            args.get("screen").asString.ifEmpty { null }
        else null

        val timeoutMs = if (args.has("timeout_ms")) args.get("timeout_ms").asLong else 30_000L
        val start = System.currentTimeMillis()
        val deadline = start + timeoutMs

        var lastSeen = "INITIAL"
        while (System.currentTimeMillis() < deadline) {
            val current = Minecraft.getInstance().screen?.javaClass?.simpleName
            if (current != lastSeen) {
                LOGGER.info("wait_for_screen: screen changed → {}", current ?: "<none>")
                lastSeen = current ?: "<none>"
            }
            if (current == targetScreen) {
                val elapsed = System.currentTimeMillis() - start
                val screenJson = current?.let { "\"$it\"" } ?: "null"
                return """{"ok":true,"screen":$screenJson,"elapsed_ms":$elapsed}"""
            }
            Thread.sleep(100)
        }

        val current = Minecraft.getInstance().screen?.javaClass?.simpleName
        val wantedJson = targetScreen?.let { "\"$it\"" } ?: "null"
        val actualJson = current?.let { "\"$it\"" } ?: "null"
        return """{"error":"timeout","wanted":$wantedJson,"actual":$actualJson,"elapsed_ms":${System.currentTimeMillis() - start}}"""
    }
}
