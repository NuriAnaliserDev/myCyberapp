import re
from typing import Dict, List
from urllib.parse import urlparse

# Simple ML model for phishing detection (can be replaced with actual trained model)
class PhishingDetector:
    def __init__(self):
        self.feature_weights = {
            'punycode': 0.3,
            'homograph': 0.25,
            'suspicious_keywords': 0.15,
            'ip_address': 0.2,
            'long_domain': 0.05,
            'many_subdomains': 0.05
        }
    
    def extract_features(self, url: str) -> Dict[str, float]:
        """Extracts features from URL for ML model."""
        parsed = urlparse(url)
        domain = parsed.netloc
        
        features = {
            'punycode': 1.0 if 'xn--' in domain else 0.0,
            'homograph': 1.0 if self._is_homograph(domain) else 0.0,
            'suspicious_keywords': self._count_suspicious_keywords(domain),
            'ip_address': 1.0 if re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", domain) else 0.0,
            'long_domain': min(len(domain) / 100.0, 1.0),
            'many_subdomains': min(domain.count('.') / 5.0, 1.0)
        }
        
        return features
    
    def _is_homograph(self, domain: str) -> bool:
        """Checks for homograph attack."""
        has_latin = bool(re.search(r'[a-zA-Z]', domain))
        has_cyrillic = bool(re.search(r'[\u0400-\u04FF]', domain))
        return has_latin and has_cyrillic
    
    def _count_suspicious_keywords(self, domain: str) -> float:
        """Counts suspicious keywords in domain."""
        keywords = ["secure", "login", "account", "update", "verify", "banking", "wallet"]
        count = sum(1 for kw in keywords if kw in domain.lower())
        return min(count / 3.0, 1.0)
    
    def predict(self, url: str) -> Dict:
        """Predicts phishing probability using ML model."""
        features = self.extract_features(url)
        
        # Simple weighted sum (can be replaced with actual ML model)
        score = sum(features[key] * self.feature_weights[key] for key in features)
        score = min(score * 100, 100)  # Scale to 0-100
        
        verdict = "safe"
        if score > 60:
            verdict = "dangerous"
        elif score > 30:
            verdict = "warning"
        
        return {
            "score": int(score),
            "verdict": verdict,
            "features": features,
            "ml_confidence": min(score / 100.0, 1.0)
        }

# Global instance
ml_detector = PhishingDetector()

def analyze_url_with_ml(url: str) -> Dict:
    """Analyzes URL using ML model."""
    return ml_detector.predict(url)

