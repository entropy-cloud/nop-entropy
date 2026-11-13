# C1 共享可变状态与可见性 — 第 2 轮深挖（盲审）

- 审计单元：`nop-stream-orchestration-thin`（nop-stream-flow / nop-stream-sql / nop-stream-fraud-example 三模块 src/main/java，非 `_` 前缀文件）
- 维度：C1 共享可变状态与可见性（第 2 轮定向深挖）
- 仓库根：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- 日期：2026-10-14；审计代理：C1 第 2 轮盲审子代理
- 输入：仅第 1 轮文件 `C1-shared-mutable-state.md`（C1-001~007 + 22 项清单 + 5 个移交残留点）与 grep 基线 `_tmp/cra-baseline-nop-stream-orchestration-thin.txt`；未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何其他文件

## 1. 本轮发现

**未发现新的高价值问题。C1 维度深挖终止。**

本轮按追加指令的五个方向（移交残留点兄弟站点、C1-001 失效面扩展、构造器 this 逃逸/发布安全、long/double 与 `++` 专项、构建期/运行期归属边界）逐一执行定向核查，全部收敛为"干净"或"归并到既有发现/已声明边界"，未产生可具体化触发时序的新载体。新发现条目数：0（序号 C1-008 起无占用，留待复核轮）。

## 2. 排除项/定向核查

### 2.1 C1-001 失效面扩展：`StreamScalarFunctionResolver` 完整读者矩阵（复核，归并不另立案）

对 `functions`（LinkedHashMap，nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/StreamScalarFunctionResolver.java:46）的全部访问点重新逐一落实：

| 访问点 | 位置 | 线程面 | 立案归属 |
|---|---|---|---|
| `call()` → `functions.get` | L88-97（读点 L93） | 逐记录、subtask 线程（SqlScalarXplPrinter.java:186-198 发射） | **C1-001（P1）**，无变化 |
| `contains()` → `containsKey` | L53-56 | 仅编译期：StreamSqlExprCompiler.compileFunction L226 | C1-001 失效面（同表并发纪律缺失） |
| `resolve()` → `get` | L63-66 | 仅编译期：StreamSqlExprCompiler.compileFunction L239 | C1-001 失效面 |
| `registeredNames()` → `new ArrayList<>(functions.keySet())` | L58-61 | 仅编译期 fail-fast 文案：StreamSqlExprCompiler.namesOf L253-257 | C1-001 触发时序已含（拷贝构造器内部迭代活 keySet，并发 put 可 CME）；返回值本身是安全快照 |
| `namesList()` → `new ArrayList<>(functions.keySet())` | L68-71 | 全单元 **零调用方**（grep 双确认，含 fraud 模块）——纯公开 API 面，仅供单元外 fail-fast 文案 | C1-001 失效面（同型拷贝期 CME）；无独立触发路径，不另立案 |

补充确认：`call()` L89 对 volatile `defaultResolver` 做单次快照读（无 TOCTOU 二次读）；`registerAsDefault()`（L78-81）为 IoC init 期调用，`setDefault(this)` 是**构造完成后**经 volatile 写发布 `this`——安全发布，非构造器 this 逃逸。`registerAsDefault()` 与应用首次 `register()` 之间的窗口只会导致 `call()` L90-92 / compileFunction L226-231 的**响亮 fail-fast**（IllegalStateException / unknown-function），不产生静默错误值——排除。

### 2.2 兄弟 resolver 完整状态矩阵（第 1 轮标注 clean 者，本轮复核）

