# SQLLib SQL库管理

## 概述

SQLLib提供了类似MyBatis的SQL统一管理功能，支持在XML文件中定义SQL语句、EQL查询、查询条件和验证逻辑。SQLLib是NopORM中最常用的高级特性之一。

## 核心组件

| 组件 | 路径 | 说明 |
|-----|------|------|
| ISqlLibManager | `nop-orm/sql_lib/ISqlLibManager.java` | SQL库管理器接口 |
| SqlLibManager | `nop-orm/sql_lib/SqlLibManager.java` | SQL库管理器实现 |
| SqlLibModel | `nop-orm/sql_lib/SqlLibModel.java` | SQL库模型 |
| SqlLibProxyFactoryBean | `nop-orm/sql_lib/proxy/SqlLibProxyFactoryBean.java` | SQL库代理工厂 |
| SqlMethod | `nop-orm/sql_lib/SqlMethod.java` | SQL执行方法枚举 |

## sql-lib.xml结构

### 完整配置示例

```xml
<sql-lib x:schema="/nop/schema/orm/sql-lib.xdef"
         xmlns:x="/nop/schema/xdsl.xdef"
         xmlns:sql="sql"
         xmlns:c="c"
         xmlns:xpl="xpl">

    <!-- SQL片段（可重用） -->
    <fragments>
        <fragment id="commonWhere">
            where status = 1 and deleted = 0
        </fragment>
    </fragments>

    <sqls>
        <!-- 原生SQL查询 -->
        <sql name="selectById"
             sqlMethod="findFirst"
             rowType="com.example.entity.User"
             colNameCamelCase="true">
            <fields>
                <field name="id" stdSqlType="VARCHAR"/>
                <field name="name" stdSqlType="VARCHAR"/>
                <field name="email" stdSqlType="VARCHAR"/>
            </fields>
            <source>
                select id, name, email from user where id = ${id}
            </source>
        </sql>

        <!-- 支持SQL片段引用 -->
        <sql name="selectActiveUsers"
             sqlMethod="findAll"
             rowType="com.example.entity.User">
            <source>
                select * from user
                <sql:fragment id="commonWhere"/>
                order by create_time desc
            </source>
        </sql>

        <!-- 支持验证输入 -->
        <sql name="findByStatus"
             sqlMethod="findList"
             rowType="com.example.entity.User">
            <validate-input>
                return {
                    "status": [1, 2, 3]
                }
            </validate-input>
            <source>
                select * from user where status = ${status}
            </source>
        </sql>

        <!-- 支持动态SQL过滤 -->
        <eql name="findUsersWithFilter">
            <batchLoadSelection>
                userRoles { role }
            </batchLoadSelection>
            <source>
                <c:import from="/nop/orm/xlib/sql.xlib"/>
                select o
                from com.example.entity.User o
                where 1=1
                <sql:filter>and o.status = :status</sql:filter>
                <sql:filter>and o.name like ${'%' + name + '%'}</sql:filter>
            </source>
        </eql>

        <!-- 支持DQL查询 -->
        <query name="statByDept">
            <source>
                <fields>
                    <field name="deptId"/>
                    <field owner="users" name="id" aggFunc="count" alias="userCount"/>
                </fields>
                <sourceName>com.example.entity.Department</sourceName>
            </source>
        </query>
    </sqls>
</sql-lib>
```

## SQL类型

### 1. 原生SQL (sql标签)

用于执行原生SQL语句。

```xml
<sql name="insertUser" sqlMethod="execute">
    <source>
        insert into user(id, name, email)
        values (${id}, ${name}, ${email})
    </source>
</sql>
```

### 2. EQL查询 (eql标签)

用于执行EQL（ORM查询语言）语句。

```xml
<eql name="findActiveUsers" sqlMethod="findAll" rowType="com.example.entity.User">
    <source>
        <c:import from="/nop/orm/xlib/sql.xlib"/>
        select o
        from com.example.entity.User o
        where o.status = 1
    </source>
</eql>
```

### 3. DQL查询 (query标签)

用于执行领域查询语言，支持聚合统计。

```xml
<query name="statByDept">
    <source>
        <fields>
            <field name="deptId"/>
            <field owner="users" name="id" aggFunc="count" alias="userCount"/>
        </fields>
        <sourceName>com.example.entity.Department</sourceName>
    </source>
</query>
```

## sqlMethod执行方法

| 方法 | 说明 | 返回类型 |
|-----|------|---------|
| findAll | 查询所有记录 | `List<T>` |
| findFirst | 查询第一条记录 | `T` |
| findPage | 分页查询 | `List<T>` 或 `PageBean<T>` |
| exists | 检查是否存在 | `boolean` |
| execute | 执行增删改操作 | `int` (影响行数) |

```xml
<!-- 查询单条记录 -->
<sql name="selectById" sqlMethod="findFirst" rowType="User">
    <source>select * from user where id = ${id}</source>
</sql>

<!-- 查询列表 -->
<sql name="selectAll" sqlMethod="findAll" rowType="User">
    <source>select * from user</source>
</sql>

<!-- 执行更新 -->
<sql name="updateStatus" sqlMethod="execute">
    <source>update user set status = ${status} where id = ${id}</source>
</sql>
```

## 参数绑定

### 1. 命名参数

