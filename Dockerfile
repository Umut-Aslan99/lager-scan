# syntax=docker/dockerfile:1

# ── 1. Angular app: unit tests, production build (service worker included) ──
FROM node:22-alpine AS web
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY web/ .
RUN npx ng test --watch=false && npx ng build

# ── 2. Spring Boot jar with the Angular build as static resources ───────────
FROM maven:3-eclipse-temurin-25 AS api
WORKDIR /src
COPY api/pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY api/src src
COPY --from=web /web/dist/web/browser src/main/resources/static/
# Unit + integration tests run in ./ops.sh against a real PostgreSQL; here only the package.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/lagerscan.jar extract --destination /app

# ── 3. Runtime: JRE only, non-root ──────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS runtime
RUN apk add --no-cache tzdata && adduser -D -u 10001 app
ENV TZ=Europe/Berlin \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
WORKDIR /app
COPY --from=api /app .
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --start-interval=2s CMD wget -qO- http://127.0.0.1:8080/api/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "lagerscan.jar"]
