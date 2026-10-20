import type { Analysis, TestResult } from '../src/types'

export const failedTests: TestResult = {
  total: 4, passed: 2, failed: 2, skipped: 0, status: 'failed', command: 'npm test',
  output: 'BASELINE: cart totals fail\nExpected: 20\nReceived: NaN', durationMs: 127,
}

export function analysis(overrides: Partial<Analysis> = {}): Analysis {
  return {
    id: 'diagnosis-42', repository: 'broken-react-shop', source: 'demo', branch: 'main', stack: ['React', 'TypeScript'],
    status: 'issues_found', createdAt: '2026-10-20T10:00:00Z', durationMs: 550, filesScanned: 8,
    summary: 'A cart calculation uses the wrong variable.', executionMode: 'sandbox', aiEnabled: false,
    fileTree: ['src/cart.ts', 'tests/cart.test.ts'], tests: { ...failedTests }, baselineTests: { ...failedTests },
    findings: [{ id: 'finding-1', title: 'Incorrect quantity reference', severity: 'critical', category: 'Logic',
      file: 'src/cart.ts', line: 12, message: 'The total uses count instead of quantity.', explanation: 'An undefined count makes the total NaN.',
      before: 'return price * count;', after: 'return price * quantity;', patchable: true }],
    patches: [{ id: 'patch-1', file: 'src/cart.ts', line: 12, before: 'return price * count;', after: 'return price * quantity;',
      explanation: 'Use the function parameter for quantity.', provider: 'deterministic', status: 'suggested' }],
    events: [{ label: 'Repository inspected', detail: '8 files scanned', status: 'complete', durationMs: 100 }],
    ...overrides,
  }
}

export function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
