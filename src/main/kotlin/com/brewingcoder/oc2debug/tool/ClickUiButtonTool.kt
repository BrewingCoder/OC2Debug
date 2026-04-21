package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener

/**
 * Click a button in the currently open Screen by matching its visible label text.
 * Case-insensitive substring match — so "Create" matches "Create New World".
 *
 * Args:
 *   label      (string, required) — button text to match (case-insensitive substring)
 *   exact      (bool, optional, default false) — require exact match instead of substring
 *
 * Returns:
 *   { "clicked":"<exact label>", "buttons_found":["...","..."] }
 * Error if no screen is open, or no matching button is found.
 *
 * Typical automation sequence:
 *   game_state                          → confirm we're at TitleScreen
 *   click_ui_button("Singleplayer")     → opens SelectWorldScreen
 *   click_ui_button("Create New World") → opens CreateWorldScreen
 */
object ClickUiButtonTool : Tool(
    name = "click_ui_button",
    description = "Click a button in the open Screen by label text (case-insensitive substring). Returns all button labels found if none matched.",
    inputSchemaJson = """
        {"type":"object","required":["label"],"properties":{
          "label":{"type":"string"},
          "exact":{"type":"boolean","description":"Require exact match (default: substring)"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val label = args.strReq("label")
        val exact = if (args.has("exact")) args.get("exact").asBoolean else false

        return onClient {
            val mc = Minecraft.getInstance()
            val screen = mc.screen ?: error("No screen is open — nothing to click")

            val buttons = mutableListOf<AbstractButton>()
            collectButtons(screen, buttons)

            val all = buttons.map { it.message.string }
            val target = if (exact) {
                buttons.firstOrNull { it.message.string.equals(label, ignoreCase = true) }
            } else {
                buttons.firstOrNull { it.message.string.contains(label, ignoreCase = true) }
            } ?: error(
                "No button matching '${label.esc()}' on ${screen.javaClass.simpleName}. " +
                "Available: ${all.joinToString(", ") { "\"${it.esc()}\"" }}"
            )

            val cx = target.x + target.width / 2.0
            val cy = target.y + target.height / 2.0
            target.mouseClicked(cx, cy, 0)

            val allJson = all.joinToString(",") { "\"${it.esc()}\"" }
            """{"clicked":"${target.message.string.esc()}","screen":"${screen.javaClass.simpleName}","buttons_found":[$allJson]}"""
        }
    }

    private fun collectButtons(handler: Any, out: MutableList<AbstractButton>) {
        if (handler is AbstractButton) out.add(handler)
        if (handler is ContainerEventHandler) {
            for (child in handler.children()) collectButtons(child, out)
        }
    }

    private fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
}
