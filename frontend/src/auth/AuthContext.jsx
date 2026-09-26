import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { api, loadAuth, saveAuth } from '../api/client.js';

const AuthContext = createContext(null);

export const HOME_BY_ROLE = { STUDENT: '/student', INSTRUCTOR: '/instructor', ADMIN: '/admin' };

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState(loadAuth);

  const store = useCallback((response) => {
    const next = { token: response.token, user: response.user };
    saveAuth(next);
    setAuth(next);
    return next.user;
  }, []);

  const login = useCallback(
    async (email, password) => store(await api('/api/auth/login', { method: 'POST', body: { email, password } })),
    [store],
  );

  const register = useCallback(
    async (fields) => store(await api('/api/auth/register', { method: 'POST', body: fields })),
    [store],
  );

  const logout = useCallback(() => {
    saveAuth(null);
    setAuth(null);
  }, []);

  const value = useMemo(() => ({ user: auth?.user ?? null, login, register, logout }), [auth, login, register, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
