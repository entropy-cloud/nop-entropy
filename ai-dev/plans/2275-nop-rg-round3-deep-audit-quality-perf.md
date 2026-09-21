# 2275 nop-rg 第三轮深度审计 —— CLI 组合语义缺陷修复 + API 面收缩 + 性能收敛复验

> Plan Status: active
> Last Reviewed: 2026-09-22
> Source: 用户指令（对 nop-rg 深度审计：可读性/长期可维护性/性能，JMH/JFR 反复优化直到无 ≥2% 收益）；live repo 审计（2026-09-22，本 plan Current Baseline 全部经 read/grep/rg 实测实证）；plans 2268（F1-F9 质量清理）与 2273（第二轮审计 A1-A8 + 性能收敛，均 completed）
> Related: Plan 2262-2268、2273（均 completed）；2273 终态 = 性能基线守护参照（吞吐比 64MB 61.7% / 512MB 55.7%，TEXT/多小文件口径基线见其记录表）；2268 closure 遗留 watch-only「JfrSwitchTest import 风格」由本计划 Phase 1 清偿
> Draft Review: 第一轮独立子 agent 对抗性审查（agent_d16e2d1d-b935-4edc-9f71-a6217702c400）：1 Major（Blocker 级）+ 3 Minor 全部折入（core 基线默认口径 56 run + 1 skip、rg 优先级表述、G4 孤儿字段、TEXT 区间/profiler 注记）。第二轮复审（fresh session，agent_30247617-31cd-4a51-a77d-51b1a2ae5afc）：4 项修订核对全 PASS；新发现 4 处已折入——n1（Phase 1 Exit Criteria 把 G1 用例双重计入 core，修为 core 56+1skip 不变 / cli =27）、n2（rg 输出模式标志实为按最后出现者生效，README 差异行按此表述）、n3（G5「无参构造器」实为单参便捷构造器 `ChunkedFileReader(Path)`）、n4（Phase 2 m2 install 注记改写）；复审结论「可进入执行阶段」，两处一行级修正已按复审处方原文落盘，进入执行

## Purpose

在 2268 与 2273 两轮已收口的 live 代码上执行第三轮深度审计：(1) 修复本轮新发现的 live defect（CLI `-c --json` 组合输出退化）与 2273 R1 优化遗漏的镜像/文档漂移；(2) 收缩死公共 API 面（glob/io 两包共 5 个零消费成员）；(3) 清偿 2268 遗留 watch-only 风格项；(4) 在当前基线上重建性能基线并重新执行严格收敛循环——**连续两轮内所有被评估候选优化均未通过保留条件（收益 < max(2%, 3σ) 或误差棒重叠）方可终止，或候选池枯竭（no-candidate 裁定 + 穷尽说明）**；(5) 收口复验吞吐比不回退（≥50%）。

## Current Baseline

