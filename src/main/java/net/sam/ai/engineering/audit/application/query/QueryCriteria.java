package net.sam.ai.engineering.audit.application.query;

import net.sam.ai.engineering.audit.domain.shared.Outcome;

public record QueryCriteria(
        String actor,
        String resource,
        String eventType,
        Outcome outcome,
        TimeRange timeRange) {}
