# XLang 与 JavaScript 兼容策略分析：完全兼容 / 兼容模式 / 冻结基线超集化

> Status: open
> Date: 2026-09-01
> Scope: nop-kernel/nop-xlang（文法/AST/编译/执行/双解析器）、nop-xlang-java 与 nop-xlang-truffle 后端、nop-core（EvalGlobalRegistry/扩展方法）、docs-for-ai
> Conclusion: （推荐，待确认）不应追求"完全兼容 JS"，不应做"JS 兼容模式"开关。推荐**冻结基线的语法超集化**：以 ES2015 核心快照为一次性目标补齐高频语法缺口（首要是修复模板字符串这一静默陷阱），无法对齐的语义分歧用编译警告 + 文档显性化围堵，完成后宣布文法冻结（grammar freeze）——超集化目标是"已冻结 10 年的 ES2015 快照"而非"活着的 JavaScript 规范"，因此是一次性有限工作量，不构成持续追随义务，与语言稳定性要求正交兼容。

## Context

- **动因**：AI 大模型生成 XLang（xbiz source、xpl `c:script`、规则表达式）时频繁出错。根因是 XLang 是"TS 文法派生 + Java 语义"的方言：看起来像 JS，语法与语义都有系统性差异，而模型按标准 JS 训练。
- **与前置分析的关系**：[../2026-08/2026-08-21-xlang-js-compat-for-dsh-ptc-mode.md](../2026-08/2026-08-21-xlang-js-compat-for-dsh-ptc-mode.md) 已从 PTC 沙箱场景否决"为执行不可信模型代码而 JS 化 XLang"（Option A 估 38–74 人周）。其 Open Question #6 留下的正是本问题：**日常开发场景**（人/AI 写平台自身的 biz/模板/规则）的 XLang JS 风格改进是否单独立项、怎么做。本分析回答该问题，不重复 PTC 结论。
- **约束**：用户明确要求考虑长期语法稳定性——XLang 语法不能跟随第三方（JS/TS 规范）的演进而不断变动。
- **本文事实来源**：全部差异条目经源码核实（文法 `model/antlr/XLang{Lexer,Parser}.g4`、语义 `exec/XLangSemantics.java`、`MathHelper`、测试语料 `src/test/resources/io/nop/xlang/expr/exprs/`），关键承重事实由本次分析独立复核（见各条证据路径）。

## 1. 差异全景

### 1.0 失败模式分类框架（分析的地基）

AI 代码生成工作流中，两类差异的代价完全不对称：

| 失败模式 | 表现 | AI 工作流代价 | 典型例子 |
|---|---|---|---|
| **响亮失败**（编译错误） | 解析/编译期报错，含行列号 | **良性**：模型读到错误即可自我修正重试 | `var`、`try` 无 `finally`、`undefined`、调用处 spread |
| **静默偏差**（语义分歧） | 正常运行但产出错误值 | **恶性**：无报错，错误结果流入业务 | 模板字符串不插值、catch 后重抛、`'2'*3=NaN` |

因此治理优先级应为：**先消灭静默陷阱（危险），再补响亮缺口（烦人但安全），最后把无法对齐的分歧显性化（文档+警告围堵）**。"完全兼容"方案之所以不必要，部分原因就是它把三种治理混为一谈。

### 1.1 语法差异清单（与 ES2015+ 对照，全部经 g4 文法核实）

#### A. JS 有、XLang 缺失（响亮失败源）

