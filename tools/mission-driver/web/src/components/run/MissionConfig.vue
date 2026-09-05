<template>
  <n-empty v-if="!config" description="No mission config." size="small" />
  <n-descriptions
    v-else
    bordered
    :column="1"
    size="small"
    label-placement="left"
    :label-style="{ width: '130px', whiteSpace: 'nowrap', verticalAlign: 'top' }"
    content-style="word-break: break-all"
  >
    <n-descriptions-item label="description">
      {{ config.description || '—' }}
      <n-button
        v-if="config.description"
        size="tiny"
        quaternary
        class="cfg-copy"
        @click="copy(config.description)"
      >
        <template #icon><n-icon :component="CopyOutline" /></template>
      </n-button>
    </n-descriptions-item>

    <n-descriptions-item label="roadmapPath">
      {{ config.roadmapPath || '—' }}
      <n-button
        v-if="config.roadmapPath"
        size="tiny"
        quaternary
        class="cfg-copy"
        @click="copy(config.roadmapPath)"
      >
        <template #icon><n-icon :component="CopyOutline" /></template>
      </n-button>
    </n-descriptions-item>

    <n-descriptions-item label="plansDir">
      {{ config.plansDir || '—' }}
      <n-button
        v-if="config.plansDir"
        size="tiny"
        quaternary
        class="cfg-copy"
        @click="copy(config.plansDir)"
      >
        <template #icon><n-icon :component="CopyOutline" /></template>
      </n-button>
    </n-descriptions-item>

    <n-descriptions-item label="moduleDir">
      {{ config.moduleDir || '—' }}
      <n-button
        v-if="config.moduleDir"
        size="tiny"
        quaternary
        class="cfg-copy"
        @click="copy(config.moduleDir)"
      >
        <template #icon><n-icon :component="CopyOutline" /></template>
      </n-button>
    </n-descriptions-item>

    <n-descriptions-item label="flowName">
      {{ config.flowName || '—' }}
      <n-button
        v-if="config.flowName"
        size="tiny"
        quaternary
        class="cfg-copy"
        @click="copy(config.flowName)"
      >
        <template #icon><n-icon :component="CopyOutline" /></template>
      </n-button>
    </n-descriptions-item>

    <n-descriptions-item label="commitFormat">
      {{ config.commitFormat || '—' }}
      <n-button
        v-if="config.commitFormat"
        size="tiny"
        quaternary
        class="cfg-copy"
        @click="copy(config.commitFormat)"
      >
        <template #icon><n-icon :component="CopyOutline" /></template>
      </n-button>
    </n-descriptions-item>

    <n-descriptions-item label="commands" :span="1">
      <div v-if="config.commands && Object.keys(config.commands).length" class="commands-list">
        <div
          v-for="(val, key) in config.commands"
          :key="key"
          class="command-row"
        >
          <n-tag size="tiny" round>{{ key }}</n-tag>
          <span class="cmd-val" :title="val ?? ''">{{ val || '—' }}</span>
          <n-button v-if="val" size="tiny" quaternary @click="copy(val)">
            <template #icon><n-icon :component="CopyOutline" /></template>
          </n-button>
        </div>
      </div>
      <n-text v-else depth="3">—</n-text>
    </n-descriptions-item>
  </n-descriptions>
</template>

<script setup lang="ts">
import {
  NButton,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NIcon,
  NTag,
  NText,
  useMessage,
} from 'naive-ui'
import { CopyOutline } from '@vicons/ionicons5'
import type { MissionConfig } from '@/types/config'

defineProps<{
  config: MissionConfig | null
}>()

const message = useMessage()

async function copy(value: string | null | undefined): Promise<void> {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    message.success(`Copied: ${value.slice(0, 24)}${value.length > 24 ? '…' : ''}`)
  } catch {
    message.error('Copy failed — clipboard unavailable')
  }
}
</script>

<style scoped>
.cfg-copy {
  margin-left: 4px;
  vertical-align: middle;
}
.commands-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.command-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.cmd-val {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #cbd5e1;
  font-family: ui-monospace, monospace;
}
</style>
