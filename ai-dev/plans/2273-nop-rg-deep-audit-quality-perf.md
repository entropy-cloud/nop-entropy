# 2273 nop-rg 第二轮深度审计 —— 质量修复 + 性能收敛复验

> Plan Status: active
> Last Reviewed: 2026-09-21
> Source: 用户指令（对 nop-rg 深度审计：可读性/长期可维护性/性能，JMH/JFR 反复优化直到无 ≥2% 收益）；live repo 审计（2026-09-21，本 plan Current Baseline 全部经 read/grep/实测实证）；plans 2267/2268（前序性能收敛与质量改进，均 completed）
> Related: Plan 2262-2268（Wave 1-4 + 性能收敛 + 代码质量，均 completed）；2267 终态 = 性能基线守护参照；2267 Non-Blocking Follow-up「512MB 吞吐比安静环境复测」由本计划 Phase 4 清偿
> Draft Review: 第一轮独立子 agent 对抗性审查（agent_65e271fb-045e-4934-bf11-86669a216344）：1 Blocker + 2 Major + 10 Minor。B1（GitIgnoreFile 符号链接递归——默认路径崩溃点在 walker 之前的 nop-core 侧，已复核源码证实）折入 A1 两点修复；M1（A2 测试裁定矛盾）、M2（共测配对 stale-jar 机制）与 m1-m10 全部折入；第二轮复审（同 agent）判定可执行——第一轮发现全部正确折入且修订未引入新 Blocker/Major，N1（Purpose 终止措辞）/N2（GitIgnoreFile 下游消费者影响面记录）/N3（候选侧 -cp 覆盖语义）已折入，编号因 2269/2270-2272 已被占用调整为 2273

## Purpose

在 2267（性能收敛循环）与 2268（F1-F9 质量清理）已收口的 live 代码上执行第二轮深度审计：(1) 修复本轮新发现的 live defect 与可维护性问题（含 nop-core 侧符号链接递归崩溃）；(2) 在 2267 未覆盖的口径（TEXT 文本输出、多小文件）上重建性能基线并重新执行严格收敛循环——**连续两轮内所有被评估候选优化均未通过保留条件（收益 < max(2%, 3σ) 或误差棒重叠）方可终止，或候选池枯竭（no-candidate 裁定 + 穷尽说明）**；(3) 收口复验吞吐比不回退（≥50%）。

## Current Baseline

