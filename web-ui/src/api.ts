import type { Candidate, Job, Override, OverrideDecision, Ranking } from './types'

export class ApiError extends Error {
  status: number

  constructor(message: string, status = 0) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const data: unknown = await response.json()
    if (data && typeof data === 'object' && 'error' in data && typeof data.error === 'string' && data.error.trim()) {
      return data.error
    }
  } catch {
    // The API sometimes returns an empty body.
  }
  if (response.status === 404) return 'That record was not found.'
  if (response.status === 409) return 'That action is not available yet.'
  if (response.status === 413) return 'PDF exceeds the 10MB size limit.'
  if (response.status >= 500) return 'The API hit an unexpected error.'
  return `Request failed (${response.status}).`
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, init)
  } catch {
    throw new ApiError('HireFlow API is unreachable. Check that core-api is running.')
  }
  if (!response.ok) {
    throw new ApiError(await errorMessage(response), response.status)
  }
  return response.json() as Promise<T>
}

export const api = {
  jobs(): Promise<Job[]> {
    return request<Job[]>('/api/jobs')
  },
  job(id: string): Promise<Job> {
    return request<Job>(`/api/jobs/${id}`)
  },
  createJob(title: string, description: string): Promise<Job> {
    return request<Job>('/api/jobs', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ title, description }),
    })
  },
  rankings(jobId: string): Promise<Ranking[]> {
    return request<Ranking[]>(`/api/jobs/${jobId}/rankings`)
  },
  upload(jobId: string, files: File[]): Promise<{ candidate_ids: string[] }> {
    const body = new FormData()
    for (const file of files) body.append('files', file)
    return request<{ candidate_ids: string[] }>(`/api/jobs/${jobId}/candidates`, {
      method: 'POST',
      body,
    })
  },
  candidate(id: string): Promise<Candidate> {
    return request<Candidate>(`/api/candidates/${id}`)
  },
  override(id: string, decision: OverrideDecision, note: string, decidedBy: string): Promise<Override> {
    return request<Override>(`/api/candidates/${id}/override`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ decision, note, decided_by: decidedBy }),
    })
  },
}
