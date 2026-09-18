# nop-jq 完整功能设计文档

> Status: active
> Created: 2026-09-18
> Last Reviewed: 2026-09-18
> Source: jq 1.7.1 官方测试套件 (~/sources/jq/tests/jq.test)

## 一、产品定位

nop-jq 是 Nop 平台的自研 JSON 查询引擎，同时支持两种语法：
- **JsonPath 语法**：面向提取场景，兼容 fastjson API
- **jq 语法**：面向转换场景，兼容 jq CLI 核心语法

当前状态：JsonPath 引擎已完成（解析器 + 执行器 + 完整 API），jq 翻译器仅支持基础语法（约 35% 的 jq 测试通过）。

## 二、jq 功能全景

基于 jq 1.7.1 官方测试套件（616 个测试用例）分析，jq 功能分为以下类别：

### 2.1 基础值与字面量

| 功能 | 示例 | 当前状态 |
|------|------|---------|
| null/true/false 字面量 | `null`, `true`, `false` | ✅ 已支持 |
| 数字字面量 | `42`, `-1`, `3.14` | ✅ 已支持 |
| 字符串字面量 | `"hello"` | ✅ 已支持 |
| 字符串转义 | `\n`, `\t`, `\u0000` | ❌ 未实现 |
| 字符串插值 | `"inter\("pol" + "ation")"` | ❌ 未实现 |

### 2.2 字段访问与路径

| 功能 | 示例 | 当前状态 |
|------|------|---------|
| 点号访问 | `.foo` | ✅ 已支持 |
| 链式访问 | `.foo.bar` | ✅ 已支持 |
| 括号访问 | `.["foo"]` | ✅ 已支持 |
| 递归下降 | `..` | ✅ 已支持 |
| 数组迭代 | `.[]` | ✅ 已支持 |
| 数组索引 | `.[0]`, `.[-1]` | ✅ 已支持 |
| 数组切片 | `.[1:3]` | ✅ 已支持 |
| 可选操作 | `.foo?`, `.[]?` | ❌ 未实现 |

### 2.3 过滤与选择

| 功能 | 示例 | 当前状态 |
|------|------|---------|
| select | `select(. > 10)` | ✅ 已支持 |
| map | `map(.foo)` | ✅ 已支持 |
| map_values | `map_values(.foo)` | ❌ 未实现 |
| sort | `sort` | ❌ 未实现 |
| sort_by | `sort_by(.foo)` | ❌ 未实现 |
| group_by | `group_by(.foo)` | ❌ 未实现 |
| unique | `unique` | ❌ 未实现 |
| unique_by | `unique_by(.foo)` | ❌ 未实现 |
| flatten | `flatten` | ❌ 未实现 |
| flatten | `flatten(1)` | ❌ 未实现 |
| reverse | `reverse` | ❌ 未实现 |
| indices | `indices(",")` | ❌ 未实现 |
| index | `index(",")` | ❌ 未实现 |
| inside | `inside(b)` | ❌ 未实现 |
| contains | `contains(b)` | ❌ 未实现 |
| limit | `limit(3; .[])` | ❌ 未实现 |
| range | `range(10)`, `range(0;10;2)` | ❌ 未实现 |
| repeat | `repeat(.foo)` | ❌ 未实现 |
| recurse | `recurse`, `recurse(.a)` | ❌ 未实现 |
| until | `until(. >= 100)` | ❌ 未实现 |
| while | `while(. < 100)` | ❌ 未实现 |
| first | `first(.[] | select(. > 5))` | ❌ 未实现 |
| last | `last(.[] | select(. > 5))` | ❌ 未实现 |
| nth | `nth(2; .[])` | ❌ 未实现 |
| any | `any(.[] | . > 5)` | ❌ 未实现 |
| all | `all(.[] | . > 5)` | ❌ 未实现 |

### 2.4 归约与累积

| 功能 | 示例 | 当前状态 |
|------|------|---------|
| reduce | `reduce .[] as $x (0; . + $x)` | ❌ 未实现 |
| foreach | `foreach .[] as $x (0; . + $x)` | ❌ 未实现 |
| label/break | `label $out \| break $out` | ❌ 未实现 |

