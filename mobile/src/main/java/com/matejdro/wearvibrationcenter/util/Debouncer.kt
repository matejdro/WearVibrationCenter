package com.matejdro.wearvibrationcenter.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import si.inova.kotlinova.core.time.TimeProvider
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Class that will only execute passed task if a particular time span has been passed without
 * another task being added.
 *
 * When [triggerFirstImmediately] flag is set, task will be triggered immediately if there was
 * no other task within [debouncingTimeMs]. Otherwise, it will wait for another [debouncingTimeMs]
 * to ensure no further tasks are there.
 *
 * This is a copy of [si.inova.kotlinova.core.data.Debouncer], with ability to define debouncing
 * delay per-request
 */
class Debouncer(
    private val scope: CoroutineScope,
    private val timeProvider: TimeProvider,
    private val debouncingTimeMs: Long = 500L,
    private val targetContext: CoroutineContext = EmptyCoroutineContext,
) {
    private var previousJob: Job? = null
    private var lastStart = 0L

    fun executeDebouncing(
        debouncingTimeMs: Long = this.debouncingTimeMs,
        task: suspend () -> Unit
    ) {
        previousJob?.cancel()

        previousJob = scope.launch(targetContext) {
            delay(debouncingTimeMs)

            lastStart = System.currentTimeMillis()
            task()
        }
    }
}
