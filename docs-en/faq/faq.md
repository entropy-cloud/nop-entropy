# FAQ

## Development Questions

### 1. After modifying a column name and saving, the change does not appear on the frontend list page after a refresh. However, the designer shows it as modified.

After saving to the backend, you may find that page.yaml contains a label with an i18n key. On the frontend, the label is actually replaced by the internationalized text.

```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthDept.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
body:
  name: crud-grid
  columns:
    - name: deptType
      label: '@i18n:col.NopAuthDept.deptType,prop.label.NopAuthDept.deptType|Change Type'
      placeholder: '-'
      x:virtual: true
  x:virtual: true
```

In the `@i18n:key|defaultValue` format, the part after the `|` is the default value. It is returned only when the i18n key has no corresponding internationalized text.

When previewing in the designer, i18n keys are not replaced because it is the design phase. If you want to strictly use the modified value, delete the i18n key.

```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthDept.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
body:
  name: crud-grid
  columns:
    - name: deptType
      label: 'Change Type'
      placeholder: '-'
      x:virtual: true
  x:virtual: true
```

If you modify in the designer, you need to manually delete it in the JSON view.

![](remove-i18n-key.png)

### 2. How to construct Query conditions in GraphQL

On the frontend, you can use simplified query syntax like `url："@query:NopAuthDept__findList/id?filter_deptName=a"`. When executing an object's findList or findPage, parameters prefixed with `filter_` are recognized and converted into the QueryBean filter tree structure. If you must manually construct a QueryBean, you can follow the example below:

```graphql
query($query:QueryBeanInput,$q2:QueryBeanInput){
  NopAuthDept__findList(query:$query) {
    id,
    deptName
    parent {
      id
    }
  },

  NopAuthUser__findPage(query:$q2){
    page
    items{
      nickName
      userName
    }
  }
}
```

Set variables to:

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

### 3. How to run unit tests without using a local database

See [autotest.md](../dev-guide/autotest.md) for an introduction to automated testing.

1. Use an in-memory database with NopOrm’s underlying data-layer record/replay mechanism.
2. If you don’t need in-memory database support, you can extend JunitBaseTestCase to implement pure logic tests; it only starts the IoC container. If IoC is also not needed, extend BaseTestCase, which only provides some helper functions.

### 4. When the application is running and model files in the virtual file system are updated, will it auto-refresh? Or do we need to restart the application?

Nop uses ResourceComponentManager to load model files, and the loaded models are cached in memory. ResourceLoadingCache has built-in dependency tracking: it automatically records all dependent model files used during parsing. If any file changes (timestamp changes), the model cache is invalidated and the model is re-parsed upon next access.

For newly generated files, the virtual file system does not automatically scan to detect them. You need to call VirtualFileSystem.instance().refresh(true).
The virtual file system not only includes the _vfs directory under the classpath, but also automatically includes the _vfs directory under the current working directory at system startup. The _vfs directory under the current working directory has higher priority; its files override the classpath files.

On the frontend page, there is a “Refresh Cache” button that clears the global model cache on the backend and automatically refreshes the virtual file system.

### 4. Can we get web environment objects like request and response from IServiceContext?

NopGraphQL is designed to be web-agnostic. It can be used in message queues, batch processing, etc., as a general service dispatch and result aggregation framework, therefore it provides no web-specific methods.

In IServiceContext, you can store custom objects via setAttribute/getAttribute methods. IServiceContext.getCache() provides a cache object valid within a single request scope, which can be used to cache dictionary data, etc. Business parameters should generally be passed explicitly; IServiceContext is essentially equivalent to a Map used mainly to store shared information within a single request inside the framework.

### 5. Besides single sign-on, can Nop integrate with Keycloak for authorization features?

Roles are integrated. You can use roles configured in Keycloak, but role-to-permission associations must be configured in the Nop platform. Keycloak can configure user-to-role associations; see OAuthLoginServiceImpl.java.

### 6. When are model classes like `_ExcelWorkbook` generated?

Model classes are generated by a code generator during Maven packaging.

When executing mvn package, the exec-maven-plugin runs. In the root pom.xml of the nop-entropy project’s pluginManagement, the following config is included:

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
        Avoid including META-INF which could load uncompiled ICoreInitializer
        -->
        <addResourcesToClasspath>false</addResourcesToClasspath>
        <addOutputToClasspath>false</addOutputToClasspath>
      </configuration>
    </execution>
    ...
  </executions>
</plugin>
```

In the nop-excel project’s pom, including the exec-maven-plugin automatically executes the code generation scripts under the precompile directory:

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

All files with the xgen suffix under the precompile directory are executed automatically:

```xml
<c:script>
  codeGenerator.renderModel('/nop/schema/excel/workbook.xdef','/nop/templates/xdsl', '/',$scope);
  codeGenerator.renderModel('/nop/schema/excel/imp.xdef','/nop/templates/xdsl', '/',$scope);
