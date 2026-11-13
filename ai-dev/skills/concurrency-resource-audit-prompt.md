# 并发·异步·资源审计提示词（通用，逐模块滚动执行）

> **定位**：面向 Nop 平台**所有模块**的专项深度审计提示词，覆盖六个失效轴——并发正确性、异步正确性、内存有界性（大数据量不 OOM）、资源生命周期（分配必释放）、长时运行稳定性、并发高性能。本文件是 `deep-audit-prompts.md` 维度 14（异步与事务模式）的本领域深化：当目标模块包含长驻、异步、大吞吐或大状态逻辑时，用本提示词按维度派发子代理执行。
>
> **前提**：执行前必须完整阅读 `AGENTS.md`、`docs-for-ai/INDEX.md`、目标模块的 owner 文档（`docs-for-ai/03-modules/` 或 `02-core-guides/` 对应文件）与 `ai-dev/design/` 中相关设计文档。
>
> **使用方式**：将「共享提示词前缀」中的 `{MODULE}` 替换为目标模块路径（如 `nop-stream`、`nop-orm`），与任一维度正文拼接后派发子代理。深挖迭代、独立复核、合并裁定的调度流程**沿用 `deep-audit-prompts.md`「调度架构」「发现条目完整性守则」两节，本文件不重复**；两文件冲突时以本文件的领域口径为准。
>
> **归档**：审计记录写入 `ai-dev/audits/{YYYY}-{MM}/{YYYY-MM-DD}-{HHMM}-deep-audit-{MODULE}-concurrency/`，每个维度一个文件（`{C编号}-{英文短名}.md`），汇总写 `summary.md`（格式同 `deep-audit-prompts.md` 附录 B）。

## 适用判定

目标模块/子模块命中以下任一特征即应执行本审计（可全维度执行，也可按特征裁剪）：

| 模块特征 | 必选维度 |
|---|---|
| 自建线程/线程池、定时调度（job、scheduler、retry） | C1 C2 C4 C5 C7 |
| 异步链、回调、CompletableFuture、RPC 异步（graphql、rpc、message） | C1 C3 C4 C7 |
| 长驻服务/引擎（stream、workflow、task、cep、runtime） | C1–C5 C7 C8 |
| 无界输入处理（文件解析、消息消费、批处理、上传、导入导出） | C5 C6 C8 |
| 缓存/对象池/注册表/全局注册（cache、pool、registry、model cache） | C1 C2 C6 C8 |
| 持久化状态/事务/checkpoint/恢复语义（orm、dao、stream checkpoint、undo） | C1 C3 C6 C7 C8 |
| 热路径（每请求/每记录/每字段执行的代码） | C2 C9 C10 |
| 纯模型/配置/文档类模块 | C1 + C6 抽查即可 |

## 高频缺陷族画像（本仓库审计史实证，所有维度必对照）

以下缺陷族在本仓库多轮审计中实证出现，审计时必须作为定向检查项，不是泛泛的"最佳实践"清单：

