package week04.task3.config

import kotlinx.serialization.json.Json

object JsonConfig {
    val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
}
