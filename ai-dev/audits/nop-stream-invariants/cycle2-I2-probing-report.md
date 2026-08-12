# Cycle 2 / I2 聚焦对抗探查报告（门禁盲区 a-d + 非族候选评估）

> 状态: active（Cycle 2 / I2 Phase 3 产出，2026-08-12 实测）
> 日期: 2026-08-12
> 来源: plan `2026-08-12-1217-09-nop-stream-invariants-cycle2-I2-invariant-driven-audit.md` Phase 3
> 范围: nop-stream 输出契约族（不变式 #6）——4 个 main `Output` 实现类 + 6 发射点 + 登记盲区清单 + 3 个非族候选
> 方法: `open-ended-adversarial-review-prompt.md` 聚焦对抗探查（非全仓漫游），每发现含 位置 / 场景 / 影响 / 族标注
> 去重: 已对照 `cycle2-I1-input.md`（102 tests / 2 pin 基线）、`output-contract-registry.json`（4 实现类 / 6 发射点）、
> `mjs-pins.json`（2 过渡 pin）、Cycle 1 的 `I2-probing-report.md`（不复用不追加，本文件为新形态产物）

## 0. 探查范围声明（覆盖类清单）

聚焦于注册表目标集 + 盲区清单，未做全仓漫游。实际逐项检查的类/文件：

- **实现类（4）**：`ChainingOutput.java`（共享 consumer map 注册 / 转发 / fail-fast 语义）、
  `TimestampedCollector.java`（纯透传 + 透传目标敏感）、`StreamTaskInvokable.java:611-718`
  （RWO/BRWO 全部 Output 方法体，含 emitWatermarkStatus / emitLatencyMarker / emitBarrier / close）。
- **发射点（6）**：`ProcessOperator.java:111/:134`（ContextImpl/OnTimerContextImpl.output）、
  `WindowOperator.java:1030/:1860`（sideOutput helper / ContextImpl.output）、
  `CepOperator.java:483/:777`（late-data / ContextFunctionImpl.output）。
- **接线/注册点**：`StreamTaskInvokable.java`（wireOperators :162-249、wireTailToRecordWriter :348-354、
  registerSideOutputConsumer :310-313、sideOutputConsumers :99）、`GraphExecutionPlan.java:453-463`
  （fanOutWriters 分支）、`OperatorChain.java`（processElement 广播核查 = 非族候选 R16-AR-14）。
- **门禁工具**：`ai-dev/tools/check-nop-stream-invariants.mjs`（parseTypeStructure :622-652 /
  finalizeTypeFrames :658-687 / findCollectOutputTagMethod :694-750 / classifyCollectBody :752-764 /
  analyzeOutputContractSource :774-811 ——嵌套类解析与 V1/V3/V4 判定路径逐行核读）。
- **透传链中间形态核查**：全 main 源码 `new TimestampedCollector(` 调用点（3 处）+ `extends ChainingOutput/
  TimestampedCollector/RecordWriterOutput` 子类（0 个）+ 匿名/record `implements Output`（0 个）。
- **测试面**：`TestSideOutputChainingE2E`（3 用例）、`TestOutputContractInvariant`（10 用例）、
  `TestProcessOperator`、CEP operator 测试集（OutputTag 使用核查）。

## 1. 探查发现

### C2-PR-1. 全部 6 发射点的 side-output 均直连算子 `output` 字段，不经 TimestampedCollector（透传链中间形态实测）

- **位置**：`ProcessOperator.java:111/:134`、`WindowOperator.java:1860`、`CepOperator.java:777`
  （ctx.output 实现体）——均直接调用 `output.collect(outputTag, record)`（`output` = AbstractStreamOperator 字段）；
  `CepOperator.java:483` / `WindowOperator.java:1030` 同型。
- **场景**：盲区 a「透传目标敏感分类」的中间链形态核查——`TimestampedCollector`（:97-99 纯透传）是否可能
  成为 side-output 的实际传输中间形态。核查结论：**不是**。`Collector<T>` 接口（`io.nop.stream.core.util.Collector.java:25`）
  **不继承** `Output`——用户函数持有的 `collector` 无法调用 `collect(OutputTag, ...)`；全部 6 个发射点来自
  `ContextImpl.output()` / `sideOutput()` helper，直接落在算子 `output` 字段上。
- **影响**：JUnit `testTimestampedCollectorWrappingRecordWriterOutputEqualsCrossTaskDrop`
  （`TestOutputContractInvariant.java:285-296`）断言的「包装 RWO = 等价跨 task 丢弃」场景为**合成场景**——
  该断言作为分类语义声明有效（forward 类被包装对象语义为准），但该包装链当前无生产可达的 side-output 路径
  （生产路径 = ctx.output → 算子 output → ChainingOutput 或 RWO/BRWO，绕过 TimestampedCollector）。
