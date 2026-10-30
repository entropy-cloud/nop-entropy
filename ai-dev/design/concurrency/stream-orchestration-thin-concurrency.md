# stream 编排薄层并发契约（R2-09 批五项裁定）

> Status: active
> Last Reviewed: 2026-10-30（plan 2512 Phase 1 产出；本批唯一设计裁决权威）
> Source: plan 2512（`ai-dev/plans/2512-cra-phase8.18-r2-09-orchestration.md`）Current Baseline 四成员表 + Baseline 勘误 5 条 + draft review R1 增补（裁定③形态 B 须给出不改 `RichFunction` 公共契约的绑定机制，给不出即收敛深拷贝单分支）；发现事实源 = WI 1.3 审计目录 `ai-dev/audits/2026-10/2026-10-14-1440-deep-audit-nop-stream-orchestration-thin-concurrency/`；行号 = live `a6f757feaa`（分支 `concurrency-audit-remediation`）2026-10-30 亲读回读值，执行期漂移以 live 为准并登记于 plan 勘误
> 实现锚：nop-stream-sql `eval/StreamScalarFunctionResolver`（defaultResolver :31、functions LinkedHashMap :46、register :49-51、registeredNames/namesList 迭代面 :59-61/:69-71、静态 call :88-97）+ `SqlScalarXplPrinter`（SqlRegularFunction UDF 分支发射 :190-191）+ `docs-for-ai/03-modules/nop-stream-sql.md` §3a（:47 docs bug 本体）；nop-stream-fraud-example `scenario/DirectoryFileSourceFunction`（共享 cursors TreeMap :68、runEntered :79-80、cancelled :82、run :111-130[CAS :114/读点 :119/:124/finally :128]、advanceCursor :224-227、cancel :230-233、snapshotState :247-257、initializeState :261-285[clear+putAll :281-284]、putAllCursors :287-298）；nop-stream-core `operators/ProcessOperator`（copyForSubtask :42-44、open keyed 装配 :92-103[setKeyedStateStore :98]）、`operators/AbstractUdfStreamOperator.open`（:74-77，setFunctionRuntimeContext :76）、`operators/StreamingRuntimeContext`（keyedStateStore 实例字段 :17、setKeyedStateStore :69-70）、`operators/StreamSourceOperator`（copyForSubtask :226-231、close→cancel :392-400）、`util/FunctionUtils`（setFunctionRuntimeContext :51-56）、`common/functions/RichFunction`（getRuntimeContext :92、setRuntimeContext :112）；消费点 `fraud/scenario/UserHistoryEnricher`（:51/:62）
> Related：深拷贝范式先例 = [stream-connectors-concurrency-and-envelope.md](stream-connectors-concurrency-and-envelope.md) §3.2（2511 裁定② round-trip + xlang ShareRef 豁免，本文档引用不重开）；编排面相邻 = [stream-core-concurrency-and-resource-envelope.md](stream-core-concurrency-and-resource-envelope.md)（R2-07，零共享站点）；CDC 复位契约对照 = nop-stream-connector-debezium `DebeziumCdcSourceFunction.run` 复位块 :247-252

## 一、裁定总表

| 裁定 | 成员 | 唯一结论 |
|------|------|---------|
| ① | 1.3-C1-001（MA-01 主，←C3-002/C2-001；含 sql.md §3a docs bug） | `functions` 改 `ConcurrentHashMap`（零锁热路径读，迭代面 CME 免疫）+ `register`/`registerAsDefault` 运行期换绑 WARN 契约（不采 typed fail-fast）+ §3a 双路径表述同 diff 修正 |
| ② | 1.3-C3-008（独立案） | `initializeState` 弃 REPLACE（clear+putAll），改 per-file 位点 max 增量合并——位点=已发射前缀单调边界，max 合并即已发射前缀并集，防丢防重一次闭合；`restored.isEmpty()` 守卫维持 |
| ③ | 1.3-C3-009（独立案，根因载体 core） | per-subtask 封闭收敛为深拷贝单分支：`ProcessOperator.copyForSubtask` 对 `RichFunction` 形态 userFunction 做序列化 round-trip 私有化（2511 裁定②同族手段，ShareRef 豁免引用不重开）；形态 B holder 不采 |
| ④ | 1.3-C6-007（独立案） | `run()` 在 runEntered CAS 成功后复位 `cancelled=false`，落败者早退不触碰；对齐 Debezium 基类复位契约；受 C10-006 反向钉零回退约束 |
| ⑤ | C10 验收前置 | 四条收口门（C10-001~004）随本体同 diff + C10-006 反向钉零回退 + C10-007 搭车 + C10-008 不搭车；五个测试缝 + 十个零回退钉全列（§3.5） |

