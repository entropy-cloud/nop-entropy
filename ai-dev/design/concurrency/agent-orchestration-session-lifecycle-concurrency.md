# agent 编排/会话生命周期与方言流式协议并发契约（3.1 agent 批 10 项裁定）

> Status: active
> Source: WI 3.1 审计目录 `ai-dev/audits/2026-10/2026-10-19-1410-deep-audit-nop-ai-core-nop-ai-agent-concurrency/`（C1-shared-mutable-state[C1-008]、C5-memory-bounds[C5-008/015]、C4-thread-pools[C4-010/015]、C7-long-running[C7-012/022]、C3-async-chains[C3-011/021/028] + merge-adjudication §一/§五 + C10-test-protection）；批次成员表、站点行号基线、PA 判定与勘误登记以执行计划 `ai-dev/plans/2519-cra-phase8.25-r2-16-agent.md` 成员表为准（本文档为最终状态文档，勘误不落此处；行号 = live @ `3cf64bc3d7` 2026-10-31 亲读核实值，漂移以 live 为准）
> 实现锚：nop-ai-agent `engine/DefaultAgentEngine`（runningInjectionTargets CHM :127 / injectInput get :617 / doExecute put :774 / 终局 finally remove :1010 / cancelBackgroundViaTracker :695）、`engine/AgentSessionLifecycle`（resume :245 / wake :450 / restore :605 三恢复路径零注册——live grep `runningInjectionTargets` 零命中）、`session/AgentSession`（pendingInjections 裸 ArrayList :77 / addPendingInjection :83-89 / clear :92）、`session/InMemorySessionStore`（sessions :21 / remove :34-35 已实现未接线）、`session/DBSessionStore`（sessions :79 / remove :198-203）、`session/FileBackedSessionStore`（sessions :106 / remove :177-182）、`tool/TeamExecuteFlowExecutor`（per-invocation `new TeamTaskFlowOrchestrator` :234 后丢弃，close() 零调用）、`team/flow/TeamTaskFlowOrchestrator`（close :448-459 shutdownNow :450 + awaitTermination(2s) :452 / resolveSpawnExecutor check-then-act :468-478 / overall deadline orTimeout :634）、`team/flow/MemberFanOutDispatcher`（executeBoundMember orTimeout :322 + cancelSession(forced=true) :330——fan-out 路径已实现取消）、`team/DefaultMemberSpawner`（bounded get :188 / TimeoutException 分支 :189-199 仅 SPAWN_FAILED 返回、spawnedSessionId 未取消）、`team/scheduler/TaskDispatchCoordinator`（shutdownOwnSpawnExecutor shutdownNow :104 无 awaitTermination / inFlightDispatches :46）、`engine/AgentToolDispatcher`（slice 等待循环 f.cancel(true) :210/:268 / cancelCall :328-336 仅 future.cancel(true)）、`engine/LlmCallCoordinator`（callChatWithTimeout :785 / f.cancel(true) :806）、`engine/ReActAgentExecutor`（kill 通道 heartbeat abandon :1677）、`engine/SessionLockRenewal`（renewOnceSafe catch Throwable→WARN :74-85 / handleLeaseLost :86）、`runtime/lock/DbSessionTakeoverLock`（tryRenew :286-317 SQLException→NopAiAgentException）、`engine/DefaultAgentEngineConfig`（lockLeaseMs :155 / lockRenewIntervalMs :156）、`reliability/FileBackedCheckpointManager`（byWatermark :102 put :159 remove 仅随 session 清理 :211 / snapshotCache :103 / loadedSessions :105 / javadoc「no rotation/cap」:80-81）、`compact/InMemorySpillStore`（store HashMap :27）、`compact/InSessionCompactionArchive`（store HashMap :36）、`message/DBMessageService`（单线程 poller :208-213 / consumers CHM :114 / sweep 同 poller :217）；nop-ai-core `io/nop/ai/core/dialect/`（ILlmDialect.parseStreamChunk 单 chunk 签名 :138 / OpenAiDialect tool_call 单 entry return :345、buildBody 未启用 include_usage、parseStreamChunk :248-305 无 usage / OllamaDialect content!=null 空串抢占 :327 + get(0) 截断 :341 / GeminiDialect 首 part return :496/:512 / AnthropicDialect error→"[ERROR]" text DELTA :646-661 + message_start usage :538-546 / ResponsesDialect 免疫对照）
> Related: [bindcancel-context-unification.md](bindcancel-context-unification.md)（R1-04 取消原语——裁定⑤⑦消费面，禁新造并行机制）；[pool-facility-hardening.md](pool-facility-hardening.md)（globalTimer 容量语义——裁定③⑤ MA-16 约束继承面）；[registry-lifecycle-contract.md](registry-lifecycle-contract.md) 裁定⑥（REGISTRY 契约域——裁定⑨对齐注记零触碰）；[wf-task-scanner-fencing-and-task-wf-concurrency.md](wf-task-scanner-fencing-and-task-wf-concurrency.md) 裁定⑥（条件 UPDATE/租约形态菜单——裁定⑨ claim 世代 fencing 引用不重建）；[orm-session-txn-atomicity-and-model-cache-generation.md](orm-session-txn-atomicity-and-model-cache-generation.md)（2517 先例：终态清理接线/容器 CHM 化范式邻接）；plan 2505 R03 盘写异步化范式（裁定⑥ journal 异步化引用不重开）；plan 2496 MA-07 平台绝缘层（裁定⑩ Throwable 护栏零回退基线）

## 一、裁定总表

| 裁定 | 成员 | 唯一结论 |
|------|------|---------|
| ① | 3.1-C1-008（P1，MA-02 主；←C3-014；C10-005） | 三恢复路径（resume/wake/restore）执行窗对称注册/注销（与 doExecute :774/:1010 同型 finally 对，预设）；注入链复合原子化 = per-session 注入锁（get→enqueue 同锁原子）；pendingInjections 载体 synchronizedList + MAX_PENDING_INJECTIONS 溢出判定入锁；终态摘除序 = terminal 标记 + drain 同锁先行、persist/remove 锁外、标记后注入锁内显式拒绝（B1 选形 (b) 精确化，§3.1；C3-014/CL-061 丢写窗闭环） |
| ② | 3.1-C5-015（P1，MA-03 族首；F4 族 29 站点；C10-003） | 终态清理清单扩容（sessionStore 三实现 remove 接线 + F4 承载面逐容器登记）+ gate3 反向钉死改写同批（清单扩容 + 参数化终态 isEmpty 断言 + §5.3 改写语义对照留痕——全批唯一允许的既有钉改写）；终态语义矩阵 = 内存即时释放/持久层终态保留/restore 补偿加载；终态 save 反向 pin 随改写消解；CL-IND-01 对齐注记 + MA-10 接缝登记不代做 |
| ③ | 3.1-C4-010（P1，MA-04 主；←C3-022+C7-032；CL-036；C10-007） | spawn executor 生命周期上提至 TeamExecuteFlowExecutor 单例持有（经 orchestrator 既有 setSpawnStepExecutor 注入缝，:210-211 注入面零改动），与 TEE 生命周期对称（预设）；per-invocation try/finally close 备选拒绝；CL-036 惰性建池 synchronized double-check 随上提同 diff（上提后单点建池）；C3-022 异步终局不对称 + C7-032 多处建池收敛为 TEE + daemon 两处随池上提闭环 |
| ④ | 3.1-C4-015（P2 搭车 G51；C10-007） | 有界 drain：shutdown()（非 Now）→ awaitTermination 有界（2s 沿 live 既有预算）→ 超时 shutdownNow → 残余任务显式 fail/settle（dispatch future completeExceptionally，禁静默丢弃——rule 24）；TaskDispatchCoordinator.shutdownOwnSpawnExecutor :102-108 对称补 awaitTermination；`TestFanOutFutureLifecycle` 既有钉零回退；与裁定③同变更集（MA-04×C4-015 绑定） |
| ⑤ | 3.1-C7-022（P1，MA-05 主；←C7-023/C3-025/C4-014；C10-007） | DefaultMemberSpawner TimeoutException 分支（:189-199）补 `agentEngine.cancelSession(spawnedSessionId, reason, forced=true)`（预设）+ overall deadline 汇聚取消 = 既有 taskRt.cancel 图取消（live :655 AR-3 在案，本批零回改）之上由 orchestrator per-flow 成员会话登记表补会话级 cancelSession（登记表为数据面非取消机制）；取消一律消费 R1-04 bindCancel/ICancelToken 通道禁新造；MemberFanOutDispatcher :322/:330 既有取消面零回改；勘误 5 残留核对结论 = plan 252–259 零覆盖、红⑤全口径保留（§3.5） |
| ⑥ | 3.1-C5-008（P1，MA-06 主；←C5-010/011+C7-031 拆分面+CL-006/063/026/070/077；C10-017） | journal 双 cap 参数化轮转（条数 + 尺寸；活跃段超 cap 环形截断最旧 + WARN，归档不受 cap 约束——2505 异步化范式引用）+ 压缩完成会话淘汰（增量面；终态五容器清理为 FileBackedCheckpointManager.remove :199-215 既有实现，裁定②只接线）+ 恢复分批解析（parseAll 拆 per-session batch 消解 4× 物化峰值）+ 恢复峰值物化观测器（held-reference 计数 + 解析持有行数上界断言，m4 成文 §3.6）+ spill/归档容器 CHM 化 + 归档 put 入容错 try（CL-077）+ ioLock javadoc 修订（CL-006/063） |
| ⑦ | 3.1-C3-011（P1，MA-08 主；←C3-004/007/012+C4-005+C7-024；CL-005；C10-006） | 超时/取消路径接入 `IToolProgressTracker.cancelExecution`（复用 heartbeat 同一 kill 通道，预设——判据：tracker 已装配则机械超时与 cancelCall 必经 kill）；HTTP/LLM 吸收面消费 R1-04 bindCancel 请求级取消；cancelCall javadoc 契约句与实现对齐修订；条件申报①检查条款成文（预设零签名变更不触发） |
| ⑧ | 3.1-C3-028（P1，MA-09 主；←C3-030/031/032 族单修复批；C10-002） | AbstractLlmDialect 包内多 chunk 通道 `parseStreamChunks` 返回 List（预设，ILlmDialect :138 单 chunk 签名零变更）；Ollama 阶梯判定修正（空串不再抢占——C3-028×C3-031 两案一次消解）+ 多 tool_call 全枚举（get(0) 废除）；usage 四方言接入矩阵（§3.9）；带内错误通道类型化（error chunk 类型，禁 text 污染）；m3 机械验收式成文（红⑧前两断言同 diff 落地且缺陷态红，§3.9）；Anthropic/Responses 免疫复核结论 = 非免疫（Anthropic 带内错误污染 + usage 残缺，Responses 免疫面收窄登记） |
| ⑨ | 3.1-C3-021（P1，MA-11 主；←C7-025+C1-019；CL-040/024/069；C10-009） | 治理四轴：投递与 poll 分离（独立有界投递 executor + 显式拒绝策略）+ 毒消息处置（重投上界 + DLQ 死信落账 + guarded 失败回调对齐 4.1-C8-002 契约[attempt=0]）+ consumers 注册治理（顶替显式拒绝/身份校验/cancel 对称）+ claim 世代 fencing（引用 2514 裁定⑥菜单不重建）；R1-08 consumeAwaitTimeout 对齐注记落 cra-index :247/:268 双侧 |
| ⑩ | 3.1-C7-012（P1，MA-19 主；C10-010 半边） | 连续 N 次续期异常（缺省 3，setter 配置化零 @cfg: 键）或不可分类异常（DB 不可达判据）升级 `handleLeaseLost` 同型 abort（lease 未知即视为可能丢失——fail-safe，预设）；瞬时豁免窗 = 计数 + 上界显式；plan 2496 Throwable 护栏零回退（仅 false→handleLeaseLost 协议窄化不变）；C7-018 批外接缝登记不代做 |
| ⑪ | 测试缝与零回退钉 | 红①–红⑩驱动器（§5.1）+ gate3 改写语义对照表（§5.3，全批最重测试面义务）+ 零回退钉全列（§5.4）+ C10 八条成员面集落名（§5.6）+ 锁序总表（§5.7） |

