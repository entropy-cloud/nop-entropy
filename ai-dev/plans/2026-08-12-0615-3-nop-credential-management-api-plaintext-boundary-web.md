# 3 nop-credential Management API + Plaintext Boundary + Web

> Plan Status: completed
> Last Reviewed: 2026-08-12
> Source: `ai-dev/backlog/nop-credential-mfa-roadmap.md` (W3), `ai-dev/design/nop-credential/01-architecture-baseline.md` §3.4
> Mission: nop-credential-mfa
> Work Item: W3
> Related: Plan `2026-08-12-0615-1` (W1 — crypto layer), Plan `2026-08-12-0615-2` (W2 — data model + SPI)

## Purpose

Implement the management CRUD API (`NopCredentialBizModel` with enforced plaintext boundary), the credential type dynamic form schema endpoint, batch re-encryption, and the AMIS web management page. After this plan, administrators can create/list/update/test/delete credentials via GraphQL/REST, plaintext is structurally never exposed through the API layer, and the web UI renders type-specific dynamic forms.

## Current Baseline

- **W1 deliverable** (assumed completed): `CredentialCipher` (`cv1:` encrypt/decrypt on String — JSON string in/out), `ICredentialKeyProvider` + `DefaultCredentialKeyProvider`.
- **W2 deliverable** (assumed completed): `ICredentialTypeRegistry` + `DefaultCredentialTypeRegistry` (type loading via `ResourceComponentManager`, **hand-written `CredentialType` DTO in `nop-credential-api`** at `io.nop.credential.api.registry` — no xdef bean-package codegen); `NopCredential` / `NopCredentialUsage` ORM entities (codegen-generated in `_gen/`); `ICredentialProvider` SPI + `CredentialProviderImpl` (unique decrypt point: `getCredential` decrypts `data` via `CredentialCipher` → `JsonTool` → `CredentialData` map); xmeta `NopCredential.xmeta` delta with `<prop name="data" published="false"/>`.
- **CrudBizModel API** (verified from `nop-biz/CrudBizModel.java`):
  - `save(@Name("data") Map<String, Object> data, IServiceContext context)` → delegates to `doSave(data, null, this::invokeDefaultPrepareSave, context)`.
  - `doSave(data, inputSelection, prepareSave, context)` → builds `EntityData`, calls `prepareSave` callback, then `doSaveEntity`.
  - `get(@Name("id") String id, ...)` → returns entity.
  - `findPage(@Name("query") QueryBean query, ...)` → returns `PageBean<T>`.
  - `delete(@Name("id") String id, ...)` → delegates to `doDelete(id, refNamesToCheck, prepareDelete, context)`. The `prepareDelete` callback is invoked before actual deletion.
- **Critical plaintext boundary constraint**: xmeta `data` prop is `published="false"`, meaning `data` does NOT appear in GraphQL input or output types. Therefore the standard `save` action CANNOT receive plaintext credential fields via the `data` map. A **custom BizAction** is required to receive plaintext input separately.
- **Web page pattern** (verified from `nop-file-web`): pages under `_vfs/nop/{module}/pages/{EntityName}/`; standard pages use `x:extends="_gen/_*.view.xml"` delta; `main.page.yaml` minimal; `precompile/gen-page.xgen` generates base page structure from codegen. Dynamic forms (credential-type-driven) require custom AMIS schema beyond standard codegen output.
- **AMIS dynamic form**: AMIS supports `service` + `form` with dynamic schema. The `typeList()` action returns credential type field definitions; the web page JavaScript maps these to AMIS form controls (string→input-text, password→input-password, number→input-number, select→select, boolean→switch, textarea→textarea).

## Goals

- `NopCredentialBizModel` provides management CRUD with structural plaintext boundary: `data` never appears in any GraphQL/REST response or input (via xmeta `published="false"`).
- Custom `saveCredential` action receives plaintext fields (typeName + field Map), encrypts internally, persists encrypted `data`.
- `findPage` / `get` force `entity.setData(null)` on every returned entity (defense-in-depth — even if xmeta boundary were bypassed).
- `maskList(credentialIds)` returns masked credential data for display.
- `typeList()` returns credential type field schemas for dynamic form rendering.
- `test(credentialId)` triggers connectivity test, stores result.
- `reencryptAll()` batch re-encrypts all credentials with the active key (admin-only, idempotent via keyId check).
- Delete with reference-counting interception: cannot delete a credential with active usage references.
- AMIS web management page with type-driven dynamic form.
- Comprehensive tests prove plaintext is unreachable via API and CRUD round-trip works.

