package com.orgzly.android.espresso

import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.widget.DatePicker
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions.open
import androidx.test.espresso.contrib.PickerActions.setDate
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import cc.alensiljak.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.onNoteContent
import com.orgzly.android.espresso.util.onNoteRow
import com.orgzly.android.espresso.util.onNoteTitle
import com.orgzly.android.espresso.util.performLongClick
import com.orgzly.android.espresso.util.scrollBookToIndex
import com.orgzly.android.espresso.util.waitUntilNoteCount
import com.orgzly.android.espresso.util.EspressoUtils.closeSoftKeyboardWithDelay
import com.orgzly.android.espresso.util.EspressoUtils.contextualToolbarOverflowMenu
import com.orgzly.android.espresso.util.EspressoUtils.onActionItemClick
import com.orgzly.android.espresso.util.EspressoUtils.replaceTextCloseKeyboard
import com.orgzly.android.espresso.util.EspressoUtils.scroll
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class BookTest : OrgzlyTest() {

    private lateinit var scenario: ActivityScenario<MainActivity>

    private val LONG_CONTENT_NOTE_TITLE = "Note with very long content"
    private val LONG_CONTENT_START = "START OF LONG CONTENT..."
    private val LONG_CONTENT_END = "...END OF LONG CONTENT"
    private val LONG_CONTENT_LENGTH = 6000

    private fun buildLongString(): String {
        val sb = StringBuilder(LONG_CONTENT_LENGTH)
        sb.append(LONG_CONTENT_START)
        val repeatLength = LONG_CONTENT_LENGTH - LONG_CONTENT_START.length - LONG_CONTENT_END.length
        repeat(repeatLength) { sb.append('x') }
        sb.append(LONG_CONTENT_END)
        return sb.toString()
    }

    @get:Rule
    val mRetryTestRule = RetryTestRule()

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        // Book with enough notes to get a scrollable list on every device.
        // Preface: "Sample book used for tests" → LazyColumn item 0
        // Note #1 → LazyColumn item 1 → note_row index 0
        // Note #n → LazyColumn item n → note_row index n-1
        testUtils.setupBook("book-name",
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
            "** Note #11.\n" +
            "** Note #12.\n" +
            "** Note #13.\n" +
            "** Note #14.\n" +
            ":PROPERTIES:\n" +
            ":CREATED: [2017-01-01]\n" +
            ":END:\n" +
            "** Note #15.\n" + buildLongString() + "\n" +
            "** Note #16.\n" +
            "** Note #17.\n" +
            "** Note #18.\n" +
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
            "** Note #29.\n" +
            "** Note #30.\n" +
            "** Note #31.\n" +
            "** Note #32.\n" +
            "** Note #33.\n" +
            "** Note #34.\n" +
            "** Note #35.\n" +
            "** Note #36.\n" +
            "** Note #37.\n" +
            "** Note #38.\n" +
            "** Note #39.\n" +
            "** Note #40.\n")

        testUtils.setupBook("Book B", "")

        scenario = ActivityScenario.launch(MainActivity::class.java)
        onView(allOf(withText("book-name"), isDisplayed())).perform(click())
        composeTestRule.waitUntilNoteCount(5)
    }

    @After
    @Throws(Exception::class)
    override fun tearDown() {
        super.tearDown()
        scenario.close()
    }

    @Test
    fun testNoteExists() {
        // Note #7 is at LazyColumn index 7, note_row index 6
        composeTestRule.onNoteTitle(6).assertTextContains("Note #7.", substring = true)
    }

    @Test
    fun testBookHasNewNoteIconDisplayed() {
        onView(withId(R.id.fab)).check(matches(isDisplayed()))
    }

    @Test
    fun testOpensNoteFromBook() {
        composeTestRule.onNoteRow(1).performClick()
        onView(withId(R.id.view_flipper)).check(matches(isDisplayed()))
    }

    @Test
    fun testScheduledNoteTimeStaysTheSameAfterSetting() {
        // Note #9 is the only note with a scheduled time (note_row index 8)
        composeTestRule.onAllNodesWithTag("note_scheduled")[0]
            .assertIsDisplayed()
            .assertTextContains(userDateTime("<2014-05-26 Mon>"), substring = true)

        composeTestRule.onNoteRow(8).performLongClick()
        onView(withId(R.id.schedule)).perform(click())
        onView(withText(R.string.set)).perform(click())

        composeTestRule.onAllNodesWithTag("note_scheduled")[0]
            .assertIsDisplayed()
            .assertTextContains(userDateTime("<2014-05-26 Mon>"), substring = true)
    }

    @Test
    fun testRemovingScheduledTimeFromMultipleNotes() {
        // Only Note #9 (index 8) has a scheduled time initially
        composeTestRule.onAllNodesWithTag("note_scheduled").assertCountEquals(1)

        // Select Note #8 (index 7) and Note #9 (index 8)
        composeTestRule.onNoteRow(7).performLongClick()
        composeTestRule.onNoteRow(8).performClick()

        onView(withId(R.id.schedule)).perform(click())
        onView(withText(R.string.clear)).perform(click())

        // Both scheduled times cleared
        composeTestRule.onAllNodesWithTag("note_scheduled").assertCountEquals(0)
    }

    @Test
    fun testRemovingDoneState() {
        // Note #5 (index 4) and Note #8 (index 7) are both DONE
        composeTestRule.onNoteTitle(4).assertTextContains("DONE", substring = true)
        composeTestRule.onNoteTitle(7).assertTextContains("DONE", substring = true)

        composeTestRule.onNoteRow(4).performLongClick()
        composeTestRule.onNoteRow(7).performClick()

        onView(withId(R.id.state)).perform(click())
        onView(withText("TODO")).perform(click())

        composeTestRule.onNoteTitle(4).assertTextContains("TODO", substring = true)
        composeTestRule.onNoteTitle(7).assertTextContains("TODO", substring = true)
    }

    @Test
    fun testClearStateTitleUnchanged() {
        composeTestRule.onNoteTitle(0).assertTextContains("Note #1.", substring = true)

        composeTestRule.onNoteRow(0).performLongClick()
        onView(withId(R.id.state)).perform(click())
        onView(withText(R.string.clear)).perform(click())

        composeTestRule.onNoteTitle(0).assertTextContains("Note #1.", substring = true)
    }

    @Test
    fun testScrollPositionKeptOnRotation() {
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        SystemClock.sleep(500)

        // Scroll to Note #40 (LazyColumn item 40 with preface at 0)
        composeTestRule.scrollBookToIndex(40)
        composeTestRule.onNodeWithText("Note #40.", substring = true).assertIsDisplayed()

        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }

        composeTestRule.onNodeWithText("Note #40.", substring = true).assertIsDisplayed()
    }

    @Test
    fun testScrollPositionKeptInBackStack() {
        composeTestRule.scrollBookToIndex(40)
        composeTestRule.onNodeWithText("Note #40.", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Note #40.", substring = true).performClick()
        pressBack()
        composeTestRule.onNodeWithText("Note #40.", substring = true).assertIsDisplayed()
    }

    @Test
    fun testCreateNewNoteUsingFabWhenBookIsEmpty() {
        pressBack()
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.dialog_input)).perform(*replaceTextCloseKeyboard("book-created-from-scratch"))
        onView(withText(R.string.create)).perform(click())

        onView(allOf(withText("book-created-from-scratch"), isDisplayed())).perform(click())

        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.scroll_view)).check(matches(isDisplayed()))
    }

    @Test
    fun testFoldUnfoldAllButtonWhenBookIsEmpty() {
        pressBack()
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.dialog_input)).perform(*replaceTextCloseKeyboard("book-created-from-scratch"))
        onView(withText(R.string.create)).perform(click())

        onView(allOf(withText("book-created-from-scratch"), isDisplayed())).perform(click())

        onView(withId(R.id.books_options_menu_item_cycle_visibility)).check(doesNotExist())
    }

    @Test
    fun testBackFromSettingsShouldReturnToPreviousFragment() {
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        pressBack()
        onView(withId(R.id.fragment_book_view_flipper)).check(matches(isDisplayed()))
    }

    @Test
    fun testCutThenOpenNoteAtThePosition() {
        composeTestRule.onNoteRow(1).performLongClick()
        onActionItemClick(R.id.cut, R.string.cut)
        // Open note now at index 1 (was at index 2 before cut)
        composeTestRule.onNoteRow(1).performClick()
    }

    @Test
    fun testCabForMovingNotesDisplayed() {
        composeTestRule.onNoteRow(0).performLongClick()
        onActionItemClick(R.id.move, R.string.move)
        onView(withId(R.id.notes_action_move_down)).check(matches(isDisplayed()))
    }

    @Test
    fun testOrderOfMovedNote() {
        // Note #3 is at index 2; move it down past Note #4
        composeTestRule.onNoteRow(2).performLongClick()
        onActionItemClick(R.id.move, R.string.move)
        onView(withId(R.id.notes_action_move_down)).perform(click())

        composeTestRule.onNoteTitle(0).assertTextContains("Note #1.", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note #2.", substring = true)
        composeTestRule.onNoteTitle(2).assertTextContains("Note #4.", substring = true)
        composeTestRule.onNoteTitle(3).assertTextContains("Note #5.", substring = true)
        composeTestRule.onNoteTitle(4).assertTextContains("Note #6.", substring = true)
        composeTestRule.onNoteTitle(5).assertTextContains("Note #3.", substring = true)
    }

    @Test
    fun testActionModeMovingStaysOpenAfterRotation() {
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }

        onView(withId(R.id.notes_action_move_down)).check(doesNotExist())

        composeTestRule.onNoteRow(1).performLongClick()
        onActionItemClick(R.id.move, R.string.move)
        onView(withId(R.id.notes_action_move_down)).check(matches(isDisplayed()))

        SystemClock.sleep(500)
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }

        onView(withId(R.id.notes_action_move_down)).check(matches(isDisplayed()))
    }

    @Test
    fun testPromoting() {
        composeTestRule.onNoteRow(1).performLongClick()
        onActionItemClick(R.id.move, R.string.move)
        onView(withId(R.id.notes_action_move_left)).perform(click())
    }

    @Test
    fun testPasteAbove() {
        // Cut Note #2 (index 1)
        composeTestRule.onNoteRow(1).performLongClick()
        onActionItemClick(R.id.cut, R.string.cut)

        // After cut: indices 0=Note#1, 1=Note#8, 2=Note#9
        composeTestRule.onNoteTitle(0).assertTextContains("Note #1.", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note #8.", substring = true)
        composeTestRule.onNoteTitle(2).assertTextContains("Note #9.", substring = true)

        // Paste above Note #1
        composeTestRule.onNoteRow(0).performLongClick()
        onActionItemClick(R.id.paste, R.string.paste)
        onView(withText(R.string.heads_action_menu_item_paste_above)).perform(click())

        // Note #2 subtree is now first
        composeTestRule.onNoteTitle(0).assertTextContains("Note #2.", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note #3.", substring = true)
        composeTestRule.onNoteTitle(2).assertTextContains("Note #4.", substring = true)
        composeTestRule.onNoteTitle(3).assertTextContains("Note #5.", substring = true)
        composeTestRule.onNoteTitle(4).assertTextContains("Note #6.", substring = true)
        composeTestRule.onNoteTitle(5).assertTextContains("Note #7.", substring = true)
        composeTestRule.onNoteTitle(6).assertTextContains("Note #1.", substring = true)
        composeTestRule.onNoteTitle(7).assertTextContains("Note #8.", substring = true)
        composeTestRule.onNoteTitle(8).assertTextContains("Note #9.", substring = true)
    }

    @Test
    fun testPasteUnder() {
        // Cut Note #2 (index 1) with its subtree
        composeTestRule.onNoteRow(1).performLongClick()
        onActionItemClick(R.id.cut, R.string.cut)

        // After cut: index 0=Note#1, 1=Note#8, 2=Note#9
        composeTestRule.onNoteTitle(0).assertTextContains("Note #1.", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note #8.", substring = true)
        composeTestRule.onNoteTitle(2).assertTextContains("Note #9.", substring = true)

        // Paste under Note #8 (now at index 1)
        composeTestRule.onNoteRow(1).performLongClick()
        onActionItemClick(R.id.paste, R.string.paste)
        onView(withText(R.string.heads_action_menu_item_paste_under)).perform(click())

        // Head of list unchanged
        composeTestRule.onNoteTitle(0).assertTextContains("Note #1.", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note #8.", substring = true)
        composeTestRule.onNoteTitle(2).assertTextContains("Note #9.", substring = true)
        composeTestRule.onNoteTitle(3).assertTextContains("Note #10.", substring = true)

        // Note #2 subtree is pasted as last children of Note #8; scroll to them
        composeTestRule.scrollBookToIndex(35)
        composeTestRule.onNodeWithText("Note #2.", substring = true).assertIsDisplayed()
        composeTestRule.scrollBookToIndex(36)
        composeTestRule.onNodeWithText("Note #3.", substring = true).assertIsDisplayed()
    }

    @Test
    fun testCopyBelow() {
        // Copy Note #1 (index 0) and paste below it
        composeTestRule.onNoteRow(0).performLongClick()
        onActionItemClick(R.id.copy, R.string.copy)

        composeTestRule.onNoteRow(0).performLongClick()
        onActionItemClick(R.id.paste, R.string.paste)
        onView(withText(R.string.heads_action_menu_item_paste_below)).perform(click())

        composeTestRule.onNoteTitle(0).assertTextContains("Note #1.", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note #1.", substring = true)
        composeTestRule.onNoteTitle(2).assertTextContains("Note #2.", substring = true)
    }

    @Test
    fun testFoldNotes() {
        // Note #2 is the first note with children → fold_button index 0
        composeTestRule.onAllNodesWithTag("note_fold_button")[0].performClick()

        composeTestRule.onNoteTitle(0).assertTextContains("Note #1.", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note #2.", substring = true)
        // After folding Note #2, its 5 children are hidden; next visible is Note #8
        composeTestRule.onNoteTitle(2).assertTextContains("Note #8.", substring = true)
    }

    @Test
    fun testCreateNewNoteUnderFolded() {
        // Fold Note #2 (fold_button index 0)
        composeTestRule.onAllNodesWithTag("note_fold_button")[0].performClick()

        // Long-click folded Note #2 (index 1 after fold)
        composeTestRule.onNoteRow(1).performLongClick()
        onActionItemClick(R.id.new_note, R.string.new_note)
        onView(withText(R.string.new_under)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("Created"))
        onView(withId(R.id.done)).perform(click())

        // New note created under folded Note #2 should not be visible
        composeTestRule.onNoteTitle(0).assertTextContains("Note #1.", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note #2.", substring = true)
        composeTestRule.onNoteTitle(2).assertTextContains("Note #8.", substring = true)
    }

    @Test
    fun testNewNoteHasCreatedAtPropertyAfterSaving() {
        AppPreferences.createdAt(context, true)

        pressBack()
        onView(allOf(withText("Book B"), isDisplayed())).perform(click())

        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("Title"))
        onView(withId(R.id.done)).perform(click())

        // "Book B" has no preface → note_row index 0 = first note
        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteRow(0).performClick()
        onView(withId(R.id.scroll_view)).perform(swipeUp())

        onView(allOf(withId(R.id.name), withText(R.string.created_property_name))).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.name), withText(""))).check(matches(isDisplayed()))
    }

    @Test
    fun testReturnToNonExistentNoteByPressingBack() {
        composeTestRule.onNoteRow(0).performClick()

        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withText(R.string.notebooks)).perform(click())

        onView(withText("book-name")).perform(androidx.test.espresso.action.ViewActions.longClick())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.delete)).perform(click())
        onView(withText(R.string.delete)).perform(click())

        pressBack()
        onView(withId(R.id.view_flipper)).check(matches(isDisplayed()))
        onView(withText(R.string.note_does_not_exist_anymore)).check(matches(isDisplayed()))
        onView(withId(R.id.done)).check(doesNotExist())
        onView(withId(R.id.delete)).check(doesNotExist())
    }

    @Test
    fun testSetDeadlineTimeForNewNote() {
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.deadline_button)).perform(closeSoftKeyboardWithDelay(), scroll(), click())
        onView(withId(R.id.date_picker_button)).perform(click())
        onView(withClassName(equalTo(DatePicker::class.java.name))).perform(setDate(2014, 4, 1))
        onView(withText(android.R.string.ok)).perform(click())
        SystemClock.sleep(100)
        onView(withText(R.string.set)).perform(click())
        onView(withId(R.id.deadline_button)).check(matches(withText(userDateTime("<2014-04-01 Tue>"))))
    }

    @Test
    fun testToggleState() {
        composeTestRule.onNoteRow(0).performLongClick()
        onView(withId(R.id.toggle_state)).perform(click())
        composeTestRule.onNoteTitle(0).assertTextContains("DONE", substring = true)

        onView(withId(R.id.toggle_state)).perform(click())
        composeTestRule.onNoteTitle(0).assertTextContains("TODO", substring = true)
    }

    @Test
    fun testToggleStateForAllDone() {
        // Note #5 (index 4) and Note #8 (index 7) are DONE
        composeTestRule.onNoteRow(4).performLongClick()
        composeTestRule.onNoteRow(7).performClick()
        onView(withId(R.id.toggle_state)).perform(click())

        composeTestRule.onNoteTitle(4).assertTextContains("TODO", substring = true)
        composeTestRule.onNoteTitle(7).assertTextContains("TODO", substring = true)
    }

    @Test
    fun testToggleStateForAllTodo() {
        composeTestRule.onNoteRow(0).performLongClick()
        composeTestRule.onNoteRow(1).performClick()
        onView(withId(R.id.toggle_state)).perform(click())

        composeTestRule.onNoteTitle(0).assertTextContains("DONE", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("DONE", substring = true)
    }

    @Test
    fun testToggleStateForMixed() {
        composeTestRule.onNoteRow(0).performLongClick()
        composeTestRule.onNoteRow(4).performClick()
        onView(withId(R.id.toggle_state)).perform(click())

        composeTestRule.onNoteTitle(0).assertTextContains("DONE", substring = true)
        composeTestRule.onNoteTitle(4).assertTextContains("DONE", substring = true)
    }

    @Test
    @Ignore("Not implemented yet")
    fun testPreselectedStateOfSelectedNote() {
        composeTestRule.onNoteRow(2).performLongClick()
        onView(withId(R.id.state)).perform(click())
        onView(withText("TODO")).check(matches(androidx.test.espresso.matcher.ViewMatchers.isChecked()))
    }

    @Test
    fun testLongContentDisplayedInNote() {
        // Note #15 is at index 14; scroll to make it visible
        composeTestRule.scrollBookToIndex(15)
        // Note #15 is the first (and only) note with content in this book
        composeTestRule.onNoteContent(0)
            .assertTextContains(LONG_CONTENT_START, substring = true)
            .assertTextContains(LONG_CONTENT_END, substring = true)
    }
}
