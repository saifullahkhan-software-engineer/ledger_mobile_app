@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ahsantraders.admin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahsantraders.admin.data.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs


@Composable fun HomeScreen(s: AdminState, vm: AdminViewModel, choose: (FormKind) -> Unit) {
    Text(if (s.language == "ur") "خوش آمدید، ${s.user?.name.orEmpty()}" else "Welcome, ${s.user?.name.orEmpty()}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(if (s.language == "ur") "اپنے کاروبار ایک جگہ سنبھالیں" else "Your businesses. One clear picture.", color = Muted)
    SectionTitle("Your businesses")
    if (s.businesses.isEmpty() && !s.loading) Empty("No businesses assigned. Ask your owner for access.")
    s.businesses.chunked(3).forEach { group ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            group.forEach { business ->
                Card(
                    onClick = { vm.go(Page.BUSINESS, business) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = sectorColor(business.type))
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        SectorIcon(
                            type = business.type,
                            tint = Color.White,
                            modifier = Modifier.size(35.dp)
                        )
                        Text(
                            tr(sectorName(business.type)),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                        val row = s.dashboard?.businesses?.find { it.business_id == business.id }
                        Text(row?.let { rupees(it.revenue) } ?: "—", fontSize = 12.sp, color = Color.White)
                        Text(tr("Sales"), fontSize = 10.sp, color = Color.White.copy(alpha = .8f))
                    }
                }
            }
        }
    }
    SectionTitle("Quick actions")

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionTile(
            label = "Add sale",
            icon = Icons.Default.AddCircle,
            modifier = Modifier.weight(1f)
        ) { choose(FormKind.SALE) }
        ActionTile(
            label = "Add expense",
            icon = Icons.Default.AccountBalanceWallet,
            color = Color(0xFFE58B19),
            modifier = Modifier.weight(1f)
        ) { choose(FormKind.EXPENSE) }
    }
    LinkRow(
        title = "Overall View",
        icon = Icons.Default.Insights,
        subtitle = "Overall view and per-business charts"
    ) { vm.go(Page.REPORTS) }
    
    Surface(color = Forest, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Grow Together, With Trust", color = Gold, fontWeight = FontWeight.Medium)
            Text("AHSAN TRADERS", color = Color.White.copy(alpha = .65f), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable fun BusinessScreen(s: AdminState, vm: AdminViewModel, confirmClose: (Day) -> Unit) {
    val b = s.business ?: return
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(sectorColor(b.type), Forest))).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // Circular container (not square) for the bundled sector artwork.
            SectorIcon(
                type = b.type,
                tint = Color.White,
                modifier = Modifier.size(54.dp)
            )
            Column {
                Text(b.name, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(tr(sectorName(b.type)), color = Color.White.copy(alpha = .8f), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    SectionTitle("Today’s summary", businessDate())
    val summary = s.summary
    if (summary != null) {
        Panel {
            if (b.type == "BROILER") {
                DataRow("Live birds", summary.live_birds.toString()); DataRow("Feed consumed", "${summary.feed_kg} kg"); DataRow("Mortality", summary.mortality.toString())
                Text("No daily investor payout. Profit is settled at harvest.", color = Muted, fontSize = 12.sp)
            } else {
                summary.day?.let { Status(it.status) }
                DataRow("Revenue", rupees(summary.day?.revenue ?: 0))
                DataRow("Purchased", "${summary.purchased_quantity} kg")
                DataRow("Sold", "${summary.sold_quantity} kg")
                if (b.type == "CHICKEN" && summary.purchased_count > 0) DataRow("Purchased (birds)", summary.purchased_count.toString())
                if (b.type == "CHICKEN" && summary.sold_count > 0) DataRow("Sold (birds)", summary.sold_count.toString())
                if (b.type == "LPG" && summary.purchased_count > 0) DataRow("Purchased (cylinders)", summary.purchased_count.toString())
                if (b.type == "LPG" && summary.sold_count > 0) DataRow("Sold (cylinders)", summary.sold_count.toString())
                if (b.type == "CHICKEN") DataRow("Other sale", rupees(summary.byproduct_revenue))
                else { DataRow("Retail sales", summary.retail_sold); DataRow("Commercial sales", summary.commercial_sold) }
                DataRow("Operating expenses", rupees(summary.day?.expenses ?: 0))
                HorizontalDivider(); DataRow("Net profit", rupees(summary.day?.profit ?: 0), Green)
                if (summary.day == null) Text("No operations recorded today.", color = Muted, fontSize = 12.sp)
            }
        }
    }


    if (b.type == "BROILER") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Create batch", Icons.Default.AddCircle, modifier = Modifier.weight(1f)) { vm.openForm(FormKind.BATCH_CREATE) }
            ActionTile("Batches", Icons.Default.Eco, Lpg, modifier = Modifier.weight(1f)) { vm.go(Page.BATCHES) }
        }
        summary?.batches?.forEach { batch -> BatchCard(batch) { vm.openBatch(batch) } }
    } else if (summary?.day?.status != "CLOSED") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(
                label = "Add sale",
                icon = Icons.Default.AddCircle,
                modifier = Modifier.weight(1f)
            ) { vm.openForm(FormKind.SALE) }
            ActionTile(
                label = "Add purchase",
                icon = Icons.Default.ShoppingCart,
                color = Lpg,
                modifier = Modifier.weight(1f)
            ) { vm.openForm(FormKind.PURCHASE) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(
                label = "Add expense",
                icon = Icons.Default.AccountBalanceWallet,
                color = Color(0xFFE58B19),
                modifier = Modifier.weight(1f)
            ) { vm.openForm(FormKind.EXPENSE) }
            if (b.type == "CHICKEN") ActionTile(
                label = "Other sale",
                icon = Icons.Default.LocalOffer,
                color = Chicken,
                modifier = Modifier.weight(1f)
            ) { vm.openForm(FormKind.BYPRODUCT) }
        }
    }
    if (b.type != "BROILER") LinkRow("Daily summary", Icons.Default.CalendarMonth, businessDate()) { vm.openDayAt(businessDate()) }
    LinkRow("Stock", Icons.Default.Inventory2) { vm.go(Page.STOCK) }
    if (b.type != "BROILER") {
        LinkRow("Transaction history", Icons.Default.ReceiptLong) { vm.go(Page.LEDGER) }
        LinkRow("Expenses", Icons.Default.AccountBalanceWallet) { vm.go(Page.EXPENSES) }
        LinkRow("Suppliers", Icons.Default.LocalShipping) { vm.go(Page.SUPPLIERS) }
        summary?.day?.takeIf { it.status == "OPEN" }?.let { day ->
            OutlinedButton(onClick = { confirmClose(day) }, enabled = !s.saving, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text(tr("Close day")) }
            Text(tr("Days close automatically at midnight (Pakistan time). You can also close today now."), color = Muted, fontSize = 12.sp)
        }
    }
    LinkRow("Settlement history", Icons.Default.AccountBalance) { vm.go(Page.SETTLEMENTS) }
}
@Composable fun BatchCard(batch: Batch, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(batch.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Status(batch.status) }
            DataRow("Live birds", (batch.chicks - batch.deaths).toString())
            DataRow("Total shares", batch.total_shares.toString())
        }
    }
}
@Composable fun BatchScreen(s: AdminState, vm: AdminViewModel, confirmStart: () -> Unit) {
    val b = s.batch ?: return
    SectionTitle(b.name, translate = false)
    Panel {
        Status(b.status); DataRow("Chicks", b.chicks.toString()); DataRow("Live birds", (b.chicks - b.deaths).toString()); DataRow("Mortality", b.deaths.toString())
        DataRow("Total shares", b.total_shares.toString()); DataRow("Share price (Rs.)", rupees(b.share_price))
        DataRow("Total costs & expenses", rupees(b.expenses))
        b.started_on?.let { DataRow("Started", it) }
        b.closed_on?.let { DataRow("Harvested", it); DataRow("Yield (kg)", b.yield_kg); DataRow("Revenue", rupees(b.revenue)); DataRow("Net profit", rupees(b.net_profit)) }
    }
    when (b.status) {
        "FUNDING" -> Button(onClick = confirmStart, enabled = !s.saving, modifier = Modifier.fillMaxWidth()) { Text(tr("Start batch")) }
        "ACTIVE" -> {
            Button(onClick = { vm.openForm(FormKind.BATCH_LOG) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Add daily record")) }
            OutlinedButton(onClick = { vm.openForm(FormKind.HARVEST) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Harvest batch")) }
        }
    }
    SectionTitle("Batch history")
    if (s.logs.isEmpty() && !s.loading) Empty()
    s.logs.forEach { log -> Panel { Text(log.date, fontWeight = FontWeight.Bold); DataRow("Feed consumed", "${log.feed_kg} kg"); DataRow("Mortality", log.deaths.toString()); DataRow("Expenses", rupees(log.expense)); if (log.note.isNotEmpty()) Text(log.note) } }
}
/** User-facing transaction name; the API kind stays BYPRODUCT. */
fun kindLabel(kind: String): String = when (kind) {
    "BYPRODUCT" -> "Other sale"
    "SALE" -> "Sale"
    "PURCHASE" -> "Purchase"
    "EXPENSE" -> "Expense"
    else -> kind.replace('_', ' ')
}

fun row_price_per_unit_paisa(op: Operation): Long? {
    if (op.kind == "EXPENSE") return null
    val qty = op.quantity.toBigDecimalOrNull() ?: return null
    if (qty.signum() <= 0) return null
    return try { BigDecimal.valueOf(op.amount).divide(qty, 0, RoundingMode.HALF_UP).longValueExact() }
    catch (_: ArithmeticException) { null }
}
fun row_price_per_unit(op: Operation): String? = row_price_per_unit_paisa(op)?.let { rupees(it) }

/** Transaction-type colour: sale green, purchase blue, expense orange, other sale red. */
fun kindColor(kind: String): Color = when (kind) {
    "SALE" -> Green
    "PURCHASE" -> Lpg
    "EXPENSE" -> Color(0xFFE58B19)
    else -> Chicken
}

@Composable fun TypeChip(kind: String) {
    val color = kindColor(kind)
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(8.dp)) {
        Text(tr(kindLabel(kind)), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

/** Secondary line of a transaction row: weight/price, bird count or why it was spent. */
fun transactionDetail(row: Operation, unit: String): String {
    if (row.kind == "EXPENSE") return row.category?.takeIf { it.isNotBlank() } ?: row.note.ifBlank { "—" }
    val parts = mutableListOf<String>()
    val qty = row.quantity.toBigDecimalOrNull()
    if (qty != null && qty.signum() > 0) {
        parts += "${row.quantity} $unit"
        row_price_per_unit_paisa(row)?.let { parts += "${amount(it)} / $unit" }
    }
    row.count?.takeIf { it > 0 }?.let { parts += "$it pcs" }
    if (row.note.isNotBlank()) parts += row.note
    return parts.joinToString(" · ").ifBlank { "—" }
}

/** One transaction of the history list. */
@Composable fun TransactionRow(row: Operation, unit: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(row.date ?: "", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("Rs. " + amount(row.amount), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip(row.kind)
                Text(transactionDetail(row, unit), color = Muted, fontSize = 12.sp, maxLines = 2, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Compact "last 5" / "recent" table used on the entry forms. */
@Composable fun TransactionTable(title: String, rows: List<Operation>, unit: String, emptyText: String, onViewAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (rows.isNotEmpty()) TextButton(onClick = onViewAll, enabled = true) { Text(tr("View all"), fontSize = 12.sp) }
    }
    if (rows.isEmpty()) { Text(tr(emptyText), color = Muted, fontSize = 12.sp); return }
    val expense = rows.first().kind == "EXPENSE"
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            TableLine(if (expense) listOf("Date", "Category", "Amount") else listOf("Date", unit, "Price", "Total"), header = true)
            rows.forEach { row ->
                val qty = row.quantity.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.let { row.quantity } ?: "—"
                TableLine(
                    if (expense) listOf(row.date.orEmpty(), row.category?.takeIf { it.isNotBlank() } ?: "—", amount(row.amount))
                    else listOf(row.date.orEmpty(), qty, row_price_per_unit_paisa(row)?.let { amount(it) } ?: "—", amount(row.amount))
                )
            }
        }
    }
}

@Composable fun TableLine(cells: List<String>, header: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEachIndexed { index, cell ->
            Text(
                cell,
                modifier = Modifier.weight(if (index == 0) 1.05f else 1f),
                color = if (header) Muted else Ink,
                fontSize = if (header) 11.sp else 12.sp,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                maxLines = 1
            )
        }
    }
}

@Composable fun OperationCard(row: Operation, onClick: () -> Unit = {}) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(tr(kindLabel(row.kind)), color = Green, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f)); Text(rupees(row.amount), fontWeight = FontWeight.Bold) }
            row.date?.takeIf { it.isNotBlank() }?.let { DataRow("Date", it) }
            if (row.kind != "EXPENSE") DataRow("Weight (kg)", row.quantity)
            row.count?.takeIf { it > 0 }?.let { DataRow("Quantity (count)", it.toString()) }
            row.category?.takeIf { it.isNotBlank() }?.let { DataRow("Category", it) }
            row.channel?.let { DataRow("Sale channel", tr(it.lowercase().replaceFirstChar { c -> c.uppercase() })) }
            if (row.note.isNotEmpty()) Text(row.note, style = MaterialTheme.typography.bodyMedium)
            Text(row.created_at, fontSize = 11.sp, color = Muted)
        }
    }
}
@Composable fun DateFilters(s: AdminState, vm: AdminViewModel, businessFilter: Boolean = false) {
    var start by remember(s.start) { mutableStateOf(s.start) }
    var end by remember(s.end) { mutableStateOf(s.end) }
    var selected by remember(s.reportBusiness) { mutableStateOf(s.reportBusiness.orEmpty()) }
    val today = LocalDate.parse(businessDate())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Daily" to today, "Weekly" to today.minusDays(6), "Monthly" to today.withDayOfMonth(1)).forEach { (label, first) ->
            FilterChip(selected = s.start == first.toString() && s.end == today.toString(), onClick = { vm.range(first.toString(), today.toString(), s.reportBusiness) }, label = { Text(tr(label)) }, enabled = !s.loading && !s.saving)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(start, { start = it }, label = { Text(tr("From")) }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(end, { end = it }, label = { Text(tr("To")) }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
    }
    if (businessFilter) Selection("Choose a business", selected, listOf("" to "All businesses") + s.businesses.map { it.id to it.name }) { selected = it }
    OutlinedButton(onClick = { vm.range(start, end, selected.ifEmpty { null }) }, modifier = Modifier.fillMaxWidth(), enabled = !s.loading && !s.saving) { Text(tr("Apply dates")) }
}
@Composable fun ReportsScreen(s: AdminState, vm: AdminViewModel) {
    DateFilters(s, vm, true)
    s.report?.let { report ->
        // Overall view — company-wide totals for the selected period.
        SectionTitle("Overall View", "${report.start}  →  ${report.end}")
        Panel {
            DataRow("Total sales", rupees(report.total_sales))
            DataRow("Total costs & expenses", rupees(report.total_cost_and_expenses))
            HorizontalDivider()
            DataRow("Net profit", rupees(report.total_profit), if (report.total_profit < 0) Chicken else Green)
            if (report.businesses.any { it.open_days > 0 }) Text("Includes open days. These totals can change before settlement.", fontSize = 12.sp, color = Muted)
        }
        // A separate chart for every business in the report.
        SectionTitle("Business charts", "Sales, costs and net profit per business for the selected period.")
        if (report.businesses.isEmpty()) Empty()
        report.businesses.forEach { business -> BusinessChart(business) }
        SectionTitle("Business-wise profit")
        Panel {
            report.businesses.forEach { business ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectorIcon(business.type, tint = sectorColor(business.type), modifier = Modifier.size(24.dp))
                    Text(business.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(rupees(business.net_profit), color = if (business.net_profit < 0) Chicken else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            val positive = report.businesses.filter { it.net_profit > 0 }
            val total = positive.sumOf { it.net_profit }.toDouble()
            if (total > 0) {
                Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(145.dp)) {
                        var angle = -90f
                        positive.forEach { b ->
                            val sweep = (b.net_profit / total * 360).toFloat()
                            drawArc(sectorColor(b.type), angle, sweep, false, style = Stroke(width = 22.dp.toPx()))
                            angle += sweep
                        }
                    }
                    Text("Positive\nprofit mix", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                Text("Chart shows positive profits only; losses remain included in totals above.", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

/**
 * Separate chart for one business: horizontal bars comparing sales, costs
 * and net profit for the selected period. Bars scale against the largest
 * value of that business so each chart is easy to read on its own.
 */
@Composable private fun BusinessChart(b: BusinessReport) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectorIcon(b.type, tint = sectorColor(b.type), modifier = Modifier.size(26.dp))
            Text(b.name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp).weight(1f))
            Text(rupees(b.net_profit), color = if (b.net_profit < 0) Chicken else Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        val top = maxOf(b.revenue, b.cost_and_expenses, abs(b.net_profit), 1L)
        ChartBar("Sales", b.revenue, top, Green)
        ChartBar("Costs", b.cost_and_expenses, top, Color(0xFFE58B19))
        ChartBar("Net profit", b.net_profit, top, if (b.net_profit >= 0) Green else Chicken)
    }
}

@Composable private fun ChartBar(label: String, value: Long, max: Long, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr(label), color = Muted, fontSize = 12.sp, modifier = Modifier.width(64.dp))
        Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE4EFE9))) {
            val fraction = (value.coerceAtLeast(0).toDouble() / max.toDouble()).toFloat().coerceIn(0f, 1f)
            if (fraction > 0f) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(6.dp)).background(color))
            }
        }
        Text(rupees(value), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.End, modifier = Modifier.width(88.dp))
    }
}
/**
 * Day stepper: browse the shop day by day. Says what kind of date it is
 * (today / closed / no records / not started) using the server's classification,
 * stops at the first recorded date and at today, and allows a direct jump.
 */
