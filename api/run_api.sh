#!/bin/bash
# Run LEAP Analytics API with uvicorn
# Usage: ./run_api.sh [port] [workers]

set -e

PORT=${1:-8000}
WORKERS=${2:-4}

echo "🚀 Starting LEAP Analytics API..."
echo "   Port: $PORT"
echo "   Workers: $WORKERS"
echo ""

# Load environment from .env if it exists
if [ -f .env ]; then
    export $(cat .env | grep -v '#' | xargs)
fi

# Start API
python3 -m uvicorn api.main:app \
    --host 0.0.0.0 \
    --port $PORT \
    --workers $WORKERS \
    --access-log

echo ""
echo "📚 API Docs: http://localhost:$PORT/docs"
echo "🔧 OpenAPI Schema: http://localhost:$PORT/openapi.json"
