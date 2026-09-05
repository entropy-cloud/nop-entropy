<template>
  <div class="context-explorer">
    <AppHeader title="Context & Memory Explorer" show-back />

    <div class="section">
      <n-tabs v-model:value="activeTab" type="line" animated>
        <n-tab-pane name="injection" tab="Flow">
          <FlowInjectionMap :on-view-prompt="viewPrompt" :on-view-memory="viewMemory" />
        </n-tab-pane>
        <n-tab-pane name="memory" tab="Memory Browser">
          <MemoryBrowser :select-hint="memoryHint" />
        </n-tab-pane>
        <n-tab-pane name="prompts" tab="Prompt Library">
          <PromptLibrary :select-hint="promptHint" />
        </n-tab-pane>
      </n-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import AppHeader from '@/components/layout/AppHeader.vue'
import FlowInjectionMap from '@/components/context/FlowInjectionMap.vue'
import MemoryBrowser from '@/components/context/MemoryBrowser.vue'
import PromptLibrary from '@/components/context/PromptLibrary.vue'

// Cross-panel navigation state. The Flow Injection Map calls these handlers to
// deep-link into the other two panels (view a prompt / open a memory file),
// which avoids duplicating the detail UI in three places. Each hint carries a
// monotonically-increasing nonce so repeat clicks of the SAME target still
// re-trigger the child watch (a plain string equality wouldn't).
const activeTab = ref<'injection' | 'memory' | 'prompts'>('injection')
const promptHint = ref<{ name: string; n: number } | null>(null)
const memoryHint = ref<{ store: string; file: string; n: number } | null>(null)
let navNonce = 0

function viewPrompt(name: string): void {
  promptHint.value = { name, n: ++navNonce }
  activeTab.value = 'prompts'
}

function viewMemory(store: string, file: string): void {
  memoryHint.value = { store, file, n: ++navNonce }
  activeTab.value = 'memory'
}
</script>

<style scoped>
.context-explorer {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.section {
  display: flex;
  flex-direction: column;
}
</style>
