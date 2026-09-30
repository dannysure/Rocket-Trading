# Rocket Trading Platform - Complete Setup & Testing Guide

## Prerequisites

- Docker & Docker Compose installed
- Java 21+ for local development
- Node.js 22+ for frontend
- PostgreSQL client (optional, for debugging)
- Maven 3.9+
- npm 11+

## Quick Start

### 1. Clone and Navigate
```bash
cd /path/to/Rocket-Trading
```

### 2. Set Environment Variables
Copy and configure `.env` file:
```bash
cp .env.example .env
# Edit .env with your settings
```

### 3. Start All Services
```bash
docker compose up -d
```

This starts:
- **PostgreSQL** (port 5435): Database
- **Kafka** (port 9092): Event streaming
- **Zookeeper** (port 2181): Kafka coordination
- **Kafka UI** (port 8080): Kafka monitoring
- **Java Backend** (port 8081): Trading API
- **Angular Frontend** (port 4200): Web UI
- **Python Analytics** (port 8082): Analytics API

### 4. Verify Services Are Running
```bash
# Check container status
docker compose ps

# Check health
curl http://localhost:8081/health
curl http://localhost:4200
```

## Architecture Overview

```
┌─────────────┐
│   Angular   │
│  Frontend   │ (Port 4200)
│  4200       │
└──────┬──────┘
       │ HTTP/WebSocket
       ▼
┌─────────────────────────────────────┐
│   Spring Boot Backend (Java 21)     │
│   Port 8081 - REST API              │
│   Features:                         │
│   - Auth (JWT)                      │
│   - Orders                          │
│   - Portfolio                       │
│   - Quotes                          │
│   - Reporting                       │
│   - WebSocket support               │
└────┬────────────────────────────────┘
     │
     ├─────────────┐
     │             │
     ▼             ▼
┌───────────┐  ┌──────────────────┐
│ PostgreSQL│  │  Kafka Topics    │
│   DB      │  │  - order-*       │
│ Port 5435 │  │  - portfolio-*   │
│           │  │  - audit-log     │
│ Tables:   │  │                  │
│ - orders  │  │ Port 9092        │
│ - fills   │  │ (with Zookeeper) │
│ - holdings│  │ UI: Port 8080    │
│ - clients │  └──────────────────┘
└───────────┘
     ▲
     │
     ▼
┌──────────────────────────────────────┐
│  Python FastAPI Analytics Service    │
│  Port 8082 (Optional)                │
│  - Trading metrics                   │
│  - Client analytics                  │
│  - Portfolio analysis                │
└──────────────────────────────────────┘
```

## Core Features Implemented

### ✅ Authentication & Authorization
- User registration with profile data
- JWT-based authentication
- Session management (sign-in/sign-out)
- Time-limited revocable sessions

### ✅ Order Management
- Submit orders (BUY/SELL)
- Get order status
- View order timeline
- Track fills with pricing
- Idempotent order submission

### ✅ Portfolio Management
- View current holdings
- Track cash balance
- Monitor total portfolio value
- Real-time position updates

### ✅ Quote Management
- Get indicative quotes
- Support for stocks, crypto, FX
- Quote caching for performance

### ✅ Event-Driven Architecture
- Kafka event streaming
- Topics: order-submitted, order-filled, order-rejected, portfolio-updated, audit-log
- Guaranteed message delivery
- Event sourcing foundation

### ✅ Real-Time Updates
- WebSocket support (STOMP protocol)
- Real-time order status updates
- Real-time portfolio updates
- SimpMessagingTemplate for server-initiated pushes

### ✅ API Endpoints

#### Auth Endpoints
```
POST /api/v1/auth/register
POST /api/v1/auth/sign-in
POST /api/v1/auth/sign-out
```

#### Order Endpoints
```
POST   /api/v1/orders              - Submit order
GET    /api/v1/orders              - List all orders
GET    /api/v1/orders/{orderId}    - Get order details
GET    /api/v1/fills/{orderId}     - Get fills for order
GET    /api/v1/orders/{orderId}/timeline - Get order timeline
```

