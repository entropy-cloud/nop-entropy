# The DSLs Used in the Nop Code Generator

The Nop platform was written from scratch based on the so-called Reversible Computation theory. Its overall implementation can be regarded as repeated applications of the following software construction formula at different levels of abstraction:

> `App = Delta x-extends Generator<DSL>`

To concretize this abstract formula into specific technical implementations, the Nop platform builds in a set of general mechanisms to implement Generators, define DSLs (Domain Specific Languages), and split/merge Deltas. It is important to emphasize that the fundamental difference between the Nop platform and traditional technologies is that it provides a general solution guided by Reversible Computation theory. From a traditional perspective, DSL design is typically an ad-hoc solution for edge domains whose usefulness depends largely on luck; fully general-purpose solutions are thought not to exist. We often see statements like:

> Redefining a new DSL to replace the Java language that developers are already familiar with—where does the confidence come from to surpass masters of language design? And beyond that, the development process still needs IDEs, debugging environments, and tools.

As the saying goes, people cannot comprehend what they have not yet comprehended. Because the current mainstream technology stacks do not offer solutions, many people believe that certain problems have no general solution, and further assume that some problems do not exist or do not need to be solved: **problems that cannot be solved are subconsciously regarded as problems that do not need to be solved**.

The Nop platform aims to be a general Domain Specific Language Workbench. In other words, it provides a series of technical supports for creating new domain languages, striving to minimize the cost of building a new domain language. The most important part of a domain-specific language is its semantic structure (what domain-specific atomic concepts exist and how they interact), while surface syntax is secondary. **Designing and implementing a DSL does not need to start from scratch; you can reuse a variety of general-purpose infrastructure**. The emergence of Nop is a natural consequence of the development of general-purpose language design reaching its peak—further development following the standardization of general-purpose language infrastructure. In fact, developing a new general-purpose language in 2024 costs far less than it did 20 years ago; for example, a small team of a dozen people like Moonbit can develop a new language. And the runtime for a DSL does not have the stringent performance requirements of a general-purpose language; it can be translated into a general-purpose language via a Generator, making it far cheaper than building a general-purpose language.

The Nop platform systematically uses DSLs to solve problems in traditional software engineering, which leads to the introduction of a large number of custom DSLs. To those unfamiliar with Nop, it may look like a bunch of xdef, xpl, xrun, xdsl files, and even Antlr—so how do these DSL files with different suffixes work together? This article explains the various DSLs used in the Nop platform’s code generator using the execution process of XCodeGenerator as an example.

## 1. XCodeGenerator Execution Flow

The Generator mentioned in the formula `App = Delta x-extends Generator<DSL>` is an abstract concept that broadly represents any abstract transformation mechanism on domain structures. Its concrete manifestations can be standalone code generation tools or macro functions and metaprogramming mechanisms built into programming languages. XCodeGenerator is a general-purpose code generator built into the Nop platform.

