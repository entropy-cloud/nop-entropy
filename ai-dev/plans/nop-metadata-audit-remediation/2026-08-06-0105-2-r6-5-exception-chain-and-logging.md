# R6-5 异常链保留 + 日志补全（P2-01/02/04）

> Plan Status: completed
> Last Reviewed: 2026-08-06
> Draft Review: R1 `ses_02d14010affewXQcsrFpJNXE2c`（0 Blocker / 3 Major / 7 Minor——M-1 "无 log capture 设施"为伪事实（TestMetaTableProfilerSecurity 已有 Logback ListAppender 先例）→ 已改：复用 ListAppender 做日志内容断言；M-2 Phase 2 测试目标错置（TestNopMetaClassificationBizModelIntegration 零 suggestTags 覆盖，真测试在 TestMetadataPropagationUnit）→ 已改目标 + 如实声明正路径测试需从零搭建；M-3 Phase 3 测试规格与 live 语义矛盾（parseValidations 公开入口语义为 ERR_CHECKPOINT_NO_RULES、static 签名变更、readRegisteredCron 需 scheduler mock）→ 已改逐处测试入口；m-4~m-10 基线断言强度/接线示例/类名/Logger 缺失/刷屏/口径已修）。R2 `ses_02d052947ffefQ3sKvB6KF4tpi`（全部 PASS，0 Blocker / 0 Major——5 Minor 处置：doThrow 先例引用已改同文件 :75/:84/:97/:106；parseValidations 既有断言强度注记（:200-205 仅 hasError，需按 :559-562 先例补精确错误码断言）已写入测试项；DIM_CONSISTENCY 回退措辞（仅 CUSTOM_SQL/null 落 consistency）已精确化；readRegisteredCron/readExtConfigDimension 测试类位置注记已加；P2-04 残余静默分支（instanceof 形态校验分支）已登记 Deferred watch-only 防 closure audit 翻案）。consensus 达成。
> Mission: nop-metadata-audit-remediation
> Work Item: MR6（R6.5）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MR6 段 R6.5 行 + Follow-up Backlog P2-01/02/04）、`ai-dev/audits/arm-index-nop-metadata.md`（§P2 MR6 裁决记录）
> Related: 执行顺序 `{2}` of 3 — R6.4（`{1}`）先行；R6.6（`{3}`，P2-03 死码清理 + docs sweep）随后。文件域与 R6.4（query/entity 三文件）不重叠。

## Purpose

按 MR6 R6.5 行收口三项 Backlog finding（2026-08-05 两轮审计登记，R6.0 live 复核提级为日志类，低成本 + 诊断收益）：

1. **P2-01**：`NopMetaSearchProcessor` fail-closed 分支 throw 不带 cause、无日志——索引故障根因完全丢失。
2. **P2-02**：`AutoClassificationProcessor` 正则编译失败 catch → continue 无日志——非法正则使规则永久失效且不可观测。
3. **P2-04**：4 处辅助函数 catch 后无日志静默返回默认值（`MetaQualityCheckpointExecutor.parseValidations`→emptyList / `MetaQualityScorer.readExtConfigDimension`→null / `NopMetaQualityCheckpointBizModel.readAutoScoreConfig`→true / `MetaQualityCheckpointScheduler.readRegisteredCron`→null）——配置损坏与"用户没配"不可区分。

目标状态：异常链保留（cause 不丢）+ 每处 catch 补日志（throwable 末参，沿 Scheduler:320-323 先例），回归测试钉死行为保持。

## Current Baseline

2026-08-06 live repo 核对：

