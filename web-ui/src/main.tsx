import { Component, type ReactNode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import { ToastProvider } from './toast'
import './styles.css'

class Boundary extends Component<{ children: ReactNode }, { message: string | null }> {
  state = { message: null as string | null }

  static getDerivedStateFromError(error: unknown) {
    const message = error instanceof Error ? error.message : 'This screen hit an unexpected error.'
    return { message }
  }

  render() {
    if (this.state.message) {
      return (
        <div className="fatal">
          <p className="kicker">Something broke</p>
          <h1>This screen hit an unexpected error.</h1>
          <p>{this.state.message}</p>
          <button type="button" className="btn" onClick={() => window.location.reload()}>
            Reload
          </button>
        </div>
      )
    }
    return this.props.children
  }
}

const root = document.getElementById('root')
if (!root) throw new Error('Root element is missing.')

createRoot(root).render(
  <ToastProvider>
    <Boundary>
      <App />
    </Boundary>
  </ToastProvider>,
)
