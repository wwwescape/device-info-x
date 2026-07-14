package com.wwwescape.deviceinfox.data.deviceos

import android.content.Context
import org.json.JSONObject

/**
 * Resolves a raw [android.os.Build.MODEL] (e.g. "AC2001") to its consumer marketing name (e.g.
 * "OnePlus Nord"), using Google Play's public supported-devices list bundled as a JSON asset
 * (see scripts/generate_device_names.py). Models absent from the list, or ambiguous across
 * multiple retail brandings, have no entry — callers should fall back to the raw model string.
 */
object DeviceNameResolver {
    private const val ASSET_PATH = "device_names.json"

    @Volatile
    private var cache: Map<String, String>? = null

    fun marketingNameFor(context: Context, model: String): String? = loadMap(context)[model]

    private fun loadMap(context: Context): Map<String, String> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val loaded = runCatching {
                val json = context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val obj = JSONObject(json)
                val keys = obj.keys()
                buildMap(obj.length()) {
                    for (key in keys) put(key, obj.getString(key))
                }
            }.getOrDefault(emptyMap())
            cache = loaded
            return loaded
        }
    }
}