- **P2-01（confirmed）**：`NopMetaSearchProcessor.addToIndex`（`search/NopMetaSearchProcessor.java:56-66`）/ `removeFromIndex`（:77-87）fail-closed 分支：`throw new NopMetadataException(ERR_SEARCH_INDEX_ADD_FAILED/ERR_SEARCH_INDEX_REMOVE_FAILED).param(...)` —— 无 cause、无 LOG。`NopMetadataException` 有 `(ErrorCode, Throwable)` 构造器（cause 保留能力已有，见 service/NopMetadataException.java）。既有测试 `TestNopMetaSearchProcessor.java` 断言强度有限：:78/:87 仅 `assertThrows(NopMetadataException.class)`（无错误码断言）、:93-115 fail-open 路径仅 `assertDoesNotThrow`（无 LOG.error 断言）——新测试需补 `getErrorCode()` + cause 断言 + ListAppender 日志断言。错误码：`MiscErrors.ERR_SEARCH_INDEX_ADD_FAILED`（MiscErrors.java:190）+ ERR_SEARCH_INDEX_REMOVE_FAILED。
- **P2-02（confirmed，测试现状需如实描述）**：`AutoClassificationProcessor`（`entity/AutoClassificationProcessor.java:129-134`）：`Pattern.compile(pattern, CASE_INSENSITIVE)` catch Exception → `continue`（跳过该规则）无日志。同文件 :99-102 有 LOG.warn 先例（main 代码，含 classificationId + throwable 末参；pattern/tagFQN/ruleIndex/classificationId 均在 :118/:123 双层循环作用域内可记录）。**测试现状（live 核对）**：`TestNopMetaClassificationBizModelIntegration.java` 仅 88 行 2 个纯 CRUD 测试（testSaveAndGet/testDelete），**零 suggestTags 覆盖**；真正的 AutoClassificationProcessor 测试在 `TestMetadataPropagationUnit.java`（mock DAO 脚手架，6 个测试，含 :149-238，:213-219 有 discoverClassification 装配雏形）；**当前仓库无"合法规则命中 → 生成标签"的正路径测试**——本 plan 的 (a) 断言需从零搭建（沿 TestMetadataPropagationUnit mock 模式，含 bizObjectManager 嵌套 mock `getBizObject().invoke(...)` 返回标签）。
- **P2-04（confirmed，4 处）**：
  - `MetaQualityCheckpointExecutor.parseValidations`（`quality/MetaQualityCheckpointExecutor.java:349-358`）：`JsonTool.parse(validationsJson)` catch → `return emptyList()`。审计注：有 ERR_CHECKPOINT_NO_RULES 兜底（空规则集时显式失败），但配置损坏被报为"空规则集"语义漂移且无根因日志。**注意：该方法是 `private static`，仅收 validationsJson 参数，checkpointId 不在作用域**——补日志如需 checkpointId 上下文必须改签名（加参，调用点 :209 的 cp 可用）或记录 JSON 摘要。
  - `MetaQualityScorer.readExtConfigDimension`（`quality/MetaQualityScorer.java:249-264`）：`JsonTool.parse(json)` catch → `return null`。**注意：MetaQualityScorer 无 slf4j import/无 LOG 字段**（其余 3 处均有）——需补 import + 静态字段。
  - `NopMetaQualityCheckpointBizModel.readAutoScoreConfig`（`entity/NopMetaQualityCheckpointBizModel.java:351-364`）：`JsonTool.parseBeanFromText` catch → `return true`（R2.12 已改为强类型 DTO 消费，catch 兜底仍在）。注释已声明"不静默伪造关闭"（默认开启语义），但无日志。
  - `MetaQualityCheckpointScheduler.readRegisteredCron`（`quality/MetaQualityCheckpointScheduler.java:253-261`）：`scheduler.getJobDetail` catch → `return null`——**失败机制是 scheduler 抛异常（非"损坏配置"）**，测试需 mock scheduler.getJobDetail 抛异常。同文件 :322 有 LOG.warn 先例（风格参照）。
