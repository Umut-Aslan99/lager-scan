#!/usr/bin/env bash
# Runs Maven (Java 25) in Docker – no JDK on the host. Usage: ./mvn.sh -q package
set -euo pipefail
# A new cache volume belongs to root (fresh machine, CI runner): hand it to the calling user first.
docker run --rm -u 0 --entrypoint sh -v lagerscan-m2:/tmp/.m2 maven:3-eclipse-temurin-25 \
  -c 'u=$1 g=$2; shift 2; for d; do [ "$(stat -c %u "$d")" = "$u" ] || chown -R "$u:$g" "$d"; done' sh "$(id -u)" "$(id -g)" /tmp/.m2
docker run --rm ${MVN_DOCKER_ARGS:-} -u "$(id -u):$(id -g)" -e HOME=/tmp -e MAVEN_CONFIG=/tmp/.m2 \
  -v lagerscan-m2:/tmp/.m2 -v "$(cd "$(dirname "$0")" && pwd)/api:/src" -w /src \
  maven:3-eclipse-temurin-25 mvn -Dmaven.repo.local=/tmp/.m2/repository "$@"
