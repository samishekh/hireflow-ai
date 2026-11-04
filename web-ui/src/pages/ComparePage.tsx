import { Link, useParams } from 'react-router-dom'
import { ScoreBars } from '../components/ScoreCard'
import { personLabel, scoreText } from '../format'
import { useLive } from '../live'
import type { Ranking } from '../types'

export function ComparePage() {
  const { jobId } = useParams()
  const { rows, names } = useLive()
  if (!jobId) return null
  if (rows == null) return <div className="skeleton" role="status">Loading the comparison…</div>

  const ranked = rows
    .filter((row) => row.score?.comparison_rank != null)
    .sort((left, right) => (left.score?.comparison_rank ?? 0) - (right.score?.comparison_rank ?? 0))
  const comparing = rows
    .filter((row) => row.status === 'comparing')
    .sort((left, right) => (right.score?.overall_score ?? -1) - (left.score?.overall_score ?? -1))
  const shown = ranked.length > 0 ? ranked : comparing
  const shownIds = new Set(shown.map((row) => row.id))
  const outside = rows.filter((row) => row.status === 'done' && row.score && row.score.comparison_rank == null && !shownIds.has(row.id))
  const earlier = rows.some((row) => row.status === 'pending' || row.status === 'parsing' || row.status === 'scoring')
  const doneCount = rows.filter((row) => row.status === 'done' || row.status === 'comparing').length
  const failed = rows.filter((row) => row.status === 'failed')

  return (
    <div className="stack">
      <header className="page-head">
        <p className="kicker">Comparison pass</p>
        <h1>Why each of the top five is in that order.</h1>
        <p className="lede">
          After individual scores finish, HireFlow sends only the current top five back for a relative ranking.
          Rank 1 is the best fit. Each reason is one line. The weighted score stays beside it and is not replaced.
        </p>
      </header>
      {rows.length === 0 ? (
        <section className="card empty-card">
          <h2>Nothing to compare</h2>
          <p>Upload at least two CVs. A single CV keeps its weighted score and skips this pass.</p>
          <Link to={`/jobs/${jobId}`} className="btn">Back to pipeline</Link>
        </section>
      ) : null}
      {shown.length === 0 && rows.length > 0 ? (
        <section className="card empty-card">
          <h2>{emptyTitle(doneCount, earlier, failed.length)}</h2>
          <p>{emptyBody(doneCount, earlier, failed)}</p>
          <Link to={`/jobs/${jobId}`} className="btn">Watch the pipeline</Link>
        </section>
      ) : null}
      {ranked.length > 0 && earlier ? (
        <div className="banner banner-note">
          Another upload is still parsing or scoring. This order refreshes when that batch finishes.
        </div>
      ) : null}
      {ranked.length === 0 && comparing.length > 0 ? (
        <div className="banner banner-note">
          These candidates are in the comparing phase together. Reasons land when the pass finishes.
        </div>
      ) : null}
      {shown.length > 0 ? (
        <ol className="compare-list">
          {shown.map((row) => (
            <CompareCard key={row.id} jobId={jobId} row={row} name={names[row.id]} pending={ranked.length === 0} />
          ))}
        </ol>
      ) : null}
      {outside.length > 0 ? (
        <section className="card">
          <h2>Outside this comparison</h2>
          <p className="muted">Only the current top five by weighted score get a comparison rank. The others keep their score.</p>
          <ul className="outside-list">
            {outside.map((row) => (
              <li key={row.id}>
                <Link to={`/jobs/${jobId}/candidates/${row.id}`}>
                  {personLabel(row.original_filename, names[row.id])}
                </Link>
                <span className="num">{scoreText(row.score?.overall_score)}</span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  )
}

function CompareCard({
  jobId,
  row,
  name,
  pending,
}: {
  jobId: string
  row: Ranking
  name?: string
  pending: boolean
}) {
  const rank = row.score?.comparison_rank
  const lead = rank === 1
  return (
    <li className={lead ? 'compare-card lead' : 'compare-card'}>
      <div className="rank-no num">{pending || rank == null ? '—' : String(rank).padStart(2, '0')}</div>
      <div className="compare-copy">
        <h2>{personLabel(row.original_filename, name)}</h2>
        <p className="filename">{row.original_filename}</p>
        <p className={pending ? 'reason muted' : 'reason'}>
          {pending
            ? 'The reason appears when this pass finishes.'
            : (row.score?.comparison_reason || 'No reason was returned for this rank.')}
        </p>
        {row.score ? <ScoreBars score={row.score} /> : null}
        <Link className={lead ? 'text-link ink' : 'text-link'} to={`/jobs/${jobId}/candidates/${row.id}`}>
          Open profile
        </Link>
      </div>
      <div className="score-hero">
        <span className="num">{scoreText(row.score?.overall_score)}</span>
        <small>Weighted</small>
      </div>
    </li>
  )
}

function emptyTitle(doneCount: number, earlier: boolean, failedCount: number): string {
  if (earlier || doneCount === 0) return 'The comparison has not started.'
  if (doneCount < 2) return 'One scored CV is not enough to compare.'
  if (failedCount > 0) return 'The comparison pass did not finish.'
  return 'No comparison ranks yet.'
}

function emptyBody(doneCount: number, earlier: boolean, failed: Ranking[]): string {
  if (earlier) return 'Candidates still in the pipeline are moving. This page fills in when at least two have a score and the top five are compared.'
  if (doneCount < 2) return 'HireFlow keeps the weighted score and skips the relative ranking until a second CV is done.'
  if (failed.length > 0) {
    const detail = failed.map((row) => row.error_message?.trim()).filter(Boolean)[0]
    return detail || 'The candidates in that pass failed. The message is on each row in the pipeline.'
  }
  return 'Ranks show up here after the compare pass stores a reason for each of the top five.'
}
