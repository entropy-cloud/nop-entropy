# Nop Lint — xscript 执行引擎

> 日期: 2026-09-19（修订 2026-09-20）· 状态: 设计草案（索引见 [00-nop-lint-design.md](./00-nop-lint-design.md)）
> Phase 归属：xscript 引擎 v1（API 契约 + 超时执行器）在 **Phase 1** 交付；`scopeAnalyzer` 绑定 **Phase 3** 才可用（依赖矩阵见 08-migration.md §2）。

## 1. 执行模型（基于已有 XLang 基础设施）

XLang 已有 `IExpressionExecutor` / `IEvalScope` / `ScriptEvalAction` 编译执行体系，per-match 评估可行（编译一次 → `IExecutableExpression`，每 match 新建子 `IEvalScope` 绑定 `node`/`report`）。现有体系**没有超时/中断设施**（nop-core/nop-xlang 全量检索无 deadline/timeout 设施），需按 §4 方案新增。

> **v1 编译入口（已落地：`io.nop.lint.core.xscript.XScriptCompiler`，roadmap item 14）**：编译载体 = 公开入口 `XLang.newCompileTool()` + `allowUnregisteredScopeVar(false)` + `registerScopeVarDefinition(readOnly(...))` 白名单注册（仅 `node`/`captures`/`report`/`declType`，L2/Phase 3 绑定不注册）；路径 = `parseFullExpr` 得 `Program` → **import AST 预扫描拒绝**（XLang 词法分析会静默注册 import，公开 API 无"禁 import"开关，预扫描是调用方职责，零平台改动）→ `buildEvalAction` 产出 `ExprEvalAction`（内含 `IExecutableExpression`）。违禁构造闭环：import = AST 预扫拒绝；类定义 = 语法层直接解析失败（XLang 文法无 class 产生式）；`Class.forName` 与文件/网络全局函数 = 未注册标识符编译期 `unresolved-identifier` 显式失败；JS 内置全局（`Math`/`JSON`/`Number`/`Date`/`Object`/`Promise` → JsXxx 纯工具类）保留可用，即 §3 的"少量内置全局"。运行时绑定 `scope.setLocalValue` 后 `action.invoke(scope)`；XLang 的 `EvalFunctionHelper` 使 `Consumer`/`Function` 值可直接被脚本调用（`report`/`declType` 的载体）。

```java
public class XScriptEngine {
    private final IExecutableExpression compiled;  // 规则加载时编译一次

    public void executeMatch(MatchContext ctx) {
        IEvalScope scope = ctx.rootScope().newChildScope();
        scope.setLocalValue("node", new NodeWrapper(ctx.matchNode()));
        scope.setLocalValue("captures", ctx.captureWrappers());
        scope.setLocalValue("report", new ReportFunction(ctx));
        scope.setLocalValue("typeAnalyzer", ctx.typeAnalyzer());   // L2+，可空
        scope.setLocalValue("scopeAnalyzer", ctx.scopeAnalyzer()); // Phase 3，可空
        scope.setLocalValue(LINT_DEADLINE_KEY, startNanos + budgetNanos) // deadline 经 scope-local value 传递（§4 路线 A）
        // 执行经全局 DeadlineLintExecutor（§4 路线 A：运行期包装，每次 execute 入口检查 deadline）
        compiled.invoke(scopeArgument);
    }
}
```

## 2. API 契约（暴露给 xscript 的完整对象）

### 2.1 上下文对象

