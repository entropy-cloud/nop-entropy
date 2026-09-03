# 连接器生态产品化：SPI 注册中心 + 能力矩阵机制 + OLAP 端最小集裁定（roadmap item 19 / P-REQ-28）

> Plan Status: completed
> Mission: nop-stream-productization
> Work Item: item 19 连接器生态产品化
> Last Reviewed: 2026-09-03
> Source: `ai-dev/analysis/2026-09/2026-09-01-competitor-productization-synthesis-and-p-req.md` §2.2 P-REQ-28（要求与验收标准）+ §2.4 tis 建议①映射（插件市场显式不采纳、SPI=IoC bean 形态已裁定）+ §3.2 tis 报告 Open Question 2 归属本 item；`ai-dev/analysis/2026-09/2026-09-01-seatunnel-productization-analysis.md`（连接器生态组织方式参照）；`ai-dev/analysis/2026-08/2026-08-14d-tis-vs-nop-data-integration-comparison.md`（~45 端类型 + Jenkins 式插件体系证据）
> Related: `2026-09-03-0830-2-pre-submit-validation-productization.md`（item 20 在本 plan 之后执行，其 dry-run/conf-validate 应消费本 plan 的注册中心与能力描述面——执行顺序 1 → 2）；`2026-09-01-1457-2-connectors-module-audit.md` §2.1 H-6（builder 级统一入口事实）

## Purpose

把 nop-stream 连接器生态从「模块化存在但无注册发现机制」（4 个连接器模块、XDSL 仅 bean 引用接线、无 SPI 注册中心）推进到「SPI 注册 + 能力矩阵机制 + OLAP 扩展裁定落档」：P-REQ-28 达到其验收标准（注册接口 + 注册发现测试存在；OLAP 端扩展裁定记录落档），并把 tis 报告 Open Question 2（Delta 作为市场替代机制）的悬置裁定收口。

## Current Baseline

（2026-09-03 live 核对）

- 连接器资产：4 模块 10 组件（`nop-stream-connector`：FileSource/FileSourceReader/FileTwoPhaseCommitSink/MessageSourceFunction/MessageSinkFunction；`connector-jdbc`：JdbcTwoPhaseCommitSink(+Builder)；`connector-debezium`：DebeziumCdcSourceFunction；`connector-batch`：BatchLoaderSourceFunction/BatchConsumerSinkFunction）。能力矩阵已人工文档化：`docs-for-ai/03-modules/nop-stream-connectors.md`（item 17 交付，2026-09-03 全量复核，行为级测试锚定）——**文档面已有，代码级注册/发现机制无**。
- SPI 现状：`rg "IStreamSourceFactory|IStreamSinkFactory"` 零命中（P-REQ 报告 §2.6 抽查，至今无变化）；XDSL source/sink 实例化落点 = `StreamModelDslBuilder.buildSource/buildSink`（flow 模块），仅支持 bean 引用（`beanResolver.resolve(t.getBean(), SourceFunction.class)`）或内联 xpl 两种形态；`<source>` 上的 `params`/`maxParallelism`/`outputType`/非默认 `consistencyCapability` 与 `<sink>` 上的 `params`/`maxParallelism`/`inputType`/非默认 `consistencyCapability` 均走 FL-1 fail-fast（`failFastOnUnsupportedSourceConfig`/`failFastOnUnsupportedSinkConfig`，`ERR_STREAM_NOT_IMPLEMENTED`，无消费路径；FL-2 是 per-transform parallelism fail-fast，属 item 29——勿混淆）。
- 能力契约现状：函数级能力声明已存在——`getSourceConsistency()`/`getSinkConsistency()` + `SourceConsistencyCapability`/`SinkConsistencyCapability` 枚举（core/common/functions）；2PC sink 并行度门禁（`ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED`，规划期 fail-fast）已存在。缺的是**注册中心级的工厂发现 + 能力矩阵聚合机制**。
- NopIoC 约束（AGENTS.md）：bean 只经 `_vfs` 下 `beans.xml` 发现，无注解扫描；connector 各模块当前**无任何 beans.xml**（仅 runtime 有 stream-control-rpc/stream-data-plane 两个）。
- 竞品参照（报告引用，不重开分析）：SeaTunnel 74 连接器模块 + factory/discovery 组织（`2026-09-01-seatunnel-productization-analysis.md`）；tis ~45 端类型 + Jenkins 式插件市场（08-14d）。综合报告 §2.4 已裁定：**Jenkins 式运行期插件市场/市场安装机制显式不采纳**（Nop 以 NopIoC + Delta 定制为扩展机制），SPI 形态 = IoC bean。
- tis 报告 Open Question 2（Delta 作为连接器市场替代机制）未决，归属本 item 裁定（综合报告 §3.2 显式记录该报告因 3 条 Open Questions 保持 open，其中第 2 条等本 item 收敛）。
- fraud-example 场景（S1/S2）以 bean 引用声明连接器（`fraud-s1-cdc.stream.xml` / `fraud-s2-file.stream.xml`）；**连接器 bean 不在 beans.xml——由测试支持代码程序化注册**（`ScenarioTestSupport`/`DistributedScenarioSupport` 的 `BeanFunctionResolver.register(...)`，fraud-example main 侧无任何 beans.xml）。结构性事实：现有 XDSL bean 路径（程序化 resolver / `GlobalBeanFunctionResolver`→NopIoC）与「SPI = NopIoC beans.xml 承载」是两套解析世界，Phase 1 设计注册中心与 builder 互通时须知悉并裁定衔接方式。
- 组件资格事实：10 组件中 `FileSourceReader` 由 `FileSource.createReader()` 内部构造（非独立实例化端点）、`JdbcTwoPhaseCommitSinkBuilder` 为 fluent builder（矩阵中方向/并行度列为「—」）——注册资格集需 Phase 1 裁定（预期 8 个端点组件可注册，Reader/Builder 显式不注册）。

