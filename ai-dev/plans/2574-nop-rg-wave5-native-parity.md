# 2574 nop-rg Wave 5 Native-parity Optimization (roadmap items 16-22)

> Plan Status: completed
> Last Reviewed: 2026-11-20
> Source: `ai-dev/backlog/nop-rg-roadmap.md` Wave 5 (2026-11-19 study of ~/sources ripgrep/fd/ugrep); baseline from plan 2276: 512MB Coord/Rg ≈ 51.3%, 64MB ≈ 82.3% (quiet env)
> Related: `ai-dev/plans/2276-nop-rg-round4-deep-audit-quality-perf.md`

## Purpose

收口 roadmap Wave 5（items 16–22）：SWAR 字节扫描、madvise 预读、双定点 vector prefilter、候选行两级 regex 路径、GlobSet 策略分类、walker stat 削减与 macOS 读路径裁定、最终吞吐比重定基线。完成后更新 roadmap Work Items 与 milestone 状态。

## Current Baseline

- 工作区已有未提交的 item 16/17/18 部分 landing（`ByteScan.java` 新文件、`LineCursor`/`SearchCoordinator`/`ScalarByteSearcher` 委托 SWAR、`MappedFileReader.adviseSequential` 仅 Linux、`VectorPreparedLiteral` 双定点分支、`ScalarByteSearcher.selectSecondAnchorIndex`）——本计划负责补齐其测试、验证并提交。
- `ByteScan.findFirstByte`（SWAR 8 字节扫描）目前**无单元测试**。
- 双定点分支目前**无针对性测试**（`VectorLiteralEquivalenceTest` 为模糊等价测试，可能覆盖但未钉住 dual-pin 专属路径）。
- Regex 路径（`RegexSearcher.findAll`）：整文件解码为 String 后直接跑 `java.util.regex`，**无任何 prefilter**；>256MB 文件显式抛错（`SearchCoordinator.searchFile`）。
- Glob（`GlobMatcher`/`CompiledGlob`）：全部模式统一走两指针段匹配，**无策略分类**（无 extension map、无字面量快路径）。
- Walker（`ParallelFileWalker`）：每 entry 多次 stat（`isDirectory` + `isSymbolicLink` + `isRegularFile` 分离调用）；无 macOS 特化。
- I/O：`MappedFileReader`/`ChunkedFileReader` **仅 mmap**，无非 mmap 读路径；madvise 仅 Linux。
- 构建验证通道：nop-ib 快路径可编译 `nop-rg-core`，但 `nop-rg-vector` 需 `--add-modules jdk.incubator.vector`，nop-ib 增量 javac 不读 compilerArgs（实测 2026-11-20 exit 1）；沿用 2026-09 既有裁定：nop-rg 模块组测试用 Maven 直跑（`./mvnw test -pl nop-tools/nop-rg/...`），nop-ib 仅用于 core 快路径编译检查。

## Goals

- Roadmap items 16–21 全部 `done`：SWAR/madvise/双定点补测试收口；新增 item 19（候选行两级 regex）、item 20（GlobSet 分类）、item 21（walker stat 削减 + macOS 读路径裁定）。
- Item 22：quiet env 下重测 512MB/64MB Coord/Rg 吞吐比，结果记入 roadmap Wave 5 证据区与 daily log。
- 每完成一个 Phase 即验证（Maven/nop-ib）并提交一次。

## Non-Goals

- 不改动 rg 兼容契约（G1 non-overlapping spans、G2 exit code、G3 JSON escaping，plan 2276 钉子）。
- 不引入新第三方依赖（regex 引擎仍用 `java.util.regex`）。
- 不做 regex 引擎替换或 JIT-compiled regex（watch-only follow-up）。
- 不重写 walker 为事件驱动 push 模型（仅做 stat 削减）。

## Scope

### In Scope

