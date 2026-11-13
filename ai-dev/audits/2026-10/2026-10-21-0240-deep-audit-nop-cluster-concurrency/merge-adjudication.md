# 单元内合并裁定 — nop-cluster（WI 4.2）

- **裁定人**: CRA WI 4.2 独立合并裁定员（与初审/深挖/复核不同 task_id）
- **裁定日期**: 2026-10-20
- **基线**: commit 120ff53d0d（worktree nop-entropy-concurrency-audit，分支 concurrency-audit-remediation）
- **Status**: closed（本单元合并裁定终稿；判级分歧与 owner 裁决项按 §五 移交 7.1）

## 一、裁定口径与输入

**输入**（仅以下材料，未读其他单元审计目录详情）：

1. 五维度文件 `## 最终保留项` + `## 维度复核结论` 节：`C1-shared-mutable-state.md`、`C2-lock-sync.md`、`C3-async-chains.md`、`C4-thread-pools.md`、`C8-backpressure.md`。
2. 深挖 round2 文件（`C1-round2.md`～`C8-round2.md`，取 R2 新增条目与扩展并入、CL-018～021）。
3. `C10-test-protection.md`（保护力矩阵 43 行 + 缺保护登记 14 条 + C10 复核结论，R1 并入执行后口径 P2×4/P3×10）。
4. `ai-dev/audits/cra-index.md`（P1 台账 WI 1.1–4.1 累计 133 条 + CL 台账）。
5. `dispatch-log.md`（派发口径、基线、CL-024 漂移复查输入）。

**三态判定规则**：每条保留发现恰好落入一处——①缺陷族（≥2 条同根因同修复点，合成单工单）②独立立案（单发现工单）③watch-only（登记不立案）。工单判级 = 主登记判级；并入成员判级保留为注记，不参与工单计数。条件化/回升条款随工单整体携带。

**复核已登记裁定（作为输入直接采纳，共 8 处）**：
- 并入 5 处：C8-004→C3-002、C8-008→C3-008、C8-009→C1-006、C8-010→C3-003、C3-010→C1-007。
- 双轨 1 处：C4-001→C1-004 主登记 P1（调度句柄面 C2-003 同工单）。
- 并入 1 处：C4-002→C2-003（守护空转面，机制勘误保留）。
- 拆分 1 处：C8-005 三面拆分——(b) cleanupTasks→C3-006、(c) 订阅泄漏→C3-005、(a) 缓存无界+永不退订保留为本体。

**单元计数口径**：复核后保留 43 条（去重后 P0×0/P1×4/P2×23/P3×12）。五文件登记实数 P1×4/P2×22/P3×13（39）+ C8 驳回并入 4 行 = 43 行矩阵闭合；与派发口径的 ±1 为 C3-010 计级归类差异，按 C10 复核 §一.2 闭合，合并后视图（成员计注记不计数）中该差异消解。

**本裁定在复核登记之外的扩员**（判据：同类/同方法/同机制 + 单 diff 可覆盖 + 同一 C10 保护族）：C3-004、C2-005 扩入 MA-01；C3-006 扩入 MA-02；C8-005 本体扩入 MA-03；C8-003、C3-012 扩入 MA-04；C3-008 扩入 MA-05；C4-005、C8-011 扩入 MA-06；C8-002、C3-009 扩入 MA-07。共 13 条新吸收（7 条为复核已登记并入）。

## 二、缺陷族结构（编号 MA-01 起）

### MA-01 选主生命周期并发纪律族（P1）

- **主登记**: C1-004（P1，stop 复活+监听器乱序）
- **成员**: C4-001（P2，双轨并入——复核登记）、C2-003（P2，调度句柄面同工单——复核登记）、C3-004（P2，promise 等待者时序）、C2-005（P2，complete 持锁派发）、C4-002（P3，isDestroyed 守卫空转——复核登记并入 C2-003 面）
- **根因**: `AbstractLeaderElector`/`AbstractPollingLeaderElector` 状态机并发纪律缺失——状态写站点锁覆盖不全（5 写站点仅 2 有锁）；stop 后 pending 调度无撤销手段且 allowRestart=true（LifeCycleSupport:26/:107-108）致复活；electionPromise 生命周期无守护（原地 cancel 旧 promise/重启不重建/newElection 无同步替换）；complete 在临界区内同步派发等待者 continuation；scheduleCheck 销毁守护空转（BindScheduledExecutor 不转发 isDestroyed）。
- **同一修复点**: 单 diff elector 并发治理——全部状态写站点统一锁 + stopped 终态守卫（阻断 stop 后复活与完整重选主/DB 抢主）+ pendingCheck Future 保留与撤销 + promise 重建纳入 stop/start 生命周期 + `promise.complete()` 取局部引用移出临界区 + BindScheduledExecutor.isDestroyed 转发。
- **协同约束**: C2-005 与 C2-003 互为前置——简单「全站点加 synchronized」会放大 complete 持锁面（C2-round2 明文警告）；C4-002 的 isDestroyed 转发与独立工单 C4-004 的监督改造同涉及 BindScheduledExecutor（两工单协同，不合并）。
- **回归断言面**（修复验收门禁）: 「停机后无 lease 行重写、无尾部 reschedule」（C4 复核点名）+ C10-003 建议用例 (1)(2)。CL-005（轮询回路停止句柄）并入本族 pending 撤销面。
- **工单判级**: P1（单元 P1 之一）。

