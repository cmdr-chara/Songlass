package dev.cmdrchara.songlass.core.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Safe readers over [JsonElement] trees.
 *
 * Remote payloads are untrusted: every function here returns null when a value is missing or has an
 * unexpected type, and none of them throws for malformed input. No `@Serializable` model is used and
 * the kotlinx-serialization compiler plugin is not applied: callers pick exactly the fields they
 * need from the tree.
 *
 * Types are strict on purpose. A JSON number is not coerced to a [String] and vice versa, so a
 * provider that changes the type of a field becomes a missing value instead of a silently wrong
 * value.
 */

/** Parses [text] as JSON, or returns null when it is not valid JSON. */
fun parseJsonOrNull(text: String): JsonElement? = try {
    Json.parseToJsonElement(text)
} catch (e: Exception) {
    // JsonDecodingException / IllegalArgumentException: a broken payload is unavailable data.
    null
}

/** This element as a JSON object, or null for any other JSON type. */
fun JsonElement?.asJsonObjectOrNull(): JsonObject? = this as? JsonObject

/** This element as a JSON array, or null for any other JSON type. */
fun JsonElement?.asJsonArrayOrNull(): JsonArray? = this as? JsonArray

/**
 * The text of a JSON string primitive, or null when the value is a number, boolean, null or absent.
 */
fun JsonElement?.asStringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** A JSON number as [Int]; null when absent, fractional, out of range or sent as a string. */
fun JsonElement?.asIntOrNull(): Int? = nonStringContent()?.toIntOrNull()

/** A JSON number as [Long]; null when absent, fractional, out of range or sent as a string. */
fun JsonElement?.asLongOrNull(): Long? = nonStringContent()?.toLongOrNull()

/** A JSON number as [Double]; overflowed literals (infinite) are rejected. */
fun JsonElement?.asDoubleOrNull(): Double? =
    nonStringContent()?.toDoubleOrNull()?.takeIf { it.isFinite() }

/** A JSON boolean literal, or null when the value is a string, number or null. */
fun JsonElement?.asBooleanOrNull(): Boolean? =
    nonStringContent()?.toBooleanStrictOrNull()

/** The element stored under [key], or null when the key is absent. */
fun JsonObject.elementOrNull(key: String): JsonElement? = this[key]

/** The object stored under [key], or null when the key is absent or holds another JSON type. */
fun JsonObject.objectOrNull(key: String): JsonObject? = this[key].asJsonObjectOrNull()

/** The array stored under [key], or null when the key is absent or holds another JSON type. */
fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key].asJsonArrayOrNull()

/** The string stored under [key], strictly typed. */
fun JsonObject.stringOrNull(key: String): String? = this[key].asStringOrNull()

/** The integer stored under [key], strictly typed. */
fun JsonObject.intOrNull(key: String): Int? = this[key].asIntOrNull()

/** The long stored under [key], strictly typed. */
fun JsonObject.longOrNull(key: String): Long? = this[key].asLongOrNull()

/** The double stored under [key], strictly typed. */
fun JsonObject.doubleOrNull(key: String): Double? = this[key].asDoubleOrNull()

/** The boolean stored under [key], strictly typed. */
fun JsonObject.booleanOrNull(key: String): Boolean? = this[key].asBooleanOrNull()

/**
 * GET + parse in one step: null on any failure, on non-2xx statuses and when the body is not JSON.
 * Callers that need the failure kind (for example to retry) use [HttpTransport.get] directly.
 */
suspend fun HttpTransport.getJsonElement(
    url: String,
    headers: Map<String, String> = emptyMap(),
): JsonElement? = (get(url, headers) as? HttpResult.Success)?.body?.let(::parseJsonOrNull)

private fun JsonElement?.nonStringContent(): String? =
    (this as? JsonPrimitive)?.takeIf { !it.isString }?.content
