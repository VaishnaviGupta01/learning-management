import { useEffect, useMemo, useState } from 'react';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api/client.js';
import Section from '../components/Section.jsx';
import StatCard from '../components/StatCard.jsx';
import { useAuth } from '../auth/AuthContext.jsx';
import { useApi } from '../hooks/useApi.js';

const pct = (v) => (v === null || v === undefined ? '—' : `${Number(v).toFixed(1)}%`);
const ratio = (v) => (v === null || v === undefined ? '—' : Number(v).toFixed(2));

export default function InstructorDashboardPage() {
  const { user } = useAuth();
  const courses = useApi('/api/courses');
  const mine = (courses.data || []).filter((c) => user.role === 'ADMIN' || c.instructorId === user.id);
  const [courseId, setCourseId] = useState(null);

  useEffect(() => {
    if (!courseId && mine.length) setCourseId(mine[0].id);
  }, [mine, courseId]);

  const analytics = useApi(courseId ? `/api/instructor/analytics?courseId=${courseId}&k=5` : null);
  const a = analytics.data;
  const chartData = useMemo(() => (a?.topics || []).map((t) => (
    { ...t, accuracy: t.accuracyPercentage === null ? 0 : Number(t.accuracyPercentage) })), [a]);

  return (
    <div className="dashboard">
      <div className="row-between">
        <h1>Instructor dashboard</h1>
        {mine.length > 0 && (
          <label className="inline">
            Course{' '}
            <select value={courseId ?? ''} onChange={(e) => setCourseId(Number(e.target.value))}>
              {mine.map((c) => <option key={c.id} value={c.id}>{c.code} — {c.title}</option>)}
            </select>
          </label>
        )}
      </div>

      {courses.data && mine.length === 0 && <p className="muted">You have no courses yet. Create one through the API to get started.</p>}
      {analytics.error && <p className="error" role="alert">{analytics.error.message}</p>}

      {a && (
        <>
          <div className="stat-row">
            <StatCard label="Enrolled students" value={a.enrolledStudents} />
            <StatCard label="Avg completion" value={pct(a.averageCompletionPercentage)} />
            <StatCard label="Avg quiz score" value={pct(a.averageScorePercentage)} hint={`${a.submittedAttempts} attempts`} />
            <StatCard label="Questions to review" value={a.pendingQuestions} hint="AI-generated, pending" />
          </div>

          <Section title="Topic performance">
            {a.topics.length === 0 ? <p className="muted">No topics yet.</p> : (
              <div className="chart">
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="title" fontSize={12} interval={0} />
                    <YAxis yAxisId="left" domain={[0, 100]} unit="%" fontSize={12} />
                    <YAxis yAxisId="right" orientation="right" allowDecimals={false} fontSize={12} />
                    <Tooltip />
                    <Legend />
                    <Bar yAxisId="left" dataKey="accuracy" name="Accuracy %" fill="#3ebd93" radius={[3, 3, 0, 0]} isAnimationActive={false} />
                    <Bar yAxisId="right" dataKey="strugglingStudents" name="Struggling students" fill="#e12d39" radius={[3, 3, 0, 0]} isAnimationActive={false} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
            <div className="table-wrap">
              <table>
                <thead>
                  <tr><th>Topic</th><th>Questions</th><th>Answers</th><th>Accuracy</th><th>Students</th><th>Struggling</th><th>Avg mastery</th></tr>
                </thead>
                <tbody>
                  {a.topics.map((t) => (
                    <tr key={t.topicId}>
                      <td>{t.title}</td><td>{t.approvedQuestions}</td><td>{t.answers}</td><td>{pct(t.accuracyPercentage)}</td>
                      <td>{t.studentsAttempted}</td><td>{t.strugglingStudents}</td>
                      <td>{t.averageMastery === null ? '—' : `${Math.round(t.averageMastery * 100)}%`}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Section>

          <div className="grid-2">
            <Section title="Knowledge distribution">
              <ul className="plain-list">
                {Object.entries(a.knowledgeDistribution).map(([band, count]) => (
                  <li key={band} className="row-between"><span className={`badge band-${band}`}>{band.replace('_', ' ').toLowerCase()}</span><span>{count}</span></li>
                ))}
              </ul>
            </Section>
            <Section title={`Recommendation quality (k = ${a.evaluation.k})`}>
              <Evaluation e={a.evaluation} />
            </Section>
          </div>

          <CourseMaterial courseId={courseId} />
        </>
      )}
    </div>
  );
}

function Evaluation({ e }) {
  return (
    <>
      <div className="stat-row compact">
        <StatCard label="Precision@k" value={ratio(e.precisionAtK)} />
        <StatCard label="Recall@k" value={ratio(e.recallAtK)} />
        <StatCard label="Students evaluated" value={e.studentsEvaluated} />
      </div>
      <table>
        <thead><tr><th>Path</th><th>Attempts</th><th>Mean accuracy</th><th>Follow-ups</th><th>Mean gain</th></tr></thead>
        <tbody>
          {e.paths.map((p) => (
            <tr key={p.path}>
              <td>{p.path.toLowerCase()}</td><td>{p.attempts}</td>
              <td>{p.meanAccuracy === null ? '—' : `${Math.round(p.meanAccuracy * 100)}%`}</td>
              <td>{p.followUpPairs}</td>
              <td>{p.meanGain === null ? '—' : `${p.meanGain >= 0 ? '+' : ''}${Math.round(p.meanGain * 100)} pts`}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {!e.sufficientData && <p className="muted small">Not enough real attempt data yet for a reliable comparison.</p>}
    </>
  );
}

function CourseMaterial({ courseId }) {
  const docs = useApi(`/api/courses/${courseId}/documents`);
  const [file, setFile] = useState(null);
  const [status, setStatus] = useState(null);

  async function upload(e) {
    e.preventDefault();
    if (!file) return;
    const form = new FormData();
    form.append('file', file);
    form.append('courseId', courseId);
    setStatus({ busy: true, text: 'Processing… (extracting, chunking and embedding)' });
    try {
      const doc = await api('/api/documents/upload', { method: 'POST', form });
      setStatus({ text: `Indexed "${doc.title}" into ${doc.chunkCount} chunks.` });
      setFile(null);
      e.target.reset();
      docs.reload();
    } catch (err) {
      setStatus({ error: true, text: err.message });
    }
  }

  return (
    <Section title="Course material (AI tutor sources)" loading={docs.loading} error={docs.error}>
      <form className="upload-form" onSubmit={upload}>
        <input type="file" accept=".pdf,.txt,.md" aria-label="Course material file" onChange={(e) => setFile(e.target.files[0])} />
        <button type="submit" className="primary" disabled={!file || status?.busy}>Upload</button>
      </form>
      {status && <p className={status.error ? 'error' : 'muted'} role={status.error ? 'alert' : undefined}>{status.text}</p>}
      <ul className="plain-list">
        {docs.data?.map((d) => (
          <li key={d.id} className="row-between">
            <span>{d.title}</span><span className="muted small">{d.chunkCount} chunks · {Math.round((d.fileSize || 0) / 1024)} KB</span>
          </li>
        ))}
      </ul>
      {docs.data?.length === 0 && <p className="muted">No material uploaded yet — the tutor answers without course sources.</p>}
    </Section>
  );
}
