# 1 nop-credential Module Skeleton + Crypto Layer

> Plan Status: completed
> Last Reviewed: 2026-08-12
> Source: `ai-dev/backlog/nop-credential-mfa-roadmap.md` (W1), `ai-dev/design/nop-credential/01-architecture-baseline.md` §3.1–§3.2
> Mission: nop-credential-mfa
> Work Item: W1
> Related: Plan 334 (encrypted-value format — established `v1:` marker in `AESTextCipher`)

## Purpose

Create the `nop-credential` module skeleton (standard Nop layered module) and implement the credential-specific crypto layer (`cv1:` format + multi-key provider). After this plan, `nop-credential` compiles standalone, the `cv1:{keyId}:{v1密文}` format is fully implemented and unit-tested, and downstream plans (W2 data model, W3 management API) have a stable crypto foundation to build on.

## Current Baseline

- **`AESTextCipher`** (`nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/impl/AESTextCipher.java`): implements `ITextCipher`. Plan 334 added the self-describing `v1:` format: `encrypt()` → `v1:{base64(iv||ciphertext+tag)}`, `decrypt()` dispatches by `V1_MARKER` presence. PBKDF2WithHmacSHA256 (65536 iterations, AES-256-GCM), per-message random IV, thread-safe. API: `new AESTextCipher().encKey("passphrase")` → configured cipher. Note: `encrypt()` output contains `v1:` prefix followed by base64 payload (no colons in base64 alphabet), so the full output is `v1:{base64data}`.
- **`ITextCipher`** (`nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/ITextCipher.java`): interface with `encKey(String)`, `encrypt(String)`, `decrypt(String)`. Located in `nop-commons` (NOT nop-api-core).
- **nop-api-core dependency**: verified that `nop-api-core/pom.xml` does NOT depend on `nop-commons`. Therefore `nop-credential-api` must declare an explicit dependency on `nop-commons` to use `ITextCipher`. This aligns with design §3.1 which states "仅 nop-api-core/nop-commons" (both, not just nop-api-core).
- **`nop-credential` module does not exist** — no directory, no root pom entry. Confirmed via glob.
- **Reference modules**: `nop-file/` (parent pom + api/dao/meta/service/web/app/codegen sub-modules), `nop-retry/` (same pattern). Parent pom uses `<packaging>pom</packaging>`.
  - `nop-file-api/pom.xml`: depends on `nop-api-core` only.
  - `nop-file-dao/pom.xml`: depends on `nop-file-api` + nop-orm dependencies. No exec-maven-plugin (passive consumer of codegen output).
  - `nop-file-meta/pom.xml`: depends on `nop-file-dao` + `nop-file-codegen` (both test scope) + exec-maven-plugin with `<classpathScope>test</classpathScope>` for precompile xgen scripts.
  - `nop-file-codegen/pom.xml`: depends on nop-ooxml-xlsx, nop-orm, nop-graphql-core, nop-xlang-debugger + exec-maven-plugin.
- **Root pom.xml** (`pom.xml:536-578`): `<modules>` section lists all top-level modules; `nop-credential` is absent.
- **Plan 334** (`ai-dev/plans/334-encrypted-value-format.md`): `completed`. Established that `AESTextCipher.encrypt()` outputs `v1:` prefixed ciphertext. W1's `cv1:` format wraps this: `cv1:{keyId}:{v1密文}` where the inner `{v1密文}` is a standard `AESTextCipher.encrypt()` output (i.e. `v1:{base64data}`).
- **Data serialization convention (cross-plan, established here)**: `CredentialCipher` operates on `String` only. The calling layer (W2 `CredentialProviderImpl`) is responsible for JSON serialization/deserialization: credential field values are serialized to a JSON string (e.g. `{"apiKey":"sk-xxx","orgId":"org1"}`) before calling `CredentialCipher.encrypt(jsonString, keyId)`, and the decrypted string is parsed back from JSON to `Map<String,Object>` after `CredentialCipher.decrypt(cv1Text)`. Platform's `io.nop.api.core.json.JsonTool` is the standard JSON utility.

## Goals

- `nop-credential` directory exists with standard Nop module structure (parent pom + sub-module poms) and compiles via `./mvnw compile -pl nop-credential -am`.
- `CredentialCipher` implements `cv1:{keyId}:{v1密文}` encrypt/decrypt by delegating the inner `v1:` payload to `AESTextCipher`.
- `ICredentialKeyProvider` SPI + `DefaultCredentialKeyProvider` implementation manage multiple master keys (active key for encryption, all keys for decryption/rotation).
- Master key source: Nop config property `nop.credential.master-keys` (a `List<String>` of `keyId:passphrase` entries, injectable via standard `@InjectValue` / environment variable mapping). KeyId constrained to `[A-Za-z0-9_-]`.
- Focused unit tests prove: cv1 round-trip, multi-key decrypt after rotation, keyId validation, fail-closed on unknown/tampered values.

