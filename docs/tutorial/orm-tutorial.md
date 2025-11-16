# NopORM 教程：支持企业级SaaS应用的下一代ORM 框架

## 什么是 NopORM？

NopORM 是基于可逆计算理论设计的下一代 ORM 框架，它不仅提供了类似 JPA/Hibernate 的完整 ORM 功能，还内置了对 OLAP 分析查询的支持，实现了类似润乾 DQL 语言的多维查询能力。相比于传统 ORM 框架，NopORM 在保持易用性的同时，解决了企业级应用中的复杂查询、性能优化和系统扩展性等痛点。

## 核心特性概览

### 1. 统一的 SQL 管理
```xml
<!-- 在 sql-lib.xml 中统一管理 SQL/EQL/DQL -->
<sql-lib>
    <sqls>
        <sql name="findActiveUsers">
            select * from nop_auth_user where status = 1
        </sql>
        
        <eql name="findUserWithDepartment">
            select u, u.department.name 
            from NopAuthUser u 
            where u.department.status = 1
        </eql>
        
        <query name="userAnalysis">
            <sourceName>NopAuthUser</sourceName>
            <fields>
                <field name="department.name"/>
                <field name="status" aggFunc="count" alias="userCount"/>
            </fields>
            <groupBy>
                <field name="department.name"/>
            </groupBy>
        </query>
    </sqls>
</sql-lib>
```

### 2. 声明式数据访问
与 MyBatis 的命令式风格不同，NopORM 采用声明式数据访问：

```java
// MyBatis - 需要显式调用 update
user.setName("newName");
userMapper.update(user);

// NopORM - 修改即更新
user.setName("newName");
// 自动检测变更，在 session.flush() 时生成优化后的 UPDATE 语句
```

### 3. 对象查询语言 (EQL)
EQL = SQL + AutoJoin，支持复杂的对象导航：

```sql
// 复杂关联自动转换为 JOIN 查询
select o from Order o 
where o.customer.address.city = 'Beijing' 
  and o.items.product.category.name = 'Electronics'

// 多对多关联查询
select b from Book b 
where b.authors.name like '张%'
```

## 快速开始

### 1. 添加依赖
```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-orm</artifactId>
    <version>2.0.0</version>
</dependency>
```

### 2. 配置数据源
```yaml
nop:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/test
    username: root
    password: 123456
```

### 3. 定义数据模型并生成实体

**第一步：在模型文件中定义实体**

在 `/src/main/resources/_vfs/` 目录下创建 `orm.xml` 或使用 Excel 格式的 `orm.xlsx`：

```xml
<!-- orm.xml -->
<orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <entities>
        <entity name="app.NopAuthUser" tableName="nop_auth_user">
            <columns>
                <column name="sid" code="SID" stdDomain="id" primaryKey="true"/>
                <column name="user_name" code="USER_NAME" stdDomain="string" length="50"/>
                <column name="email" code="EMAIL" stdDomain="email" length="100"/>
            </columns>
            <relations>
                <to-one name="department" refEntityName="app.NopAuthDepartment"/>
                <to-many name="roles" refEntityName="app.NopAuthUserRole" collectionType="set"/>
            </relations>
        </entity>
    </entities>
</orm>
```

**第二步：使用nop-cli生成代码**

```bash
# 执行代码生成
java -jar nop-cli.jar gen model/demo.orm.xml "-o=."
```

**第三步：使用生成的实体**

```java
// 生成的实体类位于 target/generated-sources 目录
// 实体类名如：NopAuthUser.java 等

// 创建实体实例
NopAuthUser user = new NopAuthUser();
user.setUserName("zhangsan");
user.setEmail("zhangsan@example.com");
```

