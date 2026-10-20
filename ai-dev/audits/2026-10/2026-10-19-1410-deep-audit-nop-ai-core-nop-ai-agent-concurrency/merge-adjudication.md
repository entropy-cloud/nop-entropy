# 单元内合并裁定 — nop-ai-core-nop-ai-agent（WI 3.1）

> 裁定人：WI 3.1 独立合并裁定员（与五维初审批次/Phase 4 复核隔离）。日期：2026-10-20。Status: closed。
> 输入：本目录五维度文件 `## 最终保留项` + `## 维度复核结论` 节（C1/C3/C4/C5/C7）、round 文件、`C10-test-protection.md`（含复核修正 M1–M7）、`ai-dev/audits/cra-index.md`（WI 1.1–2.5 台账）。不读其他单元审计目录详情。

## 一、裁定口径与输入

1. **输入事实**：复核后保留 128 条（C1×27 + C3×32 + C4×16 + C5×21 + C7×32 = 128；P0×0 / P1×30 / P2×76 / P3×22；现状标注全部「新发现」——与 C10 复核对账结果一致）。维度集外线索 CL×77（CL-001…074 五维 overlay + CL-075/076 于 C1-round2 + CL-077 于 C3-round3）。C10 缺保护登记 19 条（C10-001…019，全 P2，经其复核 M2/M3 修正后 128 条唯一主归属闭合）。
2. **三态判定**：每条保留发现恰好落入 ①缺陷族（≥2 条同根因同修复点，吸收合并，编号 MA-01 起）②独立立案 ③watch-only 之一；不遗漏、不重复计数。**族与独立项总数 = 去重后工单数**。
3. **族严重度取最严**：族内成员保留各自复核终判严重度备查，工单排期严重度取族内最严档——由此自动消解族内登记的大部分判级分叉。
4. **指针网为输入**：五维复核已登记的跨维/跨单元指针为已定事实，本次收敛为无交叉冲突的族结构；指针冲突**显式裁定**（见 §五.2 收口记录，共 8 项）。
5. **C10 缺保护登记不独立立项**：19 条随本体工单映射（§九），作为修复批的强制护栏义务（护栏 + gate 表改写同批）。
6. **跨单元**：移交/家族关联/批次对齐单列于 §七，不改变本单元工单归属（除 MA-07 整族移交单列外）；与 WI 1.1–2.5 台账**零重复立项**（五维复核 + C10 复核三重对账，见 §七声明）。
7. **CL×77**：逐条处置为 并入工单 / 独立线索 / watch / 驳回（§八）；独立线索不计入工单数 N。

## 二、缺陷族结构（MA-01…MA-21，21 族吸收 97 条）

> 成员「严重度」= 五维复核最终严重度；现状标注全部为「新发现」，各表不再重复。**族严重度 = 取最严**。

### MA-01 流式主链族（callStream 发布器/订阅链）— 族严重度 **P1**

- **根因**：`ChatServiceImpl.callStream`/`aggregateStreamToResponse` 发布器-订阅链的生命周期、背压、承载与盘写治理整体缺失（同一物理站点、同一修复 PR 面）。
- **修复点**：①publisher 关闭绑定取消/终态（含 onNext 容错 + closeExceptionally 兜底）②背压窗口与订阅 request 上界 ③缺省承载换平台受管池 ④DefaultChatLogger 盘写异步化（脱敏/正则/写盘移出调用线程）⑤聚合累积与键累加器上界。
- **成员（7）**：C3-001（P1 取消单向悬挂）、C3-002（P1 背压缺失共享池饥饿）、C3-003（P1 日志 I/O 前置悬挂）、C3-009（P2 commonPool 承载阻塞 I/O）、C4-001（P2 缺省 publisher 承载 + 终态同步盘写）、C4-002（P1 取消路径 publisher 永不关闭）、C5-001（P1 聚合无界累积 + 双层 request(MAX_VALUE) + 线上可控键无上界）。
- **家族关联（不并案）**：C7-001（聚合层期限/静默可检测性，独立工单、修复批次协同）、C7-002（终止信号语义，已划界）、CL-029/CL-031/CL-057/CL-016/CL-001（见 §八）。
- **跨单元批次对齐**：C3-009 承接 WI 2.1 CL-021 移交面（裸 JDK 载体）具体化，修复批次对齐平台设施替换（§七）。

### MA-02 引擎注入目标登记/执行槽窗口族 — 族严重度 **P1**

- **根因**：`runningInjectionTargets` 登记不对称（注册→dispatch 双窗口 + resume/wake/restore 三恢复路径零注册）+ 外层清理清单缺件 + 摘除先于终态保存 + `pendingInjections` 裸 ArrayList——注入目标生命周期登记与清理不完整。
- **修复点**：统一注入目标登记（双窗口 + 三恢复路径）+ dispatchExecution/doExecute 两窗口四件套清理镜像 + 终态保存完成后摘除 + pendingInjections 同步容器 + steering 队列有界化（CL-047/065 同 diff）。
- **成员（5）**：C1-008（P1，族首：三恢复路径零注册 × 裸 ArrayList）、C3-010（P1 注册→dispatch 窗口孤儿化 + 接管锁无限续命）、C3-014（P2 恢复路径注入目标缺失）、C1-009（P2 拒绝路径泄漏 + remove 先于 terminal-save）、C3-013（P2 injectInput 终局边界三面）。
- **并入线索**：CL-061（摘除先于终态保存 = C1-009 面 2 同点）、CL-023（enqueue 窗口取消丢失→随 C1-010，见独立清单）、CL-047/CL-065（steering 队列无上界，随族有界化）、CL-018（ensureSessionMailbox 锁内注册，随邮箱站点面治理）。
- **关联**：C1-010（独立工单，取消意图窗口）；C7-015（独立工单，restore 锁门）。

### MA-03 F4 引擎终态清理族 — 族严重度 **P1**（族首 C5-015）

