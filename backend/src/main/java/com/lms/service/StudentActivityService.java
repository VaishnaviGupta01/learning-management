package com.lms.service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.dto.student.ActivityResponse;
import com.lms.dto.student.ActivityResponse.Day;
import com.lms.dto.student.ActivityResponse.DueRevision;
import com.lms.entity.RevisionSchedule;
import com.lms.entity.StudySession;
import com.lms.exception.BadRequestException;
import com.lms.repository.RevisionScheduleRepository;
import com.lms.repository.StudySessionRepository;

@Service
@Transactional(readOnly = true)
public class StudentActivityService {

    private final StudySessionRepository sessionRepository;
    private final RevisionScheduleRepository revisionRepository;

    public StudentActivityService(StudySessionRepository sessionRepository, RevisionScheduleRepository revisionRepository) {
        this.sessionRepository = sessionRepository;
        this.revisionRepository = revisionRepository;
    }

    public ActivityResponse activity(Long studentId, String timeZone) {
        ZoneId zone = zone(timeZone);
        Instant now = Instant.now();
        LocalDate today = LocalDate.ofInstant(now, zone);

        Map<LocalDate, int[]> byDay = new TreeMap<>(); // date -> {minutes, sessions}
        for (StudySession s : sessionRepository.findByStudentIdOrderByStartedAtDesc(studentId)) {
            int[] day = byDay.computeIfAbsent(LocalDate.ofInstant(s.getStartedAt(), zone), d -> new int[2]);
            day[0] += s.getDurationMinutes() == null ? 0 : s.getDurationMinutes();
            day[1]++;
        }

        List<Day> last14 = new ArrayList<>();
        int minutes7 = 0;
        for (int i = 13; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            int[] d = byDay.getOrDefault(date, new int[2]);
            last14.add(new Day(date, d[0], d[1]));
            if (i < 7) {
                minutes7 += d[0];
            }
        }

        Instant endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant();
        List<DueRevision> due = revisionRepository.findByStudentIdAndActiveTrueAndNextReviewAtBefore(studentId, endOfToday)
                .stream()
                .map(r -> dueRevision(r, now))
                .toList();

        return new ActivityResponse(zone.getId(), currentStreak(byDay.keySet(), today), longestStreak(byDay.keySet()),
                byDay.containsKey(today), byDay.getOrDefault(today, new int[2])[0], minutes7, last14, due);
    }

    static int currentStreak(java.util.Set<LocalDate> days, LocalDate today) {
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    static int longestStreak(java.util.Set<LocalDate> days) {
        int best = 0;
        for (LocalDate d : days) {
            if (days.contains(d.minusDays(1))) {
                continue; // not the start of a run
            }
            int run = 0;
            while (days.contains(d.plusDays(run))) {
                run++;
            }
            best = Math.max(best, run);
        }
        return best;
    }

    private static DueRevision dueRevision(RevisionSchedule r, Instant now) {
        return new DueRevision(r.getTopic().getId(), r.getTopic().getTitle(), r.getTopic().getModule().getCourse().getId(),
                r.getStage(), r.getNextReviewAt(), r.getNextReviewAt().isBefore(now));
    }

    private static ZoneId zone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            throw new BadRequestException("Unknown time zone: " + timeZone);
        }
    }
}
