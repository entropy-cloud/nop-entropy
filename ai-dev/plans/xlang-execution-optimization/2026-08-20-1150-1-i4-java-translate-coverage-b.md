# I4 java 转译覆盖 B（函数闭包/控制流/输出节点生成族）+ 覆盖矩阵闭环 + `$out` 契约执行验证

> Plan Status: completed
> Last Reviewed: 2026-08-21
> Source: `ai-dev/backlog/xlang-execution-optimization-roadmap.md` I4（含 I3 Phase 1 移交口径）；类别划分 = 设计 `ai-dev/design/xlang-java/01-architecture-baseline.md` §三分类表（函数/闭包 cell 契约、控制流 ExitMode 传播边界不变式）与 §七（`$out` 第二隐参约定）；对拍口径 = 设计 `ai-dev/design/xlang-execution/01-architecture-baseline.md` §五
> Mission: xlang-execution-optimization
> Work Item: I4
> Related: I3（前置：覆盖 A + 矩阵机制 + 四分区单一事实源，本 plan 直接消费）；I2（移交：模板入口包装器契约定稿，执行路径验证归本 plan——Deferred But Adjudicated 责任链）；I1（对拍 harness 与 corpus 机制）；I7（truffle 侧覆盖 B，消费本 plan 的 corpus 覆盖 B 与边缘裁定口径；若 I7 先执行并触发其 fallback 自补产物，本 plan 后续执行时按"先落地方为事实源"对账消费——对称条款见 I7 plan Current Baseline）

<!-- Draft review: round-1（fresh session ses_fe2b1df1effeb3Xppd5oyohleR，1 Blocker+2 Major+6 Minor：共享基线 truffle 侧耦合/换缓冲形态缺决策/函数载荷处置缺裁定/fail-fast 引用错/计数漂移/122 硬编码/清零点不全/白名单放宽/Targets 缺访问器）→ 修复 → round-2（fresh session ses_fe2a23e27ffeUzg8Gjq0s6UeBQ，确认 round-1 全部真正修复；新发现 1 Blocker+1 Major：非根 CallFuncExecutable 局部函数调用形态无归属（矩阵类级断言对形态缺口失明）/Phase 1 锚点切换中期红灯窗口 + 6 Minor）→ 修复（形态裁定项 + 锚点切换移 Phase 2 同次变更 + 清零点补 CallFunc finally + 无树类证据形态 + 白名单具名 accessor + Targets 补 truffle 目录 + roadmap 漂移修正承诺）→ round-3（fresh session ses_fe29382beffejle8s6gWKb5sho，确认 N1 Blocker/N2 Major 修复扎实；F1 roadmap 漂移当场实际修正（计数 13+12+7、第三态删除、per-backend 表述、137→live 138）；F2-F5 Minor 当场处置：Phase 1 门扩三模块/LazyCompiled 载荷决策伞/局部函数调用形态 corpus 语料钉死/javadoc 语义更新项）→ 共识达成 → active。 -->

## Purpose

把 java 转译器从覆盖 A（87 类）推进到全节点覆盖：B 族 35 类（函数/闭包 10 + 控制流 13 + 输出/节点生成 12）逐具体节点类转译，corpus 覆盖 B 扩充后 java 列 vs 解释器列对拍全绿，覆盖矩阵闭环（java 侧目标集扩至全量、B 族 pending 集清零、矩阵全绿）；闭合 I2 移交的模板入口包装器契约（`$out` 第二隐参）执行路径验证；完成 I3 移交三边缘类（`LocationFunction`/`ReturnScopeValuesExecutable`/`ExecutableFunctionEvalAction`）最终裁定。

## Current Baseline

- I3 产物存在（前置断言——执行本 plan 前须核验 I3 已 `completed`）：转译器支持集 87 类（`ExecToJavaTranslator.getSupportedNodeClasses()`）；`ExecNodeBaseline` 四分区单一事实源（A 44 + 残余 15 + I2 28 + B 35 + 排除 16 = live 138 文件）+ `TestExecNodeBaselineFreshness` 新鲜度红灯；java 侧覆盖矩阵 `TestExecTranslationCoverageMatrix` 124 用例（支持集 ↔ `registeredTarget()` 双向一致 + 87 类真实转译 + B 族 35 类 pending fail-fast 反证 + `FutureExecutable` 红灯注入）；corpus 覆盖 A（20 静态 + 13 动态）java 列 `TestCorpusCoverageAJavaColumn` 33/33 全绿。
- **共享基线耦合（live 事实，Phase 1 必须处理）**：`ExecNodeBaseline` 经 test-jar 被 **truffle 侧矩阵消费**——`TestTruffleCoverageMatrix` 断言 truffle 支持集 == `registeredTarget()`（87）且以 `bFamily()`（35）为 pending 测试数据源。本 plan 扩量 java 侧目标集、清零 java pending、迁移边缘类分区，任何一项若直接改写共享 accessor 语义都会打红 truffle 侧测试。演进规则（per-backend 目标集口径 + truffle 侧锚点最小适配）Phase 1 定稿，truffle 侧适配纳入本 plan scope（见 Phase 1）。
- I2 移交（`Deferred But Adjudicated`，Successor = 本 plan 起草时引用）：模板入口包装器契约已定稿——xpl/xlib 有输出语义单元生成入口方法追加固定第二隐参 `IEvalOutput $out`（live 常量 `EvalMethodConvention.OUT_PARAM` 在案，位于声明参数之前），**执行路径验证归 I4**：corpus 全类别含模板单元样例覆盖 `$out` API 调用序列的执行验证（本 plan 闭合）。
- I3 移交三边缘类（roadmap I4 条目注记）：`LocationFunction`（全仓无产生路径、宿主互通语义——返回 SourceLocation）、`ReturnScopeValuesExecutable`（宏 script 单元产物，`Program.isMacroScript` 路径）、`ExecutableFunctionEvalAction`（懒编译适配器，implements IExecutableExpression 但非标准树编译产物）——**本 plan 闭环时最终裁定**（转译并入目标集，或改判排除分区）。roadmap I4 条目注记的 B 族构成计数笔误（11+12+6+3=32）与"维持 pending"第三态措辞已在起草审阅轮修正为 live 口径（35；无第三态）。
- **非根 `CallFuncExecutable`（局部函数调用形态）无归属（live 事实，Phase 1 必须裁定）**：局部函数声明调用由前端产出非根 `CallFuncExecutable`（`BuildExecutableProcessor`）；转译器对该形态显式 fail-fast（"CallFunc 族在非根位置 = 局部函数调用，子集排除"——I2 Goals 明确其属 I4 覆盖 B 范围）；但该类因入口形态（程序根）已在 I2 转译而被归入 `I2_SUBSET` 分区，live `B_FAMILY` 不含它——**矩阵是类级断言，对形态级缺口失明**。最普通的"函数声明 + 调用"语料当前仍 fail-fast，Phase 1 落显式裁定项。
- B 族构成（live `ExecNodeBaseline.B_FAMILY`，单一事实源）：
  - 函数/闭包族 10：`VarFunctionExecutable`、`VarExecutableFunction`、`LazyCompiledExecutableFunction`、`FunctionalAdapterExecutable`、`CallFuncWithClosureExecutable`、`BuildFuncRefExecutable`、`BuildClosureBodyExecutable` + 三边缘类；
  - 控制流族 13：`IfExecutable`、`SwitchExecutable`、`ForExecutable`、`ForInExecutable`、`ForOfExecutable`、`WhileExecutable`、`DoWhileExecutable`、`BreakExecutable`、`ContinueExecutable`、`ReturnExecutable`、`TryExecutable`、`ThrowErrorCodeExecutable`、`ThrowExceptionExecutable`；
  - 输出/节点生成族 12：`OutputTextExecutable`、`OutputValueExecutable`、`OutputXmlAttrExecutable`、`OutputXmlExtAttrsExecutable`、`GenNodeExecutable`、`GenNodeAttrExecutable`、`GenXJsonExecutable`、`CollectJsonExecutable`、`CollectNodeExecutable`、`CollectSqlExecutable`、`CollectTextExecutable`、`EscapeOutputExecutable`。
