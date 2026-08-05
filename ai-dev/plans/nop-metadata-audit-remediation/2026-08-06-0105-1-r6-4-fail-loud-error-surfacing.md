# R6-4 fail-loud 修复（P2-06/07/09）

> Plan Status: completed
> Last Reviewed: 2026-08-06
> Draft Review: R1 `ses_02d141f8bffeF02U18a0ZWB79I`（0 Blocker / 3 Major / 5 Minor——M1 测试目标错置（TestAggregationHelper 非测试类，3 参构造器 JUnit 拒跑）→ 已改新建专用类/复用 TestMixedSameDbJoinAggregationProcessor + Mockito；M2 判别性 fixture 窄窗口（full-load :164 先于 parseDeltaModel :180，XML 语法错在 full 阶段即抛）→ 已加 fixture 规格 + errorCode 判别器；M3 P2-09 失败路径（wfName 硬编码 xmeta、mock 无先例）→ 已改 invalid-status 确定性真实失败 + CREATE 语义不落库断言；m4-m8 基线措辞/LOG 约定/ERR_AGGR_EXEC_FAILED 候选/口径 caveat/子串断言已修）。R2 `ses_02d054415ffeIn9ajFMeoHkC0S`（M1/M3/m4-m8 PASS；M2 判别器有效但 fixture 需补"base 无实体"前置约束——已补：base 含实体时 merge 阶段即抛 ERR_XDSL_MULTIPLE_NODE_HAS_SAME_UNIQUE_ATTR_VALUE，窄窗口仅在 base 无 `<entities>` 时成立 + 错误码括注修正 + 预期可能先红一次的注记；附 3 Minor 已修：TestAggregation* 复用数 3、save 行号 :176、ERR_AGGR_EXEC_FAILED 定义/使用位置）。consensus 达成。
> Mission: nop-metadata-audit-remediation
> Work Item: MR6（R6.4）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MR6 段 R6.4 行 + Follow-up Backlog P2-06/07/09）、`ai-dev/audits/arm-index-nop-metadata.md`（§P2 MR6 裁决记录）
> Related: 执行顺序 `{1}` of 3 — 本 plan 先行；R6.5（`{2}`）、R6.6（`{3}`，P2-03 死码清理需在本 plan 落地后重新核验死码清单）随后。与 R6.1/R6.2/R6.3 文件域不重叠（quality/field、connection、NopMetaDataSourceBizModel/NopMetaTableBizModel）。

## Purpose

按 MR6 R6.4 行收口三项 Backlog finding（2026-08-05 两轮审计登记，R6.0 live 复核提级为正确性类）：

1. **P2-06**：`AggregationHelper.checkTableExists` 把 getTables 的 SQLException 归类为"表不可见"（返回 false）——连接中断/权限缺失等真实故障被错误分类为业务性空字段集（`ERR_FIELD_RESOLVE_NO_FIELDS` 语义漂移）。
2. **P2-07**：`NopMetaModuleBizModel.parseDeltaModel` 解析失败静默降级 delta=full——x:extends 链存在时 delta=full 语义不等价（delta 覆盖声明丢失），数据完整性风险。
3. **P2-09**：`NopMetaTagLabelBizModel.trySubmitForApproval` 提审失败仅 LOG.warn 继续——标签保存成功但永不进审批流，用户侧零感知。

目标状态：三处静默降级/静默跳过全部改为 fail-loud（显式错误码 + 原始异常链保留），回归测试钉死新语义。

## Current Baseline

2026-08-06 live repo 核对：

