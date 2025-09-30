# Biz Model

Video introduction: [How to implement backend service functions via XBiz configuration files in the Nop platform](https://www.bilibili.com/video/BV1aN411B7Ju/)

## Code Generation

The default code generation template will automatically create the corresponding xbiz model file based on the meta file, and will automatically include the biz-gen.xlib tag library to generate default function parameter declarations for CRUD operations.

In xbiz files, you can directly write function definitions such as query, mutation, loader, etc.; these will override the corresponding function implementations in Java's BizModel.
In other words, the externally exposed BizObject is composed of all BizModel classes as well as all functions defined in XBiz model files. When the BizObjectManager starts, it scans all BizModel classes and XBiz model files in the system to generate the corresponding BizObject instances.

**Functions defined in XBiz have the highest priority; if they share the same name as existing functions, they will automatically override them.**

For example, NopAuthUser.xbiz adds an active_findPage function:

```xml
<query name="active_findPage" x:prototype="findPage">

    <source>
        <c:import class="io.nop.auth.api.AuthApiConstants" />

        <bo:DoFindPage query="${query}" selection="${selection}" xpl:lib="/nop/biz/xlib/bo.xlib">
            <filter>
                <eq name="status" value="${AuthApiConstants.USER_STATUS_ACTIVE}" />
            </filter>
        </bo:DoFindPage>
    </source>
</query>
```

x:prototype="findPage" indicates that it inherits the parameter definitions of the default generated findPage function. If you do not inherit, the complete function would be written as follows:

```xml
<query name="active_findPage">
    <arg name="query" type="io.nop.api.core.beans.query.QueryBean"/>
    <arg name="selection" type="io.nop.api.core.beans.FieldSelectionBean" kind="FieldSelection"/>
    <arg name="svcCtx" type="io.nop.core.context.IServiceContext" kind="ServiceContext"/>
    <return type="PageBean&lt;io.nop.auth.dao.entity.NopAuthUser&gt;"/>
    <source>
        <c:import class="io.nop.auth.api.AuthApiConstants"/>
        <bo:DoFindPage query="${query}" selection="${selection}" xpl:lib="/nop/biz/xlib/bo.xlib">
            <filter>
                <eq name="status" value="${AuthApiConstants.USER_STATUS_ACTIVE}"/>
            </filter>
        </bo:DoFindPage>
    </source>
</query>
```

This function definition is equivalent to defining the following function in Java:

```javascript

@BizQuery
public PageBean<NopAuthUser> active_findPage(@Name("query") QueryBean query, 
    FieldSelection selection, IServiceContext ctx){
    return ...    
}
```

<!-- SOURCE_MD5:c41e94c74520623f6b0f39b42e071cbb-->
