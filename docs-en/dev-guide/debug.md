# Development Debugging

## Debug Mode

**Setting `nop.debug=true` will enable debug mode. In this mode, DevDoc (used for debugging) is registered as a backend service and `_dump` files are generated.**

The default configuration file is `application.yaml`. If you want to use a special environment configuration file like `application-dev.yaml`, you must configure the corresponding `nop.profile` in either `bootstrap.yaml` or `application.yaml`.

## Error Location

When exceptions occur in the backend, we generally use the `NopException` class, which has a `SourceLocation` property. This property reports the source code location of the error in the XLang program. Additionally, the `XLang` execution stack is also reported. When an exception occurs, it will print the error message along with the stack trace to the logs.

For example:

```
io.nop.api.core.exceptions.NopException: NopException[seq=4,status=-1,errorCode=nop.err.xui.ref-view-not-exists,params={viewPath=/app/mall/LitemallGoods/attributes.page.yaml},desc=view配置不存在：/app/mall/LitemallGoods/attributes.page.yaml]@_loc=[114:22:0:0]/app/mall/pages/LitemallGoods/LitemallGoods.view.xml
@@getFormSelection(formModel,objMeta)@[7:30:0:0]/nop/web/xlib/web/page_simple.xpl
@@</_delta/default/nop/web/xlib/web.xlib#GenPage>("/app/mall/pages/LitemallGoods/LitemallGoods.view.xml","add",null)@[1:17:0:0]/app/mall/pages/LitemallGoods/add.page.yaml
@@__fn_1()@[1:17:0:0]/app/mall/pages/LitemallGoods/add.page.yaml
```

The above exception indicates:

1. `add.page.yaml` called the `GenPage` tag in `web.xlib`
2. `GenPage` tag called `page_simple.xpl`
3. `page_simple.xpl` called the `getFormSelection` function at line 7
4. The function accessed `LitemallGoods.view.xml` at line 114, which had an error

## Logging Information

### 1. Configuration Information
If `nop.debug=true`, the system will print all configuration values and their locations in the logs when it starts up. This includes multiple configuration files, with higher-priority files overriding lower ones.

```
# [84:11:0:0]classpath:application.yaml
quarkus.log.level=INFO
```

### 2. Model File Parsing
All model files are parsed and log messages are printed during parsing, including the time taken for each parse.

For example:

```
2023-07-11 21:13:16,152 INFO [io.nop.cor.res.com.par.AbstractResourceParser] (Quarkus Main Thread) nop.core.component.finish-parse-resource:usedTime=10,path=/nop/schema/beans.xdef,parser=class io.nop.core.lang.xml.parse.XNodeParser
```

### 3. Dynamic Bean Parsing
If the logging level for `io.nop.ioc` is set to DEBUG, the system will print information about each bean being parsed when it starts up.

For example:

```
quarkus:
  log:
    category:
      "io.nop":
        level: DEBUG
```

The Nok IoC container first analyzes all beans' conditions. If a condition fails, it logs why that bean was disabled and creates it only if necessary. Disabled beans are those marked with `disabled-bean`, like `nopLoginService`.

For example:

1. `add.page.yaml` called the `GenPage` tag in `web.xlib`
2. `GenPage` tag called `page_simple.xpl`
3. `page_simple.xpl` called the `getFormSelection` function at line 7
4. The function accessed `LitemallGoods.view.xml` at line 114, which had an error

# Technical Documentation Translation

## Directory Structure
In the `_dump` directory, `/nop/main/beans/merged-app.beans.xml` outputs all active beans along with their corresponding configuration file source codes.

---

### 4. Database Access
All database access SQL statements are recorded in the logs, including SQL execution time display.

```
2023-07-11 21:14:39,637 INFO [io.nop.dao.jdb.imp.JdbcTemplateImpl] (Quarkus Main Thread) nop.jdbc.run:usedTime=1,querySpace=default,range=0,1,name=jdbc,null,sql=select o.SEQ_NAME as c1
from nop_sys_sequence as o
 where o.DEL_FLAG = 0
```

---

### 5. Successful Initialization
After successful initialization, the Nop platform prints out the total execution time and banner information.

```
2023-07-11 21:14:40,562 INFO [io.nop.cor.ini.CoreInitialization] (Quarkus Main Thread) nop.core.end-initialize:usedTime=87286
```

---

### 6. Virtual File Scan
The Nop platform uses class scanning at startup to find all files in the `_vfs` directory. In debug mode, scan results are output to `nop-vfs-index.txt`. This file records all accessible virtual file paths.

---

## Logging

### Log Printing

#### XScript Scripts
Built-in functions like `logInfo/logDebug` are included in XScript scripts.

```javascript
logInfo("nop.err.invalid-name:name={}", name);
```

- The first parameter must be a static string.
- Prevents log injection attacks by disallowing concatenation of variables.
- Uses Slf4j's log message template syntax, requiring `{}` for variable substitution.

For specific implementations, refer to `LogFunctions.java` and `LogHelper.java`.

---

#### Any Object Call
Triggers debug statements when `$` methods are called on objects.

```javascript
b = a.f().$("test")
```

This is equivalent to:

```javascript
b = DebugHelper.v(location(), "test", a.f());
```

- The `test` parameter is custom prompt information.
- Use this for debugging expressions like `a.f() => 1`.