</c:script>
```

The renderModel function on XCodeGenerator can read model files and execute code generation templates. In the example above, it reads xdef meta-model definitions and invokes templates under /nop/templates/xdsl in the nop-codegen module.

### 7. When does the system automatically create tables?

With nop.orm.init-database-schema=true configured, DatabaseSchemaInitializer will auto-create database tables when the application starts.
If a CREATE TABLE statement fails, errors are ignored and subsequent statements are not executed. In other words, if the database is empty, tables will be created successfully; if tables already exist, CREATE failures will be ignored.

If you only add a few new tables, currently they are not automatically detected and created. You can set the log level to TRACE; table creation failures will print logs.

### 8. If reverse-engineering multiple databases results in multiple Excels, should we merge them into a single Excel for code generation?

Each Excel corresponds to one module in the data model. It is inherently a multi-module system. It can be deployed as microservices or bundled as a monolithic application.
Built-in modules like nop-auth and nop-wf are each submodules with their own models. Every module can be developed and debugged independently.
If tables reference each other across modules, you can add external table references in Excel (set the table label to not-gen).

### 9. Can ORM store tables in different databases?

If you assign different querySpace values to different entities, they can reside in different databases. Each querySpace can map to a DataSource configuration named nopDataSource_{querySpace}.

Entity loading by primary/foreign keys supports cross-database queries; however, a single EQL query statement can access only one database and will not automatically perform cross-database queries.

### 10. Where is the documentation for the expression syntax in report templates?

See report usage documentation [report.md](../user-guide/report.md) and [xpt-report.md](../dev-guide/report/index.md).
Report expressions are similar to ordinary XLang Expressions (JavaScript-like syntax), with the xptRt runtime variable and some extended functions.

### 11. After modifying the Excel data model, do we need to run mvn clean install to regenerate code?

Generally, no clean is needed—only if files were deleted.
Additionally, code generation will create a xxxCodeGen.java in the xxx-codegen subproject (e.g., NopAuthCodeGen.java in nop-auth) that can be run directly in IDEA to generate code, equivalent to running mvn install.

### 12. Where do variables used in XPL templates come from?

Question: In the codegen module’s ORM template, `@init.xrun` calls the `gen:DefineLoopForOrm` tag. In its definition,
`<attr name="codeGenModel" implicit="true"/>`,
the codeGenModel attribute is implicit, and `@init.xrun` does not pass it. Where does its value come from?

Answer: This variable is stored via scope.setLocalValue in XGenerator. In xrun files, in addition to defined variables, you can access variables passed via the scope context.
In tag libraries, all variables must be passed as parameters; tags cannot directly access scope variables. Therefore, implicit=true means the tag captures a variable named codeGenModel from the calling context and uses it as a tag parameter.

### 13. How to use “like”-style query conditions on the frontend

See [xview.md](../dev-guide/xui/xview.md)

- Configure allowed filter ops in meta:

```xml
<prop name="userName" allowFilterOp="eq,contains" xui:defaultFilterOp="contains"/>
```

This means `userName` supports filter operators `eq` (equals) and `contains` (contains, implemented with `like`). `xui:defaultFilterOp` sets the default to `contains`.

- Send filter conditions from the frontend:
  Concatenate conditions as filter_{propName}_{filterOp}={value}, e.g., filter_userName__contains=abc

### 14. Why did Inject bean fail?

```javascript
@Inject
private MyBean myBean;
```

NopIoC does not support injection into private fields. Spring also no longer recommends this because private fields hinder compile-time IoC processing and break encapsulation.
Use package-protected or protected fields, or define a public setter.

### 15. Besides the system’s auto-loaded `app-xxx.beans.xml`, how to load a specific beans.xml file?

Entry beans in NopIoC are all auto-discovered. You can import other files in auto-discovered beans.xml.

- Note: NopIoC automatically de-duplicates multiple imports of the same file, which is better than Spring’s handling. In Spring, the import node is include semantics rather than the language-level import semantics. Importing the same package multiple times in different files should be equivalent to importing once; include semantics fully includes the file each time, causing bean definition conflicts.

```xml
<import resource="a.beans.xml"/>
```

### 16. Can xdef be used standalone?

Yes. Add nop-xlang. The code generator can also be used independently of the Nop platform. Although the Nop platform has many modules, because the overall design uses dependency injection and dynamic loading, modules are loosely coupled and most can be used standalone and integrated with other frameworks independently of Nop.

For module dependency relationships, see [module-dependency.md](../arch/module-dependency.md)

### 17. In the generated app.orm.xml, there is a property like ext:dict="obj/LitemallBrand". This ext:dict cannot be found in xdef. Can we arbitrarily add attributes not defined in xdef?

Yes. You can freely add extension attributes with namespaces. According to Reversible Computation, any data definition necessarily comes with its Delta extension definition. We always reserve a mechanism to store extension information locally; it’s always a paired design of `(data, meta-data)`. Here, meta-data essentially stores extension information.

The ext namespace is generally used for temporary extension attributes. If an extension attribute is frequently used, choose a dedicated namespace and specify validation on the xdef model so that the extension attributes must also satisfy the xdef meta-model requirements.

```
<schema xdef:check-ns="graphql,ui,biz" ...>
  <props>
     <prop  ui:control="xml-name" graphql:connectionProp="prop-name" ...> </prop>
  </props>
