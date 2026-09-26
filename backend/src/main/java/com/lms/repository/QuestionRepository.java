package com.lms.repository;

import java.util.List;

import com.lms.entity.Question;
import com.lms.entity.enums.CreatedByType;
import com.lms.entity.enums.Difficulty;
import com.lms.entity.enums.QuestionStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByTopicId(Long topicId);
    List<Question> findByTopicIdAndStatus(Long topicId, QuestionStatus status);
    List<Question> findByTopicIdAndStatusAndDifficulty(Long topicId, QuestionStatus status, Difficulty difficulty);
    List<Question> findByStatus(QuestionStatus status);
    List<Question> findByStatusAndCreatedByType(QuestionStatus status, CreatedByType createdByType);
    long countByTopicIdAndStatus(Long topicId, QuestionStatus status);

    @Query("select q from Question q where q.topic.module.course.id = :courseId and q.status = :status")
    List<Question> findByCourseIdAndStatus(@Param("courseId") Long courseId, @Param("status") QuestionStatus status);

    @Query("""
            select q.topic.id as topicId, count(q) as total from Question q
            where q.topic.module.course.id = :courseId and q.status = :status
            group by q.topic.id""")
    List<TopicStatsView.Count> countByCourseGroupedByTopic(@Param("courseId") Long courseId,
                                                           @Param("status") QuestionStatus status);
}