### 2.5 条件与逻辑

| 功能 | 示例 | 当前状态 |
|------|------|---------|
| if-then-else | `if . > 10 then "big" else "small" end` | ✅ 已支持 |
| if-then-elif | `if . > 10 then "big" elif . > 5 then "med" end` | ✅ 已支持 |
| and/or/not | `. > 5 and . < 10` | ✅ 已支持 |
| try-catch | `try .foo catch "error"` | ❌ 未实现 |
| try | `try .foo` | ❌ 未实现 |
| error | `error`, `error("msg")` | ❌ 未实现 |
| env | `$ENV.HOME` | ❌ 未实现 |

### 2.6 变量绑定

| 功能 | 示例 | 当前状态 |
|------|------|---------|
| as 绑定 | `. as $x \| $x` | ❌ 未实现 |
| $var 引用 | `$x` | ❌ 未实现 |
| 变量赋值 | `setpath(["a","b"]; 1)` | ❌ 未实现 |
| 自定义函数 | `def f: . + 1; f` | ❌ 未实现 |
| 递归函数 | `def f: if . > 0 then f(. - 1) else . end; f(10)` | ❌ 未实现 |

### 2.7 对象与数组构造

| 功能 | 示例 | 当前状态 |
|------|------|---------|
| 对象构造 | `{a: .x, b: .y}` | ✅ 已支持 |
| 对象简写 | `{a, b}` | ❌ 未实现 |
| 对象插入 | `.foo += 1` | ❌ 未实现 |
| 数组构造 | `[.a, .b]` | ✅ 已支持 |
| 数组切片赋值 | `.[:3] = [1,2,3]` | ❌ 未实现 |
| to_entries | `to_entries` | ❌ 未实现 |
| from_entries | `from_entries` | ❌ 未实现 |
| with_entries | `with_entries(select(.value > 10))` | ❌ 未实现 |
| getpath | `getpath(["a","b"])` | ❌ 未实现 |
| setpath | `setpath(["a","b"]; 1)` | ❌ 未实现 |
| delpaths | `delpaths([["a"]])` | ❌ 未实现 |
| has | `has("foo")` | ❌ 未实现 |
| in | `"foo" in .` | ❌ 未实现 |

### 2.8 字符串操作

| 功能 | 示例 | 当前状态 |
|------|------|---------|
| 字符串拼接 | `"a" + "b"` | ✅ 已支持 |
| 字符串长度 | `"foo" \| length` | ✅ 已支持 |
| tojson | `tojson` | ❌ 未实现 |
| fromjson | `fromjson` | ❌ 未实现 |
| @text | `@text` | ❌ 未实现 |
| @json | `@json` | ❌ 未实现 |
| @html | `@html` | ❌ 未实现 |
| @uri | `@uri` | ❌ 未实现 |
| @urid | `@urid` | ❌ 未实现 |
| @csv | `@csv` | ❌ 未实现 |
| @tsv | `@tsv` | ❌ 未实现 |
| @sh | `@sh` | ❌ 未实现 |
| @base64 | `@base64` | ❌ 未实现 |
| @base64d | `@base64d` | ❌ 未实现 |
| ascii_downcase | `ascii_downcase` | ❌ 未实现 |
| ascii_upcase | `ascii_upcase` | ❌ 未实现 |
| ltrimstr | `ltrimstr("foo")` | ❌ 未实现 |
| rtrimstr | `rtrimstr("foo")` | ❌ 未实现 |
| test | `test("^a")` | ❌ 未实现 |
| match | `match("a(b)c")` | ❌ 未实现 |
| capture | `capture("(?<x>[0-9]+)")` | ❌ 未实现 |
| scan | `scan("[0-9]+")` | ❌ 未实现 |
| splits | `splits(",")` | ❌ 未实现 |
| sub | `sub("foo"; "bar")` | ❌ 未实现 |
| gsub | `gsub("foo"; "bar")` | ❌ 未实现 |
| strftime | `strftime("%Y-%m-%d")` | ❌ 未实现 |
| strptime | `strptime("%Y-%m-%d")` | ❌ 未实现 |
| gmtime | `gmtime` | ❌ 未实现 |
| localtime | `localtime` | ❌ 未实现 |
| mktime | `mktime` | ❌ 未实现 |
| now | `now` | ❌ 未实现 |
| fromdate | `fromdate` | ❌ 未实现 |
| todate | `todate` | ❌ 未实现 |