| # | 缺陷族 | 实证出处 | 定向检查形态 |
|---|---|---|---|
| F1 | **异步快照持有活引用 → 快照撕裂** | nop-stream r6 ST-01/02（异步快照期间源数据仍被写，快照内部不一致） | 一切"先拷贝再异步处理"的快照/备份/导出路径：拷贝是深是浅？拷贝后源是否继续可变？ |
| F2 | **失败路径"半个成功"** | nop-stream r6 AR-01（sink 部分 ACK 后崩溃，恢复期 N-1→N 静默丢数据） | 多步写出/多目标提交：部分成功后失败，重试/恢复语义是否定义？是否有中间态标记？ |
| F3 | **修实例不修类别（同族兄弟漏修）** | invariant-loop 口径；nop-stream r6 R5 轮 6/16 条 P1 来自修复批次自身 | 审计发现的每个并发/资源缺陷，必须 grep 全部同类兄弟方法/入口并逐一登记，不适用豁免 |
| F4 | **无界账本只增不删** | nop-stream AR-06（JDBC 2PC ledger 只增不删）；nop-code OOM 同族 | 任何 append-only 持久化（ledger、审计、事件、日志表）：是否有清理/TTL/归档路径？ |
| F5 | **挂死生产者对健康检测不可见** | nop-stream AR-05 | 心跳/stall 检测是否覆盖"不抛异常也不产出"的静默挂死？超时是否作用于所有等待点？ |
| F6 | **落盘无持久化屏障** | nop-stream AR-07（LocalFileCheckpointStorage 无 fsync） | 声称 durable 的数据：flush ≠ fsync；rename 原子性；断电/kill -9 语义是否有测试？ |
| F7 | **OOM 同族复发** | nop-code（AR-168→AR-177 交叉引用）；nop-stream CEP×RocksDB | 无界集合/缓存/队列/物化；一次载入全量 vs 流式；分页缺失；序列化中间体积 |
| F8 | **资源清理不对称** | invariant-loop 复发族（"资源清理不对称"） | register/open/acquire/subscribe 与 unregister/close/release/unsubscribe 是否成对？异常与取消路径是否也释放？ |
| F9 | **静默吞异常 → 状态不一致** | invariant-loop 复发族 | catch 后仅记日志/仅返回默认值：吞掉后状态机是否还一致？失败是否对外可见？ |
| F10 | **并发守卫缺失** | nop-stream TwoPhaseCommitSinkFunction 并发族；WindowAggregationOperator 被击穿 7+ 次 | 声明"单线程/不可重入"契约的方法是否有运行时守卫（fail-fast），还是依赖调用方自觉？ |
| F11 | **序列化断链（内存可用、持久化读回断）** | nop-stream CEP×RocksDB 三层缺陷（Lockable 不可 JSON 序列化 / 类型信息丢失 / 白名单拒绝） | 一切"写入走 A 路径、读回走 B 路径"的状态：读回类型与写入类型是否一致？往返是否有测试？ |
| F12 | **complete 洗白（错误以正常完成收口）** | CRA 8.68 MA-03（plan 2562：Servlet async onTimeout 用 `complete()` 收口丢失错误语义；F9 的变体——异常未被吞，而是被替换为成功） | 一切错误/超时/取消收口点：下游（调用方/客户端/future 消费者）能否区分失败与成功？修复模式：错误走显式失败通道（completeExceptionally/异常回调），`complete()` 只负责已写出字节的收口。判级：失败对外表现为静默截断/空成功即 P1 |
| F13 | **墙钟回拨（相对计时误用 System.currentTimeMillis）** | CRA 8.72 七站点批（plan 2566：LocalResourceLockManager 租约、TokenBucket 回填、ThresholdBreaker 熔断窗、WfTaskScanner 预算等） | 逐个 currentTimeMillis 消费点区分两个域：绝对时刻域（日志/mtime/展示，保留墙钟）vs 相对时长域（elapsed/租约/预算/限流窗）。相对域必须用单调时钟（CoreMetrics.nanoTime()），否则 NTP 回拨/前跳导致误判。判级：回拨可致租约误判/预算失真=P1；纯诊断展示保留墙钟=P3 并注记豁免理由 |
| F14 | **替身契约缺失（测试 stub 与真契约系统性偏离）** | CRA 8.69 MA-04（plan 2563：MockScheduledExecutor 时间换算/生命周期/取消/复排四面偏离 IScheduledExecutor 契约，绿测试保护力为零） | 每个自建 test double 对照真接口逐方法核对契约：不一致要么补齐实现，要么 javadoc 边界声明（如"仅手动驱动单轮，不承担周期复排契约"）并删死字段。修复模式：以真实现为对照腿建立语义基准测试。判级：替身偏离遮蔽真缺陷或反向钉死错误行为=P1；无消费方的死替身=P2 |
| F15 | **批内累积语义缺口（批处理丢同批前序状态）** | CRA 8.71 R2-2（plan 2565：code-index 批首冻结 globalTable 快照，同批前序文件符号不进后续判定视图，违反语义保持承诺） | 批处理读侧视图是"每条目重读"还是"批首冻结快照"？与声明的语义（最终一致 vs 批内一致）是否匹配？修复模式：快照 + 随每条目增量扩入（plan 2565 裁定 K 形态）。与 F1 的区分：F1 是异步期间源被写导致快照内部撕裂，F15 是快照本身与批内增量语义的缺口。判级：批内一致性违约且无补偿=P1 |

---

## 共享提示词前缀

派发任何维度子代理前，将本节全文连同维度正文一起下发，`{MODULE}` 已由主 agent 替换。

