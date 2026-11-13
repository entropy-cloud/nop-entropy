# C6 资源生命周期与泄漏 — nop-stream-orchestration-thin 第 4 轮深挖（盲审·终局收口轮）

- 审计单元：`nop-stream/nop-stream-flow` + `nop-stream/nop-stream-sql` + `nop-stream/nop-stream-fraud-example`（src/main/java 非 `_` 前缀，98 files / 13,792 lines）。
- 仓库根：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation，HEAD 8dcfa4cd0c——与第 1/2/3 轮完全相同，live code 无漂移，前轮全部行号锚点有效）。
- 必要输入（均已按序全文读取）：第 1 轮 `C6-resource-lifecycle.md`（C6-001~005 + 16 项配对表）、第 2 轮 `C6-round2.md`（C6-006）、第 3 轮 `C6-round3.md`（C6-007）。
- 盲审纪律：除上述三个前轮文件外，未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件；未读取其他维度审计输出。跨单元对照阅读（不作为发现对象，仅核定 C6-006/007 共同根因的复用契约）：`DebeziumCdcSourceFunction`（:196-350 复位块 + cancel）、`SourceFunction`/`SinkFunction` 接口全文、`StreamSourceOperator`（copyForSubtask/close）、`OperatorChain`（deepCopy javadoc）。
- 本轮范围：**严格限定**为 C6-007 失效面闭合验证 + C6-006/007 共同根因面（region restart 复用 source 实例契约）下的 cancel-then-reuse 行为矩阵 + 第 3 轮"生命周期标志全清单"完备性独立复核。不做全面重扫。

## 1. 本轮发现

**本轮无新增发现**（无 C6-008 及以后编号）。C6-007 及其同族面的全部剩余失效点经独立复核确认闭合，细节见 §2。C6-007（P1）维持原判且本轮追加证据强化其成立性（§2.1：`cancelled` 非 transient，跨 JVM 序列化路径同样保留 stale 标志——两代际复用路径均无意外复位面，不存在可掩盖该缺陷的缓解机制）。

## 2. 排除项/定向核查

### 2.1 C6-007 失效面闭合之：`cancelled` 标志全部读写点枚举（独立 grep，非沿用前轮清单）

对 `DirectoryFileSourceFunction.java` 全文重读 + 单元级 token 精确 grep（`-w cancelled|running|draining|stopped|subscribed|started|finished`，剔除注释行）：

| 点位 | 位置 | 性质 |
|---|---|---|
| 声明 | `DirectoryFileSourceFunction.java:82` `private volatile boolean cancelled;` | **非 transient** |
| 唯一写点 | `:232`（`cancel()` 内 `cancelled = true`） | 无任何 `cancelled = false` |
| 读点 ×4 | `:119`（文件循环顶）、`:124`（finishLinger 守卫）、`:169`（read 缓冲循环）、`:173`（字节循环） | 全部在 `run()` 调用帧内 |
| 复位写点 | 无 | 全类（299 行）grep 证实 |

两项本轮追加确认：① `cancelled` **非 transient**（该类唯一 transient 字段是 `runEntered`，`:79`；readObject 重初始化仅针对 `runEntered`，`:98-104`）——因此 C6-007 的 stale 标志同时经由**两条**复用路径存活：同 JVM region restart（`OperatorChain.deepCopy` 原样共享实例，javadoc :231-232 "user functions ... are shared"）与跨 JVM 部署序列化（`cancelled=true` 被照原值序列化/反序列化）。不存在任何"意外复位"路径，C6-007 无被其他机制掩盖的可能——这是对第 3 轮发现的强化而非新缺陷，并入 C6-007 证据面，不另立案。② 写点唯一且读点全部位于 `run()` 帧内，`run()` 入口 CAS 后不复位——C6-007 第 3 轮证据片段（`:82, :110-130, :230-233`）逐行复核无偏差。

### 2.2 C6-007 失效面闭合之：`runEntered` 复位对照（基类复位四件套 vs 本类）

生产基类 `DebeziumCdcSourceFunction.run`（跨单元对照，`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java`）经本轮重读核实：

