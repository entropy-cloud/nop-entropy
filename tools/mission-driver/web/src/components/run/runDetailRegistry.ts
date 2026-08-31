// FSD §5.2 — RunDetail renderer registry (itp2-3).
//
// RunDetail.vue is now a thin shell that owns the data lifecycle (store init,
// SSE connect/disconnect, sysmon) and dispatches a *renderer component* based
// on the run's `flowName`. The default renderer (DefaultRunDetail) reproduces
// the legacy StepTimeline + config/log/chart/plans/roadmap layout.
//
// Dispatch rules (resolveRenderer):
//   - null/undefined flowName           → DefaultRunDetail (sync)
//   - registered flowName               → its lazy loader (async chunk)
//   - unregistered non-empty flowName   → DefaultRunDetail (sync)
//
// DefaultRunDetail is imported synchronously because it is the common path and
// needed on first paint. Project-specific renderers can be registered below as
// dynamic-import factories so their heavy deps stay out of the first-screen
// bundle (NFR-3 / NFR-6).

import type { Component } from 'vue'
import { defineAsyncComponent } from 'vue'
import DefaultRunDetail from './detail/DefaultRunDetail.vue'

// Map of flowName → lazy loader. Empty by default; the built-in mission-driver
// flow always falls through to DefaultRunDetail.
export const runDetailRenderers: Record<string, () => Promise<Component>> = {}

// Cache defineAsyncComponent results per flowName so resolveRenderer is
// idempotent across re-renders: a fresh wrapper each call would remount the
// async component and lose its internal state.
const asyncComponentCache = new Map<string, Component>()

/**
 * Resolve the renderer for a run's flowName.
 *
 * Returns DefaultRunDetail (synchronously imported) for null/unknown values,
 * or a cached async component for a registered flowName.
 */
export function resolveRenderer(flowName?: string | null): Component {
  if (!flowName) return DefaultRunDetail
  const loader = runDetailRenderers[flowName]
  if (!loader) return DefaultRunDetail
  let comp = asyncComponentCache.get(flowName)
  if (!comp) {
    comp = defineAsyncComponent(loader)
    asyncComponentCache.set(flowName, comp)
  }
  return comp
}
