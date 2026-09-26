import { Link } from 'react-router-dom';
import SystemStatus from '../components/SystemStatus.jsx';
import { HOME_BY_ROLE, useAuth } from '../auth/AuthContext.jsx';

export default function Home() {
  const { user } = useAuth();
  return (
    <div>
      <h1>Learning Management System</h1>
      <p>Adaptive learning with ML-driven recommendations and an AI study assistant.</p>
      <p>
        {user
          ? <Link className="button primary" to={HOME_BY_ROLE[user.role]}>Go to your dashboard</Link>
          : <Link className="button primary" to="/login">Sign in</Link>}
      </p>
      <SystemStatus />
    </div>
  );
}
