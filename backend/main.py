import sys
import os

# Add the current directory (backend/) to sys.path to find 'app' module
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from fastapi import FastAPI, HTTPException, Depends, Header
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel
from typing import Optional, Dict, List
from app.utils import analyze_url, check_apk_hash
from app.database import init_db
from app.auth import create_user, authenticate_user, verify_token
from app.sessions import create_session, get_user_sessions, terminate_session, terminate_all_sessions, update_session_activity
from app.statistics import update_statistics, get_user_statistics, get_total_statistics
from app.push_notifications import register_push_token, unregister_push_token
from app.ml_model import analyze_url_with_ml

# Initialize Database
init_db()

app = FastAPI(title="PhishGuard API", version="2.0.0")
security = HTTPBearer()

# Request Models
class UrlCheckRequest(BaseModel):
    url: str

class ApkCheckRequest(BaseModel):
    hash: str

class RegisterRequest(BaseModel):
    device_id: str
    password: Optional[str] = None

class LoginRequest(BaseModel):
    device_id: str
    password: Optional[str] = None

class SessionCreateRequest(BaseModel):
    device_name: str
    device_info: Dict
    ip_address: str

class PushTokenRequest(BaseModel):
    device_token: str
    platform: str = "android"

# Dependency to get current user
async def get_current_user(credentials: HTTPAuthorizationCredentials = Depends(security)):
    token = credentials.credentials
    user = verify_token(token)
    if not user:
        raise HTTPException(status_code=401, detail="Invalid or expired token")
    return user

# Existing endpoints
@app.get("/")
def read_root():
    return {"status": "running", "service": "PhishGuard Reputation Engine v2.0"}

@app.post("/check/url")
async def check_url(request: UrlCheckRequest, credentials: Optional[HTTPAuthorizationCredentials] = Depends(security)):
    user = None
    if credentials:
        user = verify_token(credentials.credentials)
    # Use ML model for enhanced detection
    ml_result = analyze_url_with_ml(request.url)
    # Also use traditional analysis
    traditional_result = analyze_url(request.url)
    
    # Combine results (ML takes priority if confidence is high)
    if ml_result.get("ml_confidence", 0) > 0.7:
        result = ml_result
        result["method"] = "ml_enhanced"
    else:
        result = traditional_result
        result["method"] = "traditional"
        result["ml_confidence"] = ml_result.get("ml_confidence", 0)
    
    # Update statistics if user is authenticated
    if user:
        update_statistics(user["user_id"], "urls_scanned")
        if result["verdict"] in ["dangerous", "warning"]:
            update_statistics(user["user_id"], "threats_detected")
    
    return result

@app.post("/check/apk")
async def check_apk(request: ApkCheckRequest, credentials: Optional[HTTPAuthorizationCredentials] = Depends(security)):
    user = None
    if credentials:
        user = verify_token(credentials.credentials)
    result = check_apk_hash(request.hash)
    
    # Update statistics if user is authenticated
    if user:
        update_statistics(user["user_id"], "apps_scanned")
        if result["verdict"] == "dangerous":
            update_statistics(user["user_id"], "threats_detected")
    
    return result

# Authentication endpoints
@app.post("/auth/register")
async def register(request: RegisterRequest):
    result = create_user(request.device_id, request.password)
    if "error" in result:
        raise HTTPException(status_code=400, detail=result["error"])
    return result

@app.post("/auth/login")
async def login(request: LoginRequest):
    result = authenticate_user(request.device_id, request.password)
    if "error" in result:
        raise HTTPException(status_code=401, detail=result["error"])
    return result

@app.post("/auth/verify")
async def verify(user: Dict = Depends(get_current_user)):
    return {"valid": True, "user_id": user["user_id"], "device_id": user["device_id"]}

@app.post("/auth/logout")
async def logout(credentials: HTTPAuthorizationCredentials = Depends(security)):
    token = credentials.credentials
    from app.auth import revoke_token
    if revoke_token(token):
        return {"message": "Logged out successfully"}
    raise HTTPException(status_code=400, detail="Invalid token")

# Session Management endpoints
@app.post("/sessions/create")
async def create_user_session(request: SessionCreateRequest, user: Dict = Depends(get_current_user)):
    result = create_session(
        user["user_id"],
        request.device_name,
        request.device_info,
        request.ip_address
    )
    if "error" in result:
        raise HTTPException(status_code=400, detail=result["error"])
    return result

@app.get("/sessions/list")
async def list_sessions(user: Dict = Depends(get_current_user)):
    sessions = get_user_sessions(user["user_id"])
    return {"sessions": sessions}

@app.post("/sessions/{session_id}/terminate")
async def terminate_user_session(session_id: int, user: Dict = Depends(get_current_user)):
    success = terminate_session(user["user_id"], session_id)
    if not success:
        raise HTTPException(status_code=404, detail="Session not found")
    return {"message": "Session terminated successfully"}

@app.post("/sessions/terminate-all")
async def terminate_all_user_sessions(exclude_session_id: Optional[int] = None, user: Dict = Depends(get_current_user)):
    count = terminate_all_sessions(user["user_id"], exclude_session_id)
    return {"message": f"Terminated {count} sessions", "count": count}

# Statistics endpoints
@app.get("/statistics")
async def get_statistics(days: int = 7, user: Dict = Depends(get_current_user)):
    daily_stats = get_user_statistics(user["user_id"], days)
    total_stats = get_total_statistics(user["user_id"])
    return {
        "daily": daily_stats,
        "total": total_stats
    }

# Push Notifications endpoints
@app.post("/push/register")
async def register_push(request: PushTokenRequest, user: Dict = Depends(get_current_user)):
    from app.push_notifications import register_push_token
    success = register_push_token(user["user_id"], request.device_token, request.platform)
    if not success:
        raise HTTPException(status_code=400, detail="Failed to register push token")
    return {"message": "Push token registered successfully"}

@app.post("/push/unregister")
async def unregister_push(request: PushTokenRequest, user: Dict = Depends(get_current_user)):
    from app.push_notifications import unregister_push_token
    success = unregister_push_token(user["user_id"], request.device_token)
    if not success:
        raise HTTPException(status_code=400, detail="Failed to unregister push token")
    return {"message": "Push token unregistered successfully"}

