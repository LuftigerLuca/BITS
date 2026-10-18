package eu.luftiger.bits.shared

import kotlinx.serialization.json.Json

/** JSON format shared by backend and bot for REST payloads and events. */
val BitsJson =
    Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }
