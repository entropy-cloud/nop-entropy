# Truffle 框架深度知识（为实现 nop-xlang Truffle 运行时）

**日期**：2026-08-16（决策迁出收口：2026-08-19）
**范围**：Truffle 框架执行模型、核心 API、多线程模型、DSL 编写规范、SimpleLanguage 参考地图
**状态**：active（知识参考层：外部框架知识速查 + SL 源码地图。**本文不承载 XLang 侧设计决策**——愿景见 `00-vision.md`，架构决策见 `02-architecture-baseline.md`，原 §7 映射草案/§4.4 选型等决策性内容已迁出收口）
**事实来源**：`~/sources/graal`（oracle/graal sparse clone，`truffle/` + `sdk/` 模块）一手源码与 javadoc；引用处标注文件路径与行号

---

## 一、一页结论

1. Truffle 的性能来自**自修改 AST 解释器 + partial evaluation (PE)**：节点首次执行时做类型画像并"自我改写"为特化节点，热点 CallTarget 被运行时 Graal 编译器内联展开成机器码。**只有跑在 GraalVM 系运行时上才有 JIT**；stock JVM 上纯解释执行。**native image 形态**：Truffle guest 代码的运行时 JIT 在 GraalVM 25 为默认（优化运行时编入镜像），宿主 Java 代码则无运行时 JIT（closed-world AOT 语义；Truffle 侧机制见 `~/sources/graal/truffle/docs/AOTOverview.md`）。
2. 实现一门 Truffle 语言 = 实现一组 `Node` + 一个 `TruffleLanguage` 子类。没有 parser 也可行（`Source` 可为合成源或直接程序化构造 AST）。
3. 多线程有两条机制路径：**路 A**——host 侧每线程一个 `polyglot.Context`，显式共享同一个 `Engine`（编译代码与 parse 缓存跨线程复用，语言实现无需支持并发 AST 访问）；**路 B**——单 Context 多线程并发执行（要求语言覆写 `isThreadAccessAllowed` 返回 true 并保证节点线程安全，且该 Context 内所有已初始化语言都允许）。**xlang 的选型决策（选定路 A）见 `02-architecture-baseline.md` §五。**
4. 语言上下文共享策略由 `ContextPolicy` 决定（EXCLUSIVE → REUSE → SHARED，共享程度递增）。**注意 REUSE ≠ 池化共享**：REUSE 是"context 销毁后回收复用 language 实例"，同一时刻一个实例只服务一个存活 context（TruffleLanguage.java:4306-4310）；**多个同时存活的 Context 要共享 AST/parse 缓存/JIT，只有 SHARED 一条路**（AOTOverview.md:32-34）。官方建议手写语言从 EXCLUSIVE 起步、成熟后升 SHARED（SLLanguage.java:552；SL 本身已是 SHARED，L226）。context-independent 准则清单见 §4.3。**xlang 的 Policy 决策（终态 SHARED + 正确性验证期 EXCLUSIVE 过渡）见 `02-architecture-baseline.md` §五。**
5. 依赖形态：`org.graalvm.truffle:truffle-api` + 注解处理器 `truffle-dsl-processor`（编译期生成特化子类与 provider 注册文件），嵌入侧用 `org.graalvm.polyglot:polyglot`。语言注册**不依赖注解扫描**——DSL 处理器在编译期生成 `META-INF/services/...TruffleLanguageProvider`（SL 源码 META-INF 下无手写 services 文件，只有 native-image.properties）。
6. Native image 内**可以**嵌入 Truffle 语言：官方 23.1+ 无需特殊配置，GraalVM 25 起优化运行时（guest 代码运行时 JIT）为默认（构建期传 `-Dtruffle.UseFallbackRuntime=true` 才退回纯解释）。**xlang 是否利用 native 内 guest JIT 的路线裁定（否决，native 提速走构建期 Java 转译、两者互补）见 `00-vision.md` §四**；native 内动态加载字节码的官方通路是 Espresso（Java on Truffle，支持在 native exe 内动态加载字节码）。

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
- XLang 对应物：现有解释器的 `MathHelper`/方法分派按接收者类型走 Map 查找——Truffle 的对应机制是"缓存 + guard"（内联缓存节点）。

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
    contextPolicy = ContextPolicy.SHARED /* xlang 选定值，见 02-architecture-baseline §五 */)
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

### 4.4 两种并行架构（选型已收口）

两条路径的机制事实：路 A（每线程一 Context + 共享 Engine）要求 `ContextPolicy=SHARED` + context-independent 准则，节点无需支持并发访问，语言 context 天然隔离；路 B（单 Context 多线程并发）要求 `isThreadAccessAllowed→true` + 节点全线程安全，共享语言 context（全局状态需自行同步），XLang 适配需重审 `IEvalOutput` 等线程绑定资源。

**xlang 运行时的选型决策（选定路 A、拒绝路 B 的完整对比与理由）已正式化至 `02-architecture-baseline.md` §五**，本文不再承载该决策。

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
| 同目录 bytecode 子包的 `SLBytecodeRootNode` | **新式 Bytecode DSL 写法**（GenerateBytecode）：AST→字节码解释器的官方新路线（xlang 路线裁定见 `02-architecture-baseline.md` §九 Q1；官方文档 truffle/docs/bytecode_dsl/UserGuide.md） |
| 同目录 `SLLexer`、`SLParser` | parser 参考（xlang 不需要，自有前端） |
| `~/sources/graal/truffle/src/com.oracle.truffle.st` | 最小语言（仅 5 个 Java 文件），快速通读的最佳起点 |

---

## 七、XLang → Truffle 映射（草案已正式化）

本章原为映射草案（对象映射、多线程运行时架构、非目标）。**决策性内容已全部迁出收口**：

- 对象映射与帧/slot 映射 → `02-architecture-baseline.md` §四（含与 `LexicalScopeAnalysis` slot 分配的关系、按名访问残余路径）
- 多线程运行时架构（Context 池 + 共享 Engine + SHARED）→ `02-architecture-baseline.md` §五
- 明确的非目标 → `00-vision.md` §四（正式化）

---

## 八、依赖与构建（外部构件形态）

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

- 外部事实：三个构件自 2023-10 "Truffle Unchained" 起以 Maven Central 独立分发，与 GraalVM JDK 解耦；stock JDK 21+ 嵌入运行 = 解释模式，GraalVM 运行 = JIT 模式，同一二进制。truffle-api 源码基线 javaCompliance 17+（multi-release overlay 9/21，见 `~/sources/graal/truffle/mx.truffle/suite.py`）。
- **nop 侧版本钉版决策（钉 25.x LTS 线 + 条件钉版条款）见 `02-architecture-baseline.md` §二**；性能收益边界的方向性口径见 `00-vision.md` §三。

---

## 九、本地源码副本（~/sources/graal）

- 克隆方式：`git clone --depth 1 --filter=blob:none --sparse` + `sparse-checkout set truffle sdk`（经 gitclone.com 镜像，github 直连超时）；当前 ~55MB。
- 已含：`truffle/`（api/dsl/runtime/SL/TCK/docs 全部源码与官方文档）、`sdk/`（org.graalvm.polyglot 公共 API）。
- 扩展：`git -C ~/sources/graal sparse-checkout add compiler`（Graal 编译器/PE 实现）、`substratevm`（native image）。
- 首要入口：`truffle/docs/`（LanguageTutorial、Optimizing、Options、Safepoints、Exit、AOTOverview、DSLGuidelines、bytecode_dsl/UserGuide）。

## 十、Open Questions（已清账：2026-08-19）

原五条 Open Questions 已在 `00-vision.md` / `02-architecture-baseline.md` 中逐条裁定，清账表见 **`02-architecture-baseline.md` §九**：

| 原问题 | 裁定落点 |
|---|---|
| Bytecode DSL vs 传统 AST DSL 路线 | `02` §九 Q1：一期 AST DSL，Bytecode DSL 列 watch-only 重评估 |
| Context 池大小与工作线程映射策略、创建/销毁成本 | `02` §五"Context 池策略"（池租借 + 配置化，实测归 I7） |
| 按名访问 slot 化覆盖率、残余按名访问路径 | `02` §四（翻译期 slot 化优先，残余走 context scope 链对象） |
| 与 nop-js 共享 Engine 的可行性与收益 | `02` §八（一期不共享，独立 Engine；触发条件重评估） |
| GraalVM 版本钉法与 JDK 21 基线兼容矩阵 | `02` §二（钉 25.x LTS + 条件钉版条款；构件实测确认点移交 W2-review） |

另有移交 W2-review 复核的确认点清单，见 `02` §九末尾。本文不再保留 open 状态的问题条目。
