# RepoMedic AI backend

Java **25**, Spring Boot **4.1.1**, Maven wrapper **3.10.0**. No database,
Lombok, npm installation, or credentials are required. Node **23.10.0** must be
on `PATH` to run the real bundled demo and the demo integration tests.

## Setup, run, and test

Run these commands from `backend/`:

```sh
./mvnw test
./mvnw spring-boot:run
```

Maven dependencies are kept in `backend/.m2/repository` by `.mvn/maven.config`.
The first build downloads dependencies. Once populated, `./mvnw -o test` runs
offline; the tests do not contact GitHub or an external AI provider. Tests use
generated workspaces under `target/`.

Package and launch:

```sh
./mvnw package
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

The main project build may copy frontend `dist/` contents into
`backend/src/main/resources/static/` **before packaging**. Spring serves those
assets, and the common dashboard/history/analysis routes forward to
`index.html`. Root container, frontend-build, and deployment configuration are
maintained by the main project build.

## Environment

| Variable | Default | Meaning |
| --- | --- | --- |
| `PORT` | `8080` | HTTP listener |
| `REPOMEDIC_DATA_DIR` | `.repomedic` | Private workspace, history, and multipart temporary data root, relative to the process working directory |
| `REPOMEDIC_SANDBOX_ENABLED` | `false` | Explicitly opt in to the Docker CLI runner for uploaded/imported code |
| `AI_API_URL` | empty | Exact chat-completions endpoint |
| `AI_API_KEY` | empty | Bearer token; never written to analysis data or child-process environments |
| `AI_MODEL` | empty | Model identifier |

The app reads environment/configuration; it does not rewrite it or implicitly
load `.env`. All three AI values must be explicitly set before an outbound AI
request is possible. `/api/health` and each analysis expose configuration via
`aiEnabled`; events identify whether a provider was actually used. The bundled
demo and content-verified uploaded copies always use the audited rule-based
fix and never call GitHub or AI.

## API

All analysis endpoints return the complete contract DTO. Creation and
validation are synchronous. History is newest-first, is persisted as atomic
JSON snapshots, and is capped at 50 analyses, with older private directories
pruned. Operations on the same analysis are serialized; repeated successful
validation is idempotent. Run one backend process per data directory.

| Method | Endpoint | Input / output |
| --- | --- | --- |
| GET | `/api/health` | `{status:"ok", aiEnabled:boolean}` |
| GET | `/api/analyses` | Analysis array |
| POST | `/api/analyses/demo` | New bundled demo analysis |
| POST | `/api/analyses/upload` | Multipart `file`, ZIP |
| POST | `/api/analyses/import` | JSON `{ "url": "https://github.com/owner/repo" }` |
| GET | `/api/analyses/{id}` | Saved analysis |
| POST | `/api/analyses/{id}/validate` | Latest analysis and validation logs |
| GET | `/api/analyses/{id}/report` | Markdown attachment, including baseline and latest logs |
| GET | `/api/analyses/{id}/patch` | Plain-text unified diff attachment |

Invalid requests receive `{status,code,message,timestamp}` JSON with meaningful
400/404/409/413/415/429/502/503 responses. An empty patch download means there
are no eligible suggestions; rejected patches are not exported.

## Analysis and execution

The bundled React cart has six dependency-free `node:test` cases. Initially
three fail because the discount percentage is multiplied as a whole number.
The exact rule-based replacement divides it by 100. Validation applies source
patches, runs real Node tests, and accepts only a nonzero passing test run with
at least one passed test. Otherwise it restores all original source files.
Baseline counts/output are immutable; latest output records the validation
attempt. Historical findings continue to describe the original source after
validation.

An uploaded ZIP is recognized as the trusted audited fixture **only** when its
extracted workspace contains exactly these four shipped files at its root:
`package.json`, `cart.js`, `cart.test.js`, and `app.jsx`. Every byte must match
the bundle; only `cart.js` may instead contain the one exact audited arithmetic
fix. Extra files/directories, modified tests/manifests/UI code, symlinks, or any
other byte changes disqualify it. ZIP entries omitted by the existing
credential/generated-file filters are not part of the extracted workspace.
The runner rechecks all four file bytes and absence of extras immediately
before **every** host execution, including after patches are applied. A fresh
check also gates validation and bypasses AI suggestions for matching uploads.
Neither ZIP/repository names, request flags, previous successful checks, nor
persisted `executionMode` values grant trust. Recognition is recorded as an
explicit event and only then sets `executionMode: "trusted-demo-node"`; the
analysis keeps `source: "upload"` and its repository name. The host path is
limited to `demo` and exact matching `upload` content; GitHub imports are never
eligible, even if their bytes match. AI changes cannot enter this audited host
execution path.

**Untrusted uploads and all GitHub imports never execute on the host.** They
are static-only by default, with zero test counts and explicit `blocked` test
status. Validation leaves their suggestions/workspaces intact when sandboxing
is disabled. Blocked analyses use `issues_found`, even with no static findings;
their summary explicitly says `Unverified static-only.` when applicable.
`healthy` requires no static findings and a real passing test result.
Static checks cover debugger statements, loose equality, React raw HTML
injection sinks, Java console output, and the exact percentage bug. Only
deterministic source edits become rule-based patches; coercion/HTML/logging
findings require human review. Small relevant snippets may be sent to an
explicitly configured AI provider. Its response must name an existing source
path and a unique exact replacement; tests and manifests cannot be patched.
Provider failure or rejected output produces a visible warning and preserves
rule-based fallback. AI is an explicit opt-in disclosure of relevant source
snippets to the configured provider: filename exclusions and size bounds are
not source-secret redaction. Secret literals embedded in otherwise ordinary
source may be present in those snippets. Configure AI only for repositories
whose selected source may be shared with that provider; leaving any AI setting
empty disables these requests. Audited demo/fixture uploads bypass AI entirely.

ZIP limits: 20 MiB compressed, 50 MiB expanded, at most 2000 files; archive
paths, duplicates, CRCs, special files, and symlinks are checked. Git metadata,
credential filenames, `.env*`, dependencies, and generated output are omitted.
Archives and multipart temporary files stay within the configured data root.
GitHub imports only accept public HTTPS owner/repository URLs, use fixed
`api.github.com` and `codeload.github.com` hosts, fetch the actual default branch,
and reject redirects with bounded requests/downloads.

The optional Docker runner uses fixed `node:23.10.0-bookworm-slim` or
`maven:3.9.11-eclipse-temurin-25` images, `--pull=never`, no network, a read-only
root/source mount, dropped capabilities, no-new-privileges, a non-root user,
512 MiB memory, one CPU, 128 PIDs, 128 KiB retained output, and a 45-second
deadline. Only `node --test --test-reporter=tap` or `mvn -o
-Dmaven.repo.local=/tmp/m2 test` is invoked; no npm scripts or dependency
installation are run. The current Maven command selects a fresh `/tmp/m2`
inside the container's empty tmpfs; the stock Maven image cannot resolve a
normal project's plugins/dependencies there offline. Optional Java execution
therefore remains **blocked** for those projects. Real imported Maven tests
would require future support for a preseeded custom image that populates the
selected repository before the fixed test command; such images/cache seeding
are not built or configured here. Maven's last test-summary line is treated as
the final aggregate when an actual summary is produced. Missing offline
dependencies, missing images/daemon, missing Node modules, and absent test
summaries produce honest `blocked` results. There is no host fallback for
untrusted code and no application-managed Docker socket mount.

Live Docker/GitHub/AI access is optional and is not required by the offline
test suite. The suite exercises real demo and exact uploaded-fixture execution,
HTTP contracts/downloads, ZIP safety/limits, malicious lookalike blocking,
content re-verification before validation and after patching, rollback,
concurrent validation, restart/history pruning, bounded/no-redirect HTTP,
AI bypass for audited fixtures, and AI output validation.
