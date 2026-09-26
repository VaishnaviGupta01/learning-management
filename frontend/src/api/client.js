import { BACKEND_URL } from '../config.js';

const STORAGE_KEY = 'lms.auth';

export class ApiError extends Error {
  constructor(status, message, fieldErrors) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors || {};
  }
}

export function loadAuth() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) || null;
  } catch {
    return null;
  }
}

export function saveAuth(auth) {
  if (auth) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
  } else {
    localStorage.removeItem(STORAGE_KEY);
  }
}

/**
 * Calls the backend with the stored bearer token. Throws ApiError with the backend's message on failure;
 * a 401 on an authenticated call clears the stored session.
 */
export async function api(path, { method = 'GET', body, form } = {}) {
  const auth = loadAuth();
  const headers = {};
  if (auth?.token) headers.Authorization = `Bearer ${auth.token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let res;
  try {
    res = await fetch(`${BACKEND_URL}${path}`, {
      method,
      headers,
      body: form ?? (body !== undefined ? JSON.stringify(body) : undefined),
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Is the backend running?');
  }

  const text = await res.text();
  const data = text ? safeJson(text) : null;
  if (!res.ok) {
    if (res.status === 401 && auth?.token) saveAuth(null);
    throw new ApiError(res.status, data?.message || `Request failed (${res.status})`, data?.fieldErrors);
  }
  return data;
}

function safeJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}