| 名称 | 类型 | 说明 | 可用 Phase |
|------|------|------|-----------|
| `node` | NodeWrapper | 当前匹配节点（LintNode 门面包装） | Phase 1 |
| `captures` | Map\<String, NodeWrapper/List\> | pattern 捕获的 meta-var | Phase 1 |
| `report(diag)` | Function | 报告违规（见 §2.3） | Phase 1 |
| `typeAnalyzer` | TypeAnalyzer 或 null | `isSubtypeOf(fqn, fqn)`、`resolveType(node)` | Phase 2（L2） |
| `scopeAnalyzer` | ScopeAnalyzer 或 null | `getScope(node)`、`resolveDefinition(ref)` | Phase 3 |
| `declType(node)` | Function → String? | L1 声明类型查询（design 06 §5.2 消费契约：书写类型文本或 null） | Phase 1（v1 已落地） |
| `metrics.cyclomatic(node)` / `metrics.cognitive(node)` / `metrics.npath(node)` | 对象方法 → int/int/long | deep 档方法度量查询（design 01 §6，item 32）；requires 门控白名单变体——仅 `requires` 含 METRICS 的规则编译期注册该标识，未声明引用 = 编译期 unresolved 显式失败；provider 经 ServiceLoader 发现（`MetricsResolverDiscovery`），无 provider/未具名文件 = gate DEGRADE 不可达 | Phase 3（item 32 已落地） |
| `scope.definition(node)` / `scope.declaredVariables(node)` / `scope.shadows(node)` / `scope.kind(node)` | 对象方法 → NodeWrapper?/List\<String\>/boolean/String | deep 档作用域查询（design 05 §2，item 33）；同 requires 门控白名单变体（`XScriptCompiler.compile` 第三参为 resolved capability 集）；definition 回传 CST NodeWrapper（resolver 返回字节偏移，binding 从树根贪心下探映射）；provider 经 ServiceLoader（`ScopeResolverDiscovery`），门控契约同 metrics | Phase 3（item 33 已落地） |
| `semantic.implementsInterface(node, name)` / `semantic.isOverridable(node)` / `semantic.isLoggerCall(node)` | 对象方法 → boolean | deep 档 L4 语义查询（design 06 §4.5，item 34）；门控契约同上（`SemanticResolverDiscovery`）；solver 不可答 = 异常 skip-and-count | Phase 3（item 34 已落地） |
| `dataflow.constantValue(node)` / `dataflow.useCount(node)` / `dataflow.isSelfAssigned(node)` | 对象方法 → String?/long/boolean | deep 档 L3 数据流查询（design 06 §4.4，item 34；identity 匹配声明节点）；constantValue 的 null = 合法"非常量"；门控契约同上（`DataflowResolverDiscovery`） | Phase 3（item 34 已落地） |

> **v1 绑定语义（已落地：`io.nop.lint.core.xscript.NodeWrapper` / `XScriptEngine`，roadmap item 14）**：NodeWrapper 按方法表映射到 `LintNode` 现有 API，`children`/`siblings` 枚举**具名节点**（文法匿名标点不进脚本 API），`range()` 输出 1-based 行号 + 1-based 字节列（源码字节经 `SourceMap` 换算，每文件每 run 建一次）；`descendant` 前序首匹配且排除自身，根节点 `siblings` 为空表。captures 单捕获 → NodeWrapper、序列捕获 → List\<NodeWrapper\>（同名双绑定取序列）。`declType` 为 L1 绑定名裁定（design 06 §5.2 首个注入消费方）；非 NodeWrapper 入参显式抛错。

### 2.2 NodeWrapper 方法表

| 方法 | 返回 | 说明 |
|------|------|------|
| `kind()` | String | 节点类型名 |
| `text()` | String | 源码切片 |
| `child(fieldName)` | NodeWrapper? | 按字段取子节点 |
| `children([kind])` | List\<NodeWrapper\> | 全部/指定 kind 子节点 |
| `ancestor(kind)` | NodeWrapper? | 最近指定 kind 祖先 |
| `descendant(kind)` | NodeWrapper? | 第一个指定 kind 后代 |
| `siblings([kind])` | List\<NodeWrapper\> | 兄弟节点 |
| `range()` | {startLine, startCol, endLine, endCol} | 位置 |

### 2.3 report() 参数（与 01 §2 fix schema 对齐）

```
report({
  message: String,          // 必填
  severity: 'error'|'warning'|'info'|'hint',   // 默认规则 severity
  node: NodeWrapper?,       // 诊断目标节点，默认当前匹配节点
  capture: String?,         // 或指定 capture 名（覆盖 node）
  fix: { description: String?, template: String, capture: String? }?  
                            // 修复：默认作用于诊断节点，capture 可覆盖目标
})
```

