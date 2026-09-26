# 术语表

> 本页每条定义均核对到源码声明行；概念性表述与 `docs-for-ai/04-reference/glossary.md`、`docs-for-ai/02-core-guides/xdef-and-xdsl.md` 互证。源码主要出处：
>
> - [XLangConstants.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/XLangConstants.java)（语言族常量聚合接口）
> - [XLang.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLang.java)（求值门面与统一执行出口）
> - [IXDefinition.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/IXDefinition.java)（XDef 元模型解析产物）
> - [XDslKeys.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/XDslKeys.java)（XDSL `x:` 合并键族）
> - [IXplCompiler.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/IXplCompiler.java)（XPL 模板编译器）
> - [IScriptCompiler.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/script/IScriptCompiler.java)（外部脚本引擎接入）
> - [XLangASTNode.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTNode.java) / [XLangASTKind.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTKind.java)（AST 基类与 Kind 枚举）
> - [AbstractExecutable.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/AbstractExecutable.java)（可执行体基类）
> - [IXLangCompileScope.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/IXLangCompileScope.java)（编译上下文）
> - [IStdDomainHandler.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/IStdDomainHandler.java)（XDef 域处理器协议）
> - [xdef.xdef](../../nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xdef.xdef)（XDef 自举元模型，位于 nop-xdefs）
> - [xt.xdef](../../nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xt.xdef) / [xlib.xdef](../../nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xlib.xdef)（XT / xlib 的 XDef 定义，位于 nop-xdefs）

本页定义 XLang 元语言族各术语并划清边界。编译全过程见[编译管线](./flows/compile-pipeline.md)，求值模型见[表达式求值](./flows/expression-eval.md)，AST 分类细节见 [AST 节点体系](./modules/ast-model.md)，XDef/XDSL 实现细节见 [XDef 与 XDSL](./modules/xdef-xdsl.md)。

## 术语总表

出处列路径约定：`xlang/` 前缀 = `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/`；`xdefs/` 前缀 = `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/`；`core/` 前缀 = `nop-kernel/nop-core/src/main/java/io/nop/core/`。完整路径见页尾 Sources。

