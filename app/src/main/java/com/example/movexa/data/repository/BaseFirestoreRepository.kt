package com.example.movexa.data.repository

import com.example.movexa.data.model.ResultState
import com.example.movexa.data.remote.FirebaseProvider
import com.example.movexa.data.remote.toModel
import com.example.movexa.data.remote.toModelList
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Base repository for all Firestore collection operations.
 *
 * Provides generic CRUD operations templated on a data model type [T].
 * All Firestore repositories extend this to inherit standard operations
 * while adding collection-specific logic.
 *
 * Features:
 * - Type-safe CRUD with automatic mapping via [fromMap]/[toMap]
 * - Paginated queries with cursor-based pagination
 * - Real-time snapshot listeners exposed as Kotlin Flows
 * - Compound query support
 * - Batch write operations
 * - Consistent error handling via [BaseRepository.firebaseSafeCall]
 *
 * @param T The data model type this repository manages.
 */
abstract class BaseFirestoreRepository<T> : BaseRepository() {

    /**
     * The Firestore collection name this repository operates on.
     */
    protected abstract val collectionName: String

    /**
     * Map a Firestore document map to the model type.
     */
    protected abstract fun fromMap(map: Map<String, Any?>): T

    /**
     * Map a model instance to a Firestore document map.
     */
    protected abstract fun toMap(model: T): Map<String, Any?>

    /**
     * Extract the document ID from a model instance.
     */
    protected abstract fun getDocumentId(model: T): String

    // ─── Collection Reference ──────────────────────────────────

    /**
     * Get the Firestore collection reference.
     */
    protected val collectionRef: CollectionReference
        get() = FirebaseProvider.collection(collectionName)

    /**
     * Get a document reference by ID.
     */
    protected fun documentRef(documentId: String): DocumentReference {
        return collectionRef.document(documentId)
    }

    // ═══════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════

    /**
     * Create a new document with an auto-generated ID.
     * Returns the generated document ID.
     */
    suspend fun create(model: T): ResultState<String> = firebaseSafeCall {
        val docRef = collectionRef.document()
        val id = docRef.id
        val data = toMap(model).toMutableMap()
        // Inject the generated ID into the document
        data[getIdFieldName()] = id
        docRef.set(data).await()
        id
    }

    /**
     * Create a document with a specific ID.
     */
    suspend fun createWithId(documentId: String, model: T): ResultState<Unit> =
        firebaseSafeCall {
            collectionRef.document(documentId).set(toMap(model)).await()
        }

