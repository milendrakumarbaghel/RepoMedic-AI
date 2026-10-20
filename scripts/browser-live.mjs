import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

// Optional real-browser test against a running app. Creates one public demo.
// No browser package/download, provider calls, or machine-specific paths.
if (!process.env.CHROME_BIN) throw new Error('Set CHROME_BIN to an installed Chrome or Chromium executable.')
const base = process.env.REPOMEDIC_URL || 'http://localhost:8080'
const output = resolve('.cache/browser-live')
await mkdir(output, { recursive: true })
const profile = await mkdtemp(resolve(output, 'profile-'))
const chrome = spawn(process.env.CHROME_BIN, ['--headless', '--disable-gpu', '--no-first-run',
  '--no-default-browser-check', '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'],
{ stdio: ['ignore', 'ignore', 'pipe'] })
let socket

try {
  const endpoint = await new Promise((done, reject) => {
    let buffer = ''
    const timeout = setTimeout(() => reject(new Error('Chrome did not start in 20 seconds')), 20_000)
    chrome.stderr.on('data', bytes => {
      buffer += bytes
      const match = buffer.match(/DevTools listening on (ws:\/\/[^\s]+)/)
      if (match) { clearTimeout(timeout); done(match[1]) }
    })
    chrome.once('error', error => { clearTimeout(timeout); reject(error) })
  })
  const targets = await fetch(`http://${new URL(endpoint).host}/json/list`).then(response => response.json())
  socket = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl)
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
    const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.text)
    return result.result.value
  }
  const waitFor = async expression => {
    for (let i = 0; i < 150; i++) {
      if (await evaluate(expression)) return
      await new Promise(done => setTimeout(done, 100))
    }
    throw new Error(`Browser condition timed out: ${expression}`)
  }
  const click = label => evaluate(`Array.from(document.querySelectorAll('button')).find(button => button.textContent.trim() === ${JSON.stringify(label)})?.click()`)
  const viewport = (width, height) => send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width < 600 })
  const screenshot = async name => {
    await new Promise(done => setTimeout(done, 200))
    const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    await writeFile(resolve(output, `${name}.png`), Buffer.from(image.data, 'base64'))
  }

  await send('Page.enable')
  await viewport(1440, 1080)
  await send('Page.navigate', { url: base })
  await waitFor(`document.querySelector('.engine-status')?.textContent.includes('connected')`)
  assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true)
  await screenshot('desktop-initial')
  await evaluate(`Array.from(document.querySelectorAll('button')).find(button => ['Try broken React demo','Run demo'].includes(button.textContent.trim()))?.click()`)
  await waitFor(`document.querySelector('.finding-title')?.textContent.includes('percentage') && !document.querySelector('.operation-banner')`)
  assert.equal(await evaluate(`document.querySelector('.stat-card.tests strong')?.textContent`), '50%')
  await click('Patch preview')
  assert.equal(await evaluate(`document.querySelector('.code-pane.after')?.textContent.includes('discountPercent / 100')`), true)
  await screenshot('desktop-patch')
  await click('Apply patch & rerun tests')
  await waitFor(`document.querySelector('.stat-card.tests strong')?.textContent === '100%'`)
  await click('Test output')
  assert.equal(await evaluate(`document.querySelector('.test-comparison')?.textContent.includes('3 passed · 3 failed')`), true)
  assert.equal(await evaluate(`document.querySelector('.test-comparison')?.textContent.includes('6 passed · 0 failed')`), true)
  await screenshot('desktop-validated')
  await viewport(390, 844)
  await evaluate('window.scrollTo(0,0)')
  assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true)
  await screenshot('mobile-validated')
  await click('New diagnosis')
  await waitFor(`!!document.querySelector('[role="dialog"]')`)
  await evaluate(`document.querySelector('[role="dialog"] [role="tab"][id$="zip-tab"]').click()`)
  assert.equal(await evaluate(`document.querySelector('[role="dialog"] a[download]')?.getAttribute('href')`), '/demo/broken-react.zip')
  await screenshot('mobile-upload')
  console.log('Real browser smoke passed: live Java API, demo, source diff, real rerun, preserved baseline, mobile layout, ZIP link.')
  console.log('Screenshots: .cache/browser-live/')
} finally {
  socket?.close()
  chrome.kill()
  await new Promise(done => setTimeout(done, 500))
  await rm(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
}
