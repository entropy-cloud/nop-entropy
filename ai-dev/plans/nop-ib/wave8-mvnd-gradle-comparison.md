# Wave 8 — mvnd 对比 + Gradle/mvnd 经验吸收

> Plan Status: completed
> Last Reviewed: 2026-11-16
> Source: [nop-ib-roadmap.md](../../backlog/nop-ib-roadmap.md) Wave 8
> Related: [wave7-performance-experiments.md](wave7-performance-experiments.md)（bench harness 复用）、[04-gradle-incremental-compilation-analysis.md](../../design/nop-incremental-build/04-gradle-incremental-compilation-analysis.md)

## Purpose

与 `~/sources/mvnd`（Maven daemon）做六维机制对照 + 实证 wall-clock 实验，对 Gradle 经验清单逐项裁定（吸收→Feature+钉；不吸收→记录理由），Maven 4 解析差异点补钉，结论落 `ai-dev/analysis/`。

## Current Baseline

> 2026-11-16 live repo

- `~/sources/mvnd` 已 clone 且 install 过（`~/sources/_tmp_mvnd_install.log` 先例）；mvnd 二进制可自建。
- nop-ib bench harness 已入 repo（`nop-tools/nop-ib/tools/bench-e2e.sh`，Wave 7 Phase 1 产物）。
- 04 文档（Gradle 机制分析）在档，含事务化输出/构建缓存/文件监听/configuration cache/worker API 五项机制分析。
- Maven 4（mvnd 底座）effective-pom 差异未系统化验证——nop-ib 用 Maven 3/4 wrapper 都能 refresh（本仓 wrapper=4.0.0-rc-5 即 Maven 4，已实证可用），但 `maven.compiler.release` 语义与新 pom flavor 的解析面无钉。

## Goals

- 六维机制对照报告（daemon vs CLI+worker 池 / 增量判定 / 缓存层 / 测试调度 / 失效语义 / 并发治理），每维"可吸收/不可吸收+理由"。
- 实证：同一变更序列在 mvnd 与 ib 上的 wall-clock 对照（复用 Wave 7 harness 口径）。
- Gradle 经验五项逐项裁定。
- Maven 4 解析差异点 100% 有钉（nop-ib 测试套内）。

## Non-Goals

- 把 nop-ib 改造成 daemon 形态（"不替换构建系统"硬约束）。
- mvnd 功能性 bug 修复（只观察、对照）。

## Execution Plan

### Phase 1 — 六维机制对照（analysis）

Status: completed
Targets: `~/sources/mvnd`、`ai-dev/analysis/`

Item Types: `Proof`

- [x] 读 mvnd 源码/文档：client-daemon 协议、incremental builder、事件监听、构建缓存扩展、测试执行路径、失效策略
- [x] 六维对照表 + 每维"可吸收/不可吸收+理由"（对照 nop-ib 硬约束逐条裁定）
- [x] 落 `ai-dev/analysis/`（时效性文档，Status: open）

Exit Criteria:

- [x] 对照报告入库，六维全覆盖，每维显式裁定
- [x] daily log 条目

### Phase 2 — 实证 wall-clock 对照

Status: completed
Targets: 小型已测项目（Juggle 或 liteflow 单模块）、`ai-dev/analysis/`

Item Types: `Proof`

- [x] mvnd 可用性验证（daemon 启动 + 项目构建成功）
- [x] 同一变更序列（单文件方法体→ABI 扩散→测试失败→修复→全绿）在 mvnd 与 ib 上分段计时对照
- [x] 对照表 + 结论（含"mvnd 失效即全量兜底 vs ib 索引选择性"的实测体现）

Exit Criteria:

- [x] 同序列分段计时表入库（两侧可比口径）
- [x] 结论段回答"ib 的快路径在哪些段优于/劣于 mvnd"
- [x] daily log 条目

### Phase 3 — Gradle 经验裁定 + Maven 4 钉

Status: completed
Targets: `ai-dev/analysis/`、`nop-tools/nop-ib/src/test/java/io/nop/ib/**`

