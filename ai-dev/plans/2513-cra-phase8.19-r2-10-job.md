# 2513 CRA Phase 8.19 — R2-10 1.4 job 批（2 项 P1：MA-01 rpcPoll 终结驱动五面 / C2-001 监视器内外部 invoker 外移）

> Design Review: R2 agent_72ef639c @ 2026-10-30（第 1 轮 FAIL[1 Blocker/3 Major/5 Minor]：Blocker=裁定②「调用点零改动」monitor 重入下锁外移不发生→§3.7 重写两阶段 prepareJobLocked+startJobUnlocked；Major=①(a) 窗口 ≈25s 失真改 ≈95s 跳轮推演、放弃分支 running 悬空+禁走 handleResult、ctx 活引用改真快照；Minor×5 全闭合——第 2 轮 PASS[probe A 交错矩阵亲推无缺口；余 2 Minor[ctx 快照字段清单+反射缝、beans 行号颠倒]协调方修入]）
> Plan Status: active
> Draft Review: R2 agent_a42ed23a @ 2026-10-30（第 2 轮：9 点机械核验全部一致落盘[勘误 6 补登/activeCount 口径/裁定①(a) 默认分支/(d) 计数器并发形态/14→15×2/行号纠正×3]；残留 3 处旧值[L29/L36/L46 同组数字]由协调方随手替换为勘误 6 口径——评审裁定修订清单全部闭合）
> Draft Review: R1 agent_a42ed23a @ 2026-10-30（第 1 轮 FAIL[1 Major/0 Blocker/4 Minor]：两处基线事实失真未入勘误[activeCount 引用面/TestLocalJobScheduler 14→15]+register/deactivate/fail-safe 三处行号微漂+裁定①(a) 双分支矛盾钉默认+头部缺 Draft Review 行+失败计数器并发归属未点名——修订经 R2 复审确认落盘后闭合，见 R2 行）
> Last Reviewed: 2026-10-30
> Source: roadmap 工作项 8.19（R2-10，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L161，Status=todo[本 plan 立项时回写为「todo（plan 2513 drafted，待 draft review）」]）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-10 行（L84 全文：finding ID 清单 2 项[1.4-C4-001；1.4-C2-001] / 模块与验证命令 nop-job `./mvnw test -pl nop-job -am` / Owner Doc=`docs-for-ai/03-modules/nop-job.md` / PA=无 / 依赖「无前置（可并行）；MA-01 R01 对齐（终结驱动五面）；C2-001 R03 设施面（chain28 两面切分，消费面=5.5 随 W-01）」/ exit criteria 增补：**rpcPoll 终结驱动+失败预算/deadline 兜底/DB 回传/PollEntry 精简（失败计数器并发形态[AtomicInteger 或 volatile]随裁定成文——计数在 RPC whenComplete 回调线程与 scheduler 轮询线程两域读写）/gauge 五面一次闭合；监视器内外部 invoker 外移**）+ §一 通用门禁 E1–E6；成员发现详情 = WI 1.4 审计目录 `ai-dev/audits/2026-10/2026-10-14-2230-deep-audit-nop-job-concurrency/`（C4-thread-pools.md C4-001 :33-71 + 复核 :363/:378-390；C2-locks-sync.md C2-001 :30-94 + 复核 :376/:387/:408；吸收成员 = merge-adjudication.md §2.1 MA-01 行 :38（←C5-001/C7-001/C5-005，失效面五面枚举）+ §2.2 C2-001 行 :50（与 C2-007 共设计约束）+ §2.3-1 :80 / §2.3-3 :82（rpcPoll 站点束五关联面）；C10-test-protection.md C10-001 :74-93 / C10-003 :113-127 + 复核 :319-321/:331-343；C5-memory-bounds.md C5-001/C5-005 与 C7-long-running.md C7-001 为 MA-01 吸收成员视角源，经 merge-adjudication 归并折叠不另读原文），逐条回读路径见 Current Baseline；执行基线 @ `84588cbd01`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.18 已 done，plan 2495–2512 均已完成或收口；本 Baseline 全部行号为该基线 live 回读值，2026-10-30 亲读，执行前如再漂移以 live 为准并登记）
> Related: plan 2496（R1-01 周期任务异常绝缘层批，completed——**nop-job 域同模块先序批**：其 MA-05 三站点 catch Throwable+终态 fail-safe 与 MA-06 worker 侧看门狗已 landed（commit 84d970d008），owner doc `nop-job.md` §六 契约节在案；本批 MA-01 的 pollOnce Throwable 守卫面（MA-05 站点①）已由 2496 闭合，本批只修终结驱动面，两者面不相交、锚点行号以 2496 后 live 为准）；plan 2497（R1-02 池设施面批，completed——owner doc `nop-job.md` §七：`nopRpcPollScheduledExecutor` 容量语义/热更契约已定稿，本批 MA-01 涉同一池的条目终结面但零池参数改动）；plan 2509（R2-06，completed——R03 出锁投递范式先例 `stream-buffer-unification-and-failover-flow-concurrency.md` §3.6，本批 C2-001 锁外 invoker 外移为其同族成员投影，范式引用不重开）；plan 2502（R1-07，completed——R01 族级 SSOT `ai-dev/design/concurrency/ledger-retention-policy.md`，**本批判定 R01 不适用**，见 Baseline 对齐纪律节）；**下游**：R4-06（roadmap 8.46，G13 前半十项 1.4 调度账本束：MA-02/03/04/07 + C1-003/C1-004/C2-004/C2-007/C4-002/C4-005）**硬依赖 8.19**（roadmap L：「硬依赖 8.2（R1-01）；硬依赖 8.19（R2-10）」）——批末须回写就绪注记（含 C2-001×C2-007 共设计结论、C1-004 同注册表联调边界、C10-009 处置边界）；R4-07（8.47）软依赖 R4-06——C7-004 三表保留（R01 族成员）与 C7-008 时钟源经本批注记传导；**并行协同**：无同门禁在飞批，本批可独立开跑
> Protected Area 授权：批次 PA=无（batch-proposal §二.2 R2-10 行授权列）。本批两成员站点分别位于 `nop-job/nop-job-coordinator`（C4-001/MA-01）与 `nop-job/nop-job-local`（C2-001）——**均非 Protected Area**（AGENTS.md Protected Areas 表所列 `nop-core/`/`nop-xlang/`/`nop-api-core`/`nop-auth`/orm 模型/生成管线均不涉及；nop-job 是业务模块聚合，非框架核心引擎行）。live 回读勘误申报：本批不做 `_` 前缀生成物改动、不触 `model/*.orm.xml`、不触 `nop-job/model/nop-job.orm.xml`；MA-01 修复落 `nop-job-coordinator` engine 包非生成物 main 源码（`app-engine.beans.xml` 为 `_vfs` 下 beans 声明文件，若 Phase 1 裁定需新增配置注入仅经既有 `@InjectValue` 路径则零 beans 改动；若确需 beans 改动按 2507–2512 先例在该裁定项内批内闭环申报后方可实施）。本批不触碰 R4-06/R4-07 的批外成员本体。

