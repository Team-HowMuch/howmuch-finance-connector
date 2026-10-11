package com.amateur.howmuchfinanceconnector.adapter.in.kafka;

import com.amateur.howmuchfinanceconnector.application.port.in.sync.AcceptFinanceSyncCommand;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class FinanceSyncCommandParser {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Schema schema = SchemaRegistry
            .withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(SchemaLocation.of("classpath:schemas/finance-sync-message-v1.schema.json"));

    public FinanceSyncCommandParser(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public AcceptFinanceSyncCommand parse(String kafkaKey, String payload) {
        var errors = schema.validate(payload, InputFormat.JSON,
                context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("RC2 금융 동기화 계약을 위반한 메시지입니다.");
        }

        JsonNode root = objectMapper.readTree(payload);
        if (!"SyncAccountTransactions".equals(root.path("messageType").stringValue())) {
            throw new IllegalArgumentException("수집 command topic에는 SyncAccountTransactions만 허용됩니다.");
        }

        UUID accountRef = uuid(root, "accountRef");
        if (!accountRef.toString().equals(kafkaKey)) {
            throw new IllegalArgumentException("Kafka key와 accountRef가 일치하지 않습니다.");
        }

        JsonNode data = root.path("data");
        LocalDate fromDate = LocalDate.parse(data.path("fromDate").stringValue());
        LocalDate toDate = LocalDate.parse(data.path("toDate").stringValue());
        if (fromDate.isAfter(toDate)
                || ChronoUnit.DAYS.between(fromDate, toDate) > 30
                || toDate.isAfter(LocalDate.now(clock.withZone(SEOUL)))) {
            throw new IllegalArgumentException("거래 수집 날짜 범위가 허용 범위를 벗어났습니다.");
        }

        UUID messageId = uuid(root, "messageId");
        UUID syncJobId = uuid(root, "syncJobId");
        long syncVersion = root.path("syncVersion").longValue();
        long connectionVersion = root.path("connectionVersion").longValue();
        UUID connectionRef = uuid(data, "connectionRef");
        LocalDateTime deadlineAt = OffsetDateTime.parse(data.path("deadlineAt").stringValue())
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        String traceId = root.path("traceId").isNull() ? null : root.path("traceId").stringValue();
        String reason = data.path("reason").stringValue();

        return new AcceptFinanceSyncCommand(
                messageId,
                syncJobId,
                accountRef,
                syncVersion,
                connectionVersion,
                connectionRef,
                fromDate,
                toDate,
                deadlineAt,
                traceId,
                sha256(payload),
                sha256Values(syncJobId.toString(), accountRef.toString(), Long.toString(syncVersion),
                        Long.toString(connectionVersion), connectionRef.toString(), fromDate.toString(),
                        toDate.toString(), reason, deadlineAt.toString()));
    }

    private UUID uuid(JsonNode node, String field) {
        return UUID.fromString(node.path(field).stringValue());
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String sha256Values(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
