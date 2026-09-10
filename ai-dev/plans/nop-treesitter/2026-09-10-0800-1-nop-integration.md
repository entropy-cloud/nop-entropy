---
status: completed
mission: nop-treesitter
work-item: "12"
group: "2026-09-10-0800"
verify: [test]
---

# Nop platform integration: IoC provider bean + GraphQL parseTreeSitter action (M4, part 1)

> Plan Status: active
> Last Reviewed: 2026-09-10
> Source: ai-dev/backlog/nop-treesitter-roadmap.md item 12; live repo pattern probes (nop-biz-file-core, nop-demo, nop-ai-gateway, nop-graphql-core tests)

## Purpose

Make nop-treesitter a first-class Nop citizen: an IoC-managed language provider bean, a GraphQL `parseTreeSitter` query, VFS bean registration, and a user guide — closing NOP-01/02/03/04 of roadmap item 12.

## Current Baseline

- Items 2-11 `done`, M3 `done`. Module suite: **373 tests green**, checkstyle -Pqa 0, hollow scan 0. Corpora: JSON 7/7, Java 108/108, JS 116/116, TS 110/111, TSX 110/111.
- `nop-treesitter/pom.xml` depends only on `nop-commons` + `slf4j-api` (+ JUnit 5 test). The module has no Nop platform wiring: no `_vfs` resources, no beans, no BizModel.
- Public parse API: `TSParser.parse(Language, String)` and `TSTree.toSExpression()`; grammars load via `Language.fromClasspath("/grammars/<name>/tree-sitter-<name>-blob.bin")` for the five shipped grammars (json, java, javascript, typescript, tsx — verified resource paths in `src/main/resources/grammars/`).
- Repo conventions verified live (2026-09-10): GraphQL action minimal chain = `@BizModel` class + the `_vfs/<moduleId>/beans/app-*.beans.xml` convention + zero-byte the zero-byte `_vfs/<moduleId>/_module` marker marker (pattern: `nop-demo/nop-spring-demo-no-orm`, `nop-service-framework/nop-biz-file-core`); beans wiring uses `<bean id="<FQCN>" ioc:type="@bean:id" ioc:default="true">` + `<property name="x" ref="..."/>` into a `protected` field (`app-file-core.beans.xml` + `NopFileStoreBizModel.fileStore`); lightweight IoC test = `AppBeanContainerLoader.loadFromResource` over a VFS beans resource (`nop-ai-gateway TestRuleBasedSelectionStrategyIoC`); direct GraphQL engine test = `CoreInitialization.initialize()` + `GraphQLEngine` + `newRpcContext` + `executeRpcAsync` (`nop-graphql-core TestOperationMfaExecutorWiring`). `nop-ioc` is test-scoped where its initializer must not leak into other tests (`nop-stream/nop-stream-runtime/pom.xml`).
- ServiceLoader usage in-repo: `CoreInitialization` loads `ICoreInitializer` via `META-INF/services` — the pattern the roadmap's "META-INF/services" wording maps to for third-party grammar registration.
- Roadmap-literal deviations adjudicated up front: (a) the roadmap's suggested `src/main/resources/io/nop/treesitter/beans.xml` becomes the repo-standard `nop-treesitter/src/main/resources/_vfs/nop/treesitter/beans/app-treesitter.beans.xml` (VFS bean discovery convention, verified against nop-biz-file-core / nop-demo); (b) "beans.xml registering built-in grammars" — the five built-ins are hardcoded in the default provider (blob paths are classpath constants, not beans), the beans file registers the provider and BizModel beans. Both deviations recorded here so closure cannot be read as contract drift.

## Goals

