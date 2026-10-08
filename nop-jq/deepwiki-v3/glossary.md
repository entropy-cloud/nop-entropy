# 术语表

> 本页精确定义 nop-jq 领域术语，消除同名混淆。核心源文件（相对基准 `../src/main/java/io/nop/jq/`，已验证可解析）：
>
> - [../src/main/java/io/nop/jq/jq/JqEngine.java](../src/main/java/io/nop/jq/jq/JqEngine.java) — jq 编译门面与编译缓存
> - [../src/main/java/io/nop/jq/jq/ast/JqAstNode.java](../src/main/java/io/nop/jq/jq/ast/JqAstNode.java) — AST 封闭基接口（39 个 permits）
> - [../src/main/java/io/nop/jq/jq/ast/JqAstVisitor.java](../src/main/java/io/nop/jq/jq/ast/JqAstVisitor.java) — AST 访问器（39 个 visit 方法）
> - [../src/main/java/io/nop/jq/jq/runtime/JqExecutor.java](../src/main/java/io/nop/jq/jq/runtime/JqExecutor.java) — AST 求值引擎
> - [../src/main/java/io/nop/jq/jq/runtime/JqValue.java](../src/main/java/io/nop/jq/jq/runtime/JqValue.java) — jq 运行时值接口
> - [../src/main/java/io/nop/jq/jsonvalue/JsonValue.java](../src/main/java/io/nop/jq/jsonvalue/JsonValue.java) — 不可变 JSON 值接口
> - [../src/main/java/io/nop/jq/JsonAccessor.java](../src/main/java/io/nop/jq/JsonAccessor.java) — 属性访问抽象
> - [../src/main/java/io/nop/jq/jsonpath/Segment.java](../src/main/java/io/nop/jq/jsonpath/Segment.java) — JsonPath 管线单步
> - [../src/main/java/io/nop/jq/jsonpath/Filter.java](../src/main/java/io/nop/jq/jsonpath/Filter.java) — 数组元素谓词
> - [../src/main/java/io/nop/jq/jsonpath/NopCompiledJsonPath.java](../src/main/java/io/nop/jq/jsonpath/NopCompiledJsonPath.java) — 已编译 JsonPath
> - [../src/main/java/io/nop/jq/jq/runtime/JqEnvironment.java](../src/main/java/io/nop/jq/jq/runtime/JqEnvironment.java) — jq 运行环境
> - [../src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java](../src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java) — 内建函数库（145 名）
> - [../src/main/java/io/nop/jq/jq/runtime/JqPathEval.java](../src/main/java/io/nop/jq/jq/runtime/JqPathEval.java) — 路径表达式求值

## 术语总表

