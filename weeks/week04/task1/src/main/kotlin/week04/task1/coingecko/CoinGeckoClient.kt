package week04.task1.coingecko

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import week04.task1.config.JsonConfig

fun createCoinGeckoHttpClient(): HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(JsonConfig.json)
    }
}

fun coinIdFromSymbol(symbol: String): String {
    return when (symbol.trim().uppercase()) {
        "BTC" -> "bitcoin"
        "ETH" -> "ethereum"
        "SOL" -> "solana"
        "BNB" -> "binancecoin"
        "XRP" -> "ripple"
        else -> symbol.lowercase()
    }
}

suspend fun fetchCoinPrice(httpClient: HttpClient, id: String, vsCurrency: String): String {
    val response = httpClient.get("https://api.coingecko.com/api/v3/coins/markets") {
        parameter("vs_currency", vsCurrency.lowercase())
        parameter("ids", id)
        parameter("order", "market_cap_desc")
        parameter("per_page", 1)
        parameter("page", 1)
        parameter("sparkline", false)
        contentType(ContentType.Application.Json)
    }
    val body: JsonElement = response.body()
    val coin = body.jsonArray.firstOrNull()?.jsonObject
    val price = coin?.get("current_price")?.jsonPrimitive?.doubleOrNull
    return JsonConfig.json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("coinId", id)
            put("coinName", coin?.get("name")?.jsonPrimitive?.content ?: id)
            put("symbol", coin?.get("symbol")?.jsonPrimitive?.content?.uppercase() ?: id.uppercase())
            put("vsCurrency", vsCurrency.lowercase())
            put("price", price ?: -1.0)
            put("priceUsd", price ?: -1.0)
            val image = coin?.get("image")?.jsonPrimitive?.contentOrNull
            if (image != null) put("imageUrl", image) else put("imageUrl", JsonNull)
            val change24h = coin?.get("price_change_percentage_24h")?.jsonPrimitive?.doubleOrNull
            if (change24h != null) put("change24h", JsonPrimitive(change24h)) else put("change24h", JsonNull)
        },
    )
}

suspend fun fetchTrending(httpClient: HttpClient): String {
    val response = httpClient.get("https://api.coingecko.com/api/v3/search/trending") {
        contentType(ContentType.Application.Json)
    }
    val body: JsonObject = response.body()
    return JsonConfig.json.encodeToString(JsonObject.serializer(), body)
}

suspend fun fetchMarketSnapshot(httpClient: HttpClient, vsCurrency: String, limit: Int): String {
    val response = httpClient.get("https://api.coingecko.com/api/v3/coins/markets") {
        parameter("vs_currency", vsCurrency.lowercase())
        parameter("order", "market_cap_desc")
        parameter("per_page", limit)
        parameter("page", 1)
        parameter("sparkline", false)
        contentType(ContentType.Application.Json)
    }
    val body: JsonElement = response.body()
    return JsonConfig.json.encodeToString(JsonElement.serializer(), body)
}

suspend fun fetchOhlc(httpClient: HttpClient, id: String, vsCurrency: String, days: String): String {
    val response = httpClient.get("https://api.coingecko.com/api/v3/coins/$id/ohlc") {
        parameter("vs_currency", vsCurrency.lowercase())
        parameter("days", days)
        contentType(ContentType.Application.Json)
    }
    val body: JsonElement = response.body()
    return JsonConfig.json.encodeToString(JsonElement.serializer(), body)
}
