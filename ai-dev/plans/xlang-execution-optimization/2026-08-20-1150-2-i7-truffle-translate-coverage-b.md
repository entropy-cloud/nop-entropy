# I7 truffle 翻译覆盖 B（函数闭包/控制流/输出族）+ 两级内联缓存 + 覆盖矩阵闭环

> Plan Status: active
> Last Reviewed: 2026-08-20
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

## Execution Plan

### Phase 1 - 口径对齐、fallback 裁定、控制流异常族/内联缓存/闭包形态设计定稿

Status: planned
Targets: 本 plan 与当日 log（裁定与决策记录）；fallback 触发时另含 `nop-kernel/nop-xlang/src/test/`（corpus 装载类、`ExecNodeBaseline` 扩量——正常路径为消费不编辑）

- Item Types: `Decision | Proof`

- [ ] 核验前置与供给方状态：I6 已 `completed`；I4 状态检查（fallback 触发条件 = I4 不处于 `completed`），据此定稿 fallback 裁定并记录（正常路径消费 I4 产物：corpus 覆盖 B、产生路径盘点、三边缘类裁定、per-backend 基线口径；fallback = 增量对账 + 补齐缺失裁定与产物，"先落地方为事实源"）
- [ ] **fallback 态补齐步骤（若触发，I6 先例同款）**：执行 I4 Phase 1 同口径的产生路径盘点（控制流/函数族经 c:script 语句单元、输出族经 xpl 模板单元 `compileXpl` 前端、不可产生节点显式记录）；corpus 覆盖 B 形态定稿（装载类 + 模板单元形态 + `$out` 通路，与 I4 plan Phase 1 同规格）；三边缘类裁定与 `ExecNodeBaseline` 扩量（含 per-backend 口径的 truffle 侧收敛落点）；以上编辑落 nop-xlang 测试源码并记录
- [ ] 消费/对齐 I4 口径产物：B 族最终裁定结果（三边缘类转译/排除 + **非根 `CallFuncExecutable` 局部函数调用形态裁定**）、corpus 覆盖 B 单元与类别口径、共享基线 per-backend 口径（**含无独立树形态 B 类的矩阵"真实翻译"证据形态口径——`GenNodeAttrExecutable`/`LazyCompiledExecutableFunction` 等，对齐 I4 Phase 2 裁定**）——truffle 侧逐项对齐（同基线同口径实体 = `ExecNodeBaseline`；核验 `TestExecNodeBaselineFreshness` 有效与 truffle 目标集定义在位，fallback 下由本 plan 落地）
- [ ] **ExitMode 控制流异常族设计定稿**（决策记录）：三值异常载体一一对应关系与载体选型（**须裁定与 `TryExecutable` catch 的交互**：live catch 分支捕获 `Exception` 且必然 adapt 重抛——控制流异常族若为 `Exception` 子类会被截获，选非 `Exception` 载体或 Try 翻译显式放行，二选一记录；**Exception 子类载体路线须盘点传播路径上全部 `catch (Exception)` 包装点（含 `XLangSemantics` 共享 helper 与 `CallFuncWithClosureExecutable` 等），否则默认非 `Exception` 载体**）；函数/闭包边界清零点（I4 同一 live 锚点全集，含 `CallFuncExecutable` finally）；与 `Try` 的 catch/finally 交互语义；对拍验证语料要求（见 Phase 3 消费）
- [ ] **truffle 侧输出/节点生成族换缓冲翻译形态决策**（对照 I4 Phase 1 对应决策项）：`Gen*`/`Collect*`/`GenXJson` 的运行期换缓冲在 truffle 侧的承载机制裁定（`XLangContext` 持有输出缓冲的 swap/restore 协议扩展——live 仅有 bind/clear/require 系无换缓冲 API；异常路径不丢恢复；`IXNodeHandler` cast quirk 保真；与"输出缓冲线程绑定"协议及 I8 SHARED 移交的兼容性）
- [ ] **两级内联缓存设计定稿**（决策记录，准则 = 设计 §六表）：适用节点集——**显式枚举既有函数调用节点（`FunctionExecutable`/`CallFuncExecutable`/`ObjFunction` 族/`StaticFunctionExecutable` 翻译产物）的缓存回填 + 本 plan 新增闭包/局部函数调用形态**，逐节点形态枚举或显式裁定不适用并记录理由（不允许静默遗漏既有调用主路径）；一级缓存身份载体（CallTarget 身份）与二级缓存形态（直达调用路径）的 guard 语义；上限与泛化纪律；**正确性验证口径**——缓存命中与未命中两路径执行结果一致（同函数重复调用语料）、多态调用点（同一调用位置先后调用不同函数）超限进泛化路径且结果正确、缓存不引入值身份误命中（多态语料对拍全绿即约束载体）；**接线证据载体落定**——可观测缓存状态断言（miss 计数器 / DSL 特化态迁移探针 / 缓存初始化探针，至少覆盖一处既有节点回填点；行为级对拍对缓存透明，不能作为接线证据）；宿主方法/属性访问 `@CachedLibrary` limit=3 模式适用面
- [ ] **闭包捕获形态裁定**（真裁定项，非预定结论）：物化共享帧（`MaterializedFrame` + MATERIALIZE 声明扩展）vs 与解释器同构的急切值拷贝（sourceSlots 拷入函数载体 + Reference cell 可变共享）——两形态按 live 语义保真度（**含两个拷贝时序载体的差异保真**：`BuildFuncRef` 捕获时拷贝 vs `CallFuncWithClosure` 调用时拷贝）、Truffle 优化收益（PE 消除/帧逃逸）、等价前提（前端 useRef 纪律下"写后被捕获的变量全走 cell"——须验证，未证实前不得依赖）裁定；**验证语料含"捕获后外部写 → 闭包内读"在两个拷贝时序载体上各 ≥1**（cell/非 cell 维度随载体覆盖）；裁定后确定 MATERIALIZE 声明是否有用法来源（无则记录 javadoc 口径更新依据）；设计 §四文本张力记 log 供设计修订（I2 先例）

