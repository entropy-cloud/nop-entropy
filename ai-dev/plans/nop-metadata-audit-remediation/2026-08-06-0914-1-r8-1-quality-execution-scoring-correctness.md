# R8.1 质量执行/评分正确性组修复（AR-11/12/13/14/15）

> Plan Status: completed
> Last Reviewed: 2026-08-06
> Mission: nop-metadata-audit-remediation
> Work Item: MR8（R8.1 质量执行/评分正确性组）
> Source: `ai-dev/audits/2026-08-05-2157-open-audit-nop-metadata-audit-remediation.md`（AR-11~15）、`ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MR8 段 R8.1 行 + R8.0 裁决记录）
> Related: 执行顺序 `{1}` of 3（**R8.2 依赖本 plan 完成后执行**——AR-13 修改 `QualityErrors.java` 声明 + `MetaQualityRuleExecutor.java` 相邻区域，R8.2 的 AR-16 改同文件 LOG 行（:631/:647），顺序执行避免交叉编辑；AR-16 不触 QualityErrors.java，仅共享 MetaQualityRuleExecutor.java）；启动门禁：R8.0 done（roadmap MR8 段）。

## Purpose

修复质量规则执行与评分链路的 5 个已确认正确性缺陷（AR-11/12/13/14/15，R8.0 全部提级为 P1 修复）：SKIP 误判使失败规则静默消失、调度器违反 MA7.5-01 不外抛契约、错误码参数不可归属、抛异常规则不写结果行导致自动评分复用陈旧结果、freshness 负年龄恒 PASS。产出 = 代码修复 + 判别性回归测试 + docs 同步（AR-11 行为收紧须同步 regex 方言例外说明）+ arm-index/roadmap 终态。

## Current Baseline

经 2026-08-06 live repo 核对（finding 描述以审计报告为准；行号以 live 复核为准）：

- 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` **970/0**（R8.0 收口口径）
- **AR-11**（`MetaQualityRuleExecutor.java:725-733` `isRegexpUnsupported`）：子串启发式 `contains("regexp") || contains("syntax") || function && not found`——MySQL（支持 REGEXP）非法 pattern 报错 "Got error ... from regexp" 含 "regexp" → 规则被标 SKIP 而非 ERROR，失败规则从 pass/fail 统计中静默消失；`judgeRegex` 调用点 `:562`；**方言面事实**：judgeRegex SQL 为 `col NOT REGEXP ?`（:550），PostgreSQL 不支持 REGEXP 运算符（报 "syntax error at or near"），H2/MySQL 支持（H2 非法 pattern 消息可能含 "syntax"）——签名集合必须含 PG 签名（"syntax error at or near"）且不能用裸 "syntax"（会误伤 H2）；`isRegexpUnsupported` 签名无 productName 参数（productName 门控方案需改签名）
- **AR-12**（`MetaQualityCheckpointScheduler.java:203-207`）：`executeScheduledCheckpoint` 中 `cpId == null` 在 try（:209）**之外**抛 `ERR_CHECKPOINT_SCHEDULER_INVALID_CRON`（定义于 `QualityErrors.java:23`）——遗留/损坏 job 参数命中即逃逸到 invoker 使 job 永久 FAILED（正是 MA7.5-01 消除的失败模式）+ 错误码与失败语义（缺 checkpointId 参数）不符；`buildErrorResult`（:242-250）已存在可复用
- **AR-13**（`QualityErrors.java:16-22` + `MetaQualityRuleExecutor.java:631-643/:647-659`）：`ERR_QUALITY_SQL_NO_ROW/FAILED` 声明 `{ruleKey}` 占位但 throw 点只设 `sql`/`error` 从不设 ruleKey（ARG_RULE_KEY 已声明但 0 使用）→ 占位恒渲染为字面量；**关键约束**：`queryLong`/`queryTimestamp` 为静态方法，调用链（judgeVolume/unique/not_null/range/freshness 等）中**任何位置都没有 ruleKey**（`judge()` 签名无 ruleKey，仅 `judgeCustomSql` 从 params 取 :285）——`.param(ARG_RULE_KEY, ...)` 仅 custom_sql 路径可行；`evalExpectPassWhen` 错误上下文 `:720` 为字面量 `<evalExpectPassWhen>`（调用点 :316 在 judgeCustomSql 内，params 有 ruleKey 可取）
- **AR-14**（`MetaQualityCheckpointExecutor.java:155-163` + `MetaQualityScorer.java:129-145/:315` + `CheckpointExecutionResultDTO.java` + `QualityRuleResultDTO.java`）：
  - 抛异常规则只入 errors 列表**不写 ERROR 结果行** → `findLatestResult` 取到前一轮旧行（可能旧 PASS）→ 失败运行后评分仍显健康
  - catch 块顺序（:156-164）：`errors.add → errorCount++ → orm.clearSession()`——若在 clearSession **之前** append ERROR 行会被清掉（静默丢行），必须在 clearSession 之后写或先 flush
  - `affectedTableIds` 收集只在成功路径（:138-142）——异常规则的表不进入 → 本次 run 的 autoScore 不会重算该表
  - DTO `totalRuleCount`/`ruleResults` 在检查点路径从不填充（`setRuleResults` 全仓 0 调用点；`setTotalRuleCount` 仅单规则路径 :223）+ 缺 `skipCount` 字段（executor 内 skipCount 局部变量 :123 从未写入 summary :168-177）；**形状约束**：`QualityRuleResultDTO` 字段为 qualityRuleId/resultCount/passCount/failCount/errors（单规则路径语义），而 summary.results 条目是 Map{qualityRuleId, ruleName, status, actualValue, expectedValue, message}——两者形状不对应，映射需先裁定
