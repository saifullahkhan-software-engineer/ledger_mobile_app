package com.ahsantraders.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ahsantraders.admin.data.*

fun formTitle(kind: FormKind): String = when (kind) {
    FormKind.SALE -> "Add sale"; FormKind.PURCHASE -> "Add purchase"; FormKind.EXPENSE -> "Add expense"
    FormKind.BYPRODUCT -> "Other sale"; FormKind.WASTAGE -> "Record wastage"; FormKind.BATCH_CREATE -> "Create batch"; FormKind.BATCH_LOG -> "Add daily record"
    FormKind.HARVEST -> "Harvest batch"; FormKind.SUPPLIER -> "Add supplier"; FormKind.PROFILE -> "Edit profile"; FormKind.PASSWORD -> "Change password"
    FormKind.EDIT_OPERATION -> "Correct transaction"; FormKind.EDIT_STOCK -> "Edit stock"
}
/**
 * "Last 5 Sales / Last 5 Purchases / Recent Expenses" below an entry form.
 * Data comes from the same transaction feed as the history screen, and
 * "View all" opens that feed with the matching kind filter.
 */
@Composable private fun RecentTransactions(s: AdminState, vm: AdminViewModel) {
    val kind = s.draft?.kind ?: return
    val unit = "KG"
    val (title, empty) = when (kind) {
        FormKind.SALE -> "Last 5 Sales" to "No sales recorded yet."
        FormKind.PURCHASE -> "Last 5 Purchases" to "No purchases recorded yet."
        FormKind.EXPENSE -> "Recent Expenses" to "No expenses recorded yet."
        FormKind.WASTAGE -> "Recent wastage" to "No wastage recorded yet."
        else -> "Last 5 Other Sales" to "No other sales recorded yet."
    }
    TransactionTable(title, s.recent, unit, empty) { vm.viewAll(kind.name) }
}

