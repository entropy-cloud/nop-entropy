# nop-lint 质量优化深度审计（可读性/设计坏味道/性能）

> Status: resolved
> Date: 2026-09-25
> Scope: nop-lint 全部 6 模块（nop-lint-core/-java/-js/-nop/-graphql/-maven-plugin，约 15k 行主代码 + 62 条生产规则）
> Conclusion: 无 Blocker；确认 Major 19 项、Minor 45 项。按结果面归并为 8 个工作项（plan 07–14）。最高杠杆三项：①每次 lint 调用全量重编译全部规则（CLI/LSP/GraphQL/fix multipass 四条热路径付费）；②抑制尾逐节点 `kind().toLowerCase()` 分配 + `children()` 每次调用 cursor 重建（perf-baseline 93% cursor 热点的直接乘数）；③四处已确认的 live correctness 缺陷（DefUseChain 遮蔽判定、TemplateFix 跨分支捕获 NPE、cache 双读哈希错位、Mojo 基线开关静默丢弃）。性能项全部以 JMH before/after + JFR 热点/分配剖析验证。

## Context

- roadmap items 1–43 全部 done（M1–M6 里程碑关闭），nop-lint 进入质量优化阶段。
- 本审计回答：在不触碰既有设计裁定（TSQuery 冻结、fail-closed 语义、golden 输出字节、平台零改动）的前提下，还有哪些可落地的优化项。
- 方法：3 个独立审计 agent 分片（引擎内核 / DSL+CLI / 语言适配+生态）+ 关键 finding 逐条人工复核 + JMH/JFR 基线实测。
- 审计基线：6 模块 1020 测试全绿（core 757 / java 101 / js 61 / nop 76 / graphql 17 / maven-plugin 8）。

## 基线（JMH，2026-09-25 本机，JDK 26 Zulu arm64，JMH 1.33）

| Benchmark | Score | alloc |
|---|---|---|
| `compileRuleSet` | ≈ 10⁻⁴ s/op | 158 KB/op |
| `engineLint` | 0.002 ± 0.001 s/op | **1.95 MB/op** |
| `matchAllPatterns` | 0.001 ± 0.001 s/op | 797 KB/op |
| `parseAndMatch` | 0.001 ± 0.001 s/op | 1.29 MB/op |

- 引擎路径 1.95 MB/op 此前未分配剖析过；且 `engineLint` 每 op 重编译 3 条规则（见 P1），当前数字含编译成本，规则库扩大后失真。
- 历史 JFR（perf-baseline.md）：~93% 时间在 nop-treesitter cursor 机制（`TreeNavigator.locateInto` 40%、`TSTreeCursor.resetTo` 13%），根因是 `LintNode.children()` 每次调用重建 cursor 祖先链 + 物化列表。perf-baseline 已记录两个未实施候选：children() 缓存、单游标下推遍历。

## 问题分析

严重程度口径：Major = 用户可感知缺陷/数量级性能项/明确设计坏味道；Minor = 局部清理。**发现均已由主审计者逐条读码复核**（标注 ✓ 的为二次核实）。

### 一、性能（热路径）

**P1. 每次 lint 调用全量重编译全部规则 ✓（Major，数量级级）**
- `LintEngine.lint`（engine/LintEngine.java:195）唯一入口接收裸 `List<RuleDslModel>`，循环内逐条 `CompiledRule.compile`。每条 pattern 规则编译含一次真实 tree-sitter 解析（SourcePatternCompiler.compile）。
- 四个消费方全部付费：CLI 每文件（CheckRunner:194）、LSP 每次 didChange、GraphQL 每请求、fix multipass 每轮 ×≤10。62 规则 × 万级文件 = 62 万次 pattern 重解析。
- perf-baseline"规则加载每进程一次性"的裁定被该 API 形状否定；design 03 §1.3 的跨进程序列化缓存不解决进程内问题。
- 连带：复合 matcher 内 pattern 被编译两次（compileNodeMatcher + kindOpinion 各一次，CompiledRule.java:297-299/502/555）；`engineLint` 基准口径含编译。
- 关键设计约束：约束编译在编译期闭包捕获 per-file 的 `TypeQuerySupport`（LintEngine.java:184→finish→Constraints.compile），预编译必须把 per-call 状态改为运行期注入，否则跨文件复用会绑定错误文件。

