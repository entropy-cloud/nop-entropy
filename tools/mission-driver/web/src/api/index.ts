// API layer — typed fetch wrappers for the Monitor Server REST endpoints.
// Monitor Server: tools/mission-driver/src/monitor.js (port 9300 in dev).
// Base URL is relative (`/api/...`); the Vite dev proxy (vite.config.ts)
// forwards `/api` → http://localhost:9300, and in production the Monitor
// Server serves the built assets itself (FSD §3.5).

import type { Run, RunSummary, StepEvent } from '@/types/run'
import type { MissionConfig, MissionConfigInfo, PlanInfo, RoadmapData } from '@/types/config'
import type { SysmonSnapshot } from '@/types/sysmon'

// ── Transport response envelopes (FSD §6 + monitor.js handler outputs) ────

/** Available step log file, returned by GET /api/runs/:runId (handleGetRun). */
export interface StepLogInfo {
  step: string
  fileName: string
  sizeBytes: number
  type?: 'log' | 'prompt'
}

/** Full run detail returned by GET /api/runs/:runId. */
export interface RunDetail {
  run: Run
  events: StepEvent[]
  stepLogs: StepLogInfo[]
  config: MissionConfig | null
}

/** Log content returned by GET /api/runs/:runId/logs/:step. */
export interface LogData {
  step: string
  fileName: string
  filePath: string
  totalLines: number
  lines: string[]
  truncated: boolean
}

/** Optional query params for {@link getLog}. */
export interface GetLogOptions {
  /** Max number of trailing lines (server default 500). */
  tail?: number
  /** Number of lines to skip from the end (server default 0). */
  offset?: number
  /** Specific log file name (e.g. `oc-EXECUTE-1234-abcd.log`). */
  file?: string
  /** Content type: "log" (default) or "prompt" for the agent prompt text. */
  type?: 'log' | 'prompt'
}

// ── Core fetch helper ─────────────────────────────────────────────────────

/**
 * Typed GET wrapper. Throws on non-2xx with the response status + statusText
 * so callers can surface a meaningful error message (FSD §8 error handling).
 */
async function request<T>(url: string): Promise<T> {
  const res = await fetch(url)
  if (!res.ok) {
    const text = `${res.status} ${res.statusText || ''}`.trim()
    throw new Error(text || `HTTP ${res.status}`)
  }
  return (await res.json()) as T
}

/** Build a query string from a record, omitting undefined/null/empty values. */
function qs(params: Record<string, string | number | undefined | null>): string {
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue
    sp.set(k, String(v))
  }
  const s = sp.toString()
  return s ? `?${s}` : ''
}

// ── Endpoint wrappers (7 REST) ────────────────────────────────────────────

/** GET /api/runs?limit=&offset= → paged run summaries. */
export interface RunsPage {
  runs: RunSummary[]
  total?: number
  offset?: number
  limit?: number
  hasMore?: boolean
}

/** GET /api/runs?limit=&offset= → `{ runs, total, hasMore, ... }` */
export function getRuns(limit?: number, offset?: number): Promise<RunsPage> {
  return request<RunsPage>(`/api/runs${qs({ limit, offset })}`)
}

/** GET /api/runs/:runId → full run detail. */
export function getRun(runId: string): Promise<RunDetail> {
  return request<RunDetail>(`/api/runs/${encodeURIComponent(runId)}`)
}

/** GET /api/runs/:runId/logs/:step?tail=&offset=&file= → log content. */
export function getLog(
  runId: string,
  step: string,
  opts?: GetLogOptions,
): Promise<LogData> {
  const query = qs({
    tail: opts?.tail,
    offset: opts?.offset,
    file: opts?.file,
    type: opts?.type,
  })
  return request<LogData>(
    `/api/runs/${encodeURIComponent(runId)}/logs/${encodeURIComponent(step)}${query}`,
  )
}

/** GET /api/runs/:runId/logs/:step?type=prompt → agent prompt text.
 *  Convenience wrapper around {@link getLog} that injects `type: 'prompt'`. */
export function getPrompt(
  runId: string,
  step: string,
  opts?: Omit<GetLogOptions, 'type'>,
): Promise<LogData> {
  return getLog(runId, step, { ...opts, type: 'prompt' })
}

/** GET /api/runs/:runId/sysmon → `{ snapshots: SysmonSnapshot[] }` */
export function getSysmon(runId: string): Promise<{ snapshots: SysmonSnapshot[] }> {
  return request<{ snapshots: SysmonSnapshot[] }>(
    `/api/runs/${encodeURIComponent(runId)}/sysmon`,
  )
}

/** GET /api/configs?limit=&offset= → paged mission config summaries. */
export interface ConfigsPage {
  configs: MissionConfigInfo[]
  total?: number
  offset?: number
  limit?: number
  hasMore?: boolean
}

/** GET /api/configs?limit=&offset= → `{ configs, total, hasMore, ... }` */
export function getConfigs(limit?: number, offset?: number): Promise<ConfigsPage> {
  return request<ConfigsPage>(`/api/configs${qs({ limit, offset })}`)
}

