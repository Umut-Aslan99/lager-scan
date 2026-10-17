#!/usr/bin/env bash
# Runs npm/npx for the Angular app inside a container (the server has no Node.js).
# A new cache volume belongs to root (fresh machine, CI runner): hand it to the calling user first.
docker run --rm -u 0 --entrypoint sh -v lagerscan-npm:/tmp/.npm node:22-alpine \
  -c 'u=$1 g=$2; shift 2; for d; do [ "$(stat -c %u "$d")" = "$u" ] || chown -R "$u:$g" "$d"; done' sh "$(id -u)" "$(id -g)" /tmp/.npm
docker run --rm ${NODE_DOCKER_ARGS:-} -u "$(id -u):$(id -g)" -e HOME=/tmp -e npm_config_cache=/tmp/.npm \
  -v lagerscan-npm:/tmp/.npm -v "$(cd "$(dirname "$0")" && pwd)/web:/app" -w /app node:22-alpine "$@"