</schema>
```

### 18. Do we need special configuration for cascading insert and update?

No. ORM operations at the entity level automatically reflect to the database. There should not fundamentally be “cascading update” problems. Hibernate’s cascade configuration misuses the action concept to drive modifications; it should use state detection to drive modifications. As long as it detects that entity-level properties have changed, it automatically converts to Insert/Update/Delete SQL.

For example, `entity.getChildren().addChild(child)` implies inserting into the child table.
`entity.relatedTable.setMyProp(3)` implies setting the my_prop field on the related table to 3.

If using the ORM engine, ideally you operate only at the entity level. The ORM engine tracks current property values of entities, automatically computes the Delta between those values and the database tables, and converts the Delta into corresponding SQL statements. The entire process is analogous to frontend virtual DOM diff.

### 19. Why is the src directory empty in a newly generated xx-meta module, and meta files were not generated from orm.xml?

Meta files are automatically generated by running postcompile code generation templates via exec-maven-plugin. Therefore, the pom must configure this plugin.
Typically we inherit from the nop-entropy pom to reduce Maven plugin configuration:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

  <parent>
    <artifactId>nop-entropy</artifactId>
    <groupId>io.github.entropy-cloud</groupId>
    <version>2.0.0-SNAPSHOT</version>
  </parent>
  ...
  <build>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <configuration>
          <classpathScope>test</classpathScope>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

### 20. After adding annotations like `@SingleSession` and `@Transactional` to Java methods, why weren’t corresponding AOP classes generated?

AOP generation is triggered via exec-maven-plugin. Therefore, the pom must configure exec-maven-plugin. If you inherit the root pom of nop-entropy, just include the plugin:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>exec-maven-plugin</artifactId>
      <configuration>
        <classpathScope>test</classpathScope>
      </configuration>
    </plugin>
  </plugins>
</build>
```

### 21. Does the report engine support nested Excel formula calls? How do I add my own report functions?

A hand-written top-down expression parser needs just over 1,000 lines. The SimpleExprParser in nop-xlang provides a basic expression parser customizable via feature flags.
Optional features:

```
public static final int ALL = LAMBDA_FUNCTION | FUNCTION_DEF | STATEMENT | FUNCTION_CALL | OBJECT_CALL | BIT_OP
| SELF_ASSIGN | CP_EXPR | TAG_FUNC | JSON | OBJECT_PROP | ARRAY_INDEX | SELF_INC | IMPORT | NEW;
```

ExcelFormulaParser inherits from SimpleExprParser, trims features, and adds recognition for report hierarchical coordinate expressions.

Currently, NopReport has only a few built-in Excel functions, all defined in ReportFunctions.
If needed, write static functions yourself and register them similarly to ReportFunctions in ReportFunctionProvider.

### 22. How to use the RedisDataSource defined in Quarkus

In beans managed by NopIoC, you can directly inject Quarkus-managed beans with `@Inject RedisDataSource redisDataSource;`.
Note: Quarkus IoC performs scanning and registration at compile time, so RedisDataSource is auto-discovered and registered only if used in the Quarkus environment. For example, add a QuarkusConfig class:

```java
@ApplicationScoped
public class QuarkusConfig {
  @Inject
  RedisDataSource redisDataSource;
}
```

### 23. For dictionary fields, the returned label automatically includes “value - name”. Is there an easy way to remove “value -” and keep only the name?

Set nop.core.dict.return-normalized-label=false.

### 24. What is the difference between save and update in CrudBizModel?

update requires an id to indicate a modification, while save is an insertion. `save_update` treats calls with id as update; otherwise, as insert.

### 25. Can action-auth.xml also control GraphQL action permissions? Auth annotations and xbiz can also control them. How do we choose, and which one wins if they conflict?

Nop uses a layered overlay design: `overall logic = base logic + Delta customization`
More customizable layers override less variable layers. Definitions in xbiz override those in Java. xmeta has the highest priority.

`action-auth.xml` does not control permissions; it defines grouping names for easier permission management. Actual permission names are defined on service methods.
`action-auth.xml` can configure default bindings between roles and permissions. The backend AuthMeta essentially configures bindings between permissions and service methods; for convenience, you can also bind roles directly to service methods. If both are specified, they are jointly enforced.

### 26. How to handle many-to-many relationships via Delta customization?

Question: In `delta.orm.xlsx`, we customize NopAuthUser for a many-to-many relationship (e.g., users and merchants). We need to generate Java properties for merchants in NopAuthUser, and for users on the merchant side. Should the intermediate association table be defined in the auth delta Excel or in the application module’s Excel?

