# Adding Fields in the Backend Graph

## 1. All fields returned by GraphQL need to be defined in the xmeta file

The code generation will automatically map database model fields to generated properties based on `published=true`. These auto-generated xmeta definitions are stored in files with a prefix of an underscore, e.g., `_NopAuthUser.xmeta`. Additional fields can be added to these files without the underscore.

For example:
- `NopAuthUser.xmeta` inherits from `_NopAuthUser.xmeta`.
- Fields can be enhanced in the non-underscore file for auto-generated xmeta definitions.
- You can add, modify, or delete auto-generated field definitions.

## 2. Providing Loader for xmeta-defined Props

xmeta definitions are automatically converted into GraphQL type definitions. However, additional GraphQL Fetcher definitions are required in the backend to actually return data.

### 2.1 Adding Get Methods on Java Entities

To implement data retrieval, simply add corresponding `get/set` methods directly in the `NopAuthUser.java` class. The xmeta only deals with interface-level definitions and makes no assumptions about underlying storage mechanisms.

### 2.2 Adding Methods to BizModel and Applying @BizLoader Annotation

For example:
- Add a `roleUsers` property with corresponding fetcher to `NopAuthUser`.

```java
@BizModel("NopAuthRole")
public class NopAuthRoleBizModel extends CrudBizModel<NopAuthRole> {

    @BizLoader
    @GraphQLReturn(bizObjName = "NopAuthUser")
    public List<NopAuthUser> roleUsers(@ContextSource NopAuthRole role) {
        return role.getUserMappings().stream()
                .map(NopAuthUserRole::getUser)
                .sorted(comparing(NopAuthUser::getUserName))
                .collect(Collectors.toList());
    }
}
```

### 2.3 Defining Loader in XBiz Model

### 2.4 Defining Getters/Setters Directly in XMeta Model

For simpler cases, no changes are needed to BizModel or entity classes. Instead, define the getters/setters directly in the backend interface's xmeta model.

```xml
<meta>
  <props>
    <prop name="createDate">
      <getter>
        entity.createTime.$toLocalDate()
      </getter>
    </prop>
  </props>
</meta>
```

$toLocalDate is an extended type conversion function. All expressions can be invoked via $toLocalDate/toInt() etc., using ConvertHelper.

For common reusable transformations, utilize Nop's built-in meta programming features such as `x:gen-extends` or `x:post-extends`.

Reference:
- [biz-gen.xlib](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/resources/_vfs/nop/core/xlib/biz-gen.xlib)