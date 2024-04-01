# Biz模型

视频介绍：[Nop平台中如何通过XBiz配置文件实现后台服务函数](https://www.bilibili.com/video/BV1aN411B7Ju/)

## 代码生成

缺省的代码生成模板会根据meta文件自动生成对应的xbiz模型文件，并且会自动引入biz-gen.xlib标签库，为CRUD操作生成缺省的函数参数声明。

在xbiz文件中可以直接编写query,muation, loader等函数定义，它们会覆盖Java的BizModel中的相应函数实现。
也就是说，对外暴露的BizObject是由所有的BizModel类以及XBiz模型文件中定义的所有函数所组成的，在BizObjectManager启动的时候会扫描
系统中所有的BizModel类，以及XBiz模型文件，生成对应的BizObject对象。

**XBiz中定义的函数优先级最高，如果与已有的函数同名，则会自动覆盖**

例如NopAuthUser.xbiz中增加了一个active\_findPage函数

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

x:prototype="findPage"表示继承了缺省生成的findPage函数的参数定义。如果不继承，完整编写的函数如下：

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

这个函数定义等价于在Java中定义函数

```javascript

@BizQuery
public PageBean<NopAuthUser> active_findPage(@Name("query") QueryBean query, 
    FieldSelection selection, IServiceContext ctx){
    return ...    
}
```