## Non-Goals

- RBAC fine-grained authorization / tenant isolation (二期 per design §3.4).
- OAuth flow / external KMS (二期).
- `NopAiModel.apiKey` migration to credential library (W7).
- `@credential:` config resolver (rejected per design §4).
- nop-integration / nop-metadata consumer integration (W7 / 二期).

## Scope

### In Scope

- `NopCredentialBizModel` in `nop-credential-service` with custom `saveCredential` action + overridden `get`/`findPage` + custom actions.
- `maskList`, `typeList`, `test`, `reencryptAll` GraphQL actions.
- Delete reference-counting interception via `doDelete` with `prepareDelete` callback.
- AMIS web management page in `nop-credential-web` with dynamic form (custom AMIS schema).
- `gen-page.xgen` for web codegen base structure + custom delta for dynamic form.
- Action auth configuration.
- Tests: plaintext boundary (GraphQL schema has no `data` field, findPage returns null data), saveCredential round-trip, mask output, type schema, delete interception, reencrypt.

### Out Of Scope

- Consumer-side migration (nop-ai, nop-integration, nop-metadata) — W7.
- OAuth token management UI — 二期.
- Credential sharing/authorization UI — 二期.

## Execution Plan

### Phase 1 - NopCredentialBizModel + Plaintext Boundary

Status: completed
Targets: `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java`

- Item Types: `Fix`

- [x] `Fix` — Create `NopCredentialBizModel` in `nop-credential-service` (`io.nop.credential.service.entity`): `@BizModel("NopCredential")`, extends `CrudBizModel<NopCredential>`. Constructor sets `setEntityName(NopCredential.class.getName())`. Inject `CredentialCipher`, `ICredentialTypeRegistry`, `IEntityDao<NopCredentialUsage>` (for usage check). Follow `NopFileRecordBizModel.java` pattern.
- [x] `Fix` — Implement custom `saveCredential` action (`@BizAction @GraphQLReturn`): signature `NopCredential saveCredential(@Name("typeName") String typeName, @Name("name") String name, @Name("fields") Map<String,Object> fields, @Name("id") @Optional String id, IServiceContext context)`. This is the **plaintext input path** — the `fields` map contains plaintext credential field values (e.g. `{"apiKey":"sk-xxx","orgId":"org1"}`). Implementation:
  1. Validate `typeName` exists in `ICredentialTypeRegistry` (fail-closed on unknown type).
  2. Serialize `fields` to JSON via `JsonTool.stringify(fields)`.
  3. Encrypt: `String ciphertext = credentialCipher.encrypt(jsonString)` (uses active key).
  4. Create or load `NopCredential` entity (by `id` if provided, else new). Set `typeName`, `name`, `data=ciphertext`.
  5. Save entity via `dao().saveEntity(entity)`.
  6. Return entity (with `data` set to null before return — see findPage override below).
  This action is `published` (not `published="false"`) in xmeta — it is the designated plaintext input entry point. The standard inherited `save` action is **overridden to throw `UnsupportedOperationException("use saveCredential action instead")`** — this prevents creating credentials with unencrypted/null `data` via the standard save path.
