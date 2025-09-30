# Core Model Overview

|Suffix|Location|Description|
|---|---|---|
|xdef|/nop/schema/|Meta-model|
|orm.xml|/{moduleId}/orm/app.orm.xml|Entity data model definition, used by the ORM engine|
|xmeta|/{moduleId}/model/{bizObjName}/|Metadata model. Used by GraphQL and CrudBizModel; expresses which fields of the model are exposed externally and whether they can be modified|
|xbiz|/{moduleId}/model/{bizObjName}/|Business behavior model. Used by the GraphQL engine; defines service functions exposed externally|
|xview|/{moduleId}/pages/{bizObjName}/|View outline model. Used for front-end page generation; specifies the basic contents of forms and lists|
|page.yaml|/{moduleId}/pages/{bizObjName}/|Page model. Used for front-end page rendering|

## Entity Data Model: orm.xml

The NopORM engine automatically generates table creation statements from ORM definitions and produces corresponding entity class code, enabling bidirectional mapping between database tables and Java entities.

## Metadata Model: xmeta

* Attributes defined at the ORM layer are not necessarily exposed to the GraphQL layer. For example, a password attribute may be insertable, but external users cannot query the password attribute.
  XMeta configuration is responsible for specifying which attributes are actually exposed to the GraphQL layer.

* The implementation of CrudBizModel leverages XMeta configuration to determine whether a field can be modified or queried, what format requirements it should satisfy, and more.
  Any metadata related to business objects can be configured in XMeta; it is more convenient to extend than adding annotations to Java classes.

* In XMeta, we can also expose attributes that do not exist on the entity. For example, for fields associated with a dictionary table, we generate a corresponding dictionary text field.

> Suppose the entity has a status field associated with a dictionary configuration; then XMeta will automatically generate a paired status\_label field at compile time.

* In XMeta, you can also directly specify a getter in the prop configuration to return a dynamically computed value

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

## Business Behavior Model: xbiz

The XBiz model can define business methods permitted on backend business objects; these methods can be invoked via GraphQL and REST.

> /r/NopAuthUser\_\_findPage calls the findPage method on the NopAuthUser object

Business methods can be defined both in XBiz models and Java classes, but methods defined in XBiz have higher priority and override those defined in Java.

If both the following method and an xbiz model are defined, the call will actually invoke the getUserInfo function defined in the xbiz model.

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

## View Outline Model: xview

XView mainly describes core UI information from a business perspective: which fields are on the page, the approximate layout of fields, which buttons are available, and which backend functions are invoked when buttons are clicked.

The XView model is independent of specific technical implementations. You can plug in different `control.xlib`
libraries to translate the XView model into implementations for different front-end frameworks. The default control.xlib translates the XView model into the JSON format corresponding to the Baidu AMIS framework.

## Page Model: page.yaml

YAML and JSON formats are mutually convertible. In YAML, it is comparatively convenient to embed XML, which facilitates writing metaprogramming code. Additionally, YAML allows comments, making it more user-friendly than JSON.

The Nop platform adds a Delta operation mechanism for arbitrary JSON and YAML formats, supporting dynamic decomposition and merging of JSON objects. Typically, we can use the XView model to automatically generate the page model, avoiding manual authoring.

```yaml

x:gen-extends: |
  <web:GenPage view="NopWfAction.view.xml" page="picker" xpl:lib="/nop/web/xlib/web.xlib" />

title: Automatically generated page content can be overridden by the content manually authored here
```
<!-- SOURCE_MD5:b38373d5762e9c3033807841b2542be9-->
