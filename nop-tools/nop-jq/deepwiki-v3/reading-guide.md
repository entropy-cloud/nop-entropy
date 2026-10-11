# 阅读指南

> 本页把 deepwiki v3 的 9 个页面编排成三条按序阅读路径，读者按自己的角色选一条走完即可获得该角色所需的最小完整知识。路径排序依据源码 fan-in（文件级 grep 口径）：被引用最多的类型最先进入对应路径。基准源文件（相对本页 `../src/main/java/io/nop/jq/`）：
>
> - [./quickstart.md](./quickstart.md) — 首次调用最短路径（同批产出）
> - [./overview.md](./overview.md) — 模块定位与平台角色（同批产出）
> - [./architecture.md](./architecture.md) — 分层与依赖方向（同批产出）
> - [./glossary.md](./glossary.md) — 术语精确定义与三张边界表
> - [./flows/query-pipeline.md](./flows/query-pipeline.md) — 文本到输出流八阶段
> - [./flows/path-assign.md](./flows/path-assign.md) — `\|=` 路径赋值机制
> - [./modules/jq-runtime.md](./modules/jq-runtime.md) — 求值运行时与 145 名内建
> - [./modules/jsonpath.md](./modules/jsonpath.md) — JSON Path 独立编译求值线
> - [./topics/error-model.md](./topics/error-model.md) — 双轨错误体系
> - [../src/main/java/io/nop/jq/jq/ast/JqAstNode.java](../src/main/java/io/nop/jq/jq/ast/JqAstNode.java) — sealed AST 基接口（39 permits）
> - [../src/main/java/io/nop/jq/jq/ast/JqAstVisitor.java](../src/main/java/io/nop/jq/jq/ast/JqAstVisitor.java) — 39 个 visit 方法
> - [../src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java](../src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java) — 内建三层分发（call 入口）
> - [../src/main/java/io/nop/jq/JsonAccessor.java](../src/main/java/io/nop/jq/JsonAccessor.java) — jsonpath 线属性访问抽象

## fan-in 排序与读者优先级

fan-in 为文件级 grep 口径（全仓 import/引用计数，非符号级）。它决定两件事：源码通读的先后（被依赖最多者先读），以及每类读者绕不开哪些类型。下表"优先级"列中 A=平台 API 用户、B=引擎维护者、C=语法/内建函数贡献者。

| 类型 | fan-in | 所属页面 | 读者优先级 |
|------|--------|---------|-----------|
| JqAstNode | 53 | [query-pipeline](./flows/query-pipeline.md)（编译产物）、[jq-runtime](./modules/jq-runtime.md)（分发对象） | B 必读、C 必读（改语法的硬约束）、A 可跳过 |
| JqAstVisitor | 44 | [jq-runtime](./modules/jq-runtime.md)「机制与控制流」（匿名 visitor 分发） | B 必读、C 必读（加节点同步点）、A 可跳过 |
| JsonAccessor | 20 | [jsonpath](./modules/jsonpath.md)（访问抽象 + 写语义） | A 必读（jsonpath 线）、B 必读、C 选读 |
| JqValue | 18 | [jq-runtime](./modules/jq-runtime.md)「JqValue：封闭值模型与全序」 | B 必读、C 必读（内建出入参）、A 选读（toJava 还原规则） |
| Segment | 16 | [jsonpath](./modules/jsonpath.md)「编译管线」「Segment 层级与过滤器」 | A 必读（jsonpath 线）、B 必读、C 可跳过 |
| Filter | 9 | [jsonpath](./modules/jsonpath.md)「Segment 层级与过滤器」 | A 选读（用 `[?(...)]` 时）、B 选读、C 可跳过 |
| JqPrinter | 7 | [query-pipeline](./flows/query-pipeline.md)「输出端」、[jq-runtime](./modules/jq-runtime.md)「输出语义」 | A 选读（输出文本格式）、B 必读、C 必读（错误消息渲染共用它） |
| JqEnvironment | 7 | [jq-runtime](./modules/jq-runtime.md)「JqEnvironment：作用域栈、函数表与资源预算」 | B 必读、C 必读（惰性形式要操作 env）、A 可跳过 |

