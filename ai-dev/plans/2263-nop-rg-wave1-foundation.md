# 2263 nop-rg Wave 1 — Foundation（骨架 + 核心搜索）

> Plan Status: completed
> Last Reviewed: 2026-09-19
> Source: `ai-dev/backlog/nop-rg-roadmap.md`（Stage 2-5 + 里程碑 M1「Core search可用」）、`ai-dev/design/nop-rg/01-architecture-baseline.md`（决策 1/3/5）、`ai-dev/design/nop-rg/00-vision.md`
> Related: Plan 2262（Stage 1 GitIgnoreFile 抽取，先行完成）

## Purpose

建立 nop-rg 模块组骨架（父 POM + BOM + core 模块，JDK 22+ 门控），落地三大核心能力：BMH 标量字节搜索、Glob 匹配器、MemorySegment+Arena 分块 I/O，并以核心层端到端集成测试收口里程碑 M1「Core search可用」。

## Current Baseline

- 仓库默认 JDK 为 26.0.1（另有 25.0.1），满足 nop-rg 的 JDK 22+ 要求；系统 rg 15.1.0 可用（Wave 2/3 对比测试用）。
- `GitIgnoreFile` 已落在 `nop-kernel/nop-core` 的 `io.nop.core.git`（Plan 2262），nop-rg-core 依赖 nop-core 即可复用；本计划不使用它（walker 在 Wave 2 才消费）。
- 根 POM `<modules>` 无 `nop-rg` 条目；仓库无 `nop-rg/` 目录。
- JDK 门控先例：`nop-utils/pom.xml` 的 `java21-modules` profile（`<jdk>[21,)` 激活）挂 `nop-commons-java21`，`nop-utils` 本身无条件在根 modules 中。nop-rg 复用同一模式（门控阈值 22）。
- `nop-utils/nop-commons-java21/pom.xml` 以 properties 直接指定 `maven.compiler.release=21` 的先例存在；nop-rg-core 同法指定 22。
- 仓库无任何文件搜索能力；`ai-dev/design/nop-rg/` 已有 vision + architecture baseline（草案），其中 ByteSearchStrategy 契约（`findPattern`/`findFirstByte`，操作 `MemorySegment`）已定义。
- JMH 依赖在根 POM/nop-bom 中均无托管（审查已核实）——Wave 3 需在 nop-rg 父 POM 自行管理版本；本计划（Wave 1）不涉及。

## Goals

- `nop-rg/` 模块组进入根 reactor：`nop-rg` 父 POM（packaging=pom）+ `nop-rg-bom` + `nop-rg-core`；JDK ≥ 22 时参与构建，JDK < 22 自动跳过且不阻断根构建。
- `nop-rg-core` 提供：
  - `ByteSearchStrategy` 接口（`findPattern`/`findFirstByte`，操作 `MemorySegment`）与 `ScalarByteSearcher`（BMH + 最罕见字节锚点）；
  - `GlobMatcher`/`CompiledGlob`（两指针贪心，支持 `**`、`?`、`[]`、`!` 反转，编译缓存）；
  - `MappedFileReader`/`ChunkedFileReader`（FFM 内存映射，256MB 分块，跨块 overlap 扫描，try-with-resources 确定性释放）；
  - 搜索请求数据类（`SearchRequest`/`SearchResult`/`MatchResult`）。
- 核心层端到端测试：磁盘文件 → 分块读取 → BMH 搜索 → glob 过滤 的完整路径有集成测试覆盖。
- roadmap Work Items 中 Work Item 2、3、4、5 及里程碑 M1 状态收敛为 `done`。

## Non-Goals

- 文件遍历/gitignore 过滤/并行（Wave 2，stage 6-7）。
- CLI、JSON 输出、rg 对比（Wave 2，stage 8-9）。
- JMH 基准、JFR、大文件 >1GB 测试、并行优化（Wave 3）。
- Vector API（Wave 4）。`ByteSearchStrategy` 接口预留多实现，但本计划只交付 Scalar 实现。
- 正则搜索器（RegexSearcher）——vision 中为回退路径，roadmap 未排期，不在本计划。

## Scope

### In Scope

