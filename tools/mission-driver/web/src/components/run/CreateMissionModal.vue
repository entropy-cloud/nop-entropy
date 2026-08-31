<template>
  <n-modal
    :show="show"
    title="New Mission"
    preset="card"
    style="width: min(680px, 94vw)"
    :mask-closable="false"
    @update:show="(v: boolean) => emit('update:show', v)"
  >
    <n-alert v-if="draftStore.error" type="warning" :title="draftStore.error" style="margin-bottom: 12px" />

    <!-- ── Wizard step: form entry (before a dev job is started) ──────────── -->
    <n-form v-if="!draftStore.currentJobId" label-placement="top" size="small">
      <!-- Flow dropdown — the flow defines the mission's nature (no Mode) -->
      <n-form-item label="Flow">
        <n-select
          v-model:value="flowHint"
          :options="flowOptions"
          :loading="loadingFlows"
          placeholder="Select a flow"
        />
      </n-form-item>

      <!-- Mission goal + optional target file (the flow above defines the nature) -->
        <n-form-item label="Mission Description">
          <n-input
            v-model:value="desc"
            type="textarea"
            :rows="5"
            :maxlength="MAX_DESC"
            show-count
            placeholder="Describe the mission, e.g. 'Build a fraud-detection mission covering the rule engine + daily aggregation batch'"
          />
        </n-form-item>

        <!-- Target input: browse or manual -->
        <n-form-item label="Target">
          <n-radio-group v-model:value="targetMode" style="margin-bottom: 8px">
            <n-radio value="manual">手写目标</n-radio>
            <n-radio value="browse">选目标文件</n-radio>
          </n-radio-group>
        </n-form-item>

        <!-- Manual target textarea -->
        <n-form-item v-if="targetMode === 'manual'" :show-label="false">
          <n-input
            v-model:value="targetFile"
            type="textarea"
            :rows="2"
            placeholder="Project-relative path, e.g. docs/backlog/my-feature-fsd.md (optional)"
          />
        </n-form-item>

        <!-- File browser -->
        <div v-else class="browse-box">
          <div class="browse-crumbs">
            <n-text depth="3" class="browse-prefix">/{{ browsePrefix || '' }}</n-text>
            <n-button v-if="browsePrefix" size="tiny" quaternary @click="goUp">↑ Up</n-button>
          </div>
          <div class="browse-list">
            <n-spin v-if="loadingBrowse" size="small" />
            <n-empty v-else-if="browseEntries.length === 0" description="No entries" size="small" />
            <template v-else>
              <div
                v-for="e in browseEntries"
                :key="e.path"
                class="browse-row"
                :class="{ selected: selectedFile === e.path }"
                @click="onBrowseClick(e)"
              >
                <span class="browse-icon">{{ e.isDir ? '📁' : '📄' }}</span>
                <span>{{ e.name }}</span>
                <n-tag v-if="selectedFile === e.path" size="tiny" type="success" round>selected</n-tag>
              </div>
            </template>
          </div>
          <n-text v-if="selectedFile" depth="3" class="browse-selected">
            Target: <code>{{ selectedFile }}</code>
          </n-text>
        </div>
    </n-form>

    <!-- ── Progress step: dev draft job polling (after submit) ───────────── -->
    <div v-else class="draft-progress">
      <div class="progress-head">
        <n-tag :type="statusTagType(draftStore.status)" round size="small">
          {{ draftStore.status || 'running' }}
        </n-tag>
        <n-tag v-if="phaseLabel" size="tiny" round>{{ phaseLabel }}</n-tag>
        <n-text depth="3" class="job-id mono">{{ draftStore.currentJobId }}</n-text>
      </div>

      <div v-if="draftStore.isRunning" class="running-hint">
        <n-spin size="small" />
        <n-text depth="3">{{ runningHint }}</n-text>
      </div>

      <!-- Completed: product display -->
      <div v-if="draftStore.isCompleted" class="product">
        <template v-if="draftStore.state?.missionName">
          <n-descriptions label-placement="left" :column="1" size="small" bordered>
            <n-descriptions-item label="Mission">
              <span class="mono">{{ draftStore.state.missionName }}</span>
            </n-descriptions-item>
            <n-descriptions-item v-if="draftStore.state.roadmapPath" label="Roadmap">
              <span class="mono">{{ draftStore.state.roadmapPath }}</span>
            </n-descriptions-item>
          </n-descriptions>
        </template>
        <n-alert v-else type="info" title="Mission file could not be auto-resolved">
          The draft finished but no mission.json with a roadmapPath was found. Check the log below or
          <code>missions/</code> manually.
        </n-alert>
      </div>

      <!-- Failed -->
      <n-alert v-if="draftStore.isFailed" type="error" :title="draftStore.state?.error || 'Draft failed'" />

      <!-- Log tail -->
      <div v-if="draftStore.logTail" class="log-box">
        <n-text depth="3" class="log-title">Draft log tail</n-text>
        <pre class="log-pre">{{ draftStore.logTail }}</pre>
      </div>
    </div>

    <template #footer>
      <div class="footer">
        <n-button size="small" @click="close">Close</n-button>
        <!-- Wizard step: submit create -->
        <template v-if="!draftStore.currentJobId">
          <n-button
            size="small"
            type="primary"
            :loading="draftStore.submitting"
            :disabled="!canSubmitDev"
            @click="submitDev"
          >
            Create
          </n-button>
        </template>
        <!-- Progress step: retry / run mission -->
        <template v-else-if="draftStore.isFailed">
          <n-button size="small" type="primary" @click="retry">Retry</n-button>
        </template>
        <template v-else-if="draftStore.isCompleted">
          <n-button
            v-if="draftStore.state?.missionName"
            size="small"
            type="primary"
            :loading="launching"
            @click="runMission"
          >
            Run Mission
          </n-button>
        </template>
      </div>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  NAlert,
  NButton,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NRadio,
  NRadioGroup,
  NSelect,
  NSpin,
  NTag,
  NText,
  useMessage,
} from 'naive-ui'
import {
  postRun,
  getFlows,
  browse,
  type FlowInfo,
  type BrowseEntry,
} from '@/api'
import { useDraftStore } from '@/stores/draft'
import { useConfigStore } from '@/stores/config'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{
  'update:show': [value: boolean]
  /** Emitted when the user closes the modal after a successful create (so the
   *  parent can refresh config cards). Also emitted on explicit "Open" via close. */
  created: [missionName: string | null]
}>()

