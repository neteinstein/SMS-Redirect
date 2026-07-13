package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.analytics.CrashReporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCompatibilityCrashHandlerTest {
    private class RecordingCrashReporter : CrashReporter {
        val recordedExceptions = mutableListOf<Throwable>()
        val loggedMessages = mutableListOf<String>()

        override fun recordException(throwable: Throwable) {
            recordedExceptions += throwable
        }

        override fun log(message: String) {
            loggedMessages += message
        }
    }

    private class RecordingUncaughtExceptionHandler : Thread.UncaughtExceptionHandler {
        var handled: Pair<Thread, Throwable>? = null

        override fun uncaughtException(
            thread: Thread,
            throwable: Throwable,
        ) {
            handled = thread to throwable
        }
    }

    @Test
    fun `NoSuchFieldError is recorded as non-fatal and terminates instead of delegating`() {
        val crashReporter = RecordingCrashReporter()
        val previousHandler = RecordingUncaughtExceptionHandler()
        var terminated = false
        val handler =
            DeviceCompatibilityCrashHandler(
                crashReporter = crashReporter,
                previousHandler = previousHandler,
                terminateProcess = { terminated = true },
            )
        val error = NoSuchFieldError("fontWeightAdjustment")

        handler.uncaughtException(Thread.currentThread(), error)

        assertEquals(listOf(error), crashReporter.recordedExceptions)
        assertTrue(terminated)
        assertNull(previousHandler.handled)
    }

    @Test
    fun `NoSuchMethodError is recorded as non-fatal and terminates instead of delegating`() {
        val crashReporter = RecordingCrashReporter()
        val previousHandler = RecordingUncaughtExceptionHandler()
        var terminated = false
        val handler =
            DeviceCompatibilityCrashHandler(
                crashReporter = crashReporter,
                previousHandler = previousHandler,
                terminateProcess = { terminated = true },
            )
        val error = NoSuchMethodError("of")

        handler.uncaughtException(Thread.currentThread(), error)

        assertEquals(listOf(error), crashReporter.recordedExceptions)
        assertTrue(terminated)
        assertNull(previousHandler.handled)
    }

    @Test
    fun `other throwables are forwarded to the previous handler unchanged and do not terminate`() {
        val crashReporter = RecordingCrashReporter()
        val previousHandler = RecordingUncaughtExceptionHandler()
        var terminated = false
        val handler =
            DeviceCompatibilityCrashHandler(
                crashReporter = crashReporter,
                previousHandler = previousHandler,
                terminateProcess = { terminated = true },
            )
        val thread = Thread.currentThread()
        val error = IllegalStateException("a real app bug")

        handler.uncaughtException(thread, error)

        assertTrue(crashReporter.recordedExceptions.isEmpty())
        assertEquals(thread to error, previousHandler.handled)
        assertFalse(terminated)
    }

    @Test
    fun `crash reporter failures do not prevent termination`() {
        val throwingCrashReporter =
            object : CrashReporter {
                override fun recordException(throwable: Throwable): Nothing = error("Crashlytics unavailable")

                override fun log(message: String): Nothing = error("Crashlytics unavailable")
            }
        var terminated = false
        val handler =
            DeviceCompatibilityCrashHandler(
                crashReporter = throwingCrashReporter,
                previousHandler = null,
                terminateProcess = { terminated = true },
            )

        handler.uncaughtException(Thread.currentThread(), NoSuchFieldError("fontWeightAdjustment"))

        assertTrue(terminated)
    }
}