- **函数值的树内形态（live 事实，Phase 1 必须裁定）**：函数字面量由前端（`BuildExecutableProcessor`）直接编译为 `ExecutableFunction` 函数对象，在树中以 `LiteralExecutable` 载荷出现（`ExecutableFunction` 在排除分区，理由"树中仅作 Literal 载荷；其函数体编译/闭包捕获归 B 族（I4）"）——即函数/闭包族的主工作量不在 35 类注册本身，而在"载荷下降为生成私有方法/lambda（设计 §三目标形态）vs 运行期复用解释器函数对象（I2 `invokeGlobalFunction`/`EvalGlobalRegistry` 先例）"这一架构级裁定（含 `executeWithArgExprs` 惰性求参语义的保真），Phase 1 落决策项。
- **输出族的运行期换缓冲（live 事实，Phase 1 必须裁定）**：`OutputText/OutputValue/OutputXmlAttr/OutputXmlExtAttrs/EscapeOutput` 确为输出缓冲上的 API 调用序列（`$out` 隐参可直接承载）；但 `GenNodeExecutable` 会把 out cast 为 `IXNodeHandler`、以 `rt.setOut(...)` 换缓冲再恢复（`CollectNode/CollectJson/CollectText` 同构；`GenXJsonExecutable` 经 `ExprEvalHelper.generateXjson` 换求值上下文）——生成代码无 `EvalRuntime`、`$out` 为固定隐参，"运行期换输出缓冲"在 `$out` 参数模型下的转译形态（换缓冲的承载方式）Phase 1 落决策项。
- 共享 helper 基座 live：`io.nop.xlang.exec.XLangSemantics`（I2 落地基座 + I3 增量 33 方法族，live 合计 76 个 public static 方法；函数调用语义 `invokeEvalFunction0-3`/`invoke`/`invokeGlobalFunction` 已于 I2 共享）。
- corpus 形态先例（I1/I3）：静态单元 = `xlang-compare/static-a/*.xpl`（c:script 编译单元，经标准编译前端取树）；动态单元 = 表达式串三变体（STANDARD / MUTABLE_SCOPE_VAR / TEMPLATE）；harness `RecordingEvalOutput` 已记录输出调用序列（三层断言的副作用域已含输出缓冲比对）。**输出族的 `$out` 通路需要 xpl 模板形态编译单元（非 c:script，经 `compileXpl` 前端）——现 corpus 无此形态单元**，模板单元经包装器执行的测试域通路需本 plan 定稿。
- 输出缓冲 live 事实（设计 java §七依据）：`IEvalOutput` 由 `EvalRuntime` 携带（`rt.getOut()`），不经 `IEvalScope`。
- ExitMode 清零点 live 事实（Goals 不变式的完整锚点）：`setExitMode(null)` 清零点 = `ExecutableFunction`（3 处 execute 路径）+ `CallFuncExecutable`（finally）+ `CallFuncWithClosureExecutable` + `LazyCompiledExecutableFunction`；`BindVarExecutable`（闭包体执行载体）不清零（边界在函数对象调用处）；循环节点消费 break/continue 后清零。
- `missions/xlang-execution-optimization.json` commands 已是三模块口径；本 plan 不新增模块、不改 commands。
- I3 Non-Blocking Follow-ups 移交：`XLangSemantics.invokeGlobalFunction` display 串急切构造（行为等价优化候选，触及 `XLangSemantics` 的 plan 可顺带收敛）；`ClassModel.getConstructorForArgs` 多候选误选（watch-only，nop-core 域）。
- 真正剩余的 gap：B 族 35 类遇即 fail-fast（java 侧证据 = `TestExecTranslationCoverageMatrix` 的 B 族 pending 反例 + `TestExecToJavaTranslatorSource` 的 IfExecutable fail-fast 用例）；`$out` 契约无执行路径验证；java 侧矩阵 pending 集非空（B 族 35 类）。

## Goals

- B 族逐具体节点类转译（**含非根 `CallFuncExecutable` 局部函数调用形态——Phase 1 裁定归属，默认并入转译**），fail-fast 边界收缩到"无 pending 集残留、无形态级缺口"：
  - 函数/闭包族：按设计 java §三（生成私有方法 / lambda + `IEvalFunction` 适配包装——**载荷处置形态 Phase 1 裁定**）；**可变 slot 闭包捕获 cell 契约**——被闭包捕获的可变 slot 一律经显式可变 cell（单元素数组包装等）读写，语义与解释器 slot 写读一致；未被捕获的可变 slot 保持普通局部变量直译；
  - 控制流族：Java 控制流直译；**ExitMode 传播边界不变式**——`ExitMode` 不跨函数/闭包边界传播（清零点全集见 Current Baseline live 锚点），生成代码以生成方法边界为传播边界，闭包/函数体内非局部跳转映射为该函数体的返回、不外传；`Break/Continue/Return` 在生成方法内为原生语句，语句位置语义与解释器一一对应；
  - 输出/节点生成族：`Output*` 子族经 `$out`（`OUT_PARAM` 隐参）的 API 调用序列；`Gen*`/`Collect*` 子族的**运行期换缓冲语义按 Phase 1 裁定形态转译**（保真：换缓冲期间输出进收集器、恢复后回主缓冲）；语义敏感操作走共享 helper。
