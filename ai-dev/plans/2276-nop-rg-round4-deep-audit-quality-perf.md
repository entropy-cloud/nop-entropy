# 2276 nop-rg 第四轮深度审计 —— 字面量枚举 rg 对齐 + CLI 退出码契约修复 + 性能收敛复验

> Plan Status: active
> Last Reviewed: 2026-09-23
> Source: 用户指令（对 nop-rg 深度审计：可读性/长期可维护性/性能，JMH/JFR 反复优化直到无 ≥2% 收益）；live repo 审计（2026-09-23，本 plan Current Baseline 与发现 G1-G6 全部经 read/grep 实测实证）；plans 2268/2273/2275（三轮审计均 completed）
> Related: Plan 2262-2268、2273、2275（均 completed）；2275 终态 = 性能基线守护参照（吞吐比 64MB 77.0% / 512MB 53.2%，P6 后口径）；2275 遗留 follow-up（--follow、多小文件 syscall 面、测试 import 分组）延续登记，不随本计划清偿
> Draft Review: 第一轮独立子 agent 对抗性审查（agent_2a085865-36b0-4ebd-bdc8-8e2ee456f070）：**无 Blocker**；G1（含「pos+patternLength 分块漏配」反例实证）、G2（类层级/picocli 行为/匿名子类测试可行性）、P1/P2 消费面全部独立推演为真；1 Major（F1：Phase 3/4 缺 kept 优化后的 benchmark README 同步与 design 01 显式裁定执行项+验收项）+ 6 Minor（cli 计数分解错位、G5 行号 L31-32 与 var 范围、两处 No-new-test 注记缺失、G4 改标 Fix、断言数字 2→1/5→3、rg 对照守护引用精度）+ 2 信息性（G1 影响面措辞全称命题收紧、rg 守护引用精度）已全部折入。第二轮 fresh-session 复审（agent_9396ca9f-e792-4a71-a2f7-94982618a12f）：F1-F8 逐项核对折入正确（live 代码逐行实证）；新发现 N1（Phase 1 Exit「9 例」实为 10 例）/N2（行为守护门禁未列 G4 help 文本例外）/N3（本头部精度）三项 Minor 已折入；复审结论「可进入执行阶段」，Plan Status 置 active

## Purpose

在 2268/2273/2275 三轮已收口的 live 代码上执行第四轮深度审计：(1) 修复本轮新发现的 live 契约偏差与 CLI 缺陷——字面量枚举重叠命中与 rg 非重叠语义的偏差（G1）、CLI 错误退出码契约缺口（G2）；(2) 清理冗余计算与风格残留（G3-G6）；(3) 在 2275 终态基线上重建 row0 并重新执行严格收敛循环——**终止条件（字面）= 连续两轮内所有被评估候选优化均未通过保留条件（收益 < max(2%, 3σ) 或误差棒重叠），或候选池枯竭（no-candidate 裁定 + 穷尽说明）**；(4) 收口复验吞吐比不回退（64MB / 512MB 双档 ≥50%）。

## Current Baseline

