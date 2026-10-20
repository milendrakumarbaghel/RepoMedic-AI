# RepoMedic AI frontend

A responsive, evidence-first repository health workspace built with the existing React 19, Vite 8, and TypeScript 6 stack. System fonts, custom SVG/CSS illustration, Tailwind CSS 4.3.3, and lucide-react 1.60.0. No external image or font requests.

## Setup and development

Run these commands from `frontend/` with Node **23.10.0** (also recorded in `.nvmrc`):

```sh
npm ci
npm run dev
```

Vite listens on `0.0.0.0:5173` with `strictPort: true`. Start the separate Java backend on port 8080. All browser API requests use `/api`; Vite proxies them to `http://localhost:8080`. To point the development proxy elsewhere:

```sh
VITE_API_PROXY_TARGET=http://localhost:8081 npm run dev
```

The app retrieves health and existing history on startup. It **never creates a demo automatically**. Click **Try broken React demo** to run the backend demo, import a public GitHub repository, or upload/drop a nonempty ZIP. Backend errors are visible and dismissible.

## Test and verify

```sh
npm test -- --run
npm run lint
npm run build
```

`make test` is the single offline test command after `npm ci`. `make check` runs tests, lint, and the production build. Tests use Node's built-in test runner, TSX, JSDOM, and React Testing Library, with mocked network boundaries. Vitest 5.0.5 was not selected because its supported Node engine range excludes the required Node 23. Tests cover API request shapes, downloads, errors, empty states, real demo and validation transitions, blocked tests, baseline preservation, history, duplicate submissions, import, keyboard dialogs/tabs, and ZIP selection/drop/reset.

An optional browser smoke test uses an already-installed Chrome/Chromium and an isolated in-process API fixture. It exercises desktop/mobile layouts, demo/validation transitions, baseline output, dialogs, and navigation without modifying the real backend:

```sh
CHROME_BIN="/path/to/chrome" npm run test:browser
```

Screenshots are written to `node_modules/.cache/repomedic-smoke/`. No browser is downloaded. Test source can additionally be typechecked with `npx tsc --project tsconfig.test.json`.

## Build and deploy with the Java application

```sh
npm ci
npm run build
```

The output is `frontend/dist/`. Copy its **contents** into the Java application's static resources directory using the parent project's deployment workflow. Serve the assets and Java `/api` endpoints on the same origin. No API base URL needs to be embedded at build time. `npm run preview` serves only the built UI for asset inspection; it is not an API proxy.

## Standalone container option

```sh
docker build -t repomedic-frontend .
docker network create repomedic
# Start the separately built Java backend on this network with network alias "backend".
docker run --rm --network repomedic -p 5173:80 repomedic-frontend
```

The Dockerfile installs locked dependencies, runs all checks, builds the assets, and serves them with Nginx. `nginx.conf` proxies `/api` to the Docker-network service `backend:8080`. Configure the network alias on your backend container; no backend files are managed here.

## Interface behavior

- The first screen has clearly labeled illustrative onboarding and em-dash statistics until a real analysis exists.
- History selects the latest existing analysis; choosing another diagnosis retrieves its detail from the API.
- Findings, patches, tests, execution mode, activity, and provider attribution come from the selected analysis. Deterministic/rule-based providers are labeled **Rule-based**.
- **Apply patch & rerun tests** posts to the selected analysis's validation endpoint. The original `baselineTests` remains visible beside the latest output; blocked execution is never presented as a passing run.
- Download buttons retrieve the real Markdown report and unified diff.
- Reports opens the history library. Settings shows live engine/AI status and a connection check. Help explains workflow and backend-dependent data handling.
- Dialogs trap focus, close with Escape when idle, and restore focus. Tabs support arrow keys. Operations disable duplicate submissions. ZIP inputs reset to allow selecting the same file again.

Only files inside `frontend/` are owned by this implementation.
