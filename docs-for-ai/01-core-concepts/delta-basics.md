# Delta定制基础

## Delta文件位置

Delta文件必须放在`_vfs/_delta/{deltaDir}`目录下：

```
src/main/resources/_vfs/_delta/default/nop/auth/app.orm.xml
```

- `_vfs/_delta`：固定前缀
- `default`：delta目录名，可自定义
- `nop/auth/app.orm.xml`：原始模型文件的相对路径

## x:extends属性

Delta文件必须使用`x:extends`属性继承原有模型：

```xml
<orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     x:extends="super">
```

`x:extends="super"`表示继承原始模型。

## Delta操作

### 新增字段

```xml
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

**关键点**：
- `name`属性使用原始实体名：`io.nop.auth.dao.entity.NopAuthUser`
- `className`使用扩展类名：`com.example.myapp.delta.dao.entity.NopAuthUserEx`
- 基础字段必须标记`tag="not-gen"`
- 新增字段不需要标记

### 修改字段

```xml
<column name="userName" stdDomain="string" displayName="用户名"
        length="200" x:override="replace" />
```

### 修改Bean属性

```xml
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

**x:override模式**：
- `replace`：完全覆盖原有节点
- `merge`：合并属性，并按照标签名合并子节点
- `bounded-merge`：只保留派生节点中定义过的子节点
- `remove`：删除基类中的节点

### 删除字段

```xml
<column name="oldField" x:override="remove" />
```

### 删除Bean

```xml
<bean id="unwantedBean" x:override="remove" />
```

## Delta合并优先级

Delta合并按以下优先级执行（从高到低）：

1. `x:post-extends`：后置扩展
2. 当前模型
3. `x:gen-extends`：生成期扩展
4. `x:extends`：基础扩展

## 实体类继承结构

Delta定制生成的实体类遵循三明治架构：

```
CustomClass extends _AutoGenClass extends BaseClass
```

## 完整示例

### 定制NopAuthUser实体

#### Delta ORM模型

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
                <column name="email" stdDomain="email" displayName="邮箱"
                        length="200" tag="not-gen" />

                <column name="mobile" stdDomain="string" displayName="手机号"
                        length="20" />
                <column name="wechat" stdDomain="string" displayName="微信号"
                        length="50" />
            </columns>
        </entity>
    </entities>
</orm>
```

#### Delta BizModel

`myapp-delta/src/main/java/com/example/myapp/delta/biz/NopAuthUserExBizModel.java`:

```java
package com.example.myapp.delta.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.auth.service.entity.NopAuthUserBizModel;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.biz.core.EntityData;
import io.nop.core.context.IServiceContext;
import io.nop.commons.util.StringHelper;

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

#### Delta Bean配置

`myapp-delta/src/main/resources/_vfs/_delta/default/nop/auth/auth-service.beans.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super">

    <bean id="io.nop.auth.service.entity.NopAuthUserBizModel"
          class="com.example.myapp.delta.biz.NopAuthUserExBizModel" />

</beans>
```

## 相关文档

- [常见定制场景](./delta-scenarios.md)
- [代码生成概念](../codegen/codegen-concepts.md)
- [nop-vs-traditional-frameworks.md](../nop-vs-traditional-frameworks.md)

---

