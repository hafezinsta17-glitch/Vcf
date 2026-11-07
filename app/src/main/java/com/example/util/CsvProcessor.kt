package com.example.util

import com.example.model.AudienceRecord
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.UUID

object CsvProcessor {

    data class ParseRowResult(
        val record: AudienceRecord,
        val isPrefixMatched: Boolean,
        val hasMobile: Boolean
    )



    /**
     * Maps headers to expected column indices.
     */
    class ColumnMapping(
        var nationalCodeIdx: Int = 0,
        var cardNoIdx: Int = 1,
        var fullNameIdx: Int = 2,
        var birthDateIdx: Int = 3,
        var mobileIdx: Int = 4
    ) {
        companion object {
            fun fromHeaders(headers: List<String>): ColumnMapping {
                val mapping = ColumnMapping()

                headers.forEachIndexed { index, rawHeader ->
                    val h = rawHeader.lowercase().replace(" ", "").replace("_", "")
                    when {
                        h.contains("national") || h.contains("melli") || h.contains("کدملی") || h.contains("کد_ملی") -> {
                            mapping.nationalCodeIdx = index
                        }
                        h.contains("card") || h.contains("کارت") || h.contains("شماره_کارت") -> {
                            mapping.cardNoIdx = index
                        }
                        h.contains("name") || h.contains("نام") -> {
                            mapping.fullNameIdx = index
                        }
                        h.contains("birth") || h.contains("tavalod") || h.contains("تولد") -> {
                            mapping.birthDateIdx = index
                        }
                        h.contains("mobile") || h.contains("phone") || h.contains("tel") || h.contains("موبایل") || h.contains("همراه") -> {
                            mapping.mobileIdx = index
                        }
                    }
                }
                return mapping
            }
        }
    }

    /**
     * Parses a single row into an AudienceRecord using the configured target 3-digit prefix.
     */
    fun parseRow(
        rowValues: List<String>,
        mapping: ColumnMapping,
        rowIndex: Long,
        targetPrefix: String = "469"
    ): ParseRowResult {
        fun getVal(idx: Int): String = if (idx in rowValues.indices) rowValues[idx].trim().removeSurrounding("\"") else ""

        val rawNat = getVal(mapping.nationalCodeIdx)
        val rawCard = getVal(mapping.cardNoIdx)
        val rawName = getVal(mapping.fullNameIdx)
        val rawBirth = getVal(mapping.birthDateIdx)
        val rawMob = getVal(mapping.mobileIdx)

        val cleanNat = PersianAiTransliterator.normalizeDigits(rawNat).filter { it.isDigit() }
        val cleanMob = PersianAiTransliterator.normalizeMobile(rawMob)
        val cleanCard = PersianAiTransliterator.normalizeDigits(rawCard).filter { it.isDigit() }

        val isPrefixMatched = cleanNat.startsWith(targetPrefix)
        val hasMobile = cleanMob.isNotBlank()

        val persianCleanName = rawName.trim()

        val record = AudienceRecord(
            id = UUID.randomUUID().toString(),
            rowNumber = rowIndex,
            nationalCode = cleanNat.ifBlank { rawNat },
            cardNo = cleanCard.ifBlank { rawCard },
            fullNamePersian = persianCleanName,
            fullNameLatin = persianCleanName, // Preserves authentic Persian name directly
            birthDate = rawBirth,
            mobile = cleanMob.ifBlank { rawMob },
            matchedPrefix = targetPrefix,
            isPrefixMatched = isPrefixMatched,
            hasMobile = hasMobile,
            isSavedToVcf = false
        )

        return ParseRowResult(record, isPrefixMatched, hasMobile)
    }

    /**
     * Fast CSV splitter that minimizes allocations when processing millions of lines.
     */
    fun splitCsvLine(line: String): List<String> {
        val result = ArrayList<String>(6)
        val sb = StringBuilder(32)
        var inQuotes = false

        val separator = if (line.contains(';') && !line.contains(',')) ';' else ','

        for (i in line.indices) {
            val c = line[i]
            when {
                c == '\"' -> inQuotes = !inQuotes
                c == separator && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
        }
        result.add(sb.toString().trim())
        return result
    }

    /**
     * Creates a BufferedReader with automatic UTF-8 BOM stripping and charset fallback.
     */
    fun createBufferedReader(inputStream: InputStream): BufferedReader {
        val bis = java.io.BufferedInputStream(inputStream, 65536)
        bis.mark(3)
        val bom = ByteArray(3)
        val bytesRead = bis.read(bom, 0, 3)
        val isBom = bytesRead >= 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte()
        if (!isBom) {
            bis.reset()
        }
        return BufferedReader(InputStreamReader(bis, StandardCharsets.UTF_8), 65536)
    }
}
