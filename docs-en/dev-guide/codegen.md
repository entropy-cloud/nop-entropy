  
  # Maven Integration with Code Generator
  
  The Nop platform provides integration capabilities with Maven for code generation. However, this has not been implemented as a Maven plugin but instead leverages the `exec-maven-plugin` plugin to execute the `CodeGenTask` class's `main` method.

  To achieve this, only specific configurations need to be added to the POM file. When executing the `maven package` command, it will automatically process both the `precompile` and `postcompile` directories of the project. The `precompile` phase occurs before the compile phase, allowing access to all dependencies but not to the project's own classes. Conversely, the `postcompile` phase runs after the compile phase, enabling access to compiled classes and resources.

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
        ...
      </plugins>
    </build>
  </pom>
  ```

  If the POM does not inherit from a parent project, additional configurations for `exec-maven-plugin` may be necessary. For example, in the `nop-auth-service` module, the `precompile` phase uses the `nop-auth-dao` module's ORM model to generate a meta-model. Similarly, the `postcompile` phase generates i18n configuration files based on the project's meta-model.

  ## Executing Code Generation Outside Maven

  The `CodeGenTask` is a regular Java class that can be called directly outside of Maven. For example:

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

  Nop provides an Idea plugin for debugging. You can add breakpoints in the xgen files to debug. See [idea-plugin.md](../user-guide/idea/idea-plugin.md) for details.

  
  According to the Excel data model, the generated code is contained within the `xxx-codegen` and `xxx-web` modules, both of which include a `CodeGen.java` class, such as `NopAuthCodeGen` and `NopAuthWebCodeGen`.  
  Using these classes allows for direct execution of code generation logic within IDEA without relying on Maven tools. Execution via Maven consistently performs the Java compilation step beforehand, negatively impacting performance.

## Analyze Mode

Code generation typically does not require initializing the IoC container, allowing configuration of the Nok platform's initialization level as follows:

```java
AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL,
    CoreConstants.INITIALIZER_PRIORITY_ANALYZE);
```

Setting the `CFG_CORE_MAX_INITIALIZE_LEVEL` configuration variable to `ANALYZE` ensures that subsequent calls to `CoreInitialization` do not invoke the IoC container's `start` method.

Analyze is Nok's strongest static analysis mode. In this mode, Nok parses all configuration files but does not create any beans or actually enter a running state.

### Using nop-cli Command Line Tool for Code Generation

The `nop-cli` command line tool encapsulates the functionality of the `CodeGenTask` class via its `gen` command. Here's an example:

```shell
nop-cli gen model/app-mall.orm.xlsx -t=/nop/templates/orm
```

This example demonstrates reading the `app-mall.orm.xlsx` model and applying templates from the `/nop/templates/orm` directory within the virtual file system, generating output files into the current project directory.

## Adding Code Generation Templates Outside the Nok Platform

1. Add a template directory in your own project (e.g., `myapp-templates`) and place it in the `src/resources/_vfs` directory (e.g., `src/resources/_vfs/xxx/yyy`). Include template files like `xgen.java` in this directory.
2. Add a dependency on `myapp-templates.jar` during code generation. This ensures that the virtual file system initializes the `_vfs` directories within all included JARs, merging them into a single unified virtual file system. You can then specify the target template path as `-t=/xxx/yyy`.

If using Maven-integrated tools for code generation, you can add your own template project in the `test` scope:

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

## Data-Driven Code Generation

The `CodeGenTask` actually calls the `XCodeGenerator` class from the `nop-codegen` module, which is data-driven. This means that all logic in the code generation process is controlled by input template files provided externally.

### Section 1.2: Template Path Encoding and Loops

In contrast to traditional code generators, `XCodeGenerator` treats path templates as a micro DSL. It encodes loop logic and conditional checks directly into the template format, allowing the template's own structure to control the generation process.

#### Rules:
1. All files with suffixes ending in "xgen" are treated as template files.
2. Files without such suffixes are static files.

  
  ### File Handling Rules
  
  #### 1. File Suffix Handling
  - `xxx.java.xgen` → `xxx.java`: Files with the "xgen" suffix are moved to a generated folder by removing the "xgen" suffix.
  - `xxx.xrun` → `ignore`: Files with the "xrun" suffix are ignored and not processed as template files.
  - `xxx.java` → `xxx.java`: Files without the "xgen" suffix are directly copied without modification.

  #### 2. Internal Use Files
  - Files starting with "@" are reserved for internal use and are not treated as template files. They are not copied to the output directory.
  - The file `@init.xrun` is used for initialization and must be executed before any template processing in the target directory.

  #### 3. Variable Expression Handling
  - Variables are specified using expressions like `{a.b.c}` to denote nested loops, allowing for natural multi-level iteration.
  
  Example:
  ```javascript
  /nop/base/generator/test/{globalVar}/{var1}/sub/{var2.packagePath}/{var3}.java.xgen

  // Equivalent to three-level nested loops
  if(globalVar){
    for(let var1 of ...){
      for(let var2 of ...){
        for(let var3 of ...){
          if(var1 && var2 && var2.packagePath && var3){
            // Path construction logic here
          }
        }
      }
    }
  }
  ```
  
  - `{var2.packagePath}` indicates that the loop iteration uses the value of `var2.packagePath`.

  #### 4. Conditional File Generation
  - Use `{a.b.c}` variable expressions to control whether directories or files are generated.
  - If a variable resolves to `false` or `null`, the corresponding directory/file is skipped.
  - If a variable resolves to `true`, the directory/file is included in the generation.

  Example:
  ```javascript
  /src/{package.name}/{webEnabled}/{model.name}Controller.java.xgen
  /src/{model.name}Service.java.xgen

  // Individual file control
  /src/{package.name}/{webEnabled}{model.name}Controller.java.xgen
  ```
  
  ### Variable Definition Example
  
  ```xml
  <gen:DefineLoop xpl:lib="/nop/codegen/xlib/gen.xlib" xpl:slotScope="builder">
    <c:script>
      builder.defineGlobalVar("ormModel", ormModel);
      builder.defineLoopVar("entityModel", "ormModel", model => model.entityModelsInTopoOrder);
    </c:script>
  </gen:DefineLoop>
  
  This defines two context variables, `task` and `response`, which can then be used in path construction using `{response.responseCode}`.
