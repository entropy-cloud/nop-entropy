# 2282 深度审计 2026-09-30 P1 缺陷修复（7 项）

> Plan Status: active
> Last Reviewed: 2026-09-30
> Source: `ai-dev/audits/2026-09/2026-09-30-1726-deep-audit-nop-entropy-full/summary.md`（P1 清单 + 复核结论）
> Related: `ai-dev/plans/2283-deep-audit-security-p2-fixes.md`

## Purpose

把 2026-09-30 全仓深度审计确认、并经独立复核保留的 7 条 P1 缺陷全部修复：每项缺陷消除、每项带回归测试、受影响模块测试全绿。审计与复核证据（文件:行号、证据片段、误报排除）以审计报告为准，本计划只记录目标与验收。

## Current Baseline

- 审计目录 `ai-dev/audits/2026-09/2026-09-30-1726-deep-audit-nop-entropy-full/` 已提交（a0d05531c5），7 条 P1 均经 4 个独立复核代理逐条核实保留。
- [G6-22-01]（报告 06）：`nop-wf-service/src/test/resources/_vfs/nop/wf/test/approval-form/v1.xwf` 的 `*end` listener 不判定结束原因，disagree 时业务单据仍被 approve。nop-metadata main 资源中 3 个同类 `*end` listener 已按 `wfRt.wf.record.appState !== 'disagree'` 范式修复，可作参照；`TestUseApprovalE2E` 仅覆盖 agree 路径。
- [G7-04-01]（报告 07）：`nop-batch/model/nop-batch.orm.xml` 的 taskKey 无 unique-key/索引；`DaoBatchStateStore` 启动防重为 check-then-act + 无条件 update（复核证实 UPDATE WHERE 无版本条件、无 affected-rows 检查）。上游提交 bcac549f47 曾自认暂缓。
- [G7-14-01]（报告 07）：nop-retry `UK_RETRY_IDEMPOTENT_ID` 全局唯一，但查重 `findPendingRecordByIdempotentId` 只查 PENDING/RETRYING；COMPLETED/SUSPENDED 记录永久占用幂等键；`blockStrategy` 从未被咨询。`docs-for-ai/03-modules/nop-retry.md:35,79` 已把"回调复用原 service"文档化为已知局限（不在本计划范围）。
- [G9-04-01]（报告 09）：nop-file FILE_HASH 列全链路无写入者；`docs-for-ai/03-modules/reusable-modules-overview.md:19,58` 宣传"Hash 去重"。复核确认 nop-code 有现成 hash 写入范式（`CodeIndexService.java:1695,2745`）。
- [G11-15-01]（报告 11）：`nop-graph/nop-graph-core/.../TarjanSCC.java:80-110` 迭代版恢复帧只合并最后一个子节点的 lowLink；`nop-code-service` 的 `CodeGraphService.tarjanSCC`（约 :1309-1382）有自带修复版及解释注释。复核已独立手工 trace 复现 SCC 错误拆分。
- [G11-03-01]（报告 11）：`nop-code-service/app-service.beans.xml` 未 import Go/C#/Rust 三个 ILanguageAdapter bean；`IocConfigs.java:30-36` 证实 NopIoC 仅自动装载 `beans/app-*.beans.xml`；merged-app.beans.xml 实测只含 Java/Python/TypeScript。service pom 已声明六语言依赖、import resolver 已注册六语言。
- [G2-10-01]（报告 02）：`nop-xlang/.../JsPromise.java` 三处错误路径偏离 JS 语义：(1) executor 抛错被 `ContinuationExecutor.runLoop` 吞掉，promise 永不 settle；(2) rejected promise 接无 onR 的 `then` 时 rejection 被当正常值；(3) `finally` 回调抛错被 `catch (Throwable e) { // ignore }` 吞掉。现有 TestJsPromise 全部 happy path。
- 各模块 target/ 依赖已在本机构建缓存中；测试命令从 worktree 根执行。

## Goals

- 7 条 P1 全部修复且每条带回归测试（先复现后修复：修复前测试能证明缺陷存在，修复后转绿）。
- 受影响模块 `./mvnw test -pl <module> -am` 全绿；不引入新的跨模块回归。
- 修复方式与审计复核确认的根因一致，不引入行为面外的新特性。

## Non-Goals

- 不修任何 P2/P3（安全 P2 在 plan 2283；其余 P2/P3 留待后续 plan）。
- 不重构 nop-wf 引擎的结束状态模型（[G6-22-02] 引擎级 EXPIRED/FAILED 死状态问题不在本计划，模板层修复即达成 P1 收口）。
- 不改 nop-retry 的"回调复用原 service"机制（已文档化局限）。
- 不给 nop-code 休眠的 cluster 分片子系统接线（[G11-03-02]，P2）。
- 不处理 nop-wf-service 之外其他模块的 xwf（复核确认 nop-metadata 3 处已修）。

## Scope

### In Scope

- 上列 7 条 P1 对应的源码/模型/资源修改与回归测试。
- [G7-04-01] 涉及 ORM 模型结构变更（plan-first 区域，本计划即为依据）。
- [G2-10-01] 涉及 nop-xlang 框架核心（plan-first 区域，本计划即为依据）。