## 二、背景与动机

R2-16 批 10 项（P1×9 + P2×1 搭车）覆盖 nop-ai 双工程六类缺陷面：**引擎注入目标登记窗口**（三恢复路径零注册使注入静默丢失、暂存分支裸 ArrayList 并发变异 CME、摘除与终态保存无复合原子性）、**F4 引擎终态清理 29 站点**（session store 三实现 put-only、历史会话堆内永驻、gate3 反向钉死清单不含 sessionStore）、**spawn 编排池生命周期**（per-invocation orchestrator 全仓无 close 每次泄漏 max(2,NCPU) 固定池 + close shutdownNow 丢 queued 工作使 dispatch future 永不 settle）、**成员/spawn 超时取消契约**（spawner 超时仅 SPAWN_FAILED 返回不取消 spawned 会话、overall deadline 图取消不达会话级[AR-3 taskRt.cancel 既有面之上的残余]——挂死成员僵尸化 + RECLAIM 双执行）、**长会话累积上界**（journal 磁盘无界 + byWatermark 全量常驻 + 恢复 ~4× 物化——防压缩 OOM 机制自身成为 OOM 源）、**超时取消传播断链**（机械超时/中断不向底层工具执行传播、kill 通道仅两处生产调用、幽灵 bash/容器/HTTP 持续运行）、**方言流式协议族**（缺省 stream=true 主路径确定性丢数据：并行工具调用截断、usage 四链失活、Ollama 空串抢占、带内错误缺失/污染）、**DBMessageService 治理**（单线程 poller 内联消费 call-agent 120s 全模块冻结、毒消息无 DLQ 停摆、consumers 顶替、claim 零 fencing）、**接管锁 abort 语义**（续期异常仅 WARN 无限容忍、非对称分区永不自 abort、fencing 失效双执行窗口）。本文档一次裁定 10 项 + 测试缝，使实施只有一种合法形态。

## 三、核心设计契约

### 3.1 裁定①：引擎注入目标登记窗口（C1-008）

- **三恢复路径对称注册（预设分支）**：resumeSession（:245）/ wakeSession（:450）/ restoreSession（:605）在进入执行窗时执行 `runningInjectionTargets.put(sessionId, ctx)`，在退出 finally 对称 `remove`——与 doExecute 既有 :774/:1010 同型 put/finally 对。备选（injectInput 侧查询恢复态判断可注入）被拒：把「窗口判定」从注册表单点搬散到注入侧判据（执行态枚举×恢复中标记），注入丢失面复刻为判据漂移面；注册表 = 唯一 SSOT（「注册在窗 = 注入可达」），三路径与 doExecute 共享同一登记语义，isInjectable 单点。
- **注入链复合原子化 = per-session 注入锁**：injectInput 的 `get`（:617）→ null 判定 → `addPendingInjection`（:621）三步对同一 sessionId 收敛入 per-session 锁（AgentSession 实例 monitor 或等价注入锁对象）；get→enqueue 与终态路径的撕裂窗由「terminal 标记与入队同锁互斥」闭合（终态摘除序见下条——remove 本身锁外，但标记先于 remove 置位，injectInput 锁内必先见标记或完成入队二者之一，无中间态）。
- **pendingInjections 载体同步化**：AgentSession :77 裸 ArrayList 改 `Collections.synchronizedList`；`addPendingInjection`（:83-89）的 `size() >= MAX_PENDING_INJECTIONS` 溢出判定与 `add` 收敛入同一 synchronized 块（synchronizedList 的复合操作不自动原子）；`clear`（:92）同锁。迭代面（消费注入的路径）一律在同步块内快照拷贝。
- **终态摘除序契约（C3-014/CL-061 吸收面闭环；B1 选形唯一形态成文）**：终态路径顺序写死为四步——(1) **锁内**置 AgentSession terminal 标记 + 执行 drain（收集 pendingInjections 残余至局部清单）→ 释放锁；(2) **锁外** persist（终态 save 含 drain 所得）；(3) **锁外**注册表 remove；(4) 标记置位后任何 injectInput 持锁即见 terminal → **锁内显式类型化拒绝**（WARN + 计数 `nop.ai.agent.inject-after-terminal`，非静默）。**形态选型 = 备选 (b)「remove 后锁内二段 re-drain + 显式拒绝」的精确化**：terminal 标记与 drain 同一临界区先行，使「drain 后 remove 前持锁入队」的注入必然落在标记之后、被 (4) 拒绝分支拦截——结构性不存在「既不被 drain 消费又晚于 persist 且不触发拒绝」的丢写窗。备选 (a)（drain→persist→remove 全程同锁 + 锁内禁 IO 豁免）被拒：persist 的 DB/盘写 IO 在 per-session 锁内执行将阻塞全部并发 injectInput 达 IO 时长，豁免判据（bounded 写）引入新的维护判定面且与 §5.7 锁序表「锁内禁 IO」纪律冲突；备选 (c)（injectInput 侧 CAS 复核）被拒：pendingInjections List 无自然 CAS 载体，引入版本号徒增复杂度而 (b) 的锁内标记-拒绝互斥已闭合同窗。**persist 失败序（R2 W1 成文）**：persist 失败不阻塞 remove，标记已拦截全部后续注入——禁实施者自行发明 persist 重试循环。**红①补定向交错测试**：构造「drain 执行后、remove 执行前」持锁 injectInput 的确定性交错（测试钩子或逐段注入），断言该注入被拒绝留痕且终态持久化不含该注入——缺陷态红（注入丢失且无拒绝），修复后绿。
- **既有钉兼容（勘误 11）**：`TestDefaultAgentEngineCancel` / `TestSessionLifecycleSaveBeforeRelease` / `TestDefaultAgentEngineConcurrencyGuard` 零改写——注册/摘除序不改变 cancel/save/release 外部可观测顺序；兼容性核对表随 Phase 2 落 `_tmp/` 留痕。

### 3.2 裁定②：F4 终态清理接线 + gate3 反向钉死改写（C5-015）