## Non-Goals

- Credential type registration / `credential-type.xdef` / register-model (W2).
- ORM entities `NopCredential` / `NopCredentialUsage` (W2).
- `ICredentialProvider` consumer SPI / BizModel / GraphQL API / Web pages (W2/W3).
- `@sec:` config encryption changes (already handled by platform — out of scope entirely).
- RBAC / tenant isolation / OAuth / external KMS (二期 per Vision §3).
- Any modification to `AESTextCipher` itself (it is used as-is, not modified).

## Scope

### In Scope

- `nop-credential/` directory creation with full module skeleton (parent pom + all sub-module poms + directory structure).
- Root `pom.xml` `<modules>` registration.
- `ICredentialKeyProvider` interface (in `nop-credential-api`).
- `CredentialCipher` class (in `nop-credential-service`).
- `DefaultCredentialKeyProvider` implementation + master key config loading.
- `CredentialConfigs` class for config property keys (`nop.credential.*`).
- Unit tests for the crypto layer.

### Out Of Scope

- ORM model / DAO entities / xmeta / BizModel (W2, W3).
- `credential-type.xdef` / register-model.xml / type instance files (W2).
- `ICredentialProvider` SPI (W2).
- Web management pages (W3).
- Existing `NopAiModel.apiKey` migration (W7).

## Execution Plan

### Phase 1 - Module Skeleton Creation

Status: completed
Targets: `nop-credential/pom.xml`, `nop-credential/nop-credential-{api,dao,meta,service,web,app,codegen}/pom.xml`, `pom.xml` (root)

- Item Types: `Fix | Decision`

- [x] `Decision` — Create `nop-credential/` parent pom (`<packaging>pom</packaging>`, parent = `nop-entropy`, name following numbering convention, `<modules>` listing all sub-modules). Follow `nop-file/pom.xml` as the structural template.
- [x] `Fix` — Create `nop-credential-api` sub-module pom: depends on `nop-api-core` AND `nop-commons` (explicit — `ITextCipher` is in nop-commons, and nop-api-core does not transitively provide it). Follow `nop-file-api/pom.xml` structure but add the nop-commons dependency.
- [x] `Fix` — Create `nop-credential-dao` sub-module pom: depends on `nop-credential-api` + nop-orm dependencies. NO exec-maven-plugin (passive consumer of codegen output written by codegen module). Follow `nop-file-dao/pom.xml`.
- [x] `Fix` — Create `nop-credential-meta` sub-module pom: depends on `nop-credential-dao` + `nop-credential-codegen` (both test scope) + exec-maven-plugin with `<classpathScope>test</classpathScope>`. Follow `nop-file-meta/pom.xml` exactly — meta needs these to run precompile xgen scripts that read dao's generated `app.orm.xml`.
- [x] `Fix` — Create `nop-credential-service` sub-module pom: depends on dao + meta + nop-biz + nop-config. Follow `nop-file-service/pom.xml`.
- [x] `Fix` — Create `nop-credential-web` sub-module pom. Follow `nop-file-web/pom.xml`.
- [x] `Fix` — Create `nop-credential-app` sub-module pom: test/runtime assembly module with exec-maven-plugin. Follow `nop-file-app/pom.xml`.
- [x] `Fix` — Create `nop-credential-codegen` sub-module pom: depends on nop-ooxml-xlsx, nop-orm, nop-graphql-core, nop-xlang-debugger + exec-maven-plugin. Follow `nop-file-codegen/pom.xml` exactly. Set `<maven.deploy.skip>true</maven.deploy.skip>`.
- [x] `Fix` — Register `<module>nop-credential</module>` in root `pom.xml` `<modules>` section (at the end of the list, after existing entries).
- [x] `Fix` — Create standard directory structure for each sub-module (`src/main/java/io/nop/credential/...`, `src/main/resources/`, `src/test/java/...`).

Exit Criteria:

- [x] `./mvnw compile -pl nop-credential -am -T 1C` succeeds (all poms resolve, directories exist, no compilation errors).
- [x] `./mvnw compile -pl nop-credential -am -T 1C` does NOT introduce any dependency cycle (nop-credential-api depends only on nop-api-core + nop-commons, both kernel modules).
- [x] Each sub-module pom follows the same structural pattern as the corresponding `nop-file-*` pom (verified by diff comparison).
- [x] `ai-dev/logs/` entry for the execution day.