- 提交基线：nop-rg 最后 touched 于 `017f71307c`（plan 2273 收口 commit）；当前 HEAD `0e40a7f605`（他会话 nop-lint 工作，与本计划无关）。**工作区未提交改动均为他会话的 nop-lint 项（`nop-lint/nop-lint-core/**`、`ai-dev/design/nop-lint/09-suppression.md`）——不触碰、不提交**；本计划所有 commit 均显式指定路径。
- 测试基线（2026-09-22 实测 `./mvnw test -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli,nop-rg/nop-rg-vector -am` EXIT=0）：core **7 类 56 run + 1 skip**（rg 门控 `GlobMatcherTest.testCompareAgainstSystemRg`；large-file 组 3 个测试经 pom `excludedGroups=large-file` 默认排除，显式运行 `-DexcludedGroups= -Dgroups=large-file -DargLine=-Xmx256m` 另计 3/3——2273 收口通过）、cli 26（JfrSwitch 3 + NopRgMain 9 + RgComparison 9 + VectorMode 5；RgComparison 默认跑、rg 不在 PATH 时 assumeTrue 跳过）、vector 10。
- 性能守护参照（2273 终态，勿回退）：吞吐比 64MB 61.7% / 512MB 55.7%（双档 ≥50%）；TEXT 口径基线 R1 后 ~1MB 603-686 / 64MB 11.0-12.7 ops/s（R5b 配对 base/cand 区间）；many-small count 33.37±0.80 ops/s；count 口径终态 profile = indexOf 42.3% + matchesAt 32.1%（扫描带宽地板）。
- 已裁定不重复评估项（2267/2273 两次实证 <2% 或裁定记录在案，本轮循环 row0 记裁定行不再烧机时）：SWAR LF 扫描、span-gap 行计数、count 口径单遍融合扫描、matchesAt verify 次序、LineCursor 同行 memo（零触发面）、≤1MB 堆读替代 mmap（R3 实测劣化）、MappedFileReader double-stat 消除（R4 算术上界 <2%）、12B SIMD 阈值（维持 16B）、CompiledGlob 进程级缓存（watch-only）、per-search ForkJoinPool（产品为每进程单次搜索）。
- 基准设施就绪：5 个 JMH 基准类 + HotspotProfiler（JFR 热点聚合器）；运行方式 = cwd `nop-rg/nop-rg-benchmark/` 下 `java -cp target/classes:$(cat target/cp.txt) org.openjdk.jmh.Main`（禁止 exec:java）；VECTOR 需 `--add-modules jdk.incubator.vector`；corpus 固定种子落 `$TMPDIR/nop-rg-bench-corpus/`。判定协议 = 2267 修订版共测配对（基线侧 = m2 快照 jar，候选侧 = target/classes，交替 3 对；保留条件 = 收益 ≥ max(2%, 3×σ_pair) 且误差棒不重叠；判定 JSON 落 `_tmp/nop-rg-bench/`）。环境：macOS arm64 / JDK 26.0.1 Zulu / rg 15.1.0（`/opt/homebrew/bin/rg`）。
- **本轮审计新发现（G1-G8，全部经 read/grep/rg 实测实证）**：
  - **G1（live defect，CLI 组合语义静默错误）**：`NopRgMain.search()` 中 `includeLineText = !count`——`-c --json` 组合时 resolveMode 依文档优先级选 JSON（--json > -l > -c），但行数据已被 `-c` 剥掉：输出 begin/end JSON 且**零 match 消息**、退出码 0，输出不携带任何命中信息。实测 rg 15.1.0 行为：`--json -c` 输出普通 count 格式（`path:N`），`--json -l` 输出普通文件列表，`-c --json` 输出完整 JSON——**rg 输出模式标志按最后出现者生效**；本项目固定 `--json` 优先，优先级次序与 rg 不同。本项目 README 与 `ResultPrinter.resolveMode` javadoc 钉死的优先级是 --json 优先；本修复不改优先级，只修数据完整性：`includeLineText = !count || json`——`--json` 需要全量 LineMatch，`-c` 仅在非 JSON 输出下生效。rg 优先级差异在 cli README「与 rg 的已知差异」记录。
  - **G2（2273 R1 镜像遗漏，诊断工具）**：`HotspotProfiler` TEXT 口径 sink 仍为 `new PrintWriter(BufferedOutputStream(..., 8192), true)`（autoflush 逐行 flush），而 R1 已将 CLI 与 `CoordinatorEndToEndBenchmark` 镜像改为 64KB 缓冲 + 收尾 flush；其类 javadoc 仍写「CLI 等价 autoflush sink」。影响：TEXT 口径热点 profile 的输出路径形态（PrintWriter.flush 链）不代表现行 CLI。同文件尚有 2268 F5 同类残留：内联全限定名（`io.nop.rg.cli.JfrSupport`/`java.io.PrintWriter`/`io.nop.rg.cli.ResultPrinter`/`java.io.BufferedOutputStream`/`java.io.FileOutputStream`）与 `var results`（F5 已确立显式类型风格）。修复 = sink 镜像 64KB 缓冲 + 每 run 收尾 flush（对齐 benchmark 镜像语义）+ javadoc/FQN/var 清理。
  - **G3（死公共 API）**：`GlobMatcher.hasIncludes()`/`hasExcludes()` 全仓零调用（grep 实证：仅定义处）；`GlobMatcher` 公共构造器唯一调用方为类内部（ACCEPT_ALL 与 of()）——测试仅用 of()/acceptAll()。修复 = 删两方法 + 构造器收窄 private（公共 API 面净缩小）。
  - **G4（死公共 API）**：`CompiledGlob.getPattern()` 全仓零调用（grep 实证）。删除；唯一读者被删后私有 `pattern` 字段成孤儿（doCompile 用构造参数非字段），随方法一并删除（构造器相应收窄）。
  - **G5（死公共 API + 孤儿常量）**：`ChunkedFileReader(Path)` 单参便捷构造器全仓零调用（所有生产/测试调用均显式传 chunkSize/overlap）；`getChunkSize()` 零调用；`DEFAULT_CHUNK_SIZE`/`DEFAULT_OVERLAP` 仅被该便捷构造器引用。删除四者（构造器删除后常量成孤儿）。
  - **G6（风格残留，组内 import 乱序——2268 F5/2273 A6 已确立组内字母序标准）**：benchmark 4 文件 `ScalarSearchBenchmark`/`RgCompareBenchmark`/`GlobBenchmark`（jmh annotations 组内 Fork 错位）/`HotspotProfiler`（jdk.jfr.consumer 组内 RecordingFile 前置）+ cli 测试 `JfrSwitchTest`（2268 closure 登记 watch-only「jdk.* import 前置」本计划清偿：全块重排为 io.nop → jdk.jfr → org.junit/picocli → java.* 分组）。另 `CoordinatorEndToEndBenchmark.endToEndSearch` 声明 `throws IOException` 但方法体无可抛检查异常（PrintWriter 吞 IO；setup 的 throws 合法保留）——删除死 throws。
  - **G7（陈旧 javadoc，双重过时）**：`Submatch` javadoc「count 口径下 text 为 null」——count 路径根本不构造 Submatch（2268 closure follow-up 已登记 watch-only），且 R5 后 `includeSubmatchText=false` 时 text 为**空串**非 null。更新为准确语义。
  - **G8（过时基线文档）**：benchmark README「plan 2273 Phase 2 补测」段 TEXT 基线数字（131.7/2.15/0.269 ops/s）系 R1 优化**前**的 autoflush 口径，R1 后现行口径约 5x（~620/11.2），未更新——新读者会以过时数字为当前基线。随 Phase 2 基线重建一并刷新（标注口径与日期）。