### 4. 基本 CRUD 操作
```java
@Inject
IDaoProvider daoProvider;

// 获取DAO实例
IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
// 创建
NopAuthUser user = dao.newEntity();
user.setUserName("zhangsan");
user.setEmail("zhangsan@example.com");
dao.saveEntity(user);

// 查询
NopAuthUser user = dao.getEntityById("1");

NopAuthUser example = new NopAuthUser();
example.setStatus(1);
List<NopAuthUser> users = dao.findAllByExample(example);

// 复杂查询
QueryBean query = new QueryBean();
query.addFilter(and(
    eq(NopAuthUser.PROP_NAME_userName, "zhangsan"),
    gt(NopAuthUser.PROP_NAME_createTime, startDate)
)).limit(100);
List<NopAuthUser> users = dao.findPageByQuery(query);

// 更新 - 无需显式调用 update
user.setEmail("new@example.com");
// 自动在 session.flush() 时同步到数据库

// 删除
dao.deleteEntity(user);
```

## 高级特性详解

### 1. 扩展字段支持
无需修改数据库表结构即可添加扩展字段：

```xml
<orm>
  <x:post-extends>
    <orm-gen:DefaultPostExtends xpl:lib="/nop/orm/xlib/orm-gen.xlib"/>
  </x:post-extends>
  
  <entities>
	<!-- 在 orm.xml 模型中标记 use-ext-field -->
	<entity name="app.MyEntity" tableName="my_entity" tagSet="use-ext-field">
		<aliases>
			<alias name="extFieldA" propPath="extFields.fldA.string" type="String"/>
			<alias name="extFieldB" propPath="extFields.fldB.int" type="Integer"/>
		</aliases>
	</entity>
  </entities>
</orm>  
```

使用扩展字段与普通字段完全一致：
```java
// Java 代码
entity.setExtFieldA("value");
String value = entity.getExtFieldA();

// EQL 查询
select o from MyEntity o where o.extFieldA = 'value' order by o.extFieldB

// GraphQL 查询
query {
    MyEntity_list {
        extFieldA
        extFieldB
    }
}
```

### 2. DQL 多维查询
面向 OLAP 的查询，简化复杂数据分析：

```java
QueryBean query = new QueryBean();
query.setSourceName("NopAuthUser");
query.fields(
    mainField("department.name"),
    subField("roles", "roleId").count().alias("roleCount"),
    mainField("status")
);
query.addOrderField("department.name", true);

List<Map<String, Object>> result = ormTemplate.findListByQuery(query);
```

对应的 SQL 需要复杂的 JOIN 和子查询，而 DQL 自动处理：
- 自动识别关联路径
- 内存中的 Hash Join 优化
- 支持分页和复杂过滤

### 3. 逻辑删除和审计

通过stdDomain标记特殊字段：

```xml
<entity name="app.MyEntity">
    <columns>
        <column name="delFlag" stdDomain="delFlag" ... />
        <column name="delVersion" stdDomain="delVersion" .../>
	<column name="updatedBy" stdDomain="updater" .../>
    </columns>
</entity>
```

```java
// 自动处理逻辑删除
dao.deleteEntity(user); // 实际设置 deleted=1

// 自动审计字段  - 自动设置，无需手动调用
// entity.setUpdateBy(currentUser); // 自动设置
// entity.setUpdateTime(new Date()); // 自动设置
```

### 4. 多数据源支持
在实体定义中增加querySpace属性
```xml
<!-- 在orm.xml实体定义中配置querySpace -->
<entity name="app.NopAuthUser" tableName="nop_auth_user" querySpace="main">
    <!-- 实体定义 -->
</entity>

<entity name="app.ReportEntity" tableName="report_data" querySpace="report">
    <!-- 实体定义 -->
</entity>
```

在ioc配置中增加数据源定义

```xml
<!-- 配置多个数据源 -->
<bean id="nopDataSource_main" class="com.zaxxer.hikari.HikariDataSource">
    <!-- 主数据源配置 -->
</bean>

<bean id="nopDataSource_report" class="com.zaxxer.hikari.HikariDataSource">
    <!-- 报表数据源配置 -->
</bean>
```

访问实体时会自动使用不同的querySpace


