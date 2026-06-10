package com.orgzly.android.ui.compose.notelist

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.orgzly.android.ui.views.style.LinkSpan

/**
 * A clickable region inside an [OrgFormattedText], mapping a character range to the original
 * Android [span] (one of [com.orgzly.android.ui.views.style.CheckboxSpan],
 * [com.orgzly.android.ui.views.style.DrawerMarkerSpan] or a [LinkSpan] subclass).
 */
data class OrgClickable(val start: Int, val end: Int, val span: Any)

/**
 * Result of converting an Android [Spanned] (as produced by
 * [com.orgzly.android.util.OrgFormatter] / [com.orgzly.android.ui.util.TitleGenerator]) into a
 * Compose [AnnotatedString], preserving the visual styling and exposing the interactive spans.
 */
data class OrgFormattedText(
    val text: AnnotatedString,
    val clickables: List<OrgClickable>,
) {
    /** Returns the first clickable span covering [offset], or null. */
    fun clickableAt(offset: Int): Any? =
        clickables.firstOrNull { offset >= it.start && offset < it.end }?.span
}

/**
 * Converts a [CharSequence] produced by the legacy Org rendering pipeline into a Compose
 * [AnnotatedString]. The parsing/styling logic is reused as-is (we do not reimplement
 * OrgFormatter); this only maps the resulting Android spans onto Compose [SpanStyle]s and records
 * the clickable spans so a composable can dispatch taps.
 */
fun orgSpannedToAnnotatedString(
    source: CharSequence,
    density: Density,
    linkColor: Color,
): OrgFormattedText {
    if (source !is Spanned) {
        return OrgFormattedText(AnnotatedString(source.toString()), emptyList())
    }

    val clickables = mutableListOf<OrgClickable>()

    val annotated = buildAnnotatedString {
        append(source.toString())

        for (span in source.getSpans(0, source.length, Any::class.java)) {
            val start = source.getSpanStart(span)
            val end = source.getSpanEnd(span)

            if (start < 0 || end <= start || end > length) {
                continue
            }

            when (span) {
                is ClickableSpan -> {
                    clickables.add(OrgClickable(start, end, span))
                    // Match the View's link appearance (coloured + underlined). Checkbox and
                    // drawer markers render no special style (their updateDrawState is a no-op).
                    if (span is LinkSpan) {
                        addStyle(
                            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                            start, end
                        )
                    }
                }

                is ForegroundColorSpan ->
                    addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)

                is AbsoluteSizeSpan -> {
                    val size: TextUnit = if (span.dip) {
                        with(density) { span.size.dp.toSp() }
                    } else {
                        with(density) { span.size.toSp() }
                    }
                    addStyle(SpanStyle(fontSize = size), start, end)
                }

                is StyleSpan -> when (span.style) {
                    Typeface.BOLD ->
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    Typeface.ITALIC ->
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    Typeface.BOLD_ITALIC ->
                        addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                            start, end
                        )
                }

                is UnderlineSpan ->
                    addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)

                is StrikethroughSpan ->
                    addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)

                is TypefaceSpan ->
                    // All TypefaceSpans used by the Org pipeline are "monospace".
                    addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, end)
            }
        }
    }

    return OrgFormattedText(annotated, clickables)
}

/**
 * A [androidx.compose.material3.Text]-like composable that renders an [OrgFormattedText] and
 * dispatches taps on its clickable spans (checkbox / drawer / link). Taps that don't hit a
 * clickable span fall through to [onPlainTap]; long-presses always go to [onLongPress].
 */
@Composable
fun ClickableOrgText(
    formatted: OrgFormattedText,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
    onSpanClick: (Any) -> Unit,
    onPlainTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    androidx.compose.material3.Text(
        text = formatted.text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        onTextLayout = { layoutResult = it },
        modifier = modifier.pointerInput(formatted) {
            detectTapGestures(
                onLongPress = { onLongPress() },
                onTap = { pos ->
                    val layout = layoutResult
                    val span = if (layout != null) {
                        val offset = layout.getOffsetForPosition(pos)
                        formatted.clickableAt(offset)
                    } else {
                        null
                    }
                    if (span != null) {
                        onSpanClick(span)
                    } else {
                        onPlainTap()
                    }
                },
            )
        },
    )
}