@Composable fun FormScreen(s: AdminState, vm: AdminViewModel) {
    val draft = s.draft ?: return
    var harvestConfirm by remember { mutableStateOf(false) }
    SectionTitle(formTitle(draft.kind), s.batch?.takeIf { draft.kind in listOf(FormKind.BATCH_LOG, FormKind.HARVEST) }?.name ?: s.business?.name)
    @Composable fun input(key: String, label: String, keyboard: KeyboardType = KeyboardType.Text, secret: Boolean = false, multiline: Boolean = false, hint: String? = null, enabled: Boolean = !s.saving) {
        var visible by remember(key) { mutableStateOf(false) }
        OutlinedTextField(value = draft.values[key].orEmpty(), onValueChange = { vm.field(key, it) },
            label = { Text(tr(label)) }, modifier = Modifier.fillMaxWidth(), singleLine = !multiline, minLines = if (multiline) 3 else 1,
            enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
            visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            supportingText = hint?.let { { Text(it) } },
            trailingIcon = if (secret) { { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "Hide password" else "Show password") } } } else null)
    }
    val owner = s.user?.role == "SUPERADMIN"
    when (draft.kind) {
        FormKind.SALE, FormKind.PURCHASE, FormKind.WASTAGE -> {
            val lpg = s.business?.type == "LPG"
            val chicken = s.business?.type == "CHICKEN"
            s.business?.let { b ->
                Panel {
                    DataRow("Weight (kg)", "${b.stock} kg")
                    if (chicken || lpg) DataRow(if (lpg) "Quantity (cylinders)" else "Number of birds", b.stock_count.toString())
                    DataRow("Price", rupees(b.stock_cost))
                    Text(tr("Current stock. Sales and wastage reduce these figures."), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            input("date", "Date", hint = if (owner) "YYYY-MM-DD · super admin can record a previous Pakistan date" else "YYYY-MM-DD · ${businessDate()} in Pakistan", enabled = !s.saving && owner)
            val liveBuy = chicken && draft.kind == FormKind.PURCHASE
            if (liveBuy) input("live_weight", "Live weight (kg)", KeyboardType.Decimal, hint = "Optional — weight of the live birds bought. At most 65% of it can be sold as meat.")
            input("quantity", if (liveBuy) "Dressed weight (kg)" else "Weight (kg)", KeyboardType.Decimal, hint = if (liveBuy) "Meat that goes into stock. Leave empty to use 65% of the live weight." else null)
            if (lpg && draft.kind != FormKind.WASTAGE) input("count", "Quantity (cylinders)", KeyboardType.Number, hint = "Number of cylinders, e.g. 5")
            if (chicken) input("count", if (draft.kind == FormKind.WASTAGE) "Birds discarded (optional)" else "Quantity (birds)", KeyboardType.Number, hint = "Optional — number of birds, e.g. 3 or 4")
            if (draft.kind == FormKind.SALE && chicken) input("wastage", "Wastage (kg)", KeyboardType.Decimal, hint = "Cutting loss or leftover weight so stock matches at day end")
            if (draft.kind != FormKind.WASTAGE) {
                input("amount", "Amount (Rs.)", KeyboardType.Decimal, hint = "Fixed total, not a per-kg rate — e.g. 1250.50")
                Text(tr("Enter the total amount. It is not calculated from weight."), color = Muted, style = MaterialTheme.typography.bodySmall)
            } else {
                Text(tr("Wastage reduces stock weight without adding a sale amount, so the day-end weight matches."), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (draft.kind == FormKind.SALE && lpg) Selection("Sale channel", draft.values["channel"].orEmpty(), listOf("RETAIL" to "Retail", "COMMERCIAL" to "Commercial"), !s.saving, translateChoices = true) { vm.field("channel", it) }
            if (draft.kind == FormKind.PURCHASE) Selection("Supplier (optional)", draft.values["supplier_id"].orEmpty(), listOf("" to "None") + s.suppliers.map { it.id to it.name }, !s.saving) { vm.field("supplier_id", it) }
            input("note", "Note", multiline = true)
        }
        FormKind.EXPENSE, FormKind.BYPRODUCT -> {
            input("date", "Date", hint = if (owner) "YYYY-MM-DD · super admin can record a previous Pakistan date" else "YYYY-MM-DD · ${businessDate()} in Pakistan", enabled = !s.saving && owner)
            if (draft.kind == FormKind.BYPRODUCT) {
                input("quantity", "Quantity (kg, optional)", KeyboardType.Decimal, hint = "Leave empty when the weight is unknown")
                Text("Use Other sale for any income that is not a regular chicken or LPG sale. Enter the total amount received; the weight is optional.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (draft.kind == FormKind.EXPENSE) {
                val current = draft.values["category"].orEmpty()
                val choices = listOf("" to "None") + (if (current.isNotBlank() && current !in EXPENSE_CATEGORIES) listOf(current) else emptyList()).plus(EXPENSE_CATEGORIES).map { it to it }
                Selection("Category", current, choices, !s.saving) { vm.field("category", it) }
            }
            input("amount", "Amount (Rs.)", KeyboardType.Decimal, hint = "Enter rupees, e.g. 1250.50 — not paisa")
            input("note", if (draft.kind == FormKind.EXPENSE) "Description" else "Note", multiline = true)
        }
        FormKind.EDIT_OPERATION -> {
            val kind = draft.values["kind"].orEmpty()
            val lpg = draft.values["lpg"] == "1"
            Text("Correcting a ${kindLabel(kind).lowercase()} from ${draft.values["date"].orEmpty()}. The date cannot change.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            val liveBuy = kind == "PURCHASE" && !lpg
            if (liveBuy) input("live_weight", "Live weight (kg)", KeyboardType.Decimal, hint = "Optional — at most 65% of it can be sold as meat; the dressed weight is capped to match.")
            if (kind != "EXPENSE") input("quantity", if (liveBuy) "Dressed weight (kg)" else "Weight (kg)", KeyboardType.Decimal)
            if (kind == "PURCHASE" || kind == "SALE" || kind == "WASTAGE") {
                if (lpg) input("count", "Quantity (cylinders)", KeyboardType.Number, hint = "Number of cylinders, e.g. 5")
                if (!lpg) input("count", "Quantity (birds)", KeyboardType.Number, hint = "Optional — number of birds, e.g. 3 or 4")
                if (kind == "SALE" && !lpg) input("wastage", "Wastage (kg)", KeyboardType.Decimal, hint = "Cutting loss or leftover weight so stock matches at day end")
                if (kind != "WASTAGE") {
                    input("amount", "Amount (Rs.)", KeyboardType.Decimal, hint = "Fixed total, not a per-kg rate — e.g. 1250.50")
                    Text(tr("Enter the total amount. It is not calculated from weight."), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                input("amount", "Amount (Rs.)", KeyboardType.Decimal, hint = "Enter rupees, e.g. 1250.50 — not paisa")
            }
            if (kind == "EXPENSE") {
                val current = draft.values["category"].orEmpty()
                val choices = listOf("" to "None") + (if (current.isNotBlank() && current !in EXPENSE_CATEGORIES) listOf(current) else emptyList()).plus(EXPENSE_CATEGORIES).map { it to it }
                Selection("Category", current, choices, !s.saving) { vm.field("category", it) }
            }
            input("note", if (kind == "EXPENSE") "Description" else "Note", multiline = true)
            Text("Owner-only correction: only the super admin can update a past-date transaction. Saving rebuilds that date's summary; a payout that was already settled is never changed.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        FormKind.EDIT_STOCK -> {
            val lpg = s.business?.type == "LPG"
            Text(tr("Set the current stock: weight, number of birds and the carrying price of what is left."), color = Muted, style = MaterialTheme.typography.bodyMedium)
            input("quantity", "Weight (kg)", KeyboardType.Decimal)
            input("count", if (lpg) "Quantity (cylinders)" else "Number of birds", KeyboardType.Number)
            input("amount", "Price (Rs.)", KeyboardType.Decimal, hint = "Total value of remaining stock, not a per-kg rate")
        }
        FormKind.SUPPLIER -> {
            input("name", "Supplier name"); input("phone", "Phone number", KeyboardType.Phone, hint = "Optional · +923001234567")
            Text("Supplier creation does not support automatic deduplication. After a timeout, check the supplier list before trying again.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        FormKind.BATCH_CREATE -> {
            input("name", "Batch name"); input("chicks", "Chicks", KeyboardType.Number); input("shares", "Total shares", KeyboardType.Number)
            input("price", "Share price (Rs.)", KeyboardType.Decimal); input("amount", "Initial cost (Rs.)", KeyboardType.Decimal)
            Text("Creates a funding-stage batch. Investors may buy shares until you start it. Unsold capital is assumed to be operator-funded by the backend.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        FormKind.BATCH_LOG -> {
            input("date", "Date", hint = "YYYY-MM-DD · one record per date")
            input("feed", "Feed (kg)", KeyboardType.Decimal); input("deaths", "Deaths", KeyboardType.Number)
            input("amount", "Expense (Rs.)", KeyboardType.Decimal, hint = "Include today's feed cost and other new costs once.")
            input("note", "Note", multiline = true)
        }
        FormKind.HARVEST -> {
            input("quantity", "Yield (kg)", KeyboardType.Decimal); input("price", "Sale price per kg (Rs.)", KeyboardType.Decimal)
            input("amount", "Additional expense (Rs.)", KeyboardType.Decimal, hint = "Only costs not already recorded in the batch.")
            val revenue = runCatching {
                val kg = quantityInput(draft.values["quantity"].orEmpty(), true).toBigDecimal()
                val price = moneyInput(draft.values["price"].orEmpty(), true)
                kg.multiply(price.toBigDecimal()).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
            }.getOrNull()
            revenue?.let { Panel { DataRow("Revenue", rupees(it)); Text("Estimate only. The server calculates and settles the final result.", color = Muted, style = MaterialTheme.typography.bodySmall) } }
            Text("Harvest permanently closes the batch and distributes principal plus profit/loss to investors. This cannot be undone in the app.", color = Chicken, style = MaterialTheme.typography.bodyMedium)
        }
        FormKind.PROFILE -> { input("name", "Name"); Selection("Language", draft.values["language"].orEmpty(), listOf("en" to "English", "ur" to "اردو"), !s.saving) { vm.field("language", it) } }
        FormKind.PASSWORD -> { input("old_password", "Current password", secret = true); input("new_password", "New password", secret = true, hint = "10–128 characters"); input("confirm", "Confirm password", secret = true) }
    }
    Button(onClick = { if (draft.kind == FormKind.HARVEST) harvestConfirm = true else vm.submit() }, enabled = !s.saving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text(tr(when (draft.kind) { FormKind.PROFILE, FormKind.PASSWORD, FormKind.EDIT_STOCK -> "Save changes"; FormKind.EDIT_OPERATION -> "Save correction"; else -> "Save record" }))
    }
    TextButton(onClick = vm::back, enabled = !s.saving, modifier = Modifier.fillMaxWidth()) { Text(tr("Cancel")) }
    if (draft.kind in listOf(FormKind.SALE, FormKind.PURCHASE, FormKind.EXPENSE, FormKind.BYPRODUCT, FormKind.WASTAGE)) {
        RecentTransactions(s, vm)
    }
    if (harvestConfirm) ConfirmDialog("Harvest batch", "This closes ${s.batch?.name.orEmpty()} and immediately settles investors. Check the yield, price and additional expenses before confirming.", { harvestConfirm = false }) { harvestConfirm = false; vm.submit() }
}