Exit Criteria:

- [ ] fallback 裁定 + I4 口径对齐（或 fallback 补齐记录，含非根 `CallFunc` 形态裁定）+ 四个设计定稿（控制流异常族含 Try 交互 / 两级内联缓存含既有节点回填枚举与接线证据载体 / 闭包形态裁定含等价前提验证 / 输出换缓冲翻译形态），全部 repo-observable（本 plan Execution note 或当日 log）
- [ ] 内联缓存身份纪律可验证（禁函数实例身份与运行时值身份缓存——验证语料与断言方式在决策记录中落定，Phase 2/3 消费）；适用面枚举无既有调用主路径静默遗漏；接线证据载体落定（可观测缓存状态断言，至少覆盖一处既有节点回填点）
- [ ] No owner-doc update required（docs-for-ai 同步归 I11）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 覆盖 B 翻译落地、闭包形态落地与两级内联缓存

Status: planned
Targets: `nop-kernel/nop-xlang-truffle/src/main/java/io/nop/xlang/truffle/`（translate/nodes/frame）、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/XLangSemantics.java`（共享 helper 增量，按"缺哪个提取哪个"）与 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/`（解释器改调与只读访问器增量，仅提取/消费所需时——I3 先例）

> 执行策略注记：本 Phase 机制密度高（35 类翻译 + 控制流异常族 + 闭包形态 + 全模块首个 DSL 特化/缓存用法），可按"B 族翻译 → 既有节点缓存回填"内部切片执行，每切片保持模块全绿后再进下一切片。

- Item Types: `Proof`

- [ ] B 族逐具体节点类翻译：语义敏感操作 generic 路径走 `XLangSemantics` 共享 helper（与 java 侧/解释器同一实现来源）；语义特化 fast-path 仅在已证实语义等价处引入（fast-path 与 generic 共存，特化不得改变语义）；SourceLocation → SourceSection 回映射覆盖新可抛错点（`NopException` 语义，`SyntheticSources` 既有机制）
- [ ] ExitMode 控制流异常族落地（Phase 1 定稿口径）：三值一一对应；函数/闭包边界清零（清零点全集）；Try 交互（载体选型/显式放行按裁定）
- [ ] 闭包捕获形态落地（Phase 1 裁定口径）：物化共享帧（含 MATERIALIZE 声明扩展）或急切值拷贝（含 Reference cell 可变共享）；节点 context-independent 保持（不存 context 数据或运行时值）
- [ ] 两级内联缓存落地（Phase 1 定稿口径）：**既有函数调用节点回填 + 新增闭包/局部函数调用形态**；一级 CallTarget 身份 / 二级直达调用形态；无函数实例身份与运行时值身份缓存；超限泛化不无限扩容；宿主互操作 `@CachedLibrary` limit=3
- [ ] **树指纹载荷覆盖扩展**：`TreeFingerprints` 白名单扩至 B 族全部可翻译类（标量载荷 + 子树皆混入；可翻译-未白名单 fail-fast 守卫保持）；payload/subtree 双变体逐类测试扩展（I6 纪律：同 sourceKey 载荷差不串用缓存）
- [ ] 翻译级单测：每族 ≥1 翻译断言（corpus 单元或合成树；无法经前端产生的节点以合成树覆盖）；fail-fast 反证随边界收缩迁移更新（闭环后无 pending 集则以"支持集外合成节点"反证 + 边界不回退守护）；内联缓存正确性单测（命中/未命中一致 + 多态超限泛化 + 既有节点回填路径 + 可观测缓存状态断言按 Phase 1 载体）；闭包形态单测（含"捕获后外部写 → 闭包内读"在两个拷贝时序载体上各 ≥1）；控制流边界单测（函数内 return/break/continue + 边界清零 + Try catch/finally 交互）；输出换缓冲单测（按 Phase 1 形态裁定：换缓冲期间输出进收集器、恢复后回主缓冲、异常路径不丢恢复）

