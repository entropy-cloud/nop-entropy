# R4.3 调度可靠性专项（P2-MA7.5-05 终局 successor：检查点执行幂等——runId/checkpointId 列 + 复合 UK + 执行入口运行标记）

> Plan Status: active
> Last Reviewed: 2026-08-05
> Draft Review: 3 轮独立子 agent 对抗性审查通过——R1 `ses_02ef391dbffen2Wuz99iWcl2Qq`（0 Blocker，3 Major：幂等范围收窄 / DTO runId 字段 / 锁释放时点；6 Minor）；R2 `ses_02ee5fb67ffeBVCi7WBXQXZTIv`（0 Blocker，共识达成，3 Minor 先项）；R3 `ses_02edf6a79ffeGPLFWAK75wZdbq`（0 Blocker，共识达成；2 Minor：错误码落点改 QualityErrors.java + 分布式锁例外条款收紧，已修复；1 Trivial 已处置）。全部 Blocker/Major 清零，裁定可执行。
> Mission: nop-metadata-audit-remediation
> Work Item: R4.3（roadmap MR4 段调度可靠性专项，Deps: R4.1 done）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（R4.3 行 + MR4 终局裁决记录）、`ai-dev/audits/arm-index-nop-metadata.md`（P2-MA7.5-05 行）、MR4 plan（2026-08-05-1408-1，终局 deferred + Successor: yes → R4.3）
> Related: 执行顺序 `{2}` of 2 — 启动门禁：R4.1 done + R4.2（2026-08-05-1625-1）无硬依赖（不同实体，可先后执行）；与 R4.2 同改 `nop-metadata/model/nop-metadata.orm.xml`，建议顺序执行避免生成物冲突

## Purpose

承接 P2-MA7.5-05 的 MR4 终局 successor（out-of-scope improvement，Successor Required: yes）：**为质量检查点执行建立运行期（concurrent）幂等保障**——`NopMetaQualityResult` 增加 **checkpointId/runId 列 + 复合 UK**（ORM 变更 Protected Area，model-first），并在**执行入口增加运行标记**（进程内锁，覆盖 executor + autoScore + dispatchActions 全程），消除**并发/运行期重复触发**（手动双击、手动×cron 并发）导致的重复结果行 + 重复 QualityScore + 重复投递（真实外部副作用）。

> **「重复」定义（Major-1 裁定，写死防止 closure audit 夸大保证）**：本 plan 消除的是**运行期（concurrent）重复触发**——第二次请求到达时第一次执行仍在进行。**顺序重复执行（两次完整执行、间隔超过单次耗时）是保留的时序语义**（每次执行 = 新 runId = 新结果行，这是 NopMetaQualityResult 的设计意图）。arm-index P2-MA7.5-05 终态措辞同步此边界。

**ORM Protected Area，plan-first——本 plan 即裁决载体**。

## Current Baseline

2026-08-05 live repo 核对（与 MR4 Phase 2 裁决依据一致，执行时以重新实测为准）：