| 术语 | 定义 | 出处 | 易混淆项 |
|---|---|---|---|
| **XLang** | Nop 元语言族总称：以统一 AST 与编译器为底座的 XDef、XDSL、XPL、XScript、XT、XPath 的统称，实现于 `io.nop.xlang` 包；对外求值门面为 `XLang` 类，其 `execute` 是全部"运行时字符串→可执行体"出口的统一入口。 | `xlang/api/XLang.java:40-47`；`xlang/XLangConstants.java:27`（常量接口按子语言聚合 ExprConstants/XplConstants/XDslConstants）；`docs-for-ai/04-reference/glossary.md:28` | XScript（仅指脚本子语言）；XLangCompileScope（编译上下文类，非语言） |
| **XDef** | 语义坐标：XML 元模型定义语言。把实例文件中的具体标签/属性值替换为"域:选项"类型声明（如 `int`、`enum:xxx`），形成对 DSL 结构的约束坐标；`xdef.xdef` 用 xdef 自身定义 xdef（自举），设计核心是"模板即是约束"的同像约束。 | `xdefs/xdef.xdef:4-9`；`xlang/xdef/IXDefinition.java:22`（解析产物 `IXDefinition` 同时继承 `IXDefNode` 与 `IXDslModel`）；`xlang/XLangConstants.java:70`（模型类型常量 `MODEL_TYPE_XDEF`） | XDSL（被 XDef 约束的实例文件）；域处理器（单个域声明的解释器，见下） |
| **XDSL** | 遵循某个 XDef 坐标、支持 `x:extends` 差量合并的模型实例文件统称（XML/JSON）；`x:extends`/`x:override`/`x:gen-extends` 等合并键族由 `XDslKeys` 固化，各 DSL 的 schema 路径常量集中在 `XDslConstants`。 | `xlang/xdsl/XDslConstants.java:10-24`；`xlang/xdsl/XDslKeys.java:36-50`；`docs-for-ai/04-reference/glossary.md:36` | XDef（定义 vs 实例，见边界组） |
| **XPL** | 模板标签语言：XML 形式的模板被逐标签解析为 Expression 输出树（TextOutput/GenNode 等 Kind），用于代码生成与文本生成；命名空间 `xpl`（标签）/`c`（内核标签）/`macro`（宏）由 `XplConstants` 固化，编译入口为 `IXplCompiler.parseTag/parseTagBody`。 | `xlang/xpl/XplConstants.java:16,27-30`；`xlang/xpl/IXplCompiler.java:22-44`；`xdefs/xpl.xdef:8-9` | XScript（模板 vs 脚本，见边界组）；xlib（模板文件 vs 标签库） |
| **XScript** | XLang 语言的脚本形态：JavaScript 兼容语法的语句序列（支持 `===`、箭头函数、try/catch）。`<c:script>` 缺省 `lang` 时由 XLang 编译器 `parseFullExpr` 直接编译为 `Program` 节点——XScript 与 XLang 表达式是同一编译器、同一 AST；仅当 `lang` 指定其他值才委托 `IScriptCompiler` 接入 groovy 等外部引擎。 | `xlang/xpl/tags/ScriptTagCompiler.java:61,78`；`xlang/script/IScriptCompiler.java:16-21`；`docs-for-ai/02-core-guides/xlang-and-xpl-basics.md:105-113` | XPL；`IScriptCompiler`（外部脚本引擎接口，不是 XScript 本体） |
| **XT** | XML 树转换规则 DSL，定位类似 XSLT：以选择器 + 模板/映射规则对源 `XNode` 做树到树的确定性改写；模型类 `XtTransformModel`，schema 为 `/nop/schema/xt.xdef`，转换表达式 `${...}` 由 `XtExprParser`（在 XPath 解析器上扩展 `$node/$output` 等内置变量）解析。 | `xlang/xt/IXTransform.java:14-19`；`xdefs/xt.xdef:4-9`；`xlang/xt/loader/XtTransformModelLoader.java:18` | XPL（模板输出文本 vs XT 改写树结构） |
| **XPath** | XLang 的路径子语言：对 `XNode` 等对象图做选择器与谓词求值，支持 `$value`/`$text`/`$node` 等后缀算子；编译产物为无状态 `IXSelector`，按路径文本缓存复用（`XPathHelper`）。 | `xlang/xpath/XPathHelper.java:20-29`；`xlang/xpath/parse/XPathSelectorParser.java:46`；`xlang/XLangConstants.java:29-36` | XT（路径求值 vs 转换规则）；JPath（nop-core 的 JSON 路径，另一套机制） |
| **Expression** | 表达式 AST 的手写抽象基类：全部编译期节点的公共父类——语句也继承它（`_Statement extends Expression`），即"一切皆表达式"；携带类型推断结果 `returnTypeInfo`，并提供反打印 `toExprString()`。 | `xlang/ast/Expression.java:18-45` | AbstractExecutable（编译产物，见边界组）；`_gen/_Expression`（codegen 生成基类） |
| **XLangASTNode / XLangASTKind** | AST 中枢：`XLangASTNode` 是所有节点的抽象基类（统一树结构、`deepClone`、沿父链回溯词法作用域）；`XLangASTKind` 是 105 值的节点类型枚举，由 codegen 生成并与 Visitor/Processor/Optimizer 三套 `switch(kind)` 派发类同步。 | `xlang/ast/XLangASTNode.java:13-34`；`xlang/ast/IXLangASTNode.java:12-14`（唯一声明 `getASTKind()`）；`xlang/ast/XLangASTKind.java:4` | `nop-kernel/nop-core/src/main/java/io/nop/core/lang/ast/ASTNode.java`（nop-core 通用树基础设施，`XLangASTNode` 的父类，与语言无关） |
| **AbstractExecutable** | 可执行体（编译产物）的手写抽象基类，实现 nop-core 的 `IExecutableExpression`：持有源码位置 `loc`，运行期由 `IExpressionExecutor.execute` 递归求值，统一做异常包装（`NopEvalException` + 错误码）与 XPL 调用栈记录（`addXplStack`）。 | `xlang/exec/AbstractExecutable.java:27-141` | Expression（编译期 AST，见边界组） |
| **IXLangCompileScope** | 编译上下文：编译期作用域接口，继承 `IEvalScope`，管理变量定义、标签库引入（`addLib`/`getCurrentLib`）、标签编译器注册（`addTagCompiler`）与父子作用域链（`newChildScope`）；实现类 `XLangCompileScope`。注：规划材料中的"IXplCompileScope"在仓库中不存在（全仓库检索无此类型），实际类型名为 `IXLangCompileScope`。 | `xlang/api/IXLangCompileScope.java:29-79`；`xlang/scope/XLangCompileScope.java:48` | `EvalRuntime`（运行期求值上下文）；`LexicalScope`（编译期槽位分配分析） |
| **TypeInferenceProcessor** | 类型推断处理器：`XLangASTProcessor<ReturnTypeInfo, TypeInferenceState>` 的子类，遍历 AST 为节点计算返回类型信息（写回 `Expression.returnTypeInfo`）；在表达式编译入口 `buildExecutable` 中按配置开关 `CFG_XLANG_TYPE_INFERENCE_ENABLED` 启用。 | `xlang/compile/TypeInferenceProcessor.java:118-122`；`xlang/expr/XLangExprParser.java:70-71` | XLangASTOptimizer（改写树 vs 只读标注类型）；BuildExecutableProcessor（同样产出结果但产物是可执行体） |
| **XLangASTOptimizer** | AST 改写骨架：按 Kind 全量 `switch` 派发到 `optimizeXxx`，自底向上替换子节点，配合基类 `AbstractOptimizer` 做变更计数与冻结节点写时复制（`deepClone`）；本身不含常量折叠规则，定制通过子类覆写钩子注入（唯一内置用例 `XLangASTTransformer.replaceIdentifier`）。 | `xlang/ast/XLangASTOptimizer.java:8-10`；`core/lang/ast/optimize/AbstractOptimizer.java:17-30` | TypeInferenceProcessor；XLangASTTransformer（改写骨架的具体应用，非平行机制） |
| **域处理器（StdDomain）** | XDef 类型声明 `域:选项` 的解释器：`IStdDomainHandler` 约定 `getName`/`getGenericType(mandatory,options)`/`parseProp`/`validate` 协议，负责把声明文本解析为泛型类型并校验属性值；内置 73 个域集中在 `SimpleStdDomainHandlers`，由 `StdDomainRegistry` 单例注册查找。 | `xlang/xdef/IStdDomainHandler.java:24-27,41-46`；`xlang/xdef/domain/IStdDomainRegistry.java:10-17` | XDef（语言 vs 语言中单个类型声明的执行器）；`dict`/`enum` 域背后的数据字典机制（DictProvider） |
| **xlib** | XPL 标签库：`.xlib` 文件把标签组织为可复用单元，"一个标签库可以看作一个服务实例"——Java 服务接口可自动转换为标签库，标签库也可反向生成 Java 接口；编译期经 `IXplTagLib` 引入名字空间（`c:import`/`xpl:lib`）。XLang 全局函数库则是另一条线：`GlobalFunctions` 等静态方法类经 `EvalGlobalRegistry.registerStaticFunctions` 注册，在脚本中免前缀直接调用。 | `xdefs/xlib.xdef:4-9`；`xlang/xpl/IXplTagLib.java:16-22`；`xlang/functions/GlobalFunctions.java:78`；`xlang/initialize/XLangCoreInitializer.java:47-49` | `.xpl` 模板文件（库 vs 模板）；全局变量（`$` 前缀，判定见 `xlang/api/XLang.java:43-46`） |

