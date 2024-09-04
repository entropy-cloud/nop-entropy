# 如何实现自动生成的只读字段

系统中保存了【姓】和【名】两个字段，希望自动生成一个可用于查询的【姓名】字段。

## 1. 禁止修改
首先，在meta中配置禁止对于【姓名】字段的新增和修改

```xml
<prop name="fullName" insertable="false" updatable="false">

</prop>
```

这里的`insertable=false`和`updatable=false`仅仅是控制Web服务层面是否允许新增和修改，与ORM这种数据存储层无关。
如果设置为false，即使提交到后台，也会被自动忽略。

## 2. 配置autoExpr自动拼接

```xml
<prop name="fullExpr" insertable="false" updatable="false">
    <autoExpr when="save,update">
      return entity.familyName + entity.givenName
    </autoExpr>
</prop>
```

Nop平台中类似订单号这种编码规则就是使用autoExpr的机制来实现，参见[coderule.md](../biz/coderule.md)

只有当使用OrmEntityCopier来实现实体更新时才会运行autoExpr的逻辑，具体使用方式参见内置的CrudBizModel提供的save/update函数。

## 3. 实现自动计算字段的其他方式

### 3.1 通过CrudBizModel提供的回调函数

一般增删改查我们会继承CrudBizModel来实现，它提供了defaultPrepareSave/defaultPrepareUpdate回调函数，在其中可以执行一些额外的扩展逻辑。

```java
@BizModel("NopAuthUser")
@Locale("zh-CN")
public class NopAuthUserBizModel extends CrudBizModel<NopAuthUser> {
  @BizAction
  @Override
  protected void defaultPrepareSave(EntityData<NopAuthUser> entityData, IServiceContext context) {
    super.defaultPrepareSave(entityData, context);
    NopAuthUser entity = entityData.getEntity();
    entity.setFullName(entity.getFamilyName() + entity.getGivenName());
  }
}
```

但是这种方式仅仅是在前台提交save/update调用的时候才会处理，如果是自己通过其他方式保存实体，则不会执行这里的逻辑

### 3.2 通过ORM引擎提供的实体生命周期回调

在实体类上可以实现orm_preSave等回调函数。ORM引擎会在保存实体前执行这些回调函数，因此这种方式比CrudBizModel中的回调更底层，可以确保不会被遗漏。

```java
public class NopAuthUser extends _NopAuthUser {
  public ProcessingResult orm_preSave() {
    this.setFullName(getFamilyName() + getGivenName());
    return ProcessingResult.CONTINUE;
  }
}
```

* 如果回调函数返回STOP，则会跳过该实体的保存。这里的逻辑类似于Hibernate中的生命周期回调函数。

### 3.3 在OrmInterceptor中实现额外的处理逻辑

NopOrm引擎提供了IOrmInterceptor接口，在其中提供了preSave/postSave等回调函数，可以拦截每个实体的增删改查操作。

在每个模块中，还可以定义一个`/{moduleId}/orm/app.orm-interceptor.xml`文件，在其中通过xpl模板语言来定义IOrmInterceptor。

```xml

<interceptor x:schema="/nop/schema/orm/orm-interceptor.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <entity name="io.nop.auth.dao.entity.NopAuthUser">
    <pre-save id="syncFullName">
      <source>
        entity.fullName = entity.familyName + entity.givenName
      </source>
    </pre-save>
  </entity>
</interceptor>
```

### 3.4 仅在查看时使用的显示字段
如果数据库中不需要存储fullName字段，仅仅是在某些显示的时候需要有这个字段，则可以在meta中增加一个扩展字段

```xml
<prop name="fullName">
  <schema type="String" />

  <getter>
    return entity.familyName + entity.givenName
  </getter>
</prop>
```

* 通过getter函数可以执行一个表达式计算得到返回的值
* 缺省情况下insertable和updatable属性都是false。如果是实体上的字段，则会从自动生成的`_NopAuthUser.xmeta`这种文件中继承得到`insertable=true`等属性

### 3.5 使用BizLoader来实现加载逻辑
如果计算逻辑比较复杂，不方便或者不想写在meta文件的getter函数中，则可以在Java的函数中实现加载逻辑。

```
public class NopAuthUserBizModel {
   @BizLoader(autoCreateField=true)
   public String fullName(@ContextSource NopAuthUser entity, IServiceContext context){
      return entity.getFamilyName() + entity.getGivenName();
   }
}
```

* autoCreateField表示如果meta中没有定义fullName这个prop，则会自动创建一个属性。如果希望所有字段都明确在meta中定义，则将autoCreateField的配置删除即可，它缺省为false。
但这个时候就要求在meta中明确定义fullName属性。

### 3.6 在xbiz文件中实现Loader
所有在Java中实现的BizAction和BizLoader都可以在xbiz中用xpl模板语言来实现。xbiz相当于是一个低代码编辑层，它覆盖在底层用Java语言实现的高代码层上，可以覆盖高代码层的任意函数。
这种做法类似于Docker的分层文件系统覆盖。

```xml
<biz>
  <loaders>
    <loader name="fullName" autoCreateField="true">
      <arg name="entity" kind="ContextSource" />
      <source>
        return entity.familyName + entity.givenName
      </source>
    </loader>
  </loaders>
</biz>
```

如果同时在xbiz文件中和BizModel类中都定义了同名的函数，则xbiz中定义的函数优先级更高，它会覆盖Java层定义的函数。

## 4. 在前台实现自动计算的冗余字段

在前台amis中，可以利用它内置的动态计算表达式机制

```json
{
  "type": "tpl",
  "tpl": "${familyName + givenName}"
}
```