### Out Of Scope

- nop-auth 任何变更（protected area，未涉及）。
- 数据库 deploy DDL 的手动重物化（ORM 源模型修正后由生成管线承担；如发现 deploy 脚本需同步，记入 Follow-up 并在 log 声明）。

## Execution Plan

> 四个 Workstream 相互独立、可并行。执行顺序：WS1+WS2 一批，WS3+WS4 一批（控制同 worktree 并发 maven 构建）。

### Workstream 1 - nop-wf 审批模板结束原因判定 [G6-22-01]

Status: completed
Targets: `nop-wf/nop-wf-service/src/test/resources/_vfs/nop/wf/test/approval-form/v1.xwf`、`TestUseApprovalE2E`（或同族测试类）

- Item Types: `Fix`、`Proof`

- [x] 参照 nop-metadata 已修 `*end` listener 范式，修改 v1.xwf 模板：approve 动作仅在结束原因非 disagree 时执行（Fix）
- [x] 新增 disagree 路径回归测试：审批人 disagree 后，流程结束但业务单据状态不得变为已通过（Proof）
- [x] 验证 agree 路径既有测试不回归

Exit Criteria:

- [ ] disagree 后业务单据保持未通过状态有测试断言，且修复前该断言失败（先红后绿）
- [x] agree 路径既有 E2E 全绿
- [x] 无静默跳过：模板层不引入吞异常分支
- [x] No owner-doc update required → 触发补记分支：workflow-configuration.md 原文档无 listener 规则，已新增「事件监听」节 + `*end` 结束原因判定强制规则
- [x] `ai-dev/logs/` 对应日期条目已更新

### Workstream 2 - nop-batch taskKey 唯一性 + nop-retry 幂等键生命周期 [G7-04-01][G7-14-01]

Status: completed
Targets: `nop-batch/model/nop-batch.orm.xml`、`DaoBatchStateStore`、nop-retry 对应 store/record 处理类

- Item Types: `Fix`、`Proof`

- [x] nop-batch ORM 源模型为 NopBatchTask 增加 **(taskName, taskKey) 复合 unique-key** 与查询索引（不得用 taskKey 单列唯一——不同 taskName 需可复用同一 taskKey；查询语义对照 `loadExistingTask` 与 `docs-for-ai/03-modules/nop-batch.md`）（Fix）
- [x] 为 nop-batch-dao 补齐 codegen 触发路径：在 nop-batch-dao/pom.xml 显式声明 exec-maven-plugin(CodeGenTask)（对齐 nop-job-dao 先例），使 ORM 源模型变更经构建同步到生成物；禁止手改 `_gen`/`_app.orm.xml`（Fix）
- [x] DaoBatchStateStore 启动防重改为数据库层有保障的语义：冲突可判定（唯一键冲突或条件更新 affected-rows=0），不再依赖 check-then-act；已 RUNNING 冲突按既有错误语义响亮失败（Fix）
- [x] nop-retry：幂等键查询覆盖全部生命周期状态——存在活跃记录时按 blockStrategy 处理；存在终态记录时不得再撞唯一键（复用结果或显式清除重建，以审计报告 07 的建议方向为准，二选一并在代码注释声明语义）（Fix）
- [x] 两个缺陷各带回归测试：并发/重放场景模拟（单测可用两线程 + 栅栏或直接构造冲突行）。测试基建声明：nop-batch-dao 当前零测试且 pom 无 DB 测试依赖，需按 nop-retry-engine 现成范式（JunitAutoTestCase + @NopTestConfig(localDb=true) + h2）新增 pom test 依赖与测试资源配置（Proof）
- [x] DaoBatchStateStore 补最小直接测试（审计确认其当前零测试）（Proof）

Exit Criteria:

- [x] ORM 模型含 (taskName, taskKey) 复合 unique-key；nop-batch-dao codegen 插件声明后构建产出与源模型一致（核对生成产物包含该约束；如生成链路走通有困难，以离线 xgen 生成并提交产物，记录所用命令）
- [x] 并发双实例场景测试证明只有一个实例能进入 RUNNING（或第二个得到响亮失败）
- [x] COMPLETED/SUSPENDED 记录存在时重提交不再抛裸唯一键冲突，行为有测试断言
- [x] `./mvnw test -pl nop-batch/nop-batch-core,nop-batch/nop-batch-dao,nop-retry/nop-retry-engine -am` 全绿
- [ ] No owner-doc update required（ORM 结构变更随模型走；`03-modules/nop-batch.md` 若描述了 taskKey 语义需核对一致）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Workstream 3 - nop-file FILE_HASH 写入 + nop-graph TarjanSCC 修复 [G9-04-01][G11-15-01]

Status: completed
Targets: nop-file 保存链路（`DaoResourceFileStore`/`NopFileStoreBizModel`/上传 bean）、`nop-graph/nop-graph-core/.../TarjanSCC.java`

- Item Types: `Fix`、`Proof`