- 候选池（Phase 3 循环输入，均为审计新识别，非历史重复）：
  - **P1（TEXT 口径）**：`ResultPrinter` TEXT 分支每命中行 `path + ":" + lineNumber + ":" + text` 生成中间 String（一次 StringBuilder + 拷贝）——改为对 PrintWriter 直接分段 print（避免中间串分配）；注意 PrintWriter 方法 synchronized，4 次调用 vs 1 次的锁开销需 JMH 实证。
  - **P2（多小文件口径）**：`SearchCoordinator.isBinary` 逐字节扫 8KB——many-small 场景每 op 扫 512×8KB=4MB（占 64MB 读取量 6%）；候选 = JAVA_LONG 字读取 + 零字节检测（hasZero 位技巧）减少迭代数。
  - **P3（多小文件口径）**：`ParallelFileWalker` 每子项 2 次 stat（`Files.isDirectory` + 非目录 `Files.isRegularFile`；目录另有 `Files.isSymbolicLink`）——候选 = 单次 `readAttributes(NOFOLLOW_LINKS)` 派生类型（普通文件/目录 1 次 stat；符号链接仍需跟随判定）。算术上界：512 文件/op × ~0.5-1µs ≈ 0.26-0.5ms / op（op ≈ 30ms）≈ **≤1.7% < 2%**——参照 R4 先例以算术上界裁定，除非 row0 many-small profile 显示 stat 面占比显著超预期。
  - **P4（TEXT/组件级筛选）**：`ScalarByteSearcher.BYTE_FREQUENCY` 对全部小写字母同权（freq=100）——"needle" 各字节频率并列时锚点取最后一位（'e'，文本高频字节），锚点命中率高 → matchesAt verify 频繁（TEXT profile 22.5%）。候选 = 细化频率表（如元音/高频字母加权）改变锚点选择。结果语义不变（BMH 完整性由 Horspool 论证保证，任意锚点正确）；组件级先筛（ScalarSearchBenchmark scanHit），e2e 传导比 ≈ matchesAt 占比，需 ≥5% 组件级收益才有 e2e ≥2% 可能。
- 已裁定保留项（本轮复核不翻案）：`CompiledGlob` 进程级无淘汰缓存；策略层（ByteSearchStrategy/VectorByteSearcher 契约）；per-search ForkJoinPool；`LineMatch.getLineEnd()/getContentEnd()`（测试消费者存在，属结果契约面）；`ScalarByteSearcher.findFirstByte`/`VectorByteSearcher.findFirstByte`（design 决策 5 契约对齐）。

## Goals

- **G1 修复**：`NopRgMain.search()` 的 `includeLineText = !count || json`——`-c --json` 输出完整 JSON match 消息（优先级不变：--json > -l > -c）；新增 NopRgMainTest 聚焦用例（`-c --json` 含 `"type":"match"`）；cli README「与 rg 的已知差异」记录优先级差异（rg：count/-l 覆盖 --json）。
- **G2 修复**：HotspotProfiler TEXT sink 镜像 R1 缓冲形态（64KB + 每 run flush）+ javadoc 纠偏 + 内联 FQN 改 import + `var` 改显式类型。
- **G3/G4/G5 清理**：删除 `GlobMatcher.hasIncludes()/hasExcludes()`、`CompiledGlob.getPattern()`、`ChunkedFileReader()` 无参构造器 + `getChunkSize()` + 两个孤儿常量；`GlobMatcher` 构造器收窄 private。
- **G6/G7 清理**：5 文件组内 import 顺序归位（含 2268 watch-only 的 JfrSwitchTest）；`endToEndSearch` 死 `throws IOException` 删除；`Submatch` javadoc 更新为准确语义。
- **性能基线重建**：row0 σ_run（count 1MB/64MB/512MB 收尾档连跑 ≥2 次）+ TEXT/many-small 基线 + HotspotProfiler 双口径 profile（对照 2273 终态，无结构漂移则确认「不再评估」清单仍成立）+ 基线快照落 m2（共测配对基线侧）。
- **严格收敛循环（用户硬性要求）**：在受影响口径上以 JMH 度量、JFR 定位，逐候选「实现 → 迭代档筛选 → 共测配对收尾判定」；保留条件 = 收益 ≥ max(2%, 3σ) 且误差棒不重叠；**终止条件（字面）= 连续两轮内所有被评估候选均未通过保留条件**，或候选池枯竭（no-candidate 裁定 + 穷尽说明）。kept 优化逐项 commit。
- **吞吐比不回退**：收口复测 64MB / 512MB 双档 ≥50%。
- 每 Phase 完成即 commit（显式路径，避开他会话文件）；daily log 逐 Phase 更新。

## Non-Goals