- **根因**（C5 复核裁定，本裁定采纳）：引擎终局清理清单仅含 `checkpointManager.remove`（9 调用点，DB 后端落空）+ `runningExecutions` + `runningInjectionTargets`，无一触达 session store / memory provider / 邮箱双表 / 安全治理表 / background registry / reply 订阅 / team 存储——同一处修复点（终态清理挂钩扩清单 + 各容器 remove 接线）的多个受害载体。C5-019 已驳回退出本族（其容器为全模块唯一已正确接线 releaseSession 者）；C1-026 按「桶骨架级」口径注记（P3 不变）。
- **修复点**：统一终态清理挂钩扩清单（TerminalCleanupRegistry 形态）+ 按站点面 remove 接线/补实现；gate3 表与清理对称性测试同批扩展（C10-003 强制护栏）。站点面即修复子点：
- **成员（29，按站点面）**：
  - **面 a 会话存储**（3）：**C5-015（P1，族首：三实现 put-only + 缺省装配 + 终态 save 反向 pin）**、C7-028（P2 终态会话工作集不退出，行为收敛面）、C5-016（P2 restore/listAll 全量物化，投影/过滤与族首同 diff）。
  - **面 b checkpoint**（3）：C5-009（P2 DBCheckpointManager 未覆写 remove）、C1-011（P2 同站点 C1 面）、C7-013（P2，复核降档收敛，非缺省面 P1 级增长率——排期权衡留 7.1）。
  - **面 c memory provider**（3）：C5-020（P2 接口无 remove 契约 + 缺省装配）、C1-016（P2 stores 无删除路径，引擎默认）、C7-030（P2 同站点收敛）。
  - **面 d 会话邮箱双表**（3）：C3-016（P1 sessionMailboxes + 订阅只增）、C5-012（P2 邮箱双表 put-only）、C1-014（P3 三账本面，跨面 d/e）。
  - **面 e 安全/治理注册表**（4）：C1-006（P2 DefaultDenialLedger 无 TTL 无上限，缺省装配）、C5-013（P3 四张 singleton 表仅 resume 路径 reset）、C7-017（P2 治理注册表族无终态清理，缺省即有状态）、C3-017（P2 陈旧 deadline 击穿 WAIT_FOR——IWaitCoordinator 生命周期协议缺失的语义面）。
  - **面 f BackgroundTaskRegistry**（5，主处置 C4-012）：C4-012（P1 账本无界 + timeoutMs 无执行点 + Error 面）、C3-023（P1 同站点 C3 面）、C7-027（P1 缺省装配面）、C5-021（P2 put-only）、C1-017（P2 终态 Entry 永不淘汰 8KB/条）。并入线索：CL-027（stop/run 双窗口）、CL-072（timeout seam 未闭合 = C4-012 同点确认）。
  - **面 g replySubscriptions**（5）：C3-024（P1 × DB 轮询线性放大）、C7-026（P1 增长乘法链）、C4-016（P2，三重登记 P1 意向留 7.1，族严重度已 P1 不受影响）、C5-018（P2 caller 可控键只增不撤）、C1-018（P3 只增不删）。修复：引用计数撤订 + 消费即撤 + 轮询模型治理。
  - **面 h team 存储（opt-in）**（2）：C1-024（P3 TeamTaskStore 终态任务永久累积）、C5-022（P3，降档，装配真实 store + daemon 即回 P2）。并入线索：CL-052（disband 永驻 + sessionToTeamId 滞留 + DB team 行）。
  - **面 i writeIntent（口径注记）**（1）：C1-026（P3 路径键只增 + O(N) 扫描——按 C5-019 驳回裁定改为桶骨架级理解，随 F4 批顺带）。
- **跨单元对照**（不重复立项，Phase 8 同族统一清理策略）：WI 1.1 C1-006、WI 1.6 C7-001、WI 1.7 C7-003（账本只增不删族，载体与模块不同）。

### MA-04 spawn 编排池泄漏族（TeamExecuteFlowExecutor per-invocation orchestrator 池）— 族严重度 **P1**

- **根因**：orchestrator/spawn 池 per-invocation 构造且 close 零调用点（惰性 fixed 池），每次调用确定性泄漏。
- **修复点**：池上提为受管共享设施或接线 close 生命周期；随池上提一并加惰性建池同步（CL-036）。
- **成员（3）**：C4-010（P1，主处置：auto-spawn 文档化正常路径 × 每调用泄漏）、C3-022（P1 异步终局不对称面）、C7-032（P2 分叉登记——**本裁定随族收敛 P1**，依据 C4-010 论证可辩护 + C7 复核倾向收敛，备案 §五.2）。
- **并入线索**：CL-036（resolveSpawnExecutor 惰性建池 check-then-act，随池上提改造加同步）。**关联**：C4-015（独立工单，stop 无 drain）；C10-007 护栏捆绑。

### MA-05 成员/spawn 超时取消契约族 — 族严重度 **P1**

- **根因**：三站点超时只放弃等待不取消底层执行（F3 漏修族：fan-out BOUND 已修，单成员/spawn 两兄弟缺失）——僵尸成员执行永不终止，持续占用线程与配额。
- **修复点**：MemberAgentTaskStep:152 / TeamTaskFlowOrchestrator:646-667 / DefaultMemberSpawner:188-198 三站点补 per-member orTimeout + cancelSession 对冲（照抄 MemberFanOutDispatcher:329-336 模板）；三案同 diff。
- **成员（4）**：C7-022（P1 单成员无期限且整体 deadline 不取消会话）、C7-023（P1 spawn 超时不取消 spawned 会话）、C3-025（P2 双执行窗面）、C4-014（P2 单 bound 裸 execute 面）。判级分叉（C7 P1 vs P2×2）经族取最严消解为 P1。
- **并入线索**：CL-007（DefaultMemberSpawner 超时不取消 = C7-023 同站点）。**护栏**：C10-007（正确兄弟已被钉、缺陷路径零断言）。

### MA-06 长会话归档/spill/journal 无界累积族 — 族严重度 **P1**

- **根因**：长会话持久累积面（checkpoint journal / 压缩快照归档 / spill store）无容量包络、无轮转、无读后驱逐——javadoc 自认 unbounded。
- **修复点**：journal 折叠/轮转上界 + 归档容量上界（驱逐最旧）+ spill 读后删除接线 + 归档 put 纳入容错包络（CL-077）；随案 HashMap→ConcurrentHashMap（CL-026/070）。
- **成员（4）**：C5-008（P1 journal 恢复 ~4× 物化 + byWatermark 全量常驻 + 磁盘无界）、C5-010（P1 每次压缩固化全量历史副本）、C5-011（P2 spill 全文本驻留读后不删）、C7-031（P2 复合登记——**本裁定按 C7 复核子面拆分归并：快照面随 C5-010、spill 面随 C5-011；不入 F4**，指针冲突裁定见 §五.2）。并入线索：CL-006/CL-063（journal 无轮转面 + ioLock 声明失真注记）、CL-026/CL-070（spill HashMap 线程安全随案）、CL-077（归档 put 在容错 try 外）。
- **护栏**：C10-017。

### MA-07 周期任务 Error 逃逸绝缘层移交族（跨单元移交单列）— 族严重度 **P2**

- **根因**：周期任务体 catch(Exception) 不含 Error → scheduleWithFixedDelay 一次 Throwable 静默永久停摆——WI 2.1 nop-kernel-concurrency-C3-001 平台绝缘层（29 站点族）在 nop-ai 的模块侧站点。
- **处置**：**整族移交 WI 2.1 C3-001 平台绝缘层统一修复，移交单列**（平台层落地即覆盖本单元六站点；本单元保留护栏义务 C10-008：Error 注入契约测试四处站点共用）。
- **成员（6）**：C3-020（P3 ScheduledRecoveryManager）、C3-027（P3 DBMessageService 双任务/兄弟收口面）、C4-003（P2 embed 重试 Error 零观测挂死）、C4-007（P2 续期/恢复周期任务 Error 静默停转）、C4-013（P2 poller/daemon Error 静默停转）、C7-029（P2 同站点收敛）。
- **交叉**：C4-007 与 MA-19（C7-012）同文件互补面，修复批次协同；C3-027 的 DBMessageService 串行面归 MA-11 交叉引用。