## Goals

- P-REQ-28 验收达成：连接器 SPI 注册接口落码（`IStreamSourceFactory`/`IStreamSinkFactory` 等价物，NopIoC beans.xml 承载）+ 注册发现测试存在（能枚举全部已注册连接器工厂及其能力）。
- 既有 4 模块的全部**端点组件**（资格集 Phase 1 裁定，预期 8 个：FileSource/FileTwoPhaseCommitSink/MessageSourceFunction/MessageSinkFunction/JdbcTwoPhaseCommitSink/DebeziumCdcSourceFunction/BatchLoaderSourceFunction/BatchConsumerSinkFunction）经 SPI 注册（向后兼容：既有 bean 引用/XDSL 场景不受影响；Reader/Builder 等非端点组件显式不注册并记录理由）。
- 能力矩阵机制落码：工厂声明能力描述（方向/交付语义/并行度/恢复语义等，与 `nop-stream-connectors.md` 人工矩阵的字段对齐），注册发现测试断言能力声明。
- OLAP/数仓端连接器最小集裁定落档：ClickHouse/Doris/StarRocks/Hive/Paimon 等逐项 go-minimal（本期交付）/defer（附条件与 revisit 触发点）/exclude（附依据），记录落 connector owner doc 或本 plan 派生裁定记录，并写回 roadmap（如产生新 Follow-up）。
- tis 报告 Open Question 2 收口：Delta 定制作为连接器市场替代机制的裁定落档（采纳/部分采纳/不采纳 + 依据），tis 报告 Status 相应收敛。

## Non-Goals

- Jenkins 式运行期插件市场、热安装、动态下载（综合报告 §2.4 已显式不采纳，不重新评估）。
- 本期不新增 OLAP 连接器实现，除非 Phase 1 裁定最小集含「本期交付」项且证据支持（默认预期为 defer 分期——见 Phase 1 必答题；若裁定交付，实现范围限于裁定记录的最小集）。
- dry-run 连通性验证 / 凭据加密 / conf-validate 命令（P-REQ-13/14 → item 20，本 plan 之后执行）。
- flow DSL 编译器收敛（item 29：xpl source 取消语义、build 期错误源位置、per-transform parallelism 消费——本 plan 的 SPI 不替代也不实现这些）。
- SQL/Table API、批流统一管道（vision non-goal / 综合报告 §2.4 显式拒绝）。
- 既有连接器行为语义变更（交付语义/恢复语义/门禁均不变；本 plan 是注册发现层增量）。

