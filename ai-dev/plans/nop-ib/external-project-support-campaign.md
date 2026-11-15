# nop-ib 外部项目支持度持续实测（campaign plan）

> Plan Status: active
> Last Reviewed: 2026-11-16
> Source: [ai-dev/analysis/2026-11/2026-11-15-nop-ib-external-project-support.md](../../analysis/2026-11/2026-11-15-nop-ib-external-project-support.md)（缺陷族 A–D 与预存清单）; [ai-dev/backlog/nop-ib-roadmap.md](../../backlog/nop-ib-roadmap.md)（Hard constraints 1–8）; [ai-dev/logs/2026/11-15.md](../../logs/2026/11-15.md)（缺陷 16–22 实测记录）
> Related: [ai-dev/design/nop-incremental-build/05-nop-ib-engine-design.md](../../design/nop-incremental-build/05-nop-ib-engine-design.md)、[06-junit-worker-reuse-and-generalization-design.md](../../design/nop-incremental-build/06-junit-worker-reuse-and-generalization-design.md)

## Purpose

把"外部项目实测 → 发现泛化缺陷 → 修复 → 复验 parity"从一次性战役变成**可重复的标准流程**，并在本计划生命周期内收口当前已知的残余面（候选缺陷 20、未测资格集），使"ib 对外部 Maven+JUnit5 项目达到精确 parity"成为**有证据支撑、可复核的常态结论**，而不是靠逐个项目碰运气。

## Current Baseline

- 已实测 8 个项目：OpenPDF / liteflow / pdfbox / spring-batch / Activiti / Syncope / flink / liquibase；flowable-engine 为第 9 个（预存裁定）；zeebe 进行中（install 66/149）。
- 已达精确 parity：liteflow 808/0、spring-batch 465/465 字节级、flink wave1 472/0、flink runtime-web 88/0、liquibase 6494/0（=mvn 6517−23 skipped）。
- 已修复缺陷 14–19、21、22（14/15 为 Syncope，16–19 为 flink，21/22 为 liquibase），每个均带回归钉；`nop-tools/nop-ib` 测试 40/40 绿。
- 已裁定为预存环境失败：flink core/runtime/streaming-java（SecurityManager 移除）、flink-yarn（同族 + hadoop）、flink-runtime `Thread.stop()`、flowable-engine（内置 Groovy 不支持 class major 70）、syncope（无 PG/Docker）。
- **真正剩余的 gap**：
  1. 候选缺陷 20 未裁定——`runSurefire` 回退直呼 `surefire:test` 忽略 phase 绑定，连带执行绑定在 integration-test phase 的 execution；
  2. 资格矩阵中仍有大批 Maven 项目未测（Maven+JUnit5 主栈的可测集远大于已测 9 个）；
  3. 缺陷族 C（框架惯例长尾）没有系统化的"新框架惯例 → 标记 + 回归钉"登记面；
  4. 每个项目的实测过程（安装前置、命令、对照口径）仍散在 daily log 中，未形成可复用 runbook。

## Goals

- 候选缺陷 20 得到明确裁定（修复或登记为有理由的 watch-only residual），且回退路径有回归钉。
- 资格矩阵从 `~/sources` 全量枚举得到**显式清单**（可测 / 排除 / 已测 / 待定），不再靠人工零散筛查。
- 至少再完成 3 个"Maven + JUnit5 主栈 + JDK26 可编译"项目的完整 parity 实测（含 zeebe）。
- 实测流程固化为 runbook，使后续任一 agent 能不看历史对话独立复现一次实测。
- 缺陷族 C/D 的新增触发点（若有）在同项目中即时修复，不做跨项目累积。

## Non-Goals

- 不为 JUnit4-only 项目做支持（owner 裁定：只承接 JUnit5/6 项目）。
- 不替换 Maven 作为冷路径/CI 构建（roadmap Hard constraints #5）。
- 不为 JDK26 不兼容的外部项目做兼容层（预存环境失败只登记，不修复）。
- 不做 Gradle 项目支持（模型来源是 effective-pom）。
- 不在本计划内做性能优化（骨架成本已有 bench 基线，属 Wave 4 item 26/27）。

## Scope

### In Scope

- `nop-tools/nop-ib` 的物化 / 派发 / 索引新鲜度 / 回退路径语义修复。
- `~/sources` 下 Maven 项目的资格枚举、实测、差分、裁定。
- 实测 runbook 与资格矩阵登记面。
- 回归钉与 `nop-tools/nop-ib` 测试集。