- 提交基线 `2f0f61abca`。工作区未提交改动均为本计划无关项（他会话的 `ai-dev/logs/2026/09-20.md` 修改、`ai-dev/analysis/2026-09/2026-09-20c-*`、`ai-dev/plans/nop-lint/01-module-skeleton.md`、仓库根游离空文件 `-k2`）——不触碰、不提交。plans 2262-2268 全部 completed，各含独立 closure audit evidence。
- 测试基线（2026-09-21 实测 `./mvnw test -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli,nop-rg/nop-rg-vector -am` EXIT=0）：core 7 类 54 run + 1 skip（rg 门控 `GlobMatcherTest.testCompareAgainstSystemRg`，`-Dtest.rg.compare=true` 激活）、cli 26（JfrSwitch 3 + NopRgMain 9 + RgComparison 9 + VectorMode 5；RgComparison 默认跑、rg 不在 PATH 时 assumeTrue 跳过）、vector 10。large-file 显式组 3（`-DexcludedGroups= -Dgroups=large-file -DargLine=-Xmx256m`，2267 收口通过）。
- 性能守护参照（2267 终态，勿回退）：吞吐比 64MB 82.3%、512MB 六次中位 49.05%（当时含外部负载，已显式裁定为环境噪声）；count 口径 e2e 为带宽/缓存行为受限——SWAR LF 扫描（R2，两次否决）、span-gap 行计数（R3）、单遍融合扫描（R4）均经共测配对实证 <2%。终态 profile：indexOf 48.9%、matchesAt 25.3%、advance 9.2%、aggregate 8.9%。
- 2267 终止协议先例：共测配对——**基线侧 = 已 install 到本地 m2 的快照 jar，候选侧 = target/classes**，交替 3 对对消机器漂移；保留条件 = 收益 ≥ max(2%, 3×σ_pair) 且 JMH 误差棒不重叠；判定 JSON 落 `_tmp/nop-rg-bench/`。benchmark README 有 stale-jar 运维提示（core 改动后不 install 则 fork JVM 读旧快照——2268 Phase 4 实际踩坑 ClassNotFoundException）。
- 基准设施就绪：5 个 JMH 基准类（Scalar/Glob/CoordinatorEndToEnd/RgCompare/VectorCompare）+ HotspotProfiler（JFR 热点聚合器，非 JMH 基准）；运行方式 = cwd `nop-rg/nop-rg-benchmark/` 下 `java -cp target/classes:$(cat target/cp.txt) org.openjdk.jmh.Main`（禁止 exec:java），可用 `-p size=64MB` 钉参数；VECTOR 需 `--add-modules jdk.incubator.vector`；corpus 固定种子落 `$TMPDIR/nop-rg-bench-corpus/`（复用键 = path+size，场景隔离靠子目录）。环境：macOS arm64 / JDK 26.0.1 Zulu / rg 15.1.0。
- **本轮审计新发现（A1-A8，全部经 read/grep 实证；B1 系第一轮 review 深挖补充）**：
  - **A1（live defect，默认路径崩溃，两点修复）**：
    - **A1-walk**：`ParallelFileWalker.ScanAction` 无符号链接处理——`Files.isDirectory(child)` 默认跟随链接，目录符号链接成环（a/b→a）时无限递归 → StackOverflowError；与 rg 默认（不跟随 symlink）偏离。全仓 grep `symlink|SymbolicLink` 零处理零测试。悬空符号链接现状 = `isDirectory`/`isRegularFile` 均 false 静默跳过（顺带钉死语义）。
    - **A1-ignore（崩溃点更早，nop-core）**：默认路径（respectGitignore=true）在 walk 开始前 `GitIgnoreFile.create(root)` → `loadSubdirectoryRules`（`nop-kernel/nop-core/src/main/java/io/nop/core/git/GitIgnoreFile.java`）以 `child.isDirectory()`（`FileResource.isDirectory` → `file.isDirectory()`，跟随链接）递归加载子目录规则——目录环在 walker 自身遍历之前即无限递归。仅修 walker 不修 GitIgnoreFile = 默认路径假修复（第一轮 review B1，已复核源码证实）。nop-core 属 Protected Area（框架核心引擎）：本计划即 plan-first 载体，证据要求 = 设计说明（design 决策 2/4 更新）+ nop-core 回归测试。
  - **A2（live defect，资源不对称）**：`NopRgMain.call()` 中 `JfrSupport.startRecording` 抛异常时（`--jfr` 路径非法等）外层 catch 直接返回 2，`CoreInitialization.destroy()` 不执行（`!noIgnore` 时 VFS 已初始化）。
  - **A3（死公共 API）**：`FileMatches.isCountOnly()` 全仓零调用（grep 实证：仅定义处命中；`isTruncated` 有测试消费者须保留）；`io.nop.rg.*` 类型无 nop-rg 目录之外的引用（全仓 grep 实证，删除安全）。
  - **A4（重复实现）**：行文本解码两份实现——`LineCursor.text(LineInfo)`（全仓零调用的公共 API）与 `MatchAggregator.decode`（行内容路径）逐语义相同；子匹配解码仍需 `decode`。
  - **A5（魔法下标）**：coordinator 内命中区间全程 `List<long[]>`（`span[0]`/`span[1]` 魔法下标），三处生产（literalSpans/regexSpans/searchFileChunked）+ MatchAggregator 消费；`MatchAggregator.aggregate` 为 public 但唯一消费者 `SearchCoordinator` 同包；spans 全程不出 coordinator 包（FileMatches/LineMatch/Submatch 保持 public，cli 消费面不变）。
  - **A6（风格）**：import 组内乱序——`SearchCoordinator`（java.util.ServiceLoader/ServiceConfigurationError/Iterator 置于 java.util.concurrent 之后）、`ParallelFileWalker`（java.security 置于 java.nio 前）、`JsonOutput`/`NopRgMain`（io.nop.rg.core.coordinator 组内乱序）、benchmark `CoordinatorEndToEndBenchmark`/`VectorCompareBenchmark`（org.openjdk.jmh.annotations 组内乱序）。
  - **A7（契约文档缺失）**：`MappedFileReader.getSegment()` 空文件返回 null（构造器 `size == 0 ? null : map(...)`），javadoc 未记录 null 语义；消费者靠 `mappedSize == 0` 短路守卫。
  - **A8（健壮性，诊断工具）**：`HotspotProfiler.main` 中 search 抛异常时 JFR recording 与 CoreInitialization 不关闭（仅正常路径 close/destroy）。
