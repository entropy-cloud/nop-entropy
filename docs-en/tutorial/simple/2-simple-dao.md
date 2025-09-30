# Getting Started with Nop: Minimalistic Data Access Layer Development

The Nop platform’s data access layer uses the NopORM engine, which is equivalent to JPA + MyBatis + SpringData, and comes with commonly used business features such as multi-tenancy, logical deletion, dynamic extension fields, and field encryption. The NopGraphQL service framework automatically recognizes ORM entity objects and uses the ORM engine to implement batch loading of associated properties.

The standard development approach on the Nop platform is to design the data model first and then generate Java entity code based on the model; however, this is only a way to simplify development. NopORM supports dynamic data models, so we can skip the code generation step and manually write data model files to build the data access layer.

Tutorial video: https://www.bilibili.com/video/BV1yC4y1r716/

The simplest steps for data access layer development are as follows:

## 1. Write the app.orm.xml file

When the Nop platform starts, it automatically loads the app.orm.xml file under the orm directory of all modules.

> For example `/_vfs/nop/demo/orm/app.orm.xml`. If there is a file \_module under the nop/orm directory, it indicates that this is a Nop module.

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

1. If you do not generate a specific Java entity class, you can use the built-in dynamic entity class DynamicEntity.
2. Each field must specify the propId attribute. It does not have to be consecutive, but it must be unique.
3. The primary key field must be marked with primary=true. Specifying tagSet=seq adds the seq tag so that a random value is automatically generated when saving.
4. If `nop.orm.init-database-schema: true` is configured in application.yaml, the system will automatically create database tables based on the model configuration at startup.

## 2. Obtain IEntityDao via IDaoProvider

We can add a DemoEntityBizModel and use `@Inject` to automatically inject IDaoProvider. In general, a BizModel implementing create/read/update/delete inherits from CrudBizModel, which already implements a wealth of standard CRUD operations. Here, to demonstrate the functionality, we will not extend the existing CrudBizModel and will write everything manually.

```java
@BizModel("DemoEntity")
public class DemoEntityBizModel {

    // Note: the field must not be declared private. NopIoC cannot inject private member variables
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

* In most cases, the object name specified by the `@BizModel` annotation is the same as the entity object name, making it easier to locate the code.

* You can obtain the Dao object corresponding to a specified entity class via `daoProvider.dao(entityName)`. On the Nop platform we only use the built-in IEntityDao interface, which already provides a sufficiently rich set of methods, so business developers do not need to extend the Dao interface. If some needs are not met by IEntityDao, you can use the `IOrmTemplate` or `SqlLibMapper` mechanisms.

* Service functions can return entity objects. This differs from the Controller in Spring MVC, which generally can only return DTOs that can be automatically serialized to JSON; otherwise, you cannot control which fields are exposed to the frontend. When we are not directly returning fields but some dynamically processed result, the Spring framework also needs DTOs for adaptation. With the NopGraphQL framework, however, we can return entities directly and control returned fields and add extra transformation logic via xmeta metadata. Note that we are using dynamic entity objects now, so we cannot determine which entity type it is based on the class name. Therefore, we need the `@GraphQLReturn` annotation to indicate the type of the returned object.

* You need to add a `DemoEntity/DemoEntity.xmeta` metadata file under the `/_vfs/nop/demo/model/` directory. When a GraphQL service function returns a specified object type, it loads this metadata file to obtain object information. In this file, we can also add fields not present on the entity and implement dynamic computation via the `getter` configuration.

## 3. Add custom fields via the XMeta model

The properties of the business objects actually returned by the NopGraphQL framework can be controlled by the xmeta model. It can also control access permissions, transformation logic, and more. By using the getter attribute, we can add custom fields to business objects.

```xml
<meta x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <props>
        <prop name="sid" displayName="SID" queryable="true">
            <schema type="String"/>
        </prop>

        <prop name="name" displayName="Name" queryable="true" insertable="true" updatable="true">
            <schema type="String"/>
        </prop>

        <prop name="status" displayName="Status" queryable="true" insertable="true" updatable="true">
            <schema type="Integer"/>
        </prop>

        <prop name="status_label" displayName="Status Text">
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

As you can see, some information in xmeta overlaps with the information in the orm model, but they serve different purposes and are generally not completely identical. The standard approach on the Nop platform is to use compile-time metaprogramming to automatically synchronize information between the two and use Delta merging to incorporate differences. We won’t delve into these details here; interested readers can refer to [Nop Platform Meta-Programming](../../dev-guide/xlang/meta-programming.md)

## 4. Execute SQL statements via the SqlLibMapper interface

### 1. Declare the DemoMapper interface and associate it with the SQL file via `@SqlLibMapper`

```java
@SqlLibMapper("/nop/demo/sql/demo.sql-lib.xml")
public interface DemoMapper {
    IOrmEntity findFirstByName(@Name("name") String name);
}
```

### 2. Register the Mapper interface class in beans.xml

Because NopIoC does not use a classpath scanning mechanism, we need to manually add the bean definition in app-simple-demo.beans.xml.

```xml
    <bean id="io.nop.auth.dao.mapper.NopAuthRoleMapper" class="io.nop.orm.sql_lib.proxy.SqlLibProxyFactoryBean"
          ioc:type="@bean:id" ioc:bean-method="build">
        <property name="mapperClass" value="@bean:type"/>
    </bean>
```

### 3. Add SQL statements or EQL object query statements in demo.sql-lib.xml

```xml
<sql-lib x:schema="/nop/schema/orm/sql-lib.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <sqls>
        <eql name="findFirstByName" sqlMethod="findFirst">
            <source>
                select o from DemoEntity o where o.name like ${'%' + name + '%'}
            </source>
        </eql>
    </sqls>
</sql-lib>
```

The value of sqlMethod corresponds to the `io.nop.orm.sql_lib.SqlMethod` enum, which includes options such as findAll, findFirst, findPage, exists, and execute to indicate how to execute the generated EQL/SQL statement.

In practice, it dynamically constructs an SQL object and then invokes methods such as findFirst(SQL sql) on IOrmTemplate or IJdbcTemplate.

### 4. Invoke SqlLibMapper in the BizModel

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
<!-- SOURCE_MD5:bdfa9733cc4673078101ae534f1e5a9b-->
