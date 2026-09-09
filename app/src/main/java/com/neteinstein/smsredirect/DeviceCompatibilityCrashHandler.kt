package com.neteinstein.smsredirect

import android.os.Process
import com.neteinstein.smsredirect.domain.analytics.CrashReporter
import kotlin.system.exitProcess

/**
 * Some OEM builds report a higher `Build.VERSION.SDK_INT` than the APIs their framework.jar
 * actually implements. When that happens, platform/AndroidX code compiled against the real
 * Android SDK throws [NoSuchFieldError] or [NoSuchMethodError] deep inside library internals
 * we don't own - e.g. Jetpack Compose's font resolver reading the Android 12+
 * `Configuration.fontWeightAdjustment` field on every `ComposeView` attach. Those errors
 * surface on the main thread's `Looper` during frame dispatch, not inside any of our own call
 * stacks, so there is no app-code call site where we could try/catch them - the only
 * available hook is a process-wide [Thread.UncaughtExceptionHandler].
 *
 * There is no way to make the device actually implement the missing API, so this handler's
 * job is narrow: recognize this specific, unrecoverable class of device/OS bug, report it as
 * a non-fatal event (so it stays visible without being conflated with real regressions or
 * skewing crash-free metrics for something outside our control), and terminate the process
 * cleanly so the system's "App has stopped" dialog never shows. [NoSuchFieldError] and
 * [NoSuchMethodError] can only be thrown when compiled bytecode references a field/method
 * that doesn't exist in the class actually loaded at runtime - our own Kotlin code is verified
 * against our declared dependency versions at compile time, and R8 rewrites call sites and
 * targets together, so any instance of these two error types reaching here is a platform/OEM
 * firmware incompatibility rather than an app bug we could fix. The one thing that could
 * invalidate that reasoning is a missing keep rule on something resolved reflectively; see
 * proguard-rules.pro before adding such a dependency.
 * Every other throwable is forwarded to [previousHandler] unchanged, preserving normal fatal
 * crash reporting.
 */
class DeviceCompatibilityCrashHandler(
    private val crashReporter: CrashReporter,
    private val previousHandler: Thread.UncaughtExceptionHandler?,
    private val terminateProcess: () -> Unit = {
        Process.killProcess(Process.myPid())
        exitProcess(1)
    },
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        if (!isKnownDeviceFrameworkMismatch(throwable)) {
            previousHandler?.uncaughtException(thread, throwable)
            return
        }

        runCatching {
            crashReporter.log(
                "Ignoring known OEM/platform API mismatch (device reports a higher API " +
                    "level than it implements): ${throwable.javaClass.name}: ${throwable.message}",
            )
            crashReporter.recordException(throwable)
        }
        terminateProcess()
    }

    companion object {
        private fun isKnownDeviceFrameworkMismatch(throwable: Throwable): Boolean =
            throwable is NoSuchFieldError || throwable is NoSuchMethodError

        /** Installs the handler, chaining to whatever handler is currently registered. */
        fun install(crashReporter: CrashReporter) {
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(
                DeviceCompatibilityCrashHandler(crashReporter, previousHandler),
            )
        }
    }
}