- **终态清理清单扩容（接线面）**：引擎终态路径（cancelled/failed/completed 收敛点，与既有 `runningExecutions.remove`、`getCheckpointManager().remove` 同一清理段）新增 sessionStore.remove 接线——三实现 remove 均已存在（InMemory :34-35 / DBSessionStore :198-203 / FileBackedSessionStore :177-182），本裁定为纯接线非方法新增；F4 29 站点承载面逐容器登记为终态清理清单行（checkpoint/账本/注册表/mailbox/backgroundRegistry 等按审计 C5-015 族清单），每行一个容器一个终态 isEmpty 断言承载。
- **终态语义矩阵（plan Risks 节义务）**：

| 载体 | 终态语义 | restore 补偿 |
|------|---------|-------------|
| InMemorySessionStore.sessions | 终态即时 remove（堆释放主目标） | 终态会话不可 restore（显式类型化拒绝） |
| DBSessionStore.sessions（内存缓存） | 缓存即时 remove；持久层行**终态标记保留**（非物理删除——审计/追责面） | restore 按持久层终态标记走补偿加载（内存重建，终态只读） |
| FileBackedSessionStore.sessions | 缓存即时 remove；磁盘文件终态标记保留 | 同 DB 侧补偿加载 |
| checkpoint/journal/byWatermark | 随裁定⑥清单行清理（会话级淘汰） | 恢复面不受影响（journal 为 checkpoint 历史非活会话） |

  `TestSessionLifecycleSaveBeforeRelease` 钉核对：save 先于 release 顺序零变化，钉零回退。
- **终态 save 反向 pin 子面**：save 必须先于 remove（先落盘后释放）——与裁定①终态摘除序契约同一顺序不变式，红②断言承载（终态后 store 空 **且** 持久层终态行在）。
- **gate3 反向钉死改写方案（唯一允许的既有钉改写）**：`TestInvariantGate3EntryPointCleanup` :87（字面钉 `"default void remove"` marker）+ :130-138（四组九串清理清单不含 sessionStore/memory/mailbox/backgroundRegistry）改写为——(a) 清单扩容：新增 sessionStore 接线 family 行（`sessions.remove` 三实现文件）+ F4 承载容器 family 行；(b) 断言补全：新增参数化终态测试（遍历引擎装配容器清单、终态执行后断言全部容器 isEmpty——C10-003 建议口径），改写语义对照见 §5.3（同 diff 留痕）。
- **终态 remove 非 restore 查询消费方行为变化登记（M3 硬条款）**：终态后内存即时 remove 使「终态会话仍可经 sessionStore.get/getAll 命中」的既有行为变化——**Phase 2 组 A 实施前置硬条款：先盘点 sessionStore.get/getAll 全部调用点并逐一分类**（终态容忍[调用方自身终态判断先行]/补偿加载[DB/FileBacked 持久层回源]/行为变化[需终态语义适配]），分类结论落 owner doc（Phase 3「agent 编排与会话生命周期并发契约」节内登记调用点分类表）；未完成盘点不得实施接线。
- **终态清理并发幂等声明**：终态清理为 **remove 型幂等**（三实现 remove 对不存在 sessionId 均 no-op，live 亲读在案）；多终态路径（cancel/failed/completed 收敛点）并发触达同一会话时 remove 幂等承载正确性，可观测面以 `terminal-cleanup-miss` 指标**双入去重**（per-sessionId 终态标记位去重，同一会话仅首个终态清理失败计数——防并发双终态路径指标翻倍误报）。
- **FileBacked 终态「标记」机制指名（Minor⑥ 定夺）**：持久层标记**复用既有 `AgentExecStatus status` 字段**（AgentSession :28/:303-307——status 已随会话持久化，终态值 cancelled/failed/completed 即标记，零新增列/文件/格式）；FileBacked/DB 持久层行不物理删除，restore 补偿加载按 status 终态值走只读重建（§3.2 矩阵第二/三行落地载体）。若实施期发现持久化格式未含 status（亲读核实点），则登记偏差并采最小 marker 追加，不得静默改格式。
- **接缝登记（不代做）**：CL-IND-01（DB 自建表保留策略族）对齐注记随裁定②登记——ai_agent_message 等表 TTL 归独立线索工单，本批零触碰；MA-10（R4-21 会话存储并发装载面）只登记裁定②终态清理接缝。

### 3.3 裁定③：spawn 编排池上提（C4-010，与裁定④同变更集）

- **落点定夺 = executor 生命周期上提至 TeamExecuteFlowExecutor（预设）**：TEE 为单例/注入载体（IoC beans 域生命周期），持有共享 `ownedSpawnExecutor`（bounded fixed pool，池容量语义沿 live :470 `Math.max(2, availableProcessors)` 不变）；per-invocation 构造 orchestrator（:234）后经**既有注入缝** `setSpawnStepExecutor`（TeamTaskFlowOrchestrator :210-211——「injected executor 由注入方持有生命周期，close() 对 injected 面零操作」live javadoc 在案）传入，orchestrator 对 injected 面不再建池不再 close——池与 TEE 生命周期对称，每次 team-execute-flow 泄漏 max(2,NCPU) 线程的面结构性消除。备选（per-invocation try/finally close）被拒：泄漏窗口收窄为调用期但「短生命周期 orchestrator 忘 close」缺陷形态原样保留（live :443-446 javadoc 自认依赖 daemon 线程兜底），且每次执行建/拆池的线程抖动成本无收益。
- **CL-036 惰性建池同步**：上提后 TEE 单点建池（`resolveSpawnExecutor` 等价逻辑落 TEE 侧）synchronized double-check（对照 `DefaultAgentEngine.getAgentExecutor` synchronized 先例）；orchestrator 侧 :468-478 对 injected 面直返，惰性建池分支随上提消解。
- **收敛面声明**：C3-022（异步终局不对称——close 无 drain 使 spawn 分支终局不对称）由裁定④ drain 承载；C7-032（多处建池登记：orchestrator/TEE/coordinator 各持池逻辑）由「池归属单点 = TEE」收敛——**三处建池收敛为 TEE + daemon 两处**（TEE 侧共享池为 team-execute-flow 主载体；coordinator `shutdownOwnSpawnExecutor`（:102-108）保留为 daemon 停机面，裁定④对称补 drain；orchestrator owned 池分支随上提退化为 injected-only，不再自建池）。
- **MA-16 约束继承声明**：本裁定新增池占用面遵守 R1-03 收窄后形态——「阻塞/长任务体不得指向 globalTimer；满足 §3.1.1 豁免判据的轻量直连合法」；spawn 池为独立 bounded fixed pool，零 globalTimer 指向。

### 3.4 裁定④：spawn 池 drain 语义（C4-015，与裁定③同变更集）

- **预设 = 有界 drain 三段式**：停机序改 `shutdown()`（停止接新任务，queued 保留）→ `awaitTermination(2s)`（沿 live 既有预算 :452）→ 超时 `shutdownNow()` + **残余任务显式 fail/settle**——每个被丢弃的 queued spawn 任务对应 dispatch future `completeExceptionally`（类型化原因「spawn pool drained before execution」），`inFlightDispatches` 条目随 settle 移除——「queued 任务被丢弃、future 永不 settle → 任务滞留 CLAIMED」面闭合，rule 24 零静默丢弃。**queued 工作停机丢弃成文为预期语义**：停机后不执行 queued spawn 是设计内行为（非缺陷），其唯一合法承载 = 显式 fail/settle 留痕——丢弃本身合法，静默丢弃非法。备选（shutdown 后逐任务取消回调 settle）被拒：per-task 取消回调注册面与 executor 内部队列结构耦合，脆弱度高于统一 drain 点 settle。
- **daemon 侧对称面**：`TaskDispatchCoordinator.shutdownOwnSpawnExecutor`（:102-108）补同型 awaitTermination + 残余 settle（`TeamTaskSchedulerDaemon.stop` 调用点零改动）。
- **inFlightDispatches 不变式恢复声明（覆盖 queued/running 两态）**：javadoc 不变式「每个 in-flight dispatch future 必然落定」对 queued 与 running 两态均成立——running 态由 awaitTermination 等待其自然落定（有界 2s），queued 态由 drain 点显式 fail/settle 承载；两态 settle 路径均为单点（自然完成 / drain 点），红④断言（stop 后 queued 任务的 future 落定）验证 queued 面，running 面由 awaitTermination 预算内完成语义覆盖。
- **`TestFanOutFutureLifecycle` 既有钉零回退**：drain 面为停机路径新增，正常 fan-out 生命周期零变化。

### 3.5 裁定⑤：成员/spawn 超时取消契约（C7-022）

