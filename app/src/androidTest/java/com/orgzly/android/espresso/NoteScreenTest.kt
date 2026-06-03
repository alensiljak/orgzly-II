package com.orgzly.android.espresso

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import cc.alensiljak.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils.onNoteInBook
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for the Note editor Compose screen (NoteFragmentCompose / NoteScreen.kt).
 *
 * Selectors use text and content-descriptions rather than legacy view IDs, since the
 * screen is fully in Compose.  The only exceptions are [R.id.title_edit] and
 * [R.id.content_edit], which are AndroidView wrappers that retain their IDs.
 */
class NoteScreenTest : OrgzlyTest() {

    private lateinit var scenario: ActivityScenario<MainActivity>

    @get:Rule
    val retryTestRule = RetryTestRule()

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        testUtils.setupBook(
            "notebook",
            """
            * Note #1.

            * Note #2.
            SCHEDULED: <2014-05-22 Thu> DEADLINE: <2014-05-22 Thu>

            ** TODO Note #3.

            *** DONE Note #4.
            CLOSED: [2014-01-01 Wed 20:07]

            * Note #5.
            :PROPERTIES:
            :MYKEY: myvalue
            :END:
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withText("notebook")).perform(click())
    }

    @After
    override fun tearDown() {
        super.tearDown()
        scenario.close()
    }

    // ------------------------------------------------------------------
    // Open existing note
    // ------------------------------------------------------------------

    @Test
    fun note_openExistingNote_displaysTitle() {
        onNoteInBook(1).perform(click())

        // Title field should contain the note text
        onView(withId(R.id.title_edit)).check(matches(withText("Note #1.")))
    }

    // ------------------------------------------------------------------
    // Edit title and body
    // ------------------------------------------------------------------

    @Test
    fun note_editTitle_savedCorrectly() {
        onNoteInBook(1).perform(click())

        onView(withId(R.id.title_edit)).perform(replaceText("Updated title"))
        onView(withContentDescription(context.getString(R.string.done))).perform(click())

        onNoteInBook(1, R.id.item_head_title_view).check(matches(withText("Updated title")))
    }

    @Test
    fun note_editContent_savedCorrectly() {
        onNoteInBook(1).perform(click())

        onView(withId(R.id.content_edit)).perform(replaceText("New body text"))
        onView(withContentDescription(context.getString(R.string.done))).perform(click())

        // Re-open and confirm content persisted
        onNoteInBook(1).perform(click())
        onView(withId(R.id.content_edit)).check(matches(withText("New body text")))
    }

    // ------------------------------------------------------------------
    // Set / clear state
    // ------------------------------------------------------------------

    @Test
    fun note_setState_todoDisplayed() {
        onNoteInBook(1).perform(click())

        // Tap the "State" metadata row (empty → shows label text)
        onView(withText(R.string.state)).perform(click())
        onView(withText("TODO")).inRoot(isDialog()).perform(click())

        onView(withText("TODO")).check(matches(isDisplayed()))
    }

    @Test
    fun note_clearState_labelRestored() {
        // Note #3 already has TODO state
        onNoteInBook(3).perform(click())

        onView(withText("TODO")).perform(click())  // click the state metadata item
        onView(withText(R.string.clear)).inRoot(isDialog()).perform(click())

        // The state label should show back (no value → shows placeholder text)
        onView(withText(R.string.state)).check(matches(isDisplayed()))
    }

    @Test
    fun note_setStateToDone_addsClosed() {
        // Note #2 has no state
        onNoteInBook(2).perform(click())

        onView(withText(R.string.state)).perform(click())
        onView(withText("DONE")).inRoot(isDialog()).perform(click())

        // CLOSED timestamp should now appear
        onView(withText(R.string.closed)).check(doesNotExist())  // label is gone — value shown
        // The closed item value (org date string) is now visible
        onView(withText(R.string.state)).check(doesNotExist())   // state is set, shows "DONE"
        onView(withText("DONE")).check(matches(isDisplayed()))
    }

    // ------------------------------------------------------------------
    // Set scheduled and deadline dates
    // ------------------------------------------------------------------

    @Test
    fun note_setScheduledDate_timestampDisplayed() {
        onNoteInBook(1).perform(click())

        onView(withText(R.string.scheduled)).perform(click())
        onView(withText(R.string.set)).perform(click())

        // After setting, the "Scheduled" label is replaced by the org date string
        onView(withText(R.string.scheduled)).check(doesNotExist())
    }

    @Test
    fun note_clearScheduledDate() {
        // Note #2 already has a scheduled date — the MetadataItem shows the org string,
        // and the "Scheduled" label is NOT present (value replaces it).
        onNoteInBook(2).perform(click())

        // The clear (×) button in the scheduled MetadataItem has contentDescription "Clear"
        onView(withContentDescription(context.getString(R.string.clear))).perform(click())

        // After clearing, the placeholder label should be restored
        onView(withText(R.string.scheduled)).check(matches(isDisplayed()))
    }

    @Test
    fun note_setDeadlineDate_timestampDisplayed() {
        onNoteInBook(1).perform(click())

        onView(withText(R.string.deadline)).perform(click())
        onView(withText(R.string.set)).perform(click())

        onView(withText(R.string.deadline)).check(doesNotExist())
    }

    // ------------------------------------------------------------------
    // Add / remove tags
    // ------------------------------------------------------------------

    @Test
    fun note_addTag_tagDisplayed() {
        onNoteInBook(1).perform(click())

        onView(withText(R.string.tags)).perform(click())
        // The Compose tags dialog shows a TextField with a "Tags" hint
        onView(withText(R.string.tags)).inRoot(isDialog()).perform(replaceText("work"))
        onView(withText(R.string.done)).inRoot(isDialog()).perform(click())

        onView(withText("work")).check(matches(isDisplayed()))
    }

    @Test
    fun note_clearAllTags() {
        onNoteInBook(1).perform(click())

        // Add a tag first
        onView(withText(R.string.tags)).perform(click())
        onView(withText(R.string.tags)).inRoot(isDialog()).perform(replaceText("work"))
        onView(withText(R.string.done)).inRoot(isDialog()).perform(click())

        // Now clear via the × clear button on the tags MetadataItem
        onView(withContentDescription(context.getString(R.string.clear))).perform(click())

        onView(withText(R.string.tags)).check(matches(isDisplayed()))
    }

    // ------------------------------------------------------------------
    // Set priority
    // ------------------------------------------------------------------

    @Test
    fun note_setPriority_priorityDisplayed() {
        onNoteInBook(1).perform(click())

        onView(withText(R.string.priority)).perform(click())
        onView(withText("A")).inRoot(isDialog()).perform(click())

        onView(withText("A")).check(matches(isDisplayed()))
    }

    @Test
    fun note_clearPriority() {
        onNoteInBook(1).perform(click())

        onView(withText(R.string.priority)).perform(click())
        onView(withText("B")).inRoot(isDialog()).perform(click())

        onView(withText("B")).check(matches(isDisplayed()))

        onView(withText("B")).perform(click())  // Re-open priority dialog
        onView(withText(R.string.clear)).inRoot(isDialog()).perform(click())

        onView(withText(R.string.priority)).check(matches(isDisplayed()))
    }

    // ------------------------------------------------------------------
    // Save note
    // ------------------------------------------------------------------

    @Test
    fun note_saveNote_returnsToBookList() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.title_edit)).perform(replaceText("Saved title"))
        onView(withContentDescription(context.getString(R.string.done))).perform(click())

        // Returned to book view
        onView(withId(R.id.fragment_book_view_flipper)).check(matches(isDisplayed()))
        onNoteInBook(1, R.id.item_head_title_view).check(matches(withText("Saved title")))
    }

    @Test
    fun note_titleCannotBeEmpty_showsSnackbar() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.title_edit)).perform(replaceText(""))
        onView(withContentDescription(context.getString(R.string.done))).perform(click())

        onView(withText(R.string.title_can_not_be_empty)).check(matches(isDisplayed()))
    }

    // ------------------------------------------------------------------
    // Discard changes (back press)
    // ------------------------------------------------------------------

    @Test
    fun note_backOnUnmodifiedNote_noDismissDialog() {
        onNoteInBook(1).perform(click())
        pressBack()

        // No "Save changes?" dialog — went straight back
        onView(withText(R.string.discard_or_save_changes)).check(doesNotExist())
        onView(withId(R.id.fragment_book_view_flipper)).check(matches(isDisplayed()))
    }

    @Test
    fun note_backOnModifiedNote_showsDiscardDialog() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.title_edit)).perform(replaceText("Modified"))
        pressBack()

        onView(withText(R.string.discard_or_save_changes))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun note_discardChanges_titleUnchanged() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.title_edit)).perform(replaceText("Modified"))
        pressBack()

        onView(withText(R.string.discard)).inRoot(isDialog()).perform(click())

        onNoteInBook(1, R.id.item_head_title_view).check(matches(withText("Note #1.")))
    }

    // ------------------------------------------------------------------
    // Create new note
    // ------------------------------------------------------------------

    @Test
    fun note_createNewNote_appearsInList() {
        onView(withId(R.id.fab)).perform(click())

        onView(withId(R.id.title_edit)).perform(replaceText("Brand new note"))
        onView(withContentDescription(context.getString(R.string.done))).perform(click())

        onView(withText("Brand new note")).check(matches(isDisplayed()))
    }

    @Test
    fun note_createNewNote_emptyTitle_showsSnackbar() {
        onView(withId(R.id.fab)).perform(click())

        onView(withContentDescription(context.getString(R.string.done))).perform(click())

        onView(withText(R.string.title_can_not_be_empty)).check(matches(isDisplayed()))
    }

    // ------------------------------------------------------------------
    // Delete note (via overflow menu)
    // ------------------------------------------------------------------

    @Test
    fun note_deleteNote_removedFromList() {
        onNoteInBook(1).perform(click())

        onView(withContentDescription(context.getString(R.string.more_options))).perform(click())
        onView(withText(R.string.delete)).perform(click())
        onView(withText(R.string.delete)).inRoot(isDialog()).perform(click())

        onView(withId(R.id.fragment_book_view_flipper)).check(matches(isDisplayed()))
        onView(withText(context.resources.getQuantityString(R.plurals.notes_deleted, 1, 1)))
            .check(matches(isDisplayed()))
    }

    // ------------------------------------------------------------------
    // Existing properties are loaded
    // ------------------------------------------------------------------

    @Test
    fun note_existingProperties_displayed() {
        // Note #5 has MYKEY property
        onNoteInBook(5).perform(click())

        onView(withText("MYKEY")).check(matches(isDisplayed()))
        onView(withText("myvalue")).check(matches(isDisplayed()))
    }
}
