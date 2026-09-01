# 2 connectors 四模块审计（roadmap item 10）

> Plan Status: completed
> Last Reviewed: 2026-09-01
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 10（Phase M 第四个审计项，deps: item 6 done）；Phase M 审计统一模式（roadmap Work Items 分组说明）
> Related: `2026-09-01-0938-1-design-productization-gap-analysis.md`（item 6，前置依赖，其 §3.1 产出 item 10 审计重点两项：候选钩子清单 + XDef/字段校验覆盖现状）；`2026-09-01-0938-2-core-module-audit.md` / `2026-09-01-0938-3-runtime-module-audit.md`（items 7/8，sibling 审计：方法论与报告结构复用；item 7 报告 §2.2 Flink/Beam 8 格 Phase M 级裁定、§1.1 §7 空壳模块结论本 plan 仅引用不重复裁定）；`2026-09-01-1457-1-cep-module-audit.md`（item 9，前序 sibling，执行顺序在本 plan 之前）
> Mission: nop-stream-productization
> Work Item: roadmap item 10

## Purpose

对 `nop-stream-connector` / `nop-stream-connector-batch` / `nop-stream-connector-jdbc` / `nop-stream-connector-debezium` 四模块按产品标准完成统一审计并收口：验证 2026-06-30 code audit 中 connector 相关发现的整改收口（2026-05-20 audit 无 connector 专属组，逐条核对确认）、执行产品化视角新增审计（D-GAP 下发的两项重点：① Source/Sink 契约上「连通性校验钩子」的接口落点预核，输出**候选钩子清单**供 Follow-up item 20 plan 直接消费；② 各连接器配置 bean 的 XDef/字段校验覆盖现状）、复查四模块间及与 core 的重复逻辑、source/sink 契约一致性、错误处理与资源管理；小缺陷就地修复（含契约一致性测试补齐），大缺陷转为 roadmap Follow-up 工作项。

## Current Baseline

（2026-09-01 live 核对）

