package com.neteinstein.smsredirect.domain.messaging

import java.text.Normalizer

/** One sign that a received message may be spam or a scam. Computed, never persisted. */
enum class SpamSignal {
    /** A link through a URL shortener, which hides where it goes. */
    SHORTENED_LINK,

    /** A link to a bare IP address instead of a domain. */
    IP_ADDRESS_LINK,

    /** A link built to look like another site: punycode, `bank.com@evil.net`, `bank.com.evil.net`. */
    DECEPTIVE_LINK,

    /** A link on a domain ending that is cheap and mostly used for throwaway sites. */
    RISKY_DOMAIN_LINK,

    /** "Urgent", "suspended", "last notice"... */
    URGENCY,

    /** Asks to verify an account, or for a password, PIN or card details. */
    ACCOUNT_OR_CREDENTIALS,

    /** A prize, refund, reward or investment. */
    MONEY_OR_PRIZE,

    /** A parcel, customs, toll or fine to pay. */
    DELIVERY_OR_FEE,
}

data class SpamAssessment(
    val signals: Set<SpamSignal>,
    val suspicious: Boolean,
) {
    companion object {
        val SAFE = SpamAssessment(emptySet(), suspicious = false)
    }
}

/**
 * On-device spam and fraud check of a received message's text. Nothing is sent anywhere: the
 * check is a few rules over the links and words in the message.
 *
 * Links are what turn a scam message into harm, so a message with a link is suspicious when the
 * link hides or disguises where it goes, when the text asks for account details or promises
 * money, or when it combines two kinds of scam wording (say urgency and a parcel fee). Without a
 * link it takes three kinds at once. Messages from contacts and trusted senders are not checked
 * at all (see `AssessIncomingMessageUseCase`).
 */
class SpamFraudDetector {
    fun assess(text: String): SpamAssessment {
        if (text.isBlank()) return SpamAssessment.SAFE
        val links = MessageLinks.find(text)
        val linkSignals = links.flatMap(::linkSignals).toSet()
        val wordSignals = wordSignals(normalize(text))
        val signals = linkSignals + wordSignals
        val suspicious =
            if (links.isNotEmpty()) {
                linkSignals.isNotEmpty() || wordSignals.any { it in STRONG_WORDS } || wordSignals.size >= 2
            } else {
                wordSignals.size >= WORDS_ONLY_THRESHOLD
            }
        return SpamAssessment(signals, suspicious)
    }

    private fun linkSignals(link: MessageLink): List<SpamSignal> {
        val host = link.host
        val labels = host.split('.')
        val tld = labels.last()
        // `gov.pt`, `com.br`, `co.uk`: a second-level ending under a country code is one suffix.
        val suffixLength = if (tld.length == 2 && labels.size >= 3 && labels[labels.lastIndex - 1] in SECOND_LEVEL) 2 else 1
        return buildList {
            if (host in SHORTENERS) add(SpamSignal.SHORTENED_LINK)
            if (IPV4.matches(host) || host.startsWith("[")) add(SpamSignal.IP_ADDRESS_LINK)
            val deceptive =
                labels.any { it.startsWith("xn--") } ||
                    MessageLinks.hasUserInfo(link.url) ||
                    labels.dropLast(suffixLength).any { it in GENERIC_TLDS } ||
                    labels.size >= MAX_HONEST_LABELS
            if (deceptive) add(SpamSignal.DECEPTIVE_LINK)
            if (tld in RISKY_TLDS) add(SpamSignal.RISKY_DOMAIN_LINK)
        }
    }

    private fun wordSignals(text: String): Set<SpamSignal> = WORDS.filterValues { pattern -> pattern.containsMatchIn(text) }.keys

