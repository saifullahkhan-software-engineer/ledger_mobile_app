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
    @Test fun profitAndLossLinesAndLiveWeightDeserialize() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(AdminApi::class.java)
            server.enqueue(MockResponse().setBody("""{"id":"d","business_id":"b","date":"2026-01-02","status":"OPEN","revenue":90000,"cost":55000,"expenses":5000,"net_profit":0,"cogs":40000,"wastage_cost":15000}"""))
            val day = api.day("d")
            assertEquals(15000L, day.wastage_cost)
            assertEquals(30000L, day.profit)
            server.enqueue(MockResponse().setBody("""[{"id":"o","day_id":"d","kind":"PURCHASE","quantity":65.0,"live_weight":100.0,"amount":1500000,"cost":0,"channel":null,"note":"","supplier_id":null,"created_at":"x"}]"""))
            assertEquals("100.0", api.operations("d")[0].live_weight)
            server.enqueue(MockResponse().setBody("""{"start":"a","end":"b","businesses":[{"business_id":"c","name":"Chicken","type":"CHICKEN","revenue":90000,"cogs":40000,"wastage_cost":15000,"expenses":5000,"cost_and_expenses":60000,"net_profit":30000,"open_days":1}],"total_sales":90000,"total_cogs":40000,"total_wastage_cost":15000,"total_expenses":5000,"total_cost_and_expenses":60000,"total_profit":30000}"""))
            val report = api.reports("a", "b")
            assertEquals(15000L, report.total_wastage_cost)
            assertEquals(40000L, report.businesses[0].cogs)
            // A server that predates the P&L lines still parses, with the lines at zero.
            server.enqueue(MockResponse().setBody("""{"start":"a","end":"b","businesses":[],"total_sales":0,"total_cost_and_expenses":0,"total_profit":0}"""))
            assertEquals(0L, api.reports("a", "b").total_cogs)
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
