import type { Analysis, Health } from '../types'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`/api${path}`, init)
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') throw error
    throw new Error('The local engine could not be reached. Check that the backend is running and try again.', { cause: error })
  }
  if (!response.ok) {
    let message = `The engine returned an error (${response.status}). Please try again.`
    const text = await response.text()
    try {
      const body = JSON.parse(text) as { message?: string; error?: string; detail?: string }
      message = body.message || body.detail || body.error || message
    } catch {
      if (text && !text.trim().startsWith('<')) message = text.slice(0, 400)
    }
    throw new Error(message)
  }
  const type = response.headers.get('content-type') || ''
  if (type.includes('text/html')) throw new Error('The API returned a page instead of data. Check the backend connection.')
  try {
    return await response.json() as T
  } catch {
    throw new Error('The engine returned an unreadable response. Please try again.')
  }
}

const analysisPath = (id: string) => `/analyses/${encodeURIComponent(id)}`

export const api = {
  health: (signal?: AbortSignal) => request<Health>('/health', { signal }),
  history: (signal?: AbortSignal) => request<Analysis[]>('/analyses', { signal }),
  analysis: (id: string) => request<Analysis>(analysisPath(id)),
  demo: () => request<Analysis>('/analyses/demo', { method: 'POST' }),
  import: (url: string) => request<Analysis>('/analyses/import', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ url }),
  }),
  upload: (file: File) => {
    const body = new FormData()
    body.append('file', file)
    return request<Analysis>('/analyses/upload', { method: 'POST', body })
  },
  validate: (id: string) => request<Analysis>(`${analysisPath(id)}/validate`, { method: 'POST' }),
}

export async function downloadArtifact(id: string, repository: string, kind: 'report' | 'patch') {
  let response: Response
  try {
    response = await fetch(`/api${analysisPath(id)}/${kind}`)
  } catch {
    throw new Error('The download could not reach the local engine. Please try again.')
  }
  if (!response.ok) throw new Error(`Could not download the ${kind} (${response.status}). Please try again.`)
  if (response.headers.get('content-type')?.includes('text/html')) {
    throw new Error('The engine returned a page instead of the requested download.')
  }
  const blob = await response.blob()
  const href = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = href
  link.download = `${repository.replace(/[^a-zA-Z0-9_-]/g, '-')}-${kind}.${kind === 'report' ? 'md' : 'diff'}`
  document.body.append(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(href), 1000)
}
