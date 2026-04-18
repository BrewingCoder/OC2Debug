package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.NbtUtils

/**
 * Inspect a block in the world.
 *
 * Args:  x, y, z (ints, required)
 *
 * Returns JSON:
 *   {
 *     "block": "oc2:computer",
 *     "state": { "facing":"north" },
 *     "block_entity": { ...NBT as snbt-ish JSON... } | null
 *   }
 */
object GetBlockTool : Tool(
    name = "get_block",
    description = "Read the block ID, blockstate properties, and BlockEntity NBT at (x,y,z).",
    inputSchemaJson = """
        {"type":"object","required":["x","y","z"],"properties":{
          "x":{"type":"integer"}, "y":{"type":"integer"}, "z":{"type":"integer"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val x = args.intReq("x"); val y = args.intReq("y"); val z = args.intReq("z")

        return onServer { server ->
            val level = server.overworld()
            val pos = BlockPos(x, y, z)
            val state = level.getBlockState(pos)
            val blockId = BuiltInRegistries.BLOCK.getKey(state.block).toString()

            val stateMap = state.values.entries.joinToString(",") { (prop, v) ->
                @Suppress("UNCHECKED_CAST")
                val name = (prop as net.minecraft.world.level.block.state.properties.Property<Comparable<Any>>).getName(v as Comparable<Any>)
                "\"${prop.name}\":\"$name\""
            }

            val be = level.getBlockEntity(pos)
            val beNbt = if (be != null) {
                val tag = be.saveWithFullMetadata(level.registryAccess())
                NbtUtils.toPrettyComponent(tag).string.replace("\n", " ").replace("\"", "\\\"")
            } else null

            buildString {
                append("{")
                append("\"block\":\"$blockId\",")
                append("\"state\":{$stateMap},")
                append("\"block_entity\":${if (beNbt != null) "\"$beNbt\"" else "null"}")
                append("}")
            }
        }
    }
}
