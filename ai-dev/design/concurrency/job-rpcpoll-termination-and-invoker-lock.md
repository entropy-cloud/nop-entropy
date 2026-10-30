# job rpcPoll 终结驱动五面与本地调度器 invoker 锁范围（R2-10 批两项裁定）

> Status: active
> Last Reviewed: 2026-10-30（plan 2513 Phase 1 产出；本批唯一设计裁决权威）
> Source: plan 2513（`ai-dev/plans/2513-cra-phase8.19-r2-10-job.md`）Current Baseline 两成员表 + Baseline 勘误 6 条 + draft review R1/R2 增补（裁定①(a) 默认分支已钉、(d) 计数器并发形态两线程域）；发现事实源 = WI 1.4 审计目录 `ai-dev/audits/2026-10/2026-10-14-2230-deep-audit-nop-job-concurrency/`；行号 = live `84588cbd01`（分支 `concurrency-audit-remediation`）2026-10-30 亲读回读值，执行期漂移以 live 为准并登记于 plan 勘误
> 实现锚：nop-job-coordinator `engine/RpcPollTaskManager`（entries CHM :65、register :105-128[whenComplete 兜底 :123、isDone 双检 :125-127]、activeCount :131-133、scheduledExecutor fallback :170-173、pollOnce :181-216[错误分支 :203-206、MA-05 站点① Throwable 守卫 :210-215 零触碰]、applyStatus :219-251、computeDeadline :268-274、PollEntry :281-349、javadoc「不查 DB」:56-58）+ `engine/JobTimeoutCheckerImpl`（executionTimeoutMs :53、注入键 :121、plan 340 钉 :485-502）+ `engine/DefaultJobCancelHandler` :30-51；nop-job-local `LocalJobScheduler`（timer 回调 :302-310、fireNow :215-222、executeJob :314-359[invokeAsync :322、占位 :331-335、whenComplete+fail-safe :336-357]、deactivate :232-241、awaitIdle :442-458）+ `BeanMethodJobInvoker.invokeReflective/toCompletionStage` :113-127；旁证 `RemoteJobInvoker`（唯一终结源=future）
> Related：出锁投递范式先例 = [stream-buffer-unification-and-failover-flow-concurrency.md](stream-buffer-unification-and-failover-flow-concurrency.md) §3.6（plan 2509，裁定②出锁段复检直接投影，引用不重开）；周期任务绝缘契约 = [periodic-task-insulation.md](periodic-task-insulation.md)（MA-05 站点①③ 已闭合零触碰，§3.1 失败治理参数为裁定①(a) 退避形态对齐模板）；R01 族级 SSOT = [ledger-retention-policy.md](ledger-retention-policy.md)（本批判定 R01 不适用、零成员传导行，§3.6 指向注记归 R4-07）

## 一、裁定总表

| 裁定 | 成员 | 唯一结论 |
|------|------|---------|
| ① | 1.4-C4-001（P1；MA-01 主，←C5-001/C7-001/C5-005） | 终结驱动五面一次闭合：(a) 连续失败超阈值（`nop.job.remote.poll-fail-threshold`，缺省 5）resolve ERROR(ERR_JOB_REMOTE_INVOKE_FAILED)，阈值前按 2^n 轮跳轮退避封顶 12 轮；(b) `computeDeadline` 优先序 schedule.timeoutSeconds > 全局 `nop.job.coordinator.execution-timeout-ms`（>0 时，零新键）> 无（0）；(c) DB 回传=checker 写终态经 manager package-private resolve 回传（弃低频 DB 回查）；(d) PollEntry 冻结标量最小视图 + 失败计数 AtomicInteger；(e) gauge `nop.job.remote.poll.active-count` + counter `nop.job.remote.poll.failures{taskId}` |
| ② | 1.4-C2-001（P1；独立案；R03 设施面=chain28 生产面） | 锁范围收窄：`executeJob` 拆两阶段 `prepareJobLocked`（锁内前置检查+占位+状态迁移）+ `startJobUnlocked`（出锁 identity/状态复检 + invokeAsync + whenComplete 重取锁终态迁移）——调用点 synchronized 块在两阶段之间闭合；出锁放弃分支重取锁显式清 running + 完结占位（禁走 handleResult）；whenComplete 重取锁形态与 MA-05 站点③ fail-safe :336-357 零回退；awaitXxx 段零改动、残留边界显式声明 |
| ③ | C10 验收前置 | 两条收口门（C10-001/003）随本体同 diff；五个测试缝 + 零回退钉全列（§3.8）；红①红②先红后绿义务（E2） |