## 成员 → 裁定映射

| # | 成员（判级） | 面 | 裁定 | 修复位（live : 行） | 回归测试（缝，§3.5） |
|---|---|---|---|---|---|
| 1 | 1.3-C1-001（P1，MA-01 主；C3-002/C2-001 吸收；含 sql.md §3a docs bug） | LinkedHashMap 逐记录热路径读 × 公开 register 写竞态 + 运行期静默换绑 | ① | StreamScalarFunctionResolver :46/:49-51 + registerAsDefault :79-81 + sql.md §3a :47 | 缝 1（C10-001 新建） |
| 2 | 1.3-C3-008（P1，独立案，复核机制修正） | region 重启 Phase-3 下 REPLACE 清零兄弟已推进游标→checkpoint 位点滞后→恢复重放已反映窗口 | ② | DirectoryFileSourceFunction initializeState :261-285（clear+putAll :281-284 弃用） | 缝 2（C10-002 新建） |
| 3 | 1.3-C3-009（P1，独立案，根因载体 core 跨单元） | 共享 UDF × 各子任务 open() 覆写共享 RuntimeContext→P≥2 键控状态收敛单一 backend | ③ | ProcessOperator.copyForSubtask :42-44 + AbstractUdfStreamOperator.open :74-77 | 缝 3（C10-003 新建，回归锚挂 core 工单） |
| 4 | 1.3-C6-007（P1，独立案；与 C1-003 同文件不同机制两立） | cancelled volatile 跨 run 永不复位→区域重启零事件静默 EOS 假成功 | ④ | DirectoryFileSourceFunction run :111-130（CAS :114 后复位） | 缝 4（C10-004 新建） |
| 5 | C10 验收前置（4 收口门 + 1 反向钉 + 1 搭车 + 1 不搭车） | 假保护缺口补测 + 零回退钉 | ⑤ | 测试树（§3.5 两表） | 缝 1–5 全覆盖 |

## 二、背景与动机

R2-09 批四项 P1 覆盖编排薄层两类缺陷面：**共享可变态的跨线程/跨子任务纪律**（UDF 注册表 LinkedHashMap 逐记录热路径读 × 无守卫 register 写竞态、共享 UDF × 各子任务 open() 覆写共享 RuntimeContext 致键控状态收敛单一 backend、cancelled volatile 跨 run 永不复位致区域重启零事件假成功）与**共享游标状态的恢复语义违约**（region 重启 Phase-3 流水线下 initializeState 的 REPLACE 清零兄弟子任务已推进游标，checkpoint 位点持续滞后→恢复重放已反映窗口，REPLAYABLE 声明违约）。

四成员被绑为一批（batch-proposal §二.2 R2-09 行），因其共享同一验证通道（`./mvnw test -pl nop-stream -am`）与 owner doc，且 C3-008/C6-007 同文件（DirectoryFileSourceFunction）、C3-009 根因载体跨单元（core）须与失效面示例（fraud-example）同裁定。四缺陷面逐条：

1. **C1-001（注册表）**：`functions` 为非线程安全 LinkedHashMap，静态 `call()`（:88-97）在逐记录热路径无同步读 × 公开 `register()`（:49-51）任意线程写——put 触发 rehash 时并发 get 可撕裂/返回 null/CME；`registerAsDefault()` 运行期静默换绑已编译作业共用的查表实例，volatile defaultResolver（:31）只保证引用可见、不约束 map 内部状态。
2. **C3-008（游标恢复）**：region 重启 Phase-3 流水线（rebuild→restore→异步 submit→循环继续）下兄弟子任务 initializeState 的 REPLACE（clear→putAll :281-284）清零已推进/已完成文件游标→此后每个 checkpoint 携带落后于同 epoch 下游进度的源位点→恢复重放已反映窗口（键控聚合重复计入，REPLAYABLE 声明违约）；既有非空守卫 :275-280 只防 restore-vs-restore，不防 restore-vs-已推进。
3. **C3-009（RuntimeContext）**：`ProcessOperator.copyForSubtask`（:42-44）同一 userFunction 引用透传每个子任务 × `AbstractUdfStreamOperator.open`（:74-77）无同步 `setFunctionRuntimeContext` × `ProcessOperator.open` keyed 装配（:92-103）覆写共享 context——parallelism ≥2 时系统性收敛 last-writer-wins 单一 keyed backend（顺序 open 亦然，非窄窗口竞态；shipped parallelism="1" 不触发）。
4. **C6-007（cancelled 残留）**：cancel 置位的 volatile `cancelled`（:82，唯一写点 cancel :230-233）跨 run 永不复位——区域重启按 javadoc 契约复用同一实例时，重启 run 在循环顶静默零事件退出、按 EOS 成功收尾（半个成功：REPLAYABLE 恢复声明违约 + maxRestartsPerRegion 重试预算零消耗的无声数据截断；纯顺序陈旧即可触发）。

