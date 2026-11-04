import { scoreText } from '../format'
import type { Score } from '../types'

export function ScoreBars({ score }: { score: Score }) {
  const rows = [
    ['Skills', score.skills_score, '50%'],
    ['Experience', score.experience_score, '35%'],
    ['Education', score.education_score, '15%'],
  ] as const
  return (
    <div className="bars">
      {rows.map(([label, value, weight]) => (
        <div key={label} className="bar-row">
          <div className="bar-label">
            <span>{label}</span>
            <span>{weight}</span>
          </div>
          <div className="bar-track" aria-hidden="true">
            <span className="bar-fill" style={{ width: `${clamp(value)}%` }} />
          </div>
          <strong className="num">{scoreText(value)}</strong>
        </div>
      ))}
    </div>
  )
}

function clamp(value: number): number {
  if (Number.isNaN(value)) return 0
  return Math.max(0, Math.min(100, value))
}

export function SkillChips({ score }: { score: Score }) {
  const matched = score.matched_skills ?? []
  const missing = score.missing_skills ?? []
  if (matched.length === 0 && missing.length === 0) return null
  return (
    <div className="chip-groups">
      {matched.length > 0 ? <ChipRow label="Matched" items={matched} className="chip" /> : null}
      {missing.length > 0 ? <ChipRow label="Missing" items={missing} className="chip chip-missing" /> : null}
    </div>
  )
}

function ChipRow({ label, items, className }: { label: string; items: string[]; className: string }) {
  const shown = items.slice(0, 8)
  const rest = items.length - shown.length
  return (
    <div className="chip-line">
      <span className="chip-label">{label}</span>
      <div className="chips">
        {shown.map((item, index) => (
          <span key={`${item}-${index}`} className={className}>{item}</span>
        ))}
        {rest > 0 ? <span className="chip">+{rest}</span> : null}
      </div>
    </div>
  )
}

export function ScoreDetails({ score }: { score: Score }) {
  return (
    <div className="score-card">
      <div className="score-hero">
        <span className="num">{scoreText(score.overall_score)}</span>
        <small>Weighted score</small>
      </div>
      <ScoreBars score={score} />
      <p className="formula">{formulaCopy(score.formula_version)}</p>
      {score.summary ? <p className="prose">{score.summary}</p> : null}
      <SkillChips score={score} />
      <Evidence title="Strengths" items={score.strengths ?? []} />
      <Evidence title="Concerns" items={score.concerns ?? []} />
      <p className="muted model-line">Model {score.model}</p>
    </div>
  )
}

function formulaCopy(version: string): string {
  if (version === 'v1') {
    return 'Computed in code with formula v1: 50% skills, 35% experience, 15% education. A recruiter decision never overwrites this number.'
  }
  return `Computed in code with formula ${version}. A recruiter decision never overwrites this number.`
}

function Evidence({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) return null
  return (
    <section className="evidence">
      <h3>{title}</h3>
      <ul>
        {items.map((item, index) => (
          <li key={`${item}-${index}`}>{item}</li>
        ))}
      </ul>
    </section>
  )
}
