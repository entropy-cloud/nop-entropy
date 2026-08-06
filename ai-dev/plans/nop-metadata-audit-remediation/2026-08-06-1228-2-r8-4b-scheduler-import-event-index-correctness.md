# R8.4b 调度/导入/事件/索引正确性组修复（AR-17, AR-23①②⑨⑩）

> Plan Status: completed
> Last Reviewed: 2026-08-06
> Mission: nop-metadata-audit-remediation
> Work Item: MR8（R8.4b 调度/导入/事件/索引正确性组）
> Source: `ai-dev/audits/2026-08-05-2157-open-audit-nop-metadata-audit-remediation.md`（AR-17、AR-23①②⑨⑩）、`ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MR8 段 R8.4 行 + R8.0 裁决记录）、`ai-dev/audits/arm-index-nop-metadata.md`（R8.0 裁决记录表）
> Related: 执行顺序 `{2}` of 2（R8.4a 与 R8.4b 无共享代码文件——两份 plan 的收口 Phase 均更新 roadmap R8.4 行 / arm-index §P2，顺序执行下无冲突）；启动门禁：R8.0 done（已满足）。

## Purpose

修复调度/导入/事件/索引域的 5 个已确认正确性缺陷（AR-17 syncExternalTables 原子性契约漂移 + 失败事件缺失、AR-23① 删除先摘 cron 后提交、AR-23② 索引重建不清陈旧文档、AR-23⑨ manifest 跨 DRAFTING 模块解析、AR-23⑩ 事件快照 Map 分支跳过敏感列脱敏，R8.0 全部提级为 P1 修复）：删除失败不再丢调度、重建后不再返回幽灵文档、manifest 不再解析到 DRAFTING 模块、Map 快照不再绕过脱敏、syncExternalTables 部分持久化语义文档化且失败路径事件可追踪。产出 = 代码修复 + 判别性回归测试 + docs 同步 + arm-index/roadmap 终态。

## Current Baseline

经 2026-08-06 live repo 核对（finding 描述以审计报告为准；行号以 live 复核为准）：

- 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` **1016/0**（R8.3 收口口径）
- **AR-17**（`NopMetaDataSourceBizModel.java`，R6.3 改造后语义未文档化）：
  - `:508-519` `upsertExternalTableGuarded`：per-key 锁 + `REQUIRES_NEW` 独立事务，每表独立提交——中途失败时已同步表持久化（部分持久化成为既定行为）
  - `:171-219` `syncExternalTables`：scan 级异常（`structureReader.read` 抛出）时异常向上传播且 `:207-213` 的 `publishEventWithSnapshots` 不执行（事件缺失，下游失同步）；per-table 失败收集 errors 不中断
  - **事务语义（live 复核关键）**：BizModel mutation 整体包在 `TransactionActionDecorator.runInTransaction`（nop-service-framework/nop-biz/src/main/java/io/nop/biz/decorator/TransactionActionDecorator.java:45）；`publishEventWithSnapshots` 把事件行存进**外层会话**——若修复方案"scan 失败 → try/catch → 发布事件 → 重抛"，重抛使外层事务回滚、事件行随之消失（空壳修复）；必须显式裁定事件发布的事务隔离（REQUIRES_NEW 包发布，沿同文件 `upsertExternalTableGuarded` 先例）
  - 快照语义：before/after snapshot 是 **dataSource 实体自身状态**（sync 期间 dataSource 实体不变，before/after 实际等同）——事件价值是"sync 尝试发生 + 已部分持久化"的下游通知，不是实体 diff
  - **测试缝（live 复核）**：`structureReader` 是 `private final` 内联 new（:86），不可注入；`TestNopMetaDataSourceBizModel` 为 JunitBaseTestCase 真实装配（无 @Mock）——"mock structureReader 抛错"不成立，需裁定失败注入机制
