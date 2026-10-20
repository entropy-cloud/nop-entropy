# C2 锁与同步原语 — nop-cluster（WI 4.2）

- **审计人**: 独立盲审初审员（C1+C2 合并批）
- **日期**: 2026-10-21
- **状态**: 初审完成，未复核

## 审计范围与口径

- **范围**: 与 C1 同一 7 源码根 / 74f / 5,577 行（逐文件）。锁载体盘点以基线 C2a（synchronized 站点 8 处：ServiceInstance:289、AbstractLeaderElector:165/:173、NacosNamingService:67/:82/:230/:238、ServiceInfo:21/:25/:29——共 10 站点）与 C2b（computeIfAbsent 4 处）为底册，人工逐站点复核，未发现基线外的锁载体（无 ReentrantLock/ReadWriteLock/Collections.synchronizedXxx/wait-notify 站点）。
- **owner doc 零覆盖声明**: 同 C1 文件——`module-groups.md` 对本单元零契约覆盖，契约以 javadoc 为准。
- **适配面薄预校准声明**: sentinel/hazelcast-core/hazelcast-cache/admin 自管锁原语为 0（基线 C2a 四根全空，逐文件确认）。**委派面单列注记（不立案为自管原语缺陷）**：`HazelcastCache.computeIfAbsent`（nop-hazelcast-cache/HazelcastCache.java:135-137，注释声明路由到 IMap 服务端原子实现，覆写 ICache 锁化 default——属已声明边界）；`CachingNamingService:37`（委派 LocalCache→Caffeine `cache.get(key, fn)`，fn 内远程 I/O 的争用形态与本案 C2-002 同构，因仓库内零装配随 C1-008 降档，不单独立案）。
- **与 C1 已立案条目的边界声明**: (1) `ServiceInstance.cleanupTasks` 锁不对称的**资源丢失后果面**立案于 C1-002，本文件 C2-001 立案其**锁正确性根因面**（迭代点在锁外），两文互引不重复计数；(2) `AbstractLeaderElector` doStop/onStop 无锁的**状态复活后果与时序**立案于 C1-004，本文件 C2-003 立案其**锁纪律缺陷面**（含 `newElection` 无锁站点与 pending check 不可取消），互引不重复计数；(3) C1-001（共享元素原地改写）根因不在锁维度（写侧锁存在但作用域错位），不在本文件重复。
- **降档口径**: 同 C1——全模块为请求触达 + 长驻运行期面，启动期降档仅适用于纯装配点。

### 锁序图（文本）

```
节点（全部锁载体）:
  BIN   = ConcurrentHashMap instanceCache 的 bin 锁（NacosNamingService:175 computeIfAbsent）
  NTHIS = NacosNamingService.this（:67/:82 双检 + :238 listener.updateInstances）
  LSN   = ServiceInfoListener 实例监视器（:230/:238）
  SINFO = ServiceInfo 实例监视器（:21/:25/:29）
  INST  = ServiceInstance 实例监视器（:289 addCleanupTask）
  ETHIS = AbstractLeaderElector.this（:165/:173）
  CAFF  = LocalCache 内部 Caffeine 条目锁（CachingNamingService:37，委派面）

边（持 A 锁期间获取 B 锁）:
  BIN  → NTHIS   （getServiceInfo 在 computeIfAbsent 映射函数内调 getNamingService:179）
  LSN  → SINFO   （updateInstances 持 listener 锁调 serviceInfo.setServiceInstances:264）
  无其他嵌套：INST、ETHIS、CAFF 均为孤立单锁站点；
  updateInstances 的清理循环（getCleanupTasks/task.run:269）在 LSN 释放之后执行（正确的锁外设计，
  但 INST 读侧无锁——见 C2-001）。

环检测: 无环 → 本模块无可达死锁。
争用热点: BIN 锁内含两次远程调用（subscribe + getAllInstances，见 C2-002）——唯一的热路径锁内重 I/O；
  NTHIS 在 createNamingService（SDK 客户端构建，见 C2-004）期间被持有，属低频重初始化。
等待点超时核查（F5 对照）: 模块内无 wait/await/lockInterruptibly/poll(timeout) 站点；
  唯一跨线程等待面是 electionPromise（CompletableFuture，等待方语义由 ILeaderElector javadoc 声明，
  取消即完成——C1-004 已覆盖其乱序面），无新增 F5 立案。
```

