<template>
  <div class="step-timeline">
    <n-empty
      v-if="steps.length === 0"
      description="No steps yet."
      size="small"
    />
    <n-timeline v-else size="medium">
      <n-timeline-item
        v-for="(step, idx) in steps"
        :key="stepKey(step, idx)"
        :type="timelineType(step.status)"
        class="tl-item"
        :class="{ selected: isSelected(idx), suspended: step.suspended }"
      >
        <!-- Header: name + duration + marker + collapse toggle -->
        <template #header>
          <span class="tl-title" @click.stop="onSelect(step, `p${idx}`, logFileArg(step))">
            {{ step.name }}
          </span>
          <span v-if="step.durationMs != null" class="tl-duration">{{ fmtDuration(step.durationMs) }}</span>
          <n-tag
            v-if="step.marker"
            :type="markerTagType(step.marker)"
            size="tiny"
            round
            class="marker-tag"
            :title="step.error || undefined"
          >
            {{ step.marker }}
          </n-tag>
          <n-tag
            v-if="step.suspended"
            type="warning"
            size="tiny"
            round
            :title="suspendTitle(step)"
          >
            ⚠ suspended
          </n-tag>
          <n-button
            v-if="step.type === 'subflow' && step.children?.length"
            size="tiny"
            quaternary
            class="collapse-toggle"
            @click.stop="toggleCollapse(idx)"
          >
            <template #icon>
              <n-icon :component="isCollapsed(idx) ? ChevronForwardOutline : ChevronDownOutline" />
            </template>
            {{ step.children.length }} sub
          </n-button>
        </template>

        <!-- Footer: error reason + visit count + log/session badges -->
        <template #footer>
          <span v-if="step.error" class="tl-error" :title="step.error">⚠ {{ step.error.slice(0, 120) }}</span>
          <span v-if="step.visits != null && step.visits > 1" class="tl-meta">visit {{ step.visits }}</span>
          <n-button
            v-if="logFileArg(step)"
            size="tiny"
            quaternary
            class="session-copy"
            title="有日志 — 点击查看"
            @click.stop="onSelect(step, `p${idx}`, logFileArg(step))"
          >
            <template #icon>
              <n-icon :component="DocumentTextOutline" />
            </template>
            log
          </n-button>
          <n-button
            v-if="step.sessionId"
            size="tiny"
            quaternary
            class="session-copy"
            title="复制 opencode 跟踪命令"
            @click.stop="copySession(step.sessionId)"
          >
            <template #icon>
              <n-icon :component="CopyOutline" />
            </template>
            session
          </n-button>
        </template>

        <!-- Subflow children: nested groups. Per FSD §4.4, render an inner
             timeline per child group. Falls back gracefully for empty children. -->
        <template v-if="step.type === 'subflow' && step.children?.length" #default>
          <div v-show="!isCollapsed(idx)" class="subflow-children" @click.stop>
            <div
              v-for="(group, gi) in step.children"
              :key="`${step.name}-child-${gi}`"
              class="subflow-group"
            >
              <!-- Label visibility:
                   - forEach subflow (step.forEach set by monitor backend, or
                     child has forEachItem): show "📋 Plan N: name" for every
                     child, including disk-only in-flight ones whose
                     forEachItem is null (they haven't been appended to
                     subflowRuns yet — engine writes on completion, not start).
                   - non-forEach subflow (DEEP_AUDIT, single child, no
                     forEachItem anywhere): hide the misleading "Plan N" label,
                     show only the status tag. -->
              <div class="subflow-label">
                <span v-if="step.forEach || group.forEachItem" :title="group.forEachItem ?? ''">
                  📋 Plan {{ group.forEachIndex + 1 }}{{ group.forEachItem ? `: ${shortPlanName(group.forEachItem)}` : '' }}
                </span>
                <n-tag
                  :type="timelineType(group.status)"
                  size="tiny"
                  round
                >
                  {{ group.status }}
                </n-tag>
              </div>
              <n-timeline class="subflow-inner">
                <n-timeline-item
                  v-for="(cs, csi) in group.steps"
                  :key="`${step.name}-${gi}-${csi}`"
                  :type="timelineType(cs.status)"
                  :time="fmtDuration(cs.durationMs)"
                  class="tl-item sub-item"
                  :class="{ selected: isChildSelected(idx, gi, csi) }"
                >
                  <template #header>
                    <span
                      class="tl-title"
                      @click.stop="onSelect(cs, `c${idx}_${gi}_${csi}`, logFileArg(cs))"
                    >
                      {{ cs.name }}
                    </span>
                    <span v-if="cs.durationMs != null" class="tl-duration">{{ fmtDuration(cs.durationMs) }}</span>
                    <n-tag
                      v-if="cs.marker"
                      :type="markerTagType(cs.marker)"
                      size="tiny"
                      round
                      class="marker-tag"
                      :title="cs.error || undefined"
                    >
                      {{ cs.marker }}
                    </n-tag>
                  </template>
                  <template #footer>
                    <span v-if="cs.error" class="tl-error" :title="cs.error">⚠ {{ cs.error.slice(0, 120) }}</span>
                    <span v-if="cs.visits != null && cs.visits > 1" class="tl-meta">visit {{ cs.visits }}</span>
                    <n-button
                      v-if="logFileArg(cs)"
                      size="tiny"
                      quaternary
                      class="session-copy"
                      title="有日志 — 点击查看"
                      @click.stop="onSelect(cs, `c${idx}_${gi}_${csi}`, logFileArg(cs))"
                    >
                      <template #icon>
                        <n-icon :component="DocumentTextOutline" />
                      </template>
                      log
                    </n-button>
                    <n-button
                      v-if="cs.sessionId"
                      size="tiny"
                      quaternary
                      class="session-copy"
                      title="复制 opencode 跟踪命令"
                      @click.stop="copySession(cs.sessionId)"
                    >
                      <template #icon>
                        <n-icon :component="CopyOutline" />
                      </template>
                      session
                    </n-button>
                  </template>
                </n-timeline-item>
              </n-timeline>
            </div>
          </div>
        </template>
      </n-timeline-item>
    </n-timeline>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { NButton, NEmpty, NIcon, NTag, NTimeline, NTimelineItem, useMessage } from 'naive-ui'
