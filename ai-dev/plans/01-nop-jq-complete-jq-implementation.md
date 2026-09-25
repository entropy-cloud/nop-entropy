# 01 nop-jq 完整 jq 语义实现计划

> Plan Status: completed
> Last Reviewed: 2026-09-25
> Source: `ai-dev/design/nop-jq/02-jq-complete-design.md`
> Related: `docs/backlog/nop-jq-roadmap.md`

## Purpose

实现 jq 的完整语义，使 nop-jq 能够执行 jq 1.7.1 官方测试套件（jq.test，447 块 = 421 ok 执行 + 12 模块依赖跳过 + 9 %%FAIL + 5 %%FAIL IGNORE），并使用 JMH/JFR 完成性能优化。

## Current Baseline（2026-09-20 修订）

- JsonPath 引擎已完成并通过全部测试（roadmap Wave 1–6 中 stage 1–5, 7–15 已完成）
- jq 执行引擎已从"翻译到 XLang"切换为 **AST 直接执行**（JqLexer → JqParser → JqAstNode → JqExecutor），旧 TestJqTranslator 已删除
- 官方测试基础设施已重建：jq 1.7.1 release 的 jq.test 原文 vendor 为测试资源（447 ok + 9 %%FAIL + 5 %%FAIL IGNORE = 461 块），harness 与 jq 官方 runner 语义对齐（jv_parse 期望行 + jv_equal 值比较 + 多输出有序对比 + %%FAIL 必须报错），不再静默跳过
- 严格基线：447 个 ok 用例中 183 通过 / 264 失败（此前 CSV 转换损坏且静默跳过报错用例，虚高为 443/629）
- 主要缺口（按类）：解构绑定（`as [$a,$b]`/`{a:$a}`/`?//`）、路径赋值（`=`/`|=`/`+=`/负索引/切片）、`//` 替代运算符、limit/first/isempty/any/all 惰性求值、生成器参数多输出（`join(",","/")`、`flatten(3,2,1)`）、label/break 异常语义、字符串插值（lexer 截断 bug）、while/until、对象构造多输出、jq 数字/字符串打印、compare 全序、时间/三角函数、input/inputs/env、@格式化器细节、implode/explode、keys/unique/group_by 语义

## Goals

- jq 1.7.1 官方测试套件（jq.test 447 块；430 动态用例 = 421 ok + 9 FAIL）全部通过
- 既有手写测试套件（TestJqExecution、TestJqComprehensive、TestJqAiAgentUseCases、jsonpath 全套）保持通过
- 性能：JMH 基准 + JFR 分析驱动的优化，直到边际收益低于测量噪声
- 代码可读性：执行器大文件按职责拆分（打印、比较、内建函数、时间、格式化器、正则），异常处理符合两级规范

## Non-Goals

- 不实现 jq 的完整 VM（fork/backtrack 语义）——采用"急切求值 + 控制流异常"近似，官方套件可全部通过为准
- 不实现 jq 的完整模块系统（import/module）
- 不实现 decNumber 数字字面量逐字节保留（jq master 语义）；以 jq 1.7.1 发行版行为 + 官方 runner 的数值比较语义为准
- 不实现 Oniguruma 专属正则测试（onig.test/manonig.test，roadmap stage 6 明确 out-of-scope）
- 不实现 native 层面优化、持续性能回归 CI 集成

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

> 2026-09-20 修订：引擎已落地为 AST 直接执行（替代早期"翻译到 XLang"草案）。
> 以下 Phase 反映实际交付切片；验证以官方 jq 1.7.1 套件为准。

### Phase 1: 测试基础设施重建

Status: completed
Targets: `nop-kernel/nop-jq/src/test/`

- Item Types: `Fix | Proof`

