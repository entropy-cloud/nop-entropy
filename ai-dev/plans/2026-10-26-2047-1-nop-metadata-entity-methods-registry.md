# 2026-10-26-2047-1 nop-metadata 实体方法注册表 + 计算属性/别名 + 执行器 + 标准 CRUD 标签（M1-M4）

> Plan Status: active
> Last Reviewed: 2026-10-27
> Source: `ai-dev/design/nop-metadata/14-entity-methods-design.md`（设计决策与安全契约权威）；`ai-dev/design/nop-metadata/nop-metadata-roadmap.md` M1-M4
> Related: roadmap Work Item Status M1-M4；`docs-for-ai/03-modules/nop-metadata.md`（owner doc）

> **Protected Area 提示**：本计划修改 `nop-metadata/model/nop-metadata.orm.xml`（ORM 模型结构），属 plan-first 区域——实施启动前需 plan audit 通过。

> **Draft Review 记录**（2026-10-26，独立子 agent agent_51da0dae，2 轮）：第 1 轮 1 Blocker（computed 求值无机制）+ 3 Major（错误码归属/dict 归属/static-mutation 语义矛盾）+ 10 Minor，全部修复；第 2 轮复核 M1/M2/M3 成立，指出 `ExpressionRuntime` 类名不实，已改为核实过的真实 XLang API 面（`XLang.newCompileTool()` + `EvalScopeImpl` + `ExprFeatures.SIMPLE`）。剩余仅 Minor，按 lessons/19 预算（≤2 轮）停止审查。

## Purpose

把设计文档 `14-entity-methods-design.md` 的 4 组能力落地到 nop-metadata 并配齐自动化测试：

1. 方法注册表（`NopMetaEntityMethod`/`NopMetaMethodArg`）建模与守卫；
2. 计算属性/别名字段（`NopMetaEntityField.fieldKind/computeExpr/mapToProp`）与查询输出投影；
3. 方法执行器（`invokeMethod` 泛化入口 + SQL 沙箱 + entityKind 取数分派 + 行级 roles）；
4. 标准 CRUD 标签（`std-crud`/`std-crud-ro` 保留标签 + 目录合成只读入口）。

## Current Baseline

- `NopMetaEntity`（`nop_meta_entity`）已建模于 `nop-metadata/model/nop-metadata.orm.xml:568`，含 `tagSet` 列（:589）；xmeta 侧 to-many 关联 19 个（`_NopMetaEntity.xmeta:171-314`），无 methods 关联。
- `NopMetaEntityField` 全部为存储字段语义列，无 `fieldKind`/`computeExpr`/`mapToProp`（`_NopMetaEntityField.xmeta:26-164`）。
- 联邦查询入口 `queryData`/`queryJoinData`/`queryAggregation` 已实现（`NopMetaEntityBizModel extends CrudBizModel<NopMetaEntity> implements INopMetaEntityBiz`，`NopMetaEntityBizModel.java:68`）；`limit` 契约与 `items: List<Map>` 语义见 owner doc AR-09/P2-24。
- SQL 沙箱先例已存在：`MetaQualityRuleExecutor` custom_sql blocklist（owner doc F9/G8-13-01）、`ExpressionMeasureValidator` save-time+query-time 同源黑名单（owner doc P1-1）。
- 平台机制：标准 CRUD 由 `CrudBizModel` 自动暴露（GraphQL `BizObjName__actionName`）；xmeta prop 支持 `<getter>`/`mapToProp`/`<arg>`（`obj-schema.xdef:34,145-177`）——本计划只消费其形态，不改平台元模型。
- `I*Biz` 接口驻 `nop-metadata-dao` `io.nop.metadata.biz`，`TestNopMetaBizInterfaceCompleteness` 程序化守卫要求接口方法集与 BizModel 一致。
- `NopMetadataErrors.java` 聚合 + 10 个子域 Errors 分组文件；错误码命名 `nop.err.metadata.*`；占位符/ARG_* 一致性有既有不变式门禁（`run-nop-metadata-invariants.sh` guard 6）。
- 38 个实体 GraphQL CRUD 自动暴露；`meta/entity-kind` dict 取值 PHYSICAL/SQL_VIEW/EXTERNAL。

