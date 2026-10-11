# nop-stream 结构重构 roadmap——边界收敛、窗口算子归位与双 checkpoint 驱动面统一

> Last updated: 2026-10-11（v1.2。v1 条目来自 2026-10-11 对 nop-stream 13 个模块的模块划分 + 内部结构实证审计；v1.1/v1.2 经五名独立子代理两轮对照代码逐条复审修订：修正死链计数（5 条→2 条真死链 + 3 条有效引用）、双 checkpoint 引擎委托关系（GMCE 内嵌并委托 CheckpointCoordinator，非两套独立实现）、WI5 测试消费者普查（9 文件直接 import + 2 文件字符串/FQN 引用，非唯一）、IWindowOperatorFactory 方法计数（4 抽象 + 13 default = 17）、metrics 绕过计数（12 处）、根 pom Java 基线冲突（17 非 21）、M1 门禁交付载体对齐（CI 固化步在 WI12）等。复审锚定 commit 14527e2da0——其 nop-stream 代码面与基线 4ec725b5c8 一致；工作树暂存改动不计入本 roadmap 数字口径）
> 位置：按仓库 roadmap 惯例存放于 ai-dev/backlog/。书写约定：未来交付物路径用普通文本文书、不加反引号；已存在的文档路径用反引号，持续受 check-doc-links 保护。该约定约束文件/目录路径；包名、类名、注解仍按代码格式使用反引号。
> mission-driver 兼容：本块采用 `## Work Item Status` 与 `- WI<n> 名称: \`status\`` 形态，状态集为 `todo` / `ready` / `planned` / `done`。状态行尾部括注必须单层非嵌套——只允许一组全角括号且其内不得再出现括号或半角右括号。改任何状态行后必须重跑解析核对条目数。里程碑行写作 `★ **里程碑：<名> …**（解锁条件 …）：\`status\``。
> 审计基线 commit = `4ec725b5c8`。规模：13 模块 / 816 main 文件 / ~133k main 行。core 421 文件 52,153 行，runtime 123 文件 34,484 行，flow 95 文件 13,134 行（以上为基线 commit 钉住值）。
> 与既有 roadmap 的边界：本 roadmap **只做模块边界与结构层**（依赖结构缺陷及其正确性后果亦归本 roadmap，如 WI4）。正确性/性能/资源类缺陷归 `ai-dev/backlog/nop-stream-r5-successors.md`（RD-05 JobCoordinator 拆分、RD-06/07/08 重复块、CC-06 checkpoint 并发——其中 CC-06/CC-17 由本 roadmap WI10 顺带修正，销号联动见 E4）；不变量类归 `nop-stream-invariant-loop-roadmap.md`（P2-01-03 core 反射软耦合已登记 todo，本 roadmap WI7 落地时销号）；功能/声明面类归 `nop-stream-productization-roadmap.md` / `nop-stream-xdsl-full-coverage-roadmap.md`（其中 `nop-stream-production-roadmap.md` 的 P2-6 与 WI8 同接口，WI8 落地时按 E4 销号）。各 roadmap 不重复立项。

> Sources:
> - `ai-dev/design/nop-stream/01-architecture-baseline.md`（§二 模块划分、§二 依赖方向约束表——本 roadmap 的规范基线，也是 WI3 要回写的对象）
> - `ai-dev/design/nop-stream/checkpoint-design.md`（§2 Epoch Checkpoint 协议，含 §2.2 Epoch 生命周期——WI9 双驱动面收敛的设计输入）
> - `ai-dev/design/nop-stream/window-design.md`（窗口算子职责边界——WI7 归位的判定依据）
> - `ai-dev/design/nop-stream/core-design.md`、`ai-dev/design/nop-stream/dataplane-transport-design.md`
> - `ai-dev/backlog/nop-stream-r5-successors.md`（前序审计裁定总表，边界裁定的上游）
> - 2026-10-11 实证审计（v1 缺陷条目的锚点来源，逐条见各 WI 描述）
> - 2026-10-11 三轮独立子代理实证复审（v1.1 修订来源：对 v1 全部锚点做代码级复核并修正本版标注的差异）

## Purpose

把 nop-stream 从「模块划分正确、内部结构弥散」推进到「边界可验证、职责单一」。核心命题不是补功能，而是三个结构性收敛：

1. **边界可验证**：消灭 `core → runtime` 的反射软耦合与 2 条 Javadoc 死链（core main 另有 4 个文件的注释级跨模块引用，随 WI3 收敛为文内可解析形式）、打破 `transport ↔ rpc ↔ taskmanager` 包级环、将 runtime 测试侧对 connector 的反向 fan-in 收敛为零或显式登记的最小集、修正 2 个悬空 BOM 条目。
2. **窗口算子归位**：`core.operators` 根包 34 个类型文件（10 个根包内具体算子 + join 子包 EquiJoinOperator + 2 个抽象基类）承载全部算子实现，唯独窗口算子族（`WindowOperator` / `OverWindowOperator` 及状态适配器等 17 个 main 文件）住在 runtime。这个切口逼出了 core 里的反射兜底、17 方法的 `IWindowOperatorFactory`、642 行适配器三层代偿。归位后三者同时消失。
3. **双 checkpoint 驱动面统一**：分布式路径（JobCoordinator → `CheckpointCoordinator`）与本地/savepoint 路径（`GraphModelCheckpointExecutor`，内嵌同一个 `CheckpointCoordinator`）共享触发/恢复内核，但两驱动面各自实现外围编排语义（trigger 时机、restore 后状态重建、savepoint 触发、终止模式），且无跨驱动面一致性测试钉——exactly-once 语义最易「分布式恢复对、本地恢复错」的形状。

