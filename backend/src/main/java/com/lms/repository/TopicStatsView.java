package com.lms.repository;

/** Per-topic aggregates returned by grouped JPQL queries. */
public final class TopicStatsView {

    private TopicStatsView() {
    }

    /** Aliases: topicId, total. */
    public interface Count {
        Long getTopicId();

        Long getTotal();
    }

    /** Aliases: topicId, total, correct. */
    public interface Accuracy {
        Long getTopicId();

        Long getTotal();

        Long getCorrect();
    }
}
