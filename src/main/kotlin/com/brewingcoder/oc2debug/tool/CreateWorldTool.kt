package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows
import net.minecraft.core.registries.Registries
import net.minecraft.world.Difficulty
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets

/**
 * Create a fresh single-player world and load it.
 * Defaults to Creative + normal terrain + random seed.
 *
 * Args:
 *   name        (string, optional) — display name; defaults to "OC2-Test-<epoch>"
 *   gamemode    (string, optional) — creative | survival | adventure | spectator (default creative)
 *   seed        (string, optional) — seed string/int; omit for random
 *   flat        (bool, optional)   — superflat instead of normal terrain (default false)
 *   timeout_ms  (int, optional, default 90000)
 *
 * Returns: { "ok":true, "world":"<name>", "level_id":"<folder>", "elapsed_ms":N }
 */
object CreateWorldTool : Tool(
    name = "create_world",
    description = "Create and load a new single-player world. Defaults to Creative + normal terrain. Blocks until fully loaded.",
    inputSchemaJson = """
        {"type":"object","properties":{
          "name":{"type":"string"},
          "gamemode":{"type":"string","enum":["creative","survival","adventure","spectator"]},
          "seed":{"type":"string"},
          "flat":{"type":"boolean"},
          "timeout_ms":{"type":"integer","default":90000}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val name = args.strOr("name", "OC2-Test-${System.currentTimeMillis()}")
        val gamemodeStr = args.strOr("gamemode", "creative")
        val seedStr = if (args.has("seed")) args.get("seed").asString else ""
        val flat = if (args.has("flat")) args.get("flat").asBoolean else false
        val timeoutMs = if (args.has("timeout_ms")) args.get("timeout_ms").asLong else 90_000L
        val start = System.currentTimeMillis()

        // Sanitise folder name: alphanumeric + underscore/hyphen
        val levelId = name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(32).ifEmpty { "OC2_World" }

        val gameType = when (gamemodeStr.lowercase()) {
            "survival", "s" -> GameType.SURVIVAL
            "adventure", "a" -> GameType.ADVENTURE
            "spectator" -> GameType.SPECTATOR
            else -> GameType.CREATIVE
        }

        val worldOptions = if (seedStr.isNotEmpty()) {
            WorldOptions(seedStr.toLongOrNull() ?: seedStr.hashCode().toLong(), true, false)
        } else {
            WorldOptions.defaultWithRandomSeed()
        }

        // WorldDataConfiguration is part of LevelSettings in 1.21.1
        val levelSettings = LevelSettings(
            name,
            gameType,
            false,              // hardcore
            Difficulty.NORMAL,
            true,               // allowCommands (cheats) — needed for /gamemode etc.
            GameRules(),
            WorldDataConfiguration.DEFAULT,
        )

        onClient {
            val mc = Minecraft.getInstance()
            val presetKey = if (flat) WorldPresets.FLAT else WorldPresets.NORMAL
            // The dimension factory receives RegistryAccess from inside createFreshLevel
            WorldOpenFlows(mc, mc.levelSource).createFreshLevel(
                levelId,
                levelSettings,
                worldOptions,
                { ra ->
                    ra.registryOrThrow(Registries.WORLD_PRESET)
                        .getOptional(presetKey)
                        .orElseThrow { RuntimeException("World preset $presetKey not found in registry") }
                        .createWorldDimensions()
                },
                mc.screen,
            )
        }

        // Poll until player spawns
        val deadline = start + timeoutMs
        Thread.sleep(500)
        while (System.currentTimeMillis() < deadline) {
            val mc = Minecraft.getInstance()
            if (mc.player != null && mc.level != null && mc.screen == null) {
                val elapsed = System.currentTimeMillis() - start
                return """{"ok":true,"world":"${name.esc()}","level_id":"$levelId","elapsed_ms":$elapsed}"""
            }
            Thread.sleep(200)
        }
        val screen = Minecraft.getInstance().screen?.javaClass?.simpleName
        return """{"error":"timeout waiting for world to load","level_id":"$levelId","screen":"$screen"}"""
    }

    private fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
}
