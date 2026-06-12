package com.orgzly.android.espresso

import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions.open
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import cc.alensiljak.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.onNoteRow
import com.orgzly.android.espresso.util.performLongClick
import com.orgzly.android.espresso.util.waitUntilNoteCount
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ActionModeTest : OrgzlyTest() {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val mRetryTestRule = RetryTestRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        testUtils.setupBook(
            "book-one",
            "First book used for testing\n" +
                    "* Note A.\n" +
                    "** Note B.\n" +
                    "* TODO Note C.\n" +
                    "SCHEDULED: <2014-01-01>\n" +
                    "** Note D.\n" +
                    "*** TODO Note E.\n" +
                    ""
        )

        testUtils.setupBook(
            "book-two",
            "Sample book used for tests\n" +
                    "* Note #1.\n" +
                    "* Note #2.\n" +
                    "** TODO Note #3.\n" +
                    "** Note #4.\n" +
                    "*** DONE Note #5.\n" +
                    "CLOSED: [2014-06-03 Tue 13:34]\n" +
                    "**** Note #6.\n" +
                    "** Note #7.\n" +
                    "* DONE Note #8.\n" +
                    "CLOSED: [2014-06-03 Tue 3:34]\n" +
                    "**** Note #9.\n" +
                    "SCHEDULED: <2014-05-26 Mon>\n" +
                    "** Note #10.\n" +
                    ""
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)

        onView(allOf(withText("book-one"), isDisplayed())).perform(click())
        composeTestRule.waitUntilNoteCount(1)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        scenario.close()
    }

    @Test
    fun testQueryFragmentCabShouldBeOpenedOnNoteLongClick() {
        onView(withId(R.id.drawer_layout)).perform(open())
        onView(allOf(withText("Scheduled"), isDescendantOfA(withId(R.id.drawer_navigation_view)))).perform(click())
        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteRow(1).performLongClick()
    }

    @Test
    fun testCabStaysOpenOnRotation() {
        scenario.onActivity { activity ->
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        }

        // book-one has preface at pos 0; Note C. is at pos 3 → row index 2
        composeTestRule.onNoteRow(2).performLongClick()

        scenario.onActivity { activity ->
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        }

        onView(withId(R.id.toggle_state)).check(matches(isDisplayed()))
        // TODO: Check *the same* note is selected.
    }

    @Test
    fun testCabStaysOpenOnRotationInQueryFragment() {
        scenario.onActivity { activity ->
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        }

        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withText("Scheduled")).perform(click())
        composeTestRule.waitUntilNoteCount(1)
        composeTestRule.onNoteRow(1).performLongClick()

        scenario.onActivity { activity ->
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        }

        // TODO: Check *the same* note is selected.

        SystemClock.sleep(500)
        scenario.onActivity { activity ->
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        }

        onView(withId(R.id.toggle_state)).check(matches(isDisplayed()))
    }

    @Test
    fun testBackPressClosesDrawer() {
        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withId(R.id.drawer_navigation_view)).check(matches(isDisplayed()))
        pressBack()
        onView(withId(R.id.drawer_navigation_view)).check(matches(not(isDisplayed())))
    }
}
