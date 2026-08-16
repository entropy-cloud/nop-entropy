# Truffle 框架深度知识（为实现 nop-xlang Truffle 运行时）

**日期**：2026-08-16
**范围**：Truffle 框架执行模型、核心 API、多线程模型、DSL 编写规范、SimpleLanguage 参考地图、XLang 映射草案
**状态**：active（知识层文档，实现 nop-xlang Truffle 运行时的通用知识基座）
**事实来源**：`~/sources/graal`（oracle/graal sparse clone，`truffle/` + `sdk/` 模块）一手源码与 javadoc；引用处标注文件路径与行号

---

## 一、一页结论

1. Truffle 的性能来自**自修改 AST 解释器 + partial evaluation (PE)**：节点首次执行时做类型画像并"自我改写"为特化节点，热点 CallTarget 被运行时 Graal 编译器内联展开成机器码。**只有跑在 GraalVM 上才有 JIT**；stock JVM 上纯解释执行。
2. 实现一门 Truffle 语言 = 实现一组 `Node` + 一个 `TruffleLanguage` 子类。没有 parser 也可行（`Source` 可为合成源或直接程序化构造 AST）——**nop-xlang 已有自己的编译前端（宏/标签全部编译期展开为 Executable 树），Truffle 层只承担"执行后端"职责**。
3. 多线程的正确打开方式有两条路：
   - **路 A（推荐起步）**：host 侧每线程一个 `polyglot.Context`，显式共享同一个 `Engine` → 编译代码与 parse 缓存跨线程复用，语言实现无需支持并发 AST 访问；
   - **路 B（进阶）**：单 Context 多线程并发执行，要求语言覆写 `isThreadAccessAllowed` 返回 true 并保证节点线程安全，且**该 Context 内所有已初始化语言**都允许（混合语言时易踩坑）。
4. 语言上下文共享策略由 `ContextPolicy` 决定（EXCLUSIVE → REUSE → SHARED，共享程度递增）。**注意 REUSE ≠ 池化共享**：REUSE 是"context 销毁后回收复用 language 实例"，同一时刻一个实例只服务一个存活 context（TruffleLanguage.java:4306-4310）；**多个同时存活的 Context 要共享 AST/parse 缓存/JIT，只有 SHARED 一条路**（AOTOverview.md:32-34）。官方建议手写语言从 EXCLUSIVE 起步、成熟后升 SHARED（SLLanguage.java:552；SL 本身已是 SHARED，L226）。**xlang 的翻译 AST 来自无状态 Executable 树**（纯数据、宏全展开、不含运行时值），context-independent 准则大部分可由翻译器设计自动满足——但准则 2（两级内联缓存）需显式设计、准则 4（节点不存 context 数据）靠翻译纪律保证（SL 也做了真实适配），见 §4.3 准则清单，仍是 SHARED 起步的好候选。
5. 依赖形态：`org.graalvm.truffle:truffle-api` + 注解处理器 `truffle-dsl-processor`（编译期生成特化子类与 provider 注册文件），嵌入侧用 `org.graalvm.polyglot:polyglot`。语言注册**不依赖注解扫描**——DSL 处理器在编译期生成 `META-INF/services/...TruffleLanguageProvider`（SL 源码 META-INF 下无手写 services 文件，只有 native-image.properties）。
6. Native image 内**不能**运行 Truffle 语言（无运行时 JIT，且官方不支持该组合）——本知识库服务的场景是 **JVM 部署形态下的 XLang 提速**，与 native image 路线（构建期转译 Java）互斥互补。

---

## 二、Truffle 执行模型

### 2.1 从解释到机器码的管线

```
parse → AST(Node 树, 每 RootNode 包装为 CallTarget)
  │
  ├─ 冷执行: 树遍历解释器 + 运行时画像(类型/分支/内联缓存)
  │     └─ 节点自我改写(node rewriting): 泛型节点 → 类型特化节点
  │
  ├─ 热点(默认阈值: FirstTier=400次 / LastTier=10000次, Options.md:86/88;
  │     DynamicCompilationThresholds 默认开启, 依编译队列负载动态缩放)
  │     └─ 运行时 Graal 编译器对 CallTarget 做 partial evaluation:
  │        解释器循环被内联, AST 常量折叠, 画像信息变成机器码里的
  │        直接类型检查与分支 → 每 CallTarget 一份机器码
  │
  └─ 画像失效(遇到新类型等) → deopt → 回解释器 → 重新特化/重编译
```

