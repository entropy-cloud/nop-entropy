# WI 6.1 nop-tools-parallel 搜索/FFM/聚合/读取束（G85，R4-30）设计裁定

> Status: draft
> Last Reviewed: 2026-11-12
> Source: `ai-dev/audits/2026-10/2026-10-22-1400-deep-audit-nop-tools-parallel-concurrency/`（merge-adjudication §二 W-01/W-03/W-05/W-14 + §四#2/#3 家族判定；C10-test-protection §二 P2-01..04；live 取证 fresh-session @ 2026-11-12，分支 concurrency-audit-remediation）
> 批次: roadmap 8.70 行（R4-30，G85 四工单：6.1-W-01、W-03、W-05、W-14）；批次约束 `ai-dev/audits/cra-index.md:470`（P2-G85 行：先红后绿 P2-01/02/03；W-03=R27 双机实测复现；W-14 对照 collectJar 正确形态 BG-5；C10 缺保护 P2-01~04 随本体验收前置）

## 〇、Live 取证（2026-11-12 逐条核对）

### W-01 patternBytes 惰性双写窗（在位）

载体：`nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/coordinator/SearchCommand.java`

| 面 | 锚点 | 现状 |
|---|---|---|
| 唯一非 final 字段 | `SearchCommand.java:19` | `private byte[] patternBytes;`（惰性缓存，其余 8 字段全 final，globs 经 `List.copyOf` :46 防御） |
| check-then-act 双写窗 | `:63-68` | `patternBytes()` 内 `if (patternBytes == null) patternBytes = pattern.getBytes(UTF_8)`（:64-66）+ 返回 `patternBytes.clone()`（:67）——非同步、非 volatile、字段非 final：并发调用既有重复编码（良性）也有 JMM 未安全发布窗（读线程可见引用但见数组默认值，理论面） |
| 调用点（3 处，全部每命令主线程单次） | `SearchCoordinator.java:98`（LITERAL：`PreparedLiteral.compile(command.patternBytes(), ...)`）、`:186`（分块路径 `byte[] pattern = command.patternBytes()`）、`:245`（VECTOR：`provider.compile(command.patternBytes(), ...)`） | 与审计 :96-98/:186/:245 一致 |
| FindCommand 对照（全 final） | `nop-tools/nop-find/nop-find-core/src/main/java/io/nop/find/core/model/FindCommand.java:16-22` | 5 字段全 final，builder 构造、构造器内快速失败（:24-33）；无任何惰性派生缓存——对照形态成立 |

### W-03 FFM 映射存活期 × 外部截断 TOCTOU（在位）

载体：`nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/io/MappedFileReader.java` + `SearchCoordinator.java`

| 面 | 锚点 | 现状 |
|---|---|---|
| 映射构造 | `MappedFileReader.java:31-35` | `Arena.ofConfined()` + `channel.map(READ_ONLY, 0, size, arena)`；try-with-resources 存活期 = Arena 关闭前 |
| 整文件路径窗口 | `SearchCoordinator.java:163-178`（searchFile try-with-resources：isBinary 扫描 :166 → spans :170-172 → aggregate :177 全程持有 segment） | 外部截断落在窗口内任意点即触 fault |
| 分块路径窗口 | `ChunkedFileReader.java:81`（每块 `channel.map(READ_ONLY, viewStart, viewLen, arena)`）+ `SearchCoordinator.java:221-223`（行提取**二次**整文件映射，与已关闭的分块映射共存） | 双窗口 |
| 陈旧 spans 二次映射变体（W-05 失效面 8） | `SearchCoordinator.java:217-223` | spans 采集自截断前尺寸的映射；行提取重新 `Files`→map 时文件已缩小 → aggregate 越新 EOF 读 → 确定性 IOOBE（消息同样无文件路径） |
| 错误上下文丢失链 | 任务抛 InternalError → `future.get()` 包 ExecutionException → `SearchCoordinator.java:126-127` `new NopRgException("search task failed", e.getCause())` → CLI `NopRgMain.java:118-121` `System.err.println("nop-rg: " + e)` 只打顶层 toString | `NopRgException.java` 无 toString 覆写（全文 18 行核实）；`NopException.getMessage()`（`nop-kernel/nop-api-core/.../NopException.java:383`）不折入 cause——文件路径只存在于 cause 栈帧，用户不可见 |

