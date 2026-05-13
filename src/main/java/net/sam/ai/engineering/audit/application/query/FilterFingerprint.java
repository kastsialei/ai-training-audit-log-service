package net.sam.ai.engineering.audit.application.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.TreeMap;

final class FilterFingerprint {

    private static final DateTimeFormatter UTC_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSxxx");

    private final ObjectMapper mapper;

    FilterFingerprint(ObjectMapper baseMapper) {
        this.mapper = baseMapper
                .copy()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    String compute(QueryCriteria filters) {
        TreeMap<String, Object> canonical = new TreeMap<>();
        if (filters.actor() != null) {
            canonical.put("actor", filters.actor());
        }
        if (filters.resource() != null) {
            canonical.put("resource", filters.resource());
        }
        if (filters.eventType() != null) {
            canonical.put("event_type", filters.eventType());
        }
        if (filters.outcome() != null) {
            canonical.put("outcome", filters.outcome().name());
        }
        if (filters.timeRange() != null) {
            if (filters.timeRange().from() != null) {
                canonical.put("from", formatUtc(filters.timeRange().from()));
            }
            if (filters.timeRange().to() != null) {
                canonical.put("to", formatUtc(filters.timeRange().to()));
            }
        }

        byte[] bytes = serialize(canonical);
        byte[] digest = sha256(bytes);
        return hex(digest, 8);
    }

    private byte[] serialize(TreeMap<String, Object> canonical) {
        try {
            return mapper.writeValueAsBytes(canonical);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize canonical filter map", e);
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private static String formatUtc(java.time.OffsetDateTime instant) {
        return UTC_ISO.format(instant.withOffsetSameInstant(ZoneOffset.UTC));
    }

    private static String hex(byte[] bytes, int byteCount) {
        StringBuilder sb = new StringBuilder(byteCount * 2);
        for (int i = 0; i < byteCount; i++) {
            sb.append(String.format("%02x", bytes[i] & 0xff));
        }
        return sb.toString();
    }

}
