package com.orgzly.android.espresso

import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.widget.DatePicker
import android.widget.TextView
import android.widget.TimePicker
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.typeTextIntoFocusedView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.PickerActions.setDate
import androidx.test.espresso.contrib.PickerActions.setTime
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import cc.alensiljak.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils
import com.orgzly.android.espresso.util.EspressoUtils.clickSetting
import com.orgzly.android.espresso.util.EspressoUtils.onActionItemClick
import com.orgzly.android.espresso.util.EspressoUtils.onNoteInBook
import com.orgzly.android.espresso.util.EspressoUtils.onSnackbar
import com.orgzly.android.espresso.util.EspressoUtils.replaceTextCloseKeyboard
import com.orgzly.android.espresso.util.EspressoUtils.scroll
import com.orgzly.android.espresso.util.EspressoUtils.setNumber
import com.orgzly.android.espresso.util.EspressoUtils.settingsSetTodoKeywords
import com.orgzly.android.ui.main.MainActivity
import junit.framework.TestCase.assertTrue
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasToString
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Migrated from the legacy-View NoteFragment to the Compose NoteScreen.
 *
 * Selector guide for the Compose note screen:
 *  - Done button       → withContentDescription(getString(R.string.done))
 *  - Overflow menu     → withContentDescription(getString(R.string.more_options))
 *  - State (empty)     → withText(R.string.state)
 *  - State (set)       → withText(stateValue)
 *  - Priority (empty)  → withText(R.string.priority)
 *  - Priority (set)    → withText(priorityValue)
 *  - Scheduled (empty) → withText(R.string.scheduled)
 *  - Scheduled (set)   → withText(value)
 *  - Deadline (empty)  → withText(R.string.deadline)
 *  - Deadline (set)    → withText(value)
 *  - Closed (absent)   → withText(R.string.closed).check(doesNotExist())
 *  - Closed (present)  → withText(value)
 *  - Tags (empty)      → withText(R.string.tags)
 *  - Title edit        → withId(R.id.title_edit)  [AndroidView — retains ID]
 *  - Content edit      → withId(R.id.content_edit) [AndroidView — retains ID]
 *  - Insert timestamp  → withContentDescription(getString(R.string.insert_timestamp))
 *  - Property name     → withContentDescription(getString(R.string.property_name))
 *  - Property value    → withContentDescription(getString(R.string.property_value))
 *  - Add property (+)  → withContentDescription(getString(R.string.new_property))
 *  - Breadcrumb book   → withText(bookName)
 *  - Breadcrumb note   → withText(noteTitle)
 */
class NoteFragmentTest : OrgzlyTest() {
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Rule
    @JvmField
    val mRetryTestRule = RetryTestRule()

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        testUtils.setupBook(
                "book-name",
                """
                    Sample book used for tests

                    * Note #1.

                    * Note #2.
                    SCHEDULED: <2014-05-22 Thu> DEADLINE: <2014-05-22 Thu>

                    ** TODO Note #3.

                    ** Note #4.
                    SCHEDULED: <2015-01-11 Sun .+1d/2d>

                    *** DONE Note #5.
                    CLOSED: [2014-01-01 Wed 20:07]

                    **** Note #6.

                    ** Note #7.

                    * ANTIVIVISECTIONISTS Note #8.

                    **** Note #9.

                    ** Note #10.
                    :PROPERTIES:
                    :CREATED:  [2019-10-04 Fri 10:23]
                    :END:

                """.trimIndent())

        scenario = ActivityScenario.launch(MainActivity::class.java)