- **P2-06（confirmed，边界需注意）**：`AggregationHelper.checkTableExists`（`query/AggregationHelper.java:496-506`）：`try (ResultSet rs = metaData.getTables(null, schema, tableName, null))`，空结果返回 false；catch SQLException → `LOG.warn("... treated as not visible ...")` + 返回 false。调用链：`isEntityTableVisible`（:487-494，按原名/大写/小写三次探测）→ `MixedSameDbJoinAggregationProcessor.checkEntityTableVisible`（`query/MixedSameDbJoinAggregationProcessor.java:165-176`，`withConnection` lambda 内捕获 `visible[0]`）→ 聚合 join 字段解析。**边界事实（避免执行者追查幻影错误码）**：建连失败已在 `MetaDataSourceConnectionProcessor.withConnection`（:130-131）fail-loud 为 `ERR_DATASOURCE_CONNECT_FAILED`（DataSourceErrors.java:20，:398-400）——本 finding 被吞的是**连接成功后** `getTables`/`rs.next` 的 SQLException；`checkEntityTableVisible` 返回 false 后 mixed-join 路径实际落入 `CrossDbInMemoryAggregationProcessor` 回退（MixedSameDbJoinAggregationProcessor:69-72），`ERR_FIELD_RESOLVE_NO_FIELDS`（FieldErrors.java:14）是否在该链上由 Phase 1 接线 Proof 厘清（不要预设）。核心问题不变：真实故障（权限缺失/元数据面异常）与"表确实不存在"无法区分，静默吞掉根因。
- **P2-07（confirmed）**：`NopMetaModuleBizModel.parseDeltaModel`（`entity/NopMetaModuleBizModel.java:217-234`）：`hasExtends(sourceContent)` 为 true 时经 `DslNodeLoader.loadDslNodeFromResource` + `DslModelParser.parseWithXDef` 解析 delta；catch Exception → `LOG.warn("parseDeltaModel failed, falling back to full model as delta", e)` → 返回 fullModel（delta=full 语义不等价）。唯一调用点 :180（`importOrmModel` @BizMutation :158 内），**位于 `orm().save(module)`（:176）之后、`persistModelGraph(deltaModel...)`（:186）之前**——若 fail-fast 抛错，必须确认事务回滚覆盖已 save 的 module（否则留下"有 module 无 delta 行"的中间态）。批量入口 `importOrmModels`（:351-375，per-path catch :364-371）已按 per-path catch（LOG.error + 继续），`testImportOrmModelsBatch`（TestNopMetaModuleBizModel.java:94-119）已验证 per-path 失败不中断整批——fail-fast 与批量 per-path 隔离天然兼容（机制：成功路径自身 :189 flushSession 落盘，失败路径 :370 clearSession 丢弃入队数据，跨路径不丢）。
- **P2-09（confirmed）**：`NopMetaTagLabelBizModel.trySubmitForApproval`（`entity/NopMetaTagLabelBizModel.java:124-133`）：catch Exception → `LOG.warn("submitForApproval failed ... workflow may not be available")` → 方法正常返回。调用点 `triggerApprovalIfNeeded`（:100-102，Derived/Propagated/Automated 类型），`getWfNameFromMeta()`（:87）为 null 时早退（该路径为已裁定降级：无 wf 配置不自动提审，R3.6/P2-MA5-401）；**catch 捕获的是 wfName 已配置但提审调用本身失败**——此时标签已保存但永不进审批流，用户零感知。既有正路径测试：`TestNopMetaTagLabelApproval.java`（:124-147 验证 approveStatus=SUBMITTED）、`TestNopMetaTagLabelApprovalIntegration.java`。
- **异常类型**：`NopMetadataException`（`service/NopMetadataException.java`）提供 `(ErrorCode)` 与 `(ErrorCode, Throwable)` 构造器（cause 保留能力已有）。
- **既有错误码参考**：`DataSourceErrors.ERR_DATASOURCE_CONNECT_FAILED`（DataSourceErrors.java:20）、`DataSourceErrors.ERR_TABLEREF_PLATFORM_META_FAILED`（:70）；`ModuleErrors` 现有 ERR_ORM_RESOURCE_NOT_FOUND / ERR_ORM_RESOURCE_READ_FAILED（ModuleErrors.java:17,20）；`MiscErrors.ERR_TAG_LABEL_INVALID_LABEL_TYPE`（MiscErrors.java:106）。
- 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` → **909 tests / 0 failures / 0 errors / 0 skipped**（service 908 + web 1，R6.3 收口口径；**注意：909 计数为 `-pl nop-metadata/nop-metadata-service -am` 口径**，全量 `-am` 偶遇预存在 rocksdb 性能 flaky 时按单跑复绿降级，见 Closure Gates caveat）。

## Goals

- 三处静默降级改为 fail-loud：P2-06 抛连接/元数据检查类错误码（不落 `ERR_FIELD_RESOLVE_NO_FIELDS` 语义漂移）；P2-07 抛 delta 解析错误码（不落 delta=full 伪造值）；P2-09 提审失败向调用方显式抛错（用户可见，不再静默成功）
- 全部抛错保留原始异常链（`NopMetadataException(ErrorCode, Throwable)`）
- 回归测试钉死新语义；既有正路径用例不回归
- arm-index §P2 P2-06/07/09 行终态 = fixed + roadmap R6.4 行 → done

## Non-Goals

- 不改变 `getWfNameFromMeta()==null → 不自动提审` 的已裁定降级路径（R3.6 语义保持）
- 不引入 R6.5 的日志类修复（P2-01/02/04 归属下一 plan）
- 不做 P2-03 死码清理（R6.6，本 plan 引入的新错误码需在 R6.6 重新核验死码清单时保留）
- 不改 `importOrmModels` 批量语义（per-path 隔离已存在，仅需 fail-fast 与它兼容）

## Scope

### In Scope

- `AggregationHelper.checkTableExists` / `isEntityTableVisible` 错误语义（Fix）
- `NopMetaModuleBizModel.parseDeltaModel` fail-fast（Fix）+ 事务回滚验证（Proof）
- `NopMetaTagLabelBizModel.trySubmitForApproval` fail-loud（Fix）
- 对应回归测试（Fix）
- 新错误码定义（Fix，如裁定需要）
- arm-index §P2 + roadmap R6.4 行终态更新（Fix）
- `ai-dev/logs/2026/08-06.md`（或执行当日日志）更新（Follow-up）

### Out Of Scope

- P2-01/02/04（R6.5）、R6.6 批量（死码清理 / docs sweep / watch-only 登记）
- P2-10/P2-13（R6.1）、P2-11/P2-12（R6.2）、AR-07/AR-08（R6.3）
- `getWfNameFromMeta` 降级路径改造

## Execution Plan

### Phase 1 - P2-06：checkTableExists 故障显式化

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/AggregationHelper.java` + 测试类（见下）

