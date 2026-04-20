package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack

/**
 * Read the active player's inventory: hotbar, main, armor, offhand, and
 * which hotbar slot is currently selected. Returns one structured JSON
 * blob — pair with `dispatch_command` to act on the findings.
 *
 * Slot numbering in the output:
 *   - hotbar: 0–8
 *   - main:   9–35
 *   - armor:  labeled (helmet/chestplate/leggings/boots)
 *   - offhand: 40
 *
 * Each non-empty slot:
 *   {"slot":N, "id":"minecraft:diamond_pickaxe", "count":1,
 *    "damage":12, "max_damage":1561, "name":"Big Pick"?}
 *
 * Empty slots emit `null` to keep array indices stable.
 */
object GetInventoryTool : Tool(
    name = "get_inventory",
    description = "Read the active player's inventory (hotbar, main, armor, offhand, selected slot).",
    inputSchemaJson = """{"type":"object","properties":{}}""",
) {
    override fun invoke(args: JsonObject): String {
        return onServer { server ->
            val player = server.playerList.players.firstOrNull()
                ?: return@onServer """{"error":"no player loaded"}"""

            val inv = player.inventory
            val sb = StringBuilder()
            sb.append("{")
            sb.append("\"selected_slot\":").append(inv.selected).append(",")

            sb.append("\"hotbar\":[")
            for (i in 0..8) {
                if (i > 0) sb.append(",")
                appendStack(sb, inv.items[i], i)
            }
            sb.append("],")

            sb.append("\"main\":[")
            for (i in 9..35) {
                if (i > 9) sb.append(",")
                appendStack(sb, inv.items[i], i)
            }
            sb.append("],")

            // Vanilla armor list order: 0=boots, 1=leggings, 2=chestplate, 3=helmet
            sb.append("\"armor\":{")
            sb.append("\"helmet\":")
            appendStack(sb, inv.armor[3], 39)
            sb.append(",\"chestplate\":")
            appendStack(sb, inv.armor[2], 38)
            sb.append(",\"leggings\":")
            appendStack(sb, inv.armor[1], 37)
            sb.append(",\"boots\":")
            appendStack(sb, inv.armor[0], 36)
            sb.append("},")

            sb.append("\"offhand\":")
            appendStack(sb, inv.offhand[0], 40)
            sb.append(",")

            sb.append("\"held\":")
            appendStack(sb, inv.items[inv.selected], inv.selected)

            sb.append("}")
            sb.toString()
        }
    }

    private fun appendStack(sb: StringBuilder, stack: ItemStack, slot: Int) {
        if (stack.isEmpty) {
            sb.append("null")
            return
        }
        val id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        sb.append("{\"slot\":").append(slot)
        sb.append(",\"id\":\"").append(esc(id)).append("\"")
        sb.append(",\"count\":").append(stack.count)
        if (stack.isDamageableItem) {
            sb.append(",\"damage\":").append(stack.damageValue)
            sb.append(",\"max_damage\":").append(stack.maxDamage)
        }
        val custom = stack.get(DataComponents.CUSTOM_NAME)
        if (custom != null) {
            sb.append(",\"name\":\"").append(esc(custom.string)).append("\"")
        }
        sb.append("}")
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
