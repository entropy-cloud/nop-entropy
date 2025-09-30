# How to Implement QR Code Export in NopReport

NopReport is a next-generation Chinese-style reporting engine written from scratch. Its core has just over 3,000 lines of code, yet it fully implements the hierarchical coordinates and the row/column symmetric expansion algorithm defined by the Chinese-style non-linear reporting theory.

* Introduction: [An open-source Chinese-style reporting engine using Excel as the designer: NopReport](https://zhuanlan.zhihu.com/p/620250740), [Video](https://www.bilibili.com/video/BV1Sa4y1K7tD/)
* Source analysis: [Source code analysis of the non-linear Chinese-style reporting engine NopReport](https://zhuanlan.zhihu.com/p/663964073), [Video](https://www.bilibili.com/video/BV17g4y1o7wr/)

NopReport does not come with business-specific components such as QR code rendering built in, but it adheres to the Reversible Computation theory and therefore includes a wealth of extensibility mechanisms to introduce extension components. This article takes the implementation of QR code export as an example to introduce NopReport’s extensibility mechanisms. These mechanisms are naturally derived from the Reversible Computation theory and are not limited to the Nop platform; they can also provide guidance for extensibility in other frameworks.

## I. Configuring QR Code Export

Currently, the `nop-report-ext` module provides the QR code extension component. To use it, add the following dependency:

```xml
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-report-ext</artifactId>
    </dependency>
```

In the Excel template, call the `QRCODE()` extension function via the cell’s annotation. [Sample template](https://gitee.com/canonical-entropy/nop-entropy/raw/master/nop-report/nop-report-demo/src/main/resources/_vfs/nop/report/demo/base/11-%E6%89%93%E5%8D%B0%E6%9D%A1%E7%A0%81%E5%92%8C%E4%BA%8C%E7%BB%B4%E7%A0%81.xpt.xlsx)

![](report/qrcode-config.png)

* valueExpr: Here we directly specify a demonstration output value via valueExpr. In actual development, you can use other built-in mechanisms of NopReport to generate the cell value.
* formatExpr: Since we don’t need to output the cell value in the final Excel or the HTML page for display, we set formatExpr to return an empty string. Otherwise, the corresponding text would be overlaid on top of the QR code.
* processExpr: Calls the extension function `QRCODE` to actually generate the QR code.
* `qr:barcodeFormat`: Specifies the output barcode format. The default is `QRCODE`; set it to `CODE_128` to generate a barcode.

Variables with the `qr:` prefix are extension data passed to the `QRCODE` function, but they do not need to be passed directly as `QRCODE` function parameters. The properties you can set correspond to the member variables in the [QrcodeOptions.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-integration/nop-integration-api/src/main/java/io/nop/integration/api/qrcode/QrcodeOptions.java) class.

You can use `qr:width` and `qr:height` to specify the output image size. If not specified, the current cell’s width and height are used automatically.

## II. Implementation Principles

### 1. Extensible Attributes of the Cell Model

NopReport’s design follows the principles of Reversible Computation, systematically adopting the pairing design of `(data, ext_data)` to ensure that extension attributes can be appended at any model node. By default, all namespaced attributes do not participate in meta-model validation, so we can introduce a `qr` namespace and use it to set the configuration information required for QR code output, such as QR code format and size. If you need to validate the attribute formats in the qr namespace, you can introduce a custom xdef meta-model.

```xml
<workbook xdef:check-ns="qr">
  <sheets>
    <sheet>
      <table>
        <rows>
          <cell>
            <model xdef:name="XptCellModel"
                   qr:barcodeFormat="string" qr:margin="int" qr:imgType="string" qr:width="double"
                   qr:height="double" qr:encoding="string" qr:errorCorrection="int">
            </model>
          </cell>
        </rows>
      </table>
    </sheet>
  </sheets>
</workbook>
```

Currently, NopReport uses Excel as the visual designer, and the cell model information is set in the cell’s annotations. An online visual editor will be provided later; at that time, the property definitions declared in the xdef meta-model can be used to automatically generate visual editing pages.

### 2. Extensible Function Space

NopReport provides multiple expression configurations such as `expandExpr`, `valueExpr`, `formatExpr`, `styleIdExpr`, and `processExpr` to call external functions for complex logic processing. NopReport’s expression engine is extended from the Nop platform’s built-in XLang expression engine (it adds the reporting hierarchical coordinate syntax on top of XLang EL), so it automatically inherits the global functions and global objects defined in XLang. Meanwhile, the report engine also introduces a series of report-specific functions for the report execution environment.

#### Global Functions

```javascript
// Register XLang EL global functions
EvalGlobalRegistry.instance().registerStaticFunctions(GlobalFunctions.class);

// Register report-specific functions for the Report execution environment
ReportFunctionProvider.INSTANCE.registerStaticFunctions(ReportExtFunctions.class);
```

In general, you can follow the approach used in the `nop-report-ext` module and register extension functions during initialization.

```java
public class ReportExtInitializer {

    @PostConstruct
    public void init() {
        ReportFunctionProvider.INSTANCE.
                  registerStaticFunctions(ReportExtFunctions.class);
    }
}
```

#### Integrating the IoC Container

In addition to global registration, you can directly obtain beans managed by the NopIoC container in expressions via the `inject` function, for example, `inject('qrService').genQrCode('123456')`.

> Since NopIoC supports a BeanScope concept similar to the Spring container, beans obtained from NopIoC are not necessarily singletons.

#### Passing in at Call Time

When invoking a specific report, you can also pass helper objects via the scope object.

```javascript
IEvalScope scope = XLang.newEvalScope();
scope.setLocalValue("myTool", new MyTool());
reportEngine.getRenderer("/my.xpt.xlsx","html").generateToFile(file, scope);
```

You can then call methods on the myTool object in expressions, for example, `myTool.myMethod(cell.value)`.

#### Defined Within the Report

A significant difference between the NopReport engine and typical report engines is that it strongly emphasizes the self-containment and custom abstraction capabilities of the report model. In the report model’s “Before Expand” configuration, we can define functions that are used only within this report. This function definition is stored in the report model and does not require external registration or passing in.

![](report/fn-def.png)

In the “Before Expand” configuration, we can use the XPL template language’s tag library abstraction to dynamically load external tag functions. Subsequently, the Nop platform will provide a general-purpose visual logic orchestration designer for all XPL configuration sections, enabling you to introduce custom functions into the report model through visual configuration.

## 3. Implicitly Passed Context

The Nop platform provides a set of standard patterns for developing custom Domain Models and Domain-Specific Languages (DSLs). Among these is the concept of implicit context introduced into the expression language.

When we work within a specific domain (or specific business scenario), there is always certain systematic background knowledge. When writing domain-specific code, we can assume this background knowledge is known or can be deterministically derived, so in principle it does not need to be explicitly specified in code. However, in most cases we program using general-purpose languages and frameworks, and there is no simple, standardized way to bake this knowledge into the language. As a result, we often find that a lot of glue code, which merely serves to bind parts together, repeatedly expresses the same background information.

For instance, in a reporting engine, our background knowledge is that there is always a context object `IXptRuntime` at runtime. Can we avoid explicitly passing this parameter when calling functions and instead assume it is background knowledge that can be passed implicitly? If we don’t want to explicitly pass IXptRuntime to all functions, the common approach is to pass the context object via something like `ThreadLocal`, a quasi-global variable. This approach breaks function structure and introduces unnecessary complexity.

The Nop platform’s XLang language introduces the concept of implicit parameters, which is similar to Scala’s implicit syntax.

```scala
// Implicit parameters in Scala
def welcome(implicit name: String) = s"Welcome, $name!"

implicit val guestName: String = "Guest"

println(welcome) // Output: Welcome, Guest!
```

In Scala, the language automatically looks up implicit variables in the context by type and binds them to function parameters. The Nop platform’s Xpl template language also provides implicit parameters, but they are bound implicitly by name.

```xml
<!-- Tag library my.xlib -->

<lib>
  <tags>
    <MyTag>
      <attr name="xptRt" implicit="true" />
      <source>
        ...
      </source>
    </MyTag>
  </tags>
</lib>
```

When calling the tag, you can pass the xptRt parameter. You can also omit it, in which case it will automatically bind to a same-named variable from the context.

```xml
<my:MyTag />
```

In XLang expressions, implicit passing of IEvalScope is also provided.

```
    @EvalMethod
    public static ExcelImage QRCODE(IEvalScope scope) {
        IXptRuntime xptRt = IXptRuntime.fromScope(scope);
        ExpandedCell cell = xptRt.getCell();

        QrcodeOptions options = new QrcodeOptions();
        cell.getModel().readExtProps("qr:", true, options);
        ...
        return image;
    }
```

If a function is annotated with `@EvalMethod`, its first parameter must be IEvalScope. When called from an expression, the expression’s runtime scope is automatically passed in. Through the scope, you can access other variables in the context.

The `QRCODE` function defined in `ReportExtFunctions` uses this implicit parameter mechanism, so there is no need to explicitly pass the IXptRuntime context object. Inside the `QRCODE` function, you can obtain the current cell being processed via IXptRuntime, and thereby access the extended attributes on the cell model.
<!-- SOURCE_MD5:c35e458e2bdd241f78faa6a942c97d55-->