关键点：

- **CallTarget 是编译根**（经 `RootNode.getCallTarget()` 惰性获取，RootNode.java:446）。跨函数内联由 PE 在编译期完成，不是运行时虚调用。
- **AST 在首次执行后应当不可变**（除画像字段）。PE 把整棵子树当常量折叠，节点字段在编译后变更 = deopt。
- 分层编译：Tier 1 快速编译（Truffle tier + Graal tier 分开计时，见 `--engine.TraceCompilation` 输出格式，Optimizing.md:44-79）；编译在后台线程异步进行（`--engine.BackgroundCompilation=true` 默认开），编译线程数默认随 CPU 核数（`--engine.CompilerThreads`）。
- **splitting**：同一函数被多态调用点共享导致内联缓存失稳时，Truffle 可克隆整棵子树按调用点分裂（`RootNode.isCloningAllowed`），是控制内联缓存 polymorphism 的机制性手段。

### 2.2 内联缓存（性能的核心机制）

- DSL 的 `@Specialization` + `@Cached` 生成的就是**内联缓存式节点**：首次执行锁定一个特化实现 + 缓存值，后续执行退化为一次类型检查 + 直达代码。
- `@CachedLibrary`（如 `InteropLibrary`）为互操作消息做同样的缓存，`limit = "3"` 控制缓存槽位数，超限进入 generic/fallback 状态（避免缓存爆炸）。
- XLang 对应物：现有解释器的 `MathHelper`/方法分派按接收者类型走 Map 查找——在 Truffle 里应改造为"缓存 + guard"模式。

### 2.3 帧模型（Frame）

| 概念 | 职责 | XLang 对应物 |
|---|---|---|
| `FrameDescriptor` | 每个 RootNode 一份，声明全部 slot（可标注 primitive kind 帮助 PE 消装箱） | `LexicalScopeAnalysis` 产出的 slot 布局 |
| `FrameSlot` | 编译期分配的变量槽位（按名索引） | `SlotIdentifierExecutable` 的 slot 下标 |
| `VirtualFrame` | 执行期帧（编译器可见，PE 可消除读写） | `EvalFrame(Object[] stack)` |
| `MaterializedFrame` | 物化帧（逃逸/跨调用存活，如闭包捕获） | 闭包捕获的 `EvalScope` |
| 帧访问模式 READ/WRITE/MATERIALIZE | 声明节点的帧使用方式，帮助编译器优化 | — |

规则：能进 VirtualFrame 的变量不要放语言 context；帧 slot 类型单调升级（monotonic）以减少重新检查。

---

## 三、核心 API 速查

### 3.1 语言生命周期

```java
@TruffleLanguage.Registration(
    id = "xl", name = "XLang", defaultMimeType = "application/x-xlang",
    contextPolicy = ContextPolicy.SHARED /* 目标值；正确性验证期可暂用 EXCLUSIVE 单 Context 对拍 */)
public final class XLangLanguage extends TruffleLanguage<XLangLanguageContext> { ... }
```

- `TruffleLanguage<C>` 钩子：`createContext(Env)` / `initializeContext` / `finalizeContext` / `disposeContext`；`parse(ParsingRequest)` 返回 `CallTarget`（parse 缓存按 language instance 作用域，ContextPolicy javadoc L4250-4252）。
- `TruffleLanguage.Env`：宿主交互门面（`asValue`/`lookupLanguage`/`out()`/`createThread`/`newTruffleContext`/`submitThreadLocal`…），只应在语言实现内使用。
- `ContextReference<C>`（`lookupContext()`）在节点内取回语言 context；`LanguageReference` 取回 language 实例。**context/language 引用经由 `@Bind` 注入节点方法**。可折叠性（PE 常量折叠）须区分：`LanguageReference` 在 sharing layers 下保证折叠为常量（TruffleLanguage.java:4261-4262）；**`ContextReference` 仅当 engine 内只有一个 context 实例时才折叠**（TruffleLanguage.java:4207-4211）——SHARED 多 context 模式下不折叠，节点内频繁取 context 有真实开销，这是 context-independent 代码变慢的原因之一。
- 注册机制：`@Registration` 注解由 `truffle-dsl-processor` 在**编译期**生成 provider 服务文件，无反射扫描——这与 Nop 平台"beans.xml 显式注册"的思路同构，无 NopIoC 冲突。