**P2. 抑制尾逐节点 `toLowerCase()` 分配 ✓（Major）**
- CommentSuppressionScanner.java:195：`node.isExtra() || node.kind().toLowerCase().contains("comment")` 对树上每个节点分配一个新 String；万行文件 ≈ 万次分配/文件，fix multipass ×10 重复。
- 修复方向：先 `isExtra()` 短路 + `regionMatches(ignoreCase)` 无分配判断（或按 kind 小写集合去重）。

**P3. `NodeExactEquality` 每节点 4 次 `children()` ✓（Major）**
- pattern/NodeExactEquality.java:24-31：`a.children().isEmpty() && b.children().isEmpty()` 后又各取一次 = 同一节点对 4 次 cursor 重建（每次正是 93% 热点形态）；递归无深度上界（ReferentMatcher 有 64 层防护，此处不对称）。
- 修复：局部变量化（4→2）+ 深度防护。

**P4. KindIndex 每节点装箱 + `canMatchKinds` O(R×K) 线性扫（Minor）**
- engine/KindIndex.java:23-29 用 `HashSet<Integer>`（>127 的 kindId 每节点一个 Integer）；CompiledRule.canMatchKinds(Iterable<Integer>) 嵌套线性。
- 修复：`BitSet`/boolean[]（kind 值域有限）+ O(1) 判定。

**P5. MetaVarEnv.multiCaptures 三重防御拷贝（Minor）**
- pattern/MetaVarEnv.java:92-101：LinkedHashMap 拷贝→每值 List.copyOf→Map.copyOf 三层；singleCaptures 只有一层，不对称。xscript 每 match 白付两次全量拷贝。

**P6. children() 缓存 / 单游标遍历（Major，perf-baseline 已记录候选，本次认领）**
- TreeSitterLintNode.children()（node/TreeSitterLintNode.java:104-120）每次调用：新建 cursor（祖先链重建）+ ArrayList + List.copyOf + N 个 wrapper 分配；wrapper 本身每次遍历也是新分配，实例级缓存命中率低。
- 需要设计决策：tree 级 (node.id → children) 缓存（TSTree 不可变故安全，但 LSP 长驻需生命周期策略）vs 匹配期单游标下推遍历。NodeIterator/PatternMatcher/scanTree 是主要调用面。
- 分配热点佐证：parseAndMatch 1.29 MB/op。

**P7. 分配/查找杂项（Minor）**
- RuleResultCache.sha256 每字节 `String.format("%02x")`（cli/RuleResultCache.java:198-200）→ `HexFormat`；MessageDigest.getInstance 每诊断一次（BaselineEngine.java:48-54、RuleResultCache）→ 复用/clone 原型。
- Fixer.merge O(n²) 重叠扫（fix/Fixer.java:41-55，诊断数千级时）；StopBy 探测逐节点 env.clone（pattern/StopBy.java:90-101）；FixApplier 每文件多一次全量 parse（errorNode 计数重复解析）+ CLI byte[]→String→byte[] 往返（fix/FixApplier.java:122,149 + LintEngine 无 byte[] 入口）。

### 二、正确性缺陷（live defect，只能 Fix）

**C1. DefUseChain.findVar 用"最大声明行号"近似"最内层作用域" ✓（Major）**
- nop-lint-java/semantic/DefUseChain.java:254-270：同名声明中取行号最大者，但行号大 ≠ 作用域内层。反例（合法 Java）：`{ int x = 2; use(x); } int x = 1;`——外层 x 行号更大且其块作用域（方法块）包含引用点，`use(x)` 被错判为外层 x 的 use；下游 useCount/isSelfAssigned/constantValue 全部张冠李戴。与 javadoc 宣称的 "the innermost declaration" 不符。
- 修复：真正的最内层作用域比较（作用域嵌套深度优先），或复用 ScopeAnalyzer.resolve 的外向逐界走查（正确语义已存在，两套实现并存本身是重复）。

