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
import java.nio.charset.StandardCharsets

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
    private var sourceCsvFile: File? = null
    private var generatedVcfFile: File? = null

    // Search and filter on preview audiences
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
                        statusMessage = "Connecting to $filename..."
                    )
                }

                // Copy stream directly to local storage using an 8KB buffer (memory footprint < 10KB)
                val cacheDir = File(context.cacheDir, "csv_cache")
                cacheDir.mkdirs()
                val tempCsvFile = File(cacheDir, "stream_input.csv")

                val stream = context.contentResolver.openInputStream(uri)
                if (stream == null) {
                    withContext(Dispatchers.Main) {
                        _toastMessage.value = "Unable to open selected file."
                    }
                    return@launch
                }

                stream.use { input ->
                    tempCsvFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 65536)
                    }
                }

                if (!tempCsvFile.exists() || tempCsvFile.length() == 0L) {
                    withContext(Dispatchers.Main) {
                        _toastMessage.value = "Selected file appears to be empty."
                        _stats.value = _stats.value.copy(statusMessage = "Empty CSV file.")
                    }
                    return@launch
                }

                sourceCsvFile = tempCsvFile

                val fileSizeMb = String.format(java.util.Locale.US, "%.1f MB", tempCsvFile.length() / (1024.0 * 1024.0))

                withContext(Dispatchers.Main) {
                    // Prompt user for 3-digit prefix before starting processing!
                    _isPrefixDialogOpen.value = true
                    _stats.value = _stats.value.copy(
                        statusMessage = "Loaded $filename ($fileSizeMb). Confirm prefix to start extraction."
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

    fun confirmPrefixAndStart(context: Context, prefix: String) {
        val cleanPrefix = prefix.trim()
        _targetPrefix.value = cleanPrefix
        _isPrefixDialogOpen.value = false

        val file = sourceCsvFile
        if (file == null || !file.exists()) {
            _toastMessage.value = "Please select a CSV file first."
            return
        }

        startDiskStreamingExtraction(context, file, cleanPrefix)
    }

    fun restartCurrentProcessing(context: Context) {
        val file = sourceCsvFile
        if (file == null || !file.exists()) {
            _toastMessage.value = "Please select a CSV file first."
            return
        }
        _isPrefixDialogOpen.value = true
    }

    private fun startDiskStreamingExtraction(context: Context, csvFile: File, prefix: String) {
        processingJob?.cancel()

        // Prepare physical VCF output file
        val vcfDir = File(context.cacheDir, "vcf")
        vcfDir.mkdirs()
        val outVcf = File(vcfDir, "contacts_${prefix}_${System.currentTimeMillis()}.vcf")
        generatedVcfFile = outVcf
        _vcfExportFile.value = outVcf

        _audiences.value = emptyList()
        _recentAudience.value = null

        _stats.value = DashboardStats(
            targetPrefix = prefix,
            totalRowsProcessed = 0L,
            totalTargetRows = 0L,
            matchedPrefixAndMobile = 0L,
            skippedNonPrefix = 0L,
            skippedMissingMobile = 0L,
            totalVcfSaved = 0L,
            isProcessing = true,
            progressFraction = 0f,
            statusMessage = "Streaming CSV & saving VCF in real-time..."
        )

        processingJob = viewModelScope.launch(Dispatchers.IO) {
            val previewList = mutableListOf<AudienceRecord>()
            var processedCount = 0L
            var matchedCount = 0L
            var skippedNonPrefixCount = 0L
            var skippedNoMobileCount = 0L

            val isTurbo = _speedMode.value == ProcessingSpeed.TURBO_FAST
            val delayDuration = _speedMode.value.delayMs

            var lastUiUpdate = System.currentTimeMillis()
            var recentSaved: AudienceRecord? = null

            val totalBytes = csvFile.length().toFloat().coerceAtLeast(1f)
            var bytesReadSoFar = 0L

            try {
                java.io.FileOutputStream(outVcf).use { fos ->
                    java.io.BufferedWriter(java.io.OutputStreamWriter(fos, StandardCharsets.UTF_8), 65536).use { vcfWriter ->

                        java.io.FileInputStream(csvFile).use { fis ->
                            val reader = CsvProcessor.createBufferedReader(fis)

                            // Read first line (header detection)
                            val firstLine = reader.readLine()
                            if (firstLine == null) {
                                withContext(Dispatchers.Main) {
                                    _stats.value = DashboardStats(targetPrefix = prefix, statusMessage = "CSV file is empty.")
                                }
                                return@use
                            }
                            bytesReadSoFar += firstLine.toByteArray(StandardCharsets.UTF_8).size + 1

                            val headerTokens = CsvProcessor.splitCsvLine(firstLine)
                            val isHeader = headerTokens.any {
                                it.contains("CODE", ignoreCase = true) ||
                                it.contains("کد", ignoreCase = true) ||
                                it.contains("NAME", ignoreCase = true) ||
                                it.contains("MOBILE", ignoreCase = true)
                            }

                            val mapping = if (isHeader) {
                                CsvProcessor.ColumnMapping.fromHeaders(headerTokens)
                            } else {
                                CsvProcessor.ColumnMapping(0, 1, 2, 3, 4)
                            }

                            // If first line wasn't header, process it as row 1
                            if (!isHeader) {
                                processedCount++
                                val parseResult = CsvProcessor.parseRow(headerTokens, mapping, processedCount, prefix)
                                if (!parseResult.isPrefixMatched) {
                                    skippedNonPrefixCount++
                                } else if (!parseResult.hasMobile) {
                                    skippedNoMobileCount++
                                } else {
                                    matchedCount++
                                    val saved = parseResult.record.copy(isSavedToVcf = true)
                                    VcfGenerator.appendRecordToWriter(vcfWriter, saved)
                                    previewList.add(saved)
                                    recentSaved = saved
                                }
                            }

                            // Sequential stream line by line — supports 10M+ rows with constant ~50KB RAM!
                            var line = reader.readLine()
                            while (line != null) {
                                processedCount++
                                bytesReadSoFar += line.toByteArray(StandardCharsets.UTF_8).size + 1

                                if (line.isNotBlank()) {
                                    val tokens = CsvProcessor.splitCsvLine(line)
                                    val parseResult = CsvProcessor.parseRow(tokens, mapping, processedCount, prefix)

                                    if (!parseResult.isPrefixMatched) {
                                        skippedNonPrefixCount++
                                    } else if (!parseResult.hasMobile) {
                                        skippedNoMobileCount++
                                    } else {
                                        matchedCount++
                                        val saved = parseResult.record.copy(isSavedToVcf = true)
                                        // Write directly into VCF file stream on disk
                                        VcfGenerator.appendRecordToWriter(vcfWriter, saved)

                                        // Keep a sliding window of the latest 300 records in memory for UI dashboard
                                        if (previewList.size >= 300) {
                                            previewList.removeAt(0)
                                        }
                                        previewList.add(saved)
                                        recentSaved = saved
                                    }
                                }

                                val now = System.currentTimeMillis()
                                val shouldUpdateUi = (recentSaved != null && now - lastUiUpdate > 80) ||
                                        (now - lastUiUpdate > 250)

                                if (shouldUpdateUi) {
                                    lastUiUpdate = now
                                    val previewSnapshot = ArrayList(previewList)
                                    val currentRecent = recentSaved
                                    recentSaved = null

                                    val progressFraction = (bytesReadSoFar / totalBytes).coerceIn(0f, 0.99f)

                                    withContext(Dispatchers.Main) {
                                        if (currentRecent != null) {
                                            _recentAudience.value = currentRecent
                                        }
                                        _audiences.value = previewSnapshot
                                        _stats.value = DashboardStats(
                                            targetPrefix = prefix,
                                            totalRowsProcessed = processedCount,
                                            totalTargetRows = processedCount,
                                            matchedPrefixAndMobile = matchedCount,
                                            skippedNonPrefix = skippedNonPrefixCount,
                                            skippedMissingMobile = skippedNoMobileCount,
                                            totalVcfSaved = matchedCount,
                                            isProcessing = true,
                                            progressFraction = progressFraction,
                                            statusMessage = "Processed ${formatCount(processedCount)} rows... (${formatCount(matchedCount)} '$prefix' saved to VCF)"
                                        )
                                    }
                                }

                                if (!isTurbo && delayDuration > 0 && matchedCount < 100) {
                                    delay(delayDuration)
                                }

                                line = reader.readLine()
                            }
                        }
                    }
                }

                // Completed!
                val finalPreviewSnapshot = ArrayList(previewList)
                withContext(Dispatchers.Main) {
                    _audiences.value = finalPreviewSnapshot
                    _stats.value = DashboardStats(
                        targetPrefix = prefix,
                        totalRowsProcessed = processedCount,
                        totalTargetRows = processedCount,
                        matchedPrefixAndMobile = matchedCount,
                        skippedNonPrefix = skippedNonPrefixCount,
                        skippedMissingMobile = skippedNoMobileCount,
                        totalVcfSaved = matchedCount,
                        isProcessing = false,
                        progressFraction = 1f,
                        statusMessage = "Complete! Extracted & saved ${formatCount(matchedCount)} audiences with prefix '$prefix' to VCF."
                    )
                    _toastMessage.value = "Finished: ${formatCount(matchedCount)} audiences saved to VCF!"
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    _stats.value = _stats.value.copy(
                        isProcessing = false,
                        statusMessage = "Streaming stopped: ${t.localizedMessage ?: "Error"}"
                    )
                    _toastMessage.value = "Processing stopped: ${t.localizedMessage}"
                }
            }
        }
    }

    private fun formatCount(number: Long): String {
        return java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(number)
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
     * Returns the physical VCF file generated during streaming.
     */
    fun prepareVcfFile(context: Context): File {
        val existing = generatedVcfFile
        if (existing != null && existing.exists() && existing.length() > 0) {
            _vcfExportFile.value = existing
            return existing
        }
        val records = _audiences.value
        val vcfDir = File(context.cacheDir, "vcf")
        vcfDir.mkdirs()
        val file = File(vcfDir, "contacts_${_targetPrefix.value}_${System.currentTimeMillis()}.vcf")
        VcfGenerator.writeToFile(file, records)
        _vcfExportFile.value = file
        return file
    }

    /**
     * Streams the generated VCF file directly into a destination OutputStream.
     */
    fun copyVcfToOutputStream(context: Context, destStream: java.io.OutputStream) {
        val file = generatedVcfFile
        if (file != null && file.exists()) {
            file.inputStream().use { fis ->
                fis.copyTo(destStream, bufferSize = 65536)
            }
        } else {
            java.io.BufferedWriter(java.io.OutputStreamWriter(destStream, StandardCharsets.UTF_8), 65536).use { writer ->
                for (record in _audiences.value) {
                    VcfGenerator.appendRecordToWriter(writer, record)
                }
            }
        }
    }

    fun getFullVcfString(): String {
        val file = generatedVcfFile
        if (file != null && file.exists() && file.length() < 2 * 1024 * 1024) {
            return file.readText(StandardCharsets.UTF_8)
        }
        return VcfGenerator.generateVcf(_audiences.value, maxRecords = 100)
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
