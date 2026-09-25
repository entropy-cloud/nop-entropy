# nop-lint 性能基线（perf baseline）

> 日期: 2026-09-21 · 依据: ai-dev/plans/nop-lint/05-jmh-benchmark-baseline.md（roadmap item 13）
> 性质: 单机单次测量锚点，非统计严格结论；一切后续性能声称以本文数字为对照基线

## 环境

| 项 | 值 |
|---|---|
| JDK | OpenJDK 26.0.1 Zulu 26.30+11-CRaC-CA（arm64） |
| OS | macOS (darwin 25.6.0, Apple Silicon) |
| JMH | 1.33（fork 1，warmup 3×1s，measurement 5×1s，gc profiler） |
| ast-grep | 0.43.0（Homebrew，Rust 实现） |
| 语料 | 固定内嵌 Java 编译单元（~60 行，`BenchCorpus.JAVA_SOURCE` = `bench/corpus/OrderService.java`） |

## 基准结果（JMH，avgt，5 iterations）

| Benchmark | Score | 说明 |
|---|---|---|
| `compileRuleSet` | ≈ 0.1 ms/op | 3 条旗舰规则编译（规则加载冷路径；注：类声明规则在语料中零命中，其遍历成本仍计入） |
| `matchAllPatterns` | ≈ 1 ms/op（±0.001） | 已编译规则集 ×1 棵语法树全量匹配（热路径） |
| `parseAndMatch` | ≈ 1 ms/op（±0.001） | parse + 3 规则匹配（端到端单文件口径） |
| `parseAndMatch` alloc | 1.30 MB/op（分配速率 644 ± 483 MB/s，高噪声指标） | 门面子树包装与 children 列表物化 |

口径提示：1s measurement 下 ±0.001s 的误差粒度较粗，数字为量级锚点；复测时建议加大 iterations。

**目标口径对照（design 11 §6）**：编辑器 fast profile 预算 <50ms/文件——当前端到端 ≈1ms/文件，余量充足（~50×）。CI 分钟级口径同样满足。

## ast-grep 同规则对比

| 工具 | 口径 | 200 文件耗时 |
|---|---|---|
| nop-lint | warm JVM，parse+3 规则匹配/文件（`parseAndMatch` ×200 换算） | ≈ 0.2 s |
| ast-grep | 冷进程 + 规则加载 + 扫描 + 输出（`/usr/bin/time`） | ≈ 0.01 s（real 0.01） |

**口径不对称声明**：sg 计时含进程启动/规则解析/输出；nop-lint 为稳态 warm JVM 单文件口径，未含 JVM 启动（CI 场景应按进程口径另行测量）。两口径不可直接判优；本对比的目的：
1. 同一规则在 ast-grep 语义下命中数一致（200 文件 × 2 throws = **400 matches**，nop-lint M1 验收同规则命中结构一致）——**语义对齐验证**；
2. 建立后续（编辑器常驻进程、批量 CI）双口径对比的锚点。

复现：`bash nop-lint/bench/compare-ast-grep.sh`（脚本内置匹配数>0 断言，防零匹配空转计时）。

## JFR 热点（matchAllPatterns，60×1s 稳态录制，4972 ExecutionSample）

录制方式：`-jvmArgsAppend "-XX:StartFlightRecording=settings=profile,dumponexit=true"`（**必须注入 JMH forked JVM**——录制挂在 runner 父进程时采不到基准代码，首次录制即犯此错）。

| 帧 | 占比 |
|---|---|
| `TreeNavigator.locateInto` | 39.98% |
| `TSTreeCursor.resetTo` | 13.33% |
| `TreeNavigator.flattenedIndexOf` | 11.71% |
| `TreeNavigator.isChain` | 10.28% |
| `TSTreeCursor.pushFrame` | 10.28% |
| `SubtreeArena.checkLive` | 7.38% |
| nop-lint 帧（NodeIterator.next 0.86% + collect 0.54% 等） | ≈ 1.5% |

