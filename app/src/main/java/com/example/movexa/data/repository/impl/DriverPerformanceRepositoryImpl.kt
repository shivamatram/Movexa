package com.example.movexa.data.repository.impl

import com.example.movexa.data.model.DriverSummary
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.repository.BaseFirestoreRepository
import com.example.movexa.data.repository.contracts.DriverPerformanceRepository
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow

/**
 * Firestore implementation of [DriverPerformanceRepository].
 *
 * Manages the `driver_summary` collection for the Driver Performance
 * Scoring System. Each document is keyed by `driverId` and holds
 * cumulative performance metrics (score, grade, violations, stats).
 */
class DriverPerformanceRepositoryImpl :
    BaseFirestoreRepository<DriverSummary>(), DriverPerformanceRepository {

    override val collectionName: String = DriverSummary.COLLECTION_NAME

    override fun fromMap(map: Map<String, Any?>): DriverSummary = DriverSummary.fromMap(map)
    override fun toMap(model: DriverSummary): Map<String, Any?> = model.toMap()
    override fun getDocumentId(model: DriverSummary): String = model.driverId

    // ═══════════════════════════════════════════════════════════
    //  CREATE / UPDATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun saveSummary(summary: DriverSummary): ResultState<Unit> =
        createWithId(summary.driverId, summary)

    override suspend fun updateSummaryFields(
        driverId: String,
        fields: Map<String, Any?>
    ): ResultState<Unit> = updateFields(driverId, fields)

    // ═══════════════════════════════════════════════════════════
    //  READ
    // ═══════════════════════════════════════════════════════════

    override suspend fun getSummaryByDriver(driverId: String): ResultState<DriverSummary?> =
        getById(driverId)

    override suspend fun getSummariesByCompany(
        companyId: String
    ): ResultState<List<DriverSummary>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .orderBy("score", Query.Direction.DESCENDING)
    }

    override suspend fun getTopDrivers(
        companyId: String,
        limit: Int
    ): ResultState<List<DriverSummary>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(limit.toLong())
    }

    override suspend fun getRiskyDrivers(
        companyId: String
    ): ResultState<List<DriverSummary>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .whereLessThan("score", 60)
            .orderBy("score", Query.Direction.ASCENDING)
    }

    override suspend fun getDriversByGrade(
        companyId: String,
        grade: String
    ): ResultState<List<DriverSummary>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .whereEqualTo("grade", grade)
            .orderBy("score", Query.Direction.DESCENDING)
    }

    // ═══════════════════════════════════════════════════════════
    //  REAL-TIME OBSERVATION
    // ═══════════════════════════════════════════════════════════

    override fun observeDriverSummary(
        driverId: String
    ): Flow<ResultState<DriverSummary?>> = observeDocument(driverId)

    override fun observeCompanySummaries(
        companyId: String
    ): Flow<ResultState<List<DriverSummary>>> = observeByField(
        field = "companyId",
        value = companyId
    )

    // ═══════════════════════════════════════════════════════════
    //  DELETE
    // ═══════════════════════════════════════════════════════════

    override suspend fun deleteSummary(driverId: String): ResultState<Unit> =
        delete(driverId)
}
