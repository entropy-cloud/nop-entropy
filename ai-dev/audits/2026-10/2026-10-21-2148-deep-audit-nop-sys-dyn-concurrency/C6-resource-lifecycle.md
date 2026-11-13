# C6 资源生命周期与泄漏 — 初审（第 1 轮）

- 资源类型盘点：DB 行资源（NopSysLock 锁行、NopSysEvent/NopSysBroadcastEvent 事件行、NopSysChangeLog 审计行、NopSysServiceInstance 临时实例行）、内存缓存（InMemoryCodeCache 租户缓存、ruleCache、LocalCache×2）、周期任务句柄（Future×4）、全局注册（DictLoader、I18nMessageManager、ResourceTenantManager/ModuleManager、GlobalCacheRegistry、VirtualFileSystem in-memory layer）。

## 资源获取点配对表

| 资源 | 获取 | 释放 | 判定 |
|------|------|------|------|
| NopSysLock 锁行 | saveNew :204-224 | releaseLock :260-273 / forceUnlock / 过期争用删除 :146-154 | **无 TTL 清理任务 → C6-001** |
| NopSysEvent/BroadcastEvent 行 | saveMessage :463-495 | cleanupExpiredEvents :253-282（7d/30d 周期） | 有界，正确（有 TestSysEventCleanup） |
| NopSysChangeLog 审计行 | OrmEntityChangeLogInterceptor :34-48/:64-77 | **无任何清理/TTL/归档** | **C6-003（F4）** |
| NopSysServiceInstance 临时行 | registerInstance :105-121 | unregister + cleanup 周期 :89-101 | 有界，正确（unregister 吞异常由 cleanup 兜底） |
| 租户 InMemoryCodeCache | initTenantCache（注册 on-load） | clearForTenant/destroy :210-223 | **Caffeine 驱逐路径绕过生命周期 → C6-002** |
| Future×3（SysDaoMessageService） | doStart scheduleWithFixedDelay :217-229 | doStop cancel(false) :285-292 | 配对存在；cancel(false) 不等待在途任务（watch） |
| Future×1（SysDaoNamingService） | init :77 | destroy :81-87 | 配对正确 |
| 全局注册 | SysDictLoader init/destroy :44-59；DynOrmModelProvider register/unregister :33-51；DynCodeGen init/destroy :80-113 | — | 全部对称（含先逐租户 on-unload 再清空 ：104-109） |
| ORM 会话（saveDirectly 等） | runLocal/runInNewSession/runInSession | 模板封闭（lambda 返回即关） | 正确 |

## 发现条目

### [C6-001] NopSysLock 锁行无 TTL/清理任务：崩溃持有者 + 无人争用的资源行永久残留（F4 无界账本）

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/lock/SysDaoResourceLockManager.java`（全文件：仅有的删除点 forceUnlock :107-111、过期争用删除 :146-154、releaseLock :260-273）
- **证据片段**:
  ```java
  // 全文件对 NopSysLock 的删除仅三处：管理接口 forceUnlock、
  // tryLockWithLease 重试循环中对“已过期且正被争用”行的删除、releaseLock。
  // 无任何 scheduleWithFixedDelay / deleteByQuery(expireAt < now) 形态的后台清理。
  ```
- **严重程度**: P2
- **并发场景**: 长时运行集群：节点 A 对资源 R 获锁后进程崩溃（未走 releaseLock），此后 R 无新竞争者
- **触发时序**: 无需交错：A 崩溃 → R 的锁行 expireAt 已过但**无人再对 R 调 tryLockWithLease** → 该行永久驻留 sys_lock 表。每多一个“崩溃+不再争用”的资源就多一行，只增不删（F4 定向形态）。对照同模块 SysDaoMessageService 已实现 cleanupExpiredEvents（:253-282）+ SysDaoNamingService 已实现 cleanup（:89-101）——**同族资源均有清理，锁行没有**（F3 资源面漏项）。
- **现状**: 过期锁的删除是“争用触发式惰性清理”，对不再被争用的资源零覆盖。
- **风险**: 表膨胀污染（慢查询扫描面扩大）；运维误读（残留行看起来像被持有）；与 forceUnlock 人工清理形成隐性运维契约。
- **建议**: 仿照 SysDaoMessageService.cleanupExpiredEvents 增加周期任务 `delete where expireAt < now - grace`；或文档声明 forceUnlock 为必配运维动作。
- **失效面枚举**: 本模块 DB 行资源清理对照：①NopSysEvent——有 7d/30d 清理（已覆盖）；②NopSysBroadcastEvent——同上（已覆盖）；③NopSysServiceInstance——有 cleanup（已覆盖）；④NopSysChangeLog——无（C6-003 单列）；⑤NopSysLock——无（本条）。逐一登记。
- **信心水平**: 确定
- **误报排除**: 非“有界声明存在”豁免——无任何上限/TTL 配置声明；非争用自愈覆盖——惰性删除依赖后续争用这一前提对僵尸资源不成立。
- **复核状态**: 已保留

### [C6-002] 租户 InMemoryCodeCache 被 Caffeine 容量驱逐时绕过 on-unload 生命周期，且使用中实例被静默摘除

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:77-78`（tenantCache，`newConfig(CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE.get())`，默认 100，`nop-kernel/nop-core/.../CoreConfigs.java:107-109`）、`:130-136`（initTenantCache：逐模块 addModule→on-load.xpl）、`:210-223`（仅 clearForTenant/destroy 走 removeTenantCache+clear）
- **证据片段**:
  ```java
  private final ICache<String, AtomicReference<InMemoryCodeCache>> tenantCache = LocalCache.newCache("gen-code-cache",
          newConfig(CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE.get()), k -> new AtomicReference<>());
  // LocalCache → Caffeine maximumSize(100)：第 101 个租户进入即触发 LRU 驱逐
  ```
