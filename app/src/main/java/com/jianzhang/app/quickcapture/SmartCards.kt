package com.jianzhang.app.quickcapture

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class SmartCardType { PICKUP, PACKAGE, TRAVEL }

data class SmartCard(
    val id: String,
    val type: SmartCardType,
    val title: String,
    val content: String,
    val fingerprint: String,
    val createdAt: Long,
    val completed: Boolean = false
)

class SmartCardStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun activeCards(): List<SmartCard> = read().filterNot(SmartCard::completed).sortedByDescending(SmartCard::createdAt)

    fun upsert(candidate: SmartCard): SmartCard {
        val cards = read().toMutableList()
        val existing = cards.firstOrNull { !it.completed && it.fingerprint == candidate.fingerprint }
        if (existing != null) return existing
        cards += candidate
        write(cards)
        return candidate
    }

    fun complete(id: String): SmartCard? {
        val cards = read().toMutableList()
        val card = cards.firstOrNull { it.id == id } ?: return null
        cards.removeAll { it.id == id }
        write(cards)
        return card.copy(completed = true)
    }

    /**
     * Seeds only the non-accounting cards used by the competition recording.
     * The pickup card is intentionally created by the screenshot flow itself;
     * these two cards make the multi-scenario result visible after the first
     * capture without pretending that they came from a real user session.
     */
    fun replaceCompetitionDemoCards() {
        val existing = read().filterNot(::isCompetitionDemoCard)
        val now = System.currentTimeMillis()
        write(
            existing + listOf(
                SmartCard(
                    id = "competition-demo-package",
                    type = SmartCardType.PACKAGE,
                    title = "菜鸟驿站 · 学校东门",
                    content = "取件码 2-3-4567\n学校东门 · A12货架\n请于今晚 21:00 前取件",
                    fingerprint = "$COMPETITION_DEMO_PREFIX|package|2-3-4567",
                    createdAt = now - 2_000L
                ),
                SmartCard(
                    id = "competition-demo-travel",
                    type = SmartCardType.TRAVEL,
                    title = "G1375",
                    content = "长沙南 → 广州南\n18:32发车 · A12检票口\n05车 · 08A座",
                    fingerprint = "$COMPETITION_DEMO_PREFIX|travel|G1375|18:32",
                    createdAt = now - 1_000L
                )
            )
        )
    }

    fun clearCompetitionDemoCards(): List<String> {
        val all = read()
        val removedIds = all.filter(::isCompetitionDemoCard).map(SmartCard::id)
        if (removedIds.isNotEmpty()) {
            write(all.filterNot(::isCompetitionDemoCard))
        }
        return removedIds
    }

    fun observeActiveCards(): Flow<List<SmartCard>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CARDS) trySend(activeCards())
        }
        trySend(activeCards())
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun read(): List<SmartCard> = runCatching {
        val array = JSONArray(preferences.getString(KEY_CARDS, "[]"))
        buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                add(
                    SmartCard(
                        id = item.getString("id"),
                        type = SmartCardType.valueOf(item.getString("type")),
                        title = item.getString("title"),
                        content = item.getString("content"),
                        fingerprint = item.getString("fingerprint"),
                        createdAt = item.getLong("createdAt"),
                        completed = item.optBoolean("completed", false)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun write(cards: List<SmartCard>) {
        val array = JSONArray().apply {
            cards.forEach { card ->
                put(
                    JSONObject()
                        .put("id", card.id)
                        .put("type", card.type.name)
                        .put("title", card.title)
                        .put("content", card.content)
                        .put("fingerprint", card.fingerprint)
                        .put("createdAt", card.createdAt)
                        .put("completed", card.completed)
                )
            }
        }
        check(preferences.edit().putString(KEY_CARDS, array.toString()).commit()) { "当前事项保存失败" }
    }

    private fun isCompetitionDemoCard(card: SmartCard): Boolean =
        card.fingerprint.startsWith(COMPETITION_DEMO_PREFIX) ||
            card.fingerprint == COMPETITION_CAPTURED_PICKUP_FINGERPRINT ||
            card.fingerprint == LEGACY_COMPETITION_PACKAGE_FINGERPRINT ||
            card.fingerprint == LEGACY_COMPETITION_TRAVEL_FINGERPRINT

    companion object {
        fun newCard(type: SmartCardType, title: String, content: String, fingerprint: String) = SmartCard(
            id = UUID.randomUUID().toString(),
            type = type,
            title = title.take(80),
            content = content.take(240),
            fingerprint = fingerprint,
            createdAt = System.currentTimeMillis()
        )

        private const val NAME = "smart_cards"
        private const val KEY_CARDS = "active_cards_json"
        private const val COMPETITION_DEMO_PREFIX = "competition-demo"
        private const val COMPETITION_CAPTURED_PICKUP_FINGERPRINT = "pickup|麦当劳·五一广场店|A037"
        private const val LEGACY_COMPETITION_PACKAGE_FINGERPRINT = "package|菜鸟驿站|2-3-4567"
        private const val LEGACY_COMPETITION_TRAVEL_FINGERPRINT = "travel|G1375||18:32"
    }
}