- **已裁定保留项（本轮复核不翻案）**：`CompiledGlob` 进程级无淘汰缓存——javadoc 已记录 CLI 场景裁定且测试锁定同一性（`GlobMatcherTest` assertSame），改有界缓存属过度设计 → watch-only residual；`ScalarByteSearcher`/`VectorByteSearcher` 策略层为 design 决策 3/5 契约与 vector 测试床（2268 Non-Goal）；每 search 新建 ForkJoinPool（2267 裁定：产品为每进程单次搜索，复用仅惠及基准自身）。

## Goals

- **A1 两点修复（含 Protected Area 证据）**：
  - walker 跳过符号链接目录（不递归，防环；对齐 rg 默认不跟随目录链接）；文件符号链接行为保持现状（可搜索）、悬空链接保持现状（静默跳过），两者在 cli README「与 rg 的已知差异」显式记录；
  - `GitIgnoreFile.loadSubdirectoryRules` 跳过符号链接子目录（不递归加载其规则）——设计说明落 design 决策 2/4，回归测试落 `nop-kernel/nop-core/src/test/java/io/nop/core/git/GitIgnoreFileTest.java`（平台不支持 symlink 时 assumeTrue 跳过）；
  - 新增 walker 回归测试（默认路径 `of()` 目录环有限时间终止 + 文件符号链接/悬空链接行为防漂移）。
- **A2 修复**：`NopRgMain.call()` 初始化/销毁对称——JFR 录制启动失败路径同样执行 `CoreInitialization.destroy()`（正常路径行为等价）。
- **A3/A4/A5 清理**：删除 `isCountOnly()`；行文本解码归一到 `LineCursor.text()`；`List<long[]>` spans 改 coordinator 包私有 `MatchSpan` record 并将 `MatchAggregator.aggregate` 收窄包私有（公共 API 面净缩小，仓内消费者全部同包）。
- **A6/A7/A8**：core/cli/benchmark import 顺序统一；`MappedFileReader` null 契约补 javadoc；HotspotProfiler 异常路径资源对称释放。
- **性能基线重建 + 两个新口径**：row0（σ_run + HotspotProfiler 热点 + 对照 2267 终态）；e2e 基准新增 **TEXT 口径**（includeLineText=true + CLI 等价输出 sink）与**多小文件场景**（512 × 128KB，暴露每文件开销）；`VectorCompareBenchmark` 补 12B 探测场景（2267 R1 留口：8-15B 段保守归标量，未测）。
- **严格收敛循环（用户硬性要求）**：在受影响口径上以 JMH 度量、JFR 定位，逐候选「实现 → 迭代档筛选 → 共测配对收尾判定」；保留条件 = 收益 ≥ max(2%, 3σ) 且误差棒不重叠；**终止条件（字面）= 连续两轮内所有被评估候选均未通过保留条件**；候选池枯竭亦可终止（须记 no-candidate 裁定行 + 末轮 profile）。kept 优化逐项 commit。
- **吞吐比不回退**：收口复测 64MB / 512MB 双档 ≥50%；同时清偿 2267 的 512MB 安静环境复测 follow-up。
- 每 Phase 完成即 commit；daily log 逐 Phase 更新（对应日期日志文件）。

