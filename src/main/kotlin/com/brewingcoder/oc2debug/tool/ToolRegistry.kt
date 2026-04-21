package com.brewingcoder.oc2debug.tool

/**
 * Registry of all MCP tools the OC2-Debug server exposes. Tools register here
 * at class init; the MCP server reads this map to advertise + dispatch.
 */
object ToolRegistry {
    val tools: Map<String, Tool> = listOf(
        // State / observation
        GameStateTool,
        ScreenshotTool,
        GetBlockTool,
        SetBlockTool,
        SimulateRightClickTool,
        WaitTicksTool,
        // World lifecycle — full end-to-end automation
        ListWorldsTool,
        LoadWorldTool,
        SaveAndQuitTool,
        CreateWorldTool,
        // UI navigation
        ClickUiButtonTool,
        WaitForScreenTool,
        // Player control
        SetLookTool,
        SendInputTool,
        TeleportTool,
        SetFlySpeedTool,
        // Commands
        DispatchCommandTool,
        // OC2 computer / peripheral tooling
        ListComputersTool,
        ReadComputerConsoleTool,
        WriteComputerFileTool,
        RunComputerCommandTool,
        ReadMonitorTool,
        // Async job polling
        PollJobTool,
        // World / environment
        FindBiomeTool,
        ScanChunkTool,
        ScanAreaTool,
        FindBlocksTool,
        GetBlocksTool,
        GetInventoryTool,
        // Dev helpers
        ToggleDevEffectTool,
    ).associateBy { it.name }
}
