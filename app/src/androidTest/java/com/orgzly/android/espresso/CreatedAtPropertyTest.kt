package com.orgzly.android.espresso

import android.widget.EditText
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
import com.orgzly.android.espresso.util.EspressoUtils.clickSetting
import com.orgzly.android.espresso.util.EspressoUtils.onActionItemClick
import com.orgzly.android.espresso.util.EspressoUtils.replaceTextCloseKeyboard
import com.orgzly.android.espresso.util.EspressoUtils.searchForTextCloseKeyboard
import com.orgzly.android.espresso.util.onNoteTitle
import com.orgzly.android.espresso.util.waitUntilExactNoteCount
import com.orgzly.android.espresso.util.waitUntilNoteCount
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CreatedAtPropertyTest : OrgzlyTest() {

    private lateinit var scenario: ActivityScenario<MainActivity>

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val mRetryTestRule = RetryTestRule()

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        val createdProperty = context.getString(R.string.created_property_name)

        testUtils.setupBook(
            "book-a",
            "* Note [a-1]\n" +
                    ":PROPERTIES:\n" +
                    ":$createdProperty: [2018-01-05]\n" +
                    ":ADDED: [2018-01-01]\n" +
                    ":END:\n" +
                    "SCHEDULED: <2018-01-01>\n" +
                    "* Note [a-2]\n" +
                    ":PROPERTIES:\n" +
                    ":$createdProperty: [2018-01-02]\n" +
                    ":ADDED: [2018-01-04]\n" +
                    ":END:\n" +
                    "SCHEDULED: <2014-01-01>\n"
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    @Throws(Exception::class)
    override fun tearDown() {
        super.tearDown()
        scenario.close()
    }

    @Test
    fun testCondition() {
        enableCreatedAt()

        searchForTextCloseKeyboard("cr.le.today")
        composeTestRule.waitUntilExactNoteCount(2)
    }

    @Test
    fun testSortOrder() {
        enableCreatedAt()

        searchForTextCloseKeyboard("o.cr")
        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteTitle(0).assertTextContains("Note [a-2]", substring = true)

        searchForTextCloseKeyboard(".o.cr")
        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteTitle(0).assertTextContains("Note [a-1]", substring = true)
    }

    @Test
    fun testChangeCreatedAtPropertyResultsShouldBeReordered() {
        searchForTextCloseKeyboard("o.cr")
        composeTestRule.waitUntilNoteCount(2)
        composeTestRule.onNoteTitle(0).assertTextContains("Note [a-1]", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note [a-2]", substring = true)

        enableCreatedAt()

        composeTestRule.waitUntilNoteCount(2)
        composeTestRule.onNoteTitle(0).assertTextContains("Note [a-2]", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note [a-1]", substring = true)

        changeCreatedAtProperty("ADDED")

        composeTestRule.waitUntilNoteCount(2)
        composeTestRule.onNoteTitle(0).assertTextContains("Note [a-1]", substring = true)
        composeTestRule.onNoteTitle(1).assertTextContains("Note [a-2]", substring = true)
    }

    @Test
    fun testNewNote() {
        onView(withText("book-a")).perform(click())
        composeTestRule.waitUntilNoteCount(2)

        enableCreatedAt()

        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("new note created by test"))
        onView(withId(R.id.done)).perform(click())

        composeTestRule.waitUntilNoteCount(3)
        composeTestRule.onNoteTitle(2).assertTextContains("new note created by test", substring = true)

        searchForTextCloseKeyboard("o.cr")
        composeTestRule.waitUntilNoteCount(3)
        composeTestRule.onNoteTitle(0).assertTextContains("Note [a-2]", substring = true)

        searchForTextCloseKeyboard(".o.cr")
        composeTestRule.waitUntilNoteCount(3)
        composeTestRule.onNoteTitle(0).assertTextContains("new note created by test", substring = true)
    }

    private fun enableCreatedAt() {
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.sync)
        clickSetting(R.string.use_created_at_property)
        onView(withText(R.string.yes)).perform(click())
        pressBack()
        pressBack()
    }

    private fun changeCreatedAtProperty(propName: String) {
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.sync)
        clickSetting(R.string.created_at_property)
        onView(instanceOf(EditText::class.java)).perform(*replaceTextCloseKeyboard(propName))
        onView(withText(android.R.string.ok)).perform(click())
        onView(withText(R.string.yes)).perform(click())
        pressBack()
        pressBack()
    }
}
