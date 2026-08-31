# `web/` — Mission-Driver Monitor (Vue 3 SPA)

The production frontend for the Mission-Driver Monitor Server. Replaces the legacy
single-file Alpine.js UI in [`../web/`](../web/README.md) (deprecated).

Built with **Vue 3 + TypeScript + Naive UI + Pinia + Vue Router**, bundling
**xterm.js** (log viewer) via Vite. Naive UI is imported **on-demand**
(`unplugin-vue-components` + `NaiveUiResolver`, no global `app.use(naive)`), so
unused components are tree-shaken out (first-screen JS gzip ≈198KB). Resource
monitoring is a plain Naive UI table (recent snapshots); ECharts was removed to
cut ~65MB of node_modules and a ~539KB lazy chunk.

## Quick start

### Development (hot reload)

Two processes: the Vite dev server (frontend, `:5173`) and the Monitor API server
(`:9300`). Vite proxies `/api` → `:9300` (see `vite.config.ts`).

```bash
# Terminal 1 — API/SSE only (static hosting OFF in dev mode)
node ../src/main.js --monitor --dev
#   or: MONITOR_DEV=1 node ../src/main.js --monitor

# Terminal 2 — Vite dev server with HMR
npm run dev
# open http://localhost:5173
```

In `--dev` mode the monitor serves **no static files** — `GET /` on `:9300` returns
a JSON hint (`{ error: "dev mode: static hosting disabled, run vite dev at :5173" }`).
All `/api/*` and SSE endpoints work normally.

### Production (static hosting)

```bash
# Build the SPA (runs vue-tsc --noEmit, then vite build → dist/)
npm run build

# Serve dist/ via the monitor (default — no --dev flag)
node ../src/main.js --monitor
#   open http://localhost:9300
```

In prod mode the monitor hosts `dist/index.html` and `/assets/*` with correct MIME
types and path-traversal protection. If `dist/` is missing at startup, the monitor
prints `[WARN] ... not found — serving API-only` and `GET /` degrades to a
placeholder page while all APIs keep working (FSD §8).

## Scripts

| Script | Description |
|--------|-------------|
| `npm run dev` | Vite dev server (HMR) on `:5173`. |
| `npm run build` | Type-check (`vue-tsc --noEmit`) + production build → `dist/`. |
| `npm run typecheck` | Type-check only (no emit). |
| `npm run preview` | Preview the production build locally. |

## Directory structure

```
src/
├── api/              REST client (6 endpoints) + SSE consumer
├── components/
│   ├── chart/        ResourceChart.vue        (recent-snapshots table + active processes)
│   ├── layout/       AppHeader.vue
│   ├── log/          LogViewer.vue            (xterm.js, ANSI color, search)
│   ├── roadmap/      RoadmapProgress.vue      (overall + per-phase badges)
│   └── run/          PlansTable.vue, MissionConfig.vue, StepTimeline.vue
├── composables/      useSSE.ts, useClock.ts
├── router/           index.ts                 (/ → RunList, /run/:runId → RunDetail)
├── stores/           mission.ts, sysmon.ts, config.ts (Pinia)
├── types/            run.ts, config.ts, sysmon.ts
├── views/            RunList.vue, RunDetail.vue
├── App.vue
└── main.ts
```

`dist/` is **committed to git** (clone-and-run: the monitor serves the prebuilt
`web/dist/` so consumers need zero install and zero build). If you change the
frontend, rebuild and commit `dist/` — CI (`.github/workflows/web-dist-check.yml`)
and `pnpm check:dist` verify the committed `dist/` matches the source.

## RoadmapProgress rendering decision

The Alpine.js baseline (FIX-3) renders roadmap progress as **per-phase status
badges** with a single overall text pill (`done/total done · NN%`); it deliberately
dropped the per-phase progress bars.

This Vue version keeps that badge model for phase rows, and adds **one overall
`n-progress` bar** driven by the backend's global `overallProgress` (which counts
work items only — milestones are excluded from the denominator).

**Why not per-phase progress bars (FSD §4.7 draft)?** The backend
`GET /api/configs/:name/roadmap` (`handleGetRoadmap` → `parseRoadmapMarkdown` in
`../src/monitor.js`) returns only `seq / name / status / isMilestone` per phase plus
the global `overallProgress`. It does **not** return per-phase `doneCount /
totalCount`, so there is no data to feed per-phase bars. The badge form is the
documented equivalence baseline; the single overall bar is the only addition.

This is a documented adjudication (roadmap item 4, Phase 1 Decision) and is
watch-only — it may be revisited if the roadmap API later returns per-phase counts.

## Migration & equivalence

This SPA is the functional successor to `../web/index.html` (Alpine.js). Every
feature was verified feature-by-feature against `../web/index.html.bak-2026-06-30`:

- Run list + run detail
- Live SSE updates (`step_started` / `step_completed` / `state_update` / `heartbeat` / `run_completed`)
- Step timeline (subflow nesting, suspend badge, sessionId copy)
- Log viewer (ANSI color, step switching, find next/previous, 3s auto-refresh of running step, load-more)
- Resource table (recent snapshots: time, free mem, opencode RSS/count, node count, pressure) + active processes
- Roadmap progress (overall pill + per-phase badges, milestone ★)
- Plans table
- Mission config collapse (with copy buttons)
- Error / empty / loading states

See the legacy [`../web/README.md`](../web/README.md) for rollback instructions.