```java
// DebeziumCdcSourceFunction.java:227-237（本轮 sed 实读复核）
if (!runEntered.compareAndSet(false, true)) { return; }
// Reset lifecycle state: a region restart reuses this instance (the rebuilt
// operator chain shares the source function) after cancel() set running=false.
// Without this reset the loop below exits immediately and the CDC source is
// silently treated as EOS (missed changes, no error).
this.running = true;
this.draining = false;
this.pendingError = null;
initCompletionLatch();   // :203-207 javadoc：复用上一轮 latch 即 silent EOS，故每 run 新建
```

基类可复用实例共管理 **5** 项生命周期状态：`runEntered`（CAS + finally `:317` 复位）+ 四件套（`running`/`draining`/`pendingError`/`completionLatch`，run 入口整体复位）。`DirectoryFileSourceFunction` 管理其中同构的 2 项：`runEntered`（`:79-80` 声明、`:114` CAS、`:128` finally 复位、`:103` readObject 重初始化——配对完整，第 1 轮已判、本轮复核维持）与 `cancelled`（**无任何复位**）。对照证实：C6-007 定位精确——遗漏的恰是本类唯一的第二个生命周期标志，不存在第三个被漏掉的状态项（`cursors` 是业务状态走 checkpoint 恢复，非生命周期标志；`directoryPath`/`lineDelayMs`/`finishLingerMs` 为 final 配置）。闭合。

### 2.3 C6-007 失效面闭合之：单元内生命周期标志全清单独立复核（第 3 轮"仅 3 个"的完备性验证）

本轮独立执行两组 grep 复核第 3 轮 §2.1 清单的完备性：(a) 三模块全量实例字段 grep（`AtomicBoolean|AtomicInteger|AtomicLong|boolean` 非静态、非 final 声明模式）；(b) 补充 grep（`state|phase|epoch|generation|status` 作实例字段名——零命中）。结果：单元内生命周期标志全清单为 **4 个字段**（第 3 轮的"3 个"口径是 volatile 布尔标志，未含 AtomicBoolean 门字段；本轮扩充为 4 字段全表后与第 3 轮结论无实质冲突，且无第 5 个）：

| # | 标志 | 读写点 | 复位机制 | cancel-then-reuse 判定 |
|---|---|---|---|---|
| 1 | `DirectoryFileSourceFunction.cancelled`（:82） | 写 :232 唯一；读 :119/:124/:169/:173 | **无**（非 transient，序列化同样保留） | **BROKEN → C6-007**（重启 run 零事件假成功 EOS） |
| 2 | `DirectoryFileSourceFunction.runEntered`（:79-80, AtomicBoolean） | CAS :114；finally 复位 :128；readObject 重初始化 :103 | ✔ 双路径复位 | SAFE |
| 3 | `ReplayableCdcSourceFunction$ReplayEngine.cancelled`（:116） | 写 :148（ReplaySubscription.cancel）/:215（stop）；读 :138/:143/:169/:178 | 无复位，但引擎实例经 `createMessageSource`（:97-105）**每 run 新建**，标志无跨 run 面 | 标志本身 SAFE；跨 run 毒害由 stale emitter finally-cancel 承载 = 已立案 C6-006 |
| 4 | `XplSourceFunction.running`（flow builder/functions/XplSourceFunction.java:45） | 写 :65（cancel）；读 :69（isRunning） | 无复位 | SAFE——`run()` 不读该标志（:55-61，javadoc :34-37 显式声明）；取消观测走 `ctx.isCancelled()`（`SourceFunction.SourceContext` 接口 javadoc：production context 反映任务 mailbox cancel flag），而 mailbox 属 per-run fresh 状态（`StreamSourceOperator.copyForSubtask` javadoc :222-224 "per-subtask mutable state (mailbox executor, progress marker, running flags) is fresh"）→ 重启 run 的 body 正常执行 |

grep (a) 的其余命中逐一排除：全部为 boolean 返回值方法（`contains`/`isPassed`/`filter`/`equals`/`isAvgValid` 等约 25 处）或 final 配置布尔（`JoinSideTagFunction.left` :34、`StreamSqlAggregation.allowsNoArg` :39/`distinct` :40、`SqlRowAggregateSpec.star` :58/`distinct` :60、`StreamSqlAggregation` 内部 `min` :475）——无一为生命周期标志。**结论：第 3 轮"生命周期标志全清单"经独立复核完备，无遗漏站点。**