### MA-08 超时取消传播/幽灵执行族 — 族严重度 **P1**

- **根因**：超时/取消语义只作用于包装层（orTimeout/cancel(true) 不达底层非中断 I/O）或超时契约未接线——挂死工具/LLM 连接/重试幽灵持续占用线程，对健康检测不可见。
- **修复点**：统一取消传播契约——orTimeout 分支补底层 `cancel(true)` + `toolProgressTracker.cancelExecution` 通道对齐 abandon 路径；requestTimeout 全链接线；embedding 链 ICancelToken 贯通；call-agent async 取消协调。
- **成员（6）**：C3-011（P1 工具超时/取消不传播 + javadoc 失实）、C3-004（P2 幽灵重试）、C3-007（P2 requestTimeout 死契约，全模块零消费点）、C3-012（P2 LLM 幽灵调用）、C4-005（P2 embedding 链无取消通道）、C7-024（P2 call-agent async 无取消协调）。
- **并入线索**：CL-005（AgentToolDispatcher orTimeout 面 = C3-011 同点 + LlmCallCoordinator cancel 只中断面）。**关联**：CL-012 独立线索（无超时包装的等待点族）；C4-009/CL-064 独立工单（承载面）。

### MA-09 方言流式协议族（单修复批）— 族严重度 **P1**

- **根因**：五方言流式解析契约缺陷族：单事件多 entry 截断、usage 全链失活（通道建成源头断流）、空串抢占（与截断互为前提闭合）、带内错误路由缺失（错误载荷伪造/静默丢）。
- **修复点**：多 chunk 契约 + stream usage 采集（buildBody + 字段级合并）+ isEmpty 守卫 + error 事件出口，**单修复批** + 真实 ollama serve 抓帧样本回归；护栏 C10-002 参数化 fixture 矩阵同批。
- **成员（4）**：C3-028（P1 tool_call 单事件截断）、C3-030（P1 usage 全链路失活）、C3-031（P1 Ollama 空串抢占，与 028 两案必居其一）、C3-032（P2 带内错误路由缺失）。
- **关联**：C7-002（独立工单，终止信号语义，同批集成回归）。

### MA-10 会话存储装载/自愈/移除并发族 — 族严重度 **P2**

- **根因**：会话存储 per-session 锁纪律不完整——装载全程锁外、误 heal 删除已登记 delta、整表替换发散、租户模式绕过缓存多实例 last-writer-wins、remove 摘锁致锁身份更换。
- **修复点**：统一 per-session 装载锁模板（C1 复核 P2 修复顺位首位）+ heal 读-判-删入锁 + 租户键缓存设计 + remove 不摘锁/锁内复查（CL-028/039/067 同 diff）。
- **成员（3）**：C1-021（P2 锁外装载 + 误 heal 删 delta，修复顺位首位）、C1-012（P2 空读窗口 + 整表替换）、C1-020（P2 租户模式丢注入，与 C1-021 统一存储锁设计）。并入线索：CL-028/CL-039/CL-067（remove 摘锁会话复活，三重登记归并）。
- **护栏**：C10-004。

### MA-11 DBMessageService 治理族 — 族严重度 **P1**

- **根因**：DB 传输实现四轴治理缺失（串行度/守护/毒消息/订阅窃取）——单线程 poller 内联消费、毒消息无限重投队列停摆、订阅静默顶替与身份窃取。模块交付的完整 DB 传输实现，触发面即设计场景，不适用休眠降档（C3-021 复核判例）。
- **修复点**：handler 移出 poller 线程（或 handler 级 orTimeout + 滞后指标）+ maxDeliveryAttempts/DLQ 对齐 InMemory 兄弟 + subscribe 拒绝重复/cancel 身份校验 + suspend/resume 接线 + claim 世代 fencing。
- **成员（3）**：C3-021（P1 单线程 poller 内联嵌套委派确定性失败）、C7-025（P1 毒消息无限重投 + 队列停摆）、C1-019（P2 consumers 静默替换 + cancel 无身份窃取）。
- **并入线索**：CL-040（单线程互饿 = C3-021 主输入）、CL-024（订阅三缺陷）、CL-069（claim 无世代 fencing + suspend 死标志）。**交叉**：C3-027 Error 停摆面归 MA-07 移交单。

### MA-12 运行期换装/volatile 清扫族 — 族严重度 **P2**

- **根因**：配置/执行器换装面 plain 字段 + 公开 setter 无可见性保障 + 旧资源（调度器/池）换装后不回收——非对称锁与孤儿资源。
- **修复点**：全家族一次清扫 volatile 化 + 旧调度器 shutdown 生命周期钩子（IoC @Deinit/AutoCloseable）；护栏 C10-013（换装后旧资源 shutdown 断言）。
- **成员（4）**：C1-003（P2 7 个 static 非 final 单例换装面）、C1-004（P3 retryScheduler 换装废弃不 shutdown）、C1-013（P3 agentExecutor/lockRenewExecutor 非对称锁 + 孤儿池）、C1-022（P3 批次 D 7 组 plain 字段）。并入线索：CL-014/CL-044/CL-059（Embedding 调度器生命周期三重登记归并）、CL-013 注记（static 面交叉，主处置随 C7-011 工单）。
- **同案**：C1-003 ↔ WI 2.1 CL-030；C1-013/C1-022 ↔ WI 2.2 C1-007（休眠面校准一致，不重复立项）。

### MA-13 惰性记忆化/惰性发布族 — 族严重度 **P2**

- **根因**：共享缓存模型惰性记忆化非原子非 volatile（重复构造 + memoization 失效）；构造后变异对象惰性发布（休眠面）。
- **修复点**：volatile 化或去 memoization（同源修法 WI 2.1 CL-035）。
- **成员（2）**：C1-001（P2 PromptTemplateManager 非原子惰性记忆化）、C1-002（P2 PromptOutputModel 惰性发布，休眠面）。并入线索：CL-015/CL-042（同站点 check-then-act 双建，归并）。护栏 C10-013/C10-014。

### MA-14 复合操作 check-then-act（F10）族 — 族严重度 **P2**

- **根因**：检查-然后-行动复合操作无原子性（配额突破/过期键回删/EMA 丢失更新）。
- **修复点**：逐站点原子化（quota 判定入 compute、unregister 全局全序、AtomicDouble 语义）；护栏 C10-012（barrier 并发用例，本项目已示范可行）。
- **成员（3）**：C1-023（P2 配额检查-然后-行动突破）、C1-025（P2 unregister 过期键快照回删）、C1-015（P3 EMA 读-改-写丢失更新）。并入线索：CL-019（订阅句柄 check-then-act）、CL-032（actor 配额 check-then-act）、CL-062（EMA 同点归并）。