### 3.2 节点与调用

- `Node`：`execute(VirtualFrame)` 约定方法族；`Node.replace(newNode)` 触发自改写（DSL 生成代码自动处理同步）。
- `RootNode`：函数体根，持有 `FrameDescriptor`、`getName`、`getSourceSection`；`getCallTarget()` 惰性创建并缓存 CallTarget。
- 控制流用**异常**实现非局部跳转（SL 的 `SLReturnException`/`SLBreakException` 模式），XLang 的 `ExitMode`（`CONTINUE`/`BREAK`/`RETURN`，见 `nop-kernel/nop-core` 的 `io.nop.core.lang.eval.ExitMode`）按同样模式映射。注意：Truffle 侧也有一个同名 `TruffleLanguage.ExitMode`（NATURAL/HARD，指 Context 退出模式），与 xlang 的控制流 ExitMode **无关**，勿混淆。
- `@TruffleBoundary`：切出 PE 边界（`BigInteger.add` 这类不可 PE 的调用必须标注，SLAddNode.java:131 有示范；`allowInlining=true` 允许 Graal 侧再内联）。
- `@HostCompilerDirectives.InliningCutoff`：慢路径切块，控制编译产物体积。
- `@GenerateUncached`：生成无实例缓存版本（供 instrumentation/_uncached 调用路径）。
- `@GenerateInline`：缓存子节点作为字段内联进父节点，减小 footprint。

### 3.3 嵌入侧（org.graalvm.polyglot）

```java
Engine engine = Engine.newBuilder().option("engine.TraceCompilation", "true").build(); // 进程内单例
Context ctx = Context.newBuilder("xl").engine(engine).allowAllAccess(true).build();
Value fn = ctx.eval(source);
fn.execute(args);
```

- `Engine` 是代码共享的作用域：多个 Context 显式传同一 engine → 共享已编译代码、parse 缓存、instrument。Engine.java:137 原文只明说 instruments 共享；代码/parse 缓存共享的权威依据是 AOTOverview.md "Code sharing within the same Isolate/Process" 一节。
- `Context.enter()/leave()`：在当前线程显式进出 context，消除每次调用的进出开销（可重入，Context.java:795-803）。

---

## 四、多线程模型（本文档重点）

### 4.1 Context 的线程规则（权威原文，Context.java:288-297）

> 单线程使用 Context 安全；多线程**串行**（不同时）使用也安全；多线程**同时**使用取决于所有已初始化语言是否支持多线程——支持则可并发执行，否则抛 `IllegalStateException`。

含义：**并发能力是 Context 级别的全有或全无**，由其中所有语言共同决定（纯 host 嵌入时也要注意混入的语言，如 JS 默认支持、SL 不支持并发）。

### 4.2 语言侧多线程钩子（TruffleLanguage.java）

| 钩子 | 触发时机 | 默认行为 |
|---|---|---|
| `isThreadAccessAllowed(Thread, boolean singleThreaded)` | 任意线程访问 context 前 | `return singleThreaded`（L1318-1320，**默认拒绝并发**） |
| `initializeMultiThreading(C)` | 首次并发访问前（所有语言都允许才会触发） | 空实现 |
| `initializeThread(C, Thread)` | 每个新线程首次进入 context 前（串行多线程也会触发） | 空实现；存 Thread 需 WeakReference |
| `finalizeThread(C, Thread)` | 线程最后一次离开前（23.1+） | 空实现 |
| `disposeThread(C, Thread)` | 线程与 context 解绑时 | 空实现 |

### 4.3 ContextPolicy 与代码共享