- **AR-23①**（`NopMetaQualityCheckpointBizModel.java`）：
  - `:277-281` `delete` override：先 `notifySchedulerUnregister(id)`（:279）再 `super.delete(id, context)`（:280）——若 super.delete 失败（方法内异常），cron 已摘除但检查点仍在，调度静默丢失；`notifySchedulerUnregister` 自身有 try/catch + LOG.warn（:295-304，日志键 `unregister-before-delete-failed`）
  - **残余窗口（live 复核）**：外层事务 commit 在 action 返回后由 decorator 执行——swap 后 unregister 仍在 commit 前，commit 阶段失败仍是"cron 已摘除但行存在"（残余，需显式声明）
  - **测试缝（live 复核）**：orm.xml 中 NopMetaQualityCheckpoint 无指向它的 FK（NopMetaModule 对其 to-many cascadeDelete），且 `CrudBizModel.delete` 对不存在 id 返回 true 静默（:1073-1075）——自然删除失败缝不存在；**正确注入点 = `CrudBizModel.doDelete`（@BizAction，CrudBizModel.java:1066）**：`Mockito.spy(checkpointBizModel)` + `doThrow().when(spy).doDelete(...)`——真实 `delete` override 方法体会执行（old 代码先 unregister 后 super.delete 抛 → RED；new 代码先抛、unregister 不执行 → GREEN），而 doThrow 打在 `delete` 本身上是空心测试（spy 拦截后真实方法体不执行，两版本都不调 unregister，恒绿）；须配真实 LocalJobScheduler 观察 job 存留
  - 兜底语义（MA7.5-01）：`MetaQualityCheckpointScheduler.executeScheduledCheckpoint` catch-all（:213-228）对 `ERR_CHECKPOINT_NOT_FOUND` 存活处理——unregister 失败残留 cron 不会使 job 转 FAILED，但每个 tick 打 ERROR 日志（噪音，需显式声明为已接受成本）
- **AR-23②**（`NopMetaIndexBuilder.java`）：
  - `:39-124` `buildFullIndex`：只 `addDocs` 不 purge——重建前不清理陈旧文档，被删除实体文档永久残留，重建后搜索返回幽灵结果
  - `ISearchEngine` API（nop-search-api）：`removeTopic(topic)`（:41）、`removeDocs(topic, docIds)`（:39）、`getDocsByTerm(topic, term)`（:25）、`search(request)`（:21）
  - docs 以 tagSet=entityType 标记（NopMetaIndexBuilder:140 等），`searchMetadata` 按 tag 过滤（NopMetaSearchBizModel.java:79）；`rebuildSearchIndex` GraphQL 入口 `NopMetaSearchBizModel.java:52-58` 支持部分类型重建（entityTypes 子集）
  - **枚举机制（live 复核）**：`getDocsByTerm` 只查 FIELD_CONTENT 分词（LuceneSearchEngine:399-441），**不能**按 tag 枚举——plan 的 getDocsByTerm 路径是死路；唯一可行是空 query + tags 过滤的 search（LuceneSearchEngine:831-879 支持），但 SearchRequest 无分页，枚举被 limit 截断——超限语义需裁定
  - **测试基建（live 复核）**：`TestNopMetaIndexBuilder` 为 @Mock ISearchEngine（Mockito verify 调用，无真实引擎效果断言）；仓库内无真实 Lucene 引擎集成测试先例——判别性测试需声明 mock 验证 + 正向行为断言的组合，或新建临时目录真实引擎测试
- **AR-23⑨**（`NopMetaModuleBizModel.java`）：
  - `:618-648` `buildGlobalClassNameToModuleId`：`:625` `moduleDao.findAll()` 无 status 过滤——DRAFTING 模块的实体也进入 className→moduleId 索引，RELEASED 模块 manifest 可解析到 DRAFTING 模块实体；常量 `MODULE_STATUS_DRAFTING` / `MODULE_STATUS_RELEASED`（`_NopMetadataCoreConstants.java:9/:14`）
  - **测试陷阱（live 复核）**：同模块 reimport 场景（DRAFTING v2 与 RELEASED v1 共享 moduleId）下两行 put 到 classNameToModuleId 的 value 相同——"只映射 RELEASED"断言修复前后恒绿（空心测试）；判别性测试必须限定**不同 moduleId** 的两个模块（一 DRAFTING 一 RELEASED，last-writer-wins 不确定），或经 MetaManifestBuilder 解析为 `unresolved:` 标记（:30/:101）