### MA-15 租户上下文传播族 — 族严重度 **P2**

- **根因**：异步完成链/取消路径上租户上下文清除不对称（清除后不恢复、嵌套清除）。
- **修复点**：统一 save-restore 租户上下文包装（resume/wake/cancel/fan-out 四兄弟参数化遍历，护栏随 C10-019）。
- **成员（2）**：C3-015（P2 cancelSession 租户清除）、C3-026（P2 fan-out 完成链租户嵌套清除）。

### MA-16 续期调度器族 — 族严重度 **P2**

- **根因**：租约续期单线程调度器承担 DB I/O——队头阻塞使租约裕量（3 倍）失准。
- **修复点**：专用小型多线程调度池（**不得指向 globalTimer**——WI 2.1 C4-001 修复落地前禁用，批次先后留 7.1）。
- **成员（2）**：C4-008（P2 主处置：续期单线程队头阻塞）、C7-019（P2 单线程续期调度器，同站收敛随单）。

### MA-17 沙箱进程/reader 清理族 — 族严重度 **P2**

- **根因**：Docker 沙箱清理链不对称——超时不 destroy 本地进程、interrupt 对管道 read 无效、kill 失败 reader 线程滞留（与 NoOp 兄弟 F3 分叉）。
- **修复点**：超时 destroyForcibly + reader 退出补偿（缓冲上限 + 强制关闭）+ cleanup 异常可见化；护栏 C10-011（docker 门内超时 kill 用例）。
- **成员（2）**：C1-007（P2 超时不 destroy + interrupt 无效）、C7-020（P2 reader 线程泄漏 + 空输出）。并入线索：CL-034（清理链进程级残留，同站点）。

### MA-18 恢复链路预算/收敛族 — 族严重度 **P2**

- **根因**：orphan 恢复 fire-and-forget 无重试预算、结果不可见、无收敛。
- **修复点**：恢复任务预算 + 结果观测 + 失败升级；护栏 C10-018（orphan 恢复预算断言）。
- **成员（2）**：C3-018（P2 恢复 fire-and-forget 静默）、C7-016（P2 orphan RESUME 无重试预算）。

### MA-19 接管锁 fencing 加固族 — 族严重度 **P1**

- **根因**：DbSessionTakeoverLock 续期异常结局类型链窄化（RuntimeException 落入无限容忍分支）+ 期限判定墙钟敏感——非对称分区下双执行。
- **修复点**：异常计失败→超限 abort + 可注入单调钟；与 C4-007（MA-07 移交面）同文件协同。
- **成员（2）**：C7-012（P1，条件面：DB 锁装配 + 多实例）、C7-018（P2 租约期限墙钟）。护栏 C10-010。
- **跨单元**：↔ WI 1.6 fencing 族**家族关联不并案**（C7 复核裁定维持）。

### MA-20 文件操作符读取限额族 — 族严重度 **P2**

- **根因**：窗口/限额参数未作用于读取面——切片先整读、分页整读、grep 整读、globGrep 无限制遍历。
- **修复点**：limit 语义下沉至读取面（读取字节/条数上界断言护栏，C10-015）；globGrep maxFileCount 守卫。
- **成员（2）**：C5-004（P2 全量物化族）、C7-010（P2 窗口读退化全量读，同判级收敛）。并入线索：CL-058（globGrep 无限制，同站）。

### MA-21 聊天日志保留期族 — 族严重度 **P2**

- **根因**：请求/响应日志逐请求双文件落盘、按日期分片但全模块无 TTL/轮转/保留期。
- **修复点**：保留期/轮转策略（或声明运维边界 + owner doc 包络）；随 MA-01 盘写异步化同文件分账实施。
- **成员（2）**：C5-003（P2 双文件落盘无轮转）、C7-009（P3 无保留期——P3/P2 镜头分叉留 7.1 校准，工单严重度已按取最严落 P2）。并入线索：CL-030（同站点无 TTL）。

## 三、独立立案清单（29 条）

| # | ID | 严重度 | 一句话 | 并入线索/注记 |
|---|----|--------|--------|--------------|
| 1 | C1-005 | P2 | DefaultPermissionProvider 共享单例 per-session 规则写副作用（零生产实例化降档） | 去状态化修复；激活即回 P1；口径先例 WI 2.5 C7-005 |
| 2 | C1-010 | P2 | cancelSession 装配窗口 TOCTOU 取消意图静默丢失 | 并入 CL-023（enqueue 窗口取消丢失，同根同修：取消标志绑定后自补） |
| 3 | C3-005 | P2 | 同步抛出逃逸包络（returnExceptionAsResponse 契约） | 护栏随 C10-019 |
| 4 | C3-006 | P2 | 缓存读写语义不对称 | — |
| 5 | C3-019 | P3 | 逃生舱路径批提交无隔离 | 触发前提限定 P3 |
| 6 | C3-029 | P2 | 审计/记账副作用中止主流程 | — |
| 7 | C4-004 | P2 | 重试调度器无关闭路径 | 修复指向受 globalTimer 批次先后约束（§五.1） |
| 8 | C4-006 | P2 | call-agent 同池嵌套（有界池注入条件下面 P1） | 条件升档口径留 7.1（§五.1）；家族对照 WI 1.5 MA-03 |
| 9 | C4-009 | P2 | SingleTurnExecutor 缺省 commonPool | 并入 CL-064（agentExecutor 兜底超时任务承载，同批专用池治理）；生产主路径不受影响（公开构造面） |
| 10 | C4-011 | **P1** | AgentDispatchExecutor 工具体阻塞占用 commonPool（生产主路径） | 并入 CL-025（commonPool 桥接同站点）；与 WI 1.2 C4-004 / WI 2.3 C8-001 同族判级一致 |
| 11 | C4-015 | P2 | spawn 池 stop 无 drain 丢已提交工作 | 与 MA-04 池上提同批；护栏 C10-007 |
| 12 | C5-002 | P2 | AI 响应缓存磁盘无界（TTL 缺省关 + 无容量上界） | 并入 CL-041（promptName 路径穿越，同文件安全面） |
| 13 | C5-005 | P3 | Embedding 响应全量物化 + 装箱树 5-8× 放大 | — |
| 14 | C5-006 | P3 | 系统提示词缓存无容量 + workMode 键空间无界 | 并入 CL-043（workMode 穿越读，同站同参数） |
| 15 | C5-007 | P3 | AiXDefHelper xdef:ref 循环引用无环守卫 SOE | — |
| 16 | C5-014 | P3 | AgentActor.receivedMessages 观测日志无界 + 快照全拷贝 | — |
| 17 | C5-017 | P2 | DeferredAckMailbox deadLetter 无界永驻 + inFlight 无崩溃回收 | 并入 CL-010/CL-037（同站点双登记归并）；对齐 DB 兄弟 sweep |
| 18 | C7-001 | **P1** | 流式聚合无期限/静默不可见 | 家族关联 MA-01（同链异层，修复批次协同，不并案） |
| 19 | C7-002 | **P1** | 干净半关闭误判成功交付截断内容 | 家族关联 MA-01/MA-09（终止信号语义，集成抓帧回归同批） |
| 20 | C7-003 | P2 | Retry-After 无上限 | 护栏 C10-018 |
| 21 | C7-004 | P2 | 熔断/冷却墙钟敏感 | 注入时钟护栏（TestProviderFailoverQueue 模式） |
| 22 | C7-005 | P2 | breaker probe 逃逸无身份令牌 | 护栏 C10-018 |
| 23 | C7-006 | P2 | AiCommand 零退避重试 + 绕过限流 | — |
| 24 | C7-007 | P2 | 失败记忆表无界（条件面：model 名可达性） | 护栏 C10-018 |
| 25 | C7-008 | P3 | 响应缓存毒条目无自愈 | 与 C5-002 分账（声明默认 vs 容量面，C7 复核裁定） |
| 26 | C7-011 | P3 | secret 负缓存永驻 | 并入 CL-013（secretCache 锁内 I/O + static 面，同站点工单收口） |
| 27 | C7-014 | P2 | heartbeat wakeup 无预算 + 永久入史 | 与 R4 反向钉死改写同批 |
| 28 | C7-015 | P2 | restoreSession 锁前变更共享状态（F3 漏修兄弟） | TestSessionLifecycleLockGate 补 restore 锁门用例 |
| 29 | C7-021 | P3 | plan untyped 失败零退避热循环 | — |