### MA-02 cleanupTasks 治理族（P2，条件化回升 P1）

- **主登记**: C1-002（P2 条件化，读侧丢任务/CME）
- **成员**: C2-001（P2 条件化，锁不对称——复核已明文与 C1-002 同 diff）、C3-006（P2 条件化，事件线程内联执行；C8-005 面(b) 已拆分并入此处）
- **根因**: `ServiceInstance` cleanupTasks 机制整体未治理——写读锁不对称（`addCleanupTask:289` synchronized vs `getCleanupTasks:296` 裸读）致丢任务/CME；任务在 Nacos 事件分发线程内联 `task.run`（`NacosNamingService:268-276`）致慢清理阻塞全平台服务发现事件。
- **同一修复点**: 单 diff——cleanupTasks 结构 COW/锁对称化 + 执行卸载至独立执行器（事件线程只入队）。
- **条件化**: `addCleanupTask` 全仓零生产调用方（复核 grep 证实）——接线即整体回升 P1。
- **测试族**: C10-006 建议 (3)(4)。

### MA-03 Nacos 订阅配对与实例缓存治理族（P2）

- **主登记**: C3-005（P2，订阅泄漏，含 round2 NacosConfigService 兄弟站点扩展）
- **成员**: C8-005（P2，收缩后单面——instanceCache 裸 CHM 无淘汰 `:46` + 正常路径永不退订 `:194-197` 条件性无界；getServices 截断子注记随同，吸收 CL-004）
- **根因**: 订阅/缓存生命周期无配对——`catch(NacosException)` 分类不备致失败路径不回滚退订（naming getServiceInfo + config getConfigAndSignListener 两站点）；正常路径订阅钉存 + 缓存无淘汰钩子 → 键空间无界配置下条件性无界。
- **同一修复点**: 单 diff——异常分类放宽为 Exception + subscribe/register 后失败路径统一回滚退订 + LocalCache 有界化 + 淘汰钩子触发退订 + getServices 截断告警。
- **判级注记**: C8-005 无界为条件校准非降档（需显式配置无界键空间/网关转发形态）。
- **测试族**: C10-005（P2，含 R1 并入的 C1-003/C3-011 断言面）。

### MA-04 BroadcastRpcClient 休眠治理族（P2，携回升 P1 条款）

- **主登记**: C3-002（P2 条件化，广播半个成功）
- **成员**: C8-004（P2——复核登记并入）、C8-003（P2，扇出无上界，休眠条件化）、C3-012（P3，发射中断孤儿腿）
- **根因**: `BroadcastRpcClient.callServersAsync` 扇出链路三面缺陷——waitAll=allOf 首败即全败、不取消兄弟腿、成功响应 get(0) 丢弃；并发扇出无上界；发射循环无 per-leg 隔离（第 k 腿同步抛出→已发射腿成不可取消幽灵请求）。
- **同一修复点**: 单类单 diff——聚合语义（per-server 明细+失败聚合）+ 失败路径统一 cancel + 发射循环 per-leg try/catch + 并发窗口上界。
- **条件化**: BroadcastRpcProxyFactoryBean 全仓零接线（复核双 grep）——声明 broadcast proxy 即回升 P1，并同时暴露 C10-002 零测试面（回升与补测试同批）。
- **测试族**: C10-002（单测试类盖四面）。

### MA-05 Sentinel runAsync 加固族（P2）

- **主登记**: C3-007（P2，entry 泄漏——exit 唯一挂接 future 完成事件）
- **成员**: C3-008（P3，同步逃逸）、C8-008（P3——复核登记并入 C3-008，统计漏计面 traceEntry 三路径不对称随同 diff 强制要求）
- **根因**: `SentinelFlowControlRunner.runAsync` 异常/生命周期路径加固缺失——永不完成 future 无兜底 exit；Supplier 同步异常裸逃逸（非失败 future、无 traceEntry）。
- **同一修复点**: runAsync 单 diff——同步段 catch + `Tracer.traceEntry` + `FutureHelper.reject`；orTimeout/超时装饰兜底 exit 配对。
- **强制项**: 修复必先改写反向钉死测试 `testSupplierSyncThrowPropagatesSynchronously`（硬反向钉死 #1，同 diff 解除并以「rejected future + 异常统计计入」新断言钉定）。
- **测试族**: C10-009。