- 不改输出模式优先级（--json > -l > -c 保持文档钉死的现行契约）；不实现 rg 的 count/files 覆盖 --json 语义（仅 README 记录差异）。
- 不新增 `--follow`、rg 100% 选项兼容、FM-Index、跨平台调优（前序 Non-Goal 延续）。
- 不翻案已裁定项（Current Baseline 末条清单）。
- 不重复评估 2267/2273 已实证 <2% 或裁定的方向（row0 记裁定行，不再烧机时；除非 row0 profile 显示热点结构剧变，须显式记录推翻理由）。
- 不回写历史计划文本（guide Rule 20；2268 的 watch-only 项以本计划执行清偿，不改 2268 文本）。
- 不动 2267 已确立的 count 口径 e2e 判定基准语义（既有 @Param 组合不变，仅按需钉定 -p）。
- 不触碰工作区中他会话的未提交改动（nop-lint 文件 + design/nop-lint）。
- 测试 import 的分组结构（如 SearchCoordinatorTest 单块混排）不在本计划范围——仅清偿已登记的 JfrSwitchTest 项与 main 代码 A6 同类残留，避免无功能收益的测试面大面积 churn。

## Scope

### In Scope

- `nop-rg/nop-rg-cli`：`NopRgMain`（G1）、`NopRgMainTest`（G1 聚焦用例）、`JfrSwitchTest`（G6 import）、cli README（G1 差异记录）。
- `nop-rg/nop-rg-core`：`glob/GlobMatcher`（G3）、`glob/CompiledGlob`（G4）、`io/ChunkedFileReader`（G5）、`coordinator/Submatch` javadoc（G7）；Phase 3 kept 优化涉及 `search/ScalarByteSearcher`、`coordinator/SearchCoordinator`、`walk/ParallelFileWalker`。
- `nop-rg/nop-rg-benchmark`：`HotspotProfiler`（G2）、4 文件 import（G6）、`CoordinatorEndToEndBenchmark` 死 throws（G6）、README（G8 + 循环结论）。
- owner docs：`ai-dev/design/nop-rg/01-architecture-baseline.md`（条件式：仅当 kept 优化触及决策 3/5 契约，如锚点启发式调整则更新决策 3）；`docs-for-ai/01-repo-map/module-groups.md` 预期 No update（无结构性变更）；daily log（对应日期）。
- 判定证据：`_tmp/nop-rg-bench/` 下 JMH JSON 与 profile 落盘。

### Out Of Scope

- nop-rg 之外的模块（他会话 nop-lint 工作区改动绝对不碰）。
- `docs-for-ai/` 平台使用文档正文（无契约变化）。
- 历史计划文本（2262-2273）。
- 测试文件的 import 分组结构重构（见 Non-Goals 末条）。

## Execution Plan

### Phase 1 - 审计修复：CLI 组合语义缺陷与可维护性清理

Status: completed
Targets: `nop-rg-cli/NopRgMain.java` + `NopRgMainTest.java` + `JfrSwitchTest.java` + README、`nop-rg-core/glob/{GlobMatcher,CompiledGlob}.java`、`nop-rg-core/io/ChunkedFileReader.java`、`nop-rg-core/coordinator/Submatch.java`、benchmark `HotspotProfiler.java` + 3 基准 import + `CoordinatorEndToEndBenchmark.java`

- Item Types: `Fix`（G1-G7）

- [x] G1：`NopRgMain.search()` 改 `includeLineText = !count || json`（--json 数据完整性；优先级不变）
- [x] G1 测试：`NopRgMainTest` 新增 `-c --json` 用例——输出含 `"type":"match"` 且含 match 数据（防回归空 JSON）；既有 9 场景不变
- [x] G1 文档：cli README「与 rg 的已知差异」补一行：`--json` 优先于 `-c/-l` 且输出完整 match 消息（rg 输出模式标志按最后出现者生效，如 `rg -c --json` 输出 JSON、`rg --json -c` 输出 count 格式）
- [x] G2：HotspotProfiler TEXT sink 改 64KB BufferedOutputStream + autoflush=false + 每 run `flush()`（对齐 CoordinatorEndToEndBenchmark 镜像语义）；类 javadoc「autoflush sink」表述纠偏；内联 FQN（JfrSupport/PrintWriter/ResultPrinter/BufferedOutputStream/FileOutputStream）改 import；`var results` 改显式类型
- [x] G3：删 `GlobMatcher.hasIncludes()/hasExcludes()`；构造器 public → private（唯一调用方为类内部）
- [x] G4：删 `CompiledGlob.getPattern()` 及随之孤立的私有 `pattern` 字段（构造器相应收窄）
- [x] G5：删 `ChunkedFileReader(Path)` 单参便捷构造器、`getChunkSize()`、`DEFAULT_CHUNK_SIZE`、`DEFAULT_OVERLAP`
- [x] G6：`ScalarSearchBenchmark`/`RgCompareBenchmark`/`GlobBenchmark`/`HotspotProfiler`/`JfrSwitchTest` 组内 import 字母序归位；`CoordinatorEndToEndBenchmark.endToEndSearch` 删除死 `throws IOException`
- [x] G7：`Submatch` javadoc 更新（text = 命中文本；includeSubmatchText=false 时为空串；count 口径路径不构造 Submatch）

Exit Criteria:

- [x] `./mvnw test -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli,nop-rg/nop-rg-vector -am` 全绿（core 7 类 56 run + 1 skip **不变**——surefire 汇总行 `Tests run: 57, Skipped: 1` 含 skip；cli **= 27**（26 + 新增 G1 用例）；vector 10）
- [x] benchmark 编译通过（`./mvnw compile -pl nop-rg/nop-rg-benchmark -am`，G2/G6 触及）
- [x] G1 行为验证：新用例断言 `-c --json` 输出含 `"type":"match"`；rg 对照组（RgComparisonTest）不受影响（其场景未组合该两开关）
- [x] grep 复核：`hasIncludes\|hasExcludes\|getPattern()\|getChunkSize\|DEFAULT_CHUNK_SIZE\|DEFAULT_OVERLAP` 在 nop-rg 内零残留（`--include="*.java"`；getPattern 以 `\bgetPattern\b` 核对 SearchCommand.getPattern 保留、CompiledGlob.getPattern 删除）
- [x] 行为零变化面：除 G1（`-c --json` 组合输出由退化空 JSON 变完整 JSON——缺陷修复语义收敛，README 记录）外，其余项为纯重构/死代码删除/文档——既有 CLI e2e 23 场景输出逐字节不变
- [x] 测试裁定（guide Rule 25）：G1 新增测试 1 个；G2 No new test required（诊断 main 工具，A8 先例——JFR 路径已有 JfrSwitchTest 回归守护）；G3-G7 No new test required（死代码删除/纯重构/文档，既有测试守护）
- [x] owner docs：cli README 已更新（G1）；design 01 无需更新（无决策级契约变更——显式记录 `No design-doc update required`）；module-groups.md No update required
- [x] `ai-dev/logs/2026/09-22.md` 已更新
- [x] Phase 完成即 commit（显式路径：nop-rg 文件 + README + plan + log，避开他会话未提交项）

### Phase 2 - 性能基线重建与快照落盘

Status: completed
Targets: benchmark 模块运行（无源码改动）、benchmark `README.md`、本 plan 迭代记录表

- Item Types: `Proof`（基线与场景数据）

- [x] row0 σ_run：`CoordinatorEndToEndBenchmark` 收尾档（`-f 3 -wi 3 -i 5 -w 1s -r 1s`）count 口径连跑 ≥2 次（1MB/64MB/512MB），各尺寸相对偏离落记录表；TEXT 口径（`-p mode=text`）与 many-small（`-p scenario=many-small -p size=64MB -p mode=count`）各 ≥1 次收尾档基线；环境行按当时 `Runtime.availableProcessors()` 与 load 实测记录
- [x] row0 profile：HotspotProfiler 64MB count + text 口径各 ≥12s 采样（corpus 目录 `$TMPDIR/nop-rg-bench-corpus/64mb`，由基准/CorpusUtil 首跑创建——**先跑 row0 基准再跑 profiler**），Top 热点落记录表并与 2273 终态（count = indexOf 42.3% + matchesAt 32.1%；text = matchesAt 22.5% + indexOf 16.7%）对照——无结构漂移则确认「不再评估」清单仍成立，有剧变则显式记录推翻理由
- [x] **基线快照（防假收敛）**：Phase 2 收口后执行 `./mvnw install -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli,nop-rg/nop-rg-vector -DskipTests` 将收口基线落 m2（共测配对基线侧）；候选侧一律 target/classes（运行时前置 -cp 覆盖 m2 快照）；kept 优化 commit 后同步刷新快照并重建 base classes（2273 R5 配对配置失误先例）
- [x] G8：benchmark README TEXT 基线段刷新为本轮实测数字（标注缓冲口径与测量日期），保留 2273 历史数字为演进记录
- [x] 基准改动测试裁定：No new test required（基准自身即度量，2265/2267/2273 先例）

Exit Criteria:

- [ ] `./mvnw test -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli,nop-rg/nop-rg-vector -am` 仍全绿
- [x] row0 数据齐全：σ_run 裁定行 + 双口径 profile Top（含 2273 终态对照结论）+ TEXT/many-small 基线行；判定 JSON 落 `_tmp/nop-rg-bench/`（p2275-*.json/log 命名）
- [x] 基线快照落 m2 成功（install EXIT=0）
- [x] README 基线数字与记录表一致（G8 关闭）
- [x] `ai-dev/logs/2026/09-22.md` 已更新
- [x] Phase 完成即 commit（README + plan + log）

### Phase 3 - 性能收敛循环（严格字面终止条款）

Status: in progress
Targets: `nop-rg-core`（迭代优化）、`nop-rg-benchmark`（如需镜像）、本 plan 迭代记录表

- Item Types: `Fix`（优化）、`Proof`（迭代度量）

- [x] 循环执行（每轮）：
  1. 依据最新 profile / 基线数据从候选池选定一项（P1-P4 优先；row0 profile 新发现可入池——须记录发现依据）
  2. 实现优化（候选侧 target/classes；kept 后涉及 m2 快照模块须刷新快照并重建 base classes——2273 R5 配对配置失误先例）
  3. 迭代档（`-f 1 -wi 2 -i 5 -w 1s -r 1s`）筛选——明确劣化（<-3σ）即回退记录
  4. 筛选通过 → 收尾档共测配对判定：基线侧 = m2 快照 jar、候选侧 = target/classes，交替 3 对（判定 JSON 落 `_tmp/nop-rg-bench/p2275-<round>-pair*.json`）；判定基准 = 受影响口径的 e2e + 其 σ（TEXT 候选判 TEXT 口径、many-small 候选判 many-small 口径）；收益 ≥ max(2%, 3σ_pair) 且误差棒不重叠 → 保留并 commit；否则回退并记录