@Composable fun DayStepper(s: AdminState, vm: AdminViewModel, onPickDate: () -> Unit) {
    val today = businessDate()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { vm.stepDay(-1) }, enabled = !s.saving && vm.dayCanGoBack()) { Icon(Icons.Default.ChevronLeft, tr("Previous day")) }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onPickDate() }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.CalendarMonth, null, tint = Green, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(s.dayDate, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (s.dayDate == today) Text("  ${tr("Today")}", color = Muted, fontSize = 12.sp)
        }
        IconButton(onClick = { vm.stepDay(1) }, enabled = !s.saving && vm.dayCanGoForward()) { Icon(Icons.Default.ChevronRight, tr("Next day")) }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = s.dayDate == today, onClick = { vm.openDayAt(today) }, label = { Text(tr("Today")) }, enabled = !s.saving)
        FilterChip(selected = s.dayDate == LocalDate.parse(today).minusDays(1).toString(), onClick = { vm.openDayAt(LocalDate.parse(today).minusDays(1).toString()) }, label = { Text(tr("Yesterday")) }, enabled = !s.saving)
        FilterChip(selected = false, onClick = onPickDate, label = { Text(tr("Pick a date")) }, enabled = !s.saving)
    }
}

/** Calendar with the same bounds as the stepper: first recorded date → today. */
@Composable fun DayPickerDialog(initial: String, firstDate: String?, lastDate: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    fun toMillis(date: String) = LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    fun toDate(millis: Long) = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
    val last = LocalDate.parse(lastDate)
    val first = firstDate?.let { LocalDate.parse(it) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = runCatching { toMillis(initial) }.getOrDefault(toMillis(lastDate)),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = toDate(utcTimeMillis)
                return !date.isAfter(last) && (first == null || !date.isBefore(first))
            }
            override fun isSelectableYear(year: Int): Boolean = year <= last.year && (first == null || year >= first.year)
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(toDate(it).toString()) }; onDismiss() }) { Text(tr("OK")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    ) { DatePicker(state = state) }
}

