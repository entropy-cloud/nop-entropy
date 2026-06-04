# 104 CRUD API 代码生成实现

> Plan Status: completed
> Last Reviewed: 2026-06-03
> Source: `ai-dev/design/crud/crud-api-codegen-design.md`
> Related: `ai-dev/design/crud/relation-write-mode-design.md`

## Purpose

为 Nop 平台的 CRUD 服务增加强类型 API 接口代码生成能力，让外部系统通过 `*-api/` 模块获得类型安全的 RPC 调用契约。

## Current Baseline

- CRUD 服务通过 `CrudBizModel<T>` 暴露，使用通用 REST adapter（`/r/{bizObj}__{method}`），无强类型 API 接口
- 自定义 API 接口（如 `WorkflowService`）由 `*.api.xml` 经 `/nop/templates/api` 模板生成，有 `ApiRequest/ApiResponse` 包装
- ORM 代码生成链：`model/*.orm.xml` → `{app}-codegen/postcompile/gen-orm.xgen` → `{app}-dao/service/meta/web`
- CRUD API 当前实现基于 `/{moduleId}/model/*.xmeta` 扫描，只对具备 `objMeta.entityName` 的实体 xmeta 生成接口与 Bean
- `postcompile/gen-crud-api.xgen` 通过 `codeGenerator.withTplDir('/nop/templates/crud-api').execute(...)` 触发模板，`@init.xrun` 使用 `DefineLoop` 建立循环上下文并注册 `apiPackageName` / `apiPackagePath`
- `ICrudBiz<T>` 在 `nop-orm` 中，依赖 ORM 层，不适合外部系统引用
- `_chgType`（A/U/D）控制集合项增删改，`_writeMode_{propName}` 控制关系写入模式，`OrmEntityCopier` 只从 Map 读取这些控制字段
- 项目中 DTO 命名主导模式为 `*Bean`（如 `WfStartRequestBean`、`QueryBean`），`*Vo` 零使用，`*Dto` 仅 `nop-code` 模块
- `ExtensibleBean` 提供 `attrs` Map 但不使用 `@JsonAnyGetter`/`@JsonAnySetter`；子类需自行覆盖添加注解。`CrudInputBase` 不继承 `ExtensibleBean`，独立实现 `@JsonAnySetter`/`@JsonAnyGetter`，因为需要将控制字段直接展开到 JSON 根级（而非嵌套在 `attrs` 中）
- 现有 API 模板中保留文件不带 `//__XGEN_FORCE_OVERRIDE__` 头，生成文件带此头
- `nop-api-core` 中已存在 `io.nop.api.core.api.ICrudApi<T>`；本 plan 实际落地为将其演进为 `ICrudApi<I, O>`，并新增 `ICrudTreeApi<O>`，而不是并列新增一个同名接口

## Goals

- 在 `nop-api-core` 中定义 `ICrudApi<I, O>` 和 `ICrudTreeApi<O>` 泛型接口
- 定义 `CrudInputBase` 基类（含 `_chgType` + `@JsonAnySetter`/`@JsonAnyGetter`）
- 在 `*-meta/postcompile` 生成阶段为每个实体 xmeta 生成 InputBean、OutputBean 和具体 API 接口
- 以 `nop-auth` 模块验证生成结果的正确性

## Non-Goals

- 不修改 `CrudBizModel<T>` 的内部实现
- 不修改 `ICrudBiz<T>` 的接口定义
- 不生成桥接代码（框架通用机制处理）
- 不生成 GraphQL Schema
- 不处理 `@BizLoader` 计算字段在 OutputBean 中的生成策略（开放问题）

## Scope

### In Scope

- `nop-api-core` 中新增 `ICrudApi<I, O>`、`ICrudTreeApi<O>`、`CrudInputBase` 类
- `nop-codegen` 中新增 CRUD API 代码生成模板
- `*-meta` 生成链集成新模板
- `docs-for-ai/02-core-guides/api-model-and-codegen.md` 文档更新

