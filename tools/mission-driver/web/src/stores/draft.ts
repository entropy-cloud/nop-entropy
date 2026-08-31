// Pinia store — async Mission Draft job polling (mdo-2 / FSD §3.1A-C).
//
// Holds the current draft job's state + log tail and polls
// GET /api/missions/draft/:jobId every 5s, stopping when the job reaches a
// terminal state (completed/failed). The CreateMissionModal drives this store:
//   desc → postDraft(desc) → store.startPolling(jobId) → completed/failed.
//
// Polling is owned by the store (not the component) so the modal can re-render
// on state changes; stopPolling() is called on component unmount.

import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  postDraft,
  getDraftJob,
  type DraftState,
  type DraftJobDetail,
} from '@/api'

const POLL_INTERVAL_MS = 5000

const TERMINAL_STATUSES = new Set(['completed', 'failed'])

export const useDraftStore = defineStore('draft', () => {
  const currentJobId = ref<string | null>(null)
  const state = ref<DraftState | null>(null)
  const logTail = ref<string | null>(null)
  const error = ref<string | null>(null)
  const submitting = ref(false)
  let timer: ReturnType<typeof setInterval> | null = null

  const status = computed(() => state.value?.status ?? null)
  const isRunning = computed(() => status.value === 'running')
  const isTerminal = computed(() =>
    status.value != null && TERMINAL_STATUSES.has(status.value),
  )
  const isCompleted = computed(() => status.value === 'completed')
  const isFailed = computed(() => status.value === 'failed')

  function clear(): void {
    currentJobId.value = null
    state.value = null
    logTail.value = null
    error.value = null
  }

  function stopPolling(): void {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  /** Fetch once immediately (used by startPolling + manual refresh). */
  async function pollOnce(jobId: string): Promise<void> {
    try {
      const detail: DraftJobDetail = await getDraftJob(jobId)
      state.value = detail.state
      logTail.value = detail.logTail
      if (detail.state && TERMINAL_STATUSES.has(detail.state.status)) {
        stopPolling()
      }
    } catch (err: unknown) {
      // Keep the last-known state; surface the error for the UI. A transient
      // fetch failure does NOT stop polling (the job may still complete later).
      error.value = err instanceof Error ? err.message : String(err)
    }
  }

  /**
   * Start polling a draft job. Fetches once immediately, then every
   * POLL_INTERVAL_MS until the job reaches completed/failed (or stopPolling).
   */
  function startPolling(jobId: string): void {
    stopPolling()
    currentJobId.value = jobId
    error.value = null
    void pollOnce(jobId)
    timer = setInterval(() => {
      if (currentJobId.value && !isTerminal.value) {
        void pollOnce(currentJobId.value)
      }
    }, POLL_INTERVAL_MS)
  }

  /**
   * Submit a new draft job: POST the desc (+ optional wizard selections), then
   * begin polling the returned jobId. Returns the jobId so callers (e.g. the
   * modal) can react.
   *
   * mdo-4 P2: `opts` carries flowHint/targetFile/skipBrief into the two-stage
   * brief→draft pipeline. The initial state carries the phase so the wizard can
   * display progress before the first poll resolves.
   */
  async function submit(
    desc: string,
    opts?: { flowHint?: string; targetFile?: string; skipBrief?: boolean },
  ): Promise<string> {
    submitting.value = true
    try {
      clear()
      const { jobId } = await postDraft(desc, opts)
      state.value = {
        status: 'running',
        desc,
        phase: opts?.skipBrief ? 'draft' : 'brief',
        flowHint: opts?.flowHint ?? null,
        targetFile: opts?.targetFile ?? null,
      }
      startPolling(jobId)
      return jobId
    } catch (err: unknown) {
      error.value = err instanceof Error ? err.message : String(err)
      throw err
    } finally {
      submitting.value = false
    }
  }

  return {
    currentJobId,
    state,
    logTail,
    error,
    submitting,
    status,
    isRunning,
    isTerminal,
    isCompleted,
    isFailed,
    clear,
    stopPolling,
    startPolling,
    submit,
    pollOnce,
  }
})