## Scope

### In Scope

- SPI 接口设计裁定与落码（工厂族、能力描述符、注册载体、发现语义、与既有 bean 引用路径的关系）。
- 既有 4 模块 10 组件的注册接入 + 注册发现测试。
- XDSL 消费路径裁定与最小接线（见 Phase 1 必答题；无论裁定结果如何，注册中心必须有 ≥1 个仓库内运行时消费者并被测试验证——Anti-Hollow）。
- 能力矩阵机制（代码级能力声明 + 与 `nop-stream-connectors.md` 的对齐方式裁定与同步）。
- OLAP 最小集三态裁定 + tis OQ-2 裁定 + 文档/roadmap 写回。

### Out Of Scope

- 上列 Non-Goals 全部；item 20 的校验工具面；runtime 模块治理（items 25—28）。

## Execution Plan

### Phase 1 - 裁定与设计基线

Status: completed
Targets: `ai-dev/design/nop-stream/connector-design.md`（owner doc，架构决策追加）、OLAP 裁定记录落点（Phase 1 裁定）

- Item Types: `Decision`

- [x] **SPI 形态裁定**（必答题，每项含拒绝替代方案）：① 工厂接口族划分（source/sink 分立 vs 单一工厂；工厂创建物 = 既有 `SourceFunction`/`SinkFunction`/FLIP-27 `Source` 端点实例，不引入新执行契约）+ **注册资格集**（哪些组件可注册：预期 8 个端点组件；`FileSourceReader`（由 `FileSource.createReader()` 内部构造）/`JdbcTwoPhaseCommitSinkBuilder`（fluent builder，非端点）等显式不注册并记录理由）+ **类型名命名空间与别名清单**（类型名若成为用户面契约须 Phase 1 定名，不留既成事实）；② 注册载体 = 各连接器模块 `_vfs` beans.xml 中的 bean 定义（NopIoC 发现，无注解扫描）+ **注册中心聚合机制**（registry 如何获得工厂集合：NopIoC 多 bean 注入 vs 容器按类型查询等——影响跨模块接线与测试容器搭建）+ 发现语义（按类型名/别名解析）+ **注册中心接口的模块落点**（core vs connector 汇聚模块，含依赖方向合规）+ 与既有 XDSL bean 解析世界（程序化 `BeanFunctionResolver` / `GlobalBeanFunctionResolver`→NopIoC）的衔接方式；③ 能力描述符的字段集（至少覆盖 `nop-stream-connectors.md` 矩阵的既有列：方向/交付语义/并行度/恢复语义）与既有函数级 `getSourceConsistency()`/`getSinkConsistency()` 及 2PC 并行度门禁的关系（单一事实源原则——避免能力声明与门禁两套逻辑漂移）+ **能力声明与文档矩阵的一致性核对口径**（哪些列须结构化相等、哪些列文档专属——供 Phase 2 注册断言与 Phase 3 同步复核对齐）+ **文档同步机制裁定**（文档标注代码锚点 vs 生成 vs 复核清单——Phase 3 消费）；④ 未知连接器类型名的解析语义 = fail-fast typed 错误（含类型名与可选清单），不允许静默回落 bean 路径
  — *落档 connector-design.md §8.1 D1 / §8.2 D2 / §8.3 D3 / §8.4 D4 / §8.6 D6，各含拒绝替代方案*
