package com.example

import com.example.model.AudienceRecord
import com.example.util.CsvProcessor
import com.example.util.PersianTransliterator
import com.example.util.VcfGenerator
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testPersianDigitsNormalization() {
        val raw = "۰۹۱۸۳۳۱۱۲۲۳"
        val normalized = PersianTransliterator.normalizeDigits(raw)
        assertEquals("09183311223", normalized)

        val nationalCodeRaw = "۴۶۹۰۱۲۳۴۵۶"
        val normalizedCode = PersianTransliterator.normalizeDigits(nationalCodeRaw)
        assertEquals("4690123456", normalizedCode)
        assertTrue(normalizedCode.startsWith("469"))
    }

    @Test
    fun testPersianToLatinTransliteration() {
        val name1 = PersianTransliterator.transliterate("سید علیرضا حسینی")
        assertTrue("Name should contain Seyed", name1.contains("Seyed"))
        assertTrue("Name should contain Alireza", name1.contains("Alireza"))

        val name2 = PersianTransliterator.transliterate("محمد احمدی پور")
        assertTrue("Name should contain Mohammad", name2.contains("Mohammad"))
        assertTrue("Name should contain Ahmadipour", name2.contains("Ahmadipour"))

        val name3 = PersianTransliterator.transliterate("فاطمه رضازاده")
        assertTrue("Name should contain Fatemeh", name3.contains("Fatemeh"))
        assertTrue("Name should contain Rezazadeh", name3.contains("Rezazadeh"))
    }

    @Test
    fun testCsvFilteringConditionWithCustomPrefix() {
        val mapping = CsvProcessor.ColumnMapping(0, 1, 2, 3, 4)

        // Case 1: 469 prefix and mobile present -> Qualified!
        val row1 = listOf("4690123456", "6037", "علیرضا حسینی", "1368/05/12", "09181234567")
        val res1 = CsvProcessor.parseRow(row1, mapping, 1, "469")
        assertTrue(res1.isPrefixMatched)
        assertTrue(res1.hasMobile)
        assertTrue(res1.record.isQualified)

        // Case 2: 469 prefix but missing mobile -> Not qualified
        val row2 = listOf("4690123456", "6037", "سارا مرادی", "1375/06/14", "")
        val res2 = CsvProcessor.parseRow(row2, mapping, 2, "469")
        assertTrue(res2.isPrefixMatched)
        assertFalse(res2.hasMobile)
        assertFalse(res2.record.isQualified)

        // Case 3: Custom user-specified 3-digit prefix (e.g. Tehran "001")
        val row3 = listOf("0012345678", "6037", "محسن رضایی", "1365/02/10", "09121112233")
        val res3 = CsvProcessor.parseRow(row3, mapping, 3, "001")
        assertTrue("Custom 001 prefix should match", res3.isPrefixMatched)
        assertTrue(res3.hasMobile)
        assertTrue(res3.record.isQualified)

        // Case 4: Persian digits matching custom prefix
        val row4 = listOf("۴۶۹۸۷۶۵۴۳۲", "6037", "امیرحسین کریمی", "1370/01/01", "۰۹۱۸۳۳۲۲۱۱۴")
        val res4 = CsvProcessor.parseRow(row4, mapping, 4, "469")
        assertTrue("Persian digits 469 should match", res4.isPrefixMatched)
        assertTrue("Persian digits mobile should be present", res4.hasMobile)
        assertTrue(res4.record.isQualified)
    }

    @Test
    fun testVcfGeneration() {
        val record = AudienceRecord(
            id = "test-1",
            rowNumber = 1,
            nationalCode = "4690123456",
            cardNo = "6037997123456781",
            fullNamePersian = "سید علیرضا حسینی",
            fullNameLatin = "Seyed Alireza Hosseini",
            birthDate = "1368/04/15",
            mobile = "09183311223",
            matchedPrefix = "469",
            isPrefixMatched = true,
            hasMobile = true,
            isSavedToVcf = true
        )

        val vcf = VcfGenerator.recordToVcard(record)
        assertTrue(vcf.contains("BEGIN:VCARD"))
        assertTrue(vcf.contains("VERSION:3.0"))
        assertTrue(vcf.contains("FN:Seyed Alireza Hosseini"))
        assertTrue(vcf.contains("TEL;TYPE=CELL,VOICE:09183311223"))
        assertTrue(vcf.contains("X-NATIONAL-CODE:4690123456"))
        assertTrue(vcf.contains("CATEGORIES:ParsVCF,Audience-469"))
        assertTrue(vcf.contains("END:VCARD"))
    }
}
