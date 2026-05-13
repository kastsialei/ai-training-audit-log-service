package net.sam.ai.engineering.audit.application.query;

import java.util.List;
import java.util.Optional;
import net.sam.ai.engineering.audit.domain.shared.AuditEvent;

public interface EventReader {
    List<AuditEvent> read(QueryCriteria criteria, Optional<KeysetPosition> position, int rowLimit);
}