### MA-06 AutoRegistration 生命周期治理族（P3）

- **主登记**: C1-007（P3，stop 与定时刷新无序；吸收 C3-010——复核登记）
- **成员**: C3-010（P3——复核登记并入）、C4-005（P3，start 两步获取无回滚）、C8-011（P3，stop 注销失败无兜底 ghost）
- **根因**: `AutoRegistration` 生命周期状态机无终态守卫、无补偿——stop×在途刷新竞态（cancel(false) 不中断、instance 非同步读写，当前被 no-op updateInstance 掩蔽）；start 两步外部获取零回滚（register 成功后 schedule 失败→无刷新残留注册）；stop 注销单次无重试无兜底（ephemeral=false 永久 ghost）。三失效面机制独立（C8-011 本体自证为同族），修复为同一状态机改造。
- **同一修复点**: 单类单 diff——stopped 标志统一守卫（stop 后拒绝 refresh 写回）+ start 失败回滚 unregister（或调换为先建链后注册）+ stop 注销 try/catch 兜底（error 带标识 + 健康标志 + 有限重试）。
- **条件标注**: C4-005/C8-011 为 `nop.cluster.registration.enabled` feature 门控（休眠非零接线，不降档）；C1-007 潜伏零后果现状注记。
- **测试族**: C10-011（建议 (3) 以并发 stop×refresh 用例替代弱 sleep 断言）。

### MA-07 ClusterRpcClient 调用链重试/取消治理族（P1）

- **主登记**: C3-001（P1，重试异常吞没→future 永不完成挂死；live 默认装配路径——AbstractRpcProxyFactoryBean 缺省 parent 证实）
- **成员**: C8-002（P2，重试零退避零抖动风暴）、C3-009（P3，返回 future 与 cancelToken 无反向联动）
- **根因**: `ClusterRpcClient.callAsync:60-94`/`call:96-126` 重试递归链三缺陷——重试跳 whenComplete 内同步异常被吞（手工 future 永不完成）；重试立即递归无退避无抖动；取消不向底层 cancelToken 传播（幽灵请求）。
- **同一修复点**: 单 diff 重试循环重写——重试体 try/catch + `completeExceptionally` 终结、退避+抖动调度、future.cancel→cancelToken 联动。
- **同批**: 独立工单 C3-003（MA-09）同为 ClusterRpc 调用链加固，共用 C10-001 测试族与 Mock IRpcService 基建（两工单协同，修复点不同文件不合并）。
- **跨单元**: future 永不完成族关联单列（§七）。
- **测试族**: C10-001（建议 (1)(3)(4) 直接钉定本族三面）。

### MA-08 权重守护与排流语义族（P2）

- **主登记**: C1-006（P2，零权重总和未守护；吸收 C8-009 全部三项扩展——复核登记）
- **成员**: C8-009（P3→并入——复核登记）
- **根因**: 零/负权重输入无守护链——`nextInt(ttlWeight)` 对 ttlWeight<=0 崩溃（WeightedRandomLoadBalance:28-31）；输入面 `copyFromInstance:289` `(int)` 截断使合法 0.5 权重静默归零；权重快照逐元素读 C1-001 可变字段弱化触发前提；`WeightedPartitionAssigner.getWeight:94-99` 将 weight<=0 静默改写为 100，使 Nacos 标准排流语义在分区分配面失效（LB 尊重 0、Assigner 改写 0，语义相反）。
- **同一修复点**: 守卫条件 `<=0` 回退 + copyFromInstance 非整数权重 round/告警 + Assigner 零权重语义按 owner 裁决落地（§五）。
- **强制项**: 反向钉死 #3（`TestWeightedPartitionAssigner.testZeroAndNegativeWeight:251-270` 明文钉定「0/-50→各得 100」缺陷契约）同 diff 改写为 owner 裁决后的语义契约。
- **测试族**: C10-007 建议 (1)(4)。

### MA-09 无主防护类型化族（P2）

- **主登记**: C3-003（P2，MasterServerChooser 无主返回 null 违反 IServerChooser:29 契约）
- **成员**: C8-010（P3→并入——复核登记；裸 NPE 不可归因面）
- **根因**: 无主降级路径双 null 返回点违约 + 异步链下游零 null 防护 → 裸 NPE，失败语义未类型化。
- **同一修复点**: null → 类型化错误（`ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE` 形态）+ 下游契约守护。
- **测试族**: C10-001 建议 (2)（与 MA-07 同测试族）。

