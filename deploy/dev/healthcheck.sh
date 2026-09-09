#!/usr/bin/env bash
set -euo pipefail

curl --fail --silent --show-error http://127.0.0.1:8081/actuator/health | grep -q '"status":"UP"'
docker inspect --format '{{.State.Health.Status}}' howmuch-finance-kafka | grep -qx healthy