- **NOP-01**: `ITreeSitterLanguageProvider` — an interface resolving grammar names to `Language` instances; default implementation ships the five built-in grammars and additionally loads third-party providers declared under `META-INF/services/io.nop.treesitter.provider.ITreeSitterLanguageProvider` (ServiceLoader, C `ICoreInitializer` precedent). Registered as a NopIoC bean.
- **NOP-02**: `TreeSitterBizModel` with `@BizQuery parseTreeSitter(@Name("source") String source, @Name("language") String language): String` returning the tree's `toSExpression()`; unknown language raises a typed `NopException` with a defined error code.
- **NOP-03**: `nop-treesitter/src/main/resources/_vfs/nop/treesitter/beans/app-treesitter.beans.xml` registering the provider bean and the BizModel (property-wired), plus the `_module` marker.
- **NOP-04**: user guide section in `nop-treesitter/README.md` (grammar registration + GraphQL usage).

## Non-Goals

- XLang integration (roadmap: deferred).
- Streaming/paged parse APIs, mutation actions, per-language configuration files.
- Any change to the parser/lexer/recovery machinery (items 2-11 code paths stay untouched).

## Scope

### In Scope

- `pom.xml`: add `nop-graphql-core` (compile — brings `nop-api-core` annotations and `nop-core` transitively, patterned on `nop-biz-file-core`) and `nop-ioc` (test scope, patterned on `nop-stream-runtime`).
- `io.nop.treesitter.provider.ITreeSitterLanguageProvider` (interface), `io.nop.treesitter.provider.DefaultTreeSitterLanguageProvider` (built-ins + ServiceLoader extension), `io.nop.treesitter.biz.TreeSitterBizModel`, `io.nop.treesitter.biz.TreeSitterErrors` (error code via `ErrorCode.define`, `FileErrors` pattern).
- `_vfs/nop/treesitter/_module`, `nop-treesitter/src/main/resources/_vfs/nop/treesitter/beans/app-treesitter.beans.xml`.
- Tests: IoC container test loading the real `app-treesitter.beans.xml` from VFS (asserts the BizModel bean resolves with the provider property wired and answers a parse); GraphQLEngine end-to-end test driving `TreeSitter__parseTreeSitter` (happy path + unknown-language NopException path); ServiceLoader extension test with a fake provider registered under test `META-INF/services`.
- `nop-treesitter/README.md`: user guide section (NOP-04).

### Out Of Scope

- Everything else in the module; other modules' code; platform initialization order changes.

## Execution Plan

### Phase 1 — Provider, BizModel, beans wiring, IoC test

Status: completed
Targets: `pom.xml`, `provider/`, `biz/`, `_vfs/nop/treesitter/`, IoC test

- Item Types: `Fix | Proof`

