# 2268 nop-rg 代码质量改进 —— 可读性与可维护性

> Plan Status: active
> Last Reviewed: 2026-09-20
> Source: 用户指令（改进 rg 代码质量/可读性/可维护性）；live repo 审计（2026-09-20，本 plan Current Baseline 全部经 grep/read 实证）；`ai-dev/design/nop-rg/01-architecture-baseline.md`（决策 3/5）
> Related: Plan 2262-2267（Stage 1 + Wave 1-4 + 性能收敛，均 completed）；plan 2267 终态 = 本计划的性能基线守护参照
> Draft Review: 第一轮独立子 agent 对抗性审查（agent_351c09f9-1590-4d7a-8079-ac8cbd306014）：6 Major + 5 Minor 全部修复（F4 种子事实错误、消费者清单、F5 指错文件、行数门槛、Rule 25 裁定、corpus 留痕方案），第二轮复核判定修完 4 个 Minor 后可执行（N1-N4 已折入）

## Purpose

在**行为零变化**的硬约束下，清理 nop-rg 的死公共 API、分解超大类、消除重复逻辑、统一风格，使代码库对后续维护者可读、可导航、可安全修改。所有既有测试（core 55 + cli 26 + vector 10 + large-file 3）必须保持绿色且语义不变——它们是行为不变的守护。

## Current Baseline

- 代码量：主代码 3126 行 / 测试 2227 行（5 模块：core/vector/cli/benchmark/bom）。错误消息全英文 ✓；无 TODO/FIXME/裸 RuntimeException ✓；无裸 printStackTrace ✓；javadoc 覆盖良好。整体健康，改进点为定向清理而非重构灾难。
- **F1（死公共 API，最高优先）**：`search/SearchRequest`、`search/SearchResult`、`search/MatchResult`（Wave 1 SRCH-03 数据类）**零生产调用方**（grep 实证：生产代码无引用）——Wave 2 的 `SearchCommand`/`FileMatches`/`LineMatch` 已取代其职责。现存消费者仅 2 个测试文件（`CoreSearchIntegrationTest`、`ScalarByteSearcherTest`）当 helper 用。死契约面误导读者以为它们是搜索管线类型。
- **F2（超大类）**：`SearchCoordinator` 457 行，混杂四种职责：(a) 搜索编排（search/searchFile/searchFileChunked）、(b) 结果聚合（aggregate/buildLineMatches/newLineMatch/decode）、(c) 二进制嗅探（isBinary）、(d) 三个嵌套结果类型 FileMatches/LineMatch/Submatch（约 95 行）。嵌套类型消费者（grep 实证）：cli `NopRgMain`/`JsonOutput`/`JfrSwitchTest`、benchmark `CoordinatorEndToEndBenchmark`、core 测试 `SearchCoordinatorTest`/`LargeFileSearchTest`；vector 模块零引用。
- **F3（死分支）**：`LineCursor.indexOf` 的 `target != LF` 与 `LF` 两个 for 循环逐字符相同（复制粘贴残留），读者需自行证明二者等价。
- **F4（结构性重复）**：benchmark `CorpusUtil` 的行构造逻辑（词表 + 逐行构建循环形状）在 `textBytes` 与 `ensureFile` 中各有一份拷贝。**注意：两方法输出本就逐字节不同**——`textBytes` 用 `new Random(seed)`、`ensureFile` 用 `new Random(seed ^ 0x5eed)`，种子派生刻意不同（不同 corpus 场景）。重复的仅是行构造结构，共享实现必须参数化 Random/种子派生，两处输出各自逐字节保持不变（否则同尺寸复用机制会静默掩盖 corpus 变化，污染 2267 基准可比性）。
- **F5（风格不一致）**：内联全限定名与既有 import 风格冲突——`SearchCoordinator`（`java.util.concurrent.ForkJoinPool`、`java.util.ServiceLoader`、`java.util.Iterator`、`java.util.ServiceConfigurationError`）、`ParallelFileWalker`（`java.util.concurrent.RecursiveAction`、`java.security.AccessControlException`）、benchmark `ScalarSearchBenchmark`（`java.nio.charset.StandardCharsets` ×2）、`CoordinatorEndToEndBenchmark`（`java.util.List.of()`）；`ParallelFileWalker` 的 `var stream` 与全库显式类型风格不一致；`JfrSwitchTest` 重复 import ×3（CommandLine/ByteArrayOutputStream/PrintStream）。
- **F6（长方法）**：`NopRgMain.call()` 62 行，混杂 setup/搜索/四种输出模式/cleanup；文本/计数/文件名三种输出内联（json 已有 `JsonOutput`）。
- **F7（重复分配）**：`SearchCommand.patternBytes()` 每次调用重新 UTF-8 编码（分块路径每文件调用一次；语义上模式不可变，可安全缓存）。
- **F8（导航缺失）**：9 个 main 包（core 及其 5 子包、cli、vector、benchmark）均无 `package-info.java`。
- **F9（陈旧 javadoc）**：`SearchCoordinator` 两处 "LITERAL/FOLDING" 策略描述（`FoldingByteSearcher` 已于 f5d44be31e 删除，Strategy 枚举无 FOLDING）。
- 测试基线：core 55（默认 surefire 54 run + 1 rg 门控 skipped；含 large-file 3 显式组）+ cli 26 + vector 10 = 91 常规 + 3 显式。CLI e2e 场景数：NopRgMainTest 9 + RgComparisonTest 9 + VectorModeTest 5 = 23。rg 对照 opt-in（`-Dtest.rg.compare=true`）可用。既有等价性护栏：BMH fuzz、--vector 一致性、RgComparisonTest、NopRgMainTest。
- nop-rg pom 无模块级 checkstyle（全局插件统一跑）；scan-hollow/doc-links 工具可用。
- 性能守护参照（plan 2267 终态，勿回退）：吞吐比 64MB 82.3%、512MB 中位 49.05%（本机含外部负载，详见 2267 记录表）；本计划不改任何搜索路径逻辑，性能特征随代码等价性自然保持。
- 提交基线：`a3a6f3a088`（plan 2267 收口）。

