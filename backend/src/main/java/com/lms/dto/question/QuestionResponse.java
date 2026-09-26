package com.lms.dto.question;

import java.time.Instant;
import java.util.List;

import com.lms.entity.Question;
import com.lms.entity.enums.CreatedByType;
import com.lms.entity.enums.Difficulty;
import com.lms.entity.enums.QuestionStatus;

/** Full question with answers; only returned to instructors/admins. */
public record QuestionResponse(
        Long id,
        Long topicId,
        String text,
        String explanation,
        Difficulty difficulty,
        QuestionStatus status,
        CreatedByType createdByType,
        Long createdById,
        Long reviewedById,
        Instant reviewedAt,
        List<OptionResponse> options,
        Instant createdAt) {

    public static QuestionResponse from(Question q) {
        return new QuestionResponse(q.getId(), q.getTopic().getId(), q.getText(), q.getExplanation(),
                q.getDifficulty(), q.getStatus(), q.getCreatedByType(),
                q.getCreatedBy() == null ? null : q.getCreatedBy().getId(),
                q.getReviewedBy() == null ? null : q.getReviewedBy().getId(),
                q.getReviewedAt(),
                q.getOptions().stream().map(OptionResponse::from).toList(),
                q.getCreatedAt());
    }
}
