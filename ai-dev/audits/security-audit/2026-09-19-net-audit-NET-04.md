# NET-04 — TCC Transaction Timeout Handling Audit

> Mission: security-audit (roadmap item 7, deliverable NET-04)
> Date: 2026-09-19. Severity labels: CRITICAL/HIGH/MEDIUM/LOW and P0/P1/P2/P3.
> Scope: `nop-tcc/` (core engine, record store, transaction state machine, runner,
> default wiring) — timeout enforcement, retry bounds, indefinite-lock analysis.
> Method: full read of TccEngine, TccRunner, TccTransaction, TccRecordStore
> (fetchExpiredRecords/removeCompletedRecords), TccTransactionRegistry,
> tcc-core-defaults.beans.xml; scheduler-wiring grep; test cross-check
> (TestTccEngine, TestTccRecordStore, TestTccRunner).

## Verified controls

1. **Per-record compensation timeout is bounded**
   (`TccEngine.checkExpiredTransactions` L315-343): each expired record's
   compensation future is awaited with `get(expireGap, MILLISECONDS)` — a hung
   confirm/cancel RPC cannot block the scanner loop forever; timeout is logged and
   the record is skipped (L331-334). `InterruptedException` restores the interrupt
   flag and returns; the `canceller` token is checked between records — no
   unbounded/infinite loop on the enforcement path.
2. **Retry bound is enforced in the query itself** (`TccRecordStore
   .fetchExpiredRecords` L289-326): filter `retryTimes < maxRetryCount` (L303) —
   once a transaction exhausts its retry budget it is NEVER picked up again
   ("超过上限的事务会被忽略，不再处理" L302); each claim increments retryTimes and pushes
   expireTime to the next check window (L313-316); claims use optimistic version
   check so two scanners cannot double-compensate (L318-321, `tryUpdateManyWithVersionCheck`);
   the claim-retry loop is capped at 100 iterations (L307).
3. **No in-JVM locks that can hang**: transactions coordinate exclusively through
   DB state-machine transitions (TRYING/CANCELLING/CONFIRMING/terminal) with mutual-
   exclusion guards — cancel is refused once confirm started and vice versa
   (TccTransaction.doCancelAsync L92-100 / doConfirmAsync L114-122) — and the
   thread-local registry (`TccTransactionRegistry`) is always restored on both the
   sync and async paths including sync-throw recovery (TccEngine L191-206, L253-255).
   No `synchronized`/`ReentrantLock` on the transaction path; no lock is held across
   an RPC await.
4. **Timeout semantics are typed, not swallowed**: timeout cancel flows through
   `beginCancelAsync(timeout)`/`finishCancelAsync(timeout,...)`; `TIMEOUT_FAILED`
   aggregates to retryable `CANCEL_FAILED`, NOT to a fake `CANCEL_SUCCESS` terminal
   (TccRunner.aggregateCancelBranchStatus L190-193 — the previous double-give-up bug
   is fixed and documented in-code); null-status dirty rows are excluded with WARN
   rather than NPE (TccRunner L138-144, L180-184; TccEngine L176-181).
5. **Cleanup cannot destroy live transactions** (`cleanCompletedTransactions`
   L351-355): deletes only completed/cancelled records (`removeCompletedRecords(
   retentionTime, true)`) — the prior beginTime-based stateless filter that
   physically deleted in-flight transactions (destroying all compensation info) is
   fixed and documented.
6. **Tests cover the enforcement semantics**: `TestTccEngine` /
   `TestTccRecordStore` exercise expiry fetch, retry-bound filtering and
   optimistic-claim behavior; `TestTccRunner` covers branch confirm/cancel
   aggregation including timeout aggregation.

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-N4-1 | LOW (P3) — deployment-owned enforcement | nop-tcc/nop-tcc-core/src/main/resources/_vfs/nop/tcc/beans/tcc-core-defaults.beans.xml (engine registered, no scheduler); grep: zero in-repo callers of `checkExpiredTransactions`/`cleanCompletedTransactions` (only TccEngine definition + tests) | **No default scheduler drives timeout enforcement.** The bounded enforcement machinery (controls 1-2) exists but nothing in the repo schedules `checkExpiredTransactions` — a deployment that assembles nopTccEngine without wiring a periodic driver gets NO automatic expiry compensation: TRYING records linger until driven. nop-tcc is itself an integrator-assembled module (no app-level wiring ships a job), so this is a coverage/deployment gap rather than a code defect; the roadmap's "timeout enforcement" question resolves to "enforced only when the deployment drives it". | Item-9 deployment checklist: schedule `checkExpiredTransactions` (nop-job or equivalent) with explicit expireGap/maxRetryCount; optional shipped default @cfg-driven scheduler bean. |
| F-N4-2 | LOW (P3) | TccRecordStore.java:302-303 (over-budget records filtered and ignored) + TccEngine.java:351-355 (cleanup removes only completed) | **Retry-exhausted transactions stay in non-terminal status forever with no alert hook.** Records past `maxRetryCount` are silently ignored (by design, comment at L302) and never deleted by cleanup (cleanup only removes completed) — participant-side reserved resources hang indefinitely with only DB inspection revealing them. No terminal KILLED marking, no alarm callback. Bounded by design (no infinite retry — good), but the terminal state of "gave up" is invisible. | On crossing the retry bound, mark the record with a distinct terminal status (e.g. KILLED/RETRY_EXHAUSTED) or emit an alert/log-WARN per abandoned txn; enables ops dashboards. |
| F-N4-3 | LOW (P3) | TccRunner.java:82,108 (`serviceInvoker.invokeAsync(...)` — no engine-level per-call timeout) | **Branch confirm/cancel RPCs carry no engine-level timeout** — bounded only indirectly (scanner-side `get(expireGap)` cap, control 1; plus whatever timeout the underlying RPC stack applies). Within one `endAsync` call on a business thread, a hanging cancel RPC holds that call's completion until the caller's own future timeout. Defense-in-depth gap only; the scanner cap prevents systemic indefinite blocking. | Optional: apply an explicit `orTimeout(expireGap)` on branch invocation futures inside TccRunner. |

## Explicit no-finding statements

- No indefinite lock exists anywhere on the TCC path: no JVM-level transaction locks;
  DB coordination uses bounded optimistic claims with a capped retry loop; scanner
  waits are capped per record and interruptible; registry (ThreadLocal-equivalent)
  restoration is exception-safe.
- Retry amplification is bounded by construction (scanner claim increments
  retryTimes before compensation; optimistic version check prevents concurrent
  double-processing).
- No injection surface found: record/branch identifiers flow through ORM queries
  (parameterized); service/method names come from recorded branch metadata
  (server-side written), not from untrusted request text; no secret material in
  module sources (sweep §6 of inventory).
- Confirm-after-cancel / cancel-after-confirm state overwrite is guarded
  (control 3); dirty null-status rows cannot NPE the aggregation paths.

## Adjudication (Phase 3 input)

- F-N4-1: `watch-only residual` (deployment checklist entry for item 9; optional
  follow-up to ship a default scheduler).
- F-N4-2: `remediation-target (small)` — terminal marking/alert for retry-exhausted
  transactions; successor item 9 (may be adjudicated no-fix with reason).
- F-N4-3: `watch-only residual` (defense-in-depth note).

## Owner mapping

- F-N4-1..F-N4-3 → item 9. No overlap: TCC record CRUD API surface is standard
  generated CRUD (authn/authz at the GraphQL layer — item 4 scope, API audits);
  `TccGatewayInterceptor` (nop-tcc-integration) is gateway glue, no security logic
  found beyond txn-context propagation.