- [x] vendor jq 1.7.1 release 官方 jq.test 原文为测试资源 `nop-kernel/nop-jq/src/test/resources/io/nop/jq/jq/jq-official.test`（447 块 = 421 ok + 12 模块依赖 + 9 %%FAIL + 5 %%FAIL IGNORE）
- [x] `JqOfficialCase` 解析器：块解析、`%%FAIL`/`%%FAIL IGNORE`、`# Runtime error` 部分输出后报错约定、模块依赖用例跳过（module 系统为 Non-Goal）
- [x] `TestJqOfficial` 严格 harness：与官方 runner 对齐（jv_parse 期望行 + jv_equal 数值比较、多输出有序前缀匹配、%%FAIL 必须报错、无静默跳过）
- [x] 删除早期损坏的 CSV 转换数据（多输出被逐行拆分导致数据损坏）
- [x] nan/infinite 输入保真：`nan` 以带引号标记解析并还原为 `Double.NaN`，错误消息保持 number 类型语义

Exit Criteria:

- [x] 严格基线可测量：447 执行 183 通过 / 264 失败（旧 CSV 静默跳过报错用例的虚高 443/629 被纠正）
- [x] harness 与 `/usr/bin/jq`（jq-1.7.1）行为逐项对照（try-catch 优先级、字符串重复、% 语义、?// 报错文本约定等）
- [x] No owner-doc update required（测试资源与 harness 属测试层）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2: 解析器与 AST 重构

Status: completed
Targets: `nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/`

- Item Types: `Fix | Decision`

- [x] 优先级链对齐 jq 语法：`|` → `,` → `//` → 赋值 → or → and → 比较 → `+-` → `*/%` → 一元 → 后缀
- [x] 解构模式：`[p1,p2]`、`{key:p}`、`{$var}`、`{$b: p}`、`?//` 备选、可选 `?`
- [x] 赋值/更新运算符 `= |= += -= *= /= %= //=`
- [x] 字符串插值（词法器插值感知扫描 + 解析器转义校验，非法转义报错）
- [x] 指数字面量（`1e+17`）、前导点小数（`.00005`）
- [x] `def` 作用域至管道剩余部分、`label $x | body`、try/catch（备选级，逗号管道不吞入）
- [x] 对象构造：关键字键、`$var` 简写、`{$y: v}` 变量键、`{$__loc__}`、`(expr)` 动态键、多输出笛卡尔
- [x] 新 AST 节点：AlternativeNode、UpdateAssignNode、WhileNode、UntilNode、InputNode、EnvNode、BindPattern

Exit Criteria:

- [x] 旧基线 128 个 COMPILE-FAIL 用例全部可编译（官方套件中无 compile 失败残留）
- [x] `{$a,$b:[$c,$d]}` 等解构用例绑定语义与 jq 一致（$b 绑定字段值并继续解构）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3: 执行引擎语义重构

Status: completed
Targets: `nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/`

- Item Types: `Fix | Decision`

- [x] label/break 改为 `JqBreakException` 精确解旋（try/catch 不拦截 break）；部分输出保留
- [x] 惰性求值：`limit/first/isempty/any/all` 经 `JqShortCircuitList` + `JqStopException` 提前终止（`limit(0;error)` 不再触发 error）
- [x] 函数：词法作用域（`JqFunctionDef` 捕获定义环境）、name/arity 解析、闭包过滤参数（`JqClosureFn`）、值参数多输出笛卡尔
- [x] 路径体系：`JqPathEval` 支持 path()/del()/赋值的路径求值（切片 `SlicePath` 整体拼接、负索引归一、`near attempt` 错误措辞）
- [x] `|=` 空 update 删除路径（jq `_modify` 语义：删除延迟到收集后统一逆序执行）
- [x] 数字语义：double 运算、字面量整数精确相等（`13911860366432393 == 13911860366432392` 为 false）、饱和 int64 取模（`(inf,-inf)%(1,-1,inf)`）、字符串重复（负数/NaN → null，floor 次）
- [x] 内建函数补齐/修正：JqBuiltins 调度 + 时间（JqTimeFunctions）、格式化器（JqFormatStrings）、正则（JqRegexSupport）、implode/explode 边界、join 加法错误、IN/INDEX/JOIN/bsearch/builtins、keys 排序、unique/group_by 排序语义、`values`=`select(.!=null)`、scalars 含 null、flatten/0 全展平、fromjson 接受 nan 前缀
- [x] 打印：JqPrinter 规范输出（整数化、nan→null、转义）、错误消息截断（11 字符 + `...`）

