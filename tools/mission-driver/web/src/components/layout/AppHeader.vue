<template>
  <div class="app-header">
    <n-button
      v-if="showBack"
      quaternary
      size="small"
      class="back-btn"
      @click="goBack"
    >
      <template #icon>
        <n-icon :component="ArrowBack" />
      </template>
      Back
    </n-button>
    <n-text class="title">{{ title || '—' }}</n-text>
    <n-tag v-if="status" :type="statusTagType" round size="small">
      {{ status }}
    </n-tag>
    <n-text v-if="elapsedText" depth="3" class="elapsed">⏳ {{ elapsedText }}</n-text>
    <div class="header-spacer" />
    <n-button quaternary size="small" tag="a" @click="goContext" title="Context Explorer">
      <template #icon>
        <n-icon :component="GridOutline" />
      </template>
      Context
    </n-button>
    <n-button quaternary circle size="small" title="执行命令" @click="handleOpenCommandModal">
      <template #icon>
        <n-icon :component="TerminalOutline" />
      </template>
    </n-button>
    <n-button quaternary circle size="small" title="Base Config" @click="handleOpenBaseConfig">
      <template #icon>
        <n-icon :component="SettingsOutline" />
      </template>
    </n-button>
  </div>
</template>

<script setup lang="ts">
import { computed, inject } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NIcon, NTag, NText } from 'naive-ui'
import { ArrowBack, SettingsOutline, TerminalOutline, GridOutline } from '@vicons/ionicons5'
import type { RunStatus } from '@/types/run'
import { useClock } from '@/composables/useClock'

const props = defineProps<{
  title?: string | null
  status?: RunStatus | null
  startedAt?: string | null
  showBack?: boolean
  missionName?: string | null
}>()

const router = useRouter()
const openBaseConfig = inject<() => void>('openBaseConfig', () => {})
const openCommandModal = inject<(missionName?: string | null) => void>('openCommandModal', () => {})

// FSD §4.3 header — status tag type mirrors the run status mapping used
// elsewhere (running→info, completed→success, failed→error, max_*→warning).
// WI5 — `single_step_done` maps to success here too so the RunDetail header
// stays consistent with RunList's status tag (AppHeader.vue:60-61 mirror rule).
const statusTagType = computed<'info' | 'success' | 'error' | 'warning' | 'default'>(() => {
  const s = props.status
  if (!s) return 'default'
  if (s === 'running') return 'info'
  if (s === 'completed') return 'success'
  if (s === 'single_step_done') return 'success'
  if (s === 'failed') return 'error'
  if (typeof s === 'string' && s.startsWith('max_')) return 'warning'
  return 'default'
})

// Elapsed only ticks while the run is running. Once status is terminal
// (completed/failed/max_*), the getter returns null and the clock stops.
const { elapsedText } = useClock(() => props.status === 'running' ? (props.startedAt ?? null) : null)

function goBack(): void {
  router.back()
}

function goContext(): void {
  router.push('/context')
}

function handleOpenBaseConfig(): void {
  openBaseConfig?.()
}

function handleOpenCommandModal(): void {
  openCommandModal?.(props.missionName ?? null)
}
</script>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}
.back-btn {
  flex-shrink: 0;
}
.title {
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.elapsed {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.header-spacer {
  flex: 1;
}
</style>