- `NopMetaQualityResult`（`nop-metadata/model/nop-metadata.orm.xml:2040-2100`）：**无 checkpointId/runId 列、无业务 UK**；仅 `IX_NOP_META_QRESULT_RULE` 非唯一索引 `(qualityRuleId, executeTime)`（:2094-2099）
- `QualityResultWriter.append`（`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/QualityResultWriter.java:43-61`）：恒新增一行（时序语义 executeTime=now，不覆盖旧行），**无幂等键**
- 检查点编排路径：`NopMetaQualityCheckpointBizModel.executeCheckpoint`（`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityCheckpointBizModel.java:159`，requireEntity 门禁）→ `MetaQualityCheckpointExecutor.execute`（`MetaQualityCheckpointExecutor.java:97-176`，per-rule try/catch + orm.clearSession 失败隔离 :161）→ `resultWriter.append`（:131）→ 自动评分/action 投递（dispatchActions，:201-203 附近，webhook/notify 真实外部副作用）
- **`CheckpointExecutionResultDTO`（`nop-metadata-api/src/main/java/io/nop/metadata/api/dto/CheckpointExecutionResultDTO.java`）当前 13 个字段（:20-32），无 runId 字段**——runId 暴露需新增 DTO 字段 + `executeCheckpoint` summary 携带 + `MetaQualityCheckpointScheduler.buildErrorResult` 同步（Major-2，nop-metadata-api 公共面变更须按 plan-first 声明处理）
- 单规则路径：`NopMetaQualityRuleBizModel.appendQualityResult`（`NopMetaQualityRuleBizModel.java:344-346`）同样走 `resultWriter.append`（无 checkpoint 上下文，checkpointId/runId 应为 null）；**全仓库核实仅 2 处 `resultWriter.append` 调用点**（executor :131 + ruleBizModel 私有助手 :345，单规则 3 个内部调用点 :136/:157/:255 全部经助手隔离——签名变更零回归面）
- 平台守卫现状：`LocalJobScheduler`（`nop-job/nop-job-core/src/main/java/io/nop/job/core/LocalJobScheduler.java`）——cron 自触发 WAITING 态门（:233-243）+ `job.running` 占位 future（:263-266）+ fireNow running 检查（:180-188）覆盖**单 JVM 自重叠**
- 残余面（MR4 终局裁定确认）：手动 GraphQL 双击同一检查点无守卫 → **运行期并发**时重复结果行 + 重复 QualityScore + 重复 webhook/notify 投递（dispatchActions 窗口内第二次请求可穿过平台守卫）；多实例部署无 DB 分布式锁 → 每实例每 tick 各执行一次（多实例扩展面，非当前单实例 baseline 活跃缺陷）
- 绿色基线：`./mvnw test -pl nop-metadata -T 1C` → **858 tests / 0 failures / 0 errors / 0 skipped**（MV V.1 收口，2026-08-05）；`-am` 全 reactor 存在 3 项 pre-existing 失败（nop-xlang / nop-wf / nop-stream-rocksdb，MR4 plan 已文档化归因）
- 相关测试：`TestMetaQualityCheckpointScheduler`（fireNow → 结果落盘 e2e）、`TestQualityResultWriter`（3 个测试以 3 参调用 append，签名变化编译失败可见——需适配）、`TestCheckpointActionDispatcher*`（action 投递）、`TestNopMetaQualityCheckpointBizModel`（GraphQL mutation 路径）
- **`NopMetaQualityResult` xmeta 暴露面（Minor-6）**：`NopMetaQualityResultBizModel extends CrudBizModel` + retention xmeta 无字段覆盖 + web 页面自动生成网格——新列**必然**出现在 GraphQL 类型/过滤器和 UI 网格，属公开契约**确定性扩展**（新增字段非破坏性），需同步 `docs-for-ai/03-modules/nop-metadata.md` 并记录
- 工作树：git status 干净（HEAD `2d20b6d1a` MG 收口提交）

## Goals

- 裁定幂等键设计：runId 生成/传递语义（**runId = UUID**，`StringHelper.generateUUID()` 仓库惯例）、checkpointId 落盘、复合 UK 设计（防同一次执行重复写行）、执行入口运行标记机制（进程内锁 + 作用域 + 释放时点）
- 以 model-first 方式为 `NopMetaQualityResult` 增加 checkpointId/runId 列 + 复合 UK，三方言 DDL 再生成，`_gen/` 重新生成（禁止手编）
- 执行入口运行标记落地：运行期/并发重复触发同一检查点时快速失败（fail-fast）而非静默重复执行（Minimum Rules #24）；**锁覆盖 executor + autoScore + dispatchActions 全程，最外层 finally 释放**
- **`CheckpointExecutionResultDTO` 新增 runId 字段**（nop-metadata-api 公共面变更，plan-first 声明），摘要携带 runId
- `QualityResultWriter` 幂等语义落地：携带 checkpointId/runId，复合 UK 兜底防同 runId 重复行
- 行为回归测试（e2e：运行期重复触发不产生重复结果/投递；并发触发 fail-fast；单规则路径回归），独立子 agent closure audit 通过，roadmap R4.3 → done