- **既有面登记（R1 复核修订，事实前提修正）**：TeamTaskFlowOrchestrator overall deadline 超时分支（live :646-667，TimeoutException unwrap 处）**已执行 `taskRt.cancel(ICancellable.CANCEL_REASON_TIMEOUT)`**（AR-3 注释明言防 member agent/spawn worker zombie——nop-task GraphTaskStep 调度器检查 cancel token 停止后继步）——「orTimeout 只断 DAG」的原始审计表述在图取消面已被 AR-3 部分修复；本裁定增量收窄为「**图取消之上补会话级 cancelSession**」——taskRt.cancel 停止图推进但 spawned 成员会话本身（已提交给 agentEngine 的执行）不因此终止，会话级僵尸面仍开放。
- **预设 = spawner TimeoutException 分支补 cancelSession**：DefaultMemberSpawner :189-199 分支在返回 SPAWN_FAILED 前执行 `agentEngine.cancelSession(spawnedSessionId, "spawn timeout", forced=true)`（spawnedSessionId :170 已在作用域，live 亲读未引用未取消）——吸收 C7-023 同型面；cancel 失败（会话已终态等）显式 WARN + 计数留痕（禁吞，rule 24）。
- **overall deadline 汇聚取消（会话级补齐）**：在既有 taskRt.cancel（:655）同分支追加——遍历本 flow 登记的全部成员会话，未 settle 者逐个 `cancelSession(forced=true)`；单成员取消失败不中断汇聚（逐个留痕）。**per-flow 成员会话登记表唯一归属裁定 = orchestrator**：built 阶段（图构建时）收集各 spawner 返回的 spawnedSessionId 登记入 flow 上下文（登记表为数据收集面非取消机制——取消执行仍只走 `cancelSession`/R1-04 bindCancel 既有通道，与「禁新造并行取消机制」边界划清：登记表无任何取消语义，仅为汇聚取消提供成员清单）；spawner 返回 spawnedSessionId 为既有 SpawnMemberResult 面，零 SPI 变更。
- **R1-04 原语消费声明（禁新造机制）**：成员/spawn 超时底层取消一律经 plan 2499 已落地 `FutureHelper.bindCancel`/`ICancelToken` 通道与 `IAgentEngine.cancelSession` 既有方法——零新并行取消机制、零 nop-api-core 签名变更（条件申报①不触发，检查记录随实施留痕）。MemberFanOutDispatcher :322/:330 既有 orTimeout+cancelSession(forced=true) 面零回改（勘误 5 收窄后已实现路径）。
- **勘误 5 残留核对结论（Phase 1 亲读，登记）**：plan 252–259（nop-ai-agent 生命周期早期修复批，八单全 completed）经 grep 核对**零命中** Spawner/Orchestrator/FanOut/cancelSession/orTimeout 站点——C7-022 残余面（spawner 超时分支 + overall deadline 汇聚）**未被先例覆盖，红⑤判据全口径保留不收窄**；本核对结论与裁定⑤同 diff 在实施期复核（行号漂移以 live 为准）。
- **吸收面**：C3-025（RECLAIM 双执行窗——取消补齐后 RECLAIM 前会话已终止，双执行窗闭合）+ C4-014（单 bound 裸 execute——spawner bounded get 为唯一等待面，取消面补齐后闭合）随族闭环。**MA-16 约束继承**：新增取消协调零池/线程新增占用，不指向 globalTimer。

### 3.6 裁定⑥：journal/spill 累积上界（C5-008）

- **journal 轮转（预设 = 双 cap 参数化）**：FileBackedCheckpointManager journal 增条数 cap 与尺寸 cap 双上界（setter 配置化，零 `@cfg:` 键，缺省常量沿测试规模安全值放大到生产量级）；写路径超 cap 触发轮转——**活跃会话段超 cap 唯一形态 = 环形截断最旧 checkpoint + WARN 留痕**（截断只丢最旧恢复点，活跃会话恢复语义退化为「最近 checkpoint 可恢复」——恢复正确性面零损失，仅历史恢复点减少；截断永不触及最新写入段）；已完成会话段超 cap 归档截断（写入归档文件后截断主 journal，2505 R03「受管承载 + 盘写异步化」范式引用不重开）。**归档文件不受 cap 约束（定夺）**：cap 只约束主 journal 活跃段（进程内读写热路径），归档为冷审计面且已出进程内存包络——对归档再施 cap 属 CL-IND-01 保留策略族对齐注记范围，不属本批。类 javadoc :80-81「no rotation/cap」自认句随修复改写为轮转契约句。
- **既有面登记与增量收窄（R1 复核修订）**：`FileBackedCheckpointManager.remove`（live :199-215）**已实现**会话终态五容器清理——bySession 移除 + 其 byWatermark 条目遍历清除 + snapshotCache/saveCounters/loadedSessions 四容器 remove；裁定②终态清理清单行对该面为**接线调用**（引擎终态路径调用既有 remove），非容器清理逻辑新增。**裁定⑥真实增量 = 仅「压缩完成会话淘汰」**：压缩（compaction）完成但会话未终态的中间态下，已固化条目（snapshotCache 已固化快照 + byWatermark 已固化 watermark 条目）的会话级淘汰——终态面由裁定② remove 接线承载，两裁定增量边界以此为唯一划线。
- **byWatermark 会话级淘汰**：byWatermark（:102，put :159）的压缩完成会话淘汰为裁定⑥增量（上述）；终态淘汰挂接裁定②清单行（remove 既有五容器清理被真实调用）。snapshotCache（:103）/loadedSessions（:105）同边界（终态随 remove、压缩完成随本裁定增量淘汰）。
- **恢复分批解析（4× 放大消解，一次定夺）**：CheckpointJournalReader `parseAll` 全文 String + substring + sections + lines/fields 的全量物化改**分批解析**——按 journal 段（session 分段边界）逐批读入解析、逐批交付消费方，单批驻留上界 = 单会话段尺寸；禁一次性全文载入。备选（流式逐行 SAX 型解析）被拒：journal 行结构为多行 section，逐行流式解析器复杂度高且分段边界已提供天然批界。
- **恢复峰值物化观测器（m4 R1 转来义务，具体形态成文）**：
  - **held-reference 计数**：reader 解析批处理路径挂 `AtomicLong heldRefs` 计数器——每批物化容器创建时 increment、交付消费方（或释放）时 decrement；**上界断言**：单次恢复全程 `heldRefs` 峰值 ≤ 预设批上界（`maxHeldBatches`，缺省 = 单会话段批数上界），红⑥断言以该计数器为观测载体（缺陷态全量 parseAll 峰值 = 总条数 >> 上界，红；分批后绿）。
  - **解析持有行数上界断言**：`TestFileBackedCheckpointManager` 新增恢复峰值钉——构造超大 journal（N 会话 × M checkpoint），断言解析过程中 heldRefs 峰值与单批物化行数均在预设上界内（非总行数量级）。该钉为新增断言即新钉，缺陷态红 → 修复后绿。
- **spill/归档容器 CHM 化 + 容错面（CL-026/070/077/006/063）**：InMemorySpillStore :27 / InSessionCompactionArchive :36 `HashMap` → `ConcurrentHashMap`（InSessionCompactionSnapshotArchive 同病核对随 diff）；归档 put 移入容错 try（CL-077——put 失败显式 WARN 留痕非抛穿归档流程）；ioLock javadoc 失真声明修订为实际保护范围（CL-006/063——修订为「保护 journal 文件读写互斥」实态，不扩语义）。
- **拆分面闭环**：C5-010（压缩固化全量历史副本——压缩面随压缩完成淘汰 + 分批恢复承载）、C5-011（spill 全文本驻留——CHM 化为并发面，驻留上界归 CL-IND-01 族对齐注记）、C7-031（快照面随 C5-010、spill 面随 C5-011——按 §五.2 裁定拆分）随本裁定声明闭环。**红⑥增量断言修正（防假红）**：终态五容器清理为既有实现——红⑥的容器断言只对**增量面**（压缩完成淘汰 + cap 轮转 + 峰值观测器）设缺陷态红判据；终态接线面（裁定②）的缺陷态红挂在「引擎终态路径未调用 remove」的接线缺失上（调用存在性断言），两者判据源不同不得混用——否则对既有 remove 实现设红必为假红。

### 3.7 裁定⑦：超时取消传播（C3-011）

- **预设 = kill 通道接入判据成文**：AgentToolDispatcher cancelCall（:328-336）与 slice 等待循环超时/中断 cancel 点（:210/:268）在 `future.cancel(true)` 后追加——`IToolProgressTracker` 已装配时必经 `tracker.cancelExecution(callId)`（复用 heartbeat abandon :1677 与 background stop :695 同一通道）；**判据写死：tracker 已装配 ⇒ 机械超时与 cancelCall 必达 kill**。`BashSandboxProgressTracker.cancelExecution` → sandbox.cancelExecution（:31-32）为进程级终止载体（R1-06 已收口面零回改）。kill 失败/未装配（NoOp tracker）显式 WARN + 计数留痕（禁静默——现状即缺陷面反转）。**tracker 装配缝（实施期可决项登记）**：tracker 自 engine 注入到 dispatcher 持有的装配缝（engine 侧 `IToolProgressTracker` 配置 → AgentToolDispatcher 可达）实施期核实——若 dispatcher 现无 tracker 引用，以最小传递（执行上下文携带或 dispatcher 构造注入）接线并在实施记录登记选点，零接口签名变更。备选（dispatcher 自持进程句柄直杀）被拒：与 tracker 抽象重复建设且绕开沙箱层生命周期管理。
- **HTTP/LLM 吸收面逐项定夺**（消费 R1-04 bindCancel 请求级取消，禁新造）：
  - C3-004（幽灵重试）/ C3-007（requestTimeout 死契约）：LlmCallCoordinator `callChatWithTimeout`（:785）超时 `f.cancel(true)`（:806）后追加 bindCancel 链接的请求级取消（HTTP 客户端取消传播），requestTimeout 契约句与实现对齐；
  - C3-012（LLM 幽灵调用）：同上通道覆盖；
  - C4-005（embedding 链无取消通道）：embedding 调用挂同一 bindCancel 链接；
  - C7-024（call-agent async 无取消协调）：call-agent 异步执行挂 cancelSession 既有语义（裁定⑤通道），无新增协调器。
