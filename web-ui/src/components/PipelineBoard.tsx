import { useRef, type CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import { personLabel, PHASE_INDEX, PHASES, scoreText } from '../format'
import type { Ranking } from '../types'

export function PipelineBoard({
  jobId,
  rows,
  names,
}: {
  jobId: string
  rows: Ranking[]
  names: Record<string, string>
}) {
  const ordered = useOrdered(rows)
  const steps = useRef(new Map<string, number>())
  if (ordered.length === 0) {
    return (
      <p className="empty-copy">
        No CVs yet. Upload a batch and each file gets a row that moves from Pending through Parsing and Scoring to Done.
      </p>
    )
  }
  return (
    <div className="pipe-scroll">
      <ol className="pipe-list">
        {ordered.map((row) => {
          const failed = row.status === 'failed'
          if (!failed) steps.current.set(row.id, PHASE_INDEX[row.status] ?? 0)
          const step = steps.current.get(row.id) ?? 0
          const label = failed ? 'Failed' : (PHASES[step]?.label ?? 'Pending')
          const motion = { '--step': step } as CSSProperties
          const name = personLabel(row.original_filename, names[row.id])
          return (
            <li key={row.id} className={failed ? 'pipe-item is-failed' : 'pipe-item'}>
              <div className="pipe-top">
                <div>
                  <Link to={`/jobs/${jobId}/candidates/${row.id}`} className="pipe-name">
                    {name}
                  </Link>
                  <span>{row.original_filename}</span>
                </div>
                <div className="pipe-score num">{scoreText(row.score?.overall_score)}</div>
              </div>
              <div className="stepper" style={motion}>
                <span className={failed ? 'phase-marker bad' : 'phase-marker'}>{label}</span>
                <div className="track" aria-hidden="true">
                  <span className={failed ? 'track-fill bad' : 'track-fill'} />
                  {PHASES.map((phase, index) => (
                    <span key={phase.id} className={index <= step ? 'dot on' : 'dot'} />
                  ))}
                </div>
                <ol className="phase-names">
                  {PHASES.map((phase, index) => (
                    <li key={phase.id} className={!failed && index === step ? 'phase-on' : undefined}>
                      {phase.label}
                    </li>
                  ))}
                </ol>
              </div>
              {failed ? (
                <p className="row-error">{row.error_message?.trim() || 'The pipeline stopped on this CV.'}</p>
              ) : null}
            </li>
          )
        })}
      </ol>
    </div>
  )
}

function useOrdered(rows: Ranking[]): Ranking[] {
  const order = useRef<string[]>([])
  const present = new Set(rows.map((row) => row.id))
  for (const row of rows) {
    if (!order.current.includes(row.id)) order.current.push(row.id)
  }
  order.current = order.current.filter((id) => present.has(id))
  const byId = new Map(rows.map((row) => [row.id, row]))
  return order.current.map((id) => byId.get(id) as Ranking)
}
