# 05 JMH 性能基线（roadmap item 13，M1 后门禁）

> Plan Status: active
> Last Reviewed: 2026-09-21
> Source: ai-dev/backlog/nop-lint-roadmap.md Wave 2 item 13（deps: M1 ✓）；设计 11-performance-profiles.md（成本模型权威）
> Related: plan 04（内核，completed）；未来一切性能声称以本 plan 产物为对照基线

## Purpose

为 nop-lint 建立 JMH 基准基线并完成与 ast-grep CLI 的同规则对比（roadmap item 13："gate for all future performance claims"，mirror nop-treesitter item 13 discipline）。含 JFR 热点采样（用户性能优化授权），为后续内核优化提供对照数据。

## Current Baseline

- M1 已达成（items 1–7 done）：`SourcePatternCompiler.compile` → `matchIn` 全链可用，core 80 tests 全绿。
- **环境事实（2026-09-21 核对）**：ast-grep CLI 已安装（`/opt/homebrew/bin/ast-grep`，即 `sg`）；JDK 26（Zulu，含 `jfr view` 工具）。
- nop-treesitter 纪律（mirror 对象，**实测坐标**）：jmh-core **1.33** scope test + jmh-generator-annprocess **1.33** scope test（`nop-treesitter/pom.xml:69-79`；"1.37+provided" 是 nop-rg 口径，不采用）；JDK 23+ 需显式 annotationProcessorPaths 挂 jmh-generator（nop-rg-benchmark 先例）；bench 类放 `src/test/java/.../bench/`；`TreeSitterBenchmarkRunner` main 从仓库根启动、结果写 `_tmp/`；SmokeTest 保证 bench 不腐坏。
- 编译是规则加载冷路径、匹配是热路径（plan 03/04 已裁定）——基线重点：**端到端 lint 吞吐（parse+match/文件）与匹配内核微基准**。
- 设计 11 §6 的目标口径：编辑器 <50ms/文件（fast profile）、CI 分钟级——基线数据将对照此口径记录。

## Goals

- `io.nop.lint.core.bench`：`PatternCompileBenchmark`（规则集编译，冷路径量化）、`PatternMatchBenchmark`（已编译规则集对固定语料匹配，热路径微基准）、`EndToEndLintBenchmark`（parse+compile 一次 + match 多文件）、`LintBenchmarkRunner`（全量跑批写 `_tmp/`）、`BenchmarkSmokeTest`（防腐坏）。
- 语料确定性：内嵌固定 Java 语料（约 100 行、含可匹配构造）为字符串常量，多文件场景以 N 份副本模拟；不依赖文件系统状态。
- ast-grep 同规则对比：`bench/compare-ast-grep.sh`——同一语料 N 文件 × 同一规则（throw new RuntimeException 捕获），`sg scan --json` 计时 vs EndToEnd 口径计时，数值进 perf doc。
- JFR 热点：匹配基准 60s 录制（`-XX:StartFlightRecording`），`jfr view hot-methods` 摘要进 perf doc。
- perf doc：`nop-lint/docs/perf-baseline.md`（环境、方法、数字、ast-grep 对比、JFR 热点、目标口径对照、复现命令）。

## Non-Goals

- 不做内核优化改动（本 plan 只测量+记录；优化依据热点数据另立决策）。
- 不实现 fast/standard/deep profile 分档执行（设计 11 的 LintEngine 归 item 9；本基线测内核裸口径）。
- 不建独立 benchmark 模块（mirror nop-treesitter 的 test-scope 模式即可；模块拆分待规则库扩大后评估）。
- 不修改 nop-treesitter。

## Scope

### In Scope

- `nop-lint/nop-lint-core/src/test/java/io/nop/lint/core/bench/*`（3 bench + runner + smoke）
- `nop-lint/nop-lint-core/pom.xml`：JMH 1.37 依赖（test scope，同 nop-treesitter 版本口径）
- `nop-lint/bench/compare-ast-grep.sh` + 规则 YAML（临时生成于 `_tmp/`）
- `nop-lint/docs/perf-baseline.md`
- `_tmp/` 下的基准结果与 JFR 录制（不入 git）
- `ai-dev/logs/`、roadmap item 13 回写

