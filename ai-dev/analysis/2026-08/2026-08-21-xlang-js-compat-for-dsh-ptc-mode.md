# XLang 兼容 JavaScript 以支持 DeepSeek Harness PTC 模式：工作量、必要性与实施路径分析

> Status: open
> Date: 2026-08-21
> Scope: nop-kernel/nop-xlang（文法/运行时/执行后端）、nop-kernel/nop-xlang-java（构建期转译后端）、nop-frontend-support/nop-js（GraalJS 封装）、nop-ai/*（agent/tool 治理层）
> Conclusion: （推荐，待确认）为支撑 PTC **不应**把 XLang 改造成 JS 兼容语言；推荐 Option C——基于 GraalJS（复用 nop-js 基础设施）新建 PTC Code Executor，工具调用回流 AgentToolDispatcher 治理管线，工作量约 8–14 人周。XLang 可选做小步 JS 风格语法改进，但与 PTC 解耦、另独立项。

## Context

- PTC（Programmatic Tool Calling / Programmatic Tool Use）是 DeepSeek Harness（dsh）四种 Agent preset 之一（标准 / PTC / 极简 / 创造），前身为 "Code Mode"：模型不再返回 JSON 格式的工具调用，而是返回一段 TypeScript/JS 程序（经保留工具名 `run_code` 传输执行），程序内用 `await` 编排多次工具调用、过滤与聚合结果。Anthropic、OpenAI 已各自官方支持同名机制，属生态趋势而非单家私有协议。
- 目标场景：nop-ai-agent 支持该模式——模型返回代码 → 平台在受控环境中执行 → 代码内的**每次**工具调用回流平台既有治理管线（审批 / 安全链 / 超时 / 账本）。
- 本文回答三个问题：把 XLang 语法与内置对象改到 JS 兼容（特别是 await/yield）要多少工作？是否有必要？如果要做应该怎么做？
- 仓库已有针对性铺垫：`ai-dev/references/dsh-community-articles/`（6 篇 dsh 拆解文章，2026-08-19 归档）、`ai-dev/articles/2026-08/2026-08-17-dsh-architecture-from-reversible-computation.v3.md`（作者已核实 dsh Code Mode 源码级行为）、nop-js GraalJS 模块、xlang-execution 三后端架构（见 [../../design/xlang-execution/01-architecture-baseline.md](../../design/xlang-execution/01-architecture-baseline.md)）。

## Analysis

### 1. DSH PTC 模式对执行环境的实际要求

dsh code preset 的已核实事实（`ai-dev/articles/2026-08/2026-08-17-dsh-architecture-from-reversible-computation.v3.md` §556、§764）：

- 模型代码为 TypeScript → 前缀包装式类型剥离（保行列号）→ `AsyncFunction` 构造 → 运行于 `worker_threads`（独立 V8 堆，`resourceLimits` 默认 512MiB）；
- 双超时：事件循环利用率 + 总耗时；与宿主**仅通过消息通道通信**，可从外部强制终止；
- `run_code` 为保留传输工具名，任何插件不得注册或遮蔽；
- 关键架构原则：代码内每次工具调用回宿主后，走与结构化工具调用**完全相同**的 pre-execute → 防护 → dispatch → post-execute 流水线；"PTC 改变的是模型调用工具的协议层，没有建立第二套调度循环"（`ai-dev/references/dsh-community-articles/DSH - DeepSeek Harness 架构解析.md` §510）。

由此推出对 Nop 侧执行环境的硬要求：

1. **完整 ECMAScript 语义**：模型按标准 JS/TS 训练，会自然使用 `async/await`、`Promise.all`、模板字符串 `${}`、`undefined`/`NaN`/`Infinity`、`console.log`、`JSON`、`==` 强制转换、微任务时序。任何"近似 JS"方言的偏差都会变成难调试的运行期静默缺陷（dsh 社区已有 PTC 模式工具调用缺陷的讨论，见 References）。
2. **不可信代码硬沙箱**：模型代码属第三方不可信输入，需要宿主访问封死、资源上限、墙钟/事件循环双超时、外部强制终止。
3. **不需要 generator/yield**：dsh、Anthropic、OpenAI 三家的 PTC 模式均只用 async/await + 普通函数 + console，无 generator 场景。yield 只有在"模型代码需要流式增量产出"时才需要——当前 PTC 生态没有这个要求。
4. **治理正交**：执行引擎可以换，工具调用必须回流同一治理管线。

### 2. XLang 现状与 JavaScript 的差距

#### 2.1 定位差异（先给结论性事实）

XLang 是"TypeScript 文法派生 + Java 语义"的平台 DSL，不是 JS 方言。`docs/dev-guide/xlang/xscript.md` §"从JavaScript语法中去除的特性"明确记录了**设计决策**（不是未完成项）：去除 class/prototype、去除 `undefined`（只用 null）、**去除 generator 和 async 语法**、`===`/`==` 同实现不做类型转换。

文法为 ANTLR4（`nop-kernel/nop-xlang/model/antlr/XLangLexer.g4` 825 行、`XLangParser.g4` 644 行，由 TypeScript 官方文法裁剪而来）+ 手写 `SimpleExprParser`（943 行，XPL `${}` 表达式用）。任何语法改动都要同时顾及两个解析面，且该文法是全平台所有 XPL/xlib/meta 模型的编译入口——回归面等于全平台。

#### 2.2 语法差距（LLM 代码高频项精选）

| JS 特性 | 状态 | 证据 |
|---|---|---|
| function 表达式（匿名函数） | 缺失 | `XLangParser.g4:523-525` 规则被注释 |
| 对象方法/getter/setter 简写 | 缺失 | `XLangParser.g4:473-494` 注释；且 Map 字面量无属性访问器钩子 |
| 模板字符串 `${}` 插值 | 缺失（反引号串是整体 token，转义用 `` `` ``） | `XLangLexer.g4:201` |
| 调用点 spread `f(...args)` | 缺失 | `XLangParser.g4:497-499` 无 Ellipsis 分支 |
| 赋值解构 `({a} = obj)` | 缺失 | `XLangParser.g4:207-214` 赋值左值受限 |
| `**`、`??=`/`&&=`/`||=`、标签、逗号运算符、`void`/`delete`、`0o`/`0n` | 缺失 | `XLangOperator.java:40-41` 定义了逻辑赋值枚举但零使用；标签 `XLangParser.g4:231-233` 注释 |
| `undefined`/`NaN`/`Infinity` 标识符 | 缺失（词法无此记号） | `XLangLexer.g4`/`ExprConstants.java` 全文无匹配（2026-08-21 实测）→ `x === undefined` 直接编译错误 |
| `==` 强制转换 | 语义分歧 | `==` 与 `===` 同实现（`XLangSemantics.java:179-184`），数值跨类型等值、无 JS 隐式转换 |
| `this` | 语义分歧 | 重写为作用域变量 `"this"`（`LexicalScopeAnalysis.java:540-543`），无动态接收者 |
| `new` | 语义分歧 | 仅限导入的 Java 类型，构造 Java 对象（`XLangParser.g4:536`） |
| try/catch 吞异常恢复 | 语义分歧 | catch 体执行后**必然重抛**（`TryExecutable.java:47-57` 实测：`executor.execute(catchExpr, rt); throw NopException.adapt(e);`） |
| switch fallthrough | 语义分歧 | 每个 case 是独立块，无贯穿（`XLangParser.g4:216-229`） |
| 块级作用域 | 比 JS 严 | 嵌套块禁 shadow（`exprs/lexical-scope.test.md`） |
| 箭头函数、`?.`、`??`、解构声明、字面量 spread、for-of、regex 字面量 | 支持 | 详见语法矩阵（本次调研全量核对过，此处列高频支持项） |

#### 2.3 内置对象差距

| JS 内置 | 状态 | XLang 现状 |
|---|---|---|
| `console.log` | 缺失 | `logInfo` 等全局函数且消息须字面量模板（`LogFunctions.java:45-68`） |
| `JSON.parse/stringify` | 改名 | `$JSON.*`（`EvalGlobalRegistry.java:44-58`） |
| `Math` | 部分 | `$Math`，round/sign/trunc 语义不同（`MathHelper.java:712-1170`） |
| `Date` | 无 JS 形态 | `now()/today()/$Date.*`（Java 时间类型） |
| `isNaN`/`parseInt`/`parseFloat`/`encodeURIComponent`/`setTimeout` | 缺失 | 无全局注册（`parseInt` 需 `$String.parseInt(s,radix)`） |
| Array 方法（map/filter/reduce/push/pop/splice/slice/includes…） | 基本齐 | `ListFunctions`/`SetFunctions`/`ArrayAdapterFunction` 扩展方法，闭包经 `EvalFunctionalAdapter` 适配 |
| String 方法 | 大半 Java 语义 | `includes` 缺、`padStart` → `$leftPad`、`split` 正则语义、`replace` 字面量语义 |
| 数值体系 | 分歧 | Integer/Long/BigInteger/Double/BigDecimal 提升（`MathHelper.getNumericType`），非 JS 全 double；`1/2=0.5` 已 JS 化；除零得 NaN 非 Infinity |

#### 2.4 await / yield 专项分析

**await——缺的不是语法，是整个异步执行契约：**

- 词法已备：`Async: 'async'; Await: 'await';`（`XLangLexer.g4:139-140`）；AST 已备：`AwaitExpression` 节点 + 类型推断 hook（`TypeInferenceProcessor.java:1207-1218`）。但文法无任何规则、无代码路径构造该节点 → `await x` 今天是语法错误，`processAwaitExpression` 是占位直通（`BuildExecutableProcessor.java:1505-1507`）。
- 执行契约 100% 同步：`IExecutableExpression.execute(...)` → `Object`、`IEvalFunction.call*` → `Object`、`EvalBackendRouter` 三种结果都同步取值。树走解释器、构建期转译 Java（`EvalMethodConvention` 生成 `static Object execute(...)` 同步签名）、Truffle 后端全部假设同步栈执行。无 Promise、无事件循环、无微任务队列；`EvalRuntime` 甚至不携带取消令牌（`ICancelToken` 只存在于 AI 层和任务图层）。
- 改造代价集中在契约而非文法：真 async 要么全量 CPS 化（触及全部 120+ `exec/*Executable`、ExitMode 非局部控制流跨挂起点、try/finally 跨挂起点、闭包捕获挂起帧），要么三后端各自引入异步变体。**且 `nop-xlang-java` 的 binder/树指纹/路由正处于 `ai-dev/plans/xlang-execution-optimization`（I10/I11）活跃改造期**（本分析当日 git status 可证），动核心同步契约会与在役计划直接撞车。

**yield——从零开始，且 PTC 不需要：**

- `Yield` token 注释（`XLangLexer.g4:162`）、文法规则全注释（`XLangParser.g4:199-201,569`）、无 `YieldExpression` AST 节点。
- XPL 输出是 push 模型（`IEvalOutput`），无 pull/惰性生产者。generator 需要 CPS 或字节码生成（仓库有 janino 可依），估计 8–16 人周。
- 判定：dsh / Anthropic / OpenAI 的 PTC 均不用 generator；若未来确需模型代码流式增量产出，GraalJS 原生支持 generator 与 async generator，不应成为给 XLang 加 yield 的理由。

#### 2.5 nop-ai-agent 侧的集成点（无论选哪条路都复用）

- 执行器扩展点：`AgentExecutorResolver.resolveExecutor` 按 `AgentModel.getMode()` 分支（react/single-turn/plan），新增 `ptc` 分支即设计内扩展（`nop-ai/nop-ai-agent/.../engine/AgentExecutorResolver.java:139`）。
- 工具 presentation：`AgentPromptAssembly.assembleExecutionSetup` + `options.setTools(...)`（`AgentPromptAssembly.java:259,273`）——PTC 模式下替换为系统提示 + 生成的工具 SDK。
- 响应解释：模型代码以纯文本 `ChatAssistantMessage` 到达（各 dialect 已解析），替换 `ReActAgentExecutor.extractToolCalls`（`ReActAgentExecutor.java:1107`）为代码块抽取 + 本地执行。
- 治理回流：`AgentToolDispatcher.executeAllowedCalls`（`AgentToolDispatcher.java:165`）+ `SecurityCheckpointChain` 七类拒绝路径 + `IToolManager.callTool` 拦截器链 + per-tool 超时与结果溢写——代码内每次工具调用应逐次进入这里。
- 沙箱缝隙：`ISandboxBackend`（NoOp/ Docker 两实现）已存在；`ReActAgentExecutor.getSandboxBackend()` 的 javadoc（`ReActAgentExecutor.java:329-336`）已明确预留 "future shell-exec / code-exec IToolExecutor successors"。
- nop-js 现状：GraalJS（`org.graalvm.polyglot:js-community` 23.1.2）已封装 worker 池 + `invokeAsync` → `CompletableFuture`（`JavaScriptService.java:67-93`）。**但当前 Context 是全开配置**：`allowHostClassLoading(true)` + `HostAccess.ALL` + `allowIO(true)` + `allowHostClassLookup(→true)`（`JavaScriptWorker.java:344-356`）——定位是"可信内部 JS 引擎"，与 PTC 需要的封死沙箱相反，需新建受限 Context 工厂而非复用该配置。

### 3. 方案对比

#### Option A：XLang 全面 JS 兼容（真 async/await，含语义对齐）

- 核心思路：补齐 2.2/2.3 全部差距 + 引入异步执行契约 + yield。
- 工作量明细：

| 工作项 | 估计 | 依据 |
|---|---|---|
| 语法补齐（var/函数表达式/方法简写/模板插值/调用 spread/赋值解构/`**`/逻辑赋值/标签/逗号/delete/void/0o/0n…，双解析面 + corpus 测试） | 6–10 人周 | ANTLR 文法 + 重新生成 11k 行 parser + `SimpleExprParser` + LSA/BEP 两级 |
| 语义对齐且不破坏平台（undefined 三值逻辑、JS 数值塔、`==` 强制转换、catch 不重抛、this 绑定——必须做成 dialect 门控双语义，否则全平台 XPL 回归） | 8–16 人周 | `MathHelper`/`XLangSemantics`/`TryExecutable` 语义是全平台在用行为 |
| 真 async/await（CPS 或异步契约，三后端 + ExitMode + try/finally + 闭包捕获） | 12–24 人周 | 与 xlang-execution-optimization（I10/I11）在役改造直接冲突 |
| yield/generator | 8–16 人周 | token/AST/运行时全零起步 |
| XLang 沙箱 profile（禁 Java import/new/`g_`/`$beanProvider`、EvalRuntime 引入取消令牌、循环与分配上限、墙钟超时） | 4–8 人周 | 现 eval 路径无任何取消/限额机制（实测 `EvalRuntime` 不含 `ICancelToken`） |
| **合计** | **38–74 人周（约 9–18 人月）** | |

- 优点：单一语言栈；模型代码享受 XLang 编译期校验/后端优化。
- 缺点：逆转 `xscript.md` 记录的显式设计决策；同步契约是三后端与在役优化计划的根基；XLang 的 Java interop（import 任意类、`new`、反射方法解析）对不可信代码是攻击面，封死它又削弱 XLang 自身价值；**"近似 JS"永远差最后一公里**（微任务时序、prototype 链没有、也不该有）。

#### Option B：XLang JS-lite 方言（js-compat 编译 profile + 虚拟线程阻塞式 await）

- 核心思路：不动全局语义，新增 `js-compat` 编译 profile（`ExprFeatures` 门控 + ANTLR 谓词，`supportCpExpr()` 是现成先例）——profile 内补语法糖、`await x` 编译为阻塞 join 的 `AwaitExecutable`（脚本跑虚拟线程，超时/取消经中断）、`Promise.all` 做成宿主并行 shim、配沙箱 profile。
- 工作量：门控机制 1–2 人周；语法子集（调用 spread、函数表达式、模板 `${}`、赋值解构、`**`、逻辑赋值、`String.includes`、`console`/`JSON` 别名）3–5 人周；阻塞式 await + Promise.all shim 2–4 人周；沙箱 profile 3–5 人周；测试 2–3 人周 → **合计 11–19 人周（约 3–5 人月）**。
- 优点：不触碰三后端契约；XLang 生态内闭环。
- 缺点：语义分歧依旧（undefined/`==`/数值/catch 重抛），靠提示词约束模型写"受限 JS"——与"模型按标准 JS 训练"这个前提正面冲突，失败模式是运行期静默偏差；无真实微任务并发（`Promise.all` 只是宿主并行 join）；本质是造一个质量存疑的 JS 山寨方言。**不推荐为 PTC 走这条路**。

#### Option C：内嵌 GraalJS 的 PTC Code Executor（推荐）

- 核心思路：新建受限 GraalJS Context 工厂（不碰 nop-js 现有全开配置），模型代码以标准 JS 执行（await/Promise/console/JSON 全部原生），工具经 Value 代理暴露为 guest 函数，每次调用回流 `AgentToolDispatcher` 治理管线。这正是 dsh 自身做法的 Nop 对应物：dsh 让模型代码跑在平台原生引擎（Node/V8 worker）上，JVM 平台的原生对应就是 GraalJS，而非把 DSL 改成 JS。
- 工作量明细：

| 工作项 | 估计 | 说明 |
|---|---|---|
| Phase 0 spike：`CompletionStage` ↔ JS Promise 桥（关键路径，见下） | 1 人周 | 两设计对拍：事件循环泵 vs AsyncFunction+阻塞 RPC；验证 Promise.all 并行性、超时、取消、上下文内存 |
| Phase 1：CodeRunService——受限 Context 工厂（HostAccess 显式白名单、无 IO/类加载/类查找、墙钟超时 + 外部强制 close、内存/语句限额）+ console 捕获进会话转录 + guest prelude + js-community 版本对齐（23.1.2 → 25.x，与 truffle 25.2.4 统一） | 2–4 人周 | `JavaScriptWorker.java:344-356` 的反面配置即需求清单；版本配对参照 [2026-08-16-truffle-graalvm-ecosystem-research.md](2026-08-16-truffle-graalvm-ecosystem-research.md) |
| Phase 2：工具桥 + 治理回流（guest 每次调用 → `AiToolCall` → `AgentToolDispatcher.executeAllowedCalls` 同管线：审批、安全链、per-tool 超时、账本、结果溢写；`ICancelToken` 贯通到 Context close） | 1–2 人周 | 复用 [../../design/nop-ai-agent/04-tool-invocation.md](../../design/nop-ai-agent/04-tool-invocation.md) 全部机制 |
| Phase 3：`ptc` executor 模式（`AgentExecutorResolver` 新分支 + 系统 prompt/工具 SDK 生成 + 代码块抽取 + console/进度事件流） | 2–3 人周 | dsh 四 preset 共用 loop、只换 presentation 的架构同构 |
| Phase 4：对拍回归（deepseek-chat/reasoner 的 PTC prompt 适配；TS 策略：先提示词约束纯 JS） | 1–2 人周 | |
| **合计** | **8–14 人周（约 2–3.5 人月）** | 与 XLang 核心零耦合 |

- 关键技术点（Phase 0 要裁决的）：GraalJS 无内置事件循环。桥接两条路——(i) **事件循环泵**：单线程独占 Context，宿主函数返回真 Promise 代理，工具 future 完成后调度回 Context 线程 settle + drain 微任务（`js.interop-complete-promises` 选项相关，约数百行 + 测试）；(ii) **DSH 式阻塞 RPC**：guest 函数内阻塞 `CompletableFuture.join(timeout)`，实现最简但 `Promise.all` 会串行化（除非开启多线程 Context 访问）。倾向 (i)，spike 定案。
- 优点：标准 ECMAScript（await/yield/generator 全原生，未来若 PTC 需要 generator 免费获得）；GraalJS 沙箱选项成熟（dsh 的 worker 隔离 + 双超时在 JVM 上的对应物齐备）；性能对 PTC 负载足够（脚本短、主要等工具 I/O，stock JVM 上纯解释执行可接受，GraalVM 运行时下有 JIT）；与在役 XLang 计划零冲突；与仓库已押注的 GraalVM/Truffle 方向（nop-xlang-truffle、nop-js）同向。
- 缺点：引入 guest 运行时运维面（Context 生命周期/内存池化、native-image 打包注意点）；工具桥与治理管线之间的会话上下文传递需要设计；依赖 GraalJS 版本升级节奏。

#### Option D：JS→XLang 转译器（否决）

需要完整 ES parser + 把 undefined/prototype/method-receiver 语义降落到 Java 对象模型上——无处安放的语义比缺语法更致命，等于重写一个 JS 引擎前端再丢弃其运行时。否决。

#### Option E：Node sidecar 直接复用 dsh 执行内核（否决）

部署面引入 Node 运行时与进程管理，违背平台单 JVM（含 native-image）交付形态；且治理回流要跨进程协议。除非未来明确要 dsh 插件生态互操作，否则否决。

#### Comparison

| 维度 | A 全面兼容 | B JS-lite | C GraalJS（推荐） |
|---|---|---|---|
| JS 语法覆盖 | 高（做完的话） | 中（语法糖级） | 完整（标准 ES） |
| await | 真·CPS（贵） | 阻塞 join 近似 | 原生 |
| yield/generator | 从零造 | 无 | 原生（PTC 暂不需要） |
| 内置对象 | 需逐个补 | 别名近似 | 原生 |
| 沙箱强度 | 需新建且与 interop 定位冲突 | 需新建 | 引擎级成熟选项 |
| 对现有平台风险 | 极高（文法/语义全平台共用） | 中（profile 门控） | 低（新增模块） |
| 与 I10/I11 冲突 | 直接撞车 | 低 | 零 |
| 工作量 | 9–18 人月 | 3–5 人月 | 2–3.5 人月 |

### 4. 必要性评估

**为 PTC 把 XLang 改成 JS 兼容：没有必要。** 理由按权重排序：

1. **语义陷阱不可收敛**：模型按标准 JS 训练，`x === undefined` 在 XLang 直接编译错（词法无 undefined）、`try/catch` 不吞异常、`==` 不转换、数值塔不同——这些不是缺语法，是设计决策（`xscript.md` 明文）。补齐 = 逆转决策且必须 dialect 门控双语义，复杂度爆炸。
2. **await 的真实成本在执行契约**：三后端 + ExitMode + 同步签名 + 在役 I10/I11 改造，是全平台最不该为一个展示层功能去动的地基。
3. **不可信代码需要的是硬沙箱而不是更强 DSL**：XLang 的价值恰在 Java interop 与编译期宏，对模型代码这全是攻击面。
4. **仓库已有正确资产**：nop-js/GraalJS、xlang-truffle、GraalVM 生态调研、DSH 源码级拆解——Option C 是顺着已有投资的延长线。
5. **dsh 自身就是这么选的**：模型代码跑平台原生引擎（V8 worker），没有人为它改造宿主 DSL。

**XLang 小步 JS 风格改进（Option B 的语法子集）**：对人写模板/规则有独立价值（调用 spread、`String.includes`、`$JSON`/`JSON` 别名等），建议作为独立低优先级改进项评估，与 PTC 解耦——不要用 PTC 作为它的立项理由。

**yield**：为 PTC 加 yield 没有任何必要；仅当未来出现"模型代码流式增量产出"的真实需求时，走 GraalJS 原生 generator，不走 XLang。

**PTC 本身值得做**：一次往返编排多工具、token/延迟收益、Claude/OpenAI/DSH 三方生态已定型；且 Nop 的治理正交设计（见 `ai-dev/articles/2026-08/2026-08-17-dsh-architecture-from-reversible-computation.v3.md` §556 的结论"能力越强、入口形态越多，治理就越要正交于能力"）正好是 PTC 需要的承接形态。

## Conclusion

- 推荐 Option C：基于 GraalJS 的新 PTC Code Executor（受限 Context + Promise 桥 + 工具治理回流 + `ptc` executor 模式），工作量 8–14 人周，先做 1 人周 Phase 0 spike 裁决 Promise 桥设计。
- 被否决的方案：Option A（XLang 全面 JS 兼容，9–18 人月、逆转显式设计决策、撞车在役优化计划、语义不可收敛）；Option D（JS→XLang 转译，语义无处安放）；Option E（Node sidecar，破坏单 JVM 交付）。Option B 降级为"独立低优先级语法改进项"，不作为 PTC 路线。
- await/yield 专项裁定：await 走 GraalJS 原生（XLang 侧阻塞式近似方案仅存档备查）；yield 判定为 PTC 不需要，不实施。
- 后续工作：确认后立 `ai-dev/design/nop-ai-agent/` PTC code-executor 设计文档 + Phase 0 spike plan。

## Open Questions

- [ ] GraalJS（js-community 23.1.2 与升级目标 25.x）的微任务 drain 时机与 `js.interop-complete-promises` 行为需 Phase 0 spike 实测定案（事件循环泵 vs 阻塞 RPC）。
- [ ] js-community 构件许可/再分发条款复核（衔接 [2026-08-16-truffle-graalvm-ecosystem-research.md](2026-08-16-truffle-graalvm-ecosystem-research.md) 的许可结论与 25.x 版本配对 Open Question）。
- [ ] dsh PTC preset 的系统提示词/工具 SDK 生成格式是否有稳定规范可对齐，还是需逆向跟随其版本演进。
- [ ] 模型输出 TypeScript 的比例与剥离策略：先提示词约束纯 JS 是否足够，必要时评估嵌入 type-stripper 的成本。
- [ ] PTC 每轮代码新建 vs 池化复用 GraalJS Context 的内存/延迟权衡。
- [ ] XLang 小步 JS 风格改进（与 PTC 解耦后）是否单独立项、优先级如何。

## References

- `ai-dev/articles/2026-08/2026-08-17-dsh-architecture-from-reversible-computation.v3.md`（DSH Code Mode 源码级事实）
- `ai-dev/references/dsh-community-articles/DSH - DeepSeek Harness 架构解析.md`（四 preset 与 PTC 协议层定位）
- `ai-dev/analysis/2026-08/2026-08-16-truffle-graalvm-ecosystem-research.md`（GraalJS/Truffle 版本、许可、native-image 结论）
- `ai-dev/analysis/2026-08/2026-08-14-deepseek-harness-vs-reversible-comparison.md`
- `ai-dev/design/xlang-execution/01-architecture-baseline.md`（三后端与路由决策树）
- `ai-dev/design/nop-ai-agent/04-tool-invocation.md`（工具调用治理管线）
- `docs/dev-guide/xlang/xscript.md`（XScript 刻意移除的 JS 特性清单）
- 源码证据：`nop-kernel/nop-xlang/model/antlr/XLang{Lexer,Parser}.g4`、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/AwaitExpression.java`、`.../exec/TryExecutable.java`、`.../backend/EvalBackendRouter.java`、`nop-kernel/nop-xlang-java/.../translator/ExecToJavaTranslator.java`、`nop-frontend-support/nop-js/.../engine/JavaScriptService.java`、`JavaScriptWorker.java`、`nop-ai/nop-ai-agent/.../engine/{AgentExecutorResolver,AgentPromptAssembly,AgentToolDispatcher,ReActAgentExecutor}.java`
- 外部资料：[Anthropic: Programmatic tool calling](https://platform.claude.com/docs/en/agents-and-tools/tool-use/programmatic-tool-calling)、[OpenAI: Programmatic tool calling](https://developers.openai.com/api/docs/guides/tools-programmatic-tool-calling)、[deepseek-ai/deepseek-harness Releases（PTC 更名记录）](https://github.com/deepseek-ai/deepseek-harness/releases)、[DSH discussion #1605：PTC 模式工具调用缺陷](https://github.com/deepseek-ai/deepseek-harness/discussions/1605)、[AWS Bedrock PTC 实现博客](https://aws.amazon.com/blogs/machine-learning/implementing-programmatic-tool-calling-on-amazon-bedrock/)、[open-ptc-agent](https://github.com/Chen-zexi/open-ptc-agent)、[DeepSeek Harness developer preview](https://deepseek.com/harness/en/)
