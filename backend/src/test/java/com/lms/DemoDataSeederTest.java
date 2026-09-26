package com.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.JsonNode;

/** Runs the demo seeder against H2 (with the fake ml-service) and checks the dataset is complete and usable. */
@TestPropertySource(properties = "app.demo.enabled=true")
class DemoDataSeederTest extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void seedsThreeCoursesOver100QuestionsAndActiveStudents() throws Exception {
        assertThat(count("select count(*) from courses where code in ('DSA101','PY101','DB101')")).isEqualTo(3);
        assertThat(count("select count(*) from questions q join topics t on t.id = q.topic_id join course_modules m "
                + "on m.id = t.module_id join courses c on c.id = m.course_id where c.code in ('DSA101','PY101','DB101') "
                + "and q.status = 'APPROVED'")).isEqualTo(108);
        assertThat(count("select count(*) from users where email like 'student%@demo.lms'")).isEqualTo(15);
        assertThat(count("select count(*) from topic_prerequisites")).isGreaterThanOrEqualTo(17);
        assertThat(count("select count(distinct student_id) from quiz_attempts where submitted_at is not null")).isEqualTo(15);
        assertThat(count("select count(*) from student_topic_knowledge")).isGreaterThan(100);
        assertThat(count("select count(*) from recommendations")).isGreaterThan(0);
        // correct answers are not always the first option
        assertThat(count("select count(distinct order_index) from question_options where is_correct = true")).isGreaterThan(1);
        // attempts were moved into the past
        assertThat(count("select count(*) from quiz_attempts where submitted_at < current_timestamp - interval '7' day")).isGreaterThan(0);

        String student = post_("/api/auth/login", null, Map.of("email", "student01@demo.lms", "password", "Demo12345!"), 200)
                .get("token").asText();
        assertThat(get_("/api/students/me/progress", student, 200)).hasSizeGreaterThanOrEqualTo(2);
        assertThat(get_("/api/students/me/activity", student, 200).get("longestStreakDays").asInt()).isPositive();

        String instructor = post_("/api/auth/login", null, Map.of("email", "instructor1@demo.lms", "password", "Demo12345!"), 200)
                .get("token").asText();
        long dsa = count("select id from courses where code = 'DSA101'");
        JsonNode analytics = get_("/api/instructor/analytics?courseId=" + dsa, instructor, 200);
        assertThat(analytics.get("enrolledStudents").asInt()).isEqualTo(15);
        assertThat(analytics.at("/evaluation/studentsEvaluated").asInt()).isPositive();
        assertThat(analytics.at("/evaluation/precisionAtK").isNull()).isFalse();
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
