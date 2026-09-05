// Pinia store — mission run state + SSE event handling.
// FSD §5.1, §3.6 (SSE data flow), §4.10.

import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Run, Step, StepEvent, StepStatus } from '@/types/run'
import { getRun } from '@/api'
import type { StepLogInfo } from '@/api'
import { useSSE, type UseSSEReturn } from '@/composables/useSSE'
import { useConfigStore } from './config'
import { useSysmonStore } from './sysmon'

// SSE event-name → payload contract for the mission event stream (FSD §3.6).
// `snapshot`/`state_update` carry the full run-state object; step/heartbeat
// events carry their event-record payload; `error` carries a message envelope.
interface MissionSSEEvents {
  snapshot: Run
  state_update: Run
  step_started: StepEvent
  step_completed: StepEvent
  step_failed: StepEvent
  heartbeat: StepEvent
  run_completed: StepEvent
  error: { message: string; runId?: string }
}

// Non-reactive holder for the active SSE controller pair. Stored outside Pinia
// reactivity (controllers are not serializable) and module-scoped because only
// one run is monitored at a time (FSD §4.10).
let activeSSE: UseSSEReturn | null = null

/** Derive a step status from an event, defaulting by event type. */
function deriveStepStatus(ev: StepEvent): StepStatus {
  if (ev.status) return ev.status as StepStatus
  switch (ev.type) {
    case 'step_started':
      return 'running'
    case 'step_completed':
      return 'completed'
    case 'step_failed':
      return 'failed'
    case 'step_skipped':
      return 'skipped'
    default:
      return 'pending'
  }
}

export const useMissionStore = defineStore('mission', () => {
  const currentRunId = ref<string | null>(null)
  const currentRun = ref<Run | null>(null)
  const steps = ref<Step[]>([])
  const selectedStep = ref<string | null>(null)
  const selectedStepKey = ref<string | null>(null)
  const selectedLogFile = ref<string | null>(null)
  const stepLogs = ref<StepLogInfo[]>([])
  const errorMsg = ref<string | null>(null)

  /** Replace current run and sync the steps array from the authoritative state. */
  function setRun(run: Run): void {
    currentRun.value = run
    steps.value = Array.isArray(run.steps) ? run.steps : []
  }

  /** Preserve the mission config across SSE refreshes: the live snapshot/state
   *  payloads omit `config`, so fall back to the previously-loaded config. */
  function mergeConfig(run: Run): Run {
    if (run.config) return run
    const prev = currentRun.value?.config ?? null
    return { ...run, config: prev }
  }

  /** Load a run's full detail via REST (FSD §4.3 step 2). The Monitor Server
   *  returns `config` as a sibling of `run` (RunDetail.config), so merge it into
   *  the run object to populate `currentRun.config` for MissionConfig. */
  async function loadRun(runId: string): Promise<void> {
    currentRunId.value = runId
    errorMsg.value = null
    try {
      const detail = await getRun(runId)
      setRun({ ...detail.run, config: detail.config })
      stepLogs.value = detail.stepLogs ?? []
    } catch (err: unknown) {
      errorMsg.value = err instanceof Error ? err.message : String(err)
    }
  }

  /** Insert or update a step in the timeline, matched by name + visit.
   *  Only patches fields that are actually present in the event — step_started
   *  events omit durationMs/marker/etc, and spreading undefined would clobber
   *  the correct values set by a prior snapshot or step_completed event. */
  function upsertStep(ev: StepEvent): void {
    if (!ev.step) return
    const visit = ev.visit ?? 1
    const idx = steps.value.findIndex(
      (s) => s.name === ev.step && (s.visits ?? 1) === visit,
    )
    const next: Partial<Step> = {
      name: ev.step,
      status: deriveStepStatus(ev),
      visits: visit,
    }
    if (ev.marker != null) next.marker = ev.marker
    if (ev.durationMs != null) next.durationMs = ev.durationMs
    if (ev.startedAt != null) next.startedAt = ev.startedAt
    if (ev.endedAt != null) next.endedAt = ev.endedAt
    if (ev.error != null) next.error = ev.error
    if (ev.sessionId != null) next.sessionId = ev.sessionId
    if (ev.promptFile != null) next.promptFile = ev.promptFile as string
    if (idx >= 0) {
      steps.value[idx] = { ...steps.value[idx], ...next }
    } else {
      steps.value.push(next as Step)
    }
  }

  /** Mark the selected step + log file (FSD §4.4 selection). */
  function selectStep(stepName: string | null, key: string | null, logFile: string | null): void {
    selectedStep.value = stepName
    selectedStepKey.value = key
    selectedLogFile.value = logFile
  }

  /** Open the SSE stream for a run and wire all event handlers (FSD §3.6). */
  function connectSSE(runId: string): void {
    disconnectSSE()
    currentRunId.value = runId
    // Lazy store accessors — called inside the action, not at module top-level,
    // to avoid any cross-store instantiation cycle.
    const configStore = useConfigStore()
    const sysmonStore = useSysmonStore()

    activeSSE = useSSE<MissionSSEEvents>(`/api/runs/${encodeURIComponent(runId)}/events`, {
      // snapshot/state_update carry the live run state but NOT the mission
      // config (config comes only from GET /api/runs/:runId detail). Preserve
      // the config that loadRun merged so MissionConfig survives SSE refreshes.
      snapshot: (run) => setRun(mergeConfig(run)),
      state_update: (run) => setRun(mergeConfig(run)),
      step_started: (ev) => upsertStep(ev),
      step_completed: (ev) => {
        upsertStep(ev)
        // Refresh roadmap progress when a step completes (FSD §3.6).
        const missionName = currentRun.value?.missionName
        if (missionName) void configStore.fetchRoadmap(missionName)
      },
      step_failed: (ev) => upsertStep(ev),
      heartbeat: () => {
        // Critical for Plan 3 ResourceChart real-time updates (FSD §3.6).
        void sysmonStore.fetch(runId)
      },
      run_completed: (ev) => {
        if (currentRun.value && ev.status) {
          currentRun.value = { ...currentRun.value, status: ev.status }
        }
      },
      error: (payload) => {
        errorMsg.value = payload.message || 'SSE error'
      },
    })
    activeSSE.connect()
  }

  /** Close the active SSE stream, if any. */
  function disconnectSSE(): void {
    if (activeSSE) {
      activeSSE.disconnect()
      activeSSE = null
    }
  }

  /** Reset all state (used when leaving the detail page). */
  function clear(): void {
    disconnectSSE()
    currentRunId.value = null
    currentRun.value = null
    steps.value = []
    selectedStep.value = null
    selectedStepKey.value = null
    selectedLogFile.value = null
    stepLogs.value = []
    errorMsg.value = null
  }

  return {
    currentRunId,
    currentRun,
    steps,
    selectedStep,
    selectedStepKey,
    selectedLogFile,
    stepLogs,
    errorMsg,
    loadRun,
    connectSSE,
    disconnectSSE,
    upsertStep,
    selectStep,
    clear,
  }
})
