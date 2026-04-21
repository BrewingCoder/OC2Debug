package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import java.io.File

/**
 * Capture the MC window's render target and write it to a file.
 *
 * Args:
 *   path (string, required) — absolute path to write to (.png or .jpg)
 *
 * Returns:
 *   { "width": W, "height": H, "tick": T, "screen": "ClassName"|null, "path": "/abs/path" }
 */
object ScreenshotTool : Tool(
    name = "screenshot",
    description = "Capture the Minecraft window framebuffer and save it to a file. Returns metadata; no base64.",
    inputSchemaJson = """
        {"type":"object","required":["path"],"properties":{
          "path":{"type":"string","description":"Absolute path for the output file (.png or .jpg)"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val path = args.get("path")?.asString
            ?: return """{"error":"'path' is required"}"""

        return onClient {
            val mc = Minecraft.getInstance()
            val target: RenderTarget = mc.mainRenderTarget
            val w = target.width
            val h = target.height

            val image = NativeImage(w, h, false)
            RenderSystem.bindTexture(target.colorTextureId)
            image.downloadTexture(0, true)
            image.flipY()

            try {
                val outFile = File(path)
                outFile.parentFile?.mkdirs()
                image.writeToFile(outFile)
            } finally {
                image.close()
            }

            val openScreen = mc.screen?.javaClass?.simpleName
            val tick = mc.level?.gameTime ?: -1L
            val safeScreen = openScreen?.let { "\"$it\"" } ?: "null"
            val safePath = path.replace("\\", "\\\\")

            """{"width":$w,"height":$h,"tick":$tick,"screen":$safeScreen,"path":"$safePath"}"""
        }
    }
}