- 三边缘类最终裁定：逐类裁定为**转译**（并入 java 目标集，矩阵以最小实例真实转译覆盖）或**改判排除分区**（更新 `ExecNodeBaseline` 分区 + `EXCLUDED_REASONS` 逐类理由 + roadmap I4 条目注记）——闭环时 java 侧 B 族 pending 集清零，无维持 pending 的第三态（pending 集存在即矩阵非全绿）。
- **共享基线演进规则落地（Phase 1 定稿）**：`ExecNodeBaseline` 单一事实源内区分 per-backend 目标集口径——java 侧目标集随本 plan 扩至全量、truffle 侧目标集维持 I6 口径（87）直至 I7 闭环；两侧矩阵各自锚定本后端目标集；**java 侧锚点切换与 B 族注册收口在同一次变更内完成（不制造中期红灯窗口）**；truffle 侧矩阵测试的锚点适配纳入本 plan scope（不改 truffle 翻译器与测试语义，行为中性切换）；三边缘类改判排除时 `bFamily()` 同步收缩、两侧一致。
- corpus 覆盖 B 扩充：B 各族静态形态 ≥1（硬要求；输出族以模板单元形态承载 `$out` 通路）；动态形态按动态编译出口自然产生能力配比（能产生则 ≥1，不能产生显式记录类别与原因）；含异常语义单元 ≥1；**含 `$out` 通路模板单元 ≥1（I2 移交闭合的载体）**；既有 corpus 覆盖 A 的防越界白名单重锚定到稳定口径（不因本 plan 扩量而静默放宽）。
- java 列对拍全绿：覆盖 B corpus 单元 java 列 vs 解释器列对拍全绿（三层断言 + 身份断言 = 生成类实例；模板单元含输出缓冲副作用比对；动态单元 java 列不适用按列适用性机制记录）。
- 覆盖矩阵闭环：java 侧目标集扩至全部非排除且未改判排除的具体类（138 - 16 - N，N = 改判排除的边缘类数，裁定后确定）；支持集 ↔ java 目标集双向 set 相等；java 侧 B 族 pending 断言清零（或随三边缘类裁定迁移）；新增节点类红灯注入保持有效；truffle 侧矩阵经锚点适配后保持全绿（其 pending 集仍为 `bFamily()`，与 truffle 翻译器现状一致）。
- 语义敏感操作共享 helper 纪律：B 族语义敏感操作（函数调用/闭包构造与捕获/控制流 ExitMode 语义/输出与换缓冲语义/`Collect*` 族）三类标注法延续（直译无语义风险 / 已共享复用 / 内联提取），禁止为生成代码重写语义等价实现。

## Non-Goals

- truffle 侧翻译/矩阵闭环/内联缓存（I7——本 plan 仅做 truffle 侧矩阵测试的**锚点最小适配**（共享基线演进规则的机械应用），不做任何 truffle 翻译器变更）。
- SHARED 形态、Context 池、并发对拍（I8）。
- 生产代码路径的后端选择/路由/降级（I9）、生成类加载与 RCM 绑定（I10）、构建任务/双清单/native image（I11）。
- 性能优化（含共享 helper 性能优化，见 Non-Blocking Follow-ups）与三后端基准（I12）。
- 生产构建管线接入（本 plan java 列仍为测试域验证载体，`nop-javac` 内存编译通路不变）。

## Scope

### In Scope

- B 族 35 类转译落地（translator 注册 + fail-fast 收缩 + 转译级单测）+ 三边缘类最终裁定。
- B 族语义敏感操作盘点与共享 helper 增量（提取/复用裁定 + 解释器同步改调 + 行为不变回归；含 B 族节点类只读访问器增量——I3 先例）。
- corpus 覆盖 B 单元扩充（含模板单元形态定稿与 `$out` 执行通路接法）与解释器基线。
- java 列对拍扩展到覆盖 B corpus（含 `$out` 模板单元——I2 移交闭合）。
- 覆盖矩阵闭环（java 目标集全量 + pending 清零 + 红灯保持）+ 共享基线 per-backend 口径落地与 truffle 侧矩阵锚点适配。

### Out Of Scope

- 同 Non-Goals。

## Execution Notes

### Phase 1 裁定记录（2026-08-20，全部 repo-observable，I7 可直接消费）

**1. B 族产生路径盘点**（经 `BuildExecutableProcessor` + 前端 live 诊断，scratch 树形证据见当日 log）：

| 族 | 可产生（corpus 载体） | 不可产生（Phase 2 合成树转译级测试覆盖，不进 corpus） |
|---|---|---|
| 控制流 13 | If/Switch/For/ForIn/ForOf/While/DoWhile/Break/Continue/Return/ThrowException（c:script 语句与模板 c:* 标签；Simple* 嵌套变体为常态形态，经顶层类分派覆盖） | **TryExecutable**（`processTryStatement` 委托 super→default 报 not-supported，c:try 同路径死亡）、**ThrowErrorCodeExecutable**（全仓无 `new` 产生点） |
| 函数/闭包 10 | VarFunctionExecutable（函数值调用，嵌套 NoArg..ThreeArg 变体）、CallFuncWithClosureExecutable、BuildFuncRefExecutable（含闭包箭头函数）；**非根 CallFuncExecutable**（局部函数声明+调用） | **VarExecutableFunction**（全仓无产生点）、**FunctionalAdapterExecutable**（全仓无产生点，载荷为运行期 IEvalFunction）、**BuildClosureBodyExecutable**（全仓无产生点）、LazyCompiledExecutableFunction（产生点存在但仅 xlib 编译现场可达，测试域不可低成本构造，按合成路径覆盖） |
| 输出/节点生成 12 | OutputText/OutputValue/OutputXmlAttr/OutputXmlExtAttrs/EscapeOutput（xml/text 模板）、GenNode+GenNodeAttr（node 模板，attr 为宿主描述符）、GenXJson/CollectText/CollectNode/CollectSql（`<c:collect outputMode=...>`） | **CollectJsonExecutable**（`processCollectOutputExpression` 对 xjson 产出 GenXJson，CollectJson 无产生点） |

