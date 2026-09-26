import SystemStatus from '../components/SystemStatus.jsx';
import StatCard from '../components/StatCard.jsx';
import { useApi } from '../hooks/useApi.js';

export default function AdminDashboardPage() {
  const { data: s, error, loading } = useApi('/api/admin/stats');

  return (
    <div className="dashboard">
      <h1>Admin dashboard</h1>
      {loading && <p className="muted">Loading…</p>}
      {error && <p className="error" role="alert">{error.message}</p>}
      {s && (
        <div className="stat-grid">
          <StatCard label="Users" value={s.totalUsers}
                    hint={`${s.usersByRole.STUDENT} students · ${s.usersByRole.INSTRUCTOR} instructors · ${s.usersByRole.ADMIN} admins`} />
          <StatCard label="Active students (7 days)" value={s.activeStudentsLast7Days} />
          <StatCard label="Courses" value={s.courses} hint={`${s.publishedCourses} published`} />
          <StatCard label="Approved questions" value={s.approvedQuestions} hint={`${s.pendingQuestions} awaiting review`} />
          <StatCard label="Quizzes" value={s.quizzes} />
          <StatCard label="Quiz attempts" value={s.submittedAttempts} hint={`${s.attemptsLast7Days} in the last 7 days`} />
          <StatCard label="AI tutor exchanges" value={s.aiInteractions} hint={`${s.aiInteractionsLast7Days} in the last 7 days`} />
          <StatCard label="Course documents" value={s.documents} hint={`${s.documentChunks} indexed chunks`} />
        </div>
      )}
      <SystemStatus />
    </div>
  );
}
