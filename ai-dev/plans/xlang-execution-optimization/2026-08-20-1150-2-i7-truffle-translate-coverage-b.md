# I7 truffle 翻译覆盖 B（函数闭包/控制流/输出族）+ 两级内联缓存 + 覆盖矩阵闭环

> Plan Status: completed
> Last Reviewed: 2026-08-21
> Source: `ai-dev/backlog/xlang-execution-optimization-roadmap.md` I7（类别同 I4 = 设计 `ai-dev/design/xlang-java/01-architecture-baseline.md` §三分类表）；设计冻结于 `ai-dev/design/xlang-truffle/02-architecture-baseline.md`（§三控制流 ExitMode→异常族、§四帧映射与 context-independent 准则、§五多线程与 EXCLUSIVE 过渡形态、§六两级内联缓存准则、§七翻译器/缓存键纪律）；对拍口径 = 设计 `ai-dev/design/xlang-execution/01-architecture-baseline.md` §五
> Mission: xlang-execution-optimization
> Work Item: I7
> Related: I6（前置：truffle 覆盖 A + truffle 侧矩阵 + 指纹载荷纪律，本 plan 直接消费）；I4（执行顺序在前 `{N}`=1：corpus 覆盖 B 单元、B 族产生路径盘点与三边缘类裁定的正常供给方 + 共享基线 per-backend 口径的落地方——fallback 裁定见 Current Baseline，roadmap 依赖仅 I6，I4 非依赖）；I8（后继：SHARED 形态/Context 池/并发对拍/缓存淘汰——本 plan 保持 EXCLUSIVE 形态并显式移交）

<!-- Draft review: round-1（fresh session ses_fe2b1846fffebvZW1nbEtuICIm，3 Major+4 Minor：内联缓存适用集可被空洞满足/fallback 机制缺件/闭包物化锚点错误且预定结论/Try catch 坑/Phase 2 密度/防冲突单侧）→ 修复 → round-2（fresh session ses_fe2a1f0e9ffeHsCj0ybBAt5yhD，确认 F2-F6 真正修复、F1 结构性修复；新发现 2 Major：输出换缓冲 truffle 侧无决策项（XLangContext 无换缓冲 API）/接线证据"行为级验证"不能证伪摆设缓存 + 4 Minor：方法名漂移/StaticFunction 漏具名/闭包双拷贝时序载体/roadmap 137 计数）→ 修复（输出换缓冲决策项 + 可观测缓存状态证据五处贯通 + invokeGlobalFunction 等实路径名 + StaticFunction 具名 + 两拷贝时序载体语料各 ≥1）→ round-3（fresh session ses_fe2934a7effeFXfDExtSVbKPMr，无 Blocker/Major：R2-1/R2-2 及全部 Minor 确认真正修复且与 live 精确一致；R2-6 roadmap 137 计数当场实际修正；N3-1 Exception 子类载体路线盘点要求与 N3-2 无树类证据形态对齐项当场补入）→ 共识达成 → active。 -->

## Purpose

把 truffle 翻译器从覆盖 A（87 类）推进到覆盖 B 全族：35 类逐具体节点类翻译（EXCLUSIVE 形态不变），`ExitMode` → 控制流异常族按 SL 模式三值一一对应，闭包捕获语义按 live 解释器机制裁定并落地（含帧物化形态裁定）；两级内联缓存按设计 §六准则落地并验证（一级 CallTarget 身份 / 二级直达调用形态；禁止函数实例身份与任何运行时值身份缓存；**适用面含既有函数调用节点的缓存回填**）；语义特化 fast-path 仅在已证实语义等价处引入（generic/fallback 走共享 helper）；corpus 覆盖 B 单元 truffle 列 vs 解释器列对拍全绿（静态 + 动态）；truffle 侧覆盖矩阵与 java 侧同基线同口径闭环（目标集收敛到 I4 落地的 per-backend 全量口径）。

## Current Baseline

- I6 产物存在（前置断言——执行本 plan 前须核验 I6 已 `completed`）：`nop-kernel/nop-xlang-truffle` 模块——`ExecToTruffleTranslator`（支持集 87 类可编程枚举；子集外 fail-fast 报节点类名 + SourceLocation）、truffle 侧矩阵 `TestTruffleCoverageMatrix` 124 用例（与 java 侧同一 `ExecNodeBaseline` 实体：支持集 ↔ truffle 目标集双向一致 + 87 类逐类最小实例真实翻译 + B 族 35 类 pending fail-fast + `FutureExecutable` 红灯注入）、corpus 覆盖 A truffle 列 `TestCorpusCoverageATruffleColumn` 33/33、`TreeFingerprints` 白名单全量覆盖 + 可翻译-未白名单 fail-fast 守卫（`TestTreeFingerprintPayloadCoverage` 158 用例，payload/subtree 双变体逐类）、`FrameLayoutMapper` READ/WRITE 声明齐 A 族用法（live javadoc 注记：**MATERIALIZE 在当前支持集内无使用，闭包捕获节点归本 plan**——实际用法来源依 Phase 1 闭包形态裁定）。
- **truffle main 现无任何 DSL 特化/缓存用法（live 事实）**：truffle 模块主代码全目录无 `@Cached`/`@Specialization`/`@CachedLibrary`；既有函数调用节点的翻译产物全部走 generic 静态 helper 路径（`XGlobalFuncNode` → `XLangSemantics.invokeGlobalFunction`（`FunctionExecutable` 族）、`XObjMethodNode` → `invokeObjMethod`（`ObjFunction` 族）、`XStaticMethodNode` → `invokeStaticMethodResolved`（`StaticFunctionExecutable`））——**两级内联缓存的主要落点恰是这些既有调用节点**（设计 §六"函数调用节点（`CallFuncExecutable`/`ObjFunction` 族的翻译产物）"），不只挂在本 plan 新增的 B 族闭包调用形态上。
- **非根 `CallFuncExecutable`（局部函数调用形态）**：live 同一形态缺口存在于 truffle 侧（支持集 87 含该类仅入口形态；非根形态是否翻译未经语料验证）——归属裁定消费 I4 Phase 1 落地口径（fallback 触发时由本 plan 按 I4 同款裁定项裁定），corpus"函数声明 + 调用"语料随供给方。
- **闭包捕获的 live 解释器机制（Phase 1 裁定的锚点）**：解释器闭包捕获**不是**共享作用域链，而是**急切值拷贝**——且有两个拷贝时序不同的载体：`BuildFuncRefExecutable`（函数作值）在**捕获时**把 sourceSlots 值拷入函数对象 `vars[]`（捕获后外部写不可见；经 `ExecutableFunction.bindClosureVars` → `BindVarExecutable` 写入被调帧 slot）；`CallFuncWithClosureExecutable`（局部具名函数）在**调用时**从当前帧拷值到目标 slot（捕获后外部写可见）；可变共享经 Reference cell（引用族已由 I6 翻译，cell 对象按引用拷贝）。设计 truffle 02 §四"闭包捕获：MaterializedFrame（物化帧），对应解释器的 EvalScope 捕获语义"的表述与该 live 机制存在张力（设计文档冻结不改）——物化共享帧 vs 与解释器同构的值拷贝，两形态及其等价前提（前端 useRef 纪律下"写后被捕获的变量全走 cell"）由 Phase 1 裁定，设计文本张力记 log 供后续设计修订（I2 先例）。
- I5 移交 I8 项（非本 plan 范围，显式排除）：SHARED 形态切换（`@Registration` 编译期常量变更）、Context 池租借协议、并发正确性对拍、翻译缓存淘汰（容量上限/LRU）——本 plan 全程保持 EXCLUSIVE 单 Context 过渡形态（I5 落地，翻译正确性验证载体）。
- 设计冻结依据（truffle 02）：§三控制流——`ExitMode`（CONTINUE/BREAK/RETURN）翻译为控制流异常族（SL 模式，三值一一对应；与 Truffle 嵌入侧 Context Exit 无关不对接）；§四——context-independent 准则（节点不存 context 数据或运行时值）；§五 context-independent 翻译纪律（禁值身份推测）；§六两级内联缓存准则表（一级 = CallTarget 身份、二级 = 直达调用形态；禁止函数实例身份一级缓存与任何运行时值身份缓存；缓存上限与泛化遵循 DSL 规范；宿主方法/属性访问走 `@CachedLibrary` limit=3 模式）；§七——特化节点仅承担已证实语义等价的加速路径，generic/fallback 一律调用共享 helper。
- I4 关系（I3→I6 同构先例）：roadmap 对 I7 的依赖仅 I6（done）；**I4 是 corpus 覆盖 B 单元、B 族产生路径盘点、三边缘类最终裁定与共享基线 per-backend 口径的正常供给方**（执行顺序 `{N}`=1 在前；I4 侧已写对称条款）。**fallback 裁定（Phase 1 定稿记录）**：触发条件 = 执行本 plan 时 I4 plan 不处于 `completed` 状态（含 active 进行中/延期/被拒）；触发时对 I4 已落地部分做**增量对账**并补齐缺失产物——corpus 覆盖 B 单元（含模板单元形态与 `$out` 通路定稿）、B 族产生路径盘点（I4 Phase 1 同口径）、三边缘类裁定与基线扩量（由本 plan 作出并落 `ExecNodeBaseline`），"先落地方为事实源"（I5 D3 / I6 先例）；I4 后续执行时对账消费本 plan 产物。正常路径（I4 done）直接消费，不重复推导。
- 共享 helper：`XLangSemantics`（I2 落地基座 + I3 增量 33 方法族，live 合计 76 个 public static 方法；函数调用语义 I2 已共享）；"缺哪个提取哪个"规则延续（本 plan 翻译所需而 nop-xlang 尚未共享的操作由本 plan 提取最小集，不受 I4 进度影响——I6 同源裁定）。
- `missions/xlang-execution-optimization.json` commands 已是三模块口径；本 plan 不新增模块、不加依赖、不改 commands。
- 真正剩余的 gap：B 族 35 类翻译遇即 fail-fast（`TestTranslatorFailFast` 以 `OutputTextExecutable` 为反例即证）；无函数调用内联缓存（含既有调用节点）；闭包捕获形态未裁定未落地；truffle 侧矩阵 pending 集非空（B 族 35 类）。