无整族不可产生——各族 corpus 硬要求维持（各族静态 ≥1）。另：`GenNodeExecutable.visit` 对 `tagNameExpr==null` 常量标签形态 NPE（live 缺陷，corpus 树遍历断言被阻断）——Phase 2 随 B 族只读访问器增量一并修复（null guard，行为中性）。

**2. 非根 `CallFuncExecutable`（局部函数调用形态）裁定：并入转译**。形态 = 生成私有方法 + 调用点实参求值（与函数载荷处置同族）：调用点求值 argExprs（调用者帧）→ `$fn_k($scope, new Object[]{...})`；被调函数 slotNames 帧映射为私有方法内 `$v` 局部变量（方法作用域天然隔离，无内联展开的 slot 命名冲突）；ExitMode 边界 = 私有方法边界（body 内 return/break/continue 原生语句，`CallFuncExecutable.execute` 的 finally 清零由方法边界原生对应——语句位置一一对应，Seq 在每个子表达式后检查 exitMode（live `SeqExecutable.execute`），故原生跳转与解释器逐语句停走一致）。矩阵类级 + 形态级双证据：类级（程序入口形态既有）+ Phase 2 转译级单测"函数声明 + 调用"最小语料 + corpus 静态单元承载（Phase 3）。

**3. 三边缘类最终裁定**：`LocationFunction` → **转译并入**（具体节点类，`return LOC_k` 常量直译与解释器"返回调用点 SourceLocation"逐一对应；EXCLUDED 分区语义保持"仅抽象基类/接口/非节点辅助类"不被污染；无产生路径不影响可转译性，矩阵最小实例真实转译覆盖）。`ReturnScopeValuesExecutable` → **改判排除**（宏 script 编译期执行产物，不进入运行期编译单元树；ScopeValues 载荷 List&lt;LocalVarDeclaration&gt; 无生成源码自包含表示）。`ExecutableFunctionEvalAction` → **改判排除**（宿主 API `XLang.getTagAction` 构造，非树编译产物；implements IExecutableExpression 为宿主互通委托形态）。N=2，`B_FAMILY` 35→33、`EXCLUDED` 16→18（逐类理由已入 `EXCLUDED_REASONS`），java 目标集 = 138-16-2 = **120**。roadmap I4 条目注记已同步。

**4. 共享基线 per-backend 口径落地（行为中性部分）**：`ExecNodeBaseline.javaTargetSet()` = registeredTarget ∪ bFamily（120）；`truffleRegisteredTarget()` = 既有 87（内容一致，I7 收敛）；`registeredTarget()` 语义冻结 = CorpusCoverageA 白名单锚（既有 `isAllowedNodeClass` 锚定不动，不随扩量放宽）。truffle 侧矩阵已切锚 `truffleRegisteredTarget()`（内容不变中性切换，三模块全绿即证）；java 侧矩阵锚点切换归 Phase 2 与 B 族注册同次变更。`TestExecNodeBaselineFreshness` 增 `testPerBackendTargetSetsConsistent` 对新口径保持有效。

**5. 函数值载荷处置决策：载荷下降为生成私有方法 + 同类方法引用适配**（设计 §三目标形态）。`LiteralExecutable(ExecutableFunction)` / `BuildFuncRefExecutable` / `CallFuncWithClosureExecutable` / 非根 `CallFuncExecutable` / `LazyCompiledExecutableFunction` 统一承载：
- 生成类内 `private static Object $fn_k(IEvalScope $scope, Object[] $args, Object[] $captured)`；slotNames → 方法内 `$v` 局部变量；实参槽 = `$args[i]`（i < 提供数），缺省参数槽 = 内嵌字面量（前端 `getDefaultArgValues` 仅产生 Literal/CloneLiteral，故"解释器在调用者帧求值缺省"与"方法内嵌常量"行为等价）；闭包捕获 = `$v<targetSlot[i]> = $captured[i]`（对应 `BindVarExecutable` 语义）。
- 函数值 = `XLangSemantics.generatedFunction(argCount, demandArgCount, Gen_x::$fn_k, capturedArray)`——`XLangSemantics` 新增 `GeneratedEvalFunction implements IEvalFunction` 适配器（invoke/callN → 私有方法；argCount 超限报 ERR_EXEC_TOO_MANY_ARGS 与 `ExecutableFunction.callN` 同码）。`BuildFuncRef` 的 capturedArray = 创建点读取 `$v<sourceSlot>` 当前值（引用槽持 `EvalReference` 对象 → 捕获即共享 cell，与 `bindClosureVars` 快照一致）。
- 拒绝"运行期复用解释器函数对象/按名注册表"：生产通路（I11）转译在构建 JVM、执行在运行 JVM，任何转译期对象引用/注册表 fundamentally 不可跨进程；命名解析（EvalGlobalRegistry 先例）仅适用全局注册函数，不适用局部函数值。
- ExitMode 边界 = 私有方法边界（原生）；cell 契约 = EvalReference 对象经 captured 数组按引用传递（§9）；惰性求参（`executeWithArgExprs` 实参表达式在调用者帧求值）= 调用点求值后传值 ✓。
- **`LazyCompiledExecutableFunction` 载荷（同类决策伞）**：选**转译期 force-compile**（`getCompiled()` 触发惰性编译得 ExecutableFunction → 下降为私有方法直调，无适配器层）——构建期转译与编译同 JVM（I11 口径）成立；拒绝"运行期按名解析"（无名字注册表）、拒绝"捕获注入"（LazyCompiledFunction 对象不可内嵌）。载荷 null/不可解析 → 显式 fail-fast（矩阵最小实例 null 载荷即此路径的反证）。矩阵证据形态 = 支持集成员 + 分派 fail-fast 反证（无独立可转译最小树形态，truffle 侧 NO_TREE_FORM 先例的闭环侧对应）。

**6. 输出缓冲交换转译形态（Gen\*/Collect\* 在 $out 参数模型下）**：**共享 helper + 生成体私有方法回调 + 显式 ExitMode cell 通道**：
- Output\*/EscapeOutput：`$out` API 调用序列直译（无换缓冲）。
- Collect\*/Gen\*/GenXJson：生成体下降为 `private static Object $body_k(IEvalScope $scope, IEvalOutput $out, ExitMode[] $exit)`；换缓冲语义提取至 `XLangSemantics`（collectText/collectJson/collectNode/collectSql/genXjson/genNode——save/set/try-body-finally-restore/collect，解释器对应节点同步改调，行为不变回归）；调用点：`$exit_k` cell + 收集值 + 按 pending exit 分派（RETURN→`return $t`（含收集值，跨语句停走与函数边界清零原生对应）；BREAK/CONTINUE 且点在循环内→原生 `break;`/`continue;`；无循环→`return $t`（解释器边界清零吞没 + Seq 停走语义的合并对应））。
- 保真依据（live `SeqExecutable.execute` 每 child 后检查 exitMode）：pending exit 停走剩余语句 ✓、收集值经 Seq/loop/函数边界链路不变 ✓、try finally 在 unwind 中执行 ✓（Java 原生 finally）。异常路径恢复 = helper 内 finally restore（与解释器同一实现）。

