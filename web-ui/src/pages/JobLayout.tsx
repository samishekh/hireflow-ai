import { useEffect, useState } from 'react'
import { Link, Outlet, useParams } from 'react-router-dom'
import { api, ApiError } from '../api'
import { Subnav } from '../components/Shell'
import { IN_FLIGHT } from '../format'
import { LiveProvider, useLive } from '../live'
import { useToast } from '../toast'
import type { Job } from '../types'

export function JobLayout() {
  const { jobId } = useParams()
  if (!jobId) {
    return (
      <section className="card empty-card">
        <h1>That role is missing from the link.</h1>
        <Link to="/" className="btn">All roles</Link>
      </section>
    )
  }
  return <JobScreen jobId={jobId} />
}

function JobScreen({ jobId }: { jobId: string }) {
  const toast = useToast()
  const [job, setJob] = useState<Job | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    document.title = 'Role · HireFlow'
  }, [])

  useEffect(() => {
    let cancel = false
    api.job(jobId)
      .then((next) => {
        if (!cancel) setJob(next)
      })
      .catch((caught: unknown) => {
        if (cancel) return
        const message = caught instanceof ApiError ? caught.message : 'Could not load this role.'
        setError(message)
        toast.error(message)
      })
    return () => {
      cancel = true
    }
  }, [jobId, toast])

  if (error) {
    return (
      <section className="card empty-card">
        <p className="kicker">Role</p>
        <h1>This role could not be opened.</h1>
        <p>{error}</p>
        <Link to="/" className="btn">All roles</Link>
      </section>
    )
  }
  if (!job) return <div className="skeleton" role="status">Loading the role…</div>
  return (
    <LiveProvider jobId={jobId}>
      <JobFrame job={job} />
    </LiveProvider>
  )
}

function JobFrame({ job }: { job: Job }) {
  const { rows, error } = useLive()
  const inFlight = rows?.some((row) => IN_FLIGHT.has(row.status)) ?? false
  useEffect(() => {
    document.title = `${job.title} · HireFlow`
  }, [job.title])
  return (
    <div className="stack">
      <header className="role-head">
        <div>
          <p className="kicker">Role</p>
          <h1>{job.title}</h1>
        </div>
        {inFlight ? (
          <span className="pill pill-live"><span className="live-dot" aria-hidden="true" /> Live</span>
        ) : null}
      </header>
      <Subnav jobId={job.id} count={rows ? rows.length : null} />
      {error ? <div className="banner" role="alert">{error} Retrying.</div> : null}
      <Outlet context={{ job }} />
    </div>
  )
}
