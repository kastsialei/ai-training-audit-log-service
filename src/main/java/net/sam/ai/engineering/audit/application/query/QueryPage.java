package net.sam.ai.engineering.audit.application.query;

import java.util.List;
import java.util.Optional;
import net.sam.ai.engineering.audit.domain.shared.AuditEvent;

public record QueryPage(List<AuditEvent> items, Optional<String> nextCursor) {}
