# Adding Fields to the Backend Graph

## 1. All fields returned in GraphQL must be defined in xmeta files

During code generation, based on the database model, prop definitions are automatically generated for each field with published=true. The auto-generated xmeta definitions are stored in files prefixed with an underscore.
For example, NopAuthUser.xmeta extends \_NopAuthUser.xmeta. We can enhance the auto-generated xmeta in the file without the underscore.
You can add fields, add attributes to fields, or delete auto-generated field definitions.

## 2. Provide Loaders for props defined in xmeta

xmeta definitions are automatically converted into GraphQL type definitions, and the backend also needs to add the Fetcher definitions required by GraphQL to actually return data.

### 2.1 Add getter methods on the Java entity

Simply add the corresponding get/set methods in the NopAuthUser.java class to achieve data access. xmeta only concerns the interface layer and makes no assumptions about the underlying storage mechanism.

### 2.2 Add methods on the BizModel and annotate with @BizLoader

For example, add a fetcher for the roleUsers property of NopAuthUser

```
@BizModel("NopAuthRole")
public class NopAuthRoleBizModel extends CrudBizModel<NopAuthRole> {

    @BizLoader
    @GraphQLReturn(bizObjName = "NopAuthUser")
    public List<NopAuthUser> roleUsers(@ContextSource NopAuthRole role) {
        return role.getUserMappings().stream().map(NopAuthUserRole::getUser)
                .sorted(comparing(NopAuthUser::getUserName)).collect(Collectors.toList());
    }
}   
```

### 2.3 Define loaders in the XBiz model

### 2.4 Define getters/setters directly in the XMeta model

For relatively simple cases, we don't need to modify the BizModel or entity classes; we can define accessors directly in the xmeta model at the backend interface layer.

```
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

$toLocalDate is an extended type conversion function. All expressions can ultimately invoke type conversion functions on ConvertHelper via $toLocalDate, toInt(), and similar methods.

For common, reusable conversion functions and the like, we can leverage the Nop platform’s built-in metaprogramming mechanisms, such as `x:gen-extends` and `x:post-extends`, to automatically generate property definitions.
See [biz-gen.xlib](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xlang/src/main/resources/_vfs/nop/core/xlib/biz-gen.xlib)
<!-- SOURCE_MD5:b7d9d7d18ff8d1ebfbb19b6b6359d7fa-->
