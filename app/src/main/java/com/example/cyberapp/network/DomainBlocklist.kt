package com.example.cyberapp.network

import android.content.Context
import android.util.Log
import com.example.cyberapp.EncryptedPrefsManager
import java.util.regex.Pattern

object DomainBlocklist {

    // Initial hardcoded list, can be updated from a remote server
    private val PRESET_BLOCKED_DOMAINS = setOf(
        "example-phishing.com",
        "fake-telegram-login.com",
        "free-crypto-giveaway.net",
        "secure-bank-login-update.com",
        "click-here-for-prize.xyz",
        "telegram-premium-free.com",
        "uzcard-verify.com",
        "humo-bonus.com"
    )

    // Dynamic list that can be added to at runtime (in-memory cache)
    private val dynamicBlockedItems = mutableSetOf<String>()
    
    // Context and prefs for persistence (set via init method)
    private var prefs: EncryptedPrefsManager? = null
    private const val BLOCKED_ITEMS_KEY = "blocked_domains_and_ips"

    // IP address pattern (IPv4)
    private val IP_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    /**
     * Initialize with context for persistent storage
     */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = EncryptedPrefsManager(context)
            loadPersistedBlocklist()
        }
    }

    /**
     * Load persisted blocklist from EncryptedPrefs
     */
    private fun loadPersistedBlocklist() {
        prefs?.let { prefs ->
            val persisted = prefs.getStringSet(BLOCKED_ITEMS_KEY, null)
            if (persisted != null) {
                dynamicBlockedItems.clear()
                dynamicBlockedItems.addAll(persisted)
                Log.d("DomainBlocklist", "Loaded ${persisted.size} blocked items from storage")
            }
        }
    }

    /**
     * Checks if a domain or IP is in the blocklist.
     * @param item The domain or IP address to check.
     * @return True if the item is blocked, false otherwise.
     */
    fun isBlocked(item: String): Boolean {
        val normalizedItem = item.lowercase().trim()
        
        // Check dynamic list first for exact matches (IPs or domains)
        if (dynamicBlockedItems.contains(normalizedItem)) return true

        // Check preset list for exact domain matches
        if (PRESET_BLOCKED_DOMAINS.contains(normalizedItem)) return true

        // Check for subdomain matches in the preset list (e.g., login.fake-telegram.com)
        return PRESET_BLOCKED_DOMAINS.any { normalizedItem.endsWith(".$it") }
    }

    /**
     * Adds a new domain or IP to the blocklist and persists it.
     * @param item The domain or IP to block.
     * @return True if the item was added, false if it was already blocked.
     */
    fun add(item: String): Boolean {
        val normalizedItem = item.lowercase().trim()
        
        // Validate IP or domain format
        if (!isValidIpOrDomain(normalizedItem)) {
            Log.w("DomainBlocklist", "Invalid IP/domain format: $item")
            return false
        }
        
        if (dynamicBlockedItems.add(normalizedItem)) {
            // Persist to EncryptedPrefs
            prefs?.let { prefs ->
                val currentSet = prefs.getStringSet(BLOCKED_ITEMS_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                currentSet.add(normalizedItem)
                prefs.putStringSet(BLOCKED_ITEMS_KEY, currentSet)
            }
            Log.w("DomainBlocklist", "Blocked and persisted: $normalizedItem")
            return true
        }
        return false
    }

    /**
     * Removes a domain or IP from the blocklist.
     * @param item The domain or IP to unblock.
     * @return True if the item was removed, false if it wasn't in the list.
     */
    fun remove(item: String): Boolean {
        val normalizedItem = item.lowercase().trim()
        
        if (dynamicBlockedItems.remove(normalizedItem)) {
            // Update persisted storage
            prefs?.let { prefs ->
                val currentSet = prefs.getStringSet(BLOCKED_ITEMS_KEY, null)?.toMutableSet() ?: mutableSetOf()
                currentSet.remove(normalizedItem)
                prefs.putStringSet(BLOCKED_ITEMS_KEY, currentSet)
            }
            Log.d("DomainBlocklist", "Unblocked: $normalizedItem")
            return true
        }
        return false
    }

    /**
     * Gets all blocked items (both preset and dynamic)
     */
    fun getAllBlockedItems(): Set<String> {
        return PRESET_BLOCKED_DOMAINS + dynamicBlockedItems
    }

    /**
     * Gets only user-added blocked items (excluding preset)
     */
    fun getUserBlockedItems(): Set<String> {
        return dynamicBlockedItems.toSet()
    }

    /**
     * Validates if the string is a valid IP address or domain
     */
    private fun isValidIpOrDomain(item: String): Boolean {
        // Check if it's a valid IP address
        if (IP_PATTERN.matcher(item).matches()) {
            return true
        }
        
        // Check if it's a valid domain (basic validation)
        // Domain should contain at least one dot and valid characters
        if (item.contains(".") && item.length >= 3) {
            val domainPattern = Pattern.compile("^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,}$")
            return domainPattern.matcher(item).matches()
        }
        
        return false
    }
}
