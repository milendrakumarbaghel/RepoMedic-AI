import { spawn, spawnSync } from 'node:child_process'
import { cp, mkdir, readFile, rm, stat } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { generateDemoZip } from './demo-zip.mjs'

const root = fileURLToPath(new URL('../', import.meta.url))
const frontend = resolve(root, 'frontend')
const backend = resolve(root, 'backend')
const jar = resolve(backend, 'target/backend-0.0.1-SNAPSHOT.jar')
const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm'
const wrapper = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw'
const env = {
  ...process.env,
  MAVEN_USER_HOME: resolve(backend, '.m2'),
  npm_config_cache: resolve(root, '.cache/npm'),
}

function command(bin, args, cwd = root, overrides = {}) {
  console.log(`\n> ${bin} ${args.join(' ')}`)
  const result = spawnSync(bin, args, {
    cwd, env: { ...env, ...overrides }, stdio: 'inherit', timeout: 285_000,
  })
  if (result.error) throw new Error(`${bin}: ${result.error.message}`)
  if (result.status !== 0) throw new Error(`${bin} exited with ${result.status ?? result.signal}`)
}

async function checkRuntimes() {
  const expected = (await readFile(resolve(root, '.nvmrc'), 'utf8')).trim()
  if (process.versions.node !== expected) throw new Error(`Node ${expected} required; found ${process.versions.node}. Use your version manager or Docker.`)
  const java = spawnSync('java', ['-version'], { encoding: 'utf8', timeout: 10_000 })
  if (java.error || java.status !== 0) throw new Error('Java 25 is required. Set JAVA_HOME/PATH, or use Docker.')
  if (!/version "25(?:\.|\")/.test(java.stderr + java.stdout)) throw new Error('Java 25 is required by backend/pom.xml; use Docker for the pinned patch release.')
}

async function requireJar() {
  try { await stat(jar) }
  catch { throw new Error('No packaged application found. Run make setup && make build first.') }
}

async function runServices(development) {
  await requireJar()
  try { process.loadEnvFile(resolve(root, '.env')) }
  catch (error) { if (error.code !== 'ENOENT') throw error }
  const children = [spawn('java', ['-jar', jar], { cwd: root, env: process.env, stdio: 'inherit' })]
  if (development) children.push(spawn(npm, ['run', 'dev'], { cwd: frontend, env: process.env, stdio: 'inherit' }))
  console.log(development ? 'UI: http://localhost:5173 · API: http://localhost:8080' : `RepoMedic: http://localhost:${process.env.PORT || 8080}`)
  let stopping = false
  const stop = () => { stopping = true; children.forEach(child => child.kill('SIGTERM')) }
  process.on('SIGINT', stop)
  process.on('SIGTERM', stop)
  await Promise.all(children.map(child => new Promise((done, reject) => {
    child.on('error', error => { stop(); reject(error) })
    child.on('exit', code => {
      const normal = stopping || code === 0
      stop()
      normal ? done() : reject(new Error(`Service exited with code ${code}`))
    })
  })))
}

try {
  await checkRuntimes()
  switch (process.argv[2]) {
    case 'setup':
      command(npm, ['ci', '--no-audit', '--no-fund'], frontend)
      command(wrapper, ['-B', 'package'], backend, { AI_API_URL: '', AI_API_KEY: '', AI_MODEL: '', REPOMEDIC_SANDBOX_ENABLED: 'false' })
      break
    case 'test':
      command(npm, ['test', '--', '--run'], frontend)
      command(npm, ['run', 'lint'], frontend)
      command(npm, ['exec', '--offline', '--', 'tsc', '--project', 'tsconfig.test.json'], frontend)
      command(npm, ['run', 'build'], frontend)
      command(wrapper, ['-B', '-o', 'test'], backend, { AI_API_URL: '', AI_API_KEY: '', AI_MODEL: '', REPOMEDIC_SANDBOX_ENABLED: 'false' })
      break
    case 'build': {
      await generateDemoZip()
      command(npm, ['run', 'build'], frontend)
      const destination = resolve(backend, 'src/main/resources/static')
      await rm(destination, { recursive: true, force: true })
      await mkdir(destination, { recursive: true })
      await cp(resolve(frontend, 'dist'), destination, { recursive: true })
      command(wrapper, ['-B', '-o', 'package'], backend, { AI_API_URL: '', AI_API_KEY: '', AI_MODEL: '', REPOMEDIC_SANDBOX_ENABLED: 'false' })
      console.log('\nBuilt backend/target/backend-0.0.1-SNAPSHOT.jar (UI + API).')
      break
    }
    case 'run': await runServices(false); break
    case 'dev': await runServices(true); break
    default: throw new Error('Usage: node scripts/project.mjs setup|test|build|run|dev')
  }
} catch (error) {
  console.error(`\nRepoMedic: ${error.message}`)
  process.exitCode = 1
}
