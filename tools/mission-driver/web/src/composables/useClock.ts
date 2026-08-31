// useClock composable — live elapsed-time display.
// Given a getter for a run's `startedAt` timestamp, exposes a reactive
// `elapsedText` that updates every second (e.g. "23m 5s"). Used by the run
// header / step timeline to show running durations without per-component timers.

import { computed, onScopeDispose, ref } from 'vue'
import type { ComputedRef } from 'vue'

export interface UseClockReturn {
  /** Reactive elapsed-time label, empty string when not running/invalid. */
  elapsedText: ComputedRef<string>
}

/**
 * @param getStartedAt Getter returning an ISO timestamp string (or null).
 */
export function useClock(getStartedAt: () => string | null): UseClockReturn {
  const now = ref(Date.now())

  const timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)

  onScopeDispose(() => {
    clearInterval(timer)
  })

  const elapsedText = computed<string>(() => {
    const startedAt = getStartedAt()
    if (!startedAt) return ''
    const start = Date.parse(startedAt)
    if (Number.isNaN(start)) return ''
    const totalSec = Math.max(0, Math.floor((now.value - start) / 1000))
    const days = Math.floor(totalSec / 86400)
    const hours = Math.floor((totalSec % 86400) / 3600)
    const minutes = Math.floor((totalSec % 3600) / 60)
    const seconds = totalSec % 60
    if (days > 0) return `${days}d ${hours}h ${minutes}m`
    if (hours > 0) return `${hours}h ${minutes}m ${seconds}s`
    if (minutes > 0) return `${minutes}m ${seconds}s`
    return `${seconds}s`
  })

  return { elapsedText }
}
