package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.client.Minecraft

/**
 * Save the current single-player world and disconnect back to the main menu.
 * Blocks until the title screen is shown or timeout.
 *
 * Args:
 *   timeout_ms (int, optional, default 30000)
 *
 * Returns: { "ok":true, "elapsed_ms":N } or { "error":"..." }
 */
object SaveAndQuitTool : Tool(
    name = "save_and_quit",
    description = "Save the current single-player world and return to the main menu. Blocks until the title screen appears.",
    inputSchemaJson = """
        {"type":"object","properties":{
          "timeout_ms":{"type":"integer","default":30000}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val mc = Minecraft.getInstance()

        if (mc.singleplayerServer == null)
            return """{"error":"No integrated server running — not in a single-player world"}"""

        // Fire disconnect on the render thread and return immediately — large worlds
        // can take 60s+ to flush to disk, and blocking here locks up the HTTP thread.
        // Callers that need to know when the title screen appears should poll /state.
        mc.execute { mc.disconnect() }
        return """{"ok":true,"queued":true}"""
    }
}
