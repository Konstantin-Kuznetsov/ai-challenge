package week04.task1

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.nio.file.Paths
import week04.task1.api.configureRoutes
import week04.task1.mcp.McpClientBridge
import week04.task1.mcp.runMcpServer
import week04.task1.storage.JsonJobStore
import week04.task1.storage.JsonToolHistoryStore
import week04.task1.storage.JsonTraceStore

fun main(args: Array<String>) {
    if (args.contains("--mcp-stdio")) {
        runMcpServer()
        return
    }

    val port = System.getenv("WEEK04_TASK1_PORT")?.toIntOrNull() ?: 6101
    val traceStore = JsonTraceStore(Paths.get("data", "trace_history.json"))
    val toolHistoryStore = JsonToolHistoryStore(Paths.get("data", "tool_calls_history.json"))
    @Suppress("UNUSED_VARIABLE")
    val jobStore: JsonJobStore = JsonJobStore(Paths.get("data", "scheduled_jobs.json"))
    val mcpClient = McpClientBridge()

    embeddedServer(Netty, port = port) {
        configureRoutes(traceStore, toolHistoryStore, mcpClient)
    }.start(wait = true)
}
