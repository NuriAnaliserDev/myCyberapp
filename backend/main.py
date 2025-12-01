from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="PhishGuard API", version="1.0.0")

class UrlCheckRequest(BaseModel):
    url: str

class ApkCheckRequest(BaseModel):
    hash: str

@app.get("/")
def read_root():
    return {"status": "running", "service": "PhishGuard Reputation Engine"}

@app.post("/check/url")
def check_url(request: UrlCheckRequest):
    # Placeholder for logic
    return {"url": request.url, "score": 0, "verdict": "unknown"}

@app.post("/check/apk")
def check_apk(request: ApkCheckRequest):
    # Placeholder for logic
    return {"hash": request.hash, "score": 0, "verdict": "unknown"}