## 四、watch-only 清单（2 条）

| ID | 严重度 | 理由 | 转立案触发条件 |
|----|--------|------|---------------|
| C1-027 | P3 | DefaultFencingTokenService 双计数器无删除/复位——**休眠面**（全库零生产接线，仅 javadoc 引用与测试门清单）；任务指针清单未列其 F4 成员位 | 任一生产接线接入 fencing 注册表即转 MA-03 新站点面；若 F4 修复批触及该类则顺带收口 |
| C3-008 | P2 | callStream 晚订阅竞态——初审即「理论风险标注合规」（复核维持），触发需发布器复用 + 晚订阅组合 | callStream 契约扩展（多订阅者模型）或 MA-01 修复后仍可复现即转独立立案；护栏义务随 C10-001 |

## 五、开放裁定与 7.1 移交项

### 5.1 留 7.1 终裁（任务指派 5 项，全部不阻塞工单落地）

1. **三组严重度分叉**（工单严重度均已按取最严落地，分叉仅为台账标签校准）：
   - C3-027/C4-013 P3/P2 → MA-07 移交单内标签校准（移交单严重度 P2 已定）；
   - C4-016 三重登记 P1 意向 → MA-03 面 g 站点内 C4-016 标签（工单已 P1）；
   - C7-009↔C5-003 P3/P2 → MA-21 镜头校准（容量面 vs 保留期面，工单已 P2）。
2. **globalTimer 修复批次先后**：C4-004（独立工单）与 MA-16（C4-008/C7-019）的修复建议在 WI 2.1 C4-001（globalTimer 死配置 + 4 线程共享）落地前**不得指向 globalTimer**——先平台后使用方，否则新增占用方复刻 MA-04/C8-001 形态。
3. **C4-006 条件升档口径**：有界池注入条件下面 P1 级——口径先例对齐（缺省 cached 池不死锁仅线程放大，降档有证据）；激活条款随条目。

### 5.2 本裁定收口记录（指针冲突显式裁定，8 项）

| # | 冲突 | 裁定 |
|---|------|------|
| 1 | C7-001 入/不入流式主链族（任务指针列为族成员 vs C7 复核「同链异层不并案」） | **独立工单 + 家族关联 MA-01**（修复批次协同）；C10-001 护栏面不变 |
| 2 | C7-031 归属（任务指针列 F4 族 vs C7 复核子面拆分随 C5-010/011） | **归 MA-06 归档/spill 族**（同根因同修复批：持久累积无上界），不入 F4 终态清理族 |
| 3 | C3-003 归属（任务指针未列 vs C3 复核修复批列入 publisher 三轴并单） | **并入 MA-01**（盘写异步化轴，与 C4-001 终态盘写同站点 DefaultChatLogger） |
| 4 | C3-027 双归属（C3 复核 DBMessageService 四轴工单 vs 任务指针平台绝缘层移交） | **Error 停摆面归 MA-07 移交单**（主）；MA-11 交叉引用其串行面 |
| 5 | C1-027 F4 归属（C1 终表标 F4 族 vs 任务 F4 成员清单未列） | **watch-only**（休眠面），不占 MA-03 工单位，转立案条件见 §四 |
| 6 | BackgroundTaskRegistry 五登记独立成族 vs F4 站点面（C5 归并建议） | **F4 站点面 f**（主处置 C4-012），不另立族——避免同一容器双工单 |
| 7 | spawn 三重登记判级（C3-022/C4-010 P1 vs C7-032 P2 分叉） | **统一 P1**（C4-010 论证可辩护 + C7 复核倾向收敛 + 族取最严），备案 7.1 |
| 8 | C7-022/023 ↔ C3-025/C4-014 判级分叉（P1 vs P2×2） | **族内取最严 P1 消解**（僵尸执行持久性论据成立），成员标签备查不改档 |

另确认采纳 C10 复核修正 M2（C3-024 主归属补入 C10-003）、M3（C3-008/C3-009 主归属定 C10-001）。

### 5.3 跨单元移交项（详见 §七）

- MA-07（6 条）整族移交 WI 2.1 C3-001 平台绝缘层，移交单列；
- C3-009（MA-01 成员）/C3-012（MA-08 成员）→ WI 2.1 CL-021 移交面具体化，承接确认、修复批次对齐，不离族；
- C7-012（MA-19）↔ WI 1.6 fencing 族家族关联不并案。

## 六、权威汇总表

**合并前 128 条 → 合并后 50 工单 = 21 缺陷族吸收 97 条 + 29 独立 + 2 watch-only。**

| 维度 | 保留条数 | 族吸收 | 独立 | watch |
|------|---------|--------|------|-------|
| C1 | 27 | 24 | 2（C1-005/C1-010） | 1（C1-027） |
| C3 | 32 | 27 | 4（C3-005/006/019/029） | 1（C3-008） |
| C4 | 16 | 11 | 5（C4-004/006/009/011/015） | 0 |
| C5 | 21 | 15 | 6（C5-002/005/006/007/014/017） | 0 |
| C7 | 32 | 20 | 12（C7-001…008/011/014/015/021 中 12 条，见 §三） | 0 |
| **合计** | **128** | **97** | **29** | **2** |

