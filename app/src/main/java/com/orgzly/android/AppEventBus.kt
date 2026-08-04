package com.orgzly.android

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * In-process pub/sub replacing LocalBroadcastManager. Events are plain [Intent]s (action +
 * extras) so the existing AppIntent.ACTION_* constants and their extras can be reused as-is.
 */
object AppEventBus {
    private val _events = MutableSharedFlow<Intent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<Intent> = _events.asSharedFlow()

    @JvmStatic
    fun send(intent: Intent) {
        _events.tryEmit(intent)
    }

    @JvmStatic
    fun send(action: String) {
        send(Intent(action))
    }

    fun interface Listener {
        fun onEvent(intent: Intent)
    }

    /**
     * Subscribes [listener] to events whose action is in [actions], active only while [owner]'s
     * lifecycle is at least [minState]. Collection restarts/stops automatically with the
     * lifecycle, so no manual unregistration is needed.
     */
    @JvmStatic
    @JvmOverloads
    fun observe(
        owner: LifecycleOwner,
        actions: Set<String>,
        listener: Listener,
        minState: Lifecycle.State = Lifecycle.State.STARTED
    ) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(minState) {
                events.filter { it.action in actions }.collect { listener.onEvent(it) }
            }
        }
    }
}
