package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows

/**
 * Load an existing single-player world by its folder ID (as returned by list_worlds).
 * Blocks until the world is fully loaded (player spawned) or until timeout.
 *
 * Args:
 *   id          (string, required) — level folder ID, e.g. "MyWorld"
 *   timeout_ms  (int, optional, default 90000) — ms to wait for load
 *
 * Returns: { "ok":true, "world":"...", "elapsed_ms":N }
 *       or { "error":"...", "screen":"<blocking dialog class>", ... }
 *
 * If a dialog appears (backup warning, version incompatibility, etc.)
 * the tool returns early with the blocking screen name so you can use
 * click_ui_button to dismiss it, then poll game_state until in_world=true.
 *
 * Typical flow:
 *   list_worlds → load_world → game_state → ...work... → save_and_quit
 */
object LoadWorldTool : Tool(
    name = "load_world",
    description = "Load an existing single-player world by its folder ID. Blocks until fully loaded or until a dialog appears that needs user input.",
    inputSchemaJson = """
        {"type":"object","required":["id"],"properties":{
          "id":{"type":"string","description":"World folder ID from list_worlds"},
          "timeout_ms":{"type":"integer","default":90000}
        }}
    """.trimIndent(),
) {
    // Screens that indicate a blocking dialog (not a normal loading overlay)
    private val DIALOG_SCREENS = setOf(
        "BackupConfirmScreen", "ConfirmScreen", "AlertScreen",
        "DatapackLoadFailureScreen", "NoticeWithLinkScreen",
    )

    override fun invoke(args: JsonObject): String {
        val levelId = args.strReq("id")
        val timeoutMs = if (args.has("timeout_ms")) args.get("timeout_ms").asLong else 90_000L
        val start = System.currentTimeMillis()

        // Trigger load on the client thread (openWorld starts an async chain)
        onClient {
            val mc = Minecraft.getInstance()
            WorldOpenFlows(mc, mc.levelSource).openWorld(levelId) {
                LOGGER.warn("load_world: openWorld reported failure for '{}'", levelId)
            }
        }

        // Poll from HTTP thread until player is spawned or a blocking dialog appears
        Thread.sleep(500)
        val deadline = start + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val mc = Minecraft.getInstance()
            if (mc.player != null && mc.level != null && mc.screen == null) {
                val elapsed = System.currentTimeMillis() - start
                return """{"ok":true,"world":"$levelId","elapsed_ms":$elapsed}"""
            }
            val screenName = mc.screen?.javaClass?.simpleName
            if (screenName in DIALOG_SCREENS) {
                val elapsed = System.currentTimeMillis() - start
                return """{"error":"load paused — dialog requires input","world":"$levelId","screen":"$screenName","elapsed_ms":$elapsed,"tip":"use click_ui_button to proceed"}"""
            }
            // Detect hard failure: back at TitleScreen with no level
            if (screenName == "TitleScreen" && mc.level == null && (System.currentTimeMillis() - start) > 5_000) {
                return """{"error":"world load failed or was cancelled","world":"$levelId","screen":"$screenName"}"""
            }
            Thread.sleep(200)
        }

        val screenName = Minecraft.getInstance().screen?.javaClass?.simpleName
        return """{"error":"timeout waiting for world to load","world":"$levelId","screen":"$screenName","elapsed_ms":${System.currentTimeMillis() - start}}"""
    }
}
