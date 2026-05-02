package week04.task1.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import week04.task1.coingecko.coinIdFromSymbol
import week04.task1.coingecko.createCoinGeckoHttpClient
import week04.task1.coingecko.fetchCoinPrice
import week04.task1.coingecko.fetchMarketSnapshot
import week04.task1.coingecko.fetchOhlc
import week04.task1.coingecko.fetchTrending

fun runMcpServer() {
    createCoinGeckoHttpClient().use { client ->
        val server = Server(
            serverInfo = Implementation(name = "coingecko-mcp", version = "0.1.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)),
            ),
        )

        server.registerCoinGeckoTools(client)

        val transport = StdioServerTransport(
            System.`in`.asInput(),
            System.out.asSink().buffered(),
        )

        runBlocking {
            val session = server.createSession(transport)
            val done = Job()
            session.onClose { done.complete() }
            done.join()
        }
    }
}

private fun Server.registerCoinGeckoTools(httpClient: io.ktor.client.HttpClient) {
    addTool(
        name = "getCoinPrice",
        description = "Get a coin price by symbol and fiat currency",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("symbol") {
                    put("type", "string")
                    put("description", "Coin symbol, for example BTC or ETH")
                }
                putJsonObject("vsCurrency") {
                    put("type", "string")
                    put("description", "Fiat currency, for example usd or eur")
                }
            },
            required = listOf("symbol"),
        ),
        toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true),
    ) { request ->
        val symbol = request.arguments?.get("symbol")?.jsonPrimitive?.content ?: "BTC"
        val vsCurrency = request.arguments?.get("vsCurrency")?.jsonPrimitive?.content ?: "usd"
        val id = coinIdFromSymbol(symbol)
        val result = fetchCoinPrice(httpClient, id, vsCurrency)
        CallToolResult(content = listOf(TextContent(result)))
    }

    addTool(
        name = "getTrendingCoins",
        description = "Get current trending cryptocurrencies from CoinGecko",
        inputSchema = ToolSchema(properties = buildJsonObject {}, required = emptyList()),
        toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true),
    ) {
        val result = fetchTrending(httpClient)
        CallToolResult(content = listOf(TextContent(result)))
    }

    addTool(
        name = "getMarketSnapshot",
        description = "Get top market coins by market cap",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("vsCurrency") {
                    put("type", "string")
                    put("description", "Fiat currency for pricing")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "How many coins to return")
                }
            },
            required = emptyList(),
        ),
        toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true),
    ) { request ->
        val vsCurrency = request.arguments?.get("vsCurrency")?.jsonPrimitive?.content ?: "usd"
        val limit = request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 5
        val result = fetchMarketSnapshot(httpClient, vsCurrency, limit.coerceIn(1, 20))
        CallToolResult(content = listOf(TextContent(result)))
    }

    addTool(
        name = "getCoinOhlc",
        description = "Get OHLC data for one coin for charting",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("symbol") {
                    put("type", "string")
                    put("description", "Coin symbol")
                }
                putJsonObject("vsCurrency") {
                    put("type", "string")
                    put("description", "Fiat currency, default usd")
                }
                putJsonObject("days") {
                    put("type", "string")
                    put("description", "Range supported by CoinGecko, for example 1, 7, 30")
                }
            },
            required = listOf("symbol"),
        ),
        toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true),
    ) { request ->
        val symbol = request.arguments?.get("symbol")?.jsonPrimitive?.content ?: "BTC"
        val vsCurrency = request.arguments?.get("vsCurrency")?.jsonPrimitive?.content ?: "usd"
        val days = request.arguments?.get("days")?.jsonPrimitive?.content ?: "1"
        val id = coinIdFromSymbol(symbol)
        val result = fetchOhlc(httpClient, id, vsCurrency, days)
        CallToolResult(content = listOf(TextContent(result)))
    }
}