- **严重程度**: P2
- **并发场景**: 租户数 > 容器上限（默认 100）的多租户部署；驱逐发生在任意请求线程的 cache.get 内部（Caffeine 维护线程或读后清理）
- **触发时序**: ①生命周期不对称（F8）：租户初始化执行了全部模块的 on-load.xpl 与全局可见的代码生成；当租户数超 100，Caffeine 静默驱逐旧租户条目——**on-unload.xpl 永不执行**（驱逐不走 removeTenantCache），无日志无钩子。②使用中摘除：线程 T1 在 `getTenantCodeCache` 已取得 ref 并进入业务使用 → Caffeine 因容量驱逐该条目 → T1 继续对“已脱离缓存的实例”做 genBizObjFiles（写生成的资源/状态全部写进孤儿实例）→ T1 完成后下一请求 `get(tenantId)` 命中 loader 新建 AtomicReference → 全量重新初始化（逐模块 DB 查询+生成，秒级，且发生在**请求线程**而非后台）。T1 的写结果丢失。
- **现状**: 驱逐路径完全没有对应 removeTenantCache 的对称逻辑；初始化/清理两条手工路径对称（destroy :104-110 先逐租户 on-unload），唯独自动驱逐不对称。
- **风险**: 超过 100 租户后：周期性整租户重初始化延迟尖峰、on-unload 副作用（若有）丢失、并发写丢失。部署面默认配置即可触发。
- **建议**: 容器上限按部署租户数配置并文档声明驱逐语义；或在 eviction listener（Caffeine removalListener）里执行 cache.clear()+on-unload；或租户缓存改显式 LRU 管理以走 removeTenantCache。
- **失效面枚举**: 租户缓存入口全集：`getTenantCodeCache` :151-168（get+DCL）、`clearForTenant` :210-214（手工，对称）、`destroy` :105-110（手工，对称）、Caffeine 自动驱逐（无钩子，本条）；`getUsedTenantIds` :198-200 返回驱逐后残余集合（监控面失真）。
- **信心水平**: 确定（驱逐语义由 Caffeine maximumSize 决定）
- **误报排除**: 非“有界声明存在”豁免——上限确实配置了，但**驱逐副作用与生命周期契约冲突**（校准条款限“上限配置与使用方规模声明矛盾”之外的淘汰语义缺陷照报）；非内核实现重审——审计对象是本模块对 LocalCache 的**使用方式**（装载了带生命周期的重资源却依赖静默驱逐的缓存）。
- **复核状态**: 已保留

