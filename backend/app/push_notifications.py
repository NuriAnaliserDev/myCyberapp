import time
import requests
from typing import Dict, List, Optional
from app.database import get_db_connection

# For Firebase Cloud Messaging (FCM)
import os
FCM_SERVER_KEY = os.getenv("FCM_SERVER_KEY")  # Set via environment variable

def register_push_token(user_id: int, device_token: str, platform: str = "android") -> bool:
    """Registers a push notification token for a user."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        # Check if token exists
        cursor.execute(
            "SELECT id FROM push_tokens WHERE user_id = ? AND device_token = ?",
            (user_id, device_token)
        )
        existing = cursor.fetchone()
        
        if existing:
            # Update last used
            cursor.execute(
                "UPDATE push_tokens SET last_used = ? WHERE id = ?",
                (time.time(), existing[0])
            )
        else:
            # Insert new token
            cursor.execute(
                """INSERT INTO push_tokens (user_id, device_token, platform, created_at, last_used)
                   VALUES (?, ?, ?, ?, ?)""",
                (user_id, device_token, platform, time.time(), time.time())
            )
        
        conn.commit()
        return True
    except Exception:
        return False
    finally:
        conn.close()

def send_push_notification(user_id: int, title: str, body: str, data: Optional[Dict] = None) -> bool:
    """Sends a push notification to all user devices."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        cursor.execute(
            "SELECT device_token FROM push_tokens WHERE user_id = ?",
            (user_id,)
        )
        tokens = [row[0] for row in cursor.fetchall()]
        
        if not tokens:
            return False
        
        # Send via FCM
        return send_fcm_notification(tokens, title, body, data)
    except Exception:
        return False
    finally:
        conn.close()

def send_fcm_notification(tokens: List[str], title: str, body: str, data: Optional[Dict] = None) -> bool:
    """Sends notification via Firebase Cloud Messaging."""
    if not FCM_SERVER_KEY:
        # FCM not configured, return False
        return False
    
    try:
        url = "https://fcm.googleapis.com/fcm/send"
        headers = {
            "Authorization": f"key={FCM_SERVER_KEY}",
            "Content-Type": "application/json"
        }
        
        payload = {
            "registration_ids": tokens,
            "notification": {
                "title": title,
                "body": body
            }
        }
        
        if data:
            payload["data"] = data
        
        response = requests.post(url, json=payload, headers=headers, timeout=5)
        return response.status_code == 200
    except Exception:
        return False

def unregister_push_token(user_id: int, device_token: str) -> bool:
    """Unregisters a push notification token."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        cursor.execute(
            "DELETE FROM push_tokens WHERE user_id = ? AND device_token = ?",
            (user_id, device_token)
        )
        conn.commit()
        return cursor.rowcount > 0
    except Exception:
        return False
    finally:
        conn.close()

