# RepoMedic AI

**Find the failure. Understand the code. Verify the fix.**

A repository doctor built with **React 19, TypeScript, Tailwind CSS 4, and
Spring Boot 4.1.1 / Java 25**. Import a public GitHub repository, upload a ZIP,
or use the deliberately broken React cart. Review line-referenced findings,
side-by-side source changes, real test output, and a downloadable Markdown report.

## Submission demo

**Demo video:** [Watch the 42-second recording](https://github.com/milendrakumarbaghel/RepoMedic-AI/raw/refs/heads/main/assets/repomedic-demo.mov)

Direct video URL for submission scanners:
`https://github.com/milendrakumarbaghel/RepoMedic-AI/raw/refs/heads/main/assets/repomedic-demo.mov`

Repository copy: [`assets/repomedic-demo.mov`](./assets/repomedic-demo.mov)

The recording uses the real packaged application and shows the complete winning
flow: launch the broken React diagnosis, inspect the failing test evidence,
review the line-level side-by-side patch, apply it, rerun the tests, and compare
the preserved 3-failing baseline with the final 6-passing run. It ends by opening
the ZIP upload flow. Re-record it locally with `make record-demo` while the
packaged app is running on port 8080. The script uses an already installed
Chrome/Chromium and macOS screen capture; no browser or third-party service is
downloaded.

**Live link:** this project is intentionally packaged for local/self-hosted use;
there is no hosted public deployment. Run the Docker command below and open
`http://localhost:8080`.

## Quick start — clean Linux, Docker only

Requirements: Git, Docker Engine with BuildKit, and optionally Docker Compose v2.
The initial build needs internet access to download pinned images/dependencies.
No host Node, Java, Maven, database, or AI account is required with Docker.

```sh
git clone https://github.com/milendrakumarbaghel/RepoMedic-AI.git
cd RepoMedic-AI
docker build -t repomedic-ai:local .
docker run --rm --name repomedic -p 127.0.0.1:8080:8080 \
  -v repomedic-data:/app/.repomedic repomedic-ai:local
```

Open **http://localhost:8080**. The image builds the UI and API together, runs the
full offline test command after dependency setup, and smoke-tests the packaged
application. Runtime is non-root and includes Node for the audited demo.

Run the same tests explicitly inside the prepared Linux image:

```sh
docker build --target test -t repomedic-ai:test .
docker run --rm --network none repomedic-ai:test make test
```

Or use Compose for persistent data, health checks, a read-only root filesystem,
and local-only publishing:

```sh
cp .env.example .env       # Optional; defaults work without credentials.
docker compose up --build -d
docker compose logs -f
docker compose down        # Retains the named data volume.
```

## The 60-second demo

1. Click **Try broken React demo**, or choose **Upload ZIP → Download demo ZIP**
   and upload the downloaded `broken-react.zip`.
2. Inspect the real baseline: **3 passed, 3 failed**, out of six tests.
3. Open **Findings**. `cart.js` calculates a percentage as a whole number. The
   finding includes the actual source line and an explanation.
4. Open **Patch preview**. The minimal edit changes
   `subtotal * discountPercent` to `subtotal * (discountPercent / 100)`.
5. Click **Apply patch & rerun tests**. The actual Node test process runs again:
   **6 passed, 0 failed**. The original baseline remains visible.
6. Download the Markdown report and applicable unified diff. The fix is labelled
   **Rule-based**, not falsely presented as a model response.

The ZIP is generated from the exact four bundled demo resources. The backend
verifies every byte and file before allowing this audited fixture to execute on
the host. Changing its tests or adding files removes that trust. No arbitrary
uploaded code executes on the host.

Generate the archive locally with `make demo-zip`; output:
`examples/broken-react.zip`. A real example report and patch are in
`reports/demo-diagnostic.md` and `reports/demo-fix.patch`.

## Local development

Install/select **Node 23.10.0** and **JDK 25** before running setup. The exact
container JDK is **25.0.4.1**. These retain the provided starter's runtime stack;
version files are `.nvmrc` and `.java-version`. Maven **3.10.0** is downloaded by
the checked-in wrapper and verified with SHA-256. A system Maven is not needed.
Local prerequisites also include Make, a POSIX shell, curl or wget, and unzip.
For an entirely provisioned environment, use the Docker instructions above.

From the repository root:

```sh
make setup       # npm ci + Maven package; first-time dependency download
make test        # Offline tests, lint, typechecks, frontend build, backend tests
make build       # Offline combined UI + Spring Boot executable JAR
make run         # http://localhost:8080
```

For frontend hot reload, after `make build`:

```sh
make dev         # React http://localhost:5173 + packaged API :8080
```

Ctrl-C stops both services. Backend changes require another `make build` and
restart. `make run` / `make dev` optionally load a local `.env`; plain Java only
reads exported variables. Use a new terminal to run the live packaged-app check:

```sh
make smoke       # Starts its own temporary server; real demo + ZIP upload tests
```

`make smoke` uses an isolated temporary data directory, cleans it up, and writes
only the public-demo report and diff into `reports/`. The main offline command
is **`make test`**; no GitHub, model provider, browser download, or Docker daemon
is required after `make setup`. Project caches stay inside `frontend/node_modules`,
`backend/.m2`, and `.cache`; none are committed.

An optional live-browser check uses an already installed Chrome/Chromium:

```sh
CHROME_BIN="$(command -v chromium)" node scripts/browser-live.mjs
```

Run it against the running app on port 8080 (or set `REPOMEDIC_URL`). It creates
one bundled demo, validates the real fix through the browser, checks mobile
layout, and writes screenshots under `.cache/browser-live/`. No browser is
downloaded. Actual verification results and limits are in
`reports/reproducibility.md`.

## What is implemented

- Public GitHub default-branch import and ZIP upload, with persistent recent history.
- Bounded static source checks for JavaScript/TypeScript/React and Java, including
  debugger statements, loose equality, raw-HTML sinks, and console logging.
- Exact file/line explanations and source-only patch suggestions.
- Real baseline and rerun evidence for the audited demo; failed patch attempts
  restore original source. Empty/blocked test runs never count as validation.
- Optional, explicitly configured AI patch advice with provider attribution.
- Optional Docker execution of imported Node tests or offline Maven tests.
- Markdown diagnostics, unified diffs, timestamps, activity, and original test logs.
- Responsive UI with keyboard-accessible tabs, dialogs, error and blocked states.

### Honest scope and safety limits

The default analyzer is a small deterministic rule set, **not a full compiler,
ESLint, security scanner, or general-purpose autonomous repair agent**. Its
findings need review. Compiler/linter errors present in an executed test process
are preserved in the output; arbitrary project-defined lint scripts are not run.
ZIPs have no Git history, and GitHub imports use source archives: this version
does **not** identify a culprit commit or implement `git bisect`.

Ordinary uploads/imports are **static-only by default** and show tests as
**blocked**, not passing. The only host-execution exception is the exact audited
fixture described above, verified afresh before each process launch. Other test
suites require the explicitly enabled Docker runner. No npm install or arbitrary
npm script is executed for imported projects. React/Vitest/Jest projects with
missing dependencies will be blocked, not fabricated as successful.

The optional Maven runner has no preseeded dependency cache and no network; a
normal Spring Boot upload will therefore report blocked dependencies. Preseeded
Java runner support is a future extension. Spring Boot powers this app's backend;
the completely verified repair demonstration is the React cart.

This is a **single-user, local-workspace application**, with no authentication or
tenant isolation. Keep the listener on loopback and do not publish it directly
to the internet. Containers are defense-in-depth, not a sufficient boundary for
hostile multi-tenant execution. No Docker socket is mounted in the supplied
deployment. History/workspaces are private local data; at most 50 analyses are
retained and older ones are pruned. Use one process per data directory.

## Configuration

All settings are documented in **`.env.example`**; `.env` and private workspaces
are ignored by both Git and the Docker build context. No credential is required
for the complete demo. Never place real keys in tracked files.

| Setting | Default | Purpose |
| --- | --- | --- |
| `PORT` | `8080` | Local listener / Compose published host port |
| `BIND_ADDRESS` | `127.0.0.1` | Local Java bind address; image uses `0.0.0.0` internally |
| `REPOMEDIC_DATA_DIR` | `.repomedic` | Private workspaces and persisted analysis metadata |
| `REPOMEDIC_SANDBOX_ENABLED` | `false` | Explicit imported-project Docker-runner opt-in |
| `AI_API_URL` | empty | Exact chat-completions-compatible endpoint |
| `AI_API_KEY` | empty | Bearer token, server-side only |
| `AI_MODEL` | empty | Your provider's model identifier |
| `VITE_API_PROXY_TARGET` | `http://localhost:8080` | Development proxy only |

AI advice is enabled only when all three `AI_*` values are set. Relevant bounded
source snippets then leave the workspace for the configured provider. Snippets
can contain sensitive source literals: obtain permission before using private
code. Credential filenames are excluded, but this is not a general secret
scanner. Provider errors are redacted; invalid or unsafe-path responses are
rejected. Tests and manifests cannot be patched. AI output remains untrusted
and requires sandbox validation; the audited demo never calls a model.

### Optional imported Node-test sandbox

For trusted local experimentation only, run the Java application on a host with
Docker CLI/daemon access. Pull the fixed image explicitly before starting:

```sh
docker pull node:23.10.0-bookworm-slim
REPOMEDIC_SANDBOX_ENABLED=true make run
```

The runner invokes only `node --test --test-reporter=tap`, with no network, a
read-only root/source mount, non-root user, dropped capabilities, memory/CPU/PID
limits, capped logs, and a deadline. Missing images/daemon/dependencies become
blocked results. There is no unsafe host fallback. Compose intentionally forces
this option off and does not install Docker CLI or mount the host socket.

## Structure and deployment

```text
frontend/       React + Tailwind dashboard, package-lock.json, 20 frontend tests
backend/        Spring Boot API, Maven wrapper, backend/security tests
scripts/        Portable setup/test/build and real packaged-app smoke checks
examples/       Demo ZIP instructions (ZIP is generated, not hand-maintained)
reports/        Public demo diagnostic report and applicable patch
Dockerfile      Pinned multi-stage build, offline tests, non-root combined runtime
compose.yaml    Local deployment with persistent named volume
Makefile        One entrypoint for setup, tests, build, run, smoke, Docker
```

`make build` produces **`backend/target/backend-0.0.1-SNAPSHOT.jar`**, including
the built frontend and downloadable demo. Run with `java -jar` plus Node on PATH,
or deploy the combined Docker image to a Linux Docker host. Publish only the
internal **8080** port behind an authenticated TLS proxy if leaving loopback.
Persist `/app/.repomedic`; never bake uploaded repositories or `.env` into an
image. The image health endpoint is `/api/health`.

API and backend test details: `backend/README.md`. Frontend behavior, optional
local-browser smoke test, and its independent commands: `frontend/README.md`.

## Rebuildability checklist

- [x] Git repository with commits; supplied GitHub URL configured as `origin`.
- [x] npm dependency lockfile; exact Spring Boot BOM and wrapper version.
- [x] Node and Java version files; both container runtimes pinned by image digest.
- [x] Dockerfile installs required system packages and builds/runs UI plus API.
- [x] One offline test command: `make test` after initial `make setup`.
- [x] Exact setup, build, run, test, demo, and deploy instructions.
- [x] `.env.example`, no committed secrets or private repository payloads.
- [x] Workspace-relative paths; no developer-machine paths needed to rebuild.
