import re
import os
import requests
from urllib.parse import urlparse
from app.database import check_blacklist, log_request

def is_punycode(domain: str) -> bool:
    """Checks if the domain contains Punycode (xn--)."""
    return "xn--" in domain

def is_homograph(domain: str) -> bool:
    """Checks for mixed scripts (Latin + Cyrillic) which is common in homograph attacks."""
    has_latin = bool(re.search(r'[a-zA-Z]', domain))
    has_cyrillic = bool(re.search(r'[\u0400-\u04FF]', domain))
    return has_latin and has_cyrillic

def analyze_url(url: str) -> dict:
    """
    Analyzes a URL for potential phishing indicators.
    Returns a dictionary with score (0-100) and reasons.
    0 = Safe, 100 = Dangerous.
    """
    score = 0
    reasons = []
    
    try:
        parsed = urlparse(url)
        domain = parsed.netloc
        
        if not domain:
            return {"score": 0, "verdict": "invalid", "reasons": ["Invalid URL"]}

        # 0. Check Database Blacklist
        blacklist_reason = check_blacklist(domain)
        if blacklist_reason:
            log_request("URL", url, 100, "dangerous")
            return {
                "score": 100,
                "verdict": "dangerous",
                "reasons": [f"Blacklisted: {blacklist_reason}"]
            }

        # 1. Punycode Check (High Risk)
        if is_punycode(domain):
            score += 50
            reasons.append("Punycode domain detected (potential homograph attack)")

        # 2. Suspicious Keywords in Domain (Medium Risk)
        suspicious_keywords = ["secure", "login", "account", "update", "verify", "banking", "wallet"]
        for keyword in suspicious_keywords:
            if keyword in domain and "google" not in domain and "facebook" not in domain: # Simple whitelist for demo
                score += 20
                reasons.append(f"Suspicious keyword '{keyword}' in domain")
                break

        # 3. IP Address as Domain (High Risk)
        if re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", domain):
            score += 40
            reasons.append("IP address used as domain")

        # 4. Long Subdomains (Medium Risk)
        if len(domain) > 50:
            score += 15
            reasons.append("Unusually long domain name")

        # 5. Multiple Subdomains (Medium Risk)
        if domain.count(".") > 3:
            score += 15
            reasons.append("Excessive number of subdomains")

        # 6. Homograph Attack (High Risk)
        if is_homograph(domain):
            score += 60
            reasons.append("Homograph attack detected (mixed scripts)")

        # 7. Evilginx2 Patterns (High Risk)
        # Check for known brands in subdomains but not in the main domain
        brands = ["google", "facebook", "instagram", "paypal", "microsoft", "netflix"]
        if any(brand in domain for brand in brands):
             # If brand is present, check if it is the TLD
             is_official = any(domain.endswith(f"{brand}.com") or domain.endswith(f"{brand}.uz") for brand in brands)
             if not is_official:
                 score += 40
                 reasons.append("Brand name found in suspicious domain (potential Evilginx2)")

        # 8. Google Safe Browsing (Optional - requires API Key)
        sb_result = check_google_safe_browsing(url)
        if sb_result:
            score = 100 # If Google says it's bad, it's definitely bad
            reasons.append(f"Google Safe Browsing: {sb_result}")

        # Normalize score
        score = min(score, 100)
        
        verdict = "safe"
        if score > 60:
            verdict = "dangerous"
        elif score > 30:
            verdict = "warning"

        log_request("URL", url, score, verdict)

        return {
            "score": score,
            "verdict": verdict,
            "reasons": reasons
        }

    except Exception as e:
        return {"score": 0, "verdict": "error", "reasons": [str(e)]}

def check_google_safe_browsing(url: str) -> str:
    """
    Checks URL against Google Safe Browsing API if key is present.
    Returns the threat type if found, or None.
    """
    api_key = os.getenv("GOOGLE_SAFE_BROWSING_KEY")
    if not api_key:
        return None

    try:
        payload = {
            "client": {
                "clientId": "nurisafety-app",
                "clientVersion": "1.0.0"
            },
            "threatInfo": {
                "threatTypes": ["MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE"],
                "platformTypes": ["ANY_PLATFORM"],
                "threatEntryTypes": ["URL"],
                "threatEntries": [{"url": url}]
            }
        }
        
        response = requests.post(
            f"https://safebrowsing.googleapis.com/v4/threatMatches:find?key={api_key}",
            json=payload,
            timeout=3
        )
        
        if response.status_code == 200:
            data = response.json()
            if "matches" in data and data["matches"]:
                return data["matches"][0]["threatType"]
                
    except Exception:
        pass # Fail silently to not block the app
        
    return None

# Mock database of malicious hashes for demo (should be moved to DB in future)
MALICIOUS_HASHES = {
    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855": "Empty File (Test)",
    "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8": "Test Virus Signature"
}

def check_apk_hash(file_hash: str) -> dict:
    """Checks if the APK hash is in the known malicious database."""
    # Check DB blacklist first
    blacklist_reason = check_blacklist(file_hash)
    if blacklist_reason:
        log_request("APK", file_hash, 100, "dangerous")
        return {
            "score": 100,
            "verdict": "dangerous",
            "reasons": [f"Blacklisted: {blacklist_reason}"]
        }

    if file_hash in MALICIOUS_HASHES:
        log_request("APK", file_hash, 100, "dangerous")
        return {
            "score": 100,
            "verdict": "dangerous",
            "reasons": [f"Known malicious signature: {MALICIOUS_HASHES[file_hash]}"]
        }
    
    log_request("APK", file_hash, 0, "safe")
    return {
        "score": 0,
        "verdict": "safe",
        "reasons": []
    }
