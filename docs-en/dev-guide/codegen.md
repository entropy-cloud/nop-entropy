
# Maven-Integrated Code Generator

The Nop platform provides code generation capabilities integrated with Maven. Instead of implementing a Maven plugin, it uses the `exec-maven-plugin` to run the `main` function of the `CodeGenTask` class.

You only need to add the following configuration to your POM file; when you run `maven package`, the xgen code under the project's precompile and postcompile directories will be executed automatically. The precompile directory runs before the compile phase; the execution environment can access all dependency libraries but cannot access the current project's class directory. The postcompile directory runs after the compile phase and can access compiled classes and resource files.

```xml
<pom>
    <parent>
        <artifactId>nop-entropy</artifactId>
        <groupId>io.github.entropy-cloud</groupId>
        <version>2.0.0-SNAPSHOT</version>
    </parent>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</pom>
```

If you do not use POM parent inheritance, you need to provide additional parameter configuration for the `exec-maven-plugin`. See the configuration in [nop-entropy/pom.xml](../../pom.xml) for details.

> For example, in the `nop-auth-service` module, the precompile phase generates the meta model based on the ORM model in the `nop-auth-dao` module, while the postcompile phase generates i18n configuration files based on the meta model in the current project.

## Invoking the Code Generator Outside Maven

`CodeGenTask` is a regular Java class and can be invoked directly outside Maven. For example:

```java
public class NopOrmCodeGen {
    public static void main(String[] args) {
        AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL,
                CoreConstants.INITIALIZER_PRIORITY_ANALYZE);

        CoreInitialization.initialize();
        try {
            File projectDir = MavenDirHelper.projectDir(NopOrmCodeGen.class);
            String targetRootPath = FileHelper.getFileUrl(new File(projectDir, "src/main/java"));
            XCodeGenerator generator = new XCodeGenerator("/nop/templates/orm-entity", targetRootPath);
            IResource resource = VirtualFileSystem.instance().getResource("/nop/test/orm/app.orm.xml");
            OrmModel ormModel = (OrmModel) DslModelHelper.loadDslModel(resource);
            generator.execute("", Collections.singletonMap("ormModel", ormModel), XLang.newEvalScope());
        } finally {
            CoreInitialization.destroy();
        }
    }
}
```

The Nop platform provides an Idea debugging plugin for the XLang language, allowing you to add breakpoints in xgen files for debugging. See [idea-plugin.md](../user-guide/idea/idea-plugin.md).

In code projects generated from Excel data models, both the `xxx-codegen` module and the `xxx-web` module include a `CodeGen.java` class (e.g., `NopAuthCodeGen` and `NopAuthWebCodeGen`). Using these classes, you can execute code generation directly in IDEA without going through Maven. Maven execution always compiles Java first, which impacts performance.

## Analyze Mode

Code generation typically does not require starting the IoC container, so you can configure the Nop platform's initialization level as follows:

```
  AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL,
                CoreConstants.INITIALIZER_PRIORITY_ANALYZE);
```

After setting the maximum initialization level configuration variable to ANALYZE, calling `CoreInitialization` will not invoke the IoC container's start method.

Analyze is the strongest static analysis mode provided by the Nop platform. In this mode, the Nop platform parses all configuration files but does not create any beans and does not enter a running state.

### Execute Code Generation via the nop-cli CLI Tool

The `gen` command of the `nop-cli` tool wraps the functionality of the `CodeGenTask` utility class, exposing it as a command-line invocation.

```shell
nop-cli gen model/app-mall.orm.xlsx -t=/nop/templates/orm
```

The example above reads the `app-mall.orm.xlsx` model, applies the template files under `/nop/templates/orm` in the virtual file system, and generates code into the current project directory.

## Adding Code Generation Templates Outside the Nop Platform

1. In your own project (e.g., `myapp-templates`), add a template directory under `src/resources/_vfs`, such as `src/resources/_vfs/xxx/yyy`, and then add xgen files to it.
2. When running the code generator, add a dependency on `myapp-templates.jar`. During virtual file system initialization, all `_vfs` directories in jars are automatically scanned and merged into a unified virtual file system. You can then specify `-t=/xxx/yyy` during code generation.

```
java -Xbootclasspath/a:myapp-templates.jar -jar nop-cli-2.0.0-BETA.1.jar  gen model/demo.orm.xlsx -t=/xxx/yyy
```

If you are using the Maven-integrated code generation tool, you can introduce your newly created template project in the test scope:

```xml

<pom>
    <dependencies>
        <dependency>
            <groupId>me.app</groupId>
            <artifactId>myapp-template</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</pom>
```

## Data-Driven Code Generator

`CodeGenTask` actually invokes the data-driven code generator `XCodeGenerator` in the `nop-codegen` module. Data-driven means that the entire logic of the generation process is specified by the input template files—i.e., the generation process is driven by externally provided template data.

### I. 1.2 Encoding Conditionals and Loops in Template Paths