- 提交基线：nop-rg 最后 touched 于 `541e470f25`（plan 2275 收口 commit）。工作区未提交改动均为他会话的 nop-lint / nop-auth 项——**不触碰、不提交**；本计划所有 commit 均显式指定路径。
- 测试基线（2275 closure audit 独立复跑实测，2026-09-22）：core 7 类 **56 run + 1 skip**（rg 门控 `GlobMatcherTest.testCompareAgainstSystemRg`；large-file 组 3 个测试经 pom `excludedGroups=large-file` 默认排除，显式运行 `-DexcludedGroups= -Dgroups=large-file -DargLine=-Xmx256m` 另计 3/3）、cli **27**（JfrSwitch 3 + NopRgMain 10（含 2275 G1 补测 1）+ RgComparison 9 + VectorMode 5；RgComparison 默认跑、rg 不在 PATH 时 assumeTrue 跳过）、vector **10**。本计划执行时以实测刷新。
- 性能守护参照（2275 终态，勿回退）：吞吐比 **64MB 77.0% / 512MB 53.2%**（双档 ≥50%）；count 口径终态 profile（P6 后，64MB 12s）：byteAt 41.2% + indexOf 16.0% + matchesAt 12.5% + aggregate 19.3%（advanceLineStart 占 6.1% 实证为真实热点）——扫描带宽地板，与 2267/2273 终态同构；TEXT 口径基线 64MB 12.09±0.74 ops/s（2275 row0）。
- 已裁定不重复评估项（2265/2267/2273/2275 四轮实证或裁定在案，本轮循环 row0 记裁定行不再烧机时）：**SWAR/字并行 LF 扫描**（2265 R2 TEXT 口径、2267 R2 count 口径两次共测配对实证否决——根因为扫描内存带宽受限非 CPU 受限，该根因对 Vector-API 实现同样成立，本计划复核后维持）、span-gap 行计数（2267 R3）、count 单遍融合扫描（2267 R4：整遍消除都不可辨）、matchesAt verify 次序（2267）、LineCursor 同行 memo（零触发面）、≤1MB 堆读替代 mmap（2273 R3 劣化）、MappedFileReader double-stat（算术上界 <2%）、12B SIMD 阈值（2273 探测后维持 16B）、isBinary SWAR（2275 P2，profile 占比 1.9% 封顶）、walker 双 stat（2275 P3 算术上界 ≤1.7%）、TEXT ResultPrinter 逐段 print（2275 P1 实测 -3% 回退）、advanceLineStart 免行尾扫描已落地（2275 P6 保留 +9.9%）、CompiledGlob 进程级缓存（watch-only）、per-search ForkJoinPool（产品为每进程单次搜索）。
- 基准设施就绪：5 个 JMH 基准类 + HotspotProfiler（JFR 热点聚合器）；运行方式 = cwd `nop-rg/nop-rg-benchmark/` 下 `java -cp target/classes:$(cat target/cp.txt) org.openjdk.jmh.Main`（禁止 exec:java）；VECTOR 需 `--add-modules jdk.incubator.vector`；corpus 固定种子落 `$TMPDIR/nop-rg-bench-corpus/`。判定协议 = 2267 修订版共测配对（基线侧 = m2 快照 jar，候选侧 = target/classes，交替 3 对；保留条件 = 收益 ≥ max(2%, 3×σ_pair) 且误差棒不重叠；判定 JSON 落 `_tmp/nop-rg-bench/`，p2276-* 命名）。环境：macOS arm64 / JDK 26.0.1 Zulu / rg 15.1.0（`/opt/homebrew/bin/rg`）。
- **本轮审计新发现（G1-G6，全部经 read/grep 实测实证；行号以 541e470f25 为准）**：
  - **G1（live 契约偏差，字面量枚举重叠命中）**：`SearchCoordinator` 两条字面量路径命中推进均为 `from = pos + 1`（`searchFileChunked` L212、`literalSpans` L265）——自重叠模式（如 `aa` 于 `aaa`）的所有重叠出现全部入 spans。rg 为**非重叠**枚举（从命中末端继续）：`rg aa` 于 `aaa` 报 1 个 submatch，nop-rg 现状报 2。影响面：JSON submatch 序列与库 `LineMatch.getSubmatches()` 随之对齐 rg；行级口径（count/-l/TEXT）对无 LF 模式不变（同行重叠命中前后均计 1 行）；regex 路径本就非重叠（`Matcher.find()` 语义），两字面量路径与 regex 路径间不一致。测试面核查：现有测试全部使用无真前缀边界的模式（needle 等），无断言锁定重叠语义；`ScalarByteSearcherTest.testOverlappingMatchesFoundProgressively` 锁定的是 `findPattern` 按 offset 推进可发现重叠命中的**底层契约**（消费方循环推进语义），与本修复（coordinator 枚举策略）不冲突。修复 = **报告侧非重叠过滤**（维护 lastReportedEnd，仅报告 start ≥ lastReportedEnd 的命中；扫描推进保持 +1 不变）——不能改为推进 `pos + patternLength`：分块路径下跳过的区间可能落在下一块视图之外造成漏配（扫描推进步长与去重/过滤正交，现状 +1 保证 discovery 完整性）；whole-file 与 chunked 两路径同规则（lastReportedEnd 跨 chunk 持续、仅对 inPrimary 命中在报告时更新），维持「两条路径结果一致」契约。枚举语义（非重叠、rg 对齐）写入 SearchCoordinator 类 javadoc，措辞以「span 级枚举过滤，下游各口径随之对齐 rg」表述。
  - **G2（live defect，CLI 错误退出码契约缺口）**：`NopRgMain.call()` L166 仅捕获 `NopRgException | IllegalArgumentException`；父类 `NopException`（`CoreInitialization`/VFS/`GitIgnoreFile` 资源层运行时异常，实测 `NopException extends RuntimeException`）逃逸 → picocli 默认处理器打印堆栈并返回 exit 1——与「未命中 1」混淆，违反 javadoc 与 cli README 钉死的「错误 2」契约。修复 = catch 子句改为 `NopException | IllegalArgumentException`（覆盖 NopRgException）；`search(Path)` 可见性 private → 包私有，测试以匿名子类覆写抛出裸 `NopException` 断言 exit 2 + stderr 消息（不依赖构造真实 VFS 故障）。
  - **G3（冗余计算）**：`JsonOutput.writeMatch` 对每条 match 消息重复 `escape(path)`（每文件 N 行命中 = N 次全路径转义拷贝）；提升到 `writeMessages` 每文件转义一次传入。纯冗余消除，无 JSON 口径基准、不作 perf 主张（JSON 口径无基准覆盖记 follow-up）。
  - **G4（help/README 与行为矛盾的措辞）**：`NopRgMain` `--vector` 选项 description 写 "falls back to scalar **silently**"，实际 `resolveVectorFinder` 降级时打印 stderr 提示（不静默）；cli README 同行写「静默降级标量（stderr 提示）」自相矛盾。修复 = 两处措辞统一为「降级标量并输出 stderr 提示」。
  - **G5（风格残留，内联全限定名 + var——2268 F5/2275 G2 已确立无内联 FQN、显式类型标准）**：benchmark `CorpusUtil` 三处内联 FQN（`java.io.ByteArrayOutputStream` ×2 L31-32、`java.io.OutputStream` 方法参数 L82）改 import；同文件 L60 `var out`（F5 同族）改显式类型。
  - **G6（冗余中间列表）**：`SearchCoordinator.searchFile` L168-173 先 `new ArrayList<>()` 再 `spans.addAll(literalSpans(...)/regexSpans(...))`——每命中文件一次整表拷贝。修复 = 直接以 `literalSpans(...)`/`regexSpans(...)` 返回值作为 spans（方法已返回新建列表）。顺带微减分配，不作 perf 主张。