- [x] **XDSL 消费路径裁定**（必答题）：SPI 注册中心是否引入 XDSL 类型化连接器声明（如按类型名 + 参数构造，替代/并列于 bean 引用）；若引入——`<params>` 的消费路径必须一并裁定（消除 FL-1 fail-fast 中的 params 项 vs 维持 fail-fast + 显式指引），**且** source/sink 两侧全部既有 FL-1 拒绝面（source：`outputType`/`maxParallelism`/`consistencyCapability`；sink：`inputType`/`maxParallelism`/`consistencyCapability`）与注册中心能力描述符的关系必须裁定（冗余声明？冲突 fail-fast？维持 FL-1 拒绝？——`maxParallelism` 的运行时消费属 item 29 边界，本 plan 不得越界实现运行时重分片语义）；`bean` 与类型名**同时声明**的冲突语义（fail-fast vs 优先级）必须裁定；若裁定本期不引入（仅注册 + 供消费），运行时消费者从**预绑定候选集**中指定：维护/校验工具入口（仅注册清单枚举与能力探测，**显式不做字段级 conf 校验**——那是 item 20 的 scope 边界，避免重复建设），且该工具入口的形态与落点模块（CLI 子命令归属哪个模块）一并裁定。**无论何种裁定，禁止「注册中心存在但无任何运行时消费者」的空壳形态**
  — *落档 §8.5 D5：本期不引入；FL-1 维持；消费者 = StreamConnectorCatalog（core）；CLI 归 item 20；类型化预案记录不实施*
- [x] **OLAP/数仓端最小集裁定**（P-REQ-28 验收必答）：ClickHouse/Doris/StarRocks/Hive/Paimon（及裁定中识别的其他候选）逐项 go-minimal/defer/exclude；对照 SeaTunnel 74 模块与 tis ~45 端的组织方式给出分期建议；依据至少含：现有用户需求证据（roadmap Phase S 场景均未涉及 OLAP 端）、JDBC 2PC sink 既有形态的可复用性（ClickHouse/Doris 等有 JDBC 驱动者的边际成本）、维护成本。裁定记录落档并写回 roadmap（新增 Follow-up 或维持 item 19 关闭，按 Rules）
  — *落档 §8.7 D7：defer×4 + exclude×2（Iceberg 为识别候选）；本期 go-minimal 集为空；roadmap 不追加无需求 Follow-up（defer 为需求门控）*
- [x] **tis OQ-2 裁定**（必答）：Delta 定制（`x:extends` 拓扑级/参数级覆盖）作为「连接器市场」替代机制的适用边界——哪些连接器配置面适合 Delta 覆盖、哪些必须走工厂参数；落 connector-design.md
  — *落档 §8.8 D8：部分采纳（配置面适用/本体分发不适用）；tis 报告 OQ-2 条目已写回收敛*
- [x] 设计文档更新（connector-design.md 追加 SPI 注册与能力矩阵的架构决策章节；仅决策与契约，不含实现级类签名/代码）；README 索引核对
  — *§8 全章 + header Revised + design README 集成层条目与编号清单更新*

Exit Criteria:

- [x] 四项必答题全部有裁定记录且各含拒绝替代方案；OLAP 裁定逐候选三态 + 依据可追溯（§8.1—§8.8 + §8.9 汇总表；tis 报告 OQ-2 写回）
- [x] connector-design.md 更新落盘且不含实现级签名/伪代码（guide 规则 14）；doc-links 通过
- [x] **无静默跳过**：本 phase 为纯决策，无新增公共方法分支（不适用，显式声明）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - SPI 注册中心落地与既有连接器注册

Status: completed
Targets: `nop-stream/nop-stream-core/`（SPI 接口与注册中心抽象，若 Phase 1 裁定放 core）+ 4 个连接器模块（beans.xml 注册 + 工厂适配）

- Item Types: `Fix | Proof`

- [x] SPI 接口与注册中心落码（按 Phase 1 裁定形态与模块落点）；未知类型名 fail-fast typed 错误（含 `ARG_*` 参数：类型名、已注册清单）
  — *core 新包 `io.nop.stream.core.connector.registry`：三工厂契约 + 描述符/参数/枚举 + `StreamConnectorRegistry`（of/fromContainer 聚合 + 构建期校验）+ `StreamConnectorCatalog` 工具入口 + `StreamConnectorConfig`；新错误码 6 个（type-not-found/direction-mismatch/duplicate-type/descriptor-invalid/param-required/capability-mismatch，全部带 ARG 参数）*
- [x] Phase 1 裁定的资格集内全部端点组件（预期 8 个）注册：每组件一个工厂 bean + 能力描述符声明，能力值按 Phase 1 核对口径与 `nop-stream-connectors.md` 既有矩阵对齐——不一致处要么修代码声明要么修文档并在日志记录裁定；资格集外组件（Reader/Builder 等）的「不注册 + 理由」记录随裁定落档
  — *8 工厂类 + 5 个 beans.xml（connector-{file,message,jdbc,debezium,batch}.beans.xml，base 模块贡献 file+message 两件——按家族命名细化设计文本已同步）；能力值与矩阵结构化相等列全对齐（发现测试钉定）；不注册理由落 §8.1*
