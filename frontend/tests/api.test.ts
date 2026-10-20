import { afterEach, mock, test } from 'node:test'
import assert from 'node:assert/strict'
import { api, downloadArtifact } from '../src/lib/api'
import { analysis, json } from './fixtures'

afterEach(() => mock.restoreAll())

test('import uses the exact same-origin JSON API contract', async () => {
  const fetch = mock.method(globalThis, 'fetch', async () => json(analysis()))
  await api.import('https://github.com/team/shop')
  assert.equal(fetch.mock.calls[0].arguments[0], '/api/analyses/import')
  const init = fetch.mock.calls[0].arguments[1] as RequestInit
  assert.equal(init.method, 'POST')
  assert.deepEqual(JSON.parse(String(init.body)), { url: 'https://github.com/team/shop' })
})

test('upload sends a browser multipart file without manually setting content type', async () => {
  const fetch = mock.method(globalThis, 'fetch', async () => json(analysis()))
  await api.upload(new File(['zip-bytes'], 'repo.zip'))
  const [path, init] = fetch.mock.calls[0].arguments as [string, RequestInit]
  assert.equal(path, '/api/analyses/upload')
  assert.equal((init.body as FormData).get('file') instanceof File, true)
  assert.equal(init.headers, undefined)
})

test('history retrieval and validation use encoded diagnosis ids and real endpoints', async () => {
  const fetch = mock.method(globalThis, 'fetch', async () => json(analysis()))
  await api.analysis('id/with spaces')
  await api.validate('id/with spaces')
  assert.equal(fetch.mock.calls[0].arguments[0], '/api/analyses/id%2Fwith%20spaces')
  assert.equal(fetch.mock.calls[1].arguments[0], '/api/analyses/id%2Fwith%20spaces/validate')
  assert.equal((fetch.mock.calls[1].arguments[1] as RequestInit).method, 'POST')
})

test('backend errors are surfaced and HTML fallback is not treated as analysis data', async () => {
  const fetch = mock.method(globalThis, 'fetch', async () => json({ message: 'Archive could not be extracted' }, 422))
  await assert.rejects(api.demo(), /Archive could not be extracted/)
  fetch.mock.mockImplementation(async () => new Response('<html>Vite fallback</html>', { headers: { 'Content-Type': 'text/html' } }))
  await assert.rejects(api.history(), /page instead of data/)
  fetch.mock.mockImplementation(async () => { throw new TypeError('Failed to fetch') })
  await assert.rejects(api.health(), /local engine could not be reached/)
})

test('report and diff downloads fetch real artifacts and use safe filenames', async () => {
  const fetch = mock.method(globalThis, 'fetch', async () => new Response('# Report', { headers: { 'Content-Type': 'text/markdown' } }))
  mock.method(URL, 'createObjectURL', () => 'blob:test')
  const names: string[] = []
  mock.method(window.HTMLAnchorElement.prototype, 'click', function (this: HTMLAnchorElement) { names.push(this.download) })
  await downloadArtifact('diagnosis-42', 'owner/repo', 'report')
  await downloadArtifact('diagnosis-42', 'owner/repo', 'patch')
  assert.equal(fetch.mock.calls[0].arguments[0], '/api/analyses/diagnosis-42/report')
  assert.equal(fetch.mock.calls[1].arguments[0], '/api/analyses/diagnosis-42/patch')
  assert.deepEqual(names, ['owner-repo-report.md', 'owner-repo-patch.diff'])
})
