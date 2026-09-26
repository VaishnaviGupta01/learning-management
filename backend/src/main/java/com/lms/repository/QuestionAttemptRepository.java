package com.lms.repository;

import java.util.Collection;
import java.util.List;

import com.lms.entity.QuestionAttempt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionAttemptRepository extends JpaRepository<QuestionAttempt, Long> {

    List<QuestionAttempt> findByQuizAttemptId(Long quizAttemptId);

    @Query("select qa from QuestionAttempt qa where qa.quizAttempt.student.id = :studentId")
    List<QuestionAttempt> findByStudentId(@Param("studentId") Long studentId);

    @Query("select qa from QuestionAttempt qa where qa.quizAttempt.student.id = :studentId and qa.question.topic.id = :topicId")
    List<QuestionAttempt> findByStudentIdAndTopicId(@Param("studentId") Long studentId, @Param("topicId") Long topicId);

    @Query("select count(qa) from QuestionAttempt qa where qa.quizAttempt.student.id = :studentId and qa.question.topic.id = :topicId and qa.correct = true")
    long countCorrectByStudentIdAndTopicId(@Param("studentId") Long studentId, @Param("topicId") Long topicId);

    /** Per topic of a course: number of distinct APPROVED questions the student has answered correctly at least once. */
    @Query("""
            select q.topic.id as topicId, count(distinct q.id) as total
            from QuestionAttempt qa join qa.question q
            where qa.quizAttempt.student.id = :studentId and qa.correct = true
              and q.status = com.lms.entity.enums.QuestionStatus.APPROVED
              and q.topic.module.course.id = :courseId
            group by q.topic.id""")
    List<TopicStatsView.Count> countDistinctCorrectApprovedByTopic(@Param("studentId") Long studentId,
                                                                    @Param("courseId") Long courseId);

    /** Per topic of a course: answers given (total) and correct answers across the student's full history. */
    @Query("""
            select q.topic.id as topicId, count(qa) as total,
                   sum(case when qa.correct = true then 1 else 0 end) as correct
            from QuestionAttempt qa join qa.question q
            where qa.quizAttempt.student.id = :studentId and q.topic.module.course.id = :courseId
            group by q.topic.id""")
    List<TopicStatsView.Accuracy> accuracyByTopic(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    /** Submitted answers on the given topics, newest first, with attempt, quiz and question loaded. */
    @Query("""
            select qa from QuestionAttempt qa
              join fetch qa.quizAttempt a join fetch a.quiz join fetch qa.question q
            where a.student.id = :studentId and q.topic.id in :topicIds and a.submittedAt is not null
            order by a.submittedAt desc, qa.id desc""")
    List<QuestionAttempt> findSubmittedByStudentAndTopics(@Param("studentId") Long studentId,
                                                          @Param("topicIds") Collection<Long> topicIds);
}
