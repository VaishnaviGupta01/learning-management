package com.lms.dto.question;

import java.util.List;

import com.lms.entity.enums.Difficulty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Single-answer multiple-choice question: 2-6 options, exactly one marked correct. */
public record QuestionRequest(
        @NotBlank String text,
        String explanation,
        Difficulty difficulty,
        @NotNull @Size(min = 2, max = 6) List<@Valid @NotNull OptionRequest> options) {

    @AssertTrue(message = "Exactly one option must be marked correct")
    public boolean isExactlyOneCorrect() {
        return options == null || options.stream().filter(o -> o != null && o.correct()).count() == 1;
    }
}
