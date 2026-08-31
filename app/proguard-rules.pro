# minifyEnabled is false, so none of this is active today. It is here because JavaMail
# resolves its providers reflectively by fully-qualified class name, read from the
# META-INF/javamail.* descriptors. If minification is ever turned on without these rules,
# IMAP breaks at runtime with no build-time warning.
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class javax.activation.** { *; }
-dontwarn java.awt.**
-dontwarn javax.security.**
