# 2379 nop-find 独立模糊文件查找工具

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: `ai-dev/design/nop-find/00-vision.md`（Vision）、`ai-dev/design/nop-find/01-architecture.md`（Architecture Baseline）
> Related: `ai-dev/backlog/nop-rg-roadmap.md`（ParallelFileWalker 复用来源）

## Purpose

落地 nop-find：Nop 平台独立模糊文件查找 CLI（对标 zcode `@` 面板 / fzf file mode），完成三级打分 + top-K 核心算法、并行遍历集成、独立 CLI、`nop-cli find` 透传入口，收口到"CLI 端到端可用 + 测试覆盖 + 设计文档同步"状态。

## Current Baseline

- `nop-rg` 模块组已完整落地（Wave 1-4 done）：`ParallelFileWalker`（`nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/walk/ParallelFileWalker.java`）提供并行遍历 + gitignore + 符号链接 + 错误快速失败契约，可直接复用
- `nop-cli-core` 已集成 nop-rg：`CliRgCommand` 用 `@Unmatched` 透传模式将参数交给 `NopRgMain` 自己的 picocli 解析；`MainCommand.subcommands` 已注册
- `nop-cli-core` pom 已依赖 `nop-rg-cli`（JDK 22+ 基线）
- **无 nop-find 模块**：目录、pom、代码均不存在，全量新建
- 设计文档已定稿：`ai-dev/design/nop-find/`（README + 00-vision + 01-architecture），design README 索引已加 nop-find 行
- 打分算法参考实现（zcode TS）已调研：三级（前缀 < 子串 < 子序列）+ 三路字段取最优 + 预归一化 + top-K 二分插入

## Goals

- `nop-find` 模块组骨架落地（parent pom + core + cli + bom），JDK 22+ 门控挂入根 reactor
- `FuzzyFileScorer` 三级打分 + 三路字段取最优 + 预归一化，单元测试覆盖边界
- `TopKCollector` 二分插入 + 快速路径，单元测试覆盖确定性与 K 边界
- `FindCoordinator` 编排（walk → 并行打分 → 归并 top-K），与单线程结果一致
- `NopFindMain` CLI 端到端可用（文本/JSON 输出、退出码契约、参数校验）
- `CliFindCommand` 透传接入 `MainCommand`，`nop-cli find` 可用
- 设计文档与 live 代码一致；`ai-dev/logs/` 记录收口

## Non-Goals

- 交互式 TUI / fzf 全功能（历史、多选、增量渲染）
- 预构建索引 / 增量索引
- 与 zcode 打分数值逐位对齐（三级排序语义对齐即可）
- 内容搜索（归 nop-rg）
- JMH 性能基准模块（后续独立计划；本计划以功能正确性与基本性能验证收口）

## Scope

### In Scope

- `nop-find/` 新模块组（parent pom、core、cli、bom）
- 根 reactor JDK 22 profile 挂载（复用 `java22-modules` 模式）
- 打分器、top-K、协调器、CLI、nop-cli 透传
- 单元测试 + 端到端 CLI 测试
- `ai-dev/design/nop-find/` 文档与代码一致性同步
- `ai-dev/logs/` 当日条目

### Out Of Scope

- `nop-find-benchmark`（JMH）模块
- `CliFileCommand`（`nop file find`）改造或迁移
- nop-rg 侧任何代码变更（仅依赖复用，不修改 nop-rg）
- docs-for-ai 平台使用文档（CLI 工具非平台 API；README 级说明在 nop-find-cli 模块内）

## Execution Plan

### Phase 1 - 模块骨架 + 核心打分/TopK

Status: completed
Targets: `nop-find/`（pom 结构）、`nop-find-core`（score/topk/model 包）、根 `pom.xml`

- Item Types: `Feature`