## Non-Goals

- 不新增 `--follow`（符号链接跟随开关）等新功能——A1 仅做防崩溃的最小行为收敛；文件符号链接/悬空链接语义保持现状。
- 不做 rg 100% 选项兼容、不做 FM-Index、不做跨平台调优（2267 Non-Goal 延续）。
- 不翻案已裁定项：`CompiledGlob` 缓存、策略层结构、per-search ForkJoinPool（见 Current Baseline 末条）。
- 不重复评估 2267 已两次实证 <2% 的方向（SWAR LF 扫描、span-gap 行计数、单遍融合扫描、matchesAt verify 次序）——循环 row0 记裁定行，不再烧机时；除非 row0 profile 显示热点结构剧变（须显式记录推翻理由）。
- 不回写历史计划文本（guide Rule 20）。
- 不动 2267 已确立的 count 口径 e2e 判定基准语义（既有 @Param 组合不变，仅新增参数值/场景）。
- 不触碰工作区中他会话的未提交改动（见 Current Baseline 首条）。

## Scope

### In Scope

- `nop-kernel/nop-core`：`io/nop/core/git/GitIgnoreFile.java`（A1-ignore 符号链接防护）+ `GitIgnoreFileTest`（回归测试）——Protected Area plan-first：本计划 + design 决策 2/4 更新 + 回归测试即证据链。
- `nop-rg/nop-rg-core`：walk（A1-walk + 测试）、coordinator（A3/A4/A5）、io（A7）、search/coordinator/glob 包 import（A6）。
- `nop-rg/nop-rg-cli`：`NopRgMain`（A2）、`JsonOutput` import（A6）、README 已知差异（A1 记录）。
- `nop-rg/nop-rg-benchmark`：`CoordinatorEndToEndBenchmark`（TEXT 口径 + 多小文件场景）、`VectorCompareBenchmark`（12B 场景）、import 顺序（A6）、`HotspotProfiler`（A8）、README。
- owner docs：`ai-dev/design/nop-rg/01-architecture-baseline.md`（决策 2/4 symlink 语义）、`docs-for-ai/01-repo-map/module-groups.md`（walker 描述）、daily log（对应日期）、本 plan 迭代记录表。
- 判定证据：`_tmp/nop-rg-bench/` 下 JMH JSON 与 profile 落盘。

### Out Of Scope

- `docs-for-ai/` 平台使用文档正文（nop-rg 非平台用户功能；仅 module-groups.md 行级同步）。
- nop-rg 与 nop-core GitIgnoreFile 之外的模块；roadmap 状态变更。
- 历史计划文本（2262-2268）。

## Execution Plan

### Phase 1 - 审计修复：符号链接崩溃缺陷与可维护性清理

Status: completed
Targets: `nop-kernel/nop-core/io/nop/core/git/GitIgnoreFile.java` + `GitIgnoreFileTest.java`、`nop-rg-core/walk/ParallelFileWalker.java` + `ParallelFileWalkerTest.java`、`coordinator/{FileMatches,LineCursor,MatchAggregator,SearchCoordinator}.java`、`io/MappedFileReader.java`、`cli/NopRgMain.java`、`cli/JsonOutput.java`、benchmark `HotspotProfiler.java` + 两基准 import（A6）

- Item Types: `Fix`（A1-A8）