**工单严重度分布（族严重度取最严后）**：P0×0 / **P1×13**（MA-01/02/03/04/05/06/08/09/11/19 + 独立 C4-011、C7-001、C7-002）/ **P2×29**（MA-07/10/12/13/14/15/16/17/18/20/21 + 独立 18）/ **P3×8**（独立 C3-019、C5-005/006/007/014、C7-008/011/021）。合计 13+29+8 = 50 ✓。

**逐条落位无遗漏声明**：128 条保留发现每条恰好落入一处——97 条吸收进 MA-01…MA-21（§二成员清单逐条可数）、29 条独立立案（§三）、2 条 watch-only（§四）；无重复计数（C1-014 单条跨邮箱/治理两面、C7-031 单条跨快照/spill 两面均只在族内计一次）。5 个分维度计数复核：C1 24+2+1=27、C3 27+4+1=32、C4 11+5=16、C5 15+6=21、C7 20+12=32。

**供 cra-index 登记的计数口径**：合并裁定后 50 工单（21 缺陷族 MA-01~21 吸收 97 条 + 29 独立；P1×13/P2×29/P3×8，P0×0 即时通道显式空执行）；watch-only 2 条（C1-027/C3-008，附转立案触发条件）；C10 缺保护 19 条单列随本体工单映射；CL 77 条全部落位（并入工单 46 / 独立线索 29 / watch 2 / 驳回 0，见 §八）；跨单元移交 1 族单列（MA-07 六条 → WI 2.1 C3-001）+ 批次协同单列 2 处（C3-009/C3-012 → CL-021 具体化、C7-012 ↔ WI 1.6 家族关联）；与 WI 1.1–2.5 台账零重复立项。

## 七、跨单元对账

| 关系类型 | 本单元载体 | 对侧 | 处置 |
|---------|-----------|------|------|
| **移交（单列）** | MA-07（C3-020/027 + C4-003/007/013 + C7-029，6 条） | WI 2.1 nop-kernel-concurrency-C3-001 周期任务异常绝缘层（29 站点族） | 整族移交，本单元保留 Error 注入护栏义务（C10-008）；严重度随平台裁定，移交单暂计 P2 |
| 批次协同（单列） | C3-009（MA-01 成员）、C3-012（MA-08 成员） | WI 2.1 CL-021 裸 JDK 载体移交面（点名 ai-agent） | 承接确认：本单元具体化站点，修复批次对齐平台设施替换；不离族不重复立项 |
| 家族关联（不并案） | MA-19（C7-012/C7-018） | WI 1.6 fencing/租约族（C3-003 续租缺失、C1-001 CAS 守卫等） | 载体与模块不同，修复模式对齐，不并案 |
| 同案（校准一致，不立项） | C1-013/C1-022 ↔ WI 2.2 C1-007；C1-001 ↔ WI 2.1 CL-035；C1-003 ↔ WI 2.1 CL-030；C1-019/C1-025 ↔ WI 2.4 CL-415（watch 级平台侧） | 平台侧登记在案 | 模块侧立案、平台侧 watch/已登记，判级校准一致，无重复修复面 |
| 同族对照（Phase 8 对齐） | F4 族 ↔ WI 1.1 C1-006 / WI 1.6 C7-001 / WI 1.7 C7-003；C3-003 ↔ WI 2.4 C3-001；C3-002 ↔ WI 1.2 C4-004；C4-006 ↔ WI 1.5 MA-03；C4-011 ↔ WI 1.2 C4-004 / WI 2.3 C8-001；C7-004/018 ↔ 墙钟敏感族各单元 P2 先例；C3-025 面 1 ↔ WI 1.6 C3-005；C4-002 失效面④ ↔ WI 2.3 C3-003 | 各单元台账 | 载体/站点/模块不同，零重复立项，修复批次按族对齐模式 |
| 防混淆登记 | 本单元 CL-022（AgentCallDelegate join 依赖） | WI 2.3 CL-022（nop-message JDK Delayer 11 站点） | **编号巧合不同案**；协同点在 IAgentMessenger/IMessageService 契约层 |
| 移交（线索级） | CL-075（middleware 伪 lifecyclePoint，非并发域） | 功能正确性线 | 独立线索移交落名 |
| **零重复声明** | 五维复核 + C10 复核三重对账 cra-index WI 1.1–2.5（P0/P1 台账 + P2 裁决 + CL 台账）逐条比对 | — | **无任何同单元重复立项**；历史 nop-ai-agent 审计（2026-06）属功能/依赖/测试 campaign，未登记本批并发面载体；全部命中为跨维同站指针或跨单元同族对照 |

## 八、CL 线索处置表（77 条全部落位：并入工单 46 / 独立线索 29 / watch 2 / 驳回 0）

> 处置类别：**并入** = 并入 §二/§三 工单（随单修复）；**独立** = 独立线索台账（不计工单数 N）；**watch** = watch-only 线索。独立线索编号 CL-IND-x 为本裁定新增的归并桶。

### 8.1 并入工单（46）