## 三、独立立案清单

13 张独立工单（单发现，修复点独占，无同根因成员；括注内为随同处置的 CL/跨单元关联）：

| # | 工单 | 判级 | 一句话 | 关键修复点 | 关联 |
|---|------|------|--------|-----------|------|
| 1 | C1-001 | **P1** | Nacos 事件线程原地改写共享 ServiceInstance + `_host` 永久缓存陈旧端点（失效面 12 项） | updateInstances:251-254 重建对象替换元素 + `_host` 失效（getHost:172-178/toString:167-170 副作用消除） | MA-08 触发前提弱化源、CL-021 放大源 |
| 2 | C1-003 | **P1** | 订阅初始化快照可回退已生效监听事件态（乱序窗口为本模块代码段自有） | subscribe→getAllInstances→initInstances:186-192 编排加版本守护/单调合并 | C10-005 R1 并入断言面 |
| 3 | C1-005 | P2 | destroy 与懒初始化复活竞态（3 站点：shutdown 后重建 Nacos 客户端并泄漏） | destroyed 标志快速失败 + 禁止懒重建（getNamingService:65-78/destroy:101-120） | shutdown 懒重建族跨单元关联（§七）；CL-006（NacosConfigService close 竞态，失效面③ 展开）随同案修复 |
| 4 | C2-002 | P2 | computeIfAbsent:175 映射函数内 subscribe+getAllInstances 两次远程调用（发现热路径 bin 锁内重 I/O） | 装载锁外化/异步装载 | 与 C1-008 的 computeIfAbsentAsync 收敛同型；C10-005 建议 (4) |
| 5 | C4-003 | P2 | 裸 globalTimer（5 线程共享池）交给 PollingRpcClient + 轮询链无整体截止 | executeOn 卸载 + 可选整体 deadline（两注入点 ClusterRpcProxyFactoryBean:43/ClusterRpcServiceInvoker:68） | globalTimer 族跨单元关联（§七）；C10-013 |
| 6 | C4-004 | P2 | 选主/注册两条自续调度链「单点断链、无监督」（rejection/Error 即永久静默死亡） | 保留 pending future + whenComplete 告警 + 重臂 | 自续链断链族跨单元关联（§七）；CL-011（基类守护）+ CL-014（业务标识失败上报）随同批；MA-01 C4-002 协同 |
| 7 | C8-001 | P2 | LeastActive 活跃数背压信号恒 0（getActiveCount 恒 0 + javadoc 矛盾），过载感知路由整面失效 | 真实活跃计数实现（形态为 owner 裁决项，§五） | CL-017（roundRobin 误绑 random 装配勘误）随本工单处置；C10-007 建议 (2) |
| 8 | C8-006 | P2 | PartitionResolver 失败/空结果无负缓存——依赖故障期退化为逐调用远端查询（含 round2 异常路径扩展） | 短 TTL 负缓存 / stale 回退 + 异常路径同盖；fail-closed 语义 owner 裁决 | C10-008 |
| 9 | C8-007 | P2 | SentinelRuleConfig 热更新单向棘轮（四族 isEmpty 跳过 + 单族解析失败静默保留旧规则） | isEmpty→显式清空 + 失败聚合上报（参数化 4 族） | C10-010 |
| 10 | C1-008 | P3（条件化回升 P2） | CachingNamingService 同步/异步双路径缓存不连贯（async 旁路 put 旧覆新） | `computeIfAbsentAsync` 收敛原子装载 | CL-009（async miss 无并发去重，同修）随本工单；全仓零装配——装配启用即回升 |
| 11 | C1-009 | P3 | RoundRobinLoadBalance 单例共享 AtomicInteger seq——轮询公平性未按服务隔离（相位锁步可 100% 偏斜） | per-serviceName 发号（CHM 计数器，注意下线清理配对）或 javadoc 声明边界 | 与 C8-001 工单 CL-017 修复协同裁决（C1-round2 明文）；C10-007 建议 (3) |
| 12 | C1-010 | P3 | IDiscoveryClient:35/IServerChooser:36 契约声明「可修改」vs 实现 immutableList/emptyList/List.of() 三违约 | 以实现现状修契约文本（一处覆盖两接口三违约站点） | C10-014（契约测试防双向漂移）；与 C1-001/C8-005 修复方向一致性注记 |
| 13 | C3-011 | P3 | NacosConfigService 配置监听器推送线程内联业务回调且无异常隔离（更新静默丢失，自愈性支持 P3） | onUpdateConfig 回调 try/catch 隔离 + 降级日志 | C10-005（R1 同条收录，P3 不改条目定级） |

