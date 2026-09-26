package com.lms.dto.question;

import com.lms.entity.QuestionOption;

/** Option including its correctness flag; only returned to instructors/admins. */
public record OptionResponse(Long id, String text, boolean correct, int orderIndex) {

    public static OptionResponse from(QuestionOption o) {
        return new OptionResponse(o.getId(), o.getText(), o.isCorrect(), o.getOrderIndex());
    }
}