### 2.9 数学与类型操作

| 功能 | 示例 | 当前状态 |
|------|------|---------|
| 算术运算 | `+`, `-`, `*`, `/`, `%` | ✅ 已支持 |
| 比较运算 | `==`, `!=`, `>`, `<`, `>=`, `<=` | ✅ 已支持 |
| 负数 | `-.` | ❌ 未实现 |
| floor | `floor` | ❌ 未实现 |
| ceil | `ceil` | ❌ 未实现 |
| round | `round` | ❌ 未实现 |
| sqrt | `sqrt` | ❌ 未实现 |
| pow | `pow(2;3)` | ❌ 未实现 |
| fabs | `fabs` | ❌ 未实现 |
| nan | `nan` | ❌ 未实现 |
| isnan | `isnan` | ❌ 未实现 |
| infinite | `infinite` | ❌ 未实现 |
| isinfinite | `isinfinite` | ❌ 未实现 |
| type | `type` | ❌ 未实现 |
| length | `length` | ✅ 已支持 |
| keys | `keys` | ❌ 未实现 |
| values | `values` | ❌ 未实现 |
| keys_unsorted | `keys_unsorted` | ❌ 未实现 |
| tostring | `tostring` | ❌ 未实现 |
| tonumber | `tonumber` | ❌ 未实现 |
| empty | `empty` | ❌ 未实现 |
| null | `null` | ✅ 已支持 |
| env | `env` | ❌ 未实现 |

### 2.10 I/O 与流

| 功能 | 示例 | 当前状态 |
|------|------|---------|
| input | `input` | ❌ 未实现 |
| inputs | `inputs` | ❌ 未实现 |
| debug | `debug` | ❌ 未实现 |
| stderr | `stderr` | ❌ 未实现 |

### 2.11 高级特性

| 功能 | 示例 | 当前状态 |
|------|------|---------|
| 负数索引 | `.[-1]` | ✅ 已支持 |
| 字符串插值 | `"inter\("pol" + "ation")"` | ❌ 未实现 |
| 多输出 | `.foo, .bar` | ❌ 未实现 |
| 逗号表达式 | `{a:1}, {b:2}` | ❌ 未实现 |
| try with label | `try .[] catch .` | ❌ 未实现 |
| reduce with range | `reduce range(5) as $x (0; .+$x)` | ❌ 未实现 |
| 自定义函数带参数 | `def f(x): . + x; f(1)` | ❌ 未实现 |
| 递归函数 | `def f: if . > 0 then f(. - 1) else . end` | ❌ 未实现 |
| 模块系统 | `import "module" as m` | ❌ 未实现 |
| 格式化器 | `@base64`, `@csv`, `@tsv` 等 | ❌ 未实现 |

## 三、架构设计

### 3.1 当前架构

```
jq 表达式
    ↓
JqLexer (词法分析)
    ↓
JqToken[]
    ↓
JqParser (语法分析 → XLang 翻译)
    ↓
XLang 表达式字符串
    ↓
XLang 引擎执行 (deferred)
```

**问题**：当前架构是"翻译器"模式，将 jq 翻译为 XLang 表达式。这限制了：
1. 无法支持 jq 特有语义（try-catch、label/break、empty、多输出）
2. 依赖 XLang 引擎的执行能力
3. 无法直接执行 jq 表达式

### 3.2 建议架构：直接执行模式