- 一次 xscript 执行可多次调用 `report()`（多条诊断）
- 修复范围默认 = 诊断节点 range（与 04 §7 Fix 模型一致）
- **fix schema 口径**：静态规则级 fix = `{description, template, suggest}`（元模型定义，见 10 §2）；report() 内嵌 fix 是其**运行期变体**，字段 `{description?, template, capture?}`——`capture` 用于覆盖修复目标节点（只存在于动态报告场景，不进规则级元模型）；两者 `description`/`template` 语义相同
- **v1 拒绝面（已落地）**：v1 对 `fix` 子字段**显式拒绝**（编译进白名单契约：出现非 null `fix` 即抛错，英文消息指向 roadmap item 25），不静默忽略；`message` 缺失/空白、非法 `severity`、未知 capture 名、空序列 capture 目标、非对象入参全部显式抛错（脚本异常语义归 §3：该 match 跳过 + 计数）；`capture` 与 `node` 同给时 **capture 覆盖 node**，序列 capture 的目标取其首节点（源码序）

## 3. 安全模型

| 维度 | 策略 |
|------|------|
| **语言子集** | XLang 表达式 + 语句（let/if/for/of/return）；**禁止** import、类定义、静态方法反射调用（`Class.forName` 等）、文件/网络访问全局函数 |
| **子集强制机制** | XLang 无完整沙箱，强制边界 = **编译期白名单 scope**：xscript 仅经专用 CompileConfig 编译，编译上下文只注册 `node`/`captures`/`report`/`typeAnalyzer`/`scopeAnalyzer` 与少量内置全局函数，不注册 import/Class/Resource 类访问器。安全前提是**规则来源受信**（VFS 规则集由平台管理员部署，非终端用户输入）；运行期再叠加超时与诊断数上限兜底 |
| **超时** | deadline 检查（方案见 §4）：默认 100ms/match（fast 档自动收紧为 20ms，见 11 §7），规则可配 `xscriptTimeoutMs`（元模型字段，见 10 §2），上限 1000ms |
| **资源** | 单 match 诊断数上限（默认 100，超出则终止脚本并告警）；禁止无限递归（调用深度上限 32） |
| **错误语义** | 脚本抛异常 → 该 match 跳过 + 记录一次 internal warning（不杀规则、不杀文件、不杀整个 run）；连续失败超过阈值（如 50 match）→ 禁用该规则并上报 |
| **超时后果** | 该 match 视为不匹配，记录 timeout 指标；同规则超时率 > 1% → CI 输出警告建议优化 |
| **可观测** | 每规则累计执行次数/平均耗时/超时数暴露到 LintStats（GraphQL 可查） |

> **v1 计数口径与 LintStats 承载（已落地：`LintStats` xscript 计数域，roadmap item 14）**：v1 为 run 级计数（非每规则细分）：`xscriptMatchesExecuted`（脚本真实执行的 match 数）、`xscriptFailedMatches`（异常跳过的 match 数）、`xscriptCappedMatches`（诊断超限中止的 match 数）、`disabledRuleIds`（连续失败达阈值被禁用的规则 id，按禁用顺序）；三条路径均 fail-fast + 计数 + 结构化 warn 日志（`nop.lint.xscript.match-failed` / `diagnostics-capped` / `rule-disabled`），无一处静默。诊断超限（默认 100/match）= 终止脚本、**保留已产出诊断**、计数告警——它是资源上界而非正确性裁决，且不计入失败连击。异常覆盖 `Exception` 与 `StackOverflowError`（递归失控不杀文件/run；调用深度上限 32 需 §4 路线 A 的 executor 钩子，随 item 15 落地）。`xscriptTimeoutMs` v1 携带不强制（见下 timeout 残留）。
>
> **deadline/深度落地口径（已落地：roadmap item 15，plan `2026-09-22-0128-1-deadline-executor`）**：上表 timeout 行全维度生效——`xscriptTimeoutMs` 解析期 fail-closed 校验（`RuleDslParser.timeoutMs`：非数值/非正数/超过 1000ms 上限一律解析错误，英文消息含规则 id；缺省 100）；预算按档位缩放（`LintProfile.xscriptBudgetMs`：FAST = min(20ms, 规则值)、STANDARD = 规则值，design 11 §7）；超时后果 = 该 match 视为不匹配（零诊断）+ `LintStats.xscriptTimedOutMatches` 独立计数 + `nop.lint.xscript.match-timeout` 结构化 warn，**不计入** `xscriptFailedMatches`、不触发连续失败禁用；同规则超时率 > 1%（严格大于，`RuleSetRunner.shouldWarnTimeoutRate`）输出 `nop.lint.xscript.timeout-rate-high` run 级警告。调用深度上限 32 经 §4 路线 A 的 executor 钩子落地：deadline 作用域内每节点嵌套执行按线程计深（`LintDeadlineExecutor`，finally 回退保证平衡），超限以模块异常走**脚本失败路径**显式中止 + 计数；非 lint 求值不适用。超时/深度/透传三路径的焦点断言：`TestXScriptTimeoutSemantics`、`TestLintDeadlineExecutor`；端到端：`TestXScriptDeadlineEndToEnd`（`.rule.yml` 夹具 → `LintEngine.lint` 在 deadline 内可控终止）。

