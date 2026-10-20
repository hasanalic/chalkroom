package app.chalkroom.shared

import kotlinx.serialization.json.Json

/**
 * The app's wire format. Nulls are left out of request bodies, so every nullable DTO field must
 * default to null or the server rejects the request.
 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
