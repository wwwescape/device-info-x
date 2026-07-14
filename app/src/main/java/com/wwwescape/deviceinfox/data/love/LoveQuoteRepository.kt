package com.wwwescape.deviceinfox.data.love

import android.content.Context
import org.json.JSONArray

object LoveQuoteRepository {
    private const val ASSET_PATH = "love_quotes.json"

    @Volatile
    private var cache: List<String>? = null

    fun randomQuote(context: Context): String {
        val quotes = loadQuotes(context)
        return if (quotes.isEmpty()) "" else quotes.random()
    }

    private fun loadQuotes(context: Context): List<String> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val loaded = runCatching {
                val json = context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val array = JSONArray(json)
                buildList(array.length()) {
                    for (i in 0 until array.length()) add(array.getString(i))
                }
            }.getOrDefault(emptyList())
            cache = loaded
            return loaded
        }
    }
}
