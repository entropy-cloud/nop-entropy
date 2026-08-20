# I9 统一后端选择机制（注册 SPI + 决策树 + 配置开关 + 降级观测）

> Plan Status: completed
> Last Reviewed: 2026-08-21
> Source: `ai-dev/backlog/xlang-execution-optimization-roadmap.md` I9（范围/验收 = 定稿条目，验收为对拍引用口径）；设计冻结于 `ai-dev/design/xlang-execution/01-architecture-baseline.md`（§二模块边界与依赖方向、§三后端选择机制=判定输入四项/判定时机三档/决策树/单跳降级/构建期扫描清单、§四后端注册 SPI 契约表、§五对拍口径=验收引用框架）；对拍口径同 §五
> Mission: xlang-execution-optimization
> Work Item: I9

<!-- Draft review: round-1（fresh session ses_fdf83e3d9ffe3Hga5hC3UzVo5w，Verdict Ready / 0 Blocker：锚点全对 live 核验（含 test-jar 发布与消费链路）、I8 硬门禁论证实质性非教条、scope 五项逐一映射无越界、单跳降级/无全局默认后端/SPI 方向等设计保真；1 Major advisory：场景矩阵测试载体模块可见性硬约束（无任何 test scope 同时见两列、矩阵必然后端拆分）未写入裁定项输入；4 Minor：出口盘点边界欠精确/native 钩子 ImageInfo 张力/后端未注册回归护栏/WARN 断言载体与 if/else 断言机制）→ 修复（扫描边界定稿 I11 同款/系统属性式形态标记预注 + 未注册护栏裁定 + I8 消费路径对偶项/WARN 载体核实 + if/else 断言机制定形/模块可见性硬约束为裁定必答题 + Phase 3 Targets 拆分落点）→ round-2（fresh session ses_fdf7dfdadffe3jCFEms1jzaZ6r，Verdict Ready / 0 Blocker；5/5 round-1 advisory 确认解决（含两列互不可见 live 实证）；剩 2 Minor（未注册护栏 Phase 2 显式用例/if/else 审查记录附于出口清单）当场折入）→ 共识达成 → active。 -->
> Related: I4（前置：java 侧覆盖闭环与 per-backend 基线口径供给方，已完成）；I8（前置：池运行时 + 单元级翻译失败观测事件供给方，执行顺序在前 `{N}`=1）；I10（后继：静态路径 java 侧绑定落地——本 plan 交付决策树与 SPI，java 绑定加载/RCM 缓存归 I10）；I1（验收引用：对拍框架 `io.nop.xlang.compare` harness，场景用例经框架执行与断言）

## Purpose

落地三后端统一选择机制的生产代码路径：后端注册 SPI（显式注册表，`ScriptCompilerRegistry`/`@GlobalInstance` 先例；条目 = 能力集/可用性状态/不可用原因；不做 classpath 扫描）、统一决策树（静态路径 = 扫描清单成员资格；动态路径含单元级翻译失败第三分支——消费 I8 观测事件；单跳降级 java→解释器、truffle→解释器）、配置开关（java/truffle 各自 enable + 强制解释器诊断模式；不设"全局默认后端"）、降级观测（WARN + 指标 + 注册表不可用条目；日志/指标命名契约落 docs-for-ai）、动态编译出口清单化核验（`ExpressionExecutor` 系"运行时字符串→Executable 树"出口统一回调注册表裁决，禁止各入口自带后端 if/else）。验收以 I1 对拍框架运行结果为输入：路由场景矩阵逐场景断言后端身份 + 执行结果一致性。

## Current Baseline

- I4 已 `completed`（依赖前半满足）：java 侧支持集 120（`ExecNodeBaseline.javaTargetSet()`）、per-backend 基线口径双 accessor、corpus 覆盖 B 19 单元（`CorpusCoverageB` + `static-b/`）。
- I2 产物（java 侧测试域执行通路先例）：`nop-xlang-java` `EvalMethodConvention`/`GeneratedEvalBinding`（gen）+ `ExecToJavaTranslator`；java 列测试域执行通路 = nop-javac 内存编译（I2 执行裁定，记 log 供设计修订）；`JavaBackendColumn`（nop-xlang-java 测试源码）为 harness 的 java 列。
- **I8 供给核验（前置硬门禁——执行顺序 `{N}`=1 在前）**：本 plan 执行前须核验 I8 已 `completed`（roadmap live）。正常路径消费：池运行时（动态路径 truffle 执行体载体）+ 单元级翻译失败观测事件（第三分支输入）。**未完成时处置**：本 plan 置 `blocked` 回引擎并记当日 log——依赖硬门禁（roadmap 依赖 I4+I8），不做绕行 fallback（绕行 = 以 EXCLUSIVE 串行 driver 顶替池运行时或以空实现顶替观测事件，均为静默弱化形态，拒绝）。
- **SPI 先例（live 锚点）**：`io.nop.xlang.script.ScriptCompilerRegistry`（`@GlobalInstance` 单例 + registerCompiler/getCompiler；`JaninoScriptCompiler` 模块初始化显式注册先例）。
- **全局 executor 钩子（live 锚点）**：`io.nop.core.lang.eval.IExpressionExecutor`（nop-core）+ `EvalExprProvider.registerGlobalExecutor/getGlobalExecutor`（全局可替换执行器入口，nop-xlang 依赖 nop-core 方向合法）；`io.nop.xlang.api.XLang` 系 API 为动态编译出口候选（Phase 1 live 盘点定全集）。
- **对拍框架（live 锚点，验收引用）**：nop-xlang 测试源码 `io.nop.xlang.compare` 包（test-jar 发布）：`ExecCompareHarness`、`IEvalBackendColumn`、`CompareBackendIds`、`BackendExecRequest/BackendExecutionResult/BackendExecutionEvidence`、身份断言规则（`IBackendIdentityRule` 系）、列缺席显式记录机制（`ColumnSkipRecord`）、三层断言工具（`CompareValues`/`SideEffectSnapshot`/`RecordingEvalOutput`）。
- **配置先例（live 锚点）**：`io.nop.xlang.XLangConfigs`（varRef 模式，`nop.xlang.*` 命名空间）——本 plan 配置开关增量的同款形态。
- **指标先例（live 锚点）**：`io.nop.commons.metrics.GlobalMeterRegistry`/`IMetrics`（nop-commons；nop-xlang 经 nop-core 传递依赖方向合法）。
- **模块边界约束（设计 execution 01 §二，硬纪律）**：SPI 契约与注册表定义在 `nop-xlang`；后端模块依赖 nop-xlang 并显式注册；nop-xlang 不得依赖后端模块、不得 import GraalVM（truffle 依赖不泄漏纪律）；注册表对后端实现的感知止步于契约。
- **静态路径现状**：构建期扫描清单生产归 I11（codegen/xgen 任务双清单产物）、生成类绑定 + RCM `ComponentCacheEntry` 缓存归 I10——本 plan 静态路径只落"清单成员资格判定 + 决策树分支 + SPI 能力接口"，测试以合成清单/转译器 API 合成产物承载（I10 验收同款"生产管线产物归 I11"边界）。
- 真正剩余的 gap：无后端注册 SPI 与显式注册表；无统一决策树与运行时求值期统一裁决入口；动态编译出口无统一回调（各入口无后端概念，尚不存在 if/else 但也无裁决接入）；无 java/truffle enable 配置开关与强制解释器模式；无降级观测（WARN/指标/不可用条目）与命名契约。

## Goals

