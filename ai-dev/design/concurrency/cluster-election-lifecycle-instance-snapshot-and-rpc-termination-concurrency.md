# cluster 选主生命周期/服务实例快照与 RPC 重试终结并发契约（R2-18 4.2 cluster 批 4+1 项裁定）

> Status: draft（plan 2521 Phase 1 产出物，独立 draft review 通过后转 active——转 active 后为该批实施唯一设计裁决权威）
> Source: 批次成员表、站点行号基线、PA 判定与勘误登记以执行计划 `ai-dev/plans/2521-cra-phase8.27-r2-18-cluster.md` 为准（本文档为最终状态文档，勘误不落此处）；成员发现详情 = WI 4.2 审计目录 `ai-dev/audits/2026-10/2026-10-21-0240-deep-audit-nop-cluster-concurrency/`（C1-shared-mutable-state/C1-round2/C2-lock-sync/C2-round2/C3-async-chains/C3-round2/merge-adjudication[MA-01 :32-45]/C10-test-protection/inventory-p2-c10 :525）；行号 = live @ `a1b3cc7e6c` 2026-11-01 全文亲读核实值，与 plan 起草基线（`a9fc5b01a7`）逐锚核对零漂移，漂移以 live 为准
> 实现锚：nop-cluster-core `elector/AbstractLeaderElector`（leaderEpoch volatile :32 / electionPromise volatile :33 / listeners COW :43 / doStart :109-117 / doStop :120-124[:121 cancel+:122 onStop+:123 clear，无锁] / whenElectionCompleted :157-159 / newElection :161-163[无锁] / onRestartElection :165-171[synchronized] / onElectionCompleted :173-181[:174 epoch 写+:176-178 isDone 重建+:180 complete] / onBecomeLeader :183-191 / onBecomeFollower :193-201 / onException :203-211 / onStop :213-225[:214-220 派发+:221 epoch null+:222-224 cancel，无锁]）、`elector/AbstractPollingLeaderElector`（javadoc :38-39 明文 stop/restart 生命周期面归 R2-18 / pendingCheck :53 保留 :103 / scheduleCheck(long) :88-122[executor.isDestroyed :91 / whenComplete :111-118：CancellationException 豁免 :112 + !isStopping :114] / runGuardedElectionRound :131-147[finally 重臂 :141] / rearmAfterBreakdown :150-154 / getPendingCheckForObservation :174-176）、`discovery/ServiceInstance`（cleanupTasks :151 / `_host` :153 / attrs CHM :158[javadoc :156-157「本地维护的临时信息，从NamingService获取的信息中不包含这些属性」] / toString :167-171 / getHost :172-178[:174-175 惰性缓存] / addCleanupTask synchronized :289-293 / setModifyIndex :322-324）、`ClusterErrors`（:18-19 ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE + :15 ARG_SERVICE_NAME）、nop-cluster-nacos `NacosNamingService`（类 :38 非 final / getNamingService :65-78[public 非 final] / getInstances :174-176 / getServiceInfo :178-199[:188 subscribe+:189 subscribed+:191 getAllInstances+:192 initInstances+:194-197 回滚] / onEvent :214-220 / initInstances :230-232[synchronized] / updateInstances :235-277[:238 synchronized+:249 old.getRight+:251 instanceText 比较+:252 setModifyIndex+:253 copyFromInstance+:263 map 换装+:264-265 不可变 List+:268-276 cleanup] / copyFromInstance static :280-304[:292-296 `@@` pos 守卫]）、nop-rpc-cluster `ClusterRpcClient`（callAsync :59-94[:64 thenCompose+:65-67 空实例抛+:69 手工 future+:73 choose+:74 内层 callAsync+:79-80 终态+:82-84 remove+:85 重入无 try/catch+:88 whenComplete 返回 stage 丢弃+:91 首跳+:92 return future] / call :96-126[有 try/catch 安全对照面] / newNoAvailableServerError :128-131 / getRpcClient :137-154[plan 2498 deadline 注记 :147-150]）、`SentinelFlowControlRunner`（runAsync :45-100，entry.exit 挂任务 stage whenComplete :71-81）、`HttpRpcClientInstanceProvider`（:35 getAddr 首解引用）、`MasterServerChooser`（:42/:48 双 null 返回点）、`rpc-cluster-defaults.beans.xml`（:85 AbstractClusterRpcProxyFactoryBean→ClusterRpcProxyFactoryBean + :94/:97 AbstractRpcProxyFactoryBean parent 链——全平台 @RpcClient 代理装配路径）、`LifeCycleSupport`（allowRestart 默认 true :26 / stop 终态 CREATED :107-108 / isStopping :114-117，**全文无 isStopped 访问器**）
> Related: [periodic-task-insulation.md](periodic-task-insulation.md)（plan 2496 监督形态的绝缘层先例——裁定①共居 AbstractPollingLeaderElector，监督面零回退为硬条款）；plan 2496（javadoc :38-39 预留 cancel-on-stop 半边，本批承接，两批边界以该声明互证）；plan 2498（getRpcClient C4-003 deadline 已落 :147-150，同文件续作边界 = callAsync 重试终结面，互不重叠）；R21 future-永不完成族（family-clustering :65/:596「对齐 completeExceptionally 纪律，不互相吸收」）为裁定④模式基座；plan 2520（R2-17 批）为本文结构模板与门禁先例

