import type { Analysis, TestResult } from '../types'

export function testRate(tests?: TestResult): string {
  if (!tests || tests.status === 'blocked' || tests.total - tests.skipped <= 0) return '—'
  return `${Math.round(tests.passed / (tests.total - tests.skipped) * 100)}%`
}

export function healthLabel(analysis?: Analysis | null): string {
  if (!analysis) return '—'
  if (analysis.status === 'error') return 'Error'
  if (analysis.tests.status === 'blocked') return 'Blocked'
  if (analysis.tests.status === 'failed') return 'Needs care'
  if (analysis.status === 'validated') return 'Validated'
  if (analysis.status === 'healthy') return 'Healthy'
  return 'Needs care'
}

export function providerLabel(provider: string): string {
  return /deterministic|rule[-_ ]?based|heuristic/i.test(provider) ? 'Rule-based' : provider || 'Unspecified provider'
}

export function duration(ms: number): string {
  return ms < 1000 ? `${Math.round(ms)} ms` : `${(ms / 1000).toFixed(1)} s`
}

export function formatDate(date: string): string {
  const value = new Date(date)
  return Number.isNaN(value.getTime()) ? 'Unknown date' : value.toLocaleString(undefined, {
    month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
  })
}

export function normalizeRepositoryUrl(value: string): string {
  try {
    const url = new URL(value.trim())
    const parts = url.pathname.replace(/\/$/, '').split('/').filter(Boolean)
    if (url.protocol !== 'https:' || url.hostname !== 'github.com' || url.port || url.username || url.password || parts.length !== 2 || !parts.every(part => /^[\w.-]+$/.test(part))) throw new Error()
    return `https://github.com/${parts.join('/')}`
  } catch {
    throw new Error('Enter a GitHub repository URL, like https://github.com/owner/repository.')
  }
}

export function validateZip(file: File): string | null {
  if (!file.name.toLowerCase().endsWith('.zip')) return 'Please choose a .zip repository archive.'
  if (file.size === 0) return 'This archive is empty. Choose a ZIP containing your source files.'
  return null
}