- **后端注册 SPI + 显式注册表（roadmap 范围第一项）**：条目 = 后端标识（java/truffle）+ 能力集（静态生成物/动态翻译）+ 可用性状态与不可用原因 + 参与选择机制所需元信息；注册/反注册 API（`@GlobalInstance` 模式）；注册时机 = 后端模块初始化显式注册（触发载体 Phase 1 裁定：NopIoC bean 初始化回调 vs 静态初始化——依 NopIoC 显式注册主义，无注解扫描）；失败语义 = 初始化失败 → 不可用条目（保留原因）不阻断启动；查询不做 classpath 扫描。
- **统一决策树（范围第二项）**：判定输入四项操作化（产生时机 = 扫描清单成员资格接口 / 后端可用性 = 注册表查询 / 部署形态 = native 下 truffle 不启用的判定钩子 / 配置开关）；运行时求值期统一裁决入口（契约级接入缝：动态编译出口统一回调）；**第三分支** = 动态路径单元级翻译失败 → 降级解释器 + 观测事件（消费 I8 事件接口接线）；单跳降级 java→解释器、truffle→解释器（无跨跳）；决策树全仓唯一（设计 §三伪代码为行为规格）。
- **配置开关（范围第三项）**：java/truffle 各自 enable + 强制解释器诊断模式；不设"全局默认后端"（设计 §三裁定保持）；配置项命名/缺省值 Phase 1 定稿（候选 `nop.xlang.execution.*` 命名空间，`XLangConfigs` 同款 varRef）。
- **降级观测（范围第四项）**：每次降级 WARN 日志 + 指标计数（`GlobalMeterRegistry`）+ 注册表不可用条目可查询；日志/指标命名契约落 docs-for-ai（落点 Phase 1 裁定，候选 `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` 增节或新 owner doc；如路由变化同步 `docs-for-ai/INDEX.md`）。
- **动态编译出口清单化核验（范围第五项）**：live 盘点"运行时字符串→Executable 树"出口全集 → 逐出口接入统一回调注册表裁决；出口不得自带后端 if/else（审查项 + 断言项）。
- **路由场景矩阵（roadmap 验收，对拍引用口径）**：静态→java / 动态→truffle / java 降级→解释器 / truffle 降级→解释器 / 单元级降级判 FAIL——逐场景断言后端身份 + 执行结果一致性；**场景用例经 I1 对拍框架执行与断言**（非独立重写比对）。

## Non-Goals

- java 生成类加载集成：绑定决策树 java 侧落地、生成类清单树指纹一致性校验、生成类优先/解释器兜底绑定、RCM `ComponentCacheEntry` 缓存（I10——本 plan 静态路径仅到"清单成员资格判定 + SPI 能力接口"，java 绑定执行体在测试域以合成产物承载）。
- 构建集成：codegen/xgen 任务注册、扫描口径清点、双清单分离产物、native image 兼容、docs-for-ai 新模块开发指南大同步（I11——本 plan 的 docs-for-ai 义务仅限降级观测日志/指标命名契约）。
- 性能基准与全量三后端对拍收口（I12）。
- corpus 扩充（路由场景用例消费既有 corpus 单元）。
- 改动三后端对拍语义/列适用性机制（I1 冻结产物）。

## Scope

### In Scope

- nop-xlang：SPI 契约 + 显式注册表 + 统一决策树（含静态路径判定接口、第三分支）+ 运行时求值期统一裁决入口 + 配置开关 + 降级观测（WARN/指标/不可用条目）。
- nop-xlang-truffle：动态翻译能力注册（初始化失败→不可用条目）；单元级翻译失败事件→第三分支接线；池运行时作为动态路径执行体接入。
- nop-xlang-java：静态生成物能力注册（能力声明 + 条目；绑定加载本体归 I10）。
- 动态编译出口 live 盘点 + 统一回调接入（逐出口）。
- 路由场景矩阵测试（经 I1 harness 执行与断言）+ 降级观测命名契约 docs-for-ai 同步。

### Out Of Scope

- 同 Non-Goals。

## Execution Notes

### Phase 1 决策记录（2026-08-21）

**0. 前置硬门禁核验**：I8 roadmap live 状态 = `done`（`ai-dev/backlog/xlang-execution-optimization-roadmap.md` I8 条目，2026-08-21 closure audit CAN CLOSE）。I8 供给两通道核验：观测事件消费接口 = `TranslationFailureListener`（载荷 `TranslationFailureEvent`，注册通道 `XLangContextPool.getLanguage().addTranslationFailureListener(...)`，I8 Execution Notes §14）；池运行时 = `XLangContextPool.open()/lease()/Lease.eval/close` + 共享 Engine `XLangTruffleEngine.sharedEngine()`（创建失败显式 IllegalStateException = 不可用条目判定信号）。

**1. 动态编译出口清单（live 盘点，边界定稿）**：

- **扫描边界裁定**：出口**定义域** = nop-kernel 全域（nop-xlang 编译 API + nop-core `EvalExprProvider` expr-parser 钩子——全部"运行时字符串→Executable 树"公共 API 定义在 nop-kernel，业务模块无自定义出口定义）；出口**消费域** = 全仓 main sources（live grep `newCompileTool()`/`getDefaultExprParser()`/`newDefault()`：nop-report（XptRuntime/XptModelLoader/ExcelFormulaParser 等 7 处）、nop-rule（RuleManager/RuleModelCompiler/RuleDslModelParser 等 4 处）、nop-task（TaskStepBuilder）、nop-excel（ImportModelHelper/ImportExcelParser）、nop-ooxml-docx（OfficeDocModelParser/WordTemplateParser）、nop-batch-gen（BatchTemplateBasedProducer）、nop-dyn（NopDynFunctionMeta）、nop-match（MatchPatternCompileConfig）、nop-core（XJsonLoader/ValueResolverCompileOptions/DeltaJsonOptions）、nop-xlang-debugger。消费域全部经 compile 出口产出 `ExprEvalAction`/`IEvalAction`，invoke 统一流经 choke point（见 §3），**传递覆盖**）。"无遗漏出口"断言以该边界为可验证全集：出口定义域逐出口列清单，消费域经 choke point 传递覆盖（无需逐模块改动）。

| # | 出口（live 锚点） | 产物 | 统一回调接入点 |
|---|---|---|---|
| E1 | `XLangCompileTool.compileSimpleExpr(loc,source)`（含 `SimpleExprHelper.getCompiledExpr` 与 `XLangCoreInitializer.parseExpr`→`EvalExprProvider.registerDefaultExprParser` 全局钩子） | `ExprEvalAction` | choke point：`XLang.execute` 统一裁决（见 §3）✅ |
| E2 | `XLangCompileTool.compileFullExpr` | `ExprEvalAction` | 同 E1 ✅ |
| E3 | `XLangCompileTool.compileTemplateExpr`（全重载） | `ExprEvalAction` | 同 E1 ✅ |
| E4 | `XLangCompileTool.compileTag/compileTagBody/compileTagBodyWithSource`（运行时 XNode） | `ExprEvalAction` | 同 E1 ✅（`XplModel extends ExprEvalAction`，模型加载期 java 绑定归 I10，执行期经 choke point 裁决） |
| E5 | `XLangCompileTool.compileXpl(loc,source)`（字符串→XNode→compileTag） | `ExprEvalAction` | 同 E1 ✅ |
| E6 | `XLang.genJsonExtends(loc,source,json)`（XNodeParser+compileTagBody+invoke） | 值 | 经 E4 action invoke → choke point ✅ |
| E7 | `XLangCompileTool.compileScript/compileScriptAction` | `IEvalFunction`（janino/外部脚本引擎，`ScriptCompilerRegistry` 域） | **不适用**：产物为脚本引擎函数（非 Executable 树），选择机制判定对象不存在；脚本引擎注册是 SPI 形态先例而非接入对象 |
| E8 | `XLangCompileTool.getStaticValue` | 字面量常量 | **不适用**：常量折叠无树执行 |
| E9 | `XLangCompileTool.compileFunction/compileEvalFunction` | `IEvalFunction`（函数载荷） | **不适用**：函数载荷粒度非编译单元根，载荷随所在编译单元整体路由（truffle 翻译含函数载荷，I7） |
| E10 | `XLangCompileTool.buildExecutable` | 裸树 | 接入点同 choke point（main 消费方均包装为 action 后 invoke；裸树直接执行见 E12 裁定） |
| E11 | `FilterExprHelper.parseFilterExpr` | `TreeBean`（match 引擎 AST） | **不适用**：产物非 Executable 树；nop-match 编译到表达式时经 E1 |
| E12 | `BetweenOpExecutable.passConditions` / `ExecutableFunctionEvalAction.doInvoke`（`XLang.getExecutor()` 直用） | 部分树/函数执行 | **不适用**：子树谓词与函数调用粒度（非编译出口），所在编译单元在其根上路由；trampoline 语义（子节点经 `executor.execute` 递归，不重入 choke point）保证根唯一 |