- [x] 注册发现测试：枚举断言（已注册类型清单、每类型能力字段断言，含 2PC 门禁语义的能力表达）；未知类型名错误测试（错误码 + 参数断言）；按类型名解析并构造出端点实例的用例（证明注册→解析→构造链在无消费者前即可独立验证）
  — *三层：`TestStreamConnectorRegistry`（core，13 用例：枚举/别名优先级/重复拒绝/描述符校验/未知类型/方向错配/必填参数）+ 每模块工厂测试（`TestFileMessageConnectorFactories` 11 / `TestJdbcConnectorFactory` 3 / `TestDebeziumConnectorFactory` 3 / `TestBatchConnectorFactories` 4，含单一事实源实例级对齐断言）+ `TestStreamConnectorRegistryDiscovery`（runtime，8 用例：5 beans 文件发现/8 组件全量枚举/能力矩阵逐项断言（§8.4 对齐记录）/8 组件 catalog probe 构造/未知类型含全清单/方向错配/renderListing）*
- [x] 向后兼容验证：既有 bean 引用路径（fraud-example S1/S2 XDSL 场景测试）不经 SPI 仍原样通过
  — *`./mvnw test -pl nop-stream -am -T 1C` 全绿（含 fraud-example 16.4s / runtime 35.0s 全模块通过）；注册中心与 GlobalBeanFunctionResolver 两世界零交互（设计 §8.3）*

Exit Criteria:

- [x] 注册发现测试存在且断言资格集内全部端点组件（P-REQ-28 验收原文「SPI 注册接口 + 注册发现测试存在」）；未知类型名显式错误用例存在
- [x] 按类型名解析→构造实例链经测试验证（本 phase 可独立验证的接线证明；注册中心的**运行时消费者**接线验证归 Phase 3 Exit Criteria——消费者实体在 Phase 3 落地，此处不提前要求）
- [x] 能力声明单一事实源原则经测试或代码审查确认（无第二套漂移逻辑）；一致性核对口径（Phase 1 ③）下的对齐记录存在
  — *实例级：每模块工厂测试断言描述符值 ≡ 端点实例 getXxxConsistency()；2PC 门禁：probe 运行期断言 PLANNING_GATE_PARALLELISM_1 ⟺ instanceof TwoPhaseCommitSinkFunction；对齐记录 = 发现测试 testCapabilityMatrixAlignedWithDesignAdjudication + 测试类 javadoc 声明其为 §8.4 代码锚点*
- [x] **无静默跳过**：能力描述符缺失字段/未注册类型均为显式错误或显式默认语义，无静默 null/空实现
  — *描述符构造器 null/方向一致性校验（IllegalArgumentException）+ 注册期 typed error；未知参数不拒绝 = 显式边界裁定（item 20），非静默跳过（记录于工厂契约 javadoc 与 §8.3）*
- [x] **新功能必有测试**：列出注册发现/未知类型错误/能力断言的新增测试用例名
  — *TestStreamConnectorRegistry（13）、TestFileMessageConnectorFactories（11）、TestJdbcConnectorFactory（3）、TestDebeziumConnectorFactory（3）、TestBatchConnectorFactories（4）、TestStreamConnectorRegistryDiscovery（8）*
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] owner-doc 裁定：connector-design.md 已在 Phase 1 更新（若 Phase 2 实现与裁定有偏差，回写裁定）
  — *偏差 1：beans.xml 按家族命名 5 件（非 4 件）→ §8.3 已同步；偏差 2：聚合发现测试落 runtime（非 fraud-example）——runtime 已有 nop-ioc test-scope + surefire init cap 基建，fraud-example 加 nop-ioc 会改变其全量 init 行为（runtime pom 注释明示该风险）→ 记日志*
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - XDSL 消费路径与能力矩阵同步

