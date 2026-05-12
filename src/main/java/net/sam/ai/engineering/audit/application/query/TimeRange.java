package net.sam.ai.engineering.audit.application.query;

import java.time.OffsetDateTime;

public record TimeRange(OffsetDateTime from, OffsetDateTime to) {}