### W-05 invokeAll 首错弃全（在位）

载体：`SearchCoordinator.java:113-133`

- `:117-122`：`pool.invokeAll(tasks)` 已等全部任务完成，收集循环首个异常 future 处抛出 → `:129` 已聚合的 `results` 整体丢弃。
- `:126-127`：`ExecutionException` → `NopRgException("search task failed", e.getCause())`——消息无文件路径（路径在各任务内 `:148`/"stat file failed: "+file、`MappedFileReader.java:43`/"map file failed: "+file、`ChunkedFileReader` 构造文案中，仅在 cause 里）。
- CLI（`NopRgMain.java:118-121`）：exit 2 + 零结果输出。
- **FindCoordinator 同型（失效面 9）**：`nop-find-core/.../FindCoordinator.java:148-153`——`invokeAll` 收集循环首个异常 shard 处即抛 `NopFindException("find shard failed", e.getCause())`（:151-153），已完成的 shard 结果全部弃置；与 SearchCoordinator 结构同型（首错弃全 + cause 中无 shard 上下文）。
- 对照已声明契约：`ParallelFileWalker` javadoc :31-32 fail-fast 为**显式声明**契约（walk 发生在任何搜索工作之前，无成果丢弃不对称）——search 侧无对称声明，与 rg「逐文件报错继续、exit 2 反映有错」契约背离（审计裁定采纳对齐方向 (a)）。

### W-14 read-back 逐 artifact 重开 JarFile（在位）

载体：`nop-tools/nop-bytecode/src/main/java/io/nop/bytecode/cli/NopBytecodeMain.java`

- `:121-124`：分析主循环已按 **per-input** 外层组织（`for (CollectInput input : collectInputs)` → `artifactsOfInput` → 内层逐 artifact）。
- `:244`（readBytes 内）：JAR 输入对**每个 artifact** `new JarFile(input.path().toFile())`——O(entries × central-directory)，30k-entry fat jar ≈30s 串行付费。
- 对照正确形态：`ClassArtifactCollector.java:118`（collectJar）每 jar 输入打开 1 次、遍历全部 entry、manifest 命中免读内容——与审计/merge-adjudication 引用逐字一致。
- 读侧相邻钉（冻结面核实）：`NopBytecodeMainTest.java:213`（tamperedSha256BetweenCollectAndReadFailsLoudly）、`:236`（corruptManifestFailsLoudly）在位。

### 历史证据与 C10 缺保护核对

- **`_tmp/c1r2-verify/TruncFaultTest.java` 已不存在**（`find _tmp -name "*Trunc*"` 零命中，目录消失）——merge-adjudication :34/:150 以该脚本为双机实测存证，取证失效（勘正 1）。R27 双机复现义务转由本批载体测试承接。
- C10 P2-01..04 建议形态逐条核实（C10-test-protection.md :44-59），与批次约束映射：P2-01→W-01、P2-02→W-03、P2-03→W-05、P2-04→W-14，全部随本体验收前置。

## 一、设计裁定（四工单 A-D）