本文档一次裁定五项，使 Phase 2 实施只有一种合法形态。

## 三、核心设计契约

### 3.1 裁定①：UDF 注册表容器化 + 运行期换绑契约（C1-001，MA-01 三面一次）

- **容器化唯一结论**：`StreamScalarFunctionResolver.functions` 改 `ConcurrentHashMap`。静态 `call()`（:88-97）逐记录 `r.functions.get(fnName)` 零锁热路径读；`registeredNames`/`namesList`（:59-61/:69-71）对 keySet 拷贝前读活 map 的迭代面获 CHM 弱一致迭代器天然 CME 免疫（吸收成员 C3-002 面）；`register`（:49-51）put 与并发 get 不再撕裂。LinkedHashMap 保序语义无消费方（注册名仅用于 fail-fast 列举与编译期 contains，无序即足）。
- **运行期换绑契约一次定夺 = WARN，不采 typed fail-fast**。判据：`registerAsDefault()` 生产注册为应用侧经自身 app-beans 调用（审计 C1 复核勘误：nop-stream-sql 模块自身无注册 app-beans），换绑的发起方是应用而非框架，框架侧不存在可判定的「启动窗」契约锚——typed fail-fast 会把多作业长驻进程下合法的应用侧热更新（换实现、灰度换绑）变成无预警的部署期失败，而静默换绑则让已编译作业共用的查表实例被无痕替换。折中即「放行 + 留痕」：`registerAsDefault()`（及 `setDefault` 的非空换绑路径）在 `getDefault() != null && getDefault() != this` 时输出 WARN 日志（含新旧 resolver 标识），javadoc 成文生命周期契约——default 一经注册即全局单例语义，运行期换绑影响全部后续逐记录查表，应用自担一致性。与 R4-05 C6-003（解绑/close 语义）边界注记：本裁定只落并发容器化 + WARN，解绑语义归 R4-05 不代做。
- **换绑 WARN 契约成文（javadoc 落笔口径）**：类 javadoc 声明——default resolver 为进程级单例语义；`register` 任意时刻可调（注册新名随时生效，覆写同名即时影响后续逐记录查表）；`registerAsDefault` 在已有 default 时输出 WARN（换绑事件留痕），换绑后已编译 xpl 体无需重编译即对新记录生效（查表在逐记录路径）。观测面：换绑 WARN 触发频率归 owner doc 运维节指引，不阻塞 closure（plan Non-Blocking Follow-ups 同源）。
- **sql.md §3a docs bug 同 diff 修正（:47）**：现文案「编译期一次解析（非逐记录查找）」仅对 `StreamSqlExprCompiler.compileFunction` 的 Java 捕获闭包路径成立；声明面（`<sql>`/`<sqlTransform>` 经 SqlScalarXplPrinter :190-191 发射的生成 xpl 体）运行路径为逐记录静态查表（`StreamScalarFunctionResolver.call` 每记录经 defaultResolver 查表）。修正文案成文：「UDF 解析按声明面双路径：Java 内嵌编译路径编译期一次解析捕获闭包；`<sql>`/`<sqlTransform>` 声明面为逐记录静态查表（生成 xpl 体每记录经 `StreamScalarFunctionResolver.call` 按 defaultResolver 查表），运行期换绑（重复 `registerAsDefault`，WARN）对声明面后续记录即时生效」。未注册函数名 fail-fast 列出已注册名的表述维持。C10-001 收口门随同 diff。

### 3.2 裁定②：游标恢复 REPLACE 改增量合并（C3-008）