## Purpose

关闭 R2-10 批的 2 项已确认 P1（均 open，cra-index 台账行 L159/L160）。核心结果面有二：①**rpcPoll 条目终结驱动五面一次闭合**（MA-01，主条目 1.4-C4-001 吸收 C5-001/C7-001/C5-005）——`RpcPollTaskManager` 条目在默认装配下四终结条件（future 终态/deadline/cancelToken/whenComplete）中三个失效（pollOnce 错误分支无失败预算 warn-and-return、`computeDeadline` 只读 schedule.timeoutSeconds 不纳入全局 `execution-timeout-ms`、DB 终态无回传通道、cancelToken 内建装配恒 null[C4-010 前置约束]），唯一活性来源 future 在远程失联时永不完成 → entries 注册表+周期句柄+5s 无退避重试风暴+WARN 洪水永久累积；修复须同盖**失败预算/退避、deadline 全局超时兜底、DB 终态→future 回传联动、PollEntry 精简视图、activeCount gauge** 五面。②**监视器内外部 invoker 外移**（C2-001，R03 设施面）——`LocalJobScheduler` per-job 监视器内调用 `job.invoker.invokeAsync(ctx)`（SPI 外部代码），平台唯一内置 invoker `BeanMethodJobInvoker` 把任意 bean 方法同步执行完毕再包 `completedFuture` → 本地模式业务方法体全程运行在 `synchronized(job)` 内：管理 API/停机（`@PreDestroy`→`deactivate`）无限期阻塞（确定性可达）+ 跨边界 EXT(L) 反向边可构造 ABBA 永久死锁。批末 roadmap 8.19 → done，cra-index 两行收敛，R4-06 硬依赖解除（随 R1-01 已 done 双前置齐备）。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-10 = 2 项成员（P1×2——merge-adjudication §4.2 P1 两项中 MA-01 即本批成员 1、C2-001 即本批成员 2，**1.4 单元全部 2 个 P1 恰好整批入 R2-10**）；cra-index 台账两行独立（L159 C4-001[MA-01，成员 C5-001/C7-001/C5-005 括注]、L160 C2-001，均 open、批次列均=R2-10）；roadmap 8.19 行 Status=todo。前置：无前置（可并行），plan 2495–2512 前序批均收口——基线门禁已满足。
- **吸收成员（随主条目同 diff，不单列工单）**：C4-001←C5-001（无界注册表视角）、C7-001（泄漏+重试风暴+WARN 放大视角）、C5-005（PollEntry 钉住三实体 JSON payload 放大器）——MA-01 四成员单修复（merge-adjudication §2.1 MA-01 行：同一缺陷三维度视角归并，主条目取 C4-001[四终结条件枚举是根因的规范陈述]）。cra-index 回填时以主 ID 收敛、吸收成员括注。
- **nop-job 域先序批状态（live 形态登记，2026-10-30 亲读）**：plan 2496（R1-01 绝缘层，commit 84d970d008）与 plan 2497（R1-02 池设施）已 landed，owner doc `nop-job.md` §六（周期任务异常绝缘契约：MA-05 三站点 Throwable 守卫+fail-safe 语义表、MA-06 worker observe-only 看门狗、AbstractBatchScanner 扫描链守卫）与 §七（调度载体容量与热更契约：pollOnce 为 globalTimer fallback 豁免面——「纯内存 deadline/cancel 检查」）在案。**任务交办输入勘误**：交办文本所称「nop-job 域 plan 2505（cron 全站落地）/2506（store-contract）先序批」与 live 不符——plan 2505=R2-02（nop-ai/nop-network）、2506=R2-03（nop-core-framework），nop-job 域先序批实为 2496/2497；Baseline 以 live 实读为准登记。
- **C10 验收前置入批集（2 条本体收口门，随本体同 diff 落地[E2]）**：C10-001（MA-01 收口门——getJobStatus 失败注入[现全测试面零失败注入] + activeCount()/entries 终结断言[activeCount() 终结断言零、唯一既有引用=2496 绝缘钉 :125] + 三终结面[失败预算/deadline 兜底/DB 回传]各自回归 + C5-005 PollEntry 驻留体积断言；建议形态 `TestRpcPollTaskManagerEntryTermination`，复用 `TestRemoteJobInvoker` mock 体系）；C10-003（C2-001 收口门——同步阻塞型 invoker × 管理 API 响应性锚点[现三个 running 态测试 invoker 全为 supplyAsync :298/:348/:398，锁范围修复在现有测试下行为不可区分]；建议形态 `TestLocalJobSchedulerManagementResponsiveness`，`LocalJobScheduler` 构造器已开放 invoker 注入，可落单元测试）。**不在本批的 C10 处置**：C10-004（宿主 C2-007，R4-06）、C10-005（宿主 C4-003，R1-01 已由 plan 2496 随本体闭合——见勘误 1 补登）
勘误 2）、C10-006（宿主 C7-011，R4-06）、C10-007（宿主 C2-004/MA-03，R4-06）、C10-008（宿主 C5-002，R4-06）、C10-009（时序敏感测试本体，G91 无宿主条目——**维持 R-G/R4-06 触达面不搭车**，见勘误 3）。

### 对齐纪律显式条款