## 一、裁定总表

| 裁定 | 成员 | 唯一结论 |
|------|------|---------|
| ① | 4.2-C1-004（P1，MA-01 主；←C4-001/C2-003/C3-004/C2-005/C4-002 五吸收；C10-003 全条 + C10-004 MA-01 半边） | 独立 `volatile boolean stopped` 终态守卫（doStop 锁内首动作置位；清除点 = **基类与 polling 子类两处 doStart 覆写同步落位**——`AbstractPollingLeaderElector.doStart` :72-78 覆写**不调 super.doStart()**，全部生产 elector 均 extends 该子类，仅清基类 :109-117 对 100% 生产 elector 永不执行[R2 Major-1]；**禁止借机补 super.doStart() 调用**——会意外激活基类 addr 自动解析行为回退；LifeCycleSupport isStopping 不作唯一守卫——allowRestart=true 使 stop 返回回 CREATED、isStopping()=false 结构性失明且无 isStopped 访问器）+「stop 返回后窗口」三场景守卫消费矩阵（§3.1）；5 写站点统一私有锁短临界区，promise complete/cancel 与监听器派发全部出临界区（C2-005×C2-003 一次联动定夺，禁全站点 synchronized）；pendingCheck cancel-on-stop 与 2496 重臂协同（scheduleCheck 入口单咽喉拒绝，:114/:141/:112 零回退）；监听器回调停止守护；promise 换装不取消 + 完成 drain 等待者契约；模块外 SysDao/Jdbc 零行为回退 + isStopped() 访问器供迁移；既有 9 钉兼容核对表（§3.2） |
| ② | 4.2-C1-001（P1；C10-006 半边） | 内容变化时重建 ServiceInstance 新对象替换 map 成员（copy-on-write 元素，预设）：copyFromInstance 复用 + modifyIndex 旧值+1 随重建携带 + attrs 快照/cleanupTasks 携带（本地态携带一次定夺）；`Pair.getLeft()` instanceText 探测键不变语义；12 读者站点零消费者变更（清单 SSOT 在 plan，本文引用不复制）；隐式同一性依赖全仓 grep 核对义务成文（live 结论：setAttribute/addCleanupTask 零生产调用方） |
| ③ | 4.2-C1-003（P1；C10-005 半边） | 「仅当 listener 尚未收到过事件时才写入」事件态标志（initInstances 锁内 check-then-write，与 onEvent 同监视器原子）；备选（init 前重拉比较/延迟 init 至首事件）拒绝；测试注入缝预设 = 测试子类 override getNamingService()（:65 public 非 final + 类 :38 非 final 亲读证实，生产零触碰），工厂接缝抽取降备选；与裁定②同写入口（updateInstances）同 diff 协同；失败回滚 :194-197 零回退 |
| ④ | 4.2-C3-001（P1，MA-07 主；←C3-008/C8-008 同 diff[落地 R4-25]；C10-001 MA-07 半边） | 重试体（accept 体全段）与完成回调 err 分支体（:79-:86，含 :79 isCallExpired/:82-84 remove——**完成线程回调体不在任何 accept 帧内，须一并包裹[R2 Major-2]**）try/catch → `completeExceptionally(NopException.adapt(e))`（手工 future 终结权唯一归属重试体，首跳同包裹）；chooseFromCandidates null → 既有 `ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE` 类型化守卫**同 diff 纳入**（消费侧判定，C3-003 本体归 R4-25 零触碰）；call/getRpcClient/SentinelFlowControlRunner 零触碰；C8-002/C3-009 不代做；nop-api-core 条件申报条款预设不触发 |
| ⑤ | 测试缝与零回退钉 | 红①–红④驱动器（§5.1，含 C10-003 四钉形态、红④超时失败模式）+ 测试资产落位（§5.2）+ 既有钉零改写边界（§5.3）+ 零回退钉全列（§5.4）+ C10 五条成员面集落名（§5.5，半边口径按 plan 勘误 6） |

## 二、背景与动机

R2-18 批 4 项（P1×4）覆盖 nop-cluster 三模块三类缺陷面：**选主域**（AbstractLeaderElector 无锁 doStop 与 synchronized onElectionCompleted 竞交——stop 后 leaderEpoch/electionPromise 复活为「本节点是 leader」+ 监听器先 onStop 后 becomeLeader 乱序回调；allowRestart=true 使 LifeCycleSupport 停止后回 CREATED，生产子类 SysDao/JdbcLeaderElector 的 checkElection DB I/O 宽窗口内复活面持续开放；5 写站点仅 2 有锁，pending 调度无 stop 撤销手段）、**发现域**（NacosNamingService.updateInstances 经 copyFromInstance 原地改写共享 ServiceInstance——请求线程撕裂读 + `_host` 惰性缓存固化陈旧端点；subscribe 先于 getAllInstances 的初始化编排无版本守护，事件 B 落在查询返回与 initInstances 之间即以陈旧快照 A 无条件覆盖新事件态）、**调用域**（ClusterRpcClient.callAsync 重试递归体在 whenComplete 回调内同步执行且无 try/catch——异常只被丢弃的返回 stage 捕获，手工 future 永不完成，调用方永久挂起 + SentinelFlowControlRunner entry 泄漏复合）。本文档一次裁定 4+1 项，使实施只有一种合法形态。

