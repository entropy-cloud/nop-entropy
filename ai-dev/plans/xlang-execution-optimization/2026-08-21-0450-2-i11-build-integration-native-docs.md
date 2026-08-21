# I11 构建集成 + native image 兼容 + docs-for-ai 同步

> Plan Status: completed
> Last Reviewed: 2026-08-21
> Source: `ai-dev/backlog/xlang-execution-optimization-roadmap.md` I11（范围/验收 = 定稿条目）；设计冻结于 `ai-dev/design/xlang-java/01-architecture-baseline.md`（§六 `_gen/` 构建任务接入方式 = 本 plan 行为规格：任务输入/双清单分离产物/经相同前端取树/重生成幂等/不绕过 codegen 管线）与 `ai-dev/design/xlang-execution/01-architecture-baseline.md`（§三构建期扫描清单 = 静态性判定操作化 + "java 后端启用但扫描清单缺失 → 注册不可用条目 + 全局 WARN"）；I10 移交契约 = `ai-dev/plans/xlang-execution-optimization/2026-08-21-0450-1-i10-java-generated-class-loading.md` Phase 3 移交记录（执行时以 I10 实际落地为准复核）
> Mission: xlang-execution-optimization
> Work Item: I11
> Related: I10（前置：双清单内存契约 + 生产 binder + 加载期绑定缝——本 plan 为生产产物供给方，执行顺序在前 `{N}`=1）；I9（`markUnavailable` 漏跑可观测消费点 + 观测命名契约）；I12（后继：性能基准 + 全量三后端对拍收口）

<!-- Draft review: round-1（fresh session ses_fdf0b9c20ffelqaEFGdnypeD00，Verdict Not Ready：0 Blocker / 4 Major——M1 xlib 多根生产形态无裁定项且不在 I10 对账清单；M2 "漏跑可观测"与"缺省空态 = 行为与现状一致"护栏冲突（java-backend-enabled 缺省 true），判别子缺失；M3 e2e"常规编译"载体预 commit 测试域编译有自定义 ClassLoader 通路风险 + 无真实管线运行证据项；M4 native 验证无最低可执行下界（裁定与 Closure Gate 循环自证）→ 修复（对账扩六项 + Phase 1 增 xlib 生产形态裁定；判别子列 Phase 1 必答含张力显式化；e2e 默认载体改 fixture 模块全真链路 + 禁自定义 ClassLoader/内存编译 + 真实管线运行证据项；native 下界预置（closed-world 结构断言 + GraalvmConfigGenerator 可观察 + 优先真实 native-image 构建含仓内可用先例）；顺手消化 Minor：ServiceLoader/isEnabled 门控隐患预注、依赖方向标注为推导约束、postcompile 时序约束、I10 前瞻表述挂复核口径）→ round-2（fresh session ses_fdf03935fffeygR6ClDwnxzobu，Verdict **Ready**：0 Blocker/0 Major——锚点全部核验（pluginManagement/opt-in、CodeGenTask 目录驱动分派、native profile 先例、docs 缺省值 live 证实）；六项对账与 I10 D1/D4/移交一一对应；六范围要素与三验收 1:1 映射无 I12 泄漏；6 Minor 建议执行期吸收（漏跑措辞回指判别子/多 jar 聚合裁定输入/口径纳入标准加运行时消费维度/fixture 模块环约束预注/native 先例精度/命名约定记录）→ 当场吸收全部 6 项 → 共识达成 → active。 -->

## Purpose

落地 java 后端的构建期管线与部署形态配套：**codegen/xgen 任务注册**（扫描 `_vfs` 静态资源 → 经与运行时相同的编译前端取树 → 转译 → `_gen/` Java 源码 + 构建期扫描清单/生成类清单双清单分离产物；重生成幂等、不绕过 codegen 管线）+ **运行时供给闭环**（清单文件 → I10 供给缝 → 生产 binder/扫描清单填充；构建管线漏跑可观测）+ **native image 兼容**（`GraalvmConfigGenerator` 管线复用 + 生成类直编镜像验证 + truffle 模块镜像排除机制）+ **docs-for-ai 新模块开发指南同步**。验收 = roadmap I11 三项：端到端对拍（真实 `_vfs` 静态资源经任务扫描→转译→常规编译→加载绑定后 java 列 vs 解释器列全量一致 + 身份断言）、构建管线漏跑可观测断言（java 后端启用但扫描清单缺失 → 注册不可用条目 + 全局 WARN）、重生成幂等断言。

## Current Baseline

- **I10 前置硬门禁（执行顺序 `{N}`=1 在前）**：本 plan 执行前须核验 I10 已 `completed`（roadmap live）。正常路径消费：双清单**内存契约**（本 plan 的文件产物填充对象）、生产 binder + `setBinder`/`setStaticScanList` 供给缝（本 plan 的装载接线对象）、加载期绑定 hook（端到端链路的运行时半侧）、**I10 D1 指纹实现（构建任务写清单指纹必须与运行时校验复用同一实现）**、**I10 D4 编译单元形态裁定（xlib 多根消费面——本 plan 生产侧同形态落地）**、I10 Phase 3 移交记录（文件格式需求/任务接线缝/唯一性校验/漏跑可观测消费点）。**未完成时处置**：本 plan 置 `blocked` 回引擎并记当日 log——依赖硬门禁（roadmap 依赖 I10），不做绕行 fallback。
- **构建任务体系 live**：根 `pom.xml` 与 `nop-kernel/pom.xml` 经 exec-maven-plugin 运行 `io.nop.codegen.task.CodeGenTask`（参数 `${project.basedir}` + `postcompile`）——模块级 codegen 统一入口；`_gen/` 生成源码随模块常规编译的既有先例（如 `nop-datav/nop-datav-dao/src/main/java/.../entity/_gen`，`_` 前缀产物不可手改——仓库硬规则）；`GraalvmConfigGenerator`（nop-codegen `graalvm/`：`generateVfsIndex`/`generateGraalvmConfig`）经 `CodeGenAfterInitialization`/`CodeGenCoreInitializer`（`ICoreInitializer` + `CFG_CODEGEN_TRACE_ENABLED` 门控）驱动；`ResourceConstants.RESOURCE_VFS_INDEX = "classpath:nop-vfs-index.txt"`。
- **I9/I10 移交（live 锚点）**：`JavaEvalExecutionBackend.markUnavailable(reason)` = 漏跑可观测消费点（"java 后端启用但扫描清单缺失 → 不可用条目 + 全局 WARN"设计 execution §三）；`EvalMethodConvention` javadoc 移交——"生产 `_gen/` 产物布局与包名策略由 I11 定稿" + "同形路径（如 `a-b` 与 `a_b`）折叠同名，生产清单侧唯一性校验归 I11"；I9 出口清单扫描边界先例（定义域/消费域 + 逐项裁定表——本 plan 扫描口径清点同款做法）。
- **扫描口径现状**：设计 java §六定稿 `_vfs/**/*.xpl` / `*.xlib` 为确定成员；其余 `_vfs` XDSL 类型按 live 清点定稿（候选锚点：`XplTaskLoader`（nop-xlang `xpl/impl/`）证明 xtask 形态存在；xgen/xrun/嵌入 xpl 片段等待清点）；不引入 live 不存在类型（设计硬约束）。
- **转译器 live**：`ExecToJavaTranslator.translate(resourcePath, tree)`（单根 API，支持集 120）；测试域包名 `io.nop.xlang.gen`（生产布局归本 plan 定稿）。
- **docs 现状**：`docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` §执行后端选择与降级观测（I9 落；观测分级 reason 增量以 I10 实际落地为准复核）——**新模块开发指南缺失**（启用方式/构建任务/清单产物/降级诊断/truffle 镜像排除的使用契约）。
- **依赖方向约束（设计 execution 01 §二 + 推导）**：`nop-codegen` → `nop-xlang` 为既有 pom 依赖方向（live 核验）；设计 §二未直接提及 nop-codegen，"nop-codegen ↛ nop-xlang-java"为**推导约束**（后端模块进内核构建链 = 后端泄漏 + 反向必成 Maven 构建环）；truffle 依赖不泄漏纪律（镜像排除机制的动机之一）。
- 真正剩余的 gap：无构建任务（无扫描/无产物）；无双清单文件格式与生产填充；无漏跑可观测接线（`markUnavailable` 无生产调用方）；无 native image 兼容验证与 truffle 镜像排除机制；无新模块开发指南。

## Goals

- **任务注册与扫描口径定稿（roadmap 范围第一项）**：codegen/xgen 任务体系接入（`CodeGenTask postcompile` 入口形态）；扫描口径 = `_vfs/**/*.xpl`/`*.xlib` 确定 + 其余 `_vfs` XDSL 类型 live 清点逐项裁定纳入/排除及理由（不引入 live 不存在类型）。
- **双清单分离产物（范围第二项）**：构建期扫描清单（resourcePath should-set）+ 生成类清单（resourcePath → 类名 + 树指纹）为**分离的两份产物**（杜绝"以生成类命中反推静态性"——设计 execution §三）；格式与存放路径定稿（classpath 可发现、确定性可 diff、I10 内存契约可无损填充）；同形路径折叠唯一性校验落地（清单侧冲突 fail-fast）。
- **任务行为规格（范围第三项）**：经与运行时**相同的编译前端**取树（不复制前端逻辑）；重生成幂等；不绕过 codegen 管线（不自建独立构建通道、不在运行期补生成）。
- **运行时供给闭环 + 漏跑可观测（范围第四项，设计 §三；触发条件以 Phase 1 判别子裁定为准——区分"从未接入构建任务（合法空态，静默）"与"接入后管线漏跑（缺陷）"）**：清单文件 → I10 供给缝装载 → `setStaticScanList`/binder 生产填充；漏跑形态下 → `markUnavailable`（原因=构建管线漏跑）+ 全局 WARN，全部资源走动态路径/解释器。
- **native image 兼容（范围第五项）**：`GraalvmConfigGenerator` 管线复用；生成类作为普通类直编进镜像的验证；truffle 模块镜像排除机制（profile/classifier 形态裁定）。
- **docs-for-ai 同步（范围第六项）**：新模块开发指南（双后端使用契约：启用/构建/清单/降级诊断/排除机制）落点定稿并同步（如路由变化同步 INDEX/source-anchors）。
- **端到端对拍验收（roadmap 验收第一项）**：真实 `_vfs` 静态资源经任务扫描→转译→常规编译→加载绑定后 java 列 vs 解释器列全量一致 + 身份断言（经 I1 harness 断言工具）。