| Policy | 语义 | 对 AST 的要求 |
|---|---|---|
| `EXCLUSIVE`（默认） | 每个语言 context 一个 language 实例 | context 可直接存节点字段（仍建议 ContextReference） |
| `REUSE` | context 销毁后复用 language 实例（AST 跨 context 复用但默认不同时） | 必须用 ContextReference；若语言不允许并发（默认），language 实例仍保证单线程使用；若语言允许并发则 AST 亦可被多线程同时执行（条件保证，见 javadoc L4317-4322） |
| `SHARED` | 一个 language 实例服务多个语言 context，**AST 可被多线程同时执行**（无论 isThreadAccessAllowed） | language 实例字段只能放可共享数据且可变部分需同步；节点不得持有 context 数据 |

代码共享的开关在**嵌入侧**：`Context.newBuilder().engine(sharedEngine)` 显式共享，或启用 `--engine.CacheStore`。共享时框架先回调 `initializeMultipleContexts()` 再创建任何 context（TruffleLanguage.java:4267-4270）。

**context-independent code 准则清单**（AOTOverview.md:49-62，原文 6 个 bullet，此处归纳为五条要点）：

1. 多 context 模式下禁止对运行时**值身份**做推测（必然二次 deopt）；
2. 函数内联缓存做两级：一级缓函数实例身份、二级缓 CallTarget；多 context 模式必须禁用一级；
3. DynamicObject 根 Shape 存 language 实例而非 context；
4. Node 实现不得存储 context 相关数据结构或运行时值；
5. 源码加载/解析一律走 `Env.parse`（享受按 language 实例作用域的 parse 缓存）；Assumption 存 language 实例而非 context。

代价：context-independent 代码因不能对值身份做常量折叠，编译产物**慢于**单 context 版本——这是"一份代码多处跑"换来的（AOTOverview.md:58-61 明说）。

### 4.4 两种并行架构选型（xlang 运行时的关键决策）

| 维度 | 路 A：每线程一 Context + 共享 Engine | 路 B：单 Context 多线程并发 |
|---|---|---|
| 语言要求 | `ContextPolicy=SHARED`（跨存活 Context 共享 AST/JIT 的唯一途径）+ context-independent 准则；节点无需支持并发访问 | `isThreadAccessAllowed→true` + 节点全线程安全 |
| 隔离性 | 语言 context 天然隔离（各线程独立全局状态） | 共享语言 context（全局状态需自行同步） |
| JIT 代码复用 | ✅ 同一 Engine 内共享（SHARED 下 parse 一次、AST 一份） | ✅ 同一份 AST |
| XLang 适配成本 | 低：EvalScope/全局变量本来就是"每次求值独立作用域链" | 高：共享 EvalScope 的 HashMap 链需重设计为并发结构 |
| 语义贴合 | **贴合**：nop 的 IEvalScope 求值模型 = 无共享可变状态假设 | 需重新审视 xpl 输出缓冲（IEvalOutput）等线程绑定资源 |
| 风险 | Context 创建开销（池化缓解）；context-independent 准则须遵守 | DSL 生成的节点改写在高并发下的争用；调试困难 |

**推荐**：路 A 起步（Context 池 + 共享 Engine + `contextPolicy = SHARED`）。理由：池化并发下多个 Context 同时存活，跨 Context 复用 AST/parse 缓存/JIT 只有 SHARED 可选（REUSE 仅支持"销毁后回收"，EXCLUSIVE 无任何复用）；而 SHARED 的代价——context-independent 准则与"禁值身份推测"——对 xlang 影响很小，因为翻译 AST 源自无状态 Executable 树、slot 布局编译期已定，本来就不依赖运行时值身份。函数调用内联缓存按准则做两级（一级 CallTarget 身份，不缓函数实例身份）。保守的过渡路径：**正确性验证阶段**用 EXCLUSIVE 单 Context 与现解释器对拍（不追求共享），验证通过后切 SHARED 上池。

### 4.5 guest 侧线程与协作原语（实现 xlang 线程相关标签/函数时用）

