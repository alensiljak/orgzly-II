package com.orgzly.android.espresso

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import cc.alensiljak.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils.*
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers.hasToString
import org.junit.Before
import org.junit.Rule
import org.junit.Test


class BooksSortOrderTest : OrgzlyTest() {
    @get:Rule
    val retryTestRule = RetryTestRule()

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        testUtils.setupBook("Book A", "* Note A-01")
        testUtils.setupBook("Book B", "* Note B-01")

        ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun books_sortOrder() {
        onView(withText("Book A")).check(matches(isDisplayed()))
        onView(withText("Book B")).check(matches(isDisplayed()))
    }

    @Test
    fun books_sortOrderAfterSettingsChange() {
        modifySecondBook()
        setBooksSortOrder(R.string.notebooks_sort_order_modification_time)

        onView(withText("Book A")).check(matches(isDisplayed()))
        onView(withText("Book B")).check(matches(isDisplayed()))
    }

    private fun modifySecondBook() {
        onView(withText("Book B")).perform(click())
        onNoteInBook(1).perform(longClick())
        onView(withId(R.id.toggle_state)).perform(click())
        pressBack()
        pressBack()
    }

    private fun setBooksSortOrder(id: Int) {
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.sort_order)
        onData(hasToString(context.getString(id))).perform(click())
        pressBack()
        pressBack()
    }
}
