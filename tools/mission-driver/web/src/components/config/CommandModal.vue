<template>
  <n-modal
    :show="show"
    title="执行命令"
    preset="card"
    style="width: min(640px, 90vw)"
    :mask-closable="true"
    @update:show="(v: boolean) => emit('update:show', v)"
  >
    <!-- A. 当前 Mission 命令 — 仅当 missionName 存在时显示 (FSD §2.2.2) -->
    <template v-if="missionName">
      <n-text depth="3" class="section-title">当前 Mission: {{ missionName }}</n-text>
      <div v-for="cmd in missionCommands" :key="cmd" class="cmd-row">
        <n-code :code="cmd" word-wrap class="cmd-code" />
        <n-button quaternary circle size="tiny" title="复制" @click="copyCommand(cmd)">
          <template #icon><n-icon :component="CopyOutline" /></template>
        </n-button>
      </div>
      <n-divider v-if="missionName" class="section-divider" />
    </template>

    <!-- B. 全局命令 — 始终显示 (FSD §2.2.2) -->
    <n-text depth="3" class="section-title">全局命令</n-text>
    <div v-for="cmd in globalCommands" :key="cmd" class="cmd-row">
      <n-code :code="cmd" word-wrap class="cmd-code" />
      <n-button quaternary circle size="tiny" title="复制" @click="copyCommand(cmd)">
        <template #icon><n-icon :component="CopyOutline" /></template>
      </n-button>
    </div>

    <n-text depth="3" class="footer-text">
      在终端中执行。Windows 需在 Git Bash 或 WSL 下运行。<br/>
      环境变量: <n-code code="OPENCODE_MODEL=<id> OPENCODE_AGENT=<agent> MAX_CYCLES=<n>" style="font-size:11px" />
    </n-text>
  </n-modal>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { NButton, NCode, NDivider, NIcon, NModal, NText, useMessage } from 'naive-ui'
import { CopyOutline } from '@vicons/ionicons5'

const props = defineProps<{ show: boolean; missionName?: string | null }>()
const emit = defineEmits<{ 'update:show': [value: boolean] }>()

const message = useMessage()

const SH = './tools/mission-driver.sh'

// 当前 Mission 命令 (FSD §2.2.2 A 区) — 完整命令集
const missionCommands = computed<string[]>(() => {
  const name = props.missionName
  if (!name) return []
  return [
    `${SH} ${name}`,
    `${SH} ${name} --dry-run`,
    `${SH} ${name} --step CHECK`,
    `${SH} ${name} --max-cycles 5`,
    `${SH} ${name} --model <id>`,
    `${SH} ${name} --pure`,
  ]
})

// 全局命令 (FSD §2.2.2 B 区) — 完整命令集，含子命令形式和旧标志形式
const globalCommands = computed<string[]>(() => [
  `${SH} -h`,
  `${SH} list`,
  `${SH} list-steps <mission>`,
  `${SH} draft "<描述>"`,
  `${SH} analyze`,
  `${SH} analyze <run-dir>`,
  `${SH} monitor`,
  `${SH} monitor --dev`,
])

async function copyCommand(cmd: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(cmd)
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}
</script>

<style scoped>
.section-title {
  display: block;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.cmd-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.cmd-code {
  flex: 1;
  min-width: 0;
}
.section-divider {
  margin: 12px 0;
}
.footer-text {
  display: block;
  margin-top: 12px;
  font-size: 12px;
}
</style>
