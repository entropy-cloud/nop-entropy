# 2269 CrudBizModel 批量与 by-query 变更的入参上限治理

> Plan Status: completed
> Last Reviewed: 2026-09-20（两轮对抗性审查通过：首轮 1B+3M+4m 全部修复，次轮 0B+0M+1m 措辞修正）
> Source: ai-dev/analysis/2026-09/2026-09-20-nop-entropy-full-repo-design-code-quality-audit.md（P1-3 静默截断、P2-T1 批量入参无上限）+ 用户设计裁定
> Related: 无

## Purpose

关闭 CrudBizModel 前端可变更入口的两类资源上限缺口：

1. **by-query 变更静默截断**：`deleteByQuery`/`updateByQuery` 受 maxPageSize 封顶后仅 `LOG.warn`，剩余行不处理且返回值误导（语义"全部删除/更新"，实际部分执行）。
2. **集合入参无上限**：`batchGet`/`batchDelete`/`batchModify`/三个 many-to-many 方法的用户提交集合直传 DAO，无任何 size 检查（filter 内 IN 有 100 上限，ids 直传路径构成旁路）。

## Current Baseline

以下事实均已核对 live code（2026-09-20）：

- `CrudBizModel.java:1481,1524`：`doUpdateByQuery`/`doDeleteByQuery` 取回数量命中 limit 时仅 `LOG.warn("nop.biz.*-by-query-result-truncated")`，随后照常部分执行并返回已处理数。
- `CrudBizModel.java:1458-1464`（updateByQuery）、`:1504-1511`（deleteByQuery）：两个 @BizMutation 包装方法设置 `disableLogicalDelete(false)` 后直接委托 `do*`（@BizAction）。
- `CrudBizModel.java:1596-1608`：`doFindList0` 统一走 `prepareFindPageQuery`；`:395-403` 对 `limit<=0` 归一化为 maxPageSize、`limit>maxPageSize` 钳位。
- `CrudBizModel.java:419-427`：`getMaxPageSize()` = 全局 `CFG_GRAPHQL_MAX_PAGE_SIZE` + 每实体 `ext:maxPageSize`（`BizConstants.java:93`）覆写，覆写仅允许抬高。
- `CrudBizModel.java:1030-1049`（batchGet，@BizQuery）、`:1300-1320`（batchUpdate，@BizMutation，ids 集合直传 dao）、`:1323-1346`（batchDelete，@BizMutation）、`:1350+`（batchModify，@BizMutation，入参 data + delIds 两个集合）、`:1634/1651/1667`（add/remove/updateManyToManyRelations，@BizMutation，relValues 集合）：均无 size 校验。
- **内部大集合调用方（对抗审查 B1 发现）**：`getEntityListByTreeEntity`（`CrudBizModel.java:1919-1922`）内部调用 `this.batchGet(idList, false, context)`，idList 大小受 maxPageSize（默认 1000，可被 ext:maxPageSize 抬高）钳位——默认配置即可超过批量上限 500，是平台主代码中唯一向 batchGet 传大集合的路径（上游 `findListForTree`/`findPageForTree`，均 @BizQuery）。另 credential 模块 4 处覆写为 super 委托型，继承检查即可。
- 测试基座前提（对抗审查 M3 发现）：`TestCrudBizModelBatchAndTreeQuery` 的 BatchFixture **未注册 BizObject**（`getThisObj()` 为 null），走不到 `prepareFindPageQuery`/`getMaxBatchSize` 路径；注册了 BizObject + ObjMetaImpl 的先例是同文件 TreeFixture（:141-165）。count-first 与 ext:maxBatchSize 覆写测试必须按 TreeFixture 模式构建 fixture。
- `BizConfigs.java:21-24`：`CFG_BIZ_QUERY_IN_OP_MAX_ALLOW_VALUE_SIZE`（`nop.biz.query.in-op-max-allow-value-size`，缺省 100）仅在 filter 校验路径生效（`ObjMetaBasedFilterValidator.java:79-83`）。
- `BizErrors.java`：ErrorCode 用 `define("nop.err.biz.*", "中文消息{param}", ARG_*)` 模式，ARG 常量定义于同文件。
- `ReflectionBizModelBuilder.java:168-186`：@BizAction 方法不生成 GraphQL operation（仅内部 bizActions 表）——@BizMutation/@BizAction 即前后端信任边界，无需新机制。
- 既有覆写兼容面：`NopDatavSingleWriterCrudBizModel`、`MfaSensitiveTableBizModel` 整方法 reject（不受影响）；`NopAiModelBizModel.deleteByQuery:174`、`NopMetaDataSourceBizModel.deleteByQuery:571` 为 collect-then-super 模式（super 抛错时外层仅多一次被钳位的只读 findList，无副作用、无 usage 泄漏，天然兼容）。`NopAuthUserBizModel:913,1796` 使用的是 **DAO 层** `dao.deleteByQuery`，与本计划无关。
- `CrudBizModel.java:1543-1573`：`asDict`（@BizQuery）按 maxPageSize 取字典选项，截断时仅 warn（`as-dict-options-truncated`），前端拿到不完整字典。
- 测试基座：`nop-service-framework/nop-biz/src/test/java/io/nop/biz/crud/TestCrudBizModelBatchAndTreeQuery.java` 已有 fake-dao/Proxy 模式纯逻辑测试可直接扩展。

