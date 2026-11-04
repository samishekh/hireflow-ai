import { useOutletContext } from 'react-router-dom'
import { JobDocument } from '../components/JobDocument'
import { PipelineBoard } from '../components/PipelineBoard'
import { UploadDrop } from '../components/UploadDrop'
import { IN_FLIGHT } from '../format'
import { useLive } from '../live'
import type { Job } from '../types'

export function PipelinePage() {
  const { job } = useOutletContext<{ job: Job }>()
  const { rows, names } = useLive()
  const list = rows ?? []
  const terminal = list.filter((row) => row.status === 'done' || row.status === 'failed').length
  const moving = list.some((row) => IN_FLIGHT.has(row.status))
  const pct = list.length === 0 ? 0 : Math.round((terminal / list.length) * 100)

  return (
    <div className="work">
      <section className="card doc-card">
        <p className="kicker">Job description</p>
        <h2>{job.title}</h2>
        <JobDocument description={job.description} />
      </section>
      <section className="stack">
        <UploadDrop jobId={job.id} />
        {rows == null ? <div className="skeleton" role="status">Connecting to the pipeline…</div> : null}
        {rows && rows.length > 0 ? (
          <div className="pipe-meta">
            <p>{moving ? 'Rows move together as each CV changes phase.' : 'Every CV has left the pipeline.'}</p>
            <p className="num">{terminal} / {rows.length}</p>
            <div className="progress" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
              <span style={{ width: `${pct}%` }} />
            </div>
          </div>
        ) : null}
        {rows ? <PipelineBoard jobId={job.id} rows={rows} names={names} /> : null}
      </section>
    </div>
  )
}