- **恢复语义唯一结论 = per-file 位点 max 增量合并**：`initializeState`（:261-285）弃 `cursors.clear(); putAllCursors(restored)`（:281-284）——改为 `cursors` 监视器内按文件路径取 restored 位点与现存位点较大者写入（merge-max），**零 clear**。`restored.isEmpty()` 守卫（:275-280）维持；`putAllCursors` 的 Number/String 双形态解析维持，max 在解析后的 long 域进行。
- **单调性论据（唯一恢复语义的成立基础）**：per-file 位点 = 该文件已发射前缀的下一字节偏移——`advanceCursor`（:224-227）只在 `emitLine` 之后调用（emitRemaining :181/:191/:202），位点单调不减且位点之前的字节必然已发射（本进程内）。故共享 map 中任一子任务视野的现存位点是「确已发射」的事实，restored checkpoint 位点是「已持久化」的事实；max 合并 = 已发射前缀并集：防丢（不回退到比已发射更早的位点）与防重（不越过任何已发射边界）同时成立，无需在防丢/防重间取舍。
- **与 snapshotState 交互**：`snapshotState`（:247-257）在 `cursors` 监视器内拷贝快照，max 合并后快照恒 ≥ 任一子任务已推进值，「checkpoint 位点落后于已推进值」的违约路径（复核重建机制，非发现原陈述的同 run 重发形态）结构性消除；验收断言以复核机制为准（多文件「已完成文件」变体：restore 后兄弟已推进/已完成文件游标不被清零、后续 checkpoint 位点不回退）。
- **跨进程边界声明**：max 合并语义只在共享 map 的进程内成立（位点 = 本进程已发射前缀事实）；跨进程恢复（新进程冷启动）时共享 map 为空，restored 位点即唯一事实，merge 退化为直接写入——两种形态同一代码路径，无需分支。文件内容变短（位点 > file.length）时现有代码按 `emitRemaining` :153-155 前置守卫 `cursor >= file.length()` **静默早退**（按已消费完处理，无显式报错；:160-163 skip 失败仅覆盖检查后变化窗），该静默面超出位点协议表达力，本裁定不扩展，归 C10-007 探针的契约面观察。
- **编排侧移交边界注记**：`SupervisionLoop.restartRegion` Phase-3「全部 rebuild+restore 完成后再统一 submit」为 runtime 单元纵深防御候选，归后续立项（merge-adjudication §4.6 #2），本批 `SupervisionLoop` 只读零触碰。CDC 兄弟面（`NopStreamOffsetBackingStore.setOffsets` REPLACE）已由 plan 2511 C8-001 收敛，零触碰。C10-002 收口门随同 diff；C10-007 搭车探针（§3.5）与本裁定机制面共享。

### 3.3 裁定③：RuntimeContext per-subtask 封闭 = 深拷贝单分支（C3-009）

- **形态定夺 = 收敛深拷贝单分支，形态 B holder 不采**。亲读结论（写死）：`RichFunction` 公共契约把 context 存放在 UDF 实例自身字段（`getRuntimeContext` :92 / `setRuntimeContext` :112，消费点 `UserHistoryEnricher` :51/:62 经 this 读取）；共享 UDF 实例只有一个字段槽位，per-subtask holder 若不改公共契约，唯一可实施机制是让 `StreamingRuntimeContext` 内部经侧信道（静态/ThreadLocal 注册表按 subtask 路由 keyedStateStore/timerService）——这引入新的全局可变态与注册表清理生命周期（open×N 注册、close×N 反注册、任务线程模型耦合），且 open 顺序敏感面（timerService 覆写）原样保留。改动面与风险全面大于一次性拷贝，不采。
- **深拷贝唯一结论**：`ProcessOperator.copyForSubtask()`（:42-44）对 `RichFunction` 形态的 userFunction 做序列化 round-trip 私有化（2511 裁定②同族手段）——每 subtask 私有 UDF 实例，`AbstractUdfStreamOperator.open`（:74-77）`setFunctionRuntimeContext` 与 `ProcessOperator.open` keyed 装配（:92-103，`setKeyedStateStore` :98）的共享实例字段写随之封闭：`StreamingRuntimeContext`（实例字段 :17）与 keyedStateStore 天然 per-subtask，parallelism ≥2 顺序 open 亦不再 last-writer-wins。
- **作用域与非 RichFunction 豁免**：仅 `userFunction instanceof RichFunction` 时深拷贝——键控状态污染缺陷面只存在于接受 `setFunctionRuntimeContext` 写的 RichFunction UDF；非 RichFunction UDF（纯 `ProcessFunction` lambda/匿名类）零 context 写入，共享无害，维持现引用透传（无状态 UDF 零回归）。白名单外非 Serializable 的 RichFunction UDF typed 失败（`StreamException` 指引实现 Serializable），不做静默共享回退——与跨进程部署序列化约束互证（2511 §3.2 部署期行为变更声明同源）。
- **round-trip + ShareRef 工具落点裁定（唯一结论）**：round-trip 与 ShareRef 白名单豁免工具落 **nop-stream-core util 包**（2511 裁定②判据原样平移——xlang 编译态不可变可执行体豁免判据不重开）；cep（CepOperator :345-348）与 debezium（DebeziumSourceSupport :188、DebeziumCdcSourceFunction :761）的既有模块内私有实现本批零触碰、不强制收敛（core 工具就位后其迁移归后续各自主人批）。
- **存量盘点注记（Minor-3）**：零回退钉 fixture（`TestProcessOperator`/`TestOperatorSubtaskIsolation` 的 UDF 桩）均非 RichFunction——落非拷贝透传分支，钉保绿；bean 形态 KeyedProcessFunction（nop-stream-flow `AdvancedTransforms.java:930`）是 typed 失败声明的现实暴露面，Phase 2 按 2511 §3.2「测试侧已知形态」范式登记适配（fixture 序列化化，不改断言语义）。
> 实施期勘误（core 组 live 亲读）：「钉 fixture 均非 RichFunction」前提失实——`ProcessFunction extends AbstractRichFunction implements RichFunction`，ProcessOperator 的 UDF 全部为 RichFunction，透传分支对 ProcessOperator 实际不可达；据此零回退钉 `TestOperatorSubtaskIsolation` 的 assertSame 断言属缺陷钉住本体，按 2511 C10-003 范式同 diff 反转为 assertNotSame（`processOperatorRichFunctionUdfIsPrivatePerSubtask`）。透传分支对通用工具面其他调用形态仍有效。
- **失效面外溢矩阵（C10-003 复核义务载体）**：

