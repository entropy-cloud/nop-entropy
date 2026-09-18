# 2263 nop-rg Wave 1 — Foundation（骨架 + 核心搜索）

> Plan Status: draft
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
- JMH 依赖在根 POM dependencyManagement 中有无托管未核实——若无需自行在 nop-rg 父 POM 管理（Wave 3 才需要）。

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
- 对 `docs-for-ai/` 平台使用文档的更新（nop-rg 尚非平台用户可用功能；模块级设计已由 `ai-dev/design/nop-rg/` 承载，若实现中偏离 design 决策需回写 design doc）。

## Execution Plan

### Phase 1 - 模块组骨架（SKEL-01..03）

Status: planned
Targets: `pom.xml`（根）、`nop-rg/pom.xml`、`nop-rg/nop-rg-bom/pom.xml`、`nop-rg/nop-rg-core/pom.xml`

- Item Types: `Decision`（JDK 22 门控落位，与 design 决策 1 一致）、`Fix`（根 reactor 接线）

- [ ] 根 POM `<modules>` 增加 `nop-rg`
- [ ] `nop-rg/pom.xml`：packaging=pom；子模块经 JDK ≥ 22 激活的 profile 引入（复用 nop-utils `java21-modules` 模式）
- [ ] `nop-rg-bom`：管理 nop-rg-core 及后续模块版本
- [ ] `nop-rg-core`：`maven.compiler.release=22`，依赖 `nop-core`（GitIgnoreFile 复用预留）；定义 `io.nop.rg.core` 包结构（search/glob/io 空包可由后续 Phase 的类自然创建，不留空壳类）

Exit Criteria:

- [ ] `./mvnw compile -pl nop-rg/nop-rg-core -am` 在 JDK 26 下通过
- [ ] `./mvnw validate`（根）输出包含 `nop-rg`、`nop-rg-bom`、`nop-rg-core` 三个 reactor 条目
- [ ] JDK 门控语法经审查确认：profile activation 为 `<jdk>[22,)`，与 nop-utils 先例同构（JDK < 22 跳过行为由 activation 区间语义保证，本机无 JDK<22 环境实测，作为 documented evidence 记录）
- [ ] **接线验证**：根 reactor 确实包含 nop-rg（validate 输出为证），而非孤立模块
- [ ] No new test required: 纯 POM 骨架，无行为逻辑
- [ ] `ai-dev/logs/` 已更新

### Phase 2 - ByteSearchStrategy + ScalarByteSearcher（SRCH-01..04）

Status: planned
Targets: `nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/search/`

- Item Types: `Fix`（新功能实现）

- [ ] `ByteSearchStrategy` 接口：`findPattern(MemorySegment, long offset, long limit, byte[] pattern)`、`findFirstByte(MemorySegment, long offset, long limit, byte target)`，返回匹配偏移或负值（未找到）
- [ ] `ScalarByteSearcher`：BMH，锚点字节取 pattern 中最罕见字节（按启发式频率表），坏字符跳跃表
- [ ] `SearchRequest`/`SearchResult`/`MatchResult` 数据类（承载模式、搜索范围、匹配位置等）
- [ ] 单元测试：空模式/单字节/多字节模式、模式长于数据、未命中、重叠匹配、跨 offset/limit 子区间搜索、`MemorySegment` 边界（offset/limit 裁剪）

Exit Criteria:

- [ ] `ScalarByteSearcher` 对已知用例返回正确匹配偏移（断言具体偏移值）
- [ ] `./mvnw test -pl nop-rg/nop-rg-core -am -Dtest=ScalarByteSearcherTest -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [ ] **无静默跳过**：接口方法在实现中无空方法体/no-op
- [ ] 新增功能测试覆盖显式列出（上条测试清单即覆盖要求）
- [ ] `ai-dev/logs/` 已更新

### Phase 3 - GlobMatcher（GLOB-01..03）

Status: planned
Targets: `nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/glob/`

- Item Types: `Fix`（新功能实现）

- [ ] `GlobMatcher`：两指针贪心算法；支持 segment-level（单段内 `*` 不跨 `/`）与 path-level（`**` 跨段）匹配；支持 `?`、`[...]`（含 `!`/`^` 反转与范围）、转义
- [ ] `CompiledGlob` + 编译缓存（ConcurrentHashMap，相同 pattern 复用编译产物）
- [ ] 单元测试：`**`（含 `a/**/b`、`**/b`、`a/**`）、`?`、`[]`、`[!...]` 反转、空模式、无通配符字面量、长路径、深嵌套目录、`/` 边界（`a*b` 不得匹配 `a/b`）

Exit Criteria:

- [ ] `GlobMatcher` 对边界用例断言具体匹配/不匹配结果
- [ ] 缓存生效可观察：相同 pattern 多次获取返回同一编译实例（断言同一性）
- [ ] `./mvnw test -pl nop-rg/nop-rg-core -am -Dtest=GlobMatcherTest -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [ ] **无静默跳过**：无空方法体/no-op
- [ ] `ai-dev/logs/` 已更新

### Phase 4 - MemorySegment + Arena I/O（IO-01..03）

Status: planned
Targets: `nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/io/`

- Item Types: `Fix`（新功能实现）

- [ ] `MappedFileReader`：FileChannel + Arena 内存映射，实现 AutoCloseable，close 时确定性释放 Arena
- [ ] `ChunkedFileReader`：>256MB 文件分块映射（256MB chunk），提供带 overlap 的分块顺序访问接口（overlap ≥ 最大模式长度，保证跨块边界模式可在某一块内完整命中）
- [ ] 单元测试：小文件（<1 chunk）、跨块边界匹配（构造 >256MB 文件或以可注入的更小 chunk size 测试同一逻辑——允许以包内可见参数缩小 chunk 便于测试，生产默认 256MB）、close 后 Arena 释放（重复 close 不抛、访问已关闭资源抛 IllegalStateException/Arena 已关闭异常）

Exit Criteria:

- [ ] 跨块边界匹配测试通过（模式恰好横跨 chunk 边界仍被找到）
- [ ] 资源安全测试通过：try-with-resources 关闭后无泄漏句柄（Arena closed 语义验证）
- [ ] `./mvnw test -pl nop-rg/nop-rg-core -am` 通过
- [ ] **无静默跳过**：无空方法体/no-op；不支持的操作显式抛异常
- [ ] `ai-dev/logs/` 已更新

### Phase 5 - 核心层端到端集成测试（里程碑 M1 收口）

Status: planned
Targets: `nop-rg/nop-rg-core/src/test/java/io/nop/rg/core/`

- Item Types: `Proof`（端到端验证）

- [ ] 端到端测试：`@TempDir` 生成多文件目录树 → `ChunkedFileReader`/`MappedFileReader` 逐文件读取 → `ScalarByteSearcher` 内容搜索（多行匹配、跨块匹配场景）→ `GlobMatcher` 文件名过滤（含排除 glob）→ 断言聚合的匹配文件/行/偏移结果与预期一致
- [ ] 测试证明三个组件（search/glob/io）在运行时互相协作（wiring），而非各自孤立通过单测

Exit Criteria:

- [ ] 端到端集成测试存在且通过（`CoreSearchIntegrationTest` 或同名）
- [ ] **端到端验证**：从磁盘文件入口到匹配结果输出的完整路径已验证
- [ ] **接线验证**：ScalarByteSearcher 消费 ChunkedFileReader/MappedFileReader 提供的 MemorySegment，GlobMatcher 过滤参与聚合结果（测试断言可证）
- [ ] `./mvnw test -pl nop-rg/nop-rg-core -am` 全量通过
- [ ] `ai-dev/logs/` 已更新

## Closure Gates

- [ ] roadmap Work Item 2、3、4、5 改为 `done`；里程碑 M1 依赖项全 done 后标 `done`
- [ ] 所有 in-scope confirmed live defects 已修复
- [ ] 行为/契约结果已达成：nop-rg-core 三包能力可用且被端到端测试覆盖
- [ ] 必要 focused verification 已完成（Phase 1-5 Exit Criteria 全勾）
- [ ] 不存在被静默降级的 in-scope live defect 或 contract drift
- [ ] owner docs：若实现偏离 `ai-dev/design/nop-rg/01-architecture-baseline.md` 的既有决策，已回写 design doc；否则明确 No owner-doc update required
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：端到端调用链（文件→chunk→search→glob→结果）运行时连通，无空方法体/静默跳过
- [ ] `./mvnw test -pl nop-rg/nop-rg-core -am` 通过
- [ ] 代码规范检查：新增代码 imports 分组、无裸 RuntimeException、错误消息英文
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-rg/nop-rg-core --severity high` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- Wave 2+（walker/coordinator/CLI）另行拟制。
- 性能调优（BMH 跳表优化、向量化）归 Wave 3，本计划以正确性优先。

## Closure

Status Note: （closure 时填写）
Completed: （closure 时填写）

Closure Audit Evidence:

- Reviewer / Agent: （closure 时填写）
- Evidence: （closure 时填写）

Follow-up:

- （closure 时填写）
