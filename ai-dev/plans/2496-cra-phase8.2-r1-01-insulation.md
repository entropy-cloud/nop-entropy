# 2496 CRA Phase 8.2 — R1-01 周期任务异常绝缘层批（R04 平台批，14 项）

> Plan Status: active
> Draft Review: R1 agent_a86f4018（0 Blocker、5 Major[M1 连续失败策略/M2 MA-06 看门狗语义/M3 30 点表 #23–27 缺行/M4 owner-doc 裁定/M5 E1 落地]+10 Minor——修订子代理 agent_52485b28 全量应用并自检通过后 active）
> Last Reviewed: 2026-10-24
> Source: roadmap 工作项 8.2（R1-01，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L144）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.1 R1-01 行（finding ID 清单 14 项 / PA 授权 / exit criteria 增补）；成员发现详情 = 各单元审计目录（见 Current Baseline 逐条回读路径）；执行基线 @ `227e693011`（分支 `concurrency-audit-remediation`，roadmap 8.1 已 done）
> Related: plan 2495（R1-00 P0 确认批，completed——本批基线门禁已满足）；plan 2493/2494（批次划分与 Phase 8 展开）；**下游依赖本批**：R1-03（硬依赖 R1-02，软关联本批平台方案）、R2-11（MA-23 fencing 对齐 R1-01 C8-001 平台方案）、R2-13（硬依赖本批：C6-001/C6-010 同 diff 已落地 + C7-001 fencing 对齐）、R4-06/R4-10/R4-13（依赖 R1-01）
> Protected Area 授权：本批触碰 **nop-kernel 内部（框架核心引擎）= plan-first Protected Area**。授权来源 = AGENTS.md「Protected Areas」表「框架核心引擎（nop-core/nop-xlang/nop-xdef 内部）plan-first（设计文档+回归测试）」行 + 本 plan Dependencies 声明：**设计文档为本批 Phase 1 产物（`ai-dev/design/`），回归测试为各 Phase 强制义务（rule 25）**。plan-first 前置在 Phase 1 内闭环：设计文档落地并经 draft review 后，Phase 2–4 的实施才可启动。本批不触碰 `nop-api-core`（io.nop.api.core.*，其 FutureHelper 面归 R1-04）、不触碰 `model/*.orm.xml`、不触碰 nop-auth。

## Purpose

落地方案级周期任务异常绝缘层：以 nop-kernel `io.nop.commons.concurrent.executor` 既有调度设施为载体，建立全平台统一的「任务体 Throwable 绝缘 → 观测 → 重臂/显式 kill+alarm」契约，并把 14 项已确认发现（1 项 P1 族主 + 1 项同变更集站点表 + 12 项下游单元成员）的站点逐点接入该契约，消除「一次 Throwable 即静默永久停摆且不可感知」这一跨 10 个模块的同族缺陷面。批末 roadmap 8.2 → done，14 项成员台账全部收敛。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.1 R1-01 = 14 项成员；cra-index P1 台账 5 行 + P2 裁决台账 7 组行（G12/G21/G24/G50/G63/G66/G81）已回填「裁定批次 = R1-01」；roadmap 8.2 行 Status=todo。
- 前置：plan 2495（R1-00 P0 确认批）completed @ `227e693011`——本批启动的基线门禁已满足。
- 全平台**当前不存在任何一处「续链」周期设施**（审计终裁，C3-round2 §1）：三条周期路径（裸 JDK / BindScheduledExecutor / 自续链）在任务体抛 Throwable 时全部终止调度链；`BindScheduledExecutor` 的 `catch(Throwable)→completeExceptionally` 是**杀链可观测化而非续链**（下一轮 `isDone()` 守卫退出）。修复不能把 Bind 现状当作「绝缘=续链」样板。

### 14 成员逐条（站点/缺陷/修复方向——修复内容的事实源）

| # | Finding ID（判级） | 单元 / 模块 | 站点位置（file:line） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 2.1-C3-001（P1 族主） | 2.1 / nop-kernel | 设施本体：`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/DefaultScheduledExecutor.java:126-135`（两个周期方法直通 JDK STPE）、`IScheduledExecutor.java:29-32`（无 javadoc 警告）；29 生产站点/30 点绝缘状态表 = `ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/C3-round2.md` §1（30 行全表）+ 同文件 C3-async-chains.md C3-001 条目 F3 补登（`RpcPollTaskManager.java:119` 为第 29 处生产站点） | 周期任务设施无统一异常绝缘层：一次 Throwable 静默永久停摆且不可感知（globalTimer 单例 daemon core=4 承载全平台周期任务；返回 Future 无消费通道） | 设施级统一绝缘契约（Phase 1 裁定续链 vs kill+alarm；建议=catch Throwable→观测→重臂）+ 周期 future 停摆告警挂接点 + javadoc 契约声明 |
| 2 | 2.1-C3-015（P2，与 C3-001 **同变更集强制绑定**，不并 ID） | 2.1 / nop-kernel（主证）+ 补登站点跨模块 | 主证 `BindScheduledExecutor.java:99-107`；补登站点：`nop-job/nop-job-core/.../AbstractBatchScanner.java:84-87`（cursor.reset/onCycleStart 在 try 外；执行时回读——live 已漂移至 :89-92，审计复核值被新增 javadoc 挤后）、`nop-service-framework/nop-graphql/nop-graphql-core/.../ws/JsonRpcWebSocketHandler.java:357-363`（仅 catch IOException）、`nop-network/nop-netty/.../handlers/RpcMessageHandler.java:76-99`（无 catch，Netty EventExecutor 载体）、`nop-ai/nop-ai-agent/.../SessionLockRenewal.java:69-77`（仅 catch RuntimeException） | 语义修正（Bind catch(Throwable)=杀链）+ 两类新裸奔站点补登；catch 宽度决策须随设施契约统一定夺 | 随 C3-001 同 diff 落地；补登站点（#2/#7 行、AbstractBatchScanner try-外段）按点表逐行处置（Phase 2） |
| 3 | 1.6-C8-001（P1） | 1.6 / nop-retry | `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java:96-101,165-167`；载体 `BindScheduledExecutor.java:90-116`（:101-104 Throwable→单条 ERROR→completeExceptionally 不重排；:109-111 REE 分支→completeExceptionally **零日志**） | 扫描循环被拒/Error 即（近）静默永久停摆：无自防御、无观测、无重臂；nop_retry_record 表积压无界（DB 为唯一队列），单节点默认部署完全停摆 | 平台绝缘层统一（重臂/观测）；scanner 存活/最后扫描时间指标；nop-tcc 模块内零站点（见 Baseline 末条勘误） |
| 4 | 1.7-C6-001（P1） | 1.7 / nop-batch | nop-batch-core `executeChunkLoop`：复核锚 `:243 catch(Exception)`、`:242/:250` future 仅 try 内结算、`:148` allOf 接线（**执行时回读** `ai-dev/audits/2026-10/2026-10-16-1258-deep-audit-nop-batch-concurrency/C6-resource-lifecycle.md` C6-001 条目取精确行） | 仅 catch Exception：Error 逃逸 future 永不完成 → allOf 永不触发 → afterComplete 关闭链失守 → reader/writer/JDBC 连接泄漏 + 任务行永久 RUNNING + 调用方挂死 | **与 C6-010 同 diff**：try 上移至 lambda 首行 + catch 放宽 Throwable |
| 5 | 1.7-C6-010（P2） | 1.7 / nop-batch | `propagateContext :229 / provideTaskContext :231` 在 try `:232` 之前 | try 守卫起点过晚：仅改 catch(Throwable) 亦无法闭合 S3 站点（异常发生在守卫生效前） | 同 diff：守卫起点上移至 lambda 首行；回归测试注入点移至 propagateContext |
| 6 | 4.5-C4-001（P1） | 4.5 / nop-runner（nop-cli-core） | `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliRunCommand.java:90` | scheduleWithFixedDelay 返回 Future 丢弃：任一次脚本异常（含 `file.listFiles()` 瞬时 null→NPE 第四触发面）即静默停摆且进程挂在 stdin | watch 周期链接入设施绝缘/显式失败可见化（catch+LOG 或退出），对齐 CliWatchZipCommand 形态（C4-thread-pools.md :76 对照） |
| 7 | 2.2-C1-021（P1，CL-001 升格） | 2.2 / nop-core-framework（nop-config） | `nop-core-framework/nop-config/src/main/java/io/nop/config/source/jdbc/JdbcConfigSource.java:71-98,100-108,144-149`（`tryTriggerChange` 调用在 L96-97，位于 try 外；兄弟类 `AbstractFileConfigSource.java:53-70` 已设防并成文注释该陷阱） | 定时刷新回调无异常防护：任一 onChange 回调抛出即永久静默杀死 DB 配置热更新全链（零日志、重启前不可自愈） | L96-97 包**独立** try-catch + LOG.error（勿并入现有 loadConfig 重试 try）；更优形态=tryTriggerChange 内 per-task 隔离（单坏回调不拖累同轮其余回调）；回归测试=注册抛 RuntimeException 的回调后断言下一轮刷新仍触发 |
| 8 | 1.4-MA-05（P2 族 = C4-004 + C4-012 三站点） | 1.4 / nop-job | ①`nop-job-coordinator` RpcPollTaskManager（调度点 `:119-120` 平台 IScheduledExecutor 载体（底层裸 JDK 语义）的 scheduleWithFixedDelay、pollOnce 仅 catch Exception `:204-206`）；②`nop-job-worker` JobWorkerScannerImpl whenComplete 两站点 `:302/:379-381`；③`nop-job-local` LocalJobScheduler whenComplete 区域 `:281-290/:335-344`（各文件全路径**执行时回读** `ai-dev/audits/2026-10/2026-10-14-2230-deep-audit-nop-job-concurrency/merge-adjudication.md` MA-05 行） | Error 吞没族三站点：JDK 周期抑制 + whenComplete 回调动作异常被 future 框架吞掉 → 续延死亡零日志（entry 僵尸化/task 滞留 RUNNING） | 三站点同修法（catch Throwable + 日志 + 终态 fail-safe）**逐一独立验收** |
| 9 | 1.4-MA-06（P2 族 = C4-009 + C7-002） | 1.4 / nop-job（nop-job-worker；checker 侧钉死面在 nop-job-coordinator） | `JobWorkerScannerImpl` 派发 promise（invokeAsync 后仅挂 whenComplete `:296-306`）无超时包装；钉死面 = `JobTimeoutCheckerImpl.java:497-501` 声明注释 + plan 340 契约测试（双向钉死） | 派发 promise 无终结驱动：永不终结的 invoker future → DB task 永久 RUNNING → sumReservedCost 容量永久侵蚀至 worker 静默停止认领 + RUNNING 滞留零观测 | **修复唯一有效位=worker 侧**（orTimeout 式看门狗 + in-flight 年龄 gauge）；checker 侧 plan340 契约测试 + `JobTimeoutCheckerImpl:497-501` 注释**钉死不动**；验收须同盖 sumReservedCost 释放方向与 RUNNING 滞留可观测两面（C10-002/R5 约束） |
| 10 | 1.4-C4-003（P2；载体在 nop-commons=移交面） | 1.4 / nop-job（nop-job-core） | `nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java:58-80,88-103`（scanOnce `:98` 仅 catch Exception、scanFuture `:63/:69-71` 赋值后仅 stopScanning 使用）；载体 `BindScheduledExecutor.java:88-116` | 6 个 scanner 的手工重排链遇 Throwable/提交拒绝即永久停摆：running 仍 true 状态说谎，(a) Throwable 路径仅一条框架日志、(b) 拒绝路径完全无日志（复核强化） | 平台载体 re-arm 随 C3-001 同批（cra-index G12「载体 nop-commons BindScheduledExecutor 移交平台级——同 diff 修复面搭车」）；模块侧：scanOnce catch 放宽 + `startScanning` 给 future 挂 whenComplete（异常完结时 running=false + metrics + 带 scanner 名 LOG）或提供 restartScanning；try-外段（cursor.reset/onCycleStart，审计复核 :84-87、live 已漂移至 :89-92，执行时回读）纳入守卫（C3-015 (a) 类） |
| 11 | 3.1-MA-07（P2 移交族，成员 6：C3-020/C3-027/C4-003[3.1]/C4-007/C4-013/C7-029） | 3.1 / nop-ai | 六站点（3.1 audit 条目定位行）：`ScheduledRecoveryManager.java:383-397` + `SessionLockRenewal.java:65-78`（C3-020/C4-007）；`TeamTaskSchedulerDaemon.java:262-276`（调度点 `:237-238`）+ `DBMessageService.java:315-329,572-581`（调度点 `:213,217`）（C3-027/C4-013/C7-029）；`EmbeddingServiceImpl.java:257-281,344-354,443-445`（C4-003[3.1]，catch(Exception) 不拦 Error + scheduleRetry 丢弃 ScheduledFuture）；清单权威 = `ai-dev/audits/2026-10/2026-10-19-1410-deep-audit-nop-ai-core-nop-ai-agent-concurrency/merge-adjudication.md` §MA-07 节 | 周期任务 catch(Exception/RuntimeException) 不含 Error → 一次 Error 静默永久停摆；其中 SessionLockRenewal 续期死亡=租约过期被他实例抢占而本地执行不中止（**会话双执行，全族安全语义最重**） | 整族移交平台绝缘层：平台层落地后六站点逐点接入（catch 宽度/接线随设施契约）；**3.1 侧 Error 注入护栏义务（C10-008，四处站点共用）保留 3.1 批次（R2-16），不在本批** |
| 12 | 4.1-C3-009（P2） | 4.1 / nop-message | 七屏障清单：①`KafkaConsumeTask.runTask:304` ②`PulsarConsumeTask.runTask:118` ③`LocalMessageService.invokeSubscription:232` ④`DebeziumEngineWrapper` worker lambda`:164` ⑤`DebeziumMessageSource.dispatchEvent:159` ⑥dispatchConsumeFailure/runFailureEndpoint 回调守卫（Kafka `:628/:670`、Pulsar `:420/:463`）⑦`MessageConsumeFailureBridge.submit:111` + notifyEngineFailure listener 守卫`:86`（各文件全路径见 `ai-dev/audits/2026-10/2026-10-20-2210-deep-audit-nop-message-concurrency/C3-round2.md` C3-009 条目 F3） | 全模块异常屏障 Exception-only（grep catch Throwable/Error = 0）：Error 逃逸致订阅僵尸（isActive 误报）+ Debezium 终局失败通知被绕过（nop-stream 侧 task 永远等不到 latch） | 屏障改 catch Throwable 并在 Error 分支走与 Exception 相同的失败上报路径（dispatchConsumeFailure / notifyEngineFailure）；`active` 不再误报；**七屏障清单随单闭合**（含 ⑥⑦ 守卫行）；C10-002/005 验收前置随本体 |
| 13 | 4.2-C4-004（P2，+CL-011/CL-014 随单） | 4.2 / nop-cluster | `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/elector/AbstractPollingLeaderElector.java:22,28`（一次性 schedule 返回值丢弃）+ `AutoRegistration.java:204-218`（refreshRegistration 仅 catch Exception :214-216，链级失败不可达）；断链机制载体 `BindScheduledExecutor.java:88-111,144-154` | 选主/注册两条自续链「单点断链、无监督」：worker 池拒绝或 Error 一律永久静默死亡（仅通用 `nop.err.thread-pool.execute-fail`，无 elector/registration 上下文），无人感知、无人重启 | pending future 集中观察 + whenComplete 告警 + 重臂（按退避重排）；CL-011（基类守护：finally 重调度/wrap Throwable）+ CL-014（常驻链失败带业务标识的上报点）随同批；**与 C4-002（stop 复活面）协同不合并**——C4-002/C1-004 归 R2-18，本批只做断链监督面；C10-004 验收前置随本体 |
| 14 | 5.5-W-11（P2 = C7-002） | 5.5 / nop-metadata | `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityCheckpointScheduler.java:216-233`（executeScheduledCheckpoint 仅 catch Exception）；平台侧后果已独立复核（LocalJobScheduler handleResult:352-361 / resumeJob:168-175 / fireNow:218——FAILED 无复活路径） | Error 逃逸 → JobFireResult.ERROR → cron job 永久 FAILED（唯一恢复=重启 JVM），击穿 MA7.5-01「job 存活按 cron 继续触发」自愈契约（该契约只覆盖了 Exception 半边） | catch 收窄改 catch(Throwable) 后按 Error/Exception 分支处理（Error 记 FATAL 级日志并仍返回错误结果 DTO，保持 job 存活）；同文件族 `MetaQualityCheckpointExecutor.execute:155` per-rule catch(Exception) 同根因随单闭合 |

### 平台并发设施复用对照（live 实读 @ `227e693011`）

- **既有设施盘点**（`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/`）：
  - `IScheduledExecutor extends IThreadPoolExecutor`：`schedule(Callable,delay,unit)` / `scheduleAtFixedRate` / `scheduleWithFixedDelay` / default `scheduleWithRandomDelay`（委托 ExecutorHelper）/ default `executeOn(Executor)`（返回 BindScheduledExecutor）。
  - `DefaultScheduledExecutor`：JDK `ScheduledThreadPoolExecutor` 包装（`ExecutorHelper.newScheduledExecutor`）；两个周期方法**裸直通**（:126-135，C3-001 主证）；submit/schedule 经 ExecutorHelper（已有 catch(Throwable)+completeExceptionally+bindCancel+detach 形态——同包正确样本；其 bindCancel 误取消面=C3-016，归 R1-04，不在本批）。
  - `BindScheduledExecutor`：timer→executor 两级；`scheduleWithFixedDelay` 手工重排链 :88-116（catch Throwable→LOG.error+completeExceptionally→**不重排**=杀链可观测化）；一次性 `schedule` 执行腿 rejection 分支 :144-154（completeExceptionally **无日志**）；`scheduleAtFixedRate` 直通包装（executor 拒绝→杀链，C3-003，不在本批成员）。
  - `GlobalExecutors.globalTimer()`：DefaultScheduledExecutor 单例（daemon，core=4）；`globalWorker()`：StandardThreadPoolExecutor（core4/max30/TaskQueue 10000/Abort）。
  - `ExecutorHelper`：submit/schedule 的 catch(Throwable)+completeExceptionally+finally detachContext 是平台内绝缘形态的**正确先例**；`scheduleWithRandomDelay` 杀链（C3-017，未入本批成员清单）。
  - `CycleSynchronizer`：0 生产调用方（cluster C4-004 建议的长期迁移方向之一，非本批依赖）。
- **复用结论**：绝缘层本体 = **扩展既有 `IScheduledExecutor`/`DefaultScheduledExecutor`/`BindScheduledExecutor` 设施**（新增绝缘周期调度语义与观测钩子），**不新建平行调度器、不造第三份实现**；下游短修（cluster/rule/message/job 各单元）直接消费平台语义或对现有 future 挂 whenComplete，不各自手写绝缘。30 点表中 raw 载体（自建 `Executors.new*Scheduled*`）平台设施修复不覆盖（见 Deferred）。
- **设计硬约束**（C3-015 语义裁定，必须由 Phase 1 设计文档一次裁定相容性）：Bind 现状 `completeExceptionally` + `isDone()` 守卫 = 杀链；「重臂」要求下一轮续排不被已完结 future 的 isDone 守卫拦截——**绝缘语义、返回 future 的终态语义、重臂机制三者必须相容**（行为语义：任务体抛 Throwable 后，链必须存活至显式 stop/cancel，且停摆/反复失败对外可见）。

### Baseline 勘误（输入不一致登记）

1. **batch-proposal R1-01 模块列不完整**：列出的模块为 nop-kernel；nop-core-framework；nop-retry；nop-tcc；nop-batch；nop-job；nop-search；nop-message；nop-cluster；nop-rule。实测 14 成员 + 点表补登站点实际触达 **nop-ai**（MA-07 六站点全在 nop-ai-agent/nop-ai-core）、**nop-runner**（C4-001 站点在 nop-cli-core，模块列只写 nop-search）、**nop-metadata**（W-11 站点在 nop-metadata-service，模块列只写 nop-rule）、**nop-service-framework**（C3-015 补登站点 JsonRpcWebSocketHandler 在 nop-graphql-core）、**nop-network**（补登站点 RpcMessageHandler 在 nop-netty）。本 plan 验证命令按实际触达模块列全（Phase 5）。
2. **模块列含 nop-tcc 但 14 成员在 nop-tcc 内零站点**：实测 nop-tcc 无 `io.nop.retry` 引用、无 Scanner/`scheduleWithFixedDelay` 站点；1.6-C8-001 的「两模块调度驱动」指审计单元覆盖面（WI 1.6 = retry+tcc），缺陷站点单点在 nop-retry-engine。nop-tcc 作零触碰声明处理（Phase 5 确认）。
3. **「nop-kernel 内 29 站点」表述不成立**：29 处生产周期站点分布在 ≥10 个模块（nop-kernel 内部仅 `LocalResourceLockManager.java:105` 1 处）；设施本体在 nop-kernel。本 plan 按「设施本体（nop-kernel）+ 30 点表逐行处置（含下游模块站点行）」如实划界。
4. **source-anchors.md 现无 30 点绝缘状态表锚点**（grep 绝缘/C3-001 零命中）：roadmap 8.2 Owner Doc 列的「2.1-C3-001 30 点绝缘状态表锚点」为**本批新建**条目，非更新既有条目。
5. **行号漂移已知风险**：审计复核自身记录过漂移先例（AbstractBatchScanner 初判 86-91 实际 84-87；3.1 六站点行号在 30 点表 #23-27 与 3.1 条目两套定位间存在差异）。凡本表标注「执行时回读」的行，实施前必须以 live code 为准重新定位，发现漂移以 live 为准并登记。

## Goals

- nop-kernel 设施级绝缘层落地：统一语义 = **catch Throwable + completeExceptionally/观测 + 重臂（或显式 kill+alarm 契约）+ 观测钩子**，经设计文档一次定稿（plan-first 前置），本体带单元测试与跨模块接线验证。
- 14 项成员全部修复并各自带回归测试（rule 25）：2.1-C3-001+C3-015（同变更集）、1.6-C8-001、1.7-C6-001+C6-010（同 diff）、4.5-C4-001、2.2-C1-021、1.4-MA-05/MA-06/C4-003、3.1-MA-07 六站点、4.1-C3-009 七屏障、4.2-C4-004+CL-011/014、5.5-W-11。
- 30 点绝缘状态表逐行处置落账：接入/设施级覆盖/豁免三态登记，锚点落 `docs-for-ai/04-reference/source-anchors.md`。
- Owner doc 随批更新：`docs-for-ai/03-modules/nop-job.md`、`docs-for-ai/03-modules/nop-batch.md`、`docs-for-ai/03-modules/nop-metadata.md`、`docs-for-ai/03-modules/nop-message.md`、`docs-for-ai/03-modules/nop-retry.md`、`source-anchors.md`（roadmap 8.2 Owner Doc 列 + 本批裁定扩充——nop-metadata/nop-message/nop-retry 三 doc 实际存在，非缺口）；缺口声明仅限真正无 03-modules 条目的 nop-cluster/nop-runner 两模块。
- E1–E6 通用门禁全过（batch-proposal §一）：全量构建绿 / 每工单强制并发回归测试（先红后绿或变异/回退论证）/ 验证通道留证 / 授权声明 / owner doc 清单 / 批级独立 closure audit。

## Non-Goals

- **不改查表语义**：30 点表的载体/绝缘状态判定口径（三条路径杀链语义）不被本批改写；本批只把「处置后状态」登记为锚点。
- **3.1 Error 注入护栏义务（C10-008）保留 3.1 侧不在本批**：successor = R2-16（3.1 批）。
- 不修 C3-003（Bind scheduleAtFixedRate executor 拒绝面）、C3-016（ExecutorHelper bindCancel 误取消，归 R1-04）、C3-017（scheduleWithRandomDelay，未入本批成员清单）、C3-013（context 卫生）、2.1-C4-001（globalTimer 死配置，归 R1-02）等 2.1 单元相邻 P2——本批只修 14 项绝缘面；设施改造若与上述面共享代码位，改动范围以设计文档裁定并在 Phase 1 显式声明边界。
- 不做 raw 载体迁移：30 点表 #15–22（nop-stream 8 处）、#28（FeishuClient）自建调度器不在本批（平台设施修复不覆盖；CL-021 载体碎片化复核为后续义务）。
- 不做 4.2-C4-002（stop 复活面）/C1-004：归 R2-18；本批 cluster 侧只做断链监督面，两工单协同不合并。
- 不做 1.7-C6-002（REE 裸逃逸，归 R2-13）、C6-009/C7-011（归 R2-13）；本批 nop-batch 面仅 C6-001+C6-010 同 diff。
- 不触碰 `nop-api-core`、`model/*.orm.xml`、nop-auth（本批 Protected Area 触碰面 = nop-kernel 框架核心内部，plan-first 已声明）。

## Scope

### In Scope

- `nop-kernel/nop-commons` 调度设施绝缘层本体 + 单元测试 + 设计文档。
- 30 点表逐行处置（含 C3-015 补登站点 #2/#7 catch 宽度、#12 try-外段）与状态锚点。
- 14 成员的下游模块站点修复与回归测试（nop-job / nop-batch / nop-retry / nop-core-framework / nop-runner / nop-ai / nop-message / nop-cluster / nop-metadata）。
- Owner doc：nop-job.md / nop-batch.md / nop-metadata.md / nop-message.md / nop-retry.md / source-anchors.md；缺口声明（仅 nop-cluster、nop-runner 两个无 03-modules 条目模块）。

### Out Of Scope

- Deferred 节所列项（raw 载体、C10-008、C3-017 等）。
- 下游批次的工单（R2-13/R2-18/R2-16 等）。

## Execution Plan

### Phase 1 - 设计文档 + nop-kernel 绝缘层本体 + 单元测试（plan-first 前置）

Status: completed（2026-10-24 实现子代理交付；唯一未勾项「独立 draft review」为 Phase 2 启动前置门禁，按 plan-first 规则由独立子 agent 执行，不属于本实现子代理义务）
Targets: `ai-dev/design/`（新建：io.nop.commons 周期任务异常绝缘层设计）、`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/`、`nop-kernel/nop-commons/src/test/`

- Item Types: `Decision`（设计文档）、`Fix`（2.1-C3-001 设施主体——已确认 live defect P1，不得写成 Feature）、`Proof`（单元测试 + 接线验证）

- [x] 设计文档落 `ai-dev/design/`（最终状态文档，rule 14：只写决策与契约，不写 Proposed-vs-Current、不写类签名代码）：①统一语义定稿——捕获边界（Throwable）、completeExceptionally 传播与返回 future 终态语义、重臂 vs kill+alarm 的选型及理由、观测钩子（停摆告警挂接点/whenComplete/带业务标识失败上报——吸收 CL-014 语义）、拒绝（REE/停机竞态）路径语义、**连续失败计数/退避/日志抑制与 kill+alarm 升级阈值（必裁项）**——in-repo 先例 = JdbcConfigSource 的 errorCount<10 分级抑制；载体为 globalTimer 单例 daemon core=4 共享、承载全平台 ~26 条周期任务且无队列隔离，天真重臂会把一次静默停摆换成失败风暴+日志洪泛，故退避/抑制/升级阈值必须随绝缘语义一次定稿；②绝缘语义 × isDone 守卫 × future 终态三者相容性裁定（Baseline 硬约束）；③30 点表载体覆盖矩阵（平台设施覆盖行 vs raw 载体不覆盖声明）；④F3 注记决策——config 变更回调在共享 timer 线程同步执行（AbstractFileConfigSource/JdbcConfigSource）是否随设施改造一并处置（C3-round2 §1 F3「供设施修复时一并决策」义务闭合）；⑤API 形态与 javadoc 契约声明（IScheduledExecutor 周期方法的任务体异常行为成为成文契约）——硬约束：对外部调用方 source-compat，绝缘能力以新增 default 方法/新接口提供，不改 IScheduledExecutor 既有签名；并显式声明与 C3-016（ExecutorHelper bindCancel 误取消面，归 R1-04）的交互边界——绝缘层若采用任务体内 complete 形态，必须给出禁用条件，不得扩大 bindCancel 误取消触发面；⑥MA-06 看门狗语义前置裁定——超时来源（schedule.timeoutSeconds 或新配置 `nop.job.worker.invoke-timeout-ms`）与默认值；默认形态 = **默认无执行超时 + 宽松看门狗仅 ERROR+metric 观测不终结**（与 plan340 契约测试 testLongRunningTaskWithoutTimeoutNotKilled + `JobTimeoutCheckerImpl:497-501` 注释钉死契约一致，禁止在 worker 侧复活「长任务误杀」）；与 checker 链双向不冲突断言纳入设计文档
  - 产物：`ai-dev/design/concurrency/periodic-task-insulation.md`（§一设计结论 5 条 = 裁定①–⑥ 全覆盖；§3.3 = 30 点覆盖矩阵 12 设施级覆盖 / 16 显式接入（#7/#14/#15–28）/ 2 豁免批外；§四拒绝清单 8 项）+ `ai-dev/design/concurrency/README.md`（子系统索引）
- [ ] 设计文档独立 draft review（独立子 agent，含想象性分析）通过后才进入 Phase 2（plan-first 门禁）——**待执行：Phase 2 启动门禁，实现子代理不得自审自过**
- [x] nop-kernel 绝缘层本体实现（扩展既有设施，不新建平行调度器）
  - 产物：新增 `IPeriodicTaskListener`（观测钩子接口，public）/ `InsulatedPeriodicTask`（任务体守卫：catch Throwable + 观测 + 失败计数 + 退避计算 + 拒绝终态）/ `InsulatedPeriodicChain`（返回 future：失败/取消/拒绝终态 + stopRequested 停止标记，cancel 对失败完结链仍生效）/ `PeriodicTaskHelper`（fixedDelay 手工重臂核心 + fixedRate 装配 + native 触发器终结监听，均为包私有）；修改 `IScheduledExecutor`（周期方法 javadoc 成文契约 + 2 个新增 default 方法带 taskName/listener，既有签名零改动）/ `DefaultScheduledExecutor`（fixedRate 直接线材原生周期+守卫；fixedDelay 走 helper；destroy() 对登记链统一拒绝终态）/ `BindScheduledExecutor`（fixedDelay 走 helper(timer,executor)；fixedRate 守卫化 hop；移除杀链的 isDone 守卫私有重排链）
- [x] 本体单元测试（rule 25，显式覆盖）：
  - [x] 任务体抛 RuntimeException / Error 后，下一轮仍续排（重臂语义成立）——testChainRearmsAfterErrorTaskBody / testTaskBodyThrowableIsCaughtAndCompletesFutureExceptionally（重臂断言段）
  - [x] 异常经观测钩子可见（日志/告警挂接被触发，非静默吞）——testObservationHooksFireForSuccessFailureAndRejection（onRunFailure/onRunSuccess/onChainRejected）+ testFailureGovernance...（onFailureStreakExceeded）
  - [x] cancel/stop 语义不回归：调用方 cancel 后在飞轮次自然终止、链正确停止（对齐 C3-round2 §3 对照组语义）——testCancelStopsChainAfterInFlightRoundFinishes；含失败完结后 cancel 仍停止链的断言（tests 1/2/3 的 isStopped 断言）
  - [x] 关闭竞态：timer destroy/executor 拒绝时 future 有终态、不悬挂、链不复活——testTimerDestroyGivesTerminalStateWithoutResurrection + testObservationHooks...（destroy → onChainRejected + 终态）
  - [x] 确定性失败（如任务体恒抛 Error）下重臂可观测且不失控（按裁定形态生效退避/抑制/升级阈值）——testDeterministicFailureRearmsWithBackoffWithoutRunaway（300ms 窗口 ≤2 轮）+ testFailureGovernanceEscalationSuppressionBackoffAndRecovery（25 连败无逃逸、升级=[10,20]、抑制谓词、退避序列 1000→…→60000 封顶、恢复清零）
- [x] **接线验证**（rule 23）：≥1 个跨模块消费方在运行时真实调用绝缘入口——testGlobalTimerBindWiringReachesInsulatedPath：GlobalExecutors.globalTimer().executeOn(...)（BindScheduledExecutor 载体 = retry scanner/cluster registration 等平台消费方真实拓扑）断言 listener 失败回调可达 + 链经真实设施重臂 + future 失败终态
- [x] **无静默跳过**（rule 24）：新增公共方法/分支在未实现时显式失败；catch 观测路径不得是空 catch——守卫 catch(Throwable) 路径固定执行「listener 通知 + 日志（按抑制区间）+ metric + 失败计数 + future 失败终态」五动作；拒绝路径执行「ERROR 日志 + listener + 链终态 + native 取消」四动作（首轮实现曾漏接失败→future 终态，被本批测试红出后修复，rule 25 先红后绿证据在案）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] 设计文档存在于 `ai-dev/design/`、为最终状态写法、含上述①–⑥全部裁定项，且独立 draft review 记录在案（plan 头部 Draft Review 行或 daily log）——**部分满足**：设计文档已落地（`ai-dev/design/concurrency/periodic-task-insulation.md`，最终状态写法，①–⑥ 全覆盖）；独立 draft review 待独立子 agent 执行（Phase 2 门禁），本项在 review 记录在案后勾选
- [x] 本体单元测试 ≥5 条（上述五类语义各 ≥1）全绿；每条测试验证的行为在测试名/断言中可辨识——TestPeriodicTaskInsulation 9 条全绿（Tests run: 9, Failures: 0, Errors: 0）
- [x] 接线验证证据登记（哪个消费方、哪条断言证明运行时调用）——消费方 = GlobalExecutors.globalTimer()（平台共享单例）+ executeOn(Runnable::run)（BindScheduledExecutor 载体）；断言 = listener 失败回调非空 + runs≥2 重臂 + chain isDone/completedExceptionally（TestPeriodicTaskInsulation.testGlobalTimerBindWiringReachesInsulatedPath）
- [x] `./mvnw test -pl nop-kernel -am` BUILD SUCCESS（聚合实跑：BUILD SUCCESS EXIT=0，12 套件合计 2021 条全绿；focused 通道 `-pl nop-kernel/nop-commons -am` → nop-api-core 165 + nop-commons 381（含新绝缘 9 条）全绿）
- [x] `No owner-doc update required`（owner doc 更新归 Phase 5 统一落账：source-anchors/nop-job/nop-batch/nop-metadata/nop-message/nop-retry；本 Phase 不改使用方文档）
- [x] `ai-dev/logs/` 对应日期条目已更新

