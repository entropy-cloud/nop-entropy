# 阅读指南

> 本页引用基准（wiki 页相对本页 `deepwiki/nop-xlang/`；源码相对本页，`../../` 回到仓库根）：
>
> - [overview.md](overview.md)｜[quickstart.md](quickstart.md)｜[glossary.md](glossary.md)｜[architecture.md](architecture.md)（前三页与本页同批产出）
> - [flows/compile-pipeline.md](flows/compile-pipeline.md)｜[flows/expression-eval.md](flows/expression-eval.md)
> - [modules/ast-model.md](modules/ast-model.md)｜[modules/xdef-xdsl.md](modules/xdef-xdsl.md)｜[topics/error-model.md](topics/error-model.md)
> - [../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLang.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLang.java)
> - [../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java)
> - [../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTNode.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTNode.java)
> - [../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/Expression.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/Expression.java)
> - [../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/XLangErrors.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/XLangErrors.java)
> - [../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/AbstractExecutable.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/AbstractExecutable.java)
> - [../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/SimpleStdDomainHandlers.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/SimpleStdDomainHandlers.java)
> - [../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/StdDomainRegistry.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/StdDomainRegistry.java)
> - [../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/IXplTagCompiler.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/IXplTagCompiler.java)
> - [../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/tags/InternalTagCompilers.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/tags/InternalTagCompilers.java)
> - [../../nop-kernel/nop-xlang/model/antlr/XLangParser.g4](../../nop-kernel/nop-xlang/model/antlr/XLangParser.g4)

本页为 nop-xlang 全部 10 个 wiki 页给出三类读者的按序阅读路径。排序依据 fan-in（文件级 grep 口径：源码中引用该类型的文件数）——被引用最多的类型是全部机制的交汇点，先读它们的定义页（术语表、AST 模型），再读使用它们的机制页（flows），最后读横切主题（错误模型）。三类读者：**A 平台使用者**（在业务代码里用 XPL/XScript 表达式）、**B 编译器维护者**（理解/修改编译管线）、**C 语言扩展贡献者**（加语法/加域处理器/加标签编译器）。

## fan-in 排序表

下表同时是三类路径的优先级索引：「读者优先级」列的编号对应下文各路径的步骤号，`·` 分隔多个读者的到达点。

| 类型 | fan-in | 所属页面 | 读者优先级 |
|---|---|---|---|
| `Expression` | 185 | 表达式求值「求值模型：AST 与可执行体分离」（定义见 AST 模型） | A4·B4·C1 |
| `XLangASTNode` | 145 | AST 模型「节点中枢：XLangASTNode 与 XLangASTKind」 | B3·C1 |
| `XLangErrors` | 129 | 错误模型「全量界定：348 个错误码的定义与引用」 | A6·B6·C5 |
| `XLangASTKind` | 119 | AST 模型「105 种节点的分组分类」 | B3·C2 |
| `AbstractExecutable` | 101 | 表达式求值「可执行体层级与 AbstractExecutable」 | B5 |
| `Identifier` | 75 | 表达式求值「与编译产物的衔接：标识符解析决定求值形态」 | A5·B5 |
| `IXLangCompileScope` | 61 | 表达式求值「求值上下文与作用域机制」 | A5·B4 |
| `XLangOperator` | 58 | 表达式求值「典型算子语义」 | B5 |

三条路径在下图汇于同一组 wiki 页，仅入口与顺序不同；边标为到达该页的步骤序号，区间（如 `4-5`）表示同一页连续两步。

```mermaid
flowchart TD
    subgraph SA["A 平台使用者"]
        A1["总览"] -->|"2"| A2["快速上手"]
        A2 -->|"3"| A3["术语表"]
        A3 -->|"4-5"| A4["表达式求值"]
        A4 -->|"6"| A5["错误模型"]
    end
    subgraph SB["B 编译器维护者"]
        B1["术语表"] -->|"2"| B2["架构与数据流"]
        B2 -->|"3"| B3["AST 模型"]
        B3 -->|"4"| B4["编译管线"]
        B4 -->|"5"| B5["表达式求值"]
        B5 -->|"6"| B6["错误模型"]
    end
    subgraph SC["C 语言扩展贡献者"]
        C1["AST 模型"] -->|"2-3"| C2["编译管线"]
        C2 -->|"4"| C3["XDef 与 XDSL"]
        C3 -->|"5"| C4["错误模型"]
    end
```

