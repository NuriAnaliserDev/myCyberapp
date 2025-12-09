import time
from typing import Dict, List, Optional
from datetime import datetime
from app.database import get_db_connection

def update_statistics(user_id: int, stat_type: str, increment: int = 1) -> bool:
    """Updates user statistics for today."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        today = datetime.now().strftime("%Y-%m-%d")
        
        # Check if record exists
        cursor.execute(
            "SELECT id FROM statistics WHERE user_id = ? AND date = ?",
            (user_id, today)
        )
        existing = cursor.fetchone()
        
        if existing:
            # Update existing record
            if stat_type == "urls_scanned":
                cursor.execute(
                    "UPDATE statistics SET urls_scanned = urls_scanned + ? WHERE user_id = ? AND date = ?",
                    (increment, user_id, today)
                )
            elif stat_type == "threats_detected":
                cursor.execute(
                    "UPDATE statistics SET threats_detected = threats_detected + ? WHERE user_id = ? AND date = ?",
                    (increment, user_id, today)
                )
            elif stat_type == "apps_scanned":
                cursor.execute(
                    "UPDATE statistics SET apps_scanned = apps_scanned + ? WHERE user_id = ? AND date = ?",
                    (increment, user_id, today)
                )
            elif stat_type == "anomalies_found":
                cursor.execute(
                    "UPDATE statistics SET anomalies_found = anomalies_found + ? WHERE user_id = ? AND date = ?",
                    (increment, user_id, today)
                )
        else:
            # Create new record
            urls_scanned = 1 if stat_type == "urls_scanned" else 0
            threats_detected = 1 if stat_type == "threats_detected" else 0
            apps_scanned = 1 if stat_type == "apps_scanned" else 0
            anomalies_found = 1 if stat_type == "anomalies_found" else 0
            
            cursor.execute(
                """INSERT INTO statistics (user_id, date, urls_scanned, threats_detected, 
                                          apps_scanned, anomalies_found)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                (user_id, today, urls_scanned, threats_detected, apps_scanned, anomalies_found)
            )
        
        conn.commit()
        return True
    except Exception as e:
        conn.rollback()
        return False
    finally:
        conn.close()

def get_user_statistics(user_id: int, days: int = 7) -> List[Dict]:
    """Gets user statistics for the last N days."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        cursor.execute(
            """SELECT date, urls_scanned, threats_detected, apps_scanned, anomalies_found
               FROM statistics
               WHERE user_id = ?
               ORDER BY date DESC
               LIMIT ?""",
            (user_id, days)
        )
        rows = cursor.fetchall()
        
        stats = []
        for row in rows:
            stats.append({
                "date": row[0],
                "urls_scanned": row[1],
                "threats_detected": row[2],
                "apps_scanned": row[3],
                "anomalies_found": row[4]
            })
        return stats
    except Exception:
        return []
    finally:
        conn.close()

def get_total_statistics(user_id: int) -> Dict:
    """Gets total statistics for a user."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        cursor.execute(
            """SELECT 
                   SUM(urls_scanned) as total_urls,
                   SUM(threats_detected) as total_threats,
                   SUM(apps_scanned) as total_apps,
                   SUM(anomalies_found) as total_anomalies
               FROM statistics
               WHERE user_id = ?""",
            (user_id,)
        )
        row = cursor.fetchone()
        
        return {
            "total_urls_scanned": row[0] or 0,
            "total_threats_detected": row[1] or 0,
            "total_apps_scanned": row[2] or 0,
            "total_anomalies_found": row[3] or 0
        }
    except Exception:
        return {
            "total_urls_scanned": 0,
            "total_threats_detected": 0,
            "total_apps_scanned": 0,
            "total_anomalies_found": 0
        }
    finally:
        conn.close()


