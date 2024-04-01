# Nop入门：极简数据访问层开发

Nop平台的数据访问层使用NopORM引擎，它的功能相当于JPA + MyBatis + SpringData，并且内置了多租户、逻辑删除、动态扩展字段、字段加密等业务常用功能。
NopGraphQL服务框架会自动自动识别ORM的实体对象，自动使用ORM引擎去实现实体关联属性的批量加载。

Nop平台的标准开发模式是先设计数据模型，然后再根据数据模型生成Java实体代码，但是这只是简化开发的一种方式。NopORM支持动态数据模型，我们可以跳过代码生成的步骤
直接手工编写数据模型文件，从而实现数据库访问层。

讲解视频：https://www.bilibili.com/video/BV1yC4y1r716/

最简单的数据访问层开发步骤如下：

## 一. 编写app.orm.xml文件

Nop平台启动时会自动加载所有模块的orm目录下的app.orm.xml文件。

> 例如`/_vfs/nop/demo/orm/app.orm.xml`。 nop/orm目录下具有文件\_module，表示它是一个Nop模块。

```xml
<orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <entities>
        <entity name="app.demo.DemoEntity" tableName="demo_entity"
                className="io.nop.orm.support.DynamicOrmEntity" registerShortName="true">
            <columns>
                <column name="sid" code="SID" propId="1" stdSqlType="VARCHAR" precision="32" tagSet="seq" mandatory="true"
                        primary="true"/>
                <column name="name" code="NAME" propId="2" stdSqlType="VARCHAR" precision="100" mandatory="true"/>
                <column name="status" code="STATUS" propId="3" stdSqlType="INTEGER"/>
            </columns>
        </entity>
    </entities>
</orm>
```

1. 如果不生成特定的Java实体类，可以使用系统内置的动态实体类DynamicEntity
2. 每个字段都必须指定propId属性，不要求连续，但是不能重复。
3. 主键字段需要标注primary=true。指定tagSet=seq表示为它增加seq标签，从而在保存的时候自动生成随机值
4. 如果application.yaml中配置了`nop.orm.init-database-schema: true`，则系统启动的时候会自动根据模型配置创建数据库表

## 二. 通过IDaoProvider获取IEntityDao

我们可以增加一个DemoEntityBizModel，在其中通过`@Inject`自动注入IDaoProvider。一般情况下实现增删改查的BizModel会从CrudBizModel继承，它已经实现了
大量标准的CRUD操作。这里为了演示功能，我们选择不继承已有的CrudBizModel，完全手工编写。

```java
@BizModel("DemoEntity")
public class DemoEntityBizModel {

    // 注意，字段不能声明为private。NopIoC无法注入私有成员变量
    @Inject
    IDaoProvider daoProvider;

    @BizQuery
    @GraphQLReturn(bizObjName = "DemoEntity")
    public IOrmEntity getEntity(@Name("id") String id) {
        IEntityDao<IOrmEntity> dao = daoProvider.dao("app.demo.DemoEntity");
        return dao.getEntityById(id);
    }

    @BizMutation
    @GraphQLReturn(bizObjName = "DemoEntity")
    public IOrmEntity saveEntity(@Name("data") Map<String, Object> data) {
        IEntityDao<IOrmEntity> dao = daoProvider.dao("app.demo.DemoEntity");
        OrmEntity entity = dao.newEntity();
        BeanTool.instance().setProperties(entity, data);
        dao.saveEntity(entity);
        return entity;
    }

    @BizQuery
    @GraphQLReturn(bizObjName = "DemoEntity")
    public List<IOrmEntity> findByName(@Name("name") String name) {
        IEntityDao<IOrmEntity> dao = daoProvider.dao("app.demo.DemoEntity");

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.contains("name", name));
        return dao.findAllByQuery(query);
    }
}
```

* 一般情况下`@BizModel`注解指定的对象名与实体对象名相同，便于代码定位。

* 通过`daoProvider.dao(entityName)`可以获取到指定实体类对应的Dao对象。在Nop平台中我们只会使用平台内置的IEntityDao接口，它已经提供了足够丰富的方法，
  不需要业务开发人员再去扩展Dao接口。如果有些功能IEntityDao接口无法满足需求，可以使用`IOrmTemplate`或者`SqlLibMapper`机制。

