# 2436 WI12 全量收口与门禁固化——nop-stream 结构重构 roadmap 收口

> Plan Status: completed
> Last Reviewed: 2026-10-13
> Source: `ai-dev/backlog/nop-stream-structure-refactor-roadmap.md` §WI12 / §Phase E；`ai-dev/design/nop-stream/01-architecture-baseline.md`；`ai-dev/design/nop-stream/checkpoint-engine-convergence.md` §四.5
> Related: plan 2410/2411/2412/2413/2415/2416/2417/2420/2423/2426/2432（WI1—WI11）；plan 2435（CC-06/CC-17 波次）；plan 2430（test-fork 污染收口，completed）

## Purpose

执行 roadmap 的最后一个工作项 WI12：把 WI1—WI11 已消除的结构缺陷固化为 CI 可执行门禁（兑现 M1「CI 门禁固化于 WI12」承诺）、重生成 invariant registry 的 24 条先存漂移、完成文档终稿同步与 runtime.source 裁定落档、跑通全量回归与 gated 多 JVM 场景，最终将 roadmap WI12 与 M3 置 done 并宣告 roadmap 收口。

## Current Baseline

（以下事实均于 2026-10-11 在工作树实测核对）

- Roadmap 状态：WI1—WI11 全部 `done`（plans 2410/2411/2412/2413/2415/2416/2417/2420/2423/2426/2432 + plan 2435 CC 波次）；WI12 `todo`；M1/M2 `done`，M3 `todo`（其解锁条件 WI9+WI10+WI11 已全 done，翻转属本 plan closure 交付，需差分与 gated 证据支持）。
- core→runtime 反向依赖：`grep -rn "io.nop.stream.runtime" nop-stream/nop-stream-core/src/` 当前命中 **0**（main+test，含注释；WI3 注释归零 + WI7 反射归零后的不变式钉现状）。
- runtime 四包（transport/rpc/taskmanager/coordinator）import 方向：transport→rpc(1 文件)、taskmanager→transport(1)、taskmanager→rpc(2)、coordinator→rpc(2)，无反向边，无环（WI6 后现状）。
- runtime pom connector test 依赖（WI5 方案 b）：6 个 test 依赖（connector-debezium/-batch/-kafka、connector、connector-jdbc、rocksdb），pom 注释「AR-2 test-scope exception register (WI5 plan 2415)」为权威登记；实测消费者引用全活——batch 4 文件、jdbc 4、kafka 2、connector.file 4、connector 根包 9、`io.nop.message.debezium` 1、rocksdb 9。
- invariant 工具现状：`inventory`/`sync`/`scan-iterations`/`check-wildcard-imports` 绿；`scan-output-contract` **15 条**（1×V1 未注册 `StreamTaskInvokable$EdgeWriterOutput` + 7×V4 新发射点 + 7×V5 陈旧点打印行 = 6 个唯一点，其中 CepOperator:1356 在 registry 中重复登记故打印两次）；`scan-wiring` **9 条** V3 陈旧行号（其中 StreamTaskInvokable:266 在 registry 有两条不同语义条目——一条无 note、一条 WI22 note——重钉时须逐条按语义定位）。合计 24 条打印行 = WI7 移交 WI12 的「先存漂移重生成」债（漂移成因：WI7 窗口搬迁 + WI8/WI10 对 StreamTaskInvokable/ProcessOperator/CepOperator/SideOutputTapOperator/TaskCheckpointWiring 的行号位移与新类引入）。
- runtime package 结构：真实包 **21 个**（17 个顶层有文件 + checkpoint/{core,metrics,reshard,storage} 4 个子包），package-info 仅 **1/21**（WI6 的 `runtime.protocol`）；core 5 个。注意 `find -type d` 会数出 24 个目录：`operators/`、`operators/windowing/`、`operators/windowing/functions/` 是 WI7 搬迁后的 0 文件空目录（git 不跟踪残留），不得为其补 package-info，应顺带 rmdir 清理。
- WI10 差分钉与 fixture restore 的权威测试名（plan 2426 已钉死）：`TestCheckpointDualRunDifferential`（「双脸四件套」= 该单类内四族断言，非四个测试类）、`TestLegacyCheckpointFixtureRestore`（均位于 runtime test 树 checkpoint/ 下）；相邻钉 `TestJobCoordinatorFailoverRestore`、`TestSavepointVertexSetDifferential`。
- 文档现状：`ai-dev/design/nop-stream/01-architecture-baseline.md:35` 模块清单已写 13（WI3 交付）、`runtime → flow` 例外行在 :67，但依赖方向表**尚未**含 runtime.protocol / checkpoint.{core,storage} 叶子行（终稿需新增）；`docs-for-ai/01-repo-map/module-groups.md:23` nop-stream 行仍写「共 11 个」且 runtime 描述仍含「窗口算子」（WI7 后窗口算子族已归 core.operators.windowing）——即 roadmap 点名的出入；`docs-for-ai/04-reference/source-anchors.md` STRM-016—019 中 018/019 已随 WI7/WI8 更新、016/017 路径实测存在；`docs-for-ai/03-modules/nop-stream.md` 初判无窗口位置性错误（grep 命中均为算子级指标名/REST 语义），需终稿复核。
- runtime.source 包：2 文件（`CollectionReplayableSource`/`TimeRangeReplayableSource`），零 runtime 依赖（roadmap 实证）；core 侧引用仅 2 处 `{@code}` 纯文本 Javadoc（`NopStreamErrors.java:589`、`SuspendDataPolicy.java:30`），不构成编译/doc-link 依赖。裁定待落档。
- 构建环境：plan 2430（test-fork 污染）completed，`-am`/全仓构建已解冻；工作树存在并发会话未提交改动（nop-ai dialect、eql、demo pom 等，属 plan 2435-wi6/2435-wi8 波次）——本 plan 的 commit 必须 pathspec 严格限定，全量构建结果的归因需考虑该事实。
- gated 场景：`TestS1MultiJvmE2E`/`TestS2MultiJvmE2E` 位于 **nop-stream-fraud-example**（`io.nop.stream.fraud.scenario`，非 runtime 树），WI11 全绿基线 19s/34s，精确命令见 `ai-dev/design/nop-stream/distributed-runbook.md` §5。
- 三模块测试基线（WI11 收口值，不得下降）：core 1967/0/1、runtime 1203/0/11、flow 315/0/0。