- **cancelCall javadoc 契约句修订**：进程级 kill 契约由「声明无实现」改为「tracker 装配即达 kill + 未装配显式留痕」实态描述——javadoc 与实现同 diff 对齐。
- **「底层是否停」断言补齐（C10-006）**：红⑦断言工具超时后底层进程/tracker 收到 cancelExecution（mock tracker 层非 docker 可跑——勘误 10 面落实）。
- **条件申报①检查条款**：预设 = 纯消费 R1-04 原语 + 模块内 IToolProgressTracker 接口，零 nop-api-core 触碰；若实施中确需平台侧新增设施/SPI 签名变更，须先在 plan PA 行 + 本文档修订 + draft review 申报并列全 SPI 回归面（bindCancel/ICancelToken 全实现逐类列举）方可实施。

### 3.8 裁定⑧：方言流式协议族（C3-028，族单修复批）

- **多 chunk 语义收敛（一次定夺 = AbstractLlmDialect 包内多 chunk 通道，ILlmDialect :138 签名零变更）**：`AbstractLlmDialect` 新增包内 `parseStreamChunks(String): List<ChatStreamChunk>`（单事件多 entry 全枚举）；`ILlmDialect.parseStreamChunk`（:138 单 chunk）保留为兼容委托（取 chunks 首元素），消费侧 `ChatServiceImpl`（:263）与聚合端改调 `parseStreamChunks`（nop-ai-core 模块内消费面，签名演进为零——**条件申报②不触发**，检查记录随实施留痕）。备选（改接口返回 List）被拒：触发条件申报②申报流程与全部方言实现 + 外部实现方回归面，且兼容委托形态已承载多 chunk 语义无能力损失。OpenAI（:345 单 entry return 取首个）/ Gemini（:496/:512 首 part return）/ Ollama（:341 get(0) 截断）三站点同改全枚举。
- **Ollama 阶梯判定修正（C3-028×C3-031 两案一次消解——MA-09 两案必居其一）**：:327 `content != null` 判定改 `content != null && !content.isEmpty()`（空串不再抢占 text 分支）——空串帧落空后 thinking（reasoning）与 tool_calls 分支可达，get(0) 截断面随多 chunk 全枚举一并消解；同一 else-if 阶梯单点修正，两案同 diff 消解（C3 round3 复核「两案必居其一同批」义务兑现）。
- **usage 四方言接入矩阵**：

| 方言 | 站点 | 修复 |
|------|------|------|
| OpenAI | buildBody 未启用 `stream_options.include_usage`（live grep 零命中）+ parseStreamChunk :248-305 无 usage | 请求启用 include_usage + 终止 usage 帧解析（usage.prompt/completion_tokens）；**usage 帧（data: [DONE] 前末帧）choices 为空数组——解析须容 null/空 choices**（现 :248-305 choices 取值路径对空数组的行为随多 chunk 通道一并容错），usage 帧只产 usage chunk 不产内容 chunk |
| Anthropic | message_start usage 残缺 :538-546（cache token 未入流式链） | message_start usage 补 cache_creation/cache_read token 映射（非流式 :477-496 既有映射对齐） |
| Gemini | 无 usageMetadata 解析 | 流式 usageMetadata 帧解析接入 |
| Ollama | 无 eval_count 解析 | eval_count/eval_duration 映射接入 |

  消费链四条（usage 聚合/计费/观测）随解析复原而失活反转；usage 缺省帧（provider 未回）容忍为 null（非 fail-loud——usage 为增强信号非正确性信号，与 2505 聚合双上界的 null 容忍口径一致）。
- **带内错误通道（C3-032）**：`ChatStreamChunk` 增类型化错误承载（error itemType 或等价字段——现有字段承载预设内）；Anthropic :646-661 「[ERROR] …」text DELTA 污染改型为类型化 error chunk（禁文本污染 assistant 内容）；五方言 error 事件统一经该通道；消费侧对 error chunk 显式终止 + 类型化留痕。
- **Anthropic/Responses 免疫复核结论（C3 round2 建议面 a）**：**非免疫**——Anthropic 在带内错误污染（text DELTA）与流式 usage 残缺两面确有缺陷（:646-661/:538-546 亲读）；Responses 在 tool_call 多 entry 与 usage 面免疫（:515-540/:571-574 对照成立），但带内错误通道缺失为其五方言共性——免疫声明收窄为「Responses 在 usage/tool_call 两面免修，错误通道仍随族接入」。红⑧的 Anthropic/Responses 免疫钉按收窄口径落（Responses 零 diff 面 + Anthropic 修复面）。
- **m3 机械验收式（R1 review 转来义务成文）**：MA-09「两案必居其一」的机械验收 = **红⑧前两断言（单事件多 tool_call 全枚举断言 + Ollama 空串帧 thinking/tool_call 可达断言）必须在同一 staged diff 内落地，且两断言在缺陷态（pre-fix 回放：`git stash` 主实现保留测试）下为红**——验收操作序列：(1) 确认两测试文件与 OllamaDialect/OpenAiDialect 修复在 `git diff --cached` 同一变更集；(2) 对缺陷基线 commit 单独 apply 测试文件跑红并快照留 `_tmp/`；(3) 恢复全 diff 跑绿。两断言任一缺失或缺陷态非红 = MA-09 验收 FAIL，closure audit 抽查该序列留痕。
- **方言 fixture 矩阵钉（C10-002 四轴）**：单 entry 多 tool_call / 零 usage / 零 error / 空串掩蔽四轴 × 五方言 fixture 钉（§5.1 红⑧）。

### 3.9 裁定⑨：DBMessageService 治理四轴（C3-021）

- **(a) 投递与 poll 分离**：单线程 poller（:208-213 `newSingleThreadScheduledExecutor` + `scheduleWithFixedDelay(this::pollAllTopics)`）收敛为「poller 只取消息 + claim」，投递回调外移**独立有界投递 executor**（bounded pool，缺省容量沿裁定③池形态，拒绝策略显式 = 队列满类型化拒绝 + 消息回 claim 释放 + WARN 计数留痕——禁静默丢弃，rule 24；拒绝策略禁 Discard/OmitQueue 静默形态）——call-agent handler 阻塞 120s 期间其他 topic 投递不断链（CL-040 单线程互饿消解）；sweep（:217）随投递外移获得独立执行面。
- **(b) 毒消息处置（C7-025）**：per-message 重投计数上界（缺省 3，claim 列携带或等价）——超上界消息落 DLQ（死信落账 = 独立表行或等价持久留痕，含原始载荷/失败原因/计数）并 ack 跳过，队列停摆反转；失败回调形态对齐 4.1-C8-002 契约（attempt=0 起算 + guarded 回调——失败回调自身异常绝缘，plan 2503 Phase 4 已落地契约消费面）。
- **(c) consumers 注册治理（C1-019/CL-024）**：同 topic 同 consumerId 重复注册 = **显式类型化拒绝**（顶替静默替换反转）；注册携带身份校验（ownerId/等价标识，消费回调归属可核）；取消路径 cancel 与注册对称（退订后消息不再投递该 consumer）。**兼容盘点义务成文**：既有 subscribe 调用方与测试族（`TestDBMessageServiceInsulation` 等——live :104 `svc.subscribe("agent.X.inbox", handler)` 单参形态）零顶替场景，Phase 2 组 F 实施前置盘点全部 subscribe 调用点确认无一依赖「同 consumerId 重复注册=顶替」语义；若有依赖点，登记偏差并按显式 re-register（先 cancel 后 subscribe）适配——拒绝策略不得静默放宽。
- **(d) claim 世代 fencing（CL-069）**：claim 增世代号（fencing token——**引用 2514 裁定⑥条件 UPDATE/租约形态菜单不重建**，本站点实例化为 claim 行世代列条件 UPDATE），旧世代消费写被条件 UPDATE 拒绝；suspend 死标志面随世代化消解（suspend 状态由 claim 行世代+状态承载，非进程内死标志）。
- **拒绝策略 × 重投上界兜底关系成文**：投递队列满的类型化拒绝与毒消息重投上界为**同一兜底链**——队列满拒绝的消息回 claim 释放时计入该消息重投计数（非零起点重新入队重投），重投超上界（缺省 3）即落 DLQ——拒绝风暴被 DLQ 上界终结，不存在「无限拒绝-重投循环」；拒绝计数与重投计数同源（per-message），指标去重由计数单调性自然承载。
- **DLQ 自建表与零 orm 触碰不冲突声明**：DLQ 死信落账为 DB 自建表行（ai-agent 模块既有自建表同族——DBMessageService 既有消息表 JDBC 面），**非 ORM 源模型**（零 `model/*.orm.xml` 触碰、零 DDL 源模型变更），与 PA 判定「零 orm 触碰」不冲突（自建表 JDBC 面与裁定⑩ DbSessionTakeoverLock 同形先例）。
- **R1-08 对齐声明（MA-11）**：本裁定实施按 4.1 merge-adjudication §五-6 对齐——attempt=0/guarded 回调契约消费；落侧后在 cra-index :247（3.1-C3-021 行）/:268（4.1 侧）双行注记闭合（Phase 4）；4.1 REGISTRY 契约域（registry-lifecycle-contract.md 裁定⑥）对齐注记零触碰。

