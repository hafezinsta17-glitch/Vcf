package com.example.util

import com.example.model.AudienceRecord
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object VcfGenerator {

    /**
     * Streams an individual record directly to a Writer with no intermediate string concatenation.
     */
    /**
     * Streams an individual record directly to a Writer with original Persian name.
     */
    fun appendRecordToWriter(writer: java.io.Writer, record: AudienceRecord) {
        val persianName = record.fullNamePersian.trim()
        val displayName = persianName.ifBlank { "مخاطب ${record.nationalCode}" }

        // Split Persian name into first name and family name if possible
        val tokens = persianName.split(" ").filter { it.isNotEmpty() }
        val firstName = if (tokens.isNotEmpty()) tokens.first() else displayName
        val lastName = if (tokens.size > 1) tokens.subList(1, tokens.size).joinToString(" ") else ""

        writer.write("BEGIN:VCARD\r\n")
        writer.write("VERSION:3.0\r\n")
        writer.write("FN;CHARSET=UTF-8:${escapeVcf(displayName)}\r\n")
        writer.write("N;CHARSET=UTF-8:${escapeVcf(lastName)};${escapeVcf(firstName)};;;\r\n")
        if (record.mobile.isNotBlank()) {
            writer.write("TEL;TYPE=CELL,VOICE:${record.mobile}\r\n")
        }
        val noteContent = buildString {
            append("کد ملی: ${record.nationalCode}")
            if (record.cardNo.isNotBlank()) append(" | کارت: ${record.cardNo}")
            if (record.birthDate.isNotBlank()) append(" | تاریخ تولد: ${record.birthDate}")
        }
        writer.write("NOTE;CHARSET=UTF-8:${escapeVcf(noteContent)}\r\n")
        writer.write("X-NATIONAL-CODE:${escapeVcf(record.nationalCode)}\r\n")
        if (record.cardNo.isNotBlank()) {
            writer.write("X-CARD-NO:${escapeVcf(record.cardNo)}\r\n")
        }
        if (record.birthDate.isNotBlank()) {
            writer.write("X-BIRTH-DATE:${escapeVcf(record.birthDate)}\r\n")
        }
        writer.write("CATEGORIES:ParsVCF,Audience-${record.matchedPrefix}\r\n")
        writer.write("END:VCARD\r\n")
    }

    /**
     * Converts a single AudienceRecord into standard vCard 3.0 text block.
     */
    fun recordToVcard(record: AudienceRecord): String {
        val sw = java.io.StringWriter(256)
        appendRecordToWriter(sw, record)
        return sw.toString()
    }

    /**
     * Converts an entire collection of audience records into a full VCF file string (for small preview).
     */
    fun generateVcf(records: List<AudienceRecord>, maxRecords: Int = 100): String {
        val sw = java.io.StringWriter()
        val slice = if (records.size > maxRecords) records.take(maxRecords) else records
        for (record in slice) {
            appendRecordToWriter(sw, record)
        }
        if (records.size > maxRecords) {
            sw.write("\r\n# ... and ${records.size - maxRecords} more contacts saved in complete VCF file\r\n")
        }
        return sw.toString()
    }

    /**
     * Writes the records directly into a target file with UTF-8 encoding.
     */
    fun writeToFile(file: File, records: List<AudienceRecord>) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { fos ->
            java.io.BufferedWriter(OutputStreamWriter(fos, StandardCharsets.UTF_8), 65536).use { writer ->
                for (record in records) {
                    appendRecordToWriter(writer, record)
                }
            }
        }
    }

    private fun escapeVcf(value: String): String {
        return value.replace("\\", "\\\\")
            .replace(",", "\\,")
            .replace(";", "\\;")
            .replace("\r", "")
            .replace("\n", "\\n")
    }
}