## 4. 超时执行器设计（新增组件，含技术路线决策）

**约束**：线程中断无法停止同线程死循环，必须有线程内主动检查点。nop-xlang 属框架核心引擎（AGENTS.md Protected Area，变更需 plan-first），因此优先选择**不改动 nop-xlang/nop-core** 的路线。

**关键事实（已对照代码核验）**：XLang 的可执行表达式统一经 `IExpressionExecutor.execute(IExecutableExpression, EvalRuntime)` 执行，且 `WhileExecutable` 在**每次循环回边**都调用 executor 执行循环体；`EvalExprProvider.registerGlobalExecutor(...)` 可替换全局 executor。即：**运行期替换全局 executor 即可获得每节点/每次循环回边的拦截点，无需编译期插桩**（代价是每节点一次额外调用）。

| 路线 | 做法 | 平台改动 | 取舍 |
|------|------|---------|------|
| **A：全局 executor 包装（v1 采用）** | 注册包装 executor：每次 `execute` 入口比对 deadline（deadline 以 scope-local value 传入，如 `scope.getValue("__lintDeadline")`） | **零**（只用 `EvalExprProvider.registerGlobalExecutor` 公开扩展点） | 拦截粒度 = 每节点（含每次循环回边），足够截断死循环；每节点一次调用开销，编辑器 fast 档可测后评估 |
| B：nop-lint 本地预插桩 | 在 nop-lint 内对 XLang AST 做预处理 pass（解析 → 仅在循环回边/调用点插入检查节点 → 再编译） | 零（自建 pass，不改平台） | 检查点更稀疏、开销更低；实现量大于 A，作为 A 的性能优化后手 |
| C：改 nop-xlang 编译器（`BuildExecutableProcessor` 插桩） | 平台级支持 deadline | **nop-xlang 内部改造 → plan-first + 独立平台设计 + 回归测试** | 仅当 A/B 性能不达标且多模块都需要 deadline 时才立项；当前不做 |

```java
// 路线 A（v1）：全局 executor 包装，签名对齐现有 IExpressionExecutor
public class DeadlineLintExecutor implements IExpressionExecutor {
    @Override
    public Object execute(IExecutableExpression expr, EvalRuntime rt) {
        Long deadline = (Long) rt.getScope().getValue(LINT_DEADLINE_KEY); // scope-local value，不改 IEvalScope 接口
        if (deadline != null && System.nanoTime() > deadline)
            throw new XScriptTimeoutException(rt.getScope().getValue(LINT_RULE_ID_KEY));
        return expr.execute(rt); // 原语义透传
    }
}
// 注册：EvalExprProvider.registerGlobalExecutor(new DeadlineLintExecutor())
// deadline 写入：每 match 新建子 scope 时 setLocalValue(LINT_DEADLINE_KEY, startNanos + budgetNanos)
```

> `IEvalScope` 上**不存在** `getDeadlineNanos()` 之类的平台接口；deadline 一律走 scope-local value，避免触碰 nop-core 公共接口。

