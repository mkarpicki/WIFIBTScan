package com.example.wifibtscan

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean

object FilterManager {
    private val TAG = "FilterManager"
    private val client = OkHttpClient()

    // normalized set of MAC strings (upper-case, trimmed)
    private val filteredSet: MutableSet<String> = mutableSetOf()

    // whether init() already completed successfully
    private val initialized = AtomicBoolean(false)

    // initialize once: fetch list from remote GET API
    // call from a coroutine (will do network IO)
    suspend fun initOnce() {
        if (initialized.get()) {
            Log.d(TAG, "FilterManager already initialized")
            return
        }

        val url = BuildConfig.FILTER_API_URL
        val apiKey = BuildConfig.FILTER_API_KEY

        if (url.isNullOrBlank()) {
            Log.w(TAG, "FILTER API URL not configured; skipping filter fetch")
            initialized.set(true) // consider empty filter set as initialized
            return
        }

        try {
            val req = Request.Builder()
                .url(url)
                .header("x-api-key", apiKey)
                .get()
                .build()

            val bodyText = withContext(Dispatchers.IO) {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw Exception("Filter GET failed with code ${resp.code}")
                    }
                    resp.body?.string() ?: ""
                }
            }

            if (bodyText.isBlank()) {
                Log.w(TAG, "Filter API returned empty body")
                initialized.set(true)
                return
            }

            // Expecting JSON array of strings: ["AA:BB:CC:..", ...]
            val arr = JSONArray(bodyText)
            synchronized(filteredSet) {
                filteredSet.clear()
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i, null)
                    if (!v.isNullOrBlank()) {
                        filteredSet.add(normalizeMac(v))
                    }
                }
            }
            initialized.set(true)
            Log.d(TAG, "Filter list loaded: ${filteredSet.size} entries")
        } catch (t: Throwable) {
            // If fetch fails, we still mark initialized to avoid blocking forever.
            // You can change this to retry logic if desired.
            Log.e(TAG, "Failed to fetch filter list: ${t.message}", t)
            synchronized(filteredSet) { filteredSet.clear() }
            initialized.set(true)
        }
    }

    fun isInitialized(): Boolean = initialized.get()

    fun isFiltered(mac: String?): Boolean {
        if (mac.isNullOrBlank()) return false
        val n = normalizeMac(mac)
        synchronized(filteredSet) {
            return filteredSet.contains(n)
        }
    }

    private fun normalizeMac(raw: String): String {
        // Standardize: uppercase, remove surrounding whitespace
        return raw.trim().uppercase()
    }

    // Optional: expose current list copy for debugging
    fun getFilteredCopy(): Set<String> = synchronized(filteredSet) { filteredSet.toSet() }
}
