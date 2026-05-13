package net.sam.ai.engineering.audit.application.query;

import java.time.OffsetDateTime;
import java.util.UUID;

public record KeysetPosition(OffsetDateTime recordedAt, UUID id) {}
