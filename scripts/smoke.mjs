import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises'
import { createServer } from 'node:net'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { generateDemoZip } from './demo-zip.mjs'

const root = fileURLToPath(new URL('../', import.meta.url))
const target = resolve(root, 'backend/target')
await mkdir(target, { recursive: true })
const data = await mkdtemp(resolve(target, 'smoke-'))
const socket = createServer()
await new Promise(done => socket.listen(0, '127.0.0.1', done))
const port = socket.address().port
await new Promise(done => socket.close(done))
const base = `http://127.0.0.1:${port}`
let output = ''
const app = spawn('java', ['-jar', 'backend/target/backend-0.0.1-SNAPSHOT.jar'], {
  cwd: root, stdio: ['ignore', 'pipe', 'pipe'],
  env: { ...process.env, PORT: String(port), BIND_ADDRESS: '127.0.0.1', REPOMEDIC_DATA_DIR: data,
    AI_API_URL: '', AI_API_KEY: '', AI_MODEL: '', REPOMEDIC_SANDBOX_ENABLED: 'false' },
})
app.stdout.on('data', bytes => { output = (output + bytes).slice(-128_000) })
app.stderr.on('data', bytes => { output = (output + bytes).slice(-128_000) })
app.on('error', error => { output += error.message })

async function request(path, init = {}) {
  const response = await fetch(base + path, { ...init, signal: AbortSignal.timeout(30_000) })
  assert.equal(response.ok, true, `${path} returned ${response.status}: ${await response.clone().text()}`)
  return response
}

try {
  let ready = false
  for (let i = 0; i < 160; i++) {
    if (app.exitCode !== null) throw new Error(`Application exited early. ${output}`)
    try {
      const response = await fetch(`${base}/api/health`, { signal: AbortSignal.timeout(1000) })
      if (response.ok) { ready = true; break }
    } catch { /* Wait only for server startup, not a failed check. */ }
    await new Promise(done => setTimeout(done, 250))
  }
  assert.equal(ready, true, `Server did not become ready. ${output}`)
  assert.match(await (await request('/')).text(), /RepoMedic AI/)
  const archive = await generateDemoZip()
  assert.equal((await (await request('/demo/broken-react.zip')).arrayBuffer()).byteLength, archive.length)

  const demo = await (await request('/api/analyses/demo', { method: 'POST' })).json()
  assert.equal(demo.tests.failed, 3)
  assert.equal(demo.patches.length, 1)
  assert.equal(demo.patches[0].file, 'cart.js')
  const fixed = await (await request(`/api/analyses/${demo.id}/validate`, { method: 'POST' })).json()
  assert.equal(fixed.status, 'validated')
  assert.equal(fixed.tests.passed, 6)
  assert.equal(fixed.tests.failed, 0)
  assert.equal(fixed.baselineTests.failed, 3)

  const form = new FormData()
  form.set('file', new Blob([archive], { type: 'application/zip' }), 'broken-react.zip')
  const uploaded = await (await request('/api/analyses/upload', { method: 'POST', body: form })).json()
  assert.equal(uploaded.source, 'upload')
  assert.equal(uploaded.executionMode, 'trusted-demo-node')
  assert.equal(uploaded.tests.failed, 3)
  const validated = await (await request(`/api/analyses/${uploaded.id}/validate`, { method: 'POST' })).json()
  assert.equal(validated.tests.passed, 6)
  assert.equal(validated.status, 'validated')

  const report = await (await request(`/api/analyses/${uploaded.id}/report`)).text()
  assert.match(report, /Baseline tests/)
  assert.match(report, /rule-based/)
  const patch = await (await request(`/api/analyses/${uploaded.id}/patch`)).text()
  assert.match(patch, /discountPercent \/ 100/)
  const history = await (await request('/api/analyses')).json()
  assert.equal(history.length, 2)
  // Only the public bundled fixture is saved as a user-visible example report.
  await mkdir(resolve(root, 'reports'), { recursive: true })
  const publicReport = report.replaceAll(data, '[demo-workspace]').split('\n').map(line => line.trimEnd()).join('\n')
  await writeFile(resolve(root, 'reports/demo-diagnostic.md'), publicReport)
  await writeFile(resolve(root, 'reports/demo-fix.patch'), patch)
  console.log('Live packaged-app smoke passed: UI, demo ZIP, real baseline, patch validation, ZIP upload, history, report and diff.')
  console.log('Demo and upload: 3 failed → 6 passed; baseline preserved. Report: reports/demo-diagnostic.md')
} catch (error) {
  console.error(output)
  throw error
} finally {
  const exited = new Promise(done => app.once('exit', done))
  if (app.exitCode === null) {
    app.kill('SIGTERM')
    await Promise.race([exited, new Promise(done => setTimeout(done, 5000))])
    if (app.exitCode === null) app.kill('SIGKILL')
  }
  await rm(data, { recursive: true, force: true })
}
