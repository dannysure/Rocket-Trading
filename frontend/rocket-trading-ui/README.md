# Rocket Trading UI

Angular 19 client for the Spring Boot API. Use Node.js 22.

```sh
npm ci
npm start
```

Open http://localhost:4200. `/api` is proxied to the native backend on localhost:8081.
The container build uses nginx to proxy the same routes to Compose's `api` service.

```sh
npm run test:ci   # Unit tests; Chrome or CHROME_BIN required
npm run build
npm run test:e2e  # Requires the isolated Compose test stack
```

See [the connection guide](../../docs/db-backend-connection.md) for backend/database startup,
Playwright browser installation, the isolated test stack and fixture authentication limits.
