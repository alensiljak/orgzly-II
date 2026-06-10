package com.orgzly.android.ui.notes

import android.content.Context
import androidx.annotation.ColorInt
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.util.TitleGenerator
import com.orgzly.android.ui.util.styledAttributes
import cc.alensiljak.orgzly.R

/**
 * Produces styled Org-mode note titles and decides content visibility.
 * Extracted from NoteItemViewBinder so that Compose screens can call generateTitle() and
 * shouldDisplayContent() without dragging in the legacy RecyclerView / ViewHolder stack.
 */
class NoteItemTitleGenerator(private val context: Context, private val inBook: Boolean) {

    var levelOffset: Int? = null

    private val titleGenerator: TitleGenerator

    init {
        val attrs = Attrs.obtain(context)
        val titleAttributes = TitleGenerator.TitleAttributes(
            attrs.todoColor,
            attrs.nextColor,
            attrs.waitColor,
            attrs.doneColor,
            attrs.customColor,
            attrs.postTitleTextSize,
            attrs.postTitleTextColor,
            AppPreferences.stateIcons(context)
        )
        titleGenerator = TitleGenerator(context, inBook, titleAttributes)
    }

    fun generateTitle(noteView: NoteView): CharSequence = titleGenerator.generateTitle(noteView)

    fun shouldDisplayContent(note: Note): Boolean = titleGenerator.shouldDisplayContent(note)

    companion object {
        const val ARCHIVE_TAG = "ARCHIVE"
    }

    private data class Attrs(
        @param:ColorInt val todoColor: Int,
        @param:ColorInt val nextColor: Int,
        @param:ColorInt val waitColor: Int,
        @param:ColorInt val doneColor: Int,
        @param:ColorInt val customColor: Int,
        val postTitleTextSize: Int,
        @param:ColorInt val postTitleTextColor: Int
    ) {
        companion object {
            @SuppressWarnings("ResourceType")
            fun obtain(context: Context): Attrs {
                return context.styledAttributes(
                    intArrayOf(
                        R.attr.item_head_state_todo_color,
                        R.attr.item_head_state_next_color,
                        R.attr.item_head_state_wait_color,
                        R.attr.item_head_state_done_color,
                        R.attr.item_head_state_custom_color,
                        R.attr.item_head_post_title_text_size,
                        android.R.attr.textColorTertiary
                    )
                ) { typedArray ->
                    Attrs(
                        typedArray.getColor(0, 0),
                        typedArray.getColor(1, 0),
                        typedArray.getColor(2, 0),
                        typedArray.getColor(3, 0),
                        typedArray.getColor(4, 0),
                        typedArray.getDimensionPixelSize(5, 0),
                        typedArray.getColor(6, 0)
                    )
                }
            }
        }
    }
}