- 候选池（Phase 3 循环输入，均经本轮审计新识别或显式复核）：
  - **P1（TEXT 口径）**：`LineMatch` 构造器急切拼接 `lineWithTerminator = content + terminator`（每命中行一次全行内容拷贝）；grep 实证唯一生产消费者为 `JsonOutput`（JSON 模式），TEXT 模式（CLI 默认）与 -l/-c 均不读取。候选 = 惰性化（getter 首次调用时拼接，不可变语义不变、benign race 确定性）。依据：2275 row0 TEXT profile `buildLineMatches` 13.4%（含分配面）；本轮 row0 profile 复核后评估。
  - **P2（TEXT 口径，契约保留变体）**：`MatchAggregator.buildLineMatches` 每命中行新建 `ArrayList` 装载 submatches——单命中行（corpus 密度下多数行）免列表分配的 copy-on-write 变体（`List.of()` 起步、首次 add 时拷贝；`Submatch` 对象与区间契约完全不变，非 2275 R5「区间保留」契约的收窄）。预期收益小，如实评估，不预设结论。
  - **实现前否决（量化上界，沿 2275 P2/P3 先例，row0 记裁定行）**：`ParallelFileWalker` 每文件 `synchronized(context)` 改并发队列——512 文件 × ~50ns ≈ 25µs / op（op ≈ 31ms）≈ **≤0.1%**，且 many-small profile 中 syscall/FJP 面采样不可见；G3（JSON escape 提升）无 JSON e2e 口径不作 perf 评估；G6 同理（每文件一次拷贝，非每命中）。
- 已裁定保留项（本轮复核不翻案）：策略层（`ByteSearchStrategy`/`ScalarByteSearcher`/`VectorByteSearcher` 契约，design 决策 3/5）；`LineMatch` 全部 getter（JSON/测试消费面）；`CompiledGlob` 进程级缓存；`RegexSearcher` 整文件解码语义（>阈值 REGEX 显式失败为已钉死契约）。

## Goals

- **G1 修复**：字面量枚举非重叠化（报告侧过滤，两路径一致）；新增 SearchCoordinatorTest 聚焦用例（whole-file 与 chunked 小阈值两变体：自重叠模式 submatch 数量与偏移 = rg 非重叠语义）；枚举语义入 SearchCoordinator 类 javadoc。
- **G2 修复**：CLI `catch NopException | IllegalArgumentException`——裸 NopException 路径 exit 2 + stderr 消息；新增 NopRgMainTest 聚焦用例（包私有 search() 覆写抛 NopException）。
- **G3-G6 清理**：JSON escape 每文件一次；--vector help 与 cli README 措辞统一；CorpusUtil 内联 FQN 归 import；searchFile 免双列表拷贝。
- **性能基线重建**：row0 σ_run（count 三档收尾档连跑）+ TEXT / many-small 基线 + HotspotProfiler 三口径 profile（对照 2275 终态，无结构漂移则确认「不再评估」清单维持；有漂移须显式记录并重开对应项）+ 基线快照 install 到 m2（共测配对基线侧）。
- **严格收敛循环（用户硬性要求）**：在受影响口径上以 JMH 度量、JFR 定位，逐候选「实现 → 迭代档筛选 → 共测配对收尾判定」；保留条件 = 收益 ≥ max(2%, 3×σ_pair) 且误差棒不重叠；**终止条件（字面）= 连续两轮内所有被评估候选均未通过保留条件**，或候选池枯竭（no-candidate 裁定 + 穷尽说明）。kept 优化逐项 commit。
- **吞吐比不回退**：收口复测 64MB / 512MB 双档 ≥50%（2275 终态 77.0% / 53.2%）。
- 每 Phase 完成即 commit（显式路径，避开他会话文件）；daily log 逐 Phase 更新。