## 三、核心设计契约

### 3.1 裁定①：elector stop 生命周期终态守卫与锁纪律（C1-004，MA-01 六条族一处闭合）

- **守卫载体（预设）**：AbstractLeaderElector 新增独立 `volatile boolean stopped`——doStop 锁内**首动作**置位（先于一切清理，置位后所有守卫消费点即时可见）；清除点 = 基类 doStart（:109-117）与 AbstractPollingLeaderElector.doStart（:72-78 覆写**不调 super.doStart()**，亲读证实）**两处同步落位**（[R2 Major-1]：全部生产 elector 均为 polling 子类，单清基类则重启后 stopped 恒 true → 选举链静默永久死亡；两处清除体均为「stopped = false」单行，禁改 doStart 其余行为、**禁补 super.doStart() 调用**——会激活基类 addr 自动解析回退）；且当前 electionPromise 已终结（done/cancelled）时重装全新 promise（封「stop→start 后暴露已取消旧 promise」面）；字段初始 false——**未 start 的 CREATED 态不拒绝**（未启动 elector 上直接 complete 为既有合法形态，§3.2 钉 2 两态绿的前提）。备选（LifeCycleSupport isStopping 唯一守卫）被拒：live 全文亲读——无 isStopped 访问器；allowRestart 默认 true（:26）→ stop() finally CAS 回 CREATED（:107-108）→ isStopping()（:114-117，status≥STOPPING）在 stop 返回后恒 false，对「stop 返回后窗口」（复活缺陷的主窗，2496 finally 重臂 :131-147 在该态同样失明）结构性失明。
- **「stop 返回后窗口」三场景守卫消费矩阵**：

| 场景 | 守卫消费点 | 契约语义 |
|------|-----------|---------|
| mid-round stop（轮在途，stop 置位） | onElectionCompleted 入口（锁内）拒绝 epoch 写与 promise 完成；onBecomeLeader/onBecomeFollower/onException 派发入口拒绝 | 复活确定性拦截；等待者不收停止后当选通知 |
| post-stop rearm（2496 finally 重臂/子类尾部在 CREATED 态重臂） | scheduleCheck(long) 入口**单咽喉** stopped 检查：拒绝调度且不告警（stop 引发的短路 = 设计内终态语义，非断链，不进 reportLoopBroken） | 「stop 后无尾部 reschedule」；建议(2) 双回路钉的判据基础 |
| post-stop onElectionCompleted（stop 返回后在途裁决完成） | stopped 持续 true，同入口拒绝 | getLeaderEpoch()==null 停止后持续不变（红①主判据） |

  与 2496 协同：:114/:141 `!isStopping()` 守卫**保持零回退**（覆盖 beginStop→doStop 进入的 STOPPING 窗），stopped 覆盖 doStop 进入后全窗——两窗无缝；CancellationException 告警豁免（:112）已在位，cancel-on-stop 触发的 whenComplete 不误报断链告警，不重开。
- **锁纪律与派发纪律（C2-005×C2-003 一次联动定夺）**：私有锁对象（非 `this`、非 synchronized 方法形态）统一 5 写站点（doStop :120-124 / onStop :213-225 / newElection :161-163 / onRestartElection :165-171 / onElectionCompleted :173-181）；临界区仅状态变更（volatile 写/引用换装/快照捕获）；promise complete/cancel（等待者 continuation 在完成线程同步执行）与监听器回调派发一律**出临界区**——锁内捕获快照（promise 引用集 + epoch + listener 快照），锁外派发。备选（全站点 synchronized(this) 方法含派发）被拒：把派发面留在锁内即 C2-005 机制在 complete/stop 两面复现（C2-round2 明文警告放大持锁面）。
- **pendingCheck cancel-on-stop**：AbstractPollingLeaderElector doStop 覆写（super.doStop() 后 `pendingCheck.cancel(false)`）——运行中轮不被中断，轮体自然完成后 finally 重臂（:141）撞 scheduleCheck 入口拒绝即终；stop 后 cancel 携 CancellationException → :112 豁免不误报；2496 监督行为可观测形态（告警键/退避重臂/单链约束）零变化。
- **监听器回调停止守护**：onStop 派发后 becomeLeader/becomeFollower/onException 禁达（派发入口拒绝，入口拒绝后禁静默 no-op 语义混同——拒绝即终态守卫行为，由红①监听器序断言机械钉住）。设计内残余：stop 前已越过入口的在途派发与 onStop 派发可交叠——全串行化需锁内派发，违反派发纪律，登记不追求；**stop→start 代际混合窗[R2 Minor-m5 登记]**——doStart 清 stopped 后，上一代在途轮（DB I/O 宽窗）的 onElectionCompleted 可过布尔守卫在新代 promise 上完成旧代真实获胜 epoch（generation 备选被拒的已知代价，语义可辩护：epoch 为真实获胜值），显式登记不追求。
- **promise 等待者契约（C3-004，读取-attach 交错/stop→start 两面）**：换装不取消——onRestartElection/newElection 换装时未完成旧 promise 不 cancel，移入 outstanding 集合；onElectionCompleted 完成当前 promise + drain outstanding（同一获胜 epoch，等待者在重启后的竞选结果上完成）；stop 取消当前 + outstanding（诚实取消信号）；:176-178 isDone 重建分支保持。outstanding 无界面登记：仅「重启无完成」病理循环下线性增长，产出量级与 promise 对象自身同阶（restartElection 调用频率即上界），不设 cap、非新无界账本。
- **模块外 elector 子类零行为回退声明**：SysDaoLeaderElector（do-while `!isStopping()` :65 / 尾部 scheduleCheck :73-74 / onElectionCompleted 调用点 :153/:185/:215）与 JdbcLeaderElector（循环头 `while (!isStopping())` :123 / 尾部 :146-147 / 复检 :224/:260 / onElectionCompleted :247/:281/:415 三调用点[R2 Minor-m4 补全]）**零改写**；基类守卫确定性拦截 base 态复活与监听器回调（子类回调前的 lease 行写不在基类拦截面——「停机后无 lease 行重写」断言本体归两模块对应批次，Non-Blocking Follow-up 登记）；基类新增 protected `isStopped()` 访问器供子类 loop-head/尾部检查迁移（各子类批次义务，本批不强制）；fencing 单调性钉族保持绿（§5.4 焦点腿）。

