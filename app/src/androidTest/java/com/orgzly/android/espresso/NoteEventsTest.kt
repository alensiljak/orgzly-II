package com.orgzly.android.espresso

import android.icu.util.Calendar
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import cc.alensiljak.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils.searchForTextCloseKeyboard
import com.orgzly.android.espresso.util.assertDeadlineCount
import com.orgzly.android.espresso.util.assertEventCount
import com.orgzly.android.espresso.util.assertScheduledCount
import com.orgzly.android.espresso.util.onNoteRow
import com.orgzly.android.espresso.util.onNoteTitle
import com.orgzly.android.espresso.util.performLongClick
import com.orgzly.android.espresso.util.waitUntilAgendaItemCount
import com.orgzly.android.espresso.util.waitUntilExactNoteCount
import com.orgzly.android.espresso.util.waitUntilNoteCount
import com.orgzly.android.ui.main.MainActivity
import com.orgzly.org.datetime.OrgDateTime
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Rule
import org.junit.Test

class NoteEventsTest : OrgzlyTest() {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val retryTestRule = RetryTestRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    private val now: String
        get() = OrgDateTime(true).toString()

    private val today: String
        get() = OrgDateTime.Builder()
            .setDateTime(System.currentTimeMillis())
            .setIsActive(true)
            .build()
            .toString()

    private val tomorrow: String
        get() = OrgDateTime.Builder()
            .setDateTime(System.currentTimeMillis() + 1000 * 60 * 60 * 24)
            .setIsActive(true)
            .build()
            .toString()

    private val inFewDays: String
        get() = OrgDateTime.Builder()
            .setDateTime(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 3)
            .setIsActive(true)
            .build()
            .toString()

    private val yesterday: String
        get() = OrgDateTime.Builder()
            .setDateTime(System.currentTimeMillis() - 1000 * 60 * 60 * 24)
            .setIsActive(true)
            .build()
            .toString()

    private val fewDaysAgo: String
        get() = OrgDateTime.Builder()
            .setDateTime(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 3)
            .setIsActive(true)
            .build()
            .toString()

    @After
    override fun tearDown() {
        super.tearDown()
        scenario.close()
    }

    @Test
    fun search_OneInTitle() {
        testUtils.setupBook("book-a", "* Note $now")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("e.ge.today")
        composeTestRule.waitUntilExactNoteCount(1)
    }

    @Test
    fun search_OneInContent() {
        testUtils.setupBook("book-a", "* Note\n$now")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("e.ge.today")
        composeTestRule.waitUntilExactNoteCount(1)
    }

    @Test
    fun search_TwoSameInContent() {
        testUtils.setupBook("book-a", "* Note\n$now $now")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("e.ge.today")
        composeTestRule.waitUntilExactNoteCount(1)
    }

    @Test
    fun agenda_OneInTitle() {
        testUtils.setupBook("book-a", "* Note $now")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.1")
        composeTestRule.waitUntilAgendaItemCount(2)
    }

    @Test
    fun agenda_TwoInTitle() {
        testUtils.setupBook("book-a", "* Note $now $tomorrow")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.2")
        composeTestRule.waitUntilAgendaItemCount(4)
    }

    @Test
    fun agenda_OneInContent() {
        testUtils.setupBook("book-a", "* Note\n$now")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.1")
        composeTestRule.waitUntilAgendaItemCount(2)
    }

    @Test
    fun agenda_TwoInContent() {
        testUtils.setupBook("book-a", "* Note\n$now $tomorrow")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.2")
        composeTestRule.waitUntilAgendaItemCount(4)
    }

    private fun time(offset: Long = 0, hasTime: Boolean = false): OrgDateTime {
        return OrgDateTime.Builder()
            .setDateTime(System.currentTimeMillis() + offset)
            .setHasTime(hasTime)
            .setIsActive(true)
            .build()
    }

    @Test
    fun agenda_MultipleWithTimes() {
        testUtils.setupBook(
            "book-a", """
            * Note
            SCHEDULED: ${time(1000 * 60 * 60 * 24 * 2)}
            DEADLINE: ${time(hasTime=true)}

            Now: ${time(hasTime = true)}
            In one hour: ${time(1000 * 60 * 60, hasTime = true)}
            Tomorrow: ${time(1000 * 60 * 60 * 24, hasTime = true)}"
        """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.5")
        composeTestRule.waitUntilAgendaItemCount(10)

        // Verify per-occurrence timestamp types via total counts:
        // 1 deadline, 3 events (now, in-1hr, tomorrow), 1 scheduled
        composeTestRule.assertDeadlineCount(1)
        composeTestRule.assertScheduledCount(1)
        // Events: "now" + "in one hour" = 2 today; "tomorrow" = 1 more.
        // Near midnight, "in one hour" may shift to next day but total stays 3.
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 23) {
            composeTestRule.assertEventCount(3)
        } else {
            composeTestRule.assertEventCount(3) // still 3 events, just shifted positions
        }
    }