## Non-Goals

- 性能基准、全量三后端对拍收口、mission.json commands 汇总口径核验（I12）。
- 转译器/翻译器覆盖变更、决策树/binder/绑定 hook 语义变更（I4/I9/I10 已收口——本 plan 仅消费 I10 契约，如发现契约缺口回 I10 结论记录并按裁定处理，不就地改语义）。
- 存量全仓 `_vfs` 资源的全量生成产物落盘（首落范围 = 端到端验收所需资源集；全仓铺开与配套治理另行裁定，不构成本 plan 验收项）。
- corpus 扩充（对拍消费真实 `_vfs` 测试资源 + 既有 corpus 单元）。
- GraalVM native image 的构建基础设施新建（复用既有管线；不引入新的 native 构建框架）。

## Scope

### In Scope

- 构建任务（扫描 → 相同前端取树 → 转译 → `_gen/` 源码 + 双清单产物）+ 任务落点与注册（Phase 1 裁定）。
- 双清单文件格式/存放路径定稿 + 同形路径唯一性校验 + I10 供给缝装载闭环 + 漏跑可观测接线。
- native image 兼容：`GraalvmConfigGenerator` 复用接线 + 生成类直编镜像验证（Phase 1 裁定载体）+ truffle 镜像排除机制。
- docs-for-ai 新模块开发指南 + INDEX/source-anchors 同步（如路由变化）。
- 端到端对拍/幂等/漏跑可观测验收测试。

### Out Of Scope

- 同 Non-Goals。

## Execution Notes

### Phase 1 决策记录（2026-08-21）

**0. 前置硬门禁核验 + I10 移交六项对账**：I10 roadmap live 状态 = `done`（2026-08-21 closure audit CAN CLOSE，task `ses_fdec13e4effeOOx6yIsyfwyz0i`）。六项逐项对账（live 锚点核验）：

1. **双清单内存契约**：`io.nop.xlang.java.gen.GeneratedClassManifest`（resourcePath → className FQN + treeFingerprint 64-hex）在案，`of(...)` 注入时 fail-fast——本 plan 文件产物以其为序列化界面（S4）。✓ 无偏差。
2. **供给缝**：`JavaEvalExecutionBackend.setStaticScanList` + `setGeneratedClassManifest`（内部经 I9 `setBinder` 缝接生产 binder `GeneratedClassBindingBinder`）在案——本 plan 装载器经同一对缝填充（S6）。✓ 无偏差。
3. **唯一性校验**：`EvalMethodConvention.generatedClassName` javadoc 移交项——本 plan 落地清单侧 fail-fast + 聚合装载侧同类名异路径 fail-fast（S3/S4）。✓ 落地。
4. **漏跑可观测消费点**：`JavaEvalExecutionBackend.markUnavailable(reason)` 在案——本 plan 接线为生产调用方（S5）。✓ 落地。
5. **D1 指纹实现复用**：`ExecutableTreeFingerprints.fingerprint`（hex SHA-256，载荷白名单）在案——构建任务写清单指纹与运行时校验**同一实现**（同类同方法，无第二实现）。增量：根级 `ExecutableFunction` 分支（xlib 每标签树指纹的根形态——原实现仅覆盖 LiteralExecutable 载荷/BuildFuncRef/LazyCompiled 三种函数出现位；根分支为纯增量，既有调用方不传函数根，行为不变——fail-fast 纪律实证见 Phase 2 测试）。✓ 同算法约束成立。
6. **D4 编译单元形态（xlib 每标签条目）**：消费面键 = `resourcePath + '#' + tagName`、指纹 = 该标签编译树指纹、绑定作用面 = 每标签 executable——本 plan 生产侧按同形态落地（S7）；"xlib 绑定落地前不得将 xlib **裸路径**纳入扫描清单"约束按本 plan 落地化解：扫描清单 xlib 成员 = `path#tag` 键（非裸路径），裸路径恒不在清单 → 标签体经出口编译时按 I9 执行期分支判非成员走动态路径（无误降级）——约束的解除形态即其设计意图（I10 D4 三理由 (i)(ii)(iii) 全部为本 plan 定稿项，现已定稿）。✓ 形态一致。

**1. S1 扫描口径 live 清点定稿**（全仓 `_vfs` 文件类型清点：xml 1217 / yaml 887 / xmeta 481 / xbiz 426 / xjs 211 / xgen 178 / xdef 157 / xlib 80 / xlsx 66 / json 60 / xwf 54 / js 50 / beans 44 / **xpl 43** / xrun 33 / xtask 1（仅测试资源）/ 其它散类）：

| 类型 | 独立 Executable 编译单元？ | 运行时可绑定消费？ | 裁定 | 理由（live 锚点） |
|---|---|---|---|---|
| `.xpl` | 是（register-model `xpl` → `HtmlXplModelLoader` → `XLang.parseXpl` 单根） | 是（RCM/parseXpl 装载即 I10 绑定 hook 所在） | **纳入** | 设计 java §六确定成员 |
| `.xlib` | 是（`xdsl-loader` → `XplTagLib` 多根，每标签独立 Executable） | 是（标签体经 `XplLibTagCompiler.LazyCompiledFunction` 惰性编译后运行时执行） | **纳入（每标签条目，S7）** | 设计 java §六确定成员；I10 D4 消费面 |
| `.xgen` | 形式上是（register-model `xgen` → `HtmlXplModelLoader` → parseXpl） | **否**——live 消费方 = 构建期 `XCodeGenerator` 模板求值（178 个全在 codegen 模板树内） | **排除** | 纳入标准第二维（运行时可绑定消费）不满足；仅按编译单元标准会误纳入（plan 预注场景） |
| `.xrun` | 形式上是（`NoneXplModelLoader` → parseXpl） | **否**——live 消费方 = 构建期 codegen `@init.xrun` 初始化脚本 | **排除** | 同上（33 个全在模板树内） |
| `.xtask` | 是（`xtask.register-model.xml` → `XplTaskLoader` → parseXpl + 装载即执行） | 是（装载即 invoke，绑定对 parse 生效） | **排除（首落）** | live 全仓仅 1 个且在测试资源（`nop-xlang/src/test/resources/_vfs/test/tasks/a.xtask`）——无生产形态人口；"不引入 live 不存在（生产）类型"保守面；后续出现生产人口时按同机制直接纳入（装载路径同为 parseXpl，无机制缺口） |
| 其余 `_vfs` XDSL（xml/yaml/xmeta/xbiz/xjs/xdef/xwf/beans/...） | 否（各自模型类型；内嵌表达式片段经 E1-E6 动态出口编译，非编译单元） | 否（动态出口产物 = 运行时源，I10 D4 E1-E6 逐出口复核在案） | **排除** | 非独立编译单元；嵌入 xpl 片段归宿主模型编译 |
| xpl 片段（`impl_*.xpl`，live 43 个 xpl 中约 30+ 为 xlib `<source>` 内 `<c:include>` 引入的片段，如 `nop-wf-web/dingflow-gen.xlib`） | 否（经宿主标签编译，include 解析期内联） | 否（独立装载无人消费） | **排除（机制注记）** | 任务扫描按"RCM 可装载独立单元"口径；片段独立扫描不破坏正确性（allowUnregisteredScopeVar 下可编译、生成物无消费者），但为无效膨胀——全仓铺开的片段识别机制归 I12+ 治理（rollout 输入记录）；首落范围 = fixture 资源集（无片段）不受影响 |

不引入 live 不存在类型 ✓（xtask 排除裁定即此约束的保守执行）。清点口径表 repo-observable（本表 + 当日 log）。

**2. S2 任务落点与入口形态裁定**：**裁定 = 任务类落 nop-xlang-java main**（`io.nop.xlang.java.gen.task.XlangJavaGenTask`，独立 `main(String[] args)` 入口，args[0] = `${project.basedir}`——与 `CodeGenTask` 同形）。接入形态 = 模块在自身 pom 显式声明 exec-maven-plugin 增量 execution（id `xlang-java-gen`，phase `generate-test-resources`，mainClass 指向本任务；nop-datav-meta/nop-ai-codegen 显式声明先例的增量形态）。**live 事实对账**：`CodeGenTask.main` 分派 = 目录驱动（precompile/postcompile 模板目录）+ 特例分派（"aop"）——无服务式任务分派；本任务不复制 XCodeGenerator 模板机制（非模板生成），以独立 mainClass 接入 = live 已支持的插件配置面（`<mainClass>` 本就是 exec-maven-plugin 配置项），nop-codegen **零改动、零新依赖边**（nop-codegen ↛ nop-xlang-java 保持；反向也不新增——任务只用 nop-xlang-java 既有依赖域 nop-xlang→nop-core）。**ServiceLoader/ICoreInitializer 隐患处置**：任务不注册为 initializer（独立入口形态，plan 预注两候选中的后者）——生产运行期 `CoreInitialization` 无路径触发生成 ✓；任务自身运行时设置 `nop.xlang.execution.java-backend-enabled=false`（同 `CodeGenTask` 设 `CFG_CONFIG_SERVICE_ENABLED=false` 先例）——防任务 JVM 内装载到已提交清单而触发绑定缝（任务取树须干净树，见 S8 时序）。**拒绝项**：(b) 显式依赖边增补（nop-codegen→nop-xlang-java）= Maven 构建环（nop-xlang-java 构建需 exec 插件携 nop-codegen）+ 后端模块泄漏进内核构建链，拒绝（plan 预判一致）。

