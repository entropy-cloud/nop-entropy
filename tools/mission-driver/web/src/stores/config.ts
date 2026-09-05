// Pinia store — mission configs, roadmap progress, and plan file listings.
// FSD §5.3, §4.7 (RoadmapProgress), §4.9 (PlansTable).

import { defineStore } from 'pinia'
import { ref } from 'vue'
import type {
  MissionConfig,
  MissionConfigInfo,
  PlanInfo,
  RoadmapData,
} from '@/types/config'
import { getConfigs, getPlans, getRoadmap } from '@/api'

const EMPTY_ROADMAP: RoadmapData = { phases: [], overallProgress: 0 }

export const useConfigStore = defineStore('config', () => {
  const configs = ref<MissionConfigInfo[]>([])
  const currentConfig = ref<MissionConfig | null>(null)
  const roadmap = ref<RoadmapData>({ ...EMPTY_ROADMAP })
  const plans = ref<PlanInfo[]>([])
  const plansDir = ref<string | null>(null)

  // Pagination state for the Mission Configs list (FSD §3.4). Default page size
  // 9 matches the n-grid :cols="3" × 3 rows pattern on RunList.vue.
  const total = ref<number | null>(null)
  const hasMore = ref(false)
  const limit = ref(9)
  const offset = ref(0)
  const loadingMore = ref(false)

  /**
   * GET /api/configs → mission config summaries (paged).
   *
   * - `reset = true` (default): jump back to the first page and replace the list.
   * - `reset = false`: append the next page, de-duping by `name` (mirrors the
   *   runId de-dupe in RunList.vue:loadMore).
   */
  async function fetchConfigs(reset = true): Promise<void> {
    try {
      if (reset) {
        offset.value = 0
        const page = await getConfigs(limit.value, 0)
        configs.value = page.configs
        total.value = page.total ?? page.configs.length
        hasMore.value = page.hasMore ?? configs.value.length < (total.value ?? 0)
      } else {
        loadingMore.value = true
        const nextOffset = configs.value.length
        const page = await getConfigs(limit.value, nextOffset)
        // De-dupe by name in case a refresh shifted the window between calls.
        const seen = new Set(configs.value.map((c) => c.name))
        for (const c of page.configs) if (!seen.has(c.name)) configs.value.push(c)
        total.value = page.total ?? total.value
        hasMore.value = page.hasMore ?? configs.value.length < (total.value ?? 0)
        offset.value = nextOffset
      }
    } catch {
      // Leave existing list intact (FSD §8).
    } finally {
      loadingMore.value = false
    }
  }

  /** GET /api/configs/:name/roadmap → phase progress. */
  async function fetchRoadmap(missionName: string): Promise<void> {
    try {
      roadmap.value = await getRoadmap(missionName)
    } catch {
      roadmap.value = { ...EMPTY_ROADMAP }
    }
  }

  /** GET /api/configs/:name/plans → plan file list + plansDir. */
  async function fetchPlans(missionName: string): Promise<void> {
    try {
      const result = await getPlans(missionName)
      plans.value = result.plans
      plansDir.value = result.plansDir
    } catch {
      plans.value = []
      plansDir.value = null
    }
  }

  /** Convenience: fetch roadmap + plans for a mission in parallel. */
  async function fetchAllConfig(missionName: string): Promise<void> {
    await Promise.all([fetchRoadmap(missionName), fetchPlans(missionName)])
  }

  return {
    configs,
    currentConfig,
    roadmap,
    plans,
    plansDir,
    total,
    hasMore,
    limit,
    offset,
    loadingMore,
    fetchConfigs,
    fetchRoadmap,
    fetchPlans,
    fetchAllConfig,
  }
})