    companion object {
        private const val WORDS_ONLY_THRESHOLD = 3

        /** Next to a link, either of these is enough: what phishing asks for and what it promises. */
        private val STRONG_WORDS = setOf(SpamSignal.ACCOUNT_OR_CREDENTIALS, SpamSignal.MONEY_OR_PRIZE)

        /** Hosts this deep (`a.b.c.d.example.com`) are almost always disguising the real domain. */
        private const val MAX_HONEST_LABELS = 5

        private val IPV4 = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")

        /** Lower case, accents removed: "Prémio" and "premio" match the same word. */
        fun normalize(text: String): String =
            Normalizer
                .normalize(text.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("""\p{Mn}+"""), "")

        val SHORTENERS: Set<String> =
            setOf(
                "bit.ly",
                "tinyurl.com",
                "t.co",
                "goo.gl",
                "is.gd",
                "ow.ly",
                "buff.ly",
                "cutt.ly",
                "rb.gy",
                "shorturl.at",
                "tiny.cc",
                "t.ly",
                "rebrand.ly",
                "s.id",
                "bl.ink",
                "short.io",
                "v.gd",
                "x.co",
                "lnkd.in",
                "qrco.de",
                "tiny.one",
                "shorturl.me",
                "urlz.fr",
                "surl.li",
                "u.to",
                "clck.ru",
            )

        /** Cheap endings that spam and phishing campaigns use far more than anyone else. */
        val RISKY_TLDS: Set<String> =
            setOf(
                "xyz",
                "top",
                "icu",
                "buzz",
                "cyou",
                "sbs",
                "cfd",
                "bond",
                "click",
                "link",
                "live",
                "online",
                "site",
                "shop",
                "store",
                "rest",
                "monster",
                "quest",
                "lol",
                "mom",
                "tk",
                "ml",
                "ga",
                "cf",
                "gq",
                "info",
                "win",
                "vip",
                "loan",
                "work",
            )

        /** `paypal.com.secure-login.net`: a generic ending in the middle of a host. */
        private val GENERIC_TLDS = setOf("com", "net", "org", "gov", "edu")

        private val SECOND_LEVEL = GENERIC_TLDS + setOf("co", "ac", "gob", "mil")

        private fun words(vararg phrases: String): Regex =
            Regex(phrases.joinToString("|", prefix = """(?<![\p{L}\p{N}])(?:""", postfix = ")"))

        /** English and Portuguese, the app's two languages. Accent-free: see [normalize]. */
        private val WORDS: Map<SpamSignal, Regex> =
            mapOf(
                SpamSignal.URGENCY to
                    words(
                        "urgent",
                        "immediately",
                        "within 24",
                        "within 48",
                        "last chance",
                        "final notice",
                        "act now",
                        "suspended",
                        "blocked",
                        "locked",
                        "deactivated",
                        "will be closed",
                        "urgente",
                        "imediatamente",
                        "nas proximas 24",
                        "ultimo aviso",
                        "ultima oportunidade",
                        "suspens",
                        "bloquead",
                        "desativad",
                        "sera encerrad",
                    ),
                SpamSignal.ACCOUNT_OR_CREDENTIALS to
                    words(
                        "(?:verify|confirm|update|validate) your (?:account|identity|details|information|card|payment|billing)",
                        "password",
                        "pin\\b",
                        "cvv",
                        "login",
                        "log in",
                        "sign in",
                        "bank details",
                        "card details",
                        "security code",
                        "(?:verifique|confirme|atualize|valide) (?:a sua|os seus|o seu|sua|seus) " +
                            "(?:conta|identidade|dados|cartao|pagamento)",
                        "palavra-passe",
                        "senha",
                        "dados bancarios",
                        "dados do cartao",
                        "codigo de seguranca",
                        "iban",
                        "homebanking",
                    ),
                SpamSignal.MONEY_OR_PRIZE to
                    words(
                        "you won",
                        "you have won",
                        "winner",
                        "prize",
                        "reward",
                        "gift card",
                        "refund",
                        "cashback",
                        "claim",
                        "crypto",
                        "bitcoin",
                        "investment",
                        "unclaimed",
                        "ganhou",
                        "premio",
                        "vencedor",
                        "reembolso",
                        "devolucao",
                        "recompensa",
                        "cartao presente",
                        "reclame",
                        "investimento",
                    ),
                SpamSignal.DELIVERY_OR_FEE to
                    words(
                        "parcel",
                        "package",
                        "redelivery",
                        "shipment",
                        "customs",
                        "unpaid",
                        "outstanding",
                        "toll",
                        "fine\\b",
                        "small fee",
                        "delivery fee",
                        "shipping fee",
                        "encomenda",
                        "alfandega",
                        "portes",
                        "taxa",
                        "pagamento pendente",
                        "portagem",
                        "multa",
                        "reagendar a entrega",
                    ),
            )
    }
}