`Task{task.taskCode}{response.responseCode}ResponseTrigger.java` path will automatically identify the cyclic dependency between task and response, and automatically expand the cyclic relationship.


### Expression Convention

1. `{!!entity.field}` such expressions indicate negation (`!`) and double negation (`!!`). For empty values, it will return `false`.
   - If the expression returns `false`, it will be skipped in this execution.
   - However, for empty values, the expression's value will be ignored.

  Empty values are not equivalent to `false`. Therefore, an empty value should be treated differently from a `false` value.

2. `{data.@mapper}` property expressions have a special convention. For `HashSet` or `LinkedHashSet`, the `@mapper` attribute checks if the collection contains the specified value.
   - This is equivalent to `((Set)data).contains('mapper')`


### XGen Template File
XGen is essentially an extension of the XPL template language. It dynamically generates and outputs content. The XPL template language can be used with tags like `<c:for>`, `<c:script>`, and `<c:import>` to execute logic, while also providing functionality for dynamic output.

For example:
```xml
<c:unit>
  <c:script>
    let n = 100; // Set a variable and execute complex XScript code
  </c:script>

  <!-- Using expressions to access the current environment variables. The XPL template language provides tags like <c:for> for loop logic -->
  <c:for var="i" begin="${1}" end="${n}">
    <div/>
  </c:for>
</c:unit>
```
The above code will output 100 `<div>` nodes.

`<c:script>` is compiled into an Expression, and then `expr.invoke(scope)` is executed within the current scope. The data used in `@init.xrun` is prepared in `@init.xurn`. The `assign("x", 1")` can set variables in the scope. Additionally, `builder.defineGlobalVar('basePackagePath', pkgPath)` will also set variables.

For more information about XPL, refer to [XPL Documentation](xlang/xpl.md).


## II. Differential Generator

If we do not view code generators as one-time or temporary auxiliary tools, but rather as an integral part of meta-programming, then the code generator must inherently support incremental generation. Incremental generation means that the code generator allows multiple executions and manual modifications, while both automatically generated and manually modified parts can be considered as incremental modifications to the initial generation.

The total result is calculated as:
```
Result = FirstGeneration + AutoGenDelta + ManualDelta
```

Reversible computing explicitly introduced the concept of reversible delta merging. It points out that the total is a special case of delta, providing a unified explanation for a series of delta-related practices and offering insights into future developments.

