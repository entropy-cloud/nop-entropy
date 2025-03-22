# Common Issues

## Development Issues

### 1. After modifying the column name and saving, the front-end list page does not reflect the changes. However, the back-end has already been modified.

When saved to the back-end, the `page.yaml` file will carry the i18n key for the label. Consequently, the front-end page will be replaced with internationalized text.

```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthDept.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
body:
  name: crud-grid
  columns:
  - name: deptType
    label: '@i18n:col.NopAuthDept.deptType,prop.label.NopAuthDept.deptType|改变类型'
    placeholder: '-'
    x:virtual: true
  x:virtual: true
```

In the `@i18n:key|defaultValue` format, the part after the pipe (`|`) represents the default value. This default value will only be returned if the corresponding i18n key does not exist in the design phase.

When viewing in the back-end, since it is still in the design phase, the i18n key will not be replaced. If you absolutely want to use the modified value, you can delete the i18n key.

```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthDept.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
body:
  name: crud-grid
  columns:
  - name: deptType
    label: '改变类型'
    placeholder: '-'
    x:virtual: true
  x:virtual: true
```

In the back-end, you must manually delete it in the JSON view.

![remove-i18n-key.png](https://via.placeholder.com/10)

### 2. How to Construct Query Conditions in GraphQL

You can use the simplified query syntax `@query:NopAuthDept__findList/id?filter_deptName=a` in the front-end for simplified querying. When executing `findList` or `findPage`, it will recognize `filter_` as a prefix for parameters and convert it into a `QueryBean` with a tree-like structure for filtering conditions. If you absolutely need to manually construct a `QueryBean`, you can follow the example below:

```graphql
query($query: QueryBeanInput, $q2: QueryBeanInput) {
  NopAuthDept__findList(query: $query) {
    id,
    deptName
    parent {
      id
    }
  },

  NopAuthUser__findPage(query: $q2) {
    page
    items {
      nickname: nickName
      username: userName
    }
  }
}
```

The variables are set as follows:

```json
{
  "query": {
    "filter": {
      "$type": "and",
      "$body": [
        {
          "$type": "eq",
          "name": "deptName",
          "value": "a"
        }
      ]
    }
  },
  "q2": {
    "filter": {
      "$type": "eq",
      "name": "userName",
      "value": "a"
    }
  }
}
```

### 3. Unit Testing Without Using a Local Database

Automated testing documentation can be found in [autotest.md](../dev-guide/autotest.md).

1. Use an in-memory database based on NopOrm's data layer for recording and replaying mechanisms.
2. If you do not need an in-memory database, you can inherit from JunitBaseTestCase to implement pure logic testing, which only starts the IoC container. If IoC is also unnecessary, simply inherit from BaseTestCase for some helper functions.

### 4. When models in the virtual file system are updated during application execution, will they automatically refresh? Or do I need to restart the application?

The Nop platform internally uses the `ResourceComponentManager` to load model files. Loaded models are cached in memory within the `ResourceLoadingCache`, which includes dependency tracking. This means that if dependencies change, the cache will automatically be updated and the models will reflect the changes without requiring a restart of the application.

```markdown
# Model Parsing Process

The model parsing process uses all the dependent model files. If any of these files are modified (with a changed timestamp), the model cache will automatically become invalid. The next time the model is accessed, it will be re-parsed.

If the file is newly generated, the virtual file system will not automatically detect the new file. It is necessary to call `VirtualFileSystem.instance().refresh(true)`.

The virtual file system includes directories such as `_vfs` in the classpath and also automatically includes the `_vfs` directory from the current working directory at startup. The `_vfs` directory in the current working directory has higher priority, where its files can override those in the classpath.

In the front-end interface, there is a "Refresh Cache" button that clears the global model cache and automatically refreshes the virtual file system.

### 4. Retrieving request, response, etc., from IServiceContext

The design of NopGraphQL is independent of the Web environment. It can be used in scenarios like messaging queues, batch processing, etc., as a generic service dispatching and result aggregation framework. Therefore, it does not provide any methods related to the Web environment.

In IServiceContext, you can use methods like `setAttribute/getAttribute` to store custom objects. The `IServiceContext.getCache()` method also provides a cache object valid within a single request scope, which can be used for caching dictionary data, etc. Business parameters should generally be explicitly passed; `IServiceContext` is basically equivalent to a Map structure and is mainly used for storing shared information within the framework's internal processing of a single request.

### 5. Integration with Nop and Keycloak (excluding single-point integration), can authorization features also be used?

Role integration has been done, so you can use roles configured in Keycloak. Role permissions are associated in the Nop platform; user-role relationships should be configured within the Nop platform. Keycloak can configure user and role associations, as seen in `OAuthLoginServiceImpl.java`.

### 6. What is the `_ExcelWorkbook` model class generated for?

The model class is generated by a code generator during Maven packaging.

When `mvn package` is executed, it runs the `exec-maven-plugin` plugin in the `nop-entropy` project's root pom.xml file under `pluginManagement`.

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.0.0</version>
    <executions>
        <execution>
            <id>precompile</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>java</goal>
            </goals>
            <configuration>
                <arguments>
                    <argument>${project.basedir}</argument>
                    <argument>precompile</argument>
                </arguments>

                <!--
                Prevents including META-INF directories from loading the ICOREInitializer -->
                -->
                <addResourcesToClasspath>false</addResourcesToClasspath>
                <addOutputToClasspath>false</addOutputToClasspath>
            </configuration>
        </execution>
        ...
    </executions>
</plugin>
```

In the `nop-excel` project's pom file, adding `exec-maven-plugin` will automatically execute the `precompile` directory's code generation script.

```xml
    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
```

In the `precompile` directory, all files with suffixes ending in `xgen` will be automatically executed.

```xml
<c:script>
codeGenerator.renderModel('/nop/schema/excel/workbook.xdef','/nop/templates/xdsl', '/',$scope);
codeGenerator.renderModel('/nop/schema/excel/imp.xdef','/nop/templates/xdsl', '/',$scope);
</c:script>
```

The `XCodeGenerator`'s `renderModel` function can read model files and execute code generation templates. In the example provided, it reads an `xdef` meta-model definition and calls the `nop-codegen` module's `/nop/templates/xds` template.

### 7. When does the system automatically create tables?

After configuring `nop.orm.init-database-schema=true`, the `DatabaseSchemaInitializer` class will handle the automatic creation of database tables. However, if the table creation statement fails, it will be ignored, and subsequent table creation statements will not be executed. This means that if the database is empty, the table will be successfully created. If the database already contains tables, the creation will fail due to duplicate table issues.

Normally, only individual tables are added during configuration. Currently, the system does not automatically identify or create new tables based on existing configurations. You can set the log level to `TRACE` to see detailed error messages when table creation fails.

### 8. When generating code from multiple libraries, should you combine them into a single Excel file?

The data model is designed such that each Excel module corresponds to one Excel sheet. It should be a modular system, allowing for either microservices or monolithic application deployment. The `nop-auth`, `nop-wf`, and other internal modules are each separate sub-modules with their own models. Each module can be developed and debugged independently.

If there are cross-module references, you can add external table references in the Excel file (tagged with `not-gen`).

### 9. Can tables in an ORM be stored in different databases?

If you configure each entity to point to a different query space (`querySpace`), they can be stored in different databases. Each `querySpace` corresponds to a `nopDataSource_{querySpace}` data source configuration.

However, when loading entities based on their primary and foreign keys, cross-database queries are automatically performed. A single EQL query is only allowed to access one database, so cross-database queries are not supported.

### 10. Where is the documentation for the expression syntax in report templates located?

Documentation for reporting-related usage can be found in:
- `[report.md](../user-guide/report.md)` (for general reports)
- `[xpt-report.md](../dev-guide/report/index.md)` (for XPT-specific reports)

The syntax of expressions in report templates is similar to standard XLang expressions, which are based on JavaScript. However, they include `xptRt` environment variables and some extended functions.

### 11. After modifying the Excel data model, do you need to call `mvn clean install` to regenerate code?

Generally, you don't need to run `mvn clean install`. Only when files are deleted does it become necessary. Additionally, during code generation, a new file named `xxx-CodeGen.java` will be created in the `xxx-codegen` sub-project (e.g., `nop-auth/NopAuthCodeGen.java`), which can be directly executed in IDEA.

The effect is equivalent to running `mvn install`.

### 12. Where do variables used in XPL templates come from?

**Question:** Variables used in XPL templates come from where?
- **Answer:** These variables are set within the `XGenerator` class using `$scope.setLocalValue()`. They can be accessed in the `xrun` file, including both predefined variables and context-scope variables.
- **Note:** All variables must be passed as parameters to the tag library. The scope variable (`$scope`) cannot be directly accessed from the template; it must be injected using `<x:scope>...</x:scope>`.

**Example:**
```xml
<c:script>
codeGenerator.renderModel('/nop/schema/excel/workbook.xdef','/nop/templates/xdsl', '/',$scope);
</c:script>
```

In this example, `$scope` is injected into the template and used within the `renderModel` method. The `xrun` file can access variables defined in both the template and the scope context.

### 13. How to Use Query Conditions Similar to "Like" in the Frontend

See [xview.md](../dev-guide/xui/xview.md)

* Configuration in meta allows for `contains` filtering operations.

```xml
<prop name="userName" allowFilterOp="eq,contains" xui:defaultFilterOp="contains"/>
```

This condition indicates that `userName` allows filtering using the `eq` and `contains` operators. `eq` represents equality conditions, while `contains` represents containment. Both can be implemented using the `like` operator.

* Front-end filter conditions
  - The condition is constructed as `filter_{propName}_{filterOp}={value}`, e.g., `filter_userName_contains=abc`.

### 14. Why @Inject Has Not Worked

```javascript
@Inject
private MyBean myBean;
```

NopIoC does not support injecting private variables. Currently, Spring also does not recommend using `@Inject` for private variables because it complicates compile-time IoC handling and breaks encapsulation. It is better to use `protected` variables or define public `set` methods.

### 15. Loading Specified beans.xml Files Instead of System-Inbuilt app-xxx.beans.xml

NopIoC's entry files are designed to automatically discover all dependencies, including those specified in `beans.xml`. You can place other files within the auto-discovery `beans.xml`.

* Note: In NopIoC, repeatedly importing the same file will automatically deduplicate it, which is an advantage over Spring. In Spring, each import statement adds a new dependency, potentially causing conflicts.
  - Importing the same package multiple times in multiple files is equivalent to importing it once.

```xml
<import resource="a.beans.xml" />
```

### 16. Can xdef Be Used Independently?

Yes, `xdef` can be used independently. The Nop platform's modules are designed to work with other frameworks due to their low coupling and dependency injection mechanisms. Specific module dependencies are defined in the `module-dependency.md` file.

### 17. Properties Like "ext:dict" in app.orm.xml

In `app.orm.xml`, properties like `ext:dict="obj/LitemallBrand"` may not be found in `xdef`. Can undefined attributes in `xdef` be added freely?

Yes, you can add any attribute with a namespace. According to the reversible design principle, any data definition inherently requires its delta extension definition. Thus, we always leave room for storing extension information locally.

The `ext` namespace is typically used for temporary or experimental purposes. If an extension is frequently used, define a dedicated namespace in `xdef` and validate it there.

```xml
<schema xdef:check-ns="graphql,ui,biz" ...>
  <props>
    <prop ui:control="xml-name" graphql:connectionProp="prop-name" ...>Value</prop>
  </props>
</schema>
```

### 18. Do We Need Special Configuration for Cascading Insert and Update?

No, standard ORM operations handle cascading insert and update automatically. The Hibernate cascade configuration is unnecessary because it uses the `action` concept for updates, which should be replaced with state detection.

For example:
- `entity.getChildren().addChild(child)` automatically inserts a record in the child table.
- `entity.relatedTable.setMyProp(3)` updates the `my_prop` field in the related table to 3.

With an ORM tool, ideal usage is to perform operations directly on entities. The ORM tracks entity attributes and generates corresponding SQL statements for insert, update, or delete when changes are detected.


The process of converting differences into corresponding SQL statements resembles the virtual DOM diff process.

---


### 19. Why is the src directory empty when generating a xx-meta module? Because no meta files are generated based on orm.xml?

Meta files are generated by the exec-maven-plugin plugin during the postcompile phase using templates. Therefore, the pom file must be configured with this plugin.
In general, we inherit from nop-entropy's pom file to minimize maven plugin configuration.

---


### 20. Why are `@SingleSession` and `@Transactional` annotations added to Java classes? Because no AOP classes are automatically generated?

AOP classes are generated by the exec-maven-plugin plugin during the build process. Therefore, the pom file must be configured with this plugin.
If we inherit from nop-entropy's root pom, we only need to add the exec-maven-plugin plugin.

---


### 21. Does the Excel engine support nested calls in Excel formulas? How can I add my own report function?

Manually writing a top-down expression parser requires over 1,000 lines of code. The nop-xlang package provides a basic expression parser that can be customized using feature flags.
The following syntax features are available:

```
public static final int ALL = LAMBDA_FUNCTION | FUNCTION_DEF | STATEMENT | FUNCTION_CALL | OBJECT_CALL | BIT_OP
| SELF_ASSIGN | CP_EXPR | TAG_FUNC | JSON | OBJECT_PROP | ARRAY_INDEX | SELF_INC | IMPORT | NEW;
```

The ExcelFormulaParser class inherits from SimpleExprParser and can be extended to identify expression patterns for coordinate-based calculations.


The NopReport contains a limited number of built-in Excel functions, all defined within the `ReportFunctions` class. If additional functions are needed, they can be manually added as static methods in this class.


### #22. Using RedisDataSource in Quarkus

In the context of dependency injection managed by NopIoC, you can inject `RedisDataSource` into your Quarkus application using the `@Inject` annotation. However, please note that Quarkus handles IoC at compile time through its own scanning and registration mechanisms. For example:

```java
@ApplicationScoped
public class QuarkusConfig {
    @Inject
    RedisDataSource redisDataSource;
}
```


### #23. Dictionary Fields in Labels

When a dictionary value-key pair is automatically added to the label, it appears as "Dictionary Value - Dictionary Name". If you want to remove only the key while keeping the name, set `return-normalized-label` to `false`.


### #24. Differences Between save and update Methods in CrudBizModel

- **update**: Requires an `id` attribute to identify a modification operation.
- **save**: Represents a new insertion operation, which is translated into an `INSERT` statement.

The method `save_update` checks if an `id` exists to determine whether it's an update or insert operation.


### #25. action-auth.xml vs. Graphql and xbiz

When using both `action-auth.xml` and xbiz in NopPlatform, the following rules apply:
1. **Priority**: xmeta (extensions) have higher priority than xbiz.
2. **Conflicts**: If both are defined, xmeta takes precedence.


### #26. Handling Many-to-Many Relationships with Delta Customization

In `delta.orm.xlsx`, for a many-to-many relationship between `NopAuthUser` and `Merchant`, you need to:
1. Add the association table in your business model.
2. Annotate it as `@NotGen` to prevent automatic generation.

This approach avoids generating helper methods for the association, which can be added manually if needed.


### #27. Differences Between nop-ooxml-xlsx and Java's POI

- **Nop-ooxml-xlsx**: Lightweight XML parser implemented in NopPlatform, optimized for performance and size.
- **POI**: Full-featured library requiring significant memory (tens of MB) and slower processing.


### #28. Customizing System Fields like updateTime

You can rename `updateTime` to `updatedAt` by configuring the `return-normalized-label` setting in your ORM configuration.


### #29. Anonymous Users in Auth Module

All authenticated users have a default role (`role.user`). If you need anonymous access, consider extending `AuthHttpServerFilter`.


### #30. Customizing DSL with gen and post Commands

DSL files support custom extensions using:
- `x:gen-extends` for pre-processing
- `x:post-extends` for post-processing

These can be nested within the generator to achieve complex customizations.

# Push Mode
- **Description**: In the Push Mode, `mvn install` triggers the precompile directory's `xgen`, generating code and compiling it.

# Pull Mode: x:gen-extends
- **Description**: In the Pull Mode, when loading models, the `x:gen-extends` script is triggered to generate code and compile it.

# What is querySpace in NopORM?
- **Definition**: A Query Space in NopORM refers to a configuration for multiple data sources. Each Query Space can correspond to a different database or storage mechanism. For example:
  - Some data may reside in Elasticsearch,
  - While other data may be stored in another database.

# Relationship Between IContext and IServiceContext
- **IContext**: This is typically an synchronous context, often found in API Core (`api-core` package). It provides basic global information and is used throughout the system.

- **IServiceContext**: This is an extension of the context provided by the service framework. It includes more detailed information such as `IEvalScope` (used for evaluating expressions) and `IUserContext` (for user-related data), which are typically handled in the service layer and depend on the `nop-core` module.

- **Usage**: When a request comes in, an `IContext` is created, bound to the thread, and passed to the transaction or session. The GraphQL engine then creates an `IServiceContext`, referencing the existing `IContext`, and passing along additional information.

# How does EQL Support cast(value as date)?
- ** limitation**: EQL does not support the `cast(value as date)` syntax.

- **Workaround**: Use the `date(field)` function instead. This function is defined in the `dialect.xml` file, allowing you to extend it with additional date-related functions.

# Do we need a sequence for primary keys?
- **No Sequence Needed**: If no sequence is set, you must manually assign primary keys.

- **Using Sequences**: By setting a sequence (e.g., using `nop_sys_sequence`), Nop can generate sequential IDs if the corresponding entity record doesn't exist.

# Global Sequence in Nop
- **Global Sequence Usage**: Using a global sequence (`seq-default`) ensures all entities use the same, shared sequence for ID generation. This avoids conflicts when multiple components try to generate IDs simultaneously.

# Is it necessary to have a seq label for primary keys?
- **No**: Nop does not require a sequence for primary keys. The platform supports distributed databases and uses incremental IDs or UUIDs by default.

# How to Ensure Consistent Primary Key Assignment
- **Using Sequences**: If sequences are defined, each entity will use the next available ID in its own sequence if the corresponding record exists. Otherwise, it generates a random ID.

# Nop and Distributed Databases
- **Distributed Support**: Nop is designed to work with distributed databases by using sequential IDs or UUIDs for primary keys.

# Does NopORM Track Modified Attributes?
- **No Tracking by Default**: NopORM does not track modified attributes unless enabled explicitly in the configuration.

# How to Manually Generate IDs
- **Manual Assignment**: If no sequence is defined, you can manually assign primary keys using the Nop ORM API. For example:
  ```java
  repo.save(new Entity()).persist();
  ```

# Why Doesn't nop-cli Generate xmeta Files?
- **Reasoning**: `nop-cli` does not automatically generate `xmeta.xml` because XMeta configurations are typically defined in `orm.xml`. The CLI simply provides a starting point for generating these configurations manually.

# Example: Generating Code with XXXWebCodeGen
```java
public class XXXWebCodeGen {
    public static void main(String[] args) {
        // Code generation logic here
    }
}
```

# How Does XScript Handle Type Conversion?
- **Built-in Functions**: `XScript` supports built-in functions like `$toInt`, `$toString`, etc. These can be extended by registering custom type converters in the `SysConverterRegistry`.

- **Default Handling**: If no converter is registered, a default implementation will convert strings to appropriate types, using default values if conversion fails.

# Does NopGraphQL Support Map<String, MyEntity>?
- **No Support for Maps**: NopGraphQL only supports scalar types and objects. It does not support `Map<String, MyEntity>` structures due to the inherent uncertainty in key formatting.

# Why Can't I Use a Map in GraphQL Queries?
- **Reasoning**: GraphQL schemas typically do not allow for arbitrary or dynamic keys in maps due to their unpredictability and potential impact on performance.

# Ending Notes
```java
@DataBean
public class MyResponseBean {
    private MyEntity myEntity;

    public MyEntity getMyEntity() {
        return myEntity;
    }

    public void setMyEntity(MyEntity myEntity) {
        this.myEntity = myEntity;
    }
}
```

Additionally, it can return `List<MyEntity>` such as in GraphQL. GraphQL identifies object types and list types.

### 39. Why would NopGraphQL encounter errors when using a custom generic class `MyPageBean<T>`?
For custom data types, NopGraphQL does not support generics natively. Therefore, you need to create a derived class that returns data. For each type like PageBean or ApiResponse, special handling is required.

```java
public MyUserPageBean findAll() {
    ...
}

class MyUserPageBean extends MyPageBean<User> {}
```

### 40. Why doesn't `logInfo` print the data?
The `logInfo` method does not output data because it requires a specific format. For example:

```javascript
logInfo("data: {}", data);
```

Here, the first parameter is a template message string, and you need to use `{}` for variable substitution with the slf4j logging framework.

### 41. How are existing view files in the `_delta` directory handled? Are they overwritten or merged?
They are overwritten. To merge, you must explicitly mark them with `x:extends="super"` in the root node. DSL files have self-explanatory nature and their XDef model and inherited base model can be inferred from the file itself.

### 42. How is merging handled when multiple customizations exist? For example:
- NopAuthUserView
- Framework customization -> Product customization -> Derived product customization

Nop platform uses a unified virtual file system to manage all DSL files. You can define multiple hierarchical delta directories and configure them using `nop.core.vfs.delta-layer-ids`. For example:

```properties
nop.core.vfs.delta-layer-ids=deploy,product
```

This means the `_delta/deploy` directory will override the `_delta/product` directory, which in turn overrides files not in a delta directory.

### Deployment Issues

### Design Issues

### 1. What does "function abstraction" and "function templating" mean in Nop platform documentation?
Function abstraction refers to abstracting functionality into reusable components or steps. For example:

```xml
<step>
    <when class="xxx.MyCondition" />
</step>
```

In workflow engines, some might implement conditional logic with plugins. However, in Nop platform, function abstraction is isolated from the workflow engine's design. You only need to define function abstraction; how it is implemented is a separate concern and does not depend on the workflow engine.

Nop platform uses xpl template language for function templating, which abstracts function structures into templates.

### 2. How is the Nop platform handling workflow steps?
In Nop platform:
1. Use `x:extends="super"` in root node to merge.
2. DSL files are self-explanatory and define their own XDef model and inheritance.

Nop platform does not require plugin architecture for workflow engines. Instead, it uses function abstraction and templating based on xpl.

### 3. How is conditional logic handled in Nop platform?
In Java:

```java
if(user.age < 18)
    return false;

if(user.gender == 1)
    return false;

return true;
```

This logic can be implemented using the Nop platform's XPL template language for function templating.

# Translation of Technical Document

## 1. Converting to Abstract Syntax Tree and XML Format
We can convert the data into an abstract syntax tree (AST) and then save it in XML format. However, the process is not very intuitive.
Using the abstraction mechanism provided by the Xpl template language, we encapsulate specific logic into more business-oriented tag functions and provide a visual editor.
This is similar to how we configure custom components in form editing, rather than working with low-level `div/span` nodes.

```xml
<and>
    <app:already_adults/>
    <app:gender_male/>
</and>
```

## 2. Custom .xlib File Usage
When would we define our own `.xlib` file? Should the xlib files be placed in a fixed location?
The `xlib` directory is where custom libraries for the Xpl template language are stored. You can place xlib files anywhere, but it's standard practice to store them in the module's `xlib` directory. When working within an Xpl segment, use `import` statements to access xlib functions.

### Common Intrinsic Tag Libraries
1. `web.xlib`: Generates pages based on the `xview` model.
2. `control.xlib`: Handles field derivation for display controls.

## 3. Defining BizModel Without Entity Definition
Is it possible to create a BizModel without defining entities?
Yes, it's possible under Nop's layered architecture. The lower layers are independent of each other. You can write your own BizModel class, as seen in `LoginApiBizModel`.

```
Excel --> ORM --> BizModel --> XView --> Page
```

Using the code generator in the Nop platform, we can derive page elements from an Excel data model step by step. Each derivation step is optional and does not depend on previous steps.

## 4. Implementation of BizModel
For example, referencing `LoginApiBizModel`:
- Write a `BizModel` class directly in Java.
- Register it in a `beans.xml` file.
- Use the NopGraphQL engine to query.

> **Note:** Implementing `ILoginSpi` is optional for code clarity and maintainability. The `BizModel` class doesn't require inheritance or interface implementation, resembling a `Controller` in Spring but independent of any runtime environment.

## 5. Nop Platform Development Direction
Is the focus on a domain-specific workbench, a low-code platform, or Spring Cloud framework?
Nop's goal is to become a general-purpose domain language workbench, with other tools and frameworks serving as secondary features. The platform supports rapid development and expansion of domain languages, allowing automatic generation of a designer and parser. This approach leads to a bottom-up implementation based on the reversible computing principle, where code generators create 20,000+ lines of boilerplate code.

## 6. Understanding Low-Code Platforms
How is Nop's low-code platform understood in comparison with domain-specific languages (DSLs)?
I focus on general-purpose descriptive programming rather than low-code products. From my article on reversible computing and low-code ([From Reversible Computing to LowCode](https://zhuanlan.zhihu.com/p/344845973)), low-code development mirrors model-driven development, where DSL is used for domain-specific modeling.

The Turing machine's ability to simulate all other machines is fundamental. By continually increasing the level of abstraction in a virtual machine, we aim to create a DSL for domain-specific operations. However, this approach leads to differential understanding and potential information overflow, resulting in a Delta item.

### The Evolution of Programming Languages and the Emergence of Domain-Specific Languages (DSLs)

In the development of programming languages from Generation 1 to 3, there has been a continuous progression in abstracting layers. However, they remain general-purpose programming languages. With the advent of Generation 4, we are likely to encounter not another general-purpose programming language but a forest of Domain-Specific Languages (DSLs) constructed from numerous domain-specific languages.

These DSLs enable us to form a new representation and understanding of existing program structures.

---

### Question on Excel Model's AppName

Is the appName in the Excel model required to be two-level? If it is one-level or three-level, what issues would that pose? The current restriction seems unusual. Additionally, it automatically becomes a two-level directory name. Here, does this refer to the package directories under `src`, specifically within the virtual file system?

The current restriction is set to two levels because during platform initialization, there's a module scanning process. To limit the scope of this module scanning, only two-level directories are scanned. For instance, if `/_vfs/xxx/yyy/_module` files exist, they are considered modules, and their `app-*.beans.xml` files under the `beans` directory are automatically loaded. Similarly, `module:/abc/yy.xml` is used to load all module-related configuration files, such as `/_vfs/xxx/yyy/abc/yy.xml`.

---

### Can Nop Platform's GraphQL Be Used Independently of Spring and Quarkus Frameworks?

Nop platform's GraphQL engine is a pure logic implementation. It essentially functions as a generic service decomposition mechanism applicable across all `request=>response` processing workflows.

GraphQLWebService provides a JAX-RS-based integration with the web layer. Specific implementations, such as QuarkusGraphQLWebService and SpringGraphQLWebService, are derived from this base class to handle framework-specific details. This allows for both `/graphql` and `/r/{operationName}` types of external interfaces to be exposed.

If using Vert.x or Netty for HTTP protocol adaptation, the dependency on Quarkus or Spring can be avoided.

Since NopGraphQL's entry parameters and return values are POJOs, it is directly applicable in scenarios involving batch processing engines and Kafka message queues, even in streaming frameworks. If you only want to use the backend without introducing AMIS (Application Mobility Integrated Services), you can choose between:
- `nop-spring-web-orm-starter` (includes GraphQL and ORM)
- `nop-spring-core-starter` (basic XLang support)
- `nop-quarkus-web-orm-starter` or `nop-quarkus-core-starter`

The starter provides automatic integration with Spring and Quarkus frameworks. By adding the appropriate dependencies, the platform will automatically invoke `CoreInitialization.initialize()` during startup.

---

### Is Nop Platform's Logic Arrangement Related to Code Logic or Service Call Logic? Similar to Zeebe for Microservices Orchestration

Logic arrangement engines should not concern themselves with whether it is code logic or service call logic. That's implementation detail. When a microservice is called, it is essentially represented as a function. As long as you can orchestrate functions, you can orchestrate services. However, services may have additional management logic beyond what is required for orchestration. This logic should not interfere with the orchestration engine.

Nop platform leverages template languages and DSLs for orchestration. Below the surface lies the infinite extensibility of XPL (eXtensible Process Language). Mechanisms like retries, TCC (Transactional Consistency Control), etc., can be represented as decorators.

---

### Implementing Light Association Mechanism

Can we implement light association and light filling functionalities? For example:
- Field default values can reference other fields or directly use EL expressions like `${user.userId}`. The associated table and field are not inherently linked, nor do they require complex association relationships.
- No need to define complex associations for simple requirements.

---

### Solutions

1. **CrudBizModel** provides a comprehensive set of generic CRUD operations without manual coding. It leverages GraphQL's renaming mechanism for field adaptation. For example:
   - `/r/NopAuthUser__get?id=3&@selection=name:userName,status:userStatus` can query data based on `id`, selecting specific fields.
2. **Meta** files allow custom props, which can be further configured in getters using XScript without requiring XBiz files.
3. If using NopDynEntity, you can define backend service functions directly in the configuration, enabling flexible data retrieval from the backend.

In a decoupled architecture:
- Light association is handled on the frontend by configuring services and actions. The backend remains unaware of these associations.
- Service function definitions are made on the frontend, with no impact on the orchestration engine's functionality.

# Modularization in the Nop Platform

The naming convention for backend service functions in the Nop platform is standardized as `/r/{bizObjName}_{bizMethodName}?@selection={selectionSet}`.

With a data provider, the frontend can freely decide whether to use it as the default value or fixed binding.

### 9. Modularization in the Nop Platform

The concept of sub-systems is implemented in the Nop platform as three independent subsystems: finance, CRM, and HR.

After adopting a microservices architecture, modularization is further achieved by service division. The Nop platform internally supports a two-level module system, distinguishing modules via virtual file system subdirectories such as `/nop/auth` corresponding to `{vendor}/{subModule}`.

For rationalizing module paths, the following patterns are implemented: `ap/fin`, `app/crm`, `app/hr`. These represent three independent modules that can be developed and managed separately.

The Nop platform follows a modular and composite design. If `app/fin` is introduced as a module, it automatically includes its own functionality. Each sub-module can either be deployed as an individual `.exe` or bundled into a single `.exe`.

From a conceptual perspective, the Nop platform resembles a micro-nucleus architecture, allowing dynamic loading and unloading of business modules. However, in the current implementation, if a module contains Java classes, a restart is required. This can be avoided by using a separate `Java ClassLoader` for module loading.

In development mode, the Quarkus framework includes a hot load mechanism. Changes to Java code are automatically reloaded without requiring a restart when developed using Nop's standard modules.

### 10. Flexibility and Extensibility of Nop

The Nop platform's flexibility is evident in its fine-grained modularization. When other parties extend the system above, how can we prevent improper configurations from affecting functionality? For example, if a specific bean is removed, it may disrupt overall functionality.

Nop primarily addresses structural and transformational issues. The semantic space must be ensured through constraints and validations. Frameworks like ORM perform field-level validations on data before storage.

### 11. Technical Details

1. xdef: Definition models require standardized formats, which are validated after merging.
2. IoC Container: Automatically checks dependencies during `mvn install`, including bean relationships.
3. ORM: Checks all table references and attribute mappings for correctness.
4. XView: Validates field references; if not marked as custom, they must be defined in XMeta.
5. In summary, various engines perform semantic checks based on their own requirements, similar to enhanced type checks.

### 12. Excel Model Configuration

In the Excel model, fields are marked with `not-pub`, which affects how they are generated. At the meta level, this is a binary decision: whether to publish or not. While ORM layers generally shouldn't need to know whether to publish, some configurations store this information in `tagSet`.

### 13. Core Formula for Reversible Computation

The core formula is defined as:
`App = Delta x-extends Generator<DSL>`

In Nop, the `Generator` concept corresponds to a theoretical abstraction that maps to concrete mechanisms in the platform.

Reverse computation involves merging at the structural level. This process occurs within the structure where all possible structures are considered. The structure layer represents the feasible space of all possible structures.

This concept is analogous to quantum mechanics, where phenomena outside observation can violate classical laws but still operate within a self-consistent framework. After merging, true observability requires passing through layers of constraints.

Nop's design includes multi-stage compilation. Before actual execution, the platform provides extensive validation and intermediate checks, including aspects like contract-based programming and unit testing.

In traditional programming, structural encapsulation is rigid, determined solely by the compiler. Nop opens this structure, allowing custom rules at various levels, from data binding to business logic.

Frontend tools like Babel offer limited compiler access, but only a few frameworks fully expose this capability. Nop breaks this limitation by providing a comprehensive framework for defining and applying rules.

### 11. Why Nop Doesn't Support Class Scanning

Built-in scanning disrupts customizability. Implementing a `scan` tag function within the `x:gen-extends` segment, similar to Spring 2.0's conventions, would require significant performance trade-offs. However, Nop does not include this feature natively.

### 12. Excel Model Usage of not-pub Tags

In the Excel model, the `not-pub` tag determines whether a field is generated with `published=false`. At the meta level, this decision must be explicitly defined. While ORM layers typically shouldn't need to know about publishing decisions, some configurations store this information in `tagSet`.

### 13. Core Formula for Reversible Computation

The core formula is defined as:
`App = Delta x-extends Generator<DSL>`

In Nop, the `Generator` concept corresponds to a theoretical abstraction that maps to concrete mechanisms in the platform.

Reverse computation involves merging at the structural level. This process occurs within the structure where all possible structures are considered. The structure layer represents the feasible space of all possible structures.

This concept is analogous to quantum mechanics, where phenomena outside observation can violate classical laws but still operate within a self-consistent framework. After merging, true observability requires passing through layers of constraints.

Nop's design includes multi-stage compilation. Before actual execution, the platform provides extensive validation and intermediate checks, including aspects like contract-based programming and unit testing.

In traditional programming, structural encapsulation is rigid, determined solely by the compiler. Nop opens this structure, allowing custom rules at various levels, from data binding to business logic.

Frontend tools like Babel offer limited compiler access, but only a few frameworks fully expose this capability. Nop breaks this limitation by providing a comprehensive framework for defining and applying rules.

### 1. XCodeGenerator代码生成工具
The **XCodeGenerator** code generation tool can generate code based on an Excel model. During generation, it automatically covers all files in the `_gen` directory, including those with names prefixed with underscores. Handwritten code inherits from automatically generated code for differential customization.

---

### 2. XDSL文件中的动态代码生成
All **XDSL** files support `x:gen-extends` and `x:post-extends` dynamic code generation segments. Within these, you can use the `xpl` template language to dynamically generate model nodes and then merge them with external nodes for differential merging.

```xml
<model x:extends="A,B">
  <x:gen-extends>
    <my:GenC/>
    <my:GenD/>
  </x:gen-extends>
  <x:post-extends>
    <my:GenE/>
  </x:post-extends>
  ...
</model>
```

The merging strategy is **Result = E x-extends Model x-extends D x-extends C x-extends B x-extends A**, where `Axml` refers to the XML format model file, generated code with underscores at the beginning, handwritten code, and the **Generator** tool. The model files correspond to DSL, so the **Generator** tool corresponds to `Genetator`. If not, then `Genetator` in the formula corresponds to which parts of Nop?

---

### 14. 企业级防御性编程
In enterprise-level defense programming, like **Nop**, it is essential to design a防腐层 (defense layer). Is a防腐层 specifically for Nop necessary?

Nop itself supports differential customization without modifying its source code. This significantly reduces the need for a defense layer. Furthermore, general programming cannot achieve the level of protection that Nop's **NopGraphQL** and **NopORM** provide. Designing a defense layer for Nop is challenging because it offers minimal information leakage. Additionally, Nop's dependency on third-party packages is minimal, so upgrading them does not affect its application side.

Therefore, designing a defense layer for Nop is generally unnecessary.
For example, Nop's XML parser does not use the JDK's built-in parser but instead uses a custom one that is high performance and free from vulnerabilities caused by complex features.

---

