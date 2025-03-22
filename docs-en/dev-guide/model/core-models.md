# Core Model Explanation

| Suffix | Position | Explanation |
|--------|----------|-------------|
| xdef   | /nop/schema/ | Meta model |
| orm.xml | {moduleId}/orm/app.orm.xml | Entity data model definition for ORM engine |
| xmeta  | {moduleId}/model/{bizObjName}/ | Meta data model. Used for GraphQL and CrudBizModel, which fields are exposed and whether they can be modified |
| xbiz   | {moduleId}/model/{bizObjName}/ | Business behavior model. Used for GraphQL engine, defining exposed service functions |
| xview  | {moduleId}/pages/{bizObjName}/ | View structure model. Used for front-end page generation, specifying form and list contents |
| page.yaml | {moduleId}/pages/{bizObjName}/ | Page model. Used for front-end rendering |

## Entity Data Model: orm.xml

The NopORM engine generates table creation statements based on the ORM definition and generates corresponding entity class code to implement a bidirectional mapping between database tables and Java entities.

## Meta Data Model: xmeta

- **Properties Not Exposed to GraphQL Layer**: For example, the `password` property can only be inserted but not queried by external users.
- **Configurable Properties in XMeta**: These determine which fields are exposed by the GraphQL layer.
- **CrudBizModel Implementation Utilizes XMeta Configuration**: This is used to check if fields can be modified or queried and to enforce format requirements.
- **Any metadata related to business objects can be configured in XMeta**, making it easier to extend compared to adding annotations directly in Java classes.
- **Additional Properties Exposed in XMeta**: For example, for a dictionary table field, a corresponding dictionary text field will be generated.

> If an entity has a `status` field and is associated with a dictionary configuration, the XMeta will generate a corresponding `status_label` field during compilation.

* Properties Can Be Directly Specified in Prop Configurations**: These are used to define getters for dynamically computed values.

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

The XBiz model allows defining business methods that can be executed on backend business objects. These methods can be called via GraphQL or REST protocols.

> Example: `/r/NopAuthUser__findPage` will call the `findPage` method of the NopAuthUser object.

- Business methods can be defined both in XBiz models and Java classes, but XBiz model definitions take priority and will override those in Java classes.
- If both a method and an XBiz model define `getUserInfo`, the XBiz model's definition will be used.

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

## View Structure Model: xview

The XView model primarily describes the UI core information from a business perspective, such as which fields are present on a page, their layout, and which buttons are present. It also defines which functions are called when certain buttons are clicked.

- The XView model is independent of specific technical implementations and can be translated into different front-end frameworks using `control.xlib`.
- By default, `control.xlib` translates the XView model into a corresponding JSON format for.baidu AMIS framework.

## Page Model: page.yaml

YAML format and JSON format can be converted to each other. In the YAML format, it is easier to embed XML and write meta-programming code. Additionally, YAML allows comments to be added, making it more user-friendly than JSON.

The Nop platform has added a Delta differential computation mechanism for arbitrary JSON and YAML formats, enabling dynamic disassembly and merging of JSON objects. Generally, we can utilize the XView model to automatically generate page models, reducing the need for manual coding.

```yaml
x:gen-extends: |
  <web:GenPage view="NopWfAction.view.xml" page="picker" xpl:lib="/nop/web/xlib/web.xlib" />

title: Automatically generated content can replace manually written content here.
```