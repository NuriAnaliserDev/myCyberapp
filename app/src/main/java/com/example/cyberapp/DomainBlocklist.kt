package com.example.cyberapp

object DomainBlocklist {
    
    // In a real app, this should be updated from a remote server
    private val BLOCKED_DOMAINS = setOf(
        "example-phishing.com",
        "fake-telegram-login.com",
        "free-crypto-giveaway.net",
        "secure-bank-login-update.com",
        "click-here-for-prize.xyz",
        "telegram-premium-free.com",
        "uzcard-verify.com",
        "humo-bonus.com"
    )

    fun isBlocked(domain: String): Boolean {
        // Exact match
        if (BLOCKED_DOMAINS.contains(domain)) return true
        
        // Subdomain match (e.g., login.fake-telegram.com)
        return BLOCKED_DOMAINS.any { domain.endsWith(".$it") }
    }
}
