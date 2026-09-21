package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.auth.SessionManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MaxBird", appName)
  }

  @Test
  fun `session manager initialization works reliably`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sessionManager = SessionManager(context)
    assertNotNull(sessionManager.getDeviceId())
  }

  @Test
  fun `lesson filtering strictly isolates selected chapter lessons`() {
    val ch1Lessons = listOf(
      com.example.api.StudentLessonItem(
        id = "l1",
        title = "ভেক্টর পরিচিতি - লেকচার ০১",
        chapter_id = "chap_vector",
        live_class = com.example.api.LiveClassDetails(
          id = "lc1",
          chapter_id = "chap_vector",
          chapter_name = "ভেক্টর"
        )
      ),
      com.example.api.StudentLessonItem(
        id = "l2",
        title = "ভেক্টর গুণন - লেকচার ০২",
        chapter_id = "chap_vector",
        live_class = com.example.api.LiveClassDetails(
          id = "lc2",
          chapter_id = "chap_vector",
          chapter_name = "ভেক্টর"
        )
      )
    )

    val ch2Lessons = listOf(
      com.example.api.StudentLessonItem(
        id = "l3",
        title = "গতিবিদ্যা - লেকচার ০১",
        chapter_id = "chap_motion",
        live_class = com.example.api.LiveClassDetails(
          id = "lc3",
          chapter_id = "chap_motion",
          chapter_name = "গতিবিদ্যা"
        )
      )
    )

    val allLessons = ch1Lessons + ch2Lessons

    val filteredForVector = com.example.course.LessonCacheManager.filterLessons(
      lessons = allLessons,
      candidateChapterIds = listOf("chap_vector"),
      chapterName = "ভেক্টর",
      otherChapterIds = listOf("chap_motion"),
      otherChapterNames = listOf("গতিবিদ্যা")
    )

    assertEquals(2, filteredForVector.size)
    assertEquals("l1", filteredForVector[0].id)
    assertEquals("l2", filteredForVector[1].id)

    val filteredForMotion = com.example.course.LessonCacheManager.filterLessons(
      lessons = allLessons,
      candidateChapterIds = listOf("chap_motion"),
      chapterName = "গতিবিদ্যা",
      otherChapterIds = listOf("chap_vector"),
      otherChapterNames = listOf("ভেক্টর")
    )

    assertEquals(1, filteredForMotion.size)
    assertEquals("l3", filteredForMotion[0].id)
  }
}