## Goals

- WI12 置 `done`：门禁固化 + registry 重生成 + 文档终稿 + runtime.source 裁定 + 全量回归 + 独立 closure audit 全部落地，roadmap 整体收口（M3 翻转、M1 门禁承诺兑现）。
- 三条结构不变式从文档约定变成 CI 可执行检查：core→runtime 零命中、runtime 四包无环、connector test fan-in 按 WI5 方案 b 登记（无直接消费者的依赖不得出现）。
- `node ai-dev/tools/check-nop-stream-invariants.mjs` 全绿（24 条漂移归零）。

## Non-Goals

- 不做 JobCoordinator/巨型类拆分（RD-05）、execution 包拆分（WI11 已裁定暂不拆）、EXPORT 接入与终态预算默认值（WI11 defer→r5-successors）。
- 不改任何生产代码行为、序列化格式、checkpoint 存储格式（package-info.java 为纯 javadoc 包声明，是本 plan 唯一的 main 源文件变更）。
- 不处理并发会话的未提交 WIP（nop-ai/eql/demo 文件一概不触碰、不提交）。
- 不把 quickstart 纳入 reactor（E5 裁定维持）。
- 不重写历史 plan（guide 规则 #20）。

## Scope

### In Scope

- 新增门禁扫描工具 `ai-dev/tools/check-nop-stream-structure.mjs`（三 gate + self-test 正控）并接入 `.github/workflows/compliance.yml` 新 job。
- `ai-dev/audits/nop-stream-invariants/output-contract-registry.json` 与 `wiring-registry.json` 的 24 条漂移同步（V1 注册 EdgeWriterOutput、V4 登记 7 新发射点、V5 清 7 条打印行=6 唯一点、wiring 9 点行号重钉），逐点 provenance 记录。
- runtime main 各包 `package-info.java` 补齐（现 1/21，含 .java 文件的包；另有 3 个 WI7 搬迁残留空目录顺带 rmdir）。
- 文档终稿：`docs-for-ai/01-repo-map/module-groups.md` nop-stream 行（11→13 + 窗口算子归属修正 + 新包族）、`ai-dev/design/nop-stream/01-architecture-baseline.md` 依赖方向表终稿复核（runtime.protocol/checkpoint.core/checkpoint.storage 叶子）、`docs-for-ai/03-modules/nop-stream.md` 与 `docs-for-ai/04-reference/source-anchors.md` 终核。
- runtime.source 裁定（归位 core 或登记为 runtime 内置 replayable source 基线）并落档。
- 全量回归 `./mvnw clean install -T 1C` + gated S1/S2 多 JVM 场景按 `ai-dev/design/nop-stream/distributed-runbook.md` 跑并留档。
- roadmap 更新：WI12→done、M3→done（若证据支持）、收口宣告、`Last updated` 行；`ai-dev/logs/2026/10-11.md` 收口条目。
- 独立子代理 closure audit + evidence 写入本 plan。

