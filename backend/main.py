from fastapi import FastAPI
from pydantic import BaseModel
from app.utils import analyze_url, check_apk_hash

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
    result = analyze_url(request.url)
    return result

@app.post("/check/apk")
def check_apk(request: ApkCheckRequest):
    result = check_apk_hash(request.hash)
    return result

