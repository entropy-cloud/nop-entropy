
# DSL Design Essentials from the Perspective of Reversible Computation

Low-code platforms’ visual designers are essentially structured editors for a DSL (Domain Specific Language). The specification used by a visual designer to serialize editing results into a textual format is a DSL grammar definition.

Based on the principles of Reversible Computation, the Nop platform proposes a systematic construction mechanism to simplify the design and implementation of DSLs, making it easy to add DSLs for your own business domain and to extend existing DSLs. Specifically, DSLs defined in the Nop platform generally adopt an XML syntax format and conform to the so-called XDSL specification requirements. The key design points of XDSL are as follows:

## I. DSL-first rather than visual-design-first

Many low-code platforms focus on making the visual designer easy to use, which leads to DSL formats that are arbitrary, cluttered, and verbose, and not suitable for programmers to read or write manually. XDSL emphasizes that the textual form of the DSL should be concise and intuitive, suitable for manual authoring and easy for automated processing. The visual presentation can be regarded as another representation of the textual DSL; the visual and textual forms can be reversibly transformed according to standardized rules.

Under this design philosophy, the same DSL can have multiple visual designers. For example, the DSL corresponding to the NopORM model is the model file app.orm.xml, and its visual designers can be Excel, PowerDesigner, or PDMiner. We can add more visual designers as long as their design files can be bidirectionally converted with orm.xml model files.

In specific business applications, we can introduce customized visual designers—for instance, a local detail designer that designs only part of the model file—and then merge the local design results into the overall model via Delta merge operations.

## II. Define DSL syntax via a metamodel, and let all DSLs share the same metamodel definition language.

The value of a DSL lies in the domain semantic space it abstracts with business value; what syntax form it adopts is essentially secondary. XDSL uniformly adopts an XML syntax form, which allows the introduction of a unified XDefinition metamodel language to standardize the specific DSL grammar.

> A metamodel is a model that describes models, similar to how metadata describes data.

```xml
<orm x:schema="/nop/schema/orm/orm.xdef"  xmlns:x="/nop/schema/xdsl.xdef">
	...
</orm>
```

* On the root node of the model file, we use `x:schema` to specify the metamodel definition file.

* [orm.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/orm.xdef) uses [xdef.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xdef.xdef), a meta-metamodel, to define itself.

* xdef.xdef is defined using xdef.xdef itself, so we do not need a higher-level meta-meta-metamodel.

### A unified metamodel language facilitates seamless nesting among DSLs

In the Nop platform, many DSL metamodel definitions reference other already-defined DSL models. For example, both [api.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/api.xdef) and [xmeta.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xmeta.xdef) reference the already defined [schema.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/schema/schema.xdef).

Different DSLs using the same type definitions also makes it easy to reuse the same visual design components, conversion tools, validation rules, etc.

### Automatically provide IDE plugins based on the metamodel

The Nop platform provides an IDEA plugin [nop-idea-plugin](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-idea-plugin). It automatically validates DSL syntax based on the metamodel specified by `x:schema`, and provides auto-completion, link navigation, and other features. For function-type DSL nodes, it can even provide breakpoint debugging. When we add a new DSL language, we do not need to develop a separate IDEA plugin; IDEA support is available out of the box.

Based on the metamodel, we can also automatically derive visual designers, without introducing separate visual designers for each DSL.

## III. All DSLs must provide decomposition and merging mechanisms

Once a DSL file becomes sufficiently complex, you inevitably need mechanisms like decomposition, merging, and library abstraction to manage complexity. XDSL defines a standardized Delta syntax; see [xdsl.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef).

```xml
<meta x:extends="_NopAuthUser.xmeta"
	  x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef" >

	<x:post-extends>
		<biz-gen:GenDictLabelFields xpl:lib="/nop/core/xlib/biz-gen.xlib"/>
	</x:post-extends>
</meta>
```

`x:extends` is used to inherit an existing model file, while `x:gen-extends` and `x:post-extends` are built-in metaprogramming mechanisms. They implement the Generator part in Reversible Computation theory: dynamically generating DSL model objects and then performing Delta merging.

`x:override` specifies the merging strategy for nodes during merge. For details, see the [Delta merge algorithm in Reversible Computation theory](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/dev-guide/xlang/x-override.md).

## IV. Manage all DSL files through a unified Delta file system

The Nop platform manages all model files under a unified virtual file system. This virtual file system provides functionality similar to UnionFS in Docker technology: different internal directories constitute different layers; files in higher-layer directories automatically override files at the same virtual path in lower-layer directories.
Specifically, `/_vfs/_delta/default/a.xml` automatically overrides the file `/_vfs/a.xml`. In code, anywhere using the virtual path `/a.xml` will actually load `/_vfs/_delta/default/a.xml` at runtime. In other words, we do not need to modify the original source code; by simply adding a file with the same name under the delta directory, we can automatically change the actual model that is loaded.

