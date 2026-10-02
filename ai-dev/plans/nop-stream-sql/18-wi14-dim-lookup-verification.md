# 18 WI14 静态维表 lookup join 验证与适配

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI14 行、§三 #8 集成条款）、`ai-dev/design/nop-stream/join-operator.md`（复用义务）
> Related: `ai-dev/plans/nop-stream-sql/13-wi6-union-multi-input.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）
>
> 审查修订记录：经独立子 agent 对抗性审查一轮修订——B1 DSL 级 E2E 从 flow 移到 runtime（runLocal 无 backend provisioning，KeyedProcessFunction 必抛异常；runtime 有 checkpoint engine 自动 provision——这正是 roadmap「DSL→运行时路径证据」本意）；M1 custom 测试钉死 runtime 单一位置；M2 判别性机制落纸（stub 版本代际 + 加载计数器：restore 后换代，已缓存 key 得旧值证 restore、新 key 得新值且计数+1 证重载）；M3 applyPendingRestoreState 实存 protected（AbstractStreamOperator.java:260）直接继承调用，行号修正 :59-62/:72-76；m1 lookup key 取自记录字段（Context.getCurrentKey 未接线）；m2 snapshot 用 processBarrier 形态；m3 ITableLookup 落 io.nop.stream.core.connector.lookup；m4 runtime test classpath 有 nop-dao，增 IJdbcTemplate→ITableLookup 适配用例（类型证明），IBatchLoader 属 nop-batch-core 措辞修正。

## Purpose

验证静态维表 lookup join 的两条执行路径：process 路径（KeyedProcessFunction + keyed state 缓存维表条目）的端到端 checkpoint/restore 证据，与 custom 路径（operator 在 open 内自建 keyed backend）的有用例证明。同时核对维表访问面遵守 §三 #8（走 IJdbcTemplate/IBatchLoader 形态的接口，不自建数据源）。

## Current Baseline

- process 路径：`ProcessOperator.open()`（nop-stream-core）在 keyed stream 场景自建 keyedStateBackend 并注入 StreamingRuntimeContext（:55-75 先例，WI10 已实测）；operator 级 keyed-state checkpoint/restore 端到端证据存在（`TestE2EWindowOperatorWithCheckpoint`，nop-stream-runtime，LocalFileCheckpointStorage + CheckpointCoordinator + MemoryStateBackend 形态）。
- custom 路径：`<custom>` 元素解析 OneInputStreamOperator bean（`AdvancedTransforms.buildCustom`），**无自动注入**——但可照 `ProcessOperator.open()` 先例在 operator 的 `open()` 内自建（roadmap §3.1 明示「`<custom>` 亦非不可行」）。现状无任何 custom 路径自建 backend 的用例。
- 维表访问面：§三 #8 要求 lookup 走 `IJdbcTemplate` 或 `IBatchLoader` 而非自建数据源。nop-stream-core 不依赖 nop-dao（IJdbcTemplate 在 nop-dao）——维表访问在流侧的正确形态是**接口抽象 + 宿主注入实现**（平台集成由应用层桥接）；本 WI 验证该形态的接线可行，不引入 core 对 dao 的依赖。
- checkpoint 测试基建：runtime 模块的 CheckpointCoordinator + TaskLocation + LocalFileCheckpointStorage 形态（TestE2EWindowOperatorWithCheckpoint 全套先例）。
- DSL 测试基建：flow 侧 `.stream.xml` 全链（WI6 的 TestUnionPipelineE2E 先例）。

## Goals

- **process 路径证据**（runtime 测试）：KeyedProcessFunction 维表 lookup——维表条目缓存于 keyed state，lookup miss 时经维表访问接口加载并写缓存；判别性机制：stub 返回版本代际值 + 加载计数器，restore 后换代（v1→v2）——已缓存 key 断言得 v1（命中 restore 缓存非重载）、新 key 断言得 v2 且计数 +1（miss 重载）。快照经 `operator.processBarrier(barrier)` 形态触发（先例同款）。
- **维表访问接口**（core，最小新增）：`ITableLookup` 单方法接口（按 key 同步取维表记录），落 `io.nop.stream.core.connector.lookup` 包——§三 #8 的流侧桥接面（应用实现包 IJdbcTemplate（nop-dao）或 IBatchLoader（nop-batch-core））；core 不增 dao 依赖；**runtime test classpath 已有 nop-dao——增一个 IJdbcTemplate→ITableLookup 适配用例把桥接从形态证明升级为类型证明**。
- **custom 路径证据**（runtime 测试，单一位置）：自定义 OneInputStreamOperator 在 `open()` 内按 ProcessOperator :59-62 先例 `stateBackend.createKeyedStateBackend(Object.class)` 自建 backend + 调用**继承的 protected `applyPendingRestoreState()`**（AbstractStreamOperator.java:260，实存无需手写）+ `setKeyedStateStore(backend)`（StreamingRuntimeContext :69 public）——KeyedStateStore 可用并参与 snapshot/restore。
- **DSL 级接线**（runtime E2E）：`.stream.xml` 的 `<keyBy>+<process bean=lookupFn>` 拓扑经 `StreamExecutionEnvironment.execute()` 跑通——runtime classpath 有 checkpoint engine（ServiceLoader 注册 CheckpointExecutorFactoryImpl），execute 自动 provision backend（这正是 roadmap §3.1「DSL→运行时路径证据」的本意；flow 模块 runLocal 无 provisioning 不可行）。同 JVM 首 execute 限制照 WI6 处置。
- 结论落档：roadmap WI14 行翻转时括注「两路径证据齐备；维表访问面=ITableLookup 桥接（应用层接 IJdbcTemplate/IBatchLoader），不自建数据源」。

## Non-Goals

- 不实现完整维表 join 算子（双流 join 归 WI13）；不接真实数据库（测试 stub）；不新增 core→dao 依赖；不改变 ProcessOperator/custom 的产品代码行为（除非 audit 推翻「无需改动」）。

## Scope

### In Scope

- `nop-stream/nop-stream-core`：ITableLookup 接口（io.nop.stream.core.connector.lookup）+ 语义单测
- `nop-stream/nop-stream-runtime`：process 路径 checkpoint/restore 测试、custom 路径自建 backend 测试、DSL 级 E2E、IJdbcTemplate 适配用例
- roadmap WI14 行（Phase 2 翻转）；当日日志

### Out Of Scope

- WI13 双流 join；真实数据源；connector 体系。

## Execution Plan

### Phase 1 - 证据测试

Status: completed
Targets: `nop-stream/nop-stream-core`、`nop-stream/nop-stream-runtime`、`nop-stream/nop-stream-flow`

- Item Types: `Proof`

- [x] core：`ITableLookup` 接口（io.nop.stream.core.connector.lookup；javadoc 记录 §三 #8 桥接形态——应用实现包 IJdbcTemplate（nop-dao）或 IBatchLoader（nop-batch-core））；KeyedProcessFunction 维表缓存语义单测（miss 加载/命中跳过/接口委托；lookup key 取自记录字段——Context.getCurrentKey 未接线）
- [x] runtime：`TestE2EDimLookupWithCheckpoint`——process 路径全链：加载→缓存→processBarrier 快照→新 backend restore→判别性断言（restore 后 stub 换代：已缓存 key 得旧值、新 key 得新值且计数 +1）
- [x] runtime：`TestCustomOperatorSelfProvisionedBackend`——custom 形态 operator open() 内自建 keyed backend（:59-62 先例 + 继承 applyPendingRestoreState），断言 RuntimeContext keyed store 可用且参与 snapshot/restore
- [x] runtime：`TestDimLookupPipelineE2E`——`.stream.xml` `<keyBy>+<process>` 拓扑经 execute() 端到端输出断言（runtime checkpoint engine 自动 provision；独立 init/destroy）
- [x] runtime：IJdbcTemplate→ITableLookup 适配用例（test-scope 类型证明；执行期实测：IJdbcTemplate 非 Serializable——适配器以 transient 持有 + 重新解析契约，序列化往返测试钉住）
- [x] ai-dev/logs/ 当日条目更新
- [x] **执行期发现与修正**：DSL E2E 首跑失败（Keyed state is only available on a keyed stream）——根因是 `.stream.xml` 未声明 `<checkpoint>`，execute 走 runLocal 无 backend provisioning（wiring 探针实证 DSL 声明 checkpoint 后 ProcessOperator stateBackend 被 provisioning）。修正=DSL 声明 `<checkpoint enabled="true" interval="50" processingGuarantee="AT_LEAST_ONCE"/>`，恰好坐实 roadmap「DSL→运行时路径证据」的义务：DSL 声明 checkpoint → checkpoint engine 接管 → wiring provision → ProcessOperator.open() 自建 keyed backend 全链打通

Exit Criteria:

- [x] process 路径：checkpoint/restore 端到端证据落地（判别性断言：restore 缓存命中 vs 重新加载可区分）
- [x] custom 路径：自建 keyed backend 有用例（RuntimeContext 注入断言 + snapshot/restore 参与），不以「无自动注入」结案
- [x] 维表访问面为接口桥接形态且经类型证明（IJdbcTemplate 适配用例），core 零 dao 依赖
- [x] `./mvnw test -pl nop-stream/nop-stream-core -Dtest=<lookup 语义单测>` 绿；runtime 四个新测试类绿；`./mvnw test -pl nop-stream/nop-stream-runtime` 全量零退化
- [x] ai-dev/logs/ 当日条目已更新

### Phase 2 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（不同 task_id）：判定 PASS——判别机制推演成立、自建序列与先例逐行一致、DSL checkpoint 声明载荷开关核实（gate 三段 live 代码）、§三 #8 合规（pom 零 dao diff）、门禁全过；4 项 Minor 非阻塞（M-1 计数已更正、M-2 恒真探针已删、M-3/M-4 记账）；证据落 ai-dev/audits/nop-stream-sql/wi14-closure-audit.md
- [x] audit 通过后 roadmap WI14 `todo` → `done`（括注单层无嵌套）；`parseRoadmapMarkdown` 复核 31 工作项 + 7 里程碑、18 done、无静默丢弃
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI14 = done + 解析器 31 + 7 复核通过
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

## Closure Gates

- [x] process 路径维表 join 有端到端 checkpoint 与 restore 证据（判别性断言：代际戳+计数器使 restore 缓存命中与重新加载可区分——audit 推演核验）
- [x] custom 路径按 ProcessOperator 先例在 open 内自建 keyed backend 并有用例（非「无自动注入」结案——audit 逐行一致性核验）
- [x] 维表访问面遵守 §三 #8（接口桥接形态 + IJdbcTemplate 适配类型证明，core 零 dao 依赖——pom diff 核对）
- [x] 既有测试零退化（core 1665 / runtime 1190 全量绿）
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id，判定 PASS）
- [x] `./mvnw test -pl nop-stream/nop-stream-core` 绿；`./mvnw test -pl nop-stream/nop-stream-runtime` 绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/18-wi14-dim-lookup-verification.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### 真实数据库维表（IJdbcTemplate 实桥接）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: WI14 是验证类（Proof）——接口桥接形态已证明可行；真实数据源属应用层集成，connector 体系归 productization roadmap
- Successor Required: `no`
- Successor Path: 应用层按 ITableLookup 契约包 IJdbcTemplate（docs-for-ai 用户文档归 WI18 记账）

## Closure

Status Note: 静态维表 lookup join 两条路径证据齐备——process 路径判别性 checkpoint/restore 证据（代际戳+计数器）、custom 路径自建 backend 证据（先例逐行一致）、DSL 全链证据（发现并闭合 checkpoint 声明载荷开关缺口）、§三 #8 类型证明（IJdbcTemplate 适配 + transient 契约）。独立 closure audit 判定 PASS（4 项 Minor，M-1/M-2 已修）。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi14-closure-audit.md（判定 PASS）
- Evidence:
  - 判别机制推演成立（restore 失效/直接重载/未换代三种反事实各有断言失败路径）
  - custom 自建序列与 ProcessOperator.open() 先例逐行一致（含行号吻合）
  - DSL gate 三段 live 代码核实（checkpointingDeclared 为真正判别子）
  - 实跑 core 1665 / runtime 1190 / 隔离新测试类全绿；门禁（doc-links/invariants/scan-hollow/roadmap 31+7）全过
  - git 纪律：仅日志一文件 tracked 改动，探针零残留
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/18-wi14-dim-lookup-verification.md --strict` 退出码 0

Follow-up:

- lookup null 哨兵的 NULL 列值语义 → WI18 用户文档记账（audit M-4）
