# 30 点绝缘状态表逐行三态处置登记（plan 2496 R1-01 Phase 2）

> Status: active（30/30 行 + F3 补登行处置登记完成；`##四` 为 source-anchors 锚点内容的定稿形态，落账 `docs-for-ai/04-reference/source-anchors.md` 归 plan 2496 Phase 5）
> Date: 2026-10-24
> Source: 原点表 = `C3-round2.md` §1 周期任务调用点绝缘状态表（30 行全表，`ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/C3-round2.md`）+ 同文件 C3-015 条目 F3 补登（`RpcPollTaskManager.java:119` = 第 29 处生产站点）；覆盖归属权威 = `ai-dev/design/concurrency/periodic-task-insulation.md` §3.3 修订版矩阵（12 设施级 / 16 显式接入 / 2 豁免）；处置义务与承接去向 = `ai-dev/plans/2496-cra-phase8.2-r1-01-insulation.md` Phase 2 checklist
> Method: 每行独立回读 C3-round2 §1 原表后登记（行号/站点名/载体/缺陷描述以原表为准，禁止从设计矩阵转抄）；全部 22 个 in-repo 站点文件已按 plan Baseline 勘误 5 以 live code 回读复核（结果见 `##五`）

## 一、三态判据（plan 2496 Phase 2 原文）

- **接入**：该站点在绝缘设施覆盖范围外，需显式包装。
- **设施级覆盖**：绝缘层本体改造后自动覆盖，登记即可。
- **豁免**：有其他防护或非 Error 逃逸面，写明理由。

覆盖归属列采用设计文档 §3.3 的三分类（设施级覆盖 12 行 / 显式接入 16 行 / 豁免·批外 2 行，Phase 1 评审勘误后的权威口径）；三态处置列按 plan 判据登记。注意：设计矩阵「显式接入」中 #15–22/#28 的处置经 plan `Deferred But Adjudicated` 裁定为**批外**（承接 successor 登记即为 plan checklist 要求的「豁免登记」，理由与 successor path 见行内）。

## 二、输入不一致勘误注（承接去向映射）

处置指令中的承接映射与 plan Phase 2 checklist / 设计矩阵存在 3 处不一致，按「plan 为准」裁定并登记：

1. **#15–22（nop-stream 8 处）**：指令写「归 Phase 4 MA-07/C3-009 对应」——不成立。原表 #15–22 站点全部在 nop-stream（raw 自建调度器），与 nop-ai MA-07（#23–27）和 nop-message C3-009（非点表行）无对应关系。按 plan checklist 登记：#15–22 = 批外（CL-021 承接）。
2. **#10（AutoRegistration）**：指令写「#10–13 归 Phase 3 各工单」——#10 的模块侧义务工单 4.2-C4-004 在 **Phase 4**（plan Phase 2 checklist 与 Baseline 表成员 13 均为 Phase 4），#13（SysDaoMessageService）无模块侧义务工单（设施级覆盖 + 注释复核 follow-up）。仅 #11/#12 归 Phase 3。
3. **#28（FeishuClient）**：指令写「#23–28 同」——#28 不归 MA-07（MA-07 六站点全在 nop-ai）。按 plan `Deferred But Adjudicated` 登记：#28 = 批外，归 R2-19/R2-20（4.3 单元批次裁决）。

## 三、逐行三态处置登记（30/30 行 + F3 补登行）

处置状态图例：`本批已覆盖` = Phase 1 绝缘层本体改造后该站点自动获得链存活兜底；`本 Phase 已修复` = plan 2496 Phase 2 站点侧修复 + 回归测试；`Phase N 承接` = 模块侧义务归本 plan 后续 Phase 工单；`批外` = plan `Deferred But Adjudicated` 裁定 + successor。

