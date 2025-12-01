import re
from urllib.parse import urlparse

def is_punycode(domain: str) -> bool:
    """Checks if the domain contains Punycode (xn--)."""
    return "xn--" in domain

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

        # Normalize score
        score = min(score, 100)
        
        verdict = "safe"
        if score > 60:
            verdict = "dangerous"
        elif score > 30:
            verdict = "warning"

        return {
            "score": score,
            "verdict": verdict,
            "reasons": reasons
        }

    except Exception as e:
        return {"score": 0, "verdict": "error", "reasons": [str(e)]}

# Mock database of malicious hashes for demo
MALICIOUS_HASHES = {
    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855": "Empty File (Test)",
    "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8": "Test Virus Signature"
}

def check_apk_hash(file_hash: str) -> dict:
    """Checks if the APK hash is in the known malicious database."""
    if file_hash in MALICIOUS_HASHES:
        return {
            "score": 100,
            "verdict": "dangerous",
            "reasons": [f"Known malicious signature: {MALICIOUS_HASHES[file_hash]}"]
        }
    return {
        "score": 0,
        "verdict": "safe",
        "reasons": []
    }
