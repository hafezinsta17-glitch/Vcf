package com.example.model

/**
 * Represents an audience entry parsed from the CSV file.
 */
data class AudienceRecord(
    val id: String,
    val rowNumber: Int,
    val nationalCode: String,
    val cardNo: String,
    val fullNamePersian: String,
    val fullNameLatin: String,
    val birthDate: String,
    val mobile: String,
    val is469Matched: Boolean,
    val hasMobile: Boolean,
    val isSavedToVcf: Boolean = false
) {
    val isQualified: Boolean
        get() = is469Matched && hasMobile
}

/**
 * Processing summary statistics for the dashboard.
 */
data class DashboardStats(
    val totalRowsProcessed: Int = 0,
    val totalTargetRows: Int = 0,
    val matched469AndMobile: Int = 0,
    val skippedNon469: Int = 0,
    val skippedMissingMobile: Int = 0,
    val totalVcfSaved: Int = 0,
    val isProcessing: Boolean = false,
    val progressFraction: Float = 0f,
    val statusMessage: String = "Ready to load CSV"
)