Answer: It’s recommended to add the many-to-many association table in your own business model and mark the user table as not-gen to reference it as an external table. This avoids auto-generating many-to-many helper functions for NopAuthUserEx; you can add those manually.

### 27. What is the difference between the nop-ooxml-xlsx module and Apache POI?

POI is large (at least a few dozen MB) and slow. `nop-ooxml-xlsx` uses Nop’s own XML parser to parse xlsx; it does not use POI underneath. It is much faster and uses far less memory, but supports fewer features—only those needed in current report development.

### 28. Can system-conventional fields in the Excel model (like updateTime) be renamed, e.g., updateTime to updatedAt?

During code generation, special data domains are recognized: createdBy, updateTime, updatedBy, delFlag, version, createTime, tenantId.
Fields marked with these data domains are automatically recognized for ORM supported features (e.g., optimistic locking, creation time, etc.)
The data domain itself cannot be changed; database column names can be changed.

### 29. Does the auth module have a concept similar to Spring Security’s anonymous user and anonymous role?

All logged-in users have the role user, but currently there is no built-in anonymous user mechanism. If needed, extend AuthHttpServerFilter.

### 30. Besides explicitly running gen commands, when else can code be generated?

Every DSL file supports `x:gen-extends` and `x:post-extends` subnodes; these are embedded generators within the DSL.

- push model: mvn install triggers xgen under the precompile directory to run code generation
- pull model: `x:gen-extends` triggers code generation when loading the model

### 31. What does the querySpace concept in NopORM mean?

It supports multi-data-source configuration. Each querySpace maps to a different database or storage system. For example, some data in ElasticSearch or another database—each data source is a querySpace.

### 32. What is the relationship between IContext and IServiceContext, and their usage scenarios?

IContext mainly provides the async context and includes minimal global info. It is in the api-core package and can exist in various scenarios; it won’t include many other details. Across the Nop platform, ThreadLocal is not directly relied upon; implicit context passing stores objects in IContext, such as the current ITransaction and IOrmSession.

In a service invocation environment, we need to pass more environment info. IServiceContext is an extended service framework context; it includes IEvalScope and IUserContext and must depend on nop-core.

Typically, when a request arrives, an IContext is created and bound to a worker thread; then the GraphQL engine creates IServiceContext referencing the IContext and adds more information. Therefore, for one request, there is one IServiceContext and one IContext.

### 33. EQL does not support `cast(value as date)`. What to do?

Use the `date(field)` function. To see available functions, check `dialect.xml`. You can customize `dialect.xml` to add functions. Use template functions to transform to specific SQL syntax.

### 34. Must the primary key id have a seq tag? How is continuity guaranteed?

If the primary key id doesn’t have seq, you must set it manually. If seq is set, it can generate sequential ids based on configurations in `nop_sys_sequence`. Each entity corresponds to a record; if not found, a random id is generated.
If you want a global sequence for all tables, use the `seq-default` tag; it checks whether the entity-specific sequence exists; if not, it uses the default sequence.

Nop does not use database auto-increment to better support distributed databases and multiple database types.

### 35. If no properties are changed on submit, is no database update triggered?

Correct. If a property wasn’t modified, the entity is not marked as dirty, no OrmInterceptor callbacks fire, and the database is not updated.

### 36. After re-running the nop-cli generation tool, why didn’t the xmeta file update?

nop-cli generates the initial project skeleton; it does not generate meta. XMeta is generated based on your manually customized `orm.xml`, not directly from xlsx.
You can run XXXWebCodeGen.java in IDEA to generate code. After the first generation, you don’t use nop-cli again.

That’s why xlsx models are placed in the model directory: every mvn install runs the code generator to regenerate all code. nop-cli’s precompile directory conventionally includes the model directory and xlsx model file names.

### 37. How to implement type conversion in XScript

Built-in extension functions like `$toInt`, `$toString`. For example, `a.$toInt()` converts a to Integer. Implementation uses ITypeConverter registered in SysConverterRegistry and eventually calls `ConvertHelper.toInt`, etc.
Type conversion functions support default values, e.g., `a.$toInt(10)` returns the default when a is empty or null.

### 38. Why does NopGraphQL error when returning `Map<String,MyEntity>`?

GraphQL supports only object and object list structures, not Map<String, Object>. Keys are not predetermined; the GraphQL spec doesn’t support structures with unpredictable keys.
Map is a NopGraphQL extension intended for one-shot returns to the frontend, without field selection, and it cannot contain entities internally. Entities may form graph structures, making ordinary JSON serialization impossible; entities may fetch too much data through associations—even the entire database unintentionally.

Fix by returning a normal DataBean, for example:

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

Or return `List<MyEntity>`. GraphQL recognizes object types and object list types.