## 重点边界组

| 边界 | 划界依据 |
|---|---|
| **XDef vs XDSL**：定义 vs 实例+合并 | XDef 是约束坐标（`域:选项` 类型声明，`xdefs/xdef.xdef:7-9` 同像约束）；XDSL 是按坐标书写的实例模型并携带 `x:extends` 合并键（`xlang/xdsl/XDslKeys.java:36-50`）。二者的接缝是 `IXDefinition extends IXDefNode, IXDslModel`（`xlang/xdef/IXDefinition.java:22`）——xdef 解析产物自身也是 XDSL 模型，故 xdef 可被差量定制。 |
| **XPL vs XScript**：模板 vs 脚本 | XPL 的输入输出是 XML 标签树，编译为输出类 Expression（`xlang/xpl/IXplCompiler.java:26-44`）；XScript 是语句序列，与 XLang 表达式共用同一编译器（`ScriptTagCompiler.java:61` 缺省 lang 直接 `parseFullExpr`）。两个方向可互嵌：脚本中用 `` xpl`...` `` 模板字面量调标签（编译期宏），模板中用 `<c:script>` 嵌脚本（`docs-for-ai/02-core-guides/xlang-and-xpl-basics.md:184-209`）。 |
| **Expression vs AbstractExecutable**：AST vs 编译产物 | Expression 是编译期表示：可打印（`toExprString`）、可优化、可标注类型（`xlang/ast/Expression.java:18-45`）；AbstractExecutable 是运行期表示：只可 `execute`，不携带子树结构语义（`xlang/exec/AbstractExecutable.java:27`）。转换点是编译管线末端的 `BuildExecutableProcessor`（AST→`IExecutableExpression`），全程见[编译管线](./flows/compile-pipeline.md)与[表达式求值](./flows/expression-eval.md)。 |
| **编译期 vs 运行期** | 编译期角色：`IXLangCompileScope`（符号与标签库）、`TypeInferenceProcessor`、`XLangASTOptimizer`；运行期角色：`EvalRuntime`（作用域+栈帧）与 `IExpressionExecutor`，统一出口 `XLang.execute`（`xlang/api/XLang.java:47`）。同一名字 `scope` 在两侧对应不同类型——编译期查 `IXLangCompileScope`，运行期查 `IEvalScope` 实现链，这是读代码时最常撞上的分界。 |

