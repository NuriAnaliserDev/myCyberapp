import hashlib
import secrets
import time
from typing import Optional, Dict
from app.database import get_db_connection

def generate_token() -> str:
    """Generates a secure random token."""
    return secrets.token_urlsafe(32)

def hash_password(password: str, salt: Optional[str] = None) -> tuple[str, str]:
    """Hashes a password using PBKDF2. Returns (hash, salt)."""
    if salt is None:
        salt = secrets.token_hex(16)
    
    # Use PBKDF2 with SHA-256
    hash_obj = hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), salt.encode('utf-8'), 100000)
    hash_hex = hash_obj.hex()
    return hash_hex, salt

def verify_password(password: str, stored_hash: str, salt: str) -> bool:
    """Verifies a password against stored hash."""
    hash_hex, _ = hash_password(password, salt)
    return hash_hex == stored_hash

def create_user(device_id: str, password: Optional[str] = None) -> Dict:
    """Creates a new user account. Returns user data."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        # Check if user exists
        cursor.execute("SELECT id FROM users WHERE device_id = ?", (device_id,))
        existing = cursor.fetchone()
        
        if existing:
            return {"error": "User already exists", "user_id": existing[0]}
        
        # Create user
        password_hash = None
        salt = None
        if password:
            password_hash, salt = hash_password(password)
        
        cursor.execute(
            """INSERT INTO users (device_id, password_hash, salt, created_at, last_active)
               VALUES (?, ?, ?, ?, ?)""",
            (device_id, password_hash, salt, time.time(), time.time())
        )
        user_id = cursor.lastrowid
        
        # Generate auth token
        auth_token = generate_token()
        cursor.execute(
            """INSERT INTO auth_tokens (user_id, token, expires_at, created_at)
               VALUES (?, ?, ?, ?)""",
            (user_id, auth_token, time.time() + 86400 * 30, time.time())  # 30 days
        )
        
        conn.commit()
        return {
            "user_id": user_id,
            "device_id": device_id,
            "auth_token": auth_token,
            "created_at": time.time()
        }
    except Exception as e:
        conn.rollback()
        return {"error": str(e)}
    finally:
        conn.close()

def authenticate_user(device_id: str, password: Optional[str] = None) -> Dict:
    """Authenticates a user. Returns auth token if successful."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        cursor.execute("SELECT id, password_hash, salt FROM users WHERE device_id = ?", (device_id,))
        user = cursor.fetchone()
        
        if not user:
            return {"error": "User not found"}
        
        user_id, stored_hash, salt = user
        
        # If password is provided, verify it
        if password:
            if not stored_hash or not salt:
                return {"error": "Password not set for this account"}
            if not verify_password(password, stored_hash, salt):
                return {"error": "Invalid password"}
        
        # Update last active
        cursor.execute("UPDATE users SET last_active = ? WHERE id = ?", (time.time(), user_id))
        
        # Generate new auth token
        auth_token = generate_token()
        cursor.execute(
            """INSERT INTO auth_tokens (user_id, token, expires_at, created_at)
               VALUES (?, ?, ?, ?)""",
            (user_id, auth_token, time.time() + 86400 * 30, time.time())
        )
        
        conn.commit()
        return {
            "user_id": user_id,
            "device_id": device_id,
            "auth_token": auth_token,
            "expires_at": time.time() + 86400 * 30
        }
    except Exception as e:
        return {"error": str(e)}
    finally:
        conn.close()

def verify_token(token: str) -> Optional[Dict]:
    """Verifies an auth token. Returns user data if valid."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        cursor.execute(
            """SELECT u.id, u.device_id, at.expires_at 
               FROM users u
               JOIN auth_tokens at ON u.id = at.user_id
               WHERE at.token = ? AND at.expires_at > ?""",
            (token, time.time())
        )
        result = cursor.fetchone()
        
        if result:
            user_id, device_id, expires_at = result
            # Update last active
            cursor.execute("UPDATE users SET last_active = ? WHERE id = ?", (time.time(), user_id))
            conn.commit()
            return {
                "user_id": user_id,
                "device_id": device_id,
                "expires_at": expires_at
            }
        return None
    except Exception as e:
        return None
    finally:
        conn.close()

def revoke_token(token: str) -> bool:
    """Revokes an auth token."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        cursor.execute("DELETE FROM auth_tokens WHERE token = ?", (token,))
        conn.commit()
        return cursor.rowcount > 0
    except Exception:
        return False
    finally:
        conn.close()