**3. S3 `_gen/` 产物布局与包名策略定稿**：**裁定 = 生成源码落 `<srcRoot>/io/nop/xlang/gen/Gen_<derived>.java`（包 = `EvalMethodConvention.GENERATED_PACKAGE` = `io.nop.xlang.gen`，与测试域同包），首落 fixture 落 `src/main/java`（nop-datav-dao `_gen` 随 src/main/java 落盘提交先例）**。可编译性依据：包名与转译器 `GeneratedJavaSource.className`（GENERATED_PACKAGE + 类名）逐字一致——无需转译器包名扩展（扩展面最小化）；文件头 `// source: <path>` + `// GENERATED BY XlangJavaGenTask — DO NOT EDIT`（生成标记 + 再生成幂等机械执行不可手改纪律——手改必被下轮任务覆盖）。同形路径折叠唯一性校验（移交项落地）：**任务侧**——生成前对全部（含 xlib 每标签）键 → 派生类名做折叠检测，冲突即 fail-fast（NopException 携带两键）；**聚合装载侧**——不同 resourcePath 键映射同一 className 时 fail-fast（跨模块折叠，S4 装载器校验）。**构建时序约束显式记录**：exec-maven-plugin `xlang-java-gen` execution 绑定 generate-test-resources（= postcompile 同相位于 compile 之后）——当轮新生成源不经本轮 main compile；`_gen/` 落 src/main/java + 落盘提交（下轮编译）与该时序自洽（live postcompile 先例同时序）；由此产生的"源已改、产物未重生成"窗口 = 指纹失配降级观测的**设计内行为**（stale 检测即验收第三项的日常形态），处置 = 重跑任务并提交（`_` 前缀产物纪律的 Gen_ 命名形态，再生成幂等保证无冲突合并）。

**4. S4 双清单文件格式与存放路径定稿**：**裁定 = 文本行协议，分离两份产物**：
- 扫描清单（`META-INF/nop-xlang/` 目录下文件名 `xlang-java-static-scan.txt`）：每行一个清单键（xpl = resourcePath；xlib = `path#tag`），排序稳定（字典序），UTF-8，结尾换行。
- 生成类清单（同目录下文件名 `xlang-java-generated-classes.txt`）：每行 TAB 三列 `<键>\t<className FQN>\t<treeFingerprint 64-hex>`，按键排序。
- 存放 = 产出模块 `src/main/resources/` 下上述路径（classpath 可发现；`src/main/resources` 提交先例）。确定性/可 diff = 排序 + 定界符 + 无时间戳（重生成幂等逐字节等价的载体）；I10 内存契约无损往返 = 列 → `GeneratedClassManifest.of`（既有注入校验复用）+ 行 → `setStaticScanList`。字段约束（无 TAB、类名合法、64-hex）由装载器 fail-fast。
- **多 jar 聚合语义裁定 = `ClassLoader.getResources` 级聚合内建**：装载器枚举 classpath 全部同路径清单文件，按键合并——同键同条目 = 去重；同键异条目（类名/指纹分歧）= fail-fast（NopException 携带两来源）；聚合后同类名异键 = fail-fast（S3 折叠校验的装载侧半边）。全仓铺开（多模块各自产清单）虽为 Non-Goal，装载器契约已内建聚合，无单文件假定。**拒绝项**：JSON/YAML（nop-xlang-java main 无既有 JSON 解析依赖面（JsonTool 在 nop-core 可达但行协议更轻）、diff 噪声更大、手工排查成本高——文本行协议 grep/diff 友好为硬要求最优解）。
- 装载器落点：`io.nop.xlang.java.gen.GeneratedManifestFiles`（nop-xlang-java main）。

**5. S5 漏跑可观测接线形态定稿（含判别子必答）**：**判别子裁定 = "清单在场性 + 显式启用声明组合"（plan 预注候选三）**：新配置 `nop.xlang.execution.java-backend.require-manifest`（缺省 **false**，`XLangConfigs` 增量 varRef）。张力化解（plan 必答项）：classpath 信号无法区分"从未接入"与"接入后漏跑"（任务产物两种形态下同样缺席——标记资源与清单同为任务产物），故区分只能来自**部署方显式声明**：接入方（部署/应用）设 require-manifest=true 声明"本部署应有产物"；false（缺省）= 全部存量部署与既有测试的合法空态（静默，I9/I10 护栏语义保持——缺省行为与现状一致）。**触发条件**：`XLangJavaBackendInitializer.initialize()`（生产注册时机）内装载后检查——require=true 且 classpath 无任何生成类清单文件 → `JavaEvalExecutionBackend.markUnavailable("codegen-pipeline-missed")` + 全局 WARN（`EvalBackendObservation` 命名契约：消息键 `nop.xlang.execution.backend-degraded`、counter `nop.xlang.execution.backend-degradation`，新 reason 枚量 `codegen-pipeline-missed`——I9 命名契约一致）+ 扫描清单为空 → 全部资源走动态路径/解释器（无 per-load 噪声：清单空 = 清单外 = 动态路径不记降级，与 I10 语义一致）。**检测时机** = 初始化装载时（plan 预注两候选之一；后端启用态探测不可行——无清单时无探测点）。红/绿载体 = Phase 2 测试（红：require=true + 空 classpath 装载 → 不可用条目 + WARN + 计数；绿：清单在场 → 正常供给零观测）。

**6. S6 运行时供给闭环接线定稿**：`XLangJavaBackendInitializer.initialize()` = register + `GeneratedManifestFiles.installSupplies(getClass().getClassLoader())`（装载 → 双缝填充；destroy() 反注册 + 清缝，测试隔离语义保持）。缺省（classpath 无清单）= 空态：双缝不设值，行为与现状一致（I9/I10 护栏）。装载恒执行（getResources 探测为常量成本）——清单在场即激活（模块接入即生效，无需额外开关；require-manifest 只升级"缺席"的语义）。

**7. S7 xlib 多根生产形态裁定**：**裁定 = 每标签一单元类 + 清单键 `resourcePath + '#' + tagName`（I10 D4 消费面一致）**。生产生成形态：每标签一个生成类（类名 = `generatedClassName(path + "#" + tagName)`，`#` 折叠为 `_`），入口 = `public static Object execute(IEvalScope $scope, Object[] $args)`（EvalMethod 约定族的新入口变体：首参 `$scope` 保持、第二参为标签实参组——`EvalMethodConvention.ARGS_PARAM` 常量定稿），方法体 = 委托转译器发射的 `$fn` 私有方法（`emitFunctionMethod` 既有机制：slot 绑定 + 缺省实参 + 函数体——I4 函数载荷下降同款）。**转译器扩展**：`ExecToJavaTranslator.translateTagUnit(String entryKey, ExecutableFunction fn)`（纯增量 API——既有 `translate` 与全部既有测试零改动；GenContext 增量 entry-args 发射位）。**指纹扩展**：`ExecutableTreeFingerprints` 增根级 `ExecutableFunction` 分支（mixExecutableFunction 全载荷：funcName/argCount/demandArgCount/slotNames/defaults/body——见 §0 第 5 项对账）。**运行时绑定缝（nop-xlang 增量，无 SPI 变更）**：`EvalBackendRouter.bindTagFunction(String bindingKey, ExecutableFunction fn)`——逐分支与 `bindLoadedUnit` 同构（注册表空/force/开关关/清单外 → 返回原 body 静默；不可用 → 观测 + degraded 包装；binder null → degraded 包装不补记；命中 → `EvalStaticBoundExecutable` 包装）；接入点 = `XplLibTagCompiler.LazyCompiledFunction.compile()` 内 `compiledFn = compileTool.compileFunction(ast)` 后对 `ExecutableFunction.setBody(...)` 换绑（**不替换 IEvalFunction 实例**——live 事实：`LazyCompiledExecutableFunction.getCompiled()` 硬转型 `ExecutableFunction`、`CompiledTag.functionModel.setInvoker` 共享该实例，替换即断——绑定作用面 = 每标签 body executable，与 I10 D4"绑定作用面 = 每标签 executable"一致）。**输出模式变体键**：默认缓存（`buildCompiledTag(null)`，标签声明输出模式）键 = `path#tag`；node 强制缓存（`buildCompiledTag(node)`）键 = 标签声明模式为 node 时 `path#tag`（同树形态）否则 `path#tag@node`（变体命名空间）——首落 `@node` 键不入扫描清单（xml/html 标签在 node 上下文的强制变体保持解释器，记录为 rollout 输入；fixture 标签用 none/text 模式绕开变体面）。**标签纳入过滤**：`isMacro()` 排除（宏标签在调用方编译期执行——`parseTag` 内 `runMacroExpression`，live 锚点）；编译产物非 `ExecutableFunction`（无 source 体 → Noop 族）排除（记录于任务产物日志）。语义保持对账（Non-Goals 边界）：决策树四输入/单跳降级/观测命名契约/I10 xpl hook 零改动；新缝为纯增量（默认空态行为不变），非"语义变更"。