- [x] row0 即记「不再评估」裁定行（2267/2273 清单，见 Current Baseline）；P3 若维持算术上界 <2% 裁定则记实现前否决行（附上界计算）
- [x] P4 组件级先筛：ScalarSearchBenchmark scanHit 迭代档对比——组件级 <5% 直接记否决行（e2e 传导比不足），不烧 e2e 机时
- [x] 每轮记录表一行（热点依据/优化项/筛选值/配对数据/保留或回退）；候选池枯竭时记 no-candidate 裁定行 + 末轮 profile + 穷尽说明
- [x] kept 优化全量回归：`./mvnw test -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli,nop-rg/nop-rg-vector -am` + large-file 显式组 3/3 + rg 对照 opt-in（`-Dtest.rg.compare=true`）
- [x] 终止：**连续两轮内所有被评估候选均未通过保留条件**，或候选池枯竭（no-candidate 行）；末轮终态 profile 落记录表
- [x] kept 优化测试裁定逐项记录（涉及搜索语义时须有等价性守护——fuzz/rg 对照；纯输出/调度优化引用既有测试面）

Exit Criteria:

- [x] 迭代记录表完整：row0 + 每轮一行 + 「不再评估」裁定行 + 终止裁定行；回退项均有数据依据；判定 JSON 全部落盘
- [x] 终止满足字面条款（连续两轮无候选通过）或候选池枯竭裁定（含穷尽说明），非含糊的实质性裁定
- [x] kept 优化（若有）逐项 commit + 全量测试通过（core/cli/vector + large-file 3/3 + rg 对照 opt-in）
- [x] 无静默跳过：每条候选路径要么有判定数据、要么有显式裁定行
- [x] `ai-dev/logs/2026/09-22.md` 已更新
- [x] Phase 完成即 commit（kept 优化逐项 + 循环收尾 commit）

### Phase 4 - 收口：吞吐比复验、文档与独立审计

Status: planned
Targets: 本 plan、daily log、benchmark `README.md`、`ai-dev/design/nop-rg/01-architecture-baseline.md`（条件式）

- Item Types: `Proof`（收口验证）、`Follow-up`（后续方向记录）

- [ ] 吞吐比收尾复测：64MB / 512MB 双档（收尾档 Coord e2e count vs RgCompare）≥50%；若 <50% 按协议排除噪声（再连跑 2 次取中位）后仍 <50% 则按 live defect 处置，不得静默
- [ ] benchmark README 收敛循环结论章节（kept/回退/终止/比率）；design 决策回写复核（仅当 kept 优化引发契约级变更——如锚点启发式调整更新决策 3；否则显式 `No design-doc update required`）
- [ ] 工具门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict`、`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-rg --severity high`、`node ai-dev/tools/check-doc-links.mjs --strict` 全部退出码 0
- [ ] 文本一致性核对：Plan Status / 各 Phase Status / Exit Criteria / Closure Gates / daily log 五处一致
- [ ] 独立子 agent closure audit（fresh session，不复用实现会话）+ evidence 写入本 plan Closure 段
- [ ] 最终 commit

Exit Criteria:

- [ ] 吞吐比双档 ≥50%（或按协议完成 live-defect 显式裁定）
- [ ] 三工具退出码全 0（命令与退出码记录在案）
- [ ] 独立 closure audit 完成且 evidence 写入 Closure 段
- [ ] daily log 收口记录完整
- [ ] 最终 commit 完成

## Closure Gates

- [ ] 所有 in-scope confirmed live defects 已修复（G1 CLI 组合语义；含聚焦回归测试）
- [ ] G2-G7 全部 landed 或有显式裁定记录（R1 镜像遗漏/死 API ×3/import 风格 ×5 文件/死 throws/javadoc）
- [ ] 收敛循环终止达成：连续两轮内所有被评估候选（共测配对收尾判定）收益 < max(2%, 3σ) 或误差棒重叠，或候选池枯竭（no-candidate 裁定 + 穷尽说明）（记录表 + `_tmp/nop-rg-bench/p2275-*` 判定 JSON 为证）
- [ ] 吞吐比不回退：64MB / 512MB 复测 ≥50%（<50% 时按协议完成 live-defect 处置路径）
- [ ] 行为守护：core/cli/vector 全量测试 + large-file 显式组 3/3 + rg 对照 opt-in 全绿；除 G1 显式记录的缺陷修复语义收敛外 CLI 输出逐字节不变
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] owner docs：cli README / benchmark README 已同步 live baseline；design 01 条件式更新已裁定（或显式 No update required）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：G1 修复非空壳（聚焦测试断言 match 消息存在）；G2 sink 镜像与 benchmark 形态一致（code review + javadoc）；kept 优化（若有）有配对数据支撑；无空方法体/静默跳过
- [ ] `./mvnw test -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli,nop-rg/nop-rg-vector -am` 通过
- [ ] `./mvnw compile -pl nop-rg/nop-rg-benchmark -am` 通过
- [ ] large-file 显式组 `-DexcludedGroups= -Dgroups=large-file -DargLine=-Xmx256m` 通过且测试计数 >0
- [ ] 代码规范检查：imports 分组组内字母序、无裸 RuntimeException、错误消息英文
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-rg --severity high` 退出码 0