## Goals

- 目录可登记带参查询方法（静态入口/实体函数两种绑定），参数、实现、行级角色一体化建模，GraphQL 可调用。
- 目录字段可声明计算列与别名，`queryData` 返回行按声明解析，存量行为零变化。
- 实体可经 `tagSet` 保留标签声明标准查询面；SQL_VIEW/EXTERNAL 声明后获得目录合成只读入口（无需自有 bizObj）。
- 方法 SQL 全生命周期受沙箱约束（save-time + query-time 同源校验），错误面不泄漏 SQL 全文。
- 每个能力配齐单元 + AutoTest 端到端测试；owner doc 与 roadmap 同步。

## Non-Goals

- 动态生成 xbiz/bean 注册进平台 BizModelManager（设计 §四 拒绝）。
- `implKind=xpl/java-bean`：不开放任意代码执行面。
- 计算列 SQL 下推（XLang 表达式无下推语义，行上求值为 v1 裁定；如有需求将随表达式语言选择另裁定，设计 §3.2）。
- `sourceKind=imported` 快照导入通道的实现（本计划只建模列与写路径守卫）。
- 方法行级缓存/事务/补偿建模。
- SQL_VIEW/EXTERNAL 的写路径合成（目录只合成只读面）。
- 方法执行支持 JOIN / 多语句 / 存储过程调用。

## Scope

### In Scope

- `nop-metadata/model/nop-metadata.orm.xml`：新增 2 实体 + `NopMetaEntityField` 3 列 + relations/UK。
- `nop-metadata-meta`：新实体 xmeta、`_NopMetaEntity.xmeta`/`_NopMetaEntityField.xmeta` 增列、dict ×5、i18n。
- `nop-metadata-dao`：生成实体、`INopMetaEntityMethodBiz`/`INopMetaMethodArgBiz`、`INopMetaEntityBiz` 增补。
- `nop-metadata-service`：BizModel 守卫、`MetaMethodSqlValidator`、`MetaMethodExecutor`、`NopMetaEntityBizModel` 增 `invokeMethod`/`findEntityPage`/`findEntityList`/`getEntity`、`NopMetadataErrors` 新分组。
- `nop-metadata-api`：`MethodInvocationResultDTO`。
- upgrade DDL：`nop-metadata` 模块 `deploy` 目录下 `sql` 的 `mysql`/`postgresql`/`oracle` 三方言子目录（全新安装脚本 `_create_nop-metadata.sql` 为生成物，随 orm.xml 变更由构建管线重新生成，不手编）。
- 测试：JUnit 单元 + Nop AutoTest 端到端（含 GraphQL 层）。

### Out Of Scope

- 平台模块（nop-biz/nop-xlang）任何变更。
- 前端页面（nop-metadata-web）适配。
- 搜索索引对新实体的接入（沿既有索引实体清单不变）。

## Execution Plan

