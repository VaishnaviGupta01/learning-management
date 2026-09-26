package com.lms.dto.question;

import jakarta.validation.constraints.NotBlank;

public record OptionRequest(@NotBlank String text, boolean correct) {
}
