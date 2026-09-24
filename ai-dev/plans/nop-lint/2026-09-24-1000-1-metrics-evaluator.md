# MetricsEvaluator（roadmap item 32）

> Plan Status: completed
> Last Reviewed: 2026-09-24
> Source: ai-dev/design/nop-lint/01-pattern-dsl.md §6（MetricsEvaluator 契约）、06-pmd-errorprone-alignment.md §4.1（metrics 规则无需类型推导）、11-performance-profiles.md §2/§5（deep 档 + 阶梯）、07-xscript-engine.md（绑定机制）
> Related: roadmap item 32（deps: M1 done）；前序 plan 2026-09-24-0600-1（nop-lint-java semantic 先例）、2026-09-24-0900-1（METRICS capability + AnalyzerAvailability 接口）
> Review: R1 对抗审查 agent_0a662b31（1 Blocker + 4 Major，全部已修）+ R2 增量复核（白名单变体机制自洽无漏洞；N1–N5 文本同步已落）

## Purpose

落地 design 01 §6 的 MetricsEvaluator：圈复杂度（决策点计数 +1）、认知复杂度（SonarSource 增量表：嵌套增量 + 线性流中断 + 逻辑运算符序列）、NPath（决策结构路径数乘积——**不是 AST 深度**）。度量经 item 31 的 METRICS capability + AnalyzerAvailability 接口接入 deep 档，规则面经 xscript `metrics` 绑定消费（item 33 的 scopeAnalyzer 绑定将复用同一机制）。

## Current Baseline

- item 30 先例：`DataFlowAnalyzer`/`DefUseChain`/`ConstantPropagation` 落于 `nop-lint-java` semantic 包、JavaParser AST 之上、不接引擎；测试直接驱动（`./mvnw -pl nop-lint-java test`）。
- item 31：`LintCapability.METRICS` 已存在（`isDeepAnalyzer()`=true）；`AnalyzerAvailability` 探针接口已定义（按 capability 注册、廉价探测、无 provider=fail-closed DEGRADE、注入与 TypeResolver 同构）；deep 档上限含 METRICS；无任何 METRICS provider 或度量实现（全仓无 MetricsEvaluator）。
- item 26 先例：位置键查询契约（`(filePath,line,col)` → JavaParser 节点）由 `ASTMapping`/`JavaNodeIndex` 提供（`getJavaNode(LintNode, expectedType)`；LineColBytes 处理 end-col inclusive 与多字节）；`TypeResolver` 是"core 接口 + 模块实现 + 位置键"的完整先例。
- xscript 绑定机制（design 07）：宿主侧 `scope.setLocalValue` 注入通道（`node`/`captures`/`report`/`declType`）；`declType()` 是既有宿主函数先例；脚本引用未注入标识符 = 编译期 unresolved identifier（fail-closed）；deadline 注入通道刻意不在编译白名单（防脚本自改）。
- 规则面现状：度量规则尚无表达形态；item 35 的规则批量（含复杂度规则）依赖本 item 的查询面；设计 06 §4.1 明确 metrics 规则无需类型推导。
- 性能基线：`nop-lint/docs/perf-baseline.md`（engineLint 0.002±0.001 s/op 为引擎层对照锚点，JMH+JFR 纪律已建立）。

## Goals

1. `MetricsEvaluator`（nop-lint-java semantic 包，JavaParser AST，方法级，item 30 同型）：
   - 圈复杂度 = 决策点计数（if/for/while/do/case/catch/&&/||/?:）+ 1（design 01 §6 原文口径）
   - 认知复杂度 = SonarSource 增量表 v1（嵌套增量 + 结构增量 + 逻辑运算符序列增量 + 线性流中断），以白皮书公开范例为测试锚点
   - NPath = 各决策结构路径数乘积（if/三元/循环 ×2、case 标签 n 个 → ×(n+1) 含 default、catch 子句每个 ×2、&&/|| 每个 ×2——以 Decision 4 裁定为唯一口径）
