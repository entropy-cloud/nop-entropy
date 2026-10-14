# C3 异步链路正确性 — nop-stream-orchestration-thin 第 3 轮深挖（盲审·收口轮）

- 审计单元：`nop-stream-orchestration-thin`（`nop-stream-flow` + `nop-stream-sql` + `nop-stream-fraud-example`，src/main/java，排除 `_` 前缀生成文件）
- 维度：C3 异步链路正确性 第 3 轮（第 1 轮：C3-001~007，同目录 `C3-async-chains.md`；第 2 轮：C3-008~009，同目录 `C3-round2.md`）
- 审计日期：2026-10-14；分支：concurrency-audit-remediation；盲审（未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件，唯一例外为本维度第 1、2 轮两份文件）
- 本轮定位：**收口轮**。按 campaign 继续条件（前轮新增含 P1 必派下一轮），对 C3-008/C3-009 做失效面同族扩展、第 2 轮 15 项排除核查中「条件安全」项的条件可达性抽验、前两轮未点名小文件兜底清查。

---

## 1. 本轮发现

**无。本轮定向核查全部收敛为排除项（见第 2 节），未发现新的高价值问题。**

---

## 2. 排除项/定向核查

### 2.1 C3-008 失效面扩展：initializeState / clear / REPLACE 同族清扫（F3）

1. **单元内 `initializeState` 实现全量盘点**：`grep -rn "initializeState|snapshotState|restoreState"` 三模块 src/main/java（排除 `_gen/`）仅命中 `DirectoryFileSourceFunction` L248（snapshotState）/ L261（initializeState）——即 C3-008 本体，**单元内不存在第二个 StatefulFunction/CheckpointedSourceFunction 实现**。`ReplayableCdcSourceFunction` L38-39 的 javadoc 提及 snapshotState/initializeState，但其实现载体是父类 `DebeziumCdcSourceFunction`（connector-debezium，单元外）——该兄弟面（`NopStreamOffsetBackingStore.setOffsets` 的 REPLACE clear+putAll）已由 C3-008 失效面枚举显式移交，不重复立案。
2. **REPLACE 语义调用点清扫**（`.clear()` / `putAll(`，排除注释）：全单元仅 3 处命中——`DirectoryFileSourceFunction` L282（C3-008 本体）、`DemoKeyedStateStore` L71/L74/L104-106（`update`/`clear`/`putAll`，类 javadoc L26 显式声明 DEMO-ONLY 的 disconnected-state 简化，且实例化点仅 `FraudDetectionDemo` L115/L144 的 demo main，单线程）——**同族清扫闭合，无遗漏的 REPLACE 面向共享运行态**。
3. **DirectoryFileSourceFunction 全文重读（C3-008 之外残余面狩猎）**：
   - `snapshotState`（L248-257）：快照拷贝在 `synchronized(cursors)` 内且每次新建 `TreeMap` 快照——逃逸到 `OperatorSnapshotResult` 的是独立副本，后续 `cursors` 推进不影响已产出快照；无 unmodifiable 包装但单元内无消费者写路径（checkpoint 机制在 nop-stream-runtime，只读消费），无新交错。
   - `readObject`（L98-104）：transient `runEntered` CAS 守卫跨 JVM 反序列化后重置——与 deepCopy 重建路径一致，无陈旧守卫面。
   - `finally { runEntered.set(false) }`（L128）的二次 run() 窗口：同实例二次 `run()` 需要 region 重启复用同一实例；第 2 轮已核对 `SupervisionLoop.rebuildTask` → `OperatorChain.deepCopy`（重建副本）且初始部署路径 buildTasks 全新构建——**同实例二run在本单元可达面内不可达**，不立案。
   - `putAllCursors`（L287-298）的 `Long.parseLong` NumberFormatException 风险：调用点在 `initializeState` 的 `synchronized(cursors)` 内，异常直接上抛 restore 链路（fail-fast，任务 FAILED），非异步吞异常面；且 fixture 状态自产自销，非缺陷。
   - `discoverFiles()` 重枚举/文件消失（L132-143）：fixture 生命周期面，非并发正确性维度。

### 2.2 C3-009 失效面扩展：getRuntimeContext / getKeyedStateStore 同族清扫（F3）

