<template>
  <n-modal
    :show="show"
    title="Base Config"
    preset="card"
    style="width: min(720px, 90vw); max-height: 80vh"
    :mask-closable="true"
    @update:show="(v: boolean) => emit('update:show', v)"
  >
    <template #header-extra>
      <n-button size="tiny" quaternary @click="copyConfig">
        <template #icon><n-icon :component="CopyOutline" /></template>
        复制 JSON
      </n-button>
    </template>

    <n-spin :show="loading" description="Loading base config…">
      <n-alert v-if="errorMsg" type="error" :title="errorMsg" style="margin-bottom: 12px" />
      <n-empty v-else-if="!config" description="base.json not found." size="small" />
      <template v-else>
        <n-code
          :code="configJson"
          language="json"
          word-wrap
          style="max-height: 56vh"
        />
        <n-text depth="3" style="display: block; margin-top: 10px; font-size: 12px;">
          合并来源: {{ sources }}
        </n-text>
      </template>
    </n-spin>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  NAlert,
  NButton,
  NCode,
  NEmpty,
  NIcon,
  NModal,
  NSpin,
  NText,
  useMessage,
} from 'naive-ui'
import { CopyOutline } from '@vicons/ionicons5'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ 'update:show': [value: boolean] }>()

const message = useMessage()
const loading = ref(false)
const errorMsg = ref<string | null>(null)
const config = ref<Record<string, unknown> | null>(null)
const sources = ref('')

const configJson = computed(() =>
  config.value ? JSON.stringify(config.value, null, 2) : '',
)

async function fetchConfig(): Promise<void> {
  loading.value = true
  errorMsg.value = null
  try {
    const res = await fetch('/api/configs/base')
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const data = await res.json()
    config.value = data.config ?? null
    sources.value = (data.sources as string[] | undefined)?.join(' + ') ?? ''
    if (data.error) errorMsg.value = data.error as string
  } catch (err: unknown) {
    errorMsg.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

async function copyConfig(): Promise<void> {
  if (!configJson.value) return
  try {
    await navigator.clipboard.writeText(configJson.value)
    message.success('Base config copied')
  } catch {
    message.error('Copy failed')
  }
}

watch(
  () => props.show,
  (v) => {
    if (v) void fetchConfig()
  },
)
</script>
