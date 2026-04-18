package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block

/**
 * Place a block in the world. Equivalent to `/setblock` but synchronous from
 * the caller's perspective (returns when the block has been placed and the
 * BE has run its onLoad).
 *
 * Args:
 *   x, y, z (ints, required)
 *   block   (string, required) — e.g. "oc2:computer", "minecraft:stone"
 */
object SetBlockTool : Tool(
    name = "set_block",
    description = "Place a block at (x,y,z). block is a namespaced ID like 'oc2:computer'.",
    inputSchemaJson = """
        {"type":"object","required":["x","y","z","block"],"properties":{
          "x":{"type":"integer"}, "y":{"type":"integer"}, "z":{"type":"integer"},
          "block":{"type":"string"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val x = args.intReq("x"); val y = args.intReq("y"); val z = args.intReq("z")
        val blockId = args.strReq("block")

        return onServer { server ->
            val rl = ResourceLocation.parse(blockId)
            val block: Block = BuiltInRegistries.BLOCK.getOptional(rl).orElseThrow {
                IllegalArgumentException("Unknown block: $blockId")
            }
            val level = server.overworld()
            val pos = BlockPos(x, y, z)
            val placed = level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL)
            "{\"placed\":$placed,\"block\":\"$blockId\",\"pos\":[$x,$y,$z]}"
        }
    }
}