**7. corpus 覆盖 B 形态定稿**：独立装载类 `io.nop.xlang.compare.CorpusCoverageB`（nop-xlang 测试源码）+ 资源目录 `xlang-compare/static-b/`（模板单元 `.xpl`）与动态路径基 `xlang-compare/dynamic-b/`；类别：`b-function-closure` / `b-control-flow` / `b-output-node-gen`；单元 schema 四字段齐备（I1 口径）；类别 → 必含节点规则 + B 范围白名单 = `ExecNodeBaseline.javaTargetSet()`（单一事实源）+ 既有 corpus 白名单回退。既有 CorpusV1/CorpusCoverageA 单元与类别不动。

**8. 模板单元形态与 `$out` 执行通路定稿（I2 契约测试域接法）**：模板单元 = `xlang-compare/static-b/*.xpl` 资源（含输出语义，经 `compileTag(node, outputMode)` 标准前端取树——`compileXpl` 文本入口 outputMode=none 拒绝输出，node/xml/text 模式经 XNode parse + compileTag）；java 列按 `EvalMethodConvention.OUT_PARAM` 契约生成第二隐参 `IEvalOutput $out`（`execute(IEvalScope $scope, IEvalOutput $out)`）并以 `request.getOut()`（harness 的 RecordingEvalOutput）为实参直接反射调用入口方法（`GeneratedEvalBinding.findEntryMethod` 按参数个数定位）；期望值声明 = `ExpectedOutcome.outputCalls(...)`（副作用域既有承载，harness 三层断言的输出缓冲比对即 `$out` API 调用序列比对）；`RecordingEvalOutput` 扩展实现 `IXNodeHandler`（记录 beginNode/endNode/simpleNode 事件为 RecordedOutputCall 新 Op——GenNode 族通路对两列对称可达，无 CCE）。纯表达式单元（c:script 无输出）签名不变（`execute(IEvalScope $scope)`），java 列按参数个数区分调用。

**9. 可变 slot 闭包捕获 cell 契约转译形态**：解释器 cell = `EvalReference`（被捕获可变 slot 由前端转 useRef，slot 值即 EvalReference 对象；`BuildFuncRefExecutable` 捕获的是该对象引用 → `bindClosureVars` 快照 → `BindVarExecutable` 写入函数帧目标槽）。生成代码同构：外层方法 `$v<slot>` 按 A 族既有翻译持 EvalReference；函数创建点 captured 数组按值传递该**对象引用**（= 共享 cell）；函数体内 `ReferenceIdentifier/ReferenceAssign/ReferenceSelfAssign/Inc/Dec` 经 A 族既有共享 helper（getRefValue/setRefValue/asRef）读写同一对象 ✓ 语义一致。未被捕获的可变 slot 保持普通局部变量直译（前端未转 useRef 的 slot 不出现在任何 sourceSlots 数组中，判定依据 = BuildFuncRef/CallFuncWithClosure 的 sourceSlots 成员检查，转译期静态可知）。

## Execution Plan

### Phase 1 - 覆盖 B 盘点、三边缘类与局部函数调用形态裁定、共享基线演进与 corpus 模板形态定稿

Status: completed
Targets: 本 plan 与当日 log（裁定记录）；`nop-kernel/nop-xlang/src/test/java/io/nop/xlang/compare/ExecNodeBaseline.java`（per-backend 口径 + 三边缘类裁定若改判排除则更新分区）；`nop-kernel/nop-xlang-truffle/src/test/java/io/nop/xlang/truffle/TestTruffleCoverageMatrix.java`（锚点适配——行为中性切换）；corpus 覆盖 B 装载类与资源目录落点（nop-xlang 测试源码，I3 先例）

- Item Types: `Decision | Proof`

