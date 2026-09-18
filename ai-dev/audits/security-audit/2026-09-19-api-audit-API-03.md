# API-03 — Field-Level Visibility Enforcement Audit

> Mission: security-audit (roadmap item 4, deliverable API-03)
> Date: 2026-09-19. Auditor: ZCode session (plan 2026-09-18-2340-6, Phase 2).
> Severity labels: CRITICAL/HIGH/MEDIUM/LOW + P0-P3.
> Scope: xmeta `published` / `internal` / `auth` enforcement in the GraphQL schema
> build and selection validation (document + RPC paths); `not-pub` tagSet clearing;
> biz-layer field auth interplay; sensitive-field default-exposure posture (owner-doc
> warning 敏感字段默认全开).
> Method: full read of ObjMetaToGraphQLDefinition / BizObjectBuilder /
  RpcSelectionSetBuilder / GraphQLSelectionResolver / DefaultBizAuthChecker; live
  xmeta spot checks (nop-dyn-meta); cross-check AUTH-03 (not-pub chain auth-side).

## Verified controls (anchor-verified)

1. **published=false excluded at schema build**: `ObjMetaToGraphQLDefinition.
   toObjectDefinition` skips unpublished props entirely
   (nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/
   core/schema/meta/ObjMetaToGraphQLDefinition.java:60-63) — they never enter the
   `GraphQLObjectDefinition`, so both document and RPC selection validation reject
   them as undefined fields (`ERR_GRAPHQL_UNDEFINED_FIELD`: GraphQLSelectionResolver.
   java:370-383; RpcSelectionSetBuilder.java:126-129). There is no "hidden but
   fetchable" path.
2. **not-pub tagSet clears the whole type**: `BizObjectBuilder.buildBizObject`
   (nop-service-framework/nop-biz/src/main/java/io/nop/biz/impl/BizObjectBuilder.java:
   157-160) empties the field list; meta-governed cleanup `removeFieldsNotInMeta()`
   L154-155; empty-definition objects get `setObjectDefinition(null)` L186-188 —
   no GraphQL selection syntax available for them (consistent with the MFA-table
   lockdown verified in AUTH-03, auth-side).
3. **Prop read-auth propagates to the executable schema**: `field.setAuth(propMeta.
   getReadAuth())` (ObjMetaToGraphQLDefinition.java:91); enforcement is the API-02
   field branch — deny throws `ERR_AUTH_NO_PERMISSION_FOR_FIELD` or prunes when
   `skipWhenNoAuth` (GraphQLActionAuthChecker.java:107-115). Pruned fields never
   reach a fetcher (recursion only on allow).
4. **Biz-layer programmatic checks use the same definition**: `DefaultBizAuthChecker.
   checkAuth` (nop-service-framework/nop-biz/src/main/java/io/nop/biz/impl/
   DefaultBizAuthChecker.java:42-66) resolves the field on the (published-only)
   object definition, unknown field → `ERR_BIZ_UNKNOWN_PROP`, auth!=null → same
   `GraphQLActionAuthChecker.checkAuth`; null objDef (not-pub) → allow with the
   documented alignment rationale (comment L51-52). Regression-tested
   (TestDefaultBizAuthChecker, 4 cases).
5. **RPC/default selection paths cannot resurrect hidden fields**: default selections
   build from the same published-only `objDef` (RpcSelectionSetBuilder.
   addDefaultFieldsForObjType L145-164 iterates objType.getFields()); connection
   fetchers apply `graphql:maxFetchSize` / max-page-size caps (API-01 control 5).
6. **Sensitive-field default-open posture (敏感字段默认全开)**: `_published = true`
   is the xmeta prop default (nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xmeta/
   impl/_gen/_ObjPropMetaImpl.java:187). Per the plan's Deferred-But-Adjudicated
   entry this is a documented declaration-layer posture (`watch-only residual`,
   item 9 triage), not a finding class here. Live instance recorded for item 9:
   `NopDynSql.source` (SQL text, propId 13) is published/queryable by default
   (nop-dyn/nop-dyn-meta/src/main/resources/_vfs/nop/dyn/model/NopDynSql/
   _NopDynSql.xmeta:73) — with action auth off (default) any authenticated caller
   can read dynamic SQL sources via `NopDynSql__findPage`.

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-API3-1 | LOW (P3) | ObjMetaToGraphQLDefinition.java:227-243 (`buildObjType`) | Inline sub-schemas (props declared with nested `<schema>` obj types) are converted **without** the `published` gate that top-level props get (L60-63): a `published="false"` prop inside an inline obj schema still becomes a field of the nested GraphQL type. Exploitability is low (inline schemas are model-author-declared, and the containing prop must itself be published to be reachable), but the visibility contract is inconsistent between levels. Fix handoff: apply the same `isPublished()` skip in `buildObjType`'s loop (L233-236). | Fix-phase candidate (one-line guard + regression test with a nested unpublished prop); low risk. |
| F-API3-2 | LOW (P3) | nop-dyn-meta .../NopDynSql/_NopDynSql.xmeta:73 | Instance pointer for the adjudicated default-open posture: dynamic SQL source text is published/queryable by default (control 6). Filed so item 9 triage has a concrete in-repo instance tied to the existing `watch-only residual` adjudication. | Item 9: either mark `source` `published="false"` (+ dedicated admin view field) or accept posture; belongs with the deployment hardening checklist (enable action auth). |

## Explicit no-finding statements

- No path returns unpublished/not-pub field data through the GraphQL document or RPC
  selection machinery (schema exclusion + undefined-field rejection, controls 1-2, 5).
- No field-level auth decision is made on data not present in the executable schema —
  biz-layer and engine-layer checks share one definition source (controls 3-4).
- `internal` props follow the same `published=false` exclusion chain (published is
  the single gate; AUTH-03 verified the auth-side `not-pub` → `published=false`
  tagSet chain for MFA tables — link-only, no re-verification).
- No overlap: ORM-column-level masking and biz-loader data masking are item 1 /
  biz-module concerns; this report owns only the GraphQL schema/selection layer.

## Adjudication (Phase 3 input)

- F-API3-1: `remediation-target` (LOW/P3) — schema-build consistency guard.
- F-API3-2: instance pointer under the existing `watch-only residual` adjudication
  (plan Deferred-But-Adjudicated; Successor: item 9).
- Default-open posture itself: NOT re-adjudicated here (plan-328 / plan-4
  Deferred-But-Adjudicated already classify it).

## Owner mapping

- F-API3-1 successor owner: fix-phase triage (nop-graphql-core schema build).
- F-API3-2 successor owner: item 9 consolidation (with the deployment checklist
  entry "enable action auth in production").
- Coverage note: `published` exclusion and `not-pub` clearing have no dedicated
  graphql-core unit test (biz-side TestDefaultBizAuthChecker covers the checker
  layer only) — add to the fix-phase test batch alongside the API-02 engine-side
  action-auth gap.
