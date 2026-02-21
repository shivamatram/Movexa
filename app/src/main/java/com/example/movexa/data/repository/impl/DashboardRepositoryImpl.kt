package com.example.movexa.data.repository.impl

import com.example.movexa.data.model.DashboardSummary
import com.example.movexa.data.model.OperationsSummary
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.remote.FirebaseProvider
import com.example.movexa.data.remote.toModel
import com.example.movexa.data.repository.contracts.DashboardRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Implementation of [DashboardRepository] using Firestore.
 *
 * Reads from:
 * - dashboard_summary/{companyId} — admin overview document
 * - operations_summary/{companyId} — manager operations document
 */
class DashboardRepositoryImpl : DashboardRepository {

    private val dashboardCollection =
        FirebaseProvider.firestore.collection(DashboardSummary.COLLECTION_NAME)

    private val operationsCollection =
        FirebaseProvider.firestore.collection(OperationsSummary.COLLECTION_NAME)

    // ── Admin Dashboard ─────────────────────────────────────────

    override suspend fun getDashboardSummary(
        companyId: String
    ): ResultState<DashboardSummary?> {
        return try {
            val snapshot = dashboardCollection.document(companyId).get().await()
            val summary = snapshot.toModel(DashboardSummary::fromMap)
            ResultState.Success(summary)
        } catch (e: Exception) {
            ResultState.Error(
                message = e.message ?: "Failed to load dashboard summary",
                exception = e
            )
        }
    }

    override fun observeDashboardSummary(
        companyId: String
    ): Flow<ResultState<DashboardSummary?>> = callbackFlow {
        val listener = dashboardCollection.document(companyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(
                        ResultState.Error(
                            message = error.message ?: "Dashboard listener error",
                            exception = error
                        )
                    )
                    return@addSnapshotListener
                }
                val summary = snapshot?.toModel(DashboardSummary::fromMap)
                trySend(ResultState.Success(summary))
            }
        awaitClose { listener.remove() }
    }

    // ── Manager Dashboard ───────────────────────────────────────

    override suspend fun getOperationsSummary(
        companyId: String
    ): ResultState<OperationsSummary?> {
        return try {
            val snapshot = operationsCollection.document(companyId).get().await()
            val summary = snapshot.toModel(OperationsSummary::fromMap)
            ResultState.Success(summary)
        } catch (e: Exception) {
            ResultState.Error(
                message = e.message ?: "Failed to load operations summary",
                exception = e
            )
        }
    }

    override fun observeOperationsSummary(
        companyId: String
    ): Flow<ResultState<OperationsSummary?>> = callbackFlow {
        val listener = operationsCollection.document(companyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(
                        ResultState.Error(
                            message = error.message ?: "Operations listener error",
                            exception = error
                        )
                    )
                    return@addSnapshotListener
                }
                val summary = snapshot?.toModel(OperationsSummary::fromMap)
                trySend(ResultState.Success(summary))
            }
        awaitClose { listener.remove() }
    }
}
