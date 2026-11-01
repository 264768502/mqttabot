package com.mqttabot.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DontMemoize
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleStartEffect
import com.mqttabot.mqtt.Observable

/** Current value of an [Observable] as Compose state; listens while the screen is started. */
@Composable
fun <T> Observable<T>.observeAsState(): State<T> {
    // Observables are app-wide singletons, so neither needs a key.
    val state = remember { mutableStateOf(value) }
    // Not memoized: the receiver never changes, and the key check would be dead code.
    LifecycleStartEffect(Unit) @DontMemoize {
        val listener: (T) -> Unit = { state.value = it }
        observe(listener)
        onStopOrDispose { remove(listener) }
    }
    return state
}