#### Portfolio Endpoints
```
GET /api/v1/portfolio/summary      - Get portfolio summary
```

#### Quote Endpoints
```
GET /api/v1/quotes/{symbol}        - Get quote for symbol
GET /api/v1/quotes/instruments     - List supported instruments
```

#### Reporting Endpoints
```
GET /api/v1/reporting/overview     - Reporting overview
GET /api/v1/reporting/orders       - Order metrics
GET /api/v1/reporting/clients      - Client metrics
```

#### WebSocket Endpoints
```
WS  /ws                            - WebSocket connection
/app/subscribe/{clientId}          - Subscribe to portfolio updates
/topic/portfolio/{clientId}        - Receive portfolio updates
/topic/orders/{clientId}           - Receive order updates
```

## Testing the Application

### 1. Register a New Client

**Request:**
```bash
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john@example.com",
    "dateOfBirth": "1990-01-15",
    "riskProfile": "Balanced"
  }'
```

**Response:**
```json
{
  "success": true,
  "data": {
    "clientId": 1,
    "email": "john@example.com",
    "name": "John Doe",
    "registeredAt": "2024-01-15T10:30:00Z"
  }
}
```

### 2. Sign In

**Request:**
```bash
curl -X POST http://localhost:8081/api/v1/auth/sign-in \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com"
  }'
```

**Response:**
```json
{
  "success": true,
  "data": {
    "clientId": 1,
    "sessionId": 12345,
    "expiresAt": "2024-01-15T18:30:00Z",
    "accessToken": "eyJhbGc...",
    "tokenType": "Bearer"
  }
}
```

Save the `accessToken` for subsequent requests.

### 3. Get Portfolio Summary

**Request:**
```bash
curl -X GET http://localhost:8081/api/v1/portfolio/summary \
  -H "Authorization: Bearer {accessToken}"
```

**Response:**
```json
{
  "success": true,
  "data": {
    "clientId": 1,
    "accountId": 1,
    "accountType": "DIRECT_TRADING",
    "cashBalance": 10000.00,
    "currency": "GBP",
    "positions": [],
    "totalValue": 10000.00
  }
}
```

### 4. Get Supported Instruments

**Request:**
```bash
curl -X GET http://localhost:8081/api/v1/quotes/instruments \
  -H "Authorization: Bearer {accessToken}"
```

### 5. Get a Quote

**Request:**
```bash
curl -X GET http://localhost:8081/api/v1/quotes/AAPL \
  -H "Authorization: Bearer {accessToken}"
```

### 6. Place an Order

**Request:**
```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {accessToken}" \
  -H "Idempotency-Key: unique-order-id-123" \
  -d '{
    "instrumentId": 1,
    "side": "BUY",
    "quantity": 10,
    "orderType": "MARKET",
    "accountId": 1
  }'
```

**Response:**
```json
{
  "success": true,
  "data": {
    "orderId": 101,
    "clientId": 1,
    "instrumentId": 1,
    "symbol": "AAPL",
    "side": "BUY",
    "quantity": 10,
    "orderType": "MARKET",
    "status": "ACCEPTED",
    "submittedAt": "2024-01-15T10:45:00Z"
  }
}
```

### 7. Get Order Details

**Request:**
```bash
curl -X GET http://localhost:8081/api/v1/orders/101 \
  -H "Authorization: Bearer {accessToken}"
```

### 8. Get Order Timeline

**Request:**
```bash
curl -X GET http://localhost:8081/api/v1/orders/101/timeline \
  -H "Authorization: Bearer {accessToken}"
```

### 9. View Kafka Topics

Open Kafka UI at: `http://localhost:8080`

- View all topics created: order-submitted, order-accepted, order-filled, order-rejected, portfolio-updated, audit-log
- Monitor messages in real-time
- View topic configurations

## Frontend Testing (Angular UI)

### 1. Open the Application
Navigate to: `http://localhost:4200`

### 2. Register
- Click "Register" link
- Fill in the form with your details
- Click "Register" button

### 3. Sign In
- Enter the email you registered with
- Click "Sign In"