- **AR-23⑩**（`MetaModelChangedEventPublisher.java`）：
  - `:202-203` `buildEntitySnapshot` Map 分支：`new LinkedHashMap<>(map)` 原样拷贝——不应用 `SENSITIVE_COLUMN_FALLBACK` 脱敏（ORM 分支 :188-191 有脱敏）；`publishEvent`/`buildSnapshot` 参数为 `Object`，调用方传 Map 可合法绕过脱敏契约；`:74` `SENSITIVE_COLUMN_FALLBACK` 列名集 + `:213-224` `isSensitiveColumn` 已有（**大小写敏感**——`SENSITIVE_COLUMN_FALLBACK.contains(name)` 为 HashSet 精确匹配）
  - **既有测试冲突**：`TestMetaModelChangedEventPublisherSecurity.testMapEntityPathUnchanged`（:196-204）注释断言"Map 路径不受影响（caller responsibility）"——与修复意图正面冲突，需更新
- docs-for-ai 模块文档：`docs-for-ai/03-modules/nop-metadata.md` §失败路径（:210-216）只声明 per-row 隔离未声明原子性变化——AR-17 必须同步；其余 4 项复核后再裁定

## Goals

- AR-17：syncExternalTables 的 per-table REQUIRES_NEW 部分持久化契约显式文档化（docs-for-ai §失败路径 + design 同步）；scan 级失败事件行为裁定并落地——事件发布须经 REQUIRES_NEW 独立事务（沿 :512-517 先例），否则重抛后随外层回滚消失（空壳）；判别性测试
- AR-23①：delete 顺序修正——删除成功后才摘 cron（或等效补偿逻辑），方法内删除失败时调度保留；残余 commit 窗口显式声明；日志键语义更新；判别性测试 red→green
- AR-23②：buildFullIndex 重建前清理对应 entityType 陈旧文档（全量重建 topic 级 / 部分重建类型级枚举清理）；清理语义与"重建后非删除实体仍可搜索"正向断言并存；判别性测试 red→green
- AR-23⑨：buildGlobalClassNameToModuleId 排除 DRAFTING 模块（status 过滤）；判别性测试（不同 moduleId 场景）red→green
- AR-23⑩：buildEntitySnapshot Map 分支应用敏感列脱敏（fallback 列名集，大小写语义与 ORM 分支对齐）；既有冲突测试更新；判别性测试 red→green
- 每个修复带判别性回归测试（red 先于修复实测或至少行为断言可捕获回归）；收口更新 roadmap MR8 R8.4 行（整体 done）+ arm-index §P2

## Non-Goals

- 不处理 R8.4a 组 finding（AR-20/21/22——由 plan 2026-08-06-1228-1 承接）
- 不处理 R8.1/R8.2/R8.3 已收口项
- 不改变 syncExternalTables 的事务隔离机制本身（REQUIRES_NEW 是 R6.3 已裁定方案，本 plan 只收口语义文档化 + 失败事件面）
- 不做分布式锁/多实例调度扩展（R4.3 已裁定单实例 baseline）
- 不重设计事件发布机制（只在既有 publishEventWithSnapshots 路径上补失败面 + 事务隔离）
- 不改 ORM 模型 / 不改 api DTO（除错误码外无公共面变更预期）
- 不新增搜索枚举分页 API（SearchRequest 无分页是平台现状，枚举超限语义在 plan 内裁定）

## Scope

### In Scope

- `NopMetaDataSourceBizModel.java`（AR-17 + 相关测试）
- `NopMetaQualityCheckpointBizModel.java`（AR-23① + 相关测试）
- `NopMetaIndexBuilder.java`（AR-23② + 相关测试）
- `NopMetaModuleBizModel.java`（AR-23⑨ + 相关测试）
- `MetaModelChangedEventPublisher.java`（AR-23⑩ + 相关测试）
- 错误码文件（若裁定新增，沿 R8.2 先例：既有 Errors 文件，不建新文件）
- `docs-for-ai/03-modules/nop-metadata.md`（AR-17 原子性契约段必须同步）
- `ai-dev/design/nop-metadata/`（若 AR-17 契约影响 design 文档则同步）
- `TestMetaModelChangedEventPublisherSecurity.java`（AR-23⑩ 冲突测试更新）
- `ai-dev/audits/arm-index-nop-metadata.md` §P2 + roadmap MR8 段 R8.4 行（整体 done）

### Out Of Scope

- R8.4a 组（AR-20/21/22）
- 事务隔离机制改造（REQUIRES_NEW → 全量原子/分布式事务）
- 事件发布架构重设计（事件表、幂等消费）
- 调度器多实例/分布式锁（R4.3 已裁定）
- 搜索索引 schema/打分逻辑改进

