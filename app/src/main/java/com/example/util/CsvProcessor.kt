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
     * Splits a CSV line taking quotes into account. Supports comma, semicolon, or tab.
     */
    fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        // Determine separator: if line has semicolons and no commas, prefer semicolon
        val separator = if (line.contains(";") && !line.contains(",")) ';' else ','

        for (i in line.indices) {
            val c = line[i]
            when {
                c == '\"' -> {
                    inQuotes = !inQuotes
                }
                c == separator && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> {
                    sb.append(c)
                }
            }
        }
        result.add(sb.toString().trim())
        return result
    }

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
        rowIndex: Int,
        targetPrefix: String = "469"
    ): ParseRowResult {
        fun getVal(idx: Int): String = if (idx in rowValues.indices) rowValues[idx].trim().removeSurrounding("\"") else ""

        val rawNat = getVal(mapping.nationalCodeIdx)
        val rawCard = getVal(mapping.cardNoIdx)
        val rawName = getVal(mapping.fullNameIdx)
        val rawBirth = getVal(mapping.birthDateIdx)
        val rawMob = getVal(mapping.mobileIdx)

        val cleanNat = PersianTransliterator.normalizeDigits(rawNat).filter { it.isDigit() }
        val cleanMob = PersianTransliterator.normalizeMobile(rawMob)
        val cleanCard = PersianTransliterator.normalizeDigits(rawCard).filter { it.isDigit() }

        val isPrefixMatched = cleanNat.startsWith(targetPrefix)
        val hasMobile = cleanMob.isNotBlank()

        val latinName = if (rawName.isNotBlank()) {
            PersianTransliterator.transliterate(rawName)
        } else {
            "Contact $cleanNat"
        }

        val record = AudienceRecord(
            id = UUID.randomUUID().toString(),
            rowNumber = rowIndex,
            nationalCode = cleanNat.ifBlank { rawNat },
            cardNo = cleanCard.ifBlank { rawCard },
            fullNamePersian = rawName,
            fullNameLatin = latinName,
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
     * Reads lines from an InputStream with UTF-8 / Windows-1256 charset auto-detection
     * and UTF-8 BOM stripping.
     */
    fun readLines(inputStream: InputStream): List<String> {
        val lines = mutableListOf<String>()
        // Read raw bytes to detect encoding (UTF-8, UTF-8 BOM, or Persian Windows-1256/ISO-8859-1)
        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) return emptyList()

        val charset = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> {
                StandardCharsets.UTF_8
            }
            else -> {
                // Try UTF-8 first, fallback gracefully
                try {
                    val decoder = StandardCharsets.UTF_8.newDecoder()
                    decoder.decode(java.nio.ByteBuffer.wrap(bytes))
                    StandardCharsets.UTF_8
                } catch (e: Exception) {
                    try {
                        java.nio.charset.Charset.forName("windows-1256")
                    } catch (e2: Exception) {
                        StandardCharsets.ISO_8859_1
                    }
                }
            }
        }

        var text = String(bytes, charset)
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1)
        }

        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank()) {
                lines.add(trimmed)
            }
        }
        return lines
    }
}
