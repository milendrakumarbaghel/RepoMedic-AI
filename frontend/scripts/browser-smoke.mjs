import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir, writeFile, mkdtemp, rm } from 'node:fs/promises'
import { createServer as createHttpServer } from 'node:http'
import { resolve } from 'node:path'
import { createServer } from 'vite'
import { analysis, failedTests } from '../tests/fixtures.ts'

// Optional local-browser smoke test. No browser downloads or backend mutations.
if (!process.env.CHROME_BIN) throw new Error('Set CHROME_BIN to an installed Chrome/Chromium executable.')
const output = resolve('node_modules/.cache/repomedic-smoke')
await mkdir(output, { recursive: true })
const profile = await mkdtemp(`${output}/profile-`)
let current = null
const mockApi = createHttpServer((request, response) => {
  response.setHeader('Content-Type', 'application/json')
  if (request.url === '/api/health') response.end(JSON.stringify({ status: 'ok', aiEnabled: false }))
  else if (request.url === '/api/analyses') response.end(JSON.stringify(current ? [current] : []))
  else if (request.url === '/api/analyses/demo' && request.method === 'POST') {
    current = analysis()
    response.end(JSON.stringify(current))
  } else if (request.url?.endsWith('/validate') && request.method === 'POST') {
    current = analysis({ status: 'validated', tests: { ...failedTests, passed: 4, failed: 0, status: 'passed', output: 'LATEST: all four tests pass' }, patches: analysis().patches.map(patch => ({ ...patch, status: 'validated' })) })
    response.end(JSON.stringify(current))
  } else { response.statusCode = 404; response.end(JSON.stringify({ message: 'Unknown test endpoint' })) }
})
await new Promise(done => mockApi.listen(0, '127.0.0.1', done))
const address = mockApi.address()
const vite = await createServer({ server: { host: '127.0.0.1', port: 0, strictPort: false, proxy: { '/api': { target: `http://127.0.0.1:${address.port}` } } } })
await vite.listen()
const chrome = spawn(process.env.CHROME_BIN, [
  '--headless', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--disable-dev-shm-usage', '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank',
], { stdio: ['ignore', 'ignore', 'pipe'] })
let socket

try {
  const endpoint = await new Promise((done, reject) => {
    let stderr = ''
    const timeout = setTimeout(() => reject(new Error('Chrome did not start in 20 seconds')), 20_000)
    chrome.stderr.on('data', data => {
      stderr += data
      const match = stderr.match(/DevTools listening on (ws:\/\/[^\s]+)/)
      if (match) { clearTimeout(timeout); done(match[1]) }
    })
    chrome.once('error', reject)
  })
  const debuggerUrl = new URL(endpoint)
  const targets = await fetch(`http://${debuggerUrl.host}/json/list`).then(response => response.json())
  const target = targets.find(item => item.type === 'page')
  socket = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise(done => socket.addEventListener('open', done, { once: true }))
  let sequence = 0
  const pending = new Map()
  socket.addEventListener('message', event => {
    const message = JSON.parse(String(event.data))
    if (message.id) {
      const request = pending.get(message.id)
      pending.delete(message.id)
      if (message.error) request?.reject(new Error(message.error.message))
      else request?.resolve(message.result)
    }
  })
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++sequence
    pending.set(id, { resolve, reject })
    socket.send(JSON.stringify({ id, method, params }))
  })
  const evaluate = async expression => {
    const response = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (response.exceptionDetails) throw new Error(response.exceptionDetails.text)
    return response.result.value
  }
  const waitFor = async expression => {
    for (let i = 0; i < 100; i++) {
      if (await evaluate(expression)) return
      await new Promise(done => setTimeout(done, 60))
    }
    throw new Error(`Browser condition timed out: ${expression}`)
  }
  const click = label => evaluate(`Array.from(document.querySelectorAll('button')).find(button => button.textContent.trim() === ${JSON.stringify(label)})?.click()`)
  const viewport = (width, height) => send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width < 600 })
  const screenshot = async name => {
    await new Promise(done => setTimeout(done, 250))
    const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    await writeFile(`${output}/${name}.png`, Buffer.from(image.data, 'base64'))
  }
  await send('Page.enable')
  await viewport(1440, 1080)
  await send('Page.navigate', { url: vite.resolvedUrls.local[0] })
  await waitFor(`document.querySelector('.engine-status')?.textContent.includes('connected')`)
  assert.equal(await evaluate(`document.querySelectorAll('.stat-card strong').length`), 4)
  assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true)
  console.log('Desktop section layout:', await evaluate(`Object.fromEntries(['.page-heading','.hero-panel','.stats-grid','.workbench','.empty-preview'].map(selector=>{const rect=document.querySelector(selector).getBoundingClientRect();return [selector,{top:Math.round(rect.top),height:Math.round(rect.height)}]}))`))
  await screenshot('desktop-empty')
  await click('Try broken React demo')
  await waitFor(`document.querySelector('.finding-title')?.textContent.includes('Incorrect quantity')`)
  await screenshot('desktop-diagnosis')
  await click('Apply patch & rerun tests')
  await waitFor(`document.querySelector('.stat-card.tests strong')?.textContent === '100%'`)
  await click('Test output')
  assert.equal(await evaluate(`document.querySelector('.test-comparison')?.textContent.includes('BASELINE: cart totals fail')`), true)
  await viewport(390, 844)
  await evaluate('window.scrollTo(0,0)')
  assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true)
  await screenshot('mobile-diagnosis')
  await click('New diagnosis')
  await waitFor(`!!document.querySelector('[role="dialog"]')`)
  assert.equal(await evaluate(`document.querySelector('.main-shell').inert`), true)
  await screenshot('mobile-import')
  await evaluate(`document.querySelector('[aria-label="Close dialog"]').click()`)
  await evaluate(`document.querySelector('[aria-label="Open navigation"]').click()`)
  await waitFor(`getComputedStyle(document.querySelector('.sidebar')).visibility === 'visible'`)
  console.log('Browser smoke passed: desktop/mobile layout, demo, validation, baseline, dialog, navigation.')
  console.log(`Screenshots: ${output}`)
} finally {
  socket?.close()
  chrome.kill()
  await vite.close()
  await new Promise(done => mockApi.close(done))
  await new Promise(done => setTimeout(done, 250))
  await rm(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
}