4. **`getRuntimeContext|getKeyedStateStore|getState(|getListState|getMapState` 全量盘点**：单元内消费点仅 `UserHistoryEnricher` L51/L62（C3-009 本体）与 `UserTransactionHistory` 构造器 L40-41（其唯一生产构造点 = UserHistoryEnricher L62，同一失效面）；`DemoKeyedStateStore` 的 getState/getListState/getMapState 实现为 DEMO-ONLY（排除 2 已计）。**单元内不存在第 1 轮清单之外、经 runtime-context 访问键控状态的第二个 UDF**。
5. **含状态 RichFunction/KeyedProcessFunction UDF 逐类核对**（C3-009 共享契约同族）：
   - `FraudAlertPatternFunction`（extends `PatternProcessFunction`）全文重读：唯一字段 `final String fraudType`，processMatch 全部派生值来自 match 参数（L40-72），空 match 抛 IllegalStateException 直接传播（无 catch）——**无状态，即使被共享实例也无交错面**（第 2 轮排除项 14 维持）。
   - `TransactionWindowAggregate` / `AlertCountAggregate`（AggregateFunction）：累加器经 `createAccumulator()`/参数流转，函数实例字段 final（第 2 轮全文核对；本轮经 `SqlRowCompositeFunction` 同构验证维持）。
   - `UserTransactionHistory`：持 `KeyedStateStore` 引用做 read-modify-write——其并发面完全由 C3-009 主导（第 2 轮"附带"段已计懒初始化与共享 backend，不单独立案）。
6. **SqlRowCompositeFunction 重读**（C3-009 邻域 + 第 2 轮排除项 6 条件抽验）：字段全 final（spec/keyEvals/subFns，L35-37），`add`/`merge`/`getResult` 只变异**累加器入参**（L60-110），共享缓存实例（`SqlRowAggregateOps.CACHE`）无实例可变态——第 2 轮排除项 6 的「字段全 final」条件在 live code 复核成立。

### 2.3 第 2 轮「条件安全」项的条件可达性抽验

7. **排除项 3（DirectoryFileSourceFunction 可中断）**：条件 = 任务线程执行 run() 且 sleep 可被 interrupt。live code 复核：`emitRemaining` 签名 `throws InterruptedException`（L151）、`Thread.sleep(lineDelayMs)`（L212）——条件成立，维持不立案。
8. **排除项 6（StreamSqlAggregation 累加器 per-key 单线程）**：条件 = 累加器状态不驻留共享实例。经第 6 项 `SqlRowCompositeFunction` 复核成立；其键控驻留面在平台 keyed/window 机制（nop-stream-core，单元外）。维持。
9. **排除项 8（RecordColumnAccess Map 分支两步读）**：条件 = 记录对象子任务内单线程流动。无本单元内违反该条件的代码路径（记录 map 并发改写属应用违约面）。维持。
10. **排除项 11（InMemoryBeanFunctionResolver register-then-start happens-before）**：全仓使用面 grep 核对 = nop-stream-runtime 各 E2E 测试的「new → register → build」链（构建期注册，作业启动前完成），**无任何运行期注册调用点**——条件在本单元及全部现存使用面成立。维持（裸 `LinkedHashMap` 不构成运行期竞态）。
11. **排除项 12/13（初始部署 restore→run 次序、region 重启 Phase 1/2 次序）**：载体在 nop-stream-runtime，第 2 轮已钉到 live code 行号；本轮单元内无新增交错点（见 2.1 第 3 项）。维持。

### 2.4 前两轮未点名小文件兜底清查

