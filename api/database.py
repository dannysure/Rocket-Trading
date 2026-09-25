"""
Database connection pooling and session management
Provides SQLAlchemy engine with connection pooling for production
"""
import os
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session
from sqlalchemy.pool import QueuePool
from urllib.parse import quote_plus
from typing import Generator

# Database configuration from environment
DB_USER = os.getenv("DB_USER", "team_rocket_admin")
DB_PASSWORD = os.getenv("DB_PASSWORD", "team_rocket_password123")
DB_HOST = os.getenv("DB_HOST", "db")
DB_PORT = os.getenv("DB_PORT", "5432")
DB_NAME = os.getenv("DB_NAME", "team_rocket_db")

# URL encode password to handle special characters
encoded_password = quote_plus(DB_PASSWORD)
DATABASE_URL = f"postgresql://{DB_USER}:{encoded_password}@{DB_HOST}:{DB_PORT}/{DB_NAME}"

# Create engine with connection pooling (production-grade)
engine = create_engine(
    DATABASE_URL,
    poolclass=QueuePool,
    pool_size=20,           # Number of connections to keep in pool
    max_overflow=40,        # Max additional connections beyond pool_size
    pool_pre_ping=True,     # Test connections before using
    echo=False,             # Set to True for SQL debugging
    connect_args={
        "connect_timeout": 10,
        "application_name": "leap_analytics_api"
    }
)

# Session factory
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def get_db() -> Generator[Session, None, None]:
    """
    Dependency for FastAPI routes to get database session
    Usage: def my_route(db: Session = Depends(get_db))
    """
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

def test_connection() -> bool:
    """Test database connection"""
    try:
        with engine.connect() as conn:
            conn.execute("SELECT 1")
        return True
    except Exception as e:
        print(f"Database connection failed: {e}")
        return False
