package com.orgzly.android.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cc.alensiljak.orgzly.R
import com.orgzly.org.datetime.OrgRepeater

@Composable
fun RepeaterPickerDialog(
    initialValue: String,
    onSet: (OrgRepeater) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val typeLabels = remember { context.resources.getStringArray(R.array.repeater_types).toList() }
    val typeDescriptions = remember { context.resources.getStringArray(R.array.repeater_types_description).toList() }
    val repeater = remember { OrgRepeater.parse(initialValue) }

    PeriodWithTypePickerDialog(
        title = context.getString(R.string.repeater_dialog_title),
        description = context.getString(R.string.repeater_description),
        typeLabels = typeLabels,
        typeDescriptions = typeDescriptions,
        initialTypeIndex = repeater.type.ordinal,
        initialValue = repeater.value,
        maxValue = maxOf(100, repeater.value),
        initialUnitIndex = repeater.unit.ordinal,
        onConfirm = { typeIndex, value, unitIndex ->
            val type = when (typeIndex) {
                0 -> OrgRepeater.Type.CUMULATE
                1 -> OrgRepeater.Type.CATCH_UP
                2 -> OrgRepeater.Type.RESTART
                else -> throw IllegalArgumentException("Unexpected type index ($typeIndex)")
            }
            onSet(OrgRepeater(type, value, ordinalToUnit(unitIndex)))
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}
