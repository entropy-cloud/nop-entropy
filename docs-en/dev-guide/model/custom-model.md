# Custom Models

Compared with all other public platform technologies, the Nop platform has a fundamental difference: it implements model decomposition, merging, and inference based on Reversible Computation principles. All of its internal components and mechanisms allow users to extend or customize them. Users can also leverage the Nop platform’s built-in meta-model and metaprogramming mechanisms to develop models specific to their own domains.

The following uses parsing and code generation of the API model as an example to introduce the process of incorporating custom models.

## 1. Register loaders for model file suffixes

Create the file api.register-model.xml under the virtual path /nop/core/registry.

```xml
<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
    name="api">
    <loaders>
        <xlsx-loader fileType="api.xlsx" impPath="/nop/graphql/imp/api.imp.xml" />
        <xdsl-loader fileType="api.xml" schemaPath="/nop/schema/api.xdef" />
    </loaders>
</model>
```

* xlsx-loader: Parses Excel files according to the imp model definition to obtain a Java object, typically of type DynamicObject. When used in the Xpl templating language, it behaves similarly to a JSON object, but attempting to read properties not defined in the model will throw an exception.
* xdsl-loader: Parses XML files according to the xdef meta-model definition to obtain a Java object, typically of type DynamicObject.

fileType is more complex than the file extension (fileExt): it may take the last two segments of the filename separated by dots. Because we extensively use composite filenames like app.orm.xml and app.orm.xlsx, using only fileExt cannot identify XML and XLSX files that are specific to the ORM model.

## 2. Load the model

During framework startup, the Nop platform’s RegisterModelCoreInitializer automatically collects all `/_vfs/nop/core/registry/xxx.register-model.xml` files, merges them into a complete registry file, then parses it into configuration objects and registers them with the ResourceComponentManager.

After registration, you can obtain a model object as follows:

```javascript
apiModel = ResourceComponentManager.instance().loadComponentModel("/xxx/yyy.api.xlsx");
```

loadComponentModel looks up the corresponding FileLoader based on the file’s fileType, then parses and returns the model object.

ResourceComponentManager provides model caching and automatically tracks resource file dependencies discovered during model parsing. For example, when parsing yyy.orm.xlsx and loading the tag library orm-gen.xlib, any change to orm-gen.xlib or to yyy.orm.xlsx itself will invalidate the parsing cache. This mechanism is similar to the source-change watching provided by frontend frameworks like Webpack or Vite.

If you need to force-refresh the model cache, you can call methods on the GlobalCacheRegistry object:

```
GlobalCacheRegistry.instance().clearAllCache();
```

### 3. Code Generation

The Nop platform’s built-in code generation tools support rendering custom models; see [gen-orm.xgen](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-wf/nop-wf-codegen/precompile/gen-orm.xgen)

```javascript
// Generate service messages and interface classes from the API model
codeGenerator.withTargetDir("../").renderModel('../../model/nop-wf.api.xlsx','/nop/templates/api', '/',$scope);
```

renderModel(modelPath, templatePath) reads the model object and renders it using the template files in the template directory.

> The Nop platform organizes all files under resources/\_vfs into a unified virtual file system, so you can use template files in the code generator without specifying which module they belong to.

<!-- SOURCE_MD5:bb8926f12407dc10e0025baa8fcf4dbe-->