### 4. Dashboard
- View portfolio summary (cash balance, account type, total value)
- See current holdings (if any)
- View order history

### 5. Place Order
- Enter symbol (e.g., "AAPL")
- Select BUY or SELL
- Enter quantity
- Click "Place Order"
- See quote information (bid/ask)
- Monitor order status updates in real-time

## WebSocket Testing

### Connect to WebSocket
```javascript
const ws = new WebSocket('ws://localhost:8081/ws');

ws.onopen = () => {
  console.log('Connected');
  // Subscribe to portfolio updates
  ws.send(JSON.stringify({
    type: 'SUBSCRIBE',
    clientId: 1
  }));
};

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  console.log('Received:', message);
};

ws.onerror = (error) => {
  console.error('WebSocket error:', error);
};
```

## API Documentation

Full OpenAPI/Swagger documentation available at:
```
http://localhost:8081/swagger-ui.html
```

## Database Schema

### Key Tables
- `client_profiles` - Client identity and demographics
- `client_sessions` - Active session management
- `client_accounts` - Client account wrappers (ISA, GIA, etc.)
- `account_holdings` - Current positions
- `orders` - Order records
- `fills` - Executed fills with pricing
- `market_quotes` - Quote cache
- `transactions` - Cash transactions
- `audit_logs` - Audit trail for compliance

## Kafka Topics

All topics have replication factor 1 and 3 partitions for development:

| Topic | Purpose | Key | Value |
|-------|---------|-----|-------|
| order-submitted | New order submitted | clientId | OrderSubmittedEvent |
| order-accepted | Order accepted by system | clientId | OrderAcceptedEvent |
| order-filled | Order executed | clientId | OrderFilledEvent |
| order-rejected | Order rejected | clientId | OrderRejectedEvent |
| portfolio-updated | Portfolio changed | clientId | PortfolioUpdateEvent |
| audit-log | Audit trail | orderId | AuditLogEvent |

## Troubleshooting

### Services Won't Start
1. Check Docker is running: `docker info`
2. Check ports are available: `netstat -an | grep LISTEN`
3. Check logs: `docker compose logs -f [service_name]`

### Database Connection Failed
1. Check PostgreSQL is healthy: `docker compose ps db`
2. Verify credentials in `.env` match database setup
3. Try resetting: `docker compose down -v && docker compose up -d`

### Kafka Connection Issues
1. Ensure Zookeeper started first: `docker compose logs zookeeper`
2. Check Kafka broker is healthy: `docker compose logs kafka`
3. Test connectivity: `docker compose exec kafka kafka-broker-api-versions.sh --bootstrap-server localhost:9092`

### Frontend Can't Connect to Backend
1. Check CORS settings in backend
2. Verify backend is running: `curl http://localhost:8081/health`
3. Check browser console for network errors
4. Ensure API_BASE_URL in frontend is correct

## Production Considerations

### Security
- [ ] Change JWT_SECRET to a secure value
- [ ] Implement rate limiting
- [ ] Add HTTPS/TLS termination
- [ ] Implement authentication via OAuth2/OIDC
- [ ] Add field-level encryption for sensitive data

### Scalability
- [ ] Configure Kafka with proper replication
- [ ] Set up database connection pooling
- [ ] Implement caching layer (Redis)
- [ ] Scale backend with load balancer
- [ ] Use CDN for frontend assets

### Reliability
- [ ] Set up database backups
- [ ] Configure logging aggregation (ELK, Splunk)
- [ ] Implement circuit breakers
- [ ] Add distributed tracing
- [ ] Set up alerts and monitoring

### Compliance
- [ ] Implement audit logging for all trades
- [ ] Add data retention policies
- [ ] Implement encryption at rest
- [ ] Regular security audits
- [ ] Compliance testing framework

## Support

For issues or questions:
1. Check application logs: `docker compose logs -f`
2. Review database schema in `database/schema.sql`
3. Consult API documentation
4. Check Kafka topics for events

---
Generated: 2024-01-15
Version: 0.0.1-SNAPSHOT