## Goals

- 删除死公共 API（F1）：`SearchRequest`/`SearchResult`/`MatchResult` 三类删除，两个测试文件改为自持 helper；全仓 grep 零残留。
- `SearchCoordinator` 单一职责分解（F2）：结果聚合逻辑提取为独立类；三个嵌套结果类型提升为 coordinator 包顶层类；`SearchCoordinator` 主类收敛到纯编排职责；清理 F9 陈旧 javadoc。
- 修复 `LineCursor.indexOf` 死分支（F3）。
- benchmark `CorpusUtil` 行构造逻辑去重（F4）——**两方法输出各自逐字节不变**（参数化 Random/种子派生）。
- 风格一致性（F5）+ CLI 输出抽取（F6）+ `SearchCommand.patternBytes()` 缓存（F7）+ `package-info.java` ×9（F8）。
- **行为零变化**：全部既有测试语义不变、全部通过（core 55 + cli 26 + vector 10 + large-file 3）；CLI 输出逐字节不变（由 RgComparisonTest/NopRgMainTest/VectorModeTest 守护）。
- 每个保留变更的 Phase 完成即 commit（防丢失 + 可回溯）。

## Non-Goals

- 不删除 `ByteSearchStrategy` 层（`ScalarByteSearcher`/`VectorByteSearcher` 是 design 决策 3/5 记录的策略契约与 vector 测试床，`selectAnchorIndex`/`buildSkipTable` 为 `PreparedLiteral` 生产复用）。
- 不改任何行为、CLI 语义、输出格式；不做性能优化或重跑收敛循环（性能特征由代码等价性自然保持，不做基准复测，除非 review 发现必要）。
- 不动 benchmark 基准语义（`CorpusUtil` 仅内部去重）。
- 不新增功能、不新增测试覆盖面（新增测试仅限被移动/重构代码的既有语义保持所需，依 guide Rule 25 裁定）。
- 不回写历史计划（roadmap SRCH-03 等历史记录不动，guide Rule 20）。

