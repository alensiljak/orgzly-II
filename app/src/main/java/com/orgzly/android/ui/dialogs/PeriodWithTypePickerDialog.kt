package com.orgzly.android.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.alensiljak.orgzly.R
import com.orgzly.android.ui.compose.WheelNumberPicker
import com.orgzly.org.datetime.OrgInterval

/**
 * Base composable for the repeater, delay and warning-period picker dialogs.
 * Shows up to three drum-roll wheels: optional [typeLabels] wheel, value wheel, unit wheel.
 */
@Composable
fun PeriodWithTypePickerDialog(
    title: String,
    description: String,
    typeLabels: List<String>?,
    typeDescriptions: List<String>?,
    initialTypeIndex: Int,
    initialValue: Int,
    maxValue: Int = 100,
    initialUnitIndex: Int,
    onConfirm: (typeIndex: Int, value: Int, unitIndex: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var typeIndex by remember { mutableIntStateOf(initialTypeIndex) }
    var value by remember { mutableIntStateOf(initialValue) }
    var unitIndex by remember { mutableIntStateOf(initialUnitIndex) }

    val context = LocalContext.current
    val timeUnits = remember { context.resources.getStringArray(R.array.time_units).toList() }
    val valueItems = remember(maxValue) { (1..maxValue).map { it.toString() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = description,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (typeLabels != null) {
                        WheelNumberPicker(
                            items = typeLabels,
                            selectedIndex = typeIndex,
                            onSelectionChanged = { typeIndex = it },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(16.dp))
                    }

                    WheelNumberPicker(
                        items = valueItems,
                        selectedIndex = value - 1,
                        onSelectionChanged = { value = it + 1 },
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(Modifier.width(16.dp))

                    WheelNumberPicker(
                        items = timeUnits,
                        selectedIndex = unitIndex,
                        onSelectionChanged = { unitIndex = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (!typeDescriptions.isNullOrEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = typeDescriptions[typeIndex],
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(typeIndex, value, unitIndex) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

internal fun ordinalToUnit(unitIndex: Int): OrgInterval.Unit = when (unitIndex) {
    0 -> OrgInterval.Unit.HOUR
    1 -> OrgInterval.Unit.DAY
    2 -> OrgInterval.Unit.WEEK
    3 -> OrgInterval.Unit.MONTH
    4 -> OrgInterval.Unit.YEAR
    else -> throw IllegalArgumentException("Unexpected unit index ($unitIndex)")
}