/** Kind and date-range filters of the transaction history. */
@Composable fun HistoryFilters(s: AdminState, vm: AdminViewModel) {
    val today = LocalDate.parse(businessDate())
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("" to "All", "SALE" to "Sale", "PURCHASE" to "Purchase", "EXPENSE" to "Expense", "BYPRODUCT" to "Other sale").forEach { (kind, label) ->
            FilterChip(
                selected = s.historyKind == kind,
                onClick = { vm.setHistoryKind(kind) },
                label = { Text(tr(label)) },
                enabled = !s.loading && !s.saving
            )
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val ranges: List<Triple<String, String?, String?>> = listOf(
            Triple("Latest", null, null),
            Triple("Today", today.toString(), today.toString()),
            Triple("7 days", today.minusDays(6).toString(), today.toString()),
            Triple("This month", today.withDayOfMonth(1).toString(), today.toString())
        )
        ranges.forEach { (label, start, end) ->
            FilterChip(
                selected = s.historyStart == start && s.historyEnd == end,
                onClick = { vm.setHistoryRange(start, end) },
                label = { Text(tr(label)) },
                enabled = !s.loading && !s.saving
            )
        }
    }
}

@Composable fun SecondaryScreen(s: AdminState, vm: AdminViewModel, confirmClose: (Day) -> Unit) {
    when (s.page) {
        Page.STOCK -> s.stock?.let { stock ->
            val countLabel = if (s.business?.type == "LPG") "Quantity (cylinders)" else "Quantity (birds)"
            Panel { Icon(Icons.Default.Inventory2, null, tint = Green, modifier = Modifier.size(38.dp)); DataRow(if (stock.unit == "birds") "Live birds" else "Remaining stock", if (stock.unit == "birds") stock.live_birds.toString() else "${stock.quantity} ${stock.unit}")
                if (stock.unit == "kg" && stock.count > 0) DataRow(countLabel, stock.count.toString())
                if (stock.unit != "birds") DataRow("Inventory value", rupees(stock.inventory_cost)) }
            Text("Stock changes when you record operations. Negative stock is not allowed.", color = Muted, fontSize = 12.sp)
        }
        Page.LEDGER -> {
            // Single transactions by default; the day overview stays one tap away.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Transactions" to "TRANSACTIONS", "Days" to "DAYS").forEach { (label, view) ->
                    FilterChip(
                        selected = s.historyView == view,
                        onClick = { vm.setHistoryView(view) },
                        label = { Text(tr(label)) },
                        enabled = !s.loading && !s.saving
                    )
                }
            }
            if (s.historyView == "DAYS") {
                if (s.days.isEmpty() && !s.loading) Empty()
                s.days.forEach { day -> Card(onClick = { vm.openDay(day) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row { Text(day.date, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold); Status(day.status) }
                        DataRow("Revenue", rupees(day.revenue)); DataRow("Net profit", rupees(day.profit))
                        day.variance?.takeIf { it != 0L }?.let { Text("Corrected after settlement — difference ${rupees(it)}", color = Muted, fontSize = 11.sp) }
                    }
                } }
            } else {
                HistoryFilters(s, vm)
                val unit = "kg"
                if (s.operations.isEmpty() && !s.loading) Empty("No transactions in this range")
                s.operations.forEach { row -> TransactionRow(row, unit) { vm.openOperation(row, s.page) } }
            }
            MoreButton(s, vm)
        }
        Page.DAY -> {
            var pickDay by remember { mutableStateOf(false) }
            DayStepper(s, vm) { pickDay = true }
            val day = s.day
            if (day != null) {
                Panel {
                    Status(day.status)
                    DataRow("Revenue", rupees(day.revenue)); DataRow("Cost of sales", rupees(day.cost)); DataRow("Expenses", rupees(day.expenses)); DataRow("Net profit", rupees(day.profit), Green)
                    day.settled_net_profit?.let { DataRow("Profit settled with investors", rupees(it)) }
                    day.variance?.takeIf { it != 0L }?.let { DataRow("Difference after correction", rupees(it), if (it < 0) Chicken else Green) }
                }
                if (day.settled_net_profit != null) {
                    Text(tr("This date is already settled. A super-admin correction updates these records and this summary only; the payout that was made stays unchanged, and any difference is shown above."), color = Muted, fontSize = 12.sp)
                } else if (day.status == "OPEN") {
                    Text(tr("This date closes automatically at midnight (Pakistan time). You can also close it now."), color = Muted, fontSize = 12.sp)
                }
                if (day.status == "OPEN") Button(onClick = { confirmClose(day) }, enabled = !s.saving, modifier = Modifier.fillMaxWidth()) { Text(tr("Close day")) }
            } else if (s.dayRelation == "FUTURE") {
                Panel { DataRow("Date", s.dayDate); Text(tr("This date has not started. Sales, purchases and expenses can only be recorded on the day itself."), color = Muted, fontSize = 12.sp) }
            } else {
                Panel { DataRow("Date", s.dayDate); Text(tr("No records for this date."), color = Muted, fontSize = 12.sp) }
            }
            val unit = "kg"
            if (s.operations.isEmpty() && !s.loading) Empty("No transactions on this date")
            s.operations.forEach { row -> TransactionRow(row, unit) { vm.openOperation(row, s.page) } }
            if (pickDay) DayPickerDialog(s.dayDate, s.dayFirstDate, s.dayLastDate ?: businessDate(), { pickDay = false }) { vm.openDayAt(it) }
        }
        Page.OPERATION -> s.operationDetail?.let { od ->
            val op = od.operation
            val countLabel = if (od.business.type == "LPG") "Quantity (cylinders)" else "Quantity (birds)"
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(tr(kindLabel(op.kind)), color = Green, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f)); Status(od.day.status) }
                DataRow("Date", od.day.date)
                DataRow("Business", od.business.name)
                if (op.kind != "EXPENSE") DataRow("Weight (kg)", op.quantity)
                op.count?.takeIf { it > 0 }?.let { DataRow(countLabel, it.toString()) }
                row_price_per_unit(op)?.let { DataRow("Price per unit", it) }
                DataRow("Total amount", rupees(op.amount))
                if (op.kind == "SALE") DataRow("Cost of sales", rupees(op.cost))
                if (op.kind == "SALE") DataRow("Profit contribution", rupees(op.amount - op.cost), Green)
                op.category?.takeIf { it.isNotBlank() }?.let { DataRow("Category", it) }
                op.channel?.let { DataRow("Sale channel", tr(it.lowercase().replaceFirstChar { c -> c.uppercase() })) }
                if (op.note.isNotEmpty()) Text(op.note, style = MaterialTheme.typography.bodyMedium)
                Text("Recorded ${op.created_at}", fontSize = 11.sp, color = Muted)
            }
            Text("Profit is calculated on the total price above, not on the weight or count.", color = Muted, fontSize = 12.sp)
            if (s.user?.role == "SUPERADMIN") {
                Button(onClick = { vm.openEditOperation() }, enabled = !s.saving, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text(tr("Edit transaction")) }
                if (od.day.status == "OPEN") {
                    Text("This date is still open. Saving a correction updates the date summary immediately.", color = Muted, fontSize = 12.sp)
                } else {
                    Text("This date is already settled. Saving a correction rebuilds the date summary; the settled payout is not changed and the difference stays visible on the date.", color = Muted, fontSize = 12.sp)
                }
            } else {
                Text("Only the super admin can correct a previous-date transaction. Managers can add records to today's open date only.", color = Muted, fontSize = 12.sp)
            }
        }
        Page.SUPPLIERS -> {
            Button(onClick = { vm.openForm(FormKind.SUPPLIER) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text(tr("Add supplier")) }
            if (s.suppliers.isEmpty() && !s.loading) Empty()
            s.suppliers.forEach { supplier -> LinkRow(supplier.name, Icons.Default.LocalShipping, supplier.phone, translate = false) { vm.openSupplier(supplier) } }
        }
        Page.BILLS, Page.EXPENSES -> {
            if (s.page == Page.EXPENSES) DateFilters(s, vm)
            else Text("Purchase records, not outstanding payable balances.", color = Muted, fontSize = 12.sp)
            if (s.operations.isEmpty() && !s.loading) Empty()
            s.operations.forEach { OperationCard(it) { vm.openOperation(it, s.page) } }; MoreButton(s, vm)
        }
        Page.BATCHES -> {
            Button(onClick = { vm.openForm(FormKind.BATCH_CREATE) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Create batch")) }
            if (s.batches.isEmpty() && !s.loading) Empty()
            s.batches.forEach { batch -> BatchCard(batch) { vm.openBatch(batch) } }; MoreButton(s, vm)
        }
        Page.SETTLEMENTS -> {
            if (s.settlements.isEmpty() && !s.loading) Empty()
            s.settlements.forEach { row -> Panel { Text(row.created_at, color = Muted, fontSize = 12.sp); DataRow("Net profit", rupees(row.net_profit)); DataRow("Investor distribution", rupees(row.distributed), Green); DataRow("Retained", rupees(row.retained)); Text(row.source, color = Muted, fontSize = 10.sp) } }; MoreButton(s, vm)
        }
        else -> Unit
    }
}
@Composable fun SettingsScreen(s: AdminState, vm: AdminViewModel, logout: () -> Unit) {
    Surface(color = Forest, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.AccountCircle, null, tint = Color.White, modifier = Modifier.size(52.dp))
            Column { Text(s.user?.name.orEmpty(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp); Text(s.user?.phone.orEmpty(), color = Color.White.copy(alpha = .7f)); Text(s.user?.role.orEmpty(), color = Gold, fontSize = 11.sp) }
        }
    }
    LinkRow("Edit profile", Icons.Default.Person, "Name · English / اردو") { vm.openForm(FormKind.PROFILE) }
    LinkRow("Change password", Icons.Default.Lock) { vm.openForm(FormKind.PASSWORD) }
    LinkRow("Sign out", Icons.Default.Logout) { logout() }
    SectionTitle("About this app")
    Panel {
        Text("Ahsan Traders", fontWeight = FontWeight.Bold); Text("Version 1.0.0 • Native Android", color = Muted)
        Text("Manage daily business operations and batch settlements. Server confirmation is required for every financial change.", color = Muted, fontSize = 12.sp)
        Text("Server: ${vm.server}", color = Muted, fontSize = 12.sp)
        Text("The backend address is set at build time (APP_SERVER_URL environment variable or appServerUrl in gradle.properties). Notifications, customer accounts and real payment-provider integrations are not available in this MVP.", color = Muted, fontSize = 12.sp)
    }
}