## Scope

### In Scope

- `nop-rg-core`：search（删 3 类）、coordinator（分解 + 提取 + patternBytes 缓存 + 陈旧 javadoc 清理 + LineCursor 死分支）、walk（风格）、9 个包的 `package-info.java`。
- `nop-rg-cli`：`NopRgMain` 输出抽取、`JfrSwitchTest` 重复 import、内联全限定名清理。
- `nop-rg-benchmark`：`CorpusUtil` 去重、`ScalarSearchBenchmark`/`CoordinatorEndToEndBenchmark` import 清理与嵌套类型 import 更新。
- owner docs：`ai-dev/design/nop-rg/01-architecture-baseline.md`（条件式：实证有引用才更新）、`docs-for-ai/01-repo-map/module-groups.md`（条件式）、daily log。

### Out Of Scope

- `ai-dev/design/nop-rg/00-vision.md` 成功标准（无行为变化）。
- 其他模块（nop-core 等）。
- 历史计划文本（2262-2267 内的旧引用按 guide Rule 20 不回写）。

## Execution Plan

### Phase 1 - 死公共 API 清理与 LineCursor 死分支修复

Status: planned
Targets: `nop-rg-core/src/main/java/io/nop/rg/core/search/{SearchRequest,SearchResult,MatchResult}.java`（删除）、`coordinator/LineCursor.java`、`CoreSearchIntegrationTest`、`ScalarByteSearcherTest`

- Item Types: `Fix`（死代码删除 + 死分支修复）

- [ ] 删除 `SearchRequest`/`SearchResult`/`MatchResult` 三类；`CoreSearchIntegrationTest` 与 `ScalarByteSearcherTest` 重构为自持 helper（测试内私有 record/方法承载原语义）；**裁定：针对被删类型自身行为的断言（如 SearchRequest.of 空模式校验、SearchResult 累积语义）允许删除或适配为对私有 helper 的断言，不算覆盖削弱**——被删类型无生产语义可守护
- [ ] 测试内 javadoc/注释同步清理（`CoreSearchIntegrationTest` L27/L49 注释含被删类型字样）
- [ ] `LineCursor.indexOf` 合并 `target != LF` 死分支为单一逐字节循环（语义不变：从 from 到 to 找首个 target 字节，未找到 -1）
- [ ] `grep -rn "SearchRequest\|SearchResult\|MatchResult" nop-rg --include="*.java"` 复核零残留
- [ ] owner docs：design 决策 3/5 表经审查（draft review 实证）无被删类型引用 → 预期 No owner-doc update required；若执行中发现引用则同步更新

Exit Criteria:

- [ ] `./mvnw test -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli -am` 全绿（默认 core 计数不低于 54 run + 1 skip，cli 26）
- [ ] grep 复核零残留
- [ ] LineCursor 语义不变：既有行语义测试（SearchCoordinatorTest count 语义/fuzz）全过
- [ ] No new test required: 纯重构 + 死代码删除，既有测试语义不变守护行为（guide Rule 25）
- [ ] `ai-dev/logs/` 已更新

### Phase 2 - SearchCoordinator 单一职责分解

Status: planned
Targets: `nop-rg-core/coordinator/SearchCoordinator.java`（457 行 → 纯编排）、新 `coordinator/MatchAggregator.java`（命名执行时可定为聚合语义的等价名称）、新顶层 `coordinator/FileMatches.java`/`LineMatch.java`/`Submatch.java`、`coordinator/SearchCommand.java`

- Item Types: `Fix`（类分解，纯移动 + 可见性调整，无逻辑变更）