- [x] B 族产生路径盘点（I3 Phase 1 同口径，经 `BuildExecutableProcessor` + 前端语法/编译选项 live 诊断）：控制流族与函数/闭包族经 c:script 语句单元的可产生性逐类确认；输出/节点生成族经 xpl 模板单元（`compileXpl` 前端，非 c:script）的产生路径确认；**无法经标准前端产生的节点类显式记录**（其转译验证走 Phase 2 合成树转译级测试，不进 corpus）；若某族整族证实不可产生，该族 Phase 3 corpus 硬要求降级为"合成树转译级测试覆盖 ≥1 + 盘点证据记录"（显式裁定，非静默跳过）
- [x] **非根 `CallFuncExecutable`（局部函数调用形态）归属裁定**：默认并入本 plan 转译（生成私有方法 + 调用，与函数载荷处置决策同族；"函数声明 + 调用"是函数/闭包族 corpus 与 ExitMode/cell 行为级测试的最自然语料）；或显式排除并记录 successor 归属——不允许无裁定残留（矩阵类级断言对形态级缺口失明，裁定须显式覆盖形态层面）
- [x] 三边缘类最终裁定（`LocationFunction`/`ReturnScopeValuesExecutable`/`ExecutableFunctionEvalAction`）：逐类裁定为转译（并入 java 目标集）或改判排除分区（更新 `ExecNodeBaseline` 分区与 `EXCLUDED_REASONS` 逐类理由）；裁定依据逐类记录（产生路径 + 语义族 + 可转译性）；裁定结果同步 roadmap I4 条目范围行注记（一次小修订）
- [x] **共享基线演进规则定稿并落地（行为中性部分）**（决策记录 + 代码化）：`ExecNodeBaseline` 引入 per-backend 目标集口径（java 侧目标集 = 非排除且未改判排除的全部具体类；truffle 侧目标集 = 既有 87 口径，I7 闭环时收敛到同一全量）；本 Phase 落地 truffle 侧矩阵锚点适配（`TestTruffleCoverageMatrix` 的支持集断言与 pending 数据源改锚 truffle 侧口径——内容不变的行为中性切换，适配后 `:nop-xlang-truffle` 全绿即证）；**java 侧矩阵锚点切换不在本 Phase**（归 Phase 2 与 B 族注册收口同次变更，避免"目标集 122 vs 支持集 87"的中期红灯窗口）；既有 `CorpusCoverageA` 防越界白名单重锚到既有 87 口径具名 accessor（`registeredTarget()` 语义 = `i3Scope() ∪ I2_SUBSET`，不随本 plan 扩量放宽）；`TestExecNodeBaselineFreshness` 对新口径保持有效
- [x] **函数值载荷处置决策**：树中 `LiteralExecutable` 载荷携带的 `ExecutableFunction` 函数对象（body 树 + slotNames + 默认参数 + `executeWithArgExprs` 惰性求参语义）——载荷下降为生成私有方法/lambda（设计 §三目标形态）vs 运行期复用解释器函数对象（I2 `invokeGlobalFunction`/`EvalGlobalRegistry` 先例）vs 混合；裁定含依据与对拍影响分析（ExitMode 边界/cell 契约/求值顺序在两形态下的保真方式）；**同类决策伞覆盖 `LazyCompiledExecutableFunction` 的载荷**（live 载荷 = `XplLibTagCompiler.LazyCompiledFunction` 运行时对象，不可从字面量重建——转译期 force-compile / 运行期按名解析 / 捕获注入，三选一记录）
- [x] **输出缓冲交换转译形态决策**：`Gen*`/`Collect*` 子族的运行期换缓冲（`rt.setOut` 换/恢复、`IXNodeHandler` cast、`GenXJson` 求值上下文交换）在 `$out` 参数模型下的承载方式（换缓冲经共享 helper + 生成体回调 / 参数重赋值逐层传递 / 其他——Phase 1 定稿）；保真要求：换缓冲期间输出进收集器、恢复后回主缓冲、异常路径不丢恢复
- [x] corpus 覆盖 B 形态定稿（决策记录）：独立装载类 `CorpusCoverageB`（nop-xlang 测试源码，`io.nop.xlang.compare` 包；`CorpusV1`/`CorpusCoverageA` 既有单元与类别不动）+ 静态资源目录；单元 schema 四字段齐备（I1 口径）；类别 → 必含节点规则与 B 范围白名单（java 目标集口径单一事实源，I3 先例）
- [x] **模板单元形态与 `$out` 执行通路定稿**（决策记录，I2 契约的测试域接法）：模板单元（含输出语义的 xpl 编译单元）如何进入 corpus——经标准编译前端取树后，java 列如何按 `EvalMethodConvention.OUT_PARAM` 契约以第二隐参 `$out` 执行（`JavaBackendColumn` 测试域通路扩展）；期望值如何声明输出缓冲内容（副作用域承载）；harness 既有 `RecordingEvalOutput` 的复用方式
- [x] 可变 slot 闭包捕获 cell 契约的转译形态决策记录（设计 java §三既定契约的落地形态：cell 载体形态、被捕获 slot 的读写改写规则、未捕获 slot 保持直译的判定依据）

Exit Criteria:

- [x] 产生路径盘点 + 局部函数调用形态裁定 + 三边缘类裁定 + 共享基线演进规则（truffle 侧锚点适配落地 + java 侧切换时序裁定）+ 函数载荷处置 + 换缓冲形态 + corpus 形态 + `$out` 执行通路 + cell 契约形态，全部 repo-observable（本 plan Execution note 或当日 log；I7 可直接消费）
- [x] 局部函数调用形态与三边缘类裁定已同步 `ExecNodeBaseline`（若改判排除/形态并档）与 roadmap I4 条目注记（repo-observable）
- [x] truffle 侧矩阵测试经锚点适配后、java 侧矩阵（消费共享 test-jar）回归后 `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` 全绿（行为中性切换，不改翻译器与测试语义；java 侧锚点未切换、无中期红灯窗口）
- [x] No owner-doc update required（矩阵与裁定属 ai-dev 层产物；docs-for-ai 同步归 I11）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 覆盖 B 转译落地与共享 helper 增量

Status: completed
Targets: `nop-kernel/nop-xlang-java/src/main/java/io/nop/xlang/java/`（translator/gen）、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/`（helper 增量、解释器改调与 B 族节点类只读访问器增量——I3 先例）、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/XLangSemantics.java`

- Item Types: `Proof`

- [x] B 族语义敏感操作盘点：逐节点类 × 逐语义分支三类标注（直译无语义风险 / 已共享复用 / 内联提取至 `XLangSemantics`），清单落当日 log；重点：函数调用与闭包构造捕获语义（与解释器同一实现；含 Phase 1 载荷处置裁定的落实）、控制流 ExitMode 传播与边界清零语义（清零点全集逐一对应；`Break/Continue/Return` 语句位置一一对应；`Try` 的异常交互）、输出 API 调用序列与换缓冲语义（Phase 1 形态裁定的落实）、`Collect*`/`Gen*` 家族的节点构造与收集语义
- [x] 内联待提取项提取为共享 helper，解释器对应节点同步改调（行为不变）；已共享项直接引用不重复造（"缺哪个提取哪个"——I6 同源裁定，不受 I7 进度影响）
- [x] translator 注册 B 族（含裁定转译的边缘类与非根 `CallFuncExecutable` 形态，若裁定并入）逐具体节点类：语义敏感操作生成代码统一调共享 helper；SourceLocation 静态常量内嵌覆盖新可抛错点；子集外 fail-fast 边界相应收缩（错误信息仍报节点类名 + SourceLocation；java 侧 fail-fast 反例随边界收缩迁移更新——`TestExecToJavaTranslatorSource`/矩阵反证用例）
- [x] **java 侧矩阵锚点切换**：与 B 族注册收口在同一次变更内完成（支持集达到 java 目标集时同步切锚 `TestExecTranslationCoverageMatrix`——无"目标集 122 vs 支持集 87"红灯窗口）；`registeredTarget()` accessor 的 javadoc 语义注记同步更新（切换后其实际角色 = truffle 锚点（至 I7）+ CorpusCoverageA 白名单，防误用为 java 目标集）；无独立树形态的 B 类（如 `GenNodeAttrExecutable` 属性描述符、`LazyCompiledExecutableFunction`）的矩阵"真实转译"证据形态一并裁定（宿主节点载体 / 支持集成员 + 合成路径，I3 pending 侧先例的闭环侧对应口径）
- [x] 转译级单测：每族 ≥1 真实转译断言（corpus 单元或合成树源码级断言；无法经前端产生的节点以合成树覆盖）；fail-fast 反证 ≥1 例（边界收缩后的新边界，闭环后以"支持集外合成节点"反证）；可变 slot 闭包捕获 cell 行为级测试 ≥1（被捕获 slot 写读经 cell 与解释器语义一致）；ExitMode 边界清零行为级测试 ≥1（函数/闭包内非局部跳转不外泄，含局部函数调用形态）