1. **裁定 A（W-01 构造器急切编码 + C10-01 缺保护钉）**：采纳 merge-adjudication §二.1 终裁修法——**主构造器（`SearchCommand.java:36-50`）急切编码**：`this.patternBytes = pattern.getBytes(StandardCharsets.UTF_8);` 字段改 `private final byte[] patternBytes;`（:19），删除 `patternBytes()`（:63-68）内 null 检查分支，保留返回 `.clone()`（防御性语义不变；javadoc :61「惰性缓存」措辞同步改写——修后失实，R1-m4）。理由：(a) 与 FindCommand 全 final 对照形态归一（FindCommand.java:16-22 无任何惰性派生缓存）；(b) 3 个调用点均在命令构造后的搜索主线程段，急切编码零额外成本（原本也会编码一次）；(c) final 字段获得 JMM 安全发布保证，消除未发布窗；(d) C4-003 的 volatile 备选被吸收后不再单列——volatile 只消除可见性窗，保留 check-then-act 结构面，不如 final 急切一步到位。**C10-01 缺保护钉（P2-01，先红后绿）**：并发复用等价测试（两线程对同一 SearchCommand 高次数并发 `patternBytes()`，断言返回字节恒等于 `pattern.getBytes(UTF_8)`）随修复落地——采纳急切修法后测试退化为普通等价测试（merge-adjudication §五.3 注记兑现：补强而非改写冲突）；**确定性红证 = 结构钉**：反射断言 `SearchCommand.class.getDeclaredField("patternBytes")` 为 `final`（修前非 final → 红，修后绿），以结构红兑现「先红后绿」而不依赖数据竞争的非确定性观测（jcstress 成本不做，沿 C10 建议）。
2. **裁定 B（W-03 限缩后修法：补错误上下文 + 声明边界；与 W-05 联动不并单）**：采纳 C1-R2-001 复核限缩后的建议 (a)——**不做窗口收窄**（(b) 的逐块重建 Arena 放弃 CL-001 批量 unmap 语义、扫描前 size 复核不能闭合 TOCTOU，均不采纳）。两件套：
   - **错误上下文补齐**：SearchCoordinator 任务边界对 `InternalError`（FFM unsafe fault 的实测转换形态，C1 维度 :205-209 实测记录）补带文件相对路径的包装——包装点在**任务 lambda 内**（`SearchCoordinator.java:109-110` 的 task 体，catch `InternalError` → `new NopRgException("search fault while scanning: " + rel, e)`），使该入口回到「错误携带文件名」的族内一致性（对照 :148/:152-153 既有文案形态）。
   - **边界声明**：`MappedFileReader` 类 javadoc（:12-20 契约段）补一条：「映射存活期内文件被外部截断/变短的行为未定义，段访问可能抛 `InternalError`；调用方（SearchCoordinator 任务边界）负责转换为携带文件路径的 NopRgException」——声明边界而非承诺消除，TOCTOU 窗口本体（searchFile :163-178 / 分块 :221-223 双映射共存）不修（无数据损坏面，只读映射；修复收益/成本比不成立，C1 复核限缩维持）。
   - **与 W-05 的接缝（merge-adjudication :34 明示联动不并单）**：W-05 建立聚合层「单文件错误 → 错误项」通道（裁定 C）；W-03 的 InternalError 包装是**喂给该通道的单点**——实施顺序 W-05 先行（或同 diff），W-03 落地后 InternalError 不再直达 `:126-127` 首错弃全路径，而是作为某文件的错误项进入 stderr 汇总 + exit 2。W-03 修法 = 包装点 + javadoc；W-05 修法 = 聚合策略；两单各自验收、同批联动。
   - **R27 双机实测复现义务**：TruncFaultTest 已消失（勘正 1）——载体测试 `TestMappingTruncationFault` 承接，**两腿确定性形态（R1-M2）**：单元腿=MappedFileReader map 后第二 rw channel truncate→访问越新 EOF→断言 InternalError 已被包装为 NopRgException 且含 rel（assumeTrue 门控=**JDK 版本门**[FFM final 后 fault→InternalError 形态]+POSIX 门[Windows 映射句柄锁 truncate 抛 IOException——R2-m4 与 chmod 腿同款]；**不赌「扫描中外部截断」竞态窗**——8MB 扫描微秒级完成、truncate 线程大概率迟到=绿但零覆盖；SIGBUS 直崩 VM 风险同被规避）；通道腿=确定性注入 RuntimeException 验 W-05 边界转换，两腿合起来闭合链路。**注入通道机制（R2-M1 定死）**：SearchCoordinator 增加包私有测试 seam `taskDecorator`（默认恒等函数，测试注入抛 InternalError / RuntimeException 的装饰器包装每个 task）——确定性把两类异常送进任务边界（coordinator 包装腿可验、变异链可回退）；单元腿只验 MappedFileReader 层 InternalError 形态本身。**条件红红证形态**：门控通过的 JDK 上修前单元腿断言「错误含 rel」红（现状顶层消息无路径）、通道腿红（首错弃全）；门控未过时 assumeTrue skip 日志留证豁免。