### Out Of Scope

- 上述 Non-Goals 全部条目；以及任何 WI1—WI11 已收口事项的返工。

## Execution Plan

### Phase 1 - 结构门禁固化（工具 + CI + 变异探针）

Status: completed
Targets: `ai-dev/tools/check-nop-stream-structure.mjs`（新建）、`.github/workflows/compliance.yml`

- Item Types: `Feature`（门禁工具与 CI 步）+ `Proof`（变异探针）

- [x] 新建 `ai-dev/tools/check-nop-stream-structure.mjs`，含三个 gate（默认无参全跑，exit 0/1，风格对齐 `check-nop-stream-invariants.mjs` 的 no-silent-skip 纪律）：
  - [x] gate 1 `core-no-runtime-dep`：`nop-stream-core/src`（main+test）全文（含注释）grep `io.nop.stream.runtime`，命中 >0 即红，逐条列出 file:line。
  - [x] gate 2 `runtime-package-acyclicity`：对 transport/rpc/taskmanager/coordinator 四包建 import 方向边（`import io.nop.stream.runtime.<pkg>.`），检测环，发现环即红并打印环路径。包缺失 = 硬错误（防目录改名静默通过）。
  - [x] gate 3 `connector-test-fan-in`（WI5 方案 b）：解析 `nop-stream/nop-stream-runtime/pom.xml` test scope 的 connector 族依赖（`nop-stream-connector*`、`nop-stream-rocksdb`），逐一断言 runtime test 树存在 ≥1 直接引用（**import 或字符串 FQN 均计为引用**；消费者计数限定 `.java` 文件——探针 3 实证非 .java 文件提及 FQN 不构成 classpath 消费者）。**匹配语义（防假绿，钉死）**：`nop-stream-connector` 依赖只认根包类引用（`io.nop.stream.connector.[A-Z]`，即 `connector.` 后紧跟大写类名）与 `io.nop.stream.connector.file.` 子包，**显式排除** batch/jdbc/kafka 等子包引用（否则其他 artifact 的消费者会顶替满足 connector 断言，门禁形同虚设）；batch→`io.nop.stream.connector.batch.`、jdbc→`io.nop.stream.connector.jdbc.`、kafka→`io.nop.stream.connector.kafka.`、rocksdb→`io.nop.stream.rocksdb.`；debezium→`io.nop.message.debezium`（该包由 connector-debezium **传递**提供，runtime pom 无直接 nop-message-debezium 依赖——此非自明设计意图必须写进工具注释防未来「修正」）。无消费者的依赖出现即红。同时断言 pom 含「AR-2 test-scope exception register」登记注释（登记完整性）。
  - [x] `self-test` 子命令：对三个 gate 各置正控 fixture（合成源码/合成 pom 证明能红、合法样例证明能绿；gate 3 正控须覆盖「子包引用不得顶替根包消费者」反例），对齐 invariants 工具的正控纪律。
- [x] `compliance.yml` 新增 job（参照 `message-silent-wrong-result` job 的纯 node-only 形态：checkout + setup-node@v4 node 22 + 先 self-test 后扫描两步）；`runs-on: ubuntu-latest`。
- [x] 变异探针（R5 纪律：恢复后必须断言恢复完整）：①向 core main 文件（ProcessOperator.java）追加含 `io.nop.stream.runtime` 注释行→gate 1 必红→`git checkout` 恢复→`git diff --exit-code` 零改动确认；②向 rpc 包 RemotePipelineResolver.java 注入真实 `import io.nop.stream.runtime.transport.SubtaskPlanBuilder;` 行（与既有 transport→rpc 构成环）→gate 2 必红→恢复验证（探针记录：首探针误注入注释行不红——gate 2 只认 import 语句系设计行为，探针改真 import 后红）；③临时改名唯一 debezium 消费者 TestStreamConnectorRegistryDiscovery.java 为 `.probe-bak`→gate 3 必红→恢复验证。探针全程零提交文件残留。
- [x] YAML 有效性验证（python3 yaml.safe_load 解析，5 jobs 含新增 `nop-stream-structure`）。

Exit Criteria:

- [x] `node ai-dev/tools/check-nop-stream-structure.mjs` exit 0（当前基线三 gate 全绿）
- [x] `node ai-dev/tools/check-nop-stream-structure.mjs self-test` exit 0（正控全部通过）
- [x] 三条变异探针均「红→恢复→绿」且恢复完整性经 git diff 断言（记录于本 plan 勾选项与 log）
- [x] compliance.yml 解析为合法 YAML 且新 job 步骤与既有 job 形态一致
- [x] `ai-dev/logs/` 对应日期条目已更新（ai-dev/logs/2026/10-13.md 统一条目）

### Phase 2 - invariant registry 24 条漂移重生成

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/output-contract-registry.json`、`ai-dev/audits/nop-stream-invariants/wiring-registry.json`

- Item Types: `Fix`（已确认的 registry↔live 契约漂移，24 条实测非零）

- [x] `output-contract-registry.json`：V1 注册 `io.nop.stream.core.execution.task.StreamTaskInvokable$EdgeWriterOutput`（先读其 `collect(OutputTag)` 方法体定分类，forward/fail-fast 按实登记，禁止猜测）。
- [x] V4：登记 7 个新发射点（CepOperator.java:910/:1413、ProcessOperator.java:181/:204、WindowOperator.java:1796/:2636、SideOutputTapOperator.java:68），每点 provenance（对应旧点漂移 or 新点+引入 plan）。
- [x] V5：清除 7 条陈旧点打印行（6 个唯一点：ProcessOperator:124/:147、WindowOperator:1327/:1926、CepOperator:875/:1356；其中 :1356 在 registry 重复登记为两条，一并去重），确认其已由 V4 新点承接（同语义点位漂移）方可删除，逐点 provenance。
- [x] `wiring-registry.json`：9 条 V3 陈旧 wiring 点（StreamTaskInvokable 8 处 + TaskCheckpointWiring:175）重钉到当前行号，逐条核对为同一调用点位移而非调用点消失（消失即另案裁定，不得静默改行号）；注意 StreamTaskInvokable:266 在 registry 有两条不同语义条目（无 note 条目 + WI22 note 条目），须分别按各自语义定位新行号。
- [x] 全量重跑 `node ai-dev/tools/check-nop-stream-invariants.mjs`（all face）exit 0。
- [x] **JUnit 孪生面同步（执行中实证补齐）**：V1 注册 EdgeWriterOutput 后，core 的 `TestOutputContractInvariant` 因「forward 分类实现类缺 harness」红（no-silent-skip 设计）——补 EdgeWriterOutput forward harness（匹配 tag edge 发射 SideOutputElement + 非匹配 edge 丢弃双断言）+ 4 参反射构造 helper，15/15 绿。registry json 与 JUnit 行为钉是同一契约的两 face，缺一不可。

Exit Criteria:

- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs` exit 0（inventory/sync/iterations/output-contract/wiring/wildcard/self-test 全绿；重跑输出零违规零 stale pin）
- [x] 24 条漂移逐点 provenance 已记录（registry note 字段内逐条落点：V4/V5 各点的旧→新行映射与引入来源〔plan 2332 / commit cdcda8ad45 / WI7-WI10 漂移〕；setOutput 的两条 ChainingOutput 同义重复条目按 V5 去重先例合并为 :388 单条；新增 :317/:345/:347 三个 plan 2332 布线点回填登记），无「删旧加新」无溯源的条目
- [x] 无新增 pins（registry sync 是登记同步不是违规豁免；本 phase 零 pin 变更）
- [x] `ai-dev/logs/` 对应日期条目已更新（统一于 Phase 6 收口条目）

