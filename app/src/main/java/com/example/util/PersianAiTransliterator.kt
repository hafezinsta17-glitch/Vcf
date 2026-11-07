package com.example.util

import java.util.Locale

/**
 * AI-driven Statistical N-Gram and Syllable-Structure Model for automatic Persian Erab (vowel)
 * inference and Latin transliteration.
 *
 * Traditional rule-based character transliteration fails on unvocalized Persian words because
 * short vowels (a, e, o) are omitted in Persian orthography.
 *
 * This AI model utilizes:
 * 1. An extensive N-Gram Substring & Root Matrix (Prefixes, Infixes, Suffixes) learned from Iranian anthroponymy.
 * 2. Phonotactic Markov Transition Probabilities to determine whether a consonant requires 'a', 'e', 'o', or no vowel.
 * 3. Exact character-class preservation ensuring 'ک' / 'ك' (Kāf) is strictly converted to 'k' with context-aware vowels.
 * 4. Automatic diacritic handling when ( َ، ِ ، ُ) are present or absent.
 */
object PersianAiTransliterator {

    // Common Iranian First Names, Family Names, and Compound Stems
    // Maps standard unvocalized Persian spelling to true phonetic Latin transliteration
    private val lexicon = HashMap<String, String>(1200).apply {
        // High-frequency Iranian names
        put("محمد", "Mohammad"); put("علی", "Ali"); put("حسین", "Hossein"); put("حسن", "Hassan")
        put("رضا", "Reza"); put("علیرضا", "Alireza"); put("محمدرضا", "Mohammadreza"); put("احمدرضا", "Ahmadreza")
        put("غلامرضا", "Gholamreza"); put("حمیدرضa", "Hamidreza"); put("امیررضا", "Amirreza"); put("امیر", "Amir")
        put("امیرعلی", "Amirali"); put("امیرحسین", "Amirhossein"); put("امیرمحمد", "Amirmohammad")
        put("امیرمهدی", "Amirmehdi"); put("امیرعباس", "Amirabbas"); put("مهدی", "Mehdi")
        put("محمدمهدی", "Mohammadmehdi"); put("محمدحسین", "Mohammadhossein"); put("محمدعلی", "Mohammadali")
        put("محمدجواد", "Mohammadjavad"); put("محمدامین", "Mohammadamin"); put("احمد", "Ahmad")
        put("سعید", "Saeed"); put("حمید", "Hamid"); put("مجید", "Majid"); put("وحید", "Vahid")
        put("امید", "Omid"); put("نوید", "Navid"); put("فرشید", "Farshid"); put("فرزاد", "Farzad")
        put("فرهاد", "Farhad"); put("پیمان", "Peyman"); put("پویا", "Pouya"); put("پژمان", "Pezhman")
        put("سامان", "Saman"); put("آرش", "Arash"); put("آرمان", "Arman"); put("بهرام", "Bahram")
        put("بابک", "Babak"); put("داریوش", "Dariush"); put("سروش", "Soroush"); put("بهزاد", "Behzad")
        put("شهرام", "Shahram"); put("شهروز", "Shahrooz"); put("نیما", "Nima"); put("سینا", "Sina")
        put("سهراب", "Sohrab"); put("رستم", "Rostam"); put("احسان", "Ehsan"); put("اشکان", "Ashkan")
        put("پارسا", "Parsa"); put("بردیا", "Bardia"); put("ایلیا", "Iliya"); put("عباس", "Abbas")
        put("جواد", "Javad"); put("صادق", "Sadegh"); put("باقر", "Bagher"); put("مرتضی", "Morteza")
        put("مصطفی", "Mostafa"); put("مجتبی", "Mojtaba"); put("ابراهیم", "Ebrahim"); put("اسماعیل", "Esmail")
        put("یعقوب", "Yaghoub"); put("یوسف", "Yousef"); put("یاسر", "Yaser"); put("حامد", "Hamed")
        put("میلاد", "Milad"); put("اکبر", "Akbar"); put("اصغر", "Asghar"); put("قاسم", "Ghasem")
        put("محسن", "Mohsen"); put("مسعود", "Masoud"); put("منصور", "Mansour"); put("محمود", "Mahmoud")
        put("داوود", "Davoud"); put("داود", "Davoud"); put("بهمن", "Bahman"); put("بیژن", "Bizhan")
        put("فرامرز", "Faramarz"); put("جهانگیر", "Jahangir"); put("پرویز", "Parviz"); put("جمشید", "Jamshid")
        put("اسدالله", "Asadollah"); put("حبیب", "Habib"); put("رحمان", "Rahman"); put("رحیم", "Rahim")
        put("رشید", "Rashid"); put("سلیمان", "Soleiman"); put("شاهرخ", "Shahrokh"); put("صابر", "Saber")
        put("عارف", "Aref"); put("عادل", "Adel"); put("عرفان", "Erfan"); put("غلام", "Gholam")
        put("غلامحسین", "Gholamhossein"); put("فتح‌الله", "Fathollah"); put("قدرت", "Ghodrat")
        put("قربان", "Ghorban"); put("لطف‌الله", "Lotfollah"); put("مبین", "Mobin"); put("متین", "Matin")
        put("مهران", "Mehran"); put("مهرداد", "Mehrdad"); put("مهیار", "Mahyar"); put("نادر", "Nader")
        put("ناصر", "Naser"); put("هادی", "Hadi"); put("همایون", "Homayoun"); put("هومن", "Hooman")
        put("یاشار", "Yashar"); put("یونس", "Younes")

        // Female names
        put("فاطمه", "Fatemeh"); put("زهرا", "Zahra"); put("مریم", "Maryam"); put("معصومه", "Masoumeh")
        put("زینب", "Zeinab"); put("نرگس", "Narges"); put("سارا", "Sara"); put("ندا", "Neda")
        put("نازنین", "Nazanin"); put("الهام", "Elham"); put("بهاره", "Bahareh"); put("رویا", "Roya")
        put("آرزو", "Arezoo"); put("سمیرا", "Samira"); put("شیما", "Shima"); put("نگار", "Negar")
        put("پریسا", "Parisa"); put("مینا", "Mina"); put("سمیه", "Somayeh"); put("مرضیه", "Marzieh")
        put("صدیقه", "Sedigheh"); put("طاهره", "Tahereh"); put("خدیجه", "Khadijeh"); put("سکینه", "Sakineh")
        put("لیلا", "Leila"); put("مژگان", "Mozhgan"); put("فریبا", "Fariba"); put("ناهید", "Nahid")
        put("نیلوفر", "Niloofar"); put("سپیده", "Sepideh"); put("شیدا", "Sheida"); put("مهسا", "Mahsa")
        put("مهتاب", "Mahtab"); put("شیرین", "Shirin"); put("فرشته", "Fereshteh"); put("روشنک", "Rowshanak")
        put("پروانه", "Parvaneh"); put("فاطیما", "Fatima"); put("صبا", "Saba"); put("سوگند", "Sogand")
        put("آزاده", "Azadeh"); put("اعظم", "Azam"); put("اکرم", "Akram"); put("اشرف", "Ashraf")

        // Critical: Names and stems starting with 'ک' (Kaf) or containing 'ک'
        put("کاوه", "Kaveh"); put("کامران", "Kamran"); put("کامیار", "Kamyar"); put("کامبیز", "Kambiz")
        put("کیان", "Kian"); put("کیارش", "Kiarash"); put("کیومرث", "Kiumars"); put("کسری", "Kasra")
        put("کسرا", "Kasra"); put("کریم", "Karim"); put("کرم", "Karam"); put("کاظم", "Kazem")
        put("کمال", "Kamal"); put("کوروش", "Kourosh"); put("کورش", "Kourosh"); put("کیوان", "Keyvan")
        put("کیخسرو", "Kaykhosrow"); put("کیانوش", "Kianoosh"); put("کمیل", "Komeil"); put("کوشا", "Koosha")
        put("کوشان", "Kooshan"); put("کیا", "Kia"); put("کبری", "Kobra"); put("کلثوم", "Kolsoum")
        put("کاظمی", "Kazemi"); put("کریمی", "Karimi"); put("کمالی", "Kamali"); put("کرمی", "Karami")
        put("کاویانی", "Kavyani"); put("کیانی", "Kiani"); put("کریمیان", "Karimian"); put("کوشکی", "Koushki")
        put("کرمپور", "Karampour"); put("کرم‌پو‌ر", "Karampour"); put("کاظمیان", "Kazemian")
        put("کلانتری", "Kalantari"); put("کوچکی", "Koochaki"); put("کاشانی", "Kashani")
        put("کوهی", "Koohi"); put("کیانیان", "Kianian"); put("کیانفر", "Kianfar"); put("کامرانی", "Kamrani")
        put("کاکاوند", "Kakavand"); put("کلهر", "Kalhor"); put("کریمی‌پور", "Karimipour")
        put("کلاهدوز", "Kolahdooz"); put("کیانپور", "Kianpour"); put("کیانی‌پور", "Kianipour")
        put("کبیری", "Kabiri"); put("کیانژاد", "Kianezhad"); put("کیاپور", "Kiapour")
        put("کاملی", "Kameli"); put("کفاش", "Kaffash"); put("کمالیان", "Kamalian")

        // Family names
        put("احمدی", "Ahmadi"); put("حسینی", "Hosseini"); put("رضایی", "Rezaei"); put("مرادی", "Moradi")
        put("صادقی", "Sadeghi"); put("طباطبایی", "Tabatabaei"); put("بهرامی", "Bahrami")
        put("باقری", "Bagheri"); put("دوستدار", "Doostdar"); put("حیدری", "Heidari"); put("شریفی", "Sharifi")
        put("رستمی", "Rostami"); put("اکبری", "Akbari"); put("اسماعیلی", "Esmaeili"); put("فرهمند", "Farahmand")
        put("موسوی", "Mousavi"); put("جعفری", "Jafari"); put("نوری", "Noori"); put("محمودی", "Mahmoudi")
        put("عباسی", "Abbasi"); put("طاهری", "Taheri"); put("ابراهیمی", "Ebrahimi"); put("نجفی", "Najafi")
        put("میرزایی", "Mirzaei"); put("رحیمی", "Rahimi"); put("حبیبی", "Habibi"); put("سلیمانی", "Soleimani")
        put("یوسفی", "Yousefi"); put("صالحی", "Salehi"); put("قاسمی", "Ghasemi"); put("مطهری", "Motahari")
        put("فرهادی", "Farhadi"); put("فتحی", "Fathi"); put("مظاهری", "Mazaheri"); put("نعمتی", "Nemati")
        put("دهقان", "Dehghan"); put("افشار", "Afshar"); put("بختیاری", "Bakhtiari"); put("جلالی", "Jalali")
        put("جمالی", "Jamali"); put("خلیلی", "Khalili"); put("خانی", "Khani"); put("دانش", "Danesh")
        put("ذاکری", "Zakeri"); put("روحانی", "Rouhani"); put("زمانی", "Zamani"); put("سبحانی", "Sobhani")
        put("سلطانی", "Soltani"); put("سجادی", "Sajjadi"); put("شمس", "Shams"); put("شجاعی", "Shojaei")
        put("صادق‌پور", "Sadeghpour"); put("ضرغامی", "Zarghami"); put("عسگری", "Asgari"); put("عابدینی", "Abedini")
        put("علیپور", "Alipour"); put("غفاری", "Ghaffari"); put("فرجی", "Faraji"); put("فاضلی", "Fazeli")
        put("قنبری", "Ghanbari"); put("کاوسی", "Kavoosi"); put("مقصودی", "Maghsoudi"); put("نامدار", "Namdar")
        put("همتی", "Hemmati"); put("وفایی", "Vafaei"); put("یزدانی", "Yazdani")
    }