- [x] A1-ignore：`GitIgnoreFile.loadSubdirectoryRules` 递归前跳过符号链接子目录（不加载其规则、不递归）——nop-core Protected Area 修复，行为仅收窄「符号链接目录不再递归加载规则」，匹配语义不变
- [x] A1-ignore 测试：`GitIgnoreFileTest` 新增目录符号链接环用例——`GitIgnoreFile.create` 在有限时间返回（配 @Timeout 防挂死），平台不支持 symlink 时 assumeTrue 跳过
- [x] A1-walk：`ParallelFileWalker` 子项分类前置 `Files.isSymbolicLink` 判定——符号链接目录跳过不递归（防环）；文件符号链接行为不变（现状可搜索）；悬空链接行为不变（静默跳过）；类 javadoc 契约补 symlink 语义
- [x] A1-walk 测试：`ParallelFileWalkerTest` 新增 (a) 默认路径 `of()`（respectGitignore=true）下目录符号链接环 walk 在有限时间正常返回且结果不含链接目标重复文件；(b) 文件符号链接入结果（现状防漂移）+ 悬空链接跳过（现状钉死）；平台不支持 symlink 时 assumeTrue 跳过
- [x] A2：`NopRgMain.call()` 重构使 JFR 启动失败路径同样执行 `CoreInitialization.destroy()`（初始化/销毁对称；正常路径行为等价）
- [x] A3：删除 `FileMatches.isCountOnly()`（死公共 API，2268 F1 先例）
- [x] A4：`MatchAggregator` 行内容解码复用 `LineCursor.text()`（单一实现；`newLineMatch` 传 cursor 或等价接线）；`decode` 收窄为子匹配职责；`LineCursor.text` 转正为生产路径
- [x] A5：命中区间 `List<long[]>` → coordinator 包私有 `MatchSpan(long start, long end)` record；三处生产点 + `MatchAggregator.aggregate` 签名同步；`aggregate` 收窄包私有
- [x] A6：`SearchCoordinator`/`ParallelFileWalker`/`JsonOutput`/`NopRgMain`/`CoordinatorEndToEndBenchmark`/`VectorCompareBenchmark` import 分组组内字母序
- [x] A7：`MappedFileReader` 构造器与 `getSegment()` javadoc 补空文件 null 契约
- [x] A8：`HotspotProfiler.main` recording/CoreInitialization 异常路径对称释放（try/finally）

Exit Criteria:

- [x] `./mvnw test -pl nop-kernel/nop-core,nop-rg/nop-rg-core,nop-rg/nop-rg-cli,nop-rg/nop-rg-vector -am` 全绿（nop-core 含新增 GitIgnoreFile 环测试；core ≥54 run + 1 skip，cli 26，vector 10；新增 symlink 测试计入）
- [x] A1 行为验证：walker 与 GitIgnoreFile 的目录环测试在默认路径（respectGitignore=true）下通过（超时即失败）；rg 对照双保险通过——core 门控 glob 对照（`-Dtest.rg.compare=true`）+ cli `RgComparisonTest` 9 场景（rg 可用时默认跑）
- [x] benchmark 模块编译通过（`./mvnw compile -pl nop-rg/nop-rg-benchmark -am`，A8/A6 触及）
- [x] grep 复核：`isCountOnly` 全仓零残留；`List<long[]>` 在 coordinator 生产/消费签名零残留（均 `--include="*.java"`）
- [x] 行为零变化面：除 A1（符号链接目录：不再递归遍历/加载规则——防崩溃语义收敛，已在 README/design 记录）与 A2（失败路径清理）外，其余项为纯重构——既有 CLI e2e 23 场景守护输出逐字节不变
- [x] 测试裁定（guide Rule 25）：新增 = GitIgnoreFile 环 ×1 + walker symlink ×1 组；A2/A8 No new test required——失败路径外部不可触发（startRecording 失败需 classpath JFR 配置损坏；HotspotProfiler 为诊断 main 工具），守护 = JfrSwitchTest 3 场景回归 + code review；A3-A7 No new test required（死代码删除/纯重构/文档，既有测试守护）
- [x] owner docs：cli README「与 rg 的已知差异」补符号链接语义（目录不跟随/文件跟随/悬空跳过）；design 决策 2（GitIgnoreFile 复用契约收窄，含下游消费者影响面记录：nop-cli-core CliFileCommand、nop-ai-code-analyzer GitProject——收窄对二者同为防崩溃方向，不改签名）与决策 4（walker symlink 契约）更新；module-groups.md walker 描述同步
- [x] 对应日期 daily log 已更新
- [x] Phase 完成即 commit