**C2. TemplateFix 多捕获路径缺失 null 防御，跨 any 分支引用触发裸 NPE ✓（Major）**
- fix/TemplateFix.java:126-131：`env.getMultiCapture(name)` 返回 null 时 `nodes.isEmpty()` NPE。根因：CompiledRule.CaptureIndex 把 any 各分支捕获取并集做编译期校验（CompiledRule.java:268,335-336），但每个 match 的 env 只含命中分支的绑定。单捕获路径（133-138）有完整的 fail-closed NopLintException，多捕获路径不对称缺失。NPE 从 fix 渲染点穿透出 lint()，整个文件失败。
- 修复：短期补 null 检查（消息模板现成）；中期按分支携带捕获集或编译期拒绝"模板引用 ∉ 各分支捕获集交集"。

**C3. `--cache` 路径每 miss 文件读盘两次，哈希与 lint 内容可能错位 ✓（Major）**
- cli/CheckRunner.java:187（read#1）→:196（cache.put 用 read#1 字节算哈希）→lintFile 内 :323（read#2 lint）。两次读取间文件被并发修改时，缓存持久化"旧哈希+新诊断"错位条目，下次命中重放与内容不匹配的诊断——与缓存"可重放、fail-closed"契约不符。
- 修复：bytes 单次读取贯穿哈希与 lint（lintWithTrace 本就接收 byte[]）。

**C4. CheckMojo 基线开关在缺 baselineFile 时被静默丢弃 ✓（Major）**
- nop-lint-maven-plugin/CheckMojo.java:240-251：`if (baselineApply && baselineFile != null)`——用户配了开关漏了文件时静默退化为普通 check。baselineApply 是"压制已知告警"的开关，静默失效 = 用户以为被压制的告警其实没压制（或 CI 基线检查没生效还全绿）。
- 修复：开关为 true 而 file 缺失时抛 MojoExecutionException。

**C5. CheckMojo 日志桥逐字节转 char，非 ASCII 消息必然乱码 ✓（Major）**
- CheckMojo.java:304-343：`LogOutputStream.write(int b)` 逐字节 `(char) b` 拼行。PrintStream 按平台 charset 编码字节，中文诊断（UTF-8 3 字节/字）渲染成 3 倍长乱码。用户每次构建可见。
- 修复：改用 `ConsoleReporter.render(outcome, Writer)` 重载，Writer.write(String) → log.info，删字节桥。

**C6. NopLintBizModel.checkFile 先整读后查 cap，cap 对磁盘/VFS 文件不设防 ✓（Major）**
- nop-lint-graphql/NopLintBizModel.java:120-121：`readControlled(path)`（整读入内存）在前、`checkSourceCap` 在后；VFS 分支 `resource.readText` 同样无预检。工作目录内 GB 级文件即可 OOM——cap 的目的恰是防这个。
- 修复：读取前 `Files.size(real)`/resource.length() 对照 cap 拒绝，或有界读取。

**C7. LSP/MatchCommand 扩展名大小写未归一化，违反 TargetScanner 文档化契约（Minor）**
- lsp/NopLintLanguageServer.java:193-197、cli/MatchCommand.java：`name.substring(dot+1)` 未 lowercase 直传 `languageIdForExtension`（契约要求已小写）；`FILE.JAVA` → 查表 miss → didOpen 整体失败。
- 修复：两处补 `toLowerCase(Locale.ROOT)`；顺带删向 NO_EXTENSION 查表的无效兜底。

**C8. NodeTscBridge 进程重启后旧 reader 线程的迟到 EOF 污染新会话 ✓（Major，条件触发）**
- nop-lint-js/tsc/NodeTscBridge.java:195-210/231-243：spawn() 先 destroyProcess（destroyForcibly 异步、不 waitFor、不 join 旧 reader）再 inbound.clear()；旧线程退出时 finally 向共享队列 offer(EOF)——迟到 EOF 被新 spawn 的握手 pollWithDeadline 消费 →"peer exited before the ready frame"误判 → EXHAUSTED 终态烧掉整个桥。
- 修复：按代际隔离队列或帧打 generation 标签；destroyProcess 后 onExit().get(短超时)；reader 线程捕获实际 Process 引用。同族：inbound 无界队列、reader 吞 IOException 无日志、peerInput 不 close。