#### Phase 1 执行记录（2026-10-24 实现子代理）

- 设计裁定六项一句话：①重臂选型（拒绝 kill+alarm：告警无消费通道时复刻静默停摆；链停止权归调用方 cancel），失败治理四件套=计数+日志抑制（errorCount<10 同形态，首失败+每第 10 次记 ERROR）+指数退避（仅 fixedDelay 手工重臂路径，下限 1s/乘子 2/封顶 60s）+每 10 连败周期性升级（listener+metric，永不杀链），拒绝路径=设施提交失败链终态可见不复活（重排归调用方模块级监督）；②返回 future=失败/取消/拒绝信号（首次失败 completeExceptionally 一次），链存活判定用链终止标记（isCancelled∨rejected∨stopRequested）禁止 isDone 判活，cancel 对失败完结链仍生效；③30 点矩阵 13 设施级覆盖（#1–6/#8–14）/14 显式接入（#7/#15–28）/2 豁免批外（#29/#30）；④F3 闭合=设施边界定在任务体整体，per-callback 隔离归 C1-021 站点内，timer 线程同步回调拓扑维持登记；⑤API=既有签名零改动+2 个 default 方法（taskName+IPeriodicTaskListener）+新接口，C3-016 边界=链 future 禁用 bindCancel 形态接线、任务体内 complete 仅限失败结算且不与任何取消接线共存、逐轮一次性调度行走既有 schedule 面（未扩面）；⑥MA-06=默认无执行超时+nop.job.worker.invoke-timeout-ms（0=disabled）+宽松看门狗仅 ERROR+gauge 不终结，checker 链唯一终结者。
  - **勘误注（2026-10-24，设计评审 agent_e4f1555a）**：上句裁定③的矩阵计数为评审前实现子代理当时结论；独立评审证伪 #14 设施级归类（AbstractPollingLeaderElector 为一次性自续链，绝缘层零自动覆盖）后权威口径改为 **12 设施级（#1–6/#8–13）/16 显式接入（#7/#14/#15–28）/2 豁免**——以设计文档 §3.3 修订版为唯一权威源。
