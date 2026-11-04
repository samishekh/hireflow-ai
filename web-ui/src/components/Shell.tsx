import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'

export function Shell() {
  const { pathname } = useLocation()
  const atHome = pathname === '/'
  return (
    <div className="app">
      <header className="topbar">
        <Link to="/" className="brand">
          <span className="mark" aria-hidden="true" />
          <span>
            <strong>HireFlow</strong>
            <small>Recruiter desk</small>
          </span>
        </Link>
        {atHome ? (
          <Link to="/jobs/new" className="btn">New role</Link>
        ) : (
          <Link to="/" className="btn btn-ghost">All roles</Link>
        )}
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  )
}

export function Subnav({ jobId, count }: { jobId: string; count: number | null }) {
  return (
    <nav className="subnav" aria-label="Role">
      <NavLink to={`/jobs/${jobId}`} end className={({ isActive }) => (isActive ? 'tab on' : 'tab')}>
        Pipeline{count == null ? '' : ` ${count}`}
      </NavLink>
      <NavLink to={`/jobs/${jobId}/rankings`} className={({ isActive }) => (isActive ? 'tab on' : 'tab')}>
        Rankings
      </NavLink>
      <NavLink to={`/jobs/${jobId}/compare`} className={({ isActive }) => (isActive ? 'tab on' : 'tab')}>
        Compare
      </NavLink>
    </nav>
  )
}