## Execution Plan

### Phase 1 - AR-23① 删除先提交后摘 cron

Status: completed
Targets: `NopMetaQualityCheckpointBizModel.java` + 相关测试

- Item Types: `Fix | Decision | Proof`

- [x] `delete` override 顺序修正：先 `super.delete(id, context)`（成功）再 `notifySchedulerUnregister(id)`（:279 移至 :280 之后）；若 super.delete 抛异常，notifySchedulerUnregister 不执行（cron 保留，调度不丢失）；unregister 自身 try/catch + LOG.warn 保持；日志键 `unregister-before-delete-failed` → `unregister-after-delete-failed`（语义同步，沿仓库日志键前缀惯例）
- [x] 裁定（Decision）：残余窗口声明——外层事务 commit 在 action 返回后由 decorator 执行，swap 后 unregister 仍在 commit 之前：commit 阶段失败时仍是"cron 已摘除但检查点行存在"。裁定：接受为残余（commit 失败概率低、executeScheduledCheckpoint 对残留 cron 存活兜底、调度器 init() @PostConstruct 重注册自愈），显式记录于 plan + arm-index；不引入 commit 后置回调（超 scope）
- [x] 裁定（Decision）：残留 cron 噪音——unregister 失败时残留 cron 每 tick 对已删检查点打 ERROR（:213-228 存活兜底，不转 FAILED）：显式声明为已接受成本（沿 MA7.5-01 存活语义），不额外抑制日志
- [x] 判别性测试（**Mockito spy 方案，注入点 = doDelete**）：`Mockito.spy(checkpointBizModel)` + `doThrow().when(spy).doDelete(...)`（**不打在 `delete` 本身上**——spy 拦截 delete 会使真实 override 体不执行，两版本都不调 unregister，空心）+ 真实 LocalJobScheduler——(i) doDelete 抛错 → `notifySchedulerUnregister` 不被调用（cron 保留，修复前先摘后删实测 red）；(ii) 删除成功 → unregister 被调用（keep-green）；(iii) 既有调度测试不回归
- [x] 回归：`TestNopMetaQualityCheckpointBizModel` / `TestMetaQualityCheckpointScheduler` / `TestMetaQualityCheckpointSchedulerCronReadFailure` / 调度 e2e 全绿

Exit Criteria:

- [x] 方法内删除失败时调度不丢失（判别性测试实证，**接线验证**：spy 确认 unregister 调用时序）（Minimum Rules #23）
- [x] 删除成功路径 unregister 仍执行（keep-green）
- [x] **无静默跳过**：删除失败异常正常传播（fail-loud），不吞错（Minimum Rules #24）
- [x] 残余窗口 + 日志噪音裁定记录于 plan + arm-index（可追溯）
- [x] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 2 - AR-23② 索引重建前清理陈旧文档

Status: completed
Targets: `NopMetaIndexBuilder.java` + `NopMetaSearchBizModel.java`（如需）+ 相关测试

- Item Types: `Fix | Decision | Proof`

- [x] 裁定（Decision）：清理粒度——全量重建（entityTypes 为 null/全类型）用 `removeTopic(TOPIC)` 循环前一次（不 per-type 循环内多次，避免真实引擎清掉前一个类型刚写入的文档）；部分重建（entityTypes 子集）按类型级清理：空 query + tags 过滤的 search 枚举该类型现有 docId → `removeDocs(TOPIC, docIds)`（**getDocsByTerm 是死路**——只查 FIELD_CONTENT 分词不能按 tag 枚举，明确排除）；枚举超限裁定：SearchRequest 无分页——枚举 limit 设为显式上限（如 10000，沿 CrossDbConfigHolder.maxCrossDbRows 常量先例），超限部分记录残余（真实场景类型文档数 << 上限，声明为 watch-only residual）——裁定记录于 plan + arm-index
- [x] `buildFullIndex` 在 addDocs 前执行清理（按裁定）；`IndexResult` 语义保持（indexed/failed 如实反映）
- [x] 判别性测试：(i) Mock ISearchEngine——全量重建 → `removeTopic` 恰好调用 1 次且先于任何 `addDocs`（修复前仅 addDocs 实测 red）；(ii) 部分重建 → 仅目标类型 docId `removeDocs` 被调用、非目标类型不受影响；(iii) **正向断言（防假绿）**：Mock 引擎记录状态（in-memory fake ISearchEngine 实现或 verify 组合）——重建后"已删除实体不在新索引、未删除实体仍在"（负向 + 正向双向断言，避免只验证调用不验证效果）
- [x] 回归：`TestNopMetaIndexBuilder`（Mock 环境）+ `NopMetaSearchBizModel` 相关测试全绿

