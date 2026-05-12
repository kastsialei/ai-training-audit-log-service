package net.sam.ai.engineering.audit.infrastructure.persistence.shared;

import java.util.UUID;
import net.sam.ai.engineering.audit.domain.shared.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {}
