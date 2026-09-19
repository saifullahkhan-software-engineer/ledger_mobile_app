package com.ahsantraders.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahsantraders.admin.data.*

// ---------------------------------------------------------------------------
// Users & access (SUPERADMIN only; every action goes through the backend).
// ---------------------------------------------------------------------------

@Composable fun UsersScreen(s: AdminState, vm: AdminViewModel) {
    val search = s.userSearch
    val filter = s.userFilter
    val filtered = s.users.filter { u ->
        val roleMatch = filter.isEmpty() || u.role == filter
        val searchMatch = search.isBlank() ||
            u.name.contains(search.trim(), ignoreCase = true) ||
            u.phone.contains(search.trim())
        roleMatch && searchMatch
    }
    Button(
        onClick = { vm.openAddUser() },
        enabled = !s.saving,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text(tr("Add user")) }

    OutlinedTextField(
        value = search,
        onValueChange = { vm.setUserSearch(it) },
        label = { Text(tr("Search users")) },
        placeholder = { Text("Name or phone") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = filter.isEmpty(),
            onClick = { vm.setUserFilter("") },
            label = { Text(tr("All")) },
            enabled = !s.saving
        )
        FilterChip(
            selected = filter == "INVESTOR",
            onClick = { vm.setUserFilter("INVESTOR") },
            label = { Text("${tr("Investors")} (${s.users.count { it.role == "INVESTOR" }})") },
            enabled = !s.saving
        )
        FilterChip(
            selected = filter == "ADMIN",
            onClick = { vm.setUserFilter("ADMIN") },
            label = { Text("${tr("Managers")} (${s.users.count { it.role == "ADMIN" }})") },
            enabled = !s.saving
        )
        FilterChip(
            selected = filter == "SUPERADMIN",
            onClick = { vm.setUserFilter("SUPERADMIN") },
            label = { Text("${tr("Super admin")} (${s.users.count { it.role == "SUPERADMIN" }})") },
            enabled = !s.saving
        )
    }

    if (s.users.isEmpty() && !s.loading) Empty("No users on the server yet")
    if (filtered.isEmpty() && s.users.isNotEmpty() && !s.loading) Empty("No users match your search")
    filtered.forEach { user -> UserCard(user, s.businesses) { vm.openUser(user) } }
}

@Composable private fun UserCard(user: UserOut, businesses: List<Business>, onClick: () -> Unit) {
    val businessNames = user.assigned_businesses.mapNotNull { id -> businesses.find { it.id == id }?.name }
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).background(
                    if (user.role == "SUPERADMIN") Gold else if (user.role == "ADMIN") Lpg else Forest,
                    CircleShape
                ), contentAlignment = Alignment.Center
            ) {
                Text(if (user.name.isNotEmpty()) user.name.first().uppercase() else "U", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(user.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                    RoleBadge(user.role)
                }
                Text(user.phone, color = Muted, fontSize = 12.sp)
                if (businessNames.isNotEmpty()) Text(businessNames.joinToString(", "), color = Muted, fontSize = 11.sp, maxLines = 1)
                Text(
                    "KYC: ${user.kyc_status}",
                    color = if (user.kyc_status == "VERIFIED") Green else Color(0xFFB97A00),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable private fun RoleBadge(role: String) {
    val (bg, fg) = when (role) {
        "SUPERADMIN" -> Gold.copy(alpha = .25f) to Color(0xFF8E6C00)
        "ADMIN" -> Lpg.copy(alpha = .15f) to Lpg
        else -> Green.copy(alpha = .15f) to Green
    }
    Surface(color = bg, shape = RoundedCornerShape(10.dp)) {
        Text(role, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable fun UserDetailScreen(s: AdminState, vm: AdminViewModel) {
    val user = s.userDetail ?: return
    val isSuper = s.user?.role == "SUPERADMIN"
    Surface(color = Forest, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.AccountCircle, null, tint = Color.White, modifier = Modifier.size(52.dp))
            Column {
                Text(user.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(user.phone, color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
                Text(user.role, color = Gold, fontSize = 11.sp)
            }
        }
    }
    SectionTitle("Details")
    Panel {
        DataRow("User ID", user.id)
        DataRow("Role", user.role)
        DataRow("KYC status", user.kyc_status)
        val names = user.assigned_businesses.mapNotNull { id -> s.businesses.find { it.id == id }?.name }
        if (names.isNotEmpty()) DataRow("Assigned businesses", names.joinToString(", "))
    }

    if (isSuper && user.role != "SUPERADMIN") {
        SectionTitle("Super admin actions")
        Panel {
            Selection("Role", user.role, listOf("INVESTOR" to "Investor", "ADMIN" to "Manager"), !s.saving, translateChoices = true) { vm.changeUserRole(it) }
        }
        if (user.role == "ADMIN") {
            Panel {
                Text(tr("Business access"), fontWeight = FontWeight.Bold)
                s.businesses.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = b.id in user.assigned_businesses,
                            onCheckedChange = { vm.setUserBusinesses(b.id, it) },
                            enabled = !s.saving
                        )
                        Text(b.name)
                    }
                }
            }
        }
        if (user.kyc_status != "VERIFIED") {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.VerifiedUser, null, tint = Forest)
                    Column(Modifier.weight(1f)) {
                        Text(tr("Verify KYC"), fontWeight = FontWeight.Bold)
                        Text("Current: ${user.kyc_status} — required before investing.", color = Muted, fontSize = 12.sp)
                    }
                    Button(onClick = { vm.verifyUserKyc() }, enabled = !s.saving) { Text(tr("Verify")) }
                }
            }
        }
    }
}

