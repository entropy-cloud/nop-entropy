# 在后台Graph中增加字段

## 1. 所有GraphQL中返回的字段都需要在xmeta文件中定义

代码生成时会自动根据数据库模型设置为每个published=true的字段生成prop定义，自动生成的xmeta定义存放在以下划线为前缀的文件中。
例如 NopAuthUser.xmeta继承了\_NopAuthUser.xmeta。我们可以在不带下划线的文件中对自动生成的xmeta进行增强。
可以增加字段、为字段增加属性或者删除自动生成的字段定义。

## 2. 为xmeta中定义的prop提供Loader

xmeta定义会自动转化为GraphQL的类型定义，后台还需要增加GraphQL所要求的Fetcher定义才能实际返回数据

### 2.1 在Java实体上增加get方法

直接在NopAuthUser.java类中增加对应的get/set方法即可实现数据存取。xmeta仅仅涉及到接口层，对底层存储机制没有任何假定。

### 2.2 在BizModel上增加方法，并设置@BizLoader注解

例如为NopAuthUser增加roleUsers属性对应的fetcher

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

### 2.3 在XBiz模型中定义loader

### 2.4 在XMeta模型中直接定义getter/setter

对于比较简单的情况，我们不需要修改BizModel或者实体类，可以直接在后台接口层的xmeta模型中直接定义存取器。

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

$toLocalDate是扩展类型转换函数。所有的表达式最后都可以通过$toLocalDate/toInt()等方式调用ConvertHelper上的类型转换函数。

对于一些常见的可复用转换函数等，我们可以利用Nop平台内置的`x:gen-extends`、`x:post-extends`等元编程机制自动生成属性定义。
参见[biz-gen.xlib](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xlang/src/main/resources/_vfs/nop/core/xlib/biz-gen.xlib)
