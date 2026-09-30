# nop-stream 深度审计 R4 —— 文档-代码一致性与契约一致性（08 号报告）

> **轮次**：R4（深度审计第 4 轮，文档-代码一致性专项）
> **日期**：2026-09-30
> **范围**：`nop-stream/`（README + 设计文档 `ai-dev/design/nop-stream/` + `docs-for-ai` 导航/owner doc + XDSL 模型契约 + 配置/指标契约 + 跨模块契约 + 错误码契约）
> **方法**：以 live code 为准逐条核对；关键声明用类/方法签名、枚举值、常量、pom、实测运行验证。本轮执行了：
> - `./mvnw -pl nop-stream -am validate`（reactor 范围实证：`-pl nop-stream -am` 确实拉起全部 10 个子模块，Maven 4 wrapper 行为）
> - `./mvnw -pl nop-stream -am compile`（EXIT=0，跨模块 API 兼容性实证）
> - README/user-guide 快速示例**原样运行探针**（对 `nop-stream-core/target/classes` 编译执行，捕获运行时异常）
> - repo 文档链接检查器 `node ai-dev/tools/check-doc-links.mjs --strict`（nop-stream 域 0 broken link）
>
> **结论概览**：共 **13 项发现**（P0×0、P1×4、P2×5、P3×4）。docs-for-ai 层（module-groups / INDEX 任务路由 / owner doc 指标表 / user-guide / connectors）质量很高，逐项抽查基本全对；漂移集中在 **nop-stream/README.md 门面**（模块表 6/10、"规划中"自相矛盾、快速示例不可运行）、**两处快速示例的 STRICT_EXACTLY_ONCE 门控盲区**（实测必抛异常）、**cep-design.md 的推荐用法示例 API 不存在**、以及设计文档内少量历史阶段句与过期行号锚点。历史已知漂移项（INDEX/module-groups 子模块清单、RuntimeTopology）**大部分已修复**：module-groups.md 已是 10 模块正确口径，RuntimeTopology 仅以"已退役"注记出现。

---

## 发现总表

| 编号 | 严重程度 | 一句话标题 |
|---|---|---|
| R5-DC-01 | **P1** | nop-stream/README.md 快速开始示例原样执行必抛 STRICT_EXACTLY_ONCE 一致性校验异常（实测复现） |
| R5-DC-02 | **P1** | nop-stream-user-guide.md 快速示例同一盲区：默认 guarantee=STRICT 下 `fromElements+print` 必失败 |
| R5-DC-03 | **P1** | cep-design.md「方式二（当前推荐）」代码示例两个 API 调用均不存在（无法编译） |
| R5-DC-04 | **P1** | README 模块表仅列 6/10 模块（缺 rocksdb / connector-jdbc / connector-batch / connector-debezium）；01-architecture §二模块树同病 |
| R5-DC-05 | P2 | README:35 自相矛盾：XDSL 主入口标「规划中」，同文件模块表与 flow 实况均为「已落地/活跃」 |
| R5-DC-06 | P2 | INDEX.md:244 子模块清单陈旧（8/10，缺 rocksdb 与 connector-jdbc），且把 nop-stream-flow 误述为「流控」 |
| R5-DC-07 | P2 | error-handling.md 错误码描述「默认中文、仅 nop-ai 例外」规则与 NopStreamErrors 100 码全英文的实况不符（AGENTS.md 同样冲突） |
| R5-DC-08 | P2 | state-management-design.md §1 定位句仍称「纯内存 HashMap 存储 + JSON 序列化的极简策略」，与本档 §5.3 及 nop-stream-rocksdb 实况矛盾 |
| R5-DC-09 | P2 | 01-architecture §8.1 对比表残留「composite fencing token」，与 §五「单一单调 long epoch」（Stage 39 已落地）自相矛盾 |
| R5-DC-10 | P3 | 3 处过期行号锚点：checkpoint-design:31 / cep-design:262 / state-management-design:426 |
| R5-DC-11 | P3 | stream.xdef 头注释「五层执行管线」列了 6 个阶段；user-guide 称其为「权威表述」，与设计文档 5 阶段口径并存 |
| R5-DC-12 | P3 | design/nop-stream/README.md §四文件索引与阅读顺序均遗漏 failover-design.md |
| R5-DC-13 | P3 | 01-architecture:264/267/277 以裸文件名引用 `invariant-catalog.md`/`red-list.md`/`07-distributed-comparison.md`，实际均不在设计目录（后两者在 ai-dev/audits、ai-dev/analysis） |

---

## P0

无。

## P1

### [R5-DC-01] nop-stream/README.md 快速开始示例原样执行必抛 STRICT_EXACTLY_ONCE 一致性校验异常（实测复现）

**文件**：`nop-stream/README.md:26-33`（快速开始代码块）；关联代码 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/CheckpointConfig.java:49`、`.../environment/StreamExecutionEnvironment.java:347-352`、`.../model/StreamRequirementValidator.java:84-114`、`.../common/functions/source/SourceFunction.java:46-49`、`.../common/functions/sink/SinkFunction.java:34-37`

**证据片段**（README 示例，逐字）：

```java
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
env.fromElements(1, 2, 3, 4, 5)
   .map(x -> x * 2)
   .filter(x -> x > 4)
   .print();
env.execute("simple-pipeline");  // 统一走图模型路径：StreamGraph → JobGraph → TaskExecutor
```

实测探针（对 `nop-stream-core/target/classes` 原样编译执行该示例）：

```
EXECUTE_FAILED: StreamException[..., errorCode=nop.err.stream.job-execute-failed, ...]
ROOT_CAUSE: StreamException[..., errorCode=nop.err.stream.invalid-state,
  params={detail=Connector consistency validation failed for STRICT_EXACTLY_ONCE:
  source[0] has capability BEST_EFFORT but STRICT_EXACTLY_ONCE requires at least REPLAYABLE;
  sink[0] has capability AT_LEAST_ONCE but STRICT_EXACTLY_ONCE requires at least TWO_PHASE_COMMIT}]
