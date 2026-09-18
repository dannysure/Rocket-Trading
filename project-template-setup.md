# Rocket Trading Blank Project Template Setup

## Purpose

This document gives you a **blank full-stack template** for building the trading platform described in [Project-Business-Requirements-Specification.txt](C:/Users/Administrator/Downloads/Rocket-Trading/Project-Business-Requirements-Specification.txt), using:

- Angular
- NestJS
- Spring Boot
- PostgreSQL
- Apache Kafka
- Redis

This is intentionally a **starter blueprint only**. It does not implement business features yet. It gives you:

- the target folder structure,
- what commands to run,
- starter code snippets,
- what still needs to be filled in.

## Recommended top-level structure

```text
Rocket-Trading/
|- frontend/
|- backend/
|  |- gateway/
|  |- trading-core/
|  |- reporting-service/
|  `- contracts/
|- database/
|  |- migrations/
|  |- seeds/
|  `- schema/
|- infrastructure/
|  |- docker/
|  `- env/
|- implementation-plan/
|  `- plan.md
|- testing-strategy/
|  `- testing-strategy.md
|- .env.example
`- README.md
```

## What each area is for

### [frontend/](C:/Users/Administrator/Downloads/Rocket-Trading/frontend)

Angular browser application for:

- registration and sign-in
- order ticket
- holdings and cash views
- order history / blotter
- internal dashboards

### [backend/gateway/](C:/Users/Administrator/Downloads/Rocket-Trading/backend/gateway)

NestJS gateway / BFF for:

- auth/session handling
- frontend-facing REST APIs
- WebSocket or SSE endpoints
- request validation
- orchestration across Java services

### [backend/trading-core/](C:/Users/Administrator/Downloads/Rocket-Trading/backend/trading-core)

Spring Boot domain service for:

- trading rules
- order lifecycle
- quote usage
- holdings and cash mutations
- audit event production

### [backend/reporting-service/](C:/Users/Administrator/Downloads/Rocket-Trading/backend/reporting-service)

Spring Boot reporting service for:

- Kafka consumers
- reporting projections
- internal analytics APIs

### [database/](C:/Users/Administrator/Downloads/Rocket-Trading/database)

Database-owned workstream for:

- schema design
- migrations
- outbox strategy
- audit tables
- reporting tables

### [infrastructure/](C:/Users/Administrator/Downloads/Rocket-Trading/infrastructure)

Infrastructure setup for:

- Docker Compose
- PostgreSQL
- Kafka
- Redis
- local environment templates

## Blank template structure by app

### Angular template

```text
frontend/
|- src/
|  |- app/
|  |  |- core/
|  |  |  |- auth/
|  |  |  |- config/
|  |  |  |- http/
|  |  |  `- state/
|  |  |- features/
|  |  |  |- auth/
|  |  |  |- order-ticket/
|  |  |  |- portfolio/
|  |  |  |- blotter/
|  |  |  `- insights/
|  |  |- shared/
|  |  |- app.ts
|  |  |- app.html
|  |  |- app.scss
|  |  `- app.routes.ts
|  |- environments/
|  `- main.ts
|- package.json
`- angular.json
```

### NestJS gateway template

```text
backend/gateway/
|- src/
|  |- common/
|  |  |- filters/
|  |  |- guards/
|  |  `- interceptors/
|  |- modules/
|  |  |- auth/
|  |  |- orders/
|  |  |- portfolio/
|  |  |- quotes/
|  |  |- reporting/
|  |  `- realtime/
|  |- app.module.ts
|  `- main.ts
|- test/
`- package.json
```

### Spring Boot trading-core template

```text
backend/trading-core/
|- src/
|  |- main/
|  |  |- java/com/rockettrading/tradingcore/
|  |  |  |- config/
|  |  |  |- auth/
|  |  |  |- marketdata/
|  |  |  |- orders/
|  |  |  |- portfolio/
|  |  |  |- audit/
|  |  |  `- TradingCoreApplication.java
|  |  `- resources/
|  |     `- application.yml
|  `- test/
`- pom.xml
```

### Spring Boot reporting-service template

```text
backend/reporting-service/
|- src/
|  |- main/
|  |  |- java/com/rockettrading/reporting/
|  |  |  |- config/
|  |  |  |- ingestion/
|  |  |  |- projections/
|  |  |  |- api/
|  |  |  `- ReportingServiceApplication.java
|  |  `- resources/
|  |     `- application.yml
|  `- test/
`- pom.xml
```

### Database and infrastructure template

```text
database/
|- migrations/
|  |- V001__baseline.sql
|  |- V002__orders.sql
|  `- V003__reporting.sql
|- schema/
|  |- trading-core-schema.md
|  `- reporting-schema.md
`- seeds/
   `- sample-reference-data.sql

infrastructure/
|- docker/
|  `- docker-compose.yml
`- env/
   `- local.env.example
```

## What to run to create the blank scaffolds

Run these from [Rocket-Trading/](C:/Users/Administrator/Downloads/Rocket-Trading).

### 1. Create the top-level folders

```powershell
New-Item -ItemType Directory -Force -Path `
  backend, `
  database, `
  infrastructure, `
  implementation-plan, `
  testing-strategy
```

### 2. Create the Angular app

```powershell
Set-Location frontend
npx @angular/cli@latest new frontend --directory . --routing --style scss --skip-git --package-manager npm --defaults
```

If [frontend/](C:/Users/Administrator/Downloads/Rocket-Trading/frontend) does not exist yet:

```powershell
npx @angular/cli@latest new frontend --directory frontend --routing --style scss --skip-git --package-manager npm --defaults
```

### 3. Create the NestJS gateway

```powershell
Set-Location C:\Users\Administrator\Downloads\Rocket-Trading\backend
npx @nestjs/cli@latest new gateway --package-manager npm --skip-git --strict
```

### 4. Create the Spring Boot trading-core service

```powershell
$tmp = Join-Path $env:TEMP 'trading-core.zip'
Invoke-WebRequest -Uri 'https://start.spring.io/starter.zip?type=maven-project&language=java&baseDir=trading-core&groupId=com.rockettrading&artifactId=trading-core&name=trading-core&description=Rocket%20Trading%20Core&packageName=com.rockettrading.tradingcore&packaging=jar&javaVersion=21&dependencies=web,data-jpa,validation,security,actuator,postgresql,kafka' -OutFile $tmp
Expand-Archive -Path $tmp -DestinationPath 'C:\Users\Administrator\Downloads\Rocket-Trading\backend' -Force
Remove-Item $tmp -Force
```

### 5. Create the Spring Boot reporting-service

```powershell
$tmp = Join-Path $env:TEMP 'reporting-service.zip'
Invoke-WebRequest -Uri 'https://start.spring.io/starter.zip?type=maven-project&language=java&baseDir=reporting-service&groupId=com.rockettrading&artifactId=reporting-service&name=reporting-service&description=Rocket%20Trading%20Reporting%20Service&packageName=com.rockettrading.reporting&packaging=jar&javaVersion=21&dependencies=web,data-jpa,validation,actuator,postgresql,kafka' -OutFile $tmp
Expand-Archive -Path $tmp -DestinationPath 'C:\Users\Administrator\Downloads\Rocket-Trading\backend' -Force
Remove-Item $tmp -Force
```

### 6. Create Docker-based PostgreSQL, Kafka, and Redis locally

Create [infrastructure/docker/docker-compose.yml](C:/Users/Administrator/Downloads/Rocket-Trading/infrastructure/docker/docker-compose.yml) with the starter content below, then run:

```powershell
Set-Location C:\Users\Administrator\Downloads\Rocket-Trading\infrastructure\docker
docker compose up -d
```

## Starter files to create

These are intentionally minimal and should be treated as placeholders.

### 1. Root environment template

File: [.env.example](C:/Users/Administrator/Downloads/Rocket-Trading/.env.example)

```env
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=rocket_trading
POSTGRES_USER=rocket_user
POSTGRES_PASSWORD=rocket_password

REDIS_HOST=localhost
REDIS_PORT=6379

KAFKA_BOOTSTRAP_SERVERS=localhost:9092

GATEWAY_PORT=3000
TRADING_CORE_PORT=8081
REPORTING_SERVICE_PORT=8082
```

### 2. Docker Compose starter

File: [infrastructure/docker/docker-compose.yml](C:/Users/Administrator/Downloads/Rocket-Trading/infrastructure/docker/docker-compose.yml)

```yaml
services:
  postgres:
    image: postgres:16
    container_name: rocket-postgres
    environment:
      POSTGRES_DB: rocket_trading
      POSTGRES_USER: rocket_user
      POSTGRES_PASSWORD: rocket_password
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data

  redis:
    image: redis:7
    container_name: rocket-redis
    ports:
      - "6379:6379"

  kafka:
    image: bitnami/kafka:3.9
    container_name: rocket-kafka
    ports:
      - "9092:9092"
    environment:
      KAFKA_CFG_NODE_ID: 0
      KAFKA_CFG_PROCESS_ROLES: controller,broker
      KAFKA_CFG_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CFG_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_CFG_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: 0@kafka:9093
      KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE: "true"
      ALLOW_PLAINTEXT_LISTENER: "yes"

volumes:
  postgres-data:
```

### 3. Angular route starter

File: [frontend/src/app/app.routes.ts](C:/Users/Administrator/Downloads/Rocket-Trading/frontend/src/app/app.routes.ts)

```ts
import { Routes } from '@angular/router';

