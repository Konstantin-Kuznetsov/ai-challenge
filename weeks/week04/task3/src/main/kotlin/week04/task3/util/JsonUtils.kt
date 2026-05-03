package week04.task3.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

fun jsonElementToAny(element: JsonElement): Any? {
    return when (element) {
        is JsonPrimitive -> {
            when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.booleanOrNull
                element.longOrNull != null -> element.longOrNull
                element.doubleOrNull != null -> element.doubleOrNull
                else -> element.content
            }
        }

        is JsonObject -> element.mapValues { (_, value) -> jsonElementToAny(value) }
        is JsonArray -> element.map { jsonElementToAny(it) }
    }
}
