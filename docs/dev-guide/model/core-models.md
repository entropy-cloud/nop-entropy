# 核心模型说明

|后缀名|位置|说明|
|---|---|---|
|xdef|/nop/schema/|元模型|
|orm.xml|/{moduleId}/orm/app.orm.xml|实体数据模型定义，用于ORM引擎|
|xmeta|/{moduleId}/model/{bizObjName}/|元数据模型。用于GraphQL和CrudBizModel, 表达模型哪些字段对外透出以及能否修改|
|xbiz|/{moduleId}/model/{bizObjName}/|业务行为模型。用于GraphQL引擎, 定义对外暴露的服务函数|
|xview|/{moduleId}/pages/{bizObjName}/|视图大纲模型。用于前端页面生成，指定表单和列表的基本内容|
|page.yaml|/{moduleId}/pages/{bizObjName}/|页面模型。用于前端页面渲染|

## 实体数据模型: orm.xml

NopORM引擎根据orm定义自动生成建表语句，并生成对应实体类代码，实现数据库表和Java实体之间的双向映射。

## 元数据模型：xmeta

* ORM层定义的属性不一定暴露给GraphQL层。比如password属性，只能够插入，但是外部用户是无法查询到password属性的。
  XMeta配置所负责的就是指定GraphQL层实际对外暴露的属性是哪些。

* CrudBizModel的实现利用了XMeta的配置信息，用于判断字段是否可以被修改、是否可以被查询，应该满足什么样的格式要求等。
  **任何与业务对象相关的元数据信息都可以配置在XMeta**中，它比在Java类中增加注解要更方便扩展。

* 在XMeta中，我们也可以新增一些实体上没有的属性对外暴露。比如对于关联字典表的字段，我们会对应的生成一个字典文本字段。

> 假设实体上具有status字段，且它关联了一个字典配置，则XMeta在编译阶段会自动生成一个配对的status\_label字段。

* 在XMeta中，也可以直接在prop配置中直接指定getter，用于返回一个动态计算的值

```xml

<meta>
    <props>
        <prop name="myField">
            <getter>
                <c:script>
                    return entity.name + '123'
                </c:script>
            </getter>
        </prop>
    </props>
</meta>
```

## 业务行为模型: xbiz

XBiz模型可以定义后台业务对象上允许执行的业务方法，这些业务方法可以通过GraphQL协议和REST协议调用到。

> /r/NopAuthUser\_\_findPage 会调用到 NopAuthUser对象上的findPage方法

在XBiz模型和Java类中都可以为业务对象定义业务方法，但是XBiz模型中定义的方法优先级更高，它会覆盖Java中定义的方法。

如果同时定义了如下方法和xbiz模型，则实际调用的是xbiz模型中定义的getUserInfo函数。

```java
class NopAuthUserBizModel {
    @BizQuery
    public LoginUserInfo getUserInfo(@Name("id") String id) {
        return ...
    }
}
```

```xml

<biz>
    <actions>
        <query name="getUserInfo">
            <arg name="id" type="String"/>
            <source>
                ...
            </source>
        </query>
    </actions>
</biz>
```

## 视图大纲模型: xview

XView主要描述从业务角度看到的界面核心信息，即页面上有哪些字段、字段的布局大致是什么，有哪些按钮，点击了按钮会调用后台的什么函数。

XView模型与具体的技术实现无关，可以引入不同的`control.xlib`
库，将XView模型翻译为不同的前端框架实现。缺省的control.xlib将XView模型翻译为百度AMIS框架对应的json格式。

## 页面模型: page.yaml

YAML格式和JSON格式可以互相转化。在YAML格式中，可以比较方面的嵌入XML，便于元编程代码的编写。另外YAML允许添加注释，使用起来也比JSON要更加友好。

Nop平台为任意的JSON和YAML格式增加了Delta差量运算机制，可以支持JSON对象的动态分解、合并。一般我们可以利用XView模型来自动生成页面模型，避免手工编写。

```yaml

x:gen-extends: |
  <web:GenPage view="NopWfAction.view.xml" page="picker" xpl:lib="/nop/web/xlib/web.xlib" />

title: 自动生成的页面内容可以被这里手工编写的内容覆盖
```