Status: completed
Targets: 按 Phase 1 裁定条件化：引入分支 = `nop-stream/nop-stream-flow/`（builder 消费）+ `nop-kernel/nop-xdefs/`（stream.xdef 扩展）；不引入分支 = Phase 1 预绑定消费者候选的落点模块（维护/校验工具入口）；共同 = `docs-for-ai/03-modules/nop-stream-connectors.md`

- Item Types: `Fix | Proof | Decision`

- [x] 按 Phase 1 的 XDSL 消费路径裁定执行：若引入类型化声明——xdef 扩展 + builder 经注册中心解析 + 至少一个场景级使用（fraud-example 或 quickstart 增加类型化声明用例）；若不引入——为 Phase 1 指定的预绑定消费者（维护/校验工具入口）完成接线并验证（显式不做字段级 conf 校验，守住 item 20 边界）
  — *Phase 1 D5 裁定不引入 → 预绑定消费者 `StreamConnectorCatalog` 接线验证：`TestStreamConnectorRegistryDiscovery`（工具入口→注册中心→probe 探测/清单输出）+ `TestStreamConnectorRegistryE2E`（强化：registry 构造 source/sink 端点 → `env.execute()` → batch-consumer 收集 exactly-once + 2PC file sink 经真实周期 checkpoint 提交（manifest + epoch 文件 + 无 tmp 残留）——含 FLIP-27 split source / SourceFunction source / 2PC sink 三工厂形态全部经执行路径）*
- [x] 能力矩阵同步机制落地：`nop-stream-connectors.md` 与代码级能力声明的对齐方式（文档标注代码锚点 vs 生成 vs 复核清单——Phase 1 裁定），按 Phase 1 ③ 核对口径执行同步并跑 doc-links
  — *裁定机制 = 代码锚点标注 + 发现测试钉定：connectors 目录页新增「SPI 注册中心与类型名」节（8 类型名→工厂类→beans.xml 锚点表 + 注册资格集 + 单一事实源声明 + catalog 用法示例 + item 20 边界）；能力矩阵 8 行组件名标注 SPI 类型名；锚点索引表新增 SPI 注册中心行（四层测试锚点）；doc-links exit 0*
- [x] INDEX / source-anchors 若路由或锚点变化则同步（`docs-for-ai/INDEX.md` + `04-reference/source-anchors.md`）
  — *STRM-047..051 五个新锚点（registry/catalog/契约族+描述符/beans 注册/发现+E2E 测试）；INDEX connectors 路由行补 SPI 注册中心语义*

Exit Criteria:

- [x] Phase 1 裁定的消费路径有端到端用例：从 XDSL/工具入口 → 注册中心解析 → 连接器实例构造 → 进 execution 的完整路径一条测试跑通（若消费路径为工具面，则为工具入口 → 注册中心 → 探测/校验输出）
  — *工具面：discovery 测试 probe 全 8 组件；**并超额**完成 execution 级：e2e 测试两条（batch-consumer 链 + 2PC checkpoint 链）从 catalog→registry→factory 构造的端点跑进 `env.execute()` 到最终输出*
- [x] **接线验证**：注册中心被运行时消费者真实调用（测试断言工厂解析路径被走到，非仅注册 bean 存在——本 plan Anti-Hollow 核心项，消费者实体由本 phase 落地并验证）
  — *`TestStreamConnectorRegistryE2E` 全链路：容器→catalog→registry.resolve→factory.create→env.execute→输出断言；3× 复跑稳定*
- [x] `nop-stream-connectors.md` 与代码级能力声明按核对口径一致（逐项核对记录在日志或测试）
  — *结构化相等列（方向/交付语义/并行度）= 发现测试 `testCapabilityMatrixAlignedWithDesignAdjudication` 逐项断言（§8.4 对齐记录）；文档专属列（恢复语义 prose/依据/测试锚点）保留文档侧*
- [x] **端到端验证**：类型化声明路径（若引入）在真实场景测试中从 stream.xml 定义跑到 sink 输出；既有 bean 引用路径回归不受影响
  — *类型化声明未引入（D5 裁定，前半句不适用）；既有 bean 引用路径回归：`./mvnw test -pl nop-stream -am -T 1C` 全绿（fraud-example S1/S2 含 TestS2FileAggregationE2E 3/3）*