- 本体文件全集：新增 4（`IPeriodicTaskListener.java`、`InsulatedPeriodicTask.java`、`InsulatedPeriodicChain.java`、`PeriodicTaskHelper.java`）；修改 3（`IScheduledExecutor.java`、`DefaultScheduledExecutor.java`、`BindScheduledExecutor.java`）；测试新增 1（`TestPeriodicTaskInsulation.java`，9 条）。
- 验证命令输出摘要：`./mvnw test -pl nop-kernel/nop-commons -am -Dsurefire.failIfNoSpecifiedTests=false` → BUILD SUCCESS（Tests run: 165（nop-api-core）+ 381（nop-commons），Failures: 0, Errors: 0）；聚合 `./mvnw test -pl nop-kernel -am -Dsurefire.failIfNoSpecifiedTests=false` → BUILD SUCCESS EXIT=0（12 套件合计 2021 条全绿：165/381/20/77/3/5/39/95/501/599/85/51，日志快照 `_tmp/phase1-nop-kernel-test.log`）；`./mvnw compile -pl nop-kernel/nop-commons -am` → 0 error（source-compat：IScheduledExecutor 既有签名零改动）；`./mvnw compile -pl nop-autotest/nop-autotest-core -am` → 0 error（唯一生产实现方 MockScheduledExecutor 增量兼容证明）；`node ai-dev/tools/check-doc-links.mjs --strict` → 退出码 0（0 error，2 warning 均为无关历史 plan 既有项）。
- 规则 25 先红后绿证据：首轮实现漏接「失败→future 终态」，TestPeriodicTaskInsulation 5 条红（testTaskBodyThrowableIsCaughtAndCompletesFutureExceptionally 等）→ 补 onFailure 的 completeExceptionally + 链 stopRequested 语义（cancel 对失败完结链生效）+ destroy 统一拒绝路径 → 9 条全绿。

