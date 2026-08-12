# 3 算子与部署契约修复（fan-out EOS + CEP timer 注册表对称 + merge fail-fast 回归测试 + beans.xml 语法）

> Plan Status: active
> Last Reviewed: 2026-08-13
> Draft Review: 2 轮独立子 agent 对抗性审查通过（round 1：1 Blocker（注释内模板验收机制）+ 3 Major（messageService 构造器注入 / BroadcastingRecordWriterOutput 死路 / Phase 4 构造现实）全部修复；round 2：1 Major（Closure Gates 与 Non-Goals 矛盾）+ 5 Minor 全部修复，verdict 可转 active）
> Source: `ai-dev/audits/2026-08-12-1217-open-audit-nop-stream-invariant-loop.md` P1-03 / P1-04；`ai-dev/audits/2026-08-12-1217-multi-audit-nop-stream-invariant-loop.md` P0-01 / P1-01
> Related: `2026-08-13-0132-1-...`（运行时服务接线，独立面）；`2026-08-13-0132-2-...`（checkpoint 恢复，独立面）
> Mission: nop-stream-invariant-loop

## Purpose

修复四个独立修复面的 P0/P1 缺陷：(1) fan-out 多输出任务只 close writer[0]，边 2..N 的 EOS 永不送达、有界作业下游永久阻塞（open-audit P1-03）；(2) CepOperator 事件时间 timer 注册表 `open()` 无条件重建覆盖 restoreState 结果、注册表永不触发/永不删除（open-audit P1-04）；(3) WindowOperator 非累加器 merge fail-fast 无回归测试保护（multi-audit P0-01，按 mission 定义 = absent test for changed behavior）；(4) 部署模板 beans.xml 使用不存在的 Nop IoC 语法（multi-audit P1-01）。

## Current Baseline

> 已核对 live repo（2026-08-13）。

- **P1-03（实测）**：`StreamTaskInvokable.java:67` 只保留 `private final RecordWriter<Object> outputWriter` 字段（= `fanOutWriters.get(0)`，:121/:148），**完整 `fanOutWriters` 列表在构造后被丢弃**；`invokeMiddle` finally（:480-482）与 `invokeSource`（:451-452）只 close `outputWriter`；`GraphExecutionPlan.java:407-427` 为每个出边建独立 `RecordWriter`/`ResultPartition`；`ResultPartition.close()`（:316-329）是唯一 EOS 来源；`InputGate.java:407-413` 未 finish channel 返回 null 循环重试 → 永久悬挂。`BroadcastingRecordWriterOutput.close()`（:687-703）遍历各 output 但 `RecordWriterOutput.close()` 是 no-op（:634-636 "RecordWriter lifecycle is managed by invoke()"）——**"经 BroadcastingRecordWriterOutput 统一关闭"是死路**，不能作为修复路径。`SupervisionLoop.java:690` 区域重启复用 `oldOutputWriter`（writer[0]）→ 重启后 fan-out 只喂边 1。
- **P1-04（实测）**：`CepOperator.java:307-314` `registerEventTimeTimer` 只 add 进 `registeredEventTimeTimers`（TreeSet），`deleteEventTimeTimer` 全仓零调用者；`:455` `onEventTime` 唯一入口是 watermark（整批推进，不经 timer）；`:278` `open()` 无条件 `registeredEventTimeTimers = new TreeSet<>()` 覆盖 restoreState（:441-443）恢复结果；`:422-424` snapshotState 全量拷入每个 checkpoint；`:530` onEventTime 移除队列桶但不删对应 timer。AR-9 只落地了存储侧持久化（`7fd60f0e9`），未接线消费侧。`TestCepCheckpointRestoreE2E:94-109` 已钉死 restore-before-open 顺序。
- **multi P0-01（实测）**：`WindowOperator.java:1468-1484` merge 冲突 fail-fast（`ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT` :1482 / `ERR_STREAM_INVALID_STATE` :1475）；`TestWindowOperatorCorrectness.java:489-533` 测试名 `testMergeTypeIncompatibilityThrowsException` 但只验证 happy path（3 元素、watermark、sum=60）；`MixedTypeWindowOperator`（:453-487，`useAccumulator` **final boolean** 开关）全库零实例化；错误码全测试零引用。**双 raw 场景可构造**（`useAccumulator=false` 时 merge 双 raw → :1482 throw）——审计中"公共 API 无法构造混合类型"的前提对双 raw 场景不成立；"accumulator+raw" 场景单实例的 final 开关无法构造，需 state 注入（`getKeyedStateBackend().getMapState(new MapStateDescriptor<>("window-contents", ...))`，`windowContentsState` 即按此名创建，WindowOperator.java:444-447）向源窗口 namespace 写 raw 值，或按窗口计数的子类。
- **multi P1-01（实测，含边界澄清）**：`stream-control-rpc.beans.xml:37-58` 与 `stream-data-plane.beans.xml:36-49` 的**部署模板全部位于 `<!-- -->` 注释块内**——XML 解析器不读注释，beans.xdef 校验对模板零生效（这是 B1 的验收机制前提）；模板语法问题（实测）：`ioc:configMethod="..."`（control-rpc :42/:52，beans.xdef 无此属性）、`ioc:bean="true"`（data-plane :48，全仓唯一使用点）、`<property name="messageService" ref="streamMessageService"/>`（data-plane :45——`RpcDistributedExecutor.java:84/:105-110` 为仅构造器类，messageService 是 `private final` 构造器参数，property 注入断链）、`<property name="serviceName" ...>`（control-rpc——`StreamControlRpcServer.java:62-66` 仅构造器）。`<constructor-arg>` 为 beans.xdef 支持写法（:127-130，仓库 13 个 beans.xml 在用）。
- **门禁基线（实测）**：JUnit 门禁 10 类 / 102 tests / 0 failures；mjs `all` exit 0（pin 0）；全量 2833 tests / 0 failures 基线。
- **真正剩余的 gap**：四处缺陷均 live；fan-out 有界 E2E、CEP timer 恢复顺序测试、merge fail-fast 触发测试、beans.xml 模板语法合规验证均缺失。