`XCodeGenerator` differs from traditional code generators: it treats template paths as a micro-format DSL, encoding conditionals and loop logic into the path format. The template's own structure thereby controls the generation process. The rules are as follows:

1. All files with the xgen suffix are considered template files, and files without the xgen suffix are static files.

```
xxx.java.xgen --> xxx.java  generated to the file with the xgen suffix removed
xxx.xrun      --> ignore    if the suffix is xrun, the xpl template code is executed directly (no output file is generated)
xxx.java      --> xxx.java  files without the xgen suffix are directly copied
```

2. All files with the `@` prefix are for internal use; they are not parsed as templates and are not copied to the target directory. The `@init.xrun` file is an initialization file; before running templates under this directory, `@init.xrun` must be executed to complete initialization. For example, in `@init.xrun`, you can define which variables are loop variables and specify the relationships among these loop variables:

```xml
<gen:DefineLoop xpl:lib="/nop/codegen/xlib/gen.xlib" xpl:slotScope="builder">
<c:script>
builder.defineGlobalVar("ormModel",ormModel);
builder.defineLoopVar("entityModel","ormModel", model => model.entityModelsInTopoOrder);
</c:script>
</gen:DefineLoop>
```

3. Directory and file names use variable expressions in the `{a.b.c}` form to specify loop variables, expressing multi-level nested loops naturally. For example:

```javascript
/nop/base/generator/test/{globalVar}/{var1}/sub/{var2.packagePath}/{var3}.java.xgen

// Equivalent to a triple loop
if(globalVar){
    for(let var1 of ...){
     for(let var2 of ...){
        for(let var3 of ...){
            if(var1 && var2 && var2.packagePath && var3){
                let path = '/nop/base/generator/test/'
                +globalVar+'/'+var1+'/sub/'+var2.packagePath
                +'/'+var3+'.java';
                ...
            }
        }
     }
    }
}
```

- `{var2.packagePath}` indicates looping over the variable `var2`, taking the `var2.packagePath` property on each iteration.

- In nested loops, references to parent loop variables are fixed values. For example, for `{var1}/{var2}/{var1}_{var2}.java.xgen`, when referencing `var1` and `var2` again under the `{var2}` subdirectory, they are fixed values.

4. Directory and file names also use `{a.b.c}` variable expressions to specify switch variables. When the variable value evaluates to `false` or `null`, the directory or file is skipped. When it evaluates to `true`, the placeholder content is automatically omitted from the path. For example:

```
Control whether to generate files under a directory
/src/{package.name}/{webEnabled}/{model.name}Controller.java.xgen
                                /{model.name}Service.java.xgen

You can also control generation of a single file
/src/{package.name}/{webEnabled}{model.name}Controller.java.xgen
```

### Concrete Example
```xml
<gen:DefineLoop xpl:lib="/nop/codegen/xlib/gen.xlib" xpl:slotScope="builder">
<c:script>
builder.defineLoopVar("task","tasks",model=>model);
builder.defineLoopVar("response","task", model => model.taskResponse?.responseList);
</c:script>
</gen:DefineLoop>
```

This defines two context variables, `task` and `response`. You can then reference variable properties in paths using `{response.responseCode}`.

A path like `Task{task.taskCode}{response.responseCode}ResponseTrigger.java` will automatically recognize the loop dependency between `task` and `response` and expand the loops accordingly.

### Special Conventions for Expressions

1. In expressions like `{!!entity.field}`, `!` means logical negation and `!!` means double negation; for null values, it returns false. If the expression evaluates to false, the current processing is skipped; however, for null values, the expression value is ignored. Therefore, null and false are not equivalent.

2. The property expression `{data.@mapper}` has a special convention: for `HashSet` or `LinkedHashSet` types, the `@mapper` property checks whether the collection contains that text value—equivalent to `((Set)data).contains('mapper')`.

### xgen Template Files
xgen is essentially the xpl template language that dynamically executes to produce output. In xpl, you can use tags like `<c:for>` and `<c:script>` to execute logic, and `<c:import>` to import tag libraries. xpl also provides the `<c:print>` tag to output its body content verbatim, even if it contains tags like `c:script`.

For example:
```xml
<c:unit>
  <c:script>
    let n = 100; // Set a variable; complex XScript code can be executed
  </c:script>

  <!-- Expressions can use variables in the current environment. The xpl template language provides tags like c:for for looping logic -->
  <c:for var="i" begin="${1}" end="${n}">
    <div/>
  </c:for>
</c:unit>
```
The above code will output 100 `<div>` nodes.

`c:script` is compiled into an Expression and then `expr.invoke(scope)` is executed in the current scope. The data in `scope` is what was prepared in `@init.xrun`. In `@init.xurn`, `assign("x",1")` can set variables in the scope. In addition, `builder.defineGlobalVar('basePackagePath', pkgPath)` etc. also set variables.

For an introduction to the xpl template language, see [xpl.md](xlang/xpl.md).

## II. Delta-Based Code Generator

