package com.neteinstein.smsredirect

import java.net.URLDecoder

/*
 * Pure helpers for "send a message" requests from other apps, kept free of android.net.Uri so
 * they are unit-testable (see the isReturnDefaultValues trap in CLAUDE.md).
 */

/**
 * Whether a URI handed in by another app may be attached to an outgoing message.
 * [ComposeSmsActivity] and [MainActivity] are exported, so a caller could name a URI only *this*
 * app can read -- another conversation's MMS part, or one of our own cache files -- hoping the
 * user taps Send without looking. Those are refused; everything else the caller can read anyway.
 *
 * Any authority with a user-info part is refused outright: `content://0@mms/part/1` reaches the
 * MMS provider through ContentResolver's user-qualified authorities.
 */
internal fun isShareableAttachment(
    scheme: String?,
    authority: String?,
    ownPackage: String,
): Boolean {
    if (scheme != "content" || authority.isNullOrBlank()) return false
    if ('@' in authority) return false
    return authority.lowercase() !in setOf("sms", "mms", "mms-sms", "telephony", "$ownPackage.fileprovider")
}

/**
 * The `body` parameter of an `sms:`/`smsto:`/`mms:`/`mmsto:` link, from its *encoded*
 * scheme-specific part (`+351912345678?body=Ol%C3%A1`). These URIs are opaque, so
 * `Uri.getQueryParameter` throws on them; and the decoded part would turn an encoded `&` in the
 * text into a parameter separator.
 */
internal fun composeLinkBody(encodedSchemeSpecificPart: String?): String? {
    val query = encodedSchemeSpecificPart?.substringAfter('?', missingDelimiterValue = "") ?: return null
    if (query.isEmpty()) return null
    val value =
        query
            .split('&')
            .firstOrNull { it.substringBefore('=').equals("body", ignoreCase = true) }
            ?.substringAfter('=', missingDelimiterValue = "")
            ?: return null
    // URLDecoder turns "+" into a space; in these links "+" is a literal plus (a phone number).
    return runCatching { URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8) }.getOrNull()
}
