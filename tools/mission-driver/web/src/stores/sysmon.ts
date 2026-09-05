// Pinia store — system monitor snapshots.
// FSD §5.2, §4.6 (ResourceChart history table).

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { SysmonSnapshot } from '@/types/sysmon'
import { getSysmon } from '@/api'

export const useSysmonStore = defineStore('sysmon', () => {
  const snapshots = ref<SysmonSnapshot[]>([])

  /** The latest snapshot — used by the process table. */
  const latest = computed(() => {
    const snaps = snapshots.value
    return snaps.length > 0 ? snaps[snaps.length - 1] : null
  })

  /** Append a single snapshot to the store (heartbeat). */
  function append(snapshot: SysmonSnapshot): void {
    snapshots.value.push(snapshot)
  }

  /** Fetch the full sysmon history for a run. */
  async function fetch(runId: string): Promise<void> {
    try {
      const { snapshots: snaps } = await getSysmon(runId)
      snapshots.value = snaps
    } catch {
      // Graceful degrade: leave existing data intact on fetch failure (FSD §8).
    }
  }

  return {
    snapshots,
    latest,
    fetch,
    append,
  }
})
