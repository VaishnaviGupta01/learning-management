import { useCallback, useEffect, useState } from 'react';
import { SERVICES } from '../config.js';

const REFRESH_MS = 15000;
const TIMEOUT_MS = 5000;

async function ping(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  const started = performance.now();
  try {
    const res = await fetch(`${url}/api/health`, { signal: controller.signal });
    const body = await res.json().catch(() => ({}));
    return {
      up: res.ok && body.status === 'UP',
      latency: Math.round(performance.now() - started),
      detail: body,
    };
  } catch {
    return { up: false, latency: null, detail: null };
  } finally {
    clearTimeout(timer);
  }
}

export default function SystemStatus() {
  const [statuses, setStatuses] = useState({});
  const [checkedAt, setCheckedAt] = useState(null);

  const refresh = useCallback(async () => {
    const results = await Promise.all(SERVICES.map((s) => ping(s.url)));
    setStatuses(Object.fromEntries(SERVICES.map((s, i) => [s.name, results[i]])));
    setCheckedAt(new Date());
  }, []);

  useEffect(() => {
    refresh();
    const id = setInterval(refresh, REFRESH_MS);
    return () => clearInterval(id);
  }, [refresh]);

  return (
    <section className="system-status">
      <div className="system-status-header">
        <h2>System status</h2>
        <button type="button" onClick={refresh}>Refresh</button>
      </div>
      <ul>
        {SERVICES.map((s) => {
          const st = statuses[s.name];
          const state = !st ? 'checking' : st.up ? 'up' : 'down';
          return (
            <li key={s.name} className={`status-${state}`}>
              <span className="dot" />
              <span className="name">{s.name}</span>
              <span className="url">{s.url}</span>
              <span className="state">
                {state === 'checking' && 'Checking…'}
                {state === 'up' && `UP · ${st.latency} ms`}
                {state === 'down' && 'DOWN'}
              </span>
              {st?.detail?.database && (
                <span className="extra">DB: {st.detail.database}</span>
              )}
            </li>
          );
        })}
      </ul>
      {checkedAt && <p className="checked-at">Last checked {checkedAt.toLocaleTimeString()}</p>}
    </section>
  );
}