### Phase 1 - 方法注册表建模与守卫（M1-1..M1-4）

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`、`nop-metadata-meta`、`nop-metadata-dao`、`nop-metadata-service`、`nop-metadata/deploy`

- Item Types: `Feature`

- [x] orm.xml 新增 `NopMetaEntityMethod`（methodKind/sourceKind/implKind/implSource/returnType/roles/disabled/isDelta + 审计五件套 + remark/extConfig）与 `NopMetaMethodArg`（argName/argType/mandatory/defaultValue/displayName + 审计五件套），UK `(metaEntityId, methodName)`、`(methodId, argName)`，`NopMetaEntity` 增加 methods to-many、method 增加 args to-many relation
- [x] xgen 管线生成 DAO 实体，`./mvnw compile -pl nop-metadata/nop-metadata-dao -am` 通过（不手编 `_gen/` 产物）
- [x] xmeta：`NopMetaEntityMethod/_NopMetaEntityMethod.xmeta` + `NopMetaEntityMethod.xmeta`、`NopMetaMethodArg` 同构；`_NopMetaEntity.xmeta` 增加 `methods` prop；新 dict `meta/method-kind`（static-query/static-mutation/loader）、`meta/method-source`（imported/custom）、`meta/method-impl-kind`（sql）+ i18n
- [x] `INopMetaEntityMethodBiz`/`INopMetaMethodArgBiz` 接口（dao 模块）+ `NopMetaEntityMethodBizModel`/`NopMetaMethodArgBizModel` 实现（service 模块）；`TestNopMetaBizInterfaceCompleteness` 覆盖新接口（新增 `testMethodRegistryInterfacesRegistered`——两接口当前无自定义方法，全集守卫 COVERAGE 只登记非空接口，存在性/泛型绑定/实现类关系由新测试钉死）
- [x] save 守卫 + `MethodErrors` 分组：methodName 保留名（平台标准 action 名集合）拒绝、非小驼峰拒绝、`sourceKind=imported` 手写拒绝、argName 重复拒绝；本 Phase 无执行入口，`implSource` 仅做非空校验（SQL 沙箱在 Phase 3 落地并回接 save-time 校验）
- [x] upgrade DDL `upgrade-nop-meta-entity-methods.sql` 落 `nop-metadata` 模块 `deploy` 目录下 `sql` 的 mysql/postgresql/oracle 三方言子目录
- [x] AutoTest `TestNopMetaEntityMethodBizModel`：GraphQL CRUD 端到端 + 守卫拒绝路径断言（错误码逐一验证）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 通过，新增测试显式覆盖：CRUD 正常路径、每个守卫错误码一条拒绝用例（2026-10-27 实测 `./mvnw test -pl nop-metadata-service` 全量 1366 tests 全绿，含新增 11 用例）
- [x] GraphQL 层可见 `NopMetaEntityMethod__findPage`/`__save` 等标准 action（实体数 38→40），AutoTest 有对应用例（findPage/save/get/delete 断言 + `TestAllEntitiesHaveBizModels` 基线上调 40）
- [x] **接线验证**：`NopMetaEntity` 的 `methods` to-many 经 GraphQL 查询返回已登记方法行（AutoTest 断言，`testMethodCrudAndArgsEndToEnd`）
- [x] **无静默跳过**：守卫全部以 `NopException` + ErrorCode 拒绝，无静默改写/吞错
- [x] `TestNopMetaBizInterfaceCompleteness` 通过（新接口已登记）
- [x] owner doc `docs-for-ai/03-modules/nop-metadata.md` 增补实体清单 2 行与方法注册表契约段；roadmap M1 子项状态同步
- [x] `ai-dev/logs/` 对应日期条目已更新

**Phase 1 执行裁定（2026-10-27 实测修正，3 处与原计划写法的行为分层差异——行为契约不变，拒绝机制归属修正）**：

1. **kind 非法值拒绝面分层**：methodKind/implKind 非法值在 GraphQL 入口先被 xmeta dict 校验拒绝（平台码 `nop.err.biz.invalid-dict-option`），BizModel 守卫（`method-kind-unknown`/`method-impl-kind-unsupported`）实为 DAO 直写行（绕过 xmeta 校验）经 update 触达的 defense-in-depth 码。AutoTest 按 observable 行为断言入口码，defense-in-depth 码以 DAO 直写行 + update 接线用例单独验证（`testGuardKindDefenseInDepth`）。
2. **argName 重复拒绝归属 DB UK**：methodId/argName 均 mandatory、无 NULL-distinct 豁免面，DB UK `UK_NOP_META_METHOD_ARG_NAME` 全覆盖（平台转译 `nop.err.biz.entity-already-exists`）。按 P2-28 先例（守卫不接管 UK 已覆盖面）不设应用层查重码——计划原文的 `method-arg-duplicate` 码未落地（避免死码，invariant guard 6 约束），AutoTest 断言 UK 拒绝 + 回滚零残留。
3. `TestAllEntitiesHaveBizModels.KNOWN_ENTITY_BASELINE` 38→40（新增实体基线同步）。

### Phase 2 - 计算属性与别名字段（M2-1..M2-2）

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`、`nop-metadata-meta`、`nop-metadata-service`

