package com.orgzly.android.ui.compose.notelist

import android.graphics.Typeface
import android.text.Spanned
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import cc.alensiljak.orgzly.R
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.db.entity.toList
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.notes.NoteItemViewBinder
import com.orgzly.android.ui.notes.NoteItemViewBinder.Companion.ARCHIVE_TAG
import com.orgzly.android.ui.views.style.CheckboxSpan
import com.orgzly.android.ui.views.style.DrawerMarkerSpan
import com.orgzly.android.ui.views.style.DrawerSpan
import com.orgzly.android.util.OrgFormatter
import com.orgzly.android.util.UserTimeFormatter
import com.orgzly.org.datetime.OrgRange
import android.text.SpannableStringBuilder

private val INDENT_WIDTH = 16.dp

/**
 * A single note row in the book (notebook tree) view, rendered entirely in Compose.
 *
 * The styled title and content are produced by the existing Org rendering pipeline
 * ([NoteItemViewBinder.generateTitle] / [OrgFormatter.parse]) and converted to Compose
 * [androidx.compose.ui.text.AnnotatedString]s — so all org formatting, state colours, tags and
 * links match the legacy view exactly. The three in-row interactions (checkbox toggle, drawer
 * fold, link follow) are preserved.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteItemContent(
    noteView: NoteView,
    levelOffset: Int?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFold: (Long) -> Unit,
    onToggleFoldSubtree: (Long) -> Unit,
    onCheckboxToggle: (Note, CheckboxSpan) -> Unit,
    onLinkClick: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val note = noteView.note

    val binder = remember(context) { NoteItemViewBinder(context, inBook = true) }
    binder.levelOffset = levelOffset

    val linkColor = MaterialTheme.colorScheme.primary

    // Done / archived items are dimmed, matching NoteItemViewBinder.setupAlpha.
    val isDone = note.state != null && AppPreferences.doneKeywordsSet(context).contains(note.state)
    val isArchived = note.tags.toList().contains(ARCHIVE_TAG) ||
            noteView.getInheritedTagsList().contains(ARCHIVE_TAG)
    val contentAlpha = if (isDone || isArchived) 0.45f else 1f

    val level = maxOf(0, note.position.level - 1 - (levelOffset ?: 0))

    val descendants = note.position.descendantsCount
    val contentFoldable = note.hasContent() &&
            AppPreferences.isNotesContentFoldable(context) &&
            AppPreferences.isNotesContentDisplayedInList(context)
    val isFoldable = descendants > 0 || contentFoldable

    val rowBackground = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBackground)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Indentation columns with vertical guide lines.
        IndentColumns(level)

        // Bullet
        Bullet(
            descendantsCount = descendants,
            isFolded = note.position.isFolded,
            isFoldable = isFoldable,
            onClick = { onToggleFold(note.id) },
            onLongClick = { onToggleFoldSubtree(note.id) },
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 4.dp),
        )

        // Title + planning + content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
        ) {
            val titleFormatted = remember(noteView, levelOffset) {
                orgSpannedToAnnotatedString(binder.generateTitle(noteView), density, linkColor)
            }

            ClickableOrgText(
                formatted = titleFormatted,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 17.sp),
                color = LocalContentColor.current.copy(alpha = contentAlpha),
                maxLines = Int.MAX_VALUE,
                onSpanClick = { span -> onLinkClick(span) },
                onPlainTap = onClick,
                onLongPress = onLongClick,
                modifier = Modifier.fillMaxWidth(),
            )

            PlanningTimes(noteView = noteView, alpha = contentAlpha)

            if (note.hasContent() && binder.shouldDisplayContent(note)) {
                NoteContent(
                    note = note,
                    alpha = contentAlpha,
                    onCheckboxToggle = onCheckboxToggle,
                    onLinkClick = onLinkClick,
                    onPlainTap = onClick,
                    onLongPress = onLongClick,
                )
            }
        }

        // Folding button (matches the right-side fold affordance in item_head.xml)
        if (isFoldable) {
            Text(
                text = if (note.position.isFolded) {
                    stringResource(R.string.unfold_button_character)
                } else {
                    stringResource(R.string.fold_button_character)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 4.dp, start = 8.dp, end = 8.dp)
                    .combinedClickable(
                        onClick = { onToggleFold(note.id) },
                        onLongClick = { onToggleFoldSubtree(note.id) },
                    ),
            )
        }
    }
}

@Composable
private fun IndentColumns(level: Int) {
    if (level <= 0) return
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Row(modifier = Modifier.fillMaxHeight()) {
        repeat(level) {
            Box(
                modifier = Modifier
                    .width(INDENT_WIDTH)
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(lineColor),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bullet(
    descendantsCount: Int,
    isFolded: Boolean,
    isFoldable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Drawn with Canvas because the legacy bullet drawables are layer-list/shape XML, which
    // Compose's painterResource cannot load.
    val showFoldedRing = descendantsCount > 0 && isFolded
    val innerColor = MaterialTheme.colorScheme.onSecondaryContainer
    val ringColor = MaterialTheme.colorScheme.secondaryContainer
    val clickable = if (isFoldable) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(16.dp)
            .then(clickable),
        contentAlignment = Alignment.Center,
    ) {
        if (showFoldedRing) {
            Canvas(modifier = Modifier.fillMaxSize()) { drawCircle(ringColor) }
        }
        Canvas(modifier = Modifier.size(8.dp)) { drawCircle(innerColor) }
    }
}

@Composable
private fun PlanningTimes(noteView: NoteView, alpha: Float) {
    val context = LocalContext.current
    if (!AppPreferences.displayPlanning(context)) return

    val formatter = remember(context) { UserTimeFormatter(context) }

    @Composable
    fun TimeRow(value: String?, iconRes: Int) {
        if (value == null) return
        val text = remember(value) { formatter.formatAll(OrgRange.parse(value)).toString() }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = LocalContentColor.current.copy(alpha = 0.7f * alpha),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.7f * alpha),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }

    TimeRow(noteView.scheduledRangeString, R.drawable.ic_today)
    TimeRow(noteView.deadlineRangeString, R.drawable.ic_alarm)
    TimeRow(noteView.eventString, R.drawable.ic_access_time)
    TimeRow(noteView.closedRangeString, R.drawable.ic_check_circle_outline)
}

@Composable
private fun NoteContent(
    note: Note,
    alpha: Float,
    onCheckboxToggle: (Note, CheckboxSpan) -> Unit,
    onLinkClick: (Any) -> Unit,
    onPlainTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val linkColor = MaterialTheme.colorScheme.primary

    // Local, mutable rendering of the content so drawer folding can be toggled in place
    // (visual only), mirroring RichText.toggleDrawer.
    var contentSpanned by remember(note.id, note.content) {
        mutableStateOf<Spanned>(OrgFormatter.parse(note.content.orEmpty(), context))
    }

    val formatted = remember(contentSpanned) {
        orgSpannedToAnnotatedString(contentSpanned, density, linkColor)
    }

    ClickableOrgText(
        formatted = formatted,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        color = LocalContentColor.current.copy(alpha = alpha),
        maxLines = Int.MAX_VALUE,
        onSpanClick = { span ->
            when (span) {
                is CheckboxSpan -> onCheckboxToggle(note, span)
                is DrawerMarkerSpan -> contentSpanned = toggleDrawer(contentSpanned, span)
                else -> onLinkClick(span)
            }
        },
        onPlainTap = onPlainTap,
        onLongPress = onLongPress,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
    )
}

/**
 * Folds/unfolds the drawer whose marker is [markerSpan], returning a new [Spanned].
 * Port of RichText.toggleDrawer for the read-only Compose content view.
 */
private fun toggleDrawer(spanned: Spanned, markerSpan: DrawerMarkerSpan): Spanned {
    val pos = spanned.getSpanStart(markerSpan)
    val drawerSpan = spanned.getSpans(pos, pos, DrawerSpan::class.java).firstOrNull()
        ?: return spanned

    val drawerStart = spanned.getSpanStart(drawerSpan)
    val drawerEnd = spanned.getSpanEnd(drawerSpan)

    val builder = SpannableStringBuilder(spanned)

    val replacement = OrgFormatter.drawerSpanned(
        drawerSpan.name, drawerSpan.content, isFolded = !drawerSpan.isFolded)

    builder.removeSpan(drawerSpan)
    builder.removeSpan(markerSpan)
    builder.replace(drawerStart, drawerEnd, replacement)

    return builder
}
