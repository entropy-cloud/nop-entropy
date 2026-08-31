<template>
  <n-card title="Roadmap Progress" size="small">
    <n-empty
      v-if="phases.length === 0"
      description="暂无 roadmap 数据"
      size="small"
    />
    <div v-else class="roadmap-progress">
      <!-- Overall progress (single n-progress driven by backend overallProgress,
           which counts only work items, milestones excluded). -->
      <div class="overall-row">
        <n-progress
          type="line"
          :percentage="overallPercent"
          :indicator-placement="'inside'"
          :height="20"
          :border-radius="4"
        />
        <n-tag :type="overallTagType" round size="small" class="overall-pill">
          {{ overallLabel }}
        </n-tag>
      </div>

      <!-- Per-phase status badges (Alpine.js FIX-3 badge-mode equivalent). -->
      <div class="phase-list">
        <div
          v-for="(p, idx) in phases"
          :key="'phase-' + idx + '-' + p.name"
          class="phase-row"
        >
          <div class="phase-name" :title="p.name">
            <span v-if="!p.isMilestone" class="phase-seq">{{ seqLabel(p) }}</span>
            <span>{{ p.name }}</span>
          </div>
          <n-tag :type="phaseTagType(p)" size="tiny" round :bordered="false">
            {{ phaseStatusLabel(p) }}
          </n-tag>
        </div>
      </div>
    </div>
  </n-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { NCard, NEmpty, NProgress, NTag } from 'naive-ui'
import { useConfigStore } from '@/stores/config'
import type { RoadmapPhase } from '@/types/config'

const configStore = useConfigStore()
const phases = computed(() => configStore.roadmap.phases)

// Replicates Alpine roadmapOverallLabel(): milestones excluded from the count.
const overallLabel = computed(() => {
  const items = phases.value.filter((p) => !p.isMilestone)
  const done = items.filter((p) => p.status === 'done').length
  const total = items.length
  const pct = total > 0 ? Math.round((done / total) * 100) : 0
  return `${done}/${total} done · ${pct}%`
})

const overallPercent = computed(() =>
  Math.round((configStore.roadmap.overallProgress ?? 0) * 100),
)

// Overall pill tag type mirrors the work-item done ratio.
const overallTagType = computed<'success' | 'default'>(() => {
  const items = phases.value.filter((p) => !p.isMilestone)
  const done = items.filter((p) => p.status === 'done').length
  return done === items.length && items.length > 0 ? 'success' : 'default'
})

function seqLabel(p: RoadmapPhase): string {
  return p.seq != null ? `${p.seq}.` : '·'
}

// status → Naive UI tag type (Alpine phaseStatusGroup mapping).
function phaseTagType(p: RoadmapPhase): 'success' | 'info' | 'default' | 'warning' {
  switch (p.status) {
    case 'done':
      return 'success'
    case 'ready':
    case 'planned':
      return 'info'
    case 'not-done':
      return 'warning'
    default:
      return 'default'
  }
}

// Replicates Alpine phaseStatusLabel(): milestone/work-item icon + status text.
function phaseStatusLabel(p: RoadmapPhase): string {
  if (p.isMilestone) {
    if (p.status === 'done') return '★ done'
    return '★ ' + (p.status === 'not-done' ? '未达成' : p.status)
  }
  const icon = p.status === 'done' ? '✓ ' : p.status === 'todo' ? '○ ' : ''
  return icon + p.status
}
</script>

<style scoped>
.overall-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.overall-row :deep(.n-progress) {
  flex: 1;
  min-width: 0;
}
.overall-pill {
  font-family: ui-monospace, monospace;
  white-space: nowrap;
}
.phase-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.phase-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 2px 0;
}
.phase-name {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
  font-size: 13px;
  color: #cbd5e1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.phase-seq {
  color: #94a3b8;
  font-family: ui-monospace, monospace;
  flex-shrink: 0;
}
</style>
