# 2264 nop-rg Wave 2 — Integration（walker + coordinator + CLI + rg 对比）

> Plan Status: completed
> Last Reviewed: 2026-09-19
> Source: `ai-dev/backlog/nop-rg-roadmap.md`（Stage 6-9 + 里程碑 M2「CLI可用」）、`ai-dev/design/nop-rg/01-architecture-baseline.md`（决策 2/4/5、分层架构）、`00-vision.md` 成功标准 3/4
> Related: Plan 2262（GitIgnoreFile 落位 nop-core）、Plan 2263（Wave 1 核心，已完成）

## Purpose

在 Wave 1 核心能力之上交付并行文件遍历、搜索协调器（含策略选择与行提取）、CLI 入口（rg --json 兼容输出 + --delegate-rg），并以与系统 rg 的对比测试收口里程碑 M2「CLI可用」。

## Current Baseline

- Wave 1 已落地（plan 2263，37 tests green）：`nop-rg-core` 的 `search`/`glob`/`io` 三包。audit Observation：`ChunkedFileReader` DEFAULT_OVERLAP=1023 只保证 ≤1024 字节模式跨界可见；`SearchResult.truncated` 约定由消费者置位。
- `GitIgnoreFile` 在 `nop-core` `io.nop.core.git`：**private 构造器**，唯一入口 `create(IResource)`，规则加载走 `VirtualFileSystem.instance()`——**未初始化 VFS 时抛 `ERR_RESOURCE_VIRTUAL_FILE_SYSTEM_NOT_INITIALIZED`**；nop-rg-core 现有测试无任何 VFS 初始化设施。
- picocli 版本链已实证：nop-rg parent → root `nop-bom` → `nop-dependencies` → quarkus-bom 3.35.1 → 托管 picocli/picocli-codegen 4.7.7（nop-cli-core 同法无版本声明）。**nop-bom 未管理任何 nop-rg-\* 构件**，nop-rg-cli 依赖 nop-rg-core 须写 `${project.version}`。
- rg 15.1.0 实测语义（本 plan 的兼容基准）：
  - `--json` 消息结构：`begin`/`match`/`end` 三类（无命中的文件零消息），另有 `summary`（本 plan 不实现）；`data.path`/`data.lines`/`submatches[].match` 均为 `{"text":...}` 对象；`submatches[]` 元素为 `{"match":{"text":...},"start":<行内字节偏移>,"end":<行内字节偏移>}`（**无 byte_offset/byte_end**）；`data.absolute_offset` 是**行首**偏移；`end` 消息含 `binary_offset` 与 `stats`（不可复现字段）；`lines.text` 含行终止符（CRLF 原样保留）；多文件消息顺序非确定。
  - `--no-ignore` **只关闭 ignore 规则，隐藏文件依然跳过**（`--hidden` 才恢复）。
  - `-c` 计的是**匹配行数**（一行三命中输出 1）；`-c` 与 `--json` 组合时输出纯数字。
  - 退出码：命中 0 / 未命中 1 / 错误 ≥2。
  - glob 匹配基于相对 CWD 的路径；`!` 前缀排除规则原生支持。
- vision 成功标准 4 要求 `RegexSearcher` 回退可用；roadmap COORD-02 要求策略选择覆盖 Scalar/Vector/Regex。design 决策 5 的表把 RegexSearcher 列为 `ByteSearchStrategy` 实现层级——本 plan 将其改为独立接口，属契约修订，须回写 design doc（Phase 2 交付项）。

## Goals

- `io.nop.rg.core.walk.ParallelFileWalker`：专用 ExecutorService 并行遍历，输入为 `java.nio.file.Path`；gitignore 过滤（内部以 `FileResource` 包装后调 `GitIgnoreFile`）与隐藏文件跳过为**两个独立开关**（`respectGitignore` 默认 true、`includeHidden` 默认 false）；并行度参数。
- `io.nop.rg.core.coordinator`：`SearchCoordinator` 编排（glob 过滤 → walker → 搜索 → 聚合）；策略选择（Scalar/Regex，Vector 预留）；行提取（行号/行文本，**基于整文件 MappedFileReader 映射**，禁止基于 chunk 视图）；`FoldingByteSearcher`（ASCII 折叠字面量搜索，实现 `ByteSearchStrategy`，放在 coordinator 包，Wave 1 `search/` 仅放宽 `ScalarByteSearcher.selectAnchorIndex` 为 public（无行为变化，复用锚点选择））。
- `RegexSearcher`（独立接口 + 实现）：java.util.regex + UTF-8 **整文件解码**（coordinator 已整文件映射，无阈值/分块问题），字符偏移经 char→字节偏移桥接映射回文件字节域。
- `nop-rg-cli` 模块（release=22 四属性）：picocli 命令行，参数 `PATTERN`/`PATH`、`-g/--glob`（可重复）、`-i`、`-c/--count`（**匹配行数**语义）、`-l/--files-with-matches`、`--json`、`--no-ignore`（只关 gitignore，隐藏始终跳过）、`--threads N`（WALK-03 的开关在 CLI 落地）、`--delegate-rg[=path]`（可选值指定 rg 可执行路径，默认 `rg`；供测试注入不存在路径）。退出码对齐 rg（命中 0/未命中 1/错误 2）。
- 与系统 rg 的对比测试（显式归一化规则见 Phase 4）。
- roadmap Work Item 6/7/8/9 与里程碑 M2 → done。

