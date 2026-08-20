# I10 java 生成类加载集成（绑定决策树 + RCM 缓存）

> Plan Status: completed
> Last Reviewed: 2026-08-21（closure audit CAN CLOSE，见 Closure 段）
> Source: `ai-dev/backlog/xlang-execution-optimization-roadmap.md` I10（范围/验收 = 定稿条目）；设计冻结于 `ai-dev/design/xlang-java/01-architecture-baseline.md`（§五生成类加载与 RCM 集成绑定决策树 = 本 plan 行为规格、§七调用约定）与 `ai-dev/design/xlang-execution/01-architecture-baseline.md`（§三判定时机三档之"模型加载期"、§六与 RCM/构建管线集成边界）；I9 移交契约 = `ai-dev/plans/xlang-execution-optimization/2026-08-21-0240-2-i9-unified-backend-selection.md` Execution Notes §9；对拍口径同 execution 01 §五
> Mission: xlang-execution-optimization
> Work Item: I10
> Related: I9（前置：SPI/决策树/观测 + §9 移交契约供给方，已完成）；I11（后继：构建期任务 + 双清单文件产物 + `_gen/` 布局 + native 兼容——本 plan 只定双清单**内存契约**与消费缝，文件格式与生产产物归 I11）；I1（验收引用：对拍框架 `io.nop.xlang.compare` harness 断言工具）

<!-- Draft review: round-1（fresh session ses_fdf0bc25fffebxu7YA2rQTYDT1，Verdict Not Ready：0 锚点错误；1 Blocker——Phase 3 验收第一项的测试域生成类物化机制无决策项（生产绑定路径/非 nop-javac/无自定义 ClassLoader 三约束在无构建产物时点如何同时满足未裁定）；1 Major——租户差异化树测试构造无机制锚点（仓内无先例）→ 修复（新增 D6 物化机制决策（转译器产物作 Maven 编译夹具 + 反漂移断言 + Class.forName 常规加载）；D5 补租户五锚点（CFG_TENANT_RESOURCE_ENABLED/enabledTenantPaths/ContextProvider/DeltaResourceStore/每租户 cache）+ 禁止模拟分级语义；顺手消化 Minor：三层断言补异常语义层/D3 router 增量对账显式化/D1 指纹强度裁定/Phase 2 补 D4 消费面测试项/ComponentCacheEntry 措辞修正）→ round-2（fresh session ses_fdf03ab5affeGyrVnXHODcnkVf，Verdict **Ready**：0 Blocker/0 Major——全量锚点核验通过、roadmap 五范围要素与三验收 1:1 映射、六决策裁定输入完整性经想象性分析推演无断层；6 Minor 建议执行期吸收（xlib hook 路径枚举/I9 引文逐字/稳态 WARN 噪声策略/Goals 标签/reason 五值基线/身份断言载体措辞）→ 当场吸收全部 6 项 → 共识达成 → active。 -->

## Purpose

落地统一决策树静态路径的 java 侧生产实现（设计 java §五绑定决策树的行为规格）：**生产 binder**（生成类清单消费 + 生成类 classpath 常规加载 + Executable 树指纹一致性校验，经 I9 移交的 `setBinder` 接入缝接入）+ **模型加载期绑定**（编译单元加载完成后"生成类优先 / 解释器兜底"，绑定结果随 RCM 缓存条目复用）+ **降级观测事件分级**（stale 缺陷 vs 租户差异化树预期稳态降级可区分）。验收 = roadmap I10 三项：绑定路由对拍（静态单元经绑定后 java 列 vs 解释器列一致 + 身份断言）、指纹失配/清单缺失降级解释器 + 观测事件断言、清单外资源走动态路径不记降级事件断言——测试以转译器 API 合成双清单产物，生产管线产物归 I11。

## Current Baseline

- **I9 已 `completed`**（前置满足）：`io.nop.xlang.backend` 包（SPI 契约/注册表/决策树/观测，live 11 文件）+ `XLang.execute` choke point + 路由场景矩阵五类场景全绿（java 侧执行体为**测试域合成绑定**——转译器 + nop-javac 内存编译，`TestJavaBackendRoutingScenarios.syntheticBinder`）。
- **I9 §9 移交契约（live 锚点）**：(a) 清单成员资格 = `IEvalStaticBackend#isStaticCandidate(resourcePath)`，java 侧 `JavaEvalExecutionBackend.setStaticScanList` 已可设置（生产清单产物归 I11）；(b) 绑定供给 = `JavaEvalExecutionBackend.setBinder(IEvalStaticBindingBinder)` 接入缝，绑定执行体契约 `IEvalStaticBinding#execute(EvalRuntime)` + `getBindingArtifact()` 身份证据；(c) binder 返回 null = "应有而缺失"降级观测（reason=`generated-binding-missing`，Phase 2 测试在案）；(d) `JavaEvalExecutionBackend.markUnavailable(reason)` = 生产可用性接入缝（I11 消费）；(e) 分级观测归 I10 扩展（`EvalBackendObservation` reason 增量）；(f) I10 生产落地 = 真实 classpath 加载 + 树指纹校验 + RCM 缓存，"同一契约、同一降级语义，仅供给方替换，裁决树/观测/配置开关零改动（reason 枚量增量除外）"。
- **转译器 live**：`ExecToJavaTranslator.translate(resourcePath, tree)` 单根翻译（支持集 120 = `ExecNodeBaseline.javaTargetSet()`）；`EvalMethodConvention`（入口 static `execute`、首参 `$scope`、模板 `$out` 第二隐参、类名确定性派生——javadoc 注记"生产 `_gen/` 布局与包名策略由 I11 定稿"与"同形路径折叠唯一性校验归 I11"）；`GeneratedEvalBinding.findEntryMethod/bind`（EvalMethod 入口定位 + `MethodInvoker` 包装先例，生产 binder 可复用）。
- **树指纹现状**：`TreeFingerprints` 定义在 **nop-xlang-truffle**（`io.nop.xlang.truffle.translate`，载荷白名单纪律 + 可翻译未白名单 fail-fast，javadoc 明示"与 java 后端生成类清单的指纹纪律**对称**"）；nop-xlang-java 禁止依赖 nop-xlang-truffle（设计 execution 01 §二）→ **java 侧树指纹实现不存在 = 真正 gap**。
- **模型加载路径 live**：`XplModelLoader.loadObjectFromPath/loadObjectFromResource` → `XLang.parseXpl(resource, outputMode)` → `XplModel`（`extends ExprEvalAction`，`expr` 为 final）；RCM `ModelLoader.loadObjectFromPath` 包装包级私有静态内部类 `ComponentCacheEntry`（`ResourceComponentManager.java:409`，model 字段——nop-xlang 不可直接引用，缓存复用以"绑定后模型即缓存条目 model"的等效语义实现）经 `IResourceLoadingCache` 缓存（租户隔离经 `ResourceTenantManager.supportTenant(path)` 分路）；xlib 经 `ResourceComponentManager.loadComponentModel(libPath)` → `XplTagLib`（**多标签、每标签独立 Executable 的多根形态**）。
- **执行期组合 live**：`ExprEvalAction.invoke/doInvoke` → `XLang.execute`（choke point）；`EvalBackendRouter.decide` javadoc 明示"供测试与静态路径 java 绑定集成消费"——加载期绑定的裁决入口已预留。
- **观测 live**：`EvalBackendObservation` reason 代码常量四值（config-disabled / unavailable / unit-translation-failure / generated-binding-missing）；docs-for-ai §执行后端选择与降级观测（行 377 起）现列五值（含 `backend-unavailable`，I9 closure 修复项）——Phase 3 docs 同步以 docs 现列表为 live 基线合并，防漏并。
- 真正剩余的 gap：无生产 binder（无清单消费、无 classpath 加载、无指纹一致性校验）；无模型加载期绑定与"绑定结果随 RCM 缓存条目复用"语义；无 stale vs 租户差异化分级观测；java 侧 Executable 树指纹实现不存在。