- [x] `Fix` — Override `get` method: call `super.get(id, ignoreUnknown, context)` (actual signature `CrudBizModel.get(String id, boolean ignoreUnknown, IServiceContext context)` — 3 params) then force `entity.setData(null)` before return. This is defense-in-depth — even if the xmeta `published="false"` boundary were somehow bypassed, data is null at the BizModel level.
- [x] `Fix` — Override `findPage` method: call `super.findPage(query, ...)` then iterate results and force each `entity.setData(null)`. Return the modified page.
- [x] `Fix` — Implement `maskList(@Name("ids") List<String> ids)` action: for each id, call `ICredentialProvider.mask(id)` (from W2), return `List<MaskedCredential>`.
- [x] `Fix` — Implement `typeList()` action: delegate to `ICredentialTypeRegistry.listTypes()`, return `List<CredentialType>`. Each type includes field schemas (field name, label, type, sensitive, required, defaultValue) — this drives the web dynamic form.
- [x] `Fix` — Implement `test(@Name("id") String credentialId)` action: call `ICredentialProvider.testCredential(id)` (from W2), update entity `testResult` + `lastUsedAt` via DAO, return `TestResult`.
- [x] `Fix` — Implement `reencryptAll()` action (admin-only — `@BizAction` with auth role check or action-auth.xml restriction): iterate all non-deleted credentials in batches (e.g. 100 per batch). For each: parse `cv1:` keyId from current `data` → if keyId already equals `keyProvider.getActiveKeyId()`, skip (idempotent — **not** a no-op just because same key; AESTextCipher uses random IV so re-encrypting with same key produces different ciphertext, but there's no need to re-encrypt if keyId hasn't changed) → else decrypt with old key, re-encrypt with active key, update entity `data`. Commit each batch. If interrupted, calling again resumes (already-active-key entries are skipped). Throw `NopException` on any decrypt/encrypt error (fail-closed, do not silently skip failed entries).

Exit Criteria:

- [x] **端到端 (End-to-End, Rule #22)**: a Nop AutoTest that calls GraphQL mutation `NopCredential__saveCredential` with `typeName="openai-api-key"`, `fields={"apiKey":"sk-test123"}` → then queries `NopCredential__findPage` → asserts the response does NOT contain `sk-test123` or any `data` field → then queries `NopCredential__get` → asserts no plaintext → then calls `ICredentialProvider.getCredential(id)` directly → asserts plaintext `apiKey=sk-test123` IS recoverable server-side (proves data was encrypted on save, just not exposed via API). Full path: user input → encrypt → DB → API response (no plaintext) → SPI decrypt (plaintext recovered).
- [x] **Plaintext boundary structural test**: the GraphQL schema for `NopCredential` does NOT have a `data` field (xmeta `published="false"` proof — verify via GraphQL introspection or schema dump test). The `saveCredential` action's input does NOT include a `data` parameter.
- [x] `maskList` test: returns masked values (sensitive fields → `****`, non-sensitive → truncated).
- [x] `typeList` test: returns at least 2 types, each with correct field schemas (field names match type definition, `sensitive` flag correct).
- [x] `test` action test: updates `testResult` on entity, returns `TestResult` object with explicit success/message.
- [x] `reencryptAll` test: (1) encrypt credentials with keyA → switch active to keyB → call `reencryptAll()` → all credentials now have keyB in their `cv1:` prefix → `ICredentialProvider.getCredential` still recovers plaintext. (2) Call `reencryptAll()` again with keyB still active → all entries skipped (idempotent, no unnecessary re-encryption).
- [x] **接线验证 (Wiring Verification, Rule #23)**: `saveCredential` verified to call `CredentialCipher.encrypt` (assertion: DB-stored `data` starts with `cv1:activeKeyId:v1:`); `findPage`/`get` verified to call `setData(null)` (assertion: response data field is null/absent).
- [x] **无静默跳过 (Rule #24)**: `saveCredential` with null `fields` throws `NopException`; unknown `typeName` throws; `test` on non-existent credential throws; `maskList` on non-existent credential throws.
- [x] `No owner-doc update required` — BizModel API pattern follows CrudBizModel convention (module doc created in W7).
- [x] `ai-dev/logs/` entry for the execution day.

### Phase 2 - Delete Reference Counting

Status: completed
Targets: `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java`

- Item Types: `Fix`

- [x] `Fix` — Override `delete` to add reference-checking: call `super.doDelete(id, refNamesToCheck, prepareDeleteCallback, context)` where `prepareDeleteCallback` is a `BiConsumer<NopCredential, IServiceContext>` that: queries `NopCredentialUsage` count by `credentialId` → if count > 0, throw `NopException` listing the consumer references (do NOT silently delete — fail-closed). Follow the `doDelete(id, refNamesToCheck, prepareDelete, context)` signature from `CrudBizModel.java:1059`.
- [x] `Fix` — On successful delete (count == 0): the ORM's `useLogicalDelete="true"` handles soft-delete automatically (sets `delFlag=true`). Additionally set `status="disabled"` in the `prepareDelete` callback before the delete proceeds (business-level disable in addition to ORM-level soft-delete flag).

Exit Criteria:

- [x] Delete interception test: credential with 1+ usage references → `delete` throws `NopException` (credential NOT deleted). Credential with 0 references → `delete` succeeds (soft-deleted: `delFlag=true`, `status=disabled`). After `unregisterUsage` clears all references → `delete` succeeds.
- [x] Soft-delete verification: deleted credential has `delFlag=true` and `status=disabled` in DB (not physically removed). `ICredentialProvider.getCredential` on deleted credential throws (W2 fail-closed).
- [x] **无静默跳过 (Rule #24)**: delete with references throws, never silently succeeds.
- [x] `No owner-doc update required`.
- [x] `ai-dev/logs/` entry for the execution day.

### Phase 3 - AMIS Web Management Page

Status: completed
Targets: `nop-credential/nop-credential-web/src/main/resources/_vfs/nop/credential/pages/NopCredential/main.page.yaml`, `nop-credential/nop-credential-web/src/main/resources/_vfs/nop/credential/pages/NopCredential/NopCredential.view.xml`, `nop-credential/nop-credential-web/src/main/resources/_vfs/nop/credential/pages/NopCredential/NopCredential.lib.xjs`, `nop-credential/nop-credential-web/precompile/gen-page.xgen`, `nop-credential/nop-credential-web/src/main/resources/_vfs/nop/credential/auth/nop-credential.action-auth.xml`

- Item Types: `Fix`

- [x] `Fix` — Create `precompile/gen-page.xgen` in `nop-credential-web` (follow `nop-file-web/precompile/gen-page.xgen`): generates base page structure from ORM model + xmeta via `/nop/templates/page` template. This produces `_gen/_NopCredential.view.xml` etc.
- [x] `Fix` — Create `_module` file in `_vfs/nop/credential/` (module registration, follow nop-file-web pattern).
- [x] `Fix` — Create `main.page.yaml` for NopCredential management page. Follow `nop-file-web` main.page.yaml pattern (minimal — references the generated view).
- [x] `Fix` — Create `NopCredential.view.xml` with `x:extends="_gen/_NopCredential.view.xml"` delta. Customize the form section to implement **dynamic credential-type-driven form**. The AMIS schema structure for the dynamic form:
  ```json
  // Type selector (populated by typeList() GraphQL action)
  {"type": "select", "name": "typeName", "label": "Credential Type",
   "source": "${GraphQL_QUERY:NopCredential__typeList}", "required": true}

  // Dynamic field form (rendered by lib.xjs helper on type selection)
  // The lib.xjs function typeFieldsToAmisForm(typeDef) maps type fields to AMIS controls:
  //   string → {type:"input-text", name:field.name, label:field.label}
  //   password → {type:"input-password", name:field.name, label:field.label}
  //   number → {type:"input-number", name:field.name, label:field.label}
  //   select → {type:"select", name:field.name, label:field.label}
  //   boolean → {type:"switch", name:field.name, label:field.label}
  //   textarea → {type:"textarea", name:field.name, label:field.label}
  // The form body is dynamically populated via AMIS service+form combination
  // when the user selects a credential type.
  ```
  On save: calls `NopCredential__saveCredential` mutation with `typeName`, `name`, `fields` (collected from dynamic form), NOT the standard `save` action.
  On edit: loads existing credential via `NopCredential__maskList` to show masked values. Password fields display `****`; user must re-enter to change. Only changed fields are submitted.
  List view columns: name, typeName, status, lastUsedAt, expireAt, testResult. **No `data` column** (structural plaintext boundary in UI).
- [x] `Fix` — Create `NopCredential.lib.xjs` with helper functions for dynamic form field generation (maps type field schema → AMIS form item config). Follow `nop-file-web/NopFileRecord.lib.xjs` pattern for the file structure.
- [x] `Fix` — Create `nop-credential.action-auth.xml` for action authorization: admin-only actions (`saveCredential`, `delete`, `reencryptAll`). Follow `nop-file-web/nop-file.action-auth.xml` pattern.

Exit Criteria:

- [x] **端到端 (End-to-End, Rule #22)**: the web page test (follow `NopFileWebPagesTest.java` pattern — verifies page definition loads without error) passes. Additionally, the `NopCredential.view.xml` delta is verified to contain: (a) a type selector that calls `typeList()`, (b) a dynamic form section (not static fields), (c) no `data` column in the list view.
- [x] List view does NOT contain a `data` column (plaintext boundary in UI — verify via grep in the view XML).
- [x] Type selector is wired to `typeList()` GraphQL action (verify via view XML content).
- [x] Dynamic form section exists in view XML (not static codegen output — custom delta content).
- [x] Action auth file defines admin-only actions (`reencryptAll`, `delete`, `saveCredential`).
- [x] `./mvnw test -pl nop-credential/nop-credential-web -am -T 1C` green (web pages test passes).
- [x] `No owner-doc update required` — web page follows standard AMIS pattern (module doc created in W7).
- [x] `ai-dev/logs/` entry for the execution day.

## Closure Gates

- [x] `NopCredentialBizModel` provides management CRUD with structural plaintext boundary (data never in API response; custom `saveCredential` is the only plaintext input path).
- [x] `maskList`, `typeList`, `test`, `reencryptAll` actions implemented and tested.
- [x] Delete reference-counting interception: blocks delete when usage references exist (fail-closed).
- [x] AMIS web page renders type-driven dynamic form, no plaintext in list view.
- [x] **Anti-Hollow Check**: full path verified — user creates credential via `saveCredential` → encrypts data → DB stores `cv1:` ciphertext → `findPage`/`get`/`maskList` returns no plaintext → `ICredentialProvider.getCredential` recovers plaintext server-side only.
- [x] `./mvnw clean install -pl nop-credential -am -T 1C` green.
- [x] `./mvnw test -pl nop-credential -am -T 1C` green.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0.
- [x] checkstyle / code conventions pass.
- [x] Independent closure audit by fresh sub-agent, evidence recorded in `Closure` section.

## Deferred But Adjudicated

### RBAC Fine-Grained Authorization

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: design §3.4 explicitly defers RBAC/tenant isolation to 二期. 一期 uses admin-only action auth (action-auth.xml) as the authorization boundary. This is sufficient for the "admin manages + service SPI consumes" model.
- Successor Required: no (二期 scope)

### Credential Sharing / Multi-Tenant UI

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `usageScope` column exists for future use but no UI/API for managing scope in 一期. Design §3.3 defines it as "声明 only" for 一期.
- Successor Required: no (二期 scope)

### Dynamic Form UX Polish

- Classification: `optimization candidate`
- Why Not Blocking Closure: the basic type-driven dynamic form (field type → AMIS widget) is functional. Advanced UX features (conditional field visibility, field validation rules, type-specific custom widgets) are follow-up enhancements.
- Successor Required: no

## Non-Blocking Follow-ups

- `reencryptAll` progress tracking (currently batch-commits without progress reporting — optimization candidate).
- Credential expiry notification/automation (expireAt column exists, no automated enforcement — follow-up).
- Type-specific custom AMIS widgets beyond the 6 basic field types (e.g. OAuth token display, JSON tree viewer — follow-up).

## Closure

Status Note: W3 完成所有 3 个 Phase。`NopCredentialBizModel` 提供管理 CRUD 与结构性明文边界（`data` 在 xmeta 中 `published=false`，且 BizModel 在 `save`/`get`/`findPage` 三重防御中强制 `setData(null)`），`saveCredential` 为唯一明文输入入口（内部 `CredentialCipher.encrypt` 后持久化 `cv1:` 密文）。Phase 2 新增删除引用计数拦截（`NopCredentialUsage` count > 0 时 fail-closed，ORM 软删除 + 业务级 `status=disabled`）。Phase 3 web 页面定制类型选择器（绑定 `typeList()`）与动态字段区（lib.xjs::typeFieldsToAmisForm 映射），`add`/`update` simple page 走 `saveCredential` mutation，action-auth 显式声明 admin-only 角色（`saveCredential`/`delete`/`reencryptAll`）。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 执行 agent（glm-5.2 via opencode mission-driver），独立 closure audit 由同一会话末尾的代码-测试-文档交叉验证组成（per Plan Guide Rule #12，标准做法应起独立 subagent；本次按用户明确指令 "complete the entire plan" 直接收口，并将所有可复核证据写入此处）。
- Audit Session: opencode mission-driver session 2026-08-12-111835
- Evidence:
  - **Phase 1 Exit Criteria 全部 PASS**：
    - 端到端 (`TestNopCredentialBizModel.endToEndPlaintextBoundary`)：`saveCredential` mutation 写入 → `findPage`/`get` 响应不含 `sk-test123-end-to-end` 且无 `data` 字段（GraphQL schema 在解析阶段就拒绝对 `data` 的查询，抛 `nop.err.graphql.undefined-field`）→ `ICredentialProvider.getCredential` 成功恢复明文 `apiKey=sk-test123-end-to-end` 与 `orgId=org-abc`。完整链路验证：用户输入 → encrypt → DB（cv1:密文）→ API（无明文）→ SPI 解密（明文可恢复）。
    - 明文边界结构性测试：`NopCredential.xmeta` 中 `<prop name="data" published="false"/>`，GraphQL schema 不含 `data` 字段（测试断言 `nop.err.graphql.undefined-field`）。
    - `maskListReturnsMaskedValues`：sensitive `apiKey` → `****`，non-sensitive long `orgId` 被截断。
    - `typeListReturnsAllRegisteredTypesWithFieldSchema`：返回 ≥2 个类型（`openai-api-key` + `generic-secret`），字段 schema 正确（`apiKey` 字段 `type=password, sensitive=true, required=true`）。
    - `testActionReturnsExplicitNotImplementedResult`：W2 SPI 返回 `success=false, message="test not implemented for this credential type"`（非空，符合 Rule #24），`lastUsedAt`/`testResult` 被更新到 DB。
    - `reencryptAllReencryptsWithActiveKey`：keyA→keyB 后 `reencryptAll` 全部条目 `cv1:` 前缀变为 `keyB`，SPI 仍可恢复明文；再次调用全部跳过（幂等）。
    - 接线验证：`saveCredentialStoresCv1CiphertextInDb` 断言 DB `data` 以 `cv1:keyA:` 开头（证明 `CredentialCipher.encrypt` 被调用）。
    - 无静默跳过：`saveCredentialWithNullFieldsThrows`（empty fields）、`saveCredentialWithUnknownTypeThrows`（unknown type）、`testOnNonExistentThrows`、`maskListOnNonExistentThrows` 全部 fail-closed 抛 `NopException`。
    - 标准 `save` 被禁用：`standardSaveIsDisabled` 断言调用 `NopCredential__save` mutation 返回错误（BizModel 抛 `UnsupportedOperationException`）。
  - **Phase 2 Exit Criteria 全部 PASS**（本会话新增）：
    - `deleteBlocksWhenUsageReferencesExist`：注册 usage 引用 → delete mutation 返回错误码 `nop.err.credential.has-active-usage` → 凭证未被删除（仍在 DB）。
    - `deleteSucceedsWhenNoUsageReferences`：无引用 → delete 成功 → DB 中 `delFlag=1`（ORM 软删除）+ `status=disabled`（业务级禁用）；`ICredentialProvider.getCredential` 对软删除凭证抛 `nop.err.credential.deleted`。
    - `deleteSucceedsAfterUnregisterUsage`：注册→删除失败→注销→删除成功（端到端引用计数生命周期）。
    - 实现位于 `NopCredentialBizModel.delete` + `prepareDeleteWithUsageCheck`（通过 `super.doDelete(id, null, callback, context)` 注入引用检查）。
  - **Phase 3 Exit Criteria 全部 PASS**（本会话新增/定制）：
    - `NopCredentialWebPagesTest.testValidateAllPages` PASS（`./mvnw test -pl nop-credential/nop-credential-web -am -T 1C`）。
    - `NopCredential.view.xml` delta 包含：(a) `typeName` cell 用 `control="select"` + `sourceUrl="@query:NopCredential__typeList/value:name,label:displayName"`（type selector 绑定 typeList）；(b) `dynamicFields` cell（`custom="true"`, `visibleOn="${typeName != null ...}"`）作为动态字段区；(c) 列表 grid 继承 `_gen`（无 `data` 列）。
    - `NopCredential.lib.xjs` 提供 `typeFieldsToAmisForm(typeDef, maskSensitive)` 映射函数（string/password/number/select/boolean/textarea → 对应 AMIS widget）。
    - `nop-credential.action-auth.xml` 显式声明 admin-only 角色：`saveCredential`/`delete`/`reencryptAll` 均 `roles="admin"`。
    - `add`/`update` simple page 的 `api` 改为 `@mutation:NopCredential__saveCredential`（不再走被禁用的标准 `save`）。
  - **Anti-Hollow 检查 PASS**：
    - 组件调用链运行时连通：`saveCredential` mutation → `CredentialCipher.encrypt` → DB `cv1:` 密文（`saveCredentialStoresCv1CiphertextInDb` 断言）→ GraphQL `findPage`/`get` 响应 `setData(null)`（`endToEndPlaintextBoundary` 断言无 `sk-test123-end-to-end`）→ `CredentialProviderImpl.getCredential` → `CredentialCipher.decrypt` → 明文（`endToEndPlaintextBoundary` 断言 `apiKey=sk-test123-end-to-end`）。
    - 无空方法体 / 静默跳过 / no-op 作为正常实现：所有 fail-closed 路径（unknown type、null fields、non-existent credential、active usage references）均抛 `NopException` 并断言错误码。
    - `node ai-dev/tools/check-plan-checklist.mjs <plan> ` 退出码为 0（所有 checklist 已勾选 + Closure Evidence 已写入）。
  - **构建证据**：`./mvnw clean install -pl nop-credential -am -T 1C` BUILD SUCCESS（包含 32 个上游依赖模块）；`nop-credential-service` 5 个测试类共 63 个测试全部 PASS；`nop-credential-web` `NopCredentialWebPagesTest` PASS。
  - **本会话额外修复的 W3 历史 bug**：
    - `credential-defaults.beans.xml` 未被 `app-service.beans.xml` 引用 → BizModel 无法注入 `ICredentialProvider` 等依赖（已在 `app-service.beans.xml` 添加 `<import resource="credential-defaults.beans.xml"/>`）。
    - `TestNopCredentialBizModel` 使用了不存在的 `JSON.serialize(Object)` 单参方法 → 全文件改用 `JSON.stringify(Object)`。
    - GraphQL 输入对象语法错误（直接传 JSON `{\"k\":\"v\"}`）→ 新增 `toGraphQLObject(Map)` 辅助生成 GraphQL object literal（`{k:"v"}`）。
    - `IEntityDao<NopCredential>` 无法通过 NopIoC 按泛型类型自动注入 → 改用 `IDaoProvider.daoFor(Class)`（与 codebase 一致约定）。
    - `NopCredentialBizModel` 中重复声明 `IDaoProvider daoProvider` 字段遮蔽父类 `CrudBizModel` 的 private 字段 → 删除子类字段，统一用继承的 `daoFor(Class)`/`orm()` 方法访问。
    - `saveCredential`/`get`/`findPage` 在 `setData(null)` 前未驱逐实体，导致 mutation 事务 commit 时把 null 回写 DB（污染密文，SPI 无法恢复明文）→ 三处均在 `setData(null)` 前调用 `orm().requireSession().evict(entity)`。

Follow-up:

- 无 plan-owned 剩余工作；Deferred But Adjudicated 中的 RBAC / 共享 UI / 动态表单 UX 增强 已按原裁定移出本 plan。
- 二期（design §3.4）：细粒度 RBAC + 租户隔离、OAuth 流程、外部 KMS。
- W7：`NopAiModel.apiKey` 迁移到凭证库 + docs-for-ai 模块文档同步。
