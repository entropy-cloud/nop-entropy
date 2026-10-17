# 2461 实体级 no-service 标签：meta 控制 GraphQL 服务方法发布

> Plan Status: completed
> Last Reviewed: 2026-10-17
> Source: 用户需求（ORM/meta 标签必须能控制服务方法是否发布）；调研见 `ai-dev/logs/2026/10-17.md`
> Related: `docs-for-ai/02-core-guides/model-first-development.md`、`docs-for-ai/02-core-guides/api-model-and-codegen.md`
> Draft Review: R1 agent `ses_eb6ef1c31ffeYuds2IwgehZ7Yn`（explore，imagining-analysis）；据此改为「xmeta 通道 + BizObjectImpl 读 objMeta」方案并修订 B1-B5/B7。

## Purpose

新增实体级 tag `no-service`：在 ORM 实体 `tagSet` 中标记后，该实体生成的 `_*.xmeta` 携带该 tag，运行时其 BizObject 的 GraphQL/RPC/gRPC 服务方法（`XXX__findPage`/`get`/`save` 等）不再对外发布；而页面生成、类型字段、typed CRUD API 的既有开关（`no-web` / `not-pub` / `no-api`）语义不变。使"是否发布服务方法"由 meta 标签显式控制。

> 实现通道：ORM 实体 tagSet → `_*.xmeta` root tagSet → `BizObjectImpl` 读 `objMeta`。不改 xbiz 生成模板，不新增构建期标记。

## Current Baseline

- ORM 实体 `tagSet` 会进入生成的 `_*.xmeta`：`templates/meta/.../{deltaDir}/_{shortName}.xmeta.xgen` 根节点 `tagSet="${_.join(entityModel.tagSet,',')}"`，因此实体级任意 tag（`no-web`、`no-tenant`…）都会出现在 xmeta tagSet。**这是本计划采用的生效通道。**
- 运行时服务方法发布收口在 `nop-service-framework/nop-biz/.../BizObjectImpl.java`：`getOperationDefinition(opType,name)`、`getOperationDefinitions(opType)` 原本按 `bizModel.containsTag("not-pub")` 返回 null/空；**无参** `getOperationDefinitions()` 原本**未**按 `not-pub` 收口。`BizObjectBuilder` 在 `not-pub` 时清空类型字段。
- GraphQL 查询、`/r/` RPC、introspection 都经 `BizObjectManager`（`implements IGraphQLSchemaLoader`）→ `IBizObject.getOperationDefinition*`；`BizObjectManager.getBizOperationDefinitions()` 走**无参**入口，被 gRPC 与 maker-checker 消费。
- `BizObjectImpl` 自身持有 `objMeta`（`getObjMeta()`），因此可直接在 `getOperationDefinition*` 里读取 `no-service`，无需改 `BizObjectBuilder`。
- 当前：实体级 `not-pub` = 页面+字段+方法全不发布；`no-web` = 仅页面/菜单；`no-api` = 仅 typed API；**不存在**只控制服务方法发布的独立 tag。

## Goals

- 在 ORM 实体 `tagSet`（或 xmeta root tagSet）标记 `no-service` 后，其 GraphQL query/mutation/subscription 与 `/r/` RPC、gRPC 操作定义对所有外部入口（GraphQL 查询、`/r/` RPC、introspection、gRPC schema）不可见。
- 不改变 `no-web` / `not-pub` / `no-api` 的既有语义（**含 `not-pub` 在无参 `getOperationDefinitions()` 上当前未收口的既有行为也不改**）。
- 对象类型字段保持可见（除非另行标记 `not-pub`）；内部可编程调用（`getAction`/`method_invoke`）不受影响。
- 有聚焦单元测试与 owner-doc 更新。

## Non-Goals

- 不改 `not-pub` / `no-web` / `no-api` 语义与实现。
- 不改 typed CRUD API（`no-api` 领域）、页面生成（`no-web` 领域）。
- 不把 `no-service` 应用到任何具体实体（如 EmailCode/LoginAttempt）——是否应用属独立决策。
- 不引入方法级/action 级发布开关（仅实体级）。

## Scope

### In Scope

- `OrmModelConstants.TAG_NO_SERVICE` 常量。
- `BizObjectImpl` 读取 `objMeta` 的 `no-service` 并收口三个操作定义入口。
- 聚焦测试（nop-biz，含 `BizObjectManager` 接线断言）。
- `docs-for-ai/02-core-guides/api-model-and-codegen.md` 与 `model-first-development.md` 的实体级 tag 说明。

### Out Of Scope

- 具体实体的标记与页面收口；xbiz 生成模板改动。

## Execution Plan

### Phase 1 - no-service 常量 + 运行时收口 + 测试