- 四模块文件清单（`find -name "*.java"` 计数）：`nop-stream-connector` 10 main / 8 test（`connector/file/`：FileSource/FileSplit/FileSplitEnumerator/FileSplitEnumeratorState/FileSourceReader/FileTwoPhaseCommitSink/FilePendingCommit + `connector/`：MessageSourceFunction/MessageSinkFunction/package-info）；`nop-stream-connector-batch` 3 main / 5 test（BatchConsumerSinkFunction/BatchLoaderSourceFunction/StreamConnectors）；`nop-stream-connector-jdbc` 2 main / 3 test（JdbcTwoPhaseCommitSink/JdbcTwoPhaseCommitSinkBuilder）；`nop-stream-connector-debezium` 1 main / 4 test（DebeziumCdcSourceFunction）
- 2026-05-20 duplicate-code audit：无 connector 专属组（§1—§9 中主体归 core/runtime/cep，§7 空壳模块四件为 api/checkpoint/flink/flow——connector 模块不在其中）；本 plan Phase 1 显式记录该归属核对结论，引用 item 7 §1.1 §7 行空壳模块结论不重复核验
- 2026-06-30 code audit connector 相关发现 seed 清单（本 plan Phase 1 核对基准）：§1.1 模块统计（时点 connector 7 main/11 test「✅ 活跃开发」——**live 已变为 4 模块 16 main/20 test，batch/jdbc/debezium 拆分与统计漂移由本 plan 核对表记录**）+ §1.3 依赖方向（connector → core + nop-batch-core，时点 ✅）+ §3.1 测试覆盖（connector 11 类 50 @Test，测试/源码比 1.57）+ §2.1 UOE 桩 6 处中无 connector 项 + §2.2 通配符导入（live 复核：四模块 test 侧共 16 个文件残留——connector 5 / batch 5 / jdbc 2 / debezium 4；main 侧全模块已清零）；**派生规则**：报告中其余发现按「涉及文件位于四个 connector 模块」准则纳入核对表
- 2026-08-04-2300-1/2/3 remediation plans：2300-2 Targets 全在 rocksdb/runtime/cep（item 8 报告 §1.3 已复核 runtime 侧）；2300-1/3 以 core/runtime 为主——本 plan Phase 1 逐条核对三 plan 是否存在 connector 侧修复点（预期无，执行时确认）
- D-GAP 报告 §3.1 item 10 审计重点（两项，本 plan Phase 2 逐条消化）：① **P-REQ-13/14 go 裁定的接口落点预核**——Source/Sink 契约（SourceWorkUnit/FLIP-27 协议、`SinkFunction`/`TwoPhaseCommitSinkFunction`）上「连通性校验钩子」的存在性与最小侵入点，审计需输出**候选钩子清单**（供 Follow-up item 20 plan 直接消费），非仅缺陷清单；② **各连接器配置 bean 的 XDef/字段校验覆盖现状**（是否全部走 `stream.xdef` 校验链路——`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef` live 存在，D-GAP §1.1 P-REQ-14 证据锚点）
- 前序 production roadmap 已 shipped 的连接器能力（roadmap Current baseline）：FLIP-27 Source 体系、CDC（Debezium + offset checkpoint）、事务型 JDBC/File sink（exactly-once）；roadmap Framework/platform reuse 表：2PC sink 框架 `TwoPhaseCommitSinkFunction`（JDBC/File 实现）已实现
- core 侧契约锚点（item 7 已核，供审计对照）：`TwoPhaseCommitSinkFunction` 深度生产接线（core 审计报告 §1.1 §4.4 行——runtime/checkpoint/CheckpointPlanBuilder、core/operators/StreamSinkOperator、connector/file/FileTwoPhaseCommitSink）；`CheckpointedSourceFunction` 由 connector-debezium/DebeziumCdcSourceFunction 实现、StreamSourceOperator 引用
- item 7 报告 §2.2：Flink/Beam 8 格 Phase M 级裁定一次落定（全部无需补评），本 plan 引用不重复裁定
- 工具：`ai-dev/tools/scan-hollow-implementations.mjs`（消息语义分级版；high/critical 退出码非 0）；`ai-dev/tools/check-nop-stream-invariants.mjs`（CI fail-fast 门禁）；多行 UOE 人工补审沿 items 7/8/9 同一基线
- 验证基线：roadmap Cross-cutting concerns 约定 `./mvnw test -pl nop-stream -am -T 1C` 全绿

## Goals

- 整改收口验证：05-20 归属核对（无 connector 专属组，显式记录）+ 06-30 connector 相关发现（seed 清单 + 派生规则所得项，含模块统计漂移核对）逐项核对 live 状态（landed / partial / regressed），每项有证据指针；2300-1/2/3 connector 侧归属逐条核对
- 产品化新增审计：D-GAP item 10 两项重点逐条消化（含勾销清单无遗漏）——①输出**候选钩子清单**（每个钩子：落点契约接口/方法、现有校验存在性、最小侵入点评估、供 item 20 消费的接口语义说明）；②各连接器配置 bean 的 XDef/字段校验覆盖现状结论表 + 四模块统一审计（模块间重复代码、与 core 的重复逻辑、source/sink 契约一致性、错误处理与资源管理——连接器生命周期 open/close、异常路径、资源释放）+ hollow scan（四模块分别执行）+ 测试覆盖抽查 + 契约一致性测试补齐（roadmap item 10 交付物）
- 缺陷处置：单 plan 范围内可收敛的小缺陷就地修复（每个修复配 focused 回归测试；契约一致性测试缺口补齐属本类）；跨模块/大规模缺陷转 roadmap Follow-up 工作项（编号顺延、来源标注本 plan）。小/大缺陷判定准则与 items 7/8/9 一致：修复限于四模块内、不改 core 公共契约、无需新测试基建 → 小缺陷；否则 → 大缺陷转 Follow-up
- 审计报告落地 `ai-dev/analysis/{执行当月}/`（遵循 `00-analysis-writing-guide.md`；结构对齐 items 7/8 报告）并完成 roadmap item 10 写回