- Item Types: `Feature`

- [x] `NopMetaEntityField` 增加 `fieldKind`（dict `meta/field-kind`：storage/computed/alias，缺省 storage）/`computeExpr`/`mapToProp` 列 + xmeta + i18n + `upgrade-nop-meta-entity-field-kind.sql` DDL（三方言）
- [x] kind 互斥守卫 + `FieldKindErrors` 分组：computed 无表达式拒绝、storage 带 expr/mapToProp 拒绝、alias 目标非法（不存在/自引用/非 storage）统一 `field-alias-target-unknown` 拒绝
- [x] `queryData` 输出投影解析：computed 字段经 XLang 表达式在返回行上求值（`XLang.newCompileTool()` 解析 + `EvalScopeImpl(rowMap)` 作用域 + `XLang.execute` 求值，`ExprFeatures.SIMPLE` 特性集禁 import/new/语句；save-time 校验 = 可解析 + 标识符白名单 ⊆ 实体字段集，求值失败抛 `field-computed-eval-failed`）、alias 字段以别名列名出现；不下推 SQL（行上解析）
- [x] AutoTest：computed/alias 字段出现在 `queryData` 返回行、storage 字段行为零变化、守卫拒绝路径、computed 求值失败显式报错

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 通过，新增测试显式覆盖：computed 求值、alias 重命名、storage 零变化（回归断言既有 queryData 测试不改预期）、每个守卫错误码一条拒绝用例（2026-10-27 实测全量 1371 tests 全绿，含新增 `TestNopMetaEntityFieldKind` 5 用例；既有 queryData 测试零改动通过 = storage 零变化回归）
- [x] **端到端验证**：GraphQL `queryData` 入口 → 含 computed/alias 字段实体的返回行断言（AutoTest `testComputedAndAliasFieldsInQueryData`）
- [x] **无静默跳过**：非法 kind 组合显式抛错码；表达式求值失败显式报错（不静默置 null）
- [x] owner doc 增补计算属性/别名契约段；roadmap M2 子项状态同步
- [x] `ai-dev/logs/` 对应日期条目已更新

**Phase 2 执行裁定（2026-10-27 实测修正——API 面与计划描述的机械差异，语义契约不变）**：

1. **解析器特性面落点**：`XLangCompileTool.compileSimpleExpr` 底层 `XplExprParser` 启用 `ExprFeatures.ALL`（"simple" 指表达式文法而非特性面），不满足本 Phase 的 SIMPLE 安全契约。实际落地 = 直接构造 `SimpleExprParser` + `enableFeatures(ExprFeatures.SIMPLE)` 解析（FilterExprHelper newFilterExprParser 同款平台用法），再经 `IXplCompiler.buildExecutable` 出可执行体、`ExprEvalAction.invoke(EvalScopeImpl(rowMap))` 求值（与计划所述 `XLang.execute` 同一执行语义的等价 API 面）。
2. **行字段标识符编译期解析**：编译期作用域默认拒绝未注册标识符（`LexicalScopeAnalysis` → `unresolved-identifier`）；行字段经 compile scope `setAllowUnregisteredScopeVar(true)` 注册为 SCOPE_VAR_REF、求值时从行 Map 解析（平台回退语义），save-time 标识符白名单仍为本 Phase 显式校验层。
3. **query-time 失败面扩展**：投影器对编译失败（如校验器水位变化后的存量行）与求值失败统一抛 `field-computed-eval-failed`，不静默跳过该列。

### Phase 3 - 方法执行器与 SQL 沙箱（M3-1..M3-3）

Status: completed
Targets: `nop-metadata-service`、`nop-metadata-api`、`nop-metadata-dao`

- Item Types: `Feature`