- [x] nop-file：在文件保存/入库链路（`DaoResourceFileStore.saveFile`）计算内容 hash 并写入 FILE_HASH；hash 算法与调用范式参照 `nop-code` 的 `CodeIndexService` 既有实现（DigestHelper.sha256Hex）。**裁定（对抗审查 F-2282-1）：本计划只做"摘要落库可查询"，不实现去重复用特性**——live code 无消费 fileHash 的去重路径，新增去重属 scope 漂移（Fix）
- [x] nop-file：回归测试——保存文件后记录的 FILE_HASH 非空且内容变化时随之变化；同内容文件 hash 相同。测试基建声明：nop-file-dao 当前零测试且 pom 无 junit 依赖，需新增 test 依赖（junit + autotest + h2，参照 nop-retry-engine 范式）（Proof）
- [x] nop-graph：修复 TarjanSCC 迭代版 lowLink 合并（以 `CodeGraphService.tarjanSCC` 修复版语义为准），库版本与使用方语义收敛（Fix）
- [x] nop-graph：新增回归测试——用审计复核中"特定图形状"构造反例（DFS 树中存在多个子树回边的形状），断言 SCC 结果与递归定义一致；测试覆盖空图/不连通既有边界 + 自环（自环为新增用例）（Proof）

Exit Criteria:

- [x] FILE_HASH 在正常保存路径有值（测试断言）
- [x] TarjanSCC 新增反例测试修复前失败、修复后通过
- [x] `./mvnw test -pl nop-file/nop-file-dao,nop-graph/nop-graph-core -am` 全绿
- [x] `docs-for-ai/03-modules/reusable-modules-overview.md` 的"Hash 去重"宣称已收窄为与实际行为一致（摘要落库可查询；去重复用为后续特性）——文档同步属本 WS 交付物
- [x] `ai-dev/logs/` 对应日期条目已更新

### Workstream 4 - nop-code 语言 bean 接线 + nop-xlang JsPromise 错误路径 [G11-03-01][G2-10-01]

Status: completed
Targets: `nop-code/nop-code-service/app-service.beans.xml`（或对应手写 beans 文件）、`nop-kernel/nop-xlang/.../JsPromise.java`

- Item Types: `Fix`、`Proof`

- [x] nop-code-service：将 Go/C#/Rust 三个 ILanguageAdapter bean 纳入 `app-*` beans 装载（import 或移入正确文件名），与 pom 六语言依赖、六语言 import resolver 对齐（Fix）
- [x] nop-code-service：接线验证测试——容器装配后六语言 adapter 均可解析（经 merged beans 装载断言，满足 Wiring Verification Rule）（Proof）
- [x] nop-xlang JsPromise 三处修复：executor 抛错 → promise 进入 rejected；无 onR 的 `then` 保持 rejected 透传（不调用 identity 包装值）；`finally` 回调抛错 → promise 转 rejected（不再 ignore）（Fix）
- [x] TestJsPromise 新增错误路径用例：executor 同步抛错、executor 异步抛错、rejected+then(onF only)、then 链透传、finally 回调抛错、finally 正常放行（Proof）

Exit Criteria:

- [x] merged beans 实测含六个语言 adapter bean（测试或 dump 断言）
- [x] JsPromise 六个新用例全部通过，且至少 executor 抛错与 finally 抛错两例在修复前失败（先红后绿）
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 与 `./mvnw test -pl nop-kernel/nop-xlang -am` 全绿
- [ ] No owner-doc update required（修复使实现与既有宣称/JS 语义对齐，无契约变化）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 全部 7 条 P1 对应 in-scope live defect 已修复且各有回归测试
- [ ] 无 in-scope defect 被降级到 deferred / follow-up
- [ ] 每个 Workstream 的 Exit Criteria 全部勾选
- [ ] 受影响 owner docs 已核对（WS1/WS2/WS4 声明 No owner-doc update required 的核对动作已完成；WS3 的 reusable-modules-overview.md 已核对）
- [ ] 独立子 agent closure audit 完成并写入下方 Closure 段
- [ ] Anti-Hollow Check：closure audit 已验证修复路径从入口到出口连通（如：disagree→*end→不 approve；保存文件→hash 落库；容器装配→adapter 可解析），无空方法体/静默吞异常新增
- [ ] `./mvnw test -pl <受影响模块清单> -am` 全绿（汇总命令与退出码记入 log）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module <受影响模块> --severity high` 退出码 0（逐模块执行）

## Deferred But Adjudicated

（无——in-scope 项不允许延期）

## Non-Blocking Follow-ups

- nop-batch deploy DDL 若与 ORM 源模型 unique-key 不同步，由部署侧 dbtool 管线在下次物化时收敛；确认路径后记入 daily log（out-of-scope improvement：不阻塞模型层修复成立）。
- [G6-22-02/03/04]（引擎级结束状态与 withdraw 联动）为 P2，successor 见后续安全/治理批次 plan。

## Closure

Status Note: （closure 时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:（待独立 closure audit）
- Evidence:

Follow-up:

-（待 closure 时确认）