> `Total = Unit + Delta`, for example: `1 = 0 + 1`. Here, `1` is both the combined result of `0 + 1` and the difference between `0` and `1`.

Based on reversible computing theory, the `<XCodeGenerator>` treats manual modifications as customized deltas for generated code. It uses various technical means to achieve delta merging.


### II.1. Leveraging Object-Oriented Language's Inheritance Mechanism

In common object-oriented languages, we can leverage class inheritance to implement isolation between manually modified and automatically generated code. `<XCodeGenerator>` defines the following coverage rules:

1. Files starting with `_` are always covered.
2. Files in the `_gen` directory are always covered.
3. If a file's first 250 characters contain `__XGEN_FORCE_OVERRIDE__`, it is automatically covered.

In typical usage, it follows a **sandwich structure**: Custom classes inherit from auto-generated classes, which in turn inherit from base classes provided by the platform. In this structure:
```
CustomClass extends _AutoGenClass extends BaseClass
```

The auto-generated class `<_AutoGenClass>` typically includes basic functionality required by the application, while `BaseClass` provides a foundation for further extensions.

    
```java
public class SqlSubqueryTableSource extends _SqlSubqueryTableSource {
    public boolean isGeneratedAlias() {
        return alias != null && alias.isGenerated();
    }

    public ISqlTableMeta getResolvedTableMeta() {
        return getQuery().getResolvedTableMeta();
    }

    @Override
    public void normalize() {

    }
}

class _SqlSubqueryTableSource extends SqlTableSource { ... }
class SqlTableSource extends _SqlTableSource { ... }
class _SqlTableSource extends EqlASTNode { ... }
```

### 2.2. Generalizing the `x:extends` Operator

For generic XML and JSON formats, which correspond to the general Tree structure, the `x:extends` operator from reversible computing theory can be used for differential merging. For example, the Baidu AMIS framework is a low-code front-end framework in JSON format; it can be modified using differential techniques as shown below:

```json
{
  "x:extends": "_page_crud.json5",
  "body": {
    "columns": [
      {
        "x:id": "operation",
        "buttons": [
          {
            "x:id": "row-update-button",
            "visibleOn": "chgSts == '1'"
          },
          {
            "x:id": "row-delete-button",
            "visibleOn": "chgSts == '1'"
          }
        ]
      }
    ]
  }
}
```

The above example demonstrates how to add visibility controls to automatically generated buttons in the operations list.

### 2.3. Generalizing Delta Customization

The entire Nop platform is built on reversible computing theory, enabling differential customization at various levels. Notably, `XCodeGenerator` itself supports delta customization through Nop's built-in delta customization mechanism:

1. All template files support delta customization. If a `_delta/xxx/yyy.xgen` file exists, it will automatically replace the default `/xxx/yyy.xgen` file.
2. Templates use xpl template language for customization, allowing function-level customization by adding xpl libraries to the `/_delta` directory:
   ```xml
   <lib x:extends="super">
       <!-- Using x:extends indicates inheritance of previous tag library implementation -->
       <tags>
           <CustomTag>
               <source>
                   This implementation will replace the default tag library definitions.
                   All CustomTag calls will use this customized implementation
               </source>
           </CustomTag>
       </tags>
   </lib>
   ```

## Common Issues

### 1. Differences Between `precompile` and `postcompile`

- **Precompile**: Runs before compilation, cannot access current project's classes or resources.
- **Precompile2**: Runs before compilation but can access current project's resources.
- **Postcompile**: Runs after compilation to check IOC container configurations and can access current project's classes and resources.

### 2. Customizing Existing Templates, e.g., ORM Generation

- Add a template directory:  
  ```bash
  mkdir -p templates/custom
  ```
- Create an `a-impl.xrun` file in the templates directory to override the default implementation:
  ```xrun
  gen:Render("custom_orm_template") {
      // Custom logic here
  }
  ```

The above setup allows for selective generation of specific components while preserving others.
```xml
<c:Unit>
    <gen:Render template="/nop/templates/orm/{appName}-dao" targetDir="${targetResource.path.$filePath()}"
                xpl:lib="/nop/codegen/xlib/gen.xlib" inheritCodeGenLoop="true"/>
</c:Unit>
```


### Escaping in Variable Substitution
```
${''}{varName}
```
This demonstrates how to escape quotes during variable substitution in the xgen template generation process.

