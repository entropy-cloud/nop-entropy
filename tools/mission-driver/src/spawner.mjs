/**
 * spawner.mjs — shared spawn seam for the mission-driver tool (mdo-2).
 *
 * Both monitor.js (POST /api/runs → handleStartRun) and draft-job.mjs
 * (startDraftJob) spawn detached `node main.js` children. They MUST share ONE
 * testability seam so a single `__setSpawnerForTest` call (the one exported by
 * monitor.js, which the existing monitor.test.js already imports) stubs BOTH
 * spawn sites — no real `node main.js` is launched in CI (FSD §8 R2).
 *
 * monitor.js and draft-job.mjs re-export {@link __setSpawnerForTest} and read
 * the current spawner via {@link getSpawner} at call time (not import time), so
 * a test-installed stub takes effect for both modules.
 */

import { spawn as _realSpawn } from "node:child_process";

let spawnEngineProcess = _realSpawn;

/** @returns {(cmd: string, args: string[], opts: object) => import("node:child_process").ChildProcess} */
export function getSpawner() {
  return spawnEngineProcess;
}

/** @internal Test-only: replace the shared engine spawner. Returns the previous value. */
export function __setSpawnerForTest(fn) {
  const prev = spawnEngineProcess;
  spawnEngineProcess = fn || _realSpawn;
  return prev;
}