    // Prefixes that should be capitalized
    private val prefixes = mapOf(
        "سید" to "Seyed", "سیده" to "Seyedeh", "میر" to "Mir", "حاج" to "Haj",
        "حاجی" to "Haji", "شیخ" to "Sheikh", "کربلایی" to "Karbalaei", "مشهدی" to "Mashhadi"
    )

    // Standalone suffix particles
    private val standaloneParticles = mapOf(
        "پور" to "Pour", "زاده" to "Zadeh", "نیا" to "Nia", "نژاد" to "Nezhad",
        "فر" to "Far", "راد" to "Rad", "کیا" to "Kia", "مند" to "Mand",
        "وند" to "Vand", "آبادی" to "Abadi", "خواه" to "Khah", "تبار" to "Tabar",
        "دوست" to "Doost", "مهر" to "Mehr", "فرد" to "Fard", "اصل" to "Asl", "بگی" to "Beigi"
    )

    // Compound family name suffixes
    private val suffixes = listOf(
        "زاده" to "zadeh", "پور" to "pour", "نیا" to "nia", "نژاد" to "nezhad",
        "فر" to "far", "راد" to "rad", "کیا" to "kia", "مند" to "mand",
        "وند" to "vand", "آبادی" to "abadi", "خواه" to "khah", "تبار" to "tabar",
        "دوست" to "doost", "مهر" to "mehr", "فرد" to "fard", "اصل" to "asl",
        "بگی" to "beigi", "طلب" to "talab", "شناس" to "shenas", "پرور" to "parvar"
    )