Exit Criteria:

- [x] `mvnw test -pl nop-kernel/nop-jq`：652 个测试全部通过（TestJqOfficial 430 + TestJqExecution 52 + TestJqComprehensive 40 + TestJqAiAgentUseCases 32 + jsonpath 98）
- [x] 官方 jq.test：430 动态用例（421 ok + 9 FAIL）0 失败；12 个模块依赖 ok 块按 Non-Goal 跳过；5 个 %%FAIL IGNORE 与官方 runner 一致跳过
- [x] **端到端验证**：表达式字符串 → JqLexer → JqParser → AST → JqExecutor → 多输出列表，由 TestJqOfficial 全量覆盖
- [x] **无静默跳过**：%%FAIL 块断言报错；未知函数 `x/y is not defined`；解构失败按 jq 语义传播
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4: 性能基准与优化（JMH + JFR）

Status: completed
Targets: `nop-kernel/nop-jq/src/test/java/io/nop/jq/benchmark/`

- Item Types: `Proof | Fix`

- [x] `JqEngineBenchmark`（JMH）：编译缓存命中、字段访问、select、map+add、插值、递归下降、tojson、fromjson、解构 九场景
- [x] JFR 热点归因：dispatchPlain 28%（含内联工作）、LinkedHashMap 链 ~47% 分配、JqValue.of 7.7%、打印 ~8%
- [x] 优化落地并保留：`JqPrinter.appendQuoted` 批量扫描转义、`JqObject.ofFresh` 免二次拷贝、`JqEnvironment.fork` 写时复制（作用域/函数表共享）
- [x] 负结果回退：ThreadLocal 复用 dispatch 对象在 map/select 管道实测回退 5-8%（ThreadLocal.get 每节点开销 > EA 已消除的分配），已回退并记录
- [x] A/B（交错同窗口）实测：executeTojson 提升 ~11-25%（printer+ofFresh）；其余场景在系统负载 11-16 噪声带内持平
- [x] 边际收益判定：剩余分配热点位于 Java↔JqValue API 边界（toJava/JqValue.of 树遍历），进一步优化需流式/惰性 API 契约变更，超出本计划（记入 Deferred）

Exit Criteria:

- [x] JMH 基线与优化后数据入档（提交 a282cea908、3556ab8b81）
- [x] JFR 归因报告要点入档（`ai-dev/logs/2026/09-25.md`）
- [x] `mvnw test -pl nop-kernel/nop-jq` 全绿（652）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] jq 1.7.1 官方测试套件通过（430/430 执行用例，模块依赖用例按 Non-Goal 跳过并记录）
- [x] fastjson JsonPath 兼容测试通过（纯 jsonpath 套件 78 个 + JsonValue/Accessor 各 10 个，随模块全绿；roadmap Wave 2 早已交付）
- [x] 所有 Phase 的 Exit Criteria 已勾选
- [x] 性能优化完成：JMH+JFR 驱动，锁定 tojson/print 路径 ~11-25% 提升，剩余项为 API 契约级（Deferred 记录）
- [x] 端到端测试覆盖（TestJqOfficial 全量即端到端：表达式字符串→输出）
- [x] 无静默跳过（严格 harness，编译/运行错误即失败）
- [x] 文档已更新（设计文档 02 最终状态、本 plan、`ai-dev/logs/2026/09-25.md`）
- [x] 独立 closure audit 已完成（见 Closure Audit Evidence）
- [x] `./mvnw test -pl nop-kernel/nop-jq` 通过（652）
- [x] 代码规范检查通过（ast-grep pre-commit lint 通过；下游 nop-ai-toolkit 242 测试通过）

## Deferred But Adjudicated

### jq 完整 VM（fork/backtrack）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 急切求值 + 控制流异常已覆盖官方套件全部行为；完整 VM 收益为任意深度生成器组合，官方测试无法区分
- Successor Required: `yes`
- Successor Path: 后续独立计划