| 术语 | 定义 | 出处 | 易混淆项 |
|------|------|------|----------|
| jq 程序 / 过滤器（jq program / filter） | 一段 jq 表达式文本，编译为 AST 后作为一个从单输入映射到一列输出的过滤器执行；"过滤器"在 jq 语境下指整个程序，不是单个谓词 | nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqExecutor.java:10-13；nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/IJsonQuery.java:8-14 | jsonpath 的 `Filter`（数组元素谓词，见下）；见边界表 B |
| JqAstNode | jq 语法的封闭（sealed）AST 基接口，permits 恰 39 个节点类型，v3 清理删除了 3 个死节点（SelectNode、MapNode、LimitNode）；唯一方法 `accept` 做双分发 | nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/JqAstNode.java:7-23 | `JqAstVisitor`（访问器，非节点）；jsonpath 的 `Segment`（另一条编译线的步骤，非 AST） |
| JqAstVisitor | AST 访问器接口，39 个 `visitXxx` 方法与 39 个节点一一对应，执行引擎以它做逐节点分发 | nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/JqAstVisitor.java:4-46 | `JqAstNode.accept`（被访问侧的双分发入口） |
| JqValue | jq 执行期的运行时值，sealed 接口 permits 7 个实现：JqNull、JqBoolean、JqNumber、JqString、JqArray、JqObject、JqFunction（函数也是值）；6 个数据实现位于 JqValues.java，函数值由 JqClosureFn 承载 | nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqValue.java:5-13；nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqValues.java:9-123；nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqClosureFn.java:12 | `JsonValue`（另一套值模型，见边界表 A）；原生 Java 对象（jsonpath 线的求值对象） |
| JsonValue | 不可变 JSON 值类型，sealed 接口 permits 6 个实现（JsonNull、JsonBoolean、JsonNumber、JsonString、JsonArray、JsonObject），实例线程安全可共享；仓库内当前仅测试消费 | nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonvalue/JsonValue.java:7-12 | `JqValue`（见边界表 A） |
| JsonAccessor | 属性/数组访问抽象接口，屏蔽 Map、DynamicObject、JavaBean 三类目标对象的差异；唯一实现 `NopJsonAccessor.INSTANCE` | nop-kernel/nop-jq/src/main/java/io/nop/jq/JsonAccessor.java:5-9；nop-kernel/nop-jq/src/main/java/io/nop/jq/NopJsonAccessor.java:17-18 | `JsonValue`/`JqValue`（Accessor 不表示值本身，只是读写在任意目标对象上的操作面） |
| Segment | JsonPath 管线中的一个求值步骤，`eval(accessor, root, current)` 取当前值产出下一步值；实现有 Property、ArrayAccess、Range、WildCard、DeepScan、Filter、Root、FusedProperty 八种 | nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/Segment.java:6-18 | `Filter`（Segment 的一个种类内部使用的谓词，见边界表 B）；jq AST 节点 |
| Filter | 施加于数组元素的谓词接口，`apply(accessor, root, item)` 返回布尔；实现有 CompareFilter、LogicFilter、RegexFilter，由 `FilterSegment` 包装使用 | nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/Filter.java:6-15；nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/FilterSegment.java:13-15 | "jq 过滤器"（整个 jq 程序的别称，见边界表 B） |
| NopCompiledJsonPath | 已编译的 JsonPath 表达式，持有一列 Segment，可对多个根对象复用；由 `NopJsonPath.compile(String)` 创建，提供 eval/evalOne/set/remove 等操作 | nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/NopCompiledJsonPath.java:11-15；nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/NopJsonPath.java:20-40 | `IJsonQuery`/`JqDirectQuery`（jq 线的编译产物，见边界表 C） |
| JqEnvironment | jq 求值的运行环境：变量作用域栈、函数定义表，外加资源预算（MAX_CALL_DEPTH=2000、MAX_OUTPUT_COUNT=1,000,000）；函数调用经 `fork()` 写时复制共享作用域与函数表 | nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqEnvironment.java:9-17,140-144 | `JsonQueryContext`（外部查询上下文）；JVM 栈（调用深度是环境内计数，非 JVM 限制） |
| 路径表达式 / 路径赋值（`\|=`） | 路径表达式指 filter 在输入上选出的具体键/下标路径序列（供 `=`、`\|=`、`del()`、`path()` 使用）；`\|=` 是 UPDATE 型路径赋值，语义为 jq 的 `_modify`：对每条路径取原值、用右侧 filter 变换后写回，空输出视为删除且删除最后统一施加 | nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqPathEval.java:24-29；nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/UpdateAssignNode.java:4-9；nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqExecutor.java:630-662 | 普通 `=` 赋值（右侧对原输入求值后整体写入）；`+=` 等算术更新（同属 UpdateAssignNode 的其他 Op）；完整流机制见 [路径赋值](./flows/path-assign.md) |
| 内建函数（builtin） | jq 引擎预置的函数，按"名/元数"登记于 `BUILTIN_NAMES`，恰 145 条（如 `add/0`、`limit/2`），该数组同时是 `builtins` 内建的报告来源；分发分惰性特殊形式、急切内建、用户函数遮蔽三层 | nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java:209-236 | `JqFunctionDef`/`JqClosureFn`（用户在程序内定义的函数，可遮蔽同名内建） |
| 编译缓存 | `JqEngine` 内置的静态 `LocalCache<String, IJsonQuery>`，容量 DEFAULT_CACHE_SIZE=1000，按表达式文本缓存编译产物，使 `JqEngine.compile(expr)` 对同一表达式复用同一可执行查询 | nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqEngine.java:15-24 | jsonpath 的 `NopJsonPath`（其 COMPILER 为静态 `JsonPathCompiler` 实例，无同名 LocalCache 缓存） |

## 边界 A：JqValue vs JsonValue

| | JqValue | JsonValue |
|---|---|---|
| 包 | `io.nop.jq.jq.runtime` | `io.nop.jq.jsonvalue` |
| 定义 | jq 执行期运行时值 | 不可变 JSON 值类型 |
| 实现（permits） | 7 个，含 `JqFunction`（函数是一等值） | 6 个，纯数据，无函数型 |
| 服务对象 | jq 引擎：`JqExecutor` 的输入/输出流 | 独立数据模型，仓库内当前仅测试引用 |
| 生态 | 配套 JqTruthiness（jq 真值）、JqValues、`of(Object)` 双向转换 | 配套 `fromObject`/`toObject` 与普通 Java 集合互转 |
| 依据 | JqValue.java:5-13；JqTruthiness 同包 | JsonValue.java:7-12 |

