import time
import secrets
from typing import List, Dict, Optional
from app.database import get_db_connection

def create_session(user_id: int, device_name: str, device_info: Dict, ip_address: str) -> Dict:
    """Creates a new session for a user."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        session_token = secrets.token_urlsafe(32)
        cursor.execute(
            """INSERT INTO sessions (user_id, session_token, device_name, device_info, ip_address, 
                                     created_at, last_active, is_active)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            (user_id, session_token, device_name, str(device_info), ip_address, 
             time.time(), time.time(), True)
        )
        session_id = cursor.lastrowid
        conn.commit()
        
        return {
            "session_id": session_id,
            "session_token": session_token,
            "device_name": device_name,
            "created_at": time.time(),
            "is_active": True
        }
    except Exception as e:
        conn.rollback()
        return {"error": str(e)}
    finally:
        conn.close()

def get_user_sessions(user_id: int) -> List[Dict]:
    """Gets all active sessions for a user."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        cursor.execute(
            """SELECT id, session_token, device_name, device_info, ip_address, 
                      created_at, last_active, is_active
               FROM sessions
               WHERE user_id = ? AND is_active = 1
               ORDER BY last_active DESC""",
            (user_id,)
        )
        rows = cursor.fetchall()
        
        sessions = []
        for row in rows:
            sessions.append({
                "session_id": row[0],
                "session_token": row[1],
                "device_name": row[2],
                "device_info": row[3],
                "ip_address": row[4],
                "created_at": row[5],
                "last_active": row[6],
                "is_active": bool(row[7])
            })
        return sessions
    except Exception as e:
        return []
    finally:
        conn.close()

def terminate_session(user_id: int, session_id: int) -> bool:
    """Terminates a specific session."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        cursor.execute(
            """UPDATE sessions SET is_active = 0, terminated_at = ? 
               WHERE id = ? AND user_id = ?""",
            (time.time(), session_id, user_id)
        )
        conn.commit()
        return cursor.rowcount > 0
    except Exception:
        return False
    finally:
        conn.close()

def terminate_all_sessions(user_id: int, exclude_session_id: Optional[int] = None) -> int:
    """Terminates all sessions for a user except the current one."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        if exclude_session_id:
            cursor.execute(
                """UPDATE sessions SET is_active = 0, terminated_at = ? 
                   WHERE user_id = ? AND id != ?""",
                (time.time(), user_id, exclude_session_id)
            )
        else:
            cursor.execute(
                """UPDATE sessions SET is_active = 0, terminated_at = ? 
                   WHERE user_id = ?""",
                (time.time(), user_id)
            )
        conn.commit()
        return cursor.rowcount
    except Exception:
        return 0
    finally:
        conn.close()

def update_session_activity(session_token: str) -> bool:
    """Updates the last active time for a session."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        cursor.execute(
            "UPDATE sessions SET last_active = ? WHERE session_token = ? AND is_active = 1",
            (time.time(), session_token)
        )
        conn.commit()
        return cursor.rowcount > 0
    except Exception:
        return False
    finally:
        conn.close()