| # | 调用点（原表 · live 复核） | 载体（原表判定列，不改写） | 覆盖归属（design §3.3） | 三态处置 | 处置状态 / 承接去向 |
|---|---|---|---|---|---|
| 1 | `CliRunCommand.java:90`（live :90 一致） | globalTimer（裸 JDK，任务体全裸） | 设施级覆盖 | 设施级覆盖 | 本批已覆盖（Phase 1 绝缘核心）；模块侧义务（watch 周期链失败可见化）= 成员 4.5-C4-001 → **Phase 3** |
| 2 | `JsonRpcWebSocketHandler.java:82`（live :82 一致；任务体 `sendKeepAlive` 修复前 :357-363 仅 catch IOException） | globalTimer（裸 JDK） | 设施级覆盖 | 设施级覆盖 + 站点 catch 宽度接入义务 | 设施覆盖=本批已覆盖；站点侧 **本 Phase 已修复**：catch(Throwable) 兜底 + `nop.err.websocket.keep-alive-fail` 结构化 ERROR（失败可见、不吞语义、不向外传播）；回归 `TestJsonRpcKeepAliveInsulation`（先红后绿在案）；finding 2.1-C3-015 |
| 3 | `SysDaoNamingService.java:77`（live :77 一致） | globalTimer（catch Exception 自备，生产注释自证） | 设施级覆盖 | 设施级覆盖 | 本批已覆盖（站点 catch(Exception) 与设施 Error 兜底正交）；注释表述复核 = Non-Blocking Follow-up |
| 4 | `AbstractFileConfigSource.java:45`（live :45 一致，:65 已设防注释在证） | globalTimer（catch Exception；回调在 timer 线程同步执行） | 设施级覆盖 | 设施级覆盖 | 本批已覆盖（已设防正面样本）；F3 回调拓扑决策随 Phase 1 设计裁定④闭合（设施边界=任务体整体；per-callback 隔离归 C1-021，design §3.4） |
| 5 | `JdbcConfigSource.java:54`（live :54 一致） | globalTimer（catch Exception 双重 + errorCount 抑制） | 设施级覆盖 | 设施级覆盖 + 站点侧 per-callback 隔离义务 | 设施覆盖=本批已覆盖；`tryTriggerChange` 独立守卫/per-task 隔离 = 成员 2.2-C1-021 → **Phase 3** |
| 6 | `LocalResourceLockManager.java:105`（live :105 一致） | globalTimer（无 catch；任务体近无抛点） | 设施级覆盖 | 设施级覆盖 | 本批已覆盖（设施兜底闭合残余 Error 面：租约超时清理链存活 + 失败可观测） |
| 7 | `RpcMessageHandler.java:73`（live :73 一致；`checkTimeout` 修复前 :76-99 无 catch） | **Netty EventExecutor.scheduleWithFixedDelay（非平台设施）** | 显式接入（设施不覆盖） | **接入** | **本 Phase 已修复**：调度入口包 `sweepExpiredFutures` 守卫（catch Throwable + `nop.err.netty.rpc-timeout-sweep-fail` 结构化 ERROR；Error 不逃逸 Netty 管线；清扫失败轮次跳过、链存活、过期 future 终态义务不变）；回归 `TestRpcMessageHandlerSweepInsulation`（先红后绿在案）；finding 2.1-C3-015 |
| 8 | `SocketClient.java:133`（live :133 一致） | IScheduledExecutor 注入（生产 globalTimer；catch Exception 自备） | 设施级覆盖 | 设施级覆盖 | 本批已覆盖（心跳链链存活 + 失败可观测） |
| 9 | `RpcTaskMonitor.java:74`（live :74 一致，:105 注释自证在证） | timer 注入 IScheduledExecutor（逐任务 catch Exception） | 设施级覆盖 | 设施级覆盖 | 本批已覆盖 |
| 10 | `AutoRegistration.java:206`（live :206 一致） | globalTimer.executeOn(globalWorker) = Bind | 设施级覆盖 | 设施级覆盖 + 站点侧告警挂接义务 | 设施覆盖=本批已覆盖；断链监督（whenComplete 告警 + 退避重臂，CL-011/CL-014 随单）= 成员 4.2-C4-004 → **Phase 4** |
| 11 | `RetryScannerImpl.java:96`（live :96 一致） | Bind 载体（doScan 外层 catch Exception + 逐批 catch Exception） | 设施级覆盖 | 设施级覆盖 + 站点侧义务 | 设施覆盖=本批已覆盖；接入对齐 + scanner 存活/最后扫描时间指标 = 成员 1.6-C8-001 → **Phase 3** |
| 12 | `AbstractBatchScanner.java:63`（live :63 一致；try-外段 cursor.reset/onCycleStart 原表 86-91 → 复核 84-87 → live :89-92，漂移按勘误 5 以 live 为准） | Bind 载体（部分裸：try-外段抛 RuntimeException 即杀链） | 设施级覆盖 | 设施级覆盖 + try-外段接入义务 | 设施覆盖=本批已覆盖；scanOnce catch 放宽 + future 观测（running 状态真实化）+ try-外段纳入守卫（C3-015 (a) 类）= 成员 1.4-C4-003 → **Phase 3** |
| 13 | `SysDaoMessageService.java:217/221/228`（live 三行一致） | timer.executeOn(globalWorker) = Bind（三入口 catch Exception 自备） | 设施级覆盖 | 设施级覆盖 | 本批已覆盖；注释表述复核 = Non-Blocking Follow-up |
| 14 | `AbstractPollingLeaderElector.java:22/:28`（live 一致；自续链 doStart/scheduleCheck） | Bind.schedule 一次性自续链 | 显式接入（绝缘层只改写 fixedRate/fixedDelay，对一次性自续链零自动覆盖） | **接入** | 成员 4.2-C4-004（重排在 SysDaoLeaderElector.checkElection 尾部 try 之外且仅 catch Exception）→ **Phase 4** |
| 15-22 | nop-stream 8 处 raw（原表站点：CachingTableLookup:205、TaskManager:343、JobCoordinator:693/1309、PollingJdbcMessageService:293、OpsJobManager:342、TaskCheckpointWiring:275、StreamMetricsReporter:80） | **raw Executors 自建调度器（绕过平台设施）** | 显式接入（设施不覆盖自建调度器） | 接入类 × **批外裁决（豁免登记）** | 批外：本批不做 raw 载体迁移（plan Non-Goals / Deferred §raw 载体，Classification=out-of-scope improvement）；承接 = **CL-021** 载体碎片化复核（R-G 条款登记册；roadmap 8.28/8.29）。live 回读漂移登记见 `##五` |
| 23 | `SessionLockRenewal.java:62`（live :62 一致） | **raw 单线程 scheduler**（仅 catch RuntimeException；:69-77） | 显式接入 | **接入** | 成员 3.1-MA-07 → **Phase 4**（会话双执行安全语义最重，具名回归测试义务；C10-008 Error 注入护栏保留 3.1 侧 R2-16） |
| 24 | `ScheduledRecoveryManager.java:361`（live :361 一致） | scheduledExecutor 注入（scanOnceSafe 仅 catch RuntimeException） | 显式接入 | **接入** | 成员 3.1-MA-07 → **Phase 4** |
| 25 | `TeamTaskSchedulerDaemon.java:237`（live :237 一致） | scheduledExecutor 注入（仅 catch RuntimeException） | 显式接入 | **接入** | 成员 3.1-MA-07 → **Phase 4** |
| 26 | `DBMessageService.java:213`（live :213 一致） | **raw 单线程 poller**（pollAllTopics 逐 topic catch Exception） | 显式接入 | **接入** | 成员 3.1-MA-07 → **Phase 4** |
| 27 | `DBMessageService.java:217`（live :217 一致） | **raw 单线程 poller**（sweep catch Exception） | 显式接入 | **接入** | 成员 3.1-MA-07 → **Phase 4**（MA-07 第 6 站 EmbeddingServiceImpl 同单承接，非点表行） |
| 28 | `FeishuClient.java:262`（nop-integration-feishu，live :262 一致） | **raw 单线程 scheduler**（sendHeartbeat catch Exception） | 显式接入 | 接入类 × **批外裁决（豁免登记）** | 批外：归 4.3 单元批次裁决 R2-19/R2-20，非 R1-01 成员（plan Deferred，Classification=watch-only residual） |
| 29 | `MockScheduledExecutor`（nop-autotest-core，live 在证） | DelayQueue 自实现（mock 实现） | 豁免 | **豁免** | 豁免理由 = 原表判定列原文「测试设施，不列生产失效面」（非生产失效面）；零处置 |
| 30 | `IScheduledExecutor.scheduleWithRandomDelay` default → `ExecutorHelper.scheduleWithRandomDelay`（live :210 在证） | 调用方传入 IScheduledExecutor | 豁免/批外 | **豁免（批外工单）** | 批外理由 = C3-017（杀链+无日志+执行中取消失效）为独立 P2 工单、未入本批 14 成员（生产 0 调用）；承接 = cra-index `2.1-C3-017` 行裁定批次列 |
| +F3 | `RpcPollTaskManager.java:119`（nop-job-coordinator，live :119 一致，:118 防御性注释在证）——C3-015 F3 补登第 29 处生产站点 | 平台 IScheduledExecutor 载体（pollOnce catch Exception + 防御性 isDone 检查） | 设施级覆盖 | 设施级覆盖 + 站点侧义务 | 设施覆盖=本批已覆盖；pollOnce catch 宽度 = 成员 1.4-MA-05 → **Phase 3** |