- **"出口不得自带后端 if/else"断言机制定形**：结构性保证 + 逐出口审查记录（本表）双载体。结构性保证 = 依赖方向断言（nop-xlang main 无 `io.nop.xlang.truffle`/`io.nop.xlang.java.`/`org.graalvm` import、pom 无后端模块/graalvm 依赖——后端类型在 nop-xlang main 不可引用，if/else 结构性不可能）；逐出口审查记录 = 本清单（每出口接入点/不适用裁定 + 理由，独立可审）。

**2. SPI 契约定稿**：

- 落点：nop-xlang 新包 `io.nop.xlang.backend`。
- 条目（契约即条目，实例承载字段）：`IEvalExecutionBackend` = `getBackendId()`（"java"/"truffle"）/ `getCapabilities()`（`EvalBackendCapability.STATIC_GENERATED | DYNAMIC_TRANSLATION` 枚举集）/ `isAvailable()` / `getUnavailableReason()`（可用时 null）。扩展接口：`IEvalDynamicBackend#executeDynamic(EvalBackendDynamicRequest)→EvalBackendDynamicOutcome`（值+身份证据 artifact+fallback 标记+fallback 原因）；`IEvalStaticBackend#isStaticCandidate(resourcePath)` + `findStaticBinding(resourcePath, tree)→IEvalStaticBinding`（`execute(EvalRuntime)` + `getBindingArtifact()` 身份证据）。绑定加载本体归 I10（java 侧 binder 钩子先行，测试域合成）。
- 注册表：`EvalBackendRegistry`（`@GlobalInstance` 模式，`ScriptCompilerRegistry` 同款）：`register(backend)`（同 id 不同实例 → fail-fast；同实例幂等）、`unregister(backend)`（remove(id,instance) 条件式——非 owner 时 no-op，`ScriptCompilerRegistry.unregisterCompiler` 先例同款；Phase 2 定稿语义，closure audit 裁定以本修正为准）、`getBackend(id)`/`getBackendIds()`/`isEmpty()`/`getUnavailableBackends()`（诊断查询 Map id→reason）/按能力查询 `findStaticBackend()`/`findDynamicBackend()`。无 classpath 扫描。未知 id/非法契约（null id、空能力集）fail-fast。
- **注册时机载体裁定**：`ICoreInitializer` + `META-INF/services`（ServiceLoader）。锚点 = `XLangCoreInitializer`（`initialize()` 内 `JaninoScriptCompiler.register()` 先例——平台"模块初始化显式注册"的 live 标准形态，CoreInitialization 在内核测试与生产启动统一运行）。NopIoC bean 初始化回调被拒：后端模块无 beans.xml 存量、IoC 容器启动对内核级模块非保证路径；ServiceLoader services 文件本身是显式注册（无注解扫描），与 NopIoC 显式主义一致（truffle-dsl-processor 的 provider 服务文件同构，设计 §四）。
- 失败语义：初始化探测（truffle=Engine 创建探测，经可注入 Supplier 缝）失败 → 注册不可用条目（保留原因），不抛阻断启动异常；对不可用后端的请求按降级路径走解释器 + 观测事件。
- 依赖方向验证：SPI/注册表/决策树全部在 nop-xlang；后端模块实现契约并经自身 initializer 显式注册；注册表对后端感知止步于契约接口（结构性断言测试在案，见 Phase 2）。

**3. 决策树定稿**：

- **统一裁决入口 = `XLang.execute(IExecutableExpression, EvalRuntime)` choke point**（nop-xlang `api/XLang.java:46`）。依据（live 核验）：trampoline 模式下子节点经 `executor.execute(child,rt)` 递归、不重入 `XLang.execute`，根唯一；所有动态出口产物 `ExprEvalAction.invoke/doInvoke` → `XLang.execute`（E1-E6/E10）；嵌套 action invoke 正确重入再裁决。裁决入口非 `IExpressionExecutor` 接口本身（该接口见每个节点——调试器语义，非根裁决语义）；解释器路径仍经 `EvalExprProvider.getGlobalExecutor()`（与 debugger 全局执行器可组合）。
- 判定输入四项操作化：(1) 产生时机 = 注册表 `findStaticBackend()`（java）的 `isStaticCandidate(resourcePath)`（扫描清单供给 = SPI 可查询接口，生产产物归 I11，缺省空清单=全动态）；(2) 后端可用性 = 注册表查询；(3) 部署形态 = 配置式标记 `nop.xlang.execution.deployment-form`（auto|jvm|native-image，缺省 auto = 探测系统属性 `org.graalvm.nativeimage.kind` 存在性——字符串探测，无 GraalVM import/无 ImageInfo 类引用，依赖方向纪律保持）；native → truffle 结构性不适用（不记降级事件——与"后端不在 classpath"同族的结构性排除裁定，镜像排除机制归 I11）；(4) 配置开关 = `XLangConfigs` varRef 增量（§4）。
- 决策树（设计 §三伪代码的行为规格落地）：`force-interpreter` → INTERPRETER 静默（诊断隔离，不记降级事件——边界裁定）；静态命中（清单成员）→ java 启用+可用+绑定命中 → JAVA；java 开关关/不可用条目/绑定缺失（清单内应有）→ INTERPRETER + 降级观测；静态分支**永不咨询 truffle**（单跳无跨跳结构性成立）；动态路径 → truffle 启用+可用+非 native → TRUFFLE；truffle 开关关/不可用 → INTERPRETER + 降级观测；truffle 未注册/native → INTERPRETER 静默。指纹失配降级归 I10（本 plan 无 java 指纹校验分支）。
- 第三分支接线契约：truffle SPI 适配器（truffle 模块内）在池打开时经 `pool.getLanguage().addTranslationFailureListener(listener)` 注册消费者（I8 接口真实消费）；listener 按 sourceKey 记录失败事件（有界映射）；`executeDynamic` 捕获求值异常后按 sourceKey 关联事件——命中 = 单元级翻译失败 → 返回 fallback outcome（携带事件细节）→ 裁决入口改走解释器 + 观测（WARN+指标，reason=unit-translation-failure）；未命中 = 真实求值错误 → 重抛（fail-fast 保持，无静默吞没）。I8 fail-fast 重抛语义对非路由路径（harness 列直驱）不变。
- 静态路径 java 执行体测试域载体：合成清单（java backend 可设置 scan list）+ `ExecToJavaTranslator.translate` + nop-javac 内存编译 → 合成 `IEvalStaticBinding`（`JavaBackendColumn`/I2 先例）；生产绑定加载归 I10（binder 钩子接口先行）。
- **后端未注册回归护栏**：注册表空 → `XLang.execute` fast-path 直通 `EvalExprProvider.getGlobalExecutor()`（行为与现状逐字节一致，无观测、无决策记录）；证据 = 既有 514/263/577 套件全绿 + 显式护栏用例（registry 空 → `isActive()==false`、决策静默 INTERPRETER、零指标增量）。缺省值不引入隐性行为漂移（裁定记录：缺省 enable=true 只在"后端已注册"时激活路由，未注册=classpath 缺席=安静缺省）。
- 决策诊断环：裁决入口记有界 recent-decisions 环（backendId/reason/身份证据 artifact/sourceKey），供生产诊断与测试身份断言（java=生成类绑定 artifact / truffle=翻译 AST artifact / 解释器=原树）。

