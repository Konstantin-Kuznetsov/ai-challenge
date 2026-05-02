package week04.task1.mcp

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonElement
import week04.task1.model.ToolInfo
import week04.task1.model.TurnTrace
import week04.task1.util.jsonElementToAny
import week04.task1.util.timedStep
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference

class McpClientBridge {
    private val clientRef = AtomicReference<Client?>(null)

    fun isConnected(): Boolean = clientRef.get() != null

    suspend fun listTools(trace: TurnTrace): List<ToolInfo> {
        val client = ensureConnected(trace)
        val result = timedStep(trace, "mcp.tools.list") {
            client.listTools()
        }
        return result.tools.map { tool ->
            ToolInfo(
                name = tool.name,
                description = tool.description ?: "",
                inputSchema = tool.inputSchema.toString(),
            )
        }
    }

    suspend fun callTool(name: String, arguments: Map<String, JsonElement>, trace: TurnTrace): String {
        val client = ensureConnected(trace)
        val response = timedStep(trace, "mcp.tool.call:$name") {
            client.callTool(name = name, arguments = arguments.mapValues { (_, value) -> jsonElementToAny(value) })
        }
        return response.content.joinToString("\n") { content ->
            when (content) {
                is TextContent -> content.text
                else -> content.toString()
            }
        }
    }

    private suspend fun ensureConnected(trace: TurnTrace): Client {
        clientRef.get()?.let { return it }

        val javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString()
        val classPath = System.getProperty("java.class.path")
        val process = ProcessBuilder(javaBin, "-cp", classPath, "week04.task1.AppKt", "--mcp-stdio")
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        val transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered(),
            error = process.errorStream.asSource().buffered(),
        )

        val client = Client(
            clientInfo = Implementation(name = "week04-client", version = "0.1.0"),
            options = ClientOptions(),
        )

        timedStep(trace, "mcp.connect") {
            client.connect(transport)
        }
        clientRef.set(client)
        return client
    }
}
