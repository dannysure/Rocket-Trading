"""
Database connection and session management
"""

from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker, Session
from dotenv import load_dotenv
import os
import logging

load_dotenv()

logger = logging.getLogger(__name__)

# Database configuration with proper URL encoding
from urllib.parse import quote_plus

db_host = os.getenv('DB_HOST', 'localhost')
db_port = os.getenv('DB_PORT', '5435')
db_name = os.getenv('DB_NAME', 'team_rocket_db')
db_user = os.getenv('DB_USERNAME', 'team_rocket_admin')
db_password = os.getenv('DB_PASSWORD', 'team_rocket_password123')

# URL-encode password to handle special characters
db_password_encoded = quote_plus(db_password)

DATABASE_URL = os.getenv(
    'DATABASE_URL',
    f"postgresql+psycopg2://{db_user}:{db_password_encoded}@{db_host}:{db_port}/{db_name}"
)

# Create engine with connection pooling
engine = create_engine(
    DATABASE_URL,
    pool_size=10,
    max_overflow=20,
    pool_timeout=30,
    pool_recycle=3600,
    echo=False
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def get_db() -> Session:
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
            conn.execute(text("SELECT 1"))
        return True
    except Exception as e:
        logger.error(f"Database connection failed: {e}")
        return False