```text
你正在审计 nop-entropy 项目中的 {MODULE} 模块，审计领域为「并发·异步·资源·内存·长时运行」。这是一个基于 Nop 平台的 Java 多模块 Maven 工程（Java 25）。

执行前先阅读：
1. AGENTS.md（代码规范、验证命令、Protected Areas——禁改任何生成文件）
2. docs-for-ai/INDEX.md 及 {MODULE} 对应的 owner 文档
3. ai-dev/design/ 中与 {MODULE} 相关的设计文档（如有）
4. 本维度正文列出的必读文档

Nop 平台并发语境（审计时必须代入，不是通用 Java 项目）：
- NopIoC bean 默认单例且无并发守卫约定：singleton bean 的实例字段即跨请求共享可变状态。
  @Inject 只支持 protected/package-private 字段注入（private 注入不生效）——看到 private
  可变字段未被注入不代表无并发问题，仍需分析其归属生命周期。
- 编译产物缓存跨线程共享：XPL 模板、XDSL 模型、ORM 元数据等解析产物有全局缓存，
  "检查-然后-放入"缓存路径是高频竞态点（double-checked locking、computeIfAbsent 误用）。
- 请求上下文 IContext（io.nop.api.core.context.IContext / ContextProvider）依赖
  ThreadLocal 传播：跨线程/异步边界若不显式传递，取到的可能是 null 或别的请求的上下文。
- 平台自带并发基础设施，发现手写重复造轮子时先判断是否有平台设施未用：
  * io.nop.commons.concurrent.executor：IThreadPoolExecutor、StandardThreadPoolExecutor、
    SyncThreadPoolExecutor、ThreadPoolConfig（含拒绝策略/队列配置）
  * io.nop.commons.concurrent：IBlockingQueue/IBlockingSource/IBlockingConsumer、
    QueueOverflowPolicy（队列满策略）、AsyncJoinType、CycleSynchronizer、IAtomicLong
  * io.nop.commons.concurrent.lock：IResourceLockManager、LocalFileLock
  * io.nop.commons.concurrent.ratelimit：IRateLimiter
  * io.nop.commons.concurrent.semaphore：ISemaphore、HighWatermarkSemaphore（水位信号量）
  * io.nop.commons.concurrent.thread：NamedThreadFactory、NamedThreadLocal、ThreadHelper
  * io.nop.commons.cache：ICache/IAsyncCache、CacheConfig（容量/淘汰配置）、GlobalCacheRegistry
  * io.nop.commons.pool：IPool、PoolConfig、PoolAcquireOptions、IPoolMetrics
- 错误处理两档策略（ErrorCode / 模块异常类），error-handling 维度不在本审计范围，但
  "吞异常导致并发状态不一致"在范围内（F9）。

通用审计纪律：
1. 以当前 live code 为准。初审阶段禁止阅读 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/ 历史记录
   （盲审）；现状标注（新发现/已知未修复/已修复）只在复核阶段做，流程见 deep-audit-prompts.md。
2. `_` 前缀生成文件不作为发现对象，除非追溯到生成模板层面的并发问题。
3. 每个发现必须可定位：文件路径 + 行号范围 + 3-10 行证据片段，按统一发现格式完整输出
   （见本提示词附录 A），禁止压缩为关键词或表格行。
4. 并发类发现的证据标准高于其他领域：**必须写出具体触发时序**（T1 执行到 X 行、T2 执行到
   Y 行、交叠后果）。写不出具体交错序列的，一律降级 P2 并标注"理论风险，未具体化"。
   禁止看到共享字段就直接报"线程不安全"。
5. 规模口径：涉及"大数据量"的判断必须对照 owner 文档声明的规模包络（如某引擎自限
   GB 级状态），超出声明包络的 OOM 风险要标注"超出声明包络"，不与包络内的缺陷混排。
6. 区分"缺陷"与"已声明的边界"：owner doc 明文声明的限制（如某组合不可用 + fail-fast 守卫）
   不是缺陷；守卫缺失、文档未声明、守卫可绕过才是。

严重程度判级（本领域校准）：
- P0：主路径数据竞争且后果是数据损坏/语义违约；声明规模包络内必然 OOM/资源耗尽；
  主路径资源泄漏（正常成功路径即泄漏）；可达死锁；持久化数据在声明的故障模型下损坏。
- P1：错误/取消/重试路径的资源泄漏；罕见但可具体化的竞态（有明确触发链）；长时运行
  必然触发的无界增长；异步链异常被静默吞掉导致状态不一致；负载上升必然触发的死锁/
  队列溢出；"半个成功"无恢复语义；快照撕裂。
- P2：锁粒度过粗/可证明的争用热点；有界但边界配置缺陷；ThreadLocal 在池化线程未清理
  （有明确泄漏链）；并发测试缺失导致 P1 级缺陷无保护力；写不出具体时序的理论竞态；
  热路径可量化的分配/序列化浪费。
- P3：性能优化候选；防御性代码冗余；命名/结构问题。

本领域误报校准（以下不是问题，不应报告）：
- 无状态单例 bean（仅有注入依赖 + final/只读字段）被并发调用——这是设计意图。
- synchronized/FutureTask/ConcurrentHashMap 本身的使用——原语使用不是问题，复合操作
  误用才是。
- 低频路径（启动、配置、管理操作）使用粗粒度 synchronized——合理，不为优雅性报问题。
- 有界声明存在的缓存/队列/池（CacheConfig/PoolConfig/QueueOverflowPolicy 配置了上限）
  ——除非上限配置与使用方规模声明矛盾。
- 平台基础设施自身（nop-kernel）的并发实现在审计业务模块时不重审，只审使用方式。
- 为压测/基准写的测试专用代码中的简化并发模型，不按生产代码标准报 P1。
```

