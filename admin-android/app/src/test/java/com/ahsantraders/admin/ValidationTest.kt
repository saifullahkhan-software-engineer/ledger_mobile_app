package com.ahsantraders.admin

import com.ahsantraders.admin.data.*
import org.junit.Assert.*
import org.junit.Test

class ValidationTest {
    private val chicken = Business("c", "Chicken", "CHICKEN", 100, 10000, "10", 100000)
    private val lpg = chicken.copy(id = "l", type = "LPG")
    private val broiler = chicken.copy(id = "b", type = "BROILER")
    private val batch = Batch("batch", "b", "Batch A", 10, 2, 10, 10000, 100000, 0, 0, "0", "ACTIVE", "2026-01-01", null)
    private val date = "2026-01-02"

    @Test fun exactMoneyConversion() {
        assertEquals(125050L, moneyInput("1250.50"))
        assertEquals(1L, moneyInput("0.01"))
        assertEquals(0L, moneyInput("0", true))
        assertEquals("Rs. 1,250.50", rupees(125050))
    }
    @Test fun rejectsInvalidMoney() {
        listOf("-1", "0", "0.001", "NaN", "Infinity", "10000000000.01", "abc").forEach { text ->
            assertThrows(IllegalArgumentException::class.java) { moneyInput(text) }
        }
    }
    @Test fun imageServingUrlsResolveAgainstConfiguredServer() {
        // Relative database-image URLs must resolve against the configured
        // backend, never a hardcoded host; absolute (legacy external) URLs pass through.
        assertEquals("http://10.0.2.2:8000/api/v1/images/abc", absoluteUrl("http://10.0.2.2:8000", "/api/v1/images/abc"))
        assertEquals("https://api.example.com/api/v1/images/abc", absoluteUrl("https://api.example.com/", "/api/v1/images/abc"))
        assertEquals("https://cdn.example.com/x.png", absoluteUrl("http://10.0.2.2:8000", "https://cdn.example.com/x.png"))
        assertEquals("", absoluteUrl("http://10.0.2.2:8000", null))
        assertEquals("", absoluteUrl("http://10.0.2.2:8000", ""))
    }
    @Test fun validatesCylindersAndKg() {
        assertEquals("1.125", quantityInput("1.125"))
        assertEquals("2", quantityInput("2", whole = true))
        assertThrows(IllegalArgumentException::class.java) { quantityInput("1.5", whole = true) }
        assertThrows(IllegalArgumentException::class.java) { quantityInput("1.0001") }
        assertThrows(IllegalArgumentException::class.java) { quantityInput("-1") }
    }
    @Test fun saleMatchesApiContractAndPreservesUnicode() {
        val result = formBody(Draft(FormKind.SALE, mapOf("date" to date, "quantity" to "2.5", "amount" to "1500.25", "note" to "  احسن sale  ")), chicken, null, date)
        assertEquals(150025L, result["amount"].asLong)
        assertEquals("2.5", result["quantity"].asString)
        assertEquals("  احسن sale  ", result["note"].asString)
        assertEquals("SALE", result["kind"].asString)
    }
    @Test fun lpgSaleRequiresChannel() {
        val draft = Draft(FormKind.SALE, mapOf("date" to date, "quantity" to "2", "amount" to "300"))
        assertThrows(IllegalArgumentException::class.java) { formBody(draft, lpg, null, date) }
        assertEquals("RETAIL", formBody(draft.copy(values = draft.values + ("channel" to "RETAIL")), lpg, null, date)["channel"].asString)
    }
    @Test fun rejectsBackdatedDailyEntry() {
        assertThrows(IllegalArgumentException::class.java) { formBody(Draft(FormKind.EXPENSE, mapOf("date" to "2026-01-01", "amount" to "1")), chicken, null, date) }
    }
    @Test fun batchMortalityCannotExceedRemainingBirds() {
        val draft = Draft(FormKind.BATCH_LOG, mapOf("date" to date, "feed" to "1", "deaths" to "9", "amount" to "0"))
        assertThrows(IllegalArgumentException::class.java) { formBody(draft, broiler, batch, date) }
        assertEquals(8, formBody(draft.copy(values = draft.values + ("deaths" to "8")), broiler, batch, date)["deaths"].asInt)
    }
    @Test fun harvestUsesOnlyNewExpenses() {
        val body = formBody(Draft(FormKind.HARVEST, mapOf("quantity" to "100", "price" to "150.50", "amount" to "25")), broiler, batch, date)
        assertEquals(15050L, body["price_per_kg"].asLong)
        assertEquals(2500L, body["additional_expense"].asLong)
        assertEquals(setOf("yield_kg", "price_per_kg", "additional_expense"), body.keySet())
    }
    @Test fun immutableBatchCannotBeLoggedOrHarvested() {
        assertThrows(IllegalArgumentException::class.java) { formBody(Draft(FormKind.HARVEST), broiler, batch.copy(status = "HARVESTED"), date) }
    }
    @Test fun passwordConfirmationRequired() {
        assertThrows(IllegalArgumentException::class.java) { formBody(Draft(FormKind.PASSWORD, mapOf("old_password" to "old", "new_password" to "NewPassword123", "confirm" to "different")), null, null, date) }
    }
    @Test fun rejectsCredentialsOrPathInServerUrl() {
        assertEquals("http://10.0.2.2:8000/", normalizeServer("http://10.0.2.2:8000", true))
        listOf("http://user:password@example.com", "https://example.com/docs", "https://example.com?token=x", "ftp://example.com").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { normalizeServer(value, true) }
        }
        assertThrows(IllegalArgumentException::class.java) { normalizeServer("http://example.com", false) }
    }
    @Test fun retryHashBindsAccountAndBody() {
        assertEquals(retryHash("a", "sale", "body"), retryHash("a", "sale", "body"))
        assertNotEquals(retryHash("a", "sale", "body"), retryHash("b", "sale", "body"))
        assertNotEquals(retryHash("a", "sale", "body"), retryHash("a", "sale", "changed"))
    }
}