- **日志测试设施（重要）**：**log capture 设施存在**——`TestMetaTableProfilerSecurity.java:251-259, 282-288` 使用 Logback `ListAppender` 断言 WARN 事件（`logger.addAppender(appender)` → 断言 level + formattedMessage → finally detach），这是 R3.9/MA6.2-002（与本 plan 同族的 catch 补 LOG.warn 修复）的既有回归测试先例。**P2-02/P2-04 的日志行断言可直接复用该模式**，不必降级为"代码审查替代"。
- 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` → **909 tests / 0 failures / 0 errors / 0 skipped**（**注意：909 为 `-pl nop-metadata/nop-metadata-service -am` 口径**，R6.3 记录；全量 `-am` 偶遇预存在 rocksdb 性能 flaky 时按单跑复绿降级，见 Closure Gates caveat）。

## Goals

- P2-01：fail-closed 分支异常保留原始 cause 链 + throw 前补 LOG.error（根因可诊断）
- P2-02：正则编译失败补日志（记录 pattern + 规则上下文），continue 语义保持（非法规则被跳过但可观测）
- P2-04：4 处 catch 补 LOG.warn（throwable 末参），默认值语义保持（配置损坏 vs 未配置可在日志区分）
- 回归测试钉死"行为保持"（默认值/跳过语义不因加日志而改变）；**日志行用既有 Logback `ListAppender` 模式（TestMetaTableProfilerSecurity 先例）做内容断言**
- arm-index §P2 P2-01/02/04 行终态 = fixed + roadmap R6.5 行 → done

## Non-Goals

- 不改变 P2-01 fail-open/fail-closed 开关语义（searchIndexFailOpen 配置行为保持）
- 不改变 P2-04 各处默认值语义（readAutoScoreConfig 默认 true 等——R6.0 归类为日志类，非正确性类，不做 fail-fast）
- 不引入新的日志捕获测试框架（**复用既有 Logback ListAppender 模式，不新增依赖**）
- 不触碰 R6.4 文件域（AggregationHelper/NopMetaModuleBizModel/NopMetaTagLabelBizModel）

## Scope

### In Scope

- `NopMetaSearchProcessor.addToIndex/removeFromIndex` fail-closed 分支 cause + LOG（Fix）
- `AutoClassificationProcessor` 正则编译失败 LOG（Fix）
- 4 处 quality 辅助函数 catch 补 LOG（Fix）
- 对应回归测试（Fix）
- arm-index §P2 + roadmap R6.5 行终态更新（Fix）
- `ai-dev/logs/2026/08-06.md`（或执行当日日志）更新（Follow-up）

### Out Of Scope

- P2-06/07/09（R6.4）、R6.6 批量（死码清理 / docs sweep / watch-only 登记）
- P2-04 的"更优方案"（parseValidations 定义 validations 解析错误码显式抛出——R6.0 已归类日志类，显式抛出属行为变更，不在本 plan；如需可记录为 successor 候选）
- searchIndexFailOpen 开关语义变更

## Execution Plan

### Phase 1 - P2-01：NopMetaSearchProcessor fail-closed cause + 日志

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/search/NopMetaSearchProcessor.java` + `TestNopMetaSearchProcessor.java`

- Item Types: `Fix | Proof`

