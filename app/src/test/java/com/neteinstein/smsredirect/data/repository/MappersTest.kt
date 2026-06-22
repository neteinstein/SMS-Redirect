package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.ForwardHistoryEntity
import com.neteinstein.smsredirect.data.local.RuleEntity
import com.neteinstein.smsredirect.domain.model.ForwardHistory
import com.neteinstein.smsredirect.domain.model.Rule
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {
    // --- RuleMappers ---

    @Test
    fun `RuleEntity toDomain maps all fields`() {
        val entity =
            RuleEntity(
                id = 5,
                name = "TestRule",
                senderText = "BANK",
                phoneNumber = "+1555",
                targetNumber = "+351999",
                sortOrder = 3,
            )

        val domain = entity.toDomain()

        assertEquals(5L, domain.id)
        assertEquals("TestRule", domain.name)
        assertEquals("BANK", domain.senderText)
        assertEquals("+1555", domain.phoneNumber)
        assertEquals("+351999", domain.targetNumber)
        assertEquals(3, domain.sortOrder)
    }

    @Test
    fun `Rule toEntity maps all fields`() {
        val rule =
            Rule(
                id = 7,
                name = "MyRule",
                senderText = "SHOP",
                phoneNumber = "+44123",
                targetNumber = "+351888",
                sortOrder = 2,
            )

        val entity = rule.toEntity()

        assertEquals(7L, entity.id)
        assertEquals("MyRule", entity.name)
        assertEquals("SHOP", entity.senderText)
        assertEquals("+44123", entity.phoneNumber)
        assertEquals("+351888", entity.targetNumber)
        assertEquals(2, entity.sortOrder)
    }

    @Test
    fun `Rule roundtrip preserves all fields`() {
        val original =
            Rule(
                id = 10,
                name = "Roundtrip",
                senderText = "X",
                phoneNumber = "Y",
                targetNumber = "Z",
                sortOrder = 99,
            )

        val roundtripped = original.toEntity().toDomain()

        assertEquals(original, roundtripped)
    }

    @Test
    fun `Rule with defaults roundtrips correctly`() {
        val original = Rule()
        val roundtripped = original.toEntity().toDomain()
        assertEquals(original, roundtripped)
    }

    // --- ForwardHistoryMappers ---

    @Test
    fun `ForwardHistoryEntity toDomain maps all fields`() {
        val entity =
            ForwardHistoryEntity(
                id = 3,
                sender = "BANK",
                targetNumber = "+351999",
                messageBody = "Hello World",
                ruleName = "BankRule",
                forwardedAtEpochMs = 1719000000000L,
            )

        val domain = entity.toDomain()

        assertEquals(3L, domain.id)
        assertEquals("BANK", domain.sender)
        assertEquals("+351999", domain.targetNumber)
        assertEquals("Hello World", domain.messageBody)
        assertEquals("BankRule", domain.ruleName)
        assertEquals(1719000000000L, domain.forwardedAtEpochMs)
    }

    @Test
    fun `ForwardHistory toEntity maps all fields`() {
        val history =
            ForwardHistory(
                id = 8,
                sender = "SHOP",
                targetNumber = "+44123",
                messageBody = "Order confirmed",
                ruleName = "ShopRule",
                forwardedAtEpochMs = 1719100000000L,
            )

        val entity = history.toEntity()

        assertEquals(8L, entity.id)
        assertEquals("SHOP", entity.sender)
        assertEquals("+44123", entity.targetNumber)
        assertEquals("Order confirmed", entity.messageBody)
        assertEquals("ShopRule", entity.ruleName)
        assertEquals(1719100000000L, entity.forwardedAtEpochMs)
    }

    @Test
    fun `ForwardHistory roundtrip preserves all fields`() {
        val original =
            ForwardHistory(
                id = 15,
                sender = "Test",
                targetNumber = "999",
                messageBody = "body",
                ruleName = "Rule",
                forwardedAtEpochMs = 123456789L,
            )

        val roundtripped = original.toEntity().toDomain()

        assertEquals(original, roundtripped)
    }

    @Test
    fun `ForwardHistory with defaults roundtrips correctly`() {
        val original = ForwardHistory()
        val roundtripped = original.toEntity().toDomain()
        assertEquals(original, roundtripped)
    }
}