**C9. JavaSemanticResolver/SemanticAnalyzer 放行裸 UnsolvedSymbolException（Major）**
- JavaSemanticResolver.java:62-64 无 catch；SemanticAnalyzer.java:170-175 主动 throw。两者 javadoc 均宣称由 resolver 层翻译为 NopLintException——L2 路径（JavaTypeResolver.tryResolve）有完整翻译层，L4 不一致，违背 AGENTS.md 两级异常约定。

**C10. 杂项正确性（Minor）**
- RuleResultCache 条目级形状零校验（version 非 Number 时 CCE、hash 命中但 diagnostics 形状错时 NPE/全 null Diagnostic），与"损坏缓存 fail-closed"自述不符（cli/RuleResultCache.java:84-85,120-127）。
- SuiteResult.ruleId 早失败路径填 suiteName，与字段契约"the id of the rule under test"不一致（testing/RuleTestRunner.java:177,186 vs :194,213）。
- relationalStopBy default 分支静默降级为 end（CompiledRule.java:708-709，当前靠 parser 外层校验守护，模块内唯一静默兜底）。
- CheckMojo threadSafe=true 但全局 CoreInitialization init/destroy 在 `mvn -T` 并行 reactor 下不安全（CheckMojo.java:57,157-189）→ execute 期间持全局锁或文档声明不支持 -T。
- ConstantPropagation javadoc 宣称"字符串拼接已折叠"、实现返回表达式原文（ConstantPropagation.java:27-28 vs 156-164）——文档/行为不一致。
- CLI 顶层错误输出 `nop-lint: error: null`（NopLintCli.java:143，getMessage() 为 null 时）。

### 三、设计坏味道与可读性

**D1. resolver 桥接样板 4 类近乎逐行重复 ✓（Major）**
- JavaScopeResolver/JavaMetricsResolver/JavaDataflowResolver/JavaSemanticResolver：CACHE_SIZE=32、匿名 LinkedHashMap LRU（×3 一字不差）、`new JavaParser(ParserConfiguration…JAVA_17)`、parse-orElseThrow、LineColBytes+JavaNodeIndex 装配、line+1/col+1 换算（×2）、isAvailable(){return true;}（×4）。ParsedUnitCache 一项可回收约 200 行。
- 附带：JavaTypeResolver 双缓存与兄弟类设计不一致（无界 HashMap、无同步、queryCache 存 "true"/"false" 字符串，JavaTypeResolver.java:57-58 ✓）；JavaDataflowResolver 缓存按 `filePath+"@"+line` 键控，同文件 50 个位置 = 50 次整读整解析（JavaDataflowResolver.java:84-129 ✓，JavaMetricsResolver 已示范按 filePath 的正确形态）；minimalAt 在 ScopeAnalyzer/DataflowQueries 各有一份 O(N) 全树扫描而 JavaNodeIndex 已有 O(depth) 版（×2 重复）；DataflowQueries.constantValue 每查询构建两遍 DefUseChain（DataflowQueries.java:33-41,125-127）；三处死字段（JavaTypeResolver.typeSolver、JavaSemanticResolver.analyzer、JavaDataflowResolver.queries）。

**D2. CheckRunner 编排方法过载 + 死代码（Major/Minor）**
- run(scan,profile,fixMode,baselineOp,baselineFile,cacheFile) 约 70 行串行 6 件事，前面 5 层 delegating overload；三个 discover* 私有方法零调用（CheckRunner.java:229-239 ✓）。
- 修复：抽 loadValidatedRuleSet/openCacheIfRequested/lintAllFiles，删死方法。

**D3. RuleDslParser XOR 校验五处复制粘贴（Major）**
- rule/RuleDslParser.java:226-234/372-379/464-471/484-491/590-594：同一"present 为空→throw；size>1→throw；取 get(0)"三连问 ×5，消息拼接模式重复十余次。抽 `requireSingleMatcher` 可减 100+ 行。parser 状态机本身（单遍递归下降）结构清晰，**不需要拆类**。
- 附带：RULE_MATCHERS 与 NESTED_MATCHERS 两份相同字面量（:90-96）；csvToList/csvSet 合并点。

