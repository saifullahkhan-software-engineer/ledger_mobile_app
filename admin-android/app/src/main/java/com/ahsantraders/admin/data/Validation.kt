package com.ahsantraders.admin.data

import com.google.gson.JsonObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

fun businessDate(): String = LocalDate.now(ZoneId.of("Asia/Karachi")).toString()
fun rupees(paisa: Long): String = "Rs. " + NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}.format(BigDecimal.valueOf(paisa, 2))

fun moneyInput(text: String, zeroAllowed: Boolean = false): Long {
    val number = text.trim().toBigDecimalOrNull() ?: throw IllegalArgumentException("Enter a valid amount in rupees")
    val paisa = try { number.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }
    catch (_: ArithmeticException) { throw IllegalArgumentException("Use an amount with at most two decimal places") }
    require(paisa in (if (zeroAllowed) 0L else 1L)..1_000_000_000_000L) { "Amount is outside the allowed range" }
    return paisa
}
fun quantityInput(text: String, zeroAllowed: Boolean = false, whole: Boolean = false): String {
    val value = text.trim().toBigDecimalOrNull() ?: throw IllegalArgumentException("Enter a valid quantity")
    require(value >= (if (zeroAllowed) BigDecimal.ZERO else BigDecimal("0.001")) && value <= BigDecimal("1000000")) { "Quantity is outside the allowed range" }
    require(value.stripTrailingZeros().scale() <= if (whole) 0 else 3) { if (whole) "Enter whole cylinders" else "Use at most three decimal places" }
    return value.toPlainString()
}
enum class FormKind { SALE, PURCHASE, EXPENSE, BYPRODUCT, BATCH_CREATE, BATCH_LOG, HARVEST, SUPPLIER, PROFILE, PASSWORD }
data class Draft(val kind: FormKind, val values: Map<String, String> = emptyMap())

/** UI uses rupees; only this boundary converts to exact integer paisa. */
fun formBody(draft: Draft, business: Business?, batch: Batch?, today: String = businessDate()): JsonObject {
    val v = draft.values
    fun value(key: String) = v[key].orEmpty()
    fun name(key: String): String = value(key).also { require(it.isNotBlank() && it.length <= 120) { "Name is required (maximum 120 characters)" } }
    fun count(key: String, zero: Boolean = false): Int = (value(key).toIntOrNull() ?: throw IllegalArgumentException("Enter a whole number for $key")).also {
        require(it in (if (zero) 0 else 1)..1_000_000) { "Invalid $key" }
    }
    fun date(): String = value("date").also {
        try { LocalDate.parse(it) } catch (_: java.time.format.DateTimeParseException) {
            throw IllegalArgumentException("Enter the date as YYYY-MM-DD")
        }
    }
    return JsonObject().apply {
        when (draft.kind) {
            FormKind.SALE, FormKind.PURCHASE, FormKind.EXPENSE, FormKind.BYPRODUCT -> {
                val b = requireNotNull(business) { "Choose a business" }
                require(b.type != "BROILER") { "Use batch logs for broiler expenses" }
                require(date() == today) { "Daily records must use today's Pakistan date" }
                addProperty("business_id", b.id); addProperty("date", date()); addProperty("kind", draft.kind.name)
                addProperty("amount", moneyInput(value("amount")))
                if (draft.kind != FormKind.EXPENSE) addProperty("quantity", quantityInput(value("quantity"), draft.kind == FormKind.BYPRODUCT, b.type == "LPG"))
                if (b.type == "LPG" && draft.kind == FormKind.SALE) {
                    require(value("channel") in listOf("RETAIL", "COMMERCIAL")) { "Choose a sale channel" }
                    addProperty("channel", value("channel"))
                }
                if (draft.kind == FormKind.PURCHASE && value("supplier_id").isNotEmpty()) addProperty("supplier_id", value("supplier_id"))
                require(value("note").length <= 1000) { "Note must be at most 1,000 characters" }
                addProperty("note", value("note"))
            }
            FormKind.SUPPLIER -> {
                addProperty("business_id", requireNotNull(business).id); addProperty("name", name("name"))
                if (value("phone").isNotBlank()) {
                    require(Regex("^\\+[1-9]\\d{7,14}$").matches(value("phone"))) { "Phone must use international format, e.g. +923001234567" }
                    addProperty("phone", value("phone"))
                }
            }
            FormKind.BATCH_CREATE -> {
                require(business?.type == "BROILER") { "Choose a broiler business" }
                addProperty("business_id", business.id); addProperty("name", name("name"))
                addProperty("chicks", count("chicks")); addProperty("total_shares", count("shares"))
                addProperty("share_price", moneyInput(value("price"))); addProperty("initial_cost", moneyInput(value("amount"), true))
            }
            FormKind.BATCH_LOG -> {
                require(batch?.status == "ACTIVE") { "Choose an active batch" }
                require(date() >= batch.started_on.orEmpty() && date() <= today) { "Date must be between batch start and today" }
                val dead = count("deaths", true)
                require(dead <= batch.chicks - batch.deaths) { "Mortality exceeds live birds" }
                addProperty("date", date()); addProperty("deaths", dead)
                addProperty("feed_kg", quantityInput(value("feed"), true)); addProperty("expense", moneyInput(value("amount"), true))
                require(value("note").length <= 1000) { "Note is too long" }; addProperty("note", value("note"))
            }
            FormKind.HARVEST -> {
                require(batch?.status == "ACTIVE") { "Batch is not active" }
                addProperty("yield_kg", quantityInput(value("quantity"), true))
                addProperty("price_per_kg", moneyInput(value("price"), true))
                addProperty("additional_expense", moneyInput(value("amount"), true))
            }
            FormKind.PROFILE -> { addProperty("name", name("name")); addProperty("language", value("language")) }
            FormKind.PASSWORD -> {
                require(value("new_password").length in 10..128) { "New password must contain 10–128 characters" }
                require(value("new_password") == value("confirm")) { "Passwords do not match" }
                require(value("old_password").isNotEmpty()) { "Enter your current password" }
                addProperty("old_password", value("old_password")); addProperty("new_password", value("new_password"))
            }
        }
    }
}