```

**机制链**（每环均有代码锚点）：
1. `CheckpointConfig.java:49`：`processingGuarantee` 默认 `STRICT_EXACTLY_ONCE`；
2. `StreamExecutionEnvironment.execute()` `:348-352`：`validateConnectorConsistency(checkpointConfig.getProcessingGuarantee(), ...)` **无条件**先于一切执行路径调用；
3. `fromElements` → `CollectionSourceFunction` 未 override `getSourceConsistency()` → 默认 `BEST_EFFORT`（`SourceFunction.java:46-49`）；`print()` → `PrintSinkFunction` 未 override → 默认 `AT_LEAST_ONCE`（`SinkFunction.java:34-37`）；
4. `StreamRequirementValidator.java:94-108`：两项均低于 STRICT 门槛 → 抛 `ERR_STREAM_INVALID_STATE`。

**严重程度**：P1

**现状**：README 是模块门面，「快速开始」第一段代码复制即抛异常。模块自己的脚手架 `nop-stream/quickstart/template/.../Topology1MinimalPipeline.java` javadoc 明确承认该门控："默认 processingGuarantee 为 STRICT_EXACTLY_ONCE（要求 REPLAYABLE source + 2PC sink）；本拓扑的内联 source/sink 只满足 at-least-once，故显式声明降档"——即脚手架作者知道这一行为，README 示例却未同步。

**风险**：所有按 README 上手的新用户第一条管线即失败，异常信息（job-execute-failed 包装）也不指向 README 缺了哪一行；损害对整个模块文档可信度的第一印象。

**建议**：README 示例补一行 `env.getCheckpointConfig().setProcessingGuarantee(io.nop.stream.core.checkpoint.ProcessingGuarantee.AT_LEAST_ONCE);`（或改用 `createTestEnvironment()`），并加一句注释说明语义组合门控（与 quickstart Topology1 的教学口径对齐）。

**信心水平**：高（runtime 实测复现 + 全链路代码锚点）。

**误报排除**：已验证该校验调用无条件（`:348` 在 checkpoint 门控 `:358` 之前，与 classpath 上是否有 runtime 无关）；`print()`/`fromElements` 确实使用默认 capability（两函数体内无 override，`grep getSinkConsistency PrintSinkFunction` 零命中）；`execute()` 内 `requireSinkTransformations`/`buildStreamModel`/validate 顺序逐行核对。

---

### [R5-DC-02] nop-stream-user-guide.md 快速示例同一盲区：默认 guarantee=STRICT 下 `fromElements+print` 必失败

**文件**：`docs-for-ai/03-modules/nop-stream-user-guide.md:22-31`

**证据片段**：

```java
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
env.setParallelism(2);
env.enableCheckpointing(60_000);           // 周期 checkpoint（ms）
env.fromElements(1, 2, 3, 4, 5)
   .map(x -> x * 2)
   .filter(x -> x > 4)
   .print();
StreamExecutionResult result = env.execute("simple-pipeline");
```

**严重程度**：P1

**现状**：与 R5-DC-01 同一机制（默认 `STRICT_EXACTLY_ONCE` + `validateConnectorConsistency` 无条件前置）。示例里的 `enableCheckpointing(60_000)` 不改变 guarantee，只影响 `:358` 之后的 checkpoint 工厂门控——失败发生在更早的 `:348`。该文档其余部分（checkpoint 工厂诚实契约 ：33、算子表、XDSL 节、触发语义矩阵）抽查准确度很高，唯独这第一个示例不可运行。

**风险**：owner doc 面向「使用 nop-stream 构建流作业」的开发者（INDEX.md:136 路由入口），首个示例失败直接误导 XDSL 之前的全部 DataStream API 评估。

**建议**：同 R5-DC-01，示例加显式 `setProcessingGuarantee(AT_LEAST_ONCE)`；或改用可重放 source + 2PC sink 组合并保留 STRICT（更贴合文档宣称的「产品推荐主入口」叙事，但示例变长）。同时在示例后补一句「为何需要降档」指向语义组合规则节（:153 已有该规则，示例与规则脱节）。

**信心水平**：高（与 DC-01 同一 runtime 探针证据 + 同一校验调用点）。

**误报排除**：已核对 `enableCheckpointing` 只置 `checkpointingDeclared` 与 checkpointEnabled/interval（`StreamExecutionEnvironment.java:157-162`），不触碰 `processingGuarantee`；文档 :33 的工厂接线描述本身与 F-06 实现一致（非本发现范围）。

---

### [R5-DC-03] cep-design.md「方式二（当前推荐）」代码示例两个 API 调用均不存在（无法编译）

**文件**：`ai-dev/design/nop-stream/cep-design.md:56-67`

**证据片段**（文档逐字）：

```java
NFA<Event> nfa = NFACompiler.compile(pattern, timeoutHandling, comparator);
SharedBuffer<Event> buffer = new SharedBuffer<>(stateStore, serializer, config);
...
Collection<Map<String, List<Event>>> matches = nfa.advanceTimeSharedBuffer(
    sharedBufferAccessor, event, timestamp, state);