---

## 审核维度

### C1：共享可变状态与可见性

**目标**：盘点模块全部跨线程共享的可变状态，验证每一处的可见性与原子性；这是其他维度的基础盘点。

**执行步骤**：

1. 盘点可变共享状态的五类载体，逐类列出清单：
   - singleton bean 的实例字段（`@Inject` 注入的可变协作对象 + 非 final 可变字段）
   - static 可变字段（含 static 集合、static SimpleDateFormat/Random 等非线程安全工具）
   - 全局注册表/缓存（含 `ContextualizedRegistry`、`GlobalCacheRegistry` 的使用点）
   - ThreadLocal（重点：池化线程场景下是否 `remove`；`NamedThreadLocal` 是否按规范清理）
   - 通过集合/数组/可变对象参数逃逸到其他线程的隐式共享（构造器/getter 泄漏 this 或内部集合）
2. 对清单中每一项回答：谁写、谁读、通过什么同步机制建立 happens-before？
   无同步机制的，写出具体触发时序（T1/T2 交错），写不出则降 P2 理论风险。
3. 检查发布安全：构造后跨线程共享的对象是否安全发布（final/volatile/锁内发布/并发容器 put），
   有无不安全发布（构造器启动线程、构造器注册监听器）。
4. 检查"检查-然后-行动"复合操作：`if (!map.containsKey(k)) map.put(k, v)`、
   `if (started) return; started = true` 形态的守卫是否原子（对照 F10）。
5. 检查 long/double 非原子读写、`++` 复合递增、BigDecimal/Date 等可变对象的共享引用。
6. 对照 F3：每个缺陷登记后 grep 全类，列出同族兄弟字段/方法的完整清单及各自状态。

**输出**：共享可变状态清单表（字段 × 写者 × 读者 × 同步机制 × 判定）+ 按标准格式的发现条目。

---

### C2：锁与同步原语

**目标**：验证锁的使用正确（互斥成立、无死锁）且代价合理（无 unjustified 争用热点）。

**执行步骤**：

1. 盘点全部锁载体：synchronized 方法/块、ReentrantLock/ReadWriteLock、synchronized 集合包装
   （`Collections.synchronizedXxx`）、`computeIfAbsent`/`compute` 内含重逻辑的用法。
2. 锁正确性：
   - synchronized 集合的**迭代/遍历/批量操作**是否在锁内（本仓库门禁先例：
     "每个 synchronizedMap 字段的迭代点必须在 synchronized 块内"）——逐字段核对其全部迭代点。
   - 可重入锁在回调/未知代码（listener、用户函数）持有时是否会造成锁扩展或死锁。
   - 锁对象是否会被替换（锁-final 检查）；private 锁对象 vs 锁 this 的一致性。
   - 双重检查锁定是否配 volatile；`computeIfAbsent` 内部是否可能递归 map 自身或持锁做重 I/O。
