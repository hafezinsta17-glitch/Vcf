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
import java.io.InputStream

enum class ProcessingSpeed(val label: String, val delayMs: Long) {
    ANIMATED_STREAM("Real-Time Visual", 50L),
    TURBO_FAST("Instant / Turbo", 0L)
}

class DashboardViewModel : ViewModel() {

    private val _stats = MutableStateFlow(DashboardStats())
    val stats: StateFlow<DashboardStats> = _stats.asStateFlow()

    private val _audiences = MutableStateFlow<List<AudienceRecord>>(emptyList())
    val audiences: StateFlow<List<AudienceRecord>> = _audiences.asStateFlow()

    private val _recentAudience = MutableStateFlow<AudienceRecord?>(null)
    val recentAudience: StateFlow<AudienceRecord?> = _recentAudience.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedAudience = MutableStateFlow<AudienceRecord?>(null)
    val selectedAudience: StateFlow<AudienceRecord?> = _selectedAudience.asStateFlow()

    private val _currentFileName = MutableStateFlow("sample_iranian_database.csv")
    val currentFileName: StateFlow<String> = _currentFileName.asStateFlow()

    private val _speedMode = MutableStateFlow(ProcessingSpeed.ANIMATED_STREAM)
    val speedMode: StateFlow<ProcessingSpeed> = _speedMode.asStateFlow()

    private val _vcfExportFile = MutableStateFlow<File?>(null)
    val vcfExportFile: StateFlow<File?> = _vcfExportFile.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var processingJob: Job? = null

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

    init {
        // Automatically start with sample dataset demonstration so user immediately sees functionality!
        loadSampleData(autoStart = true)
    }

    fun toggleSpeedMode() {
        _speedMode.value = if (_speedMode.value == ProcessingSpeed.ANIMATED_STREAM) {
            ProcessingSpeed.TURBO_FAST
        } else {
            ProcessingSpeed.ANIMATED_STREAM
        }
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

    fun loadSampleData(autoStart: Boolean = true) {
        val sample = CsvProcessor.getSampleCsv()
        _currentFileName.value = "iran_sample_469_dataset.csv"
        processCsvLines(sample.lines().filter { it.isNotBlank() }, autoStart)
    }

    fun loadFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filename = getFileNameFromUri(context, uri) ?: "imported_data.csv"
                _currentFileName.value = filename

                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val lines = CsvProcessor.readLines(stream)
                    withContext(Dispatchers.Main) {
                        processCsvLines(lines, start = true)
                    }
                } ?: run {
                    _toastMessage.value = "Could not open selected CSV file."
                }
            } catch (e: Exception) {
                _toastMessage.value = "Failed to read CSV: ${e.localizedMessage}"
            }
        }
    }

    fun restartCurrentProcessing() {
        // Re-run with currently loaded lines
        loadSampleData(autoStart = true)
    }

    private fun processCsvLines(allLines: List<String>, start: Boolean) {
        processingJob?.cancel()

        if (allLines.isEmpty()) {
            _stats.value = DashboardStats(statusMessage = "CSV file is empty")
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
            totalRowsProcessed = 0,
            totalTargetRows = total,
            matched469AndMobile = 0,
            skippedNon469 = 0,
            skippedMissingMobile = 0,
            totalVcfSaved = 0,
            isProcessing = start,
            progressFraction = 0f,
            statusMessage = if (start) "Extracting 469 records & transliterating Persian names..." else "Ready to process ($total rows)"
        )

        if (start) {
            startExtraction(dataLines, mapping)
        }
    }

    private fun startExtraction(lines: List<String>, mapping: CsvProcessor.ColumnMapping) {
        processingJob?.cancel()
        processingJob = viewModelScope.launch(Dispatchers.Default) {
            val total = lines.size
            val matchedList = mutableListOf<AudienceRecord>()
            var skippedNon469Count = 0
            var skippedNoMobileCount = 0

            val delayDuration = _speedMode.value.delayMs

            lines.forEachIndexed { index, line ->
                val tokens = CsvProcessor.splitCsvLine(line)
                val parseResult = CsvProcessor.parseRow(tokens, mapping, index + 1)
                val record = parseResult.record

                if (!parseResult.is469) {
                    skippedNon469Count++
                } else if (!parseResult.hasMobile) {
                    skippedNoMobileCount++
                } else {
                    // Match! Mark as saved to VCF
                    val savedRecord = record.copy(isSavedToVcf = true)
                    matchedList.add(savedRecord)

                    withContext(Dispatchers.Main) {
                        _recentAudience.value = savedRecord
                        _audiences.value = ArrayList(matchedList)
                    }
                }

                val currentProcessed = index + 1
                val progress = if (total > 0) currentProcessed.toFloat() / total.toFloat() else 1f

                withContext(Dispatchers.Main) {
                    _stats.value = DashboardStats(
                        totalRowsProcessed = currentProcessed,
                        totalTargetRows = total,
                        matched469AndMobile = matchedList.size,
                        skippedNon469 = skippedNon469Count,
                        skippedMissingMobile = skippedNoMobileCount,
                        totalVcfSaved = matchedList.size,
                        isProcessing = currentProcessed < total,
                        progressFraction = progress,
                        statusMessage = if (currentProcessed < total) {
                            "Extracting row $currentProcessed of $total (${(progress * 100).toInt()}%)..."
                        } else {
                            "Completed: ${matchedList.size} audiences extracted & transliterated to VCF."
                        }
                    )
                }

                if (delayDuration > 0) {
                    delay(delayDuration)
                }
            }

            withContext(Dispatchers.Main) {
                _stats.value = _stats.value.copy(
                    isProcessing = false,
                    progressFraction = 1f,
                    statusMessage = "Extraction finished! ${matchedList.size} audiences ready in VCF."
                )
                _toastMessage.value = "Successfully generated VCF for ${matchedList.size} audiences!"
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
        val file = File(vcfDir, "contacts_469_${System.currentTimeMillis()}.vcf")
        VcfGenerator.writeToFile(file, records)
        _vcfExportFile.value = file
        return file
    }

    fun getFullVcfString(): String {
        return VcfGenerator.generateVcf(_audiences.value)
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }
}
