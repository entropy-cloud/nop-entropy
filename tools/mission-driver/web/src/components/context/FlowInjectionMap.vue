<template>
  <div class="flow-injection-map">
    <div class="controls">
      <n-select
        v-model:value="selectedFlow"
        :options="flowOptions"
        placeholder="Select a flow…"
        size="small"
        style="max-width: 320px"
        :loading="flowsLoading"
        @update:value="loadMap"
      />
      <n-button size="small" secondary :loading="mapLoading" :disabled="!selectedFlow" @click="loadMap(selectedFlow)">
        Refresh
      </n-button>
      <div class="spacer" />
      <n-button-group v-if="map" size="small">
        <n-button :type="viewMode === 'graph' ? 'primary' : 'default'" @click="viewMode = 'graph'">
          State Machine
        </n-button>
        <n-button :type="viewMode === 'table' ? 'primary' : 'default'" @click="viewMode = 'table'">
          Table
        </n-button>
      </n-button-group>
    </div>

    <n-spin :show="mapLoading">
      <n-alert v-if="error" type="error" :title="error" style="margin-top: 12px" />
      <n-empty
        v-else-if="!map && !mapLoading"
        description="Select a flow to see its per-step injection map."
        style="padding: 36px 0"
      />
      <template v-else-if="map">
        <n-text depth="3" style="display:block; margin: 8px 0">
          {{ map.flowName }} — {{ map.steps.length }} steps · {{ (map.edges || []).length }} transitions
          <span v-if="map.entry"> · entry: {{ map.entry }}</span>
        </n-text>

        <!-- Graph view: state-machine DAG + selected-step detail panel -->
        <template v-if="viewMode === 'graph'">
          <FlowStateMachine
            :map="map"
            :selected-step="selectedStepName"
            :on-view-prompt="viewPrompt"
            @select-step="onGraphSelect"
          />
          <div class="step-detail">
            <template v-if="selectedStep">
              <div class="step-detail-head">
                <n-text class="step-detail-title">
                  <code>{{ selectedStep.name }}</code>
                </n-text>
                <n-tag v-if="selectedStep.isEntry" size="tiny" type="primary" round>entry</n-tag>
                <n-tag v-if="selectedStep.subflowName" size="tiny" type="info" round>
                  subflow: {{ selectedStep.subflowName }}
                </n-tag>
                <n-tag v-if="selectedStep.type" size="tiny" round :bordered="false">{{ selectedStep.type }}</n-tag>
              </div>
              <component :is="() => (selectedStep ? renderStepDetail(selectedStep) : null)" />
            </template>
            <n-empty v-else description="Click a node to see its injection details." style="padding: 24px 0" />
          </div>
        </template>

        <!-- Table view: full per-step breakdown (existing) -->
        <n-data-table
          v-else
          :columns="stepColumns"
          :data="map.steps"
          :row-key="(s: InjectionStep) => s.name"
          :expanded-row-keys="expandedKeys"
          @update:expanded-row-keys="onExpandUpdate"
          striped
          bordered
          size="small"
        />
      </template>
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, h, computed, onMounted } from 'vue'
import type { VNode } from 'vue'
import { NDataTable, NTag, NText, NTable, NButton, NButtonGroup, NEmpty } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { getFlows, getInjectionMap } from '@/api'
import type { FlowInfo, InjectionMap, InjectionStep, InjectionVar } from '@/api'
import FlowStateMachine from './FlowStateMachine.vue'

const props = defineProps<{
  onViewPrompt?: (name: string) => void
  onViewMemory?: (store: string, file: string) => void
}>()

const selectedFlow = ref<string | null>(null)
const flowOptions = ref<{ label: string; value: string }[]>([])
const flowsLoading = ref(false)
const mapLoading = ref(false)
const map = ref<InjectionMap | null>(null)
const error = ref<string | null>(null)

// Default to the graph (state-machine) view — it gives the whole-flow overview
// the dashboard is otherwise missing; the table remains one click away.
const viewMode = ref<'graph' | 'table'>('graph')
const selectedStepName = ref<string | null>(null)
const expandedKeys = ref<string[]>([])

