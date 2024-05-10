# 类似JSR303的验证机制

Nop平台并不直接支持JSR303中的`@NotEmpty`等注解。它内部使用更为强大的Schema模型。

1. 所有的实体对象都具有对应的ObjMeta，Nop平台会利用ObjMeta中的Schema元数据来进行验证，它远比JSR303更强大的验证机制。比如说它识别dictName配置，会自动验证字典表
2. 对于不是实体的JavaBean，可以通过`@PropMeta`注解来引入元数据，它会被自动转换为ISchema元数据对象

```
@DataBean
public class MyBean{
   @PropMeta(domain="email")
   public String getEmail(){
     return ...
   }
}
```