## Goals

- B 族 35 类逐具体节点类翻译（**含非根 `CallFuncExecutable` 局部函数调用形态——消费 I4 裁定口径，fallback 时由本 plan 裁定**；三边缘类消费 I4 最终裁定口径，fallback 触发时由本 plan 裁定并记录）：fail-fast 边界收缩；语义敏感操作 generic 路径统一走共享 helper；**语义特化 fast-path 自本 plan 引入**——仅承担已证实语义等价的加速路径，且特化与 generic 共存不得改变语义（对拍全绿即约束）。
- **ExitMode → 控制流异常族**：三值一一对应（SL 模式）；跨函数/闭包边界清零语义与解释器一致（清零点 = `ExecutableFunction` 3 路径 + `CallFuncExecutable`（finally）+ `CallFuncWithClosureExecutable` + `LazyCompiledExecutableFunction`；`BindVarExecutable` 不清零；循环消费后清零——I4 plan 同一 live 锚点）；异常载体选型须考虑与 `TryExecutable` catch 的交互（live `TryExecutable` catch 分支捕获 `Exception` 且必然 adapt 重抛——控制流异常族若为 `Exception` 子类会被截获，载体选型或 Try 翻译显式放行须裁定）。
- **闭包捕获语义按 live 机制裁定并落地**：物化共享帧（`MaterializedFrame` + MATERIALIZE 声明）vs 与解释器同构的急切值拷贝（含 Reference cell 可变共享路径）——两形态由 Phase 1 裁定（含等价前提验证："写后被捕获的变量全走 cell"的前端 useRef 纪律）；**验证语料含"捕获后外部写 → 闭包内读"在两个拷贝时序载体上各 ≥1**（`BuildFuncRef` 捕获时拷贝——外部写不可见；`CallFuncWithClosure` 调用时拷贝——外部写可见）；节点 context-independent 保持。
- **输出/节点生成族的换缓冲翻译形态按 Phase 1 裁定落地**：`Output*` 子族经 `XLangContext` 输出缓冲 API 序列；`Gen*`/`Collect*`/`GenXJson` 的运行期换缓冲（live `rt.setOut` 换/恢复 + `IXNodeHandler` cast + 求值上下文交换）在 truffle 侧的承载机制（context 持有缓冲的 swap/restore、异常路径不丢恢复、cast quirk 保真）Phase 1 落决策项。
- **两级内联缓存落地**（设计 §六准则）：适用面 = **函数调用类节点的翻译产物，含既有节点回填**（`FunctionExecutable`/`CallFuncExecutable`/`ObjFunction` 族/`StaticFunctionExecutable` + 本 plan 新增的闭包/局部函数调用形态——逐节点形态枚举或显式裁定不适用并记录理由）；一级 CallTarget 身份 / 二级直达调用形态；**禁止**函数实例身份作一级缓存、禁止对任何运行时值身份建缓存；缓存上限与泛化遵循 DSL 规范（超限进泛化路径，不无限扩容）；宿主对象方法/属性访问互操作消息走 `@CachedLibrary` limit=3 模式；**接线证据用可观测缓存状态**（miss 计数器 / DSL 特化态迁移探针 / 缓存初始化探针——至少覆盖一处既有节点回填点；行为级对拍无法区分"缓存工作"与"缓存不存在"，Phase 1 决策记录落定载体）。
- 树指纹载荷覆盖扩展至 B 族：`TreeFingerprints` 白名单扩至 B 族全部可翻译类（标量载荷 + 子树皆混入），可翻译-未白名单 fail-fast 守卫保持；payload/subtree 双变体逐类测试（I6 纪律延续，防缓存串用）。
- corpus 覆盖 B truffle 列 vs 解释器列对拍全绿（三层断言 + 身份断言 = 翻译 AST 经 CallTarget 执行；静态/动态按单元适用性——动态单元 truffle 列适用）。
- truffle 侧覆盖矩阵闭环：与 java 侧同基线同口径（同一 `ExecNodeBaseline` 实体）全绿；**目标集从 I4 落地的 truffle 侧 87 口径收敛到 per-backend 全量口径**；pending 集清零；新增未注册节点类红灯保持。
- `org.graalvm.*` 不泄漏口径保持（收口复跑不泄漏断言）。

## Non-Goals

- java 侧转译与矩阵（I4——fallback 触发时按 Phase 1 裁定补 corpus 单元、产生路径盘点与基线裁定（落 nop-xlang 测试源码），不建 java 侧转译产物）。
- SHARED 形态切换、Context 池、并发正确性对拍、翻译缓存淘汰容量/LRU（I8，I5 移交延续）。
- 生产代码路径的后端注册 SPI 接入与路由（I9）。
- native image 兼容（I11）。
- 性能基准（含 Context 池成本实测、Q1 Bytecode DSL / Q4 共享 Engine 重评估触发口径量化——归 I12）。

