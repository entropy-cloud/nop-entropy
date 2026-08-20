# I9 统一后端选择机制（注册 SPI + 决策树 + 配置开关 + 降级观测）

> Plan Status: active
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

## Execution Plan

### Phase 1 - I8 供给核验、动态编译出口盘点、SPI/决策树/开关/观测契约设计定稿

Status: planned
Targets: 本 plan Execution Notes 与当日 log（出口清单与决策记录 repo-observable）

- Item Types: `Decision | Proof`

- [ ] **前置硬门禁核验**：I8 已 `completed`（roadmap live 核验）；未完成 → 本 plan 置 `blocked` 回引擎并记 log（Current Baseline 既定处置，不绕行）
- [ ] **动态编译出口 live 盘点**：扫描 nop-xlang 及调用方"运行时字符串→Executable 树"出口全集（候选锚点：`io.nop.xlang.api.XLang` 系 API、`SimpleExprParser`/`XLangExprParser` 直用点、`XplCompiler` 动态编译入口、规则/表达式配置产物入口），产出出口清单——每出口标注统一回调接入点或"不适用"裁定与理由（清单落 plan Execution Notes，repo-observable）；盘点口径 = live 扫描而非设计文档转述；**扫描边界定稿**（nop-kernel 全域 vs 全仓含业务模块——纳入/排除逐项记录理由，I11 扫描口径清点同款做法；"无遗漏出口"断言以该边界为可验证全集）
- [ ] **SPI 契约定稿**：条目字段（标识/能力集枚举/可用性状态/不可用原因/元信息）、注册/反注册/查询 API、失败语义（初始化失败→不可用条目不阻断启动）；**注册时机载体裁定**（后端模块初始化显式注册的触发机制：NopIoC bean 初始化回调 vs 静态初始化——依 NopIoC 显式主义与 beans.xml 注册约定裁定，落锚点）；依赖方向保持验证（nop-xlang 不依赖后端模块、契约对实现的感知止步于契约）
- [ ] **决策树定稿**：判定输入四项操作化——扫描清单成员资格接口形态（清单供给 = SPI 可查询接口；生产产物归 I11，测试合成清单）、后端可用性查询、部署形态判定钩子（native 下 truffle 不启用；**实现张力预注**：nop-xlang 无 GraalVM import——钩子走系统属性/配置式部署形态标记而非 `ImageInfo` 类探测，违反即被依赖方向断言拦截）、配置开关读取；运行时求值期统一裁决入口签名；第三分支接线契约（消费 I8 观测事件接口的回调形态与降级动作——消费方 = truffle 侧 SPI 适配器，I8 Phase 1 消费路径裁定对偶项）；单跳降级全分支枚举（java 侧两形态：开关关/不可用条目；指纹失配归 I10 不在本 plan）；静态路径 java 执行体在本 plan 的测试域载体裁定（合成清单 + 转译器 API 合成生成类，`JavaBackendColumn`/I2 nop-javac 先例）；**后端未注册回归护栏**：注册表空/后端不在 classpath 时所有出口行为与现状一致（既有测试全绿即为证据——缺省值选择不得引入隐性行为漂移，显式裁定记录）
- [ ] **配置开关定稿**：配置项清单 + 命名 + 缺省值（候选 `nop.xlang.execution.java-backend-enabled` / `nop.xlang.execution.truffle-backend-enabled` / `nop.xlang.execution.force-interpreter`；`XLangConfigs` 增量 varRef）；强制解释器诊断模式语义（全路由短路 INTERPRETER + 不记降级事件的边界裁定）；不设全局默认后端
- [ ] **降级观测命名契约定稿**：WARN 日志消息键与格式（日志断言载体一并核实——live 日志测试先例或可注入 Logger，与指标断言同标准）、指标命名（`GlobalMeterRegistry` live 命名先例核实后定）、注册表不可用条目查询 API；"出口不得自带后端 if/else"的断言机制定形（结构检查脚本/逐出口审查记录形态，二选一裁定）；**docs-for-ai 落点裁定**（候选 `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` 增节或新 owner doc；如新增/路由变化同步 `docs-for-ai/INDEX.md` 与 source-anchors）
- [ ] **路由场景矩阵用例设计**：五类场景（静态→java / 动态→truffle / java 降级→解释器 / truffle 降级→解释器 / 单元级降级判 FAIL）× I1 harness 执行载体定稿（场景用例经 `ExecCompareHarness` 参数化驱动或"决策树裁决 + harness 断言工具组合"载体二选一裁定——验收语义保持"经框架执行与断言"）；**模块可见性硬约束（裁定必答题）**：`JavaBackendColumn` 在 nop-xlang-java test、`TruffleBackendColumn` 在 nop-xlang-truffle test，仓内无任何模块 test scope 能同时看到两列，且 nop-xlang-java→nop-xlang-truffle 连 test 依赖也被设计 §二禁止——场景矩阵必然按后端模块拆分落点（每模块各测己方列 + 共享裁决入口经 nop-xlang test 或 test-jar 驱动），落点方案随本裁定定稿；每场景的身份断言载体（java=生成类实例 / truffle=翻译 AST CallTarget / 解释器=解释器树）与降级观测断言（日志/指标）落定
- [ ] 决策记录全部落 plan Execution Notes / 当日 log

