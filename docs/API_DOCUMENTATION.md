# LEAP Analytics REST API Documentation

## Quick Start

### 1. Install Dependencies
```bash
pip install -r requirements.txt
```

### 2. Configure Environment
```bash
cp .env.example .env
# Edit .env with your database credentials
```

### 3. Run API
```bash
python api_server.py
# or
./run_api.sh 8000 4
```

### 4. Access Documentation
- **Interactive Docs (Swagger UI)**: http://localhost:8000/docs
- **OpenAPI Schema**: http://localhost:8000/openapi.json
- **Health Check**: http://localhost:8000/health

---

## API Endpoints

### Health & Status
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Service health status |
| `/` | GET | API root with links |

### Portfolio Endpoints
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/portfolio/{client_id}` | GET | Get portfolio performance for client |
| `/api/portfolio/risk/{client_id}` | GET | Get risk profile for client |
| `/api/portfolio/top-clients` | GET | Get top clients by portfolio value |

**Example Request:**
```bash
curl http://localhost:8000/api/portfolio/1

# Response:
{
  "client_id": 1,
  "total_cash": 50000000.00,
  "total_holdings_value": 250000.00,
  "total_portfolio_value": 50250000.00,
  "holdings": [
    {
      "instrument_id": 1,
      "instrument_name": "APPLE",
      "asset_class": "EQUITY",
      "quantity": 100,
      "current_price": 150.50,
      "total_value": 15050.00
    }
  ]
}
```

---

### Trading Endpoints
| Endpoint | Method | Description | Query Params |
|----------|--------|-------------|--------------|
| `/api/trading/orders/{client_id}` | GET | Get order history with fills | `limit=100`, `status=FILLED` |
| `/api/trading/activity` | GET | Get trading activity metrics | `client_id=1` |
| `/api/trading/popular-instruments` | GET | Get most traded instruments | `limit=10` |

**Example Request:**
```bash
curl "http://localhost:8000/api/trading/orders/1?limit=10"

# Response:
[
  {
    "order_id": 1,
    "instrument_name": "APPLE",
    "order_side": "BUY",
    "order_type": "MARKET",
    "requested_quantity": 100,
    "order_status": "FILLED",
    "created_at": "2026-09-24T10:00:00",
    "fills": [
      {
        "fill_id": 1,
        "order_id": 1,
        "executed_quantity": 100,
        "executed_price": 150.50,
        "executed_at": "2026-09-24T10:00:05"
      }
    ]
  }
]
```

---

### Analytics Endpoints
| Endpoint | Method | Description | Query Params |
|----------|--------|-------------|--------------|
| `/api/analytics/kpis` | GET | Get 24h KPI metrics | `client_id=1` |
| `/api/analytics/alerts` | GET | Get risk alerts | `client_id=1`, `severity=High` |
| `/api/analytics/audit-trail` | GET | Get audit trail | `entity_name=orders`, `entity_id=1`, `limit=100` |
| `/api/analytics/order-lifecycle/{order_id}` | GET | Get complete order lifecycle | - |
| `/api/analytics/platform-overview` | GET | Get platform statistics | - |

**Example Request:**
```bash
curl http://localhost:8000/api/analytics/platform-overview

# Response:
{
  "total_clients": 10,
  "total_accounts": 10,
  "total_instruments": 12,
  "total_orders": 57,
  "total_fills": 40,
  "total_cash_managed": 500000000.00,
  "total_portfolio_value": 250000.00,
  "avg_fill_rate_pct": 70.2
}
```

---

### Watchlist Endpoints
| Endpoint | Method | Description | Query Params |
|----------|--------|-------------|--------------|
| `/api/watchlist/{client_id}` | GET | Get client watchlist | - |
| `/api/watchlist/popular` | GET | Get most watched instruments | `limit=20` |

**Example Request:**
```bash
curl http://localhost:8000/api/watchlist/1

# Response:
{
  "client_id": 1,
  "items": [
    {
      "instrument_id": 1,
      "instrument_name": "APPLE",
      "current_price": 150.50,
      "alert_price_buy": 145.00,
      "alert_price_sell": 155.00,
      "added_at": "2026-09-20T08:00:00"
    }
  ]
}
```

---

## Error Handling

### Standard Error Response
```json
{
  "detail": "Client 999 not found"
}
```

### HTTP Status Codes
- **200 OK** - Request successful
- **400 Bad Request** - Invalid query parameters
- **404 Not Found** - Resource not found
- **500 Internal Server Error** - Database or server error

---

## Connection Pooling

The API uses SQLAlchemy connection pooling for production performance:
- **Pool Size**: 20 connections
- **Max Overflow**: 40 additional connections
- **Pool Pre-Ping**: Enabled (tests connections before use)
- **Timeout**: 10 seconds

---

## Performance Optimization

### Caching (Phase 2)
Recommended additions for scaling:
```python
from fastapi_cache2 import FastAPICache2
from fastapi_cache2.backends.redis import RedisBackend