**计数核对**：设施级覆盖 12 行（#1–6、#8–13）+ 显式接入 16 行（#7、#14、#15–22 八行、#23–27 五行、#28）+ 豁免 2 行（#29、#30）= 30 行 ✓；另 F3 补登 1 行（非点表 30 行内，平台生产站点总数 29 处的成员）。无未处置行。

## 四、30 点状态表「处置后状态」锚点内容定稿（Phase 5 落 source-anchors.md 的正文形态）

以下为 `docs-for-ai/04-reference/source-anchors.md` 新建条目「2.1-C3-001 30 点绝缘状态表锚点」的最终内容形态（**只增处置列，不改 C3-round2 §1 判定列**；全文登记以本文件 `##三` 为准）。

### 锚点条目正文

> **2.1-C3-001 周期任务绝缘状态表（处置后状态）** — 权威原表：`ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/C3-round2.md` §1（载体/绝缘状态判定列以此为准，未被改写）；逐行处置登记：`ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/insulation-30pt-disposition.md`；绝缘契约 owner doc：`ai-dev/design/concurrency/periodic-task-insulation.md`。
>
> 设施级覆盖语义：`IScheduledExecutor` 周期方法已全部路由绝缘核心（任务体 Throwable 捕获 + 观测 + 重臂 + future 失败终态，`nop-kernel/nop-commons/.../executor/` 的 `InsulatedPeriodicTask`/`InsulatedPeriodicChain`/`PeriodicTaskHelper`）；设施覆盖只闭合「链存活 + 失败可观测」两面，各站点模块侧义务仍归对应工单。