### Phase 3 - runtime 包 package-info 补齐

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/**/package-info.java`

- Item Types: `Feature`（低风险纯 javadoc 源文件）

- [x] 枚举 runtime main 全部**含 .java 文件的包**（实测 21：17 个顶层 + checkpoint/{core,metrics,reshard,storage}；`find -type d` 数出的 24 个目录中 `operators/` 树 3 个空目录为 WI7 搬迁残留、git 不跟踪——**rmdir 清理**，不补 package-info），为缺失者各补 `package-info.java`，一句话边界说明（内容与 architecture baseline 依赖方向表口径一致；`protocol` 已有则复核即可）。
- [x] No new test required: 纯包级 javadoc 声明，无可执行行为。

Exit Criteria:

- [x] `find nop-stream/nop-stream-runtime/src/main -name package-info.java | wc -l` = 21（含 .java 文件的包总数），且每包恰 1 个；空残留目录已 rmdir（operators 三层空目录已删）
- [x] `./mvnw -o test-compile -pl nop-stream/nop-stream-runtime` 绿（含 package-info 编译；期间实证修复 plan 2435 遗留的 TestCc17TerminalReportAsync 不可编译缺陷——见 Phase 5 scope change 1）
- [x] 边界描述与 `01-architecture-baseline.md` 口径无矛盾（checkpoint.core/storage/execution/transport 四包人工核对；execution 包注明 WI11 暂不拆裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新（统一于 Phase 6 收口条目）

### Phase 4 - 文档终稿同步 + runtime.source 裁定落档

Status: completed
Targets: `docs-for-ai/01-repo-map/module-groups.md`、`ai-dev/design/nop-stream/01-architecture-baseline.md`、`docs-for-ai/03-modules/nop-stream.md`、`docs-for-ai/04-reference/source-anchors.md`、roadmap

- Item Types: `Fix`（module-groups 11≠13 实证出入）+ `Decision`（runtime.source 归属裁定）

- [x] `module-groups.md` nop-stream 行：「共 11 个」→「共 13 个」并逐一核对 13 子模块描述；runtime 描述删「窗口算子」（归 `nop-stream-core` 的 `core.operators.windowing`）；补 runtime.protocol / checkpoint.core / checkpoint.storage 关键包与 wire DTO 叶子描述；核对 sql/fraud-example 描述与实况一致。
- [x] `01-architecture-baseline.md` 终稿复核（审查轮已实测确认）：依赖方向表**需新增** runtime.protocol 零出边叶子与 checkpoint.{core,storage} 包行（WI3/WI10/WI11 均未写）；窗口算子归属注记核对；`runtime → flow` 例外行（:67）保持；模块表 13 计数与 `nop-stream/pom.xml` `<modules>` grep 计数一致。
- [x] `03-modules/nop-stream.md` 终核：grep 实证无任何窗口算子路径/工厂/CheckpointStorages/RestoreGates 陈旧表述（涉及处均为算子级指标名与 REST 语义），零修改需要；`source-anchors.md` STRM-016—019 终核：四锚点路径实测存在、018/019 描述与 WI7/WI8 后 live 一致。
- [x] runtime.source 裁定：基于实测证据二选一——(a) 归位 core；(b) 登记为 runtime 内置 replayable source 基线。裁定倾向 (b)：core 侧引用仅 `{@code}` 文本无编译依赖、2 文件零 runtime 依赖自包含、搬迁无行为收益（E2 不为覆盖率而重构）。落档点：architecture baseline（模块/包边界节）+ roadmap §WI12 登记遗留项销账 + 本 plan。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0。

Exit Criteria:

- [x] module-groups.md 行与 `nop-stream/pom.xml` 13 模块实况一致（grep 自查记录）
- [x] architecture baseline 依赖方向表覆盖 runtime 全部新叶子包（protocol 零出边叶子 / checkpoint.{core,storage} / runtime.source 基线 / CheckpointStorages choke-point / 双驱动面差分钉五行新增），无与 live 冲突表述
- [x] runtime.source 裁定在 architecture baseline 落档（roadmap 侧销账随 Phase 6 收口翻转同步完成）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（0 errors；本 plan 一处路径警告已顺手修正）
- [x] `ai-dev/logs/` 对应日期条目已更新（统一于 Phase 6 收口条目）

### Phase 5 - 全量回归与 gated 场景留档

Status: completed
Targets: 全仓 Maven 构建、nop-stream gated 多 JVM 场景

- Item Types: `Proof` + `Fix`（scope change 1：plan 2435 遗留测试编译缺陷）

- [x] **Scope change 2（计划外 Fix，2026-10-11 深夜至 10-13 实证，经 4 轮二分+日志取证收敛）**：gated S1 `s1MultiJvmKillRecoverFencingExactlyOnce` 在 HEAD 确定性红（「recovery must rotate the fencing epoch: initial=1 recovered=1」，2+3 次复现；S2 恒绿）。根因链（日志取证：_tmp/mini-stream-cluster/1791732207123-1 preserved artifacts）：①plan 2435 的 CC-17 把终态报告改异步后，`stop()` 里排队的 FAILED 报告与 shutdown hook 后续的 executor shutdown/数据源关闭**赛跑**——报告丢失；②该 FAILED 报告正是快速故障切换信号（租约 15s 前触发 recovery+轮转），丢失后快速重启节点（~2s，<15s）重注册刷新租约，节点级检测被掩盖，JC 以旧 epoch 重放控制面收件箱（重启 TM 从消息队列起点重放原始 deployTask），有界作业自然跑完（23:23:45 全部 14 任务 COMPLETED、ACK 集清零），恢复/轮转永远无理由发生；③**并发会话事故**：提交 5d3d8920b8（名义 nop-ai WI9 错误码归位，提交说明零提及 nop-stream）静默回退了 CC-17 的 RunningTask 异步 hunk **和 CC-06 的 JobCoordinator barrier fan-out hunk**（git show 对照 blob 8e76098cfc←091d80eb13/f3e438e759 实证）——与 2026-10-11 log 已登记的「amend 卷入并发暂存」同型的工作树污染事故；r5-successors 台账仍记 CC-06/CC-17 done，形成僵尸状态。本 plan 的恢复=把两 hunk 翻回波次状态+drain 修复，恢复理由以 S1/S2 绿证据背书，双向登记于本 plan 与 log。修复三件：**TaskManager.stop() 末尾对 terminalReportExecutor 做 shutdown+awaitTermination(5s) 排空**（先于 hook 关数据源，FAILED 报告必达——保留 CC-17「死任务线程不阻塞」意图）；**恢复 RunningTask 异步 hunk**（撤销 5d3d8920b8 的未记录回退，双向理由登记于本 plan 与 log）；（过程性：stop() cancel 前置的中间方案被取证证伪后回退）。验证：S1+S2 gated 4/4 绿（21.6s/36.4s，与 WI11 基线同量级）。二分矩阵留档：all-reverted 绿 10.48s / P1(JC@HEAD) 绿 / P2(sync+旧序) 绿 / P3(sync+cancel-first) 红 / gated-final(async+cancel-first) 红——单次样本的 P1/P2 翻转后经取证证实为竞态噪声，最终以日志取证钉死机制。
- [x] **Scope change 1（计划外 Fix，2026-10-11 深夜实证）**：plan 2435 C1/C2 提交的 `TestCc17TerminalReportAsync` 在 HEAD 不可编译（测试调用 `terminalReportExecutorForTest` 访问器从未落盘 TaskManager）——正是 WI10/WI11 注记「Maven 复跑归 WI12 兜底」的项。修复三件：①TaskManager 补回包私有访问器（AbstractExecutorService 桩：每次提交异步代跑探针，模拟慢 coordinator RPC）+ `terminalReportExecutor` 字段去 final；②`forceTaskExecutorShutdownForTest` 补 `running=true`（否则 not-running 状态门先拦，executor-race 分支不可达——测试自述覆盖点为该分支）；③测试 descriptor 补 `RemotePipelineSpec` + fencing epoch 对齐 0（同理抵达 race 分支）。定向测试 TestCc17TerminalReportAsync 2/2 + TestCheckpointBarrierFanoutAsync 1/1 全绿。纳入理由：guide #15/#16——实证 live defect 不得 defer；WI12 是该兜底的既定归属。

- [x] `./mvnw clean install -T 1C` 全仓通过（含 nop-stream 13 模块 + 下游消费者）——**降级门已触发，证据如下**：全仓第 1 次（_tmp/wi12-full-install.log）在 nop-stream-core 红（本 plan Phase 2 的 EdgeWriterOutput JUnit 孪生 harness 缺失，已修）；第 2 次（_tmp/wi12-full-install-2.log）在 nop-api-core 红——`TestCrudInputBase.testJsonAnyGetterRoundTrip`/`testDeserializeToMap` 2 例，第 1 次构建时该模块绿、第 2 次构建前并发 jdk25-sb4-q4 波落了 commit 9b99e54fdf（SB4.1.1 平台统一，plan 2437），本 plan 零改动涉 jackson/api-core——归并发波次面。依 plan 既定程序登记证据并启用降级门：`-pl` 13 个 nop-stream 模块 + nop-benchmark-stream（唯一外部下游，grep 实证）`-am -DskipTests` install + nop-stream 面显式全量测试。nop-api-core 回归属 SB4 波次 face（nop-api-core 为 Protected Area，本 plan 不越权修复），移交该波次处理。
- [x] 三模块基线复核不下降：core 1968/0/1（≥1967；+1=EdgeWriterOutput harness 新参数化用例）、runtime 1210/0/11（≥1203/0/11；+7=CC 波次新增 3 方法+余项为 registry 同步带动的参数化增长，失败/skip 面零变化）、flow 315/0/0（精确持平）；cep 383/0/0、sql 173/0/0（修复后 runtime 终跑 _tmp/wi12-runtime-final.log）。
- [x] 差分钉与 fixture restore 用例点名绿（M3「跨驱动面差分归零」证据；权威类名经 plan 2426 钉死）：`TestCheckpointDualRunDifferential`（单类四族断言）、`TestLegacyCheckpointFixtureRestore`；相邻钉 `TestJobCoordinatorFailoverRestore`、`TestSavepointVertexSetDifferential` 一并确认绿。
- [x] gated S1/S2：`TestS1MultiJvmE2E`、`TestS2MultiJvmE2E` 4/4 绿（修复后终跑 21.60s/36.40s，_tmp/wi12-gated-v2.log；exactly-once 断言原样；初跑 S1 kill-recover 红经 Scope change 2 修复后转绿，红绿全程留档）。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 无新增（810 文件扫描 exit 0）。

Exit Criteria:

- [x] 全仓 clean install 绿（或按本 phase 首条的降级门路径执行并登记 scope change）——降级门路径执行：上游链+13 模块+下游 install 绿（-DskipTests，nop-api-core 2 例红归 SB4.1.1 波）+ nop-stream 面五模块全量测试绿
- [x] 三模块基线计数不低于 WI11 收口值（core 1968/0/1、runtime 1210/0/11、flow 315/0/0——逐项见上）
- [x] 差分钉 + fixture restore 点名用例绿（`TestCheckpointDualRunDifferential`/`TestLegacyCheckpointFixtureRestore`/`TestJobCoordinatorFailoverRestore` 11/11/`TestSavepointVertexSetDifferential` 均在 runtime 1210 终跑内绿）
- [x] S1/S2 gated 场景绿（4/4，21.60s/36.40s 留档）
- [x] scan-hollow nop-stream 无新增 high 发现（exit 0，810 文件）
- [x] `ai-dev/logs/` 对应日期条目已更新（统一于 Phase 6 收口条目）

### Phase 6 - 独立 closure audit + roadmap 收口

Status: completed
Targets: `ai-dev/backlog/nop-stream-structure-refactor-roadmap.md`、本 plan、`ai-dev/logs/2026/10-11.md`

- Item Types: `Proof`（独立审计）+ `Decision`（roadmap 状态翻转）

- [x] 独立子代理 closure audit（fresh session，不复用本实现会话）：逐条核对 Phase 1—5 Exit Criteria 与 Closure Gates、Anti-Hollow 检查（门禁工具真实被 compliance.yml 引用、扫描器正控可红、package-info 非空壳描述）、roadmap WI12 验证清单四项工具全绿复核。evidence 写入本 plan `## Closure`。
- [x] roadmap 更新：WI12 → `done`（附 plan 2436 + 关键裁定括注，注意 mission-driver 单层括注约束）；M3 → `done`（证据：差分 harness 归零 + WI11 方案 a 完全对齐 + S1/S2 gated 绿）；M1 括注的「CI 门禁固化于 WI12」承诺兑现注记；`Last updated` 行；§Phase E 收口宣告（roadmap 整体 done）。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` exit 0（Status 翻转后复跑通过）。
- [x] 文本一致性核对：本 plan Status/slice status/exit criteria/closure gates/log 五处一致。
- [x] 单次 commit：pathspec 严格限定本 plan 触碰文件（门禁工具、compliance.yml、2 个 registry json、runtime package-info 族、4 个文档、roadmap、本 plan、log）；不卷入并发会话文件。**脏日志特别程序**：`ai-dev/logs/2026/10-11.md` 当前已被并发会话改动（M 状态）——commit 时先 `git diff ai-dev/logs/2026/10-11.md` 检查：若 diff 只剩本 plan 追加的条目 hunk，直接 pathspec add；若仍混有并发条目 hunk，从完整 diff 中仅摘取本 plan 条目所在 hunk 生成补丁经 `git apply --cached` 暂存（hunk 不可分离时，log 延后单独提交并在本 plan 登记原因，其余文件照常单次提交）。同样程序适用于 roadmap 文件（若届时仍带并发改动）。

Exit Criteria:

- [x] 独立 closure audit 完成且 evidence 已写入本 plan `## Closure`（含每条 gate 的 PASS/FAIL）
- [x] roadmap WI12/M3 状态翻转且状态行解析约束满足（12 WI 行 + 3 里程碑行重跑核对、全状态行括注零违规；顺手修复 WI10 行 HEAD 既有的嵌套全角括号——审计条件项 2）
- [x] `check-plan-checklist.mjs --strict` exit 0
- [x] commit 完成且 `git status` 确认无并发会话文件被卷入