## 路径 A：平台使用者（在业务代码里用 XPL/XScript 表达式）

目标：在业务模型、过滤条件、模板里正确写出并求值表达式，知道编译入口、作用域契约和错误从哪层冒出来。不读编译器内部。

1. **总览**：读 [overview.md](overview.md)（全页，同批产出）。确认 XLang 是嵌入平台的元语言族而非独立脚本引擎：XPL 是模板、XScript 是表达式脚本、XT/XPath 是转换与路径，业务代码接触的通常只是其中表达式子集。
2. **快速上手**：读 [quickstart.md](quickstart.md)（全页，同批产出），跑通一次最小表达式编译加求值。后续每一步都在解释这个例子里出现的对象。
3. **术语表**：读 [glossary.md](glossary.md)「术语总表」的 XPL/XScript/Expression/Executable 四行与「重点边界组」的 XPL vs XScript 一行。写业务表达式前必须分清：XPL 模板里的 `c:script` 块、属性里的 `${}` 插值、独立 XScript 求值走的是不同编译入口。
4. **编译与求值入口**：读 [flows/expression-eval.md](flows/expression-eval.md)「求值模型：AST 与可执行体分离」。编译一次、多次求值：入口 `XLang.newCompileTool()`（`XLang.java:72`）拿到 `XLangCompileTool`，按场景选 `compileFullExpr`（`XLangCompileTool.java:151`）、`compileSimpleExpr`（`:156`）、`compileTemplateExpr`（`:176`）或 `compileTag`（`:180-193`），产物统一是 `ExprEvalAction`（`ExprEvalAction.java:20`）；求值侧经 `XLang.execute(expr, rt)`（`XLang.java:47`）配 `newEvalScope`（`XLang.java:60-64`）传入上下文变量。
5. **作用域契约**：读 [flows/expression-eval.md](flows/expression-eval.md)「求值上下文与作用域机制」与「与编译产物的衔接：标识符解析决定求值形态」。编译期作用域是 `IXLangCompileScope`（`IXLangCompileScope.java:29`），运行期是 `IEvalScope`，两者变量集合不一致是使用方第一常见错误源；`Identifier`（`Identifier.java:22`）在编译期解析为注册变量、属性访问还是外部函数，直接决定产物形态与报错时机。
6. **错误模型**：读 [topics/error-model.md](topics/error-model.md)「编译期与运行期的传播差异」。你的代码在编译期拿到的是 `XLangErrors`（`XLangErrors.java:16`）声明的 `ERR_XDEF_*`/`ERR_XPL_*` 族解析与语义错误，运行期才是 `ERR_EXEC_*` 求值错误；两族的定位参数约定见「平台机制：ErrorCode 与 NopException」。

## 路径 B：编译器维护者（理解/修改编译管线）

目标：建立"源文本 → 解析树 → AST → 优化 → 类型推断 → 可执行体 → 求值"的完整心智模型，能定位任何一处行为的责任类。顺序 = fan-in 降序：先 AST 中枢，再管线，再求值。