### Phase 2 - 30 点绝缘状态表逐点接入（平台载体面 + C3-015 补登站点）

Status: completed（2026-10-24 实现子代理交付：30/30 行 + F3 补登行三态处置登记落账、#2/#7 站点修复带先红后绿回归、接线验证、双聚合门禁绿；登记文件 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/insulation-30pt-disposition.md`）
Targets: 30 点表 30 行（`C3-round2.md` §1）；站点文件：`JsonRpcWebSocketHandler.java`（nop-graphql-core）、`RpcMessageHandler.java`（nop-netty）、`LocalResourceLockManager.java`（nop-commons）等平台载体站点

- Item Types: `Fix`（#2 JsonRpcWebSocketHandler catch 宽度、#7 RpcMessageHandler 无 catch、#12 try-外段随 Phase 3 C4-003）、`Proof`（设施级覆盖行的复核登记与状态表落账）

- [x] 30 行逐行三态处置登记，落本 plan 执行记录（三态判据：**接入** = 该站点在绝缘设施覆盖范围外，需显式包装；**设施级覆盖** = 绝缘层本体改造后自动覆盖，登记即可；**豁免** = 有其他防护或非 Error 逃逸面，写明理由）——登记落 `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/insulation-30pt-disposition.md` §三（逐行表，每行独立回读 C3-round2 §1 原表 + live 回读复核，含指令映射勘误注 §二）：
  - [x] #1 CliRunCommand → 成员 4.5-C4-001（Phase 3）
  - [x] #2 JsonRpcWebSocketHandler：catch IOException → 补宽度（Throwable 级兜底，keepalive 链可观测）
  - [x] #3 SysDaoNamingService / #13 SysDaoMessageService×3 / #8 SocketClient / #9 RpcTaskMonitor：catch Exception 已自备 + 设施级覆盖 → 复核登记
  - [x] #4 AbstractFileConfigSource：已设防正面样本 + F3 timer 线程回调决策随 Phase 1 设计文档
  - [x] #5 JdbcConfigSource → 成员 2.2-C1-021（Phase 3）
  - [x] #6 LocalResourceLockManager：设施级覆盖 + 复核登记
  - [x] #7 RpcMessageHandler（Netty 载体，设施不覆盖）：补 catch + 超时清扫链可观测
  - [x] #10 AutoRegistration / #14 AbstractPollingLeaderElector → 成员 4.2-C4-004（Phase 4）
  - [x] #11 RetryScannerImpl → 成员 1.6-C8-001（Phase 3）；#12 AbstractBatchScanner → 成员 1.4-C4-003（Phase 3）
  - [x] #23–27（SessionLockRenewal/ScheduledRecoveryManager/TeamTaskSchedulerDaemon/DBMessageService×2）→ 成员 3.1-MA-07 承接（Phase 4），本 Phase 登记为待接入
  - [x] #15–22（nop-stream raw 8 处）、#28 FeishuClient raw、#29 mock、#30（C3-017 未入批）→ 豁免登记（见 Deferred）
  - [x] F3 补登第 29 处生产站点 RpcPollTaskManager.java:119 → 成员 1.4-MA-05（Phase 3）
