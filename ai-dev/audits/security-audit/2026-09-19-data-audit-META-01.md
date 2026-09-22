# META-01 — Metadata Lineage / Quality / Reconciliation Security Audit

> Mission: security-audit (roadmap item 8, deliverable META-01)
> Date: 2026-09-19. Auditor: ZCode session (plan 2026-09-18-2344-10, Phase 2).
> Severity labels: CRITICAL/HIGH/MEDIUM/LOW and P0/P1/P2/P3 (P0=CRITICAL … P3=LOW).
> Scope: lineage extractor input boundaries (`SqlSourceTableExtractor`,
> `SqlColumnLineageExtractor`, `NopMetaLineageEdgeBizModel`), quality-checkpoint
> scheduler authorization (`MetaQualityCheckpointScheduler`,
> `NopMetaQualityCheckpointBizModel`, `CheckpointActionDispatcher`), and
> reconciliation data exposure (`NopMetaReconciliationConfigBizModel`,
> `ReconciliationExecutor`, `LocalReconciliationProcessor`).
> Method: full read of the classes above; cross-check against owner doc
> `docs-for-ai/03-modules/nop-metadata.md` security-contract section (F1-F9,
> P2-06/P2-07) — all previously adjudicated controls re-verified live where
> touched by this item's surfaces. DataSource credential/SSRF chain (F2/F5/F7,
> credentialId migration W16) remains roadmap item 3 scope and is NOT re-audited.

## Verified controls

1. **Lineage extraction input boundary = stored view SQL, keyed by id**:
   `NopMetaLineageEdgeBizModel.extractLineageFromSql/extractColumnLineageFromSql/extractMeasureLineage`
   (`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeBizModel.java:126-171`)
   accept only `metaTableId`; the parsed SQL is the persisted
   `NopMetaTable.sourceSql`, never raw client SQL. `SqlSourceTableExtractor.extract`
   (`.../lineage/SqlSourceTableExtractor.java:57-105`) is pure-syntax AST work
   (`EqlASTParser.parseFromText`, no session binding, no execution) with CTE-name
   exclusion and fullName dedup; unparseable SQL fails explicitly. The column
   extractor follows the same parse-only model. **No execution surface, no
   injection surface.**
2. **Cron entry is registered only from checkpoint config**: `MetaQualityCheckpointScheduler`
   (`.../quality/MetaQualityCheckpointScheduler.java`) registers jobs in `init()`
   (ACTIVE + non-empty valid cron, L130-157) and on checkpoint save/delete
   (`NopMetaQualityCheckpointBizModel.save/delete` overrides L284-306 →
   registerCheckpoint/unregisterCheckpoint). Job params are fixed
   {beanName, methodName, checkpointId} (buildJobSpec L349-366) — no
   client-controlled bean/method surface through the job system.
3. **Cron execution path and its guards**: `executeScheduledCheckpoint` (L204-234)
   delegates to the raw `executeCheckpoint(checkpointId, null, null)` — null
   context is by design (system identity); per-checkpoint run lock
   (`NopMetaQualityCheckpointBizModel` L203-206) fail-fasts concurrent
   cron×manual duplicates; concurrent-run rejections are downgraded to WARN;
   all other checkpoint errors are converted to typed error results so the job
   is never permanently FAILED (MA7.5-01).
4. **Webhook dispatch SSRF chain (owner doc 维度13-04/P2-06) verified live**:
   `CheckpointActionDispatcher` (`.../quality/CheckpointActionDispatcher.java`):
   protocol whitelist http/https, internal hosts default-denied with explicit
   `webhook-allowed-hosts` allowlist (resolveAllowedWebhookHosts L128-140;
   default empty = no internal host allowed), method whitelist POST/PUT,
   explicit timeout (default 30s), redirect fail-closed both directions
   (global follow-redirects mirror L251-259 + explicit 3xx rejection L267+),
   and implausible-host-shape rejection (P2-06).
5. **Webhook payload is summary-level, not row data**: `dispatchWebhook` posts
   `JsonTool.stringify(summary)` — checkpoint counters, per-rule status
   (PASS/FAIL/SKIP/ERROR), error identifiers, score results. Reconciliation
   details are NOT part of checkpoint summaries; custom_sql results persist
   `sqlHash` only (F9/P2-07, verified in MetaQualityRuleExecutor).