## Goals

- fan-out 任务关闭时遍历关闭**全部** `fanOutWriters`（新增字段保留完整列表），边 2..N 的 EOS 正常送达，有界 fan-out 作业正常终止（P1-03 关闭）。
- `CepOperator.open()` 仅在 `registeredEventTimeTimers == null` 时初始化（与 restoreState 对称），恢复的 timer 不再丢失；`deleteEventTimeTimer` 与触发语义明确化（注册表真实消费或明确标注记账结构 + 清理路径补齐）。（P1-04 关闭）
- merge fail-fast 双场景回归测试落地：**双 raw 场景（主路径，`useAccumulator=false`）→ `ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT`；accumulator+raw 场景（state 注入构造）→ `ERR_STREAM_INVALID_STATE`**。删死类 + 诚实改名仅限"双场景均证明不可构造"的保留路径（multi P0-01 关闭）。
- 两个 beans.xml 部署模板修正为 schema 支持的写法（`<constructor-arg>` / 嵌套 `<bean>`，删除 `ioc:configMethod` / `ioc:bean="true"` / property-注入-构造器 断链），验收 = 模板文本级语法合规验证 + 照抄演练（模板在注释内，无法直接容器加载）（multi P1-01 关闭）。
- 全量回归绿 + 既有门禁零命中。

## Non-Goals

- **不做跨 task side-output 线协议结构性重构**（`HG-01` 人工确认门未过，mission Cross-Cutting；P1-03 的 fan-out close 修复不涉及 side-output 线协议）。
- **不做运行时服务接线（P0-01/P1-02 → plan 1）与 checkpoint/恢复修复（→ plan 2）**。
- **不新增/不改既有不变式门禁语义**。
- **不做 P2 批次修复**（→ Follow-up Backlog）。
- **不把注释模板改为 live bean**（`streamMessageService` 双文件冲突 + 模板引用不存在的 `taskManager_node0` 等 ref——改 live 需要 ref 处理与碰撞规避，超出本 plan 契约修复面；模板维持注释形态，验收走文本级验证）。

## Scope

### In Scope

- fan-out 关闭遍历修复（新增完整列表字段）+ 有界 fan-out E2E（先红后绿）+ `SupervisionLoop:690` 重启路径同步。
- CEP timer 注册表 open/restore 对称 + 注册表语义裁定（触发/清理/记账三选一，恢复语义正确）+ 恢复顺序测试。
- merge fail-fast 回归测试（先红后绿）+ 死类/命名裁定执行（保留路径受约束）。
- beans.xml 部署模板语法修正（注释内模板文本级合规）+ 照抄演练验证。
- 文档收口：`docs-for-ai/04-reference/source-anchors.md`（STRM 锚点如有漂移则同步）+ `ai-dev/logs/`。

### Out Of Scope

- `HG-01` 线协议支持（人工确认门）。
- plan 1 / plan 2 的修复面。
- P2 批次（Follow-up Backlog）。
- 既有门禁内容改写。
- 模板转为 live bean（Non-Goals 已裁定维持注释形态）。