非目标（明确排除）：巨型类职责抽离（JobCoordinator 2882 / WindowOperator 2787 / StreamModelDslBuilder 2473 / StreamTaskInvokable 1480 / InputGate 1341）——已由 RD-05 等前序裁定登记，不在本 roadmap 重复立项；功能新增；性能优化；`core` 内部包级强连通（Flink 血统图计算引擎的固有形态，非缺陷）。

## Work Item Status

- WI1 nop-bom 悬空条目清理与依赖版本收口: `done`（plan 2410，2026-10-11）
- WI2 quickstart 生成器跨平台修复与模板版本对齐: `done`（plan 2411，2026-10-11；实测订正：平台 junit-jupiter 解析版本 6.0.3，经 quarkus.platform 3.35.1 import 链传递管理，模板钉版本与之一致无漂移，本节原文「5.x 线漂移」前提不成立；模板三版本点经实测全部维持现状）
- WI3 架构基线与 owner docs 回写: `done`（plan 2412，2026-10-11；STRM-018/019 已登记 WI7/WI8 变更预告，最终改写归后续 WI）
- WI4 connector-jdbc provided+optional 依赖硬化: `done`（plan 2413，2026-10-11；裁定方案 b 窄接口隔离——a 方案 compile 化实测边际 65 构件约 33.6MB 触发 roadmap 预设的不可接受阈值；防回归门禁为 surefire 独立 execution insert-no-debezium 持久自动化）
- WI5 runtime 测试侧 connector 反向 fan-in 收敛: `done`（plan 2415，2026-10-11；裁定方案 b 显式登记例外、最小集=现有 6 个 test 依赖——debezium 被 P-REQ-28 discovery 断言硬性要求、rocksdb 有 9 个真实消费文件、门控分支同样削弱验收面；pom 注释重写为逐依赖消费者普查表；runtime 测试基线重钉：surefire 1377 run/0 failed/10 skipped——Docker 不可用环境、Docker 可用时 skipped=8——加测试树 361 java 文件、320 个 Test* 类，WI6/WI7/WI10 以此值为不得下降基线）
- WI6 runtime 包级环打破与 wire DTO 叶子包提取: `done`（plan 2416，2026-10-11；三环全消——7 个 wire 类型含 JobStatus/RemotePipelineSpec 迁入 protocol 零出边叶子，四环包编译级依赖图成 DAG；Kafka/Pulsar codec 抽 AbstractStringWireCodec 消除登记在案的克隆；metrics 收口 10/12——windowing 2 处顺延 WI7 防 core→runtime 反向边，StreamOpsHttpServer 的 addMember/removeMember 属成员管理非 registry 访问器不计；runtime 测试 1380/0/10——基线 1377 全数保持加新增 wire 兼容钉 3）
- WI7 窗口算子实现包归位 core: `done`（plan 2417，2026-10-11；main 17 + test 46 迁入 core/operators/windowing，TestIdlePeriodProcessingTimeWindowE2E 留守 runtime 并以 test-visible 访问器适配白盒探针；WindowedStreamImpl 反射删除改直接构造、4 处 call-site 消息随事实改写、flow 3 用例正向化；E4 双向销号 invariant-loop P2-01-03/P2-23 完成；invariant inventory exit 0、24 条先存漂移交 WI12 重生成；基线 core 1952/0/1、runtime 1195/0/10、flow 315/0/0；不变式钉 core src 的 io.nop.stream.runtime 命中数=0 达成）
- WI8 IWindowOperatorFactory 收敛为 WindowSpec 单一契约: `todo`
- WI9 双 checkpoint 驱动面重叠测绘与收敛设计裁定: `todo`
- WI10 共享 checkpoint 内核抽取: `todo`
- WI11 分布式路径切换至统一内核: `todo`
- WI12 全量收口与门禁固化: `todo`

★ **里程碑：M1 边界零泄漏**（WI1 + WI2 + WI3 + WI4 + WI5 + WI6 全部 done，注释级 `core → runtime` 反向依赖与 runtime 包级环已消除且二者 CI 门禁固化于 WI12，反射面随 WI7 归零，测试侧 connector fan-in 按 WI5 裁定收敛为零或显式登记的最小集）：`done`（2026-10-11——消除已达成、CI 门禁按本行约定由 WI12 交付）
★ **里程碑：M2 窗口算子归位**（WI7 + WI8 done，反射兜底与 17 方法接口消失）：`todo`
★ **里程碑：M3 双驱动面统一**（WI9 + WI10 + WI11 done，分布式与本地恢复共用同一内核且跨驱动面差分归零）：`todo`

> 注：各 Phase 标题中的「完成门」指该 Phase 全部 WI done 时应达成的里程碑，**非进入前置**；执行顺序按 Phase 字母序与 WI 编号序，其中 WI8 在 WI7 完成后执行。Phase E 的完成门是收口判据，不属于 M1—M3 编号里程碑；M1 括注中的 CI 门禁固化动作由 WI12 交付，M1 解锁条件按「消除已达成 + 门禁已排期」审计。

## Phase A——事实纠偏与零风险清理（完成门：无）

### WI1 nop-bom 悬空条目清理与依赖版本收口

> 无需 plan 即可执行的 XS 级修复；仍按 plan 走流程以保留验证与 commit 记录。

