package com.veyra.notifmonitor.core

import android.os.Bundle
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Model that represents a normalized event extracted from StatusBarNotification.
 */
data class NormalizedNotificationEvent(
    val packageName: String,
    val notificationKey: String,
    val title: String?,
    val text: String?,
    val rawExtras: Bundle?, // Passed here raw, sanitized later during processing
    val postedAt: Long,
    val receivedAt: Long,
    val category: String? = null
)

object FingerprintUtils {
    /**
     * Canonical encoding: packageName|notificationKey|postedAt|title|text
     * Output: SHA-256 Hex String
     */
    fun generate(
        packageName: String,
        notificationKey: String,
        postedAt: Long,
        title: String?,
        text: String?
    ): String {
        val p = packageName
        val k = notificationKey
        val t = title ?: ""
        val txt = text ?: ""

        val pLen = p.toByteArray(Charsets.UTF_8).size
        val kLen = k.toByteArray(Charsets.UTF_8).size
        val tLen = t.toByteArray(Charsets.UTF_8).size
        val txtLen = txt.toByteArray(Charsets.UTF_8).size

        // Format: len(pkg):pkg|len(key):key|postedAt|len(title):title|len(text):text
        val payload = "$pLen:$p|$kLen:$k|$postedAt|$tLen:$t|$txtLen:$txt"
        
        return hashSha256(payload)
    }

    private fun hashSha256(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

object ExtrasSanitizer {
    private const val MAX_SERIALIZED_SIZE = 50 * 1024 // 50KB

    /**
     * Converts a Bundle to JSON while aggressively stripping complex Parcelables, 
     * bitmaps, and intents that are not needed and could cause crashes or bloat.
     */
    fun sanitizeToJson(bundle: Bundle?): String? {
        if (bundle == null) return null
        
        val json = JSONObject()
        try {
            for (key in bundle.keySet()) {
                val value = bundle.get(key)
                if (isAllowedPrimitive(value)) {
                    json.put(key, value)
                } else if (value is Array<*> && value.isArrayOf<String>()) {
                    val arr = org.json.JSONArray()
                    for (v in value) arr.put(v)
                    json.put(key, arr)
                } else if (value is ArrayList<*> && value.all { it is String }) {
                    val arr = org.json.JSONArray()
                    for (v in value) arr.put(v)
                    json.put(key, arr)
                }
                // Silently drop anything else (Intents, Bitmaps, Custom Parcelables)
            }
        } catch (e: Exception) {
            // Defensively catch any unparceling errors from third-party bad intents
            return "{ \"error\": \"Failed to sanitize extras: ${e.message}\" }"
        }
        
        val serialized = json.toString()
        if (serialized.toByteArray(Charsets.UTF_8).size > MAX_SERIALIZED_SIZE) {
            return "{ \"error\": \"Extras exceeded maximum allowed size of 50KB\" }"
        }
        return serialized
    }

    private fun isAllowedPrimitive(value: Any?): Boolean {
        return value is String || 
               value is Int || 
               value is Long || 
               value is Boolean || 
               value is Double || 
               value is Float
    }
}