3. **裁定 C（W-05 聚合策略：对齐 rg「报错继续」——部分成功语义设计）**：采纳审计建议 (a)（merge-adjudication :36「建议 (a) 对齐 rg」）——**首错不弃全，部分结果 + 错误聚合返回**：
   - **结果模型**：新增载体类型 `SearchOutcome`（nop-rg-core，record：`Map<String, FileMatches> results()` + `List<FileError> errors()`；`FileError` record：`String relativePath()` + `String message()`）。`SearchCoordinator.search(SearchCommand)` 签名**改为返回 `SearchOutcome`**（nop-rg-core 公共类、非 `nop-*-api` 叶，不触 Protected Area；全仓调用点 = nop-rg-cli `NopRgMain.search` :145（R1-m7 勘正，:113 为 finally 行）+ benchmark + 测试，同批改写；另 NopRgMainTest:203 匿名类覆写 search(Path) 的返回类型须同步改——覆写面登记 R2-m3）。
   - **任务边界形态**：`:109-110` task 体改为「try { 搜索 } catch (NopRgException | InternalError | RuntimeException e) → 返回 FileError 项」（InternalError 腿即 W-03 包装点；catch RuntimeException 覆盖失效面 8 的确定性 IOOBE——越新 EOF 的 IndexOutOfBoundsException 转为该文件错误项，不再穿透弃全）。任务 lambda 返回载体改 task 级联合 record（正常 FileMatches / FileError——形态留实现评审，R2-m5）；收集循环（:117-122）改为：FileError 项入 errors、正常项入 results——**任何单文件失败不再中断收集**。
   - **rg 对齐语义钉**：部分结果照常输出（ResultPrinter 不变）；有 errors 时 CLI stderr 逐条 `nop-rg: error scanning {rel}: {message}`、**exit 2**（保持 plan 2276 G2 裸 NopException → exit 2 契约兼容：`NopRgMain.call` :118-121 改为消费 `SearchOutcome.errors()` 决定退出码，异常路径仅保留 coordinator 级失败如 walker fail-fast 穿透）。无 errors 时维持既有 0/1 语义不变。
   - **9 项失效面逐一覆盖清单**（聚合边界单点 catch 统一覆盖，非逐站修）：
     | # | 站点 | 覆盖方式 |
     |---|---|---|
     | 1 | `searchFile:146-149` Files.size IOException | 任务边界 catch → FileError(rel) |
     | 2 | `searchFile:163` MappedFileReader 构造（NopRgException "map file failed: "+file） | 同上（消息已带路径，直接入项） |
     | 3 | `searchFileChunked:189-190` ChunkedFileReader 构造 + `ChunkedFileReader.java:81` nextChunk map | 同上 |
     | 4 | `searchFileChunked:221` 行提取二次 MappedFileReader | 同上 |
     | 5 | （原 C4 文件枚举 :93 判 FindCoordinator N/A——被 merge-adjudication 9 项枚举取代，见失效面 9） | — |
     | 6 | ParallelFileWalker walk 阶段 fail-fast | **不覆盖（维持已声明契约）**——javadoc :31-32 显式声明、发生在任何搜索工作之前，冻结面；walk 异常仍走 coordinator 级失败路径 |
     | 7 | RegexSearcher/MatchAggregator/LineCursor 纯内存 | 无独立 I/O 源，N/A |
     | 8 | 陈旧 spans 二次映射确定性 IOOBE（:217-223） | 任务边界 catch RuntimeException → FileError(rel)（IOOBE 消息无路径，FileError 以 rel 补齐） |
     | 9 | FindCoordinator shard 聚合同型（FindCoordinator.java:148-153） | **随本工单统一**（merge-adjudication :36 裁定「随聚合策略统一」）：`invokeAll` 收集循环改同型「错误聚合、全部 shard 跑完再处置」——shard 失败收集（含 shard 标识），全完成后若有错抛 `NopFindException` 汇总全部 shard 失败（含各 shard 下标/候选区间上下文），无错照常归并。不引入 partial 结果返回（find 语义是 top-K 全序，部分 shard 缺席会破坏「候选全量打分」确定性契约——FindCommand javadoc :33-34 index 确定性 tie-break 冻结面；与 search 的差异在find 的 shard 是全量必要输入而 search 的文件是独立可失败单元，**形态统一在「不弃已完成工作+错误携带上下文」层，返回语义按单元契约分立**） |
   - **C10-03 缺保护钉（P2-03，先红后绿）**：双文件树 + 其一 `chmod ---------`（任务内 stat/map 确定性失败）→ 断言另一文件结果照常返回 + 错误项含文件路径。修前现行为抛异常零结果 → 载体测试红（确定性红，兑现先红后绿；chmod 腿 assumeTrue 非 root+POSIX——root 下 chmod 000 不产生 PermissionDenied，R1-m3）。**既有测试语义迁移登记（R1-M1）**：`SearchCoordinatorTest.testRegexOnFileAboveChunkedThresholdFailsExplicitly:243-256` assertThrows(NopRgException) 修后必红（异常源在任务 lambda 内）→ 改写为「该文件入 errors 含路径、其余结果照常」（语义强化非弱化）。**收集循环兜底腿（R1-m1）**：改写后的收集循环保留 ExecutionException→FileError 兜底腿，防 checked 异常仍弃全。**InternalError message null 约束（R1-m2）**：裸 InternalError.getMessage 常为 null，W-05 载体 InternalError 腿断言只断 relativePath 不断 message。
