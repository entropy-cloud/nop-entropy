<template>
  <div class="run-detail">
    <AppHeader
      show-back
      :title="missionStore.currentRun?.missionName ?? 'Loading run…'"
      :status="missionStore.currentRun?.status ?? null"
      :started-at="missionStore.currentRun?.startedAt ?? null"
      :mission-name="missionStore.currentRun?.missionName ?? null"
    />

    <n-alert
      v-if="missionStore.errorMsg"
      type="error"
      :title="missionStore.errorMsg"
      style="margin-bottom: 12px"
    />

    <!-- Render content is dispatched by the renderer registry (FSD §5.2):
         default flowName → DefaultRunDetail; registered flows lazy-load.
         The shell keeps run-level data lifecycle (store init / SSE / sysmon). -->
    <n-spin :show="loading && !missionStore.currentRun">
      <component
        :is="resolveRenderer(run?.flowName)"
        :run-id="runId"
        :run="run"
      />
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { NAlert, NSpin } from 'naive-ui'
import AppHeader from '@/components/layout/AppHeader.vue'
import { resolveRenderer } from '@/components/run/runDetailRegistry'
import { useMissionStore } from '@/stores/mission'
import { useSysmonStore } from '@/stores/sysmon'
import { useConfigStore } from '@/stores/config'

const route = useRoute()
const missionStore = useMissionStore()
const sysmonStore = useSysmonStore()
const configStore = useConfigStore()

const loading = ref(false)

// Normalized runId for child components (LogViewer needs a string prop).
const runId = computed(() =>
  typeof route.params.runId === 'string' ? route.params.runId : '',
)

// The current run drives renderer dispatch (flowName) and is passed to the
// resolved renderer as a prop.
const run = computed(() => missionStore.currentRun)

// FSD §4.3 page-enter flow. Order matters: loadRun populates currentRun (so the
// mission name + flowName are known for renderer dispatch), then SSE + sysmon +
// roadmap/plans fan out in parallel.
async function initRun(runId: string): Promise<void> {
  loading.value = true
  try {
    await missionStore.loadRun(runId)
    missionStore.connectSSE(runId)
    void sysmonStore.fetch(runId)
    const missionName = missionStore.currentRun?.missionName
    if (missionName) {
      void configStore.fetchAllConfig(missionName)
    }
  } finally {
    loading.value = false
  }
}

function teardown(): void {
  missionStore.disconnectSSE()
  missionStore.clear()
}

onMounted(() => {
  if (runId.value) void initRun(runId.value)
})

onUnmounted(teardown)

// Navigation between runs (same component, different :runId) — teardown then
// re-init so the SSE stream + stores reflect the new run (FSD §4.3).
watch(runId, (next, prev) => {
  if (next === prev) return
  teardown()
  if (next) void initRun(next)
})
</script>

<style scoped>
.run-detail {
  display: flex;
  flex-direction: column;
}
</style>