12. **model/ 非 _gen 包（36 文件）**：逐一按非样板行计数，最大 5 行有效代码——全部为生成模型类的一行子类声明，无字段、无逻辑、无静态状态。安全。
13. **validate/ 包（StreamConfValidator / StreamModelLinter / StreamConfValidationReport / ValidationIssue）**：静态字段全为 `static final` 常量（层级号、掩码串、选项键表），方法静态纯函数，构建期校验生命周期。安全。
14. **spi/ 包（IAggregatorFunctionResolver / IScalarFunctionResolver / ISqlStreamCompiler）**：纯接口；单元内实现者 = StreamAggregatorFunctionResolver（本轮精读：无状态 resolve、typed StreamException、构建期调用）、StreamScalarFunctionResolver（C3-002 已计）、StreamSqlCompilerProvider（本轮精读：无状态委托 StreamSqlCompiler 静态方法）。安全。
15. **SqlScalarXplPrinter**：实例字段 `final ColumnResolver/Final AggregateRef` + `requiredImports`（per-instance LinkedHashSet，每次编译新建实例）——codegen 打印器构建期使用，无共享可变态。安全。
16. **SqlCastHelper / SqlAccShapeUtil / CompileErrors**：无状态静态工具（private 构造器 + 静态方法/异常工厂）。安全。
17. **BeanFunctionResolver**：纯接口（builder/GlobalBean/InMemoryBean/BeanContainer 四实现已被第 2 轮排除项 11 覆盖）。安全。
18. **rate-limit 声明面**：`StreamModelDslBuilder` L70 仅 import `io.nop.stream.core.source.RateLimitedSourceFunction`——令牌桶机制在 nop-stream-core（单元外），单元内无 timer/调度器残留。第 1 轮 E10「无定时任务/CompletableFuture」结论经本轮全单元 re-grep 复核成立（`CompletableFuture|ScheduledExecutor|Executors.|new Timer|ExecutorService` 零命中）。
19. **静态可变状态全单元收口**：非 final 静态字段全单元唯一 = `StreamScalarFunctionResolver.defaultResolver`（volatile，C3-002 已计）；`static final` 集合 5 处 = `StreamSqlAggregations.BUILTINS`、`StreamSqlCompiler.MANAGED_TYPES`、`SqlRowAggregateOps.CACHE`（C3-006）、`RecordColumnAccess.GETTER_CACHE`（C3-005）、`StreamSqlExprCompiler.MANAGED_TYPE_BY_NAME`——全部已被前轮覆盖，无新增。
20. **并发关键字分布收口**：`volatile|Atomic|ConcurrentHashMap|synchronized` 命中文件共 6 个（DirectoryFileSourceFunction ×8、SqlRowAggregateOps ×3、RecordColumnAccess ×3、ReplayableCdcSourceFunction ×2、XplSourceFunction ×2、StreamScalarFunctionResolver ×1）——逐一对照，全部落在前两轮已审发现/排除项内，无未审计的并发原语使用文件。

---

## 3. 终止声明

C3 维度第 3 轮（收口轮）深挖完成。第 1 轮异步入口全量清单（E1-E10）、第 2 轮生命周期/恢复回调面 + 取消/interrupt + IContext 传播 + catch 同族清扫之上，本轮补齐：initializeState/REPLACE 同族收口（单元内唯一实现 = C3-008 本体）、getRuntimeContext 同族收口（单元内唯一消费 = C3-009 本体）、第 2 轮全部「条件安全」项的条件可达性 live-code 抽验（4/4 成立）、前两轮未点名小文件兜底清查（model/validate/spi/provider/printer/util 共 45+ 文件，全部干净）、静态可变状态与并发原语全单元收口（6 文件全部落于已审面）。

**未发现新的高价值问题。C3 维度深挖终止。**

### 累计发现清单（C3 维度，3 轮合计 9 项）

| ID | 严重程度 | 一句话 | 位置（单元内） |
|---|---|---|---|
| C3-001 | P1 | CDC 回放引擎失败被吞、cancel 伪装为正常 EOS（半个成功，绕开父类 pendingError 机制） | ReplayableCdcSourceFunction L165-190 |
| C3-002 | P2 | StreamScalarFunctionResolver LinkedHashMap register/call 无同步（运行期注册竞态 + namesList CME 掩盖原始错误） | StreamScalarFunctionResolver L46-51/88-97 |
| C3-003 | P2 | side-input「冻结快照」无防御性冻结，活引用直通运行期任务线程 | StreamModelDslBuilder L1520-1550、XplFunctionSupport L35-52 |
| C3-004 | P2（理论） | ReplaySubscription.onCancelTasks 裸 ArrayList 违反 ICancellable 跨线程契约 | ReplayableCdcSourceFunction L133-163 |
| C3-005 | P2（理论） | GETTER_CACHE computeIfAbsent 锁内反射内省的类加载锁倒置隐患 | RecordColumnAccess L36-37/82-111 |
| C3-006 | P3 | SqlRowAggregateOps.CACHE 进程级静态缓存无上界无淘汰 | SqlRowAggregateOps L27-38 |
| C3-007 | P3 | ReplayEngine 取消无 interrupt 响应点（有界幽灵任务 + offset 越过未确认记录） | ReplayableCdcSourceFunction L125-131/147-152 |
| C3-008 | P1 | initializeState REPLACE（cursors.clear）与 region 重启 Phase-3 提交-继续编排交错，游标清零重发 | DirectoryFileSourceFunction L261-285 |
| C3-009 | P1 | 共享 UDF 实例 × 各子任务 open() 无同步覆写 RuntimeContext → 并行 ≥2 键控状态跨子任务污染（根因载体在 core，移交） | UserHistoryEnricher L47-55/61-62 |