## 三条路径总览

```mermaid
graph TD
    subgraph SA["路径A 平台API用户"]
        A1["quickstart 首次调用"] -->|2| A2["glossary 边界C"]
        A2 -->|3| A3["jsonpath 编译求值"]
        A3 -->|4| A4["query-pipeline 全链路"]
        A4 -->|5| A5["error-model 异常面"]
        A5 -->|6| A6["jq-runtime 调用契约"]
    end
    subgraph SB["路径B 引擎维护者"]
        B1["overview 定位"] -->|2| B2["architecture 分层"]
        B2 -->|3| B3["glossary 术语表"]
        B3 -->|4| B4["query-pipeline 八阶段"]
        B4 -->|5| B5["jq-runtime 控制流"]
        B5 -->|6| B6["path-assign 路径赋值"]
        B6 -->|7| B7["error-model 异常类型"]
    end
    subgraph SC["路径C 语法内建贡献者"]
        C1["glossary AST约束"] -->|2| C2["query-pipeline 词法语法"]
        C2 -->|3| C3["jq-runtime 内建分发"]
        C3 -->|4| C4["error-model 错误抛法"]
    end
```

边的数字是到达该页的步骤序号。三条路径共享的底座是 glossary 与 query-pipeline：fan-in 最高的 JqAstNode(53)/JqAstVisitor(44) 都在这两页首次登场。

## 路径 A：平台 API 用户

目标：会选线（jq 还是 jsonpath）、会调用、知道要防哪些异常。全程不需要读 AST。

1. **[quickstart.md](./quickstart.md)** — 拿到 `JqEngine.compile(expr).apply(root)` 与 `NopJsonPath.eval` 的最短可用调用。对照仓库内唯一生产消费方的真实调用形态：`JqToolExecutor` 就是 `compile+apply` 两行（nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/JqToolExecutor.java:44-45）。
2. **[glossary.md](./glossary.md)「边界 C：jq 引擎 vs JSON Path 子系统」** — 选线决策页。判据：输入是 jq 程序文本还是 JSONPath 字符串；jq 线封闭于 JqValue 值世界，jsonpath 线直接操作 Map/List/bean。两张线仅共享根包设施（JsonAccessor、NopJqErrors），无代码交叉。
3. **[modules/jsonpath.md](./modules/jsonpath.md)「编译管线：字符串到 Segment 管道」+「求值：列表传播与融合段旁路」+「写语义」** — jsonpath 线的 API 语义面：`eval`/`evalOne`/`set`/`remove` 各自的形状归一规则。重点记住融合段旁路（FusedPropertySegment 对 List 是整值取属性而非逐元素映射，JsonPathExecutor.java:47-65）与"set 不创建中间容器、失败返回 false"（NopCompiledJsonPath.java:52-99）。fan-in 视角：这一步覆盖 JsonAccessor(20)/Segment(16)/Filter(9) 三个类型。
4. **[flows/query-pipeline.md](./flows/query-pipeline.md)「全链路一览」八阶段表 +「编译入口：JqEngine 与编译缓存」** — jq 线的调用语义：编译产物按表达式文本缓存（容量 1000，JqEngine.java:15-33），同一表达式复用同一查询对象；`apply` 返回 `List<Object>`，`applyOne` 只取首个输出。若关心输出文本格式（整数化、NaN→null），加读同页「输出端：toJava 与 JqPrinter」（覆盖 JqPrinter(7)）。
5. **[topics/error-model.md](./topics/error-model.md)「双轨分工」表 +「消费方回传：JqToolExecutor」** — 要预期什么：调 `NopJsonPath` 只需防编译期 `NopJqException`；调 jq 线必须预期 `JqRuntimeException` 在编译和求值两个时刻出现（JqEngine.compile 不包装，JqEngine.java:22-32）。嵌入方拿到的错误文本是 errorValue 的 jq 规范渲染（JqRuntimeException.java:8-18、30-36）。
6. **[modules/jq-runtime.md](./modules/jq-runtime.md)「入口点与调用契约」** — 收尾确认两件事：每次 `apply` 新建 `JqEnvironment`，共享的只有 AST 与 executor，求值期不写共享结构（跨线程复用同一 `IJsonQuery` 安全）；`applyPartial` 与 `apply` 的错误语义差异（运行错折叠为 errored 标志、保留已产出输出，JqDirectQuery.java:50-66）。