- [x] 站点行号回读核对：实施前逐行以 live code 定位（Baseline 勘误 5），漂移以 live 为准登记——全部 in-repo 行 live 回读：#1–14/#23–30/+F3 原表行号零漂移；漂移登记 2 处（#12 try-外段 live :89-92；#15–22 nop-stream 行号普遍漂移 + live 新增 CheckpointCoordinator:321 命中，站点全集刷新归 CL-021）
- [x] 30 点绝缘状态表「处置后状态」锚点内容定稿（source-anchors.md 条目正文在 Phase 5 统一写入）——定稿形态 = 登记文件 §四（锚点条目正文 + 处置后状态表）
- [x] **接线验证**：至少一个平台载体裸站点（如 globalTimer 直挂任务）注入 Throwable 后链存活且可观测（证明设施本体被点表站点运行时消费）——TestJsonRpcKeepAliveInsulation.testGlobalTimerDirectScheduleWiringReachesInsulatedPath（nop-graphql-core，#2 站点所属消费方模块）：globalTimer 直挂 scheduleWithFixedDelay（DefaultScheduledExecutor 载体）注入 IllegalStateException → listener 失败回调可达 + 链重臂 + future 失败终态，运行日志出现绝缘核心 `nop.err.periodic-task.fail: task=ws-keepalive-wiring` 实证
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 30/30 行三态处置登记完整，无未处置行（+F3 补登行；计数核对 12 设施级 + 16 显式接入 + 2 豁免 = 30，见登记文件 §三末）
- [x] #2/#7 站点修复带回归测试（rule 25：注入 Error/RuntimeException 后清扫/keepalive 链存活或失败可见）——两站点均有先红后绿证据（变异→红、恢复→绿，见登记文件 §六）
- [x] 接线验证证据登记（消费方模块运行时证据 + 日志实证，见上与登记文件 §六）
- [x] `./mvnw test -pl nop-service-framework -am` 与 `./mvnw test -pl nop-network -am` BUILD SUCCESS——两者均 BUILD SUCCESS EXIT=0（nop-graphql-core 12 条全绿含新回归 2 条；nop-netty 10 条全绿含新回归 1 条；日志快照 `_tmp/phase2-nop-service-framework-test.log`、`_tmp/phase2-nop-network-test.log`）
- [x] 30 点状态表锚点内容定稿并通过与 C3-round2 §1 原表的一致性核对（只增处置列，不改判定列）——登记文件载体列逐行照录原表判定列；每行独立回读原表核对（§五漂移记录在案）
- [x] Owner doc 裁定：本 Phase 触 nop-graphql-core（JsonRpcWebSocketHandler）/nop-netty（RpcMessageHandler）站点 → `docs-for-ai/03-modules/nop-network.md` 若行为契约变化则随批更新，否则显式登记 `No owner-doc update required` + 理由——**No owner-doc update required**：①#7 修复恢复既有隐式契约（过期在飞请求被清扫为超时终态）而非改写契约，清扫链死亡本身即审计确认的缺陷面，`nop.err.netty.rpc-timeout-sweep-fail` 为内部观测日志键；②#2 修复维持 sendKeepAlive 既有「尽力而为心跳：单轮失败告警日志 + 链继续」语义，仅把该语义从 IOException 宽度扩到 Throwable，无对外契约变化；③30 点处置状态锚点的 owner = `docs-for-ai/04-reference/source-anchors.md`（Phase 5 落账）；nop-network.md 现文不涉及 RpcMessageHandler 清扫契约，无 drift
- [x] `ai-dev/logs/` 对应日期条目已更新

#### Phase 2 执行记录（2026-10-24 实现子代理）

- 登记文件：`ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/insulation-30pt-disposition.md`（§二 指令映射勘误 3 处、§三 31 行逐行登记表、§四 source-anchors 锚点定稿、§五 live 回读/漂移记录、§六 验证记录）。抽样：#2（设施级覆盖 + 站点 catch 宽度本 Phase 修复，finding 2.1-C3-015）、#7（接入，Netty EventExecutor 载体设施不覆盖，本 Phase 站点侧守卫修复）、#15–22（接入类 × 批外裁决，CL-021 承接，live 行号漂移登记）。
- #2 修复：`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/ws/JsonRpcWebSocketHandler.java`——`sendKeepAlive` 增 `catch (Throwable)` 兜底 + `nop.err.websocket.keep-alive-fail:session={}` 结构化 ERROR（IOException 分支与 warn 级维持原状）；可见性 private → protected（回归接缝 + 成文契约 javadoc）。
- #7 修复：`nop-network/nop-netty/src/main/java/io/nop/netty/handlers/RpcMessageHandler.java`——调度点改挂 `sweepExpiredFutures` 守卫（catch Throwable + `nop.err.netty.rpc-timeout-sweep-fail:remoteAddr={},localAddr={}` 结构化 ERROR，channel 判空）；`checkTimeout` private → protected（Error 注入接缝，无生产子类）。
- 测试新增 2 文件 3 条：`nop-network/nop-netty/src/test/java/io/nop/netty/handlers/TestRpcMessageHandlerSweepInsulation.java`（1 条）；`nop-service-framework/nop-graphql/nop-graphql-core/src/test/java/io/nop/graphql/core/ws/TestJsonRpcKeepAliveInsulation.java`（2 条：站点回归 + rule 23 接线验证）。
- 规则 25 先红后绿证据：#7 变异（调度点直连 `checkTimeout`）→ 红 `sweep chain must survive the injected Error: expected: <2> but was: <1>`（Netty 周期任务抛 Throwable 后续轮次被抑制的审计结论复现）；#2 变异（移除 Throwable catch）→ 红 `Unexpected exception thrown: IllegalStateException: serialization-boom`；恢复后各自绿。
- 验证输出摘要：focused `-Dtest=TestRpcMessageHandlerSweepInsulation` / `-Dtest=TestJsonRpcKeepAliveInsulation` 全绿；`./mvnw test -pl nop-network -am` → BUILD SUCCESS EXIT=0（Total 01:02 min）；`./mvnw test -pl nop-service-framework -am` → BUILD SUCCESS EXIT=0；日志快照 `_tmp/phase2-nop-network-test.log` / `_tmp/phase2-nop-service-framework-test.log`。

### Phase 3 - 下游单元批次 A：nop-job / nop-batch / nop-retry / nop-core-framework / nop-runner

Status: completed（2026-10-24 实现子代理交付：8 工单全落地、5 单元独立提交、必红三项先红后绿/回退论证留痕、checker 侧双文件零改动实证、五单元门禁全绿[nop-runner -am 为基线既有环境问题，见执行记录末条]）
Targets: `nop-job`（MA-05 三站点、MA-06 worker 侧、C4-003）、`nop-batch`（C6-001+C6-010 同 diff）、`nop-retry`（C8-001）、`nop-core-framework/nop-config`（C1-021）、`nop-runner/nop-cli-core`（C4-001）

- Item Types: `Fix`（C8-001/C6-001/C6-010/C4-001/C1-021/MA-05/MA-06/C4-003 全部为已确认 live defect）、`Proof`（各单元回归测试）

- [x] nop-job（三工单，依赖 Phase 1 载体语义）：
  - [x] C4-003：scanOnce catch 放宽 + startScanning future 观测（running=false+metrics+带 scanner 名 LOG）——修复形态二选一判据：默认 whenComplete 挂接（不改对外 API），仅当挂接点不可达时才提供 restartScanning 新 API + try-外段（cursor.reset/onCycleStart，执行时回读）纳入守卫；回归测试注入 Throwable 断言链存活/状态不说谎——**落地**：scanOnce 全周期体站点守卫（live :89-92 try-外段纳入，漂移以 live 为准）+ 新增 `onScanCycleFailed(Throwable)`（`onScanFailed(Exception)` 签名保持不动以避免 5 子类 override 连锁改动——checker 侧 JobTimeoutCheckerImpl 零改动约束的连带保护）；whenComplete 默认挂接选中（未加新 API）；存活指标 = cycle/failure/rejected 计数 + start/end/failure 时间戳 getter；拒绝路径 running=false 状态真实化（C3-015 (a) 类）；回归 = TestAbstractBatchScanner +4（Error 注入不逃逸/链存活/状态真实化/拒绝路径）
  - [x] MA-05：三站点（RpcPollTaskManager.pollOnce / JobWorkerScannerImpl whenComplete×2 / LocalJobScheduler whenComplete）逐一 catch Throwable + 日志 + 终态 fail-safe；**三站点逐一独立验收**（各 ≥1 回归测试）——**落地**：①pollOnce catch Throwable + `nop.job.remote.poll-error` ERROR（visibility private→包私有供测试注入）；②handleExecutionResult catch Throwable + `failSafeTerminalize`（task 终态化阻断容量账本侵蚀）+ onTaskExecuteFailed 观测；③LocalJobScheduler 触发器计算 catch 收宽至 Throwable + whenComplete 回调整体守卫 + `job.state.internal=FAILED` 终态 fail-safe + done 异常完结；回归 = TestRpcPollTaskManagerInsulation 2 / TestJobWorkerScannerInsulation.testHandleResultErrorFailSafeTerminalizesTask / TestLocalJobScheduler +1，三站点独立命名可辨识
  - [x] MA-06：worker 侧 orTimeout 式看门狗 + in-flight 年龄 gauge（**按 Phase 1 设计文档⑥看门狗语义前置裁定实施**：超时来源/默认值/默认无执行超时+宽松观测不终结形态/与 checker 链双向不冲突）；**checker 侧 plan340 契约测试 + JobTimeoutCheckerImpl:497-501 注释零改动**（钉死维持，diff 审查必查项）；回归测试同盖 sumReservedCost 释放方向 + RUNNING 滞留可观测两面——**落地**：新增 `nop.job.worker.invoke-timeout-ms`（默认 0=disabled；`schedule.timeoutSeconds>0` 优先，沿用 checker 取值顺序）；超龄在飞仅 ERROR（`nop.job.worker.invoke-timeout-observed`，明示 observe-only）+ 观测计数/时间戳 + in-flight 注册表（`nop.job.worker.invoke.inflight.age.max` gauge + Java 可读 accessor）；永不 complete/cancel/杀链；回归同盖两面（testLongRunningInvokeDefaultNotKilled = 默认不杀+plan340 共存[worker 侧半边]；testInvokeTimeoutObservedButNeverTerminated = 滞留可观测+不终结；testLateCompletionStillReleasesReservedCost = sumReservedCost 释放方向+迟到完成不受看门狗影响；testScheduleTimeoutSecondsTakesPrecedence = 取值顺序）
