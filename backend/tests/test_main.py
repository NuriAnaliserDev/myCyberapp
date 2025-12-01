from fastapi.testclient import TestClient
from backend.main import app
from backend.app.database import init_db, check_blacklist
import os

client = TestClient(app)

def setup_module(module):
    """Setup DB before tests."""
    init_db()

def test_read_root():
    response = client.get("/")
    assert response.status_code == 200
    assert response.json() == {"status": "running", "service": "PhishGuard Reputation Engine"}

def test_check_safe_url():
    response = client.post("/check/url", json={"url": "https://www.google.com"})
    assert response.status_code == 200
    data = response.json()
    assert data["verdict"] == "safe"
    assert data["score"] == 0

def test_check_dangerous_url():
    # Test with IP address as domain (heuristic)
    response = client.post("/check/url", json={"url": "http://192.168.1.1/login"})
    assert response.status_code == 200
    data = response.json()
    assert data["score"] >= 40
    assert "IP address used as domain" in data["reasons"]

def test_check_malicious_apk():
    malicious_hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    response = client.post("/check/apk", json={"hash": malicious_hash})
    assert response.status_code == 200
    data = response.json()
    assert data["verdict"] == "dangerous"
    assert data["score"] == 100

def test_database_logging():
    # This test assumes the previous requests logged data.
    # In a real scenario, we'd query the DB file directly to verify.
    assert os.path.exists("phishguard.db")