### 3.2 裁定①既有 9 钉兼容核对表（勘误 13 义务，必查项；Phase 2 落 `_tmp/` 留痕）

| # | 钉 | 终态守卫/锁纪律迁移后兼容性判定 |
|---|----|------|
| 1 | TestAbstractLeaderElector.testStartTriggersPollingCheckElection :101 | doStart 清 stopped（基类+polling 子类两处落位）→ scheduleCheck(0) 照常；isActive 断言不受影响（LifeCycleSupport 零触碰） |
| 2 | testElectionCompletionPublishesEpoch :113 | 未启动 elector complete 合法（stopped 初始 false）——「必须 ACTIVE」守卫形态被此钉结构性否决 |
| 3 | testListenersNotifiedOnLeaderAndFollower :125 | stopped=false 派发全通；COW 注销（handle.close）语义不变 |
| 4 | testStopNotifiesListenersAndResetsEpoch :148 | onStop 派发恰一次 + epoch 锁内清位 → null 断言照常命中 |
| 5 | testRestartElectionResetsStateAndOpensNewPromise :163 | restart 后 epoch null ✓；whenElectionCompleted 非已完成 ✓（当前 promise 新装；已完成的旧 promise 不入 outstanding） |
| 6 | testHostIdDefaultsToConfiguredValue :177 | 零变化 |
| 7 | TestPollingElectorLoopInsulation.testErrorEscapeReframesLoopAndReportsWithBusinessIdentifier :112 | Error 逃逸重臂照常（stopped=false 时守卫不拒）；业务标识日志形态不变 |
| 8 | testExecutionLegRejectionAlarmsAndRearms :140 | 告警 + 重臂照常，scheduleCount≥2 判据不变 |
| 9 | testSubclassTailRearmKeepsSingleChain :170 | 单链约束不变（调度拒绝仅发生在 stopped 之后） |

  9/9 零改写；新增断言 = 红① + C10-003 四钉（§5.1），缺陷态红 → 修复后绿。

### 3.3 裁定②：ServiceInstance 重建对象不可变化（C1-001，copy-on-write 元素）

- **重建协议（预设）**：updateInstances 内容变化分支（:251 instanceText 比较不等）→ 新建 ServiceInstance + `copyFromInstance`（:280 既有 static 复用）→ modifyIndex = 旧值+1 → attrs 快照拷贝 + cleanupTasks 携带 → `map.put(key, Pair.of(instance.toString(), 新对象))`（:247 形态对齐）；未变化分支保持旧 Pair 引用零变（:255）。撕裂读/`_host` 陈旧/modifyIndex 非原子三面一次消除（新对象 `_host` 自新 addr/port 惰性计算）。
- **本地态携带一次定夺**：attrs（:158 CHM）与 cleanupTasks（:151）为本地维护态（javadoc :156-157 明文非 nacos 快照派生）——重建丢弃 = 每次内容变更静默丢失集成态，行为回退；携带语义：attrs 逐条快照拷贝（旧引用冻结）、cleanupTasks 列表内容复制进新实例自身 synchronized 列表（:289-293 per-instance 监视器保形——共享同一 ArrayList 会使 addCleanupTask 同步失配）。移除清理（:268-276）语义不变：任务在最终移除时恰运行一次。
- **modifyIndex/键协同**：copyFromInstance（:280-304）不触 modifyIndex（亲读证实）→ +1 置位与字段拷贝写序无关；`Pair.getLeft()` 仍是 nacos Instance.toString() 内容变更探测器，重建只换 Right——探测键不变语义，递增仅挂真实内容变化。
- **隐式同一性依赖全仓 grep 核对义务（成文）**：全仓 grep `setAttribute|addCleanupTask`（live 2026-11-01 亲核）——ServiceInstance 两 API 零生产调用方（唯一命中为定义自身）；携带语义进一步压缩同一性依赖面。实施期如发现同一性依赖消费方 → 升级条件申报（plan Risks 条款）；12 读者站点零消费者变更清单以 plan 对齐条款为准（引用不复制）。
- **C10-006 前置核对**：TestNacosNamingServiceCopyFromInstance 2 钉（:27/:38）兼容——copyFromInstance static 签名与 `@@` pos 守卫（:292-296）零变化（重建复用该 static）；建议(1)(2) = 红②两钉（§5.1）。

