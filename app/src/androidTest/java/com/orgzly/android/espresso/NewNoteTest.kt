package com.orgzly.android.espresso

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import cc.alensiljak.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils.onActionItemClick
import com.orgzly.android.espresso.util.EspressoUtils.replaceTextCloseKeyboard
import com.orgzly.android.espresso.util.onNoteRow
import com.orgzly.android.espresso.util.onNoteTitle
import com.orgzly.android.espresso.util.performLongClick
import com.orgzly.android.espresso.util.waitUntilNoteCount
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.endsWith
import org.junit.Rule
import org.junit.Test

class NewNoteTest : OrgzlyTest() {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val mRetryTestRule = RetryTestRule()

    @Test
    fun testNewNoteInEmptyNotebook() {
        testUtils.setupBook("notebook", "")
        ActivityScenario.launch(MainActivity::class.java)

        onView(withText("notebook")).perform(click())
        composeTestRule.waitUntilNoteCount(0)

        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("new note created by test"))
        onView(withId(R.id.done)).perform(click())

        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteTitle(0).assertTextContains("new note created by test", substring = true)
    }

    @Test
    fun testNewNoteUnder() {
        testUtils.setupBook("notebook", "description\n* 1\n** 2\n*** 3\n*** 4\n** 5\n* 6")
        ActivityScenario.launch(MainActivity::class.java)

        onView(withText("notebook")).perform(click())
        composeTestRule.waitUntilNoteCount(6)

        composeTestRule.onNoteRow(1).performLongClick()
        onActionItemClick(R.id.new_note, R.string.new_note)
        onView(withText(R.string.new_under)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("A"))
        onView(withId(R.id.done)).perform(click())

        composeTestRule.waitUntilNoteCount(7)
        composeTestRule.onNoteTitle(1).assertTextContains("2", substring = false)
        composeTestRule.onNoteTitle(2).assertTextContains("3", substring = false)
        composeTestRule.onNoteTitle(3).assertTextContains("4", substring = false)
        composeTestRule.onNoteTitle(4).assertTextContains("A", substring = false)
        testUtils.assertBook("notebook", "description\n\n* 1\n** 2\n*** 3\n*** 4\n*** A\n** 5\n* 6\n")
    }

    @Test
    fun testNewNoteAbove() {
        testUtils.setupBook("notebook", "description\n* 1\n** 2\n*** 3\n*** 4\n** 5\n* 6")
        ActivityScenario.launch(MainActivity::class.java)

        onView(withText("notebook")).perform(click())
        composeTestRule.waitUntilNoteCount(6)

        composeTestRule.onNoteRow(1).performLongClick()
        onActionItemClick(R.id.new_note, R.string.new_note)
        onView(withText(R.string.new_above)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("A"))
        onView(withId(R.id.done)).perform(click())

        composeTestRule.waitUntilNoteCount(7)
        composeTestRule.onNoteTitle(0).assertTextContains("1", substring = false)
        composeTestRule.onNoteTitle(1).assertTextContains("A", substring = false)
        composeTestRule.onNoteTitle(2).assertTextContains("2", substring = false)
        testUtils.assertBook("notebook", "description\n\n* 1\n** A\n** 2\n*** 3\n*** 4\n** 5\n* 6\n")
    }

    @Test
    fun testNewNoteBelow() {
        testUtils.setupBook(
            "booky",
            "Booky Preface\n* 1\n** 2\n*** 3\n*** 4\n** 5\n* 6"
        )
        ActivityScenario.launch(MainActivity::class.java)

        onView(withText("booky")).perform(click())
        composeTestRule.waitUntilNoteCount(6)

        composeTestRule.onNoteRow(1).performLongClick()
        onActionItemClick(R.id.new_note, R.string.new_note)
        onView(withText(R.string.new_below)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("A"))
        onView(withId(R.id.done)).perform(click())

        composeTestRule.waitUntilNoteCount(7)
        composeTestRule.onNoteTitle(1).assertTextContains("2", substring = false)
        composeTestRule.onNoteTitle(2).assertTextContains("3", substring = false)
        composeTestRule.onNoteTitle(3).assertTextContains("4", substring = false)
        composeTestRule.onNoteTitle(4).assertTextContains("A", substring = false)
        composeTestRule.onNoteTitle(5).assertTextContains("5", substring = false)
        testUtils.assertBook(
            "booky",
            "Booky Preface\n\n* 1\n** 2\n*** 3\n*** 4\n** A\n** 5\n* 6\n"
        )
    }

    @Test
    fun testNewNoteAfterMovingNotesAround() {
        testUtils.setupBook("notebook-1", "")
        ActivityScenario.launch(MainActivity::class.java)

        onView(withText("notebook-1")).perform(click())
        composeTestRule.waitUntilNoteCount(0)

        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("A"))
        onView(withId(R.id.done)).perform(click())
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("B"))
        onView(withId(R.id.done)).perform(click())
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("C"))
        onView(withId(R.id.done)).perform(click())
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("Parent 1"))
        onView(withId(R.id.done)).perform(click())

        composeTestRule.waitUntilNoteCount(4)

        // Move A, B, C under Parent 1
        repeat(3) {
            composeTestRule.onNoteRow(0).performLongClick()
            onActionItemClick(R.id.move, R.string.move)
            onView(withId(R.id.notes_action_move_down)).perform(click())
            onView(withId(R.id.notes_action_move_down)).perform(click())
            onView(withId(R.id.notes_action_move_down)).perform(click())
            onView(withId(R.id.notes_action_move_right)).perform(click())
            pressBack()
            pressBack()
        }

        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("Parent 2"))
        onView(withId(R.id.done)).perform(click())

        composeTestRule.waitUntilNoteCount(5)

        composeTestRule.onNoteRow(0).performLongClick()
        onActionItemClick(R.id.new_note, R.string.new_note)
        onView(withText(R.string.new_under)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("Note"))
        onView(withId(R.id.done)).perform(click())

        composeTestRule.waitUntilNoteCount(6)
        composeTestRule.onNoteTitle(4).assertTextContains("Note", substring = true)
    }
}