### Phase 2 - 性能基线重建与新口径场景扩展

Status: planned
Targets: `CoordinatorEndToEndBenchmark.java`、`VectorCompareBenchmark.java`、benchmark `README.md`、本 plan 迭代记录表

- Item Types: `Proof`（基线与场景数据）

- [ ] row0 σ_run：`CoordinatorEndToEndBenchmark` 收尾档（`-f 3 -wi 3 -i 5 -w 1s -r 1s`）连跑 ≥2 次（1MB/64MB/512MB，count 口径），各尺寸相对偏离落记录表；环境行按当时 `Runtime.availableProcessors()` 实测记录
- [ ] row0 profile：HotspotProfiler 64MB count 口径 ≥12s 采样，Top 热点落记录表并与 2267 终态（indexOf 48.9% / matchesAt 25.3%）对照——无结构漂移则确认 2267 终态裁定（含「不再评估」清单）仍成立，有剧变则显式记录
- [ ] 新口径 A——TEXT e2e：`CoordinatorEndToEndBenchmark` 增加输出模式维度（count / text）；text = `includeLineText=true` + `ResultPrinter`（benchmark pom 已依赖 cli，已实证）输出至 CLI 等价 sink——PrintWriter(BufferedOutputStream(→/dev/null), autoflush=true) 复刻 `NopRgMain` stdout 形态（否则 autoflush 开销不可测）；记录 count/text 两口径基线
- [ ] 新口径 B——多小文件 e2e：新增 scenario 值（512 × 128KB = 64MB，固定种子），corpus 参数完全由 scenario 决定、独立子目录防复用污染；运行时以 `-p size=64MB` 钉定消除 size 维度重复 trial；记录基线
- [ ] SIMD 12B 探测：`VectorCompareBenchmark` 增加 sparse/dense-mid-12B 场景（12B 命中词常量，先例 MID_HIT_16B）——向量 ≥2% 胜出则转 Phase 3 e2e 口径判定（阈值 16→12 的证据链）；否则记录维持 16B 阈值裁定
- [ ] **基线快照（M2 防假收敛）**：Phase 2 收口后执行 `./mvnw install -pl nop-kernel/nop-core,nop-rg/nop-rg-core,nop-rg/nop-rg-vector,nop-rg/nop-rg-cli -DskipTests` 将收口基线落 m2（共测配对基线侧）；候选侧一律对应模块 target/classes（运行时前置 -cp 覆盖 m2 快照，2267 R2 机制）+ 重编译后刷新；README stale-jar 提示写进循环协议
- [ ] benchmark README 更新：新场景/新参数运行方式与基线数字
- [ ] 基准改动测试裁定：No new test required（基准自身即度量，2265/2267 先例）

Exit Criteria:

- [ ] `./mvnw compile -pl nop-rg/nop-rg-benchmark -am` 通过；`./mvnw test -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli,nop-rg/nop-rg-vector -am` 仍全绿
- [ ] row0 数据齐全：σ_run 裁定行 + 热点 Top（含 2267 终态对照结论）+ TEXT/多小文件/SIMD-12B 基线行；判定 JSON 落 `_tmp/nop-rg-bench/`
- [ ] 新场景 corpus 隔离验证：TEXT 复用同 corpus（同内容合法）、多小文件与 12B 各自独立子目录
- [ ] 基线快照落 m2 成功（install EXIT=0）
- [ ] 对应日期 daily log 已更新
- [ ] Phase 完成即 commit

### Phase 3 - 性能收敛循环（严格字面终止条款）

Status: planned
Targets: `nop-rg-core`（迭代优化）、`nop-rg-vector`（若 SIMD 候选成立）、本 plan 迭代记录表