### 5. 字段级安全控制
```xml
<!-- 字段掩码配置 -->
<prop name="creditCard" ui:maskPattern="6*4">
    <schema type="String"/>
</prop>

<!-- 字段加密 -->
<column name="secret_data" stdDomain="encrypted"/>
```

### 6. 实体变更拦截器
在`/{moduleId}/orm/app.orm-interceptor.xml`中监听每个实体的pre-update/pre-save等事件。

```xml
<interceptor x:schema="/nop/schema/orm/orm-interceptor.xdef" 
             xmlns:x="/nop/schema/xdsl.xdef"
             xmlns:c="c">
    
    <!-- 用户实体的拦截逻辑 -->
    <entity name="app.NopAuthUser">
        <pre-update id="trackUserChange">
            <source>
                <!-- 记录用户信息变更 -->
                <c:if test="${entity.orm_propDirtyByName('email')}">
                    <log:info message="用户邮箱变更: userId=${entity.sid}, 
                        oldEmail=${entity.orm_propOldValueByName('email')}, newEmail=${entity.email}" />
                </c:if>
                
                <c:if test="${entity.orm_propDirtyByName('status')}">
                    <log:info message="用户状态变更: userId=${entity.sid}, 
                        oldStatus=${entity.orm_propOldValueByName('status')}, newStatus=${entity.status}" />
                    
				   <c:script>
                     const notifyService = inject('userNotifyService');
                     notifyService.sendStatusChangeNotify(entity, 
                         entity.orm_propOldValueByName('status'), entity.status);
                   </c:script>
                </c:if>
            </source>
        </pre-update>
    </entity>
</interceptor>
```

## 与主流 ORM 框架对比

### 与 MyBatis 的对比

| 特性 | MyBatis | NopORM |
|------|---------|---------|
| 数据访问模式 | 命令式 | 声明式 |
| 缓存机制 | 二级缓存 | Session 级一级缓存 + 业务缓存 |
| 关联查询 | 手动配置 ResultMap | 自动对象导航 |
| 动态 SQL | XML 标签有限 | XPL 模板语言，支持自定义标签 |
| 扩展性 | 有限 | Delta 定制，无需修改源码 |
| OLAP 支持 | 需要手动编写复杂 SQL | 内置 DQL 多维查询 |

**MyBatis 示例：**
```java
// 需要显式调用 update
user.setName("newName");
userMapper.update(user);

// 复杂查询需要编写 XML
public interface UserMapper {
    List<User> findComplexUsers(@Param("filter") UserFilter filter);
}
```

**NopORM 示例：**
```java
// 声明式，修改自动跟踪
user.setName("newName");

// 复杂查询统一管理
List<User> users = sqlLibManager.invoke("findComplexUsers", params);
```

### 与 JPA/Hibernate 的对比

| 特性 | JPA/Hibernate | NopORM |
|------|---------------|---------|
| **实体定义** | 注解或XML配置 | 模型文件 + 代码生成 |
| **代码生成** | 运行时AOP增强 | 编译时一次性生成，性能更好 |
| **查询语言** | JPQL/HQL，功能有限 | EQL = SQL + AutoJoin，支持完整 SQL 语法 |
| **关联查询** | 受限的 JOIN 语法 | 任意表之间的 JOIN，支持复杂关联条件 |
| **动态 SQL** | 需要 Criteria API，代码冗长 | 统一的 sql-lib 管理，支持 XPL 模板语言 |
| **扩展机制** | 有限的拦截器机制 | Delta 定制，无需修改源码即可扩展功能 |
| **OLAP 支持** | 需要原生 SQL 或第三方库 | 内置 DQL 多维查询语言 |

**JPA/Hibernate 复杂查询：**
```java
// Criteria API 冗长难维护
CriteriaBuilder cb = em.getCriteriaBuilder();
CriteriaQuery<User> query = cb.createQuery(User.class);
Root<User> root = query.from(User.class);
List<Predicate> predicates = new ArrayList<>();

if (StringUtils.hasText(name)) {
    predicates.add(cb.like(root.get("name"), "%" + name + "%"));
}
// ... 更多条件
query.where(predicates.toArray(new Predicate[0]));
```

