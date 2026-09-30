package it.melodia.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal val JsonElement?.obj: JsonObject? get() = this as? JsonObject
internal val JsonElement?.arr: JsonArray? get() = this as? JsonArray
internal val JsonElement?.str: String?
    get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

/** Follows a path of object keys; integers in the path index into arrays. */
internal fun JsonElement?.at(vararg path: Any): JsonElement? {
    var cur: JsonElement? = this
    for (p in path) {
        cur = when (p) {
            is String -> cur.obj?.get(p)
            is Int -> cur.arr?.getOrNull(p)
            else -> null
        }
        if (cur == null) return null
    }
    return cur
}

/** Depth-first search for every object stored under [key]. */
internal fun JsonElement?.findAll(key: String, out: MutableList<JsonObject> = mutableListOf()): List<JsonObject> {
    when (this) {
        is JsonObject -> for ((k, v) in this) {
            if (k == key && v is JsonObject) out.add(v)
            v.findAll(key, out)
        }
        is JsonArray -> for (v in this) v.findAll(key, out)
        else -> {}
    }
    return out
}

internal fun JsonElement?.findFirst(key: String): JsonElement? {
    when (this) {
        is JsonObject -> {
            this[key]?.let { return it }
            for (v in values) v.findFirst(key)?.let { return it }
        }
        is JsonArray -> for (v in this) v.findFirst(key)?.let { return it }
        else -> {}
    }
    return null
}

/** Concatenated text of a `{runs:[...]}` or `{simpleText:...}` object. */
internal fun JsonElement?.text(): String? {
    val o = obj ?: return null
    o["simpleText"].str?.let { return it }
    o["content"].str?.let { return it }
    val runs = o["runs"].arr ?: return null
    return runs.joinToString("") { it.at("text").str.orEmpty() }.takeIf { it.isNotEmpty() }
}

internal fun JsonElement?.runs(): List<JsonObject> = obj?.get("runs").arr?.mapNotNull { it.obj } ?: emptyList()
