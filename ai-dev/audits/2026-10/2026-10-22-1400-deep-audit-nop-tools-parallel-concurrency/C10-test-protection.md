# C10 测试保护力评估 — nop-tools-parallel 并发审计四维最终保留项

- **审计单元**: `nop-tools-parallel` 四维最终保留项全部 20 条实体（C1×3 / C4×5 / C6×6 / C9×6，均已过独立复核）；评估对象 = 三模块 `src/test` 测试面（32 个 Java 文件：nop-rg 14（含 vector 1）/ nop-find 4 / nop-bytecode 14，其中 3 个为 helper/bench 非测试类）。
- **方法**: 对每条发现核问「现有测试能否在该缺陷引入时变红？」——逐条将缺陷面与 src/test 的断言面比对：先按发现给出的站点定位对应测试文件通读断言，再用跨测试 grep 交叉验证否定结论（命令与命中数随行附注）；nop-bytecode.md owner doc 提到的测试基建（TestCompiler toy 源编译、CLI run() 捕获退出码/stdout/stderr 的端到端形态、BenchmarkSmokeTest 的 JMH smoke 先例）已纳入核对，无额外的并发测试基建。
- **盲审声明**: 本评估为盲审——未读取 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何文件；输入仅限同目录 C1/C4/C6/C9 四份已复核最终保留项（任务指定输入）与 live 代码（src/test 全量 + 必要的 src/main 站点回看）。未修改产品代码与四维审计文件。
- **判定口径**: 「会红」= 存在直接钉住缺陷面的断言，缺陷引入（或复发）必失败；「部分」= 仅相邻面有钉（缺陷修复若顺手破坏相邻契约会红，缺陷面本身不会红）；「不会红」= 现有套件对该缺陷面零断言，引入后全绿。
- **日期**: 2026-10-22

---

## 一、20 行保护力矩阵

