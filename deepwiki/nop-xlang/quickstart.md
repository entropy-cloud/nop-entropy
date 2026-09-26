# 快速上手

> 本页断言基于以下源文件（相对本页 `../nop-kernel/nop-xlang/...`，已逐条验证存在）：
>
> - [../nop-kernel/nop-xlang/pom.xml](../../nop-kernel/nop-xlang/pom.xml)
> - [../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLang.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLang.java)
> - [../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java)
> - [../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/ExprEvalAction.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/ExprEvalAction.java)
> - [../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/AbstractEvalAction.java](../../nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/AbstractEvalAction.java)
> - [../nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestXLangParser.java](../../nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestXLangParser.java)
> - [../nop-kernel/nop-xlang/src/test/java/io/nop/xlang/xpl/TestXpl.java](../../nop-kernel/nop-xlang/src/test/java/io/nop/xlang/xpl/TestXpl.java)

兄弟页：[总览](./overview.md) · [阅读指南](./reading-guide.md)

nop-xlang 是 XLang 语言的编译与求值引擎，无 Spring 依赖，可在任意 Java 程序中以三行代码编译并求值表达式（见第 3 节）。

## 1. 构建

Maven 坐标（`nop-kernel/nop-xlang/pom.xml:13`，版本继承父 POM `io.github.entropy-cloud:nop-kernel:2.0.0-SNAPSHOT`，见 `pom.xml:7-11`）：

```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-xlang</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

编译期依赖共 7 个（`pom.xml:17-63`）：`nop-commons`、`nop-core`、`nop-xdefs`、`nop-antlr4-common`、`nop-antlr4-tool`（`optional=true`，`pom.xml:41`）、`jakarta.validation-api`、`janino`；测试域仅 `junit-jupiter`（`pom.xml:59-63`）。构建产物额外打出 test-jar（`maven-jar-plugin` 的 `test-jar` goal，`pom.xml:69-79`），供其他模块复用测试基建。

本地从源码构建并跑该模块测试：

```bash
./mvnw install -pl nop-kernel/nop-xlang -am
```

## 2. 测试

测试代码位于 `nop-kernel/nop-xlang/src/test/java/io/nop/xlang/`，按子包划分：`expr`（表达式）、`xpl`（模板）、`xdef`、`xdsl`、`xmeta`、`exec`、`backend`、`compare`、`janino`、`xpath` 等。以工作区当前状态统计（`find`/`grep` 核实，未运行测试）：

- `Test*.java` 测试类共 **66** 个，其中 64 个含 `@Test`；
- `@Test` 方法共 **492** 个（`grep -r "@Test"` 命中 493 行，扣除 `TestXScript.java:28` 一行注释掉的用例）；
- 另有 **3** 个 `@ParameterizedTest` 方法：`TestXpl.runTest`、`TestXScript.runTest`、`TestCorpusV1InterpreterBaseline`（`grep -rc "@ParameterizedTest"`）。`TestXpl` 与 `TestCorpusV1InterpreterBaseline` 完全不使用 `@Test`，因此按 `@Test` 计数会漏掉它们。

用例数排名靠前的类（`grep -c "@Test"` 逐文件核实）：

| 测试类 | @Test 数 | 覆盖点 |
|---|---|---|
| `compile/TestTypeInferenceProcessor` | 117 | 类型推导 |
| `expr/simple/TestTypeDefinitionParser` | 31 | 类型定义文法 |
| `expr/TestSimpleExprParser` | 27 | 简单表达式 `compileSimpleExpr` |
| `xt/TestXtTransform` | 26 | `xt:transform` 代码生成模板 |
| `backend/TestEvalBackendRouterDecisions` | 17 | 执行后端路由 |
| `delta/TestDeltaMerger` | 15 | Delta 合并 |
| `expr/TestScopeVarAccess` | 13 | 作用域变量访问 |
| `expr/TestXLangParser` | 12 | 表达式编译/求值/AST |
| `xpath/TestXPath` | 11 | XPath |
| `compile/TestUnionTypeNarrower` | 10 | 联合类型收窄 |

两个参数化测试是"Markdown 即用例"的驱动器：`TestXpl` 读取 `src/test/resources/io/nop/xlang/xpl/xpls/` 下 27 个 `*.test.md`，每个 Markdown 小节编译为 XPL 并按 `outputMode` 属性断言（`TestXpl.java:51-57` 加载、`TestXpl.java:59-92` 编译求值与断言）；`TestXScript` 以同样机制驱动 `exprs/` 目录的 XScript 用例（`TestXScript.java:54-67`）。

运行方式：

```bash
./mvnw test -pl nop-kernel/nop-xlang -am
```

## 3. 最小用法

### 3.1 编程式编译并求值表达式

入口是 `XLang.newCompileTool()`（`XLang.java:72-74`），它内部构造 `IXplCompiler` 并包成 `XLangCompileTool`（`XLangCompileTool.java:64-68`）。范本取自 `TestXLangParser.testHexLiteralUnsignedParse`（`TestXLangParser.java:57-64`）与 `testStrictEqWithVariable`（`TestXLangParser.java:170-178`）：

```java
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;