| CL | 来源/建议归维 | 一句话 | 落点 |
|----|--------------|--------|------|
| CL-001 | C1/C3 | callStream 内部 subscriber onNext 抛异常 publisher 永不闭合 → future 永挂 | 并入 MA-01（onNext 容错 + closeExceptionally 兜底） |
| CL-005 | C1/C3/C6 | 工具/LLM 超时只取消包装 Future 不取消底层 | 并入 MA-08（= C3-011 同点） |
| CL-006 | C1/C2/C6 | CheckpointJournalWriter 单 ioLock 声明失真 + journal append-only 无轮转 | 并入 MA-06（轮转面主）；锁声明面随 CL-IND-02 同批声明修订 |
| CL-007 | C1/C3/C6 | DefaultMemberSpawner 超时不取消 spawned 执行 | 并入 MA-05（= C7-023 同站点） |
| CL-010 | C1/C6/C8 | DeferredAckMailbox deadLetter 无界无清理 | 并入 C5-017 独立工单 |
| CL-013 | C3/C2 | LlmConfigHelper secretCache computeIfAbsent 锁内文件 I/O + static 无同步 | 并入 C7-011 独立工单；static 换装面与 MA-12 交叉 |
| CL-014 | C3/C8 | EmbeddingServiceImpl retryScheduler 无生命周期关闭 | 并入 MA-12（= C1-004 同站点） |
| CL-015 | C1 | PromptTemplateManager check-then-act 双建实例 | 并入 MA-13（= C1-001 同点） |
| CL-016 | C9 | DefaultChatLogger 每请求同步双文件写 + 全文正则脱敏 | 并入 MA-01（盘写异步化轴） |
| CL-018 | C2/C8 | ensureSessionMailbox computeIfAbsent 锁内 registerHandler（代理 I/O） | 并入 MA-03 面 d（随邮箱站点面治理） |
| CL-019 | C1 | AgentCallDelegate 订阅句柄检查-后-行动泄漏旧订阅 | 并入 MA-14（F10 族） |
| CL-023 | C3 | enqueue 窗口 forced cancel 中断静默丢失 | 并入 C1-010 独立工单（取消标志绑定后自补） |
| CL-024 | C3 | DBMessageService 订阅三缺陷（顶替/窃取/suspend 死标志） | 并入 MA-11（C1-019 同点 + suspend 面） |
| CL-025 | C6/C9 | AgentDispatchExecutor commonPool 桥接无界 runAsync | 并入 C4-011 独立工单（同站点） |
| CL-026 | C3/C1 | InMemorySpillStore HashMap 无同步 + 无上界 | 并入 MA-06（随案 CHM 化） |
| CL-027 | C3 | DefaultBackgroundTaskRegistry stop/run 双窗口 | 并入 MA-03 面 f（同文件治理） |
| CL-028 | C3/C1 | FileBackedSessionStore.remove 摘锁会话复活 | 并入 MA-10（三重登记之一） |
| CL-029 | C4/C3 | aggregateStreamToResponse 返回 future 取消不传播 | 并入 MA-01（聚合层取消绑定） |
| CL-030 | C4/C8 | log-dir 逐请求落盘无 TTL/清理 | 并入 MA-21（= C5-003/C7-009 同站） |
| CL-032 | C4/C2 | InMemoryActorRuntime 配额 check-then-act | 并入 MA-14（F10 族） |
| CL-034 | C6 | DockerSandboxBackend 清理链进程级残留 | 并入 MA-17（同站点） |
| CL-036 | C4/C2 | resolveSpawnExecutor 惰性建池 check-then-act | 并入 MA-04（随池上提加同步） |
| CL-037 | C8/F4 | DeferredAckMailbox 缺省无界 + deadLetter 永驻 | 并入 C5-017 独立工单 |
| CL-039 | C4/C2 | FileBackedSessionStore.remove 持锁摘锁 | 并入 MA-10（三重登记之二） |
| CL-040 | C4/F5 | DBMessageService 单线程 poller 互饿 + 挂死不可见 | 并入 MA-11（= C3-021 主输入） |
| CL-041 | C5/C9 | 响应缓存 promptName 路径穿越 | 并入 C5-002 独立工单（同文件安全面） |
| CL-042 | C5/C2 | PromptTemplateManager 竞态（= CL-015 同点） | 并入 MA-13（归并） |
| CL-043 | C5/C9 | DefaultSystemPromptLoader workMode 穿越读 | 并入 C5-006 独立工单（同站同参数） |
| CL-044 | C5/C6 | retryScheduler 无关闭（= CL-014 同点） | 并入 MA-12（归并） |
| CL-046 | C5/C6/C8 | FAILED actor executors/registry 条目滞留 | 并入 MA-03（actor 注册表清理面，与 C5-013 一并收口） |
| CL-047 | C5/C2 | steeringQueue 无界 ConcurrentLinkedQueue | 并入 MA-02（注入链路有界化同 diff） |
| CL-052 | C5/F4 | InMemoryTeamManager disband 永驻 + sessionToTeamId 滞留 | 并入 MA-03 面 h（team 存储面） |
| CL-057 | C7/C6 | StreamAggregator 无上界（= C5-001 面） | 并入 MA-01（归并） |
| CL-058 | C7/C6 | globGrep 无限制遍历 + grepFiles 全驻留 | 并入 MA-20（= C5-004/C7-010 同站） |
| CL-059 | C7/C8 | embedding 调度器生命周期（= CL-014 三登记） | 并入 MA-12（归并） |
| CL-061 | C7/C2 | injectInput 摘除先于终态保存丢写窗口 | 并入 MA-02（= C1-009 面 2 同点） |
| CL-062 | C7/C2 | EMA volatile 非原子读改写（= C1-015 同点） | 并入 MA-14（归并） |
| CL-063 | C7/C2/C4 | CheckpointJournalWriter ioLock（= CL-006 同点） | 并入 MA-06（归并；声明面随 CL-IND-02） |
| CL-064 | C7/C4 | agentExecutor 无界 cached 兜底超时任务 | 并入 C4-009 独立工单（timeoutExecutor 承载治理） |
| CL-065 | C7/C6 | steeringQueue 无界（= CL-047 同点） | 并入 MA-02（归并） |
| CL-066 | C7/C8 | WaitCoordinator 延迟唤醒任务不可取消 | 并入 MA-03 面 e（IWaitCoordinator 生命周期协议 = C3-017 同修复点） |
| CL-067 | C7/C2 | sessionLocks 摘锁（= CL-028/039 三登记） | 并入 MA-10（归并） |
| CL-069 | C7/C2 | DBMessageService claim 无世代 fencing + suspend 死标志 | 并入 MA-11（fencing 面 + suspend 面与 CL-024 合并） |
| CL-070 | C7/C6/F4 | spill/双归档 HashMap 非线程安全 | 并入 MA-06（随案 CHM 化，与 CL-026 归并） |
| CL-072 | C7/C4/C8 | BackgroundTaskRegistry timeoutMs 无强制者 | 并入 MA-03 面 f（= C4-012 timeout 面同点确认） |
| CL-077 | C3/C6 | 归档 put 在 compact 容错 try 外，违背 Design §8.3 声明 | 并入 MA-06（归档容错包络） |

### 8.2 独立线索台账（29，不计工单数）

