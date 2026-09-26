/** Card with a heading that also renders loading / error states for its data. */
export default function Section({ title, loading, error, children, actions }) {
  return (
    <section className="card">
      <div className="card-header">
        <h2>{title}</h2>
        {actions}
      </div>
      {loading && <p className="muted">Loading…</p>}
      {error && <p className="error" role="alert">{error.message}</p>}
      {!loading && !error && children}
    </section>
  );
}
