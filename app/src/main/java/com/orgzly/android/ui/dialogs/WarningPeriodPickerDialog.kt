package com.orgzly.android.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cc.alensiljak.orgzly.R
import com.orgzly.org.datetime.OrgDelay

@Composable
fun WarningPeriodPickerDialog(
    initialValue: String,
    onSet: (OrgDelay) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val delay = remember { OrgDelay.parse(initialValue) }

    PeriodWithTypePickerDialog(
        title = context.getString(R.string.warning_period_dialog_title),
        description = context.getString(R.string.warning_period_description),
        typeLabels = null,
        typeDescriptions = null,
        initialTypeIndex = 0,
        initialValue = delay.value,
        maxValue = maxOf(100, delay.value),
        initialUnitIndex = delay.unit.ordinal,
        onConfirm = { _, value, unitIndex ->
            onSet(OrgDelay(OrgDelay.Type.ALL, value, ordinalToUnit(unitIndex)))
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}
