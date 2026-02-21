@file:Suppress("UNCHECKED_CAST")

package com.example.movexa.data.repository

import com.example.movexa.data.model.ResultState
import com.example.movexa.data.remote.FirebaseProvider
import com.example.movexa.data.remote.toModel
import com.example.movexa.data.remote.toModelList
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Base repository for Firebase Realtime Database operations.
 *
 * Optimized for low-latency, high-frequency data like live tracking.
 * Uses simpler data structures and short keys for bandwidth efficiency.
 *
 * Features:
 * - Type-safe read/write with automatic mapping via [fromMap]/[toMap]
 * - Real-time value/child listeners exposed as Kotlin Flows
 * - Atomic multi-path updates
 * - Priority-based queries
 *
 * @param T The data model type this repository manages.
 */
abstract class BaseRealtimeRepository<T> : BaseRepository() {

    /**
     * The root path in the Realtime Database.
     */
    protected abstract val rootPath: String

    /**
     * Map a Realtime Database snapshot map to the model type.
     */
    protected abstract fun fromMap(map: Map<String, Any?>): T

    /**
     * Map a model instance to a Realtime Database map.
     */
    protected abstract fun toMap(model: T): Map<String, Any?>

    // ─── Reference Helpers ─────────────────────────────────────

    /**
     * Get the root database reference.
     */
    protected val rootRef: DatabaseReference
        get() = FirebaseProvider.databaseRef(rootPath)

    /**
     * Get a child reference by path segments.
     */
    protected fun childRef(vararg segments: String): DatabaseReference {
        var ref = rootRef
        segments.forEach { ref = ref.child(it) }
        return ref
    }

    // ═══════════════════════════════════════════════════════════
    // WRITE
    // ═══════════════════════════════════════════════════════════

    /**
     * Set a value at a specific path (overwrites).
     */
    suspend fun setValue(
        path: String,
        model: T
    ): ResultState<Unit> = firebaseSafeCall {
        FirebaseProvider.databaseRef("$rootPath/$path")
            .setValue(toMap(model))
            .await()
    }

    /**
     * Set a value at the root path.
     */
    suspend fun setValueAtRoot(model: T): ResultState<Unit> = firebaseSafeCall {
        rootRef.setValue(toMap(model)).await()
    }

    /**
     * Update specific fields at a path (merge).
     */
    suspend fun updateFields(
        path: String,
        fields: Map<String, Any?>
    ): ResultState<Unit> = firebaseSafeCall {
        FirebaseProvider.databaseRef("$rootPath/$path")
            .updateChildren(fields)
            .await()
    }

    /**
     * Atomic multi-path update across multiple locations.
     */
    suspend fun multiPathUpdate(
        updates: Map<String, Any?>
    ): ResultState<Unit> = firebaseSafeCall {
        FirebaseProvider.realtimeDb.reference
            .updateChildren(updates)
            .await()
    }

    /**
     * Push a new child with auto-generated key.
     */
    suspend fun push(model: T): ResultState<String> = firebaseSafeCall {
        val ref = rootRef.push()
        ref.setValue(toMap(model)).await()
        ref.key ?: throw IllegalStateException("Failed to generate push key")
    }

    /**
     * Push a new child under a specific path.
     */
    suspend fun pushAt(
        path: String,
        model: T
    ): ResultState<String> = firebaseSafeCall {
        val ref = FirebaseProvider.databaseRef("$rootPath/$path").push()
        ref.setValue(toMap(model)).await()
        ref.key ?: throw IllegalStateException("Failed to generate push key")
    }

    // ═══════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════

    /**
     * Read a value at a specific path once.
     */
    suspend fun getValue(path: String): ResultState<T?> = firebaseSafeCall {
        val snapshot = FirebaseProvider.databaseRef("$rootPath/$path")
            .get()
            .await()
        snapshot.toModel(::fromMap)
    }

    /**
     * Read the value at root once.
     */
    suspend fun getValueAtRoot(): ResultState<T?> = firebaseSafeCall {
        val snapshot = rootRef.get().await()
        snapshot.toModel(::fromMap)
    }

    /**
     * Read all children at the root as a list.
     */
    suspend fun getAllChildren(): ResultState<List<T>> = firebaseSafeCall {
        val snapshot = rootRef.get().await()
        snapshot.toModelList(::fromMap)
    }

    /**
     * Read all children at a specific path.
     */
    suspend fun getChildrenAt(path: String): ResultState<List<T>> = firebaseSafeCall {
        val snapshot = FirebaseProvider.databaseRef("$rootPath/$path")
            .get()
            .await()
        snapshot.toModelList(::fromMap)
    }

    // ═══════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════

    /**
     * Remove a value at a specific path.
     */
    suspend fun remove(path: String): ResultState<Unit> = firebaseSafeCall {
        FirebaseProvider.databaseRef("$rootPath/$path")
            .removeValue()
            .await()
    }

    /**
     * Remove the value at root.
     */
    suspend fun removeAtRoot(): ResultState<Unit> = firebaseSafeCall {
        rootRef.removeValue().await()
    }

    // ═══════════════════════════════════════════════════════════
    // REAL-TIME LISTENERS (Kotlin Flows)
    // ═══════════════════════════════════════════════════════════

    /**
     * Observe a single value in real-time.
     */
    fun observeValue(path: String): Flow<ResultState<T?>> = callbackFlow {
        val ref = FirebaseProvider.databaseRef("$rootPath/$path")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val model = snapshot.toModel(::fromMap)
                trySend(ResultState.Success(model))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(
                    ResultState.Error(
                        parseErrorMessage(error.toException()),
                        error.toException()
                    )
                )
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Observe the root value in real-time.
     */
    fun observeRoot(): Flow<ResultState<T?>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val model = snapshot.toModel(::fromMap)
                trySend(ResultState.Success(model))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(
                    ResultState.Error(
                        parseErrorMessage(error.toException()),
                        error.toException()
                    )
                )
            }
        }
        rootRef.addValueEventListener(listener)
        awaitClose { rootRef.removeEventListener(listener) }
    }

    /**
     * Observe all children in real-time.
     */
    fun observeChildren(): Flow<ResultState<List<T>>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val models = snapshot.toModelList(::fromMap)
                trySend(ResultState.Success(models))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(
                    ResultState.Error(
                        parseErrorMessage(error.toException()),
                        error.toException()
                    )
                )
            }
        }
        rootRef.addValueEventListener(listener)
        awaitClose { rootRef.removeEventListener(listener) }
    }

    /**
     * Observe children at a specific path in real-time.
     */
    fun observeChildrenAt(path: String): Flow<ResultState<List<T>>> = callbackFlow {
        val ref = FirebaseProvider.databaseRef("$rootPath/$path")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val models = snapshot.toModelList(::fromMap)
                trySend(ResultState.Success(models))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(
                    ResultState.Error(
                        parseErrorMessage(error.toException()),
                        error.toException()
                    )
                )
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