| # | 条目（复核后严重度） | 缺陷面一句话 | 现有测试断言面（证据） | 引入即红？ |
|---|---|---|---|---|
| 1 | C1-001（P2，主登记，并入 C4-003+CL-301） | SearchCommand.patternBytes 惰性缓存非原子检查-然后-行动，跨线程复用 command 竞态 | `grep -rn "patternBytes" --include="*.java" nop-tools/{nop-rg,nop-find}/*/src/test nop-tools/nop-bytecode/src/test` = **0 命中**；全部测试单线程新建 command 即用；唯一复用点 LargeFileSearchTest.testRepeatScanConsistency 为同线程顺序复用且 @Tag("large-file") 默认排除 | **不会红** |
| 2 | C1-002（P3） | WalkContext.recordError volatile 检查-然后-行动，「首错胜出」注释与「任意胜出」实现漂移 | ParallelFileWalkerTest.testWalkFailsFastOnUnreadableDirectory 仅构造**单一**锁定目录，断言 walk() 抛 NopRgException（fail-fast 存在性已钉）；多错误胜负语义无用例 | **部分**（fail-fast 存在性钉死；胜负语义缺陷面不会红） |
| 3 | C1-R2-001（P2，限缩） | 映射存活期 × 外部截断 TOCTOU → InternalError 无文件上下文 | MappedFileReaderTest / ChunkedFileReaderTest / LargeFileSearchTest 全部在静态树上演；`grep -rn -iE "truncat" .../src/test` = 14 命中均为 maxMatches 截断/截断 class 用例，「扫描中文件截断」= **0 用例** | **不会红** |
| 4 | C4-001（P3） | 每调用新建池且线程无命名，JFR 归因劣化 | `grep -rn "ThreadFactory" .../src/test` = **0**；`.getName()` 4 命中均为 JFR 事件类型/类名，无线程名断言；testThreadCountDoesNotChangeResult 与命名正交 | **不会红** |
| 5 | C4-002（P2） | invokeAll 首错弃全已完成结果 + 文案无文件路径 | `grep -rn -E "stat file failed|map file failed|open file failed|search task failed|search fault" .../src/test` = **0 命中**；无 TOCTOU 用例；相邻钉仅覆盖 walker 侧已声明契约（JfrSwitchTest.testJfrDumpStillGeneratedOnSearchError、ParallelFileWalkerTest 锁定目录测试）——即 C4-002 失效面枚举第 6 项的**非缺陷半边** | **不会红**（缺陷半边：search 任务聚合语义零断言） |
| 6 | C4-003（P3，并入 C1-001） | 同站点（SearchCommand.patternBytes） | 随 #1，同站同证据 | **不会红**（随主登记） |
| 7 | C4-R2-001（P3） | ParallelFileWalker.walk() 完全不可取消，中断悬挂 | `grep -rn -E "\.interrupt\(\)|isCancelled|cancel\(true\)" rg/find/bytecode 全部 src/test` = **1 命中**（SystemRgDelegate.java:47 清理性 interrupt，非取消用例）；walker 侧唯一 @Timeout(60) 钉符号环终止，非取消通道 | **不会红** |
| 8 | C4-R2-002（P3） | --jfr 在 SIGINT/SIGTERM 下无 dump 无 shutdown hook | JfrSwitchTest 钉死正常路径与搜索错误路径的 dump（3 测试断言 jfr 文件存在且可解析）；`grep -rn -E "SIGINT|SIGTERM|addShutdownHook" .../src/test` = **0**（信号路径进程内不可测，也无 shell 级集成测试） | **部分**（相邻面钉死；缺陷面——信号路径——不会红） |
| 9 | C6-001（P3，降） | ChunkedFileReader 单 arena 累积全部块视图，映射驻留≈全文件 | ChunkedFileReaderTest 钉平铺/跨界去重/参数校验/close 幂等/关闭后访问 IllegalStateException（testAccessAfterCloseThrows）；无映射驻留量/块数上界断言 | **部分**（关闭语义相邻面钉死；驻留形状缺陷面不会红） |
| 10 | C6-002（P3，降） | 扫描循环无中断检查点 + shutdownNow 不等待，取消后资源驻留 | rg 侧测试 interrupt 命中 = **0**（同 #7 证据）；无取消延迟/资源驻留用例 | **不会红** |
| 11 | C6-003（P3） | RgCompareBenchmark 子进程无 destroy/无超时/stderr 不消费 | `find nop-tools/nop-rg/nop-rg-benchmark -path "*src/test*" -name "*.java"` = **0 文件**（模块无测试目录）；`grep -rln -E "RgCompareBenchmark|HotspotProfiler|CorpusUtil" .../src/test` = **0** | **不会红** |
| 12 | C6-004（P3） | HotspotProfiler /dev/null sink 只 flush 不 close | 同 #11：基准模块零测试、零引用 | **不会红** |
| 13 | C6-005（P3） | GlobMatcherTest 两处 createTempDirectory 无清理 | 缺陷本体即测试代码自身（GlobMatcherTest.java:144/:185）；JUnit 对 createTempDirectory 不自动清理，套件内亦无任何清理断言载体 | **不会红**（本体无保护概念） |
| 14 | C6-R2-001（P3） | CollectManifest.save 原地截断写非原子，崩溃自伤 manifest | NopBytecodeMainTest.corruptManifestFailsLoudly 钉**读侧** malformed→loud 契约、tamperedSha256BetweenCollectAndReadFailsLoudly 钉 TOCTOU 校验；无写中断/原子性用例 | **部分**（读侧相邻契约钉死；写侧原子性缺陷面不会红） |
| 15 | C9-001（P3，降） | walker 每文件全局锁 + 每子项双 stat 串行化 | FindCoordinatorTest.testParallel* 系列钉**结果等价**（1/2/4/8/16 线程一致），不钉锁次数/stat 次数；全测试源性能/时间断言仅 3 处且均非本面（JfrSwitchTest 满载循环、@Timeout(60)、ResourceAnalysisTerminationTest 的 bytecode deadline） | **不会红** |
| 16 | C9-002（P2） | read-back 逐 artifact 重开 JarFile，O(entries×central-directory) | NopBytecodeMainTest 14 个端到端测试全为功能断言（exit code/finding 文本/JSON 结构）；jar 输入测试（multiRelease、dedup）对打开次数完全不敏感，无 open 计数/耗时断言 | **不会红** |
| 17 | C9-003（P3） | RegexSearcher 零命中文件也无条件构建 4B/char 偏移映射 | 输出等价类测试（RgComparisonTest、VectorModeTest、SearchCoordinatorTest regex 用例）对内部分配不敏感；无 -prof gc / 分配率断言（BG-3 基准缺位） | **不会红** |
| 18 | C9-004（P3） | LineCursor.advance 逐 span 重扫行尾，多命中长行 O(spans×行长) | testFusedCountEquivalenceFuzz + testCountOnlyDistinctLineSemantics 钉死**行数语义正确性**（count/text 等价 fuzz 60 轮）——若修复破坏行语义必红；扫描量本身无断言 | **部分**（正确性钉死——防坏修复；性能缺陷面不会红） |
| 19 | C9-R2-001（P3） | advanceLineStart 同行 span 簇打破均摊，行前缀重扫 | 同 #18：count 口径语义由同一 fuzz 钉死；同行重扫量无断言 | **部分**（同上） |
| 20 | C9-R2-002（P3） | CompiledGlob 多 `**` 回溯无 memoization，串行段 O(depth^k) | GlobMatcherTest 钉死 glob 语义正确性（含 a/**/b 全域语义、转义、字符类）；病态 pattern（多 `**`+深路径）性能无用例，BG 表亦无该基准 | **部分**（正确性钉死——防坏修复；最坏形态性能不会红） |