**NopORM 复杂查询：**
```xml
<!-- sql-lib 中声明式管理 -->
<eql name="findUsers">
    <source>
        select u from User u 
        where 1=1
        <c:if test="${!_.isEmpty(name)}">
            and u.name like ${'%' + name + '%'}
        </c:if>
        <app:CustomFilter/> <!-- 自定义标签复用逻辑 -->
    </source>
</eql>
```

### 与 Spring Data JPA 的对比

| 特性 | Spring Data JPA | NopORM |
|------|-----------------|---------|
| **开发范式** | Repository 抽象，方法名派生查询 | 统一的 DAO 模式 + 声明式查询 |
| **实体生成** | 手工编写或Lombok注解 | 模型驱动，自动代码生成 |
| **查询构建** | 方法名约定、@Query 注解、Specification | QueryBean、EQL、sql-lib 统一管理 |
| **动态查询** | Specification API 复杂冗长 | QueryBean 类型安全，模板动态生成 |
| **关联处理** | 懒加载 + N+1 问题常见 | 批量属性加载，自动优化关联查询 |
| **扩展机制** | 自定义 Repository 实现 | Delta 定制，无需代码修改 |

**Spring Data JPA Repository：**
```java
public interface UserRepository extends JpaRepository<User, Long> {
    // 方法名派生查询
    List<User> findByNameAndStatus(String name, Integer status);
    
    // 复杂查询需要 Specification
    Specification<User> hasRole(String roleName) {
        return (root, query, cb) -> 
            cb.isMember(roleName, root.get("roles"));
    }
}
```

**NopORM 统一 DAO：**
```java
// 统一的 DAO 接口
IEntityDao<User> userDao = daoProvider.daoFor(User.class);

// 简单查询
User example = userDao.newEntity();
example.setStatus(1);
List<User> users = userDao.findAllByExample(example);

// 复杂查询
QueryBean query = new QueryBean();
query.addFilter(and(
    eq(User.PROP_NAME_status, 1),
    like(User.PROP_NAME_department + ".name", "IT%")
)).limit(100);
List<User> users = userDao.findPageByQuery(query);
```

## 性能优化特性

### 1. 智能 Session 管理
```java
// 延迟获取数据库连接
@SingleSession
@Transactional
public void businessMethod() {
    // 先执行非数据库操作
    processBusinessLogic();
    
    // 需要时才获取连接
    User user = dao.getEntityById("1");
    // 自动在方法结束时 flush
}
```

### 2. 批量操作优化
```java
// 自动 JDBC Batch
for (int i = 0; i < 1000; i++) {
    User user = dao.newEntity();
    user.setName("user" + i);
    dao.saveEntity(user);
}
// 自动合并为 Batch INSERT
```

### 3. 关联属性批量加载
```java
List<User> users = dao.findAll();
// 一次性加载所有关联属性，避免 N+1 查询
dao.batchLoadProperties(users, Arrays.asList(
    "department", 
    "orders", 
    "orders.items",
    "department.company"
));
```

## 架构理念差异

### 传统 ORM 框架
- **命令式编程**：需要显式调用 save/update 方法
- **配置分散**：注解、XML、代码配置混杂
- **有限扩展**：修改需要重新编译部署
- **查询复杂**：复杂查询需要编写冗长代码

### NopORM 基于可逆计算理论
- **声明式编程**：修改自动跟踪，无需显式 save
- **模型驱动**：ORM 数据模型作为唯一事实源生成前后端完整代码
- **差量定制**：定制化开发无需修改原有代码
- **统一抽象**：SQL、EQL、DQL 统一管理

## 实际应用场景

