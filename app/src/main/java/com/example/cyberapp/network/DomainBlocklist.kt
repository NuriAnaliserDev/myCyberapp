package com.example.cyberapp.network

import android.util.Log

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

    // Dynamic list that can be added to at runtime
    private val dynamicBlockedItems = mutableSetOf<String>()

    /**
     * Checks if a domain or IP is in the blocklist.
     * @param item The domain or IP address to check.
     * @return True if the item is blocked, false otherwise.
     */
    fun isBlocked(item: String): Boolean {
        // Check dynamic list first for exact matches (IPs or domains)
        if (dynamicBlockedItems.contains(item)) return true

        // Check preset list for exact domain matches
        if (PRESET_BLOCKED_DOMAINS.contains(item)) return true

        // Check for subdomain matches in the preset list (e.g., login.fake-telegram.com)
        return PRESET_BLOCKED_DOMAINS.any { item.endsWith(".$it") }
    }

    /**
     * Adds a new domain or IP to the dynamic blocklist.
     * This change is temporary and will be reset when the app process is killed.
     * For persistence, this should be saved to SharedPreferences or a database.
     * @param item The domain or IP to block.
     */
    fun add(item: String) {
        if (dynamicBlockedItems.add(item)) {
            Log.w("DomainBlocklist", "Dynamically blocked: $item")
        }
    }
}
