export interface TestResult {
  total: number
  passed: number
  failed: number
  skipped: number
  status: 'passed' | 'failed' | 'blocked'
  command: string
  output: string
  durationMs: number
}

export interface Finding {
  id: string
  title: string
  severity: 'critical' | 'warning' | 'info'
  category: string
  file: string
  line: number
  message: string
  explanation: string
  before: string
  after: string
  patchable: boolean
}

export interface Patch {
  id: string
  file: string
  line: number
  before: string
  after: string
  explanation: string
  provider: string
  status: 'suggested' | 'validated' | 'rejected'
}

export interface Analysis {
  id: string
  repository: string
  source: 'demo' | 'upload' | 'github'
  branch: string
  stack: string[]
  status: 'issues_found' | 'healthy' | 'validated' | 'error'
  createdAt: string
  durationMs: number
  filesScanned: number
  summary: string
  executionMode: string
  aiEnabled: boolean
  fileTree: string[]
  tests: TestResult
  baselineTests: TestResult
  findings: Finding[]
  patches: Patch[]
  events: { label: string; detail: string; status: 'complete' | 'blocked' | 'failed'; durationMs: number }[]
}

export interface Health {
  status: string
  aiEnabled: boolean
}