## Non-Goals

- Vector 搜索策略实现（Wave 4 预留接口）。
- JMH/JFR/大文件 >1GB/并行优化（Wave 3）。**ChunkedFileReader 的生产接线归 Wave 3 大文件路径**（本 Wave coordinator 走整文件 MappedFileReader：搜索与行提取同源，无跨视图行提取问题；Wave 3 收口其 >1GB 生产消费）。
- 全量 rg 选项兼容；`--hidden`/`-A/-B/-C`/颜色等不做。
- Unicode case folding（`-i` 按 ASCII 折叠；非 ASCII 字面按精确匹配；与 rg 差异记录于 javadoc 与对比测试豁免说明）。
- 非 UTF-8 文件的正则语义：RegexSearcher 整文件 UTF-8 解码遇非法字节走 replacement char，与 rg 纯字节域行为发散（对比 corpus 纯 ASCII 规避；已知偏差声明）。
- rg `summary` 消息与 `end.stats` 的实现（归一化时剔除）。

## Scope

### In Scope

- `nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/walk/`、`io/nop/rg/core/coordinator/`（含 `FoldingByteSearcher`、`RegexSearcher`、行提取）及测试。
- `nop-rg/nop-rg-cli/` 模块及测试。
- `ai-dev/design/nop-rg/01-architecture-baseline.md` 决策 5 的 RegexSearcher 落位修订（owner doc 回写）。
- roadmap Work Items/M2 状态更新、daily log、`docs-for-ai/01-repo-map/module-groups.md` nop-rg 条目更新。

### Out Of Scope

- nop-rg-benchmark、nop-rg-vector 模块；平台 usage 文档。

## Execution Plan

### Phase 1 - ParallelFileWalker（WALK-01..03）

Status: completed
Targets: `nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/walk/`

- Item Types: `Fix`（新功能）、`Decision`（walker 输入类型 = `Path`；gitignore/隐藏为双开关）

- [x] `ParallelFileWalker`：输入 `Path`（目录根），专用 ExecutorService（非 commonPool），并行收集匹配文件（`--threads N` 对应并行度参数，默认 = CPU 核数）；结果收集允许先用 CopyOnWriteArrayList（正确性优先；OPT-01/Wave 3 再替换为 work-stealing 收集）
- [x] 过滤双开关：`respectGitignore`（默认 true；内部以 `FileResource` 包装根目录调 `GitIgnoreFile.isIgnored`）与 `includeHidden`（默认 false；`.` 前缀文件/目录跳过——`.gitignore` 本身即隐藏文件、默认不入结果；其规则加载由 GitIgnoreFile.create() 自身的 VFS 遍历完成，不受本开关影响，与 rg 行为一致）
- [x] **VFS 初始化约束**：`GitIgnoreFile` 依赖已初始化 VFS；walker 本体不初始化 VFS（保持库零副作用），由调用方（CLI 启动、测试 `@BeforeAll CoreInitialization.initialize()`/`destroy()`，同 nop-core `TestResourceHelper` 模式）负责；walker 在 respectGitignore=true 且 VFS 未初始化时让异常自然抛出（fail-fast，不吞）
- [x] 单元测试：`@BeforeAll` 初始化 VFS；生成含 `.gitignore`/隐藏目录/嵌套子目录的临时树，断言具体收集结果；threads=1 与 threads=4 结果一致；双开关各组合生效

Exit Criteria:

- [x] walker 结果对给定目录树确定且符合双开关语义（断言具体文件列表）
- [x] **接线验证**：walker 测试含 gitignore 命中/未命中行为断言（isIgnored 效果经 walker 结果可见）
- [x] `./mvnw test -pl nop-rg/nop-rg-core -am -Dtest=ParallelFileWalkerTest -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [x] 新增测试显式覆盖上述行为
- [x] No owner-doc update required（模块内部能力；repo-map 在 Phase 3 统一更新）
- [x] `ai-dev/logs/` 已更新

### Phase 2 - RegexSearcher + SearchCoordinator（COORD-01..03）

Status: completed
Targets: `nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/coordinator/`、`ai-dev/design/nop-rg/01-architecture-baseline.md`

- Item Types: `Fix`（新功能）、`Decision`（策略契约修订：RegexSearcher 独立接口）

- [x] `FoldingByteSearcher`（coordinator 包）：实现 `ByteSearchStrategy`，ASCII 大小写折叠的字面量搜索（`-i` 语义；非 ASCII 字节精确匹配），Wave 1 `search/` 包零修改
- [x] `RegexSearcher`（独立接口 + 实现，**不**实现 `ByteSearchStrategy` 签名）：java.util.regex，UTF-8 整文件解码（无阈值），`Pattern.CASE_INSENSITIVE`（不带 UNICODE_CASE）支持 `-i`；char 偏移→字节偏移桥接映射
- [x] 行提取：基于**整文件** MappedFileReader 映射计算行号/行起止偏移/行文本（CRLF/LF 兼容）；**禁止基于 chunk 视图做行提取**（长行跨界时视图内无行首数据）；`ChunkedFileReader` 本 Phase 不接生产线（Wave 3 大文件路径接线，记入 Follow-up）
- [x] `SearchCoordinator`：glob 过滤（GlobMatcher）→ ParallelFileWalker → 整文件映射搜索（字面量：Scalar/Folding；正则：RegexSearcher）→ 聚合（`SearchResult.truncated` 由触限置位；二进制文件**整文件跳过**——文件头 8KB 含 NUL 字节即视为二进制，对齐 rg 默认行为）
- [x] 策略选择：字面量/正则请求分发到对应 searcher；未注册/未知策略显式抛异常（不静默降级）
- [x] 集成测试：临时树 + gitignore + glob + 字面量（含 `-i` 折叠）/正则两策略 + 行号/行文本断言
- [x] **回写 design doc**：决策 5 表中 RegexSearcher 从 `ByteSearchStrategy` 实现层级移出，注明独立接口 + 整文件解码决策；`FoldingByteSearcher` 补入实现表

Exit Criteria:

- [x] coordinator 端到端测试通过且断言行号/行文本具体值
- [x] **接线验证**：FoldingByteSearcher/RegexSearcher 被策略选择真实调用（SearchCoordinatorTest 断言可证）；`ai-dev/design/nop-rg/01-architecture-baseline.md` 决策 5 已修订
- [x] **无静默跳过**：VECTOR 策略显式抛 NopRgException；二进制整文件跳过（头 8KB NUL 规则）
- [x] `./mvnw test -pl nop-rg/nop-rg-core -am` 全量通过（49 tests）
- [x] `ai-dev/logs/` 已更新（收口时统一补记）

### Phase 3 - CLI 入口（CLI-01..04）

Status: completed
Targets: `nop-rg/nop-rg-cli/`

- Item Types: `Fix`（新模块 + 新功能）

- [x] `nop-rg-cli` 模块：加入 `nop-rg/pom.xml` java22-modules profile；release=22 四属性；依赖 `nop-rg-core`（`${project.version}`）+ picocli（无版本，BOM 链解析）+ junit test；主类 `io.nop.rg.cli.NopRgMain`
- [x] 参数：`PATTERN`/`PATH` 位置参数、`-g/--glob`（可重复）、`-i`、`-c/--count`（**匹配行数**）、`-l/--files-with-matches`、`--json`、`--no-ignore`（只关 gitignore；隐藏始终跳过）、`--threads N`、`--delegate-rg[=path]`（可选值 = rg 可执行路径，默认 `rg`；测试可指向不存在路径）
- [x] 退出码：命中 0 / 未命中 1 / 错误 2
- [x] `--json` 输出（对齐 rg 15.1.0 实测 schema）：`begin`/`match`/`end` 三类消息、无命中文件零消息；`data.path`（相对搜索根）、`data.lines.text`（含行终止符）、`data.line_number`、`data.absolute_offset`（行首）、`data.submatches[]` = `{"match":{"text":...},"start":行内字节偏移,"end":行内字节偏移}`；`end` 消息不含 stats（豁免项）、保留 `binary_offset`:null 字段名以同构；不实现 `summary`；**同一行多命中聚合为单条 match 消息（多个 submatch，对齐 rg 实测）**；非 json 模式输出 `path:line:text`
- [x] CLI 启动时初始化 VFS（CoreInitialization）以支持 gitignore 过滤；`--no-ignore` 时跳过
- [x] 单元/集成测试：进程内调用主类（捕获 stdout/退出码），断言参数解析、JSON 字段结构、`-c` 行计数语义、`-l` 输出、`--delegate-rg` 正常桥接 + 注入不存在 rg 路径时明确报错
- [x] `docs-for-ai/01-repo-map/module-groups.md` nop-rg 条目更新（walk/coordinator/cli 子模块）

偏差：参数清单增加 `-r/--regex` 开关（plan 首版遗漏）——COORD-02 的 REGEX 策略需 CLI 入口；rg 无对应开关（其默认即正则）。已记入 daily log。

Exit Criteria:

- [x] `./mvnw test -pl nop-rg/nop-rg-cli -am` 通过（17 tests：NopRgMainTest 8 + RgComparisonTest 9）
- [x] `--json` 输出与 rg --json 同构（RgComparisonTest.testJsonModeMatchesRg 元组级对照通过；豁免规则见 Phase 4 归一化）
- [x] `--delegate-rg` 桥接正确；`invokeRg` 注入不存在 rg 路径 → 报错 + 退出码 2（testDelegateRgMissingBinaryFailsWithCode2）
- [x] **端到端验证**：从 CLI 参数到 stdout 的完整路径已验证（进程内 main，8 个参数/输出/退出码用例）
- [x] No owner-doc update required（repo-map 已在本 Phase 更新）
- [x] `ai-dev/logs/` 已更新

### Phase 4 - rg 对比测试 + M2 收口（TEST-01..03）

Status: completed
Targets: `nop-rg/nop-rg-cli/src/test/`

- Item Types: `Proof`（对比验证）

- [x] 对比框架：同 corpus（测试内确定性生成、纯 ASCII）上 nop-rg（进程内 main）与 rg（子进程，corpus 为 CWD）结果规范化后比较。**归一化规则**：按文件分组比较（消息顺序非确定）；比较 `begin`/`match` 消息的 `line_number`/`lines.text`/`submatches[].match.text`/`submatches[].start|end`；`end` 消息仅比较 `path` 与 `binary_offset:null`（stats/elapsed 豁免）；`summary` 不参与
- [x] 场景：基本字面量、glob 过滤（含 `!` 负规则）、`-i`（ASCII corpus）、`--no-ignore`（注意：两侧隐藏文件均不搜，corpus 含隐藏文件以验证该语义一致）、`-c` 行数、`-l` 文件列表、空结果退出码 1
- [x] rg 不可用时 assumeTrue 跳过并记日志

Exit Criteria:

- [x] 对比测试全绿（9 场景全过：basic/glob-include/glob-exclude/-i/--no-ignore/-c/-l/--json 元组/空结果退出码）
- [x] **端到端验证**：nop-rg CLI vs rg 子进程在同一 corpus 的结果一致性已验证
- [x] `./mvnw test -pl nop-rg/nop-rg-cli -am` 全量通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [x] roadmap Work Item 6/7/8/9 → `done`；M2 依赖项全 done 后标 `done`
- [x] 所有 in-scope confirmed live defects 已修复
- [x] 行为/契约结果已达成：CLI 可用（参数→搜索→JSON/文本输出、--delegate-rg、rg 对比一致）
- [x] 必要 focused verification 已完成（Phase 1-4 Exit Criteria 全勾）
- [x] 不存在被静默降级的 in-scope live defect 或 contract drift
- [x] owner docs：repo-map nop-rg 条目已更新；design 决策 5 已按 RegexSearcher 独立接口修订
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：CLI 入口到输出端到端连通（审计逐环追踪 NopRgMain:103→coordinator:58→walker:59→MappedFileReader:103→策略分发:112-119→LineCursor:140→JsonOutput）；无空方法体/静默跳过
- [x] `./mvnw test -pl nop-rg/nop-rg-cli -am` 通过（审计独立复跑 17→现 18 tests；nop-core 294 上游测试无回归）
- [x] 代码规范检查：imports 分组、无裸 RuntimeException、错误消息英文
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-rg --severity high` 退出码 0