### 3.4 裁定③：订阅快照版本守护（C1-003，事件态标志）

- **守护形态（预设）**：listener 收事件态标志——onEvent→updateInstances 路径置位（**置位点 = :238 synchronized 块内、map 发布前[R2 Minor-m1：置位若在锁外且写后，存在「B 写完→标志未置→init 拿锁 check 通过→A 覆盖 B」窗口]**）；initInstances（:230-232）锁内 check：标志已置 → 跳过陈旧快照写入。原子性：initInstances 与 updateInstances 同以 listener 实例为监视器（:230/:238 synchronized）→ check-then-write 原子；事件 B 落在 getAllInstances 返回（:191）与 initInstances（:192）之间时标志先置 → init(A) 拒写 → serviceInfo 保持 B 态，「后到者胜」结构性消除。init 先胜 race：init 写 A（标志未置）→ 后续事件 B 正常写入，无信息丢失。陈旧态存活窗（扩容不可见/缩容幽灵调用）随守护闭合。
- **备选拒绝**：(b) init 前重拉 getAllInstances 与事件态比较——nacos 快照无版本号/epoch，与事件态无全序可依，内容比较判不了新旧，递归重拉仍竞态；(c) 延迟 init 至订阅首个事件——依赖 SDK「订阅后必首推」语义（订阅后无变更则首推不来 → getInstances 首调空挂），首调行为回退，风险不可控。
- **测试注入缝（一次定夺，与裁定②共用）**：预设 = 测试子类 override `getNamingService()`（:65-78 public 非 final、类 :38 非 final——live 亲读证实；getServiceInfo 双调用点 :179/:191 全经缝）→ 生产零触碰；工厂接缝抽取降备选——生产代码改动 + 懒初始化时序与 C1-005 destroy 复活面（R4-25）交错漂移。缝为模块内 seam，不扩公共契约。
- **失败回滚零回退**：NacosException → subscribed → unsubscribe（:194-197）原样保留。C10-005 半边 = 红③（§5.1）。

### 3.5 裁定④：callAsync 重试体终结性（C3-001，R21 族纪律对齐）

- **终结协议（预设）**：accept 体全段 try/catch（chooseFromCandidates :73 → getRpcClient :74 → 内层 callAsync 同步段）**+ 完成回调 err 分支体（:79-:86——:79 ctx.isCallExpired()/:83 instances.remove(instance) 在完成线程回调体内执行、不在任何 accept 帧内，chooser 返回不可变 List 时 :83 抛 UnsupportedOperationException 同被 :88 丢弃 stage 吞掉[R2 Major-2]，须一并包裹）**→ catch Throwable → `future.completeExceptionally(NopException.adapt(e))`。不变式：手工 future（:69）终结权唯一归属重试体，**任一跳同步异常与任一回调体同步异常必终结返回 future**（rule 24——禁 catch 后仅记日志不终结）。首跳 fn.accept(0)（:91）同包裹：首跳同步异常现经 thenCompose 外层 stage 到达调用方（CF 机制捕获），包裹后统一经手工 future 终结——**调用方异常必达 + 类型规范化为 adapt 包装[R2 Minor-m6：交付语义等价、异常类型规范化为可观测变化；:65-67 空实例抛仍走外层原样，两路类型不一致为登记现状]**。终态分支（:80 raw err）与 :82-84 remove 语义原样（包裹只加终结不改控制流）。
- **null 守卫同 diff 纳入（一次定夺）**：chooseFromCandidates 返回 null（MasterServerChooser :42/:48 双 null 返回点）现走 getRpcClient(null) → HttpRpcClientInstanceProvider :35 getAddr 首解引用 NPE——仅 try/catch 时被 adapt 成无业务参数的 untyped 错误。纳入：accept 体 null 检查 → `completeExceptionally(newNoAvailableServerError(serviceName))`（:128-131 既有 helper + ClusterErrors :18 既有错误码，零契约演进）。C3-003 本体边界保持：chooser 生产侧行为与 IServerChooser 契约零触碰（归 R4-25）；null 消费点类型化防御在消费侧（ClusterRpcClient）。
- **R21 族纪律与机制联动声明**：禁依赖上层超时兜底；Sentinel entry 释放 = future 终结的机制联动——SentinelFlowControlRunner.runAsync 的 entry.exit 挂任务 stage whenComplete（:71-81），返回 future 终结 → 外层 stage 终结 → whenComplete 触发 exit；`SentinelFlowControlRunner.java` 零触碰（C3-007/C3-008 面 = R4-25 MA-05）。C8-002（退避）/C3-009（反向取消）不代做——重试循环改写禁顺手引入退避调度或 cancelToken 反向挂接。
- **零触碰预设与条件申报条款**：call（:96-126）/getRpcClient（:137-154）/live 装配链（rpc-cluster-defaults.beans.xml :85-97）零触碰；nop-api-core 纯消费（IRpcService/ICancelToken/NopException.adapt）——条件申报预设不触发，实施期 git diff 核对留痕。

