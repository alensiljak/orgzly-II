package com.orgzly.android.espresso

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions.open
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.matcher.ViewMatchers.hasChildCount
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import cc.alensiljak.orgzly.R
import com.orgzly.android.BookFormat
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.waitUntilNoteCount
import com.orgzly.android.espresso.util.onNoteTitle
import com.orgzly.android.espresso.util.EspressoUtils.contextualToolbarOverflowMenu
import com.orgzly.android.espresso.util.EspressoUtils.onActionItemClick
import com.orgzly.android.espresso.util.EspressoUtils.onSnackbar
import com.orgzly.android.espresso.util.EspressoUtils.replaceTextCloseKeyboard
import com.orgzly.android.espresso.util.EspressoUtils.sync
import com.orgzly.android.repos.RepoType
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import android.app.Activity
import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import java.io.File

class BooksTest : OrgzlyTest() {

    @get:Rule
    val mRetryTestRule = RetryTestRule()

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        testUtils.setupBook("book-1", """
                First book used for testing
                * Note A.
                ** Note B.
                * TODO Note C.
                SCHEDULED: <2014-01-01>
                ** Note D.
                *** TODO Note E.
                """.trimIndent())

        testUtils.setupBook("book-2", """
                Sample book used for tests
                * Note #1.
                * Note #2.
                ** TODO Note #3.
                ** Note #4.
                *** DONE Note #5.
                CLOSED: [2014-06-03 Tue 13:34]
                **** Note #6.
                ** Note #7.
                * DONE Note #8.
                CLOSED: [2014-06-03 Tue 3:34]
                **** Note #9.
                SCHEDULED: <2014-05-26 Mon>
                ** Note #10.
                """.trimIndent())

        testUtils.setupBook("book-3", "")

        ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun testOpenSettings() {
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        onView(withText(R.string.look_and_feel)).check(matches(isDisplayed()))
    }