## Non-Goals

- 新连接器开发（含 Follow-up item 19 的连接器 SPI 注册中心与 OLAP 端连接器最小集——本 plan 只为 item 20 输出钩子清单、为 item 19 提供契约现状证据，不做生态扩展）
- 实施 Follow-up item 20 的功能开发（dry-run 校验/凭据加密/conf-validate 命令——本 plan 只输出候选钩子清单与校验覆盖现状）
- 跨模块重构（含修改 core 契约接口——钩子清单中若需 core 侧改动，仅记录为 item 20 的设计输入）
- 修复位于 core/runtime 但影响连接器路径的发现——只记录并路由
- 四模块 test 侧 16 个通配符导入文件的清理——路由 Follow-up item 22（跨模块统一 sweep，理由同 item 22 立项：避免 items 8—11 重复机械修改；**item 22 现有枚举（core 169/runtime 108/cep 15/flow 1）未含 connector 16 文件——系 item 7 时点部分计数，本 plan closure 写回时以来源标注方式对 item 22 枚举做事实补全；若 mission owner 裁定属语义变更，则按 stop-edit-restart 提请，本 plan 不擅自扩 scope**）
- 重复裁定 Flink/Beam 8 格补评（item 7 报告 §2.2 已落定）、重核空壳模块结论（item 7 §1.1 §7 行已落定）
- cep/rocksdb/flow/fraud-example 模块审计（items 9/11）
- 2026-05-20/2026-06-30 报告正文的历史重写

## Scope

### In Scope

- 审计与报告：`nop-stream/nop-stream-connector/`、`nop-stream/nop-stream-connector-batch/`、`nop-stream/nop-stream-connector-jdbc/`、`nop-stream/nop-stream-connector-debezium/` 四模块 + 新增审计报告一份（`ai-dev/analysis/{执行当月}/`，命名遵循 writing guide）
- 修复：四模块内小缺陷（含 focused 测试）+ 契约一致性测试补齐
- 修改：`ai-dev/backlog/nop-stream-productization-roadmap.md`（Follow-up 追加 + Last updated + closure 后 item 10 写回 + item 22 枚举事实补全——见 Non-Goals 路由约定）
- 修改（条件性）：`ai-dev/tools/scan-hollow-implementations.mjs` 检测规则——仅当 hollow scan 误报经核实源于工具检测缺陷时允许修正
- 修改（条件性）：受修复影响的最小 owner-doc 同步（见 Phase 3；`ai-dev/design/nop-stream/connector-design.md` 若因修复产生事实漂移，仅做最小事实同步，不扩写、不重构）
- 只读输入：两份历史审计报告、2300-1/2/3 plans、D-GAP 报告、items 7/8 审计报告、`stream.xdef` schema、`~/sources` 竞品源码（SeaTunnel connector 生态组织 / Flink connector 契约如需对照）

### Out Of Scope

- 其余 6 个子模块的审计与修复
- `ai-dev/design/nop-stream/` 的结构性重写与历史回写
- Follow-up items 19/20 的功能实现（本 plan 产出仅为其输入）

## Execution Plan

### Phase 1 - 历史审计整改收口验证

Status: completed
Targets: 四个 connector 模块、审计报告 Phase 1 章节

- Item Types: `Proof`

- [x] 05-20 归属核对：逐组确认 §1—§9 无 connector 专属组（引用 item 7 §1.1 §7 行空壳模块结论），显式记录「无 connector 专属组」结论
- [x] 06-30 connector 相关发现逐项核对（seed 清单见 Current Baseline + 「涉及文件位于四个 connector 模块」派生规则，执行时先落完整核对表再逐项核对）：landed / partial / regressed 三态 + 证据指针；模块统计漂移（7/11 → 4 模块 16/20）单列核对行；test 通配符导入 16 文件按 Current Baseline 记载归入核对表并路由 item 22（partial · 代码风格治理项，处置见 Non-Goals 路由约定）
- [x] 逐条核对 2300-1/2/3 三 plan 的 Targets 是否存在 connector 侧修复点（预期无，执行时确认；若存在则复核 live 存在性）
- [x] partial/regressed 项归类：按小/大缺陷判定准则进 Phase 3 或 Follow-up 候选，逐项记录理由