### 39. Why does returning data via a custom generic class `MyPageBean<T>` in NopGraphQL cause errors?

NopGraphQL currently does not support generics for custom data types, so you need a derived class to return. NopGraphQL must generate a generic type handler for each type; PageBean and ApiResponse have special handling.

```
public MyUserPageBean findAll(){
 ...
}

class MyUserPageBean extends MyPageBean<User>{}
```

If you only want to extend info based on an existing PageBean, use `PageBean.setExtData`.

### 40. logInfo did not print data

```javascript
 logInfo("data",data);
```

logInfo’s first parameter is a templated message string using slf4j syntax; use `{}` as placeholders:

```javascript
logInfo("data: {}",data);
```

### 41. If a view file is defined under the `_delta` directory and already exists, is it overlay or merge?

Overlay. You need to mark `x:extends="super"` on the root node for merge. DSL files are self-describing: by inspecting a DSL file, you know its XDef meta-model and the base models it extends.

### 42. If there are multiple customizations, in what order are they merged? For example, NopAuthUser view files: built-in Nop -> framework customization -> product customization -> derived product customization

Nop manages all DSL files via a unified virtual file system. In the virtual file system, you can define multiple peer delta directories and specify overlay ordering via `nop.core.vfs.delta-layer-ids`.
For example, `nop.core.vfs.delta-layer-ids=deploy,product` means files under `_delta/deploy` override those under `_delta/product`, and then override files with the same name outside delta.

### 43. Why did x:override not take effect?

```xml
<pages>
  <crud name="main">
    <listActions><action id="stat-button" label="Data Analysis" icon="fa fa-bar-chart pull-left" actionType="drawer"....
    </listActions>
  </crud>
  <crud name="error-tags" grid="simple-list" x:prototype="main" filterForm="false"><table no0perations="true"...>
    <listActions x:override="bounded-merge">
      <action id="batch-delete-button"/>
      <action id="add-button"/>
    </listActions>
  </crud>
</pages>
```

`x:override` is not used to override what’s inherited via `x:prototype`. Use `x:prototype-override`. `x:override` overrides content inherited via `x:extends`.

### 43. Can GraphQL be used standalone in a Spring Boot project, or must Nop ORM be included?

NopGraphQL can be used standalone. nop-graphql-orm integrates the two. For GraphQL-only integration examples, see the nop-spring-simple-demo module.

### 44. If the user sends an attribute that is not insertable at save time, is it filtered by meta or ignored by ORM?

The meta layer filters it. Many fields are not insertable by the frontend but are insertable by backend logic. At the ORM layer, if a field is configured with insertable=false, it will not appear in the generated insert SQL at all.

### 45. With the current save_update method, nonexistent parts sent from the frontend are deleted. How do we customize different save strategies? For example, I don’t want any delete behavior.

EntityData entityData = new EntityData<>(data, validated, entity, objMeta);
data retains all frontend input, validated is the result after validation and conversion per configuration. Both exist.

### 46. Y = A + B + D = X + (-C + D) = X + Delta; with many branches/versions, how do we know X=a+b+c? Do we still have to check configs?

When starting in debug mode, the dump directory outputs the merged full result. If node/attribute positions are inconsistent, it is clearly distinguished, and you can see which files each delta consists of.

## Deployment Questions

## Design Questions

### 1. What do “function abstraction” and “function templating” mentioned in Nop documentation mean?

For example, in a workflow that reaches step A, a condition needs to be added. Regardless of how it’s coded, it essentially corresponds to a predicate function. Some workflow engines may do:

```xml
<step>
  <when class="xxx.MyCondition"/>
</step>
```

A Java class provides the predicate. The workflow engine may even provide a plugin mechanism to dynamically load condition plugins. These plugins must meet the workflow interface standard and can be used only in the current engine.

However, in Nop’s view, what is needed here is merely a function abstraction interface. How it’s implemented is a separate problem and is independent of the workflow engine’s design.
That is, in the workflow engine design, we only need function abstraction—no additional plugin loading abstraction.

Nop’s approach is to uniformly use the xpl template language to solve function implementation by templating function structure.

In short, when solving problems on Nop, we systematically adopt the following strategy:

1. Define functions at critical points using the general IEvalFunction or IEvalAction interface.
2. Provide function implementations via the template language.

Because the template language is XML (a structured representation), it can be visually edited via a generic TreeEditor.

According to Reversible Computation, any information structure can have multiple representations, and these can be reversibly transformed. Visual editing is merely a bidirectional mapping between visual and textual representations.

This bidirectional mapping capability is compositional. If `a <--> A`, `b <--> B`, we can often automatically obtain `a + b <--> A + B`.

#### Template-based function structuring via XPL template language

Suppose our predicate is implemented like this in Java:

```
if(user.age < 18)
  return false

if(user.gender == 1)
  return false

return true
```