2. core 侧查询接口（语义包 `MetricsResolver` 命名实现期定）：位置键 `(filePath,line,col)` → 方法级三度量，TypeResolver 同构（0-based/UTF-16 契约）；nop-lint-java 提供实现（JavaTypeResolver 同型：自 parse + minimalContaining + 父链上溯 → MethodDeclaration，不经 ASTMapping/LintTree，Decision 6）。
3. xscript `metrics` 绑定：`metrics.cyclomatic(node)`/`metrics.cognitive(node)`/`metrics.npath(node)`（宿主注入通道，与 `declType()` 同型；白名单按 requires 门控变体注册，见 Decision 2）；运行含 live provider + 具名文件时注入并放行，`requires: METRICS` 门控保证引用它的规则只在 provider live 时编译执行（无 provider/未具名文件 → 上限内 DEGRADE，item 31 既有出口）。
4. deep 档接线：Java 语言绑定的 run（CLI/e2e）经 ServiceLoader 拾取 METRICS provider 探针（Decision 5），CLI `--profile deep` 下度量查询真实可用——该项**演进并部分推翻** design 11 §2 的"CLI 不接探针"裁决（METRICS 维度从降级变为可用；L2/L3/L4/SCOPE 维持降级），owner doc 随 Phase 2 落字。
5. demo RuleTester 套件（Wave-2+ 硬约束：规则必须带 fixtures）——一条 `requires: METRICS` 的 demo 复杂度规则（xscript 比较阈值 + report），走真实 RuleTester 管线；不落生产规则（复杂度规则归 item 35 批量）。

## Non-Goals

- 不落生产复杂度规则（item 35）；不做类级/文件级聚合度量（设计未要求，方法级 v1）
- 不做 Scope 分析（item 33）、L3/L4 语义面（item 34）
- 不改 nop-treesitter；不改 nop-xlang/nop-core（Protected Areas）
- 不做增量度量缓存（方法级 O(n) 遍历，无证据不做缓存）

## Key Decisions（执行前裁定，审查确认）