## Execution Plan

### Phase 1 - multi P1-01：beans.xml 部署模板语法修正（注释内模板文本级验收）

Status: planned
Targets: `nop-stream/nop-stream-runtime/src/main/resources/_vfs/nop/stream/beans/stream-control-rpc.beans.xml`（注释内模板 :37-58）；`stream-data-plane.beans.xml`（注释内模板 :36-49）

- Item Types: `Fix | Proof`
- [ ] **修复（Fix）**：两文件注释内模板改写为 schema 支持的写法——(a) 删除 `ioc:configMethod="..."` 占位（control-rpc :42/:52）；(b) `StreamControlRpcServer` 构造器参数改 `<constructor-arg index="..." ref="..."/>`（或等价，5 参构造全部可表达）；(c) `data-plane` 模板的 `<property name="messageService" ref="streamMessageService"/>`（:45）改 `<constructor-arg index="0" ref="streamMessageService"/>`（`RpcDistributedExecutor` 仅构造器）；(d) `ioc:bean="true"`（:48）改嵌套 `<property name="dataPlaneWireCodec"><bean class="io.nop.stream.runtime.transport.SysDaoWireCodec"/></property>`。
- [ ] **验收机制（Proof）**：模板位于 XML 注释内，容器无法直接加载 → 验收 = **模板文本级语法合规验证**：测试从文件读取注释块内模板文本，**包裹 `<beans>` 根 + `xmlns:ioc` 等命名空间声明**后作为 beans.xdef 文档解析（XML → XDSL 解析器直接解析模板文本），断言解析成功且无 schema 违规（`ioc:configMethod` / `ioc:bean` / property-注入-仅构造器类 三类语法不再出现——前两类是 schema 级可抓，第三类是语义级靠 grep/review 抓）；或等价的"模板文本提取 + 校验"测试。诚实声明：模板为部署示意图，容器级实例化验证不属于本 plan（Non-Goals）。
- [ ] **照抄演练（Proof）**：按修正后模板文本模拟照抄路径——验证模板片段（含 `<constructor-arg>` / 嵌套 `<bean>` 语法）与 beans.xdef schema 支持面一致（对照 `nop-kernel/nop-xdefs/.../beans.xdef:116-138`），grep 确认两文件注释内零 `ioc:configMethod` / `ioc:bean`。
- [ ] 回归：既有 ioc 相关测试全绿（`TestStreamModuleDiscovery` / `TestStreamControlRpcBootstrap` 等——它们加载文件本体，不受注释内模板影响，验证无回退）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 两文件全文件（含注释内模板文本）无 `ioc:configMethod` / `ioc:bean`（grep 零命中，`ioc:bean` 检索带词边界防误匹配 `ioc:bean-method`）
- [ ] 模板文本级合规验证测试绿（注释内模板提取 + beans.xdef 解析 + 无 schema 违规）
- [ ] 模板中 property-注入-仅构造器类 的断链已消除（`messageService` / `serviceName` 等均为 `<constructor-arg>` 表达）
- [ ] 既有 ioc 测试全绿（无回退）
- [ ] No owner-doc update required（若部署接线文档引用旧语法则同步）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - open-audit P1-03：fan-out 全 writer 关闭（EOS 完整送达）

Status: planned
Targets: `nop-stream/nop-stream-core/.../execution/StreamTaskInvokable.java`（新增完整 fanOutWriters 字段 :67 附近 + :451-452 / :480-482 finally 关闭路径）；`nop-stream/nop-stream-runtime/.../execution/SupervisionLoop.java`（:690 重启复用路径同步）