- [x] 落地（Fix）：`addToIndex`/`removeFromIndex` fail-closed 分支改为 `new NopMetadataException(错误码, e)`（cause 保留）+ `.param(...)` + throw 前 `LOG.error(...)`（含 entityType/entityId + throwable）；fail-open 分支保持（已有 LOG.error）——执行裁定：LOG.error 提到分支判断之前，fail-open/fail-closed 两路径统一留 ERROR 根因日志（fail-open 原已 LOG.error，无行为变化）
- [x] **回归测试（Fix，判别性验证）**：`TestNopMetaSearchProcessor` 扩展——(a) fail-closed 时 `assertThrows` 捕获异常，断言 `getCause()` 为原始 mock 异常（**恒等断言**：持 mock 异常实例变量，`doThrow(实例)` 后 `assertSame(实例, ex.getCause())`——doThrow 匿名实例用法先例见同文件 :75/:84/:97/:106）；(b) **补 `getErrorCode()` 断言**（既有 :78/:87 仅 assertThrows 无错误码断言，需补强）；(c) fail-open 路径断言保持（:93-110 不回归）；(d) **ListAppender 断言 LOG.error 行存在**（沿 TestMetaTableProfilerSecurity 先例：`(ch.qos.logback.classic.Logger) LoggerFactory.getLogger(NopMetaSearchProcessor.class)` → addAppender → 断言 level+message → finally detach）
- [x] **接线验证（Proof）**：确认 fail-closed 分支在真实调用链上可达——`addToIndex`/`removeFromIndex` 有 7 个真实调用点（EntityField/Classification/Entity/GlossaryTerm/Module/Table BizModel；`NopMetaModuleBizModel.importOrmModel` 调用点 :192/:195/:198），测试经 BizModel 或直接调用断言一条活路径——实际核查：7 个 BizModel（EntityField/Classification/Entity/GlossaryTerm/Module/Table/Tag）共 18 处调用点，处理器为唯一索引入口；测试直接调用处理器断言 fail-closed 活路径

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] fail-closed 抛错携带原始 cause（测试断言 getCause 链）
- [x] **无静默跳过**：无 catch-and-continue 引入；fail-closed 语义保持（抛错不吞）
- [x] 既有 fail-open/fail-closed 测试全部不回归；`./mvnw test -pl nop-metadata -am -T 1C` 相关测试类全绿
- [x] `No owner-doc update required`（日志/cause 补全不改变公开契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P2-02：AutoClassificationProcessor 正则编译失败日志

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/AutoClassificationProcessor.java` + `TestMetadataPropagationUnit.java`（AutoClassificationProcessor 测试所在文件；**不是** TestNopMetaClassificationBizModelIntegration——后者仅 2 个 CRUD 测试零 suggestTags 覆盖）

- Item Types: `Fix | Proof`

- [x] 落地（Fix）：`Pattern.compile` catch 内补 `LOG.warn`（记录非法 pattern 字符串 + classificationId/ruleIndex 上下文 + throwable 末参，沿同文件 :99-102 先例），continue 语义保持；**防日志刷屏**：pattern 编译位于 field×rule 双层循环内（:118/:123），同一非法 pattern 会对每个字段打一条 WARN——按 (classificationId, pattern) 在单次调用内去重（或执行时裁定其他等效防刷屏方式，需记录理由）——执行裁定：`Set<String> warnedInvalidPatterns`（key=`classificationId|pattern`，方法级局部 Set，单次调用生命周期），与 LOG.warn 同处生效，原因已记录于代码注释
- [x] **回归测试（Fix，判别性验证）**：在 `TestMetadataPropagationUnit.java`（既有 mock DAO 脚手架 + 6 个 AutoClassificationProcessor 测试）新增——(a) 含非法 pattern 的规则集 → 非法规则被跳过、合法规则仍生效（**该正路径断言当前仓库不存在，需从零搭建**：沿 :213-219 discoverClassification 装配雏形 + bizObjectManager 嵌套 mock）；(b) **ListAppender 断言 LOG.warn 行存在且含非法 pattern 字符串**（沿 TestMetaTableProfilerSecurity 先例）；(c) 同一非法 pattern 多字段不刷屏（断言 warn 行数 ≤ 规则数，如做去重）——新增 `testAutoClassifyInvalidPatternSkippedValidRuleStillWorks`：双字段 + 1 非法规则 + 1 合法规则 → 断言 result.size()==1 且 tagId=tag-b（非法跳过合法生效）、WARN 含 pattern `[unclosed` + cls-1、非法 pattern WARN 计数 ==1（去重生效）
- [x] **无静默跳过核查（Proof）**：确认 catch 不再裸 continue——日志留证 + 测试断言；不改变"跳过非法规则"的业务语义（R6.0 归类日志类，非 fail-fast）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 非法正则可观测（LOG.warn 含 pattern + 上下文），跳过语义保持，无日志刷屏
- [x] 回归测试断言行为保持（非法规则跳过 + 合法规则生效）+ ListAppender 日志断言，测试类全绿
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - P2-04：4 处 quality catch 补日志

Status: completed
Targets: `MetaQualityCheckpointExecutor.java` + `MetaQualityScorer.java` + `NopMetaQualityCheckpointBizModel.java` + `MetaQualityCheckpointScheduler.java`（`quality/` + `entity/`，注意类名：无 `MetaQualityScheduler`）+ 对应测试类

- Item Types: `Fix | Proof`

- [x] 落地（Fix，4 处）：(1) `MetaQualityCheckpointExecutor.parseValidations` catch → LOG.warn（**方法为 private static、checkpointId 不在作用域**——改签名加 checkpointId 参数（调用点 :209 的 cp 可用）或记录 validationsJson 摘要，执行时裁定并记录；throwable 末参）——执行裁定：**改签名** `parseValidations(String validationsJson, String checkpointId)`（唯一调用点 :209 的 `cp.getCheckpointId()` 可用，checkpointId 上下文比 JSON 摘要更有诊断价值），LOG.warn 含 checkpointId + throwable；(2) `MetaQualityScorer.readExtConfigDimension` catch → LOG.warn（记录 ruleId + throwable 末参；**该文件无 slf4j import/无 LOG 字段，需补 import + 静态字段**）——已补 `org.slf4j` import + `LOG` 静态字段，LOG.warn 含 rule.getQualityRuleId() + throwable；(3) `NopMetaQualityCheckpointBizModel.readAutoScoreConfig` catch → LOG.warn（记录 checkpointId + throwable，注释"默认开启"语义保持）；(4) `MetaQualityCheckpointScheduler.readRegisteredCron` catch → LOG.warn（记录 checkpointId + throwable，沿 :322 先例）——`nop.meta.checkpoint-scheduler.read-registered-cron-failed` 前缀对齐同文件 :322/:294 风格
- [x] **回归测试（Fix，行为保持断言 + 日志断言）**，逐处指定测试入口与可观测路径（4 处机制不同，不得用统一规格概括）：
  - parseValidations：**公开入口 execute() 语义是 ERR_CHECKPOINT_NO_RULES**（corrupt validations → emptyList → resolveRules 空 → execute :111-115 抛 ERR_CHECKPOINT_NO_RULES；既有 `TestNopMetaQualityCheckpointBizModel` testExecuteCheckpointEmptyRuleSetFails（:200-205）覆盖该路径但仅断言 `resp.hasError()`——**新测试需按 :559-562 先例经 raw impl 断言精确错误码**），如裁定签名变更则补 checkpointId 参数断言——新增 `testCorruptValidationsFailsWithNoRulesAndLogsWarn`（TestNopMetaQualityCheckpointBizModel）：corrupt validations → GraphQL hasError + raw impl `assertThrows` 精确断言 `ERR_CHECKPOINT_NO_RULES.getErrorCode()` + ListAppender WARN 含 checkpointId（签名已加 checkpointId 参数，日志即参数断言）
  - readAutoScoreConfig：损坏 extConfig → executeCheckpoint 摘要 autoScore=true（默认开启语义，断言先例 :303）保持 + 不抛异常——新增 `testCorruptExtConfigAutoScoreDefaultsOnAndLogsWarn`（TestNopMetaQualityCheckpointBizModel）：`{{{corrupt` extConfig + 真实表/规则 → `data.contains("autoScore=true")` + 评分行落盘 +1 + ListAppender WARN 含 cp-badext
  - readExtConfigDimension：损坏 extConfig → scorer.score 维度映射回退**静态 ruleType 映射**（readExtConfigDimension 返回 null 时仅 CUSTOM_SQL/null/未知 ruleType 落到 DIM_CONSISTENCY，volume/not_null 落 completeness/accuracy——测试须选用映射到 consistency 的 ruleType）；**MetaQualityScorer 无既有单测**（仅 TestNopMetaQualityScoreBizModel 集成覆盖），测试类位置执行时裁定——新建 `TestMetaQualityScorer.java`（quality 包，mock DAO 脚手架沿 TestMetadataPropagationUnit 模式）：CUSTOM_SQL 规则 + `{{{corrupt` extConfig → score 结果 consistency=100.0、completeness=null（回退静态映射不伪造）+ ListAppender WARN 含 ruleId
  - readRegisteredCron：**mock `scheduler.getJobDetail` 抛异常**（非"损坏配置"场景）→ 返回 null + 无异常抛出；方法 private，可观测路径 `registerCheckpoint → doRegister catch → readRegisteredCron`（mock addJob 抛异常）；**既有 TestMetaQualityCheckpointScheduler 经 IoC 注入真实 LocalJobScheduler 无 mock 先例——需新建手工构造 + setter 注入的 mock 测试，位置执行时裁定**——新建 `TestMetaQualityCheckpointSchedulerCronReadFailure.java`（quality 包，手工构造 + setDaoProvider/setScheduler 注入）：mock addJob 抛异常 + getJobDetail 抛异常 → registerCheckpoint 不向外抛 + ListAppender WARN 含 checkpointId
  - 4 处均加 **ListAppender 日志断言**（LOG.warn 行存在，沿 TestMetaTableProfilerSecurity 先例：`LoggerFactory.getLogger(目标类.class)` → addAppender → level+message 断言 → finally detach）
- [x] **无静默跳过核查（Proof）**：确认 4 处 catch 均不再裸返回默认值（日志留证）；注释同步（如有"静默"表述需更新）——4 处 catch 均补 WARN + throwable 末参；parseValidations 注释更新为"留 WARN 根因"，readAutoScoreConfig 注释更新为"但留 WARN 根因"，readExtConfigDimension/readRegisteredCron 同理；残余形态校验分支（`instanceof` 非 List/Map → 默认值）仍无日志，已登记 Deferred watch-only（见 Deferred But Adjudicated）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 4 处 catch 均补 LOG.warn（throwable 末参），默认值语义保持
- [x] 回归测试断言行为保持（按 4 处各自的测试入口与可观测路径）+ ListAppender 日志断言，相关测试类全绿
- [x] **无静默跳过**：无新增裸 catch；既有 :322 先例风格一致
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 收口（arm-index 终态 + closure audit）

Status: completed
Targets: `ai-dev/audits/arm-index-nop-metadata.md` + `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`

- Item Types: `Fix | Proof`

- [x] arm-index §P2 对应行（P2-01/02/04）终态 = fixed + 本 plan 引用 + 修复摘要 + 测试证据——三行均已标注 `fixed（plan-2026-08-06-0105-2 Phase 1/2/3）` + 修复摘要 + 测试证据；R6.5 收口注 + header 最后更新已同步；AR-10 状态确认行已更新（P2-01/04 已 fixed）
- [x] roadmap MR6 R6.5 行 → done（注明 plan 引用 + 测试计数）——`done（plan-2026-08-06-0105-2 Phase 1-3...925/0 全绿...独立子 agent closure audit PASS）` + header v22
- [x] 独立子 agent closure audit（fresh session）逐项核对 Phase Exit Criteria + Closure Gates，证据写入本 plan Closure 段——fresh session `ses_02c9d65d4ffeyQY8uLuVIbfVNd`，10 项核查全 PASS（见 Closure 段）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（涉及 arm-index/roadmap 变更后）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] arm-index + roadmap 终态一致可追溯（P2-01/02/04 三行 fixed）
- [x] 独立 closure audit PASS，evidence 已写入本 plan Closure 段
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（0 failures）——925/0（全量 `-am` 偶遇预存在 nop-stream rocksdb 性能 flaky，单跑复绿 2/2，非本 plan 引入，按 R6.3 降级口径记录）
- [x] 无静默降级：三项日志类 finding 为 fixed，无 live defect 被降级
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] P2-01：fail-closed 分支 cause 保留 + LOG.error（根因可诊断）
- [x] P2-02：正则编译失败 LOG.warn（pattern 可观测），跳过语义保持
- [x] P2-04：4 处 catch 补 LOG.warn（throwable 末参），默认值语义保持
- [x] 必要 focused verification 已完成（行为保持回归测试 + 既有测试不回归）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据（fresh session，见 Closure 段）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）日志行确实存在于对应 catch 路径（ListAppender 测试断言 + 代码审查确认 throwable 末参），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿 —— **口径 caveat（沿 R6.3 先例）**：909/0 基线为 `-pl nop-metadata/nop-metadata-service -am` 口径；全量 `-am` 偶遇预存在 rocksdb 性能 flaky 时按"单跑复绿 + 记录非本 plan 引入"降级口径，不得判 gate 假红——本跑：metadata 全量 925/0 绿；全量 `-am` 唯一红 = `nop-stream TestRocksDBIncrementalRestoreAndBenchmark.incrementalCheckpointIsFasterThanFullScanForLargeState`（ratio=1.27 阈值抖动，性能基准非确定性），**单跑复绿 2/2**，非本 plan 引入（本 plan 仅触碰 nop-metadata 文件），按降级口径记录
- [x] checkstyle / 代码规范检查通过（nop-metadata 无独立 checkstyle 命令，以 mvn 构建默认检查为准）——`./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C` BUILD SUCCESS + 全量 test 编译/运行通过；`checkstyle:check` 项目级 pre-existing 957 violations（nop-metadata-api），非本 plan 引入，按 plan 口径以 mvn 构建默认检查为准
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（closure 时）

## Deferred But Adjudicated

### P2-04 parseValidations 显式错误码（审计"更优方案"）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: R6.0 已归类 P2-04 为日志类（非正确性类）；显式抛出 = 行为变更（配置损坏时 checkpoint 直接失败而非空规则集兜底），超出本 plan 日志补全范围；ERR_CHECKPOINT_NO_RULES 兜底已保证不静默执行
- Successor Required: `no`
- Successor Path: —

### P2-04 残余静默分支（非 catch 的形态校验分支）

- Classification: `watch-only residual`
- Why Not Blocking Closure: `parseValidations` 的 `!(parsed instanceof List)` → emptyList（MetaQualityCheckpointExecutor:359-361）与 `readExtConfigDimension` 的 `!(parsed instanceof Map)` → null（MetaQualityScorer:256-258）——可解析但形态错误的 JSON 仍无日志，属 P2-04 "配置损坏不可观测"的残余面；但形态错误 JSON 是更低概率的配置异常，catch 路径（本 plan 修复）覆盖主要面；不阻塞 closure，登记 watch-only 防止 closure audit 翻案
- Successor Required: `no`
- Successor Path: —

### P2-02 单次调用内同 pattern 去重粒度

- Classification: `watch-only residual`
- Why Not Blocking Closure: 去重仅限单次 suggestTags 调用内（进程内存 Set）；跨调用重复日志在配置修正前仍会重现——但配置修正前规则持续失效本身即需反复提醒，重复 WARN 有运维价值；刷屏风险已在单次调用内消除
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- 如后续引入更丰富的日志断言设施（当前 Logback ListAppender 已够用），可为 P2-02/P2-04 补结构化日志字段断言（当前以 level + message 断言为准）
- 工作树提交由 mission 流程/用户决定（本 plan 执行不代提交）

## Closure

Status Note: 三项日志类 finding（P2-01/02/04）全部修复落地 + 回归测试钉死行为保持 + 独立 closure audit PASS；Phase 1-4 全部 completed，Closure Gates 全部勾选，文本一致性核对通过（Plan Status / Phase Status / Exit Criteria / Closure Gates / daily log 五处一致）。
Completed: 2026-08-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）
- Audit Session: `ses_02c9d65d4ffeyQY8uLuVIbfVNd`
- Evidence:
  - Phase 1（P2-01）：PASS——`NopMetaSearchProcessor.java:58-65/:78-85` fail-closed 分支 2 参构造 `NopMetadataException(ErrorCode, e)`（NopMetadataException.java:24-25 构造器实证）+ LOG.error 于 throw 前；fail-open 不抛语义保持。TestNopMetaSearchProcessor 11/11：`assertSame(cause, ex.getCause())` 恒等 + `getErrorCode()` 精确断言（:83-85/:98-101）+ ListAppender ERROR 断言（:107-133/:136-160）+ fail-open assertDoesNotThrow（:165-182）
  - Phase 2（P2-02）：PASS——`AutoClassificationProcessor.java:134-144` catch 内 LOG.warn（pattern + classificationId + ruleIndex + throwable 末参）+ 方法级 `Set`（key=classificationId|pattern，:122）去重 + continue 跳过语义保持。`testAutoClassifyInvalidPatternSkippedValidRuleStillWorks`（TestMetadataPropagationUnit:251-330）：非法跳过 + 合法生效（result==1/tagId=tag-b）+ WARN 含 `[unclosed` + cls-1 + 去重计数==1；类 11/11
  - Phase 3（P2-04）：PASS——4 处 catch 全补 WARN + throwable 末参：(a) `MetaQualityCheckpointExecutor.java:349` parseValidations 签名加 checkpointId（调用点 :209 传 cp.getCheckpointId()）；(b) `MetaQualityScorer.java:17-18/:50` slf4j import + LOG 字段，:265-270 WARN 含 ruleId；(c) `NopMetaQualityCheckpointBizModel.java:368-373` WARN 含 checkpointId（默认开启）；(d) `MetaQualityCheckpointScheduler.java:258-263` WARN `read-registered-cron-failed` 前缀 + checkpointId。测试：TestNopMetaQualityCheckpointBizModel 25/25（corrupt validations → raw impl 精确断言 ERR_CHECKPOINT_NO_RULES + WARN；corrupt extConfig → autoScore=true + 评分落盘 + WARN）；TestMetaQualityScorer 1/1（CUSTOM_SQL + corrupt extConfig → consistency=100.0 静态回退 + completeness null + WARN）；TestMetaQualityCheckpointSchedulerCronReadFailure 1/1（mock addJob + getJobDetail 抛 → 不外抛 + WARN）
  - Phase 4（收口）：PASS——arm-index §P2 P2-01/02/04 三行 → fixed（plan 引用 + 修复摘要 + 测试证据）+ R6.5 收口注 + header 更新 + AR-10 状态确认行更新；roadmap R6.5 → done（plan 引用 + 测试计数）+ header v22；`node ai-dev/tools/check-plan-checklist.mjs --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）；`node ai-dev/tools/check-doc-links.mjs --strict` 0 errors
  - Anti-Hollow 检查：PASS——7 个变更路径（addToIndex/removeFromIndex fail-closed ×2、Pattern.compile warn+去重、parseValidations、readExtConfigDimension、readAutoScoreConfig、readRegisteredCron）全部有 ListAppender/行为断言测试覆盖，无空方法体/静默跳过/no-op；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
  - Deferred 项分类检查：PASS——3 项 deferred 全部为 watch-only residual / out-of-scope improvement 且附理由（parseValidations 显式错误码 = R6.0 日志类裁定的更优方案，行为变更超范围；instanceof 形态校验残余 = 低概率配置异常，catch 主面已覆盖；去重粒度 = 跨调用重复 WARN 有运维价值）；无 in-scope live defect 被降级
  - 构建/测试证据：`./mvnw test -pl nop-metadata/nop-metadata-service -am -T 1C` 全绿（925/0 于 metadata 全量）；全量 `-am` 唯一红 = 预存在 nop-stream rocksdb 性能 flaky（`TestRocksDBIncrementalRestoreAndBenchmark.incrementalCheckpointIsFasterThanFullScanForLargeState` ratio=1.27 阈值抖动），**单跑复绿 2/2**，非本 plan 引入（本 plan 仅触碰 nop-metadata 文件），按 R6.3 降级口径记录；`./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C` BUILD SUCCESS

Follow-up:

- 如后续引入更丰富的日志断言设施，可为 P2-02/P2-04 补结构化日志字段断言（当前以 level + message 断言为准）
- 工作树提交由 mission 流程/用户决定（本 plan 执行不代提交）
- no remaining plan-owned work