## Scope

### In Scope

- B 族 35 类翻译落地（translator 注册 + fail-fast 收缩 + 翻译级单测 + 缓存键载荷覆盖）。
- ExitMode 控制流异常族（含 Try 交互语义与载体选型）+ 闭包捕获语义裁定与落地（物化或值拷贝，含 MATERIALIZE 声明扩展 if 裁定为物化形态）。
- 两级内联缓存落地与正确性验证（**含既有函数调用节点的缓存回填**；命中/未命中/多态超限泛化路径）。
- 语义特化 fast-path（仅已证实语义等价处）与共享 helper 增量（"缺哪个提取哪个"）。
- corpus 覆盖 B truffle 列对拍（正常路径消费 I4 corpus；fallback 按 Phase 1 裁定，含 corpus/基线编辑落 nop-xlang 测试源码）+ 内联缓存/控制流边界/闭包捕获语料缺口补齐。
- truffle 侧覆盖矩阵闭环（目标集收敛 + 红灯保持）。

### Out Of Scope

- 同 Non-Goals。

## Execution Notes

### Phase 1 裁定记录（2026-08-21，全部 repo-observable）

**0. 前置与 fallback 裁定**：I6 `completed`（roadmap live 核验）；**I4 `completed`（2026-08-21，roadmap live 核验）→ fallback 未触发**，正常路径直接消费 I4 产物：B 族最终口径 = 33 类（三边缘类：`LocationFunction` 转译并入、`ReturnScopeValuesExecutable`/`ExecutableFunctionEvalAction` 改判排除）、非根 `CallFuncExecutable` 并入转译、corpus 覆盖 B 19 单元（`CorpusCoverageB` + `static-b/` 资源）、per-backend 基线口径（`javaTargetSet()`=120 / `truffleRegisteredTarget()`=87 直至本 plan 收敛）、共享基线白名单 = `registeredTarget()` 语义冻结。I4 Phase 1 §1-9 裁定逐项消费，不重复推导；本 plan 不编辑 nop-xlang corpus 装载类（无 fallback 补齐）。

**1. ExitMode 控制流异常族设计定稿**（三值一一对应，SL 模式）：

- 载体 = **非 `Exception` 直系自定义载体不可行**——采用 Truffle 惯例 `ControlFlowException` 子类（`ControlFlowException extends RuntimeException`，**仍是 `Exception` 子类**），因此选型 = **"Exception 子类载体 + 传播路径全部 catch 点显式放行"**（Phase 1 决策项二选一中的后者）。`catch (Exception)` 包装点全量盘点与处置：
  - `TryExecutable` 翻译（XTryNode）：catch 分支前显式 `catch (XLControlFlowException) { throw e; }` 放行；catchExpr/finallyExpr 自身抛出的控制流异常被吞入 cell 后仍按 live 语义被原始异常的重抛覆盖（interpreter 为 flag 语义：catch 体内 break 只置 flag，随后 `NopException.adapt(e)` 必然重抛原始异常——异常胜出，控制流 flag 死亡；已知残余边缘：finally 内多语句 Seq 在 flag 置位下的提前停走不可经异常载体复现，corpus 无此形态，记录在案）；
  - `CallFuncExecutable`/`CallFuncWithClosureExecutable` 翻译（XLocalCallNode）：函数边界在 `XLangFunctionRootNode` 内消化（XLReturn→值 / XLBreak、XLContinue→null = 边界清零），控制流异常**从不外泄**到 wrapCallFuncException 的 catch(Exception)——无需放行点；
  - `XLangSemantics` 共享 helper 的 `catch (Exception)`/`catch (NopException)` 点：控制流异常不可能进入——被调者为解释器/宿主世界代码（自身 EvalRuntime，无 truffle 控制流异常源）；truffle 函数值经 `XLangTruffleFunction.invoke` 调用时异常已在函数边界消化；
  - 换缓冲回调（XOutputSwapNode/XGenNodeNode）：显式 `catch (XLControlFlowException)` 记入 ExitMode cell 并抑制（I4 cell 协议承载），helper 的 finally restore 不受影响；
  - `XLangRootNode`：既有 `catch (ControlFlowException) rethrow` 之上新增根边界消费（XLReturn→captureReturned(值)；XLBreak/XLContinue→captureReturned(null)，对应 root CallFunc finally 清零）。
- 清零点全集逐一对应：`ExecutableFunction` 3 路径 + `CallFuncExecutable`(finally) + `CallFuncWithClosureExecutable` + `LazyCompiledExecutableFunction` → 统一由被调体 `XLangFunctionRootNode` 边界消化（= 解释器 setExitMode(null) 清零点一一对应；`BindVarExecutable` 不清零——truffle 侧 captured 绑定即 BindVar 对应物，不设边界 ✓）；循环消费后清零 → XLBreak/XLContinue 被循环节点 catch 消费（消费即清零，无残留态）；`XSeqNode` 无需 exitMode 检查（异常载体天然实现"置位后停走剩余语句"）。
- Try 交互语义：catch 命中控制流异常 = **不可能**（放行先行）——"Try catch 命中控制流异常"语料的实际断言形态 = 控制流异常穿过 try 体不被 catch 截获（body 内 break 不触发 catch 分支）；finally 穿透 = 控制流异常穿越 try 时 finallyExpr 执行（Java 原生 unwind）。

**2. truffle 侧输出/节点生成族换缓冲翻译形态决策**（对照 I4 Phase 1 §6/§8）：**共享 helper（I4 已提取的 collectText/collectJson/collectNode/collectSql/genXjson/genNode 换缓冲族）+ `XLangContext` 换缓冲协议扩展**：

- `XLangContext` 新增 `swapOutput(IEvalOutput)`/`restoreOutput(IEvalOutput)`（live 仅有 bind/clear/require 系；换缓冲 = context 持有缓冲的线程绑定 swap/restore，EXCLUSIVE 单线程栈式安全；SHARED/池归还协议兼容性 = 归还前清空既有纪律覆盖，I8 无额外移交项）。
- `Collect*`/`GenXJson`：XOutputSwapNode 以 IGeneratedOutBody 回调接入共享 helper，回调内 swap/restore + 控制流异常→ExitMode cell 抑制；helper 返回收集值后按 I4 调用点分派协议（cell RETURN→XLReturn(收集值)；BREAK/CONTINUE 且点在循环内→对应异常由循环消费；无循环→XLReturn(收集值) = 边界清零吞没 + 收集值为终值的合并对应）；循环内判定 = 翻译期词法循环嵌套静态标志。异常路径不丢恢复 = helper/回调双层 finally restore。
- `GenNode`：XGenNodeNode 经 `XLangSemantics.genNode`（DisabledEvalOutput→收集返回 XNode / 否则 `(IXNodeHandler)` cast 直发——cast quirk 保真，CCE 原样传播）；attr/tagName 求值在换缓冲后（live 求值顺序一致）；body 经 genNodeHandler 的 Runnable 回调换缓冲执行。
- `$out` 通路：模板单元经 `XLangContext.requireOutput()`（窗口绑定 = harness 的 RecordingEvalOutput，与 java 列第二隐参同一对象来源）。

**3. 两级内联缓存设计定稿**（准则 = 设计 §六表；适用面逐节点形态枚举，无既有调用主路径静默遗漏）：