## 用户已裁定的设计决策（不再重议）

1. by-query 检查放在 @BizMutation 包装层（`deleteByQuery`/`updateByQuery`），**do\* @BizAction 完全不动**（后台调用方如 NopCredentialBizModel、idea-plugin 行为不变，其内部 warn 保留作后台诊断与竞态兜底）。
2. 前端语义 = 有界操作，超限**抛专用 ErrorCode**，且必须在任何变更发生之前（fail-before-side-effect）；不提供前端"全部删除"语义、不做返回值标志位、不做自动循环补全。
3. 超限判定用 **count-first**：克隆 query → 与 do\* 内部列表查询完全相同的 prepare（同 authObjName + METHOD_FIND_LIST）→ `count > 有效 limit` 抛错（严格大于；恰好等于 limit 放行，消除 >= 误报）。
4. 批量集合上限用**独立配置** `nop.biz.max-batch-size`，**缺省 500**（用户指定），不 fallback 到 maxPageSize（理由：等价入口限额一致性——filter IN 上限 100 不应被 ids 直传路径以更宽的默认值旁路；读写成本不同；fallback 链引入隐藏耦合）。
5. 每实体覆写对称复用 maxPageSize 机制（`ext:maxBatchSize`，仅允许抬高）。
6. asDict 一并纳入（读路径同款问题，第二批实施）。

## Goals

- 前端可达的批量/集合入参全部有显式上限（缺省 500，可全局配置 + 每实体覆写），超限抛专用 ErrorCode 且零副作用。
- `deleteByQuery`/`updateByQuery` 在命中数超过有效 limit 时抛专用 ErrorCode 且零行被变更；未超限时行为与现状完全一致。
- `asDict` 在字典表行数超过 maxPageSize 时抛专用 ErrorCode（消除不完整字典）。
- 平台规范落地：新 ErrorCode 走 `BizErrors.define()` 模式；新行为有同 Phase 单测。

## Non-Goals

- 不为后台提供新的分批循环 API（后台已有 DAO 组合能力；大规模运维走 nop-batch）。
- 不改动 `do*` 系列内部行为、不删除其 warn。
- 不改动 filter IN 上限（100）与 maxPageSize（读分页）本身。
- 不处理 `OrmEntityPropConnectionFetcher` 的关联分页（现有 maxPageSize 已覆盖）。
- 不做 GraphQL 层的通用参数大小校验框架（GraphQLArgumentValidator 改造超出本计划）。

## Scope

### In Scope

- `nop-service-framework/nop-biz`：BizConfigs、BizConstants、BizErrors、CrudBizModel。
- `docs-for-ai/02-core-guides/service-layer.md`（owner doc 同步）。
- CHANGELOG.md 行为变更条目。
- 下游模块受影响面验证（grep + 必要时跑相关测试）。

### Out Of Scope

- 上一节 Non-Goals 的全部内容。
- nop-auth/nop-ai/nop-metadata 等下游模块的代码改动（除非其测试因新行为需要适配——预期仅 reject 型覆写测试，不受影响）。

## Execution Plan

### Phase 1 - maxBatchSize 配置与集合入参上限检查

