package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AudienceRecord
import com.example.model.DashboardStats
import com.example.util.CsvProcessor
import com.example.util.VcfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ProcessingSpeed(val label: String, val delayMs: Long) {
    ANIMATED_STREAM("Real-Time Visual", 50L),
    TURBO_FAST("Instant / Turbo", 0L)
}

class DashboardViewModel : ViewModel() {

    private val _targetPrefix = MutableStateFlow("469")
    val targetPrefix: StateFlow<String> = _targetPrefix.asStateFlow()

    private val _isPrefixDialogOpen = MutableStateFlow(false)
    val isPrefixDialogOpen: StateFlow<Boolean> = _isPrefixDialogOpen.asStateFlow()

    private val _stats = MutableStateFlow(
        DashboardStats(
            targetPrefix = "469",
            statusMessage = "Select a CSV database file to begin extraction"
        )
    )
    val stats: StateFlow<DashboardStats> = _stats.asStateFlow()

    private val _audiences = MutableStateFlow<List<AudienceRecord>>(emptyList())
    val audiences: StateFlow<List<AudienceRecord>> = _audiences.asStateFlow()

    private val _recentAudience = MutableStateFlow<AudienceRecord?>(null)
    val recentAudience: StateFlow<AudienceRecord?> = _recentAudience.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedAudience = MutableStateFlow<AudienceRecord?>(null)
    val selectedAudience: StateFlow<AudienceRecord?> = _selectedAudience.asStateFlow()

    private val _currentFileName = MutableStateFlow("No CSV file loaded")
    val currentFileName: StateFlow<String> = _currentFileName.asStateFlow()

    private val _speedMode = MutableStateFlow(ProcessingSpeed.ANIMATED_STREAM)
    val speedMode: StateFlow<ProcessingSpeed> = _speedMode.asStateFlow()

    private val _vcfExportFile = MutableStateFlow<File?>(null)
    val vcfExportFile: StateFlow<File?> = _vcfExportFile.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var processingJob: Job? = null
    private var lastLoadedLines: List<String> = emptyList()

