# I1 三后端对拍验证框架 + corpus v1

> Plan Status: completed
> Last Reviewed: 2026-08-20
> Source: `ai-dev/backlog/xlang-execution-optimization-roadmap.md` I1；对拍口径冻结于 `ai-dev/design/xlang-execution/01-architecture-baseline.md` §五
> Mission: xlang-execution-optimization
> Work Item: I1
> Related: W4-audit（解冻前提，done）；后继 I2/I5（消费本 plan 产物）

<!-- Draft review: round-1（fresh session ses_fe5205455ffegp3LckfEgJ4Sv2，2 Major+7 Minor）→ 修复 → round-2（fresh session ses_fe51598acffelNS60esIt3CNYl，全部 resolved，GO 0 Blocker/0 Major，共识达成）→ active。 -->

## Purpose

落地三后端对拍验证框架的执行载体与表达式子集 corpus v1：同一棵 Executable 树按可用后端列矩阵化执行，三层断言 + 后端身份断言 + 列缺席显式记录，并以差异注入自检证明断言机制红/绿可控。产出 I2（java 列）与 I5（truffle 列）可直接接入、无需改造的对拍基座。

## Current Baseline

- 阶段一 W1-W4 全部 done（2026-08-20），阶段二解冻；对拍不变式口径冻结于 `ai-dev/design/xlang-execution/01-architecture-baseline.md` §五：三层断言（返回值 equals 含类型 / 副作用 scope 变量与输出缓冲逐项比对 / 异常 错误码 + SourceLocation 回映射）、后端身份断言（不断言身份的比对判无效）、列适用性（静态单元三列 / 动态单元两列）、后端列缺席显式记录跳过（不算通过）、单元级降级判 FAIL 不判跳过。
- 评估契约 live 事实：`IExpressionExecutor`、`IExecutableExpression`、`IEvalScope`、`IEvalOutput`、`EvalRuntime`、`ExitMode`、`DefaultExpressionExecutor` 均在 `nop-kernel/nop-core/src/main/java/io/nop/core/lang/eval/`；解释器节点 137 文件在 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/`。
- 输出缓冲 live 事实：`EvalRuntime` 携带 `IEvalOutput out`（默认 `DisabledEvalOutput.INSTANCE`），经 `getOut()/setOut()` 存取；`StringBuilderEvalOutput` / `WriterEvalOutput` 为既有捕获实现，可作副作用快照基础。
- Nop AutoTest 机制 live：`nop-autotest/nop-autotest-junit`（`@EnableVariants` + `VariantsArgumentProvider`）。live 核查：`VariantsArgumentProvider` 枚举的是 `variants` 数据目录名，不是后端参数通道——AutoTest 机制仅作矩阵化**先例**参照，矩阵化由 JUnit5 `@ParameterizedTest` 直接承载，不引入 nop-autotest-junit 依赖（Phase 1 Decision 记录）。
- 现状无任何对拍 harness / corpus；`nop-xlang-java` / `nop-xlang-truffle` 模块不存在——列缺席是当前常态，机制必须面向缺席设计。
- `missions/xlang-execution-optimization.json` live commands 仅引用 `:nop-xlang`；本 plan 不新增模块，不切换 commands。

## Goals

- 对拍 harness 落地可运行：corpus 用例 × 已注册后端列矩阵化执行，三层断言逐项可判，any-divergence 即 FAIL（fail-fast，不做"允许少量漂移"弱化）。列不足 2 列时（如仅解释器列的 I1 阶段），判定基准 = 列结果 vs 单元声明的预期（返回值/副作用/异常三类预期中适用者）。
- 后端身份断言：**由 harness 依据执行证据判定，列不得自证**——列执行入口须返回实际执行工件证据（如执行体/执行器类型），harness 对照该列声明的后端标识断言（设计 execution 01 §五：不断言身份的比对判无效）；解释器列身份 = 执行证据为 Executable 树经 `IExpressionExecutor` 执行。
- 列适用性（静态单元三列 / 动态单元两列）与列缺席显式记录机制：缺席列记 skipped、不计入通过。
- corpus v1：表达式子集 6 类（字面量 / slot 标识符 / 算术 / 逻辑 / 比较 / 简单方法调用）× 静态/动态双形态，解释器基线列全绿；配比定稿（见 Phase 2）。
- 差异注入自检：故意构造分歧的注入列（测试专用假列）令 harness 判 FAIL，红/绿可控。
- 测试级强制路由 API：测试域指定某单元用某后端执行并断言后端身份，供 I2/I5/I9 测试复用。
- harness 经 test-jar 发布：I2/I5 在自身测试源码中依赖 nop-xlang test-jar 即可注册自身为列。

## Non-Goals

- java / truffle 真实后端列实现（I2 / I5）。
- 表达式子集之外节点类别的 corpus 扩充与覆盖矩阵（I3/I4/I6/I7）。
- 生产代码路径的后端选择 / 路由 / 降级机制（I9）；本 plan 的强制路由 API 仅存在于测试域。
- 租户差异化 / Delta 场景样例（对拍对象是树，Delta 合并在上游完成，不影响 harness 形态）。

## Scope

### In Scope

- harness 落点决策（roadmap 委托本 plan 定稿）与实现。
- 后端列注册 SPI（测试域）、三层断言引擎、身份断言钩子、列缺席记录、测试级强制路由 API。
- corpus v1 构建与解释器基线列。
- 差异注入自检（注入列为测试专用假列，不是真后端）。

### Out Of Scope

- 同 Non-Goals。

## Execution Plan

### Phase 1 - harness 落点决策与骨架

Status: completed
Targets: `nop-kernel/nop-xlang`（pom test-jar 配置 + 测试源码；不新增模块）

> Execution note（2026-08-20）：落点按本 item 裁定落地——harness 核心与解释器列定义在 `nop-kernel/nop-xlang/src/test/java/io/nop/xlang/compare/`（`ExecCompareHarness` 注册表 + `IEvalBackendColumn` SPI + `IBackendIdentityRule` 身份规则 + 录制输出 wrapper + 三层断言引擎 + `routeAndExecute` 强制路由 API），pom 追加 `maven-jar-plugin` test-jar 执行（先例：nop-stream-core）。AutoTest 复用裁定维持：矩阵化由 JUnit5 `@ParameterizedTest` 承载（`TestCorpusV1InterpreterBaseline`），不引入 nop-autotest-junit 依赖。java/truffle 后端身份规则以临时规则 `NonInterpreterArtifactIdentityRule`（执行工件 ≠ 解释器树/执行器）先行占位，I2/I5 落盘时经 `replaceIdentityRule` 用强规则（生成类实例/CallTarget）替换。

- Item Types: `Decision | Proof`

- [x] 裁定并记录 harness 落点：harness 核心（列注册 SPI + 三层断言引擎 + 身份断言 + 列缺席记录 + 强制路由 API）定义在 `nop-xlang` 测试源码并经 test-jar 发布；corpus 用例与解释器列注册同置 `nop-xlang` 测试源码。依据：模块依赖方向仅允许后端模块 → nop-xlang，后端模块测试域依赖 nop-xlang test-jar 即可接入（单一事实源，一份 corpus 三处执行；仓内先例：nop-stream-core 产出 test-jar 供下游消费）；拒绝 harness 放 nop-core（nop-core 不感知 XLang 编译单元/树概念，设计 §二模块边界）与独立对拍聚合模块（I2/I5 尚不存在，提前建模块违反"模块落盘即切换"commands 纪律）。同 item 记录 AutoTest 复用裁定：`@EnableVariants`/`VariantsArgumentProvider` 仅作矩阵化先例参照（live 语义为 variants 数据目录枚举，非后端参数通道），不作为依赖引入
- [x] 实现后端列注册 SPI：列 = 后端标识 + 能力声明（适用静态/动态单元）+ 执行入口 + 身份证据提供（执行入口返回实际执行工件证据，供 harness 判定身份——列不得自证身份）
- [x] 实现三层断言引擎：返回值 equals 含类型信息比对；副作用 = 求值后求值现场快照逐项比对（harness 自建独立 scope 后取本地变量集封闭化比较；输出缓冲经录制 wrapper 捕获完整 API 调用序列后比对，不用会丢事件信息的裸 `StringBuilderEvalOutput`——其 `comment()` 为 no-op）；异常 = 错误码一致 + SourceLocation 回映射同一源位置 + 不允许一侧抛异常一侧正常返回
- [x] 执行现场语义：每列收到**同一棵 Executable 树实例** + **独立新建、按单元声明等价初始化的求值现场**（IEvalScope + 输出缓冲）；副作用快照逐列在各自现场上获取（禁止跨列共享可变现场，否则副作用逐列累积污染断言语义）
- [x] 实现列适用性与列缺席记录：按单元静态/动态类别 × 已注册列执行；缺席列显式记 skipped（含原因），不计入通过
- [x] 实现测试级强制路由 API（测试域注册表：指定后端执行单元并返回身份证据供断言）

Exit Criteria:

- [x] 落点决策与 AutoTest 复用裁定记录进本 plan 与当日 log（Decision item 勾选即落点定稿，后续 phase 不得漂移）
- [x] nop-xlang test-jar 产物落盘：`./mvnw install -pl :nop-xlang -am` 产出 `nop-xlang-*-tests.jar`（repo-observable；下游模块消费由 I2/I5 首次行使验证）
- [x] harness 骨架可运行：以解释器列注册后，能对最小冒烟 Executable 树执行三层断言（此 phase 不要求 corpus）
- [x] 身份断言对解释器列生效：harness 依据执行证据（树经 `IExpressionExecutor` 执行）判定，非列自证
- [x] 列缺席记录机制可触发（未注册列产生 skipped 记录）
- [x] 无静默跳过：断言引擎对无法断言的维度（如缺快照器）显式 FAIL，不默认通过
- [x] No owner-doc update required（纯测试域设施；docs-for-ai 新模块开发指南同步归 I11）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - corpus v1 构建与解释器列基线

Status: completed
Targets: `nop-kernel/nop-xlang/src/test/`（corpus 用例与数据）

> Execution note（2026-08-20）：corpus v1 落地为 22 单元（11 静态 + 11 动态；6 类 × 双形态全覆盖 + 组合静态/动态各 1；异常语义单元静态/动态**双侧**均在位）。执行中定稿的三项裁定：
> ① 静态载体 = 测试资源 `xlang-compare/static/*.xpl`（`<c:script>` 表达式编译单元），经标准编译前端取树；树根为 `CallFuncExecutable` **程序入口包装**（编译前端对 script 单元的固有产物，slotNames 承载帧布局），子集纪律按"body 仅含子集节点 + 结构性载体"执行——白名单 guard（`CorpusV1.isAllowedNodeClass` + 基线测试逐单元断言）将结构性载体显式枚举为 CallFuncExecutable（入口）/ Block/Seq/SlotAssign（let 载体）/ ReturnNullExecutable（语句位置包装）/ GuardNotNullExecutable（成员调用接收者守卫）/ NullExecutable；用户级 CallFunc 族调用不在任何单元中出现。I2/I5 转译须将入口包装作为程序入口处理（与其 EvalMethod 生成方法约定同构），与非用户函数调用排除条款不冲突。
> ② slot 标识符类的最小产生载体 = let 声明（slot 写为 I3 族节点，作为结构性载体随树存在）；类别按标识符读取节点（SlotIdentifierExecutable，基线测试逐单元断言必须出现）归类。
> ③ 简单方法调用类静态分派形态 live 为 `FunctionExecutable` 族（注册全局函数经 IFunctionModel 宿主反射分派，`assign('result', 1+2)` 即此类，兼作副作用层绿路径：写 scope 变量）；实例形态为 `ObjFunctionExecutable` 族。两者均属"宿主方法反射分派族"（与 I2 plan"ObjFunctionExecutable/StaticFunctionExecutable 系"对账：FunctionExecutable 族为该系的注册函数静态分派成员，转译范围对齐由 I2 消费 corpus 时承接）。
> 动态单元经 `compileSimpleExpr`（纯表达式）与 `compileFullExpr`（含 let 语句单元）取树，编译时传入非 null 合成 SourceLocation（`xlang-compare/dynamic/*.expr`）。异常单元预期源位置：静态 [文件 path, line 2]（xpl 资源行号）、动态 [合成 path, line 1]。

- Item Types: `Proof`

- [x] 构建 corpus v1（配比定稿）：表达式子集 6 类（字面量 / slot 标识符 / 算术 / 逻辑 / 比较 / 简单方法调用）× 静态/动态双形态，每类每形态 ≥1 单元（合计 ≥12）；另含跨类组合单元 ≥2（静态/动态各 ≥1）。
  - 静态样例形态：测试资源下的 xpl/xlib 表达式编译单元（编译产物根为表达式树、仅含子集 6 类节点），经标准编译前端（XplCompiler + 宏展开 + LexicalScopeAnalysis）取树——静态单元，三列适用
  - 动态样例形态：运行时字符串表达式经既有动态编译出口（如 `XLangCompileTool.compileSimpleExpr` 系）取树，编译时传入非 null 合成 SourceLocation（合成 path），使异常层源位置断言不退化为 null==null——动态单元，两列适用
- [x] corpus 单元 schema 定稿（I2/I5 的消费契约）：每单元四字段——源（静态资源路径 / 动态表达式串）、初始求值现场声明（输入变量名与值）、预期（返回值+类型 / 副作用快照 / 异常 错误码+预期源位置，适用者）、列适用性（静态=三列 / 动态=两列）
- [x] 子集语义边界（与 I2/I5 转译范围对齐）：算术类必须含字符串拼接语义单元（live `PlusExecutable` 的 String 分支——数值路径走 `MathHelper.add` 但拼接分支内联于节点类）；简单方法调用类 = 宿主方法反射分派（含静态方法调用），**不含** XLang 局部函数定义/调用与函数字面量（CallFunc 族/闭包，属 I4 覆盖 B 范围）
- [x] harness 保证"对拍对象是树"：同一单元的多列执行使用同一棵 Executable 树实例（非源文本重新编译），差异只可能来自后端
- [x] 解释器基线列全量执行 corpus v1（单列阶段判定基准 = 列结果 vs 单元声明预期）

Exit Criteria:

- [x] corpus v1 落盘：单元清单与类别覆盖（6 类 × 2 形态）repo-observable；单元 schema 四字段齐备；算术类含字符串拼接单元、简单方法调用类不含 CallFunc 族（边界与 I2/I5 plan 对账一致）
- [x] corpus v1 解释器基线列全绿（静态 + 动态全部单元，含至少 1 例异常语义单元——静态或动态至少一边，行使异常断言引擎的绿路径）
- [x] 同树分列执行机制有验证（同一树实例传递断言或等效证据）
- [x] 回归不削弱既有解释器测试（roadmap 纪律 3；本 phase 只新增不删改）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 差异注入自检与列缺席机制测试

Status: completed
Targets: `nop-kernel/nop-xlang/src/test/`（自检用例）

> Execution note（2026-08-20）：注入列工厂 `InjectedColumns`（测试专用假列，包装解释器列）；`TestCompareDivergenceInjection` 以 corpus 单元经 harness 完整链路（单元定义 → 编译 → 矩阵化执行 → 三层断言 + 身份断言 + 列缺席 → 判定）逐例注入并断言**具体失败维度**（return-value-mismatch / scope-vars-keyset-mismatch / output-calls-mismatch / exception-error-code-mismatch + location-path-mismatch / identity-mismatch），同报告内解释器列保持 PASS（红/绿对照）；类型分歧例 7L vs 7 被显式检出。列间比对归因裁定：解释器列为参照列，分歧失败归因非参照列（any-divergence 仍使单元判 FAIL——fail-fast 不弱化；参照列自身判定仍由身份断言与声明预期驱动，满足"注入列 FAIL、解释器列 PASS"红/绿口径）。身份伪装例：注入列声称 java 后端、实际经解释器执行（经典 vacuous-pass 场景），harness 依据执行证据（executedArtifact == 解释器树实例）判 identity-mismatch——非注入列自招供。

- Item Types: `Proof`

- [x] 构造测试专用注入列（包装解释器列，故意制造分歧），四类分歧注入各 ≥1 例：返回值分歧（含类型分歧，如 1L vs 1）/ 副作用分歧（scope 变量或输出缓冲）/ 异常分歧（错误码或 SourceLocation 不一致）/ 身份伪装（执行体身份与声称后端不符）
- [x] 红/绿对照：同单元下注入列各分歧例均判 FAIL、解释器列 PASS——红/绿可控；身份伪装例的判定由 harness 依据执行证据做出（注入列返回的执行证据与其声称后端不符），非注入列自招供
- [x] 列缺席机制测试：仅注册解释器列的环境执行静态单元，断言 java/truffle 列被显式记 skipped 且不计入通过计数；动态单元在仅解释器列环境同样记录 truffle 列缺席

Exit Criteria:

- [x] 四类分歧注入 FAIL 各 ≥1 例且逐例可复跑（repo-observable 测试用例）
- [x] 红/绿对照测试通过：注入列红、解释器列绿
- [x] 列缺席记录机制有测试（java/truffle 缺席显式记跳过、不算通过）——roadmap I1 验收第三项
- [x] **端到端验证**：从 corpus 单元定义 → 树编译 → 矩阵化执行 → 三层断言 + 身份断言 + 列缺席记录 → FAIL/PASS 判定完整链路可运行（注入自检即端到端载体）
- [x] **接线验证**：差异注入自检证明断言引擎确实被列执行结果驱动（非摆设断言）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 纯代码 + 测试计划，构建验证条目如下。

- [x] 对拍不变式断言机制落地（三层断言/身份断言/列适用性）并以差异注入自检证明（红/绿可控）——roadmap I1 验收第一项
- [x] corpus v1 解释器基线列全绿——roadmap I1 验收第二项
- [x] 列缺席记录机制有测试——roadmap I1 验收第三项
- [x] 回归不允许削弱现解释器测试（纪律 3）：`./mvnw test -pl :nop-xlang -am` 既有测试全绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] owner-docs：No owner-doc update required（docs-for-ai 同步归 I11）
- [x] 独立子 agent closure-audit 已完成并记录证据（task `ses_fe4ede786ffeuzX4oxTRiiXzli`，verdict CAN CLOSE 0 Blocker/0 Major/3 Minor——Minor 均为收口动作项：提交代码、roadmap 记 done、输出层绿路径信息性注记）
- [x] Anti-Hollow Check：断言引擎被真实列执行驱动；无空方法体/静默跳过/no-op
- [x] `./mvnw compile -pl :nop-xlang -am`
- [x] `./mvnw test -pl :nop-xlang -am -T 1C`
- [x] checkstyle / 代码规范检查通过（`-Pqa checkstyle:check -pl :nop-xlang`：新增 compare 包 0 违例；`scan-hollow-implementations.mjs --module nop-xlang --severity high` 退出码 0）

## Deferred But Adjudicated

（无——起草时无 deferred 项；如执行中产生，按 guide 补录并写明 Why Not Blocking Closure。）

## Non-Blocking Follow-ups

- W4 audit 移交清单中与本 plan 无关项（P2-1/P2-3/P2-4 roadmap 表述修订、设计文档旧编号换算）维持既定归属，不由本 plan 承接。
- 设计 execution 01 §五"纳入回归的方式"与 roadmap I1"复用：Nop AutoTest 机制"措辞与本 plan 裁定（AutoTest 仅作矩阵化先例参照、`@ParameterizedTest` 承载、不引入依赖）存在机制性表述偏差——语义内核（用例一份、后端矩阵化）保持一致。Why Not Blocking：设计文档按 W2 round-5 裁定冻结不改；下次设计文档修订时同步措辞（并入 W4-audit §七 #1/#2 修订批次）。

## Closure

Status Note: 三个 Phase 全部完成并逐项勾选：对拍 harness（列 SPI + 三层断言 + 证据驱动身份断言 + 列缺席记录 + 强制路由 API）落地于 nop-xlang 测试源码并经 test-jar 发布；corpus v1（22 单元，6 类 × 双形态 + 组合双形态）解释器基线列全绿；四类分歧注入自检红/绿可控。独立 closure audit verdict CAN CLOSE（0 Blocker/0 Major），Minor 项均为收口动作（提交、roadmap 记 done），已在本次收口中完成。
Completed: 2026-08-20

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh session 子 agent（task `ses_fe4ede786ffeuzX4oxTRiiXzli`）
- Evidence:
  - 全部 Phase item / Exit Criterion / Closure Gate 逐项 PASS（审计报告逐条对照 live 文件与测试；关键锚点：`ExecCompareHarness.java:198-354` 三层断言引擎、`IEvalBackendColumn` SPI、`CompareValues.typedEquals` 类型敏感比对（7L vs 7 实测检出）、`RecordingEvalOutput` 含 comment 事件录制、`ExecCompareHarness.java:173-186` 无身份规则显式 FAIL、`CorpusV1.java:62-113` 22 单元、`TestCorpusV1InterpreterBaseline` 24/24、`TestCompareDivergenceInjection` 7/7（各注入例断言具体失败维度且同报告解释器列 PASS）、`TestColumnAbsenceRecording` 4/4）
  - roadmap I1 验收三项全 PASS（断言机制+自检 / 基线全绿 / 列缺席有测试）
  - Anti-Hollow：接线追踪完整（corpus → compiler → runUnit 同树单次编译 → 每列独立现场 → 引擎消费真实执行结果/快照/证据 → skip 由期望集减执行集派生）；无空方法体/静默捕获/占位返回
  - 工具门 live 复跑：compare 39/39 green；`checkstyle:check -Pqa` compare 包 0 违例；`scan-hollow-implementations.mjs --module nop-xlang --severity high` exit 0；`check-doc-links.mjs --strict` exit 0
  - `./mvnw test -pl :nop-xlang -am -T 1C` BUILD SUCCESS（nop-xlang 495/0/2 + 依赖模块全绿）；`nop-xlang-2.0.0-SNAPSHOT-tests.jar` 产出并安装
  - Deferred 项检查：Deferred But Adjudicated 为空；Non-Blocking Follow-ups 两项均有归属且经审计确认非 drift
  - Minor 发现处置：①未提交 → 收口时提交；②roadmap I1 仍 planned → 收口同步 done；③输出层绿路径为空==空（输出节点属 I4）→ 信息性注记，无动作

Follow-up:

- no remaining plan-owned work（后继 I2/I5 以 test-jar 消费 harness 与 corpus；Phase 2 执行注记的三项裁定为其对账输入）
