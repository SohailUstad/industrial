package com.homerun.marketplace.customer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

data class Product(
    val id: String, val sku: String, val name: String, val category: String,
    val description: String, val unit: String, val priceMinor: Long, val imageUrl: String?
)

data class CheckoutDetails(
    val name: String = "", val phone: String = "", val address: String = "",
    val city: String = "", val postalCode: String = ""
)

data class OrderReceipt(val id: String, val status: String, val totalMinor: Long)

fun money(minor: Long): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(minor / 100.0)

object MarketplaceApi {
    suspend fun products(): List<Product> = withContext(Dispatchers.IO) {
        val connection = URL(BuildConfig.CATALOG_API_URL.trimEnd('/') + "/api/products").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            if (connection.responseCode !in 200..299) error("Catalog is unavailable (${connection.responseCode})")
            val data = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            List(data.length()) { index ->
                val item = data.getJSONObject(index)
                Product(item.getString("id"), item.getString("sku"), item.getString("name"),
                    item.getString("category"), item.getString("description"), item.getString("unit"),
                    item.getLong("priceMinor"), if (item.isNull("imageUrl")) null else item.optString("imageUrl").takeIf { it.isNotBlank() })
            }
        } finally { connection.disconnect() }
    }

    suspend fun placeOrder(details: CheckoutDetails, items: List<Pair<Product, Int>>, key: String): OrderReceipt = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("customerName", details.name.trim())
            put("phone", details.phone.trim())
            put("addressLine", details.address.trim())
            put("city", details.city.trim())
            put("postalCode", details.postalCode.trim())
            put("expectedTotalMinor", items.sumOf { (product, quantity) -> product.priceMinor * quantity })
            put("items", JSONArray().apply {
                items.forEach { (product, quantity) -> put(JSONObject().put("productId", product.id).put("quantity", quantity)) }
            })
        }
        val connection = URL(BuildConfig.ORDER_API_URL.trimEnd('/') + "/api/orders").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Idempotency-Key", key)
            connection.doOutput = true
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                val detail = runCatching { JSONObject(response).optString("detail") }.getOrNull().orEmpty()
                error(detail.ifBlank { "Could not place order (${connection.responseCode})" })
            }
            val receipt = JSONObject(response)
            OrderReceipt(receipt.getString("id"), receipt.getString("status"), receipt.getLong("totalMinor"))
        } finally { connection.disconnect() }
    }
}
