import { useEffect, useState, type SubmitEvent } from 'react'
import { api, ApiError } from '../api'
import { useSetFormula } from '../formula'
import { formatHundredths, formatWeight, weightDraftError, weightHundredths } from '../formulaWeights'
import { useToast } from '../toast'
import type { Formula } from '../types'

const V1 = { skills: '50', experience: '35', education: '15' }

export function FormulaPage() {
  const toast = useToast()
  const setFormula = useSetFormula()
  const [current, setCurrent] = useState<Formula | null>(null)
  const [skills, setSkills] = useState('50')
  const [experience, setExperience] = useState('35')
  const [education, setEducation] = useState('15')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    document.title = 'Formula · HireFlow'
  }, [])

  useEffect(() => {
    let cancel = false
    api.formula()
      .then((formula) => {
        if (cancel) return
        setCurrent(formula)
        setFormula(formula)
        setSkills(formatWeight(formula.skills_weight))
        setExperience(formatWeight(formula.experience_weight))
        setEducation(formatWeight(formula.education_weight))
      })
      .catch((caught: unknown) => {
        if (cancel) return
        const message = caught instanceof ApiError ? caught.message : 'Could not load the scoring formula.'
        setError(message)
        toast.error(message)
      })
    return () => {
      cancel = true
    }
  }, [setFormula, toast])

  const draftError = weightDraftError({ skills, experience, education })
  const total = [skills, experience, education]
    .map(weightHundredths)
    .reduce<number | null>((sum, part) => (sum == null || part == null ? null : sum + part), 0)
  const unchanged = current != null
    && weightHundredths(skills) === Math.round(current.skills_weight * 100)
    && weightHundredths(experience) === Math.round(current.experience_weight * 100)
    && weightHundredths(education) === Math.round(current.education_weight * 100)

  async function onSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (draftError) {
      toast.error(draftError)
      return
    }
    setBusy(true)
    try {
      const next = await api.updateFormula(skills.trim(), experience.trim(), education.trim())
      setCurrent(next)
      setFormula(next)
      const count = next.rescored_candidates
      toast.success(
        count === 0
          ? 'Formula saved. No scored candidates needed a new total.'
          : `Formula saved. ${count} ${count === 1 ? 'candidate was' : 'candidates were'} rescored.`,
      )
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : 'The formula could not be saved.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="stack">
      <header className="page-head">
        <p className="kicker">Scoring formula</p>
        <h1>One formula for every role.</h1>
        <p className="lede">
          Skills, experience, and education stay as the model scored them. Applying a new formula
          recomputes every stored overall, and the next upload uses only these weights.
        </p>
      </header>
      {error && !current ? <div className="banner" role="alert">{error}</div> : null}
      <form className="card formula-form" onSubmit={(event) => void onSubmit(event)}>
        <div className="weight-grid">
          <WeightField id="skills" label="Skills" value={skills} onChange={setSkills} />
          <WeightField id="experience" label="Experience" value={experience} onChange={setExperience} />
          <WeightField id="education" label="Education" value={education} onChange={setEducation} />
        </div>
        <p className={draftError ? 'weight-total bad' : 'weight-total'}>
          Total {total == null ? '—' : formatHundredths(total)} / 100
          {current ? <span className="muted"> · active {current.formula_version}</span> : null}
        </p>
        {draftError ? <p className="field-error" role="alert">{draftError}</p> : null}
        <div className="form-actions">
          <button type="submit" className="btn" disabled={busy || draftError != null || unchanged || current == null}>
            {busy ? 'Applying…' : 'Apply formula'}
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => {
              setSkills(V1.skills)
              setExperience(V1.experience)
              setEducation(V1.education)
            }}
          >
            Fill v1
          </button>
        </div>
      </form>
    </div>
  )
}

function WeightField({
  id,
  label,
  value,
  onChange,
}: {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
}) {
  return (
    <label className="field" htmlFor={`field-${id}`}>
      <span>{label}</span>
      <input
        id={`field-${id}`}
        inputMode="decimal"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  )
}