- Item Types: `Decision | Fix | Proof`

- [x] **错误码裁定（Decision）**：候选 (a) 复用 `ERR_DATASOURCE_CONNECT_FAILED`（连接类语义，但 getTables 失败不限于连接）；(b) 新建 `AggregationErrors.ERR_AGGR_TABLE_VISIBILITY_CHECK_FAILED`（表可见性检查失败，param: schema + tableName，语义精确）；(c) 复用 `ERR_AGGR_EXEC_FAILED`（定义 AggregationErrors.java:72，AggregationHelper.java:467/:525 有使用）。裁定需记录理由；推荐 (b)（错误语义精确，且 R6.6 P2-03 死码清理时新码在 use 侧有引用不会被误删）
- [x] 按裁定落地（Fix）：`checkTableExists` catch SQLException → 抛 `NopMetadataException(裁定码, e)` + param(schema, tableName)；仅空结果返回 false；`LOG.warn` 保留在 throw 前（诊断留证）。`isEntityTableVisible` 三连探测语义保持（首个调用抛错即 fail-fast，不再尝试大小写变体——探测本身异常意味着元数据面不可用，重试变体无意义）
- [x] **单元测试（Fix，判别性验证）**：**禁止**向 `TestAggregationHelper.java` 添加 `@Test`——该文件是共享 helper（3 参构造器、零 `@Test`，被 3 个 TestAggregation* 测试类实例化复用），加 `@Test` 会被 JUnit 以无零参构造器拒跑。改为：新建专用测试类（如 `TestAggregationHelperTableVisibility.java`，NopTest 或纯 JUnit + Mockito，执行时按仓库既有测试模式裁定）或复用 `TestMixedSameDbJoinAggregationProcessor.java`（44 行，已有 Mockito `mock`/`when` + `assertThrows` + errorCode 断言先例，且 `checkEntityTableVisible` 的接线验证同文件可得）。用例——(a) mock `DatabaseMetaData.getTables` 抛 SQLException → `assertThrows` NopMetadataException 且 errorCode 为裁定码、cause 链保留原始 SQLException；(b) 空结果 → 返回 false（语义保持）；(c) 有行 → 返回 true。mock 用 Mockito（已在 pom 且全模块在用）
- [x] **接线验证（Proof）**：确认 `MixedSameDbJoinAggregationProcessor.checkEntityTableVisible`（:165-176）的 `withConnection` lambda 内异常向调用方传播（已实测：`MetaDataSourceConnectionProcessor.withConnection` 仅 catch SQLException，NopMetadataException 属 RuntimeException 直接上抛——写进 plan 证据）；确认聚合 join 路径的真实回退/错误语义（false → CrossDbInMemoryAggregationProcessor 回退 vs 抛 ERR_FIELD_RESOLVE_NO_FIELDS——如实记录，不预设）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 错误码裁定已记录（含选择与理由）——**裁定 = 候选 (b) `AggregationErrors.ERR_AGGR_TABLE_VISIBILITY_CHECK_FAILED`**（`nop.err.metadata.aggr-table-visibility-check-failed`，params schema/tableName/error；新增 `NopMetadataArgs.ARG_SCHEMA`）。理由：(a) ERR_DATASOURCE_CONNECT_FAILED 语义限定建连阶段（MetaDataSourceConnectionProcessor:130-131 已覆盖建连失败），getTables 元数据面异常不属于连接失败；(c) ERR_AGGR_EXEC_FAILED 语义过宽（聚合执行失败），本错误发生在执行前的可见性探测阶段，且使用侧已有多处（:115/:135/:467/:525），并入会稀释其语义。新码在 use 侧（AggregationHelper.checkTableExists）有引用，R6.6 P2-03 死码清理不会误删
- [x] **端到端验证**：checkTableExists 的 SQLException 路径从 `getTables` 抛错到调用方收到裁定错误码完整连通（单测断言 + 接线核查）；"表不存在"（空结果）仍返回 false 不误报——`TestAggregationHelperTableVisibility` 5/5（testCheckTableExistsMetaDataFailureThrows / testCheckTableExistsEmptyResultReturnsFalse / testCheckTableExistsFoundReturnsTrue / testIsEntityTableVisibleFailsFastOnFirstProbeError / testExecutePropagatesVisibilityCheckFailure）；**接线核查**：testExecutePropagatesVisibilityCheckFailure 从 `MixedSameDbJoinAggregationProcessor.execute()` 公开入口 → checkEntityTableVisible（:165-176）→ withConnection lambda → isEntityTableVisible → checkTableExists 抛 ERR_AGGR_TABLE_VISIBILITY_CHECK_FAILED（cause=原始 SQLException）完整传播到调用方（doAnswer 直调 action 模拟真实 withConnection 的 lambda 调用契约；真实实现 MetaDataSourceConnectionProcessor.withConnection:130-131 仅 catch SQLException，NopMetadataException 属 RuntimeException 直接上抛——双层证据）
- [x] **接线验证**：withConnection lambda 异常传播链已验证（不吞异常、不落到 ERR_FIELD_RESOLVE_NO_FIELDS）——真实回退语义如实记录：mixed-join 路径 `checkEntityTableVisible==false`（正常"表不存在"）→ `CrossDbInMemoryAggregationProcessor` 回退（MixedSameDbJoinAggregationProcessor:69-72），该路径不抛 ERR_FIELD_RESOLVE_NO_FIELDS；ERR_FIELD_RESOLVE_NO_FIELDS（FieldErrors.java:14）仅用于 MetaTableFieldResolver（:111/:327/:361）字段解析面，不在本链上——本次修复后 SQLException 路径 fail-loud 抛裁定码，不再落入 false→回退分支
- [x] **无静默跳过**：catch 不再返回 false 掩盖故障；throw 前 LOG.warn 留证（AggregationHelper.java:502-505）
- [x] `No owner-doc update required`（checkTableExists 为内部工具方法，docs-for-ai 无该细节章节；行为向"故障显式化"方向修正）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P2-07：parseDeltaModel fail-fast

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaModuleBizModel.java` + `TestNopMetaModuleBizModel.java`

- Item Types: `Decision | Fix | Proof`

- [x] **事务语义验证（Proof，前置）**：确认 `importOrmModel`（@BizMutation :158）在 fail-fast 抛错时 `orm().save(module)`（:173）会被事务回滚（Nop mutation 事务包装），或确认需要将 parseDeltaModel 前移到 save 之前。验证方式：先写失败测试（malformed delta），断言失败后无 `nop_meta_module` 残留行；若残留则裁定调整（前移 parse 或显式事务边界），不允许部分状态——**结论：GraphQL @BizMutation 事务包装覆盖整个 importOrmModel，`orm().save(module)` 随事务回滚**；`testImportOrmModelMalformedDeltaFailsFast` 断言失败后 NopMetaModule/NopMetaOrmModel 均 `total=0`（无部分状态），无需前移 parse
- [x] **错误码裁定（Decision）**：候选 (a) 新建 `ModuleErrors.ERR_MODEL_DELTA_PARSE_FAILED`（语义精确，param: path/resource）；(b) 复用 ERR_ORM_RESOURCE_READ_FAILED（语义偏"读失败"不精确）。推荐 (a)；裁定记录理由——**裁定 = 候选 (a) `ERR_MODEL_DELTA_PARSE_FAILED`**（`nop.err.metadata.module-delta-parse-failed`，param path/error，ModuleErrors.java:23-26）。理由：ERR_ORM_RESOURCE_READ_FAILED 语义限定"资源读取失败"（ModuleErrors.java:20 既有定义用于 loadFromResource 读取面），delta 解析失败发生在读取成功之后的解析阶段，复用会模糊根因
- [x] 按裁定落地（Fix）：`parseDeltaModel` catch Exception → 抛 `NopMetadataException(裁定码, e)` + param(resource/path)；**throw 前保留 LOG.warn（与 Phase 1/3 诊断约定一致）**；javadoc 同步（删除"降级 delta=full"表述，改为 fail-fast 语义）；`hasExtends==false` 的 `return fullModel` 路径保持（该路径 delta==full 语义本来成立）——NopMetaModuleBizModel.java:220-241（fail-fast + cause 保留 + LOG.warn 留证 + non-OrmModel 兜底抛错）
- [x] **判别性 fixture 构造规格（Proof，执行前置）**：full 加载（`importOrmModel:164` 的 `OrmModelLoader.loadFromResource`）先于 `parseDeltaModel`（:180）——XML 语法错误 / x:extends 基缺失会在 :164 全模型加载阶段就抛错，**永远到不了 parseDeltaModel**。**执行注记（原 duplicate-key 规格经实测不成立，已调整为实证窄窗口）**：delta 内部重复 key 在 merge 阶段 `ChildNodeMap.addByUniqueAttr`（ChildNodeMap.java:222）即抛 `ERR_XDSL_MULTIPLE_NODE_HAS_SAME_UNIQUE_ATTR_VALUE`——无论 base 是否含实体，full load 永远先死，**不存在 duplicate-key 窄窗口**（ScratchProbe 实测：`delta-dup-entity`/`dup-vs-base` 均 FULL FAIL + FILTERED FAIL）。**实测窄窗口 = partial-override**：base 含合法实体 BaseE + delta 部分覆盖 BaseE 且缺失 mandatory 属性 `tableName`（entity.xdef `tableName="!string"` 无默认值）→ full load merge 后 tableName 来自 base（合法）且 merge 后 `result.isValidated()=true` 跳过 XDslValidator（DslNodeLoader.loadFromNode:97-101）→ FULL OK；filtered 阶段 validator 直接检查 delta 原始节点 → `ERR_XDSL_ATTR_VALUE_IS_EMPTY`（probe 实测 FULL OK + FILTERED FAIL）→ 命中 parseDeltaModel。**判别器**：失败测试断言 errorCode == `ERR_MODEL_DELTA_PARSE_FAILED`（外加 `!errorCode.contains("nop.err.xlang")` 排除 full-load 阶段死）——若 fixture 意外失败在 full-load 阶段，错误码不同，测试自动红（预期可能先红一次属正常迭代，本次执行确实先红于旧 fixture，据实测调整后转绿）
- [x] **回归测试（Fix，判别性验证）**：(a) 按上述 fixture 规格构造 delta 资源（`delta-base-one-entity.orm.xml` + `delta-partial-override.orm.xml`）→ `importOrmModel` 抛裁定错误码（cause 保留）；(b) 失败后无 module/orm-model 残留（事务回滚验证，total=0）；(c) 批量 `importOrmModels` 传入 [有效路径, malformed-delta 路径] → per-path 记录失败（success=false + error 文本含裁定码子串），整批不中断、有效路径成功——新增 `testImportOrmModelMalformedDeltaFailsFast` + `testImportOrmModelsBatchMalformedDelta`（TestNopMetaModuleBizModel.java:130-190）；(d) 既有正路径（app.orm.xml 导入成功）不回归——**GraphQL errorCode 判别走 `GraphQLResponseBean.getErrorCode()`（top-level，由 addError 设置）；`getErrors()[0].getMessage()` 为 raw description 不含错误码，初版断言因此红过一次（判别器如实工作），已改为 getErrorCode()**
- [x] **无静默跳过（Fix 约束）**：无 delta=full 伪造降级；失败显式抛错；批量路径 per-path 显式记录

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 事务语义验证结论已记录（回滚覆盖 or 调整落地），无部分状态残留——**回滚覆盖（不调整落地）**：失败测试断言 module/orm-model 均 total=0
- [x] **端到端验证**：importOrmModel 入口 → parseDeltaModel → 显式错误码（单测 + GraphQL 批量入口断言）——`testImportOrmModelMalformedDeltaFailsFast`（getErrorCode 判别）+ `testImportOrmModelsBatchMalformedDelta`（per-path success=false + 错误码子串）
- [x] **无静默跳过**：delta 解析失败不再降级 fullModel；批量路径失败显式记录
- [x] 既有正路径导入测试不回归；`./mvnw test -pl nop-metadata -am -T 1C` 相关测试类全绿——**TestNopMetaModuleBizModel 13/13 绿；全量 919/0 绿**
- [x] `No owner-doc update required`（parseDeltaModel 为内部实现细节；fail-fast 方向与 R6.0 裁决一致）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - P2-09：提审失败 fail-loud

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTagLabelBizModel.java` + `MiscErrors.java`（或裁定文件）+ `TestNopMetaTagLabelApproval.java` / `TestNopMetaTagLabelApprovalIntegration.java`

