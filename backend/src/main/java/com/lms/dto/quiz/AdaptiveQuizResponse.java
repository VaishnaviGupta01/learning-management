package com.lms.dto.quiz;

import java.util.List;

import com.lms.entity.enums.Difficulty;
import com.lms.entity.enums.TopicClassification;

/** The started attempt plus, per topic, why it got its share of questions and difficulty tier. */
public record AdaptiveQuizResponse(AttemptStartResponse attempt, List<TopicPlan> plan) {

    public enum Adjustment {
        /** No recent answers: start at the topic's own difficulty. */
        BASELINE,
        UP,
        SAME,
        DOWN
    }

    public record TopicPlan(
            Long topicId,
            String topicTitle,
            TopicClassification classification,
            Double recentAccuracy,
            int answersConsidered,
            Difficulty currentDifficulty,
            Difficulty targetDifficulty,
            Adjustment adjustment,
            int questionsAllocated,
            boolean revisionScheduled) {
    }
}