## 路径 B：引擎维护者

目标：建立可定位任意行为到源码行的心智模型，知道改哪里会破坏什么不变式、用什么回归。

1. **[overview.md](./overview.md)** — 模块边界：jq.jq（前端）/ jq.jq.runtime（求值）/ jsonpath（平行线）/ jsonvalue（无生产消费者）/ 根包（JsonAccessor、错误码）。
2. **[architecture.md](./architecture.md)** — 依赖方向与两线交汇点（仅 JqJPathInitializer 的 SPI 委托与共享错误码族）。
3. **[glossary.md](./glossary.md) 术语总表 + 边界 A/B/C** — 先钉死三组易混：JqValue vs JsonValue（边界 A）、Segment vs Filter（边界 B）、jq 线 vs jsonpath 线（边界 C）。维护者最容易在这三处改错包。
4. **[flows/query-pipeline.md](./flows/query-pipeline.md) 全文** — 主干页，按八阶段表顺序通读：词法（JqLexer.java:23-179）→ 语法（JqParser 12 层优先级链）→ 求值（管道不变式：左端完整求值为临时列表、右端逐左输出写入共享 outputs，JqExecutor.java:137-158）→ 输出 → 失败路径。这是 fan-in 前两名（JqAstNode 53、JqAstVisitor 44）在机制层面的用法现场。
5. **[modules/jq-runtime.md](./modules/jq-runtime.md)「机制与控制流」+「JqEnvironment」+「JqValue」+「状态与生命周期」+「不变式与失败语义」+「关键测试」** — 运行时深水区：两个控制流异常如何近似回溯（JqShortCircuitList + JqStopException，JqExecutor.java:40-44）、作用域栈与写时复制 fork（JqEnvironment.java:78-84、134-144）、三条资源预算（MAX_CALL_DEPTH=2000 / MAX_OUTPUT_COUNT=100 万 / MAX_SPLIT_LIMIT=10 万，JqEnvironment.java:14-18）、值模型全序（JqOrdering.java:5-97，覆盖 JqValue(18)）。本节末尾记住回归基线：`src/test/resources/io/nop/jq/jq/jq-official.test`（2117 行官方用例）由 `TestJqOfficial` 驱动，任何运行时改动必须过它。
6. **[flows/path-assign.md](./flows/path-assign.md)「机制定位」+「collect」+「函数式更新」+「不变式与失败边界」** — 独立子系统：路径收集（JqPathEval.java:52-256）与复制-重建式改写（getAt/setAt/deleteAt，JqPathEval.java:426-595）。两条必须能复述的不变式：空流即删除；删除先按 compareObjectPaths 排序再倒序施加（防下标位移 + 后代先删，JqExecutor.java:643-662）。
7. **[topics/error-model.md](./topics/error-model.md)「控制流异常：不是错误的异常」+「NOP_JQ-001..008 错误码全表」** — 改代码前的护栏：`JqStopException`/`JqBreakException` 是控制流不是错误，任何新增捕获点必须放行它们；错误码族 8 个中仅 NOP_JQ-002 有抛出点，其余预留未用（jsonpath 求值期零 throw）。