Status: completed
Targets: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/BizConfigs.java`、`BizConstants.java`、`BizErrors.java`、`nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java`、`src/test/java/io/nop/biz/crud/`（新增或扩展测试）

- Item Types: `Fix | Decision | Proof`

- [x] Fix: `BizConfigs` 新增 `CFG_BIZ_MAX_BATCH_SIZE`（`nop.biz.max-batch-size`，Integer，缺省 500，带中文 @Description，模式对齐相邻 IN 上限配置）
- [x] Fix: `BizConstants` 新增 `EXT_MAX_BATCH_SIZE = "ext:maxBatchSize"`
- [x] Fix: `BizErrors` 新增 `ERR_BIZ_BATCH_SIZE_EXCEEDS_LIMIT`（`nop.err.biz.batch-size-exceeds-limit`，消息含 bizObjName/size/maxBatchSize 三个 param 位）及所需 ARG 常量
- [x] Fix: `CrudBizModel` 新增 `public int getMaxBatchSize()`（对称 `getMaxPageSize()`：全局配置 + `ext:maxBatchSize` 覆写，覆写仅允许抬高）
- [x] Fix: **提取 `doBatchGet`（@BizAction）**：现 `batchGet` 方法体（DAO 加载 + 逻辑删/权限逐实体检查）整体移入 `doBatchGet`（含 `CollectionHelper.isEmpty(ids)` 的 null 短路），`batchGet`（@BizQuery）保留原签名与原有注解（@Description/@BizQuery/@GraphQLReturn 留在 wrapper，doBatchGet 仅加 @BizAction + @Name 参数注解）、入口加 size 检查后委托 `doBatchGet`；`getEntityListByTreeEntity` 内部调用改为 `doBatchGet`（树查询 idList 可合法达到 maxPageSize，属平台内部路径，绕过批量上限——对齐"wrapper 检查、do\* 不检查"的既有模式）
- [x] Fix: `batchGet`、`batchDelete`、`batchUpdate` 入口处 `ids.size() > getMaxBatchSize()` 抛错（在任何 DAO 调用之前）
- [x] Fix: `batchModify` 入口处 `data.size()` 与 `delIds.size()` 分别检查
- [x] Fix: `addManyToManyRelations`/`removeManyToManyRelations`/`updateManyToManyRelations` 入口处 `relValues.size()` 检查（在 `get(id)` 之前，保证零副作用）
- [x] Proof: 单测覆盖——超限抛 `ERR_BIZ_BATCH_SIZE_EXCEEDS_LIMIT` 且无 DAO 变更调用发生；恰好等于上限（500）通过；`ext:maxBatchSize` 覆写生效（抬高）；三个 many-to-many 方法超限在 entity 加载前抛出；树路径经 `doBatchGet` 不受批量上限影响（idList > maxBatchSize 仍正常）

Exit Criteria:

- [x] 上述 7 个方法（8 个集合检查点：batchGet/batchUpdate/batchDelete + batchModify 的 data 与 delIds + M2M×3）在集合超限时抛专用 ErrorCode，且实现位于任何实体加载/DAO 写调用之前（代码顺序可复查）
- [x] `getEntityListByTreeEntity` 走 `doBatchGet`，树查询在大 idList 下行为与现状一致（回归测试或代码复查可证）
- [x] 缺省值 500 生效（测试或配置断言可见）
- [x] 新增测试全部通过，且覆盖"抛错时零副作用"断言（fake dao 未收到 `batchGetEntitiesByIds`/`tryBatchGetEntitiesByIds`/`batchRequireEntitiesByIds` 调用）；涉及 `getMaxBatchSize` 的测试使用注册了 BizObject 的 fixture（TreeFixture 模式），`FakeBatchDao` 按需补 `countByQuery`/`batchRequireEntitiesByIds` 分支
- [x] **无静默跳过**：超限路径是抛异常，不是截断/静默返回
- [x] `docs-for-ai/02-core-guides/service-layer.md` 增加 max-batch-size 语义说明（含配置名、缺省、ext 覆写）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - deleteByQuery/updateByQuery 前置计数检查

Status: completed
Targets: `CrudBizModel.java`、`BizErrors.java`、`src/test/java/io/nop/biz/crud/`

- Item Types: `Fix | Decision | Proof`

- [x] Fix: `BizErrors` 新增 `ERR_BIZ_BY_QUERY_EXCEEDS_LIMIT`（`nop.err.biz.by-query-exceeds-limit`，消息含 bizObjName/limit/count，行动指引：缩小过滤条件或走后台通道）
- [x] Fix: `updateByQuery`（@BizMutation）在委托 `doUpdateByQuery` 前增加检查：`QueryBean countQuery = query == null ? new QueryBean() : query.cloneInstance()`（null 语义：等价全表 count 对比 maxPageSize；克隆在 `setDisableLogicalDelete(false)` 之后进行）→ `prepareFindPageQuery(countQuery, getAuthObjName(METHOD_FIND_LIST), METHOD_FIND_LIST, null, context)`（与 do\* 内部列表查询完全同参）→ `dao().countByQuery(countQuery)` → `count > countQuery.getLimit()`（prepare 归一化后的有效 limit）抛错
- [x] Fix: `deleteByQuery`（@BizMutation）同款检查
- [x] Decision: `doUpdateByQuery`/`doDeleteByQuery` 及其 warn 一字不动（后台逃生通道 + 竞态兜底；count==limit 放行时 do\* 内部 warn 仍会触发一次属可接受诊断噪音，见 Non-Blocking Follow-ups）
- [x] Proof: 单测覆盖——count > limit 抛错且 deletedIds/updates 为空（零副作用）；显式小 limit（如 limit=5、count=10）抛错；count == limit 恰好通过；count < limit 正常执行路径不变；query == null 且 count > maxPageSize 抛错；count 与 delete 之间无 filter 漂移（克隆不污染原 query——原 query 随后仍交给 do\* 使用）。测试使用注册了 BizObject 的 fixture（TreeFixture 模式），`FakeBatchDao` 增加 `countByQuery` 可配置分支与 `batchRequireEntitiesByIds` 分支

Exit Criteria:

- [x] 两个 @BizMutation 入口在任何变更发生前抛错（fake 测试断言零写调用）
- [x] `count > limit`（严格大于）语义落地，== limit 放行
- [x] 计数与删除使用同一 prepared query 语义（同 authObjName、同 action、同 disableLogicalDelete），克隆不产生双重 filter 叠加（测试或代码复查可证）
- [x] `do*` 方法 diff 为零（git diff 可证）
- [x] collect-then-super 覆写兼容性在 plan 或测试注释中留档（NopAiModelBizModel/NopMetaDataSourceBizModel 模式与抛错行为的组合语义）
- [x] 新增测试全部通过
- [x] `docs-for-ai/02-core-guides/service-layer.md` 更新 deleteByQuery/updateByQuery 语义（超限抛错、前端不再有静默截断、后台走 do\*）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - asDict 字典选项超限检查

Status: completed
Targets: `CrudBizModel.java`、`BizErrors.java`、测试

- Item Types: `Fix | Proof`

- [x] Fix: `BizErrors` 新增 `ERR_BIZ_DICT_OPTIONS_EXCEEDS_LIMIT`（`nop.err.biz.dict-options-exceeds-limit`，含 bizObjName/count/maxPageSize）
- [x] Fix: `asDict` 在取数前先计数：计数的 prepared query 必须与 `findPage` 实际取数路径完全同参（同 `disableLogicalDelete(false)` 设置、同 prepareQuery=`this::invokeDefaultPrepareQuery`、同 authObjName/action——实现时从 `asDict → findPage → doFindPage` 的实际调用链核对），`count > getMaxPageSize()` 抛错（替换现有截断 warn 路径；warn 可保留为不达阈值的诊断或直接移除——实现时按最小改动裁定并在日志留档）
- [x] Proof: 单测覆盖——超限抛错；未超限返回完整字典

Exit Criteria:

- [x] 超限抛专用 ErrorCode，未超限行为与现状一致
- [x] 新增测试通过
- [x] owner doc 若已覆盖 asDict 行为则同步（`service-layer.md`；如未提及则 No owner-doc update required 并在日志写明裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 下游验证与收口

Status: completed
Targets: CHANGELOG.md、下游模块测试、plan 维护

- Item Types: `Proof | Follow-up`

- [x] Proof: grep 下游模块（nop-auth/nop-ai/nop-metadata/nop-datav/nop-demo/nop-credential 等）对 deleteByQuery/updateByQuery/batchGet/batchUpdate/batchDelete/batchModify/asDict/ManyToMany/doBatchGet 的测试与代码引用，确认：reject 型覆写不受影响；super 委托型覆写（credential 4 处）继承新检查；无测试依赖"超限静默截断"旧行为；发现受影响测试则修复适配
- [x] Proof: 受影响下游模块中实际引用这些 API 的模块测试通过（按 grep 结果确定最小集，预期为空或极小）
- [x] Fix: CHANGELOG.md 增加行为变更条目（deleteByQuery/updateByQuery 超限抛错；批量入参上限 500；asDict 超限抛错）

Exit Criteria:

- [x] 下游 grep 清单与结论留档于本 plan 或日志
- [x] CHANGELOG 条目已加
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects（静默截断、集合入参无上限、asDict 截断）已修复且有 focused tests
- [x] `./mvnw compile -pl nop-service-framework/nop-biz -am` 通过
- [x] `./mvnw test -pl nop-service-framework/nop-biz -am` 通过
- [x] checkstyle（随 mvn 构建内置）通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2269-crud-write-batch-and-by-query-limits.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-biz --severity high` 退出码 0（或确认无新增空壳）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors
- [x] 受影响 owner docs（service-layer.md、CHANGELOG）已同步
- [x] 独立子 agent closure audit 已完成且 evidence 写入本 plan（含 Anti-Hollow：超限路径从 GraphQL 入口到 ErrorCode 抛出的调用链追踪）
- [x] 无 in-scope live defect 被降级为 follow-up