| 行 | 处置后状态（R1-01 批后） |
|---|---|
| #1 | 设施级覆盖（R1-01 Phase 1）；模块义务 4.5-C4-001 → R1-01 Phase 3 |
| #2 | 设施级覆盖（R1-01 Phase 1）+ 站点 catch 宽度已修（R1-01 Phase 2：catch Throwable 兜底 + `nop.err.websocket.keep-alive-fail`） |
| #3 / #8 / #9 / #13 | 设施级覆盖（R1-01 Phase 1）；站点自备 catch(Exception) 与设施 Error 兜底正交 |
| #4 | 设施级覆盖（R1-01 Phase 1）；F3 回调拓扑裁定 = 设施边界任务体整体（design §3.4），per-callback 隔离归 C1-021 |
| #5 | 设施级覆盖（R1-01 Phase 1）；模块义务 2.2-C1-021 → R1-01 Phase 3 |
| #6 | 设施级覆盖（R1-01 Phase 1） |
| #7 | 站点侧接入已修（R1-01 Phase 2：`sweepExpiredFutures` 守卫 + `nop.err.netty.rpc-timeout-sweep-fail`；Netty EventExecutor 载体在平台设施覆盖外） |
| #10 | 设施级覆盖（R1-01 Phase 1）；模块义务 4.2-C4-004（断链监督，CL-011/014 随单）→ R1-01 Phase 4 |
| #11 | 设施级覆盖（R1-01 Phase 1）；模块义务 1.6-C8-001 → R1-01 Phase 3 |
| #12 | 设施级覆盖（R1-01 Phase 1）；模块义务 1.4-C4-003（scanOnce catch 宽度 + future 观测 + try-外段守卫）→ R1-01 Phase 3 |
| #14 | 显式接入：4.2-C4-004 → R1-01 Phase 4（一次性自续链，设施零自动覆盖） |
| #15–22 | 显式接入 × 批外：raw 载体平台设施不覆盖 → CL-021 载体碎片化复核（R-G 条款登记册；live 行号漂移已登记，站点全集刷新归 CL-021） |
| #23–27 | 显式接入：3.1-MA-07 六站点 → R1-01 Phase 4（含非点表第 6 站 EmbeddingServiceImpl；C10-008 护栏保留 3.1 侧 R2-16） |
| #28 | 显式接入 × 批外：R2-19/R2-20（4.3 单元批次裁决） |
| #29 | 豁免：测试设施（MockScheduledExecutor），非生产失效面 |
| #30 | 豁免/批外：C3-017 独立工单（批次归属以 cra-index `2.1-C3-017` 裁定批次列为准） |
| +F3 | RpcPollTaskManager.java:119 设施级覆盖（R1-01 Phase 1）；模块义务 1.4-MA-05 → R1-01 Phase 3 |

