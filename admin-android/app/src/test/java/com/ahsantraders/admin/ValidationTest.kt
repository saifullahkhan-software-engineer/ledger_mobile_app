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
    @Test fun saleAmountIsFixedNotWeightTimesRate() {
        val result = formBody(Draft(FormKind.SALE, mapOf("date" to date, "quantity" to "2.5", "amount" to "800")), chicken, null, date)
        assertEquals(80000L, result["amount"].asLong)
        assertEquals("2.5", result["quantity"].asString)
        assertFalse(result.has("price"))
    }
    @Test fun chickenSaleSendsWastage() {
        val result = formBody(Draft(FormKind.SALE, mapOf("date" to date, "quantity" to "4", "amount" to "500", "wastage" to "0.5")), chicken, null, date)
        assertEquals("0.5", result["wastage"].asString)
        assertEquals(50000L, result["amount"].asLong)
        val waste = formBody(Draft(FormKind.WASTAGE, mapOf("date" to date, "quantity" to "1.25", "note" to "")), chicken, null, date)
        assertEquals("WASTAGE", waste["kind"].asString)
        assertEquals(0L, waste["amount"].asLong)
        assertEquals("1.25", waste["quantity"].asString)
        assertThrows(IllegalArgumentException::class.java) { formBody(Draft(FormKind.WASTAGE, mapOf("date" to date, "quantity" to "1")), lpg, null, date) }
    }
    @Test fun chickenPurchaseSendsLiveWeightAndDressedWeightIsOptional() {
        val live = formBody(Draft(FormKind.PURCHASE, mapOf("date" to date, "live_weight" to "100", "quantity" to "", "amount" to "15000")), chicken, null, date)
        assertEquals("100", live["live_weight"].asString)
        assertEquals("0", live["quantity"].asString)
        assertEquals(1500000L, live["amount"].asLong)
        val both = formBody(Draft(FormKind.PURCHASE, mapOf("date" to date, "live_weight" to "100", "quantity" to "62.5", "amount" to "15000")), chicken, null, date)
        assertEquals("62.5", both["quantity"].asString)
        val plain = formBody(Draft(FormKind.PURCHASE, mapOf("date" to date, "quantity" to "10", "amount" to "1000")), chicken, null, date)
        assertFalse(plain.has("live_weight"))
        assertThrows(IllegalArgumentException::class.java) { formBody(Draft(FormKind.PURCHASE, mapOf("date" to date, "amount" to "1000")), chicken, null, date) }
    }
    @Test fun liveWeightIsOnlySentForChickenPurchases() {
        val lpgBuy = formBody(Draft(FormKind.PURCHASE, mapOf("date" to date, "live_weight" to "100", "quantity" to "5", "amount" to "1000")), lpg, null, date)
        assertFalse(lpgBuy.has("live_weight"))
        val sale = formBody(Draft(FormKind.SALE, mapOf("date" to date, "live_weight" to "100", "quantity" to "5", "amount" to "1000")), chicken, null, date)
        assertFalse(sale.has("live_weight"))
    }
    @Test fun correctingAChickenPurchaseCanSendLiveWeight() {
        val chickenEdit = formBody(Draft(FormKind.EDIT_OPERATION, mapOf("kind" to "PURCHASE", "lpg" to "0", "quantity" to "60", "amount" to "15000", "live_weight" to "100", "note" to "")), chicken, null, date)
        assertEquals("100", chickenEdit["live_weight"].asString)
        val lpgEdit = formBody(Draft(FormKind.EDIT_OPERATION, mapOf("kind" to "PURCHASE", "lpg" to "1", "quantity" to "5", "amount" to "1000", "live_weight" to "100", "note" to "")), lpg, null, date)
        assertFalse(lpgEdit.has("live_weight"))
    }
    @Test fun stockEditSendsWeightCountAndPrice() {
        val result = formBody(Draft(FormKind.EDIT_STOCK, mapOf("quantity" to "12.25", "count" to "9", "amount" to "1500.50")), chicken, null, date)
        assertEquals("12.25", result["quantity"].asString)
        assertEquals(9, result["count"].asInt)
        assertEquals(150050L, result["inventory_cost"].asLong)
        assertEquals("c", result["business_id"].asString)
        assertThrows(IllegalArgumentException::class.java) { formBody(Draft(FormKind.EDIT_STOCK, mapOf("quantity" to "1", "amount" to "1")), broiler, null, date) }
    }
    @Test fun lpgSaleRequiresChannel() {
        val draft = Draft(FormKind.SALE, mapOf("date" to date, "quantity" to "2", "amount" to "150"))
        assertThrows(IllegalArgumentException::class.java) { formBody(draft, lpg, null, date) }
        assertEquals("RETAIL", formBody(draft.copy(values = draft.values + ("channel" to "RETAIL")), lpg, null, date)["channel"].asString)
    }
    @Test fun rejectsBackdatedDailyEntry() {
        assertThrows(IllegalArgumentException::class.java) { formBody(Draft(FormKind.EXPENSE, mapOf("date" to "2026-01-01", "amount" to "1")), chicken, null, date) }
        val past = formBody(Draft(FormKind.EXPENSE, mapOf("date" to "2026-01-01", "amount" to "1")), chicken, null, date, allowPastDate = true)
        assertEquals("2026-01-01", past["date"].asString)
        assertThrows(IllegalArgumentException::class.java) { formBody(Draft(FormKind.EXPENSE, mapOf("date" to "2026-01-03", "amount" to "1")), chicken, null, date, allowPastDate = true) }
    }
    @Test fun batchMortalityCannotExceedRemainingBirds() {
        val draft = Draft(FormKind.BATCH_LOG, mapOf("date" to date, "feed" to "1", "deaths" to "9", "amount" to "0"))
        assertThrows(IllegalArgumentException::class.java) { formBody(draft, broiler, batch, date) }
        assertEquals(8, formBody(draft.copy(values = draft.values + ("deaths" to "8")), broiler, batch, date)["deaths"].asInt)
    }
    @Test fun otherSaleAcceptsAnOptionalWeight() {
        // "Other sale" (backend BYPRODUCT) is any extra income: the amount is
        // always required, the weight may be left empty.
        val draft = Draft(FormKind.BYPRODUCT, mapOf("date" to date, "amount" to "250.50", "quantity" to ""))
        val body = formBody(draft, chicken, null, date)
        assertEquals("BYPRODUCT", body["kind"].asString)
        assertEquals("0", body["quantity"].asString)
        assertEquals(25050L, body["amount"].asLong)
        assertEquals("1.5", formBody(draft.copy(values = draft.values + ("quantity" to "1.5")), chicken, null, date)["quantity"].asString)
        // The owner correction form keeps the same optional weight.
        val fix = Draft(FormKind.EDIT_OPERATION, mapOf("kind" to "BYPRODUCT", "quantity" to "", "amount" to "10", "note" to "", "date" to date))
        assertEquals("0", formBody(fix, chicken, null, date)["quantity"].asString)
        assertEquals(1000L, formBody(fix, chicken, null, date)["amount"].asLong)
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
