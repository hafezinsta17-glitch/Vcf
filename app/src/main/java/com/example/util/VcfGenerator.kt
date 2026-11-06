package com.example.util

import com.example.model.AudienceRecord
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object VcfGenerator {

    /**
     * Converts a single AudienceRecord into standard vCard 3.0 text block.
     */
    fun recordToVcard(record: AudienceRecord): String {
        val names = record.fullNameLatin.trim().split(" ").filter { it.isNotEmpty() }
        val firstName = if (names.isNotEmpty()) names.first() else ""
        val lastName = if (names.size > 1) names.subList(1, names.size).joinToString(" ") else ""

        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCARD")
        sb.appendLine("VERSION:3.0")
        sb.appendLine("FN:${escapeVcf(record.fullNameLatin)}")
        sb.appendLine("N:${escapeVcf(lastName)};${escapeVcf(firstName)};;;")
        if (record.mobile.isNotBlank()) {
            sb.appendLine("TEL;TYPE=CELL,VOICE:${record.mobile}")
        }
        val noteContent = buildString {
            append("National Code: ${record.nationalCode}")
            if (record.cardNo.isNotBlank()) append(" | Card: ${record.cardNo}")
            if (record.birthDate.isNotBlank()) append(" | Birth: ${record.birthDate}")
            if (record.fullNamePersian.isNotBlank()) append(" | Persian Name: ${record.fullNamePersian}")
        }
        sb.appendLine("NOTE:${escapeVcf(noteContent)}")
        sb.appendLine("X-NATIONAL-CODE:${escapeVcf(record.nationalCode)}")
        if (record.cardNo.isNotBlank()) {
            sb.appendLine("X-CARD-NO:${escapeVcf(record.cardNo)}")
        }
        if (record.birthDate.isNotBlank()) {
            sb.appendLine("X-BIRTH-DATE:${escapeVcf(record.birthDate)}")
        }
        if (record.fullNamePersian.isNotBlank()) {
            sb.appendLine("X-PERSIAN-NAME:${escapeVcf(record.fullNamePersian)}")
        }
        sb.appendLine("CATEGORIES:ParsVCF,Audience-${record.matchedPrefix}")
        sb.appendLine("END:VCARD")
        return sb.toString()
    }

    /**
     * Converts an entire collection of audience records into a full VCF file string.
     */
    fun generateVcf(records: List<AudienceRecord>): String {
        val sb = StringBuilder()
        for (record in records) {
            sb.append(recordToVcard(record))
        }
        return sb.toString()
    }

    /**
     * Writes the records directly into a target file with UTF-8 encoding.
     */
    fun writeToFile(file: File, records: List<AudienceRecord>) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                for (record in records) {
                    writer.write(recordToVcard(record))
                }
            }
        }
    }

    private fun escapeVcf(value: String): String {
        return value.replace("\\", "\\\\")
            .replace(",", "\\,")
            .replace(";", "\\;")
            .replace("\n", "\\n")
    }
}
