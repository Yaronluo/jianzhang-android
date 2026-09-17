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
    }
}
