# 1. 传递一些实体上没有的字段到后台
在meta中增加prop，然后设置它的virtual属性为true，表示是虚拟字段，就不会自动拷贝到实体上。

如果不允许读取，则需要配置readable=false，这样查询后台的时候就不允许从实体上读取此属性。

````xml
<meta>
  <props>
    <prop name="myProp" readable="false" virtual="true">
        <schema stdDomain="string" />
    </prop>
  </props>
</meta>
````

在后台可以通过entityData读取

````java
class MyEntityBizModel extends CrudBizModel {
    @BizMutation
    public MyEntity myMethod(@Name("data") Map<String, Object> data, IServiceContext ctx) {
        return doSave(data, null, (entityData, ctx) -> {
            String myProp = (String) entityData.getData().get("myProp");
            //...
        }, ctx);
    }
}  
````