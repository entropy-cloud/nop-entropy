<template>
  <div class="run-list">
    <AppHeader title="Mission-Driver Monitor" :mission-name="null" />

    <div class="section">
      <div class="section-head">
        <n-text class="section-title">Recent Runs</n-text>
        <div class="head-actions">
          <n-button size="small" secondary :loading="loading" @click="refresh">
            Refresh
          </n-button>
        </div>
      </div>

      <n-spin :show="loading && runs.length === 0">
        <n-alert
          v-if="error"
          type="error"
          :title="error"
          closable
          style="margin-bottom: 12px"
        />
        <n-empty
          v-else-if="!loading && runs.length === 0"
          description="No runs yet."
          style="padding: 36px 0"
        />
        <template v-else>
          <n-data-table
            :columns="runColumns"
            :data="runs"
            :row-key="(r: RunSummary) => r.runId"
            :row-props="runRowProps"
            striped
            bordered
            size="small"
          />
          <div class="load-more">
            <n-text depth="3" class="load-more-count">
              显示 {{ runs.length }}{{ total != null ? ` / ${total}` : '' }} 条
            </n-text>
            <n-button
              v-if="hasMore"
              size="small"
              secondary
              :loading="loadingMore"
              @click="loadMore"
            >
              加载更多
            </n-button>
          </div>
        </template>
      </n-spin>
    </div>

    <div class="section">
      <n-text class="section-title">Mission Configs</n-text>
      <n-empty
        v-if="!loading && configs.length === 0"
        description="No mission configs found."
        style="padding: 24px 0"
      />
      <n-grid v-else :cols="3" :x-gap="12" :y-gap="12" responsive="screen">
        <n-grid-item v-for="c in configs" :key="c.name">
          <n-card hoverable size="small" class="config-card">
            <div class="card-name">{{ c.name }}</div>
            <div class="card-desc">{{ c.description || '—' }}</div>
            <div class="card-meta">
              <n-text depth="3">Last run:</n-text>
              <n-tag
                v-if="c.lastRunStatus"
                :type="statusTagType(c.lastRunStatus)"
                round
                size="tiny"
              >
                {{ c.lastRunStatus }}
              </n-tag>
              <n-text v-else depth="3">none</n-text>
            </div>
            <div class="card-action">
              <n-button
                v-if="c.lastRunId"
                size="tiny"
                tag="a"
                text
                @click="openRun(c.lastRunId)"
              >
                Open last run →
              </n-button>
            </div>
          </n-card>
        </n-grid-item>
      </n-grid>
      <div v-if="configs.length > 0" class="load-more">
        <n-text depth="3" class="load-more-count">
          显示 {{ configs.length }}{{ configTotal != null ? ` / ${configTotal}` : '' }} 条
        </n-text>
        <n-button
          v-if="configHasMore"
          size="small"
          secondary
          :loading="configStore.loadingMore"
          @click="loadMoreConfigs"
        >
          加载更多
        </n-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NAlert,
  NButton,
  NCard,
  NDataTable,
  NEmpty,
  NGrid,
  NGridItem,
  NIcon,
  NPopconfirm,
  NSpin,
  NTag,
  NText,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import { CopyOutline, TrashOutline } from '@vicons/ionicons5'
import AppHeader from '@/components/layout/AppHeader.vue'
import type { RunStatus, RunSummary } from '@/types/run'
import { getRuns, deleteRun } from '@/api'
import { useConfigStore } from '@/stores/config'

const router = useRouter()
const configStore = useConfigStore()
const message = useMessage()