3. 死锁排查：画全模块锁序图（锁 A 内取锁 B 的全部边），找环；重点：回调进入点持锁调用外部代码、
   两个组件互持对方锁的交叉路径。
4. 锁性能（对照 C9 口径）：锁内含 I/O、RPC、反序列化、大集合遍历的，逐个登记热路径锁内重操作；
   读多写少场景是否该用 ReadWriteLock/并发容器而用了独占锁（P2/P3 候选）。
5. 对照 F5：所有 `wait/await/poll(timeout)/lockInterruptibly` 等待点是否有超时；无限期等待是否
   都有中断响应与挂死可检测性。

**输出**：锁序图（文本）+ 发现条目。

---

### C3：异步链路正确性

**目标**：验证异步组合的异常传播、取消/超时语义、上下文传播与时序假设。

**执行步骤**：

1. 盘点全部异步入口：CompletableFuture 链、回调接口、自建线程提交、定时任务、消息监听器、
   ReactiveStreams（如有）。
2. 异常传播：每个 `thenApply/thenCompose/whenComplete/handle` 链检查——
   - 无 `exceptionally/handle` 的链，异常去哪了？返回的 future 被谁消费？是否可能静默丢弃？
   - `runAsync/supplyAsync` 未指定 executor 的落在哪个池（ForkJoinPool.commonPool），
     与模块声明的执行模型是否一致？
   - 回调中抛出的异常是否会杀死调度线程或被吞（对照 F9）。
3. 取消与超时：future 取消后，正在执行的底层任务是否真正停止（中断响应点检查）？
   超时后资源（连接/缓冲/租约）是否释放？`orTimeout/completeOnTimeout` 使用处超时后原任务
   是否仍在跑（幽灵任务）？
4. 上下文传播：异步边界是否传递 IContext/ThreadLocal 依赖（ContextProvider 取值点跨线程后
   是否为 null 或串号）；平台设施（如 `NamedThreadLocal`、装饰 Runnable 的工具类）是否被绕过。
5. 时序假设：依赖"先 A 后 B"顺序但无同步保证的路径（如假设监听器注册先于事件触发、
   假设初始化先于首个请求）；double-subscribe / 重复回调幂等性。
6. 对照 F1/F2：异步快照/异步写出的路径核对活引用与部分成功语义。

**输出**：异步入口清单 + 发现条目。

---

### C4：线程池与执行器治理

**目标**：验证每个线程池/执行器的配置有界、关闭正确、任务隔离合理。

**执行步骤**：

1. 盘点全部线程创建点：自建线程池（平台 `IThreadPoolExecutor`/JDK）、`new Thread`、
   commonPool 间接使用（并行流、`CompletableFuture` 默认池）、ForkJoinPool（`NopForkJoinWorkerThread`）。
2. 每个池核对：核心/最大参数是否有依据；**队列是否有界**（无界队列 = OOM 候选 + 背压失效）；
   拒绝策略是什么、被拒绝的任务去哪了（静默丢弃还是 fail-fast）；线程命名（`NamedThreadFactory`）
   是否可观测。
3. 关闭路径：模块 stop/destroy 时池是否 shutdown + awaitTermination； shutdownNow 后在途任务
   的资源是否回收；是否存在"池随 bean 泄漏"（关闭钩子未注册）。
4. 任务隔离：不同业务是否共享同一池（一个慢业务饿死其他业务）；调用方线程执行策略
   （caller-runs）是否会造成上游线程被阻塞的连锁反应；池内任务再提交同池任务的死锁
   （池耗尽互等）。
5. 定时任务：period 与执行时长的关系（任务比周期慢时的堆积策略）；`scheduleAtFixedRate` 任务
   抛异常后是否停止调度（JDK 语义）且该停止是否被感知（对照 F9）。
6. 对照 F8：池/线程生命周期与持有者生命周期是否对称。

**输出**：线程池清单表（池 × 参数 × 队列 × 拒绝策略 × 关闭路径 × 判定）+ 发现条目。

---

### C5：内存有界性与大数据量

**目标**：验证在模块声明的数据规模包络内，任何执行路径的内存占用有上界；大数据量不 OOM。

**执行步骤**：

1. 盘点全部"随输入增长"的容器：集合、缓存（`ICache` 无 CacheConfig 上限的使用点）、队列、
   批缓冲、Session/请求级 Map、去重集合。每项判定：上界是什么？由谁保证？