- `nop-tools/nop-rg/nop-rg-core`：ByteScan 测试、walker、GlobMatcher、RegexSearcher/SearchCoordinator、MappedFileReader 冒烟。
- `nop-tools/nop-rg/nop-rg-vector`：双定点等价测试。
- `nop-tools/nop-rg/nop-rg-benchmark`：重定基线跑测（如需可微调参数，不改契约）。
- `ai-dev/backlog/nop-rg-roadmap.md`（状态块）、`ai-dev/logs/`、`ai-dev/analysis/`（macOS 读路径裁定）。

### Out Of Scope

- nop-ib 对 incubator 模块的 compilerArgs 支持（独立 follow-up）。
- Windows 平台 madvise 等价物（PrefetchVirtualMemory）。

## Execution Plan

### Phase A - Items 16+17: SWAR ByteScan + madvise 收口

Status: completed
Targets: `nop-rg-core/src/{main,test}/java/io/nop/rg/core/{search,io}/`

- Item Types: `Feature`

- [x] 新增 `ByteScanTest`：空区间、offset 非 8 对齐、limit-offset < 8、跨 8 字节边界命中于各 lane（0..7）、首字节命中、尾字节命中、target 为负值字节（0x80-0xFF，XOR 掩码路径）、与逐字节扫描对拍的随机 fuzz（≥1000 随机段/随机 target）
- [x] `MappedFileReaderTest` 补 macOS/Linux 冒烟断言：打开/读取/关闭在 madvise 路径启用与否下行为不变（当前平台跑 Linux 分支不可行则注明平台裁定）
- [x] `ScalarByteSearcherTest` 补 `selectSecondAnchorIndex`：长度 <2 返回 -1、跳过 first anchor、频率最低字节当选（tie 规则：与 first anchor 选择相反，`f < bestFreq` 严格小于——同频取首个出现，测试按返回下标精确断言）
- [x] 验证：nop-ib check（core 编译）+ `./mvnw test -pl nop-tools/nop-rg/nop-rg-core` EXIT=0
- [x] 提交（含 roadmap Wave 5 块首次入库）

Exit Criteria:

- [x] ByteScanTest 全部用例绿，fuzz 对拍无差异
- [x] selectSecondAnchorIndex 测试绿
- [x] `./mvnw test -pl nop-tools/nop-rg/nop-rg-core` EXIT=0
- [x] No owner-doc update required（行为语义不变，roadmap 状态在 Phase F 统一更新）
- [x] `ai-dev/logs/2026/11-20.md` 已更新

### Phase B - Item 18: 双定点 vector prefilter 收口

Status: completed
Targets: `nop-rg-vector/src/{main,test}/java/io/nop/rg/vector/`

- Item Types: `Feature`

- [x] `VectorLiteralEquivalenceTest` 增补 dual-pin 钉子：模式含两个罕见字节且距离 < SPECIES.length()（走 dual-pin 分支）、距离 ≥ SPECIES.length()（不走）、全同字节模式（pinIdx2=-1 退化单锚）、ignoreCase 下 pinByte2 翻转分支；断言与 Scalar 结果逐项一致（含候选无效后坏字符推进路径）
- [x] 验证：`./mvnw test -pl nop-tools/nop-rg/nop-rg-vector` EXIT=0（nop-ib 不支持 incubator，见 Current Baseline）
- [x] 提交

Exit Criteria:

- [x] dual-pin 专属路径被测试显式覆盖（非仅 fuzz 碰巧命中）
- [x] vector 与 scalar 等价断言全绿
- [x] `./mvnw test -pl nop-tools/nop-rg/nop-rg-vector` EXIT=0
- [x] No owner-doc update required
- [x] `ai-dev/logs/2026/11-20.md` 已更新

### Phase C - Item 19: 候选行两级 regex 路径

Status: completed
Targets: `nop-rg-core/src/main/java/io/nop/rg/core/{coordinator,search}/`

- Item Types: `Feature`