We could convert it to an AST and store as XML, but that’s not intuitive.
With the tag library abstraction in the Xpl template language, we can encapsulate specific logic into business-semantic tag functions and provide a visual editor. (Similar to configuring custom components rather than raw div/span nodes in a form editor.)

```xml
<and>
  <app:IsAdult/>
  <app:GenderIsMale/>
</and>
```

### 2. Under what circumstances do we define our own .xlib files, and must xlib be placed in a fixed location?

xlib is a function library for the xpl template language. Generally put it under your module’s xlib directory. It can be placed anywhere; it’s just a regular function library. Import the tag library in an xpl block to use it. Common built-ins:

1. `web.xlib` generates AMIS pages from xview models.
2. `control.xlib` infers display controls based on domain, type, etc.

### 3. Can we write BizModel without entity definitions?

Yes. Nop’s design is layered, and a later layer does not depend on an earlier layer. Just write a BizModel class; see LoginApiBizModel.

```
  Excel --> ORM --> BizModel --> XView --> Page
```

Using Nop’s code generator, we can infer frontend pages from Excel data models step by step, but each step is optional; a previous step is not required.

Refer to [LoginApiBizModel](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/LoginApiBizModel.java). We can directly write a BizModel class in Java, register it in a beans.xml, and invoke it via NopGraphQL.

> LoginApiBizModel implements ILoginSpi only to improve readability and maintainability; it’s not required. A BizModel class need not extend any base class or implement any interface. Essentially, BizModel is similar to a Spring Controller, but it is independent of any Web runtime and does not require POJOs ready for direct JSON serialization.
> (A BizModel method’s return value will be processed by NopGraphQL to form the response.)

With NopGraphQL, there’s often no need to distinguish Controller and Service. Just add @BizModel to a Service (and register in beans.xml) to publish as a web service. The meta layer can perform response enhancement and post-processing; NopGraphQL provides an elastic adapter layer, often eliminating the need for an extra adapter object.

### 4. Is Nop’s main development focus a “Domain Language Workbench,” a “Low-Code Development Platform,” or a SpringCloud-style framework?

Nop aims to be a general Domain Language Workbench; the rest is incidental. Because we can rapidly develop and extend domain languages and automatically infer designers and parsers, with built-in domain models it can be used as a low-code platform. To simplify implementation and maximize extensibility, we re-implemented a bottom-layer development framework based on Reversible Computation principles—this was a byproduct of R&D. Ultimately, with around 200K lines of hand-written code, all functionality will be completed; other code is generated—kept within one developer’s manageable scope.

### 5. How does Nop view low-code products, and what is their relationship to domain languages?