- 验证并删除 `nop-bom/pom.xml` 中两个指向**不存在模块**的条目：`nop-stream-api`（:1246）与 `nop-stream-checkpoint`（:1318）。两模块在 commit `e13895fd87`「设计声明式 Stream DSL 元模型并清理空壳模块」中已删除，BOM 未同步。同类残留还在 `tests/pom.xml:866`（nop-stream-api）与 `:890`（nop-stream-checkpoint），纳入本 WI 一并删除。删除前须用 `git log --diff-filter=D` 复核删除事实与当时决策，避免误删复活中的模块。
- 复核 BOM 对 nop-stream 13 模块的覆盖现状：当前仅覆盖 7 个（core/flow/cep/connector/connector-kafka/format-avro/runtime），缺 `connector-batch`、`connector-jdbc`、`connector-debezium`、`rocksdb`、`sql`、`fraud-example`。裁定哪些应进 BOM（对外可消费坐标）哪些属内部实现坐标，**只补应补的，不做全量对齐**。
- 版本收口：`nop-stream/nop-stream-rocksdb/pom.xml:23` 硬编码 `rocksdbjni 9.11.2`，绕过 `nop-kernel/nop-dependencies` 单点管理（对照该文件中 `kafka.version` / `avro.version` 的既有集中管理模式，二者均无 rocksdb 条目）。移入 `nop-dependencies`。
- 验证：`./mvnw -o dependency:tree -pl nop-stream/nop-stream-rocksdb` 版本来源正确（`-o` 离线模式前提为本地仓库已含全量依赖，全新环境先在线构建一次）；`./mvnw -o -q install -DskipTests -pl nop-stream/nop-stream-rocksdb` 通过；全仓无对已删模块坐标的残留引用（含 `tests/pom.xml`）。

### WI2 quickstart 生成器跨平台修复与模板版本对齐

- `nop-stream/quickstart/generate.sh:89` 使用 BSD 专用 `sed -i ''`：BSD 下空串是就地编辑后缀；GNU sed 的 `-i` 可选后缀必须连写（如 `-i.bak`），独立词 `''` 不会被消费、会被当作文件操作数导致脚本失败。改为可移植写法（临时文件替换，或按 `uname` 分支）。这是 quickstart 唯一的一键入口，Linux CI/新同事克隆后第一步即断。
- `nop-stream/quickstart/template/pom.xml` 版本对齐，分三类处理：
  - `maven.compiler.release` 当前为 17，**与根 pom `java.version=17` 一致**（根 pom `:25-30`），但与 AGENTS.md 自述的「Java 21」平台基线冲突。本 WI 不做机械升版：plan 阶段先核实平台真实基线（以根 pom / nop-dependencies 为准）并记录裁定——若平台维持 17 则模板不动；若裁定升 21，属平台级基线变更，另立条目落地后同步模板。
  - `junit-jupiter 6.0.3` 与平台实际解析版本漂移：根 pom 与 nop-bom 均不直接管理 junit-jupiter（`nop-dependencies` 经 import 的 quarkus BOM 传递管理 5.x 线），模板无 parent 只能显式钉版本或自行引 BOM——对齐为平台实际解析版本，具体形态（钉版本 / 引 BOM）由 plan 阶段裁定。
  - `maven-surefire-plugin 3.2.2` 对照根 pom pluginManagement 对齐或删除显式版本。
- `nop-stream/quickstart/verify.sh:28-37` 在 jar 存在时跳过 `install`（自述 AR-28/AR-1 已知，注释见 `verify.sh:26-27`），保留但需在 README 显式标注该启发式的失效场景与 `FORCE_REBUILD=1` 的用法。
- 验证：在 macOS 上跑通 `generate.sh` + `verify.sh` 全流程；若采用分支实现，GNU 分支必须经 `docker run`（GNU sed）或 Linux 环境覆盖一次（macOS 自力无法覆盖，BSD sed 无 `--version` 可探测）；若采用临时文件替换则 macOS 与 Linux 各跑一次；生成项目 `./mvnw test` 绿。

### WI3 架构基线与 owner docs 回写

- `ai-dev/design/nop-stream/01-architecture-baseline.md:32` 写「子模块清单与 nop-stream/pom.xml `<modules>` 一一对应（共 10 个）」，实际 **13 个**（缺 `connector-kafka`、`format-avro`、`sql`）。§二 依赖方向表（:36-63）亦缺 `runtime → flow` 编译边（实证锚点 `nop-stream/nop-stream-runtime/pom.xml:54`）。
- 依赖方向表补两条实测事实：(a) `runtime → flow` 为编译期边但仅 3 个文件且全在 `runtime.maintain`（StreamConfValidateCommand / StreamSampleCommand / StreamLintCommand），裁定为「可辩护的例外」并写明范围；(b) `runtime` 测试侧对全部 5 个 connector 的 fan-in（WI5 的对象），明确它违反 `nop-stream/nop-stream-connector/pom.xml:14-29` 注释所述 AR-2 意图。
- core 对 runtime 的注释级引用收敛（为 WI7 的零命中钉扫清注释半边）：core `src/main` 现有 **5 个文件**引用 `io.nop.stream.runtime`，其中真死链 2 条——`core/windowing/assigners/WindowAssigner.java:74` 引用 `io.nop.stream.runtime.task.StreamTask`（runtime 无 `task` 包也无 `StreamTask` 类）、`core/source/SimpleSourceSplit.java:18` 引用 `runtime.source.SourceSplit`（注释自述「The concrete is gone」）；引用有效但随结构演进需同步改写 3 处——`WindowAssigner.java:73`（指向 runtime 的 `WindowOperator`，WI7 搬迁后语义更迭）、`DeploymentAssignment.java:27`（`JobCoordinator#assignTasks()`，方法实际存在于 `JobCoordinator.java:831`）、`CheckpointBarrierTracker.java:54`（`runtime.checkpoint.PendingCheckpoint`，类存在）。本 WI 将上述 4 文件的 `{@link runtime.*}` 引用统一改写为文内可解析形式（纯文本描述或设计文档锚点），纯注释修改零风险；`WindowedStreamImpl.java:295-296` 的反射调用是代码级引用，归 WI7 消除。
- `docs-for-ai/04-reference/source-anchors.md` 的 STRM-018（WindowOperator 路径，WI7 搬迁后变更）与 STRM-019（IWindowOperatorFactory，路径不变、WI8 后描述更新）——本 WI 先登记预告，WI7/WI8 负责最终改写；`docs-for-ai/03-modules/nop-stream.md` 相关段落同步。避免本 WI 写两次。
- 验证：`node ai-dev/tools/check-doc-links.mjs --strict` exit 0；基线文档模块数与 `nop-stream/pom.xml` `<modules>` 计数一致（可用 grep 计数自查）；本 WI 完成后 core `src/main` 下 `io.nop.stream.runtime` 命中仅剩 `WindowedStreamImpl` 反射 1 处（归 WI7 消除）。