**4. 配置开关定稿**：

- `nop.xlang.execution.java-backend-enabled`（缺省 **true**）/ `nop.xlang.execution.truffle-backend-enabled`（缺省 **true**）/ `nop.xlang.execution.force-interpreter`（缺省 **false**）/ `nop.xlang.execution.deployment-form`（缺省 **auto**）——`XLangConfigs` 增量 varRef（`CFG_XLANG_EXECUTION_*` 常量，同款形态）。
- 缺省 true 理由：注册即路由是设计 auto 语义；false 缺省会令"已注册未启用"成为缺省态并触发逐执行降级 WARN（更劣）。现有部署的安静缺省 = 后端模块不在 classpath（未注册）。
- 强制解释器诊断模式：全路由短路 INTERPRETER + **不记降级事件**（诊断隔离边界裁定：全量降级是操作者显式意图，事件为噪声）。
- 不设"全局默认后端"（设计 §三拒绝项保持）。
- 测试切换载体：`AppConfig.getConfigProvider().updateConfigValue(ref, value)` + finally 恢复（`AppConfig.hostId` 先例）。

**5. 降级观测命名契约定稿**：

- WARN 日志：logger `io.nop.xlang.backend.EvalBackendObservation`；消息键 `nop.xlang.execution.backend-degraded`；格式 `backend={}, reason={}, sourceKey={}`（有 SourceLocation 时附加）。断言载体 = logback `ListAppender` attach/capture/detach（logback-classic 经 nop-commons compile scope 在测试类路径；仓内先例 `TestNopMetaSearchProcessor`、nop-ai-agent 测试族）。
- 指标：counter `nop.xlang.execution.backend-degradation`，tags `backend`（java|truffle）、`reason`（config-disabled | unavailable | unit-translation-failure | generated-binding-missing）——`GlobalMeterRegistry.instance().counter(...)`，命名随 `nop.job.worker.*`/`nop.dao.*` 先例（nop. 前缀、点分、kebab）。
- 注册表不可用条目查询：`EvalBackendRegistry.getUnavailableBackends()`。
- docs-for-ai 落点裁定：`docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` **增节**（"执行后端选择与降级观测"）——主题归属既有 xlang 基础指南，不新建 owner doc；INDEX 无路由变化（Phase 3 执行）。

**6. 路由场景矩阵用例设计（模块可见性硬约束裁定）**：

- 硬约束 live 事实：`JavaBackendColumn` + nop-javac 内存编译在 nop-xlang-java **test** scope；`TruffleBackendColumn` 在 nop-xlang-truffle **test** scope；两者依赖 nop-xlang test-jar；nop-xlang-java 与 nop-xlang-truffle 互不依赖（设计 §二禁止，test 依赖同禁）→ 仓内无任何模块 test scope 同时可见两列。**裁定：矩阵按后端模块拆分落点**——(a) nop-xlang test（test-jar 同模块）：裁决入口逐分支/单跳无跨跳/强制解释器短路/配置开关矩阵（三开关 × 两后端可用性，fake backend 注入）/后端未注册回归护栏/注册表契约（注册/反注册/幂等/fail-fast）/依赖方向结构性断言；(b) nop-xlang-java test：静态→java（合成清单+合成生成类绑定；真实出口 compile→invoke→裁决→JAVA；身份=生成类绑定 artifact；三层对拍 vs 解释器）、java 降级两形态（开关关/不可用条目）→ 解释器身份 + WARN/指标断言；(c) nop-xlang-truffle test：动态→truffle（真实出口→裁决→池运行时；身份=翻译 AST CallTarget/RootNode）、truffle 降级→解释器（Engine 创建失败注入——适配器 Supplier 缝）、单元级降级双载体（生产第三分支：路由 fallback + 事件消费 + 观测；对拍不变式：harness truffle 列翻译失败 → 列 FAIL 非 SKIP）。
- **harness 消费载体裁定**："决策树裁决 + harness 断言工具组合"（二选一中的后者）——路由发生在 `XLang.execute`，harness 列结构性绕过它；场景用例经**真实出口**（compile→invoke→裁决入口）执行，用 I1 harness 断言工具（`CompareValues`/`SideEffectSnapshot`/`RecordingEvalOutput` 三层断言）+ 裁决环身份断言 + 观测断言（counter/ListAppender）收口；"单元级降级判 FAIL"场景另经 `ExecCompareHarness.runUnit` + truffle 列直驱断言 FAIL-not-SKIP 语义。验收语义"经框架执行与断言"保持：断言工具全幅消费 + FAIL 语义经框架执行。
- 身份断言载体定稿：java=生成类绑定 artifact（entry Method/生成类实例）/ truffle=翻译 AST（XLangRootNode/RootCallTarget）/ 解释器=裁决环 artifact 为原树实例。

**7. Phase 2 落地记录（2026-08-21）**：

