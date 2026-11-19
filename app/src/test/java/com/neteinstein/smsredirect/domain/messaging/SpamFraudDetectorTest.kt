package com.neteinstein.smsredirect.domain.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamFraudDetectorTest {
    private val detector = SpamFraudDetector()

    private fun suspicious(text: String) = detector.assess(text).suspicious

    @Test
    fun `parcel fee scam with a risky domain is flagged`() {
        val result = detector.assess("CTT: A sua encomenda esta retida. Pague a taxa de 1,99 EUR em ctt-pt.info/pagar")
        assertTrue(result.suspicious)
        assertTrue(SpamSignal.RISKY_DOMAIN_LINK in result.signals)
        assertTrue(SpamSignal.DELIVERY_OR_FEE in result.signals)
    }

    @Test
    fun `account verification through a shortened link is flagged`() {
        val result = detector.assess("Your account has been suspended. Verify your details at bit.ly/3xYz")
        assertTrue(result.suspicious)
        assertEquals(
            setOf(SpamSignal.SHORTENED_LINK, SpamSignal.URGENCY, SpamSignal.ACCOUNT_OR_CREDENTIALS),
            result.signals,
        )
    }

    @Test
    fun `a disguised link is flagged even without scam wording`() {
        assertTrue(suspicious("Hi, see https://paypal.com.account-check.net/"))
        assertTrue(suspicious("Hi, see https://www.bank.com@evil.example.net/"))
        assertTrue(suspicious("Hi, see http://xn--pple-43d.com/"))
        assertTrue(suspicious("Hi, see http://203.0.113.7/claim"))
    }

    @Test
    fun `integer IPs and look-alike letters are flagged`() {
        assertTrue(suspicious("Hi, see http://3232235777/x"))
        assertTrue(suspicious("Hi, see https://p\u0430ypal.com/login"))
    }

    @Test
    fun `a huge input is assessed without failing`() {
        assertFalse(suspicious("a.".repeat(50_000) + "1"))
    }

    @Test
    fun `prize wording in portuguese with accents is flagged with a link`() {
        assertTrue(suspicious("Parabéns! Ganhou um PRÉMIO. Reclame em https://premios-portugal.com/r"))
    }

    @Test
    fun `an ordinary link from a stranger is not flagged`() {
        assertFalse(suspicious("Olha este vídeo www.youtube.com/watch?v=abc"))
        assertFalse(suspicious("A sua encomenda chega hoje. Acompanhe em www.ctt.pt"))
    }

    @Test
    fun `country second-level domains are not mistaken for disguised links`() {
        assertFalse(suspicious("Consulte https://www.portaldasfinancas.gov.pt e https://loja.com.br"))
    }

    @Test
    fun `codes and plain messages are not flagged`() {
        assertFalse(suspicious("Your verification code is 123456"))
        assertFalse(suspicious("Chego às 19h, até já!"))
        assertFalse(suspicious(""))
    }

    @Test
    fun `without a link it takes three kinds of scam wording`() {
        assertFalse(suspicious("URGENT: your card is blocked"))
        assertTrue(suspicious("URGENT: your account is blocked. Confirm your PIN to claim your refund"))
    }

    @Test
    fun `normalize lower-cases and strips accents`() {
        assertEquals("premio alfandega", SpamFraudDetector.normalize("Prémio ALFÂNDEGA"))
    }
}
