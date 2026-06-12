package com.orgzly.android.espresso.util

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput

// ── Note rows (whole clickable row) ──────────────────────────────────────────

fun ComposeTestRule.onNoteRows(): SemanticsNodeInteractionCollection =
    onAllNodesWithTag("note_row")

fun ComposeTestRule.onNoteRow(index: Int): SemanticsNodeInteraction =
    onAllNodesWithTag("note_row")[index]

fun ComposeTestRule.assertNoteRowCount(count: Int) =
    onAllNodesWithTag("note_row").assertCountEquals(count)

// ── Note titles (for text assertions) ────────────────────────────────────────

fun ComposeTestRule.onNoteTitles(): SemanticsNodeInteractionCollection =
    onAllNodesWithTag("note_title")

fun ComposeTestRule.onNoteTitle(index: Int): SemanticsNodeInteraction =
    onAllNodesWithTag("note_title")[index]

fun ComposeTestRule.assertNoteTitleCount(count: Int) =
    onAllNodesWithTag("note_title").assertCountEquals(count)

// ── Note content ─────────────────────────────────────────────────────────────

fun ComposeTestRule.onNoteContent(index: Int): SemanticsNodeInteraction =
    onAllNodesWithTag("note_content")[index]

// ── Book name (in search/agenda results) ─────────────────────────────────────

fun ComposeTestRule.onNoteBookName(index: Int): SemanticsNodeInteraction =
    onAllNodesWithTag("note_book_name")[index]

// ── Fold button ───────────────────────────────────────────────────────────────

fun ComposeTestRule.onNoteFoldButton(index: Int): SemanticsNodeInteraction =
    onAllNodesWithTag("note_fold_button")[index]

// ── Preface row (BookScreen only) ─────────────────────────────────────────────

fun ComposeTestRule.onPreface(): SemanticsNodeInteraction =
    onNodeWithTag("preface_row")

// ── Agenda items (headers + note rows) ───────────────────────────────────────

fun ComposeTestRule.onAgendaItems(): SemanticsNodeInteractionCollection =
    onAllNodesWithTag("agenda_item")

fun ComposeTestRule.onAgendaItem(index: Int): SemanticsNodeInteraction =
    onAllNodesWithTag("agenda_item")[index]

fun ComposeTestRule.assertAgendaItemCount(count: Int) =
    onAllNodesWithTag("agenda_item").assertCountEquals(count)

// ── Waiting helpers ───────────────────────────────────────────────────────────

/**
 * Block until at least [count] note_title nodes appear (or [timeoutMs] elapses).
 * Use when the list loads asynchronously and you need a minimum number of notes before asserting.
 */
fun ComposeTestRule.waitUntilNoteCount(count: Int, timeoutMs: Long = 5_000) {
    waitUntil(timeoutMs) {
        onAllNodesWithTag("note_title").fetchSemanticsNodes().size >= count
    }
}

/**
 * Block until exactly [count] note_title nodes are present (or [timeoutMs] elapses).
 * Use as a Compose replacement for [EspressoUtils.waitForExactAdapterCount].
 */
fun ComposeTestRule.waitUntilExactNoteCount(count: Int, timeoutMs: Long = 5_000) {
    waitUntil(timeoutMs) {
        onAllNodesWithTag("note_title").fetchSemanticsNodes().size == count
    }
}

/**
 * Block until exactly [count] agenda_item nodes are present (or [timeoutMs] elapses).
 */
fun ComposeTestRule.waitUntilAgendaItemCount(count: Int, timeoutMs: Long = 5_000) {
    waitUntil(timeoutMs) {
        onAllNodesWithTag("agenda_item").fetchSemanticsNodes().size == count
    }
}

// ── Book scroll helper ────────────────────────────────────────────────────────

/**
 * Scrolls the book's LazyColumn to the item at [lazyIndex].
 *
 * The LazyColumn item indices mirror the old RecyclerView positions when a preface is present:
 *   - lazyIndex 0  = preface row
 *   - lazyIndex 1  = first note  (was onNoteInBook(1))
 *   - lazyIndex n  = nth note    (was onNoteInBook(n))
 *
 * After scrolling, access the note via [onNoteRow]`(lazyIndex - 1)` or [onNoteTitle]`(lazyIndex - 1)`.
 */
fun ComposeTestRule.scrollBookToIndex(lazyIndex: Int) {
    onNodeWithTag("book_note_list").performScrollToIndex(lazyIndex)
}

// ── Touch input helpers ───────────────────────────────────────────────────────

/**
 * Long-click a Compose node. Mirrors [androidx.test.espresso.action.ViewActions.longClick].
 */
fun SemanticsNodeInteraction.performLongClick(): SemanticsNodeInteraction =
    performTouchInput { longClick() }