| UDF 形态 | 修复前 | 修复后 |
|----------|--------|--------|
| 有状态 RichFunction（如 `UserHistoryEnricher` 懒初始化 keyed store :47-55/:62） | P≥2 收敛单一 keyed backend，跨子任务静默污染 | 每 subtask 私有实例 + 私有 context/backend，跨键零污染 |
| 无状态 RichFunction | 共享可用（侥幸） | 私有实例，行为等价，零回归 |
| 非 RichFunction 纯函数 | 共享 | 维持共享（引用透传），零改动 |
| 非 Serializable RichFunction UDF | 共享可运行 | 部署期 typed 失败（行为变更，声明见上） |

- **PA 声明**：落点 `nop-stream-core` operators/util 非生成物 main 源码（含 §3.3 round-trip + ShareRef 工具的 core util 包落点），非 Protected Area（plan 头部授权行）；不触 `nop-kernel`/`nop-api-core` 平台层（非 PA 上收）；若实施中需上收平台层，批内闭环申报 plan-first 后方可（当前预判不需要——StreamingRuntimeContext/FunctionUtils 均在 core 内自洽）。`lastCityState` 懒初始化竞争面（P3 C1-006，R4-05）被本裁定主导不单独立案。C10-003 回归锚归属登记注明挂 core 工单（merge-adjudication §4.6 #1，WI 1.1 覆盖空洞闭合）。

### 3.4 裁定④：cancelled volatile 复位（C6-007）

- **唯一缝 = run 入口 CAS 成功后复位**：`run()`（:111-130）在 `runEntered.compareAndSet(false, true)`（:114）成功后、循环首次读 `cancelled`（:119）之前复位 `this.cancelled = false`。CAS 落败者立即 return（既有形态），不触碰 cancelled——`runEnteredGuardPreventsDoubleEmission`（`TestDirectoryFileSourceFunction` :213 既有钉）语义不变：并行首入落败者继续早退、零读取。单读者不变量：CAS 成功后本 run 循环内的 cancelled 读写均由本任务线程进行；跨线程的 `cancel()` 写（teardown 经 `StreamSourceOperator.close` :392-400 `sourceFunction.cancel()` :396）由 volatile 保可见。复位发生在 CAS 成功之后即天然排除「落败者误清首入者的取消位」——复位与守卫共享同一原子门，零新同步设施。
- **基类复位契约对齐（四件套对照）**：`DebeziumCdcSourceFunction.run` 复位块 :247-252（running=true / draining=false / pendingError=null / latch 重初始化）确立「region 重启复用实例须在 run 入口复位生命周期态」的基类先例；本裁定把 `cancelled` 补进同一复位纪律（DirectoryFileSourceFunction 侧无 draining/pendingError 对应面，复位集 = cancelled 一项）。
- **触发路径与两重建路径验证**：Phase-2 `waitForTerminal` 的先行关系保证 teardown cancel happens-before 新 run 读 cancelled（勘误 3）——纯顺序陈旧触发，修复后在两条重建路径（LOCAL `SupervisionLoop` 与 embedded-distributed deployTask）同时验证（C6 复核 :351 义务）：cancel 后同实例重跑全量重发 + 重跑后快照游标单调。
- **C1-003 联调边界**：修复与 C1-003（runEntered CAS 非粘性，P2 本体在 R4-05）同文件联调验证、受 C10-006 trip-wire 反向约束——本批只落 cancelled 复位，C1-003 本体（attempt 身份/代际守卫）归 R4-05 不代做；联调结论写入 R4-05 就绪注记。C10-004 收口门（含 run 中 cancel 变体）随同 diff；C10-007 搭车经本裁定机制面承载。
- **可见性论证（成文）**：复位写在 CAS 成功之后、由本任务线程执行；后续循环读在同线程，无可见性缺口；先前 run 的 cancel 写经 volatile 对本线程可见后即被复位覆盖——复位语义 = 「本次 run 以未取消态开局」。复位紧随 CAS 成功、先于循环首次读（:119）执行，与并发 cancel 的交错窗口极窄；窗口内的先到 cancel 由「teardown 必再 cancel」兜底（编排路径经 `waitForTerminal` 保证 cancel happens-before 新 run，`StreamSourceOperator.close` :396 必经）。

