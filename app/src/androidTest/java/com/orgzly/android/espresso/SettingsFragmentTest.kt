package com.orgzly.android.espresso

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import cc.alensiljak.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils.clickSetting
import com.orgzly.android.espresso.util.EspressoUtils.onActionItemClick
import com.orgzly.android.espresso.util.EspressoUtils.replaceTextCloseKeyboard
import com.orgzly.android.espresso.util.onNoteRow
import com.orgzly.android.espresso.util.performClick
import com.orgzly.android.espresso.util.waitUntilNoteCount
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasToString
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsFragmentTest : OrgzlyTest() {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val mRetryTestRule = RetryTestRule()

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun testImportingGettingStartedFromGettingStartedNotebook() {
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.app)
        clickSetting(R.string.reload_getting_started)
        pressBack()
        pressBack()
        onView(withText(R.string.getting_started_notebook_name)).check(matches(isDisplayed()))
        onView(allOf(withText(R.string.getting_started_notebook_name), isDisplayed())).perform(click())
        onView(withId(R.id.fragment_book_view_flipper)).check(matches(isDisplayed()))
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.app)
        clickSetting(R.string.reload_getting_started)
        pressBack()
        pressBack()
        onView(withId(R.id.fragment_book_view_flipper)).check(matches(isDisplayed()))
        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteRow(0).performClick()
        onView(withId(R.id.scroll_view)).check(matches(isDisplayed()))
    }

    @Test
    fun testAddingNewTodoKeywordInSettingsAndChangingStateToItForNewNote() {
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.states)
        onView(withId(R.id.todo_states)).perform(*replaceTextCloseKeyboard("TODO AAA BBB CCC"))
        onView(withText(android.R.string.ok)).perform(click())
        onView(withText(R.string.not_now)).perform(click())
        clickSetting(R.string.state)
        onData(hasToString(containsString("CCC"))).perform(click())
    }

    @Test
    fun testAddingNewTodoKeywordInSettingsNewNoteShouldHaveDefaultState() {
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.states)
        onView(withId(R.id.todo_states)).perform(*replaceTextCloseKeyboard("TODO CCC"))
        onView(withText(android.R.string.ok)).perform(click())
        onView(withText(R.string.not_now)).perform(click())
        clickSetting(R.string.state)
        onData(hasToString(containsString("NOTE"))).perform(click())
    }

    @Test
    fun testStateSummaryAfterNoStates() {
        AppPreferences.states(context, "|")
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.states)
        onView(withId(R.id.todo_states)).perform(*replaceTextCloseKeyboard("TODO"))
        onView(withText(android.R.string.ok)).perform(click())
        onView(withText(R.string.not_now)).perform(click())
        onView(allOf(withText(R.string.states), hasSibling(withText("TODO |")))).check(matches(isDisplayed()))
    }

    @Test
    fun testStatesDuplicateDetectedIgnoringCase() {
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.states)
        onView(withId(R.id.todo_states)).perform(*replaceTextCloseKeyboard("TODO NEXT next"))
        onView(withText(context.getString(R.string.duplicate_keywords_not_allowed, "NEXT")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testNewNoteDefaultStateIsInitiallyVisibleInSummary() {
        AppPreferences.states(context, "AAA BBB CCC | DONE")
        AppPreferences.newNoteState(context, "BBB")
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.state)
        onView(withText("BBB")).check(matches(isDisplayed()))
    }

    @Test
    fun testNewNoteDefaultStateIsSetInitially() {
        AppPreferences.states(context, "AAA BBB CCC | DONE")
        AppPreferences.newNoteState(context, "BBB")
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.state)
        onData(hasToString(containsString("BBB"))).perform(click())
    }

    @Test
    fun testDefaultPriorityUpdateOnLowestPriorityChange() {
        AppPreferences.defaultPriority(context, "C")
        AppPreferences.minPriority(context, "E")
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.lowest_priority)
        onData(hasToString(containsString("B"))).perform(click())
        clickSetting(R.string.default_priority)
        onData(hasToString("B")).check(matches(isChecked()))
    }

    @Test
    fun testLowestPriorityUpdateOnDefaultPriorityChange() {
        AppPreferences.defaultPriority(context, "C")
        AppPreferences.minPriority(context, "E")
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.default_priority)
        onData(hasToString(containsString("X"))).perform(click())
        clickSetting(R.string.lowest_priority)
        onData(hasToString("X")).check(matches(isChecked()))
    }

    @Test
    fun testLowercaseStateConvertedToUppercase() {
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.states)
        onView(withId(R.id.todo_states)).perform(*replaceTextCloseKeyboard("TODO NEXT wait"))
        onView(withText(android.R.string.ok)).perform(click())
        onView(withText(R.string.not_now)).perform(click())
        onView(allOf(withText(R.string.states), hasSibling(withText("TODO NEXT WAIT | DONE"))))
            .check(matches(isDisplayed()))
    }
}