```
jq 表达式
    ↓
JqLexer (词法分析)
    ↓
JqToken[]
    ↓
JqParser (语法分析 → AST)
    ↓
JqAstNode (AST 节点树)
    ↓
JqCompiler (AST → 可执行指令)
    ↓
JqProgram (指令序列)
    ↓
JqExecutor (执行引擎)
    ↓
结果流
```

**核心设计决策**：

1. **AST 直接执行**：不翻译为 XLang，直接执行 jq AST。理由：jq 有大量 XLang 不支持的语义（empty、多输出、label/break、try-catch 等）。

2. **惰性流式执行**：jq 的核心语义是"流"——一个表达式可以产生多个输出。例如 `.[]` 产生 N 个输出，`.foo.bar` 对每个输出继续处理。这需要流式执行模型。

3. **变量作用域**：jq 的变量是词法作用域，`as $x` 绑定的变量在整个管道中可用。需要一个运行时环境来管理变量绑定。

4. **函数定义**：`def f: body` 定义的函数需要在编译时注册，执行时展开。支持递归调用。

5. **错误处理**：`try expr catch handler` 需要在执行时捕获异常并继续执行 catch 分支。

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

### 3.5 变量作用域

```python
# 运行时环境
class JqEnv:
    scopes: List[Map<String, JqValue>>  # 词法作用域栈
    
    def bind(self, name, value):
        self.scopes[-1][name] = value
    
    def lookup(self, name):
        for scope in reversed(self.scopes):
            if name in scope:
                return scope[name]
        raise JqError(f"Undefined variable: ${name}")
    
    def push_scope(self):
        self.scopes.append({})
    
    def pop_scope(self):
        self.scopes.pop()
```

### 3.6 函数定义与调用

```python
# 函数注册表
class JqFunctionRegistry:
    builtins: Map<String, JqFunction>     # 内置函数
    user_defs: Map<String, JqFunctionDef> # 用户定义函数
    
    def call(self, name, args, input):
        if name in self.builtins:
            return self.builtins[name](args, input)
        if name in self.user_defs:
            return self.user_defs[name].apply(args, input)
        raise JqError(f"Unknown function: {name}")

# 用户定义函数
class JqFunctionDef:
    params: List<String]  # 参数名
    body: JqAstNode       # 函数体 AST
    
    def apply(self, args, input):
        env = JqEnv()
        env.push_scope()
        for param, arg in zip(self.params, args):
            env.bind(param, arg)
        return JqExecutor().execute(self.body, input, env)
```

## 四、实现路线图

### Phase 1: AST 与执行引擎基础

**目标**：建立 AST 节点体系和执行引擎，支持最基础的 jq 语法。

**包含功能**：
- AST 节点类型定义
- 词法分析器（支持所有 token 类型）
- 语法分析器（解析为 AST）
- 执行引擎（流式输出模型）
- 基础值操作：字面量、字段访问、数组索引、管道、逗号
- 变量绑定：`as $x`、`$x` 引用

**验证标准**：
- 所有基础值测试通过（null/true/false/数字/字符串）
- 字段访问测试通过（`.foo`、`.foo.bar`、`.["foo"]`）
- 数组操作测试通过（`.[]`、`.[0]`、`.[-1]`、`.[1:3]`）
- 管道测试通过（`a | b`、`a, b`）
- 变量测试通过（`. as $x | $x`）

### Phase 2: 过滤与选择

**目标**：实现完整的过滤和选择功能。

**包含功能**：
- `select(expr)` - 条件过滤
- `map(f)` / `map_values(f)` - 映射
- `sort` / `sort_by(f)` - 排序
- `group_by(f)` - 分组
- `unique` / `unique_by(f)` - 去重
- `flatten` / `flatten(n)` - 扁平化
- `reverse` - 反转
- `limit(n; expr)` - 限制数量
- `range(n)` / `range(n;m)` / `range(n;m;s)` - 范围生成
- `first(expr)` / `last(expr)` / `nth(n; expr)` - 取值
- `any(expr)` / `all(expr)` - 聚合判断
- `indices(s)` / `index(s)` - 搜索
- `inside(b)` / `contains(b)` - 包含判断