### Out Of Scope

- 框架桥接层适配（`InputBean → Map`、`Entity → OutputBean` 的运行时转换）
- 前端页面生成（`api-web` 模板）
- OutputBean 关系字段使用 `*OutputBean` 类型（当前为 `Map<String, Object>`）
- `@BizLoader` 计算字段处理
- `entityModel.components`（`OrmFileComponent` 等）和 `OrmMappingTableMeta`（多对多映射表）的生成——第一版仅处理 columns 和 relations

## Execution Plan

### Phase 1 - nop-api-core 接口定义

Status: completed
Targets: `nop-kernel/nop-api-core/`

- Item Types: `Decision`, `Proof`

- [x] 将现有 `ICrudApi<T>` 演进为 `ICrudApi<I, O>`：保留现有 `ICancelToken`/`FieldSelectionBean` 风格与高级 CRUD 方法，核心方法集（get/findPage/findList/findFirst/findCount/save/update/delete/saveOrUpdate/batchDelete/batchGet）的方法名与 `CrudBizModel` 对应，且 `save/update/saveOrUpdate` 改为强类型 `I` 输入，输出统一改为 `O`
- [x] 新增 `ICrudTreeApi<O>` 接口：定义 findRoots/findTreeEntityPage/findTreeEntityList 方法
- [x] 新增 `CrudInputBase` 抽象类：包含 `_chgType` 字段（String）、`_extAttrs` Map（`Map<String, Object>`）、`@JsonAnyGetter`/`@JsonAnySetter` 注解。不继承 `ExtensibleBean`（理由：`ExtensibleBean` 的 attrs 不用 Jackson Any 机制，无法将控制字段展开到 JSON 根级）

Exit Criteria:

- [x] `ICrudApi`、`ICrudTreeApi`、`CrudInputBase` 编译通过，仅依赖 `nop-api-core` 内部类
- [x] `ICrudApi` 的方法集是 `CrudBizModel` 公开方法的一个子集（get/findPage/findList/findFirst/findCount/save/update/delete/saveOrUpdate/batchDelete/batchGet），方法名完全匹配
- [x] `CrudInputBase` 的 `@JsonAnySetter` 能吸收未声明的 JSON 属性（如 `_chgType_items`、`_writeMode_dept`）到 `_extAttrs`，`@JsonAnyGetter` 能将其展开回 JSON 根级
- [x] 单元测试验证 `CrudInputBase` 的 JSON 序列化/反序列化行为：含 `_chgType` 时正确保留为字段，含 `_chgType_items` 等动态字段时正确吸收到 `_extAttrs`
- [x] No owner-doc update required（此 Phase 仅新增类，不改变已有行为）

### Phase 2 - codegen 模板开发

Status: completed
Targets: `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/crud-api/`

- Item Types: `Decision`, `Proof`

**模板位置决策**：新建 `/nop/templates/crud-api/` 目录（不扩展 `/nop/templates/meta/`）。理由：不干扰现有 meta 生成逻辑；通过独立的 `@init.xrun` 初始化变量；在 `gen-meta.xgen` 中新增 `renderModel` 调用。

**数据源**：实体 `xmeta`（不是 ORM 直扫）。`@init.xrun` 通过 `DefineLoop` 扫描 `/{moduleId}/model/*.xmeta`，过滤 `_*.xmeta` 与无 `objMeta.entityName` 的非实体 xmeta，并注册 `apiPackageName` / `apiPackagePath` 供模板使用。

**字段过滤逻辑**：复用 `meta-gen.xlib` 的函数（通过 `c:import from="/nop/codegen/xlib/meta-gen.xlib"`）：
- InputBean 字段：`meta-gen:IsColInsertable(col)` 返回 true **或** `meta-gen:IsColUpdatable(col)` 返回 true 的列
- OutputBean 字段：列的 `tagSet` 不包含 `'not-pub'` 的列（等价于 meta 模板中的 `published` 计算）
- 关系字段：`insertable`/`updatable` 来自 `rel.tagSet?.contains('insertable')` / `rel.tagSet?.contains('updatable')`，`published` 来自 `rel.tagSet?.contains('pub')`