6. **Reconciliation chain reuses the audited query path**: `NopMetaReconciliationConfigBizModel.executeReconciliation`
   (`.../entity/NopMetaReconciliationConfigBizModel.java:106-145`) validates
   `columnName` against the resolved field set, fetches rows via
   `queryTableData` (DATA-01-classified parameterized path) with an explicit
   fetch limit (`nop.metadata.reconciliation.fetch-limit`, check2 P2-07);
   `ReconciliationExecutor.execute` (`.../reconciliation/ReconciliationExecutor.java:67-121`)
   bounds serialized candidates at 50 (AR-13) and fails explicitly on
   missing-column rows.

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-M1-1 | LOW (P3) | `NopMetaQualityCheckpointBizModel.save` L284-289 + `MetaQualityCheckpointScheduler.doRegister` L288-322; rule authoring `NopMetaQualityRuleBizModel` (plain CrudBizModel) | The scheduler surface is a privilege-amplification seam: any holder of the coarse `NopMetaQualityCheckpoint:mutation` permission can (a) register an arbitrary-cron job on the shared scheduler and (b) schedule execution of quality rules — including `custom_sql` rules — against external datasources under system identity (null context, no per-user recheck at fire time). This is the designed authorization model (checkpoint/rule authors are trusted metadata admins), but nothing at module level distinguishes "can configure checks" from "can schedule server-side SQL", and cron-triggered custom_sql rows execute without any user attribution. | Item 9 deployment guidance: restrict NopMetaQualityCheckpoint/NopMetaQualityRule mutation permissions to metadata admins; optional hardening — record triggering identity (cron vs userId) into QualityResult rows, or require an admin role for `extConfig.schedule`-bearing saves (precedent: `nop.metadata.credential-admin-roles` gate on NopMetaDataSourceBizModel L126). |
| F-M1-2 | LOW (P3) | `ReconciliationExecutor.toRowDetail` L150-172 (`originalValue` + candidates into `details` JSON); result read surface = `NopMetaReconciliationResult` plain CrudBizModel | Reconciliation results persist **source row values** (`originalValue` of the configured column — potentially PII/identifiers) plus matched candidates into `nop_meta_reconciliation_result.details`, readable by any holder of the coarse result-query permission with no row-level authz. Same permission domain as `queryTableData` on the source table (the reader could query the source anyway), hence LOW — but the result table becomes a durable, exportable copy of matched-column data that outlives source changes and is not covered by any source-side redaction contract. | Optional: document `NopMetaReconciliationResult:query` as equivalent-to-source-read in deployment guidance; or add column-value hashing/truncation option for identifier columns (config flag), mirroring the sqlHash precedent. |
| F-M1-3 | LOW (P3, observation) | `MetaQualityCheckpointScheduler.executeScheduledCheckpoint` L215 (`executeCheckpoint(checkpointId, null, null)`) | The cron entry bypasses the GraphQL action-auth layer by design (raw impl + null context). Today the only callers are the registered jobs; however any code path that obtains the `nopMetaQualityCheckpointScheduler` bean (e.g. future internal callers, nop-job beanMethod jobs created by other modules using the same BEAN_NAME convention) reaches checkpoint execution without auth. The BeanMethodJobInvoker convention (beanName/methodName as job params) is a generic nop-job surface — job-creation permissions are owned by the job module, not re-audited here. | Record the assumption "job-system write access = trusted" in item 9 deployment notes; no code change requested. |

## Explicit no-finding statements

- No injection or execution path in lineage extraction: parse-only AST over
  stored SQL, id-keyed entries (control 1).
- No unauthenticated entry into quality/lineage/reconciliation surfaces: the
  only `@Auth(publicAccess=true)` action across all three groups is datav's
  `getSharedDashboard` (DATA-01 scope), verified by group-wide grep.
- No SSRF regression in webhook dispatch: allowlist-default-empty semantics,
  redirect fail-closed, method/protocol whitelists all verified live
  (control 4) — consistent with owner doc 维度13-04/P2-06 adjudications.
- No reconciliation data flows into webhook payloads or checkpoint summaries
  (control 5); candidate serialization bounded (AR-13).
- Cron concurrency/duplication is fail-fast guarded (R4.3 run lock) — no
  repeated external dispatch side channel via concurrent triggers.

## Adjudication (Phase 3 input)

- F-M1-1: `adjudicated design-constraint with deployment guidance` + optional
  identity-attribution hardening (LOW).
- F-M1-2: `remediation-target` (LOW, optional redaction) or adjudicated
  constraint with doc note — recommend adjudication decision at item 9.
- F-M1-3: `adjudicated assumption recorded` (no change).

## Owner mapping

- F-M1-1..F-M1-3 successor owner: item 9 consolidation triage (nop-metadata).
- DataSource connection/credential chain (validateJdbcUrl, credentialId,
  connectionConfig redaction) owned by roadmap item 3 (CRED reports) — not
  re-audited, no double ownership.
- Dynamic-SQL classification for quality-rule execution (custom_sql blocklist)
  is DATA-01 F-D1-2; this report owns only the scheduling/exposure angles.
