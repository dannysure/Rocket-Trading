"""
Entrypoint for running the API via uvicorn
Run: python -m uvicorn api.main:app --host 0.0.0.0 --port 8000 --workers 4

Or use run_api.sh script
"""
from .main import app

if __name__ == "__main__":
    import uvicorn
    import os
    
    host = os.getenv("API_HOST", "0.0.0.0")
    port = int(os.getenv("API_PORT", "8000"))
    workers = int(os.getenv("API_WORKERS", "4"))
    
    uvicorn.run(
        "main:app",
        host=host,
        port=port,
        workers=workers,
        reload=False,
        access_log=True
    )