## 成员 → 裁定映射

| # | 成员（判级） | 面 | 裁定 | 修复位（live : 行） | 回归测试（缝，§3.8） |
|---|---|---|---|---|---|
| 1 | 1.4-C4-001（P1，MA-01 主；C5-001/C7-001/C5-005 吸收） | rpcPoll entry 四终结条件三失效 → entries+句柄+失败 RPC+WARN 无界累积 | ①(a)(b)(c)(d)(e) | RpcPollTaskManager pollOnce 错误分支 :203-206、computeDeadline :268-274、PollEntry :281-349、activeCount :131-133、javadoc :56-58 | 缝 1–4（C10-001 新建） |
| 2 | 1.4-C2-001（P1，独立案；R03 设施面=chain28 生产面） | per-job 监视器内 invokeAsync（同步型 invoker 方法体全程持锁）→ 管理 API/停机无限期阻塞 + 可构造 ABBA | ② | LocalJobScheduler executeJob 两阶段化 :314-359（prepareJobLocked 锁内 / startJobUnlocked 锁外，invokeAsync :322 出锁） | 缝 5（C10-003 新建） |
| 3 | C10 验收前置（2 收口门随本体同 diff） | 假保护缺口补测 + 零回退钉 | ③ | 测试树（§3.8 两表） | 缝 1–5 全覆盖 |

## 二、背景与动机

R2-10 批两项 P1 覆盖 nop-job 两类缺陷面：**在途条目的终结驱动缺失**（RpcPollTaskManager）与**监视器内执行 SPI 外部代码**（LocalJobScheduler）。

1. **MA-01（rpcPoll 终结驱动）**：entry 在默认装配下四终结条件（future 终态 / deadline / cancelToken / whenComplete）中三个失效——pollOnce 错误分支（:203-206）warn-and-return 无失败预算、`computeDeadline`（:268-274）只读 schedule.timeoutSeconds 不纳入全局超时、DB 终态（TimeoutChecker 已置 TIMEOUT）无回传通道、cancelToken 内建装配恒 null（C4-010）。唯一活性来源 future 在远程失联时永不完成 → entries 注册表 + 周期句柄 + 每 5s 失败 RPC + WARN 洪水永久累积（与故障频度成正比，长时运行必然劣化）。
2. **C2-001（锁范围）**：`executeJob` 在 per-job 监视器内调 `job.invoker.invokeAsync(ctx)`（:322）；平台唯一内置 invoker `BeanMethodJobInvoker`（:113-127）把非 CompletionStage 返回值同步执行完毕再包 `completedFuture` → 本地模式业务方法体全程持锁——管理 API / 停机（`@PreDestroy`→deactivate）无限期阻塞（确定性可达，阻塞上界=最长 job 体时长），持应用级锁 L 的业务方法 × 持 L 调管理 API 的线程构成可构造 ABBA 永久死锁。

本文档一次裁定两项 + 测试缝，使 Phase 2 实施只有一种合法形态。

## 三、核心设计契约

### 3.1 裁定①(a)：失败预算与退避（终结主通道）