public class QuickStart {
    public static void main(String[] args) {
        // 1) 初始化核心运行时（注册全局函数、类型系统等），测试类在 @BeforeAll 中同样先执行它
        //    参照 TestXLangParser.java:32-35
        CoreInitialization.initialize();
        try {
            // 2) 编译：compileFullExpr(源码位置, 表达式文本)，返回 ExprEvalAction
            ExprEvalAction action = XLang.newCompileTool()
                    .allowUnregisteredScopeVar(true)   // 允许引用未预注册的变量，见 XLangCompileTool.java:92-95
                    .compileFullExpr(null, "x === 10 ? 'yes' : 'no'");

            // 3) 绑定作用域变量并求值
            IEvalScope scope = XLang.newEvalScope();   // XLang.java:64-66
            scope.setLocalValue("x", 10);
            Object result = action.invoke(scope);      // ExprEvalAction.java:45-47
            System.out.println(result);                // 输出: yes
        } finally {
            CoreInitialization.destroy();
        }
    }
}
```

要点：

- `compileFullExpr` 编译整段 XScript（可含 `return`、多语句），`compileSimpleExpr` 只接受单个表达式（`XLangCompileTool.java:151-159`；`TestSimpleExprParser.java:54` 是 `compileSimpleExpr` 的真实调用）。
- 编译一次、多次 `invoke` 可换不同 scope 复用；未注册变量默认编译报错，除非调用 `allowUnregisteredScopeVar(true)`（`TestXLangParser.java:66-76` 展示了两种用法的对比）。
- `ExprEvalAction.invoke(IEvalContext)` 接受任何 `IEvalContext`，测试里也直接传 `new ServiceContextImpl()`（`TestXpl.java:65`）。

### 3.2 编译 XPL 模板

范本取自 `TestXpl.runTestSection`（`TestXpl.java:59-92`）。`compileXpl(loc, source)` 先把文本解析为 XNode 再走 `compileTag`（`XLangCompileTool.java:300-303`）；按输出形态选择消费方式：`invoke`（`outputMode=none`）、`generateText`（text/html/xml，默认方法定义在 `nop-kernel/nop-core/src/main/java/io/nop/core/resource/tpl/ITextTemplateOutput.java:35`）、`generateNode`（`AbstractEvalAction.java:47-49`）：

```java
import io.nop.core.context.IEvalContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;