    // Search and filter on audiences
    val filteredAudiences: StateFlow<List<AudienceRecord>> = combine(_audiences, _searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            val q = query.trim().lowercase()
            list.filter {
                it.fullNameLatin.lowercase().contains(q) ||
                it.fullNamePersian.contains(q) ||
                it.nationalCode.contains(q) ||
                it.mobile.contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun toggleSpeedMode() {
        _speedMode.value = if (_speedMode.value == ProcessingSpeed.ANIMATED_STREAM) {
            ProcessingSpeed.TURBO_FAST
        } else {
            ProcessingSpeed.ANIMATED_STREAM
        }
    }

    fun openPrefixDialog() {
        _isPrefixDialogOpen.value = true
    }

    fun closePrefixDialog() {
        _isPrefixDialogOpen.value = false
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectAudience(audience: AudienceRecord?) {
        _selectedAudience.value = audience
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    fun loadFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filename = getFileNameFromUri(context, uri) ?: "imported_data.csv"
                withContext(Dispatchers.Main) {
                    _currentFileName.value = filename
                    _stats.value = _stats.value.copy(
                        statusMessage = "Loading $filename..."
                    )
                }

                // Copy stream to a local cache file to guarantee persistent access and prevent OOM
                val cacheDir = File(context.cacheDir, "csv_cache")
                cacheDir.mkdirs()
                val tempCsvFile = File(cacheDir, "temp_input.csv")

                val stream = context.contentResolver.openInputStream(uri)
                if (stream == null) {
                    withContext(Dispatchers.Main) {
                        _toastMessage.value = "Unable to open selected file."
                    }
                    return@launch
                }

                stream.use { input ->
                    tempCsvFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val lines = tempCsvFile.inputStream().use { fileInput ->
                    CsvProcessor.readLines(fileInput)
                }

                if (lines.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _toastMessage.value = "Selected file appears to be empty."
                        _stats.value = _stats.value.copy(statusMessage = "Empty CSV file.")
                    }
                    return@launch
                }

                lastLoadedLines = lines
                withContext(Dispatchers.Main) {
                    // Prompt user for 3-digit prefix before starting processing!
                    _isPrefixDialogOpen.value = true
                    _stats.value = _stats.value.copy(
                        totalTargetRows = lines.size,
                        statusMessage = "Loaded $filename (${lines.size} rows). Confirm 3-digit prefix to start."
                    )
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Error reading CSV: ${t.localizedMessage ?: "Unknown error"}"
                    _stats.value = _stats.value.copy(
                        statusMessage = "Failed to open CSV file."
                    )
                }
            }
        }
    }

    fun confirmPrefixAndStart(prefix: String) {
        val cleanPrefix = prefix.trim()
        _targetPrefix.value = cleanPrefix
        _isPrefixDialogOpen.value = false

        if (lastLoadedLines.isEmpty()) {
            _toastMessage.value = "Please select a CSV file first."
            return
        }

        processCsvLines(lastLoadedLines, cleanPrefix, start = true)
    }

    fun restartCurrentProcessing() {
        if (lastLoadedLines.isEmpty()) {
            _toastMessage.value = "Please select a CSV file first."
            return
        }
        // Ask for 3-digit prefix or re-run
        _isPrefixDialogOpen.value = true
    }

    private fun processCsvLines(allLines: List<String>, prefix: String, start: Boolean) {
        processingJob?.cancel()

        if (allLines.isEmpty()) {
            _stats.value = DashboardStats(
                targetPrefix = prefix,
                statusMessage = "CSV file is empty"
            )
            return
        }

        // Extract header
        val headerTokens = CsvProcessor.splitCsvLine(allLines[0])
        val mapping = CsvProcessor.ColumnMapping.fromHeaders(headerTokens)

        val dataLines = if (headerTokens.any { it.contains("CODE", ignoreCase = true) || it.contains("کد", ignoreCase = true) || it.contains("NAME", ignoreCase = true) }) {
            allLines.drop(1)
        } else {
            allLines
        }

        val total = dataLines.size
        _audiences.value = emptyList()
        _recentAudience.value = null
        _vcfExportFile.value = null

        _stats.value = DashboardStats(
            targetPrefix = prefix,
            totalRowsProcessed = 0,
            totalTargetRows = total,
            matchedPrefixAndMobile = 0,
            skippedNonPrefix = 0,
            skippedMissingMobile = 0,
            totalVcfSaved = 0,
            isProcessing = start,
            progressFraction = 0f,
            statusMessage = if (start) "Extracting '$prefix' records & transliterating Persian names..." else "Ready to process ($total rows)"
        )

        if (start) {
            startExtraction(dataLines, mapping, prefix)
        }
    }

    private fun startExtraction(lines: List<String>, mapping: CsvProcessor.ColumnMapping, prefix: String) {
        processingJob?.cancel()
        processingJob = viewModelScope.launch(Dispatchers.Default) {
            val total = lines.size
            val matchedList = mutableListOf<AudienceRecord>()
            var skippedNonPrefixCount = 0
            var skippedNoMobileCount = 0

            val isTurbo = _speedMode.value == ProcessingSpeed.TURBO_FAST
            val delayDuration = _speedMode.value.delayMs

            // Throttle UI updates to prevent Main thread jank/OOM when processing thousands of rows
            var lastUiUpdate = System.currentTimeMillis()

            lines.forEachIndexed { index, line ->
                val tokens = CsvProcessor.splitCsvLine(line)
                val parseResult = CsvProcessor.parseRow(tokens, mapping, index + 1, prefix)
                val record = parseResult.record

                var newlyAdded: AudienceRecord? = null
                if (!parseResult.isPrefixMatched) {
                    skippedNonPrefixCount++
                } else if (!parseResult.hasMobile) {
                    skippedNoMobileCount++
                } else {
                    // Match! Mark as saved to VCF
                    val savedRecord = record.copy(isSavedToVcf = true)
                    matchedList.add(savedRecord)
                    newlyAdded = savedRecord
                }

                val currentProcessed = index + 1
                val progress = if (total > 0) currentProcessed.toFloat() / total.toFloat() else 1f
                val now = System.currentTimeMillis()

                // Update UI on matches, or every 60ms in live mode, or on the final item
                val shouldUpdateUi = newlyAdded != null ||
                        (!isTurbo && now - lastUiUpdate > 60) ||
                        (isTurbo && (currentProcessed % 50 == 0 || currentProcessed == total))

                if (shouldUpdateUi) {
                    lastUiUpdate = now
                    val currentMatchesCopy = ArrayList(matchedList)
                    withContext(Dispatchers.Main) {
                        if (newlyAdded != null) {
                            _recentAudience.value = newlyAdded
                        }
                        _audiences.value = currentMatchesCopy
                        _stats.value = DashboardStats(
                            targetPrefix = prefix,
                            totalRowsProcessed = currentProcessed,
                            totalTargetRows = total,
                            matchedPrefixAndMobile = currentMatchesCopy.size,
                            skippedNonPrefix = skippedNonPrefixCount,
                            skippedMissingMobile = skippedNoMobileCount,
                            totalVcfSaved = currentMatchesCopy.size,
                            isProcessing = currentProcessed < total,
                            progressFraction = progress,
                            statusMessage = if (currentProcessed < total) {
                                "Extracting '$prefix' row $currentProcessed of $total (${(progress * 100).toInt()}%)..."
                            } else {
                                "Completed: ${currentMatchesCopy.size} audiences ('$prefix') extracted & transliterated to VCF."
                            }
                        )
                    }
                }

                if (!isTurbo && delayDuration > 0) {
                    delay(delayDuration)
                }
            }

            val finalMatchesCopy = ArrayList(matchedList)
            withContext(Dispatchers.Main) {
                _audiences.value = finalMatchesCopy
                _stats.value = _stats.value.copy(
                    totalRowsProcessed = total,
                    totalTargetRows = total,
                    matchedPrefixAndMobile = finalMatchesCopy.size,
                    skippedNonPrefix = skippedNonPrefixCount,
                    skippedMissingMobile = skippedNoMobileCount,
                    totalVcfSaved = finalMatchesCopy.size,
                    isProcessing = false,
                    progressFraction = 1f,
                    statusMessage = "Extraction finished! ${finalMatchesCopy.size} audiences ('$prefix') ready in VCF."
                )
                _toastMessage.value = "Successfully generated VCF for ${finalMatchesCopy.size} audiences (Prefix: $prefix)!"
            }
        }
    }

    fun stopProcessing() {
        processingJob?.cancel()
        _stats.value = _stats.value.copy(
            isProcessing = false,
            statusMessage = "Processing paused by user."
        )
    }

    fun updateAudienceLatinName(recordId: String, newLatinName: String) {
        val current = _audiences.value.toMutableList()
        val idx = current.indexOfFirst { it.id == recordId }
        if (idx != -1) {
            val updated = current[idx].copy(fullNameLatin = newLatinName.trim())
            current[idx] = updated
            _audiences.value = current
            if (_selectedAudience.value?.id == recordId) {
                _selectedAudience.value = updated
            }
        }
    }

    /**
     * Generates a physical VCF file in the app's cache directory ready for sharing or saving.
     */
    fun prepareVcfFile(context: Context): File {
        val records = _audiences.value
        val vcfDir = File(context.cacheDir, "vcf")
        vcfDir.mkdirs()
        val file = File(vcfDir, "contacts_${_targetPrefix.value}_${System.currentTimeMillis()}.vcf")
        VcfGenerator.writeToFile(file, records)
        _vcfExportFile.value = file
        return file
    }

    fun getFullVcfString(): String {
        return VcfGenerator.generateVcf(_audiences.value)
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = it.getString(index)
                    }
                }
            }
        } catch (_: Throwable) {
            // Fallback gracefully to lastPathSegment if cursor query fails
        }
        return name ?: uri.lastPathSegment
    }
}