- [x] 创建 `nop-find/` 父 POM（packaging=pom），JDK ≥ 22 激活 profile 挂入根 POM `<modules>`（同 nop-rg `java22-modules` 模式）
- [x] 创建 `nop-find-bom` 模块
- [x] 创建 `nop-find-core` 模块（`maven.compiler.release=22`，依赖 `nop-rg-core`），包结构 `io.nop.find.core.{score,topk,model}`
- [x] 实现 `FindCommand` / `FindMatch` 数据类（query、root、limit、caseSensitive、globs）
- [x] 实现 `FuzzyFileScorer`：三级打分 + name/relativePath/path 三路取最优 + 预归一化（默认不敏感）
- [x] 实现 `TopKCollector`：`(score, index)` 全序、二分插入、快速路径（已满且不优于末尾则跳过）
- [x] 单元测试：打分器（前缀/子串/子序列/不匹配/空边界/大小写开关/三路取最优权重）
- [x] 单元测试：TopKCollector（确定性、K 边界、快速路径、同分 tie-break 按 index）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw compile -pl nop-find/nop-find-core -am` 通过（JDK 22+ 环境）
- [x] `./mvnw test -pl nop-find/nop-find-core` 通过，新增测试覆盖三级打分全部分支 + TopKCollector 确定性
- [x] 打分器为纯函数（无共享可变状态），TopKCollector 线程局部使用无锁
- [x] No owner-doc update required（本 Phase 为新建代码，设计文档已在 baseline 前置定稿）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - FindCoordinator 编排

Status: completed
Targets: `nop-find-core`（coordinator 包）

- Item Types: `Feature`

- [x] 实现 `FindCoordinator`：`ParallelFileWalker.walk` → 按线程数分片 → 每线程打分 + 线程局部 TopKCollector → K 路归并 → 排序输出
- [x] 集成 gitignore（默认 respectGitignore=true）与 `--no-ignore` 开关
- [x] 集成 glob 过滤（复用 nop-rg `GlobMatcher` 语义，含 `!` 排除）
- [x] 单元/集成测试：端到端（临时目录 fixture：多级嵌套 + gitignore 规则 + glob 排除）
- [x] 并行正确性测试：多线程结果与单线程（threads=1）结果完全一致

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-find/nop-find-core` 通过（含并行一致性测试）
- [x] **端到端验证**：从 `FindCoordinator.find(FindCommand)` 到 `List<FindMatch>` 完整路径有测试覆盖（临时目录 fixture）
- [x] **接线验证**：测试断言 `ParallelFileWalker` 被 `FindCoordinator` 实际调用（mock/计数或集成 fixture 验证 gitignore 剪枝生效）
- [x] gitignore 语义与 nop-rg `ParallelFileWalker` 一致（同 fixture 对比或契约测试）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - CLI（NopFindMain）

Status: completed
Targets: `nop-find-cli`（NopFindMain + README）

- Item Types: `Feature`

