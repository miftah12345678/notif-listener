package com.veyra.notifmonitor.domain

import com.veyra.notifmonitor.core.NormalizedNotificationEvent
import org.json.JSONObject

/**
 * Registry holding different notification parsers for specific apps.
 */
object ParserRegistry {

    private val parsers = mutableMapOf<String, NotificationParser>()

    init {
        // Register default parsers
        parsers["default"] = GenericNotificationParser()
        // Example logic for V2: parsers["dana"] = DanaTransactionParser()
    }

    /**
     * Finds the applicable parser by ID and extracts structured JSON data.
     * Uses GenericNotificationParser if unknown ID.
     */
    fun parse(parserId: String, event: NormalizedNotificationEvent): String? {
        val parser = parsers[parserId] ?: GenericNotificationParser()
        
        return try {
            parser.parse(event)
        } catch (e: Exception) {
            // Safely swallow parser crashes. Never lose the raw event!
            "{ \"error\": \"Parser failed: ${e.message}\" }"
        }
    }
}

interface NotificationParser {
    fun parse(event: NormalizedNotificationEvent): String?
}

class GenericNotificationParser : NotificationParser {
    override fun parse(event: NormalizedNotificationEvent): String? {
        // A generic parser might just extract basic known shapes or do nothing
        return null
    }
}
