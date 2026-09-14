package com.ahsantraders.admin

import com.ahsantraders.admin.data.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiContractTest {
    @Test fun operationUsesExactMoneyAndIdempotencyHeader() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("{}"))
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(AdminApi::class.java)
            api.daily("stable-key-123", JsonObject().apply { addProperty("amount", 125050L); addProperty("kind", "SALE") })
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/admin/ledger/daily", request.path)
            assertEquals("stable-key-123", request.getHeader("Idempotency-Key"))
            assertEquals(125050L, JsonParser.parseString(request.body.readUtf8()).asJsonObject["amount"].asLong)
        } finally { server.shutdown() }
    }
    @Test fun numericAndStringQuantitiesDeserialize() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(AdminApi::class.java)
            server.enqueue(MockResponse().setBody("""{"business_id":"id","quantity":2.5,"unit":"kg","inventory_cost":25001,"live_birds":0}"""))
            assertEquals("2.5", api.stock("id").quantity)
            server.enqueue(MockResponse().setBody("""{"business_id":"id","quantity":"2.500","unit":"kg","inventory_cost":25001,"live_birds":0}"""))
            assertEquals(25001L, api.stock("id").inventory_cost)
        } finally { server.shutdown() }
    }
}