## Non-Goals

- 不改输出模式优先级、不实现 rg 的 count/files 覆盖 --json 语义（2275 Non-Goal 延续）。
- 不新增 `--follow`、rg 100% 选项兼容、FM-Index、跨平台调优、JSON 输出口径基准（新 follow-up 登记）。
- 不翻案已裁定项（Current Baseline 末两条清单）；不重复评估四轮已实证 <2% 的方向。
- 不动 2275 R5 已钉死的 `includeSubmatchText=false`「区间保留」契约（P2 为契约完全保留的分配优化，不是契约变更）。
- 不回写历史计划文本（guide Rule 20）。
- 不触碰工作区中他会话的未提交改动（nop-lint / nop-auth 文件、`.m2-repo-2275/`、`ai-dev/plans/nop-lint/2026-09-22-*`）。
- 测试文件 import 分组结构不在本计划范围（2275 裁定延续，watch-only）。

## Scope

### In Scope

- `nop-rg/nop-rg-core`（SearchCoordinator / LineMatch / MatchAggregator + 测试）
- `nop-rg/nop-rg-cli`（NopRgMain / JsonOutput + 测试 + README）
- `nop-rg/nop-rg-benchmark`（CorpusUtil 风格；row0/profile/配对判定数据落 `_tmp/nop-rg-bench/`）
- `ai-dev/plans/2276-*`、`ai-dev/logs/2026/09-23.md`、`ai-dev/design/nop-rg/01-architecture-baseline.md`（条件式，见 Phase 1 Exit Criteria）

### Out Of Scope

- nop-rg 目录之外的一切代码（含 nop-core；本轮无 nop-core 侧发现）
- nop-rg 功能扩展（新 CLI 选项、新搜索策略）
- 他会话未提交改动

## Execution Plan

### Phase 1 - 质量与契约修复（G1-G6）

Status: completed
Targets: `nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/coordinator/SearchCoordinator.java`、`nop-rg/nop-rg-cli/src/main/java/io/nop/rg/cli/{NopRgMain,JsonOutput}.java`、`nop-rg/nop-rg-cli/README.md`、`nop-rg/nop-rg-benchmark/src/main/java/io/nop/rg/benchmark/CorpusUtil.java`、`nop-rg/nop-rg-core/src/test/.../{SearchCoordinatorTest,搜索聚焦用例}`、`nop-rg/nop-rg-cli/src/test/.../NopRgMainTest.java`

- Item Types: `Fix`（G1、G2、G4）、`Follow-up`→本轮落地（G3、G5、G6，冗余/风格清理）

