package com.lms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lms.entity.Question;
import com.lms.entity.QuestionAttempt;
import com.lms.entity.Quiz;
import com.lms.entity.QuizAttempt;
import com.lms.entity.RevisionSchedule;
import com.lms.entity.StudentTopicKnowledge;
import com.lms.entity.Topic;
import com.lms.entity.enums.QuizType;
import com.lms.entity.enums.RevisionStage;
import com.lms.entity.enums.TopicClassification;
import com.lms.ml.MlClient;
import com.lms.ml.MlClient.KnowledgeScore;
import com.lms.ml.MlClient.TopicEvidence;
import com.lms.ml.MlServiceException;
import com.lms.repository.QuestionAttemptRepository;
import com.lms.repository.RevisionScheduleRepository;
import com.lms.repository.StudentTopicKnowledgeRepository;
import com.lms.repository.TopicRepository;
import com.lms.repository.UserRepository;

/** Unit test with mocked repositories and ml client: how the four evidence ratios are derived. */
@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    private static final long STUDENT = 5L;
    private static final long TOPIC = 9L;

    @Mock QuestionAttemptRepository questionAttemptRepository;
    @Mock RevisionScheduleRepository revisionRepository;
    @Mock StudentTopicKnowledgeRepository knowledgeRepository;
    @Mock TopicRepository topicRepository;
    @Mock UserRepository userRepository;
    @Mock MlClient mlClient;
    @InjectMocks KnowledgeService service;

    @Captor ArgumentCaptor<List<TopicEvidence>> evidence;

    private final Topic topic = new Topic();
    private long ids = 1;

    @BeforeEach
    void setUp() {
        topic.setId(TOPIC);
    }

    @Test
    void derivesDiagnosticRecentPracticeAndRevisionRatios() {
        List<QuestionAttempt> newestFirst = new ArrayList<>();
        QuizAttempt latestDiagnostic = attempt(QuizType.DIAGNOSTIC);
        QuizAttempt olderDiagnostic = attempt(QuizType.DIAGNOSTIC);
        QuizAttempt assessment = attempt(QuizType.ASSESSMENT);
        QuizAttempt practice = attempt(QuizType.PRACTICE);
        newestFirst.add(answer(assessment, true));
        newestFirst.add(answer(assessment, false));
        newestFirst.add(answer(latestDiagnostic, true));
        newestFirst.add(answer(latestDiagnostic, true));
        newestFirst.add(answer(latestDiagnostic, false));
        newestFirst.add(answer(practice, false));
        newestFirst.add(answer(olderDiagnostic, false)); // ignored: only the latest diagnostic counts
        when(questionAttemptRepository.findSubmittedByStudentAndTopics(eq(STUDENT), anyCollection())).thenReturn(newestFirst);

        RevisionSchedule schedule = new RevisionSchedule();
        schedule.setStage(RevisionStage.DAY_7); // ordinal 2 of 5 -> 0.4
        when(revisionRepository.findByStudentIdAndTopicId(STUDENT, TOPIC)).thenReturn(Optional.of(schedule));
        when(knowledgeRepository.findByStudentIdAndTopicId(STUDENT, TOPIC)).thenReturn(Optional.empty());
        when(topicRepository.getReferenceById(TOPIC)).thenReturn(topic);
        when(mlClient.scoreKnowledge(any())).thenReturn(List.of(new KnowledgeScore(TOPIC, 0.61, "MODERATE")));

        service.recalculate(STUDENT, Set.of(TOPIC));

        verify(mlClient).scoreKnowledge(evidence.capture());
        TopicEvidence e = evidence.getValue().get(0);
        assertThat(e.diagnostic()).isEqualTo(2 / 3.0);
        assertThat(e.recentQuiz()).isEqualTo(0.5);
        assertThat(e.practice()).isEqualTo(0.0);
        assertThat(e.revision()).isEqualTo(0.4);

        ArgumentCaptor<StudentTopicKnowledge> saved = ArgumentCaptor.forClass(StudentTopicKnowledge.class);
        verify(knowledgeRepository).save(saved.capture());
        assertThat(saved.getValue().getMasteryScore()).isEqualTo(0.61);
        assertThat(saved.getValue().getClassification()).isEqualTo(TopicClassification.MODERATE);
        assertThat(saved.getValue().getAttemptsCount()).isEqualTo(7);
        assertThat(saved.getValue().getCorrectCount()).isEqualTo(3);
    }

    @Test
    void missingEvidenceIsSentAsNullAndMlOutageKeepsPreviousScore() {
        QuizAttempt practice = attempt(QuizType.PRACTICE);
        when(questionAttemptRepository.findSubmittedByStudentAndTopics(eq(STUDENT), anyCollection()))
                .thenReturn(List.of(answer(practice, true)));
        when(revisionRepository.findByStudentIdAndTopicId(anyLong(), anyLong())).thenReturn(Optional.empty());
        StudentTopicKnowledge existing = new StudentTopicKnowledge();
        existing.setMasteryScore(0.9);
        existing.setClassification(TopicClassification.STRONG);
        when(knowledgeRepository.findByStudentIdAndTopicId(STUDENT, TOPIC)).thenReturn(Optional.of(existing));
        when(mlClient.scoreKnowledge(any())).thenThrow(new MlServiceException("down", null));

        service.recalculate(STUDENT, Set.of(TOPIC));

        verify(mlClient).scoreKnowledge(evidence.capture());
        TopicEvidence e = evidence.getValue().get(0);
        assertThat(e.diagnostic()).isNull();
        assertThat(e.recentQuiz()).isNull();
        assertThat(e.practice()).isEqualTo(1.0);
        assertThat(e.revision()).isNull();

        verify(knowledgeRepository).save(existing);
        assertThat(existing.getMasteryScore()).isEqualTo(0.9);                 // unchanged
        assertThat(existing.getClassification()).isEqualTo(TopicClassification.STRONG);
        assertThat(existing.getAttemptsCount()).isEqualTo(1);                  // counts still updated
    }

    private QuizAttempt attempt(QuizType type) {
        Quiz quiz = new Quiz();
        quiz.setType(type);
        QuizAttempt a = new QuizAttempt();
        a.setId(ids++);
        a.setQuiz(quiz);
        a.setSubmittedAt(Instant.now());
        return a;
    }

    private QuestionAttempt answer(QuizAttempt attempt, boolean correct) {
        Question q = new Question();
        q.setTopic(topic);
        QuestionAttempt qa = new QuestionAttempt();
        qa.setQuizAttempt(attempt);
        qa.setQuestion(q);
        qa.setCorrect(correct);
        return qa;
    }
}