- **族标注**：已知族（不变式 #6 输出契约族）门禁表达完备性观察，非缺陷、非新族。
- **结论**：分类语义在中间链上仍成立（forward 即 forward；丢弃语义由被包装对象决定）；注册表 / JUnit 对
  该语义的表达完整且与实际生产路径无冲突（生产跨 task 丢弃面 = C2-RL-1/2 已 pin）。

### C2-PR-2. 扫描器嵌套类解析的静默跳过形态（匿名类 / record / raw OutputTag 声明）——理论盲区，当前零实例

- **位置**：`check-nop-stream-invariants.mjs` parseTypeStructure :622-652（仅框定 `class|interface|enum` 关键字）、
  analyzeOutputContractSource :795-799（V4 声明 regex 要求 `OutputTag<...>` 泛型形态）。
- **场景**：盲区 b「嵌套类方法体解析鲁棒性」对抗核查——显式 fail 路径全部实测存在（:673 未闭合 brace、
  :715 无方法体、:727 方法体 brace 不配对、:763 未识别方法体形态、:783 Output 实现类无 collect(OutputTag) 方法，
  self-test :1148-1160 覆盖未识别形态）。**静默跳过形态**（不显式 fail，当前 0 实例）：
  a) **匿名类** `new Output<X>(){...}` implements Output——parseTypeStructure 不建 frame → V1 类级枚举静默漏检
  （不红）；b) **record** `record Foo(...) implements Output`——`record` 关键字不在 {class,interface,enum} 集合 → 同漏检；
  c) **raw 类型** `OutputTag tag`（无泛型，合法 Java）→ V4 声明 regex 不匹配 → 其发射调用静默漏检。
  false-positive 方向安全：`implements X<Output<Y>>`（Output 作其他接口泛型参数）→ `/\bimplements\b[^{]*\bOutput\b/`
  会误判 implementsOutput → 红（fail-loud，安全方向）。
- **影响**：若未来 main 代码引入匿名/record Output 实现或 raw OutputTag 声明，门禁将静默放过（非 fail）——
  属门禁表达缺口，非当前 live defect（grep 实测 0 实例：main 无 `new Output<`、无 record implements Output、
  无 raw OutputTag 声明）。
- **族标注**：已知族（不变式 #6 输出契约族）门禁表达扩展候选——供 I6 按 Loop Rule 评估 Cycle 3 / I1
  （scan-output-contract 增加匿名类 / record 形态显式 fail 或枚举，及 raw OutputTag 声明覆盖）。

### C2-PR-3. 同一 OutputTag 重复注册消费者 = 静默覆盖（last-wins）

- **位置**：`ChainingOutput.java:67-69`（`sideOutputConsumers.put(outputTag, consumer)`）、
  `StreamTaskInvokable.java:310-313`（同型 put）。
- **场景**：盲区 c「注册/接线时序」多消费者注册核查——同一 OutputTag 第二次注册时，前一消费者被静默替换
  （Map put 语义），无 fail-fast / 无合并 / 无日志。多个消费者订阅同一 side-output 是 Flink 生态的合理需求
  （多 sink 场景）。
- **影响**：重复注册场景下行为可预测但易误配（第二个注册者静默遮蔽第一个，数据只到一处）；不违反不变式 #6
  （仍转发到"一个"注册消费者，无静默丢弃）；生产当前无重复注册调用面（唯一注册入口 = StreamTaskInvokable /
  ChainingOutput 公开方法，无多消费者接线代码）。观察级，非 defect。
- **族标注**：已知族（不变式 #6 输出契约族）API 语义观察项——优化候选（重复注册 fail-fast 或广播语义），
  供 I3 裁决处置（P3 级候选）。

### C2-PR-4. E2E 覆盖缺口确认：6 发射点仅 1 个有 E2E（盲区 d 评估结论）

- **位置**：`TestSideOutputChainingE2E`（`nop-stream-runtime/.../integration/`，3 用例）仅覆盖
  `WindowOperator.java:1030`（late-data sideOutput → ChainingOutput → 消费者 / fail-fast / 接线）。
- **场景**：盲区 d 全量核查——`ProcessOperator.java:111/:134`（ProcessFunction / OnTimer ctx.output）、
  `WindowOperator.java:1860`（ProcessWindowFunction ctx.output）、`CepOperator.java:483/:777`
  （late-data / PatternProcessFunction ctx.output）共 5 个发射点**零 E2E 覆盖**；单元层亦无 OutputTag 发射断言
  （`TestProcessOperator` 仅 main-stream `out.collect`；CEP operator 测试集仅 `TestCepSkipStrategyE2E` import
  OutputTag 未发射）。
