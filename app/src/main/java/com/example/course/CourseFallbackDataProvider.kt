package com.example.course

import com.example.api.EnrollmentDetails
import com.example.api.EnrolledProgram
import com.example.api.OtherProgram

/**
 * Fallback programs generator for Course exploration when offline or network empty.
 */
object CourseFallbackDataProvider {

    fun getFallbackEnrolledPrograms(className: String, group: String): List<EnrolledProgram> {
        val displayClass = when (className.uppercase()) {
            "C9", "CLASS 9", "C09" -> "নবম শ্রেণি"
            "C10", "CLASS 10" -> "দশম শ্রেণি"
            "C11", "CLASS 11" -> "একাদশ শ্রেণি"
            "C12", "CLASS 12" -> "দ্বাদশ শ্রেণি"
            else -> "এইচএসসি"
        }

        val examYear = when (className.uppercase()) {
            "C9", "C09" -> "SSC '28"
            "C10" -> "SSC '27"
            "C11" -> "HSC '27"
            "C12" -> "HSC '26"
            else -> "HSC"
        }

        val displayGroup = when (group.lowercase()) {
            "humanities", "humanities_group" -> "মানবিক"
            "science", "science_group" -> "বিজ্ঞান"
            "business", "business_studies", "commerce", "businessstudies" -> "ব্যবসায় শিক্ষা"
            else -> "মানবিক"
        }

        return listOf(
            EnrolledProgram(
                id = "fallback-ep-1-${className}-${group}",
                classes = listOf(className),
                title_bn = "$displayClass - $examYear ($displayGroup) বার্ষিক পরীক্ষা প্রস্তুতি",
                banner_url = "https://res.cloudinary.com/cross-border-education-technologies-pte-ltd/image/upload/v1751437593/hftof9ha6mequpiqpkbh.jpg",
                color = null,
                is_free = false,
                trial_enabled = true,
                enrollment_details = EnrollmentDetails(batch_id = "fallback-batch-1", is_active = true, trial_end_date = null, type = "Paid", expiry_date = "2026-12-31T17:59:59Z"),
                subjects = emptyList()
            ),
            EnrolledProgram(
                id = "fallback-ep-2-${className}-${group}",
                classes = listOf(className),
                title_bn = "দুরন্ত $displayClass - $displayGroup",
                banner_url = "https://res.cloudinary.com/cross-border-education-technologies-pte-ltd/image/upload/v1751274615/mqllgmiyqdg3mf7xyxva.jpg",
                color = null,
                is_free = false,
                trial_enabled = false,
                enrollment_details = EnrollmentDetails(batch_id = "fallback-batch-2", is_active = true, trial_end_date = null, type = "Paid", expiry_date = "2025-09-15T17:59:59Z"),
                subjects = emptyList()
            ),
            EnrolledProgram(
                id = "fallback-ep-3-${className}-${group}",
                classes = listOf(className),
                title_bn = "Think AI",
                banner_url = "https://res.cloudinary.com/cross-border-education-technologies-pte-ltd/image/upload/v1777438148/mjpjbimt6wkkwsmtrh8l.jpg",
                color = null,
                is_free = false,
                trial_enabled = false,
                enrollment_details = EnrollmentDetails(batch_id = "fallback-batch-3", is_active = true, trial_end_date = null, type = "Paid", expiry_date = "2029-04-30T17:59:59Z"),
                subjects = emptyList()
            )
        )
    }

    fun getFallbackFreePrograms(className: String, group: String): List<OtherProgram> {
        val displayClass = when (className.uppercase()) {
            "C9", "CLASS 9", "C09" -> "নবম শ্রেণি"
            "C10", "CLASS 10" -> "দশম শ্রেণি"
            "C11", "CLASS 11" -> "একাদশ শ্রেণি"
            "C12", "CLASS 12" -> "দ্বাদশ শ্রেণি"
            else -> "এইচএসসি"
        }
        return listOf(
            OtherProgram(
                id = "fallback-free-${className}-${group}",
                classes = listOf(className),
                title_bn = "$displayClass বেসিক কনসেপ্ট ও ফ্রি রিভিশন ক্লাস",
                banner_url = null,
                is_free = true,
                trial_enabled = true
            )
        )
    }

    fun getFallbackOtherPrograms(className: String, group: String): List<OtherProgram> {
        val displayClass = when (className.uppercase()) {
            "C9", "CLASS 9", "C09" -> "দশম শ্রেণি"
            "C10", "CLASS 10" -> "একাদশ শ্রেণি"
            "C11", "CLASS 11" -> "দ্বাদশ শ্রেণি"
            "C12", "CLASS 12" -> "এইচএসসি এডমিশন"
            else -> "এইচএসসি"
        }
        val displayGroup = when (group.lowercase()) {
            "humanities", "humanities_group" -> "মানবিক"
            "science", "science_group" -> "বিজ্ঞান"
            "business", "business_studies", "commerce", "businessstudies" -> "ব্যবসায় শিক্ষা"
            else -> "বিজ্ঞান"
        }
        return listOf(
            OtherProgram(
                id = "fallback-other-${className}-${group}",
                classes = listOf(className),
                title_bn = "$displayClass পরীক্ষার প্রস্তুতি ($displayGroup)",
                banner_url = null,
                is_free = false,
                trial_enabled = false
            )
        )
    }
}
