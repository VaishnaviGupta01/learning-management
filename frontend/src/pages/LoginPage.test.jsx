import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext.jsx';
import LoginPage from './LoginPage.jsx';

function renderLogin() {
  render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/student" element={<p>Student home</p>} />
          <Route path="/instructor" element={<p>Instructor home</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function mockFetch(status, body) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(body), { status }));
}

describe('LoginPage', () => {
  it('signs in, stores the token and routes to the dashboard for the role', async () => {
    const fetch = mockFetch(200, {
      token: 'jwt-123', tokenType: 'Bearer',
      user: { id: 7, email: 'sam@x.io', firstName: 'Sam', role: 'STUDENT' },
    });
    renderLogin();

    await userEvent.type(screen.getByLabelText('Email'), 'sam@x.io');
    await userEvent.type(screen.getByLabelText('Password'), 'Password123!');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Student home')).toBeInTheDocument();
    const [url, init] = fetch.mock.calls[0];
    expect(url).toMatch(/\/api\/auth\/login$/);
    expect(JSON.parse(init.body)).toEqual({ email: 'sam@x.io', password: 'Password123!' });
    expect(JSON.parse(localStorage.getItem('lms.auth')).token).toBe('jwt-123');
  });

  it('shows the server message on bad credentials and stays on the page', async () => {
    mockFetch(401, { status: 401, message: 'Invalid email or password' });
    renderLogin();

    await userEvent.type(screen.getByLabelText('Email'), 'sam@x.io');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-pass');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
    expect(localStorage.getItem('lms.auth')).toBeNull();
  });

  it('registers an instructor and shows validation messages from the API', async () => {
    const fetch = mockFetch(400, { message: 'Validation failed', fieldErrors: { password: 'size must be between 8 and 100' } });
    renderLogin();

    await userEvent.click(screen.getByRole('button', { name: /create an account/i }));
    await userEvent.type(screen.getByLabelText('First name'), 'Ina');
    await userEvent.type(screen.getByLabelText('Last name'), 'Structor');
    await userEvent.type(screen.getByLabelText('Email'), 'ina@x.io');
    await userEvent.type(screen.getByLabelText('Password'), 'short');
    await userEvent.selectOptions(screen.getByLabelText('I am a'), 'INSTRUCTOR');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('size must be between 8 and 100');
    const body = JSON.parse(fetch.mock.calls[0][1].body);
    expect(body).toMatchObject({ firstName: 'Ina', lastName: 'Structor', role: 'INSTRUCTOR' });
  });
});