**8. S8 任务行为规格落地形态**：扫描（S1 口径）→ 取树：xpl 族经 `XplModelParser` 干净编译（I10 D6 `parseClean` 同款——与 `XLang.parseXpl` 同装载语义同一编译前端，不经绑定 hook + 任务 JVM java-backend 禁用双保险）；xlib 经 RCM `loadComponentModel` → `XplTag` → `getFunctionModel()`（惰性编译真实触发——与运行时同一编译机制，非前端复制）→ `translateTagUnit`。**指纹-转译次序硬规则**：先 `ExecutableTreeFingerprints.fingerprint(tree)` 后 `translate`（live 事实：转译器对 `LazyCompiledExecutableFunction` force-compile 会改变节点载荷（null→compiled），运行时绑定发生在加载期（惰性节点未编译，指纹按 null 载荷混合）——先指纹后转译保证构建期/运行时指纹同口径；该次序在任务内固化并测试锁定）。产物写入：`_gen/` 源码 + 双清单（S3/S4）+ native reflect 配置增量（S9）。**重生成幂等（等价口径 = 逐字节）**：任务对全部产物 write-if-changed（内容比对后写）+ 排序稳定 + 无时间戳；`check` 模式 = 只比对不写盘、漂移即非零退出（CI stale 哨兵 + 幂等断言载体）。**不绕过 codegen 管线**：任务经 exec-maven-plugin 进入模块构建生命周期（不另建构建通道）；不在运行期补生成（任务唯一入口 = 构建期 main）。**原子性**：先全量转译 + 校验（含唯一性），后写盘——转译失败不产出任何半成品产物（fail-fast 于写盘前）。

**9. S9 native 兼容载体裁定**：**(i) closed-world 结构断言（下界一，测试落地）**：生成源码无 `defineClass`/`URLClassLoader`/`javax.tools`/`janino` 引用 + 生成类经应用类加载器加载（`getClassLoader()` 同一性断言，无自定义 ClassLoader）+ 装载/绑定路径唯一类加载原语 = `Class.forName`（I10 生产 binder 既有，结构性扫描测试锁定）。**(ii) `GraalvmConfigGenerator` 复用接线在任务产物侧可观察（下界二，证据落地）**：任务运行于 exec-maven-plugin JVM 时 nop-codegen 经根 pom 插件级依赖在 classpath（live 事实：根 pom exec pluginManagement `<dependencies>` 声明 nop-codegen）——`-Dnop.codegen.trace-enabled=true` 运行任务时 `CodeGenAfterInitialization`/`CodeGenCoreInitializer`（ServiceLoader 发现 + `CFG_CODEGEN_TRACE_ENABLED` 门控，isEnabled 先例）驱动 `generateVfsIndex`/`generateGraalvmConfig` 真实执行，产物落模块 `src/main/resources/nop-vfs-index.txt` 与 graalvm 配置目录（Phase 3 记录一次带 trace 的真实运行证据）；**生成类反射配置增量 = 任务产物之一**：任务将生成类名（`allPublicMethods=true`——入口 public static，`Class.forName`+`getDeclaredMethods`+`invoke` 的最小充分集）写入产出模块 `src/main/resources/META-INF/native-image/<groupId>/<artifactId>/reflect-config.json`（native-image 标准自动发现路径；与 GraalvmConfigGenerator 的 delta 写入语义同目录合流——read-merge-sort 幂等，两者均为增量合并语义，冲突面记录在案）。**(iii) 真实 native-image 构建（优先项）——环境裁定：不可得**（工作区无 GraalVM 发行版、无 `native-image` 可执行文件——live 核验；仓内可用先例 = `nop-kernel-cli` `-Pnative`（org.graalvm.buildtools native-maven-plugin 0.10.6）与 `nop-quarkus-demo`（`-Pnative` + build-native 脚本，已验证）为 GraalVM 环境就绪时的可复跑入口，docs-for-ai 记录复跑步骤）；不静默降级——(i)(ii) 以测试 + 记录证据真实执行（非文档背书），(iii) 的环境不可得与先例入口显式记录于本 notes + 当日 log + docs。**truffle 镜像排除机制裁定 = 文档化部署约定（Maven profile/exclusion 配方）+ 运行时 deployment-form 标记协同**：native 部署在应用 pom 以 profile 排除 `nop-xlang-truffle` 依赖（镜像不含 truffle）；若依赖仍在 classpath，`nop.xlang.execution.deployment-form=native-image`（I9 已落）使动态分支静默走解释器（belt-and-suspenders 协同语义：排除 = 结构性，标记 = 行为性）。拒绝 classifier（无仓内先例、坐标分裂成本高）。不泄漏纪律 = 既有断言保持（`TestEvalBackendDependencyDirection` + truffle 侧隔离测试全绿即 Closure Gate 证据）。

**10. S10 端到端对拍载体裁定**：**默认载体成立 = fixture 模块全真链路**，新模块 `nop-kernel/nop-xlang-java-e2e`（nop-kernel 子模块）：
- 资源集 `src/main/resources/_vfs/test/xlang/e2e/`：xpl 单元（表达式 expr / 模板 template（`$out` 输出族）/ 控制流 control（while 循环 + slot 写）/ 异常 exception（throw + 源位置回映射））+ xlib `e2e.xlib`（3 非 macro 标签：Sum（none 模式 + defaultValue 实参）/ Greet（text 模式输出）/ RangeSum（循环）——键 `path#tag`）+ 排除类型负样本（negatives/ 下 .xgen/.xrun/.xtask 各 1，断言不入清单）。
- **执行中发现的 live 约束（rollout 输入，rollout 治理归 I12+）**：(i) xpl 单元内**元素形态 xlib 标签调用**（`<ns:Tag/>`）经 `XplLibTagCompiler.parseTag` → `lazyCompile()` 后 `buildIdentifierCall` 以 `fn.getInvoker() instanceof ExecutableFunction` 分支内联为 `ExecutableFunction.withArgs` 调用节点——该节点类不在转译器支持集 120 内（I4 闭环；本 plan Non-Goal 不扩展）→ 含标签调用的 xpl 单元当前**不可转译**，任务 fail-fast（设计行为：不允许部分生成）；(ii) `xpl('ns:Tag', args)` 函数形态调用同构内联为 ExecutableFunction 调用节点，同约束。→ fixture 首落集不含标签调用单元（xlib 覆盖 = 每标签生成类 + 直接调用 + 运行时缝绑定即 I10 D4 消费面）；转译器支持集扩展（ExecutableFunction 调用节点形态）归 I12+ 后继裁定（rollout 前置条件）。
- 构建链：模块 pom 声明 exec-maven-plugin `xlang-java-gen` execution（S2/S8）→ 任务真实经构建管线运行（每次 `./mvnw test -pl ...` 即真实管线执行——"真实管线运行证据项"的常态化载体 + Phase 3 记录一次带产物 diff 的运行）；`_gen/` 落 `src/main/java/io/nop/xlang/gen/`（S3）+ 双清单 + reflect 配置落 `src/main/resources`（S4/S9）——全部落盘提交。
- 测试域：断言测试落 **fixture 模块自身** `src/test/java`（模块环消解——plan 预注方案 b："断言测试落 fixture 模块"；fixture compile-dep nop-xlang-java + test-dep nop-xlang test-jar（I1 harness 断言工具 `CompareValues`/`RecordingEvalOutput`/`SideEffectSnapshot` 显式引用）——无环）。java 列 = 供给激活态（classpath 清单经 initializer 自动装载——供给闭环本体）`XLang.parseXpl` 加载绑定 → invoke；解释器列 = 干净态（缝清空 + RCM 缓存清空——防标签 RCM 缓存跨列污染）干净编译执行；三层断言 + 身份断言（裁决环 artifact / 标签 body 包装 `EvalStaticBoundExecutable.getBinding().getBindingArtifact()` = 确定性派生生成类入口 Method）。**禁止自定义 ClassLoader/内存编译通路充当产物编译** ✓（产物编译 = Maven main compile 常规编译；测试仅消费）。live 全真链路无阻断 → 无等价载体裁定需求。
- mission.json commands 四条同步追加 `:nop-xlang-java-e2e`（"模块落盘即切换"裁定，W3-supplement 备注先例）+ `nop-kernel/pom.xml` modules 登记。

**11. docs 落点裁定**：新模块开发指南 = `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` 新增"构建集成与新模块接入（java 生成类后端）"章节（启用方式/构建任务接入/双清单产物与部署/漏跑诊断（require-manifest + reason 枚量增量）/降级诊断分级收口（I9 §五 + I10 分级 + 本 plan 使用契约统一）/truffle 镜像排除与部署形态标记/native 复跑入口）——路由不变（既有 owner doc 增节），INDEX/source-anchors 无路由变化则不动（描述性条目核对后定）；reason 枚量表增 `codegen-pipeline-missed`。Phase 3 执行。

**Phase 1 决策记录全部 repo-observable**（本 Execution Notes §0-§11 + `ai-dev/logs/2026/08-21.md` 对应条目）。

### Phase 2 落地记录（2026-08-21）

**12. 代码落点与增量**：

