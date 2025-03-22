# Custom Models

The Nop platform differs from other platforms in one fundamental aspect: it is based on reversible computation principles to decompose, combine, and derive models. All its components and mechanisms are designed to allow users to extend or customize them as needed. Users can leverage Nop's built-in meta-model and meta-programming mechanisms to develop models tailored to their specific domain.

The following example illustrates the process of introducing a custom model using an API model analysis and code generation as examples.


## 1. Registering Model Files and Their associated Loaders

Create a file named `api.register-model.xml` in the virtual path `/nop/core/registry`.

```xml
<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
    name="api">
    <loaders>
        <xlsx-loader fileType="api.xlsx" impPath="/nop/graphql/imp/api.imp.xml" />
        <xdsl-loader fileType="api.xml" schemaPath="/nop/schema/api.xdef" />
    </loaders>
</model>
```

* xlsx-loader: Parses Excel files into Java objects, typically of type DynamicObject. When used with the Xpl template language, it behaves similarly to a Json object but throws errors if undefined properties are accessed.
* xdsl-loader: Parses XML files into Java objects, typically of type DynamicObject.

The `fileType` comparison is more complex and may extract the last two segments of the filename based on delimiters. Since we use file names like `app.orm.xml`, `app.orm.xlsx`, etc., using only `fileExt` would fail to identify ORM-related XML and XLSX files properly.


## 2. Loading Models

The `RegisterModelCoreInitializer` automatically collects all files in the `/_vfs/nop/core/registry/xxx.register-model.xml` directory during framework startup, consolidates them into a single registry file, and then parses it into configuration objects, which are then registered with `ResourceComponentManager`.

After registration, models can be retrieved using:

```javascript
apiModel = ResourceComponentManager.instance().loadComponentModel("/xxx/yyy.api.xlsx");
```

The `loadComponentModel` method identifies the appropriate FileLoader based on the file's type and returns the model object. The `ResourceComponentManager` maintains a cache of models and tracks dependencies discovered during parsing. For example, when parsing `yyy.orm.xlsx`, it loads `orm-gen.xlib`. If `orm-gen.xlib` or `yyy.orm.xlsx` changes, the cache is invalidated.

To force a cache refresh, call:

```javascript
GlobalCacheRegistry.instance().clearAllCache();
```


### 3. Code Generation

Nop's built-in code generation tool supports rendering custom models. For example, it can generate service messages and interface classes based on an API model, using `gen-orm.xgen` located at [https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-wf/nop-wf-codegen/precompile/gen-orm.xgen](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-wf/nop-wf-codegen/precompile/gen-orm.xgen).

```javascript
// Generate service messages and interface classes based on API model
codeGenerator.withTargetDir("../").renderModel('../../model/nop-wf.api.xlsx', '/nop/templates/api', '/', $scope);
```

The `renderModel` method reads the model from `modelPath`, uses `templatePath` to locate the template files, and renders them into Java classes.

> The Nop platform organizes all resources into a virtual file system under `/resources/_vfs`, so you don't need to specify which module a template belongs to when using the code generator.