Exit Criteria:

- [x] 重建前清理已接线（判别性测试实证，Mock verify 调用顺序 + 效果双向断言）
- [x] 部分重建语义正确（非目标类型文档保留，判别性测试实证）
- [x] 无静默跳过：清理失败显式反映在 IndexResult（沿 R8.2 AR-23③ 先例）而非吞掉（Minimum Rules #24）
- [x] 枚举机制 + 超限裁定记录于 plan + arm-index（可追溯）
- [x] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 3 - AR-23⑨ manifest 排除 DRAFTING 模块

Status: completed
Targets: `NopMetaModuleBizModel.java` + 相关测试

- Item Types: `Fix | Proof`

- [x] `buildGlobalClassNameToModuleId`（:618-648）：`:625` `moduleDao.findAll()` 改为带 status 过滤的查询——排除 DRAFTING（`not-eq(status, MODULE_STATUS_DRAFTING)` 或 `eq(status, MODULE_STATUS_RELEASED)` 二选一裁定）；**裁定注意 DEPRECATED 分叉**：`MODULE_STATUS_DEPRECATED` 常量存在（_NopMetadataCoreConstants）——`eq(RELEASED)` 会把 DEPRECATED 模块实体也剔出索引（manifest 解析变 unresolved），`not-eq(DRAFTING)` 保留 DEPRECATED；默认倾向 `not-eq(DRAFTING)`（DEPRECATED 元数据仍应可解析，audit 只点名 DRAFTING），裁定记录于 plan + arm-index
- [x] 判别性测试（**不同 moduleId 场景，避免同模块 reimport 空心**）：(i) 模块 A（moduleId=A，RELEASED）与模块 B（moduleId=B，DRAFTING）各含 className X → 索引仅映射 A（修复前 last-writer-wins 不确定/可能 B，实测 red 或断言确定性）；(ii) 仅 DRAFTING 模块含 X → X 不可解析（null/跳过，或经 MetaManifestBuilder 解析为 unresolved 标记断言）；(iii) RELEASED 正常解析（keep-green）
- [x] 回归：`TestNopMetaModuleBizModel` / `TestNopMetaModuleImportConsistency` / manifest 生成 e2e 全绿

Exit Criteria:

- [x] DRAFTING 模块实体不进入全局 className 索引（判别性测试实证，不同 moduleId 场景）
- [x] RELEASED 模块解析语义不变（keep-green）
- [x] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 4 - AR-23⑩ 事件快照 Map 分支脱敏

Status: completed
Targets: `MetaModelChangedEventPublisher.java` + `TestMetaModelChangedEventPublisherSecurity.java` + 相关测试

- Item Types: `Fix | Decision | Proof`

- [x] 裁定（Decision）：Map 分支脱敏的大小写语义——**与 ORM 分支对齐（大小写敏感，`SENSITIVE_COLUMN_FALLBACK.contains(key)` 精确匹配）**，不引入大小写不敏感语义（避免 ORM 分支与 Map 分支脱敏语义分叉）；Map 无 column model，仅 fallback 列名集可应用——裁定记录于 plan + arm-index
- [x] `buildEntitySnapshot` Map 分支（:202-203）：遍历 Map key，命中 `SENSITIVE_COLUMN_FALLBACK`（大小写敏感，与 ORM 分支一致）时值替换为 `REDACTED_VALUE`；ORM 分支行为不变
- [x] 判别性测试：`TestMetaModelChangedEventPublisherSecurity`——(i) Map 含敏感 key（如 connectionConfig/password 族 fallback 名）→ 快照值为 REDACTED_VALUE（修复前原样拷贝实测 red）；(ii) 非敏感 key 原样保留；(iii) 大小写变体（如 "Password"）不脱敏（与 ORM 分支一致，keep-red 负例钉死语义）；(iv) **`testMapEntityPathUnchanged` 按裁定更新**（原"Map 路径不受影响"断言 → 改为脱敏后断言，re-adjudication 记录）；(v) ORM 实体分支不回归（既有断言 keep-green）
- [x] 回归：`TestMetaModelChangedEventPublisherSecurity` / 事件发布 e2e 全绿

