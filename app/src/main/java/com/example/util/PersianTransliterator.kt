package com.example.util

import java.util.Locale

/**
 * High-accuracy transliterator that converts Persian (Farsi) names into standard Latin script.
 * Supports prefixes, compound names, suffixes, ZWNJ (Zero-Width Non-Joiner),
 * Persian digits normalization, and common Iranian given/family name vocabularies.
 */
object PersianTransliterator {

    // Common Iranian First Names and Family Names dictionary for natural romanization
    private val commonGivenNames = mapOf(
        "محمد" to "Mohammad",
        "علی" to "Ali",
        "حسین" to "Hossein",
        "حسن" to "Hassan",
        "رضا" to "Reza",
        "علیرضا" to "Alireza",
        "محمدرضا" to "Mohammadreza",
        "احمدرضا" to "Ahmadreza",
        "غلامرضا" to "Gholamreza",
        "حمیدرضا" to "Hamidreza",
        "امیررضا" to "Amirreza",
        "امیر" to "Amir",
        "امیرعلی" to "Amirali",
        "امیرحسین" to "Amirhossein",
        "امیرمحمد" to "Amirmohammad",
        "مهدی" to "Mehdi",
        "محمدمهدی" to "Mohammadmehdi",
        "فاطمه" to "Fatemeh",
        "زهرا" to "Zahra",
        "مریم" to "Maryam",
        "معصومه" to "Masoumeh",
        "زینب" to "Zeinab",
        "نرگس" to "Narges",
        "سارا" to "Sara",
        "ندا" to "Neda",
        "نازنین" to "Nazanin",
        "الهام" to "Elham",
        "بهاره" to "Bahareh",
        "رویا" to "Roya",
        "آرزو" to "Arezoo",
        "سمیرا" to "Samira",
        "شیما" to "Shima",
        "نگار" to "Negar",
        "پریسا" to "Parisa",
        "مینا" to "Mina",
        "سمیه" to "Somayeh",
        "سعید" to "Saeed",
        "حمید" to "Hamid",
        "مجید" to "Majid",
        "وحید" to "Vahid",
        "امید" to "Omid",
        "نوید" to "Navid",
        "فرشید" to "Farshid",
        "فرزاد" to "Farzad",
        "فرهاد" to "Farhad",
        "پیمان" to "Peyman",
        "پویا" to "Pouya",
        "پژمان" to "Pezhman",
        "کامران" to "Kamran",
        "سامان" to "Saman",
        "آرش" to "Arash",
        "آرمان" to "Arman",
        "بهرام" to "Bahram",
        "بابک" to "Babak",
        "کیان" to "Kian",
        "کوروش" to "Kourosh",
        "کورش" to "Kourosh",
        "داریوش" to "Dariush",
        "سروش" to "Soroush",
        "بهزاد" to "Behzad",
        "شهرام" to "Shahram",
        "شهروز" to "Shahrooz",
        "نیما" to "Nima",
        "سینا" to "Sina",
        "سهراب" to "Sohrab",
        "رستم" to "Rostam",
        "کاوه" to "Kaveh",
        "کامیار" to "Kamyar",
        "کیارش" to "Kiarash",
        "کیومرث" to "Kiumars",
        "کسری" to "Kasra",
        "کسرا" to "Kasra",
        "کریم" to "Karim",
        "کرم" to "Karam",
        "کاظم" to "Kazem",
        "کمال" to "Kamal",
        "کامبیز" to "Kambiz",
        "کیوان" to "Keyvan",
        "کیخسرو" to "Kaykhosrow",
        "احسان" to "Ehsan",
        "اشکان" to "Ashkan",
        "پارسا" to "Parsa",
        "بردیا" to "Bardia",
        "ایلیا" to "Iliya",
        "عباس" to "Abbas",
        "جواد" to "Javad",
        "صادق" to "Sadegh",
        "باقر" to "Bagher",
        "مرتضی" to "Morteza",
        "مصطفی" to "Mostafa",
        "مجتبی" to "Mojtaba",
        "ابراهیم" to "Ebrahim",
        "اسماعیل" to "Esmail",
        "یعقوب" to "Yaghoub",
        "یوسف" to "Yousef",
        "یاسر" to "Yaser",
        "حامد" to "Hamed",
        "میلاد" to "Milad",
        "اکبر" to "Akbar",
        "اصغر" to "Asghar",
        "قاسم" to "Ghasem",
        "محسن" to "Mohsen",
        "مسعود" to "Masoud",
        "منصور" to "Mansour",
        "محمود" to "Mahmoud",
        "داوود" to "Davoud",
        "داود" to "Davoud",
        "بهمن" to "Bahman",
        "بیژن" to "Bizhan",
        "فرامرز" to "Faramarz",
        "جهانگیر" to "Jahangir",
        "پرویز" to "Parviz",
        "جمشید" to "Jamshid",
        "احمد" to "Ahmad",
        "احمدی" to "Ahmadi",
        "حسینی" to "Hosseini",
        "رضایی" to "Rezaei",
        "مرادی" to "Moradi",
        "صادقی" to "Sadeghi",
        "طباطبایی" to "Tabatabaei",
        "کریمی" to "Karimi",
        "بهرامی" to "Bahrami",
        "کاظمی" to "Kazemi",
        "باقری" to "Bagheri",
        "دوستدار" to "Doostdar",
        "حیدری" to "Heidari",
        "کمالی" to "Kamali",
        "شریفی" to "Sharifi",
        "رستمی" to "Rostami",
        "اکبری" to "Akbari",
        "اسماعیلی" to "Esmaeili",
        "فرهمند" to "Farahmand",
        "کاویانی" to "Kavyani",
        "کیانی" to "Kiani",
        "کریمیان" to "Karimian",
        "کوشکی" to "Koushki",
        "کرمی" to "Karami",
        "کرمپور" to "Karampour",
        "کرم‌پور" to "Karampour",
        "کاظمیان" to "Kazemian",
        "کلانتری" to "Kalantari",
        "کوچکی" to "Koochaki",
        "کاشانی" to "Kashani",
        "کوهی" to "Koohi",
        "کوشا" to "Koosha",
        "کیانیان" to "Kianian"
    )