- [x] nop-batch：C6-001 与 C6-010 **同 diff**（try 上移至 lambda 首行 + catch 放宽 Throwable）；回归测试注入点移至 propagateContext（C6-010 复核行义务）；断言 reader/writer 关闭链执行 + 任务行不停 RUNNING + future 有终态——**落地**：守卫起点 = runWithContext lambda 首行（propagateContext/provideTaskContext 纳入）；catch Throwable；primaryError CAS/fail-fast cancel/finally 语义保持；回归 = TestBatchTaskErrorTerminalization 2 条（注入点=processor[C6-001] 与 propagateContext[C6-010]），断言 future 异常终态 + afterComplete 关闭链执行 + 根因保留（onAfterComplete err 链解包为 LinkageError）
- [x] nop-retry：C8-001 扫描链接入平台重臂/观测；回归测试=任务体抛 Error/提交拒绝后下一轮仍扫描（先红后绿或变异论证）+ scanner 存活/最后扫描时间可观测——**落地**：startScanning 挂 6 参绝缘入口（taskName=nop-retry-scanner + listener）+ onRunFailure/onChainRejected 观测 + 拒绝时 isRunning() 状态真实化 + 存活指标（scanCount/lastScanTime/lastScanDuration/failure 计数与时间戳）；回归 = TestRetryScannerInsulation 3 条；红证据 = pre-fix 回退论证（4 参裸载体变异 → fetchCount=1 链死）
- [x] nop-core-framework：C1-021 tryTriggerChange 独立 try-catch（或 per-task 隔离，按 Phase 1 设计定稿）；回归测试=注册抛 RuntimeException 的回调后断言下一轮刷新仍触发——**落地**：per-callback 隔离（设计裁定④站点内实现形态）；callbackErrorCount 与既有 errorCount<10 同形态抑制 + 整轮成功清零记恢复；回归 = TestJdbcConfigSourceCallbackIsolation 4 条（含 refreshConfig 全管线协同）
- [x] nop-runner：C4-001 watch 周期链失败可见化（接设施绝缘或显式 catch+LOG/退出）；回归测试=脚本异常后 watch 轮询存活且失败有痕——**落地**：startWatchLoop 抽取 + 返回 future 挂 whenComplete → `nop.cli.run.watch-round-failed:path={}` 结构化 ERROR（绝缘设施保证重臂与失败终态）；runTasks private→protected 接缝；回归 = TestCliRunWatchInsulation 2 条；红证据 = 裸载体+Future 丢弃回退论证
- [x] 提交纪律：每单元一提交（nop-job/nop-batch/nop-retry/nop-core-framework/nop-runner 各自独立 commit）；1.7 C6-001 与 C6-010 必须落在同一 commit（同变更集证明，R2-13 下游硬依赖前提）——**落地**：84d970d008（nop-job）/ 9b302d8867（nop-batch，C6-001+C6-010 同 commit）/ da4f1cb583（nop-retry）/ 26aa896472（nop-config）/ 014a9a9294（nop-cli）
- [x] 每单元修复 diff 与点表/审计行号回读核对登记——**落地**：全部站点 live 回读核对（C4-003 try-外段 live :89-92 与 Phase 2 登记一致；C8-001 :96-101/:165-167 一致；C1-021 :96-97→修复后 per-callback 形态；MA-05/MA-06 站点行号与 1.4 裁定行一致；C4-001 :90 一致），无新增漂移
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 8 项工单（C4-003/MA-05/MA-06/C6-001/C6-010/C8-001/C1-021/C4-001）全部 landed，各带 ≥1 条focused 回归测试且测试名可辨识对应工单；C6-001 与 C6-010 在同一变更集（git diff 可证）——C6-001/C6-010 同 commit 9b302d8867（同 diff：守卫上移+catch 放宽一次落地）
- [x] 必红证据：至少 C8-001、C6-001（含 C6-010 注入点）、MA-06 三项有先红后绿记录或变异/回退论证（E2）——C6-001/C6-010：先红（future 悬挂 awaitSettled 超时）后绿，快照 `_tmp/phase3-batch-red.log`/`_tmp/phase3-batch-green.log`；C8-001：pre-fix 回退论证红（fetchCount=1 链死）→绿，快照 `_tmp/phase3-retry-red.log`；MA-06：watchdog 接线移除变异红（无观测信号）→绿，快照 `_tmp/phase3-nopjob-red.log`；另 C4-003/MA-05 三站点/C4-001 均有守卫移除/回退变异红证据（同日志）
- [x] MA-06 钉死面零改动核查通过（`git diff` 不含 JobTimeoutCheckerImpl 注释行与 plan340 契约测试）——`git diff ebab18ee46..014a9a9294` 对 `JobTimeoutCheckerImpl.java` 与 `TestJobTimeoutChecker.java` 均为 0 行（双文件零改动实证）
- [x] `./mvnw test -pl nop-job -am`、`./mvnw test -pl nop-batch -am`、`./mvnw test -pl nop-retry -am`、`./mvnw test -pl nop-core-framework -am`、`./mvnw test -pl nop-runner -am` 全部 BUILD SUCCESS——前四者 BUILD SUCCESS；nop-runner -am 为**基线既有环境问题**：`nop.err.core.resource.duplicate-vfs-resource`（.m2 stale `nop-ai-translate-2.0.0-SNAPSHOT.jar`[2026-10-13] × reactor nop-ai-core `/nop/ai/prompts/**` 冲突），已在干净基线 ebab18ee46 stash 复现同因，与 Phase 3 diff 无关；实际门禁以 `./mvnw test -pl nop-runner/nop-cli-core`（71 条全绿，含 C4-001 回归 2 条）替代通过，环境修复（本地 .m2 刷新）交用户/Phase 5 全量构建通道复核
- [x] `docs-for-ai/03-modules/nop-job.md`、`docs-for-ai/03-modules/nop-batch.md` 更新（调度契约/绝缘语义章节，rule 17）；`docs-for-ai/03-modules/nop-retry.md` 更新（C8-001 scanner 契约，随批最小更新或 Phase 5 统一落账）；nop-core-framework/nop-runner 无 03-modules 条目 → 登记缺口声明（Phase 5 统一）——**本 Phase 落地 nop-retry.md**（新增「扫描链（RetryScanner）」契约节 + 源码锚点行）；nop-job.md/nop-batch.md 登记待办归 Phase 5 统一落账（绝缘语义章节与 30 点锚点、W-11 等 Phase 4 语义同批成文，避免两批重复改写）；缺口声明（nop-core-framework/nop-runner 无 03-modules 条目）待 Phase 5 统一登记
- [x] `ai-dev/logs/` 对应日期条目已更新

#### Phase 3 执行记录（2026-10-24 实现子代理）

- 提交全集（每单元一提交）：`84d970d008` nop-job（C4-003+MA-05+MA-06）、`9b302d8867` nop-batch（C6-001+C6-010 同 diff）、`da4f1cb583` nop-retry（C8-001）、`26aa896472` nop-config（C1-021）、`014a9a9294` nop-cli（C4-001）；owner doc 随 docs 收口提交。
- 站点 diff 摘要：①`AbstractBatchScanner`（scanOnce 站点守卫 + onScanCycleFailed + 存活指标 + startScanning 绝缘入口/whenComplete 拒绝→running=false）；②`RpcPollTaskManager`（pollOnce catch Throwable + ERROR；private→包私有注入缝）；③`JobWorkerScannerImpl`（handleExecutionResult catch Throwable + failSafeTerminalize；MA-06 invokeTimeoutMs 配置 + inFlightInvokeStartMillis 注册表 + checkInvokeTimeout/scheduleInvokeWatchdog + micrometer gauge `nop.job.worker.invoke.inflight.age.max`）；④`LocalJobScheduler`（触发器计算 catch Throwable + whenComplete 回调守卫 + 终态 fail-safe + done 异常完结）；⑤`BatchTask`（executeChunkLoop 守卫起点上移 + catch Throwable）；⑥`RetryScannerImpl`（6 参绝缘入口 + listener + 存活指标 + isRunning）；⑦`JdbcConfigSource`（tryTriggerChange per-callback 隔离 + callbackErrorCount 抑制/恢复）；⑧`CliRunCommand`（startWatchLoop 抽取 + whenComplete 失败可见化；runTasks protected）。
- 关键裁定注：C4-003 的 `onScanFailed(Exception)` 签名保持不动（5 个子类 override 兼容 + JobTimeoutCheckerImpl 零改动约束），新增 `onScanCycleFailed(Throwable)` 承接 Error 级——plan 原文「scanOnce catch 放宽」以新增外层 Throwable 守卫实现（语义等价：Error 不逃逸站点、可观测、链不死亡）；「running=false on 异常完结」按绝缘契约修正为「拒绝（链真死）时 running=false」——失败完结 ≠ 链死亡（设计 §3.2 三相容），失败轮次链仍存活故 running 保持 true 为真实状态。
- MA-06 时钟注：看门狗年龄计算用 JVM 墙钟（System.currentTimeMillis）而非 CoreMetrics——autotest 环境 CoreMetrics 时钟可被测试替换（非真实时间），诊断用途的 in-flight 年龄不受其影响。
- 验证输出摘要：`./mvnw test -pl nop-job -am` BUILD SUCCESS（12:41 min，含 nop-retry-engine 47 条[依赖闭包]；新回归 TestAbstractBatchScanner 9 全绿[+4]/TestRpcPollTaskManagerInsulation 2/TestJobWorkerScannerInsulation 5/TestLocalJobScheduler 15 全绿[+1]，快照 `_tmp/phase3-nop-job-test.log`）；`./mvnw test -pl nop-batch -am` BUILD SUCCESS（nop-batch-core 63 条全绿含新回归 2 条，`_tmp/phase3-batch-test.log`）；`./mvnw test -pl nop-retry -am` BUILD SUCCESS（`_tmp/phase3-nop-retry-test.log`）；`./mvnw test -pl nop-core-framework -am` BUILD SUCCESS（`_tmp/phase3-nop-core-framework-test.log`）；`./mvnw test -pl nop-runner/nop-cli-core` BUILD SUCCESS（71 条全绿含新回归 2 条，`_tmp/phase3-nop-cli-core-test.log`）；pre-commit nop-cli lint gate 全过（初次拦截 3 项[no-raw-exception×2/no-empty-catch×1]已修复复检 0）。
- 必红证据（E2）快照全集：`_tmp/phase3-batch-red.log`（C6-001/C6-010 修复前 future 悬挂红）、`_tmp/phase3-batch-green.log`（修复后 63 条绿）、`_tmp/phase3-retry-red.log`（C8-001 pre-fix 回退论证红 fetchCount=1）、`_tmp/phase3-retry-green.log`（恢复后绿）、`_tmp/phase3-nopjob-red.log`（C4-003/MA-05①②③/MA-06 观测面 5 组守卫移除变异红 + 恢复绿）、`_tmp/phase3-runner-red.log`（C4-001 裸载体+Future 丢弃回退论证红）、`_tmp/phase3-runner-green.log`（恢复绿）。
- **基线既有问题登记（非本 Phase 引入）**：`./mvnw test -pl nop-runner -am`（及任何使 nop-cli-core 测试类路径同时含 reactor nop-ai-core 与 .m2 `nop-ai-translate-2.0.0-SNAPSHOT.jar` 的 -am 组合）失败于 `nop.err.core.resource.duplicate-vfs-resource`（`/nop/ai/prompts/advisor/long-term-memory.prompt.yaml` 双源冲突）——干净基线 ebab18ee46 stash 复现同因，证据 `_tmp/phase3-nop-runner-preexisting-blocker.txt`；修复=刷新/清理本地 .m2 的 stale nop-ai-translate 构件（用户本地环境动作，超出本批授权），Phase 5 全量构建通道（`clean install`）不受 -am 组合影响需复核确认。
- 发现的设施缺陷：无（绝缘层本体未改动；站点接入中未发现设施面缺口）。

### Phase 4 - 下游单元批次 B：nop-ai / nop-message / nop-cluster / nop-metadata

