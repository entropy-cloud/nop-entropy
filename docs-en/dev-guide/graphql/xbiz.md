# Biz Model

Video Introduction: [How to Implement Backend Service Functions in the Nop Platform Using XBiz Configuration Files](https://www.bilibili.com/video/BV1aN411B7Ju/)

## Code Generation

The default code generation template will generate corresponding XBiz model files based on the meta file and automatically import the biz-gen.xlib tag library to generate default function parameter declarations for CRUD operations.

In XBiz files, you can directly define query, mutation, loader functions. These will override the corresponding functions in Java's BizModel.
This means that the exposed BizObject is composed of all functions defined in both BizModel classes and XBiz model files. When BizObjectManager starts up, it scans
all BizModel classes in the system as well as XBiz model files to generate corresponding BizObject objects.

**Functions defined in XBiz have the highest priority. If a function with the same name already exists, it will be automatically overridden.**

For example, if NopAuthUser.xbiz adds an active_findPage function:

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

If x:prototype="findPage" is not inherited, the complete function can be written as:

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

This function is equivalent to defining the following Java function:

```javascript
@BizQuery
public PageBean<NopAuthUser> active_findPage(
    @Name("query") QueryBean query,
    FieldSelection selection,
    IServiceContext ctx
) {
    return ...;
}
```