## Sources

- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/XLangConstants.java:27-36](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/XLangConstants.java#L27-L36)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLang.java:40-47](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLang.java#L40-L47)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/IXLangCompileScope.java:29-79](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/IXLangCompileScope.java#L29-L79)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/scope/XLangCompileScope.java:48](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/scope/XLangCompileScope.java#L48)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/IXDefinition.java:22](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/IXDefinition.java#L22)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/IStdDomainHandler.java:24-46](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/IStdDomainHandler.java#L24-L46)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/IStdDomainRegistry.java:10-17](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/IStdDomainRegistry.java#L10-L17)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/XDslConstants.java:10-24](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/XDslConstants.java#L10-L24)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/XDslKeys.java:36-50](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/XDslKeys.java#L36-L50)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/XplConstants.java:16-30](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/XplConstants.java#L16-L30)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/IXplCompiler.java:22-44](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/IXplCompiler.java#L22-L44)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/IXplTagLib.java:16-22](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/IXplTagLib.java#L16-L22)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/tags/ScriptTagCompiler.java:61-78](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/tags/ScriptTagCompiler.java#L61-L78)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/script/IScriptCompiler.java:16-21](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/script/IScriptCompiler.java#L16-L21)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xt/IXTransform.java:14-19](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xt/IXTransform.java#L14-L19)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xt/loader/XtTransformModelLoader.java:18](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xt/loader/XtTransformModelLoader.java#L18)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpath/XPathHelper.java:20-29](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpath/XPathHelper.java#L20-L29)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpath/parse/XPathSelectorParser.java:46](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpath/parse/XPathSelectorParser.java#L46)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/Expression.java:18-45](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/Expression.java#L18-L45)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTNode.java:13-34](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTNode.java#L13-L34)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/IXLangASTNode.java:12-14](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/IXLangASTNode.java#L12-L14)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTKind.java:4](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTKind.java#L4)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTOptimizer.java:8-10](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTOptimizer.java#L8-L10)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/TypeInferenceProcessor.java:118-122](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/TypeInferenceProcessor.java#L118-L122)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/expr/XLangExprParser.java:70-71](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/expr/XLangExprParser.java#L70-L71)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/AbstractExecutable.java:27-141](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/AbstractExecutable.java#L27-L141)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/functions/GlobalFunctions.java:78](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/functions/GlobalFunctions.java#L78)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/initialize/XLangCoreInitializer.java:47-49](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/initialize/XLangCoreInitializer.java#L47-L49)
- [nop-kernel/nop-core/src/main/java/io/nop/core/lang/ast/optimize/AbstractOptimizer.java:17-30](/nop-kernel/nop-core/src/main/java/io/nop/core/lang/ast/optimize/AbstractOptimizer.java#L17-L30)
- [nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xdef.xdef:4-9](/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xdef.xdef#L4-L9)
- [nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xpl.xdef:8-9](/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xpl.xdef#L8-L9)
- [nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xt.xdef:4-9](/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xt.xdef#L4-L9)
- [nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xlib.xdef:4-9](/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xlib.xdef#L4-L9)
- [docs-for-ai/04-reference/glossary.md:22-36](/docs-for-ai/04-reference/glossary.md#L22-L36)
- [docs-for-ai/02-core-guides/xlang-and-xpl-basics.md:105-209](/docs-for-ai/02-core-guides/xlang-and-xpl-basics.md#L105-L209)

---

## On this page

- 术语总表
- 重点边界组