**结论**：≈93% 时间消耗在 nop-treesitter cursor 机制——每次 `LintNode.children()` 经 `node.cursor()` 触发 `resetTo` 全祖先链重建 + 线性子定位（nop-treesitter javadoc 已声明该成本形态）。nop-lint 匹配内核自身占比极低。

**优化候选（记录，不在本 plan 实施）**：
1. `TreeSitterLintNode.children()` 结果按 (tree,id) 缓存或匹配期内复用（消除重复 cursor 重建）；
2. 匹配期单游标下推遍历替代逐节点 children() 物化；
3. 分配热点（1.3MB/op）随 1/2 项自然下降。

复现 JFR：
```bash
CP="nop-lint/nop-lint-core/target/test-classes:nop-lint/nop-lint-core/target/classes:$(cat nop-lint/nop-lint-core/target/bench.cp)"
java -cp "$CP" org.openjdk.jmh.Main LintBenchmarks.matchAllPatterns -wi 5 -w 1s -i 60 -r 1s -f 1 \
  -jvmArgsAppend "-XX:StartFlightRecording=settings=profile,dumponexit=true,filename=$(pwd)/_tmp/matcher.jfr"
jfr view hot-methods _tmp/matcher.jfr
```

## 增注（2026-09-25，plan 08 热路径分配治理）：大语料基建 + 站点级 before/after

**基建**：bench 新增 5 基准（总 9）——`engineLintJavaLarge`（确定性 ~2000 行 Java 语料，120 类，含注释/匹配点）、`engineLintXmlLarge`（~800 混合大小写 tag 的 XNode 文档）、`suppressionScanXmlLarge`（预解析 XML 树上的抑制尾直测，隔离解析成本）、micro `multiCaptureSnapshot`/`sha256Hex`；BenchmarkSmokeTest 防呆数 4→8。JFR 注入路线 = `org.openjdk.jmh.Main` + `-jvmArgsAppend`（不走 run-benchmarks.sh，其 runner 忽略 args）。

**环境**：同 §环境（JDK 26 Zulu arm64 / JMH 1.33 fork 1 / 2026-09-25）。

| Benchmark | before | after | Δ |
|---|---|---|---|
| `engineLintJavaLarge` | 0.180 ± 0.009 s/op · **203.8 MB/op** | 0.172 ± 0.002 s/op · 203.9 MB/op | 时间 -4%（噪声内）；分配持平（物化主导=plan 09 面） |
| `engineLintXmlLarge` | 0.001 s/op · 3.93 MB/op | 0.001 s/op · 3.91 MB/op | 持平 |
| `suppressionScanXmlLarge` | —（新增）| — | **站点直测见下** |
| `multiCaptureSnapshot` | 1192 B/op | 968 B/op | **-18.8%** |
| `sha256Hex`（4KB 输入）| 12416 B/op | 344 B/op | **-97.2%** |
| 既有 4 基准（compileRuleSet/engineLint/matchAllPatterns/parseAndMatch）| 0.002/0.001/0.001 s/op · 158K/1951K/797K/1293K B/op | 同 | 零回归 |

**站点判定（plan 08 Phase 5 按站点判据）**：