## 五、行号漂移与 live 回读记录（plan Baseline 勘误 5 执行记录）

本 Phase 对全部 in-repo 行做了 live 回读（grep 调用点行号核对）。结论：**#1–14、#23–27、#28、#29、#30、+F3 的原表行号与 live 全部一致，零漂移**。两处登记：

1. **#12 try-外段**：`AbstractBatchScanner` 的 `cursor.reset()/onCycleStart()` 段原表记 86-91 → 复核 84-87 → live :89-92（三次漂移，方向一致：新增 javadoc 挤后行号）。按勘误 5 以 live 为准登记 :89-92；该段守卫义务随成员 1.4-C4-003 在 Phase 3 执行时最终回读。
2. **#15–22（nop-stream）**：原表 8 站点行号已普遍漂移（live：CachingTableLookup:199、TaskManager:260、JobCoordinator:510/1307、PollingJdbcMessageService:96、OpsJobManager:340、TaskCheckpointWiring:272、StreamMetricsReporter:78），且 live grep 新增 `CheckpointCoordinator.java:321` 一处 raw scheduler 命中（审计基线 8c13908bfd 之后 nop-stream 有后续批次提交）。本行批外裁决不受影响；漂移清单与站点全集刷新登记为 CL-021 复核义务的输入。

#2/#7 修复后的 live 行号变化（`sendKeepAlive` :357-363 → :365-377 含守卫；`RpcMessageHandler` 调度点 :73 指向 `sweepExpiredFutures`）属修复 diff 本身，非漂移。

## 六、验证记录（Phase 2）

- **#7 回归**：`TestRpcMessageHandlerSweepInsulation.testSweepChainSurvivesInjectedErrorAndStillExpiresFutures`（nop-netty）——注入 LinkageError 后：清扫链存活（第 2/3 轮照常触发）、过期 future 异常终态（NopTimeoutException）、观测日志 `nop.err.netty.rpc-timeout-sweep-fail` 可见。先红后绿：守卫变异（调度点直连 `checkTimeout`）→ 红（`expected: <2> but was: <1>`，链死于首轮 Error）；恢复守卫 → 绿。
- **#2 回归**：`TestJsonRpcKeepAliveInsulation.testKeepAliveThrowableDoesNotPropagateAndChainContinues`（nop-graphql-core）——注入 RuntimeException 与 LinkageError 各一轮：均不向外传播、`nop.err.websocket.keep-alive-fail` 结构化 ERROR 可见、第 3 轮正常投递 ping、`keepAliveSender` 未被取消。先红后绿：窄 catch 变异 → 红（`serialization-boom` 逃逸）；恢复 → 绿。
- **接线验证（rule 23）**：`TestJsonRpcKeepAliveInsulation.testGlobalTimerDirectScheduleWiringReachesInsulatedPath`——从 30 点表 #2 站点所属消费方模块运行时验证其真实载体形态（`GlobalExecutors.globalTimer()` 直挂 `scheduleWithFixedDelay` = DefaultScheduledExecutor 载体）路由到绝缘核心：listener 失败回调可达、链重臂、future 失败终态；运行日志出现绝缘核心 `nop.err.periodic-task.fail: task=ws-keepalive-wiring` 实证。
- **聚合门禁**：`./mvnw test -pl nop-network -am` → BUILD SUCCESS EXIT=0（nop-netty 10 条全绿，含新回归 1 条）；`./mvnw test -pl nop-service-framework -am` → BUILD SUCCESS EXIT=0（nop-graphql-core 新回归 2 条全绿；全量结果见 `ai-dev/logs/2026/10-24.md` Phase 2 条目）。