CoreInitialization.initialize();
try {
    // 编译 XPL 模板（TestXpl.java:60 同款调用）
    ExprEvalAction action = XLang.newCompileTool()
            .compileXpl(null,
                "<c:unit><c:script>const n = 2;</c:script>sum = ${n + 3}</c:unit>");

    // 变量绑定方式与 3.1 相同：XLang.newEvalScope() 后 setLocalValue，
    // 模板内的 const 等局部变量也可直接写在 <c:script> 中
    IEvalContext ctx = new ServiceContextImpl();

    // outputMode=none：执行副作用，返回值经 invoke 取
    Object result = action.invoke(ctx);

    // outputMode=text/html/xml：生成文本（TestXpl.java:79 同款调用）
    String text = action.generateText(ctx);
} finally {
    CoreInitialization.destroy();
}
```

对应的 XPL 源码形态（节选自测试语料 `nop-kernel/nop-xlang/src/test/resources/io/nop/xlang/xpl/xpls/c-assign.test.md`，完整语料含 `c:for`、`c:if`、`c:choose`、宏、slot 等 27 个文件）：

```xml
<c:unit>
  <c:script>
    const entity = { x : 3, y : 'ss' }
  </c:script>
  <c:assign obj="${entity}">
     <field name="x" value="${entity.x + 2}" />
  </c:assign>
</c:unit>
```

注意：`TestXpl` 不在编译器上设置 `outputMode`，而是在断言侧按 Markdown 属性选择 `invoke`/`generateText`/`generateNode`（`TestXpl.java:61-90`）；若需要编译期固化输出形态，用 `XLangCompileTool.outputMode(...)`（`XLangCompileTool.java:106-109`）。

## Sources

- [nop-kernel/nop-xlang/pom.xml:7-11](/nop-kernel/nop-xlang/pom.xml#L7-L11)
- [nop-kernel/nop-xlang/pom.xml:13](/nop-kernel/nop-xlang/pom.xml#L13)
- [nop-kernel/nop-xlang/pom.xml:17-63](/nop-kernel/nop-xlang/pom.xml#L17-L63)
- [nop-kernel/nop-xlang/pom.xml:69-79](/nop-kernel/nop-xlang/pom.xml#L69-L79)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLang.java:40](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLang.java#L40)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLang.java:60-74](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLang.java#L60-L74)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java:55](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java#L55)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java:64-68](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java#L64-L68)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java:92-95](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java#L92-L95)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java:106-109](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java#L106-L109)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java:151-159](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java#L151-L159)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java:300-303](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/XLangCompileTool.java#L300-L303)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/ExprEvalAction.java:45-47](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/ExprEvalAction.java#L45-L47)
- [nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/AbstractEvalAction.java:47-49](/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/api/AbstractEvalAction.java#L47-L49)
- [nop-kernel/nop-core/src/main/java/io/nop/core/resource/tpl/ITextTemplateOutput.java:35](/nop-kernel/nop-core/src/main/java/io/nop/core/resource/tpl/ITextTemplateOutput.java#L35)
- [nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestXLangParser.java:32-35](/nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestXLangParser.java#L32-L35)
- [nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestXLangParser.java:57-64](/nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestXLangParser.java#L57-L64)
- [nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestXLangParser.java:66-76](/nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestXLangParser.java#L66-L76)
- [nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestXLangParser.java:170-178](/nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestXLangParser.java#L170-L178)
- [nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestXScript.java:28](/nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestXScript.java#L28)
- [nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestXScript.java:47-67](/nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestXScript.java#L47-L67)
- [nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestSimpleExprParser.java:54](/nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestSimpleExprParser.java#L54)
- [nop-kernel/nop-xlang/src/test/java/io/nop/xlang/xpl/TestXpl.java:51-57](/nop-kernel/nop-xlang/src/test/java/io/nop/xlang/xpl/TestXpl.java#L51-L57)
- [nop-kernel/nop-xlang/src/test/java/io/nop/xlang/xpl/TestXpl.java:59-92](/nop-kernel/nop-xlang/src/test/java/io/nop/xlang/xpl/TestXpl.java#L59-L92)
- [nop-kernel/nop-xlang/src/test/resources/io/nop/xlang/xpl/xpls/c-assign.test.md:1-24](/nop-kernel/nop-xlang/src/test/resources/io/nop/xlang/xpl/xpls/c-assign.test.md#L1-L24)

---

## On this page

- 1. 构建
- 2. 测试
- 3. 最小用法
