package net.sam.ai.engineering.audit.application.query;

import org.springframework.stereotype.Component;

@Component
public class QueryCriteriaValidator {

    public void validate(QueryCriteria criteria) {
        checkBlank("actor", criteria.actor());
        checkBlank("resource", criteria.resource());
        checkBlank("event_type", criteria.eventType());
        checkTimeRange(criteria.timeRange());
        checkAtLeastOneFilter(criteria);
    }

    private void checkBlank(String field, String value) {
        if (value != null && value.trim().isEmpty()) {
            throw InvalidQueryException.blankFilter(field);
        }
    }

    private void checkTimeRange(TimeRange timeRange) {
        if (timeRange == null) {
            return;
        }
        if (timeRange.from() != null && timeRange.to() != null && timeRange.from().isAfter(timeRange.to())) {
            throw InvalidQueryException.invalidTimeRange();
        }
    }

    private void checkAtLeastOneFilter(QueryCriteria criteria) {
        if (criteria.actor() != null
                || criteria.resource() != null
                || criteria.eventType() != null
                || criteria.outcome() != null
                || hasAnyTimeBound(criteria.timeRange())) {
            return;
        }
        throw InvalidQueryException.noFilter();
    }

    private boolean hasAnyTimeBound(TimeRange timeRange) {
        return timeRange != null && (timeRange.from() != null || timeRange.to() != null);
    }
}