- Item Types: `Fix | Proof`
- [ ] **修复（Fix）**：`StreamTaskInvokable` **新增字段保留完整 `fanOutWriters` 列表**（当前构造器 :116-123/:141-152 丢弃列表、只留 `outputWriter` 字段）；`invokeSource`（:451-452，**保留 `sourceError == null` 成功路径条件**——基线已文档化重启语义 :433-440）与 `invokeMiddle` finally（:480-482）**遍历关闭全部 writers**（不再只 close `outputWriter`）。**唯一方案 = 全列表遍历**——"经 `BroadcastingRecordWriterOutput` 统一关闭"不可行（其 `close()` 调 `RecordWriterOutput.close()` 为 no-op :634-636，是死路）。
- [ ] **重启路径同步（Fix）**：`SupervisionLoop.java:690` 区域重启复用 `oldOutputWriter` 的路径改为复用完整列表（重启后的 fan-out 生产者必须喂全部边，不只边 1）。
- [ ] **端到端验证（Proof，Rule #22）**：新增有界 fan-out E2E——一分流多 sink 拓扑（≥2 出边）+ 有界源，作业从入口到全部下游 sink 正常终止（不悬挂）；修复前红（下游 read() 永久阻塞 / 作业不收敛）、修复后绿。
- [ ] **接线验证（Proof，Rule #23）**：边 2..N 的 EOS 确实由运行时 close 路径发出（断言 / 标志位：下游能读到边 2 的 finish）。
- [ ] **无静默跳过（Proof，Rule #24）**：close 路径不静默跳过任一 writer；`BroadcastingRecordWriterOutput.close()` 的 no-op 形态不成为"关闭已完成"的借口。
- [ ] 回归：既有 fan-out / 多输出相关测试全绿（`TestSideOutputChainingE2E` 等）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **端到端验证**：有界 fan-out（≥2 边）作业从入口到全部 sink 正常终止（E2E 绿，先红后绿证据在案）
- [ ] **接线验证**：边 2..N 的 EOS 送达有断言证据
- [ ] 关闭路径遍历全部 fanOutWriters（新增字段在案；grep / code review 确认无 writer[0]-only）
- [ ] **无静默跳过**：close 路径显式遍历，无 no-op 兜底
- [ ] `SupervisionLoop:690` 重启路径复用完整列表（code review 证据）
- [ ] 既有 fan-out / E2E 测试全绿
- [ ] No owner-doc update required（fan-out 关闭行为属内部执行面；如 `source-anchors.md` 有相关锚点漂移则同步）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - open-audit P1-04：CEP 事件时间 timer 注册表 open/restore 对称

Status: planned
Targets: `nop-stream/nop-stream-cep/.../operator/CepOperator.java`（:278 open / :307-314 注册 / :422-424 snapshot / :437-445 restore / :455 onEventTime）

- Item Types: `Fix | Decision | Proof`
- [ ] **修复（Fix）**：`open()` 仅在 `registeredEventTimeTimers == null` 时初始化（与 restoreState :441-443 对称），恢复的 timer 不再被无条件覆盖。
- [ ] **裁定（Decision）**：注册表语义二选一——(a) 接入真实触发机制（对齐 `WindowOperator` 的 `HeapInternalTimerService` 用法）；(b) 明确注册表为记账结构 + `deleteEventTimeTimer`/onEventTime 清理路径补齐（不静默增长；"fail-fast/显式标注"是 (b) 的子集：记账结构 + 清理路径 + 显式标注，不是独立第三方案）。裁定结果必须解决"永不触发 + 无界增长 + 恢复丢失"三个症状，且以恢复语义正确为底线（AR-9 存储侧已持久化，消费侧必须对称）。
- [ ] **测试（Proof，先红后绿）**：restore→open 顺序测试（对齐 `TestCepCheckpointRestoreE2E:94-109` 已钉死顺序）——断言恢复的 timer 在 open() 后仍存在（修复前红：被覆盖丢失）；快照-恢复往返不丢 timer。
- [ ] **无静默跳过（Proof，Rule #24）**：注册表相关路径不静默丢 timer / 不静默无界增长；若裁定为记账结构则 delete/清理路径有行为。
- [ ] 回归：既有 CEP 测试全绿（cep 模块全部测试，含 `TestCepOperatorStateRecovery` / `TestCepCheckpointRestoreE2E`）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] open()/restore 对称测试绿（恢复 timer 不被覆盖，先红后绿证据在案）
- [ ] 注册表语义裁定结果落地（触发 / 记账结构+清理 二选一，无静默丢 timer 与无界增长）
- [ ] snapshot→restore 往返不丢 timer（测试证据）
- [ ] 既有 CEP 测试全绿
- [ ] `docs-for-ai/04-reference/source-anchors.md` STRM-032/037 CEP 锚点如漂移已同步
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - multi P0-01：merge fail-fast 回归测试（先红后绿）