### 3.10 裁定⑩：接管锁续期异常升级 abort（C7-012）

- **预设 = 连续 N 次 + 不可分类异常判据升级 abort**：renewOnceSafe（:65-85）异常处理增计数面——**计数 per-handle 归属指名**：计数挂 `AgentSessionLifecycle.CancelHandle`（renewOnceSafe 首参，:65 live 亲读——每会话续期句柄唯一，per-handle 计数天然隔离多会话互不干扰；句柄注销随 handleLeaseLost/终态清理同步销毁）——**连续 N 次**续期异常（缺省 N=3，setter 配置化零 `@cfg:` 键）或**不可分类异常**即调用 `handleLeaseLost`（:86）同型 abort 路径——**lease 未知即视为可能丢失**（fail-safe 方向：非对称分区下本节点自 abort 胜过双执行）；成功续期复位计数。**异常分类判据 = unwrap 后 cause 链（指名）**：`tryRenew` 的 SQLException 被 `NopAiAgentException` 包装抛出（DbSessionTakeoverLock :313-316 ERR_AGENT_INTERNAL_DETAIL）——分类不得按外层 NopAiAgentException 判（恒「不可分类」→ 单次即 abort 误杀，等价备选即时 abort 的可用性缺陷回归）；**必须沿 cause 链 unwrap 至根因**：根因 SQLException/连接类（连接超时/拒绝/断连）= DB 不可达不可分类；根因可判定瞬时类（锁等待超时等可重试语义）方入豁免窗——unwrap 判据随实施落分类函数单点，禁按外层类型短路。备选（即时 abort——单次异常即 abort）被拒：DB 单次抖动误杀健康执行放大失败率（plan Risks 节可用性风险面），连续 N 次为可用性与 fencing 的最小折中。
- **瞬时豁免窗 = 计数 + 上界显式**：瞬时异常（判据 = 非连接类异常或可重试类）豁免窗以计数承载（N 次内 WARN 留痕 + 计数暴露），无独立定时器；豁免窗上界即 N，超界必 abort（禁无限 WARN——现状即缺陷面，rule 24）。
- **plan 2496 护栏零回退声明**：:74-85 catch Throwable 面零改动（护栏语义保留）；「仅 false 返回触发 handleLeaseLost :86-98 的协议窄化」由本裁定扩为「false 或异常升级判据满足」双触发——护栏消费零回退，升级分支为纯新增。
- **C10-010 半边口径**：本裁定只承载 MA-19 半边（异常分支/abort 轴断言），墙钟轴（租约期限墙钟 C7-018）为批外接缝登记不代做（successor = P2 束/R4-21 触发条款）。

### 3.11 守卫指标与可观测一览（键名/指标名一次定夺，零二源）

| 名称 | 形态 | 裁定 | 语义 |
|------|------|------|------|
| `nop.ai.agent.inject-after-terminal` | WARN + 计数 | ① | 终态后到达注入显式拒绝留痕 |
| `nop.ai.agent.terminal-cleanup-miss` | WARN | ② | 终态清理清单行清理失败显式留痕 |
| `nop.ai.agent.spawn-drain-discard` | WARN + 计数 | ④ | drain 残余任务显式 fail/settle 留痕（禁静默） |
| `nop.ai.agent.member-cancel-failed` | WARN + 计数 | ⑤ | cancelSession 失败/汇聚取消单点失败留痕 |
| `nop.ai.agent.checkpoint-journal-rotate` | INFO + 计数 | ⑥ | journal 轮转触发观测 |
| `nop.ai.agent.tool-kill-unreachable` | WARN + 计数 | ⑦ | tracker 未装配/kill 不可达显式留痕 |
| `nop.ai.message.dlq-drop` | WARN + 计数 | ⑨ | 毒消息 DLQ 落账观测 |
| `nop.ai.message.claim-fenced-reject` | WARN + 计数 | ⑨ | 旧世代消费写被拒观测 |
| `nop.ai.agent.lease-renew-abort` | WARN + abort 事件 | ⑩ | 连续异常升级 abort 观测 |

零新 `@cfg:` 配置键声明：全部上界/缺省（N=3、drain 2s、重投 3、批上界、journal cap）为常量缺省 + setter 注入面。

### 3.12 零 orm 触碰声明与两处条件申报条款

- **零 orm/零生成物声明（10/10 PA 判定兑现）**：全部成员 = `src/main/java` 纯代码修复 + gate 测试改写；站点分布于 nop-ai-agent/nop-ai-core 两模块，无 `model/*.orm.xml` 触碰、无 `_` 生成物触碰（plan PA 判定表 live 核实结论承接）。
- **条件申报条款①（裁定⑤⑦⑨平台 API 面）**：预设 = 纯消费 R1-04 已落地原语 + 模块内接口，零签名变更；若需 `io.nop.api.core.*` 新增设施或 SPI 签名变更，触发 nop-api-core plan-first——须在 plan PA 行 + 本文档修订 + draft review 完成申报并列全 SPI 回归面（bindCancel/ICancelToken 全实现逐类列举）方可实施；预设分支不触。
- **条件申报条款②（裁定⑧ ILlmDialect 契约演进）**：预设 = AbstractLlmDialect 包内多 chunk 通道 + ChatStreamChunk 现有字段承载，`ILlmDialect` 接口零签名变更；若裁定演进为接口签名/新增抽象方法，须在 draft review 申报并登记迁移方案（全方言实现 + 外部实现方回归面）；预设分支不触。

## 四、对齐纪律条款（逐条成文）

1. **MA-04 × C4-015 同变更集**：裁定③（池上提/close 接线）与裁定④（drain 语义）同一变更集承载——池生命周期一次修（batch-proposal R2-16 行增补段「MA-04 spawn 池 close 接线（C4-015 同批）」+ G51 行「C4-015 与 MA-04 同批」）；CL-036 惰性建池同步随池上提同 diff。Phase 2 提交纪律：组 B 单一变更集含③④两裁定，拆分提交即违反绑定。
2. **R1-04 原语消费禁新造（MA-05/MA-08）**：裁定⑤⑦全部取消路径经 `FutureHelper.bindCancel`/`ICancelToken` 通道与 `IAgentEngine.cancelSession`/`IToolProgressTracker.cancelExecution` 既有方法——禁止新造并行取消机制；不覆盖面（进程级 kill 已有通道、HTTP 请求级取消）在裁定⑤⑦内一次定夺并完成条件申报①检查（预设不触发）。
3. **MA-09 两案必居其一同批（C3-028×C3-031）**：Ollama :341 get(0) 截断与 :327 空串抢占共享同一 else-if 阶梯——裁定⑧单点修正一次消解两案，机械验收式见 §3.8（红⑧前两断言同 diff 落地且缺陷态红）；usage 四方言站点与错误通道五方言站点同变更集落地（族单修复批）。
4. **MA-11 × R1-08 consumeAwaitTimeout 对齐**：裁定⑨按 4.1 merge-adjudication §五-6 对齐（4.1-C8-002 attempt=0/guarded 回调契约消费）；落侧后 cra-index :247/:268 双行注记闭合（Phase 4）；4.1 REGISTRY 契约域对齐注记零触碰。
5. **MA-19 × WI 1.6 家族关联不并案 + plan 2496 护栏零回退**：裁定⑩ abort 语义修复模式对齐 WI 1.6 fencing 族（续租缺失/CAS 守卫），载体与模块不同零重复立项；:74-85 Throwable 护栏零回退（升级分支纯新增）；C7-018（P2 墙钟）批外接缝登记不代做。
6. **gate3 改写 = 全批唯一允许的既有钉改写（MA-02 绑定）**：`TestInvariantGate3EntryPointCleanup` 改写仅限裁定②义务范围（清单扩容 + F4 断言补全），语义对照留痕（§5.3）；零回退钉清单（§5.4）其余全部钉零回改；勘误 11 兼容性核对表随 Phase 2 留痕。
7. **MA-16 约束继承**：本批全部新增池/线程占用面（裁定③共享 spawn 池、裁定④ daemon 面、裁定⑨投递 executor）遵守 R1-03 收窄后形态——「阻塞/长任务体不得指向 globalTimer；满足豁免判据的轻量直连合法」；全部新 executor 零 globalTimer 指向（裁定③已显式声明）。

## 五、裁定⑪：测试缝与零回退钉

### 5.1 红①–红⑩驱动器（先红后绿，E2；缺陷态红判据 → 修复后绿口径）