**矩阵计数**：会红 0 / 部分 7（#2、8、9、14、18、19、20）/ 不会红 13。20 行与四维最终保留项一一对应（C1-001/C1-002/C1-R2-001；C4-001/C4-002/C4-003/C4-R2-001/C4-R2-002；C6-001..005/C6-R2-001；C9-001..004/C9-R2-001/C9-R2-002），无遗漏、无重复。

---

## 二、缺保护登记（P2 逐条 / P3 聚合）

### P2 逐条（4 条，C4-003 并入 P2-01）

**P2-01【C1-001，含 C4-003】并发复用 SearchCommand 零保护**
- 缺口：全部测试单线程用后即弃 command；`patternBytes()` 在测试中零直接调用；唯一复用测试为同线程且默认排除。竞态双写与未安全发布窗口均不可观测。
- 建议测试形态：两线程对同一 `SearchCommand` 并发各调 `search()`（或直接并发 `patternBytes()`）× 高次数循环，每次断言返回字节内容与 `pattern.getBytes(UTF_8)` 恒等（宽松 loop+assert 即可捕捉双写错乱；JMM 零数组窗口若要确定性捕捉需 jcstress，成本高可不做）。若采纳主登记建议 (1) 构造器急切编码，本测试退化为普通等价测试，仍守护修法不被回退。

**P2-02【C1-R2-001】扫描窗口内文件截断零保护**
- 缺口：无任何「映射存活期 × 外部截断」用例；InternalError 无文件上下文的行为未被钉定为缺陷。
- 建议测试形态：worker 线程对 8MB 文件建映射扫描中，主线程 `FileChannel.truncate` 后断言——按建议 (a) 落地后：`search()` 抛 `NopRgException` 且消息含文件相对路径（`assumeTrue` 门控 FFM fault→InternalError 的 JDK 行为差异，防跨 JDK flaky）；正/F3 兄弟面（分块路径二次映射 IOOBE 变体）随同一测试参数化。

**P2-03【C4-002】单文件 TOCTOU 全量弃结果零保护**
- 缺口：搜索任务聚合语义（首错弃全 vs 部分结果）零断言；错误文案是否携带文件路径零断言。
- 建议测试形态：双文件树 + 其一 `chmod ---------`（任务内 stat/map 必败，构造确定性错误，避免真实 TOCTOU 时序）→ 按建议 (a) 断言：另一文件结果照常返回 + 错误项含文件路径；按 (b) 断言：异常消息含失败文件路径。无论取哪个修法，先钉「错误路径不再静默丢弃已完成结果」。

**P2-04【C9-002】JarFile 重开放大零保护**
- 缺口：功能测试对打开次数不敏感；nop-bytecode 侧无 read-back 性能断言。
- 建议测试形态：优先随修复落地 per-input 单实例，再以 BenchmarkSmokeTest 先例补 JMH smoke（forks(0)+1 iteration，对标 nop-bytecode 既有 BenchmarkSmokeTest）防回退；轻量替代 = 注入式 JarOpener 计数 seam 断言「read-back 每 jar 至多 open 1 次」。与 C9 BG-5 基准缺口合并排期。

### P3 聚合（6 组，命名覆盖 14 行）

**AG-P3-01 池治理与取消面【C4-001 + C4-R2-001 + C6-002】**
- 建议：命名——为池创建引入 ThreadFactory seam 后，断言 worker 线程名前缀（如 `nop-rg-search-`）；取消——嵌入方线程 interrupt + `assertTimeoutPreemptively` 断言 walk/search 有界终止（现行不可取消实现下该测试必红，须随「cancelled 通道」修复同步落地，先红后绿即本组缺保护的兑现路径）；javadoc 契约钉死后可以 ArchUnit/文档检查守护。

**AG-P3-02 信号与基准模块生命周期面【C4-R2-002 + C6-003 + C6-004】**
- 建议：--jfr 信号路径用 shell 级集成测试（POSIX 门控）：后台启动 `--jfr` 搜索 + `kill -INT` → 轮询断言 jfr 文件生成（随 shutdown hook 修复落地）；基准模块（无测试目录）以最低成本静态规约门控守护——grep gate 钉「ProcessBuilder 必配 destroyForcibly 路径」「PrintWriter sink 必配 close」，或纳入代码评审 checklist。

