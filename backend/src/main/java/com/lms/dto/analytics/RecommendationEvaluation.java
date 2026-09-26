package com.lms.dto.analytics;

import java.util.List;

/**
 * Offline evaluation computed only from stored recommendations and real submitted answers.
 *
 * <p><b>Precision@K / Recall@K</b>: for each student with stored recommendations in the course, the "relevant"
 * topics are those where the student's answers submitted <em>after</em> the recommendations were generated
 * have accuracy below 60% (they needed the practice). Precision@K = relevant topics among the top K / K;
 * Recall@K = relevant topics among the top K / all relevant topics. Students without later answers are skipped.
 *
 * <p><b>Fixed vs adaptive path</b>: for every student and topic, consecutive quiz attempts on that topic are
 * paired; the accuracy change to the next attempt is credited to the path of the earlier one (ADAPTIVE vs fixed =
 * PRACTICE / ASSESSMENT / REVISION). Diagnostics are baselines and not credited to either path.
 */
public record RecommendationEvaluation(
        int k,
        int studentsEvaluated,
        Double precisionAtK,
        Double recallAtK,
        List<PathStats> paths,
        boolean sufficientData) {

    public record PathStats(
            String path,
            int attempts,
            int students,
            Double meanAccuracy,
            int followUpPairs,
            Double meanNextAccuracy,
            Double meanGain) {
    }
}