### [C6-003] NopSysChangeLog 审计表无清理/TTL/归档路径，且每实体每保存逐列放大写入（F4+F7 同族）

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/log/OrmEntityChangeLogInterceptor.java:34-48`（postSave 逐列 INSERT）、`:64-77`（postUpdate 逐脏列 INSERT）；全模块无 NopSysChangeLog 删除点（grep 证据：dao 层仅本拦截器写入）
- **证据片段**:
  ```java
  entityModel.getColumns().forEach(col -> {
      ...
      NopSysChangeLog log = changeLog.cloneInstance();
      log.setPropName(col.getName());
      log.setNewValue(ConvertHelper.toString(entity.orm_propValue(propId), ""));
      saveDirectly(dao, log);   // 每列一行、每行一个 runInNewSession
  });
  ```
- **严重程度**: P2
- **并发场景**: 长时运行 + 高频写入开启 `audit-save/audit` 标签的实体（每请求路径）
- **触发时序**: 无需交错：每次 save 写 列数 行、每次 update 写 脏列数 行；无任何 deleteByQuery/归档作业。事件表（NopSysEvent）在同模块已实现 7 天保留清理（SysDaoMessageService :106-108 声明“到期清理防止事件表无限膨胀拖垮轮询扫描”），**同族审计表没有对应机制**——F4 定向检查命中。
- **现状**: 写入语义（审计留痕）可能有意长期保留；但“无 TTL、无归档、无上限”三无状态未被任何文档/配置声明为有意边界。
- **风险**: 表线性膨胀拖垮审计查询与主库空间；`saveDirectly` 每列一次 `runInNewSession`（:108-111）——一次保存 N 列 = N 个独立会话+事务，写放大与连接占用随列数倍增（资源开销面）。
- **建议**: 增加 retention 配置 + 周期清理（照抄 SysDaoMessageService.cleanupExpiredEvents 形态）；saveDirectly 批量化（单会话批量插入同实体列行）。
- **失效面枚举**: 写入点全集：postSave :34-48、postUpdate :64-77、postDelete :81-96（单行）——三者均走 saveDirectly，均无清理对面；删除点：**全模块零**。
- **信心水平**: 确定
- **误报排除**: 非已声明边界——同模块事件表专门建了清理机制并写明动机，审计表无对称声明；审计留痕不豁免 F4（要求的是“有清理/TTL/归档路径或显式声明”）。
- **复核状态**: 已保留

### [C6-004] SysDaoMessageService.doStop cancel(false) 不等待在途周期任务，停止窗口内任务仍可触达已停服务

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:284-292`（对照 doStart :217-229）
- **证据片段**:
  ```java
  public void doStop() {
      if (checkBroadcastFuture != null) checkBroadcastFuture.cancel(false);
      if (checkNonBroadcastFuture != null) checkNonBroadcastFuture.cancel(false);
      if (cleanupFuture != null) cleanupFuture.cancel(false);
  }
  ```
- **严重程度**: P3
- **并发场景**: 应用停机（LifeCycleSupport.stop）时周期任务正在 process()/cleanupExpiredEvents() 中途
- **触发时序**: T1 轮询线程在 process() 的 claim+dispatch 中途 → T2 doStop cancel(false)（不中断）→ T1 继续消费、发送 ack 消息、更新事件行，直至本轮完成——停机序列上后于 doStop 执行的销毁步骤（如数据源关闭）与其竞态。任务自身每次 try/catch 全包（:294-303/:365-373），不会因中断标记抛错。
- **现状**: 无 awaitTermination/draining；任务跑在 globalWorker（非自有池），无法对池 await。
- **风险**: 停机窗口内重复消费/迟到写入，通常被幂等消费吸收；优雅停机不彻底。
- **建议**: doStop 后对三个 future `awaitDone`（或记录在途计数栅栏），作为 P3 改进。
- **失效面枚举**: 三个周期入口逐一登记：processBroadcastEvent/processNonBroadcastEvent/cleanupExpiredEvents——均同型；SysDaoNamingService.destroy :81-87 同为 cancel(false)（同族，一并改进）。
- **信心水平**: 确定
- **误报排除**: 非“池随 bean 泄漏”——用的是全局池，无泄漏；登记为停机语义细化。
- **复核状态**: 已保留