## Non-Goals

- 不消除**顺序重复执行**（两次完整执行、间隔超过单次耗时）——时序追加语义保留（每次执行 = 新 runId = 新结果行，Major-1 裁定边界，见 Purpose）
- 不实现跨进程分布式锁（多实例扩展属配置触发或多实例部署场景；**Phase 1 裁定后若判定「单实例 baseline 必须」，仅做设计记录 + 登记 successor，不在本 plan 落分布式锁实现**——预期裁定方向为「不做」，Deferred 段 Successor Required 值在 Phase 4 收口时确定，不悬置）
- 不改 `LocalJobScheduler` / `nop-job` 平台代码（nop-job 为独立模块，平台守卫已覆盖单 JVM 自重叠）
- 不改变单规则执行路径（`NopMetaQualityRuleBizModel`）的外部行为（checkpointId/runId 为 null，保持时序追加语义）
- 不改平台模板（nop-persistence/nop-orm 等 Protected Area）
- 不执行 R4.2（多 schema 专项，独立 plan 2026-08-05-1625-1）

## Scope

### In Scope

- 幂等键设计裁定（runId 语义 + **runId=UUID 约束**、列设计、复合 UK、运行标记机制 + **锁作用域/释放时点**）——Decision
- `NopMetaQualityResult` 模型变更（checkpointId/runId 列 + 复合 UK + 必要索引），model-first + `_gen/` 重新生成
- `deploy/sql/` 三方言 DDL 再生成 + 存量表结构兼容性核对（新列可空 + 复合 UK 对存量全 NULL 行安全——SQL 标准复合唯一索引 NULL 不参与冲突判定，ALTER ADD CONSTRAINT 可安全执行）+ **升级 SQL 落点确定**（`deploy/sql/` 无 `_alter` 目录——落点如新建 `_alter_*.sql` 或 docs 段，Phase 2 裁定）
- `QualityResultWriter` 幂等语义 + 检查点执行入口运行标记（进程内锁，覆盖 executor + autoScore + dispatchActions 全程，最外层 finally 释放）
- **`CheckpointExecutionResultDTO` 新增 runId 字段（nop-metadata-api 公共面，plan-first 声明）** + `MetaQualityCheckpointScheduler.buildErrorResult` 同步
- `NopMetaQualityCheckpointBizModel.executeCheckpoint` 接线（runId 生成 + 运行标记 + fail-fast）
- 行为回归测试（e2e 运行期重复触发 + 并发触发 fail-fast + 单规则路径回归 + `TestQualityResultWriter` 调用点适配）
- arm-index P2-MA7.5-05 行终态更新（**措辞收窄为「运行期重复触发」**）+ roadmap R4.3 → done

### Out Of Scope

- 跨进程分布式锁实现（如裁定需要，登记 successor）
- nop-job 平台调度器修改
- 多实例部署支持
- R4.2 多 schema 专项（独立 plan）
- 非 `nop-metadata` 模块的任何变更

## Execution Plan

### Phase 1 - 幂等键设计裁定 + 运行标记机制裁定

Status: planned
Targets: `NopMetaQualityCheckpointBizModel.java` + `MetaQualityCheckpointExecutor.java` + `QualityResultWriter.java` + `LocalJobScheduler.java`（证据读取）

- Item Types: `Decision | Proof`

