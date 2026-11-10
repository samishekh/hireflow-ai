import { BrowserRouter, Link, Route, Routes } from 'react-router-dom'
import { Shell } from './components/Shell'
import { FormulaProvider } from './formula'
import { CandidatePage } from './pages/CandidatePage'
import { FormulaPage } from './pages/FormulaPage'
import { JobLayout } from './pages/JobLayout'
import { JobsPage } from './pages/JobsPage'
import { NewJobPage } from './pages/NewJobPage'
import { PipelinePage } from './pages/PipelinePage'
import { RankingsPage } from './pages/RankingsPage'

export function App() {
  return (
    <BrowserRouter>
      <FormulaProvider>
        <Routes>
          <Route element={<Shell />}>
            <Route index element={<JobsPage />} />
            <Route path="formula" element={<FormulaPage />} />
            <Route path="jobs/new" element={<NewJobPage />} />
            <Route path="jobs/:jobId" element={<JobLayout />}>
              <Route index element={<PipelinePage />} />
              <Route path="rankings" element={<RankingsPage />} />
              <Route path="candidates/:candidateId" element={<CandidatePage />} />
            </Route>
            <Route path="*" element={<NotFound />} />
          </Route>
        </Routes>
      </FormulaProvider>
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
