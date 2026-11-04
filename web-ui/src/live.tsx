import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { api, ApiError } from './api'
import { IN_FLIGHT } from './format'
import { useToast } from './toast'
import type { Ranking } from './types'

type LiveValue = {
  rows: Ranking[] | null
  error: string | null
  refresh: () => Promise<void>
  names: Record<string, string>
}

const LiveContext = createContext<LiveValue | null>(null)

export function useLive(): LiveValue {
  const value = useContext(LiveContext)
  if (!value) throw new Error('Pipeline data is only available on a role.')
  return value
}

export function LiveProvider({ jobId, children }: { jobId: string; children: ReactNode }) {
  const toast = useToast()
  const toastRef = useRef(toast)
  toastRef.current = toast
  const [rows, setRows] = useState<Ranking[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [names, setNames] = useState<Record<string, string>>({})
  const primed = useRef(false)
  const seenFail = useRef(new Set<string>())
  const namesAsked = useRef(new Set<string>())
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const apply = useCallback((next: Ranking[]) => {
    if (!primed.current) {
      for (const row of next) {
        if (row.status === 'failed') seenFail.current.add(row.id)
      }
      primed.current = true
    } else {
      for (const row of next) {
        if (row.status === 'failed' && !seenFail.current.has(row.id)) {
          seenFail.current.add(row.id)
          const detail = row.error_message?.trim() || 'The pipeline stopped on this CV.'
          toastRef.current.error(`${row.original_filename}: ${detail}`)
        }
      }
    }
    setRows(next)
    const need = next
      .filter((row) => (row.status === 'done' || row.status === 'failed') && !namesAsked.current.has(row.id))
      .map((row) => row.id)
    if (need.length === 0) return
    for (const id of need) namesAsked.current.add(id)
    void Promise.all(
      need.map(async (id) => {
        try {
          const candidate = await api.candidate(id)
          return [id, candidate.parsed_data?.candidate_name?.trim() ?? ''] as const
        } catch {
          return [id, ''] as const
        }
      }),
    ).then((pairs) => {
      if (!mounted.current) return
      setNames((current) => {
        let changed = false
        const copy = { ...current }
        for (const [id, name] of pairs) {
          if (name && copy[id] !== name) {
            copy[id] = name
            changed = true
          }
        }
        return changed ? copy : current
      })
    })
  }, [])

  const refresh = useCallback(async () => {
    const next = await api.rankings(jobId)
    if (!mounted.current) return
    setError(null)
    apply(next)
  }, [apply, jobId])

  useEffect(() => {
    let cancel = false
    let timer = 0
    let announced = false
    const tick = async () => {
      if (cancel) return
      if (document.hidden) {
        timer = window.setTimeout(tick, 800)
        return
      }
      let delay = 500
      try {
        const next = await api.rankings(jobId)
        if (cancel) return
        announced = false
        setError(null)
        apply(next)
        delay = next.some((row) => IN_FLIGHT.has(row.status)) ? 500 : 4000
      } catch (caught) {
        if (cancel) return
        const message = caught instanceof ApiError ? caught.message : 'Could not reach the API.'
        setError(message)
        if (!announced) {
          announced = true
          toastRef.current.error(message)
        }
        delay = 2500
      }
      if (!cancel) timer = window.setTimeout(tick, delay)
    }
    void tick()
    return () => {
      cancel = true
      window.clearTimeout(timer)
    }
  }, [apply, jobId])

  return (
    <LiveContext.Provider value={{ rows, error, refresh, names }}>
      {children}
    </LiveContext.Provider>
  )
}
