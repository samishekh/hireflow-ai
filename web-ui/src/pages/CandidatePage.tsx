import { useEffect, useState, type SubmitEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '../api'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { ScoreDetails } from '../components/ScoreCard'
import { decisionLabel, formatWhen, statusLabel, yearsLabel } from '../format'
import { useLive } from '../live'
import { useToast } from '../toast'
import type { Candidate, Override, OverrideDecision } from '../types'

export function CandidatePage() {
  const { jobId, candidateId } = useParams()
  if (!jobId || !candidateId) return null
  return <CandidateScreen jobId={jobId} candidateId={candidateId} />
}

function CandidateScreen({ jobId, candidateId }: { jobId: string; candidateId: string }) {
  const toast = useToast()
  const navigate = useNavigate()
  const { rows, refresh } = useLive()
  const [candidate, setCandidate] = useState<Candidate | null>(null)
  const [leaving, setLeaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState<Override | null>(null)
  const liveOverride = rows?.find((row) => row.id === candidateId)?.override ?? null
  const override = newer(saved, liveOverride)

  useEffect(() => {
    if (leaving) return
    let cancel = false
    let timer = 0
    let announced = false
    const tick = async () => {
      try {
        const next = await api.candidate(candidateId)
        if (cancel) return
        setCandidate(next)
        setError(null)
        document.title = `${next.parsed_data?.candidate_name || next.original_filename} · HireFlow`
        if (next.status === 'done' || next.status === 'failed') return
        timer = window.setTimeout(() => void tick(), 600)
      } catch (caught) {
        if (cancel) return
        const message = caught instanceof ApiError ? caught.message : 'Could not load this candidate.'
        setError(message)
        if (!announced) {
          announced = true
          toast.error(message)
        }
        if (caught instanceof ApiError && caught.status === 404) return
        timer = window.setTimeout(() => void tick(), 2500)
      }
    }
    void tick()
    return () => {
      cancel = true
      window.clearTimeout(timer)
    }
  }, [candidateId, leaving, toast])

  if (error && !candidate) {
    return (
      <section className="card empty-card">
        <h1>This candidate could not be opened.</h1>
        <p>{error}</p>
        <Link to={`/jobs/${jobId}`} className="btn">Back to pipeline</Link>
      </section>
    )
  }
  if (!candidate) return <div className="skeleton" role="status">Loading the candidate…</div>

  const parsed = candidate.parsed_data
  return (
    <div className="stack">
      <header className="role-head">
        <div>
          <p className="kicker">{candidate.original_filename}</p>
          <h1>{parsed?.candidate_name?.trim() || candidate.original_filename}</h1>
          <p className="lede">{parsed ? yearsLabel(parsed.years_of_experience) : 'The CV is still being read.'}</p>
        </div>
        <div className="role-actions">
          <span className={candidate.status === 'failed' ? 'pill pill-bad' : 'pill'}>{statusLabel(candidate.status)}</span>
          <ConfirmDelete
            idle="Delete"
            prompt="Delete this candidate?"
            pending="Deleting…"
            success={`${parsed?.candidate_name?.trim() || candidate.original_filename} was deleted.`}
            onDelete={async () => {
              setLeaving(true)
              try {
                await api.deleteCandidate(candidate.id)
              } catch (error) {
                setLeaving(false)
                throw error
              }
              navigate(`/jobs/${jobId}`)
            }}
          />
        </div>
      </header>
      {candidate.status === 'failed' ? (
        <div className="banner" role="alert">{candidate.error_message?.trim() || 'The pipeline stopped on this CV.'}</div>
      ) : null}
      <div className="profile">
        <section className="card">
          <h2>Profile</h2>
          <p className="muted blind-note">
            Name, email, and phone stay in HireFlow. Scoring used a blind profile: skills, work history, education, and years of experience.
          </p>
          {parsed ? (
            <>
              <dl className="facts">
                <div><dt>Email</dt><dd>{parsed.email || 'Not found'}</dd></div>
                <div><dt>Phone</dt><dd>{parsed.phone || 'Not found'}</dd></div>
              </dl>
              <div className="chips">
                {(parsed.skills ?? []).map((skill, index) => (
                  <span key={`${skill}-${index}`} className="chip">{skill}</span>
                ))}
              </div>
              <h3>Work history</h3>
              {parsed.work_history?.length ? (
                <ol className="timeline">
                  {parsed.work_history.map((job, index) => (
                    <li key={`${job.company}-${job.role}-${index}`}>
                      <strong>{job.role}</strong>
                      <span>{job.company} · {job.duration}</span>
                      {job.description ? <p>{job.description}</p> : null}
                    </li>
                  ))}
                </ol>
              ) : <p className="muted">No work history was extracted.</p>}
              <h3>Education</h3>
              {parsed.education?.length ? (
                <ol className="timeline">
                  {parsed.education.map((item, index) => (
                    <li key={`${item.institution}-${index}`}>
                      <strong>{[item.degree, item.field_of_study].filter(Boolean).join(', ')}</strong>
                      <span>{item.institution} · {item.duration}</span>
                    </li>
                  ))}
                </ol>
              ) : <p className="muted">No education was extracted.</p>}
            </>
          ) : (
            <p className="muted">Parsed sections show up when parsing finishes. Status is {candidate.status}.</p>
          )}
          {candidate.raw_text ? (
            <details className="raw">
              <summary>Extracted text</summary>
              <pre>{candidate.raw_text}</pre>
            </details>
          ) : null}
        </section>
        <div className="stack">
          {candidate.score ? <section className="card"><ScoreDetails score={candidate.score} /></section> : (
            <section className="card empty-card">
              <h2>No score yet</h2>
              <p>The breakdown appears after scoring. This CV is {candidate.status}.</p>
            </section>
          )}
          <OverridePanel
            candidate={candidate}
            override={override}
            onSaved={async (next) => {
              setSaved(next)
              try {
                await refresh()
              } catch (caught) {
                toast.error(caught instanceof ApiError ? caught.message : 'The decision was saved, but the list did not refresh.')
              }
            }}
          />
        </div>
      </div>
    </div>
  )
}

function newer(left: Override | null, right: Override | null): Override | null {
  if (!left) return right
  if (!right) return left
  return left.created_at >= right.created_at ? left : right
}

function OverridePanel({
  candidate,
  override,
  onSaved,
}: {
  candidate: Candidate
  override: Override | null
  onSaved: (override: Override) => Promise<void>
}) {
  const toast = useToast()
  const [decision, setDecision] = useState<OverrideDecision | ''>('')
  const [note, setNote] = useState('')
  const [decidedBy, setDecidedBy] = useState('recruiter')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (decision !== 'accepted' && decision !== 'overridden') {
      setError('Choose accepted or overridden.')
      return
    }
    if (!note.trim()) {
      setError('Write why you made this decision.')
      return
    }
    if (note.trim().length > 4000) {
      setError('Keep the note under 4,000 characters.')
      return
    }
    if (!decidedBy.trim()) {
      setError('Say who decided.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const saved = await api.override(candidate.id, decision, note.trim(), decidedBy.trim())
      toast.success('Decision recorded. The weighted score is unchanged.')
      setNote('')
      setDecision('')
      await onSaved(saved)
    } catch (caught) {
      const message = caught instanceof ApiError ? caught.message : 'The decision could not be saved.'
      setError(message)
      toast.error(message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <h2>Recruiter decision</h2>
      <p className="muted">This sits beside the model score. It does not change it.</p>
      {override ? (
        <div className={override.decision === 'accepted' ? 'decision-banner ok' : 'decision-banner'}>
          <strong>{decisionLabel(override.decision)}</strong>
          <span>by {override.decided_by} · {formatWhen(override.created_at)}</span>
          <p>{override.note}</p>
        </div>
      ) : null}
      {candidate.status !== 'done' ? (
        <p className="muted">A decision opens when this CV reaches Done. It is {candidate.status}.</p>
      ) : (
        <form className="stack" onSubmit={(event) => void onSubmit(event)}>
          <div className="choice-row">
            <button
              type="button"
              className={decision === 'accepted' ? 'choice on' : 'choice'}
              onClick={() => setDecision('accepted')}
            >
              Accepted
            </button>
            <button
              type="button"
              className={decision === 'overridden' ? 'choice on warn' : 'choice'}
              onClick={() => setDecision('overridden')}
            >
              Overridden
            </button>
          </div>
          <label className="field">
            <span>Note</span>
            <textarea value={note} onChange={(event) => setNote(event.target.value)} placeholder="Why this person should move forward, or why the score should not decide it." />
          </label>
          <label className="field">
            <span>Decided by</span>
            <input value={decidedBy} maxLength={200} onChange={(event) => setDecidedBy(event.target.value)} />
          </label>
          {error ? <p className="field-error" role="alert">{error}</p> : null}
          <button type="submit" className="btn" disabled={busy}>{busy ? 'Saving…' : 'Record decision'}</button>
        </form>
      )}
    </section>
  )
}