- **nop-xlang 新包 `io.nop.xlang.backend`**（8 主类 + 接入点）：`EvalBackendCapability`/`IEvalExecutionBackend`（基础契约：标识/能力集/可用性/不可用原因）/`IEvalDynamicBackend`（动态翻译扩展 + 第三分支 fallback 契约）/`IEvalStaticBackend`（清单成员资格 + 绑定查找）/`IEvalStaticBinding`/`EvalBackendDynamicRequest`/`EvalBackendDynamicOutcome`（value+artifact / fallback+reason+detail）/`EvalBackendRegistry`（`@GlobalInstance`；register 幂等 + 同 id 异实例 fail-fast + 能力槽位唯一性 fail-fast + `getUnavailableBackends()` 诊断 + 按能力槽位查询；无 classpath 扫描）/`EvalBackendObservation`（WARN 消息键 `nop.xlang.execution.backend-degraded` + counter `nop.xlang.execution.backend-degradation` tags backend/reason + `degradationCount` 查询）/`EvalBackendDecision`（裁决结果 + RouteKind + degraded + artifact 身份证据）/`EvalBackendRouter`（统一决策树：`isActive()`/`executeAdjudicated`/`decide`/近期裁决诊断环 128 + 部署形态配置式钩子（auto=系统属性 `org.graalvm.nativeimage.kind` 字符串探测，无 GraalVM import））。
- **出口统一回调接入**：`io.nop.xlang.api.XLang#execute`（`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLang.java`）——`router.isActive()` fast-path（注册表空直通全局执行器，行为与现状一致）；E1-E6/E10 出口经此单点接入（Phase 1 清单），解释器路径保持 `EvalExprProvider.getGlobalExecutor()`（debugger 全局执行器可组合）。
- **配置开关**：`XLangConfigs` 增量 4 varRef（`CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED`=true / `CFG_XLANG_EXECUTION_TRUFFLE_BACKEND_ENABLED`=true / `CFG_XLANG_EXECUTION_FORCE_INTERPRETER`=false / `CFG_XLANG_EXECUTION_DEPLOYMENT_FORM`="auto"）+ `XLangErrors` 增量 3 错误码（already-registered / capability-conflict / invalid-contract，`ARG_BACKEND_ID`/`ARG_CAPABILITY` 参数）。
- **nop-xlang-java**：`io.nop.xlang.java.backend.JavaEvalExecutionBackend`（单例，STATIC_GENERATED；`setStaticScanList`/`setBinder`（`IEvalStaticBindingBinder` 契约，生产 I10 接入缝）/`markUnavailable`（生产接入缝 + 测试注入）；缺省惰性空态=行为与现状一致）+ `initialize.XLangJavaBackendInitializer` + `META-INF/services/io.nop.core.initialize.ICoreInitializer`（order = REGISTER_XLANG+10）。
- **nop-xlang-truffle**：`io.nop.xlang.truffle.backend.TruffleEvalExecutionBackend`（单例 + Supplier\<Engine\> 注入缝（缺省 `XLangTruffleEngine::sharedEngine`）；`probeInitialization`（注册时探测，失败 → sticky 不可用条目不阻断启动）；池惰性打开（打开失败 → sticky 不可用）；池打开时经 `pool.getLanguage().addTranslationFailureListener` 注册 I8 事件消费者（有界 LRU 256 关联表，sourceKey 关联 → 第三分支 fallback `unit-translation-failure`；未命中异常原样重抛——真实求值错误不吞没）；sourceKey = resourcePath 或树实例身份键 `xl-route-dyn/<identityHashCode>`（同一 action 复用同一树实例 → 稳定命中翻译缓存）；`close()` 反注册消费者防 SHARED 语言实例泄漏）+ `initialize.XLangTruffleBackendInitializer`（order = REGISTER_XLANG+20）+ services 文件。I8 交付物增量：`XLangLanguage.removeTranslationFailureListener`（反注册通道，close 对称性）。
- **测试（Phase 2 载体）**：nop-xlang `io.nop.xlang.backend` 包 4 测试类 29 用例——`TestEvalBackendRegistry` 7（注册/反注册/查询/幂等/同 id 异实例 fail-fast/能力槽位冲突 fail-fast/非法契约 fail-fast/不可用条目查询/非 owner 反注册 noop）、`TestEvalBackendRouterDecisions` 17（空注册表护栏（isActive=false + 真实出口行为不变 + 零观测）/force-interpreter 静默短路/静态命中→绑定执行/静态开关关/不可用/绑定缺失三降级形态（各 counter 断言）/清单外→动态/单跳无跨跳（java 不可用 + truffle 可用 → INTERPRETER + 动态后端零执行）/动态可用/开关关/不可用/未注册静默/native 部署形态静默/第三分支 fallback→解释器+观测+artifact=原树/动态后端真实错误重抛/真实出口→choke point 接线（DYNAMIC + artifact 断言）/配置开关矩阵 2×2×2×2 全组合）、`TestEvalBackendObservation` 3（WARN ListAppender 消息键+三段格式+级别/指标 tags 分 reason 计数/不可用条目注册表查询）、`TestEvalBackendDependencyDirection` 2（main 源禁引 io.nop.xlang.truffle/io.nop.xlang.java./org.graalvm + pom 禁依赖——"出口无自带 if/else"结构性保证）；nop-xlang-java `TestJavaBackendRegistration` 4（初始化注册/能力/缺省惰性空态/清单成员+绑定查找/markUnavailable 不可用条目）；nop-xlang-truffle `TestTruffleBackendRegistration` 3（初始化注册 + Engine 探测可用/Engine 失败注入 → 不可用条目 + 决策降级（含 reason）/池运行时求值冒烟 + artifact=XLangRootNode）。
- **验证**：`./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` 全绿——**543/0/2 + 267/0/0 + 580/0/0**（基线 514/263/577 全保持，新增 29+4+3=36）；`./mvnw checkstyle:check -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -Pqa` 退出码 0。
- **静默跳过审查**：不可用后端请求 → 降级路径 + 显式观测（三形态测试）；未知后端标识/非法契约（null id/空能力集/重复注册/槽位冲突）→ fail-fast（NopException + ErrorCode）；动态后端真实求值错误原样重抛（`testDynamicBackendErrorPropagates`）；fallback 只在翻译失败事件关联命中时发生（关联表 take 语义，无凭空 fallback）。

**8. Phase 3 落地记录（2026-08-21）**：

- **路由场景矩阵（roadmap I9 验收项；经 I1 框架执行与断言——"决策树裁决 + harness 断言工具组合"载体，Phase 1 §6 裁定）**：
  - **静态→java**（`nop-xlang-java` test `TestJavaBackendRoutingScenarios.testStaticRoutesToGeneratedClass` / `testStaticTemplateRoutesToGeneratedClassWithOutput`）：真实出口（`compileSimpleExpr`/`compileTag(text)`）→ invoke/generateToWriter → 统一裁决 → JAVA；身份断言 = 裁决环 artifact 为生成类入口 `Method`（确定性派生类名 `EvalMethodConvention.GENERATED_PACKAGE + generatedClassName(path)`；$out 双参形态）；结果一致性 = `CompareValues.typedEquals` 返回值 + scope 变量 + 输出文本（关路由同出口解释器基线对照）。生成类执行体 = 测试域合成（转译器 + nop-javac 内存编译，I2 先例；生产绑定归 I10）。
  - **java 降级→解释器两形态**（同测试类 `testJavaDegradedByConfigSwitch` / `testJavaDegradedByUnavailableEntry`）：开关关 / `markUnavailable` 不可用条目 → INTERPRETER；身份断言 = 裁决环 artifact 与原树实例 `assertSame`（解释器执行）；观测断言 = WARN（ListAppender：消息键 + backend/reason 段）+ counter delta=1（reason=config-disabled / unavailable）。
  - **动态→truffle**（`nop-xlang-truffle` test `TestTruffleBackendRoutingScenarios.testDynamicRoutesToTrufflePool`）：真实出口（`compileSimpleExpr`）→ invoke → 统一裁决 → TRUFFLE 经池运行时（I8 `XLangContextPool` 租借协议）；身份断言 = 裁决环 artifact 为 `XLangRootNode` 且 `getSourceTree()` 与请求树实例同一（翻译 AST 身份）；结果一致性 = typedEquals + scope（强制解释器诊断模式作解释器基线——同出口对照）+ 静默断言（force 模式零 WARN）。
  - **truffle 降级→解释器（初始化失败注入）**（`testTruffleDegradedByInitFailure`）：Supplier 缝注入 Engine 创建失败 → 不可用条目 → INTERPRETER + 原树身份 + WARN/counter（reason=unavailable，原因含 injected 标记）。
  - **单元级降级判 FAIL（双载体）**：生产第三分支（`testUnitLevelTranslationFailureFallsBackToInterpreter`）——支持集外合成节点树经真实出口 invoke → I8 翻译失败事件被消费（fallback 仅在事件关联命中时发生的结构性因果）→ 降级解释器 + 结果与解释器一致（"ok"）+ WARN/counter（reason=unit-translation-failure）+ 原树身份；红/绿对照（绿 = 可翻译树 → DYNAMIC 裁决 + 零单元级降级观测）。对拍不变式（`testUnitLevelDegradationJudgesFailNotSkipInHarness`）——经 `ExecCompareHarness.runUnit` + `TruffleBackendColumn` 直驱：支持集外树 → truffle 列 FAIL（column-crashed，非 SKIP；缺席记录不含 truffle）、解释器列 PASS、report 判 FAIL；绿灯对照 = 可翻译单元两列全 PASS（FAIL 语义非恒真）。
- **docs-for-ai 同步**：`docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` 新增"执行后端选择与降级观测"章节（SPI/注册表锚点、决策树、配置开关表、降级观测命名契约 + 静默边界）——Phase 1 §5 增节裁定落点；无新增文档、INDEX 无路由变化；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- **三模块回归全绿**：`./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` = **543/0/2 + 271/0/0 + 584/0/0**（合计 1398；基线 514/263/577 全保持，corpus 三列/对拍/矩阵既有套件零削弱——不泄漏断言 `TestTruffleDependencyIsolation` 在 584 内全绿复跑）。

**9. I10 移交显式记录（责任链 repo-observable）**：

- **静态路径 java 绑定消费的接口契约**：(a) 扫描清单成员资格接口 = `io.nop.xlang.backend.IEvalStaticBackend#isStaticCandidate(resourcePath)`（java 侧查询实现已落 `JavaEvalExecutionBackend.setStaticScanList` 可设置清单；生产清单产物归 I11 构建任务）；(b) 生成类绑定供给 = `io.nop.xlang.java.backend.IEvalStaticBindingBinder#findStaticBinding(resourcePath, tree)`（`JavaEvalExecutionBackend.setBinder` 接入缝；绑定执行体契约 `IEvalStaticBinding#execute(EvalRuntime)` + `getBindingArtifact()` 身份证据）；(c) 指纹失配/校验失败语义 = 返回 null 即"应有而缺失"降级观测（reason=generated-binding-missing，Phase 2 测试在案）；(d) 可用性生产接入缝 = `JavaEvalExecutionBackend.markUnavailable(reason)`（构建管线漏跑/产物完整性失败 → 不可用条目，I11 验收"java 后端启用但扫描清单缺失→注册不可用条目 + 全局 WARN"的消费点）。
- **测试域合成形态与 I10 生产落地的对账点**：本 plan 场景矩阵 java 侧以"转译器产物 + nop-javac 内存编译"合成绑定（`TestJavaBackendRoutingScenarios.syntheticBinder`，I2 测试域通路先例）；I10 生产落地 = 真实 `_gen/` 生成类 classpath 加载 + 树指纹一致性校验 + `ComponentCacheEntry` 缓存——同一 `IEvalStaticBindingBinder` 契约、同一降级语义，仅供给方替换。裁决树/观测/配置开关零改动（I10 不需要触碰 `io.nop.xlang.backend` 包）。
- **决策树静态分支指纹失配降级**（roadmap I10 范围项"stale 缺陷 vs 租户差异化树预期稳态降级分级"）：本 plan 决策树预留 reason=generated-binding-missing 单一语义；分级观测归 I10 扩展（`EvalBackendObservation` reason 枚举增量）。

## Execution Plan

### Phase 1 - I8 供给核验、动态编译出口盘点、SPI/决策树/开关/观测契约设计定稿

Status: completed
Targets: 本 plan Execution Notes 与当日 log（出口清单与决策记录 repo-observable）

- Item Types: `Decision | Proof`

- [x] **前置硬门禁核验**：I8 已 `completed`（roadmap live 核验）；未完成 → 本 plan 置 `blocked` 回引擎并记 log（Current Baseline 既定处置，不绕行）（Execution Notes §0）
- [x] **动态编译出口 live 盘点**：扫描 nop-xlang 及调用方"运行时字符串→Executable 树"出口全集（候选锚点：`io.nop.xlang.api.XLang` 系 API、`SimpleExprParser`/`XLangExprParser` 直用点、`XplCompiler` 动态编译入口、规则/表达式配置产物入口），产出出口清单——每出口标注统一回调接入点或"不适用"裁定与理由（清单落 plan Execution Notes，repo-observable）；盘点口径 = live 扫描而非设计文档转述；**扫描边界定稿**（nop-kernel 全域 vs 全仓含业务模块——纳入/排除逐项记录理由，I11 扫描口径清点同款做法；"无遗漏出口"断言以该边界为可验证全集）（Execution Notes §1：出口定义域=nop-kernel、消费域=全仓经 choke point 传递覆盖；12 出口逐项表）
- [x] **SPI 契约定稿**：条目字段（标识/能力集枚举/可用性状态/不可用原因/元信息）、注册/反注册/查询 API、失败语义（初始化失败→不可用条目不阻断启动）；**注册时机载体裁定**（后端模块初始化显式注册的触发机制：NopIoC bean 初始化回调 vs 静态初始化——依 NopIoC 显式主义与 beans.xml 注册约定裁定，落锚点）；依赖方向保持验证（nop-xlang 不依赖后端模块、契约对实现的感知止步于契约）（Execution Notes §2：ICoreInitializer+services 裁定，Janino 先例锚点）
- [x] **决策树定稿**：判定输入四项操作化——扫描清单成员资格接口形态（清单供给 = SPI 可查询接口；生产产物归 I11，测试合成清单）、后端可用性查询、部署形态判定钩子（native 下 truffle 不启用；**实现张力预注**：nop-xlang 无 GraalVM import——钩子走系统属性/配置式部署形态标记而非 `ImageInfo` 类探测，违反即被依赖方向断言拦截）、配置开关读取；运行时求值期统一裁决入口签名；第三分支接线契约（消费 I8 观测事件接口的回调形态与降级动作——消费方 = truffle 侧 SPI 适配器，I8 Phase 1 消费路径裁定对偶项）；单跳降级全分支枚举（java 侧两形态：开关关/不可用条目；指纹失配归 I10 不在本 plan）；静态路径 java 执行体在本 plan 的测试域载体裁定（合成清单 + 转译器 API 合成生成类，`JavaBackendColumn`/I2 nop-javac 先例）；**后端未注册回归护栏**：注册表空/后端不在 classpath 时所有出口行为与现状一致（既有测试全绿即为证据——缺省值选择不得引入隐性行为漂移，显式裁定记录）（Execution Notes §3：choke point 裁定 + 四输入操作化 + 全分支枚举 + 护栏设计）
- [x] **配置开关定稿**：配置项清单 + 命名 + 缺省值（候选 `nop.xlang.execution.java-backend-enabled` / `nop.xlang.execution.truffle-backend-enabled` / `nop.xlang.execution.force-interpreter`；`XLangConfigs` 增量 varRef）；强制解释器诊断模式语义（全路由短路 INTERPRETER + 不记降级事件的边界裁定）；不设全局默认后端（Execution Notes §4：四配置项（含 deployment-form 钩子）+ 缺省 true 理由 + force 静默边界裁定）
- [x] **降级观测命名契约定稿**：WARN 日志消息键与格式（日志断言载体一并核实——live 日志测试先例或可注入 Logger，与指标断言同标准）、指标命名（`GlobalMeterRegistry` live 命名先例核实后定）、注册表不可用条目查询 API；"出口不得自带后端 if/else"的断言机制定形（结构检查脚本/逐出口审查记录形态，二选一裁定）；**docs-for-ai 落点裁定**（候选 `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` 增节或新 owner doc；如新增/路由变化同步 `docs-for-ai/INDEX.md` 与 source-anchors）（Execution Notes §1 末段 + §5：ListAppender 载体核实 + counter 命名 + 增节裁定）
- [x] **路由场景矩阵用例设计**：五类场景（静态→java / 动态→truffle / java 降级→解释器 / truffle 降级→解释器 / 单元级降级判 FAIL）× I1 harness 执行载体定稿（场景用例经 `ExecCompareHarness` 参数化驱动或"决策树裁决 + harness 断言工具组合"载体二选一裁定——验收语义保持"经框架执行与断言"）；**模块可见性硬约束（裁定必答题）**：`JavaBackendColumn` 在 nop-xlang-java test、`TruffleBackendColumn` 在 nop-xlang-truffle test，仓内无任何模块 test scope 能同时看到两列，且 nop-xlang-java→nop-xlang-truffle 连 test 依赖也被设计 §二禁止——场景矩阵必然按后端模块拆分落点（每模块各测己方列 + 共享裁决入口经 nop-xlang test 或 test-jar 驱动），落点方案随本裁定定稿；每场景的身份断言载体（java=生成类实例 / truffle=翻译 AST CallTarget / 解释器=解释器树）与降级观测断言（日志/指标）落定（Execution Notes §6：拆分三落点 + 后者载体裁定 + 身份载体定稿）
- [x] 决策记录全部落 plan Execution Notes / 当日 log（Execution Notes §0-§6 + `ai-dev/logs/2026/08-21.md` 当日条目）

