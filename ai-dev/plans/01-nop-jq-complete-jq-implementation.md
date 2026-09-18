# 01 nop-jq 完整 jq 语义实现计划

> Plan Status: draft
> Last Reviewed: 2026-09-18
> Source: `ai-dev/design/nop-jq/02-jq-complete-design.md`
> Related: `docs/backlog/nop-jq-roadmap.md`

## Purpose

实现 jq 的完整语义，使 nop-jq 能够执行所有 jq 官方测试用例（616 个），并修复所有 fastjson JSONPath 测试（97 个）。

## Current Baseline

- JsonPath 引擎已完成：解析器 + 执行器 + 完整 API（220 个测试通过）
- jq 翻译器仅支持基础语法：35% 的 jq 测试通过（221/616）
- fastjson 测试未移植：自动转换后因 API 签名不匹配无法编译
- 性能基准测试未完成：与 jq CLI 的对比不公平（进程 I/O 开销）

## Goals

- 所有 616 个 jq 官方测试通过
- 所有 97 个 fastjson JSONPath 测试通过
- 性能达到 jq CLI 的 50% 以上（纯计算）
- 端到端测试覆盖：从 jq 表达式到结果输出

## Non-Goals

- 不实现 jq 的完整 VM（fork/backtrack 语义）
- 不实现 jq 的完整模块系统（import/module）
- 不实现 native 层面优化
- 不实现持续性能回归测试 CI 集成

## Scope

### In Scope

- jq AST 节点体系
- 流式执行引擎
- 完整的 jq 语法支持（字面量、字段访问、过滤、归约、条件、字符串操作、格式化器、对象/数组操作、函数定义）
- fastjson 测试移植
- 性能基准测试

### Out Of Scope

- jq 的 fork/backtrack VM
- jq 的完整模块系统
- native 优化
- CI 集成

## Execution Plan

### Phase 1: AST 与执行引擎基础

Status: planned
Targets: `nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/`

- Item Types: `Fix | Decision | Proof`

- [ ] 定义 AST 节点类型体系（LiteralNode, FieldAccessNode, PipeNode 等 20+ 种节点）
- [ ] 实现 JqLexer 词法分析器（支持所有 token 类型：标识符、数字、字符串、运算符、关键字）
- [ ] 实现 JqParser 语法分析器（递归下降，解析为 AST）
- [ ] 实现 JqExecutor 执行引擎（流式输出模型：输入 → 输出流）
- [ ] 实现基础值操作：字面量、字段访问、数组索引、管道、逗号
- [ ] 实现变量绑定：`as $x`、`$x` 引用
- [ ] 编写单元测试：基础值、字段访问、数组操作、管道、变量

Exit Criteria:

- [ ] 所有基础值测试通过（null/true/false/数字/字符串）
- [ ] 字段访问测试通过（`.foo`、`.foo.bar`、`.["foo"]`）
- [ ] 数组操作测试通过（`.[]`、`.[0]`、`.[-1]`、`.[1:3]`）
- [ ] 管道测试通过（`a | b`、`a, b`）
- [ ] 变量测试通过（`. as $x | $x`）
- [ ] **端到端验证**：从 jq 表达式字符串到结果输出的完整路径已验证
- [ ] **无静默跳过**：未实现的功能抛出 `UnsupportedOperationException`
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2: 过滤与选择

Status: planned
Targets: `nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/`

- Item Types: `Fix | Proof`

- [ ] 实现 `select(expr)` - 条件过滤
- [ ] 实现 `map(f)` / `map_values(f)` - 映射
- [ ] 实现 `sort` / `sort_by(f)` - 排序
- [ ] 实现 `group_by(f)` - 分组
- [ ] 实现 `unique` / `unique_by(f)` - 去重
- [ ] 实现 `flatten` / `flatten(n)` - 扁平化
- [ ] 实现 `reverse` - 反转
- [ ] 实现 `limit(n; expr)` - 限制数量
- [ ] 实现 `range(n)` / `range(n;m)` / `range(n;m;s)` - 范围生成
- [ ] 实现 `first(expr)` / `last(expr)` / `nth(n; expr)` - 取值
- [ ] 实现 `any(expr)` / `all(expr)` - 聚合判断
- [ ] 实现 `indices(s)` / `index(s)` - 搜索
- [ ] 实现 `inside(b)` / `contains(b)` - 包含判断
- [ ] 编写单元测试：所有过滤、排序、分组、去重操作

