package net.sam.ai.engineering.audit.infrastructure.persistence.query;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.sam.ai.engineering.audit.application.query.KeysetPosition;
import net.sam.ai.engineering.audit.application.query.QueryCriteria;
import net.sam.ai.engineering.audit.application.query.TimeRange;
import net.sam.ai.engineering.audit.domain.shared.AuditEvent;
import net.sam.ai.engineering.audit.domain.shared.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Transactional
class JpaEventReaderIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    EntityManager em;

    @Autowired
    JpaEventReader reader;

    private static final OffsetDateTime T = OffsetDateTime.of(2026, 5, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void clean() {
        em.createNativeQuery("DELETE FROM audit_events").executeUpdate();
    }

    @Test
    void readsOnlyRowsMatchingAllFilters_AndSemantics() {
        UUID match = seed(T, "u_42", "doc.read", "doc:9821", Outcome.SUCCESS);
        seed(T, "other", "doc.read", "doc:9821", Outcome.SUCCESS);
        seed(T, "u_42", "doc.write", "doc:9821", Outcome.SUCCESS);
        seed(T, "u_42", "doc.read", "doc:other", Outcome.SUCCESS);
        seed(T, "u_42", "doc.read", "doc:9821", Outcome.DENIED);

        QueryCriteria criteria = new QueryCriteria("u_42", "doc:9821", "doc.read", Outcome.SUCCESS, null);

        List<AuditEvent> result = reader.read(criteria, Optional.empty(), 10);

        assertThat(result).extracting(AuditEvent::getId).containsExactly(match);
    }

    @Test
    void returnsRowsNewestFirstByRecordedAtDesc() {
        UUID oldest = seed(T, "u", "t", "r", Outcome.SUCCESS);
        UUID middle = seed(T.plusSeconds(1), "u", "t", "r", Outcome.SUCCESS);
        UUID newest = seed(T.plusSeconds(2), "u", "t", "r", Outcome.SUCCESS);

        List<AuditEvent> result = reader.read(criteria("u"), Optional.empty(), 10);

        assertThat(result).extracting(AuditEvent::getId).containsExactly(newest, middle, oldest);
    }

    @Test
    void tieBreaksEqualRecordedAtByIdDesc() {
        UUID lower = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higher = UUID.fromString("00000000-0000-0000-0000-000000000002");
        seedWithId(lower, T, "u", "t", "r", Outcome.SUCCESS);
        seedWithId(higher, T, "u", "t", "r", Outcome.SUCCESS);

        List<AuditEvent> result = reader.read(criteria("u"), Optional.empty(), 10);

        assertThat(result).extracting(AuditEvent::getId).containsExactly(higher, lower);
    }

    @Test
    void appliesHalfOpenTimeWindow_inclusiveFromExclusiveTo() {
        OffsetDateTime t0 = T;
        OffsetDateTime t1 = T.plusNanos(1_000_000);
        OffsetDateTime t2 = T.plusNanos(2_000_000);
        UUID idT0 = seed(t0, "u", "t", "r", Outcome.SUCCESS);
        UUID idT1 = seed(t1, "u", "t", "r", Outcome.SUCCESS);
        UUID idT2 = seed(t2, "u", "t", "r", Outcome.SUCCESS);

        List<AuditEvent> windowed = reader.read(
                new QueryCriteria("u", null, null, null, new TimeRange(t0, t2)), Optional.empty(), 10);

        assertThat(windowed).extracting(AuditEvent::getId).containsExactly(idT1, idT0);

        List<AuditEvent> fromOnly = reader.read(
                new QueryCriteria("u", null, null, null, new TimeRange(t0, null)), Optional.empty(), 10);
        assertThat(fromOnly).extracting(AuditEvent::getId).containsExactly(idT2, idT1, idT0);

        List<AuditEvent> toOnly = reader.read(
                new QueryCriteria("u", null, null, null, new TimeRange(null, t1)), Optional.empty(), 10);
        assertThat(toOnly).extracting(AuditEvent::getId).containsExactly(idT0);

        List<AuditEvent> empty = reader.read(
                new QueryCriteria("u", null, null, null, new TimeRange(t1, t1)), Optional.empty(), 10);
        assertThat(empty).isEmpty();
    }

    @Test
    void keysetReturnsNextContiguousSlice() {
        UUID id1 = seed(T.plusSeconds(5), "u", "t", "r", Outcome.SUCCESS);
        UUID id2 = seed(T.plusSeconds(4), "u", "t", "r", Outcome.SUCCESS);
        UUID id3 = seed(T.plusSeconds(3), "u", "t", "r", Outcome.SUCCESS);
        UUID id4 = seed(T.plusSeconds(2), "u", "t", "r", Outcome.SUCCESS);
        UUID id5 = seed(T.plusSeconds(1), "u", "t", "r", Outcome.SUCCESS);

        List<AuditEvent> page1 = reader.read(criteria("u"), Optional.empty(), 2);
        assertThat(page1).extracting(AuditEvent::getId).containsExactly(id1, id2);

        AuditEvent anchor1 = page1.get(1);
        List<AuditEvent> page2 = reader.read(
                criteria("u"),
                Optional.of(new KeysetPosition(anchor1.getRecordedAt(), anchor1.getId())),
                2);
        assertThat(page2).extracting(AuditEvent::getId).containsExactly(id3, id4);

        AuditEvent anchor2 = page2.get(1);
        List<AuditEvent> page3 = reader.read(
                criteria("u"),
                Optional.of(new KeysetPosition(anchor2.getRecordedAt(), anchor2.getId())),
                2);
        assertThat(page3).extracting(AuditEvent::getId).containsExactly(id5);
    }

    @Test
    void keysetExercisesEqualRecordedAtSecondLeg() {
        UUID higher = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID lower = UUID.fromString("00000000-0000-0000-0000-000000000001");
        seedWithId(higher, T, "u", "t", "r", Outcome.SUCCESS);
        seedWithId(lower, T, "u", "t", "r", Outcome.SUCCESS);

        List<AuditEvent> after = reader.read(
                criteria("u"), Optional.of(new KeysetPosition(T, higher)), 10);

        assertThat(after).extracting(AuditEvent::getId).containsExactly(lower);
    }

    @Test
    void limitCapsResultSet() {
        seed(T.plusSeconds(2), "u", "t", "r", Outcome.SUCCESS);
        seed(T.plusSeconds(1), "u", "t", "r", Outcome.SUCCESS);
        seed(T, "u", "t", "r", Outcome.SUCCESS);

        assertThat(reader.read(criteria("u"), Optional.empty(), 1)).hasSize(1);
        assertThat(reader.read(criteria("u"), Optional.empty(), 100)).hasSize(3);
    }

    private static QueryCriteria criteria(String actor) {
        return new QueryCriteria(actor, null, null, null, null);
    }

    private UUID seed(OffsetDateTime recordedAt, String actor, String eventType, String resource, Outcome outcome) {
        return seedWithId(UUID.randomUUID(), recordedAt, actor, eventType, resource, outcome);
    }

    private UUID seedWithId(
            UUID id, OffsetDateTime recordedAt, String actor, String eventType, String resource, Outcome outcome) {
        em.createNativeQuery(
                        "INSERT INTO audit_events (id, recorded_at, actor, event_type, resource, outcome, context) "
                                + "VALUES (?1, ?2, ?3, ?4, ?5, CAST(?6 AS audit_outcome), CAST(?7 AS jsonb))")
                .setParameter(1, id)
                .setParameter(2, recordedAt)
                .setParameter(3, actor)
                .setParameter(4, eventType)
                .setParameter(5, resource)
                .setParameter(6, outcome.name())
                .setParameter(7, "{}")
                .executeUpdate();
        em.flush();
        em.clear();
        return id;
    }
}
