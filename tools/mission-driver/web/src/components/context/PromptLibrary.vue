<template>
  <div class="prompt-library">
    <n-spin :show="loading">
      <n-alert v-if="error" type="error" :title="error" style="margin-bottom: 12px" />
      <n-grid v-else :cols="2" :x-gap="12" :y-gap="12" responsive="screen">
        <!-- Left: prompt list -->
        <n-grid-item>
          <n-card size="small" title="Prompts" style="height: 70vh; overflow: auto">
            <n-input
              v-model:value="filter"
              placeholder="Filter prompts…"
              size="small"
              clearable
              style="margin-bottom: 8px"
            />
            <div
              v-for="p in filteredPrompts"
              :key="p.name"
              class="prompt-item"
              :class="{ active: selectedName === p.name }"
              @click="selectPrompt(p.name)"
            >
              <div class="prompt-name">
                <code>{{ p.name }}</code>
                <n-text depth="3" class="prompt-count">{{ p.vars.length }} vars · {{ p.usedBy.length }} uses</n-text>
              </div>
              <n-text depth="3" class="prompt-summary">{{ p.summary }}</n-text>
            </div>
          </n-card>
        </n-grid-item>

        <!-- Right: detail view -->
        <n-grid-item>
          <n-card size="small" style="height: 70vh; overflow: auto">
            <template #header>
              <span v-if="selectedName"><code>{{ selectedName }}</code></span>
              <span v-else>Select a prompt</span>
            </template>
            <n-spin :show="detailLoading">
              <n-empty
                v-if="!detail && !detailLoading"
                description="Click a prompt to preview its full text with {{var}} highlighting."
                style="padding: 24px 0"
              />
              <template v-else-if="detail">
                <div v-if="usedByForSelected.length" class="used-by">
                  <n-text depth="3">Used by:</n-text>
                  <n-tag
                    v-for="u in usedByForSelected"
                    :key="`${u.flow}-${u.step}`"
                    size="small"
                    type="info"
                    round
                  >
                    {{ u.flow }} / {{ u.step }}
                  </n-tag>
                </div>
                <pre class="prompt-text" v-html="highlightedContent"></pre>
              </template>
            </n-spin>
          </n-card>
        </n-grid-item>
      </n-grid>
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { getPrompts, getPromptDetail } from '@/api'
import type { PromptSummary, PromptDetail } from '@/api'

// Optional deep-link hint from the Flow Injection Map: { name, n }. The nonce
// makes every click a new object reference so the watch re-fires even when the
// same prompt is requested twice. Selecting also works before the list finishes
// loading (the detail fetch is independent of the list).
const props = defineProps<{ selectHint?: { name: string; n: number } | null }>()

const loading = ref(false)
const detailLoading = ref(false)
const error = ref<string | null>(null)
const prompts = ref<PromptSummary[]>([])
const filter = ref('')
const selectedName = ref<string | null>(null)
const detail = ref<PromptDetail | null>(null)

const filteredPrompts = computed(() => {
  const f = filter.value.trim().toLowerCase()
  if (!f) return prompts.value
  return prompts.value.filter(
    (p) => p.name.toLowerCase().includes(f) || p.summary.toLowerCase().includes(f),
  )
})

const usedByForSelected = computed(() => {
  if (!selectedName.value) return []
  return prompts.value.find((p) => p.name === selectedName.value)?.usedBy ?? []
})

// Highlight {{var}} placeholders in the prompt text via a regex replace into
// <code> spans. The content is escaped first to avoid raw HTML injection.
const highlightedContent = computed(() => {
  if (!detail.value) return ''
  const escaped = detail.value.content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return escaped.replace(/\{\{(\w+)\}\}/g, '<code class="ph">{{$1}}</code>')
})

async function loadPrompts() {
  loading.value = true
  error.value = null
  try {
    const { prompts: list } = await getPrompts()
    prompts.value = list
  } catch (e) {
    error.value = String((e as Error).message || e)
  } finally {
    loading.value = false
  }
}

async function selectPrompt(name: string) {
  selectedName.value = name
  detail.value = null
  detailLoading.value = true
  try {
    detail.value = await getPromptDetail(name)
  } catch (e) {
    error.value = String((e as Error).message || e)
  } finally {
    detailLoading.value = false
  }
}

onMounted(async () => {
  await loadPrompts()
  // When the Flow Injection Map deep-links into this panel, the tab switch
  // mounts PromptLibrary fresh (n-tabs default display-directive='if'), so the
  // selectHint watch below does NOT fire (it's set before mount + not immediate).
  // Pick up a pending hint here after the list loads.
  if (props.selectHint?.name) {
    await selectPrompt(props.selectHint.name)
  }
})

// React to subsequent deep-link hints while already mounted (repeat clicks from
// the Flow Injection Map without a tab remount). Ensures the prompt list is
// loaded (so the highlight + used-by show) then selects the target.
watch(
  () => props.selectHint,
  async (hint) => {
    if (!hint || !hint.name) return
    if (!prompts.value.length) await loadPrompts()
    await selectPrompt(hint.name)
  },
)
</script>

<style scoped>
.prompt-library {
  display: flex;
  flex-direction: column;
}
.prompt-item {
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  margin-bottom: 4px;
}
.prompt-item:hover {
  background: rgba(59, 130, 246, 0.12);
}
.prompt-item.active {
  background: rgba(59, 130, 246, 0.22);
}
.prompt-name {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.prompt-count {
  font-size: 11px;
  white-space: nowrap;
}
.prompt-summary {
  display: block;
  font-size: 12px;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.used-by {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-bottom: 12px;
}
.prompt-text {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Cascadia Code', 'Fira Code', Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  margin: 0;
}
.prompt-text :deep(code.ph) {
  color: #60a5fa;
  background: rgba(59, 130, 246, 0.15);
  padding: 1px 4px;
  border-radius: 3px;
}
</style>