1. **AST 选型**：JavaParser AST（nop-lint-java，item 30 先例）而非 tree-sitter CST——决策点/嵌套结构在真语法树上有唯一判定，CST kind 映射跨语法脆弱；与 item 26 的 ASTMapping 桥接天然衔接。
2. **查询面形态与编译白名单（R1 Blocker 1 修复后口径）**：xscript 绑定为主（design 05 §2 的 scopeAnalyzer 绑定先例，item 33 复用同机制）；不做 constraint 形态。`metrics` **进编译白名单**（与 declType 同型），但按 requires 门控变体注册：`CompiledRule` 编译时依据 `model.getRequires()` 含 METRICS 才注册 `metrics` 标识（gate 已保证 RUN 态规则 provider live）；**未声明 requires 却引用 `metrics` → 编译期 unresolved identifier 失败**（三态测试第三态），声明了 requires 但 run 无 provider → gate DEGRADE 不可达注入点。provider 穿透链：LintEngine 构造（新增参数）→ RuleSetRunner.run → XScriptEngine.executeMatch → scope 注入（per-match，与 deadline 同型）；CompiledRule 保持档位无关（provider 是运行期注入，不进编译产物）。
3. **SonarSource v1 口径（R1 Major 4 修复后，以白皮书 v1.7 Appendix B 为准）**：if/else if/else 各 flat +1（自身无嵌套增量）但**抬嵌套层级**；switch 连全部 case 合计单次 +1（非每 case）；循环 +1+嵌套；catch +1+嵌套；三元 +1+嵌套；嵌套层级抬升结构 = if/else if/else/switch/循环/catch/三元/lambda；逻辑运算符序列 +1/新序列；递归方法增量**不实现、显式入 residual**（需调用图，v1 无消费方）；labeled break/continue（白皮书 jump 类增量）不实现、入 residual（v1.7 下普通 break/continue 本就不增量）。白皮书公开范例（getWords 等）作逐例测试锚点。
4. **NPath v1 口径（R1 Major 5 修复后统一）**：乘积枚举（if/三元/循环 ×2、case 标签 n 个 → ×(n+1) 含 default、**catch 子句每个 ×2**、&&/|| 每个 ×2）；continue/break/return 序列修正项 v1 不实现（记录 residual，非 AST 深度计算——design 01 §6 红线）。Goal 1 的公式以本裁定为准（每 catch ×2，对齐 PMD 系规则面）。
5. **provider 发现（R1 Minor 7 裁定落字）**：ServiceLoader SPI（`META-INF/services`，`LanguageRegistry.discoverDefaults` 同构；nop-lint-java 已有 LintLanguage services 文件先例可照抄）；CLI 与 RuleTestRunner 默认构造拾取，显式构造子为测试通道。`requires: METRICS` 规则在无 provider run 中 DEGRADE（item 31 契约，绝不 L1 顶替）。
7. **XML 路径白名单行为（R2 N4 裁定）**：XML 规则路径（XmlRuleCompiler 自建 XScriptEngine，不经 compileTreeSitter 变体注册）v1 **不注册** `metrics`——requires: METRICS 的 XML 规则引用它即编译期显式失败，与该路径 fix/constraints 的显式拒绝先例同型（fail-closed，非静默）。
8. **位置键桥接与行列约定（R1 Minor 6 修复后）**：provider 走 `JavaTypeResolver.resolvedTypeAt` 同型（自读文件 parse + `JavaNodeIndex.minimalContaining` + 父链上溯到 MethodDeclaration），**不经 ASTMapping/LintTree**（那是 binding 持节点的反查方向）；接口契约 0-based line / UTF-16 col（与 TypeResolver 一致，binding 侧 1-based byte col 的换算在 provider 实现内补，`LineColBytes` 无 colOfByte 则新增）；gate 对 METRICS 增加**具名文件要求**（同 l2Ready——unnamed run 的位置键查询无路径可用，DEGRADE 而非运行期失败）。

## Execution Plan

### Phase 1 - MetricsEvaluator 内核（三度量 + 测试锚点）

Status: completed
Targets: `nop-lint/nop-lint-java/src/main/java/io/nop/lint/java/semantic/`、`nop-lint/nop-lint-java/src/test/java/...`

- Item Types: `Proof`

- [x] `MetricsEvaluator`：cyclomatic（决策点 +1）、cognitive（SonarSource 增量表 v1，Decision 3）、npath（乘积枚举 v1，Decision 4）；方法级入口（MethodDeclaration）
- [x] 测试：白皮书结构范例作认知复杂度锚点（switch 单次增量/else-if 平坦化/循环嵌套形态/lambda 抬嵌套——TestMetricsEvaluator 11 例手算逐例断言）
- [x] NPath 溢出防护（long 上限饱和，不抛异常；80 层嵌套 if = 2^80 路径饱和断言）

Exit Criteria:

- [x] 三度量的正/反/边界测试全绿（含与手算期望逐例断言；执行期修正：javaparser 3.26.3 为 ForEachStmt/每 && 节点为独立决策点/NPath 期望三处手算修正）
- [x] **无静默跳过**：null 方法输入 fail-closed（NopLintException 英文消息），不返回 0 冒充
- [x] No owner-doc update required（内核纯新增，契约即测试；§增注随 Phase 2 落）
- [x] `ai-dev/logs/2026/09-24.md` 条目更新

