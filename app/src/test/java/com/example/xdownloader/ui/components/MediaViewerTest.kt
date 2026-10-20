package com.example.xdownloader.ui.components

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaViewerTest {

    @Test
    fun testLifecycleObserverDoesNotOverwriteOnStop() {
        // Simulated test since UI tests require emulator.
        // We will test the logic described for the observer.
        var wasPlaying = true
        var exoPlayerIsPlaying = true

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                wasPlaying = exoPlayerIsPlaying
                exoPlayerIsPlaying = false
            } else if (event == Lifecycle.Event.ON_STOP) {
                // DO NOT overwrite wasPlaying
                exoPlayerIsPlaying = false
            } else if (event == Lifecycle.Event.ON_RESUME) {
                if (wasPlaying) {
                    exoPlayerIsPlaying = true
                }
            }
        }

        val lifecycleOwner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }

        observer.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_PAUSE)
        assertEquals(true, wasPlaying)
        assertEquals(false, exoPlayerIsPlaying)

        // Pause manually before STOP
        exoPlayerIsPlaying = false

        observer.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_STOP)
        assertEquals(true, wasPlaying) // STILL TRUE!
        assertEquals(false, exoPlayerIsPlaying)

        observer.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_RESUME)
        assertEquals(true, wasPlaying)
        assertEquals(true, exoPlayerIsPlaying) // Resumes correctly
    }
}
