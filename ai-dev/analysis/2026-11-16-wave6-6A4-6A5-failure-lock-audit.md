---
Status: open
Date: 2026-11-16
Auditor: independent audit subagent (Wave 6, audits 6A-4 + 6A-5)
Scope: nop-tools/nop-ib (worktree nop-entropy-ib, branch feature/nop-ib, HEAD c8bc793ac6)
Ground truth: `./mvnw test -pl nop-tools/nop-ib -q` → exit 0, surefire total=57 failures=0 errors=0 skipped=0 (18 test classes)
Constraint: no source code modified during this audit.
---

# Audit 6A-4: 失败语义审计（failure-path behavior matrix）

Probes: known prior incidents — (1) spring-batch-core rerun-timeout result overwrite; (2) jsr352 firstError evidence pruning.

Files: `JUnitWorkerPool.java`, `JUnitForkExecutor.java`, `cli/TestCommand.java`, `cli/RunLog.java` (all under `nop-tools/nop-ib/src/main/java/io/nop/ib/`).

## 6A-4.1 Behavior matrix

| # | Trigger | Where | Visible output (JSON / exit / warnings) | Oracle impact | Verdict |
|---|---------|-------|------------------------------------------|---------------|---------|
| F1 | Worker spawn fails (hello timeout / boot crash) | JUnitWorkerPool.java:79-97 (pre-spawn), :332-337 (hello timeout) | warning `worker pre-spawn failed: <full stack>` or `NopIbException ... stderr tail` | spawn exception inside dispatch → retry path F4 | safe |
| F2 | Worker dies mid-class (EOF) | JUnitWorkerPool.java:371-374 | IOException with 4KB stderr tail → caught at :153 | retry once on fresh worker; second failure → F4 | safe |
| F3 | Class hangs; watchdog kill | JUnitWorkerPool.java:357-360 (`nop.ib.forkTimeoutSeconds`, default 300s) | stderr `nop-ib worker watchdog: killing hung class`; worker killed → EOF → F2 path | hung class cannot block module; rerun gate (F8) re-executes it | safe |
| F4 | Same class fails twice on worker path | JUnitWorkerPool.java:156-159 | warning `class X failed twice (worker path): t`; `firstError` set; ALL not-yet-started classes silently skipped (:119-120 `if (firstError.get() != null) return;`) | pool abort at :188-192: exit 1, summary `{tests:0, failures:0, poolAborted:true}`, warning `worker pool aborted` | risk-mitigated: jsr352-style firstError pruning still present (:119, :158), BUT the evidence gap is healed by the module-level differential rerun — see F8. Residual: unstarted classes appear neither in `results` nor in `missingClasses` |
| F5 | Fork exits nonzero with no/empty result document | JUnitForkExecutor.java:269-290 (`readResultSummary`) | summary `{tests:0, failures:1, crashed:true, forkError, forkStdoutTail}`; warning `fork produced no result document (exit N)` | anti fake-green guard (defect 17) intact; fork exit preserved | safe |
| F6 | Fork batch watchdog timeout | JUnitForkExecutor.java:233-241 | exit 1, summary `{tests:0, failures:1, timedOut:true}`; warning `fork killed by batch watchdog after Ns` | timedOut flag EXISTS in summary but is NOT propagated by TestCommand (see F8/Finding A-2) | safe direction, evidence-thin |
| F7 | junit setup unsatisfiable | JUnitForkExecutor.java:67-80, 177-180 (exit −3); JUnitWorkerPool cmdPrefix :415, 456-458 | exit −3 → TestCommand.java:505-507 / :538-543 fall back to surefire with reason in `run.kind` | no silent degradation; reason explicit | safe |
| F8 | Worker run failed (exit 1) → module differential rerun, cap `nop.ib.moduleRerunTimeoutSeconds` default 1800s | **TestCommand.java:522-537** (rerun gate), :525-526 (cap applied) | debug stderr lines; on pass: warning `... PASSES in fresh single-fork ... fresh result authoritative`; on fail: `... genuine failures confirmed`; then **`fork = fresh` (:536)** | **OVERWRITE, not merge.** If the fresh fork itself hits the 1800s cap (F6 path), the worker-collected per-class results (tests count, `failed` detail list, `missingClasses`) are replaced by `{tests:0, failures:1, timedOut:true}`. Verdict stays FAILED (fail-safe direction), but: (a) failure detail lost → `recordLastFailed` (TestCommand.java:820-828, parses `FAILED_CLASS_LINE` from `run.output`) finds no `{class=...` lines → `last-failed.json` NOT updated → `--rerun-failed` loses the class list; (b) `timedOut` in fresh.summary is never surfaced — `report()` (TestCommand.java:918-956) reads only tests/failed/errors/skipped/warnings, and the watchdog text lives only in `run.warnings` → present in JSON `warnings` array but no structured `timedOut:true` field | **result-loss risk (pattern probe 1 CONFIRMED as residual)** — Finding A-1, Major |
| F9 | Fork exit 2 (missing classes) → Maven test-compile retry, then re-fork | TestCommand.java:563-580 | `run.summary.clear()` then re-filled from retry fork; `run.exitCode = f2 > 0 ? FAILED : OK` | **retry fork exit code ignored**: if classes are still missing after the retry compile, the re-fork exits 2 again with `failures=0` (IbJUnitRunner.java:149 exits `!missing ? 2 : counts[1]>0 ? 1 : 0`; missing classes carry failures=0) → `f2==0` → **exitCode = EXIT_OK = fake green**; retry fork's `missingClasses`/warnings also dropped (fresh summary not inspected, `fork.warnings()` of retry not propagated) | **result-loss / fake-green risk** — Finding A-3, Major |
| F10 | Pool degrade / pollution detection | JUnitWorkerPool.java:226-230 | warnings `reuse not profitable...`, `pollution guard triggered for: ...` | advisory only; pollution classes get fresh-fork rerun via F8 | safe |
| F11 | `--rerun-failed` state handling | TestCommand.java:802-857 (`recordLastFailed`), :872-892 (`readLastFailed`) | best-effort; all exceptions swallowed (:811, :854) | success clears module entry (:806-814); LOCK_TIMEOUT / empty summary no-op (:816-817) — a lock-timeout run does NOT wipe prior recorded failures | safe (bookkeeping can under-record after F8, see A-1) |
| F12 | Maven fallback summaries | TestCommand.java:288-293, :902-916 (`extractSummaries`, per-class line dedup :905-908) | `run.summary` from regex; exit mapped 0→OK else FAILED, lock-timeout 75 preserved | double-count defect fixed; if mvn output missing summaries → `summary` empty; report shows run=0 failed=0 with `ok:false` (exit preserved) — failure cannot be masked by missing output because exitCode is independent | safe |
| F13 | RunLog persistence failures | RunLog.java:60-65 (disabled log), :99-101, :118-123 (swallowed IO) | nothing visible; `last-run.json` silently absent on write failure | verdict never depends on log; but a swallowed `close()` write failure means NO durable evidence of the whole run — acceptable best-effort, documented | safe (Minor note) |
| F14 | `-pl` parallel branch task exception | TestCommand.java:216-226 | `err.println("module test failed: " + e)` + cause stack; missing `runByModule` entry → counted as failure at :227-231 | exceptions cannot mask failure (absence = failure), but the module's report entry is missing from `last-run.json` (`report` was never called) | safe (evidence-thin, Minor) |