- 根 POM 增 `nop-rg` module；新建 `nop-rg/pom.xml`（含 JDK≥22 激活 profile）、`nop-rg/nop-rg-bom/`、`nop-rg/nop-rg-core/`（`maven.compiler.release=22`）。
- `io.nop.rg.core.search`、`io.nop.rg.core.glob`、`io.nop.rg.core.io` 三个包及单元测试。
- 核心层端到端集成测试（含跨块边界匹配验证）。
- 错误处理遵循仓库两层策略（模块内异常类 + 英文消息，无裸 RuntimeException，见 `docs-for-ai/02-core-guides/error-handling.md`）。
- roadmap Work Items 状态更新 + daily log。

### Out Of Scope

- `io.nop.rg.core.walk`、`io.nop.rg.core.coordinator`（Wave 2）。
- nop-rg-cli、nop-rg-benchmark、nop-rg-vector 模块。
- 导航类文档（INDEX.md/source-anchors.md）超出 module-groups.md 根分组表所需的深度同步不属本计划；`docs-for-ai/` 平台使用文档（usage 类）不更新：nop-rg 尚非平台用户可用功能；模块级设计由 `ai-dev/design/nop-rg/` 承载，若实现中偏离 design 决策需回写 design doc。

## Execution Plan

### Phase 1 - 模块组骨架（SKEL-01..03）

Status: completed
Targets: `pom.xml`（根）、`nop-rg/pom.xml`、`nop-rg/nop-rg-bom/pom.xml`、`nop-rg/nop-rg-core/pom.xml`

- Item Types: `Decision`（JDK 22 门控落位，与 design 决策 1 一致）、`Fix`（根 reactor 接线）

- [x] 根 POM `<modules>` 增加 `nop-rg`
- [x] `nop-rg/pom.xml`：packaging=pom；子模块经 JDK ≥ 22 激活的 profile 引入（复用 nop-utils `java21-modules` 模式）
- [x] `nop-rg-bom`：管理 nop-rg-core 及后续模块版本（其消费者是 Wave 2+ 的 nop-rg-cli/nop-rg-vector，经 import 或子模块 dependencyManagement 消费；本 Phase 交付即可，不算空壳）
- [x] `nop-rg-core`：`maven.compiler.release=22`（properties 同步设 source/target/release/java.version 四项，与 nop-commons-java21 先例同构），依赖 `nop-core`（Wave 2 walker 复用 GitIgnoreFile，架构基线依赖图即 nop-rg-core → nop-core；Wave 1 代码暂不消费属预期）

Exit Criteria:

- [x] `./mvnw compile -pl nop-rg/nop-rg-core -am` 在 JDK 26 下通过
- [x] `./mvnw validate`（根）输出包含 `nop-rg`、`nop-rg-bom`、`nop-rg-core` 三个 reactor 条目
- [x] JDK 门控语法经审查确认：profile activation 为 `<jdk>[22,)`，与 nop-utils 先例同构（JDK < 22 跳过行为由 activation 区间语义保证，本机无 JDK<22 环境实测，作为 documented evidence 记录）
- [x] **接线验证**：根 reactor 确实包含 nop-rg（validate 输出为证，reactor 432 模块含 `34-nop-rg`/`nop-rg-bom`/`nop-rg-core`），而非孤立模块
- [x] owner docs：`docs-for-ai/01-repo-map/module-groups.md` 根模块分组表新增 nop-rg 条目（注明 JDK 22+ 门控、非默认构建可达）；doc-link checker 0 errors（INDEX/source-anchors 无需更新，导航不受影响）
- [x] No new test required: 纯 POM 骨架，无行为逻辑
- [x] `ai-dev/logs/` 已更新

### Phase 2 - ByteSearchStrategy + ScalarByteSearcher（SRCH-01..04）

Status: completed
Targets: `nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/search/`

- Item Types: `Fix`（新功能实现）

