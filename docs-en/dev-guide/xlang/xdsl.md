# XDSL: A General Domain-Specific Language Design

The Nop platform provides a programming paradigm oriented toward language-based programming. When solving problems, we tend to first design a domain-specific language (DSL) tailored to the specific domain and then use that DSL to describe the business logic in detail. The Nop platform significantly simplifies the process of creating custom DSLs.

## 1. Using XML or JSON Syntax

The value of a domain-specific language lies in its ability to distill domain-specific logic, defining atomic semantic concepts unique to the domain, while abstracting away specifics of the syntax. The actual syntax form is not as critical as the semantic concepts it represents. After passing through a `Lexer` and `Parser`, program code is converted into an Abstract Syntax Tree (AST), which carries all semantic principles in the AST. Both XML and JSON are tree structures and can directly express the AST, thus eliminating the need to write specialized `Lexer` and `Parser`.

> Lisp's approach is to use the general `S-Expr` to represent the AST, making it easy to define custom DSLs using macro mechanisms. With XML syntax, we can achieve similar results, especially since XML tags can represent template functions that dynamically generate new XML nodes, mimicking Lisp macros (both the code and its generated structure are XML nodes, corresponding to [Lisp's concept of similarity](https://zhuanlan.zhihu.com/p/34063805)).

We use XDef meta-modeling language to constrain the syntax structure of the DSL, such as `[beans.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/beans.xdef)`. Compared to XML Schema or JSON Schema, XDef is simpler and more intuitive while allowing for more complex constraints. For details on the XDef language, refer to `[xdef.md](xdef.md)`.

> All DSLs in the Nop platform are defined using the XDef language, including workflows, reports, IoC, ORM, etc., with definition files unified in the `[nop-xdefs module](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-xdefs/src/main/resources/_vfs/nop/schema)`.

XDef not only defines XML syntax for DSL but also establishes rules for bidirectional conversion between XML and JSON. Therefore, defining an XDef meta-model allows automatic generation of JSON representations and facilitates input/output in front-end visualization editors.

Without an XDef meta-model, the Nop platform also provides a compact convention-based transformation rule to enable XML and JSON conversion even without Schema constraints. For details, refer to the front-end AMIS page's XML representation: `[amis.md](../xui/amis.md)`.

## 2. General XDSL Syntax

After normalizing all DSLs into XML format, we can uniformly provide mechanisms for module decomposition, difference merging, and meta-programming, among other advanced features. The Nop platform defines a unified XDSL extension syntax, automatically extending the syntax for all DSLs defined using the XDef meta-model. The specific content of the XDSL syntax is defined by `[xdsl.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/beans.xdef)`.

XDSL's key elements are defined by its primary meta-model, such as:

```
<list>
  <item>Item 1</item>
  <item>Item 2</item>
</list>
```

```xml
<orm x:schema="/nop/schema/orm/orm.xdef"
     x:extends="base.orm.xml" x:dump="true"
     xmlns:x="/nop/schema/xdsl.xdef" xmlns:xpl="/nop/schema/xpl.xdef">
    <x:gen-extends>
        <pdman:GenOrm src="test.pdma.json" xpl:lib="/nop/orm/xlib/pdman.xlib"
                      versionCol="REVISION"
                      createrCol="CREATED_BY" createTimeCol="CREATED_TIME"
                      updaterCol="UPDATED_BY" updateTimeCol="UPDATED_TIME"
                      tenantCol="TENANT_ID"
        />
    </x:gen-extends>

    <x:post-extends>
        <orm-gen:JsonComponentSupport xpl:lib="/nop/orm/xlib/orm-gen.xlib"/>
    </x:post-extends>

    <entities>
        <entity name="io.nop.app.SimsClassFee" x:override="remove"/>
    </entities>
</orm>
```

1. All XDSL files require the root node to use the `x:schema` attribute to specify the used xdef definition file.
2. The root node can set the `x:dump="true"` attribute to print the differences in the merge process, including intermediate and final results. In Quarkus' debug mode, these results are output to the project's `_dump` directory.
3. The `x:extends` attribute introduces the base model being inherited. The current model and base model will be merged hierarchically.
4. `x:gen-extends` and `x:post-extends` provide built-in meta-programming mechanisms. They allow dynamically generating model objects, which are then merged with the current model.
5. The `x:override` attribute controls details during node merging. For example:
   - `x:override="remove"` removes the base model's corresponding node.
   - `x:override="replace"` replaces the base model's corresponding node with the current node.
   - By default, `x:override="merge"` merges nodes level by level, performing a deep merge of child nodes.

### Merge Order of x-extends

The `x-extends` mechanism implements reversible computing theory requirements through a delta merging approach:

> App = Delta x-extends Generator<DSL>

Specifically:
- `x:gen-extends` and `x:post-extends` are compiled-time Generators<DSL>.
- They use XPL template language to dynamically generate model nodes, allowing for bulk generation followed by sequential merging based on the defined order.

The merging process is as follows:

```xml
<model x:extends="A,B">
    <x:gen-extends>
        <C/>
        <D/>
    </x:gen-extends>

    <x:post-extends>
        <E/>
        <F/>
    </x:post-extends>
</model>
```

The final merged result is:

```xml
<F x-extends=E x-extends=D x-extends=C x-extends=B x-extends=A>
```

The current model will override the results of `x:gen-extends` and `x:extends`. Conversely, `x:post-extends` overrides the current model.

Using `x:extends` and `x:gen-extends`, we can effectively implement the decomposition and combination of DSL.

### Significance of x:post-extends

If you've already created an XDSL domain-specific language and want to introduce additional extensions for specific scenarios without modifying the underlying runtime engine, `x:post-extends` is your tool.

Based on reversible computing theory, for existing DSLs, we can further decompose them into a new DSLx.

```java
App = Delta x-extends Generator<DSL>
DSL = Delta x-extends Generator<DSLx>
```

When describing business logic, we can utilize the DSLx extended syntax. This extended syntax can then be converted into existing DSL syntax using the `x:post-extends` mechanism. **The `x-extends` merging algorithm will automatically remove all namespace-related properties and child nodes after execution**, so the underlying parsing and runtime engines need not know anything about these extended syntaxes. They only need to handle the original DSL semantic concepts, as the extension mechanisms are abstracted away by the `x:post-extends` mechanism.

For example, in an ORM engine, for a JSON text field, we want it to correspond to two entity properties: one for storing raw JSON text (`jsonText`) and another for parsing JSON text into structured objects (`jsonComponent`). By adding a `json` tag to the field, we can mark it as containing JSON data. This will automatically generate corresponding `component` properties. This is a special convention that we do not want to bake into the ORM engine itself. Instead, we can utilize the `x:post-extends` mechanism to abstract this behavior.

```xml
<orm x:schema="/nop/schema/orm/orm.xdef"
     x:extends="base.orm.xml">
    <x:post-extends>
        <orm-gen:JsonComponentSupport xpl:lib="/nop/orm/xlib/orm-gen.xlib"/>
    </x:post-extends>
    <entities>
      <entity name="xxx.MyEntity">
        <columns>
          <column name="jsonExt" code="JSON_EXT" propId="101" tagSet="json"
                  stdSqlType="VARCHAR"
                  precision="4000"/>
        </columns>
        <!-- Automatically generated component configurations -->
        <components>
          <component name="jsonExtComponent"
                     class="io.nop.orm.component.JsonOrmComponent">
            <prop name="jsonText" column="jsonExt"/>
          </component>
        </components>
      </entity>
    </entities>
</orm>
```

If we have many custom extensions, they can be further encapsulated into a base model, such as:

```xml
<!-- std.orm.xml -->
<orm x:schema="/nop/schema/orm/orm.xdef"
     x:extends="base.orm.xml">
    <x:post-extends>
        <orm-gen:JsonComponentSupport xpl:lib="/nop/orm/xlib/orm-gen.xlib"/>
    </x:post-extends>
</orm>
<!-- my.orm.xml -->
<orm x:extends="std.orm.xml">
    ....
</orm>
```

Common extensions can be encapsulated into a `std.pom.xml` model, and existing models can simply inherit from this base model by specifying `x:extends="std.orm.xml"`.

> The `x:extends` mechanism supports multiple schema paths separated by commas. This allows for inheriting multiple base models in a single declaration.

Furthermore, **the `x:post-extends` mechanism enables custom visualization through specialized designer tools**. The `x-extends` merging algorithm can be configured to merge at specific stages (e.g., `mergeBase`). If we only merge at the `mergeBase` stage, we will get the current model merged with the result of `x:gen-extends`, but `x:post-extends` will not have been applied yet. Visualization tools can then be designed to work directly with the `mergeBase` output, while still allowing for abstracted extensions through `x:post-extends`.


In the Nop platform, OA approval is typically implemented using the `x:post-extends` mechanism. The underlying workflow engine is designed for general scenarios. Since the join functionality can be achieved by combining a simple step node with a Join merge node, there is no need to embed join-related knowledge into the lower-level engine. In the workflow designer, we provide various OA-related simplifications, and in the meta-programming phase, the `x:post-extends` mechanism is responsible for expanding these OA-related configurations into model nodes and properties that the lower-level engine can recognize.


### Executable Semantics

In XDSL, executable semantics are implemented using the XLang language. If an attribute is marked as an EL expression in the xdef meta-model or if a node's content is set to XPL template language, then this attribute will be automatically resolved into an `IEvalAction` executable function interface. Specific examples can be found in `[wf.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/wf/wf.xdef)`.

```xml
<action name="!string">
    <when xdef:value="xpl-predicate"/>

    <arg name="!var-name" xdef:ref="WfArgVarModel" xdef:unique-attr="name"/>

    <source xdef:value="xpl"/>
</action>
```

The Nop platform provides various features such as document hints, auto-completion, syntax checking, and debug points through the `nop-idea-plugin` plugin for the XLang language. For more details, refer to `[idea-plugin.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/user-guide/idea/idea-plugin.md)`.

![Nop Workflow Executor UI](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/xlang-debugger.png)


### Beyond Interfaces and Components: Delta Customization

Based on reversible computing theory, the Nop platform's XDSL layer incorporates a generic Delta customization mechanism. Compared to traditional interface abstraction and component assembly methods, this approach is more straightforward and flexible.

All XDSL model files are stored in the `src/resources/_vfs` directory, forming a virtual file system. This virtual file system supports Delta layered merging (similar to Docker's `overlay-fs`), with a default layered path of `/_delta/default`. If both `/_vfs/_delta/default/nop/app.orm.xml` and `/nop/app.orm.xml` exist, the Delta directory version is used. In Delta customization files, you can inherit the specified base model using `x:extends="raw:/nop/app.orm.xml"` or `x:extends="super"` to refer to the parent model.

Delta customization is highly flexible, both in granularity and depth. From a high level, it allows customization of the entire model file. At a fine level, individual attributes or nodes can be customized. Unlike interface customization, Delta customization supports **deletion** functionality, allowing specific parts of the model to be marked for deletion in the customization file, ensuring true deletion without impacting runtime performance.

Compared to traditional programming language customization mechanisms, **Delta customization rules are highly generic and intuitive**, making them independent of specific implementation details. For example, when extending Hibernate's built-in `MySQLDialect`, you need to understand Hibernate's framework if using Spring integration. In Nop, however, you only need to add the `/_vfs/default/nop/dao/dialect/mysql.dialect.xml` file to ensure that all MySQL-related usages are updated to use the new Dialect model.

Here is the translated English version of the provided Chinese technical documentation, maintaining the original Markdown format, including headers, lists, and code blocks.

1. **Delta Custom Code**
   - Delta custom code is stored in a separate directory and can be kept independent from the main application code.
   - For example, delta customization files should be packaged into the `nop-platform-delta` module. When this module is required, it can automatically load the corresponding module without any additional setup.
   - Multiple delta directories can also be imported simultaneously. The order of delta layers can be controlled using the `nop.core.vfs.delta-layer-ids` parameter. For instance, a configuration like `nop.core.vfs.delta-layer-ids=base,hunan` enables two delta layers: the base layer and the Hunan-specific layer.
   - This approach allows for low-cost productization: while maintaining functionality in the core product, only delta customization is added without modifying the original code. **A well-established core product can be extended with minimal changes by simply adding Delta custom code**.

2. **Antlr Extensions**
   - The Nop platform provides support for custom program syntax DSL development to some extent.
   - Based on Antlr4's g4 files, it is possible to directly generate an AST parser (Antlr itself only supports parsing up to `ParseTree`, requiring manual conversion from `ParseTree` to AST).
   - For more details, refer to [antlr.md](antlr.md).

3. **Support for Xdsl**
   - XML/JSON can be automatically converted into XDSL.
   - For example, the frontend AMIS framework uses JSON format inherently. Without any modifications to the AMIS engine, the DeltaJsonLoader can be used to introduce a reversible decomposition and merging mechanism for AMIS.

### Example of Xdsl Configuration
```json
{
  "x:extends": "Extends existing AMIS files for decomposition, such as page decomposition in AMIS which lacks built-in decomposition capabilities",
  "title": "Page Decomposition through Delta Customization",
  "x:override": "Merge operations are handled using `merge` methods like `/api/merge/bounded-merge`, and can be customized via `x:override`.",
  "x:gen-extends": "Dynamic generation of xpl templates for object structure construction is possible here",
  "feature:on": "Node existence check is triggered by a feature being enabled; non-existing nodes are automatically deleted"
}
```

The Xdsl configuration uses specific syntax properties, which can be further explored in the `xdsl.xdef` model definition at [https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef).

For override mechanisms, refer to [x-override.md](x-override.md).<br>
