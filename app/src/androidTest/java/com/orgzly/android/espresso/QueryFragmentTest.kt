package com.orgzly.android.espresso

import android.icu.util.Calendar
import android.os.SystemClock
import android.widget.DatePicker
import android.widget.TextView
import android.widget.TimePicker
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions.open
import androidx.test.espresso.contrib.PickerActions.setDate
import androidx.test.espresso.contrib.PickerActions.setTime
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import cc.alensiljak.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils.contextualToolbarOverflowMenu
import com.orgzly.android.espresso.util.EspressoUtils.grantAlarmsAndRemindersSpecialPermission
import com.orgzly.android.espresso.util.EspressoUtils.onActionItemClick
import com.orgzly.android.espresso.util.EspressoUtils.replaceTextCloseKeyboard
import com.orgzly.android.espresso.util.EspressoUtils.scroll
import com.orgzly.android.espresso.util.EspressoUtils.searchForTextCloseKeyboard
import com.orgzly.android.espresso.util.EspressoUtils.waitId
import com.orgzly.android.espresso.util.onNoteBookName
import com.orgzly.android.espresso.util.onNoteContent
import com.orgzly.android.espresso.util.onNoteFoldButton
import com.orgzly.android.espresso.util.onNoteRow
import com.orgzly.android.espresso.util.onNoteTitle
import com.orgzly.android.espresso.util.performClick
import com.orgzly.android.espresso.util.performLongClick
import com.orgzly.android.espresso.util.waitUntilExactNoteCount
import com.orgzly.android.espresso.util.waitUntilNoteCount
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.main.MainActivity
import com.orgzly.org.datetime.OrgDateTime
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue

class QueryFragmentTest : OrgzlyTest() {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val mRetryTestRule = RetryTestRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    override fun tearDown() {
        super.tearDown()
        scenario?.close()
    }

    private fun defaultSetUp() {
        testUtils.setupBook(
            "book-one",
            "First book used for testing\n" +
                    "* Note A.\n" +
                    "** [#A] Note B.\n" +
                    "* TODO Note C.\n" +
                    "SCHEDULED: <2014-01-01>\n" +
                    "** Note D.\n" +
                    "*** TODO Note E.\n" +
                    "*** Same title in different notebooks.\n" +
                    "*** Another note.\n" +
                    ""
        )

        testUtils.setupBook(
            "book-two",
            "Sample book used for tests\n" +
                    "* Note #1.\n" +
                    "* Note #2.\n" +
                    "** TODO Note #3.\n" +
                    "** Note #4.\n" +
                    "*** DONE Note #5.\n" +
                    "CLOSED: [2014-06-03 Tue 13:34]\n" +
                    "**** Note #6.\n" +
                    "** Note #7.\n" +
                    "* DONE Note #8.\n" +
                    "CLOSED: [2014-06-03 Tue 3:34]\n" +
                    "**** Note #9.\n" +
                    "SCHEDULED: <2014-05-26 Mon>\n" +
                    "** Note #10.\n" +
                    "** Same title in different notebooks.\n" +
                    "** Note #11.\n" +
                    "** Note #12.\n" +
                    "** Note #13.\n" +
                    "DEADLINE: <2014-05-26 Mon>\n" +
                    "** Note #14.\n" +
                    "** [#A] Note #15.\n" +
                    "** [#A] Note #16.\n" +
                    "** [#B] Note #17.\n" +
                    "** [#C] Note #18.\n" +
                    "** Note #19.\n" +
                    "** Note #20.\n" +
                    "** Note #21.\n" +
                    "** Note #22.\n" +
                    "** Note #23.\n" +
                    "** Note #24.\n" +
                    "** Note #25.\n" +
                    "** Note #26.\n" +
                    "** Note #27.\n" +
                    "** Note #28.\n" +
                    ""
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun testSearchFromBookOneResult() {
        defaultSetUp()
        onView(allOf(withText("book-one"), isDisplayed())).perform(click())
        searchForTextCloseKeyboard("b.book-one another note")
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()))
        composeTestRule.waitUntilExactNoteCount(1)
        composeTestRule.onNoteTitle(0).assertTextContains("Another note.", substring = false)
    }

