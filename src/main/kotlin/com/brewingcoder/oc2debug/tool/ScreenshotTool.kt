package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.util.Base64

/**
 * Capture the MC window's render target as a PNG. Returns metadata + base64
 * image data in the JSON response.
 *
 * Args:
 *   include_image (bool, default true) — embed base64 PNG in the response
 *
 * Returns JSON like:
 *   {
 *     "width": 1900, "height": 1280,
 *     "tick": 12345,
 *     "screen": "ComputerScreen" | null,
 *     "image_base64": "iVBORw..." (if include_image)
 *   }
 */
object ScreenshotTool : Tool(
    name = "screenshot",
    description = "Capture the Minecraft window framebuffer as PNG with metadata.",
    inputSchemaJson = """
        {"type":"object","properties":{
          "include_image":{"type":"boolean","description":"Embed base64 PNG (default true)"}
        }}
    """.trimIndent(),
) {
    override fun invoke(args: JsonObject): String {
        val includeImage = if (args.has("include_image")) args.get("include_image").asBoolean else true

        return onClient {
            val mc = Minecraft.getInstance()
            val target: RenderTarget = mc.mainRenderTarget
            val w = target.width
            val h = target.height

            val image = NativeImage(w, h, false)
            // Read framebuffer pixels into the NativeImage. Must be on render thread (we are via onClient).
            RenderSystem.bindTexture(target.colorTextureId)
            image.downloadTexture(0, true)
            image.flipY()

            val pngBytes: ByteArray = if (includeImage) {
                val tmp = Files.createTempFile("oc2debug-screenshot-", ".png")
                try {
                    image.writeToFile(tmp.toFile())
                    Files.readAllBytes(tmp)
                } finally {
                    Files.deleteIfExists(tmp)
                }
            } else ByteArray(0)
            image.close()

            val openScreen = mc.screen?.javaClass?.simpleName
            val tick = mc.level?.gameTime ?: -1L

            buildString {
                append("{")
                append("\"width\":$w,")
                append("\"height\":$h,")
                append("\"tick\":$tick,")
                append("\"screen\":${openScreen?.let { "\"$it\"" } ?: "null"}")
                if (includeImage) {
                    val b64 = Base64.getEncoder().encodeToString(pngBytes)
                    append(",\"image_base64\":\"$b64\"")
                }
                append("}")
            }
        }
    }
}
