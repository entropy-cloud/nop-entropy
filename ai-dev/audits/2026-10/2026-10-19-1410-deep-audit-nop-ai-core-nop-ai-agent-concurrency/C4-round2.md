# C4 深挖第 2 轮 — nop-ai-core-nop-ai-agent（WI 3.1）

## 深挖范围与方法

- **深挖面（任务指派三项，逐一处置）**：① 未覆盖的自建线程站点（基线 C4a/C4b 全量清单逐站点复核是否漏评）；② middleware/hook 的线程切换；③ skill/recipe 装配的并行初始化。
- **方法**：(a) 基线复核——`_tmp/cra-baseline-nop-ai-core-nop-ai-agent.txt` C4a（thread creation and pools，:1767 起）/ C4b（schedulers/timers，:1860 起）两节逐行对照第 1 轮 C4 立案与清单表，确认每条基线命中均有归属；(b) 独立普查——以 `new Thread|Executors.|ForkJoinPool|commonPool|parallelStream|.schedule(|scheduleWith|scheduleAt|SubmissionPublisher|GlobalExecutors|new Timer` 全模块正则重扫 nop-ai-core + nop-ai-agent main 源码，得到 21 个命中文件清单后逐文件比对第 1 轮覆盖归属；(c) 定点读审——`middleware/MiddlewareChain.java`（全文 63 行）、`hook/DefaultHookRegistry`（第 1 轮批次 E 清单 E1 判定复读）、`skill/FileSystemSkillProvider`（DCL 复读）、`skill/SkillResolver`、`recipe/RecipeResolver`（第 1 轮清单 E14 判定复读）、`engine/AgentExecutorResolver`（装配链）。
- **盲审纪律**：仅读第 1 轮五文件 + 本文件；未读其他 audits/plans/bugs/lessons/backlog；未查 git。

## 新增发现

本轮无新增 C4 立案。复核证据如下。

### 基线 C4a/C4b 逐站点归属复核（任务深挖面①）

基线两节在 nop-ai-core 侧仅命中 `EmbeddingServiceImpl`（第 1 轮 C4-003/C4-004/C4-005 + CL-044/CL-059）；nop-ai-agent 侧基线命中 13 文件，逐站点归属如下，**无漏评站点**：

| 基线命中站点 | 第 1 轮归属 |
|---|---|
| `DockerSandboxBackend:254` / `NoOpSandboxBackend:106` reader Thread | C4 清单表 X2（reader 线程接受，边界已注释）+ C1-007（进程 destroy 缺口）+ C7-020（success 路径线程泄漏）+ CL-033/CL-034 |
| `InMemoryActorRuntime:133,301-307,372` per-actor executor | C4 清单表 X2（create/destroy 对称，接受）+ CL-035（Error 逃逸僵尸）+ CL-046（FAILED 条目滞留） |
| `ScheduledRecoveryManager:199,361` 注入 IScheduledExecutor | C4-007（Error 逃逸面 ②）+ 清单表（注入式，对称） |
| `DBMessageService:116,208-218` poller | C4-013（Error 逃逸）+ C4 清单表 X3 + CL-040（单线程互饿） |
| `TaskDispatchCoordinator:50,120,134` daemon spawn 池 | C4-015（shutdownNow 丢队列任务）+ CL-036（惰性建池 check-then-act） |
| `TeamTaskFlowOrchestrator:215,475,492` owned spawn 池 | C4-010（per-invocation 泄漏）+ C4-015（cancel 不可达）+ CL-036 |
| `DefaultAgentEngine:114,130,469-481,641-648` agent/background executor | C4-006（同池嵌套）+ C4 清单表 X2 + CL-064（无界池兜底超时任务） |
| `SessionLockRenewal:24,33,44,61-62` lockRenewExecutor | C4-007/C4-008（Error 逃逸 + 单线程队头阻塞）+ C7-019 |
| `SingleTurnExecutor:42,59` commonPool 缺省 | C4-009 |
| `TeamTaskSchedulerDaemon:237` scheduleWithFixedDelay | C4-013（Error 逃逸 ③）+ CL-059 同型生命周期面 |
| `EmbeddingServiceImpl:88,122-142` 重试调度器 | C4-003/C4-004/C4-005 |

### 独立正则普查收口（21 命中文件全覆盖）