We focus less on low-code as a product form and more on broadly advancing descriptive programming, expanding its application scope, and achieving seamless fusion of descriptive and imperative programming. How low should low-code be? See my article
\[From Reversible Computation to LowCode\] (https://zhuanlan.zhihu.com/p/344845973).
The essence is similar to model-driven approaches. Serializing models into text gives us DSL. Reversible Computation introduces a Delta-based understanding of models and new technical means for model construction and extension.

A Turing machine is complete because it is a virtual machine that can simulate any automatic computing machine. If we keep raising the abstraction level of virtual machines, we can obtain virtual machines that directly “run” domain-specific languages (DSLs). But since DSLs focus on domain concepts, they cannot conveniently express all general computation (otherwise they become general-purpose languages), causing information overflow—becoming the Delta term.

In the evolution of programming languages (1st–3rd generation), abstraction increased while remaining general-purpose. By the 4th generation, we likely get not another general language but a forest of DSLs, yielding new representations and cognition of traditional program structures.

### 6. In the Excel model, must appName be two-level? What happens if it’s one- or three-level? Also, it automatically becomes a two-level directory name—does that refer to package directories under src?

The two-level directory corresponds to `src/resources/_vfs/xxx/yyy` in the virtual file system.
Currently limited to two levels because the platform scans modules at startup and limits scan scope. It scans only two levels; if a `/_vfs/xxx/yyy/_module` file is found, it is considered a module, and it auto-loads `app-*.beans.xml` under its beans directory.

When loading files via `module:/abc/yy.xml`, it scans all modules for /abc/yy.xml, e.g., `/_vfs/xxx/yyy/abc/yy.xml`.

### 7. Can Nop’s GraphQL be used independently of Spring and Quarkus?

Nop’s GraphQL engine is pure logic, akin to a general request=>response service decomposition mechanism.
GraphQLWebService provides a jaxrs-based web adapter. QuarkusGraphQLWebService and SpringGraphQLWebService fill in framework-specific details, exposing `/graphql` and `/r/{operationName}` endpoints.

If you write a simple HTTP adapter based on vertx or netty, you can avoid depending on quarkus or spring.

Because NopGraphQL’s inputs and outputs are POJOs, it can be used in batch engines, kafka consumers, and even stream processing frameworks.

If you want only the backend without AMIS frontend tech, use
nop-spring-web-orm-starter (GraphQL + ORM) or nop-spring-core-starter (core xlang only),
or nop-quarkus-web-orm-starter or nop-quarkus-core-starter.

Starters provide auto-integration with Spring/Quarkus. Include the dependency, and application startup automatically calls Nop’s initialization (CoreInitialization.initialize()).

### 8. Is Nop’s logic orchestration about code logic or service invocation logic, like Zeebe for microservice orchestration?

The orchestration engine itself shouldn’t care whether it’s code or a service; that’s purely implementation detail. A microservice call is a function invocation. If you can orchestrate functions, you can orchestrate services in principle. Services have additional management logic unrelated to orchestration and should not pollute the orchestration engine. Nop orchestrates via template language and DSL; the runtime can use XPL to extend infinitely.
Retries, TCC rollback, etc., can appear as function decorators.

### 8. How to implement “light association” mechanisms

Can we implement a light association/fill feature—for example, a field default value can reference another table field or directly use an EL expression to load local methods, just specifying `${user.userId}` to auto-fill, with no relational link between the target table and the expression’s table, and no need to specify complex associations?

Solution:

1. The built-in CrudBizModel already provides many general-purpose service retrieval functions, avoiding manual coding, and GraphQL’s renaming mechanism supports field adaptation. For example `/r/NopAuthUser__get?id=3&@selection=name:userName,status:userStatus` retrieves data by id but returns fields name and status.
2. In a meta file, add a custom prop, and write an XScript in the prop’s getter to fetch data—no need for xbiz.
3. Using NopDynEntity, you can define backend service functions online and call them from the frontend to return data.

Essentially in a frontend-backend separated architecture, frontend “light association” is adding a standardized backend service call in the page config. How the service is implemented (online-defined or otherwise) is an independent issue.
Nop standardizes backend service naming: /r/{bizObjName}_{bizMethodName}?@selection={selectionSet}

With the data provider available, the frontend can decide whether to use it as a default value or for fixed linkage.

### 9. How does modularity and subsystem concepts manifest in Nop? In enterprise apps, finance, CRM, HR—customers may buy independently; they are separate subsystems.

In a microservices architecture, modularity is mainly through service boundaries. Nop supports a two-level module system via virtual file system subdirectories. That is, /nop/auth corresponds to {vendor}/{subModule} style module ids.
By arranging module paths, you can have app/fin, app/crm, app/hr independently for development and management.
Nop is designed to be composable and separable. Including app/fin automatically adds functionality. Each submodule can be deployed as an exe, or multiple can be bundled as a monolith.

Conceptually Nop is like a micro-kernel that can dynamically load/unload business modules. But currently, if a module contains Java classes, a restart is needed.
You could load module classes via a Java ClassLoader. If the module has no Java classes, no restart is needed.
These are detail features; Nop core may not go into such fine granularity.

During development, Quarkus has hot-load mechanisms. In dev mode, modifying Java auto-reloads. So Nop module development can use Quarkus integration mode.

### 10. Nop’s extensibility is flexible with fine granularity. If others extend on top of it, how do we avoid misconfigurations affecting functionality? For example, disabling a bean at the source impacts features.

Nop only solves structure space construction and transformation. Constraints in the semantic space must be ensured by yourself—add your own extra validations. The framework performs some semantic checks in various engines.

1. xdef meta-model defines format requirements; after merging, models are validated by the validator.
2. IoC checks bean dependencies automatically during mvn install packaging and application startup.
3. ORM checks all table and property references at startup.
4. XView checks that all referenced fields, if not marked custom, must be defined in XMeta.
   In short, in each engine, you can add domain-specific checks—akin to enhanced typing. xdef is a stronger validation than language types. Engines can include more domain semantics validation.

In Reversible Computation, Delta merging occurs in the structural layer, where runtime-illegal structures can exist. The structural layer is the feasible space—the largest set of all possible structures.
Like quantum mechanics, outside observation, energy conservation can be “violated”—quantum tunneling is allowed. But post-merge, when entering the observable, runnable world, constraints must be applied.
Nop has multi-stage compilation; before actual running, it provides many Turing-complete, application-side validation hooks—even contract-based programming—to insert unit tests,
and in debug mode the system may require certain sample tests to pass to start.

In traditional programming, the structural space is closed, defined only by compiler vendors. Nop opens the structural space, allowing custom structural rules and exposing compiler capabilities to the application layer.
In frontend tech, with babel’s popularity, compiler capabilities are partially opened, but few use them at the application level besides some frameworks.
Nop proposes structural rules and usage patterns to make such capabilities simpler and more intuitive.

### 11. Why doesn’t NopIoC support class scanning?

Built-in scanning harms customizability. Class annotations cannot be easily customized like XML.
Implementing scanning is simple—write a scan tag function in `x:gen-extends`, similar to Spring 2.0’s named tags. This has performance impacts; Nop won’t include it by default.

### 12. In the Excel model, a field is labeled not-pub. How does it work? I only found model-level not-pub in BizObjectBuilder, not field-level.

At prop level, during code generation, the `not-pub` tag becomes `published=false`. Meta has an explicit property to record whether to publish—an explicit knowledge layer. At the ORM layer (storage model), it shouldn’t know publish status, so related info is stored in tagSet—an extension description. When generating xmeta from ORM models, it converts this back to explicit knowledge.

### 13. The core formula of Reversible Computation, `App = Delta x-extends Generator<DSL>`, has Generator as an abstract concept. What does it correspond to in Nop?

1. The XCodeGenerator tool generates code from Excel models, automatically overwriting files under the _gen directory and all files with names starting with underscores. Handwritten code inherits from the generated code for Delta customization.
2. All XDSL files support dynamic code generation segments: x:gen-extends and x:post-extends. Inside, you can generate model nodes via the xpl template language and then Delta-merge with external nodes.

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

The merge strategy: Result = E x-extends Model x-extends D x-extends C x-extends B x-extends A

XML-format model files, underscore-prefixed auto-generated source, handwritten source, and Generator tools—model files correspond to DSL. Does the Generator tool correspond to the formula’s Generator? If not, which parts in Nop correspond to Generator?

### 14. Enterprise-grade engineering values defensive programming (e.g., anti-corruption layers). Is it necessary to create an anti-corruption layer for Nop?

Nop supports Delta customization, enabling deep customization without modifying Nop source. This greatly reduces the need for anti-corruption layers. Most people’s designs cannot approach Nop’s level; NopGraphQL and NopORM already minimize information expression. Designing your own anti-corruption layer rarely provides real change isolation value. Nop has few third-party dependencies, so upgrades rarely impact applications indirectly. We do not recommend building an anti-corruption layer for Nop.
For example, Nop’s XML parser is not the JDK’s; it’s hand-written, high-performance, and avoids complex feature security holes.

### 15. For the nop-entropy project, is Quarkus or Spring Boot recommended?

Either; no preference. The domestic Solon framework is also supported.

### 16. Why does Nop far surpass current tech in strong-type development?

Type systems essentially provide predefined constraint rules. Built-in language rules are limited and general-purpose, so their capabilities are limited. Many lint systems add best-practice constraints—custom enhancements on type systems—but still domain-agnostic. DSL’s unique value lies in rules and constraints tailored to a specific domain, fully exploiting domain information to achieve what general rules cannot.
For example, a workflow DSL validator can automatically validate unique step ids and whether the graph is a DAG. If DSL is open for user editing, it’s easy to add custom validations outside the engine, such as code segments only using fixed tag functions, or allowing specific expressions only. Because Nop DSLs all use XML, parsed as generic XNode nodes, regardless of DSL, you can insert custom syntactic validations and structure transformations in a unified compile-time meta-programming phase like `x:gen-extends`. In a typical strongly-typed language, you can only use a fixed AST, writing meta-programming for a specific syntax; this is inconvenient and cannot unify runtime and compile-time processing (to unify, we need Lisp-like homoiconicity: the structure of the compile-time code must match the structure it generates. Most strong-typed languages lack such homoiconicity).

More importantly, Nop DSLs uniformly use the XDef meta-model and support the unified paradigm Y=F(X)+Delta, automatically enabling Delta definitions and operations—i.e., Delta customization by default. Most strongly-typed languages do not have fine-grained Delta capabilities.

Strong-typed languages apply compile-time type constraints. Nop can apply any domain-specific constraints via Maven plugins during mvn install—not limited to general type constraints. XDef’s constraint capabilities exceed type systems; for example, at the property level, it recognizes extensible formats like v-path.

Nop’s DSLs serve not only as information carriers, but also as coordinate spaces for Delta operations. Every DSL syntactic property has a unique coordinate; type systems assume different objects can have the same type, making fine-grained Delta definition difficult. Typically, in type systems, we cannot effectively specify Delta enhancements to one particular button in a button list.

Nop emphasizes representation-agnostic information expression—information can be reversibly transformed freely among XML/YAML/Expr representations.

#### I. The intrinsic limits of type systems
Type systems’ core constraints have three boundaries:
1. Semantic boundary
   Can validate `price: number`, but not `price > cost`
2. Domain boundary
   Cannot express business rules like `approval flow must include a risk-control node`
3. Extension boundary
   Constraints are locked in the compiler; cannot extend domain-specific validations on demand

As if checking only part sizes of a car but not verifying road safety after assembly.

#### Essence of paradigm shift
Nop’s core innovation is building a domain-aware constraint system:

1. Open extension of constraints
   XDef meta-model exposes standard extension interfaces to inject domain-specific validation rules

2. Precise localization of modifications
   Structural coordinate system implements declarative Delta operations

3. Spatial-temporal continuity of processing
   Homoiconic model unifies compile-time and runtime processing

<!-- SOURCE_MD5:a049506b6787b00aa64b74245680035f-->
