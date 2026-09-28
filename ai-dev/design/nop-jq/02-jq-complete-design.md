# nop-jq 完整功能设计文档

> Status: active
> Created: 2026-09-18
> Last Reviewed: 2026-09-25
> Source: jq 1.7.1 官方测试套件 (tests/jq.test，已 vendor 至 nop-jq 测试资源)

## 一、产品定位

nop-jq 是 Nop 平台的自研 JSON 查询引擎，同时支持两种语法：
- **JsonPath 语法**：面向提取场景，兼容 fastjson API
- **jq 语法**：面向转换场景，兼容 jq CLI 核心语法

当前状态：jq 引擎采用 **AST 直接执行**架构，jq 1.7.1 官方测试套件（jq.test）**全量通过**（430/430 执行用例；模块系统用例按 Non-Goal 跳过）。测试基础设施 vendor 官方 jq.test 原文，harness 与 jq 官方 runner 语义对齐（jv_parse 期望行 + jv_equal 数值比较 + 多输出有序匹配 + %%FAIL 必须报错）。

## 二、jq 功能覆盖（最终状态）

以下功能类别均已在 AST 执行引擎中实现并通过官方套件验证：

| 类别 | 覆盖内容 |
|------|---------|
| 基础值与字面量 | null/true/false、整数/浮点/指数、字符串转义（\uXXXX、代理对）、插值（多输出笛卡尔） |
| 字段访问与路径 | `.foo`、链式、`.[expr]`、`..`、`.[]`、切片（含字符串）、`.foo?` |
| 过滤与选择 | select、map、map_values、sort/sort_by、group_by、unique/unique_by、flatten、reverse、limit、range、first/last/nth、any/all、indices/index/rindex、contains/inside/in |
| 归约与累积 | reduce、foreach（含 extract）、label/break（异常解旋） |
| 条件与逻辑 | if/elif/else、and/or/not、`//` 备选、try/catch/`?`、error（error 值语义）、env/$ENV |
| 变量与函数 | as 绑定（含解构模式）、def（词法作用域、name/arity）、闭包过滤参数、值参数笛卡尔、$__loc__ |
| 对象/数组构造 | 简写、关键字键、动态键、`{$__loc__}`、多输出笛卡尔构造 |
| 对象/数组操作 | to/from/with_entries、getpath/setpath/delpaths/del/path/paths/pick、transpose、walk、`=`/`|=`/`+=` 等赋值运算符（路径求值） |
| 字符串 | tojson/fromjson（含 nan 前缀）、tonumber/toboolean（jq 错误文本）、ascii 大小写、ltrimstr/rtrimstr、split/join、explode/implode、@text/@json/@html/@uri/@urid/@csv/@tsv/@sh/@base64/@base64d |
| 正则 | test/match/capture/scan/splits/sub/gsub（flags g/i/x/s/m） |
| 时间 | now、gmtime/localtime、mktime、strftime/strflocaltime、strptime、to/fromdate |
| 数学 | floor/ceil/round/sqrt/pow/fabs/log/log2/log10/exp/exp2/exp10/sin/cos/tan/asin/acos/atan/atan2/sinh/cosh/tanh、isnan/isinfinite/isnormal、infinite/nan |
| I/O | input/inputs（输入流）、debug、halt_error、input_line_number |
| 组合 | tostream/fromstream、combinations、getpath 路径、leaf_paths、IN/INDEX/JOIN、bsearch、builtins、toboolean、trim、pick、scalars/objects/arrays/… 类型过滤 |

## 三、架构设计

### 3.1 架构：AST 直接执行模式（已落地）

```
jq 表达式
    ↓
JqLexer (词法分析，插值感知，指数/前导点数字)
    ↓
JqToken[]
    ↓
JqParser (递归下降 → AST，优先级对齐 jq 语法)
    ↓
JqAstNode (sealed 节点树，含解构/赋值/备选等)
    ↓
JqExecutor (急切求值 + 控制流异常近似流语义)
    ↓
JqValue 输出流 → Java 对象列表
```

