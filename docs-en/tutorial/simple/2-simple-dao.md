# Nop Introduction: Simplified Data Access Layer Development

The data access layer in the Nop platform uses the Nop ORM Engine, which combines the functionality of JPA + MyBatis + SpringData and includes built-in features such as multi-tenant support, logical deletion, dynamic field extensions, and field encryption. The Nop GraphQL service framework automatically identifies ORM entities and utilizes the ORM engine to implement entity relationships for batch loading.

The standard development pattern on the Nop platform involves designing the data model first, followed by generating Java entity code based on the data model. However, this is just one way to simplify development. The Nop ORM supports dynamic data models, allowing you to skip the code generation step and manually create data model files instead, thus directly implementing the database access layer.

Video Explanation: [Bilibili Video](https://www.bilibili.com/video/BV1yC4y1r716/)

The simplest steps for data access layer development are as follows:

## 1. Writing app.orm.xml File

When the Nop platform starts, it automatically loads all modules by reading the app.orm.xml file located in the orm directory under the nop schema.

> Example: `/_vfs/nop/demo/orm/app.orm.xml`  
> The nop/orm directory contains a file named `_module`, indicating that it is a Nop module.

```xml
<orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <entities>
        <entity name="app.demo.DemoEntity" tableName="demo_entity"
                className="io.nop.orm.support.DynamicOrmEntity" registerShortName="true">
            <columns>
                <column name="sid" code="SID" propId="1" stdSqlType="VARCHAR" precision="32" tagSet="seq" mandatory="true" primary="true"/>
                <column name="name" code="NAME" propId="2" stdSqlType="VARCHAR" precision="100" tagSet="seq" mandatory="true"/>
                <column name="status" code="STATUS" propId="3" stdSqlType="INTEGER"/>
            </columns>
        </entity>
    </entities>
</orm>
```

1. If you do not generate specific Java entity classes, you can use the built-in DynamicEntity.
2. Each field must have a propId attribute. They do not need to be consecutive but cannot repeat.
3. Primary key fields require primary="true". Specifying tagSet="seq" adds a sequence tag, which generates random values during insertion.
4. If `nop.orm.init-database-schema: true` is configured in application.yaml, the system will automatically create database tables at startup based on the model configuration.

## 2. Obtaining IEntityDao

You can add a DemoEntityBizModel to demonstrate functionality. Use `@Inject` to automatically inject IDaoProvider. In most cases, BizModels inherit from CrudBizModel, which already implements standard CRUD operations. For demonstration purposes, we will not inherit from the existing CrudBizModel and manually create the data model instead.


```java
@BizModel("DemoEntity")
public class DemoEntityBizModel {

    // Note that fields cannot be declared as private. Nop IoC cannot inject private member variables.
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

* Generally, the `@BizModel` annotation specifies the object name, which matches the entity name for easier location.
* The `daoProvider.dao(entityName)` method retrieves the DAO object corresponding to the specified entity class. In the Nop platform, we only use the built-in `IEntityDao` interface, which provides sufficiently rich methods and requires no additional extensions from developers. If the `IEntityDao` interface does not meet certain requirements, you can use `IOrmTemplate` or `SqlLibMapper` mechanisms to add custom logic.
* Service methods can return entity objects directly. This is different from Spring MVC controllers, which typically return serialized DTOs unless specified otherwise. When returning a dynamic result instead of a direct field, you need to adapt it using a DTO in Spring frameworks. However, with the Nop GraphQL framework, you can directly return entities and control which fields are returned using metadata.
* To specify the return type for an entity, you must use the `@GraphQLReturn` annotation. Without this, the system cannot determine which entity type to return based on the class name alone. The `@GraphQLReturn` annotation is essential here.

* In the directory `/_vfs/nop/demo/model/`, you need to add a metadata file named `DemoEntity/DemoEntity.xmeta`. When the GraphQL service function returns a specific type, it loads this metadata file to retrieve object information.
* This file can also include additional fields not present in the entity itself. For example, using properties like `getter` methods or other configurations allows for dynamic computation of values.

## Adding Custom Fields Using XMeta

The Nop GraphQL framework allows you to define custom fields for business objects by leveraging the XMeta model. Through this mechanism, you can control which fields are returned, access rights, and transformation logic. You can also dynamically add fields that are not present in the entity itself using methods like `getter`.

For example:
- In the metadata file `DemoEntity.xmeta`, you can define custom fields.
- The system will use this file to determine which fields should be exposed when the service function returns a specific type.


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

It is evident that the information in `xmeta` overlaps with the information in the `orm` model to some extent. However, they serve different purposes and are generally not fully consistent. The standard practice in the Nop platform is to use compiled-time meta programming to synchronize information between them and leverage Delta merging to introduce differences. This document does not delve into these details, but interested readers can refer to [Nop platform meta programming](../../dev-guide/xlang/meta-programming.md).

## Four. Calling SQL Statements Using `SqlLibMapper` Interface

### 1. Declaring the `DemoMapper` interface using `@SqlLibMapper` annotation and linking it with a SQL file

```java
@SqlLibMapper("/nop/demo/sql/demo.sql-lib.xml")
public interface DemoMapper {
    IOrmEntity findFirstByName(@Name("name") String name);
}
```

### 2. Registering the `Mapper` Interface in `beans.xml`

Since Nop IoC does not use class scanning, we need to manually register the mapper interface in `app-simple-demo.beans.xml`.

```xml
<bean id="io.nop.auth.dao.mapper.NopAuthRoleMapper" class="io.nop.orm.sql_lib.proxy.SqlLibProxyFactoryBean"
      ioc:type="@bean:id" ioc:bean-method="build">
    <property name="mapperClass" value="@bean:type"/>
</bean>
```

### 3. Adding SQL Statements or EQL Queries in `demo.sql-lib.xml`

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

### 4. Calling `SqlLibMapper` in `BizModel`

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
