package com.example.movexa.data.model.enums

/**
 * Verification status for drivers and documents.
 */
enum class VerificationStatus(val displayName: String) {
    PENDING("Pending Review"),
    UNDER_REVIEW("Under Review"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    EXPIRED("Expired");

    companion object {
        fun fromString(value: String?): VerificationStatus {
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true)
            } ?: PENDING
        }
    }

    fun isApproved(): Boolean = this == APPROVED
    fun needsAction(): Boolean = this == PENDING || this == UNDER_REVIEW
}