收尾：按 fan-in 顺序通读源码一遍——JqAstNode(53) → JqAstVisitor(44) → JqExecutor → JqValues → JqEnvironment(7) → JqPrinter(7)，再平行读 jsonpath 线 JsonAccessor(20) → Segment(16) → Filter(9)。jq-runtime 页「性能注意」一节列出五处已知代价点（急切物化、不可变更新复制、异常填栈、每调用 visitor 分配、作用域线性扫描），调优前先读。

## 路径 C：语法/内建函数贡献者

目标：能独立完成"加一个内建函数"和"加一种 AST 节点"，且知道每一处同步点。fan-in 前两名（JqAstNode 53、JqAstVisitor 44）正是本路径的约束来源：sealed permits 与 visitor 是必须成对修改的清单。

1. **[glossary.md](./glossary.md) 术语总表** — 先读三个词条：JqAstNode（sealed，39 permits，nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/JqAstNode.java:7-18）、JqAstVisitor（39 个 visit 一一对应，nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/JqAstVisitor.java:6-46）、内建函数（`BUILTIN_NAMES` 登记 145 条，同时是 `builtins` 内建的报告来源）。sealed + visitor 双分发的硬约束从这里开始。
2. **[flows/query-pipeline.md](./flows/query-pipeline.md)「词法：JqLexer.tokenize」+「语法：JqParser 的 12 层优先级链」** — 加语法形态的解析层落点：新算符先在 JqLexer 的双字符消歧分支落户（JqLexer.java:34-161），再决定插入优先级链哪一层（JqParser.java:35-248）或 parsePrimary 的关键字结构（JqParser.java:304-475）。注意 12 层链的结合性表——插错层会改变既有表达式的语义。
3. **[modules/jq-runtime.md](./modules/jq-runtime.md)「内建函数：145 名的三层分发」+「扩展点」+「关键测试」** — 分发模型：惰性特殊形式 switch（13 名）→ 用户遮蔽检查 → `dispatchPlain` 巨型 switch（JqBuiltins.java:22-27 入口、28 起惰性 switch、323 起 dispatchPlain）。扩展点一节给出新内建落点与遮蔽白名单规则。
4. **[topics/error-model.md](./topics/error-model.md)「jq 线：JqRuntimeException 与 error value」** — 新代码怎么报错：抛 `JqRuntimeException` 并让它携带 jq error value；字符串错误即该字符串，其他值经 `JqPrinter.print` 规范渲染（JqExecutor.java:861-866）。禁止裸 `RuntimeException`。
5. **加内建函数：四处同步点**（以下行号已对 nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java 核实）：

   | 改动点 | 位置 | 规则 |
   |--------|------|------|
   | 急切内建实现 | JqBuiltins.java:323 起 `dispatchPlain` switch 加 `case` | 参数已急切求值；多输出参数用 `evalAllArgs`（JqBuiltins.java:1423-1440） |
   | 惰性特殊形式 | JqBuiltins.java:27-28 起 `call` 内惰性 switch 加 `case` | 仅当需要参数 AST 本身（limit/first/path/del 类）；截断原语用 `take`（JqBuiltins.java:259-269） |
   | 登记名字 | JqBuiltins.java:210-236 `BUILTIN_NAMES` 加 `"名/元数"` 条目（现 145 条） | 漏登记则 `builtins` 内建不报告该名（输出点 JqBuiltins.java:1346-1352），但功能不受影响 |
   | 遮蔽决策 | `isPlainBuiltin`（JqBuiltins.java:1891-1897，现 17 个不可遮蔽名）；遮蔽转调点在 JqBuiltins.java:184-189 | 希望用户 `def` 可遮蔽 → 不加白名单；不可遮蔽（语言语义核心如 empty/error/not）→ 必须加 |

   正则/时间类新内建不要继续膨胀 switch，委托 `JqRegexSupport`/`JqTimeFunctions`（jq-runtime 页「扩展点」节的既有约定）。

