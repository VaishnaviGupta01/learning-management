import { Navigate, useLocation } from 'react-router-dom';
import { HOME_BY_ROLE, useAuth } from './AuthContext.jsx';

/** Sends anonymous users to /login and users of another role to their own dashboard. */
export default function RequireRole({ roles, children }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (!roles.includes(user.role)) {
    return <Navigate to={HOME_BY_ROLE[user.role] || '/'} replace />;
  }
  return children;
}