## Goals

- **生产 binder（roadmap 范围要素：清单成员资格判定 + 生成类清单树指纹一致性校验）**：生成类清单内存契约消费（resourcePath → 生成类名 + 树指纹）+ 生成类 classpath 常规加载（无自定义 ClassLoader，`GeneratedEvalBinding` 先例复用）+ **树指纹一致性校验**（施加对象 = Executable 树指纹：构建期固化指纹 vs 运行时树指纹比对，防 stale、Delta 变更不漏检——设计 java §五"不采用源资源指纹"裁定保持）+ 经 `setBinder` 接入 `JavaEvalExecutionBackend`；绑定含身份证据（生成类入口 Method/生成类）。
- **模型加载期绑定（范围要素：生成类优先/解释器兜底 + 绑定结果随 RCM 缓存）**：编译单元加载完成后按设计 java §五伪代码绑定——扫描清单成员资格 → java 启用+可用+绑定命中 → 生成类执行体；缺失/失配 → 降级观测 + 解释器兜底；清单外 → 动态路径（不记降级）；绑定结果随 RCM `ComponentCacheEntry` 缓存复用（RCM 职责边界不变：nop-core 不感知后端概念，绑定在 nop-xlang 域内完成后模型照常入缓存）。
- **降级观测事件分级（范围要素）**：stale 缺陷（默认形态下指纹失配/清单内类缺失——codegen 漏跑/未重生成）vs 租户差异化树预期稳态降级（租户 delta 合并致树指纹失配——预期行为非缺陷）可区分（reason 增量 + docs-for-ai 同步）。
- **扫描清单供给缝（验收"测试以转译器 API 合成双清单产物"的使能件）**：清单数据的可注入供给形态定稿（缺省空态 = 行为与现状一致；生产文件产物与格式归 I11）。
- **绑定路由对拍（roadmap 验收）**：三项验收经真实绑定路径（非 `JavaBackendColumn` 直驱）+ I1 harness 断言工具落地。

## Non-Goals

- 构建期任务注册、扫描口径清点、双清单**文件**格式与存放路径、`_gen/` 产物布局、重生成幂等、native image 兼容、truffle 镜像排除、docs-for-ai 新模块开发指南（I11——本 plan 义务仅限观测分级 reason 枚举/分级语义的 docs 同步）。
- 性能基准与全量三后端对拍收口（I12）。
- 转译器覆盖扩展（I4 闭环支持集 120 不变；本 plan 不新增可翻译节点类）。
- 决策树动态路径 / truffle 侧任何变更（I9 已收口；本 plan 触碰 `io.nop.xlang.backend` 仅限静态分支语义增量与 reason 枚举增量）。
- corpus 扩充（对拍消费既有 corpus 静态单元）。
- RCM/nop-core 行为变更（职责边界不变；如裁定需要 nop-core 配合，视为 scope 外并回引擎）。

## Scope

### In Scope

- nop-xlang-java：生产 binder + java 侧 Executable 树指纹实现 + 清单内存契约与供给缝。
- nop-xlang：模型加载期绑定接入（含已绑定执行体与 choke point 的组合语义，如裁定需要触碰 `EvalBackendRouter` 静态分支/reason 枚举增量）。
- 绑定路由对拍测试（落点 nop-xlang-java test，经 I1 harness 断言工具）+ 观测分级断言 + 租户隔离断言。
- docs-for-ai：`xlang-and-xpl-basics.md` §执行后端选择与降级观测 的 reason 增量/分级语义同步。

### Out Of Scope

- 同 Non-Goals。

## Execution Notes

### Phase 1 决策记录（2026-08-21）

**0. 前置硬门禁核验**：I9 roadmap live 状态 = `done`（`ai-dev/backlog/xlang-execution-optimization-roadmap.md` I9 条目，2026-08-21 closure audit CAN CLOSE）。I9 §9 移交契约五通道 live 核验：(a) `IEvalStaticBackend#isStaticCandidate` + `JavaEvalExecutionBackend.setStaticScanList` 在案；(b) `JavaEvalExecutionBackend.setBinder(IEvalStaticBindingBinder)` 接入缝在案（`io.nop.xlang.java.backend`）；(c) null = 应有而缺失语义 + reason=`generated-binding-missing`（router 静态分支 + `TestJavaBackendRoutingScenarios` 在案）；(d) `markUnavailable(reason)` 在案；(e) `EvalBackendObservation` 四 reason 常量在案（分级归本 plan 增量）。

**1. D1 树指纹实现落点与纪律定稿**：

- **裁定 = 候选 (a)：nop-xlang-java 新实现** `io.nop.xlang.java.gen.ExecutableTreeFingerprints`（载荷白名单 + 支持集内未白名单 fail-fast，纪律与 truffle `TreeFingerprints` 对称——**共享纪律而非共享实现**）。拒绝 (b) 抽取共享到 nop-xlang：(i) 模块边界——nop-xlang-java ↛ nop-xlang-truffle 禁依赖（设计 execution 01 §二），共享实现只能落 nop-xlang；(ii) 语义边界——载荷白名单与**各自转译器的支持集**绑定（java 白名单 ↔ `ExecToJavaTranslator.getSupportedNodeClasses()` = 120 类 live；truffle ↔ 其自身支持集），共享实现会把两后端的载荷覆盖表与支持集耦合进内核（nop-xlang 感知后端实现细节，违反"契约对实现的感知止步于契约"）；(iii) I11 构建任务（落 nop-xlang-java）与运行时校验必须**同一实现**——落 nop-xlang-java main 则构建期写入清单与运行时比对天然同算法同实现。
- **指纹强度裁定 = hex SHA-256（64 字符）**，非 long。理由：java 侧指纹是防 stale 校验且将被 I11 **持久化进生成类清单**的数据界面；64-bit long 的"必不同"仅概率成立（每对碰撞 ~2^-64），作为持久化完整性契约强度不足须显式认领——SHA-256 碰撞概率在实践中不可达（~2^-256），且每加载仅计算一次、成本可忽略。truffle 侧 long 保持不变（翻译缓存键口径、内存态不持久化）——两侧口径差异记录在案：truffle 指纹与 java 指纹互不比较、互不替代。
- 防碰撞要求保持：同 resourcePath 树任一差异（含源位置 path:line:col、标量载荷、子树结构）必产生不同指纹——白名单全载荷混合 + 源位置混合 + 字段定界（长度前缀防拼接歧义）。
- **白名单覆盖面与支持集一致性断言化**：`TestExecutableTreeFingerprints` 以 `ExecNodeBaseline.javaTargetSet()`（= `ExecToJavaTranslator.getSupportedNodeClasses()`，`TestExecTranslationCoverageMatrix.testSupportSetMatchesJavaTargetSet` 双向一致在案）逐类经 `MinimalNodeFactory.minimalTree` 真实指纹不 fail-fast（证据形态裁定类同矩阵：`GenNodeAttrExecutable` 宿主载体 / `LazyCompiledExecutableFunction` 载荷不可解析跳过 / `FunctionalAdapterExecutable` 类名混合——分支与 truffle 同构）；支持集内未白名单节点 → `IllegalStateException` fail-fast（新节点类漂移红灯，转译器支持集扩展而指纹白名单未跟随时矩阵级红灯）。

**2. D2 双清单内存契约与供给缝定稿**：