const router = useRouter()
const message = useMessage()
const draftStore = useDraftStore()
const configStore = useConfigStore()

const MAX_DESC = 2000

// ── Wizard state ────────────────────────────────────────────────────────────
const flowHint = ref<string | null>(null)
const desc = ref('')
const launching = ref(false)

// Target input (manual goal text or file picker)
const targetMode = ref<'manual' | 'browse'>('manual')
const targetFile = ref('')
const selectedFile = ref('')
const browsePrefix = ref('')
const browseEntries = ref<BrowseEntry[]>([])
const loadingBrowse = ref(false)

// Flow dropdown source
const flows = ref<FlowInfo[]>([])
const loadingFlows = ref(false)

const flowOptions = computed(() =>
  flows.value.map((f) => ({
    label: f.entry ? `${f.name} — entry: ${f.entry}` : f.name,
    value: f.name,
  })),
)

const canSubmitDev = computed(
  () => desc.value.trim().length > 0 && desc.value.length <= MAX_DESC,
)

// ── Phase label for progress display ────────────────────────────────────────
const phaseLabel = computed(() => {
  const phase = draftStore.state?.phase
  if (!phase) return ''
  if (phase === 'brief') return 'Stage 1/2: Brief'
  if (phase === 'brief_done') return 'Stage 1/2: Brief ✓'
  if (phase === 'draft') return 'Stage 2/2: Draft'
  if (phase === 'completed') return 'Done'
  return phase
})

const runningHint = computed(() => {
  const phase = draftStore.state?.phase
  if (phase === 'brief' || phase === 'brief_done') {
    return 'Generating brief… polling every 5s.'
  }
  return 'Generating mission… polling every 5s.'
})

// ── Data loading ────────────────────────────────────────────────────────────
async function loadFlows(): Promise<void> {
  loadingFlows.value = true
  try {
    const { flows: list } = await getFlows()
    flows.value = list
    // Default to the main dev loop; user can pick another top-level flow.
    flowHint.value = flowHint.value || 'mission-driver'
  } catch {
    // keep defaults; dropdown stays usable
  } finally {
    loadingFlows.value = false
  }
}

