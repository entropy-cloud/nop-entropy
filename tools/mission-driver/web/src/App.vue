<template>
  <n-config-provider :theme="darkTheme" :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-layout style="height: 100vh">
        <!-- App shell only. Each view owns its own AppHeader bar (FSD §4.2/§4.3)
             so RunDetail can swap the brand header for a back+mission+status bar. -->
        <n-layout-content
          content-style="padding: 12px 24px 24px; height: 100vh; overflow: auto"
        >
          <router-view />
        </n-layout-content>
      </n-layout>
      <BaseConfigModal v-model:show="showBaseConfig" />
      <CommandModal v-model:show="showCommandModal" :mission-name="commandModalMission" />
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { ref, provide } from 'vue'
import { darkTheme } from 'naive-ui'
import type { GlobalThemeOverrides } from 'naive-ui'
import BaseConfigModal from '@/components/config/BaseConfigModal.vue'
import CommandModal from '@/components/config/CommandModal.vue'

// FSD §3.7 — project palette overlays on top of Naive UI darkTheme.
const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#3b82f6',
    primaryColorHover: '#60a5fa',
    primaryColorPressed: '#2563eb',
    bodyColor: '#0f172a',
    cardColor: '#1e293b',
    borderColor: '#334155',
    textColorBase: '#e2e8f0',
  },
  Tag: {
    borderRadius: '9999px',
  },
}

const showBaseConfig = ref(false)
provide('openBaseConfig', () => { showBaseConfig.value = true })

// CommandModal: a single instance shared by both RunList and RunDetail. The
// missionName is passed in at open-time so RunDetail can show mission-specific
// commands while RunList only shows the global command block.
const showCommandModal = ref(false)
const commandModalMission = ref<string | null>(null)
provide('openCommandModal', (missionName?: string | null) => {
  commandModalMission.value = missionName ?? null
  showCommandModal.value = true
})
</script>
