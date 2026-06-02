package com.orgzly.android.espresso

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import cc.alensiljak.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.ui.main.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Espresso UI tests for the Books (Notebooks) Compose screen.
 *
 * Uses standard Espresso + Compose interop: text and content descriptions from Compose
 * composables are accessible via Espresso's accessibility bridge.
 */
class BooksScreenTest : OrgzlyTest() {

    @get:Rule
    val retryTestRule = RetryTestRule()

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        testUtils.setupBook("notebook-alpha", "* Note A\n* Note B\n")
        testUtils.setupBook("notebook-beta", "* Note X\n")

        ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun books_listDisplayedOnLaunch() {
        onView(withText("notebook-alpha")).check(matches(isDisplayed()))
        onView(withText("notebook-beta")).check(matches(isDisplayed()))
    }

    @Test
    fun books_createNewBook_viaFab() {
        val newBookName = "my-new-notebook"

        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.dialog_input))
            .perform(androidx.test.espresso.action.ViewActions.replaceText(newBookName))
        onView(withText(R.string.create)).perform(click())

        onView(withText(newBookName)).check(matches(isDisplayed()))
    }

    @Test
    fun books_longPressEntersSelectionMode() {
        onView(withText("notebook-alpha")).perform(longClick())

        // Selection toolbar appears with cancel button
        onView(withContentDescription(context.getString(R.string.cancel)))
            .check(matches(isDisplayed()))
    }

    @Test
    fun books_deleteBook_showsSnackbar() {
        onView(withText("notebook-alpha")).perform(longClick())
        com.orgzly.android.espresso.util.EspressoUtils.contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.delete)).perform(click())
        onView(withText(R.string.delete)).perform(click())

        onView(withText(R.string.message_book_deleted)).check(matches(isDisplayed()))
    }

    @Test
    fun books_backPressInSelectionMode_clearsSelection() {
        onView(withText("notebook-alpha")).perform(longClick())

        // Selection mode — cancel via back
        pressBack()

        // FAB should be visible (default mode restored)
        onView(withId(R.id.fab)).check(matches(isDisplayed()))
    }

    @Test
    fun books_deleteBook_bookDisappears() {
        onView(withText("notebook-alpha")).perform(longClick())
        com.orgzly.android.espresso.util.EspressoUtils.contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.delete)).perform(click())
        onView(withText(R.string.delete)).perform(click())

        onView(withText("notebook-alpha")).check(doesNotExist())
    }

    @Test
    fun books_clickBook_opensBookFragment() {
        onView(withText("notebook-alpha")).perform(click())
        onView(withId(R.id.fragment_book_view_flipper)).check(matches(isDisplayed()))
    }

    @Test
    fun books_searchBar_openAndClose() {
        // Open search bar via the search icon
        onView(withContentDescription(context.getString(R.string.search))).perform(click())

        // Search bar is open — placeholder text visible
        onView(withText(R.string.search_hint)).check(matches(isDisplayed()))

        // Close via the back/cancel arrow
        onView(withContentDescription(context.getString(R.string.cancel))).perform(click())

        // Back to normal — FAB is visible and search placeholder is gone
        onView(withId(R.id.fab)).check(matches(isDisplayed()))
        onView(withText(R.string.search_hint)).check(doesNotExist())
    }

    @Test
    fun books_searchBar_filtersByName() {
        // Open search bar and type a partial book name
        onView(withContentDescription(context.getString(R.string.search))).perform(click())
        // The Compose TextField exposes its placeholder as a hint in the accessibility tree
        onView(androidx.test.espresso.matcher.ViewMatchers.withHint(R.string.search_hint))
            .perform(replaceText("alpha"))

        // Submit the search — navigates to query results for "alpha"
        onView(withContentDescription(context.getString(R.string.search))).perform(click())

        // The query fragment opens — books list is no longer the active view
        onView(withId(R.id.fab)).check(doesNotExist())
    }

    @Test
    fun books_renameBook() {
        onView(withText("notebook-alpha")).perform(longClick())
        com.orgzly.android.espresso.util.EspressoUtils.contextualToolbarOverflowMenu().perform(click())
        onView(withText(R.string.rename)).perform(click())
        onView(withId(R.id.name))
            .perform(androidx.test.espresso.action.ViewActions.clearText())
            .perform(androidx.test.espresso.action.ViewActions.typeText("notebook-renamed"))
        onView(withText(R.string.rename)).perform(click())

        onView(withText("notebook-renamed")).check(matches(isDisplayed()))
    }
}
