# 1. 传递一些实体上没有的字段到后台

在meta中增加prop，然后设置它的virtual属性为true，表示是虚拟字段，就不会自动拷贝到实体上。

如果不允许读取，则需要配置readable=false，这样查询后台的时候就不允许从实体上读取此属性。

```xml
<meta>
  <props>
    <prop name="myProp" readable="false" virtual="true">
        <schema stdDomain="string" />
    </prop>
  </props>
</meta>
```

在后台可以通过entityData读取

```java
class MyEntityBizModel extends CrudBizModel {
    @BizMutation
    public MyEntity myMethod(@Name("data") Map<String, Object> data, IServiceContext ctx) {
        return doSave(data, null, (entityData, ctx) -> {
            String myProp = (String) entityData.getData().get("myProp");
            //...
        }, ctx);
    }
}  
```

## 2. 在事务提交成功之后再执行某个操作

使用ITransactionTemplate.afterCommit(null, action)函数。

CrudBizModel已经注入了transactionTemplate，可以通过this.txn()来使用。

## 3. 扩展CrudBizModel内置的save/update等操作，增加业务相关的特殊处理

如果只是少量的更新几个字段，原则上可以使用自定义的bean作为参数，然后直接调用dao().save(entity)即可。
但是如果要接收大量字段，并考虑到未来是否有新扩展的字段需要保存，保存的时候需要执行定制的处理逻辑等，这时就
不要使用自定义的JavaBean作为参数，应该直接使用内置的doSave等函数。

```javascript
    @BizMutation
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    @BizMakerChecker(tryMethod = METHOD_TRY_SAVE)
    public MyEntity my_save(@Name("data") Map<String, Object> data, IServiceContext context) {
        // 如果需要限制只允许接收部分字段，可以在meta中配置。如果没有特殊限制，inputSelection设置为null即可
        FieldInputSelection inputSelection = getObjMeta().getFieldSelection("my_selectio"); 
        return doSave(data, inputSelection, this::myPrepareSave, context);
    }
```

* inputSelection参数可以用于限制只接收前端传过来的某些参数
* prepareSave回调函数可以用于定制实体实际保存前执行的额外的业务逻辑