- [x] 编写 `@init.xrun`：使用 `DefineLoop` 扫描 `/{moduleId}/model/*.xmeta`，过滤出具备 `objMeta.entityName` 的实体 xmeta，并注册 `apiPackageName` / `apiPackagePath` 全局变量；`entityModel` 循环变量由 `DefineLoop` 自动注册
- [x] 编写 InputBean 生成模板 `_{EntityName}InputBean.java.xgen`：以 `//__XGEN_FORCE_OVERRIDE__` 开头，继承 `CrudInputBase`，遍历 xmeta props 中 `insertable || updatable` 的字段并处理关系字段（实际落地为生成层引用 `_gen` 包中的关联 InputBean 基类，避免生成层反向依赖保留层）
- [x] 编写 OutputBean 生成模板 `_{EntityName}OutputBean.java.xgen`：以 `//__XGEN_FORCE_OVERRIDE__` 开头，遍历 xmeta props 中 `published != false` 的字段生成输出（关系字段类型为 `Map<String, Object>` / `List<Map<String, Object>>`）
- [x] 编写 API 接口生成模板 `_{EntityName}Api.java.xgen`（FORCE_OVERRIDE）和 `{EntityName}Api.java.xgen`（保留文件）：`_{EntityName}Api extends ICrudApi<I, O>`，`{EntityName}Api extends _{EntityName}Api`。树形检测：`entityModel.objMeta.tree?.parentProp` 存在时额外 `extends ICrudTreeApi<O>`
- [x] 编写保留层 InputBean/OutputBean 模板（不带 FORCE_OVERRIDE 头，仅首次生成）：`{EntityName}InputBean extends _{EntityName}InputBean`，`{EntityName}OutputBean extends _{EntityName}OutputBean`
- [x] `@BizModel` 注解决策：参考现有 API 模板（`{serviceModel.name}.java.xgen` L37），`@BizModel` 放在保留层 `{EntityName}Api.java` 上。`_{EntityName}Api.java` 上**不放** `@BizModel`（避免 BizModel 注册冲突）。设计文档中 `_{EntityName}Api` 上的 `@BizModel` 是示意图，实际以本条为准

Exit Criteria:

- [x] 模板文件存在于 `/nop/templates/crud-api/` 下，包含 `@init.xrun` 和所有 `.xgen` 模板
- [x] 模板中引用的变量名（`entityModel`、`moduleId`、`apiPackageName`、`apiPackagePath` 等）在实际 `DefineLoop` 初始化上下文中存在
- [x] `rel.refEntityModel.shortName` 在模板中用于拼接关联 InputBean 类型名
- [x] FORCE_OVERRIDE 头仅用于 `_` 前缀生成文件，保留层模板不带此头
- [x] **想象性验证**：手动推演 `NopAuthUser.xmeta` 的 props，确认 `password` 在 InputBean 中可生成、在 OutputBean 中因 `published=false` 被排除；确认 `NopAuthUser` 无 tree 配置因此不继承 `ICrudTreeApi`
- [x] No owner-doc update required（此 Phase 仅新增模板）

### Phase 3 - *-meta 生成链集成

Status: completed
Targets: `nop-auth/nop-auth-meta/postcompile/`、`nop-kernel/nop-codegen/`

- Item Types: `Fix`, `Proof`

