# XDSL: General-Purpose Domain-Specific Language Design

The Nop platform offers a language-oriented programming paradigm: when solving problems, we tend to first design a Domain-Specific Language (DSL), and then use that DSL to describe business logic. The Nop platform greatly simplifies the process of creating custom DSLs.

## I. Using XML or JSON Syntax

The value of a DSL lies in distilling domain-specific logical relationships and defining atomic semantic concepts unique to that domain. The concrete syntax is not the key. After code is parsed by a Lexer and Parser, it yields an Abstract Syntax Tree (AST), and all program semantics are, in principle, carried by the AST. Both XML and JSON are tree structures and can directly represent an AST, thus completely avoiding the need to implement a special Lexer and Parser.

> Lisp does exactly this by directly using a general S-Expr to represent the AST, making it easy to define custom DSLs via macros. A similar effect can be achieved based on XML syntax, especially since XML tags can represent template functions that dynamically generate new XML nodes, serving a role similar to Lisp macros (both the code and the generated result are XML nodes, corresponding to what Lisp calls homoiconicity: https://zhuanlan.zhihu.com/p/34063805).

We use the XDef meta-model definition language to constrain the syntax structure of a DSL, for example [beans.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/beans.xdef). Compared with XML Schema or JSON Schema, XDef definitions are simpler and more intuitive, while expressing more complex constraints. For details on the XDef language, see [xdef.md](xdef.md)

> All DSLs in the Nop platform are defined using the XDef language, including workflow, reporting, IoC, ORM, etc. The definition files are uniformly stored in the [nop-xdefs module](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema).

![](xml-to-json.png)

XDef not only defines the XML-format DSL syntax, but also specifies a bidirectional conversion rule between XML and JSON. Therefore, once an XDef meta-model is defined, a JSON representation is obtained automatically and can be used directly as the input/output of a front-end visual editor.

In the absence of an XDef meta-model, the Nop platform also defines a compact, convention-based conversion rule that enables bidirectional conversion between XML and JSON without schema constraints. See the XML representation of front-end AMIS pages: [amis.md](../xui/amis.md)

## II. XDSL Common Syntax

After unifying all DSLs to XML format, we can uniformly provide advanced mechanisms such as module decomposition, Delta merging, and metaprogramming. The Nop platform defines a unified XDSL extension syntax that automatically adds Reversible Computation extension syntax to all DSLs defined via XDef meta-models. The specific XDSL syntax is defined by the meta-model [xdsl.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/beans.xdef).

The main syntactic elements of XDSL are exemplified as follows:

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

1. All XDSL files require the root node to specify the xdef definition file via the x:schema attribute.
2. On the root node, set x:dump="true" to print intermediate results of the Delta merge as well as the final merged result. In the Quarkus framework’s debug mode, the final merged result will be output to the current project’s \_dump directory.
3. The x:extends attribute introduces the base model being inherited; the current model and the base model will be merged hierarchically along the tree structure.
4. x:gen-extends and x:post-extends provide built-in metaprogramming mechanisms. They can dynamically generate model objects and then merge them with the current model.
5. The x:override attribute controls the details of merging two nodes. For example, x:override="remove" means to delete the corresponding node from the base model, while x:override="replace" means the current node fully overrides the corresponding node in the base model. By default, x:override="merge", which means child nodes are merged hierarchically. For a detailed introduction to the merge rules, see [x-override.md](x-override.md)

### Merge Order of x-extends

The x-extends Delta merge mechanism implements the technical pattern required by the theory of Reversible Computation:

> App = Delta x-extends Generator<DSL>

Specifically, x:gen-extends and x:post-extends are compile-time Generators. They use the XPL template language to dynamically generate model nodes, allowing multiple nodes to be generated at once and merged sequentially. The merge order is defined as follows:

```
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

The merge result is

```
F x-extends E x-extends model x-extends D x-extends C x-extends B x-extends A
```

The current model overrides the results of x:gen-extends and x:extends, while x:post-extends overrides the current model.

By leveraging x:extends and x:gen-extends we can effectively achieve the decomposition and composition of DSLs.

### The Significance of x:post-extends

If we have created an XDSL for a domain and now want to introduce additional extensions for special scenarios without modifying the underlying runtime engine, we can use x:post-extends.

Based on the theory of Reversible Computation, for an existing DSL, we can further perform a reversible decomposition to obtain a new DSLx.

```java
App = Delta x-extends Generator<DSL>
DSL = Delta x-extends Generator<DSLx>
```

When describing business logic, we can use the extended DSLx syntax and then transform it into the existing DSL syntax via x:post-extends. After the x-extends merge algorithm finishes, it will automatically remove all attributes and child nodes in the x namespace, so the lowest-level parsing and runtime engines do not need to know anything about these extension syntaxes. They only need to be written for the original DSL semantic concepts, while all general extension mechanisms are implemented at the XDSL syntax layer via compile-time metaprogramming.

Here is a concrete example. In the ORM engine, for a JSON text field we want it to correspond to two entity properties: one jsonText that stores the JSON text, and another jsonComponent that maps the JSON text to an object structure. Modifying the object properties would ultimately modify the jsonText stored text. We want to mark a field as JSON by adding a json tag, and then automatically generate the corresponding component property for that field. This is a special convention that we do not want to hardcode into the ORM engine. In this case, we can implement this abstraction using x:post-extends.

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
        <!-- Will eventually auto-generate the component configuration
           <components>
              <component name="jsonExtComponent"
                         class="io.nop.orm.component.JsonOrmComponent">
                 <prop name="jsonText" column="jsonExt" />
              </component>
           </components>
         -->
      </entity>
    </entities>
</orm>
```

If we have many custom extensions, we can further encapsulate them into a base model, for example:

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

Common extensions can be encapsulated into a std.pom.xml model, and then you only need to inherit this model to obtain the corresponding extension support.

> x:extends supports multiple comma-separated model paths, allowing you to inherit multiple base models at once. These models are merged in sequence from front to back.

Furthermore, x:post-extends paves the way for building customized visual designers. When executing the x-extends merge algorithm, you can specify the merge stage. If you only merge to the mergeBase stage, you obtain the result of merging the current model with x:gen-extends, but x:post-extends has not yet been applied. A visual designer can target the output at the mergeBase stage, providing abundant business-specific configuration options, while the underlying runtime engine requires no changes.

In the Nop platform, countersignature nodes commonly seen in OA approvals are implemented using x:post-extends. The underlying workflow engine is designed for general-purpose scenarios. Since the countersignature function can be achieved via “a normal step node + a Join merge node,” there is no need to embed countersignature knowledge into the engine. In the workflow designer, we provide countersignature nodes and a wealth of OA-related simplified operations, and at the metaprogramming stage, the x:post-extends mechanism expands these OA-related configurations into model nodes and attributes recognizable by the underlying engine.

### Executable Semantics

XDSL uses the XLang language to implement executable semantics. As long as an attribute is annotated as an EL expression in the xdef meta-model, or a node’s content is annotated as the XPL template language, the attribute will be automatically parsed into the IEvalAction executable function interface. For a specific example, see [wf.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/wf/wf.xdef)

```xml
 <action name="!string" >
    <when xdef:value="xpl-predicate"/>

    <arg name="!var-name" xdef:ref="WfArgVarModel" xdef:unique-attr="name"/>

    <source xdef:value="xpl"/>
 </action>
```

The Nop platform provides documentation hints, auto-completion, syntax checking, and breakpoint debugging for the XLang language via the nop-idea-plugin. See [idea-plugin.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/user-guide/idea/idea-plugin.md)

![](../../user-guide/idea/idea-executor.png)

![](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/xlang-debugger.png)

### Delta Customization Beyond Interfaces and Components

Based on the theory of Reversible Computation, XDSL in the Nop platform has a built-in, general Delta customization mechanism that is simpler and more flexible than traditional approaches using interface abstraction and component assembly.

All XDSL model files are stored in the src/resources/_vfs directory, forming a virtual file system. This virtual file system supports the concept of Delta layered overlays (similar to the overlay-fs layered file system in Docker). By default it has a layer /_delta/default (more layers can be added via configuration). In other words, if both /_vfs/_delta/default/nop/app.orm.xml and /nop/app.orm.xml exist, the version under the delta directory is used. In a delta customization file, you can inherit a specified base model with x:extends="raw:/nop/app.orm.xml", or inherit the base model of the upper layer with x:extends="super".

Delta customization is extremely flexible, with adjustable granularity. It can be as coarse as customizing an entire model file, or as fine as customizing a single attribute or node. Unlike interface-based customization, Delta customization supports deletion—that is, the customization file can mark a portion of a model for deletion, and it is truly removed rather than simulated with a no-op, which does not impact runtime performance.

Compared with customization mechanisms provided by traditional programming languages, Delta customization rules are highly general and intuitive, and independent of specific application implementations. Take database Dialect customization used by the ORM engine as an example: if you want to extend Hibernate’s built-in MySQLDialect, you need some knowledge of the Hibernate framework, and if Spring integration is used, you also need to understand how Spring wraps Hibernate and where to find and configure the Dialect for the current SessionFactory. In the Nop platform, you only need to add the file /_vfs/default/nop/dao/dialect/mysql.dialect.xml to ensure that all places using the MySQL dialect are updated to use the new Dialect model.

Delta customization code is stored in a separate directory and can be separated from the main application code. For example, you can package delta customization files into a module named nop-platform-delta; to use this customization, simply import the corresponding module. You can also introduce multiple delta directories and control the order of delta layers via the nop.core.vfs.delta-layer-ids parameter. For example, configuring nop.core.vfs.delta-layer-ids=base,hunan enables two delta layers: a base product layer with a specific deployment’s delta layer on top. In this way, we can productize software at extremely low cost: a functionally complete base product can be implemented at different customers without altering the base product’s code, only by adding Delta customization code.

### III. Antlr Extensions

The Nop platform also provides support for DSLs with custom program syntaxes. Based on Antlr4 g4 definitions, it can directly generate an AST parser (Antlr natively only supports parsing to a ParseTree, and you would otherwise need to write the transformation from ParseTree to AST manually). See [antlr.md](antlr.md)

## Add XDSL Support on Top of Any XML/JSON
Any XML or JSON can be automatically adapted into XDSL. For example, since the front-end AMIS framework uses JSON, we do not need to modify the AMIS engine. By using a unified DeltaJsonLoader, we can introduce reversible decomposition-merge mechanisms for AMIS.

```json
{
  "x:extends": "Inherit other existing AMIS files to achieve page decomposition; AMIS does not have a built-in decomposition mechanism",
  "title": "On top of the inherited page, you can customize and adjust via Delta",
  "x:override": "The default merge operation is merge; you can change it to remove/replace/bounded-merge via x:override",
  "x:gen-extends": "You can write XPL template code here to dynamically generate base object structures",
  "feature:on": "This feature expression must return true for this node to exist; otherwise the node is automatically removed"
}
```

For which syntactic attributes XDSL uses specifically, see the [xdsl.xdef meta-model definition](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef).
For an introduction to the x:override merge operator, see [x-override.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs-en/dev-guide/xlang/x-override.md)
<!-- SOURCE_MD5:39fa9181a168b1caca0aedc675674d99-->