6. **加 AST 节点：五处同步点**（漏一处即编译失败或行为缺口）：

   | 改动点 | 位置 | 说明 |
   |--------|------|------|
   | sealed permits | nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/JqAstNode.java:7-18 | sealed 清单加节点类型（现 39 个） |
   | visitor 方法 | nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/JqAstVisitor.java:6-46 | 加 `visitXxx`（现 39 个，一一对应由 sealed 保证，漏加即编译失败） |
   | 执行分支 | nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqExecutor.java:40-47 起的匿名 visitor | `executeInto` 每调用现建匿名 `JqAstVisitor`，在此实现 `visitXxx` 求值语义 |
   | 解析分支 | nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqParser.java:35-248（优先级链）、304-475（parsePrimary） | 决定语法形式与优先级；报错统一走 `error()` 前缀 `Parse error:`（JqParser.java:1036-1047） |
   | 路径表达式支持（可选） | nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqPathEval.java:52-256 的 `collect` 加分支 | 若新节点应可出现在 `=`/`\|=`/`del()` 左侧必须加；不加则落到 default 分支抛 `Invalid path expression`（JqPathEval.java:250-256） |

7. **回归**：语义用例加进 `src/test/resources/io/nop/jq/jq/jq-official.test`（官方三行一块格式，`%%FAIL` 块测错误）或 `TestJqExecution`（`assertJq` 按输出条数+逐条比较）。运行：`./mvnw test -pl nop-kernel/nop-jq`。

## Sources

- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/JqAstNode.java:7-23](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/JqAstNode.java#L7-L23)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/JqAstVisitor.java:6-46](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/JqAstVisitor.java#L6-L46)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java:22-28](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java#L22-L28)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java:184-189](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java#L184-L189)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java:210-236](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java#L210-L236)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java:259-269](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java#L259-L269)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java:323-327](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java#L323-L327)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java:1346-1352](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java#L1346-L1352)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java:1423-1440](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java#L1423-L1440)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java:1891-1897](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqBuiltins.java#L1891-L1897)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqExecutor.java:40-47](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqExecutor.java#L40-L47)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqExecutor.java:137-158](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqExecutor.java#L137-L158)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqExecutor.java:643-662](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqExecutor.java#L643-L662)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqExecutor.java:861-866](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqExecutor.java#L861-L866)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqPathEval.java:52-256](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqPathEval.java#L52-L256)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqPathEval.java:250-256](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqPathEval.java#L250-L256)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqPathEval.java:426-595](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqPathEval.java#L426-L595)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqEnvironment.java:14-18](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqEnvironment.java#L14-L18)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqEnvironment.java:78-84](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqEnvironment.java#L78-L84)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqEnvironment.java:134-144](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqEnvironment.java#L134-L144)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqRuntimeException.java:8-36](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqRuntimeException.java#L8-L36)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqOrdering.java:5-97](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/runtime/JqOrdering.java#L5-L97)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqEngine.java:15-33](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqEngine.java#L15-L33)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqDirectQuery.java:50-66](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqDirectQuery.java#L50-L66)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqLexer.java:23-179](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqLexer.java#L23-L179)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqParser.java:35-248](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqParser.java#L35-L248)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqParser.java:304-475](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqParser.java#L304-L475)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqParser.java:1036-1047](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqParser.java#L1036-L1047)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/NopCompiledJsonPath.java:52-99](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/NopCompiledJsonPath.java#L52-L99)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/JsonPathExecutor.java:47-65](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/JsonPathExecutor.java#L47-L65)
- [nop-kernel/nop-jq/src/test/resources/io/nop/jq/jq/jq-official.test:1-2117](/nop-kernel/nop-jq/src/test/resources/io/nop/jq/jq/jq-official.test#L1-L2117)
- [nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/JqToolExecutor.java:44-45](/nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/JqToolExecutor.java#L44-L45)