> **路线 A 实施裁定（已落地：`io.nop.lint.core.xscript.LintDeadlineExecutor` 等，roadmap item 15）**：
> - **安装**：`LintDeadlineExecutor.install()` 幂等安装，调用点 = `LintEngine.lint` 入口（每次 run 前确保全局 executor 槽位被包装）；包装对象 = 安装时刻 `EvalExprProvider.getGlobalExecutor()` 取回的既有 executor（不硬连 `DefaultExpressionExecutor`，可与 debugger executor 等共存组合），重复调用观测到已包装即 no-op，不叠层。
> - **委托语义（关键裁定）**：无 deadline → 逐字透传给被包装 executor，由其按平台惯例将**自身**下传表达式树——非 lint 求值语义与未安装时完全一致（含异常语义）；有 deadline → wrapper 以 `expr.execute(this, rt)` 把**自身**下传（平台 executor 惯例，cf. `DefaultExpressionExecutor`/`DebugExpressionExecutor`），使每个嵌套 `executor.execute` 入口——含 `WhileExecutable` 每次循环回边、lambda 体——重新进入 deadline/深度检查。**接线证据**：`TestLintDeadlineExecutor.expiryMidLoopProvesPerBackEdgeInterception`（顶层入口通过后 deadline 于循环中途过期，中止只能来自嵌套入口）+ 死循环端到端 `TestXScriptDeadlineEndToEnd`；伪代码中 `expr.execute(rt)` 应读作"把当前 executor 下传"（真实签名为双参）。
> - **deadline scope-local keys**：`__lintDeadlineNanos` / `__lintRuleId` / `__lintBudgetMs`（`XScriptDeadline` 常量），宿主侧注入值，**不进** `XScriptCompiler` 编译白名单——脚本引用即 unresolved-identifier 编译失败（`deadlineKeysAreNotInTheCompileWhitelist` 钉死），脚本不可读亦不可篡改自身 deadline。
> - **注入点**：`XScriptEngine.executeMatch` 四参重载在 `action.invoke` 前 `setLocalValue` 注入；三参路径（直接引擎调用方/既有测试）不注入、不强制——deadline 严格 opt-in，引擎管线内由 `RuleSetRunner` 按 `LintProfile.xscriptBudgetMs(规则值)` 逐 match 构造 `XScriptDeadline.startNow`。
> - **异常承载**：`XScriptTimeoutException extends NopLintException`（= `NopException`），函数调用包装（`wrapCallFuncException`）保留原实例，`RuleSetRunner` 按 `instanceof` 分流超时/失败；栈迹抑制（超时位置无诊断价值，消息含规则 id 与预算）。已知边界（与诊断上限同族）：脚本自带 `try/catch` 理论上可捕获中止异常——XLang 异常通道的固有限制，v1 接受，真不可中断需求属路线 C 重评条件。

## 5. 性能考虑

- xscript 仅在 Pattern Matching + Constraint 之后执行（快速过滤后）
- 编译一次（`IExecutableExpression`），每 match 只新建子 scope（对象分配最小化）
- `typeAnalyzer`/`scopeAnalyzer` 按需加载、项目级缓存（见 06 §6.6）
- 相同 AST 节点 + 相同规则版本的结果可缓存（内容寻址，编辑器场景）

## 6. xscript 语法示例（已按 XLang 语法校对）

```javascript
// 变量绑定（XLang: let 表达式）
let classDecl = node.ancestor('class_declaration');
let className = classDecl.child('name').text();

// 条件与报告（XLang 反引号字符串是普通字面量、无 ${} 插值——动态消息用 + 拼接）
if (typeAnalyzer.isSubtypeOf(className, 'CrudBizModel')) {
  report({ message: 'BizModel ' + className + ' 不应直接调用 dao()', severity: 'error' });
}

// 循环遍历（XLang: for..of）
for (child of node.children('method_declaration')) {
  let methodName = child.child('name').text();
  if (methodName.startsWith('get')) { /* ... */ }
}

// 正则（XLang 直接调用 Java String 方法，不用 JS 字面量语法）
if (methodName.matches('^get[A-Z].*')) { /* ... */ }

// 集合操作（XLang lambda：箭头是 =>，不是 ->）
let methodNames = node.children('method_declaration')
  .filter(x => x.child('name').text().startsWith('get'))
  .map(x => x.child('name').text());
```

> 注意：xscript 使用 **XLang 语法**——lambda 箭头是 `=>`（`XLangOperator.ARROW`，两个解析器均不识别 `->`）、正则是 `String.matches()`（Java String 方法直调）、反引号字符串是**普通字面量**（``` `` ``` 转义，无 JS 式 `${}` 插值，动态消息用 `+` 拼接）、无 JS 的 `/regex/.test()` 字面量。