**D4. CompiledRule 901 行五块职责混杂（Major，与 P1 同一重构线）**
- 身份/访问器、compile 编排、节点匹配器编译矩阵、kind 观点推导（与编译矩阵平行走同一结构——正是双编译来源）、两个内嵌注册表。抽 `CompositeRuleCompiler`（编译一步产出 matcher+kind 观点，消除双编译）+ UtilRegistry/CaptureIndex 升顶层。

**D5. 输出/报告层重复与不一致（Minor）**
- 匿名 PrintStream→Writer 桥两处逐行等价（NopLintCli.java:168-185 ≡ ConsoleReporter.java:51-66）→ 提取 PrintStreamWriter（golden 字节不变）；CheckRunner.cacheHit 豁免过滤循环与 applyFilters 前半段重复；RunSummary 集合暴露策略不一致（2 个防御拷贝 vs 4 个泄漏内部可变集，RunSummary.java:213-311）→ 统一 unmodifiable 视图。

**D6. 遥测/诊断文本（Minor）**
- XmlRuleCompiler.java:281-284/340-344 与 CompiledRule.java:484-488 错误消息引用已落地的 roadmap item（"until roadmap item 24"/"deferred to roadmap item 22"）——把已成事实说成未来时，误导规则作者 → 改现在时+出口指引。
- Constraints.java:46-67 两个连续 Javadoc、前块孤儿；SemanticAnalyzer.java:52-62 同病。
- CheckRunner/NopLintCli/TestCommand/MatchCommand/RuleDslParser 内联全限定名（java.nio.file.Path.of、java.util.Arrays.copyOfRange 等）+ 三个文件 io.nop 组内 import 紊乱——违反 AGENTS.md import 规范。
- 模块内 IllegalArgumentException/IllegalStateException 散点（ConsoleReporter:68、RuleResultCache:203、XNodePatternMatcher:110、FileDiff:15、FileFindings:20、DataflowQueries、ScopeAnalyzer、LineColBytes）vs NopLintException——统一为 NopLintException（它已是 NopException 子类，代价为零）。

**D7. CliOptions/RuleDslModel 可空构造器海（Minor）**
- CliOptions 三个兼容构造器零使用（:88-107）；withFixMode 的 flag 参数未用；RuleDslModel 13/14 参构造器唯一调用点是 15 参版（:41-58）；Matcher 4/10/11 参位置式可空构造器——parser 已自建 matchesMatcher/relationalMatcher 工厂说明作者也意识到 → 工厂移入 Matcher 本体、其余私有化。

**D8. 规则 YAML 治理（Minor，62 文件）**
- 26 条规则 message 顶层与 xscript report() 双写且已漂移（bizmodel-dao-access 顶层无插值/report 有）；消息语言三种风格并存（英文/双语/中文）且同规则内不一致；metadata 样板 ×62（autoFixable:false + version:"1.0" 100% 重复）；method-cyclomatic-complexity 阈值 10 三处硬编码。
- 需要 design 裁定（report() 无参回落顶层 message、metadata 默认值兜底），改动面大、涉及用户可见输出——单独立项审慎评估。

**D9. 其余（Minor）**
- ReferentMatcher 共享 ThreadLocal 深度计数（pattern/ReferentMatcher.java:25-26）——虚拟线程低效路径，depth 作参数传递或编译期展开。
- SourcePattern.possibleKindIds() 返回内部数组 vs CompiledRule.targetKindIds() 每次克隆——口径统一。
- TscProtocol.resultOf 未用 id 参数、NodeTscBridge 借 request(0,"",…) 造参数 map；TscBridgeConfig.defaultEnvironment 违背自述 lazy 原则（构造期走 FS + 双调用）。
- CheckMojo resolveTargets 用 null 哨兵；JavaLanguage/TypeScriptLanguage/TsxLanguage 三胞胎 ~70 行适配器样板 ×3；MetricsEvaluator 多标签 case 计数口径与 javadoc 措辞出入；DefUseChain/JavaSemanticResolver 全限定内联 FQN。

### 四、审计确认的「不是问题」