1. **collectComments（抑制尾注释判定）**：`regionMatches` 滑窗替代逐节点 `toLowerCase()`。站点直测 `suppressionScanXmlLarge`：**100,984 → 52,942 B/op（-47.6%）**——混合大小写 XML tag 面上每节点 String 分配消除（判据"帧消失或占比减半"以 B/op 减半口径 PASS）；余量 ~53KB 为遍历/wrapper 必要分配（plan 09 面）。R1 审查修正的事实基线：Java 语料 kind 名全小写且 `symbolName` 返回缓存元素，该站点在 Java 语料上 before 即零分配——修复价值在 XML facade/ERROR 恢复节点路径。引擎级 XML JFR 中该帧不可见（解析主导 ~19% lineOfChar），故以站点基准为权威口径。
2. **multiCaptures 快照**：三重拷贝→单次快照（unmodifiableMap 包一次性 per-entry 拷贝）。**-18.8%**——原 plan ≥40% 门槛系误估：外层 `Map.copyOf` 即全部可省项，余量（LinkedHashMap + value List.copyOf）是快照契约（写穿透防护）的必要成本，TestMetaVarEnvSnapshot 三断言钉死。判定 PASS（按修订后语义）并如实记录门槛偏差。
3. **sha256Hex**：`HexFormat` 替代逐字节 `String.format` + clone 原型免 provider 查找。**-97.2%** PASS；FIPS 180-2 黄金值断言保证编码逐字不变（`--cache` 旧工件兼容性由指纹不变保证）。
4. **KindIndex 装箱 / NodeExactEquality 重复 children()**：Java 大语料 before JFR 分配剖析中均不在 top-10（cursor 机制占 ~90%：TSTreeCursor.grow 68.5% 等）——按 plan 预案记 **"before 不可见"** 判定：修复由代码级结构证实（int[] 归并无装箱；每节点对 children 4→2），行为等价性由既有测试背书，分配面改善归 plan 09 的 children 缓存后一并复测。
5. **NodeExactEquality 深度防护**：行为面新增（80 层深链 → NopLintException 而非 StackOverflowError，TestNodeExactEquality 钉死），契约增注见 design 04 §2。

**结论**：全基准无 >10% 时间回归；三个站点直测 PASS（其一门槛如实修正）、一个站点按预案降级判定。大语料 Java 基准证实引擎分配的主导面是 children 物化（203.8 MB/op，cursor 机制 ~90%）——plan 09 的缓存优化的目标量级由此锚定。

## 增注（2026-09-25，plan 09 节点缓存）：children() 按 (tree, id) 缓存——候选 1 落地

**机制**：`TreeCache`（node 包，包私有）由 `LintTree` 持有并传入其派生的每个 wrapper——children/namedChildren 列表与 wrapper 按 arena node id 各物化一次（`ConcurrentHashMap` + putIfAbsent/computeIfAbsent）。生命周期 = tree 生命周期（实例字段，无任何 static/GC-root 路径——plan 09 R1 F1 裁定否决静态 WeakHashMap 方案：value-holds-key 结构性泄漏）。facade（XNode）路径零感知。契约增注见 design 03（含 alias 完备性、LSP 驻留内存权衡）。

**JMH before/after（plan 08 的 after 即本 plan 的 before，同口径）**：

| Benchmark | before | after | Δ |
|---|---|---|---|
| `matchAllPatterns` | 797 KB/op · 0.001 s/op | **19.4 KB/op · ≈10⁻⁵ s/op** | 分配 **-97.6%**，时间 ~百倍（口径已达测量下限） |
| `engineLint` | 1.95 MB/op · 0.002 s/op | 1.04 MB/op · 0.001 s/op | 分配 -46.6% |
| `parseAndMatch` | 1.29 MB/op | 855 KB/op | **-33.8%**（门槛 ≤1.0 MB 达标） |
| `engineLintJavaLarge` | 203.9 MB/op · 0.180 s/op | **59.2 MB/op · 0.051 s/op** | **-71%/-72%**（2000 行文件 3.5×） |
| `engineLintXmlLarge` | 3.91 MB/op | 3.91 MB/op | 持平（facade 路径不经此缓存，符合预期） |
| **真规则库** `graphqlCheckSource`（62 规则）| 42.593 ± 1.171 ms/op · 46.3 MB/op（item 43 锚点）| **6.642 ± 0.166 ms/op · 9.93 MB/op** | **时间 -84.4%、分配 -78.6%**——62× children 乘数收敛的直接证据。**归属注记（closure audit R2）**：该锚点采于 plan 08 落地前，故此 Δ 为 plan 08（抑制尾等站点修复）+ plan 09（children 缓存）的**累计**效果；children 乘数的单独贡献由 LintBenchmarks 矩阵（engineLintJavaLarge -71% 等）钉死 |