- Item Types: `Fix`（优化）、`Proof`（迭代度量）

- [ ] 循环执行（每轮）：
  1. 依据最新 profile / 基线数据选定一项候选——候选来源须覆盖三类：(a) 2267 遗留热点（row0 复认或新证据推翻原裁定），(b) successor 场景（SIMD 12B e2e 判定，若 Phase 2 触发），(c) 新口径 profile 新发现（TEXT 输出路径、每文件开销面、LineCursor 行遍历等）
  2. 实现优化（候选侧 target/classes；涉及 m2 快照模块时重编译）
  3. 迭代档（`-f 1 -wi 2 -i 5 -w 1s -r 1s`）筛选——明确劣化（<-3σ）即回退记录
  4. 筛选通过 → 收尾档共测配对判定：**基线侧 = m2 快照 jar、候选侧 = target/classes，交替 3 对**（判定 JSON 落 `_tmp/nop-rg-bench/<round>.json`）；**判定基准 = 受影响口径的 e2e 基准 + 其 σ**（TEXT 候选判 TEXT 口径、多小文件候选判多小文件口径，不要求全口径配对——kept 后其余口径全量回归兜底）；收益 ≥ max(2%, 3σ_pair) 且 JMH 误差棒不重叠 → 保留并 commit（注明轮次与数据）；否则回退并记录
- [ ] row0 即记「不再评估」裁定行：SWAR LF 扫描 / span-gap 行计数 / 单遍融合扫描 / matchesAt verify 次序（2267 两次实证 <2%，避免重复烧机时）
- [ ] 每轮记录表一行（热点依据/优化项/筛选值/配对数据/保留或回退）；某轮无候选可实现时记 no-candidate 裁定行——**候选池枯竭亦可终止循环，须附末轮 profile 与候选池穷尽说明**（与「连续两轮配对未通过」并列的合法终止路径，防假收敛：禁止为凑终止制造稻草人候选）
- [ ] kept 优化全量回归：`./mvnw test -pl nop-kernel/nop-core,nop-rg/nop-rg-core,nop-rg/nop-rg-cli,nop-rg/nop-rg-vector -am` + large-file 显式组 3/3 + rg 对照 opt-in
- [ ] 终止：**连续两轮内所有被评估候选均未通过保留条件**，或候选池枯竭（no-candidate 行）；末轮终态 profile 落记录表
- [ ] 新增/修改行为的测试裁定逐项记录（kept 优化涉及搜索语义时须有等价性守护——fuzz/rg 对照；纯调度/缓冲优化引用既有测试面）

Exit Criteria:

- [ ] 迭代记录表完整：row0 + 每轮一行 + 「不再评估」裁定行 + 终止裁定行；回退项均有数据依据；判定 JSON 全部落盘 `_tmp/nop-rg-bench/`
- [ ] 终止满足字面条款（连续两轮无候选通过）或候选池枯竭裁定（含穷尽说明），非含糊的实质性裁定
- [ ] kept 优化（若有）逐项 commit + 全量测试通过（core/cli/vector + large-file 3/3 + rg 对照 opt-in）
- [ ] 无静默跳过：每条候选路径要么有判定数据、要么有显式裁定行
- [ ] 对应日期 daily log 已更新
- [ ] Phase 完成即 commit（kept 优化逐项 + 循环收尾 commit）

### Phase 4 - 收口：吞吐比复验、文档与独立审计

Status: planned
Targets: 本 plan、daily log（对应日期）、benchmark `README.md`、`ai-dev/design/nop-rg/01-architecture-baseline.md`、`docs-for-ai/01-repo-map/module-groups.md`

- Item Types: `Proof`（收口验证）、`Follow-up`（后续方向记录）