### 2.4 XplSourceFunction `isRunning()` 生产消费方核查

全仓 grep `isRunning()` 限定 XplSourceFunction 语境：唯一命中是该类自身 javadoc `:36` 的 `{@link #isRunning()}` 引用——**无任何生产或测试消费方**（即便作为 test surface 也无现存断言依赖其复位语义）。第 3 轮"无功能影响，排除"的判定维持，且排除理由进一步加强（连 test surface 消费方都不存在）。

### 2.5 XplSinkFunction 生命周期面核查

`XplSinkFunction.java` 全文重读（39 行）：唯一实例字段为 `final IEvalFunction body`，无任何布尔/原子字段。接口面核实：`SinkFunction` 接口（nop-stream-core `common/functions/SinkFunction.java`）仅声明 `consume()`/`getSinkConsistency()`（default）/`finish()`（default flush 钩子）——**接口无 `cancel()` 方法**，sink 侧不存在与 `SourceFunction.cancel()` 对称的标志置位入口；`XplSinkFunction` 亦未覆写 `finish()`。无标志面，无立案对象。

### 2.6 BatchLoader 族核查（任务 1 指定"若在单元内"）

单元内 `batchloader`（不区分大小写）全量 grep：唯一命中 `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/validate/StreamModelLinter.java:184`——为 lint 规则的用户可读消息字符串（`"...releases the endpoint params face (IJdbcTemplate/IBatchLoader rule)"`），非运行期 `IBatchLoader` 实现，无实例字段、无生命周期标志。**BatchLoader 族不在单元内，方向排除。**

### 2.7 SubtaskShardedSourceFunction 豁免分支核查（本轮新增排除项，第 3 轮清单未涉及）

本轮核对 `StreamSourceOperator.copyForSubtask(int)`（nop-stream-core :237-246）时发现存在第二复制语义分支：实现 `SubtaskShardedSourceFunction` 的 source function 在 per-subtask 复制时获得**函数副本**（"each subtask gets its OWN function copy"），若命中该分支，实例共享前提（C6-006/007 的共同根因面）将被削弱。单元内三模块全量 grep `SubtaskShardedSourceFunction`：**零命中**——单元内 4 个 source function 成员（2 fraud + 1 flow source + 基类承载）无一实现该接口，全部落入 legacy shared-instance 语义（javadoc :239-240 "the legacy shared-instance semantics is preserved for all other functions"）。C6-006/007 的复用前提无豁免路径，维持成立。

### 2.8 fraud-example source function 成员矩阵（任务 2：确认无第 4 个成员）

fraud-example 21 个文件全量分类（类声明 grep + 逐包归类）：实现运行期 source 语义的类**恰为 2 个**——`DirectoryFileSourceFunction`（implements `SourceFunction<String>` + `CheckpointedSourceFunction<String>`，:46-47）与 `ReplayableCdcSourceFunction`（extends 生产 `DebeziumCdcSourceFunction`，:46）。任务前提中第 3 个成员 `XplSourceFunction` **不存在于 fraud-example**（其归属 flow `builder/functions/`，已纳入 §2.3 矩阵第 4 行）；按任务书"若存在"限定语，此项如实记录为前提修正而非遗漏。fraud 其余 runtime 类均为非 source 成员：`FraudAlertPatternFunction`（extends PatternProcessFunction）、`UserHistoryEnricher`（extends KeyedProcessFunction）、`AlertCountAggregate`/`TransactionWindowAggregate`（累加器）、`CdcChangeDecoder`/`TransactionLineParser`（纯转换）、`DemoKeyedStateStore`/`UserTransactionHistory`（状态包装，demo-only 边界已声明）、5 个 POJO、`FraudDetectionDemo`/`MockTransactionGenerator`（demo main/工厂）——第 1/2/3 轮已全部出清，本轮结构复核无漂移。**确认无第 4 个 source function 成员。**