Exit Criteria:

- [ ] 前置核验记录 + 出口清单（含逐出口接入点/不适用裁定）+ 五项契约定稿全部 repo-observable
- [ ] 路由场景矩阵用例设计与 harness 消费载体在决策记录中可审（Phase 2/3 消费；显式引用关系可验证——非独立重写比对）
- [ ] No owner-doc update required（本 Phase 无文档变更；命名契约文档落点为 Phase 3 执行项）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - SPI/注册表/决策树/开关/降级观测落地与出口接入

Status: planned
Targets: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/`（SPI/注册表/决策树/配置/观测）、`nop-kernel/nop-xlang-truffle/src/main/`、`nop-kernel/nop-xlang-java/src/main/`、动态编译出口接入点

- Item Types: `Proof`

- [ ] nop-xlang：SPI 契约 + 显式注册表（`@GlobalInstance` 模式）+ 统一决策树（静态路径判定接口/动态路径裁决/第三分支/单跳降级）+ 运行时求值期统一裁决入口 + 配置开关（`XLangConfigs` 增量）+ 降级观测（WARN 日志 + 指标 + 不可用条目查询）
- [ ] nop-xlang-truffle：动态翻译能力注册（能力=动态翻译；Engine/池初始化失败→不可用条目不阻断启动）；单元级翻译失败事件→第三分支接线（消费 I8 接口）；池运行时作为动态路径执行体接入裁决入口
- [ ] nop-xlang-java：静态生成物能力注册（条目 + 能力声明 + 扫描清单成员资格接口的 java 侧查询实现——绑定加载本体归 I10，接口先行）
- [ ] 动态编译出口统一回调接入：按 Phase 1 清单逐出口接入裁决入口；出口不得自带后端 if/else（逐出口审查记录）
- [ ] 单测：注册/反注册/查询/失败语义（初始化失败→不可用条目、不阻断启动）；决策树逐分支（静态命中/未命中、动态可用/降级、第三分支、单跳无跨跳、强制解释器短路）；配置开关矩阵（三开关 × 两后端可用性）；**后端未注册回归护栏显式用例**（注册表空/后端不在 classpath → 出口行为与现状一致，Phase 1 裁定的证据载体在此落地）；降级观测（WARN 日志断言 + 指标计数断言 + 不可用条目查询断言）；依赖方向断言（nop-xlang 无后端模块依赖/无 GraalVM import——不泄漏口径扩展到 SPI 层）；"出口无自带 if/else"断言按 Phase 1 定形机制执行（若为审查记录形态，记录附于 Execution Notes 出口清单，独立可审）
- [ ] 新代码无静默跳过：不可用后端请求按降级路径走且显式记观测事件；未知后端标识/非法契约调用 fail-fast

Exit Criteria:

- [ ] SPI/注册表/决策树/开关/观测代码在仓且每项有对应单测（guide 规则 25：新增功能显式列出测试覆盖）
- [ ] 三后端注册路径在仓（java/truffle 显式注册 + 初始化失败不可用条目语义有测试注入验证）
- [ ] 出口清单逐出口接入在仓或显式裁定不适用（无遗漏出口、无自带 if/else）
- [ ] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am` 全绿；依赖方向断言通过
- [ ] No owner-doc update required（命名契约文档同步为 Phase 3 执行项，Phase 边界在案）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 路由场景矩阵对拍验证（经 I1 框架）与 docs-for-ai 同步

Status: planned
Targets: 路由场景矩阵测试（落点按 Phase 1 裁定——按后端模块拆分：java 侧场景落 nop-xlang-java test、truffle 侧场景落 nop-xlang-truffle test、裁决入口/出口回调通用断言落 nop-xlang test 或经 test-jar 驱动）、`docs-for-ai/`（Phase 1 裁定落点）

- Item Types: `Proof`

