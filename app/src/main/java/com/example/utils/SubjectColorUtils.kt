package com.example.utils

import androidx.compose.ui.graphics.Color

data class SubjectColorScheme(
    val backgroundColor: Color,
    val textColor: Color
)

object SubjectColorUtils {

    private val presetColors = listOf(
        SubjectColorScheme(Color(0xFFFFEBEB), Color(0xFFDC2626)), // Red / Coral
        SubjectColorScheme(Color(0xFFE0F2FE), Color(0xFF0284C7)), // Blue / Sky
        SubjectColorScheme(Color(0xFFF3E8FF), Color(0xFF7E22CE)), // Purple
        SubjectColorScheme(Color(0xFFE0E7FF), Color(0xFF4338CA)), // Indigo
        SubjectColorScheme(Color(0xFFFEF3C7), Color(0xFFB45309)), // Amber
        SubjectColorScheme(Color(0xFFDCFCE7), Color(0xFF15803D)), // Green
        SubjectColorScheme(Color(0xFFCFFAFE), Color(0xFF0E7490)), // Cyan
        SubjectColorScheme(Color(0xFFFFE4E6), Color(0xFFBE123C)), // Rose
        SubjectColorScheme(Color(0xFFCCFBF1), Color(0xFF0F766E)), // Teal
        SubjectColorScheme(Color(0xFFFFEDD5), Color(0xFFC2410C)), // Orange
        SubjectColorScheme(Color(0xFFEDE9FE), Color(0xFF6D28D9)), // Violet
        SubjectColorScheme(Color(0xFFFEF9C3), Color(0xFF854D0E))  // Yellow/Bronze
    )

    fun getColorScheme(subjectName: String?): SubjectColorScheme {
        if (subjectName.isNullOrBlank()) {
            return presetColors[0]
        }
        val name = subjectName.trim().lowercase()
        return when {
            name.contains("পৌরনীতি") || name.contains("civics") -> SubjectColorScheme(Color(0xFFFFE4E6), Color(0xFFBE123C))
            name.contains("ভূগোল") || name.contains("geography") -> SubjectColorScheme(Color(0xFFFFEDD5), Color(0xFFC2410C))
            name.contains("বাংলা") || name.contains("bangla") -> SubjectColorScheme(Color(0xFFFFEBEB), Color(0xFFDC2626))
            name.contains("ইংরেজি") || name.contains("english") -> SubjectColorScheme(Color(0xFFF3E8FF), Color(0xFF7E22CE))
            name.contains("পদার্থ") || name.contains("physics") -> SubjectColorScheme(Color(0xFFE0F2FE), Color(0xFF0284C7))
            name.contains("রসায়ন") || name.contains("chemistry") -> SubjectColorScheme(Color(0xFFFEF3C7), Color(0xFFB45309))
            name.contains("জীব") || name.contains("biology") -> SubjectColorScheme(Color(0xFFDCFCE7), Color(0xFF15803D))
            name.contains("গণিত") || name.contains("math") -> SubjectColorScheme(Color(0xFFE0E7FF), Color(0xFF4338CA))
            name.contains("তথ্য") || name.contains("ict") -> SubjectColorScheme(Color(0xFFCFFAFE), Color(0xFF0E7490))
            name.contains("অর্থনীতি") || name.contains("economics") -> SubjectColorScheme(Color(0xFFCCFBF1), Color(0xFF0F766E))
            name.contains("হিসাব") || name.contains("accounting") -> SubjectColorScheme(Color(0xFFEDE9FE), Color(0xFF6D28D9))
            else -> {
                val index = Math.abs(subjectName.hashCode()) % presetColors.size
                presetColors[index]
            }
        }
    }
}