- **MA-01 × R01 对齐（判别结论：R01 不适用，零 SSOT 成员传导行）**：R01 族级 SSOT（`ai-dev/design/concurrency/ledger-retention-policy.md`）管的是「账本表/注册表只写不逐出」的**保留策略**面（保留期/TTL/行数上限/逐出/fail-fast 包络，§3.1 七参数；进程内账本投影=生命周期配对 close 桥接，形态 D）。MA-01 的失效本体是**在途条目的终结驱动缺失**（in-flight entry 无路径到达终态），非「已终态条目不逐出」的保留缺失——修复组合（失败预算/deadline 兜底/DB 回传）是让条目按生命周期终态到达 removal，不引入任何保留期/TTL 参数、不涉任何 DB 账本表清理谓词；entries 注册表修复后随 future 终结即时移除，无「保留窗」语义可声明。故本批**不消费、不修订** R01 SSOT，无成员传导行。1.4 单元真正的 R01 族成员是 **1.4-C7-004**（nop_job_fire/task/task_log 三表保留，G13 后半）——归 R4-07，届时按 SSOT §3.6「R4-07（1.4-C7-004）按同构行对齐」条款消费本 SSOT；本批在 R4-07 就绪注记中带上该指向。若执行期裁定意外引入保留/TTL 语义（预判不会），须回 SSOT 修订而非模块内发明。
- **C2-001 × R03 对齐（chain28 两面切分：生产面本批、消费面随 W-01）**：R03 族行（family-clustering §二.1 :47）纪律 =「锁内同步 I/O/长临界区外移（快照后锁外执行、get-then-async-load、超时化）」；chain28 = 本批 C2-001（**设施面**：LocalJobScheduler 监视器覆盖外部 invoker 代码）↔ 5.5 C4-R2-001（**消费面**，已随 R3-05 W-01 批次安排）。本批只落设施面；范式先例 = plan 2509 `stream-buffer-unification-and-failover-flow-concurrency.md` §3.6（锁内仅状态机变更、快照后出锁执行、出锁段 identity 复检）引用不重开。消费面归 R3-05，本批零触碰（Non-Goal）。
- **C2-001 × C2-007 共设计约束（merge-adjudication §2.3-1 升格批次约束，跨批执行）**：C2-001（本批）与 C2-007（running 占位滞后 :322 vs :335，R4-06）作用于同一段 `executeJob` 区域，修复须合并设计、双向验收——**本批锁外移不得重引/加剧 C2-007 占位滞后**（占位 `job.running = done` 与 invokeAsync 的先行关系在锁外移重构中必须显式保序并留 R4-06 可续接的接缝），C2-007 本体修复（占位前移至 invokeAsync 之前）归 R4-06 不代做；R4-06 就绪注记中登记本批重构后的 executeJob 形态供其共设计引用。同段既有防护（:331-335 注释所述「占位先于 whenComplete 注册」防已完成 future 同步回调覆盖 running）零回退。
- **C2-001 × MA-05 站点③ 零回退**：plan 2496 已在 `LocalJobScheduler` whenComplete 回调与 `scheduleNext` 触发器计算落 Throwable 守卫+终态 fail-safe（:283-291/:336-357（勘误 6），owner doc §六 表）——本批锁外移重构不得弱化该 fail-safe 语义（回调动作 Throwable → FAILED 终态 + done 异常完结的覆盖面在锁范围变化后保持等价）。

### 2 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `84588cbd01` 2026-10-30 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级） | 文件（模块） | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 1.4-C4-001（P1；MA-01 主，←C5-001/C7-001/C5-005） | `RpcPollTaskManager.java`（nop-job-coordinator `engine`）；旁证 `RemoteJobInvoker.java`（同包，唯一终结源=future）、`DefaultJobCancelHandler.java` :30-51（cancel 只调 `invoker.cancelAsync` 不 complete future）、`JobTimeoutCheckerImpl.java` :53/:493-502（全局 executionTimeoutMs 默认 -1 + checker 侧声明注释） | `entries` CHM :65；`register` :105-132（scheduleWithFixedDelay 挂裸 `DefaultScheduledExecutor` :119-120、whenComplete 兜底 :126-127、双检 :125-127）；`activeCount()` :131-133（package-private，注释「可观测/测试用」全测试面零调用）；`scheduledExecutor()` fallback :170-174；`pollOnce` :181-215——错误分支 `err != null` warn-and-return **无失败预算** :203-206、`catch (Exception)` :210-212、`catch (Throwable)` :212-215（plan 2496 MA-05 站点① 已落，本批零触碰）；deadline 检查 :189-194；`applyStatus` :219-249（六终态映射）；`computeDeadline` :268-274 **只读 schedule.timeoutSeconds，全局 execution-timeout-ms 默认 -1 不参与，未配置时 deadline=0**；`PollEntry` :281-301（**钉住 schedule/fire/task 三实体引用**，payload=完整 JSON 行对象图[C5-005]）；javadoc「不查 DB」契约 :56-58 | rpcPoll 条目在默认装配下无终结驱动：远程失联（worker 崩溃/网络分区）×未配置 timeoutSeconds 时四终结条件中三个失效（无失败预算、deadline 恒 0、DB 终态[TimeoutChecker 已置 TIMEOUT]无回传通道、cancelToken 恒 null[C4-010]），唯一活性来源 future 永不完成 → entry+周期句柄+每 5s 失败 RPC+WARN 日志永久累积（与故障频度成正比；长时运行必然劣化） | 裁定①（batch exit criteria「rpcPoll 终结驱动+失败预算/deadline 兜底/DB 回传/PollEntry 精简/gauge 五面一次闭合」）：五面组合一次定夺，Phase 1 唯一预设默认分支——**(a) 失败预算/退避**：pollOnce 错误分支加连续失败计数，默认分支=连续失败计数超阈值→resolve `ERROR(ERR_JOB_REMOTE_INVOKE_FAILED)`（判据：entry 必须在有限时间内到达终态 + 瞬态抖动不误杀[预算阈值与退避窗口可配置、缺省值成文]；降频退避仅为达阈值前辅助形态；周期性 DB 回查归 (c) 机制，不与 (a) 双头开放）；**(b) deadline 兜底**：`computeDeadline` 纳入全局 `execution-timeout-ms`（schedule.timeoutSeconds 优先序维持），deadline=0 缺省态获得墙钟上限（新配置键命名与缺省值 Phase 1 成文；**不与 plan 340 checker 侧钉死契约冲突**——兜底位在 manager 轮询侧非 checker 侧）；**(c) DB 回传联动**：TimeoutChecker 置终态后回传 in-memory future（候选机制：退避后低频 DB 状态回查 / checker 写终态时经 manager resolve——Phase 1 按不破坏「manager 驱动轮询不查 DB」javadoc 契约的最小改动原则一次定夺，javadoc 契约随裁定同步修订）；**(d) PollEntry 精简**：失败计数器并发形态随裁定成文（AtomicInteger 或 volatile——计数在 RPC whenComplete 回调线程 :202-207 与 scheduler 轮询线程 :188-198 两域读写）；capture 冻结面收窄为终结/取消所需最小视图（taskId/scheduleId/fireId/targetHost/cancelToken/deadline/future），schedule/fire/task 全实体 JSON payload 不驻留（C5-005 驻留体积断言随 C10-001）；**(e) gauge**：`activeCount()` 升格为可观测 gauge（命名按现有 metrics 惯例 Phase 1 定）+ 失败计数可见。**前置约束（C4-010，merge-adjudication §2.3-3）**：「补 cancelToken」式修复无效（内建装配恒 null），不得以 token 通道为终结依赖；**同点协调**：C7-008（computeDeadline 时钟源——本批重写该函数时若顺带落单调/DB 时钟则 C7-008 面闭合并注记，本体仍归 R4-07 不代做）、C1-004（register 回滚面，R4-06——本批 register 触达时保序不回滚、就绪注记登记）、MA-05 站点①（已闭合零触碰）；C10-001 收口门随同 diff |
| 2 | 1.4-C2-001（P1；独立案；R03 设施面=chain28 生产面） | `LocalJobScheduler.java`（nop-job-local）；旁证 `BeanMethodJobInvoker.java` :113-127（`invokeReflective`→`toCompletionStage`：非 CompletionStage 返回值同步包 completedFuture，方法体在调用线程跑完） | timer 回调 `synchronized (job)` 内 `executeJob(job)` :303-309；`fireNow` `synchronized (job)` 内 `executeJob` :215-221；`executeJob` 锁内调 `job.invoker.invokeAsync(ctx)` :314-346（invokeAsync :322，调用方持监视器进入）；管理 API 全部取同一监视器：`getJobState` :151-158 / `getJobDetail` :64-67 / `suspendJob` :178-192 / `cancelJob` :194-207 / `removeJob` :135-149 / `deactivate` :232-250（`@PreDestroy` 入口 `LocalJobConfigLoader` :222-227）；`awaitIdle`/`awaitXxx` :442-470（监视器获取段不受 timeout 参数约束）；占位防护注释 :331-335 | per-job 监视器持有期间调用 SPI 外部代码（invoker.invokeAsync）：同步执行型 invoker（BeanMethodJobInvoker=平台推荐 YAML 模式唯一内置 invoker）使业务方法体全程持锁——管理 API/停机无限期阻塞（确定性可达，停机阻塞上界=最长 job 体时长），持应用级锁 L 的业务方法 × 持 L 调管理 API 的线程构成可构造 ABBA 永久死锁 | 裁定②（batch exit criteria「监视器内外部 invoker 外移」）：预设默认分支 = **锁范围收窄**——`executeJob` 调用点（timer 回调 :303-309 / fireNow :215-221）改为「锁内完成状态迁移（WAITING→RUNNING、`job.running` 占位、scheduledFire 清理）→ 释放监视器 → 锁外调 `invoker.invokeAsync`」；whenComplete 回调重新取锁做终态迁移（既有形态 :336-357（勘误 6） 保持）；锁外段 identity/状态复检（job 已被 remove/deactivate 时的放弃判定，对齐 plan 2509 §3.6 出锁段复检范式）；`awaitXxx` 监视器获取段的 timeout 语义按裁定②一并处置或显式声明残留边界（Phase 1 成文）。**共设计约束（§2.3-1 升格）**：占位 `job.running = done` 与 invokeAsync 先行关系显式保序（不重引/不加剧 C2-007 占位滞后，留 R4-06 接缝）；MA-05 站点③ fail-safe（:336-357（勘误 6）/:283-291）零回退；**修法边界**：不在本批改 `BeanMethodJobInvoker` 同步执行语义本身（「invoker 必须真异步」的 SPI 契约文档化归 owner doc 面，javadoc 契约声明随裁定落，`TestBeanMethodJobInvoker` 零回退）；C10-003 收口门随同 diff |