| 节点形态 | 裁定 | 理由/机制 |
|---|---|---|
| `VarFunctionExecutable`/`VarExecutableFunction`（本 plan 新增翻译） | **L1+L2 两级缓存**（XFunctionDispatchNode，DSL） | 函数值调用点天然多态；L1 guard = CallTarget 身份（`truffleTarget(function) == cachedTarget`，逐调用重取，**不缓存函数实例身份**）；L2 = 命中路径 `DirectCallNode`（PE 可内联）；limit=3 + generic replaces（`XLangSemantics.callVarFunction` 共享 helper）；探针计数器（directCalls/genericCalls）随节点暴露 = 可观测缓存状态 |
| 非根 `CallFuncExecutable`/`CallFuncWithClosureExecutable`/`LazyCompiledExecutableFunction`（新增） | **L2 直达（静态 DirectCallNode）** | 被调目标 = 翻译期已知的函数体 RootNode CallTarget（编译期常量），无分派多态，无需 L1 guard；CallFuncWithClosure 调用时闭包拷贝（args→captured 求值次序与解释器一致）；LazyCompiled 消费 I4 force-compile 裁定（转译期 `getCompiled()`，null/不可解析显式 fail-fast） |
| `BuildFuncRefExecutable`（新增） | 函数值生产方 | `XLangTruffleFunction`（implements IEvalFunction）= CallTarget + targetSlots + **捕获时值快照**（对应 bindClosureVars）；可变共享经 Reference cell（sourceSlots 槽持 EvalReference 对象按引用拷贝 = 共享 cell，I4 §9 同构） |
| `LiteralExecutable(ExecutableFunction)` 函数字面量载荷 | **载荷下降**（XFunctionValueNode） | 与 java 侧 I4 §5 对称的 truffle 形态：函数体翻译为独立 RootNode（own FrameDescriptor），值 = XLangTruffleFunction（无捕获时 captured=空） |
| **`StaticFunctionExecutable` → XStaticMethodNode（既有节点回填）** | **回填：L2 直达形态 = 解析产物编译期常量化** | 树内 `methodCollection` 为编译期常量（`StaticFunctionExecutable.getMethodCollection()`，解释器直译同一常量 + 同一 helper `invokeStaticMethod`）；truffle 节点直持该常量消除逐调用 `Class.forName`+反射模型解析（与 `XNewObjectNode` 持 ClassModel 同构模式）；**接线证据 = 节点持有常量与源树实例同一性断言 + 直调 invokeStaticMethod（非 Resolved 变体）** |
| `FunctionExecutable` 族 → XGlobalFuncNode（既有） | 不适用（generic 共享 helper 保持） | 被调者 = `EvalGlobalRegistry` 的 IFunctionModel（宿主反射模型，无 CallTarget 载体）；I2 裁定按名运行时解析（最新注册语义）；按运行时值身份缓存被 §六禁止 |
| `ObjFunction` 族 → XObjMethodNode（既有） | 不适用（节点级） | 分派依赖接收者动态类型，无 CallTarget；per-funcName 反射分派缓存在共享 helper（`ObjFunctionHandle` 单态 class 缓存，解释器/java/truffle 同一实现）——节点级重复缓存 = 双实现风险 |
| `CallFuncExecutable` 根形态（既有） | 不适用 | 程序入口帧绑定，单次执行无分派 |
| `@CachedLibrary` limit=3 | 适用面 = **空**（记录） | 现翻译全走共享 helper 宿主反射（invokeObjMethod/getProperty 等），无 Truffle 互操作库消息分派路径；为缓存而引入 interop 包装层 = 双实现，拒绝 |

- 身份纪律验证语料与断言：同函数重复调用（命中路径 directCalls 探针增长 + 结果一致）；多态调用点（同位置先后不同函数 = 不同 CallTarget）3 个以内双命中、**第 4 个超限进泛化**（探针可观测 directCalls 停增/genericCalls 增长 + 结果正确）；**值身份不误命中**——同 BuildFuncRef 产生的两个函数值（同 CallTarget、不同 captured）经同一缓存条目直调、各自 captured 传递正确（结果不同即证非值身份缓存）。
- 首个 DSL 特化/缓存用法：XFunctionDispatchNode（dsl-processor 已由 I5 配置于 pom annotationProcessorPaths）。

**4. 闭包捕获形态裁定**（真裁定，依据 live 保真度 + Truffle 收益）：**与解释器同构的急切值拷贝**（否决物化共享帧）：

- 依据：live 解释器闭包捕获是**急切值拷贝**且有两拷贝时序载体——`BuildFuncRef`（捕获时拷入函数对象，捕获后外部写**不可见**）与 `CallFuncWithClosure`（调用时从当前帧拷值，外部写**可见**）；`MaterializedFrame` 共享帧形态会使 BuildFuncRef 载体的"捕获后外部写不可见"语义**不可复现**（共享帧下外部写恒可见）——语义保真度判定物化形态出局。可变共享经 Reference cell（引用对象按值拷贝 = cell 共享）两形态同构复现。Truffle 优化收益不作为语义让步的交换条件（设计 §七纪律）。
- 落地形态：函数体 = 独立 RootNode（own FrameDescriptor，`FrameLayoutMapper.mapFunction`）；BuildFuncRef → 捕获时快照 captured[]（XLangTruffleFunction 载荷）；CallFuncWithClosure → 调用时 callerFrame 读 sourceSlots 传参绑定 targetSlots。MATERIALIZE 声明**无用法来源**（值拷贝形态不物化帧）——`FrameLayoutMapper` javadoc 口径更新依据在案。
- 等价前提（"写后被捕获的变量全走 cell"）**未经证实即不依赖**：两拷贝时序载体语料（fn-arrow-call 系 = BuildFuncRef 捕获时 / 合成 CallFuncWithClosure 调用时）各自覆盖"捕获后外部写 → 闭包内读"断言（可见性按载体分别断言，非 cell 前提断言）。
- 设计 truffle 02 §四"闭包捕获：MaterializedFrame（物化帧）"文本与 live 机制张力 → 记 log 供后续设计修订（I2 先例：设计文档冻结，张力移交）。

**5. 消费 I4 的矩阵证据形态口径**：`GenNodeAttrExecutable`（宿主节点载体——GenNode 翻译的属性描述符数组，非树节点）；`LazyCompiledExecutableFunction`（支持集成员 + null 载荷显式 fail-fast 反证，无独立可 visit 最小树形态）；`FunctionalAdapterExecutable`（truffle 无跨 JVM 约束 → **真实翻译**：载荷 IEvalFunction 为编译期常量，节点直持构造 `EvalFunctionalAdapter`——与 java 侧 fail-fast 证据形态差异 = per-backend 证据形态先例）。

### Phase 2/3 执行记录（2026-08-21）

**6. 落地切片**（三切片各自全绿后推进）：控制流族（异常族 + 循环消费 + Try 交互 + 根/函数边界）→ 函数/闭包族（载荷下降 + 两拷贝时序载体 + XFunctionDispatchNode DSL 缓存 + XStaticMethodNode 回填）→ 输出/节点生成族（XLangContext swap/restore + I4 helper 回调 + ExitMode cell 协议 + cast quirk）。

**7. 缓存观测语料落点裁定**：多态调用点语料与同函数重复调用语料（含探针断言）**不进 corpus**（I4 corpus 单元为对账冻结产物；且 corpus harness 不支持 AST 探针断言），落点 = `TestTranslatorCoverageB` 合成树 + 反射 AST 遍历探针（directCalls/genericCalls 可观测断言 + XStaticMethodNode 常量同一性断言）；corpus 侧由既有单元承载行为级对拍（fn-local-call = 同函数重复调用、fn-arrow-call/dyn-iife/dyn-closure = 函数值通路）。