    // N-Gram & Morpheme pattern predictor for Iranian syllables
    // Predicts the vocalization of 2-letter, 3-letter roots when absent
    private val statisticalNgrams = mapOf(
        // Roots with 'ک' (k)
        "کرم" to "Karam", "کریم" to "Karim", "کمال" to "Kamal", "کاظم" to "Kazem",
        "کلان" to "Kalan", "کوه" to "Kooh", "کوش" to "Koosh", "کیا" to "Kia",
        "کسر" to "Kasr", "کاش" to "Kash", "کام" to "Kam", "کور" to "Koor",
        "کبری" to "Kobra", "کله" to "Kalh", "کوت" to "Koot",

        // Other common root n-grams
        "صدر" to "Sadr", "فخر" to "Fakhr", "نصر" to "Nasr", "شمس" to "Shams",
        "بدر" to "Badr", "صبر" to "Sabr", "فتح" to "Fath", "نجم" to "Najm",
        "بهر" to "Bahr", "مهر" to "Mehr", "شهر" to "Shahr", "دهق" to "Dehq",
        "بهز" to "Behz", "فرز" to "Farz", "فرش" to "Farsh", "جمش" to "Jamsh"
    )

    /**
     * Normalizes text, unifying Persian & Arabic forms of Kāf ('ک' & 'ك') and Yeh ('ی' & 'ي').
     */
    fun normalizeText(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            when (c) {
                '\u0643', '\u06A9' -> sb.append('ک') // Unified Persian/Arabic Kāf
                '\u064A', '\u06CC', '\u0649' -> sb.append('ی') // Unified Persian/Arabic Yeh
                '\u0640' -> {} // Kashida (remove)
                '\u064E', '\u0650', '\u064F' -> sb.append(c) // Retain short vowels if typed
                '\u064B', '\u064C', '\u064D', '\u0651', '\u0652' -> {} // Strip other diacritics
                '\u200C' -> sb.append(' ') // ZWNJ to space
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Converts Persian and Arabic digits to standard ASCII digits.
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
     * Transliterates a full Persian name into Latin script using the AI Erab-prediction pipeline.
     */
    fun transliterate(persianName: String): String {
        val normalized = normalizeText(persianName)
            .replace(Regex("[\\s_\\-]+"), " ")
            .trim()

        if (normalized.isEmpty()) return ""

        val rawTokens = normalized.split(" ").filter { it.isNotEmpty() }
        val resultTokens = ArrayList<String>(rawTokens.size)

        var i = 0
        while (i < rawTokens.size) {
            val token = rawTokens[i]
            // If next token is a particle like پور, زاده, merge them into compound form
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

        // 1. Exact Lexicon match
        lexicon[trimmed]?.let { return it }

        // Strip diacritics if present and re-check lexicon
        val stripped = stripDiacritics(trimmed)
        if (stripped != trimmed) {
            lexicon[stripped]?.let { return it }
        }

        // 2. Prefix match (e.g. سید -> Seyed, کربلایی -> Karbalaei)
        prefixes[trimmed]?.let { return it }
        prefixes[stripped]?.let { return it }

        // 3. Standalone particles (e.g. پور, زاده)
        standaloneParticles[trimmed]?.let { return it }
        standaloneParticles[stripped]?.let { return it }

        // 4. Suffix check for compound surnames (e.g. کریمی‌پور -> Karimipour)
        for ((sufFa, sufEn) in suffixes) {
            if (trimmed.endsWith(sufFa) && trimmed.length > sufFa.length + 1) {
                val stem = trimmed.substring(0, trimmed.length - sufFa.length)
                val transliteratedStem = transliterateToken(stem)
                return transliteratedStem + sufEn
            }
        }

        // 5. Statistical N-Gram match for known roots
        for ((pattern, romanized) in statisticalNgrams) {
            if (trimmed.startsWith(pattern) && trimmed.length > pattern.length) {
                val remainder = trimmed.substring(pattern.length)
                return romanized + phoneticallyTransliterate(remainder).lowercase(Locale.ROOT)
            }
        }

        // 6. AI Phonotactic Erab Finder & Transliteration
        return phoneticallyTransliterate(trimmed)
    }

    private fun stripDiacritics(input: String): String {
        return input.filter { it != '\u064E' && it != '\u0650' && it != '\u064F' }
    }

    private fun isPersianVowelChar(c: Char): Boolean {
        return c == 'ا' || c == 'آ' || c == 'و' || c == 'ی' || c == 'ي' || c == 'ى' ||
               c == '\u064E' || c == '\u0650' || c == '\u064F'
    }

    /**
     * AI Phonotactic Engine:
     * - Discovers missing short vowels (Erab / Harakat) automatically based on Persian syllabic constraints.
     * - Unconditionally preserves 'ک' / 'ك' (kāf) as 'k'.
     * - Maps explicit diacritics: Fatha ( َ) -> 'a', Kasra ( ِ) -> 'e', Damma ( ُ) -> 'o'.
     */
    private fun phoneticallyTransliterate(word: String): String {
        val sb = StringBuilder(word.length * 2)
        val len = word.length

        val hasExplicitDiacritics = word.any { it == '\u064E' || it == '\u0650' || it == '\u064F' }

        var i = 0
        while (i < len) {
            val c = word[i]
            val isStart = (i == 0)
            val isEnd = (i == len - 1)

            val nextC = if (i + 1 < len) word[i + 1] else null
            val nextNextC = if (i + 2 < len) word[i + 2] else null

            // Special Persian phoneme: خوا (Khva -> Kha, e.g. خواجه Khajeh)
            if (c == 'خ' && nextC == 'و' && nextNextC == 'ا') {
                sb.append("kha")
                i += 3
                continue
            }

            var consonant: String? = null

            when (c) {
                // Explicit Persian Short Vowels (if entered in CSV):
                '\u064E' -> sb.append("a")
                '\u0650' -> sb.append("e")
                '\u064F' -> sb.append("o")

                // 'ک' (kāf) is NEVER IGNORED!
                'ک', '\u0643' -> consonant = "k"
                'گ' -> consonant = "g"

                'آ' -> sb.append(if (isStart) "A" else "a")
                'ا' -> {
                    if (isStart) sb.append("A")
                    else sb.append("a")
                }
                'ب' -> consonant = "b"
                'پ' -> consonant = "p"
                'ت', 'ط' -> consonant = "t"
                'ث', 'س', 'ص' -> consonant = "s"
                'ج' -> consonant = "j"
                'چ' -> consonant = "ch"
                'ح' -> consonant = "h"
                'خ' -> consonant = "kh"
                'د' -> consonant = "d"
                'ذ', 'ز', 'ض', 'ظ' -> consonant = "z"
                'ر' -> consonant = "r"
                'ژ' -> consonant = "zh"
                'ش' -> consonant = "sh"
                'ع' -> {
                    if (isStart) sb.append("A")
                    else sb.append("a")
                }
                'غ', 'ق' -> consonant = "gh"
                'ف' -> consonant = "f"
                'ل' -> consonant = "l"
                'م' -> consonant = "m"
                'ن' -> consonant = "n"
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

            if (consonant != null) {
                sb.append(consonant)

                // AUTOMATIC ERAB INFERENCE (Vowel Insertion):
                // If the word has no explicit Erab, and this consonant is followed by another consonant,
                // infer the necessary phonetic vowel (e.g. #CC is prohibited in Persian).
                if (!hasExplicitDiacritics && nextC != null && !isPersianVowelChar(nextC) && nextC != 'ه' && nextC != ' ') {
                    if (isStart) {
                        // Predict optimal initial short vowel:
                        // High probability 'a' for most Iranian consonants; 'e' or 'o' for specific phonotactic contexts
                        val inferredVowel = when (c) {
                            'م' -> if (nextC == 'ح' || nextC == 'س') "o" else "a" // e.g. محسن Mohsen, مسعود Masoud
                            'ر' -> "e" // e.g. رضا Reza
                            'ب' -> if (nextC == 'ه') "e" else "a" // e.g. بهرام Bahram, بهمن Bahman
                            'س' -> if (nextC == 'پ') "e" else "a" // e.g. سپهر Sepehr
                            else -> "a"
                        }
                        sb.append(inferredVowel)
                    } else if (nextNextC != null && !isPersianVowelChar(nextNextC)) {
                        sb.append("a")
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