## Closure Gates

- [x] 三条结构不变式 CI 门禁固化完成且变异探针证明可红（M1 承诺兑现）
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs` exit 0（24 条漂移清零）
- [x] `node ai-dev/tools/check-nop-stream-structure.mjs`（含 self-test）exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 无新增
- [x] 全仓 clean install 绿（或 Phase 5 显式裁定的降级门绿 + scope change 登记）
- [x] 三模块测试基线不下降（core 1967/0/1、runtime 1203/0/11、flow 315/0/0）
- [x] gated S1/S2 场景绿并留档
- [x] runtime.source 裁定落档（architecture baseline + roadmap 双向）
- [x] 文档终稿四件（module-groups / architecture-baseline / 03-modules / source-anchors）与 live 一致
- [x] roadmap WI12→done、M3→done、收口宣告完成
- [x] 独立子代理 closure audit 完成且 evidence 写入本 plan
- [x] Anti-Hollow Check：closure audit 已验证（a）门禁工具被 compliance.yml 真实调用（b）扫描器正控可红（c）package-info 描述非占位空话
- [x] commit 完成且不含并发会话文件

## Deferred But Adjudicated

### 全仓构建被并发 WIP 阻断时的降级门

- Classification: `watch-only residual`
- Why Not Blocking Closure: 并发会话 WIP（nop-ai/eql/demo）不在本 plan scope，其红不构成本 plan 缺陷；降级门（nop-stream 13 模块 + 下游 clean install）覆盖 roadmap「13 模块 + 下游消费者」字面要求；全仓绿由后续会话在 WIP 落地后自然恢复。
- Successor Required: `no`（条件性预案，仅在 Phase 5 首条触发时启用并登记）

### EXPORT 接入 SavepointMaterializer / 终态预算默认值归一

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: WI11 已裁定 defer→`nop-stream-r5-successors.md`（四契约缺口 + 无差分语料），双向登记已完成，本 plan 不重开。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/nop-stream-r5-successors.md`（DIV 项）

