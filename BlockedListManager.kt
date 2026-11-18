package com.example.hiyaclone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object BlockedListManager {
    private const val PREF_NAME = "hiya_clone_prefs"
    private const val KEY_BLOCKED_NUMBERS = "blocked_numbers"
    private const val KEY_BLOCK_UNKNOWN = "block_unknown_contacts"

    fun normalizePhone(phone: String): String {
        return phone.trim()
            .replace("^\\+84".toRegex(), "0")
            .replace("[^0-9]".toRegex(), "")
    }

    fun setBlockUnknown(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BLOCK_UNKNOWN, enabled).apply()
    }

    fun isBlockUnknownEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BLOCK_UNKNOWN, false)
    }

    fun isNumberInContacts(context: Context, phoneNumber: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }

        if (phoneNumber.isBlank()) return false

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup._ID)

        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: Exception) {
            true
        }
    }

    fun getBlockedNumbers(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_BLOCKED_NUMBERS, emptySet()) ?: emptySet()
        return set.toList().sorted()
    }

    fun addNumber(context: Context, rawPhone: String): Boolean {
        val cleanPhone = normalizePhone(rawPhone)
        if (cleanPhone.length < 3) return false

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val currentSet = HashSet(prefs.getStringSet(KEY_BLOCKED_NUMBERS, emptySet()) ?: emptySet())
        val added = currentSet.add(cleanPhone)
        if (added) {
            prefs.edit().putStringSet(KEY_BLOCKED_NUMBERS, currentSet).apply()
        }
        return added
    }

    fun removeNumber(context: Context, phone: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val currentSet = HashSet(prefs.getStringSet(KEY_BLOCKED_NUMBERS, emptySet()) ?: emptySet())
        if (currentSet.remove(phone)) {
            prefs.edit().putStringSet(KEY_BLOCKED_NUMBERS, currentSet).apply()
        }
    }

    fun isBlocked(context: Context, incomingPhone: String): Boolean {
        val cleanIncoming = normalizePhone(incomingPhone)
        if (cleanIncoming.isEmpty()) return false

        val blockedList = getBlockedNumbers(context)
        return blockedList.any { blocked ->
            cleanIncoming == blocked || cleanIncoming.startsWith(blocked)
        }
    }
}