```

live code 对照：
- `NFACompiler`（`nop-stream-cep/.../nfa/compiler/NFACompiler.java`）仅有 `public static <T> NFAFactory<T> compileFactory(...)`（:73）与 `canProduceEmptyMatches(...)`（:100）——**不存在 `compile(pattern, timeoutHandling, comparator)`**。真实用法（`FraudDetectionDemo.java:110`）：`NFACompiler.compileFactory(pattern, true).createNFA()`。
- `advanceTimeSharedBuffer` **全仓库 0 命中**。`NFA` 的真实 API 是 `advanceTime(SharedBufferAccessor<T>, NFAState, long, AfterMatchSkipStrategy)`（`NFA.java:266-275`），返回 `Tuple2<Collection<Map<String,List<T>>>, Collection<Tuple2<...,Long>>>`——参数表无 `event`，返回类型也非文档所写。
- 第三行 `new SharedBuffer<>(stateStore, serializer, config)` 与 `SharedBuffer.java:165-169` 三参构造一致（该行没错）。

**严重程度**：P1

**现状**：该示例被文档明确标注为「方式二：直接使用 NFA + SharedBuffer（**当前推荐**）」，且下文声称「FraudDetectionDemo 使用方式二，完全绕过 DataStream API」——FraudDetectionDemo 实际调用的是 `compileFactory(...).createNFA()` + `NFA.process(...)`/`advanceTime(...)`，与示例签名不同。

**风险**：CEP 是模块三大聚焦能力之一（00-vision §七）；按此「当前推荐」示例写直连 NFA 代码的开发者两行都编译不过，且文档返回类型谎言（漏掉 timeout 结果通道）会引入语义错误。

**建议**：示例改为 live API 形态（`compileFactory(pattern, timeoutHandling).createNFA()`；`nfa.advanceTime(sharedBufferAccessor, nfaState, timestamp, afterMatchSkipStrategy)` 返回 `Tuple2`，说明两个集合分别为 matches 与 timed-out），或直接内联 FraudDetectionDemo 的真实片段并标注文件行号锚点。

**信心水平**：高（API 面全量 grep + 真实消费者 FraudDetectionDemo 对照）。

**误报排除**：`NFACompiler.compile` 在仓库任何分支形态下均无定义（`grep -n "public static"` 全量列出仅 2 个静态方法）；`advanceTimeSharedBuffer` 在 `.java` 全仓 0 命中（含测试）；`SharedBuffer` 三参构造确认为真实签名（排除连构造器一起写错的过度判定）。

---

### [R5-DC-04] README 模块表仅列 6/10 模块（缺 rocksdb / connector-jdbc / connector-batch / connector-debezium）；01-architecture §二模块树同病

**文件**：`nop-stream/README.md:11-18`（模块表）；`nop-stream/pom.xml`（`<modules>` 10 项）；`ai-dev/design/nop-stream/01-architecture-baseline.md:18-26`（模块树）

**证据片段**：

- `nop-stream/pom.xml` modules（10）：`nop-stream-core`、`nop-stream-cep`、`nop-stream-connector`、`nop-stream-connector-batch`、`nop-stream-connector-jdbc`、`nop-stream-connector-debezium`、`nop-stream-runtime`、`nop-stream-rocksdb`、`nop-stream-flow`、`nop-stream-fraud-example`。
- README 模块表（6 行）：core / runtime / cep / connector / fraud-example / flow。**缺失 4 个**：`nop-stream-rocksdb`、`nop-stream-connector-jdbc`、`nop-stream-connector-batch`、`nop-stream-connector-debezium`；且 connector 行描述「（nop-batch 桥接、CDC、消息队列）」未含 JDBC 2PC。
- `01-architecture-baseline.md:18-26` 代码块模块树同样只有 6 个（§三存储层却出现 `JdbcCheckpointStorage`，§五大量引用 rocksdb 相关裁定），模块清单与正文自身引用不一致。
- 对照：`docs-for-ai/01-repo-map/module-groups.md:23` 已是正确口径——「子模块（与 `nop-stream/pom.xml` `<modules>` 一一对应，共 10 个）」并列全 10 项。2026-08-06 历史漂移在 module-groups 已修复，但 README/01-architecture 两个门面未跟进。

**严重程度**：P1

**现状**：README 与架构基线是进入 nop-stream 的前两份文档。缺失的 4 个模块里有 2 个生产关键面：`nop-stream-rocksdb`（唯一 off-heap 状态后端，且 module-groups:23 明文警示「CEP 算子与 RocksDB 状态后端的组合当前不可用」——这一边界在 README 层完全不可见）、`nop-stream-connector-jdbc`（JDBC 两阶段提交 exactly-once sink，user-guide :154 依赖它讲述 2PC 能力）。

**风险**：仅读 README/01-architecture 的开发者会得出「nop-stream 只有 Memory 后端、连接器只有 file/message/CDC/batch」的错误结论；技术选型（状态后端、exactly-once sink）在错误前提上做出。

**建议**：README 模块表补齐 10 行（或在 connector 行下加 3 个子模块行 + rocksdb 行），并在 connector 行描述中加「JDBC 2PC」；01-architecture §二模块树同步补齐（其表格式「模块职责边界」表 :30-36 也只覆盖 6 模块，建议同改）。可引用 module-groups.md:23 的现成文字避免口径分叉。

**信心水平**：高（pom 与文档逐行 diff）。

**误报排除**：`quickstart/` 目录不在 pom `<modules>` 中（它是脚手架模板，有自己的 README 且 INDEX.md:140 已登记），不计入「缺失模块」；README 表若被理解为「非穷举」也不成立——表头即为「## 模块」全量清单语境，且 module-groups 明文「一一对应共 10 个」构成对照基线。

---

## P2

### [R5-DC-05] README:35 自相矛盾：XDSL 主入口标「规划中」，同文件模块表与 flow 实况均为「已落地/活跃」

**文件**：`nop-stream/README.md:35`

**证据片段**：

> DataStream API 是 StreamModel 的编程构造器，不是最终用户的主入口。主入口是 XDSL 声明式图模型定义（**规划中**，见 nop-stream-flow 模块）。

对照：同文件 :18 模块表 `nop-stream-flow | 活跃 | XDSL 声明式流编排，依赖 core + cep（CepPatternModel）+ nop-xdefs`；live 实况：`nop-stream-flow` 有完整 main 源（`StreamModelDslBuilder` 810 行 + `AdvancedTransforms` 618 行 + 29 个 model 类），覆盖 xdef 全部 15 种 transform，26 个 `.stream.xml` 消费 `/nop/schema/stream/stream.xdef`；00-vision.md:14 称 XDSL「已落地」，quickstart 3 拓扑中 2 个是 XDSL 形态，user-guide 整节教 XDSL 用法。

**严重程度**：P2

**现状**：同一 README 内部两处口径相反；「规划中」是历史阶段残留。

**风险**：读者采信 :35 会认为 XDSL 不可用、直接放弃产品推荐主入口；与 00-vision/user-guide 的「已落地」叙述冲突后，读者无从判断谁新谁旧。

**建议**：:35 改为「主入口是 XDSL 声明式图模型定义（已落地，见 nop-stream-flow 模块与 `nop-stream/quickstart/`）」，与 :18 表项、00-vision:14、quickstart/README 对齐。

**信心水平**：高。

**误报排除**：无（矛盾为同一文件内的直接文本冲突）。

---

### [R5-DC-06] INDEX.md:244 子模块清单陈旧（8/10，缺 rocksdb 与 connector-jdbc），且把 nop-stream-flow 误述为「流控」

**文件**：`docs-for-ai/INDEX.md:244`（「当前项目的关键认知」节）

**证据片段**：

> `nop-stream/` 是流处理引擎子模块组，包含 `nop-stream-core`（…）、`nop-stream-cep`（…）、`nop-stream-runtime`（…）、`nop-stream-connector`（…）、`nop-stream-connector-batch`（…）、`nop-stream-connector-debezium`（…）、`nop-stream-flow`（**流控**）、`nop-stream-fraud-example`（欺诈检测示例）。

问题：(a) 8 项清单缺 `nop-stream-rocksdb`、`nop-stream-connector-jdbc`；(b) `nop-stream-flow` 被描述为「流控」（flow control）——实为 XDSL StreamModel 声明式编排（flow = 流程编排而非流量控制）；(c) 与 :23 所引 `module-groups.md`（「共 10 个」+ 正确职责描述）同页冲突。

**严重程度**：P2

**现状**：INDEX.md 的 By Task 路由表（:135-144）与链接全部有效且准确；漂移仅在「关键认知」小结这一段，属 2026-08-06 漂移的残留（当时指出的清单滞后问题在 module-groups 已修、此处漏改）。

**风险**：「流控」误导是实质性的——flow control 在 nop-stream 是 edge 上的 `FlowControlPolicy`/`EdgeConfig` 概念（runtime 域），把声明式编排模块误标为流控会让找「流控」和找「XDSL 编排」的两类读者都走错门；rocksdb/jdbc 缺失同 R5-DC-04。

**建议**：:244 改为与 module-groups.md:23 相同的 10 模块口径（或直接收缩为一句「见 `01-repo-map/module-groups.md` nop-stream 条目」避免双份清单漂移——双份清单正是本轮与 2026-08-06 两轮漂移的共同根因）。

**信心水平**：高。

**误报排除**：`nop-stream-flow` 无任何流控职责（`FlowControlPolicy`/`EdgeConfig` 均在 core/runtime，flow 模块 0 引用）；清单缺项以 pom 为准核对。

---

### [R5-DC-07] error-handling.md 错误码描述「默认中文、仅 nop-ai 例外」规则与 NopStreamErrors 100 码全英文的实况不符

**文件**：`docs-for-ai/02-core-guides/error-handling.md:119-121`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/exceptions/NopStreamErrors.java`（707 行，100 个 `define(...)`）

