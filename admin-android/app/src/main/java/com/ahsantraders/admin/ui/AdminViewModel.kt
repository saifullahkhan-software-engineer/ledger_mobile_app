package com.ahsantraders.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahsantraders.admin.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.LocalDate

enum class Page { HOME, BUSINESS, LEDGER, DAY, STOCK, SUPPLIERS, BILLS, EXPENSES, REPORTS, SETTINGS, BATCHES, BATCH, SETTLEMENTS, USERS, USER, ADD_USER, ICONS }
data class UserDraft(val role: String = "ADMIN", val values: Map<String, String> = emptyMap(), val businesses: Set<String> = emptySet())
data class AdminState(
    val user: Profile? = null, val loading: Boolean = false, val saving: Boolean = false,
    val error: String? = null, val notice: String? = null, val page: Page = Page.HOME,
    val businesses: List<Business> = emptyList(), val business: Business? = null,
    val dashboard: Report? = null, val report: Report? = null, val summary: Summary? = null,
    val stock: Stock? = null, val days: List<Day> = emptyList(), val day: Day? = null,
    val suppliers: List<Supplier> = emptyList(), val supplier: Supplier? = null,
    val operations: List<Operation> = emptyList(), val batches: List<Batch> = emptyList(),
    val batch: Batch? = null, val logs: List<BatchLog> = emptyList(), val settlements: List<Settlement> = emptyList(),
    val more: Boolean = false, val start: String = businessDate(), val end: String = businessDate(),
    val reportBusiness: String? = null, val draft: Draft? = null, val language: String = "en",
    val users: List<UserOut> = emptyList(), val userDetail: UserOut? = null, val userDraft: UserDraft? = null,
    val userSearch: String = "", val userFilter: String = "", val icons: List<AppIconItem> = emptyList()
)
class AdminViewModel(private val repo: AdminRepository) : ViewModel() {
    private val mutable = MutableStateFlow(AdminState(language = repo.store.language))
    val state = mutable.asStateFlow()
    val server get() = repo.store.baseUrl
    private var readJob: Job? = null
    private var generation = 0
    init { if (repo.hasSession) refresh() }
    private fun updateState(block: (AdminState) -> AdminState) { mutable.value = block(mutable.value) }
    fun dismissError() = updateState { it.copy(error = null) }
    fun dismissNotice() = updateState { it.copy(notice = null) }
    private fun failed(e: Exception) {
        if (e is CancellationException) throw e
        if (e is HttpException && e.code() == 401) {
            repo.clearSession(); updateState { AdminState(language = it.language, error = apiError(e)) }
        } else updateState { it.copy(error = apiError(e)) }
    }
    fun login(server: String, phone: String, password: String) {
        if (state.value.saving || state.value.loading) return
        viewModelScope.launch {
            updateState { it.copy(saving = true, error = null) }
            try {
                val normalized = normalizePhone(phone)
                require(Regex("^\\+[1-9]\\d{7,14}$").matches(normalized)) { "Use a valid phone number, e.g. 03001234567 or +923001234567" }
                require(password.isNotEmpty()) { "Enter your password" }
                val user = repo.login(server, normalized, password)
                repo.store.language = user.language
                updateState { it.copy(user = user, language = user.language, page = Page.HOME) }
                loadPage()
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 401) {
                    repo.clearSession()
                    updateState { it.copy(error = "Incorrect phone number or password. Please sign in again.") }
                } else failed(e)
            }
            finally { updateState { it.copy(saving = false) } }
        }
    }
    fun refresh(append: Boolean = false) {
        if (state.value.saving) return
        readJob?.cancel()
        val turn = ++generation
        readJob = viewModelScope.launch {
            updateState { it.copy(loading = true, error = null) }
            try {
                if (state.value.user == null) {
                    val user = repo.api.profile()
                    require(user.role in listOf("ADMIN", "SUPERADMIN")) { "Administrator account required" }
                    repo.store.language = user.language
                    updateState { it.copy(user = user, language = user.language) }
                }
                loadPage(append)
            } catch (e: Exception) { failed(e) }
            finally { if (turn == generation) updateState { it.copy(loading = false) } }
        }
    }
    private suspend fun loadPage(append: Boolean = false) {
        val s = state.value
        val api = repo.api
        if (s.businesses.isEmpty() || s.page == Page.HOME) {
            val businesses = api.businesses().sortedBy { businessOrdinal(it.type) }
            updateState { it.copy(businesses = businesses) }
        }
        val id = s.business?.id.orEmpty()
        when (s.page) {
            Page.HOME -> { val result = api.dashboard(); updateState { it.copy(dashboard = result) } }
            Page.BUSINESS -> {
                val summary = api.summary(id)
                updateState { it.copy(summary = summary, business = summary.business) }
            }
            Page.STOCK -> { val result = api.stock(id); updateState { it.copy(stock = result) } }
            Page.LEDGER -> {
                val rows = api.days(id, if (append) s.days.size else 0)
                updateState { it.copy(days = (if (append) s.days else emptyList()) + rows, more = rows.size == 50) }
            }
            Page.DAY -> { val day = api.day(requireNotNull(s.day).id); val result = api.operations(day.id); updateState { it.copy(day = day, operations = result) } }
            Page.SUPPLIERS -> { val result = api.suppliers(id); updateState { it.copy(suppliers = result) } }
            Page.BILLS -> {
                val rows = api.bills(requireNotNull(s.supplier).id, if (append) s.operations.size else 0)
                updateState { it.copy(operations = (if (append) s.operations else emptyList()) + rows, more = rows.size == 50) }
            }
            Page.EXPENSES -> {
                val rows = api.expenses(id, s.start, s.end, if (append) s.operations.size else 0)
                updateState { it.copy(operations = (if (append) s.operations else emptyList()) + rows, more = rows.size == 50) }
            }
            Page.REPORTS -> { val result = api.reports(s.start, s.end, s.reportBusiness); updateState { it.copy(report = result) } }
            Page.BATCHES -> {
                val rows = api.batches(id, if (append) s.batches.size else 0)
                updateState { it.copy(batches = (if (append) s.batches else emptyList()) + rows, more = rows.size == 50) }
            }
            Page.BATCH -> {
                val result = api.batchHistory(requireNotNull(s.batch).id)
                updateState { it.copy(batch = result.batch, logs = result.logs) }
            }
            Page.SETTLEMENTS -> {
                val rows = api.settlements(id, if (append) s.settlements.size else 0)
                updateState { it.copy(settlements = (if (append) s.settlements else emptyList()) + rows, more = rows.size == 50) }
            }
            Page.USERS -> {
                val rows = api.users()
                updateState { it.copy(users = rows) }
            }
            Page.USER -> {
                val detail = api.userDetail(requireNotNull(s.userDetail).id)
                updateState { it.copy(userDetail = detail) }
            }
            Page.ADD_USER -> Unit
            Page.ICONS -> {
                val rows = api.icons()
                updateState { it.copy(icons = rows) }
            }
            Page.SETTINGS -> { val user = api.profile(); updateState { it.copy(user = user) } }
        }
    }
    fun go(page: Page, business: Business? = state.value.business) {
        if (state.value.saving) return
        readJob?.cancel()
        updateState { it.copy(page = page, business = business, draft = null, error = null, summary = null, stock = null,
            operations = emptyList(), logs = emptyList(), days = emptyList(), suppliers = emptyList(), batches = emptyList(), settlements = emptyList(), report = null, more = false,
            users = emptyList(), userDetail = null, userDraft = null) }
        refresh()
    }
    fun back() {
        if (state.value.saving) return
        if (state.value.draft != null) { readJob?.cancel(); ++generation; updateState { it.copy(draft = null, error = null) }; refresh(); return }
        go(when (state.value.page) {
            Page.DAY -> Page.LEDGER
            Page.BILLS -> Page.SUPPLIERS
            Page.BATCH -> Page.BATCHES
            Page.USER, Page.ADD_USER -> Page.USERS
            Page.STOCK, Page.SUPPLIERS, Page.LEDGER, Page.EXPENSES, Page.BATCHES, Page.SETTLEMENTS -> Page.BUSINESS
            else -> Page.HOME
        })
    }
    fun openDay(day: Day) { updateState { it.copy(day = day) }; go(Page.DAY) }
    fun openSupplier(supplier: Supplier) { updateState { it.copy(supplier = supplier) }; go(Page.BILLS) }
    fun openBatch(batch: Batch) { updateState { it.copy(batch = batch) }; go(Page.BATCH) }
    fun isSuperAdmin(): Boolean = state.value.user?.role == "SUPERADMIN"
    fun openUsers() { if (isSuperAdmin()) go(Page.USERS) }
    fun openUser(user: UserOut) {
        readJob?.cancel()
        updateState { it.copy(page = Page.USER, userDetail = user, userDraft = null, error = null, draft = null) }
        refresh()
    }
    fun openAddUser() {
        readJob?.cancel()
        updateState { it.copy(page = Page.ADD_USER, userDetail = null, userDraft = UserDraft(), error = null, draft = null) }
        refresh()
    }
    fun openIcons() { if (isSuperAdmin()) go(Page.ICONS) }
    fun setUserFilter(filter: String) { updateState { it.copy(userFilter = filter) } }
    fun setUserSearch(search: String) { updateState { it.copy(userSearch = search) } }
    fun setUserRole(role: String) { updateState { it.copy(userDraft = (it.userDraft ?: UserDraft()).copy(role = role)) } }
    fun setUserField(name: String, value: String) { updateState { it.copy(userDraft = (it.userDraft ?: UserDraft()).copy(values = (it.userDraft?.values ?: emptyMap()) + (name to value))) } }
    fun toggleUserBusiness(id: String) {
        updateState { current ->
            val draft = current.userDraft ?: UserDraft()
            val next = if (id in draft.businesses) draft.businesses - id else draft.businesses + id
            current.copy(userDraft = draft.copy(businesses = next))
        }
    }
    fun submitUser() = write {
        val s = state.value; val d = requireNotNull(s.userDraft)
        val name = d.values["name"]?.trim().orEmpty()
        val phone = normalizePhone(d.values["phone"].orEmpty())
        val password = d.values["password"].orEmpty()
        require(name.isNotBlank() && name.length <= 120) { "Name is required (maximum 120 characters)" }
        require(Regex("^\\+[1-9]\\d{7,14}$").matches(phone)) { "Use a valid phone number, e.g. 03001234567" }
        require(password.length >= 10) { "Password must contain at least 10 characters" }
        val notice: String
        if (d.role == "INVESTOR") {
            repo.api.register(mapOf("name" to name, "phone" to phone, "password" to password))
            notice = "Investor $name registered."
        } else {
            val created = repo.api.createManager(mapOf("name" to name, "phone" to phone, "password" to password))
            val id = created.get("id")?.asString.orEmpty()
            d.businesses.forEach { businessId -> repo.api.assignManager(businessId, id) }
            notice = "Manager $name added${if (d.businesses.isEmpty()) "" else " and assigned"}."
        }
        updateState { it.copy(userDraft = null, userDetail = null, page = Page.USERS, notice = notice, users = emptyList()) }
        loadPage()
    }
    fun changeUserRole(role: String) = write {
        val target = requireNotNull(state.value.userDetail)
        val updated = repo.api.updateRole(target.id, mapOf("role" to role))
        updateState { it.copy(userDetail = updated, notice = "Role changed to $role.") }
        loadPage()
    }
    fun verifyUserKyc() = write {
        val target = requireNotNull(state.value.userDetail)
        val updated = repo.api.verifyKyc(target.id)
        updateState { it.copy(userDetail = updated, notice = "KYC verified for ${updated.name}.") }
        loadPage()
    }
    fun setUserBusinesses(businessId: String, enabled: Boolean) = write {
        val target = requireNotNull(state.value.userDetail)
        if (enabled) repo.api.assignManager(businessId, target.id) else repo.api.unassignManager(businessId, target.id)
        val updated = repo.api.userDetail(target.id)
        updateState { it.copy(userDetail = updated, notice = if (enabled) "Business access granted." else "Business access removed.") }
        loadPage()
    }
    fun setScreenIcon(key: String, label: String, imageUrl: String) = write {
        repo.api.setIcon(key, AppIconUpdateReq(label = label, screen = "dashboard", image_url = imageUrl))
        val icons = repo.api.icons()
        updateState { it.copy(icons = icons, notice = "Screen icon updated.") }
        loadPage()
    }
    fun range(start: String, end: String, businessId: String?) {
        try {
            val first = LocalDate.parse(start); val last = LocalDate.parse(end)
            require(!last.isBefore(first) && java.time.temporal.ChronoUnit.DAYS.between(first, last) <= 366) { "Select a range of at most 366 days" }
            updateState { it.copy(start = start, end = end, reportBusiness = businessId) }; refresh()
        } catch (_: Exception) { updateState { it.copy(error = "Enter valid YYYY-MM-DD dates, in order, within 366 days") } }
    }
    fun openForm(kind: FormKind) {
        if (state.value.saving) return
        readJob?.cancel(); ++generation
        val user = state.value.user
        val values = mutableMapOf("date" to businessDate(), "channel" to "RETAIL", "deaths" to "0", "feed" to "0")
        if (kind in listOf(FormKind.BATCH_CREATE, FormKind.BATCH_LOG, FormKind.HARVEST)) values["amount"] = "0"
        if (kind == FormKind.PROFILE) { values["name"] = user?.name.orEmpty(); values["language"] = user?.language ?: "en" }
        updateState { it.copy(draft = Draft(kind, values), loading = false, error = null) }
        if (kind == FormKind.PURCHASE) {
            val id = state.value.business?.id ?: return
            val turn = generation
            readJob = viewModelScope.launch {
                try { val rows = repo.api.suppliers(id); if (turn == generation) updateState { it.copy(suppliers = rows) } }
                catch (e: Exception) { if (e is CancellationException) throw e; if (turn == generation) failed(e) }
            }
        }
    }
    fun field(name: String, value: String) {
        if (!state.value.saving) updateState { current ->
            val draft = current.draft
            current.copy(draft = draft?.copy(values = draft.values + (name to value)))
        }
    }
    private fun write(block: suspend () -> Unit) {
        if (state.value.saving) return
        readJob?.cancel(); ++generation
        viewModelScope.launch {
            updateState { it.copy(saving = true, loading = false, error = null, notice = null) }
            try { block() } catch (e: Exception) { failed(e) }
            finally { updateState { it.copy(saving = false) } }
        }
    }
    fun submit() = write {
        val s = state.value; val draft = requireNotNull(s.draft)
        val body = formBody(draft, s.business, s.batch)
        val user = requireNotNull(s.user)
        val batchId = s.batch?.id.orEmpty()
        var message = "Record saved successfully."
        when (draft.kind) {
            FormKind.SALE, FormKind.PURCHASE, FormKind.EXPENSE, FormKind.BYPRODUCT -> repo.idempotent(user.id, "daily", body.toString()) { repo.api.daily(it, body) }
            FormKind.BATCH_CREATE -> repo.idempotent(user.id, "create-batch", body.toString()) { repo.api.createBatch(it, body) }
            FormKind.BATCH_LOG -> repo.idempotent(user.id, "batch-log:$batchId", body.toString()) { repo.api.logBatch(batchId, it, body) }
            FormKind.HARVEST -> {
                val result = repo.idempotent(user.id, "harvest:$batchId", body.toString()) { repo.api.harvest(batchId, it, body) }
                updateState { it.copy(batch = result.batch) }
                message = "Batch harvested. Investor distribution: ${rupees(result.settlement.distributed)}."
            }
            FormKind.SUPPLIER -> { repo.api.addSupplier(body) }
            FormKind.PROFILE -> {
                val profile = repo.api.saveProfile(mapOf("name" to body["name"].asString, "language" to body["language"].asString))
                repo.store.language = profile.language
                updateState { it.copy(user = profile, language = profile.language) }
            }
            FormKind.PASSWORD -> {
                repo.api.password(mapOf("old_password" to body["old_password"].asString, "new_password" to body["new_password"].asString))
                repo.clearSession(); updateState { AdminState(language = it.language, notice = "Password changed. Sign in again.") }; return@write
            }
        }
        updateState { it.copy(draft = null, notice = message) }
        // A refresh failure must not be mistaken for a failed write: success notice stays visible.
        loadPage()
    }
    fun closeDay(day: Day) = write {
        val result = repo.idempotent(requireNotNull(state.value.user).id, "close-day", day.id) { repo.api.close(it, mapOf("day_id" to day.id)) }
        updateState { it.copy(day = result.day, notice = "Day closed. Investor distribution: ${rupees(result.settlement.distributed)}.") }
        loadPage()
    }
    fun startBatch() = write {
        val batch = requireNotNull(state.value.batch)
        val result = repo.idempotent(requireNotNull(state.value.user).id, "start-batch", batch.id) { repo.api.startBatch(batch.id, it) }
        updateState { it.copy(batch = result, notice = "Batch started. New investments are now locked.") }; loadPage()
    }
    fun logout() = write {
        try {
            repo.api.logout()
            repo.clearSession(); updateState { AdminState(language = it.language, notice = "Signed out on all devices.") }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            repo.clearSession(); updateState { AdminState(language = it.language, notice = "Signed out on this device. Server revocation could not be confirmed; existing tokens may remain valid until expiry.") }
        }
    }
}
