package week04.task3

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.nio.file.Paths
import kotlinx.coroutines.runBlocking
import week04.task3.api.configureTask3Routes
import week04.task3.mcp.McpClientBridge
import week04.task3.mcp.runMcpServer
import week04.task3.service.SchedulerService
import week04.task3.storage.JsonJobStateStore
import week04.task3.storage.JsonMeasurementStore
import week04.task3.storage.JsonSummaryStore

fun main(args: Array<String>) {
    if (args.contains("--mcp-stdio")) {
        runMcpServer()
        return
    }

    val port = System.getenv("WEEK04_TASK3_PORT")?.toIntOrNull() ?: 6103
    val mcpClient = McpClientBridge()
    val jobStateStore = JsonJobStateStore(Paths.get("task3", "data", "job_state.json"))
    val measurementStore = JsonMeasurementStore(Paths.get("task3", "data", "measurements.json"))
    val summaryStore = JsonSummaryStore(Paths.get("task3", "data", "summary_latest.json"))
    val scheduler = SchedulerService(
        mcpClient = mcpClient,
        jobStateStore = jobStateStore,
        measurementStore = measurementStore,
        summaryStore = summaryStore,
    )
    scheduler.start()

    Runtime.getRuntime().addShutdownHook(
        Thread {
            runBlocking { scheduler.stop() }
        },
    )

    embeddedServer(Netty, port = port) {
        configureTask3Routes(scheduler, mcpClient)
    }.start(wait = true)
}
