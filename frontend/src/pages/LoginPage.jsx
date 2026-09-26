import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { HOME_BY_ROLE, useAuth } from '../auth/AuthContext.jsx';

const EMPTY = { email: '', password: '', firstName: '', lastName: '', role: 'STUDENT' };

export default function LoginPage() {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  async function onSubmit(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const user = mode === 'login' ? await login(form.email, form.password) : await register(form);
      const from = location.state?.from;
      navigate(from && from !== '/login' ? from : HOME_BY_ROLE[user.role] || '/', { replace: true });
    } catch (err) {
      const fields = Object.values(err.fieldErrors || {});
      setError(fields.length ? fields.join('. ') : err.message);
    } finally {
      setSubmitting(false);
    }
  }

  const registering = mode === 'register';
  return (
    <div className="auth-page">
      <form className="card auth-card" onSubmit={onSubmit} noValidate>
        <h1>{registering ? 'Create an account' : 'Sign in'}</h1>

        {registering && (
          <div className="form-row">
            <label>
              First name
              <input value={form.firstName} onChange={set('firstName')} required autoComplete="given-name" />
            </label>
            <label>
              Last name
              <input value={form.lastName} onChange={set('lastName')} required autoComplete="family-name" />
            </label>
          </div>
        )}

        <label>
          Email
          <input type="email" value={form.email} onChange={set('email')} required autoComplete="email" />
        </label>
        <label>
          Password
          <input type="password" value={form.password} onChange={set('password')} required minLength={8}
                 autoComplete={registering ? 'new-password' : 'current-password'} />
        </label>

        {registering && (
          <label>
            I am a
            <select value={form.role} onChange={set('role')}>
              <option value="STUDENT">Student</option>
              <option value="INSTRUCTOR">Instructor</option>
            </select>
          </label>
        )}

        {error && <p className="error" role="alert">{error}</p>}

        <button type="submit" className="primary" disabled={submitting}>
          {submitting ? 'Please wait…' : registering ? 'Create account' : 'Sign in'}
        </button>
        <button type="button" className="link" onClick={() => { setMode(registering ? 'login' : 'register'); setError(null); }}>
          {registering ? 'Already have an account? Sign in' : 'New here? Create an account'}
        </button>
      </form>
    </div>
  );
}
