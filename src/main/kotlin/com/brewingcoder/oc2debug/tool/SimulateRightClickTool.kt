package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

/**
 * Simulate a player right-click on a block face. Calls the integrated
 * server's gameMode useItemOn — same code path as a real interaction. If the
 * target block opens a Screen, that Screen will be open by the time this
 * returns (next client tick).
 *
 * Args:
 *   x, y, z   (ints, required) — block position
 *   face      (string, optional, default "up") — which face was clicked: up/down/north/south/east/west
 *   hand      (string, optional, default "main") — main | off
 */
object SimulateRightClickTool : Tool(
    name = "simulate_right_click",
    description = "Simulate a player right-clicking the specified face of a block. Opens GUIs etc. as if the player clicked.",
    inputSchemaJson = """
        {"type":"object","required":["x","y","z"],"properties":{
          "x":{"type":"integer"}, "y":{"type":"integer"}, "z":{"type":"integer"},
          "face":{"type":"string","enum":["up","down","north","south","east","west"]},
          "hand":{"type":"string","enum":["main","off"]}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val x = args.intReq("x"); val y = args.intReq("y"); val z = args.intReq("z")
        val face = Direction.byName(args.strOr("face", "up")) ?: Direction.UP
        val hand = if (args.strOr("hand", "main") == "off") InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND

        return onClient {
            val mc = net.minecraft.client.Minecraft.getInstance()
            val player = mc.player ?: error("No local player — load a single-player world first")
            val level = mc.level ?: error("No level loaded")
            val pos = BlockPos(x, y, z)

            // Build a synthetic BlockHitResult clicking the requested face center
            val hitVec = Vec3(
                x + 0.5 + face.stepX * 0.5,
                y + 0.5 + face.stepY * 0.5,
                z + 0.5 + face.stepZ * 0.5,
            )
            val hit = BlockHitResult(hitVec, face, pos, /* inside */ false)

            val gameMode = mc.gameMode ?: error("Game mode not available")
            val result = gameMode.useItemOn(player, hand, hit)
            "{\"result\":\"${result.name}\",\"pos\":[$x,$y,$z],\"face\":\"${face.name.lowercase()}\"}"
        }
    }
}