export const routes: Routes = [
  // TODO: auth routes
  // TODO: trading dashboard routes
  // TODO: internal insights routes
];
```

### 4. Angular API config starter

File: [frontend/src/app/core/config/api.config.ts](C:/Users/Administrator/Downloads/Rocket-Trading/frontend/src/app/core/config/api.config.ts)

```ts
export const apiConfig = {
  gatewayBaseUrl: 'http://localhost:3000/api',
};
```

### 5. NestJS main starter

File: [backend/gateway/src/main.ts](C:/Users/Administrator/Downloads/Rocket-Trading/backend/gateway/src/main.ts)

```ts
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  app.enableCors();
  app.setGlobalPrefix('api');
  await app.listen(process.env.PORT ?? 3000);
}

bootstrap();
```

### 6. NestJS health controller starter

File: [backend/gateway/src/modules/health/health.controller.ts](C:/Users/Administrator/Downloads/Rocket-Trading/backend/gateway/src/modules/health/health.controller.ts)

```ts
import { Controller, Get } from '@nestjs/common';

@Controller('health')
export class HealthController {
  @Get()
  getHealth() {
    return { status: 'ok', service: 'gateway' };
  }
}
```

### 7. Spring Boot application.yml starter

File: [backend/trading-core/src/main/resources/application.yml](C:/Users/Administrator/Downloads/Rocket-Trading/backend/trading-core/src/main/resources/application.yml)

```yaml
server:
  port: 8081

spring:
  application:
    name: trading-core
  datasource:
    url: jdbc:postgresql://localhost:5432/rocket_trading
    username: rocket_user
    password: rocket_password
  data:
    redis:
      host: localhost
      port: 6379
  kafka:
    bootstrap-servers: localhost:9092
```

### 8. Spring Boot health controller starter

File: [backend/trading-core/src/main/java/com/rockettrading/tradingcore/health/HealthController.java](C:/Users/Administrator/Downloads/Rocket-Trading/backend/trading-core/src/main/java/com/rockettrading/tradingcore/health/HealthController.java)

```java
package com.rockettrading.tradingcore.health;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "trading-core");
    }
}
```

### 9. Reporting service application.yml starter

File: [backend/reporting-service/src/main/resources/application.yml](C:/Users/Administrator/Downloads/Rocket-Trading/backend/reporting-service/src/main/resources/application.yml)

```yaml
server:
  port: 8082

spring:
  application:
    name: reporting-service
  datasource:
    url: jdbc:postgresql://localhost:5432/rocket_trading
    username: rocket_user
    password: rocket_password
  kafka:
    bootstrap-servers: localhost:9092
```

## What still needs to be filled in

### Angular

- login/register pages
- route guards
- order ticket UI
- holdings/blotter screens
- internal reporting dashboard
- live update client

### NestJS gateway

- JWT or session auth
- auth module
- request DTO validation
- portfolio/order/quote controllers
- WebSocket or SSE support
- proxy/orchestration to Java services

### Trading core

- client identity integration
- quote adapters
- trading rule validation
- order acceptance flow
- execution flow
- holdings/cash transaction logic
- audit event generation
- Kafka publishing

### Reporting service

- Kafka consumers
- projection builders
- aggregation APIs
- insights dashboard support

### PostgreSQL

- client table
- orders table
- fills table
- holdings table
- cash ledger
- audit trail tables
- reporting projection tables
- transactional outbox

### Kafka

Recommended starter topics:

- `orders-submitted`
- `orders-accepted`
- `orders-rejected`
- `orders-filled`
- `portfolio-updated`
- `audit-events`
- `reporting-events`

Still needed:

- topic ownership
- payload schemas
- consumer groups
- retry/dead-letter strategy

### Redis

- session revocation
- quote cache
- throttling
- real-time subscription support

## What to run after the template exists

### Start infrastructure

```powershell
Set-Location C:\Users\Administrator\Downloads\Rocket-Trading\infrastructure\docker
docker compose up -d
```

### Run Angular

```powershell
Set-Location C:\Users\Administrator\Downloads\Rocket-Trading\frontend
npm install
npm start
```

### Run NestJS gateway

```powershell
Set-Location C:\Users\Administrator\Downloads\Rocket-Trading\backend\gateway
npm install
npm run start:dev
```

### Run trading-core tests

```powershell
Set-Location C:\Users\Administrator\Downloads\Rocket-Trading\backend\trading-core
mvn test
```

### Run reporting-service tests

```powershell
Set-Location C:\Users\Administrator\Downloads\Rocket-Trading\backend\reporting-service
mvn test
```

## Order to fill things in

1. scaffold and boot each app,
2. add health endpoints,
3. wire PostgreSQL, Kafka, and Redis config,
4. define contracts between gateway and Java services,
5. implement auth,
6. implement quotes,
7. implement order lifecycle,
8. implement holdings/history,
9. implement reporting,
10. implement real-time updates and hardening.

## Best next step

Once you are ready to move beyond the blank template, use:

- [implementation-plan/plan.md](C:/Users/Administrator/Downloads/Rocket-Trading/implementation-plan/plan.md)
- [testing-strategy/testing-strategy.md](C:/Users/Administrator/Downloads/Rocket-Trading/testing-strategy/testing-strategy.md)

to drive story-by-story implementation rather than building everything at once.