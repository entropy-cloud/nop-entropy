# C2 锁与同步原语 — 初审（第 1 轮）

- 范围：8 源码根全部锁载体。基线 C2 命中 4 文件 32 处 synchronized：InMemoryCodeCache×13、DynCodeGen×13、SysSequenceGenerator×3、DynResourceStore×3；另有 DB 乐观锁/条件删除等分布式锁语义（SysDaoResourceLockManager/SysDaoLeaderElector/SysDaoMessageService.claim）逐一核对。

## 锁序图（文本）

```
节点（JVM 内 monitor）:
  DynCodeGen(this)  ──调用──▶  InMemoryCodeCache(this)   [generateBizModel:294→genBizObjFiles:297;
        ▲                                                     generateForModule:252→addModule]
        │                                              ──▶ ResourceLock 无
        │ 回调 prepare* (hook)        InMemoryCodeCache(this) ──▶ 无 DynCodeGen 锁（4 个 hook 方法
        └───────────────────────────── 均未 synchronized: prepareLoadModule:432/prepareBizObject:452/
                                       prepareOrmModel:472/prepareResource:485）→ 单向边，无环
  InMemoryCodeCache.getOrmModel:349 ──▶ ormModels CHM bin 锁（computeIfAbsent）
  （注释 :346-348 自证曾发生 ABBA，已修复为先 this 后 bin；TestInMemoryCodeCacheDeadlock 钉死）

DB 层锁序:
  SysSequenceGenerator: synchronized(item) 内 session.lock(NopSysSequence 行) —— per-seq 锁下再取行锁
  SysDaoResourceLockManager: 无 JVM 锁，纯 DB 行插入/条件删除（version/holderId 条件）
  NonBroadcastEventProcessor.claim: tryUpdateManyWithVersionCheck 乐观版本 CAS
```

结论：JVM 内无锁环；DB 层无跨行多锁获取（单行 lock/update），无死锁环。发现集中在锁的**互斥语义漏洞**与**粒度/代价**。

## 发现条目