| 红 | 成员 | 载体形态 | 缺陷态红 → 修复后绿 |
|----|------|---------|-------------------|
| ① | C1-008 | N 线程 resume/wake × injectInput 压力断言（压力窗上界防噪声化）+ 定向交错断言（drain 执行后/remove 执行前持锁注入——§3.1 B1 唯一形态确定性交错测试） | 无 CME/无注入丢失断言红（CME/注入丢失实发）+ 交错注入丢失且无拒绝红 → 对称注册 + 注入锁 + synchronizedList + 标记后拒绝绿（C10-005） |
| ② | C5-015 | 终态执行后 sessionStore/checkpoint 等容器 isEmpty 断言（参数化遍历） | put-only 红 → 接线 + 清单扩容绿；持久层终态行在（save 反向 pin）断言同 diff；gate3 改写后新钉保持绿（C10-003） |
| ③ | C4-010 | K 次 team-execute-flow 后 spawn 池线程数守恒断言 + 惰性建池并发双建断言 | 线性增长红 → 池上提 + 单点建池绿（C10-007） |
| ④ | C4-015 | stop 后 queued 任务 dispatch future 落定断言 | 永不 settle 红 → 有界 drain + 显式 fail 绿（C10-007） |
| ⑤ | C7-022 | spawner 超时后 spawnedSessionId 会话终态断言 + RECLAIM 后无双执行断言；overall deadline 侧两层断言分层——「图已断」（taskRt cancel token 置位，既有 AR-3 面）与「会话已终态」（成员会话 cancelSession 生效，本批增量）各独立断言 | 僵尸执行红 → cancelSession 绿；汇聚取消断言同 diff（C10-007） |
| ⑥ | C5-008 | 超 cap journal 轮转/压缩完成淘汰断言（**增量面**——终态五容器清理为既有 remove，红判据挂接线缺失非容器逻辑）+ 恢复峰值物化上界断言（heldRefs 观测器 §3.6）+ spill 并发写断言 | 无界/峰值 4× 红 → 双 cap + 分批解析 + CHM 化绿（C10-017） |
| ⑦ | C3-011 | 工具超时后底层进程/tracker 收到 cancelExecution 断言（mock tracker 层非 docker 可跑） | 幽灵执行红 → kill 接入绿（C10-006） |
| ⑧ | C3-028 | 单事件多 tool_call 全枚举断言 + Ollama 空串帧 thinking/tool_call 可达断言 + usage 四方言非空断言 + error 事件类型化断言（缺陷态逐项红）；Anthropic/Responses 免疫钉按 §3.8 收窄口径 | 截断/抢占/失活/污染红 → 协议族修复绿（C10-002）；m3 机械验收式 §3.8 |
| ⑨ | C3-021 | call-agent 长回调期间其他 topic 投递不断链断言 + 毒消息 DLQ/停摆上界断言 + consumers 顶替拒绝断言 + claim 旧世代写拒绝断言 | 冻结/停摆/顶替/越权红 → 治理四轴绿（C10-009） |
| ⑩ | C7-012 | 连续 SQLException 后本地执行 abort 断言 + 瞬时抖动豁免窗断言 | 无限 WARN 红 → 升级 abort 绿（C10-010 半边） |

### 5.2 测试资产落位与 docker-gated 规避

新增/扩展测试按站点归位：nop-ai-agent——engine（红①：TestDefaultAgentEngineInjectionWindow 新建；红⑦：TestAgentToolDispatcherKillPropagation 新建 mock tracker 层；红⑩：TestSessionLockRenewalAbortUpgrade 扩展）、session（红②：TestTerminalCleanupSessionStore 新建 + TestInMemorySessionStore/TestDBSessionStore/TestFileBackedSessionStore 扩展）、gate（红② gate3 改写 + 参数化终态钉）、tool/team.flow（红③④⑤：TestTeamExecuteFlowExecutorPoolLifecycle/TestSpawnPoolDrain/TestMemberSpawnerTimeoutCancel 新建，`TestFanOutFutureLifecycle` 零回退）、reliability/compact（红⑥：TestFileBackedCheckpointManager 扩展 + TestSpillStoreConcurrency 新建）、message（红⑨：TestDBMessageService 治理四轴扩展 + TestDBMessageServiceReliability DLQ 面）。nop-ai-core——dialect（红⑧：五方言 Test*Dialect 扩展 fixture 矩阵 + TestLlmDialectErrorResponse 类型化断言扩展）。补测类声明：新增断言即新钉、缺陷态红→修复后绿；红绿快照落 `_tmp/` 并登记。docker-gated 规避：红⑦ mock `IToolProgressTracker`/fake 进程层非 docker 可跑（勘误 10 面落实）；`TestDBMessageService*` 族内嵌 DB 构建内可跑，环境缺位按三联口径登记。

### 5.3 gate3 改写语义对照表（全批最重测试面义务，同 diff 留痕）

| 钉（改写前） | 改写前语义 | 改写后语义 | 兼容性 |
|-------------|-----------|-----------|--------|
| :87「源码含 `"default void remove"` marker」（checkpoint-removal family 内） | 钉 ICheckpointManager.remove 默认方法存在性 | 保留原 marker 断言 + **新增** sessionStore 三实现 `sessions.remove` 接线 family 行 | 原 marker 语义零变化（checkpoint remove 仍存在），纯加法扩容 |
| :130-138 四组九串清理清单（runningExecutions×2 / tryAcquire / releaseLockQuietly / startLockRenewal / cancelLockRenewalQuietly / default void remove / public void close / extends AutoCloseable） | 「字面串在指定文件存在」的死串完备性钉——清单不含 sessionStore/memory/mailbox/backgroundRegistry | 九串**零删改**保持绿 + **新增** F4 承载容器 family 行（sessionStore 三实现 + backgroundRegistry/mailbox 等按裁定②清单） | 全部既有 family 语义零变化；扩容行为加法 |
| 终态断言（既有 gate3 入口对称性断言） | 入口-清理对称性源码级断言 | **新增**参数化运行时终态钉：装配引擎 → 执行至终态 → 遍历清单容器断言全部 isEmpty（运行时行为级，与源码级钉互补） | 纯新增钉；缺陷态红（put-only）→ 修复后绿 |

改写义务边界：改写仅限上表三行加法扩容；任何既有九串删改或断言弱化 = 违反 MA-02 绑定，零容忍。

### 5.4 零回退钉全列（钉零回改逐条 git diff 为空核对；唯一允许改写 = §5.3 gate3 三行）

- engine：`TestDefaultAgentEngineCancel` / `TestDefaultAgentEngineConcurrencyGuard` / `TestEngineLifecycleAndCheckpointBounded` / `TestSessionLifecycleLockGate` / `TestSessionLifecycleSaveBeforeRelease` / `TestFanOutFutureLifecycle`——零回退（裁定①⑦⑩兼容性核对表随 Phase 2 留痕）。
- session：`TestInMemorySessionStore`（:56 remove 钉）/ `TestDBSessionStore`（20 @Test）/ `TestFileBackedSessionStore`——零回退。
- team/flow：`TestMemberSpawner` / `TestTeamTaskFlowOrchestratorAsync` / `TestAsyncSpawnStep*` 族 / `TestAsyncTeamTaskFlowEndToEnd`——零回退。
- reliability：`TestFileBackedCheckpointManager`（17 @Test）/ `TestDBCheckpointManager` / `TestCheckpointJournalSnapshotFormat`——零回退（红⑥为加法钉）。
- dialect（nop-ai-core）：`TestOpenAiDialect` / `TestOllamaDialect`（:191 tool_calls 参数保留钉——裁定⑧修复后该钉必须保持绿：全枚举后参数保留语义不弱化）/ `TestGeminiDialect` / `TestAnthropicDialect` / `TestResponsesDialect`（Responses 零 diff 面钉）/ `TestLlmDialectErrorResponse`（错误文本可观测面钉——类型化改型后文本面等价保留，语义对照随 diff 留痕）——零回退。
- message：`TestDBMessageService` / `TestDBMessageServiceCrossInstance` / `TestDBMessageServiceInsulation` / `TestDBMessageServiceReliability`——零回退；裁定⑨投递异步化致既有同步断言时序化时，**同 diff await 化改写并登记语义对照**（参照 2518 MA-02 await 化先例口径）——该改写为登记在案的第二个受控改写面（非静默弱化）。
- 2501 钉：`TestShellPipelinePoolIsolation_C4_004_3_2` / `TestShellPipelineDeadlockFamily_C4_003` / `TestToolManagerQueueChain_C1_001`——零触碰。
- 上游环境抖动按执行计划勘误 9 三联裁定（stash 基线同红 / solo 绿 / 恢复复跑全绿）处理留痕，不得静默重跑或跳过。

### 5.5 组 A–G 落位与同变更集绑定（Phase 2 提交纪律对齐）

| 组 | 成员（裁定） | 模块 | 同变更集绑定 |
|----|-------------|------|-------------|
| A | C1-008（①）+ C5-015（②） | nop-ai-agent engine/session/gate | ①②共享终态摘除序不变式；C10-005/003 + gate3 改写随同 diff |
| B | C4-010（③）+ C4-015（④）+ C7-022（⑤） | nop-ai-agent tool/team/team.flow/team.scheduler | ③④同变更集（MA-04 绑定）；⑤消费 R1-04 原语；C10-007 随同 diff |
| C | C5-008（⑥） | nop-ai-agent reliability/compact | 与②清单协同行同 diff 协调；C10-017 随同 diff |
| D | C3-011（⑦） | nop-ai-agent engine/tool | C10-006 随同 diff；条件申报①检查留痕 |
| E | C3-028（⑧） | nop-ai-core dialect | 族单修复批；C10-002 随同 diff；条件申报②检查留痕 |
| F | C3-021（⑨） | nop-ai-agent message | R1-08 对齐；C10-009 随同 diff |
| G | C7-012（⑩） | nop-ai-agent engine/runtime.lock | plan 2496 护栏零回退；C10-010 半边 |