- Item Types: `Decision | Fix | Proof`

- [x] **错误码裁定（Decision）**：新建 `MiscErrors.ERR_TAG_LABEL_SUBMIT_APPROVAL_FAILED`（param: tagLabelId）或复用现有码——推荐新建（现有 ERR_TAG_LABEL_* 仅 INVALID_LABEL_TYPE）；裁定记录理由——**裁定 = 新建 `ERR_TAG_LABEL_SUBMIT_APPROVAL_FAILED`**（`nop.err.metadata.tag-label-submit-approval-failed`，param tagLabelId/error，MiscErrors.java:110-114）。理由：现有 ERR_TAG_LABEL_* 仅 INVALID_LABEL_TYPE（语义为类型校验），提审失败是独立故障面，复用会稀释语义
- [x] **行为裁定（Decision）**：`triggerApprovalIfNeeded` 在 save()（@BizMutation）内调用——fail-loud 抛错时标签 save 整体回滚（用户看到错误，标签不落库）为预期语义；确认事务回滚覆盖 `super.save` + `saveOrUpdateEntity`（沿 Phase 2 事务验证方法）。`getWfNameFromMeta()==null` 早退路径保持（无 wf 配置时不提审，R3.6 已裁定）——**结论**：save 为 CREATE 语义，GraphQL @BizMutation 事务包装回滚覆盖 super.save + saveOrUpdateEntity（失败测试断言按 tagLabelId 查询无行，实证通过）；getWfNameFromMeta()==null 早退路径未改动
- [x] 按裁定落地（Fix）：`trySubmitForApproval` catch Exception → 抛 `NopMetadataException(裁定码, e)` + param(tagLabelId)；LOG.warn 保留在 throw 前——NopMetaTagLabelBizModel.java:124-140
- [x] **回归测试（Fix）**：(a) 正路径不回归——wfName 配置且提审成功 → save 成功 + approveStatus=SUBMITTED（既有 TestNopMetaTagLabelApproval:124-147 保持绿）；(b) **失败路径（判别性，确定性真实失败，零 mock）**：save 的 data 中预置 `approveStatus ∉ {null, UNSUBMITTED, REJECTED}`（如 APPROVED）的 Derived 标签 → `submitForApproval` XPL（approval-support.xbiz:19-26）抛 `nop.err.wf.approve.invalid-status` → save 抛裁定错误码（cause 保留）+ **标签不落库**（save 为 CREATE 语义，事务回滚后按 tagLabelId 查询无该行；若执行裁定改走 update 路径则断言 approveStatus/state 无变化）。**禁止**"wfName 指向不存在的工作流"方案：`wf:wfName="tagLabelConfirmApproval"` 硬编码于 nop-metadata-meta 的 xmeta 根属性，测试资源 delta 覆盖 xmeta 为全 test classpath 全局，会打挂同容器正路径测试（:124-147）——**执行注记（测试环境缺口修复）**：fail-loud 落地后既有正路径测试首次如实暴露"wf 启动失败"——autotest 环境无操作人（svcCtx.getUserId()=null），`ApprovalFlowHelper.start` 的 start-step `allowCallByUser` → `canBeDelegatedBy` → `DaoUserDelegateService.requireUser(null)` 抛 `nop.err.dao.unknown-entity`（旧代码吞掉此异常、approveStatus=SUBMITTED 先于 wf 启动设置，旧断言因此误判"提审成功"）。修复：两个 approval 测试类按仓库先例（AbstractWorkflowTestCase.newServiceContext）注入真实用户——`ensureUser()` 建 NopAuthUser 行 + `execute()` 经 `newGraphQLContext(request, svcCtx)` 携带 `UserContextImpl`（setUserContext，不污染线程局部 context）——正路径现为**真实提审成功**（日志无 submitForApproval failed 警告）；失败路径新增 `testDerivedLabelApprovalFailureFailsLoud`（getErrorCode 判别 + 无残留行断言）
- [x] **无静默跳过（Fix 约束）**：提审失败不再 LOG.warn 后继续；显式抛错让用户可见

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 错误码与行为裁定已记录（含 save 回滚语义确认）
- [x] **端到端验证**：save 入口（GraphQL NopMetaTagLabel__save）→ triggerApprovalIfNeeded → 提审失败（invalid-status 确定性路径）→ 显式错误码 + 标签不落库（判别性测试）；正路径 approveStatus=SUBMITTED 不回归——`testDerivedLabelApprovalFailureFailsLoud`（errorCode=nop.err.metadata.tag-label-submit-approval-failed + total 无行）+ `testDerivedLabelAutoSubmitsForApproval`（SUBMITTED，真实 wf 启动）
- [x] **无静默跳过**：提审失败显式抛错，用户侧可见
- [x] 既有 `TestNopMetaTagLabelApproval` / `TestNopMetaTagLabelApprovalIntegration` 正路径全部不回归——**8/8 + 4/4 绿；全量 919/0 绿**
- [x] `No owner-doc update required`（提审失败显式化与 R6.0 裁决一致，docs-for-ai 无提审细节章节）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 收口（arm-index 终态 + closure audit）

