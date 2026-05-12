package net.sam.ai.engineering.audit.api.query;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import net.sam.ai.engineering.audit.domain.shared.Outcome;

/**
 * Direct-SQL seeding helper for query API integration tests. Bypasses the JPA
 * `@Generated(EventType.INSERT)` machinery on {@code id} and {@code recorded_at}
 * so tests can pin both fields deterministically. Append-only triggers block
 * UPDATE/DELETE only — explicit INSERT is fine.
 */
final class QueryFixtures {

    private static final String INSERT_SQL =
            "INSERT INTO audit_events (id, recorded_at, actor, event_type, resource, outcome, context) "
                    + "VALUES (?, ?, ?, ?, ?, CAST(? AS audit_outcome), CAST(? AS jsonb))";

    private final DataSource dataSource;

    QueryFixtures(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    void seed(List<SeedRow> rows) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            for (SeedRow r : rows) {
                ps.setObject(1, r.id());
                ps.setTimestamp(2, Timestamp.from(r.recordedAt().toInstant()));
                ps.setString(3, r.actor());
                ps.setString(4, r.eventType());
                ps.setString(5, r.resource());
                ps.setString(6, r.outcome() == null ? null : r.outcome().name());
                ps.setString(7, r.contextJson());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to seed audit_events", e);
        }
    }

    record SeedRow(
            UUID id,
            OffsetDateTime recordedAt,
            String actor,
            String eventType,
            String resource,
            Outcome outcome,
            String contextJson) {}
}
