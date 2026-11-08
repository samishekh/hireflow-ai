import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api'
import { formatWhen } from '../format'
import { excerpt } from '../jobText'
import { useToast } from '../toast'
import type { Job } from '../types'

export function JobsPage() {
  const toast = useToast()
  const [jobs, setJobs] = useState<Job[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    document.title = 'Roles · HireFlow'
  }, [])

  useEffect(() => {
    let cancel = false
    api.jobs()
      .then((next) => {
        if (!cancel) setJobs(next)
      })
      .catch((caught: unknown) => {
        if (cancel) return
        const message = caught instanceof ApiError ? caught.message : 'Could not load roles.'
        setError(message)
        toast.error(message)
      })
    return () => {
      cancel = true
    }
  }, [toast])

  return (
    <div className="stack">
      <section className="hero">
        <p className="kicker">Open roles</p>
        <p className="stat num">{jobs ? jobs.length : '—'}</p>
        <h1>Post a real job. Watch every CV move.</h1>
        <p>
          Write the role the way you would send it to a candidate, then drop in the PDFs.
          HireFlow parses and scores them while you watch.
        </p>
        <Link to="/jobs/new" className="btn btn-ink">Create a role</Link>
      </section>
      {error ? <div className="banner" role="alert">{error}</div> : null}
      {jobs == null && !error ? <div className="skeleton" role="status">Loading roles…</div> : null}
      {jobs && jobs.length === 0 ? (
        <section className="card empty-card">
          <h2>No roles yet</h2>
          <p>The first one should read like a posting, with responsibilities and requirements.</p>
        </section>
      ) : null}
      {jobs && jobs.length > 0 ? (
        <ul className="job-grid">
          {jobs.map((job) => (
            <li key={job.id}>
              <Link to={`/jobs/${job.id}`} className="job-card">
                <span className="kicker">{formatWhen(job.created_at)}</span>
                <h2>{job.title}</h2>
                <p>{excerpt(job.description)}</p>
                <span className="text-link">Open pipeline</span>
              </Link>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}