2. 无界 API 使用点：无 LIMIT 的查询结果全量载入（`findAll`/无分页迭代）、全量文件读入内存
   （readAllBytes/整文件 DOM）、消息消费无 prefetch 上限、导出全量物化。
3. 物化 vs 流式：声明处理大数据量的路径（导入导出、批处理、流聚合）逐段核对是流式处理还是
   全量物化；中间产物（装箱、反射元数据、JSON 中间树）的放大倍数。
4. 缓存淘汰：有上限的缓存是否有淘汰策略（LRU/LFU/TTL）还是仅"满了拒绝"；缓存键空间是否
   可枚举（用户可控键的缓存 = 每键驻留 → 绕过上限）。
5. 峰值叠加：单请求有界 × 并发数 × key 基数 = 全局峰值，验算是否超出堆预算；off-heap
   （如有 RocksDB/直接内存）的分配上限与释放路径。
6. 对照 F7/F11：本仓库 OOM 同族与序列化中间体积膨胀的教训逐点核对。

**输出**：无界风险清单（路径 × 增长源 × 声明上界 × 判定）+ 发现条目。

---

### C6：资源生命周期与泄漏

**目标**：验证一切分配的资源在所有路径（成功/失败/取消/中断/超时）上被正确释放。

**执行步骤**：

1. 盘点资源类型与获取点：连接（JDBC/JMS/Kafka）、流（文件/网络）、`IPool` 借出对象、
   文件锁（`LocalFileLock`）、信号量许可、Buffer（直接内存/池化 buffer）、临时文件、
   注册的 listener/handler。
2. 对每个获取点核对配对释放：try-with-resources / finally / close 钩子；**异常路径**（获取后
   抛错）、**取消路径**（future cancel / thread interrupt 后）、**超时路径**、**提前 return/continue**
   路径逐一核对（对照 F8 清理不对称）。
3. 释放幂等与二次释放：close 重复调用是否安全；release 顺序（先借后还的逆序要求）。
4. 借还契约：池借出对象异常时归还不（destroy vs return 分支）；borrow 超时后成功返回的
   迟到对象归属；`PoolAcquireOptions` 超时路径的对象去向。
5. 注册表对称性：add/removeListener、register/unregister、put/evict 的全部调用点配对核查，
   列出只有 add 没有 remove 的注册点清单。
6. 进程退出与长驻切换：临时文件清理钩子；优雅停机时的在途资源 draining。

**输出**：资源获取点配对表 + 发现条目。

---

### C7：长时运行稳定性

**目标**：验证模块在连续运行（天/周级）下不劣化：无单调增长、有挂死检测、失败可自愈。

**执行步骤**：

1. 单调增长盘点：只增不删的持久化（账本/审计/事件表——对照 F4）、内存中的历史累积
   （统计 Map、去重集合、指标标签基数——metrics label 基数爆炸单独核对）、日志放大
   （循环内 error 日志无抑制）。
2. 挂死可检测性（对照 F5）：所有可能永久阻塞的点（无超时 I/O、无限期等待、死锁）在健康
   检测/心跳/stall 监控中是否可见；"不产出也不报错"路径是否有看门狗。
3. 重试风暴：重试策略是否有指数退避 + 上限；重试是否放大下游故障；定时任务失败重试与
   新调度叠加。
4. 自愈语义：组件失败后重启/恢复路径的状态清理（旧状态、旧租约、旧临时文件——对照 F2/F10）；
   fencing：旧实例复活后是否被隔离。
5. 时钟与期限：依赖系统时钟单调性的逻辑（超时计算）在时钟回拨下的行为；长期运行的
   epoch/generation 溢出。
6. 热路径外的慢性病：每次调用都增长的小对象（如 listener 累加、缓存项只加不减）。

**输出**：单调增长源清单 + 挂死点清单 + 发现条目。

---

### C8：背压、流控与过载行为

**目标**：验证上游快于下游时系统有界降级，而非无界积压或静默丢弃。

**执行步骤**：

1. 盘点生产-消费边界：源→处理→汇、请求入口→worker、消息消费→处理。每条边界核对：
   有界队列（容量多少）？满了以后行为（阻塞/拒绝/覆盖/降级）与声明语义是否一致
   （平台 `QueueOverflowPolicy` 是否该用未用）？
