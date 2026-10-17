#!/usr/bin/env bash
# angular.umut-aslan.dev (Lager-Scan):  ./ops.sh test | deploy | logs
set -euo pipefail
cd "$(dirname "$0")"
export GIT_SHA="${GIT_SHA:-$(git rev-parse --short HEAD 2>/dev/null || echo local)}"
PW_IMAGE="mcr.microsoft.com/playwright:v1.64.0-noble"

backend_tests() {   # JUnit: domain rules + the whole API on a random port against a throw-away PostgreSQL
  docker network create ls-unit >/dev/null 2>&1 || true
  docker rm -f ls-unit-db >/dev/null 2>&1 || true
  docker run -d --name ls-unit-db --network ls-unit -e POSTGRES_USER=lagerscan -e POSTGRES_PASSWORD=lagerscan postgres:17-alpine >/dev/null
  trap 'docker rm -f ls-unit-db >/dev/null 2>&1 || true' RETURN
  until docker exec ls-unit-db pg_isready -U lagerscan >/dev/null 2>&1; do sleep 1; done
  MVN_DOCKER_ARGS="--network ls-unit -e DB_URL=jdbc:postgresql://ls-unit-db:5432/lagerscan" ./mvn.sh -B -q test
}

e2e_tests() {       # Playwright against the full stack, isolated from production (web unit tests run in the image build)
  # The compose file joins Traefik's external network: it exists on the server; elsewhere (CI, a fresh clone) an
  # empty one stands in, so the stack can start (Traefik itself is off here).
  docker network inspect proxy >/dev/null 2>&1 || docker network create proxy >/dev/null
  TRAEFIK_ENABLE=false PUBLIC_URL=http://localhost:8080 docker compose -p ls-ci up -d --build --wait
  trap 'docker compose -p ls-ci down -v >/dev/null 2>&1 || true' RETURN
  local app; app=$(docker compose -p ls-ci ps -q app)
  docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -v "$PWD/e2e:/app" -w /app node:22-alpine npm ci --no-audit --no-fund --ignore-scripts --silent
  docker run --rm --ipc=host --network "container:$app" -u "$(id -u):$(id -g)" -e HOME=/tmp -e CI=1 -v "$PWD/e2e:/app" -w /app "$PW_IMAGE" npx playwright test
}

test()   { backend_tests; e2e_tests; }
deploy() { test; docker compose -p lager-scan up -d --build --wait --remove-orphans; docker compose -p lager-scan ps; }
logs()   { docker compose -p lager-scan logs -f --tail=100; }

"${1:-test}"
