package com.orgzly.android.espresso

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import cc.alensiljak.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils.clickSetting
import com.orgzly.android.espresso.util.EspressoUtils.onActionItemClick
import com.orgzly.android.espresso.util.EspressoUtils.searchForTextCloseKeyboard
import com.orgzly.android.espresso.util.onNoteContent
import com.orgzly.android.espresso.util.onNoteTitle
import com.orgzly.android.espresso.util.waitUntilExactNoteCount
import com.orgzly.android.espresso.util.waitUntilNoteCount
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasToString
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsChangeTest : OrgzlyTest() {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val mRetryTestRule = RetryTestRule()

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        testUtils.setupBook(
            "book-a",
            "* [#B] Note [a-1]\n" +
                    "SCHEDULED: <2014-01-01>\n" +
                    "Content for [a-1]\n" +
                    "* Note [a-2]\n" +
                    "SCHEDULED: <2014-01-01>\n"
        )

        ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun testChangeDefaultPrioritySearchResultsShouldBeReordered() {
        searchForTextCloseKeyboard("o.p")
        composeTestRule.waitUntilExactNoteCount(2)

        composeTestRule.onNoteTitle(0).assertTextContains("#B  Note [a-1]", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note [a-2]", substring = true)

        setDefaultPriority("A")

        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteTitle(0).assertTextContains("Note [a-2]", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("#B  Note [a-1]", substring = true)
    }

    @Test
    fun testChangeDefaultPriorityAgendaResultsShouldBeReordered() {
        searchForTextCloseKeyboard("o.p ad.2")
        composeTestRule.waitUntilExactNoteCount(2)

        // agenda_item(0)=day header; note titles are 0-indexed
        composeTestRule.onNoteTitle(0).assertTextContains("#B  Note [a-1]", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note [a-2]", substring = true)

        setDefaultPriority("A")

        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteTitle(0).assertTextContains("Note [a-2]", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("#B  Note [a-1]", substring = true)
    }

    @Test
    fun testDisplayedContentInBook() {
        onView(withText("book-a")).perform(click())
        composeTestRule.waitUntilNoteCount(1)

        composeTestRule.onNoteContent(0).assertIsDisplayed()
        composeTestRule.onNoteContent(0).assertTextContains("Content for [a-1]", substring = true)

        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.display_content)
        pressBack()
        pressBack()

        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onAllNodesWithTag("note_content").assertCountEquals(0)
    }

    private fun setDefaultPriority(priority: String) {
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.default_priority)
        onData(hasToString(containsString(priority))).perform(click())
        pressBack()
        pressBack()
    }
}
