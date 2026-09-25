# API contract and usage

[openapi.yaml](openapi.yaml) describes the nine implemented Rocket Trading API
operations using [OpenAPI 3.0.3](https://spec.openapis.org/oas/v3.0.3).
It includes JSON request/response schemas, status codes, JWT authentication,
validation constraints, defaults, and examples. The base URL is
`http://localhost:8081/api/v1`.

The YAML is a design and integration artifact. Spring does not load it to create
routes or enforce validation. This change adds documentation only; the existing
backend and Angular service already implement the described HTTP interface.

## How it is implemented

Backend sources are under
`backend/rocket-trading/src/main/java/com/rockettrading/rocket_trading`:

| Contract | Existing implementation |
| --- | --- |
| Auth operations | `controller/AuthController.java` and `service/AuthService.java` |
| Quote lookup | `controller/QuoteController.java`, `service/QuoteService.java`, and `service/FauxnanceQuoteClient.java` |
| Portfolio summary | `controller/PortfolioController.java` and `service/PortfolioService.java` |
| Orders and fills | `controller/OrderController.java` and `service/OrderService.java` |
| Request/response schemas | Records in `dto/auth`, `dto/quote`, `dto/portfolio`, and `dto/order` |
| Success/error envelopes | Records in `dto/common` |
| HTTP errors | `exception/GlobalExceptionHandler.java` |
| Bearer authentication | `config/SecurityConfig.java`, `security/JwtAuthenticationFilter.java`, and `security/RestAuthenticationEntryPoint.java` |

A request passes through JWT/session authentication, controller binding and
Bean Validation, service business logic, and MyBatis persistence. The controller
wraps successful data in `{data, meta}`. Errors use `{error}`. Sign-out returns
204 with no body. Registration and sign-in are public; all other operations,
including quote lookup, require a bearer token.

For a new operation, first agree on its path, schema, and responses in the YAML.
Then add matching DTOs, controller mapping, service logic, repository work if
needed, and validation. Add HTTP integration tests for the success response,
invalid input, authentication, and ownership where relevant. Validate the YAML
and run backend tests before merging. Updating YAML alone does not change the API.

## Run and call the API

Follow the root README for backend configuration (`JWT_SECRET` and
`FAUXNANCE_API_KEY`) and database setup. From the repository root:

```powershell
Set-Location backend/rocket-trading
mvn spring-boot:run
```

In a second PowerShell terminal, register a fresh demo client and sign in:

```powershell
$baseUrl = 'http://localhost:8081/api/v1'
$email = 'contract-' + [guid]::NewGuid().ToString('N') + '@example.com'
$registration = @{ name = 'Demo Trader'; email = $email; initialCash = 10000 }
Invoke-RestMethod -Method Post -Uri "$baseUrl/auth/register" `
  -ContentType 'application/json' -Body ($registration | ConvertTo-Json)

$session = Invoke-RestMethod -Method Post -Uri "$baseUrl/auth/sign-in" `
  -ContentType 'application/json' -Body (@{ email = $email } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($session.data.accessToken)" }

Invoke-RestMethod -Uri "$baseUrl/portfolio/summary" -Headers $headers
Invoke-RestMethod -Uri "$baseUrl/quotes/AAPL?market=stock" -Headers $headers

$request = @{ symbol = 'AAPL'; side = 'BUY'; quantity = 1; market = 'stock'; orderType = 'MARKET' }
$order = Invoke-RestMethod -Method Post -Uri "$baseUrl/orders" -Headers $headers `
  -ContentType 'application/json' -Body ($request | ConvertTo-Json)
$order.data
Invoke-RestMethod -Uri "$baseUrl/orders/$($order.data.orderId)" -Headers $headers
Invoke-RestMethod -Uri "$baseUrl/fills/$($order.data.orderId)" -Headers $headers
Invoke-RestMethod -Uri "$baseUrl/orders" -Headers $headers
Invoke-RestMethod -Method Post -Uri "$baseUrl/auth/sign-out" -Headers $headers
```

The order call changes demo cash and holdings and needs a working quote provider.
Read result values under `.data`, metadata under `.meta`, and failures under
`.error`. After sign-out, the session token no longer authorizes requests.

## Use it in API tools and Angular

Import the YAML into an OpenAPI-compatible API client, such as Postman, to create
requests, or open it in Swagger Editor to inspect the documentation. Configure
the local server URL, sign in, and use the returned token for protected requests.
No Swagger UI or HTTP endpoint serving this file has been installed in the app.

The current Angular implementation is `frontend/rocket-trading-ui/src/app/api.service.ts`
with hand-written interfaces in `models.ts`. Keep these aligned with the contract,
or optionally generate a client into a separate directory. With OpenAPI Generator
CLI installed, run from the repository root:

```powershell
openapi-generator-cli validate -i docs/api/openapi.yaml
openapi-generator-cli generate -i docs/api/openapi.yaml -g typescript-angular -o generated/angular-api
```

The [official CLI guide](https://openapi-generator.tech/docs/usage/) documents
validation and generation. Check the
[Angular generator options](https://openapi-generator.tech/docs/generators/typescript-angular/)
against the project's Angular version before integrating generated files. Pin
the generator version in your build for repeatable output. Configure its base
path and bearer token, then call methods named after the contract's `operationId`
values. Generation does not automatically replace or wire up `ApiService`.

Server interface generation is also an optional future workflow: generate into a
separate module and implement the interfaces by delegating to existing services.
Do not generate new controllers over the current controllers; that can create
duplicate route mappings. Business logic, persistence, authentication, and
transaction handling still need explicit implementation.

## Current behavior and implementation gaps

- Sign-in checks only the registered email. It is demo authentication.
- The contract requires order quantity for successful execution. The Java DTO
  has `@DecimalMin` but lacks `@NotNull`, so missing/null quantity currently
  reaches the service and produces 500. Add `@NotNull` to make this a 400.
- LIMIT orders do not require a limit price today. A null price skips the limit
  check, and price positivity is not validated. Conditional validation would be
  needed before tightening this contract.
- Fill lookup requires a token but does not check that the order belongs to the
  signed-in client. Pass the authenticated client ID into the service and verify
  ownership before returning fills to close this gap.
- Submission returns 202 even though successful execution completes immediately
  with status FILLED. Rejections return 409 and roll back the transaction,
  including the attempted rejected-order record. No idempotency mechanism exists.
- IDs are JSON int64 numbers; generated JavaScript number types cannot precisely
  represent every possible Java long. Client IDs can exceed the safe integer
  range. A future switch to string IDs needs coordinated API/client changes.

These gaps are documented, not fixed by adding the YAML. For CI, combine a
specification validator with HTTP integration tests that compare actual payloads
and status codes to the contract. Syntax validation alone cannot detect drift
between this file and controller behavior.