| JS 语法 | 状态 | 证据 |
|---|---|---|
| `var` 声明 | 缺失（lexer 中被注释"取消var语法"） | `XLangLexer.g4:117` |
| 函数表达式 `function(){}` | 缺失（规则被注释；**AST 节点 `FunctionExpression.java` 已存在**） | `XLangParser.g4:530` |
| 模板字符串 `${}` 插值 + `\`` 转义 | **缺失且静默**（见 1.2 节首条） | `XLangLexer.g4:201` |
| 调用处 spread `f(...args)` | 缺失（`arguments_` 无 Ellipsis 分支；字面量内 spread `[...a]`/`{...a}` 支持） | `XLangParser.g4:497-499` |
| 赋值表达式位置 `if(a=f())`、链式 `a=b=1` | 缺失（赋值只能是独立语句） | `XLangParser.g4:207-209` |
| 解构赋值语句 `[a,b]=x`、for 头解构 | 缺失（声明处解构支持） | `XLangParser.g4:177-180,211-214` |
| 剩余参数 `function f(a,...rest)` | 缺失（规则被注释） | `XLangParser.g4:297-299` |
| `switch` case 无花括号 `case 1: foo();` | 缺失（consequent 必须是块） | `XLangParser.g4:223-225` |
| `try{}catch(e){}` 无 finally | 缺失（**finally 语法强制**） | `XLangParser.g4:239-241` |
| `**` 幂运算 | 缺失（无 token） | `XLangLexer.g4` |
| `??=`/`&&=`/`||=` | 缺失（**`XLangOperator` 枚举已定义、零接线**） | `ast/XLangOperator.java:31-37` |
| 标签语句 / 带 label 的 break/continue | 缺失（规则被注释） | `XLangParser.g4:187-193,231-233` |
| 逗号表达式 `(1,2)` | 缺失（仅 for 头允许序列） | `XLangParser.g4:501-521` |
| `delete` / `void` / `with` / `debugger` | 缺失（规则被注释） | `XLangParser.g4:203-205,269-271,540,542` |
| `undefined` / `NaN` / `Infinity` 标识符 | 缺失（编译期"未解析标识符"错误） | 词法无此 token；`LexicalScopeAnalysis.java:633-637` |
| `0o` 八进制、BigInt `123n`、`1e3`（无小数点科学计数） | 缺失 | `XLangLexer.g4:86,90` |
| `\x41`/`\0`/`\v`/行续行 字符串转义 | 缺失（转义为 Java 风格子集，`unescapeJava`） | `XLangLexer.g4:296-300`、`XLangParseHelper.java:74-78` |
| async/await、generator | 缺失（token 存在但文法零使用；`AwaitExpression.java` 为遗留占位节点） | `XLangLexer.g4:139-140` |
| class/getter/setter/方法简写 | 缺失（规则被注释） | `XLangParser.g4:351-394,473-475` |
| default import/export、`export default`、动态 `import()` | 缺失 | `XLangParser.g4:34-119` |

#### B. XLang 特有语法（JS 没有）

这些是 XLang 的价值所在（编译期宏体系），任何兼容方案都必须保留：

| 语法 | 语义 | 证据 |
|---|---|---|
| `#{expr}` | 编译期宏表达式（立即求值并替换 AST） | `XLangLexer.g4:771`、`LexicalScopeAnalysis.java:857-897` |
| `` xpl`…` ``/`` sql`…` ``/`` tpl`…` ``/`` jpath` ``/`` xpath` ``/`` selection` ``/`` order_by` `` | 标签模板=**编译期宏调用**（传原始串给宏函数） | `GlobalFunctions.java:94-186` |
| `${}`/`#{}`/`%{}`/`@{}` | 四相插值定界符（eval/compile/transform/binding） | `expr/simple/SimpleExprParser.java`、`ExprPhase.java` |
| `@Decorator{meta}` 注解 | 函数/参数/类型上 | `XLangTypeSystem.g4:297-328` |
| 类型注解/泛型/union/tuple、`type`/`enum` 声明 | TS 风格子集（Java 兼容） | `XLangTypeSystem.g4` |
| `expr as Type`、后缀 `!` 非空断言 | 类型转换/断言 | `XLangParser.g4:535,579` |
| `and`/`or` 关键字运算符 | 等价 `&&`/`||`；**是保留字**（JS 代码用 `and` 作标识符会坏） | `XLangLexer.g4:77-78` |
| `123L` 长整型后缀 | Long 字面量 | `XLangParseHelper.java:94-97` |
| `import a.b.C` | 导入 Java 类 | `XLangParser.g4:100-106` |
| 类型化 `catch(e: MyException)` | Java 式 | `XLangParser.g4:251-253` |
| `~=` 自赋值 | 仅手写解析器识别（**双轨漂移实例**，ANTLR 侧无） | `AbstractExprParser.java:452-455` |
| `$xxx`/`_` 全局变量约定、`x.$toInt()` 转换方法 | 系统变量/成员式转换 | `api/XLang.java:43-45` |

#### C. 双轨解析器（结构性问题）

XLang 有**两个独立解析面**，语法改动成本天然 ×2：

