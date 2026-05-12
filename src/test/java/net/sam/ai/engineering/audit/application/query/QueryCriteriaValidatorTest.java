package net.sam.ai.engineering.audit.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import net.sam.ai.engineering.audit.domain.shared.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QueryCriteriaValidatorTest {

    private static final OffsetDateTime T1 = OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T2 = OffsetDateTime.of(2026, 5, 2, 0, 0, 0, 0, ZoneOffset.UTC);

    private final QueryCriteriaValidator validator = new QueryCriteriaValidator();

    @Test
    void rejectsCriteriaWithNoSubstantiveFilter_allNull() {
        QueryCriteria criteria = new QueryCriteria(null, null, null, null, null);

        assertThatThrownBy(() -> validator.validate(criteria))
                .isInstanceOf(InvalidQueryException.class)
                .extracting(e -> ((InvalidQueryException) e).kind())
                .isEqualTo(InvalidQueryException.ProblemKind.NO_FILTER);
    }

    @Test
    void rejectsCriteriaWithNoSubstantiveFilter_emptyTimeRange() {
        QueryCriteria criteria = new QueryCriteria(null, null, null, null, new TimeRange(null, null));

        assertThatThrownBy(() -> validator.validate(criteria))
                .isInstanceOf(InvalidQueryException.class)
                .extracting(e -> ((InvalidQueryException) e).kind())
                .isEqualTo(InvalidQueryException.ProblemKind.NO_FILTER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t"})
    void rejectsBlankActor(String blank) {
        QueryCriteria criteria = new QueryCriteria(blank, null, null, null, null);

        InvalidQueryException ex = catchInvalidQuery(criteria);

        assertThat(ex.kind()).isEqualTo(InvalidQueryException.ProblemKind.BLANK_FILTER);
        assertThat(ex.field()).isEqualTo(Optional.of("actor"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t"})
    void rejectsBlankResource(String blank) {
        QueryCriteria criteria = new QueryCriteria(null, blank, null, null, null);

        InvalidQueryException ex = catchInvalidQuery(criteria);

        assertThat(ex.kind()).isEqualTo(InvalidQueryException.ProblemKind.BLANK_FILTER);
        assertThat(ex.field()).isEqualTo(Optional.of("resource"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t"})
    void rejectsBlankEventType(String blank) {
        QueryCriteria criteria = new QueryCriteria(null, null, blank, null, null);

        InvalidQueryException ex = catchInvalidQuery(criteria);

        assertThat(ex.kind()).isEqualTo(InvalidQueryException.ProblemKind.BLANK_FILTER);
        assertThat(ex.field()).isEqualTo(Optional.of("event_type"));
    }

    @Test
    void rejectsFromAfterTo() {
        QueryCriteria criteria = new QueryCriteria(null, null, null, null, new TimeRange(T2, T1));

        InvalidQueryException ex = catchInvalidQuery(criteria);

        assertThat(ex.kind()).isEqualTo(InvalidQueryException.ProblemKind.INVALID_TIME_RANGE);
        assertThat(ex.field()).isEmpty();
    }

    @Test
    void permitsFromEqualToWhenOtherFilterPresent() {
        QueryCriteria criteria = new QueryCriteria("u_42", null, null, null, new TimeRange(T1, T1));

        assertThatCode(() -> validator.validate(criteria)).doesNotThrowAnyException();
    }

    @Test
    void permitsOnlyFrom() {
        QueryCriteria criteria = new QueryCriteria("u_42", null, null, null, new TimeRange(T1, null));

        assertThatCode(() -> validator.validate(criteria)).doesNotThrowAnyException();
    }

    @Test
    void permitsOnlyTo() {
        QueryCriteria criteria = new QueryCriteria("u_42", null, null, null, new TimeRange(null, T2));

        assertThatCode(() -> validator.validate(criteria)).doesNotThrowAnyException();
    }

    @Test
    void permitsTimeRangeAloneAsSubstantiveFilter() {
        QueryCriteria onlyFrom = new QueryCriteria(null, null, null, null, new TimeRange(T1, null));
        QueryCriteria onlyTo = new QueryCriteria(null, null, null, null, new TimeRange(null, T2));

        assertThatCode(() -> validator.validate(onlyFrom)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(onlyTo)).doesNotThrowAnyException();
    }

    @Test
    void permitsOutcomeAloneAsSubstantiveFilter() {
        QueryCriteria criteria = new QueryCriteria(null, null, null, Outcome.SUCCESS, null);

        assertThatCode(() -> validator.validate(criteria)).doesNotThrowAnyException();
    }

    @Test
    void exceptionExposesStableProblemKindEnum() {
        assertThat(InvalidQueryException.ProblemKind.values())
                .containsExactlyInAnyOrder(
                        InvalidQueryException.ProblemKind.NO_FILTER,
                        InvalidQueryException.ProblemKind.BLANK_FILTER,
                        InvalidQueryException.ProblemKind.INVALID_TIME_RANGE);
    }

    private InvalidQueryException catchInvalidQuery(QueryCriteria criteria) {
        try {
            validator.validate(criteria);
        } catch (InvalidQueryException e) {
            return e;
        }
        throw new AssertionError("Expected InvalidQueryException was not thrown");
    }
}