### [C6-005] BroadcastEventProcessor.lastEvent 持实体作游标：游标行被保留期清理删除后，重启前窗口依赖 findNext 的内核语义

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/BroadcastEventProcessor.java:30`（字段）、`:63`（`dao.findNext(lastEvent, filter, null, fetchSize)`）、对照清理 `SysDaoMessageService.cleanupExpiredEvents :276-278`（7 天删广播行）
- **证据片段**:
  ```java
  private NopSysBroadcastEvent lastEvent;   // 长生命周期字段，持有已清理行的实体快照
  ...
  List<NopSysBroadcastEvent> batch = dao.findNext(lastEvent, filter, null, fetchSize);
  ```
- **严重程度**: P3
- **并发场景**: 单轮询线程顺序执行（无并发）；风险是**时间跨度状态**：lastEvent 所指行在 7 天保留期后被 cleanupExpiredEvents 物理删除，而游标继续以该实体为 seek 起点。
- **触发时序**: 非交错竞态：进程长驻且某 topic 停更 >7 天 → 游标行被删 → 下轮 findNext 以已删实体为界。若内核 findNext 按 (eventTime, id) 比较式 seek 则行为正确（行存在与否无关）；若依赖主键存在性则可能退化为从头扫描或漏扫。内核语义未在本模块验证，登记为依赖假设。
- **现状**: 内存中仅持快照值（detached 实体），无 DB 依赖读——最可能安全；但无测试覆盖“游标行已删”场景。
- **风险**: 极端停更场景下广播扫描行为未定义（漏扫/重扫），无可观测信号。
- **建议**: 游标改存 (eventTime,id) 值对而非实体；补一条“清理游标行后继续扫描”的行为钉死测试。
- **失效面枚举**: 游标状态点：startTime :29（时间戳，清理无关，安全）、lastEvent :30（本条）；同类“实体作长期状态”扫描：SysDaoMessageService 无；NonBroadcastEventProcessor 无游标（每次全条件查询）。
- **信心水平**: 有趣的猜测（内核 findNext 语义待证）
- **误报排除**: 单线程访问无并发缺陷；按“写不出具体时序降级”精神登记 P3 并显式标注依赖假设。
- **复核状态**: 已保留

## 本维度不计入发现的验证结论

- 注册对称性核查（F8 全量）：SysDictLoader addDictLoader/removeDictLoader :44-59 ✅；DynOrmModelProvider GlobalCacheRegistry register/unregister :33-51 ✅；DynCodeGen ResourceTenantManager/ModuleManager set/null :91-94/:97-102 ✅（含 VirtualFileSystem.updateInMemoryLayer(null) :111 与 ModuleManager.updateDynamicModules(null) :112）；SysDaoMessageService 的 localService.subscribe 与 DurableSubscription.cancel 委托对 ✅（map 层竞态另立 C1-002）。
- InMemoryCodeCache.addModule :127-133 先 removeModule：对未启用模块 removeModule 直接 return（:136-138），不会误跑 on-unload——on-load/on-unload 对称 ✅。
- 临时文件：dyn 模块 importExcel 用 fileStore.detachFile 主动接管控件（NopDynModuleBizModel :90）✅。
- close 幂等：SysDaoMessageService.doStop/repeat destroy（DynCodeGen.destroy :97-113 幂等置空）✅。

## 维度复核结论（第 4 阶段独立复核）

- 复核方式：5 条逐条回码验证。C6-001 全仓库 grep 确认 NopSysLock 删除点仅 3 处且无周期清理（与同模块 SysDaoMessageService/SysDaoNamingService 已有清理形成 F3 对照）；C6-002 复核 LocalCache→Caffeine maximumSize 链路（LocalCache.java:83）与 CoreConfigs.java:107-109 默认值 100；C6-003 grep 确认 NopSysChangeLog 全模块零删除点。
- 三态判定：已保留 5 / 已降级 0 / 已驳回 0。C6-005（lastEvent 游标）维持 P3 但标注**信心水平=有趣的猜测**（依赖内核 findNext 对已删游标行的语义，未经证实），裁定阶段按 watch-only 处置。
- 判级复核：三条 P2 维持。C6-002 特别复核了"驱逐是否真实发生"：Caffeine maximumSize(100) 配置存在即驱逐存在，非理论。
- 现状标注：5 条全部**新发现**（cra-index 全campaign grep：NopSysLock/租户驱逐/ChangeLog 留存均无前案登记）。C6-002 与 C6-102（R2）构成同一生命周期缝隙的两侧（驱逐侧+冷启动侧），裁定阶段保留为独立工单（修复面不同：removalListener vs 预算/异步化）。

## 最终保留项

| 编号 | 标题 | 严重度 | 现状 | 复核状态 |
|------|------|--------|------|---------|
| C6-001 | NopSysLock 行无 TTL 清理（F4） | P2 | 新发现（同模块对照：event/naming 均有清理） | 已保留 |
| C6-002 | 租户缓存 Caffeine 驱逐绕过 on-unload 生命周期 | P2 | 新发现（全campaign 无前案） | 已保留 |
| C6-003 | NopSysChangeLog 无留存机制+逐列写放大（F4） | P2 | 新发现（全campaign 无前案） | 已保留 |
| C6-004 | doStop cancel(false) 不等在途任务 | P3 | 新发现 | 已保留 |
| C6-005 | lastEvent 游标行被清理后的 findNext 语义 | P3（watch） | 新发现（依赖内核语义，未证实） | 已保留（watch-only） |
