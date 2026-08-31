<template>
  <n-layout has-sider class="detail-layout">
    <!-- Left: Step timeline sidebar.
         Selected step is tracked in missionStore (this renderer owns StepTimeline
         selection; the RunDetail shell no longer passes a select callback). -->
    <n-layout-sider
      bordered
      :width="320"
      content-style="padding: 12px; height: calc(100vh - 120px); overflow: auto"
    >
      <n-text depth="2" class="pane-title">Step Timeline</n-text>
      <StepTimeline
        v-if="missionStore.steps.length > 0"
        :steps="missionStore.steps"
        :selected-key="missionStore.selectedStepKey"
        @select="onSelectStep"
      />
      <n-empty v-else description="No steps yet." size="small" />
    </n-layout-sider>

    <!-- Right: config + log/chart/plans/roadmap stack -->
    <n-layout-content
      content-style="padding: 12px 16px; height: calc(100vh - 120px); overflow: auto"
    >
      <!-- MissionConfig — collapsible card with toggle in header-right (FSD §4.8) -->
      <n-card title="Mission Config" size="small" class="pane-card">
        <template #header-extra>
          <n-button size="tiny" text @click="showConfig = !showConfig">
            <n-icon :component="showConfig ? ChevronUp : ChevronDown" />
          </n-button>
        </template>
        <MissionConfig v-show="showConfig" :config="missionStore.currentRun?.config ?? null" />
      </n-card>

      <n-card title="Log Viewer" size="small" class="pane-card">
        <LogViewer :run-id="runId" />
      </n-card>

      <n-card title="Resource Monitor" size="small" class="pane-card">
        <ResourceChart />
      </n-card>

      <!-- Plans table (real component, FSD §4.9). -->
      <div class="pane-card">
        <PlansTable :plans="configStore.plans" :plans-dir="configStore.plansDir" />
      </div>

      <div class="pane-card">
        <RoadmapProgress />
      </div>
    </n-layout-content>
  </n-layout>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import {
  NButton,
  NCard,
  NEmpty,
  NIcon,
  NLayout,
  NLayoutContent,
  NLayoutSider,
  NText,
} from 'naive-ui'
import { ChevronDown, ChevronUp } from '@vicons/ionicons5'
import StepTimeline from '@/components/run/StepTimeline.vue'
import MissionConfig from '@/components/run/MissionConfig.vue'
import PlansTable from '@/components/run/PlansTable.vue'
import RoadmapProgress from '@/components/roadmap/RoadmapProgress.vue'
import LogViewer from '@/components/log/LogViewer.vue'
import ResourceChart from '@/components/chart/ResourceChart.vue'
import { useMissionStore } from '@/stores/mission'
import { useConfigStore } from '@/stores/config'
import type { Run, Step } from '@/types/run'

// DefaultRunDetail is the 1:1 extraction of the pre-itp2-3 RunDetail render
// content (itp2-3 §5.2). The RunDetail shell now owns data lifecycle (store
// init, SSE, sysmon) and dispatches this renderer for null/unknown flowName.
// Zero behavior change vs. the legacy single-body RunDetail.
defineProps<{
  runId: string
  run: Run | null
}>()

const missionStore = useMissionStore()
const configStore = useConfigStore()

const showConfig = ref(false)

// Step selection from the timeline → load log + mark selected (FSD §4.4).
// Owned by the renderer; the shell does not impose a selection contract.
function onSelectStep(payload: { step: Step; key: string; logFile: string }): void {
  missionStore.selectStep(payload.step.name, payload.key, payload.logFile)
}
</script>

<style scoped>
.pane-title {
  font-size: 13px;
  font-weight: 600;
  display: block;
  margin-bottom: 8px;
}
.detail-layout {
  border: 1px solid #334155;
  border-radius: 6px;
  overflow: hidden;
}
.pane-card {
  margin-top: 12px;
}
</style>
