package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "saved_items")
data class SavedItemEntity(
    @PrimaryKey
    val id: String,
    val type: String, // e.g., "QUESTION", "NOTE", "CLASS"
    val title: String,
    val subtitle: String? = null,
    val contentJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_QUESTION = "QUESTION"
        const val TYPE_NOTE = "NOTE"
        const val TYPE_CLASS = "CLASS"

        /**
         * Helper to serialize question details into contentJson
         */
        fun buildQuestionContentJson(
            options: List<Pair<String, String>>, // (no, description)
            correctOption: String?,
            userGivenAns: String?,
            explanation: String?,
            chapterName: String?
        ): String {
            val json = JSONObject()
            val optsArray = JSONArray()
            options.forEach { (no, desc) ->
                val optObj = JSONObject().apply {
                    put("no", no)
                    put("description", desc)
                }
                optsArray.put(optObj)
            }
            json.put("options", optsArray)
            json.put("correct_option", correctOption ?: "")
            json.put("user_given_ans", userGivenAns ?: "")
            json.put("explanation", explanation ?: "")
            json.put("chapter_name", chapterName ?: "")
            return json.toString()
        }

        /**
         * Helper to parse question details from contentJson
         */
        fun parseQuestionContent(jsonString: String?): ParsedQuestionContent {
            if (jsonString.isNullOrBlank()) return ParsedQuestionContent()
            return try {
                val json = JSONObject(jsonString)
                val optsArray = json.optJSONArray("options")
                val optionsList = mutableListOf<ParsedOption>()
                if (optsArray != null) {
                    for (i in 0 until optsArray.length()) {
                        val optObj = optsArray.optJSONObject(i)
                        if (optObj != null) {
                            optionsList.add(
                                ParsedOption(
                                    no = optObj.optString("no"),
                                    description = optObj.optString("description")
                                )
                            )
                        }
                    }
                }
                ParsedQuestionContent(
                    options = optionsList,
                    correctOption = json.optString("correct_option").ifBlank { null },
                    userGivenAns = json.optString("user_given_ans").ifBlank { null },
                    explanation = json.optString("explanation").ifBlank { null },
                    chapterName = json.optString("chapter_name").ifBlank { null }
                )
            } catch (e: Exception) {
                ParsedQuestionContent()
            }
        }
    }
}

data class ParsedQuestionContent(
    val options: List<ParsedOption> = emptyList(),
    val correctOption: String? = null,
    val userGivenAns: String? = null,
    val explanation: String? = null,
    val chapterName: String? = null
)

data class ParsedOption(
    val no: String,
    val description: String
)
