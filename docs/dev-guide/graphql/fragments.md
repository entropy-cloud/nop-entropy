# 利用Fragment定义简化GraphQL查询

GraphQL要求在前端调用时指定返回的字段，对于字段比较多的情况会显得比较繁琐。此时我们可以利用GraphQL语言的Fragment功能来定义一些常用的字段集合，然后在查询时引用这些Fragment，从而简化查询。

## 1. 在XMeta中增加selection定义，并以`F_`为前缀

```xml
<meta>
  <selections>
    <selection id="F_defaults">
      userId, userName, status, relatedRoleList{ roleName}
    </selection>
  </selections>
</meta>
```

* 这里约定了必须用`F_`为前缀才是前台可以访问的Fragment定义。selection还有其他的用处。
* 如果不配置`F_defaults`，它会根据GraphQL类型的所有非lazy字段自动推定。如果明确指定了，则以指定的内容为准

## 2. 在前台查询时引用Fragment

使用GraphQL方式调用后台服务时可以使用Fragment

```graphql
query{
   NopAuthUser__findList{
     ...F_defaults, groupMappings{...F_defaults}
   }
}
```

或者使用REST方式来调用后台服务，通过`@selection`参数使用Fragment

```
/r/NopAuthUser__findList?@selection=...F_defaults,groupMappings
```

* REST方式调用时，如果不传`@selection`参数，则等价于返回`F_defaults`

REST方式下的selection如果只表达到对象层级，则会自动向下展开。

