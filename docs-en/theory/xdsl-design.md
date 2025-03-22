# From Reversible Computation to DSL Design Points

The visualization editor for low-code platforms is essentially a structured editor for Domain Specific Language (DSL). When the results of the editing process are serialized into text format, the standard used is a specific syntax defined by the DSL.

The Nop platform is based on the principles of reversible computation and provides a comprehensive mechanism for constructing and simplifying DSL design and implementation. This allows for easy addition of custom DSLs tailored to specific business domains, as well as seamless extension of existing DSLs. Specifically, the DSLs defined within the Nop platform typically use XML syntax, conforming to XDSL standards.

## 1. DSL Before Visualization

Many low-code platforms prioritize the simplicity and usability of their visualization tools, leading to inconsistent and overly complex DSL design. In contrast, XDSL emphasizes a concise and intuitive text-based DSL for programmers, combining manual editing with automated processing. Visualization alone can be seen as a form of text-based DSL, separate from but complementary to programmatic DSLs.

Under this design philosophy, a single DSL can support multiple visualization tools, such as NopORM's DSL (app.orm.xml), which can be visualized using tools like Excel, PowerDesigner, or PDMiner. Additional visualization tools can be added as long as their design files enable bidirectional conversion with orm.xml.

In specific business applications, custom visualization tools, such as localized detail editors for specific model parts, can be implemented. These editors use difference merge operations to integrate localized designs into the overall model.

## 2. DSL Syntax Defined by Meta-Models

The value of DSL lies in its abstraction of domain semantics, abstracted further through meta-models. The choice of syntax is secondary; what matters is that a unified XDefinition meta-language can standardize DSL syntax for specific domains.

A meta-model is essentially a model of models, akin to metadata describing data. Just as metadata structures data, a meta-model defines the structure of models.

### Example Meta-Model Structure

```xml
<orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl/xdsl.xdef">
    ...
</orm>
```

- The root element represents the model file.
- `x:schema` specifies the meta-model definition.
- `[orm.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/orm.xdef)` is the meta-model defined using `[xdef.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xdef/xdsl.xdef)`.
- This meta-model defines the structure of the DSL syntax.

### Defining Meta-Models

The meta-model is defined by `[orm.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/orm.xdef)` and `[xdef.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xdef/xdsl.xdef)`, both of which are defined using `[schema.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/schema/xdsl.xdef)`.

These meta-models ensure that all DSLs share the same definition language, enabling seamless nesting and integration of different DSLs within the Nop platform.

### Unified Meta-Language for DSLs

In the Nop platform, numerous DSL meta-models are defined, such as `[api.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/api/xdsl.xdef)` and `[xmeta.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xmeta/xdsl.xdef)`, which in turn reference already defined meta-models like `[schema.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/schema/xdsl.xdef)`.

This mutual referencing ensures that all DSLs share a consistent definition framework, enabling bidirectional conversion and seamless integration across different domains.

### Benefits of Unified Meta-Language

The power of a unified meta-language lies in its ability to abstract domain-specific complexities into a standard structure. This abstraction allows for:
- Consistent definition of DSL syntax across domains.
- Automatic generation of tools tailored to specific domains.
- Easy extension and customization of existing DSLs.

This approach minimizes the need for custom code, reducing development complexity while maintaining flexibility.

## 3. Custom Visualization Tools

In addition to standard visualization tools like Excel or PowerDesigner, custom visualization tools can be developed for specific use cases. For example, a localized editor for model details might focus on a subset of the model, using difference merge operations to synchronize changes across localized versions.

Such tools are not limited to XML-based representations; they can utilize other formats as long as they support bidirectional conversion with the central meta-model.

### Example of Custom Visualization

A custom visualization tool for a specific business domain might use XSLT transformations to convert the underlying XML representation into a more user-friendly format, while maintaining referential integrity through difference tracking.

This approach ensures that changes in one localized model are reflected across all connected models, minimizing data fragmentation and ensuring consistency.

### Integration with Existing Systems

Existing systems can be integrated by defining appropriate mappings between their data models and the Nop platform's meta-model. This allows for seamless data exchange while preserving the integrity of both systems.

## 4. Conclusion

The design of DSLs is a critical aspect of platform development, particularly in low-code environments where flexibility and extensibility are key. By focusing on meta-models and unified definition languages like XDSL, platforms can achieve greater consistency, reduce development complexity, and provide users with powerful tools for domain-specific modeling.


The Nop platform provides an IDEA plugin called **[nop-idea-plugin](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-idea-plugin)**. This plugin automatically validates the syntax of your DSL based on the schema defined by `x:schema`, and it provides auto-completion, link navigation, and debugging capabilities for function types of DSL nodes. You don't need to develop a separate IDEA plugin for each new DSL language; instead, you can directly obtain IDEA support.

Based on the meta-model, we can automatically derive a visualization designer. You don't need to manually import a visualization designer for each individual DSL.


## Three. All DSLs Need to Provide Decomposition and Merging Mechanisms

When a DSL file becomes sufficiently complex, it is necessary to introduce decomposition, merging, and library abstraction mechanisms to manage complexity. XDSL defines a standardized Delta difference syntax, as specified in **[xdsl.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef)**.


## Three. Decomposition and Merging Mechanisms for All DSLs

For a complex DSL file, decomposition, merging, and library abstraction mechanisms are essential to manage complexity. XDSL defines a standardized Delta difference syntax, as specified in **[xdsl.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef)**.