## Phase B——边界泄漏收敛（完成门：M1）

### WI4 connector-jdbc provided+optional 依赖硬化

> 本 WI 是**实证确认的潜伏运行时缺陷**，非风格问题。

- 事实：`nop-stream/nop-stream-connector-jdbc/pom.xml:36-47` 声明 `nop-message-debezium` 为 `provided` + `optional`（注释 :36-41、依赖块 :42-47），pom 注释称「pure INSERT-mode sinks never touch these types」；但 `JdbcTwoPhaseCommitSink.java:27` 在 **main** 作用域 import `ChangeEvent`，并在 `:483`、`:500`、`:546`、`:567` 四处 `instanceof` 解引用——其中 `:483` 恰位于 `dmlMode==INSERT` 分支内，坐实 INSERT 路径即触发类解析。
- 已实测验证：JVM 的 `instanceof` 会触发类解析。将被引用类从 classpath 移除后，`instanceof` 表达式抛出 `NoClassDefFoundError`（非惰性安全）。因此「纯 INSERT 模式不带 `nop-message-debezium` 运行」会在 `toDmlOp` 首次调用时炸——注释中的安全假设不成立。
- 两种处置，**由本 WI 的 plan 阶段做二选一裁定并记录理由**：(a) 改为 compile 作用域（`nop-message-debezium` 是 nop 平台自有模块，代价可控，且与 `connector-debezium` 的隔离初衷不同——此处隔离的是 CDC 引擎而非平台模块）；(b) 把 `ChangeEvent` 分支隔离到 `ICdcRecordDecoder` 之类的窄接口后，INSERT 路径零链接。倾向 (a)（改动面小、语义直白），(b) 仅在 plan 阶段实测证明 (a) 引入不可接受的依赖体积时启用。
- 补一个防回归测试：**不带** `nop-message-debezium` 的 classpath 下跑 `dmlMode=INSERT` 全路径。当前 8 个测试类全部在带该依赖的 test classpath 下运行，**无法捕获此类缺陷**——需 `maven-surefire-plugin` 的 `classpathDependencyExcludes` 或独立 profile 实现。注意：同模块中真正消费 CDC 分支的既有测试需以独立 surefire execution / profile 限定排除范围，避免防回归配置误伤——具体形态由 plan 阶段裁定。
- 验证：新防回归测试红（修复前）绿（修复后）；`./mvnw -o test -pl nop-stream/nop-stream-connector-jdbc,nop-stream/nop-stream-connector-debezium -am` 绿。

### WI5 runtime 测试侧 connector 反向 fan-in 收敛

> v1.1 复审修正：v1 的「唯一消费者是 TestStreamConnectorRegistryDiscovery」断言错误，两个原处置选项均建立在错误前提上，本节按重新普查结果重写。

- 事实（重新普查）：`nop-stream/nop-stream-runtime/pom.xml:98-221` 以 test 作用域依赖全部 5 个 connector（debezium :98-100 / batch :104-106 / kafka :112-113 / connector :209-211 / jdbc :219-221），另有 `rocksdb` :145-147 同在 test 扇入内；pom 注释 :89-95 自述「ALL FOUR」已过时（现 5 个）。消费者面：runtime 测试树有 **9 个文件直接 `import io.nop.stream.connector.*`**（jdbc ← TestE2EJdbcTwoPhaseCommitSink / TestCheckpointInheritedTwoPhaseCommitRestore / TestRecalculateBackfillE2E；batch ← TestBatchLoaderSourceE2E / TestRecalculateBackfillE2E；connector ← TestMessageSourceE2E / TestRateLimitValidationFace；connector.file ← TestFileSourceE2E / TestSourceApiRestoreE2E；kafka ← TestKafkaConnectorExactlyOnceE2E），另 2 个文件以字符串/FQN 形式引用（TestStreamConnectorRegistryDiscovery 经 registry 发现间接消费，唯一直接相关 import 是 `io.nop.message.debezium.DebeziumConfig`；TestWi19BoundedReplayDeclarationE2E）；仅 debezium 依赖无任何直接 import。各 test 依赖的 pom 注释已逐条记录其 E2E 动因（Stage 49 FileSource E2E、Stage 52 JDBC 2PC E2E、plan 2335 kafka kill-recover E2E 等）。设计意图面：`nop-stream/nop-stream-connector/pom.xml:14-29` 的注释（AR-2，编号系该 pom 注释自有，与 production roadmap 的 AR-2 无涉）明确「引擎不依赖具体 connector」——该意图在编译面成立，但在**测试面被完整 reintroduce**，并连带 reactor fan-in 扩大。
- 处置方向（plan 阶段二选一裁定，两个选项均以上述 9+2 消费面为前提）：(a) 将 connector 消费型 E2E 测试族整体迁至独立 IT 模块（参照仓库其他 IT 模块惯例），runtime 的 test classpath 彻底不含 connector；(b) 承认测试面 fan-in 为显式登记的例外：重写过时的 pom 注释（逐依赖列消费者与动因，修正「ALL FOUR」），把无直接消费者的 debezium 依赖（及裁定后确认无消费者的 rocksdb）收掉或门控，其余保留并在 pom 注释写明 AR-2 test-scope 例外与理由。
- 验证：处置后 `./mvnw -o test -pl nop-stream/nop-stream-runtime -am` 绿；`mvn dependency:tree -pl nop-stream/nop-stream-runtime` 确认 test 依赖面为「零 connector」（方案 a）或「直接消费者集 + 显式注释登记」（方案 b）；**本 WI 完成后重钉 runtime 测试基线（双值记录：surefire 用例通过数 + 测试文件数）**（WI6/WI7/WI10 引用的基线自本 WI 起替换为重钉值）。