**8. 共享 helper 增量**：零提取——I4 已提取集合（collectText/collectJson/collectNode/collectSql/genXjson/genNode 换缓冲族 + callVarFunction + asForInMap/forOfIterator + wrapCallFuncException + throwErrorCode/throwException/output 族）覆盖本 plan 全部需求；nop-xlang 主代码零变更（"缺哪个提取哪个"——无缺口）。

**9. MATERIALIZE 声明口径更新依据**：急切值拷贝形态下函数体为独立 RootNode + 独立 FrameDescriptor、无帧物化——`FrameLayoutMapper` javadoc 已按 §4 裁定更新（MATERIALIZE 无用法来源；被调帧入口槽以 WRITE 非字面量写记入 `mapFunction` 布局）。

**10. Phase 3 语料缺口逐项闭合**（corpus 承载 / 合成树承载落点）：

| 语料要求 | 承载 | 锚点 |
|---|---|---|
| 多态调用点（内联缓存验证，含超限泛化） | 合成树 | `TestTranslatorCoverageB.testPolymorphicSiteOverflowsToGeneric`（directCalls=3 + genericCalls≥1 探针 + 结果正确） |
| 同函数重复调用（命中路径） | corpus + 合成树 | `fn-local-call`（f(1)+f(2) 行为级）+ `testBuildFuncRefCapturesEagerly`（directCalls=2/genericCalls=0 探针） |
| 值身份不误命中 | 合成树 | `testBuildFuncRefCapturesEagerly`（同 CallTarget 双 captured 各自正确） |
| 控制流边界（函数内 return/break/continue + 边界清零） | corpus + 合成树 | `fn-local-call`/`ctrl-*` 族 + `testFunctionBoundaryClearsControlFlow`（return 取值 / break、continue 越界吞没 null） |
| 跨函数/闭包边界清零 | 合成树 | 同上（`XLangFunctionRootNode` 边界消化断言） |
| Try 与控制流异常交互（catch 不截获 + finally 穿透） | 合成树（Try 无前端产生路径，I4 盘点口径） | `testTryPassesControlFlowAndRunsFinally`（放行 + finally=99 unwind 断言）+ `testTryCatchTriggersOnRealExceptionOnly` |
| 嵌套循环 break/continue 消费层级 | corpus + 合成树 | `ctrl-for-break-continue` + `testForBreakContinueAndNestedLoopConsumption`（同树解释器等值） |
| 闭包捕获（捕获时载体：外部写不可见） | corpus + 合成树 | `fn-arrow-call`/`dyn-iife`/`dyn-closure` + `testBuildFuncRefCapturesEagerly`（捕获后写 → 读旧值断言） |
| 闭包捕获（调用时载体：外部写可见） | 合成树（CallFuncWithClosure 无低成本 corpus 形态） | `testCallFuncWithClosureCopiesAtCallTime`（声明后写 → 调用读新值断言） |
| 可变 slot cell round-trip | corpus | `fn-closure-cell`（inc()×2 经 Reference cell 共享 → 3） |
| 输出换缓冲（收集器/恢复/主缓冲零泄漏） | corpus + 合成树 | `tpl-collect-*` 3 单元 + `testCollectTextSwapsBufferAndRestores`/`testCollectExceptionPathRestoresBuffer`（异常路径不丢恢复） |
| pending exit 分派（collect 内 return/break） | 合成树 | `testCollectPendingExitDispatch`（RETURN→收集值；循环内 break→循环消费） |
| 既有节点回填接线证据 | 合成树 | `testStaticMethodNodeBackfillHoldsCompileTimeConstant`（常量同一性 assertSame + 行为断言） |

**11. I8 移交项显式记录（责任链 repo-observable）**：本 plan 全程保持 EXCLUSIVE 单 Context 过渡形态（`@Registration` 编译期常量未变更）；SHARED 形态切换、Context 池租借协议、并发正确性对拍（多线程经池并发求值 + 无跨 Context 串值断言）、翻译缓存淘汰（容量上限/LRU）移交 I8（I5 Deferred 既定归属延续；`XLangContext.swapOutput` 的池归还兼容性 = 归还前清空既有纪律覆盖，无额外移交项）。

## Execution Plan

### Phase 1 - 口径对齐、fallback 裁定、控制流异常族/内联缓存/闭包形态设计定稿

Status: completed
Targets: 本 plan 与当日 log（裁定与决策记录）；fallback 触发时另含 `nop-kernel/nop-xlang/src/test/`（corpus 装载类、`ExecNodeBaseline` 扩量——正常路径为消费不编辑）

- Item Types: `Decision | Proof`

- [x] 核验前置与供给方状态：I6 已 `completed`；I4 状态检查（fallback 触发条件 = I4 不处于 `completed`），据此定稿 fallback 裁定并记录（正常路径消费 I4 产物：corpus 覆盖 B、产生路径盘点、三边缘类裁定、per-backend 基线口径；fallback = 增量对账 + 补齐缺失裁定与产物，"先落地方为事实源"）——**I4 completed（2026-08-21 核验），fallback 未触发**（Execution Notes §0）
- [x] **fallback 态补齐步骤（若触发，I6 先例同款）**：未触发（I4 completed；本项按条件不适用，显式记录）
- [x] 消费/对齐 I4 口径产物：B 族最终裁定结果（三边缘类转译/排除 + **非根 `CallFuncExecutable` 局部函数调用形态裁定**）、corpus 覆盖 B 单元与类别口径、共享基线 per-backend 口径（**含无独立树形态 B 类的矩阵"真实翻译"证据形态口径——`GenNodeAttrExecutable`/`LazyCompiledExecutableFunction` 等，对齐 I4 Phase 2 裁定**）——truffle 侧逐项对齐（同基线同口径实体 = `ExecNodeBaseline`；`TestExecNodeBaselineFreshness` 有效 + truffle 目标集定义在位；fallback 未触发无需本 plan 落地）（Execution Notes §0/§5）
- [x] **ExitMode 控制流异常族设计定稿**（决策记录）：三值异常载体一一对应关系与载体选型（`ControlFlowException` 子类 + **传播路径全部 `catch (Exception)` 点显式放行**——盘点：XTryNode 放行 / 函数边界消化使 wrap 点不可达 / 共享 helper 不可达 / 换缓冲回调 cell 抑制 / 根节点消费）；函数/闭包边界清零点（I4 同一 live 锚点全集，含 `CallFuncExecutable` finally）；与 `Try` 的 catch/finally 交互语义（含 catch 体内控制流被原始异常重抛覆盖的 live 对应 + finally 多语句停走残余边缘记录）；对拍验证语料要求（见 Phase 3 消费）（Execution Notes §1）
- [x] **truffle 侧输出/节点生成族换缓冲翻译形态决策**（对照 I4 Phase 1 对应决策项）：`Gen*`/`Collect*`/`GenXJson` 的运行期换缓冲在 truffle 侧的承载机制裁定（`XLangContext` 持有输出缓冲的 swap/restore 协议扩展 + I4 共享 helper 回调接入 + ExitMode cell 协议；异常路径不丢恢复；`IXNodeHandler` cast quirk 保真；与"输出缓冲线程绑定"协议及 I8 SHARED 移交的兼容性）（Execution Notes §2）
- [x] **两级内联缓存设计定稿**（决策记录，准则 = 设计 §六表）：适用节点集——**显式枚举既有函数调用节点（`FunctionExecutable`/`CallFuncExecutable`/`ObjFunction` 族/`StaticFunctionExecutable` 翻译产物）的缓存回填 + 本 plan 新增闭包/局部函数调用形态**，逐节点形态枚举或显式裁定不适用并记录理由（不允许静默遗漏既有调用主路径）；一级缓存身份载体（CallTarget 身份）与二级缓存形态（直达调用路径）的 guard 语义；上限与泛化纪律（limit=3 + generic replaces）；**正确性验证口径**——缓存命中与未命中两路径执行结果一致（同函数重复调用语料）、多态调用点（同一调用位置先后调用不同函数）超限进泛化路径且结果正确、缓存不引入值身份误命中（多态语料对拍全绿即约束载体）；**接线证据载体落定**——可观测缓存状态断言（探针计数器 directCalls/genericCalls + XStaticMethodNode 常量同一性断言，覆盖既有节点回填点 XStaticMethodNode 与函数值调用点 XFunctionDispatchNode）；宿主方法/属性访问 `@CachedLibrary` limit=3 模式适用面 = 空（记录理由）（Execution Notes §3）
- [x] **闭包捕获形态裁定**（真裁定项，非预定结论）：物化共享帧（`MaterializedFrame` + MATERIALIZE 声明扩展）vs 与解释器同构的急切值拷贝（sourceSlots 拷入函数载体 + Reference cell 可变共享）——两形态按 live 语义保真度（**含两个拷贝时序载体的差异保真**：`BuildFuncRef` 捕获时拷贝 vs `CallFuncWithClosure` 调用时拷贝）、Truffle 优化收益（PE 消除/帧逃逸）、等价前提裁定 → **急切值拷贝**（物化形态无法复现 BuildFuncRef 载体"捕获后外部写不可见"语义）；**验证语料含"捕获后外部写 → 闭包内读"在两个拷贝时序载体上各 ≥1**（cell/非 cell 维度随载体覆盖）；裁定后确定 MATERIALIZE 声明无用法来源（javadoc 口径更新依据在案）；设计 §四文本张力记 log 供设计修订（I2 先例）（Execution Notes §4）

