package net.sam.ai.engineering.audit.application.query;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Cursor(OffsetDateTime rt, UUID id, String fp) {}