- [ ] **live 复核调用链与残余面（Proof）**：读取 `executeCheckpoint`（:159-，含 dispatchActions 投递段 :201-203 附近）+ `MetaQualityCheckpointExecutor.execute`（:97-176，per-rule append :131）+ `QualityResultWriter.append`（:43-61）+ 单规则路径（`NopMetaQualityRuleBizModel:344-346`）；核对 `LocalJobScheduler` 平台守卫覆盖范围（WAITING 门 :233-234、running 占位 :263、fireNow 检查 :180-188）与残余面（手动双击 / 手动×cron 并发）
- [ ] **runId 语义裁定（Decision）**：**runId = UUID**（`StringHelper.generateUUID()` 仓库主键惯例；反向约束：非 UUID 的「checkpointId+时间戳」类方案在 ms 粒度顺序执行时撞 runId 会让 UK 错误拒绝合法执行）——生成时机（executeCheckpoint 入口一次生成）、载体（结果行 + 摘要返回 + DTO）、传递链（BizModel → executor → writer）；单规则路径 runId/checkpointId 置 null
- [ ] **复合 UK 设计裁定（Decision）**：`NopMetaQualityResult` 复合 UK 列清单（候选如 `(checkpointId, runId, qualityRuleId)` 防同次执行同规则重复写行——resolveRules 按 ruleId 去重保证每次执行每规则一行，UK 仅兜底同 runId 重复写）——核对与现有 `IX_NOP_META_QRESULT_RULE` 索引、时序追加语义兼容；**可空列进 UK 的语义确认**：三方言一致——复合唯一索引中任一列 NULL 即不参与冲突判定（存量全 NULL 行不冲突、单规则路径 NULL/NULL 不受约束、检查点路径非 NULL 完全强制），**无需数据迁移**；列契约（checkpointId/runId 可空）
- [ ] **运行标记机制裁定（Decision）**：执行入口防重入机制——进程内锁（per-checkpoint 锁 map，**BizModel 实例字段**——raw impl 与 GraphQL 代理路径汇聚同一 raw 实例，scheduler :211 注入 raw impl 实证）；**锁获取语义 = 非阻塞（putIfAbsent/try-lock），命中即 fail-fast——blocking 实现会让并发重复变串行重复，修复目标落空**（Round-2 审查 E）；**锁作用域 = requireEntity 之后、executor 之前获取，方法体最外层 finally 释放（覆盖 executor + autoScore + dispatchActions 全程，锁不能放 dispatch 内部 try/catch 里）**（Major-3）；与 LocalJobScheduler 平台守卫职责边界（平台按 job 名守卫 cron 自重叠，模块按 checkpointId 守卫手动路径与手动×cron 交叉，均非阻塞拒绝，无死锁）；多实例分布式锁面裁定（当前单实例 baseline 是否必做，裁定不做则显式记录）
- [ ] **cron 路径 fail-fast 语义裁定（Decision，Minor-8）**：cron tick 与手动执行并发时 cron 侧被 fail-fast 拒绝 → 使用专用错误码 + **`MetaQualityCheckpointScheduler.executeScheduledCheckpoint` catch 分支（:212-219 当前无条件 LOG.error）按错误码降级 WARN**——避免 MA7.5-01 catch-all 转 ERROR 造成运维误读
- [ ] **错误码落点裁定（Decision，Round-3 Minor-1）**：`NopMetadataErrors` 为聚合接口（extends QualityErrors/MiscErrors 等，零常量），checkpoint 子域错误码在 `QualityErrors.java`（如 ERR_CHECKPOINT_* 系）/`MiscErrors.java`（ERR_CHECKPOINT_NOT_FOUND 在 :147）——新错误码**写入子域接口文件（QualityErrors.java），经 `NopMetadataErrors` 聚合公开**，不写入聚合接口
- [ ] **升级 SQL 落点裁定（Decision，Round-2 Minor-C）**：`deploy/sql/` 无 `_alter` 目录——落点确定为（新建 `deploy/sql/{dialect}/_alter_*.sql` 或 docs 段），**本项为 Phase 2 执行时裁定**（Phase 2 引用，不在 Phase 1 交付）
- [ ] 裁定记录 repo-observable（本 plan + arm-index P2-MA7.5-05 行）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] runId/复合 UK/运行标记三项设计裁定完成（含 runId=UUID、锁作用域/释放时点、cron fail-fast 语义），结论 + Why 基于 live 复核（非复制旧文），repo-observable
- [ ] 与平台守卫（LocalJobScheduler）职责边界清晰（平台守卫不动，模块内标记只补残余面）
- [ ] `No owner-doc update required`（Phase 1 纯裁定，无代码变更；docs-for-ai 不变）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - model-first ORM 变更 + 生成管线 + DDL 再生成

