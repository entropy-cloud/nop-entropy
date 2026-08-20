# I11 构建集成 + native image 兼容 + docs-for-ai 同步

> Plan Status: active
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

## Execution Plan

### Phase 1 - 扫描口径清点 + 任务落点/格式/机制裁定（含 xlib 生产形态与漏跑判别子必答）

Status: planned
Targets: 本 plan Execution Notes 与当日 log（口径清单与决策记录 repo-observable）

- Item Types: `Decision | Proof`

- [ ] **前置硬门禁核验**：I10 已 `completed`（roadmap live 核验）+ I10 移交记录复核（**六项**逐项对账：双清单内存契约/供给缝/唯一性校验/漏跑可观测消费点/**D1 指纹实现复用**（构建任务写清单指纹与运行时校验同一算法——live 锚点对账）/**D4 编译单元形态**（xlib 多根消费面裁定——本 plan 生产侧按同形态落地，形态冲突即就地裁定并记录））；实际落地与本 plan 假设的偏差就地修订 plan；未完成 → 置 `blocked` 回引擎并记 log
- [ ] **扫描口径 live 清点定稿**：`_vfs` XDSL/可执行资源类型全清点（`_vfs` 内 live 文件类型扫描），`_vfs/**/*.xpl`/`*.xlib` 确定纳入；其余类型（xtask（`XplTaskLoader` 锚点）/xgen/xrun/嵌入 xpl 片段等）逐项裁定纳入/排除及理由（纳入标准 = 是否构成独立 Executable 编译单元 **且运行时经可绑定加载路径消费**——如 `.xgen` 在 `_vfs` 内大量存在且构成编译单元、但仅构建期消费（模板生成入口），仅按编译单元标准会误纳入；不引入 live 不存在类型——设计 java §六硬约束）；清点口径表落 Execution Notes（I9 出口清单同款做法：逐项裁定 + 理由，独立可审）
- [ ] **xlib 多根生产形态裁定**：live `XplTagLib` 为多标签、每标签独立 Executable 的多根形态，`ExecToJavaTranslator.translate` 为单根 API——生产生成形态（每标签一入口方法 vs 每标签一单元类）与清单键形态（resourcePath vs resourcePath+标签名）裁定，**须与 I10 D4 消费面裁定一致**（前置对账第六项）；如需转译器多入口扩展，扩展面与既有测试不削弱方案一并裁定
- [ ] **任务落点与依赖方向裁定**：候选 = (a) 任务类落 nop-xlang-java + 经 `CodeGenTask postcompile` 流程扩展发现（services/显式注册形态——nop-codegen 对任务**无编译依赖**，方向纪律保持）vs (b) 显式依赖边增补（须同步设计文档修订——默认拒绝）。裁定输入（live 锚点）：根 pom/nop-kernel pom exec-maven-plugin 入口形态、`CodeGenTask` 扩展点（live 盘点其任务分派机制——live 为目录驱动 `XCodeGenerator` + 特例分派，无服务式任务分派，扩展形态按 live 事实裁定）、模块边界纪律（nop-codegen ↛ nop-xlang-java，推导约束见 Current Baseline）；**ServiceLoader/ICoreInitializer 发现隐患预注**：生产运行时 `CoreInitialization` 同样会发现并运行注册的 initializer（live `CoreInitialization` 全 classpath ServiceLoader 发现）——任务入口须经 `isEnabled` 门控或独立入口形态，防止生产运行期触发生成（与 Goals"不在运行期补生成"冲突；门控先例：`CodeGenAfterInitialization.isEnabled()` 经 `CFG_CODEGEN_TRACE_ENABLED`）
- [ ] **`_gen/` 产物布局与包名策略定稿**：生产生成源码的存放路径/包结构（与模块常规编译衔接——`_gen/` 随 src/main/java 且落盘提交先例（nop-datav-dao）vs 构建输出目录先例，裁定并记录可编译性依据）；**构建时序约束显式记录**：exec-maven-plugin postcompile 在 generate-test-resources 阶段执行（compile 之后）——当轮新生成源不经本轮 compile，`_gen/` 落 src + 落盘提交（下轮编译）的先例与该时序自洽，布局裁定须含此时序说明；**同形路径折叠唯一性校验**（`EvalMethodConvention.generatedClassName` 同形冲突 → 清单侧 fail-fast，移交项落地）
- [ ] **双清单文件格式与存放路径定稿**：分离两份产物（扫描清单 should-set / 生成类清单 resourcePath→类名+树指纹）；格式候选（文本行协议 vs JSON/YAML——确定性/可 diff/classpath 资源可发现/I10 内存契约无损往返为硬要求）与存放路径（classpath 可发现位置）裁定；I10 供给缝的文件装载器接线形态定稿（**多 jar 聚合语义列为裁定输入**：多模块各自产清单时运行时聚合机制——`ClassLoader.getResources` 级聚合 vs 单文件假定，全仓铺开虽为 Non-Goal，装载器契约是否内建聚合须显式裁定防返工）
- [ ] **漏跑可观测接线形态定稿（含判别子必答）**：**张力必答**——`java-backend-enabled` 缺省 true（I9 落地）意味着任何 classpath 含 nop-xlang-java 的存量部署/测试即"java 后端启用"，若"扫描清单缺失 → markUnavailable + 全局 WARN"无条件触发，则所有未接入构建任务的部署与既有测试（含 nop-xlang-java 自身测试族，initializer 经 ServiceLoader 在 CoreInitialization 运行、测试 classpath 无清单）启动即 WARN + 不可用条目，与"缺省无产物 = 空态行为与现状一致"护栏（I9/I10）直接冲突——**判别子裁定为必答项**：区分"从未接入构建任务（合法空态，静默）"与"接入后管线漏跑（缺陷，WARN + 不可用条目）"的机制（候选：期望清单 opt-in 标记/部署形态标记/清单在场性 + 显式启用声明组合——按 live 事实裁定），红/绿测试载体随之定稿；检测时机（初始化装载时 vs 后端启用态探测）+ `markUnavailable` 调用方 + 全局 WARN 载体（消息键/级别与 I9 命名契约一致性）
- [ ] **truffle 镜像排除机制裁定**：候选形态（Maven profile 排除依赖 / classifier / 文档化部署约定）+ native-image 下 `deployment-form` 配置式标记（I9 已落）与排除机制的协同语义；不泄漏纪律（org.graalvm 依赖仅 nop-xlang-truffle）保持断言
- [ ] **native 验证载体裁定（最低可执行下界预置）**：`GraalvmConfigGenerator` 复用范围（生成类反射/资源配置增量——生成类入口为 static 直调，配置需求 live 核验后定）；"生成类直编镜像验证"的执行形态裁定**不得低于下界**：(i) closed-world 结构断言（生成类与装载路径无 `defineClass`/运行期编译/自定义 ClassLoader/janino 依赖——结构性可测）+ (ii) `GraalvmConfigGenerator` 复用接线在任务产物侧可观察；优先 (iii) 真实 native-image 构建 + 运行生成类入口的可复跑证据（仓内可用先例：`nop-kernel-cli` native profile（org.graalvm.buildtools native-maven-plugin）、`nop-quarkus-demo`（`-Pnative` + build-native 脚本，已验证）；注：nop-spring-demo 的 native profile 为注释态存留、不作可用先例；GRAALVM 环境不可得时须显式裁定并记录理由，不得静默降级为纯文档背书）；约束：本 plan 验收三项不弱化
- [ ] **端到端对拍载体裁定**：真实 `_vfs` 静态资源的承载（测试 fixture 资源集——覆盖扫描口径内纳入类型各 ≥1（含 xlib）；落点与模块可见性（java 列身份断言在 nop-xlang-java test 可见域）；**模块环约束预注**：若 fixture 为独立模块且身份断言测试落 nop-xlang-java test，test-dep fixture + fixture compile-dep nop-xlang-java = Maven reactor 环——落点裁定须消解该环（如 fixture 落 nop-xlang-java 自身 test resources，或断言测试落 fixture 模块））；**默认载体 = fixture 模块全真链路**（任务在构建管线（postcompile/exec-maven-plugin 或 `CodeGenTask` 入口）真实运行 → `_gen/` 落盘 → Maven 常规编译 → classpath 清单 → I10 供给缝装载 → 绑定执行——live 先例：nop-datav-dao `_gen/` 随 src/main/java 落盘提交 + exec-maven-plugin 模块 opt-in（pluginManagement + 模块显式声明，nop-datav-meta 先例））；**禁止自定义 ClassLoader/内存编译通路充当产物编译**（设计 java §二拒绝项 + I10 生产 binder 契约"classpath 常规加载"）；仅当全真链路被 live 事实阻断时允许显式裁定等价载体（最低限：临时目录标准 javac 编译产物 + 标准 ClassLoader 加载 + 真实管线至少运行一次的证据项），阻断理由与等价性论证记录在案
- [ ] docs 落点裁定（新模块开发指南：`xlang-and-xpl-basics.md` 增节 vs 新 owner doc；如新增/路由变化同步 INDEX/source-anchors）
- [ ] 决策记录全部落 plan Execution Notes / 当日 log

Exit Criteria:

- [ ] 扫描口径清点表 + 九项裁定（任务落点/布局/**xlib 生产形态**/清单格式/漏跑接线含判别子/truffle 排除/native 载体（含最低下界）/对拍载体（默认全真链路）/docs 落点）repo-observable，每项含候选、裁定、live 锚点
- [ ] I10 移交六项对账记录在案（偏差已修订 plan 或显式裁定）
- [ ] No owner-doc update required（本 Phase 无文档变更；docs 同步为 Phase 3 执行项）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 任务实现 + 双清单产物 + 供给闭环 + native 兼容

Status: planned
Targets: 构建任务（Phase 1 裁定落点）、`nop-kernel/nop-xlang-java/src/main/`（清单装载器/漏跑接线）、`nop-kernel/nop-xlang-truffle` pom（如裁定的排除机制）、docs 待同步项

- Item Types: `Proof`

- [ ] 任务实现：扫描（口径表）→ 与运行时相同前端取树（nop-xlang 编译前端复用，不复制逻辑——Delta 合并语义与运行时一致）→ `ExecToJavaTranslator` 转译 → `_gen/` 源码写入 + 双清单分离产物写入；**重生成幂等**（同输入重跑产物等价——等价口径 Phase 1 裁定：逐字节或语义等价断言）
- [ ] 同形路径唯一性校验落地（清单侧冲突 fail-fast + 用例）
- [ ] 运行时供给闭环：清单文件装载器 → I10 供给缝（`setStaticScanList` + binder 生产填充）；缺省无产物 = 空态行为与现状一致（I9/I10 护栏语义保持）
- [ ] 漏跑可观测接线：按 Phase 1 判别子裁定的触发条件（区分"从未接入（合法空态，静默）"与"接入后漏跑（缺陷）"）——漏跑形态 → `markUnavailable`（原因=构建管线漏跑）+ 全局 WARN；全部资源走动态路径/解释器
- [ ] native 兼容：`GraalvmConfigGenerator` 复用接线（Phase 1 裁定范围）+ truffle 镜像排除机制落地 + 不泄漏断言保持
- [ ] 单测（显式覆盖清单，guide 规则 25）：任务正/负（口径过滤逐类型/产物存在性与内容/幂等重跑断言/同形冲突 fail-fast/前端一致性——合成资源扫描产物与运行时编译树指纹一致）；装载闭环（文件 → 内存契约 → 绑定可用 → 路由命中）；漏跑可观测（红：清单缺席 + 启用 → 不可用条目 + WARN + 全解释器/动态；绿：清单在场 → 正常绑定）
- [ ] 新代码无静默跳过审查：扫描口径外类型显式排除（记录于口径表而非静默忽略）；清单数据非法（坏行/指纹缺失/类名非法）fail-fast；任务失败不产出半成品（原子性或失败标记——裁定记录）

Exit Criteria:

- [ ] 任务/产物/装载/漏跑观测/排除机制代码在仓且单测清单逐项在案（新增功能测试覆盖显式列出）
- [ ] 重生成幂等断言通过；`_` 前缀产物不可手改纪律保持（产物仅由任务生成）
- [ ] 漏跑可观测红/绿对照在案——roadmap I11 验收第二项（测试载体）
- [ ] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` 全绿（如任务涉及其它模块，`-pl` 口径 Phase 1 裁定并入）；checkstyle 退出码 0
- [ ] 依赖方向保持（nop-codegen 无 nop-xlang-java 编译依赖——Phase 1 裁定成立时断言化）
- [ ] No owner-doc update required（docs 同步为 Phase 3 执行项，Phase 边界在案）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端对拍验收 + docs-for-ai 同步