### WI6 runtime 包级环打破与 wire DTO 叶子包提取

- 已验证的环：`transport → rpc → taskmanager → transport`
  - `transport/SubtaskPlanBuilder.java:21-22` import `rpc.RemotePipelineResolver` / `rpc.TaskDeploymentDescriptor`
  - `rpc/IStreamCoordinatorRpcService.java:15-18` import `coordinator.{JobStatusResponse,TaskProgress,TaskStatusReport}` 与 `taskmanager.CheckpointAckMessage`
  - `taskmanager/TaskManager.java:58-60` import `transport.{RemoteInputChannel,RemoteResultPartition,SubtaskPlanBuilder}`
- 第二个环：`coordinator ↔ taskmanager`（`coordinator/JobCoordinator.java:56-57` import `taskmanager.{CheckpointAckMessage,TaskManager}`；`taskmanager/TaskManager.java:53-54` 反向 import coordinator）。其中 `JobCoordinator.java:57` 的 `TaskManager` import 经验证**零使用**——它使任何耦合度扫描都误判 coordinator→TM 存在所有权边，本 WI 先删除该死 import。
- 处置：提取 5 个 wire DTO（`TaskDeploymentDescriptor`、`CheckpointAckMessage`、`TaskProgress`、`TaskStatusReport`、`JobStatusResponse`）到零依赖叶子包 `io.nop.stream.runtime.protocol`，使 `transport` / `rpc` / `taskmanager` / `coordinator` 共同依赖该叶子而不彼此成环。
- 顺带（低成本，同批处理）：`transport/KafkaStringWireCodec.java:24,36` 自述为 `PulsarStringWireCodec` 的逐字克隆（注释已登记为「deliberately deferred duplication」），抽共同基类；**12 处** `StreamMetricsRegistries.registry()` 内联调用绕过 `runtime.metrics` 包装层（`TaskManager.java:306,311,563,751`、`JobCoordinator.java:1921`、`ChannelQueueGauges.java:105,113`、`DbBacklogGauges.java:52`、`WindowOperator.java:622`、`OverWindowOperator.java:128`、`TaskCheckpointWiring.java:100`、`ops/StreamMetricsReporter.java:110`；包装层自身 4 处合法调用不计），统一走 `EngineMetrics` / `TaskNodeMetrics`。
- 验证：抽取后包级依赖图无环（可用 import 扫描脚本判定）；runtime 全量测试绿（**WI5 重钉后的基线**不得下降）；行为保持——wire DTO 为跨 JVM 传输类型，序列化格式不得变动。

## Phase C——窗口算子归位（完成门：M2）

### WI7 窗口算子实现包归位 core

> 本 WI 是本 roadmap 投入产出比最高的一项：一次搬迁消灭三层代偿中的两层。