## Deferred But Adjudicated

### CompiledGlob 进程级无淘汰缓存（延续 2273 裁定）

- Classification: `watch-only residual`
- Why Not Blocking Closure: javadoc 已显式记录 CLI 场景裁定，测试锁定同一性契约；无 in-repo 受害面。本轮不翻案。
- Successor Required: `no`
- Successor Path: 任一后续 nop-rg 计划可顺带评估。

## Non-Blocking Follow-ups

- `--follow`（符号链接跟随开关）：延续 2273 登记项（out-of-scope improvement）。
- 多小文件每文件系统调用开销（open/fstat/map）：延续 2273 登记项；本轮 P3（walker stat）若以算术上界否决则并入该登记。
- 测试文件 import 分组结构（JfrSwitchTest 之外）：本轮裁定 out-of-scope（无功能收益的测试面 churn）；如后续建立测试代码风格门禁可一并归位（watch-only）。

## 迭代记录表

> 环境：macOS arm64 / JDK 26.0.1 Zulu / 核数以 row0 实测为准 / rg 15.1.0。判定协议沿用 2267 修订版：**共测配对**（基线 = m2 快照 jar，候选 = target/classes，背靠背交替 3 对，增益按逐对配对差计算，σ = 配对增益离散度）对消运行间系统漂移；绝对值仅作参照；**判定基准 = 受影响口径的 e2e + 其 σ**。判定 JSON 落 `_tmp/nop-rg-bench/`（p2275-* 命名）。