- [x] `MetaMethodSqlValidator`：单语句 SELECT-only + token 黑名单（水位不低于 custom_sql 现状：DDL/DML/文件族/PG 危险函数/`INTO`/`WITH` 等）+ `?` 占位符参数绑定检查；`MetaMethodExecutor` save-time（拒绝码 `method-impl-invalid`）与 query-time（拒绝码 `method-sql-unsafe`，兜底存量行）调用同一校验器
- [x] `MethodInvocationResultDTO`（methodId/methodName/columns/items/rowCount）入 `nop-metadata-api`
- [x] `NopMetaEntityBizModel.invokeMethod(metaEntityId, methodName, args)`：加载校验（not-found/disabled/arg-missing/arg-unknown）→ roles 检查（`IUserContext.isUserInAnyRole`，无登录态放行）→ implKind 分派 → entityKind 取数分派（PHYSICAL→EQL/ORM；SQL_VIEW/EXTERNAL→绑定数据源 withConnection，单表）→ 强类型 DTO 返回；执行错误 param 只带 methodId/methodName/类别
- [x] `INopMetaEntityBiz` 增补 `invokeMethod`；执行错误码族（method-not-found/method-disabled/method-arg-missing/method-arg-unknown/method-role-denied/method-sql-unsafe + method-exec-failed）
- [x] AutoTest：PHYSICAL 实体 invokeMethod 端到端（H2 localDb）；EXTERNAL 实体 invokeMethod 端到端（沿质量规则外部连接测试基建）；沙箱拒绝族（DML/多语句/黑名单函数/字面量注入形态）；roles 拒绝与放行；错误响应不含 SQL 全文断言

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 通过，新增测试显式覆盖上列全部路径（沙箱拒绝族逐类一条）（2026-10-27 实测全量 1378 tests 全绿，含新增 `TestNopMetaEntityMethodInvoke` 7 用例）
- [x] **端到端验证**：GraphQL `NopMetaEntity__invokeMethod` 入口 → 校验链 → SQL 执行 → DTO 返回，PHYSICAL 与 EXTERNAL 各一条完整用例
- [x] **接线验证**：save-time 与 query-time 确认调用同一 `MetaMethodSqlValidator`（代码追踪 + 测试证明 save 时拒掉的 SQL query 时同样被拒：`testQueryTimeRevalidationSameValidator`——DAO 直写 DML 行 invoke 时得 `method-sql-unsafe`，与 save-time `method-impl-invalid` 同一校验逻辑不同码）
- [x] **无静默跳过**：disabled/roles/校验失败全部显式错误码；无 catch 吞错
- [x] owner doc 增补 invokeMethod 契约与安全约束段；roadmap M3 子项状态同步
- [x] `ai-dev/logs/` 对应日期条目已更新

**Phase 3 执行裁定（2026-10-27 实测修正——语义契约不变，机制归属/契约补全 3 处）**：

1. **黑名单同域共享**：`MetaMethodSqlValidator` 不复制 custom_sql token 集合——方法 SQL 与 custom_sql 为同一语义域（整条语句文本 + token 精确匹配 + fail-closed），直接复用 `MetaQualityRuleExecutor.CUSTOM_SQL_FORBIDDEN_WORDS`/`CUSTOM_SQL_FORBIDDEN_SEQUENCES`/`tokenizeSqlForSandbox`（可见性 public 化）。F9 DRY 裁定拒绝的是与 `ExpressionMeasureValidator`（SELECT 片段域）共享，同域共享正是该裁定的相反面。水位随 custom_sql 演进自动同步。
2. **PHYSICAL 取数走 TableReferenceExecutor**（平台事务 JDBC 连接），非 plan 原文"EQL/ORM"——方法 implSource 是带 `?` 占位符的物理 SQL 文本，EQL 无法执行；entityKind 分派（PHYSICAL 平台连接 / SQL_VIEW/EXTERNAL withConnection）经 `MetaEntityReferenceResolver.resolve` + `TableReferenceExecutor.execute` 完成，与 profileEntity 同一取数先例（原文"绑定数据源连接执行"语义的精确化）。
3. **绑定顺序契约显式化**：`NopMetaMethodArg` 无 position 列，声明参数按 `argName` 字典序与 SQL `?` 出现顺序对应（owner doc 已记）；占位符数 ≠ 声明参数数在执行入口拒绝（`method-impl-invalid`/placeholder-count-mismatch）。

