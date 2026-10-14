#!/usr/bin/env bash
# Runs Maven (Java 25) in Docker – no JDK on the host. Usage: ./mvn.sh -q package
set -euo pipefail
docker run --rm ${MVN_DOCKER_ARGS:-} -u "$(id -u):$(id -g)" -e HOME=/tmp -e MAVEN_CONFIG=/tmp/.m2 \
  -v lagerscan-m2:/tmp/.m2 -v "$(cd "$(dirname "$0")" && pwd)/api:/src" -w /src \
  maven:3-eclipse-temurin-25 mvn -Dmaven.repo.local=/tmp/.m2/repository "$@"