- 事实：`core.operators` 根包 34 个类型文件（10 个根包内具体算子 + join 子包 EquiJoinOperator + 2 个抽象基类）承载全部算子实现，**唯独**窗口算子族住在 `runtime.operators.windowing`：17 个 main 文件 = `WindowOperator` + `OverWindowOperator` + 5 个 `NamespaceAware*` 状态适配器 + 2 个 KeyedStateStore（GlobalKeyedStateStore / PerWindowKeyedStateStore）+ `MergingWindowSet` + `WindowOperatorBuilder` + `WindowOperatorFactoryImpl`，外加 `functions` 子包 5 个 `Internal*WindowFunction`；测试侧 47 个文件。
- **可搬迁性（main 作用域已验证）**：`runtime/operators/windowing/*.java` 对 `io.nop.stream.runtime.*` 的 import 只有对自身子包 `operators.windowing.functions` 的引用，其余全部指向 `io.nop.stream.core.*`，即该包是 runtime 内的叶子。**test 侧有一处例外**：`TestIdlePeriodProcessingTimeWindowE2E.java:26` import 并在 `:207` 调用 `runtime.execution.GraphModelCheckpointExecutor.executeWithCheckpoint`——该测试不得随包搬入 core（否则制造 core→runtime 测试依赖、违反本 WI 自身的不变式钉），plan 阶段裁定其留守 runtime（改造为经 core 入口驱动）或随包迁移并重写，二选一。另 runtime.windowing 包下的 `TestE2EWindowAllowedLatenessFullAggregate` 为窗口语义同族 E2E 但位于搬迁包之外（47 文件口径不含它），其归属随上一步一并裁定（留守 runtime 不违反任何不变式钉）。
- 处置：main 17 文件搬迁至 `io.nop.stream.core.operators.windowing`（与既有的 `core.operators.join` 同构）；test 侧按上一条裁定搬迁（该 E2E 例外除外）。
- 搬迁后 core 侧同步：(a) `WindowedStreamImpl.java:295-296` 的 `Class.forName("io.nop.stream.runtime.operators.windowing.WindowOperatorFactoryImpl")` **删除**，改为直接构造——core 与实现同模块后无需任何兜底；(b) 复核 WI3 已收敛的 `WindowAssigner.java:73-74` Javadoc 改写随搬迁仍语义正确；(c) `nop-stream-invariant-loop-roadmap.md` 的 P2-01-03（core 反向软耦合）与 P2-23（反射类名契约未记录）在本 WI 落地后销号，按 E4 双向记录——该 roadmap 引用 `WindowedStreamImpl` 旧行号的条目锚点随搬迁漂移，销号时同步重钉。
- `TestWindowedStreamFactoryFailFast` 修订：该测试已用空 `StreamComponents` 桩构造 null-factory 路径（:43-47，javadoc 自述 without relying on classpath exclusion），搬迁后真正失效的只是 `:50` 的错误消息断言文本（contains "nop-stream-runtime on classpath"）与 `:28` 的过时 javadoc——改写消息文本与 javadoc 即可，保留 fail-fast 契约本身的覆盖；如需编程构造可用 `WindowedStreamImpl.setDefaultFactory(null)`（注意：`setDefaultFactory` 在 `WindowedStreamImpl` :283，`StreamComponents` 无此 setter）。
- 验证：`./mvnw -o test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-flow -am` 全绿（core 282 / flow 66 基线不得下降；runtime 以 WI5 重钉后基线为准，若 E2E 例外留守则按裁定相应调整）；全仓 `grep -rn 'io.nop.stream.runtime'` 在 `nop-stream-core/src` 下命中数为 **0**（main 与 test 均须为 0：注释半边已由 WI3 归零、反射半边由本 WI (a) 消除、test 半边搬迁前为 0——三段归属链齐备后此钉才可达，此为不变式钉）；行为保持——窗口语义、状态布局、timer 触发、序列化格式零变更。

### WI8 IWindowOperatorFactory 收敛为 WindowSpec 单一契约

