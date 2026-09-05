<template>
  <div class="memory-browser">
    <n-spin :show="loading">
      <n-alert v-if="error" type="error" :title="error" style="margin-bottom: 12px" />
      <n-layout has-sider style="height: 70vh">
        <!-- Left: store / file tree -->
        <n-layout-sider
          bordered
          :width="240"
          content-style="padding: 8px; overflow: auto"
        >
          <div
            v-for="store in stores"
            :key="store.store"
            class="store-group"
          >
            <div class="store-head" @click="toggleStore(store.store)">
              <n-icon :component="store.open ? ChevronDown : ChevronForward" size="14" />
              <n-text class="store-label">{{ store.store }}</n-text>
              <n-text depth="3" class="store-count">{{ store.exists ? store.files.length : 0 }}</n-text>
            </div>
            <template v-if="store.open">
              <div v-if="store.indexSummary" class="store-meta">
                <n-text depth="3" v-if="store.indexSummary.lessonCount != null">
                  {{ store.indexSummary.lessonCount }} rules
                </n-text>
                <n-text depth="3" v-if="store.indexSummary.updated">
                  · {{ store.indexSummary.updated }}
                </n-text>
              </div>
              <div
                v-for="f in store.files"
                :key="`${store.store}-${f.name}`"
                class="file-item"
                :class="{ active: selectedKey === `${store.store}/${f.name}` }"
                @click="selectFile(store.store, f.name)"
              >
                <code>{{ f.name }}</code>
                <n-text depth="3" class="file-size">{{ formatSize(f.sizeBytes) }}</n-text>
              </div>
              <n-empty
                v-if="!store.exists"
                size="small"
                description="(no files)"
                style="margin: 8px 0"
              />
            </template>
          </div>
        </n-layout-sider>

        <!-- Right: preview / edit -->
        <n-layout-content content-style="padding: 12px 16px; overflow: auto">
          <template v-if="!selectedKey">
            <n-empty description="Select a memory file to preview or edit." style="padding: 48px 0" />
          </template>
          <template v-else>
            <div class="detail-head">
              <n-text class="detail-title"><code>{{ selectedKey }}</code></n-text>
              <div class="detail-actions">
                <n-button
                  v-if="!editing"
                  size="small"
                  secondary
                  @click="startEdit"
                >
                  Edit
                </n-button>
                <template v-else>
                  <n-button
                    size="small"
                    type="primary"
                    :loading="saving"
                    :disabled="!draft.trim()"
                    @click="save"
                  >
                    Save
                  </n-button>
                  <n-button size="small" quaternary @click="cancelEdit">Cancel</n-button>
                </template>
              </div>
            </div>
            <n-alert
              v-if="runWarning"
              type="warning"
              :bordered="false"
              style="margin-bottom: 8px; font-size: 12px"
            >
              {{ runWarning }}
            </n-alert>
            <pre v-if="!editing" class="memory-text">{{ content }}</pre>
            <n-input
              v-else
              v-model:value="draft"
              type="textarea"
              :rows="24"
              placeholder="Memory file content…"
              style="font-family: 'Cascadia Code', Consolas, monospace; font-size: 12px"
            />
          </template>
        </n-layout-content>
      </n-layout>
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { ChevronDown, ChevronForward } from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import { getMemoryStores, getMemoryFile, putMemoryFile } from '@/api'
import type { MemoryStore } from '@/api'

interface UiStore extends MemoryStore {
  open: boolean
}

// Optional deep-link hint from the Flow Injection Map: { store, file, n }. An
// empty store means "the target is module-scoped but the module can't be known
// statically" — we just ensure the panel is loaded so the user can pick.
const props = defineProps<{ selectHint?: { store: string; file: string; n: number } | null }>()

const loading = ref(false)
const error = ref<string | null>(null)
const stores = ref<UiStore[]>([])
const selectedKey = ref<string | null>(null)
const content = ref<string>('')
const editing = ref(false)
const draft = ref<string>('')
const saving = ref(false)
const runWarning = ref<string | null>(null)
const message = useMessage()

let currentStore = ''
let currentFile = ''

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes}B`
  return `${(bytes / 1024).toFixed(1)}KB`
}

function toggleStore(name: string) {
  const s = stores.value.find((x) => x.store === name)
  if (s) s.open = !s.open
}

async function loadStores() {
  loading.value = true
  error.value = null
  try {
    const { stores: list } = await getMemoryStores()
    // Default-open the self store (most commonly browsed).
    stores.value = list.map((s) => ({ ...s, open: s.store === 'self' }) as UiStore)
  } catch (e) {
    error.value = String((e as Error).message || e)
  } finally {
    loading.value = false
  }
}

async function selectFile(store: string, file: string) {
  currentStore = store
  currentFile = file
  selectedKey.value = `${store}/${file}`
  editing.value = false
  draft.value = ''
  runWarning.value = null
  error.value = null
  try {
    const res = await getMemoryFile(store, file)
    content.value = res.content
  } catch (e) {
    content.value = ''
    error.value = String((e as Error).message || e)
  }
}

function startEdit() {
  draft.value = content.value
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  draft.value = ''
}

async function save() {
  if (!draft.value.trim()) return
  saving.value = true
  runWarning.value = null
  try {
    const res = await putMemoryFile(currentStore, currentFile, draft.value)
    content.value = draft.value
    editing.value = false
    if (res.warning) runWarning.value = res.warning
    message.success(`Saved ${currentStore}/${currentFile}`)
    // Refresh store sizes (file may have grown).
    await loadStores()
  } catch (e) {
    message.error(`Save failed: ${(e as Error).message}`)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadStores()
  // Pick up a pending hint set before this panel mounted (n-tabs default
  // display-directive='if' → fresh mount on tab switch → non-immediate watch
  // won't fire). Mirrors the PromptLibrary deep-link fix.
  const hint = props.selectHint
  if (hint) {
    if (!hint.store) return
    const s = stores.value.find((x) => x.store === hint.store)
    if (s) s.open = true
    if (hint.file) await selectFile(hint.store, hint.file)
  }
})

// React to subsequent deep-link hints while already mounted. Opens the target
// store and selects the file. An empty store (moduleMemoryIndex — module
// unknowable statically) just ensures the stores are loaded so the user can
// pick manually.
watch(
  () => props.selectHint,
  async (hint) => {
    if (!hint) return
    if (!stores.value.length) await loadStores()
    if (!hint.store) return
    const s = stores.value.find((x) => x.store === hint.store)
    if (s) s.open = true
    if (hint.file) await selectFile(hint.store, hint.file)
  },
)
</script>

<style scoped>
.memory-browser {
  display: flex;
  flex-direction: column;
}
.store-group {
  margin-bottom: 8px;
}
.store-head {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 4px;
}
.store-head:hover {
  background: rgba(59, 130, 246, 0.1);
}
.store-label {
  font-weight: 600;
  font-size: 13px;
}
.store-count {
  font-size: 11px;
  margin-left: auto;
}
.store-meta {
  font-size: 11px;
  padding: 0 6px 4px 24px;
}
.file-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 3px 6px 3px 24px;
  cursor: pointer;
  border-radius: 4px;
  font-size: 12px;
}
.file-item:hover {
  background: rgba(59, 130, 246, 0.12);
}
.file-item.active {
  background: rgba(59, 130, 246, 0.22);
}
.file-size {
  font-size: 10px;
}
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.detail-title {
  font-size: 14px;
  font-weight: 600;
}
.detail-actions {
  display: flex;
  gap: 8px;
}
.memory-text {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Cascadia Code', 'Fira Code', Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  margin: 0;
}
</style>
