import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import AdminDashboardPage from './AdminDashboardPage.jsx';

const STATS = {
  totalUsers: 18, usersByRole: { STUDENT: 15, INSTRUCTOR: 2, ADMIN: 1 }, activeStudentsLast7Days: 11,
  courses: 3, publishedCourses: 3, approvedQuestions: 108, pendingQuestions: 4, quizzes: 40,
  submittedAttempts: 420, attemptsLast7Days: 95, aiInteractions: 12, aiInteractionsLast7Days: 5,
  documents: 2, documentChunks: 37,
};

describe('AdminDashboardPage', () => {
  it('renders a stat card per metric from /api/admin/stats with the stored token', async () => {
    localStorage.setItem('lms.auth', JSON.stringify({ token: 'admin-jwt', user: { role: 'ADMIN' } }));
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) =>
      String(url).endsWith('/api/admin/stats')
        ? new Response(JSON.stringify(STATS), { status: 200 })
        : new Response(JSON.stringify({ status: 'UP' }), { status: 200 }));

    render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>);

    const users = (await screen.findByText('Users')).closest('.stat-card');
    expect(within(users).getByText('18')).toBeInTheDocument();
    expect(within(users).getByText('15 students · 2 instructors · 1 admins')).toBeInTheDocument();
    const questions = screen.getByText('Approved questions').closest('.stat-card');
    expect(within(questions).getByText('108')).toBeInTheDocument();
    expect(within(questions).getByText('4 awaiting review')).toBeInTheDocument();

    const statsCall = fetch.mock.calls.find(([url]) => String(url).endsWith('/api/admin/stats'));
    expect(statsCall[1].headers.Authorization).toBe('Bearer admin-jwt');
  });

  it('shows the API error instead of cards', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) =>
      String(url).endsWith('/api/admin/stats')
        ? new Response(JSON.stringify({ message: 'You do not have permission to perform this action' }), { status: 403 })
        : new Response('{}', { status: 200 }));

    render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>);

    expect(await screen.findByText('You do not have permission to perform this action')).toBeInTheDocument();
    expect(screen.queryByText('Users')).not.toBeInTheDocument();
  });
});