- [ ] 吞吐比收尾复测：64MB / 512MB 双档（收尾档 Coord e2e vs RgCompare）≥50%；安静环境下 512MB 复测并记录——同时清偿 2267 watch-only follow-up「512MB 吞吐比安静环境复测」；若 <50% 按协议排除噪声（再连跑 2 次取中位）后仍 <50% 则按 live defect 处置，不得静默
- [ ] benchmark README 收敛循环结论章节（kept/回退/终止/比率）；design 回写裁定（若 kept 优化引发契约级变更则更新决策 3/4/5；A1 的 design 更新已在 Phase 1 完成——此处为终态复核）
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

- [ ] 所有 in-scope confirmed live defects 已修复（A1 两点：walker + GitIgnoreFile，含默认路径回归证据；A2 初始化/销毁不对称）
- [ ] A3-A8 全部 landed 或有显式裁定记录（死 API/重复实现/魔法下标/import/null 契约/资源泄漏）
- [ ] 收敛循环终止达成：连续两轮内所有被评估候选（共测配对收尾判定）收益 < max(2%, 3σ) 或误差棒重叠，或候选池枯竭（no-candidate 裁定 + 穷尽说明）（记录表 + `_tmp/nop-rg-bench/` 判定 JSON 为证）
- [ ] 吞吐比不回退：64MB / 512MB 复测 ≥50%（<50% 时按协议完成 live-defect 处置路径）
- [ ] 2267 遗留 follow-up「512MB 安静环境复测」已清偿并记录
- [ ] 行为守护：nop-core + core/cli/vector 全量测试 + large-file 显式组 3/3 + rg 对照 opt-in 全绿；除 A1/A2 显式记录的行为收敛外输出逐字节不变
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] owner docs：cli README / design / module-groups 已同步 live baseline（或显式 No owner-doc update required）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：A1 两点修复非空壳（walker + GitIgnoreFile 均有对应回归测试）；A2/A8 显式裁定见 Phase 1（失败路径不可外部触发，JfrSwitchTest 回归守护）；kept 优化（若有）有基准数据支撑；无空方法体/静默跳过
- [ ] `./mvnw test -pl nop-kernel/nop-core,nop-rg/nop-rg-core,nop-rg/nop-rg-cli,nop-rg/nop-rg-vector -am` 通过
- [ ] `./mvnw compile -pl nop-rg/nop-rg-benchmark -am` 通过
- [ ] large-file 显式组 `-DexcludedGroups= -Dgroups=large-file -DargLine=-Xmx256m` 通过且测试计数 >0
- [ ] 代码规范检查：imports 分组、无裸 RuntimeException、错误消息英文
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-rg --severity high` 退出码 0

## 迭代记录表

> 环境：macOS arm64 / JDK 26.0.1 Zulu / 核数以 row0 实测为准 / rg 15.1.0。判定协议沿用 2267 修订版：**共测配对**（基线 = m2 快照 jar，候选 = target/classes，背靠背交替 3 对，增益按逐对配对差计算，σ = 配对增益离散度）对消运行间系统漂移；绝对值仅作参照；**判定基准 = 受影响口径的 e2e + 其 σ**。判定 JSON 落 `_tmp/nop-rg-bench/`。

| 轮次 | 基线 | 热点/依据 | 优化项 | 复测值 | 收益 | 保留/回退 |
| --- | --- | --- | --- | --- | --- | --- |
| （执行中填写） | | | | | | |

## Deferred But Adjudicated

### CompiledGlob 进程级无淘汰缓存

- Classification: `watch-only residual`
- Why Not Blocking Closure: javadoc 已显式记录 CLI 场景裁定，测试锁定同一性契约（assertSame）；仓内无长驻多模式消费者，无 in-repo 受害面。若未来库用户在长驻进程编译无界去重模式串，再评估有界化。
- Successor Required: `no`
- Successor Path: 无独立计划必要；任一后续 nop-rg 计划可顺带评估。

## Non-Blocking Follow-ups

- （待执行中产出后填写；confirmed live defect 不得记录于此）

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （收口时填写：独立子 agent）
- Evidence: （收口时填写）

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
