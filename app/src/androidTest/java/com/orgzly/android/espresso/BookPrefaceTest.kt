package com.orgzly.android.espresso

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import cc.alensiljak.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.espresso.util.EspressoUtils.clickSetting
import com.orgzly.android.espresso.util.EspressoUtils.onActionItemClick
import com.orgzly.android.espresso.util.EspressoUtils.replaceTextCloseKeyboard
import com.orgzly.android.espresso.util.onPreface
import com.orgzly.android.espresso.util.performClick
import com.orgzly.android.espresso.util.waitUntilNoteCount
import com.orgzly.android.RetryTestRule
import com.orgzly.android.ui.main.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BookPrefaceTest : OrgzlyTest() {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val retryTestRule = RetryTestRule()

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        testUtils.setupBook(
            "book-name",
            """
                Line 1
                Line 2
                Line 3
                Line 4
                Line 5

                * Note #1.
                * Note #2.
                ** TODO Note #3.
            """.trimIndent()
        )

        ActivityScenario.launch(MainActivity::class.java)

        onView(withText("book-name")).perform(click())
        composeTestRule.waitUntilNoteCount(1)
    }

    @Test
    fun testOpensBookDescription() {
        composeTestRule.onPreface().performClick()
        onView(withId(R.id.fragment_book_preface_container)).check(matches(isDisplayed()))
    }

    @Test
    fun testUpdatingBookPreface() {
        composeTestRule.onPreface().performClick()
        onView(withId(R.id.fragment_book_preface_content)).perform(click())
        onView(withId(R.id.fragment_book_preface_content_edit)).perform(*replaceTextCloseKeyboard("New content"))
        onView(withId(R.id.done)).perform(click()) // Preface done
        composeTestRule.onPreface().performClick()
        onView(withId(R.id.fragment_book_preface_content_view)).check(matches(withText("New content")))
    }

    @Test
    fun testDeleteBookPreface() {
        composeTestRule.onPreface().assertIsDisplayed()

        composeTestRule.onPreface().performClick()
        onActionItemClick(R.id.delete, R.string.delete)

        composeTestRule.onPreface().assertDoesNotExist()
    }

    @Test
    fun testPrefaceFullDisplayed() {
        setPrefaceSetting(R.string.preface_in_book_full)
        composeTestRule.onPreface().assertTextContains("Line 1\nLine 2\nLine 3\nLine 4\nLine 5", substring = true)
    }

    @Test
    fun testPrefaceHiddenNotDisplayed() {
        setPrefaceSetting(R.string.preface_in_book_hide)
        composeTestRule.onPreface().assertDoesNotExist()
    }

    private fun setPrefaceSetting(@StringRes id: Int) {
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.preface_in_book)
        onView(withText(id)).perform(click())
        pressBack()
        pressBack()
    }
}