**JFR（engineLintJavaLarge，30×1s）**：cursor 六帧（TreeNavigator.* / TSTreeCursor.* / SubtreeArena.checkLive）占比 before ~87%（2810 样本）→ after ~62%（1887 样本）；**归一样本成本（每 op cursor 样本）16.9 → 3.0，-82%**——占比读数因 op 时长缩短需归一化才可比，如实记录。剩余 cursor 成本 = 每树首次物化（必要一次）+ 解析（Lexer.findTransition 16.6%）。

**判定**：四个量化门槛全部达标；候选 2（单游标下推遍历）**归 Deferred**——首物化后 cursor 成本已非主导（NodeIterator.next 0.74%→2.27% 但绝对成本随 -72% 时间而降），复杂度不划算。

**警示记录**：graphql 基准曾因 .m2 中旧版 nop-lint-core jar（未 install）测得虚假持平（42.4ms）；install 后复测得 -84.4%。跨模块 JMH 基准测量前必须 install 最新依赖模块。

## 增注（2026-09-25，plan 10 编译复用）：预编译入口——全库编译成本消除

**机制**：`LintEngine.lintCompiled`（预编译规则集入口）+ 全消费方迁移（CheckRunner/LSP/GraphQL/FixApplier/RuleTestRunner）；TypeQuerySupport 从编译期捕获改为求值期注入（`ConstraintContext` 携带）；`CompiledRule` 携带 raw requires tokens 保持 gate 四类 token 可观察等价；复合 matcher 编译与 kind 观点推导收敛为单次下降（`CompiledNode`，UtilRegistry opinion 编译期一次产出）——同一 pattern 文本不再编译两次。

**基准（新增 `FullLibraryBenchmark`，nop-lint-graphql 测试域——生产 YAML 在该 classpath；59 条 java 生产规则 + ~50 行源码；同口径同 run 对照，差值即每文件编译成本）**：

| Benchmark | Score | alloc |
|---|---|---|
| `fullLibraryCompilePerCall`（历史面：每 lint 重编译全库）| 0.006 ± 0.001 s/op | 10.73 MB/op |
| `fullLibraryPrecompiled`（预编译面：gate+匹配+抑制尾）| **0.003 ± 0.001 s/op** | **3.82 MB/op** |
| **差值（每文件编译成本）** | **~-48% / ~3ms** | **~-64% / ~6.9MB** |

**既有基准对照（plan 10 合入前后，口径同 plan 08/09）**：matchAllPatterns 19.4KB/op、engineLint 1.04MB/op、parseAndMatch 855KB/op、engineLintJavaLarge 59.2MB/0.051s、graphqlCheckSource 6.6ms——全部零回归。规划期"数量级级"的预期已由 plan 09 提前兑现大半（遍历乘数），编译复用是剩余乘数的实测 -48%（时间口径），Purpose 措辞按 R1 F2 修订为准。

**JFR（`fullLibraryPrecompiled`，30×1s，_tmp/full-library-precompiled.jfr）**：`NodeIterator.next` 22.5%（children 缓存命中后的廉价迭代——缓存真实生效的反向证据）、`Lexer.findTransition` 13.2%（解析）、`TreeNavigator.logicalParent` 10.9%（关系匹配器 parent 攀升——未缓存路径，见 plan 09 Deferred）、`SourcePattern.mayMatchKind` 6.2%（kind 预过滤）；编译相关帧（GLR pattern parse、XNode）退出热点榜。录制与复现经 `nop-lint/bench/run-graphql-benchmarks.sh`（新增，含 install 前置警示）。

**警示（延续）**：跨模块 JMH 前必须 install 最新依赖模块——本次测量本身曾因 .m2 旧 jar 踩坑一次（见 plan 09 增注）。

## 全量复现

```bash
bash nop-lint/bench/run-benchmarks.sh     # JMH 四基准 → _tmp/lint-bench-result.txt
bash nop-lint/bench/compare-ast-grep.sh   # ast-grep 同规则对比
./mvnw -pl nop-lint/nop-lint-core test    # BenchmarkSmokeTest 防基准腐坏
```