Status: completed
Targets: `ai-dev/audits/arm-index-nop-metadata.md` + `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`

- Item Types: `Fix | Proof`

- [x] arm-index §P2 对应行（P2-06/07/09）终态 = fixed + 本 plan 引用 + 修复摘要 + 测试证据——arm-index-nop-metadata.md:29-31（三行 fixed + plan-2026-08-06-0105-1 Phase 1/2/3 引用 + 修复摘要 + 测试证据）+ :16 MR6 段 R6.4 收口注 + header 最后更新
- [x] roadmap MR6 R6.4 行 → done（注明 plan 引用 + 测试计数）——roadmap R6.4 行 → done（plan-2026-08-06-0105-1 引用 + 919/0）+ header v21
- [x] 独立子 agent closure audit（fresh session）逐项核对 Phase Exit Criteria + Closure Gates，证据写入本 plan Closure 段——closure audit session `ses_02cc36f7cffeWa7u3Mkp19uNAj`，21 项代码/测试/终态/anti-hollow 核查全 PASS；audit 发现 2 项文档缺陷（本 Phase 4 未勾选 + 日志缺 R6.4 条目）已由执行者补齐后复验（下述 check 工具全 0 即复验结果）；evidence 见 Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）——0（closure 时最终复跑）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（涉及 arm-index/roadmap 变更后）——0（No errors found，2026-08-06 跑）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] arm-index + roadmap 终态一致可追溯（P2-06/07/09 三行 fixed）
- [x] 独立 closure audit PASS，evidence 已写入本 plan Closure 段
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（0 failures；含 R6.3 口径 caveat：全量 `-am` 偶遇预存在 rocksdb 性能 flaky 时按单跑复绿口径降级记录，见 Closure Gates）——**919 tests / 0 failures / 0 errors / 0 skipped**，BUILD SUCCESS；本跑全量 `-am` 未遇 rocksdb 性能 flaky，无需降级口径
- [x] 无静默降级：三项正确性 finding 为 fixed，无 live defect 被降级——Deferred But Adjudicated 段仅 watch-only/out-of-scope 项（P2-07 per-path errors 列表化 out-of-scope improvement + P2-06 大小写变体异常跳过 watch-only residual）
- [x] `ai-dev/logs/` 对应日期条目已更新——`ai-dev/logs/2026/08-06.md` 顶部新增 R6.4 收口条目（reverse chronological）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] P2-06：checkTableExists SQLException 显式抛裁定错误码（含 cause），不再归类为"表不可见"——AggregationHelper.java:501-509（LOG.warn + throw NopMetadataException(ERR_AGGR_TABLE_VISIBILITY_CHECK_FAILED, e) + param schema/tableName；仅空结果返回 false）；TestAggregationHelperTableVisibility 5/5
- [x] P2-07：parseDeltaModel fail-fast（含 cause），无 delta=full 伪造降级，无部分状态残留——NopMetaModuleBizModel.java:220-241（catch → throw + cause；non-OrmModel 兜底抛错；无 return fullModel 降级）；判别性测试断言失败后 module/orm-model 均 total=0
- [x] P2-09：提审失败显式抛错（含 cause），用户可见，无标签静默成功不入审批流——NopMetaTagLabelBizModel.java:126-139（catch → throw + cause + param tagLabelId）；判别性测试断言错误码 + 标签不落库
- [x] 必要 focused verification 已完成（三处判别性测试 + 既有正路径不回归）——TestAggregationHelperTableVisibility 5/5 + TestNopMetaModuleBizModel 13/13（含 2 新增判别性测试）+ TestNopMetaTagLabelApproval 8/8 + Integration 4/4（含 1 新增判别性测试）；919/0 全绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（Deferred But Adjudicated 段仅 watch-only/out-of-scope 项）——两项均 non-blocking（见段内 Why Not Blocking Closure）
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required——arm-index + roadmap 已更新；三 Phase 均 `No owner-doc update required`（内部工具方法/内部实现细节，docs-for-ai 无对应章节）
- [x] 独立子 agent closure-audit 已完成并记录证据（fresh session，见 Closure 段）——`ses_02cc36f7cffeWa7u3Mkp19uNAj` PASS，evidence 见 Closure 段
- [x] **Anti-Hollow Check**：closure audit 已验证（a）抛错路径在真实调用链上生效（importOrmModel:180 → parseDeltaModel / save → triggerApprovalIfNeeded → trySubmitForApproval → submitForApproval XPL（approval-support.xbiz:19-26）/ MixedSameDbJoinAggregationProcessor.execute:69 → checkEntityTableVisible:165-176 → withConnection lambda:173 → isEntityTableVisible → checkTableExists），（b）无空方法体/静默跳过/no-op 作为正常实现——三 catch 均 LOG.warn + throw；scan-hollow 0 发现
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿 —— **口径 caveat（沿 R6.3 先例）**：909/0 基线为 `-pl nop-metadata/nop-metadata-service -am` 口径；全量 `-pl nop-metadata -am` 偶遇预存在 rocksdb 性能 flaky（`TestRocksDBIncrementalRestoreAndBenchmark` ratio）时按"单跑复绿 + 记录非本 plan 引入"降级口径，不得判 gate 假红——**本次全量 `-pl nop-metadata -am`：919/0 全绿，未遇 flaky，无需降级口径**
- [x] checkstyle / 代码规范检查通过（nop-metadata 无独立 checkstyle 命令，以 mvn 构建默认检查为准）——mvn 构建默认检查通过（BUILD SUCCESS）；导入分组按仓库惯例（io.nop.* 组内排序已核对）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）——0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（closure 时）——0（无 high/critical 发现）