**架构决策**：
1. **急切求值 + 控制流异常**：label/break 用 `JqBreakException` 解旋；limit/first/isempty/any/all 用 `JqShortCircuitList`+`JqStopException` 提前终止生成器。官方套件全部行为可用此模型覆盖，无需 fork/backtrack VM。
2. **函数语义**：`JqFunctionDef` 捕获定义环境（词法作用域）；过滤参数为调用点闭包（`JqClosureFn`）；值参数按输出笛卡尔绑定；函数按 name/arity 解析。
3. **路径体系**：`JqPathEval` 统一 path()/del()/赋值运算符的路径求值，切片以 `SlicePath` 整体拼接。
4. **解构**：`JqDestructurer`，`?//` 绑定所有备选变量的并集（未命中绑 null）。
5. **数字**：double 运算；字面量整数（Long）参与 `==` 时精确比较，算术结果为 double（jq 1.7.1 行为）；`%` 为饱和 int64 截断取模。

### 3.2 运行时组件

```
jq 表达式
    ↓
JqLexer (词法分析，插值感知)
    ↓
JqToken[]
    ↓
JqParser (递归下降 → AST)
    ↓
JqAstNode (sealed AST 节点树)
    ↓
JqDirectQuery / JqExecutor (AST 直接执行)
    ↓
JqValue 输出流 → Java 对象列表
```

### 3.3 AST 节点类型

| 节点类型 | 说明 | 对应 jq 语法 |
|---------|------|-------------|
| LiteralNode | 字面量 | `null`, `true`, `false`, `42`, `"str"` |
| IdentityNode | 恒等 | `.` |
| FieldAccessNode | 字段访问 | `.foo` |
| IndexAccessNode | 索引访问 | `.[0]`, `.[-1]` |
| SliceNode | 切片 | `.[1:3]`, `.[:]` |
| IteratorNode | 迭代 | `.[]` |
| RecursiveDescentNode | 递归下降 | `..` |
| PipeNode | 管道 | `a \| b` |
| CommaNode | 逗号（多输出） | `a, b` |
| FilterNode | 过滤 | `select(expr)` |
| ReduceNode | 归约 | `reduce expr as $x (init; body)` |
| ForEachNode | 循环 | `foreach expr as $x (init; update; extract)` |
| IfThenElseNode | 条件 | `if expr then expr else expr end` |
| TryCatchNode | 错误处理 | `try expr catch handler` |
| LabelBreakNode | 标签跳转 | `label $out \| break $out` |
| FunctionDefNode | 函数定义 | `def f: body` |
| FunctionCallNode | 函数调用 | `f`, `f(a)` |
| VariableNode | 变量引用 | `$x` |
| BindNode | 变量绑定 | `expr as $x \| body` |
| ObjectConstructNode | 对象构造 | `{a: .x, b: .y}` |
| ArrayConstructNode | 数组构造 | `[.a, .b]` |
| StringInterpNode | 字符串插值 | `"hello \(.)"` |
| FormatterNode | 格式化器 | `@base64`, `@csv` |
| MathOpNode | 数学运算 | `+`, `-`, `*`, `/`, `%` |
| ComparisonNode | 比较运算 | `==`, `!=`, `>`, `<` |
| BooleanNode | 逻辑运算 | `and`, `or`, `not` |
| UpdateNode | 更新操作 | `.foo += 1` |
| EmptyNode | 空输出 | `empty` |
| DebugNode | 调试输出 | `debug` |
| ErrorNode | 错误 | `error`, `error("msg")` |

### 3.4 执行模型

jq 的执行模型基于"输出流"：

```
每个表达式是一个函数：输入 → 输出流（可能多个值）

.       → [input]                    (恒等：返回输入本身)
.foo    → [input.foo]                (字段访问：返回字段值)
.[]     → [input[0], input[1], ...]  (迭代：展开数组)
a | b   → b(a[0]), b(a[1]), ...     (管道：对每个输出执行 b)
a, b    → a[0], ..., a[n], b[0], ..., b[m]  (逗号：合并输出)
empty   → []                         (空：无输出)
```

