import { Link, useParams } from 'react-router-dom'
import { api } from '../api'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { ScoreBars, SkillChips } from '../components/ScoreCard'
import { decisionLabel, personLabel, scoreText, statusLabel } from '../format'
import { useLive } from '../live'

export function RankingsPage() {
  const { jobId } = useParams()
  const { rows, names, refresh } = useLive()
  if (!jobId) return null
  if (rows == null) return <div className="skeleton" role="status">Loading rankings…</div>
  if (rows.length === 0) {
    return (
      <section className="card empty-card">
        <h2>No one to rank yet</h2>
        <p>Upload CVs on the pipeline. Scores appear here as soon as a CV finishes scoring.</p>
        <Link to={`/jobs/${jobId}`} className="btn">Back to pipeline</Link>
      </section>
    )
  }
  return (
    <ol className="rank-list">
      {rows.map((row, index) => {
        const name = personLabel(row.original_filename, names[row.id])
        return (
        <li key={row.id}>
          <article className="rank-card">
            <div className="score-hero">
              <span className="num">{scoreText(row.score?.overall_score)}</span>
              <small>#{index + 1} by score</small>
            </div>
            <div className="rank-body">
              <div className="rank-title">
                <h2>
                  <Link to={`/jobs/${jobId}/candidates/${row.id}`}>
                    {name}
                  </Link>
                </h2>
                <span className="muted">{row.original_filename}</span>
              </div>
              <ConfirmDelete
                idle="Delete"
                prompt="Delete this candidate?"
                pending="Deleting…"
                success={`${name} was deleted.`}
                onDelete={async () => {
                  await api.deleteCandidate(row.id)
                  try {
                    await refresh()
                  } catch {
                    // The pipeline poll reloads the list.
                  }
                }}
              />
              <p className="status-line">
                <span className={row.status === 'failed' ? 'pill pill-bad' : 'pill'}>{statusLabel(row.status)}</span>
                {row.override ? (
                  <span className={row.override.decision === 'accepted' ? 'pill pill-ok' : 'pill pill-bad'}>
                    {decisionLabel(row.override.decision)}
                  </span>
                ) : null}
              </p>
              {row.status === 'failed' ? (
                <p className="row-error">{row.error_message?.trim() || 'The pipeline stopped on this CV.'}</p>
              ) : null}
              {row.score ? (
                <>
                  {row.score.summary ? <p className="prose">{row.score.summary}</p> : null}
                  <ScoreBars score={row.score} />
                  <SkillChips score={row.score} />
                </>
              ) : (
                <p className="muted">The weighted score appears when scoring finishes. The row is still {row.status}.</p>
              )}
            </div>
          </article>
        </li>
        )
      })}
    </ol>
  )
}