2. 信号量/限流使用：`ISemaphore`/`HighWatermarkSemaphore`/`IRateLimiter` 的许可泄漏
   （acquire 后异常路径 release 缺失）；限流阈值与下游容量是否匹配。
3. 批处理边界：批次大小上限；单批超大输入（一条巨大消息/一个超大文件）是否绕过批上限。
4. 过载可观测：积压深度、拒绝数是否有指标；过载时行为是否有文档声明且与实现一致。
5. 对照 F2：过载拒绝路径的"半个成功"（已处理一半的批被拒绝后的语义）。

**输出**：生产-消费边界清单 + 发现条目。

---

### C9：并发高性能（热路径）

**目标**：识别并发热路径上可量化的性能缺陷：锁争用、分配放大、串行化瓶颈。本维度发现默认 P2/P3，除非掩盖正确性问题。

**执行步骤**：

1. 定位热路径：每请求/每记录/每字段执行的代码（对照 owner doc 的规模声明）。冷路径不做本维度。
2. 锁争用：热路径上的全局锁/单例方法级 synchronized；读写比与锁选择的匹配。
3. 分配放大：热路径上的重复分配（循环内 new 反射对象/编解码器/正则编译、`JsonTool`
   每次调用重新反射、日志参数的急切求值、装箱）；可复用/可缓存对象未复用。
4. 串行化瓶颈：本应并行的逻辑被单线程点串行化（全局单例处理器、共享无状态却被
   synchronized 包裹的服务方法）。
5. 批量化机会：逐条 I/O（逐条 DB 写/逐条 RPC）无批量路径——仅当 owner doc 声明规模下
   可量化收益时报告。
6. 证据要求：每条性能发现给出量化口径（调用频率 × 单次代价的估算链），无量化依据的
   不报告；基准缺口按 P3 登记（对照 nop-benchmark 惯例，注明建议的 JMH 基准形态）。

**输出**：热路径清单 + 量化发现条目 + 基准缺口登记。

---

### C10：并发正确性的测试保护力

**目标**：验证本模块对并发/资源/规模缺陷的测试保护力——不是有没有测试，而是回归会不会红。

**执行步骤**：

1. 盘点既有并发相关测试：并发/竞争/资源/大规模测试清单及其保护范围。
2. 变异推演（同 deep-audit 维度 00 口径）：对每个已登记的 P0/P1 并发缺陷（本轮新发现 +
   已知未修复），回答"补上正确实现后，现有哪个测试会红？"——无测试会红的，登记
   "缺保护力"（P2，与缺陷本体分开计数）。
3. 时序敏感测试审查：`Thread.sleep` 同步的测试（平台性脆弱）；未等待异步结果就断言的测试
   （偶发绿）；测试自己 catch 掉竞争（superficially-pass）。
4. 资源泄漏测试：是否有泄漏检测钩子（关闭后断言池空/句柄数/注册表空）；没有的，为
   P1 级资源缺陷标注建议的测试形态。
5. 大规模测试：声明规模包络的边界是否有测试（如有界队列满、缓存上限淘汰、maxParallelism
   边界）——以小规模参数化模拟为主，不要求真实大规模压测。
6. 输出保护力矩阵：缺陷 × 测试 × 变异结论（会红/假保护/无测试）。

**输出**：保护力矩阵 + 发现条目（本维度发现的"缺保护"按 P2 立项）。

---

## 附录 A：发现条目统一格式

本领域在 `deep-audit-prompts.md` 附录 A 的基础上增加三个领域字段（并发场景、触发时序、失效面枚举），其余字段与全局格式完全一致：

```markdown
### [C{N}-{序号}] 简短标题

- **文件**: `相对路径/文件名.java:行号范围`
- **证据片段**:
  ```java
  // 3-10 行代码片段
  ```
- **严重程度**: P0/P1/P2/P3
- **并发场景**: 涉及哪些线程/执行器/调用路径（如"HTTP worker 线程与后台 checkpoint 线程"）
- **触发时序**: 具体交错序列。T1 执行到 X 行（状态为 S1）→ T2 执行到 Y 行 → 交叠后果 Z。
  写不出具体时序的必须标注"理论风险，未具体化"并降级 P2。
- **现状**: 一句话描述当前问题是什么
- **风险**: 不修复会怎样（对照声明规模包络与故障模型）
- **建议**: 修复方向（优先复用平台基础设施，指明具体类）
- **失效面枚举**: 同族兄弟方法/入口的完整清单及逐一状态（F3 类别清扫要求，禁止留空）
- **信心水平**: 确定 / 很可能 / 有趣的猜测
- **误报排除**: 为什么这不是"本领域误报校准"中的同类误报
- **复核状态**: 未复核 / 已保留 / 已降级 / 已驳回
```