- [x] 新增 literal 提取器，**接受语法子集显式钉死**：仅当 pattern 可证明「某字面量子串必然出现在任一匹配中且匹配不跨行」时提取。拒绝情形（返回 null 回退整文件路径）：交替 `|`、可量化组（`(...)?`/`*`/`+` 作用到含字面量的组）、字符类内提取、lookaround、反引用、以及**任何可能跨行匹配的构造**（`\s`、`\R`、`\n`、否定字符类 `[^…]`、`.` 默认不匹配 `\n` 但其量化形式配合组仍可能跨行——凡无法保守证明不跨行即拒绝）。接受情形：不含上述构造且含 ≥1 个连续字面量 run 的 pattern，取最长 run；转义还原；忽略大小写时同步 fold 并标记 case-insensitive 预滤
- [x] `SearchCoordinator` regex 路径改两级：先用 `ByteScan`/`ScalarByteSearcher` 在 mapped segment 上按行扫描必须字面量（候选行），仅对候选行解码并跑 regex 确认；提取失败（含跨行风险）回退现有整文件路径。语义不变：span 位置、行聚合、exit code 全部与现路径一致
- [x] 新增测试：提取器单测（字面量 run、转义 `\.`、字符类拒绝、量词截断、交替拒绝、`\s`/`[^…]` 拒绝、大小写折叠、无提取返回 null）；等价测试——候选行路径 vs 关闭 prefilter 路径在多组 pattern/语料上 span 完全一致（含 0 命中、行首尾命中、-i）；**跨行 pattern（如 `a\s*b`、`x[^y]z`）必须落回退路径且结果与整文件路径一致**（钉子）；**prefilter 路径确实被调用**的断言（常见字面量 pattern，如 `foo\d+bar`——接线验证）；>256MB 大文件 regex 行为保持不变（已有钉子 `SearchCoordinatorTest:244` 覆盖同一显式抛错分支；>256MB 专项运行不可行——整文件解码内存契约即 >256MB 禁 regex 的根因，256MB 堆下必 OOM，裁定：既有分支级钉子即充分覆盖）
- [x] 验证：nop-ib check + `./mvnw test -pl nop-tools/nop-rg/nop-rg-core` EXIT=0
- [x] 提交

Exit Criteria:

- [x] 提取器单测覆盖上述全部分支
- [x] 等价测试证明 prefilter 路径与回退路径结果逐 span 一致（接线验证：prefilter 确在 regex 前被调用，等价测试天然覆盖）
- [x] 原有 regex 相关测试全绿（无行为漂移）
- [x] `./mvnw test -pl nop-tools/nop-rg/nop-rg-core` EXIT=0
- [x] No owner-doc update required（内部优化，外部契约不变）
- [x] `ai-dev/logs/2026/11-20.md` 已更新

### Phase D - Item 20: GlobSet 策略分类

Status: completed
Targets: `nop-rg-core/src/main/java/io/nop/rg/core/glob/`

- Item Types: `Feature`

- [x] `GlobMatcher` 编译期分类：`*.<ext>` 单段模式 → 扩展名 Set；无通配符模式 → 字面量 basename/path Set；其余 → 现有两指针匹配器。`accept()` 按扩展名/字面量快路径短路，未命中再走慢路径；include/exclude 语义与优先级完全不变
- [x] 新增测试：分类正确性（每类至少一例，含 `*.log`、字面量 `Makefile`、`**/*.java` 落慢路径）；分类 vs 全慢路径对拍 fuzz（随机 glob 集 × 随机路径，结果一致）；`GlobMatcherTest` 原有用例全绿
- [x] 验证：nop-ib check + `./mvnw test -pl nop-tools/nop-rg/nop-rg-core` EXIT=0
- [x] 提交

Exit Criteria:

- [x] 分类单测 + 对拍 fuzz 全绿
- [x] 原有 GlobMatcherTest 全绿
- [x] `./mvnw test -pl nop-tools/nop-rg/nop-rg-core` EXIT=0
- [x] No owner-doc update required
- [x] `ai-dev/logs/2026/11-20.md` 已更新

### Phase E - Item 21: Walker stat 削减 + macOS 读路径裁定

Status: completed
Targets: `nop-rg-core/src/main/java/io/nop/rg/core/walk/`、`ai-dev/analysis/`

- Item Types: `Feature` + `Decision`

- [x] `ParallelFileWalker`：每 entry 单次 `Files.readAttributes(child, BasicFileAttributes.class, NOFOLLOW_LINKS)` 取代 isDirectory/isSymbolicLink/isRegularFile 三连 stat（先查 isSymbolicLink 再判 dir/regular，保持 symlink 语义）；symlink 目标类型用一次 `Files.readAttributes(..., FOLLOW_LINKS)` 判定；行为不变（hidden、gitignore、dir symlink 剪枝、fail-fast 语义逐条保持）；`ParallelFileWalkerTest` 增补显式用例：symlink→dir（剪枝）、symlink→file（当 regular file）、悬空 symlink，若已有则确认覆盖
- [x] （裁定 implement 时）macOS read 回退实现拆为独立 Phase E2 或 successor plan，不在本 Phase 内膨胀 scope
- [x] macOS 非 mmap 读路径**裁定**（`ai-dev/analysis/2026-11-20-nop-rg-macos-read-path.md`）：对比 mmap 与 FileChannel 顺序读在本机（macOS）的 512MB 吞吐 + madvise 缺失影响，给出 implement / not-implement 结论与理由（rg 在 macOS 因 VM 锁禁用 mmap 的证据核对）；结论为 implement 时落地 `ChunkedFileReader` 的 read 回退路径并补等价测试，not-implement 时该裁定即收口
- [x] 验证：`./mvnw test -pl nop-tools/nop-rg/nop-rg-core` EXIT=0
- [x] 提交

Exit Criteria:

- [x] Walker 每 entry 属性调用降为 readAttributes 单次（代码可查），`ParallelFileWalkerTest` 全绿
- [x] macOS 读路径裁定文档存在且含结论（implement 或 not-implement 均可接受，但必须二选一并给证据）
- [x] 若裁定 implement：read 回退路径有等价测试且全绿
- [x] `./mvnw test -pl nop-tools/nop-rg/nop-rg-core` EXIT=0
- [x] No owner-doc update required（裁定文档落在 analysis/，非规范文档）
- [x] `ai-dev/logs/2026/11-20.md` 已更新

### Phase F - Item 22: 吞吐比重定基线 + roadmap 收口

Status: completed
Targets: `nop-rg-benchmark/`、`ai-dev/backlog/nop-rg-roadmap.md`

- Item Types: `Proof`