> See [The most powerful model-driven code generator: NopCodeGen](https://mp.weixin.qq.com/s/rd36AFh5pmjwtRFmApRswg)

Generally, we can invoke XCodeGenerator via a Maven plugin:

![](images/CodeGenTask.png)

We can also invoke XCodeGenerator using the nop-cli command-line tool:

![](images/nop-cli.png)

When we run the command `java -jar nop-cli.jar gen model/test.orm.xlsx -t=/nop/templates/orm` from the command line, the logic it actually executes can be expressed in pseudocode as:

```javascript
   templateDir = "/nop/templates/orm"
   modelPath = "model/test.orm.xlsx"
   // Specify the template directory and input file directory via parameters
   codegen = new XCodeGenerator(templateDir, targetDir);
   codegen.renderModel(modelPath, scope);
```

The execution logic of XCodeGenerator’s renderModel function can be expressed in pseudocode as:

```javascript
void renderModel(modelPath,scope){
   // Parse the model file to obtain the model object
   codeGenModel = ResourceComponentManager.instance().loadComponentModel(modelPath);
   // Put it into the execution context
   scope.setLocalValue("codeGenModel", codeGenModel);
   // Execute the code generation templates
   execute(scope);
}
```

The execute function of XCodeGenerator, in pseudocode:

```javascript
void execute(scope){
    // Run the init file under the root of templateDir
    runInitFile("@init.xrun",scope);
    // Retrieve the NestedLoop loop model object created by the init code
    codeGenLoop = scope.getValue("codeGenLoop");
    // Recursively traverse each templatePath
    //   Use NestedLoop.loopForVars(templatePath) to determine how many times each template file should be generated
    //   For each combination of loop variables, execute code generation once
    processDir(codeGenLoop)
}
```

XCodeGenerator uses the following DSLs:
![](images/codegen-dsl.png)

## 2. Parsing Excel Files into ExcelWorkbook

The Nop platform does not use the Apache POI library to parse Excel files. Instead, it implements the xlsx file format parser ExcelWorkbookParser from scratch.

```javascript
ExcelWorkbook wk = new ExcelWorkbookParser().parseFromFile(file);
```

ExcelWorkbook is a model object defined by Nop for the Excel tabular data model. It provides a simplified description of the Excel model; you can parse an ExcelWorkbook from an xlsx file, and you can also generate an xlsx from an ExcelWorkbook.

```javascript
new ExcelTemplate(wk).generateToFile(file, XLang.newEvalScope());
```

ExcelWorkbook supports only some basic Excel features (including styles, images, etc.); many advanced features are not supported. However, for most applications, the information provided by ExcelWorkbook is rich enough, and for report import/export scenarios, Excel’s advanced features are typically unnecessary.

A distinguishing feature of the Nop platform versus other technologies is: **every model object has a corresponding DSL format, and every DSL has a corresponding XDef meta-model definition**. For example, ExcelWorkbook is a modeling result for the tabular data model; it is not bound to Excel. Excel is merely one serialization form of ExcelWorkbook; it can also be serialized into a simpler xpt.xml model file.

```javascript
ExcelWorkbook wk = (ExcelWorkbook) DslModelHelper.loadDslModel(resource);
DslModelHelper.saveDslModel("/nop/schema/excel/workbook.xdef",
                  wk, targetResource);
```

![](images/workbook-model.png)

1. First define the workbook.xdef meta-model, then automatically generate the code for the ExcelWorkbook model class from it.

2. Based on the information in workbook.xdef, ExcelWorkbook can be saved as an xpt.xml model file and parsed back. All DSL models defined via xdef can automatically support parsing and serialization. **By analogy: JSON serialization = object + annotations; DSL serialization = object + XDef**. Since XML and JSON can be converted to each other on the Nop platform, although DSL models are typically stored in XML, they can also be stored in formats like YAML or JSON. JSON objects edited by the front-end visual designer can be automatically saved in XML format. **The Nop platform emphasizes that the same information can have multiple manifestations (multiple representations), and there can be automatic reversible conversions between different representations**.

3. The mutual conversion between ExcelWorkbook and xlsx is implemented via ExcelWorkbookParser and ExcelTemplate. The Nop platform does not use the POI library; it parses xlsx files itself (an xlsx file is essentially a zip archive of several XML files). Note that **the Nop platform does not use Java’s built-in XML parsers nor the DOM node interfaces defined by the XML standards**; it uses a custom XNodeParser and a custom XNode structure written from scratch.

> Some people remain fixated on Nop’s extensive use of XML, believing XML is outdated and that any technology using XML is behind the times. In reality, XML as a format is not the problem; most issues encountered with XML stem from a series of bloated and cumbersome XML specifications and their implementations. By implementing its own parsers and transformers for XML structures, the Nop platform naturally avoids related issues. See [Why the Nop platform insists on XML instead of JSON or YAML](https://zhuanlan.zhihu.com/p/651450252)

One point to note is that the DSL file format corresponding to ExcelWorkbook is generally `xpt.xml`, i.e., the Xpt report model file. In the design of the Nop platform, an Excel file without any dynamic expansion logic is also a valid report template file, so any ExcelWorkbook can be directly saved as a valid `xpt.xml`.

## 3. Parsing ExcelWorkbook into ModelObject

The Nop platform extensively uses models to express requirements: for example, using data models to define database schema and API models to define backend service interfaces. These models can be expressed as DSLs in XML format. However, XML is a language more familiar to technical staff, while business users are more familiar with Excel. If business users can directly edit and view model information in Excel, requirement documents can serve directly as inputs to the code generation tool, ensuring that models and code remain consistent forever.

The Nop platform provides a general-purpose [ImportExcelParser](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-excel/src/main/java/io/nop/excel/imp/ImportExcelParser.java) that can automatically parse an ExcelWorkbook into a model object according to an ImportModel configuration. During parsing, detailed field validation is performed and complex transformation logic can be executed; if an error is found, it will indicate the exact cell and the reason for the error.

![](images/excel-to-obj.png)

Traditionally, parsing Excel requires writing format-specific parsing code for particular needs, and ultimately the Excel format must strictly match the format assumed by the parsing code. Operations that are semantically invariant, such as adding blank rows or reordering cells, still break the parser and lead to failures.

The Nop platform offers a general-purpose parser that decides how to parse an ExcelWorkbook into field values based on the information provided by the ImportModel. It runs conversion and validation logic and finally assembles a strongly typed Java object. In the reverse direction, given a ModelObject and the metadata provided by a ReportModel, we utilize the NopReport engine to generate an ExcelWorkbook.

Note that the conversion logic here is more complex than JSON serialization. The parsed model object loses the Excel formatting and layout information. Its conversions to/from ExcelWorkbook are asymmetric in both directions; you can think of it as a non-symmetric extension of JSON serialization.

> **ExcelWorkbook  + ImportModel => ModelObject**
>
> **ModelObject + ReportModel => ExcelWorkbook**

ImportModel is the model information used by ImportExcelParser at runtime. It can be saved as an imp.xml file—i.e., it exists in the form of a DSL.

### Report Model (ReportModel)

The Nop platform provides a Chinese-style report engine that supports complex row/column expansion algorithms and hierarchical coordinates. See [NopReport: An open-source Chinese-style report engine using Excel as its designer](https://mp.weixin.qq.com/s/_nKUiryetF2O5zSrPfU8FQ). When generating Excel from a ModelObject, we typically do not simply fill property values into specific cells; instead, there may be nested child object lists requiring conditionals, loops, and other logic to dynamically generate rows—even columns. Therefore, generating an ExcelWorkbook from a ModelObject is performed via the NopReport engine.

In the implementation of NopReport, ReportModel is built upon ExcelWorkbook, augmented with model information required by the report expansion computation.

> ReportModel = ExcelWorkbook + XptWorkbookModel

The Nop platform provides multiple ways to express a ReportModel:

1. Use the xpt.xml DSL model file; parse `xpt.xml` to obtain a ReportModel. The `xpt.xml` is constrained by the `workbook.xdef` meta-model.

2. NopReport also supports defining report models using Excel as the carrier, with annotations in regular Excel files to introduce additional configuration.

3. A third way to define a ReportModel is to automatically infer it from the `imp.xml` import model configuration combined with an empty template.

![](../../user-guide/report/MOM-YOY-report.png)

## 4. A Unified Model Loader

The Nop platform introduces a large number of model file formats, and the same model can correspond to multiple file formats. For example, an ORM model can be saved in an Excel file like `nop-auth.orm.xlsx`, or in an XML DSL file like `app.orm.xml`.

The Nop platform provides a programming paradigm called Language Oriented Programming (LOP). That is, before solving business problems, we first define a DSL for the relevant business domain, and then use that DSL to solve the business problems. To uniformly manage numerous DSLs, the Nop platform provides a unified model loader.

```javascript
model = ResourceComponentManager.instance().loadComponentModel(path)
```

We can use ResourceComponentManager to load all model files. How does ResourceComponentManager know the mapping between files and model formats? As expected, **the Nop standard approach is to introduce a new `register-model.xml` for registration**.

```xml
<!-- orm.register-model.xml -->
<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       name="orm">
    <loaders>
        <xlsx-loader fileType="orm.xlsx" impPath="/nop/orm/imp/orm.imp.xml"/>
        <xdsl-loader fileType="orm.xml" schemaPath="/nop/schema/orm/orm.xdef"/>
    </loaders>
</model>
```

When the Nop platform starts up, it automatically scans all files in the virtual file system that match `/nop/core/registry/*.register-model.xml` and registers the mappings from file suffixes to model parsers.

In the example above, files ending with `orm.xlsx` will be parsed using ImportExcelParser, with ImportModel information sourced from `/nop/orm/imp/orm.imp.xml`. Files ending with `orm.xml` will be parsed using DslModelParser, with the XDefinition meta-model sourced from `/nop/schema/orm/orm.xdef`.

## 5. Executing Code Generation Templates

File formats such as xrun and xgen used in code generation templates are essentially XPL template language files.

```xml
<!-- xpl.register-model.xml -->
<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       name="xpl">

    <loaders>
        <loader fileType="xpl" class="io.nop.xlang.xpl.loader.HtmlXplModelLoader"/>
        <loader fileType="xgen" class="io.nop.xlang.xpl.loader.HtmlXplModelLoader"/>
        <loader fileType="xrun" class="io.nop.xlang.xpl.loader.NoneXplModelLoader"/>
    </loaders>

</model>
```

The `xpl.register-model.xml` file clearly shows their relationships and differences:

- xpl and xgen are the same thing; when used in code generation, xgen is a specially recognized suffix, and files produced by it will have the xgen suffix removed. For example, `test/a.java.xgen` generates `test/a.java`.

- xrun is also an xpl template file, but it does not allow output; it is used purely for code execution.

**In debug mode, when the Nop platform starts, it will output the contents of all register-model files into `/nop/main/registry/app.registry.xml`. You can inspect this file to know all the file suffixes recognized by the unified model loader.**

### The XLang Language

The xpl template language is part of the broader XLang language family. XLang is a key piece of infrastructure in the Nop platform, providing a set of sub-languages that together form a family of programming languages with built-in support for the Delta concept, compile-time metaprogramming, and the computation model `Delta x-extends Generator<DSL>`. For details, see [xlang.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/dev-guide/xlang/xlang.md).

![](images/xlang.png)

- The Xpl template language supports a tag-library mechanism similar to Vue components; tag libraries are described by the Xlib DSL.

- The Xpl template language can invoke the XScript scripting language via the built-in `<c:script>` tag.

- XScript has a syntax similar to TypeScript; its parser is generated using Antlr.

- Nop extends Antlr to automatically generate AST parsers, not just the built-in ParseTree parser. See [How Antlr4 can automatically parse to AST instead of ParseTree](https://zhuanlan.zhihu.com/p/534178264).

- Generating XLangASTParser requires using XCodeGenerator, and that in turn uses XScript. In other words, generating the XScript parser requires using XScript syntax, which introduces a circular dependency. The Nop platform initially hand-wrote XLangASTParser and XLangASTBuildVisitor to complete the most basic code generator, then gradually switched to auto-generating XLangASTParser and XLangASTBuildVisitor.

- **All XML-format files in the Nop platform use xdef meta-models to define their structure, collectively known as XDSL.** For example, the xpl template language corresponds to xpl.xdef, and the tag library xlib corresponds to xlib.xdef.

- The XDef meta-model itself is constrained by `xdef.xdef`. However, in implementation, XDefinitionParser for parsing XDef files is hand-written rather than auto-parsed, otherwise parsing XDef would first require parsing `xdef.xdef`, causing a cycle.

- Although Xpl uses XML syntax, the AST obtained after parsing is consistent with XScript—both are XLangAST.

  ```xml
  <c:unit>
     <c:for var="x" items="${list}">
        ...
     </c:for>
     <c:script>
        for(let x of list){
           // ...
        }
     </c:script>
  </c:unit>
  ```

  The `<c:for>` tag above and the for statement inside `<c:script>` both parse to a ForOfStatement.

## 6. Unified Structural Construction Laws Behind DSLs

Nop is not merely a concrete development framework—**it is a new way of thinking**. Nop’s approach to problem-solving is to first define a DSL that establishes a local descriptive framework, akin to building strongholds across the vast model space. Then, by establishing automatic conversion paths between different models, fully automated inference enables free traversal across this model space. In traditional programming approaches, each structure requires hand-written code to establish connections. In the world of Nop, by viewing everything from the higher level of meta-models, we discover that behind the myriad DSLs lie unified laws of decomposition, merging, and transformation. We can write unified logic against meta-models, which then automatically adapts to each specific model object.

Existing low-code platforms try to build some ad-hoc development models and then think about how to improve visual editors so users can more easily configure models using the editor. Nop contemplates a completely different set of questions: what are the construction laws behind all models, how can we automatically infer visual designers, and how can we automatically infer free conversions between models?

<!-- SOURCE_MD5:7da6163702ff8ac473efac24fc077b92-->