    @Test
    fun testReturnToNonExistentBookByPressingBack() {
        onView(withText("book-1")).perform(click())

        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withText(R.string.notebooks)).perform(click())
        onView(withText("book-1")).perform(longClick())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.delete)).perform(click())
        onView(withText(R.string.delete)).perform(click())
        pressBack()

        onView(withId(R.id.fragment_book_view_flipper)).check(matches(isDisplayed()))
        onView(withText(R.string.book_does_not_exist_anymore)).check(matches(isDisplayed()))
        onView(withId(R.id.fab)).check(matches(not(isDisplayed())))
        pressBack()

        android.os.SystemClock.sleep(500)
        onView(withText("book-2")).check(matches(isDisplayed()))
        onView(withText("book-2")).perform(click())
        onView(allOf(withText(R.string.book_does_not_exist_anymore), isDisplayed())).check(doesNotExist())
    }

    @Test
    @Ignore("Debugging")
    fun testJustExport() {
        onView(withText("book-1")).perform(longClick())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.export)).perform(click())
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressEnter()
    }

    @Test
    fun testCancelExportFileSelection() {
        onView(withText("book-1")).perform(longClick())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.export)).perform(click())
        for (i in 1 until 10) {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        }
    }

    @Test
    fun testExportWithFakeResponse() {
        onView(withText("book-1")).perform(longClick())
        contextualToolbarOverflowMenu().perform(click())

        Intents.init()

        val resultData = Intent()
        val file = File(context.cacheDir, "book-1.org")
        resultData.data = DocumentFile.fromFile(file).uri
        val result = android.app.Instrumentation.ActivityResult(Activity.RESULT_OK, resultData)
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(result)

        onView(withText(R.string.export)).perform(click())

        intended(allOf(hasAction(Intent.ACTION_CREATE_DOCUMENT), hasExtra(Intent.EXTRA_TITLE, "book-1.org")))
        onSnackbar().check(matches(withText(startsWith(context.getString(R.string.book_exported, "")))))

        file.delete()

        Intents.release()
    }

    @Test
    fun testCreateNewBookWithoutExtension() {
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.dialog_input)).perform(*replaceTextCloseKeyboard("book-created-from-scratch"))
        onView(withText(R.string.create)).perform(click())
        onView(allOf(withText("book-created-from-scratch"), isDisplayed())).perform(click())
        onView(withId(R.id.fragment_book_view_flipper)).check(matches(isDisplayed()))
    }

    @Test
    fun testCreateNewBookWithExtension() {
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.dialog_input)).perform(*replaceTextCloseKeyboard("book-created-from-scratch.org"))
        onView(withText(R.string.create)).perform(click())
        onView(allOf(withText("book-created-from-scratch.org"), isDisplayed())).perform(click())
        onView(withId(R.id.fragment_book_view_flipper)).check(matches(isDisplayed()))
    }

    @Test
    fun testCreateAndDeleteBook() {
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.dialog_input)).perform(*replaceTextCloseKeyboard("book-created-from-scratch"))
        onView(withText(R.string.create)).perform(click())

        onView(allOf(withText("book-created-from-scratch"), isDisplayed())).check(matches(isDisplayed()))

        onView(withText("book-created-from-scratch")).perform(longClick())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.delete)).perform(click())
        onView(withText(R.string.delete)).perform(click())

        onView(withText("book-created-from-scratch")).check(doesNotExist())
    }

    @Test
    fun testDifferentBookLoading() {
        onView(allOf(withText("book-1"), isDisplayed())).perform(click())
        composeTestRule.waitUntilNoteCount(1)
        // book-1 preface at LazyColumn index 0; first note at index 1 → note_row index 0
        composeTestRule.onNoteTitle(0).assertTextContains("Note A.", substring = true)
        pressBack()
        onView(allOf(withText("book-2"), isDisplayed())).perform(click())
        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteTitle(0).assertTextContains("Note #1.", substring = true)
    }

    @Test
    fun testLoadingBookOnlyIfFragmentHasViewCreated() {
        onView(allOf(withText("book-1"), isDisplayed())).perform(click())

        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withText(R.string.notebooks)).perform(click())

        onView(withText("book-2")).perform(longClick())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.delete)).perform(click())
        onView(withText(R.string.delete)).perform(click())
    }

    @Test
    fun testCreateNewBookWithExistingName() {
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.dialog_input)).perform(*replaceTextCloseKeyboard("new-book"))
        onView(withText(R.string.create)).perform(click())

        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.dialog_input)).perform(*replaceTextCloseKeyboard("new-book"))
        onView(withText(R.string.create)).perform(click())

        onSnackbar().check(matches(withText(context.getString(R.string.book_name_already_exists, "new-book"))))
    }

    @Test
    fun testCreateNewBookWithWhiteSpace() {
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.dialog_input)).perform(*replaceTextCloseKeyboard(" new-book  "))
        onView(withText(R.string.create)).perform(click())
        onView(withText("new-book")).check(matches(isDisplayed()))
    }

    @Test
    fun testRenameBookToExistingName() {
        onView(withText("book-1")).perform(longClick())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.rename)).perform(click())
        onView(withId(R.id.name)).perform(*replaceTextCloseKeyboard("book-2"))
        onView(withText(R.string.rename)).perform(click())
        onView(withText(endsWith(context.getString(R.string.book_name_already_exists, "book-2"))))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testRenameBookToSameName() {
        onView(withText("book-1")).perform(longClick())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.rename)).perform(click())
        onView(withText(R.string.rename)).check(matches(not(isEnabled())))
    }

    @Test
    fun testNoteCountDisplayed() {
        onView(withText(context.resources.getQuantityString(R.plurals.notes_count_nonzero, 5, 5)))
            .check(matches(isDisplayed()))
        onView(withText(context.resources.getQuantityString(R.plurals.notes_count_nonzero, 10, 10)))
            .check(matches(isDisplayed()))
        onView(withText(R.string.notes_count_zero))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testBackPressClosesSelectionMenu() {
        onView(withText("book-1")).perform(longClick())
        pressBack()
        onView(withText("book-1")).check(matches(isDisplayed()))
    }

    @Test
    fun testSetLinkOnSingleBookCurrentRepoIsSelected() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo")
        sync()
        onView(withText("mock://repo")).check(matches(isDisplayed()))
        onView(withText("book-1")).perform(longClick())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.books_context_menu_item_set_link)).perform(click())
        onView(withText("mock://repo")).check(matches(isChecked()))
    }

    @Test
    fun testSetLinkOnMultipleBooksNoRepoIsSelected() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo")
        sync()
        onView(withText("book-1")).perform(longClick())
        onView(withText("book-2")).perform(click())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.books_context_menu_item_set_link)).perform(click())
        onView(withText("mock://repo")).check(matches(isNotChecked()))
    }

    @Test
    fun testDeleteSingleBookLinkedUrlIsShown() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo")
        sync()
        onView(withText("book-1")).perform(longClick())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.delete)).perform(click())
        onView(withText(R.string.also_delete_linked_book)).check(matches(isDisplayed()))
        onView(withId(R.id.delete_linked_url)).check(matches(withText("mock://repo/book-1.org")))
    }

    @Test
    fun testDeleteMultipleBooksLinkedUrlIsNotShown() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo")
        sync()
        onView(withText("book-1")).perform(longClick())
        onView(withText("book-2")).perform(click())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.delete)).perform(click())
        onView(withText(R.string.also_delete_linked_books)).check(matches(isDisplayed()))
        onView(withId(R.id.delete_linked_url)).check(matches(withText("")))
    }

    @Test
    fun testDeleteMultipleBooksWithNoLinks() {
        onView(withText("book-1")).perform(longClick())
        onView(withText("book-2")).perform(click())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.delete)).perform(click())
        onView(withText(R.string.delete)).perform(click())
        assert(dataRepository.getBooks().size == 1)
    }

    @Test
    fun testDeleteMultipleBooksAndRooks() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo")
        sync()
        onView(withText("book-1")).perform(longClick())
        onView(withText("book-2")).perform(click())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.delete)).perform(click())
        onView(withId(R.id.delete_linked_checkbox)).perform(click())
        onView(withText(R.string.delete)).perform(click())
        assert(dataRepository.getBooks().size == 1)
    }

    @Test
    fun testMultipleBooksSelectedContextMenuShowsSupportedActionsOnly() {
        onView(withText("book-1")).perform(longClick())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.rename)).check(matches(isDisplayed()))
        onView(withText(R.string.export)).check(matches(isDisplayed()))
        onView(withClassName(containsString("MenuDropDownListView"))).check(matches(hasChildCount(4)))
        pressBack()
        onView(withText("book-2")).perform(click())
        contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.rename)).check(doesNotExist())
        onView(withText(R.string.export)).check(doesNotExist())
        onView(withClassName(containsString("MenuDropDownListView"))).check(matches(hasChildCount(2)))
    }
}
