package com.example.movexa.data.repository

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.Query

/**
 * Fluent builder for constructing Firestore compound queries.
 *
 * Provides a type-safe, chainable API for building complex queries
 * without verbose Firestore method chains.
 *
 * Usage:
 * ```kotlin
 * val query = FirestoreQueryBuilder(collectionRef)
 *     .whereEquals("companyId", companyId)
 *     .whereEquals("status", "ACTIVE")
 *     .orderByDescending("createdAt")
 *     .limitTo(20)
 *     .build()
 * ```
 *
 * Can also be used with [BaseFirestoreRepository.query]:
 * ```kotlin
 * query { ref ->
 *     FirestoreQueryBuilder(ref)
 *         .whereEquals("companyId", companyId)
 *         .whereIn("status", listOf("CREATED", "ASSIGNED"))
 *         .orderByDescending("createdAt")
 *         .build()
 * }
 * ```
 */
class FirestoreQueryBuilder(
    private val collectionRef: CollectionReference
) {

    private var query: Query = collectionRef
    private val appliedFilters = mutableListOf<String>()

    // ═══════════════════════════════════════════════════════════
    // EQUALITY FILTERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Filter where field equals value.
     */
    fun whereEquals(field: String, value: Any): FirestoreQueryBuilder {
        query = query.whereEqualTo(field, value)
        appliedFilters.add("$field == $value")
        return this
    }

    /**
     * Filter where field does not equal value.
     */
    fun whereNotEquals(field: String, value: Any): FirestoreQueryBuilder {
        query = query.whereNotEqualTo(field, value)
        appliedFilters.add("$field != $value")
        return this
    }

    // ═══════════════════════════════════════════════════════════
    // COMPARISON FILTERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Filter where field is greater than value.
     */
    fun whereGreaterThan(field: String, value: Any): FirestoreQueryBuilder {
        query = query.whereGreaterThan(field, value)
        appliedFilters.add("$field > $value")
        return this
    }

    /**
     * Filter where field is greater than or equal to value.
     */
    fun whereGreaterThanOrEqual(field: String, value: Any): FirestoreQueryBuilder {
        query = query.whereGreaterThanOrEqualTo(field, value)
        appliedFilters.add("$field >= $value")
        return this
    }

    /**
     * Filter where field is less than value.
     */
    fun whereLessThan(field: String, value: Any): FirestoreQueryBuilder {
        query = query.whereLessThan(field, value)
        appliedFilters.add("$field < $value")
        return this
    }

    /**
     * Filter where field is less than or equal to value.
     */
    fun whereLessThanOrEqual(field: String, value: Any): FirestoreQueryBuilder {
        query = query.whereLessThanOrEqualTo(field, value)
        appliedFilters.add("$field <= $value")
        return this
    }

    /**
     * Filter where field value falls within a range (inclusive).
     */
    fun whereBetween(field: String, start: Any, end: Any): FirestoreQueryBuilder {
        query = query
            .whereGreaterThanOrEqualTo(field, start)
            .whereLessThanOrEqualTo(field, end)
        appliedFilters.add("$field BETWEEN $start AND $end")
        return this
    }

    // ═══════════════════════════════════════════════════════════
    // ARRAY / IN FILTERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Filter where field value is in the given list (max 30 values).
     */
    fun whereIn(field: String, values: List<Any>): FirestoreQueryBuilder {
        require(values.size <= 30) { "whereIn supports max 30 values, got ${values.size}" }
        query = query.whereIn(field, values)
        appliedFilters.add("$field IN [${values.size} values]")
        return this
    }

    /**
     * Filter where field value is NOT in the given list (max 10 values).
     */
    fun whereNotIn(field: String, values: List<Any>): FirestoreQueryBuilder {
        require(values.size <= 10) { "whereNotIn supports max 10 values, got ${values.size}" }
        query = query.whereNotIn(field, values)
        appliedFilters.add("$field NOT IN [${values.size} values]")
        return this
    }

    /**
     * Filter where array field contains a value.
     */
    fun whereArrayContains(field: String, value: Any): FirestoreQueryBuilder {
        query = query.whereArrayContains(field, value)
        appliedFilters.add("$field CONTAINS $value")
        return this
    }

    /**
     * Filter where array field contains any of the values (max 30).
     */
    fun whereArrayContainsAny(field: String, values: List<Any>): FirestoreQueryBuilder {
        require(values.size <= 30) { "whereArrayContainsAny supports max 30 values" }
        query = query.whereArrayContainsAny(field, values)
        appliedFilters.add("$field CONTAINS_ANY [${values.size} values]")
        return this
    }

    // ═══════════════════════════════════════════════════════════
    // ORDERING
    // ═══════════════════════════════════════════════════════════

    /**
     * Order results by field ascending.
     */
    fun orderByAscending(field: String): FirestoreQueryBuilder {
        query = query.orderBy(field, Query.Direction.ASCENDING)
        appliedFilters.add("ORDER BY $field ASC")
        return this
    }

    /**
     * Order results by field descending.
     */
    fun orderByDescending(field: String): FirestoreQueryBuilder {
        query = query.orderBy(field, Query.Direction.DESCENDING)
        appliedFilters.add("ORDER BY $field DESC")
        return this
    }

    /**
     * Order by field with configurable direction.
     */
    fun orderBy(field: String, descending: Boolean = false): FirestoreQueryBuilder {
        return if (descending) orderByDescending(field) else orderByAscending(field)
    }

    // ═══════════════════════════════════════════════════════════
    // PAGINATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Limit the number of results.
     */
    fun limitTo(count: Int): FirestoreQueryBuilder {
        require(count > 0) { "Limit must be positive, got $count" }
        query = query.limit(count.toLong())
        appliedFilters.add("LIMIT $count")
        return this
    }

    /**
     * Limit to the last N results (requires at least one orderBy).
     */
    fun limitToLast(count: Int): FirestoreQueryBuilder {
        require(count > 0) { "Limit must be positive, got $count" }
        query = query.limitToLast(count.toLong())
        appliedFilters.add("LIMIT_LAST $count")
        return this
    }

    /**
     * Start after a specific document snapshot.
     */
    fun startAfter(vararg fieldValues: Any): FirestoreQueryBuilder {
        query = query.startAfter(*fieldValues)
        appliedFilters.add("START_AFTER [${fieldValues.size} values]")
        return this
    }

    /**
     * Start at a specific document snapshot.
     */
    fun startAt(vararg fieldValues: Any): FirestoreQueryBuilder {
        query = query.startAt(*fieldValues)
        appliedFilters.add("START_AT [${fieldValues.size} values]")
        return this
    }

    /**
     * End before a specific document snapshot.
     */
    fun endBefore(vararg fieldValues: Any): FirestoreQueryBuilder {
        query = query.endBefore(*fieldValues)
        appliedFilters.add("END_BEFORE [${fieldValues.size} values]")
        return this
    }

    /**
     * End at a specific document snapshot.
     */
    fun endAt(vararg fieldValues: Any): FirestoreQueryBuilder {
        query = query.endAt(*fieldValues)
        appliedFilters.add("END_AT [${fieldValues.size} values]")
        return this
    }

    // ═══════════════════════════════════════════════════════════
    // CONDITIONAL FILTERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Apply a filter only if the condition is true.
     * Useful for optional filter parameters.
     */
    fun whereEqualsIf(
        field: String,
        value: Any?,
        condition: Boolean = value != null
    ): FirestoreQueryBuilder {
        if (condition && value != null) {
            whereEquals(field, value)
        }
        return this
    }

    /**
     * Apply a builder block only if the condition is true.
     */
    fun applyIf(
        condition: Boolean,
        block: FirestoreQueryBuilder.() -> Unit
    ): FirestoreQueryBuilder {
        if (condition) {
            block()
        }
        return this
    }

    // ═══════════════════════════════════════════════════════════
    // BUILD
    // ═══════════════════════════════════════════════════════════

    /**
     * Build and return the constructed query.
     */
    fun build(): Query = query

    /**
     * Get a summary of applied filters for debugging.
     */
    fun describe(): String = appliedFilters.joinToString(" | ")

    override fun toString(): String = "FirestoreQueryBuilder(${describe()})"
}

/**
 * Extension function to create a [FirestoreQueryBuilder] from a [CollectionReference].
 */
fun CollectionReference.buildQuery(): FirestoreQueryBuilder = FirestoreQueryBuilder(this)