Item Types: `Decision | Feature | Follow-up`

- [x] 事务化输出裁定（对照 ib 先删后编+stash 现状）
- [x] content-addressable build cache 裁定（对照 Deferred 26，是否翻案）
- [x] 文件系统监听裁定（对照 Deferred 26 前半）
- [x] configuration cache / 模型过期策略对照裁定
- [x] worker API 隔离复用对照（design 06 worker 池）
- [x] Maven 4 解析差异钉：`maven.compiler.release`、新 pom flavor 在 EffectivePomParser 测试中的钉（本仓 wrapper=Maven 4 即活体语料）
- [x] 每项：吸收（Feature+钉）或不吸收（记录"零假绿/不替换构建系统约束下不成立"理由）

Exit Criteria:

- [x] 五项 Gradle 经验每项显式裁定
- [x] Maven 4 差异点钉全部入 `src/test/java/io/nop/ib/` 且全绿
- [x] daily log 条目

### Phase 4 — 文档收口 + closure

Status: completed
Targets: design 05、roadmap Wave 8

- [x] 对照/实验结论落 analysis；采纳项 design 变更原地更新
- [x] roadmap Wave 8 checklist 勾选 + Status
- [x] 独立子 agent closure audit APPROVED，Evidence 落本 plan
- [x] `./mvnw test -pl nop-tools/nop-ib` 全绿；提交

Exit Criteria:

- [x] 六维对照 + 实证表 + 五项裁定 + Maven 4 钉全部入库
- [x] closure audit APPROVED
- [x] daily log + 提交

## Closure Gates

- [x] 六维对照报告（每维显式裁定）
- [x] 实证 wall-clock 对照表
- [x] Gradle 五项 + Maven 4 钉全部落账
- [x] `./mvnw test -pl nop-tools/nop-ib` 全绿
- [x] 独立 closure audit APPROVED
- [x] daily log + 提交

## Deferred But Adjudicated

### design 05 stash/restore 规格未实现（Gradle 事务化输出裁定副产物）

- Classification: `watch-only residual`（规格已原地修正为 fail-loud 语义：编译失败结构化诊断+冷路径权威恢复；旧"事务化回滚"措辞与代码不符）
- Why Not Blocking Closure: 方向 fail-safe（失败=红+诊断，无假绿）；自实现事务在零假绿约束下被裁定不成立
- Successor Required: no
- Successor Path: 无（design 05 已改）

### mvnd 多模块变更链实验轴

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 单模块序列已回答 roadmap 问题（mvnd 优势仅在冷启动段）；多模块链是延伸语料
- Successor Required: no
- Successor Path: 无（需要时再立项）

## Closure

Status Note: Wave 8 收口：六维对照（mvnd 零增量代码等事实核正）、实证对照（编辑验证环 mvnd 89.3s vs ib check 38.5s）、Gradle 五项裁定、Maven 4 flavor 钉 3 入库（77/77 绿内）、design 05 事务化输出裁定原地更新。
Completed: 2026-11-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（fresh session, read-only）— verdict APPROVED（findings F1-F3 已修）
- Evidence:
  - Plan+P1-3 证据: 两份 analysis 在档（六维全 verdict、五项 Gradle 裁定、实证表含 min-of-2 数字）；TestMaven4FlavorPins 3 tests 在套内；PASS
  - mvnd 事实抽查: `grep -ri incremental --include=*.java` 零命中（零增量代码）；WatchServiceCacheFactory.java:46-48 在档；PASS
  - roadmap Wave 8 勾选逐条可溯，audit 行收口时才勾；PASS
  - `./mvnw test -pl nop-tools/nop-ib`: BUILD SUCCESS；surefire 汇总 77 tests / 0 fail（F3 复核=77）
  - 诚实性: ib test≠compile 语义分列、mvnd no-op 假绿陷阱披露、Deferred 分类（stash/restore watch-only、多模块轴 out-of-scope）；PASS
  - check-plan-checklist --strict 退出 0（completed 后验证）
