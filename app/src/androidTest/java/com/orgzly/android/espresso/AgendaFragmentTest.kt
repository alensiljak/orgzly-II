package com.orgzly.android.espresso

import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.widget.DatePicker
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import cc.alensiljak.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils
import com.orgzly.android.espresso.util.EspressoUtils.searchForTextCloseKeyboard
import com.orgzly.android.espresso.util.onAgendaItem
import com.orgzly.android.espresso.util.onNoteTitle
import com.orgzly.android.espresso.util.performClick
import com.orgzly.android.espresso.util.performLongClick
import com.orgzly.android.espresso.util.waitUntilAgendaItemCount
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.joda.time.DateTime
import org.junit.After
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class AgendaFragmentTest : OrgzlyTest() {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val mRetryTestRule = RetryTestRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    private fun defaultSetUp(): ActivityScenario<MainActivity> {
        testUtils.setupBook(
            "book-one",
            "First book used for testing\n" +
                    "* Note A.\n" +
                    "* TODO Note B\n" +
                    "SCHEDULED: <2014-01-01>\n" +
                    "*** TODO Note C\n" +
                    "SCHEDULED: <2014-01-02 ++1d>\n"
        )

        testUtils.setupBook(
            "book-two",
            "Sample book used for tests\n" +
                    "*** DONE Note 1\n" +
                    "CLOSED: [2014-01-03 Tue 13:34]\n" +
                    "**** Note 2\n" +
                    "SCHEDULED: <2014-01-04 Sat>--<2044-01-10 Fri>\n"
        )

        return ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        scenario?.close()
    }

    private fun waitForActionMode(actionButtonId: Int) {
        onView(isRoot()).perform(EspressoUtils.waitId(actionButtonId, 5000))
    }

    @Test
    fun testAgendaSavedSearch() {
        testUtils.setupBook(
            "book-three",
            "Sample book used for tests\n" +
                    "**** Note 5\n" +
                    "DEADLINE: <2014-01-04 Sat>\n"
        )
        scenario = defaultSetUp()
        searchForTextCloseKeyboard(".it.done ad.7")
        /*
         * 1 Overdue
         * 1 Note 5
         * 7 Day
         * 1 Note B
         * 7 Note C
         * 7 Note 2
         */
        composeTestRule.waitUntilAgendaItemCount(24)
    }

    @Test
    fun testWithNoBook() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard(".it.done (s.7d or d.7d) ad.7")
        composeTestRule.waitUntilAgendaItemCount(7)
        searchForTextCloseKeyboard(".it.done (s.7d or d.7d) ad.3")
        composeTestRule.waitUntilAgendaItemCount(3)
    }

    @Test
    fun testDayAgenda() {
        testUtils.setupBook(
            "book-three",
            "Sample book used for tests\n" +
                    "**** Note 5\n" +
                    "DEADLINE: <2014-01-04 Sat>\n"
        )
        scenario = defaultSetUp()
        searchForTextCloseKeyboard(".it.done (s.7d or d.7d) ad.1")
        composeTestRule.waitUntilAgendaItemCount(6)

        // agenda_item(0) = Overdue header
        composeTestRule.onAgendaItem(0).assertTextContains(context.getString(R.string.overdue), substring = true)
        // Note titles: 0=Note 5, 1=Note C, 2=Note 2, 3=Note B
        composeTestRule.onNoteTitle(0).assertTextContains("Note 5", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note C", substring = true)
        composeTestRule.onNoteTitle(2).assertTextContains("Note 2", substring = true)
        composeTestRule.onNoteTitle(3).assertTextContains("Note B", substring = true)
    }

    @Test
    fun testDayAgendaLegacyGrouping() {
        scenario = defaultSetUp()
        AppPreferences.groupScheduledWithTodayInAgenda(context, false)
        searchForTextCloseKeyboard(".it.done (s.7d or d.7d) ad.1")
        composeTestRule.waitUntilAgendaItemCount(7)

        // agenda_item(0) = Overdue header; note titles are ordered differently
        composeTestRule.onAgendaItem(0).assertTextContains(context.getString(R.string.overdue), substring = true)
        composeTestRule.onNoteTitle(0).assertTextContains("Note B", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note C", substring = true)
        composeTestRule.onNoteTitle(2).assertTextContains("Note 2", substring = true)
        // Day 1 header at agenda_item(4); then day-1 occurrences
        composeTestRule.onNoteTitle(3).assertTextContains("Note C", substring = true)
        composeTestRule.onNoteTitle(4).assertTextContains("Note 2", substring = true)
    }

    @Test
    fun testAgendaRepeatingNote() {
        testUtils.setupBook(
            "book-three",
            "Sample book used for tests\n" +
                    "**** Repeating note 1\n" +
                    "SCHEDULED: <2014-01-01 Sat .+1d>\n"
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.1")
        composeTestRule.waitUntilAgendaItemCount(2)
        composeTestRule.onNoteTitle(0).assertTextContains("Repeating note 1", substring = true)
    }

    @Test
    fun testAgendaRangeEvent() {
        val start = DateTime.now().withTimeAtStartOfDay()
        val end = DateTime.now().withTimeAtStartOfDay().plusDays(4)
        testUtils.setupBook(
            "book",
            "Book for testing\n* Event A.\n<${start}>--<${end}>\n"
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.5")
        composeTestRule.waitUntilAgendaItemCount(10)
    }

    @Test
    fun testOneTimeTaskMarkedDone() {
        scenario = defaultSetUp()
        searchForTextCloseKeyboard(".it.done ad.7")
        /*
         * Today
         * 1 Note C
         * 1 Note 2
         * 1 Note B <- Mark as done
         */
        composeTestRule.waitUntilAgendaItemCount(22)
        composeTestRule.onAgendaItem(3).performLongClick()
        waitForActionMode(R.id.toggle_state)
        onView(withId(R.id.toggle_state)).perform(click())
        composeTestRule.waitUntilAgendaItemCount(21)
    }

    @Test
    fun testRepeaterTaskMarkedDone() {
        scenario = defaultSetUp()
        searchForTextCloseKeyboard(".it.done ad.7")
        /*
         * Today
         * 1 Note C
         * 1 Note 2 <- Mark as done
         * 1 Note B
         */
        composeTestRule.waitUntilAgendaItemCount(22)
        composeTestRule.onAgendaItem(2).performLongClick()
        waitForActionMode(R.id.toggle_state)
        onView(withId(R.id.toggle_state)).perform(click())
        composeTestRule.waitUntilAgendaItemCount(15)
    }

    @Test
    fun testRangeTaskMarkedDone() {
        scenario = defaultSetUp()
        searchForTextCloseKeyboard(".it.done ad.7")
        /*
         * Today
         * 1 Note C
         * 1 Note 2 <- Mark as done
         * 1 Note B
         */
        composeTestRule.waitUntilAgendaItemCount(22)
        composeTestRule.onAgendaItem(2).performLongClick()
        waitForActionMode(R.id.toggle_state)
        onView(withId(R.id.toggle_state)).perform(click())
        composeTestRule.waitUntilAgendaItemCount(15)
    }

    @Test
    fun testMoveTaskWithRepeaterToTomorrow() {
        EspressoUtils.grantAlarmsAndRemindersSpecialPermission()
        val tomorrow = DateTime.now().withTimeAtStartOfDay().plusDays(1)
        scenario = defaultSetUp()
        searchForTextCloseKeyboard(".it.done ad.7")
        /*
         * Today
         * 1 Note C <- Move to tomorrow
         * 1 Note 2
         * 1 Note B
         */
        composeTestRule.waitUntilAgendaItemCount(22)
        composeTestRule.onAgendaItem(1).performLongClick()
        waitForActionMode(R.id.schedule)
        onView(withId(R.id.schedule)).perform(click())
        onView(withId(R.id.date_picker_button)).perform(click())
        onView(withClassName(equalTo(DatePicker::class.java.name)))
            .perform(androidx.test.espresso.contrib.PickerActions.setDate(
                tomorrow.year, tomorrow.monthOfYear, tomorrow.dayOfMonth))
        onView(withText(android.R.string.ok)).perform(click())
        onView(withText(R.string.set)).perform(click())
        composeTestRule.waitUntilAgendaItemCount(21)
    }

    @Test
    fun testPersistedSpinnerSelection() {
        scenario = defaultSetUp()
        searchForTextCloseKeyboard(".it.done ad.7")
        composeTestRule.waitUntilAgendaItemCount(22)

        SystemClock.sleep(500)
        scenario!!.onActivity { activity ->
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        }

        composeTestRule.waitUntilAgendaItemCount(22)
    }

    @Test
    fun testDeselectRemovedNoteInAgenda() {
        testUtils.setupBook(
            "notebook",
            "* TODO Note A\nSCHEDULED: <2018-01-01 +1d>\n" +
                    "* TODO Note B\nSCHEDULED: <2018-01-01 .+1d>\n"
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("i.todo ad.3")
        composeTestRule.waitUntilAgendaItemCount(9)

        composeTestRule.onAgendaItem(1).performLongClick()
        waitForActionMode(R.id.state)

        // Check title for number of selected notes
        onView(allOf(instanceOf(android.widget.TextView::class.java), withParent(withId(R.id.top_toolbar))))
            .check(matches(withText("1")))

        // Remove state
        onView(withId(R.id.state)).perform(click())
        onView(withText(R.string.clear)).perform(click())

        composeTestRule.waitUntilAgendaItemCount(6)

        // Check subtitle for search query
        onView(allOf(
            instanceOf(android.widget.TextView::class.java),
            not(withText(R.string.agenda)),
            withParent(withId(R.id.top_toolbar))
        )).check(matches(withText("i.todo ad.3")))
    }

    @Ignore("Not implemented yet")
    @Test
    fun testPreselectedStateOfSelectedNote() {
        testUtils.setupBook("notebook", "* TODO Note A\nSCHEDULED: <2018-01-01 +1d>")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.3")
        composeTestRule.waitUntilAgendaItemCount(1)
        composeTestRule.onAgendaItem(1).performLongClick()
        waitForActionMode(R.id.state)
        onView(withId(R.id.state)).perform(click())
        onView(withText("TODO")).check(matches(isDisplayed()))
    }

    @Test
    fun testSwipeDivider() {
        testUtils.setupBook("notebook", "* TODO Note A\nSCHEDULED: <2018-01-01 +1d>")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.3")
        composeTestRule.waitUntilAgendaItemCount(1)
        composeTestRule.onAgendaItem(0).performTouchInput { swipeLeft() }
        composeTestRule.onAgendaItem(2).performTouchInput { swipeLeft() }
    }

    @Test
    fun testOpenCorrectNote() {
        testUtils.setupBook("notebook", "* TODO Note A\nSCHEDULED: <2018-01-01 +1d>")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.3")
        composeTestRule.waitUntilAgendaItemCount(1)
        composeTestRule.onAgendaItem(1).performClick()
        onView(isRoot()).perform(EspressoUtils.waitId(R.id.scroll_view, 5000))
        onView(withId(R.id.scroll_view)).check(matches(isDisplayed()))
        onView(withId(R.id.title_view)).check(matches(withText("Note A")))
    }

    @Test
    fun testChangeStateWithReverseNoteClick() {
        testUtils.setupBook("book-1", "* DONE Note A")
        testUtils.setupBook(
            "book-2",
            "* TODO Note B\nSCHEDULED: <2014-01-01>\n* TODO Note C\nSCHEDULED: <2014-01-02>\n"
        )
        AppPreferences.isReverseNoteClickAction(context, false)
        scenario = ActivityScenario.launch(MainActivity::class.java)

        searchForTextCloseKeyboard(".it.done ad.7")
        composeTestRule.waitUntilAgendaItemCount(1)
        composeTestRule.onAgendaItem(1).performLongClick()
        waitForActionMode(R.id.state)
        onView(withId(R.id.state)).perform(click())
        onView(withText("NEXT")).perform(click())
    }

    @Test
    fun testInactiveScheduled() {
        testUtils.setupBook("notebook-1", "* Note A\nSCHEDULED: [2020-07-01]\nDEADLINE: <2020-07-01>")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.1")
        // Overdue, note (deadline), today
        composeTestRule.waitUntilAgendaItemCount(3)
    }

    @Test
    fun testInactiveDeadline() {
        testUtils.setupBook("notebook-1", "* Note A\nDEADLINE: [2020-07-01]\nSCHEDULED: <2020-07-01>")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.1")
        // today, note (scheduled)
        composeTestRule.waitUntilAgendaItemCount(2)
    }

    @Test
    fun testInactiveScheduledAndDeadline() {
        testUtils.setupBook("notebook-1", "* Note A\nSCHEDULED: [2020-07-01]\nDEADLINE: [2020-07-01]")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("ad.1")
        // Today
        composeTestRule.waitUntilAgendaItemCount(1)
    }
}
