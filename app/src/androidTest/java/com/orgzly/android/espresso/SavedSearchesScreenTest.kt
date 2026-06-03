package com.orgzly.android.espresso

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions.open
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
 * Espresso UI tests for the SavedSearches Compose screen.
 */
class SavedSearchesScreenTest : OrgzlyTest() {

    @get:Rule
    val retryTestRule = RetryTestRule()

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        testUtils.setupBook("book-one", "* Note A\n")

        ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withText(R.string.searches)).perform(click())
    }

    @Test
    fun savedSearches_listDisplayedOnLaunch() {
        // Default saved searches should be present
        onView(withText("Agenda")).check(matches(isDisplayed()))
    }

    @Test
    fun savedSearches_fabVisible() {
        onView(withId(R.id.fab)).check(matches(isDisplayed()))
    }

    @Test
    fun savedSearches_longPressEntersSelectionMode() {
        onView(withText("Agenda")).perform(longClick())

        // Cancel/back button should appear in selection toolbar
        onView(withContentDescription(context.getString(R.string.cancel)))
            .check(matches(isDisplayed()))
        // FAB hides
        onView(withId(R.id.fab)).check(doesNotExist())
    }

    @Test
    fun savedSearches_backPressClearsSelection() {
        onView(withText("Agenda")).perform(longClick())

        // Selection mode active
        onView(withContentDescription(context.getString(R.string.cancel)))
            .check(matches(isDisplayed()))

        pressBack()

        // Back to default mode — FAB is visible again
        onView(withId(R.id.fab)).check(matches(isDisplayed()))
    }

    @Test
    fun savedSearches_selectionToolbarShowsMoveButtons_forSingleItem() {
        onView(withText("Agenda")).perform(longClick())

        onView(withContentDescription(context.getString(R.string.up)))
            .check(matches(isDisplayed()))
        onView(withContentDescription(context.getString(R.string.down)))
            .check(matches(isDisplayed()))
    }

    @Test
    fun savedSearches_moveDown() {
        onView(withText("Agenda")).perform(longClick())
        onView(withContentDescription(context.getString(R.string.down))).perform(click())
    }

    @Test
    fun savedSearches_moveUp() {
        onView(withText("Agenda")).perform(longClick())
        onView(withContentDescription(context.getString(R.string.up))).perform(click())
    }

    @Test
    fun savedSearches_cancelSelectionViaBackButton() {
        onView(withText("Agenda")).perform(longClick())
        onView(withContentDescription(context.getString(R.string.cancel))).perform(click())

        onView(withId(R.id.fab)).check(matches(isDisplayed()))
    }

    @Test
    fun savedSearches_openDrawerWhileInSelectionModeClearsSelection() {
        onView(withText("Agenda")).perform(longClick())
        onView(withId(R.id.drawer_layout)).perform(open())
        // After opening drawer (which triggers lockDrawer/unlockDrawer), verify no move buttons
        onView(withContentDescription(context.getString(R.string.up))).check(doesNotExist())
    }
}
