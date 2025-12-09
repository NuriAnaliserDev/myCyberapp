import sqlite3
from typing import List, Optional
import time

DB_NAME = "phishguard.db"

def get_db_connection():
    """Returns a database connection."""
    return sqlite3.connect(DB_NAME)

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
    
    # Users table
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id TEXT UNIQUE NOT NULL,
            password_hash TEXT,
            salt TEXT,
            created_at REAL,
            last_active REAL
        )
    ''')
    
    # Auth tokens table
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS auth_tokens (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            token TEXT UNIQUE NOT NULL,
            expires_at REAL NOT NULL,
            created_at REAL,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
    ''')
    
    # Sessions table
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            session_token TEXT UNIQUE NOT NULL,
            device_name TEXT,
            device_info TEXT,
            ip_address TEXT,
            created_at REAL,
            last_active REAL,
            is_active INTEGER DEFAULT 1,
            terminated_at REAL,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
    ''')
    
    # Statistics table
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS statistics (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            date TEXT NOT NULL,
            urls_scanned INTEGER DEFAULT 0,
            threats_detected INTEGER DEFAULT 0,
            apps_scanned INTEGER DEFAULT 0,
            anomalies_found INTEGER DEFAULT 0,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            UNIQUE(user_id, date)
        )
    ''')
    
    # Push notification tokens
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS push_tokens (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            device_token TEXT NOT NULL,
            platform TEXT DEFAULT 'android',
            created_at REAL,
            last_used REAL,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
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