- [x] `ITreeSitterLanguageProvider`: `Language getLanguage(String name)` (null return for unknown, never throw) + `Set<String> languageNames()`. `DefaultTreeSitterLanguageProvider`: built-in five grammars from classpath (cached), plus ServiceLoader-merged third-party providers (deduplicated by name; custom wins over built-in — recorded in javadoc). **ServiceLoader semantics (adjudicated): the module itself does NOT publish a `META-INF/services/io.nop.treesitter.provider.ITreeSitterLanguageProvider` file in main resources** — the default provider is exposed only as an IoC bean, and the ServiceLoader merge defensively skips the `DefaultTreeSitterLanguageProvider` class itself (prevents self-recursion StackOverflow; the roadmap's `src/main/resources/META-INF/` area stays empty unless a third party registers providers).
- [x] `TreeSitterBizModel` (`@BizModel("TreeSitter")`): `@BizQuery parseTreeSitter(@Name("source"), @Name("language"), IServiceContext)` → `toSExpression()`; unknown language → `NopException(ERR_TREE_SITTER_UNKNOWN_LANGUAGE).param(ARG_LANGUAGE, language)`; dependency wired exactly like the `NopFileStoreBizModel` precedent: a `@Inject` setter assigning a `protected` field, fed by beans `<property name="languageProvider" ref="treeSitterLanguageProvider"/>`. Blank source parses (empty document tree) — no special-casing.
- [x] pom deps + `_module` marker + `app-treesitter.beans.xml`: `<bean id="treeSitterLanguageProvider" class="io.nop.treesitter.provider.DefaultTreeSitterLanguageProvider"/>` and `<bean id="io.nop.treesitter.biz.TreeSitterBizModel" ioc:type="@bean:id" ioc:default="true"><property name="languageProvider" ref="treeSitterLanguageProvider"/></bean>`; no `ioc:condition` needed (both beans live in this same self-contained file); error code string `nop.err.treesitter.unknown-language` with `ARG_LANGUAGE = "language"`.
- [x] IoC test (TestRuleBasedSelectionStrategyIoC pattern verbatim): `CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT)` → `AppBeanContainerLoader.loadFromResource(...)` over the VFS resource `/nop/treesitter/beans/app-treesitter.beans.xml` → `container.start()` → `container.getBean` of both beans → BizModel parses `{"a":1}` through the wired provider (wiring proof: the BizModel bean must answer with a document s-expression, not a direct-construction instance).
- [x] ServiceLoader extension test: a test-only provider under `src/test/resources/META-INF/services/...` registers a sixth grammar name (e.g. re-exposing json as "custom-json"); the default provider resolves it and `languageNames()` contains it.

Exit Criteria:

- [x] IoC test green with both beans resolved from the real beans.xml (wiring verification per Minimum Rules #23 — the BizModel instance comes from the container with its provider injected, proven by a parse through the container instance).
- [x] ServiceLoader extension test green; unknown-language error path green (NopException with the defined code, message names the language).
- [x] `No owner-doc update required` (README lands in Phase 2 as NOP-04).
- [x] `ai-dev/logs/2026/09-10.md` entry added.

### Phase 2 — GraphQL end-to-end + README guide

Status: completed
Targets: GraphQLEngine test, `nop-treesitter/README.md`

- Item Types: `Proof | Fix`

- [x] GraphQLEngine test (TestOperationMfaExecutorWiring pattern): `CoreInitialization.initialize()` + schema built from the **IoC container's `TreeSitterBizModel` bean** (AppBeanContainerLoader as in Phase 1 — proves the container-wired bean serves GraphQL traffic, closing the Anti-Hollow runtime-call requirement end to end) + `newRpcContext(GraphQLOperationType.query, "TreeSitter__parseTreeSitter", request)` → response ok, data equals the expected s-expression; unknown language → response error carrying the error code.
- [x] README user guide section (NOP-04): what the module is, Maven coordinates, the five built-in grammars, `ITreeSitterLanguageProvider` extension via `META-INF/services`, beans registration, and a GraphQL query example — content checked against the live code in the same phase.

Exit Criteria:

- [x] **端到端验证**: a GraphQL query string through `GraphQLEngine.executeRpcAsync` reaches `TSParser.parse` and returns the s-expression (entry point → provider → parser → exit point), plus the error path returns a structured error.
- [x] README section matches live code (paths, class names, query example actually executable per the engine test).
- [x] `ai-dev/logs/2026/09-10.md` updated.

## Draft Review Record

- Round 1 (2026-09-10, independent subagent agent_6e3bd4e1, adversarial + imaginative): all claimed repo facts verified true (BOM artifacts, AppBeanContainerLoader signature, engine-test pattern, annotation locations, _module convention, test-classpath ServiceLoader precedent); 1 Major (P1 ServiceLoader self-recursion semantics unspecified) + 5 Minor (P2 injection precedent, P3 IoC test steps, P4 engine-test bean source, P5 missing blockquote/Purpose, P6 bean-id/error-code literals, P7 roadmap-literal deviations unrecorded). All fixed in this revision; NopIoC protected-field property injection confirmed setter-free-capable (BeanDefinitionBuilder field-reflection fallback) but precedent-style @Inject setter chosen.
- Round 2 (2026-09-10, independent subagent agent_16b21acb, fix verification): 7/7 RESOLVED, no new contradictions, Phase 1 artifacts uniquely determined. Verdict: approved for execution.

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 frontmatter `status` 改为 `completed`。

- [x] 所有 in-scope confirmed live defects 已修复（含执行中发现的新缺陷）
- [x] 所有 in-scope confirmed contract drifts 已收敛：审计 M-1（custom-wins 契约与代码相反）已按审计方向 (a) 修复——loadLanguage 先查 custom、languageNames 并集简化、同名遮蔽用 loadCustomProviders 覆写点测试钉住（javascript blob 冒充 json 可区分）
- [x] 行为/契约结果已达成：NOP-01/02/03/04 全部落地
- [x] 必要 focused verification 已完成（IoC 装配、ServiceLoader 扩展、GraphQL 端到端、错误路径）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步（README 为 owner doc，含 Maven 坐标；docs-for-ai 模块页归 item 14）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（写入 `## Closure` 段）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）provider 被 BizModel 在运行时真实调用——GraphQL 端到端用 BeanContainer.getBeanByType 容器 bean 实测（b）无空方法体/静默跳过/no-op
- [x] `./mvnw -pl nop-treesitter -am test -T 1C` 通过（381 tests）
- [x] checkstyle / 代码规范检查通过（`checkstyle:check -Pqa` 0 violations）

## Closure

Status Note: NOP-01/02/03/04 全部落地：ITreeSitterLanguageProvider（5 内置 + ServiceLoader 第三方扩展、custom-wins、防自递归）、TreeSitterBizModel（TreeSitter__parseTreeSitter，未知语法 NopException nop.err.treesitter.unknown-language）、_vfs/nop/treesitter VFS 注册（_module + app-treesitter.beans.xml，provider/BizModel 双 bean）、README 用户指南（含 Maven 坐标）。381 tests green、checkstyle -Pqa 0、hollow scan 0。审计 M-1（同名语法 custom/built-in 优先级：文档声明 custom-wins、代码内置-wins）已按审计方向 (a) 修复并新增 loadCustomProviders 覆写点的同名遮蔽测试（javascript blob 冒充 json，输出可区分）；m-1/m-2/m-3（frontmatter、Draft Review Record、README Maven 坐标）一并补齐。
Completed: 2026-09-10

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）agent_aa0de20a-243d-4810-b7be-1595341b5cad
- Audit Session: agent_aa0de20a-243d-4810-b7be-1595341b5cad
- Evidence:
  - Phase 1/2 Exit Criteria：PASS——11 项交付物逐条 live 核对（provider/BizModel/Errors/_module/beans.xml/三测试/services 文件/README/pom）
  - Anti-Hollow：TestParseTreeSitterGraphQL:41-44 实读——BeanContainer.getBeanByType 容器 bean 进引擎，调用链 GraphQL RPC → BizModel → provider.getLanguage → TSParser.parse 全连通；无 new 绕过
  - 无静默跳过：未知语言抛带码 NopException（两条测试覆盖）；getLanguage 未知名返回 null 为接口契约且 javadoc 明示
  - 行为：381 green、checkstyle -Pqa 0、hollow scan --severity high 0（审计实测）
  - P1 修复验证：main resources 无 META-INF/services；loadCustomProviders 跳过自身类
  - M-1 修复（审计后）：loadLanguage custom-first + 同名遮蔽测试（javascript 冒充 json，`(program (expression_statement ...))` vs `(document ...)` 可区分）；languageNames 死分支清理
  - m-1/m-2/m-3 修复（审计后）：frontmatter 流转、Draft Review Record 回填两轮审查、README 补 Maven 坐标
  - `node ai-dev/tools/check-plan-checklist.mjs --strict`：closure 动作完成后退出码 0
  - Deferred 分类检查：无 deferred 项

## Deferred But Adjudicated
## Non-Blocking Follow-ups

- （无）