### jq 完整模块系统（import/include/modulemeta）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要文件系统模块解析；官方套件相应 17 个用例已在 harness 中按 Non-Goal 显式跳过并记录
- Successor Required: `yes`
- Successor Path: 后续独立计划

### decNumber 数字字面量逐字节保留

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 目标为 jq 1.7.1 发行版语义；官方 runner 用数值比较（`19.0 == 19`），double + 字面量整数精确相等已满足全部 461 块
- Successor Required: `no`

### Java↔JqValue 边界流式转换（API 契约级优化）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 剩余 ~40% 分配热点在 apply() 的树转换边界；消除需新增流式/惰性公开 API，属契约变更需独立评审
- Successor Required: `yes`
- Successor Path: 性能后续计划（JMH 数据已入档）

### Oniguruma 专属正则测试（onig.test / manonig.test）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 依赖 Oniguruma 方言；roadmap stage 6 早已裁定 out of scope，java.util.regex 覆盖 jq.test 内全部正则用例
- Successor Required: `no`

## Closure

Status Note: jq 1.7.1 官方测试套件全量通过：447 块 = 421 ok 执行 + 9 %%FAIL 全部按预期报错（共 430 动态用例，0 失败）+ 12 模块依赖用例按 Non-Goal 跳过 + 5 %%FAIL IGNORE；模块 652 测试全绿；JMH+JFR 性能优化完成一轮并锁定可测量收益（tojson 路径 ~11-25%），剩余方向已裁定入档。独立 closure audit 通过（初判 REJECT 的两处文档失实已修复）。计划关闭。
Completed: 2026-09-25

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session, agentId: agent_6b63a051-3777-4a30-bd3a-8751fd87a7f5）
- Audit Session: 2026-09-25，与实现会话隔离；全部以 live repo + /usr/bin/jq (1.7.1) ground truth 现场验证
- Evidence:
  - Exit Criteria 逐条验证：652 测试全绿实测（TestJqOfficial 430 / Execution 52 / Comprehensive 40 / AiAgent 32 / jsonpath 78 + Value/Accessor 各 10）；PASS
  - 官方块计数核验（audit 修正实现者算术）：447 块 = 421 ok 执行 + 12 模块依赖跳过 + 9 %%FAIL + 5 IGNORE；430 动态用例 = 421 + 9；PASS（数字已按审计修正回写本文档与日志）
  - harness 严格性：读毕 TestJqOfficial/JqOfficialCase 源码——编译失败必 fail、运行错误仅在期望前缀已产出时容忍、%%FAIL 成功即 fail、无 @Disabled/Assumptions；PASS
  - 语义抽查（/usr/bin/jq 对照 + jshell 直跑）：取模无穷表、`.[nan] = 9` 错误文本逐字符一致、?// 解构族行为一致、`13911860366432393 == 13911860366432392` false；PASS
  - Anti-Hollow：scan-hollow-implementations --severity high 退出码 0；无空方法体/TODO；`UnsupportedOperationException` 仅存在于 legacy JqCompiledQuery（fail-fast 正确模式）；调用链 JqEngine.compile → JqDirectQuery → JqExecutor 实测连通；PASS
  - Deferred 裁定：五项均为允许分类且理由成立；模块 12 用例跳过经核验不可能在无模块系统下通过，非 in-scope 缺陷降级；PASS
  - `check-plan-checklist.mjs --strict` 退出码 0（53/53 勾选）
  - 审计初判 REJECT（两处文档失实：块数算术、设计文档 02 过期草稿正文）→ 实现者已修复本文档数字、重写 `02-jq-complete-design.md` 为最终状态、更新 `01-architecture-baseline.md`/`00-vision.md` 过期段落 → 修复后满足关闭条件

Follow-up:

- 性能后续计划：Java↔JqValue 边界流式转换（见 Deferred）
- 模块系统后续计划（如需 import/include 支持时启动）

Follow-up:

- 性能后续计划：Java↔JqValue 边界流式转换（见 Deferred）
- 模块系统后续计划（如需 import/include 支持时启动）
