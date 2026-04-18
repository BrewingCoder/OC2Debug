package com.brewingcoder.oc2debug

import com.brewingcoder.oc2debug.mcp.McpHttpServer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * OC2-Debug — dev-time companion mod that embeds an MCP server inside Minecraft.
 *
 * Lets Claude (or any MCP client) drive the running game programmatically:
 *   - Take MC-window screenshots (with metadata) instead of full desktop captures
 *   - Read/write block states + BlockEntity NBT
 *   - Simulate player interactions (right-click, left-click)
 *   - Dispatch vanilla commands as a fallback
 *
 * Server starts on FMLClientSetup (client-side only — debug tool, never ships
 * to a real server). Default port: 9876, configurable.
 *
 * NOT shipped with OC2 production. Lives in its own jar, loaded only in dev
 * environments alongside OC2.
 */
@Mod(OC2Debug.ID)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object OC2Debug {
    const val ID = "oc2debug"
    val LOGGER: Logger = LogManager.getLogger(ID)

    private var server: McpHttpServer? = null

    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        val port = System.getenv("OC2DEBUG_PORT")?.toIntOrNull() ?: DEFAULT_PORT
        LOGGER.info("Starting OC2-Debug MCP server on 127.0.0.1:{}", port)
        server = McpHttpServer(port).also { it.start() }
        // Shutdown hook — stop the server cleanly on JVM exit
        Runtime.getRuntime().addShutdownHook(Thread {
            LOGGER.info("Shutting down OC2-Debug MCP server")
            server?.stop()
        })
    }

    const val DEFAULT_PORT = 9876
}