## 6A-4.2 Where results are merged / replaced (grep anchors)

- **Replace (whole-object)**: TestCommand.java:536 `fork = fresh;` — differential rerun replaces the worker aggregate (A-1).
- **Replace after clear**: TestCommand.java:571-576 — missing-class retry: `run.summary.clear()` + re-add, retry exit code ignored (A-3).
- **Merge (accrual)**: JUnitWorkerPool.java:141 `results.put(cls, r)` under `lock` (:136-147) — per-class accrual is correct, no overwrite of a different class.
- **Merge (aggregate)**: executeForks TestCommand.java:660-740 — ACCRUES (sums) rather than replaces, but is currently **dead code** (no caller; only `pool.run` / single `executor.execute` are live) — Minor.
- **Merge (state)**: recordLastFailed TestCommand.java:851-853 `all.put(moduleId, failedClasses)` — per-module replace inside a shared map; read-modify-write is NOT lock-protected, but all live call sites are on serial paths (plan scope :336/:347, --class :257, --rerun-failed :147) except… the `-pl` parallel branch (:202-214) never calls `recordLastFailed`, so today there is no concurrent writer. Cross-process concurrent `ib test` invocations would race `last-failed.json` (Minor, see 6A-5).

## 6A-4.3 Findings

- **A-1 (Major, Deferred)** — Rerun-gate timeout still overwrites collected worker results. TestCommand.java:522-537: `fork = fresh` unconditionally; on a 1800s-cap timeout the fresh result is `{tests:0, failures:1, timedOut:true}` (JUnitForkExecutor.java:237-241). Verdict direction is fail-safe (FAILED preserved), but per-class failure detail and counts are discarded, and `last-failed.json` goes un-updated (TestCommand.java:849-850 early return when no `{class=` lines parsed). Fix shape: merge worker summary with fresh (keep worker `failed` detail, set `timedOut:true`, keep exit=FAILED); or record worker failure classes into last-failed before overwrite. Deferred: fail-safe direction means no false-green; user-visible symptom is degraded diagnostics only. Pattern probe (1) = CONFIRMED residual (diagnostics loss, not verdict loss).
- **A-2 (Minor, Deferred)** — `timedOut` never escapes into structured output. JUnitForkExecutor.java:240 sets `summary.timedOut=true`; TestCommand.report (:918-956) never reads it; only the warning string survives. Consumers of `last-run.json` must string-match warnings. Same fix as A-1.
- **A-3 (Major, Fix recommended)** — Missing-class retry maps persistent exit-2 to EXIT_OK. TestCommand.java:577: `run.exitCode = f2 > 0 ? EXIT_FAILED : EXIT_OK` ignores the retry fork's own exit code (still 2 = missing classes → fake green) and drops the retry's warnings/missingClasses. Zero-fake-green violation. Pattern probe (2) adjacent: evidence (missingClasses) pruned at the last hop. Suggested: propagate `fork.exitCode()==2` as EXIT_FAILED (or re-enter recovery once with a warning).
- **A-4 (Minor, Deferred)** — jsr352-style firstError pruning still exists (JUnitWorkerPool.java:119, 158): unstarted classes vanish from `results` without landing in `missingClasses`. Mitigated because pool abort exit=1 triggers the F8 full-module rerun which re-executes everything in one fork; residual risk only if the rerun is itself skipped (`classes==null` cannot happen on the abort path — dispatch requires non-null classes) or degraded per A-1. Verdict: safe-with-dependency on F8.
- **A-5 (Minor, Deferred)** — Dead code `executeForks` (TestCommand.java:660) and `remapSummary` (:585) retain an older failure-aggregation semantics (accrual with `timedOutClasses`); misleading for future edits. Delete or wire explicitly.