Status: planned
Targets: `nop-metadata/model/nop-metadata.orm.xml`（NopMetaQualityResult）+ `deploy/sql/**` + `_gen/`（生成产物）

- Item Types: `Fix | Proof`

- [ ] 按 Phase 1 裁定修改 `NopMetaQualityResult`：新增 checkpointId/runId 列（可空，propId 接续 16/17）+ 复合 UK（含 constraint 属性，防 DDL 零 UK 发射——R3.19 教训）+（如裁定）索引调整——**只改源模型，禁止手编 `_gen/` 与 `_*.xml`**（AGENTS.md Hard Stop）
- [ ] `./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C` 重新生成 `_gen/` + `deploy/sql/*/_create_nop-metadata.sql` 三方言 DDL（codegen 管线 `orm/deploy/sql` xgen），核对 `_NopMetaQualityResult.java` 等生成文件；**全文件 git diff 复核**（除 NopMetaQualityResult 列 + UK 外零无关漂移；`_drop_`/`_add_tenant_` 文件同时重生成，同样全量 diff 核对）（Minor-4）
- [ ] 存量库升级路径：新列可空 + 复合 UK 对存量全 NULL 行安全（SQL 标准复合唯一索引 NULL 不参与冲突判定，ALTER ADD CONSTRAINT 可安全执行，无需数据迁移）——升级 SQL 落点按 Phase 2 裁定确定（新建 `deploy/sql/{dialect}/_alter_*.sql` 或 docs 段）+ ALTER 语句 + NULL 语义依据记录（Minor-5）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] orm.xml 变更与 Phase 1 裁定一致（列 + UK + 索引）
- [ ] `_gen/` 重新生成，无手编生成产物；deploy/sql 全文件 git diff 核对（除 NopMetaQualityResult 列+UK 外零差异）
- [ ] 三方言 DDL 再生成，新列 + 复合 UK 约束发射（DDL 断言测试可验证）
- [ ] 存量库升级路径明确（新列可空 → 无数据迁移 + NULL 语义依据；升级 SQL 落点确定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 运行标记 + 幂等写入落地 + 接线

Status: planned
Targets: `NopMetaQualityCheckpointBizModel.java` + `MetaQualityCheckpointExecutor.java` + `QualityResultWriter.java` + `CheckpointExecutionResultDTO.java`（nop-metadata-api）+ `MetaQualityCheckpointScheduler.java`（buildErrorResult + catch 分支）+ `QualityErrors.java`（新错误码）+ 测试文件

- Item Types: `Fix | Proof`