### 3.5 裁定⑤：测试缝与零回退钉

| # | 测试缝 | 覆盖裁定 | 断言焦点（红→绿口径） |
|---|--------|---------|----------------------|
| 1 | 并发 register×call 压窗器 + namesList CME 注入 | ① | 修复前撕裂/null/CME 红；修复后 CHM 零 CME、注册可见、换绑 WARN 断言（红①） |
| 2 | Phase-3 restore-vs-run 时序模拟（多文件「已完成文件」变体） | ② | 修复前 checkpoint 位点滞后红；修复后增量合并位点不回退、恢复重放零重复计入（红②） |
| 3 | LOCAL P=2 有状态 keyed UDF 隔离矩阵 + keyedStateStore 实例同一性白盒断言 | ③ | 修复前两子任务收敛同一 backend 红（白盒 assertSame）；修复后各子任务 context/backend 独立（红③，回归锚挂 core 工单） |
| 4 | cancel 后同实例重跑 + run 中 cancel 变体 | ④ | 修复前零事件假成功红；修复后全量重发 + 快照游标单调；run 中 cancel 变体先红留痕（红④） |
| 5 | C10-007 搭车三探针（MultiFile ≥3 文件跨文件顺序 + ≥8KB 块边界 resume 不丢不重 + 空目录 EOS 契约） | ②④ | 参数化入 CI，纯契约探针 |

**零回退钉（十个，修复 diff 零触碰钉内断言；钉零回改逐条以 git diff 为空核对）**：

1. `TestUdfE2E`（① UDF 声明面端到端钉，关键钉）
2. `TestStreamScalarFunctionResolver` 补测外其余用例（① 功能面）
3. `TestDirectoryFileSourceFunction#runEnteredGuardPreventsDoubleEmission` :213（C10-006 反向钉，关键钉——修复不得使并行首入落败者继续读取，全矩阵唯一反向约束）
4. `TestDirectoryFileSourceFunction` 既有 8 用例中钉 3 外其余（新鲜实例 resume 面）
5. `TestProcessOperator`（③ 零回归门）
6. `TestOperatorSubtaskIsolation`（③ core 子任务隔离既有钉）
7. `TestS1CdcPipelineE2E`（④ 组合行为回归）
8. `TestS2RecoveryAndRescaleE2E`（②③④ S2 场景链路钉——恢复/重分伸缩 E2E，裁定组合行为最近端到端载体）
9. `TestS2FileAggregationE2E`（② 场景链路钉）
10. `TestParallel2PcFileE2E`（② 场景链路钉）

**补测类声明**：C10-001/002/003/004/007 五个补测类（或既有测试类内新用例集）新增断言即新钉——「新增断言即新钉、缺陷态红→修复后绿」；补测不得改写既有钉内断言（钉 1–10 逐条零回改（补测类自身断言外））。既有测试资产口径：`TestStreamScalarFunctionResolver` 现有 9 用例与 `TestDirectoryFileSourceFunction` 现有 8 用例全为单线程功能面/新鲜实例 resume 面，并发与交错面零覆盖——补测即第一重并发覆盖，不以既有用例绿为收口依据。

**C10 集合落名**：C10-001/002/003/004 四条收口门随对应本体同 diff 落地（C10-003 登记注明挂 core 工单）；C10-006 反向钉维持零回退不新写；**C10-007 搭车入批**（探针面与裁定②已完成文件游标机制、裁定④假成功形状直接相关；cra-index 回填注记 G91 行 1.3 半边消号）；**C10-008 不搭车**（关联本体 C3-007/MA-04/C6-005 均在 R4-05，强落会造无本体红的空壳断言，维持归 R-G）。