    // Prefixes that should be capitalized
    private val prefixes = mapOf(
        "سید" to "Seyed",
        "سیده" to "Seyedeh",
        "میر" to "Mir",
        "حاج" to "Haj",
        "حاجی" to "Haji",
        "شیخ" to "Sheikh",
        "کربلایی" to "Karbalaei",
        "مشهدی" to "Mashhadi"
    )

    // Standalone suffix particles
    private val standaloneParticles = mapOf(
        "پور" to "Pour",
        "زاده" to "Zadeh",
        "نیا" to "Nia",
        "نژاد" to "Nezhad",
        "فر" to "Far",
        "راد" to "Rad",
        "کیا" to "Kia",
        "مند" to "Mand",
        "وند" to "Vand",
        "آبادی" to "Abadi",
        "خواه" to "Khah",
        "تبار" to "Tabar",
        "دوست" to "Doost",
        "مهر" to "Mehr",
        "فرد" to "Fard",
        "اصل" to "Asl",
        "بگی" to "Beigi",
        "کیا" to "Kia"
    )

    // Suffixes for compound Iranian family names (e.g. رضازاده, احمدی‌پور, کریمی‌کیا)
    private val suffixes = listOf(
        "زاده" to "zadeh",
        "پور" to "pour",
        "نیا" to "nia",
        "نژاد" to "nezhad",
        "فر" to "far",
        "راد" to "rad",
        "کیا" to "kia",
        "مند" to "mand",
        "وند" to "vand",
        "آبادی" to "abadi",
        "خواه" to "khah",
        "تبار" to "tabar",
        "دوست" to "doost",
        "مهر" to "mehr",
        "فرد" to "fard",
        "اصل" to "asl",
        "بگی" to "beigi",
        "طلب" to "talab",
        "شناس" to "shenas",
        "پرور" to "parvar"
    )