| Resolver | 文件 | 实例字段 | 判定 |
|---|---|---|---|
| `StreamAggregatorFunctionResolver` | nop-stream-sql/.../eval/StreamAggregatorFunctionResolver.java:38-139 | 仅 `serialVersionUID`；resolve/validateArgType 全静态委托（StreamSqlAggregations/StreamSqlExprCompiler/EqlExprSupport 均无状态入口） | **复核 clean** |
| `BeanContainerFunctionResolver` | nop-stream-flow/.../builder/BeanContainerFunctionResolver.java:26-61 | `final IBeanContainer container`（构造后只读委托） | 复核 clean |
| `GlobalBeanFunctionResolver` | nop-stream-flow/.../builder/GlobalBeanFunctionResolver.java:18-38 | 零字段；`INSTANCE` static final 无状态单例（委托 BeanContainer） | 复核 clean（无状态单例并发调用属误报校准） |
| `BeanFunctionResolver`（接口） | nop-stream-flow/.../builder/BeanFunctionResolver.java | — | 接口，N/A |
| `InMemoryBeanFunctionResolver` | nop-stream-flow/.../builder/InMemoryBeanFunctionResolver.java:27-37 | `final Map beans` + `getBeans()` 活视图 | **C1-007（P3）**，无变化 |

结论：C1-001 所在的"可变注册表 + 静态默认实例"模式在单元内**无第二个实例**（基线 static 非终态清单复核：flow=0、sql=仅 defaultResolver、fraud=0，与第 1 轮清单一致）。

### 2.3 构造器 this 逃逸 / 监听器注册面（第 1 轮未专项盘点，本轮全查）

全单元 grep（`this[),]`、`registerListener/addListener/addObserver/subscribe(`、`new Thread/.start()`、C4 基线）逐一落点：

1. `StreamModelDslBuilder.java:1973` `AdvancedTransforms.build(this, ...)`——builder 传入**静态方法、同栈构建期消费**，无存储、无逃逸。clean。
2. `StreamScalarFunctionResolver.java:80` `setDefault(this)`——IoC init 期（构造后）volatile 发布。clean（见 2.1）。
3. `ReplayableCdcSourceFunction.java:126-131` `ReplayEngine.subscribe()` 内 `new Thread(...).start()`——线程在 run 路径（非构造器）启动，Thread.start() 提供 happens-before；lambda 捕获的 `ReplayEngine.this` 全 final 字段 + volatile `cancelled`。复核第 1 轮清单 #10 判定成立。clean。
4. 九个 Xpl 包装器 + `EvalActionKeySelector` / `JoinSideTagFunction` / `JoinSideKeySelector` / `SideOutputTapOperator`——逐一核对：全部 final 字段、构造器内零方法调用、零监听器注册；`SideOutputTapOperator.copyForSubtask()`（L58-61）经构造器重新发布（final 冻结），安全。clean。
5. 监听器/回调注册表同族（残留点 #1 的兄弟站点）：全单元唯一回调表 = `ReplaySubscription.onCancelTasks`（ArrayList，ReplayableCdcSourceFunction.java:134-162），写者=生产基类 `DebeziumCdcSourceFunction` 注册路径（单元外）——**维持第 1 轮移交 runtime 轮次**，无未盘点兄弟站点。

**构造器 this 逃逸发现数：0。**

### 2.4 long/double 非原子读写、`++` 复合递增、可变共享引用专项 grep

- `++`/`--` 全部落点：StreamModelDslBuilder L644/648（构建期局部 inDegree/outDegree）、AdvancedTransforms L268-270（静态方法局部 triggerSlotFaces）、TransactionWindowAggregate.Acc L88 / AlertCountAggregate.Acc L99（keyed 分区单写者累加器，第 1 轮 #19 已裁）、DirectoryFileSourceFunction L181/187（`emitRemaining` **局部变量** offset）。无实例级无同步复合递增。
- long/double 实例字段：仅 fraud 模块 per-record POJO（EnrichedTransaction.historyCount、TxSummaryRow/AlertSummaryRow/FraudAlert/TransactionEvent 各字段）——管道单写者流动的记录对象，非共享可变状态；`TransactionWindowAggregate.Acc.count/minTimestamp` 为 keyed 累加器。无跨线程共享的裸 long/double 字段。
- `static final` 可变类型对象：唯一非集合形态为 `ReplayableCdcSourceFunction.REPLAY_INDEX_KEY`（static final ByteBuffer，L57-58）——全部使用点（readReplayIndex L196、writeReplayIndex L210）仅作 offsetStore 的**只读 key**，无 put/flip/clear 写路径；排除。

