package com.lms.dto.quiz;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Only the selected option ids are accepted. Any extra fields a client adds (e.g. "correct": true)
 * are ignored; correctness is always recomputed server-side.
 */
public record SubmitAttemptRequest(@NotNull List<@Valid @NotNull Answer> answers) {

    /** {@code selectedOptionId} may be null for a skipped question. */
    public record Answer(
            @NotNull Long questionId,
            Long selectedOptionId,
            @PositiveOrZero Integer timeSpentSeconds) {
    }
}
