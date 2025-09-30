# How to Use the Nop Platform to Enhance the Extensibility of Third-Party Frameworks
The Nop platform’s core can be easily integrated with any well-designed third-party framework. With a minimal amount of integration code, you can greatly enhance the extensibility of third-party frameworks.
Coincidentally, the Solon framework recently introduced a stream-processing plugin, and its sample code exposes a clear extension point that can integrate with the Nop platform.

![](images/solon-chain.png)

In this example, Chain is a model object that can be defined via a JSON file; it can be parsed and loaded through the `Chain.parseByUrl` function.

If integrated with the Nop platform, you can directly replace `Chain.parseByUrl` with `ResourceComponentManager.loadComponentModel(path)`, and then move the JSON file into the `resources/_vfs` directory.

With this change, you immediately gain the following capabilities:

1. The parsed result of the JSON file is automatically cached, and the model file is only read when it is not found in the cache.
2. To customize a model file, you don’t need to modify the original; just create a file with the same name under the `resources/_delta/default` directory. The Nop platform will prioritize loading files in the Delta directory.
3. If you prefer to store model files in a database rather than on the classpath, simply introduce a DAO file protocol, for example `dao:Chain/my-task/v1`.
4. If a file is too large, you can use the `x:extends` syntax on any child node to include a base file; the current node and the included node are automatically merged, enabling the split and recomposition of large model files.
5. ResourceComponentManager automatically tracks the dependencies of model files; whenever a dependent file changes, the model cache is automatically invalidated.
6. Through mechanisms such as `x:gen-extends` and `x:post-extends`, you can use metaprogramming to dynamically generate model nodes, offering more flexible secondary abstraction mechanisms.
7. With the Nop platform, you no longer need to design bespoke `import/plugin` mechanisms inside model files.

```json
{
  "x:gen-extends": "<chain-gen:GenBaseFlow xpl:lib='/xlib/chain-gen.xlib'/>",
  "steps": [
    {
      "x:extends": "my-step.chain.json",
      "name": "step1",
      "ui:hidden": false
    }
  ]
}
```

If you choose to implement the above yourself, it’s also straightforward—only two to three thousand lines of code.

```java
interface IDeltaModelLoader{
  <T> T loadDeltaModel(String path, Class<T> clazz);
}
```
For example, add the IDeltaModelLoader interface defined above, then use DeltaFileSystem to locate the file corresponding to the path, perform Delta merging at the JSON level, and convert it into the designated Java model class.

**Without modifying any runtime code of the Solon framework, simply replace the line that invokes the model loader to gain the Nop platform’s unique Delta customization capability and powerful metaprogramming abstraction.** With Delta customization, even after all JSON files are packaged into a JAR, you can deeply customize any model file without modifying files inside the JAR by adding additional Delta configurations. You may replace the entire model file or customize only a specific property within the model file.

## Metamodel Enhancement
If you supplement the JSON file with an xdef metamodel, you immediately gain the following capabilities:

1. You can define model files in multiple formats such as XML, JSON, and YAML.
2. Java classes corresponding to the model files can be generated automatically, making them convenient to use directly in Java code.
3. In the IntelliJ IDEA development tool, syntax validation, auto-completion, and breakpoint debugging are provided automatically.
4. You can define the model content via Excel; without writing any Excel parsing code, bidirectional conversion between complex, nested model objects and Excel files is performed automatically.
5. Model nodes automatically support extension attributes. XDef metamodel format checking will automatically skip all namespaced attributes.

<!-- SOURCE_MD5:6aae42186f65b8eb6078d77bfcabcb07-->