**cancel-then-reuse 行为矩阵汇总**（共同根因 = region restart 复用 source 实例契约，锚点：`OperatorChain.deepCopy` javadoc "user functions ... are shared" + `StreamSourceOperator.close()` :392-400 终态路径必调 `sourceFunction.cancel()`）：

| 成员（单元内归属） | 生命周期标志处置 | cancel-then-reuse 结论 | 归属 |
|---|---|---|---|
| DirectoryFileSourceFunction（fraud） | `cancelled` 无复位、run 读、非 transient | **BROKEN** | C6-007（P1） |
| ReplayableCdcSourceFunction（fraud） | 基类四件套 run 入口复位 + fresh latch；引擎每 run 新建 | 标志面 **SAFE**；跨 run 残留 emitter finally-cancel 无代际守卫 | C6-006（P1） |
| XplSourceFunction（flow） | `running` 无复位但 run 不读；取消观测走 per-run fresh mailbox | **SAFE** | 排除（§2.3/§2.4） |
| XplSinkFunction（flow） | 接口无 cancel，无标志 | **N/A** | 排除（§2.5） |

## 3. 终止声明

第 4 轮（终局收口轮）按任务书全部 3 项限定范围完成核查：C6-007 失效面闭合验证（§2.1 `cancelled` 读写点全枚举 + 非 transient 序列化面、§2.2 `runEntered` 对照基类五项状态复位、§2.3 生命周期标志全清单独立复核为 4 字段无第 5 个）、指定豁免面排除（§2.6 BatchLoader 族不在单元、§2.7 SubtaskShardedSourceFunction 分支单元内零实现者）、fraud-example 成员矩阵闭合（§2.8 确认 2 个成员、无第 4 个；任务前提中"XplSourceFunction 在 fraud-example"经核实不成立，其已按 flow 归属纳入矩阵）。

**未发现新的高价值问题。C6 维度深挖终止。** C6-006（P1）与 C6-007（P1）为本维度最终新增发现，共同根因（region restart 复用 source 实例契约下的代际守卫缺失）在单元内的站点已穷尽：同族 4 个生命周期标志中 2 个 BROKEN（分属两条已立案链）、2 个 SAFE，无遗留未知站点。残留移交面不变：第 1 轮残留边界 3 的 owner doc "非 REPLAYABLE" 文档漂移，归 docs 维护，不属 C6 立案对象。

## 4. 覆盖声明

- 本轮人工全文阅读（6 文件，均在本轮限定范围内）：`DirectoryFileSourceFunction`（fraud，299 行全文）、`ReplayableCdcSourceFunction`（fraud，259 行全文）、`XplSourceFunction`（flow，71 行全文）、`XplSinkFunction`（flow，39 行全文）、跨单元对照 `SourceFunction` 接口全文 + `SinkFunction` 接口全文（仅核定生命周期接口面）；定点阅读：`DebeziumCdcSourceFunction` :196-250/:317-350（复位块 + cancel + throwPendingError）、`StreamSourceOperator` :220-246/:388-402（copyForSubtask/close）、`OperatorChain` :225-262（deepCopy javadoc）。
- 机器扫描：单元级实例字段全量 grep（boolean/原子 + state/phase/epoch/generation/status 字段名）、生命周期 token 精确 grep（7 词，剔除注释行）、`isRunning()` 全仓消费方 grep、`batchloader` 全单元 grep、`SubtaskShardedSourceFunction` 全单元 grep、`CheckpointedSourceFunction` 实现者 grep、`new DirectoryFileSourceFunction` 构造点 grep、`transient` 字段 grep、`close()/open()` 覆写 grep（fraud）。
- 范围纪律：本轮未做全面重扫，全部核查限于 C6-007 失效面与 C6-006/007 同族面；前轮 16 项资源获取点配对表、C6-001~005 判定未重复核验（HEAD 未变，前轮锚点全部有效）。
- 与前三轮合并：C6 维度最终清单 = **C6-001（P1）、C6-006（P1）、C6-007（P1）、C6-002~005（P2×4）**，共 7 项；本轮 0 新增。
- 未复核声明：本文件全部条目均为盲审初稿，复核状态"未复核"，待独立审计方核验。