- 扫描清单（静态性判定）= `Set<String>` resourcePath should-set（既有 `JavaEvalExecutionBackend.setStaticScanList` 供给缝，无契约变更）。
- 生成类清单 = 新类 `io.nop.xlang.java.gen.GeneratedClassManifest`（不可变）：条目 `resourcePath → {className（FQN）, treeFingerprint（hex SHA-256）}`，`find(path)` 查询、`size()` 诊断。**内存数据契约即 I11 清单文件的序列化界面**（I11 产文件填充本契约；文件格式与存放路径显式排除、归 I11）。
- 供给缝 = `JavaEvalExecutionBackend.setGeneratedClassManifest(GeneratedClassManifest)`：内部构造生产 binder `GeneratedClassBindingBinder`（消费内存契约：清单查找 + 指纹校验 + `Class.forName` 常规加载 + `GeneratedEvalBinding.findEntryMethod` 入口定位）经 I9 移交的 `setBinder` 接入缝注册——**同一接入缝、同一降级语义，仅供给方替换**（I9 §9(f) 对账见 D3）。
- 缺省空态 = 无清单 + 无 binder：`isStaticCandidate` 恒 false + `findStaticBinding` 恒 null = 全动态路径 = 行为与现状一致。
- **非法清单数据裁定 = 注入时 fail-fast**：`GeneratedClassManifest.of(...)` 校验 resourcePath 非空、className 合法标识符链、treeFingerprint 为 64-hex；违规抛 `NopException` 携带条目信息（构建/配置错误应在接入点暴露，不静默）。

**3. D3 模型加载期绑定 hook 与 RCM 缓存组合语义定稿**：

- **hook 落点 = `XLang.parseXpl(IResource, XLangOutputMode)`**（nop-xlang 域内、依赖方向合法）。live 核验：`xpl/xgen/xrun` 文件类型全部经 register-model.xml → `XplModelLoader`/`HtmlXplModelLoader`/`NoneXplModelLoader` → `XLang.parseXpl` 单点装载（`XplModelLoader.loadObjectFromPath/loadObjectFromResource` 源码直调）；RCM 装载与直接 parseXpl 均流经该单点。xlib 不经此路径（见 D4）。绑定助手 = `EvalBackendRouter.bindLoadedUnit(resourcePath, tree)`（设计 java §五伪代码逐分支落地，落 `io.nop.xlang.backend` 包）。
- **组合语义 (1) 绑定结果随 RCM 缓存条目复用**：parseXpl 以绑定后执行体构造 `XplModel` 返回 → RCM `ComponentCacheEntry.model` 即携带绑定结果（"绑定后模型即缓存条目 model"的等效语义达成，`ComponentCacheEntry` 包级私有不可直接引用的约束绕开）；资源变更检测驱动的重载自然触发重绑定（重载 = 重新 parseXpl = 重新绑定）。nop-core 零改动（职责边界保持）。清单/开关热变更不主动失效缓存（裁定记录：绑定结果随缓存条目生命周期；I11 如需清单文件变更触发失效，可将清单文件纳入资源依赖——I11 界面）。
- **组合语义 (2) 已绑定直通**：绑定命中产物 = `EvalStaticBoundExecutable`（新类，`io.nop.xlang.backend`：包装原树 + 绑定 + backendId，`getLocation()/display()/visit()/containsReturn/Break` 委托原树）；choke point（`executeAdjudicated`）识别即**直通执行绑定体**——无重复指纹计算、无降级观测，decision ring 记 STATIC + artifact = `binding.getBindingArtifact()`（生成类入口 Method）。绑定缺失/失配/不可用产物 = `EvalStaticDegradedExecutable`（新类：包装原树 + reason）；choke point 识别即**直通解释器执行原树**（观测已在绑定期完成，不重复记、不再咨询 binder、不重算指纹），decision ring 记 INTERPRETER + degraded=true + artifact=原树。**未标记树**（碎片/字符串出口产物/后端注册前加载/测试域）走 I9 决策树**原语义零改动**。`bindLoadedUnit` 各静默分支：注册表空 / force-interpreter / 开关关 / 清单外 → 返回原树（§五伪代码条件 1/2 逐字对应；开关关时执行期按 I9 静态分支观测——加载期不越权）。不可用条目 → 绑定期观测（reason=unavailable）+ degraded 标记（稳态噪声 = 每加载一次，执行期静默）。
- **I9 §9(f) 显式对账**：原文 = "裁决树/观测/配置开关零改动（I10 不需要触碰 `io.nop.xlang.backend` 包）"。该"不触碰包"预判被 I9 同条 bullet 3"分级观测归 I10 扩展（`EvalBackendObservation` reason 增量）"与本 plan D3 直通语义增量**合法超越**（超越点 = (i) reason 常量增量两枚 + 稳态去重观测方法——I9 已预留的枚量增量；(ii) `executeAdjudicated` 增两条已裁定执行体直通分支 + `bindLoadedUnit` 新方法 + 两个执行体包装类——均为"加载期绑定"新语义的承接结构，非既有裁决分支改写）。裁决树判定输入四项/单跳降级/观测命名契约（消息键/counter/tags）/配置开关零改动保持。
- **组合语义 (3) 租户隔离**：绑定在加载线程按**当前租户上下文**发生（RCM per-tenant loading cache → 每租户各自模型实例 → 各自绑定，同路径两租户不串用）；降级分级输入 = 绑定时租户上下文（D5）；`ResourceTenantManager.supportTenant` 分路与 `TenantAwareResourceLoadingCache` 既有机制零改动消费。

**4. D4 绑定作用面与编译单元形态裁定**：

- **xpl 单根单元确定纳入**（含 xgen/xrun 同经 parseXpl 装载的文件类型）：绑定作用面 = 单元根 executable；清单键 = resourcePath。
- **xlib 多标签根单元裁定 = 每标签条目形态、I10 落地范围 = xpl 形态**：消费侧形态定稿——清单键 = `resourcePath + '#' + tagName`（键命名空间与 xpl 路径天然不交：resourcePath 不含 '#'；与 I11 清单格式可衔接），条目指纹 = 该标签 source 树指纹，绑定作用面 = 每标签 executable。I10 不落地 xlib 绑定 hook，理由：(i) live 事实——xlib 经 `xlib.register-model.xml` 通用 `xdsl-loader` 装载 → `XplTagLib` 多根（每标签独立 executable），不流经 `XplModelLoader/parseXpl`，且 `XplTagLib` 不可变、无逐标签重绑缝（D3 hook 落点按本裁定枚举后确认单点 parseXpl 不覆盖 xlib）；(ii) 转译器为单根 API，xlib 每标签生成类涉及 `_gen/` 布局/包名策略/同形路径唯一性——`EvalMethodConvention` javadoc 显式移交 I11 的定稿项；(iii) 生产扫描/生成归 I11。**约束记录（I11 衔接）**：xlib 绑定落地前 I11 不得将 xlib 路径纳入扫描清单（否则 xlib 标签体经出口编译时按 I9 执行期静态分支误判成员 → 指纹必失配 → 误降级观测）。
- **I9 动态出口清单 E1-E6 对清单内静态资源的绑定语义复核**（逐出口 vs 设计 java §五伪代码）：E1-E3（compileSimpleExpr/fullExpr/templateExpr 字符串出口）/E4（compileTag 运行时 XNode）/E5（compileXpl 字符串→XNode）/E6（genJsonExtends 经 E4）——产物均为**运行时字符串/运行时节点**编译的树，产生时机 = 动态源，**不是编译单元加载**（§五 hook 的施加对象是 onCompilationUnitLoaded），不适用加载期绑定；其 SourceLocation path 若落清单内（实践中仅测试域构造该形态），执行期按 I9 决策树静态分支处理——生产 binder 指纹必失配 → 分级降级观测 + 解释器（可观测非静默，行为与 §五"非单元资源不适用绑定"裁定一致：解释器兜底正确、观测一次为可接受的哨兵噪声）。E4 的**模型加载形态**（`XplModelParser.doParseResource` 内 compileTag，经 parseXpl 流入）= 编译单元加载 ✓ 绑定（即 D3 hook 所在，corpus 静态单元即此形态）。逐出口一致或显式裁定成立。

**5. D5 降级观测分级机制定稿**：

