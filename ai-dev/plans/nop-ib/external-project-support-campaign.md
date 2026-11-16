# nop-ib 外部项目支持度持续实测（campaign plan）

> Plan Status: completed
> Last Reviewed: 2026-11-16
> Completed: 2026-11-16
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

Status: completed
Targets: `ai-dev/plans/nop-ib/external-project-support-matrix.md`（新建登记面）、`~/sources`

- Item Types: `Proof`

- [x] 对 `~/sources` 下全部含 `pom.xml` 的项目做机器化资格判定（构建系统、JUnit 主栈、JDK 目标、是否有测试源、是否可 install），产出一张表（脚本 `_tmp/qualify-sources.mjs`，原始输出 `_tmp/qualify-sources.json`）。
- [x] 每行归入 `measured-parity | measured-preexisting | qualified-todo | excluded:<reason> | undecided` 之一（7 parity / 2 preexisting / 14 todo / 19 junit4-only / 2 无测试源）。
- [x] 对 `undecided` 行逐条裁定：最终 0 行 undecided；两项候选疑问（data-platform-open、flyway 无测试源）人工复核后归 `excluded:no-test-sources`，无 owner blocked 项。
- [x] 矩阵标注"派生视图"，权威来源是各项目 daily log 条目。

Exit Criteria:

- [x] 矩阵覆盖 `~/sources` 下全部 `pom.xml` 项目，无遗漏（总数 44，统计见矩阵表头）。
- [x] 每一行有明确分类，无空分类。
- [x] 已测项目的结论与 daily log 一致（抽查 3 个：liteflow 808/0、spring-batch 465/465 字节级、liquibase 6494/0，均与 `ai-dev/logs/2026/11-15.md` 阶段总结表一致）。
- [x] 登记面已声明"派生视图，以 daily log 为准"。
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/11-16.md`）。

### Phase 3 — 批量实测（≥3 个新项目 + zeebe 收口）

Status: completed
Targets: `~/sources/<project>`、`nop-tools/nop-ib`

- Item Types: `Fix | Proof`

- [x] zeebe：install 完成（branch-scoped 分段仓，缺陷 25 修复）→ `ib model refresh` → mvn 基线 → `ib test` → 类级差分 → 裁定（util 780/780、logstreams 1316/1316、gateway-grpc 462=mvn 精确；engine DeploymentRejection 25/1f 两侧同红；backup-stores 预存）。
- [x] 挑选实测：shardingsphere（446 poms 规模最大候选：5 infra 模块 69/369/40/87/5 全部精确同数，零新增缺陷）、flink-agents（新框架混编：12 模块 parity）、mvnd（Maven 4 项目：client/common/daemon 逐数精确+skipped 口径）；Juggle excluded:无有效测试；BroadleafCommerce 预存（Lombok/JDK26）。
- [x] 每个项目产出一个 daily log 条目（`ai-dev/logs/2026/11-16.md`，含命令、计数、差分、裁定）。
- [x] 发现的新缺陷在本项目内修复+回归钉+复验：缺陷 23（external test-jar 静默丢弃）/24（无 wrapper 项目回退 PATH mvn）/25（分段本地仓布局）/27a（vintage engine 缺失）/27b（excludedGroups 透传）/28（@Nested-only 容器提升）/30（surefire excludes 过滤）——全部带钉复验；候选 26 判定撤销（over-dispatch 回退）；缺陷 29（Enclosed 双跑）登记 Deferred。
- [x] 每个项目明确裁定：zeebe/flink-agents/shardingsphere/mvnd=parity（部分预存），BroadleafCommerce=预存，Juggle=excluded。

Exit Criteria:

- [x] 至少 4 个项目（含 zeebe）有 daily log 条目与明确裁定：**5 个**（flink-agents、zeebe、shardingsphere、mvnd、BroadleafCommerce；另 Juggle excluded 登记）。
- [x] 新增缺陷全部修复且带回归钉：23/24/25/27/28/30 各带钉（TestSurefireFallbackArgs 5、TestMavenFallbackSelection 2、TestClasspathMaterializer 2、TestEffectivePomParser 1、TestExcludedTagFilter 1、TestNestedOnlyContainerPromotion 1、TestSurefireClassFilterExcludes 1）；缺陷 29 为 Deferred watch-only（over-dispatch 安全向）。
- [x] 预存失败全部给出根因与两侧同红证据：Mongo（spring-batch-core mvn 1e=ib 1f 同源）、zeebe backup-stores（云模拟器超时）、DeploymentRejection 25/1f 两侧同红、BroadleafCommerce Lombok/JDK26（mvn 主源码即红）。
- [x] 每轮修复后 `nop-tools/nop-ib` 全绿（本轮时点 53/53；Phase 4 缺陷 31 两钉后终态 55/55），且零回退：spring-batch 复跑 906 执行=mvn 904（差 2=skipped 口径），1 失败=Mongo 两侧同红。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 — 实测 runbook 固化

Status: completed
Targets: `ai-dev/plans/nop-ib/external-project-support-matrix.md`（合并为 runbook + matrix）或新建 runbook 文件

- Item Types: `Feature`

- [x] 写出标准流程：资格判定 → install 前置（含 test-jar/`-Dmaven.test.skip` 陷阱、enforcer）→ model refresh → mvn 基线（禁 build-cache）→ ib compile → ib test → 类级差分脚本口径 → 裁定分级。
- [x] 写入预存环境失败清单（SecurityManager、Groovy major、Thread.stop、无 DB/Docker）作为"遇到即登记，不调查"的速查表。
- [x] 写入"框架惯例 → 索引标记"对照表（Jupiter 注解 / vintage / Spock feature / JUnit3 suite / 继承外部基类），并指向对应回归钉。
- [x] 由独立子 agent 按 runbook 对一个**未测项目**做一次试跑，验证 runbook 可被独立执行（jsr352/JBeret：5 模块 parity + 缺陷 31 发现并修复 + 3 个 runbook 缺口补齐）。

Exit Criteria:

- [x] runbook 覆盖流程全部步骤，每步有可复制命令。
- [x] 独立 agent 按 runbook 试跑成功（或记录其暴露的缺口并补齐）——成功，缺口已补齐（-pl 语法/fork 根因定位/速查表 provision 条目）。
- [x] 框架惯例对照表的每一行都能定位到索引器代码与回归钉。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Risks And Watch Items

- **长尾无穷**：框架惯例（族 C）可能持续出现新变体。缓解：Phase 4 的对照表 + 每个新惯例一个钉，使增量成本为常数。
- **环境脆弱**：外部项目 install 依赖网络与本地仓库状态（flink 曾遇 s3 test-jar / dist pom / 空间瞬时不足）。缓解：runbook 记录兜底命令。
- **`-T1C` 并行 install 触发 artifact 锁超时**（zeebe 实测）：多线程并发解析同一 artifact 时 `Could not acquire shared/exclusive lock ... in 900 SECONDS`，导致多个模块 FAILURE，实为环境竞态非项目问题。缓解：大项目 install 用**串行** + `-Daether.syncContext.named.time=3600`，必要时加 `-Dspotless.check.skip=true -Drevapi.skip=true`。
- **build-cache 污染基线**：Syncope 已证伪。硬约束：所有 mvn 基线必须 `-Dmaven.build.cache.skipCache=true`。
- **JDK26 兼容性噪声**：大量外部项目在 JDK26 下预存失败，会稀释信号。缓解：资格门 + 预存速查表。

## Deferred But Adjudicated

### zeebe-gateway-grpc ActivateJobsTest JUnit4 Enclosed 双重执行（ib 36 vs mvn 18）

- Classification: `watch-only residual`
- Why Not Blocking Closure: over-dispatch 安全向（多跑不漏跑，且多跑部分全部通过）；限定于 JUnit4 `@RunWith(Enclosed.class)` + Parameterized 嵌套树被 vintage 与发现通道各执行一次的边角，其余 157+ 类全部逐数精确。修复需 vintage Enclosed 容器去重语义调查，属新独立面。
- Successor Required: `yes`
- Successor Path: 新建独立 plan（vintage Enclosed/Parameterized 容器去重）

### flink-agents-runtime fast-path fork JVM abort（exit 134）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 崩溃路径**诚实失败**（run=0/failed=1 + 显式 warning，非假绿）；`--runner maven` 回退路径（缺陷 20 修复后）提供忠实 oracle——实测 1209/270e/25s 与 standalone mvn 精确同数。单类 run 正常（CompileUtilsTest 11/0）、per-class batch=1 仍 134、4g 堆无效——根因疑为某测试类触发 fork JVM native abort，需独立重现；不影响其他 12 个模块的 fast-path parity 结论。
- Successor Required: `yes`
- Successor Path: 新建独立 plan（fork JVM abort 隔离与 forkError/forkStdoutTail 诊断信息透出到 run warnings）

## Non-Blocking Follow-ups

- zeebe 若 install 长时间不可完成，可移出本计划并登记为独立 follow-up（不阻塞其他项目推进）。

## Closure

Status Note: Phase 1–4 全部完成，独立子 agent closure audit APPROVED（无 Blocker/Major，1 Minor 已修）。外部项目支持度成为有证据链、可复核的常态结论：资格矩阵 44 项目全分类 0 undecided；累计 12 项目实测（7 parity / 2 预存 / parity+新增缺陷已修 flink-agents、zeebe、shardingsphere、mvnd / jsr352 试跑 parity）；nop-ib 55/55 绿；残留项全部 Deferred But Adjudicated 带 successor path。
Completed: 2026-11-16

Closure Audit Evidence:

Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_1cbbc653-5617-4292-82e1-b0e90b088b9c）

Audit Session: agent_1cbbc653-5617-4292-82e1-b0e90b088b9c

Evidence:
- Gate 1（Phase 1–4 Exit Criteria）PASS：逐条对照 live——Phase 1 `surefireFallbackArgs`=lifecycle test+双 flag（TestCommand.java:774-781，运行时经 MavenFallback.delegate 调用）、`extractSummaries` 剥离 per-class 行、TestSurefireFallbackArgs 5 钉；Phase 2 matrix 44 行 0 undecided + 派生视图声明；Phase 3 `./mvnw test -pl nop-tools/nop-ib` 实跑 55/55 绿、缺陷 25/27/28/30/31 修复点与调用链逐一定位（ClasspathMaterializer segmentedJar / JUnitForkExecutor vintage+applySurefireEnvironment / UnitIndex ownsDispatchableNested / SurefireClassFilter excludes / JUnitWorkerPool 双路径）；Phase 4 runbook 固化+jsr352 试跑落账。
- Gate 2（矩阵无 undecided）PASS；Gate 3（parity 证据链）PASS（gateway-grpc 462、util 780/780、logstreams 1316/1316、jsr352 逐数精确）。
- Anti-Hollow PASS：scan-hollow-implementations exit=0（critical/high 0）；修复点运行时调用链抽查成立。
- 工具退出码：check-plan-checklist --strict exit=0；scan-hollow exit=0。
- Deferred 项分类检查 PASS：Enclosed 双跑（over-dispatch 安全向）、flink-agents-runtime fork 134（诚实失败非假绿，回退 oracle 精确）；无 in-scope live defect 被降级。
- Minor（已修）：Phase 3 勾选行"53/53"补注 Phase 4 后终态 55/55。

## Closure Gates

- [x] Phase 1–4 全部 Exit Criteria 勾选。
- [x] 资格矩阵无 `undecided` 行。
- [x] 已达 parity 的项目集合有完整证据链（mvn 基线 + ib 结果 + 类级差分）。
- [x] 独立子 agent closure audit（不同 session）通过，证据写入本 plan 的 Closure 段。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-ib/external-project-support-campaign.md --strict` 退出码 0。