端到端验证义务（rule 22，随 Phase 2 落证）：≥1 条 S2 链路 E2E（源失败/取消 → region 重启 → 恢复重放零重复零截断，覆盖裁定②④组合行为；`TestS2RecoveryAndRescaleE2E` 为最近载体，不覆盖 cancel 路径则补参数化 leg）；≥1 条 SQL UDF 声明面链路（`<sql>` 声明 → 编译 → 逐记录 call，覆盖裁定①）。

接线验证义务（rule 23）：CHM 真实被生成 xpl 体 `call()` 消费（①）；max 合并真实被 `initializeState`→`advanceCursor` 链消费（②）；per-subtask context 真实被 `UserHistoryEnricher` 形态消费断言、非仅字段存在（③）；复位真实使重跑恢复发射（④）。

无静默跳过义务（rule 24）：非 Serializable RichFunction UDF typed 失败而非静默共享（③）；新增公共分支无空方法体/TODO。

### 3.6 与既有防线及设施契约的关系

- **REPLAYABLE 声明链**：`getSourceConsistency()`（:240-243）声明 REPLAYABLE 的成立前提 = 游标位点不回退 + 跨 run 复位完整——裁定②④分别闭合两半边，声明在修复后重新成立（owner doc 表述随 Phase 3 精确化）。
- **共享实例契约链**：DirectoryFileSourceFunction javadoc（:41-44「runtime shares one source-function instance」）与 `StreamSourceOperator.copyForSubtask`（:226-231 共享透传 / :240-247 `SubtaskShardedSourceFunction` 分片豁免）构成两级共享形态——本批零改共享拓扑（不迁 `SubtaskShardedSourceFunction`），在共享形态内闭合并发缺陷。
- **keyed 状态链**：`ProcessOperator.open` 的 keyed 装配块（:70-87 restore/timer 既有语义）零改写；裁定③只封闭 per-subtask context 面，`applyPendingRestoreState`/timer 重物化时序不动。

### 3.7 实施边界与落账去向