### 1. 电商订单分析
```java
QueryBean query = new QueryBean();
query.setSourceName("Order");
query.fields(
    mainField("createTime", "day"),  // 按天分组
    mainField("customer.region"),
    subField("items", "amount").sum().alias("totalAmount"),
    subField("items", "id").count().alias("orderCount")
);
query.addFilter(gt("createTime", startDate));

List<Map<String, Object>> salesReport = ormTemplate.findListByQuery(query);
```

### 2. 权限管理系统
```java
// 查找有特定权限的用户
select u from NopAuthUser u 
where u.roles.permissions.resource.name = '/api/admin'
  and u.department.name = '技术部'

// 自动处理逻辑删除、数据权限过滤
```

### 3. 实体修改状态跟踪
```java
// 判断字段是否被修改
if (entity.orm_propDirty(propId)) {
    Object oldValue = entity.orm_propOldValue(propId);
    Object newValue = entity.orm_propValue(propId);
}

// 获取所有修改的字段
Map<String, Object> dirtyOldValues = entity.orm_dirtyOldValues();
Map<String, Object> dirtyNewValues = entity.orm_dirtyNewValues();
```

### 4. 启用租户

```xml
<entity name="app.BizOrder" tableName="biz_order" tenantProp="tenantId" useTenant="true" >
    <columns>
        <column name="sid" code="SID" stdDomain="id" primaryKey="true"/>
        <column name="tenantId" code="TENANT_ID" stdDomain="string" length="50" notNull="true"/>
	...
    </columns>
</entity>    
```

### 5. 分库分表支持
NopORM支持按照shardProp的值确定分库以及分表：

```xml
<entity name="app.ShardEntity" tableName="shard_table" shardProp="tenantId">
    <!-- 实体定义 -->
</entity>
```

```java
// 实现IShardSelector接口
public class MyShardSelector implements IShardSelector {
    public ShardSelection selectShard(String entityName, String shardProp, Object shardValue) {
        // 根据分片值选择数据源和表名
        return new ShardSelection("querySpace_" + shardValue, "shard_" + shardValue);
    }
}
### 迁移建议

**从 MyBatis 迁移：**
- 保持现有的 SQL 知识，在 sql-lib 中统一管理
- 逐步将命令式代码改为声明式数据访问
- 利用 EQL 简化复杂的关联查询

**从 JPA 迁移：**
- 将 JPQL 查询转换为更强大的 EQL
- 利用批量加载解决 N+1 问题
- 使用 Delta 定制替代复杂的拦截器配置

**从 Spring Data JPA 迁移：**
- 将 Repository 转换为统一的 DAO + sql-lib
- 用 QueryBean 替代复杂的 Specification
- 享受自动 GraphQL API 生成的好处

## 总结

NopORM 通过创新的设计解决了传统 ORM 在企业级应用中的痛点：

1. **模型驱动开发** - 在模型文件中定义数据结构，自动生成实体代码
2. **编译时优化** - 预生成所有代码，避免运行时AOP开销，性能更好
3. **声明式编程模型** - 修改即更新，减少样板代码
4. **统一查询抽象** - SQL、EQL、DQL 统一管理，适应不同场景
5. **无侵入扩展** - Delta 定制实现模型扩展，提升可维护性
6. **性能优化** - 自动 Batch、延迟加载、内存 Join 等
7. **企业级特性** - 多租户、逻辑删除、审计、分库分表等开箱即用

对于需要处理复杂业务逻辑和数据分析的企业应用，NopORM 提供了比传统 ORM 更强大和灵活的解决方案。其基于可逆计算理论的设计理念，使得系统能够在不断演进的过程中保持架构的清晰和可维护性，特别适合快速发展的互联网企业和需要高度定制化的企业应用场景。

## 了解更多

- [官方文档](https://gitee.com/canonical-entropy/nop-entropy)
- [示例项目](https://gitee.com/canonical-entropy/nop-entropy/nop-demo/nop-orm-demo)
- [视频教程](https://space.bilibili.com/3493261219990250)
- [社区交流](https://gitee.com/canonical-entropy/nop-entropy/issues)