## 四、对齐纪律条款（逐条成文）

1. **MA-01 六条族一处闭合**：C1-004 一处修复同时闭合 C4-001（stop 复活调度链）/C2-003（5 写站点锁统一 + pending 撤销）/C3-004（promise 等待者时序）/C2-005（complete 出临界区派发）/C4-002（守卫空转）——六条族不立六工单不六处改；C2-005×C2-003 互为前置，锁纪律与派发纪律一次定夺（§3.1）。
2. **2496 共居文件零回退**：裁定①与 plan 2496 已落监督代码共居 AbstractPollingLeaderElector——:114/:141 isStopping 守卫、:112 CancellationException 豁免、报告键与退避参数全部保持；`TestPollingElectorLoopInsulation` 3 钉零回退（§3.2）。
3. **C1-001 × 12 读者站点零消费者变更**：重建对象后读者面（LB/filters/provider/chooser/adapter 全列在 plan）零代码变更——读者按不可变契约自然受益，禁「顺手」改读者站点（MA-08 触发前提弱化仅注记，本体 R4-25）；清单以 plan 对齐条款为准，本文不复制。
4. **C1-003 × C1-001 同写入口协同**：两缺陷共用 updateInstances 单一写入口——重建（裁定②）与版本守护（裁定③）在同一次 updateInstances 改造中协同落地，禁止只修一半；测试注入缝两案共用（§3.4）。
5. **C3-001 × R21 族纪律对齐**：completeExceptionally 终结模式对齐族先例（R2-02/R2-12/plan 2496 三件套形态）；重试循环改写禁顺手退避（C8-002）/反向取消（C3-009）；SentinelFlowControlRunner 零触碰（§3.5）。
6. **R4-25 边界条款**：C3-008/C8-008 同 diff 改写 + 反向钉死 #1（`TestSentinelFlowControlRunner.testSupplierSyncThrowPropagatesSynchronously` :102-114）改写 + C3-003 本体（MasterServerChooser 双 null 返回点 + IServerChooser 契约）+ 反向钉死 #3（`TestWeightedPartitionAssigner.testZeroAndNegativeWeight` 域）全部归 R4-25，本批零触碰（防顺手解除越批）。
7. **C10 五条同 diff（E2）**：C10-001[MA-07 半边]/C10-003[全条]/C10-004[MA-01 半边]/C10-005[C1-003 半边]/C10-006[C1-001 半边]随各自本体同 diff 落地，禁先修本体后补测拆分交付（§5.5）。
8. **模块外子类零行为回退**：SysDao/JdbcLeaderElector 本体零改写 + isStopped() 迁移不强制；焦点腿保运行（§5.4）；「停机后无 lease 行重写」断言本体归模块外批次（Non-Blocking Follow-up）。
9. **无跨批硬依赖**：本批可并行实施；批内软序 = C1-001 与 C1-003 同文件协同（对齐条款 4），建议同组实施。

## 五、裁定⑤：测试缝与零回退钉

### 5.1 红①–红④驱动器（先红后绿，E2；缺陷态红判据 → 修复后绿口径）

| 红 | 成员 | 载体形态 | 缺陷态红 → 修复后绿 |
|----|------|---------|-------------------|
| ① | C1-004（C10-003 建议(1) 主判据） | stub 子类沿 TailRearmElector 形态（绝缘测试既有基建复用）：checkElection latch 挂起模拟子类 DB I/O 宽窗 + 另线程 stop() + 释放 → 断言 getLeaderEpoch()==null 持续、whenElectionCompleted 不返回新完成、onStop 后无 becomeLeader/becomeFollower 回调（监听器序全序断言同 diff） | 缺陷态：onElectionCompleted 停止后复活/新 promise complete 红 → 终态守卫+锁统一绿。trial 纪律：latch 强制交错为确定性红，单 trial 即红证（无负载噪声面，不设多 trial） |
| ② | C1-001（C10-006 建议(1)(2)） | 同一 instanceId 两次内容变更事件（addr 迁移）后：(a) 旧引用读者视图不变（旧引用 addr/host 保持旧值且自洽）；(b) `_host` 契约（**迁移前先经当前引用读一次 getHost() 固化 `_host` 缓存[priming 读，R2 Minor-m3：不固化则缺陷态惰性计算直接取新 addr，(b) 半边缺陷态绿]**，再触发迁移，断言当前引用 getHost() 返回新端点）。并发撕裂面以 ≥10 trial 探针 + 变异探针为辅助红证（注释重建分支 → 钉红 → 恢复后 grep 断言目标行在位——R5 教训；探针件备份 `_tmp/`，产品树零残留） | 缺陷态：原地改写使旧引用呈现新 addr + `_host` 固化旧端点双红 → 重建对象绿；主体两钉确定性红 |
| ③ | C1-003（C10-005 半边） | override 缝形态（§3.4）：测试子类 override getNamingService() 注入 stub——subscribe 捕获 listener 实例；getAllInstances latch 门控返回陈旧快照 A；事件 B 经捕获 listener.onEvent 注入后放行 → 断言 getInstances 返回 B 态不回退 | 缺陷态：init(A) 无条件覆盖红（后到者胜）→ 事件态标志拒绝绿；确定性红 |
| ④ | C3-001（C10-001 MA-07 半边） | stub 首跳可重试异常（NopConnectException）+ 重试体内 chooseFromCandidates/getRpcClient 同步抛 RuntimeException + retryCount≥1 + ≥2 实例 → 断言返回 future 有限时间（get(5s)）completeExceptionally。**失败模式 = 超时**（缺陷态确定性挂死非竞态）：timeout 须给足但有限并在钉内注释失败模式语义。伴生 null 钉：chooseFromCandidates 返回 null → 错误码 = nop.err.cluster.core.no-available-server-instance（确定性） | 缺陷态：future 永不完成 get 超时红 → 重试体终结绿；null 钉缺陷态 NPE 包装错误面红/绿两态均终结（判据挂错误码） |

  **C10-003 其余三钉（全条，新钉）**：建议(2) stop→start ×2 双回路钉——两轮 stop→start 后 checkElection 计数增速不翻倍（绝缘钉 :170 形态镜像；缺陷态重臂失明双链增长红）；建议(3) 等待者终态完成钉——whenElectionCompleted 挂接 → restartElection → 断言等待者以重启后竞选结果完成而非 CancellationException（缺陷态换装取消旧 promise 红 → drain 转发绿）；建议(4) complete 出临界区**结构性钉**——同步 waiter continuation（thenRun 挂 latch）阻塞于 complete 派发 + 探针线程有界等待进入任一 synchronized 守卫方法：缺陷态（complete 在锁内）latch 持续期探针必不能进入（结构性红，失败模式 = 探针超时进入失败，非时延阈值）；修复后（派发出锁）探针在 latch 持续期即进入。