Status: completed（2026-10-24 接续收尾子代理交付：前任实现子代理配额中断后现场甄别接续——四工单全落地、四单元独立提交、必红两项变异红+恢复绿留痕、四模块门禁全绿 EXIT=0；接续中补齐 2 处前任缺口[C7-029 sweep 面回归、C3-009 ⑥ 回调守卫回归]）
Targets: `nop-ai`（MA-07 六站点）、`nop-message`（C3-009 七屏障）、`nop-cluster`（C4-004+CL-011/014）、`nop-metadata`（W-11）

- Item Types: `Fix`（MA-07/C3-009/C4-004/W-11 已确认 live defect）、`Proof`（各单元回归测试）

- [x] nop-ai：MA-07 六站点逐点接入平台绝缘语义（ScheduledRecoveryManager / SessionLockRenewal / TeamTaskSchedulerDaemon / DBMessageService×2 面 / EmbeddingServiceImpl 调度与 retry future 面）；**SessionLockRenewal 站点必须有具名回归测试**（会话双执行安全语义面）；EmbeddingServiceImpl 的 scheduleRetry 丢弃 ScheduledFuture 面随单闭合（future 有终态/有观测）——**落地**：六成员逐一 catch 收宽 Throwable（C3-020/C4-007/C4-013 站点守卫；C3-027+C7-029 DBMessageService 双面；C4-003[3.1] dispatch 内外层+scheduleRetry 任务体守卫，重试腿死亡 ERROR 观测、拒绝沿 dispatch 收进调用方 future 终态）；具名回归 testLockRenewalChainSurvivesInjectedErrorAndStaysObservable（Error 注入→链续期不断+失败可观测+不误触发 lease-lost 中止）
- [x] nop-message：七屏障清单随单闭合（①–⑦ 全部，含 ⑥ 回调守卫与 ⑦ bridge/listener 守卫——不允许只修前五个）；Error 分支走失败上报路径（dispatchConsumeFailure / notifyEngineFailure），isActive 不再误报；回归测试注入确定性 Error 形态（StackOverflowError/OOM 或自定义 Error 子类如 LinkageError 派生——确定性 Error 逃逸语义即可）断言订阅非僵尸 + Debezium FAILURE_LISTENERS 收到通知（C10-002/005 验收前置随本体）——**落地**：七屏障全闭合（Kafka/Pulsar 循环屏障 Error 分支=同路失败上报+active=false 诚实退出，Error 以 IllegalStateException 包装上报、nop-api-core 契约零触碰；Debezium worker 体抽取 runEngineWorker+Error 死亡分支 notifyEngineFailure；③⑤⑥⑦ 守卫全 Throwable 宽度）；回归 9 条（LinkageError 注入→订阅非僵尸+FAILURE_LISTENERS 通知+⑥ 专项 2 条）
- [x] nop-cluster：C4-004 两条自续链 pending future 集中观察 + whenComplete 告警（`nop.cluster.leader-elector.loop-broken` 类业务标识=CL-014）+ 退避重臂（CL-011 基类守护）；回归测试=注入 rejection/Error 断言链重臂 + 带业务标识日志/上报可见；不做 C4-002 stop 复活面（Non-Goals）——**落地**：#14 显式接入=AbstractPollingLeaderElector pending future 保留+whenComplete 告警+CL-011 finally 退避重臂（1s×2 封顶 60s，isStopping 不重臂，子类尾部续排契约不变[per-round 标记防双链]）；#10=AutoRegistration 站点 Throwable 守卫+chain future 拒绝终态业务标识告警（设施已重臂，不二次重排防链分叉）；C4-002 零触碰
- [x] nop-metadata：W-11 catch(Throwable) 分支处理（Error 记 FATAL 并仍返回错误结果 DTO 保 job 存活）；同文件族 MetaQualityCheckpointExecutor.execute per-rule catch 同根因随单闭合；回归测试注入 Error 断言 job 不被置 FAILED 且下一轮 cron 仍触发——**落地**：executeScheduledCheckpoint catch Throwable 三分支（并发拒绝 WARN/Exception ERROR/Error 记 scheduled-exec-fatal 并返回错误 DTO）；per-rule 隔离 Throwable 宽度；回归 2 条（连续两轮 cron tick 正常返回+FATAL 日志断言+WARN 分支回归保护/per-rule Error 隔离其余规则继续评定）
- [x] 每单元修复 diff 与审计清单行号回读核对登记——**落地**：全部站点 live 回读（六站点/七屏障/两链/W-11 行号与成员表定位一致或以 live 为准登记：DBMessageService 守卫 live :325/:584、SessionLockRenewal :74、Kafka 循环屏障 live :304、EmbeddingServiceImpl :273/:283/:463 与审计行 257-281/344-354/443-445 对应[344-354 的 onResponseError 解析路径经 :273 守卫路由]），无未登记漂移
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] MA-07 六站点逐点 landed 且有清单核对记录（六成员 ID 对应站点逐一勾销）；SessionLockRenewal 具名回归测试在位——六成员勾销：C3-020（ScheduledRecoveryManager:393+TestScheduledRecoveryManagerInsulation）/C3-027（DBMessageService:325 poll+TestDBMessageServiceInsulation）/C4-003[3.1]（EmbeddingServiceImpl:273/:283/:463+TestEmbeddingServiceImplErrorInsulation 3 条）/C4-007（SessionLockRenewal:74+具名回归）/C4-013（TeamTaskSchedulerDaemon:274+TestTeamTaskSchedulerDaemonScanInsulation）/C7-029（DBMessageService:584 sweep+同文件第二条回归——接续补齐）；C10-008 护栏义务保留 R2-16 未越界
- [x] C3-009 七屏障全部闭合（⑦ 清单逐项核对），回归测试覆盖 Error 注入→失败上报路径——①②③④⑤⑥⑦ 逐一闭合（⑥ Kafka/Pulsar 各两站点守卫+专项回归 2 条、⑦ bridge.submit+notifyEngineFailure listener 双守卫+回归 2 条）；9 条回归覆盖 LinkageError 注入→onConsumeFailure/notifyEngineFailure 上报+订阅非僵尸
- [x] C4-004 两条链回归测试在位（重臂 + 带业务标识观测断言）；CL-011/CL-014 随单闭合登记——TestPollingElectorLoopInsulation 3 条（Error 重臂+loop-broken 业务标识/执行腿拒绝告警重臂/单链不分叉）+TestAutoRegistrationInsulation 2 条（站点 Throwable 包含/链终态告警）；CL-011=基类 Throwable 守卫+finally 退避重臂、CL-014=`nop.err.cluster.leader-elector.loop-broken`+`refresh-registration-chain-broken` 业务标识上报点
- [x] W-11 回归测试在位（Error 注入后 job 存活断言）——TestMetaQualityCheckpointSchedulerErrorKeepAlive.testErrorInjectionKeepsJobAliveAndObservable（连续两轮 cron tick 正常返回错误 DTO=job 未被置 FAILED+FATAL 日志断言）
- [x] `./mvnw test -pl nop-ai -am`、`./mvnw test -pl nop-message -am`、`./mvnw test -pl nop-cluster -am`、`./mvnw test -pl nop-metadata -am` 全部 BUILD SUCCESS——四者均 BUILD SUCCESS EXIT=0（`_tmp/phase4-nop-ai-test.log` / `_tmp/phase4-nop-message-test.log` / `_tmp/phase4-nop-cluster-test.log` / `_tmp/phase4-nop-metadata-test.log`；nop-metadata -am 闭包含 nop-ai/nop-message/nop-cluster 全部新回归亦全绿）
- [x] Owner doc 裁定：`docs-for-ai/03-modules/nop-message.md`（C3-009 失败上报语义）与 `docs-for-ai/03-modules/nop-metadata.md`（W-11 扩展其 checkpoint 调度契约 Error 分支半边）——两 doc 实际存在、非缺口，按行为契约变化随批最小更新，Phase 5 汇总核对；nop-cluster 无 03-modules owner doc → 缺口声明登记（Phase 5 统一）——**落地**：nop-message.md 新增「消费循环异常屏障契约（Throwable 宽度）」节、nop-metadata.md 新增「cron 调度入口失败语义（job 存活契约全半边）」节（均已随单元提交）；nop-cluster 缺口声明在本行登记，Phase 5 统一落账
- [x] `ai-dev/logs/` 对应日期条目已更新

#### Phase 4 执行记录（2026-10-24 接续收尾子代理）

- **配额中断接续说明**：前任实现子代理已落盘四单元修复+13 个测试文件+两份 owner doc 修改但未验证未提交；本子代理接续执行「甄别→逐单元审计→补缺→验证→必红→提交→回填」。甄别结论：nop-format `samples/generated-pie-chart/_rels/.rels` 为**构建副作用非人工误触**（TestPieChartParser 每次构建重新生成样例目录，CRLF 换行差异、内容零变化），已还原并登记；其余全部改动与 Phase 4 工单对应，无越界文件。
- **逐单元完整性审计结论**：修复面 4/4 工单完整（六站点/七屏障/两链/W-11 全 landed 且与设计文档 3.1 管线对齐：catch Throwable+观测+重臂/诚实退出+拒绝终态可见）；测试面 2 处缺口就地补齐——①C7-029 sweep 面无回归（补 TestDBMessageServiceInsulation.sweepFaceSurvivesInjectedErrorAndKeepsSweeping：DataSource stub 注入 LinkageError+poll 间隔隔离，断言 sweep 连续 ≥2 轮存活可观测）；②C3-009 ⑥ 回调守卫无专项回归（补 Kafka/Pulsar 各 1 条：onConsumeFailure 自身抛 Error 被守围观+循环仍诚实退出）。补缺后 nop-message/nop-metadata 提交含新测试。
- 提交全集（每单元一提交）：`27c02f85a5` nop-ai（MA-07 六站点+5 测试文件 8 条）、`1925eb2dd5` nop-message（C3-009 七屏障+5 测试文件 9 条+nop-message.md）、`8c1b1505aa` nop-cluster（C4-004+CL-011/014+2 测试文件 5 条）、`bd2ea3cec4` nop-metadata（W-11+1 测试文件 2 条+nop-metadata.md）。pre-commit nop-cli lint gate 三次全过（0 violations）。
- 验证输出摘要：`./mvnw test -pl nop-ai -am` → BUILD SUCCESS EXIT=0（新回归 TestEmbeddingServiceImplErrorInsulation 3/TestDBMessageServiceInsulation 2/TestScheduledRecoveryManagerInsulation 1/TestTeamTaskSchedulerDaemonScanInsulation 1/TestSessionLockRenewalInsulation 1）；`./mvnw test -pl nop-message -am` → BUILD SUCCESS EXIT=0（新回归 9 条：Kafka 2/Pulsar 2/Debezium 3/core 2）；`./mvnw test -pl nop-cluster -am` → BUILD SUCCESS EXIT=0（TestPollingElectorLoopInsulation 3/TestAutoRegistrationInsulation 2）；`./mvnw test -pl nop-metadata -am` → BUILD SUCCESS EXIT=0（TestMetaQualityCheckpointSchedulerErrorKeepAlive 2；-am 闭包含 nop-ai/nop-message/nop-cluster 新回归全绿复证）；四份快照 `_tmp/phase4-{nop-ai,nop-message,nop-cluster,nop-metadata}-test.log`。
- 必红证据（E2）快照全集：`_tmp/phase4-nopai-red.log`（MA-07 SessionLockRenewal 站点 catch 收窄变异红：`renewal chain must survive the injected Errors and re-arm: attempts=1`——审计「一次 Error 链死、租约静默过期」结论复现）→ `_tmp/phase4-nopai-green.log`（恢复绿）；`_tmp/phase4-nopmessage-red.log`（C3-009 ① Kafka 循环屏障 catch 收窄变异红：`an Error-killed consume loop must deactivate the subscription (no zombie) expected: <false> but was: <true>`—— isActive 僵尸误报结论复现，⑥ 专项条同红）→ `_tmp/phase4-nopmessage-green.log`（恢复绿，2 条全绿）。
- 关键裁定注：①Kafka/Pulsar Error 分支语义=「同路失败上报后 active=false 诚实退出」（对齐 plan「Error 分支走失败上报路径+isActive 不再误报」，与 Exception 分支的退避续跑区分——Error 后 JVM 态不可退避恢复）；②nop-cluster #10 AutoRegistration 不做模块级重臂（平台绝缘设施已重臂任务体失败，二次重排有链分叉风险），只做拒绝终态业务标识告警——与设计文档 §3.1「重排归调用方模块级监督」一致且更保守；③nop-metadata `isConcurrentRunRejection`/`buildErrorResult` 参数宽度随捕获宽度放宽，既有调用方 source-compat。
- 基线既有问题登记（非本 Phase 引入）：`nop-ooxml-xlsx` TestPieChartParser 每次构建重新生成 `samples/generated-pie-chart/` 样例（.rels CRLF 差异入 git status），与本批无关，已还原并在提交前复核零携带。