执行器维护一个"输出栈"，每个表达式从栈中取出输入，产生零个或多个输出压入栈中。

### 3.5 环境与作用域（JqEnvironment）

变量词法作用域栈 + 函数定义表 + 输入流 + 资源限额（调用深度、输出预算）。
fork 采用写时复制：作用域 Map 与函数定义表共享引用；所有绑定路径先 pushScope，
defineFunction 写前克隆，保证 fork 不泄漏绑定到调用方。

### 3.6 函数定义与调用

- `JqFunctionDef`：函数定义 + 定义环境快照（词法作用域），body 在定义环境 fork 中执行
- 参数两类：`$x` 值参数（调用点求值，多输出按笛卡尔绑定）；过滤参数为调用点闭包 `JqClosureFn`（引用处输入求值）
- 函数按 name/arity 解析（`f/0` 与 `f/1` 是不同函数，jq 同款语义）

### 3.7 惰性与控制流

- label/break：`JqBreakException` 精确解旋，try/catch 不拦截 break，break 前的输出保留
- limit/first/isempty/any/all：`JqShortCircuitList` 在收集到足够输出时抛 `JqStopException` 终止生成器

## 四、交付状态

全部阶段已交付（plan `ai-dev/plans/01-nop-jq-complete-jq-implementation.md` 已关闭）：

- 官方 jq 1.7.1 测试套件（jq.test）全量通过：430 动态用例（421 ok + 9 %%FAIL）；12 个模块依赖用例按 Non-Goal 跳过
- 性能：JMH 基准（`JqEngineBenchmark`）+ JFR 归因驱动的分配优化（打印批量转义、对象免拷贝构造、环境 fork 写时复制）；tojson 路径实测提升 ~11-25%
- 显式不做（Non-Goals，见 plan Deferred 记录）：fork/backtrack VM、模块系统、decNumber 字面量保留、Oniguruma 方言正则

## 五、性能对比策略

### 5.1 与 jq CLI 对比

jq CLI 的性能瓶颈在进程启动和文件 I/O，不是计算本身。公平对比方案：

1. **JNI 绑定 libjq**：通过 JNI 直接调用 jq 的 C 库，消除进程开销
2. **大文件基准测试**：使用 10MB+ JSON 文件，让计算时间远大于 I/O 时间
3. **预编译路径对比**：nop-jq 的编译缓存 vs jq 的重复解析

### 5.2 与 Jayway JsonPath 对比

nop-jq 的 JsonPath 实现已比 Jayway 快 3-30 倍（取决于场景）。jq 功能完成后，转换场景的性能对比更有意义。

## 六、测试策略

### 6.1 jq 官方测试移植

从 `~/sources/jq/tests/jq.test` 移植全部 616 个测试用例：
- 格式：`program` → `input` → `expected output`
- 转换为 JUnit 5 参数化测试
- 标记 `%%FAIL` 测试为预期失败

### 6.2 fastjson 测试移植

从 `~/sources/fastjson/src/test/java/com/alibaba/json/bvt/path/` 移植 97 个测试：
- 替换 `com.alibaba.fastjson.JSONPath` → `io.nop.jpath.NopJsonPath`
- 替换 `JSON.parse()` → `JsonTool.parse()`
- 处理 `NopCompiledJsonPath` vs `NopJsonPath` 类型问题

### 6.3 端到端测试

- 从 jq 表达式输入到结果输出的完整路径
- 从 JSON 字符串解析到查询执行的完整路径
- 从文件读取到查询执行的完整路径

## 七、风险与约束

### 7.1 技术风险

1. **jq 语义复杂度**：jq 的 fork/backtrack 语义（逗号产生多个结果、迭代器回溯）实现复杂度极高
2. **模块系统**：jq 的模块系统需要文件 I/O 和命名空间管理
3. **正则依赖**：部分 jq 功能依赖 Oniguruma 正则库

### 7.2 约束

1. 零外部依赖：不依赖任何第三方 JSON 库
2. 与 nop-xlang 集成：jq 表达式可在 XLang 中使用
3. 性能要求：常见场景不低于 jq CLI 的 50%