- **reason 增量（`EvalBackendObservation` 两枚）**：`generated-fingerprint-mismatch`（清单条目在、指纹失配、无租户上下文 = **stale 缺陷**）；`tenant-divergent-tree`（指纹失配 + 绑定时租户上下文活跃 = **预期稳态**）。既有 `generated-binding-missing` **归位** = 清单条目缺失 **或** 清单内类缺失/类加载失败/入口约定违规（"应有而缺失"族——stale 缺陷；运营处置同为重生成，合并计数裁定记录）。
- **分级判定输入 = 绑定发生时 `ContextProvider.currentTenantId()`**（live 可得：`ContextProvider.runWithTenant`/`currentTenantId`，nop-api-core 对 nop-xlang-java 可见）。机制依据：`_gen/` 产物只从基树生成，租户 delta 合并树结构性无生成类 → **任何租户上下文下的失配 = 预期稳态**；默认形态（无租户上下文）失配 = 基树变更未重生成 = stale。判定不可得（上下文读取异常）→ **保守归 stale**（缺陷优先暴露，宁响勿漏）。
- **稳态降级日志噪声策略裁定 = once-per-path 去重（选去重、不改级别）**：`tenant-divergent-tree` 的 WARN 按 sourceKey(resourcePath) 去重（有界去重集 ≤1024，超界回退每次 WARN——宁噪声不无界内存）；**指标计数不衰减**（每次降级都计数）。理由：级别降 INFO 会脱离默认 WARN 扫描视野（不可发现性）；去重保留首次可发现性 + 消除稳态噪声。stale 族 reason（generated-binding-missing / generated-fingerprint-mismatch）保持**每次 WARN**（缺陷应响亮，设计 java §五"每次降级 WARN"对缺陷形态保持）。
- **观测记录方 = 生产 binder**（`GeneratedClassBindingBinder` 返回 null 前自记分级观测——唯一持有分级输入与失配细节方）；经加载期裁定的树必带 bound/degraded 标记、执行期不再进静态分支 → **双记不发生**（D3 对账）；未经加载期裁定的树走 I9 执行期分支（reason=generated-binding-missing，I9 测试域语义保持零改动）。
- **租户差异化树测试构造机制（真实构造，禁止"注入失配指纹 + 贴租户标签"模拟）**：五锚点落地 = (1) `CFG_TENANT_RESOURCE_ENABLED`（`CoreConfigs`，live 读——VFS 租户分路 `DeltaResourceStore.getResource` 与 RCM cache 创建两处均活读）；(2) `ResourceTenantManager.enabledTenantPaths` 缺省仅 `/resolve/`——测试资源路径落**非 nop- 前缀模块段**（`isSupportTenant` 模块名回退路径 live 成立，不依赖 enabled 集配置）；(3) `ContextProvider.runWithTenant(tenantId, task)` 租户上下文；(4) `DeltaResourceStore` 租户分路——测试 `ITenantResourceProvider` 供给 `InMemoryResourceStore`，内含 `/_tenant/<tenantId>/<path>` 覆盖资源（`ResourceHelper.buildTenantPath` 口径 = `TENANT_PATH_PREFIX` "/\_tenant/"）；(5) RCM 每租户 loading cache——**专用测试 modelType**（`registerComponentModelConfig` 注册新 fileType，其 cache 在租户启用后首次创建 = `TenantAwareResourceLoadingCache`；避免复用既有 xpl modelType cache 的创建时序耦合——cache 对象的租户感知在创建时固定，`clearCache` 只清条目不重建）。构造 = 同路径两租户：基资源树 A（清单指纹源）vs 租户 B 覆盖资源树 B'（不同源不同树），断言租户 A 绑定命中 / 租户 B 稳态降级 + 结果语义各自正确 + 模型实例隔离。

**6. D6 测试域生成类物化机制定稿**：

- **裁定机制（Phase 3 验收第一项载体）**：转译器 API 产出 corpus **全部静态单元**（覆盖 A 20 + 覆盖 B 17 + CorpusV1 11 = **48 单元**）生成源码 → 提交为测试夹具源码 `nop-xlang-java/src/test/java/io/nop/xlang/gen/Gen_*.java`（包名 = `EvalMethodConvention.GENERATED_PACKAGE`、类名 = resourcePath 确定性派生——Maven test 常规编译入测试 classpath、标准 ClassLoader；夹具文件头注记生成来源与"DO NOT EDIT"）→ **反漂移断言** = `TestGeneratedFixtureSources` 逐单元断言夹具源码 == `ExecToJavaTranslator.translate(corpus 树)` 当前输出（逐串相等——转译器演进致夹具漂移即红灯；再生成载体 = `GeneratedFixtureMain`（test sources，手动运行写盘））→ 合成清单条目 =（corpus 单元路径 → 夹具类 FQN + corpus 编译树指纹）经 `setGeneratedClassManifest` 注入 + 扫描清单经 `setStaticScanList` 注入 → 生产 binder `Class.forName` 常规加载。
- **四约束满足证明**：生产绑定路径（binder = `GeneratedClassBindingBinder` 经 I9 `setBinder` 缝接入，加载期绑定经 `bindLoadedUnit`）✓；非 `JavaBackendColumn` 直驱（经模型加载 → 绑定 → invoke）✓；非 nop-javac 内存编译通路（Class.forName 测试 classpath 常规加载）✓；无自定义 ClassLoader（Maven test 编译 + 应用类加载器）✓。
- **备选机制排除记录**：(i) nop-javac 内存编译（I2/I9 测试域先例）——直接违反"非 nop-javac 内存编译通路"验收约束；(ii) Maven 构建期生成插件——I10 时点无构建任务（I11 范围），且引入 `_gen/` 布局提前定稿违反 I11 边界裁定；(iii) 测试内自定义 ClassLoader/defineClass——违反"无自定义 ClassLoader"约束。三类排除理由均为验收约束直接违反，非偏好选择。

**7. Phase 2 落地记录（2026-08-21）**：

- **nop-xlang main（`io.nop.xlang.backend` SPI 域内增量 + 一处接线）**：`EvalStaticBoundExecutable`（绑定命中包装：原树 + 绑定 + backendId，location/display/visit/containsReturn/Break 委托原树，execute 直通绑定体）+ `EvalStaticDegradedExecutable`（降级包装：原树 + reason，execute 直通全局执行器）+ `EvalBackendRouter.bindLoadedUnit(resourcePath, tree)`（§五伪代码逐分支：注册表空/force/开关关/清单外返回原树静默、不可用观测+降级包装、命中 bound 包装、缺失降级包装不补记）+ `executeAdjudicated` 两直通分支（bound → STATIC 决策 artifact=绑定证据；degraded → INTERPRETER 决策 artifact=原树，均不重复观测/咨询/指纹计算）+ `EvalBackendDecision.boundUnit` 工厂 + `EvalBackendObservation` 增量（REASON_GENERATED_FINGERPRINT_MISMATCH / REASON_TENANT_DIVERGENT_TREE 两常量 + `onSteadyStateDegradation`（WARN once-per-path 去重集 ≤1024 超界回退每次 WARN、`clearSteadyStateDedup` 测试隔离）+ `degradationCount` 保持）+ `XLang.parseXpl` 一处接线（绑定后模型构造，未包装返回原模型）+ `XLangErrors.ERR_XLANG_GENERATED_MANIFEST_INVALID_ENTRY`（+ARG_RESOURCE_PATH）。
- **nop-xlang-java main**：`io.nop.xlang.java.gen.ExecutableTreeFingerprints`（D1：hex SHA-256、载荷白名单与 truffle `TreeFingerprints` 分支逐类对称移植、字段以类型标签+长度前缀定界、支持集内未白名单 fail-fast、不可翻译弱兜底）+ `io.nop.xlang.java.gen.GeneratedClassManifest`（D2 内存契约：Entry{resourcePath, className, treeFingerprint}，`of` 注入时 fail-fast 校验（路径/类名/64-hex），`find/size/empty`）+ `io.nop.xlang.java.backend.GeneratedClassBindingBinder`（生产 binder：清单查找 → 指纹校验（租户上下文分级：`ContextProvider.currentTenantId()` 活跃 = tenant-divergent-tree 稳态去重观测，否则 generated-fingerprint-mismatch 每次 WARN）→ `Class.forName` 常规加载（失败 = generated-binding-missing）→ `GeneratedEvalBinding.findEntryMethod`（约定违规 = degrade）→ `ReflectiveGeneratedBinding`（$scope/$out 双形态、artifact = 入口 Method）+ `JavaEvalExecutionBackend.setGeneratedClassManifest`（生产供给缝，内部经 I9 `setBinder` 缝接生产 binder）。
- **测试清单（新增 16 用例 + 123 参数化 + 8 用例 = 147）**：nop-xlang-java —— `TestExecutableTreeFingerprints` 123（格式/确定性/标量载荷/slot/源位置 line-col-path 敏感性 + **白名单覆盖面 = javaTargetSet 逐类 MinimalNodeFactory 真实指纹**（开发中实际拦截一处白名单缺口——AbstractBinaryExecutable 族 catch-all 漏移植，覆盖测试红灯 → 补齐，fail-fast 纪律实证）+ 子类身份可区分 + 不可翻译弱兜底不 fail-fast）；`TestGeneratedClassBindingBinder` 7（清单命中加载执行 vs 解释器 typedEquals + corpus 声明预期 / 条目缺失 stale WARN+counter / 指纹失配无租户 stale / 指纹失配租户活跃稳态（WARN 去重一次 + counter 两次不衰减）/ 类缺失 / 非法清单注入 fail-fast 三形态 / 清单外不咨询 binder（计数断言））；`TestModelLoadingBinding` 6（hook 接线：加载后模型执行体 = bound 包装 + invoke → STATIC artifact=入口 Method + 结果=corpus 预期 / 模板单元 $out 双参绑定 + 输出层对拍 / 已绑定直通：3 次 invoke binder 咨询恰一次 + 零虚假观测（计数断言）/ 指纹失配：绑定期观测一次 + 执行期静默 INTERPRETER artifact=原树 + 结果正确 / 清单外不包装零观测 / 缺省空态 = 干净树正确结果）；`TestTenantIsolationBinding` 3（**D5 真实构造**：CFG_TENANT_RESOURCE_ENABLED 活读启用 + 专用 modelType `itest-xpl`（fileType itxpl，cache 创建即 TenantAware）+ ITenantResourceProvider 供给 `/_tenant/B/` 覆盖资源（以标准路径呈现）+ runWithTenant 上下文 + 非 nop- 模块段路径——租户 A 经 RCM 加载绑定命中生成类（artifact=确定性派生夹具类）+ 租户 B 同路径覆盖树稳态降级（counter+1、WARN 去重一次、INTERPRETER artifact=原树、结果=租户语义 31≠21）+ 模型实例 assertNotSame per-tenant cache + A 复载 assertSame 无串用）。nop-xlang —— `TestEvalBackendLoadTimeBinding` 8（§五逐分支：force/开关关/清单外/注册表空返回原树、不可用观测一次执行静默、命中直通 artifact=绑定证据、缺失降级不补记（防双记裁定）、包装 location 委托）。D4 消费面：xpl 落地面全覆盖；xlib per-tag 形态 = I11 衔接契约（不落地无消费面测试，D4 记录）。
- **夹具物化（D6）**：`GeneratedFixtureMain`（再生成载体）+ corpus 全部 48 静态单元 + 租户测试单元 = **49 个夹具类**落 `src/test/java/io/nop/xlang/gen/Gen_*.java`（Maven test 常规编译；包 = GENERATED_PACKAGE、类名 = resourcePath 确定性派生）；`ProductionBindingCorpus`（48 单元供给 + 路径 VFS 标准化 + `CorpusCoverageB.outputModeOf` 逐单元输出模式 + 干净编译 `parseClean`（XplModelParser 同装载语义不经 hook）与生产装载 `loadBound`（XLang.parseXpl 经 hook）双通道 + 预期重映射（异常源位置 path 前导斜杠、line/col 不变））。
- **验证**：`./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` = **551/0/2 + 410/0/0 + 584/0/0**（I9 基线 543/271/584 全保持——nop-xlang 543→551（+8 hook）、java 271→410（+139）、truffle 584 零变更）；`./mvnw checkstyle:check -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -Pqa` 退出码 0；`TestEvalBackendDependencyDirection` 全绿（结构性保证不回退）。
- **静默跳过审查**：全部降级路径显式观测（stale 每次 WARN、稳态去重 WARN + counter 不衰减——断言在案）；非法清单数据注入时 fail-fast（三形态测试）；`Class.forName` 失败/入口约定违规 = 分级降级观测非静默（测试在案）；binder null 契约语义保持（裁决入口 I9 分支对未标记树零改动）。

**8. Phase 3 落地记录 + I11 移交（2026-08-21）**：

- **绑定路由对拍（roadmap 验收第一项）**：`TestProductionBindingRoutingScenarios.testAllCorpusStaticUnitsBindAndMatchInterpreterColumn` + `testLoadedModelIdentityIsGeneratedBinding`（nop-xlang-java test）——corpus **全部 48 静态单元**（覆盖 A 20 + 覆盖 B 17 + V1 11）经**生产绑定路径**执行：`installFullSupplies`（合成双清单 = 夹具类 FQN + 干净树指纹 + 扫描清单全路径）→ `ProductionBindingColumn`（harness java 列：`XLang.parseXpl` 模型加载 → 绑定 hook → bound 执行体 → `XLang.execute` choke point 直通，scope/输出缓冲经 `EvalRuntime` 传入——$out 双参单元同通路；**非 `JavaBackendColumn` 直驱、非 nop-javac 内存编译、无自定义 ClassLoader**，D6 四约束成立）→ I1 harness 断言工具承载三层断言（返回值 `CompareValues.typedEquals` / scope 副作用 `SideEffectSnapshot` / 输出缓冲 `RecordingEvalOutput` 完整调用序列 / 异常语义（错误码 + SourceLocation 回映射，含 exception-prop/exception-convert/exception-throw/exception-fn-throw/exception-method 五个异常单元））+ 期望断言（corpus 声明预期重映射）+ 身份断言（`JavaBackendIdentityRule`：executedArtifact = 确定性派生夹具类实例 + executorArtifact = static 入口 Method 首参 IEvalScope；另有加载后模型执行体 = `EvalStaticBoundExecutable` + artifact = 夹具类入口 Method 逐单元断言）。48/48 PASS（解释器列对照列全一致）。
- **指纹失配/清单缺失降级断言（验收第二项，红/绿对照）**：红 = `testFingerprintMismatchInjectsInterpreterWithGradedObservation`（合成清单指纹与树不符 → 加载期 `generated-fingerprint-mismatch` WARN+counter delta=1 → `EvalStaticDegradedExecutable` → invoke INTERPRETER + 结果 = 解释器基线 typedEquals + 裁决环 INTERPRETER/degraded/artifact=原树）+ `testMissingGeneratedClassInjectsInterpreterWithObservation`（合法指纹指向 classpath 无类 → `generated-binding-missing` 同链）；绿 = `testGreenLightHitBindingHasZeroDegradation`（命中绑定 → 三 reason counter 全零增量 + bound 执行）。
- **清单外动态路径零降级断言（验收第三项）**：`testOutOfListResourceRoutesDynamicPathWithZeroDegradation`——清单外路径经真实出口（`compileSimpleExpr`）→ invoke → 动态路径裁决（本模块无 truffle = `dynamic-backend-not-registered` 静默 INTERPRETER，`isDegraded()==false`）+ `generated-binding-missing`/`config-disabled` counter 零增量。
- **夹具反漂移（D6 机制护栏）**：`TestGeneratedFixtureSources` 50 用例——49 夹具逐单元"夹具源码 == 转译器当前输出"（逐串相等）+ 夹具计数守卫（无陈旧夹具残留）；再生成载体 `GeneratedFixtureMain`（手动运行）。
- **docs-for-ai 同步**：`docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` §执行后端选择与降级观测——新增"生成类加载与模型加载期绑定（java 后端）"小节（绑定决策树/指纹口径/直通语义/内存契约与供给缝/缺省空态）+ 降级观测 reason 枚量增量（`generated-fingerprint-mismatch`/`tenant-divergent-tree`）与**分级语义**（stale 缺陷族每次 WARN vs 租户稳态 once-per-path 去重、指标不衰减）；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- **I11 移交显式记录（责任链 repo-observable）**：(1) **双清单文件格式需求**：`GeneratedClassManifest`（resourcePath → className(FQN) + treeFingerprint(64-hex SHA-256)）内存契约即清单文件的序列化界面——I11 定稿文件格式/存放路径/多 jar 聚合，反序列化填充本契约经 `setGeneratedClassManifest` 接入；扫描清单 = resourcePath should-set（`setStaticScanList`）；xlib 每标签条目键 = `resourcePath + '#' + tagName`（D4）。(2) **生产任务接线缝**：供给缝的文件填充方 = I11 构建任务（codegen/xgen 体系注册，经与运行时相同编译前端取树——I11 需以 `ExecutableTreeFingerprints.fingerprint(tree)` **同一实现**写入构建期指纹（D1 同算法约束））；`GeneratedFixtureMain` 为测试域再生成载体、非生产通道。(3) **`_gen/` 布局与同形路径唯一性校验归属重申**：`EvalMethodConvention` javadoc 移交项（生产布局/包名策略/同形路径折叠唯一性）保持 I11；测试域 `GENERATED_PACKAGE` 布局不外溢。(4) **漏跑可观测 `markUnavailable` 消费点重申**：`JavaEvalExecutionBackend.markUnavailable(reason)` = "java 后端启用但构建管线漏跑 → 不可用条目 + 全局 WARN"的生产消费点（I9 §9(d) 移交，I11 验收消费）；绑定助手 `bindLoadedUnit` 不可用分支已按 `unavailable` 观测 + 降级标记消费该状态（本 plan 落地）。(5) **xlib 约束重申**：xlib 绑定落地前 I11 不得将 xlib 路径纳入扫描清单（D4，防标签体出口编译误降级）。
- **三模块回归全绿 + 不削弱既有测试**：corpus 三列（java 49→对拍列含生产路径列新增、truffle 列）、矩阵（`TestExecTranslationCoverageMatrix` 等）、路由场景（I9 `TestJavaBackendRoutingScenarios` 合成域保持——决策树路由语义测试，与生产路径测试并存）既有套件零削弱——见 §7 计数（551/410/584 = I9 基线全保持 + I10 新增）。

