package week04.task3.mcp

import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import week04.task3.coingecko.coinIdFromSymbol
import week04.task3.coingecko.createCoinGeckoHttpClient
import week04.task3.coingecko.fetchCoinPrice

fun runMcpServer() {
    createCoinGeckoHttpClient().use { client ->
        val server = Server(
            serverInfo = Implementation(name = "task3-coingecko-mcp", version = "0.1.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)),
            ),
        )
        server.registerTools(client)
        val transport = StdioServerTransport(System.`in`.asInput(), System.out.asSink().buffered())

        runBlocking {
            val session = server.createSession(transport)
            val done = Job()
            session.onClose { done.complete() }
            done.join()
        }
    }
}

private fun Server.registerTools(httpClient: io.ktor.client.HttpClient) {
    addTool(
        name = "getCoinPrice",
        description = "Get coin price by symbol and fiat currency",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("symbol") {
                    put("type", "string")
                    put("description", "Coin symbol, for example BTC")
                }
                putJsonObject("vsCurrency") {
                    put("type", "string")
                    put("description", "Fiat currency, for example usd")
                }
            },
            required = listOf("symbol"),
        ),
        toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true),
    ) { request ->
        val symbol = request.arguments?.get("symbol")?.jsonPrimitive?.content ?: "BTC"
        val vsCurrency = request.arguments?.get("vsCurrency")?.jsonPrimitive?.content ?: "usd"
        val result = fetchCoinPrice(httpClient, coinIdFromSymbol(symbol), vsCurrency)
        CallToolResult(content = listOf(TextContent(result)))
    }
}