        onView(withText("book-name")).perform(click())
    }

    @After
    override fun tearDown() {
        super.tearDown()
        scenario.close()
    }

    @Test
    fun testDeleteNote() {
        onNoteInBook(1).perform(click())

        // Confirm note screen is open (TopAppBar title)
        onView(withText(R.string.note)).check(matches(isDisplayed()))

        onView(withContentDescription(context.getString(R.string.more_options))).perform(click())
        onView(withText(R.string.delete)).perform(click())
        onView(withText(R.string.delete)).inRoot(isDialog()).perform(click())

        onView(withId(R.id.fragment_book_view_flipper)).check(matches(isDisplayed()))

        onSnackbar().check(matches(withText(
                context.resources.getQuantityString(R.plurals.notes_deleted, 1, 1))))
    }

    @Test
    fun testUpdateNoteTitle() {
        onNoteInBook(1, R.id.item_head_title_view).check(matches(withText("Note #1.")))

        onNoteInBook(1).perform(click())

        onView(withId(R.id.title_edit)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("Note title changed"))

        onView(withContentDescription(context.getString(R.string.done))).perform(click())

        onNoteInBook(1, R.id.item_head_title_view).check(matches(withText("Note title changed")))
    }

    @Test
    fun testSettingScheduleTime() {
        onNoteInBook(1).perform(click())
        onView(withText(R.string.scheduled)).check(matches(isDisplayed()))
        onView(withText(R.string.scheduled)).perform(click())
        onView(withId(R.id.is_active_label)).check(matches(not(isDisplayed())))
        onView(withId(R.id.is_active_checkbox)).check(matches(not(isDisplayed())))
        onView(withText(R.string.set)).perform(click())
        onView(withText(startsWith(defaultDialogUserDate()))).check(matches(isDisplayed()))
    }

    @Test
    fun testAbortingOfSettingScheduledTime() {
        onNoteInBook(1).perform(click())
        onView(withText(R.string.scheduled)).check(matches(isDisplayed()))
        onView(withText(R.string.scheduled)).perform(click())
        pressBack()
        onView(withText(R.string.scheduled)).check(matches(isDisplayed()))
    }

    @Test
    fun testRemovingScheduledTime() {
        // Note #4 has scheduled <2015-01-11 Sun .+1d/2d> and no deadline — unambiguous date
        onNoteInBook(4).perform(click())
        onView(withText(R.string.scheduled)).check(doesNotExist())
        onView(withText(userDateTime("<2015-01-11 Sun .+1d/2d>"))).perform(click())
        onView(withText(R.string.clear)).perform(click())
        onView(withText(R.string.scheduled)).check(matches(isDisplayed()))
    }

    @Test
    fun testRemovingScheduledTimeAndOpeningTimestampDialogAgain() {
        onNoteInBook(4).perform(click())
        onView(withText(userDateTime("<2015-01-11 Sun .+1d/2d>"))).perform(click())
        onView(withText(R.string.clear)).perform(click())
        onView(withText(R.string.scheduled)).check(matches(isDisplayed()))
        onView(withText(R.string.scheduled)).perform(click())
    }

    @Test
    fun testSettingDeadlineTime() {
        onNoteInBook(1).perform(click())
        onView(withText(R.string.deadline)).check(matches(isDisplayed()))
        onView(withText(R.string.deadline)).perform(click())
        onView(withId(R.id.is_active_label)).check(matches(not(isDisplayed())))
        onView(withId(R.id.is_active_checkbox)).check(matches(not(isDisplayed())))
        onView(withText(R.string.set)).perform(click())
        onView(withText(startsWith(defaultDialogUserDate()))).check(matches(isDisplayed()))
    }

    @Test
    fun testAbortingOfSettingDeadlineTime() {
        onNoteInBook(1).perform(click())
        onView(withText(R.string.deadline)).check(matches(isDisplayed()))
        onView(withText(R.string.deadline)).perform(click())
        pressBack()
        onView(withText(R.string.deadline)).check(matches(isDisplayed()))
    }

    @Test
    fun testRemovingDeadlineTime() {
        // Use Note #1 (no timestamps) to avoid date ambiguity: set then clear a deadline
        onNoteInBook(1).perform(click())
        onView(withText(R.string.deadline)).check(matches(isDisplayed()))
        onView(withText(R.string.deadline)).perform(click())
        onView(withText(R.string.set)).perform(click())
        // Deadline is now set — label is replaced by the date value
        onView(withText(R.string.deadline)).check(doesNotExist())
        onView(withText(startsWith(defaultDialogUserDate()))).perform(click())
        onView(withText(R.string.clear)).perform(click())
        onView(withText(R.string.deadline)).check(matches(isDisplayed()))
    }

    @Test
    fun testStateToDoneShouldAddClosedTime() {
        onNoteInBook(2).perform(click())

        onView(withText(R.string.closed)).check(doesNotExist())
        onView(withText(R.string.state)).perform(click())
        onView(withText("DONE")).inRoot(isDialog()).perform(click())
        onView(withText(startsWith(currentUserDate()))).check(matches(isDisplayed()))
    }

    @Test
    fun testStateToDoneShouldOverwriteLastRepeat() {
        onNoteInBook(4).perform(click())

        onView(withText(R.string.state)).perform(click())
        onView(withText("DONE")).inRoot(isDialog()).perform(click())

        onView(withText(R.string.state)).perform(click())
        onView(withText("DONE")).inRoot(isDialog()).perform(click())

        // This will fail if there are two or more LAST_REPEAT properties
        onView(withText("LAST_REPEAT")).check(matches(isDisplayed()))
    }

    @Test
    fun testStateToDoneForNoteShouldShiftTime() {
        onNoteInBook(4).perform(click())

        onView(withText(R.string.state)).check(matches(isDisplayed()))
        onView(withText(userDateTime("<2015-01-11 Sun .+1d/2d>"))).check(matches(isDisplayed()))
        onView(withText(R.string.closed)).check(doesNotExist())

        onView(withText(R.string.state)).perform(click())
        onView(withText("DONE")).inRoot(isDialog()).perform(click())

        onView(withText(R.string.state)).check(matches(isDisplayed()))
        onView(withText(userDateTime("<2015-01-11 Sun .+1d/2d>"))).check(doesNotExist())
        onView(withText(R.string.closed)).check(doesNotExist())
    }

    @Test
    fun testChangingStateSettingsFromNoteFragment() {
        onNoteInBook(1).perform(click())
        settingsSetTodoKeywords("")
        onView(withText(R.string.state)).perform(click())
        // Only DONE should be in the dialog
        onView(withText("DONE")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("TODO")).inRoot(isDialog()).check(doesNotExist())
        pressBack()
        settingsSetTodoKeywords("TODO")
        onView(withText(R.string.state)).perform(click())
        onView(withText("TODO")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("DONE")).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    @Test
    fun testTitleCanNotBeEmptyForNewNote() {
        onView(withId(R.id.fab)).perform(click()) // New note
        onView(withContentDescription(context.getString(R.string.done))).perform(click())
        onSnackbar().check(matches(withText(R.string.title_can_not_be_empty)))
    }

    @Test
    fun testTitleCanNotBeEmptyForExistingNote() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.title_edit)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard(""))
        onView(withContentDescription(context.getString(R.string.done))).perform(click())
        onSnackbar().check(matches(withText(R.string.title_can_not_be_empty)))
    }

    @Test
    fun testSavingNoteWithRepeater() {
        onNoteInBook(4).perform(click())
        onView(withContentDescription(context.getString(R.string.done))).perform(click())
    }

    @Test
    fun testClosedTimeInNoteFragmentIsSameAsInList() {
        onNoteInBook(5).perform(click())
        onView(withText(userDateTime("[2014-01-01 Wed 20:07]"))).check(matches(isDisplayed()))
    }

    @Test
    fun testSettingStateRemainsSetAfterRotation() {
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onNoteInBook(1).perform(click())
        onView(withText(R.string.state)).perform(click())
        onView(withText("TODO")).inRoot(isDialog()).perform(click())
        onView(withText("TODO")).check(matches(isDisplayed()))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        onView(withText("TODO")).check(matches(isDisplayed()))
    }

    @Test
    fun testSettingPriorityRemainsSetAfterRotation() {
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onNoteInBook(1).perform(click())
        onView(withText(R.string.priority)).perform(click())
        onView(withText("B")).inRoot(isDialog()).perform(click())
        onView(withText("B")).check(matches(isDisplayed()))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        onView(withText("B")).check(matches(isDisplayed()))
    }

    @Test
    fun testSettingScheduledTimeRemainsSetAfterRotation() {
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onNoteInBook(1).perform(click())
        onView(withText(R.string.scheduled)).check(matches(isDisplayed()))
        onView(withText(R.string.scheduled)).perform(click())
        onView(withText(R.string.set)).perform(click())
        onView(withText(startsWith(defaultDialogUserDate()))).check(matches(isDisplayed()))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        onView(withText(startsWith(defaultDialogUserDate()))).check(matches(isDisplayed()))
    }

    @Test
    fun testSetScheduledTimeAfterRotation() {
        onNoteInBook(1).perform(click())

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withText(R.string.scheduled)).check(matches(isDisplayed()))
        onView(withText(R.string.scheduled)).perform(click())

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        onView(withText(R.string.set)).perform(click())
        onView(withText(startsWith(defaultDialogUserDate()))).check(matches(isDisplayed()))
    }

    @Test
    fun testRemovingDoneStateRemovesClosedTime() {
        onNoteInBook(5).perform(click())
        onView(withText(userDateTime("[2014-01-01 Wed 20:07]"))).check(matches(isDisplayed()))
        onView(withText("DONE")).perform(click())  // DONE is the state MetadataItem value
        onView(withText(R.string.clear)).inRoot(isDialog()).perform(click())
        onView(withText(R.string.closed)).check(doesNotExist())
    }

    @Test
    fun testSettingPmTimeDisplays24HourTime() {
        EspressoUtils.grantAlarmsAndRemindersSpecialPermission()
        onNoteInBook(1).perform(click())

        onView(withText(R.string.deadline)).check(matches(isDisplayed()))
        onView(withText(R.string.deadline)).perform(click())

        /* Set date. */
        onView(withId(R.id.date_picker_button)).perform(click())
        onView(withClassName(equalTo(DatePicker::class.java.name))).perform(setDate(2014, 4, 1))
        onView(withText(android.R.string.ok)).perform(click())

        /* Set time. */
        onView(withId(R.id.time_picker_button)).perform(scroll(), click())
        onView(withClassName(equalTo(TimePicker::class.java.name))).perform(setTime(15, 15))
        onView(withText(android.R.string.ok)).perform(click())

        onView(withText(R.string.set)).perform(click())

        onView(withText(userDateTime("<2014-04-01 Tue 15:15>"))).check(matches(isDisplayed()))
    }

    @Test
    fun testDateTimePickerKeepsValuesAfterRotation() {
        onNoteInBook(1).perform(click())

        onView(withText(R.string.deadline)).check(matches(isDisplayed()))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withText(R.string.deadline)).perform(click())

        /* Set date. */
        onView(withId(R.id.date_picker_button)).perform(click())
        onView(withClassName(equalTo(DatePicker::class.java.name))).perform(setDate(2014, 4, 1))
        onView(withText(android.R.string.ok)).perform(click())

        /* Set time. */
        onView(withId(R.id.time_picker_button)).perform(scroll(), click())
        onView(withClassName(equalTo(TimePicker::class.java.name))).perform(setTime(9, 15))
        onView(withText(android.R.string.ok)).perform(click())

        /* Set repeater. */
        onView(withId(R.id.repeater_used_checkbox)).perform(scroll(), click())
        onView(withId(R.id.repeater_picker_button)).perform(scroll(), click())
        onView(withId(R.id.value_picker)).perform(setNumber(3))
        onView(withText(R.string.ok)).perform(click())

        /* Rotate screen. */
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        SystemClock.sleep(500) // Give AVD time to complete rotation

        /* Set time. */
        onView(withText(R.string.set)).perform(click())

        onView(withText(userDateTime("<2014-04-01 Tue 09:15 .+3w>"))).check(matches(isDisplayed()))
    }

    @Test
    fun testChangingPrioritySettingsFromNoteFragment() {
        /* Open note which has no priority set. */
        onNoteInBook(1).perform(click())

        /* Change lowest priority to A. */
        onView(withContentDescription(context.getString(R.string.more_options))).perform(click())
        onView(withText(R.string.settings)).perform(click())
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.lowest_priority)
        onData(hasToString(containsString("A"))).perform(click())
        pressBack()
        pressBack()

        onView(withText(R.string.priority)).perform(click())
        // Only A is available (highest == lowest == A)
        onView(withText("A")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("B")).inRoot(isDialog()).check(doesNotExist())
        pressBack() // dismiss dialog

        /* Change lowest priority to C. */
        onView(withContentDescription(context.getString(R.string.more_options))).perform(click())
        onView(withText(R.string.settings)).perform(click())
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.lowest_priority)
        onData(hasToString(containsString("C"))).perform(click())
        pressBack()
        pressBack()

        onView(withText(R.string.priority)).perform(click())
        onView(withText("A")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("B")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("C")).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    @Test
    fun testPropertiesAfterRotatingDevice() {
        onNoteInBook(1).perform(click())

        onView(withContentDescription(context.getString(R.string.new_property))).perform(click())
        onView(withContentDescription(context.getString(R.string.property_name)))
                .perform(replaceText("prop-name-1"))
        onView(withContentDescription(context.getString(R.string.property_value)))
                .perform(*replaceTextCloseKeyboard("prop-value-1"))

        onView(withContentDescription(context.getString(R.string.new_property))).perform(click())
        onView(allOf(withContentDescription(context.getString(R.string.property_name)), not(withText("prop-name-1"))))
                .perform(replaceText("prop-name-2"))
        onView(allOf(withContentDescription(context.getString(R.string.property_value)), not(withText("prop-value-1"))))
                .perform(*replaceTextCloseKeyboard("prop-value-2"))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        SystemClock.sleep(500)

        onView(withText("prop-name-1")).check(matches(isDisplayed()))
        onView(withText("prop-value-1")).check(matches(isDisplayed()))
        onView(withText("prop-name-2")).check(matches(isDisplayed()))
        onView(withText("prop-value-2")).check(matches(isDisplayed()))
    }

    @Test
    fun testSavingProperties() {
        onNoteInBook(1).perform(click())

        onView(withContentDescription(context.getString(R.string.new_property))).perform(click())
        onView(withContentDescription(context.getString(R.string.property_name)))
                .perform(replaceText("prop-name-1"))
        onView(withContentDescription(context.getString(R.string.property_value)))
                .perform(*replaceTextCloseKeyboard("prop-value-1"))

        onView(withText("prop-name-1")).check(matches(isDisplayed()))
        onView(withText("prop-value-1")).check(matches(isDisplayed()))

        onView(withContentDescription(context.getString(R.string.done))).perform(click())

        onNoteInBook(1).perform(click())

        onView(withText("prop-name-1")).check(matches(isDisplayed()))
        onView(withText("prop-value-1")).check(matches(isDisplayed()))
    }

    @Test
    fun testContentLineCountUpdatedOnNoteUpdate() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.content_edit)).perform(scroll()) // For smaller screens
        onView(withId(R.id.content_edit)).perform(click())
        onView(withId(R.id.content_edit)).perform(typeTextIntoFocusedView("a\nb\nc"))
        onView(withContentDescription(context.getString(R.string.done))).perform(click())
        onNoteInBook(1, R.id.item_head_fold_button).perform(click())
        onNoteInBook(1, R.id.item_head_title_view).check(matches(withText(endsWith("3"))))
    }

    @Test
    fun testBreadcrumbsFollowToBook() {
        onNoteInBook(3).perform(click())

        // Click the book name in the breadcrumbs row
        onView(withText("book-name")).perform(click())

        onView(withId(R.id.fragment_book_view_flipper)).check(matches(isDisplayed()))
    }

    @Test
    fun testBreadcrumbsFollowToNote() {
        onNoteInBook(3).perform(click())
        // "Note #2." is the ancestor breadcrumb — a separate clickable Text composable
        onView(withText("Note #2.")).perform(click())
        onView(withId(R.id.title_edit)).check(matches(withText("Note #2.")))
    }

    @Test
    fun testBreadcrumbsPromptWhenCreatingNewNote() {
        onNoteInBook(1).perform(longClick())
        onActionItemClick(R.id.new_note, R.string.new_note)
        onView(withText(R.string.new_under)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("1.1"))
        // Click the ancestor breadcrumb "Note #1."
        onView(withText("Note #1.")).perform(click())

        // Dialog is displayed
        onView(withText(R.string.discard_or_save_changes))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))

        SystemClock.sleep(500) // If we click too early, the button doesn't yet work...
        onView(withText(R.string.cancel)).perform(click())

        // Title remains the same
        onView(withId(R.id.title_edit)).check(matches(withText("1.1")))
    }

    // https://github.com/orgzly/orgzly-android/issues/605
    @Test
    fun testMetadataShowSelectedOnNoteLoad() {
        onNoteInBook(10).perform(click())
        onView(withText("CREATED")).check(matches(isDisplayed()))
        onView(withContentDescription(context.getString(R.string.more_options))).perform(click())
        onView(withText(R.string.show_selected)).perform(click())
        // CREATED property is still visible because alwaysShowSet == true (default)
        onView(withText("CREATED")).check(matches(isDisplayed()))
        pressBack()
        onNoteInBook(10).perform(click())
        onView(withText("CREATED")).check(matches(isDisplayed()))
    }

    @Test
    fun testDoNotPromptAfterLeavingNewNoteUnmodified() {
        onView(withId(R.id.fab)).perform(click())
        pressBack() // Close keyboard
        pressBack() // Leave note

        onView(withId(R.id.fragment_book_view_flipper)).check(matches(isDisplayed()))
    }

    @Test
    fun testTimestampButtonVisibleWhenEditing() {
        onNoteInBook(1).perform(click())
        // In the Compose note screen, the ContentToolbar (with the insert-timestamp button) is
        // always visible when the content section is expanded (default state).
        onView(withContentDescription(context.getString(R.string.insert_timestamp)))
                .check(matches(isDisplayed()))
        onView(withId(R.id.content_edit)).perform(click())
        onView(withContentDescription(context.getString(R.string.insert_timestamp)))
                .check(matches(isDisplayed()))
    }

    @Test
    fun testInsertInactiveTimestamp() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.content_edit)).perform(click())
        onView(withContentDescription(context.getString(R.string.insert_timestamp))).perform(click())
        onView(withId(R.id.is_active_label)).perform(scroll())
        onView(withId(R.id.is_active_label)).check(matches(isDisplayed()))
        onView(withId(R.id.is_active_checkbox)).check(matches(isDisplayed()))
        onView(withText(R.string.set)).perform(click())
        scenario.onActivity { activity ->
            val view = activity.findViewById<TextView>(R.id.content_edit)
            assertTrue(view.text.contains(Regex("\\[[0-9]{4}-[0-9]{2}-[0-9]{2} [A-Z][a-z]{2}\\]")))
        }
    }

    @Test
    fun testInsertActiveTimestamp() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.content_edit)).perform(click())
        onView(withContentDescription(context.getString(R.string.insert_timestamp))).perform(click())
        onView(withId(R.id.is_active_checkbox)).perform(scroll(), click())
        onView(withText(R.string.set)).perform(click())
        scenario.onActivity { activity ->
            val view = activity.findViewById<TextView>(R.id.content_edit)
            assertTrue(view.text.contains(Regex("<[0-9]{4}-[0-9]{2}-[0-9]{2} [A-Z][a-z]{2}>")))
        }
    }
}
