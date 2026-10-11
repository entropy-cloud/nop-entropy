# 2412 WI3 架构基线与 owner docs 回写

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: ai-dev/backlog/nop-stream-structure-refactor-roadmap.md（WI3，Phase A）
> Related: ai-dev/plans/2410-wi1-nop-bom-dangling-entries.md、ai-dev/plans/2411-wi2-quickstart-portable-sed-and-version-adjudication.md

## Purpose

把 `ai-dev/design/nop-stream/01-architecture-baseline.md` 与 live repo 的偏差收敛（模块数 10→13、缺 runtime → flow 编译边），把 core 对 runtime 的注释级跨模块引用收敛为文内可解析形式（为 WI7 的零命中钉扫清注释半边），并按 roadmap 在 source-anchors 登记 WI7/WI8 的锚点变更预告。

## Current Baseline

- `ai-dev/design/nop-stream/01-architecture-baseline.md:32` 写「子模块清单与 nop-stream/pom.xml `<modules>` 一一对应（共 10 个）」，实际 pom 恰 13 个 module（缺 connector-kafka、format-avro、sql 三行；模块树 likewise 缺此三行）。
- 依赖方向表（:36-63 区段）缺 `runtime → flow` 编译边；实证：`nop-stream/nop-stream-runtime/pom.xml:52-56` 声明 nop-stream-flow 依赖（:54 artifactId），:43-51 注释自述动机为 maintenance tooling、SPI 保持 TM 部署路径与 flow 解耦。消费者实测：runtime main 树对 flow 的 import 仅 `runtime.maintain` 下 3 个文件（StreamConfValidateCommand / StreamSampleCommand / StreamLintCommand）。注意 §一.4（:14）「依赖方向严格单向」总括句为边清单式枚举、未含此横边，需同步注记例外，否则文档内部矛盾。
- `nop-stream/nop-stream-connector/pom.xml:14-29` AR-2 注释（base connector 必须可在无 nop-batch-core / nop-message-debezium classpath 下加载）为该 pom 自有编号；runtime 测试侧对全部 5 个 connector 的 test-scope fan-in（`nop-stream-runtime/pom.xml` test 依赖）与其意图相悖，此项为 WI5 的处置对象，本 WI 仅登记事实。
- core `src/main` 引用 `io.nop.stream.runtime` 的文件恰 5 个（grep 实证）：
  1. `WindowedStreamImpl.java:296`——`Class.forName` 反射（代码级引用，WI7 消除，本 WI 不动）
  2. `source/SimpleSourceSplit.java:18`——真死链：`runtime.source.SourceSplit` 类已不存在（注释自述 The concrete is gone）
  3. `windowing/assigners/WindowAssigner.java:74`——真死链：`runtime.task.StreamTask`（runtime 无 task 包亦无此类）
  4. `WindowAssigner.java:73`——引用有效（runtime 的 WindowOperator）但 WI7 搬迁后语义更迭
  5. `execution/CheckpointBarrierTracker.java:54`——引用有效（runtime.checkpoint.PendingCheckpoint 类存在）
  6. `execution/plan/DeploymentAssignment.java:27`——引用有效（`JobCoordinator#assignTasks()` 实存于 JobCoordinator.java:831）
  （5 文件 6 处：WindowAssigner 占 2 处）
- `docs-for-ai/04-reference/source-anchors.md:269-272` STRM-016—019：STRM-018 指 runtime 侧 WindowOperator 路径（WI7 搬迁后变更）、STRM-019 指 core 侧 IWindowOperatorFactory（路径不变、WI8 后描述更新）。
- `docs-for-ai/03-modules/nop-stream.md:9-19` 模块路由表缺 `nop-stream-connector-kafka` 与 `nop-stream-format-avro` 两行（connector 族聚合行只列 batch/jdbc/debezium）。

## Goals

- 基线文档模块树与计数修为 13；依赖方向表补 runtime → flow 行（含 3 文件 maintain 例外裁定）与 runtime 测试侧 connector fan-in 事实登记（标注 WI5 处置）。
- 4 个 core 文件的 6 处 `io.nop.stream.runtime` 注释级引用改写为不含跨模块 FQN 的文内形式（纯注释，零行为变更）。
- source-anchors STRM-018/019 登记 WI7/WI8 变更预告；nop-stream.md 模块路由表补 2 行。

