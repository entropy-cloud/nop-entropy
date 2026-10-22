# C1 共享可变状态与可见性 — 初审（第 1 轮）

- 审计单元：nop-sys（api/dao/service/app）+ nop-dyn（api/dao/service/app），src/main 排除 `_gen/`
- 审读方式：8 源码根逐文件审读（245f/21,844 行）；样板 entity/bean/API 类按五类载体清单扫描，逻辑类全文精读
- 盲审纪律：未读 audits/plans/bugs/lessons/backlog，未看 git log

## 共享可变状态清单（字段 × 写者 × 读者 × 同步机制 × 判定）

| # | 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|------|------|------|---------|------|
| 1 | `InMemoryCodeCache.mergedStore`/`dynResourceStore`（volatile :78/:79） | synchronized 方法群 + genBizObjFiles 恢复逻辑 :320-328 | VFS 请求线程、getMergedStore() | volatile 发布（发布安全 OK），**但发布后仍被原地修改（merge/addResource）** | **缺陷 C1-001** |
| 2 | `InMemoryCodeCache.moduleStores/enabledModules/bizModels/ormModels`（CHM :66-73） | synchronized 方法群 | getBizModel/getDynBizModels 等无锁读 | CHM 弱一致 | 可接受（弱一致读） |
| 3 | `InMemoryCodeCache.scope`（:61，非线程安全 IEvalScope） | 全部 synchronized 方法内 setLocalValue | 同上（持锁读） | this 锁串行 | 畸形共享但锁内安全；跨代残留 C1-006 |
| 4 | `SysDaoMessageService.durableSubscriptions`（CHM<String,CoWA> :143） | subscribe :569 / DurableSubscription.cancel :610-618 | 轮询线程 invokeDurableConsumers :510 | CHM+CoWA，但 cancel 的复合序列非原子 | **缺陷 C1-002** |
| 5 | `SysDaoMessageService.nonBroadcastProcessor/broadcastProcessor`（:133/:131，非 volatile） | ensure*Processor 懒初始化 :306/:376 | timer 线程 + 批量 XLang 线程 | 无 | **缺陷 C1-003** |
| 6 | `SubscriptionState.suspended`（volatile :651） | suspend/resume | 轮询分发线程 | volatile | 正确（注释自证多线程契约） |
| 7 | `SysCompactExtFieldHelper.entityFieldPositions/entityFieldConfigs`（volatile CHM :24/:25） | refreshCache 写时复制整体替换 :50-52 | getExtValue/getPosition 无锁读 | volatile + 不可变快照引用 | 正确；双字段替换瞬态错配 C1-004 |
| 8 | `SysSequenceGenerator.cache`（CHM :56）+ `defaultCache`（LocalCache TTL :58） | findSeqItem synchronized(this) 内 put / clearCache :97 / removeCache :101 | generateLong 每次取号 | CHM + per-item synchronized(item) | 基本正确；clearCache 不清 defaultCache C1-007 |
| 9 | `SeqItem` 可变字段（nextValue/usedCount/cacheSize/stepSize） | generateLong/generateString/doSyncFromDb（均 synchronized(item)） | 同左 | per-item 锁 | 正确 |
| 10 | `DynCodeGen.tenantCache`（LocalCache<String,AtomicReference> :77） + `getTenantCodeCache` synchronized(ref) DCL :160-167 | initTenantCache | 全部请求线程 | AtomicReference + DCL（有并发测试钉死） | 正确；驱逐面见 C6 |
| 11 | `DynOrmModelProvider.entityModelLoader`（:31，非 volatile） | getEntityModelLoader 懒初始化 :41-45 | 同左 | 无 | C1-005 |
| 12 | `BroadcastEventProcessor.startTime/lastEvent`（:29/:30） | process（单 timer 线程） | 同左 | 线程封闭 | 正确（单写者） |
| 13 | `SysCodeRuleGenerator.ruleCache`（Caffeine :47） | generate getIfPresent→put :81-90 | 同左 | Caffeine 原子；检查-然后-放入竞态=重复 DB 读，幂等 | 可接受 |
| 14 | `DefaultCodeRule.variables`（CHM :28） | addVariable/setVariables | generate :127 | CHM | 正确 |
| 15 | `NopSysApplication/NopDynApplication.globalArgs`（static :22/:60） | main（Quarkus 启动线程） | start @Observes | Quarkus 启动同步（HB） | 平台样板惯例，watch-only |
| 16 | `OrmModelToDynEntityMeta`/`DynEntityMetaToOrmModel` 实例字段 | 每次 transform 前 new（调用点 :88/:104/:165/:479） | 同实例单线程 | 线程封闭 | 正确 |