1. **术语表**：读 [glossary.md](glossary.md)「重点边界组」全部行。AST/Executable、XDef/XDSL、编译期/运行期几组划界给出后续所有页面的坐标系。
2. **架构与数据流**：读 [architecture.md](architecture.md)（全页，同批产出）。拿到关键结论：包结构即管线阶段（parse → ast → compile → exec），「机制索引」一节给出细节子页入口。
3. **AST 模型**：读 [modules/ast-model.md](modules/ast-model.md) 全页，重点三节——「节点中枢：XLangASTNode 与 XLangASTKind」（`XLangASTNode.java:13`、`XLangASTKind.java:4`，一切节点的公共基类与 105 种 Kind 枚举）、「三套生成派发：Visitor / Processor / Optimizer」（同一棵树的三种遍历全部是生成代码）、「XLangASTOptimizer：统一改写骨架而非折叠规则」（`XLangASTOptimizer.java:8`，化简规则分散在各节点类而非优化器本体）。
4. **编译管线**：读 [flows/compile-pipeline.md](flows/compile-pipeline.md) 全页五阶段。语法源头是 [XLangParser.g4](../../nop-kernel/nop-xlang/model/antlr/XLangParser.g4)，`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/parse/antlr/XLangParser.java` 与 `_XLangASTBuildVisitor` 是生成物（禁手改）；手工层为解析入口 `XLangASTParser.parseFromText`（`XLangASTParser.java:22`）与语义钩子 `XLangASTBuildVisitor`（`XLangASTBuildVisitor.java:25`，继承生成访问器）；类型推断 `TypeInferenceProcessor`（`TypeInferenceProcessor.java:118`）默认关闭、错误降级；终点 `BuildExecutableProcessor`（`BuildExecutableProcessor.java:263`）产出 `IExecutableExpression`。
5. **表达式求值**：读 [flows/expression-eval.md](flows/expression-eval.md)「可执行体层级与 AbstractExecutable」与「典型算子语义」。`AbstractExecutable`（`AbstractExecutable.java:27`）实现 `IExecutableExpression`，统一 `display()` 与求值错误包装（`:57-61`）；`XLangOperator`（`XLangOperator.java:15`）是全部二元算子的枚举全集，改算子语义先看 exec 侧对应类。
6. **错误模型**：读 [topics/error-model.md](topics/error-model.md)「全量界定：348 个错误码的定义与引用」与「分组体系：前缀即子系统」。`XLangErrors`（`XLangErrors.java:16`）接口按前缀分族（`ERR_XDEF_`/`ERR_XPL_`/`ERR_EXEC_`…），新增管线阶段的错误必须落在正确前缀族，跨模块消费方按前缀过滤。

## 路径 C：语言扩展贡献者（加语法/加域处理器/加标签编译器）

目标：在正确的层落改动，不碰生成物。步骤 1 是与 B 路径重叠的前置；步骤 2-4 按三类扩展目标组织。注意 nop-xlang 属框架核心引擎，按仓库规约为 plan-first 区域。

1. **AST 模型（前置）**：读 [modules/ast-model.md](modules/ast-model.md)「节点中枢：XLangASTNode 与 XLangASTKind」与「三套生成派发：Visitor / Processor / Optimizer」。任何新扩展最终都要落成 AST 节点、过三套生成派发，这两节决定你后续要在哪里补分支。
2. **加语法**：读 [flows/compile-pipeline.md](flows/compile-pipeline.md)「阶段一：ANTLR 解析」与「阶段二：AST 构建」。改动链固定为：[XLangParser.g4](../../nop-kernel/nop-xlang/model/antlr/XLangParser.g4) 增语法规则（唯一源头）→ 构建期重新生成 `parse/antlr/` 下解析器与 `_XLangASTBuildVisitor`（生成物，禁手改）→ 语义钩子写在 `XLangASTBuildVisitor`（`XLangASTBuildVisitor.java:25`）→ `XLangASTKind`（`XLangASTKind.java:4`）增枚举值 → 阶段三/四/五的各 Processor（含 `XLangASTOptimizer.java:8`）为新 Kind 补分支。
3. **加标签编译器**：读 [flows/compile-pipeline.md](flows/compile-pipeline.md)「阶段五：作用域分析与可执行体生成」——标签编译器的产物就是进入同一管线的 `Expression`。实现 [IXplTagCompiler](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/IXplTagCompiler.java)（`IXplTagCompiler.java:14`，入口是 default 方法 `parseTag`（`:15-19`），从 `XNode` 产出 `Expression`）；内置 `c:`/`macro:` 标签在 `InternalTagCompilers` 静态块注册（`InternalTagCompilers.java:18-51`），程序化注册用 `registerTagCompiler`（`:53-55`）。分发顺序在 `XplCompiler.getTagCompiler`（`XplCompiler.java:263-285`）：`XPL_CORE_NS`/`XPL_MACRO_NS` 命名空间只认 `InternalTagCompilers`（`:274`，未知标签抛 `ERR_XPL_NOT_ALLOW_UNKNOWN_TAG`），其余命名空间走 `scope.getTagCompiler`（`:285`）——即 xlib 标签库，经 `XLangCompileTool.loadLib`（`XLangCompileTool.java:128`）装载，用户级自定义标签实现 `IXplLibTagCompiler`（`IXplLibTagCompiler.java:13`）。
4. **加域处理器**：读 [modules/xdef-xdsl.md](modules/xdef-xdsl.md)「标准域处理器体系：SimpleStdDomainHandlers 与分类」。注册模式照抄三步：继承 `StringStdDomainHandler`/`SimpleStdDomainHandler` 并实现 `getName()`+`parseProp()`（范式 `SimpleStdDomainHandlers.java:82-101` 的 `VPathType`）；只需校验的继承 `CheckStdDomainHandler` 只写 `isValid`（`:269-278` 的 `ClassNameType`）；需要产出静态类型再覆写 `getGenericType`/`isFixedType`（`:487-508` 的 `IntRangeType`）。注册点在 `StdDomainRegistry.registerStdDomainHandler`（`StdDomainRegistry.java:42-44`），内置域集中在 `registerDefaults()`（`:50-191`），查询失败回退 string 域（`:33-40`）。
5. **错误模型（新错误码）**：读 [topics/error-model.md](topics/error-model.md)「平台机制：ErrorCode 与 NopException」。域处理器与标签编译器抛错统一走 `XLangErrors` 声明的错误码加 `.param()` 诊断参数（范式 `SimpleStdDomainHandlers.java:92-93`：`ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN` 附 `stdDomain`/`value`/`propName`），不要在扩展代码里用裸异常或英文硬编码消息替代错误码。