- 事实：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/IWindowOperatorFactory.java` 共 342 行 / **17 个方法**，其中 4 个抽象 + **13 个** `default` shim。形态是典型的参数蠕变：4 种操作 × 4 代新增参数（`elementType` → `lateDataOutputTag` → `maxDelay`/`emitWindowEvents`/`windowEventsOutputTag` → `AccumulationMode`），每一代 `default` 都丢弃新参数，迫使 `WindowOperatorFactoryImpl`（**642 行**）逐个覆写全部 13 个 default（连同 4 个抽象共 17 个方法全部覆写）。
- 处置（在 WI7 完成后执行）：引入 `WindowSpec` 不可变值对象（assigner / trigger / evictor / allowedLateness / keySelector / elementType / outputTag 族 / maxDelay / emitWindowEvents / accumulationMode 等），`IWindowOperatorFactory` 收敛为单一 `create(WindowSpec)`（或按操作分 4 个方法但全部单参 `WindowSpec`）。13 个 `default` shim 与 642 行适配器的大部分随之删除。
- 兼容面：`IWindowOperatorFactory` 是 `core` 的公开类型，`StreamComponents.java` 与 `WindowedStreamImpl` 均引用它。需在 plan 阶段裁定是否保留一个 `@Deprecated(forRemoval=true)` 的旧接口适配层过渡（参照仓库既有先例：`nop-ai-core` 废弃 chat API 的 `@Deprecated(forRemoval=true)` 迁移，`io.nop.ai.core.api.chat` 的 IAiChatSession 等）。**不得为省事而跳过迁移裁定**。
- 连带清理：`StreamModelDslBuilder` 与 `WindowedStreamImpl` 的调用点同步改写；销号 `nop-stream-production-roadmap.md` 的 P2-6（IWindowOperatorFactory performative Class 参数——与本 WI 同接口同病灶），按 E4 双向记录；`docs-for-ai/04-reference/source-anchors.md` 的 STRM-019 描述更新。
- 验证：全量三模块测试绿；`IWindowOperatorFactory` 方法数从 17 降至 ≤4（可用 grep 自查并作为收口钉）；新增一个「参数蠕变防复发」测试：构造完整 `WindowSpec` 断言全部字段无损传递到算子（防止未来的 default shim 静默丢参——这正是当前设计的固有失败模式）。

## Phase D——双 checkpoint 驱动面统一（完成门：M3）

### WI9 双 checkpoint 驱动面重叠测绘与收敛设计裁定

> **本 WI 是设计裁定 WI，不改生产代码。** 输出为 ai-dev/design/nop-stream/checkpoint-engine-convergence.md（WI 阶段新建）。按 AGENTS.md「框架核心引擎变更需 plan-first + 设计文档 + 回归测试」，本 WI 产出设计文档后需 owner 确认方可进入 WI10。

- 事实（v1.1 复审修正：v1 的「两套独立实现、互不委托」与代码不符）：分布式路径由 JobCoordinator 驱动 `runtime.checkpoint.CheckpointCoordinator`（**1890 行**）；本地/savepoint 路径经 `CheckpointExecutorFactoryImpl` + `META-INF/services/io.nop.stream.core.execution.ICheckpointExecutorFactory` SPI 暴露 `runtime.execution.GraphModelCheckpointExecutor`（**1872 行**）。GMCE 并非独立第二引擎：其内部构造并内嵌 `CheckpointCoordinator`（`:668` `new CheckpointCoordinator(...)`），trigger/restore/manifest 核心委托该 coordinator（GMCE 对 CheckpointCoordinator 的 26 处提及中 ≥11 处为真实委托调用：`triggerCheckpointBounded` :432/:548/:790、`restoreLatestEpochManifest` :1061、`restoreFromCheckpoint` :1103、`getCurrentFingerprint` :1192-1193、`setCurrentFingerprint` :244 等）；反向提及仅 1 次（CheckpointCoordinator `:1223` javadoc）。真正的重叠不在内核而在**驱动面**：分布式编排（JobCoordinator 侧）与本地编排（GMCE 的 `executeWithCheckpointSkeleton` 族）各自实现 trigger 时机、restore 后状态重建、savepoint 触发、终止模式等外围语义，且无跨驱动面一致性测试钉——exactly-once 的「分布式恢复对、本地恢复错」风险正源于此。
- 测绘产出（逐项对照表）：两驱动面在 trigger 语义、barrier 传播、epoch manifest 格式、savepoint 触发条件、commit/abort 协议、restore 路径、终止模式（Drain/Suspend/Export）七个维度上的**逐条差异清单**，每条标注：完全一致 / 语义等价实现不同 / **真实语义分叉**；并显式列出两条加载链（services SPI 的加载方 = core 侧 StreamExecutionEnvironment 经 ServiceLoader；分布式侧由 JobCoordinator 直驱、不经该 SPI），避免测绘执行时重复排查。
- 收敛方案裁定（plan 阶段产出，需 owner 确认）：候选至少两条——(a) 下沉共享内核：以 `CheckpointCoordinator` 为事实内核，把仍散落在两驱动面的外围语义（触发计划、epoch manifest 编解码、savepoint 编解码）继续下沉，两驱动面变薄但保留；(b) 统一为单一驱动面引擎，本地模式作为其一种驱动形态。裁定须回答：哪条路径的语义是权威？分叉项以谁为准？迁移期双跑校验怎么做？
- 风险声明（必须写进设计文档）：这是本 roadmap 中**唯一有真实正确性风险**的项。两驱动面语义若在收敛中被反向污染（把证据覆盖更强的分布式侧已验证语义错改为未充分验证的本地侧变体，或反之），错误在单侧回归下不可见。因此 WI10/WI11 必须以「双驱动面在同一输入下产出等价结果」的差分测试为门，不得以单侧回归通过为完成标准。
- 与既有裁定的边界：R5 已登记 `CC-06`（coordinator monitor 内阻塞 RPC fan-out）与 `CC-17`（无界队列 + 终态 RPC 同步发送）为 checkpoint 编排并发重构，属 WI10 落地时的**顺带修正项**（若差分归零工作延至 WI11 则顺延），不在本 WI 重复立项。

### WI10 共享 checkpoint 内核抽取

- 依 WI9 的裁定结论，抽取/下沉共享内核。若裁定为方案 (a)：新包 `runtime.checkpoint.core`（或等价位置）承载触发计划、epoch manifest 编解码、savepoint 语义等仍散落在两驱动面的共用部分；两驱动面各自的驱动形态（分布式协调 vs 本地嵌入）保留。
- **行为保持硬约束**：checkpoint 存储格式、序列化字节、epoch manifest 结构**零变更**——存量 checkpoint 必须能被新代码 restore。落实 E1 的先钉后拆：差分测试先对齐**现状**（重构前）跑绿留档，再动内核。WI9 测绘表中「完全一致 / 语义等价」的部分做纯搬迁；「真实语义分叉」的部分按裁定归一，并在设计文档中记录归一方向与理由。
- 顺带落地 R5 的 `CC-06`（checkpoint 触发/ACK 的 N×阻塞 RPC fan-out 移出 monitor——注意非 retention I/O，retention 出 monitor 已由先前工作落地）与 `CC-17`（有界队列 + 背压）。**顺序约束**：内核抽取与差分归零先行；CC-06/CC-17 属并发行为修改，以独立 commit 在差分绿之后落地，各带定向测试（落地前先核对既有 `TestCommitFanoutOffMonitor2326` 的覆盖面），不得与行为保持部分混在同一判定批内。
- 验证：差分测试——同一作业分别在分布式路径与本地路径执行，epoch manifest 逐字段一致、恢复后状态逐 key 一致；全量 runtime 测试绿（**WI5 重钉后的基线**）；**存量 checkpoint 兼容性测试**：仓库现无跨版本 checkpoint fixture（test resources 无 checkpoint/manifest 类文件），需在本 WI 首次固化「旧版本写出」的 checkpoint fixture 并入库，经新代码 restore 成功（可参照 TestRocksDBSnapshotRestore / TestCheckpointSerDeConsistency / TestGraphModelCheckpointExecutor 的 savepoint restore 用例形态）。

### WI11 分布式路径切换至统一内核

- 依 WI9/WI10 的结果，将分布式路径切换到统一后的驱动面（或在方案 (a) 下完成两驱动面对共享内核的完全对齐）。
- 切换必须分帧：先双跑校验（两驱动面并行产出、比对 manifest 与恢复结果，差异归零），再摘除旧路径。**不允许一次性切换**。
- 顺带：`runtime.execution` 包承载 6 类职责（dispatcher 双实现 / 内嵌 checkpointing / supervision / rescale assembly / TM 侧 wiring / plan 生成 + storage 工厂），扇出 **7 个** runtime 顶层包（checkpoint / cluster / coordinator / rpc / source / taskmanager / transport），是模块内最繁忙的枢纽。Checkpoint 相关部分随本 WI 迁出后，评估该包是否达到可拆条件（拆为 `execution.{dispatcher,supervision,rescale,wiring}`，并把 GMCE 内的 storage 工厂 `createStorage`（`GraphModelCheckpointExecutor.java:921-941`）迁入**既有**的 `runtime.checkpoint.storage` 包——当前 checkpoint storage 构造散落 6 处（execution 5：GMCE :375/:1214/:1228、EmbeddedDistributedExecutor :260、RpcDistributedExecutor :220 + launch 1：JobCoordinatorMain :137），全部绕过工厂直接 `new LocalFileCheckpointStorage`）。**本 WI 只做「迁出后是否值得拆」的评估与裁定，实际拆分另立 WI 或登记为 backlog**，避免本 WI 失控膨胀。
- 验证：分布式 E2E 场景（fraud-example 的 S1/S2 多 JVM gated 场景）全绿；exactly-once 断言不弱化；`./mvnw -o test -pl nop-stream/nop-stream-runtime -am` 绿。

## Phase E——收口（完成门：WI12 验证全绿 + 独立 closure audit）

### WI12 全量收口与门禁固化

- 全量回归：`./mvnw clean install -T 1C`（含 nop-stream 全部 13 模块 + 下游消费者）；gated 多 JVM 场景按 `ai-dev/design/nop-stream/distributed-runbook.md` 的手册单独跑并留档。
- 门禁固化（在 `.github/workflows/compliance.yml` 补步，参照既有 `xdsl-scenarios` 步骤形态）：
  - `core → runtime` 反向依赖扫描：`nop-stream-core/src` 下 `io.nop.stream.runtime` 命中数必须为 0，失败即阻塞。**这是本 roadmap 最值得长期保留的一条不变式**——它把「core 不依赖 runtime」从文档约定变成 CI 可执行检查。
  - runtime 包级环扫描：对 `transport` / `rpc` / `taskmanager` / `coordinator` 四包做 import 方向扫描（WI6/M1 时以一次性脚本消除环，本步把同一扫描固化为 CI 检查防复发，同时兑现 M1「CI 门禁固化于 WI12」的承诺）。
  - connector test fan-in 断言（条件步）：若 WI5 裁定为方案 (a)，加 `mvn dependency:tree -pl nop-stream/nop-stream-runtime` 断言 test 依赖面零 connector；方案 (b) 则断言 pom 注释登记完整（无直接消费者的依赖不出现）。
  - package-info 覆盖检查（可选、低成本）：core 现有 5 个、runtime **0 个** `package-info.java`；补齐 runtime 各包的边界说明（尤其 WI6 新增的 `runtime.protocol`）。
- 文档同步收口：`01-architecture-baseline.md` 的模块表与依赖方向表最终形态（WI3 是预告，本步是终稿）；`docs-for-ai/03-modules/nop-stream.md`；`docs-for-ai/04-reference/source-anchors.md` 的 STRM-016—019 路径锚点；`docs-for-ai/01-repo-map/module-groups.md` 的 nop-stream 行（当前自述「共 11 个」与 13 模块实况有出入）。
- 登记遗留：`runtime.source` 包（`CollectionReplayableSource` / `TimeRangeReplayableSource`，2 文件 286 行，**零 runtime 依赖**，纯 `SourceFunction` 实现，但其契约被 `core/exceptions/NopStreamErrors.java:589` 与 `core/checkpoint/SuspendDataPolicy.java:30` 以 core 侧 Javadoc 引用）错置于 runtime——裁定归位 core 或明确登记为 runtime 内置 replayable source 基线，二选一并落档。
- 验证：`node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`node ai-dev/tools/check-nop-stream-invariants.mjs` exit 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 无新增；独立 closure audit 通过后方可将本 roadmap 宣告收口。