Exit Criteria:

- [ ] 所有过滤测试通过
- [ ] 所有排序测试通过
- [ ] 所有分组去重测试通过
- [ ] range 和 limit 测试通过
- [ ] **端到端验证**：从 jq 表达式到过滤结果的完整路径已验证
- [ ] **无静默跳过**：未实现的功能抛出异常
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3: 归约与累积

Status: planned
Targets: `nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/`

- Item Types: `Fix | Proof`

- [ ] 实现 `reduce expr as $x (init; body)` - 归约
- [ ] 实现 `foreach expr as $x (init; update; extract)` - 循环累积
- [ ] 实现 `label $out | break $out` - 标签跳转
- [ ] 实现 `repeat(expr)` - 重复
- [ ] 编写单元测试：所有归约、累积、标签跳转操作

Exit Criteria:

- [ ] 所有 reduce 测试通过
- [ ] foreach 测试通过
- [ ] label/break 测试通过
- [ ] **端到端验证**：从归约表达到累积结果的完整路径已验证
- [ ] **无静默跳过**：未实现的功能抛出异常
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4: 条件与错误处理

Status: planned
Targets: `nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/`

- Item Types: `Fix | Proof`

- [ ] 实现 `if expr then expr else expr end` - 条件（增强版，支持 elif）
- [ ] 实现 `try expr catch handler` - 错误捕获
- [ ] 实现 `try expr` - 静默错误
- [ ] 实现 `error` / `error("msg")` - 抛出错误
- [ ] 实现 `env` / `$ENV` - 环境变量
- [ ] 编写单元测试：所有条件、错误处理操作

Exit Criteria:

- [ ] 所有条件测试通过
- [ ] 所有 try-catch 测试通过
- [ ] error 测试通过
- [ ] **端到端验证**：从条件表达到分支结果的完整路径已验证
- [ ] **无静默跳过**：未实现的功能抛出异常
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5: 字符串与格式化器

Status: planned
Targets: `nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/`

- Item Types: `Fix | Proof`

- [ ] 实现字符串插值：`"hello \(.)"`
- [ ] 实现 `tojson` / `fromjson` - JSON 转换
- [ ] 实现 `@text`, `@json`, `@html`, `@uri`, `@urid` - 格式化器
- [ ] 实现 `@csv`, `@tsv` - 表格格式化器
- [ ] 实现 `@sh` - Shell 转义
- [ ] 实现 `@base64` / `@base64d` - Base64 编解码
- [ ] 实现 `ascii_downcase` / `ascii_upcase` - 大小写转换
- [ ] 实现 `ltrimstr(s)` / `rtrimstr(s)` - 去除前缀/后缀
- [ ] 实现 `test(r)` - 正则测试
- [ ] 实现 `match(r)` / `capture(r)` - 正则匹配
- [ ] 实现 `scan(r)` - 正则扫描
- [ ] 实现 `splits(r)` - 正则分割
- [ ] 实现 `sub(r; s)` / `gsub(r; s)` - 正则替换
- [ ] 实现时间函数：`now`, `fromdate`, `todate`, `strftime`, `strptime`, `gmtime`, `localtime`, `mktime`
- [ ] 编写单元测试：所有字符串操作和格式化器

Exit Criteria:

- [ ] 所有字符串测试通过
- [ ] 所有格式化器测试通过
- [ ] 正则操作测试通过
- [ ] 时间函数测试通过
- [ ] **端到端验证**：从字符串表达到格式化结果的完整路径已验证
- [ ] **无静默跳过**：未实现的功能抛出异常
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6: 对象与数组操作

Status: planned
Targets: `nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/`

- Item Types: `Fix | Proof`

