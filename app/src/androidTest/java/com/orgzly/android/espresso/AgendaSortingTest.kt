package com.orgzly.android.espresso

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ActivityScenario
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils.searchForTextCloseKeyboard
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.main.MainActivity
import org.joda.time.DateTime
import org.junit.After
import org.junit.Rule
import org.junit.Test

class AgendaSortingTest : OrgzlyTest() {
    @get:Rule
    val retryTestRule = RetryTestRule()

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    private fun getToday(): String {
        val start = DateTime.now().withTimeAtStartOfDay()
        return start.toString("yyyy-MM-dd EEE")
    }

    private fun getYesterday(): String {
        val start = DateTime.now().minusDays(1).withTimeAtStartOfDay()
        return start.toString("yyyy-MM-dd EEE")
    }

    private fun getTwoDaysAgo(): String {
        val start = DateTime.now().minusDays(2).withTimeAtStartOfDay()
        return start.toString("yyyy-MM-dd EEE")
    }

    @After
    override fun tearDown() {
        super.tearDown()
        scenario.close()
    }

    @Test
    fun testScheduledTimesSorting() {
        testUtils.setupBook(
            "book-one",
            """
            * Note A
            SCHEDULED: <${getToday()} 09:00>

            * Note B
            SCHEDULED: <${getToday()} 14:30>

            * Note C
            SCHEDULED: <${getToday()} 11:15>

            * Note D
            SCHEDULED: <${getToday()} 14:15>
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.1")

        // note_title[0..3] are the 4 note rows beneath the day header (headers have no note_title tag)
        composeTestRule.onAllNodesWithTag("note_title")[0].assertTextContains("Note A", substring = true)
        composeTestRule.onAllNodesWithTag("note_title")[1].assertTextContains("Note C", substring = true)
        composeTestRule.onAllNodesWithTag("note_title")[2].assertTextContains("Note D", substring = true)
        composeTestRule.onAllNodesWithTag("note_title")[3].assertTextContains("Note B", substring = true)
    }

    @Test
    fun testMidnightTaskSorting() {
        testUtils.setupBook(
            "book-one",
            """
            * Note A
            SCHEDULED: <${getToday()}>

            * Note B
            SCHEDULED: <${getToday()} 00:00>
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.1")

        composeTestRule.onAllNodesWithTag("note_title")[0].assertTextContains("Note B", substring = true)
        composeTestRule.onAllNodesWithTag("note_title")[1].assertTextContains("Note A", substring = true)
    }

    @Test
    fun testScheduledTimesWithMixedTimeFormats() {
        testUtils.setupBook(
            "book-one",
            """
            * Note A
            SCHEDULED: <${getToday()}>

            * Note B
            SCHEDULED: <${getToday()} 14:30>

            * Note C
            SCHEDULED: <${getToday()} 09:00>
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.1")

        // 1 day header + 3 notes = 4 agenda_item boxes
        composeTestRule.onAllNodesWithTag("agenda_item").assertCountEquals(4)

        composeTestRule.onAllNodesWithTag("note_title")[0].assertTextContains("Note C", substring = true)
        composeTestRule.onAllNodesWithTag("note_title")[1].assertTextContains("Note B", substring = true)
        composeTestRule.onAllNodesWithTag("note_title")[2].assertTextContains("Note A", substring = true)
    }

    @Test
    fun testDifferentTimeTypesSortingForSameNote() {
        testUtils.setupBook(
            "book-one",
            """
            * Note A
            SCHEDULED: <${getToday()} 09:00>
            DEADLINE: <${getToday()} 14:30>
            <${getToday()} 11:15>
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.1")

        // 1 day header + 3 agenda entries for Note A = 4 agenda_item boxes
        composeTestRule.onAllNodesWithTag("agenda_item").assertCountEquals(4)

        // All three entries are Note A, sorted by their triggering time (09:00, 11:15, 14:30)
        composeTestRule.onAllNodesWithTag("note_title")[0].assertTextContains("Note A", substring = true)
        composeTestRule.onAllNodesWithTag("note_title")[1].assertTextContains("Note A", substring = true)
        composeTestRule.onAllNodesWithTag("note_title")[2].assertTextContains("Note A", substring = true)

        // All time types are displayed (Compose shows all time fields per row, not just the triggering one)
        composeTestRule.onAllNodesWithTag("note_scheduled")[0].assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("note_event")[0].assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("note_deadline")[0].assertIsDisplayed()
    }

    @Test
    fun testDifferentTimeTypesSortingForMultipleNotes() {
        testUtils.setupBook(
            "book-one",
            """
            * Note A
            SCHEDULED: <${getToday()} 09:00>

            * Note B
            DEADLINE: <${getToday()} 08:30>

            * Note C
            <${getToday()} 10:15>

            * Note D
            SCHEDULED: <${getToday()} 08:45>
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.1")

        // 1 day header + 4 notes = 5 agenda_item boxes
        composeTestRule.onAllNodesWithTag("agenda_item").assertCountEquals(5)

        composeTestRule.onAllNodesWithTag("note_title")[0].assertTextContains("Note B", substring = true)
        composeTestRule.onAllNodesWithTag("note_deadline")[0].assertIsDisplayed()

        composeTestRule.onAllNodesWithTag("note_title")[1].assertTextContains("Note D", substring = true)
        composeTestRule.onAllNodesWithTag("note_scheduled")[0].assertIsDisplayed()

        composeTestRule.onAllNodesWithTag("note_title")[2].assertTextContains("Note A", substring = true)
        composeTestRule.onAllNodesWithTag("note_scheduled")[1].assertIsDisplayed()

        composeTestRule.onAllNodesWithTag("note_title")[3].assertTextContains("Note C", substring = true)
        composeTestRule.onAllNodesWithTag("note_event")[0].assertIsDisplayed()
    }

    @Test
    fun testRecurringTasksSortingLegacyGrouping() {
        AppPreferences.groupScheduledWithTodayInAgenda(context, false)
        testUtils.setupBook(
            "book-one",
            """
            * Note A
            SCHEDULED: <${getToday()} 10:00>

            * Note B
            SCHEDULED: <${getYesterday()} 14:30 +1d>

            * Note C
            SCHEDULED: <${getTwoDaysAgo()} 09:00 +1d>

            * Note D
            SCHEDULED: <${getToday()} 08:45>
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.1")

        // 1 overdue header + 2 overdue notes + 1 today header + 4 today notes = 8 agenda_item boxes
        composeTestRule.onAllNodesWithTag("agenda_item").assertCountEquals(8)

        // Today section: note_title[2..5] (indices 0 and 1 are the two overdue note rows)
        composeTestRule.onAllNodesWithTag("note_title")[2].assertTextContains("Note D", substring = true)
        composeTestRule.onAllNodesWithTag("note_scheduled")[2].assertIsDisplayed()

        composeTestRule.onAllNodesWithTag("note_title")[3].assertTextContains("Note C", substring = true)
        composeTestRule.onAllNodesWithTag("note_scheduled")[3].assertIsDisplayed()

        composeTestRule.onAllNodesWithTag("note_title")[4].assertTextContains("Note A", substring = true)
        composeTestRule.onAllNodesWithTag("note_scheduled")[4].assertIsDisplayed()

        composeTestRule.onAllNodesWithTag("note_title")[5].assertTextContains("Note B", substring = true)
        composeTestRule.onAllNodesWithTag("note_scheduled")[5].assertIsDisplayed()
    }

    @Test
    fun testRecurringTasksSorting() {
        testUtils.setupBook(
            "book-one",
            """
            * Note A
            SCHEDULED: <${getToday()} 10:00>

            * Note B
            SCHEDULED: <${getYesterday()} 14:30 +1d>

            * Note C
            SCHEDULED: <${getTwoDaysAgo()} 09:00 +1d>

            * Note D
            SCHEDULED: <${getToday()} 08:45>
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.1")

        // 1 day header + 4 notes = 5 agenda_item boxes
        composeTestRule.onAllNodesWithTag("agenda_item").assertCountEquals(5)

        composeTestRule.onAllNodesWithTag("note_title")[0].assertTextContains("Note D", substring = true)
        composeTestRule.onAllNodesWithTag("note_scheduled")[0].assertIsDisplayed()

        composeTestRule.onAllNodesWithTag("note_title")[1].assertTextContains("Note C", substring = true)
        composeTestRule.onAllNodesWithTag("note_scheduled")[1].assertIsDisplayed()

        composeTestRule.onAllNodesWithTag("note_title")[2].assertTextContains("Note A", substring = true)
        composeTestRule.onAllNodesWithTag("note_scheduled")[2].assertIsDisplayed()

        composeTestRule.onAllNodesWithTag("note_title")[3].assertTextContains("Note B", substring = true)
        composeTestRule.onAllNodesWithTag("note_scheduled")[3].assertIsDisplayed()
    }

    @Test
    fun testOverdueTasksSorting() {
        testUtils.setupBook(
            "book-one",
            """
            * Note A
            DEADLINE: <${getYesterday()} 10:00>

            * Note B
            DEADLINE: <${getYesterday()} 14:30>

            * Note C
            DEADLINE: <${getTwoDaysAgo()} 15:00>

            * Note D
            DEADLINE: <${getYesterday()}>
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.1")

        // 1 overdue header + 4 notes + 1 today header = 6 agenda_item boxes
        composeTestRule.onAllNodesWithTag("agenda_item").assertCountEquals(6)

        composeTestRule.onAllNodesWithTag("note_title")[0].assertTextContains("Note C", substring = true)
        composeTestRule.onAllNodesWithTag("note_title")[1].assertTextContains("Note A", substring = true)
        composeTestRule.onAllNodesWithTag("note_title")[2].assertTextContains("Note B", substring = true)
        composeTestRule.onAllNodesWithTag("note_title")[3].assertTextContains("Note D", substring = true)
    }

    @Test
    fun testAgendaSortingByDefaultOrderWhenTimeIsMissing() {
        testUtils.setupBook(
            "book-a",
            """
           * [#C] Note A
           SCHEDULED: <${getToday()}>

           * [#B] Note B
           SCHEDULED: <${getToday()}>

           * [#A] Note C
           SCHEDULED: <${getToday()}>

           * [#C] Note D
           SCHEDULED: <${getToday()} 16:00>
           """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.1")

        // 1 today header + 4 notes = 5 agenda_item boxes
        composeTestRule.onAllNodesWithTag("agenda_item").assertCountEquals(5)

        composeTestRule.onAllNodesWithTag("note_title")[0].assertTextContains("Note D", substring = true)
        composeTestRule.onAllNodesWithTag("note_title")[1].assertTextContains("Note C", substring = true)
        composeTestRule.onAllNodesWithTag("note_title")[2].assertTextContains("Note B", substring = true)
        composeTestRule.onAllNodesWithTag("note_title")[3].assertTextContains("Note A", substring = true)
    }
}
