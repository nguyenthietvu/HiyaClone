package com.example.hiyaclone

import android.telecom.Call
import android.telecom.CallScreeningService
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HiyaCallScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val SERVER_URL = "http://192.168.1.10:3000/api/lookup/"

    private val localMockDb = mapOf(
        "0901234567" to Triple("Telesales Bất Động Sản", "SPAM", 95),
        "02477778888" to Triple("Nghi ngờ lừa đảo tài chính", "FRAUD", 99),
        "0988888888" to Triple("Shipper Giao Hàng", "SAFE", 10)
    )

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return

        val rawPhone = callDetails.handle?.schemeSpecificPart ?: ""
        val normalizedPhone = BlockedListManager.normalizePhone(rawPhone)

        if (BlockedListManager.isBlocked(applicationContext, normalizedPhone)) {
            rejectCallImmediately(callDetails)
            return
        }

        if (BlockedListManager.isBlockUnknownEnabled(applicationContext)) {
            val inContacts = BlockedListManager.isNumberInContacts(applicationContext, rawPhone)
            if (!inContacts) {
                rejectCallImmediately(callDetails)
                return
            }
        }

        serviceScope.launch {
            val responseBuilder = CallResponse.Builder()
            val serverInfo = lookupPhoneFromServer(normalizedPhone)

            val name: String
            val category: String
            val spamScore: Int

            if (serverInfo != null) {
                name = serverInfo.optString("name", "Không xác định")
                category = serverInfo.optString("category", "UNKNOWN")
                spamScore = serverInfo.optInt("spamScore", 0)
            } else if (localMockDb.containsKey(normalizedPhone)) {
                val mock = localMockDb[normalizedPhone]!!
                name = mock.first
                category = mock.second
                spamScore = mock.third
            } else {
                name = "Số lạ ngoài danh bạ"
                category = "UNKNOWN"
                spamScore = 0
            }

            if (spamScore >= 90) {
                responseBuilder
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)
            } else {
                responseBuilder.setDisallowCall(false)
                if (category != "UNKNOWN") {
                    CallerIdOverlay.show(applicationContext, normalizedPhone, name, category, spamScore)
                }
            }

            respondToCall(callDetails, responseBuilder.build())
        }
    }

    private fun rejectCallImmediately(callDetails: Call.Details) {
        val blockResponse = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipNotification(true)
            .build()
        respondToCall(callDetails, blockResponse)
    }

    private fun lookupPhoneFromServer(phone: String): JSONObject? {
        return try {
            val url = URL("$SERVER_URL$phone")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 1500
            conn.readTimeout = 1500

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(responseText)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