**AG-P3-03 性能回归面【C9-001 + C9-003 + C9-004 + C9-R2-001 + C9-R2-002】**
- 建议：按 C9 BG-1..BG-5 落 JMH 基准（病态输入参数化：单行 5MB×10k 命中、`**/*/**/x` × depth-20 树、零命中 regex 语料），加 BenchmarkSmokeTest 式 smoke 防基准腐坏；轻量替代 = 确定性最坏输入的墙上时间宽松上限断言（防二次方退化回归，阈值放宽 5-10×防 flaky）。现有 fuzz 已钉正确性，本组只需补「量」维。

**AG-P3-04 容量包络面【C6-001】**
- 建议：小 chunkSize 注入（测试已用 8-64B 惯例）+ 包私有 seam（或反射断言 arena 内视图数）断言「映射数 ≤ ⌈S/chunkSize⌉」；若采纳建议 (b) 仅钉包络声明，则以 owner doc + javadoc @sample 形态守护，不做行为测试。

**AG-P3-05 测试卫生【C6-005】**
- 建议：缺陷本体即测试代码——`@TempDir` 归一修复即闭环，无需保护性测试；可选在 ci grep gate 扫 `createTempDirectory` 无配对 delete 的形态防复发（F3 全量复扫确认现存仅此 2 处）。

**AG-P3-06 守卫语义面【C1-002】**
- 建议：双锁定目录场景断言 walk() 抛错且消息含真实失败目录（现行消息面核实后再钉）；「首个胜出」胜负语义因时间序不可确定性断言，若采纳「改注释承认任意胜出」修法则本组以注释+契约测试（错误必抛、必为真实错误之一）收口即可，无需更多。

**复核补记（C10 独立复核修正，2026-10-22）**：原稿「覆盖 16 行」不实——上列 6 组命名覆盖仅 14 行 P3 实体；C4-003 已随 P2-01 同证登记（15/16），**C6-R2-001（CollectManifest.save 写侧原子性）未入任何组**。补记其缺保护建议：随 C6-R2-001 修复工单（临时文件 + ATOMIC_MOVE）同步落地写中断回归用例——save 中途注入 IO 异常 → 断言原 manifest 未被截断、读侧仍 loud 失败（先红后绿）；读侧相邻钉（NopBytecodeMainTest.corruptManifestFailsLoudly / tamperedSha256BetweenCollectAndReadFailsLoudly）已存在，见矩阵 #14。

---

## 三、汇总

- **矩阵规模**：20 行 = C1×3 + C4×5 + C6×6 + C9×6，与四维最终保留项一一对应（自我复核通过；C4-003 随主登记 C1-001 同证评估）。
- **会红/不会红**：会红 **0**；部分（仅相邻面有钉）**7**；不会红 **13**。整体结论：现有 32 个测试文件全部为**功能语义/正确性面**断言（输出等价、契约 loud 失败、终止性、关闭幂等），对 20 项并发/资源/热路径缺陷面**无一有直接断言**——缺陷面引入即全绿。
- **结构性盲区**（跨条目共性）：① 无任何并发原语测试（interrupt/ThreadFactory/latch 全测试源合计 1 处清理性 interrupt）；② 无 TOCTOU/外部变更注入用例（文件截断、chmod、SIGINT 均 0）；③ 无性能量断言（3 处时间断言均为终止性而非性能阈值）；④ 基准模块（nop-rg-benchmark）零测试目录。
- **缺保护登记**：**10 项**（P2 逐条 4：P2-01..P2-04；P3 聚合 6：AG-P3-01..06，命名覆盖 14 行 P3 实体；C4-003 随 P2-01 登记；C6-R2-001 写侧原子性缺口经复核补记，见二节末）。其中 3 项（P2-01、P2-03、AG-P3-01 取消半边）建议「先红后绿」——测试随修复工单同步落地；其余可按登记建议排期。
- **交叉验证口径复现**：`grep -rn "patternBytes" ...src/test`=0；`grep -rn -E "\.interrupt\(|ThreadFactory|shutdownNow|awaitTermination|CountDownLatch" ...src/test`=1（SystemRgDelegate.java:47）；`grep -rn -E "SIGINT|SIGTERM|addShutdownHook" ...src/test`=0；`grep -rn -E "stat file failed|map file failed|open file failed|search task failed|search fault" ...src/test`=0；`find nop-tools/nop-rg/nop-rg-benchmark -path "*src/test*" -name "*.java"`=0；`grep -rln "RgCompareBenchmark|HotspotProfiler|CorpusUtil" ...src/test`=0；`grep -rn -iE "truncat" ...src/test`=14（全部为 maxMatches 截断/截断 class，非文件截断；C10 复核由 11 修正为 14）。
- **本文件未修改**任何产品代码与 C1/C4/C6/C9 四维审计文件。

