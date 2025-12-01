import sqlite3
from pydantic import BaseModel
from typing import List, Optional
import time

DB_NAME = "phishguard.db"

def init_db():
    """Initializes the SQLite database with necessary tables."""
    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()
    
    # Logs table
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp REAL,
            type TEXT,
            target TEXT,
            score INTEGER,
            verdict TEXT
        )
    ''')
    
    # Blacklist table (for manual blocking)
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS blacklist (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            target TEXT UNIQUE,
            reason TEXT,
            added_at REAL
        )
    ''')
    
    conn.commit()
    conn.close()

def log_request(type: str, target: str, score: int, verdict: str):
    """Logs a request to the database."""
    try:
        conn = sqlite3.connect(DB_NAME)
        cursor = conn.cursor()
        cursor.execute(
            "INSERT INTO logs (timestamp, type, target, score, verdict) VALUES (?, ?, ?, ?, ?)",
            (time.time(), type, target, score, verdict)
        )
        conn.commit()
        conn.close()
    except Exception as e:
        print(f"Logging error: {e}")

def check_blacklist(target: str) -> Optional[str]:
    """Checks if a target is in the blacklist. Returns reason if found, else None."""
    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()
    cursor.execute("SELECT reason FROM blacklist WHERE target = ?", (target,))
    result = cursor.fetchone()
    conn.close()
    
    if result:
        return result[0]
    return None

# Initialize DB on module import (or call explicitly in main)
init_db()
