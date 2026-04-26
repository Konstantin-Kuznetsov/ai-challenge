package week03.shared.storage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import week03.shared.model.InvariantItem
import week03.shared.model.InvariantsState

class InvariantsStore(private val path: Path) {
    private val lock = Any()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun load(): InvariantsState = synchronized(lock) {
        if (!path.exists()) {
            val initial = defaultState()
            saveUnlocked(initial)
            return initial
        }
        return try {
            val raw = Files.readString(path)
            if (raw.isBlank()) {
                val initial = defaultState()
                saveUnlocked(initial)
                initial
            } else {
                json.decodeFromString<InvariantsState>(raw)
            }
        } catch (_: Exception) {
            val initial = defaultState()
            saveUnlocked(initial)
            initial
        }
    }

    fun save(state: InvariantsState) = synchronized(lock) {
        saveUnlocked(state)
    }

    fun resetToDefaults() = save(defaultState())

    /** One system block listing all enabled invariants (separate from dialog). */
    fun formatPromptBlock(): String {
        val enabled = load().items.filter { it.enabled }
        if (enabled.isEmpty()) {
            return ""
        }
        return buildString {
            appendLine("Enabled invariants (must not be violated):")
            enabled.forEach { inv ->
                append("- [")
                append(inv.category)
                append("] ")
                append(inv.id)
                append(": ")
                appendLine(inv.text.trim())
            }
        }.trimEnd()
    }

    private fun saveUnlocked(state: InvariantsState) {
        Files.createDirectories(path.parent)
        Files.writeString(path, json.encodeToString(state))
    }

    companion object {
        fun defaultState(): InvariantsState = InvariantsState(
            items = listOf(
                InvariantItem(
                    id = "travel-profile-align",
                    category = "PROFILE",
                    text = "Соблюдать выбранный в интерфейсе профиль путешественника: " +
                        "бюджетный — приоритет экономии, пересадок и простых вариантов; " +
                        "любитель дорогого пляжного отдыха — море в тёплом климате, комфортные перелёты и отели, " +
                        "без навязывания противоположного стиля без явной просьбы пользователя.",
                    enabled = true,
                    queryTriggers = emptyList(),
                    responseTriggers = emptyList(),
                ),
                InvariantItem(
                    id = "travel-booking-truth",
                    category = "TRUST",
                    text = "Не утверждать, что для пользователя уже куплены билеты, оформлена страховка или бронь отеля; " +
                        "не выдумывать номера бронирований, PNR и «гарантированные» места без оплаты пользователем.",
                    enabled = true,
                    queryTriggers = listOf(
                        "подделай визу",
                        "подделать визу",
                        "подделку визы",
                        "фальшивую визу",
                        "оформи билет без оплаты",
                        "забронируй от моего имени без карты",
                    ),
                    responseTriggers = listOf(
                        "билет уже куплен за вас",
                        "бронирование подтверждено от вашего имени",
                        "PNR:",
                        "я уже оплатил за вас",
                    ),
                ),
                InvariantItem(
                    id = "travel-price-honesty",
                    category = "PRICING",
                    text = "Цены на билеты, отели и экскурсии давать как ориентир («от …», «примерно») и напоминать проверить актуальность на сайте перевозчика или агрегатора.",
                    enabled = true,
                    queryTriggers = emptyList(),
                    responseTriggers = emptyList(),
                ),
                InvariantItem(
                    id = "travel-legal-docs",
                    category = "COMPLIANCE",
                    text = "Для поездок за границу напоминать про визы, срок действия паспорта, страховку и официальные источники (посольство, IATA Timatic). Не подсказывать обход законных требований.",
                    enabled = true,
                    queryTriggers = listOf(
                        "нелегально пересечь",
                        "обойти погранконтроль",
                        "подделай штамп в паспорт",
                        "подделать штамп",
                    ),
                    responseTriggers = listOf(
                        "можно без визы нелегально",
                        "обойдём пограничников",
                    ),
                ),
                InvariantItem(
                    id = "travel-safety-realism",
                    category = "SAFETY",
                    text = "Не рекомендовать опасные маршруты, несуществующие рейсы или «серые» перевозчики; предупреждать о рисках стыковок и невозвратных тарифов.",
                    enabled = true,
                    queryTriggers = emptyList(),
                    responseTriggers = emptyList(),
                ),
            ),
        )
    }
}