- ANTLR 全文法（`c:script`/`source` 脚本）：`XLangExprParser.parseFullExpr` → ANTLR。
- 手写表达式解析器（XPL 属性 `${expr}`、meta 表达式）：`expr/simple/SimpleExprParser.java`（943 行），特性由 `expr/ExprFeatures.java` 位掩码门控（`CP_EXPR`/`TAG_FUNC`/`STATEMENT` 等 15 个开关，`supportCpExpr()` 即此机制的先例）。

两轨已出现漂移（`~=` 仅 SimpleExprParser 支持）。任何兼容方案都需明确两轨的同步范围。

### 1.2 语义差异（运行期，文法补齐解决不了的部分）

| 语义点 | XLang 行为 | 与 JS 差异 | 证据 | 失败模式 |
|---|---|---|---|---|
| **模板字符串** | `` `Hi ${name}` `` 在脚本中是**纯字符串**（双反引号转义，不插值，不支持 `` \` ``） | JS 插值。`` \` `` 在 XLang 会提前终止字符串 | `XLangParseHelper.java:84-88`（本次复核确认） | **静默错误值**（最高频 AI 陷阱） |
| **catch 重抛** | catch 体执行后**无条件重抛**原异常，`return`/吞异常均不可能 | JS catch 吞异常继续 | `exec/TryExecutable.java:47-48`（本次复核确认：`executor.execute(catchExpr, rt); throw NopException.adapt(e);`） | **静默行为偏差** |
| `==`/`===` | 四个运算符同一实现：数值跨类型等值（`1==1.0` true）、`'1'==1` **false**、无 JS 强转 | JS `==` 强转 / `===` 严格 | `MathHelper.xlangEq`、`StrictEqExecutable.java:23-26` | 静默（条件判断恒 false） |
| 数值体系 | Integer/Long/Double/BigDecimal 提升制：`Integer` 溢出截断、`4/2=2`（整型）、`5/2=2.5`、除 0 → NaN 非 Infinity | JS 全 IEEE double、`4/2=2`（double）、除 0 → Infinity | `MathHelper.java:843-868` | 静默（边角场景） |
| `null` 运算 | `null + 1 = NaN`、`'2' * 3 = NaN`（字符串不做数字强转） | JS：1 / 6 | `XLangSemantics.java:160-165` | 静默 |
| `typeof` | 返回 **Java 全类名**（`typeof 'a'` = `"java.lang.String"`），null → `"undefined"` | JS 返回 `"string"` 等 7 值 | `XLangSemantics.java:555-557` | 静默 |
| `for-in` | 仅接受 Map，迭代 entry | JS 迭代键字符串 | `XLangSemantics.java:1468-1474` | 静默 |
| `for-of` Map | 不可直接迭代（须 `entrySet()`） | JS 可迭代 | `CollectionHelper.java:917-944` | 响亮（抛错） |
| 正则字面量 `/re/g` | 解析为纯 pattern 字符串，**flags 丢弃**，无 RegExp 对象 | JS 产生 RegExp | `XLangParseHelper.java:56-63` | 静默 |
| `this` | 重写为作用域变量 `"this"`，无动态接收者 | JS 动态 this | `LexicalScopeAnalysis.java:540-543` | 语义分歧 |
| 重复声明 | 同作用域 `let` 重声明 = **编译错误** | JS 静默允许（loose）/ SyntaxError（strict） | `exprs/lexical-scope.test.md` | 响亮 |
| 字符串方法 | 直接暴露 `java.lang.String`：`replace`=字面量替换、`split`=正则、`substring` 不交换参数 | JS 语义不同名同 | 反射分派 | 静默（边角） |

### 1.3 内置函数与对象差异

**XLang 全局函数/变量（`GlobalFunctions.java` + `EvalGlobalRegistry.java:44-58`）**：`now()/today()/currentDateTime()/inject()/optional()/get()/assign()/logInfo()` 等；`$JSON`/`$Math`/`$Date`/`$String`/`$config`/`$context`/`$scope`/`$out`/`$beans`/`_`（Underscore，869 行工具库）。

**JS 内置对象对照**：

| JS 内置 | XLang 现状 | 失败模式 |
|---|---|---|
| `JSON.stringify/parse` | 改名 `$JSON.*`（功能超集） | 响亮（编译错：未解析标识符） |
| `Math.*` | `$Math`（方法集不同：`round` 半值规则不同、无 `sign`/`trunc` 同名） | 响亮 + 边角静默 |
| `console.log` | 无（`logInfo` 且要求字面量模板） | 响亮 |
| `parseInt`/`isNaN`/`encodeURIComponent` | 无（`x.$toInt()`、`$String.parseInt(s,radix)`） | 响亮 |
| `Date` | 无构造器（`now()`/`$Date` Java 时间类型） | 响亮 |
| `undefined`/`NaN`/`Infinity` 标识符 | 无 | 响亮 |
| Array 方法 `map/filter/push/pop/slice/includes/...` | **基本齐备**（`ListFunctions`/`SetFunctions`/`ArrayAdapterFunction` 扩展方法，箭头函数经函数式接口适配） | 大体兼容 |
| String 方法 | Java String 直暴露 + `$` 前缀 298 个 StringHelper 扩展；缺 `includes`/`padStart`/`padEnd`/`trimStart`/`trimEnd`/`slice`/`charCodeAt` | 部分响亮 |
| Map/Set/RegExp/Promise/Error 构造器 | 无（Map/List 用字面量；`throw new NopScriptError("code").param(...)`） | 响亮 |
| 对象/数组字面量 | `{}`→LinkedHashMap、`[]`→ArrayList；`obj.key` 走 getter/Map.get，方法调用反射+扩展方法 | 大体符合直觉 |

**小结**：内置函数层的差异**几乎全是响亮失败**（未解析标识符 = 编译错误），且可通过 additive 的全局别名低成本缓解；真正的静默陷阱集中在 1.2 节的语义层。

## 2. 方案对比

### Option A：完全兼容 JavaScript（否决）

- 核心思路：语法 + 语义全对齐 JS（含 `==` 强转、数值塔、undefined 三值、async/await）。
- **否决理由**：
  1. **语义兼容与 XLang 存在理由冲突**。XLang 的价值在 Java interop（`import` 任意类、JavaBean 反射、编译期宏、三执行后端同步契约）。JS 数值塔/undefined/prototype/微任务时序无处安放——前置分析已给出 38–74 人周（9–18 人月）估算，且其中"语义对齐且不破坏平台"被评估为不可收敛。
  2. **移动目标违背稳定性**。"完全兼容 JS"意味着永久追随 ECMAScript 演进（ES2024/2025/…），每版新语法都要评估跟进——与"XLang 语法必须稳定、不跟随第三方变动"的要求正面冲突。
  3. **AI 场景收益错配**：完全兼容的增量收益主要在语义边角（`==` 强转、数值精度），而这些在 biz 脚本高频子集中出现率低；高频痛点（模板字符串、try/catch 形态、内置对象名）用 Option C 的 1/10 成本即可解决。

### Option B：JS 兼容模式开关（否决为主方案）

- 核心思路：新增编译 profile/开关（如 `js-compat`），启用后放宽文法接受 JS 语法（`ExprFeatures` 门控 + ANTLR 谓词，机制上有 `supportCpExpr()` 先例）。
- **否决理由**：
  1. **模式修不了静默陷阱**。开关只影响文法接受集；1.2 节的语义分歧（模板插值缺失、catch 重抛、`==`、数值塔、typeof）在两种模式下**都存在**。危险等级最高的失败一个都消不掉。
  2. **AI 不知道模式状态**。模型生成代码时并不感知开关，仍按标准 JS 先验输出；模式反而引入"这段代码该按哪套规则读"的双重心智负担，文档、测试语料、提示词模板全部 ×2。
  3. **双轨 × 模式 = 四组合**。现有 ANTLR/SimpleExprParser 两轨已出现 `~=` 漂移，再加模式维度，一致性维护成本指数上升。
  4. **同样违背稳定性**："兼容模式"隐含对齐某个活的 JS 版本集，版本选择与升级成为长期治理负担。
  5. `ExprFeatures` 的正确用途是门控 XLang **自有**扩展（宏、标签函数）在不同上下文的可用性——单个特性小而自治；"另一种语言"不是特性，是语言分叉。

### Option C：冻结基线的语法超集化 + 语义分歧显性化（推荐）

- 核心思路，四层：
  1. **目标重述**：不追求"兼容 JavaScript（活的规范）"，追求"**语法层覆盖一个冻结的 ES2015 核心快照** + 保留全部 XLang 扩展"。ES2015 已标准化十年不变，是 AI 模型训练最充分的方言，也是有限、可验收的一次性目标。
  2. **C1 高频缺口一次性补齐**（§3.1 清单）：优先修复**静默陷阱**（模板字符串插值——注意 tagged template 传原始串给宏**本来就是 JS 语义**，所以此改动是纯超集方向），再补**响亮缺口**（`var`、try 无 finally、switch 无花括号、`undefined` 别名、`**`、逻辑赋值等）。
  3. **C2 无法对齐的语义显性化**：编译期警告（可静态检测的场景）+ parse error 附修正建议 + docs-for-ai 差异速查表。让剩余分歧从"静默"变"响亮"。
  4. **C3 文法冻结**：C1 落地并全平台回归后，宣布 grammar freeze，语言规格成文（升级 `docs/dev-guide/xlang/xscript.md` 的去除清单为完整语言基线文档）。此后语言能力演进走**库形态**（xlib 标签、宏、扩展方法、全局函数）——这本来就是 XLang 的既有演进路径（可逆计算原则：能力优先下沉为元语言机制而非语法）。
- **对"是否把 XLang 作为 JS 的超集更好"的直接回答**：**方向正确，但要精确表述为"冻结 ES2015 快照的语法超集 + 语义差异显性化"，而非"JS 超集"**。纯语法超集无法让 AI 代码语义正确（Java 语义仍在），它的真实收益是：AI 的自然输出**要么直接正确运行**（biz 脚本高频子集上两语言语义重合），**要么得到精确响亮的编译错误**（可自我修正重试）——把失败模式统一压到良性一侧。

### Comparison

| 维度 | A 完全兼容 | B 兼容模式 | C 冻结基线超集化（推荐） |
|---|---|---|---|
| 消灭静默陷阱 | 部分（语义全改才能） | **不能** | **能**（模板插值修复 + 警告显性化） |
| 消灭响亮错误 | 全部 | 模式内全部 | 高频项全部（§3.1） |
| AI 感知负担 | 无变化 | **增加**（两套规则） | 减少（写自然 JS 即可） |
| 稳定性 | **违背**（追随活规范） | **违背**（同左） | **符合**（冻结快照，一次性） |
| 对存量平台风险 | 极高（语义全变） | 中（门控） | 低（additive-only + corpus 回归 + 门控过渡） |
| 工作量 | 9–18 人月（前置分析） | 3–5 人月 + 持续双轨维护 | **约 6–10 人周**（§3.1 分级估算） |
| 与三后端关系 | 契约重写 | 低冲突 | 零契约变更（全部编译期脱糖） |

## 3. Option C 实施分析

### 3.1 分级清单（每项：成本 / 风险 / 说明）

**P0-a 快赢（不动文法，可立即做）**

| 项 | 改动点 | 成本 |
|---|---|---|
| parse error 增强：识别 `try`无`finally`、`case`无花括号、`var` 等高频误写，错误信息附 XLang 正确形态示例 | `XLangParseTreeParser.java` 错误构造处 | ~2 天 |
| docs-for-ai 差异速查表：`xlang-and-xpl-basics.md` 扩充完整"XScript ≠ JS"对照（本文 §1 表格可直接转化） | docs | ~1 天 |

**P0-b 文法一次性补齐（核心批次）**

| 项 | 技术路径 | 风险 | 成本 |
|---|---|---|---|
| **模板字符串 JS 插值** | lexer 拆 `TemplateStringLiteral` 为分段的 `TemplateStringHead/Chunk/Tail` token 或 parser 层拆分；untagged → 插值表达式（编译为 concat）；**tagged（`xpl\`…\`` 等宏）保持传原始串不变**（= JS tagged template 语义，天然一致）；同时支持 `` \` `` 与既有 `` `` `` 双反引号两种转义 | 存量审计：当前全库仅 8 个 xbiz/xpl/xlib 文件含反引号，审计面很小；`${` 在非 tagged 反引号串中的存量字面量使用需扫描（预计≈0，脚本里写 `${}` 字面量无理由）；可先经 `ExprFeatures` 门控灰度 | ~1.5 周 |
| `try` 无 `finally` 可选 | `tryStatement` 文法放宽，缺省编译为空 finally | 零回归（现有带 finally 代码解析不变） | ~2 天 |
| `switch` case 无花括号 | `case … ':' statement*`（XLang 无 fallthrough，case 体=语句列表，语义不变） | 零回归 | ~2 天 |
| `var` → 视同 `let` | 文法别名 | 零回归（现有代码不可能用 var——今天它是语法错误） | ~1 天 |
| `undefined` → 别名 null | 词法/AST 层常量折叠为 null 字面量；`x === undefined` 编译为 null 判断，对 AI 意图正确 | 零回归（同上，additive）；残余：`typeof x === 'undefined'` 仍不成立（typeof 返回类名），文档围堵 | ~2 天 |
| `**` 幂 | token + 映射 `MathHelper.pow` | 零回归 | ~1 天 |
| `??=`/`&&=`/`||=` | **枚举已定义，接线文法与 `selfAssignValue`** | 零回归 | ~2 天 |

**P1（第二批次）**

| 项 | 技术路径 | 成本 |
|---|---|---|
| 函数表达式 `function(){}` | 恢复被注释文法规则；**`FunctionExpression` AST 节点已存在**；编译为 `ExecutableFunction`（闭包机制现成：`CallFuncWithClosureExecutable`） | ~1 周 |
| 调用处 spread `f(...args)` | `arguments_` 增 Ellipsis 分支 → 参数数组构建（`XLangSemantics` 已有 spread 辅助逻辑） | ~3 天 |
| 内置别名：`JSON`→`$JSON`、`Math`→`$Math`、`console`（log/warn/error → LogFunctions）、`parseInt`/`parseFloat`/`isNaN`（委托 ConvertHelper） | `EvalGlobalRegistry` additive 注册 | ~3 天 + 文档 |
| String 方法补齐 `includes/padStart/padEnd/trimStart/trimEnd/slice/charCodeAt` | 新增 StringFunctions 扩展方法类（ListFunctions 同机制先例：非 `$` 前缀 JS 名字方法） | ~3 天 |
| 编译警告：String 与 Number 静态 `==` 恒 false 提示、`typeof` 与字符串字面量比较提示 | `LexicalScopeAnalysis`/类型推断 hook | ~1 周 |

**P2（可选，或明确放弃并写入"不支持项"契约）**：赋值表达式位置、逗号表达式、标签语句、剩余参数、`0o`/`\x` 转义。频率低，**建议放弃并文档声明**——"不支持项清单"本身是稳定契约的一部分，比无限追纯度更符合稳定性目标。

**明确永不做**（写入语言基线文档）：async/await/generator（同步契约是三后端地基，前置分析 §2.4 已论证）、class/prototype、JS 数值塔与 `==` 强转（与 Java interop 冲突）、catch 吞异常语义变更（现有平台代码依赖重抛语义做审计+重抛模式，改语义=全平台行为变更）、兼容模式开关（Option B 已否决）。

### 3.2 改动文件清单（生成链路）

```
model/antlr/XLangLexer.g4 / XLangParser.g4          # 文法（手改）
model/ast/io/nop/xlang/ast/XLangAST.xjava           # 新 AST 节点（如模板插值分段节点）
model/AntlrParserConfig.json                        # 生成配置
precompile/gen-xlang-parser.xgen / gen-xlang-ast.xgen  # 触发重生成
  → src/main/java/io/nop/xlang/parse/antlr/*        # ANTLR 重生成（不得手改）
  → parse/_XLangASTBuildVisitor.java（84KB, xgen 生成）
  → ast/（145 个节点，_gen 部分）
compile/LexicalScopeAnalysis.java                   # 词法分析/宏展开/警告
compile/BuildExecutableProcessor.java               # 脱糖为 Executable 树
exec/*                                              # 新 Executable 节点（约 120 个现有）
expr/simple/SimpleExprParser.java + AbstractExprParser.java  # 双轨同步（表达式上下文）
expr/ExprFeatures.java                              # 门控灰度
nop-xlang-java …/ExecToJavaTranslator.java          # java 后端新节点支持
nop-xlang-truffle                                   # truffle 后端新节点支持
nop-core …/EvalGlobalRegistry.java + GlobalFunctions.java    # 全局别名
String 扩展方法：HelperMethodsBuilder 注册链（nop-core reflect）
测试：exprs/*.test.md 语料 + xlang-compare 对拍 + 全平台 mvn install
文档：docs-for-ai/02-core-guides/xlang-and-xpl-basics.md、docs/dev-guide/xlang/xscript.md
```

### 3.3 困难

1. **生成链路五层深**：g4 → ANTLR codegen → `XLangAST.xjava` → xgen 生成 visitor/AST → 手写 LSA/BEP。单条文法规则改动牵动全链，且生成物不可手改（AGENTS.md 生成文件红线）。
2. **回归面 = 全平台**：约 150 个 `.xpl` + 266 个 `.xlib` + 932 个 `.xbiz` + 23 个 `.sql-lib`（≈1371 个文件，未计 xmeta/xdef 属性表达式）全部经该文法编译。必须全量重编译 + golden 对拍（`exprs/*.test.md` 语料 + `xlang-compare` 解释器/Java 后端对拍套件）。
3. **java 后端指纹哨兵的隐蔽耦合**：生成类清单按 Executable 树指纹绑定。重生成 parser 后若**存量源码解析出不同的树**（例如文法放宽改变 AST 形状），会触发全平台 `generated-fingerprint-mismatch` 降级告警。回归验证必须包含"存量源码 → 树指纹不变"比对（`--check` 模式的 CI 哨兵可复用）。
4. **双轨同步**：SimpleExprParser 需同步 `**`/逻辑赋值等表达式级新语法（`~=` 漂移是前车之鉴）。
5. **Protected Area 程序约束**：`nop-xlang` 内核 + 生成管线均属 plan-first 区域。实施前必须依 AGENTS.md 立 plan（含 exit criteria）+ 独立子代理审计闭环，本文只到"分析 + 推荐"为止。
6. **模板字符串的存量语义审计**：8 个含反引号文件逐个确认 + 全库扫描非 tagged 反引号串内的 `${`（建议直接用 `ExprFeatures` 门控过渡一版，corpus 干净后转默认开启再拆门控）。

### 3.4 性能影响

| 层面 | 影响 | 论证 |
|---|---|---|
| 编译期 | 文法备选分支增多 → ANTLR `adaptivePredict` 略慢 | 两阶段 SLL→LL 解析缓解；模型缓存（RCM）摊销一次性编译成本；预计 <5% 解析耗时，无稳态影响 |
| 运行期（存量代码） | **零** | 全部改动为编译期脱糖（var→let、模板→concat、`**`→pow 调用），不触碰数值塔/`==`/闭包表示等热语义 |
| 运行期（新语法代码） | 与现有节点同级 | 新 Executable 节点（模板拼接、spread 调用）每次执行一次辅助调用，成本同现有 tag-call/扩展方法量级 |
| 三后端 | 无契约变更 | 同步签名/ExitMode/闭包槽全不动；java/truffle 后端仅需为新节点补翻译（覆盖率问题，非性能问题） |
| 验证设施 | 现成 | `nop-benchmark/nop-benchmark-xlang`（JMH）可前后对比；`xlang-compare` 对拍语义一致性 |

唯一需要盯的真实性能风险点是 3.3-3（指纹漂移导致的批量降级观测噪音），属可观测性而非吞吐。

### 3.5 稳定性治理（回应用户核心关切）

1. **语言基线成文**：将"XLang = 冻结 ES2015 核心语法快照 + Java 兼容类型注解子集 + XLang 宏/四相插值体系 + Java 对象语义"写成规范（升级 `docs/dev-guide/xlang/xscript.md`），含**支持项/不支持项/语义差异**三张清单。不支持项（P2 放弃的那些）显式列出——契约清晰比覆盖完全更重要。
2. **一次性窗口 + 冻结**：C1 批次合并落地、全平台回归通过后，宣布 grammar freeze。此后语法层只收 bug fix；**新能力一律走库形态**（xlib 标签、宏函数、扩展方法、全局函数）——这些都不碰文法，且是 XLang 既有演进范式（可逆计算：能力下沉为机制而非语法糖）。
3. **不追随第三方演进**：明文拒绝 ESNext 提案（pipeline `|>`、decorator 规格化、Temporal 等）与 TS 类型系统演进（保持 Java 兼容子集，`xscript.md` 既有设计决策）。ES2015 快照本身不变，故超集化不产生任何后续跟随义务——这是它与"完全兼容 JS"的本质区别。
4. **additive-only + 门控灰度**：新语法先经 `ExprFeatures` 门控（先例：`CP_EXPR`），corpus 回归干净后默认开启、再拆门控。任何语义变更（哪怕是修 bug）都走 plan-first。
5. **语言版本戳**：文法/`AntlrParserConfig.json` 加方言版本标识，编译错误信息可报告版本——AI 与工具可感知当前基线。

## Conclusion

- **推荐 Option C（冻结基线的语法超集化 + 语义分歧显性化 + 完成后文法冻结）**，核心批次估 6–10 人周（P0 快赢 ~1 周内可单独交付）。
- **被否决**：Option A 完全兼容（语义与 Java interop 根本冲突、9–18 人月、追随活规范违背稳定性——与前置 PTC 分析从另一场景得出同一结论，互相印证）；Option B 兼容模式（修不了静默陷阱、AI 无感知、双轨×模式四组合、"兼容活的 JS"违背稳定性；`ExprFeatures` 只应用于门控 XLang 自有扩展）。
- **对用户原问题的直接回答**：(1) 完全兼容 JS——没有必要也不应该；(2) 兼容模式——弊大于利；(3) 作 JS 超集——**方向对，但表述应为"冻结 ES2015 快照的语法超集"**：AI 写的自然 JS 从此要么直接跑对，要么得到可自我修正的响亮编译错误，而 XLang 保留全部特有能力且文法从此冻结稳定。
- **首要行动项**（即使 Option C 整体不立项也值得做）：修复模板字符串静默陷阱 + parse error 增强 + docs-for-ai 差异速查表——这三件事覆盖 AI 日常报错的大头。
- 后续工作：确认后按 AGENTS.md Protected Area 流程立 `ai-dev/plans/`（plan-first，含全平台回归与指纹比对 exit criteria）。

## Open Questions

- [ ] 模板字符串改动的存量审计：非 tagged 反引号串内 `${` 的实际使用数（初步扫描仅 8 文件含反引号，需逐个确认）；是否需要 `ExprFeatures` 门控过渡一版。
- [ ] `undefined`→null 别名与类型推断的交互（`x === undefined` 在已声明类型变量上是否可静态折叠为更优判断）。
- [ ] `console`/`JSON`/`Math` 别名的语义差异清单（`Math.round` 半值规则、`parseInt` 进制推断）是否需要配套编译警告，还是仅文档围堵。
- [ ] P2 放弃项（赋值表达式位置、逗号表达式、标签语句等）在真实 AI 生成语料中的出现频率基线（可先收集 ai-dev 工作流中的编译失败统计再定夺）。
- [ ] SimpleExprParser（`${}` 属性表达式轨）是否同步全部新语法，还是仅同步表达式级运算符（`**`/逻辑赋值），语句级不同步。
- [ ] parse error 增强的"修正建议"数据放哪（错误码参数 vs 独立 hint 表）以便 AI 工作流低成本消费。

## References

- 前置分析：[../2026-08/2026-08-21-xlang-js-compat-for-dsh-ptc-mode.md](../2026-08/2026-08-21-xlang-js-compat-for-dsh-ptc-mode.md)（PTC 场景的同一问题，工作量估算来源）
- 平台指南：[../../../docs-for-ai/02-core-guides/xlang-and-xpl-basics.md](../../../docs-for-ai/02-core-guides/xlang-and-xpl-basics.md)
- 语言设计决策：`docs/dev-guide/xlang/xscript.md`（"从 JavaScript 语法中去除的特性"清单）
- 文法：`nop-kernel/nop-xlang/model/antlr/XLangLexer.g4`、`XLangParser.g4`、`XLangTypeSystem.g4`、`model/AntlrParserConfig.json`
- 生成链路：`nop-kernel/nop-xlang/precompile/gen-xlang-parser.xgen`、`gen-xlang-ast.xgen`
- 语义：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/XLangSemantics.java`、`exec/TryExecutable.java`、`io/nop/commons/util/MathHelper.java`（xlangEq/divide）
- 编译期：`compile/LexicalScopeAnalysis.java`、`compile/BuildExecutableProcessor.java`、`expr/ExprFeatures.java`、`expr/simple/SimpleExprParser.java`
- 内置注册：`functions/GlobalFunctions.java`、`nop-core …/lang/eval/global/EvalGlobalRegistry.java`、`nop-core …/lang/utils/Underscore.java`
- 行为语料：`nop-kernel/nop-xlang/src/test/resources/io/nop/xlang/expr/exprs/*.test.md`
- 后端与指纹：`ai-dev/design/xlang-execution/01-architecture-baseline.md`、`nop-xlang-java …/translator/ExecToJavaTranslator.java`