async function loadFlows() {
  flowsLoading.value = true
  try {
    const { flows } = await getFlows()
    flowOptions.value = flows.map((f: FlowInfo) => ({
      label: `${f.name} (${f.stepCount} steps)`,
      value: f.name,
    }))
    const md = flows.find((f) => f.name === 'mission-driver')
    if (md) {
      selectedFlow.value = md.name
      await loadMap(md.name)
    }
  } catch (e) {
    error.value = String((e as Error).message || e)
  } finally {
    flowsLoading.value = false
  }
}

async function loadMap(name: string | null) {
  if (!name) return
  mapLoading.value = true
  error.value = null
  selectedStepName.value = null
  try {
    map.value = await getInjectionMap(name)
    // Default the detail panel to the entry step so the graph view is not empty.
    if (map.value?.entry) selectedStepName.value = map.value.entry
  } catch (e) {
    map.value = null
    error.value = String((e as Error).message || e)
  } finally {
    mapLoading.value = false
  }
}

const selectedStep = computed<InjectionStep | null>(() =>
  map.value && selectedStepName.value
    ? map.value.steps.find((s) => s.name === selectedStepName.value) || null
    : null,
)

function onGraphSelect(stepName: string): void {
  selectedStepName.value = stepName
}

// Naive UI emits RowKey[] (string | number); the table is keyed by step name
// (string), so a cast is safe and keeps the handler signature compatible.
function onExpandUpdate(keys: Array<string | number>): void {
  expandedKeys.value = keys as string[]
}

function viewPrompt(name: string): void {
  props.onViewPrompt?.(name)
}

function promptBaseName(promptPath: string | null): string {
  if (!promptPath) return ''
  return promptPath.replace(/^.*[\\/]/, '').replace(/\.md$/i, '')
}

function memTarget(varName: string): { store: string; file: string } {
  if (varName === 'selfMemoryIndex') return { store: 'self', file: '_index.md' }
  return { store: '', file: '_index.md' }
}

function renderPromptLink(promptPath: string | null) {
  if (!promptPath) return h(NText, { depth: 3 }, { default: () => '(script step — no prompt)' })
  return h(
    'a',
    {
      class: 'ctx-link',
      title: 'View in Prompt Library',
      onClick: (e: Event) => {
        e.stopPropagation()
        props.onViewPrompt?.(promptBaseName(promptPath))
      },
    },
    promptBaseName(promptPath),
  )
}

function renderMemoryTag(m: { name: string; source: string }) {
  return h(
    NTag,
    {
      size: 'small',
      type: 'info',
      round: true,
      class: 'ctx-chip',
      title: `Open ${m.name} in Memory Browser`,
      onClick: (e: Event) => {
        e.stopPropagation()
        const t = memTarget(m.name)
        props.onViewMemory?.(t.store, t.file)
      },
    },
    { default: () => `memory: ${m.name}` },
  )
}

function renderVarTable(vars: InjectionVar[]) {
  if (!vars.length) return h(NText, { depth: 3 }, { default: () => '(no {{var}} placeholders in this prompt)' })
  return h(
    NTable,
    { size: 'small', striped: true, bordered: false },
    {
      default: () => [
        h('thead', [
          h('tr', [
            h('th', { style: 'width:180px' }, () => 'Variable'),
            h('th', { style: 'width:90px' }, () => 'Runtime'),
            h('th', () => 'Source / Provenance'),
          ]),
        ]),
        h(
          'tbody',
          vars.map((v) =>
            h('tr', [
              h('td', () => h('code', null, `{{${v.name}}}`)),
              h('td', () =>
                v.runtime
                  ? h(NTag, { size: 'small', type: 'warning', round: true }, { default: () => 'runtime' })
                  : h(NTag, { size: 'small', type: 'success', round: true }, { default: () => 'static' }),
              ),
              h('td', { style: 'opacity:0.85' }, () => v.source),
            ]),
          ),
        ),
      ],
    },
  )
}