@Composable fun AddUserScreen(s: AdminState, vm: AdminViewModel) {
    val draft = s.userDraft ?: UserDraft()
    var visible by remember { mutableStateOf(false) }

    SectionTitle("Add user", "Managers run businesses; investors buy shares.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = draft.role == "ADMIN",
            onClick = { vm.setUserRole("ADMIN") },
            label = { Text(tr("Manager")) },
            leadingIcon = { Icon(Icons.Default.ManageAccounts, null, Modifier.size(18.dp)) },
            enabled = !s.saving
        )
        FilterChip(
            selected = draft.role == "INVESTOR",
            onClick = { vm.setUserRole("INVESTOR") },
            label = { Text(tr("Investor")) },
            leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(18.dp)) },
            enabled = !s.saving
        )
    }

    OutlinedTextField(
        value = draft.values["name"].orEmpty(),
        onValueChange = { vm.setUserField("name", it) },
        label = { Text(tr("Name")) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = !s.saving
    )
    OutlinedTextField(
        value = draft.values["phone"].orEmpty(),
        onValueChange = { vm.setUserField("phone", it) },
        label = { Text(tr("Phone number")) },
        placeholder = { Text("03001234567") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        supportingText = { Text("0300… or +92300… both work") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !s.saving
    )
    OutlinedTextField(
        value = draft.values["password"].orEmpty(),
        onValueChange = { vm.setUserField("password", it) },
        label = { Text(tr("Password")) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle password")
            }
        },
        supportingText = { Text("10+ characters") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !s.saving
    )

    if (draft.role == "ADMIN") {
        SectionTitle("Assign to businesses", "At least one business is recommended.")
        if (s.businesses.isEmpty()) Text("No businesses configured yet.", color = Muted, fontSize = 12.sp)
        s.businesses.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = b.id in draft.businesses,
                    onCheckedChange = { vm.toggleUserBusiness(b.id) },
                    enabled = !s.saving
                )
                Text(b.name)
            }
        }
    }

    Button(
        onClick = { vm.submitUser() },
        enabled = !s.saving,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text(if (draft.role == "ADMIN") tr("Create manager") else tr("Create investor")) }
    TextButton(onClick = { vm.back() }, enabled = !s.saving, modifier = Modifier.fillMaxWidth()) { Text(tr("Cancel")) }
}