- **nop-xlang main（4 处增量，均纯增量无既有语义改动）**：(1) `XLangConfigs` 增 `CFG_XLANG_EXECUTION_JAVA_BACKEND_REQUIRE_MANIFEST`（缺省 false）；(2) `EvalBackendObservation` 增 `REASON_CODEGEN_PIPELINE_MISSED` 常量 + javadoc reason 枚量表增量；(3) `EvalBackendRouter.bindTagFunction(String bindingKey, ExecutableFunction fn)`（bindLoadedUnit 同构逐分支：静默/不可用观测+degraded/binder null degraded 不补记/命中 bound 包装；绑定作用面 = body，live 事实锚点 = `LazyCompiledExecutableFunction.getCompiled()` 硬转型——不替换函数实例）；(4) `XplLibTagCompiler`：`tagBindingKey(forcedOutputMode)`（默认缓存 = `path#tag`；node 强制缓存 = 声明 node 时同键否则 `@node` 变体）+ `LazyCompiledFunction` 增 bindingKey 构造参 + `compile()` 内 `applyGeneratedClassBinding()`（`ExecutableFunction.setBody` 换绑）。`XLangErrors` 增 `ERR_XLANG_GENERATED_MANIFEST_FILE_INVALID`（文件级 fail-fast 错误码）。
- **nop-xlang-java main（6 类增量）**：(1) `ExecutableTreeFingerprints` 根级 `ExecutableFunction` 分支（§0 第 5 项对账的增量面——既有调用方不传函数根，行为不变）；(2) `EvalMethodConvention.ARGS_PARAM`（`$args` 标签入口第二隐参）+ javadoc 生产布局定稿更新；(3) `ExecToJavaTranslator.translateTagUnit(entryKey, fn)`（纯增量 API；GenContext `entryTagForm` 入口发射 + `beginFnMethod` hasOut 变体——`$fn_k(IEvalScope, Object[], Object[], IEvalOutput)`，输出族节点在标签体内合法）；(4) `GeneratedClassBindingBinder` 标签形态分派（`isTagEntryMethod`/`findTagEntryMethod` + `TagGeneratedBinding`——帧槽重建实参组 + rt.getOut() 注入 + InvocationTargetException 解包；动作路径收到标签形态入口 = 约定违规降级）；(5) `GeneratedManifestFiles`（双清单行协议装载器：getResources 多 jar 聚合 + 同键异条目/同类名异键/坏行 fail-fast + `installSupplies`/`clearSupplies` 供给闭环 + 漏跑判别子消费 `require-manifest` → `markUnavailable` + WARN + 指标）；(6) `XlangJavaGenTask`（构建任务 main + `generate` 核心自防护：禁用 java 后端 + 清 xlib RCM 缓存——main 与核心双层；trace 模式提升初始化级别到 POST_PROCESS 使 GraalvmConfigGenerator initializers 真实执行；扫描口径过滤 + `_delta` 排除 + 排除类型清点 + 指纹-转译次序硬规则 + write-if-changed 幂等 + check 模式漂移哨兵（含陈旧多余产物检测）+ 同形折叠写盘前 fail-fast（原子性）+ reflect-config 合并产物）。
- **`XLangJavaBackendInitializer`**：register + `installSupplies`（运行时供给闭环——清单在场即激活；destroy 清缝）。
- **fixture 模块 `nop-kernel/nop-xlang-java-e2e`**（S10）：pom（exec-maven-plugin `xlang-java-gen` execution @ generate-test-resources + 抑制继承的 4 个 CodeGenTask executions）+ `_vfs` 资源集（4 xpl + e2e.xlib 3 标签 + 3 排除负样本）+ 任务产物落盘提交（7 生成类 + 双清单 + reflect-config）+ e2e 断言测试（Phase 3）。`nop-kernel/pom.xml` modules + dependencyManagement（nop-xlang-java）登记；mission.json commands 四条追加 `:nop-xlang-java-e2e`（模块落盘即切换）。

**13. 测试清单（新增 41 用例 = nop-xlang-java 27 + e2e 模块 5 + 既有护栏修正 1 + 反漂移 1 + ... 计数见验证）**：
- `TestTagUnitGeneration` 6：根级函数指纹（结构等价同指纹/签名差异/函数体差异/源位置差异）+ translateTagUnit 源码形态（入口签名 + source 头）+ Sum 生成执行 vs 解释器（显式/单实参两形态）+ Greet 文本输出标签（调用方形态执行 vs 生成列输出序列逐项比对）+ 手工构造缺省实参函数（defaults 翻译行为：`$args.length > i ? $args[i] : <default>`）+ 指纹确定性（fresh 重编译同指纹）。
- `TestTagFunctionBinding` 6：真实缝路径（RCM 装载触发 LazyCompiledFunction.compile → bindTagFunction）——命中（body = bound 包装 + artifact = 确定性派生夹具类入口 Method + 执行正确 + 零降级观测增量）/清单外静默/指纹失配（degraded 包装 + 每次 WARN 计数 + 解释器兜底结果正确）/不可用（观测 + degraded）/开关关静默/调用方形态帧槽执行（30L）。
- `TestTagFixtureSources` 1 + `TestGeneratedFixtureSources` 计数守卫修正（49→50，注释标注 tag 夹具增量）：反漂移护栏。
- `TestGeneratedManifestFiles` 8：扫描清单多源聚合去重/生成清单聚合（同条目去重 + 条目查找）/同键异条目 fail-fast/同形折叠 fail-fast/坏行 fail-fast/装载闭环绿（双缝填充 + require=true 清单在场零 WARN）/漏跑红（require=true + 空 classpath → 不可用条目 + `codegen-pipeline-missed` WARN（消息键断言）+ 指标 delta + 非成员 → 动态路径）/缺省空态静默（require=false 无 WARN、可用性保持）。
- `TestXlangJavaGenTask` 6：扫描口径过滤（xpl/xlib 纳入、xgen/xrun/xtask/xmeta 排除 + 清点计数、`_delta` 排除）/产物全形态（生成源码 + 双清单排序 + reflect-config）/幂等（二轮零写 + check 干净）/漂移检测（内容漂移 + 陈旧多余产物）/同形折叠 fail-fast 不写盘（原子性）/指纹跨工程确定性（同路径同内容同指纹同生成码）。
- e2e `TestEndToEndGeneratedBinding` 5（Phase 3 验收载体，见 §14）。

**14. Phase 3 落地记录 + 验收（2026-08-21）**：

- **端到端对拍（roadmap 验收第一项）**：`TestEndToEndGeneratedBinding.testXplUnitsBoundExecutionMatchesInterpreter` + `testXlibTagsBoundExecutionMatchesInterpreter`——真实 `_vfs` 资源（4 xpl：expr/control/template（`$out` 输出族）/exception（throw + 源位置）；xlib 3 标签：Sum（none + defaultValue）/Greet（text 输出）/RangeSum（循环））经 **任务扫描（构建管线真实运行——generate-test-resources phase，exec-maven-plugin）→ 转译 → Maven 常规编译（`_gen` 随 src/main/java）→ classpath 双清单 → initializer 自动装载（供给闭环本体）→ 加载绑定（parseXpl hook / 标签运行时缝）→ invoke**；java 列 vs 解释器列（干净态 + 缓存清空）**三层断言**：返回值 `CompareValues.typedEquals`（I1 harness 显式引用）/ 输出缓冲 `RecordingEvalOutput.getCalls()` 完整调用序列（含 SourceLocation）/ 异常语义（错误码 + loc path/line/col 回映射——exception.xpl）+ **身份断言**（xpl：bound 包装 + artifact = 确定性派生生成类入口 Method（`EvalMethodConvention.generatedClassName` 同派生）；xlib：标签 body bound 包装 + artifact = 标签生成类入口）。禁止自定义 ClassLoader/内存编译 ✓（测试仅消费 Maven 常规编译产物）。
- **真实管线运行证据**：e2e 模块构建即任务运行（`./mvnw generate-test-resources -pl :nop-xlang-java-e2e` 亲跑，产物 7 生成类 + 双清单 + reflect-config 落盘在仓——"不绕过 codegen 管线"的可验证载体）；执行中任务在 fixture 资源 bug 上三次 fail-fast（XML 转义/常量 i/多根节点——全量转译后写盘前失败，无半成品）= 原子性 + 真实性证据。
- **重生成幂等复跑（验收第三项，验收口径）**：`testRegenerationIdempotentAgainstCommittedProducts`——任务 check 模式对本模块真实产物比对（committed == 再生成，7 单元零漂移）；单元级另含二轮零写 + 漂移注入红/绿（`TestXlangJavaGenTask`）。
- **漏跑可观测复跑（验收第二项，验收口径）**：`TestGeneratedManifestFiles.testMissedRunRedPathMarksUnavailableAndWarns`——红：require=true + 清单缺席 → 不可用条目查询（`markUnavailable` + reason）+ WARN（消息键 `nop.xlang.execution.backend-degraded` + reason `codegen-pipeline-missed`）+ 指标 delta + 扫描清单空 → 非成员 → 动态路径；绿：清单在场零观测。e2e 模块绿灯面（`ensureSupplies` + closed-world 断言可用性）。
- **native 验证（Phase 1 §9 载体）**：(i) closed-world 结构断言 = `testClosedWorldStructureOfGeneratedClasses`（7 生成源码无 `defineClass`/`ClassLoader`/`javax.tools`/`janino` + 生成类经应用类加载器常规加载——`getClassLoader()` 同一性断言）；(ii) `GraalvmConfigGenerator` 复用接线可观察 = 亲跑 `-Dnop.codegen.trace.enabled=true` 的任务运行：`CodeGenAfterInitialization`（POST_PROCESS 级——任务 trace 模式提升初始化级别接线）真实执行 `generateVfsIndex` → `src/main/resources/nop-vfs-index.txt`（19KB，VFS 资源索引产物）+ reflect delta 合流（58 条目观测后回归纯任务产物 7 条目——两者均增量合并语义，冲突面记录）；(iii) 真实 native-image 构建 = **环境不可得显式裁定**（工作区无 GraalVM 发行版/无 native-image 可执行文件，live 核验）——仓内先例复跑入口在 docs-for-ai 记录（`nop-kernel-cli -Pnative`、`nop-quarkus-demo -Pnative` + build-native 脚本）；不静默降级（(i)(ii) 以测试 + 亲跑证据真实执行）。truffle 镜像排除 = 文档化部署约定（profile/exclusion 配方 + deployment-form 协同）+ 不泄漏断言既有测试保持全绿。
- **docs-for-ai 同步**：`xlang-and-xpl-basics.md`——"生成类加载"小节收口（文件产物自动装载指向新章节 + xlib 每标签绑定语义与限制）+ 新章节"构建集成与新模块接入"（启用/任务行为/产物/限制）+ "漏跑诊断与部署"（判别子/native/truffle 排除/复跑入口）+ 配置表增 require-manifest + reason 枚量表增 `codegen-pipeline-missed`；INDEX.md 增路由行；source-anchors.md 增 `GEN-010` 锚点（closure audit 勘误：起草时记 GEN-008，实际落锚 GEN-010——GEN-008/009 为既有占用条目）；`check-doc-links.mjs --strict` 退出码 0（顺手修复 5 处与本 plan 无关的存量坏链——code-style/nop-ai roadmap/guardrail-contract 的反引号路径误解析）。
- **I12 移交显式记录（责任链 repo-observable）**：(1) **三后端可用形态**：解释器 = 缺省；java = 构建任务产物 + classpath 双清单自动装载（`nop-xlang-java-e2e` 参考实现；漏跑判别子 require-manifest）；truffle = 模块在场即注册（I8 池运行时）。(2) **命令口径**：mission.json commands 已含 `:nop-xlang-java-e2e`（test/build/lint/typecheck 四条）；全量三后端对拍套件入口 = corpus 三列既有测试 + e2e 模块生产路径列。(3) **全量对拍套件输入**：I10 corpus 48 静态单元生产绑定列（`TestProductionBindingRoutingScenarios`）+ I11 e2e 列（`TestEndToEndGeneratedBinding`）+ 转译/翻译矩阵（`TestExecTranslationCoverageMatrix`/`TestTruffleCoverageMatrix`）。(4) **native 验证证据指针**：closed-world 断言（e2e test）+ trace 运行 vfs-index/reflect 产物（§14）+ 环境不可得裁定 + 仓内先例入口（docs-for-ai「漏跑诊断与部署」）。(5) **Q1/Q4 watch-only 重评估触发口径量化输入**：Q1（native 内宿主解释开销）→ java 后端 native 形态收益 = 生成类直编（无解释开销）——量化基准应含 e2e fixture 单元的 java 列 vs 解释器列耗时对比（I12 基准范畴）；Q4（truffle 翻译缓存收益）→ 不变（I8 口径）。(6) **rollout 前置条件（I12+ 治理输入）**：含 xlib 标签调用的 xpl 单元不可转译（`ExecutableFunction` 内联调用节点不在支持集——Extension 归转译器支持集扩展裁定）；xpl 片段（`impl_*.xpl` 经 c:include 宿主编译）独立扫描的排除机制；`@node` 变体键生成；全仓铺开的分模块推进顺序。
- **验证**：`./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle,:nop-xlang-java-e2e -am -T 1C` = **551/0/0(2skip) + 493/0/0 + 584/0/0 + 5/0/0 全绿**（I10 基线 551/466/584 全保持——nop-xlang-java 466→493（+27）、e2e 新模块 +5）；`checkstyle:check -Pqa` 退出码 0；依赖方向 = `TestEvalBackendDependencyDirection` 全绿（551 内）+ e2e closed-world 断言 + nop-codegen 零改动（pom diff 无 nop-xlang-java 边）。