| 桶/CL | 归维 | 一句话 | 备注 |
|-------|------|--------|------|
| **CL-IND-01**（CL-008/CL-038/CL-049/CL-050/CL-051/CL-071） | C6/C5/F4 | DB 自建表保留策略族：ai_agent_message CONSUMED 行、ai_agent_denial、usage 表、team/team_task 行 append-only 无 TTL/归档，store-layer-contract 清理责任违约 | 一个线索工单：统一保留期策略 + 契约修订（或声明运维边界）；与 C5-009（MA-03 b）/C5-017 修复批对齐 |
| **CL-IND-02**（CL-009 + CL-006/063 声明面） | C2 | javadoc 并发契约失真族（InMemoryAiMemoryStore「synchronized(this)」不实、CheckpointJournalWriter per-file 锁不实） | 文档一致性批次；与批次 A #12（ThresholdBreaker）同族第三例 |
| **CL-IND-03**（CL-011/CL-076） | C8/C9 | 正则重复编译清扫族：guardrail 三站点 + dialect 错误映射路径 | 构造期 Pattern.compile 缓存；与 fail-loud 纪律对齐 |
| **CL-IND-04**（CL-012 + LLMCurator 兄弟） | C3 | 超时必须作用于所有等待点：Layer3FullSummaryStrategy/LlmCompletionJudge/LLMCurator 直调 chatService.call 无超时包装 | 套用 callChatWithTimeout 模板；与 MA-08 修复批协同 |
| CL-002 | C1/C6 | 响应缓存文件 save 非原子撕裂（F2/F6） | 可随 C5-002 工单同 diff 顺带（temp+ATOMIC_MOVE） |
| CL-003 | C1/C9 | PlanExecutor 异常路径 running 中态无 finally 复位（休眠面） | 激活前提同 C1-005 |
| CL-004 | C1/C9 | PlanScheduler 重复 taskNo 静默合并，与 DagBuilder 契约分叉（休眠面） | — |
| CL-017 | C2 | ThresholdBreaker 迟到旧探测驱动状态机（近似语义） | 接受现状须 javadoc 声明 |
| CL-020 | C6/C9 | DefaultAgentEventPublisher 同步扇出慢订阅者拖关键路径 | 治理评估项（异步派发或契约声明） |
| CL-021 | C2/C6 | destroyActorInternal 终局 finally 内同步 awaitTermination 5s | 与 C3-013/C3-016 相对面，评估异步化 |
| CL-022 | C3 | handleCallAgentRequest orTimeout().join 单线程派发死锁依赖（契约缺失） | 与 WI 2.3 CL-022 编号巧合不同案，协同点 messenger 契约层 |
| CL-033 | C6/C9 | 自建线程命名无索引后缀 + owner doc `nop-ai.md:123` 声明不符 | reconcile-on-touch 同任务修正 |
| CL-045 | C6/C8 | DefaultAgentEventPublisher.failureCounts 永驻（P3 量级） | 随订阅者包装对象回收 |
| CL-048 | C5 | recovery 三处 SELECT 无 LIMIT（峰值万级 ID 清单） | 补 LIMIT + 分批；与 MA-03 a 恢复扫描批顺带评估 |
| CL-053/CL-068 | C5/C9 | tryAppend O(prefix) 全量 JSON 指纹重算 + getMessages 全拷贝 O(n²)（归并一条） | 量化后立案；fingerprint identity 缓存方案 |
| CL-054 | C5/F7 | AdapterBackedAiMemoryStore 读路径全量 loadAll 放大 | 与 C5-020/C7-030（MA-03 c）释放契约修复同批评估投影下推 |
| CL-055 | C2 | ChatRequest 就地变更（makeSessionId/setRequestTime）并发竞态 | — |
| CL-056 | C2 | AiCommand 实例可变字段无单次使用契约 | 生命周期核查项 |
| CL-060 | C9/F9 | AiCommand 缓存加载失败吞异常仅 INFO 无 cause | WARN + cause；与 C7-008 毒条目可诊断性关联 |
| CL-073 | C2 | AgentSession.seenInputKeys LinkedHashMap 无同步 | 依赖调用方线程模型证据，随 MA-02 注入链路治理核实 |
| CL-074 | F6 | SessionFileWriter 原子 rename 无 fsync（断电模型未声明） | 与 CL-006/063 统一声明 durability = process-crash 模型 |
| CL-075 | 功能域 | executeExecutionMiddleware 硬编码伪 lifecyclePoint | **移交功能正确性线**（非并发域） |

### 8.3 watch（2）

| CL | 一句话 | 理由 |
|----|--------|------|
| CL-031 | StreamAggregator 包络内累积（单响应 token 上界内）+ 双通道翻倍累积疑虑 | 声明包络内；双通道真实输入待方言层证据 |
| CL-035 | InMemoryActorRuntime 消费循环 Error 逃逸僵尸 actor（opt-in + 引擎 finally 兜底，后果轻一档） | 初审自评 watch-only；随 MA-07 绝缘层模式评估顺带 |

## 九、C10 缺保护登记映射（19 条随本体工单，不独立立项）

> C10 复核修正 M2/M3 已采纳（C3-024 主归属 C10-003；C3-008/C3-009 主归属 C10-001，C10-019 成员 16→14）。登记条目即对应工单的**强制护栏义务**：修复批同步补护栏 + gate 表改写同批。

| C10 | 保护缺口 | 映射工单 |
|-----|---------|---------|
| C10-001 | 流式主链零 active 测试（TestChatServiceImpl @Disabled） | MA-01 + 独立 C7-001/C7-002（含 C3-008 watch 面与 C3-009 承载面） |
| C10-002 | 方言流式 fixture 矩阵（单 entry/零 usage/零 error/空串掩蔽） | MA-09 |
| C10-003 | F4 终态清理断言止于 checkpoint 单件 + gate3 钉不完整清单 | MA-03（24 成员修正后清单） |
| C10-004 | 会话存储并发装载/自愈/租户旁路零并发用例 | MA-10（+C5-016@MA-03 a） |
| C10-005 | 引擎注入/装配窗/恢复前置零并发用例 | MA-02 + 独立 C1-010、C7-015 |
| C10-006 | 超时「会触发」被钉、「底层是否停」零断言 | MA-08 + 独立 C4-009 |
| C10-007 | team-flow/spawn 僵尸与池泄漏零断言（正确兄弟被钉） | MA-04/MA-05 + 独立 C4-015 |
| C10-008 | 周期任务 Error 逃逸零注入用例 | MA-07（移交单护栏义务随本单元保留） |
| C10-009 | DBMessageService 治理四轴零用例（sweep 被钉） | MA-11 |
| C10-010 | 租约异常分支与墙钟轴零用例 | MA-19 + MA-16 |
| C10-011 | 沙箱超时 destroy 与孙进程管道零用例 | MA-17 |
| C10-012 | 复合操作竞态全单线程断言 | MA-14（+C1-026@MA-03 i） |
| C10-013 | 换装面/静态单例可见性与生命周期零断言 | MA-12/MA-13 + 独立 C4-004 |
| C10-014 | 休眠账本与惰性发布零断言轴 | MA-13（C1-002）、MA-03 h（C1-024/C5-022）、watch C1-027 |
| C10-015 | 磁盘无界四件套容量/轮转/保留期零断言 + LocalFileOperator 零测试 | MA-20/MA-21 + 独立 C5-002/C5-006/C7-008/C7-011 |
| C10-016 | 心跳/等待治理预算与陈旧唤醒轴零断言 | 独立 C7-014 + MA-03 e（C3-017） |
| C10-017 | 会话内归档与 journal 物化容量上界零断言 | MA-06 |
| C10-018 | 可靠性策略面上界/身份/预算零断言 | 独立 C7-003/004/005/006/007/021 + MA-18（C7-016） |
| C10-019 | 引擎杂项并发轴（14 成员，M3 修正后） | 独立 C3-005/006/015（MA-15）/018（MA-18）/019/029、C4-006/011、C5-005/007/014/017、MA-03 g（C5-018）；C3-008/C3-009 主归属 C10-001 |

---

**裁定终局**：128 条 → **50 工单**（21 族吸收 97 + 29 独立 + 2 watch）；工单 P0×0 / P1×13 / P2×29 / P3×8；跨单元移交 1 族（6 条 → WI 2.1 C3-001 平台绝缘层）+ 批次协同单列 2 处；指针冲突显式裁定 8 项（§5.2）；留 7.1 开放裁定 5 项（§5.1，均不阻塞工单落地）；CL 77 条全部落位（46 并入 / 29 独立线索 / 2 watch / 0 驳回）；C10 19 条随本体工单映射闭合。