### 平台设施与测试资产盘点（live 实读 @ `84588cbd01`）

- **先例范式可引用不重开**：plan 2509 §3.6 R03 出锁投递范式（锁内摘取/置位、出锁执行、出锁段 identity 复检、单 drain 守卫信号序——裁定②的锁外段复检直接投影）；plan 2496 绝缘层契约（owner doc §六——本批两站点周边 Throwable 守卫形态已定稿，零重开）；plan 2512 三联裁定模式（环境抖动 stash 基线同红/solo 绿/复跑全绿，见勘误 5）；merge-adjudication §5 批次 1 建议的「先落 C10 缺保护测试（缺陷态红）→ 修复（转绿）」次序纪律。
- **既有测试资产（回归门禁与改写/补测对象）**：`TestRemoteJobInvoker`（13 测试，T9——六终态映射 + 配置态墙钟超时 + cancelToken 监听器摘除 `:254` 全模块唯一泄漏守卫 + hung poll 隔离，零回退钉；**getJobStatus 全部成功返回零失败注入**——C10-001 补测本体）；`TestRpcPollTaskManagerInsulation`（2 测试，plan 2496 新增——pollOnce Error 不逃逸 + 链存活，**零回退钉**）；`TestJobCoordinatorScanner`（~25 测试，含 `testE2E_rpcPoll_fullChain` :1347 与 `testE2E_rpcPoll_wallClockTimeout` :1390[setTimeoutSeconds(1) 配置态] 两 E2E——MA-01 修复后行为面回归与端到端载体）；`TestDefaultRpcPollTaskClient`/`TestRpcJobInvoker`（RPC 契约，零回退）；`TestLocalJobScheduler`（15 测试[T1 14+2496 站点③ 1]，T1——注册/暂停恢复/取消/fireNow/once/cancel-while-running 系列，零回退钉；:298/:348/:398 supplyAsync 三 running 态测试为 C10-003 缺口证据；:142/:163/:193 Thread.sleep 为 C10-009 命中面，本批零触碰）；`TestBeanMethodJobInvoker`（10 测试，纯反射契约，裁定②零回退）；`TestLocalSchedulerResultContract`（JobFireResult 契约 + 病态 cron，零回退）；`TestJobTimeoutChecker`（~33 测试，plan 340 契约钉 `testLongRunningTaskWithoutTimeoutNotKilled` :614-639——**MA-01 兜底位在 manager 侧不得使该钉红**，零回退）；`TestJobWorkerScanner`（18 测试，plan 2496 MA-06 看门狗回归面，本批零触碰零回退）；`TestJobStoreImpl`/`TestJobFireStoreRace`（DB CAS 语义，本批零触碰）。
- **owner doc 现状**（2026-10-30 亲读，`docs-for-ai/03-modules/nop-job.md` 515 行）：§一.4 注意事项 :209-216（本地模式约束声明——裁定②落锁外移契约后补「管理 API 响应性/停机不阻塞于 job 体」语义句）；§三.1 IJobInvoker SPI :316-329（「必须实现」契约节——裁定②的 invoker 异步性契约声明落点候选）；§五 配置项参考（协调器）:405-421（`execution-timeout-ms` :414 既有行——MA-01 兜底配置键若新增在此登记）；§六 周期任务异常绝缘契约 :444-466（plan 2496，本批零改动）；§七 调度载体容量与热更契约 :468-476（**「pollOnce 为纯内存 deadline/cancel 检查，属平台处置矩阵豁免面」表述 :472-473——MA-01 落失败预算/回查后该豁免表述须复核修订**，Phase 3 义务）；源码锚点表 :493-508（RpcPollTaskManager 未列锚——MA-01 落地后按 E5 补行）。**本批 owner doc 主落点**。

