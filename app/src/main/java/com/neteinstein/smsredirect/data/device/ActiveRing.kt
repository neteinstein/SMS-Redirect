package com.neteinstein.smsredirect.data.device

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Coordinates rings so the process plays one at a time. A new ring, or [stop] (the notification's
 * Stop button), ends the one before it. It holds the user's alarm volume from before the first of
 * a run of overlapping rings, so the last one to finish can put it back.
 *
 * Starting and finishing run under one lock, together with the volume and notification changes
 * passed in. Otherwise a ring finishing at the moment another starts could save the temporary
 * maximum as the user's volume, or cancel the newer ring's notification.
 */
open class RingCoordinator {
    private val current = MutableStateFlow<Long?>(null)
    private var counter = 0L
    private var savedVolume: Int? = null

    /** Saves the volume unless an earlier ring already holds it, then runs [start]. */
    @Synchronized
    fun begin(
        currentVolume: () -> Int,
        start: () -> Unit,
    ): Long {
        if (savedVolume == null) savedVolume = currentVolume()
        counter++
        current.value = counter
        start()
        return counter
    }

    fun stop() {
        current.value = null
    }

    suspend fun awaitEnd(token: Long) {
        current.first { it != token }
    }

    /**
     * Ends ring [token]. Unless a newer ring is playing (and now owns the volume and the
     * notification), runs [restore] with the saved volume and returns true.
     */
    @Synchronized
    fun end(
        token: Long,
        restore: (Int) -> Unit,
    ): Boolean {
        val playing = current.value
        if (playing != null && playing != token) return false
        current.value = null
        val volume = savedVolume
        savedVolume = null
        if (volume != null) restore(volume)
        return true
    }
}

/** The process-wide coordinator; [StopRingingReceiver][com.neteinstein.smsredirect.StopRingingReceiver] stops it. */
object ActiveRing : RingCoordinator()