## 发现条目

### [C1-001] 已发布 mergedStore 在读线程并发期间被原地结构修改（非线程安全 TreeMap）

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/InMemoryCodeCache.java:307-332`（merge 恢复逻辑）、`:419-425`（addToMergedStore）；被修改对象 `nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/ResourceTreeNode.java:38`（`private Map<String, ResourceTreeNode> children`，普通 TreeMap、懒初始化、无任何同步）
- **证据片段**:
  ```java
  // InMemoryCodeCache.genBizObjFiles（持有 cache this 锁）
  InMemoryResourceStore publishedMerged = this.mergedStore;
  ...
  if (publishedMerged != null) {
      InMemoryResourceStore moduleStore = moduleStores.get(module.getModuleId());
      if (moduleStore != null)
          publishedMerged.merge(moduleStore);   // 对“已发布、正被 VFS 并发读”的 store 做结构性修改
      this.mergedStore = publishedMerged;
  ```
  ```java
  // ResourceTreeNode（内核，非线程安全）
  private Map<String, ResourceTreeNode> children = null;   // 懒初始化 new TreeMap<>()
  ```
- **严重程度**: P1
- **并发场景**: HTTP 请求线程经 VFS/`DynResourceStore.getResource` 遍历 mergedStore 读取动态模型资源 × 管理端保存实体触发 `generateBizObjFiles` 的线程对同一 store 执行 `merge`/`addResource`
- **触发时序**: T1 请求线程在 `ResourceTreeNode.getNode` 遍历 `children`（TreeMap.get，无锁）→ T2 持 cache 锁执行 `publishedMerged.merge(moduleStore)`，路径上某节点 `children==null` 懒初始化 `new TreeMap<>()` 后 `put`（ResourceTreeNode.java:112-116），或中间节点 `children.put` 结构性修改 → T1 读到撕裂的树结构：丢节点（生成的 xmeta/orm 资源“闪现闪失”）、或 TreeMap 并发结构修改导致的未定义行为（错误返回/遍历异常/罕见死循环）。
- **现状**: 类注释自声明“最终发布到虚拟文件系统中的是一个合并后的只读副本，避免出现并发更新的情况”（:45），而 :320-328 的懒生成恢复路径与 ：419-425 `addToMergedStore` 明确对已发布实例做原地修改，**违反本类自声明的只读发布契约**。
- **风险**: 动态模型热路径（每次动态实体/页面资源解析）在管理端任一次保存/生成期间读到不一致资源树；症状为偶发资源缺失或解析异常，极难复现定位。
- **建议**: 恢复“写时复制发布”：merge 到新建 store 后整体替换 volatile `mergedStore` 字段（沿用 ：50-52 SysCompactExtFieldHelper 的写时复制模式），或改用内核并发树/对 InMemoryResourceStore 内部结构加读写锁。
- **失效面枚举**（F3 同族清扫）: ①`genBizObjFiles` :320-328 merge+restore——本条主体；②`genViewFile` :259 / `genPageFile` :295 `addToMergedStore(resource)`——同一已发布 store 的 addResource，同族同病；③`DynResourceStore.getChildren`（DynResourceStore.java:31-33）完全无锁直透底层 store——同族入口；④`clearMergedStore` :334-337 仅置空引用，不修改已发布实例——安全；⑤`getResourceStore` :400-417 首次构建后发布——发布安全（volatile+锁内构建）正确。②③逐一登记为同族未修点。
- **信心水平**: 确定
- **误报排除**: 非“CHM 本身使用”误报——被并发修改的是普通 TreeMap；非低频冷路径——mergedStore 服务于全部动态模型资源读取；且违反的是本类 javadoc 的显式契约（属于“守卫缺失/契约被自身代码破坏”，非已声明边界）。
- **复核状态**: 已保留

### [C1-002] DurableSubscription.cancel 与 subscribe 并发：订阅者被孤儿化，消息被置 PROCESSED 静默丢弃

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:608-619`（cancel）、`:565-579`（subscribe）、`:509-515`（invokeDurableConsumers 丢弃点）
- **证据片段**:
  ```java
  public void cancel() {
      delegate.cancel();
      List<SubscriptionState> subscriptions = durableSubscriptions.get(state.topic);
      if (subscriptions != null) {
          subscriptions.remove(state);
          if (subscriptions.isEmpty()) {
              durableSubscriptions.remove(state.topic);   // 复合序列非原子
  ```
  ```java
  // invokeDurableConsumers
  List<SubscriptionState> subscriptions = durableSubscriptions.get(topic);
  if (subscriptions == null || subscriptions.isEmpty()) {
      LOG.warn("nop.message.ignore-message-when-no-consumer:topic={},message={}", topic, message);
      return null;   // 事件随后被置 PROCESSED 丢弃
  ```
- **严重程度**: P1
- **并发场景**: 线程 A 调用 `IMessageSubscription.cancel`（应用关闭/重订阅）× 线程 B 同 topic 调用 `subscribe` × 轮询线程 dispatch
- **触发时序**: T1（cancel）执行 `subscriptions.remove(state)` 后 list 变空、尚未执行 `durableSubscriptions.remove(topic)` → T2（subscribe）执行 `computeIfAbsent(topic, ...)` 命中同一个已变空的 list 并 `add(state2)` → T1 恢复执行 `isEmpty()→true`，`durableSubscriptions.remove(topic)` 把 T2 刚注册的条目整个从 map 摘除（且 ：617 `localService.getConsumers().remove(topic, new CopyOnWriteArrayList<>())` 因 list 非空而失败，轮询集合仍含该 topic）→ 此后所有到达事件在 ：510 处 `get(topic)==null` → warn 后事件被认领置 PROCESSED 丢弃。订阅者 state2 存活却永远收不到消息。
- **现状**: subscribe/cancel 各自的单步操作原子（CHM/CoWA），但“remove→判空→remove 键”的复合序列与 computeIfAbsent+add 交错无互斥。
- **风险**: 消息语义违约（持久订阅者静默丢消息），仅靠 warn 日志可见；持续到该订阅者再次 cancel/resubscribe。
- **建议**: cancel 的“判空+摘键”用 `durableSubscriptions.compute(topic, (k, v) -> v.isEmpty() ? null : v)` 单步完成（与 subscribe 的 computeIfAbsent 同键互斥），或在 subscribe 侧对空 list 做 remove-then-put 修复。
- **失效面枚举**: ①`DurableSubscription.cancel` :608-619——本条主体；②`subscribe` :566-579——交错对端；③`resolveSubscriberId` :581-589 读 size 生成索引名——同 map 上的非原子读，仅致 ID 重复（无害，登记不立项）；④broadcast 路径 `getBroadcastSubscriptions` :497-503 只读——安全。
- **信心水平**: 确定
- **误报排除**: 不是“CHM 本身使用”——缺陷在复合操作序列；不是低频无关路径——cancel/resubscribe 是消息服务的常规生命周期操作。
- **复核状态**: 已保留

### [C1-003] ensure*Processor 检查-然后-行动懒初始化无同步（字段非 volatile）

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:305-325`（ensureNonBroadcastProcessor）、`:375-386`（ensureBroadcastProcessor）
- **证据片段**:
  ```java
  private void ensureNonBroadcastProcessor() {
      if (nonBroadcastProcessor == null) {
          nonBroadcastProcessor = new NonBroadcastEventProcessor(...);   // 无锁、字段非 volatile
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 定时轮询线程（`processNonBroadcastEvent`，doStart 后 500ms 即触发）× 批处理 XLang 线程首次调用 `claimNonBroadcastEvents`（nop-batch-sys batch.xml 入口，:327-333 注释自证存在该并发入口）
- **触发时序**: T1（batch 线程）执行到 ：306 判空为 null → T2（timer 线程）同时判空为 null → 双方各自 new 一个 processor → T1 的实例仅用于当次 claim 后丢弃，T2 的实例驻留字段。两个实例参数相同、claim 走 DB 乐观版本检查，无数据损坏；但初始化期两实例可能并发扫描同一批事件（fetch 后 claim 版本检查兜底，仅浪费一次扫描）。此外字段非 volatile，理论上存在读到半构造引用的 JMM 窗口。
- **现状**: 典型 F10 检查-然后-行动守卫缺失；后果被 DB 层 claim 原子性削弱。
- **风险**: 启动竞态期短暂双实例；无持久化副作用；主要损失是可观测性（日志来自两个实例）与理论可见性风险。
- **建议**: 参照同文件 `durableSubscriptions.computeIfAbsent` 模式，或按 `DynCodeGen.getTenantCodeCache` 的 synchronized(ref) DCL（:160-167，已有并发测试钉死）改造。
- **失效面枚举**: ①`ensureNonBroadcastProcessor` :305-325；②`ensureBroadcastProcessor` :375-386——同族同型，逐一登记；同仓库正确范本：`DynCodeGen.getTenantCodeCache` :151-168（已修，有 TestDynCodeGenTenantCacheInit 保护）。
- **信心水平**: 确定（竞态存在）；后果轻微（限定 P2 依据）
- **误报排除**: 非无状态单例误报——字段是可变懒初始化引用；非仅启动单线程——batch.xml 的 XLang 调用入口使其可与 timer 并发。
- **复核状态**: 已保留

### [C1-004] SysCompactExtFieldHelper.refreshCache 双 volatile 替换之间的瞬态错配

- **文件**: `nop-sys/nop-sys-service/src/main/java/io/nop/sys/service/impl/SysCompactExtFieldHelper.java:32-56`
- **证据片段**:
  ```java
  // 写时复制整体替换：clear与putAll之间存在空窗，并发读会短暂读到空映射
  this.entityFieldPositions = positions;
  this.entityFieldConfigs = configs;
  ```
- **严重程度**: P3
- **并发场景**: 管理端 afterEntityChange 触发 refreshCache（NopSysCompactExtFieldBizModel:35）× 业务请求线程 getExtValue/getExtValues
- **触发时序**: T2 执行完第一行（positions 已换新、configs 仍旧）→ T1 读取新 positions 命中新增字段位置 → 走 `getDefaultValue` 查旧 configs 得 null（本应得配置默认值）。窗口为两条赋值之间（纳秒级），仅在字段新增/删除时出现。
- **现状**: 注释已意识到空窗问题并采用整体替换，但双字段无法原子替换。
- **风险**: 单次请求短暂读到 null 默认值；无持久化影响。
- **建议**: 将两 map 合并为单一不可变快照对象（record Snapshot(positions, configs)），一次 volatile 写发布。
- **失效面枚举**: 全类仅此一处双字段替换点（:51-52）；`getExtValue`/`getExtValues`/`getPosition`/`getDefaultValue` 均为单字段 volatile 读——逐一核对无其他错配面。
- **信心水平**: 确定
- **误报排除**: 非 volatile CHM 原语误报——是双变量替换的组合一致性问题；时序写不出更重后果故按口径不升 P2。
- **复核状态**: 已降级（watch-only：可观测后果限于"新增字段在两次 volatile 写之间被读到 null 默认值"的纳秒级窗口，无状态损坏面；不作为缺陷立项，随 C1-005 建议项——快照对象化——一并消化）

### [C1-005] DynOrmModelProvider.entityModelLoader 懒初始化无同步非 volatile

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynOrmModelProvider.java:41-45`
- **证据片段**:
  ```java
  public IDynamicEntityModelProvider getEntityModelLoader(){
      if(entityModelLoader == null)
          entityModelLoader = BeanContainer.getBeanByType(IDynamicEntityModelProvider.class);
      return entityModelLoader;
  }
  ```
- **严重程度**: P3
- **并发场景**: 多个 ORM 请求线程首次触发动态实体模型解析
- **触发时序**: 理论风险，未具体化为数据损坏路径：两线程可重复执行 getBeanByType（幂等，容器返回同一单例）；非 volatile 写读在 JMM 下理论上可读到非 null 但字段未完全可见的引用——实际容器侧已安全发布该单例，且对象无构造后可变状态被本类修改。
- **现状**: 循环依赖规避的权宜懒加载（注释自证），无正确性损害的具体时序。
- **风险**: 无实害；仅理论可见性。
- **建议**: 声明为 volatile（一行改动）消除 JMM 争议。
- **失效面枚举**: 全类仅此一个懒初始化字段；`cache`（CacheEntryManagement :29）由内核管理线程安全。
- **信心水平**: 确定（形态存在）/后果为理论
- **误报排除**: 按“写不出具体时序的降 P2”口径应降级，但本条连具体交错后果都无法构造（重复查找幂等），降 P3 并登记。
- **复核状态**: 已保留

### [C1-006] InMemoryCodeCache.scope 跨代共享且仅 clear() 清理：模板变量跨生成残留与引用滞留

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/InMemoryCodeCache.java:61`（`private final IEvalScope scope = XLang.newEvalScope()`）、`:99`（仅 clear() 中 scope.clear()）
- **证据片段**:
  ```java
  private final IEvalScope scope = XLang.newEvalScope();
  ...
  public synchronized void clear() { ... scope.clear(); }   // 仅全清入口
  // genViewFile/genPageFile/genModuleCoreFiles/runXpl/hook 回调均向同一 scope setLocalValue
  ```
- **严重程度**: P3
- **并发场景**: 单线程（this 锁串行化全部访问），无竞态；问题是**跨调用生命周期**：`prepareResource` 设置的 `VAR_PAGE_NAME`（DynCodeGen.java:523）、`VAR_OBJ_META`（:529）等局部变量在下一次不相关生成（如 genModuleCoreFiles）中仍然残留可读。
- **触发时序**: 非交错竞态，而是顺序复用：prepareResource(pathA) 设置 VAR_PAGE_NAME=pageA → 后续 genPageFile(pageB) 执行前若模板在某分支未覆盖读取该变量 → 读到 pageA 的残留值。当前模板均先 set 后用，未复现实害，故为防御性登记。
- **现状**: 依赖“所有模板先写后读”的隐式纪律，无结构保证。
- **风险**: 新增模板读取未设置变量时静默拿到上一代残留值（生成内容串味）；scope 持有 ModuleModel/entityMeta 引用滞留至 clear()（缓存生命周期内小量滞留）。
- **建议**: 每次生成入口使用 `XLang.newEvalScope()` 局部 scope（对象小，代价可忽略），或 genXxx 入口统一 scope.clear()。
- **失效面枚举**: 写点清单：runXpl :168-169、genModuleCoreFiles :193-194、genViewFile :252、genPageFile :288、buildGenerator :362-363、DynCodeGen.prepareLoadModule :436、prepareBizObject :457-466、prepareOrmModel :476-480、prepareResource :523/:529——全部登记，均共享同一 scope。
- **信心水平**: 很可能（滞留确定，实害未复现）
- **误报排除**: 非并发竞态误报（已声明锁内串行）；登记为长生命周期状态卫生问题。
- **复核状态**: 已保留

### [C1-007] SysSequenceGenerator.clearCache/removeCache 只清 cache 不清 defaultCache（F3 同族漏清）

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/seq/SysSequenceGenerator.java:97-103`（对照 `:56` cache、`:58-59` defaultCache、`:285/:290` defaultCache.put）
- **证据片段**:
  ```java
  public void clearCache() { cache.clear(); }
  public void removeCache(String cacheKey) { cache.remove(cacheKey); }
  // 而 findSeqItem :285/:290 会向 defaultCache 放入未知序列名与回退项
  ```
- **严重程度**: P3
- **并发场景**: 管理端保存 NopSysSequence（NopSysSequenceBizModel.afterEntityChange :37-42 调 removeCache）× 取号线程。非竞态而是失效不彻底。
- **触发时序**: 管理端对 SEQ_DEFAULT（或任何曾走 defaultCache 回退的序列名）修改 nextValue/cacheSize → removeCache 只清主缓存 → 取号线程 `findSeqItem` 在 cache miss 后从 defaultCache 取到**旧 SeqItem 实例** → 缓解：`syncFromDb→doSyncFromDb` 会 `item.update(seq)` 从 DB 刷新——仅当 usedCount 耗尽或非缓存路径触发 syncFromDb 时；在 cacheSize>0 的消耗窗口内仍按旧轨迹发号，最长 TTL 60s。
- **现状**: 双缓存只清理其一；defaultCache 有 TTL 兜底（60s），自愈有界。
- **风险**: 管理端“回拨 nextValue 修重号”的操作在最长 60s 内对回退序列名不生效（NopSysSequenceBizModel javadoc :24-25 声明的修复语义打折）。
- **建议**: clearCache/removeCache 同步 `defaultCache.invalidate(name)` / `invalidateAll()`。
- **失效面枚举**: ①`clearCache` :97-99；②`removeCache` :101-103——两处同族漏清；唯一 defaultCache 失效路径是其自身 TTL。
- **信心水平**: 确定
- **误报排除**: 非低频无关路径——正是管理端修复重号的语义路径；有 TTL 兜底故不升 P2。
- **复核状态**: 已保留

### [C1-008] NopDynModuleBizModel.generateByAI moduleName 去重为 TOCTOU（表无唯一索引，注释自证）

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/entity/NopDynModuleBizModel.java:151-155`（检查）、`:157-172`（保存+发布）
- **证据片段**:
  ```java
  // moduleName没有唯一索引约束，重复调用会创建并发布多个同名模块，这里显式去重
  NopDynModule example = dao.newEntity();
  example.setModuleName(moduleName);
  if (dao.findFirstByExample(example) != null)
      throw new NopException(ERR_DYN_MODULE_NAME_EXISTS)...
  ... dao.saveEntity(entity); dao.flushSession();
  dynCodeGen.generateForModule(entity);
  ```
- **严重程度**: P2
- **并发场景**: 两个管理员/自动化流程并发调用同一 moduleName 的 generateByAI mutation
- **触发时序**: T1 执行 ：154 检查为 null → T2 执行 ：154 检查亦为 null → 双方各自 saveEntity+发布 → 两行同名模块均 MODULE_STATUS_PUBLISHED → 双方 generateForModule：codeCache 以 moduleId 为键，后写覆盖先写（enabledModules），先写模块的 bizModels 残留（bizModels 不随 enabledModules 覆盖而清理，removeModule 才清）→ 模型集合处于混合状态直至下次全量 reload。
- **现状**: 已识别重复风险并加了应用层去重，但检查-保存无互斥、无唯一索引兜底。
- **风险**: 低频管理操作；后果为重复模块行 + 动态模型混合态，需人工清理。
- **建议**: moduleName 加唯一索引（ORM 模型变更走 plan-first）或对 generateByAI 加分布式锁/单飞。
- **失效面枚举**: ①`generateByAI` :151-172——本条；②`importExcel` :68-95——无去重（语义为导入允许重复），登记为有意差异非缺陷；③`checkModuleIdNotShadowingStatic` :118-136 静态模块遮蔽检查——同为检查-然后-使用，但其对照集（静态模块）运行期不变，无 TOCTOU 实害。
- **信心水平**: 确定
- **误报排除**: 非已声明边界——注释表明作者知道无唯一索引并试图用应用层检查弥补，弥补本身有竞态。
- **复核状态**: 已保留

### [C1-009] NopSysUserVariableBizModel.setVar get-then-save TOCTOU

- **文件**: `nop-sys/nop-sys-service/src/main/java/io/nop/sys/service/entity/NopSysUserVariableBizModel.java:46-53`
- **证据片段**:
  ```java
  NopSysUserVariable var = dao().getEntityById(id);
  if (var == null) {
      var = new NopSysUserVariable(); ...
      dao().saveEntity(var);
  }
  ```
- **严重程度**: P3
- **并发场景**: 同一用户双开页面并发 setVar 同名变量
- **触发时序**: T1/T2 同时 getEntityById 均为 null → 双方 saveEntity → 后者 (userId,varName) 主键冲突报错。用户重试即成功（此时 var 已存在）。
- **现状**: 无 upsert/捕获重复键重试。
- **风险**: 用户可见偶发错误，无数据损坏。
- **建议**: 捕获 ERR_SQL_DUPLICATE_KEY 后转 update 重试，或改 upsert 语义。
- **失效面枚举**: 全模块同类 get-then-save 形态清单：`setVar`（本条）、`SysDaoNamingService.registerInstance` :111-120（同型，实体注册重复键；实例注册框架自动重试心跳，登记不立项）、`SysCodeRuleGenerator.generate` :81-90（Caffeine put 幂等，安全）。
- **信心水平**: 确定
- **误报排除**: 幂等重试可自愈、无状态损坏，按校准口径 P3。
- **复核状态**: 已保留

## 本维度不计入发现的验证结论

- `DynCodeGen.getTenantCodeCache` :151-168：AtomicReference + synchronized(ref) DCL 正确，且有 TestDynCodeGenTenantCacheInit 双线程闩锁测试钉死（C10 复用）。
- `SubscriptionState.suspended` volatile :651：多线程轮询读写可见性正确。
- 全 8 根扫描 `ThreadLocal/NamedThreadLocal/new Thread/orTimeout/BlockingQueue`：零命中（与 R1 基线一致）——无 ThreadLocal 泄漏面。
- static 非 final 字段全量扫描仅 `NopSysApplication/NopDynApplication.globalArgs`（Quarkus 启动 HB 保障，平台样板，watch-only，不立项）。

## 维度复核结论（第 4 阶段独立复核）

- 复核方式：全部 9 条逐条回码验证（P1 两条对 ：307-332/:608-619 重新取证；其余以初审同 session 精读记录 + 定位复核核对行号与语句）。
- 三态判定：已保留 8 / 已降级 1（C1-004 → watch-only，降级理由：后果为纳秒级读窗、无状态损坏，随 C1-005 快照对象化建议一并消化）/ 已驳回 0。无条目存在行号或事实错误。
- 判级复核：C1-001/C1-002 维持 P1（交错序列完整、后果为并发结构破坏/消息静默丢弃）；C1-003/C1-008 维持 P2（可具体化、后果有界）；其余 P3 维持。
- 现状标注：本单元在 campaign 内无先前 WI（cra-index 无 nop-sys 条目），9 条全部标注**新发现**；其中 C1-001/C1-002 两站点带有前一轮（campaign 前局部审计）修复痕迹（:307-309 注释、:615-617 注释），本条发现为其修复的**残余并发面**（修复本身正确，未闭合全部交错）；C1-008 站点注释自认"显式去重"为已知问题的应用层弥补，竞态缺口为新发现。

## 最终保留项

| 编号 | 标题 | 严重度 | 现状 | 复核状态 |
|------|------|--------|------|---------|
| C1-001 | 已发布 mergedStore 并发读-写数据竞争（TreeMap） | P1 | 新发现（前轮修复残余面） | 已保留 |
| C1-002 | DurableSubscription.cancel×subscribe 孤儿化→消息丢弃 | P1 | 新发现（前轮修复残余面；家族关联 WI 4.1 MA-05/C1-006——彼为 LocalMessageService 站点） | 已保留 |
| C1-003 | ensure*Processor 检查-然后-行动懒初始化 | P2 | 新发现（同仓正确范本 DynCodeGen DCL 已修+测试） | 已保留 |
| C1-005 | DynOrmModelProvider 懒初始化非 volatile | P3 | 新发现 | 已保留 |
| C1-006 | InMemoryCodeCache.scope 跨代残留 | P3 | 新发现 | 已保留 |
| C1-007 | clearCache/removeCache 不清 defaultCache | P3 | 新发现（有 TTL 兜底） | 已保留 |
| C1-008 | generateByAI moduleName 去重 TOCTOU | P2 | 新发现（注释自认已知问题，弥补手段有竞态） | 已保留 |
| C1-009 | setVar get-then-save TOCTOU | P3 | 新发现 | 已保留 |
| C1-004 | refreshCache 双 volatile 瞬态错配 | P3 | 新发现 | 已降级（watch-only） |