Exit Criteria:

- [x] 前置核验记录 + 出口清单（含逐出口接入点/不适用裁定）+ 五项契约定稿全部 repo-observable
- [x] 路由场景矩阵用例设计与 harness 消费载体在决策记录中可审（Phase 2/3 消费；显式引用关系可验证——非独立重写比对）
- [x] No owner-doc update required（本 Phase 无文档变更；命名契约文档落点为 Phase 3 执行项）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - SPI/注册表/决策树/开关/降级观测落地与出口接入

Status: completed
Targets: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/`（SPI/注册表/决策树/配置/观测）、`nop-kernel/nop-xlang-truffle/src/main/`、`nop-kernel/nop-xlang-java/src/main/`、动态编译出口接入点

- Item Types: `Proof`

- [x] nop-xlang：SPI 契约 + 显式注册表（`@GlobalInstance` 模式）+ 统一决策树（静态路径判定接口/动态路径裁决/第三分支/单跳降级）+ 运行时求值期统一裁决入口 + 配置开关（`XLangConfigs` 增量）+ 降级观测（WARN 日志 + 指标 + 不可用条目查询）（Execution Notes §7：`io.nop.xlang.backend` 包 8 主类 + `XLang.execute` choke point + `XLangConfigs`/`XLangErrors` 增量）
- [x] nop-xlang-truffle：动态翻译能力注册（能力=动态翻译；Engine/池初始化失败→不可用条目不阻断启动）；单元级翻译失败事件→第三分支接线（消费 I8 接口）；池运行时作为动态路径执行体接入裁决入口（Execution Notes §7：`TruffleEvalExecutionBackend` + initializer + services + `XLangLanguage.removeTranslationFailureListener` 增量）
- [x] nop-xlang-java：静态生成物能力注册（条目 + 能力声明 + 扫描清单成员资格接口的 java 侧查询实现——绑定加载本体归 I10，接口先行）（Execution Notes §7：`JavaEvalExecutionBackend` + `IEvalStaticBindingBinder` 契约 + initializer + services）
- [x] 动态编译出口统一回调接入：按 Phase 1 清单逐出口接入裁决入口；出口不得自带后端 if/else（逐出口审查记录）（Phase 1 §1 清单 12 出口逐项接入点/不适用裁定 + choke point 单点接入 + 依赖方向结构性断言 = if/else 结构性不可能）
- [x] 单测：注册/反注册/查询/失败语义（初始化失败→不可用条目、不阻断启动）；决策树逐分支（静态命中/未命中、动态可用/降级、第三分支、单跳无跨跳、强制解释器短路）；配置开关矩阵（三开关 × 两后端可用性）；**后端未注册回归护栏显式用例**（注册表空/后端不在 classpath → 出口行为与现状一致，Phase 1 裁定的证据载体在此落地）；降级观测（WARN 日志断言 + 指标计数断言 + 不可用条目查询断言）；依赖方向断言（nop-xlang 无后端模块依赖/无 GraalVM import——不泄漏口径扩展到 SPI 层）；"出口无自带 if/else"断言按 Phase 1 定形机制执行（若为审查记录形态，记录附于 Execution Notes 出口清单，独立可审）（Execution Notes §7 测试清单：29+4+3 用例逐项对应）
- [x] 新代码无静默跳过：不可用后端请求按降级路径走且显式记观测事件；未知后端标识/非法契约调用 fail-fast（Execution Notes §7 静默跳过审查段）

Exit Criteria:

- [x] SPI/注册表/决策树/开关/观测代码在仓且每项有对应单测（guide 规则 25：新增功能显式列出测试覆盖）
- [x] 三后端注册路径在仓（java/truffle 显式注册 + 初始化失败不可用条目语义有测试注入验证）
- [x] 出口清单逐出口接入在仓或显式裁定不适用（无遗漏出口、无自带 if/else）
- [x] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am` 全绿；依赖方向断言通过（543/0/2 + 267/0/0 + 580/0/0）
- [x] No owner-doc update required（命名契约文档同步为 Phase 3 执行项，Phase 边界在案）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 路由场景矩阵对拍验证（经 I1 框架）与 docs-for-ai 同步

Status: completed
Targets: 路由场景矩阵测试（落点按 Phase 1 裁定——按后端模块拆分：java 侧场景落 nop-xlang-java test、truffle 侧场景落 nop-xlang-truffle test、裁决入口/出口回调通用断言落 nop-xlang test 或经 test-jar 驱动）、`docs-for-ai/`（Phase 1 裁定落点）

- Item Types: `Proof`

- [x] **路由场景矩阵落地（roadmap 验收）**：经 I1 对拍框架执行与断言——静态→java（合成清单 + 合成生成类；身份断言=生成类实例）/ 动态→truffle（身份断言=翻译 AST 经 CallTarget 执行；经池运行时驱动）/ java 降级→解释器（开关关 + 不可用条目两形态；身份断言=解释器树 + WARN/指标断言）/ truffle 降级→解释器（初始化失败注入；同上断言）/ **单元级降级判 FAIL**（truffle 列翻译失败注入 → 该列 FAIL 非 SKIP，对拍不变式"单元级降级判 FAIL 不判跳过"）——逐场景执行结果与解释器基线一致（三层断言）（Execution Notes §8：`TestJavaBackendRoutingScenarios` 4 + `TestTruffleBackendRoutingScenarios` 4，五类场景全覆盖 + 双载体 FAIL 语义红/绿对照）
- [x] 降级观测命名契约 docs-for-ai 同步（Phase 1 裁定落点：日志/指标命名契约 + 注册表不可用条目查询用法；如新增文档或路由变化同步 `docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md`）+ doc link checker（`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0）（`xlang-and-xpl-basics.md` 新增章节，增节裁定无路由变化；link checker 0 errors）
- [x] 三模块回归全绿（`./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C`）；不泄漏断言复跑（543/0/2 + 271/0/0 + 584/0/0 = 1398；`TestTruffleDependencyIsolation` 全绿）
- [x] I10 移交显式记录：静态路径 java 绑定消费的接口契约（扫描清单成员资格接口 + 能力声明）+ 本 plan 测试域合成形态与 I10 生产落地的对账点（责任链 repo-observable，落 plan Execution Notes + 当日 log）（Execution Notes §9）

Exit Criteria:

- [x] **路由场景矩阵全绿（五类场景 × 身份断言 + 结果一致性 + 降级观测断言；经 I1 框架执行与断言——显式引用关系可验证）——roadmap I9 验收项**
- [x] **单元级降级判 FAIL 有注入验证**（红/绿对照：翻译失败注入 → FAIL；正常 → 全绿）
- [x] **端到端验证**：动态编译出口（真实出口按 Phase 1 清单）→ 统一裁决 → 后端执行体（java 合成生成类 / truffle 池运行时）→ 三层对拍断言全链可运行
- [x] **接线验证**：裁决入口真实被出口回调调用（非仅注册表可查——至少一处真实出口的端到端断言：`testRealExitFlowsThroughAdjudicationChokePoint`（nop-xlang fake 后端）+ 两后端模块场景用例全部经真实出口）；第三分支真实消费 I8 观测事件（注入翻译失败 → 事件被消费 + 降级发生 + 观测记录——fallback 仅在事件关联命中时发生的结构性因果 + WARN/counter 断言）
- [x] docs-for-ai 命名契约同步在仓 + link checker 退出码 0
- [x] 回归不削弱既有测试（corpus 三列/矩阵/对拍全绿保持）；三模块全绿
- [x] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` 全绿
- [x] 受影响 owner docs 已同步（降级观测命名契约；`ai-dev/logs/` 对应日期条目已更新）

