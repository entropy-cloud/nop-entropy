<template>
  <div class="flow-step-node" :class="{ entry: data.isEntry, subflow: data.isSubflow, terminal: data.isTerminal }">
    <div class="node-head">
      <span class="node-label">{{ data.label }}</span>
      <n-tag v-if="data.isEntry" size="tiny" type="primary" round :bordered="false">entry</n-tag>
      <n-tag v-if="data.isSubflow" size="tiny" type="info" round :bordered="false">subflow</n-tag>
      <n-tag v-if="data.stepType" size="tiny" :bordered="false" round>{{ data.stepType }}</n-tag>
    </div>
    <div v-if="data.promptPath" class="node-prompt">
      <a class="node-prompt-link" title="View in Prompt Library" @click.stop="onPromptClick">{{ promptBase }}</a>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, inject } from 'vue'
import type { NodeProps } from '@vue-flow/core'
import { NTag, useMessage } from 'naive-ui'
import type { FlowNodeData } from './flowLayout'

// Vue Flow custom node. Declaring props as NodeProps<FlowNodeData> makes the
// component satisfy Vue Flow's NodeTypesObject (data/selected/id/... are all
// injected at runtime). The prompt deep-link handler is provided by
// FlowStateMachine (Vue Flow node-types don't forward custom props, so
// provide/inject is the clean channel).
const props = defineProps<NodeProps<FlowNodeData>>()

const onViewPrompt = inject<(name: string) => void>('flowViewPrompt', () => {})
const message = useMessage()

const promptBase = computed(() =>
  props.data?.promptPath ? props.data.promptPath.replace(/^.*[\\/]/, '').replace(/\.md$/i, '') : '',
)

function onPromptClick(): void {
  if (!promptBase.value) {
    message.info('Open this step in the table view to see its details.')
    return
  }
  onViewPrompt(promptBase.value)
}
</script>

<style scoped>
.flow-step-node {
  min-width: 160px;
  padding: 6px 10px;
  border-radius: 8px;
  background: #1e293b;
  border: 1px solid #475569;
  color: #e2e8f0;
  font-size: 12px;
  cursor: default;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
}
.flow-step-node.entry {
  border-color: #60a5fa;
  box-shadow: 0 0 0 2px rgba(96, 165, 250, 0.25);
}
.flow-step-node.subflow {
  border-color: #38bdf8;
  border-style: dashed;
}
.flow-step-node.terminal {
  background: #0f172a;
  border-color: #64748b;
  border-style: dotted;
  color: #94a3b8;
}
.node-head {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.node-label {
  font-weight: 600;
  font-family: 'Cascadia Code', Consolas, monospace;
}
.node-prompt {
  margin-top: 4px;
  font-size: 11px;
  font-family: 'Cascadia Code', Consolas, monospace;
}
.node-prompt-link {
  color: #60a5fa;
  cursor: pointer;
  text-decoration: underline dotted;
}
.node-prompt-link:hover {
  color: #93c5fd;
}
</style>
