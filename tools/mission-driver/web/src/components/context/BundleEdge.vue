<template>
  <BaseEdge :path="path" :style="edgeStyle" :marker-end="markerEnd" />
  <EdgeText
    v-if="label"
    :x="labelX"
    :y="labelY"
    :label="label"
    :label-style="labelStyle"
    :label-show-bg="true"
    :label-bg-style="labelBgStyle"
    :label-bg-padding="[2, 4]"
  />
</template>

<script setup lang="ts">
// BundleEdge — custom Vue Flow edge that fans out PARALLEL edges (multiple
// transitions between the same source→target pair, e.g. REVIEW_PLANS →
// EXEC_PLANS via all_complete / some_failed / all_failed). flowLayout.ts groups
// edges by (from,to) and assigns each a `bundleOffset`; this edge bows its
// cubic bezier control points horizontally by that offset so the bundle spreads
// side by side instead of stacking on top of each other.
//
// This is the standard React Flow / Vue Flow recipe for multi-edges: a custom
// edge that computes its own SVG path with a perpendicular curvature derived
// from the edge's index within its parallel group. Stroke color + arrow color
// are precomputed in flowLayout (data.color + edge.markerEnd) so the line and
// its arrowhead always match.
import { computed } from 'vue'
import { BaseEdge, EdgeText, type EdgeProps } from '@vue-flow/core'

const props = defineProps<EdgeProps>()

const offset = computed(() => Number(props.data?.bundleOffset) || 0)
const color = computed(() => String(props.data?.color ?? '#64748b'))
const dashed = computed(() => Boolean(props.data?.dashed))

// Cubic bezier: both control points pushed horizontally by `offset`. For a
// bundle of N edges, offsets are symmetric around 0 (e.g. -55, 0, +55), so the
// edges bow out to alternate sides and never overlap. A single edge (offset 0)
// renders as a gentle S-curve between the two handles.
const path = computed(() => {
  const { sourceX, sourceY, targetX, targetY } = props
  const off = offset.value
  const my = (sourceY + targetY) / 2
  return `M ${sourceX} ${sourceY} C ${sourceX + off} ${my} ${targetX + off} ${my} ${targetX} ${targetY}`
})

// Label sits on the bowed midpoint, shifted slightly further out than the path
// so it clears the line and parallel labels don't collide.
const labelX = computed(() => (props.sourceX + props.targetX) / 2 + offset.value * 0.7)
const labelY = computed(() => (props.sourceY + props.targetY) / 2)

const edgeStyle = computed(() => ({
  stroke: color.value,
  strokeWidth: '2px',
  strokeDasharray: dashed.value ? '5 4' : undefined,
}))

// Vue Flow resolves the edge's markerEnd object into a <marker> def and hands
// the resolved id string to the custom edge as props.markerEnd — just forward
// it to BaseEdge (empty string when no marker is configured).
const markerEnd = computed(() => props.markerEnd || undefined)

const labelStyle = computed(() => ({ fill: '#e2e8f0', fontSize: '11px', fontWeight: '600' }))
const labelBgStyle = computed(() => ({ fill: '#1e293b', fillOpacity: '0.9' }))
</script>
