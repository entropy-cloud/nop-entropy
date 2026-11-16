# Wave 8 — mvnd 对比 + Gradle/mvnd 经验吸收

> Plan Status: active
> Last Reviewed: 2026-11-16
> Source: [nop-ib-roadmap.md](../../backlog/nop-ib-roadmap.md) Wave 8
> Related: [wave7-performance-experiments.md](wave7-performance-experiments.md)（bench harness 复用）、[04-gradle-incremental-compilation-analysis.md](../../design/nop-incremental-build/04-gradle-incremental-compilation-analysis.md)

## Purpose

与 `~/sources/mvnd`（Maven daemon）做六维机制对照 + 实证 wall-clock 实验，对 Gradle 经验清单逐项裁定（吸收→Feature+钉；不吸收→记录理由），Maven 4 解析差异点补钉，结论落 `ai-dev/analysis/`。

## Current Baseline（2026-11-16 live repo）

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

Status: planned
Targets: `~/sources/mvnd`、`ai-dev/analysis/`

Item Types: `Proof`

- [ ] 读 mvnd 源码/文档：client-daemon 协议、incremental builder、事件监听、构建缓存扩展、测试执行路径、失效策略
- [ ] 六维对照表 + 每维"可吸收/不可吸收+理由"（对照 nop-ib 硬约束逐条裁定）
- [ ] 落 `ai-dev/analysis/`（时效性文档，Status: open）

Exit Criteria:

- [ ] 对照报告入库，六维全覆盖，每维显式裁定
- [ ] daily log 条目

### Phase 2 — 实证 wall-clock 对照

Status: planned
Targets: 小型已测项目（Juggle 或 liteflow 单模块）、`ai-dev/analysis/`

Item Types: `Proof`

- [ ] mvnd 可用性验证（daemon 启动 + 项目构建成功）
- [ ] 同一变更序列（单文件方法体→ABI 扩散→测试失败→修复→全绿）在 mvnd 与 ib 上分段计时对照
- [ ] 对照表 + 结论（含"mvnd 失效即全量兜底 vs ib 索引选择性"的实测体现）

Exit Criteria:

- [ ] 同序列分段计时表入库（两侧可比口径）
- [ ] 结论段回答"ib 的快路径在哪些段优于/劣于 mvnd"
- [ ] daily log 条目

### Phase 3 — Gradle 经验裁定 + Maven 4 钉

Status: planned
Targets: `ai-dev/analysis/`、`nop-tools/nop-ib/src/test/java/io/nop/ib/**`

Item Types: `Decision | Feature | Follow-up`

- [ ] 事务化输出裁定（对照 ib 先删后编+stash 现状）
- [ ] content-addressable build cache 裁定（对照 Deferred 26，是否翻案）
- [ ] 文件系统监听裁定（对照 Deferred 26 前半）
- [ ] configuration cache / 模型过期策略对照裁定
- [ ] worker API 隔离复用对照（design 06 worker 池）
- [ ] Maven 4 解析差异钉：`maven.compiler.release`、新 pom flavor 在 EffectivePomParser 测试中的钉（本仓 wrapper=Maven 4 即活体语料）
- [ ] 每项：吸收（Feature+钉）或不吸收（记录"零假绿/不替换构建系统约束下不成立"理由）

Exit Criteria:

- [ ] 五项 Gradle 经验每项显式裁定
- [ ] Maven 4 差异点钉全部入 `src/test/java/io/nop/ib/` 且全绿
- [ ] daily log 条目

### Phase 4 — 文档收口 + closure

Status: planned
Targets: design 05、roadmap Wave 8

- [ ] 对照/实验结论落 analysis；采纳项 design 变更原地更新
- [ ] roadmap Wave 8 checklist 勾选 + Status
- [ ] 独立子 agent closure audit APPROVED，Evidence 落本 plan
- [ ] `./mvnw test -pl nop-tools/nop-ib` 全绿；提交

Exit Criteria:

- [ ] 六维对照 + 实证表 + 五项裁定 + Maven 4 钉全部入库
- [ ] closure audit APPROVED
- [ ] daily log + 提交

## Closure Gates

- [ ] 六维对照报告（每维显式裁定）
- [ ] 实证 wall-clock 对照表
- [ ] Gradle 五项 + Maven 4 钉全部落账
- [ ] `./mvnw test -pl nop-tools/nop-ib` 全绿
- [ ] 独立 closure audit APPROVED
- [ ] daily log + 提交

## Deferred But Adjudicated

（执行中填充）

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence: （收口时填写）