## Deferred But Adjudicated

### ChunkedFileReader 生产接线延期至 Wave 3

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本 Wave coordinator 采用整文件 MappedFileReader（搜索与行提取同源，无跨视图行提取问题）；ChunkedFileReader 是 Wave 1 已交付且有组件级测试的存量（非本 plan 新增空壳），其 >1GB 生产接线由 roadmap Wave 3 stage 12/13 显式承接。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/nop-rg-roadmap.md` Wave 3（stage 12 大文件 / stage 13 并行优化）

## Non-Blocking Follow-ups

- Wave 3 大文件路径：ChunkedFileReader 接入 coordinator 生产线（>1GB 场景 + 按模式长度设置 overlap 的运行时守卫）；本 Wave coordinator 为整文件映射路径。
- Wave 3（JMH/JFR/并行优化）另行拟制。
- `--hidden`、上下文行（-A/-B/-C）、颜色输出：不在 roadmap 范围。
- walker 工作线程 scanDir 抛 IOException 时带 UncheckedIOException 死亡、walk() 静默返回不完整结果（audit Minor m2，Wave 3 并行优化一并治理：错误聚合/快速失败）。
- RegexSearcher 每文件重新 Pattern.compile（audit Minor m4，Wave 3 性能范畴）。
- LineCursor CRLF 分支已补测试（crlf.txt 用例）；--delegate-rg 正向桥接已补测试（buildRgArgs + 真实 rg 执行）。

## Closure

Status Note: Wave 2 全部交付物落地：ParallelFileWalker（gitignore/隐藏双开关 + 专用线程池）、SearchCoordinator（策略选择 + 行提取，VECTOR 显式失败）、FoldingByteSearcher/RegexSearcher、nop-rg-cli（rg --json 兼容 + --delegate-rg + 退出码对齐）。与系统 rg 15.1.0 的 9 场景对比全部一致。三项 rg 实测语义差异（行号仅 tty、gitignore 仅 git 仓库、--json submatch 行内偏移）已实测对齐并记录。roadmap Work Item 6/7/8/9 与里程碑 M2 → done。
Completed: 2026-09-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure auditor（fresh session，agent_8da55002-2b2e-4367-8fa7-f2efc4e1971b，未参与实现）
- Audit Session: agent_8da55002-2b2e-4367-8fa7-f2efc4e1971b
- Evidence:
  - 独立复跑：`./mvnw test -pl nop-rg/nop-rg-cli -am` 17 tests 0 failures（RgComparisonTest 9 场景含真实 rg 对照）；`./mvnw test -pl nop-rg/nop-rg-core -am` 49 tests（48 执行 + 1 opt-in rg 对照，补跑 -Dtest.rg.compare=true 10/10）；上游 nop-core 294 tests 无回归
  - Anti-Hollow：CLI 端到端逐环追踪连通（NopRgMain:103 → coordinator:58 → walker:59 → MappedFileReader:103 → 策略分发:112-119 → LineCursor:140 → JsonOutput）；VECTOR 显式抛异常有测试钉住；11 个主源文件无空方法体/吞异常/placeholder（grep 零命中）
  - 实现抽查：FoldingByteSearcher 折叠域一致、RegexSearcher CASE_INSENSITIVE 无 UNICODE_CASE + Utf8OffsetMap 代理对处理、LineCursor CRLF/游标重置、NopRgMain -c 匹配行数 + delegate 预解析双形式
  - 工具门禁：check-plan-checklist --strict 0（completed 后复验）；scan-hollow --module nop-rg --severity high 0；check-doc-links --strict 0 errors
  - Deferred 分类检查：ChunkedFileReader 接线延期诚实（Wave 1 存量 + 组件测试兜底 + roadmap stage 12/13 successor）；无 in-scope live defect 被降级
  - 审计问题处置：M1（delegate-rg 正向桥接测试缺失）已补 buildRgArgs 测试 + 真实 rg 执行断言；漏勾 5 项已勾；m2/m4 记入 Non-Blocking Follow-ups；m3 加注释；m5 日志精度已修正；审计环境注记（并行 Maven 互踩）记入 daily log
  - 注：审计后新增 2 个测试（delegate 正向桥接 + LineCursor CRLF），cli 测试 17→18、core 49→50，均已复跑通过

Follow-up:

- walker scanDir IOException 错误聚合/快速失败（Wave 3）
- RegexSearcher Pattern 编译缓存（Wave 3）
- ChunkedFileReader 生产线接线（roadmap Wave 3 stage 12/13）