4. **裁定 D（W-14 read-back 对齐 collectJar 形态）**：在既有 per-input 外层循环（`NopBytecodeMain.java:121-124`）内为 JAR 输入**打开一次 `JarFile`**，下沉为 readBytes 的取数通道；目录输入保持逐文件 `readAllBytes`（每文件 1 open 已是下限）。具体形态：新增包私有取数接口 `ClassByteSource`（`byte[] read(String relative)`，close 释放底层 JarFile），per-input 构造一次，**close 点=外层 per-input 循环内 artifact 内层循环结束后 try-with-resources 关闭（per-input 一次，R1-m5）**——JAR 分支 `new JarFile` 包入 lambda 实现；DIRECTORY 分支 path.resolve 实现；`readBytes(CollectInput, Path, ClassArtifact)`（:235-261）改收 `ClassByteSource` 参数，**sha256 对照断言（:256-259 TOCTOU loud 检查）逐字保留**。对照实现即 `ClassArtifactCollector.collectJar:118`（每 jar 1 次打开遍历全部 entry）。**C10-04 缺保护钉（P2-04；seam 形态 R1-B1 定形）**：NopBytecodeMain 为 public final+全 static 管线，「包私有可覆写工厂方法」不可实现——改为**注入式 jarOpener**：`run` 增加重载收 `Function<Path, JarFile>` 参数（现有 `run()` 委托默认真实打开工厂），载体测试注入计数包装工厂断言「read-back 阶段每 jar 输入至多 open 1 次」（修前 N entries 次 → 红）；JMH smoke（forks(0)+1 iteration，对标既有 BenchmarkSmokeTest）与 BG-5 基准缺口**合并排期**（cra-index G85 约束「W-14 对照 collectJar 正确形态（BG-5）」——形态对照本批兑现，量化基准随 BG-5 排期不阻塞本单验收）。

## 二、载体表

| 工单 | 载体 | 模块叶 |
|---|---|---|
| W-01 | `TestSearchCommandImmutability`（新建，nop-rg-core 包 io.nop.rg.core.coordinator：结构钉[反射断言 patternBytes 字段 final] + 并发复用等价腿 + clone 防御腿） | nop-tools/nop-rg/nop-rg-core |
| W-03 | `TestMappingTruncationFault`（新建，同包：整文件映射截断腿[assumeTrue 门控 + 断言 NopRgException 消息含 rel 路径] + 分块二次映射 IOOBE 变体参数化腿——即 R27 复现载体，双机各跑一次留证） | nop-tools/nop-rg/nop-rg-core |
| W-05 | `TestSearchPartialFailure`（新建，同包：chmod 000 单文件腿[部分结果返回 + 错误项含路径] + IOOBE 变体归错误项腿 + walker fail-fast 穿透腿）+ `NopRgMainTest` 扩展（nop-rg-cli：stderr 逐条 + exit 2 腿）+ `TestFindCoordinatorShardFailure`（新建，nop-find-core：shard 失败汇总含 shard 上下文腿） | nop-rg-core + nop-rg-cli + nop-find-core |
| W-14 | `NopBytecodeMainTest` 扩展（openJar 计数 seam ≤1 腿 + 读侧 loud 钉保持绿） | nop-tools/nop-bytecode |

