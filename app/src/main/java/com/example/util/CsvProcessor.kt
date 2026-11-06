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
        val is469: Boolean,
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
                var foundAny = false

                headers.forEachIndexed { index, rawHeader ->
                    val h = rawHeader.lowercase().replace(" ", "").replace("_", "")
                    when {
                        h.contains("national") || h.contains("melli") || h.contains("کدملی") || h.contains("کد_ملی") -> {
                            mapping.nationalCodeIdx = index
                            foundAny = true
                        }
                        h.contains("card") || h.contains("کارت") || h.contains("شماره_کارت") -> {
                            mapping.cardNoIdx = index
                            foundAny = true
                        }
                        h.contains("name") || h.contains("نام") -> {
                            mapping.fullNameIdx = index
                            foundAny = true
                        }
                        h.contains("birth") || h.contains("tavalod") || h.contains("تولد") -> {
                            mapping.birthDateIdx = index
                            foundAny = true
                        }
                        h.contains("mobile") || h.contains("phone") || h.contains("tel") || h.contains("موبایل") || h.contains("همراه") -> {
                            mapping.mobileIdx = index
                            foundAny = true
                        }
                    }
                }
                // If no matching headers were detected, default to standard order (0..4)
                return mapping
            }
        }
    }

    /**
     * Parses a single row into an AudienceRecord.
     */
    fun parseRow(rowValues: List<String>, mapping: ColumnMapping, rowIndex: Int): ParseRowResult {
        fun getVal(idx: Int): String = if (idx in rowValues.indices) rowValues[idx].trim().removeSurrounding("\"") else ""

        val rawNat = getVal(mapping.nationalCodeIdx)
        val rawCard = getVal(mapping.cardNoIdx)
        val rawName = getVal(mapping.fullNameIdx)
        val rawBirth = getVal(mapping.birthDateIdx)
        val rawMob = getVal(mapping.mobileIdx)

        val cleanNat = PersianTransliterator.normalizeDigits(rawNat).filter { it.isDigit() }
        val cleanMob = PersianTransliterator.normalizeMobile(rawMob)
        val cleanCard = PersianTransliterator.normalizeDigits(rawCard).filter { it.isDigit() }

        val is469 = cleanNat.startsWith("469")
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
            is469Matched = is469,
            hasMobile = hasMobile,
            isSavedToVcf = false
        )

        return ParseRowResult(record, is469, hasMobile)
    }

    /**
     * Reads all raw lines from an InputStream, skipping empty lines.
     */
    fun readLines(inputStream: InputStream): List<String> {
        val lines = mutableListOf<String>()
        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                if (line.isNotBlank()) {
                    lines.add(line)
                }
                line = reader.readLine()
            }
        }
        return lines
    }

    /**
     * Generates a realistic sample CSV containing 469 prefix records,
     * non-469 records, records with valid mobile, and records with missing mobile.
     */
    fun getSampleCsv(): String {
        return """
NATIONAL_CODE,CARD_NO,FULL_NAME,BIRTH_DATE,MOBILE
4690123456,6037997123456781,سید علیرضا حسینی,1368/04/15,09183311223
4691234567,6104337890123452,محمد احمدی پور,1370/11/20,09187744556
0012345678,6037991122334455,محسن رضایی,1365/02/10,09121112233
4692345678,5892101234567893,فاطمه رضازاده,1372/08/03,09189988776
4693456789,6274123456789014,امیرحسین کریمی نژاد,1366/03/25,09182233445
4694567890,5022291012345675,سارا مرادی آبادی,1375/06/14,
2290123456,6219861012345676,مهدی صادقی,1369/09/18,09173334455
4695678901,6037701234567897,سیده مریم طباطبایی زاده,1371/01/30,09185566778
4696789012,6280231234567898,علی اکبر بهرامی فر,1364/12/05,09186677889
4697890123,5041721012345679,ندا کاظمی خواه,1373/07/22,09181122334
3870123456,6393461012345670,بابک فرهمند,1367/05/11,09132223344
4698901234,6037998877665544,رویا باقری نیا,1376/10/08,09184455667
4699012345,6104331122334455,فرزاد دوستدار فرد,1362/02/19,
4690987654,5894631012345671,پیمان حیدری اصل,1369/04/01,09188899001
4691876543,6273811012345672,زهرا کمالی کیا,1374/12/17,09183344556
4692765432,5029081012345673,امیرعلی شریفی راد,1378/09/12,09182211443
4693654321,6219867788990011,حاجی مهدی رستمی مند,1359/08/29,09185544332
4694543210,6037691012345674,نگار اکبری تبار,1377/03/07,09187766554
0081234567,6104339988776655,سعید صادق زاده,1366/07/14,09123334455
4695432109,6280239988776655,سامان اسماعیلی بگی,1371/05/23,09189911223
        """.trimIndent()
    }
}
