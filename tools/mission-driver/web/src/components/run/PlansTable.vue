<template>
  <n-card title="Plans" size="small">
    <template v-if="plansDir" #header-extra>
      <n-text depth="3" class="plans-dir" :title="plansDir">{{ shortDir(plansDir) }}</n-text>
    </template>
    <n-empty
      v-if="plans.length === 0"
      description="No plan files found."
      size="small"
    />
    <n-data-table
      v-else
      :columns="columns"
      :data="planRows"
      :row-key="(r: PlanInfo) => r.fileName"
      size="small"
      striped
      bordered
    />
  </n-card>
</template>

<script setup lang="ts">
import { computed, h } from 'vue'
import { NCard, NDataTable, NEmpty, NTag, NText, type DataTableColumns } from 'naive-ui'
import type { PlanInfo } from '@/types/config'

const props = defineProps<{
  plans: PlanInfo[]
  plansDir: string | null
}>()

// status → Naive UI tag type (active→info, completed→success, draft→default…).
function statusTagType(status: string): 'info' | 'success' | 'warning' | 'error' | 'default' {
  if (status === 'active') return 'info'
  if (status === 'completed') return 'success'
  if (status === 'draft' || status === 'planned') return 'default'
  if (status === 'failed') return 'error'
  return 'default'
}

function fmtBytes(n: number): string {
  if (n == null || Number.isNaN(n)) return '—'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

function fmtDate(ms: number): string {
  if (ms == null || Number.isNaN(ms)) return '—'
  const d = new Date(ms)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString()
}

function shortDir(dir: string): string {
  return dir.replace(/\\/g, '/').split('/').slice(-2).join('/')
}

const columns = computed<DataTableColumns<PlanInfo>>(() => [
  { title: 'File', key: 'fileName' },
  {
    title: 'Status',
    key: 'status',
    render: (row) =>
      h(
        NTag,
        { type: statusTagType(row.status), size: 'tiny', round: true },
        { default: () => row.status },
      ),
  },
  {
    title: 'Size',
    key: 'sizeBytes',
    render: (row) => fmtBytes(row.sizeBytes),
  },
  {
    title: 'Last Modified',
    key: 'lastModified',
    render: (row) => fmtDate(row.lastModified),
  },
])

// Reactive projection so the table re-renders when the prop array updates.
const planRows = computed(() => props.plans)
</script>

<style scoped>
.plans-dir {
  font-size: 12px;
  font-family: ui-monospace, monospace;
}
</style>