Status: completed
Targets: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/OrmModelConstants.java`；`nop-service-framework/nop-biz/src/main/java/io/nop/biz/impl/BizObjectImpl.java`；`nop-service-framework/nop-biz/src/test/`

- Item Types: `Feature`

- [x] `OrmModelConstants` 新增 `String TAG_NO_SERVICE = "no-service";`。
- [x] `BizObjectImpl` 收口：
  - `getOperationDefinition(opType,name)` 与 `getOperationDefinitions(opType)`：发布禁用条件 = `not-pub`（`bizModel`，既有）或 `no-service`（`getObjMeta()`，新增）；
  - 无参 `getOperationDefinitions()`：**只**对 `no-service` 返回空 Map（不改 `not-pub` 既有行为）；
  - `getAction`/`method_invoke` 不受影响。
- [x] 无 `no-service` 时行为完全不变（既有实体无该 tag）。
- [x] 新增聚焦测试 `TestNoServicePublish`（no-service 隐藏 / not-pub 对照 / 无 tag 对照 / 无参入口接线）。

Exit Criteria:

- [x] `nop-persistence/nop-orm-model` 与 `nop-service-framework/nop-biz` 编译通过，常量可被引用。
- [x] `no-service` 实体经 `BizObjectManager.getOperationDefinition(query/mutation,"NoServiceObject__get/save")` 返回 null；`getBizOperationDefinitions("NoServiceObject")` 为空。
- [x] `not-pub` 既有行为不变（`getOperationDefinitions()` 无参入口仍返回操作）；无 tag 实体不受影响（`MyObject` 操作仍可见）。
- [x] 判别性：测试断言操作"存在但被收口"（无收口时 `getOperationDefinition` 会返回非 null、`getBizOperationDefinitions` 非空，断言必红）。
- [x] 无静默跳过：`no-service` 分支显式返回 null/空集合，不吞异常。
- [x] `ai-dev/logs/` 对应日期条目更新。

### Phase 2 - owner-doc 与边界说明

Status: completed
Targets: `docs-for-ai/02-core-guides/api-model-and-codegen.md`（实体级发布控制）；`docs-for-ai/02-core-guides/model-first-development.md`（实体级 tag 说明）

- Item Types: `Feature`

- [x] `api-model-and-codegen.md` 实体级发布控制处新增 `no-service` 说明与边界。
- [x] `model-first-development.md` 新增「实体级发布标签（no-web / no-api / no-service）」小节，含生效通道与 xmeta-only 边界。
- [x] 文档明确 `no-service` 对 gRPC 的影响（无参入口收口）。

Exit Criteria:

- [x] 文档与 live 行为一致（tag 名、作用层、生效通道、影响面）。
- [x] 明确写出 `no-service` 对 gRPC schema 的影响与生效通道边界。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目更新。

## Closure Gates

- [x] 所有 in-scope confirmed live defect 已修复（本计划为新增能力，无既有 live defect）
- [x] 行为/契约结果已达成：`no-service` 实体全部操作不可从外部发布入口解析（GraphQL/`/r/`/introspection/gRPC）
- [x] 必要 focused verification 已完成（`./mvnw test -pl nop-service-framework/nop-biz` 127 通过）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [x] 受影响的 owner docs 已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：`no-service` 由 `BizObjectImpl.isNoService()` 读 `objMeta` 并在 `BizObjectManager` → `IBizObject` 运行时链生效（测试经真实入口断言），非空方法体/静默跳过
- [x] `./mvnw compile`（nop-orm-model + nop-biz）
- [x] `./mvnw test`（nop-biz 全量 127 通过）
- [x] checkstyle / 代码规范检查通过（随模块 test 生命周期）

## Deferred But Adjudicated

### 是否将 no-service 应用到 EmailCode / LoginAttempt

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划只提供机制；具体实体的标记涉及权限/产品决策，不影响 `no-service` 机制本身成立。
- Successor Required: `no`
- Successor Path: 另立或并入安全收口计划

## Non-Blocking Follow-ups

- `IBizObject.getOperations()`（raw，未收口）被 `GraphQLToolCallbackProvider`（MCP）消费，当前限定 `BIZ_OBJ_AI_TOOL`，`no-service` 暂不外泄；若 MCP 泛化则需另行收口。
- `not-pub` 与 `no-service` 同时存在时以 `not-pub`（更强）为准；本计划不额外定义优先级逻辑。
- 若日后需要方法级（action 级）发布控制，可另立 successor（本计划仅实体级）。

## Closure

Status Note: 实体级 `no-service` 机制已落地并经独立 closure audit 验证：ORM tagSet → 生成 xmeta root tagSet → `BizObjectImpl.getObjMeta().containsTag("no-service")` 在 GraphQL/`/r/`/introspection/gRPC 三个操作定义入口统一收口，`not-pub`/`no-web`/`no-api` 与内部调用行为不变；聚焦与全量测试通过。
Completed: 2026-10-17

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（explore）
- Audit Session: `ses_eb6e0a3e1ffe0Ns0zj7Ke2ZAFu`
- Evidence:
  - OrmModelConstants.java:76 `TAG_NO_SERVICE="no-service"`（PASS）
  - BizObjectImpl.java:195-240 `getOperationDefinition`/`getOperationDefinitions(opType)` 经 `isServiceNotPublished()`；无参 `getOperationDefinitions()` 仅 `isNoService()`；`getAction`/`method_invoke` 未改（PASS）
  - TestNoServicePublish 经真实入口 `BizObjectManager.getOperationDefinition`/`getBizOperationDefinitions` 断言；判别性由未标记 `MyObject` 同路径返回非 null 证明（PASS）
  - 聚焦 `-Dtest=TestNoServicePublish`：Tests run 4/0/0/0；全量 `./mvnw test -pl nop-service-framework/nop-biz`：127/0/0/0（PASS）
  - `check-doc-links.mjs --strict` exit 0（PASS；2 处既有 unrelated warning）
  - Live 通道：`templates/meta/.../_{shortName}.xmeta.xgen:6` tagSet join；`BizObjectManager:247/351` 委派 IBizObject（PASS）
  - Anti-Hollow：调用链运行时连通，无空方法/静默跳过（PASS）
  - 审计唯一 Major 为「daily log 条目缺失」，已补 `ai-dev/logs/2026/10-17.md` 顶部 `no-service` 条目后消除
- Follow-up: 无 plan-owned 剩余工作。

Follow-up:

- 见 Non-Blocking Follow-ups（MCP raw 入口、优先级、方法级开关均为 non-blocking）。