- `Env.newTruffleThreadBuilder(Runnable)`：创建 **polyglot thread** 的现行 API（TruffleLanguage.java:840 等处引用）；旧 `Env.createThread(...)` 重载已 `@Deprecated`（TruffleLanguage.java:1905 起），勿在新代码使用。polyglot thread 归 Context 管辖，进入前自动触发 `initializeThread`，context 关闭时可被强制停止。
- `Env.newTruffleContext(...)` → `TruffleContext`：手动隔离层（enter/leave/enterInner），用于"同一 Context 内开子作用域栈"（调试器表达式求值、沙箱执行）。
- `TruffleSafepoint`（docs/Safepoints.md）：协作式安全点。任何 guest 侧阻塞（锁、IO 等待）必须 `TruffleSafepoint.setBlocked(Interrupter)`，否则线程取消/栈采样会被卡住。热点循环无需手插检查——编译代码自动含安全点轮询。
- `Env.submitThreadLocal(threads, action)`：向本 context 的线程提交 thread-local action（取消、中断、跨线程取栈）。
- Exit 语义三档（docs/Exit.md）：soft exit（异常解栈，不影响他线程）/ hard exit（所有 context 线程从安全点抛 `ThreadDeath` 强停）/ cancel（同 hard 但无 exit 通知）。这是**嵌入侧 Context 生命周期行为**；xlang 的 ExitMode（控制流）与此无关。仅当未来 xlang 增加语言级 exit 语义时才需对接此模型，且 guest 异常处理器必须对 `ThreadDeath` 立即重抛。

---

## 五、DSL 编写规范（以 SLAddNode 为范本）

本地路径：`~/sources/graal/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/nodes/expression/SLAddNode.java`

| 模式 | SLAddNode 示范 | xlang 迁移要点 |
|---|---|---|
| 快路径特化 + 溢出重写 | `@Specialization(rewriteOn = ArithmeticException.class) doLong(long,long)` 用 `Math.addExact`（L100-103） | `BinaryExecutable` 的算术按 long/double/Object 三档特化 |
| 慢路径泛化 | `@Specialization(replaces = "doLong") doSLBigInteger`（L117） | 类型系统用 `@TypeSystem` + `@ImplicitCast` 声明单调升级链 |
| 自定义 guard | `@Specialization(guards = "isString(left,right)")`（L152） | 标签/属性分派 guard 化 |
| 缓存子节点 | `@Cached SLToTruffleStringNode` / `@Cached TruffleString.ConcatNode`（L156-158） | 嵌套求值节点一律 @Cached 复用，禁止 execute 中 new 节点 |
| 互操作缓存 | `@CachedLibrary("left") InteropLibrary` + `limit="3"`（L203-207） | 宿主对象（biz bean）访问走 InteropLibrary 缓存 |
| 边界标注 | `@TruffleBoundary(allowInlining=true)` 包 `BigInteger.add`（L131）；慢路径 `@InliningCutoff`（L153,168） | 所有不可 PE 的调用（反射、HashMap 深链、日志）必须 boundary |
| 兜底 | `@Fallback` 走 SlowPathNode（L167-173） | 泛型路径集中兜底，抛带 SourceSection 的语言异常 |

其他高频注解：`@ExplodeLoop`（编译期展开定长循环）、`@GenerateUncached`（无状态版本）、`@GenerateInline`（字段内联）、`@Bind`（注入 Node/ContextReference 参数）。

**调试/调优入口**（docs/Optimizing.md、docs/Options.md）：`--engine.TraceCompilation`、`--engine.TraceCompilationDetails`（观测 tier/阈值/内联）、`--engine.TraceDeoptimization`、`--cpusampler`；DSL 静态检查警告见 docs/DSLWarnings.md。

---

## 六、SimpleLanguage 本地源码地图

根路径：`~/sources/graal/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/`