- [x] 在 `nop-auth-meta/postcompile/` 中新增 `gen-crud-api.xgen`，使用 `codeGenerator.withTplDir('/nop/templates/crud-api').withTargetDir("../nop-auth-api/src/main/java/").execute("/", { moduleId: "nop/auth", apiPackageName: "io.nop.auth.api" }, $scope)` 触发 CRUD API 模板
- [x] 执行 `./mvnw generate-test-resources -pl nop-auth/nop-auth-meta -am` 触发 `postcompile` 生成
- [x] 执行 `./mvnw clean install -pl nop-auth/nop-auth-api -am` 验证生成的 Java 文件编译通过
- [x] 确认生成的 `NopAuthUserInputBean extends CrudInputBase`，包含 `userName`/`nickName`/`password` 等 `insertable || updatable` 字段
- [x] 确认生成的 `NopAuthUserOutputBean` 排除了 `password`、`salt` 等 `published=false` 字段
- [x] 确认生成的继承链：`NopAuthUserApi extends _NopAuthUserApi`，且 `_NopAuthUserApi extends ICrudApi<_NopAuthUserInputBean, _NopAuthUserOutputBean>`，`NopAuthUserApi` 上带 `@BizModel("NopAuthUser")` 注解
- [x] 确认保留层文件（`NopAuthUserInputBean.java`、`NopAuthUserOutputBean.java`、`NopAuthUserApi.java`）仅首次生成，再次执行 `mvnw install` 不被覆盖

Exit Criteria:

- [x] `./mvnw generate-test-resources -pl nop-auth/nop-auth-meta -am` 成功触发生成，`nop-auth-api/src/main/java/` 下出现生成的 Java 文件
- [x] `./mvnw clean install -pl nop-auth -am` 全模块构建通过（含编译 + 测试）
- [x] `NopAuthUserInputBean` 中 `password` 字段存在（`IsColInsertable` 对普通列返回 true）但 `NopAuthUserOutputBean` 中不存在（`password` 有 `not-pub` tag）
- [x] 保留层文件在二次构建后内容不变
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 文档更新与验证

Status: completed
Targets: `docs-for-ai/02-core-guides/api-model-and-codegen.md`、`docs-for-ai/INDEX.md`

- Item Types: `Fix`, `Follow-up`

