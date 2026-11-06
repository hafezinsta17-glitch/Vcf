package com.example.util

import java.util.Locale

/**
 * High-accuracy transliterator that converts Persian (Farsi) names into standard Latin script.
 * Supports prefixes, compound names, suffixes, ZWNJ (Zero-Width Non-Joiner),
 * Persian digits normalization, and common Iranian given/family name vocabularies.
 */
object PersianTransliterator {

    // Common Iranian First Names dictionary for natural romanization
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
        "احسان" to "Ehsan",
        "اشکان" to "Ashkan",
        "پارسا" to "Parsa",
        "بردیا" to "Bardia",
        "ایلیا" to "Iliya",
        "عباس" to "Abbas",
        "جواد" to "Javad",
        "صادق" to "Sadegh",
        "باقر" to "Bagher",
        "کاظم" to "Kazem",
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
        "فرهمند" to "Farahmand"
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
        "بگی" to "Beigi"
    )

    // Suffixes for compound Iranian family names (e.g. رضازاده, احمدی‌پور)
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
     * Transliterates a full Persian name into Latin script.
     * E.g. "سید علیرضا احمدی پور" -> "Seyed Alireza Ahmadipour" (or "Ahmadi Pour")
     */
    fun transliterate(persianName: String): String {
        val cleaned = persianName
            .replace('\u200C', ' ') // replace ZWNJ with space
            .replace(Regex("[\\s_\\-]+"), " ")
            .trim()

        if (cleaned.isEmpty()) return ""

        val rawTokens = cleaned.split(" ").filter { it.isNotEmpty() }
        val resultTokens = mutableListOf<String>()

        var i = 0
        while (i < rawTokens.size) {
            val token = rawTokens[i]
            // If next token is a particle like پور, زاده, merge them if desired
            if (i + 1 < rawTokens.size && standaloneParticles.containsKey(rawTokens[i + 1])) {
                val stemTrans = transliterateToken(token)
                val particleTrans = standaloneParticles[rawTokens[i + 1]]?.lowercase() ?: ""
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

        // 1. Direct dictionary match for common given names & family names
        commonGivenNames[trimmed]?.let { return it }

        // 2. Prefix match
        prefixes[trimmed]?.let { return it }

        // 3. Standalone particles
        standaloneParticles[trimmed]?.let { return it }

        // 4. Suffix check (e.g. رضازاده -> Rezazadeh)
        for ((sufFa, sufEn) in suffixes) {
            if (trimmed.endsWith(sufFa) && trimmed.length > sufFa.length + 1) {
                val stem = trimmed.substring(0, trimmed.length - sufFa.length)
                val transliteratedStem = transliterateToken(stem)
                return transliteratedStem + sufEn
            }
        }

        // 5. Fallback character-by-character transliteration
        return phoneticallyTransliterate(trimmed)
    }

    private fun phoneticallyTransliterate(word: String): String {
        val sb = StringBuilder()
        val len = word.length

        var i = 0
        while (i < len) {
            val c = word[i]
            val isStart = (i == 0)
            val isEnd = (i == len - 1)

            when (c) {
                'آ' -> sb.append(if (isStart) "A" else "a")
                'ا' -> {
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
                'ک' -> sb.append("k")
                'گ' -> sb.append("g")
                'ل' -> sb.append("l")
                'م' -> sb.append("m")
                'ن' -> sb.append("n")
                'و' -> {
                    if (isStart) sb.append("V")
                    else if (isEnd) sb.append("ou")
                    else sb.append("ou")
                }
                'ه' -> {
                    if (isEnd) sb.append("eh")
                    else sb.append("h")
                }
                'ی', 'ي' -> {
                    if (isStart) sb.append("Y")
                    else if (isEnd) sb.append("i")
                    else sb.append("i")
                }
                'ء', 'ئ' -> sb.append("'")
                '‌' -> {}
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
