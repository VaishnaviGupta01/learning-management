package com.lms.entity.enums;

/**
 * Per-student mastery band for a topic, produced by the ML service
 * (score &ge; 0.80 STRONG, &ge; 0.60 MODERATE, &ge; 0.40 NEEDS_PRACTICE, otherwise WEAK).
 */
public enum TopicClassification {
    NOT_STARTED,
    WEAK,
    NEEDS_PRACTICE,
    MODERATE,
    STRONG
}