## Deferred But Adjudicated

### GraphQL 层通用集合参数校验（GraphQLArgumentValidator）

- Classification: out-of-scope improvement
- Why Not Blocking Closure: CrudBizModel 入口已全覆盖平台标准批量 API；自定义 BizModel 的集合参数防护属通用框架增强，独立演进。
- Successor Required: no
- Successor Path: 无（记录于全仓审计报告 P2-T1 剩余项）

## Non-Blocking Follow-ups

- count 与 delete 之间的插入竞态窗口由 do\* 保留的 warn 兜底观测（后插入行导致实际命中超限时仍记 warn）；如需彻底封闭需在 do\* 内复核，属后续可选加固。
- count==limit 放行时 do\* 内部 `>=` warn 会触发一次（完整执行也告警），属可接受诊断噪音；消除需改 do\* warn 条件（本计划裁定 do\* diff 为零，不改）。
- count-first 的克隆 prepare 与 do\* 的原 query prepare 会对全局 `nopGlobalQueryTransformer` 各执行一次，若该 transformer 非幂等则 count 与实际命中集可能漂移（与竞态窗口同类，现实风险低）。

## Closure

Status Note: 四个 Phase 全部落地并通过独立 closure audit（8/8 PASS）：8 个批量检查点 + by-query 前置计数 + asDict 计数均为"任何变更发生前抛错"的 fail-fast 实现，do* 后台逃生通道 diff 为零，树查询内部路径经 doBatchGet 不受批量上限约束，测试/文档/CHANGELOG 同步完成。
Completed: 2026-09-20