    /**
     * Create a document only if it doesn't already exist.
     */
    suspend fun createIfNotExists(
        documentId: String,
        model: T
    ): ResultState<Boolean> = firebaseSafeCall {
        val docRef = collectionRef.document(documentId)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) {
            docRef.set(toMap(model)).await()
            true
        } else {
            false
        }
    }

    // ═══════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════

    /**
     * Get a single document by ID.
     */
    suspend fun getById(documentId: String): ResultState<T?> = firebaseSafeCall {
        val snapshot = collectionRef.document(documentId).get().await()
        snapshot.toModel(::fromMap)
    }

    /**
     * Get a single document by ID, throwing if not found.
     */
    suspend fun getByIdOrThrow(documentId: String): ResultState<T> = firebaseSafeCall {
        val snapshot = collectionRef.document(documentId).get().await()
        snapshot.toModel(::fromMap)
            ?: throw NoSuchElementException("Document $documentId not found in $collectionName")
    }

    /**
     * Get all documents in the collection.
     */
    suspend fun getAll(): ResultState<List<T>> = firebaseSafeCall {
        val snapshot = collectionRef.get().await()
        snapshot.toModelList(::fromMap)
    }

    /**
     * Get documents matching a single field value.
     */
    suspend fun getByField(
        field: String,
        value: Any
    ): ResultState<List<T>> = firebaseSafeCall {
        val snapshot = collectionRef.whereEqualTo(field, value).get().await()
        snapshot.toModelList(::fromMap)
    }

    /**
     * Get a single document matching a field value.
     */
    suspend fun getFirstByField(
        field: String,
        value: Any
    ): ResultState<T?> = firebaseSafeCall {
        val snapshot = collectionRef
            .whereEqualTo(field, value)
            .limit(1)
            .get()
            .await()
        snapshot.documents.firstOrNull()?.toModel(::fromMap)
    }

    /**
     * Paginated query with cursor-based pagination.
     *
     * @param pageSize Number of documents per page.
     * @param orderByField Field to order by.
     * @param descending Whether to sort descending.
     * @param lastDocumentId ID of the last document from previous page (null for first page).
     */
    suspend fun getPaginated(
        pageSize: Int = 20,
        orderByField: String = "createdAt",
        descending: Boolean = true,
        lastDocumentId: String? = null
    ): ResultState<List<T>> = firebaseSafeCall {
        var query: Query = collectionRef
            .let { if (descending) it.orderBy(orderByField, Query.Direction.DESCENDING) else it.orderBy(orderByField) }
            .limit(pageSize.toLong())

        if (lastDocumentId != null) {
            val lastDoc = collectionRef.document(lastDocumentId).get().await()
            if (lastDoc.exists()) {
                query = query.startAfter(lastDoc)
            }
        }

        query.get().await().toModelList(::fromMap)
    }

    /**
     * Execute a custom query.
     */
    suspend fun query(
        queryBuilder: (CollectionReference) -> Query
    ): ResultState<List<T>> = firebaseSafeCall {
        val query = queryBuilder(collectionRef)
        query.get().await().toModelList(::fromMap)
    }

    /**
     * Count documents matching a query.
     */
    suspend fun count(
        queryBuilder: (CollectionReference) -> Query = { it }
    ): ResultState<Int> = firebaseSafeCall {
        val query = queryBuilder(collectionRef)
        query.get().await().size()
    }

    /**
     * Check if a document exists.
     */
    suspend fun exists(documentId: String): ResultState<Boolean> = firebaseSafeCall {
        collectionRef.document(documentId).get().await().exists()
    }

    // ═══════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════

    /**
     * Update a document entirely (overwrite).
     */
    suspend fun update(model: T): ResultState<Unit> = firebaseSafeCall {
        val id = getDocumentId(model)
        require(id.isNotBlank()) { "Document ID must not be blank for update" }
        collectionRef.document(id).set(toMap(model)).await()
    }

    /**
     * Update specific fields of a document.
     */
    suspend fun updateFields(
        documentId: String,
        fields: Map<String, Any?>
    ): ResultState<Unit> = firebaseSafeCall {
        val updates = fields.toMutableMap()
        updates["updatedAt"] = System.currentTimeMillis()
        collectionRef.document(documentId).update(updates).await()
    }

    /**
     * Update a single field of a document.
     */
    suspend fun updateField(
        documentId: String,
        field: String,
        value: Any?
    ): ResultState<Unit> = updateFields(documentId, mapOf(field to value))

    // ═══════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════

    /**
     * Delete a document by ID.
     */
    suspend fun delete(documentId: String): ResultState<Unit> = firebaseSafeCall {
        collectionRef.document(documentId).delete().await()
    }

    /**
     * Delete multiple documents by IDs (batched).
     */
    suspend fun deleteMultiple(documentIds: List<String>): ResultState<Unit> =
        firebaseSafeCall {
            val batch = FirebaseProvider.firestore.batch()
            documentIds.forEach { id ->
                batch.delete(collectionRef.document(id))
            }
            batch.commit().await()
        }

    // ═══════════════════════════════════════════════════════════
    // BATCH OPERATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Create multiple documents in a single batch.
     * Firestore limits: 500 operations per batch.
     */
    suspend fun createBatch(models: List<T>): ResultState<List<String>> =
        firebaseSafeCall {
            require(models.size <= 500) { "Batch limit is 500 operations" }
            val batch = FirebaseProvider.firestore.batch()
            val ids = mutableListOf<String>()
            models.forEach { model ->
                val docRef = collectionRef.document()
                ids.add(docRef.id)
                val data = toMap(model).toMutableMap()
                data[getIdFieldName()] = docRef.id
                batch.set(docRef, data)
            }
            batch.commit().await()
            ids
        }

    /**
     * Update multiple documents in a single batch.
     */
    suspend fun updateBatch(models: List<T>): ResultState<Unit> =
        firebaseSafeCall {
            require(models.size <= 500) { "Batch limit is 500 operations" }
            val batch = FirebaseProvider.firestore.batch()
            models.forEach { model ->
                val id = getDocumentId(model)
                require(id.isNotBlank()) { "All models must have document IDs for batch update" }
                batch.set(collectionRef.document(id), toMap(model))
            }
            batch.commit().await()
        }

    // ═══════════════════════════════════════════════════════════
    // REAL-TIME LISTENERS (Kotlin Flows)
    // ═══════════════════════════════════════════════════════════

    /**
     * Observe a single document in real-time.
     */
    fun observeDocument(documentId: String): Flow<ResultState<T?>> = callbackFlow {
        val listener = collectionRef.document(documentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(ResultState.Error(parseErrorMessage(error), error))
                    return@addSnapshotListener
                }
                val model = snapshot?.toModel(::fromMap)
                trySend(ResultState.Success(model))
            }
        awaitClose { listener.remove() }
    }

    /**
     * Observe a collection query in real-time.
     */
    fun observeCollection(
        queryBuilder: (CollectionReference) -> Query = { it }
    ): Flow<ResultState<List<T>>> = callbackFlow {
        val query = queryBuilder(collectionRef)
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(ResultState.Error(parseErrorMessage(error), error))
                return@addSnapshotListener
            }
            val models = snapshot?.toModelList(::fromMap) ?: emptyList()
            trySend(ResultState.Success(models))
        }
        awaitClose { listener.remove() }
    }

    /**
     * Observe documents matching a field value in real-time.
     */
    fun observeByField(
        field: String,
        value: Any
    ): Flow<ResultState<List<T>>> = observeCollection { ref ->
        ref.whereEqualTo(field, value)
    }

    // ─── Helpers ────────────────────────────────────────────────

    /**
     * Get the field name used as the document ID in the model.
     * Override if the ID field has a different name.
     */
    protected open fun getIdFieldName(): String {
        // Convention: collectionName minus trailing 's' + "Id"
        // e.g., "vehicles" → "vehicleId", "trips" → "tripId"
        val singular = if (collectionName.endsWith("s")) {
            collectionName.dropLast(1)
        } else {
            collectionName
        }
        return "${singular}Id"
    }
}