Exit Criteria:

- [x] Map 分支脱敏已接线（判别性测试实证，含 fallback 列名集命中 + 大小写语义钉死）
- [x] 非敏感字段/ORM 分支不回归；既有冲突测试已按裁定更新（re-adjudication 记录）
- [x] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 5 - AR-17 syncExternalTables 契约文档化 + 失败事件面

Status: completed
Targets: `NopMetaDataSourceBizModel.java` + `docs-for-ai/03-modules/nop-metadata.md` + design 文档 + 相关测试

- Item Types: `Fix | Decision | Proof`

- [x] 裁定（Decision）：scan 级失败事件行为——推荐路径 (a)：scan 级异常时仍发布事件再重新抛出原异常；**事件发布必须经 REQUIRES_NEW 独立事务**（沿同文件 `upsertExternalTableGuarded` :512-517 先例），否则重抛使外层事务回滚、事件行随之消失（空壳）——即"事件缺失面"关闭 = 事件行独立提交存活；替代路径 (b)：显式不发布 + 文档记录（理由：dataSource 实体未变更、ENTITY_UPDATED 事件语义不成立）；两者二选一，裁定记录于 plan + arm-index；推荐 (a)（R8.0 裁决记录原文"修复成本低" + 下游失同步面关闭）
- [x] 裁定（Decision）：失败注入机制（`structureReader` 为 private final 内联 new，无 mock 缝）——(i) 真实入口坏连接（withConnection 抛错，属"连接中断"扫描面；try/catch 需包住**整个 withConnection 块**而非仅内部 read——界定范围）；(ii) 反射替换 private final 字段（测试基建）；(iii) 生产代码加注入缝（超出 plan 描述改动面，仅在前两者不可行时启用，plan-first 声明）——裁定记录于 plan + arm-index
- [x] 按裁定实施：scan 级异常处理（try/catch 包 withConnection 块 → 错误收集 → 事件发布（REQUIRES_NEW）→ 重抛原异常，或按裁定 (b) 显式记录）；per-table 失败路径保持 errors 收集 + 事件发布（现状已发布）
- [x] `docs-for-ai/03-modules/nop-metadata.md` §失败路径：声明 per-table REQUIRES_NEW 独立提交的部分持久化契约（已同步表持久化、失败表收集 errors）+ scan 级失败事件行为（按裁定，含 REQUIRES_NEW 事件发布语义）；design 文档同步（如 `ai-dev/design/nop-metadata/` 中 syncExternalTables 契约段）
- [x] 判别性测试（按失败注入裁定）：scan 级失败（坏连接真实入口或反射注入）→ 事件仍发布且独立提交存活（修复前事件缺失实测 red）+ 原异常仍抛出；per-table 部分失败 → 已同步表持久化 + errors 收集 + 事件发布（keep-green）
- [x] 回归：`TestNopMetaDataSourceBizModel` / 外部表同步 e2e 全绿

Exit Criteria:

- [x] 契约已文档化（docs-for-ai §失败路径 + design 同步，repo-observable：文档段落存在且与代码行为一致）
- [x] scan 级失败事件行为已裁定并落地（判别性测试实证；事件发布 REQUIRES_NEW 存活实证——事件行不随外层回滚消失）
- [x] **端到端验证**：syncExternalTables 失败场景（scan 级 + per-table 级）经真实入口完整走通（Minimum Rules #22）
- [x] **无静默跳过**：失败路径显式处理（发布/重抛/记录），不空 catch（Minimum Rules #24）
- [x] 两项裁定记录于 plan + arm-index（可追溯）
- [x] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 6 - 收口

Status: completed
Targets: roadmap MR8 段 + arm-index §P2 + 全量验证

- Item Types: `Fix | Proof`