- [x] 更新 `docs-for-ai/02-core-guides/api-model-and-codegen.md`，增加 CRUD API 代码生成章节（生成物清单、`ICrudApi`/`ICrudTreeApi`/`CrudInputBase` 说明、命名约定、覆盖策略、与 ORM 生成的区别）
- [x] 更新 `docs-for-ai/03-runbooks/debug-codegen-and-generated-files.md` 生成链路表，增加 `*-meta → *-api` 的 CRUD API 生成环节
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict` 确认无 errors（当前仅剩历史 warnings）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] `api-model-and-codegen.md` 包含 CRUD API 生成物清单（InputBean/OutputBean/Api 接口）、`ICrudApi`/`ICrudTreeApi` 方法列表、`CrudInputBase` 设计
- [x] `debug-codegen-and-generated-files.md` 的生成链路表包含 `*-meta → *-api` 环节
- [x] doc link checker 通过（0 errors，历史 warnings 未扩大）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] `nop-auth` 模块的 CRUD API 生成结果正确且编译通过
- [x] `ICrudApi`、`ICrudTreeApi`、`CrudInputBase` 仅依赖 `nop-api-core`
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响的 owner docs 已同步
- [x] `./mvnw compile -pl nop-auth -am` 通过
- [x] `./mvnw test -pl nop-auth -am` 通过
- [x] checkstyle / 代码规范检查通过
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### OutputBean 关系字段使用 `*OutputBean` 类型

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前 `Map<String, Object>` 可工作，`*OutputBean` 类型引用需验证编译期循环依赖问题
- Successor Required: yes
- Successor Path: 待定

### `@BizLoader` 计算字段处理

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前 `published != false` 规则自动包含 virtual 字段，行为可接受
- Successor Required: no

### 框架桥接层适配

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 生成的 API 接口是编译期契约，运行时桥接由框架通用机制处理，属于独立关注点
- Successor Required: yes
- Successor Path: 待定

### entityModel.components 和 OrmMappingTableMeta

- Classification: `optimization candidate`
- Why Not Blocking Closure: 第一版仅处理 columns 和 relations，components（文件/JSON 组件）和 mapping table（多对多映射）的生成可后续增量添加
- Successor Required: no

## Non-Blocking Follow-ups

- 前端 `api-web` 模板为 CRUD API 生成页面
- GraphQL Schema 自动生成
- 其他模块（`nop-wf`、`nop-job`、`nop-task`）的 CRUD API 生成接入

## Closure

Status Note: Plan 104 已完成。`nop-api-core` 中的现有 `ICrudApi` 已演进为双泛型接口并拆分出 `ICrudTreeApi`，`CrudInputBase` 与 CRUD API 模板已接入 `nop-auth-meta` 生成链，`nop-auth-api` 成功生成并通过模块构建、测试、文档与独立 closure audit。实际落地偏差已同步到 design/plan：未引入 `ApiResponse`/JAX-RS 风格，而是复用现有 `ICancelToken`/`FieldSelectionBean` 风格并只对强类型输入输出做最小正确改动。

Closure Audit Evidence:

- Reviewer / Agent: independent general subagent
- Audit Session: `ses_1722c103fffe1aL7W7SAPRJskL`
- Evidence:
  - Phase 1 PASS: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/api/ICrudApi.java` 已演进为 `ICrudApi<I,O>`；`ICrudTreeApi.java`、`CrudInputBase.java` 存在；`nop-kernel/nop-api-core/src/test/java/io/nop/api/core/api/TestCrudInputBase.java` 覆盖 `_chgType`、动态字段吸收与 JSON/map round-trip。
  - Phase 2 PASS: `/nop/templates/crud-api/` 模板集存在，`@init.xrun` 正确注册 `apiPackageName/apiPackagePath`，模板使用 `entityModel`、`meta-gen.xlib`、`rel.refEntityModel.shortName` 与 parent-tag tree detection。
  - Phase 3 PASS: `nop-auth/nop-auth-meta/postcompile/gen-crud-api.xgen` 正确调用 `/nop/templates/crud-api`；生成的 `_NopAuthUserApi.java`、`_NopAuthDeptApi.java`、`_NopAuthUserInputBean.java`、`_NopAuthUserOutputBean.java` 满足预期，且 `NopAuthDept` 额外继承 `ICrudTreeApi`。
  - Phase 4 PASS: `docs-for-ai/02-core-guides/api-model-and-codegen.md` 与 `docs-for-ai/03-runbooks/debug-codegen-and-generated-files.md` 已补 CRUD API 生成链说明。
  - Anti-Hollow PASS: live chain 已验证为 `crud-api templates -> nop-auth-meta/postcompile/gen-crud-api.xgen -> generated nop-auth-api outputs`，不是空壳实现。
  - Drift Check PASS: plan/design/docs/log 已诚实记录实现偏差：复用并演进既有 `ICrudApi<T>` 为 `ICrudApi<I,O>`，并保留 raw return + `FieldSelectionBean`/`ICancelToken` 风格。
  - Verification PASS: `./mvnw clean install -pl nop-auth -am -DskipTests`、`./mvnw test -pl nop-auth -am` 已在执行会话中通过；`./mvnw compile -pl nop-auth -am` 已在收口复核中重新通过。
  - Verification PASS (final closure adjudication): strict doc-link check 已达成 0 errors；`./mvnw compile -pl nop-auth -am` 已重新通过；针对本次手改 Java 文件的定向 checkstyle 命令返回 `BUILD SUCCESS` 且 0 violations。仓库其余 repo-wide checkstyle 违规属于既有历史问题，不构成本 plan 的 plan-owned blocker。
  - Independent closure re-audit PASS: independent subagent 最终结论为 PASS，认定所有 closure gates 已满足，且可在写入本段证据后将 Plan 104 标记为 `completed`。

Follow-up:

- no remaining plan-owned work（所有 in-scope 项已完成或已移入 Deferred）