**发现数：0。**

### 2.5 构建期 / 运行期归属边界抽验（GraphBuilder/pipeline 装配）

- 捕获 builder 可变字段的工厂 lambda 共 3 处：StreamModelDslBuilder L2161/L2169/L2177（`body -> new Xpl*Function<>(body, sideInputScopes.get(t.getId()))`）。核实其传递链：`buildMap/buildFilter/buildFlatMap` → `resolveFunctionOrXpl(t, ..., xplWrapper)`（L2141-2152）→ **L2147 `xplWrapper.apply(xplBody)` 同栈即时执行**——lambda 不逃逸构建期，`sideInputScopes`（per-build 实例字段，第 1 轮 #16）仅在构建线程被读取一次，产物 `Xpl*Function` 只持有解析后的快照 Map（该 Map 的只读契约缺口 = 已立案 C1-004）。无构建态对象捕获进运行期。
- `resolvedSinkBeans`/`streamRegistry`/`preBuildEnvironmentConfigurer`：均 per-build、构建期单线程（第 1 轮 #16 复核成立）。
- `XplSourceFunction.running`（volatile，第 1 轮 #18）：复核 `run()` 确实不读该标志（L110-114），取消走 ctx——判定成立。

**发现数：0。**

### 2.6 候选发现否决记录：cursors 恢复交错（评估后不立案）

候选场景：region restart 窗口（C1-003 已确证的"旧 attempt 未退、新 attempt 已启"重叠）内，旧 attempt 读线程的 `advanceCursor` 与新 attempt 的 `initializeState`（L281-284 `synchronized(cursors){ clear(); putAll(restored); }`）交错。逐一推演后否决：① 全部读写/快照/清空在 `synchronized(cursors)` 内（L218-228、252-254、281-284），map 结构永不撕裂；② 交错终态恒为"某文件游标 = checkpoint 值或旧 attempt 实况值之一"，即 **REPLAYABLE 能力已声明的 at-least-once 重放语义**（javadoc L39-44 明文声明恢复从 checkpointed cursors 重放），不产生声明之外的数据损坏；③ 与 C1-003 的本质差异在于 C1-003 破坏的是**守卫语义**（零发射提前 EOF，非声明语义），本候选只是重放基线在"checkpoint vs 实况"间漂移，落在声明包络内。不立案。

### 2.7 活视图家族（C1-007 同族）完备性复查

全单元 `unmodifiable*` 返回点 9 处逐一核对：StreamSchemaRegistry L135/138（防御性拷贝后包装，clean）、StreamSqlAggregations L44（不可变 BUILTINS）、StreamSqlExprCompiler L213（不可变）、StreamScalarFunctionResolver L60（快照拷贝，拷贝期竞态归 C1-001）、ReplayableCdcSourceFunction L94（第 1 轮已核：构造器防御性拷贝后无写者）、StreamConfValidationReport L40/44（**活视图**，但该对象为同线程 builder→CLI 校验流：add/addProbeLine 与 getIssues/render 无跨线程发布路径，grep 确认单元内无并发持有者——clean）、InMemoryBeanFunctionResolver L37（= C1-007）。无新立案。

### 2.8 已声明边界复核（维持豁免）

- `DemoKeyedStateStore`（fraud/state/DemoKeyedStateStore.java:14-35 javadoc）："DEMO-ONLY"、"not a template for real applications"、fresh-disconnected-state 声明 + Reducing/AggregatingState `UnsupportedOperationException` fail-fast——符合"owner doc 明文声明且带 fail-fast 守卫"豁免标准；其匿名 MapState 的 `values` 惰性初始化（L92）与 C1-006 同族但处于声明边界内，维持豁免。
- DirectoryFileSourceFunction"每子任务快照同一游标表 + 读竞赛恢复"（javadoc L39-44）：维持第 1 轮豁免；本轮 2.6 的交错推演未突破该声明。

## 3. 终止声明

**终止信号：未发现新的高价值问题。C1 维度深挖终止。**