- **AR-15**（`MetaQualityRuleExecutor.java:735-740` `ageMinutesFromNow` + `:215-257` freshness）：`ageMinutes = (now - ts)/60000` 可为负（DB 时钟超前/未来时间戳），`ageMinutes > maxAgeMinutes` 判 FAIL → 负值恒 PASS 掩盖新鲜度违约
- 相关测试现状：`TestNopMetaQualityCheckpointBizModel`（**25** 测）、`TestMetaQualityScorer`（新建 1 测）、`TestMetaQualityCheckpointSchedulerCronReadFailure`、`TestMetaQualityCheckpointExecutorSchemaResolution`、`TestMetaQualityRuleExecutorCustomSqlSandbox`（**19** 测，行为断言已重构）

## Goals

- AR-11：SKIP 仅保留给真实"方言不支持"场景（按方言/签名集合匹配，签名集合含 "not supported"/"unknown function"/**"syntax error at or near"（PostgreSQL REGEXP 不支持签名）**，主方案不改签名；productName 门控为备选且需改签名）；MySQL/H2 非法 pattern → ERROR（显式 status=ERROR + message），判别性测试 red→green
- AR-12：`cpId==null` 分支不再抛错逃逸——改走 `buildErrorResult` + 语义匹配的新错误码（如 missing-checkpoint-id，新增于 `QualityErrors.java`），在 try 边界内完成；job 存活语义保持（MA7.5-01）
- AR-13：`{ruleKey}` 占位可解析——**裁定**：queryLong/queryTimestamp 路径（无 ruleKey 上下文）改 ErrorCode 声明（移除/替换 `{ruleKey}` 占位，最小改动），custom_sql 路径与 evalExpectPassWhen（有 ruleKey 可取）补 `.param(ARG_RULE_KEY, ruleKey)`；错误可归属
- AR-14：抛异常规则补写 ERROR 结果行（**在 clearSession 之后写 + flush**）+ 异常规则表加入 affectedTableIds（非 database 规则）→ 本次 run autoScore 即用 ERROR 行重算；DTO 新增 `skipCount` 字段 + 填充 `totalRuleCount`/`skipCount`/`ruleResults`（映射契约见 Phase 3 Decision）；自动评分不再复用陈旧结果
- AR-15：**裁定为 fail-loud 语义**——`age < 0` → FAIL（未来时间戳 = 时钟偏移异常，不再恒 PASS），details 暴露原始差值；判别性测试断言 FAIL + 原始差值
- 每个修复带判别性回归测试（red 先于修复实测或至少行为断言可捕获回归）；docs-for-ai regex 方言例外说明同步

## Non-Goals

- 不重构质量规则执行引擎整体架构（不做 ruleKey ThreadLocal 化等超越 AR-13 所需范围的改造——queryLong/queryTimestamp 路径按"改 ErrorCode 声明，占位替换为 `{sqlHash}`"最小方案落地）
- 不处理 R8.2 组的 AR-16（同文件不同行区域的 LOG 治理）与 R8.3/R8.4 组 finding
- 不改变 SKIP 的可见语义本身（SKIP 仍是显式可见结果 + details.reason 标记），只收窄 SKIP 的触发面
- 不改 `QualityRuleResultDTO` 既有字段语义（只做确定性**新增** status/message 字段，见 Scope；resultCount/passCount/failCount/errors 单规则路径语义保持）

## Scope

### In Scope

- `MetaQualityRuleExecutor.java`（AR-11/13/15 + 相关测试）
- `MetaQualityCheckpointScheduler.java`（AR-12 + 相关测试）
- `QualityErrors.java`（AR-12 新错误码 + AR-13 错误码声明修正）
- `MetaQualityCheckpointExecutor.java` + `MetaQualityScorer.java`（AR-14 结果行写入 + 陈旧复用消除 + summary 补键 + 相关测试；**scorer 不强制代码变更——ERROR 行写入后既有 findLatestResult 语义自动修正，scorer 仅出现在 e2e 断言中，若实现中发现需同步修改则按需处理**）
- `NopMetaQualityCheckpointBizModel.java`（AR-14 DTO 填充映射——summary → CheckpointExecutionResultDTO totalRuleCount/skipCount/ruleResults，:198-220 现状不填该三字段）
- `CheckpointExecutionResultDTO.java`（nop-metadata-api，AR-14 新增 skipCount 字段 + 填充既有字段）+ `QualityRuleResultDTO.java`（nop-metadata-api，确定性新增 status/message 字段——**两个 api 变更同批声明**，plan-first）
- `docs-for-ai/03-modules/nop-metadata.md` §regex 规则方言例外（P2-08）说明同步（AR-11 行为收紧）
- `ai-dev/audits/arm-index-nop-metadata.md` §P2 + roadmap MR8 段终态更新

### Out Of Scope

- R8.2 组（AR-16/AR-23③⑤④）、R8.3 组（AR-18/19）、R8.4 组（AR-20/21/22/17 + AR-23①②⑨⑩）
- 检查点执行引擎的其它行为变更（如 dispatchActions、事务边界）
- AR-14 中 batch 与 checkpoint 的 `executedCount` 口径统一（审计记录为非阻塞观察项，本 plan 只保证 checkpoint 路径 DTO 可对账）

## Execution Plan

### Phase 1 - AR-12 调度契约修复 + AR-15 freshness 语义裁定

Status: completed
Targets: `MetaQualityCheckpointScheduler.java` + `QualityErrors.java` + `MetaQualityRuleExecutor.java` + 相关测试

- Item Types: `Fix | Decision | Proof`

- [x] AR-12：`cpId == null` 分支移入 try 内（或等价改为不逃逸），改走 `buildErrorResult` + 新错误码（新增于 `QualityErrors.java`，如 `ERR_CHECKPOINT_MISSING_ID`，语义匹配缺失 checkpointId，不再复用 INVALID_CRON）；确认 `executeScheduledCheckpoint` 全路径不外抛（MA7.5-01 契约）
- [x] AR-15 Decision：裁定负年龄语义 = **fail-loud**（`age < 0` → FAIL + details 暴露原始差值；不选"钳制 0 → PASS"——未来时间戳为时钟偏移异常，钳制会继续掩盖违约）；记录裁定理由
- [x] AR-15 修复：`ageMinutesFromNow` 判定处对负值显式 FAIL（或等价：freshness 判定 `ageMinutes < 0` 直接 FAIL）+ details 记录原始差值（如 `rawAgeMinutes`）
- [x] 判别性测试：AR-12 —— mock addJob/getJobDetail 遗留 job（损坏参数/缺失 checkpointId）→ 返回 error 结果而非抛错、job 不永久 FAILED、错误码语义断言；AR-15 —— 未来时间戳（DB 时钟超前模拟）→ **FAIL** + details 原始差值断言（修复前负值恒 PASS red 实测）
- [x] 回归：既有 `TestMetaQualityCheckpointSchedulerCronReadFailure` / 调度器 cron 测试全绿

Exit Criteria:

- [x] AR-12：损坏 job 参数路径实测不再外抛（判别性测试 red→green 记录）；错误码语义与缺失 checkpointId 匹配
- [x] AR-15：未来时间戳路径实测 FAIL + details 含原始差值（判别性测试 red→green 记录）；Goals/修复/测试三者一致（负年龄 = FAIL）
- [x] **无静默跳过**：AR-12 不再有 try 外抛错逃逸路径；AR-15 无负值恒 PASS 残留（Minimum Rules #24）
- [x] 本 Phase 改行为：新增错误码同步至 arm-index 错误码清单（若模块文档含错误码清单则同步）；调度失败语义与模块文档失败路径段一致 → 其余 `No owner-doc update required`
- [x] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 2 - AR-11 judgeRegex 方言判定收窄 + docs 同步

Status: completed
Targets: `MetaQualityRuleExecutor.java` + `docs-for-ai/03-modules/nop-metadata.md`

- Item Types: `Fix | Proof`

- [x] AR-11：`isRegexpUnsupported` 子串启发式替换为方言/签名集合匹配——签名集合含 "not supported"/"unknown function"/**"syntax error at or near"（PG 不支持 REGEXP 的真实签名）**；**禁用裸 "syntax"**（H2 支持 REGEXP，非法 pattern 消息可能含 "syntax"，误伤则 H2 真实正则错误被 SKIP）；主方案不改签名，productName 门控为备选（需给 `isRegexpUnsupported`/judgeRegex 增加 productName 参数，按需评估）；MySQL/H2 非法 pattern → 走 ERROR 路径（显式 status=ERROR + message，不再 SKIP）
- [x] 判别性测试：构造含 "Got error ... from regexp" 的 SQLException（单元级，不依赖真实连接）→ ERROR 而非 SKIP；构造 "not supported"/"unknown function" 消息 → 仍 SKIP + details.reason=regexp-unsupported-dialect；**构造 PG "syntax error at or near" 消息 → 仍 SKIP（PG 真实方言不支持不翻 ERROR）**；构造 H2 非法 pattern 消息（含 "syntax" 字面量）→ ERROR（裸 "syntax" 不误伤）；合法 pattern → PASS；修复前 red 实测
- [x] docs 同步：`docs-for-ai/03-modules/nop-metadata.md` §regex 规则方言例外（P2-08）说明更新——SKIP 仅保留给真实方言不支持场景（按签名/方言门控），MySQL 等支持 REGEXP 的方言上规则级正则错误显式 ERROR
- [x] 回归：`TestNopMetaQualityRuleBizModel` / regex 相关既有测试全绿

Exit Criteria:

- [x] "Got error ... from regexp" 类消息判别性测试 red→green 实证；"not supported"/"unknown function"/"syntax error at or near"（PG）类消息仍 SKIP（不误伤）；H2 含 "syntax" 的非法 pattern 消息 → ERROR（裸 "syntax" 不误伤）
- [x] docs-for-ai regex 方言例外段落与 live 行为一致（SKIP 触发面收窄描述同步）
- [x] **无静默跳过**：SKIP 仅剩真实方言不支持场景；其余失败路径显式 ERROR（Minimum Rules #24）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 通过（0 errors）
- [x] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 3 - AR-13 错误参数归属 + AR-14 异常规则结果行与 DTO 对账

Status: completed
Targets: `QualityErrors.java` + `MetaQualityRuleExecutor.java` + `MetaQualityCheckpointExecutor.java` + `MetaQualityScorer.java` + `CheckpointExecutionResultDTO.java` + `QualityRuleResultDTO.java`（nop-metadata-api）+ 相关测试

- Item Types: `Fix | Decision | Proof`

- [x] AR-13 Decision：queryLong/queryTimestamp 静态路径**无 ruleKey 上下文**（judge 调用链无 ruleKey，仅在 judgeCustomSql 内可取）——裁定该路径最小方案 = **改 ErrorCode 声明**，且 **占位替换为 `{sqlHash}` 而非 `{sql}`**（完整 SQL 进入错误消息会随 scheduler `LOG.error(toErrorMessage)` 落日志，与 R8.2 AR-16 脱敏目标冲突）；custom_sql 路径 + evalExpectPassWhen 补 `.param(ARG_RULE_KEY, ruleKey)`（judgeCustomSql :285 已有 ruleKey 变量，evalExpectPassWhen 调用点 :316 在 judgeCustomSql 内）；记录裁定理由
- [x] AR-13 修复：`QualityErrors.java` 两处 ErrorCode 声明按裁定修正（`{ruleKey}` → `{sqlHash}`）；**throw 点接线**（queryLong :634-642 / queryTimestamp :655-657）改 `.param(ARG_SQL_HASH, sqlHashOf(sql))`（sqlHashOf 为既有 public static :416，R8.2 同方法引入计算时保持复用）并移除/替换 `.param("sql", sql)`（NopException.getMessage 会无条件拼入 params，完整 SQL 会随 scheduler/executor LOG.error 落日志——与 AR-16 脱敏目标一致）；`evalExpectPassWhen` 签名增加 ruleKey 参数并在线程化调用点设置真实 ruleKey（替换字面量 `<evalExpectPassWhen>`）
- [x] AR-14 修复（写行时序）：抛异常规则在 catch 中**先 `errors.add → errorCount++`，再于 `orm.clearSession()` 之后**构造 ERROR 判定并 `resultWriter.append`（status=ERROR + message + details）+ `orm.flushSession()`——避免被 clearSession 清掉；**同时 `results.add(buildResultEntry(rule, errorJudgment))`（catch 路径也进 summary.results，保证 ruleResults 条目数与 totalRuleCount/errorCount 可对账）**；异常规则（非 database 类型）的表加入 `affectedTableIds`，使本次 run autoScore 即用 ERROR 行重算该表
- [x] AR-14 DTO Decision + 修复：executor summary 补 `totalRuleCount`（= resolution.rules.size()）与 `skipCount`（:123 局部变量写入 summary）；`CheckpointExecutionResultDTO` 新增 `skipCount` 字段；`QualityRuleResultDTO` 确定性新增 `status`/`message` 字段（nop-metadata-api 两个确定性扩展同批 plan-first 声明）；BizModel 检查点路径按映射契约填充 `totalRuleCount`/`skipCount`/`ruleResults`（summary.results 条目 → ruleResults：qualityRuleId + status + message；其余字段保持单规则路径语义默认值）
- [x] 判别性测试：AR-13 —— 触发 `ERR_QUALITY_SQL_NO_ROW/FAILED` → message 渲染含 `sqlHashOf(sql)` 的**真实哈希值**（非字面子串空洞断言）+ **不含 SQL 原文**（error 消息与日志均无 SQL 字面量，与 R8.2 脱敏一致）；expectPassWhen 非法配置 → param 含真实 ruleKey（非 `<evalExpectPassWhen>`，判别主体）；AR-14 —— mock 规则抛异常 → ERROR 结果行落盘（clearSession 后仍存在）+ 该表进 affectedTableIds + autoScore 本次 run 取到 ERROR（不再旧 PASS）+ DTO totalRuleCount/skipCount/ruleResults 与 summary 一致（**ruleResults 条目数 = totalRuleCount**）；修复前 red 实测
- [x] 回归：`TestNopMetaQualityCheckpointBizModel` / `TestMetaQualityScorer` / 检查点 e2e（cron fireNow 写结果 + 评分）全绿

Exit Criteria:

- [x] AR-13：占位渲染实证（message 无字面 `{ruleKey}`；ErrorCode 占位替换为 `{sqlHash}` 非 `{sql}`——与 R8.2 AR-16 脱敏一致）；expectPassWhen 路径上下文含真实 ruleKey
- [x] AR-14：异常规则 ERROR 行落盘实证（clearSession 后存活）+ 本 run 评分变化实证 + DTO 字段对账一致
- [x] **端到端验证**：检查点执行入口（executeCheckpoint / 调度路径）→ 异常规则 → ERROR 行（clearSession 后存活）→ 本次 run 自动评分（affectedTableIds 含该表）→ DTO 计数全链路断言通过（Minimum Rules #22）
- [x] **接线验证**：`resultWriter.append` 在异常分支被真实调用（结果行 DB 断言或 mock verify）+ `clearSession` 与 append 时序正确（Minimum Rules #23）
- [x] **无静默跳过**：异常规则不再只进 errors 列表；DTO 字段无空置不填（Minimum Rules #24）
- [x] nop-metadata-api 两个确定性扩展已声明 plan-first（本 plan 即裁决载体）+ `nop-metadata-api` 编译/契约测试（`TestNopMetaBizInterfaceCompleteness`）全绿
- [x] 模块文档「检查点执行结果」相关段若有 DTO 字段描述则同步（无则 `No owner-doc update required` 显式记录）
- [x] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 4 - 收口

Status: completed
Targets: roadmap MR8 段 + arm-index §P2 + 全量验证

- Item Types: `Fix | Proof`

- [x] roadmap MR8 段 R8.1 行 → done（注明 5 项 AR 终态 + 测试计数基线变化）
- [x] arm-index §P2 AR-11/12/13/14/15 → fixed（含修复 commit 引用）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` exit 0
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（记录计数基线）
- [x] 独立子 agent closure audit（fresh session）PASS + Closure 段证据写入

Exit Criteria:

- [x] roadmap MR8 段与 arm-index §P2 双向一致（AR-11~15 逐条可追溯）
- [x] 全量测试通过（0 failures/errors/skipped）+ 工具验证 exit 0
- [x] 独立 closure audit READY_TO_CLOSE（含 Anti-Hollow 调用链追踪）
- [x] `ai-dev/logs/2026/08-06.md` 已更新

## Closure Gates

> 关闭条件：本 section 所有条目与每个 Phase 的 Exit Criteria 全部 `[x]` 后，才能将 Plan Status 改为 `completed`。

- [x] AR-11~15 五个已确认 live defect 全部修复（判别性测试 red→green 证据在案）
- [x] 无已确认 live defect / contract drift 被降级到 deferred / follow-up
- [x] docs-for-ai regex 方言例外说明与 live 行为一致；错误码/契约文档同步（若适用）
- [x] 必要 focused verification 已完成（每项 AR 至少一条判别性测试）
- [x] 独立子 agent / 独立审阅者 closure-audit 完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）异常规则结果行写入在运行时真实连通（resultWriter 异常分支被调用 + clearSession 时序正确），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw test -pl nop-metadata -am -T 1C`
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] checkstyle / 代码规范检查通过（历史惯例：插件仅 -Pqa profile，按仓库惯例）

## Deferred But Adjudicated

### AR-14 batch/checkpoint executedCount 口径统一

- Classification: `watch-only residual`
- Why Not Blocking Closure: 审计记录为计数口径不一致（batch 只计不抛规则），非 live 数据缺陷；本 plan 保证 checkpoint 路径 DTO 可对账（totalRuleCount/ruleResults/skipCount 填充），batch 口径统一属跨路径语义对齐，无消费方依赖不一致（DTO 字段此前从不填充）
- Successor Required: no

### AR-13 queryLong/queryTimestamp 路径 ruleKey 参数化（.param 方案）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 静态调用链无 ruleKey 上下文，参数化需改造约 7 个方法签名或 ThreadLocal——超出 AR-13 最小修复面；本 plan 以"改 ErrorCode 声明（占位 → `{sqlHash}`）"达成占位可解析目标（错误可诊断性达成 + 与 R8.2 脱敏一致），参数化属渐进改进
- Successor Required: no

## Non-Blocking Follow-ups

- AR-14 batch 路径 executedCount 口径与 checkpoint 路径对齐（watch-only，随未来质量引擎重构批次）
- AR-13 ruleKey 参数化（.param 方案）若未来重构 judge 调用链时可一并落地

## Closure

Status Note: AR-11~15 五项已确认 live defect 全部修复并经独立 closure audit READY_TO_CLOSE——判别性测试 +16 red→green 实测（git stash 逐项 red 实证），全量 `./mvnw test -pl nop-metadata -am -T 1C` **986/0 全绿**（R7.3 970 基线 + 16），check-plan-checklist/check-doc-links/scan-hollow 全 0；docs-for-ai regex 例外说明与 live 行为一致（AR-11 行为收紧同步）+ 检查点执行结果 DTO 段同步；无已确认 live defect / contract drift 被降级（Deferred 段仅 watch-only residual + out-of-scope improvement 两项，逐条附 Why Not Blocking Closure）。
Completed: 2026-08-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，fresh session）
- Audit Session: `ses_02b189e3effefEqIPmECtciqx1`
- Evidence:
  - **AR-11 PASS**：`MetaQualityRuleExecutor.java:755-759` `REGEXP_UNSUPPORTED_SIGNATURES`（not supported / unknown function / syntax error at or near）+ `isRegexpUnsupported` :761-773 仅签名匹配（无裸 regexp/syntax）；`judgeRegex` :571-584 SKIP 仅签名命中否则 ERROR；`TestMetaQualityRuleExecutorRegexDialect` 6/6 green
  - **AR-12 PASS**：`MetaQualityCheckpointScheduler.java:203-209` cpId==null 在 try 内 + `ERR_CHECKPOINT_MISSING_ID`（QualityErrors.java:23-26）+ buildErrorResult :227；全仓 grep "checkpoint-scheduler-invalid-cron" 0 code hits；`TestMetaQualityCheckpointSchedulerCronReadFailure` 2/2 + `TestMetaQualityCheckpointScheduler.testLegacyJobMissingCheckpointIdSurvives` 8/8（真实 LocalJobScheduler 二次 fireNow job WAITING 非 FAILED）
  - **AR-13 PASS**：`QualityErrors.java:16-22` `{sqlHash}` + ARG_SQL_HASH；queryLong :648-649 / queryTimestamp :669-671 `.param(ARG_SQL_HASH, sqlHashOf(sql))`，`.param("sql"` 全文件 0 残留；evalExpectPassWhen 3-arg :707 + 调用点 :327 真实 ruleKey；`TestMetaQualityRuleExecutorErrorParams` 3/3 + `TestEvalExpectPassWhenErrorPath` 10/10（qualityRuleId=test-rule 判别）
  - **AR-14 PASS**：`MetaQualityCheckpointExecutor.java:156-183` errors.add→errorCount++→clearSession :163→buildErrorJudgment :169→results.add :170→affectedTableIds :171-174→append+flush（独立 try/catch :175-182）；summary totalRuleCount :192 + skipCount :197；DTO skipCount/status/message 字段在位；BizModel :204/:214/:226 + mapRuleResults :397-410；`TestNopMetaQualityCheckpointBizModel` 27/27（testExceptionRuleWritesErrorRowAndRescoresWithError 真实 H2 run2 ERROR 行 + score 100→0 + testCheckpointDtoCountsReconcile totalRuleCount=3/ruleResults=3/skip=1/pass=1/error=1）
  - **AR-15 PASS**：`MetaQualityRuleExecutor.java:249-250` rawAgeMinutes + :254-258 `ageMinutes<0` → FAIL + "maxTimestamp is in the future (clock skew suspected)"；`TestMetaQualityRuleExecutorFreshness` 3/3
  - **Anti-Hollow PASS**：调用链 executeCheckpoint → executor.execute → catch :156 → buildErrorJudgment → results.add → affectedTableIds.add → **resultWriter.append :176（clearSession :163 之后）** → flushSession :177——运行时实证 testExceptionRuleWritesErrorRowAndRescoresWithError（countResults==2 + latest=ERROR + score 100→0，append 未调用必红）；changed files 全文复核无空方法体/静默跳过/TODO-as-done（quality 包 TODO/FIXME 0 hits）
  - **Deferred 分类检查 PASS**：Deferred But Adjudicated 仅 watch-only residual（AR-14 executedCount 口径）+ out-of-scope improvement（AR-13 ruleKey 参数化），无 in-scope live defect 被降级
  - focused 复跑 9 类 63/63 全绿（fresh session 独立执行）；check-plan-checklist --strict exit 0（本段证据写入后核验）/ check-doc-links --strict exit 0 / scan-hollow --severity high exit 0

Follow-up:

- 无 plan-owned 剩余工作（AR-11~15 全部落地；Deferred 段两项 non-blocking 分类逐条附理由）
- 非阻塞观察项（closure audit 记录）：scheduler 缺失 checkpointId 路径日志 checkpointId=null（错误码已在消息内，cosmetic）；AR-14 batch/checkpoint executedCount 口径统一 + AR-13 ruleKey 参数化随未来质量引擎重构批次落地（roadmap R8.x 后续）