* 服务函数可以返回实体对象。这一点与SpringMVC的Controller不同。Controller一般只能返回可以自动序列化为JSON的DTO对象，否则无法控制哪些字段可以返回到前台。
  当我们不是直接返回字段，而是返回某种动态处理结果的时候，在Spring框架中也需要通过DTO进行适配。但是在使用NopGraphQL框架时，我们可以直接返回实体，然后通过xmeta元数据来控制返回
  字段，并且增加额外的转换逻辑。
  需要注意的是，我们现在使用的是动态实体对象，因此无法根据类名来确定是哪个实体类型，所以需要通过`@GraphQLReturn`注解来指明返回的对象类型是什么。

* 在`/_vfs/nop/demo/model/`目录下需要增加一个`DemoEntity/DemoEntity.xmeta`元数据文件。当GraphQL服务函数返回的类型为指定对象类型时，会加载这里的元数据文件来获取对象信息。
  在这个文件中我们也可以增加实体上没有的字段，通过`getter`等配置实现动态计算。

## 三. 通过XMeta模型增加自定义字段

NopGraphQL框架实际返回的业务对象的属性可以由xmeta模型来控制。通过它还可以控制访问权限、转换逻辑等。通过getter属性我们可以为业务对象增加自定义字段。

```xml
<meta x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <props>
        <prop name="sid" displayName="SID" queryable="true">
            <schema type="String"/>
        </prop>

        <prop name="name" displayName="名称" queryable="true" insertable="true" updatable="true">
            <schema type="String"/>
        </prop>

        <prop name="status" displayName="状态" queryable="true" insertable="true" updatable="true">
            <schema type="Integer"/>
        </prop>

        <prop name="status_label" displayName="状态文本">
            <schema type="String"/>
            <getter>
                <c:script><![CDATA[
                    if(entity.status == 1)
                        return "ACTIVE";
                    return "INACTIVE";
                ]]></c:script>
            </getter>
        </prop>
    </props>
</meta>
```

可以看出xmeta中的信息与orm模型中的信息有一定的重叠之处，但是它们用于不同的目的，一般并不会完全一致。Nop平台中的标准做法是使用编译期元编程自动实现两者之间的信息同步，
并利用Delta合并来引入差异信息。在本文中我们不会涉及这些细节，感兴趣的读者可以参考[Nop平台元编程](../../dev-guide/xlang/meta-programming.md)

## 四. 通过SqlLibMapper接口调用SQL语句

### 1. 声明接口DemoMapper, 通过`@SqlLibMapper`注解与sql文件关联

```java
@SqlLibMapper("/nop/demo/sql/demo.sql-lib.xml")
public interface DemoMapper {
    IOrmEntity findFirstByName(@Name("name") String name);
}
```

### 2. 在beans.xml中注册Mapper接口类

因为NopIoC并不使用类扫描机制，所以我们需要手动在app-simple-demo.beans.xml中增加bean的定义。

```xml
    <bean id="io.nop.auth.dao.mapper.NopAuthRoleMapper" class="io.nop.orm.sql_lib.proxy.SqlLibProxyFactoryBean"
          ioc:type="@bean:id" ioc:bean-method="build">
        <property name="mapperClass" value="@bean:type"/>
    </bean>
```

### 3. 在demo.sql-lib.xml增加SQL语句或者EQL对象查询语句

```xml
<sql-lib x:scheme="/nop/schema/orm/sql-lib.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <sqls>
        <eql name="findFirstByName" sqlMethod="findFirst">
            <source>
                select o from DemoEntity o where o.name like ${'%' + name + '%'}
            </source>
        </eql>
    </sqls>
</sql-lib>
```

### 4. 在BizModel中调用SqlLibMapper

```java
class DemoEntityBizModel{
    @Inject
    DemoMapper demoMapper;

    @BizQuery
    @GraphQLReturn(bizObjName = "DemoEntity")
    public IOrmEntity findBySql(@Name("name") String name) {
        return demoMapper.findFirstByName(name);
    }
}
```