@app.get("/api/portfolio/{client_id}")
@cached(namespace="portfolio", expire=300)
def get_portfolio(client_id: int):
    # Cached for 5 minutes
    ...
```

### Pagination (Phase 2)
For large result sets:
```bash
curl "http://localhost:8000/api/trading/orders/1?skip=0&limit=20"
```

---

## Deployment

### Docker Deployment
```bash
docker-compose up --build

# API will be available at http://localhost:8000
```

### Kubernetes Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: leap-analytics-api
spec:
  replicas: 3
  containers:
  - name: api
    image: leap-analytics:1.0.0
    ports:
    - containerPort: 8000
    env:
    - name: DB_HOST
      value: postgres-service
    - name: DB_NAME
      value: team_rocket_db
```

### Environment Variables
```bash
DB_USER=team_rocket_admin
DB_PASSWORD=team_rocket_password123
DB_HOST=db  # or RDS endpoint
DB_PORT=5432
DB_NAME=team_rocket_db

API_HOST=0.0.0.0
API_PORT=8000
API_WORKERS=4
DEBUG=false
```

---

## Testing

### Run API Tests
```bash
# Start API
python api_server.py

# In another terminal
python test_api.py
```

### Run pytest
```bash
pytest api/ -v
pytest api/ --cov=api
```

---

## Architecture

### Project Structure
```
api/
├── __init__.py
├── database.py           # Connection pooling & session management
├── models.py            # Pydantic response schemas
├── queries/
│   ├── __init__.py
│   ├── portfolio.py     # Portfolio analytics queries
│   ├── trading.py       # Trading analytics queries
│   ├── analytics.py     # KPIs, alerts, audit trails
│   └── watchlist.py     # Watchlist queries
└── routes/
    ├── __init__.py
    ├── portfolio.py     # Portfolio endpoints
    ├── trading.py       # Trading endpoints
    ├── analytics.py     # Analytics endpoints
    └── watchlist.py     # Watchlist endpoints

main.py                 # FastAPI app factory
api_server.py          # Uvicorn entrypoint
run_api.sh            # Shell script to start API
test_api.py           # Integration tests
```

### Data Flow
```
Request → Route Handler → Query Function → Database
   ↓
Pydantic Model Validation
   ↓
JSON Response
```

---

## Security Considerations

### Current Implementation
- ✅ Input validation via Pydantic
- ✅ SQL injection prevention via SQLAlchemy
- ✅ Connection timeout (10s)

### Recommended Phase 2
- [ ] Authentication (JWT tokens)
- [ ] Authorization (role-based access)
- [ ] Rate limiting
- [ ] HTTPS/TLS
- [ ] Request signing
- [ ] Audit logging for API calls

---

## Monitoring & Logging

### Health Check
```bash
curl -s http://localhost:8000/health | jq .
```

### Access Logs
Enabled by default - logs all requests with response times

### Slow Query Logging (Phase 2)
```python
import time
@app.middleware("http")
async def log_slow_requests(request, call_next):
    start = time.time()
    response = await call_next(request)
    duration = time.time() - start
    if duration > 1.0:
        logger.warning(f"Slow request: {request.url} took {duration:.2f}s")
    return response
```

---

## Future Enhancements

### Phase 2 - Analytics
1. **Real-time KPIs** via WebSocket
2. **Materialized Views** for complex queries
3. **Time-series Data** (performance trends)
4. **Predictive Models** (risk forecasting)

### Phase 3 - Scale
1. **Read Replicas** for reporting isolation
2. **Redis Cache** for frequently accessed queries
3. **Message Queue** (Kafka) for async processing
4. **Data Warehouse** (Snowflake/BigQuery)

### Phase 4 - Features
1. **Custom Reports** (scheduled exports)
2. **Webhooks** (event-driven notifications)
3. **GraphQL** endpoint (alternative to REST)
4. **Mobile API** (optimized for bandwidth)

---

## Support

For issues or questions:
1. Check `/docs` interactive documentation
2. Review error messages in server logs
3. Test endpoint with curl/Postman
4. Run `python test_api.py` for basic connectivity