## Execution Plan

### Phase 1 - 契约定稿（指纹落点/清单内存契约/绑定 hook 与 RCM 组合语义/观测分级/编译单元形态/测试域物化机制）

Status: completed
Targets: 本 plan Execution Notes 与当日 log（决策记录 repo-observable）

- Item Types: `Decision | Proof`

- [x] **前置硬门禁核验**：I9 已 `completed`（roadmap live 核验）；未完成 → 本 plan 置 `blocked` 回引擎并记当日 log，不绕行（Execution Notes §0：五通道 live 核验）
- [x] **D1 树指纹实现落点与纪律定稿**（Execution Notes §1：候选 (a) 落地 nop-xlang-java 新实现、共享纪律非共享实现、拒绝抽取共享三理由（模块边界/语义边界/I11 构建与运行时同一实现）；指纹强度 = hex SHA-256（持久化数据界面 vs long 概率性"必不同"显式认领）；防碰撞 = 全载荷 + 源位置 + 字段定界；白名单覆盖 = `javaTargetSet` 逐类 `MinimalNodeFactory` 真实指纹断言化 + 支持集内未白名单 fail-fast）
- [x] **D2 双清单内存契约与供给缝定稿**（Execution Notes §2：`GeneratedClassManifest`（resourcePath → className + treeFingerprint）内存契约 = I11 清单文件序列化界面；供给缝 = `setGeneratedClassManifest` 内部经 I9 `setBinder` 缝接生产 binder；缺省空态 = 行为与现状一致；非法清单数据 = 注入时 fail-fast；文件格式/存放路径显式排除归 I11）
- [x] **D3 模型加载期绑定 hook 与 RCM 缓存组合语义定稿**（Execution Notes §3：hook = `XLang.parseXpl` 单点（xpl/xgen/xrun register-model live 核验；xlib 不经此路径按 D4 枚举）；助手 = `EvalBackendRouter.bindLoadedUnit`；缓存复用 = 绑定后模型即 `ComponentCacheEntry.model` 等效语义（nop-core 零改动）；已绑定直通 = bound/degraded 两包装类 + choke point 两直通分支（无重复指纹/无虚假观测）；未标记树走 I9 决策树零改动；租户隔离 = per-tenant cache 各自绑定；I9 §9(f) 逐字对账与超越点在案）
- [x] **D4 绑定作用面与编译单元形态裁定**（Execution Notes §4：xpl 单根确定纳入（作用面 = 单元根、清单键 = resourcePath）；xlib = 每标签条目形态定稿（键 `path#tag`、I11 衔接）+ I10 落地范围 xpl + 三理由（xdsl-loader 路径/单根转译器/`_gen/` 布局归 I11）+ I11 约束记录（xlib 入清单前置条件）；E1-E6 逐出口复核 = 运行时字符串/节点 = 动态源不适用加载期绑定、误命中清单走 I9 执行期分支可观测降级、E4 模型加载形态 = 单元加载 ✓）
- [x] **D5 降级观测分级机制定稿**（Execution Notes §5：reason 增量 `generated-fingerprint-mismatch` + `tenant-divergent-tree`、`generated-binding-missing` 归位（条目缺失 + 类缺失/加载失败/约定违规合并族）；分级判定 = 绑定时 `ContextProvider.currentTenantId()`（机制依据 = `_gen/` 只从基树生成）+ 不可得保守归 stale；噪声策略 = 稳态 once-per-path 去重（≤1024 有界、超界回退每次 WARN、指标不衰减）+ stale 族每次 WARN；观测记录方 = 生产 binder 自记 + 双记不发生对账；租户测试构造五锚点机制在案（专用测试 modelType 规避 cache 创建时序耦合））
- [x] **D6 测试域生成类物化机制定稿**（Execution Notes §6：转译器产物 = corpus 全部 48 静态单元夹具源码（包 `io.nop.xlang.gen`、Maven test 常规编译）+ 反漂移断言 `TestGeneratedFixtureSources` + 再生成载体 `GeneratedFixtureMain` + 合成双清单经可注入供给 + `Class.forName` 常规加载；四约束满足证明 + 三备选排除理由在案）
- [x] 决策记录全部落 plan Execution Notes / 当日 log

Exit Criteria:

- [x] D1-D6 六项决策 repo-observable（plan Execution Notes §0-§6 + 当日 log），每项含候选、裁定、live 锚点、I9 §9 移交契约对账（§0 五通道核验 + §3 I9 §9(f) 逐字对账 + §5 观测记录方对账）
- [x] 指纹纪律（白名单覆盖面/防碰撞/fail-fast）与绑定组合语义（缓存复用/直通/租户隔离）以可测试断言形态写明（§1 覆盖断言 = `TestExecutableTreeFingerprints` 逐类；§3 直通 = 计数断言；§3 租户隔离 = 五锚点真实构造；Phase 2 消费）
- [x] No owner-doc update required（本 Phase 无文档变更；观测分级 docs 同步为 Phase 3 执行项）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 生产 binder + 加载期绑定 + 观测分级落地

Status: completed
Targets: `nop-kernel/nop-xlang-java/src/main/`（binder/指纹/清单契约）、`nop-kernel/nop-xlang/src/main/`（加载期绑定接入 + 如裁定的 router 静态分支/reason 增量）、两模块 test

