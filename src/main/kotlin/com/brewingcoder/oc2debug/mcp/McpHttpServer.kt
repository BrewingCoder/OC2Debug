package com.brewingcoder.oc2debug.mcp

import com.brewingcoder.oc2debug.OC2Debug
import com.brewingcoder.oc2debug.tool.ToolRegistry
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * Minimal MCP HTTP server. Implements just enough of the JSON-RPC + MCP
 * protocol to let an MCP client (Claude Code) discover + call tools.
 *
 * Endpoints:
 *   POST /mcp   — JSON-RPC 2.0 requests
 *
 * Supported MCP methods:
 *   - initialize          → returns server info + capabilities
 *   - tools/list          → returns the tool registry
 *   - tools/call          → invokes a tool, returns its result
 *
 * Errors come back as standard JSON-RPC error objects.
 *
 * Threading:
 *   - HTTP requests on a small thread pool (httpserver default)
 *   - Tool execution dispatches to MC's main thread when needed (per tool)
 *   - Response sent on the HTTP thread once the tool's CompletableFuture resolves
 */
class McpHttpServer(private val port: Int) {

    private val gson = Gson()
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)

    init {
        server.executor = Executors.newFixedThreadPool(4) { r ->
            Thread(r, "oc2debug-http").apply { isDaemon = true }
        }
        server.createContext("/mcp", ::handleMcp)
        server.createContext("/health") { ex ->
                val body = """{"status":"ok","tools":${ToolRegistry.tools.size}}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
    }

    fun start() {
        server.start()
        OC2Debug.LOGGER.info("MCP HTTP server listening on http://127.0.0.1:$port  (tools={})", ToolRegistry.tools.size)
    }

    fun stop() {
        server.stop(1)
    }

    private fun handleMcp(exchange: HttpExchange) {
        if (exchange.requestMethod != "POST") {
            sendError(exchange, 405, -32600, "Only POST is supported")
            return
        }
        val raw = exchange.requestBody.bufferedReader().use { it.readText() }
        val req = try {
            JsonParser.parseString(raw).asJsonObject
        } catch (e: Exception) {
            sendError(exchange, 400, -32700, "Parse error: ${e.message}")
            return
        }

        val id = req.get("id")
        val method = req.get("method")?.asString
        val params = req.get("params")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()

        try {
            val result = when (method) {
                "initialize" -> initializeResponse()
                "tools/list" -> toolsListResponse()
                "tools/call" -> toolsCallResponse(params)
                else -> {
                    sendErrorWithId(exchange, id, -32601, "Method not found: $method")
                    return
                }
            }
            val resp = JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                if (id != null) add("id", id)
                add("result", result)
            }
            sendJson(exchange, 200, resp)
        } catch (e: Exception) {
            OC2Debug.LOGGER.error("MCP request failed", e)
            sendErrorWithId(exchange, id, -32603, "Internal error: ${e.message ?: e.toString()}")
        }
    }

    private fun initializeResponse(): JsonObject = JsonObject().apply {
        addProperty("protocolVersion", "2024-11-05")
        add("capabilities", JsonObject().apply {
            add("tools", JsonObject())
        })
        add("serverInfo", JsonObject().apply {
            addProperty("name", "oc2-debug")
            addProperty("version", "0.0.1")
        })
    }

    private fun toolsListResponse(): JsonObject = JsonObject().apply {
        val arr = com.google.gson.JsonArray()
        for (tool in ToolRegistry.tools.values) {
            val t = JsonObject().apply {
                addProperty("name", tool.name)
                addProperty("description", tool.description)
                add("inputSchema", JsonParser.parseString(tool.inputSchemaJson).asJsonObject)
            }
            arr.add(t)
        }
        add("tools", arr)
    }

    private fun toolsCallResponse(params: JsonObject): JsonObject {
        val name = params.get("name")?.asString
            ?: throw IllegalArgumentException("tools/call missing 'name'")
        val args = params.get("arguments")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val tool = ToolRegistry.tools[name]
            ?: throw IllegalArgumentException("Unknown tool: $name")
        val text = tool.invoke(args)
        return JsonObject().apply {
            val content = com.google.gson.JsonArray()
            content.add(JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", text)
            })
            add("content", content)
            addProperty("isError", false)
        }
    }

    private fun sendJson(exchange: HttpExchange, status: Int, body: JsonObject) {
        val bytes = gson.toJson(body).toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendError(exchange: HttpExchange, httpStatus: Int, code: Int, message: String) {
        sendErrorWithId(exchange, null, code, message, httpStatus)
    }

    private fun sendErrorWithId(exchange: HttpExchange, id: com.google.gson.JsonElement?, code: Int, message: String, httpStatus: Int = 200) {
        val resp = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            if (id != null) add("id", id) else add("id", com.google.gson.JsonNull.INSTANCE)
            add("error", JsonObject().apply {
                addProperty("code", code)
                addProperty("message", message)
            })
        }
        sendJson(exchange, httpStatus, resp)
    }
}