## 发现

### [C2-001] ServiceInstance 锁不对称：写侧 synchronized 管辖的 ArrayList 迭代点完全无锁

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/discovery/ServiceInstance.java:289-300`
- **证据片段**:
  ```java
  public synchronized void addCleanupTask(Runnable task) {  // 写：INST 锁
      if (cleanupTasks == null)
          cleanupTasks = new ArrayList<>();
      cleanupTasks.add(task);
  }

  @JsonIgnore
  public List<Runnable> getCleanupTasks() {                 // 读+迭代拷贝：无锁
      if (cleanupTasks == null)
          return Collections.emptyList();
      return new ArrayList<>(cleanupTasks);                 // 遍历/批量操作点
  }
  ```
- **严重程度**: P1
- **并发场景**: 业务线程（addCleanupTask 写）vs Nacos notifier 线程（实例移除事件 → NacosNamingService.java:269 `for (Runnable task : serviceInstance.getCleanupTasks())`——该唯一迭代消费点位于 LSN 锁外执行）。
- **触发时序**: T1 业务线程在 addCleanupTask 内执行 `cleanupTasks.add`（ArrayList 扩容：新建数组、逐元素迁移）→ T2 notifier 线程进入 getCleanupTasks 执行 `new ArrayList<>(cleanupTasks)` → 交叠后果：(a) 拷贝数组尾部含 null 元素 → 清理循环 `task.run()` NPE（被 :272-274 catch 降级为 warn，但该批后续任务全部丢失）；(b) 迁移中途拷贝漏掉已存在任务 → 静默资源泄漏；(c) `cleanupTasks` 非 volatile，T2 可见 null → 整批跳过。
- **现状**: 对照本仓库门禁先例"每个 synchronized 字段的迭代点必须在 synchronized 块内"：`cleanupTasks` 的全部迭代点（唯一一个）都在锁外。锁存在本身证明该类预期被并发使用，不对称属缺陷而非设计。
- **风险**: 见 C1-002（后果面）：实例下线窗口的清理任务丢失 = 连接/句柄泄漏，长时运行累积。
- **建议**: getCleanupTasks 加 synchronized；或字段改 CopyOnWriteArrayList 并删除方法级锁（写少读多，COW 最契合）。
- **失效面枚举**: `cleanupTasks` 全部触点：写 API addCleanupTask（:289）、读迭代点 getCleanupTasks（:296）+ 唯一消费点 NacosNamingService:269。模块内无其他"写锁/读裸"同构字段（grep 全类核对：`attrs` 为 CHM 自洽，`_host` 为良性惰性缓存）。
- **信心水平**: 确定（不对称结构为代码事实）
- **误报排除**: 非"原语使用不是问题"豁免——问题恰是复合操作（迭代 vs 写）跨越锁边界；非低频路径粗锁豁免——该锁保护的是运行期可达的实例下线路径
- **复核状态**: 未复核
- 复核状态: 降级(P2，携回升条款)｜休眠公共 API 面——addCleanupTask 全仓零生产调用方（与 C1-002 同根同判同 diff 修复）；任一业务模块接线即回升 P1。锁不对称结构回码成立

### [C2-002] computeIfAbsent 映射函数内执行两次远程调用：发现热路径的 bin 锁内重 I/O

- **文件**: `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosNamingService.java:174-199`
- **证据片段**:
  ```java
  public List<ServiceInstance> getInstances(String serviceName) {
      return instanceCache.computeIfAbsent(serviceName, this::getServiceInfo).getServiceInstances();
  }
  private ServiceInfo getServiceInfo(String serviceName) {
      NamingService namingService = getNamingService();          // BIN → NTHIS
      ...
      namingService.subscribe(serviceName, groupName, listener);  // 远程调用①（BIN 锁内）
      List<Instance> instances = getNamingService().getAllInstances(serviceName); // 远程调用②（BIN 锁内）
  ```
- **严重程度**: P2
- **并发场景**: 多个请求线程首次发现不同服务（CHM 不同 key 落入同 bin 时互斥）或同一服务首装；持锁时长 = Nacos SDK 往返延迟（超时/降级时秒级，参照同模块 NacosConfigService 默认 timeout=5000ms 量级）。
- **触发时序**: T1 请求线程对服务 A 首次 getInstances → 进入 computeIfAbsent 映射函数，持有 A 所在 bin 锁执行 subscribe+getAllInstances（网络往返中）→ T2 请求线程对服务 B（与 A 同 bin）首次 getInstances → 阻塞在 BIN 锁直至 T1 两次远程调用完成 → 交叠后果：无过错的服务 B 的发现被服务 A 的 Nacos 延迟牵连；Nacos 抖动窗口内多服务首装串行化，上游 `FutureHelper.futureCall` 占用的 worker 线程堆积，向调用线程池传导延迟。
- **现状**: CHM 原子性使用正确（无重复装载、无丢失写），缺陷是锁内重操作：热路径（每次 RPC 发现都经过此方法）的慢速装载被 bin 锁放大为跨服务串行点。缓存命中路径为无锁读，热点集中于首装与 TTL/重启窗口。
- **风险**: Nacos 降级（超时 5s 量级）时，首装服务数的 bin 碰撞概率决定阻塞广度；与 C1-008 的异步惊群叠加时窗口内并发度进一步放大。
- **建议**: 映射函数内只做缓存条目占位（返回未装载的 ServiceInfo 标记），subscribe/getAllInstances 移到 bin 锁外执行后回填；或首装走异步装载原语（`LocalCache.computeIfAbsentAsync` 同型方案）。
- **失效面枚举**（F3：模块内 computeIfAbsent/compute 全站点）: ① NacosNamingService:175（本案，映射函数含远程 I/O）；② CachingNamingService:37（委派 LocalCache→Caffeine get(key,fn)，fn 内远程 I/O，同构——按预校准注记不单独立案，随 C1-008 零装配降档）；③ ResourceSpec:156（compute 内纯内存合并，安全）；④ HazelcastCache:135-137（委派 IMap 服务端原子实现，已声明边界，注记不立案）。
- **信心水平**: 确定（结构事实）；实际停顿幅度取决于 Nacos 延迟与 bin 碰撞，标注为可证明的争用热点而非必然故障
- **误报排除**: 非"computeIfAbsent 本身使用是问题"的误报形态——原子性正确，立案点是 C2 口径明列的"锁内含 I/O、RPC"重操作；不与 C9 性能维度混排（此处是锁持有面缺陷，非纯分配浪费）
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现未修复（live 首装/TTL 窗口面；CHM bin 争用模型与 NacosConfigService timeout=5000 佐证均回码证实）

### [C2-003] AbstractLeaderElector 锁纪律不一致：状态机 5 个写站点仅 2 个有锁，pending 调度不可撤销

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/elector/AbstractLeaderElector.java:119-124`、`161-181`、`213-225`；`AbstractPollingLeaderElector.java:16-29`
- **证据片段**:
  ```java
  protected void newElection() {                       // 写 electionPromise：无锁
      this.electionPromise = new CompletableFuture<>();
  }
  protected synchronized void onRestartElection() { ... }      // 有锁
  protected synchronized void onElectionCompleted(LeaderEpoch epoch) { ... }  // 有锁
  protected void doStop() { electionPromise.cancel(false); onStop(); listeners.clear(); }  // 无锁
  ```
- **严重程度**: P2
- **并发场景**: 同 C1-004——globalTimer 线程（checkElection → onRestartElection/onElectionCompleted）vs 生命周期线程（doStop/onStop）vs 潜在调用方（restartElection 的子类实现）。
- **触发时序**: 结构性缺陷（非单一交错）：同一对 volatile 字段（leaderEpoch/electionPromise）的 5 个写站点中 `newElection`/`doStop`/`onStop`（:221-224）在锁外执行，使 synchronized 站点的 check-then-act（onElectionCompleted:176 `promise.isDone()` 判断）对无锁写者不成立——T1 timer 线程持 ETHIS 读 isDone()==false → T2 无锁执行 doStop 的 `promise.cancel` / `newElection` 替换字段 → T1 基于过期判定完成旧 promise。后果时序（停止后复活、乱序回调）立案于 C1-004，本条立案锁面：**互斥域没有覆盖全部写者，synchronized 形同半防**。
- **现状**: 另一锁面：`AbstractPollingLeaderElector` 的 schedule 为 fire-and-forget（:22/:28 不保留 Future），stop 无任何在途 checkElection 的撤销手段；`scheduleCheck:27` 的守护 `!executor.isDestroyed()` 检查的是全局 timer 的销毁态（globalTimer 进程生命周期内恒 false），守护空转——轮询回路的停止完全依赖子类自觉检查 `isStopping()`（SysDaoLeaderElector 有、基类契约未强制，模块内唯一测试子类无此检查）。
- **风险**: 后续子类实现者按基类签名自然假设"状态转移已由基类守护"，在 doStop/newElection 侧复现 C1-004 同族竞态（F10 并发守卫缺失族：契约靠自觉无 fail-fast）。
- **建议**: 全部 5 个写站点统一纳入 synchronized(this)（或私有锁对象）；AbstractPollingLeaderElector 保留 scheduled Future 并在 doStop 中 cancel；scheduleCheck 守护改为 `!isStopping() && !executor.isDestroyed()`。
- **失效面枚举**（F3：leaderEpoch/electionPromise 写站点全清单）: `newElection`（:161，无锁）、`onRestartElection`（:165，有锁）、`onElectionCompleted`（:173，有锁）、`onStop`（:221-224，无锁）、`doStop`（:121，无锁）——5/5 已逐一登记；`scheduledExecutor` 写点 2（setter/doStart:19，装配期，HB 经 schedule 建立，安全）。
- **信心水平**: 确定（锁覆盖面为静态事实）
- **误报排除**: 不按"低频路径粗锁无害"豁免——缺陷是锁缺失而非过粗；`newElection` 当前无生产调用方仅降低暴露面，不改变锁纪律缺陷本身的成立（该 protected 方法是子类可触达契约）
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现未修复（5 写站点锁覆盖为静态事实，全清单回码逐一证实；scheduleCheck 守护空转与 CL-005 面证实；全仓 grep newElection 零调用方证实）

### [C2-004] NacosNamingService 单监视器承载 SDK 客户端重初始化：低频粗锁 + 首装路径持双锁

- **文件**: `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosNamingService.java:65-93`、`:174-199`
- **证据片段**:
  ```java
  public NamingService getNamingService() {
      if (namingService == null) {
          synchronized (this) {
              if (namingService == null) {
                  try {
                      namingService = NacosFactory.createNamingService(properties); // SDK 客户端构建
  ```
- **严重程度**: P3
- **并发场景**: 首次访问发现/注册路径的多线程（@PostConstruct start() 预热后通常已被消解；start 失败或未走生命周期装配时落到首请求线程）。
- **触发时序**: T1 首请求线程进入 synchronized(this) 执行 createNamingService（SDK 客户端构建含本地资源初始化与连接建立，毫秒~秒级）→ T2 其余线程的 registerInstance/unregisterInstance/getInstances 等全部经由 getNamingService 的调用阻塞在 NTHIS → 交叠后果：首装延迟放大为全部命名服务操作的串行等待。叠加边：该 NTHIS 获取发生在 BIN 锁内（BIN → NTHIS，见 C2-002），首装路径形成双锁串联。
- **现状**: 双检锁写法本身正确（volatile + synchronized，不缺 volatile）；锁序图无环（见上文锁序图），无死锁面。立案点仅为：重初始化置于公共监视器 + 所有命名操作共享单锁的粒度选择。
- **风险**: 仅首装/重建窗口（低频）；destroy 复活重建时窗口再现（C1-005 修复后该窗口收敛）。无正确性后果。
- **建议**: 保持现状可接受（低频路径粗锁属豁免口径，登记为 P3 备查）；若做 C1-005 修复，顺带将 created 标志与 destroyed 标志分离，避免复活路径重入此锁。
- **失效面枚举**: NTHIS 全部获取点：getNamingService（:67）、getNamingMaintainService（:82）——同构双检、同判级；:238 的 synchronized(this)（listener.updateInstances 内，实际监视器为 listener 实例而非 NacosNamingService.this——基线 C2a 标注 ":238 synchronized(this)" 系内类 this，已人工复核更正为 LSN 锁，不影响结论）。
- **信心水平**: 确定
- **误报排除**: 本条自我适用"低频路径使用粗粒度 synchronized——合理，不报"的校准，仅以 P3 登记备查不要求修复；双检缺 volatile 的误报形态已主动排除
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜备查登记（自我豁免口径适用；锁序图无环复核确认）

## 维度集外线索（overlay C5/C6/C7/C9）

（编号续接 C1 文件末号 CL-004 之后）

### [nop-cluster-CL-005]（C6）选主轮询回路无停止句柄：stopped 后在途 check 仍会执行
- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/elector/AbstractPollingLeaderElector.java:16-29`
- 线索：`schedule` fire-and-forget 不保留 Future，doStop 无法撤销已排队的 checkElection；`scheduleCheck` 的 `executor.isDestroyed()` 守护作用于全局 timer（进程级，恒 false），空转。生产子类以 isStopping() 自救（SysDaoLeaderElector:66/:78），基类契约未强制——归属资源/生命周期清理对称性（F8）审计面。

### [nop-cluster-CL-006]（C6）NacosConfigService 关闭句柄与 stop() 竞态：removeListener 落在已 shutdown 客户端
- **文件**: `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosConfigService.java:128-139`、`162-166`
- 线索：`getConfigInfo` 返回的 ConfigInfo close lambda 读非 volatile `configService` 判空后调用 removeListener——与 stop()（shutdown + 置 null）无互斥：判空通过但实例已 shutdown → close() 抛 Nacos SDK 异常且调用方未捕获该路径；publishConfig 在 stop 后裸用 configService 直接 NPE。归属资源关闭对称性/错误路径审计面（C1-005 失效面 3 的展开）。

## 维度复核结论

- **复核人**: CRA WI 4.2 独立复核员（合并批 C1+C2+C3）
- **复核日期**: 2026-10-20
- **方法**: 同 C1 文件（逐条回码 + 台账查重 + 降档口径核对）

| 发现 | 初判 | 复核判定 | 关键复核证据 |
|------|------|---------|-------------|
| C2-001 | P1 | **降级 P2（条件化回升）** | 锁不对称（addCleanupTask:289 synchronized vs getCleanupTasks:296 裸读）回码成立；但唯一写 API 全仓零生产调用方→与 C1-002 同根同判条件化降档，接线即回升 P1；与 C1-002 同 diff 修复 |
| C2-002 | P2 | **保留 P2** | computeIfAbsent:175 映射函数内 subscribe+getAllInstances 两次远程调用回码成立；live 面（每次服务首装/TTL/重启窗口）；bin 锁放大与 C1-008 惊群叠加论证成立 |
| C2-003 | P2 | **保留 P2** | 5 个写站点（newElection:161/doStop:121/onStop:221-224 无锁，onRestartElection:165/onElectionCompleted:173 有锁）逐一回码证实；scheduleCheck:27 守护空转证实 |
| C2-004 | P3 | **保留 P3** | 双检写法正确（volatile+synchronized）、锁序无环复核确认；自我豁免登记口径恰当 |
| C2-005（R2） | P2 | **保留 P2** | promise.complete(:180) 在 synchronized 临界区内回码成立；CF 非 async 依赖在完成线程同步执行为 JDK 确定语义；10 个 synchronized 站点"锁内外部代码"枚举复核成立 |

**降档口径核对**: C2-001 是本文件唯一降档，依据为零调用方休眠面（同 C1-002）；C2-002/003 为 live 面维持原级；C2-005 虽 whenElectionCompleted 生产调用方为零（全仓 grep 仅测试调用，JobCoordinator:584 javadoc 显式禁用），但按本单元 C3-004 判例（公开 API 契约缺陷 + 机制确定 → 暴露面注记不降级）维持 P2 并保留现状标注——与 C1-002 降档的分界：后者 P1 判级在零调用方下不满足 P1 门槛须降档，C2-005 的 P2 判级本身已按潜在面校准，无重复降档必要。

## 最终保留项

| 最终严重度 | 条目 |
|-----------|------|
| P2×4 | C2-002（bin 锁内远程 I/O）、C2-003（写站点锁覆盖）、C2-005（complete 持锁派发，R2）、C2-001（cleanupTasks 锁不对称，条件化 P2，与 C1-002 同 diff） |
| P3×1 | C2-004（粗锁备查） |

统计：保留原级 4 / 降级 1 / 驳回 0。CL 线索（CL-005/CL-006）复核确认成立：CL-005 与 C2-003 失效面及 WI 2.1 C3-001 30 点表 #14 同站点关联（见台账查重节）；CL-006 与 C1-005 失效面③ 同站点，随同案修复。
