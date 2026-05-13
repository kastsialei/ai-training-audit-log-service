package net.sam.ai.engineering.audit.infrastructure.persistence.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.sam.ai.engineering.audit.application.query.EventReader;
import net.sam.ai.engineering.audit.application.query.KeysetPosition;
import net.sam.ai.engineering.audit.application.query.QueryCriteria;
import net.sam.ai.engineering.audit.application.query.TimeRange;
import net.sam.ai.engineering.audit.domain.shared.AuditEvent;
import org.springframework.stereotype.Repository;

@Repository
public class JpaEventReader implements EventReader {

    private final EntityManager em;

    public JpaEventReader(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<AuditEvent> read(QueryCriteria criteria, Optional<KeysetPosition> position, int rowLimit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<AuditEvent> cq = cb.createQuery(AuditEvent.class);
        Root<AuditEvent> root = cq.from(AuditEvent.class);

        List<Predicate> predicates = new ArrayList<>();
        if (criteria.actor() != null) {
            predicates.add(cb.equal(root.get("actor"), criteria.actor()));
        }
        if (criteria.resource() != null) {
            predicates.add(cb.equal(root.get("resource"), criteria.resource()));
        }
        if (criteria.eventType() != null) {
            predicates.add(cb.equal(root.get("eventType"), criteria.eventType()));
        }
        if (criteria.outcome() != null) {
            predicates.add(cb.equal(root.get("outcome"), criteria.outcome()));
        }

        Path<OffsetDateTime> recordedAt = root.get("recordedAt");
        TimeRange timeRange = criteria.timeRange();
        if (timeRange != null && timeRange.from() != null) {
            predicates.add(cb.greaterThanOrEqualTo(recordedAt, timeRange.from()));
        }
        if (timeRange != null && timeRange.to() != null) {
            predicates.add(cb.lessThan(recordedAt, timeRange.to()));
        }

        if (position.isPresent()) {
            KeysetPosition pos = position.get();
            Path<java.util.UUID> id = root.get("id");
            predicates.add(cb.or(
                    cb.lessThan(recordedAt, pos.recordedAt()),
                    cb.and(cb.equal(recordedAt, pos.recordedAt()), cb.lessThan(id, pos.id()))));
        }

        cq.select(root)
                .where(cb.and(predicates.toArray(new Predicate[0])))
                .orderBy(cb.desc(recordedAt), cb.desc(root.get("id")));

        return em.createQuery(cq).setMaxResults(rowLimit).getResultList();
    }
}