- [x] roadmap MR8 段 R8.4 行 → done（5 项 finding 终态 + R8.4a 子项引用 + 测试计数基线变化）
- [x] arm-index §P2 AR-17 / AR-23① / AR-23② / AR-23⑨ / AR-23⑩ → fixed（含修复 commit 引用）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` exit 0
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（记录计数基线）
- [x] 独立子 agent closure audit（fresh session）PASS + Closure 段证据写入

Exit Criteria:

- [x] roadmap MR8 段与 arm-index §P2 双向一致（5 项逐条可追溯）+ R8.4 行整体 done
- [x] 全量测试通过（0 failures/errors/skipped）+ 工具验证 exit 0
- [x] 独立 closure audit READY_TO_CLOSE（含 Anti-Hollow 调用链追踪）
- [x] `ai-dev/logs/2026/08-06.md` 已更新

## Closure Gates

> 关闭条件：本 section 所有条目与每个 Phase 的 Exit Criteria 全部 `[x]` 后，才能将 Plan Status 改为 `completed`。

- [x] AR-17 + AR-23①②⑨⑩ 五个已确认 live defect 全部修复（判别性测试 red→green 证据在案）
- [x] 无已确认 live defect / contract drift 被降级到 deferred / follow-up
- [x] docs-for-ai §失败路径（syncExternalTables 原子性契约）已同步 live baseline；其余 owner-doc 复核完成（无漂移则显式记录 `No owner-doc update required`）
- [x] 必要 focused verification 已完成（每项 finding 至少一条判别性测试）
- [x] 独立子 agent / 独立审阅者 closure-audit 完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）各修复在运行时调用链上真实生效（unregister 时序 / 清理先于 addDocs / Map 分支脱敏 / DRAFTING 过滤 / 事件失败路径 REQUIRES_NEW 存活）、（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw test -pl nop-metadata -am -T 1C`
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] checkstyle / 代码规范检查通过（历史惯例：插件仅 -Pqa profile，按仓库惯例）

## Deferred But Adjudicated

（无 —— 本 plan 5 项 finding 全部 in-scope 修复，无归类项；七项 Decision（AR-17 失败事件行为 + 失败注入机制、AR-23① 残余窗口 + 噪音成本、AR-23② 清理粒度 + 枚举超限、AR-23⑨ DRAFTING 过滤方式/DEPRECATED 分叉、AR-23⑩ 大小写语义）在 Phase 内显式裁定）

## Non-Blocking Follow-ups

- syncExternalTables 多实例并发同步（单实例 baseline 已裁定，R4.3；多实例需分布式锁）
- 索引重建的增量更新优化（当前全量重建 + 清理，增量 diff 属优化面）
- 事件发布失败重试机制（当前 publishEventWithSnapshots 失败路径按既有 LOG/异常语义，重试属优化面）
- 部分重建枚举超限残余（真实场景类型文档数远小于上限，watch-only residual——见 Phase 2 裁定）

## Closure