---

#### Debug Information
Sample debug output:

```
21:00:01.686 [main] INFO io.nop.xlang.utils.DebugHelper - test:a.f()=>1,loc=[6:8:0:0]file:/C:/can/nop/nop-entropy-bak/nop-xlang/target/test-classes/io/nop/xlang/expr/exprs/debug.test.md
```

- `test` is a custom prefix.
- `a.f() => 1` indicates the expression's return value.
- `loc` corresponds to the debug statement's source code location.

---

#### XPL Template Language
Example of log usage in XPL:

```xml
<c:log info="${data}" />
```

---

## Model Dump

The Nop platform heavily uses meta-programming for dynamic code generation. This allows effective tracking of code generation details. At startup, the platform outputs merged result models to the `_dump` directory in the project root. In debug mode, this directory contains numerous output files.

![model-dump.png](model-dump.png)

If results are generated from multiple delta files, each source node/attribute's location information will be recorded in the result file.

---

In XDSL models, the root node can have an `x:dump="true"` attribute to log more detailed merging algorithms.

```javascript
DslNodeLoader.INSTANCE.loadFromResource(resource, null, XDslExtendPhase.validate);
```

This loads an XDSL file and returns merged XNodes. The merging process has multiple stages, with `XDslExtendPhase` handling intermediate phases.

---

## XLang Debugger


The Nop platform's `nop-idea-plugin` module provides an IDEA development plugin that includes a debugger for the XScript scripting language. This module adds breakpoint debugging functionality for all XDSL domain languages. For more details, see [idea-plugin.md](../user-guide/idea/idea-plugin.md).


## GraphQL Debugging Tool

The Quarkus framework includes the `graphql-ui` debugging tool. After launching the application in debug mode, you can access the debugging interface via the URL `/q/graphql-ui`. On this page, you can view all backend GraphQL endpoints and type definitions, with code suggestions provided during input.

![GraphQL Debugging Interface](../tutorial/graphql-ui.png)


## Common Issues


## Frontend


### 1. Empty frontend control for a field (cannot be edited)

This issue may occur if the `control.xlib` library does not define an editor for the specific field. In debug mode, the `XuiHelper.getControlTag` function will log the control mapping results. For example:

```
nop.xui.resolve-control-tag:controlTag=edit-int,prop=id,domain=null,stdDomain=null,stdDataType=int,mode=add
```


### 2. "415 Unsupported Media Type" error when uploading files

This error occurs in Quarkus and can be resolved by setting the `quarkus.log.level` to DEBUG. The backend service requires the `UploadService` to be properly registered.


## Backend


### 1. Bean not being injected as expected

First, check if the beans are correctly defined in the `_dump` directory within the application's root folder, specifically in `/{appName}/nop/mai/beans/merged-app.beans.xml`. This file contains the final results after the IoC container executes all dynamic logic and logs property and object origins. For example:

```xml
<!--LOC:[18:6:0:0]/nop/auth/beans/auth-service.beans.xml-->
<bean class="io.nop.auth.service.login.DaoLoginSessionStore" id="$DEFAULT$nopLoginSessionStore" ioc:aop="false"
      name="nopLoginSessionStore">
    <property name="daoProvider" ext:autowired="true">
        <ref bean="$DEFAULT$nopDaoProvider" ext:resolved-loc="[51:6:0:0]/nop/orm/beans/orm-defaults.beans.xml"/>
    </property>
    <property name="sessionIdGenerator" ext:autowired="true">
        <ref bean="$DEFAULT$nopSessionIdGenerator"
             ext:resolved-loc="[34:6:0:0]/nop/auth/beans/auth-core-defaults.beans.xml"/>
    </property>
</bean>
```

1. If the path in `ext:resolved-loc` does not match the current location, it will log LOC information.
2. `ext:autowired="true"` indicates that the property is injected using the `@Inject` annotation.
3. `ext:resolved-loc` specifies the exact configuration file location for the bean.
4. `ext:resolved-trace="/nop/auth/beans/app-service.beans.xml"` can be used to trace which beans.xml file loaded the current bean.


### 2. Viewing currently loaded beans

In debug mode, you can access the beans via `/p/DevDoc__beans`.


### 3. Viewing globally defined functions and objects

In debug mode:
1. Access `/r/DevDoc__globalFunctions`
2. Access `/r/DevDoc__globalVars`

These links allow you to view the actual Java classes and methods corresponding to these functions and variables.


### 4. Viewing currently enabled configuration variables

The `nop-config` module collects configuration information from multiple sources and resolves conflicts based on priority. The final enabled configuration variables can be viewed via:

/r/DevDoc__configVars  
In the returned result, include the source file path for each configuration variable.  

### 5. How to view all backend GraphQL services and their type definitions  
/p/DevDoc__graphql  
Return graphql definition  

### 6. Debugging the model merging process  
In `x:gen-extends` or `x:post-extends`, add logging output or use extended attributes to output debug information.  

```xml  
<orm>  
  <x:gen-extends>  
    <orm ext:time="${now()}" xgen:x:dump="true" />  
  </x:gen-extends>  
</orm>  
```  

## Auto-recording the response may have incorrect variable names because multiple random variables can have the same value, leading to errors when trying to reverse lookup variable names during data saving.  

For example, updateTime might be recorded as addTime.  

