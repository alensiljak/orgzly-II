package com.orgzly.android.ui.dialogs

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import cc.alensiljak.orgzly.BuildConfig
import cc.alensiljak.orgzly.R
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.TimeType
import com.orgzly.android.ui.compose.base.OrgzlyBootstrap
import com.orgzly.android.ui.util.KeyboardUtils
import com.orgzly.android.ui.views.richtext.RichText
import com.orgzly.android.util.LogUtils
import com.orgzly.android.util.UserTimeFormatter
import com.orgzly.org.datetime.OrgDateTime
import java.util.Calendar
import java.util.TreeSet
import kotlin.properties.Delegates

class TimestampDialogFragment : DialogFragment() {
    private var listener: OnDateTimeSetListener? = null
    private var originViewId: Int by Delegates.notNull()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)

        check(parentFragment is OnDateTimeSetListener) {
            "Fragment $parentFragment must implement ${OnDateTimeSetListener::class.java}"
        }
        listener = parentFragment as OnDateTimeSetListener?

        val args = requireArguments()
        originViewId = args.getInt(ARG_ORIGIN_VIEW_ID)
        val timeType = TimeType.valueOf(requireNotNull(args.getString(ARG_TIME_TYPE)))
        val noteIds = TreeSet(args.getLongArray(ARG_NOTE_IDS)?.toList() ?: emptyList())
        val dateTimeString = args.getString(ARG_TIME)

        val factory = TimestampDialogViewModelFactory.getInstance(timeType, dateTimeString)
        val viewModel = ViewModelProvider(this, factory)[TimestampDialogViewModel::class.java]

        if (timestampIsInline()) {
            viewModel.setIsActive(AppPreferences.lastInlineTimestampWasActive(context))
        }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OrgzlyBootstrap {
                    TimestampDialog(
                        viewModel = viewModel,
                        isInline = timestampIsInline(),
                        onSet = {
                            val time = viewModel.getOrgDateTime()
                            if (time != null) {
                                AppPreferences.lastInlineTimestampWasActive(context, time.isActive)
                            }
                            listener?.onDateTimeSet(originViewId, noteIds, time)
                            dismiss()
                        },
                        onClear = {
                            listener?.onDateTimeSet(originViewId, noteIds, null)
                            dismiss()
                        },
                        onCancel = {
                            listener?.onDateTimeAborted(originViewId, noteIds)
                            dismiss()
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onResume() {
        super.onResume()
        KeyboardUtils.closeSoftKeyboard(activity)
    }

    override fun onDetach() {
        super.onDetach()
        if (timestampIsInline()) {
            val view = requireParentFragment().view?.findViewById<RichText>(originViewId)
            if (view != null) KeyboardUtils.openSoftKeyboard(view)
        }
    }

    private fun timestampIsInline(): Boolean {
        return originViewId == R.id.content_edit || originViewId == R.id.title_edit
    }

    interface OnDateTimeSetListener {
        fun onDateTimeSet(originViewId: Int, noteIds: TreeSet<Long>, time: OrgDateTime?)
        fun onDateTimeAborted(originViewId: Int, noteIds: TreeSet<Long>)
    }

    companion object {
        val FRAGMENT_TAG: String = TimestampDialogFragment::class.java.name

        private val TAG = TimestampDialogFragment::class.java.name

        private const val ARG_ORIGIN_VIEW_ID = "origin_view_id"
        private const val ARG_TIME_TYPE = "time_type"
        private const val ARG_NOTE_IDS = "note_ids"
        private const val ARG_TIME = "time"

        fun getInstance(
            originViewId: Int,
            timeType: TimeType,
            noteIds: Set<Long>,
            time: OrgDateTime?,
        ): TimestampDialogFragment {
            val fragment = TimestampDialogFragment()

            val bundle = Bundle()
            bundle.putInt(ARG_ORIGIN_VIEW_ID, originViewId)
            bundle.putString(ARG_TIME_TYPE, timeType.name)
            bundle.putLongArray(ARG_NOTE_IDS, toArray(noteIds))
            if (time != null) bundle.putString(ARG_TIME, time.toString())

            fragment.arguments = bundle
            return fragment
        }

        private fun toArray(set: Set<Long>): LongArray {
            var i = 0
            val result = LongArray(set.size)
            for (e in set) result[i++] = e
            return result
        }
    }
}

@Composable
private fun TimestampDialog(
    viewModel: TimestampDialogViewModel,
    isInline: Boolean,
    onSet: () -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
) {
    val dateTime by viewModel.dateTime.observeAsState()
    val dt = dateTime ?: return

    val context = LocalContext.current
    val userTimeFormatter = remember { UserTimeFormatter(context) }
    val is24Hour = remember { DateFormat.is24HourFormat(context) }

    var showRepeaterPicker by rememberSaveable { mutableStateOf(false) }
    var showDelayPicker by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = userTimeFormatter.formatAll(viewModel.getOrgDateTime(dt)),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // ── Date ──────────────────────────────────────────────────
                Text(
                    text = stringResource(R.string.timestamp_dialog_day),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        DatePickerDialog(context, { _, year, month, day ->
                            viewModel.set(year, month, day)
                        }, dt.year, dt.month, dt.day).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(userTimeFormatter.formatDate(dt))
                }

                // ── Date shortcuts ────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(
                        onClick = {
                            Calendar.getInstance().let {
                                viewModel.set(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.today), maxLines = 1) }

                    Button(
                        onClick = {
                            Calendar.getInstance().apply { add(Calendar.DATE, 1) }.let {
                                viewModel.set(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.tomorrow), maxLines = 1) }

                    Button(
                        onClick = {
                            Calendar.getInstance().apply {
                                set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                                add(Calendar.DATE, 7)
                            }.let {
                                viewModel.set(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.next_week), maxLines = 1) }
                }

                // ── Time + End time (side by side) ────────────────────────
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Start time
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.timestamp_dialog_time),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    TimePickerDialog(context, { _, h, m ->
                                        viewModel.setTime(h, m)
                                    }, dt.hour, dt.minute, is24Hour).show()
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(userTimeFormatter.formatTime(dt), maxLines = 1) }
                            Checkbox(
                                checked = dt.isTimeUsed,
                                onCheckedChange = { viewModel.setIsTimeUsed(it) },
                            )
                        }
                    }

                    // End time
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.timestamp_dialog_end_time),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    TimePickerDialog(context, { _, h, m ->
                                        viewModel.setEndTime(h, m)
                                    }, dt.endHour, dt.endMinute, is24Hour).show()
                                },
                                modifier = Modifier.weight(1f),
                                enabled = dt.isTimeUsed,
                            ) { Text(userTimeFormatter.formatEndTime(dt), maxLines = 1) }
                            Checkbox(
                                checked = dt.isEndTimeUsed,
                                onCheckedChange = { viewModel.setIsEndTimeUsed(it) },
                                enabled = dt.isTimeUsed,
                            )
                        }
                    }
                }

                // ── Repeater ──────────────────────────────────────────────
                Text(
                    text = stringResource(R.string.timestamp_dialog_repeater),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { showRepeaterPicker = true },
                        modifier = Modifier.weight(1f),
                    ) { Text(userTimeFormatter.formatRepeater(dt), maxLines = 1) }
                    Checkbox(
                        checked = dt.isRepeaterUsed,
                        onCheckedChange = { viewModel.setIsRepeaterUsed(it) },
                    )
                    Spacer(Modifier.weight(1f))
                }

                // ── Delay / Warning period ────────────────────────────────
                val delayLabel = if (viewModel.timeType == TimeType.SCHEDULED) {
                    stringResource(R.string.timestamp_dialog_delay)
                } else {
                    stringResource(R.string.timestamp_dialog_warning_period)
                }
                Text(
                    text = delayLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { showDelayPicker = true },
                        modifier = Modifier.weight(1f),
                    ) { Text(userTimeFormatter.formatDelay(dt), maxLines = 1) }
                    Checkbox(
                        checked = dt.isDelayUsed,
                        onCheckedChange = { viewModel.setIsDelayUsed(it) },
                    )
                    Spacer(Modifier.weight(1f))
                }

                // ── Active / Inactive (inline timestamps only) ────────────
                if (isInline) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.is_active_label),
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(
                            checked = dt.isActive,
                            onCheckedChange = { viewModel.setIsActive(it) },
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSet) { Text(stringResource(R.string.set)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

    // ── Sub-dialogs shown on top of the timestamp dialog ──────────────────
    if (showRepeaterPicker) {
        RepeaterPickerDialog(
            initialValue = viewModel.getRepeaterString(),
            onSet = { viewModel.set(it) },
            onDismiss = { showRepeaterPicker = false },
        )
    }

    if (showDelayPicker) {
        if (viewModel.timeType == TimeType.SCHEDULED) {
            DelayPickerDialog(
                initialValue = viewModel.getDelayString(),
                onSet = { viewModel.set(it) },
                onDismiss = { showDelayPicker = false },
            )
        } else {
            WarningPeriodPickerDialog(
                initialValue = viewModel.getDelayString(),
                onSet = { viewModel.set(it) },
                onDismiss = { showDelayPicker = false },
            )
        }
    }
}
