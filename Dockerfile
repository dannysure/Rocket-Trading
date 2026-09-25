# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY backend/rocket-trading/pom.xml backend/rocket-trading/pom.xml
COPY backend/rocket-trading/.mvn backend/rocket-trading/.mvn
COPY backend/rocket-trading/mvnw backend/rocket-trading/mvnw
COPY backend/rocket-trading/src backend/rocket-trading/src

RUN mvn -f backend/rocket-trading/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/backend/rocket-trading/target/rocket-trading-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
