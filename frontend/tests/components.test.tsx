import { afterEach, beforeEach, mock, test } from 'node:test'
import assert from 'node:assert/strict'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from '../src/App'
import { RepositoryImport } from '../src/components/RepositoryImport'
import { StatCards } from '../src/components/Workbench'
import { analysis, failedTests, json } from './fixtures'

let routes: Map<string, () => Response | Promise<Response>>
let calls: { path: string; init?: RequestInit }[]

beforeEach(() => {
  calls = []
  routes = new Map([
    ['GET /api/health', () => json({ status: 'ok', aiEnabled: false })],
    ['GET /api/analyses', () => json([])],
  ])
  mock.method(globalThis, 'fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    calls.push({ path, init })
    const handler = routes.get(`${init?.method || 'GET'} ${path}`)
    if (!handler) throw new Error(`Unexpected test request: ${path}`)
    return handler()
  })
})
afterEach(() => { cleanup(); mock.restoreAll() })

async function openApp() {
  const user = userEvent.setup()
  render(<App />)
  await screen.findByText('Local engine connected')
  await waitFor(() => assert.equal(screen.queryByText('Loading your workspace…'), null))
  return user
}

test('the initial workspace fetches history without creating a demo or fake success', async () => {
  await openApp()
  assert.equal(calls.some(call => call.init?.method === 'POST'), false)
  assert.ok(screen.getByText('SAMPLE PREVIEW'))
  assert.ok(screen.getByText('Illustrative workflow. No analysis has run.'))
  const stats = screen.getByRole('region', { name: 'Selected repository statistics' })
  assert.equal(within(stats).getAllByText('—').length, 4)
  assert.equal(within(stats).queryByText('100%'), null)
})

test('demo runs on click, shows real findings, and validates through the backend with baseline preserved', async () => {
  routes.set('POST /api/analyses/demo', () => json(analysis()))
  const validated = analysis({ status: 'validated', tests: { ...failedTests, passed: 4, failed: 0, status: 'passed', output: 'LATEST: all four tests pass' }, patches: analysis().patches.map(p => ({ ...p, status: 'validated' })) })
  routes.set('POST /api/analyses/diagnosis-42/validate', () => json(validated))
  const user = await openApp()
  await user.click(screen.getByRole('button', { name: 'Try broken React demo' }))
  await screen.findByText('Incorrect quantity reference')
  assert.ok(screen.getByText('Rule-based'))
  assert.ok(screen.getByText('50%'))
  assert.ok(screen.getByText(/BASELINE: cart totals fail/))
  await user.click(screen.getByRole('button', { name: 'Apply patch & rerun tests' }))
  await screen.findByText('100%')
  assert.ok(calls.some(call => call.path.endsWith('/validate') && call.init?.method === 'POST'))
  await user.click(screen.getByRole('tab', { name: 'Test output' }))
  assert.ok(screen.getByText(/BASELINE: cart totals fail/))
  assert.ok(screen.getByText('LATEST: all four tests pass'))
  assert.ok(screen.getByText('Original baseline · preserved'))
})

test('blocked backend tests remain blocked and never appear as a passing validation', async () => {
  routes.set('GET /api/analyses', () => json([analysis({ tests: { ...failedTests, total: 0, passed: 0, failed: 0, status: 'blocked', output: 'Network access unavailable' } })]))
  await openApp()
  assert.ok(screen.getByText('Tests blocked — not verified'))
  assert.ok(screen.getByText('Execution blocked · review output'))
  assert.equal(screen.queryByText('100%'), null)
  assert.equal(screen.queryByText('Healthy'), null)
})

test('server errors are dismissible and leave the empty workspace usable', async () => {
  routes.set('POST /api/analyses/demo', () => json({ message: 'The test sandbox is unavailable' }, 503))
  const user = await openApp()
  await user.click(screen.getByRole('button', { name: 'Try broken React demo' }))
  assert.ok(await screen.findByRole('alert'))
  assert.ok(screen.getByText('The test sandbox is unavailable'))
  await user.click(screen.getByRole('button', { name: 'Dismiss notification' }))
  assert.equal(screen.queryByRole('alert'), null)
  assert.equal((screen.getByRole('button', { name: 'Try broken React demo' }) as HTMLButtonElement).disabled, false)
})

test('in-flight requests disable a duplicate demo submission', async () => {
  let resolve!: (value: Response) => void
  routes.set('POST /api/analyses/demo', () => new Promise<Response>(done => { resolve = done }))
  const user = await openApp()
  const button = screen.getByRole('button', { name: 'Try broken React demo' })
  await user.click(button)
  assert.equal((button as HTMLButtonElement).disabled, true)
  await user.click(button)
  assert.equal(calls.filter(call => call.path === '/api/analyses/demo').length, 1)
  await act(async () => resolve(json(analysis())))
  await screen.findByText('Incorrect quantity reference')
})

