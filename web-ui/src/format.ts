import type { CandidateStatus } from './types'

export const PHASES: { id: CandidateStatus; label: string }[] = [
  { id: 'pending', label: 'Pending' },
  { id: 'parsing', label: 'Parsing' },
  { id: 'scoring', label: 'Scoring' },
  { id: 'done', label: 'Done' },
]

export const PHASE_INDEX: Record<string, number> = {
  pending: 0,
  parsing: 1,
  scoring: 2,
  done: 3,
}

export const IN_FLIGHT = new Set<CandidateStatus>(['pending', 'parsing', 'scoring'])

export function scoreText(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—'
  return value.toFixed(1)
}

export function formatWhen(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date)
}

export function personLabel(filename: string, name?: string): string {
  const trimmed = name?.trim()
  if (trimmed) return trimmed
  return filename.replace(/\.pdf$/i, '')
}

export function yearsLabel(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return 'Experience not listed'
  const text = Number.isInteger(value) ? String(value) : value.toFixed(1)
  const noun = value === 1 ? 'year' : 'years'
  return `${text} ${noun} of experience`
}

export function decisionLabel(decision: 'accepted' | 'overridden'): string {
  return decision === 'accepted' ? 'Accepted' : 'Overridden'
}

export function statusLabel(status: string): string {
  if (!status) return status
  return status.charAt(0).toUpperCase() + status.slice(1)
}