```xml
<meta x:extends="_NopAuthUser.xmeta"
      x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef" >

    <x:post-extends>
        <biz-gen:GenDictLabelFields xpl:lib="/nop/core/xlib/biz-gen.xlib"/>
    </x:post-extends>
</meta>
```

The `x:extends` attribute is used to inherit existing meta-models, while `x:gen-extends` and `x:post-extends` are built-in meta-programming mechanisms. These are used to implement the Generator part of Reversible Computing Theory, dynamically generating DSL model objects, and then performing Delta merging.

The `x:override` attribute specifies the merging strategy when nodes are merged. For detailed information, refer to **[Delta Merging Algorithm in Reversible Computing](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/dev-guide/xlang/x-override.md)**.


## Four. Managing All DSL Files with a Delta File System

The Nop platform consolidates all model files into a unified virtual file system for management. This virtual file system resembles the UnionFS file system used in Docker, organizing files into layers. Higher-level directories automatically override lower-level files with the same virtual path.

For example:
- `_/vfs/_delta/default/a.xml` automatically overrides `_/vfs/a.xml`.
- In code, any reference to `/a.xml` will load `_/vfs/_delta/default/a.xml` at runtime.
- You don't need to modify existing source code; simply add files to the delta directory.

Key configuration options include:
- `nop.core.vfs.delta-layer-ids`: Specify multiple Delta layers (default is one).
- In the delta directory, XDSL files can use `x:extends="super"` to inherit from the previous layer's model file.
- Map database table files to virtual paths, such as `wf:MyWf/1.0` for files stored in the NopWfDefinition table.

With the help of the Delta File System and XDSL's built-in Delta merging algorithm, you can implement system-level Delta customization without modifying the base product source code. For example, see **[How to Implement Customization Without Modifying Base Product Source Code](https://zhuanlan.zhihu.com/p/628770810)**.



The Nop platform uses `ResourceComponentManager` to load all DSL models uniformly.

```java
OrmModel model = (OrmModel) ResourceComponentManager.instance().loadComponentModel("/nop/auth/orm/app.orm.xml");
```

When adding a new DSL model, you can create a registration file, such as `orm.register-model.xml`.

```xml
<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       name="orm">
    <loaders>
        <xlsx-loader fileType="orm.xlsx" impPath="/nop/orm/imp/orm.imp.xml"/>
        <xdsl-loader fileType="orm.xml" schemaPath="/nop/schema/orm/orm.xdef"/>
    </loaders>
</model>
```

Through this registration of the model, we can specify how to perform the resolution based on the given file type.

* **xlsx-loader**: Specifies how to import model configurations from Excel files.
* **xdsl-loader**: Specifies how to resolve XML-based DSL files and their corresponding meta-models. The schemaPath must match the schema defined in the xdef file or be extended from it.

Based on a unified loader, we can implement code generation tools for any given model.

```bash
java -jar nop-cli.jar gen abc.model.xlsx -t=/nop/templats/my-model
```

The `gen` command accepts a model file parameter and uses the `-t` parameter to specify the template path. This allows automatic resolution of the model and injection into the template file. For detailed documentation, refer to [The Differential Code Generator](https://zhuanlan.zhihu.com/p/540022264).

### Parsing Cache and Dependency Tracking

The `ResourceComponentManager` internally manages all DSL models' parse caches as well as their interdependencies. Its dependency tracking mechanism is similar to that used by the Vue frontend framework, dynamically recording all DSL models loaded or used during resolution, with any changes to model files automatically invalidating all dependent caches.

Additionally, the **nop-cli** tool provides a `watch` feature, enabling monitoring of specific directories for model file changes. When a change is detected, the code generator automatically re-runs to generate derived code based on the updated model.

### Reverse Calculation Entry Point

The core implementation of reverse calculation is encapsulated within the `ResourceComponentManager`. To integrate reversible calculations into third-party applications, simply replace your model loading function with `ResourceComponentManager.loadComponentModel`.

For example, to implement custom Delta functionality for Spring and MyBatis frameworks, we redefined the scanning logic for `beans.xml` and `mapper.xml`, using `ResourceComponentManager` to dynamically generate DOM objects and then registering them with Spring and MyBatis parsers.

For theoretical analysis, refer to [The Design of Low-Code Platforms](https://zhuanlan.zhihu.com/p/531474176).

## All DSL Models Support Extension

XDSL models are not fixed in their properties during development. They generally inherit from the `AbstractComponentModel` base class and support arbitrary extensions. In specific business applications, you can choose to inherit from existing meta-models and add business-specific extensions.

For example, the platform includes a built-in [xmeta.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xmeta.xdef) meta-model. You can define `xmeta-ext.xdef` to extend this meta-model by adding custom extension fields.

```xml
<meta x:extends="/nop/schema/xmeta.xdef" xmlns:ui="ui" xmlns:graphql="graphql"
      x:schema="/nop/schema/xdef.xdef"
      xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef">

    <props>
        <prop ui:show="true" graphql:type="true"/>
    </props>

</meta>
```

This meta-model defines `ui:show` and `graphql:type` properties for the `prop` nodes.

In specific `meta` files, you can replace `xmeta.xdef` with `xmeta-ext.xdef` to override or extend the default meta-model.
```xml
<meta x:schema="/my/schema/xmeta-ext.xdef">...</meta>
```

* IDEA plugin will automatically identify and use an extended meta-model definition to validate Meta files.
* The ResourceComponentManager.loadComponentModel method loads model objects that include extended properties.

In other words, without modifying the built-in meta-model definitions, we can extend existing model objects at any time by adding extended properties, just like built-in properties are used in programming.  