test('history selection fetches the selected diagnosis and reports remain navigable', async () => {
  const older = analysis({ id: 'older', repository: 'older-repository', createdAt: '2026-10-19T10:00:00Z' })
  routes.set('GET /api/analyses', () => json([older, analysis()]))
  routes.set('GET /api/analyses/older', () => json(older))
  const user = await openApp()
  await user.click(screen.getByRole('button', { name: /older-repository/ }))
  await screen.findByRole('button', { name: /Diagnostics/, current: 'page' })
  assert.ok(calls.some(call => call.path === '/api/analyses/older'))
  await user.click(screen.getByRole('button', { name: 'Reports' }))
  assert.ok(screen.getByText('Diagnosis library'))
  assert.equal(screen.getAllByRole('button', { name: /Open report/ }).length, 2)
})

test('new diagnosis dialog supports keyboard tabs, URL errors, and focus restoration', async () => {
  const user = await openApp()
  const trigger = screen.getByRole('button', { name: 'New diagnosis' })
  await user.click(trigger)
  const dialog = screen.getByRole('dialog')
  const urlTab = within(dialog).getByRole('tab', { name: 'GitHub repository' })
  urlTab.focus()
  await user.keyboard('{ArrowRight}')
  assert.equal(within(dialog).getByRole('tab', { name: 'Upload ZIP' }).getAttribute('aria-selected'), 'true')
  await user.keyboard('{ArrowLeft}')
  await user.type(within(dialog).getByRole('textbox', { name: 'GitHub repository URL' }), 'not-a-repo')
  await user.click(within(dialog).getByRole('button', { name: 'Analyze repository' }))
  assert.match(within(dialog).getByRole('alert').textContent!, /GitHub repository URL/)
  assert.equal(calls.some(call => call.path.includes('/import')), false)
  await user.keyboard('{Escape}')
  assert.equal(screen.queryByRole('dialog'), null)
  assert.equal(document.activeElement, trigger)
})

test('GitHub import uses the entered URL and closes the dialog after success', async () => {
  routes.set('POST /api/analyses/import', () => json(analysis({ source: 'github', repository: 'team/shop' })))
  const user = await openApp()
  await user.click(screen.getByRole('button', { name: 'New diagnosis' }))
  const dialog = screen.getByRole('dialog')
  await user.type(within(dialog).getByRole('textbox', { name: 'GitHub repository URL' }), 'https://github.com/team/shop')
  await user.click(within(dialog).getByRole('button', { name: 'Analyze repository' }))
  await waitFor(() => assert.equal(screen.queryByRole('dialog'), null))
  assert.equal(document.activeElement, screen.getByRole('button', { name: 'New diagnosis' }))
  const call = calls.find(call => call.path === '/api/analyses/import')!
  assert.deepEqual(JSON.parse(String(call.init?.body)), { url: 'https://github.com/team/shop' })
  assert.ok(screen.getByText('GitHub import', { exact: false }))
})

test('ZIP drag-and-drop rejects invalid files, uploads valid ones, and resets selection', async () => {
  const uploads: File[] = []
  const user = userEvent.setup()
  render(<RepositoryImport busy={false} onImport={async () => true} onUpload={async file => { uploads.push(file); return true }} />)
  await user.click(screen.getByRole('tab', { name: 'Upload ZIP' }))
  const zone = screen.getByText(/Drop your repository ZIP here/).parentElement!
  fireEvent.drop(zone, { dataTransfer: { files: [new File(['text'], 'bad.txt')] } })
  assert.match(screen.getByRole('alert').textContent!, /\.zip/)
  const zip = new File(['archive'], 'repository.zip', { type: 'application/zip' })
  fireEvent.drop(zone, { dataTransfer: { files: [zip] } })
  await user.click(screen.getByRole('button', { name: 'Analyze repository' }))
  assert.equal(uploads[0].name, 'repository.zip')
  assert.equal(screen.queryByText('repository.zip'), null)
  const input = screen.getByLabelText('Repository ZIP archive') as HTMLInputElement
  await user.upload(input, zip)
  assert.equal(input.value, '')
  await user.click(screen.getByRole('button', { name: 'Remove selected archive' }))
  await user.upload(input, zip)
  assert.ok(screen.getByText('repository.zip'))
})

test('empty metrics render em dashes independently of onboarding previews', () => {
  render(<StatCards analysis={null} />)
  assert.equal(screen.getAllByText('—').length, 4)
})