## Non-Goals

- 不动 `WindowedStreamImpl.java:296` 反射（WI7 对象）。
- 不做 runtime 测试 fan-in 的处置（WI5 对象），仅登记事实。
- 不重写基线文档其他章节（§三/§四 内容不在本 WI 范围）。

## Scope

### In Scope

- `ai-dev/design/nop-stream/01-architecture-baseline.md`：模块树 +3 行、计数修正、依赖方向表 +2 条事实（runtime→flow 例外、测试侧 fan-in 登记）。
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/` 下 4 文件纯注释改写（SimpleSourceSplit / WindowAssigner / CheckpointBarrierTracker / DeploymentAssignment）。
- `docs-for-ai/04-reference/source-anchors.md`：STRM-018/019 行加预告注记。
- `docs-for-ai/03-modules/nop-stream.md`：模块路由表补 connector-kafka、format-avro 两行。

### Out Of Scope

- 任何代码行为变更；WI5/WI7/WI8/WI12 的处置对象。
- §二 职责边界表（:36-47）的 3 行补齐与 runtime 行依赖列修订、nop-stream-sql 依赖面（sql → flow compile 边）在依赖表的登记——均 defer 至 WI12 终稿（roadmap WI12 条目「模块表与依赖方向表最终形态」）。

## Execution Plan

### Phase 1 - 基线回写与注释收敛

Status: completed
Targets: `ai-dev/design/nop-stream/01-architecture-baseline.md`、core 4 个 java 文件、`docs-for-ai/04-reference/source-anchors.md`、`docs-for-ai/03-modules/nop-stream.md`

- Item Types: `Fix`（基线文档与 live repo 的确认漂移；死链 Javadoc）；例外登记为 `Decision`

- [x] 基线文档 :32 计数 10 → 13；模块树补三行（职责文案对齐 owner doc 权威口径，不用「wire 适配」误称）：`nop-stream-connector-kafka [实现] Kafka 原生连接器（source + 2PC exactly-once sink）`、`nop-stream-format-avro [实现] Avro/Schema Registry wire format 层`、`nop-stream-sql [实现] 声明式 SQL 窄范围接口（.stream.xml 顶层 <sql> 元素编译宿主）`
- [x] §一.4（:14）总括句行尾补例外注记：「（例外：runtime.maintain 维护工具链 → flow，见 §二 依赖方向表例外登记）」，消除边清单式总括与新表行的内部矛盾
- [x] 依赖方向表补两条实测事实：(a) `runtime → flow` 编译边——仅 runtime.maintain 3 文件（StreamConfValidateCommand/StreamSampleCommand/StreamLintCommand）服务维护工具链，裁定为可辩护例外（runtime pom :43-51 注释自述 SPI 解耦动机）；(b) runtime 测试侧对全部 5 个 connector 的 test-scope fan-in 违反 connector pom :14-29 AR-2 意图——登记为 WI5 处置对象
- [x] SimpleSourceSplit.java:18 改写：整句重写去除 `io.nop.stream.runtime.source.SourceSplit` 字面 FQN（该处已是 {@code} 非标签问题），保留 Stage 49 D6 历史语义，改写后不得再含该字面量
- [x] WindowAssigner.java:72-74 改写：整句重写（注意 `{@link` 开括号在 :72 行尾、FQN 在 :73-74，只改 FQN 行会留悬空 `{@link`），去除 WindowOperator 与死链 StreamTask 两个 FQN，改为「containing window operator / task 的运行时执行上下文」级英文语义描述（WI7 搬迁后仍为真）
- [x] CheckpointBarrierTracker.java:53-54 改写：整句重写（`{@link` 开括号 :53 行尾、FQN :54），`io.nop.stream.runtime.checkpoint.PendingCheckpoint` → 不含跨模块 FQN 的英文 {@code} 文字形式
- [x] DeploymentAssignment.java:27 改写：`{@link io.nop.stream.runtime.coordinator.JobCoordinator#assignTasks()}` → `{@code JobCoordinator#assignTasks()}` 级英文文字形式
- [x] 4 个 java 文件新注释一律英文（目标文件现有注释零中文）；改写文本不含 `*/`、不含未来路径的反引号形式
- [x] source-anchors.md STRM-018 行加注「WI7 窗口算子归位 core 后路径变更，WI7 负责改写」（注记不得含 WI7 目标路径的反引号形式——路径尚不存在，check-doc-links 报 BROKEN_LINK；不得引用 ai-dev/ 路径——docs-for-ai 边界为 error）；STRM-019 行加注「WI8 收敛为 WindowSpec 契约后描述更新」
- [x] nop-stream.md 模块路由表补两行：`nop-stream-connector-kafka`｜Kafka 原生连接器（KafkaSourceFunction + KafkaTwoPhaseCommitSink exactly-once sink）；`nop-stream-format-avro`｜Avro/Schema Registry payload 格式层（Confluent wire format，当前主要消费面 = Kafka 连接器）

Exit Criteria:

- [x] `grep -rn "io.nop.stream.runtime" nop-stream/nop-stream-core/src/main --include="*.java"` 仅剩 WindowedStreamImpl.java 1 个文件命中（反射字符串，WI7 消除）
- [x] `grep -c "<module>" nop-stream/pom.xml` = 13 且基线文档模块树行数与之一致、:32 计数文字为 13
- [x] 基线文档依赖方向表含 runtime → flow 行且写明 3 文件例外范围与测试侧 fan-in 登记
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] **行为保持**：4 个 java 文件仅注释行变更（`git diff -- <4 个文件路径>` 逐 hunk 核对——工作树含无关改动，diff 必须限定路径）；`./mvnw -o -q compile -pl nop-stream/nop-stream-core` 通过（前提：本地仓库已有 SNAPSHOT 依赖，本机成立）
- [x] No new test required: 纯注释与文档变更，无代码路径（编译验证即覆盖语法面）
- [x] `docs-for-ai/04-reference/source-anchors.md` 与 `docs-for-ai/03-modules/nop-stream.md` 已更新（本 WI 交付本体）；`docs-for-ai/INDEX.md` 无路由变化不需更新
- [x] `ai-dev/logs/2026/10-11.md` 已追加 WI3 条目

## Closure Gates

- [x] core src/main 的 `io.nop.stream.runtime` 命中仅剩 WindowedStreamImpl 反射 1 文件
- [x] 基线文档模块数、依赖方向表与 live repo 一致
- [x] check-doc-links --strict 退出码 0
- [x] 4 文件 diff 全为注释行，core 编译通过
- [x] 无 in-scope live defect 被降级为 follow-up
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2412-wi3-architecture-baseline-docs-rewrite.md --strict` 退出码 0

## Closure

Status Note: 全部 Exit Criteria 与 Closure Gates 经独立审计 PASS（审计者实跑 grep/diff/doc-links 复核，并独立复跑编译）。core src/main 的 runtime 引用收敛至仅剩 WindowedStreamImpl 反射 1 文件（WI7 消除对象）；基线文档 13 模块与依赖表两行事实登记与 live repo 一致；4 文件改写为纯注释行。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general-purpose，closure-audit fresh session，2026-10-11）
- Audit Session: agent_eae17496-93fb-46bf-a446-5cea4c4db0ea
- Evidence:
  - Exit Criteria 1-8：全部 PASS（grep 剩 1 文件、module 计数 13、依赖表两行、doc-links exit 0、diff 纯注释 + 编译 exit 0、log 条目）
  - Closure Gates 1-5：PASS；跨行 {@link 整句重写无悬空；新注释全英文无字面 FQN
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码 0（completed 态复跑确认）
  - Anti-Hollow：本计划为文档/注释变更，替代验证面（grep 钉 + doc-links 工具 + 编译）全绿
  - Deferred 项分类检查：无 in-scope live defect 被降级；职责边界表 3 行与 sql 依赖边 defer WI12 有 roadmap 条目依据
  - 审计者小观察（风格级，非缺口）：CheckpointBarrierTracker 注记用圆括号后缀路径而非 {@code} 包裹，grep 判据仍成立

Follow-up:

- no remaining plan-owned work