Exit Criteria:

- [x] fallback 裁定 + I4 口径对齐（或 fallback 补齐记录，含非根 `CallFunc` 形态裁定）+ 四个设计定稿（控制流异常族含 Try 交互 / 两级内联缓存含既有节点回填枚举与接线证据载体 / 闭包形态裁定含等价前提验证 / 输出换缓冲翻译形态），全部 repo-observable（本 plan Execution note 或当日 log）
- [x] 内联缓存身份纪律可验证（禁函数实例身份与运行时值身份缓存——验证语料与断言方式在决策记录中落定，Phase 2/3 消费）；适用面枚举无既有调用主路径静默遗漏；接线证据载体落定（可观测缓存状态断言，至少覆盖一处既有节点回填点）
- [x] No owner-doc update required（docs-for-ai 同步归 I11）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 覆盖 B 翻译落地、闭包形态落地与两级内联缓存

Status: completed
Targets: `nop-kernel/nop-xlang-truffle/src/main/java/io/nop/xlang/truffle/`（translate/nodes/frame）、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/XLangSemantics.java`（共享 helper 增量，按"缺哪个提取哪个"）与 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/`（解释器改调与只读访问器增量，仅提取/消费所需时——I3 先例）

> 执行策略注记：本 Phase 机制密度高（35 类翻译 + 控制流异常族 + 闭包形态 + 全模块首个 DSL 特化/缓存用法），可按"B 族翻译 → 既有节点缓存回填"内部切片执行，每切片保持模块全绿后再进下一切片。

- Item Types: `Proof`

- [x] B 族逐具体节点类翻译：语义敏感操作 generic 路径走 `XLangSemantics` 共享 helper（与 java 侧/解释器同一实现来源）；语义特化 fast-path 仅在已证实语义等价处引入（fast-path 与 generic 共存，特化不得改变语义）；SourceLocation → SourceSection 回映射覆盖新可抛错点（`NopException` 语义，`SyntheticSources` 既有机制）——33 类 + 非根 CallFunc 形态全量落地（Execution Notes §6-§8）
- [x] ExitMode 控制流异常族落地（Phase 1 定稿口径）：三值一一对应；函数/闭包边界清零（清零点全集 = `XLangFunctionRootNode` 统一消化 + 根节点消费）；Try 交互（载体选型 = Exception 子类 + 全 catch 点显式放行，含 catchExpr/finallyExpr 内 CF 被原始异常覆盖的 live 对应）
- [x] 闭包捕获形态落地（Phase 1 裁定口径）：急切值拷贝（含 Reference cell 可变共享）；节点 context-independent 保持（不存 context 数据或运行时值——函数值载荷 = 编译期常量/CallTarget，捕获快照随值对象非节点）
- [x] 两级内联缓存落地（Phase 1 定稿口径）：既有函数调用节点回填（**XStaticMethodNode = methodCollection 编译期常量直持**）+ 新增闭包/局部函数调用形态（静态 DirectCallNode）+ 函数值调用点 XFunctionDispatchNode（DSL，L1 CallTarget 身份 + limit=3 + generic=`callVarFunction`）；无函数实例身份与运行时值身份缓存；超限泛化不无限扩容；宿主互操作 `@CachedLibrary` 适用面 = 空（记录理由）
- [x] **树指纹载荷覆盖扩展**：`TreeFingerprints` 白名单扩至 B 族全部可翻译类（标量载荷 + 子树皆混入；可翻译-未白名单 fail-fast 守卫保持）；payload/subtree 双变体逐类测试扩展（212 用例全绿，含函数载荷全量混合 slotNames/参数规格/缺省/函数体）
- [x] 翻译级单测：每族 ≥1 翻译断言（corpus 单元或合成树；无法经前端产生的节点以合成树覆盖）；fail-fast 反证随边界收缩迁移更新（闭环后无 pending 集：FutureExecutable + 排除两类 + LazyCompiled null 载荷反证 + 边界不回退守护 `testCoverageFamiliesNowTranslatable`）；内联缓存正确性单测（命中/未命中一致 + 多态超限泛化 + 既有节点回填路径 + 可观测缓存状态断言按 Phase 1 载体）；闭包形态单测（含"捕获后外部写 → 闭包内读"两个拷贝时序载体各 ≥1）；控制流边界单测（函数内 return/break/continue + 边界清零 + Try catch/finally 交互）；输出换缓冲单测（换缓冲期间输出进收集器、恢复后回主缓冲、异常路径不丢恢复）——`TestTranslatorCoverageB` 17 用例

Exit Criteria:

- [x] 覆盖 B 全部具体节点类可翻译——Phase 2 自身可判：翻译器支持集可编程枚举且覆盖 Phase 1 对齐的 B 族转译集（矩阵 live 扫描交叉验证归 Phase 3 联动）——支持集 120 = `truffleRegisteredTarget()` 收敛口径双向相等
- [x] **树指纹载荷覆盖逐类测试扩展到位**（仅语义载荷不同的树对 → 异指纹；复合节点类含仅子树结构不同的树对；同 sourceKey 不串用）；无"可翻译但载荷未混合"的静默兜底（fail-fast 守卫分支保持，212 用例全绿）
- [x] 内联缓存身份纪律在码可审（缓存载体不含函数实例身份/运行时值身份——代码审查口径记录：guard 逐调用重取 `targetOf(function)`，缓存条目 = CallTarget + DirectCallNode）+ 行为级验证（命中/未命中一致 + 多态泛化正确 + **既有节点回填路径有验证**——XStaticMethodNode 常量同一性 + 行为断言）+ **可观测缓存状态断言在仓**（探针计数器 directCalls/genericCalls 覆盖函数值调用点与既有节点回填点）
- [x] 闭包形态裁定落地有测试（含"捕获后外部写 → 闭包读"在两个拷贝时序载体（`BuildFuncRef` 捕获时 / `CallFuncWithClosure` 调用时）上各 ≥1，cell/非 cell 维度随载体覆盖（fn-closure-cell corpus 单元 + 合成树非 cell 载体）；物化形态裁定为否决——MATERIALIZE 无用法来源，javadoc 口径更新在案）
- [x] 共享 helper 增量涉及解释器改调时，`./mvnw test -pl :nop-xlang -am` 全绿（回归基线一致或仅有新增测试）——**零增量零改调**（I4 已提取集合无缺口，Execution Notes §8；nop-xlang 主代码零变更，514 测试全绿）
- [x] 每族 ≥1 翻译单测 + fail-fast 反证在仓（repo-observable）；无静默跳过（无法处理形态显式 fail-fast：null 函数载荷/LazyCompiled 不可解析载荷均显式报错有断言）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - corpus 覆盖 B truffle 列对拍全绿与矩阵闭环

Status: completed
Targets: `nop-kernel/nop-xlang-truffle/src/test/`（列扩展与矩阵）、corpus（正常路径消费 I4 产物；fallback 为本 plan Phase 1 裁定产物，落 nop-xlang 测试源码）

- Item Types: `Proof`

- [x] truffle 列对拍全量执行覆盖 B corpus（静态 + 动态按单元适用性；三层断言 + 身份断言 + java 列缺席/不适用记录按 I1 机制区分）——`TestCorpusCoverageBTruffleColumn` 20/20（19 单元 + 语料缺口核验锚点用例；fallback 未触发无需补齐，corpus 为 I4 冻结产物直接消费）
- [x] 语料缺口补齐与逐项核验（Phase 1 语料要求）：**多态调用点语料**（合成树 + 探针，落点裁定 Execution Notes §7）+ **同函数重复调用语料**（corpus `fn-local-call` + 探针合成树）；**控制流边界语料**（函数内 return/break/continue + 跨函数/闭包边界清零 + **Try 与控制流异常交互（放行 + catch 不截获断言形态）≥1 + finally 穿透 ≥1 + 嵌套循环 break/continue 消费层级 ≥1**——corpus ctrl 族 + 合成树）；**闭包捕获语料**（可变 slot 捕获 round-trip `fn-closure-cell` + "捕获后外部写 → 闭包内读"两个拷贝时序载体各 ≥1）；**输出换缓冲语料**（`Gen*`/`Collect*` 单元 tpl-collect-* 3 形态——换缓冲期间输出进收集器、恢复后回主缓冲 + 异常路径恢复合成树）——逐项闭合表 Execution Notes §10
- [x] truffle 侧覆盖矩阵闭环：支持集 ↔ truffle 目标集（**收敛到 per-backend 全量口径 120**，`ExecNodeBaseline.truffleRegisteredTarget()` 与 `javaTargetSet()` 内容一致 + `TestExecNodeBaselineFreshness.testPerBackendTargetSetsConsistent` 同步收敛）双向 set 相等；逐类最小实例真实翻译；B 族 pending 断言清零（`testBFamilyPendingSetCleared` 33 类逐类支持）；`FutureExecutable` 红灯注入红/绿对照复验 + 排除两类 fail-fast 反证 + LazyCompiled null 载荷反证
- [x] I8 移交项显式记录：EXCLUSIVE 形态保持声明 + SHARED 切换/Context 池/并发对拍/缓存淘汰移交 I8（Execution Notes §11，责任链 repo-observable）

Exit Criteria:

- [x] 覆盖 B corpus truffle 列 vs 解释器列对拍全绿（含身份断言 = 翻译 AST 经 CallTarget 执行）——**roadmap I7 验收第一项**（静态含模板单元 + 动态单元双列，20/20）
- [x] **覆盖矩阵全绿（与 java 侧同基线同口径）——roadmap I7 验收第二项**：逐类注册断言全绿（126 用例：120 逐类 + 口径 + pending 清零 + 排除反证 2 + LazyCompiled 反证 + 红灯注入）；pending 集清零可断言；新增节点类红灯经注入验证
- [x] 内联缓存/控制流边界/闭包捕获语料在仓且对拍全绿（Phase 1 语料要求逐项闭合记录 Execution Notes §10，含 Try 与控制流异常交互与 finally 穿透）
- [x] **端到端验证**：corpus B 单元（含模板单元 `$out` 输出通路经 `XLangContext` 输出缓冲线程绑定机制）→ 树翻译 → Truffle AST → CallTarget 执行 → 三层对拍断言全链可运行（stock JDK 21、EXCLUSIVE 形态）
- [x] **接线验证**：truffle 列身份断言在覆盖 B 单元上持续成立（非解释器兜底，20/20 显式复核）；内联缓存接线以**可观测缓存状态证据**验证（探针计数器覆盖函数值调用点（多态超限泛化可观测）与既有节点回填点 XStaticMethodNode（常量同一性））
- [x] 回归不削弱既有测试（纪律 3）：`TestCorpusV1TruffleColumn` 22/22、`TestCorpusCoverageATruffleColumn` 33/33 与既有单测保持全绿（三模块合计 1254/0/0）；fail-fast 反例为边界收缩迁移（FutureExecutable/排除类）+ 不回退守护，非削弱
- [x] `./mvnw test -pl :nop-xlang-truffle -am` 全绿；不泄漏断言复跑通过（`TestTruffleDependencyIsolation` 在 477 用例内全绿）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全类别 corpus truffle 列 vs 解释器列对拍全绿（含身份断言）——roadmap I7 验收第一项
- [x] 覆盖矩阵全绿（与 java 侧同基线同口径；pending 清零；目标集收敛到全量口径）——roadmap I7 验收第二项
- [x] 两级内联缓存落地且身份纪律成立（无函数实例身份/运行时值身份缓存；命中/未命中/多态泛化路径行为级验证；**既有函数调用节点回填落地**——适用面枚举无主路径遗漏）
- [x] ExitMode 控制流异常族三值一一对应 + 边界清零语义 + Try 交互有行为级验证（与解释器一致）
- [x] 闭包捕获形态按 live 机制裁定落地（含两个拷贝时序载体各自的外部写可见性验证；context-independent 保持；MATERIALIZE 用法来源与裁定一致）
- [x] 输出换缓冲翻译形态按 Phase 1 裁定落地（异常路径不丢恢复；cast quirk 保真）
- [x] 树指纹载荷覆盖扩展有逐类双变体测试（缓存键语义不弱化）
- [x] 语义敏感操作无双实现（generic/fallback 走共享 helper；特化 fast-path 不改变语义——对拍全绿即约束）
- [x] fallback 裁定记录在案（无论是否触发）；触发时"先落地方为事实源"交接记录 repo-observable（未触发：I4 completed，Execution Notes §0）
- [x] 回归不允许削弱现解释器测试（纪律 3）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] owner-docs：No owner-doc update required（docs-for-ai 同步归 I11）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 已验证（a）truffle 列真实经 CallTarget 执行（身份断言）（b）内联缓存接线以可观测缓存状态证据验证（既有节点回填点非摆设装饰——行为级对拍不构成接线证据）（c）无空方法体/静默跳过/no-op
- [x] `./mvnw compile -pl :nop-xlang-truffle -am`
- [x] `./mvnw test -pl :nop-xlang-truffle -am -T 1C`
- [x] checkstyle / 代码规范检查通过（`-Pqa checkstyle:check`）