**证据片段**：

- owner doc 规则（:119）：「`ErrorCode.define(...)` 中的描述消息默认使用**中文**，框架会通过 i18n 机制翻译。」（:120）例外名单逐模块列出且**仅覆盖 `nop-ai/*` 模块族**；（:121）「其他模块的新增业务错误码仍按默认规则使用中文」。
- live：`NopStreamErrors.java` 全部 100 个错误码描述为英文（如 :47 `"Job {jobId} is already hosted by this cluster"`），`\p{Han}` 扫描 0 命中；对照 `nop-dao` 的 `DaoErrors` 确为中文（如 `"事务提交失败"`）——即 nop-stream 是**未登记的第三个全英文模块族**。
- 旁证冲突：AGENTS.md Code Conventions 写「Error messages must be in English」，与 error-handling.md:119 的「默认中文」互相矛盾。
- i18n 面：仓库无任何 `nop.err.stream.*` 的 locale 条目（`.i18n.yaml` 仅 nop-datav / demo 存在），与「英文内联、不经 i18n」的实况自洽，但与 owner doc 的「中文 + i18n 翻译」模型不符。

**严重程度**：P2

**现状**：错误码**命名**契约完好：100 码全部 `nop.err.stream.*` 前缀（逐码 grep 统计），常量名 `ERR_STREAM_*` 与之对应，无偏离命名规范的码。漂移仅在描述语言规则的例外名单未随 nop-stream 的英文化更新（或 nop-stream 违反了规则——两说必居其一，但 owner doc 未裁决）。

**风险**：按 owner doc 规则给 NopStreamErrors 新增中文描述的贡献者会造成同文件中英混排；i18n 译者会为 stream 码制造无人消费的翻译条目；AGENTS.md 与 owner doc 的规则冲突会让「读哪份信哪份」变成掷硬币。

**建议**：二选一并落档：(a) 在 error-handling.md:120 例外名单中正式登记 nop-stream（如同 nop-ai 的登记格式，附裁定 plan 引用）；(b) 若 nop-stream 应回归中文，开修复 plan。同时消除与 AGENTS.md「Error messages must be in English」的表述冲突（AGENTS.md 说的是异常消息英文，owner doc 说的是 define 描述默认中文——建议在 error-handling.md 开头显式区分这两类消息的语言规则）。

**信心水平**：高（全量 grep + 双模块对照）。