- [x] `ByteSearchStrategy` 接口：`findPattern(MemorySegment, long offset, long limit, byte[] pattern)`、`findFirstByte(MemorySegment, long offset, long limit, byte target)`；语义钉死：offset 为相对 segment 起点的绝对偏移，limit 为 exclusive 上界，未找到返回 -1，空 pattern 抛 IllegalArgumentException（fail-fast，不静默）
- [x] `ScalarByteSearcher`：BMH，锚点字节取 pattern 中最罕见字节（按启发式频率表），坏字符跳跃表
- [x] `SearchRequest`/`SearchResult`/`MatchResult` 数据类（承载模式、搜索范围、匹配位置等；Phase 5 端到端聚合以 `SearchResult`/`MatchResult` 为载体，避免落地即 dead code）
- [x] 单元测试：空模式（断言抛异常）/单字节/多字节模式、模式长于数据、未命中、重叠匹配、跨 offset/limit 子区间搜索、`MemorySegment` 边界（offset/limit 裁剪）

Exit Criteria:

- [x] `ScalarByteSearcher` 对已知用例返回正确匹配偏移（断言具体偏移值）
- [x] `./mvnw test -pl nop-rg/nop-rg-core -am -Dtest=ScalarByteSearcherTest -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [x] **无静默跳过**：接口方法在实现中无空方法体/no-op；空模式等非法入参显式抛异常
- [x] 新增功能测试覆盖显式列出（上条测试清单即覆盖要求；另含 500 次确定性 fuzz 与 String.indexOf 交叉验证）
- [x] No owner-doc update required（本 Phase 未改变既有契约；ByteSearchStrategy 契约与 `ai-dev/design/nop-rg/01-architecture-baseline.md` 决策 5 一致，语义细节以本 plan 钉死为准）
- [x] `ai-dev/logs/` 已更新

### Phase 3 - GlobMatcher（GLOB-01..03）

Status: completed
Targets: `nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/glob/`

- Item Types: `Fix`（新功能实现）

- [x] `GlobMatcher`：两指针贪心算法；支持 segment-level（单段内 `*` 不跨 `/`）与 path-level（`**` 跨段）匹配；支持 `?`、`[...]`（含 `!`/`^` 反转，二者等价，支持范围）、`\` 转义
- [x] **语义基准钉死（rg/globset 兼容）**：不含 `/` 的 glob 隐式匹配 basename（`*.java` 命中任意深度路径）；`a/**/b` 匹配 `a/b`（零目录情形）；`**` 语义对齐 gitignore/globset。实现后用本机 rg 15.1.0 抽样对照验收（`rg --files -g <pattern>` 的文件列表与 GlobMatcher 结果对照，抽样记录进测试或日志）
- [x] **glob 集合级正/负规则匹配 API**：支持 `!` 前缀排除（rg `-g` 语义：无负则命中且（有正则命中或集合仅含负则）→ 保留；即有正则时任一正则命中且无负则命中才保留，仅负则时等价于全集减去负则命中——对齐 rg 单独使用排除 glob 的行为），供 Phase 5 过滤与 Wave 2 CLI `-g` 消费
- [x] `CompiledGlob` + 编译缓存（ConcurrentHashMap，相同 pattern 复用编译产物）
- [x] 单元测试：`**`（含 `a/**/b` 匹配 `a/b` 与 `a/x/b`、`**/b`、`a/**`）、`?`、`[]`、`[!...]`/`[^...]` 反转、空模式、无通配符字面量、长路径、深嵌套目录、`/` 边界（`a*b` 不得匹配 `a/b`）、`!` 排除规则集合（正+负组合）、无 `/` glob 的 basename 匹配

Exit Criteria:

- [x] `GlobMatcher` 对边界用例断言具体匹配/不匹配结果
- [x] 缓存生效可观察：相同 pattern 多次获取返回同一编译实例（断言同一性）
- [x] `./mvnw test -pl nop-rg/nop-rg-core -am -Dtest=GlobMatcherTest -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [x] **无静默跳过**：无空方法体/no-op
- [x] No owner-doc update required（新能力，无既有契约变更；语义基准已在本 plan 钉死并对照 rg 15.1.0 验收：5 组模式含正/负规则全部一致）
- [x] `ai-dev/logs/` 已更新

### Phase 4 - MemorySegment + Arena I/O（IO-01..03）

Status: completed
Targets: `nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/io/`

- Item Types: `Fix`（新功能实现）

- [x] `MappedFileReader`：FileChannel + Arena 内存映射，实现 AutoCloseable，close 时确定性释放 Arena
- [x] `ChunkedFileReader`：>256MB 文件分块映射（256MB chunk），提供带 overlap 的分块顺序访问接口（overlap ≥ 最大模式长度）。**去重语义钉死（归消费者）**：分块步长 = primaryLen（主区间无缝平铺，`chunkStart[i+1] = chunkStart[i] + primaryLen`，末块主区间延伸到文件尾 `min(chunkStart+primaryLen, fileEnd)`，overlap ≤ primaryLen）；chunk 暴露主区间 [chunkStart, chunkStart+primaryLen) 与 overlap 扩展区；消费者只报告匹配起点落在主区间内的命中（`chunkStart <= matchStart < chunkStart+primaryLen`），overlap 区仅用于保证跨界模式完整可见——同一匹配恰好落在 overlap 区内时由下一 chunk 的主区间覆盖报告，全局恰好一次。测试须含"模式完全落在 overlap 区内"用例验证不重不漏
- [x] 单元测试：小文件（<1 chunk）、跨块边界匹配（用可注入的更小 chunk size 测试同一逻辑，生产默认 256MB；如需实体大文件用稀疏文件 `RandomAccessFile.setLength` 避免 256MB 实际写盘）、close 后 Arena 释放（重复 close 不抛、访问已关闭资源抛 IllegalStateException/Arena 已关闭异常）

Exit Criteria:

- [x] 跨块边界匹配测试通过（模式恰好横跨 chunk 边界仍被找到，且恰好报告一次）
- [x] 资源安全测试通过：try-with-resources 关闭后访问抛 IllegalStateException、重复 close 幂等（Arena closed 语义验证）
- [x] `./mvnw test -pl nop-rg/nop-rg-core` 通过（MappedFileReaderTest 3 + ChunkedFileReaderTest 7 全过）
- [x] **无静默跳过**：无空方法体/no-op；不支持的操作显式抛异常
- [x] No owner-doc update required（新模块内部能力，无既有 owner doc 记载该层契约）
- [x] `ai-dev/logs/` 已更新

### Phase 5 - 核心层端到端集成测试（里程碑 M1 收口）

Status: completed
Targets: `nop-rg/nop-rg-core/src/test/java/io/nop/rg/core/`

- Item Types: `Proof`（端到端验证）

- [x] 端到端测试：`@TempDir` 生成多文件目录树 → `ChunkedFileReader`/`MappedFileReader` 逐文件读取 → `ScalarByteSearcher` 内容搜索（多匹配、跨块匹配场景，聚合载体用 `SearchResult`/`MatchResult`）→ `GlobMatcher`（含 `!` 排除规则集合）文件名过滤 → 断言聚合的匹配文件/字节偏移结果与预期一致（M1 断言到 file/offset 粒度即可，行号语义不在 Wave 1 范围）
- [x] 测试证明三个组件（search/glob/io）在运行时互相协作（wiring），而非各自孤立通过单测

Exit Criteria:

- [x] 端到端集成测试存在且通过（`CoreSearchIntegrationTest`，4 测试全过）
- [x] **端到端验证**：从磁盘文件入口到匹配结果输出的完整路径已验证
- [x] **接线验证**：ScalarByteSearcher 消费 ChunkedFileReader/MappedFileReader 提供的 MemorySegment，GlobMatcher 过滤参与聚合结果，SearchResult/MatchResult 作为聚合载体（测试断言可证）
- [x] **无静默跳过**（如适用）：聚合结果重复匹配（overlap 区）不重不漏（去重语义见 Phase 4）
- [x] `./mvnw test -pl nop-rg/nop-rg-core -am` 全量通过（37 tests，0 failures）
- [x] No owner-doc update required（模块尚未对平台用户可见；repo-map 已在 Phase 1 同步）
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [x] roadmap Work Item 2、3、4、5 改为 `done`；里程碑 M1 依赖项全 done 后标 `done`
- [x] 所有 in-scope confirmed live defects 已修复（实现期发现的 overlap 单向缺陷已改双向并由端到端测试钉住）
- [x] 行为/契约结果已达成：nop-rg-core 三包能力可用且被端到端测试覆盖
- [x] 必要 focused verification 已完成（Phase 1-5 Exit Criteria 全勾）
- [x] 不存在被静默降级的 in-scope live defect 或 contract drift
- [x] owner docs：实现与 `ai-dev/design/nop-rg/01-architecture-baseline.md` 决策无偏离（ByteSearchStrategy 契约与决策 5 一致）；No owner-doc update required；repo-map 已在 Phase 1 同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：端到端调用链（文件→chunk→search→glob→结果）运行时连通（CoreSearchIntegrationTest 断言到文件+绝对偏移粒度），无空方法体/静默跳过
- [x] `./mvnw test -pl nop-rg/nop-rg-core -am` 通过（37 tests，0 failures；审计者独立复跑）
- [x] 代码规范检查：新增代码 imports 分组、无裸 RuntimeException、错误消息英文（ScalarByteSearcherTest import 顺序已按审计意见修正）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-rg/nop-rg-core --severity high` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- Wave 2 walker/coordinator/CLI：SearchCoordinator 须按最大模式长度设置 ChunkedFileReader overlap（或加运行时守卫）——当前 DEFAULT_OVERLAP=1023 只保证 ≤1024 字节模式的跨界可见性，更长模式起于主区间末端时会漏配（契约已在 Javadoc/plan 钉死为消费者职责，closure audit Observation）。
- Wave 2 CLI：SearchResult.truncated 标志当前仅测试置位，聚合循环触限时应由 Wave 2 消费者置位。
- Wave 3 性能调优：BMH 跳表优化、向量化（本计划正确性优先）。

## Closure

Status Note: Wave 1 全部交付物落地：nop-rg 模块组以 JDK≥22 profile 进入根 reactor（432 模块 reactor 验证）；BMH 标量搜索、Glob 匹配（语义对齐 rg/globset 并以 rg 15.1.0 抽样对照）、MemorySegment+Arena 分块 I/O（双向 overlap + 主区间去重）、端到端集成测试全部通过。实现期发现并修复 1 处 overlap 单向缺陷（plan 已同步修正）。roadmap Work Item 2/3/4/5 与里程碑 M1 → done。
Completed: 2026-09-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure auditor（fresh session，agent_f7b73a4a-1e42-45c2-8439-a4b6ef466b67，未参与实现）
- Audit Session: agent_f7b73a4a-1e42-45c2-8439-a4b6ef466b67
- Evidence:
  - Phase 1：PASS——根 POM nop-rg 条目、java22-modules profile（`<jdk>[22,)` 与 nop-utils 先例逐行同构）、BOM、release=22 四属性；validate 复跑 reactor 三条目 [430-432/432]；module-groups.md 条目事实准确；doc-link 0 errors
  - Phase 2：PASS——审计者独立复跑 ScalarByteSearcherTest 13/13；BMH 锚点/坏字符表/Horspool 不漏配论证人工推演通过；数据类被端到端真实消费
  - Phase 3：PASS——10/10（rg 对照以 -Dtest.rg.compare=true 独立复跑 10 tests 0 skipped）；`**`/字符类/转义/正负规则集合逐分支核对
  - Phase 4：PASS——10/10；平铺/双向 overlap/inPrimary 去重/关闭语义核对通过；overlap 区内命中恰好一次有专项断言
  - Phase 5：PASS——`./mvnw test -pl nop-rg/nop-rg-core -am` 复跑 37 tests 0 failures；端到端调用链追踪成立（文件落盘→walk+glob 过滤→chunk 视图→BMH→绝对偏移聚合断言）
  - Closure Gates：全部 PASS（roadmap 状态仅 Wave 1 五条变更；无 in-scope live defect；Anti-Hollow 人工+工具双确认）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-rg/nop-rg-core --severity high` 退出码 0（审计者复跑）
  - Deferred 项分类检查：Non-Blocking Follow-ups 均为 Wave 2/3 out-of-scope 项，无藏匿 in-scope live defect
  - 审计 Minor 处置：ScalarByteSearcherTest import 顺序已修正；Minor 2（truncated 标志）与 Observation（overlap 模式长度守卫）已记入 Non-Blocking Follow-ups 归属 Wave 2

Follow-up:

- Wave 2 SearchCoordinator 按模式长度设置 overlap 或加守卫（audit Observation）
- Wave 2 CLI 聚合循环置位 SearchResult.truncated（audit Minor 2）
- Wave 3 性能调优（BMH 跳表、向量化）