---

# Audit 6A-5: 锁与并发审计（BuildLock interaction matrix）

## 6A-5.1 Lock topology (verified against source)

- `BuildLock.acquire` — BuildLock.java:38-76: JVM-scoped FileChannel exclusive lock on `<repoRoot>/.mvn/build.lock`; same-JVM contenders → `OverlappingFileLockException` → treated as cross-process holder (:49-53); timeout default 3600s, caller maps null → exit 75.
- **Taken**: CompileCommand.java:134 ONLY (fast-path incremental compile). That is the sole `BuildLock.acquire` call site in nop-ib (grep-verified).
- **Deliberately NOT taken**: Maven fallback paths — TestCommand.java:747 (runMavenModuleTest) and :788 (runSurefire) rely on the mvnq wrapper taking the same lock itself (MavenFallback.java:34-43, `--lock-timeout` forwarded). Documented in comments at both sites.
- **GAP**: the **junit-fork / worker-pool test path never takes BuildLock at all**, in any branch (plan-scope, -pl parallel, --class, --rerun-failed). TestCommand's own class javadoc (TestCommand.java:36-37: "Run tests take the shared build lock") contradicts the code. Non-mvnq external projects (plain `mvn` fallback, MavenFallback.java:44-54) get NO lock even on the Maven path.
- `mvnq` (worktree) is the second holder of the same file — one protocol, per MavenFallback.java:17.

## 6A-5.2 Hidden parallelism check (the "v1 serial" assumption is FALSE)

Parallelism exists in three layers:

1. **Module-level**: TestCommand.java:66-73 static `moduleTestPool` (fixed `MODULE_SLOTS` = max(4, budget/4) threads) + :202 `CompletableFuture.runAsync` — the `-pl a,b,c` branch runs modules CONCURRENTLY (design 06 WR-D6). Not serial.
2. **Class-level (worker pool)**: JUnitWorkerPool.java:106-111 dispatch pool (`workers` threads) + eager spawner (:70-75) + static WATCHDOG (:513-518). Workers are separate JVMs; each module's pool is per-`JUnitWorkerPool` instance.
3. **Dead layer**: `executeForks` (TestCommand.java:660) would add per-class fork parallelism — currently unreferenced.

