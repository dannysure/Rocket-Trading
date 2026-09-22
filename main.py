from fastapi import FastAPI, HTTPException
import os
from sqlalchemy import create_engine, text
from urllib.parse import quote_plus

app = FastAPI(title="LEAP Analytics Engine")

DB_USER = os.getenv("DB_USER", "team_rocket_admin")
DB_PASSWORD = os.getenv("DB_PASSWORD", "team_rocket_password123")
DB_HOST = os.getenv("DB_HOST", "db")
DB_PORT = os.getenv("DB_PORT", "5432")
DB_NAME = os.getenv("DB_NAME", "team_rocket_db")

encoded_password = quote_plus(DB_PASSWORD)
DATABASE_URL = f"postgresql://{DB_USER}:{encoded_password}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
engine = create_engine(DATABASE_URL)

@app.get("/analytics/health")
def analytics_health():
    return {"service": "analytics", "status": "active"}

@app.get("/analytics/portfolio-performance/{client_id}")
def compute_performance(client_id: int):
    return {"client_id": client_id, "performance_score": 98.5}