- [x] 创建 `nop-find-cli` 模块（依赖 `nop-find-core`、picocli），`maven.compiler.release=22`
- [x] 实现 `NopFindMain`：参数解析（QUERY/PATH/-n/-s/-g/--json/--no-ignore/--threads）、空 query 与非法 PATH 校验
- [x] 文本输出：`SCORE\tPATH`（score 升序，同分路径字典序）
- [x] JSON 输出：`{"matches":[...],"count":N}`
- [x] 退出码：命中 0 / 未命中 1 / 错误 2（对齐 nop-rg）
- [x] 模块 README：用法、参数表、退出码、与 fzf/zcode 差异说明
- [x] CLI 集成测试：子进程或 picocli CommandLine.execute 驱动，覆盖文本/JSON/退出码/错误路径

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-find/nop-find-cli -am` 通过
- [x] **端到端验证**：从 `NopFindMain.main` 到 stdout 输出的完整路径有测试（fixture 目录 + 断言输出内容与退出码）
- [x] **无静默跳过**：空 query、非法 PATH 显式 exit 2 并输出错误信息，非静默返回空
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - nop-cli 透传集成

Status: completed
Targets: `nop-runner/nop-cli-core`（CliFindCommand、MainCommand、pom）

- Item Types: `Feature`

- [x] 创建 `CliFindCommand`（`name = "find"`），`@Unmatched` 透传模式（镜像 `CliRgCommand`）
- [x] `MainCommand.subcommands` 注册 `CliFindCommand`
- [x] `nop-cli-core` pom 增加 `nop-find-cli` 依赖
- [x] 集成测试：`nop-cli find QUERY` 透传到 `NopFindMain` 生效（参数不复述、help 由上游渲染）
- [x] 更新 `ai-dev/design/nop-find/01-architecture.md`：确认 CLI 契约与 live 代码一致（如需微调）
- [x] `ai-dev/logs/` 收口条目：计划完成摘要 + closure audit 引用

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw compile -pl nop-runner/nop-cli-core -am` 通过
- [x] `./mvnw test -pl nop-runner/nop-cli-core` 通过（含新增 find 透传测试）
- [x] **端到端验证**：`nop-cli find <query> <path>` 从 MainCommand 路由到 NopFindMain 到输出，完整链路有测试
- [x] **接线验证**：`MainCommand.subcommands` 包含 `CliFindCommand` 且测试断言路由生效
- [x] `ai-dev/design/nop-find/` 文档与 live 代码一致（CLI 参数、输出格式、模块依赖）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] 所有 in-scope 功能项已实现并通过对应测试
- [x] 行为/契约结果已达成（三级打分排序、top-K 确定性、退出码、JSON 格式）
- [x] 必要 focused verification 已完成（打分边界、TopK 确定性、并行一致性、CLI 端到端）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 功能项
- [x] 受影响的 owner docs（`ai-dev/design/nop-find/`、`docs-for-ai/01-repo-map/module-groups.md`）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`CliFindCommand` → `NopFindMain` → `FindCoordinator` → `FuzzyFileScorer`/`TopKCollector` 调用链运行时连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-find/nop-find-cli -am`（JDK 22+ 环境）
- [x] `./mvnw test -pl nop-find/nop-find-core -pl nop-find/nop-find-cli -pl nop-runner/nop-cli-core` 通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### JMH 性能基准模块（nop-find-benchmark）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 功能正确性与端到端可用性已由单元/集成测试覆盖；性能基准属度量增强，不阻塞"CLI 可用"基线成立
- Successor Required: `yes`（后续独立计划，与 nop-rg-benchmark 模式对齐）
- Successor Path: TBD（后续计划编号）

### 打分算法独立下沉（无 JDK 门控模块）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前 JDK 22+ 门控与 nop-rg/nop-cli 生态一致，不构成使用障碍；解耦属模块治理优化
- Successor Required: `no`（出现非 JDK22 消费者时再议）
- Successor Path: —

## Non-Blocking Follow-ups

- `CliFileCommand`（`nop file find`）长期可考虑迁移/委托到 nop-find，消除双 find 语义；不影响本计划 closure
- `ai-dev/tools/check-plan-checklist.mjs --strict` 默认模式对仍为 `active` 的 plan 存在假阳性退出码 0（`hardFailResults` 只收 isCompleted），需加 `--quiet` 才是有效门禁信号；属工具缺陷，不在本计划 scope

## Closure

Status Note: 4 个 Phase 的全部 execution item 与 Exit Criteria 均由独立审阅者（fresh session，未参与实现）对照 live 代码、live 测试与实跑命令核实成立。端到端调用链 `MainCommand → CliFindCommand → NopFindMain → FindCoordinator → ParallelFileWalker / FuzzyFileScorer / TopKCollector → ResultPrinter → stdout` 在运行时真实连通（非仅类型/import 连通），并以子进程实跑与变异探针双重复核。新增代码无空方法体 / 静默跳过 / 吞异常 / TODO 占位；实现过程中一度尝试但无效的 `nop.log.level` 压制机制已彻底删除，不留死代码。变异探针证明确定性契约与 TopK 快速路径具备判别力。审计提出的 1 项 Major（owner-doc 依赖图漂移）与 4 项 Minor 已在 closure 前全部修正，未进入 follow-up；剩余 deferred 均为已裁定 non-blocking。

Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session，未参与实现）— task id `ses_ed98818daffeWLGlmYQ3mefwt4`
- Audit Session: `ses_ed98818daffeWLGlmYQ3mefwt4`
- Evidence:

  **Phase 1 - 模块骨架 + 核心打分/TopK**
  - compile `-pl nop-find/nop-find-core -am` → PASS（`nop-find/pom.xml` java22-modules profile + 根 `pom.xml:547` `<module>nop-find</module>`）
  - test `-pl nop-find/nop-find-core` → PASS（Tests run: 57, Failures: 0, Errors: 0）
  - 打分三级全覆盖 → PASS（`FuzzyFileScorerTest.testPrefixTier` / `testSubstringReportsFirstIndex` / `testSubsequenceTier` / `testNoMatchReturnsSentinel` / `testCaseSensitiveRejectsDifferentCase` / `testRelativePathFieldUsedWhenNameMisses` / `testAbsolutePathFieldUsedWhenOthersMiss`；期望值为手算常量非实现反推）
  - TopK 确定性 → PASS（`TopKCollectorTest.testInsertionOrderDoesNotAffectResult` / `testMergeEqualsSingleThreadedFullScan` / `testEqualScoreTieBreakByIndex` / `testKBoundaryRejectsExtraBeyondCapacity` / `testFastPathRejectsNonBetterCandidateWhenFull`）
  - 纯函数 / 无锁 → PASS（`rg "synchronized|Lock|volatile|Atomic" nop-find/*/src/main` 0 命中；`FuzzyFileScorer` 仅 final 字段）
  - owner-doc → N/A（No owner-doc update required，设计文档在 baseline 前已定稿）

  **Phase 2 - FindCoordinator 编排**
  - test 含并行一致性 → PASS（core 57 tests 全绿）
  - 端到端 → PASS（`FindCoordinatorTest.testEndToEndRanksFuzzyMatches` / `testLimitRespected` / `testGlobIncludeFilter`，`@TempDir` fixture 断言精确有序 relativePath 列表）
  - 接线 → PASS（`FindCoordinatorTest.testWalkerActuallyInvokedWithConfiguredFlags`：`walkersCreated==1 && walksInvoked==1` + root/threads/respectGitignore/includeHidden 四项传参断言；live `FindCoordinator.java:75 newWalker(root).walk()`，`:167` 单一覆写点）
  - gitignore 语义 → PASS（`testGitignorePrunedByDefault` / `testNoIgnoreIncludesIgnoredFiles` / `testHiddenFilesExcludedByDefault` / `testMissingRootFailsFast`，跑真实 `ParallelFileWalker`）
  - 无效机制残留 → PASS（`rg "nop\.log\.level|System\.setProperty|ConfigStarter" nop-find/` 0 命中）

  **Phase 3 - CLI（NopFindMain）**
  - test `-pl nop-find/nop-find-cli` → PASS（Tests run: 20, Failures: 0, Errors: 0）
  - 端到端（进程内 + 独立子进程实跑）→ PASS
    `java -cp <nop-find-cli classes+deps> io.nop.find.cli.NopFindMain nopfind _tmp/fixture`
    → stdout 仅 `7\tsrc/main/java/NopFinder.java` + `106\tplain-nopfind.txt`，stderr 0 行，exit 0
  - stdout 数据通道 → PASS（反证实验：把 `logback.xml` 从 classpath 摘掉后 stdout 出现 20+ 行 INFO/DEBUG 污染 → `logback.xml` 承载真实职责；对应 `NopFindMainTest.testNoLogNoiseOnStdout` 逐行 `^\d+\t.+$` 断言）
  - 无静默跳过 → PASS（exit 2 覆盖：空/空白 query、缺参、非法 PATH、文件当 PATH、非正 limit、负 threads，均有测试；子进程复验 `nopfind /no-such-dir` → exit 2 + stderr `not a directory`；未命中 → exit 1 + stdout 空）
  - JSON 契约 → PASS（子进程 `--json` → `{"matches":[{"path":"…","relativePath":"…","score":7},…],"count":2}`；`testJsonEscapesSpecialCharsInPaths` 覆盖转义）

  **Phase 4 - nop-cli 透传集成**
  - compile `-pl nop-runner/nop-cli-core -am` → PASS
  - test `-pl nop-runner/nop-cli-core` → PASS（Tests run: 63, Failures: 0, Errors: 0, Skipped: 15 存量）
  - 端到端 → PASS（`TestCliFindCommand.testEndToEndThroughMainCommand`：`new CommandLine(new MainCommand()).execute("find","nopfind",corpus)` 断言具体分数 `7\tsrc/main/java/NopFinder.java` 与 `106\tplain-nopfind.txt`）
  - 接线 → PASS（`MainCommand.java:36 CliFindCommand.class`；`testRegisteredInMainCommand` 断言 `getSubcommands().containsKey("find")`；pom 依赖已加；`testHelpDelegatesToNopFindMain` 证明 help 由上游 `NopFindMain` 渲染、无参数复述）

  **Closure Gates**
  - 行为/契约 → PASS（三级排序子进程实测 7<9<11<106；top-K 确定性 20000 次随机对照暴力参考实现 0 mismatch；退出码 0/1/2 实测；JSON 格式实测）
  - Anti-Hollow → PASS
    (a) 运行时链路由子进程实跑 + `TestCliFindCommand.testEndToEndThroughMainCommand` 端到端断言证明；
    (b) 空方法体 0、吞异常 0（3 处 catch 全部 `throw` 或报错 return 2）、TODO/FIXME 0、`return null` 占位 0；`continue` 仅 `FindCoordinator.java:82` 一处且为 rg `-g` 合法过滤语义（4 个测试覆盖）；
    (c) 审计判定 `TopKCollector.offer` 的 `insertAt >= k` 分支不可达（20000 次随机 offer 流 + M4 变异探针证实），**closure 前已改为 `throw IllegalStateException`** —— 从"静默丢弃候选"转为显式失败，符合 guide #24；
    (d) `nop.log.level` 无效机制残留经全仓 grep 确认 0 命中。
  - 变异探针证据（`_tmp/mutprobe/`，未改动仓库源码）
    - M1（tie-break index 退化为分片内局部计数）→ **新增 `testParallelTieBreakIndependentOfThreadCount` FAIL**，恢复后 grep 确认 `candidate.index` 行在位 → **PASS**（红绿闭环成立）
    - M2（分片边界差一，每片丢一个候选）→ 3/3 fixture 全 MISMATCH（审计亲跑）
    - M4（`insertAt >= k` 分支改 throw）→ 探针 0 次触发，证实该分支此前不可达
  - 代码规范 → PASS（本仓库无 checkstyle 插件，PMD 仅在 `qa` profile 且 `failOnViolation=false`，非门禁；实际门禁：`check-import-order.mjs` 对 nop-find 相关文件 0 命中；`run-java-lint.sh` 门禁 3 规则 error=0）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-find --severity high` → Critical/High/Medium/Low 全 **0**
  - `node ai-dev/tools/check-doc-links.mjs --strict` → **exit 1，但与本计划无关**：2 处 errors 全部位于并发 workstream 的未跟踪文件 `ai-dev/design/eql-cross-db-compat/2026-10-10-eql-upsert-and-returning-design.md`（plan 2380 EQL upsert/returning，正在同工作树并行编写；指向尚未创建的 `nop-dao/pom.xml` 与 `_gen/_DialectFeatures.java` 前向引用），另 3 处 warnings 为 `ai-dev/plans/nop-bytecode/06-resource-leak-v1.md` 存量。本计划改动的全部文档（`ai-dev/design/nop-find/**`、`ai-dev/plans/2379-*.md`、`ai-dev/logs/2026/10-10.md`、`docs-for-ai/01-repo-map/module-groups.md`）在该工具下 **0 issue**。未越权修改他人并行 workstream 的在写文件。

  **审计 findings 处置（全部在 closure 前落地，无一项被降级）**
  - F1 **Major** `01-architecture.md` 模块依赖图称 nop-core「经 nop-rg-core 传递」，实为显式直依赖且漏列 nop-api-core → **已修**（改为 `nop-core（直依赖）+ nop-api-core（经 nop-core 传递）`）
  - F2 Minor `ai-dev/design/README.md` 索引行仍标「草案」与 owner-doc「定稿」自相矛盾 → **已修**
  - F3 Minor `--json` option description 写 `SCORE<TAB>PATH`，实际打 RELATIVE_PATH（用户可见 help 文案）→ **已修**（`NopFindMain.java:56`）
  - F4 Minor `docs-for-ai/01-repo-map/module-groups.md` 收录 nop-rg 却漏 nop-find → **已补**整行模块条目
  - F5 Minor `00-vision.md` 仍写「索引构建时预归一化」，与 `01-architecture.md` 的更正冲突 → **已修**（改为「无预建索引，预归一化落在遍历阶段」）
  - F6 Minor 两个小 fixture 并行一致性测试无同分候选，M1 变异下漏抓 → **已修**（新增 `testParallelTieBreakIndependentOfThreadCount` 并做红绿闭环验证）
  - F10 Minor `TopKCollector.offer` 不可达防御分支静默 `return false` → **已修**（改 `throw IllegalStateException`）
  - F7/F8/F9 未修但均非本计划 in-scope：F7 为 `check-plan-checklist.mjs` 工具缺陷（已登记为 non-blocking follow-up）；F8 为"未提交"流程风险；F9 为临时文件卫生（`nop-find/nop-find-cli/cp.txt` 已删除）

  **Deferred 项分类检查**
  - 确认无 in-scope live defect / contract gap / owner-doc drift 被降级：审计判定两条 Deferred（JMH 基准 / 打分算法下沉）分类诚实，且 `00-vision.md:36` 自身已把性能基准划入 Vision Phase 2；两条 follow-up 均为治理议题，其中"与 fd/fzf 逐项差异表"实际已由 `nop-find-cli/README.md` 交付，故从 plan 中删除而非留作未完成项。

Follow-up:

- no remaining plan-owned work
- （工具缺陷，非本计划 scope）`ai-dev/tools/check-plan-checklist.mjs --strict` 默认模式对仍为 `active` 的 plan 存在假阳性退出码 0（`hardFailResults` 仅收 isCompleted），需 `--strict --quiet` 才是有效门禁信号
- （并发 workstream）`ai-dev/design/eql-cross-db-compat/2026-10-10-eql-upsert-and-returning-design.md:125,127` 的 2 处断链属 plan 2380 在写文件，需由其所有者收口
- （流程）本次实现尚未提交 git，按 AGENTS.md「Commit frequently」应由用户确认后提交