## Closure Gates

- [x] 路由场景矩阵全绿（静态→java / 动态→truffle / java 降级→解释器 / truffle 降级→解释器 / 单元级降级判 FAIL；逐场景身份断言 + 结果一致性；**经 I1 对拍框架执行与断言**）——roadmap I9 验收项
- [x] 后端注册 SPI 落地（显式注册表/能力集/可用性/不可用原因；无 classpath 扫描；初始化失败不阻断启动有测试）
- [x] 统一决策树全仓唯一（单跳降级、无跨跳、第三分支；决策输入四项操作化）且动态编译出口统一回调接入（清单化核验在案，无出口自带 if/else）
- [x] 配置开关落地（java/truffle enable + 强制解释器；不设全局默认后端）
- [x] 降级观测落地（WARN + 指标 + 不可用条目）且命名契约落 docs-for-ai
- [x] 模块依赖方向保持（nop-xlang 不依赖后端模块、无 GraalVM 泄漏——断言在仓）
- [x] I8 供给消费闭合（池运行时接入 + 观测事件第三分支接线）；I10 移交显式记录
- [x] 回归不允许削弱现解释器测试（纪律 3）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 已验证（a）裁决入口被真实出口调用（b）降级路径真实发生且被观测（c）无空方法体/静默跳过/no-op
- [x] `./mvnw compile -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am`
- [x] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C`
- [x] checkstyle / 代码规范检查通过（mission lint 口径）
- [x] doc link checker 退出码 0（docs-for-ai 变更后）

## Deferred But Adjudicated

（无——起草时无新 deferred 项。java 绑定加载/指纹失配降级归 I10、构建管线双清单归 I11、基准归 I12 均为 roadmap 既定归属（非本 plan in-scope 缺陷），Non-Goals 显式排除。执行中产生时按 guide 补录并写明 Why Not Blocking Closure。）

## Non-Blocking Follow-ups

- 设计文档旧 I 编号引用换算（W4-audit 移交清单 #1/#2）维持"下次设计文档修订"既定归属，非本 plan 范围。
- Q1/Q4 watch-only 触发口径量化归 I12（延续 I5-I8 Follow-up）。

## Closure

Status Note: 三后端统一选择机制生产代码路径落地收口——后端注册 SPI（显式注册表/能力集/可用性/不可用原因，ICoreInitializer+services 显式注册，无 classpath 扫描）+ 统一决策树（判定输入四项操作化/单跳降级无跨跳/第三分支消费 I8 观测事件/force-interpreter 静默诊断短路）+ 配置开关（java/truffle enable + force-interpreter + deployment-form，不设全局默认后端）+ 降级观测（WARN+指标+不可用条目查询，命名契约落 docs-for-ai `xlang-and-xpl-basics.md` 新增章节）+ 动态编译出口统一回调（`XLang.execute` choke point，12 出口清单化核验在案 + 依赖方向结构性断言 = 出口无自带 if/else 的结构性保证）。roadmap I9 验收项 = 路由场景矩阵五类场景全绿（经 I1 框架断言工具 + 裁决环身份断言 + FAIL-not-SKIP 经 harness 直驱，红/绿对照在案）。注册表空 = 行为与现状一致（三模块基线全保持：514/263/577 → 543/271/584 全绿）。三 Phase Exit Criteria 与 15 条 Closure Gates 逐条 PASS（独立 fresh closure audit CAN CLOSE，0 Blocker/0 Major/2 Minor 当场修复：docs reason 枚举补 `backend-unavailable` 窄竞态路径 + plan §2 unregister 语义文本修正为 no-op 定稿）。java 绑定加载归 I10（接口契约 + 合成形态对账点移交在案 Execution Notes §9）、构建管线双清单归 I11、基准归 I12，均为 roadmap 既定归属非本 plan 缺陷。
Completed: 2026-08-21

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh 子 agent（task `ses_fdf20458affehHTggZje34AKoH`，未参与实现）
- Audit Session: ses_fdf20458affehHTggZje34AKoH
- Evidence:
  - 逐项核验 A-I 全 PASS（SPI/注册表/决策树逐行为、配置缺省值、两后端注册路径 + I8 消费接线、8 测试类断言真实性、依赖方向结构性、docs 命名契约、plan 文本一致性、roadmap 状态）——live 文件行号引用在 audit 报告
  - 每条 Exit Criterion：PASS（三 Phase Exit Criteria 全勾选，测试与 docs 证据见 Execution Notes §7/§8）
  - 每条 Closure Gate：PASS（15/15 勾选；`./mvnw compile -am` 0 error、`test -am -T 1C` 543/0/2+271/0/0+584/0/0、`checkstyle:check -Pqa` 退出码 0、link checker 0 errors）
  - Anti-Hollow：(a) 裁决入口被真实出口调用——链路 `XLangCompileTool.compileSimpleExpr → ExprEvalAction.invoke → XLang.execute → router.executeAdjudicated`（`ExprEvalAction.java:46,51`/`XLang.java:50-52`），`testRealExitFlowsThroughAdjudicationChokePoint` + 两后端模块场景用例端到端证明；(b) 降级真实发生且被观测——`testJavaDegradedByConfigSwitch`/`testJavaDegradedByUnavailableEntry`/`testUnitLevelTranslationFailureFallsBackToInterpreter` counter delta=1.0 + WARN 事件断言（红/绿对照）；(c) 新增 ~12 主类逐行人工复核无空方法体/静默 no-op（binder 返回 null = 契约语义非静默跳过）
  - `scan-hollow-implementations.mjs --module nop-xlang|nop-xlang-java|nop-xlang-truffle --severity high` 退出码均 0
  - `check-plan-checklist.mjs <plan> --strict` 退出码 0（勾选 + Closure Evidence 写入后复跑）
  - Deferred 项分类检查：Deferred 区为空；I10/I11/I12 为 roadmap 既定归属 + §9 接口契约移交记录，无 in-scope live defect 被降级
  - 2 Minor 当场修复：docs-for-ai reason 枚举补 `backend-unavailable`（窄竞态路径）；plan Execution Notes §2 unregister 语义文本修正（no-op 条件式为先例同款定稿语义）

Follow-up:

- 无 plan-owned 剩余工作。I10（java 生成类加载集成——消费 §9 移交契约）/I11（构建集成 + 扫描清单产物）/I12（基准 + 全量对拍收口）为 roadmap 后继条目。