- [x] **无静默跳过**：类型化声明中的未知参数/未知类型 fail-fast（复用/对齐 FL-1 既有语义与错误码风格，错误信息含指引）
  — *未知类型/方向错配 = typed error 含注册清单（discovery 测试两用例钉定）；FL-1 面原样维持（flow 既有测试回归绿）；未知参数拒绝显式留 item 20（工厂契约 javadoc + 目录页声明）*
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿；`node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] owner-doc 更新：connectors 目录页与 user-guide（如消费路径新增 XDSL 形态，用户指南补用法；否则记录 No update required 的理由）
  — *connectors 目录页新增 SPI 节（含用法示例）；user-guide 连接器使用指引新增 SPI 注册中心条目（类型名清单 + 目录页指向）——未新增 XDSL 形态，故用户指南 XDSL 章节无变更（理由在案）*
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 关闭条件：本 section 与各 Phase Exit Criteria 全部 `[x]` 后才能将 Plan Status 改为 completed。纯决策/文档项不涉及构建验证的可按实情裁剪，但代码项必须全绿。

- [x] P-REQ-28 验收原文逐条核对：SPI 注册接口 + 注册发现测试存在；OLAP 端扩展裁定记录（最小集或分期）落 D-GAP/Follow-up plan——**落点口径**：本 plan 即 P-REQ-28 所指 Follow-up plan，裁定记录落本 plan 派生文档（connector-design.md / roadmap 写回）为等价落点，closure audit 按此口径核对
  — *audit #5-9 PASS（注册接口 + 发现测试）/ #3 PASS（OLAP 三态落档 §8.7）*
- [x] 资格集内全部端点组件（预期 8 个）注册且既有场景（bean 引用路径）无回归；资格集裁定（含 Reader/Builder 不注册理由）落档
  — *audit #6/#7/#16 PASS（8 工厂 + 5 beans.xml；Reader/Builder 理由 §8.1；全量套件绿含 S1/S2）*
- [x] tis OQ-2 裁定落档，tis 报告（`2026-08-14d`）Status 按裁定收敛（若其余 Open Questions 仍悬置，仅写回 OQ-2 部分并如实标注）
  — *audit #2 PASS（OQ-2 勾选 + 裁定注记；报告保持 open，OQ-1/OQ-3 未决如实标注）*
- [x] OLAP 裁定写回 roadmap（如产生新 Follow-up 按 Rules 追加并更新 Last updated）
  — *裁定 = defer×4（需求门控）+ exclude×2，不追加无需求 Follow-up（Deferred But Adjudicated 收口）；roadmap item 19 写回 done + Last updated 同步*
- [x] 注册中心无空壳形态：≥1 运行时消费者被测试验证
  — *audit #10/#13 PASS（E2E：catalog→registry→factory→env.execute→输出全链路真实调用，2 用例 3× 复跑稳定）*
- [x] 独立子 agent closure audit 已完成并记录证据（含 Anti-Hollow 检查）
  — *session `ses_f9b032081ffe88EUxlu5PIFFlD`，16/16 PASS，APPROVED（3 Minor 均为格式/诚实标注类，无需整改）*
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [x] roadmap item 19 写回 `done`（closure audit 通过后）

## Deferred But Adjudicated

### OLAP 连接器实现交付（Phase 1 D7 裁定：defer×4 + exclude×2，本期 go-minimal 集为空）

- Classification: `out-of-scope improvement`（ClickHouse/Doris/StarRocks/Hive = defer（需求门控）；Paimon/Iceberg = exclude（依据在案）——裁定记录见 `ai-dev/design/nop-stream/connector-design.md` §8.7）
- Why Not Blocking Closure: P-REQ-28 验收只要求「裁定记录（最小集或分期）落档」，不要求本期交付实现；roadmap Phase S 场景无 OLAP 端需求证据；无场景需求时交付未经验证的 exactly-once 声明违反产品化纪律
- Successor Required: `no`（defer 项为需求门控而非排期工作——revisit 触发点 = 首个 OLAP 目标需求进入 roadmap Phase S 场景，届时按 roadmap Rules 以 Follow-up 立项，交付预案（connector-jdbc 方言子类 + 能力矩阵行 + 注册工厂）已在 §8.7 记录）
- Successor Path: 需求触发时按 roadmap Rules 追加（本 plan 不预留空壳 Follow-up）

## Non-Blocking Follow-ups

- 连接器数量增长后的注册清单与文档矩阵的持续同步机制（本 plan 落首次同步；长期生成化属优化候选）。
- item 20 消费本 plan 注册中心/能力面时的接口反馈（如需扩展能力字段，走 item 20 内裁定，不回改本 plan）。

## Closure

Status Note: item 19 / P-REQ-28 全部验收达成：SPI 注册接口（三工厂契约 + 8 端点组件注册，NopIoC beans.xml 承载）+ 注册发现测试（三层 42 用例，全量枚举 + 能力矩阵逐项断言）+ OLAP 端扩展裁定记录落档（defer×4/exclude×2，本期最小集为空——分期口径满足验收）；tis OQ-2 收敛落档；能力矩阵同步机制（代码锚点 + 测试钉定）落地；注册中心经 E2E 测试证明被真实消费（catalog→registry→factory→env.execute→输出）。FL-1 拒绝面/XDSL 既有形态零变更，既有场景零回归。
Completed: 2026-09-03

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，非实现 session）
- Audit Session: `ses_f9b032081ffe88EUxlu5PIFFlD`
- Evidence:
  - 16/16 检查全部 PASS，裁定 **CLOSURE-AUDIT: APPROVED**（2026-09-03）：
    - Phase 1 #1-4：connector-design.md §8 D1..D8 各含拒绝替代方案、无实现级签名；tis 报告 OQ-2 勾选 + 裁定注记 + 报告 Status open 如实标注；OLAP §8.7 六候选三态 + 依据 + revisit 触发点；design README 索引。
    - Phase 2 #5-9：registry 包 14 类型齐备；8 工厂 + 5 beans.xml 无状态注册；6 错误码带 ARG；6 测试文件用例数 13/11/3/3/4/8 与断言内容（全量枚举/能力矩阵/未知类型 registeredTypes 参数/8 组件 probe 构造）逐项核实，surefire 报告全绿。
    - Phase 3 #10-13：E2E 两用例全链路经 registry 真实构造（非直接 new）+ env.execute + 输出断言；connectors 目录页 SPI 节 + 矩阵标注；STRM-047..051 + INDEX；Anti-Hollow 检查（调用链真实 / TODO-FIXME 0 命中 / 唯一 catch 为 typed rethrow）。
    - 诚实性 #14-16：工作树即审计对象；Deferred 仅 OLAP 需求门控条目；Non-Goals 零越界（stream.xdef 与 StreamModelDslBuilder diff 为空、无插件市场机制、无 OLAP 实现）。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（31275 引用 0 错误，audit 独立复跑）。
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0（0 findings）。
  - `./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS（全模块，含新增 44 用例：42 注册面 + 2 E2E）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（收口时执行）。
  - Deferred 项分类检查：唯一 Deferred = OLAP 实现（out-of-scope improvement，需求门控，Successor Required: no + 触发点在案）——无 in-scope live defect 被降级。
  - Minor（3，均不阻塞）：D6 拒绝替代方案为行内式（实质在案，格式异于 D1/D2/D3/D5/D8）；file split source 无实例级一致性访问器（诚实标注的规则例外，由矩阵断言 + 行为测试钉定）；discovery 测试 jdbc 用 construction-only proxy（有注释、commit 路径由 connector-jdbc 模块测试覆盖）。

Follow-up:

- 连接器数量增长后的注册清单与文档矩阵持续同步（机制已落：代码锚点 + 发现测试钉定；生成化属长期优化候选——见 Non-Blocking Follow-ups）。
- item 20 消费注册中心/能力面（conf-validate / CLI 形态裁定）——接口反馈走 item 20 内裁定，不回改本 plan。
- 类型化 XDSL 连接器声明（引入预案在 §8.5，与 item 20 字段规格一次设计）。
- 除上述 non-blocking follow-up 外无 remaining plan-owned work。