### Out Of Scope

- Nop 仓内 `docs-for-ai/` 的平台使用文档（仅当改动影响 Nop 仓内行为时才回写）。
- `nop-javac` 内部（Protected Area；若实测证明需要修改，单独走 plan-first）。
- 外部项目自身的构建问题修复。

## Execution Plan

### Phase 1 — 候选缺陷 20 裁定与回退路径收口

Status: completed
Targets: `nop-tools/nop-ib/src/main/java/io/nop/ib/cli/TestCommand.java`（`runSurefire` / `runMavenModuleTest`）、`nop-tools/nop-ib/src/test/java/io/nop/ib/cli/`

- Item Types: `Fix | Decision`

- [x] 复现：在 flink-fs-tests 上确认 `surefire:test` goal 直调连带执行 integration-tests execution（80 vs mvn `test` 生命周期 40）；记录两类差异（多执行的 execution、phase 绑定差异）。
  - 复现修正了机制认定：直调 goal（无 `-Dtest`）与生命周期同数（40/7/1）；真正的分叉是 **`-Dtest=` 显式类清单覆盖模块 surefire `<includes>`**（41/7/2，ITCase 连带派发）；**80/14/2 = extractSummaries 把 per-class 行（`Tests run: N ... -- in org.X`）当模块 summary 重复计数**（40 真实 + 40 重复）。
- [x] Decision：选定修复方向 **(a) 回退改走 lifecycle `mvn test` + flag 透传**（`-Denforcer.skip=true`（flink 锁 Maven 3.8.6，本机 3.9.12 enforcer 必红）+ `-Dmaven.build.cache.skipCache=true`）——唯一同时尊重 phase 绑定与 default-test execution 级配置（argLine/forkCount/excludedGroups/includes）的方向；(b) 无法恢复 execution 级配置，(c) 已证伪不成立（差异真实存在）。
- [x] 按裁定实施修复；新增回归钉断言"回退路径只执行单元执行面"——`TestSurefireFallbackArgs`（5 钉：lifecycle 非 goal、flag 透传、`-Dtest` 形态、full 模式无 `-Dtest`、per-class 行不进 summary）。
- [x] 若裁定为 (c)：不适用（裁定为修复，非 watch-only）。

Exit Criteria:

- [x] 候选缺陷 20 已落到 `landed`（修复方向 (a)）。
- [x] flink-fs-tests 回退造出的 tests 数与 mvn `test` 一致：修复后 `nop-ib test --runner maven -pl flink-fs-tests` 实测 **40/7/1**，与 `mvn -pl :flink-fs-tests test -Denforcer.skip=true` 基线 **40/7/1** 精确同数。
- [x] `./mvnw test -pl nop-tools/nop-ib` 全绿（45/45，原 40 + 新 5 钉）。
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/11-16.md`）。

### Phase 2 — 资格矩阵全量枚举与登记

Status: planned
Targets: `ai-dev/plans/nop-ib/external-project-support-matrix.md`（新建登记面）、`~/sources`

- Item Types: `Proof`

- [ ] 对 `~/sources` 下全部含 `pom.xml` 的项目做机器化资格判定（构建系统、JUnit 主栈、JDK 目标、是否有测试源、是否可 install），产出一张表。
- [ ] 每行归入 `measured-parity | measured-preexisting | qualified-todo | excluded:<reason> | undecided` 之一。
- [ ] 对 `undecided` 行逐条裁定（需要 owner 裁定的标 blocked 并写明问题）。
- [ ] 矩阵标注"派生视图"，权威来源是各项目 daily log 条目。

Exit Criteria:

- [ ] 矩阵覆盖 `~/sources` 下全部 `pom.xml` 项目，无遗漏（给出总数与统计）。
- [ ] 每一行有明确分类，无空分类。
- [ ] 已测项目的结论与 daily log 一致（抽查 3 个）。
- [ ] 登记面已声明"派生视图，以 daily log 为准"。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 — 批量实测（≥3 个新项目 + zeebe 收口）

Status: planned
Targets: `~/sources/<project>`、`nop-tools/nop-ib`

- Item Types: `Fix | Proof`

- [ ] zeebe：install 完成 → `ib model refresh` → mvn 基线（禁 build-cache）→ `ib compile` → `ib test` → 类级差分 → 裁定。
- [ ] 从 qualified-todo 中按"规模最大、框架混编风险最高"挑选 3 个项目，逐个走标准流程。
- [ ] 每个项目产出一个 daily log 条目（含命令、计数、差分、裁定）。
- [ ] 发现的新缺陷**在本项目内**完成"修复 + 回归钉 + 复验"，不跨项目累积。
- [ ] 每个项目明确记为 `parity` / `预存` / `新增缺陷已修` 三者之一。

Exit Criteria:

- [ ] 至少 4 个项目（含 zeebe）有 daily log 条目与明确裁定。
- [ ] 新增缺陷（若有）全部修复且带回归钉，无"待修"残留。
- [ ] 预存失败全部给出根因与"两侧同红"证据。
- [ ] 每轮修复后 `nop-tools/nop-ib` 全绿，且已测 parity 项目零回退（至少复跑一个已测项目）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 — 实测 runbook 固化

Status: planned
Targets: `ai-dev/plans/nop-ib/external-project-support-matrix.md`（合并为 runbook + matrix）或新建 runbook 文件

- Item Types: `Feature`

- [ ] 写出标准流程：资格判定 → install 前置（含 test-jar/`-Dmaven.test.skip` 陷阱、enforcer）→ model refresh → mvn 基线（禁 build-cache）→ ib compile → ib test → 类级差分脚本口径 → 裁定分级。
- [ ] 写入预存环境失败清单（SecurityManager、Groovy major、Thread.stop、无 DB/Docker）作为"遇到即登记，不调查"的速查表。
- [ ] 写入"框架惯例 → 索引标记"对照表（Jupiter 注解 / vintage / Spock feature / JUnit3 suite / 继承外部基类），并指向对应回归钉。
- [ ] 由独立子 agent 按 runbook 对一个**未测项目**做一次试跑，验证 runbook 可被独立执行。

Exit Criteria:

- [ ] runbook 覆盖流程全部步骤，每步有可复制命令。
- [ ] 独立 agent 按 runbook 试跑成功（或记录其暴露的缺口并补齐）。
- [ ] 框架惯例对照表的每一行都能定位到索引器代码与回归钉。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Risks And Watch Items

- **长尾无穷**：框架惯例（族 C）可能持续出现新变体。缓解：Phase 4 的对照表 + 每个新惯例一个钉，使增量成本为常数。
- **环境脆弱**：外部项目 install 依赖网络与本地仓库状态（flink 曾遇 s3 test-jar / dist pom / 空间瞬时不足）。缓解：runbook 记录兜底命令。
- **`-T1C` 并行 install 触发 artifact 锁超时**（zeebe 实测）：多线程并发解析同一 artifact 时 `Could not acquire shared/exclusive lock ... in 900 SECONDS`，导致多个模块 FAILURE，实为环境竞态非项目问题。缓解：大项目 install 用**串行** + `-Daether.syncContext.named.time=3600`，必要时加 `-Dspotless.check.skip=true -Drevapi.skip=true`。
- **build-cache 污染基线**：Syncope 已证伪。硬约束：所有 mvn 基线必须 `-Dmaven.build.cache.skipCache=true`。
- **JDK26 兼容性噪声**：大量外部项目在 JDK26 下预存失败，会稀释信号。缓解：资格门 + 预存速查表。

## Deferred But Adjudicated

- 无（当前无 deferred 项；Phase 1 若裁定 (c) 则在此登记）。

## Non-Blocking Follow-ups

- zeebe 若 install 长时间不可完成，可移出本计划并登记为独立 follow-up（不阻塞其他项目推进）。

## Closure Gates

- [ ] Phase 1–4 全部 Exit Criteria 勾选。
- [ ] 资格矩阵无 `undecided` 行。
- [ ] 已达 parity 的项目集合有完整证据链（mvn 基线 + ib 结果 + 类级差分）。
- [ ] 独立子 agent closure audit（不同 session）通过，证据写入本 plan 的 Closure 段。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-ib/external-project-support-campaign.md --strict` 退出码 0。

## Closure

Status Note: 计划刚创建，执行未开始；closure audit 待 Phase 1–4 完成后由独立子 agent 执行。

### Closure Audit Evidence

Reviewer / Agent: （待填）

Evidence:

```
（待 closure audit 填写：逐条 Exit Criterion 与 Closure Gate 的验证结果）
```