- [ ] **执行入口运行标记（Fix）**：按 Phase 1 裁定在 `executeCheckpoint` 入口实现防重入（进程内 per-checkpoint 锁，BizModel 实例字段）——**锁获取于 requireEntity 之后、executor 之前，最外层 finally 释放（覆盖 executor + autoScore + dispatchActions 全程）；非阻塞获取（putIfAbsent/try-lock），命中即 fail-fast**；并发或运行期重复触发同检查点时 **fail-fast（专用错误码返回，cron 侧 WARN 级）而非静默重复执行**（Minimum Rules #24：不静默跳过、不吞异常）
- [ ] **cron 侧 WARN 接线（Fix，Round-2 Minor-D）**：新专用错误码写入 **`QualityErrors.java`（checkpoint 子域错误码归属文件，经 `NopMetadataErrors` 聚合公开）**；`MetaQualityCheckpointScheduler.executeScheduledCheckpoint` catch 分支（:212-219）按错误码降级 WARN（保持 MA7.5-01 存活语义，仅日志级别区分并发冲突与真实故障）
- [ ] **runId 生成与传递（Fix，Major-2）**：`executeCheckpoint` 入口生成 UUID runId，经 executor 传递至 `resultWriter.append`；**`CheckpointExecutionResultDTO` 新增 runId 字段**（nop-metadata-api 公共面变更，plan-first 声明——本 plan 即裁决载体）+ 摘要携带 + `MetaQualityCheckpointScheduler.buildErrorResult` 同步
- [ ] **幂等写入（Fix）**：`QualityResultWriter.append` 增加 checkpointId/runId 参数（可空，单规则路径传 null），落盘时写入新列；复合 UK 兜底（同 runId 重复写行 → DB 唯一约束拒绝）
- [ ] **接线验证（Fix，Minimum Rules #23）**：确认 executeCheckpoint → executor → writer 调用链上新参数运行时确实传递（非空壳——测试断言落盘行含 runId/checkpointId）
- [ ] **行为回归测试（Fix，Test-Mandated Feature Rule）**：
  - e2e：保存 ACTIVE 检查点 + cron/fireNow 触发 → 结果行含 checkpointId/runId；**同一 runId 二次写入被复合 UK 拒绝**（区分性断言）
  - 并发/运行期重复触发：并发双击同检查点 → 第二次执行 fail-fast（不产生重复结果行/不重复投递 action——**断言覆盖 dispatchActions 窗口**）
  - 单规则路径回归：`NopMetaQualityRuleBizModel` 单规则执行 → 结果行 checkpointId/runId 为 null，行为与修复前一致
  - **`TestQualityResultWriter` 3 个调用点适配新签名**（:34/:49/:62，编译期可见）+ 新增 UK 拒绝单测（Minor-7）
  - `TestNopMetaDdlUniqueKeyEmission` 增加 NopMetaQualityResult 复合 UK 发射断言（防 R3.19 类零 UK 发射复发）
- [ ] 全量回归：`./mvnw test -pl nop-metadata -T 1C`（0 failures；pre-existing 失败按 MR3/MR4 惯例归因记录）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **端到端验证**：从触发入口（fireNow/手动 executeCheckpoint）到结果落盘 + 投递出口的完整路径已验证，**运行期重复触发**不再产生重复结果/投递——见 Minimum Rules #22
- [ ] **接线验证**：runId/checkpointId 从 executeCheckpoint → executor → writer 运行时传递连通（断言落盘行含新列）+ DTO runId 字段经 GraphQL mutation 返回——见 Minimum Rules #23
- [ ] **无静默跳过**：重复触发/并发触发为显式 fail-fast（专用错误码/已运行返回），非 continue/空 catch/吞异常——见 Minimum Rules #24
- [ ] 新增行为有明确测试覆盖（幂等拒绝 + fail-fast + dispatchActions 窗口断言 + 单规则 null 回归 + DDL 断言），区分性断言
- [ ] `./mvnw test -pl nop-metadata -T 1C` 全绿（0 failures）
- [ ] 文档变化：**`CheckpointExecutionResultDTO` runId 字段为公开契约确定性扩展**（新增字段非破坏性）——同步 `docs-for-ai/03-modules/nop-metadata.md`（新列 + DTO runId 字段）+ `ai-dev/design/nop-metadata/` 对应文档；`NopMetaQualityResult` 新列经 xmeta 暴露面同步记录（Minor-6）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 收口（roadmap R4.3 → done + arm-index 终态 + closure audit）