累计发现清单（第 1 轮 + 第 2 轮，共 7 项，序号无接续）：

| ID | 标题（缩写） | 严重度 |
|---|---|---|
| C1-001 | StreamScalarFunctionResolver.functions LinkedHashMap 逐记录热路径无锁读 × register() 任意线程写 | P1 |
| C1-002 | XplFunctionSupport.ROOT_SCOPE 静态共享 eval 根作用域无运行时守卫 | P2 |
| C1-003 | DirectoryFileSourceFunction CAS 单读者守卫非粘性（restart 窗口零发射） | P2 |
| C1-004 | sideInput 只读快照契约零强制（可变引用逃逸进逐记录作用域） | P2 |
| C1-005 | StreamModelDslBuilder.build() 就地改写调用方 StreamModel | P2 |
| C1-006 | UserHistoryEnricher.lastCityState 无同步惰性初始化（良性） | P3 |
| C1-007 | InMemoryBeanFunctionResolver.getBeans() 活视图冒充快照 | P3 |

**终止依据**：① 追加指令的五个方向（2.1-2.5）全部执行完毕，每方向均有逐落点证据且结论为 clean 或归并既有条目；② 全单元可变实例字段矩阵（定向 grep）与第 1 轮 22 项清单**零偏差**——无未盘点的共享可变状态载体残留；③ 唯一尚存的开放竞态（ReplaySubscription.onCancelTasks）其成立条件取决于单元外生产基类的注册线程模型，维持第 1 轮移交，本单元内无兄弟站点；④ 两个候选新发现（cursors 恢复交错、namesList 独立立案）经推演分别落入已声明语义包络与既有条目失效面，按"不制造发现"纪律否决并留痕（2.6/2.1）。C1 维度在本单元内的载体空间已穷尽，剩余风险集中于已立案条目，继续深挖的边际收益低于复核轮对 C1-001~007 的独立验证价值。

## 4. 覆盖声明

- **序号接续**：本轮新发现 0 项，C1-008 起序号未被占用，供后续复核轮使用。
- **本轮核查面**（在第 1 轮 98 文件全量通读之上的定向深挖）：
  - 逐行复核文件：StreamScalarFunctionResolver.java（全文）、StreamAggregatorFunctionResolver.java（全文）、IScalarFunctionResolver.java（全文）、BeanContainerFunctionResolver.java、BeanFunctionResolver.java、GlobalBeanFunctionResolver.java、StreamSqlExprCompiler.java L195-270（compileFunction/namesOf/managed-types）、StreamSqlAggregations.java（全文）、StreamSqlAggregation.java L560-641（DistinctAggregateWrapper）、SqlRowCompositeFunction.java L30-58、XplMapFunction/XplFilterFunction/XplFlatMapFunction/XplSinkFunction/XplReduceFunction/XplSourceFunction/XplFunctionSupport（全文）、JoinSideTagFunction.java（全文）、JoinSideKeySelector.java、EvalActionKeySelector.java、SideOutputTapOperator.java（全文）、StreamModelDslBuilder.java L2100-2229（build* 工厂链）+ L2141-2152（resolveFunctionOrXpl）、StreamConfValidationReport.java（全文）、ReplayableCdcSourceFunction.java（全文）、DirectoryFileSourceFunction.java（全文）、DemoKeyedStateStore.java L1-120、AdvancedTransforms.java L605-640 + owner 捕获点 grep。
  - 定向 grep 8 组：this 逃逸/监听器注册、`++`/`--`、long/double 实例字段、非 final 非 volatile 实例字段全量（三模块）、unmodifiable 活视图、集合返回型 getter、namesList/registeredNames/两 SPI 全调用点、static final 可变类型。
  - 残留点处置：5 个第 1 轮移交项逐一复核归属——#1 无兄弟站点（维持移交 runtime）、#2 在单元外（core 轮次）、#3/#5 归 C9、#4 归 C4；本维度无新增移交。
- **未改动前轮文件**；本报告为唯一新增产物。