### Phase 2 - Crypto Layer: CredentialCipher + Key Provider

Status: completed
Targets: `nop-credential/nop-credential-api/src/main/java/io/nop/credential/api/crypto/ICredentialKeyProvider.java`, `nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/CredentialCipher.java`, `nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/DefaultCredentialKeyProvider.java`, `nop-credential/nop-credential-service/src/main/java/io/nop/credential/config/CredentialConfigs.java`

- Item Types: `Fix`

- [x] `Fix` — Create `ICredentialKeyProvider` interface in `nop-credential-api` (`io.nop.credential.api.crypto`): `getActiveKeyId(): String`, `getKey(String keyId): ITextCipher`, `getKeyIds(): Set<String>`. Interface references `ITextCipher` from `io.nop.commons.crypto` (nop-commons dependency declared in Phase 1).
- [x] `Fix` — Create `CredentialCipher` in `nop-credential-service` (`io.nop.credential.crypto`). This class has `@Inject ICredentialKeyProvider keyProvider` (bean injection). Methods:
  - `encrypt(String plainJson, String keyId): String` → calls `keyProvider.getKey(keyId).encrypt(plainJson)`, then wraps result as `CV1_MARKER + keyId + ":" + v1Ciphertext` where `v1Ciphertext` is the AESTextCipher output (already starts with `v1:`). Final format: `cv1:{keyId}:v1:{base64data}`.
  - `encrypt(String plainJson): String` → convenience method using `keyProvider.getActiveKeyId()`.
  - `decrypt(String cv1Text): String` → **parse logic**: check starts with `CV1_MARKER` ("cv1:") → strip "cv1:" prefix → split remaining by first `:` (using `indexOf(':')`) into `keyId` (before) and `v1Payload` (after, which is the full `v1:{base64data}` string) → call `keyProvider.getKey(keyId).decrypt(v1Payload)` → return plaintext. **Critical**: the v1Payload retains its `v1:` prefix because `AESTextCipher.decrypt()` expects it for format dispatch.
  - Constant: `CV1_MARKER = "cv1:"`, `CV1_KEY_ID_PATTERN = "[A-Za-z0-9_-]+"`.
- [x] `Fix` — Implement `DefaultCredentialKeyProvider` in `nop-credential-service`: reads master keys from Nop config property `nop.credential.master-keys` (injected via `@InjectValue` as `List<String>`, each entry formatted `keyId:passphrase`). The config property is set in `application.yaml` or via environment variable `NOP_CREDENTIAL_MASTER_KEYS` (Nop config supports env var → property mapping with `__` as list separator or comma-separated values). For each entry: parse by first `:` into keyId + passphrase, validate keyId against `CV1_KEY_ID_PATTERN`, construct `new AESTextCipher().encKey(passphrase)`, cache by keyId in a `Map<String, ITextCipher>`. `getActiveKeyId()` returns `nop.credential.active-key-id` config if set, otherwise the first configured keyId. Invalid keyId format → throw `NopException` at initialization (fail-closed, no silent skip).
- [x] `Fix` — Create `CredentialConfigs` in `nop-credential-service` (`io.nop.credential.config`): config property references using `AppConfig.varRef()` pattern (follow platform `CommonConfigs` / `CoreConfigs` convention — `public static final IConfigReference<List<String>> CFG_CREDENTIAL_MASTER_KEYS = AppConfig.varRef(CredentialConfigs.class, "nop.credential.master-keys", List.class, null)`). Properties: `nop.credential.master-keys` (List<String>), `nop.credential.active-key-id` (String, optional).
- [x] `Fix` — Register beans in `nop-credential-service/src/main/resources/_vfs/nop/credential/beans/credential-defaults.beans.xml`. Follow existing platform beans.xml patterns (e.g. search for any `*.beans.xml` in `_vfs/` for `<bean>` element structure). Bean definitions:
  - `<bean id="defaultCredentialKeyProvider" class="io.nop.credential.crypto.DefaultCredentialKeyProvider" />` (uses `@InjectValue` for config injection, `@PostConstruct` for key loading).
  - `<bean id="credentialCipher" class="io.nop.credential.crypto.CredentialCipher" />` (uses `@Inject ICredentialKeyProvider` field injection — field must be `protected` or package-private per Nop IoC rule, NOT `private`).
