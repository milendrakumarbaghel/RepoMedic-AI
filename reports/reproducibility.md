# Rebuild and verification record

Verified on October 20, 2026. This report contains only project configuration
and evidence from the public bundled fixture, not private repository payloads.

## Rebuildability

| Requirement | Implementation / evidence |
| --- | --- |
| Git repository and commits | Existing GitHub `origin`; implementation and build changes committed locally |
| Dependency lockfile | `frontend/package-lock.json`; backend BOM 4.1.1 and explicit Commons Compress 1.28.0 |
| Runtime pins | `.nvmrc`: 23.10.0; `.java-version`: 25.0.4.1; Docker multi-platform images pinned by SHA-256 |
| Maven pin | Wrapper 3.10.0, downloaded distribution verified by SHA-256 |
| Linux container | Root `Dockerfile` builds and serves React assets with the Spring Boot API |
| One offline check | `make test`, after initial online `make setup` |
| Portable setup/run/test instructions | Root `README.md`, `Makefile`, and portable Node scripts |
| Example settings, no secrets | Root `.env.example`; private config, caches, and workspaces excluded from Git/container context |
| No developer paths needed | Scripts derive the repository root; source/report scans found no developer-machine absolute paths |

## Commands actually executed

| Command / check | Actual result |
| --- | --- |
| `make setup` | Passed: locked npm install, checksum-verified project-local Maven wrapper, JAR package |
| `make test` | Passed: 20 frontend + 47 backend tests, frontend lint, both TypeScript checks, production frontend build |
| `make build` | Passed: combined executable `backend/target/backend-0.0.1-SNAPSHOT.jar` |
| `make smoke` | Passed against an actual packaged Java server, not a mocked API |
| `docker build -t repomedic-ai:local .` | Passed from a context excluding host caches, node_modules, build output, and private data |
| Dockerfile offline layer | `RUN --network=none make test && make build && make smoke` passed in Linux |
| `docker build --target test -t repomedic-ai:test .` | Passed |
| `docker run --rm --network none repomedic-ai:test make test` | Passed all 67 tests, lint, typechecks, and frontend build without network |
| `docker compose config --quiet` | Passed configuration validation |
| Non-root runtime with read-only root, writable temporary/data mounts, dropped capabilities | Started; image health check reached healthy |
| Live Chrome browser check (`scripts/browser-live.mjs`) | Passed desktop/mobile layouts, real demo API, patch preview, real rerun, baseline preservation, upload ZIP link |
| Public GitHub import: `octocat/Hello-World` | HTTP 200, actual default branch `master`, static-only analysis, tests honestly blocked |
| `git diff --check` | Passed before implementation commit; repeated for final build/docs commit |

## Demo evidence

Both **Try broken React demo** and uploading the generated
`examples/broken-react.zip` exercise real Node processes:

| Stage | Passed | Failed | Total |
| --- | ---: | ---: | ---: |
| Original baseline | 3 | 3 | 6 |
| After source-only percentage fix | 6 | 0 | 6 |

The upload retains `source: upload`; all four files must match the audited
fixture exactly, and the match is rechecked before execution. The proposal is
labelled `rule-based`; no external LLM is called for the demo. Downloaded unified
diff applicability was checked with `git apply --check`. Failed validation,
tampered fixtures, malicious extra files, ZIP traversal, provider-output bounds,
and persistence/restart are covered by the backend tests.

The public report is `reports/demo-diagnostic.md`; the exact suggested change is
`reports/demo-fix.patch`. Reports retain the actual baseline and latest TAP logs;
workspace prefixes are stripped from trusted-runner logs.

## Real limits, not passing checks

- No live AI-provider request was made: credentials were intentionally not
  configured. Provider parsing and unsafe-output rejection were tested locally.
- Arbitrary uploaded React/Vitest/Jest/Spring Boot suites were not claimed as
  verified. Ordinary uploads default to static-only; the optional imported-code
  Docker runner was not exercised against a live third-party project.
- The offline Maven sandbox currently has no dependency-cache seeding mechanism.
  Typical Spring Boot uploads will report blocked dependencies, as documented.
- Local setup initially encountered an environment-provided `wget` shim with no
  real executable. The wrapper now prefers available `curl`; setup then passed.
- npm printed an engine warning for a transitive `eslint-visitor-keys` package
  under the retained Node 23 runtime, and dependency deprecation notices. All
  configured checks passed on that exact runtime locally and in Linux. This is
  a buildathon runtime pin, not a claim of a fully maintained production stack.
- The deployed app is single-user/local-only, without authentication. No
  multi-tenant production-security claim is made.
- Commits are local; no push was requested or performed.
