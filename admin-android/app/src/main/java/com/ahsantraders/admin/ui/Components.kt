@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ahsantraders.admin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahsantraders.admin.R
import com.ahsantraders.admin.data.*

fun sectorIcon(type: String): ImageVector = when (type) { "CHICKEN" -> Icons.Default.Restaurant; "LPG" -> Icons.Default.LocalFireDepartment; else -> Icons.Default.Eco }
@Composable fun Brand(compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(R.drawable.ic_brand), contentDescription = "Ahsan Traders", tint = Color.Unspecified, modifier = Modifier.size(if (compact) 40.dp else 64.dp))
        Column {
            Text("AHSAN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (compact) 18.sp else 28.sp, letterSpacing = 3.sp)
            Text("T R A D E R S", color = Gold, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 9.sp else 12.sp)
        }
    }
}
@Composable fun SectionTitle(title: String, subtitle: String? = null, translate: Boolean = true) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(if (translate) tr(title) else title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
}
@Composable fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable fun Metric(label: String, value: String, icon: ImageVector = Icons.Default.Payments, previous: Long? = null, current: Long? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).background(Green, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(27.dp)) }
            Column(Modifier.weight(1f)) {
                Text(tr(label), style = MaterialTheme.typography.labelLarge, color = Muted)
                Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Forest)
            }
            if (previous != null && current != null) {
                val percent = if (previous > 0) (current.toDouble() - previous) / previous * 100 else null
                Column(horizontalAlignment = Alignment.End) {
                    Text(percent?.let { "%+.0f%%".format(it) } ?: "—", color = if (current >= previous) Green else Chicken, fontWeight = FontWeight.Bold)
                    Text("vs yesterday", color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}
@Composable fun DataRow(label: String, value: String, accent: Color = Ink) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(label), modifier = Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold, color = accent, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable fun Status(value: String) {
    val color = when (value) { "ACTIVE", "OPEN" -> Green; "FUNDING" -> Lpg; else -> Muted }
    Surface(color = color.copy(alpha = .10f), shape = RoundedCornerShape(8.dp)) {
        Text(tr(value.lowercase().replaceFirstChar { it.uppercase() }), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}
@Composable fun ActionTile(label: String, icon: ImageVector, color: Color = Green, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.heightIn(min = 90.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = Color.White); Text(tr(label), color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}
@Composable fun LinkRow(title: String, icon: ImageVector, subtitle: String? = null, translate: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.White, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = Green)
            Column(Modifier.weight(1f)) {
                Text(if (translate) tr(title) else title, fontWeight = FontWeight.Medium)
                subtitle?.let { Text(it, fontSize = 12.sp, color = Muted) }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Muted, modifier = Modifier.size(18.dp))
        }
    }
}
@Composable fun Empty(message: String = "No records yet") {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.Inventory2, null, tint = Muted, modifier = Modifier.size(40.dp))
        Text(tr(message), color = Muted)
    }
}
@Composable fun Banner(message: String, error: Boolean, onDismiss: () -> Unit) {
    Surface(color = if (error) Color(0xFFFFEBE9) else Mint, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = if (error) Color(0xFF912727) else Forest, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, tr("Dismiss"), modifier = Modifier.size(18.dp)) }
        }
    }
}
@Composable fun Selection(label: String, value: String, choices: List<Pair<String, String>>, enabled: Boolean = true, translateChoices: Boolean = false, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = !expanded }) {
        OutlinedTextField(value = choices.find { it.first == value }?.second?.let { if (translateChoices) tr(it) else it } ?: value, onValueChange = {}, readOnly = true,
            label = { Text(tr(label)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(), enabled = enabled, shape = RoundedCornerShape(12.dp))
        ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (id, title) -> DropdownMenuItem(text = { Text(if (translateChoices) tr(title) else title) }, onClick = { onSelect(id); expanded = false }) }
        }
    }
}
@Composable fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Default.WarningAmber, null) },
        title = { Text(tr(title)) }, text = { Text(message) }, confirmButton = { Button(onClick = onConfirm) { Text(tr("Confirm")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } })
}
@Composable fun MoreButton(s: AdminState, vm: AdminViewModel) {
    if (s.more) OutlinedButton(onClick = { vm.refresh(true) }, enabled = !s.loading && !s.saving, modifier = Modifier.fillMaxWidth()) { Text(tr("Load more")) }
}