    @Test
    fun search_MultipleWithTimes() {
        testUtils.setupBook(
            "book-a", """
            * Note
            SCHEDULED: ${time(1000 * 60 * 60 * 24 * 2)}
            DEADLINE: ${time()}

            Now: ${time(hasTime = true)}
            In one hour: ${time(1000 * 60 * 60, hasTime = true)}
            Tomorrow: ${time(1000 * 60 * 60 * 24, hasTime = true)}"
        """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("b.book-a")
        composeTestRule.waitUntilExactNoteCount(1)

        composeTestRule.assertScheduledCount(1)
        composeTestRule.assertDeadlineCount(1)
        // At least 1 event shown in search results (the earliest event)
        composeTestRule.assertEventCount(1)
    }

    @Test
    fun search_TodayAndInFewDays() {
        testUtils.setupBook(
            "book-a",
            "* Today $today\n* In few days $inFewDays\n* Today & In few days $today $inFewDays"
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("e.gt.1d")
        composeTestRule.waitUntilExactNoteCount(2)
    }

    @Test
    fun agenda_PastEvent() {
        testUtils.setupBook("book-a", "* Few days ago\n$fewDaysAgo")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.2")
        composeTestRule.waitUntilAgendaItemCount(2)
    }

    @Test
    fun agendaSearch_TwoWithScheduledTime() {
        testUtils.setupBook("book-a", "* $yesterday $fewDaysAgo\nSCHEDULED: $tomorrow")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("e.lt.now ad.3")
        composeTestRule.waitUntilAgendaItemCount(4)
    }

    @Test
    fun search_MultiplePerNote_Today() {
        testUtils.setupBook(
            "Book A",
            """
                * Note A-01
                  $today $tomorrow
            """.trimIndent()
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("e.today")
        composeTestRule.waitUntilExactNoteCount(1)
        composeTestRule.onNoteTitle(0).assertTextContains("Note A-01", substring = true)
    }

    @Test
    fun search_MultiplePerNote_OrderBy() {
        testUtils.setupBook(
            "Book A",
            """
                * Note A-01
                  <2000-01-10> <2000-01-15> <2000-01-20>
                * Note A-02
                  <2000-01-12>
            """.trimIndent()
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("e.lt.now o.e")
        composeTestRule.waitUntilExactNoteCount(2)
        composeTestRule.onNoteTitle(0).assertTextContains("Note A-01", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note A-02", substring = true)
    }

    @Test
    fun search_MultiplePerNote_OrderByDesc() {
        testUtils.setupBook(
            "Book A",
            """
                * Note A-01
                  <2000-01-10> <2000-01-15> <2000-01-20>
                * Note A-02
                  <2000-01-12>
            """.trimIndent()
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("e.lt.now .o.e")
        composeTestRule.waitUntilExactNoteCount(2)
        composeTestRule.onNoteTitle(0).assertTextContains("Note A-01", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note A-02", substring = true)
    }

    @Test
    fun shiftFromList() {
        testUtils.setupBook("Book A", "* Note A-01 <2000-01-10 +1d>")
        scenario = ActivityScenario.launch(MainActivity::class.java)

        onView(withText("Book A")).perform(click())
        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteTitle(0).assertTextContains("Note A-01 <2000-01-10 +1d>", substring = false)
        composeTestRule.onNoteRow(0).performLongClick()
        onView(withId(R.id.toggle_state)).perform(click())
        composeTestRule.onNoteTitle(0).assertTextContains("Note A-01 <2000-01-11 Tue +1d>", substring = false)
    }

    @Test
    fun shiftFromNote() {
        testUtils.setupBook("Book A", "* Note A-01 <2000-01-10 +1d>")
        scenario = ActivityScenario.launch(MainActivity::class.java)

        onView(withText("Book A")).perform(click())
        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteRow(0).performClick()
        onView(withId(R.id.title_view)).check(matches(withText("Note A-01 <2000-01-10 +1d>")))
        onView(withId(R.id.state_button)).perform(click())
        onView(withText("DONE")).perform(click())
        onView(withId(R.id.title_view)).check(matches(withText("Note A-01 <2000-01-11 Tue +1d>")))
    }

    @Test
    fun agenda_NoteWithScheduledTimeNotHiddenWhenUsingSortByEvent() {
        testUtils.setupBook(
            "Book A",
            """
                * Note A-01
                  SCHEDULED: $tomorrow
            """.trimIndent()
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard(".it.done ad.7 o.e")
        composeTestRule.waitUntilAgendaItemCount(8)
        // Note A-01 is at agenda position 2 (today header, tomorrow header, then the note)
        composeTestRule.onNoteTitle(0).assertTextContains("Note A-01", substring = true)
    }
}
