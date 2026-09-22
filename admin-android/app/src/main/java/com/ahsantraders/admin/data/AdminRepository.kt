package com.ahsantraders.admin.data

import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

fun normalizeServer(input: String, allowHttp: Boolean): String {
    val url = input.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("Enter a valid server URL")
    require(url.isHttps || allowHttp) { "Release builds require HTTPS" }
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null && url.encodedPath == "/") {
        "Use only the server origin, for example http://10.0.2.2:8000"
    }
    return url.toString()
}
fun retryHash(userId: String, action: String, payload: String): String = MessageDigest.getInstance("SHA-256")
    .digest("$userId|$action|$payload".toByteArray()).joinToString("") { "%02x".format(it) }

fun apiError(error: Throwable): String = when (error) {
    is HttpException -> {
        val detail = runCatching { JsonParser.parseString(error.response()?.errorBody()?.string()).asJsonObject.get("detail") }.getOrNull()
        when {
            error.code() == 401 -> "Your session has expired. Please sign in again."
            detail?.isJsonPrimitive == true -> detail.asString
            detail?.isJsonArray == true -> detail.asJsonArray.joinToString("\n") { item ->
                val obj = item.asJsonObject
                val field = obj.getAsJsonArray("loc")?.drop(1)?.joinToString(".") { it.asString } ?: "Input"
                "$field: ${obj.get("msg")?.asString ?: "Invalid value"}"
            }
            else -> "Server returned ${error.code()}. Please try again."
        }
    }
    is IOException -> "Cannot reach the server. Check its address, your connection, and that Python is running. A write may have completed; retry unchanged data with the same key."
    is IllegalArgumentException -> error.message ?: "Check the entered values."
    else -> "Unable to complete the request. Please try again."
}

class AdminRepository(val store: SessionStore, private val allowHttp: Boolean) {
    private var bearer = store.token()
    private fun client(url: String): AdminApi = Retrofit.Builder()
        .baseUrl(normalizeServer(url, allowHttp))
        .client(OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                if (!chain.request().url.encodedPath.endsWith("/auth/login")) bearer?.let { request.header("Authorization", "Bearer $it") }
                chain.proceed(request.build())
            }.build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(AdminApi::class.java)
    // Build lazily so an old debug HTTP URL cannot crash a release build before login.
    private var cached: AdminApi? = null
    val api: AdminApi get() = cached ?: client(store.baseUrl).also { cached = it }
    val hasSession get() = bearer != null
    suspend fun login(server: String, phone: String, password: String): Profile {
        clearSession()
        val url = normalizeServer(server, allowHttp)
        val candidate = client(url)
        val response = candidate.login(LoginRequest(phone.trim(), password))
        require(response.role == "ADMIN" || response.role == "SUPERADMIN") { "This app is for administrators, not investors." }
        bearer = response.access_token
        return try {
            val profile = candidate.profile()
            store.saveToken(response.access_token); store.baseUrl = url; cached = candidate
            profile
        } catch (e: Exception) { clearSession(); throw e }
    }
    fun clearSession() { bearer = null; cached = null; store.clearToken() }
    suspend fun <T> idempotent(userId: String, scope: String, body: String, action: suspend (String) -> T): T {
        val hash = retryHash(userId + "@" + store.baseUrl, scope, body)
        val result = action(store.requestKey(hash))
        store.completeRequest(hash)
        return result
    }
}
