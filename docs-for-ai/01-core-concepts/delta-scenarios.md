# 常见定制场景

## 场景1：定制内置模块的字段

### Delta ORM模型

`myapp-delta/src/main/resources/_vfs/_delta/default/nop/auth/app.orm.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     x:extends="super">

    <entities>
        <entity className="com.example.myapp.delta.dao.entity.NopAuthUserEx"
                displayName="用户" name="io.nop.auth.dao.entity.NopAuthUser"
                tableName="nop_auth_user">
            <columns>
                <column name="userId" stdDomain="string" primary="true"
                        displayName="用户ID" length="32" tag="not-gen" />
                <column name="userName" stdDomain="string" displayName="用户名"
                        length="100" tag="not-gen" />

                <column name="mobile" stdDomain="string" displayName="手机号"
                        length="20" />
                <column name="wechat" stdDomain="string" displayName="微信号"
                        length="50" />
            </columns>
        </entity>
    </entities>
</orm>
```

### 数据库

```sql
ALTER TABLE nop_auth_user ADD COLUMN mobile VARCHAR(20);
ALTER TABLE nop_auth_user ADD COLUMN wechat VARCHAR(50);
```

---

## 场景2：定制内置模块的API

### Delta BizModel

`myapp-delta/src/main/java/com/example/myapp/delta/biz/NopAuthUserExBizModel.java`:

```java
@BizModel("NopAuthUser")
public class NopAuthUserExBizModel extends NopAuthUserBizModel {

    @BizQuery
    public NopAuthUser getUserByMobile(String mobile) {
        NopAuthUser example = new NopAuthUser();
        example.setMobile(mobile);
        return userDao.findFirstByExample(example);
    }
}
```

### Delta Bean配置

`myapp-delta/src/main/resources/_vfs/_delta/default/nop/auth/auth-service.beans.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super">

    <bean id="io.nop.auth.service.entity.NopAuthUserBizModel"
          class="com.example.myapp.delta.biz.NopAuthUserExBizModel" />

</beans>
```

---

## 场景3：定制内置模块的页面

### Delta XView模型

`myapp-delta/src/main/resources/_vfs/_delta/default/nop/auth/view/NopAuthUser/NopAuthUser.view.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<view x:schema="/nop/schema/xui/xview.xdef" xmlns:x="/nop/schema/xdsl.xdef"
      x:extends="super">

    <grid id="list" x:override="replace">
        <columns>
            <column id="userId" displayName="用户ID" />
            <column id="userName" displayName="用户名" />
            <column id="mobile" displayName="手机号" />
            <column id="wechat" displayName="微信号" />
        </columns>
    </grid>

</view>
```

---

## 场景4：定制内置模块的配置

### Delta Bean配置

`myapp-delta/src/main/resources/_vfs/_delta/default/nop/auth/auth-service.beans.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super">

    <bean id="nopAuthFilterConfig">
        <property name="authPaths" x:override="merge">
            <list>
                <value>/mall*</value>
                <value>/custom*</value>
            </list>
        </property>
    </bean>

</beans>
```

---

## 场景5：删除内置模块的字段

### Delta ORM模型

`myapp-delta/src/main/resources/_vfs/_delta/default/nop/auth/app.orm.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     x:extends="super">

    <entities>
        <entity name="io.nop.auth.dao.entity.NopAuthUser">
            <columns>
                <column name="clientId" x:override="remove" />
            </columns>
        </entity>
    </entities>
</orm>
```

### 数据库

```sql
ALTER TABLE nop_auth_user DROP COLUMN client_id;
```

---

## 场景6：定制内置模块的业务逻辑

### Delta BizModel

`myapp-delta/src/main/java/com/example/myapp/delta/biz/NopAuthUserExBizModel.java`:

```java
@BizModel("NopAuthUser")
public class NopAuthUserExBizModel extends NopAuthUserBizModel {

    @Override
    protected void defaultPrepareSave(EntityData<NopAuthUser> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);

        NopAuthUser user = entityData.getEntity();

        if (StringHelper.isNotEmpty(user.getMobile())) {
            if (!user.getMobile().matches("^1[3-9]\\d{9}$")) {
                throw new NopException(ERR_INVALID_MOBILE)
                    .param("mobile", user.getMobile());
            }
        }
    }
}
```

### Delta Bean配置

`myapp-delta/src/main/resources/_vfs/_delta/default/nop/auth/auth-service.beans.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super">

    <bean id="io.nop.auth.service.entity.NopAuthUserBizModel"
          class="com.example.myapp.delta.biz.NopAuthUserExBizModel" />

</beans>
```

---

## 场景7：定制内置模块的数据源

### Delta Bean配置

`myapp-delta/src/main/resources/_vfs/_delta/default/nop/dao/dao-defaults.beans.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super">

    <bean id="nopDataSource" x:override="remove" />

    <bean id="nopDataSource" class="com.zaxxer.hikari.HikariDataSource">
        <property name="jdbcUrl" value="jdbc:mysql://localhost:3306/myapp" />
        <property name="username" value="root" />
        <property name="password" value="password" />
        <property name="driverClassName" value="com.mysql.cj.jdbc.Driver" />
    </bean>

</beans>
```

---

## 场景8：定制内置模块的拦截器

### 自定义拦截器

`myapp-delta/src/main/java/com/example/myapp/delta/interceptor/UserSaveInterceptor.java`:

```java
@AopInterceptor
public class UserSaveInterceptor {

    public void beforeSave(EntityData<NopAuthUser> entityData, IServiceContext context) {
        NopAuthUser user = entityData.getEntity();
        LOG.info("Before save user: userId={}, userName={}", user.getUserId(), user.getUserName());
    }

    public void afterSave(EntityData<NopAuthUser> entityData, IServiceContext context) {
        NopAuthUser user = entityData.getEntity();
        LOG.info("After save user: userId={}, userName={}", user.getUserId(), user.getUserName());
    }
}
```

### Delta Bean配置

`myapp-delta/src/main/resources/_vfs/_delta/default/nop/auth/auth-service.beans.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super">

    <bean id="userSaveInterceptor" class="com.example.myapp.delta.interceptor.UserSaveInterceptor" />

    <bean id="io.nop.auth.service.entity.NopAuthUserBizModel"
          class="com.example.myapp.delta.biz.NopAuthUserExBizModel">
        <property name="interceptors" x:override="merge">
            <list>
                <ref bean="userSaveInterceptor" />
            </list>
        </property>
    </bean>

</beans>
```

---

## 相关文档

- [Delta定制基础](./delta-basics.md)
- [常见开发任务](../quickstart/common-tasks.md)
- [服务层开发指南](../03-development-guide/service-layer.md)

---

