package com.lms.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lms.dto.quiz.AdaptiveQuizResponse.Adjustment;
import com.lms.entity.Question;
import com.lms.entity.RevisionSchedule;
import com.lms.entity.Topic;
import com.lms.entity.enums.Difficulty;
import com.lms.entity.enums.TopicClassification;

/** Plain unit tests for the pure decision rules inside the services. */
class AlgorithmsTest {

    @Test
    void adaptiveAdjustmentBoundaries() {
        assertThat(AdaptiveQuizService.adjustment(null)).isEqualTo(Adjustment.BASELINE);
        assertThat(AdaptiveQuizService.adjustment(0.81)).isEqualTo(Adjustment.UP);
        assertThat(AdaptiveQuizService.adjustment(0.8)).isEqualTo(Adjustment.SAME);   // "> 80%" is exclusive
        assertThat(AdaptiveQuizService.adjustment(0.5)).isEqualTo(Adjustment.SAME);   // 50-80% inclusive
        assertThat(AdaptiveQuizService.adjustment(0.49)).isEqualTo(Adjustment.DOWN);
    }

    @Test
    void difficultyShiftIsClamped() {
        assertThat(AdaptiveQuizService.shift(Difficulty.HARD, Adjustment.UP)).isEqualTo(Difficulty.HARD);
        assertThat(AdaptiveQuizService.shift(Difficulty.EASY, Adjustment.DOWN)).isEqualTo(Difficulty.EASY);
        assertThat(AdaptiveQuizService.shift(Difficulty.MEDIUM, Adjustment.UP)).isEqualTo(Difficulty.HARD);
    }

    @Test
    void sainteLagueAllocationIsProportionalAndRespectsCapacity() {
        Topic weak = topic(1), moderate = topic(2), strong = topic(3);
        Map<Long, TopicClassification> bands = Map.of(1L, TopicClassification.WEAK, 2L, TopicClassification.MODERATE,
                3L, TopicClassification.STRONG);
        Map<Topic, List<Question>> pool = new LinkedHashMap<>();
        pool.put(weak, questions(10));
        pool.put(moderate, questions(10));
        pool.put(strong, questions(10));

        List<Topic> slots = AdaptiveQuizService.allocate(List.of(weak, moderate, strong), bands, pool, 9);
        assertThat(count(slots, 1)).isEqualTo(5);
        assertThat(count(slots, 2)).isEqualTo(3);
        assertThat(count(slots, 3)).isEqualTo(1);

        pool.put(weak, questions(2)); // capacity cap: the weak topic only has 2 questions
        slots = AdaptiveQuizService.allocate(List.of(weak, moderate, strong), bands, pool, 9);
        assertThat(count(slots, 1)).isEqualTo(2);
        assertThat(slots).hasSize(9);
    }

    @Test
    void revisionUrgencyCurve() {
        Instant now = Instant.now();
        assertThat(RecommendationService.urgency(null, now)).isZero();
        assertThat(RecommendationService.urgency(schedule(now.plus(Duration.ofDays(3))), now)).isZero();
        assertThat(RecommendationService.urgency(schedule(now.plus(Duration.ofHours(5))), now)).isEqualTo(0.25);
        assertThat(RecommendationService.urgency(schedule(now), now)).isEqualTo(0.5);
        assertThat(RecommendationService.urgency(schedule(now.minus(Duration.ofDays(30))), now)).isEqualTo(1.0);
    }

    @Test
    void streaks() {
        LocalDate today = LocalDate.of(2026, 9, 26);
        Set<LocalDate> days = Set.of(today.minusDays(1), today.minusDays(2), today.minusDays(5), today.minusDays(6),
                today.minusDays(7), today.minusDays(8));
        assertThat(StudentActivityService.currentStreak(days, today)).isEqualTo(2); // yesterday still counts
        assertThat(StudentActivityService.longestStreak(days)).isEqualTo(4);
        assertThat(StudentActivityService.currentStreak(Set.of(today.minusDays(2)), today)).isZero();
    }

    @Test
    void sessionMinutesAreRoundedUpAndCapped() {
        Instant start = Instant.now();
        assertThat(ProgressService.sessionMinutes(start, start.plusSeconds(10))).isEqualTo(1);
        assertThat(ProgressService.sessionMinutes(start, start.plusSeconds(61))).isEqualTo(2);
        assertThat(ProgressService.sessionMinutes(start, start.plus(Duration.ofDays(2)))).isEqualTo(ProgressService.MAX_SESSION_MINUTES);
    }

    private static Topic topic(long id) {
        Topic t = new Topic();
        t.setId(id);
        return t;
    }

    private static List<Question> questions(int n) {
        List<Question> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Question());
        }
        return list;
    }

    private static long count(List<Topic> slots, long topicId) {
        return slots.stream().filter(t -> t.getId() == topicId).count();
    }

    private static RevisionSchedule schedule(Instant next) {
        RevisionSchedule s = new RevisionSchedule();
        s.setNextReviewAt(next);
        return s;
    }
}