/** GET /api/configs/:name/roadmap → roadmap phase data. */
export function getRoadmap(missionName: string): Promise<RoadmapData> {
  return request<RoadmapData>(`/api/configs/${encodeURIComponent(missionName)}/roadmap`)
}

/** GET /api/configs/:name/plans → `{ plans, plansDir }` */
export function getPlans(
  missionName: string,
): Promise<{ plans: PlanInfo[]; plansDir: string | null }> {
  return request<{ plans: PlanInfo[]; plansDir: string | null }>(
    `/api/configs/${encodeURIComponent(missionName)}/plans`,
  )
}

/** DELETE /api/runs/:runId → `{ ok: true, runId }` */
export async function deleteRun(runId: string): Promise<void> {
  const res = await fetch(`/api/runs/${encodeURIComponent(runId)}`, { method: 'DELETE' })
  if (!res.ok) {
    if (res.status === 409) throw new Error('Cannot delete a running mission')
    throw new Error(`${res.status} ${res.statusText || ''}`.trim())
  }
}

// ── Run launch (itp2-5 / FSD §6) ───────────────────────────────────────────

/** Target input for POST /api/runs (optional UI-injected item key). */
export interface RunTarget {
  key?: string
}

/** Start-run response from POST /api/runs. */
export interface StartRunResult {
  runId: string
  missionName: string
}

/**
 * POST /api/runs — launch a whitelisted mission. Optional `targets` override
 * the mission's static target list (written to {runDir}/input-targets.json and
 * read by the LOAD_TARGETS step). Returns the new runId; callers typically
 * navigate to `/runs/:runId` on success.
 */
export async function postRun(
  missionName: string,
  targets?: RunTarget[],
): Promise<StartRunResult> {
  const res = await fetch('/api/runs', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ missionName, targets }),
  })
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText || ''}`.trim()
    try {
      const body = await res.json()
      if (body?.error) msg = body.error
    } catch {
      // ignore body parse failure
    }
    throw new Error(msg)
  }
  return (await res.json()) as StartRunResult
}

// ── Mission Draft endpoints (mdo-2 / FSD §3.1A-C) ───────────────────────────

/** A draft job's persisted state (written by cmdDraftMission via draft-job.mjs). */
export interface DraftState {
  jobId?: string
  status: 'running' | 'completed' | 'failed' | string
  startedAt?: string | null
  endedAt?: string | null
  desc?: string | null
  phase?: string | null
  briefPath?: string | null
  flowHint?: string | null
  targetFile?: string | null
  missionName?: string | null
  roadmapPath?: string | null
  missionFile?: string | null
  error?: string | null
}

/** GET /api/missions/draft/:jobId response: state + draft.log tail. */
export interface DraftJobDetail {
  state: DraftState | null
  logTail: string | null
  jobId: string
}

/** A list entry from GET /api/missions/draft. */
export interface DraftJobSummary {
  jobId: string
  status: string
  startedAt: string | null
  desc: string | null
  mtime: number
}

/**
 * POST /api/missions/draft — start an async Mission Draft job. The server
 * spawns a detached `node main.js draft <desc>` child and returns its jobId;
 * poll with {@link getDraftJob}. Validates desc non-empty + ≤2KB server-side.
 *
 * mdo-4 P2: optional `opts` carries the wizard's flow selection, target file,
 * and skipBrief flag into the two-stage brief→draft pipeline.
 */
export async function postDraft(
  desc: string,
  opts?: { flowHint?: string; targetFile?: string; skipBrief?: boolean },
): Promise<{ jobId: string; pid?: number }> {
  const res = await fetch('/api/missions/draft', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ desc, ...opts }),
  })
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText || ''}`.trim()
    try {
      const body = await res.json()
      if (body?.error) msg = body.error
    } catch {
      // ignore body parse failure
    }
    throw new Error(msg)
  }
  return (await res.json()) as { jobId: string; pid?: number }
}

/** GET /api/missions/draft/:jobId → state + draft.log tail (poll target). */
export function getDraftJob(jobId: string): Promise<DraftJobDetail> {
  return request<DraftJobDetail>(
    `/api/missions/draft/${encodeURIComponent(jobId)}`,
  )
}

/** GET /api/missions/draft → recent draft jobs (newest first, default 9). */
export function getDraftJobs(): Promise<{ jobs: DraftJobSummary[] }> {
  return request<{ jobs: DraftJobSummary[] }>('/api/missions/draft')
}

// ── Flow dropdown + file browse (mdo-4 P2 unified wizard) ───────────────────

/** A flow definition entry from GET /api/flows (wizard dropdown source).
 *  Top-level flows only — subflows are filtered server-side. No `kind` tag: a
 *  flow defines itself by its name + steps. */
export interface FlowInfo {
  name: string
  entry: string | null
  stepCount: number
}

