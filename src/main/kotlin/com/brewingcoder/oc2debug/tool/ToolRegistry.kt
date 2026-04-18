package com.brewingcoder.oc2debug.tool

/**
 * Registry of all MCP tools the OC2-Debug server exposes. Tools register here
 * at class init; the MCP server reads this map to advertise + dispatch.
 */
object ToolRegistry {
    val tools: Map<String, Tool> = listOf(
        ScreenshotTool,
        GetBlockTool,
        SetBlockTool,
        SimulateRightClickTool,
        WaitTicksTool,
        DispatchCommandTool,
    ).associateBy { it.name }
}
