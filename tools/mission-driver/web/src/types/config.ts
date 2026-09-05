// FSD §6 — Mission / Roadmap / Plan config types.
// Monitor Server endpoints (see src/monitor.js):
//   GET /api/configs?limit=&offset=
//     → { configs: MissionConfigInfo[]; total; offset; limit; hasMore } (paged)
//   GET /api/configs/:name/roadmap → RoadmapData
//   GET /api/configs/:name/plans   → { plans: PlanInfo[]; plansDir: string | null }

export interface MissionConfigInfo {
  name: string
  description?: string
  roadmapPath?: string
  moduleDir?: string
  flowName?: string | null
  lastRunStatus?: string | null
  lastRunId?: string | null
}

export interface MissionConfig {
  name?: string
  description?: string
  roadmapPath?: string | null
  plansDir?: string | null
  planGuide?: string | null
  moduleDir?: string | null
  flowName?: string | null
  auditsDir?: string | null
  contextDir?: string | null
  commands: Record<string, string | null>
  commitFormat?: string | null
}

export interface RoadmapData {
  roadmapPath?: string | null
  phases: RoadmapPhase[]
  overallProgress: number
}

export interface RoadmapPhase {
  name: string
  status: 'done' | 'ready' | 'planned' | 'todo' | 'not-done'
  isMilestone?: boolean
  doneCount?: number
  totalCount?: number
  seq?: number | null
}

export interface PlanInfo {
  fileName: string
  status: string
  sizeBytes: number
  lastModified: number
}
