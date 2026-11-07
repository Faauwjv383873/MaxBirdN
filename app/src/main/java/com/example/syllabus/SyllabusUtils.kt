package com.example.syllabus

import com.example.api.BatchOptionItem
import com.example.api.ClassItem
import com.example.api.StudyGroupItem

/**
 * Loose match: user-এর stored study_group value (যেমন "HUM") আর StudyGroupItem (code="Humanities")।
 * Case-insensitive prefix / substring matching — কোনো hardcoded map নেই।
 *
 * কেন দরকার: JWT meta-তে "HUM" আসে, profile-এ "HUM" আসে, কিন্তু API enum-এ "Humanities"।
 */
fun StudyGroupItem.matchesCodeOrName(raw: String?): Boolean {
    if (raw.isNullOrBlank()) return false
    val normalized = raw.trim().lowercase()
    if (normalized.isEmpty()) return false
    return listOfNotNull(code, name_en, title_en, name_bn)
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .any { field ->
            field == normalized ||
                    field.startsWith(normalized) ||
                    normalized.startsWith(field) ||
                    field.contains(normalized)
        }
}

/**
 * Settings screen-এর সাথে consistent বাংলা ordinal class নাম।
 * Unknown code হলে API-র name_bn / title_bn / name_en use করবে — hardcoded fallback ছাড়া।
 */
fun ClassItem.bengaliClassName(): String {
    return when (code.trim().uppercase()) {
        "C5", "C05" -> "পঞ্চম শ্রেণি"
        "C6", "C06" -> "ষষ্ঠ শ্রেণি"
        "C7", "C07" -> "সপ্তম শ্রেণি"
        "C8", "C08" -> "অষ্টম শ্রেণি"
        "C9", "C09" -> "নবম শ্রেণি"
        "C10" -> "দশম শ্রেণি"
        "C11" -> "একাদশ শ্রেণি"
        "C12" -> "দ্বাদশ শ্রেণি"
        else -> name_bn?.takeIf { it.isNotBlank() }
            ?: title_bn?.takeIf { it.isNotBlank() }
            ?: name_en
            ?: code
    }
}

/**
 * API-প্রদত্ত batch options থেকে সবচেয়ে likely "current" batch বেছে নেয়।
 *
 * Priority (কোনো hardcoded year/label ছাড়া, শুধু API data):
 *  1. year == stored passing year (যেমন "2027")
 *  2. label == stored batch label (যেমন "New C11 Batch")
 *  3. label-এ "Running" / "New" আছে কিন্তু "Old" নেই
 *  4. প্রথম option
 */
fun pickDefaultBatch(
    options: List<BatchOptionItem>,
    storedPassingYear: String?,
    storedBatchLabel: String?
): BatchOptionItem? {
    if (options.isEmpty()) return null

    if (!storedPassingYear.isNullOrBlank()) {
        options.find { it.yearString.equals(storedPassingYear.trim(), ignoreCase = true) }
            ?.let { return it }
    }

    if (!storedBatchLabel.isNullOrBlank()) {
        options.find { it.label.equals(storedBatchLabel.trim(), ignoreCase = true) }
            ?.let { return it }
    }

    options.firstOrNull { b ->
        val label = b.label.orEmpty()
        (label.contains("Running", true) || label.contains("New", true)) &&
                !label.contains("Old", true)
    }?.let { return it }

    return options.firstOrNull()
}

/**
 * বাংলা ডিজিট (০-৯) -> ইংরেজি ডিজিট (0-9) এবং বিভিন্ন apostrophe ক্যারেক্টারকে সাধারণ `'` তে রূপান্তর করে।
 */