## Execution Plan
### Phase 1 - 扫描口径清点 + 任务落点/格式/机制裁定（含 xlib 生产形态与漏跑判别子必答）

Status: completed

Targets: 本 plan Execution Notes 与当日 log（口径清单与决策记录 repo-observable）

- Item Types: `Decision | Proof`

- [x] **前置硬门禁核验**：I10 已 `completed`（roadmap live 核验）+ I10 移交记录复核（**六项**逐项对账：双清单内存契约/供给缝/唯一性校验/漏跑可观测消费点/**D1 指纹实现复用**（构建任务写清单指纹与运行时校验同一算法——live 锚点对账）/**D4 编译单元形态**（xlib 多根消费面裁定——本 plan 生产侧按同形态落地，形态冲突即就地裁定并记录））；实际落地与本 plan 假设的偏差就地修订 plan；未完成 → 置 `blocked` 回引擎并记 log（Execution Notes §0：六项对账逐项 PASS，无形态冲突——xlib 裸路径约束以 `path#tag` 键形态化解，即 D4 设计意图）
- [x] **扫描口径 live 清点定稿**（Execution Notes §1：全仓类型清点 + 逐类型裁定表——xpl/xlib 纳入；xgen/xrun 排除（仅构建期消费，纳入标准第二维不满足）；xtask 排除（仅测试资源人口，保守面）；其余 XDSL 排除（非独立编译单元）；xpl 片段机制注记（impl_*.xpl 经宿主编译，rollout 输入归 I12+）；不引入 live 不存在类型 ✓）
- [x] **xlib 多根生产形态裁定**（Execution Notes §7：每标签一单元类 + 键 `path#tag`（I10 D4 一致）；转译器增量 API `translateTagUnit`（既有测试零削弱——纯增量）；指纹增根级 ExecutableFunction 分支；运行时绑定缝 = `bindTagFunction` + `LazyCompiledFunction` 内 setBody 换绑（不替换 IEvalFunction 实例——`LazyCompiledExecutableFunction.getCompiled()` 硬转型 live 事实）；输出模式变体键 `@node` 命名空间 + 首落不入清单记录；macro 标签排除）
- [x] **任务落点与依赖方向裁定**（Execution Notes §2：任务类落 nop-xlang-java main + 独立 mainClass 入口 + 模块 pom 显式 exec-maven-plugin 增量 execution；nop-codegen 零改动零新依赖边；ServiceLoader 隐患处置 = 独立入口形态（非 initializer）+ 任务 JVM java-backend 禁用；(b) 依赖边增补拒绝理由在案）
- [x] **`_gen/` 产物布局与包名策略定稿**（Execution Notes §3：包 = GENERATED_PACKAGE（转译器 FQN 契约零扩展）+ 文件落 srcRoot/io/nop/xlang/gen/Gen_*.java 落盘提交（datav 先例同构）；同形路径唯一性校验双面落地（任务侧 + 聚合装载侧）；postcompile 时序约束显式记录（stale 窗口 = 设计内行为））
- [x] **双清单文件格式与存放路径定稿**（Execution Notes §4：文本行协议两份分离产物 + `META-INF/nop-xlang/` 存放 + 排序/TAB 定界/64-hex 校验；多 jar 聚合 = getResources 级内建（同键异条目/同类名异键 fail-fast）；JSON/YAML 拒绝理由在案；装载器落点 `GeneratedManifestFiles`）
- [x] **漏跑可观测接线形态定稿（含判别子必答）**（Execution Notes §5：判别子 = 清单在场性 + 显式启用声明组合（`nop.xlang.execution.java-backend.require-manifest` 缺省 false）——张力必答在案（classpath 信号原理上不可区分，部署方显式声明为唯一判别源；缺省 = 存量合法空态静默，护栏保持）；检测时机 = 初始化装载时；消费点 = `markUnavailable("codegen-pipeline-missed")` + `EvalBackendObservation` 新 reason 枚量（I9 命名契约一致）；红/绿载体定稿）
- [x] **truffle 镜像排除机制裁定**（Execution Notes §9：文档化部署约定（profile/exclusion 配方）+ deployment-form 标记协同（结构性 + 行为性 belt-and-suspenders）；classifier 拒绝理由；不泄漏断言 = 既有测试保持）
- [x] **native 验证载体裁定（最低可执行下界预置）**（Execution Notes §9：(i) closed-world 结构断言 + (ii) GraalvmConfigGenerator 复用接线产物侧可观察（trace 门控 + 插件级 nop-codegen 依赖 live 事实 + 任务 reflect-config 增量产物）为测试/证据落地；(iii) 真实 native-image 环境不可得显式裁定（无 GraalVM/native-image，live 核验）+ 仓内先例复跑入口记录（nop-kernel-cli/-Pnative、nop-quarkus-demo）——不静默降级）
- [x] **端到端对拍载体裁定**（Execution Notes §10：默认载体成立 = 新 fixture 模块 `nop-kernel/nop-xlang-java-e2e` 全真链路（任务经 exec-maven-plugin 真实运行 + `_gen/` 落盘提交 + Maven 常规编译 + classpath 清单 + initializer 自动装载 + 绑定执行）；模块环消解 = 断言测试落 fixture 模块自身；禁止自定义 ClassLoader/内存编译 ✓；解释器列缓存隔离处置在案；mission.json commands 同步追加（模块落盘即切换））
- [x] docs 落点裁定（Execution Notes §11：`xlang-and-xpl-basics.md` 增节（路由不变）；reason 枚量增量；INDEX/source-anchors 视路由变化核对）
- [x] 决策记录全部落 plan Execution Notes / 当日 log（§0-§11 + `ai-dev/logs/2026/08-21.md`）

Exit Criteria:

- [x] 扫描口径清点表 + 九项裁定（任务落点/布局/**xlib 生产形态**/清单格式/漏跑接线含判别子/truffle 排除/native 载体（含最低下界）/对拍载体（默认全真链路）/docs 落点）repo-observable，每项含候选、裁定、live 锚点（Execution Notes §1-§11，每项含候选/裁定/live 锚点/拒绝理由）
- [x] I10 移交六项对账记录在案（偏差已修订 plan 或显式裁定）（§0 六项逐项 PASS——xlib 裸路径约束以键形态化解为设计意图内解除，无偏差修订需求）
- [x] No owner-doc update required（本 Phase 无文档变更；docs 同步为 Phase 3 执行项）
- [x] `ai-dev/logs/` 对应日期条目已更新
### Phase 2 - 任务实现 + 双清单产物 + 供给闭环 + native 兼容