Status: planned
Targets: 端到端对拍测试（Phase 1 裁定载体）、`docs-for-ai/`（Phase 1 裁定落点）

- Item Types: `Proof`

- [ ] **端到端对拍（roadmap 验收第一项）**：真实 `_vfs` 静态资源（测试 fixture，覆盖纳入类型各 ≥1 含 xlib）经 任务扫描 → 转译 → **常规编译**（Phase 1 裁定载体：默认 fixture 模块全真链路——任务真实经构建管线运行、`_gen/` 落盘、Maven 常规编译；禁止自定义 ClassLoader/内存编译通路充当产物编译）→ 清单装载 → 加载绑定 → invoke，java 列 vs 解释器列**全量一致**（三层断言：返回值 typedEquals / scope 副作用 / 输出缓冲 / 异常语义（错误码 + SourceLocation 回映射））+ 身份断言（执行体 = 生成类绑定 artifact）——经 I1 harness 断言工具（显式引用关系可验证，非独立重写比对）
- [ ] **真实管线运行证据项**：任务经真实构建管线（postcompile/exec-maven-plugin 或 `CodeGenTask` 入口，Phase 1 裁定形态）至少完整运行一次的证据（构建日志/产物时间戳/落盘产物 diff 记录——"不绕过 codegen 管线"的可验证载体，非文字背书）
- [ ] **重生成幂等断言复跑（验收第三项）**：任务重跑产物等价断言（Phase 2 载体在验收口径下复跑记录）
- [ ] **漏跑可观测断言复跑（验收第二项）**：红/绿对照在验收口径下成立（不可用条目查询 + WARN 断言 + 路由全走解释器/动态路径断言）
- [ ] native 验证按 Phase 1 裁定载体执行并记录证据（可复跑入口 + 结果落 repo 或 log）
- [ ] docs-for-ai 新模块开发指南同步（启用方式/构建任务/双清单产物/降级诊断分级/truffle 镜像排除/部署形态标记——I9 §五 + I10 分级 + 本 plan 使用契约的统一收口）；如新增文档或路由变化同步 `docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md`；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] **I12 移交显式记录**：基准与全量收口的输入（三后端可用形态/命令口径/全量对拍套件入口/native 验证证据指针）+ Q1/Q4 watch-only 重评估触发口径的量化输入（责任链 repo-observable，落 plan Execution Notes + 当日 log）
- [ ] 全量回归（`./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` 及 Phase 1 裁定的扩展口径）+ 不削弱既有测试

