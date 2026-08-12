# 3 nop-credential Management API + Plaintext Boundary + Web

> Plan Status: active
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

Status: planned
Targets: `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java`

- Item Types: `Fix`

- [ ] `Fix` — Create `NopCredentialBizModel` in `nop-credential-service` (`io.nop.credential.service.entity`): `@BizModel("NopCredential")`, extends `CrudBizModel<NopCredential>`. Constructor sets `setEntityName(NopCredential.class.getName())`. Inject `CredentialCipher`, `ICredentialTypeRegistry`, `IEntityDao<NopCredentialUsage>` (for usage check). Follow `NopFileRecordBizModel.java` pattern.
- [ ] `Fix` — Implement custom `saveCredential` action (`@BizAction @GraphQLReturn`): signature `NopCredential saveCredential(@Name("typeName") String typeName, @Name("name") String name, @Name("fields") Map<String,Object> fields, @Name("id") @Optional String id, IServiceContext context)`. This is the **plaintext input path** — the `fields` map contains plaintext credential field values (e.g. `{"apiKey":"sk-xxx","orgId":"org1"}`). Implementation:
  1. Validate `typeName` exists in `ICredentialTypeRegistry` (fail-closed on unknown type).
  2. Serialize `fields` to JSON via `JsonTool.stringify(fields)`.
  3. Encrypt: `String ciphertext = credentialCipher.encrypt(jsonString)` (uses active key).
  4. Create or load `NopCredential` entity (by `id` if provided, else new). Set `typeName`, `name`, `data=ciphertext`.
  5. Save entity via `dao().saveEntity(entity)`.
  6. Return entity (with `data` set to null before return — see findPage override below).
  This action is `published` (not `published="false"`) in xmeta — it is the designated plaintext input entry point. The standard inherited `save` action is **overridden to throw `UnsupportedOperationException("use saveCredential action instead")`** — this prevents creating credentials with unencrypted/null `data` via the standard save path.
- [ ] `Fix` — Override `get` method: call `super.get(id, ignoreUnknown, context)` (actual signature `CrudBizModel.get(String id, boolean ignoreUnknown, IServiceContext context)` — 3 params) then force `entity.setData(null)` before return. This is defense-in-depth — even if the xmeta `published="false"` boundary were somehow bypassed, data is null at the BizModel level.
- [ ] `Fix` — Override `findPage` method: call `super.findPage(query, ...)` then iterate results and force each `entity.setData(null)`. Return the modified page.
- [ ] `Fix` — Implement `maskList(@Name("ids") List<String> ids)` action: for each id, call `ICredentialProvider.mask(id)` (from W2), return `List<MaskedCredential>`.
- [ ] `Fix` — Implement `typeList()` action: delegate to `ICredentialTypeRegistry.listTypes()`, return `List<CredentialType>`. Each type includes field schemas (field name, label, type, sensitive, required, defaultValue) — this drives the web dynamic form.
- [ ] `Fix` — Implement `test(@Name("id") String credentialId)` action: call `ICredentialProvider.testCredential(id)` (from W2), update entity `testResult` + `lastUsedAt` via DAO, return `TestResult`.
- [ ] `Fix` — Implement `reencryptAll()` action (admin-only — `@BizAction` with auth role check or action-auth.xml restriction): iterate all non-deleted credentials in batches (e.g. 100 per batch). For each: parse `cv1:` keyId from current `data` → if keyId already equals `keyProvider.getActiveKeyId()`, skip (idempotent — **not** a no-op just because same key; AESTextCipher uses random IV so re-encrypting with same key produces different ciphertext, but there's no need to re-encrypt if keyId hasn't changed) → else decrypt with old key, re-encrypt with active key, update entity `data`. Commit each batch. If interrupted, calling again resumes (already-active-key entries are skipped). Throw `NopException` on any decrypt/encrypt error (fail-closed, do not silently skip failed entries).

Exit Criteria:

