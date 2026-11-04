import { useEffect, useState, type ReactNode, type SubmitEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api'
import { JobDocument } from '../components/JobDocument'
import {
  composeDescription,
  EMPLOYMENT_TYPES,
  emptyDraft,
  EXAMPLES,
  SENIORITY_LEVELS,
  validateDraft,
  type JobDraft,
} from '../jobText'
import { useToast } from '../toast'

export function NewJobPage() {
  const toast = useToast()
  const navigate = useNavigate()
  const [draft, setDraft] = useState<JobDraft>(emptyDraft)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const description = composeDescription(draft)

  useEffect(() => {
    document.title = 'New role · HireFlow'
  }, [])

  function update<K extends keyof JobDraft>(key: K, value: JobDraft[K]) {
    setDraft((current) => ({ ...current, [key]: value }))
    setErrors((current) => {
      if (!current[key]) return current
      const next = { ...current }
      delete next[key]
      return next
    })
  }

  async function onSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextErrors = validateDraft(draft)
    setErrors(nextErrors)
    const first = Object.keys(nextErrors)[0]
    if (first) {
      toast.error('This posting is missing sections a real job description needs.')
      document.getElementById(`field-${first}`)?.focus()
      return
    }
    setBusy(true)
    try {
      const job = await api.createJob(draft.title.trim(), description)
      toast.success('Role created. Upload CVs to start the pipeline.')
      navigate(`/jobs/${job.id}`)
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : 'The role could not be created.')
      setBusy(false)
    }
  }

  return (
    <div className="stack">
      <header className="page-head">
        <p className="kicker">New role</p>
        <h1>Write the posting, not a sentence.</h1>
        <p className="lede">
          Scoring uses this whole description. Fill every section the way you would on a careers page,
          or start from a complete example and edit it.
        </p>
        <div className="example-row">
          {EXAMPLES.map((example) => (
            <button
              key={example.id}
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                setDraft(example.draft)
                setErrors({})
                toast.info(`Loaded the ${example.draft.title.toLowerCase()} posting. Edit anything before you save.`)
              }}
            >
              {example.label}
            </button>
          ))}
        </div>
      </header>
      <form className="composer" onSubmit={(event) => void onSubmit(event)}>
        <div className="stack">
          <section className="card">
            <h2>Role</h2>
            <Field id="title" label="Title" error={errors.title}>
              <input id="field-title" value={draft.title} maxLength={200} onChange={(event) => update('title', event.target.value)} />
            </Field>
            <div className="split-fields">
              <Field id="location" label="Location" error={errors.location}>
                <input id="field-location" value={draft.location} onChange={(event) => update('location', event.target.value)} placeholder="Berlin, or remote within the EU" />
              </Field>
              <Field id="team" label="Team" error={errors.team}>
                <input id="field-team" value={draft.team} onChange={(event) => update('team', event.target.value)} placeholder="Logistics Platform" />
              </Field>
            </div>
            <div className="split-fields">
              <Field id="employmentType" label="Employment" error={errors.employmentType}>
                <select id="field-employmentType" value={draft.employmentType} onChange={(event) => update('employmentType', event.target.value)}>
                  <option value="">Select</option>
                  {EMPLOYMENT_TYPES.map((option) => <option key={option}>{option}</option>)}
                </select>
              </Field>
              <Field id="seniority" label="Seniority" error={errors.seniority}>
                <select id="field-seniority" value={draft.seniority} onChange={(event) => update('seniority', event.target.value)}>
                  <option value="">Select</option>
                  {SENIORITY_LEVELS.map((option) => <option key={option}>{option}</option>)}
                </select>
              </Field>
            </div>
          </section>
          <TextSection
            id="about"
            title="About the role"
            hint="A short paragraph about the team, the problem, and why the role exists."
            error={errors.about}
            value={draft.about}
            onChange={(value) => update('about', value)}
          />
          <TextSection
            id="responsibilities"
            title="Responsibilities"
            hint="One item per line. At least three."
            error={errors.responsibilities}
            value={draft.responsibilities}
            onChange={(value) => update('responsibilities', value)}
          />
          <TextSection
            id="requirements"
            title="Requirements"
            hint="What a candidate must already show. One item per line."
            error={errors.requirements}
            value={draft.requirements}
            onChange={(value) => update('requirements', value)}
          />
          <TextSection
            id="niceToHave"
            title="Nice to have"
            hint="Useful, not required."
            error={errors.niceToHave}
            value={draft.niceToHave}
            onChange={(value) => update('niceToHave', value)}
          />
          {errors.description ? <div id="field-description" className="banner" role="alert" tabIndex={-1}>{errors.description}</div> : null}
          <div className="form-actions">
            <button type="submit" className="btn" disabled={busy}>
              {busy ? 'Saving…' : 'Save role'}
            </button>
            <span className={description.length > 20000 ? 'char-count bad' : 'char-count'}>
              {description.length.toLocaleString()} / 20,000
            </span>
          </div>
        </div>
        <aside className="preview-sticky card">
          <p className="kicker">Preview</p>
          <h2>{draft.title.trim() || 'Untitled role'}</h2>
          <JobDocument description={description} />
        </aside>
      </form>
    </div>
  )
}

function Field({
  id,
  label,
  error,
  children,
}: {
  id: string
  label: string
  error?: string
  children: ReactNode
}) {
  return (
    <label className="field" htmlFor={`field-${id}`}>
      <span>{label}</span>
      {children}
      {error ? <small className="field-error" role="alert">{error}</small> : null}
    </label>
  )
}

function TextSection({
  id,
  title,
  hint,
  error,
  value,
  onChange,
}: {
  id: string
  title: string
  hint: string
  error?: string
  value: string
  onChange: (value: string) => void
}) {
  return (
    <section className="card">
      <h2>{title}</h2>
      <label className="field" htmlFor={`field-${id}`}>
        <span className="muted">{hint}</span>
        <textarea id={`field-${id}`} value={value} onChange={(event) => onChange(event.target.value)} />
        {error ? <small className="field-error" role="alert">{error}</small> : null}
      </label>
    </section>
  )
}