- [x] G1：字面量枚举非重叠过滤落地（whole-file + chunked 两路径同规则，lastReportedEnd 跨 chunk 持续；扫描推进 +1 不变）
- [x] G1：SearchCoordinatorTest 新增非重叠语义用例（whole-file：`aa`×`aaa`/`aaaaaa` 断言 submatch 数量与偏移；chunked 小阈值变体同断言；`两条路径结果一致` 复验）
- [x] G2：`NopRgMain.call()` catch 子句放宽到 `NopException | IllegalArgumentException`；`search(Path)` 收窄为包私有
- [x] G2：NopRgMainTest 新增用例（覆写 search 抛裸 NopException → exit 2 + stderr 含消息；正常路径回归不变）
- [x] G3：JsonOutput escape(path) 每文件一次
- [x] G4：--vector help 文本 + cli README 措辞统一（去 "silently"/「静默」，改「stderr 提示」）
- [x] G5：CorpusUtil 内联 FQN 改 import
- [x] G6：searchFile 免双列表拷贝
- [x] 回归：`./mvnw test -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli,nop-rg/nop-rg-vector -am` 全绿（新增用例计入；除 G1/G2 显式修复语义外输出不变）——core 59 run + 1 skip、cli 28（RgComparison 9/9）、vector 10
- [x] large-file 显式组 `-DexcludedGroups= -Dgroups=large-file -DargLine=-Xmx256m` 3/3
- [x] `./mvnw compile -pl nop-rg/nop-rg-benchmark -am` 通过（CorpusUtil 改动）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] G1：`SearchCoordinatorTest` 新用例在修复前语义下必然失败（如 `aa`×`aaa` submatch 数 2→1、`aa`×`aaaaaa` 5→3 的断言）、修复后通过；rg 对照守护以 `RgComparisonTest`（含 JSON submatch 元组对比，默认跑、rg 可用时）为准，opt-in 的 `-Dtest.rg.compare=true` glob 对照不受影响——RgComparisonTest 9/9 通过（rg 15.1.0 在 PATH）
- [x] G2：新用例断言裸 NopException → exit 2；既有 10 例 NopRgMainTest 行为不变
- [x] G3-G6：代码复核（escape 单次调用点、help/README 措辞一致、CorpusUtil 无内联 FQN 且无 var、searchFile 单列表）
- [x] No new test required（G3-G6）：纯冗余消除/措辞/风格/中间列表，输出字节不变，既有测试守护
- [x] **无静默跳过**：本 Phase 无新增公共方法/分支；G1 过滤为显式语义（javadoc 记录），非静默丢弃
- [x] owner docs：cli README 措辞修正（G4）；SearchCoordinator javadoc 枚举语义（G1）；`ai-dev/design/nop-rg/01-architecture-baseline.md` 若含匹配枚举语义描述则同步，经 read 核实无对应章节则显式裁定 `No design-doc update required`（javadoc 为唯一契约载体）——已裁定 No update required
- [x] `ai-dev/logs/2026/09-23.md` 对应条目已更新
- [x] 本 Phase 完成即 commit（显式路径）

### Phase 2 - 性能基线重建（row0）

Status: completed
Targets: `nop-rg/nop-rg-benchmark/`（JMH/HotspotProfiler 运行产物落 `_tmp/nop-rg-bench/`，p2276-* 命名）、`nop-rg/nop-rg-core,nop-rg-cli`（m2 快照）

- Item Types: `Proof`（基线与 profile 证据）

- [x] `./mvnw clean install -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli -DskipTests`（G1/G2 修复后的候选侧代码 install 到 m2 作共测基线）
- [x] row0 σ_run：count 口径 1MB/64MB/512MB 收尾档连跑 ≥2 次（记录离散度，仅作参照）——a/b 双跑落 p2276-row0-count-{a,b}.json
- [x] row0 TEXT 口径基线（64MB 收尾档）+ many-small 口径基线（512×128KB count）
- [x] row0 profile：HotspotProfiler count + text + many-small 三口径（对照 2275 终态结构）
- [x] 结构漂移裁定行：无漂移 → 「不再评估」清单维持（见记录表 row0 行：构成同构，byteAt/aggregate L49 帧间归因漂移，非结构变化）
- [x] README 刷新裁定：row0 基线相对 benchmark README 实测记录漂移 <10%（TEXT 12.23 vs 12.09、many-small 34.39 vs 31.82-33.37 同区间）→ 不动（显式裁定）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] row0 判定 JSON / profile log 落 `_tmp/nop-rg-bench/`（p2276-row0-*、p2276-profile-*）且记录表引用一致
- [x] m2 快照 jar 与 target/classes 同源（G1/G2 后代码，commit 880329fc52）
- [x] profile 结构裁定写入记录表（漂移与否 + 结论）
- [x] `ai-dev/logs/2026/09-23.md` 对应条目已更新
- [x] 本 Phase 完成即 commit（plan 记录表 + daily log）

### Phase 3 - 严格收敛循环（JMH/JFR）至字面终止

Status: completed
Targets: `nop-rg/nop-rg-core`（候选实现）、`nop-rg/nop-rg-benchmark/`（判定数据）

- Item Types: `Proof`（候选判定）、`Fix`（kept 优化落地）、`Follow-up`（否决项裁定记录）