本轮全模块重扫命中 21 个文件：上表 13 文件之外新增命中 8 个，逐一核对均为**非站点**——`TeamTaskFlowOrchestrator.java` 其余命中为 javadoc 文本（:42/:192/:406 警示注释，其治理建议本身已由 C4-006/CL-025 承接）、`SpawnMemberAgentTaskStep.java`/`SpawnMemberFanOutStep.java`/`MemberFanOutDispatcher.java`（spawn 池的**纯使用方**——supplyAsync 提交到注入 executor，无线程设施创建，C4-010 失效面 ⑤ 已判）、`ThreadLocalTenantResolver.java`（javadoc 引用 supplyAsync 边界，非线程创建）、`DefaultWaitCoordinator.java`（:89-95 `scheduler.schedule` 一次性延迟任务，句柄丢弃面已由 CL-066 登记）、`ReActAgentExecutorBuilder.java:588`/`AgentSessionLifecycle.java:113`（javadoc 文本）、`ChatServiceImpl.java`（SubmissionPublisher，C4-001/C4-002）。nop-ai-agent 其余 27 包 + 根与 nop-ai-core 其余包零命中，与第 1 轮 X3 收官清单声明一致。

### middleware/hook 的线程切换（任务深挖面②）

`MiddlewareChain.java:55-61`：proceed 以 `new MiddlewareChain(list, index+1, core)` 递进——纯调用线程同步 onion 链，**无任何线程切换/异步提交**；`AgentHookInvoker.executeWithMiddleware:61-82`/`executeExecutionMiddleware:113-141`：hook 与 middleware 全部在调用方线程（ReAct worker / 工具派发线程）内联执行，无 executor 参与；`DefaultHookRegistry` 为 per-execute 栈封闭（第 1 轮批次 E 清单 E1 通过判定维持）。结论：**不存在线程切换面**，C4 无对象（执行级中间件上下文的契约缺口为功能域，已登记于 `C1-round2.md` CL-075，非线程面）。

### skill/recipe 装配的并行初始化（任务深挖面③）

`FileSystemSkillProvider.java:54,66-77`：`cached` volatile + `synchronized(this)` DCL 惰性装载，装配后只读（第 1 轮清单 E2 通过）；`SkillResolver.loadRegistry:110-122`：per-resolve 新建 LinkedHashMap（栈封闭）；`RecipeResolver`：静态工具、clone-before-mutate（第 1 轮清单 E14 通过）；`AgentExecutorResolver:151` 的 resolve 链在执行装配段内单线程完成。结论：**无并行初始化站点**（装配链为调用线程串行），C4 无对象。

## 维度集外线索（overlay）

本轮无新增集外线索（CL 流水无追加；CL-075/CL-076 已登记于 `C1-round2.md`）。

## 继续判定

**终止信号**。判定依据：新增立案 0 条；三项深挖面全部复核收口——基线 C4a/C4b 全量清单逐站点归属无漏评、独立正则普查 21 文件全部有第 1 轮归属或证明为非站点、middleware/hook 无线程切换、skill/recipe 装配无并行初始化。第 1 轮 16 条（C4-001…C4-016）+ 12 条 CL（CL-029…CL-040）+ 本轮普查共同构成 C4 维度收敛证据：nop-ai-core-nop-ai-agent 双工程的线程设施站点集合封闭（两轮普查结果一致）。

## 维度复核结论（WI 3.1 Phase 4 独立复核，简）

**零新增维持，终止信号成立**。复核要点：① 抽验本轮非站点判定——`DefaultWaitCoordinator`（reliability 包）一次性 `scheduler.schedule` 句柄丢弃确属非站点且 CL-066 已登记于 C7 面、`MiddlewareChain` 实测 62 行（本文件记 63，非实质误差）纯同步 onion 链无线程切换、`AgentDispatchExecutor` 相关命中确由 C4-011/CL-025 承接；② 基线 13 文件归属表与第 1 轮清单逐行比对一致，未发现漏评站点；③ 包数口径（27 包 + 根）与目录实况一致。第 1 轮 16 条经独立回码复核全部保留（P1×4 / P2×12，无降级无驳回），详见 `C4-thread-pools.md` 「维度复核结论」节：含同站并单 3 组、严重度分叉 3 组（其中 C4-007↔C3-020、C4-016↔C3-024/C7-026 两组为本复核补登记）、跨单元同族对齐 6 项与 globalTimer 修复建议警示。