| 文件/目录 | 学习点 |
|---|---|
| `~/sources/graal/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/SLLanguage.java` | 语言注册、ContextPolicy=SHARED 的完整实现（L226, L538-556）、`initializeMultipleContexts` |
| 同目录 `SLContext.java` | 语言 context 职责：全局函数表、scope 对象 |
| 同目录 nodes 包 expression 子包的 `SLAddNode` | 特化/缓存/边界范本（见上节） |
| 同目录 nodes 包 controlflow 子包的 `SLReturnException` 等 | 控制流异常模式（xlang ExitMode 直接照抄结构） |
| 同目录 `SLFunctionLiteralNode`、`SLInvokeNode`（nodes 包 expression 子包） | 函数调用 + dispatch 内联缓存（两级缓存模式） |
| 同目录 `SLBlockNode` | 语句序列：实现 `BlockNode.ElementExecutor`，定长块的全展开内联语义由框架 `BlockNode` 保证（javadoc L100-102 明说触发 full unrolling 使全部子节点可内联） |
| 同目录 bytecode 子包的 `SLBytecodeRootNode` | **新式 Bytecode DSL 写法**（GenerateBytecode）：AST→字节码解释器的官方新路线，值得单独评估是否适合 xlang（见 truffle/docs/bytecode_dsl/UserGuide.md） |
| 同目录 `SLLexer`、`SLParser` | parser 参考（xlang 不需要，自有前端） |
| `~/sources/graal/truffle/src/com.oracle.truffle.st` | 最小语言（仅 5 个 Java 文件），快速通读的最佳起点 |

---

## 七、XLang → Truffle 映射草案

### 7.1 对象映射

| XLang（现解释器） | Truffle 运行时 | 说明 |
|---|---|---|
| `IExecutableExpression.execute(executor, EvalRuntime)` | `Node.execute(VirtualFrame)` | 树翻译而非适配包装：**逐节点类写翻译器**，`nop-xlang/exec/` 共 137 个 Java 文件（直接 `extends AbstractExecutable` 95 个，其余经中间抽象类间接继承） |
| `ExprEvalAction` / 编译产物根 | `RootNode` + `CallTarget` | 每个 xpl/expr/xjs 编译单元一个 CallTarget = JIT 粒度 |
| `EvalFrame(Object[] stack)` + slot 下标 | `VirtualFrame` + `FrameSlot`（`FrameDescriptor.Builder` 声明，标注 primitive kind） | `SlotIdentifierExecutable` 直译；按名访问的 `ScopeIdentifierExecutable` 需编译期尽量 slot 化（`LexicalScopeAnalysis` 已有此分析） |
| `EvalScopeImpl`（parentScope + HashMap 链） | 语言 context 内 scope 对象 / `MaterializedFrame` | 闭包捕获场景用物化帧；全局作用域放 context（EXCLUSIVE）或 language 实例（SHARED） |
| `ExecutableFunction.invoke(thisObj, args, scope)` | CallTarget.call / 直接节点调用 | 保持 `IEvalFunction` 外观不变，内部换 CallTarget |
| `ExitMode`（`CONTINUE`/`BREAK`/`RETURN`） | 控制流异常族（SL 模式） | 三值一一对应；xlang 无语言级 exit 语义，无需对接 Truffle 的 Context Exit 三档（soft/hard/cancel 是嵌入侧 Context 行为，不是语言节点的事） |
| `IEvalOutput`（xpl 输出缓冲） | 语言 context 持有、线程绑定 | 输出缓冲绝不可跨 Context 共享 |
| `SourceLocation` | `SourceSection`（合成 Source，路径+行号） | 异常/诊断/Instrumentation 都靠它 |
| `NopException`/`IEvalScope` 错误参数 | `AbstractTruffleException` 携带 SourceSection | 保持 `.param()` 语义在 host 侧可见 |

### 7.2 多线程运行时架构（推荐）

```
进程
 └─ XLangTruffleEngine（包装 polyglot Engine 单例）
     ├─ parse/翻译缓存（Executable 树 → Truffle AST，按 resourcePath）
     └─ Context 池（N 个，绑定工作线程）
         ├─ Context[0] ── Thread-0 ── execute(exprAction, args)
         ├─ Context[1] ── Thread-1 ── ...
         └─ 共享：Engine 的 JIT 代码 + parse 缓存 + 语言实例(SHARED)
```