- Item Types: `Proof`

- [x] nop-xlang-java：生产 binder（D2 内存契约消费 + 生成类 classpath 加载（`GeneratedEvalBinding.findEntryMethod` 先例）+ D1 指纹校验 + `IEvalStaticBinding` 供给（含身份证据））+ java 侧树指纹实现 + 清单供给缝接线（缺省空态 = 行为与现状一致）（Execution Notes §7）
- [x] nop-xlang：模型加载期绑定接入（D3 裁定落点与直通语义；设计 java §五伪代码全分支：绑定命中/缺失降级/失配分级降级/清单外动态路径不记降级）（Execution Notes §7）
- [x] 观测分级落地（D5：reason 增量 + WARN/指标；分级判定接线）（Execution Notes §7）
- [x] 单测（显式覆盖清单，guide 规则 25）：binder 正/负（清单命中加载并执行 / 清单内类缺失 → 分级降级观测 / 指纹失配 → 分级降级观测 / 清单外 → 不咨询 binder 且零降级观测）；指纹（同构树同指纹 / 任一差异含源位置不同指纹 / 支持集内未白名单节点 fail-fast / 白名单覆盖面 = 支持集断言）；绑定 hook 接线（模型经 RCM 加载后执行体身份 = 生成类绑定 artifact，非解释器树）；已绑定直通（无每次执行指纹重算、无虚假 WARN——计数断言）；租户隔离（按 D5 裁定机制真实构造同路径两租户不同树：租户 A 绑定命中 / 租户 B 稳态降级，互不串用）；**D4 裁定落地的消费面（xlib 单元形态/清单键形态）有对应测试（决策记录附测试清单增量）**（Execution Notes §7：测试清单——xpl 落地消费面 7+6+3 用例 + nop-xlang hook 8 用例；xlib 形态为 I11 衔接契约不落地无消费面测试，D4 记录在案）
- [x] 新代码无静默跳过审查：所有降级路径有观测（WARN/指标断言）；非法清单数据（指纹字段缺失/类名非法）fail-fast 或分级降级——裁定记录在案；binder 返回 null 仅限"应有而缺失"契约语义（Execution Notes §2/§5 裁定 + §7 断言在案）
- [x] 缺省空态回归：未接供给缝时三模块既有测试全绿（行为与现状一致——I9 护栏语义保持）（§7 验证：551/410/584 全绿 = I9 基线 543/271/584 全保持 + 新增）

Exit Criteria:

- [x] 生产 binder/指纹/绑定接入代码在仓且上述单测清单逐项在案（新增功能测试覆盖显式列出）（§7 落地记录 + 测试清单）
- [x] 设计 java §五伪代码全分支有测试对应（绑定命中/缺失/失配分级/清单外/未启用开关）（`TestEvalBackendLoadTimeBinding` 8 用例逐分支 + `TestModelLoadingBinding`/`TestGeneratedClassBindingBinder` 生产侧）
- [x] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` 全绿（truffle 模块预期零变更）；`./mvnw checkstyle:check -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -Pqa` 退出码 0（§7：551/0/2 + 410/0/0 + 584/0/0 + checkstyle EXIT=0）
- [x] 依赖方向断言保持全绿（nop-xlang main 无后端模块/ GraalVM import——如触碰 nop-xlang，结构性保证不回退）（`TestEvalBackendDependencyDirection` 在 551 内全绿；包装类/router 增量均在 `io.nop.xlang.backend` SPI 域内）
- [x] No owner-doc update required（观测分级 docs 同步为 Phase 3 执行项，Phase 边界在案）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 绑定路由对拍验收（经 I1 框架断言工具）+ docs 同步

Status: completed
Targets: 绑定路由对拍测试（落点 nop-xlang-java test）、`docs-for-ai/02-core-guides/xlang-and-xpl-basics.md`

- Item Types: `Proof`

- [x] **绑定路由对拍（roadmap 验收第一项）**：既有 corpus 静态单元经**生产绑定路径**执行（D6 裁定载体：转译器 API 合成双清单产物 + 测试夹具物化生成类 → 可注入供给 → 模型加载 → 绑定 → invoke；非 `JavaBackendColumn` 直驱、非 nop-javac 内存编译通路），java 列 vs 解释器列三层断言一致（返回值 typedEquals / scope 副作用 / 输出缓冲 / **异常语义**（错误码 + SourceLocation 回映射）——执行 01 §五三层口径全枚举）+ 身份断言（执行体 = 生成类绑定 artifact——载体为加载后模型执行体/裁决环 artifact；I1 harness 断言工具 `CompareValues`/`SideEffectSnapshot`/`RecordingEvalOutput` 承载三层断言——显式引用关系可验证）（Execution Notes §8：48/48 单元 PASS，`TestProductionBindingRoutingScenarios` 6 用例 + `TestGeneratedFixtureSources` 50 反漂移护栏）
- [x] **指纹失配/清单缺失降级断言（验收第二项）**：红/绿对照——指纹失配注入（合成清单指纹与树不符）→ INTERPRETER 执行 + 分级观测事件断言（WARN 消息键 + counter delta）；清单内类缺失注入 → 同；绿灯对照 = 命中绑定零降级观测（Execution Notes §8：红两形态 + 绿 counter 全零增量）
- [x] **清单外动态路径零降级断言（验收第三项）**：清单外资源经真实出口 → 动态路径裁决 + 零降级观测增量（counter 不动 + 无 WARN）（Execution Notes §8）
- [x] docs-for-ai：`xlang-and-xpl-basics.md` §执行后端选择与降级观测 的 reason 枚量增量与分级语义（stale vs 租户稳态）同步；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（Execution Notes §8：新增"生成类加载与模型加载期绑定"小节 + reason 两枚 + 分级语义（stale 每次 WARN / 稳态去重、指标不衰减）；link checker 0 errors）
- [x] **I11 移交显式记录**：双清单**文件**格式需求（内存契约的序列化界面）、生产任务接线缝（供给缝的文件填充方）、`_gen/` 布局与同形路径唯一性校验归属重申（`EvalMethodConvention` javadoc 移交项）、漏跑可观测 `markUnavailable` 消费点重申（责任链 repo-observable，落 plan Execution Notes + 当日 log）（Execution Notes §8 移交五项）
- [x] 三模块回归全绿 + 不削弱既有测试（corpus 三列/矩阵/路由场景既有套件零削弱）（Execution Notes §7/§8：551/410/584 = I9 基线 543/271/584 全保持 + I10 新增 8/139/0）

Exit Criteria:

- [x] **绑定路由对拍全绿（三层断言 + 身份断言 + 经生产绑定路径）——roadmap I10 验收第一项**（48 corpus 静态单元 48/48 PASS）
- [x] **指纹失配/清单缺失降级 + 分级观测断言在案（红/绿对照）——验收第二项**
- [x] **清单外动态路径零降级观测断言在案——验收第三项**
- [x] **端到端验证**：RCM/模型加载入口 → 绑定 → invoke → 生成类执行 → 三层断言全链可运行（从用户入口点到最终输出的完整路径）（对拍矩阵 = parseXpl 加载入口全链；RCM 入口 = `TestTenantIsolationBinding`/`TestModelLoadingBinding` 经 `ResourceComponentManager.loadComponentModel`/parseXpl 双证据）
- [x] **接线验证**：绑定 hook 真实被模型加载路径调用（非仅 binder 可查——加载后模型执行体身份断言即接线证据）；生产 binder 真实被 `setBinder` 接入并消费（供给缝注入后路由矩阵复验）（`setGeneratedClassManifest` 内部经 setBinder 缝 + 48 单元矩阵 + identity rule）
- [x] **无静默跳过**：全部降级/失败路径显式观测或 fail-fast（Phase 2 审查项复验）（红/绿对照 + 非法清单 fail-fast + counter/WARN 断言在案）
- [x] 受影响 owner docs 已同步（观测分级命名契约）；`ai-dev/logs/` 对应日期条目已更新
- [x] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` 全绿（收口复验见 Closure Gates 勾选记录）

