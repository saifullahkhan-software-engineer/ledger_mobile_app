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
    FormKind.BYPRODUCT -> "Pota-Kaliji sale"; FormKind.BATCH_CREATE -> "Create batch"; FormKind.BATCH_LOG -> "Add daily record"
    FormKind.HARVEST -> "Harvest batch"; FormKind.SUPPLIER -> "Add supplier"; FormKind.PROFILE -> "Edit profile"; FormKind.PASSWORD -> "Change password"
}
@Composable fun FormScreen(s: AdminState, vm: AdminViewModel) {
    val draft = s.draft ?: return
    var harvestConfirm by remember { mutableStateOf(false) }
    SectionTitle(formTitle(draft.kind), s.batch?.takeIf { draft.kind in listOf(FormKind.BATCH_LOG, FormKind.HARVEST) }?.name ?: s.business?.name)
    @Composable fun input(key: String, label: String, keyboard: KeyboardType = KeyboardType.Text, secret: Boolean = false, multiline: Boolean = false, hint: String? = null) {
        var visible by remember(key) { mutableStateOf(false) }
        OutlinedTextField(value = draft.values[key].orEmpty(), onValueChange = { vm.field(key, it) },
            label = { Text(tr(label)) }, modifier = Modifier.fillMaxWidth(), singleLine = !multiline, minLines = if (multiline) 3 else 1,
            enabled = !s.saving, keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
            visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            supportingText = hint?.let { { Text(it) } },
            trailingIcon = if (secret) { { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "Hide password" else "Show password") } } } else null)
    }
    when (draft.kind) {
        FormKind.SALE, FormKind.PURCHASE, FormKind.EXPENSE, FormKind.BYPRODUCT -> {
            input("date", "Date", hint = "YYYY-MM-DD · ${businessDate()} in Pakistan")
            if (draft.kind != FormKind.EXPENSE) input("quantity", if (s.business?.type == "LPG") "Cylinders" else "Quantity (kg)", KeyboardType.Decimal)
            input("amount", "Amount (Rs.)", KeyboardType.Decimal, hint = "Enter rupees, e.g. 1250.50 — not paisa")
            if (draft.kind == FormKind.SALE && s.business?.type == "LPG") Selection("Sale channel", draft.values["channel"].orEmpty(), listOf("RETAIL" to "Retail", "COMMERCIAL" to "Commercial"), !s.saving, translateChoices = true) { vm.field("channel", it) }
            if (draft.kind == FormKind.PURCHASE) Selection("Supplier (optional)", draft.values["supplier_id"].orEmpty(), listOf("" to "None") + s.suppliers.map { it.id to it.name }, !s.saving) { vm.field("supplier_id", it) }
            input("note", "Note", multiline = true)
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
        Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text(tr(if (draft.kind == FormKind.PROFILE || draft.kind == FormKind.PASSWORD) "Save changes" else "Save record"))
    }
    TextButton(onClick = vm::back, enabled = !s.saving, modifier = Modifier.fillMaxWidth()) { Text(tr("Cancel")) }
    if (harvestConfirm) ConfirmDialog("Harvest batch", "This closes ${s.batch?.name.orEmpty()} and immediately settles investors. Check the yield, price and additional expenses before confirming.", { harvestConfirm = false }) { harvestConfirm = false; vm.submit() }
}