## Deferred But Adjudicated

（无——起草时无新 deferred 项；I5 移交 I8 的 SHARED/池/缓存淘汰为既定归属非本 plan 责任（I5 Deferred 已显式记 Successor=I8），本 plan 仅在收口时声明移交。执行中产生时按 guide 补录并写明 Why Not Blocking Closure。）

## Non-Blocking Follow-ups

- Q1（Bytecode DSL 重评估）/ Q4（与 nop-js 共享 Engine 重评估）维持 watch-only，触发口径量化归 I12（设计 §九既定归属，延续 I5/I6 Follow-up）。

## Closure

Status Note: B 族 33 类（I4 边缘裁定口径）+ 非根 `CallFuncExecutable` 局部函数调用形态全部逐类翻译落地（支持集 120 = `truffleRegisteredTarget()` 收敛全量口径）；ExitMode 控制流异常族三值一一对应落地（Exception 子类载体 + 传播路径 catch 点全量显式放行盘点；边界清零点全集 = 函数/根边界统一消化；Try 交互含残余边缘记录）；闭包捕获按急切值拷贝裁定落地（两拷贝时序载体各自行为级验证；MATERIALIZE 无用法来源；设计 §四张力记档供修订）；两级内联缓存落地（函数值调用点 DSL dispatch L1 CallTarget 身份 + L2 DirectCallNode + limit=3 泛化；既有节点回填 XStaticMethodNode 常量直持；适用面逐形态枚举 + 不适用理由在案；可观测探针断言在仓）；corpus 覆盖 B truffle 列 vs 解释器列对拍全绿（20/20，三层断言 + 身份断言 + 输出缓冲比对）；覆盖矩阵闭环（126 用例全绿，pending 清零，目标集收敛与 java 侧同基线同口径）；三模块全绿 1254/0/0。三 Phase Exit Criteria 逐项 PASS，无 deferred 项，无静默降级。I8 移交显式记录（Execution Notes §11）。docs-for-ai 同步归 I11（owner-doc 裁定在案）。
Completed: 2026-08-21

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh closure-audit 子 agent（read-only），task `ses_fdf979f12ffezAa9PEeQ7d8Kcy`，verdict **CAN CLOSE（0 Blocker / 0 Major / 3 Minor）**；Minor ①`XFunctionDispatchNode.CACHE_LIMIT` 死常量（收口时已删除）②`-am -T 1C` 精确命令形态由等价覆盖替代（执行侧已另跑 exact 形态 BUILD SUCCESS：nop-xlang 514 + nop-xlang-truffle 477）③XTryNode finally 多语句停走残余边缘已显式裁定（plan §1 + javadoc，非静默 deferred）。
- Evidence（逐 Closure Gate，live 锚点，audit 子 agent 复跑）：
  - 对拍全绿 + 身份断言：`TestCorpusCoverageBTruffleColumn` 20/20（identity：assertNotSame 解释器树 + XLangRootNode cast + assertSame sourceTree）——PASS
  - 矩阵闭环：`TestTruffleCoverageMatrix` 126/126（支持集 ↔ truffleRegisteredTarget(120) 双向 + `javaTargetSet().size()==120` + `testBFamilyPendingSetCleared` 33 类 + 排除两类 fail-fast + LazyCompiled null 载荷反证 + 红灯注入）；`TestExecNodeBaselineFreshness.testPerBackendTargetSetsConsistent` 5/5——PASS
  - 两级缓存 + 身份纪律 + 回填：`XFunctionDispatchNode`（guard 逐调用重取 targetOf、limit=3、replaces→`callVarFunction`、探针）、`XStaticMethodNode`（常量直持 + `invokeStaticMethod`）；探针断言 `TestTranslatorCoverageB` 17/17（directCalls=2/genericCalls=0、directCalls=3/genericCalls≥1、methodCollection assertSame）——PASS
  - 控制流族：`XLControlFlowException` 三值 + `XTryNode` 放行 + 边界消化（`XLangFunctionRootNode`/`XLangRootNode`）；行为级 `testFunctionBoundaryClearsControlFlow`/`testTryPassesControlFlowAndRunsFinally`/`testTryCatchTriggersOnRealExceptionOnly`——PASS
  - 闭包两载体：`testBuildFuncRefCapturesEagerly`（捕获后写不可见 r=1 + 同 CallTarget 双 captured）+ `testCallFuncWithClosureCopiesAtCallTime`（调用时读新值 5）+ corpus `fn-closure-cell`（cell round-trip）；`FrameLayoutMapper` MATERIALIZE javadoc 口径更新——PASS
  - 换缓冲：`XLangContext.swapOutput/restoreOutput` + `XOutputSwapNode` 双层 finally + `XGenNodeNode` cast 通道；`testCollectTextSwapsBufferAndRestores`/`testCollectExceptionPathRestoresBuffer`/`testCollectPendingExitDispatch`/`testGenNodeEmitsThroughHandler` + tpl-collect-* 3 单元——PASS
  - 指纹载荷：`TestTreeFingerprintPayloadCoverage` 212/212（payload/subtree 双变体）+ B 族白名单分支（含函数载荷全量混合）——PASS
  - 无双实现：逐节点 spot-check 全部经 `XLangSemantics` 共享 helper（invokeGlobalFunction/invokeObjMethod/invokeStaticMethod/callVarFunction/collect*/genNode 族）——PASS
  - fallback 裁定：Execution Notes §0（I4 completed 未触发）+ roadmap live 核验——PASS
  - 回归不削弱：truffle 全量 477/0/0（含 corpus v1 22 + 覆盖 A 33 + 不泄漏断言）；三模块 1254/0/0——PASS
  - 无静默降级：Deferred 为空；Follow-ups 仅 Q1/Q4 watch-only（I12 归属）——PASS
- Anti-Hollow：(a1) corpus B 列身份断言 20/20（翻译 AST 经 CallTarget 执行，非解释器兜底）；(a2) 缓存接线以可观测探针证据验证（非行为级对拍）；(a3) `scan-hollow-implementations.mjs --module nop-xlang-truffle --severity high` exit 0（0 critical/0 high）；CF 空 catch = ExitMode cell 语义载体（有 javadoc + 测试），非静默 no-op；不可解析载荷显式 fail-fast 有断言。
- 工具门（2026-08-21 live）：`./mvnw compile -pl :nop-xlang-truffle -am` EXIT=0；`./mvnw test -pl :nop-xlang-truffle -am -T 1C` BUILD SUCCESS（514 + 477 全绿）；`./mvnw -Pqa checkstyle:check -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle` EXIT=0；`node ai-dev/tools/check-plan-checklist.mjs <本 plan> --strict` EXIT=0。
- Deferred 项分类检查：`Deferred But Adjudicated` 为空（I5 移交 I8 的 SHARED/池/缓存淘汰为既定归属，本 plan Execution Notes §11 显式移交声明）；Non-Blocking Follow-ups 仅 Q1/Q4 watch-only（设计 §九既定归属 I12），无 in-scope live defect 降级。

Follow-up:

- 设计 truffle 02 §四"闭包捕获：MaterializedFrame"表述与 live 急切值拷贝机制的张力——已记档（plan Execution Notes §4 + 当日 log），供后续设计修订（I2 先例；设计文档冻结不改）。
- XTryNode finally 多语句 Seq 的 flag 停走残余边缘（无 corpus 形态，Phase 1 §1 显式裁定记录）——若未来前端产生该形态语料，需按 flag 语义补载体。
- 其余无 plan-owned 剩余工作。
