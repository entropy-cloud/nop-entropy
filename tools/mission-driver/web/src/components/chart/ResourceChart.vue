<!--
  ResourceChart — resource monitoring history table + active process table.
  FSD §4.6. History: last N sysmon snapshots (newest first) — Time / Free Memory (GB) /
  Opencode RSS (GB) / Opencode Count / Node Count / Mem Pressure.
  Table: latest snapshot's top processes (RSS-sorted).

  Opencode Count replaces the old Process Count — knowing there are 347
  processes on the machine is useless for mission diagnostics; knowing there
  are 3 opencode instances (when only 1 should be running) directly spots
  orphans/stuck spawns.

  ECharts was removed (see docs/logs): the line chart was the only echarts
  consumer and pulled a ~539KB lazy chunk + ~65MB node_modules. A compact
  recent-history table covers the same diagnostic need at zero dependency cost.
-->
<template>
  <div class="resource-chart">
    <div v-if="hasData" class="hist-section">
      <div class="proc-head">
        <span class="proc-title">Resource History</span>
        <span class="proc-meta">最近 {{ recentSnapshots.length }} 条</span>
      </div>
      <n-data-table
        :columns="histColumns"
        :data="recentSnapshots"
        :row-key="(s: SysmonSnapshot) => s.ts ?? ''"
        size="small"
        :bordered="false"
        :single-line="false"
      />
    </div>
    <n-empty
      v-else
      description="暂无资源监控数据"
      size="small"
      class="empty"
    />

    <!-- Active Processes table (latest snapshot) -->
    <div v-if="showProcs && topProcs.length > 0" class="proc-section">
      <div class="proc-head">
        <span class="proc-title">Active Processes</span>
        <span v-if="sysmonStore.latest" class="proc-meta">
          {{ sysmonStore.latest.opencodeCount ?? 0 }} opencode ·
          {{ sysmonStore.latest.nodeCount ?? 0 }} node ·
          {{ sysmonStore.latest.memPressure ?? '—' }}
        </span>
      </div>
      <n-data-table
        :columns="procColumns"
        :data="topProcs"
        :row-key="(p: SysmonTopProc) => p.pid"
        size="small"
        :bordered="false"
        :single-line="false"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, h } from 'vue'
import { NDataTable, NEmpty, NTag } from 'naive-ui'
import { useSysmonStore } from '@/stores/sysmon'
import { useMissionStore } from '@/stores/mission'
import type { SysmonSnapshot, SysmonTopProc } from '@/types/sysmon'

const sysmonStore = useSysmonStore()
const missionStore = useMissionStore()

// How many recent snapshots to show in the history table.
const RECENT_LIMIT = 8

const hasData = computed(() => sysmonStore.snapshots.length > 0)

/** Last N snapshots, newest first. */
const recentSnapshots = computed<SysmonSnapshot[]>(() =>
  sysmonStore.snapshots.slice(-RECENT_LIMIT).reverse()
)

function fmtTime(ts: string | null): string {
  if (!ts) return '—'
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return ts
  return d.toLocaleTimeString(undefined, { hour12: false })
}

const histColumns = computed(() => [
  {
    title: 'Time',
    key: 'ts',
    width: 90,
    render: (row: SysmonSnapshot) => h('span', { class: 'mono-sm' }, fmtTime(row.ts)),
  },
  {
    title: 'Free (GB)',
    key: 'freeGB',
    width: 80,
    render: (row: SysmonSnapshot) =>
      h('span', { class: 'mono-sm' }, row.freeGB != null ? row.freeGB.toFixed(2) : '—'),
  },
  {
    title: 'OC RSS (GB)',
    key: 'opencodeRSS_MB',
    width: 90,
    render: (row: SysmonSnapshot) =>
      h(
        'span',
        { class: 'mono-sm' },
        row.opencodeRSS_MB != null ? (row.opencodeRSS_MB / 1024).toFixed(2) : '—'
      ),
  },
  {
    title: 'OC',
    key: 'opencodeCount',
    width: 50,
    render: (row: SysmonSnapshot) => h('span', { class: 'mono-sm' }, String(row.opencodeCount ?? '—')),
  },
  {
    title: 'Node',
    key: 'nodeCount',
    width: 56,
    render: (row: SysmonSnapshot) => h('span', { class: 'mono-sm' }, String(row.nodeCount ?? '—')),
  },
  {
    title: 'Pressure',
    key: 'memPressure',
    width: 80,
    render: (row: SysmonSnapshot) => row.memPressure ?? '—',
  },
])

const topProcs = computed(() => sysmonStore.latest?.topProcs ?? [])

const missionPid = computed(() => missionStore.currentRun?.pid ?? null)

const showProcs = computed(() => {
  const status = missionStore.currentRun?.status
  if (status === 'completed' || status === 'aborted') return false
  if (missionPid.value == null) return false
  return true
})

const procColumns = computed(() => [
  {
    title: 'PID',
    key: 'pid',
    width: 70,
    render: (row: SysmonTopProc) => h('span', { class: 'mono-sm' }, String(row.pid)),
  },
  {
    title: 'Process',
    key: 'name',
    render: (row: SysmonTopProc) => [
      h('span', { class: 'proc-name' }, row.name),
      row.name.match(/opencode/i)
        ? h(NTag, { size: 'tiny', round: true, type: 'info', style: 'margin-left:6px' }, { default: () => 'opencode' })
        : null,
    ],
  },
  {
    title: 'RSS',
    key: 'rss_mb',
    width: 80,
    render: (row: SysmonTopProc) => h('span', { class: 'mono-sm' }, `${row.rss_mb} MB`),
  },
  {
    title: 'CPU',
    key: 'cpu_pct',
    width: 60,
    render: (row: SysmonTopProc) =>
      row.cpu_pct != null ? h('span', { class: 'mono-sm' }, `${row.cpu_pct}%`) : '—',
  },
  {
    title: 'Elapsed',
    key: 'elapsed',
    width: 80,
    render: (row: SysmonTopProc) => row.elapsed || '—',
  },
])
</script>

<style scoped>
.resource-chart {
  width: 100%;
}
.hist-section {
  margin-bottom: 4px;
}
.empty {
  padding: 24px 0;
}
.proc-section {
  margin-top: 12px;
  border-top: 1px solid #334155;
  padding-top: 8px;
}
.proc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.proc-title {
  font-size: 13px;
  font-weight: 600;
  color: #cbd5e1;
}
.proc-meta {
  font-size: 11px;
  color: #64748b;
}
:deep(.proc-name) {
  font-size: 12px;
}
.mono-sm {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  color: #94a3b8;
}
</style>