- **默认分支唯一结论 = 连续失败超阈值 → resolve ERROR(ERR_JOB_REMOTE_INVOKE_FAILED)**。判据：entry 必须在有限时间内到达终态（消除无界驻留）+ 瞬态抖动不误杀（阈值容忍若干轮抖动）。降频退避仅为达阈值前的辅助形态；周期性 DB 回查不在此面（归 (c) 机制，不与 (a) 双头开放）。
- **配置键与缺省值一次定夺**：`nop.job.remote.poll-fail-threshold`，缺省 **5**（与 poll-interval-ms 缺省 5000 组合：按跳轮公式推演阈值前实际观察窗 ≈95s——5 个失败轮 + Σ2^(s-1)=2+4+8 个跳过轮共 19 轮 × 5s；窗口声明以推演值为准，不采「阈值 × 周期」线性近似）；`<=0` 装配期 fail-fast（`ERR_JOB_CONFIG_INVALID` + `ARG_CONFIG_KEY`，形态照抄 `setPollIntervalMs` :84-88）。零新键之外不引入退避窗口键——退避参数随跳轮公式内定（见下），不单独配置。
- **退避形态 = pollOnce 入口按连续失败数跳轮**：streak≥2 时跳过 `2^(streak-1)` 轮（封顶 12 轮 ≈60s@5s），不改 `scheduleWithFixedDelay` 句柄周期（零句柄重排，句柄 cancel 语义保持单一来源=future 终结）。乘子 2 与 60s 上限对齐绝缘层失败治理参数（periodic-task-insulation §3.1）。计满阈值的那一轮不跳轮、直接 resolve。
- **计数生命周期**：单轮 poll 成功（`applyStatus` 到达非错误分支，含 RUNNING/UNKNOWN 继续轮询）即清零并记恢复 DEBUG；计满阈值 resolve ERROR（显式终态，非静默清除，rule 24）前记 ERROR 日志 `nop.job.remote.poll-budget-exceeded:taskId=,streak=`。
- **交替失败残留面声明**：清零口径为「单轮 poll 成功即清零」，fail-success 交替模式下 streak 永不达阈值——该残留面由 (b) deadline 兜底与 (c) DB 回传兜底承接，不构成无界驻留（若三者同失守属多重独立故障，超出单缺陷修复包络）。与绝缘层契约的层级区分：绝缘设施（periodic-task-insulation §3.1）管**周期链存活**（失败不杀调度链），本裁定管 **coordinator 侧业务 future 的终结仲裁**——两层正交不互替，预算计数属 coordinator 业务层非设施层。
- **resolve 竞态安全**：终态结算统一走 `future.complete`（幂等，first-writer-wins），与 deadline/cancelToken/whenComplete 兜底（:123/:125-127）天然无双重终态；resolve 后条目由既有 whenComplete→cancelEntry 链（:123、:135-151）移出注册表，终结移除路径零新增。

### 3.2 裁定①(b)：deadline 全局超时兜底

- **取值优先序唯一结论**：`schedule.timeoutSeconds > 0` → 既有语义维持；否则全局 `nop.job.coordinator.execution-timeout-ms > 0` → `now + executionTimeoutMs`；两者均无 → deadline=0（无墙钟上限，维持现状）。**零新配置键**——manager 侧新增 `@InjectValue("@cfg:nop.job.coordinator.execution-timeout-ms|-1")` 读同一键，与 `JobTimeoutCheckerImpl`（:53/:121）取值同源：单一事实源，禁止 manager 侧发明第二超时键。
- **plan 340 钉不冲突声明**：`JobTimeoutCheckerImpl` :485-502 钉死契约 = 「两处均未配置时不回退 dispatchTimeout、不杀长任务，交 worker-liveness 链恢复」——本裁定的兜底位在 **manager 轮询侧**（仅当全局键 >0 时给 deadline 赋墙钟上限），未配置时 deadline=0 行为与 checker 侧完全同构（都不杀）；checker 若因全局键 >0 置 TIMEOUT 终态，则经 (c) 回传通道 resolve——两处读同一键、同一优先序，不存在双头超时裁决。钉 `testLongRunningTaskWithoutTimeoutNotKilled`（`TestJobTimeoutChecker` :614-639）零回退。
- **双侧时钟起点不同声明**：manager 侧兜底 deadline 锚 = register 时刻（computeDeadline 在 register :108 调用），checker 侧 TIMEOUT 锚 = task.startTime——两起点差一次派发延迟量级；resolve 竞态由 `future.complete` first-writer-wins 吸收（先到者定终态，两者终态语义等价均为墙钟超时 TIMEOUT），无危害面。

### 3.3 裁定①(c)：DB 终态 → future 回传联动