### Out Of Scope

- CI 集成、profile 分档、缓存（items 9/31/43）
- ast-grep 安装/升级（已存在）

## Execution Plan

### Phase 1 - JMH 基准源与防腐蚀烟测

Status: planned
Targets: `nop-lint/nop-lint-core/src/test/java/io/nop/lint/core/bench/`、pom

- Item Types: `Fix`

- [ ] pom 增加 jmh-core 1.33（test）+ jmh-generator-annprocess 1.33（test，同 nop-treesitter 坐标），并在 test-compile 显式配置 annotationProcessorPaths 挂 jmh-generator（JDK 23+ 默认不跑 classpath 注解处理器，nop-rg-benchmark 先例）
- [ ] `BenchCorpus`：内嵌约 100 行 Java 语料常量（含 throw RuntimeException、链式调用、类声明——三个基准规则的可匹配点）
- [ ] `PatternCompileBenchmark`：BenchmarkMode(AverageTime)，编译 3 条旗舰规则 ×1 次/op
- [ ] `PatternMatchBenchmark`：State(Scope.Benchmark) 预编译规则集 + 预 parse 语料树，每 op 对语料树全量 matchIn
- [ ] `EndToEndLintBenchmark`：每 op = parse 语料（1 份）+ 规则集 matchIn（吞吐口径换算 ms/文件记录）
- [ ] `LintBenchmarkRunner`：warmup 3×1s + measurement 5×1s + fork 1 + gc profiler，结果写 `_tmp/lint-bench-result.txt`（mirror TreeSitterBenchmarkRunner）
- [ ] `BenchmarkSmokeTest`：照抄 TreeSitterBenchmarkSmokeTest 纪律——forks(0) + warmup 1×100ms + measurement 1×100ms + `shouldFailOnError(true)` + 断言基准结果数（JMH 默认吞异常，failOnError+计数才是防腐坏）；附加断言 Match 数 > 0

Exit Criteria:

- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0（含 SmokeTest）
- [ ] **接线验证**：SmokeTest 断言 bench 产出的 Match 数 > 0（语料含可匹配点，防零匹配空转基准）
- [ ] `No owner-doc update required`（perf doc 即本 plan 产物）
- [ ] `ai-dev/logs/` 已更新

### Phase 2 - 基线测量 + ast-grep 同规则对比 + perf doc

Status: planned
Targets: `nop-lint/docs/perf-baseline.md`、`nop-lint/bench/compare-ast-grep.sh`

- Item Types: `Proof`

- [ ] 运行 `LintBenchmarkRunner` 全量，数字落 `_tmp/` 并转录 perf doc（含每 op 平均、误差、gc 摘要）
- [ ] `compare-ast-grep.sh`：`_tmp/agrep-corpus/` 生成 200 份语料副本 + 规则 YAML（`rule: { pattern: ... }` 完整嵌套结构，审查实测语法）→ `sg scan` 计时（`/usr/bin/time` stderr 口径）→ 与 EndToEnd 200 文件 warm-JVM 口径换算对比；脚本打印双方数字
- [ ] perf doc：环境（JDK/OS/ast-grep 版本）、方法（语料/参数/口径）、nop-lint 数字、ast-grep 数字与比值、目标口径（<50ms/文件）对照结论、复现命令；声明单机单次测量非统计严格结论
- [ ] 数字合理性 sanity：EndToEnd 吞吐与 PatternMatch 微基准量级一致；异常值（>10x 偏离）须解释或重测

Exit Criteria:

- [ ] perf doc 存在且数字来自本次真实运行（`_tmp/` 结果文件可对照）
- [ ] **端到端验证**：compare 脚本从生成语料到打印双方数字完整跑通
- [ ] **无静默跳过**：sg scan 失败/超时必须使脚本非零退出，不静默跳过对比
- [ ] `No new test required: compare 脚本为一次性测量配方，其验证由 Exit Criteria 的端到端跑通条目承担`
- [ ] `ai-dev/logs/` 已更新

### Phase 3 - JFR 热点采样

Status: planned
Targets: perf doc 附录

- Item Types: `Proof`

- [ ] PatternMatchBenchmark 专用录制运行（独立于 Phase 1 的 8s runner 参数）：measurement 60×1s + fork 1 + `-jvmArgsAppend -XX:StartFlightRecording=duration=60s,filename=_tmp/matcher.jfr`——保证采样落在稳态测量期而非启动期
- [ ] `jfr view hot-methods _tmp/matcher.jfr` 前 10 帧摘要转录 perf doc（标注 io.nop.lint 帧占比）
- [ ] 热点结论一句话（如"X% 时间在 Y"）作为后续优化候选项记录（不实施）

Exit Criteria:

- [ ] `_tmp/matcher.jfr` 存在且 hot-methods 摘要进 perf doc
- [ ] `ai-dev/logs/` 已更新

### Phase 4 - roadmap 回写与收口

Status: planned
Targets: `ai-dev/backlog/nop-lint-roadmap.md`

- Item Types: `Follow-up`

- [ ] 独立 closure audit（针对 Phase 1–3）通过后：roadmap item 13 `todo` → `done`（附 plan 编号）
- [ ] `ai-dev/logs/` 收口记录

Exit Criteria:

- [ ] roadmap item 13 标记 `done`
- [ ] `ai-dev/logs/` 收口记录已更新

## Closure Gates

- [ ] 所有 in-scope confirmed live defects 已修复（如有执行中发现记录于此）
- [ ] 所有 in-scope confirmed contract drifts 已收敛（不适用：纯测量，无行为契约变更）
- [ ] 行为/契约结果已达成：基线数字 + ast-grep 对比 + JFR 热点全部落 perf doc
- [ ] 必要 focused verification 已完成：Phase 1–3 Exit Criteria 全勾
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响 owner docs：perf doc 为本 plan 新增产物；design 11 不改（分档执行归 item 9，基线只记录裸口径——偏差已声明）
- [ ] 独立子 agent closure-audit 已完成并记录证据（fresh session）
- [ ] Anti-Hollow Check：SmokeTest 断言 Match 数 > 0（基准真实匹配）；compare 脚本完整跑通双方计时；`scan-hollow-implementations.mjs --module nop-lint --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/05-jmh-benchmark-baseline.md --strict` 退出码 0
- [ ] `./mvnw -pl nop-lint/nop-lint-core -am clean test -T 1C` 退出码 0
- [ ] 代码规范：bench 代码 4 空格缩进、导入分组

## Deferred But Adjudicated

### 内核热点优化实施

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本 plan 职责是建立对照基线；热点优化须先有数据（本 plan 产出）再立项，避免无对照的盲目优化
- Successor Required: `yes`
- Successor Path: 依据 perf doc 热点结论在后续内核 plan 中立项

### ast-grep 多规则多语料矩阵对比

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版基线以单规则集×单语料口径建立可比锚点；多维度矩阵在规则库扩大（item 11 后）才有代表性
- Successor Required: `yes`
- Successor Path: roadmap item 11 后的 perf 复测

## Non-Blocking Follow-ups

- CI 定时基准回归（防性能退化）：设计 11 §8 / item 43 承接
- 编辑器 fast profile 口径实测（含增量解析）：item 16/41 承接

## Closure

Status Note: （closure 时填写）
Completed: （closure 时填写）

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent closure audit 时填写）
- Evidence: （逐条 Exit Criterion / Closure Gate 验证结果）

Follow-up:

- （closure 时填写，或写 no remaining plan-owned work）