### 5.2 测试资产落位

nop-cluster-core elector 测试包：红① + C10-003 四钉（plain-JVM，独立命名 timer 形态沿既有两测试文件基建）；nop-cluster-nacos 测试包：红②两钉 + 红③ + override stub 子类（stub NamingService 实现 SDK 接口 no-op 形态——编译期依赖已在模块内，plain-JVM 可跑，无 nacos SDK 运行时/无 docker 门控）；nop-rpc-cluster 测试包：红④ + null 钉（直接构造 ClusterRpcClient + stub chooser/provider，不经 IoC——TestClusterRpc 的 JunitBaseTestCase 装配钉零触碰）。补测类声明：新增断言即新钉、缺陷态红 → 修复后绿；红绿快照落 `_tmp/red-green-2521-summary.md` 总册 + 逐 trial 日志。端到端义务（rule 22）：红① 生命周期链（doStop 入口→监听器回调出口全序）+ 红④ 调用链（callAsync 入口→future 终结出口）承载；C1-001/C1-003 为写入口单点修复，由红②/红③承载（免端到端义务显式声明）。接线验证（rule 23）：终态守卫在 onElectionCompleted 入口真实消费断言（非仅标志存在）、重建对象真实替换 map 成员断言（旧引用与新引用非同一对象 + 各自视图一致）、completeExceptionally 真实到达返回 future 断言。

### 5.3 既有钉兼容与改写边界

唯一允许的既有钉改写 = **无（零改写批）**。TestAbstractLeaderElector 6 钉 + TestPollingElectorLoopInsulation 3 钉兼容核对表（§3.2）为 Phase 2 必查项（勘误 13，留痕 `_tmp/`）；TestNacosNamingServiceCopyFromInstance 2 钉、TestClusterRpc 1 钉、TestClusterRpcPollOffload_C4_003 3 钉、TestLeaderElectorConfigBinding、TestAutoRegistration+TestAutoRegistrationInsulation 6 钉、TestCachingNamingService 4 钉、TestPartitionResolver 10 钉、TestLoadBalance 5 钉、filters 族、TestSentinelFlowControlRunner 6 钉 + TestSentinelFlowControlRunnerContext 2 钉全部零改写；反向钉死 #1（:102-114）与反向钉死 #3 零触碰（对齐条款 6）。

### 5.4 零回退钉全列（钉零回改逐条 git diff 为空核对）

- nop-cluster 基线 **80 tests 0F0E**（`_tmp/baseline-2521-nop-cluster.log`：core 65 / sentinel 8 / nacos 2 / hazelcast-core 1 / rpc-cluster 4）全量零回退。
- 模块外 elector 焦点腿（E3 命令盲区，勘误 11）：nop-sys-dao `-Dtest` = TestDaoLeaderElector,TestJobCoordinatorWithSysDaoLeaderElector；stream-runtime `-Dtest` = TestJdbcLeaderElector,TestJdbcLeaderElectorEpochMonotonicity,TestJobCoordinatorLeaderElection,TestJobCoordinatorJdbcHaIntegration,TestLeaderElector——solo BUILD SUCCESS 留证（fencing 单调性钉族 = 裁定①锁纪律迁移的行为面回归锚；stream-runtime E2E flake 面以 -Dtest 限定规避）。
- 上游环境抖动按执行计划勘误 10 三联裁定（stash 基线同红 / solo 绿 / 恢复复跑全绿）处理留痕，不得静默重跑或跳过。

### 5.5 C10 集落名（五条成员面，随本体同 diff，E2；半边口径按执行计划勘误 6）