**统计**：P1 × 3、P2 × 4（含理论标注 2 项）、P3 × 2。缺陷族命中：F1（C3-003）、F2（C3-001、C3-008）、F9（C3-001、C3-009）；F3（修实例不修类别）经第 2、3 轮同族清扫，各发现失效面均已枚举、无留空账。

### 终止依据

1. **同族扩展双向闭合**：两个 P1 发现（C3-008/C3-009）的失效面类别在本单元内各自有且仅有一个实现/消费点（grep 全量证明），同族清扫无第三实例。
2. **静态面收敛**：全单元静态可变状态、static final 集合、并发原语使用文件清单与已审发现/排除项一一对应，无未审面。
3. **异步原语零残留**：CompletableFuture/ExecutorService/Timer/Scheduled 全单元零命中（本轮 re-grep），第 1 轮 E10 结论维持。
4. **小文件兜底清零**：前两轮未逐一点名的 model/validate/spi/工具类 45+ 文件全部核查为无状态或构建期单线程，无候选发现。
5. **残余风险均在单元外且已移交**：编排侧 restore-vs-run 根因（→ nop-stream-runtime）、ProcessOperator 共享契约根因（→ nop-stream-core）、CDC offset REPLACE 兄弟面（→ nop-stream-connector-debezium / nop-message）。

继续轮次的边际收益：单元内 C3 触面已全量枚举且逐面落到 live code；再轮仅可能复现已排除项，**依据 campaign 继续条件建议终止本维度**。

---

## 4. 覆盖声明

**本轮精读文件（单元内，全文）**：
- `DirectoryFileSourceFunction.java`（全文，299 行——C3-008 之外残余面狩猎）
- `UserHistoryEnricher.java`（全文——C3-009 之外残余面狩猎）
- `FraudAlertPatternFunction.java`（全文——C3-009 同族无状态验证）
- `SqlRowCompositeFunction.java`（全文——第 2 轮排除项 6 条件复核）
- `StreamAggregatorFunctionResolver.java`（全文）、`XplSourceFunction.java`（全文——已声明边界复核）

**本轮定向 grep 清扫（全单元，排除 `_gen/`）**：
- `initializeState|snapshotState|restoreState`（1 文件命中 = C3-008 本体）
- `getRuntimeContext|getKeyedStateStore|getState(|getListState|getMapState`（C3-009 本体 + DEMO-ONLY）
- `new UserTransactionHistory`（唯一生产构造点 = UserHistoryEnricher）
- `.clear()|putAll(`（C3-008 本体 + DemoKeyedStateStore DEMO-ONLY）
- 静态非 final 字段 / `static final` 集合 / `volatile|Atomic|ConcurrentHashMap|synchronized`（6 文件全落于已审面）
- `CompletableFuture|ScheduledExecutor|Executors.|new Timer|ExecutorService`（零命中）
- `DemoKeyedStateStore|InMemoryBeanFunctionResolver` 全仓使用面（DEMO-ONLY / register-then-start 条件成立）
- `implements .*Function|extends AbstractRichFunction|public void open(`（UDF 接口面全量盘点）
- `RateLimit`（机制在 nop-stream-core，单元内仅声明透传）
- model/ 非 _gen 36 文件非样板行计数（最大 5 行）

**未修改任何前轮文件**；对第 1 轮排除项的事实前提无新的纠正（第 2 轮对排除项 6 的纠正仍有效，本轮复核维持）。

**盲审声明**：本轮仅读取本维度第 1、2 轮两份指定文件作为必要输入；未读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下任何其他文件，未读取其他维度审计文件。