Exit Criteria:

- [ ] 覆盖 B 全部具体节点类可翻译——Phase 2 自身可判：翻译器支持集可编程枚举且覆盖 Phase 1 对齐的 B 族转译集（矩阵 live 扫描交叉验证归 Phase 3 联动）
- [ ] **树指纹载荷覆盖逐类测试扩展到位**（仅语义载荷不同的树对 → 异指纹；复合节点类含仅子树结构不同的树对；同 sourceKey 不串用）；无"可翻译但载荷未混合"的静默兜底
- [ ] 内联缓存身份纪律在码可审（缓存载体不含函数实例身份/运行时值身份——代码审查口径记录）+ 行为级验证（命中/未命中一致 + 多态泛化正确 + **既有节点回填路径有验证**）+ **可观测缓存状态断言在仓**（Phase 1 载体——miss 计数/特化态迁移/初始化探针，至少覆盖一处既有节点回填点）
- [ ] 闭包形态裁定落地有测试（含"捕获后外部写 → 闭包读"在两个拷贝时序载体（`BuildFuncRef` 捕获时 / `CallFuncWithClosure` 调用时）上各 ≥1，cell/非 cell 维度随载体覆盖；物化形态则另有 MATERIALIZE 声明与 round-trip 验证）
- [ ] 共享 helper 增量涉及解释器改调时，`./mvnw test -pl :nop-xlang -am` 全绿（回归基线一致或仅有新增测试）
- [ ] 每族 ≥1 翻译单测 + fail-fast 反证在仓（repo-observable）；无静默跳过（无法处理形态显式 fail-fast）
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - corpus 覆盖 B truffle 列对拍全绿与矩阵闭环

Status: planned
Targets: `nop-kernel/nop-xlang-truffle/src/test/`（列扩展与矩阵）、corpus（正常路径消费 I4 产物；fallback 为本 plan Phase 1 裁定产物，落 nop-xlang 测试源码）

- Item Types: `Proof`

- [ ] truffle 列对拍全量执行覆盖 B corpus（静态 + 动态按单元适用性；三层断言 + 身份断言 + java 列缺席/不适用记录按 I1 机制区分）；fallback 补齐的 corpus 单元满足与 I4 corpus 同规格（各族静态 ≥1、异常单元 ≥1、模板单元 ≥1、动态按自然产生能力配比、schema 四字段）
- [ ] 语料缺口补齐与逐项核验（Phase 1 语料要求）：**多态调用点语料**（同一调用位置先后调用不同函数——内联缓存验证）+ **同函数重复调用语料**（命中路径）；**控制流边界语料**（函数内 return/break/continue 各 ≥1 + 跨函数/闭包边界清零 ≥1 + **Try catch 命中控制流异常 ≥1 + finally 穿透 ≥1 + 嵌套循环 break/continue 消费层级 ≥1**）；**闭包捕获语料**（可变 slot 捕获 round-trip + "捕获后外部写 → 闭包内读"两个拷贝时序载体各 ≥1）；**输出换缓冲语料**（`Gen*`/`Collect*` 单元——换缓冲期间输出进收集器、恢复后回主缓冲）——corpus 单元已承载则直接引用，缺失则补齐（落点 Phase 1 定稿）并保持与 corpus 同规格可复用（I4 后续对账消费）
- [ ] truffle 侧覆盖矩阵闭环：支持集 ↔ truffle 目标集（收敛到 per-backend 全量口径）双向 set 相等；逐类最小实例真实翻译；B 族 pending 断言清零（或随裁定迁移）；`FutureExecutable` 红灯注入红/绿对照复验
- [ ] I8 移交项显式记录：EXCLUSIVE 形态保持声明 + SHARED 切换/Context 池/并发对拍/缓存淘汰移交 I8（责任链 repo-observable）