Exit Criteria:

- [x] 报告 Phase 1 章节含 05-20 归属核对结论 + 06-30 connector 发现完整核对表（先列全表再逐项核对，每行三态 + 证据指针，无「未核对」空行）+ 2300-1/2/3 connector 侧归属核对结论
- [x] 全部 partial/regressed 项有归类结论（修复进 Phase 3 或 Follow-up 候选，附理由）
- [x] No owner-doc update required（纯核验章节）
- [x] No new test required: 纯核验章节，无代码变更
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 产品化视角新增审计

Status: completed
Targets: 四个 connector 模块、审计报告 Phase 2 章节

- Item Types: `Proof | Decision`

- [x] 消化 D-GAP §3.1 item 10 重点 ①：Source/Sink 契约全量梳理（FileSource/FileSplitEnumerator/FileSourceReader 的 FLIP-27 协议实现面、MessageSourceFunction/MessageSinkFunction、BatchLoaderSourceFunction/BatchConsumerSinkFunction、JdbcTwoPhaseCommitSink、DebeziumCdcSourceFunction 各自实现的 core 契约接口），逐契约标注「连通性/配置校验」现有存在性（构造期/open 期/无），输出**候选钩子清单**（每钩子含：落点接口与方法、现有校验存在性证据、最小侵入点评估、供 Follow-up item 20 直接消费）
- [x] 消化 D-GAP §3.1 item 10 重点 ②：各连接器配置 bean 的 XDef/字段校验覆盖现状结论表（哪些配置经 `stream.xdef` 链路字段级校验、哪些仅 Java 构造期校验、哪些无校验）；flow 侧仅核对配置段消费路径中建表所需的事实（flow 编译器路径的深审归 item 11 重点 ②，避免重复劳动）
- [x] 四模块统一审计：模块间重复代码（如 file/jdbc 两套 2PC sink 的 preCommit/commit 同构度、batch 与 message 连接器的 source/sink 对称性）、与 core 的重复逻辑、source/sink 契约一致性（错误传播语义、生命周期方法时序）、错误处理与资源管理（open/close/异常路径/外部资源释放——文件句柄、JDBC 连接、Debezium engine）
- [x] 对四模块分别运行 `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-<m> --severity high`，high/critical 发现逐条核实（真实空壳 vs 误报）+ 多行 UOE 人工补审；误报处置路径：修正代码模式或修正工具检测规则——「仅记录不改」不合法（退出码 0 为不可降级硬门禁）
- [x] 测试覆盖抽查：四模块各自代表性测试命中确认（模块小，全量盘点测试类与覆盖的契约面）+ 契约一致性测试缺口清单（哪些契约语义无测试——如 2PC 失败路径、split enumerator 恢复、CDC offset checkpoint）
- [x] closure 前对照 D-GAP §3.1 item 10 两条重点勾销清单确认无遗漏

Exit Criteria:

- [x] **候选钩子清单落地**（D-GAP 重点 ①：逐契约钩子落点 + 现有校验存在性 + 最小侵入点评估；清单自包含，Follow-up item 20 plan 无需回读 D-GAP 报告即可消费）
- [x] XDef/字段校验覆盖现状结论表落地（D-GAP 重点 ②：逐连接器配置 bean 的校验链路归属）
- [x] hollow scan 四模块退出码记录 + high/critical 发现逐条核实结论 + 多行 UOE 人工补审结论（真实发现进 Phase 3 或 Follow-up；误报处置动作已执行，非仅记录）
- [x] 四模块统一审计发现逐条带源码锚点（含严重度与处置归类）
- [x] 测试覆盖抽查结论表 + 契约一致性测试缺口清单落地（缺口归类：本 plan 补齐 or Follow-up，附理由）
- [x] No owner-doc update required（发现与清单记录在报告；owner doc 变更属 Phase 3 修复的附带裁定）
- [x] No new test required: 纯审计章节，无代码变更（Phase 3 补齐的测试要求在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 缺陷处置与收口