Status Note: 全 6 Phase 完成——AR-17 + AR-23①②⑨⑩ 五项已确认 live defect 全部修复（判别性测试 +11 red→green 实测，git stash 逐项 red）；7 项裁定全部记录于 plan + arm-index；docs-for-ai §失败路径 + design §2.5.1 已同步 live baseline；独立子 agent closure audit PASS；工具验证全 0；`./mvnw test -pl nop-metadata -am -T 1C` nop-metadata 1049/0 全绿。
Completed: 2026-08-06

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session，与实现 session 分离）
- Audit Session: ses_02a47998fffejR2q7cZSHBu7oo
- Evidence:
  - **Phase 1（AR-23①）PASS**：`NopMetaQualityCheckpointBizModel.java:284-288` super.delete 先于 unregister + 日志键 :312 `unregister-after-delete-failed`；判别性测试 `TestNopMetaQualityCheckpointBizModel#testDeleteFailureKeepsSchedule`（spy 注入点 = doDelete :1003-1005 + 接线前置断言 scheduler 注入 + 真实 LocalJobScheduler job 存留；失败保留 :1010-1012 / 成功移除 :1017-1023）；两项裁定记录 plan :96-97 + arm-index §P2 AR-23① 行
  - **Phase 2（AR-23②）PASS**：`NopMetaIndexBuilder.java:61-78`（fullRebuild removeTopic 循环前一次）+ :128-132/:173-197（部分重建类型级枚举 removeDocs，getDocsByTerm 排除，枚举上限 10000）；清理失败显式反映 :74-77/:192-196；判别性测试 +5（InOrder 时序 + 请求规格 + 全量/部分清理失败反映 + FakeSearchEngine 正向断言——幽灵 doc 移除 + live 保留 + 非目标类型保留）；枚举超限裁定记录 plan + arm-index §P2 AR-23② 行
  - **Phase 3（AR-23⑨）PASS**：`NopMetaModuleBizModel.java:629-631` `FilterBeans.ne(status, DRAFTING)`（not-eq 保留 DEPRECATED）+ generateManifest :588 调用点接线；判别性测试 +3（不同 moduleId RELEASED A vs DRAFTING B 同 className 确定性映射 A / DRAFTING-only 不可解析 / DEPRECATED 保持可解析）；DEPRECATED 分叉裁定记录 plan + arm-index §P2 AR-23⑨ 行
  - **Phase 4（AR-23⑩）PASS**：`MetaModelChangedEventPublisher.java:202-218` Map 分支 `isSensitiveColumn(null,key)` 脱敏（大小写敏感与 ORM 分支对齐）；`testMapEntityPathRedactsSensitiveKeys`（敏感脱敏 + 非敏感保留 + "Password" 大小写变体 keep-red 负例）；`testMapEntityPathUnchanged` 0 残留（re-adjudication 记录）；大小写语义裁定记录 plan + arm-index §P2 AR-23⑩ 行
  - **Phase 5（AR-17）PASS**：`NopMetaDataSourceBizModel.java:193-221` try/catch 包整个 withConnection 块 + `publishSyncScanFailureEvent` :245-262（REQUIRES_NEW 独立事务发布 → 重抛原异常，事件发布失败不掩盖原异常）；判别性测试 +2（反射注入 failing reader → 事件行存活 + 原错误码不掩盖——修复前 0 事件 red；真实入口 per-table 部分失败 → 已同步表持久化 + errors + 事件）；契约文档化：`docs-for-ai/03-modules/nop-metadata.md:217` + `ai-dev/design/nop-metadata/01-architecture-baseline.md:416`；两项裁定（失败事件行为 = 路径 (a) / 失败注入机制 = 反射替换）记录 plan + arm-index §P2 AR-17 行
  - **Phase 6（收口）PASS**：roadmap R8.4 行 "R8.4a done + R8.4b done —— R8.4 行整体 done，MR8 里程碑全行收口"（roadmap:269）+ arm-index "MR8 R8.4b 收口记录" 段（arm-index:8）+ §P2 5 行 fixed 标注；check-plan-checklist --strict exit 0；scan-hollow --severity high exit 0（0 findings）；check-doc-links --strict exit 0（0 errors，12 warnings 均为其他历史 plan 预存）；`./mvnw test -pl nop-metadata -am -T 1C` nop-metadata 1049/0（service 1048 + web 1；R8.4a 1037 基线 + 11；全 reactor 5249/0、72 skipped）
  - **Anti-Hollow 调用链追踪（PASS）**：(a) delete → CrudBizModel.delete(:1045) → doDelete 4-arg(:1057) → doDeleteEntity 运行时连通（spy 只拦 doDelete，真实 override 体执行）；(b) buildFullIndex removeTopic(:72) 严格先于 addDocs(:136)（InOrder + FakeSearchEngine 状态断言）；(c) publishEvent(:107-114) → buildSnapshot(:159-171) → buildEntitySnapshot Map 分支(:202-218) 公共 API 可达；(d) buildGlobalClassNameToModuleId 单一调用点 generateManifest :588 接线；(e) sync catch(:213-221) → publishSyncScanFailureEvent(:245) → runInTransaction(REQUIRES_NEW)(:250) → publishEventWithSnapshots(:251) 运行时连通（测试实证事件行存活）；全部新增/修改 catch 块含 LOG + 状态更新，无空方法体/静默跳过
  - **Deferred 分类检查（PASS）**：7 项裁定全部 in-scope 记录，无已确认 live defect 降级；Deferred But Adjudicated 段显式"无"；Non-Blocking Follow-ups 均为 pre-adjudicated 优化项/watch-only（多实例同步 / 增量索引 / 事件重试 / 枚举超限残余）
  - 独立复跑：5 个测试类 70/0 全绿（TestNopMetaQualityCheckpointBizModel 28 / TestNopMetaIndexBuilder 10 / TestNopMetaModuleBizModel 16 / TestMetaModelChangedEventPublisherSecurity 7 / TestNopMetaDataSourceBizModel 9）

Follow-up:

- no remaining plan-owned work（R8.4 行整体 done，MR8 里程碑收口；Non-Blocking Follow-ups 见上段，均不影响本 plan 契约 closure）