- fail-closed 解析矩阵、RuleTester 门禁、golden 字节约束、降级计数可观测性——执行良好，非问题。
- RunSummary 418 行不是 DTO 膨胀（30 字段每个被渲染或有退出码语义）。
- RuleDslParser 不需要拆类（状态机清晰）。
- 无非英文异常消息、无未预编译正则热路径、无 @Inject private、无 classpath 扫描依赖、L2 永不伪造可验证成立。

## 改进建议（工作项归并 → plans）

| Plan | 结果面 | 收纳 finding | 验证 |
|---|---|---|---|
| 07 | 引擎编译复用（P1+D4+m1）：预编译规则集 API、CompositeRuleCompiler 拆分、TypeQuerySupport 运行期注入 | P1、D4、P1 附带双编译、engineLint 基准口径 | JMH before/after（engineLint/parseAndMatch）+ JFR |
| 08 | 热路径分配治理：抑制尾 toLowerCase、NodeExactEquality、KindIndex BitSet、MetaVarEnv 拷贝、sha256/HexFormat、MessageDigest 复用 | P2-P5、P7 | JMH before/after + JFR 分配剖析 |
| 09 | children() 缓存/遍历结构优化（perf-baseline 候选认领） | P6 | JMH + JFR（时间+分配） |
| 10 | CLI/生态用户可见缺陷：cache 双读、Mojo 基线开关、Mojo 日志桥乱码、checkFile cap、LSP/MatchCommand 大小写、CLI null 消息 | C3、C4、C5、C6、C7、C10-CLI | 焦点测试 + 真实 e2e |
| 11 | 内核正确性：DefUseChain 遮蔽、TemplateFix NPE、Myers trace 上界、relationalStopBy fail-closed、RuleResultCache 条目校验、SuiteResult 语义 | C1、C2、C10-内核 + M3-A | 可证伪焦点测试（含 C1 反例） |
| 12 | nop-lint-java 语义层收敛：ParsedUnitCache、TypeResolver 缓存对齐、DataflowResolver 键控、异常翻译、死字段、minimalAt/DefUseChain 重复 | C9、D1 全部 | 单测 + LRU 行为测试 |
| 13 | NodeTscBridge 会话隔离与资源治理 | C8、D9-TscBridge、D9-TscBridgeConfig | 焦点测试（真实 Node） |
| 14 | 可读性/规范清理：CheckRunner 拆分、RuleDslParser XOR 收敛、死代码、PrintStreamWriter、RunSummary/CliOptions/RuleDslModel、import 规范、诊断文本、异常类型统一 | D2、D3、D5、D6、D7、C10-杂项 | 纯重构：全测试绿 + golden 不变 |

D8（规则 YAML 治理）独立裁定：涉及 design 层裁决与 62 文件用户可见输出，本轮不直接执行；如执行需先出 design 增补。归入 Deferred（见下）。

执行顺序建议：10/11（用户可见缺陷，改动小收益直接）→ 07/08（性能主线）→ 09（结构性能）→ 12 → 13 → 14（清理收尾）。每个 plan 独立提交。

## Conclusion

- 审计 resolved；后续工作由 `ai-dev/plans/nop-lint/07..14` 接手。
- 被否决的方向：RuleDslParser 整类拆分（状态机清晰，只抽 XOR helper）；RunSummary 重构为 builder 流式（字段即契约）；正则 matcher 引擎面（item 22 已裁定归 constraints）；TSQuery 扩展携带 pattern（冻结红线）。
- 性能验证纪律：全部性能项 JMH before/after 对照本报告基线表 + JFR（热点与分配两口径），回归即回滚；perf-baseline.md 增注。

## Open Questions

- [ ] P6 children() 缓存的生命周期策略（tree 级 vs run 级）需在 plan 09 拟制时以 LSP 长驻场景裁定
- [ ] D8 规则 YAML 治理是否立项（需 design 增补：report() 无参回落语义）

## References

- `nop-lint/docs/perf-baseline.md`（历史 JFR 热点 + 本报告基线的前序锚点）
- `ai-dev/design/nop-lint/03-execution-engine.md`、`11-performance-profiles.md`（引擎/预算口径）
- `ai-dev/backlog/nop-lint-roadmap.md`（items 1–43 全 done 的基线事实）
- 本报告审计证据：各 finding 标注的 `文件:行号` 均已在 2026-09-25 工作区实测核对