Closure Audit Evidence:

- Reviewer / Agent: 独立 general-purpose 子 agent（fresh session，agent_364b632e-8edd-46c6-bc53-bc2f32e4ab01）
- Evidence:
  - Phase 1 全部子项 PASS：CFG_BIZ_MAX_BATCH_SIZE 缺省 500（BizConfigs.java:24-26）、EXT_MAX_BATCH_SIZE（BizConstants.java:94）、getMaxBatchSize 对称实现（CrudBizModel.java:439-448）、8 检查点前置性逐一核对（check 行 → 首个 DAO 调用行：1064→1072/1344→1346/1366→1368/1396+1397→1410/1711→1712/1729→1730/1744→1745）、doBatchGet @BizAction（1068-1086）+ 树路径改走（:1998）
  - Phase 2 PASS：checkByQueryNotExceedLimit（1512-1521）null/克隆/同参 prepare/严格大于/带 param 抛错逐项核对；git diff HEAD 确认 doUpdateByQuery/doDeleteByQuery 方法体零 +/- 行，do* diff 为零成立
  - Phase 3 PASS：asDict 计数检查（1614-1623）与 findPage 实际调用链（316-336）完全同参；warn 保留为边界诊断
  - Anti-Hollow PASS：GraphQL 入口 → 检查 → throw 调用链完整；ReflectionBizModelBuilder.java:168-186 确认 @BizAction 不生成 GraphQL operation；测试以 fake dao 调用计数真实断言零副作用
  - 测试证据：TestCrudBizModelWriteLimits 17/17、TestCrudBizModelBatchAndTreeQuery 4/4、nop-biz 全量 86/86 BUILD SUCCESS（审计员独立复跑）
  - 工具脚本：check-plan-checklist.mjs --strict exit 0（52 items 全勾）；scan-hollow-implementations.mjs --module nop-biz --severity high exit 0；check-doc-links.mjs --strict 0 errors
  - Deferred 项分类检查：GraphQL 通用参数校验属 Non-Goals 声明的 out-of-scope；三条 follow-up（竞态窗口/warn 噪音/transformer 幂等）核实为纯日志或低风险漂移，无 in-scope live defect 被降级

Follow-up:

- GraphQL 层通用集合参数校验（GraphQLArgumentValidator）为 out-of-scope improvement，见 Deferred But Adjudicated，无 successor plan 需要
- 除 Non-Blocking Follow-ups 三条已裁定项外，无 remaining plan-owned work