### Baseline 勘误（输入不一致登记）

1. **行号核对（审计→live @ `84588cbd01`，漂移主因 = plan 2496/2497 落地插入注释与守卫块，机制面零实质漂移）**：C4-001 `entries` :65（审计 CHM 无行号冲突）✓、`register` live :105-132（审计 :119-128 为句柄注册区间，live 句柄 :119-127、双检 :129-131（勘误 6 纠正为 :125-127））✓、`activeCount` live :131-133（审计 :130-132，+1 粒度）✓、`pollOnce` live :181-215（审计 :175-207，**头部新增 plan 2496 javadoc 7 行致整体下移**）、错误分支 `err != null` live :203-206（审计 :195-203）✓、`catch (Exception)` live :210-212（审计 :204-206 为区间粒度）✓、**`catch (Throwable)` live :212-215 为 plan 2496 新增（审计时不存在）**、deadline 检查 live :189-194（审计无独立行号）✓、`computeDeadline` live :268-274（审计 :259-265，下移 9 行）✓、`PollEntry` live :281-301（审计未给行号）✓、javadoc「不查 DB」live :56-58（审计 :56-57）✓、`DefaultJobCancelHandler` live :30-51（审计 :35-49 为区间粒度）✓；C2-001 `getJobDetail` live :64-67（审计 :63-67，±1）✓、timer 回调 live :303-309（审计 :301-309，**头部插入 MA-05 触发器计算守卫 :283-291 致下移**）、`fireNow`+executeJob live :215-221（审计 :209-224 区间内）✓、`executeJob` live :314-346、invokeAsync live :322（审计 :321，+1）、占位 `job.running = done` live :335（审计 :334，+1；**plan 2496 站点③ fail-safe 使 whenComplete 体扩为 :336-357**，审计 :335-344 为旧粒度）、`deactivate` live :232-241（勘误 6 纠正：审计 :232-241 正确，前稿勘误误改 :232-250）、`awaitIdle` live :442-470（审计 :405-466 旧粒度，MA-05 插入致下移）、`BeanMethodJobInvoker` :113-127 ✓、`LocalJobConfigLoader` @PreDestroy live :222-227（审计 :223-227）✓。
2. **MA-05 站点①已闭合勘误**：审计 C4-004/MA-05「pollOnce 仅 catch Exception，Error 逃逸静默抑制」的陈述在 live 已失效——plan 2496（commit 84d970d008）已落 catch Throwable + `nop.job.remote.poll-error` ERROR（:212-215）+ 回归钉 `TestRpcPollTaskManagerInsulation`（2 测试）；本批 MA-01 的修复面收窄为终结驱动（失败预算/兜底/回传/精简/gauge），pollOnce Throwable 守卫零触碰。
3. **C10-009 处置勘误（G91 无宿主条目，本批不搭车）**：merge-adjudication §4.4 C10-009 注「位于批次 2[LocalJobScheduler 束]触达面内随束修复」；实际批次切分后 LocalJobScheduler 束跨 R2-10/R4-06 两批，C10-009 的三处 sleep 命中（`TestLocalJobScheduler` :142/:163/:193）关联的是 once 语义/SUSPENDED 热更面（MA-03/C2-005 宿主，R4-06），与本批 C2-001 锁范围修复机制面无直接关系；本批强落会扩测试基建面且其建议修法（fake executor seam）与 R4-06 的 MA-02/03/04/C2-004 共测试基建自然同批——**维持 G91 归 R-G、触达义务随 R4-06**（就绪注记声明），cra-index 回填时注记「C10-009 维持 deferred，触达批=R4-06」。
4. **交办输入勘误（先序批编号）**：交办文本「nop-job 域 plan 2505（cron 全站落地）/2506（store-contract）」与 live 不符（2505=R2-02、2506=R2-03）；nop-job 域先序批实为 plan 2496（R1-01）/2497（R1-02），Baseline 已按 live 登记。
5. **环境项预登记（批前既有）**：`./mvnw test -pl nop-job -am` 触达 nop-kernel/nop-persistence 上游——nop-kernel 计时 flake 与上游模块环境抖动按 plan 2512 先例**「stash 基线同红 / solo 绿 / 恢复复跑全绿」三联裁定**处理并留痕，不得静默重跑或跳过。

6. **勘误 1 补登（draft review R1 Major-1/Minor-2，2026-10-30）**：① activeCount 引用面——审计时零引用成立，live 唯一引用=plan 2496 新增 `TestRpcPollTaskManagerInsulation` :125（非终结断言）；C10-001「升格为事实断言」口径改为「终结断言零、唯一既有引用为绝缘钉」；② `TestLocalJobScheduler` 计数 14→15（2496 站点③新增 `testWhenCompleteErrorTerminalizesJobNotStuckRunning` :497），零回退钉「既有 14 用例」改「既有 15 用例」；③ register 子区间 live：whenComplete 兜底=:123、`future.isDone` 双检=:125-127、register 止于 :128；`deactivate` live=:232-241（审计值正确，前稿勘误主客颠倒，纠正）；MA-05 站点③ fail-safe=:336-357（:358-365 为收括+handleResult 起始）。

## Goals

