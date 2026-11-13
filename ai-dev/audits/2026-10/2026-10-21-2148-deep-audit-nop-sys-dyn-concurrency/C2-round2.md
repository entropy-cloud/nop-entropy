# C2 锁与同步原语 — 深挖第 2 轮

- 方法：本轮沿两条线挖掘：①持锁执行外部/用户代码的锁扩展面；②初审未覆盖的复合序列（claim-CAS 与完成写的配对、租约持有期）。第 1 轮锁序图结论经反向复查（item→this 不存在反向边）后维持。

## 发现条目

### [C2-101] on-load/on-unload 用户 XPL 钩子在 cache this 锁内执行：用户代码可无界扩展全局生成锁

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/InMemoryCodeCache.java:135-148`（removeModule 持锁调 runOnUnloadModule）、`:127-133`（addModule 持锁调 runOnLoadModule→runXpl :165-172 执行任意 XPL）
- **证据片段**:
  ```java
  public synchronized void addModule(ModuleModel module, boolean formatGenCode) {
      removeModule(module.getModuleId());
      genModuleCoreFiles(formatGenCode, module);
      runOnLoadModule(module);          // 持 this 锁执行 on-load.xpl（任意用户逻辑/IO）
      enabledModules.put(module.getModuleId(), module);
  }
  ```
- **严重程度**: P3
- **并发场景**: 管理端模块上下线（持锁执行 XPL 钩子，钩子内做 DB/HTTP/长计算）× 全部等待该 cache 锁的运行期懒加载请求（getOrmModel/getEnabledModule/getObjMeta）
- **触发时序**: 非竞态而是锁扩展：on-load.xpl 执行 30s → 其间所有动态模型读请求（含热路径资源 miss 回调经 genBizObjFiles）排队 30s。用户代码若反向调用本 cache 的 synchronized 方法，同线程可重入不死锁，但会嵌套加深持锁时长。
- **现状**: 与类注释“单线程生成”的声明一致——互斥是设计；未声明的是**钩子时长进入全局临界区**这一代价。F10 交叉（持锁调用未知外部代码）。
- **风险**: 钩子劣化直接转化为平台级动态模型停摆；无可观测隔离。
- **建议**: 文档声明 on-load/on-unload 的时间预算约定；长期可在锁外预执行钩子、锁内仅提交结果。
- **失效面枚举**: 持锁外部执行点全集：runOnLoadModule :150-153（addModule 调用）、runOnUnloadModule :155-163（removeModule 调用，catch 保护 :160-162 不会抛出锁——✅异常面已封）、genModuleCoreFiles→XCodeGenerator.execute :197（模板执行，同族）、hook.prepare* 4 方法（DB 访问，初审 C2-006 已并）。
- **信心水平**: 确定
- **误报排除**: 非低频豁免——模块上下线虽低频，但执行期影响面是全部动态请求；登记 P3 因互斥本身为声明设计。
- **复核状态**: 已保留

### [C2-102] 事件认领与完成写之间无租约续期、完成写无版本 fencing：租约过期后双节点并发处理同一事件

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/NonBroadcastEventProcessor.java:127-140`（claim 一次性设租约）、`:142-149`（process 分发）、`:151-186`（handleResult→`dao.updateEntityDirectly(event)` 无版本检查）；租约时长 `SysDaoMessageService.leaseTimeout=10000`（:139）
- **证据片段**:
  ```java
  // claim（tryUpdateManyWithVersionCheck：逐行 CAS，仅成功的进入返回列表）
  event.setLeaseExpireTime(new Timestamp(now + leaseTimeout));   // 一次性，处理期间不续期
  ...
  // handleResult：T1 完成后直接写（其实体 version 已落后于接管方的 v+2）
  dao.updateEntityDirectly(event);   // 无 version 条件、无 leaseOwner 条件
  ```
- **严重程度**: P1（多节点部署 + 消费时长 > leaseTimeout(默认10s) 时必然触发；单节点因 fixedDelay 不重叠而豁免）
- **并发场景**: 节点 A 轮询线程与节点 B 轮询线程；事件消费耗时超过租约
- **触发时序**: T1(A) claim 成功（status=CLAIMED, lease=+10s, version v+1）→ 分发消费者，耗时 15s → 租约在第 10s 过期 → T2(B) fetchCandidates :110-116 判 CLAIMED 已过期 → 候选 → claim CAS 成功（version v+2，lease=+10s）→ B 分发同一事件 → **A、B 并发处理同一事件（重复副作用）** → A 先完成：updateEntityDirectly 以 v+1 实体直写（若含 version/lease 列则把 B 的租约信息覆盖回旧值）；B 后完成置 PROCESSED。最终状态取决于写序，且处理期 A 的 ConsumeLater/重试决策可能覆盖 B 的终态。
- **现状**: 认领是 CAS 的（✅），但“处理中租约续期”与“完成写 fencing（where version=认领时版本 且 leaseOwner=self）”双双缺失——租约机制只保护了认领互斥，没保护处理互斥。
- **风险**: 队列语义（SysDaoMessageService :522-523 声明“只投递一个消费者”）在慢消费下违约；非幂等消费者产生重复业务副作用。C8-001（消费无超时）放大本条触发概率——挂死的消费者让租约过期成为常态而非例外。
- **建议**: ①分发侧以 leaseTimeout 的分数间隔调 tryResetLease 式续期（复用 C2-002 修复后的状态回写）；②handleResult/handleProcessEventError 改条件更新（where version=claim 时版本 AND leaseOwner=self），影响行数 0 则放弃写（已被接管）。
- **失效面枚举**: 认领-完成配对全集：①`process(NopSysEvent)` :142-149→handleResult :151-186（主路径，本条）；②batch 入口 `processClaimedNonBroadcastEvent`（SysDaoMessageService :361-363）→ 同一 process——外部批量线程认领后处理，同样无续期/fencing，同族；③handleProcessEventError :188-201 同一 updateEntityDirectly 失同步面（已并）。广播路径无租约（语义即全投，豁免）。
- **信心水平**: 确定
- **误报排除**: 非理论竞态——10s 默认租约下“消费>10s”是普通场景；非单节点自愈——多节点为平台声明部署形态（本模块存在 elector/naming 即为集群设计）。
- **复核状态**: 已保留

## 第 2 轮锁序反向复查（维持初审结论）

- SysSequenceGenerator：全部路径均为 `synchronized(this)（findSeqItem）→ 释放 → synchronized(item)（generate*）` 单向；:282-283 递归 findSeqItem 为同锁重入。无反向边，无环。
- InMemoryCodeCache 4 个 hook 方法（DynCodeGen prepare*）均未 synchronized：cache 锁内回调不再取 DynCodeGen 锁 → 初审锁序图“单向边”结论维持。
- `orm_disableVersionCheckError(true)` 使用点全集：SysDaoLeaderElector tryRenewLease :121（初审 C2-007）、changeLeader :204（初审 C2-004）、restartElection :232（语义自洽豁免）——与本模块 NonBroadcastEventProcessor.claim（kernel default 方法内部）合计核对于上。