## 裁定记录摘要

本 roadmap 不复制被引设计文档的正文。补充五条执行级口径：

- **E1 先钉后拆**：WI7 / WI8 / WI10 / WI11 全部是行为保持型重构，沿用 plan 359 已验证的方法论——行为保持型结构变更以**既有测试基线为钉**（重构前后跑同一套断言，通过数与覆盖面不得低于基线）；需新增行为覆盖的部分先落聚焦测试再动结构（承接前序 plan 358 的做法）。不接受「重构后测试也绿」作为完成证据。
- **E2 不为覆盖率而重构**：本 roadmap 不以「消除包级循环」为独立目标。环本身不是缺陷，环所**掩盖的真实问题**才是——WI6 的环掩盖了 wire DTO 归位问题，WI7 的切口掩盖了 core→runtime 软耦合，WI9 的双驱动面掩盖了恢复语义分叉风险。拆分只是手段。
- **E3 分叉项以分布式路径为权威的默认假设**：WI9 若测绘发现真实语义分叉且无法从代码与设计文档判定哪侧正确，**默认以分布式路径为准**（它有 R1—R5 多轮审计与 gated 多 JVM 场景的更强证据覆盖），并将该分叉升级为独立缺陷项而非在重构中顺手「统一」。
- **E4 与前序 roadmap 的销号联动**：WI7 落地时销 `nop-stream-invariant-loop-roadmap.md` 的 P2-01-03 / P2-23；WI8 落地时销 `nop-stream-production-roadmap.md` 的 P2-6；WI10 顺带收 R5 的 CC-06 / CC-17。每处销号须在两份 roadmap 中双向记录，避免出现「一处 done 一处 todo」的僵尸状态。
- **E5 quickstart 不进 reactor 是设计而非遗漏**：`quickstart/` 有意不在 `nop-stream/pom.xml` 的 `<modules>` 中（其 `nop-stream/quickstart/template/pom.xml` 是含 `@groupId@` 占位符的非工程，物理上无法进 reactor）。WI2 的目标是让它在**仓库外**可跨平台跑通，不是把它塞进构建。