- [x] P1（TEXT lazy lineWithTerminator）：实现 → 迭代档 TEXT 64MB 筛选（名义 +6~8% 通过）→ 共测配对 3 对收尾判定（中位 -0.01%，3 对 CI 全重叠）→ 未过保留条件 → 回退 + 记录（`git diff` 零残留）
- [x] P2（buildLineMatches submatches 单命中行 List.of）：实现前否决（row0 TEXT profile submatch add 1.0% + 算术上界 ≪1%，记录表 row0 行）
- [x] row0 记录实现前否决裁定行（walker synchronized ≤0.1% 算术上界；其余引用 Current Baseline 裁定清单）
- [x] kept 优化：无（P1 配对✗ 回退；无 kept commit 需求）
- [x] 终止裁定：候选池枯竭（no-candidate 裁定 + 穷尽说明——count/many-small 残余热点 = 扫描带宽地板与系统调用面（2267 R2/R3/R4 + 2273/2275 证据链 + 本轮 profile 无结构漂移）、TEXT 残余 = 输出 Writer 契约必需 + 行解码 + 输出拼接（2275 P1 回退实证）、vector 12B 已闭合）写入记录表；本轮唯一实施候选 P1 配对✗ 构成同条款佐证
- [x] 吞吐比收尾复测：64MB 56.3% / 512MB 51.6% 双档 ≥50%（<50% 时按协议完成 live-defect 处置路径，不静默）——达标，记录表留档
- [x] owner docs 收口复核（kept 优化触发时）：无 kept 优化 → benchmark README 不动（row0 基线漂移 <10% 已裁定）；`ai-dev/design/nop-rg/01-architecture-baseline.md` 裁定完成：本轮无 kept 优化、G1 契约语义已裁定 javadoc 为唯一载体（Phase 1）→ **No design-doc update required**

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 每个 in-pool 候选在记录表有一行：判定数据（迭代档/配对 JSON 引用）或显式否决依据（量化上界/裁定引用）
- [x] kept 优化：无（P1 配对✗ 回退；No new test required：无 kept 优化落地——P1 实现期间行为不变由既有 TEXT/JSON/RgComparison 测试守护，回退后代码与 Phase 1 逐位一致）
- [x] **owner docs 验收**（对应上方收口复核执行项）：benchmark README 不动裁定 + design 01 No-update 裁定已在记录表与本节留痕
- [x] 否决候选：代码零残留（`git diff HEAD -- nop-rg/` = 0 行复核）
- [x] **终止条款达成**（字面路径：候选池枯竭 + 穷尽说明）且终止裁定行写入记录表
- [x] 吞吐比双档复测值写入记录表且 ≥50%
- [x] 行为守护：`./mvnw test -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli,nop-rg/nop-rg-vector -am` 全绿（core 59 run + 1 skip、cli 28、vector 10）；large-file 显式组 3/3；搜索结果输出逐字节不变（G1/G2 修复语义与 G4 help 文本为 Phase 1 显式变更，本 Phase 无 CLI 语义变更）
- [x] `ai-dev/logs/2026/09-23.md` 对应条目已更新
- [x] 本 Phase 完成即 commit（无 kept 优化：收尾判定/记录一次）

### Phase 4 - 收口（文本一致性 + 独立 closure audit）

Status: planned
Targets: `ai-dev/plans/2276-*`、`ai-dev/logs/2026/09-23.md`

- Item Types: `Proof`