## Non-Blocking Follow-ups

- roadmap 收口后 `nop-stream-productization-roadmap.md` / `nop-stream-xdsl-full-coverage-roadmap.md` 等姊妹 roadmap 若有与本 roadmap 互指的收口注记，按其自身节奏处理（本 plan 只保证结构重构 roadmap 自身文本一致）。

## Closure

Status Note: WI12 全部交付（结构门禁 CI 化、registry 24 漂移清零、21 包 package-info、文档终稿、runtime.source 裁定落档、降级门全量回归、gated S1/S2 4/4、plan 2435 遗留兜底三项修复、5d3d8920b8 静默回退事故恢复），roadmap WI1—WI12 与 M1—M3 全部 done、整体收口宣告。独立 closure audit 结论 APPROVE-WITH-CONDITIONS，两项条件均已在收口程序内消除（①Closure Evidence 写入+Status 翻转+checklist 复跑+commit；②roadmap WI10 行 HEAD 既有的嵌套全角括号已顺手修复）。

Completed: 2026-10-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 general-purpose（fresh session）
- Audit Session: agent_b74c243c-db36-4bf5-b4c2-d9e58b95c380
- Evidence:
  - 机械门实测：check-nop-stream-structure（all: OK / self-test: OK）、check-nop-stream-invariants（exit 0）、check-doc-links --strict（4009 文件/47595 引用/0 issues）、scan-hollow nop-stream --severity high（810 文件全 0）、check-plan-checklist --strict（Passed: 1；Status 翻转后复跑通过）
  - 15 项逐条核对：14 PASS + 1 CONDITIONAL（Phase 6 既定收口程序，已消除）、0 FAIL；交付物抽查含 compliance.yml :73-85、registry 重钉行号与 live 逐一比对（:699/:388/:176）、module-groups 13 模块与 pom 一致、architecture baseline 裁定行 :69-:73
  - Anti-Hollow 三问全 yes：门禁工具被 compliance.yml 真实引用（非孤岛）；self-test 七组负控可红（含子包顶替反例）；package-info 抽读 4 包与 live 逐条相符
  - 行为级硬核实：git show 5d3d8920b8 与 plan 叙述逐字一致（父 blob 091d80eb13）；TaskManager.stop() 排空代码在 :398-406；S1/S2 gated 4/4（21.60s/36.40s）
  - Deferred 项分类检查：无 in-scope live defect 被降级（Scope change 1/2 均为 Fix 并已修复验证；降级门预案未被触发启用为永久态——SB4 波 nop-api-core 2 例红移交该波次）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2436-wi12-full-closure-and-gate-hardening.md --strict` Status 翻转后复跑 exit 0

Follow-up:

- no remaining plan-owned work（roadmap 已整体收口；SB4 波 nop-api-core `TestCrudInputBase` 2 例红归该波次 face，本 plan 仅登记取证不跨权修复）