## Sources

- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLang.java:47-72](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLang.java#L47-L72)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java:128-193](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java#L128-L193)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/ExprEvalAction.java:20](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/ExprEvalAction.java#L20)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/IXLangCompileScope.java:29](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/IXLangCompileScope.java#L29)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/Expression.java:18](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/Expression.java#L18)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTNode.java:13](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTNode.java#L13)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTKind.java:4](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTKind.java#L4)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangOperator.java:15](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangOperator.java#L15)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/Identifier.java:22](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/Identifier.java#L22)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTOptimizer.java:8](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTOptimizer.java#L8)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/XLangErrors.java:16](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/XLangErrors.java#L16)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/AbstractExecutable.java:27-61](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/AbstractExecutable.java#L27-L61)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/parse/XLangASTParser.java:11-22](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/parse/XLangASTParser.java#L11-L22)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/parse/XLangASTBuildVisitor.java:25](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/parse/XLangASTBuildVisitor.java#L25)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/TypeInferenceProcessor.java:118](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/TypeInferenceProcessor.java#L118)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/BuildExecutableProcessor.java:263](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/BuildExecutableProcessor.java#L263)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/SimpleStdDomainHandlers.java:82-101](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/SimpleStdDomainHandlers.java#L82-L101)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/SimpleStdDomainHandlers.java:269-278](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/SimpleStdDomainHandlers.java#L269-L278)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/SimpleStdDomainHandlers.java:487-508](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/SimpleStdDomainHandlers.java#L487-L508)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/SimpleStdDomainHandlers.java:92-93](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/SimpleStdDomainHandlers.java#L92-L93)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/StdDomainRegistry.java:33-44](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/StdDomainRegistry.java#L33-L44)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/StdDomainRegistry.java:50-191](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/StdDomainRegistry.java#L50-L191)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/IXplTagCompiler.java:14-26](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/IXplTagCompiler.java#L14-L26)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/IXplLibTagCompiler.java:13](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/IXplLibTagCompiler.java#L13)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/tags/InternalTagCompilers.java:18-55](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/tags/InternalTagCompilers.java#L18-L55)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/impl/XplCompiler.java:263-285](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/impl/XplCompiler.java#L263-L285)
- [nop-kernel/nop-xlang/model/antlr/XLangParser.g4]()

---

## On this page

- fan-in 排序表
- 路径 A：平台使用者（在业务代码里用 XPL/XScript 表达式）
- 路径 B：编译器维护者（理解/修改编译管线）
- 路径 C：语言扩展贡献者（加语法/加域处理器/加标签编译器）