---

## 复核结论

- **复核人/日期**：C10 独立复核子代理，2026-10-22（抽样复核，独立 grep/读源重演）。
- **完整性**：通过。四维最终保留项 C1×3 + C4×5 + C6×6 + C9×6 = 20 条与矩阵 20 行一一对应；C4-003 判级 P3 并入 C1-001 与 C4-thread-pools.md:155/:284 一致。测试面计数复现：nop-rg 14（含 vector 1）/ nop-find 4 / nop-bytecode 14 = 32，其中 TestCompiler/CorpusGenerator/NullflowKernelBenchmark `@Test`=0（3 个非测试类属实）。
- **抽样**：10 行（要求 ≥7），覆盖 P2 全部 4 行（#1/#3/#5/#16）+ P3 4 行（#7/#11/#12/#13）+ 部分 2 行（#2/#8）。逐行判定全部成立：
  - #1：`patternBytes` 全测试源 0 命中；LargeFileSearchTest.java:34 `@Tag("large-file")` 默认排除、:155-167 testRepeatScanConsistency 同线程顺序复用——「不会红」成立。
  - #3：`truncat` 复演 **14 命中**（原记 11，已修正；= CoreSearchIntegrationTest×3 + SearchCoordinatorTest×4 + ResourceAnalysisTerminationTest×5 + NopBytecodeMainTest×2，全为 maxMatches 截断/bytecode 截断 class），「扫描中文件截断」0 用例——「不会红」成立。
  - #5：错误文案 5 串 0 命中；相邻钉核实 JfrSwitchTest.java:105-127（walker 错误半边 exit 2 + dump 仍生成）+ ParallelFileWalkerTest.java:95-109——「不会红（缺陷半边零断言）」成立。
  - #16：NopBytecodeMainTest 14 个 `@Test` 全为功能断言，`openCount|JarFile` 0 命中——「不会红」成立。
  - #7：interrupt 1 命中 = SystemRgDelegate.java:47 catch(InterruptedException) 恢复性 interrupt（清理性）；`@Timeout(60)` = ParallelFileWalkerTest.java:122 钉符号环终止——「不会红」成立。
  - #11/#12：nop-rg-benchmark 无 src/test 目录、RgCompareBenchmark/HotspotProfiler/CorpusUtil 测试源 0 引用——「不会红」成立。
  - #13：GlobMatcherTest.java:144/:185 两处 createTempDirectory、全文件无 @TempDir/delete 配对——「不会红（本体无保护概念）」成立。
  - 部分 2 行相邻钉重演：#2 ParallelFileWalkerTest.java:95-109 仅单一锁定目录、仅断言抛 NopRgException（无消息/胜负断言）；#8 JfrSwitchTest 3 测试（:50/:105/:130）钉正常+搜索错误路径 dump 存在且可解析，SIGINT/SIGTERM/addShutdownHook 0 命中——均成立。
- **数字核对**：会红 0 / 部分 7（#2、8、9、14、18、19、20）/ 不会红 13 = 20 ✓；缺保护登记 10 项（P2-01..04 + AG-P3-01..06）结构一致 ✓；附证复现：getName=4（JFR 事件类型 ×3 + 类名 ×1）、时间断言 3 处（JfrSwitchTest.java:58-66 满载循环 / ParallelFileWalkerTest.java:122 / ResourceAnalysisTerminationTest.java:75）均非性能阈值 ✓。
- **修正记录（2 处，均已当场改正）**：
  1. `truncat` 命中数 11 → 14（§一 #3 行、§三交叉验证行）：分类结论（「扫描中文件截断」0 用例）不受影响，#3 判定维持。
  2. §二「P3 聚合覆盖 16 行」不实：AG-P3-01..06 命名覆盖仅 14 行，C4-003 随 P2-01 登记（15/16），**C6-R2-001 写侧原子性缺口未入任何组**——已在 §二 AG-P3-06 后补记缺保护建议（随 C6-R2-001 修复工单补写中断回归用例，先红后绿），§二节标题与 §三汇总行同步更正。
- **整体判定**：**通过**——20 行保护力判定经抽样重演全部成立，2 处数字/覆盖登记错误已修正；本复核未修改任何产品代码与 C1/C4/C6/C9 四维审计文件。
