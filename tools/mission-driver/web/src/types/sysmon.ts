// FSD §6 — System monitor snapshot type.
// Monitor Server endpoint: GET /api/runs/:runId/sysmon → { snapshots: SysmonSnapshot[] }

export interface SysmonTopProc {
  pid: number
  ppid?: number
  rss_mb: number
  cpu_pct?: number
  elapsed?: string
  name: string
}

export interface SysmonSnapshot {
  ts: string | null
  freeGB: number | null
  totalRSS_GB?: number | null
  opencodeRSS_MB: number | null
  opencodeCount?: number | null
  nodeRSS_MB?: number | null
  nodeCount?: number | null
  processCount: number | null
  memPressure?: string | null
  label?: string
  topProcs?: SysmonTopProc[]
}