- Phase 1 设计裁定文档一次裁决两项裁定（独立 draft review 通过），无临场发明空间：①MA-01 rpcPoll 终结驱动五面（失败预算/退避 + deadline 全局兜底 + DB 回传 + PollEntry 精简 + gauge，C4-010 前置约束 + C7-008/C1-004 同点协调成文）；②C2-001 监视器内外部 invoker 外移（锁范围收窄 + 出锁段复检 + C2-007 共设计接缝 + awaitXxx 语义处置）——每条唯一预设默认分支 + 测试缝 + 零回退钉清单。
- 2 项成员全部修复并各带回归测试（rule 25）；C10 两条收口门（C10-001/C10-003）随本体同 diff 落地。
- 关键工单先红后绿或变异/回退论证留痕（E2）：红① 终结缺失红（getJobStatus 恒 failedFuture + 无 timeoutSeconds——缺陷态 `activeCount()>0` 永不终结[deadline 轮询观察窗]红→修复后失败预算/兜底/回传路径各自终结绿 + 驻留体积断言绿）；红② 锁范围红（latch 阻塞型同步 invoker × `getJobState`/`deactivate` ≤500ms 响应性断言——缺陷态恒超时红→修复后绿）。
- 联调纪律显式守约（本批三条）：**C2-001×C2-007 共设计接缝**（锁外移不重引占位滞后，R4-06 就绪注记登记重构后 executeJob 形态）；**MA-01×C1-004 同注册表边界**（register 触达保序不回滚，C1-004 本体归 R4-06）；**MA-01×C7-008 同函数边界**（computeDeadline 重写时钟源结论注记，C7-008 本体归 R4-07）。
- Owner doc 随批更新：`docs-for-ai/03-modules/nop-job.md`（§七 pollOnce 豁免表述复核修订、§一.4/§三.1 契约句、§五 配置键登记、源码锚点补行）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
- 下游就绪义务：批末向 R4-06（8.46）回写硬依赖就绪注记（含 C2-007 共设计结论、C1-004/C4-002 同文件/同区域联调边界、C10-004/006/007/008 前置集确认、C10-009 触达声明）；向 R4-07（8.47）注记 C7-004→R01 SSOT §3.6 指向与 C7-008 时钟源结论。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 R4-06 成员**（G13 前半十项：1.4-MA-02/MA-03/MA-04/MA-07 + 1.4-C1-003/C1-004/C2-004/C2-007/C4-002/C4-005）及其 C10-004/006/007/008——硬依赖本批，本批零承接零顺手修；特别地 **C2-007**（占位滞后）为 C2-001 同方法相邻工单——本批只落锁外移并保占位先行关系接缝，占位前移本体归 R4-06 不代做；**C4-002**（本地池隔离缺失）与裁定②同文件相邻——锁外移后阻塞 invoker 不再持锁但仍占调度线程，C4-002 本体（触发/执行分池）归 R4-06 不代做（裁定②的 owner doc 契约句不得提前声明 C4-002 已修）。
- **不做 R4-07 成员**（G13 后半十项，含 C7-004 三表保留[R01 族]与 C7-008 时钟源本体）——C7-008 仅在本批 computeDeadline 重写顺带闭合时注记，本体不立项不代做。
- **不做 5.5 C4-R2-001 消费面**（chain28 两面切分的对侧）——随 R3-05 W-01，本批零触碰。
- **不做 MA-05/C4-003 面**（plan 2496 已闭合）——pollOnce Throwable 守卫、scanner 链守卫、worker 看门狗零触碰；MA-06 的 observe-only 看门狗与 MA-01 终结驱动是**双通道不合并**（worker 侧观测 vs coordinator 侧终结仲裁，owner doc §六 已声明「不存在双重终结」，本批维持该声明）。
- 不触碰 plan 2496/2497 已修面（绝缘层/池设施——零回退引用）、`model/*.orm.xml`、`_` 前缀生成物；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-job/nop-job-coordinator`：`engine/RpcPollTaskManager`（C4-001/MA-01 五面）+ 测试（`TestRpcPollTaskManagerEntryTermination` 新增[C10-001] + `TestRemoteJobInvoker`/`TestRpcPollTaskManagerInsulation`/`TestJobCoordinatorScanner` 零回退）。
- `nop-job/nop-job-local`：`LocalJobScheduler`（C2-001 锁范围收窄）+ 测试（`TestLocalJobSchedulerManagementResponsiveness` 新增[C10-003] + `TestLocalJobScheduler`/`TestBeanMethodJobInvoker`/`TestLocalSchedulerResultContract` 零回退）。
- 设计文档：`ai-dev/design/concurrency/job-rpcpoll-termination-and-invoker-lock.md`（新建；裁定①②+测试缝+零回退钉，最终状态写法）；Owner doc：`docs-for-ai/03-modules/nop-job.md`、`docs-for-ai/04-reference/source-anchors.md`（按实际）。

### Out Of Scope

- Non-Goals 所列全部项；R4-06/R4-07/R3-05 及后续批次工单；nop-kernel 平台设施本体（DefaultScheduledExecutor/BindScheduledExecutor/GlobalExecutors 只读引用）；`model/*.orm.xml` 与 `_` 前缀生成物（含 `_vfs` beans 文件除非裁定触发批内申报）。

## Execution Plan

### Phase 1 - job 并发设计裁定文档 + 独立评审

Status: done
Targets: `ai-dev/design/concurrency/job-rpcpoll-termination-and-invoker-lock.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [x] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下两项裁定+测试缝，每条唯一预设默认分支：
  - [x] **裁定① MA-01 终结驱动五面**：(a) 失败预算/退避——默认分支=连续失败超阈值 resolve ERROR(ERR_JOB_REMOTE_INVOKE_FAILED)，降频退避为达阈值前辅助形态、DB 回查归 (c)；阈值/窗口配置键命名与缺省值成文（判据：entry 有限时间终态 + 瞬态抖动不误杀）；(b) deadline 兜底——`computeDeadline` 纳入全局 `execution-timeout-ms` 的取值优先序（schedule.timeoutSeconds > 全局 > 无）与新键缺省值成文，兜底位=manager 轮询侧的 plan 340 钉不冲突声明；(c) DB 回传——机制三选一（退避后低频 DB 回查 / checker 写终态经 manager resolve / 其他）按「最小改动 + 不破坏 manager javadoc 契约（随裁定修订）」一次定夺；(d) PollEntry 精简——失败计数器并发形态（AtomicInteger/volatile，两线程域）成文；capture 冻结面最小视图清单成文（taskId/scheduleId/fireId/targetHost/cancelToken/deadline/future），C5-005 驻留体积断言口径成文；(e) gauge——`activeCount()` 升格命名与失败计数可见性成文；C4-010「补 cancelToken 无效」前置约束 + C7-008/C1-004 同点协调边界 + MA-05 站点① 零触碰声明成文
  - [x] **裁定② C2-001 锁外移**：预设默认分支 = 锁内状态迁移 → 释放监视器 → 锁外 invokeAsync（两个调用点 timer :303-309 / fireNow :215-221 逐一处置表）；出锁段 identity/状态复检（remove/deactivate 交错放弃判定，2509 §3.6 范式投影）；占位先行关系接缝（C2-007 共设计，R4-06 可续接）成文；`awaitXxx` 监视器获取段 timeout 语义处置或显式残留边界一次定夺；MA-05 站点③ fail-safe 零回退义务 + 既有占位防护（:331-335）零回退成文；invoker 异步性 SPI 契约文档化落点（§三.1/§一.4）成文
  - [x] **裁定③ 测试缝与零回退钉清单**：测试缝 = getJobStatus 恒失败 + 无 timeoutSeconds 终结观察（①[+deadline 兜底变体 +DB 回传变体 +PollEntry 驻留体积]）、latch 阻塞型 invoker × 管理 API/停机响应性（②[+awaitIdle 上界变体]）；零回退钉 = `TestRemoteJobInvoker`（13 测试全列，含 `:254` 监听器摘除）、`TestRpcPollTaskManagerInsulation`（2 测试）、`TestJobCoordinatorScanner`（含两 rpcPoll E2E）、`TestDefaultRpcPollTaskClient`、`TestRpcJobInvoker`、`TestLocalJobScheduler`（补测外其余用例）、`TestBeanMethodJobInvoker`、`TestLocalSchedulerResultContract`、`TestJobTimeoutChecker`（plan 340 钉 :614-639）、`TestJobWorkerScanner` 全列；补测类（C10-001/003）标注「新增断言即新钉、缺陷态红→修复后绿」
- [x] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动——记录于本 plan 头部 Draft Review 行或 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–③全部项且每条有唯一预设默认分支，独立 draft review 记录在案
- [x] 裁定①含 C4-010 前置约束与 C7-008/C1-004 协调边界、plan 340 钉不冲突声明；裁定②含 C2-007 接缝与 MA-05 零回退义务；裁定③含零回退钉全列与补测类缺陷态红口径
- [x] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 2 成员实现 + 回归测试（按模块两组）

Status: done
Targets: `nop-job/nop-job-coordinator/src/`、`nop-job/nop-job-local/src/`——各自 main + test 树

- Item Types: `Fix`（2 项均为已确认 live defect，不得写成 Feature）、`Proof`（单元测试 + C10 收口门）

- [x] 组 coordinator：C4-001/MA-01（终结驱动五面，按裁定①）+ C10-001 收口门
- [x] 组 local：C2-001（锁外移，按裁定②）+ C10-003 收口门
- [x] 回归测试（rule 25 显式覆盖 + C10 收口门同 diff，先红后绿或变异/回退论证留痕）：
  - [x] C4-001/C10-001 红①：getJobStatus 恒 failedFuture + 无 timeoutSeconds——修复前 `activeCount()>0` 永不终结红 → 修复后失败预算终结绿；deadline 兜底变体（不配 timeoutSeconds、注入全局 executionTimeoutMs → 墙钟上限内终结）+ DB 回传变体（checker 置 TIMEOUT → future 完成 ERR_JOB_TIMEOUT + activeCount 归零）+ PollEntry 驻留体积断言 + gauge 断言各自绿；每终结测试末尾统一 `activeCount()==0` 断言（终结断言零→升格为事实断言；唯一既有引用为绝缘钉）
  - [x] C2-001/C10-003 红②：latch 阻塞型 invoker（BeanMethodJobInvoker 同步形态模拟）——修复前 `getJobState`/`deactivate` 响应性断言恒超时红 → 修复后 ≤500ms 绿 + job 体最终自然完成不崩；`awaitIdle` 执行期上界变体绿
- [x] **端到端验证**（rule 22）：≥1 条 rpcPoll 全链 E2E（DB 集成 + 真实轮询——覆盖裁定①失败/兜底路径与 DB 回传联动；`TestJobCoordinatorScanner` 两既有 rpcPoll E2E 为载体，扩展错误注入 leg 或新增用例）；≥1 条本地模式链路（YAML/编程式注册 → 触发 → 同步 invoker 执行 → 执行期管理 API 可达 → 停机不阻塞——覆盖裁定②，`TestLocalJobScheduler` 扩展）
- [x] **接线验证**（rule 23）：裁定①失败预算/兜底/回传真实被 `pollOnce`→`applyStatus`/`cancelEntry` 链消费断言（非仅字段存在）；裁定②锁外 invokeAsync 真实脱离监视器断言（响应性断言即接线证据）+ whenComplete 回调重新取锁终态迁移连通断言
- [x] **无静默跳过**（rule 24）：裁定①失败预算触发的 resolve 显式终态（非静默清除）；DB 回传机制（若裁回查）查询失败显式降级路径留痕非空 catch；裁定②出锁段放弃判定显式分支（非静默吞）；新增公共分支无空方法体/TODO
- [x] **提交纪律**：设计文档先行单独提交；按组提交——coordinator / local，C10 测试与对应本体同一 commit；组间无耦合时可独立提交序列
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 2 项成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 两条收口门同 diff 落地
- [x] 必红证据（E2）：红①红②均先红后绿或变异/回退论证，快照落 `_tmp/` 并登记
- [x] 零回退实证：零回退钉清单全部保持绿（钉零回改逐条 git diff 为空核对；`TestRpcPollTaskManagerInsulation`、`TestRemoteJobInvoker:254` 监听器摘除、`TestJobTimeoutChecker` plan 340 钉、`TestLocalJobScheduler` 既有 15 用例为关键钉）
- [x] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选
- [x] `./mvnw test -pl nop-job -am` BUILD SUCCESS（上游环境抖动按 Baseline 勘误 5 三联裁定处理并留痕）；Owner doc 内容定稿归 Phase 3 统一落账（显式登记去向）
- [x] `ai-dev/logs/` 对应日期条目已更新


- 偏差登记（实施期，3 处）：① IRpcPollTaskClient 未删实体参数方法（删除会破坏不可改钉 mock）——落地为新增窄参数 default 方法+壳回退、实体方法保留并委托窄形态共享请求路径，C5-005 PollEntry 零实体引用目标完全达成；② PollEntry 白名单 10→12 项（+request 冻结标量快照/+skipRounds 退避计数），缝4 反射断言仍成立；③ 红①红②采用变异/回退论证（stash 全量回退会使新测试无法编译），四点缺陷逐一回引后复跑全绿。


- 偏差登记（closure audit 抓出，2 项闭环）：① C10-003 新钉 `testC2_001_abandonAfterInterleave_explicitTerminalNotHandleResult` 首版时序不稳定（repeatFixedDelay 首轮立即触发与手工编排竞态，1/5 红；红轮产品行为正确）→ 稳定化[首触隔离]，断言语义不变，5 连跑绿（`_tmp/2513-flaky-fix-5runs.log`）；② rule 22「扩展错误注入 leg 或新增用例」以新测试类等价承载（TestRpcPollTaskManagerEntryTermination/TestLocalJobSchedulerManagementResponsiveness），TestJobCoordinatorScanner 零改动；③ E3 完整留痕：`_tmp/2513-gate-nop-job-full-r2.log` BUILD SUCCESS EXIT=0（首跑 fork 启动崩溃为环境级 flake）。

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: `nop-job` 聚合全量测试；`docs-for-ai/03-modules/nop-job.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路 `-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2497–2512 Phase 3 先例）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-job -am` BUILD SUCCESS 且登记（模块/测试数/退出码/快照路径）；环境抖动按三联裁定留痕；实际触达模块以 git diff 为准逐条登记；零触碰确认：产品 diff 不含 `model/*.orm.xml`、`_` 前缀生成物、R4-06/R4-07 批外成员本体（C2-007/C1-004/C4-002/C7-004/C7-008 等）、MA-05/MA-06/2497 已修面（`AbstractBatchScanner`/`JobWorkerScannerImpl`/`JobTimeoutCheckerImpl` 零 diff 核对登记）
- [ ] Owner doc 更新：`nop-job.md`——§七 pollOnce 豁免表述复核修订（失败预算/回查落地后「纯内存检查」表述如实化）、§一.4 管理 API 响应性/停机契约句（裁定②）、§三.1 IJobInvoker 异步性契约声明（若裁定②落）、§五 新增配置键登记（MA-01 兜底/预算/gauge 键）、源码锚点表补 RpcPollTaskManager 行；`source-anchors.md` 新增锚点（按实际）
- [ ] 工具门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-job --severity high` 退出码 0（批前既有发现逐条裁定登记）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] E1 + 批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] nop-job.md 五处落点 + source-anchors.md 锚点；check-doc-links --strict 退出码 0；scan-hollow 逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] 零触碰确认登记（orm.xml/生成物/R4-06/R4-07 批外成员本体/2496·2497 已修面）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.19 行、cra-index 台账、R4-06/R4-07 就绪注记

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（失败预算/兜底/回传/gauge 与锁外移运行时真实生效、无空方法体/静默吞异常）
- [ ] cra-index 台账回填：L159/L160 两行状态收敛（open → fixed，注记 plan 2513；吸收成员 C5-001/C7-001/C5-005 随 C4-001 主行括注）；C10-001/003 收敛注记；C10-009 注记「维持 deferred，触达批=R4-06」；roadmap 8.19 行 Status → done；R4-06（8.46）硬依赖就绪注记（沿 2512 R4-05 先例——含 C2-007 共设计结论与 executeJob 重构后形态、C1-004/C4-002 联调边界、C10-004/006/007/008 前置集确认、C10-009 触达声明）；R4-07（8.47）注记（C7-004→R01 SSOT §3.6 指向、C7-008 时钟源结论）
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 + scan-hollow 全绿复核；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 2 行收敛 + C10 注记（含 C10-009 处置）、roadmap 8.19 = done、R4-06/R4-07 就绪注记完成
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 2 项成员全部修复：C4-001/MA-01（终结驱动五面一次闭合）、C2-001（监视器内外部 invoker 外移）
- [ ] batch-proposal R2-10 行 exit criteria 增补两段逐条达成并留证：①**rpcPoll 终结驱动+失败预算/deadline 兜底/DB 回传/PollEntry 精简/gauge 五面一次闭合**（恒失败注入+终结断言+驻留体积+gauge）；②**监视器内外部 invoker 外移**（阻塞 invoker × 管理 API/停机响应性锚点）
- [ ] 联调纪律守约：C2-001×C2-007 接缝保序（占位先行关系不回退，R4-06 注记在案）；MA-01×C1-004 register 保序边界；MA-01×C7-008 时钟源结论注记；MA-05 站点①③ fail-safe 零回退
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；红①红②均有先红后绿或变异/回退论证（E2）；C10 两条同 diff 落地
- [ ] 设计文档裁定①–③成文且经独立 draft review；既有钉（零回退钉清单全列）零回退实证
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 `./mvnw test -pl nop-job -am` BUILD SUCCESS（E1/E3；上游环境抖动三联裁定留痕）
- [ ] Owner doc 更新完成：nop-job.md（五处落点）+ source-anchors.md（E5）
- [ ] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）五面终结驱动与锁外移运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile` + `./mvnw test`（nop-job 聚合）；checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 2 行收敛 + roadmap 8.19 → done + R4-06/R4-07 就绪注记（E6）

## Deferred But Adjudicated

### R4-06 成员（G13 前半十项：1.4-MA-02/MA-03/MA-04/MA-07 + 1.4-C1-003/C1-004/C2-004/C2-007/C4-002/C4-005）及其 C10-004/006/007/008

- Classification: `out-of-scope improvement`（batch-proposal R4-06 行显式安排，非本批降级）；Successor Required: `yes`；Successor Path: R4-06 批 plan（roadmap 8.46，硬依赖 8.19——本批 done 后解除）
- Why Not Blocking Closure: 判级对齐零重复——cra-index 台账 G13 行批次列=R4-06/R4-07；本批已收敛 1.4 全部 2 个 P1，R4-06 实施以本批后的 live 形态为基线，且可引用本批裁定②（C2-007 接缝/executeJob 重构形态）与裁定①（C1-004 同注册表边界）为既有裁定不重开体。

### R4-07 成员（G13 后半十项，含 1.4-C7-004[R01 族]/C7-008）与 5.5 C4-R2-001 消费面（随 R3-05 W-01）

- Classification: `out-of-scope improvement`（batch-proposal R4-07/R3-05 行显式安排）；Successor Required: `yes`；Successor Path: R4-07（roadmap 8.47）/ R3-05（8.35）
- Why Not Blocking Closure: C7-004 到达时按 R01 SSOT `ledger-retention-policy.md` §3.6 同构行对齐（本批已判定 MA-01 不涉保留语义、零 SSOT 传导，指向已登记）；C7-008 本体（时钟源统一）独立于本批兜底位（若本批重写顺带闭合则注记消号）；chain28 消费面随 W-01 两面切分既定安排。

### 1.4-C10-009（时序敏感测试本体，G91 无宿主条目）

- Classification: `out-of-scope improvement`（cra-index G91 行 → 治理批 R-G；merge-adjudication §4.4 触达义务随 LocalJobScheduler 束=R4-06）；Successor Required: `yes`；Successor Path: R-G 治理批（触达随 R4-06）
- Why Not Blocking Closure: 三处 sleep 命中（`TestLocalJobScheduler` :142/:163/:193）关联宿主为 MA-03/C2-005 面（R4-06），与本批 C2-001 锁范围修复机制面无直接关系；本批强落会扩测试基建面且与其建议修法（fake executor seam）同 R4-06 共测试基建自然同批（勘误 3）。

## Non-Blocking Follow-ups

- **R4-06 对接注记**：本批落地后向 roadmap 8.46 行作「8.19 就绪 + 8.2（R1-01）已 done」双前置完成注记（硬依赖解除落点）；R4-06 实施引用裁定①②为既有裁定。
- **MA-01 gauge 的运维观测面**：activeCount gauge 与失败计数在生产长时运行负载下的告警阈值归运维观测（owner doc §五 留观测指引即可，不阻塞 closure）。
- （占位）执行期发现的小额治理项（如命名/注释/测试基建微整）在此登记，均不得承载 in-scope live defect。

## Closure

Status Note: （完成或关闭时填写）
Completed: —

Closure Audit Evidence:

- Reviewer / Agent: （独立 closure audit 子代理，待 Phase 4）
- Evidence: （待 Phase 4）

Follow-up:

- （待 Phase 4 填写；只记录 non-blocking follow-up）