- **机制唯一结论 = checker 写终态经 manager resolve（候选 2）**：`JobTimeoutCheckerImpl` 置 task TIMEOUT 终态后，经 manager 新增 package-private 回传方法（`resolveFromAuthority(taskId, JobFireResult)` 形态语义：entries 查表 → 命中则 `future.complete`，未命中即 no-op 返回）回传 in-memory future。checker 与 manager 同模块同包（nop-job-coordinator engine），零跨模块 API、零新增配置、零轮询开销。被拒候选 = 退避后低频 DB 回查：违反 javadoc「管理器驱动轮询不查 DB」契约本体（:56-58），且引入每 entry 周期 DB 读（与 pollOnce 的 RPC 读重复、成本面更大）。
- **javadoc 契约修订文案成文（随裁定落，:56-58 替换）**：「状态权威仍在 DB（scanner writeback + TimeoutChecker 维护 task 状态机）；管理器驱动轮询只看本地持有的 in-memory 信号（cancelToken + deadline + promise 状态），不查 DB——DB 侧权威终态经 TimeoutChecker 写终态时的显式回传通知（resolveFromAuthority）送达 entry future，轮询循环自身零 DB 访问。」契约修订后「不查 DB」承诺对**轮询循环**仍精确成立，回传是写侧单点推送而非读侧轮询。
- **幂等与覆盖**：回传 resolve 与 (a) 预算 resolve、(b) deadline resolve、既有 applyStatus resolve 竞态由 `future.complete` first-writer-wins 吸收；回传 future 已终结时 entries 已摘除（:123 whenComplete），查表未命中即静默返回（合法 no-op：条目已终结，非静默吞错误）。回传查询失败（DB 侧异常）不阻断 checker 置终态主流程，ERROR 日志留痕（rule 24：显式降级路径，非空 catch）。
- **checker 侧接线落点成文**：`JobTimeoutCheckerImpl` 新增 `@Inject RpcPollTaskManager`——与 manager 同包（nop-job-coordinator engine），package-private resolveFromAuthority 可达；beans.xml 同文件既有两 bean 声明（manager :58 / checker :102），依赖方向 checker→manager 单向零环（manager 不反依 checker）已核。
- **覆盖边界声明**：回传仅覆盖同 JVM 已注册 entry；跨实例（entry 注册在远端节点的 rpcPoll manager）查表未命中即 no-op——该面由 (a) 失败预算与 (b) deadline 兜底在本地终结，不引入跨实例回传通道。
- **两 TIMEOUT 写点均接回传**：checker 的 `tryMarkTimeout` 与 `markSuspiciousAsTimeout`（worker-liveness SUSPICIOUS→TIMEOUT 链）两处置 TIMEOUT 终态的写点均调回传通道，不得只接其一（漏接任一即复刻「DB 已 TIMEOUT 而 future 悬挂」原缺陷形态）。

### 3.4 裁定①(d)：PollEntry 精简与失败计数并发形态

- **失败计数并发形态唯一结论 = `AtomicInteger`**：计数写点在 RPC `whenComplete` 回调线程（:202-207）与 pollOnce 轮询线程（跳轮判定/清零 :188-198、计满 resolve）两域——多写者读改写（increment/读后置零）需要 RMW 原子性，volatile 只保可见不保 RMW，不采。计满判定与 resolve 同轮在 pollOnce 线程内完成，无需 CAS 竞争窗口额外防护（双 entry 同 taskId 不存在——entries keyed by taskId，put 即去重）。
- **capture 冻结最小视图清单（唯一结论）**：`taskId`、`scheduleId`、`fireId`、`targetHost`、`cancelToken`、`deadline`、`future`（+ 运行态 `pollHandle`/`onCancelListener`/失败计数）。`NopJobSchedule`/`NopJobFire`/`NopJobTask` 三实体引用不驻留（C5-005：实体 JSON payload 对象图被 entry 钉住 = 驻留放大器）；`getJobStatus`/`cancelJob` 调用所需请求参数（header id、targetHost 等）由最小视图标量构造——`IRpcPollTaskClient` 三方法参数形态按需收窄（同模块内部接口，回归面 `TestDefaultRpcPollTaskClient`/`TestRpcJobInvoker` 零回退钉覆盖）。`DefaultJobCancelHandler`（:30-51）与 `RemoteJobInvoker` 路径不触 entry，零改动。
- **C5-005 驻留体积断言口径**：白名单字段断言——反射枚举 `PollEntry` 字段集，断言不含任何 `NopJob*` 实体类型字段且字段总数 ≤ 白名单清单；驻留体积以「字段引用集」为准，不做字节体积断言（脆弱）。