## 四、watch-only 清单

| 条目 | 判级 | 不立案理由 | 复评触发条件 |
|------|------|-----------|-------------|
| C2-004 | P3（备查级） | NacosNamingService 单监视器承载 SDK 重初始化：双检写法正确（volatile+synchronized）、锁序无环（BIN→LSN→SINFO 复核确认）、低频粗锁无正确性后果——复核裁定「自我豁免登记口径恰当」 | 重初始化频率因配置热更新机制演进显著上升，或 `:230/:238` 锁内出现 I/O/用户回调时重评 |

watch 1 条。CL 侧另有 3 条 watch（CL-013/015/018，见 §八），不计入本体。

## 五、开放裁定与 7.1 移交项

| # | 事项 | 现状 | 移交请求 |
|---|------|------|---------|
| 1 | C4-001 P1↔P2 双轨终裁 | 已按 C1-004 主登记 P1、C4-001 P2 双轨并入 MA-01 同 diff（与 WI 1.2 C3-006 型、WI 4.1 C1-001 双轨型同款；五子项逐环回码成立，启用面内必现不适用条件化降档） | 7.1 形式确认双轨裁定 |
| 2 | C2-005 公开 API 潜在面 | P2 按潜在面校准维持（whenElectionCompleted 生产调用方为零——全仓仅测试，JobCoordinator:584 显式禁用；与 C3-004 判例「公开 API 契约缺陷 + 机制确定 → 注记不降级」一致）；归属 MA-01 工单内子项 | 潜在面标注复认；若维持，MA-01 修复不含本子项时的 deferred 决定需带 successor |
| 3 | Assigner 零权重语义 owner 裁决 | MA-08 内 C8-009 扩展点 1：尊重 weight=0 须回答「0 权重实例的分区给谁」（可能需下限语义而非改写 100） | owner 裁决后落地；反向钉死 #3 测试改写以裁决结论为契约 |
| 4 | C8-001 活跃计数实现形态 | 真实活跃计数注入形态（Dubbo beginCount/endCount 型）未定；实现后现 LB 测试全绿且无 leastActive 语义验证 | owner 裁决实现形态；C10-007 建议 (2) 同批钉定 |
| 5 | globalTimer 族修复归属 | C4-003 为消费端 opt-in 新站点，单列不重复；隔离/卸载方案须与平台族统一（WI 1.5 MA-04、WI 2.1 C4-001、WI 2.3 C8-001、WI 3.1 C4-011） | 平台族隔离方案统一裁决时纳入本工单 |
| 6 | owner doc 零覆盖收口 | `docs-for-ai/01-repo-map/module-groups.md` 对 nop-cluster/nop-rpc-cluster/nop-hazelcast 0 提及——无模块级并发契约；CL-020（Sentinel per-node 流控语义未声明）为同一收口面 | 补行或显式裁定 No owner-doc update；契约暂以 javadoc 为准 |

## 六、权威汇总表（合并前 43 条 → 合并后 22 工单 + watch 1）

**工单分布**: P1×4 / P2×13 / P3×5 = 22 张工单 + watch 1。**43 条落位**: 9 主登记 + 20 并入成员（7 条复核已登记 + 13 条本裁定新增吸收）+ 13 独立 + 1 watch = 43，逐条无遗漏。

