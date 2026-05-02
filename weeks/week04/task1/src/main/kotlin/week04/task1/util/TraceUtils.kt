package week04.task1.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import week04.task1.model.TraceStep
import week04.task1.model.TurnTrace
import kotlin.system.measureTimeMillis

suspend fun <T> timedStep(trace: TurnTrace, name: String, block: suspend () -> T): T {
    var result: T
    val elapsed = measureTimeMillis {
        result = block()
    }
    trace.steps += TraceStep(name = name, latencyMs = elapsed, status = "ok")
    return result
}

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
