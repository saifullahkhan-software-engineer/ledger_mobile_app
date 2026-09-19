@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ahsantraders.admin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahsantraders.admin.data.*
import kotlinx.coroutines.launch

@Composable fun AdminApp(s: AdminState, vm: AdminViewModel, onLogout: (() -> Unit)? = null) {
    if (s.user == null) { LoginScreen(s, vm); return }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var chooseKind by remember { mutableStateOf<FormKind?>(null) }
    var closeDay by remember { mutableStateOf<Day?>(null) }
    var startBatch by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    fun back() { if (s.draft != null) discard = true else vm.back() }
    BackHandler(enabled = s.page != Page.HOME || s.draft != null || drawer.isOpen || s.saving) {
        if (!s.saving) { if (drawer.isOpen) scope.launch { drawer.close() } else back() }
    }
    ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = !s.saving && s.draft == null, drawerContent = {
        ModalDrawerSheet(drawerContainerColor = Paper) {
            Column(Modifier.fillMaxWidth().background(Forest).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Brand(true)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.AccountCircle, null, tint = Color.White, modifier = Modifier.size(42.dp))
                    Column {
                        Text(s.user.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(s.user.phone, color = Color.White.copy(alpha = .75f), fontSize = 12.sp)
                        Text(s.user.role, color = Gold, fontSize = 11.sp)
                    }
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).weight(1f).padding(12.dp)) {
                NavigationDrawerItem(label = { Text(tr("Dashboard")) }, selected = s.page == Page.HOME, icon = { Icon(Icons.Default.Home, null) }, onClick = { scope.launch { drawer.close() }; vm.go(Page.HOME) })
                s.businesses.forEach { b -> NavigationDrawerItem(label = { Text(b.name) }, selected = s.business?.id == b.id && s.page == Page.BUSINESS,
                    icon = { SectorIcon(b.type, tint = sectorColor(b.type), modifier = Modifier.size(24.dp)) }, onClick = { scope.launch { drawer.close() }; vm.go(Page.BUSINESS, b) }) }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                if (s.user?.role == "SUPERADMIN") {
                    NavigationDrawerItem(label = { Text(tr("Users")) }, selected = s.page in listOf(Page.USERS, Page.USER, Page.ADD_USER), icon = { Icon(Icons.Default.People, null) }, onClick = { scope.launch { drawer.close() }; vm.openUsers() })
                    NavigationDrawerItem(label = { Text(tr("Screen icons")) }, selected = s.page == Page.ICONS, icon = { Icon(Icons.Default.Image, null) }, onClick = { scope.launch { drawer.close() }; vm.openIcons() })
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                }
                NavigationDrawerItem(label = { Text(tr("Reports")) }, selected = s.page == Page.REPORTS, icon = { Icon(Icons.Default.BarChart, null) }, onClick = { scope.launch { drawer.close() }; vm.go(Page.REPORTS) })
                NavigationDrawerItem(label = { Text(tr("Settings")) }, selected = s.page == Page.SETTINGS, icon = { Icon(Icons.Default.Settings, null) }, onClick = { scope.launch { drawer.close() }; vm.go(Page.SETTINGS) })
                NavigationDrawerItem(label = { Text(tr("Sign out")) }, selected = false, icon = { Icon(Icons.Default.Logout, null) }, onClick = { scope.launch { drawer.close() }; logout = true })
            }
            Text("AHSAN TRADERS  •  ADMIN", modifier = Modifier.fillMaxWidth().background(Mint).padding(20.dp), color = Forest, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(containerColor = Paper, topBar = {
                TopAppBar(title = {
                    if (s.page == Page.HOME && s.draft == null) Brand(true)
                    else Text(tr(s.draft?.let { formTitle(it.kind) } ?: pageTitle(s.page)), maxLines = 1, style = MaterialTheme.typography.titleMedium)
                }, navigationIcon = {
                    IconButton(onClick = { if (s.page == Page.HOME && s.draft == null) scope.launch { drawer.open() } else back() }, enabled = !s.saving) {
                        Icon(if (s.page == Page.HOME && s.draft == null) Icons.Default.Menu else Icons.AutoMirrored.Filled.ArrowBack, tr("Back"))
                    }
                }, actions = {
                    if (s.draft == null) IconButton(onClick = { vm.refresh() }, enabled = !s.loading && !s.saving) { Icon(Icons.Default.Refresh, tr("Refresh")) }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Forest, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White))
            }, bottomBar = {
                if (s.draft == null) NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(selected = s.page == Page.HOME, onClick = { vm.go(Page.HOME) }, icon = { Icon(Icons.Default.Home, null) }, label = { Text(tr("Home")) }, enabled = !s.saving)
                    NavigationBarItem(selected = s.page == Page.LEDGER || s.page == Page.DAY, onClick = { chooseKind = FormKind.SALE }, icon = { Icon(Icons.Default.PointOfSale, null) }, label = { Text(tr("Sales")) }, enabled = !s.saving)
                    NavigationBarItem(selected = s.page == Page.EXPENSES, onClick = { chooseKind = FormKind.EXPENSE }, icon = { Icon(Icons.Default.AccountBalanceWallet, null) }, label = { Text(tr("Expenses")) }, enabled = !s.saving)
                    NavigationBarItem(selected = s.page == Page.REPORTS, onClick = { vm.go(Page.REPORTS) }, icon = { Icon(Icons.Default.BarChart, null) }, label = { Text(tr("Reports")) }, enabled = !s.saving)
                    NavigationBarItem(selected = s.page == Page.SETTINGS, onClick = { vm.go(Page.SETTINGS) }, icon = { Icon(Icons.Default.MoreHoriz, null) }, label = { Text(tr("More")) }, enabled = !s.saving)
                }
            }) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    if (s.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    // Keying scroll to page/context avoids retaining a long previous list's scroll position.
                    key(s.page, s.business?.id, s.draft?.kind, s.batch?.id, s.day?.id, s.supplier?.id) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            s.error?.let { Banner(it, true, vm::dismissError); if (s.draft == null) OutlinedButton(onClick = { vm.refresh() }, enabled = !s.loading) { Text(tr("Try again")) } }
                            s.notice?.let { Banner(it, false, vm::dismissNotice) }
                            if (s.draft != null) FormScreen(s, vm)
                            else when (s.page) {
                                Page.HOME -> HomeScreen(s, vm) { chooseKind = it }
                                Page.BUSINESS -> BusinessScreen(s, vm) { closeDay = it }
                                Page.BATCH -> BatchScreen(s, vm) { startBatch = true }
                                Page.REPORTS -> ReportsScreen(s, vm)
                                Page.SETTINGS -> SettingsScreen(s, vm) { logout = true }
                                Page.USERS -> UsersScreen(s, vm)
                                Page.USER -> UserDetailScreen(s, vm)
                                Page.ADD_USER -> AddUserScreen(s, vm)
                                Page.ICONS -> IconsScreen(s, vm)
                                else -> SecondaryScreen(s, vm) { closeDay = it }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
            if (s.saving) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .15f)).pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            }, contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(18.dp), shadowElevation = 10.dp) { Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(26.dp)); Text("Waiting for server…") } }
            }
        }
    }
    chooseKind?.let { kind -> AlertDialog(onDismissRequest = { chooseKind = null }, title = { Text(tr("Choose a business")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val choices = s.businesses.filter { it.type != "BROILER" }
            if (choices.isEmpty()) Text("No daily businesses assigned. For broiler costs, open a batch and add a daily record.")
            choices.forEach { b -> TextButton(onClick = { chooseKind = null; vm.go(Page.BUSINESS, b); vm.openForm(kind) }, modifier = Modifier.fillMaxWidth()) { Text(b.name) } }
            if (kind == FormKind.EXPENSE) Text("Broiler expenses are recorded inside an active batch.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { chooseKind = null }) { Text(tr("Cancel")) } }) }
    closeDay?.let { day -> ConfirmDialog("Close day", "Close ${day.date}? Net profit is ${rupees(day.profit)}. This finalizes the records and distributes eligible investor profit immediately. You cannot reopen this day in the app.", { closeDay = null }) { closeDay = null; vm.closeDay(day) } }
    if (startBatch) ConfirmDialog("Start batch", "Starting this batch closes its funding window and locks investor ownership. Continue?", { startBatch = false }) { startBatch = false; vm.startBatch() }
    if (logout) ConfirmDialog("Sign out", "Sign out and revoke existing sessions? If offline, only this device can be signed out.", { logout = false }) { logout = false; vm.logout(); onLogout?.invoke() }
    if (discard) ConfirmDialog("Cancel", "Discard this form? Unsaved fields will be lost. If a previous submission timed out, check the records before creating a new transaction.", { discard = false }) { discard = false; vm.back() }
}
fun pageTitle(page: Page): String = when (page) {
    Page.HOME -> "Dashboard"; Page.BUSINESS -> "Business summary"; Page.LEDGER, Page.DAY -> "Transaction history"
    Page.STOCK -> "Stock"; Page.SUPPLIERS -> "Suppliers"; Page.BILLS -> "Supplier bills"; Page.EXPENSES -> "Expenses"
    Page.REPORTS -> "Reports"; Page.SETTINGS -> "Settings"; Page.BATCHES -> "Batches"; Page.BATCH -> "Batch history"; Page.SETTLEMENTS -> "Settlement history"
    Page.USERS -> "Users & access"; Page.USER -> "User details"; Page.ADD_USER -> "Add user"; Page.ICONS -> "Screen icons"
}
@Composable fun LoginScreen(s: AdminState, vm: AdminViewModel, onLoginSuccess: (() -> Unit)? = null) {
    var server by rememberSaveable { mutableStateOf(vm.server) }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val busy = s.loading || s.saving
    // Successful login populates s.user; move on to the dashboard then.
    LaunchedEffect(s.user) { if (s.user != null) onLoginSuccess?.invoke() }
    Column(Modifier.fillMaxSize().background(Paper).navigationBarsPadding().verticalScroll(rememberScrollState()).imePadding()) {
        Column(Modifier.fillMaxWidth().background(Forest).statusBarsPadding().padding(horizontal = 28.dp, vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Brand()
            Text("Your business.\nAt your fingertips.", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("CHICKEN  /  LPG  /  BROILER", color = Gold, fontSize = 11.sp, letterSpacing = 2.sp)
        }
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("Sign in", "Use the administrator account created with the backend seed command.")
            s.error?.let { Banner(it, true, vm::dismissError) }
            s.notice?.let { Banner(it, false, vm::dismissNotice) }
            OutlinedTextField(server, { server = it }, label = { Text(tr("Server address")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), supportingText = { Text("Emulator: http://10.0.2.2:8000 · Phone: computer LAN IP") })
            OutlinedTextField(phone, { phone = it }, label = { Text(tr("Phone number")) }, placeholder = { Text("03001234567") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), supportingText = { Text("0300… or +92300… both work") })
            OutlinedTextField(password, { password = it }, label = { Text(tr("Password")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle password visibility") } })
            Button(onClick = { vm.login(server, phone, password) }, modifier = Modifier.fillMaxWidth().height(54.dp), enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) else Text(tr("Sign in"))
            }
            Text("Admin access only. The backend must be running. No financial changes are queued while offline.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