Exit Criteria:

- [ ] 覆盖 B corpus truffle 列 vs 解释器列对拍全绿（含身份断言 = 翻译 AST 经 CallTarget 执行）——**roadmap I7 验收第一项**（静态含模板单元 + 动态单元双列）
- [ ] **覆盖矩阵全绿（与 java 侧同基线同口径）——roadmap I7 验收第二项**：逐类注册断言全绿；pending 集清零可断言；新增节点类红灯经注入验证
- [ ] 内联缓存/控制流边界/闭包捕获语料在仓且对拍全绿（Phase 1 语料要求逐项闭合记录，含 Try catch 命中控制流异常与 finally 穿透）
- [ ] **端到端验证**：corpus B 单元（含模板单元 `$out` 输出通路经 `XLangContext` 输出缓冲线程绑定机制）→ 树翻译 → Truffle AST → CallTarget 执行 → 三层对拍断言全链可运行（stock JDK 21、EXCLUSIVE 形态）
- [ ] **接线验证**：truffle 列身份断言在覆盖 B 单元上持续成立（非解释器兜底）；内联缓存接线以**可观测缓存状态证据**验证（Phase 1 载体——miss 计数/特化态迁移/初始化探针覆盖既有节点回填点；行为级对拍对缓存透明，不构成接线证据）
- [ ] 回归不削弱既有测试（纪律 3）：`TestCorpusV1TruffleColumn` 22/22、`TestCorpusCoverageATruffleColumn` 33/33 与既有单测保持全绿；fail-fast 反例为边界收缩迁移 + 不回退守护，非削弱
- [ ] `./mvnw test -pl :nop-xlang-truffle -am` 全绿；不泄漏断言复跑通过（`TestTruffleDependencyIsolation`）
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 全类别 corpus truffle 列 vs 解释器列对拍全绿（含身份断言）——roadmap I7 验收第一项
- [ ] 覆盖矩阵全绿（与 java 侧同基线同口径；pending 清零；目标集收敛到全量口径）——roadmap I7 验收第二项
- [ ] 两级内联缓存落地且身份纪律成立（无函数实例身份/运行时值身份缓存；命中/未命中/多态泛化路径行为级验证；**既有函数调用节点回填落地**——适用面枚举无主路径遗漏）
- [ ] ExitMode 控制流异常族三值一一对应 + 边界清零语义 + Try 交互有行为级验证（与解释器一致）
- [ ] 闭包捕获形态按 live 机制裁定落地（含两个拷贝时序载体各自的外部写可见性验证；context-independent 保持；MATERIALIZE 用法来源与裁定一致）
- [ ] 输出换缓冲翻译形态按 Phase 1 裁定落地（异常路径不丢恢复；cast quirk 保真）
- [ ] 树指纹载荷覆盖扩展有逐类双变体测试（缓存键语义不弱化）
- [ ] 语义敏感操作无双实现（generic/fallback 走共享 helper；特化 fast-path 不改变语义——对拍全绿即约束）
- [ ] fallback 裁定记录在案（无论是否触发）；触发时"先落地方为事实源"交接记录 repo-observable
- [ ] 回归不允许削弱现解释器测试（纪律 3）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] owner-docs：No owner-doc update required（docs-for-ai 同步归 I11）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：closure audit 已验证（a）truffle 列真实经 CallTarget 执行（身份断言）（b）内联缓存接线以可观测缓存状态证据验证（既有节点回填点非摆设装饰——行为级对拍不构成接线证据）（c）无空方法体/静默跳过/no-op
- [ ] `./mvnw compile -pl :nop-xlang-truffle -am`
- [ ] `./mvnw test -pl :nop-xlang-truffle -am -T 1C`
- [ ] checkstyle / 代码规范检查通过（`-Pqa checkstyle:check`）

## Deferred But Adjudicated

（无——起草时无新 deferred 项；I5 移交 I8 的 SHARED/池/缓存淘汰为既定归属非本 plan 责任（I5 Deferred 已显式记 Successor=I8），本 plan 仅在收口时声明移交。执行中产生时按 guide 补录并写明 Why Not Blocking Closure。）

## Non-Blocking Follow-ups

- Q1（Bytecode DSL 重评估）/ Q4（与 nop-js 共享 Engine 重评估）维持 watch-only，触发口径量化归 I12（设计 §九既定归属，延续 I5/I6 Follow-up）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
