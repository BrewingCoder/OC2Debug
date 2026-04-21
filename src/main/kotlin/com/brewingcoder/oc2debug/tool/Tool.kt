package com.brewingcoder.oc2debug.tool

import com.brewingcoder.oc2debug.OC2Debug
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * One callable MCP tool. Subclasses implement [run] which is dispatched to the
 * appropriate MC thread via [onClient] / [onServer].
 *
 * Tools return a String that becomes the MCP tool-call result content. JSON
 * payloads are formatted as JSON strings; binary (screenshots) is base64.
 */
abstract class Tool(
    val name: String,
    val description: String,
    /** JSON Schema for arguments — see https://json-schema.org/. */
    val inputSchemaJson: String = """{"type":"object","properties":{},"additionalProperties":true}""",
) {
    abstract fun invoke(args: JsonObject): String

    /** Run a block on MC's client thread, blocking the caller until done. */
    protected fun <T> onClient(block: () -> T): T {
        val mc = Minecraft.getInstance()
        if (mc.isSameThread) return block()
        val future = CompletableFuture<T>()
        mc.execute {
            try { future.complete(block()) } catch (e: Throwable) { future.completeExceptionally(e) }
        }
        return future.get(THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /** Run a block on the server thread (single-player integrated server). */
    protected fun <T> onServer(block: (MinecraftServer) -> T): T {
        val server = Minecraft.getInstance().singleplayerServer
            ?: error("No integrated server running — start a single-player world first")
        if (server.isSameThread) return block(server)
        val future = CompletableFuture<T>()
        server.execute {
            try { future.complete(block(server)) } catch (e: Throwable) { future.completeExceptionally(e) }
        }
        try {
            return future.get(THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.ExecutionException) {
            throw e.cause ?: e
        }
    }

    protected fun JsonObject.intOr(key: String, default: Int): Int =
        if (has(key)) get(key).asInt else default

    protected fun JsonObject.intReq(key: String): Int =
        if (has(key)) get(key).asInt else throw IllegalArgumentException("Missing required arg: $key")

    protected fun JsonObject.strReq(key: String): String =
        if (has(key)) get(key).asString else throw IllegalArgumentException("Missing required arg: $key")

    protected fun JsonObject.strOr(key: String, default: String): String =
        if (has(key)) get(key).asString else default

    companion object {
        private const val THREAD_TIMEOUT_SECONDS = 120L
        @JvmStatic protected val LOGGER = OC2Debug.LOGGER
    }
}