### 3.5 裁定①(e)：activeCount 升格 gauge 与失败计数可见

- **命名唯一结论**（按现有 metrics 惯例，先例 `nop.job.worker.invoke.inflight.age.max`）：gauge **`nop.job.remote.poll.active-count`**（entries.size()，manager 启动时经 `GlobalMeterRegistry` 注册，registry 未初始化静默跳过——绝缘层 §3.1 观测通道同形态）；counter **`nop.job.remote.poll.failures`**（tag `taskId`，错误分支每次 +1）。`activeCount()` 方法保持 package-private（可观测/测试入口），gauge 为生产可见通道。
- **失败计数可见** = counter + 计满阈值 ERROR 日志（3.1）双通道；阈值告警运维指引归 owner doc §五（plan Non-Blocking Follow-ups 同源，不阻塞 closure）。

### 3.6 裁定① 前置约束与同点协调

- **C4-010 前置约束**：「补 cancelToken」式修复无效（内建装配恒 null），不得以 token 通道为终结依赖——修复后终结活性来源 = (a) 失败预算 + (b) deadline 兜底 + (c) DB 回传 + future whenComplete 兜底四通道，cancelToken 通道维持现状（内建装配下为空路径兜底）。
- **C7-008 同点协调**：`computeDeadline` 重写维持 `CoreMetrics.currentTimeMillis()` 墙钟形态，不引入单调时钟——时钟源本体归 R4-07 不代做；本批注记「computeDeadline 已按裁定①(b) 重写、时钟源未动」供 R4-07 就绪注记引用。
- **C1-004 同点协调**：register（:105-128）触达时保序不回滚——不引入注册中途失败的注册表回滚逻辑（部分初始化 entry 由 isDone 双检 :125-127 与 whenComplete 兜底吸收）；C1-004 本体归 R4-06 不代做，就绪注记登记本批后 register 形态。
- **MA-05 站点① 零触碰**：pollOnce `catch (Exception)` :210-212 与 `catch (Throwable)` :212-215（plan 2496 已落）零 diff；`TestRpcPollTaskManagerInsulation` 2 测试零回退。
- **plan 2497 池契约零触碰**：`nopRpcPollScheduledExecutor` 容量语义/热更契约（owner doc §七）零改动；owner doc §七「pollOnce 为纯内存 deadline/cancel 检查，属豁免面」表述 :472-473 须随本批复核修订（Phase 3 义务，预算/回传落地后豁免表述如实化）。

### 3.7 裁定②：监视器内外部 invoker 外移（锁范围收窄）

- **唯一结论 = `executeJob` 拆两阶段方法**：`prepareJobLocked(job)`（锁内段：RUNNING 迁移、lastScheduledTime、ctx 构造、**占位 `done` 创建与 `job.running = done`（:331-335 形态维持）**；前置检查不通过时返回放弃信号）与 `startJobUnlocked(job, ctx, done)`（锁外段：identity/状态复检 → `invoker.invokeAsync(ctx)` → whenComplete 重取锁终态迁移，既有 :336-357 形态保持，含 MA-05 站点③ fail-safe :341-351 零回退）。**调用机制约束（评审 Blocker 裁定）**：Java monitor 可重入——调用方帧（timer/fireNow 的 `synchronized(job)`）持有监视器直至其 synchronized 块闭合，executeJob 内部拆锁不释放外层监视器，锁外移在 executeJob 内部机制上不可能发生；故拆分必须落在调用点——**两处调用点的 synchronized 块在两阶段之间闭合**（块内尾调 prepareJobLocked，出块后调 startJobUnlocked），原 executeJob 单方法持锁编排形态废弃。
- **两个调用点逐一处置表（唯一结论：两调用点均改两阶段调用，synchronized 块内保留各自前置检查）**：

