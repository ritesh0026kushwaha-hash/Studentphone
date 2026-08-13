package com.example.studentphone

import android.content.Context
import org.json.JSONObject

class ContactsManager(context: Context) {

    private val prefs = context.getSharedPreferences("contacts", Context.MODE_PRIVATE)

    fun all(): Map<String, String> {
        val json = prefs.getString("list", "{}") ?: "{}"
        return try {
            val obj = JSONObject(json)
            val map = LinkedHashMap<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.getString(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getNumber(name: String): String? {
        val all = all()
        all.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.let { return it.value }
        val lower = name.lowercase()
        return all.entries.firstOrNull { lower.contains(it.key.lowercase()) }?.value
    }

    fun displayName(name: String): String =
        all().keys.firstOrNull { it.equals(name, ignoreCase = true) } ?: name

    fun add(name: String, number: String) {
        val map = all().toMutableMap()
        map[name.trim()] = number.trim()
        prefs.edit().putString("list", JSONObject(map).toString()).apply()
    }
}