### 5.6 端到端与接线验证义务（rule 22/23，归 Phase 2）

- **端到端三链**：(a) 会话生命周期全链（resume/wake 注入并发 → 执行 → 终态清理全容器 isEmpty → 恢复会话不含已释放状态——覆盖①②）；(b) team-flow 全链（spawn → 成员超时 → cancelSession → 池 close 有界 drain → dispatch future 全落定——覆盖③④⑤）；(c) 交付链（方言流式多 tool_call+usage+error 帧全枚举 → DBMessageService 投递分离下毒消息 DLQ → 续期异常升级 abort——覆盖⑧⑨⑩）。
- **接线验证五断言**：②终态后 sessionStore.remove 真实被调用（非仅方法存在）；③池上提后 TEE 构造路径真实复用共享 executor（线程名/计数证据）；⑤spawner 超时分支真实调用 cancelSession；⑦kill 通道真实被超时路径消费（零接入现状反转）；⑨独立投递执行器真实承载回调（线程名证据）。
- **无静默跳过（rule 24）**：④残余任务显式 fail/settle；⑤⑦取消失败显式 WARN+计数；⑨DLQ 落账+拒绝策略显式；⑩豁免窗上界后显式 abort；新增公共分支零空方法体/TODO。
- **变异探针纪律**：裁定⑤⑦⑩触碰取消/停止/abort 路径——收口前必须运行含完整取消-恢复流程的端到端测试，恢复动作以断言方式确认目标可执行行真实存在。

### 5.7 锁序总表（全批新增锁域一致性核对）

| 锁域 | 裁定 | 允许嵌套 | 禁止 |
|------|------|---------|------|
| per-session 注入锁（AgentSession monitor） | ① | 锁内 drain pendingInjections + 终态 save 前段；锁外做 sessionStore.remove/persist | 锁内等待引擎执行/网络；锁内调用 injectInput 回环 |
| 终态清理段（引擎终态路径顺序契约，非新锁） | ①②⑥ | 锁内段（terminal 标记 + drain，与 per-session 注入锁同锁）→ 锁外段（persist → 注册表 remove → ②⑥清单清理，含调用 FBCM.remove 既有五容器清理） | 锁内段做 persist/remove IO（§3.1 B1 唯一形态：persist/remove 一律锁外）；清理段内发起新执行/注入 |
| TEE 共享 spawn executor 建池锁 | ③ | 无（double-check 快速路径） | 建池锁内提交任务 |
| 投递 executor（⑨）+ poller 单线程 | ⑨ | 投递线程可同步等待单消息 claim UPDATE（有界 DB 往返） | 投递线程内执行长回调阻塞队列（call-agent 等长任务沿既有 async 语义外移）；poller 线程内执行消费回调 |
| SessionLockRenewal 单线程续期 executor | ⑩ | 续期线程内 abort 调用（handleLeaseLost 既有路径） | abort 内同步等待续期结果 |

跨域无环论证：注入锁为 session 级最内层、终态清理段为顺序契约无锁竞争、spawn 建池锁与引擎池锁分属载体零交集、投递 executor 与 poller 分工单向（poll→submit→deliver）、续期线程独立——无环成立，closure audit 抽查临界区边界。

### 5.8 C10 集落名（成员面八条，随本体同 diff，E2；半边口径按执行计划勘误 7）

C10-005（红①）、C10-003（红②，含 gate3 改写履行注记）、C10-007（红③④⑤三案共用）、C10-017（红⑥）、C10-006（红⑦）、C10-002（红⑧）、C10-009（红⑨）、C10-010（红⑩，**仅 MA-19 半边**——MA-16 半边归 R4-21 不属本批）。其余 3.1 全量映射成员归 R4-21/R4-32/G49/已 fixed 各本体（inventory-p2-c10.md :789 口径）。

## 六、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|---------|
| ① injectInput 侧查询恢复态判断可注入 | 窗口判定搬散为判据漂移面；注册表单点 SSOT 最窄 |
| ① CopyOnWriteArrayList 载体 | 高频 add 低频读场景写放大；synchronizedList + 复合操作入锁已承载溢出判定原子性 |
| ③ per-invocation try/finally close | 「忘 close 即泄漏」缺陷形态保留；池与单例载体生命周期对称结构性消除 |
| ④ shutdownNow 后逐任务取消回调 settle | 与 executor 内部队列结构耦合；统一 drain 点 settle 最简 |
| ④ 无上界 awaitTermination | 停机挂死轨迹；有界 drain + 显式 fail 为唯一合法形态 |
| ⑤ spawner 侧等待超时后仅标记不取消（现状） | 僵尸成员占引擎槽与 LLM 配额 + RECLAIM 双执行——缺陷本体 |
| ⑥ 全文流式逐行 SAX 解析 | 多行 section 结构下解析器复杂度高；分段批解析已消解 4× 峰值 |
| ⑥ usage/journal 无界保留（现状） | 防 OOM 机制自身 OOM 源；双 cap 轮转为唯一合法形态 |
| ⑦ dispatcher 自持进程句柄直杀 | 绕开 tracker/沙箱层生命周期管理；复用 heartbeat 通道零新原语 |
| ⑧ ILlmDialect.parseStreamChunk 返回 List | 触发条件申报②与外部实现方回归面；包内通道 + 兼容委托零签名演进同能力 |
| ⑧ 错误文本继续经 text DELTA（现状） | assistant 内容污染；类型化 error chunk 为唯一合法形态 |
| ⑨ 消费回调仍内联 poller 线程（现状） | call-agent 120s 全模块冻结——缺陷本体；投递分离唯一闭合 |
| ⑨ 毒消息无限重投（现状） | 队列停摆；重投上界 + DLQ 落账为唯一合法形态 |
| ⑩ 单次异常即时 abort | DB 抖动误杀健康执行；连续 N 次 + 不可分类判据为可用性/fencing 最小折中 |

## 七、与已有设计的关系

- [bindcancel-context-unification.md](bindcancel-context-unification.md)：R1-04 取消原语（plan 2499 落地，SPI ×11 回归面在案）——裁定⑤⑦全部取消路径的消费基座，禁新造（对齐纪律 §四.2）。
- [pool-facility-hardening.md](pool-facility-hardening.md)：globalTimer 容量语义与 R1-03 收窄后形态——裁定③⑤⑨新增池面 MA-16 约束继承（§四.7）；`AgentDispatchExecutor` 有界队列 + fail-loud（plan 2498 C4-011）为裁定③④⑤池/拒绝形态先例。
- [wf-task-scanner-fencing-and-task-wf-concurrency.md](wf-task-scanner-fencing-and-task-wf-concurrency.md) 裁定⑥：条件 UPDATE/租约形态菜单——裁定⑨ claim 世代 fencing 引用不重建（本站点实例化）。
- [registry-lifecycle-contract.md](registry-lifecycle-contract.md) 裁定⑥：4.1 REGISTRY 契约域——裁定⑨对齐注记零触碰关系。
- plan 2505（R2-02 流式取消/期限两侧）：流式主链取消契约与聚合双上界为裁定⑦⑧消费/对齐面，零回改；R03 盘写异步化范式为裁定⑥ journal 轮转引用。
- plan 2496（MA-07 平台绝缘层）：SessionLockRenewal Throwable 护栏——裁定⑩零回退基线（升级分支纯新增）。
- plan 2501（R1-06 沙箱族）：Docker 沙箱超时清理已修面与裁定⑦ kill 通道互补不重复；2501 三钉零触碰。
- **下游 SSOT**：R4-21（8.61，硬依赖本批）到达时 (a) MA-10 会话存储并发装载引用裁定②终态清理接缝不重建；(b) MA-16 调度器 globalTimer 约束沿 R1-03 收窄后形态（本批 §四.7 继承声明为其先例面）；(c) 池/取消/清理裁定③④⑤为其消费不重建体；C7-018 接缝转达随批末就绪注记。R4-24（4.1-MA-01 consumeAwaitTimeout 另一侧）以裁定⑨落侧契约为对齐基准。就绪注记归执行计划收口段。
- owner doc 落点（Phase 3 统一落账，本 Phase 零 owner doc 变更）：`docs-for-ai/03-modules/nop-ai.md` 新增「agent 编排与会话生命周期并发契约」节（注入登记/终态清理清单语义/spawn 池生命周期与 drain/成员超时取消契约/工具超时传播与 kill 通道判据/接管锁 abort 语义/journal 累积上界）+ 方言流式协议契约句（多 chunk/usage/错误通道）+ DB 传输治理契约句（投递分离/毒消息/claim fencing）+ 源码锚点补行；`docs-for-ai/04-reference/source-anchors.md` 按实际。