- [x] `Fix` — Implement malformed-input handling in `CredentialCipher.decrypt`: if input does not start with `cv1:`, or no `:` found after stripping prefix (missing keyId), or v1Payload is empty → throw `NopException` with descriptive message. Never return null or empty string.
- [x] `Fix` — Implement unknown-keyId handling: `decrypt` with a keyId not in `keyProvider.getKeyIds()` throws `NopException` with message including the unknown keyId and available keyIds.

Exit Criteria:

- [x] `CredentialCipher.encrypt(plainJson, keyId)` produces output matching the regex `^cv1:[A-Za-z0-9_-]+:v1:[A-Za-z0-9+/=]+$` (focused test assertion — proves 4-segment format: cv1, keyId, v1 marker, base64 payload).
- [x] `CredentialCipher.decrypt` round-trips: `decrypt(encrypt(plain, keyId))` equals original plaintext (focused test). Verify with plaintext that is a JSON string (e.g. `{"apiKey":"sk-test123"}`).
- [x] Multi-key decrypt: encrypt with keyA, switch active key to keyB, decrypt still succeeds using keyA's cipher (focused test — proves rotation compatibility). Specifically: `decrypt(cv1:keyA:v1:...)` must use keyA even when active key is keyB.
- [x] Fail-closed test: decrypt with each malformed input variant (missing `cv1:` prefix, missing keyId, empty v1Payload, unknown keyId) throws `NopException`, never returns null or empty string.
- [x] KeyId validation test: `DefaultCredentialKeyProvider` initialization with a master key entry containing invalid characters (e.g. `my:key`, `key with space`) throws `NopException` at startup.
- [x] **无静默跳过 (Rule #24)**: no empty method bodies, no swallowed exceptions, no `// TODO` placeholders. Every error path throws `NopException`.
- [x] `No owner-doc update required` — this phase creates internal infrastructure with no documented public contract in `docs-for-ai/` yet (the credential module doc will be created in W7).
- [x] `ai-dev/logs/` entry for the execution day.

### Phase 3 - Beans Registration And Build Verification

Status: completed
Targets: `nop-credential/nop-credential-service/src/main/resources/_vfs/nop/credential/beans/credential-defaults.beans.xml`, `nop-credential/nop-credential-app/`

- Item Types: `Proof`

- [x] `Proof` — Verify `credential-defaults.beans.xml` is loadable by Nop IoC: bean definitions for `DefaultCredentialKeyProvider` and `CredentialCipher` with correct class paths and property/inject annotation usage. Verify field injection visibility (protected/package-private, not private).
- [x] `Proof` — Run `./mvnw clean install -pl nop-credential -am -T 1C -DskipTests` to verify the entire module builds end-to-end (all sub-modules compile and assemble).
- [x] `Proof` — Run `./mvnw test -pl nop-credential -am -T 1C` to verify all unit tests pass.

Exit Criteria:

- [x] `./mvnw clean install -pl nop-credential -am -T 1C -DskipTests` exits 0.
- [x] `./mvnw test -pl nop-credential -am -T 1C` exits 0.
- [x] **端到端 (End-to-End, Rule #22)**: a unit test that constructs `CredentialCipher` with a test `ICredentialKeyProvider` (configured with a test passphrase), calls `encrypt(jsonString)` → `decrypt(result)` → asserts plaintext equality. This proves the full path from key provider → cipher → format wrapping → format parsing → key provider lookup → AESTextCipher delegation → plaintext recovery.
- [x] **接线验证 (Wiring Verification, Rule #23)**: the test injects `CredentialCipher` and verifies it internally calls `ICredentialKeyProvider.getKey()` (assertion: encrypt output contains the keyId returned by `keyProvider.getActiveKeyId()`; decrypt success requires the keyId to be registered in the provider).
- [x] No `nop-credential` dependency appears in any other module's pom yet (W1 is self-contained — downstream consumers come in W2+).
- [x] `ai-dev/logs/` entry for the execution day.

## Closure Gates

- [x] `nop-credential` module exists, compiles, and is registered in root pom.xml.
- [x] `CredentialCipher` implements `cv1:{keyId}:v1:{base64data}` format with verified round-trip and multi-key rotation support.
- [x] `ICredentialKeyProvider` + `DefaultCredentialKeyProvider` manage master keys from Nop config property.
- [x] All failure paths (malformed input, unknown keyId, invalid keyId) throw `NopException` — no silent no-op.
- [x] `./mvnw clean install -pl nop-credential -am -T 1C -DskipTests` green.
- [x] `./mvnw test -pl nop-credential -am -T 1C` green.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0 (no new broken links from this plan's files).
- [x] checkstyle / code conventions pass (imports grouped, 4-space indent, package naming `io.nop.credential.*`).
- [x] Independent closure audit by fresh sub-agent, evidence recorded in `Closure` section.

## Deferred But Adjudicated

_(none — W1 scope is self-contained)_

## Non-Blocking Follow-ups

- Master key hot-reload at runtime (optimization candidate — currently keys loaded at startup only).
- External KMS / Vault SPI for master key source (二期 per Vision §3).
- Standalone `credential-keys.yaml` config file loader (optimization candidate — 一期 uses Nop config property which can be set via env var; dedicated file loader is a convenience enhancement).

## Closure

Status Note: W1 完成 `nop-credential` 模块骨架（7 个标准子模块 + 根 pom + BOM 注册）与凭证密码学层（`CredentialCipher` 的 `cv1:{keyId}:v1:{base64}` 格式 + `DefaultCredentialKeyProvider` 多密钥管理与 fail-closed 启动校验）。30 个单元测试覆盖 round-trip、多密钥轮换、全部畸形输入 fail-closed、keyId 校验、端到端与接线。下游 W2/W3 可基于稳定的 crypto 基础继续构建。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_00cb537efffeShevY06p94WNfp`，general 类型）
- Audit Session: ses_00cb537efffeShevY06p94WNfp
- Evidence:
  - **Per-item verification (17 项全部 PASS)**：
    - Phase 1 (1-6) PASS: parent pom (`nop-credential/pom.xml`, name=`32-nop-credential`, packaging=pom)；7 个子模块 pom 均存在且结构匹配 nop-file；api 依赖 nop-api-core + nop-commons；根 pom:577 注册；nop-bom 5 条 version-managed 条目；api 无内部子模块依赖（无环）。
    - Phase 2 (7-12) PASS: `ICredentialKeyProvider`（3 方法，引用 ITextCipher）；`CredentialCipher`（`@Inject protected` 字段，cv1 格式编解码，v1 前缀保留给 AESTextCipher，全部错误路径抛 NopException）；`DefaultCredentialKeyProvider`（@InjectValue + @PostConstruct，fail-closed init）；`CredentialConfigs`（两个 varRef）；`credential-defaults.beans.xml`（两个 bean）；`CredentialErrors`（5 个 ErrorCode）。
    - Phase 3 (13-14) PASS: TestCredentialCipher 16 tests + TestDefaultCredentialKeyProvider 14 tests = 30 tests，全部通过。
  - **端到端 (Rule #22)** PASS: `TestDefaultCredentialKeyProvider.endToEndWithCredentialCipher`（line 161）—— DefaultCredentialKeyProvider(2 keys) → CredentialCipher → encrypt → decrypt → 明文相等，验证 key provider → cipher → cv1 wrap → cv1 parse → AESTextCipher 委托 → 明文恢复 全链。
  - **接线验证 (Rule #23)** PASS: `TestCredentialCipher.cipherCallsKeyProviderAtRuntime`（line 304）—— 断言 encrypt 输出含 active keyId（证明运行时调用 getActiveKeyId/getKey），decrypt 成功要求 keyId 在 getKeyIds 中注册。
  - **无静默跳过 (Rule #24)** PASS: grep "TODO|FIXME|return null" 跨所有 main source 文件零匹配；手工复核 5 个源文件无空方法体、无 catch+ignore、无 continue 跳过、无 null placeholder；每个错误路径均抛 NopException。
  - **Anti-Hollow 自动扫描**: `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-credential --severity high` 退出码 0（Total findings: 0）。
  - **Checklist 完整性**: `node ai-dev/tools/check-plan-checklist.mjs <plan-file>` 退出码 0（Passed: 1, Failed: 0）。
  - **Build**: `./mvnw clean install -pl nop-credential -am -T 1C -DskipTests` → BUILD SUCCESS；`./mvnw test -pl nop-credential -am -T 1C` → BUILD SUCCESS（30 tests, 0 failures）。
  - **Deferred 项分类检查**: 无 in-scope live defect 被降级；Non-Blocking Follow-ups（热重载、外部 KMS、独立配置文件）均为 optimization candidate / 二期，附带 non-blocking 理由。

Follow-up:

- 主密钥运行期热重载（optimization candidate，当前启动期加载）
- 外部 KMS / Vault SPI（二期）
- 独立 `credential-keys.yaml` 配置文件加载器（optimization candidate，一期用 Nop 配置项 + 环境变量）
- no remaining plan-owned work（W1 scope 全部落地）