**验证标准**：
- 所有过滤测试通过
- 所有排序测试通过
- 所有分组去重测试通过
- range 和 limit 测试通过

### Phase 3: 归约与累积

**目标**：实现 reduce、foreach 和 label/break。

**包含功能**：
- `reduce expr as $x (init; body)` - 归约
- `foreach expr as $x (init; update; extract)` - 循环累积
- `label $out | break $out` - 标签跳转
- `repeat(expr)` - 重复

**验证标准**：
- 所有 reduce 测试通过
- foreach 测试通过
- label/break 测试通过

### Phase 4: 条件与错误处理

**目标**：实现 if-then-else、try-catch 和 error。

**包含功能**：
- `if expr then expr else expr end` - 条件（增强版，支持 elif）
- `try expr catch handler` - 错误捕获
- `try expr` - 静默错误
- `error` / `error("msg")` - 抛出错误
- `env` / `$ENV` - 环境变量

**验证标准**：
- 所有条件测试通过
- 所有 try-catch 测试通过
- error 测试通过

### Phase 5: 字符串与格式化器

**目标**：实现完整的字符串操作和格式化器。

**包含功能**：
- 字符串插值：`"hello \(.)"`
- `tojson` / `fromjson` - JSON 转换
- `@text`, `@json`, `@html`, `@uri`, `@urid` - 格式化器
- `@csv`, `@tsv` - 表格格式化器
- `@sh` - Shell 转义
- `@base64` / `@base64d` - Base64 编解码
- `ascii_downcase` / `ascii_upcase` - 大小写转换
- `ltrimstr(s)` / `rtrimstr(s)` - 去除前缀/后缀
- `test(r)` - 正则测试
- `match(r)` / `capture(r)` - 正则匹配
- `scan(r)` - 正则扫描
- `splits(r)` - 正则分割
- `sub(r; s)` / `gsub(r; s)` - 正则替换
- 时间函数：`now`, `fromdate`, `todate`, `strftime`, `strptime`, `gmtime`, `localtime`, `mktime`

**验证标准**：
- 所有字符串测试通过
- 所有格式化器测试通过
- 正则操作测试通过
- 时间函数测试通过

### Phase 6: 对象与数组操作

**目标**：实现完整的对象和数组操作。

**包含功能**：
- 对象简写：`{a, b}` (等价于 `{a: .a, b: .b}`)
- `to_entries` / `from_entries` / `with_entries` - 条目操作
- `getpath(path)` / `setpath(path; value)` / `delpaths(paths)` - 路径操作
- `has(key)` / `in(obj)` - 存在性检查
- `keys` / `values` / `keys_unsorted` - 键值操作
- `transpose` - 转置
- 数组切片赋值
- 更新操作：`|=` (apply), `+=`, `-=`

**验证标准**：
- 所有对象构造测试通过
- 所有数组操作测试通过
- 路径操作测试通过
- 更新操作测试通过

### Phase 7: 函数定义与模块系统

**目标**：实现完整的函数定义和模块系统。

**包含功能**：
- `def f: body` - 无参函数
- `def f(x): body` - 有参函数
- 递归函数
- `import "module" as m` - 模块导入
- `module` - 模块定义
- `modulemeta` - 模块元数据

**验证标准**：
- 所有函数定义测试通过
- 递归函数测试通过
- 模块系统测试通过

### Phase 8: 性能优化与测试移植

**目标**：性能优化，移植 fastjson 测试，修复所有编译问题。

**包含功能**：
- JIT 编译优化（热点路径）
- 内存池优化（减少 GC）
- 移植 fastjson 97 个 JSONPath 测试
- 修复所有 API 签名不匹配问题
- 性能基准测试（与 jq CLI 对比）

**验证标准**：
- 所有 616 个 jq 官方测试通过
- 所有 97 个 fastjson 测试通过
- 性能达到 jq CLI 的 50% 以上（纯计算，不含 I/O）

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
- 替换 `com.alibaba.fastjson.JSONPath` → `io.nop.jq.jsonpath.NopJsonPath`
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