Exit Criteria:

- [x] 覆盖 B（含裁定转译的边缘类和非根 `CallFuncExecutable` 形态，若裁定并入）全部具体节点类与形态可转译——Phase 2 自身可判：转译器支持集可编程枚举且覆盖 Phase 1 定稿的 B 族转译集 + "函数声明 + 调用"最小语料可转译（矩阵 live 扫描交叉验证归 Phase 3 联动）
- [x] 盘点清单 repo-observable（log）；解释器改调共享 helper 后 `./mvnw test -pl :nop-xlang -am` 全绿（回归基线与 I3 收口时一致或仅有新增测试）
- [x] 每族 ≥1 转译单测 + fail-fast 反证 + cell 行为级测试 + ExitMode 边界测试在仓（repo-observable 测试用例）
- [x] 无静默跳过：新注册节点转译中无法处理的形态显式 fail-fast，不默认通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - corpus 覆盖 B 对拍全绿、`$out` 执行验证与矩阵闭环

Status: completed
Targets: `nop-kernel/nop-xlang/src/test/`（corpus 与基线）、`nop-kernel/nop-xlang-java/src/test/`（java 列与矩阵）、`nop-kernel/nop-xlang-truffle/src/test/`（矩阵锚点适配回归，Phase 1 落地物的复验）

- Item Types: `Proof`

- [x] 构建覆盖 B corpus 单元：各族静态 ≥1（输出族以模板单元形态；**函数/闭包族静态单元显式含"局部函数声明 + 调用"形态语料**——非根 `CallFuncExecutable` 形态的 corpus 级对拍由该语料承载，或裁定记录中写明其承载单元）、动态按自然产生能力配比（不能产生显式记录）、异常语义单元 ≥1（错误码 + 预期源位置）、模板单元（`$out` 通路）≥1；单元 schema 四字段齐备；Phase 1 盘点证实整族不可产生的族按其裁定以合成树转译级测试覆盖替代并记录（不算静默跳过）
- [x] 解释器基线列全量执行覆盖 B corpus（单列阶段判定基准 = 列结果 vs 单元声明预期）
- [x] java 列对拍全量执行覆盖 B corpus（三层断言 + 身份断言 + truffle 列缺席显式记录；**模板单元含输出缓冲副作用比对——I2 移交闭合**；动态单元 java 列不适用按列适用性机制区分）
- [x] 覆盖矩阵闭环：java 目标集扩至全部非排除且未改判排除的具体类（138 - 16 - N）；支持集 ↔ java 目标集双向 set 相等；java 侧 B 族 pending 断言清零（三边缘类裁定迁移到位；无树形态类按 Phase 2 裁定的证据形态覆盖）；新增节点类红灯注入保持有效（`FutureExecutable` 红/绿对照复验）；truffle 侧矩阵（Phase 1 锚点适配后）全绿复验

Exit Criteria:

- [x] 覆盖 B corpus 单元 repo-observable（清单/类别/schema/动态缺席记录）；解释器基线全绿；**java 列 vs 解释器列对拍全绿（含身份断言 + 模板单元输出缓冲比对）——roadmap I4 验收第一项**
- [x] **覆盖矩阵全绿——roadmap I4 验收第二项**：java 目标集逐类注册断言全绿（逐类最小实例真实转译，非清单自证）；java pending 集清零可断言；新增节点类红灯经注入验证（红/绿可控）；truffle 侧矩阵锚点适配后全绿
- [x] **I2 移交闭合**：`$out` 包装器契约执行路径验证在仓（模板单元 java 列对拍含输出缓冲副作用比对；I2 Deferred 责任链闭环记录）
- [x] **端到端验证**：corpus B 模板单元 → 树编译 → 转译器 → 生成源码（`$out` 第二隐参）→ 测试域编译加载 → 执行 → 三层对拍断言（含输出缓冲）全链可运行
- [x] **接线验证**：java 列身份断言（生成类实例）在覆盖 B 单元（含模板单元）上持续成立（非解释器兜底）
- [x] 回归不削弱既有解释器测试（纪律 3）：`TestCorpusV1JavaColumn` 22/22、`TestCorpusCoverageAJavaColumn` 33/33、truffle 侧既有测试（含 `TestCorpusV1TruffleColumn` 22/22、`TestCorpusCoverageATruffleColumn` 33/33）与 nop-xlang/nop-xlang-java 既有测试保持全绿
- [x] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` 全绿
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全类别 corpus java 列 vs 解释器列对拍全绿（含 java 列身份断言）——roadmap I4 验收第一项
- [x] 覆盖矩阵全绿（java 目标集逐节点类注册断言，新增节点类红灯；java pending 集清零；truffle 侧锚点适配后全绿）——roadmap I4 验收第二项
- [x] I2 移交的模板入口包装器契约执行路径验证闭合（`$out` 模板单元对拍在仓）
- [x] 三边缘类最终裁定 repo-observable（转译并入或改判排除，无第三态残留）
- [x] 共享基线 per-backend 口径落地（单一事实源无漂移；truffle 侧矩阵适配不改变其测试语义）
- [x] ExitMode 传播边界不变式有行为级验证（不跨函数/闭包边界；清零点全集对应；语句位置一一对应）
- [x] 可变 slot 闭包捕获 cell 契约有行为级验证（与解释器语义一致）
- [x] 语义敏感操作无双实现（共享 helper 纪律）：生成代码调用的语义 helper 与解释器同一实现来源
- [x] 回归不允许削弱现解释器测试（纪律 3；含 corpus 覆盖 A 白名单重锚后不放宽）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] owner-docs：No owner-doc update required（docs-for-ai 同步归 I11）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 已验证（a）模板单元经 `$out` 通路端到端连通（b）矩阵注册证据 = 真实转译验证而非清单自证（c）无空方法体/静默跳过/no-op
- [x] `./mvnw compile -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am`
- [x] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C`
- [x] checkstyle / 代码规范检查通过（`-Pqa checkstyle:check` 变更模块）

## Deferred But Adjudicated

（无——起草时无新 deferred 项；I2 移交的 `$out` 执行验证已纳入 In Scope 闭合。执行中产生时按 guide 补录并写明 Why Not Blocking Closure。）