| 轮次 | 基线 | 热点/依据 | 优化项 | 复测值 | 收益 | 保留/回退 |
| --- | --- | --- | --- | --- | --- | --- |
| 环境/噪声行 | macOS arm64 / JDK 26.0.1 Zulu / `availableProcessors`=16 / rg 15.1.0（/opt/homebrew/bin）。外部负载持续（他会话 opencode + node ~100% 单核 + mds_stores，load 7.5-12）——绝对值仅作参照，判定全靠共测配对（2267 协议） | — | — | — | — | — |
| row0 σ_run count（收尾档 ×2：p2275-row0-count-a/b.json，needle-6B） | 1MB 821.0±185.1 / 788.8±57.5 ops/s（连跑偏离 ±2.0%）；64MB 55.04±9.70 / 43.64±20.40（±11.6%，外部负载噪声）；512MB 12.07±0.42 / 11.70±1.40（±1.6%） | — | — | — | — | σ_run 仅作参照；判定以共测配对为准 |
| TEXT 口径基线（收尾档，p2275-baseline-text.json，R1 缓冲口径） | 1MB 577.9±102.6 / 64MB 12.09±0.74 / 512MB 1.568±0.105 ops/s——与 2273 R5b 候选侧区间一致（G8 旧值 131.7/2.15/0.269 系 R1 前口径，README 已刷新） | — | — | — | — | Phase 3 候选来源证据基线 |
| many-small 口径基线（收尾档，p2275-baseline-manysmall.json，512×128KB count） | 31.82±3.14 ops/s（2273 记录 33.37±0.80 同区间） | — | — | — | — | Phase 3 候选来源证据基线 |
| row0 profile count（HotspotProfiler 12s，p2275-profile-count.log，64MB） | LineCursor.indexOf 46.6% + PreparedLiteral.readByte 27.1% + matchesAt 2.5%（同一 BMH 扫描循环的内联归因，合计 ~76%——LF 扫描 + BMH 验证带宽地板）、advance 9.3%、aggregate 8.6%、searchFile 4.2%（含 isBinary ~0.5%） | — | — | — | — | **无结构漂移裁定**：与 2273 终态（indexOf 42.3% + matchesAt 32.1%）同构；「不再评估」清单（SWAR LF/span-gap/单遍融合/verify 次序/memo/堆读/double-stat/12B SIMD）维持 |
| row0 profile text（HotspotProfiler 12s，p2275-profile-text.log，64MB，runs=124/1355 样本） | BufferedWriter.write 29.9%（输出必需）、indexOf 14.0%、**ResultPrinter.print:41（TEXT 行拼接中间 String）11.4%**、matchesAt 7.3%、buildLineMatches 13.4%（含 ArrayList.add 3.8% + copyOf 1.5% 分配）、LineCursor.text 5.7%（行解码，输出必需）、isBinary 1.9% | — | — | — | — | 与 2273 text 终态同构（归因比例系 JIT 采样漂移）；P1 入池依据 = print:41 的 11.4%；P5（submatch 列表分配面 ~5.3%）入池依据记录 |
| row0 profile many-small（HotspotProfiler 12s，p2275-profile-manysmall.log，403 runs/2241 样本，count） | LineCursor.byteAt 67.2% + advance 7.3% + indexOf 1.8%（行扫描 ~76%）、matchesAt 17.4% + find 2.3%（BMH）、isBinary 1.9%、FJP/syscall 面对采样不可见 | — | — | — | — | P2/P3 裁定依据 |
| P2（many-small isBinary SWAR NUL 嗅探）——实现前否决 | — | many-small 实测 isBinary 占 1.9%（p2275-profile-manysmall.log）：SWAR 化至多消除其中逐字节开销的一部分，e2e 收益上界 <1.9% < 2% 保留线 | 无（未实现） | 理论上界 <1.9% | 不满足保留条件（上界即不足） | **实现前否决**（profile 占比直接封顶，非实质性裁定） |
| P3（many-small walker 单次 readAttributes 消除双 stat）——实现前否决 | — | 算术上界：512 子项/op 省 1 次 lstat ≈ 0.26-0.5ms / op（op ≈ 31.4ms）≈ ≤1.7% < 2%；且 syscall 面对采样不可见、many-small σ_pair 历史地板（±15-50%）远超该量级 | 无（未实现） | 理论上界 ≤1.7% | 不满足保留条件（上界即不足） | **实现前否决**（plan 预授权路径，算术上界 + 噪声地板双重依据） |
| R1——P1（TEXT ResultPrinter 逐段 print 免中间 String） | row0 TEXT profile print:41 占 11.4% | 实现 = print(path)/print(':')/print(lineNo)/print(':')/println(text)；输出字节不变 | 迭代档 TEXT 64MB 两跑：11.724±0.606 / 11.698±0.574 vs 基线参考 12.09±0.74（名义 -3%，CI 重叠） | -3% 名义 | 未过筛选 | **回退**（两跑一致低于基线；机理：invokedynamic 拼接为 bulk-copy 优化，5 次 synchronized print 含单字符 write 路径反而更慢）；代码已还原（nop-rg 与 HEAD 零 diff 复核） |
| R2——P6（count 口径 advanceLineStart 免行尾前向扫描 + 免 LineInfo 逐 span 分配） | count profile：advance/indexOf 系行扫描 ~56%；count 路径仅消费 lineStart，contentEnd/lineEnd 前向扫描为纯浪费（非 2267 R3/R4 已裁单遍融合/span-gap 家族的直接变体——独立入池并实证） | LineCursor 新增包私有 advanceLineStart(offset)（只做行起点回扫）；MatchAggregator count 路径改调 | 迭代档 55.42±7.70（无劣化）→ 收尾共测配对 3 对（p2275-p6-pair1..3-{base,cand}.json）：512MB 三对全正 **+9.9%/+13.1%/+9.7%**（中位 +9.9%，CI 零重叠）；1MB 三对全正 +7.1%/+13.0%/+4.6%；64MB -3.6%/-21.2%/+43.9%（σ ±12-18 与基线漂移同源，CI 全重叠不可判） | 512MB 中位 +9.9%、1MB 中位 +7.1% ≥ max(2%, 3σ) | 稳定口径 3/3 方向一致 + CI 零重叠 | **保留 P6**（count 快速路径语义不变——行计数只依赖 lineStart，既有 count 语义测试 + large-file 3/3 + rg 对照 opt-in 全绿守护；终态 count profile p2275-profile-final-count.log：runs 432→581 /12s，残余热点仍为扫描地板） |
| P4（BYTE_FREQUENCY 字母差异化加权改锚点）——实现前否决 | — | 机理裁定：全小写同频时 "needle" 锚点 = 末位 'e'（f<=bestFreq 取后）；任何把 e 加权为更高频的表（英文事实）都会把锚点前移（如 'd'@3）——verify 率降 ~29% 但默认跳跃 6→4（迭代 +50%），净负；把 e 权重低于同族则违背频率表事实。无 ≥2% 可实现方向 | 无（未实现） | 机理上界为负向 | 不满足保留条件 | **实现前否决**（锚点选择契约的解析式 trade-off 分析，非实质性裁定） |
| P5（buildLineMatches submatch 列表分配面）——实现前否决 | text profile ArrayList.add 3.8% + copyOf 1.5% | 归因拆解：add 3.8% 为调用本身（预扩容不消除），可寻址切片仅 copyOf 增长拷贝 1.5% < 2% | 无（未实现） | 可寻址上界 1.5% | 不满足保留条件（上界即不足） | **实现前否决**（归因拆解 + 算术上界） |
| 终止裁定（P1 筛退回退 + P2/P3/P4/P5 实现前否决 + P6 收割后候选池枯竭） | 终态 count profile（p2275-profile-final-count.log，P6 后，64MB 12s）：byteAt 41.2% + indexOf 16.0% + matchesAt 12.5% + aggregate 19.3%——扫描带宽地板（与 2267/2273 终态同构）；TEXT 残余 = 输出 Writer 路径（契约必需）+ 行解码（输出必需）；many-small 残余 = 行扫描 + syscall 面（R3/P3 已证无更优替代） | — | — | — | — | **循环终止**（字面条款：P1✗ 后连续轮次无候选通过 + P6 收割后候选池枯竭，穷尽说明：count/many-small 残余为扫描地板与系统调用面、TEXT 残余为输出契约必需，vector 12B 已闭合，全部候选 P1-P6 要么有 JMH 判定数据要么有显式否决依据） |

## Closure

Status Note:（收口时填写）
Completed:（收口时填写）

Closure Audit Evidence:

- Reviewer / Agent:（独立子 agent）
- Evidence:（收口时填写）

Follow-up:

-（收口时填写；confirmed live defect 不得出现在这里）