### Phase 5 - 全量验证 + owner doc 更新

Status: planned
Targets: 受影响聚合模块全量测试；`docs-for-ai/03-modules/nop-job.md`、`docs-for-ai/03-modules/nop-batch.md`、`docs-for-ai/03-modules/nop-metadata.md`、`docs-for-ai/03-modules/nop-message.md`、`docs-for-ai/03-modules/nop-retry.md`、`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] 全量构建主通道（E1）：`./mvnw clean install -T 1C` BUILD SUCCESS（全仓）
- [ ] 全量 focused 套件（实际触达模块，Baseline 勘误 1 清单，作为聚焦验证通道保留）：`./mvnw test -pl nop-kernel -am`；`./mvnw test -pl nop-core-framework -am`；`./mvnw test -pl nop-job -am`；`./mvnw test -pl nop-batch -am`；`./mvnw test -pl nop-retry -am`；`./mvnw test -pl nop-runner -am`；`./mvnw test -pl nop-ai -am`；`./mvnw test -pl nop-message -am`；`./mvnw test -pl nop-cluster -am`；`./mvnw test -pl nop-metadata -am`；`./mvnw test -pl nop-service-framework -am`；`./mvnw test -pl nop-network -am`——全部 BUILD SUCCESS 且结果登记
- [ ] 零触碰确认：本批产品 diff 不含 nop-tcc、nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth 路径（`git diff --name-only` 核对登记；与 Non-Goals 零触碰声明一致）
- [ ] Owner doc 更新：`nop-job.md`（scanner/调度契约与绝缘语义）、`nop-batch.md`（executeChunkLoop 终态化契约）、`nop-metadata.md`（W-11 扩展其 checkpoint 调度契约 Error 分支半边）、`nop-message.md`（C3-009 失败上报语义）、`nop-retry.md`（C8-001 scanner 契约）、`source-anchors.md`（**新建** 2.1-C3-001 30 点绝缘状态表锚点条目——处置后状态 + 关键站点索引）
- [ ] 缺口声明：仅 nop-cluster、nop-runner 两个真正无 03-modules 条目的模块（本批其余触达模块均有 owner doc，不列入缺口）——在 plan 执行记录与 daily log 登记缺口，交 R-G owner-doc drift 治理轨
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module <受影响模块> --severity high`（受影响模块全集：nop-kernel、nop-core-framework、nop-job、nop-batch、nop-retry、nop-runner、nop-ai、nop-message、nop-cluster、nop-metadata、nop-service-framework、nop-network——与 Closure Gates「受影响模块全部」一致）全部退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] 全量构建 `./mvnw clean install -T 1C` BUILD SUCCESS（E1 主通道）；12 个模块 focused 测试命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码）
- [ ] 六份 owner doc 更新落地（nop-job/nop-batch/nop-metadata/nop-message/nop-retry/source-anchors）；check-doc-links --strict 退出码 0
- [ ] scan-hollow 受影响模块 high=0 逐条登记
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.2 行、cra-index 台账

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（绝缘层被消费方运行时调用的调用链追踪；无空 catch/静默跳过作为正常实现——本批 catch Throwable+观测+重臂路径不得退化为吞异常）
- [ ] cra-index 台账回填：P1 台账 5 行（2.1-C3-001、1.6-C8-001、1.7-C6-001、2.2-C1-021、4.5-C4-001）与 P2 裁决台账 7 组成员行状态收敛（open → 已修/本批）
- [ ] roadmap 8.2 行 Status → done
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2496-cra-phase8.2-r1-01-insulation.md --strict` 退出码 0；scan-hollow 全绿复核
- [ ] Closure Evidence 写入本 plan `## Closure` 段（rule 27）
- [ ] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 14 成员行状态全部收敛、roadmap 8.2 = done
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> 只有本节全部条目与每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 Plan Status 改为 `completed`。

- [ ] 14 项成员全部修复：2.1-C3-001（设施主体）、2.1-C3-015（同变更集）、1.6-C8-001、1.7-C6-001、1.7-C6-010（与 C6-001 同 diff）、4.5-C4-001、2.2-C1-021、1.4-MA-05、1.4-MA-06、1.4-C4-003、3.1-MA-07（六站点）、4.1-C3-009（七屏障）、4.2-C4-004（+CL-011/014）、5.5-W-11
- [ ] 每个下游单元修复均有对应回归测试（rule 25），关键项有先红后绿或变异/回退论证（E2）
- [ ] 绝缘层本体有单元测试 + ≥1 跨模块接线验证（rule 23）；30 点表接线验证（Phase 2）
- [ ] 30 点绝缘状态表 30 行处置登记完整，锚点落 `docs-for-ai/04-reference/source-anchors.md`
- [ ] 全量构建绿：`./mvnw clean install -T 1C` BUILD SUCCESS + Phase 5 清单 12 模块 focused 测试全部 BUILD SUCCESS（E1/E3）
- [ ] Owner doc 更新完成：nop-job.md / nop-batch.md / nop-metadata.md / nop-message.md / nop-retry.md / source-anchors.md；缺口声明仅限 nop-cluster/nop-runner 两模块（E5）
- [ ] MA-06 钉死面（plan340 契约测试 + JobTimeoutCheckerImpl:497-501 注释）零改动；C10-008 护栏义务确认保留 3.1 侧未越界承接
- [ ] 受影响模块 `scan-hollow-implementations.mjs --severity high` 全部退出码 0
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（raw 载体/C10-008/C3-017 等均为入批前已裁定的批外项，归属清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）绝缘层→消费方调用链运行时连通（不只 import 存在），（b）无空方法体/静默吞异常/no-op 作为正常实现（catch Throwable 路径必须有观测+重臂行为）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2496-cra-phase8.2-r1-01-insulation.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 14 成员行收敛 + roadmap 8.2 → done（E6）

## Deferred But Adjudicated

### raw 载体周期站点（30 点表 #15–22 nop-stream 8 处、#28 FeishuClient）

- Classification: `out-of-scope improvement`（#15–22）/ `watch-only residual`（#28）
- Why Not Blocking Closure: 平台设施修复**不覆盖自建调度器**（C3-round2 §1 表判定列原文）；#15–22 载体碎片化复核义务已归并 CL-021（「随 C3-001 平台契约批复核 raw 载体」，R-G 条款登记册承接）；#28 FeishuClient 心跳站点归 4.3 单元批次裁决（R2-19/R2-20），非 R1-01 成员。
- Successor Required: `yes`（#15–22 → CL-021/R-G 条款；#28 → R2-19/R2-20 裁决）
- Successor Path: `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §五 R-G；roadmap 8.28/8.29

### 3.1 Error 注入护栏义务（C10-008，四处站点共用）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: batch-proposal §四.2 明文「3.1-MA-07 移交后保留 Error 注入护栏义务（C10-008）→ R1-01 exit criteria」= 义务**声明**在本批 exit criteria（本行即声明），实施**保留 3.1 侧**（R2-16），避免跨批重复建设护栏。
- Successor Required: `yes`
- Successor Path: roadmap 8.16（R2-16，3.1 批）

### 2.1-C3-017（scheduleWithRandomDelay 杀链+无日志+取消失效；30 点表 #30）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 未入 R1-01 的 14 项成员清单；独立 P2 工单，批次归属以 cra-index 裁定批次列为准。
- Successor Required: `yes`
- Successor Path: cra-index `2.1-C3-017` 行「裁定批次」列（执行时回读确认）

### C3-003（BindScheduledExecutor.scheduleAtFixedRate executor 拒绝杀链）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 未入 R1-01 成员清单（P2，2.1 单元相邻面）。设施改造若顺带改变该路径行为，须在 Phase 1 设计文档显式声明边界，不默认承接。
- Successor Required: `yes`
- Successor Path: cra-index `2.1-C3-003` 行「裁定批次」列

## Non-Blocking Follow-ups

- cluster 两条自续链长期迁移 `CycleSynchronizer`/带监督调度设施（C4-004 建议的长期方向；本批以 whenComplete 观察+重臂短修收口）。
- 4.2-C4-002（stop 复活调度链面）与 C1-004 同 diff 修复——R2-18 批次承接；本批 cluster 改动若触碰同文件，实施时与 R2-18 做同 PR 协同注记。
- nop-sys 双生产注释自证站点（SysDaoNamingService/SysDaoMessageService）在设施级覆盖后可复核注释表述是否需要更新（非义务，文档卫生）。

## Closure

Status Note: （完成或关闭时填写）
Completed: （YYYY-MM-DD）

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent 标识）
- Audit Session: （session ID）
- Evidence: （每条 Exit Criterion / Closure Gate 的 PASS/FAIL + 证据来源；check-plan-checklist --strict 退出码；Anti-Hollow 调用链追踪结果；scan-hollow 退出码；Deferred 分类检查结论）

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
