package com.lms.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;

/**
 * Join between quizzes and questions with ordering and weight.
 */
@Entity
@Table(name = "quiz_questions", uniqueConstraints = @UniqueConstraint(columnNames = {"quiz_id", "question_id"}))
public class QuizQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @Column(name = "points", nullable = false, precision = 6, scale = 2)
    private BigDecimal points = BigDecimal.ONE;

    public Quiz getQuiz() {
        return quiz;
    }

    public void setQuiz(Quiz quiz) {
        this.quiz = quiz;
    }

    public Question getQuestion() {
        return question;
    }

    public void setQuestion(Question question) {
        this.question = question;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public BigDecimal getPoints() {
        return points;
    }

    public void setPoints(BigDecimal points) {
        this.points = points;
    }
}