import { CopyOutline, DocumentTextOutline, ChevronDownOutline, ChevronForwardOutline } from '@vicons/ionicons5'
import type { Step, StepStatus } from '@/types/run'

const props = defineProps<{
  steps: Step[]
  /** Currently-selected step keys (parent `pN`, child `cN_M_K`), driven by
   *  missionStore.selectedStepKey so selection highlights survive re-renders. */
  selectedKey?: string | null
}>()

/** Collapsed parent step indices (by position in steps array). */
const collapsedParents = ref(new Set<number>())
function toggleCollapse(idx: number): void {
  const s = new Set(collapsedParents.value)
  if (s.has(idx)) s.delete(idx)
  else s.add(idx)
  collapsedParents.value = s
}
function isCollapsed(idx: number): boolean {
  return collapsedParents.value.has(idx)
}

const emit = defineEmits<{
  (e: 'select', payload: { step: Step; key: string; logFile: string }): void
}>()

const message = useMessage()

// status → Naive UI timeline type (FSD §4.4 icon mapping).
function timelineType(
  status: StepStatus | string | null | undefined,
): 'success' | 'info' | 'error' | 'warning' | 'default' {
  const s = status
  if (s === 'completed') return 'success'
  if (s === 'running') return 'info'
  if (s === 'failed') return 'error'
  if (s === 'skipped') return 'warning'
  return 'default'
}

