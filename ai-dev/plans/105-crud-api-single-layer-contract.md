# 105 CRUD API 单层契约收敛

> Plan Status: completed
> Last Reviewed: 2026-06-04
> Source: `ai-dev/design/crud/crud-api-codegen-design.md`, `ai-dev/plans/104-crud-api-codegen-implementation.md`, user decision on 2026-06-04
> Related: `ai-dev/design/crud/relation-write-mode-design.md`

## Purpose

将 CRUD typed API 的 InputBean、OutputBean 与 CRUD 接口从当前的 `_gen/_* + public wrapper` 双层生成结构收敛为单层公共契约，消除对外暴露 `_` 前缀类型导致的关系字段类型漂移与契约噪音。

## Current Baseline

- CRUD typed API 已按 `xmeta + *-meta/postcompile` 生成，并由 `no-api` 控制实体级生成。
- 当前 Bean 生成策略为双层：`beans._gen._{Entity}InputBean/_OutputBean` 强制覆盖，`beans.{Entity}InputBean/{Entity}OutputBean` 保留层继承前者。
- 当前 CRUD 接口生成策略也为双层：`crud._{Entity}Api` 强制覆盖，`crud.{Entity}Api` 作为保留层扩展接口。
- 关系型 InputBean 字段当前引用 `_XxxInputBean`，这会把内部生成层类型泄漏到对外契约。
- `ICrudApi`、`ICrudTreeApi`、`CrudInputBase` 已稳定，服务端 `CrudBizModel` / `ICrudBiz` 不在本次收敛范围内。

## Goals

- 让 CRUD typed API 的对外契约只暴露单层公共 Bean / Api 类型，不再暴露 `_` 前缀 Bean 或 `_Api`。
- 让关系字段、`ICrudApi<I,O>`、`ICrudTreeApi<O>` 的类型参数统一引用公共非下划线类型。
- 保持 `xmeta` 驱动、`postcompile` 触发、`no-api` 语义与现有运行时发布边界不变。

## Non-Goals

- 不修改 `CrudBizModel`、`ICrudBiz` 或任何服务端运行时桥接逻辑。
- 不改变 `OutputBean` 关系字段是否使用 `Map` 或 `*OutputBean` 的既有裁定，除非单层收敛本身要求同步改名。
- 不扩展到 `api-web`、GraphQL schema 生成或前端页面生成。

## Scope

### In Scope

- `nop-kernel/nop-codegen` 下 CRUD API Bean / Api 模板的单层化调整
- 已生成 `*-api` 模块的引用路径与类型名同步
- `docs-for-ai/02-core-guides/api-model-and-codegen.md` 与相关 design/log 同步
- 至少一个已接入模块（优先 `nop-auth-api`）的重新生成与编译验证

### Out Of Scope

- 新增可定制 hook 或保留兼容层
- 全仓所有 `*-api` 模块一次性人工审计业务语义
- 超出 CRUD typed API codegen 边界的命名/分层重构

## Execution Plan

### Phase 1 - 契约与模板收敛

Status: completed
Targets: `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/crud-api/`, `nop-kernel/nop-api-core/`

- Item Types: `Fix`, `Decision`

- [x] 裁定 CRUD typed API 的单层公开契约文件布局：Bean 直接生成到 `beans/`，Api 直接生成到 `crud/`
- [x] 调整 CRUD API 模板，移除 `_gen._*Bean -> *Bean extends _*Bean` 双层结构
- [x] 调整 CRUD API 模板，移除 `_{Entity}Api -> {Entity}Api extends _{Entity}Api` 双层结构
- [x] 保证关系字段、`ICrudApi<I,O>`、`ICrudTreeApi<O>` 等所有引用点统一指向非下划线公共类型
- [x] 明确强制覆盖策略（如 `FORCE_OVERRIDE` 头）在单层契约下的使用方式，并避免生成链留下旧的 `_` 契约漂移

Exit Criteria:

- [x] CRUD typed API 模板不再生成或引用 `_gen._*InputBean`、`_gen._*OutputBean`、`_{Entity}Api`
- [x] 模板产出的所有关系字段类型与接口泛型均使用非下划线公共类型
- [x] `xmeta` 驱动、`postcompile` 触发、`no-api` 控制、`ICrudTreeApi` 树形继承判断保持不变
- [x] 若本 Phase 改变了 owner-facing contract，相关 `ai-dev/design/` / `docs-for-ai/` 更新要求已在后续 Phase 明确承接
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 生成验证与回归收口

Status: completed
Targets: `nop-auth/nop-auth-meta/`, `nop-auth/nop-auth-api/`, related generated `*-api` outputs

- Item Types: `Fix`, `Proof`

- [x] 重新执行至少一个已接入模块的 `postcompile` 生成，验证旧双层文件被正确替换或清理
- [x] 确认生成后的 Bean 关系字段不再引用 `_` 前缀类型
- [x] 确认生成后的 CRUD 接口只暴露单层公共 `Api` / `InputBean` / `OutputBean`
- [x] 运行受影响模块编译与测试，证明单层契约没有引入编译回归

Exit Criteria:

- [x] `nop-auth-api`（或等价验证模块）生成结果中不存在 plan-owned 的 `_gen._*Bean` / `_{Entity}Api` 对外契约引用残留
- [x] 至少一个代表性实体的 to-one / to-many 关系字段都已验证引用公共非下划线类型
- [x] `./mvnw compile -pl nop-auth/nop-auth-api -am -T 1C` 通过
- [x] `./mvnw test -pl nop-auth/nop-auth-api -am -T 1C` 通过
- [x] **接线验证**：`*-meta/postcompile/gen-crud-api.xgen -> crud-api templates -> generated *-api outputs` 链路已验证确实落到单层契约，而非残留旧双层生成
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 文档与闭环同步

Status: completed
Targets: `docs-for-ai/02-core-guides/api-model-and-codegen.md`, `ai-dev/design/crud/crud-api-codegen-design.md`, `ai-dev/logs/`, optional successor notes

- Item Types: `Fix`, `Proof`

- [x] 更新 owner doc，明确 CRUD typed API Bean / Api 为单层公共契约，不再保留 `_` 前缀对外类型
- [x] 更新 design，记录该单层契约决策与拒绝双层保留层的原因
- [x] 如路由或源码锚点发生变化，更新 `docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md`
- [x] 若修改 `docs-for-ai/` 或 `ai-dev/`，运行文档链接检查并收口

Exit Criteria:

- [x] `docs-for-ai/02-core-guides/api-model-and-codegen.md` 已反映单层 Bean / Api 契约
- [x] `ai-dev/design/crud/crud-api-codegen-design.md` 已记录当前最终裁定，而非保留旧双层叙述
- [x] 如涉及 docs 路由或 anchor 漂移：`docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md` 已同步；否则明确 `No routing/anchor update required`
- [x] 若修改了 `docs-for-ai/` 或 `ai-dev/`：`node ai-dev/tools/check-doc-links.mjs --strict` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] CRUD typed API 的 plan-owned 对外契约中不再暴露 `_` 前缀 Bean / Api 类型
- [x] 关系字段类型、`ICrudApi` 泛型参数和树形接口泛型参数全部与单层公共类型一致
- [x] 受影响模块生成、编译、测试通过
- [x] 受影响 owner docs / design / logs 已同步
- [x] 不存在被降级为 follow-up 的 in-scope live contract drift
- [x] 独立子 agent closure audit 已完成并记录证据

## Deferred But Adjudicated

### OutputBean 关系字段从 `Map` 切换到 `*OutputBean`

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划先只收敛单层契约与命名暴露面，不重新裁定 OutputBean 关系表达策略
- Successor Required: no

### 服务端运行时桥接随单层契约同步重构

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前问题是生成契约层的双层类型噪音，不是运行时桥接缺陷
- Successor Required: no

## Non-Blocking Follow-ups

- 扩大到其他已接入 `*-meta -> *-api` 模块的批量再生成与审计
- 评估是否需要对历史残留生成文件增加自动清理策略

## Closure

Status Note: Plan 105 is complete. CRUD typed API Bean / Api contracts were collapsed to a single public layer, `nop-auth-api` regeneration removed old `_gen/_*Bean` and `_{Entity}Api` outputs, relationship field and CRUD generic types now point to public non-underscore Beans, and compile/test/doc checks passed.

Closure Audit Evidence:

- Reviewer / Agent: independent general subagent
- Audit Session: `ses_16df667eeffeDQ1rIqZnyk1pxz`
- Evidence:
  - Overall Verdict PASS: plan-owned public contract in `nop-auth-api` no longer exposes `_`-prefixed CRUD Bean / Api types.
  - Phase 1 PASS: `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/crud-api/{apiPackagePath}/beans/{!entityModel.notGenCode}{entityModel.shortName}InputBean.java.xgen` and `...OutputBean.java.xgen` now generate single-layer public Beans; `.../crud/{!entityModel.notGenCode}{entityModel.shortName}Api.java.xgen` now generates the single-layer public CRUD interface; old `_gen/_*Bean` and `_{Entity}Api` templates were reduced to empty outputs.
  - Phase 2 PASS: `./mvnw generate-test-resources -pl nop-auth/nop-auth-meta -am -T 1C` removed stale underscore outputs via `nop.tpl.remove-empty-resource`; `nop-auth/nop-auth-api/src/main/java/io/nop/auth/api/beans/` and `.../crud/` now contain only public non-underscore contract files.
  - Contract Proof PASS: `nop-auth/nop-auth-api/src/main/java/io/nop/auth/api/beans/NopAuthUserInputBean.java:458-499` uses `List<NopAuthGroupInputBean>` for `relatedGroupList`; `nop-auth/nop-auth-api/src/main/java/io/nop/auth/api/crud/NopAuthGroupApi.java:13-14` and `NopAuthDeptApi.java:13-14` use public `NopAuth*InputBean` / `NopAuth*OutputBean` generics.
  - Verification PASS: `./mvnw compile -pl nop-auth/nop-auth-api -am -T 1C` and `./mvnw test -pl nop-auth/nop-auth-api -am -T 1C` both passed.
  - Phase 3 PASS: `docs-for-ai/02-core-guides/api-model-and-codegen.md`, `ai-dev/design/crud/crud-api-codegen-design.md`, and `ai-dev/logs/2026/06-04.md` were updated to describe the single-layer public contract.
  - Doc Check PASS: `node ai-dev/tools/check-doc-links.mjs --strict` returned `0 errors` with only pre-existing historical warnings.