- **Protected Area 声明**：本批站点全部位于 nop-stream-sql / nop-stream-fraud-example / nop-stream-core 非生成物 main 源码；不触 `nop-core/`/`nop-xlang/` 框架核心、不触 `model/*.orm.xml`、不做 `_` 前缀生成物改动（batch-proposal §二.2 R2-09 行 PA=无）。`nop-stream-core` 是 nop-stream 聚合内业务模块，非框架核心引擎行。若实施中裁定引入平台公共落点（per-subtask 契约设施上收 `nop-api-core`/`nop-kernel`，当前预判不需要），照 2507–2511 先例在对应裁定项内批内闭环申报 plan-first 后方可实施。
- **批内吸收成员**：C1-001←C3-002（namesList/registeredNames 编译期迭代 × put 的 CME 变体）/C2-001（同文件同行段 :46-51/:88-97）——MA-01 三面单修复，随主条目同 diff 不单列工单；cra-index 回填以主 ID 收敛 + 括注，不整行多 ID 重复立项。
- **C10 验收前置**：C10-001/002/003/004 随本体同 diff；C10-006 反向钉零回退不新写；C10-007 搭车（勘误 4 裁定）；C10-008 维持 R-G 不搭车（其关联本体 C3-007/MA-04/C6-005 均在 R4-05）。
- **先红后绿义务（E2）**：红①–红④与 §3.5 缝 1–4 一一对应，修复前红证据（竞态压窗撕裂、位点滞后断言、keyedStateStore 实例同一性 assertSame、零事件假成功）随执行落 `_tmp/` 留痕；变异或回退论证可替代先红，须登记。先例范式可引用不重开：2511 三联裁定模式（debezium testcontainers fork timeout 环境抖动 + nop-kernel 计时 flake，按 plan 2512 Baseline 勘误 5 处理）。
- **联调纪律（本批两条）**：C6-007×C1-003 同文件共验（裁定④复位与 C1-003 runEntered 守卫面，C10-006 钉保持绿）；C3-009 回归锚归属（C10-003 登记注明挂 core 工单）。
- **owner doc 同步**：`docs-for-ai/03-modules/nop-stream-sql.md` §3a（docs bug 修正 + 换绑契约一句）+ `docs-for-ai/03-modules/nop-stream.md`（source 跨 run 复用/per-subtask 契约补节或 ≤3 行摘要登记，落点 Phase 3 裁定）+ `docs-for-ai/04-reference/source-anchors.md`——sql.md §3a 修正随组 sql 本体 commit 同 diff，其余归 Phase 3 统一落账。
- **批外零承接**：R4-05 十项（MA-02~05 + C1-002/C1-003/C1-005/C3-005/C6-003/C6-005）及 C10-005/C10-008、nop-stream-runtime 编排面（SupervisionLoop 只读）、plan 2511 已修面（rocksdb/cep/avro/2pc/debezium 零回退引用）、`NopStreamOffsetBackingStore`（2511 C8-001 已收敛）——本批零顺手修。

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|----------|
| 裁定①备选：换绑 typed fail-fast | 注册发起方是应用侧 app-beans，框架无可判定的「启动窗」契约锚；fail-fast 把合法热更新变无预警部署失败；WARN + javadoc 契约「放行 + 留痕」已闭合静默面 |
| 裁定①备选：`Collections.synchronizedMap` + 读复制 | 逐记录热路径读引入锁争用；CHM 弱一致迭代已满足 fail-fast 列举面，无保序消费方 |
| 裁定②备选：runEntered 为 false（实例静默）才允许 clear+putAll、否则 typed 拒绝 | REPLACE 语义即使限定在静默窗仍会清零早前兄弟 restore/推进的游标（静默≠无兄弟推进历史）；且运行中 restore typed 拒绝把 Phase-3 异步提交时序变成脆弱前置条件；max 合并零前置时序依赖 |
| 裁定②备选：编排侧统一 submit 修复 | 属 nop-stream-runtime 单元（merge-adjudication §4.6 #2 移交项）；源类侧 max 合并已独立闭合违约路径 |
| 裁定③形态 B：per-subtask RuntimeContext holder | 不改 RichFunction 公共契约的唯一实施机制是 StreamingRuntimeContext 内部侧信道路由（静态/ThreadLocal 注册表）——新增全局可变态 + 注册表清理生命周期 + open 顺序敏感面保留，改动面全面大于一次性拷贝 |
| 裁定③备选：全部 UDF 一律深拷贝 | 非 RichFunction UDF 无 context 写入面，共享无害；一律拷贝把 typed 失败面扩大到无约束的纯 lambda（捕获外部状态即部署失败），违反无状态 UDF 零回归约束 |
| 裁定③备选：仅并行度 ≥2 时深拷贝 | 并行度部署期可变，缺陷面随 rescale 出现；无条件深拷贝保证修复行为与部署形态无关（确定性） |
| 裁定④备选：cancel() 内延迟复位 / 新增 reset() 公开方法 | 复位点悬在 cancel 与 run 之间无先行关系锚，引入「cancel 后未 run 窗口内取消位被清」的新丢取消窗；run 入口 CAS 成功后复位共享同一原子门，零新同步设施且落败者天然不触碰 |

## 五、与已有设计的关系

- [stream-connectors-concurrency-and-envelope.md](stream-connectors-concurrency-and-envelope.md) §3.2：2511 裁定② round-trip 深拷贝范式与 ShareRef 豁免判据——裁定③直接引用不重开（若涉 xpl 包装函数叶子按该豁免判据执行）。
- [stream-core-concurrency-and-resource-envelope.md](stream-core-concurrency-and-resource-envelope.md)：R2-07 同验证通道先例；WI 1.1 对 ProcessOperator/RuntimeContext 面的覆盖空洞由本批裁定③闭合（plan 2510 已收口不回写），零共享站点零回退其钉。
- `DebeziumCdcSourceFunction` :247-252 复位契约：裁定④的基类先例对照——「跨 run 复用实例须 run 入口复位生命周期态」确立为 source 函数通用纪律。
- plan 2511 C8-001 位点协议：CDC offset 兄弟面已收敛，本批裁定②零触碰该面。
- 下游：R4-05（roadmap 8.45，G11 十项）软依赖本批——裁定①为 C6-003 立前提、裁定④复位契约与 C10-006 钉现状为 C1-003/C6-005/MA-03/MA-04 同文件族引用面；就绪注记归 plan 2512 Phase 4。
- owner doc 落点（Phase 3 统一落账，本 Phase 零 owner doc 变更）：`docs-for-ai/03-modules/nop-stream-sql.md` §3a（docs bug 修正文案以 §3.1 成文为准 + 换绑契约一句）+ `docs-for-ai/03-modules/nop-stream.md`（source 跨 run 复用/per-subtask 契约补节或 ≤3 行摘要登记）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
