// Pure layout helper for the Flow Injection Map state-machine graph view.
// Turns a flow's steps + transition edges into Vue Flow nodes/edges with a
// readable layout: entry → main chain down the left column (following the
// first non-terminal, non-retry goto), branch nodes in a right column, and
// synthetic terminal nodes (done:<status>) in a far-right column. Cycle-safe
// (the mission-driver main flow loops DRAFT↔REVIEW/EXEC, so a visited set
// stops the main-chain walk at the first repeat).
//
// Kept framework-agnostic (returns plain Node/Edge shapes) so it is unit-
// testable without mounting Vue Flow.

import type { Node, Edge } from '@vue-flow/core'
import { MarkerType } from '@vue-flow/core'
import type { InjectionStep, InjectionEdge } from '@/api'

export interface FlowNodeData {
  label: string
  stepType: string | null
  isEntry: boolean
  isSubflow: boolean
  isTerminal: boolean
  promptPath: string | null
}

/** Extra data BundleEdge reads to fan out parallel edges + color the stroke. */
export interface FlowEdgeData {
  bundleOffset: number
  marker: string
  dashed: boolean
  color: string
}

const NODE_GAP_Y = 120
const MAIN_X = 0
const BRANCH_X = 420
const TERMINAL_X = 860
// Lateral gap between fanned-out parallel edges in the same (from→to) bundle.
const BUNDLE_GAP = 55

// Transition marker → stroke/arrow color. Kept in lock-step with BundleEdge's
// stroke (which reads data.color) so the line and its arrowhead always match.
function edgeColor(marker: string, dashed: boolean): string {
  if (marker === 'fail' || marker === 'all_failed') return '#ef4444' // red
  if (dashed || marker === 'error') return '#f59e0b' // amber (exceptional)
  if (['pass', 'all_complete', 'created', 'approved', 'clean'].includes(marker)) return '#22c55e' // happy
  return '#64748b' // neutral
}

export function computeFlowLayout(
  steps: InjectionStep[],
  edges: InjectionEdge[],
  entry: string | null,
): { nodes: Node<FlowNodeData>[]; edges: Edge[] } {
  const stepByName = new Map(steps.map((s) => [s.name, s]))

  // First non-terminal, non-retry goto per step — the main-chain follower.
  const firstGoto: Record<string, string> = {}
  for (const e of edges) {
    if (!e.terminal && !e.retry && !firstGoto[e.from]) firstGoto[e.from] = e.to
  }

  // Main chain: entry → firstGoto, cycle-safe.
  const mainChain: string[] = []
  const inChain = new Set<string>()
  let cur: string | null = entry || null
  while (cur && stepByName.has(cur) && !inChain.has(cur)) {
    inChain.add(cur)
    mainChain.push(cur)
    cur = firstGoto[cur] || null
  }

  const branchSteps = steps.filter((s) => !inChain.has(s.name))

  // Synthetic terminal nodes (unique done:<status> targets).
  const terminalIds: string[] = []
  const seenTerminal = new Set<string>()
  for (const e of edges) {
    if (e.terminal && !seenTerminal.has(e.to)) {
      seenTerminal.add(e.to)
      terminalIds.push(e.to)
    }
  }

  const positions: Record<string, { x: number; y: number }> = {}
  mainChain.forEach((name, i) => {
    positions[name] = { x: MAIN_X, y: i * NODE_GAP_Y }
  })
  const branchStartY = Math.max(0, Math.floor(mainChain.length / 2) - 1) * NODE_GAP_Y
  branchSteps.forEach((s, i) => {
    positions[s.name] = { x: BRANCH_X, y: branchStartY + i * NODE_GAP_Y }
  })
  terminalIds.forEach((id, i) => {
    positions[id] = { x: TERMINAL_X, y: i * NODE_GAP_Y }
  })

  const nodes: Node<FlowNodeData>[] = steps.map((s) => ({
    id: s.name,
    type: 'flowStep',
    position: positions[s.name] || { x: BRANCH_X, y: (mainChain.length + 1) * NODE_GAP_Y },
    data: {
      label: s.name,
      stepType: s.type,
      isEntry: s.isEntry,
      isSubflow: !!s.subflowName,
      isTerminal: false,
      promptPath: s.promptPath,
    },
  }))
  for (const id of terminalIds) {
    nodes.push({
      id,
      type: 'flowStep',
      position: positions[id],
      data: { label: id, stepType: null, isEntry: false, isSubflow: false, isTerminal: true, promptPath: null },
    })
  }

  // Group parallel edges by (from→to) so each bundle can be fanned out by
  // BundleEdge. Within a group of size N, offsets are symmetric around 0:
  // (i - (N-1)/2) * BUNDLE_GAP → e.g. N=3 gives [-55, 0, +55]. Single edges
  // get offset 0. Edges are sorted by marker within the group for deterministic
  // ordering across reloads.
  const groupKey = (e: InjectionEdge) => `${e.from}::${e.to}`
  const groups = new Map<string, InjectionEdge[]>()
  for (const e of edges) {
    const k = groupKey(e)
    const arr = groups.get(k)
    if (arr) arr.push(e)
    else groups.set(k, [e])
  }

  const flowEdges: Edge[] = []
  let edgeIdx = 0
  for (const [, group] of groups) {
    const sorted = [...group].sort((a, b) => a.marker.localeCompare(b.marker))
    const n = sorted.length
    sorted.forEach((e, i) => {
      const dashed = !!e.dashed || e.marker === 'error'
      const color = edgeColor(e.marker, dashed)
      const data: FlowEdgeData = {
        bundleOffset: (i - (n - 1) / 2) * BUNDLE_GAP,
        marker: e.marker,
        dashed,
        color,
      }
      flowEdges.push({
        id: `e${edgeIdx++}-${e.from}-${e.marker}-${e.to}`,
        source: e.from,
        target: e.to,
        label: e.marker,
        type: 'bundle',
        animated: e.retry,
        // Vue Flow resolves this to a <marker> def + passes the id string to
        // the custom edge as props.markerEnd; BundleEdge forwards it to BaseEdge.
        markerEnd: { type: MarkerType.ArrowClosed, width: 18, height: 18, color },
        data,
      })
    })
  }

  return { nodes, edges: flowEdges }
}