- **影响**：未覆盖发射点的端到端转发/无消费者 fail-fast 行为无回归测试兜底（WindowOperator 路径覆盖的
  ChainingOutput 语义为同一代码面，风险 = 算子侧 ctx.output 接线形态差异，如 timestamp 处理）。缺口为优化级
  候选（I1 Non-Blocking Follow-ups 已登记），非 defect——门禁 V4 注册表 + ChainingOutput 行为断言在案。
- **族标注**：已知族（不变式 #6）覆盖缺口评估项——不升格 red list（与 C2-RL-3 措辞过 claim 联动，I3 参考）。

### C2-PR-5. RWO/BRWO 的 emitWatermarkStatus / emitLatencyMarker 跨 task 空体——不变式 #6 陈述扩展候选（非本族范围）

- **位置**：`StreamTaskInvokable.java:640-642`（RWO emitWatermarkStatus 空体，注释「Not forwarded across task
  boundaries」）、:650-652（RWO emitLatencyMarker 空体）、:701-702（BRWO emitWatermarkStatus 空体，无注释）、
  :709-710（BRWO emitLatencyMarker 空体，无注释）。对照：RWO/BRWO `emitWatermark` **已转发**（writer.emitWatermark /
  outputs 广播）；`emitBarrier` 已转发；`close` 有实现。
- **场景**：非族候选评估「其他输出路径」——WatermarkStatus（空闲/活跃状态传播）与 LatencyMarker（延迟遥测）
  跨 task 静默丢弃，与 side-output（collect(OutputTag)）为不同语义维度（控制面遥测 vs 用户数据）。
- **影响**：跨 task 部署下下游任务收不到上游 IDLE/ACTIVE 状态（空闲检测 / watermark 合并降级）与延迟指标
  （可观测性降级）；**非用户数据丢失**。RWO 两处有注释（文档化选择），BRWO 两处为空体无注释（Rule #24
  注释一致性欠佳，属 code-style 级观察）。修复需 RecordWriter 线协议扩展（同 `HG-01` 处置门——人工确认）。
- **族标注**：**不变式 #6 陈述扩展候选**（同根因族：跨 task Output 方法静默丢弃；非独立新族）——不变式
  陈述扩展候选：「跨 task 部署下 Output 控制面方法（emitWatermarkStatus / emitLatencyMarker）不得静默丢弃，
  或显式 fail-fast / 文档化」；触发证据 `文件:行` = `StreamTaskInvokable.java:640-642/:650-652/:701-702/:709-710`。
  供 I6 按 Loop Rule 评估是否派生 Cycle 3 / I1（扩展不变式 #6 或新立控制面不变式）。

## 2. 盲区 a-d 逐项处置声明

- **a) 透传目标敏感分类**：
  - *中间链形态*：TimestampedCollector 是唯一 Output 包装类（`new TimestampedCollector(` 生产调用点 3 处：
    ProcessOperator:39 / WindowOperator:397 / CepOperator:338，均包装算子 output）；无更深层 Output 包装链
    （main 无 Output 子类 / 无匿名 Output 实现）——**检查后无问题**。
  - *生产可达性*：side-output 发射全部直连算子 `output`（C2-PR-1），TimestampedCollector 包装 RWO 场景为
    合成断言（分类语义有效）；跨 task 丢弃面 = C2-RL-1/2（已 pin）——**检查后无问题**。
  - *注册表表达*：TimestampedCollector disposition + subSemantics 记录透传目标敏感语义；JUnit 双场景断言
    （包装 no-op / 包装 RWO）在案——**表达完整**。
- **b) 嵌套类方法体解析**：显式 fail 五路径全部实现 + self-test 覆盖（未识别形态 hard error 实测）；
  静默跳过三形态（匿名类 / record / raw OutputTag）当前 0 实例，记录为门禁表达扩展候选（C2-PR-2）；
  RWO/BRWO 深两层嵌套 + 泛型形态实测解析正确（V1/V2/V3 绿）——**结论：显式 fail 面完备；静默跳过面零实例
  + 已登记候选**。