| 调用点 | 现状（持锁调 executeJob） | 处置 |
|--------|---------------------------|------|
| timer 回调 `schedule` lambda :302-310 | `synchronized(job)` 内 :303-309（scheduledFire 清理 + active/WAITING 检查）尾调 executeJob | 块内前置检查与 scheduledFire 清理维持，尾调改 `prepareJobLocked(job)`（放弃即短路径返回 null）；出块后调 `startJobUnlocked(job, ctx, done)` |
| fireNow :215-222 | `synchronized(job)` 内（running 非空/COMPLETED/FAILED 拒绝 + cancelScheduledFire）尾调 executeJob | 同形态：块内拒绝检查（必须持锁，与并发 fireNow/update 互斥）+ cancelScheduledFire 维持，尾调 `prepareJobLocked`；出块后 `startJobUnlocked`；prepare 放弃时返回值语义不变（仍 true），用户可见反馈升 WARN |

- **两阶段行为契约（非实现伪代码）**：prepareJobLocked 放弃即短路径返回，调用方跳过 startJobUnlocked；startJobUnlocked 三步——identity/状态复检 → invokeAsync → 回调段 whenComplete 重取锁终态迁移 + fail-safe（:336-357 形态原样保持）。invokeAsync 同步抛异常的既有 catch（:323-326）保留在 startJobUnlocked 锁外段（handleResult 调用移入回调重取锁形态）。
- **锁外 invokeAsync 抛同步异常的终态保证**：同步异常不逃逸锁外段——按既有语义经 handleResult（重取锁）落 FAILED 终态 + done 完结，job 不滞留 RUNNING（rule 24）。
- **出锁段 identity/状态复检与放弃分支（2509 §3.6 范式投影，唯一放弃判定）**：startJobUnlocked 入口对 `job` 复检（仍在 `jobs` 注册表 + `internal` 仍为 RUNNING + 未被 suspend/cancel/remove/deactivate 交错）——复检失败走显式放弃分支：**重新取锁**下 `job.running = null` + 占位 `done.complete(null)` + 状态按交错方语义终态化（COMPLETED 或维持 SUSPENDED 迁移面），**禁走 handleResult**（null result 会经 scheduleNext :392 把 SUSPENDED 改写回 WAITING，静默撤销 suspendJob 效果）；放弃留痕升 WARN（fireNow 路径为用户可见反馈，返回值语义不变），**禁止静默丢弃**（rule 24；running 悬空会使 awaitIdle :442-458 永不空闲、fireNow 恒拒绝）。
- **ctx 真快照唯一结论**：`SimpleExecutionContext`（:572-623）持 `job.spec`/`job.state` 活引用非快照——构造时复制 ctx 消费字段为真快照（唯一结论，不采「活引用 + 逐字段安全性论证」残留共享形态）；invokeAsync 消费快照零共享可变态，交错方 update（:500-507）改写 spec/trigger 不影响在途调用。
- **C2-007 共设计接缝（占位先行关系保序）**：占位 `job.running = done` 在 prepareJobLocked（锁内）置位，保持「先于 whenComplete 注册、先于 invokeAsync」的先行关系（防已完成 future 同步回调覆盖占位，:331-335 注释防护零回退）；本批不把占位前移到触发判定之前（C2-007 本体，R4-06 不代做）——重构后 executeJob 两阶段形态写入 R4-06 就绪注记供其续接。
- **awaitXxx 语义处置唯一结论 = 段零改动 + 残留边界显式声明**：`awaitState`/`awaitIdle`/`awaitFireCount`（:418-479）的 `synchronized(job)` 段为瞬时状态检查（非长持锁），timeout 参数约束的是轮询循环总时长而非单次监视器获取——缺陷态下获取等待被持锁 job 体无限拉长，修复后监视器持有段只剩 prepareJobLocked 状态迁移（微秒级），获取等待自然有界。零代码改动，残留边界成文：监视器获取上界 = 锁内段耗时 + 锁外段零持锁，恒有界。
- **修法边界**：不改 `BeanMethodJobInvoker` 同步执行语义本身（:113-127 零 diff，`TestBeanMethodJobInvoker` 10 测试零回退）；「invoker 必须真异步」SPI 契约文档化落 owner doc §三.1/§一.4（Phase 3 统一落账）。锁外移后阻塞型 invoker 仍占调度线程（C4-002 面）——owner doc 契约句不得提前声明 C4-002 已修。
- **管理 API 响应性**：`getJobState`（:151-158）/`getJobDetail`（:64-67）/`suspendJob`（:178-192）/`cancelJob`（:194-207）/`removeJob`（:135-149）/`deactivate`（:232-241）取同一监视器的无限期阻塞面随锁收窄结构性消除；`cancelRunning`（:403-406）既有空语义零改动。