fun normalizeDigitsAndQuotes(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val sb = StringBuilder(raw.length)
    for (ch in raw) {
        when (ch) {
            in '০'..'৯' -> sb.append(('0'.code + (ch.code - '০'.code)).toChar())
            '’', '‘', '`', '´', '′', 'ʼ' -> sb.append('\'')
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

/**
 * কোর্সের টাইটেল থেকে পরীক্ষার সাল (যেমন: "2028", "2027", "2026", "2025") বের করে।
 * যেমন:
 *  - "HSC '28 মানবিক" -> "2028"
 *  - "লক্ষ্য জিপিএ-৫ HSC ’26 মানবিক - রিভিশন + মডেল টেস্ট" -> "2026"
 *  - "HSC '27 মানবিক - ২য় বর্ষ প্রস্তুতি" -> "2027"
 *  - "এইচএসসি ২৮" / "SSC '27" -> "2028" / "2027"
 *  - "Think AI" (সাল নেই) -> null
 */
fun extractCourseExamYear(titleBn: String?): String? {
    if (titleBn.isNullOrBlank()) return null
    val normalized = normalizeDigitsAndQuotes(titleBn)

    // 1. Explicit 4-digit year (e.g., 2025, 2026, 2027, 2028, 2029, 2030)
    val fourDigitMatch = Regex("(?<!\\d)(202[4-9]|203[0-2])(?!\\d)").find(normalized)
    if (fourDigitMatch != null) {
        return fourDigitMatch.groupValues[1]
    }

    // 2. Exam prefix + 2-digit year (e.g., HSC '28, HSC 28, এইচএসসি ২৮, SSC '27, ব্যাচ ২৮)
    val examPrefixMatch = Regex(
        "(?i)(?:HSC|SSC|এইচএসসি|এসএসসি|ব্যাচ|Batch)[\\s\\-:']*?(2[4-9]|3[0-2])(?!\\d)"
    ).find(normalized)
    if (examPrefixMatch != null) {
        return "20${examPrefixMatch.groupValues[1]}"
    }

    // 3. Apostrophe + 2-digit year anywhere in title (e.g., '26, '27, '28)
    val shortQuoteMatch = Regex("'(2[4-9]|3[0-2])(?!\\d)").find(normalized)
    if (shortQuoteMatch != null) {
        return "20${shortQuoteMatch.groupValues[1]}"
    }

    return null
}

/**
 * ইউজারের সেভ করা passingYear বা batchId থেকে ৪ সংখ্যার পরীক্ষার সাল (যেমন "2028") বের করে।
 */
fun resolveSelectedExamYear(passingYear: String?, batchId: String?): String? {
    val normPassing = normalizeDigitsAndQuotes(passingYear).trim()
    if (normPassing.matches(Regex("20[23]\\d"))) {
        return normPassing
    }
    if (normPassing.matches(Regex("[23]\\d"))) {
        return "20$normPassing"
    }
    return extractCourseExamYear(batchId)
}

/**
 * ইউজারের নির্বাচিত পরীক্ষার সাল (যেমন "2028") অনুযায়ী কোর্স ফিল্টার ও সাজিয়ে দেয়:
 *  - যেসব কোর্সে অন্য কোনো সাল (যেমন '26 বা '27) স্পষ্টভাবে লেখা আছে সেগুলো বাদ দেয়।
 *  - নির্বাচিত সালের ('28 / 2028) কোর্সগুলো সবার উপরে রাখে এবং সালবিহীন সাধারণ কোর্স (যেমন Think AI) পরে রাখে।
 */
fun <T> filterProgramsByExamYear(
    programs: List<T>,
    selectedYear: String?,
    titleSelector: (T) -> String?
): List<T> {
    val cleanYear = resolveSelectedExamYear(selectedYear, null)
    if (cleanYear.isNullOrBlank() || programs.isEmpty()) return programs

    val exactMatches = mutableListOf<T>()
    val yearNeutral = mutableListOf<T>()

    for (prog in programs) {
        val courseYear = extractCourseExamYear(titleSelector(prog))
        when {
            courseYear == cleanYear -> exactMatches.add(prog)
            courseYear == null -> yearNeutral.add(prog)
            // courseYear != cleanYear (e.g. '26 or '27 when user selected 2028) -> excluded!
        }
    }

    return exactMatches + yearNeutral
}
