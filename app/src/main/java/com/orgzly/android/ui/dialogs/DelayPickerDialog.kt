package com.orgzly.android.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cc.alensiljak.orgzly.R
import com.orgzly.org.datetime.OrgDelay

@Composable
fun DelayPickerDialog(
    initialValue: String,
    onSet: (OrgDelay) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val typeLabels = remember { context.resources.getStringArray(R.array.delay_types).toList() }
    val typeDescriptions = remember { context.resources.getStringArray(R.array.delay_types_description).toList() }
    val delay = remember { OrgDelay.parse(initialValue) }

    PeriodWithTypePickerDialog(
        title = context.getString(R.string.delay_dialog_title),
        description = context.getString(R.string.delay_description),
        typeLabels = typeLabels,
        typeDescriptions = typeDescriptions,
        initialTypeIndex = delay.type.ordinal,
        initialValue = delay.value,
        maxValue = maxOf(100, delay.value),
        initialUnitIndex = delay.unit.ordinal,
        onConfirm = { typeIndex, value, unitIndex ->
            val type = when (typeIndex) {
                0 -> OrgDelay.Type.ALL
                1 -> OrgDelay.Type.FIRST_ONLY
                else -> throw IllegalArgumentException("Unexpected type index ($typeIndex)")
            }
            onSet(OrgDelay(type, value, ordinalToUnit(unitIndex)))
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}
