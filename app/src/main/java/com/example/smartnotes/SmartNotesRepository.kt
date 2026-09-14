package com.example.smartnotes

import android.util.Log
import com.example.api.*
import com.example.course.CourseRepository

class SmartNotesRepository(
    private val apiService: ShikhoApiService,
    private val courseRepository: CourseRepository = CourseRepository(apiService)
) {

    companion object {
        const val QUERY_LIST_TAGGABLE_RESOURCES = """
query ListTaggableResources(
  ${'$'}is_chapter_resource: Boolean, 
  ${'$'}is_subject_resource: Boolean, 
  ${'$'}subject_id: String, 
  ${'$'}chapter_id: String, 
  ${'$'}phase_id: String
) {
  listTaggableResourceType(
    is_chapter_resource: ${'$'}is_chapter_resource, 
    is_subject_resource: ${'$'}is_subject_resource, 
    subject_id: ${'$'}subject_id, 
    chapter_id: ${'$'}chapter_id, 
    phase_id: ${'$'}phase_id
  ) {
    data {
      id
      title
      icon_url
      is_chapter_resource
      is_subject_resource
    }
  }
}
"""

        const val QUERY_RESOURCE_ATTACHMENTS_OF_CHAPTER = """
query ResourceAttachmentsOfChapter(
  ${'$'}subject_id: String!, 
  ${'$'}module_id: String!, 
  ${'$'}phase_id: String, 
  ${'$'}module_name: AttachmentModuleEnum!, 
  ${'$'}chapter_ids: [String]!, 
  ${'$'}resource_type_tag_ids: [String], 
  ${'$'}is_subject_specific: Boolean
) {
  attachmentList(
    module_id: ${'$'}module_id, 
    subject_id: ${'$'}subject_id, 
    phase_id: ${'$'}phase_id, 
    module_name: ${'$'}module_name, 
    chapter_ids: ${'$'}chapter_ids, 
    resource_type_tag_ids: ${'$'}resource_type_tag_ids, 
    is_subject_specific: ${'$'}is_subject_specific
  ) {
    data {
      id
      description
      title
      url
    }
  }
}
"""
    }

    suspend fun listSubjectTaggableResources(
        subjectId: String,
        phaseId: String?
    ): List<TaggableResourceItem> {
        val variables = mutableMapOf<String, Any?>(
            "is_subject_resource" to true,
            "subject_id" to subjectId
        )
        if (!phaseId.isNullOrBlank()) {
            variables["phase_id"] = phaseId
        }

        val query = GraphQlQuery(
            operationName = "ListTaggableResources",
            query = QUERY_LIST_TAGGABLE_RESOURCES.trimIndent(),
            variables = variables
        )

        return try {
            val response = apiService.listTaggableResources(query)
            response.data?.listTaggableResourceType?.data ?: emptyList()
        } catch (e: Exception) {
            Log.e("SmartNotesRepo", "Error fetching subject taggable resources: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun listChapterTaggableResources(
        chapterId: String,
        phaseId: String?
    ): List<TaggableResourceItem> {
        val variables = mutableMapOf<String, Any?>(
            "is_chapter_resource" to true,
            "chapter_id" to chapterId
        )
        if (!phaseId.isNullOrBlank()) {
            variables["phase_id"] = phaseId
        }

        val query = GraphQlQuery(
            operationName = "ListTaggableResources",
            query = QUERY_LIST_TAGGABLE_RESOURCES.trimIndent(),
            variables = variables
        )

        return try {
            val response = apiService.listTaggableResources(query)
            response.data?.listTaggableResourceType?.data ?: emptyList()
        } catch (e: Exception) {
            Log.e("SmartNotesRepo", "Error fetching chapter taggable resources: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getResourceAttachmentsOfChapter(
        subjectId: String,
        moduleId: String,
        phaseId: String?,
        chapterIds: List<String>,
        resourceTypeTagIds: List<String>?,
        isSubjectSpecific: Boolean
    ): List<AttachmentDataItem> {
        val variables = mutableMapOf<String, Any?>(
            "subject_id" to subjectId,
            "module_id" to moduleId,
            "module_name" to "AcademicProgram",
            "chapter_ids" to chapterIds,
            "resource_type_tag_ids" to (resourceTypeTagIds ?: emptyList<String>()),
            "is_subject_specific" to isSubjectSpecific
        )
        if (!phaseId.isNullOrBlank()) {
            variables["phase_id"] = phaseId
        }

        val query = GraphQlQuery(
            operationName = "ResourceAttachmentsOfChapter",
            query = QUERY_RESOURCE_ATTACHMENTS_OF_CHAPTER.trimIndent(),
            variables = variables
        )

        return try {
            val response = apiService.getResourceAttachmentsOfChapter(query)
            response.data?.attachmentList?.data ?: emptyList()
        } catch (e: Exception) {
            Log.e("SmartNotesRepo", "Error fetching resource attachments: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getSubjectChapters(
        programId: String,
        phaseId: String?,
        subjectCode: String
    ): List<AcademicChapterItem> {
        return try {
            if (!phaseId.isNullOrBlank() && programId.isNotBlank()) {
                val list = courseRepository.getPhaseWiseChapters(programId, phaseId, subjectCode)
                if (list.isNotEmpty()) return list
            }
            if (programId.isNotBlank()) {
                val list = courseRepository.getAcademicProgramChaptersFallback(programId, subjectCode)
                if (list.isNotEmpty()) return list
            }
            courseRepository.getChaptersBySubjectCode(subjectCode)
        } catch (e: Exception) {
            Log.e("SmartNotesRepo", "Error fetching subject chapters: ${e.message}", e)
            emptyList()
        }
    }
}