    @Test
    fun testSearchFromBookMultipleResults() {
        defaultSetUp()
        onView(allOf(withText("book-one"), isDisplayed())).perform(click())
        searchForTextCloseKeyboard("b.book-one note")
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()))
        composeTestRule.waitUntilExactNoteCount(7)
    }

    @Test
    fun testSearchTwice() {
        defaultSetUp()
        searchForTextCloseKeyboard("different")
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()))
        composeTestRule.waitUntilExactNoteCount(2)
        searchForTextCloseKeyboard("another")
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()))
        composeTestRule.waitUntilExactNoteCount(1)
    }

    /**
     * Starting with 3 displayed to-do notes, removing state from one, expecting 2 to-do notes left.
     */
    @Test
    fun testEditChangeState() {
        defaultSetUp()
        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withText("To Do")).perform(click())
        composeTestRule.waitUntilExactNoteCount(3)
        composeTestRule.onAllNodes(hasText("Note C.", substring = true))[0].performLongClick()
        onView(withId(R.id.state)).perform(click())
        onView(withText(R.string.clear)).perform(click())
        composeTestRule.waitUntilExactNoteCount(2)
    }

    @Test
    fun testToggleState() {
        testUtils.setupBook("book-one", "* Note")
        scenario = ActivityScenario.launch(MainActivity::class.java)

        searchForTextCloseKeyboard("Note")
        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteRow(0).performLongClick()
        onView(withId(R.id.toggle_state)).perform(click())
        composeTestRule.onNoteTitle(0).assertTextContains("DONE", substring = true)
    }

    /**
     * Clicks on the last note and expects it opened.
     */
    @Test
    fun testClickingNote() {
        defaultSetUp()
        onView(allOf(withText("book-two"), isDisplayed())).perform(click())
        searchForTextCloseKeyboard("b.book-two Note")
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()))
        composeTestRule.waitUntilExactNoteCount(29)
        composeTestRule.onNoteRow(27).performClick()
        onView(withId(R.id.view_flipper)).check(matches(isDisplayed()))
        onView(allOf(withText("Note #28."), isDisplayed())).check(matches(isDisplayed()))
    }

    @Test
    fun testSchedulingNote() {
        defaultSetUp()
        grantAlarmsAndRemindersSpecialPermission()

        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withText("Scheduled")).perform(click())
        composeTestRule.waitUntilExactNoteCount(2)

        composeTestRule.onAllNodes(hasText("Note C.", substring = true))[0].performLongClick()
        onView(withId(R.id.schedule)).perform(click())
        onView(withId(R.id.date_picker_button)).perform(click())
        onView(withClassName(equalTo(DatePicker::class.java.name))).perform(setDate(2014, 4, 1))
        onView(withText(android.R.string.ok)).perform(click())
        SystemClock.sleep(500)
        onView(isRoot()).perform(waitId(R.id.time_picker_button, 5000))
        onView(withId(R.id.time_picker_button)).perform(scroll(), click())
        onView(withClassName(equalTo(TimePicker::class.java.name))).perform(setTime(9, 15))
        onView(withText(android.R.string.ok)).perform(click())
        onView(withText(R.string.set)).perform(click())

        composeTestRule.waitUntilExactNoteCount(2)
        onView(withText(userDateTime("<2014-04-01 Tue 09:15>"))).check(matches(isDisplayed()))
    }

    @Test
    fun testInheritedTagsAfterMovingNote() {
        testUtils.setupBook(
            "notebook-1",
            "* Note A :tag1:\n** Note B :tag2:\n*** Note C :tag3:\n*** Note D :tag3:\n"
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)

        onView(allOf(withText("notebook-1"), isDisplayed())).perform(click())
        composeTestRule.waitUntilNoteCount(1)

        // Move Note C (pos 3 → row 2) down
        composeTestRule.onNoteRow(2).performLongClick()
        onActionItemClick(R.id.move, R.string.move)
        onView(withId(R.id.notes_action_move_down)).perform(click())
        pressBack()
        pressBack()

        searchForTextCloseKeyboard("t.tag3")
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()))
        composeTestRule.waitUntilExactNoteCount(2)
        composeTestRule.onNoteTitle(0).assertTextContains("Note D  tag3 • tag2 tag1", substring = false)
        composeTestRule.onNoteTitle(1).assertTextContains("Note C  tag3 • tag2 tag1", substring = false)
    }

    @Test
    fun testInheritedTagsAfterDemotingSubtree() {
        testUtils.setupBook(
            "notebook-1",
            "* Note A :tag1:\n* Note B :tag2:\n** Note C :tag3:\n** Note D :tag3:\n"
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)

        onView(allOf(withText("notebook-1"), isDisplayed())).perform(click())
        composeTestRule.waitUntilNoteCount(1)

        // Demote Note B (pos 2 → row 1)
        composeTestRule.onNoteRow(1).performLongClick()
        onActionItemClick(R.id.move, R.string.move)
        onView(withId(R.id.notes_action_move_right)).perform(click())
        pressBack()
        pressBack()

        searchForTextCloseKeyboard("t.tag3")
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()))
        composeTestRule.waitUntilExactNoteCount(2)
        composeTestRule.onNoteTitle(0).assertTextContains("Note C  tag3 • tag1 tag2", substring = false)
        composeTestRule.onNoteTitle(1).assertTextContains("Note D  tag3 • tag1 tag2", substring = false)
    }

    @Test
    fun testInheritedTagsAfterCutAndPasting() {
        testUtils.setupBook(
            "notebook-1",
            "* Note A :tag1:\n* Note B :tag2:\n** Note C :tag3:\n** Note D :tag3:\n"
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)

        onView(allOf(withText("notebook-1"), isDisplayed())).perform(click())
        composeTestRule.waitUntilNoteCount(1)

        // Cut Note B (pos 2 → row 1)
        composeTestRule.onNoteRow(1).performLongClick()
        onActionItemClick(R.id.cut, R.string.cut)

        // Paste under Note A (pos 1 → row 0)
        composeTestRule.onNoteRow(0).performLongClick()
        onActionItemClick(R.id.paste, R.string.paste)
        onView(withText(R.string.heads_action_menu_item_paste_under)).perform(click())

        searchForTextCloseKeyboard("t.tag3")
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()))
        composeTestRule.waitUntilExactNoteCount(2)
        composeTestRule.onNoteTitle(0).assertTextContains("Note C  tag3 • tag1 tag2", substring = false)
        composeTestRule.onNoteTitle(1).assertTextContains("Note D  tag3 • tag1 tag2", substring = false)
    }

    @Test
    fun testOrderOfBooksAfterRenaming() {
        defaultSetUp()
        searchForTextCloseKeyboard("note")
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()))
        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteBookName(0).assertTextContains("book-one", substring = false)

        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withText(R.string.notebooks)).perform(click())

        onView(withText("book-one")).perform(longClick())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.rename)).perform(click())
        onView(withId(R.id.name)).perform(*replaceTextCloseKeyboard("renamed book-one"))
        onView(withText(R.string.rename)).perform(click())

        // The other book is now first. Rename it too to keep the order of notes the same.
        onView(withText("book-two")).perform(longClick())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.rename)).perform(click())
        onView(withId(R.id.name)).perform(*replaceTextCloseKeyboard("renamed book-two"))
        onView(withText(R.string.rename)).perform(click())

        pressBack()

        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()))
        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteBookName(0).assertTextContains("renamed book-one", substring = false)
    }

    @Test
    fun testContentOfFoldedNoteDisplayed() {
        AppPreferences.isNotesContentDisplayedInSearch(context, true)
        testUtils.setupBook(
            "notebook",
            "* Note A\n** Note B\nContent for Note B\n* Note C\n"
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)

        onView(allOf(withText("notebook"), isDisplayed())).perform(click())
        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteFoldButton(0).performClick()
        searchForTextCloseKeyboard("note")
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()))
        composeTestRule.waitUntilExactNoteCount(3)
        composeTestRule.onNoteTitle(1).assertTextContains("Note B", substring = true)
        composeTestRule.onNoteContent(1).assertTextContains("Content for Note B", substring = true)
    }

    @Test
    fun testDeSelectRemovedNoteInSearch() {
        testUtils.setupBook("notebook", "* TODO Note A\n* TODO Note B")
        scenario = ActivityScenario.launch(MainActivity::class.java)

        searchForTextCloseKeyboard("i.todo")
        composeTestRule.waitUntilExactNoteCount(2)
        composeTestRule.onNoteRow(0).performLongClick()

        // Check title for number of selected notes
        onView(allOf(instanceOf(TextView::class.java), withParent(withId(R.id.top_toolbar))))
            .check(matches(withText("1")))

        // Remove state from selected note
        onView(withId(R.id.state)).perform(click())
        onView(withText(R.string.clear)).perform(click())

        composeTestRule.waitUntilExactNoteCount(1)

        // Check subtitle for search query
        onView(allOf(instanceOf(TextView::class.java), not(withText(R.string.search)), withParent(withId(R.id.top_toolbar))))
            .check(matches(withText("i.todo")))
    }

    @Test
    fun testNoNotesFoundMessageIsDisplayedInSearch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("Note")
        SystemClock.sleep(200)
        onView(withText(R.string.no_notes_found_after_search)).check(matches(isDisplayed()))
    }

    @Ignore("Not implemented yet")
    @Test
    fun testPreselectedStateOfSelectedNote() {
        testUtils.setupBook("notebook", "* TODO Note A\n* TODO Note B")
        scenario = ActivityScenario.launch(MainActivity::class.java)

        searchForTextCloseKeyboard("i.todo")
        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteRow(1).performLongClick()
        onView(withId(R.id.state)).perform(click())
        onView(withText("TODO")).check(matches(isDisplayed()))
    }

    @Test
    fun testSearchAndClickOnNoteWithTwoDifferentEvents() {
        testUtils.setupBook("notebook", "* Note\n<2000-01-01>\n<2000-01-02>")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        searchForTextCloseKeyboard("e.lt.now")
        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteRow(0).performClick()
    }

    @Test
    fun testScheduledTimestamp() {
        val calendar = Calendar.getInstance()
        // Skip this test if current time is less than an hour before midnight
        assumeTrue(calendar.get(Calendar.HOUR_OF_DAY) < 23)
        val currentTime = calendar.timeInMillis
        val inOneHour = OrgDateTime.Builder()
            .setDateTime(currentTime + 1000 * 60 * 60)
            .setHasTime(true)
            .setIsActive(true)
            .build()
            .toString()

        testUtils.setupBook("notebook-1", "* Note A\nSCHEDULED: $inOneHour")
        scenario = ActivityScenario.launch(MainActivity::class.java)

        onView(withText("notebook-1")).perform(click())
        composeTestRule.waitUntilNoteCount(1)

        // Remove time usage
        composeTestRule.onNoteRow(0).performLongClick()
        onView(withId(R.id.schedule)).perform(click())
        onView(withId(R.id.time_used_checkbox)).perform(scroll(), click())
        onView(withText(R.string.set)).perform(click())
        pressBack()

        searchForTextCloseKeyboard("s.now")
        composeTestRule.waitUntilExactNoteCount(1)
    }

    @Test
    fun testSavedSearchAgendaFragmentDisplaysCorrectTitle() {
        defaultSetUp()

        testUtils.createSavedSearch("My Agenda", ".it.done ad.7")
        scenario = ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withText("My Agenda")).perform(click())

        onView(withId(R.id.fragment_query_agenda_view_flipper)).check(matches(isDisplayed()))
        onView(withId(R.id.fragment_query_search_view_flipper)).check(doesNotExist())

        onView(allOf(
            instanceOf(TextView::class.java),
            withParent(withId(R.id.top_toolbar)),
            withText("My Agenda")
        )).check(matches(isDisplayed()))
    }

    @Test
    fun testSavedSearchSearchFragmentDisplaysCorrectTitle() {
        defaultSetUp()

        testUtils.createSavedSearch("My Search", "i.todo")
        scenario = ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withText("My Search")).perform(click())

        SystemClock.sleep(200)

        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()))
        onView(withId(R.id.fragment_query_agenda_view_flipper)).check(doesNotExist())

        onView(allOf(
            instanceOf(TextView::class.java),
            withParent(withId(R.id.top_toolbar)),
            withText("My Search")
        )).check(matches(isDisplayed()))
    }
}
