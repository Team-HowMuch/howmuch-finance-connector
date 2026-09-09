# Development deployment

1. Copy `.env.example` to a root-readable environment file and replace every placeholder.
2. Generate `KAFKA_CLUSTER_ID` once with `docker run --rm apache/kafka:3.9.2 /opt/kafka/bin/kafka-storage.sh random-uuid`.
3. Start Kafka with `docker compose --env-file <env-file> up -d`.
4. Export the admin password and run `./provision-kafka.sh` once. The script is idempotent for topics and ACLs.
5. Create the dedicated database with `psql -v connector_password="$CONNECTOR_DB_PASSWORD" -f provision-db.sql`.
6. Install the built jar and `howmuch-finance-connector.service`, then set `/etc/howmuch-finance-connector.env` mode to `0600`.
7. Run `./healthcheck.sh` before enabling any backend canary payment ID.

Required connector Kafka client settings:

```properties
KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092
KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT
KAFKA_SASL_MECHANISM=PLAIN
KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.plain.PlainLoginModule required username="connector" password="...";
```

This is a single-broker development topology. It has no broker or controller high availability.