- **C10-001 → C3-001（MA-07 半边）**：红④终结性钉 + null 钉。建议(2)(3)(4)（无主类型化/退避时间戳/反向取消）随 C3-003[MA-09→R4-25]/C8-002/C3-009 各本体。
- **C10-003 → C1-004（全条）**：红① + 建议(2)(3)(4) 三钉——四条建议保护对象全部在 MA-01 集合内。
- **C10-004 → MA-01 半边**：stop 后停止投递/监听器序断言（红①伴生钉）。C4-004 半边已由 plan 2496 收敛（TestPollingElectorLoopInsulation 3 钉在案为零回退钉）。
- **C10-005 → C1-003 半边**：红③快照回退钉。其余保护对象（C1-005/C3-005/C8-005/C2-002/C3-011）归 R4-25/各本体。
- **C10-006 → C1-001 半边**：红②两钉（旧引用视图 + `_host` 契约）。建议(3)(4)（cleanupTasks 丢任务/慢任务阻塞）随 MA-02 条化本体。

## 六、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|---------|
| ① LifeCycleSupport isStopping() 唯一守卫 | allowRestart=true（:26）使 stop 返回回 CREATED（:107-108）、isStopping() 恒 false——复活主窗结构性失明；LifeCycleSupport 无 isStopped 访问器（live 全文亲读） |
| ① 「必须 ACTIVE」状态守卫 | 未 start 即 complete 为既有合法形态（§3.2 钉 2）——ACTIVE 守卫把既绿钉改红，守卫语义须挂「已停止」而非「未启动」 |
| ① generation/promise-identity 守卫 | 单 promise 身份不护 epoch/listeners/stopped 多状态线性化；generation 计数需逐读比对、漏一处即复发；独立布尔 + 单锁最小且可全站点统一消费 |
| ① 全站点 synchronized(this) 方法 | 派发面留在锁内 = C2-005 机制在 complete/stop 两面复现（C2-round2 明文警告放大持锁面）；私有锁短临界区 + 出锁派发同时闭合 C2-003/C2-005 |
| ② volatile 化最小修复（`_host` 失效 + 全字段 volatile） | volatile 只保单字段可见——addr/port/weight/healthy 跨字段撕裂读仍开放；`_host` 失效是每写点纪律，漏一处即复发；重建对象一次性消除三面且读者零改动 |
| ② 重建丢弃 attrs/cleanupTasks | 本地维护态（javadoc :156-157 明文非 nacos 派生）被内容变更静默丢弃 = 集成态行为回退；携带语义与现形态可观测行为等价 |
| ③ init 前重拉比较 | getAllInstances 快照无版本号/epoch——与事件态比较无全序，内容比较判不了新旧，递归重拉仍竞态 |
| ③ 延迟 init 至首事件 | 依赖 SDK 订阅后必首推语义（无变更不推）→ getInstances 首调空挂，首调行为回退 |
| ③ 工厂接缝抽取 | 生产代码改动 + 懒初始化时序与 C1-005 destroy 复活面（R4-25）交错漂移；override 缝现成且零生产触碰 |
| ④ 仅 try/catch 不加 null 守卫 | 确定性 null 面（MasterServerChooser :42/:48）走 NPE 被 adapt 成无业务参数 untyped 错误；两行消费侧守卫用既有错误码/helper，零契约演进 |
| ④ null 守卫归 R4-25 C3-003 代做 | 消费侧确定性面留 NPE 到下一批，红④须匹配 NPE 类（脆弱）；C3-003 本体（chooser 生产侧）边界不受影响 |
| ④ whenCompleteAsync+handle 链改写 | 重写递归载体改变重试语义边界（remove/同步段），diff 面大于缺陷本体；try/catch 与 R21 族先例同形态 |

## 七、与已有设计的关系

- [periodic-task-insulation.md](periodic-task-insulation.md)：plan 2496 监督形态（pendingCheck 保留/业务标识告警/finally 重臂）的绝缘层设计先例——裁定①共居 AbstractPollingLeaderElector，监督面可观测行为零变化为硬条款（§3.2）。
- plan 2496：javadoc :38-39 明文 stop/restart 生命周期面归 R2-18——本批承接其预留 cancel-on-stop 半边，两批边界以该声明互证；plan 2498：getRpcClient C4-003 deadline 已落（:147-150），同文件续作边界 = callAsync 重试终结面，互不重叠。
- R21 future-永不完成族（family-clustering :65/:596）：completeExceptionally 纪律先例为裁定④模式基座；「不互相吸收」维持；跨单元同号（4.3-C3-001 等）零关联。
- plan 2520（R2-17 批）：本文结构模板与 E1–E6 门禁先例；其 R21 族纪律引用同源。
- **下游 R4-25（G65）**：C3-008/C8-008 同 diff 改写 + 反向钉死 #1 改写 + C3-003 本体归 R4-25（plan 勘误 8 口径）；C1-005 destroy 复活面以本裁定③（override 缝零生产触碰）落地后的 live 形态为基线消费接缝。
- owner doc 落点（Phase 3 统一落账，本 Phase 零 owner doc 变更）：`docs-for-ai/01-repo-map/module-groups.md` 通行句 + 缺口声明；`docs-for-ai/04-reference/source-anchors.md` 新增 CLUSTER-CC-001 锚点行（plan Goals 行口径；如契约句超出通行句容量，登记 R-G 治理批去向）。