| # | 发现 | 复核终判 | 落位 | 身份 | 备注 |
|---|------|---------|------|------|------|
| 1 | C1-001 | P1 | 独立工单 #1 | 主 | live 面 |
| 2 | C1-002 | P2（条件化） | MA-02 | 主 | 接线回升 P1 |
| 3 | C1-003 | P1 | 独立工单 #2 | 主 | C10-005 R1 并入承载 |
| 4 | C1-004 | P1 | MA-01 | 主 | 单元 P1 |
| 5 | C1-005 | P2 | 独立工单 #3 | 主 | +CL-006 |
| 6 | C1-006 | P2 | MA-08 | 主 | 反向钉死 #3 同 diff |
| 7 | C1-007 | P3 | MA-06 | 主 | 吸收 C3-010 |
| 8 | C1-008 | P3（条件化） | 独立工单 #10 | 主 | 装配回升 P2；+CL-009 |
| 9 | C1-009 | P3 | 独立工单 #11 | 主 | 与 CL-017 修复协同 |
| 10 | C1-010 | P3 | 独立工单 #12 | 主 | 契约文本修复 |
| 11 | C2-001 | P2（条件化） | MA-02 | 成员 | 与 C1-002 同 diff |
| 12 | C2-002 | P2 | 独立工单 #4 | 主 | live 面 |
| 13 | C2-003 | P2 | MA-01 | 成员 | 调度句柄面同工单（复核登记口径） |
| 14 | C2-004 | P3 | **watch** | — | 备查级自我豁免 |
| 15 | C2-005 | P2 | MA-01 | 成员 | 潜在面注记（§五-2） |
| 16 | C3-001 | P1 | MA-07 | 主 | 单元 P1；future 永不完成族 |
| 17 | C3-002 | P2（条件化） | MA-04 | 主 | 回升 P1 条款；吸收 C8-004 |
| 18 | C3-003 | P2 | MA-09 | 主 | 吸收 C8-010 |
| 19 | C3-004 | P2 | MA-01 | 成员 | promise 生命周期面 |
| 20 | C3-005 | P2 | MA-03 | 主 | 含 round2 config 站点扩展 |
| 21 | C3-006 | P2（条件化） | MA-02 | 成员 | C8-005 面(b) 拆入 |
| 22 | C3-007 | P2 | MA-05 | 主 | 反向钉死 #1 同 diff |
| 23 | C3-008 | P3 | MA-05 | 成员 | 吸收 C8-008 统计面 |
| 24 | C3-009 | P3 | MA-07 | 成员 | 取消联动面 |
| 25 | C3-010 | P3 | MA-06 | 成员 | 复核登记并入 C1-007 |
| 26 | C3-011 | P3 | 独立工单 #13 | 主 | C10-005 同条收录 |
| 27 | C3-012 | P3 | MA-04 | 成员 | 理论风险标注随族 |
| 28 | C4-001 | P2（双轨） | MA-01 | 成员 | 复核登记并入 C1-004；§五-1 |
| 29 | C4-002 | P3 | MA-01 | 成员 | 复核登记并入 C2-003 面 |
| 30 | C4-003 | P2 | 独立工单 #5 | 主 | globalTimer 族单列 |
| 31 | C4-004 | P2 | 独立工单 #6 | 主 | 自续链断链族单列；+CL-011/014 |
| 32 | C4-005 | P3 | MA-06 | 成员 | feature 门控注记 |
| 33 | C8-001 | P2 | 独立工单 #7 | 主 | +CL-017；owner 裁决（§五-4） |
| 34 | C8-002 | P2 | MA-07 | 成员 | 与 WI 2.3 C8-002 分档单列 |
| 35 | C8-003 | P2（条件化） | MA-04 | 成员 | 随 C3-002 回升条款 |
| 36 | C8-004 | P2 随主 | MA-04 | 成员 | 复核登记并入 C3-002 |
| 37 | C8-005 | P2 | MA-03 | 成员 | 拆分后单面；面(b)→MA-02、面(c)→MA-03 主 |
| 38 | C8-006 | P2 | 独立工单 #8 | 主 | 含 round2 异常路径扩展 |
| 39 | C8-007 | P2 | 独立工单 #9 | 主 | |
| 40 | C8-008 | P3 随主 | MA-05 | 成员 | 复核登记并入 C3-008 |
| 41 | C8-009 | P3 随主 | MA-08 | 成员 | 复核登记并入 C1-006（含三项扩展） |
| 42 | C8-010 | P3 随主 | MA-09 | 成员 | 复核登记并入 C3-003 |
| 43 | C8-011 | P3 | MA-06 | 成员 | feature 门控注记 |

**验算**: 主 9 + 成员 20 + 独立 13 + watch 1 = 43 ✓；工单 MA×9 + 独立×13 = 22 ✓。

## 七、跨单元对账

对 `cra-index.md` P1 台账（WI 1.1–4.1 累计 133 条）+ CL 台账全量查重：**零同站点重复立项**；家族关联 6 处单列、闭环 2 项、无同案确认 2 项。