async function loadBrowse(): Promise<void> {
  loadingBrowse.value = true
  try {
    const { entries } = await browse(browsePrefix.value || undefined)
    browseEntries.value = entries
  } catch {
    browseEntries.value = []
  } finally {
    loadingBrowse.value = false
  }
}

function onBrowseClick(e: BrowseEntry): void {
  if (e.isDir) {
    browsePrefix.value = e.path
    selectedFile.value = ''
    void loadBrowse()
  } else {
    selectedFile.value = e.path
  }
}

function goUp(): void {
  const parts = browsePrefix.value.split('/').filter(Boolean)
  parts.pop()
  browsePrefix.value = parts.join('/')
  selectedFile.value = ''
  void loadBrowse()
}

// ── Submit handlers ─────────────────────────────────────────────────────────
async function submitDev(): Promise<void> {
  if (!canSubmitDev.value) return
  const tf =
    targetMode.value === 'browse' ? selectedFile.value || undefined : targetFile.value.trim() || undefined
  try {
    await draftStore.submit(desc.value.trim(), {
      flowHint: flowHint.value || undefined,
      targetFile: tf,
    })
    message.success('Draft job started')
  } catch {
    // error surfaced in draftStore.error / the alert
  }
}

function retry(): void {
  draftStore.clear()
}

async function runMission(): Promise<void> {
  const name = draftStore.state?.missionName
  if (!name) return
  launching.value = true
  try {
    const { runId } = await postRun(name)
    await configStore.fetchConfigs(true)
    emit('created', name)
    emit('update:show', false)
    router.push(`/runs/${encodeURIComponent(runId)}`)
  } catch (err: unknown) {
    message.error(err instanceof Error ? err.message : 'Start run failed')
  } finally {
    launching.value = false
  }
}

function close(): void {
  if (draftStore.isCompleted) {
    emit('created', draftStore.state?.missionName ?? null)
  }
  emit('update:show', false)
}

// Status → Naive UI tag type for the progress header.
function statusTagType(
  status: string | null | undefined,
): 'info' | 'success' | 'error' | 'default' {
  if (status === 'running') return 'info'
  if (status === 'completed') return 'success'
  if (status === 'failed') return 'error'
  return 'default'
}

// ── Target-mode browse autoload ─────────────────────────────────────────────
watch(targetMode, (tm) => {
  if (tm === 'browse' && browseEntries.value.length === 0) {
    void loadBrowse()
  }
})

// Reset + load flows whenever the modal opens.
watch(
  () => props.show,
  (v) => {
    if (v) {
      desc.value = ''
      flowHint.value = null
      targetMode.value = 'manual'
      targetFile.value = ''
      selectedFile.value = ''
      browsePrefix.value = ''
      browseEntries.value = []
      draftStore.clear()
      void loadFlows()
    }
  },
)

onUnmounted(() => {
  draftStore.stopPolling()
})
</script>

<style scoped>
.hint {
  display: block;
  font-size: 12px;
}
.footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.draft-progress {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.progress-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.job-id {
  font-size: 12px;
}
.running-hint {
  display: flex;
  align-items: center;
  gap: 8px;
}
.product {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.log-box {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.log-title {
  font-size: 12px;
}
.log-pre {
  margin: 0;
  max-height: 220px;
  overflow: auto;
  padding: 8px;
  background: rgba(0, 0, 0, 0.25);
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.browse-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 4px;
  padding: 8px;
}
.browse-crumbs {
  display: flex;
  align-items: center;
  gap: 8px;
}
.browse-prefix {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}
.browse-list {
  max-height: 180px;
  overflow: auto;
}
.browse-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 6px;
  border-radius: 3px;
  cursor: pointer;
  font-size: 13px;
}
.browse-row:hover {
  background: rgba(255, 255, 255, 0.08);
}
.browse-row.selected {
  background: rgba(34, 197, 94, 0.18);
}
.browse-icon {
  width: 18px;
}
.browse-selected {
  font-size: 12px;
}
</style>