## Non-Blocking Follow-ups

- `XLangSemantics.invokeGlobalFunction` display 串急切构造——I2 closure audit 裁定的行为等价优化候选（non-blocking，仅错误路径可观测）；本 plan Phase 2 触及 `XLangSemantics` 时可顺带收敛，未收敛则继续移交（不作为 closure 门）。
- `ClassModel.getConstructorForArgs` 多 1 参构造候选误选 0 参构造（watch-only residual，nop-core 域存量，I3 移交延续；双后端同源行为一致故对拍不因此分歧）。

## Closure

Status Note: B 族 33 类（Phase 1 三边缘类裁定后口径）全部逐类转译落地（含非根 `CallFuncExecutable` 局部函数调用形态、`LocationFunction` 转译并入；两类改判排除分区）；corpus 覆盖 B（19 单元：函数/闭包 4 静态 + 2 动态、控制流 5、模板 8）java 列 vs 解释器列对拍全绿（三层断言 + 身份断言 + 输出缓冲副作用比对）；I2 移交的 `$out` 第二隐参契约执行路径验证在仓闭合（模板单元端到端：compileTag → 转译 → `$out` 入口 → RecordingEvalOutput 对拍）；覆盖矩阵闭环（java 目标集 120 双向 set 相等 + 逐类真实转译 + pending 清零 + 红灯注入保持；truffle 侧锚点适配后全绿）。三 Phase Exit Criteria 逐项 PASS，无 deferred 项，无第三态残留。docs-for-ai 同步归 I11（owner-doc 裁定在案）。
Completed: 2026-08-21

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh closure-audit 子 agent（read-only），task `ses_fdff23ea7ffeN3POIvcB21hN32`，verdict **CAN CLOSE（0 Blocker / 0 Major / 2 Minor）**；Minor ①执行侧上下文引述"263 tests"为 nop-xlang-java 单模块数（三模块合计 1159 = 514+263+382，全绿，非 repo 缺陷）②收口日志条目待补——本日 `ai-dev/logs/2026/08-21.md` 落盘即闭合。
- Evidence（逐 Closure Gate，live 锚点）：
  - 对拍全绿 + 身份断言：`TestCorpusCoverageBJavaColumn` 19/19（身份断言 = 生成类实例 `assertNotSame`）；回归 `TestCorpusV1JavaColumn` 22/22、`TestCorpusCoverageAJavaColumn` 33/33——PASS
  - 矩阵闭环：`TestExecTranslationCoverageMatrix` 122/122（支持集 ↔ `javaTargetSet()`(120) 双向 + pending 清零 `javaTargetSetFullySupported` + `FutureExecutable` 红灯注入 + 证据形态裁定类专门断言 + 改判排除两类 fail-fast 反证）；`TestTruffleCoverageMatrix` 122/122（锚 `truffleRegisteredTarget()`(87)）——PASS
  - `$out` 移交闭合：`EvalMethodConvention.OUT_PARAM`；`JavaBackendColumn` 双参入口反射调用（`findEntryMethod` + `request.getOut()`）；模板单元 `ExpectedOutcome.outputCalls(...)` + harness 输出缓冲 cross-compare——PASS
  - 三边缘类裁定：`ExecNodeBaseline` B_FAMILY 33（含 `LocationFunction`）/ EXCLUDED 18（逐类理由）；排除类矩阵 fail-fast 反证——PASS
  - per-backend 口径：`javaTargetSet()`=120 / `truffleRegisteredTarget()`=87；`TestExecNodeBaselineFreshness.testPerBackendTargetSetsConsistent` 5/5——PASS
  - ExitMode 边界行为级：`TestExecToJavaTranslatorCoverageB` 4 例（函数内 return/break/continue 不外泄 + collect 体内 return + 模板循环 return）18/18 全绿——PASS
  - cell 契约行为级：`testClosureMutableSlotCellContract` + corpus 单元 `fn-closure-cell.xpl`；`XLangSemantics.generatedFunction`/`GeneratedEvalFunction` 同源实现——PASS
  - 共享 helper 无双实现：解释器 `CollectJson/CollectText/CollectNode/GenNode*` 同步改调 `XLangSemantics.*`；生成代码经 `genSwapCall` 发同一 helper 调用——PASS
  - 回归不削弱：corpus v1 / 覆盖 A java+truffle 双列 22/22、33/33 全绿——PASS
  - 无静默降级：7 个不可产生类全部有覆盖（矩阵最小实例 4 + 证据形态裁定 2 + Try 合成树 1）；动态缺席显式断言——PASS
- Anti-Hollow：(a) 模板单元端到端链路 live 追踪连通（`static-b/*.xpl` → `compileTag` → `ExecToJavaTranslator` → `JdkJavaCompiler` 内存编译 → 双参入口反射调用 `RecordingEvalOutput`（implements `IXNodeHandler`）→ 三层断言含输出缓冲比对）；(b) 矩阵证据 = `MinimalNodeFactory.minimalTree` 真实实例逐类转译（含 `GenNodeAttrExecutable` 宿主载体形态），非清单自证；(c) `scan-hollow-implementations.mjs --module nop-xlang / nop-xlang-java / nop-xlang-truffle --severity high` 均 0 critical/0 high、exit 0；B 族路径无空方法体/吞异常。
- 工具门（2026-08-21 live 复跑）：`./mvnw compile -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am` EXIT=0；`./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` BUILD SUCCESS（合计 1159 tests / 0 failures / 0 errors = nop-xlang 514 + nop-xlang-java 263 + nop-xlang-truffle 382）；`./mvnw -Pqa checkstyle:check` 同模块集 EXIT=0（仅 2 处存量 UnusedImports WARN 于 `ThrowErrorCodeExecutable.java`，非违规）；`node ai-dev/tools/check-plan-checklist.mjs <本 plan> --strict` EXIT=0。
- Deferred 项分类检查：`Deferred But Adjudicated` 为空（I2 `$out` 移交已 In Scope 闭合）；Non-Blocking Follow-ups 两项均为裁定在案的非阻塞项（优化候选 + watch-only nop-core 存量），无 in-scope live defect 降级。

Follow-up:

- `XLangSemantics.invokeGlobalFunction` display 串急切构造——行为等价优化候选（non-blocking，错误路径可观测；I2 closure audit 裁定延续移交）。
- `ClassModel.getConstructorForArgs` 多 1 参构造候选误选——watch-only residual（nop-core 域存量，I3 移交延续）。
- 其余无 plan-owned 剩余工作。