No `parallelStream()` anywhere in nop-ib main sources (grep-verified). Guard for module concurrency: `workerPermits` Semaphore(WORKER_BUDGET) (TestCommand.java:168, :203, :212) bounds total fork JVMs; `fairShare` (:169) prevents greedy starvation.

## 6A-5.3 Multi-module overlap analysis

Two concurrent modules never write overlapping outputs: each fork's cwd is the module's own basedir (JUnitWorkerPool.java:283, JUnitForkExecutor.java:192), classpath is per-module materialized and asserted jar-free (`assertNoIntraProjectJars`, TestCommand.java:477), fork result files are per-process temp files (JUnitForkExecutor.java:85). Product writes during tests: only `recoverMissingTestClasses` (TestCommand.java:376-408) runs a Maven test-compile and rescans indexes — this CAN run concurrently in two modules' threads (both under `runModuleTests`), and a Maven `-pl :x test-compile` in module x while module y's junit fork READS y's target/classes is fine (distinct dirs) — but a compile of x can run while ANOTHER nop-ib process compiles x (no lock on this path).

## 6A-5.4 Interaction matrix

| Actor pair | Shared resource | Guard | Verdict |
|---|---|---|---|
| `ib compile` (fast path) vs `ib compile` (fast path), any modules | target/classes products, unit index store | BuildLock (CompileCommand.java:134), exclusive | safe |
| `ib compile` fast path vs `ib test` junit-fork (same module) | compile writes target/classes while test fork reads it as classpath | **NONE** — test path takes no lock (grep: only CompileCommand:134) | **Finding B-1 (Major)**: doc claims tests take the lock; they don't. Class-file tear-out during fork classloading possible |
| `ib compile` Maven fallback (CompileCommand.java:107-124) vs anything | repo-wide mvn build | mvnq lock — but only if `ai-dev/tools/mvnq` exists; plain `mvn`/`mvnw` fallback has NO lock (MavenFallback.java:44-54) | Finding B-2 (Minor for Nop worktree — mvnq present; Major for external projects) |
| `ib test` module A (junit fork) vs module B (junit fork), same process, `-pl a,b` | per-module target dirs, temp result files, ports | distinct basedirs; workerPermits semaphore bounds JVM count; bounded teardown wait (JUnitWorkerPool.java:179-186) prevents port-race of dying workers vs fresh forks | safe |
| `ib test` module A vs module B, both needing `recoverMissingTestClasses` | Maven subprocesses (test-compile) + unit index store (`unitStore.save`) | no lock; unitStore.save is per-module keyed (write of distinct keys), but concurrent `Files.writeString`-style state writes across processes are unprotected | Finding B-3 (Minor): same-process distinct keys → no collision observed; cross-process racing possible |
| `runSurefire`/`runMavenModuleTest` from the `-pl` parallel branch vs each other | repo-root Maven invocations, `.mvn/build.lock` | mvnq wrapper serializes IF present; each child holds lock for its full mvn run | safe on Nop worktree; see B-2 |
| Two `ib` processes simultaneously (any commands) | `.nop-ib/last-failed.json` (TestCommand.java:851-869), `.nop-ib/last-run.json` (RunLog.java:119-121) | none (plain read-modify-write, no file lock; last-write-wins) | Finding B-4 (Minor/Deferred): last-run.json is single-writer-by-design per invocation but cross-process last-write-wins loses one run's record; `last-failed.json` read-modify-write can drop a module entry |
| RunLog.module/line/close from `-pl` parallel tasks vs main thread | in-memory lists + log file appends | `synchronized` on all RunLog methods (RunLog.java:70, 79, 91, 106); class doc declares thread-safety (RunLog.java:28) | safe |
| `System.out/err` PrintWriters from parallel module tasks | console | PrintWriter methods synchronized (JDK) | safe (interleaved lines only) |
| JUnitWorkerPool dispatch threads vs `results` map | LinkedHashMap `results` | `synchronized(lock)` around all mutation (JUnitWorkerPool.java:136-147) | safe |
| Worker `created` registry vs teardown | LinkedHashSet | `synchronizedSet` (:38-39) + instance-init `synchronized(JUnitWorkerPool.this)` (:255-257) | safe |
| WATCHDOG static executor across modules/pools | single thread | single-thread scheduler, daemon (:513-518); kill() idempotent | safe |
| `JUnitForkExecutor` instance shared across concurrent retries | `timeout` mutable field | documented + per-call overload (:58-64); live code passes per-call Duration (:525-526) | safe |

