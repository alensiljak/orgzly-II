package com.orgzly.android.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalBaseDp = compositionLocalOf { 16.dp }

val Number.rdp: Dp
    @Composable get() = LocalBaseDp.current * this.toFloat()