## 三、变异红证计划（每工单 ≥3 链，先红后绿验收）

- **W-01**：红0（结构红，修前）——反射 final 断言对现状红；链1 回退惰性双写（字段去 final + 恢复 null 检查）→ 结构钉红；链2 删 `patternBytes()` 返回 clone → 防御腿（修改返回数组后再次调用不受影响断言）红；链3 急切编码改错值（如 ASCII 编码替 UTF-8）→ 并发等价腿红。
- **W-03**：红0（修前，assumeTrue 通过的 JDK 上）——截断腿断言「NopRgException 消息含 rel」对现状红（现状顶层消息 "search task failed" 无路径）；链1 删 InternalError 包装 → 截断腿红；链2 包装消息丢 rel（改为固定文案）→ contains(rel) 断言红；链3 IOOBE 变体腿去掉 RuntimeException 归项（改 rethrow）→ 腿红（联动验 W-05 通道）。
- **W-05**：红0（修前）——chmod 腿断言「另一文件结果照常返回」对现状红（现状整体抛异常）；链1 收集循环恢复首错即抛 → 部分结果腿红；链2 FileError 丢 relativePath → 错误项含路径断言红；链3 CLI 有 errors 改 exit 0 → NopRgMainTest exit 腿红；链4 FindCoordinator 恢复首 shard 即抛（:151-153 还原）→ shard 汇总腿红（统一钉）。
- **W-14**：红0（修前）——计数 seam 断言「read-back 每 jar ≤1 open」对现状红（= entries 数）；链1 readBytes 内恢复 per-artifact `new JarFile` → 计数腿红；链2 删 readBytes sha256 对照 → `tamperedSha256BetweenCollectAndReadFailsLoudly`（:213）红（冻结面反证）；链3 JAR 分支误伤 DIRECTORY（统一走 JarFile）→ 目录输入既有 read-back 测试红。

## 四、接缝与回归面

- 硬接缝：① **W-05 → W-03 批内顺序**——W-05 先建「任务错误 → 错误项」聚合通道，W-03 的 InternalError 包装点（任务 lambda）作为该通道的输入落地（或同 diff，禁倒序：倒序时 InternalError 包装仍走 `:126-127` 首错弃全旧路径，W-03 验收语义不成立）；② W-05 与 W-03 **不并单**（merge-adjudication :34 裁定维持——同收敛层不同修法）；③ W-05 覆盖失效面 9 时触碰 nop-find-core（FindCoordinator.java:148-153 一站）——跨模块小站随单登记，不新立工单；④ W-01、W-14 与其他两单零耦合，可并行。
- `SearchCoordinator.search` 返回类型变更是 nop-rg-core 公共面（非 api 叶、非 Protected）——同批改写全部调用点（nop-rg-cli / benchmark / 测试），benchmark 面如编译冲突按「最小改写」原则包一层适配。
- 回归基线（R2-M2 勘正：nop-rg/nop-find 为 packaging=pom aggregator，-pl 选 aggregator 不递归=空转）：`./mvnw test -pl nop-tools/nop-rg/nop-rg-core,nop-tools/nop-rg/nop-rg-cli,nop-tools/nop-find/nop-find-core,nop-tools/nop-bytecode -am` 逐叶跑；`nop-rg-benchmark`/`nop-find-cli`/`nop-rg-vector` 纳入编译级回归面（E1 全量覆盖）。
- 接缝 seam：`NopBytecodeMain` jarOpener 注入式 seam（run 重载收 Function<Path,JarFile>——final+static 不可覆写，R1-B1；W-14 计数）；`SearchCommand.patternBytes` 字段（反射结构钉，修后为 final——钉的是终态形态）；FindCoordinator shard 错误收集（包私有收集结构留实现评审）。

## 五、审计与 live 勘正（4 处）