- [ ] **端到端 (End-to-End, Rule #22)**: a Nop AutoTest that calls GraphQL mutation `NopCredential__saveCredential` with `typeName="openai-api-key"`, `fields={"apiKey":"sk-test123"}` → then queries `NopCredential__findPage` → asserts the response does NOT contain `sk-test123` or any `data` field → then queries `NopCredential__get` → asserts no plaintext → then calls `ICredentialProvider.getCredential(id)` directly → asserts plaintext `apiKey=sk-test123` IS recoverable server-side (proves data was encrypted on save, just not exposed via API). Full path: user input → encrypt → DB → API response (no plaintext) → SPI decrypt (plaintext recovered).
- [ ] **Plaintext boundary structural test**: the GraphQL schema for `NopCredential` does NOT have a `data` field (xmeta `published="false"` proof — verify via GraphQL introspection or schema dump test). The `saveCredential` action's input does NOT include a `data` parameter.
- [ ] `maskList` test: returns masked values (sensitive fields → `****`, non-sensitive → truncated).
- [ ] `typeList` test: returns at least 2 types, each with correct field schemas (field names match type definition, `sensitive` flag correct).
- [ ] `test` action test: updates `testResult` on entity, returns `TestResult` object with explicit success/message.
- [ ] `reencryptAll` test: (1) encrypt credentials with keyA → switch active to keyB → call `reencryptAll()` → all credentials now have keyB in their `cv1:` prefix → `ICredentialProvider.getCredential` still recovers plaintext. (2) Call `reencryptAll()` again with keyB still active → all entries skipped (idempotent, no unnecessary re-encryption).
- [ ] **接线验证 (Wiring Verification, Rule #23)**: `saveCredential` verified to call `CredentialCipher.encrypt` (assertion: DB-stored `data` starts with `cv1:activeKeyId:v1:`); `findPage`/`get` verified to call `setData(null)` (assertion: response data field is null/absent).
- [ ] **无静默跳过 (Rule #24)**: `saveCredential` with null `fields` throws `NopException`; unknown `typeName` throws; `test` on non-existent credential throws; `maskList` on non-existent credential throws.
- [ ] `No owner-doc update required` — BizModel API pattern follows CrudBizModel convention (module doc created in W7).
- [ ] `ai-dev/logs/` entry for the execution day.

### Phase 2 - Delete Reference Counting

Status: planned
Targets: `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java`

- Item Types: `Fix`

- [ ] `Fix` — Override `delete` to add reference-checking: call `super.doDelete(id, refNamesToCheck, prepareDeleteCallback, context)` where `prepareDeleteCallback` is a `BiConsumer<NopCredential, IServiceContext>` that: queries `NopCredentialUsage` count by `credentialId` → if count > 0, throw `NopException` listing the consumer references (do NOT silently delete — fail-closed). Follow the `doDelete(id, refNamesToCheck, prepareDelete, context)` signature from `CrudBizModel.java:1059`.
- [ ] `Fix` — On successful delete (count == 0): the ORM's `useLogicalDelete="true"` handles soft-delete automatically (sets `delFlag=true`). Additionally set `status="disabled"` in the `prepareDelete` callback before the delete proceeds (business-level disable in addition to ORM-level soft-delete flag).

Exit Criteria:

- [ ] Delete interception test: credential with 1+ usage references → `delete` throws `NopException` (credential NOT deleted). Credential with 0 references → `delete` succeeds (soft-deleted: `delFlag=true`, `status=disabled`). After `unregisterUsage` clears all references → `delete` succeeds.
- [ ] Soft-delete verification: deleted credential has `delFlag=true` and `status=disabled` in DB (not physically removed). `ICredentialProvider.getCredential` on deleted credential throws (W2 fail-closed).
- [ ] **无静默跳过 (Rule #24)**: delete with references throws, never silently succeeds.
- [ ] `No owner-doc update required`.
- [ ] `ai-dev/logs/` entry for the execution day.

### Phase 3 - AMIS Web Management Page

Status: planned
Targets: `nop-credential/nop-credential-web/src/main/resources/_vfs/nop/credential/pages/NopCredential/main.page.yaml`, `nop-credential/nop-credential-web/src/main/resources/_vfs/nop/credential/pages/NopCredential/NopCredential.view.xml`, `nop-credential/nop-credential-web/src/main/resources/_vfs/nop/credential/pages/NopCredential/NopCredential.lib.xjs`, `nop-credential/nop-credential-web/precompile/gen-page.xgen`, `nop-credential/nop-credential-web/src/main/resources/_vfs/nop/credential/auth/nop-credential.action-auth.xml`

- Item Types: `Fix`

- [ ] `Fix` — Create `precompile/gen-page.xgen` in `nop-credential-web` (follow `nop-file-web/precompile/gen-page.xgen`): generates base page structure from ORM model + xmeta via `/nop/templates/page` template. This produces `_gen/_NopCredential.view.xml` etc.
- [ ] `Fix` — Create `_module` file in `_vfs/nop/credential/` (module registration, follow nop-file-web pattern).
- [ ] `Fix` — Create `main.page.yaml` for NopCredential management page. Follow `nop-file-web` main.page.yaml pattern (minimal — references the generated view).
- [ ] `Fix` — Create `NopCredential.view.xml` with `x:extends="_gen/_NopCredential.view.xml"` delta. Customize the form section to implement **dynamic credential-type-driven form**. The AMIS schema structure for the dynamic form:
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
- [ ] `Fix` — Create `NopCredential.lib.xjs` with helper functions for dynamic form field generation (maps type field schema → AMIS form item config). Follow `nop-file-web/NopFileRecord.lib.xjs` pattern for the file structure.
- [ ] `Fix` — Create `nop-credential.action-auth.xml` for action authorization: admin-only actions (`saveCredential`, `delete`, `reencryptAll`). Follow `nop-file-web/nop-file.action-auth.xml` pattern.

Exit Criteria:

- [ ] **端到端 (End-to-End, Rule #22)**: the web page test (follow `NopFileWebPagesTest.java` pattern — verifies page definition loads without error) passes. Additionally, the `NopCredential.view.xml` delta is verified to contain: (a) a type selector that calls `typeList()`, (b) a dynamic form section (not static fields), (c) no `data` column in the list view.
- [ ] List view does NOT contain a `data` column (plaintext boundary in UI — verify via grep in the view XML).
- [ ] Type selector is wired to `typeList()` GraphQL action (verify via view XML content).
- [ ] Dynamic form section exists in view XML (not static codegen output — custom delta content).
- [ ] Action auth file defines admin-only actions (`reencryptAll`, `delete`, `saveCredential`).
- [ ] `./mvnw test -pl nop-credential/nop-credential-web -am -T 1C` green (web pages test passes).
- [ ] `No owner-doc update required` — web page follows standard AMIS pattern (module doc created in W7).
- [ ] `ai-dev/logs/` entry for the execution day.

## Closure Gates

- [ ] `NopCredentialBizModel` provides management CRUD with structural plaintext boundary (data never in API response; custom `saveCredential` is the only plaintext input path).
- [ ] `maskList`, `typeList`, `test`, `reencryptAll` actions implemented and tested.
- [ ] Delete reference-counting interception: blocks delete when usage references exist (fail-closed).
- [ ] AMIS web page renders type-driven dynamic form, no plaintext in list view.
- [ ] **Anti-Hollow Check**: full path verified — user creates credential via `saveCredential` → encrypts data → DB stores `cv1:` ciphertext → `findPage`/`get`/`maskList` returns no plaintext → `ICredentialProvider.getCredential` recovers plaintext server-side only.
- [ ] `./mvnw clean install -pl nop-credential -am -T 1C` green.
- [ ] `./mvnw test -pl nop-credential -am -T 1C` green.
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0.
- [ ] checkstyle / code conventions pass.
- [ ] Independent closure audit by fresh sub-agent, evidence recorded in `Closure` section.

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

Status Note: _(filled at closure)_
Completed: _(filled at closure)_

Closure Audit Evidence: _(filled at closure by independent sub-agent)_

Follow-up: _(filled at closure)_