两套值模型互不依赖，也不被 jsonpath 包使用（jsonpath 全包零处 import 这两者）。

## 边界 B：Segment vs Filter

| | Segment | Filter |
|---|---|---|
| 定义 | JsonPath 管线的一个求值步骤 | 施加于数组元素的谓词 |
| 方法 | `eval(accessor, root, current)` 返回下一步值 | `apply(accessor, root, item)` 返回 boolean |
| 关系 | `FilterSegment` 是 Segment 的一种，内部包装一个 Filter | 只在 FilterSegment 内被逐元素调用 |
| 数量级 | 8 种实现 + 优化器（SegmentOptimizer 可融合属性段为 FusedPropertySegment） | 3 种实现（比较、逻辑、正则） |
| 依据 | Segment.java:6-18；FilterSegment.java:13-40 | Filter.java:6-15 |

另注意跨包同名：jq 语境的"过滤器（filter）"指整个 jq 程序（输入到输出流的映射），与 jsonpath 的 `Filter` 谓词无关。

## 边界 C：jq 引擎 vs JSON Path 子系统

| | jq 引擎 | JSON Path 子系统 |
|---|---|---|
| 包 | `io.nop.jq.jq`（+ `jq.ast`、`jq.runtime`） | `io.nop.jq.jsonpath` |
| 输入语言 | jq 程序文本 | JSONPath 表达式（API 对齐 fastjson JSONPath） |
| 编译产物 | AST（JqAstNode）+ `JqDirectQuery`，经 JqEngine 缓存 | `List<Segment>` 装入 NopCompiledJsonPath |
| 求值载体 | `JqValue`（自有值模型）+ 有状态 JqEnvironment | 原生 Java 对象（Map/List/bean），经 JsonAccessor 读写 |
| 门面 | `JqEngine.compile` / `IJsonQuery.apply` | `NopJsonPath.compile/eval` |
| 依据 | JqEngine.java:14-32；JqExecutor.java:10-19 | NopJsonPath.java:10-15；JsonPathExecutor.java:19-24 |

两条线仅共享根包设施（JsonAccessor、NopJqException/NopJqErrors）；`JqJPathInitializer` 在平台初始化时把遗留 `JPath` SPI 委托给 NopJsonPath，不经过 jq 引擎（JqJPathInitializer.java:22-51）。详见 [jq-runtime](./modules/jq-runtime.md)、[jsonpath](./modules/jsonpath.md)、[查询管线](./flows/query-pipeline.md)。

## Sources

- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqExecutor.java:10-19](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqExecutor.java#L10-L19)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqExecutor.java:630-662](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqExecutor.java#L630-L662)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/IJsonQuery.java:8-14](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/IJsonQuery.java#L8-L14)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/JqAstNode.java:7-23](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/JqAstNode.java#L7-L23)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/JqAstVisitor.java:4-46](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/JqAstVisitor.java#L4-L46)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqValue.java:5-13](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqValue.java#L5-L13)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqValues.java:9-123](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqValues.java#L9-L123)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqClosureFn.java:12](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqClosureFn.java#L12)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonvalue/JsonValue.java:7-12](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonvalue/JsonValue.java#L7-L12)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/JsonAccessor.java:5-9](/nop-kernel/nop-jq/src/main/java/io/nop/jq/JsonAccessor.java#L5-L9)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/NopJsonAccessor.java:17-18](/nop-kernel/nop-jq/src/main/java/io/nop/jq/NopJsonAccessor.java#L17-L18)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/Segment.java:6-18](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/Segment.java#L6-L18)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/Filter.java:6-15](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/Filter.java#L6-L15)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/FilterSegment.java:13-40](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/FilterSegment.java#L13-L40)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/NopCompiledJsonPath.java:11-15](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/NopCompiledJsonPath.java#L11-L15)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/NopJsonPath.java:10-40](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/NopJsonPath.java#L10-L40)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/JsonPathExecutor.java:19-24](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/JsonPathExecutor.java#L19-L24)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqEnvironment.java:9-17](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqEnvironment.java#L9-L17)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqEnvironment.java:140-144](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqEnvironment.java#L140-L144)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqPathEval.java:24-29](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqPathEval.java#L24-L29)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/UpdateAssignNode.java:4-9](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/UpdateAssignNode.java#L4-L9)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java:209-236](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java#L209-L236)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqEngine.java:14-32](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqEngine.java#L14-L32)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/JqJPathInitializer.java:22-51](/nop-kernel/nop-jq/src/main/java/io/nop/jq/JqJPathInitializer.java#L22-L51)
