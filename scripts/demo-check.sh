#!/usr/bin/env bash

set -u

failed=0
while IFS='|' read -r name url; do
  if curl --silent --show-error --fail --max-time 5 "$url" >/dev/null; then
    printf 'UP   %s\n' "$name"
  else
    printf 'DOWN %s (%s)\n' "$name" "$url"
    failed=1
  fi
done <<'ENDPOINTS'
Storefront|http://localhost:5173/
Auth|http://localhost:8081/actuator/health
Catalog|http://localhost:8082/actuator/health
Cart|http://localhost:8084/actuator/health
Order|http://localhost:8083/actuator/health
Payment|http://localhost:8085/actuator/health
Buyback|http://localhost:8086/actuator/health
ENDPOINTS

if [ "$failed" -ne 0 ]; then
  printf '\nStart or inspect the stack with: docker compose up -d --build && docker compose ps\n' >&2
  exit 1
fi