发现条目最少 10 行（上述字段缺一不可），少于 10 行视为不合格。`失效面枚举`为本领域硬性字段：报告任何并发/资源缺陷而不 grep 同族兄弟的，视为条目不合格。

## 附录 B：逐模块执行清单（主 agent 用）

1. 按「适用判定」表为 `{MODULE}` 选定维度集合，默认全选，纯模型/配置模块裁剪为 C1+C6。
2. 准备阶段：读取 owner doc 与设计文档，生成基线（附录 C 的 grep 命令输出 + 模块行数统计）。
3. 派发：所有维度第 1 轮初审可并行（共享前缀 + 维度正文）；C10 放最后批次（需要前序
   发现的缺陷清单）。深挖追加轮次串行，流程同 `deep-audit-prompts.md`「调度架构」。
4. 复核与合并裁定：同 `deep-audit-prompts.md` 阶段二/跨报告合并裁定（同族相邻按
   「根因 + 失效层」合并，防止同一缺陷被多维度重复计数）。
5. 归档：`ai-dev/audits/{YYYY}-{MM}/{YYYY-MM-DD}-{HHMM}-deep-audit-{MODULE}-concurrency/`。
6. 修复立项：按 `ai-dev/audits/README.md`「与 plans 的关系」，P0/P1 汇总为修复 roadmap/plan，
   引用审计目录作为 baseline。

多模块滚动节奏建议：每轮选 1–2 个模块，优先级 = 长驻引擎（nop-stream、nop-job、nop-workflow、
nop-task）> 热路径基础设施（nop-graphql、nop-orm、nop-dao）> 集成模块（nop-quarkus、nop-spring）
> 业务模块（nop-auth、nop-biz 应用层）。

## 附录 C：快速扫描 grep 基线命令（准备阶段由主 agent 执行）

```bash
# C1: 静态可变状态候选（static 非 final 字段 + static 线程不安全工具）
grep -rn --include="*.java" -E "static [A-Za-z]" {MODULE} | grep -v "static final" | grep -v "@"
grep -rn --include="*.java" -E "static final (Map|List|Set|HashMap|ArrayList|HashSet|Date|SimpleDateFormat|Calendar|Random)" {MODULE}
grep -rn --include="*.java" -E "new ThreadLocal|NamedThreadLocal" {MODULE}

# C2: 锁载体
grep -rn --include="*.java" -E "synchronized|ReentrantLock|ReadWriteLock|Collections.synchronized" {MODULE}
grep -rn --include="*.java" -E "computeIfAbsent|compute\(" {MODULE}

# C4: 线程创建与池
grep -rn --include="*.java" -E "new Thread\(|ExecutorService|Executors\.|newFixedThreadPool|newCachedThreadPool|IThreadPool" {MODULE}
grep -rn --include="*.java" -E "scheduleAtFixedRate|scheduleWithFixedDelay|Timer\(" {MODULE}

# C3: 异步链
grep -rn --include="*.java" -E "CompletableFuture|thenApply|thenCompose|supplyAsync|runAsync|orTimeout" {MODULE}

# C5: 无界候选
grep -rn --include="*.java" -E "new (ArrayList|HashMap|HashSet|LinkedList|ArrayDeque|ConcurrentHashMap)\(" {MODULE} | wc -l
grep -rn --include="*.java" -E "readAllBytes|readString\(|readLines" {MODULE}

# C6: 资源获取点
grep -rn --include="*.java" -E "\.getConnection|new FileInputStream|Files\.newInputStream|\.acquire\(|\.borrow" {MODULE}
grep -rn --include="*.java" -E "extends.*InputStream|implements.*Closeable|AutoCloseable" {MODULE}
```

基线仅用于圈定重点文件，不做计数结论；数量结论必须按 `deep-audit-prompts.md` 共享前缀第 10 条的口径纪律复现。
