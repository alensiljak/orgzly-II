package com.orgzly.android.espresso;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.DrawerActions.open;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;

import androidx.documentfile.provider.DocumentFile;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.intent.Intents;

import cc.alensiljak.orgzly.R;
import com.orgzly.android.LocalStorage;
import com.orgzly.android.OrgzlyTest;
import com.orgzly.android.RetryTestRule;
import com.orgzly.android.ui.main.MainActivity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

/**
 * Migrated from the legacy SavedSearchesFragment to the Compose SavedSearchesScreen.
 *
 * Selector guide:
 *  - Long-press item     → withText("Agenda") or any search name
 *  - Move up button      → withContentDescription(getString(R.string.up))
 *  - Move down button    → withContentDescription(getString(R.string.down))
 *  - Overflow menu       → withContentDescription(getString(R.string.more_options))
 *  - Export/Import items → withText(R.string.export / R.string.import_)
 */
public class SavedSearchesFragmentTest extends OrgzlyTest {
    @Rule
    public RetryTestRule mRetryTestRule = new RetryTestRule();

    @Before
    public void setUp() throws Exception {
        super.setUp();

        testUtils.setupBook("book-one", "Preface\n* Note A.\n");

        ActivityScenario.launch(MainActivity.class);

        onView(withId(R.id.drawer_layout)).perform(open());
        onView(withText(R.string.searches)).perform(click());
    }

    @Test
    public void testActionModeWhenSelectingSavedSearchThenOpeningBook() {
        // Long-press "Agenda" (first default saved search) to enter selection mode
        onView(withText("Agenda")).perform(longClick());
        onView(withId(R.id.drawer_layout)).perform(open());
        onView(allOf(withText("book-one"), isDescendantOfA(withId(R.id.drawer_navigation_view)))).perform(click());
        // Move-up button should no longer be visible after navigating away
        onView(withContentDescription(context.getString(R.string.up))).check(doesNotExist());
    }

    @Test
    public void testMovingSavedSearchDown() {
        onView(withText("Agenda")).perform(longClick());
        onView(withContentDescription(context.getString(R.string.down))).perform(click());
    }

    @Test
    public void testExportSavedSearches() throws IOException {
        Intents.init();

        // Uri to get back after sending Intent.ACTION_CREATE_DOCUMENT
        DocumentFile file = DocumentFile.fromFile(
                new LocalStorage(context).downloadsDirectory("searches.json"));
        Instrumentation.ActivityResult result = new Instrumentation.ActivityResult(
                Activity.RESULT_OK, new Intent().setData(file.getUri()));

        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(result);

        // Open overflow menu and click Export
        onView(withContentDescription(context.getString(R.string.more_options))).perform(click());
        onView(withText(R.string.export)).perform(click());

        onView(withText(
                context.getResources().getQuantityString(R.plurals.exported_searches, 4, 4)))
                .check(matches(isDisplayed()));

        Intents.release();

        file.delete();
    }
}