- 嵌入模式：`Context.newBuilder("xl").engine(sharedEngine)`，`Context.enter()/leave()` 包住一批求值减少进出开销。
- 语言侧：`contextPolicy = SHARED`（正确性验证阶段可暂用 EXCLUSIVE 单 Context 对拍）；`isThreadAccessAllowed` 保持默认（每个 Context 内串行），并发由多 Context 承担。
- nop 侧接入：仿 `ScriptCompilerRegistry` 注册执行后端（如 `IEvalActionBackend`），`ResourceComponentManager` 加载模型后按配置选择"解释器 / Truffle 后端"，双后端共存便于**对拍验证**（同一 Executable 树两边执行结果必须一致——现成 autotest 机制可复用）。
- 编译产物缓存与 Delta 兼容：翻译发生在"模型加载完成后"（差量合并已完成、Executable 树已固化），Truffle 层不感知 Delta——**差量定制全部发生在上游**，运行时只见最终树。

### 7.3 明确的非目标

- 不做 xlang parser/宏的 Truffle 化（上游 XplCompiler 保持不变）；
- 不支持 native image 内跑 Truffle 后端（native 路线走构建期 Java 转译，两者互补）；
- 一期不做 instrumentation/debugger 集成（`ExecutionEventListener` 留作二期，接入 nop 的链路追踪）。

---

## 八、依赖与构建

```xml
<dependency>
  <groupId>org.graalvm.truffle</groupId>
  <artifactId>truffle-api</artifactId>
  <version>${graalvm.version}</version>   <!-- 对齐 graalvm.polyglot 版本 -->
</dependency>
<dependency>
  <groupId>org.graalvm.polyglot</groupId>
  <artifactId>polyglot</artifactId>
  <version>${graalvm.version}</version>
</dependency>
<!-- 注解处理器（编译期生成特化类与 provider 注册文件） -->
<annotationProcessorPaths>
  <path>
    <groupId>org.graalvm.truffle</groupId>
    <artifactId>truffle-dsl-processor</artifactId>
    <version>${graalvm.version}</version>
  </path>
</annotationProcessorPaths>
```

- 版本策略：钉 25.x LTS 对齐线（Maven Central 分发，Truffle Unchained 后与 GraalVM JDK 解耦）；stock JDK 21+ 嵌入运行 = 解释模式，GraalVM 运行 = JIT 模式，**同一二进制**。
- 预期收益边界：stock JVM 上 Truffle 解释器未必快于现解释器（收益主要来自 DSL 生成的紧凑特化节点）；**真正收益在 GraalVM 部署形态**（PE + 内联缓存）。性能目标需按部署形态分别设定。

---

## 九、本地源码副本（~/sources/graal）

- 克隆方式：`git clone --depth 1 --filter=blob:none --sparse` + `sparse-checkout set truffle sdk`（经 gitclone.com 镜像，github 直连超时）；当前 ~55MB。
- 已含：`truffle/`（api/dsl/runtime/SL/TCK/docs 全部源码与官方文档）、`sdk/`（org.graalvm.polyglot 公共 API）。
- 扩展：`git -C ~/sources/graal sparse-checkout add compiler`（Graal 编译器/PE 实现）、`substratevm`（native image）。
- 首要入口：`truffle/docs/`（LanguageTutorial、Optimizing、Options、Safepoints、Exit、AOTOverview、DSLGuidelines、bytecode_dsl/UserGuide）。

## 十、Open Questions（进入正式设计前需回答）

- [ ] Bytecode DSL（`@GenerateBytecode`）路线 vs 传统 AST DSL 路线，对 xlang 翻译器的代码量/性能/维护性对比？（SL 已同时示范两者）
- [ ] Context 池大小与工作线程映射策略（每线程固定 Context vs 池租借）；Context 创建/销毁成本实测。
- [ ] xlang 按名访问变量（ScopeIdentifierExecutable）在翻译期 slot 化的覆盖率；残余按名访问走什么节点（Map frame？context scope 对象？）。
- [ ] 与 `nop-js`（GraalJS 引擎）共享同一 Engine 的可行性与收益（互操作 + 统一编译线程预算）。
- [ ] GraalVM 版本钉法与 nop 21 LTS 基线的兼容矩阵（truffle-api 25.x 是否要求运行 JDK ≥ 25？——Maven 构件的 class file 版本需确认）。
