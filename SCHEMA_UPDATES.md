# Database Schema Updates & Transaction Tests

## Summary of Changes

### New Tables Added

#### 1. **watchlist** (BR-18 - Platform Differentiation)
- User watchlist feature for tracking favorite instruments
- Supports price alerts (alert_price_buy, alert_price_sell)
- Allows client notes
- Unique constraint: one watchlist entry per (client, instrument) pair
- Enables quick-order functionality for frequently traded instruments

#### 2. **order_notifications** (BR-07)
- Real-time order status update notifications
- Tracks: status changes, partial fills, rejections, cancellations
- Supports unread notification queue (read_at timestamp)
- Enables WebSocket/SSE push without polling
- Index on order_id for fast notification retrieval

### Schema Enhancements

#### orders table modifications:
- Simplified to only track `rejection_reason` (TEXT)
- Added `accepted_at` timestamp (when order was validated and accepted)
- Added `rejected_at` timestamp (when order was rejected)
- Rejection logic handled in backend (business rules engine)
- Allows precise timeline tracking of order lifecycle

### Removed (Handled by Backend/Auth)
- **sessions table** → Managed by auth service (JWT tokens, Redis for revocation)
- **rejection_codes table** → Backend constants/enums for business rules

---

## Transaction Integrity Tests

### Test File: `tests/test_transactional_integrity.py`

Comprehensive test suite verifying BR-09 atomicity and related requirements:

#### Test Classes

**TestBR09AtomicFillUpdates** - Core atomicity tests
- `test_buy_order_fill_atomicity()` - Verifies BUY fill updates holdings, cash, and audit trail together
- `test_sell_order_fill_atomicity()` - Verifies SELL fill updates with cash proceeds

These tests verify that when a fill is created:
1. ✅ account_holdings updated (or inserted)
2. ✅ client_holdings updated (or inserted)
3. ✅ client_accounts.cash_balance adjusted
4. ✅ transactions ledger recorded (double-entry: security + cash)
5. ✅ audit_logs recorded for reconstruction
- **All succeed together or all rollback** (no partial states)

**TestBR02DataIsolation** - Security tests
- `test_client_cannot_see_other_client_holdings()` - Verifies row-level security

**TestBR14AuditTrail** - Compliance tests
- `test_order_lifecycle_audit_complete()` - Verifies full order path can be reconstructed

#### Running the Tests

```bash
# Install dependencies
pip install pytest psycopg2-binary

# Run all transaction tests
pytest tests/test_transactional_integrity.py -v

# Run specific test
pytest tests/test_transactional_integrity.py::TestBR09AtomicFillUpdates::test_buy_order_fill_atomicity -v

# Run with database connection info
DB_HOST=localhost DB_PORT=5432 DB_NAME=team_rocket_db \
DB_USER=team_rocket_admin DB_PASSWORD=team_rocket_password123 \
pytest tests/test_transactional_integrity.py -v
```

#### Test Architecture

- **DatabaseTestFixture**: Manages test database connections
- **@pytest.fixture test_data**: Creates standard test setup (client, accounts, instruments, quotes, holdings)
- Fixtures use real database connections (not mocks) to verify actual transaction semantics

---

## BRS Coverage Summary

| Table | BRS Coverage | Purpose |
|-------|--------------|---------|
| rejection_codes | BR-05 | Order validation rules |
| sessions | BR-01, BR-03 | Secure sign-in with expiry/revocation |
| watchlist | BR-18 | Watchlist / price alerts feature |
| order_notifications | BR-07 | Real-time status updates |
| orders (enhanced) | BR-04-09 | Order lifecycle with precise timestamps |
| audit_logs | BR-14, BR-15 | Immutable trail for reconstruction |
| transactions | BR-09 | Double-entry ledger for atomicity |

---

## Next Steps

### Critical (For Live Trading)
1. **Create database triggers** to auto-populate audit_logs on:
   - orders: INSERT, UPDATE, DELETE
   - fills: INSERT
   - account_holdings: INSERT, UPDATE, DELETE
   - client_holdings: INSERT, UPDATE, DELETE
   - transactions: INSERT

2. **Implement fill execution service** that:
   - Gets market quote
   - Creates fill record
   - Triggers cascading updates (holdings, cash, transactions, audit)
   - Uses SERIALIZABLE isolation level to prevent race conditions

3. **Implement session management**:
   - Generate secure session tokens on login
   - Validate session not expired/revoked on each request
   - Auto-refresh last_activity_at
   - Expire sessions after idle timeout (e.g., 30 minutes)

### Testing
4. **Database migration tests** - Ensure schema can be deployed and versioned
5. **Constraint enforcement tests** - Verify all CHECK and UNIQUE constraints work
6. **Query performance tests** - Index effectiveness for order/fill lookups

### Analytics (Reporting Isolation)
7. **Kafka consumer** - Stream fills to reporting schema
8. **Reporting projection tables** - Denormalized views for dashboard queries
9. **Isolate analytics from live trading** (prevent reporting queries from competing for resources)

---

## Database Connection Test

Verify schema is installed and accessible:

```sql
-- From psql
\c team_rocket_db team_rocket_admin

-- Check new tables exist
\dt rejection_codes sessions watchlist order_notifications

-- Check rejection codes were inserted
SELECT * FROM rejection_codes;

-- Verify indexes
\di
```

