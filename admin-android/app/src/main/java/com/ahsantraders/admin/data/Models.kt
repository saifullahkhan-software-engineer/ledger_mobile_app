package com.ahsantraders.admin.data

// API property names intentionally match the backend's OpenAPI contract.
data class LoginRequest(val phone: String, val password: String)
data class LoginResponse(val access_token: String, val role: String)
data class Profile(val id: String, val name: String, val phone: String, val role: String, val language: String = "en")
data class Business(val id: String, val name: String, val type: String, val total_shares: Long, val share_price: Long, val stock: String, val stock_cost: Long, val icon_url: String? = null)
data class UserOut(val id: String, val phone: String, val name: String, val role: String, val language: String = "en", val kyc_status: String = "UNVERIFIED", val assigned_businesses: List<String> = emptyList())
data class AppIconItem(val id: String, val key: String, val label: String, val screen: String, val image_url: String, val fallback_icon: String? = null)
data class AppIconUpdateReq(val label: String? = null, val screen: String = "dashboard", val image_url: String, val fallback_icon: String? = null)
data class BusinessIconUpdateReq(val icon_url: String)
data class IconUploadResult(val filename: String, val image_url: String)
data class Base64IconUploadReq(val filename: String, val data: String)
data class BusinessReport(val business_id: String, val name: String, val type: String, val revenue: Long, val cost_and_expenses: Long, val net_profit: Long, val open_days: Int)
data class Previous(val total_sales: Long, val total_profit: Long)
data class Report(val start: String, val end: String, val businesses: List<BusinessReport>, val total_sales: Long, val total_cost_and_expenses: Long, val total_profit: Long, val yesterday: Previous? = null)
data class Day(val id: String, val business_id: String, val date: String, val status: String, val revenue: Long, val cost: Long, val expenses: Long, val net_profit: Long) {
    val profit: Long get() = revenue - cost - expenses
}
data class Batch(val id: String, val business_id: String, val name: String, val chicks: Int, val deaths: Int, val total_shares: Long, val share_price: Long, val expenses: Long, val revenue: Long, val net_profit: Long, val yield_kg: String, val status: String, val started_on: String?, val closed_on: String?)
data class Summary(val business: Business, val date: String, val day: Day?, val purchased_quantity: String, val sold_quantity: String, val byproduct_quantity: String, val byproduct_revenue: Long, val retail_sold: String, val commercial_sold: String, val live_birds: Int, val feed_kg: String, val mortality: Int, val batches: List<Batch>)
data class Stock(val business_id: String, val quantity: String, val unit: String, val inventory_cost: Long, val live_birds: Int)
data class Supplier(val id: String, val business_id: String, val name: String, val phone: String?)
data class Operation(val id: String, val day_id: String, val kind: String, val quantity: String, val amount: Long, val cost: Long, val channel: String?, val note: String, val supplier_id: String?, val created_at: String)
data class BatchLog(val id: String, val date: String, val feed_kg: String, val deaths: Int, val expense: Long, val note: String)
data class BatchHistory(val batch: Batch, val logs: List<BatchLog>)
data class Settlement(val id: String, val source: String, val net_profit: Long, val distributed: Long, val retained: Long, val created_at: String)
data class DayClosed(val day: Day, val settlement: Settlement)
data class Harvested(val batch: Batch, val settlement: Settlement)