const runs = ref<RunSummary[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const error = ref<string | null>(null)
const total = ref<number | null>(null)
const hasMore = ref(false)
const deletingRunIds = ref<Set<string>>(new Set())
let timer: ReturnType<typeof setInterval> | null = null

// Default page size (configurable). Recent Runs shows the newest PAGE_SIZE runs;
// "加载更多" appends older pages via the offset param (§4).
const PAGE_SIZE = 10

// Reactive view of the store list (fetched on mount). Exposed to template as
// `configs` so cards re-render when configStore.fetchConfigs() resolves.
const configs = computed(() => configStore.configs)
const configTotal = computed(() => configStore.total)
const configHasMore = computed(() => configStore.hasMore)

// FSD §4.2 — 5s auto-refresh of the FIRST page only, cleared on unmount. The
// auto-refresh keeps the newest window fresh without clobbering any older pages
// the user expanded via "加载更多": it refreshes exactly the currently-loaded
// count (rounded up to a page) so expansion state survives.
async function refresh(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const limit = Math.max(PAGE_SIZE, runs.value.length)
    const page = await getRuns(limit, 0)
    runs.value = page.runs
    total.value = page.total ?? page.runs.length
    hasMore.value = page.hasMore ?? runs.value.length < (total.value ?? 0)
  } catch (err: unknown) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

async function loadMore(): Promise<void> {
  loadingMore.value = true
  error.value = null
  try {
    const page = await getRuns(PAGE_SIZE, runs.value.length)
    // De-dupe by runId in case a new run shifted the window between calls.
    const seen = new Set(runs.value.map((r) => r.runId))
    for (const r of page.runs) if (!seen.has(r.runId)) runs.value.push(r)
    total.value = page.total ?? total.value
    hasMore.value = page.hasMore ?? runs.value.length < (total.value ?? 0)
  } catch (err: unknown) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loadingMore.value = false
  }
}

// status → Naive UI tag type. max_* → warning, matches Alpine statusGroup('max').
// WI5 — `single_step_done` (WI2 --step single-step mode) maps to success: it
// already exits with code 0 in main.js exitMap and must not display as unknown
// gray (would contradict AppHeader's status tag — both maps must stay in sync).
function statusTagType(status: string | null | undefined): 'info' | 'success' | 'error' | 'warning' | 'default' {
  if (!status) return 'default'
  if (status === 'running') return 'info'
  if (status === 'completed') return 'success'
  if (status === 'single_step_done') return 'success'
  if (status === 'failed') return 'error'
  if (status === 'aborted') return 'warning'
  if (status.startsWith('max_')) return 'warning'
  return 'default'
}

// flowName → Naive UI tag type (itp2-3 §5.1). mission-driver is the common path
// (neutral gray); integration-test is the test flow (green); anything else gets
// info/blue so emerging flows are visible without further wiring.
function flowTagType(flowName: string | null | undefined): 'default' | 'success' | 'info' {
  if (!flowName) return 'default'
  if (flowName === 'integration-test') return 'success'
  if (flowName === 'mission-driver') return 'default'
  return 'info'
}

function fmtStarted(ts: string | null | undefined): string {
  if (!ts) return '—'
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return ts
  return d.toLocaleString()
}

function fmtDuration(status: string | null | undefined, startedAt: string | null | undefined, endedAt: string | null | undefined): string {
  if (!startedAt) return '—'
  const start = Date.parse(startedAt)
  if (Number.isNaN(start)) return '—'
  // For running or unfinished runs (no endedAt), use current time.
  // Do NOT use updatedAt — it's stale between state changes.
  const end = (status === 'running' || !endedAt) ? Date.now() : Date.parse(endedAt)
  if (Number.isNaN(end)) return '—'
  const totalSec = Math.max(0, Math.floor((end - start) / 1000))
  if (totalSec < 60) return `${totalSec}s`
  const m = Math.floor(totalSec / 60)
  if (m < 60) return `${m}m`
  const h = Math.floor(m / 60)
  return `${h}h ${m % 60}m`
}

function openRun(runId: string): void {
  router.push(`/runs/${encodeURIComponent(runId)}`)
}

// Append the next page of mission configs (mirrors the runs loadMore pattern).
function loadMoreConfigs(): void {
  void configStore.fetchConfigs(false)
}