- **c) 注册/接线时序**：
  - *wiring 前后注册*：共享 map（StreamTaskInvokable:99）在 wireOperators 时传给每个 ChainingOutput
    （:182/:220），注册写入共享 map → 前后时序均可达（JUnit `testInvokableRegistrationReachesWiredChainingOutput`
    实测 wiring 后注册可达）——**检查后无问题**。
  - *多消费者*：重复注册 last-wins 静默覆盖（C2-PR-3，观察项，非 defect）。
  - *broadcast vs record-writer 尾接线*：单 fanOut → RWO（:239）/ 多 fanOut → BRWO（:242-245）；单 writer 路径
    wireTailToRecordWriter → RWO（:352）；两者 collect(OutputTag) 均空体 no-op = C2-RL-1/2 覆盖——**检查后无问题**。
  - *fanOutWriters 多 vertex*：GraphExecutionPlan :453-463 分支与 wireOperators(List) 路径 Phase 2 已 live 复核
    ——**检查后无问题**。
  - *并发*：sideOutputConsumers 为普通 HashMap，注册仅发生在 setup 阶段（单线程），处理阶段只读——**检查后无问题**。
- **d) E2E 覆盖缺口**：实测 6 发射点仅 1 覆盖（WindowOperator:1030 late-data），5 发射点零覆盖
  （C2-PR-4）——缺口确认并登记；不升格 red list（非 defect），与 C2-RL-3（注册表措辞过 claim）联动供 I3 参考。

## 3. 非族候选升格评估

| 候选 | live 复核（2026-08-12） | 裁定 | 理由 |
|---|---|---|---|
| R16-AR-14（OperatorChain.processElement 广播） | `OperatorChain.java` 无 processElement 广播方法（仅注释引用 Input#processElement） | **不升格** | 已随类重构消失（Cycle 1 / I2 判定复核无变化） |
| R16-AR-19/20（BatchConsumerSinkFunction buffer 增长/序列化） | `BatchConsumerSinkFunction.java` 文档化 unsynchronized by design（线程契约注释）+ flush 失败 fail-fast（`ERR_STREAM_STATE_ERROR` 异常上抛）+ batchSize 阈值 flush（无无界增长面） | **不升格** | 与 Cycle 1 / I2 裁定一致，live 无变化 |
| emitWatermarkStatus / emitLatencyMarker 跨 task 空体 | RWO :640-642/:650-652（注释文档化）/ BRWO :701-702/:709-710（空体无注释） | **不升格为独立新族**；登记为**不变式 #6 陈述扩展候选**（C2-PR-5） | 同根因（跨 task Output 方法无线协议支持）、同处置门（`HG-01`）；影响 = 控制面遥测/空闲检测降级非数据丢失；修复属线协议结构变更需人工确认——派生与否由 I6 按 Loop Rule 裁定 |

**综合结论**：无新独立族派生候选（供 I6 评估的不变式扩展候选 = 2：C2-PR-2 扫描器形态覆盖 + C2-PR-5
控制面方法族）；无 in-scope confirmed live defect 被静默遗漏（全部红项 / 发现已在 red-list.md 或本报告）。

## 4. 已知族新实例与历史 finding 对应

- C2-PR-1（透传链中间形态）：已知族（不变式 #6）门禁表达完备性观察——对应 JUnit 断言
  `testTimestampedCollectorWrappingRecordWriterOutputEqualsCrossTaskDrop`（合成场景，分类语义有效）。
- C2-PR-2（扫描器静默跳过形态）：已知族（不变式 #6）门禁表达扩展候选——对应
  `scan-output-contract` V1/V4 判定路径（工具自身）。
- C2-PR-3（重复注册覆盖）：已知族（不变式 #6）API 语义观察——对应 ChainingOutput/StreamTaskInvokable
  注册 API（I1 产物）。
- C2-PR-4（E2E 缺口）：已知族（不变式 #6）覆盖缺口——对应 I1 Non-Blocking Follow-ups 登记项 +
  注册表 disposition 措辞过 claim（C2-RL-3）。
- C2-PR-5（控制面方法）：不变式 #6 陈述扩展候选——对应跨 task 实例族（C2-RL-1/2 同根因，`HG-01` 门）。
- 历史 finding 对照：无新发现对应未登记历史 finding（R15-AR-4 / R16-AR-9/18 等已在 Cycle 1 闭环）。

## 5. 盲区自评与移交

- **盲区自评**：本次探查为聚焦式（注册表目标集 + 登记盲区清单），未做全仓漫游；可能遗漏——connector /
  flow 模块内非 Output 形状的 side-output 类机制、极端并发下的注册/发射交错（需专门 harness）、扫描器对
  未来新 Java 形态（sealed classes 已隐含兼容——`sealed class Foo implements Output` 的 `class` 关键字可框定，
  未逐一实测 record 正例）。
- **移交**：C2-PR-1..5 全部带族标注随本报告存档；red-list 增补项 = C2-RL-3（措辞过 claim，Phase 1 已登记）
  在本报告盲区 d 评估在案；I3 裁决输入就绪（red-list.md §1 主体 + §3 本节结论 + 本报告）。
