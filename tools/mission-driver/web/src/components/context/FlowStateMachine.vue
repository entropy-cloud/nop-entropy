<template>
  <div class="flow-state-machine">
    <VueFlow
      :key="map.flowName"
      :nodes="nodes"
      :edges="edges"
      :node-types="nodeTypes"
      :edge-types="edgeTypes"
      fit-view-on-init
      :min-zoom="0.2"
      :max-zoom="1.8"
      @node-click="onNodeClick"
    >
      <Background :gap="16" pattern-color="#334155" />
      <Controls position="bottom-left" />
      <MiniMap pannable zoomable :node-color="miniMapNodeColor" />
    </VueFlow>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, provide } from 'vue'
import { VueFlow, type Node, type NodeMouseEvent } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'

import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'

import FlowStepNode from './FlowStepNode.vue'
import BundleEdge from './BundleEdge.vue'
import { computeFlowLayout, type FlowNodeData } from './flowLayout'
import type { InjectionMap } from '@/api'

// State-machine graph view for a flow. Uses the vue-flow graph pattern
// (dark canvas, minimap) driven by the injection map's steps + edges.
// Two refresh correctness points:
//   1. `:key="map.flowName"` forces a clean VueFlow remount when the user
//      switches flow in the dropdown — Vue Flow keeps internal node/edge state
//      that a plain prop swap does not fully reconcile, so remounting is the
//      robust way to repaint.
//   2. nodes/edges are a computed so they recompute whenever the parent passes
//      a refreshed map (covers in-place map updates without a flow change).
// Parallel edges (multiple markers between the same two steps, e.g.
// REVIEW_PLANS → EXEC_PLANS via all_complete/some_failed/all_failed) are fanned
// out by the custom `bundle` edge type — see BundleEdge.vue + flowLayout grouping.
const props = defineProps<{
  map: InjectionMap
  selectedStep?: string | null
  onViewPrompt?: (name: string) => void
}>()

const emit = defineEmits<{
  'select-step': [stepName: string]
}>()

const nodeTypes = { flowStep: markRaw(FlowStepNode) }
const edgeTypes = { bundle: markRaw(BundleEdge) }

const layout = computed(() =>
  computeFlowLayout(props.map.steps, props.map.edges || [], props.map.entry),
)
const nodes = computed(() => layout.value.nodes)
const edges = computed(() => layout.value.edges)

// Forward prompt deep-links from the custom node (Vue Flow doesn't pass custom
// props to node components, so this goes through provide/inject).
provide('flowViewPrompt', (name: string) => props.onViewPrompt?.(name))

function onNodeClick(evt: NodeMouseEvent): void {
  // Synthetic terminal nodes (done:*) have no injection detail — ignore.
  if (evt.node.id.startsWith('done:')) return
  emit('select-step', evt.node.id)
}

function miniMapNodeColor(n: Node<FlowNodeData>): string {
  if (n.data?.isTerminal) return '#64748b'
  if (n.data?.isEntry) return '#60a5fa'
  if (n.data?.isSubflow) return '#38bdf8'
  return '#475569'
}
</script>

<style scoped>
.flow-state-machine {
  width: 100%;
  height: 560px;
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
  overflow: hidden;
}
</style>