- [ ] **路由场景矩阵落地（roadmap 验收）**：经 I1 对拍框架执行与断言——静态→java（合成清单 + 合成生成类；身份断言=生成类实例）/ 动态→truffle（身份断言=翻译 AST 经 CallTarget 执行；经池运行时驱动）/ java 降级→解释器（开关关 + 不可用条目两形态；身份断言=解释器树 + WARN/指标断言）/ truffle 降级→解释器（初始化失败注入；同上断言）/ **单元级降级判 FAIL**（truffle 列翻译失败注入 → 该列 FAIL 非 SKIP，对拍不变式"单元级降级判 FAIL 不判跳过"）——逐场景执行结果与解释器基线一致（三层断言）
- [ ] 降级观测命名契约 docs-for-ai 同步（Phase 1 裁定落点：日志/指标命名契约 + 注册表不可用条目查询用法；如新增文档或路由变化同步 `docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md`）+ doc link checker（`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0）
- [ ] 三模块回归全绿（`./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C`）；不泄漏断言复跑
- [ ] I10 移交显式记录：静态路径 java 绑定消费的接口契约（扫描清单成员资格接口 + 能力声明）+ 本 plan 测试域合成形态与 I10 生产落地的对账点（责任链 repo-observable，落 plan Execution Notes + 当日 log）

Exit Criteria:

- [ ] **路由场景矩阵全绿（五类场景 × 身份断言 + 结果一致性 + 降级观测断言；经 I1 框架执行与断言——显式引用关系可验证）——roadmap I9 验收项**
- [ ] **单元级降级判 FAIL 有注入验证**（红/绿对照：翻译失败注入 → FAIL；正常 → 全绿）
- [ ] **端到端验证**：动态编译出口（真实出口按 Phase 1 清单）→ 统一裁决 → 后端执行体（java 合成生成类 / truffle 池运行时）→ 三层对拍断言全链可运行
- [ ] **接线验证**：裁决入口真实被出口回调调用（非仅注册表可查——至少一处真实出口的端到端断言）；第三分支真实消费 I8 观测事件（注入翻译失败 → 事件被消费 + 降级发生 + 观测记录）
- [ ] docs-for-ai 命名契约同步在仓 + link checker 退出码 0
- [ ] 回归不削弱既有测试（corpus 三列/矩阵/对拍全绿保持）；三模块全绿
- [ ] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` 全绿
- [ ] 受影响 owner docs 已同步（降级观测命名契约；`ai-dev/logs/` 对应日期条目已更新）

## Closure Gates

- [ ] 路由场景矩阵全绿（静态→java / 动态→truffle / java 降级→解释器 / truffle 降级→解释器 / 单元级降级判 FAIL；逐场景身份断言 + 结果一致性；**经 I1 对拍框架执行与断言**）——roadmap I9 验收项
- [ ] 后端注册 SPI 落地（显式注册表/能力集/可用性/不可用原因；无 classpath 扫描；初始化失败不阻断启动有测试）
- [ ] 统一决策树全仓唯一（单跳降级、无跨跳、第三分支；决策输入四项操作化）且动态编译出口统一回调接入（清单化核验在案，无出口自带 if/else）
- [ ] 配置开关落地（java/truffle enable + 强制解释器；不设全局默认后端）
- [ ] 降级观测落地（WARN + 指标 + 不可用条目）且命名契约落 docs-for-ai
- [ ] 模块依赖方向保持（nop-xlang 不依赖后端模块、无 GraalVM 泄漏——断言在仓）
- [ ] I8 供给消费闭合（池运行时接入 + 观测事件第三分支接线）；I10 移交显式记录
- [ ] 回归不允许削弱现解释器测试（纪律 3）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：closure audit 已验证（a）裁决入口被真实出口调用（b）降级路径真实发生且被观测（c）无空方法体/静默跳过/no-op
- [ ] `./mvnw compile -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am`
- [ ] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C`
- [ ] checkstyle / 代码规范检查通过（mission lint 口径）
- [ ] doc link checker 退出码 0（docs-for-ai 变更后）

## Deferred But Adjudicated

（无——起草时无新 deferred 项。java 绑定加载/指纹失配降级归 I10、构建管线双清单归 I11、基准归 I12 均为 roadmap 既定归属（非本 plan in-scope 缺陷），Non-Goals 显式排除。执行中产生时按 guide 补录并写明 Why Not Blocking Closure。）

## Non-Blocking Follow-ups

- 设计文档旧 I 编号引用换算（W4-audit 移交清单 #1/#2）维持"下次设计文档修订"既定归属，非本 plan 范围。
- Q1/Q4 watch-only 触发口径量化归 I12（延续 I5-I8 Follow-up）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
