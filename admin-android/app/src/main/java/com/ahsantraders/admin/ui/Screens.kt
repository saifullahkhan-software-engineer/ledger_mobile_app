package com.ahsantraders.admin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahsantraders.admin.data.*
import java.time.LocalDate

@Composable fun HomeScreen(s: AdminState, vm: AdminViewModel, choose: (FormKind) -> Unit) {
    Text(if (s.language == "ur") "خوش آمدید، ${s.user?.name.orEmpty()}" else "Welcome, ${s.user?.name.orEmpty()}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(if (s.language == "ur") "اپنے کاروبار ایک جگہ سنبھالیں" else "Your businesses. One clear picture.", color = Muted)
    s.dashboard?.let { report ->
        SectionTitle("Today’s overview", "${report.start} · Asia/Karachi")
        Metric("Total sales", rupees(report.total_sales), Icons.Default.Payments, report.yesterday?.total_sales, report.total_sales)
        Metric("Net profit", rupees(report.total_profit), Icons.Default.TrendingUp, report.yesterday?.total_profit, report.total_profit)
        Text("Open-day totals are provisional. Broiler revenue is recognized at harvest.", color = Muted, fontSize = 11.sp)
    }
    SectionTitle("Your businesses")
    if (s.businesses.isEmpty() && !s.loading) Empty("No businesses assigned. Ask your owner for access.")
    s.businesses.chunked(3).forEach { group ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            group.forEach { business ->
                Card(onClick = { vm.go(Page.BUSINESS, business) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = sectorColor(business.type))) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        SectorIcon(business.type, tint = Color.White, modifier = Modifier.size(35.dp))
                        Text(tr(sectorName(business.type)), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
        ActionTile("Add sale", Icons.Default.AddCircle, modifier = Modifier.weight(1f)) { choose(FormKind.SALE) }
        ActionTile("Add expense", Icons.Default.AccountBalanceWallet, Color(0xFFE58B19), Modifier.weight(1f)) { choose(FormKind.EXPENSE) }
    }
    LinkRow("Reports", Icons.Default.BarChart, "Daily, weekly and monthly performance") { vm.go(Page.REPORTS) }
    Surface(color = Forest, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Grow Together, With Trust", color = Gold, fontWeight = FontWeight.Medium)
            Text("AHSAN TRADERS  •  ADMIN", color = Color.White.copy(alpha = .65f), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable fun BusinessScreen(s: AdminState, vm: AdminViewModel, confirmClose: (Day) -> Unit) {
    val b = s.business ?: return
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(sectorColor(b.type), Forest))).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SectorIcon(b.type, tint = Color.White, modifier = Modifier.size(54.dp))
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
                DataRow("Purchased", "${summary.purchased_quantity} ${if (b.type == "LPG") "cylinders" else "kg"}")
                DataRow("Sold", "${summary.sold_quantity} ${if (b.type == "LPG") "cylinders" else "kg"}")
                if (b.type == "CHICKEN") DataRow("Pota-Kaliji sale", rupees(summary.byproduct_revenue))
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
            ActionTile("Batches", Icons.Default.Eco, Lpg, Modifier.weight(1f)) { vm.go(Page.BATCHES) }
        }
        summary?.batches?.forEach { batch -> BatchCard(batch) { vm.openBatch(batch) } }
    } else if (summary?.day?.status != "CLOSED") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Add sale", Icons.Default.AddCircle, modifier = Modifier.weight(1f)) { vm.openForm(FormKind.SALE) }
            ActionTile("Add purchase", Icons.Default.ShoppingCart, Lpg, Modifier.weight(1f)) { vm.openForm(FormKind.PURCHASE) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Add expense", Icons.Default.AccountBalanceWallet, Color(0xFFE58B19), Modifier.weight(1f)) { vm.openForm(FormKind.EXPENSE) }
            if (b.type == "CHICKEN") ActionTile("Pota-Kaliji sale", Icons.Default.Restaurant, Chicken, Modifier.weight(1f)) { vm.openForm(FormKind.BYPRODUCT) }
        }
    }
    LinkRow("Stock", Icons.Default.Inventory2) { vm.go(Page.STOCK) }
    if (b.type != "BROILER") {
        LinkRow("Transaction history", Icons.Default.ReceiptLong) { vm.go(Page.LEDGER) }
        LinkRow("Expenses", Icons.Default.AccountBalanceWallet) { vm.go(Page.EXPENSES) }
        LinkRow("Suppliers", Icons.Default.LocalShipping) { vm.go(Page.SUPPLIERS) }
        summary?.day?.takeIf { it.status == "OPEN" }?.let { day ->
            OutlinedButton(onClick = { confirmClose(day) }, enabled = !s.saving, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text(tr("Close day")) }
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
@Composable fun OperationCard(row: Operation) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(row.kind.replace('_', ' '), color = Green, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f)); Text(rupees(row.amount), fontWeight = FontWeight.Bold) }
        if (row.kind != "EXPENSE") DataRow("Quantity", row.quantity)
        row.channel?.let { DataRow("Sale channel", tr(it.lowercase().replaceFirstChar { c -> c.uppercase() })) }
        if (row.note.isNotEmpty()) Text(row.note, style = MaterialTheme.typography.bodyMedium)
        Text(row.created_at, fontSize = 11.sp, color = Muted)
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
                    Text("Positive\nprofit mix", color = Muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Text("Chart shows positive profits only; losses remain included in totals below.", color = Muted, fontSize = 11.sp)
            }
        }
        SectionTitle("Business summary")
        Panel { DataRow("Total sales", rupees(report.total_sales)); DataRow("Total costs & expenses", rupees(report.total_cost_and_expenses)); HorizontalDivider(); DataRow("Net profit", rupees(report.total_profit), if (report.total_profit < 0) Chicken else Green) }
        if (report.businesses.any { it.open_days > 0 }) Text("Includes open days. These totals can change before settlement.", fontSize = 12.sp, color = Muted)
    }
}
@Composable fun SecondaryScreen(s: AdminState, vm: AdminViewModel, confirmClose: (Day) -> Unit) {
    when (s.page) {
        Page.STOCK -> s.stock?.let { stock ->
            Panel { Icon(Icons.Default.Inventory2, null, tint = Green, modifier = Modifier.size(38.dp)); DataRow(if (stock.unit == "birds") "Live birds" else "Remaining stock", if (stock.unit == "birds") stock.live_birds.toString() else "${stock.quantity} ${stock.unit}")
                if (stock.unit != "birds") DataRow("Inventory value", rupees(stock.inventory_cost)) }
            Text("Stock changes when you record operations. Negative stock is not allowed.", color = Muted, fontSize = 12.sp)
        }
        Page.LEDGER -> {
            if (s.days.isEmpty() && !s.loading) Empty()
            s.days.forEach { day -> Card(onClick = { vm.openDay(day) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row { Text(day.date, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold); Status(day.status) }
                    DataRow("Revenue", rupees(day.revenue)); DataRow("Net profit", rupees(day.profit))
                }
            } }; MoreButton(s, vm)
        }
        Page.DAY -> {
            s.day?.let { day -> Panel { Text(day.date, fontWeight = FontWeight.Bold); Status(day.status); DataRow("Revenue", rupees(day.revenue)); DataRow("Cost of sales", rupees(day.cost)); DataRow("Expenses", rupees(day.expenses)); DataRow("Net profit", rupees(day.profit), Green) }
                if (day.status == "OPEN") Button(onClick = { confirmClose(day) }, enabled = !s.saving, modifier = Modifier.fillMaxWidth()) { Text(tr("Close day")) }
            }
            if (s.operations.isEmpty() && !s.loading) Empty()
            s.operations.forEach { OperationCard(it) }
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
            s.operations.forEach { OperationCard(it) }; MoreButton(s, vm)
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
        Text("Ahsan Traders Admin", fontWeight = FontWeight.Bold); Text("Version 1.0.0 • Native Android", color = Muted)
        Text("Manage daily business operations and batch settlements. Server confirmation is required for every financial change.", color = Muted, fontSize = 12.sp)
        Text("Server: ${vm.server}", color = Muted, fontSize = 12.sp)
        Text("To change servers, sign out. Notifications, customer accounts and real payment-provider integrations are not available in this MVP.", color = Muted, fontSize = 12.sp)
    }
}