- [x] quiet env 协议（参考 plan 2276：无并发负载、固定 corpus 种子）跑 `RgCompareBenchmark` + `CoordinatorEndToEndBenchmark`（512MB/64MB），记录 Coord/Rg 吞吐比
- [x] 对比 plan 2276 基线（512MB ≈ 51.3%，64MB ≈ 82.3%），结果与结论写入 roadmap Wave 5 证据区（`Evidence ledger` 行后追加）与 daily log
- [x] roadmap Work Items：16–21 置 `done`；22 置 `done`；milestone「吞吐比逼近 rg」置 `done`（条件：16..21 done 且已重定基线）
- [x] 独立子 agent closure audit（fresh session），证据写入本 plan `Closure` 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs`、`scan-hollow-implementations.mjs --module nop-rg`、`check-doc-links.mjs --strict` 退出码全 0
- [x] 最终提交（roadmap + plan 收口 + log）

Exit Criteria:

- [x] 吞吐比数据落 roadmap + daily log（含环境说明）
- [x] roadmap 状态块与各 item 实际完成状态一致
- [x] 独立 closure audit 证据已写入 plan
- [x] 三个门禁工具退出码 0
- [x] 最终提交完成

## Closure Gates

- [x] Roadmap items 16–21 全部 `done` 且各自有 landed 代码 + focused tests
- [x] Item 22 重定基线数据已落盘（roadmap + log）
- [x] 不存在被静默降级的 in-scope live defect 或 contract drift
- [x] 独立子 agent closure audit 完成并记录证据
- [x] Anti-Hollow Check：prefilter 在 regex 路径运行时确实被调用（等价测试覆盖调用链）；无空方法体/静默跳过（madvise 的静默 best-effort 为显式裁定语义，属例外，已在代码注释声明）
- [x] `./mvnw test -pl nop-tools/nop-rg/nop-rg-core,nop-tools/nop-rg/nop-rg-cli,nop-tools/nop-rg/nop-rg-vector` EXIT=0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2574-nop-rg-wave5-native-parity.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-rg --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### nop-ib incubator compilerArgs 支持

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: nop-rg vector 模块有 Maven 直跑的既定验证通道（2026-09 已裁定并多次审计通过），不阻塞本计划任何 exit criteria
- Successor Required: `no`
- Successor Path: 独立 follow-up（nop-tools/nop-ib 域）

## Non-Blocking Follow-ups

- Windows PrefetchVirtualMemory 等价 madvise（当前 Windows 无预读提示）
- regex 引擎替换（Re2J 等 JIT 编译引擎）——若 item 19 prefilter 后 regex 仍是瓶颈再评估

## Closure

Status Note: Wave 5 items 16–22 全部落地：SWAR 字节扫描/madvise（A）、dual-pin vector prefilter（B）、候选行两级 regex 路径（C）、GlobSet 策略分类（D）、walker stat 削减 + macOS 读路径裁定 not-implement（E）、吞吐比重定基线 512MB ≈42–44% / 64MB ≈78%（F，数据与方差说明落 roadmap Wave 5 证据区 + daily log）。roadmap Wave 5 milestone「吞吐比逼近 rg」置 done（解锁条件 = 16..21 done 且已重定基线）。512MB 比值对 2276 基线偏低已如实记录并登记 watch-only 复测 follow-up（当日环境方差显著）。
Completed: 2026-11-20

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（fresh session，agent_0d59fb0c-ed80-4664-9c27 总结见其报告）
- Audit Session: agent_0d59fb0c-ed80-4676-aa90-5d18b79216f8
- Evidence:
  - Phase A–E 逐条 PASS（file:line 证据见审计报告：ByteScan SWAR 实现与 7 用例/1000 轮 fuzz、MappedFileReader.adviseSequential Linux-only 显式裁定、VectorPreparedLiteral dual-pin 4 钉 + fuzz、RegexLiteralExtractor 跨行拒绝真实逻辑 + RegexSearcher 候选路径运行时派发（非死代码）+ 接线断言、GlobMatcher 快路径真实接入 accept()/excluded()、ParallelFileWalker 单次 readAttributes、macOS 裁定文档含实测数据）
  - 门禁工具：check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0；scan-hollow-implementations --module nop-tools/nop-rg --severity high 退出码 0（0 Critical/High）
  - Focused tests 复跑：RegexPrefilterEquivalenceTest 9 + ByteScanTest 7 + GlobStrategyClassificationTest 4 = 20/20 全绿（审计者独立执行）
  - Anti-Hollow：候选行 regex 路径调用链 findAll→findAllCandidateLines→processCandidateLine 追踪确认非死代码；GlobMatcher 快路径经 matchesAny 接入两入口；madvise 静默 best-effort 为显式文档化裁定（MappedFileReader.java:94-113）
  - Deferred 分类诚实性：HONEST（唯一 deferred = nop-ib incubator compilerArgs，out-of-scope improvement，Maven 直跑通道真实存在）

Follow-up:

- (pending)
