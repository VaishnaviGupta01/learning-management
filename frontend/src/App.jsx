import { NavLink, Route, Routes } from 'react-router-dom';
import Home from './pages/Home.jsx';
import StudentDashboard from './pages/StudentDashboard.jsx';
import InstructorDashboard from './pages/InstructorDashboard.jsx';
import AdminDashboard from './pages/AdminDashboard.jsx';
import NotFound from './pages/NotFound.jsx';

const NAV = [
  { to: '/', label: 'Home', end: true },
  { to: '/student', label: 'Student' },
  { to: '/instructor', label: 'Instructor' },
  { to: '/admin', label: 'Admin' },
];

export default function App() {
  return (
    <div className="app">
      <header className="app-header">
        <span className="brand">LMS</span>
        <nav>
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end}>
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main className="app-main">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/student/*" element={<StudentDashboard />} />
          <Route path="/instructor/*" element={<InstructorDashboard />} />
          <Route path="/admin/*" element={<AdminDashboard />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
    </div>
  );
}