// marker → tag type. pass→success, fail/failed→error, else default.
function markerTagType(marker: string): 'success' | 'error' | 'default' {
  if (marker === 'pass') return 'success'
  if (marker === 'fail' || marker === 'failed') return 'error'
  return 'default'
}

function fmtDuration(ms: number | null | undefined): string {
  if (ms == null || Number.isNaN(ms)) return ''
  const total = Math.max(0, Math.floor(ms / 1000))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  if (h > 0) return `${h}h${String(m).padStart(2, '0')}m`
  if (m > 0) return `${m}m${String(s).padStart(2, '0')}s`
  return `${s}s`
}

function shortPlanName(path: string): string {
  if (!path) return ''
  const parts = path.replace(/\\/g, '/').split('/')
  return parts[parts.length - 1].replace(/\.md$/, '')
}

/**
 * Normalize a step's log file into the bare `oc-*.log` filename the Monitor
 * Server's `?file=` param expects. run-state records `logFile` as an ABSOLUTE
 * path; the server basenames it too, but sending the bare name keeps the URL
 * clean and unambiguous. When a step has no real log file, return '' so the
 * viewer omits `?file=` and the server falls back to step-name prefix search.
 */
function logFileArg(step: { logFile?: string | null }): string {
  const lf = step.logFile
  if (!lf) return ''
  const base = lf.replace(/\\/g, '/').split('/').pop() ?? ''
  return /^oc-.*\.log$/.test(base) ? base : ''
}

function suspendTitle(step: Step): string {
  if (step.suspendGapMs != null) {
    return `系统挂起（墙钟跳变约 ${Math.round(step.suspendGapMs / 60000)} min）`
  }
  return '系统挂起（墙钟跳变）'
}

function stepKey(step: Step, idx: number): string {
  return `${step.name}-${idx}`
}

function isSelected(idx: number): boolean {
  return props.selectedKey === `p${idx}`
}
function isChildSelected(idx: number, gi: number, csi: number): boolean {
  return props.selectedKey === `c${idx}_${gi}_${csi}`
}

function onSelect(step: Step, key: string, logFile: string): void {
  emit('select', { step, key, logFile })
}

async function copySession(sessionId: string): Promise<void> {
  try {
    await navigator.clipboard.writeText('opencode --session ' + sessionId)
    message.success(`Copied: opencode --session ${sessionId.slice(0, 12)}…`)
  } catch {
    message.error('Copy failed — clipboard unavailable')
  }
}
</script>

<style scoped>
.step-timeline {
  padding: 4px 0;
}
.tl-title {
  cursor: pointer;
  font-weight: 500;
}
.tl-title:hover {
  color: #60a5fa;
}
.marker-tag {
  margin-left: 6px;
}
.tl-duration {
  margin-left: 4px;
  color: #7d8590;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.tl-error {
  display: block;
  color: #f87171;
  font-size: 11px;
  line-height: 1.4;
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}
.tl-meta {
  color: #94a3b8;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.session-copy {
  padding: 0 4px;
}
.collapse-toggle {
  padding: 0 4px;
  margin-left: 4px;
  font-size: 11px;
  opacity: 0.7;
}
.collapse-toggle:hover {
  opacity: 1;
}
.tl-item.suspended :deep(.n-timeline-item-timeline) {
  border-color: #eab308;
}
.tl-item.selected :deep(.n-timeline-item-content) {
  background: rgba(59, 130, 246, 0.12);
  border-radius: 4px;
}
.subflow-children {
  margin-top: 8px;
  padding-left: 4px;
  border-left: 2px solid #334155;
}
.subflow-group {
  margin-bottom: 8px;
}
.subflow-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 4px;
}
/* n-timeline `size` only accepts medium|large; compact the nested timeline via
   CSS (font + tighter padding) to visually nest subflow children. */
.subflow-inner {
  font-size: 12px;
}
.subflow-inner :deep(.n-timeline-item) {
  padding-bottom: 6px;
}
</style>