- [ ] 实现对象简写：`{a, b}` (等价于 `{a: .a, b: .b}`)
- [ ] 实现 `to_entries` / `from_entries` / `with_entries` - 条目操作
- [ ] 实现 `getpath(path)` / `setpath(path; value)` / `delpaths(paths)` - 路径操作
- [ ] 实现 `has(key)` / `in(obj)` - 存在性检查
- [ ] 实现 `keys` / `values` / `keys_unsorted` - 键值操作
- [ ] 实现 `transpose` - 转置
- [ ] 实现数组切片赋值
- [ ] 实现更新操作：`|=` (apply), `+=`, `-=`
- [ ] 实现 `type` / `length` / `tostring` / `tonumber` / `empty` / `null` / `nan` / `infinite` / `isnan` / `isinfinite` / `floor` / `ceil` / `round` / `sqrt` / `pow` / `fabs`
- [ ] 实现 `not` / `and` / `or` / `xor` - 逻辑运算
- [ ] 实现 `input` / `inputs` - 输入操作
- [ ] 实现 `debug` / `stderr` - 调试输出
- [ ] 编写单元测试：所有对象/数组操作、类型检查、数学运算

Exit Criteria:

- [ ] 所有对象构造测试通过
- [ ] 所有数组操作测试通过
- [ ] 路径操作测试通过
- [ ] 更新操作测试通过
- [ ] 类型检查和数学运算测试通过
- [ ] **端到端验证**：从对象/数组操作到结果的完整路径已验证
- [ ] **无静默跳过**：未实现的功能抛出异常
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 7: 函数定义与模块系统

Status: planned
Targets: `nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/`

- Item Types: `Fix | Proof`

- [ ] 实现 `def f: body` - 无参函数
- [ ] 实现 `def f(x): body` - 有参函数
- [ ] 实现递归函数
- [ ] 实现 `import "module" as m` - 模块导入（简化版，仅支持内置模块）
- [ ] 实现 `module` - 模块定义（简化版）
- [ ] 实现 `modulemeta` - 模块元数据（简化版）
- [ ] 编写单元测试：所有函数定义、递归、模块操作

Exit Criteria:

- [ ] 所有函数定义测试通过
- [ ] 递归函数测试通过
- [ ] 模块系统测试通过
- [ ] **端到端验证**：从函数定义到调用结果的完整路径已验证
- [ ] **无静默跳过**：未实现的功能抛出异常
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 8: fastjson 测试移植与性能优化

Status: planned
Targets: `nop-kernel/nop-jq/src/test/java/io/nop/jq/jsonpath/`, `nop-kernel/nop-jq/src/test/java/io/nop/jq/benchmark/`

- Item Types: `Fix | Proof`

- [ ] 移植 fastjson 97 个 JSONPath 测试到 nop-jq
- [ ] 修复所有 API 签名不匹配问题（`NopCompiledJsonPath` vs `NopJsonPath`）
- [ ] 修复所有类型转换问题
- [ ] 实现 JIT 编译优化（热点路径）
- [ ] 实现内存池优化（减少 GC）
- [ ] 编写性能基准测试（与 jq CLI 对比，使用大文件消除 I/O 开销）
- [ ] 生成性能对比报告

Exit Criteria:

- [ ] 所有 616 个 jq 官方测试通过
- [ ] 所有 97 个 fastjson 测试通过
- [ ] 性能达到 jq CLI 的 50% 以上（纯计算，不含 I/O）
- [ ] **端到端验证**：从 jq 表达式到结果输出的完整路径已验证
- [ ] **无静默跳过**：未实现的功能抛出异常
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 所有 616 个 jq 官方测试通过
- [ ] 所有 97 个 fastjson 测试通过
- [ ] 所有 Phase 的 Exit Criteria 已勾选
- [ ] 性能达到 jq CLI 的 50% 以上
- [ ] 端到端测试覆盖
- [ ] 无静默跳过
- [ ] 文档已更新
- [ ] 独立 closure audit 已完成

## Deferred But Adjudicated

### jq 完整 VM

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: jq 的 fork/backtrack 语义实现复杂度极高，当前翻译器模式已覆盖 80% 的使用场景
- Successor Required: `yes`
- Successor Path: 后续独立计划

### jq 完整模块系统

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 模块系统需要文件 I/O 和命名空间管理，当前简化版已满足基本需求
- Successor Required: `yes`
- Successor Path: 后续独立计划

## Non-Blocking Follow-ups

- 性能优化：JIT 编译、内存池、向量化
- CI 集成：持续性能回归测试
- 文档更新：用户手册、API 文档

## Closure

Status Note: 待完成
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: 待填写
- Evidence: 待填写

Follow-up:

- 待填写