Exit Criteria:

- [ ] **端到端对拍全绿（真实 `_vfs` 资源经任务全链路：扫描→转译→常规编译→装载绑定→执行；三层断言 + 身份断言；经 I1 框架断言工具）——roadmap I11 验收第一项**
- [ ] **构建管线漏跑可观测断言在案（红/绿对照）——验收第二项**
- [ ] **重生成幂等断言在案——验收第三项**
- [ ] **端到端验证**：从构建任务入口到运行时执行的完整链路可运行（任务 → 产物 → 编译 → classpath → 装载 → 绑定 → invoke → 断言）；任务经真实构建管线至少完整运行一次的证据在案
- [ ] **接线验证**：任务产物真实被运行时装载消费（装载闭环断言 = 接线证据）；漏跑检测真实调用 `markUnavailable`（红/绿因果）
- [ ] **无静默跳过**：口径外类型显式记录；非法产物 fail-fast；漏跑显式观测（Phase 2 审查项复验）
- [ ] native 验证证据在案（Phase 1 裁定载体）；truffle 排除机制落地 + 不泄漏断言全绿
- [ ] docs-for-ai 同步在仓 + link checker 退出码 0（如路由变化 INDEX/source-anchors 已同步）
- [ ] I12 移交记录 repo-observable；`ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 端到端对拍全绿（真实 `_vfs` 静态资源经任务扫描→转译→常规编译→加载绑定后 java 列 vs 解释器列全量一致 + 身份断言）——roadmap I11 验收第一项
- [ ] 构建管线漏跑可观测断言（java 后端启用但扫描清单缺失 → 注册不可用条目 + 全局 WARN）——验收第二项
- [ ] 重生成幂等断言——验收第三项
- [ ] 扫描口径清点表 repo-observable（逐类型纳入/排除 + 理由；不引入 live 不存在类型）
- [ ] 双清单分离产物落地（should-set 与生成类清单两份独立产物；同形路径唯一性校验 fail-fast 在案）
- [ ] 任务经与运行时相同编译前端取树（无前端逻辑复制）；不绕过 codegen 管线（真实管线运行证据在案）；`_` 前缀产物不可手改纪律保持
- [ ] 模块依赖方向保持（nop-codegen 无 nop-xlang-java 编译依赖；org.graalvm 依赖仅 nop-xlang-truffle——不泄漏断言全绿）
- [ ] native image 兼容项落地（GraalvmConfigGenerator 复用 + 生成类直编验证证据 + truffle 镜像排除机制）
- [ ] docs-for-ai 新模块开发指南同步 + link checker 退出码 0
- [ ] I12 移交显式记录 repo-observable
- [ ] 回归不允许削弱现解释器测试（纪律 3）；缺省无产物 = 行为与现状一致
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：closure audit 已验证（a）任务产物真实被运行时装载消费，（b）端到端 任务→产物→编译→装载→绑定→执行 连通，（c）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile`（`-pl` 按 Phase 1 裁定口径）
- [ ] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C`（及裁定扩展口径）全绿
- [ ] checkstyle / 代码规范检查通过（mission lint 口径）
- [ ] doc link checker 退出码 0（docs-for-ai 变更后）

## Deferred But Adjudicated

（起草时无新 deferred 项。存量全仓 `_vfs` 全量生成产物落盘、基准与全量收口归 I12、性能与池调优归 I12 均为 roadmap 既定归属（Non-Goals 显式排除）。执行中产生时按 guide 补录并写明 Why Not Blocking Closure。）

## Non-Blocking Follow-ups

- Q1/Q4 watch-only 触发口径量化归 I12（本 plan 提供输入，I12 移交记录承载）。

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Audit Session:
- Evidence:

Follow-up:
