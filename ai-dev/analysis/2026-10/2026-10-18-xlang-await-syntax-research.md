# XLang `await` 语法引入研究

> Status: open
> Date: 2026-10-18
> Scope: `nop-kernel/nop-xlang`（parser/grammar、AST、compile、exec、三后端）+ `nop-kernel/nop-core` 求值契约
> Conclusion: 建议分阶段——先以「API 级 await」零语法风险落地，再引入「阻塞式 `await` 语法 + `async` 标记 + 虚拟线程执行」；仅当出现真实非阻塞并发需求时才投入 CPS/状态机变换。最终采用与否、以及是否要求 `async` 标记，需 owner 裁定。

## Context

- 需求：为 XLang/XScript 引入 `await` 语法，并给出「如果引入应如何处理」的实现路径。
- 现状：`await` / `async` 相关构件已被**部分播种**（lexer token、`AwaitExpression` AST 节点、类型推断），但**未接线**——缺 parser 产生式、缺 exec 节点、缺三后端翻译。这使得"能不能用"完全取决于设计，而非从零开始。
- 约束：XLang 求值栈是**全同步**的（`IEvalFunction.invoke(...) -> Object`、`IExecutableExpression.execute(...) -> Object`），且有**三后端（解释器 / java 源码转译 / truffle）必须对拍一致**的架构承诺（见 `ai-dev/design/xlang-execution/01-architecture-baseline.md` §五）。任何异步语义都必须回答"三后端如何等价"。
- 平台运行在 JDK 25（虚拟线程已可用），并已有 `JsPromise extends CompletableFuture` + `ContinuationExecutor`（单线程 trampoline / microtask 队列）异步基础设施。

## 现状事实（live 锚点）

| 构件 | 位置 | 状态 |
|------|------|------|
| `Async` / `Await` lexer token | `model/antlr/XLangLexer.g4:139-140` | 存在（`Async=94, Await=95`） |
| keyword 列表收录 | `model/antlr/XLangCommon.g4` `keyword_` | **被注释掉（未收录）** → 既不是关键字，也不进入 `identifierOrKeyword_`，属"死 token"：`await x` 与 `let await=1` 当前都无法解析 |
| `AwaitExpression` AST | `ast/AwaitExpression.java`、`_gen/_AwaitExpression.java`、`model/ast/.../XLangAST.xjava:611` | 已生成，含 `argument` 字段 |
| visitor / printer / optimizer | `XLangASTVisitor:277`、`XLangExpressionPrinter:656`、`XLangASTOptimizer:211` | 已接线 |
| 类型推断 | `compile/TypeInferenceProcessor.java:1596` | 已实现：`await` 结果类型 = 操作数类型（透传） |
| 编译为 Executable | `compile/BuildExecutableProcessor.java:1578` | **空实现**：`return super.processAwaitExpression(...)`，不产出任何 exec 节点 |
| exec 节点 | `exec/`（137 节点清单） | **无** `AwaitExecutable` |
| 三后端翻译 | `nop-xlang-java`、`nop-xlang-truffle` | 无 await 映射 |
| Promise 基础设施 | `utils/JsPromise.java`（`extends CompletableFuture`）；`commons/.../ContinuationExecutor.java`（单线程 trampoline，微任务语义）；`LexicalScopeAnalysis` 注册 `Promise` 裸名与类型别名 | 已存在 |
| parser 产生式 | `model/antlr/XLangParser.g4` `expression_single` | **无** await/async 产生式 |

**结论**：`await` 是"种子已埋、线未接"。真正要动的是 **parser grammar（并重新生成 ANTLR）→ build/exec → 三后端 → 运行时调度语义** 这条完整链路。

## 关键约束

1. **同步求值栈**：函数调用同步返回 `Object`，没有挂起点。要在调用中途"等"，只有两条路——阻塞当前线程，或把函数体改造成可恢复的控制流。
2. **三后端等价**：新增节点必须在解释器 / java / truffle 三侧语义一致（对拍三层断言：返回值 / 副作用 / 异常语义）。
3. **parser 为生成物**：`parse/antlr/*.java` 由 ANTLR 从 `.g4` 生成且检入仓库（带 `_` 前缀的生成物不可手改），改语法须改 `.g4` 并重跑生成。
4. **`ContinuaionExecutor` 微任务语义**：`JsPromise` 的 executor 与 `then/catch/finally` 回调都经 `ContinuationExecutor.INSTANCE.execute(...)` 调度，而该 executor 是**线程本地单线程队列**。这与"阻塞式 await"存在**死锁交互**（见 §五）。
5. **自完备设计**：`ai-dev/design/self-contained-design.md` 禁止把外部大型系统当作架构支柱。JDK 内部 `jdk.internal.vm.Continuation` 属非稳定 API，不符合"可长期依赖"要求。

## 候选方案

### 方案 A：API 级 await（不加语法）

- 形态：注册全局函数 `await(x)`（或 `Promise.await(x)` / 直接 `promise.get()`），接受 `CompletionStage`/`JsPromise`/thenable，阻塞求值至完成并返回结果；普通值原样返回。
- 改动面：仅 `GlobalFunctions`/`JsPromise`（或 `JsConsole` 同款 `JS_GLOBAL_VARS`），**零 parser/AST/后端改动**。
- 优点：风险最低、当天可用；三后端零分叉（普通函数调用）；可作为语义试验台。
- 缺点：不是 JS 的 `await` 语法，与"引入 await 语法"的诉求只部分吻合。

### 方案 B：阻塞式 `await` 语法（虚拟线程）

- 形态：加一元产生式 `Await argument=expression_single # AwaitExpression`；编译为 `AwaitExecutable`：求值操作数，若为 `CompletionStage`/thenable 则 `join()`/`get()`，否则原样返回。`async` 仅作**标记**（或省略）。
- 改动面：parser（+重生成）、`AwaitExecutable`、`BuildExecutableProcessor`、java/truffle 翻译器；运行时要求执行发生在**虚拟线程**上以承担阻塞成本。
- 优点：语法完整、实现最直接、与既有同步 Java 阻塞 API 天然兼容；JDK 25 虚拟线程使"阻塞"成本可接受。
- 缺点：不是协作式并发；需规定虚拟线程执行契约；与 `ContinuationExecutor` 有死锁交互需专门处理（§五）。

### 方案 C：CPS / 状态机变换（真正的 async function）

- 形态：`async function` 体被编译成可恢复状态机，每个 `await` 是挂起点；局部变量提升到状态对象；promise 完成后回填并 resume。
- 优点：真正的协作式并发，单线程可承载海量等待；最贴近 JS 语义。
- 缺点：**工程量巨大**——parser、AST、lexical scope/slot 分配、解释器执行栈（需可恢复帧）、java 后端（生成状态机）、truffle（挂起语义）、调试器、闭包捕获全部要改；对拍难度陡增。XLang 无 generator/yield 先例可复用。

### 方案 D：JVM Continuation / 生成器变换

- 形态：用 `jdk.internal.vm.Continuation`（或先把 AST 变换为生成器）实现挂起/恢复，再在其上实现 await。
- 优点：理论上兼顾非阻塞与控制流透明。
- 缺点：依赖非稳定 JDK 内部 API（违反自完备与稳定性）；仍是深改控制流，复杂度接近 C。

### 方案 E：Truffle 原生挂起

- 形态：truffle 后端用 `TruffleSafepoint` / continuation 原语表达挂起。
- 缺点：Truffle 后端本身只服务 JVM 动态脚本；把 await 语义绑到单后端会**破坏三后端等价**，且 truffle 愿景已把"guest 线程原语暴露"列为 non-goal。

### 对比

| 维度 | A API 级 | B 阻塞语法 | C 状态机 | D Continuation | E Truffle |
|------|---------|-----------|---------|---------------|-----------|
| 语法完整度 | ✗ | ✓ | ✓ | ✓ | ✓ |
| parser/AST 改动 | 无 | 小 | 大 | 大 | 大 |
| 解释器改动 | 无 | 单节点 | 执行栈重构 | 执行栈重构 | 否 |
| java 后端 | 无 | 直译 join | 生成状态机 | 生成状态机 | 否 |
| truffle 后端 | 无 | 直译 join | 挂起语义 | 挂起语义 | 原生 |
| 三后端等价 | 天然 | 易（共享 helper） | 难 | 难 | 破坏 |
| 真实非阻塞 | ✗ | ✗ | ✓ | ✓ | ✓ |
| 工程风险 | 极低 | 低-中 | 极高 | 高 | 高 |
| 依赖稳定性 | ✓ | ✓ | ✓ | ✗ 非稳定 API | 框架绑定 |

## 阻塞式 await（方案 B）的语义与风险分析

若采纳方案 B，必须先钉死以下点：

1. **执行线程契约**：`await` 会阻塞载体线程。规定"含 `await` 的求值必须在虚拟线程上执行"，由调用侧（Runner/服务入口）保证；否则阻塞普通工作线程会造成线程池饥饿。这与平台 JDK 25 基线一致。
2. **与 `ContinuationExecutor` 的死锁交互（P0 风险）**：`JsPromise` 的 executor 与回调可能被排入**当前线程**的 `ContinuationExecutor` 队列，只有在当前同步任务让出控制权后才执行。若在**同一线程**上 `await`（阻塞）一个"其 settle 依赖该队列中待执行任务"的 promise，则队列永不推进 → **死锁**。
   - 处理方向（择一或组合）：(a) 规定 `await` 只在**非** `ContinuationExecutor` 线程上使用；(b) `await` 前先 flush/驱动本地 continuation 队列；(c) `await` 检测到当前线程持有未完成 continuation 时抛明确错误而非静默挂起。**必须给出确定性规则，不能靠用户自觉。**
3. **操作数强制（coercion）**：明确 `await` 的输入归一化：`CompletionStage` → 展开；含 `then` 的 thenable → adopt；`null`/普通值 → 原样返回（对齐 JS：await 非 promise 值即该值本身）。
4. **异常传播**：被拒绝的 promise / 异常完成 → 以 `NopException`（或 `NopEvalException`）抛出，与同步 `throw` 语义一致；错误码与 `SourceLocation` 要能回映射（对拍第三层断言）。
5. **返回类型推断**：`TypeInferenceProcessor` 当前把 `await` 结果类型等同操作数类型；若走阻塞语义且操作数静态类型是 `Promise`/`CompletableFuture`，理想结果是**去包装后的元素类型**，需补类型推断规则。
6. **输出顺序**：`await` 前后对 `$out` 的写入顺序必须保持程序序（同步模型天然满足；CPS 会打破，这也是 B 的隐性优势）。

## `async` 标记是否必需

| 选项 | 说明 | 取舍 |
|------|------|------|
| 要求 `async function` / `async () =>` 才可用 `await` | 贴近 JS；把"可能挂起"静态标在函数上，便于将来升级到 CPS；编译期即可报"await 用在非 async 上下文" | **推荐**（若引入语法），保留升级空间 |
| 不需要 `async`，`await` 在任何位置可用 | 实现更简单；但失去静态标记，一旦将来要 CPS 就是破坏性变更 | 仅方案 A 可用 |

注：`async` 当前同样是"死 token"且被注释出 `keyword_`；若采纳"要求 async 标记"，需把 `Async` 从注释中恢复并加入 `functionDeclaration` / `arrowFunctionExpression` 产生式的可选前缀。

## 推荐路径（分阶段）

- **Phase 0（零风险，先落地）**：实现**方案 A**——把 `await(x)` 作为全局函数（或 `Promise.await`）接入 `JsPromise`/`CompletionStage`，并在 `js-builtin-globals-design.md` 同族的全局对象文档中登记。立即满足 80% 的"等一个异步结果"诉求，同时为 Phase 1 固定 coercion/异常语义与测试基线。
- **Phase 1（若确需语法）**：实现**方案 B**：
  - parser：加 `Await argument=expression_single` 一元产生式；恢复 `Async` 并作为 function/arrow 可选前缀；重跑 ANTLR 生成。
  - compile/exec：加 `AwaitExecutable`（共享 helper 做 coercion），`BuildExecutableProcessor` 接上（当前是空实现）。
  - 三后端：java/truffle 映射到同一个共享 helper 的阻塞语义；对拍纳入 await 用例。
  - 运行时：落实 §五的线程契约与死锁规则。
- **Phase 2（仅当出现真实非阻塞并发需求）**：评估方案 C。触发条件应是"需要单线程承载大量并发等待 / 阻塞不可接受"的**可证伪**场景；在此之前拒绝为 C 预付复杂度。

## Parser 改动清单（Phase 1 契约级）

1. `XLangLexer.g4`：`Async` / `Await` token 已存在，无需新增。
2. `XLangCommon.g4`：视 `async` 标记决策，决定是否将 `Async` 收入 `keyword_`（`await` 作为一元运算符通常**不**入 `keyword_`，而是在 `expression_single` 直接引用 `Await` token）。
3. `XLangParser.g4` `expression_single`：新增 `Await argument=expression_single # AwaitExpression`（优先级需与一元运算符协调；`await` 应绑定比成员/调用更紧还是更松需按 JS 语义定为"高优先级一元"）。
4. 重新生成 `parse/antlr/XLangParser.java` 等生成物；`XLangASTBuildVisitor` 增加对应访问方法（生成物一般自动带上）。
5. 不应手改 `_` 前缀生成物。

## Open Questions

- [ ] 是否采纳 `async` 标记作为硬性要求？还是允许裸 `await`（Phase 0 风格）？
- [ ] `await` 是否允许在顶层（top-level await）与模板/xpl 上下文中使用？
- [ ] 阻塞式 await 与 `ContinuationExecutor` 的确定性规则选哪条（线程限制 / 主动 flush / 快速失败）？
- [ ] 若操作数是 `JsPromise`，`await` 应否保留其 `Rejected(reason)` 包装语义，还是解包为 reason？
- [ ] 是否需要在 Phase 0 就把 `await` 暴露为 `$await`（符合全局变量命名）还是裸名 `await`（符合 JS 直觉但与 `$` 一族不一致）？
- [ ] 三后端对拍如何覆盖"阻塞/调度时序"——返回值一致是否足以捕捉时序缺陷？

## References

- `ai-dev/design/js-builtin-globals-design.md`（§4.4 已明确拒绝 Promise 异步链，§7 将 async/await 列为独立项）
- `ai-dev/design/xlang-execution/01-architecture-baseline.md`（三后端等价红线）
- `ai-dev/design/self-contained-design.md`（禁非稳定外部能力支柱）
- live：`nop-kernel/nop-xlang/model/antlr/XLangLexer.g4`、`XLangCommon.g4`、`XLangParser.g4`
- live：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/utils/JsPromise.java`
- live：`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ContinuationExecutor.java`