If we do not regard the code generator as a one-off, ad hoc external tool, but instead as an organic part of metaprogramming, then the code generator must support incremental generation. Incremental generation means the code generator can be run repeatedly, and manual edits to the output are allowed. Both auto-generated and manually edited parts can be viewed as Delta modifications to the initial output, and they will be automatically merged.

```
Result = FirstGeneration + AutoGenDelta + ManualDelta
```

The Reversible Computation theory explicitly proposes the concept of reversible Delta merging, pointing out that a total value is a special case of Delta. It provides a unified theoretical explanation for a range of Delta-related programming practices and indicates future development directions.

> `Total = Identity + Delta`, e.g., `1 = 0 + 1`. The value `1` is both the total result obtained by merging `0+1` and the Delta between `0` and `1`.

Following the ideas of the Reversible Computation theory, `XCodeGenerator` treats manual edits as customized Deltas over the auto-generated part and leverages various techniques to implement Delta merging between them.

### 2.1. Using Class Inheritance in Object-Oriented Languages

For common object-oriented languages, we can use class inheritance to isolate manual code from auto-generated code. `XCodeGenerator` defines the following overriding rules:

1. Files prefixed with `_` are always overwritten.
2. Files under the `_gen` directory are always overwritten.
3. If the first 250 characters of a file contain the string `__XGEN_FORCE_OVERRIDE__`, the file is automatically overwritten.

In practice, the commonly used approach is the so-called "sandwich architecture": the customization class inherits from the auto-generated class, and the auto-generated class inherits from a base class provided by the platform. In this structure, auto-generated code can obtain helper functions and common variable environments from the platform base class, while customization classes can use auto-generated variables and functions and customize the auto-generated functions when necessary.

```
CustomClass extends _AutoGenClass extends BaseClass
```

```java
public class SqlSubqueryTableSource extends _SqlSubqueryTableSource {
    public boolean isGeneratedAlias() {
        return alias != null && alias.isGenerated();
    }

    public ISqlTableMeta getResolvedTableMeta() {
        return getQuery().getResolvedTableMeta();
    }

    @Override
    public void normalize(){

    }
}
class _SqlSubqueryTableSource extends SqlTableSource{...}
class SqlTableSource extends _SqlTableSource {...}
class _SqlTableSource extends EqlASTNode {...}
```

### 2.2. x:extends Operator for Generic Tree Structures

For general XML and JSON formats, since they correspond to generic Tree structures, you can use the generic `x:extends` operator defined in the Reversible Computation theory to implement Delta merging. For example, Baidu's AMIS framework is a JSON-based front-end low-code framework. To apply Delta-based customization, you can use the following:

```json
{
  "x:extends":"_page_crud.json5",
  "body":{
    "columns":[
      {
        "x:id":"operation",
        "buttons":[
          {
            "x:id":"row-update-button",
            "visibleOn": "chgSts == '1'"
          },
          {
            "x:id":"row-delete-button",
            "visibleOn": "chgSts == '1'"
          }
        ]
      }
    ]
  }
}
```

The example above demonstrates how to add display state control to row buttons in a standard auto-generated CRUD list.

### 2.3. General Delta Customization

The entire Nop platform is implemented based on the Reversible Computation theory and supports Delta customization at various levels. In particular, `XCodeGenerator` itself can be customized using the platform's built-in delta customization mechanism.

1. All template files support delta customization: if a `/_delta/xxx/yyy.xgen` file exists, it will automatically replace the built-in `/xxx/yyy.xgen` file.

2. Template files are implemented using the xpl template language, so you can achieve function-level customization via xpl tag libraries. By adding customized tag libraries under the `/_delta` directory, you can modify tag definitions used in the system.

```xml
<lib x:extends="super">
    <!-- x:extends indicates inheritance from the previous tag library implementation. In this file,
    you can add/modify/delete definitions of inherited tags -->
    <tags>
        <CustomTag>
            <source>
                This implementation replaces the default implementation in the tag library. All calls to the CustomTag tag will use this implementation.
            </source>
        </CustomTag>
    </tags>
</lib>
```

## FAQ

### 1. What is the difference between precompile and postcompile

* precompile runs before compilation and cannot access classes and resource files of the current project.
* precompile2 runs before compilation but can access resource files of the current project.
* postcompile runs after compilation, checks bean configuration in the IoC container, and can access classes and resource files of the current project.

### 2. How to customize existing templates, e.g., only generate certain modules during ORM generation

* Add a template directory.
* Add an `a-impl.xrun` file in it. `xrun` means only execute code without generating files. The name `a-impl` can be chosen arbitrarily; you can control execution order via the filename. All template files are sorted lexicographically.
* In the xrun file, use the `gen:Render` tag to reference existing templates:

```xml
<c:unit>
    <gen:Render template="/nop/templates/orm/{appName}-dao" targetDir="${targetResource.path.$filePath()}"
                xpl:lib="/nop/codegen/xlib/gen.xlib" inheritCodeGenLoop="true"/>
</c:unit>
```

### How to escape ${} during xgen template generation
```
${'$'}{varName}
```

<!-- SOURCE_MD5:38baf37851a1a93473207d206cc1d185-->
