# R8 rules for the release build (minifyEnabled + shrinkResources are on).
#
# Everything below pins something that is resolved by *name* at runtime and therefore cannot
# survive obfuscation on its own. R8 reports none of these as errors -- a missing rule here
# shows up only as a runtime failure in a signed release build, so add a rule whenever new
# code starts depending on a class, field or enum constant name.

# --- Crash reporting -------------------------------------------------------------------
# Crashlytics de-obfuscates with the mapping file the Crashlytics Gradle plugin uploads
# during assembleRelease/bundleRelease, but only if the frames still carry file/line info.
# Without these, every release stack trace loses its line numbers.
-keepattributes SourceFile,LineNumberTable
# Collapse the original file names (they leak the source layout); the mapping file still
# resolves frames back to the real file and line.
-renamesourcefileattribute SourceFile

# --- JavaMail --------------------------------------------------------------------------
# JavaMail resolves its providers reflectively by fully-qualified class name, read from the
# META-INF/javamail.* descriptors (which the packaging block merges rather than excludes).
# Without these, Session.getStore("imaps") fails at runtime with NoSuchProviderException and
# no build-time warning.
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class javax.activation.** { *; }
# JavaMailImapTransport surfaces MessagingException subclass simple names as failure reasons
# (never the exception text, which can echo the app password) -- the keeps above are what
# keep those names meaningful.
-dontwarn java.awt.**
-dontwarn javax.security.**

# --- Enums persisted by name -----------------------------------------------------------
# RepeatType and LandingScreen are stored as strings in Room and in the exported backup JSON,
# and MainTab is persisted by rememberSaveable; all three are read back with Enum.valueOf.
# Renaming the constants (or R8's enum unboxing) would silently turn every stored value into
# the fallback -- e.g. every repeating schedule would come back as RepeatType.NONE.
-keepclassmembers enum com.neteinstein.smsredirect.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