**误报排除**：已确认 nop-stream 不在 :120 例外名单的 11 个模块中；中文扫描用 `\p{Han}` 于 100 条 define 描述全负；命名规范（`nop.err.stream.*`）本身无发现——避免把「语言规则漂移」误报为「命名漂移」。

---

### [R5-DC-08] state-management-design.md §1 定位句仍称「纯内存 HashMap 存储 + JSON 序列化的极简策略」，与本档 §5.3 及 nop-stream-rocksdb 实况矛盾

**文件**：`ai-dev/design/nop-stream/state-management-design.md:9`（Created 2026-05-20, Revised 2026-06-01）

**证据片段**：

> nop-stream 采用纯内存 HashMap 存储 + JSON 序列化的极简策略，同时定义了分布式场景下的 `StateShard` 分片和 `StatePath` 持久化路径规则。

对照：同文档 :169-180「§5.3 RocksDBStateBackend……第二个状态后端实现（Stage 30 交付，Stage 34 演进键布局）。所有 keyed state 存储在 off-heap 的 RocksDB 列族中」+ :146/:151 后端层次图含 `RocksDBStateBackend → RocksDBKeyedStateBackend<K>` + :102-103 增量 checkpoint SST 恢复路径；live：`nop-stream-rocksdb` 为 pom 注册模块（10 模块之一）。user-guide:42/76 亦明确「状态后端（memory / RocksDB）」。

**严重程度**：P2

**现状**：典型的「历史阶段句未被 current-state 更新」——§1 是读者最先读到的定位段，而文档更深处已如实记录 RocksDB。这属于 2026-08-06 分析指出的「多份设计文档混合目标态/历史阶段/current-state 叙述」模式在 HEAD 的存量残例。

**风险**：只读 §1 的读者（或被 AI 摘引用该句）会排除 RocksDB 后端做容量/成本设计；与 module-groups 明文的「CEP×RocksDB 组合边界」警示叠加后更易产生错误记忆。

**建议**：§1 定位句改为「状态后端可插拔：默认 Memory（JSON 序列化）+ RocksDB（off-heap 列族，`nop-stream-rocksdb`，见 §5.3）」，或在该句后追加「（RocksDB 后端见 §5.3，Stage 30 起交付）」。

**信心水平**：高。

**误报排除**：该句上下文无「早期/曾」等历史时态标记，不能解读为历史叙述；§5.3 与模块存在性均以 live 为证。

---

### [R5-DC-09] 01-architecture §8.1 对比表残留「composite fencing token」，与 §五「单一单调 long epoch」（Stage 39 已落地）自相矛盾

**文件**：`ai-dev/design/nop-stream/01-architecture-baseline.md:667`（§8.1 表「分布式 HA」行）vs 同文件 :404-424（§五「单调 long fencing epoch（Stage 39 已落地，闭合 M6）」）

**证据片段**：

- :667：`已落地（Stage 38）：ILeaderElector WIRE 进 JobCoordinator + 平台 SysDaoLeaderElector（JDBC lease，零 ZooKeeper 依赖）+ composite fencing token`
- :404：`Stage 39 把原复合 String fencing token（leaderId@epoch#recoveryGen）统一为单一单调 long epoch`，编码 `fencing_epoch = leaderEpochValue * EPOCH_SCALE + recoveryGen`（live：`JobCoordinator.java:119` `EPOCH_SCALE = 1_000_000L`；`ClusterRegistry` 接口已改 `long fencingEpoch`）。
- `checkpoint-design.md:31-52`（§2.1.2）同口径确认 long 统一，且 `TestFencingEpochUnification` 存在。

**严重程度**：P2

**现状**：§8.1 是「与其他流处理引擎的架构对比」速查表，其「composite fencing token」是 Stage 38 的历史形态；§五 与 checkpoint-design §2.1.2 均已按 Stage 39 收敛，:425 甚至明确写「持久化边界 String 是 `String.valueOf(long)` 单值，**非历史复合** `leaderId@epoch#recoveryGen`」。

**风险**：对比表是向外部解释 nop-stream HA 设计的高频引用点；「composite token」会误导读者以为持久化/校验仍是复合串（例如据此设计外部监控或重放工具会解析错格式）。

**建议**：:667 改为「+ 单调 long fencing epoch（Stage 39 统一，`leaderEpochValue * EPOCH_SCALE + recoveryGen`）」。

**信心水平**：高（同文件两节直接冲突 + live 常量锚点）。

**误报排除**：:667 所在行其余声明（ILeaderElector WIRE、SysDaoLeaderElector、零 ZooKeeper）均属实，故排除整行过期的过度判定——仅 token 措辞为历史残留。

---

## P3

### [R5-DC-10] 3 处过期行号锚点

**文件与证据**：

1. `ai-dev/design/nop-stream/checkpoint-design.md:31`：「live 修复点 `CheckpointCoordinator.java:896-900`」——HEAD 的 896-900 是 `abortPendingCheckpoint`/线程池工厂代码；所述「恢复后 `restoredId + 1`」逻辑实际位于 `CheckpointCoordinator.java:983-989`（`advanceCheckpointIdCounterAfterRestore`，`checkpointIdCounter.set(restoredId + 1)` 在 :986）。
2. `ai-dev/design/nop-stream/cep-design.md:262`：「`CepOperator.open()`（`CepOperator.java:209`）」——HEAD 的 :209 是 `cepRuntimeContext` 字段 javadoc；`open()` 实际在 :322，IKeyedStateBackend 创建/回退 Memory 的逻辑在 :363-387。
3. `ai-dev/design/nop-stream/state-management-design.md:426`：「`AbstractStreamOperator.snapshotState()`……（参见 `AbstractStreamOperator.java:261-295`）」——HEAD 的 :261-269 是 `doRestoreKeyedStates`；`snapshotState` 实际在 :275-306。

**严重程度**：P3

**现状**：三处语义声明本身与 live 一致（方法存在、行为如述），仅行号随代码演进漂移。

