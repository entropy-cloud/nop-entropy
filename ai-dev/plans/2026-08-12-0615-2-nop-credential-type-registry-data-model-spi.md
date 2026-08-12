# 2 nop-credential Type Registry + Data Model + Consumer SPI

> Plan Status: active
> Last Reviewed: 2026-08-12
> Source: `ai-dev/backlog/nop-credential-mfa-roadmap.md` (W2), `ai-dev/design/nop-credential/01-architecture-baseline.md` §3.1, §3.3–§3.4
> Mission: nop-credential-mfa
> Work Item: W2
> Related: Plan `2026-08-12-0615-1` (W1 — provides module skeleton + `CredentialCipher`)

## Purpose

Implement credential type registration (declarative `credential-type.xdef` + register-model + instance files), ORM data model (`NopCredential` + `NopCredentialUsage` via model-first codegen), and the consumer SPI (`ICredentialProvider` — the unique decrypt point). After this plan, credential types are loadable via `ResourceComponentManager`, the ORM entities exist with DDL migration, and downstream consumers (nop-ai, nop-integration) can depend on `nop-credential-api` to get the SPI contract.

## Current Baseline

- **W1 deliverable** (assumed completed): `nop-credential` module skeleton compiles; `CredentialCipher` implements `cv1:{keyId}:v1:{base64data}` encrypt/decrypt; `ICredentialKeyProvider` + `DefaultCredentialKeyProvider` manage master keys via Nop config property `nop.credential.master-keys`. Data serialization convention: CredentialCipher operates on `String` (JSON string); calling layer serializes/deserializes using platform `JsonTool`.
- **Register-model pattern**: `*.register-model.xml` placed in `_vfs/nop/core/registry/`, references `/nop/schema/register-model.xdef`, declares `xdsl-loader` with `fileType` + `schemaPath`. Instance files loaded via `ResourceComponentManager`. Reference: `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/core/registry/ai-tool.register-model.xml` (`name="ai-tool"`, `fileType="tool.xml"`, `schemaPath="/nop/schema/ai/tool/tool.xdef"`).
- **XDef schemas** live in `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/`. Reference: `/nop/schema/ai/tool/tool.xdef`. XDef can declare `xdef:bean-package` to auto-generate Java model classes. **Note**: nop-xdefs is a kernel module — adding a schema here is a **Protected Area** (plan-first), but is the standard pattern for register-model schemas (all register-model schemas live here so they're globally accessible via `_vfs`).
- **ORM codegen flow** (verified from `nop-file`):
  - Step 1: `nop-file-codegen/postcompile/gen-orm.xgen` runs `codeGenerator.withTargetDir("../").renderModel('../../model/nop-file.orm.xml','/nop/templates/orm', '/',$scope)` → generates `app.orm.xml` + dialect DDL in dao's `_vfs`. Then `codeGenerator.withTargetDir("../nop-file-dao/src/main/java").renderModel('../../nop-file-dao/src/main/resources/_vfs/nop/file/orm/app.orm.xml', '/nop/templates/orm-entity','/',$scope)` → generates Java entity classes in `_gen/`.
  - Step 2: `nop-file-meta/precompile/gen-meta.xgen` runs `codeGenerator.renderModel('/nop/file/orm/app.orm.xml','/nop/templates/meta', '/',$scope)` → generates xmeta files `_NopFileRecord.xmeta` in meta's `_vfs`.
  - Meta module pom requires `nop-file-dao` + `nop-file-codegen` as test-scope deps + exec-maven-plugin `<classpathScope>test</classpathScope>`.
- **xmeta delta pattern** (verified): `NopFileRecord.xmeta` extends `_NopFileRecord.xmeta` (generated) via `x:extends="_NopFileRecord.xmeta"` with a `<props/>` override section. To set `published="false"` on a prop, add it in the delta `<props>` section — this survives codegen regeneration.
- **ORM model source** (`nop-file/model/nop-file.orm.xml`): entity with `columns` (each has `code`, `name`, `precision`, `stdDataType`, `stdSqlType`, `propId`, `tagSet`, `mandatory`), `domains` block, `displayName`, `tableName`, `useLogicalDelete`, `registerShortName`, `tagSet`.
- **Nop AutoTest**: platform test framework with `@JunitAutoTest` annotation, H2 in-memory DB, auto-initializes DDL from ORM model. Reference tests in `nop-file/nop-file-service/src/test/`.
- **No `credential-type.xdef`, `NopCredential`, `NopCredentialUsage` exist** — confirmed.

## Goals

- `credential-type.xdef` schema in `nop-xdefs` defines credential type structure (name, version, fields[], authType, testUrl/testAuth) for validation and editor support. **CredentialType is a hand-written DTO** in `nop-credential-api` (no xdef bean-package codegen — keeps api module lightweight without xgen/codegen dependencies).
- `credential-type.register-model.xml` + at least 2 example type instance files; types loadable via `ResourceComponentManager`.
- `ICredentialTypeRegistry` interface + implementation: `getType(typeName)` (fail-closed on unknown), `listTypes()`.
- ORM model `model/nop-credential.orm.xml` → codegen → `NopCredential` (with `data` encrypted column) + `NopCredentialUsage` entities. xmeta with `data` prop `published="false"` via delta override.
- `ICredentialProvider` SPI in api + `CredentialProviderImpl` in service (unique decrypt point): getCredential/getCredentialData/testCredential/mask/registerUsage/unregisterUsage.
- Soft-delete semantics (fail-closed) + reference counting via `NopCredentialUsage`.

## Non-Goals

- BizModel management CRUD / GraphQL endpoints / `maskList` / `typeList` / `reencryptAll` (W3).
- Web management pages / AMIS dynamic forms (W3).
- `NopAiModel.apiKey` migration (W7).
- RBAC / tenant isolation / OAuth flow (二期).
- Actual HTTP connectivity test execution (SPI stores `testResult`; actual test HTTP calls are consumer-side).

## Scope

### In Scope

- `credential-type.xdef` schema definition in `nop-kernel/nop-xdefs` (Protected Area — standard pattern for register-model schemas).
- `credential-type.register-model.xml` + example instance files in `nop-credential-service`.
- `ICredentialTypeRegistry` interface (api) + `DefaultCredentialTypeRegistry` implementation (service).
- `model/nop-credential.orm.xml` ORM source model with `NopCredential` + `NopCredentialUsage` entities.
- Codegen configuration: `gen-orm.xgen` (codegen module) + `gen-meta.xgen` (meta module).
- `ICredentialProvider` SPI (api) + `CredentialProviderImpl` (service).
- xmeta delta: `NopCredential.xmeta` with `data` prop `published="false"`.
- Unit/AutoTest tests: type loading, ORM round-trip, SPI decrypt/encrypt round-trip, usage reference counting, soft-delete fail-closed.

### Out Of Scope

- BizModel / GraphQL management API (W3).
- Web pages (W3).
- Actual migration of existing plaintext keys (W7).
- `@credential:` config resolver (rejected per design §4, 二期 optional).

## Execution Plan

### Phase 1 - Credential Type Registry

Status: planned
Targets: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/credential/credential-type.xdef`, `nop-credential/nop-credential-service/src/main/resources/_vfs/nop/core/registry/credential-type.register-model.xml`, `nop-credential/nop-credential-service/src/main/resources/_vfs/nop/credential/types/*.credential-type.xml`

- Item Types: `Fix | Decision`

- [ ] `Decision` — Create `credential-type.xdef` in `nop-kernel/nop-xdefs` (`_vfs/nop/schema/credential/credential-type.xdef`). This modifies a kernel module (Protected Area: plan-first) — justified because register-model schemas must be globally accessible via `_vfs` for `ResourceComponentManager` to resolve `schemaPath`. Follow `tool.xdef` pattern. **Do NOT declare `xdef:bean-package`** — the `CredentialType` model is hand-written (see next item) to keep `nop-credential-api` lightweight without codegen dependencies. The xdef serves validation and editor support only. Schema defines: root element `credential-type` with attributes `name` (string, required), `version` (string), `displayName` (string); child elements `fields/field` (attributes: name, label, type, sensitive, defaultValue, required — `type` enum: `string|password|number|select|boolean|textarea`), `authType` (enum: none/apiKey/basic/oauth2), `testUrl` (string, optional), `testAuth` (string, optional).
- [ ] `Fix` — Create `credential-type.register-model.xml` in `nop-credential-service` (`_vfs/nop/core/registry/`): `<model x:schema="/nop/schema/register-model.xdef" name="credential-type"><loaders><xdsl-loader fileType="credential-type.xml" schemaPath="/nop/schema/credential/credential-type.xdef"/></loaders></model>`. Follow `ai-tool.register-model.xml` exactly.
- [ ] `Fix` — Create at least 2 example instance files under `_vfs/nop/credential/types/`: `openai-api-key.credential-type.xml` (fields: `apiKey`[type=password, sensitive=true, required=true], `orgId`[type=string, required=false]) and `generic-secret.credential-type.xml` (fields: `secret`[type=password, sensitive=true, required=true]).
- [ ] `Fix` — Create `CredentialType` hand-written DTO class in `nop-credential-api` (`io.nop.credential.api.registry`): fields `name` (String), `version` (String), `displayName` (String), `authType` (String), `testUrl` (String), `testAuth` (String), `fields` (List of `CredentialField` — inner class with `name`, `label`, `type`, `sensitive` (boolean), `defaultValue`, `required` (boolean)). Plain POJO with getters/setters. No codegen dependency — keeps api module lightweight.
- [ ] `Fix` — Create `ICredentialTypeRegistry` interface in `nop-credential-api` (`io.nop.credential.api.registry`): `CredentialType getType(String typeName)` (throws `NopException` on unknown type — fail-closed), `List<CredentialType> listTypes()`.
- [ ] `Fix` — Create `DefaultCredentialTypeRegistry` in `nop-credential-service` (`io.nop.credential.service.registry`): at initialization (`@PostConstruct`), enumerate all `*.credential-type.xml` files under `_vfs/nop/credential/types/` via VFS directory traversal (`Vfs.instance().getChildren("/nop/credential/types/")` or similar), load each via `ResourceComponentManager.instance().loadModel(modelPath)` (modelPath = VFS path like `/nop/credential/types/openai-api-key.credential-type.xml`) — this returns a parsed XDsl model. Map the XDsl model's attributes/children to the hand-written `CredentialType` DTO (populate `name`, `version`, `fields` list from the XML structure). Cache by type `name` attribute in a `Map<String, CredentialType>`. Registered as bean in `credential-defaults.beans.xml`.

Exit Criteria:

- [ ] `credential-type.xdef` validates against the platform xdef meta-schema (no xdef loading errors at build/startup). The hand-written `CredentialType` DTO class exists in `nop-credential-api` (`io.nop.credential.api.registry`).
- [ ] `DefaultCredentialTypeRegistry.getType("openai-api-key")` returns a `CredentialType` with the expected fields (focused test — asserts field names `apiKey`, `orgId` and `sensitive=true` on apiKey).
- [ ] `DefaultCredentialTypeRegistry.listTypes()` returns at least 2 types (focused test).
- [ ] `getType` for a non-existent type throws `NopException` (fail-closed, never returns null).
- [ ] **无静默跳过 (Rule #24)**: no empty method bodies, no silent null returns on error paths.
- [ ] `No owner-doc update required` — credential type registry is internal infrastructure (module doc created in W7).
- [ ] `ai-dev/logs/` entry for the execution day.

### Phase 2 - ORM Data Model + Codegen

Status: planned
Targets: `nop-credential/model/nop-credential.orm.xml`, `nop-credential/nop-credential-codegen/postcompile/gen-orm.xgen`, `nop-credential/nop-credential-meta/precompile/gen-meta.xgen`, `nop-credential/nop-credential-dao/` (generated entities), `nop-credential/nop-credential-meta/` (generated xmeta)

- Item Types: `Fix | Decision`

- [ ] `Decision` — Create `model/nop-credential.orm.xml` source model following `nop-file/model/nop-file.orm.xml` pattern. Define standard domains (version, createTime, createdBy, updateTime, updatedBy, remark, delFlag as boolFlag). Define two entities:
  - `NopCredential` (tableName=`nop_credential`, className=`io.nop.credential.dao.entity.NopCredential`, registerShortName=true, useLogicalDelete=true, tagSet="audit"):
    - `CREDENTIAL_ID` (name=`credentialId`, propId=1, PK, stdDataType=string, stdSqlType=VARCHAR, precision=50, tagSet="var", mandatory=true)
    - `CREDENTIAL_NAME` (name=`name`, propId=2, stdDataType=string, VARCHAR, precision=100)
    - `TYPE_NAME` (name=`typeName`, propId=3, stdDataType=string, VARCHAR, precision=100)
    - `DATA` (name=`data`, propId=4, stdDataType=string, VARCHAR, precision=4000 — stores `cv1:` ciphertext JSON; large enough for encrypted credential field set)
    - `STATUS` (name=`status`, propId=5, stdDataType=string, VARCHAR, precision=20, defaultValue="enabled")
    - `DEL_FLAG` (name=`delFlag`, propId=6, domain=delFlag, stdDomain=boolFlag, stdSqlType=TINYINT, mandatory=true, ui:show="X")
    - `USAGE_SCOPE` (name=`usageScope`, propId=7, stdDataType=string, VARCHAR, precision=50)
    - `LAST_USED_AT` (name=`lastUsedAt`, propId=8, stdDataType=timestamp, TIMESTAMP)
    - `EXPIRE_AT` (name=`expireAt`, propId=9, stdDataType=timestamp, TIMESTAMP)
    - `TEST_RESULT` (name=`testResult`, propId=10, stdDataType=string, VARCHAR, precision=500)
    - Standard audit columns: `VERSION` (propId=11, domain=version), `CREATE_TIME` (propId=12, domain=createTime), `CREATED_BY` (propId=13, domain=createdBy), `UPDATE_TIME` (propId=14, domain=updateTime), `UPDATED_BY` (propId=15, domain=updatedBy), `REMARK` (propId=16, domain=remark)
  - `NopCredentialUsage` (tableName=`nop_credential_usage`, className=`io.nop.credential.dao.entity.NopCredentialUsage`, registerShortName=true):
    - `USAGE_ID` (name=`usageId`, propId=1, PK, stdDataType=string, VARCHAR, precision=50, tagSet="var", mandatory=true)
    - `CREDENTIAL_ID` (name=`credentialId`, propId=2, stdDataType=string, VARCHAR, precision=50, tagSet="var", mandatory=true)
    - `CONSUMER_REF` (name=`consumerRef`, propId=3, stdDataType=string, VARCHAR, precision=200, mandatory=true)
    - `CREATE_TIME` (name=`createTime`, propId=4, domain=createTime)
    - Unique constraint on (CREDENTIAL_ID, CONSUMER_REF) — declared via `<relations>` or index in ORM model.
- [ ] `Fix` — Create `nop-credential-codegen/postcompile/gen-orm.xgen` (follow `nop-file-codegen/postcompile/gen-orm.xgen` exactly):
  ```
  codeGenerator.withTargetDir("../").renderModel('../../model/nop-credential.orm.xml','/nop/templates/orm', '/',$scope);
  codeGenerator.withTargetDir("../nop-credential-dao/src/main/java").renderModel('../../nop-credential-dao/src/main/resources/_vfs/nop/credential/orm/app.orm.xml',
      '/nop/templates/orm-entity','/',$scope);
  ```
- [ ] `Fix` — Create `nop-credential-meta/precompile/gen-meta.xgen` (follow `nop-file-meta/precompile/gen-meta.xgen`):
  ```
  codeGenerator.renderModel('/nop/credential/orm/app.orm.xml','/nop/templates/meta', '/',$scope);
  ```
- [ ] `Fix` — Run codegen: `./mvnw install -pl nop-credential/nop-credential-codegen,nop-credential/nop-credential-meta -am -T 1C` to generate `_gen/` entities in dao + `_gen/` xmeta in meta. Verify generated files appear in `_gen/` directories and are NOT hand-edited.
- [ ] `Fix` — Create xmeta delta override for `NopCredential` in `nop-credential-meta` (`_vfs/nop/credential/model/NopCredential/NopCredential.xmeta`):
  ```xml
  <meta x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef" x:extends="_NopCredential.xmeta">
      <props>
          <prop name="data" published="false"/>
      </props>
  </meta>
  ```
  This delta sets `data` prop `published="false"` (structural plaintext boundary — data never appears in GraphQL schema). The `x:extends` ensures codegen regeneration of `_NopCredential.xmeta` does not overwrite this delta.
- [ ] `Fix` — Create `_module-meta.json` file in `nop-credential-meta` (`_vfs/nop/credential/model/_module-meta.json`, follow `nop-file-meta` pattern: content `{"moduleId":"nop/credential","moduleName":"nop-credential","appName":"nop-credential"}`).

Exit Criteria:

- [ ] `model/nop-credential.orm.xml` passes xdef validation (no ORM schema errors during codegen).
- [ ] Codegen produces `_gen/` entity classes for `NopCredential` and `NopCredentialUsage` in `nop-credential-dao/src/main/java` (verify via file existence — **not hand-edited**).
- [ ] Codegen produces `_NopCredential.xmeta` in meta's `_vfs/nop/credential/model/NopCredential/` directory (same level as the delta file, not in a `_gen/` subdirectory — verify via file existence).
- [ ] `./mvnw compile -pl nop-credential/nop-credential-dao,nop-credential/nop-credential-meta -am -T 1C` succeeds.
- [ ] xmeta delta `NopCredential.xmeta` has `<prop name="data" published="false"/>` (verify via grep — survives codegen regeneration because it's a non-underscore retention file).
- [ ] **No hand-editing of generated files**: all `_gen/` and `_`-prefixed files are codegen output only.
- [ ] `No owner-doc update required` — ORM model follows standard pattern (module doc created in W7).
- [ ] `ai-dev/logs/` entry for the execution day.

### Phase 3 - Consumer SPI (ICredentialProvider)

Status: planned
Targets: `nop-credential/nop-credential-api/src/main/java/io/nop/credential/api/ICredentialProvider.java`, `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/CredentialProviderImpl.java`, DTOs in api layer

**Phase 3 depends on Phase 2 codegen output**: `NopCredential` / `NopCredentialUsage` entity classes and the generated DAO infrastructure must exist before `CredentialProviderImpl` can compile.

- Item Types: `Fix`

- [ ] `Fix` — Create DTOs in `nop-credential-api` (`io.nop.credential.api`): `CredentialData` (wraps `Map<String,Object>` of decrypted field values), `MaskedCredential` (wraps `Map<String,String>` of masked values — sensitive fields replaced with `****`), `TestResult` (fields: `success` boolean, `message` String, `testedAt` timestamp).
- [ ] `Fix` — Create `ICredentialProvider` interface in `nop-credential-api` (`io.nop.credential.api`): `getCredential(String credentialId): CredentialData`, `getCredentialData(String credentialId, String field): Object`, `testCredential(String credentialId): TestResult`, `mask(String credentialId): MaskedCredential`, `registerUsage(String credentialId, String consumerRef): void`, `unregisterUsage(String credentialId, String consumerRef): void`.
- [ ] `Fix` — Create `CredentialProviderImpl` in `nop-credential-service` (`io.nop.credential.service`). Inject: `IOrmEntityDao` or generated DAO for `NopCredential` / `NopCredentialUsage` (codegen product from Phase 2 — the entity's companion `IEntityDao<NopCredential>` is available via `daoProvider.daoFor(NopCredential.class)`), `CredentialCipher` (from W1), `ICredentialTypeRegistry` (from Phase 1). Implementation:
  - `getCredential`: load `NopCredential` by ID via DAO → check `delFlag` (throw `NopException` if deleted — fail-closed) → `String json = credentialCipher.decrypt(entity.getData())` → `Map<String,Object> fields = JsonTool.parseBean(json, Map.class)` → return `new CredentialData(fields)`. Uses `io.nop.api.core.json.JsonTool` for JSON serialization (platform standard).
  - `getCredentialData`: call `getCredential`, return single field value from the map.
  - `mask`: load entity → decrypt → for each field in the credential type definition: if `sensitive=true`, mask as `****`; if non-sensitive, truncate to first 8 chars + `...`. Type definition obtained from `ICredentialTypeRegistry.getType(entity.getTypeName())`.
  - `testCredential`: return `TestResult` with `success=false` + message `"test not implemented for this credential type"` + current timestamp. Update entity `testResult` column with the result JSON. (Explicit not-implemented return — **NOT** a silent no-op per Rule #24; this is an adjudicated deferred item.)
  - `registerUsage`: idempotent — query `NopCredentialUsage` by credentialId+consumerRef, skip if exists, else insert new row.
  - `unregisterUsage`: delete `NopCredentialUsage` rows by credentialId+consumerRef.
- [ ] `Fix` — Register `CredentialProviderImpl` as bean in `credential-defaults.beans.xml` with `@Inject` of DAO provider, `CredentialCipher`, `ICredentialTypeRegistry` (fields must be protected/package-private per Nop IoC).
- [ ] `Fix` — **Soft-delete fail-closed**: `getCredential` / `getCredentialData` / `mask` on a credential with `delFlag=true` throw `NopException` (never silently return empty/null).

Exit Criteria:

- [ ] **端到端 (End-to-End, Rule #22)**: a Nop AutoTest (`@JunitAutoTest` with H2 in-memory DB, DDL auto-initialized from ORM model) that: creates a `NopCredential` entity with `data` = `credentialCipher.encrypt(JsonTool.stringify({"apiKey":"sk-test"}), keyId)` via DAO → calls `ICredentialProvider.getCredential(id)` → asserts decrypted `CredentialData` contains `apiKey=sk-test`. Full round-trip: JSON → encrypt → DB → load → decrypt → JSON → field map.
- [ ] **接线验证 (Wiring Verification, Rule #23)**: the test verifies `CredentialProviderImpl` calls `CredentialCipher.decrypt` (assertion: decrypted output is correct plaintext, which could only come from successful AESTextCipher delegation).
- [ ] Soft-delete test: save credential with `delFlag=true` → `getCredential` throws `NopException` (fail-closed proof).
- [ ] Reference counting test: `registerUsage(credId, "consumer1")` → `registerUsage(credId, "consumer1")` is idempotent (1 row) → `unregisterUsage(credId, "consumer1")` removes the row → verify 0 rows remain.
- [ ] Mask test: `mask(credId)` returns sensitive fields as `****` and non-sensitive fields truncated.
- [ ] `testCredential` test: returns `TestResult` with `success=false` and descriptive message (not null, not empty, not silent void).
- [ ] **无静默跳过 (Rule #24)**: `getCredential` on non-existent credentialId throws `NopException` (not null); deleted credential throws (not silent return); `testCredential` returns explicit not-implemented result.
- [ ] `./mvnw test -pl nop-credential -am -T 1C` green (all unit + AutoTests pass).
- [ ] `No owner-doc update required` — SPI is internal contract (module doc + API docs created in W7).
- [ ] `ai-dev/logs/` entry for the execution day.

## Closure Gates

- [ ] `credential-type.xdef` + register-model + instance files: types loadable via `ResourceComponentManager`.
- [ ] `ICredentialTypeRegistry` exposes types with fail-closed on unknown type.
- [ ] `model/nop-credential.orm.xml` + codegen: entities generated, compile, DDL derivable.
- [ ] xmeta `data` prop `published="false"` via delta override (structural plaintext boundary, survives codegen).
- [ ] `ICredentialProvider` SPI + `CredentialProviderImpl`: encrypt/decrypt round-trip via DAO, fail-closed on deleted/missing credentials, reference counting via `NopCredentialUsage`.
- [ ] No hand-editing of `_gen/` or `_`-prefixed generated files.
- [ ] `./mvnw clean install -pl nop-credential -am -T 1C` green.
- [ ] `./mvnw test -pl nop-credential -am -T 1C` green.
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0.
- [ ] checkstyle / code conventions pass.
- [ ] Independent closure audit by fresh sub-agent, evidence recorded in `Closure` section.

## Deferred But Adjudicated

### Connectivity Test Execution

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `testCredential` SPI contract exists and stores `testResult`; actual HTTP test calls (e.g. calling OpenAI API to verify an API key) are consumer-side concern. W2 provides the storage mechanism + SPI surface; the test execution strategy is a follow-up enhancement. The `testCredential` method explicitly returns `success=false` with "not implemented" message (not a silent no-op).
- Successor Required: no (consumer-side responsibility)

## Non-Blocking Follow-ups

- `testCredential` actual HTTP connectivity test execution (currently returns `success=false` with "not implemented" message — consumer can implement type-specific tests).
- `data` column size optimization (currently VARCHAR(4000); if credential types with many/large fields emerge, consider CLOB migration).

## Closure

Status Note: _(filled at closure)_
Completed: _(filled at closure)_

Closure Audit Evidence: _(filled at closure by independent sub-agent)_

Follow-up: _(filled at closure)_