### Phase 4 - 标准 CRUD 标签与目录合成入口（M4-1..M4-2）

Status: planned
Targets: `nop-metadata-service`、`nop-metadata-meta`、`nop-metadata-dao`

- Item Types: `Feature`

- [ ] dict `meta/entity-tag` 登记 `std-crud`/`std-crud-ro` 保留标签；`NopMetaEntityBizModel` save 守卫：SQL_VIEW/EXTERNAL 写 `std-crud` 显式拒绝、PHYSICAL 写 `std-crud-ro` 显式拒绝（错误码族 `entity-tag-*`，不静默降级）
- [ ] 目录合成只读入口 `NopMetaEntity__findEntityPage(metaEntityId, query)`/`findEntityList`/`getEntity`：要求实体带 `std-crud` 或 `std-crud-ro` 标签，无标签显式拒绝（`entity-std-crud-not-declared`）；返回结构与 `queryData` 同源（`QueryEntityDataResultDTO`）；取数复用 queryData 同源路径（limit 契约沿 AR-09）
- [ ] `INopMetaEntityBiz` 增补三方法；AutoTest：标签守卫拒绝族、EXTERNAL 实体 findEntityPage 端到端（分页/过滤/排序）、无标签拒绝

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 通过，新增测试显式覆盖：每个守卫错误码一条拒绝用例 + EXTERNAL 分页查询端到端
- [ ] **端到端验证**：GraphQL save 实体（带 `std-crud-ro` 标签）→ `findEntityPage` 返回分页结果的完整路径
- [ ] **无静默跳过**：标签与 entityKind 不匹配显式拒绝，无自动降级
- [ ] owner doc 增补标准 CRUD 标签契约段；roadmap M4 子项状态同步、M1-M4 全部翻转状态
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 所有 in-scope confirmed live defects 已修复（或不存在）
- [ ] 4 组能力行为契约达成：方法可登记可执行、计算属性/别名可声明可查询、标签可声明、目录合成入口可用
- [ ] 必要 focused verification 已完成（每能力至少一条 GraphQL 端到端 + 守卫拒绝族全覆盖）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响 owner docs 已同步到 live baseline（`docs-for-ai/03-modules/nop-metadata.md`；`docs-for-ai/04-reference/source-anchors.md` 若新增锚点）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证 invokeMethod/findEntityPage 调用链在运行时连通（GraphQL 入口 → 校验 → 取数 → DTO），无空方法体/静默跳过
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] roadmap M1-M4 状态翻转与 plan 一致性核对完成

## Deferred But Adjudicated

### method-arg-duplicate 应用层查重码（Phase 1 实测裁定：不落地）

- Classification: `adjudicated as residual-risk-only`（行为契约由 DB UK 完整承载，非缺陷非降级）
- Why Not Blocking Closure: methodId/argName 均 mandatory，UK `UK_NOP_META_METHOD_ARG_NAME` 无 NULL-distinct 豁免面，重复写入全量被 DB UK 拒绝（平台转译 `nop.err.biz.entity-already-exists`）；应用层查重码在所有可达路径上先于 DB UK 不可达（死码），invariant guard 6 禁止死码。P2-28 先例（守卫不接管 UK 已覆盖面）。AutoTest 已断言 UK 拒绝 + 回滚零残留。
- Successor Required: `no`
- Successor Path: 无（若未来引入 argName 可空语义需重新裁定）

## Non-Blocking Follow-ups

- 计算列 SQL 下推（随表达式语言选择另裁定，非性能问题——设计 §3.2 已声明）
- `sourceKind=imported` 快照导入通道实现（importOrmModel 扩展，独立 successor plan）
- 搜索索引接入新实体（沿既有索引实体清单演进独立裁定）

## Closure

Status Note: （未关闭）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （closure 时填写）