async function handleDelete(runId: string): Promise<void> {
  deletingRunIds.value.add(runId)
  try {
    await deleteRun(runId)
    runs.value = runs.value.filter((r) => r.runId !== runId)
    total.value = total.value != null ? Math.max(0, total.value - 1) : null
    message.success('Run deleted')
  } catch (err: unknown) {
    message.error(err instanceof Error ? err.message : 'Delete failed')
  } finally {
    deletingRunIds.value.delete(runId)
  }
}

async function copyRunId(runId: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(runId)
    message.success(`Copied: ${runId}`)
  } catch {
    message.error('Copy failed')
  }
}

const runColumns = computed<DataTableColumns<RunSummary>>(() => [
  {
    title: 'Run ID',
    key: 'runId',
    render: (row) =>
      h('div', { style: 'display:flex;align-items:center;gap:4px' }, [
        h('span', { class: 'mono', style: 'cursor:pointer;color:#60a5fa', onClick: () => openRun(row.runId) }, row.runId),
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            title: '复制 Run ID',
            onClick: (e: Event) => { e.stopPropagation(); void copyRunId(row.runId) },
          },
          { icon: () => h(NIcon, { component: CopyOutline }) },
        ),
      ]),
  },
  {
    title: 'Mission',
    key: 'missionName',
    render: (row) => row.missionName || '—',
  },
  {
    // Flow column — driven by itp2-1 backend summary flowName (itp2-3 §5.1).
    // Tag color distinguishes the run's execution flow; null → em-dash.
    title: 'Flow',
    key: 'flowName',
    width: 120,
    render: (row) =>
      h(
        NTag,
        { type: flowTagType(row.flowName), round: true, size: 'small' },
        { default: () => row.flowName || '—' },
      ),
  },
  {
    title: 'Status',
    key: 'status',
    render: (row) =>
      h(
        NTag,
        { type: statusTagType(row.status as RunStatus), round: true, size: 'small' },
        { default: () => row.status || 'unknown' },
      ),
  },
  {
    title: 'Steps',
    key: 'stepCount',
    render: (row) => (row.stepCount != null ? String(row.stepCount) : '—'),
  },
  {
    title: 'Started',
    key: 'startedAt',
    render: (row) => fmtStarted(row.startedAt),
  },
  {
    title: 'Duration',
    key: 'duration',
    render: (row) => fmtDuration(row.status, row.startedAt, row.endedAt),
  },
  {
    title: '',
    key: 'actions',
    width: 60,
    render: (row) =>
      h(
        NPopconfirm,
        { onPositiveClick: () => void handleDelete(row.runId) },
        {
          trigger: () =>
            h(
              NButton,
              {
                size: 'tiny',
                quaternary: true,
                loading: deletingRunIds.value.has(row.runId),
                disabled: row.status === 'running',
                onClick: (e: Event) => e.stopPropagation(),
              },
              { icon: () => h(NIcon, { component: TrashOutline }) },
            ),
          default: () => `Delete ${row.runId}? This cannot be undone.`,
        },
      ),
  },
])

// Whole-row click navigates to the run detail (FSD §4.2 interaction).
const runRowProps = (row: RunSummary) => ({
  style: 'cursor: pointer',
  onClick: () => openRun(row.runId),
})

onMounted(async () => {
  await Promise.all([refresh(), configStore.fetchConfigs()])
  timer = setInterval(refresh, 5000)
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})
</script>

<style scoped>
.run-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
}
.load-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 10px 0 2px;
}
.load-more-count {
  font-size: 12px;
}
.config-card {
  height: 100%;
}
.card-name {
  font-weight: 600;
  margin-bottom: 4px;
}
.card-desc {
  color: #94a3b8;
  font-size: 13px;
  min-height: 20px;
  margin-bottom: 8px;
}
.card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.card-action {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
