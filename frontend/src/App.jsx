import { lazy, Suspense } from 'react';
import { NavLink, Route, Routes, useNavigate } from 'react-router-dom';
import { HOME_BY_ROLE, useAuth } from './auth/AuthContext.jsx';
import RequireRole from './auth/RequireRole.jsx';
import Home from './pages/Home.jsx';
import LoginPage from './pages/LoginPage.jsx';
import NotFound from './pages/NotFound.jsx';

// Dashboards (and Recharts with them) load on demand, keeping the first page small.
const StudentDashboardPage = lazy(() => import('./pages/StudentDashboardPage.jsx'));
const InstructorDashboardPage = lazy(() => import('./pages/InstructorDashboardPage.jsx'));
const AdminDashboardPage = lazy(() => import('./pages/AdminDashboardPage.jsx'));

export default function App() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="app">
      <header className="app-header">
        <span className="brand">LMS</span>
        <nav>
          <NavLink to="/" end>Home</NavLink>
          {user && <NavLink to={HOME_BY_ROLE[user.role]}>Dashboard</NavLink>}
          {user?.role === 'ADMIN' && <NavLink to="/instructor">Courses</NavLink>}
        </nav>
        <div className="header-user">
          {user ? (
            <>
              <span>{user.firstName} · {user.role.toLowerCase()}</span>
              <button type="button" className="link light" onClick={() => { logout(); navigate('/login'); }}>Sign out</button>
            </>
          ) : (
            <NavLink to="/login">Sign in</NavLink>
          )}
        </div>
      </header>
      <main className="app-main">
        <Suspense fallback={<p className="muted">Loading…</p>}>
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/student/*" element={<RequireRole roles={['STUDENT']}><StudentDashboardPage /></RequireRole>} />
            <Route path="/instructor/*" element={<RequireRole roles={['INSTRUCTOR', 'ADMIN']}><InstructorDashboardPage /></RequireRole>} />
            <Route path="/admin/*" element={<RequireRole roles={['ADMIN']}><AdminDashboardPage /></RequireRole>} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </Suspense>
      </main>
    </div>
  );
}