**风险**：低——审计/复核场景按行号定位会读到无关代码，浪费一轮排查；不产生行为误导。

**建议**：行号锚点改为符号锚点（类名#方法名），或在本轮文档批量维护时顺手刷新三处行号。

**信心水平**：高（三处均以 sed 定位实际代码核实）。

**误报排除**：仅收语义正确、行号漂移的锚点；语义+位置双错的锚点未发现（避免把正确锚点误伤）。

---

### [R5-DC-11] stream.xdef 头注释「五层执行管线」列了 6 个阶段；user-guide 称其为「权威表述」，与设计文档 5 阶段口径并存

**文件**：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:9-10`；`docs-for-ai/03-modules/nop-stream-user-guide.md:14`；`nop-stream/README.md:5`；`ai-dev/design/nop-stream/00-vision.md:132`；`ai-dev/design/nop-stream/01-architecture-baseline.md:13,100-107`

**证据片段**：

- stream.xdef:9-10：「三种入口最终生成同一套 StreamModel，经**五层执行管线**编译执行：`StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → GraphExecutionPlan`」——说「五层」枚举 6 项（自身算术矛盾）。
- user-guide:14：「编译管线（**xdef 头注释的权威表述**）：`StreamModel → … → DeploymentPlan → GraphExecutionPlan`」——把 6 项版本封为权威。
- README:5 / 00-vision:132 / 01-architecture:13 的口径为 4-5 阶段、**不含** `GraphExecutionPlan`；01-architecture §四（D71，:93-107）专门用一张三视角表裁定层数口径（图模型 2 层 / 执行管线 5 阶段 / 部署分层 2 层），未把 `GraphExecutionPlan` 计入管线阶段（它是 LOCAL 模式下 DeploymentPlan 的运行时执行形态，`StreamExecutionEnvironment.runLocal:457` `GraphExecutionPlan.build(jobGraph, deploymentPlan, …)`）。

**严重程度**：P3

**现状**：D71 裁定已消除三份设计文档间的层数冲突，但 xdef 注释的「五层+6 项」算术错误被 user-guide 升格为「权威表述」，形成第 4 种口径。

**风险**：低——各口径可相互解释（GraphExecutionPlan 是否计入属视角差异），但「权威」标签让算术错误的版本优先级最高。

**建议**：xdef 注释改「执行管线」并明确「五阶段 + LOCAL 运行时执行形态 GraphExecutionPlan」，或改「六阶段」二选一；user-guide:14 的「权威表述」措辞改为引用 01-architecture §四 D71 表。

**信心水平**：高。

**误报排除**：`GraphExecutionPlan` 类真实存在且确在 LOCAL 路径消费 DeploymentPlan（`StreamExecutionEnvironment.java:457`），两条口径各自内部自洽——判定为措辞/计数问题而非能力声明错误。

---

### [R5-DC-12] design/nop-stream/README.md §四文件索引与阅读顺序均遗漏 failover-design.md

**文件**：`ai-dev/design/nop-stream/README.md:331-515`（「四、文件索引与阅读路径」+ 阅读顺序）

**证据片段**：索引节按「愿景层/架构基线层/…/参考层」列出全部设计文档（00-vision、01-architecture、core/graph-model/checkpoint/mailbox/state/window/time/connector/cep/stream-dsl/composite-scenario/observability/pre-submit/distributed-runbook/comparison/component-roadmap 等），**无 `failover-design.md`**；「阅读顺序」16 项也无它。而该文件实际存在于同目录（347 行），且被 `01-architecture-baseline.md:529`（「详见 `failover-design.md`」）与 `00-vision.md` Related 引用，是 Stage 27 NO-GO 裁定 + Stage 44 五个 successor 落地注记的承载文档。

**严重程度**：P3

**现状**：目录级索引漏登记一个存在且被引用的文档。

**风险**：低——经 01-architecture 的交叉引用仍可发现；但「容错层」索引小节（:379 起）缺 failover 主题，按索引找容错设计会漏读 NO-GO 裁定与 successor 状态。

**建议**：在「容错层」小节补 `- failover-design.md`（一句话定位：targeted failover Stage 27 NO-GO 裁定 + Stage 44 successor 落地注记），阅读顺序「按需深入」段插入相应序号。

**信心水平**：高（索引节全量 grep `.md` 引用与目录 ls diff，唯一缺失项即 failover-design.md）。

**误报排除**：`checkpoint-module-extraction.md` 引用（README.md:117）经全仓查找存在于 `ai-dev/analysis/`，非 broken，不列为发现；README.md 自身不入索引属正常。

---

### [R5-DC-13] 01-architecture:264/267/277 以裸文件名引用 `invariant-catalog.md`/`red-list.md`/`07-distributed-comparison.md`，实际均不在设计目录

**文件**：`ai-dev/design/nop-stream/01-architecture-baseline.md:264`（「交叉引用 `invariant-catalog.md` §5 不变式 #5」）、`:267`（「登记 `red-list.md` 移交 I2」）、`:277`（「详见 `07-distributed-comparison.md` §6」）

**证据片段**：三个文件的实际位置分别是 `ai-dev/audits/nop-stream-invariants/invariant-catalog.md`、`ai-dev/audits/nop-stream-invariants/red-list.md`、`ai-dev/analysis/nop-stream/07-distributed-comparison.md`——均不在 `ai-dev/design/nop-stream/` 下；裸文件名引用在该目录内无法解析（repo 链接检查器只校验显式路径链接，裸名不报错，属检查盲区）。同目录 `checkpoint-design.md:27` 的同型引用用了全路径 `ai-dev/audits/nop-stream-invariants/invariant-catalog.md`（正确范例）。

**严重程度**：P3

**现状**：三个目标文件都存在、语义引用正确，仅路径书写不可解析。

**风险**：低——读者/AI 需全仓搜索才能续读；跨目录（design→audits/analysis）的权威链路断裂与 AGENTS.md「按边界规则不直接链接 ai-dev 路径」的 docs-for-ai 约束无关（此处是 ai-dev 域内互引，全路径合法且已有先例）。

**建议**：三处补全路径（与 checkpoint-design.md:27 同型）；如嫌长可在节首统一声明「本文档裸文件名 `invariant-catalog.md`/`red-list.md` 指 `ai-dev/audits/nop-stream-invariants/` 下同名文件」。

**信心水平**：高。

**误报排除**：链接检查器 strict 模式对这三处零报错已复核（其只解析带路径的链接）；三个目标文件均实存，排除 broken-link 性质的更高级别判定。

---

## 抽查通过项（正面确认与误报排除，防止后续轮次重复开案）

以下声明逐条核对**与 live code 一致**，列为通过项：

1. **构建命令**：`./mvnw clean install -pl nop-stream -am -DskipTests -T 1C`（README:40）——实证 reactor 含全部 10 个 nop-stream 子模块（validate 输出逐模块 SUCCESS），Maven 4 wrapper 下 `-pl <聚合 pom> -am` 会拉起子树，命令正确。
2. **README DISTRIBUTED 能力注记（:7）全部类名实存**：`EmbeddedDistributedExecutor`/`RpcDistributedExecutor`/`JdbcClusterRegistry`/`JdbcLeaderElector`/`StreamNodeAutoRegistration`/`JobCoordinatorMain`/`TaskManagerMain`/`TestMultiJvmExactlyOnceRecovery`/`DataPlaneMessageServiceAdapter`/`SysDaoWireCodec`/`PulsarStringWireCodec`；`remoteDeployMode`（`JobCoordinator`/`RpcDistributedExecutor` 标志位）、`deployTask` RPC default 方法（`IStreamTaskRpcService.java:71-75`）、Stage 48 Kafka 后端 `KafkaStringWireCodec` 均存在。「尚未实现 K8s/YARN 编排与 HPA」如实。
3. **跨模块契约（runtime → nop-rpc-core / nop-cluster-core / nop-message-core）**：`ReflectiveRpcService(String, Class<?>, Object, IRpcMessageTransformer)` 4 参构造、`MessageRpcServer.setTopic/setMessageService/setRpcService/setMessageAdapter`、`MessageRpcClient`、`RpcChannelState`、`RpcServiceProxyFactoryBean`、`ILeaderElector.addElectionListener/isLeader`、`ILeaderElectionListener.becomeLeader/becomeFollower(LeaderEpoch)`（含 default `becomeFollower(null)`）——逐签名与 `nop-network/nop-rpc/nop-rpc-core`、`nop-cluster/nop-cluster-core` 当前 API 一致；`./mvnw -pl nop-stream -am compile` EXIT=0 实证无 API 漂移。
4. **执行管线 live 形态**：`StreamExecutionEnvironment.execute()` 严格按 `StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan`（:398-419），LOCAL 经 `GraphExecutionPlan.build + TaskExecutor`（:453-504），DISTRIBUTED 经 `IStreamExecutionDispatcher`（:363-371, :440-447）——README/设计文档叙述与代码一致。
5. **XDSL 模型契约**：`stream.xdef`/`pattern.xdef` 引用的 8 个 core/cep 枚举与类（FlowControlPolicy/PartitionPolicy/StreamRequirement/ProcessingGuarantee/JobTerminationMode/AccumulationMode/SourceConsistencyCapability/SinkConsistencyCapability）全部存在；26 个 `.stream.xml` 的 `x:schema="/nop/schema/stream/stream.xdef"` 路径存在；`_gen` 生成物与源 xdef 同步（`_StreamEdgeModel` 10 属性、`_StreamModel` 19 顶层节点、`_CheckpointConfigModel` 14 属性逐一比对 xdef，零过期）；`StreamModelDslBuilder`（Phase 1 六型 inline）+ `AdvancedTransforms`（九型）覆盖 xdef 全部 15 种 transform；`strategyRef`/`patternRef` 经 `model().getStrategy/getPattern` 解析且未知名 fail-fast（`ERR_STREAM_REF_UNKNOWN`）；xdef 声明的 FL-1 不可消费字段（source `<params>`/`maxParallelism`/`outputType`/`consistencyCapability`）在 builder 中 fail-fast（`StreamModelDslBuilder.java:539-559`）——声明面与消费面契约一致。
6. **quickstart 模板**：`Topology1MinimalPipeline` 与 live API 签名一致（`fromElements/map/filter/sink(SinkFunction)/execute`），且**正确地**显式降档 `AT_LEAST_ONCE`（其 javadoc 正是 DC-01/02 缺失的说明）；`topology2` XDSL 声明 `processingGuarantee="AT_LEAST_ONCE"` 与能力组合规则一致；generate.sh/verify.sh 存在。
7. **指标契约**：owner doc（`docs-for-ai/03-modules/nop-stream.md:20-75`）自称「唯一权威名表」——代码侧 24 个指标名/前缀字面量与表**完全一致**（engine 7、task 5、operator 3+`numLateRecordsDropped` 命名例外、io 4、state rocksdb 前缀）；配置键 `nop.stream.ops.metrics.enabled`/`nop.stream.metrics.log.*` 与 `StreamOpsConfig.java:25`、`StreamMetricsReporter.java:48` 一致且有文档（:100-103）。
8. **01-architecture 大量锚点抽查通过**：`SubtaskTask` 状态机 CREATED/RUNNING/CANCELING/…（:36-41）；`JobCoordinator implements IStreamCoordinatorRpcService`（:90）；JDBC 自动建表 `nop_stream_coordinator`/`nop_stream_node`/`nop_stream_task_assignment` + `lease_expire_at` 索引（`JdbcClusterRegistry.java:30-32,372-382`）；`FlowControlPolicy` 仅剩 `BLOCKING_QUEUE`（CREDIT_BASED/ACK_WINDOW 已移除，与 G27 裁定一致）；`EPOCH_SCALE=1_000_000`；`maxRestartsPerRegion` 默认 3；`stream-control-rpc.beans.xml`/`stream-data-plane.beans.xml` 位于 `_vfs/nop/stream/beans/`；`TestLeaderElector`/`TestClusterRegistryConsistencyInvariant`/`TestSynchronizedCollectionInvariant`/`TestCheckpointIDCounterInvariant`/`TestFencingEpochUnification`/`TestRpcDistributedExecutorRemoteDeployE2E`/`TestJobCoordinatorRemoteDeploy`/`TestTaskDeploymentDescriptor`/`TestDataPlaneSysDaoBackendE2E`/`TestJobCoordinatorWithSysDaoLeaderElector` 等门禁/接线验证测试全部实存；`invariant-catalog.md`/`red-list.md`/`mjs-pins.json`/`gate-inventory.json` 实存于 `ai-dev/audits/nop-stream-invariants/`。
9. **00-vision 引用抽查**：`RedistributionMode`/`MemoryOperatorStateBackend`/`TestE2EOperatorStateRedistribution`/`IStreamTaskRpcService`/`IStreamCoordinatorRpcService`/`RemoteResultPartition`/`RemoteInputChannel`/`CheckpointParticipant`/`EdgeConfig`/`StreamBackendCapability`/`StreamRequirementValidator`/`MaxParallelismReshardMigration` 12 类全实存；`IJdbcTemplate + IDialect` 确为 `JdbcCheckpointStorage`/`JdbcClusterRegistry` 的数据访问方式；`IStreamExecutionDispatcher` 三方法最小面（G26）与接口实况一致。
10. **user-guide / connectors 契约抽查**：`IWindowOperatorFactory` ServiceLoader 接线、`WatermarkStrategyWithIdleness`/`TestInputGateWatermarkIdleness`/`TestWatermarkIdlenessCrossTaskE2E`、Trigger/Evictor 家族 13 类与单测锚点、`batch-loader`/`batch-consumer`/`debezium-cdc`/`jdbc-2pc` SPI 类型名（`TYPE_NAME` 常量）、REST `POST /jobs/{jobId}/stop`（`StreamOpsHttpServer.java:239`）、`StreamMaintenanceMain` conf-validate/dry-run、`ClusterPipelineFactory`/`RemotePipelineResolver`/`ReplayableCdcSourceFunction`/`JdbcTwoPhaseCommitSink`/`FileTwoPhaseCommitSink`、fraud-s1/s2 XML、`TestS2FileAggregationE2E`、`JobCoordinatorMain`/`TaskManagerMain` 位于 test scope 的「入口类现状」如实披露（:179）——全部与 live 一致。
11. **RuntimeTopology 退役检查**：全仓（nop-stream + ai-dev/design/nop-stream + docs-for-ai）仅 7 处提及，**全部为「已退役」注记**（README:5、00-vision:132、01-architecture:13/33/100/119），无任何将 RuntimeTopology 描述为现行组件的残留；docs-for-ai 零提及。2026-09-01 D-GAP 裁定已在文档面完整落地。
12. **failover-design.md 非漂移（误报排除）**：其「targeted failover NO-GO」结论虽被 Stage 44 推翻，但文档 §五 已带 5 个 successor 的「Implementation status（…已落地）」逐条注记及收尾声明「Stage 44 region-based failover 全部 5 个 successor plans 已交付」——历史裁定 + 现状注记的规范形态，不列为发现。
13. **`checkpoint-module-extraction.md` 引用（design/README.md:117）**：目标文件存在于 `ai-dev/analysis/`，非 broken。
14. **错误码命名契约**：`NopStreamErrors`（恰 707 行）100 码全部 `nop.err.stream.*` 前缀，无一偏离。
15. **repo 链接检查**：`node ai-dev/tools/check-doc-links.mjs --strict` 对 nop-stream 相关文档 0 BROKEN_LINK（仅有的 3 个 broken link 在 `ai-dev/plans/nop-bytecode/06-resource-leak-v1.md`，不属本审计范围）。

## 与既往轮次/裁定的一致性

- 2026-08-06 历史漂移（INDEX/module-groups 子模块清单落后 reactor 10 模块布局）：**module-groups.md 已修复**（10 模块正确口径）；**INDEX.md:244 残留**（本报告 DC-06）；**README/01-architecture 为同族未登记残例**（DC-04）。
- 2026-09-01 D-GAP RuntimeTopology 退役：**已完整落地**（通过项 11）。
- 设计文档「目标态/历史阶段/current-state 混杂」：本轮存量残例为 DC-08（state §1）与 DC-05（README「规划中」）；failover-design/checkpoint-design/01-architecture 的历史段落均已带注记，属规范形态。
- R3（2026-09-29）修复面（plan 366）的文档回写质量良好：owner doc 指标表已接线 `numLateRecordsDropped` 例外说明与 D2b/D2c 语义，`maxRestartsPerRegion`/SupervisionLoop 叙述与 live 一致。

## 修复建议汇总（按优先级）

1. **立即**（P1，四处入口型误导）：DC-01/DC-02 两处快速示例补 `setProcessingGuarantee(AT_LEAST_ONCE)` + 一句门控说明；DC-03 cep 示例改 live API；DC-04 README 模块表与 01-architecture §二树补齐 10 模块。
2. **短期**（P2）：DC-05 删「规划中」；DC-06 INDEX:244 改 10 模块口径或去重；DC-07 error-handling.md 登记nop-stream 例外并消解与 AGENTS.md 的语言规则冲突；DC-08 state §1 定位句更新；DC-09 §8.1 「composite fencing token」改 long epoch。
3. **随下次文档维护顺带**（P3）：DC-10 三处行号、DC-11 xdef 注释计数、DC-12 索引补 failover-design、DC-13 三处裸引用补路径。