Status: completed
Targets: 四个 connector 模块、`ai-dev/backlog/nop-stream-productization-roadmap.md`、审计报告收口章节

- Item Types: `Fix | Follow-up`

- [x] 就地修复全部归类为小缺陷的项：每个修复附 focused 回归测试（验证正确结果而非仅无异常）；修复不引入空壳/静默跳过；修复若落在 `_` 前缀生成文件，上移到源模型/模板层处理（生成文件禁改）
- [x] 契约一致性测试补齐：Phase 2 缺口清单中归类为本 plan 补齐的项逐一落地（每个测试显式验证的契约语义列明）
- [x] 大缺陷逐项转为 roadmap Follow-up 工作项：按 Rules 追加到 Work Items 末尾（编号顺延、状态 todo、来源标注本 plan + 证据锚点），并更新 Last updated（或显式记录「无大缺陷」）；钩子清单的落地开发归属 Follow-up item 20（不新立项）；test 通配符导入 16 文件按 Non-Goals 路由约定完成 item 22 枚举事实补全（或 stop-edit-restart 提请记录）
- [x] 触及 source/sink 数据面语义的修复：至少一条从 source 到 sink 的端到端验证（既有 E2E 家族扩展或新增，含 exactly-once 断言当修复触及 2PC 路径时）
- [x] 全量回归：`./mvnw test -pl nop-stream -am -T 1C` 全绿；`node ai-dev/tools/check-nop-stream-invariants.mjs` 通过（CI fail-fast 门禁）
- [x] 审计报告 Status 定稿（resolved + 遗留清单），含全部发现→处置零丢失映射表（结构对齐 items 7/8 报告）
- [x] 小缺陷修复若改变 live 行为且 owner doc（connector-design.md）受影响：同步最小 owner-doc 更新或显式记录 `No owner-doc update required`

Exit Criteria:

- [x] 全部 in-scope 小缺陷修复落地且各有 focused 测试（新增测试清单列明：每个测试验证了哪个新行为/契约语义；纯清理类修复按 guide Rule #25 显式豁免并注明）
- [x] 契约一致性测试补齐项全部落地（归类 Follow-up 仅限满足大缺陷判定准则的项——需新测试基建或跨模块；不得以时间/规模便利为由弱化 roadmap item 10「契约一致性测试补齐」交付物，归类时附理由）
- [x] 全部大缺陷已追加为 roadmap Follow-up 工作项（或显式记录「无大缺陷」）
- [x] **端到端验证**（如适用，触及数据面语义时）：source 到 sink 完整路径测试存在且通过（触及 2PC 路径时含 exactly-once 断言）
- [x] **无静默跳过**：修复代码无空方法体/吞异常模式（hollow scan 复跑四模块退出码 0）
- [x] 修复改变 live 行为时：受影响 owner doc 已做最小事实同步，或显式记录 `No owner-doc update required`（含理由）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（报告新增后）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 2026-06-30（seed 清单 + 派生规则所得项）整改收口核对完成，无未处置的 partial/regressed 项；05-20 归属核对显式记录；2300-1/2/3 connector 侧归属核对结论落地
- [x] D-GAP item 10 两项重点全部消化且对照勾销清单无遗漏；**候选钩子清单已产出且自包含可被 Follow-up item 20 直接消费**
- [x] 空壳扫描（四模块）high/critical 无未处置真实发现（误报已按处置路径消除，非仅记录）
- [x] 小缺陷修复全部带 focused 测试；契约一致性测试缺口全部补齐或按大缺陷准则显式归类；大缺陷全部 Follow-up 化（或显式无）；test 通配符导入 16 文件的 item 22 枚举事实补全已落地（或 stop-edit-restart 提请已记录）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] `./mvnw compile`（随 `-pl nop-stream -am` 构建覆盖）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] checkstyle / 代码规范检查通过（随 mvnw 构建；root pom checkstyle 配置整体注释的事实沿 items 7/8 先例按现状通过）
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-connector --severity high` 及 batch/jdbc/debezium 三模块同命令退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] **Anti-Hollow Check**：closure audit 已验证修复组件被既有调用链在运行时确实调用、无空方法体/静默跳过/no-op 作为正常实现
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] roadmap item 10 状态写回（closure audit 通过后）

## Deferred But Adjudicated

（无需追加：watch-only residual W-1..W-8 全部记录于审计报告 §2.3 C 表，逐条附 Why Not Blocking Closure；无 plan 内 in-scope 项延期）

## Non-Blocking Follow-ups

- watch-only residual 8 组（W-1..W-8）与 3 项读审否决记录：见审计报告 `ai-dev/analysis/2026-09/2026-09-01-nop-stream-connectors-module-audit.md` §2.3（含各自后续归属：W-1→Phase S 输入、W-3→item 21 邻域、W-4→runtime 侧决策（item 26 邻域）、W-6→items 19/20 邻域）
- env 级 mid-stream 恢复 E2E（G-7）：归 Phase S（items 13/14）验证面，见报告 §2.5

## Closure

Status Note: 三 Phase 全部完成并经独立 closure audit（CLOSURE-APPROVED）：05-20/06-30/2300 三组历史输入 connector 侧收口核验成立（无专属组/无 regressed/零修复点）；D-GAP item 10 两项新增产出物（自包含候选钩子清单 + XDef 校验覆盖现状表）落地供 Follow-up item 20 直接消费；8 项小缺陷 + 1 项工具检测缺陷全部就地修复（14 个新 focused 用例，CN-1 经 fix-revert 验证）；无大缺陷无 Follow-up 追加；owner-doc 3 处 drift 最小同步；全模块回归绿 + 五工具门禁 exit 0。无 plan-owned 遗留工作。
Completed: 2026-09-01

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，`ses_fa2f21823ffeesGnoD4qeNUomN`）
- Evidence:
  - A.1—A.10 全 PASS：Phase 1/2/3 exit criteria 逐条 live 锚点复核（报告 §1.1/§1.2/§1.3、§2.1—§2.5、§3.1—§3.3 与 live 代码/命令结果一致；抽查事实全命中：四模块 main 零 UOE、test 通配符 5/5/2/4）
  - 修复 live 存在性 8/8 抽验（FileSourceReader:176 播种、FileSource 序列化器 4 处 fail-fast、两 ctor StreamException、MessageSource 零 LOG.error、Debezium 2 处 LOG.warn、MessageSink null 守卫、BatchConsumer close 优先级）；新测试 3 文件 14 用例全部存在且实质断言
  - Anti-Hollow CONFIRMED CLEAN：CN-1 位于真实生产路径（SourceReaderOperator:295 pollNext/:368 snapshotState + LocalSourceCoordinator:48）；修复后 7 个 main 文件零 empty-catch/静默吞；fix-revert 断言设计自证（expect 6 / failure mode 3，测试消息文档化）
  - 门禁独立复跑全 exit 0：四 connector 模块 mvnw test（49/37/34/25，0 failures）+ hollow scan ×4 + invariants + doc-links --strict + check-plan-checklist --strict
  - Deferred 诚实性：W-1..W-8 逐条 Why Not Blocking；git status 确认改动仅限四模块 + 工具 + owner-doc + ai-dev 文档（无 core/其他模块越界）；「无大缺陷」裁定成立
  - 3 项 Minor 均处置：①测试计数漂移已修正（batch 37；§1.2 #7 静态 147/surefire 145 口径分开）②fix-revert 为执行者声明但断言结构自证 ③W-1..W-8 已在 plan Non-Blocking Follow-ups 交叉引用
- Follow-up:
  - 无 plan-owned 遗留工作；watch-only residual 见 Non-Blocking Follow-ups（报告 §2.3 C 为权威清单）
  - roadmap item 10 写回 + item 22 枚举事实补全随本 closure 执行（见日志 09-01 条目）