```xml
<sql name="findByName">
    <source>
        select * from user where name = ${name} and status = ${status}
    </source>
</sql>
```

### 2. 冒号参数

```xml
<eql name="findById">
    <source>
        select o from User o where o.id = :id
    </source>
</eql>
```

### 3. 动态SQL过滤

```xml
<eql name="findUsers">
    <source>
        <c:import from="/nop/orm/xlib/sql.xlib"/>
        select o from User o
        where 1=1
        <sql:filter>and o.status = :status</sql:filter>
        <sql:filter>and o.name like ${'%' + name + '%'}</sql:filter>
    </source>
</eql>
```

## 使用方式

### 方式1: ISqlLibManager直接调用

```java
@BizModel("UserService")
public class UserService {

    @Inject
    private ISqlLibManager sqlLibManager;

    @BizQuery
    public User getUserById(String id) {
        Map<String, Object> args = new HashMap<>();
        args.put("id", id);
        return (User) sqlLibManager.invoke("selectById", null, args);
    }

    @BizQuery
    public List<User> findUsersByStatus(Integer status) {
        Map<String, Object> args = new HashMap<>();
        args.put("status", status);
        return (List<User>) sqlLibManager.invoke("findByStatus", null, args);
    }
}
```

### 方式2: Mapper接口代理（推荐）

创建Mapper接口：

```java
package com.example.mapper;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.orm.SqlLibMapper;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.orm.entity.User;

import java.util.List;

@SqlLibMapper("/com/example/sql/user.sql-lib.xml")
public interface UserMapper {

    User getUserById(@Name("id") String id);

    List<User> findByStatus(@Name("status") Integer status);

    LongRangeBean findPage(String name, LongRangeBean range);
}
```

在BizModel中使用：

```java
@BizModel("UserService")
public class UserService {

    @Inject
    private SqlLibProxyFactoryBean userMapperFactory;

    private UserMapper userMapper;

    @Inject
    public void setUserMapper(SqlLibProxyFactoryBean userMapperFactory) {
        this.userMapper = (UserMapper) userMapperFactory.build();
    }

    @BizQuery
    public User getUserById(String id) {
        return userMapper.getUserById(id);
    }

    @BizQuery
    public List<User> findUsersByStatus(Integer status) {
        return userMapper.findByStatus(status);
    }
}
```

## 高级特性

### 1. 字段类型映射

```xml
<sql name="selectWithTypes" sqlMethod="findFirst" rowType="com.example.entity.User">
    <fields>
        <field name="id" stdSqlType="VARCHAR"/>
        <field name="createTime" stdSqlType="DATETIME" stdDataType="String"/>
        <field name="status" as="userStatus" stdSqlType="INTEGER"/>
    </fields>
    <source>
        select id, create_time, status from user where id = ${id}
    </source>
</sql>
```

### 2. 计算字段

```xml
<sql name="selectWithCompute" sqlMethod="findFirst">
    <fields>
        <field name="id" stdSqlType="VARCHAR"/>
        <field name="fullName" computeExpr="row.firstName + ' ' + row.lastName"/>
    </fields>
    <source>
        select id, first_name, last_name from user where id = ${id}
    </source>
</sql>
```

### 3. 批量加载关联

```xml
<eql name="findUsersWithRoles">
    <batchLoadSelection>
        userRoles { role }
    </batchLoadSelection>
    <source>
        select o from User o
    </source>
</eql>
```

### 4. 缓存支持

```xml
<sql name="selectDict"
     sqlMethod="findAll"
     rowType="com.example.entity.Dict"
     cacheName="userDict"
     cacheKeyExpr="concat(type, '-', key)">
    <source>
        select * from dict where type = ${type}
    </source>
</sql>
```

### 5. 超时设置

```xml
<sql name="slowQuery" sqlMethod="findAll" timeout="5000">
    <source>
        select * from large_table
    </source>
</sql>
```

## 与MyBatis对比

| 特性 | SQLLib | MyBatis |
|-----|--------|---------|
| SQL管理 | XML文件 | XML文件 |
| 原生SQL | 支持 | 支持 |
| ORM查询(EQL) | 支持 | 不支持 |
| 代理接口 | 支持 | 支持 |
| 动态SQL | 支持 | 支持 |
| 缓存 | 内置 | 需配置 |
| 分页 | 原生支持 | 需PageHelper |

## 配置参考

### sql-lib.xdef属性

| 属性 | 类型 | 说明 |
|-----|------|------|
| querySpace | String | 查询空间，对应数据库 |
| rowType | String | 返回结果类型 |
| sqlMethod | SqlMethod | SQL执行方法 |
| cacheName | String | 缓存名称 |
| cacheKeyExpr | String | 缓存Key表达式 |
| timeout | int | 超时时间（毫秒） |
| fetchSize | int | JDBC fetchSize |
| colNameCamelCase | boolean | 列名转驼峰 |

## 源码参考

- 核心接口: `nop-orm/src/main/java/io/nop/orm/sql_lib/ISqlLibManager.java`
- 模型定义: `nop-kernel/src/main/resources/_vfs/nop/schema/orm/sql-lib.xdef`
- 测试示例: `nop-orm/src/test/resources/_vfs/nop/test/sql/test.sql-lib.xml`
- 代理实现: `nop-orm/src/main/java/io/nop/orm/sql_lib/proxy/`