## 6A-5.5 Findings

- **B-1 (Major, Deferred)** — Test path (junit-fork/worker pool) takes no BuildLock, contradicting TestCommand.java:36-37 javadoc and BuildLock.java:18-19 ("Held for product writes and test runs"). Within a single process, module sweeps are internally serialized per module, so the practical exposure is: process A running `ib compile` (holds lock, writes products) while process B runs `ib test` on the same module (no lock, forks read products). Fix shape: acquire BuildLock around `runModuleTests`' junit section (releasing before Maven fallback, which self-locks via mvnq) — or fix the javadoc and document the no-lock decision. Deferred: single-user worktree usage makes the cross-process window rare; within-process it cannot occur because compile-then-test in `check` is sequential.
- **B-2 (Major on external projects / Minor for Nop worktree, Deferred)** — MavenFallback plain-`mvn`/`mvnw` fallback (MavenFallback.java:44-54) bypasses the lock protocol entirely: two concurrent delegations (e.g. `-pl` parallel branch hitting `runSurefire` for two modules, or `recoverMissingTestClasses` racing a sibling) run unlocked Maven builds against one repo. On the Nop worktree mvnq exists so the guard holds; on flink-style external targets it does not. Deferred: matches the "external project best-effort" stance; suggest at least a warning when the unlocked path is taken under the parallel branch.
- **B-3 (Minor, Deferred)** — `recoverMissingTestClasses` (TestCommand.java:376-408) performs Maven product writes + `unitStore.save` + `refreshIndexesIfModelChanged` with no lock; two modules recovering concurrently in the `-pl` branch mutate the index store concurrently. Distinct module keys make collisions unlikely today; cross-process remains unguarded.
- **B-4 (Minor, Deferred)** — `.nop-ib/last-failed.json` / `last-run.json` cross-process last-write-wins (TestCommand.java:866-869; RunLog.java:119-121). Documented as state, not protected by BuildLock. Acceptable for single-operator usage; would corrupt under CI parallelism.
- **B-5 (Minor, Deferred)** — Lock-wait UX: BuildLock.acquire sleeps 1s and can wait 3600s with NO progress output (BuildLock.java:64-68); a CI job blocked by a stale holder appears hung. Suggest a periodic wait line after N seconds. Cosmetic.
- **B-6 (safe, verified)** — The `-pl` parallel sweep is NOT a hidden-overlap hazard: modules run concurrently but write only disjoint per-module outputs; the semaphore bounds total JVMs; teardown is bounded (JUnitWorkerPool.java:172-186). Task prompt's "v1 serial = trivially mutually exclusive" assumption does NOT hold (parallel since WR-D6), but mutual exclusion of module outputs still holds per-module.

---

# Summary

| ID | Severity | Disposition | One-liner |
|----|----------|-------------|-----------|
| A-1 | Major | Deferred (fail-safe direction) | rerun-gate 1800s timeout replaces worker results; failure detail + last-failed.json lost (TestCommand.java:536) |
| A-2 | Minor | Deferred (with A-1) | `timedOut` never surfaces in structured report |
| A-3 | Major | Fix recommended | persistent missing-classes (exit 2) after retry mapped to EXIT_OK — fake green (TestCommand.java:577) |
| A-4 | Minor | Deferred | firstError pruning survives but is healed by the F8 rerun |
| A-5 | Minor | Deferred | dead code executeForks/remapSummary |
| B-1 | Major | Deferred | test path takes no BuildLock; javadoc claims it does (TestCommand.java:36 vs grep: only CompileCommand.java:134) |
| B-2 | Major (external) / Minor (worktree) | Deferred | plain mvn/mvnw fallback bypasses lock protocol (MavenFallback.java:44-54) |
| B-3 | Minor | Deferred | recoverMissingTestClasses writes products+index unlocked |
| B-4 | Minor | Deferred | .nop-ib state files last-write-wins across processes |
| B-5 | Minor | Deferred | silent 3600s lock wait |
| B-6 | — | verified safe | parallel module sweep has no overlapping outputs; per-module exclusion holds |

Ground truth: `./mvnw test -pl nop-tools/nop-ib -q` → exit 0; surefire aggregate **57 tests, 0 failures, 0 errors, 0 skipped** across 18 test classes. No source files were modified.
