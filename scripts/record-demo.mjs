import { mkdir, mkdtemp, rm } from 'node:fs/promises'
import { spawn } from 'node:child_process'
import { resolve } from 'node:path'

const root = resolve(new URL('..', import.meta.url).pathname)
const chromePath = process.env.CHROME_BIN || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const base = process.env.REPOMEDIC_URL || 'http://localhost:8080'
const port = 9225
const output = resolve(root, 'assets/repomedic-demo.mov')
const profile = await mkdtemp(resolve(root, '.cache/record-profile-'))
await mkdir(resolve(root, 'assets'), { recursive: true })
await mkdir(resolve(root, '.cache'), { recursive: true })

function wait(ms) { return new Promise(done => setTimeout(done, ms)) }
async function waitForUrl() {
  for (let attempt = 0; attempt < 80; attempt++) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/json/list`)
      const targets = await response.json()
      if (targets.some(target => target.type === 'page')) return targets.find(target => target.type === 'page')
    } catch { /* Chrome is still starting. */ }
    await wait(250)
  }
  throw new Error('Chrome remote debugging did not become available.')
}

const chrome = spawn(chromePath, [
  `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`,
  '--no-first-run', '--no-default-browser-check', '--disable-extensions',
  '--window-size=1400,1000', '--window-position=20,20', base,
], { stdio: 'ignore' })
let socket
let recording

try {
  const target = await waitForUrl()
  socket = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((resolvePromise, reject) => {
    socket.addEventListener('open', resolvePromise, { once: true })
    socket.addEventListener('error', reject, { once: true })
  })
  let sequence = 0
  const pending = new Map()
  socket.addEventListener('message', event => {
    const message = JSON.parse(String(event.data))
    if (!message.id) return
    const request = pending.get(message.id)
    pending.delete(message.id)
    if (message.error) request?.reject(new Error(message.error.message))
    else request?.resolve(message.result)
  })
  const send = (method, params = {}) => new Promise((resolvePromise, reject) => {
    const id = ++sequence
    pending.set(id, { resolve: resolvePromise, reject })
    socket.send(JSON.stringify({ id, method, params }))
  })
  const evaluate = async expression => {
    const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.text)
    return result.result.value
  }
  const waitFor = async expression => {
    for (let attempt = 0; attempt < 120; attempt++) {
      if (await evaluate(expression)) return
      await wait(100)
    }
    throw new Error(`Timed out waiting for: ${expression}`)
  }
  const click = label => evaluate(`Array.from(document.querySelectorAll('button')).find(button => button.textContent.trim() === ${JSON.stringify(label)})?.click()`)

  await send('Page.enable')
  await waitFor(`document.querySelector('.engine-status')?.textContent.includes('connected')`)
  await new Promise(resolvePromise => {
    recording = spawn('screencapture', ['-v', '-D1', '-V42', '-R0,0,1440,1100', '-x', '-k', output], { stdio: 'ignore' })
    recording.once('spawn', resolvePromise)
  })
  await wait(2500)
  await evaluate(`Array.from(document.querySelectorAll('button')).find(button => ['Try broken React demo', 'Run demo'].includes(button.textContent.trim()))?.click()`)
  await waitFor(`document.querySelector('.finding-title')?.textContent.includes('percentage') && !document.querySelector('.operation-banner')`)
  await wait(2200)
  await click('Patch preview')
  await waitFor(`document.querySelector('.code-pane.after')?.textContent.includes('discountPercent / 100')`)
  await wait(3500)
  await click('Apply patch & rerun tests')
  await waitFor(`document.querySelector('.stat-card.tests strong')?.textContent === '100%'`)
  await wait(2600)
  await click('Test output')
  await wait(4200)
  await click('New diagnosis')
  await waitFor(`!!document.querySelector('[role="dialog"]')`)
  await evaluate(`document.querySelector('[role="dialog"] [role="tab"][id$="zip-tab"]')?.click()`)
  await wait(2200)
  await evaluate(`document.querySelector('[role="dialog"] [aria-label="Close dialog"]')?.click()`)
  if (recording) await new Promise((resolvePromise, reject) => {
    recording.once('exit', code => code === 0 ? resolvePromise() : reject(new Error(`screencapture exited with ${code}`)))
    recording.once('error', reject)
  })
  console.log(`Recorded ${output}`)
} finally {
  socket?.close()
  chrome.kill()
  if (recording && recording.exitCode === null) recording.kill('SIGTERM')
  await wait(500)
  await rm(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
}
