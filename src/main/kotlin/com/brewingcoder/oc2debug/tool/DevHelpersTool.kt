package com.brewingcoder.oc2debug.tool

import com.brewingcoder.oc2debug.OC2Debug
import com.google.gson.Gson
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.effect.MobEffectInstance
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.io.File

/**
 * Dev-helper toggle tool: apply (and persist) status effects on the player at
 * the effect's maximum amplifier. Toggling an already-active max-level effect
 * removes it. State persists across game restarts — a tick handler re-applies
 * active effects after world load so the player never has to re-run the command.
 *
 * Usage:
 *   toggle_dev_effect {"effect":"minecraft:saturation"}
 *   toggle_dev_effect {"effect":"minecraft:regeneration"}
 *
 * Config file: <gameDir>/config/oc2debug_devhelpers.json
 *   { "effects": ["minecraft:saturation", "minecraft:regeneration"] }
 */
object DevHelpers {

    private val gson = Gson()

    // ---- persistence --------------------------------------------------------

    private val configFile: File
        get() = File(Minecraft.getInstance().gameDirectory, "config/oc2debug_devhelpers.json")

    /** Currently-enabled effects by registry name. */
    private val enabled = mutableSetOf<String>()

    private var loaded = false

    fun load() {
        enabled.clear()
        val f = configFile
        if (!f.exists()) { loaded = true; return }
        runCatching {
            val obj = gson.fromJson(f.readText(), JsonObject::class.java)
            obj.getAsJsonArray("effects")?.forEach { enabled.add(it.asString) }
        }.onFailure { OC2Debug.LOGGER.warn("DevHelpers: failed to load config", it) }
        loaded = true
    }

    private fun save() {
        val f = configFile
        f.parentFile.mkdirs()
        val arr = com.google.gson.JsonArray()
        enabled.forEach { arr.add(it) }
        val obj = JsonObject(); obj.add("effects", arr)
        f.writeText(gson.toJson(obj))
    }

    // ---- max amplifier ------------------------------------------------------

    private fun maxAmplifier(effectName: String): Int {
        val loc = ResourceLocation.tryParse(effectName) ?: return 127
        val holder = BuiltInRegistries.MOB_EFFECT.getHolder(loc).orElse(null) ?: return 127
        // getMaxAmplifier() was added in NeoForge 1.21 — call reflectively so we
        // compile against a potentially-missing method without a hard dep.
        return runCatching {
            holder.value().javaClass.getMethod("getMaxAmplifier").invoke(holder.value()) as Int
        }.getOrDefault(127)
    }

    // ---- toggle -------------------------------------------------------------

    /**
     * Toggle the named effect. Returns a JSON string describing the result.
     * Must be called on the server thread.
     */
    fun toggle(server: net.minecraft.server.MinecraftServer, effectName: String): String {
        if (!loaded) load()

        val loc = ResourceLocation.tryParse(effectName)
            ?: return """{"error":"invalid effect name: $effectName"}"""
        val holder = BuiltInRegistries.MOB_EFFECT.getHolder(loc).orElse(null)
            ?: return """{"error":"unknown effect: $effectName"}"""

        val player = server.playerList.players.firstOrNull()
            ?: return """{"error":"no player in world"}"""

        val maxAmp = maxAmplifier(effectName)

        return if (enabled.contains(effectName)) {
            // Toggle OFF
            enabled.remove(effectName)
            save()
            player.removeEffect(holder)
            """{"toggled":"off","effect":"$effectName"}"""
        } else {
            // Toggle ON
            enabled.add(effectName)
            save()
            player.addEffect(MobEffectInstance(holder, Int.MAX_VALUE, maxAmp, false, false))
            """{"toggled":"on","effect":"$effectName","amplifier":$maxAmp}"""
        }
    }

    // ---- re-application on tick ---------------------------------------------

    /**
     * Called every 200 server ticks (~10s). Re-applies any enabled effects that
     * are missing or nearly expired — covers world reload + death.
     */
    fun onServerTick(server: net.minecraft.server.MinecraftServer) {
        if (!loaded) load()
        if (enabled.isEmpty()) return
        val player = server.playerList.players.firstOrNull() ?: return
        for (effectName in enabled) {
            val loc = ResourceLocation.tryParse(effectName) ?: continue
            val holder = BuiltInRegistries.MOB_EFFECT.getHolder(loc).orElse(null) ?: continue
            val instance = player.getEffect(holder)
            // Re-apply if missing or duration dropped below 60s (1200 ticks)
            if (instance == null || instance.duration < 1200) {
                val maxAmp = maxAmplifier(effectName)
                player.addEffect(MobEffectInstance(holder, Int.MAX_VALUE, maxAmp, false, false))
            }
        }
    }
}

object ToggleDevEffectTool : Tool(
    name = "toggle_dev_effect",
    description = "Toggle a dev-helper status effect at max amplifier (saturation, regeneration, etc.). Persists across restarts — re-applied automatically on world load. Pass effect as registry name e.g. 'minecraft:saturation'.",
    inputSchemaJson = """
        {"type":"object","required":["effect"],"properties":{
          "effect":{"type":"string","description":"Registry name, e.g. minecraft:saturation or minecraft:regeneration"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val effectName = args.strReq("effect")
        return onServer { server -> DevHelpers.toggle(server, effectName) }
    }
}