## Closure Gates

- [x] 绑定路由对拍全绿（静态单元经绑定后 java 列 vs 解释器列一致 + 身份断言；经 I1 框架断言工具——显式引用关系可验证）——roadmap I10 验收第一项（`TestProductionBindingRoutingScenarios.testAllCorpusStaticUnitsBindAndMatchInterpreterColumn`：corpus 全部 48 静态单元 48/48 PASS，ProductionBindingColumn 经 parseXpl+choke point 生产路径；harness = ExecCompareHarness + CompareValues/RecordingEvalOutput/SideEffectSnapshot + JavaBackendIdentityRule 显式引用）
- [x] 指纹失配/清单缺失降级解释器 + 观测事件断言（stale 缺陷 vs 租户稳态分级）在案——验收第二项（红/绿对照 3 用例 + 租户稳态真实构造 `TestTenantIsolationBinding` 3 用例）
- [x] 清单外资源走动态路径不记降级事件断言在案——验收第三项（`testOutOfListResourceRoutesDynamicPathWithZeroDegradation` + `testOutOfListResourceDoesNotConsultBinder` + `testOutOfListUnitNotWrappedAndNoObservation`）
- [x] 树指纹施加对象 = Executable 树（源资源指纹拒绝项保持）；构建期与运行时同算法（D1 裁定成立）（`ExecutableTreeFingerprints` 施加于树载荷+源位置；I11 构建任务同实现约束落 Execution Notes §8(2)）
- [x] RCM 职责边界不变（nop-core 零后端感知变更）；绑定结果随 `ComponentCacheEntry` 缓存语义成立（D3 裁定 + 测试证据）（nop-core 零 diff——绑定后模型即缓存条目 model 等效语义；`TestTenantIsolationBinding` RCM per-tenant cache + assertSame 复用证据）
- [x] 模块依赖方向保持（nop-xlang main 不引后端类型/GraalVM——结构性断言全绿；nop-xlang-java 不依赖 nop-xlang-truffle）（`TestEvalBackendDependencyDirection` 全绿 + pom 零新增依赖）
- [x] I11 移交显式记录 repo-observable（双清单文件格式需求/任务接线缝/唯一性校验/漏跑可观测消费点）（Execution Notes §8 五项）
- [x] 回归不允许削弱现解释器测试（纪律 3）；缺省空态 = 行为与现状一致（I9 基线 543/271/584 全保持于 551/466/584；CorpusCoverageB 增量 diff 纯新增（outputModeOf 助手，audit 复核））
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 区显式空；audit check 8 PASS）
- [x] 受影响 owner docs 已同步（观测分级 reason 契约）（`xlang-and-xpl-basics.md` 新增小节 + reason 枚量 + 分级语义；link checker 0 errors）
- [x] 独立子 agent closure-audit 已完成并记录证据（task `ses_fdec13e4effeOOx6yIsyfwyz0i`，fresh session，CAN CLOSE——0 Blocker/0 Major/4 Minor 均为收口时动作，见 Closure 段）
- [x] Anti-Hollow Check：closure audit 已验证（a）绑定 hook 被模型加载路径运行时真实调用（xpl.register-model.xml → XplModelLoader → XLang.parseXpl → bindLoadedUnit 链路逐环核验），（b）端到端 RCM→绑定→生成类执行连通（TestTenantIsolationBinding RCM 入口 + ProductionBindingColumn choke point 全链），（c）无空方法体/静默跳过/no-op 作为正常实现（新增主类逐读：catch 全部重抛/观测/裁定保守缺省）
- [x] `./mvnw compile -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am`（退出码 0）
- [x] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C`（**551/0/0(2 skipped) + 466/0/0 + 584/0/0 全绿**）
- [x] checkstyle / 代码规范检查通过（mission lint 口径）（`checkstyle:check -Pqa` 退出码 0；`scan-hollow-implementations.mjs --module nop-xlang / nop-xlang-java --severity high` 退出码 0）
- [x] doc link checker 退出码 0（docs-for-ai 变更后）（`node ai-dev/tools/check-doc-links.mjs --strict` 0 errors）

## Deferred But Adjudicated

（起草时无新 deferred 项。双清单文件产物/构建任务/native 兼容归 I11、基准归 I12 均为 roadmap 既定归属（非本 plan in-scope 缺陷），Non-Goals 显式排除。执行中产生时按 guide 补录并写明 Why Not Blocking Closure。）

## Non-Blocking Follow-ups

- Q1/Q4 watch-only 触发口径量化归 I12（延续 I5-I9 Follow-up）。

## Closure

Status Note: 三 Phase 全 completed（Phase 1 六决策 D1-D6 repo-observable；Phase 2 生产 binder + java 侧 SHA-256 树指纹 + 双清单内存契约与供给缝 + 加载期绑定 hook 与已绑定/降级直通 + 观测分级含租户稳态去重 + 租户隔离真实构造测试；Phase 3 corpus 全部 48 静态单元经生产绑定路径对拍全绿（三层断言 + 身份断言 + 非直驱/非 nop-javac/无自定义 ClassLoader）+ 降级红/绿 + 清单外零降级 + docs 同步 + I11 移交五项）。roadmap I10 三项验收逐项落地且测试固化；I9 基线全保持（缺省空态 = 行为与现状一致）；nop-core 零改动、依赖方向结构性保持。
Completed: 2026-08-21

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh 子 agent（未参与实现，task `ses_fdec13e4effeOOx6yIsyfwyz0i`）
- Audit Session: ses_fdec13e4effeOOx6yIsyfwyz0i
- Evidence:
  - 八维检查逐项 PASS：文本一致性（三 Phase 零未勾选）/ Phase 1 D1-D6 决策齐备（候选/裁定/live 锚点/I9 §9 对账）/ Phase 2 生产 binder-指纹-钩子-观测逐文件核验（含分级与去重路径 file:line）/ Phase 3 生产路径矩阵 48/48 + 红/绿 + 清单外 + 反漂移 50 + docs（audit 亲跑 link checker 0 errors）+ I11 移交五项 / 非弱化（I9 套件全绿 + CorpusCoverageB diff 纯增量）/ 依赖方向（零违规，仅 I9 字符串常量探测非类型依赖）/ deferred 诚实性（无 in-scope 缺陷降级）
  - Anti-Hollow 三问全 PASS：(a) hook 链路逐环核验 xpl.register-model.xml → XplModelLoader → XLang.parseXpl → bindLoadedUnit；(b) RCM 入口端到端连通（TestTenantIsolationBinding 经 loadComponentModel + ProductionBindingColumn choke point 全链）；(c) 新增主类逐读无空壳（catch 全部重抛/观测/保守缺省裁定）
  - 测试计数 live 核验（surefire 2026-08-21）：nop-xlang 551（含 TestEvalBackendLoadTimeBinding 8）/ nop-xlang-java 466 / nop-xlang-truffle 584，全 0 failures 0 errors；I9 既有套件全绿（路由场景 4/决策 17/注册 7/观测 3/依赖方向 2/corpus 列 33+19+22）
  - 结论 **CAN CLOSE**（0 Blocker / 0 Major / 4 Minor 均为收口时动作：roadmap 状态更新（本 Closure 同步完成）/ 提交归档（收口后 git 提交）/ fail-fast 红路径以覆盖参数化守护（D1 裁定形态，已记录）/ docs backend-unavailable 与代码一致无需动作）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（勾选后复验，见下方收口记录）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-xlang / nop-xlang-java --severity high` 退出码 0

Follow-up:

- Q1/Q4 触发口径量化归 I12（延续 I5-I9 Follow-up，roadmap 在案）
- no remaining plan-owned work（双清单文件产物/构建任务/`_gen/` 布局/native 兼容归 I11、基准归 I12 均为 roadmap 既定归属，移交记录见 Execution Notes §8）