### [C2-001] 过期锁行删除缺 holderId 条件：竞争者可删掉新持有者的新锁行，互斥被破坏

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/lock/SysDaoResourceLockManager.java:142-157`（对照同文件已修复同族 ：235-244 tryResetLease、:262-272 releaseLock）
- **证据片段**:
  ```java
  NopSysLock existing = (NopSysLock) session.get(NopSysLock.class.getName(), pk);
  if (existing != null) {
      if (isExpired(existing, clock)) {
          SQL sql = SQL.begin().deleteFrom().append(NopSysLock.class.getName())
                  .where().eq(NopSysLock.PROP_NAME_lockName, existing.getLockName())
                  .and().eq(NopSysLock.PROP_NAME_lockGroup, existing.getLockGroup())
                  .and().eq(NopSysLock.PROP_NAME_version, existing.getVersion()).end();  // ← 无 holderId
  ```
- **严重程度**: P1
- **并发场景**: 资源 R 的租约过期后，节点 A 与节点 B 同时 `tryLockWithLease(R,...)` 进入重试循环
- **触发时序**: T1（A）执行 ：143 读到 expired 行（version=0, holder=old）→ T2（B）执行 ：143 读到同一行（version=0）→ T1 执行 ：152 delete（匹配 v0，删掉旧行）→ T1 执行 saveNew 插入**自己的新行（version 恒为 0，:210-222 无 version 初始化即 0）** 并返回锁成功 → T2 继续执行 ：152 delete，条件仅 lockName+lockGroup+version=0 → **命中并删除 T1 刚插入的新行** → T2 返回 null → 走 ：164 saveNew 成功持锁 → A、B 双方各自持有 EntityResourceLockState，互斥被无声破坏。
- **现状**: 同文件 `releaseLock` :263-264 注释明确记载了同一根因（“重建的锁行version恒为0，版本防护无法区分新旧持有者：删除必须带holderId条件，否则…互斥破坏”）并在 ：268 补了 holderId；`tryResetLease` :241 同样补了 holderId。**唯独 tryLockWithLease 的过期删除分支漏补**——教科书式 F3“修实例不修类别”。
- **风险**: 租约过期 + 双竞争者的窗口内（DB 调度锁、批处理资源锁等真实场景），两节点同时认为持锁；持有方若执行非幂等任务（调度/迁移/分配）产生重复副作用。保存失败日志仅 trace/warn（:176-184），事后无从察觉互斥曾破坏。
- **建议**: :151 删除条件追加 `.and().eq(NopSysLock.PROP_NAME_holderId, existing.getHolderId())`，与 releaseLock/tryResetLease 修复对齐；长期应把“version 恒 0”改为插入时取时间戳/序号版本。
- **失效面枚举**: 条件删除全集：①`tryLockWithLease` :148-151——缺 holderId（本条）；②`releaseLock` :265-269——已带 holderId（已修）；③`tryResetLease` :235-242——已带 holderId（已修）；④`forceUnlock` :107-111 按主键无条件删——管理接口，语义即强删，豁免。逐一核对，仅①漏修。
- **信心水平**: 确定
- **误报排除**: 非理论竞态——存在完整可执行交错序列；非已声明边界——同文件两处同族修复 + 注释证明这是缺陷形态而非设计。
- **复核状态**: 已保留

### [C2-002] tryResetLease 成功后不回写内存 version：第二次续约起必然失败、isHoldingLock 恒假、releaseLock 删不掉

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/lock/SysDaoResourceLockManager.java:227-245`（对照 `EntityResourceLockState.java:53-55` 直接读 entity.getVersion()）
- **证据片段**:
  ```java
  public boolean tryResetLease(IResourceLockState lock, long leaseTime) {
      NopSysLock entity = ((EntityResourceLockState) lock).getEntity();
      return runLocal(session -> {
          ...
          SQL sql = SQL.begin().update(...)
                  .set().eq(PROP_NAME_version, entity.getVersion() + 1).comma()...
                  .and().eq(PROP_NAME_version, entity.getVersion()).end();
          return session.executeUpdate(sql) == 1;   // DB version=v+1，内存 entity.version 仍=v
      });
  }
  ```
- **严重程度**: P2（若全仓库存在周期续约调用方则升 P1）
- **并发场景**: 同一持锁线程先续约一次后再次续约（watchdog 模式），或续约后调用 isHoldingLock/releaseLock
- **触发时序**: T1 首次 tryResetLease：DB version 0→1，返回 true；T1 第二次 tryResetLease：`where version=entity.getVersion()=0` 而 DB 已是 1 → update 0 行 → 返回 **false** → 调用方（kernel `ResourceLock.tryResetLease` :70-74 直通）按“续约失败/已失锁”处理；随后 `isHoldingLock` :248-257 `state.getVersion()==lock.getVersion()` 恒 false；`releaseLock` :265-269 `where version=0` 删 0 行——锁行滞留至租约过期，期间他方 saveNew 重复键失败。
- **现状**: CAS 条件用了内存 version，但成功路径从不把 v+1 写回内存实体——状态对象与 DB 永久失同步。同文件 javadoc :233-234 自述“tryResetLease从未被测试执行过”。
- **风险**: 任何采用“周期续约保活长任务”的用法第二次续约必失败；资源被提前暴露为可抢占或持锁方被误判失锁。当前仓库内未发现周期续约调用方（ResourceWithHistoryProvider 仅用 tryLock/release），故为**潜伏缺陷**。
- **建议**: 成功分支 `entity.setVersion(v+1)` 同步回写（或让 tryResetLease 返回携带新 version 的状态）；补一条“两次连续续约都成功”的回归测试。
- **失效面枚举**: IResourceLockState 消费点全集：tryResetLease :227 / isHoldingLock :248 / releaseLock :260——三者共用同一 entity 视图，一损俱损；kernel `ResourceLock` :70-82 为透传方。
- **信心水平**: 确定（代码事实）；影响面取决于调用方（现状无）
- **误报排除**: 非“synchronized 使用”类误报；是锁 API 自身状态机断裂，且注释自证无测试覆盖。
- **复核状态**: 已保留

### [C2-003] DynResourceStore.getResource 全方法 synchronized：动态资源热路径读取全局单点串行

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynResourceStore.java:20-28`（对照 `InMemoryCodeCache.getResourceStore` :400-417 单例发布）
- **证据片段**:
  ```java
  @Override
  public synchronized IResource getResource(String path, boolean returnNullIfNotExists) {
      IResource resource = store.getResource(path, true);   // 命中路径也要过全局 monitor
      if (resource != null) return resource;
      prepare.accept(path);                                  // miss 时在锁内执行整段代码生成+DB 查询
      return store.getResource(path, returnNullIfNotExists);
  }
  ```
- **严重程度**: P2
- **并发场景**: 所有动态模型资源读取（动态实体的 xbiz/xmeta/orm/app.orm.xml、动态页面 view/page）在运行期都经该单例 store；多请求线程并发解析不同资源
- **触发时序**: 无需竞态即成立：T1..Tn 并发 getResource(不同 path) → monitor 串行化全部命中读取；任一 miss 触发 `prepare.accept` → 持锁执行 `hook.prepareResource`（InMemoryCodeCache.genBizObjFiles：DB 查询 + XPL 代码生成，秒级）→ 期间全部动态资源读取（含与生成无关的路径）阻塞。懒生成触发时是**请求路径上的全局限速器**。
- **现状**: synchronized 仅 miss 路径需要（防双生成），却覆盖了高频命中路径；miss 期间的持锁重操作与 InMemoryCodeCache 的 this 锁叠加（prepareResource→genBizObjFiles 再取 cache 锁）。
- **风险**: 管理端一次实体编辑后的首个资源访问触发整段生成，期间平台所有动态模型请求排队；负载上升表现为周期性无解释延迟尖峰。
- **建议**: 命中路径去掉锁：先无锁读，miss 时在 `synchronized` 内 double-check 后再 prepare（DCL 模式，仓库内 `DynCodeGen.getTenantCodeCache` :160-167 即范本）。
- **失效面枚举**: ①`getResource` :21-28（本条）；②`supportSave`/`saveResource` :36-43——管理写路径，低频，synchronized 合理豁免；③`getChildren` :31-33 无锁——与①不一致的另一半：同一类内一锁一无锁（本身无碍，但说明锁并非为底层 store 一致性而设）。
- **信心水平**: 确定
- **误报排除**: 非低频路径粗锁豁免——getResource 是每请求级热路径（对照 owner 规模口径：动态模型在多租户下每请求解析资源）；锁内含 DB I/O+代码生成属“锁内重操作”定向检查项。
- **复核状态**: 已保留

### [C2-004] SysDaoLeaderElector.changeLeader 无 CAS fencing：过期 leader 接管可双节点同时当选（瞬态双主）

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/elector/SysDaoLeaderElector.java:194-217`（changeLeader；`:204` `orm_disableVersionCheckError(true)`）
- **证据片段**:
  ```java
  leader.setLeaderEpoch(leader.getLeaderEpoch() + 1);   // 基于本地读到的旧 epoch +1
  ...
  leader.orm_disableVersionCheckError(true);            // 关闭乐观版本检查 → 后写覆盖先写
  dao.updateEntityDirectly(leader);
  if (leader.orm_readonly()) { ... return false; }
  onBecomeLeader(leaderEpoch);                          // 双方都可能走到这里
  ```
- **严重程度**: P2
- **并发场景**: leader 租约过期，follower A 与 follower B 同一轮询周期内 `checkFollower`→`changeLeader` 抢占
- **触发时序**: T1(A) 读 leader 行（epoch=N, expired）→ T2(B) 读同一行（epoch=N）→ T1 update（epoch=N+1, leaderId=A, 版本检查关闭→成功）→ T1 `orm_readonly()==false` → onBecomeLeader(N+1) → T2 update（同样基于 N+1? 不——T2 持有的实体是 T2 自己 session 读的 epoch=N 副本，写 epoch=N+1、leaderId=B，覆盖 T1 的行，版本检查关闭→成功）→ T2 onBecomeLeader(N+1) → **双节点以相同 epoch N+1 同时认为当选**。直至下一轮 checkLeader/checkFollower 各自发现 DB leaderId 与自身不符才自愈降级。
- **现状**: checkFollower 对“leader 已超时”分支 ：160 直接 changeLeader，无 `where expireAt=旧值` 或 epoch CAS 防护；版本检查被显式关闭。
- **风险**: 一个轮询周期内的双主窗口；leader-only 任务（协调、调度分派）非幂等时产生重复副作用。自愈有界（下轮检测），故不判 P1。
- **建议**: changeLeader 改为条件更新（`where leaderEpoch=N AND expireAt=旧值`，按影响行数判定当选），保留版本检查不关闭。
- **失效面枚举**: 当选路径全集：①`tryBecomeLeader` :168-186——saveEntityDirectly 插入，主键冲突互斥，安全；②`checkFollower`→`changeLeader`（expired 分支 ：160 与 leaderId==self 分支 ：144）——本条双主面；③`checkLeader`→`refreshLeader` :112-117——未关版本检查，leader 被抢时 update 抛版本异常→catch 全局→降级 follower，安全；④`restartElection` :220-238——epoch+1 驱逐，关闭版本检查但语义即“强迫所有人重选”，自洽。
- **信心水平**: 很可能（交错成立依赖两节点读取在同一过期窗口内，轮询对齐时必然）
- **误报排除**: 分布式选主的瞬态双主不是“平台基础设施自身实现”（本类是 nop-cluster 接口的 DB 实现，属被审模块）；已与误报校准第 5 条（基础设施不重审）区分。
- **复核状态**: 已保留

### [C2-005] SysSequenceGenerator.generateLong 在 synchronized(item) 内执行 REQUIRES_NEW DB 事务（含 session.lock 行锁等待）

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/seq/SysSequenceGenerator.java:182-201`（:185 synchronized(item)）、`:230-266`（syncFromDb→doSyncFromDb：runLocal = 新会话 + REQUIRES_NEW 事务 + `session.lock(seq)` :246）
- **证据片段**:
  ```java
  synchronized (item) {
      ...
      return syncFromDb(item);   // → doSyncFromDb → session.lock(seq); DB 往返 ×2（lock+update flush）
  }
  ```
- **严重程度**: P2
- **并发场景**: 多请求线程并发取同一序列（单据号热路径，cacheSize 耗尽时刻集中触发）；跨节点场景下 `session.lock` 还要等 DB 行锁
- **触发时序**: T1 耗尽批次进入 syncFromDb，`session.lock(seq)` 等待其他节点的事务（或 DB 慢查询）→ T2..Tn 全部阻塞在 `synchronized(item)` → JVM 内该序列所有取号请求排队时长 = DB 侧最长等待；DB 故障时锁持有时间不可界（直至事务超时）。
- **现状**: per-item 锁对“读改写 nextValue/usedCount”是必要的；问题是锁内包含跨网络 DB 往返与行锁等待，且 useDefault 回退路径让**多个未知序列名共享同一个 SEQ_DEFAULT item**（findSeqItem :282-287）——一个冷门序列的 DB 慢会拖住所有走默认序列的取号。
- **风险**: 单据号生成是主路径热点；DB 抖动被锁放大为线程池堆积。
- **建议**: 默认序列回退项按 seqName 各建 item（不共享同一锁对象）；DB 环节设置 statement/锁超时上限。结构性优化（批量段号预取队列）为 P3 候选，不在本条主张。
- **失效面枚举**: 取号入口两个：`generateLong` :183 / `generateString` :204——同型同病，逐一登记；`findSeqItem` :268-298 的 synchronized(this) 仅保护缓存装载，DB 查询也在其内（同族：:280 loadCacheItemFromDb 在 this 锁内）。
- **信心水平**: 确定
- **误报排除**: 非“低频路径粗锁”豁免——序列取号是每单据热路径；per-item 锁本身正确，报告的是锁内 I/O 代价（C2 第 4 步定向检查项）。
- **复核状态**: 已保留

### [C2-006] InMemoryCodeCache/DynCodeGen 全方法粗粒度 this 锁 + 锁内 DB I/O（设计声明下的争用登记）

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/InMemoryCodeCache.java`（13 处 synchronized；类注释 ：49“所有代码生成都使用synchronized保护，确保单线程生成”）、`DynCodeGen.java`（13 处 synchronized：generateForAllApps/generateForApp/generateForModule/generateBizModel×3/reloadModel×2/removeDynModule/getDynModule/loadAndCacheModuleModel/generateForAllModules/generateModuleToCache）
- **证据片段**:
  ```java
  // 注意：所有代码生成都使用synchronized保护，确保单线程生成   ← 类注释自声明
  public synchronized void addModule(...) { removeModule(...); genModuleCoreFiles(...); ... }
  public synchronized OrmModel getOrmModel(...) { return ormModels.computeIfAbsent(moduleId, k -> genOrmModel(...)); }
  ```
- **严重程度**: P3（互斥为声明设计；登记争用代价与锁内 I/O 面）
- **并发场景**: 共享缓存（无租户）下，任一管理端生成操作（generateBizModel 等，持 DynCodeGen→cache 双锁）与运行期懒加载（getOrmModel/getEnabledModule/getObjMeta 的 miss 路径，持 cache 锁）互相排队；懒生成在锁内做 `ormTemplate.runInSession` DB 查询 + XPL 生成（prepareBizObject :452-469）。
- **触发时序**: 非竞态缺陷：T1 管理端 generateBizModel 全量重建 → T2..Tn 动态实体首次访问触发 getOrmModel miss → 全部等待 T1 完成。互斥本身是声明意图（防并发生成撕裂），代价是生成期间动态模型整体不可用（读也排队）。
- **现状**: 已声明设计（按误报校准第 3 条不为优雅性报缺陷）；但“读请求在生成期被整体阻塞”的代价未见声明文档。
- **风险**: 大模型库（max-biz-objects 默认 1000）下全量生成秒级~十秒级，期间动态功能无响应。
- **建议**: 读写分离（生成独占、读走 volatile 快照引用）属结构优化；最低成本是文档声明“生成期间动态模型短暂不可用”。
- **失效面枚举**: 锁内 DB I/O 点清单：genModuleCoreFiles→prepareLoadModule（DB）:187、genOrmModel→prepareOrmModel（DB）:215、getObjMeta→genBizObjFiles（DB）:235、getEnabledModule→loadModule supplier（DB）:375、getOrmModel→genOrmModel（DB）:351、DynCodeGen.loadAndCacheModuleModel（DB）:383-388——逐一登记。
- **信心水平**: 确定（形态）/设计声明（判级依据）
- **误报排除**: 正因类注释声明了单线程生成意图，才不按缺陷报；本条是代价登记 + 文档缺口。
- **复核状态**: 已保留

### [C2-007] SysDaoLeaderElector.tryRenewLease 关闭版本检查无条件续期：stale leader 可延长对手租约

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/elector/SysDaoLeaderElector.java:119-127`
- **证据片段**:
  ```java
  leader.orm_disableVersionCheckError(true);
  leader.setRefreshTime(clock.getMinCurrentTime());
  leader.setExpireAt(new Timestamp(clock.getMaxCurrentTimeMillis() + this.getLeaseMs()));
  dao.updateEntityDirectly(leader);   // 仅写脏列 refreshTime/expireAt
  return !leader.orm_readonly();
  ```
- **严重程度**: P3
- **并发场景**: 旧 leader A 在 `checkLeader` 判定“可能已超时”进入 tryRenewLease（:101-102）的同时，follower B 已 changeLeader 接管（epoch 已变、leaderId=B）
- **触发时序**: T1(B) changeLeader 写入 leaderId=B/epoch=N+1 → T2(A) tryRenewLease：实体脏列仅 refreshTime/expireAt（leaderId 未在本地修改→不写）→ A 把**B 的行** expireAt 延长一个 lease → B 反而获得更长租约、或 A 误以为续约成功（`orm_readonly()==false` 返回 true）继续以 leader 身份运行至下轮 checkLeader 发现 leaderId 不符才降级。
- **现状**: 无条件续期不校验 epoch/leaderId 归属；返回值语义（readonly==false 即成功）无法发现归属已变。
- **风险**: 短暂的身份混淆与接管延迟，下一轮自愈；与 C2-004 同根（放弃版本防护），单列登记因后果更轻。
- **建议**: 续期同样走条件更新（where leaderId=self AND epoch=current）。
- **失效面枚举**: leader 续期路径仅此一处（refreshLeader :112-117 为“确定未超时”分支的正常续期，未关版本检查，安全）；checkLeader :96-109 两个续期分支逐一核对。
- **信心水平**: 很可能
- **误报排除**: 非低频管理操作——是选主周期任务路径；判 P3 因自愈快、副作用为延迟而非破坏。
- **复核状态**: 已保留

## 本维度不计入发现的验证结论

- synchronized 集合迭代纪律：全模块无 `Collections.synchronizedXxx`；CoWA（durableSubscriptions 值）迭代安全；`getBroadcastSubscriptions` 返回活列表在 dispatchBroadcastToSubscribers :395-408 遍历——CoWA 弱一致迭代，安全。
- `DefaultCodeRule.variables` CHM：无迭代点（仅 get/put/putAll/remove(name,value)），无锁需求。
- `computeIfAbsent` 审计：InMemoryCodeCache :351（getOrmModel——注释+测试钉死锁序）、:355（moduleStores，值构造轻量）、SysCompactExtFieldHelper :44/:46（构建期局部 map）、SysI18nMessageLoader :29（启动期）——均无递归/持锁重 I/O 例外。
- 双重检查锁定：DynCodeGen.getTenantCodeCache 配 AtomicReference（正确）；无未配 volatile 的 DCL。
- long/double 非原子读写、`++` 复合递增：SeqItem 字段全部锁内；`event.incRetryTimes()`（NonBroadcastEventProcessor :171/:198）单写者（claim 成功者）线程内，安全。

## 维度复核结论（第 4 阶段独立复核）

- 复核方式：7 条逐条回码验证。C2-001 对 ：142-157 重新取证并对照 ：262-272/:235-244 同族已修点；C2-002 全仓库 grep 确认 tryResetLease 唯一调用方为测试（TestSysDaoResourceLockManager:169，单次续约场景）；C2-004 复核 changeLeader 交错路径（:194-217 + checkFollower :144/:160 两入口）。
- 三态判定：已保留 7 / 已降级 0 / 已驳回 0。
- 判级复核：C2-001 维持 P1——测试文件（"check 审计 [P1] 同族"）证明 holderId 修复只覆盖了 releaseLock/tryResetLease 两站点，过期删除站点的并发竞态**无测试覆盖且未修复**（现有 takeover 测试均为顺序场景：先过期→再接管→后释放，无双竞争者交错）。C2-002 维持 P2（潜伏：无生产续约调用方，若上层引入 watchdog 续约则升 P1）。C2-004 维持 P2（瞬态双主、下轮自愈，前提为轮询对齐+租约过期）。
- 现状标注：C2-001 标注**已知未修复（同族修复 F3 遗留站点）**——同文件两处同族已修复且注释言明根因，本站点漏网；C2-002 标注**新发现（潜伏）**——代码注释自述该 API 前轮"连带修复"，version 回写缺口为本次新识别；C2-003~C2-005/C2-006/C2-007 标注**新发现**。

## 最终保留项

| 编号 | 标题 | 严重度 | 现状 | 复核状态 |
|------|------|--------|------|---------|
| C2-001 | 过期锁删除缺 holderId → 互斥破坏 | P1 | 已知未修复（F3 同族遗留，releaseLock/tryResetLease 已修） | 已保留 |
| C2-002 | tryResetLease 不回写 version → 二次续约必败 | P2 | 新发现（潜伏，无生产调用方） | 已保留 |
| C2-003 | DynResourceStore.getResource 全局串行热路径 | P2 | 新发现 | 已保留 |
| C2-004 | changeLeader 无 CAS fencing → 瞬态双主 | P2 | 新发现（家族关联 WI 1.6 fencing 族、WI 4.2 选主纪律族——均为异单元异站点） | 已保留 |
| C2-005 | synchronized(item) 内 DB 事务 + 默认序列共享 item | P2 | 新发现 | 已保留 |
| C2-006 | InMemoryCodeCache/DynCodeGen 粗锁+锁内 I/O（声明设计） | P3 | 新发现（代价登记） | 已保留 |
| C2-007 | tryRenewLease 无条件续期 → 延长对手租约 | P3 | 新发现 | 已保留 |