* You can specify multiple delta layers via the configuration item nop.core.vfs.delta-layer-ids (by default there is only one delta layer named default).
* An XDSL file under the delta directory can use `x:extends="super"` to indicate inheritance from the model file in the previous layer.
* Model files stored in database tables can also be mapped to a virtual file path; for example, wf:MyWf/1.0 indicates loading the model file from the NopWfDefinition table in the database.

With the Delta file system and XDSL’s built-in Delta merge algorithm, we can implement a system-level Delta customization mechanism. Without modifying the base product source code at all, we can add Delta modules to deeply customize the system’s data models, business logic, frontend UI, etc. See [How to achieve customized development without modifying the base product’s source code](https://zhuanlan.zhihu.com/p/628770810).

## V. Load DSL models through a unified Loader

The Nop platform uses a unified ResourceComponentManager to load all DSL models.

```
OrmModel model = (OrmModel)ResourceComponentManager.instance().loadComponentModel("/nop/auth/orm/app.orm.xml");
```

When we add a new DSL model, we can introduce a registration file, for example orm.register-model.xml:

```xml
<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       name="orm">
    <loaders>
        <xlsx-loader fileType="orm.xlsx" impPath="/nop/orm/imp/orm.imp.xml"/>
        <xdsl-loader fileType="orm.xml" schemaPath="/nop/schema/orm/orm.xdef"/>
    </loaders>
</model>
```

Through this registration model, we can specify how to parse a given file type into a model object.

* xlsx-loader specifies how to parse an Excel model file according to the Excel import model configuration.
* xdsl-loader specifies the metamodel that a DSL file must have and parses it according to the metamodel (the metamodel specified by the model file’s x:schema must be the schemaPath value, or an extension based on it).

With a unified model loader, we can implement code generation tools for arbitrary models:

```
java -jar nop-cli.jar gen abc.model.xlsx -t=/nop/templats/my-model
```

The gen command accepts a model file parameter; then specify the code generation template path via the -t parameter. It will automatically parse the model file to obtain the model object and pass it to the template to generate code. See [Data-driven Delta code generator](https://zhuanlan.zhihu.com/p/540022264).

### Parsing cache and dependency tracking

ResourceComponentManager internally manages the parsing cache for all DSL models and the dependencies between DSL model files. Its dependency tracking mechanism is similar to that used in the Vue frontend framework: the system dynamically records DSL models loaded or used during model parsing; when a model file’s modification time changes, all caches that depend on it are automatically marked invalid.

The nop-cli tool also provides a watch feature to monitor model files in a specified directory. When model files change, it automatically re-runs the code generator to produce derived code.

### Entry points to Reversible Computation

The core implementation of the principles of Reversible Computation is fully encapsulated in the ResourceComponentManager abstraction. The simplest way to introduce Reversible Computation into third-party applications is to replace your model loading function with ResourceComponentManager.loadComponentModel. For example, to bring Delta customization for model files into the Spring and MyBatis frameworks, we reimplemented the scanning of beans.xml and mapper.xml, used ResourceComponentManager to dynamically generate DOM objects, and then invoked Spring and MyBatis parsers to parse and register them into their respective engines.

For a theoretical analysis, see [Designing low-code platforms from the perspective of tensor products](https://zhuanlan.zhihu.com/p/531474176).

## VI. All DSL model objects support extension properties

XDSL model object properties are not fixed at development time; they generally inherit from the AbstractComponentModel base class and support the addition of arbitrary extension properties. In specific business applications, we can choose to inherit from existing metamodels and add business-specific extension properties.

For example, the platform has a built-in metamodel [xmeta.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xmeta.xdef).

We can define a xmeta-ext.xdef metamodel that inherits from xmeta.xdef and adds some extension fields:

```xml
<meta x:extends="/nop/schema/xmeta.xdef" xmlns:ui="ui" xmlns:graphql="graphql"
      x:schema="/nop/schema/xdef.xdef"
      xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef">

	<props>
		<prop ui:show="string" graphql:type="string" />
	</props>

</meta>
```

The above metamodel indicates adding the ui:show attribute and graphql:type attribute to the prop node of the xmeta model.

Then in the concrete meta file, we can use xmeta-ext.xdef to replace the original xmeta.xdef:

```xml
<meta x:schema="/my/schema/xmeta-ext.xdef">...</meta>
```

* The IDEA plugin will automatically recognize and use the extended metamodel definition to validate the Meta file.
* Models loaded via ResourceComponentManager.loadComponentModel will include the extension properties.

In other words, without modifying the platform’s built-in metamodel definitions, we can add extension properties to existing model objects at any time and use them programmatically just like built-in properties.

<!-- SOURCE_MD5:604233b0420346186d5e066eee419592-->
