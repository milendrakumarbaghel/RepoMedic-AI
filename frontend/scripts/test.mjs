import { spawnSync } from 'node:child_process'

// Accept the requested Vitest-style --run spelling; all tests run once and offline.
const args = process.argv.slice(2).filter(arg => arg !== '--run')
const result = spawnSync(process.execPath, [
  '--import', 'tsx', '--import', './tests/setup.ts', '--test', '--test-concurrency=1',
  ...args, 'tests/*.test.ts', 'tests/*.test.tsx',
], {
  stdio: 'inherit',
  env: { ...process.env, TSX_TSCONFIG_PATH: './tsconfig.test.json' },
})
if (result.error) console.error(result.error)
process.exitCode = result.status ?? 1