    /**
     * Normalizes Persian/Arabic characters:
     * - 'ک' (Persian Kaf \u06A9) and 'ك' (Arabic Kaf \u0643) -> Unified Persian 'ک'
     * - 'ی' (Persian Yeh \u06CC) and 'ي' (Arabic Yeh \u064A) -> Unified Persian 'ی'
     * - Explicitly preserves short vowel diacritics:
     *   - Fatha (َ / \u064E) -> maps to 'a' phoneme
     *   - Kasra (ِ / \u0650) -> maps to 'e' phoneme
     *   - Damma (ُ / \u064F) -> maps to 'o' phoneme
     * - Strips tatweel/kashida, tanwin, shaddah, sukun, and normalizes ZWNJ to space
     */
    fun normalizeText(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            when (c) {
                '\u0643', '\u06A9' -> sb.append('ک') // Arabic Kaf & Persian Kaf
                '\u064A', '\u06CC', '\u0649' -> sb.append('ی') // Arabic Yeh & Persian Yeh
                '\u0640' -> {} // Kashida/Tatweel (ignore)
                '\u064E', '\u0650', '\u064F' -> sb.append(c) // PRESERVE FATHA (َ), KASRA (ِ), DAMMA (ُ)
                '\u064B', '\u064C', '\u064D', '\u0651', '\u0652' -> {} // Other non-vowel tashkeel (tanwin, shadda, sukun)
                '\u200C' -> sb.append(' ') // ZWNJ to space
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Converts Persian / Arabic digits to standard ASCII digits.
     */
    fun normalizeDigits(input: String): String {
        val builder = StringBuilder()
        for (ch in input) {
            when (ch) {
                '۰', '٠' -> builder.append('0')
                '۱', '١' -> builder.append('1')
                '۲', '٢' -> builder.append('2')
                '۳', '٣' -> builder.append('3')
                '۴', '٤' -> builder.append('4')
                '۵', '٥' -> builder.append('5')
                '۶', '٦' -> builder.append('6')
                '۷', '٧' -> builder.append('7')
                '۸', '٨' -> builder.append('8')
                '۹', '٩' -> builder.append('9')
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }

    /**
     * Clean phone number: converts to digits, ensures Iranian mobile normalization.
     */
    fun normalizeMobile(raw: String): String {
        val clean = normalizeDigits(raw).filter { it.isDigit() || it == '+' }
        if (clean.isEmpty()) return ""
        
        return if (clean.startsWith("+98")) {
            "0" + clean.substring(3)
        } else if (clean.startsWith("98") && clean.length > 10) {
            "0" + clean.substring(2)
        } else if (clean.length == 10 && clean.startsWith("9")) {
            "0$clean"
        } else {
            clean
        }
    }

    /**
     * Transliterates a full Persian name into Latin script with Persian phoneme handling.
     * E.g. "سید علیرضا احمدی پور" -> "Seyed Alireza Ahmadipour"
     * "کاوه کریمی" -> "Kaveh Karimi" (Kaf 'ک' preserved accurately)
     */
    fun transliterate(persianName: String): String {
        val normalized = normalizeText(persianName)
            .replace(Regex("[\\s_\\-]+"), " ")
            .trim()

        if (normalized.isEmpty()) return ""

        val rawTokens = normalized.split(" ").filter { it.isNotEmpty() }
        val resultTokens = mutableListOf<String>()

        var i = 0
        while (i < rawTokens.size) {
            val token = rawTokens[i]
            // If next token is a particle like پور, زاده, merge them
            if (i + 1 < rawTokens.size && standaloneParticles.containsKey(rawTokens[i + 1])) {
                val stemTrans = transliterateToken(token)
                val particleTrans = standaloneParticles[rawTokens[i + 1]]?.lowercase(Locale.ROOT) ?: ""
                resultTokens.add(stemTrans + particleTrans)
                i += 2
                continue
            }

            val trans = transliterateToken(token)
            if (trans.isNotEmpty()) {
                resultTokens.add(trans)
            }
            i++
        }

        return resultTokens.joinToString(" ")
    }

    private fun transliterateToken(token: String): String {
        val trimmed = token.trim()

        // 1. Check exact dictionary match
        commonGivenNames[trimmed]?.let { return it }

        // Also check stripped version if word has diacritics (e.g. كَرِیم -> کریم -> Karim)
        val stripped = stripDiacritics(trimmed)
        if (stripped != trimmed) {
            commonGivenNames[stripped]?.let { return it }
        }

        // 2. Prefix match
        prefixes[trimmed]?.let { return it }
        prefixes[stripped]?.let { return it }

        // 3. Standalone particles
        standaloneParticles[trimmed]?.let { return it }
        standaloneParticles[stripped]?.let { return it }

        // 4. Suffix check (e.g. رضازاده -> Rezazadeh, کریمی‌پور -> Karimipour)
        for ((sufFa, sufEn) in suffixes) {
            if (trimmed.endsWith(sufFa) && trimmed.length > sufFa.length + 1) {
                val stem = trimmed.substring(0, trimmed.length - sufFa.length)
                val transliteratedStem = transliterateToken(stem)
                return transliteratedStem + sufEn
            }
        }

        // 5. High-fidelity phoneme transliteration with Persian short vowel awareness
        return phoneticallyTransliterate(trimmed)
    }

    private fun stripDiacritics(input: String): String {
        return input.filter { it != '\u064E' && it != '\u0650' && it != '\u064F' }
    }

    /**
     * Persian phoneme transliteration:
     * - 'ک' (kāf) is ALWAYS transliterated as 'k' (never ignored).
     * - Recognizes short vowels:
     *   - Fatha (َ / \u064E) -> "a"
     *   - Kasra (ِ / \u0650) -> "e"
     *   - Damma (ُ / \u064F) -> "o"
     * - Recognizes Persian digraphs: 'خ' (kh), 'ش' (sh), 'چ' (ch), 'ژ' (zh), 'غ'/'ق' (gh).
     */
    private fun phoneticallyTransliterate(word: String): String {
        val sb = StringBuilder()
        val len = word.length

        var i = 0
        while (i < len) {
            val c = word[i]
            val isStart = (i == 0)
            val isEnd = (i == len - 1)

            // Lookahead for digraphs and phoneme patterns
            val nextC = if (i + 1 < len) word[i + 1] else null

            // Special Persian phoneme: خوا (Khva -> Kha, e.g. خواجه Khajeh, خوانساری Khansari)
            if (c == 'خ' && nextC == 'و' && i + 2 < len && word[i + 2] == 'ا') {
                sb.append("kha")
                i += 3
                continue
            }

            when (c) {
                // Persian Short Vowels (Harakat / Erab):
                // 1. Fatha (َ / \u064E) -> Persian short 'a'
                '\u064E' -> sb.append("a")
                // 2. Kasra (ِ / \u0650) -> Persian short 'e'
                '\u0650' -> sb.append("e")
                // 3. Damma (ُ / \u064F) -> Persian short 'o'
                '\u064F' -> sb.append("o")

                // Kaf: MUST NEVER be ignored! Supports both Persian 'ک' and Arabic 'ك'
                'ک', '\u0643' -> sb.append("k")
                'گ' -> sb.append("g")

                'آ' -> sb.append(if (isStart) "A" else "a")
                'ا' -> {
                    // Check if followed by short vowel diacritic
                    if (isStart) sb.append("A")
                    else sb.append("a")
                }
                'ب' -> sb.append("b")
                'پ' -> sb.append("p")
                'ت', 'ط' -> sb.append("t")
                'ث', 'س', 'ص' -> sb.append("s")
                'ج' -> sb.append("j")
                'چ' -> sb.append("ch")
                'ح' -> sb.append("h")
                'خ' -> sb.append("kh")
                'د' -> sb.append("d")
                'ذ', 'ز', 'ض', 'ظ' -> sb.append("z")
                'ر' -> sb.append("r")
                'ژ' -> sb.append("zh")
                'ش' -> sb.append("sh")
                'ع' -> {
                    if (isStart) sb.append("A")
                    else sb.append("a")
                }
                'غ', 'ق' -> sb.append("gh")
                'ف' -> sb.append("f")
                'ل' -> sb.append("l")
                'م' -> sb.append("m")
                'ن' -> sb.append("n")
                'و' -> {
                    if (isStart) sb.append("V")
                    else if (isEnd) sb.append("ou")
                    else sb.append("oo")
                }
                'ه' -> {
                    if (isEnd) sb.append("eh")
                    else sb.append("h")
                }
                'ی', 'ي', 'ى' -> {
                    if (isStart) sb.append("Y")
                    else if (isEnd) sb.append("i")
                    else sb.append("i")
                }
                'ء', 'ئ', 'ؤ' -> sb.append("'")
                ' ' -> sb.append(" ")
                else -> {
                    if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9') {
                        sb.append(c)
                    }
                }
            }
            i++
        }

        val raw = sb.toString()
        if (raw.isEmpty()) return ""

        return raw.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }
    }
}