// Shared per-step detail tree — rendered both in the table's expand row AND in
// the graph view's detail panel (single source of truth for the injection UI).
function renderStepDetail(row: InjectionStep): VNode {
  return h('div', { style: 'padding:8px 4px' }, [
    h('div', { style: 'margin-bottom:8px' }, [renderPromptLink(row.promptPath)]),
    row.memoryBlocks.length > 0
      ? h('div', { style: 'display:flex; gap:6px; flex-wrap:wrap; margin-bottom:8px' },
          row.memoryBlocks.map((m) => renderMemoryTag(m)))
      : null,
    row.contextFiles.length > 0
      ? h('div', { style: 'margin-bottom:8px' }, [
          h(NTag, { size: 'small', type: 'info', round: true }, { default: () => 'context' }),
          ' ',
          row.contextFiles.map((c) => c.name).join(', '),
        ])
      : null,
    row.sourcePaths
      ? h('div', { style: 'margin-bottom:8px' }, [
          h(NTag, { size: 'small', type: 'info', round: true }, { default: () => 'sourcePaths' }),
        ])
      : null,
    renderVarTable(row.promptVars),
    row.subflowName
      ? h('div', { style: 'margin-top:10px' }, [
          h(NText, { depth: 3, style: 'display:block; margin-bottom:4px' }, {
            default: () => `subflow: ${row.subflowName}${row.substeps && row.substeps.length ? ` (${row.substeps.length} steps)` : ''}`,
          }),
          renderSubsteps(row.substeps, 0),
        ])
      : null,
  ])
}

function renderSubsteps(substeps: InjectionStep[] | undefined, depth: number): VNode | null {
  if (!substeps || !substeps.length) return null
  const pad = depth * 16
  return h(
    'div',
    { style: `margin-left:${pad}px; margin-top:8px; border-left:2px solid #334155; padding-left:12px` },
    substeps.map((ss) =>
      h('div', { style: 'margin-bottom:10px' }, [
        h('div', { style: 'display:flex; align-items:center; gap:6px; flex-wrap:wrap; margin-bottom:4px' }, [
          h('span', { style: 'font-weight:600; font-size:13px' }, ss.name),
          ss.isEntry ? h(NTag, { size: 'tiny', type: 'primary', round: true }, { default: () => 'entry' }) : null,
          h(NTag, { size: 'tiny', round: true, bordered: false }, { default: () => ss.type || '?' }),
          renderPromptLink(ss.promptPath),
        ]),
        ss.memoryBlocks.length
          ? h('div', { style: 'display:flex; gap:6px; flex-wrap:wrap; margin-bottom:4px' },
              ss.memoryBlocks.map((m) => renderMemoryTag(m)))
          : null,
        ss.subflowMissing
          ? h(NText, { depth: 3, style: 'font-size:12px; display:block; margin-bottom:4px' },
              { default: () => `(subflow “${ss.subflowName}” not found)` })
          : null,
        ss.substeps && ss.substeps.length ? renderSubsteps(ss.substeps, depth + 1) : null,
      ]),
    ),
  )
}

const stepColumns: DataTableColumns<InjectionStep> = [
  {
    type: 'expand',
    renderExpand: (row) => renderStepDetail(row),
  },
  {
    title: 'Step',
    key: 'name',
    render: (row) =>
      h('span', { style: 'display:inline-flex; align-items:center; gap:6px' }, [
        row.name,
        row.isEntry
          ? h(NTag, { size: 'tiny', type: 'primary', round: true }, { default: () => 'entry' })
          : null,
        row.subflowName
          ? h(NTag, { size: 'tiny', type: 'default', round: true, bordered: false }, { default: () => 'subflow' })
          : null,
      ]),
  },
  { title: 'Type', key: 'type', width: 90 },
  {
    title: 'Prompt',
    key: 'promptPath',
    render: (row) => renderPromptLink(row.promptPath),
  },
  {
    title: 'Vars',
    key: 'vars',
    width: 70,
    render: (row) => String(row.promptVars.length),
  },
]

onMounted(loadFlows)
</script>

<style scoped>
.flow-injection-map {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.controls {
  display: flex;
  gap: 8px;
  align-items: center;
}
.spacer {
  flex: 1;
}
.step-detail {
  margin-top: 12px;
  padding: 12px;
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 8px;
}
.step-detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.step-detail-title {
  font-size: 15px;
  font-weight: 600;
}
</style>
<style>
a.ctx-link {
  color: #60a5fa;
  cursor: pointer;
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 12px;
  text-decoration: none;
  border-bottom: 1px dashed rgba(96, 165, 250, 0.5);
}
a.ctx-link:hover {
  color: #93c5fd;
  border-bottom-style: solid;
}
.ctx-chip {
  cursor: pointer;
}
</style>