### 3.8 裁定③：测试缝与零回退钉

| # | 测试缝（载体 `TestRpcPollTaskManagerEntryTermination` / `TestLocalJobSchedulerManagementResponsiveness`） | 覆盖裁定 | 断言焦点（红→绿口径） |
|---|---|---|---|
| 1 | getJobStatus 恒 failedFuture + 无 timeoutSeconds（复用 `TestRemoteJobInvoker` mock 体系） | ①(a) | 缺陷态 `activeCount()>0` 永不终结红（deadline 轮询观察窗）→ 修复后阈值轮内 resolve ERROR + `activeCount()==0` 绿 |
| 2 | 不配 timeoutSeconds、注入全局 executionTimeoutMs 变体 | ①(b) | 墙钟上限内 resolve TIMEOUT 绿；两键均未配置时 deadline=0 不杀（plan 340 同构对照断言） |
| 3 | checker 置 TIMEOUT → resolveFromAuthority 变体 | ①(c) | future 完成 ERR_JOB_TIMEOUT + activeCount 归零绿；未命中 no-op 分支断言 |
| 4 | PollEntry 字段白名单反射断言 | ①(d) | 驻留体积断言（§3.4 口径）绿 |
| 5 | latch 阻塞型同步 invoker × `getJobState`/`deactivate` ≤500ms 响应性 + job 体自然完成不崩 + `awaitIdle` 执行期上界变体 | ② | 缺陷态响应性断言恒超时红 → 修复后绿；出锁放弃分支（remove/suspend 交错）显式终态断言 |

