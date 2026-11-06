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
    val matchedPrefix: String = "469",
    val isPrefixMatched: Boolean = true,
    val hasMobile: Boolean = true,
    val isSavedToVcf: Boolean = false
) {
    val isQualified: Boolean
        get() = isPrefixMatched && hasMobile
}

/**
 * Processing summary statistics for the dashboard.
 */
data class DashboardStats(
    val targetPrefix: String = "469",
    val totalRowsProcessed: Int = 0,
    val totalTargetRows: Int = 0,
    val matchedPrefixAndMobile: Int = 0,
    val skippedNonPrefix: Int = 0,
    val skippedMissingMobile: Int = 0,
    val totalVcfSaved: Int = 0,
    val isProcessing: Boolean = false,
    val progressFraction: Float = 0f,
    val statusMessage: String = "Select a CSV database file to begin extraction"
)