Status: planned
Targets: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md` + `ai-dev/audits/arm-index-nop-metadata.md`

- Item Types: `Decision | Proof`

- [ ] arm-index P2-MA7.5-05 行终态更新（fixed，措辞收窄为「运行期重复触发」，附 plan 引用 + 修复摘要）
- [ ] **Deferred Successor Required 值确定（Decision，Round-3 Minor-2）**：跨进程分布式锁条目 Successor Required 落定（`yes | no`），不留占位符——裁定「不做」则 no 并登记终局；裁定「需要设计」则 yes 并登记 roadmap successor 行（不允许悬挂）
- [ ] roadmap R4.3 行 → done（注明计划引用与修复摘要）
- [ ] 独立子 agent closure audit（fresh session，closure-audit-prompt.md）：逐项核对本 plan 全部 Phase Exit Criteria + Closure Gates，证据写入本 plan Closure 段
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] arm-index + roadmap R4.3 终态一致可追溯
- [ ] 独立 closure audit PASS，evidence 已写入本 plan Closure 段
- [ ] 无静默降级：幂等缺失（P2-MA7.5-05）为 fixed，无 live defect 被降级
- [ ] 文档变化：roadmap + arm-index 更新；docs-for-ai 按 Phase 3 核实结果处理
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 幂等键设计已裁定并落地（runId=UUID/checkpointId 列 + 复合 UK，model-first），**运行期（concurrent）重复触发**不再产生重复结果/投递
- [ ] 执行入口运行标记落地（锁覆盖 executor + autoScore + dispatchActions 全程，最外层 finally 释放），并发/运行期重复触发 fail-fast（无静默重复执行）
- [ ] **`CheckpointExecutionResultDTO` runId 字段已落地**（nop-metadata-api，plan-first 声明），摘要 + buildErrorResult 同步
- [ ] 单规则路径（无 checkpoint 上下文）行为不回归（新列 null，时序追加语义保持）
- [ ] 必要 focused verification 已完成（运行期幂等 e2e + fail-fast + dispatchActions 窗口断言 + 单规则回归 + DDL 断言）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步到 live baseline（DTO runId + xmeta 新列暴露面），或明确写明 No owner-doc update required
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）executeCheckpoint → executor → writer 调用链运行时连通（runId 确实传递并落盘），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C`
- [ ] `./mvnw test -pl nop-metadata -T 1C`（0 failures）
- [ ] checkstyle / 代码规范检查通过（nop-metadata 无独立 checkstyle 命令，以 mvn 构建默认检查为准；历史惯例 "checkstyle N/A"）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（closure 时）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（若修改 docs-for-ai/ 则必跑）

## Deferred But Adjudicated

### 跨进程分布式锁（多实例部署幂等）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前 supported baseline 为单实例部署（MR4 终局裁定）；LocalJobScheduler 平台守卫已覆盖单 JVM 自重叠；多实例部署时每实例各执行一次的重复投递面需配置触发或专门分布式锁设计，非当前单实例活跃缺陷路径；Phase 1 裁定后若判定「必须」仅设计记录 + 登记 successor（Phase 4 落定 Successor Required 值，不悬置）
- Successor Required: `yes | no`（Phase 4 收口时落定）
- Successor Path: （如需要）登记 roadmap 或专门 plan

### 顺序重复执行（两次完整执行，间隔超过单次耗时）

- Classification: `watch-only residual`
- Why Not Blocking Closure: NopMetaQualityResult 为时序追加语义（每次执行 = 新 runId = 新结果行，executeTime=now 不覆盖旧行），顺序重复执行产生「合法的多次审计快照」，非重复数据缺陷；本 plan 只消除运行期并发重复（Major-1 裁定边界）
- Successor Required: `no`
- Successor Path: —

### nop-job 平台调度器修改

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 平台守卫（WAITING 门 + running + fireNow 检查）已覆盖单 JVM 自重叠，功能完整；模块内运行标记只补残余面，不改平台
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- （按执行结果补充：如 Phase 3 发现新的重复投递路径未覆盖，登记观察项）

## Closure

Status Note: 待执行后填写
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: 待独立子 agent 填写
- Evidence: 待填写（每条 Exit Criterion 的验证结果 + check-plan-checklist exit 0 + Anti-Hollow 检查结果 + Deferred 项分类检查）

Follow-up:

- 待执行后填写

## Optional Sections

- `## Risks And Rollback`：ORM 模型变更（Protected Area）——本 plan 即裁决载体（plan-first 声明）；回滚 = 还原 orm.xml + 重新生成 + DDL 回退；`_gen/` 与 `deploy/sql` 全程禁止手编，git diff 核对生成物一致性
- `## Outdated Note`：若执行期间发现调度平台行为被其他工作改变（如 LocalJobScheduler 增加模块级守卫接口），重新评估运行标记机制并上报