Status: planned
Targets: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/TestWindowOperatorCorrectness.java`（:453-533）；`WindowOperator.java`（:1468-1484 行为核对）

- Item Types: `Fix | Decision | Proof`
- [ ] **测试（Proof，先红后绿）**：**双 raw 场景（主路径）**——`useAccumulator=false` 时两 raw value 窗口 merge → 断言抛 `ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT`（该场景**可构造**，不走删死类路径）；**accumulator+raw 场景**——`MixedTypeWindowOperator` 的 `useAccumulator` 为 final，单实例无法混合 → 通过 state 注入构造（`getKeyedStateBackend().getMapState(new MapStateDescriptor<>("window-contents", String.class, Object.class))`——与算子自身创建的 descriptor（`WindowOperator.java:444-447`，`accClass=Object.class` 默认路径）指纹一致，`verifySchemaCompatibility` 不抛 mismatch——经 `setCurrentKey` + `setCurrentNamespace` 向源窗口 namespace 写入 raw 值，再处理触发元素 → merge 读源窗口内容即注入值 → 抛 `ERR_STREAM_INVALID_STATE`），或按窗口计数返回 accumulator 的子类。修复前红（当前无测试触发 throw——**红 = "删除 :1468/:1482 的 throw 后全部测试仍绿"的实证演示**）、落地后绿（fail-fast 行为被锁定）。
- [ ] **裁定（Decision）**：删死类 + 诚实改名仅限**双场景均证明不可构造**的保留路径——**`useAccumulator=false` 双 raw 场景明确可构造，该前提已不成立，保留路径在本 plan 不可用**（仅作记录，防后续误走）；若执行中意外发现双 raw 场景也无法构造（与审计/本 plan 基线不符），需回退至执行记录说明证据再走保留路径：删除 `MixedTypeWindowOperator` 死类 + 改名 `testSessionWindowMergeHappyPath` + 注释**点名错误码**（`ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT` / `ERR_STREAM_INVALID_STATE`）记录 fail-fast 覆盖缺口，确保 Exit Criterion 2（错误码引用）在注释路径仍可 grep。
- [ ] **验证（Proof）**：`ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT` 在测试中至少一次真实引用（grep 命中：主路径 = 断言代码引用；保留路径 = 注释点名），fail-fast 路径不再零覆盖。
- [ ] 回归：既有 `TestWindowOperatorCorrectness` 其余用例全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] merge fail-fast 双场景触发测试绿（双 raw → `ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT`；accumulator+raw → `ERR_STREAM_INVALID_STATE`）——保留路径本 plan 不可用（双 raw 可构造，前提不成立，仅作记录）
- [ ] 错误码在测试中真实引用（grep 命中）
- [ ] 先红后绿证据在案（红 = 删 throw 全测试仍绿演示，或当前零引用基线）
- [ ] 既有 `TestWindowOperatorCorrectness` 全绿
- [ ] No owner-doc update required（测试修复面，无 owner-doc 变更）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] P1-03 已修复：有界 fan-out E2E 绿（全部边 EOS 送达、作业终止；重启路径同步）
- [ ] P1-04 已修复：CEP timer 注册表 open/restore 对称 + 语义裁定落地（测试绿）
- [ ] multi P0-01 已修复：merge fail-fast 回归测试绿（双场景——保留路径本 plan 不可用，已裁定）
- [ ] multi P1-01 已修复：beans.xml 模板 schema 合规（文本级验证绿 + grep 零违规）
- [ ] 无被静默降级到 deferred / follow-up 的 in-scope live defect（P0/P1 均以 Fix 落地；multi P0-01 保留路径已裁定"本 plan 不可用"——双 raw 场景可构造，无降级）
- [ ] 接线完整性：fan-out 关闭路径运行时连通（E2E 证据）；beans.xml 模板合规以文本级验证为准（容器实例化明确 Non-Goal——Closure Gates 与 Non-Goals 一致，无不可满足项）
- [ ] 无静默跳过：无 writer[0]-only 静默跳过、无 timer 静默丢失/无界增长、无吞异常、无 no-op close 兜底
- [ ] 必要 focused verification 完成（先红后绿证据在案）
- [ ] 受影响 owner docs 已同步或明确 No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（含 Anti-Hollow 检查）
- [ ] `./mvnw compile` (`-pl nop-stream -am`)
- [ ] `./mvnw test -pl nop-stream -am -T 1C`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` exit 0（closure 时）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0（closure 时）
- [ ] `node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0（既有门禁零命中）
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（无——本 plan 无 in-scope 延期项。）

## Non-Blocking Follow-ups

- CEP timer 注册表若裁定为记账结构：未来对齐 `HeapInternalTimerService` 真实触发机制 = 优化候选（open-audit P1-04 建议 (a) 方案的后继）。
- 注释模板转 live bean（`streamMessageService` 冲突规避 + ref 补全）——部署需求出现时评估（本 plan Non-Goals 已裁定维持注释形态）。

## Closure

Status Note: （完成时填写）
Completed: （完成时填写）

Closure Audit Evidence:

- Reviewer / Agent: （closure audit 时填写）
- Evidence: （closure audit 时填写）

Follow-up:

- （closure audit 时填写）