### Phase 2 - 查询接口 + xscript 绑定 + deep 接线 + demo 套件

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/semantic/`（查询接口）、`xscript/`（白名单变体 + 绑定注入）、`engine/LintEngine.java`（穿透链起点 + gate 具名文件要求）、`engine/CompiledRule.java`（requires 门控白名单注册）、`engine/RuleSetRunner.java`（provider 穿透）、`testing/RuleTestRunner.java`（探针+provider 构造通道）、`nop-lint/nop-lint-java/...`（provider + JavaTypeResolver 式桥）、`cli/CheckRunner.java`（默认构造拾取 SPI）、`nop-lint/nop-lint-nop` demo 套件、两模块测试

- Item Types: `Decision | Proof`

- [x] core 查询接口（`MetricsResolver`，0-based/UTF-16 契约 + 具名文件要求）+ nop-lint-java 实现（`JavaMetricsResolver`：自 parse + JavaNodeIndex.minimalContaining + 父链上溯 → MetricsEvaluator；`SourcePositions.lineColUtf16` 在绑定侧完成换算，无需 LineColBytes 改动）
- [x] xscript `metrics` 绑定（Decision 2 白名单变体：`XScriptCompiler.compile(ruleId, script, withMetricsBinding)` 由 `CompiledRule` 按 requires 注册）+ provider 穿透链（LintEngine 5 参构造 → RuleSetRunner.run → executeMatch 6 参重载 → scope 注入 `MetricsFunctions`）
- [x] provider 发现接线（`MetricsResolverDiscovery` ServiceLoader SPI + META-INF/services 注册）+ `RuleTestRunner` 4 参构造通道 + `CheckRunner` 默认拾取
- [x] demo RuleTester 套件（`deep-suites/metrics-complexity`：requires METRICS + kind 匹配 + xscript 阈值 + report，expect 钉死 cyclomatic=4；TestMetricsDemoSuite DEEP+provider 真实管线绿）
- [x] 测试：`TestMetricsBinding` 6 例（四态 + fast/standard ceiling skip + 非方法节点 fail-closed 走 skip-and-count）+ e2e demo 套件

Exit Criteria:

- [x] **端到端验证**：`requires: METRICS` demo 套件经 RuleTester 真实管线绿（匹配 → metrics.cyclomatic 位置键桥 → 阈值判定 → 诊断输出，expect 钉死 cyclomatic=4）
- [x] **接线验证**：METRICS provider 被引擎门控与 xscript 绑定注入真实消费（TestMetricsBinding 四态断言全绿）；CLI 装配点（CheckRunner→MetricsResolverDiscovery）接线在案（SPI 注册文件 + 装配代码）
- [x] **无静默跳过**：无 provider/unnamed → DEGRADE；非方法节点查询 → xscript 失败 skip-and-count（TestMetricsBinding.nonMethodQueryFailsTheMatchNotTheRun），无假 0
- [x] 既有测试零改动通过（core 715/0 + java 81/0 + nop 27/0；TestBudgetDegradeLadder 的 5→6 参构造调用为机械适配）
- [x] Owner-doc：design 01 §6 增注 + design 07 绑定面增注 + design 11 §2 METRICS 维度演进增注
- [x] `ai-dev/logs/2026/09-24.md` 条目更新

### Phase 3 - 性能守门 + 全量回归 + 收口

Status: completed
Targets: `nop-lint/docs/perf-baseline.md`、全模块测试、roadmap

- Item Types: `Proof`

- [x] JMH after 复测：engineLint 0.002±0.001、parseAndMatch 0.001±0.001——对 item 31 锚点零回归；JFR 未录制（无回归，裁定记入 perf-baseline.md）
- [x] 全量回归三模块（core 715 / java 81 / nop 27 全绿）+ doc-links 0
- [x] roadmap item 32 → done（closure audit 后）

Exit Criteria:

- [x] perf-baseline.md 增注（零回归 + JFR 未录制裁定）+ 全量绿 + doc-links 0
- [x] roadmap item 32 → done（closure audit 通过后翻转）
- [x] `ai-dev/logs/` 收口记录（随 closure 写入）

## Closure Gates

- [x] design 01 §6 三度量契约全部落地且与增注一致（复杂度不是 AST 深度——NPath 为乘积枚举）
- [x] deep 档 METRICS 门控 + provider 接线端到端可用（e2e 证明）
- [x] demo 套件走真实 RuleTester 管线绿（Wave-2+ fixtures 硬约束）
- [x] 无被静默降级的 in-scope 项；SonarSource/NPath residual 显式记录
- [x] owner docs（design 01 §6、design 07 绑定面、design 11 §2 METRICS 维度演进）与 live 一致
- [x] 独立子 agent closure audit 完成并写入本 plan Closure 段
- [x] **Anti-Hollow Check**：metrics 绑定被规则真实消费（e2e）；provider 被引擎门控真实消费（四态断言）；无空方法体/no-op
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test`、`./mvnw -pl nop-lint/nop-lint-java -am test`、`./mvnw -pl nop-lint/nop-lint-nop -am test` 通过（core 715/0 + java 81/0 + nop 27/0；-Dtest 定向套件审计实测全绿）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-java --severity high` 退出码 0（0 findings）

## Deferred But Adjudicated

### SonarSource goto 类增量（label/break/continue）与 NPath 序列修正项

- Classification: `watch-only residual`
- Why Not Blocking Closure: v1 规则消费方（item 35 复杂度规则）未要求；白皮书主体（嵌套+结构+序列增量）已覆盖；触发条件 = item 35 规则需要时
- Successor Required: `no`

### SonarSource 递归方法增量

- Classification: `watch-only residual`
- Why Not Blocking Closure: 需跨方法调用图（v1 为方法级单遍遍历），item 35 复杂度规则消费方未要求递归场景；白皮书主体增量已覆盖
- Successor Required: `no`

## Non-Blocking Follow-ups

- 类级/文件级聚合度量（无消费方证据）
- 度量结果缓存（同方法重复查询——现每查询 O(n)，无热点证据）

## Closure

Status Note: MetricsEvaluator 三度量内核 + MetricsResolver 查询接口 + JavaMetricsResolver SPI + metrics 白名单变体绑定 + 穿透链（LintEngine gate → RuleSetRunner → executeMatch 注入）+ 阶梯 METRICS 闭合记录 + demo 套件（真实管线钉死 cyclomatic=4）+ 四态门控测试全部落地；三 Phase Exit Criteria 与全部 Closure Gates 勾选完毕，独立子 agent closure audit APPROVED，roadmap item 32 已翻 done。
Completed: 2026-09-24

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_10316e9c-f8cb-4778-8ebf-3d22c494893b（fresh session）
- Evidence:
  - Phase 1（PASS）：三度量实现与 Decision 3/4 逐条一致；TestMetricsEvaluator 11 例中 8 例经审计者独立手算复验（含 switch 单次增量=1、else-if 平坦链=4、循环嵌套=6、逻辑序列 6/4/32）；80 层 NPath 饱和断言在案。
  - Phase 2（PASS）：白名单变体（undeclaredReferenceFailsCompilation 实证）+ 穿透链逐跳核对 + XML 路径 2 参 compile 永不注册（Decision 7 fail-closed 核实）+ SPI 注册文件 + demo e2e 数值可证伪（score=4 手算吻合，valid negate=2 零诊断）。
  - Phase 3（PASS）：perf-baseline 增注（engineLint/parseAndMatch 对 item 31 锚点零回归 + JFR 未录制裁定）。
  - 审计实测：TestMetricsBinding 6/6 + TestBudgetDegradeLadder 9/9 + TestMetricsEvaluator 11/11 + TestMetricsDemoSuite 1/1 全绿；checklist/hollow/doc-links 工具 exit 0。
  - Anti-Hollow：绑定与 provider 均有可证伪的消费断言；hollow 扫描 0 findings。
  - Deferred 三项（递归增量/labeled jump/NPath 序列修正）均 watch-only + successor=no，与实现事实（无这些分支）相符。

Follow-up:

- TestBudgetDegradeLadder 未触达 METRICS 开启态的阶梯闭合记录（audit Minor 1，3 行镜像 l2Open 模式，non-blocking）——item 33+ 的 deep 分析器落地时随阶梯测试补强
- surefire 陈旧报告（已删除 spike 测试的残留 errors=1）易误导聚合统计（audit Minor 2）——下次全量复跑前 `mvn clean`
