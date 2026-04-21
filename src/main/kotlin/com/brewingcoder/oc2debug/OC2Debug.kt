package com.brewingcoder.oc2debug

import com.brewingcoder.oc2debug.mcp.McpHttpServer
import com.brewingcoder.oc2debug.tool.AsyncJobRegistry
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.LevelEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod(OC2Debug.ID)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object OC2Debug {
    const val ID = "oc2debug"
    const val DEFAULT_PORT = 9876
    val LOGGER: Logger = LogManager.getLogger(ID)

    private var server: McpHttpServer? = null

    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        val port = System.getenv("OC2DEBUG_PORT")?.toIntOrNull() ?: DEFAULT_PORT
        LOGGER.info("Starting OC2-Debug MCP server on 127.0.0.1:{}", port)
        server = McpHttpServer(port).also { it.start() }
        Runtime.getRuntime().addShutdownHook(Thread {
            LOGGER.info("Shutting down OC2-Debug MCP server")
            server?.stop()
        })
        // Cancel async jobs on world unload so they don't block shutdown.
        NeoForge.EVENT_BUS.addListener { _: LevelEvent.Unload ->
            AsyncJobRegistry.cancelAll()
        }
        // Re-apply persistent dev-helper effects every 200 ticks (~10s).
        var devHelpersTickCounter = 0
        NeoForge.EVENT_BUS.addListener { e: net.neoforged.neoforge.event.tick.ServerTickEvent.Post ->
            if (++devHelpersTickCounter >= 200) {
                devHelpersTickCounter = 0
                com.brewingcoder.oc2debug.tool.DevHelpers.onServerTick(e.server)
            }
        }
    }
}