/** A directory/file entry from GET /api/browse (wizard target-file selector). */
export interface BrowseEntry {
  name: string
  isDir: boolean
  path: string
}

/** GET /api/flows → available flows for the wizard dropdown. */
export function getFlows(): Promise<{ flows: FlowInfo[] }> {
  return request<{ flows: FlowInfo[] }>('/api/flows')
}

/** GET /api/browse?prefix= → controlled directory listing for target-file pick. */
export function browse(prefix?: string): Promise<{ entries: BrowseEntry[] }> {
  return request<{ entries: BrowseEntry[] }>(`/api/browse${qs({ prefix })}`)
}

// ── Context Explorer endpoints (P6 / FSD §3.5) ─────────────────────────────

/** A single {{var}} placeholder in a prompt, with its provenance. */
export interface InjectionVar {
  name: string
  source: string
  runtime: boolean
}

/** A memory/context annotation derived from a step's prompt. */
export interface InjectionAnnotation {
  name: string
  source: string
}

/** A flow step's injection detail (from buildInjectionMap). */
export interface InjectionStep {
  name: string
  type: string | null
  isEntry: boolean
  promptPath: string | null
  promptVars: InjectionVar[]
  memoryBlocks: InjectionAnnotation[]
  contextFiles: InjectionAnnotation[]
  sourcePaths: boolean
  /** Present only on `type:"subflow"` steps: the referenced sub-flow name. */
  subflowName?: string
  /** Recursively-expanded subflow steps (mirrors InjectionStep shape). */
  substeps?: InjectionStep[]
  /** True when the referenced subflow file could not be found. */
  subflowMissing?: boolean
}

/** A directed transition edge derived from a flow step's transitions. */
export interface InjectionEdge {
  from: string
  to: string
  marker: string
  /** True when the transition is terminal (e.g. done:completed). */
  terminal?: boolean
  /** True when the edge is a retry (self-loop or back-edge gated by maxRetries). */
  retry?: boolean
  /** True for exceptional onError paths (rendered dashed). */
  dashed?: boolean
}

/** GET /api/flows/:name/injection-map → per-step {{var}} provenance. */
export interface InjectionMap {
  flowName: string
  entry: string | null
  steps: InjectionStep[]
  /** Top-level state-machine transitions (for the graph view). */
  edges: InjectionEdge[]
}

/** A reverse used-by entry for a prompt. */
export interface PromptUsedBy {
  flow: string
  step: string
}

/** A prompt summary from GET /api/prompts. */
export interface PromptSummary {
  name: string
  summary: string
  vars: string[]
  usedBy: PromptUsedBy[]
}

/** A single prompt's full text from GET /api/prompts/:name. */
export interface PromptDetail {
  name: string
  content: string
}

/** A file in a memory store. */
export interface MemoryFile {
  name: string
  sizeBytes: number
}

/** Parsed _index.md frontmatter. */
export interface MemoryIndexSummary {
  lessonCount?: number
  updated?: string
  raw?: string
}

/** A memory store from GET /api/memory. */
export interface MemoryStore {
  store: string
  dir: string
  exists: boolean
  files: MemoryFile[]
  indexSummary: MemoryIndexSummary | null
}

/** GET /api/flows/:name/injection-map → flow injection map. */
export function getInjectionMap(flowName: string): Promise<InjectionMap> {
  return request<InjectionMap>(
    `/api/flows/${encodeURIComponent(flowName)}/injection-map`,
  )
}

/** GET /api/prompts → prompt library with reverse used-by index. */
export function getPrompts(): Promise<{ prompts: PromptSummary[] }> {
  return request<{ prompts: PromptSummary[] }>('/api/prompts')
}

/** GET /api/prompts/:name → full prompt text. */
export function getPromptDetail(name: string): Promise<PromptDetail> {
  return request<PromptDetail>(`/api/prompts/${encodeURIComponent(name)}`)
}

/** GET /api/memory → self + per-module memory store inventory. */
export function getMemoryStores(): Promise<{ stores: MemoryStore[] }> {
  return request<{ stores: MemoryStore[] }>('/api/memory')
}

/** GET /api/memory/:store/:file → raw memory file text (Phase 3). */
export function getMemoryFile(store: string, file: string): Promise<{ content: string }> {
  return request<{ content: string }>(
    `/api/memory/${encodeURIComponent(store)}/${encodeURIComponent(file)}`,
  )
}

/** PUT /api/memory/:store/:file → atomic memory file write (Phase 3). */
export async function putMemoryFile(
  store: string,
  file: string,
  content: string,
): Promise<{ ok: boolean; warning?: string }> {
  const res = await fetch(
    `/api/memory/${encodeURIComponent(store)}/${encodeURIComponent(file)}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content }),
    },
  )
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText || ''}`.trim()
    try {
      const body = await res.json()
      if (body?.error) msg = body.error
    } catch {
      // ignore body parse failure
    }
    throw new Error(msg)
  }
  return (await res.json()) as { ok: boolean; warning?: string }
}
