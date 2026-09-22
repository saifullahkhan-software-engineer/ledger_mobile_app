package com.ahsantraders.admin.data

import com.google.gson.JsonObject
import okhttp3.MultipartBody
import retrofit2.http.*

interface AdminApi {
    @POST("api/v1/auth/login") suspend fun login(@Body body: LoginRequest): LoginResponse
    @GET("api/v1/me") suspend fun profile(): Profile
    @PATCH("api/v1/me") suspend fun saveProfile(@Body body: Map<String, String>): Profile
    @POST("api/v1/auth/change-password") suspend fun password(@Body body: Map<String, String>): JsonObject
    @POST("api/v1/auth/logout") suspend fun logout(): JsonObject
    @GET("api/v1/admin/businesses") suspend fun businesses(): List<Business>
    @GET("api/v1/admin/dashboard") suspend fun dashboard(): Report
    @GET("api/v1/admin/businesses/{id}/summary") suspend fun summary(@Path("id") id: String): Summary
    @GET("api/v1/admin/stock") suspend fun stock(@Query("business_id") id: String): Stock
    @GET("api/v1/admin/ledger/daily") suspend fun days(@Query("business_id") id: String, @Query("offset") offset: Int, @Query("limit") limit: Int = 50): List<Day>
    @GET("api/v1/admin/ledger/{id}") suspend fun day(@Path("id") id: String): Day
    @GET("api/v1/admin/ledger/{id}/operations") suspend fun operations(@Path("id") id: String): List<Operation>
    @POST("api/v1/admin/ledger/daily") suspend fun daily(@Header("Idempotency-Key") key: String, @Body body: JsonObject): JsonObject
    @POST("api/v1/admin/ledger/close") suspend fun close(@Header("Idempotency-Key") key: String, @Body body: Map<String, String>): DayClosed
    @GET("api/v1/admin/suppliers") suspend fun suppliers(@Query("business_id") id: String): List<Supplier>
    @POST("api/v1/admin/suppliers") suspend fun addSupplier(@Body body: JsonObject): Supplier
    @GET("api/v1/admin/suppliers/{id}/bills") suspend fun bills(@Path("id") id: String, @Query("offset") offset: Int, @Query("limit") limit: Int = 50): List<Operation>
    @GET("api/v1/admin/expenses") suspend fun expenses(@Query("business_id") id: String, @Query("start") start: String, @Query("end") end: String, @Query("offset") offset: Int, @Query("limit") limit: Int = 50): List<Operation>
    @POST("api/v1/admin/batch/create") suspend fun createBatch(@Header("Idempotency-Key") key: String, @Body body: JsonObject): Batch
    @POST("api/v1/admin/batch/{id}/start") suspend fun startBatch(@Path("id") id: String, @Header("Idempotency-Key") key: String): Batch
    @PUT("api/v1/admin/batch/{id}/update") suspend fun logBatch(@Path("id") id: String, @Header("Idempotency-Key") key: String, @Body body: JsonObject): JsonObject
    @POST("api/v1/admin/batch/{id}/harvest") suspend fun harvest(@Path("id") id: String, @Header("Idempotency-Key") key: String, @Body body: JsonObject): Harvested
    @GET("api/v1/admin/batches") suspend fun batches(@Query("business_id") id: String, @Query("offset") offset: Int, @Query("limit") limit: Int = 50): List<Batch>
    @GET("api/v1/admin/batch/{id}/logs") suspend fun batchHistory(@Path("id") id: String): BatchHistory
    @GET("api/v1/admin/reports") suspend fun reports(@Query("start") start: String, @Query("end") end: String, @Query("business_id") id: String? = null): Report
    @GET("api/v1/admin/settlements") suspend fun settlements(@Query("business_id") id: String, @Query("offset") offset: Int, @Query("limit") limit: Int = 50): List<Settlement>
    @GET("api/v1/admin/users") suspend fun users(@Query("role") role: String? = null, @Query("search") search: String? = null, @Query("limit") limit: Int = 100, @Query("offset") offset: Int = 0): List<UserOut>
    @GET("api/v1/admin/users/{user_id}") suspend fun userDetail(@Path("user_id") id: String): UserOut
    @POST("api/v1/admin/managers") suspend fun createManager(@Body body: Map<String, String>): JsonObject
    @POST("api/v1/auth/register") suspend fun register(@Body body: Map<String, String>): JsonObject
    @PUT("api/v1/admin/businesses/{business_id}/managers/{manager_id}") suspend fun assignManager(@Path("business_id") businessId: String, @Path("manager_id") managerId: String): JsonObject
    @DELETE("api/v1/admin/businesses/{business_id}/managers/{manager_id}") suspend fun unassignManager(@Path("business_id") businessId: String, @Path("manager_id") managerId: String): JsonObject
    @PUT("api/v1/admin/users/{user_id}/role") suspend fun updateRole(@Path("user_id") userId: String, @Body body: Map<String, String>): UserOut
    @POST("api/v1/admin/users/{user_id}/verify-kyc") suspend fun verifyKyc(@Path("user_id") userId: String): UserOut
    @GET("api/v1/admin/icons") suspend fun icons(): List<AppIconItem>
    @GET("api/v1/mobile/icons") suspend fun mobileIcons(): JsonObject
    @PUT("api/v1/admin/icons/{key}") suspend fun setIcon(@Path("key") key: String, @Body body: AppIconUpdateReq): AppIconItem
    @PUT("api/v1/admin/businesses/{business_id}/icon") suspend fun setBusinessIcon(@Path("business_id") id: String, @Body body: BusinessIconUpdateReq): JsonObject
    @Multipart
    @POST("api/v1/admin/icons/upload") suspend fun uploadIcon(@Part file: MultipartBody.Part): IconUploadResult
    @POST("api/v1/admin/icons/upload-base64") suspend fun uploadIconBase64(@Body body: Base64IconUploadReq): IconUploadResult
}
