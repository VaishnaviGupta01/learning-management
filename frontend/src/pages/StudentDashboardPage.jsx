import { useMemo } from 'react';
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import ProgressBar from '../components/ProgressBar.jsx';
import Section from '../components/Section.jsx';
import StatCard from '../components/StatCard.jsx';
import { useAuth } from '../auth/AuthContext.jsx';
import { useApi } from '../hooks/useApi.js';

const TZ = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
const WEAK = ['WEAK', 'NEEDS_PRACTICE'];

export default function StudentDashboardPage() {
  const { user } = useAuth();
  const progress = useApi('/api/students/me/progress');
  const knowledge = useApi('/api/students/me/topics/knowledge');
  const recs = useApi('/api/students/me/recommendations');
  const activity = useApi(`/api/students/me/activity?tz=${encodeURIComponent(TZ)}`);

  const topics = knowledge.data || [];
  const weak = topics.filter((t) => WEAK.includes(t.classification)).sort((a, b) => a.masteryScore - b.masteryScore);
  const strong = topics.filter((t) => t.classification === 'STRONG').sort((a, b) => b.masteryScore - a.masteryScore);
  const act = activity.data;
  // stable data: a new array on every render would restart the bar animation from its current position
  const chartData = useMemo(() => (act?.last14Days || []).map((d) => ({ ...d, day: d.date.slice(5) })), [act]);

  return (
    <div className="dashboard">
      <h1>Welcome back, {user.firstName}</h1>

      <div className="stat-row">
        <StatCard label="Current streak" value={act ? `${act.currentStreakDays} day${act.currentStreakDays === 1 ? '' : 's'}` : null}
                  hint={act && (act.studiedToday ? 'Studied today' : 'Study today to keep it going')} />
        <StatCard label="Study time (7 days)" value={act ? `${act.minutesLast7Days} min` : null}
                  hint={act && `Longest streak: ${act.longestStreakDays} days`} />
        <StatCard label="Courses" value={progress.data?.length} />
        <StatCard label="Strong topics" value={knowledge.data ? `${strong.length} / ${topics.length}` : null} />
      </div>

      <div className="grid-2">
        <Section title="Today's plan" loading={activity.loading || recs.loading} error={activity.error || recs.error}>
          <TodaysPlan due={act?.dueRevisions || []} recommendations={recs.data?.recommendations || []} />
        </Section>

        <Section title="Last 14 days" loading={activity.loading} error={activity.error}>
          <div className="chart-sm">
            <ResponsiveContainer width="100%" height={160}>
              <BarChart data={chartData}>
                <XAxis dataKey="day" fontSize={11} />
                <YAxis allowDecimals={false} fontSize={11} width={28} />
                <Tooltip formatter={(v) => [`${v} min`, 'Study time']} />
                <Bar dataKey="minutes" fill="#3ebd93" radius={[3, 3, 0, 0]} isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Section>
      </div>

      <Section title="Course progress" loading={progress.loading} error={progress.error}>
        {progress.data?.length === 0 && <p className="muted">Take a quiz in any course to start tracking progress.</p>}
        <ul className="plain-list">
          {progress.data?.map((c) => (
            <li key={c.courseId} className="course-progress">
              <div className="row-between">
                <strong>{c.courseTitle}</strong>
                <span>{Number(c.completionPercentage).toFixed(0)}%</span>
              </div>
              <ProgressBar value={c.completionPercentage} label={`${c.courseTitle} completion`} />
              <div className="muted small">
                {c.topicsCompleted}/{c.totalTopics} topics completed · {c.totalStudyMinutes} min studied
              </div>
            </li>
          ))}
        </ul>
      </Section>

      <div className="grid-2">
        <Section title="Needs work" loading={knowledge.loading} error={knowledge.error}>
          <TopicList topics={weak} empty="No weak topics right now." />
        </Section>
        <Section title="Strong" loading={knowledge.loading} error={knowledge.error}>
          <TopicList topics={strong} empty="Keep going — strong topics will show up here." />
        </Section>
      </div>

      <Section title="Recommended next" loading={recs.loading} error={recs.error}
               actions={recs.data?.stale && <span className="badge warn">offline ranking</span>}>
        <ol className="plain-list numbered">
          {recs.data?.recommendations.map((r) => (
            <li key={r.topicId}>
              <strong>{r.topicTitle}</strong> <span className={`badge band-${r.classification}`}>{label(r.classification)}</span>
              <div className="muted small">{r.courseTitle} · {r.reason}</div>
            </li>
          ))}
        </ol>
        {recs.data?.recommendations.length === 0 && <p className="muted">No recommendations yet.</p>}
        {recs.data?.blocked.length > 0 && (
          <>
            <h3>Locked</h3>
            <ul className="plain-list">
              {recs.data.blocked.map((b) => (
                <li key={b.topicId} className="muted">🔒 {b.topicTitle} — {b.message}</li>
              ))}
            </ul>
          </>
        )}
      </Section>
    </div>
  );
}

function TodaysPlan({ due, recommendations }) {
  const items = [
    ...due.map((d) => ({ key: `rev-${d.topicId}`, text: `Review ${d.topicTitle}`, note: d.overdue ? 'overdue' : 'due today' })),
    ...recommendations.slice(0, 3).map((r) => ({ key: `rec-${r.topicId}`, text: `Study ${r.topicTitle}`, note: r.reason })),
  ];
  if (items.length === 0) {
    return <p className="muted">Nothing scheduled — try an adaptive quiz to keep improving.</p>;
  }
  return (
    <ul className="plain-list checklist">
      {items.map((i) => (
        <li key={i.key}>
          <span>{i.text}</span> <span className="muted small">{i.note}</span>
        </li>
      ))}
    </ul>
  );
}

function TopicList({ topics, empty }) {
  if (topics.length === 0) return <p className="muted">{empty}</p>;
  return (
    <ul className="plain-list">
      {topics.map((t) => (
        <li key={t.topicId}>
          <div className="row-between">
            <span>{t.topicTitle}</span>
            <span className={`badge band-${t.classification}`}>{label(t.classification)} · {Math.round(t.masteryScore * 100)}%</span>
          </div>
          <ProgressBar value={t.masteryScore * 100} label={`${t.topicTitle} mastery`} />
        </li>
      ))}
    </ul>
  );
}

function label(classification) {
  return (classification || '').replace('_', ' ').toLowerCase();
}
