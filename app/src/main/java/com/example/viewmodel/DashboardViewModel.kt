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
                it.fullNamePersian.contains(q) ||
                it.nationalCode.contains(q) ||
                it.mobile.contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Initializes the ViewModel and restores any previous session state if the app was accidentally closed.
     */
    fun restorePreviousSessionOnLaunch(context: Context) {
        val prefs = context.getSharedPreferences("parsvcf_checkpoint", Context.MODE_PRIVATE)
        val lastFile = prefs.getString("last_file_name", null)
        val lastPrefix = prefs.getString("last_prefix", "469") ?: "469"
        val lastProcessedRow = prefs.getLong("last_processed_row", 0L)
        val savedCount = prefs.getLong("last_saved_count", 0L)
        val isFinished = prefs.getBoolean("is_finished", false)

        _targetPrefix.value = lastPrefix

        // Locate persistent VCF in filesDir (guaranteed to survive app closures)
        val vcfDir = File(context.filesDir, "vcf_store")
        vcfDir.mkdirs()
        val persistentVcf = File(vcfDir, "contacts_${lastPrefix}_persistent.vcf")

        if (persistentVcf.exists() && persistentVcf.length() > 0) {
            generatedVcfFile = persistentVcf
            _vcfExportFile.value = persistentVcf
        }

        if (!lastFile.isNullOrBlank() && lastProcessedRow > 0L) {
            _currentFileName.value = lastFile

            // Also check if cached input CSV is still available
            val cacheCsv = File(context.filesDir, "stream_input.csv")
            if (cacheCsv.exists() && cacheCsv.length() > 0) {
                sourceCsvFile = cacheCsv
            }

            _stats.value = DashboardStats(
                targetPrefix = lastPrefix,
                totalRowsProcessed = lastProcessedRow,
                totalTargetRows = lastProcessedRow,
                matchedPrefixAndMobile = savedCount,
                skippedNonPrefix = prefs.getLong("last_skipped_non_prefix", 0L),
                skippedMissingMobile = prefs.getLong("last_skipped_no_mobile", 0L),
                totalVcfSaved = savedCount,
                isProcessing = false,
                progressFraction = if (isFinished) 1f else 0.5f,
                statusMessage = if (isFinished) {
                    "Previous scan complete: ${formatCount(savedCount)} contacts saved in VCF."
                } else {
                    "Previous session saved: row ${formatCount(lastProcessedRow)} (${formatCount(savedCount)} contacts). Select file or resume."
                }
            )

            // Populate sample preview from existing VCF file if preview list is empty
            if (_audiences.value.isEmpty() && persistentVcf.exists()) {
                viewModelScope.launch(Dispatchers.IO) {
                    val previewItems = readTailFromVcf(persistentVcf, maxRecords = 50)
                    withContext(Dispatchers.Main) {
                        _audiences.value = previewItems
                    }
                }
            }
        }
    }

    private fun readTailFromVcf(file: File, maxRecords: Int): List<AudienceRecord> {
        val list = mutableListOf<AudienceRecord>()
        try {
            var currentFn = ""
            var currentTel = ""
            var currentNat = ""
            var count = 0L

            file.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("FN")) {
                    currentFn = trimmed.substringAfter(":", "").replace("\\,", ",")
                } else if (trimmed.startsWith("TEL")) {
                    currentTel = trimmed.substringAfter(":", "")
                } else if (trimmed.startsWith("X-NATIONAL-CODE")) {
                    currentNat = trimmed.substringAfter(":", "")
                } else if (trimmed == "END:VCARD") {
                    count++
                    val rec = AudienceRecord(
                        id = "restored_$count",
                        rowNumber = count,
                        nationalCode = currentNat,
                        cardNo = "",
                        fullNamePersian = currentFn,
                        fullNameLatin = currentFn,
                        birthDate = "",
                        mobile = currentTel,
                        matchedPrefix = _targetPrefix.value,
                        isPrefixMatched = true,
                        hasMobile = true,
                        isSavedToVcf = true
                    )
                    if (list.size >= maxRecords) {
                        list.removeAt(0)
                    }
                    list.add(rec)
                    currentFn = ""
                    currentTel = ""
                    currentNat = ""
                }
            }
        } catch (_: Throwable) {}
        return list
    }

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

                // Copy stream to persistent app storage in filesDir (never deleted by OS cache clearing)
                val tempCsvFile = File(context.filesDir, "stream_input.csv")

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

                // Check if this same file was previously scanned and has a checkpoint
                val prefs = context.getSharedPreferences("parsvcf_checkpoint", Context.MODE_PRIVATE)
                val lastScannedFile = prefs.getString("last_file_name", null)
                val lastProcessedRow = prefs.getLong("last_processed_row", 0L)
                val savedCount = prefs.getLong("last_saved_count", 0L)

                val resumeMsg = if (lastScannedFile == filename && lastProcessedRow > 0L) {
                    "Resuming $filename from row ${formatCount(lastProcessedRow)} ($savedCount contacts already saved in VCF)."
                } else {
                    "Loaded $filename ($fileSizeMb). Confirm prefix to start extraction."
                }

                withContext(Dispatchers.Main) {
                    _isPrefixDialogOpen.value = true
                    _stats.value = _stats.value.copy(
                        statusMessage = resumeMsg
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

        val filename = _currentFileName.value
        val prefs = context.getSharedPreferences("parsvcf_checkpoint", Context.MODE_PRIVATE)
        val isSameFile = prefs.getString("last_file_name", null) == filename &&
                         prefs.getString("last_prefix", null) == prefix

        val resumeRow = if (isSameFile) prefs.getLong("last_processed_row", 0L) else 0L
        var matchedCount = if (isSameFile) prefs.getLong("last_saved_count", 0L) else 0L
        var skippedNonPrefixCount = if (isSameFile) prefs.getLong("last_skipped_non_prefix", 0L) else 0L
        var skippedNoMobileCount = if (isSameFile) prefs.getLong("last_skipped_no_mobile", 0L) else 0L

        // Prepare physical persistent VCF output file in filesDir (survives app close and cache clear)
        val vcfDir = File(context.filesDir, "vcf_store")
        vcfDir.mkdirs()
        val outVcf = File(vcfDir, "contacts_${prefix}_persistent.vcf")
        generatedVcfFile = outVcf
        _vcfExportFile.value = outVcf

        // If starting a brand new file or different prefix, reset checkpoint
        if (!isSameFile || !outVcf.exists() || resumeRow == 0L) {
            outVcf.delete()
            matchedCount = 0L
            skippedNonPrefixCount = 0L
            skippedNoMobileCount = 0L
            _audiences.value = emptyList()
            _recentAudience.value = null
        }

        _stats.value = DashboardStats(
            targetPrefix = prefix,
            totalRowsProcessed = resumeRow,
            totalTargetRows = resumeRow,
            matchedPrefixAndMobile = matchedCount,
            skippedNonPrefix = skippedNonPrefixCount,
            skippedMissingMobile = skippedNoMobileCount,
            totalVcfSaved = matchedCount,
            isProcessing = true,
            progressFraction = 0f,
            statusMessage = if (resumeRow > 0) "Resuming from row ${formatCount(resumeRow)}..." else "Streaming CSV & saving VCF in real-time..."
        )

        processingJob = viewModelScope.launch(Dispatchers.IO) {
            val previewList = _audiences.value.toMutableList()
            var processedCount = resumeRow

            val isTurbo = _speedMode.value == ProcessingSpeed.TURBO_FAST
            val delayDuration = _speedMode.value.delayMs

            var lastUiUpdate = System.currentTimeMillis()
            var lastCheckpointSave = System.currentTimeMillis()
            var recentSaved: AudienceRecord? = null

            val totalBytes = csvFile.length().toFloat().coerceAtLeast(1f)
            var bytesReadSoFar = 0L

            try {
                // Open VCF in append mode so existing contacts are not lost on resume!
                java.io.FileOutputStream(outVcf, true).use { fos ->
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

                            // If first line wasn't header and we are starting from row 0, process it
                            if (!isHeader && resumeRow == 0L) {
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

                            // If resuming from row N, skip the already processed rows efficiently
                            var currentLineNumber = if (isHeader) 0L else 1L
                            if (resumeRow > currentLineNumber) {
                                while (currentLineNumber < resumeRow) {
                                    val skippedLine = reader.readLine() ?: break
                                    currentLineNumber++
                                    bytesReadSoFar += skippedLine.toByteArray(StandardCharsets.UTF_8).size + 1
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

                                        // Keep sliding window of latest 300 records for UI
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
                                            statusMessage = "Scanned ${formatCount(processedCount)} rows... (${formatCount(matchedCount)} '$prefix' in VCF)"
                                        )
                                    }
                                }

                                // Periodic checkpoint save every 3 seconds
                                if (now - lastCheckpointSave > 3000) {
                                    lastCheckpointSave = now
                                    vcfWriter.flush()
                                    prefs.edit()
                                        .putString("last_file_name", filename)
                                        .putString("last_prefix", prefix)
                                        .putLong("last_processed_row", processedCount)
                                        .putLong("last_saved_count", matchedCount)
                                        .putLong("last_skipped_non_prefix", skippedNonPrefixCount)
                                        .putLong("last_skipped_no_mobile", skippedNoMobileCount)
                                        .apply()
                                }

                                if (!isTurbo && delayDuration > 0 && matchedCount < 100) {
                                    delay(delayDuration)
                                }

                                line = reader.readLine()
                            }
                        }
                    }
                }

                // Completed scan: persist completion checkpoint
                prefs.edit()
                    .putString("last_file_name", filename)
                    .putString("last_prefix", prefix)
                    .putLong("last_processed_row", processedCount)
                    .putLong("last_saved_count", matchedCount)
                    .putLong("last_skipped_non_prefix", skippedNonPrefixCount)
                    .putLong("last_skipped_no_mobile", skippedNoMobileCount)
                    .apply()

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
                        statusMessage = "Complete! Extracted & saved ${formatCount(matchedCount)} Persian audiences ('$prefix') to VCF."
                    )
                    _toastMessage.value = "Finished: ${formatCount(matchedCount)} Persian contacts saved to VCF!"
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    _stats.value = _stats.value.copy(
                        isProcessing = false,
                        statusMessage = "Streaming paused: ${t.localizedMessage ?: "Error"}"
                    )
                    _toastMessage.value = "Paused at row ${formatCount(processedCount)}"
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
        val vcfDir = File(context.filesDir, "vcf_store")
        vcfDir.mkdirs()
        val persistent = File(vcfDir, "contacts_${_targetPrefix.value}_persistent.vcf")
        if (persistent.exists() && persistent.length() > 0) {
            _vcfExportFile.value = persistent
            return persistent
        }
        val records = _audiences.value
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