Status: completed

Targets: 构建任务（Phase 1 裁定落点）、`nop-kernel/nop-xlang-java/src/main/`（清单装载器/漏跑接线）、`nop-kernel/nop-xlang-truffle` pom（如裁定的排除机制）、docs 待同步项

- Item Types: `Proof`

- [x] 任务实现：扫描（口径表）→ 与运行时相同前端取树（nop-xlang 编译前端复用，不复制逻辑——Delta 合并语义与运行时一致）→ `ExecToJavaTranslator` 转译 → `_gen/` 源码写入 + 双清单分离产物写入；**重生成幂等**（同输入重跑产物等价——等价口径 Phase 1 裁定：逐字节或语义等价断言）（Execution Notes §12：`XlangJavaGenTask`——扫描口径过滤 + parseClean/RCM 同前端 + write-if-changed 逐字节幂等 + check 模式；§13 测试 6+6 用例）
- [x] 同形路径唯一性校验落地（清单侧冲突 fail-fast + 用例）（§12 任务侧写盘前 fail-fast + 装载侧聚合同类名异键 fail-fast；`TestXlangJavaGenTask.testSameFormPathFoldingFailsFastWithoutWriting` + `TestGeneratedManifestFiles.testSameFormPathFoldingCollisionFailsFast`）
- [x] 运行时供给闭环：清单文件装载器 → I10 供给缝（`setStaticScanList` + binder 生产填充）；缺省无产物 = 空态行为与现状一致（I9/I10 护栏语义保持）（§12 `GeneratedManifestFiles.installSupplies` + initializer 接线；`TestGeneratedManifestFiles.testInstallSuppliesGreenPathFillsSeams`/`testDefaultEmptyStateStaysSilent` + e2e 自动装载断言）
- [x] 漏跑可观测接线：按 Phase 1 判别子裁定的触发条件（区分"从未接入（合法空态，静默）"与"接入后漏跑（缺陷）"）——漏跑形态 → `markUnavailable`（原因=构建管线漏跑）+ 全局 WARN；全部资源走动态路径/解释器（§12 require-manifest 判别子消费；`testMissedRunRedPathMarksUnavailableAndWarns` 红 + 绿对照在案）
- [x] native 兼容：`GraalvmConfigGenerator` 复用接线（Phase 1 裁定范围）+ truffle 镜像排除机制落地 + 不泄漏断言保持（§12 trace 模式 POST_PROCESS 级提升接线 + reflect-config 任务产物；§14 (ii) 亲跑证据 + 文档化排除约定 + 既有隔离测试全绿）
- [x] 单测（显式覆盖清单，guide 规则 25）：任务正/负（口径过滤逐类型/产物存在性与内容/幂等重跑断言/同形冲突 fail-fast/前端一致性——合成资源扫描产物与运行时编译树指纹一致）（§13 六类测试清单逐项；前端一致性 = e2e 绑定命中即指纹一致证明 + 指纹确定性用例）；装载闭环（文件 → 内存契约 → 绑定可用 → 路由命中）；漏跑可观测（红：清单缺席 + 启用 → 不可用条目 + WARN + 全解释器/动态；绿：清单在场 → 正常绑定）
- [x] 新代码无静默跳过审查：扫描口径外类型显式排除（记录于口径表而非静默忽略——任务日志 excludedTypes 清点）；清单数据非法（坏行/指纹缺失/类名非法）fail-fast；任务失败不产出半成品（原子性或失败标记——裁定记录）（§12 原子性 = 全量转译校验后写盘 + 三次真实 fail-fast 证据；非法数据三形态 fail-fast 用例在案）

Exit Criteria:

- [x] 任务/产物/装载/漏跑观测/排除机制代码在仓且单测清单逐项在案（新增功能测试覆盖显式列出）（§12/§13：41 新增用例显式清单）
- [x] 重生成幂等断言通过；`_` 前缀产物不可手改纪律保持（产物仅由任务生成）（write-if-changed + check 哨兵 + Gen_ 文件头标记 + 重生成覆盖纪律；e2e 验收口径复跑在案）
- [x] 漏跑可观测红/绿对照在案——roadmap I11 验收第二项（测试载体）（`testMissedRunRedPathMarksUnavailableAndWarns` + 绿对照）
- [x] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` 全绿（如任务涉及其它模块，`-pl` 口径 Phase 1 裁定并入）；checkstyle 退出码 0（§14 验证：扩展口径四模块 551+493+584+5 全绿；checkstyle -Pqa EXIT=0）
- [x] 依赖方向保持（nop-codegen 无 nop-xlang-java 编译依赖——Phase 1 裁定成立时断言化）（nop-codegen pom 零改动 + `TestEvalBackendDependencyDirection` 全绿 + e2e closed-world 断言）
- [x] No owner-doc update required（docs 同步为 Phase 3 执行项，Phase 边界在案）
- [x] `ai-dev/logs/` 对应日期条目已更新
### Phase 3 - 端到端对拍验收 + docs-for-ai 同步

Status: completed

Targets: 端到端对拍测试（Phase 1 裁定载体）、`docs-for-ai/`（Phase 1 裁定落点）

- Item Types: `Proof`

- [x] **端到端对拍（roadmap 验收第一项）**：真实 `_vfs` 静态资源（测试 fixture，覆盖纳入类型各 ≥1 含 xlib）经 任务扫描 → 转译 → **常规编译**（Phase 1 裁定载体：默认 fixture 模块全真链路——任务真实经构建管线运行、`_gen/` 落盘、Maven 常规编译；禁止自定义 ClassLoader/内存编译通路充当产物编译）→ 清单装载 → 加载绑定 → invoke，java 列 vs 解释器列**全量一致**（三层断言：返回值 typedEquals / scope 副作用 / 输出缓冲 / 异常语义（错误码 + SourceLocation 回映射））+ 身份断言（执行体 = 生成类绑定 artifact）——经 I1 harness 断言工具（显式引用关系可验证，非独立重写比对）（Execution Notes §14：`TestEndToEndGeneratedBinding` 5 用例——4 xpl 单元三层断言 + 3 标签列一致 + 身份断言双面；S10 修订：fixture 集不含标签调用单元（live 约束记录——`ExecutableFunction` 内联调用节点不可转译））
- [x] **真实管线运行证据项**：任务经真实构建管线（postcompile/exec-maven-plugin 或 `CodeGenTask` 入口，Phase 1 裁定形态）至少完整运行一次的证据（构建日志/产物时间戳/落盘产物 diff 记录——"不绕过 codegen 管线"的可验证载体，非文字背书）（§14：e2e 模块构建亲跑（generate-test-resources），产物 7 生成类 + 双清单 + reflect-config 落盘在仓；执行中三次 fail-fast 证据（原子性））
- [x] **重生成幂等断言复跑（验收第三项）**：任务重跑产物等价断言（Phase 2 载体在验收口径下复跑记录）（§14：`testRegenerationIdempotentAgainstCommittedProducts` check 模式零漂移 + 单元级二轮零写/漂移注入红绿）
- [x] **漏跑可观测断言复跑（验收第二项）**：红/绿对照在验收口径下成立（不可用条目查询 + WARN 断言 + 路由全走解释器/动态路径断言）（§14：红/绿用例——不可用条目 + WARN 消息键 + 指标 delta + 非成员动态路径 + 绿灯零观测；e2e 绿灯面在案）
- [x] native 验证按 Phase 1 裁定载体执行并记录证据（可复跑入口 + 结果落 repo 或 log）（§14：(i) closed-world 测试在仓 + (ii) trace 亲跑证据（vfs-index 19KB + reflect 合流观测）+ (iii) 环境不可得显式裁定 + docs 复跑入口）
- [x] docs-for-ai 新模块开发指南同步（启用方式/构建任务/双清单产物/降级诊断分级/truffle 镜像排除/部署形态标记——I9 §五 + I10 分级 + 本 plan 使用契约的统一收口）；如新增文档或路由变化同步 `docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md`；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（§14：两新章节 + 配置表 + reason 枚量 + INDEX 路由行 + GEN-010 锚点（closure audit 勘误：原记 GEN-008，实际 GEN-010——既有条目占用）；link checker 0 errors——含 5 处存量坏链顺手修复）
- [x] **I12 移交显式记录**：基准与全量收口的输入（三后端可用形态/命令口径/全量对拍套件入口/native 验证证据指针）+ Q1/Q4 watch-only 重评估触发口径的量化输入（责任链 repo-observable，落 plan Execution Notes + 当日 log）（§14 六项移交记录）
- [x] 全量回归（`./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` 及 Phase 1 裁定的扩展口径）+ 不削弱既有测试（§14：四模块 551+493+584+5 全绿；I10 基线全保持 + 既有护栏唯一修正 = 夹具计数守卫 49→50（纯增量语义，注释标注））

Exit Criteria:

- [x] **端到端对拍全绿（真实 `_vfs` 资源经任务全链路：扫描→转译→常规编译→装载绑定→执行；三层断言 + 身份断言；经 I1 框架断言工具）——roadmap I11 验收第一项**（`TestEndToEndGeneratedBinding` 5/5 PASS）
- [x] **构建管线漏跑可观测断言在案（红/绿对照）——验收第二项**（`TestGeneratedManifestFiles` 红/绿 + e2e 绿灯面）
- [x] **重生成幂等断言在案——验收第三项**（check 模式验收口径 + 单元级红/绿）
- [x] **端到端验证**：从构建任务入口到运行时执行的完整链路可运行（任务 → 产物 → 编译 → classpath → 装载 → 绑定 → invoke → 断言）；任务经真实构建管线至少完整运行一次的证据在案（§14）
- [x] **接线验证**：任务产物真实被运行时装载消费（装载闭环断言 = 接线证据）；漏跑检测真实调用 `markUnavailable`（红/绿因果）（initializer 自动装载断言 + 红路径 markUnavailable 因果断言）
- [x] **无静默跳过**：口径外类型显式记录；非法产物 fail-fast；漏跑显式观测（Phase 2 审查项复验）（排除清点 + 三形态 fail-fast + 漏跑观测——测试在案）
- [x] native 验证证据在案（Phase 1 裁定载体）；truffle 排除机制落地 + 不泄漏断言全绿（§14）
- [x] docs-for-ai 同步在仓 + link checker 退出码 0（如路由变化 INDEX/source-anchors 已同步）（§14；INDEX + GEN-008 已同步）
- [x] I12 移交记录 repo-observable；`ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 端到端对拍全绿（真实 `_vfs` 静态资源经任务扫描→转译→常规编译→加载绑定后 java 列 vs 解释器列全量一致 + 身份断言）——roadmap I11 验收第一项（`TestEndToEndGeneratedBinding` 5/5：三层断言 + 身份断言双面）
- [x] 构建管线漏跑可观测断言（java 后端启用但扫描清单缺失 → 注册不可用条目 + 全局 WARN）——验收第二项（`TestGeneratedManifestFiles.testMissedRunRedPathMarksUnavailableAndWarns` 红/绿对照）
- [x] 重生成幂等断言——验收第三项（check 模式零漂移 + 二轮零写 + 漂移注入红绿）
- [x] 扫描口径清点表 repo-observable（逐类型纳入/排除 + 理由；不引入 live 不存在类型）（Execution Notes §1 裁定表）
- [x] 双清单分离产物落地（should-set 与生成类清单两份独立产物；同形路径唯一性校验 fail-fast 在案）（任务侧 + 装载侧双面）
- [x] 任务经与运行时相同编译前端取树（无前端逻辑复制）；不绕过 codegen 管线（真实管线运行证据在案）；`_` 前缀产物不可手改纪律保持（parseClean/RCM 同前端 + exec-maven-plugin 构建管线 + 重生成覆盖纪律）
- [x] 模块依赖方向保持（nop-codegen 无 nop-xlang-java 编译依赖；org.graalvm 依赖仅 nop-xlang-truffle——不泄漏断言全绿）（nop-codegen pom 零改动 + `TestEvalBackendDependencyDirection` + truffle 隔离测试全绿）
- [x] native image 兼容项落地（GraalvmConfigGenerator 复用 + 生成类直编验证证据 + truffle 镜像排除机制）（closed-world 测试 + trace 亲跑证据 + 环境不可得裁定 + 文档化排除约定）
- [x] docs-for-ai 新模块开发指南同步 + link checker 退出码 0（两新章节 + INDEX + GEN-010；`check-doc-links.mjs --strict` 0 errors）
- [x] I12 移交显式记录 repo-observable（Execution Notes §14 六项）
- [x] 回归不允许削弱现解释器测试（纪律 3）；缺省无产物 = 行为与现状一致（I10 基线 551/466/584 全保持；唯一护栏修正 = 夹具计数 49→50 纯增量；缺省空态测试在案）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 区显式空；rollout 约束为 I12 既定归属 + 显式移交，非 in-scope 缺陷降级）
- [x] 独立子 agent closure-audit 已完成并记录证据（fresh session `ses_fddcb5215ffe2mmdvAYc87DrGv`，2026-08-21，Verdict **CAN CLOSE**——Phase 1/2/3 Exit Criteria 与 16 条 Gates 逐项 live 核验 PASS；详见 Closure 段 Evidence）
- [x] Anti-Hollow Check：closure audit 已验证（a）任务产物真实被运行时装载消费，（b）端到端 任务→产物→编译→装载→绑定→执行 连通，（c）无空方法体/静默跳过/no-op 作为正常实现（调用链追踪：classpath 清单 → initializer.installSupplies → I10 双缝 → bindLoadedUnit/bindTagFunction → EvalStaticBoundExecutable → entryMethod.invoke；`scan-hollow-implementations.mjs --severity high` 三模块均 0 findings）
- [x] `./mvnw compile`（`-pl` 按 Phase 1 裁定口径）（四模块 `-am` compile 0 error）
- [x] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C`（及裁定扩展口径）全绿（扩展口径 = `:nop-xlang-java-e2e` 追加：**551/0/0(2skip) + 493/0/0 + 584/0/0 + 5/0/0**）
- [x] checkstyle / 代码规范检查通过（mission lint 口径）（`checkstyle:check -Pqa` 四模块 BUILD SUCCESS）
- [x] doc link checker 退出码 0（docs-for-ai 变更后）（`check-doc-links.mjs --strict` No errors found）

## Deferred But Adjudicated

（起草时无新 deferred 项。存量全仓 `_vfs` 全量生成产物落盘、基准与全量收口归 I12、性能与池调优归 I12 均为 roadmap 既定归属（Non-Goals 显式排除）。执行中产生时按 guide 补录并写明 Why Not Blocking Closure。）

## Non-Blocking Follow-ups

- Q1/Q4 watch-only 触发口径量化归 I12（本 plan 提供输入，I12 移交记录承载）。

## Closure

Status Note: 三 Phase 全部落地（Phase 1 十一项裁定 repo-observable + Phase 2 任务/双清单/供给闭环/漏跑观测实现与 41 新增用例 + Phase 3 e2e 全真链路验收与 docs 同步）；Closure Gates 16 条全勾；roadmap I11 三项验收（端到端对拍 + 漏跑可观测 + 重生成幂等）均有测试载体在仓；独立 fresh closure audit Verdict **CAN CLOSE**（0 Blocker；2 项收口动作均已执行：GEN-010 勘误 + changeset 提交）。
Completed: 2026-08-21

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh 子 agent closure audit（research-only，非实现 session）
- Audit Session: ses_fddcb5215ffe2mmdvAYc87DrGv（2026-08-21）
- Evidence:
  - **Phase 1/2/3 Exit Criteria 全 PASS**（live 锚点逐项核验）：`XlangJavaGenTask`（扫描过滤 + excludedTypeCounts 清点非静默 / `XplModelParser`+RCM 同前端 / 指纹先于转译次序（含锁定测试）/ 写盘前折叠 fail-fast（原子性）/ write-if-changed / check 模式 + 陈旧产物检测 / reflect-config 合并产物）；`GeneratedManifestFiles`（行协议 / getResources 多 jar 聚合 / 同键异条目 + 同类名异键 + 坏行三形态 fail-fast / installSupplies·clearSupplies 供给闭环）；`XLangJavaBackendInitializer.initialize()` = register + installSupplies；nop-xlang 四处增量（CFG require-manifest / REASON_CODEGEN_PIPELINE_MISSED / bindTagFunction / XplLibTagCompiler 换绑）在案；`TestEndToEndGeneratedBinding` 5 用例（三层断言 + 双面身份断言 + 负样本不入清单 + check 模式幂等 + closed-world 结构断言）。
  - **Closure Gates 16 条全 PASS**（第 13 条独立 audit / 第 14 条 Anti-Hollow 由本次 audit 履行；其余 14 条 live 证据在案）。
  - **验证命令退出码**：`./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle,:nop-xlang-java-e2e -am -T 1C` EXIT=0（**551/0/0(2skip) + 493/0/0 + 584/0/0 + 5/0/0**——与 plan 声明逐模块一致，I10 基线全保持）；`checkstyle:check -Pqa` EXIT=0；`check-doc-links.mjs --strict` EXIT=0（0 errors）；`check-plan-checklist.mjs --strict` EXIT=0。
  - **Anti-Hollow 检查结果**：(a) 产物运行时消费链连通（调用链追踪：classpath 双清单 → `XLangJavaBackendInitializer.initialize()` → `GeneratedManifestFiles.installSupplies` → I10 双缝（setStaticScanList/setGeneratedClassManifest）→ xpl `bindLoadedUnit` / xlib `LazyCompiledFunction.compile()`→`bindTagFunction`→`setBody` 换绑 → `EvalStaticBoundExecutable` → 生成类入口 `Method.invoke`；身份断言双面验证 artifact = 生成类入口）；(b) 端到端 任务→产物→Maven 常规编译→装载→绑定→执行 连通（测试仅消费常规编译产物；closed-world 断言应用类加载器同一性，无自定义 ClassLoader/内存编译充当产物编译）；(c) 无空方法体/静默跳过/no-op 充当正常实现（全部静默返回均映射 plan 裁定分支：清单外动态路径/缺省空态/非参与变体；fail-fast 覆盖坏行/折叠冲突/同键冲突/不支持转译/标签形态误入动作路径）；`scan-hollow-implementations.mjs --module nop-xlang|nop-xlang-java|nop-xlang-java-e2e --severity high` 均 EXIT=0（0 findings）。
  - **Deferred 项分类检查**：Deferred 区显式空；rollout 约束（xlib 标签调用单元不可转译等）为 I12+ 既定归属显式移交（Execution Notes §14 第 6 项），无 in-scope live defect / contract drift 降级为 follow-up。
  - **Audit findings 处置**（2 项，均已执行）：(1) GEN-008→GEN-010 文本勘误——source-anchors.md 实际落锚 `GEN-010`（GEN-008/009 为既有占用条目），plan §14/Phase 3/Gate 行已当场更正；(2) I11 changeset 落盘提交（按 AGENTS.md git 纪律与 I9/I10 收口先例，收口时提交，见当日 log/commit）。
Follow-up:

- no remaining plan-owned work（I12 移交六项已显式记录于 Execution Notes §14；Q1/Q4 watch-only 量化输入与 rollout 前置条件归 I12+ 为 roadmap 既定归属，非本 plan 遗留）。