| # | 类型 | 对账内容 | 裁定 |
|---|------|---------|------|
| 1 | 闭环 | WI 2.1 CL-024（per-client/topic 池线性累计→nop-cluster 移交） | R1/R2 双实测 + C3 复核独立 grep 复现：rpc-cluster 零自建池/线程（仅 globalTimer 共享 2 站点 + 每调用 new 包装对象），无同族形态——按「漂移复查/无同族」预期闭环，本单元零立案 |
| 2 | 闭环 | WI 2.1 CL-021（12+ raw JDK/Netty 载体绕过设施）移交点名 nop-stream/ai-agent/feishu | 点名不含本单元；本单元调度站点 4 处全部经平台设施（elector/AutoRegistration executeOn 正确卸载，2 站点裸 globalTimer 已由 C4-003 立案）——无接收义务 |
| 3 | 家族关联 | globalTimer 族：C4-003（独立工单 #5）↔ WI 1.5 MA-04 / WI 2.1 C4-001 / WI 2.3 C8-001 / WI 3.1 C4-011 | 消费端 opt-in 新站点，单列不重复；修复归属随平台族隔离方案统一裁决（§五-5）。注：输入口径列「WI 4.1 C4-003」在 cra-index 台账无同名 P1，疑引证漂移，留 7.1 对账 |
| 4 | 家族关联 | 自续链断链族：C4-004（独立工单 #6）+ CL-011 ↔ WI 2.1 C3-001（29 站点绝缘层，30 点表 #14）/ WI 1.6 C8-001 / WI 4.1 C8-002 | 两条链为新消费端站点，单列；建议随 WI 2.1 C3-001 平台方案同批 |
| 5 | 家族关联 | future 永不完成族：C3-001（MA-07 主）↔ WI 1.6 C8-012 / WI 3.2 C1-001 | 同族异站点，家族关联单列不重复 |
| 6 | 家族关联 | 监视器内外部代码族：C2-005（MA-01 成员）↔ WI 1.4 C2-001 | 同族不同载体（complete 派发 vs invoker 直调），单列 |
| 7 | 家族关联 | shutdown 懒重建族：C1-005（独立工单 #3）↔ WI 2.2 C1-009 | 同型（停机后在途路径重建资源），单列 |
| 8 | 家族关联 | 重试放大族：C8-002（MA-07 成员）↔ WI 2.3 C8-002（Gateway 重试放大 P1） | 同族相邻（重试无时间整形），分档独立成立（无 OOM 轨迹），单列 |
| 9 | 无同案 | WI 2.3 C8-005 令牌桶族 | 无同案——nop-cluster 无自建限流器（Sentinel 纯委托） |
| 10 | 无同案 | WI 3.4 C3-005 错误以成功交付族 | 无同案——本单元各失败路径显式上抛/异常完成，无「错误折叠为成功」形态 |

## 八、CL 线索处置表（CL×21 逐条落位）

处置统计：并入工单 7 / 重复合并 4 / 独立线索台账 7 / watch 3 = 21。

| CL | 维度 | 处置 | 落位与理由 |
|----|------|------|-----------|
| CL-001 | C9 | 独立线索台账（主登记） | RouteServiceInstanceFilter 热路径逐实例解析 NPM 约束 + INFO 日志；吸收 CL-007 重复登记 |
| CL-002 | C9 | 独立线索台账（主登记） | ConsistentHashLoadBalance 每请求重建整环（n×160 条目）；吸收 CL-016 重复登记 |
| CL-003 | C9 | **重复合并** | 与 CL-008 同站点（HttpRpcClientInstanceProvider 每调用新建）——复核已登记，并入 CL-008 桶 |
| CL-004 | C9 | 并入工单 | MA-03（C8-005 getServices 截断子注记随同 diff：截断告警/翻页） |
| CL-005 | C6 | 并入工单 | MA-01（pending 调度撤销面，与 C2-003 同站点；跨单元关联 WI 2.1 C3-001 30 点表 #14） |
| CL-006 | C6 | 并入工单 | 独立工单 #3 C1-005（NacosConfigService close 竞态 = C1-005 失效面③ 同案修复） |
| CL-007 | C9 | **重复合并** | 与 CL-001 同站点同内容（RouteServiceInstanceFilter:56-6x 逐实例解析）——**本裁定新发现重复**（复核仅登记 CL-003/008 一组），并入 CL-001 |
| CL-008 | C9 | 独立线索台账（桶主） | rpc-cluster 每调用/每请求分配面桶：吸收 CL-003（同站点）与 CL-012（ClusterRpcClient:137-152 重叠 + HttpRpcServiceInvoker:66-74 相邻站点 + HttpRpcService:46 日志）；修复时两入口站点一并盘点 |
| CL-009 | C9 | 并入工单 | 独立工单 #10 C1-008（同一修复：computeIfAbsentAsync/singleflight 原子装载收敛） |
| CL-010 | C6 | 独立线索台账 | HazelcastProvider 生命周期/可见性（@PostConstruct 前 null、字段非 volatile）——P3 级启动 wiring 面 |
| CL-011 | C7 | 并入工单 | 独立工单 #6 C4-004（同修：基类 finally 重调度守护/wrap Throwable；跨单元关联 WI 2.1 C3-001 30 点表 #14） |
| CL-012 | C9 | **重复合并** | 与 CL-003/008 同桶（rpc-cluster 分配面），保留 HttpRpcServiceInvoker:66-74 站点注记 |
| CL-013 | C1 线索 | **watch** | PartitionResolver 双 volatile 非原子对更新——后果限多一次幂等解析，本体自裁「理论风险不立案」 |
| CL-014 | C9 | 并入工单 | 独立工单 #6 C4-004（同批：常驻链失败带业务标识的上报点） |
| CL-015 | C5 | **watch** | retryCount 非 volatile 热更新——后果仅读旧值语义可接受；若热更新契约要求立即可见再升 volatile |
| CL-016 | C9 | **重复合并** | 与 CL-002 同站点（ConsistentHashLoadBalance 整环重建）——**本裁定新发现重复**，并入 CL-002 |
| CL-017 | C9/装配 | 并入工单 | 独立工单 #7 C8-001（复核裁定：装配勘误随 C8-001 修复批一并处置；顺带核查 RoundRobin AtomicInteger 共享语义→联动独立工单 #11 C1-009） |
| CL-018 | C1/C6 | **watch** | LeaderEpoch.getExpireAt 泄漏可变 Timestamp——仓内无消费方，模块外子类接入时改防御拷贝（复核维持 watch） |
| CL-019 | C7/F4 | 独立线索台账 | Sentinel 资源名基数无平台上界、Node 统计只增不减——告警阈值 + javadoc 契约 |
| CL-020 | C8 语义 | 独立线索台账 | Sentinel 仅本地单机流控、per-node 语义未声明——声明缺口；归属 §五-6 owner doc 收口 |
| CL-021 | C7/C8 | 独立线索台账 | 实例事件风暴无合并/限流（每事件全量 O(n log n) 重建）——关联 MA-02（C3-006 事件线程面）与 MA-03（缓存面），规模后果取决于推送频率，单列评估 |

