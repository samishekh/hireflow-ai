import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

type Tone = 'error' | 'success' | 'info'

type Toast = {
  id: number
  tone: Tone
  message: string
}

type ToastApi = {
  error: (message: string) => void
  success: (message: string) => void
  info: (message: string) => void
}

const ToastContext = createContext<ToastApi | null>(null)

let nextId = 1

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  const push = useCallback((tone: Tone, message: string) => {
    const id = nextId++
    setToasts((current) => [...current.slice(-3), { id, tone, message }])
    const life = tone === 'error' ? 9000 : 4200
    window.setTimeout(() => dismiss(id), life)
  }, [dismiss])

  const api = useMemo<ToastApi>(() => ({
    error: (message) => push('error', message),
    success: (message) => push('success', message),
    info: (message) => push('info', message),
  }), [push])

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toasts">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast toast-${toast.tone}`} role={toast.tone === 'error' ? 'alert' : 'status'}>
            <p>{toast.message}</p>
            <button type="button" onClick={() => dismiss(toast.id)} aria-label="Dismiss notification">
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastApi {
  const api = useContext(ToastContext)
  if (!api) throw new Error('Notifications are unavailable.')
  return api
}
