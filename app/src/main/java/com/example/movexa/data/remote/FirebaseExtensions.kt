@file:Suppress("UNCHECKED_CAST")

package com.example.movexa.data.remote

import com.google.firebase.database.DataSnapshot
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot

/**
 * Extension functions for safe parsing of Firebase data.
 *
 * Provides null-safe, type-safe extraction of values from
 * Firestore DocumentSnapshot, QuerySnapshot, and Realtime Database DataSnapshot.
 * Prevents ClassCastException and NullPointerException in production.
 */

// ═══════════════════════════════════════════════════════════════
// Firestore DocumentSnapshot Extensions
// ═══════════════════════════════════════════════════════════════

/**
 * Safely get a String from a Firestore document.
 */
fun DocumentSnapshot.safeString(key: String, default: String = ""): String {
    return try {
        getString(key) ?: default
    } catch (e: Exception) {
        default
    }
}

/**
 * Safely get a Long from a Firestore document.
 */
fun DocumentSnapshot.safeLong(key: String, default: Long = 0L): Long {
    return try {
        getLong(key) ?: default
    } catch (e: Exception) {
        default
    }
}

/**
 * Safely get an Int from a Firestore document.
 */
fun DocumentSnapshot.safeInt(key: String, default: Int = 0): Int {
    return try {
        getLong(key)?.toInt() ?: default
    } catch (e: Exception) {
        default
    }
}

/**
 * Safely get a Double from a Firestore document.
 */
fun DocumentSnapshot.safeDouble(key: String, default: Double = 0.0): Double {
    return try {
        getDouble(key) ?: default
    } catch (e: Exception) {
        default
    }
}

/**
 * Safely get a Float from a Firestore document.
 */
fun DocumentSnapshot.safeFloat(key: String, default: Float = 0f): Float {
    return try {
        getDouble(key)?.toFloat() ?: default
    } catch (e: Exception) {
        default
    }
}

/**
 * Safely get a Boolean from a Firestore document.
 */
fun DocumentSnapshot.safeBoolean(key: String, default: Boolean = false): Boolean {
    return try {
        getBoolean(key) ?: default
    } catch (e: Exception) {
        default
    }
}

/**
 * Safely get a nested Map from a Firestore document.
 */
fun DocumentSnapshot.safeMap(key: String): Map<String, Any> {
    return try {
        (get(key) as? Map<String, Any>) ?: emptyMap()
    } catch (e: Exception) {
        emptyMap()
    }
}

/**
 * Safely get a List of Strings from a Firestore document.
 */
fun DocumentSnapshot.safeStringList(key: String): List<String> {
    return try {
        (get(key) as? List<String>) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Convert a DocumentSnapshot to a data map (null-safe).
 */
fun DocumentSnapshot.toSafeMap(): Map<String, Any?> {
    return data ?: emptyMap()
}

/**
 * Map a DocumentSnapshot to a model using a transform function.
 * Returns null if the document doesn't exist or parsing fails.
 */
fun <T> DocumentSnapshot.toModel(mapper: (Map<String, Any?>) -> T): T? {
    return try {
        if (exists()) {
            data?.let { mapper(it) }
        } else null
    } catch (e: Exception) {
        null
    }
}

// ═══════════════════════════════════════════════════════════════
// Firestore QuerySnapshot Extensions
// ═══════════════════════════════════════════════════════════════

/**
 * Map all documents in a QuerySnapshot to models.
 * Silently skips documents that fail to parse.
 */
fun <T> QuerySnapshot.toModelList(mapper: (Map<String, Any?>) -> T): List<T> {
    return documents.mapNotNull { doc ->
        try {
            doc.data?.let { mapper(it) }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Map all documents, including their Firestore document IDs.
 */
fun <T> QuerySnapshot.toModelListWithId(
    mapper: (String, Map<String, Any?>) -> T
): List<T> {
    return documents.mapNotNull { doc ->
        try {
            doc.data?.let { mapper(doc.id, it) }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Get the first document mapped to a model, or null.
 */
fun <T> QuerySnapshot.firstModel(mapper: (Map<String, Any?>) -> T): T? {
    return documents.firstOrNull()?.toModel(mapper)
}

// ═══════════════════════════════════════════════════════════════
// Realtime Database DataSnapshot Extensions
// ═══════════════════════════════════════════════════════════════

/**
 * Safely get a String from a Realtime Database snapshot.
 */
fun DataSnapshot.safeString(key: String, default: String = ""): String {
    return try {
        child(key).getValue(String::class.java) ?: default
    } catch (e: Exception) {
        default
    }
}

/**
 * Safely get a Long from a Realtime Database snapshot.
 */
fun DataSnapshot.safeLong(key: String, default: Long = 0L): Long {
    return try {
        child(key).getValue(Long::class.java) ?: default
    } catch (e: Exception) {
        default
    }
}

/**
 * Safely get a Double from a Realtime Database snapshot.
 */
fun DataSnapshot.safeDouble(key: String, default: Double = 0.0): Double {
    return try {
        child(key).getValue(Double::class.java) ?: default
    } catch (e: Exception) {
        default
    }
}

/**
 * Safely get a Float from a Realtime Database snapshot.
 */
fun DataSnapshot.safeFloat(key: String, default: Float = 0f): Float {
    return try {
        child(key).getValue(Double::class.java)?.toFloat() ?: default
    } catch (e: Exception) {
        default
    }
}

/**
 * Safely get a Boolean from a Realtime Database snapshot.
 */
fun DataSnapshot.safeBoolean(key: String, default: Boolean = false): Boolean {
    return try {
        child(key).getValue(Boolean::class.java) ?: default
    } catch (e: Exception) {
        default
    }
}

/**
 * Convert a Realtime Database snapshot to a data map.
 */
fun DataSnapshot.toSafeMap(): Map<String, Any?> {
    return try {
        (value as? Map<String, Any?>) ?: emptyMap()
    } catch (e: Exception) {
        emptyMap()
    }
}

/**
 * Map a DataSnapshot to a model using a transform function.
 */
fun <T> DataSnapshot.toModel(mapper: (Map<String, Any?>) -> T): T? {
    return try {
        if (exists()) {
            (value as? Map<String, Any?>)?.let { mapper(it) }
        } else null
    } catch (e: Exception) {
        null
    }
}

/**
 * Map all children of a DataSnapshot to models.
 */
fun <T> DataSnapshot.toModelList(mapper: (Map<String, Any?>) -> T): List<T> {
    return children.mapNotNull { child ->
        try {
            (child.value as? Map<String, Any?>)?.let { mapper(it) }
        } catch (e: Exception) {
            null
        }
    }
}