> 重复登记勘误汇总：复核登记 1 组（CL-003=CL-008）+ 本裁定新发现 2 组（CL-007=CL-001、CL-016=CL-002）+ 同桶扩容 1 组（CL-012→CL-008 桶）。C9 热路径线索重复率高企源于五维度独立 overlay 扫描同一站点，后续单元建议初审即建单元级 CL 查重索引。

## 九、C10 缺保护登记映射（14 条 → 工单）

C10 复核 R1 执行后口径：P2×4（C10-001/003/005/006）/ P3×10；C1-003 并入 C10-005 升 P2、C3-011 同条收录。14 条全部映射至本体工单，映射即修复批验收门禁（每修一案必附「会红」回归测试）；43/43 保护面闭合。

| C10 | 级 | 保护对象（发现） | 随附工单 |
|-----|----|----------------|---------|
| C10-001 | P2 | C3-001、C3-003、C8-002、C3-009 | MA-07 + MA-09（Mock IRpcService 基建共用） |
| C10-002 | P3 | C3-002（含 C8-004）、C8-003、C3-012 | MA-04（随本体回升） |
| C10-003 | P2 | C1-004、C4-001、C2-003、C3-004、C2-005 | MA-01 全员（含「停机后无 lease 行重写」模块外断言面） |
| C10-004 | P3 | C4-004、C4-002 | 独立工单 #6 + MA-01（C4-002 注记） |
| C10-005 | P2（R1 升） | C1-005、C3-005、C8-005、C2-002 + R1 并入 C1-003、C3-011 | 独立工单 #3、MA-03、独立工单 #4、独立工单 #2、独立工单 #13 |
| C10-006 | P2 | C1-001、C1-002（C2-001 同 diff）、C3-006 | 独立工单 #1 + MA-02 |
| C10-007 | P3 | C1-006（含 C8-009）、C8-001、C1-009 | MA-08 + 独立工单 #7 + 独立工单 #11（含反向钉死 #3 解除） |
| C10-008 | P3 | C8-006 | 独立工单 #8 |
| C10-009 | P3 | C3-007、C3-008（含 C8-008 统计面） | MA-05（含反向钉死 #1 解除） |
| C10-010 | P3 | C8-007 | 独立工单 #9 |
| C10-011 | P3 | C1-007（含 C3-010）、C4-005、C8-011 | MA-06 全员 |
| C10-012 | P3 | C1-008 | 独立工单 #10（随本体回升） |
| C10-013 | P3 | C4-003 | 独立工单 #5（2 注入点参数化） |
| C10-014 | P3 | C1-010、C2-004 | 独立工单 #12 + watch C2-004（备查不要求测试形态） |

**修复批门禁**（C10 复核采纳）：优先补 C10-001/003/006 三个 P1 承载面；两处硬反向钉死（#1→MA-05、#3→MA-08）与结构性假保护（LeastActive 恒 0 adapter→独立工单 #7）列为对应工单同 diff 强制项。
