import { BrowserRouter, Link, Route, Routes } from 'react-router-dom'
import { Shell } from './components/Shell'
import { CandidatePage } from './pages/CandidatePage'
import { ComparePage } from './pages/ComparePage'
import { JobLayout } from './pages/JobLayout'
import { JobsPage } from './pages/JobsPage'
import { NewJobPage } from './pages/NewJobPage'
import { PipelinePage } from './pages/PipelinePage'
import { RankingsPage } from './pages/RankingsPage'

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Shell />}>
          <Route index element={<JobsPage />} />
          <Route path="jobs/new" element={<NewJobPage />} />
          <Route path="jobs/:jobId" element={<JobLayout />}>
            <Route index element={<PipelinePage />} />
            <Route path="rankings" element={<RankingsPage />} />
            <Route path="compare" element={<ComparePage />} />
            <Route path="candidates/:candidateId" element={<CandidatePage />} />
          </Route>
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

function NotFound() {
  return (
    <section className="card empty-card">
      <p className="kicker">Missing page</p>
      <h1>That page is not part of HireFlow.</h1>
      <Link className="btn" to="/">Back to roles</Link>
    </section>
  )
}