1. **TruncFaultTest.java 历史证据已消失**：merge-adjudication :34/:150 以 `_tmp/c1r2-verify/TruncFaultTest.java` 为双机实测存证——live `find _tmp` 零命中，脚本与目录均已不存在（_tmp 按惯例非持久存证位）。R27「双机实测复现」义务**不可依赖旧存证**，转由载体测试 `TestMappingTruncationFault` 承接（双机各执行一次留证于本批 plan 附件）；closure audit 对该实测须按新载体复核而非形式复核旧脚本。
2. **先红后绿清单两处口径不一致（以审计 C10 为权威）**：cra-index P2-G85 行写「先红后绿（P2-01/02/03）」，审计 C10-test-protection.md :89 权威清单为「P2-01、P2-03、AG-P3-01 取消半边」三项——AG-P3-01 属 W-06 取消族（P3 登记级，**不在 G85 四工单内**），P2-02（W-03 截断腿）因依赖 JDK fault 行为天然 assumeTrue 门控、只能做条件红。本批执行口径：P2-01（结构红）与 P2-03（确定性红）强制先红后绿；P2-02 条件红（门控 JDK 上红转绿）；AG-P3-01 不随本批。cra-index 行文字建议在批次收敛注勘正。
3. **W-05 失效面枚举版本演化**：C4 维度文件 :88-93 枚举 7 项且第 5 项判 FindCoordinator N/A（「不读文件内容」）；merge-adjudication :36 终裁为 **9 项**（补充 8：陈旧 spans 二次映射确定性 IOOBE；9：FindCoordinator shard 聚合同型）。live 核实第 9 项成立（FindCoordinator.java:148-153 首错弃全 + cause 无 shard 上下文），第 8 项站点确认（searchFileChunked:217-223）——设计按 9 项权威枚举覆盖（裁定 C 清单表），C4 文件 7 项枚举为被取代的早期版本。
4. **W-03「穿透嵌入方屏障」子句复核限缩的 live 佐证补全**：merge-adjudication :34 删除该子句的依据（SearchCoordinator.java:126-127 已包装为 NopRgException cause）核实成立；补全链路：任务内 InternalError → ExecutionException → :127 包装为 NopRgException（NopException 子类）→ CLI `NopRgMain.java:118-121` catch NopException 兜住——Error 不穿透 CLI 侧屏障成立；可见性缺陷本体在「包装消息 + CLI 打印均不含 cause 中的文件路径」（NopRgException 无 toString 覆写、NopException.getMessage :383 不折入 cause、CLI 只打顶层 toString :120），与限缩后立案面完全一致。

## 六、冻结面（不许回退的既有钉）

- `NopBytecodeMainTest.tamperedSha256BetweenCollectAndReadFailsLoudly:213` / `corruptManifestFailsLoudly:236`——read-back sha256 loud 契约（W-14 改造逐字保留 :256-259 检查）。
- `ParallelFileWalker` javadoc :31-32 fail-fast 已声明契约（W-05 失效面 6 不覆盖——walk 阶段 fail-fast 维持）。
- `SearchCommand.patternBytes()` 防御性克隆语义（javadoc :61「返回克隆」）——W-01 修后保留。
- plan 2276 G2 退出码契约（裸 NopException → exit 2）与 0/1 无错语义——W-05 的 errors → exit 2 是消费既有契约而非改写。
- `FindCommand` 确定性契约（FindCoordinator javadoc :33-34：结果与线程数无关、index tie-break）——W-05 失效面 9 的统一修法不得引入部分 shard 缺席形态。
- `MappedFileReader` 空文件契约（javadoc :18-19，size=0 → getSegment() null）与关闭幂等（closed 旗标 :64-68）——W-03 javadoc 增量不触碰既有契约段语义。
- `ClassArtifactCollector.collectJar:118` 每 jar 单开形态——W-14 的对照基准，本身不在修改面。

## 七、owner doc 缺口声明

nop-rg / nop-find 无 owner doc（merge-adjudication §五.4 单元级缺口，归 owner-doc 治理轨）——本批契约落 `docs-for-ai/04-reference/source-anchors.md` 新锚点行（SearchOutcome 部分成功语义 / MappedFileReader 截断边界声明 / NopBytecodeMain read-back 单开形态）；nop-bytecode.md 存在，W-14 落地时按 SSOT 规则同步补 read-back 条目（发现级缺口同 §五.4）。
