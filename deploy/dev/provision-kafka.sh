#!/usr/bin/env bash
set -euo pipefail

: "${KAFKA_ADMIN_PASSWORD:?KAFKA_ADMIN_PASSWORD is required}"

container="howmuch-finance-kafka"
config="$(mktemp)"
trap 'rm -f "$config"' EXIT
printf '%s\n' \
  'security.protocol=SASL_PLAINTEXT' \
  'sasl.mechanism=PLAIN' \
  "sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username=\"admin\" password=\"${KAFKA_ADMIN_PASSWORD}\";" > "$config"
docker cp "$config" "$container:/tmp/admin.properties"

topics=(finance.sync.command.v1 finance.sync.event.v1 finance.sync.command.v1.dlt finance.sync.event.v1.dlt)
for topic in "${topics[@]}"; do
  docker exec "$container" /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server 127.0.0.1:9092 \
    --command-config /tmp/admin.properties \
    --create --if-not-exists --topic "$topic" \
    --partitions 3 --replication-factor 1 \
    --config cleanup.policy=delete --config retention.ms=259200000
done

docker exec "$container" /opt/kafka/bin/kafka-acls.sh --bootstrap-server 127.0.0.1:9092 \
  --command-config /tmp/admin.properties --add --allow-principal User:backend \
  --operation Write --topic finance.sync.command.v1
docker exec "$container" /opt/kafka/bin/kafka-acls.sh --bootstrap-server 127.0.0.1:9092 \
  --command-config /tmp/admin.properties --add --allow-principal User:backend \
  --operation Read --topic finance.sync.event.v1 --group finance-backend-sync-v1
docker exec "$container" /opt/kafka/bin/kafka-acls.sh --bootstrap-server 127.0.0.1:9092 \
  --command-config /tmp/admin.properties --add --allow-principal User:connector \
  --operation Read --topic finance.sync.command.v1 --group finance-connector-sync-v1
docker exec "$container" /opt/kafka/bin/kafka-acls.sh --bootstrap-server 127.0.0.1:9092 \
  --command-config /tmp/admin.properties --add --allow-principal User:connector \
  --operation Write --topic finance.sync.event.v1
docker exec "$container" /opt/kafka/bin/kafka-acls.sh --bootstrap-server 127.0.0.1:9092 \
  --command-config /tmp/admin.properties --add --allow-principal User:connector \
  --operation Write --topic finance.sync.command.v1.dlt
docker exec "$container" /opt/kafka/bin/kafka-acls.sh --bootstrap-server 127.0.0.1:9092 \
  --command-config /tmp/admin.properties --add --allow-principal User:backend \
  --operation Write --topic finance.sync.event.v1.dlt