## Deferred But Adjudicated

### P2-07 批量路径 per-path errors 列表化（审计建议的替代方案）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 审计原建议"记入 importOrmModels 的 per-path errors 列表"——既有批量实现已按 per-path catch（importOrmModels:364-371）记录失败，fail-fast 抛错被批量层捕获即为等效语义；无需额外 errors 列表数据结构
- Successor Required: `no`
- Successor Path: —

### P2-06 大小写变体探测在异常时的跳过

- Classification: `watch-only residual`
- Why Not Blocking Closure: 首个 getTables 探测抛错即 fail-fast（重试大小写变体无意义）；正常"表不存在"场景仍做三连探测，语义不变
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- 本 plan 新增错误码（如 ERR_AGGR_TABLE_VISIBILITY_CHECK_FAILED / ERR_MODEL_DELTA_PARSE_FAILED / ERR_TAG_LABEL_SUBMIT_APPROVAL_FAILED）需在 R6.6 P2-03 死码清理时列入"use 侧有引用"核验，防止误删
- 工作树提交由 mission 流程/用户决定（本 plan 执行不代提交）

## Closure

Status Note: 完成。三处静默降级全部 fail-loud（显式错误码 + cause 保留），回归测试钉死新语义，既有正路径不回归；独立子 agent closure audit PASS；`./mvnw test -pl nop-metadata -am -T 1C` 919/0 全绿；工具全 0。
Completed: 2026-08-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，general，task `ses_02cc36f7cffeWa7u3Mkp19uNAj`；未复用本 plan 执行 session，只读核查未改文件）
- Evidence:
  - **Phase 1（P2-06）5/5 PASS**：AggregationHelper.java:501-509 catch SQLException → LOG.warn(:504-505) + throw NopMetadataException(ERR_AGGR_TABLE_VISIBILITY_CHECK_FAILED, e)(:506, cause 保留) + param schema/tableName(:507-508)；空结果 return false(:510)、有行 return true(:498-499)；AggregationErrors.java:76-80 错误码 + NopMetadataArgs.java:24 ARG_SCHEMA；TestAggregationHelperTableVisibility 5 个 @Test（SQLException→errorCode+cause assertSame / 空→false / 有行→true / 首探错误 verify(times(1)) / execute() 端到端 doAnswer lambda 传播）；接线核实 MixedSameDbJoinAggregationProcessor.java:165-176 checkEntityTableVisible → withConnection(:171) → isEntityTableVisible(:173)，真实 MetaDataSourceConnectionProcessor.withConnection:122-135 仅 catch SQLException，NopMetadataException 直接上抛
  - **Phase 2（P2-07）6/6 PASS**：NopMetaModuleBizModel.java:220-241（!hasExtends → return fullModel 保持 :221-222；catch → LOG.warn(:233-234) + throw + cause + param path(:235-236)；non-OrmModel 兜底抛错 :238-240）；ModuleErrors.java:23-26 错误码；importOrmModel save(:176) 先于 parseDeltaModel(:180)（回滚依赖确认）；fixture delta-base-one-entity + delta-partial-override 在位（缺失 mandatory tableName → FULL OK / FILTERED FAIL 窄窗口）；testImportOrmModelMalformedDeltaFailsFast（errorCode 判别 + !nop.err.xlang + module/orm-model total=0）+ testImportOrmModelsBatchMalformedDelta（per-path success=false + 错误码子串 + 整批不中断）；批量 per-path catch :371-377（success=false + toErrorMessage + clearSession）
  - **Phase 3（P2-09）5/5 PASS**：NopMetaTagLabelBizModel.java:126-139（catch → LOG.warn(:134-135) + throw + cause + param tagLabelId(:136-137)）；MiscErrors.java:110-114 错误码；getWfNameFromMeta()==null 早退 :89-90 保持；testDerivedLabelApprovalFailureFailsLoud（approveStatus=APPROVED 预置 → errorCode 判别 + 标签不落库 getEntityById==null，零 mock）；正路径 testDerivedLabelAutoSubmitsForApproval（SUBMITTED）+ 真实用户上下文（ensureUser NopAuthUser + UserContextImpl via newGraphQLContext(request, svcCtx)）
  - **Phase 4 2/2 PASS**：arm-index §P2 P2-06/07/09 三行 fixed（plan 引用 + 摘要 + 证据）+ roadmap R6.4 done + header v21；审计另发现 2 项文档缺陷（Phase 4 未勾选 / 日志缺条目）→ 执行者补齐后复验：check-plan-checklist --strict 0、check-doc-links --strict 0、scan-hollow --severity high 0
  - **Anti-Hollow 检查 PASS**：三抛错路径在真实调用链上（importOrmModel:180→parseDeltaModel:220 / save:77→triggerApprovalIfNeeded:83→trySubmitForApproval:103→approval-support.xbiz:19-26 invalid-status / MixedSameDbJoinAggregationProcessor.execute:69→checkEntityTableVisible:165-176→withConnection lambda:173→checkTableExists）；三 catch 均 LOG.warn + throw（无空方法体/静默跳过）；scan-hollow-implementations.mjs --module nop-metadata --severity high 退出码 0
  - **Deferred 项分类检查 PASS**：Deferred But Adjudicated 2 项 = out-of-scope improvement（P2-07 per-path errors 列表化，批量层 per-path catch 已等效）+ watch-only residual（P2-06 大小写变体异常跳过，首探失败即 fail-fast），无 in-scope live defect 被降级
  - **checklist 完整性**：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-metadata-audit-remediation/2026-08-06-0105-1-r6-4-fail-loud-error-surfacing.md --strict` 退出码 0（全部 checklist 勾选 + Closure Evidence 已写入）
  - 构建验证：`./mvnw test -pl nop-metadata -am -T 1C` → BUILD SUCCESS **919 tests / 0 failures / 0 errors / 0 skipped**（service 916 + web 3；909 baseline + 新增 8 判别性测试；全量 `-am` 未遇 rocksdb flaky）

Follow-up:

- 本 plan 新增错误码（ERR_AGGR_TABLE_VISIBILITY_CHECK_FAILED / ERR_MODEL_DELTA_PARSE_FAILED / ERR_TAG_LABEL_SUBMIT_APPROVAL_FAILED）需在 R6.6 P2-03 死码清理时列入"use 侧有引用"核验，防止误删（Non-Blocking Follow-ups 段已登记）
- 工作树提交由 mission 流程/用户决定（本 plan 执行不代提交）
- 无其他 plan-owned 剩余工作