## 增注（2026-09-24，roadmap item 31 plan 2026-09-24-0900-1 Phase 3）：引擎级基准 + 预算机制零回归

**背景**：item 31 在引擎层（`LintEngine.lint`/`RuleSetRunner.run`）新增单文件预算、降级阶梯、pattern 熔断与 fast 时间片。原有三基准全部工作在 pattern 层、不经过引擎（plan R1 Major 6），故新增 `engineLint` 基准（gate → kind 过滤 → 匹配 → 预算边界检查 → 抑制尾 → stats，bench 语料 + 3 条旗舰规则，STANDARD 档）作为引擎层性能门禁，并在改动合入前后各测一次。

**环境**：同 §环境（JDK 26 Zulu arm64 / JMH 1.33 fork 1 / 2026-09-24）。

| Benchmark | before（Phase 2 合入前，worktree @44ed78f9b2） | after（Phase 2 合入后） | 结论 |
|---|---|---|---|
| `engineLint`（新增） | 0.002 ± 0.001 s/op | 0.002 ± 0.001 s/op | 零回归（同精度下不可区分） |
| `compileRuleSet` | ≈ 10⁻⁴ s/op | ≈ 10⁻⁴ s/op | 不变 |
| `matchAllPatterns` | 0.001 ± 0.001 s/op | 0.001 ± 0.001 s/op | 不变 |
| `parseAndMatch` | 0.001 ± 0.001 s/op | 0.001 ± 0.001 s/op | 不变 |

**JFR 热点复核（engineLint，60×1s 稳态录制，`_tmp/engine-item31.jfr`）**：热点仍全部在 nop-treesitter 解析/cursor 层（`TreeNavigator.locateInto` 31.3%、`Lexer.findTransition` 13.1%、`TSTreeCursor.resetTo` 9.3% 等）；`io.nop.lint` 帧仅 `NodeIterator.next` 0.47% 入榜，**`FileBudget`/预算检查帧未进入热点榜**——预算机制的时钟检查（规则边界 1 次 + 匹配起止各 1 次 + xscript/fix 决策点）在 ~2ms/op 口径下不可见，与 before/after 数字一致。无回归，未触发"回归则消除"分支。

**与预算口径的对照（design 11 §2/§6）**：engineLint ≈2ms/文件对 fast 档 20ms 预算余量 ~10×、对 standard 500ms 余量 ~250×；fast 档 10ms xscript 时间片在正常语料下不会被耗尽（xscript 规则缺席时时间片零消耗）。

## 增注（2026-09-24，roadmap item 32 plan 2026-09-24-1000-1 Phase 3）：metrics 绑定零回归

item 32 在 xscript per-match 注入路径新增 `MetricsFunctions` 绑定（仅当 run 装配了 MetricsResolver 时注入，demo/生产规则不在 bench 规则集内）。after 复测：`engineLint` 0.002 ± 0.001 s/op、`parseAndMatch` 0.001 ± 0.001 s/op——与 §增注（item 31）锚点一致，零回归；JFR 未录制（无回归触发分支，裁定按 item 31 纪律记录于此）。

## 增注（2026-09-24，roadmap item 33 plan 2026-09-24-1130-1 Phase 3）：scope 绑定零回归

item 33 在 xscript per-match 注入路径新增 `ScopeFunctions` 绑定（仅当 run 装配了 ScopeResolver 时注入）。after 复测：`engineLint` 0.001 ± 0.001 s/op——对 item 31/32 锚点零回归；JFR 未录制（无回归触发，裁定同前）。

## 增注（2026-09-24，roadmap item 34 plan 2026-09-24-1300-1 Phase 3）：L3/L4 绑定 + 穿透收敛零回归

item 34 新增 `SemanticFunctions`/`DataflowFunctions` 绑定（per-match 注入，仅当 run 装配对应 resolver）并将穿透面收敛为 `DeepResolvers` record（消 resolverReady 重载膨胀）。after 复测：`engineLint` 0.002 ± 0.001 s/op——对 item 31–33 锚点零回归；JFR 未录制（无回归触发，裁定同前）。