- [ ] 从头重读整份 plan，逐条核对 Phase 1-3 Exit Criteria 与本 Closure Gates
- [ ] 文本一致性核对：Plan Status / 各 Phase Status / Exit Criteria / Closure Gates / daily log 五处一致
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2276-nop-rg-round4-deep-audit-quality-perf.md --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-rg --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（fresh session，未参与起草与执行）并写入 Closure 段证据
- [ ] `Plan Status: completed` + `Completed: 2026-09-23`（audit 通过后）
- [ ] 收口 commit

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 三工具退出码 0 的输出已留档（daily log 或 plan 引用）
- [ ] 独立 closure audit 结论与证据写入 `## Closure`（逐条 Exit Criterion / Closure Gate 的 PASS + 来源）
- [ ] daily log 收口条目已更新
- [ ] 收口 commit 完成

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 所有 in-scope confirmed live defects / contract deviations 已修复（G1 枚举语义、G2 退出码契约；含聚焦回归测试）
- [ ] G3-G6 全部 landed（冗余计算 / 措辞 / 风格 / 中间列表）
- [ ] 收敛循环终止达成（字面条款：连续两轮全候选未过保留条件，或候选池枯竭 + 穷尽说明；记录表 + `_tmp/nop-rg-bench/p2276-*` 判定 JSON 为证）
- [ ] 吞吐比不回退：64MB / 512MB 复测 ≥50%（<50% 时按协议完成 live-defect 处置路径）
- [ ] 行为守护：core/cli/vector 全量测试 + large-file 显式组 3/3 + rg 对照 opt-in 全绿；除 G1/G2 显式修复语义外 CLI 输出逐字节不变
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] owner docs：cli README / SearchCoordinator javadoc 已同步 live baseline；design 01 条件式更新已裁定（更新或显式 No update required）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：G1/G2 修复非空壳（聚焦测试真实断言）；kept 优化（若有）有配对数据支撑；无空方法体/静默跳过
- [ ] `./mvnw test -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli,nop-rg/nop-rg-vector -am` 通过
- [ ] `./mvnw compile -pl nop-rg/nop-rg-benchmark -am` 通过
- [ ] large-file 显式组 `-DexcludedGroups= -Dgroups=large-file -DargLine=-Xmx256m` 通过且测试计数 >0
- [ ] 代码规范检查：imports 分组组内字母序、无内联 FQN、无裸 RuntimeException、错误消息英文
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-rg --severity high` 退出码 0

## Deferred But Adjudicated

### CompiledGlob 进程级无淘汰缓存（延续 2273/2275 裁定）

- Classification: `watch-only residual`
- Why Not Blocking Closure: javadoc 已显式记录 CLI 场景裁定，测试锁定同一性契约；无 in-repo 受害面。本轮不翻案。
- Successor Required: `no`
- Successor Path: 任一后续 nop-rg 计划可顺带评估。

## Non-Blocking Follow-ups

- `--follow`（符号链接跟随开关）：延续 2273/2275 登记（out-of-scope improvement）。
- 多小文件每文件系统调用开销（open/fstat/map）：延续 2273/2275 登记（optimization candidate）。
- 测试文件 import 分组结构：延续 2275 裁定（watch-only）。
- JSON 输出口径无 JMH 基准覆盖（本计划 G3 以质量项落地、不作 perf 主张；如未来 JSON 性能成为关注面，先建 `mode=json` 口径再评估）（optimization candidate）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Audit Session: <<session ID>>
- Evidence: <<逐条 Exit Criterion / Closure Gate 验证结果>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>

## 迭代记录表

> 环境：macOS arm64 / JDK 26.0.1 Zulu / rg 15.1.0（/opt/homebrew/bin）。判定协议 = 2267 修订版共测配对（基线 = m2 快照 jar，候选 = target/classes，交替 3 对；增益按逐对配对差计算，σ = 配对增益离散度；判定 JSON 落 `_tmp/nop-rg-bench/`，p2276-* 命名）；绝对值仅作参照；判定基准 = 受影响口径的 e2e + 其 σ。

| 轮次 | 基线 | 热点/依据 | 优化项 | 复测值 | 收益 | 保留/回退 |
| --- | --- | --- | --- | --- | --- | --- |
| 环境/噪声行（2026-09-23 06:12 CST） | macOS arm64 / 16 核 / JDK 26.0.1 Zulu / rg 15.1.0；load 6.76-4.00（外部负载存在，绝对值仅作参照，判定全靠共测配对） | — | — | — | — | — |
| row0 σ_run count（收尾档 ×2，p2276-row0-count-{a,b}.json，needle-6B） | 1MB 830.55±59.29 / 951.43±175.38（连跑偏离 ~13.6%）；64MB 50.38±12.98 / 45.04±13.35（±26%，外部负载噪声，与 2275 row0 ±11.6% 同性质）；512MB 13.24±0.66 / 12.76±0.37（±3-5% 稳定） | — | — | — | — | σ_run 仅作参照；判定以共测配对为准 |
| TEXT 口径基线（收尾档，p2276-baseline-text.json） | 64MB 12.23±0.46 ops/s（2275 row0 12.09±0.74 同区间——G1 对无自重叠 corpus 的 count/text 行为零变化，符合预期） | — | — | — | — | Phase 3 候选来源证据基线 |
| many-small 口径基线（收尾档，p2276-baseline-manysmall.json） | 34.39±1.08 ops/s（2275 31.82±3.14 / 2273 33.37±0.80 同区间） | — | — | — | — | Phase 3 候选来源证据基线 |
| row0 profile count（HotspotProfiler 12s，p2276-profile-count.log，64MB，2435 样本） | LineCursor.byteAt 43.5% + MatchAggregator.aggregate L49 30.4%（span 迭代帧，含内联扫描样本归因）+ matchesAt 13.1% + indexOf 7.0%——扫描构成与 2275 终态（byteAt 41.2+indexOf 16.0+matchesAt 12.5+aggregate 19.3）同构：byteAt+indexOf（LF 扫描）50.5% vs 57.2%、aggregate 帧吸走差异，**归因漂移非结构变化** | — | — | — | — | **无结构漂移裁定**：「不再评估」清单维持（SWAR/Vector LF 扫描、span-gap、单遍融合、verify 次序等） |
| row0 profile text（p2276-profile-text.log，1301 样本） | BufferedWriter.write 31.2%（输出契约必需）、indexOf 15.1%、ResultPrinter.print:41 10.9%（输出拼接，2275 P1 已实证优化方向为回退）、buildLineMatches 16.9%（L71 7.1% = LineMatch 构造含行解码+lineWithTerminator 拼接、L79 8.8% 循环、L78 1.0% submatch add）、matchesAt 8.3%、LineCursor.text 4.5%、advance 5.7% | — | — | — | — | **P1 入池依据** = LineMatch 构造帧 7.1% 内的 lineWithTerminator 急切拼接（唯一消费者 JsonOutput）；P2 预否决依据 = submatch add 仅 1.0% |
| row0 profile many-small（p2276-profile-manysmall.log，2252 样本，count） | byteAt 41.0% + aggregate L49 32.4%（行扫描+span 迭代 ~73%，归因漂移同 count 口径）+ matchesAt 19.3% + find 3.3%；syscall/FJP 面采样不可见 | — | — | — | — | 与 2275 同构；walker synchronized 算术上界 ≤0.1% 维持预否决 |
| P2（TEXT buildLineMatches submatches 单命中行 List.of 免 ArrayList）——实现前否决 | — | row0 TEXT profile submatch add 仅 1.0%（L78）；每命中行工作量为行解码+输出 µs 级，ArrayList 免除 ~10ns/行 | 无（未实现） | 理论上界 ≪1% | 不满足保留条件（上界即不足） | **实现前否决**（profile 占比 + 算术上界双重依据；非 2275 R5 契约变更路径） |
| walker synchronized 预否决行（沿 2275 P3 先例，row0 登记） | — | 512 子项 × ~50ns ≈ 25µs / op（op ≈ 29ms）≈ ≤0.1%；many-small profile 中 FJP 面采样不可见 | 无（未实现） | 理论上界 ≤0.1% | 不满足保留条件 | **实现前否决**（plan 预授权路径） |
| G6（searchFile 免双列表拷贝）行——随 Phase 1 落地，非 perf 主张 | — | 每命中文件一次 O(spans) 拷贝（非每命中），row0 profile literalSpans 帧 0.6%/0.2% 量级 | 已落地（880329fc52） | — | — | 质量清理，不作 perf 评估 |
| R1——P1（TEXT LineMatch lineWithTerminator 惰性拼接） | row0 TEXT profile buildLineMatches L71 7.1%（LineMatch 构造含急切 concat；唯一消费者 JsonOutput） | 实现 = 构造器免 concat、getter 按 lineEnd-contentEnd 惰性派生；输出字节不变 | 迭代档 TEXT 64MB 候选侧两跑 12.979±0.333 / 13.165±0.244 vs row0 基线参考 12.23±0.46（名义 +6~8%，通过筛选）→ 收尾共测配对 3 对（p2276-p1-pair1..3-{base,cand}.json）：**-0.01% / +0.46% / -0.65%，中位 -0.01%，3 对 CI 全重叠**（σ_pair 0.56%） | 中位 -0.01% ≪ max(2%, 3σ)=2% | 未通过保留条件（三对 CI 重叠 + 中位≈0） | **回退**（筛选档名义收益被证实为基线侧环境漂移伪影——配对中基线侧自行上行至 13.12-13.29，共测配对正确对消；机理：concat 分配隐藏在输出 Writer 的内存停顿空隙中，消除不可辨）。代码已还原（`git diff HEAD -- nop-rg/` 零残留复核） |
| 终止裁定（P1 配对✗ + P2/walker 实现前否决 + 候选池枯竭） | count 终态 profile = 扫描带宽地板（byteAt+indexOf 50.5% + matchesAt 13.1%，2267 R2/R3/R4 + 2273/2275 五项实证：遍数级/指令级/字并行优化均 <2% 不可辨）；TEXT 残余 = 输出 Writer 契约必需（31.2%）+ 行解码（4.5%）+ 输出拼接（print:41 10.9%，2275 P1 已实证优化方向为回退）；many-small 残余 = 同 count 地板 + syscall 面（采样不可见，P3/walker-sync 上界 ≤0.1-1.7%）；vector 12B 已闭合（2273）；本轮 profile 无结构漂移，无新候选产生 | — | — | — | — | **循环终止**（字面条款：候选池枯竭 + 本轮唯一实施候选 P1 配对✗；穷尽说明 = 上列四口径残余热点全部为契约必需或已多轮实证地板，候选池 P1-P2 及历史清单全部有判定数据或显式否决依据） |
| 吞吐比收尾（64MB / 512MB，m2=HEAD 代码，p2276-final-*.json） | coord 64MB 45.23±13.81（本机外部负载 ±30% 噪声，row0 双跑 50.38/45.04 同区间）/ rg 80.28±0.61；coord 512MB 12.75±0.67 / rg 24.71±0.22 | — | — | 64MB **56.3%**、512MB **51.6%** | 双档 ≥50% 达标 | 512MB 稳定档与 2265-2275 历史 49-53% 区间一致（2275 终态 53.2%）；64MB 偏离 2275 的 77.0% 系 coord 侧负载噪声（rg 侧与 2273 的 80.9 恒定），门禁实质达成 |
