package com.lms.entity.enums;

/**
 * Review workflow for questions (AI-generated ones start as PENDING_REVIEW).
 */
public enum QuestionStatus {
    DRAFT,
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}