- [ ] 聚合职责提取：aggregate/buildLineMatches/newLineMatch/decode 移入独立聚合类（包私有或公共按消费者需要定）；coordinator 主类只保留编排（search/searchFile/searchFileChunked/isBinary/resolveVectorFinder + 策略选择）
- [ ] FileMatches/LineMatch/Submatch 提升为 coordinator 包顶层类（公共 API 形态变更：`SearchCoordinator.FileMatches` → `FileMatches`，源级不兼容——消费者全在仓内：cli `NopRgMain`/`JsonOutput`/`JfrSwitchTest`、benchmark `CoordinatorEndToEndBenchmark`、core 测试 `SearchCoordinatorTest`/`LargeFileSearchTest`，逐个更新 import）
- [ ] `SearchCommand.patternBytes()` 缓存编码结果（模式不可变，防御性语义不变——返回克隆或不可变视口，杜绝调用方改动缓存）
- [ ] 拆分后各类 javadoc 归位：聚合类记录 count/text 两口径与行语义契约（现散落于方法注释）；同步清理 F9 陈旧策略描述（"LITERAL/FOLDING"）
- [ ] 消费者更新覆盖 benchmark 模块（`-pl` 验证命令必须含 benchmark 或独立编译检查——见 Exit Criteria）

Exit Criteria:

- [ ] `./mvnw test -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli -am` 全绿；rg 对照 opt-in 55/55
- [ ] **benchmark 编译检查**：`./mvnw compile -pl nop-rg/nop-rg-benchmark -am` 通过（防 `-pl core,cli` 断层漏改 benchmark 消费者）
- [ ] **行为零变化证明**：RgComparisonTest 9 场景 + NopRgMainTest 9 场景 + VectorModeTest 5 场景（CLI 端到端 23 场景）全部不变通过——管线从入口到输出逐字节等价
- [ ] **接线验证**：聚合类在运行时被 coordinator 两条路径真实消费（既有测试即为证明——LargeFileSearchTest 走分块路径聚合、fuzz 走整文件路径）
- [ ] `SearchCoordinator.java` 行数 ≤ 300（聚合簇 + 三嵌套类型 + 陈旧 javadoc 移出后的纯编排主类；repo-observable 度量）
- [ ] 全仓 `grep -rn "SearchCoordinator.FileMatches\|SearchCoordinator.LineMatch\|SearchCoordinator.Submatch"` 零残留
- [ ] owner docs：决策表/repo-map 无嵌套类型引用（draft review 实证）→ No owner-doc update required；daily log 记录性能中性论证（纯移动/缓存，热循环不动）
- [ ] No new test required: 纯重构，既有 23 个 CLI e2e 场景 + rg 对照 + fuzz 守护行为（guide Rule 25）
- [ ] `ai-dev/logs/` 已更新
- [ ] Phase 完成即 commit

### Phase 3 - CLI 输出抽取与风格一致性

Status: planned
Targets: `nop-rg-cli/NopRgMain.java`、`JsonOutput.java`（扩展为全模式输出或新增输出类）、`nop-rg-core/ParallelFileWalker.java`、`SearchCoordinator.java`、benchmark `ScalarSearchBenchmark.java`/`CoordinatorEndToEndBenchmark.java`、cli `JfrSwitchTest.java`

- Item Types: `Fix`（方法提取 + 风格统一）

- [ ] 输出职责归一：text/count/files-with-matches/json 四种输出模式统一收口到输出类（json 已有 JsonOutput——扩为 ResultPrinter 或等价命名，内部复用 JsonOutput 逻辑）；`NopRgMain.call()` 只保留 setup/搜索/委派/cleanup 编排
- [ ] 内联全限定名改 import：`SearchCoordinator`（ForkJoinPool/ServiceLoader/Iterator/ServiceConfigurationError）、`ParallelFileWalker`（RecursiveAction/AccessControlException）、`ScalarSearchBenchmark`（StandardCharsets ×2）、`CoordinatorEndToEndBenchmark`（List.of）
- [ ] `ParallelFileWalker` 的 `var stream` 改显式类型；`JfrSwitchTest` 重复 import ×3 合并
- [ ] CLI 输出逐字节不变（四种模式格式串保持字符级相同——由既有 CLI e2e 测试守护）

Exit Criteria:

- [ ] `./mvnw test -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli -am` 全绿；CLI e2e（23 场景）不变通过；benchmark 模块编译通过（`./mvnw compile -pl nop-rg/nop-rg-benchmark -am`——Phase 3 触及 benchmark 源文件）
- [ ] `NopRgMain.call()` 行数 ≤ 46（输出块抽走后剩余 JFR/初始化 try-finally 编排；repo-observable）
- [ ] 内联全限定名零残留 grep：`grep -rn "java.util.concurrent.ForkJoinPool\|java.util.ServiceLoader\|java.util.ServiceConfigurationError\|java.util.Iterator\|java.util.List\|java.security.AccessControlException\|java.nio.charset.StandardCharsets" nop-rg --include="*.java"`（main 目录）仅 import 行与 javadoc `{@link java.util.*}` 行命中（后者为惯用文档链接，豁免）
- [ ] No new test required: 纯重构，既有 CLI e2e 守护输出格式（guide Rule 25）
- [ ] `ai-dev/logs/` 已更新
- [ ] Phase 完成即 commit

### Phase 4 - 基准工具去重与包文档

Status: planned
Targets: `nop-rg-benchmark/CorpusUtil.java`、9 个包的 `package-info.java`

- Item Types: `Fix`（去重）、`Follow-up`（包文档）

- [ ] **重构前留痕**：记录 `textBytes(1<<20, 42L)` 与一个小尺寸 `ensureFile` 产物的 SHA-256（落 `_tmp/nop-rg-bench/corpus-hash-before.txt`）
- [ ] `CorpusUtil` 行构造逻辑抽取为单一共享实现（词表 + 逐行构建），**参数化 Random 实例/种子派生**——`textBytes`（`new Random(seed)`）与 `ensureFile`（`new Random(seed ^ 0x5eed)`）输出各自逐字节不变；重构后重建样本并比对 SHA-256
- [ ] 删除 `$TMPDIR/nop-rg-bench-corpus/` 后由基准 Setup 重建（size 复用机制会掩盖字节变化，删除重建是必须验证步骤）
- [ ] 新增 `package-info.java` ×9：core、core.search、core.io、core.coordinator、core.glob、core.walk、cli、vector、benchmark（一句话职责 + 关键契约指针，不写实现细节）
- [ ] 测试裁定：基准模块变更 No new test required（基准自身即度量，2265 先例）；package-info 纯文档 No new test required

Exit Criteria:

- [ ] `./mvnw test -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli -am` 全绿；benchmark 模块编译通过
- [ ] corpus 逐字节不变验证记录（SHA-256 前后比对一致 + 删除重建后基准可跑）
- [ ] 9 个 package-info 就位（find 可证）
- [ ] `ai-dev/logs/` 已更新
- [ ] Phase 完成即 commit

## Closure Gates

- [ ] 行为零变化：core 55 + cli 26 + vector 10 全绿且测试语义未削弱（既有断言无删改——Phase 1 裁定的被删类型自身断言除外，仅 import/helper 适配）；rg 对照 opt-in 通过
- [ ] large-file 显式组 `-DexcludedGroups= -Dgroups=large-file -DargLine=-Xmx256m` 3/3 通过
- [ ] 所有 in-scope confirmed live defects 已修复（F1-F9 逐项对照：死 API 删除/死分支/结构性重复/风格/长方法/重复分配/包文档/陈旧 javadoc）
- [ ] 不存在被静默降级的 in-scope 项（每个 F 编号在记录中对应 landed 或显式移出）
- [ ] owner docs：design 决策 3/5 与 repo-map 经实证无被删/移动类型引用 → No owner-doc update required（若执行中发现引用则同步更新）
- [ ] 独立子 agent closure-audit 已完成并记录证据（fresh session，不复用实现会话）
- [ ] **Anti-Hollow Check**：分解非空壳——聚合类/输出类被运行时真实消费（CLI e2e 即证明）；无空方法体/静默跳过
- [ ] 代码规范检查：imports 分组（io.nop.* → 第三方 → java.*）、无裸 RuntimeException、错误消息英文
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-rg --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（本计划触达文件 0 断链）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（待执行中产出后填写；confirmed live defect 不得记录于此）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent，fresh session）
- Evidence: （待 audit 后填写）

Follow-up:

- （待填写，或明确写 no remaining plan-owned work）
