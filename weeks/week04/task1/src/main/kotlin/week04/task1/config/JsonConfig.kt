package week04.task1.config

import kotlinx.serialization.json.Json

object JsonConfig {
    val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
}