## 增注（2026-09-24，roadmap item 35 plan 2026-09-24-1400-1 Phase 3）：规则库 18→48 零回归

item 35 将生产规则库扩至 48 条（其中 7 条 deep 档），match 面规则数变化不影响基准口径（三基准的规则集固定为 3 条旗舰规则）。after 复测：`parseAndMatch` 0.001 ± 0.001 s/op——对锚点零回归；JFR 未录制（无回归触发，裁定同前）。48 条规则库的 CLI 全量口径随 item 40 的迁移映射一并考量。

## 后续裁定记录：规则加载口径是否补 JMH 基准（2026-09-21，plan "LintEngine 最小引擎" Phase 3 Follow-up）

**结论：不补**（裁定动作在本条完成；是否实施由后续证据触发，非本 plan 遗留工作）。

- 理由 1（成本结构）：规则加载（xdef 校验 + `RuleDslParser`）是**每进程一次性**成本；design 11 §4 已把 CompiledRule 序列化缓存列为"有性能证据后按需立项"。基线体系（本文件 §基准结果/§ast-grep 对比）覆盖的是管线热路径（匹配 μs–ms/规则/文件），加载口径与之不同量级。
- 理由 2（无消费方证据）：当前无 CLI/插件常驻场景（item 18 最小 CLI 未落地），规则集规模以个位数计；即使 100 条规则加载耗时 10ms，相对 fast 档 20ms/文件软预算在摊销后可忽略。现在测量没有消费方校准，存在优化错对象的风险。
- 复核触发条件：item 18 CLI 落地且出现"加载占启动时长主导"的实测证据，或 watch/常驻模式引入频繁重载。届时以本文件 §基准结果 同口径（JMH avgt，5 iterations）补 `RuleLoading` 基准。
- 分类：`optimization candidate`（watch-only residual），Why Not Blocking Closure：一次性加载成本无消费方证据，不影响已建立的匹配基线成立。

## 增注（2026-09-24，roadmap item 36 plan 2026-09-24-2300-1 Phase 3）：62 条库 + manifest 对账零回归

item 36 将生产规则库扩至 62 条并完成 coverage manifest 对账（tier 1: 4→33；全部为规则资源与 manifest 元数据变化，引擎代码零改动）。基准口径不变（3 条旗舰规则集）。after 复测：`engineLint` 0.002 ± 0.001 s/op、`parseAndMatch` 0.001 ± 0.001 s/op——对 item 31–35 锚点零回归；JFR 未录制（无回归触发，裁定同前）。

## 增注（2026-09-24，roadmap item 41 plan 2026-09-24-2330-1 Phase 1）：LSP 服务器零回归

item 41 新增 LSP 编辑器面（NopLintLanguageServer 传输无关核心 + stdio launcher；fast 档钉死 + didChange 增量解析）。引擎代码零改动（LSP 面为新增消费方）。after 复测：`engineLint` 0.002 ± 0.001 s/op、`parseAndMatch` 0.001 ± 0.001 s/op——对 item 31–36 锚点零回归；JFR 未录制（无回归触发，裁定同前）。

## 增注（2026-09-24，roadmap item 43 plan 2026-09-25-0030-1 Phase 2）：GraphQL fast 档基准 + --cache

`GraphQlLintBenchmark.graphqlCheckSource`（nop-lint-graphql test 作用域，JMH 1.33，fork 1 / warmup 3×1s / measurement 5×1s；口径 = biz 方法直调真规则库真引擎——GraphQLEngine 查询解析面为薄包装不入测量窗；fork JVM 内 @Setup 完成 CoreInitialization + 懒单例预热）：**42.593 ± 1.171 ms/op**——对照 design 11 §6 的 <100ms 预算，~2.3× 余量，达标（引擎单例暖态为主要贡献）。同 item 的 CLI `--cache` 结果缓存为 CI 冷启动优化（design 11 §4），不改变基准口径。