- **零回退钉（全列，钉零回改逐条 git diff 为空核对）**：`TestRemoteJobInvoker`（13 测试，含 :254 cancelToken 监听器摘除泄漏守卫）、`TestRpcPollTaskManagerInsulation`（2 测试）、`TestJobCoordinatorScanner`（~25 测试，含 rpcPoll E2E :1347/:1390）、`TestDefaultRpcPollTaskClient`、`TestRpcJobInvoker`、`TestLocalJobScheduler`（15 用例，补测外其余；:298/:348/:398 supplyAsync 三 running 态为 C10-003 缺口证据）、`TestBeanMethodJobInvoker`（10 测试）、`TestLocalSchedulerResultContract`、`TestJobTimeoutChecker`（plan 340 钉 :614-639）、`TestJobWorkerScanner`（18 测试，本批零触碰零回退）。
- **补测类声明**：C10-001/003 新增断言即新钉——「新增断言即新钉、缺陷态红→修复后绿」；每终结测试末尾统一 `activeCount()==0` 断言（终结断言零→升格为事实断言；唯一既有引用 = 2496 绝缘钉 :125）。
- **先红后绿义务（E2）**：红① = 缝 1 终结缺失红；红② = 缝 5 锁范围红；修复前红证据落 `_tmp/` 留痕；变异或回退论证可替代先红，须登记。上游环境抖动按 plan 2512 三联裁定（stash 基线同红/solo 绿/复跑全绿）处理留痕。
- **接线与无静默跳过义务（rule 23/24）**：失败预算/兜底/回传真实被 pollOnce→applyStatus/resolve 链消费断言（非仅字段存在）；锁外 invokeAsync 真实脱离监视器由响应性断言即接线证据；预算 resolve 显式终态、回传查询失败显式降级留痕、出锁放弃显式分支，零空方法体/TODO。

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|----------|
| ①(a) 备选：错误分支仅降频退避不设阈值终态 | entry 仍无有限时间终态保证——退避只延缓无界驻留，不消除；终态判据是根因面 |
| ①(a) 备选：改 scheduleWithFixedDelay 句柄周期实现退避 | 句柄 cancel 语义唯一来源应保持 future 终结；句柄重排引入与 cancelEntry（:135-151）的句柄竞态面 |
| ①(b) 备选：新增 manager 专用兜底超时键 | 与 checker 的全局键形成双头超时裁决源；同源读键即闭合且零新键 |
| ①(b) 备选：两键均未配置时给固定缺省墙钟 | 直接违反 plan 340 钉死契约（未配置不杀、交 worker-liveness 链恢复） |
| ①(c) 候选：退避后低频 DB 回查 | 违反 javadoc「轮询不查 DB」契约本体；每 entry 周期 DB 读成本面大于写侧单点回传 |
| ①(d) 备选：失败计数 volatile | 两线程域多写者 RMW（increment/置零），volatile 只保可见不保原子，streak 判定可丢计数 |
| ①(d) 备选：三实体引用驻留 + 软引用 | 软引用回收不可预期 = 终结条件随机失效；标量最小视图根治驻留放大器 |
| ② 备选：约束 invoker 必须 CompletionStage（改 BeanMethodJobInvoker） | 改 SPI 契约本体、扩回归面至全部 invoker 实现与 YAML 用户面；锁收窄在本侧根治且 invoker 语义零变 |
| ② 备选：管理 API 改 tryLock 带超时 | 治标——job 体仍持锁，超时只是把无限期阻塞换成失败率；锁收窄结构性消除持锁面 |
| ② 备选：把占位前移至触发判定前（顺带修 C2-007） | C2-007 本体归 R4-06 不代做；本批只保先行关系接缝，越界修将吞并 R4-06 验收面 |

## 五、与已有设计的关系

- [stream-buffer-unification-and-failover-flow-concurrency.md](stream-buffer-unification-and-failover-flow-concurrency.md) §3.6：R03 出锁投递范式（锁内摘取/置位、出锁执行、出锁段 identity 复检）——裁定②直接引用不重开；chain28 两面切分的消费面（5.5 C4-R2-001）归 R3-05 零触碰。
- [periodic-task-insulation.md](periodic-task-insulation.md)：MA-05 站点①③ 已闭合零触碰；§3.1 失败治理参数（乘子 2/上限 60s/日志抑制）为裁定①(a) 退避形态对齐模板；§3.6 MA-06 看门狗「永不终结 future」与本裁定①终结驱动是双通道不合并（worker 观测 vs coordinator 终结仲裁，owner doc §六「不存在双重终结」声明维持）。
- [ledger-retention-policy.md](ledger-retention-policy.md)：R01 族级 SSOT——本批判定 R01 不适用（失效本体=在途条目终结驱动缺失，非保留策略缺失；修复不引入任何保留期/TTL 参数），零成员传导行；1.4 单元真正的 R01 族成员 C7-004 归 R4-07 按 §3.6 同构行对齐（就绪注记带指向）。
- plan 340 / `JobTimeoutCheckerImpl` :485-502 钉死契约：裁定①(b) 兜底位在 manager 轮询侧、同键同优先序，钉零回退。
- 下游：R4-06（8.46，硬依赖本批）——裁定② 的 executeJob 两阶段重构形态（prepareJobLocked/startJobUnlocked）与 C2-007 接缝、裁定① 的 C1-004 register 边界为其共设计引用面；R4-07（8.47）——C7-008 时钟源结论注记与 C7-004→R01 §3.6 指向。就绪注记归 plan 2513 Phase 4。
- owner doc 落点（Phase 3 统一落账，本 Phase 零 owner doc 变更）：`docs-for-ai/03-modules/nop-job.md` §七 pollOnce 豁免表述复核修订、§一.4/§三.1 契约句、§五 配置键登记（`nop.job.remote.poll-fail-threshold`）、源码锚点补 RpcPollTaskManager 行 + `docs-for-ai/04-reference/source-anchors.md`（按实际）。
