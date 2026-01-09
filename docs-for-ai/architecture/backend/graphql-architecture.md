# Nop Platform GraphQL Engine Architecture

## 概述

Nop Platform的GraphQL引擎是基于模型驱动和可逆计算原理构建的下一代GraphQL服务框架。它不是简单地对现有GraphQL库的封装，而是从零开始重新设计实现，通过声明式的BizModel定义自动生成GraphQL Schema，实现了极简的API定义和强大的查询能力。

## 核心设计理念

### 1. Schema自动生成

无需手动编写GraphQL Schema文件，通过BizModel的注解自动推导和生成：

```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<NopAuthUser> {

    @BizQuery
    public User getUser(@Name("userId") String userId) {
        return dao().getEntityById(userId);
    }

    @BizMutation
    public User createUser(@Name("user") User user) {
        return save(user);
    }
}
```

自动生成的GraphQL Schema：

```graphql
type User {
    userId: ID!
    userName: String
    email: String
    status: Int
}

type UserBizModel {
    getUser(userId: String!): User
    createUser(user: UserInput!): User
}

type Query {
    User: UserBizModel
}

type Mutation {
    User: UserBizModel
}
```

### 2. 类型推导

基于Java类型和注解信息自动推导GraphQL类型：

| Java类型 | GraphQL类型 | 说明 |
|---------|-------------|------|
| String | String | 字符串 |
| Integer | Int | 整数 |
| Long | ID | 标识符 |
| BigDecimal | Float | 浮点数 |
| Boolean | Boolean | 布尔值 |
| Date | DateTime | 日期时间 |
| List\<T\> | [T!]! | 列表 |
| Map\<String, Object\> | JSON | JSON对象 |
| T | T | 自定义类型 |
| T | T! | 非空类型 |
| List\<T\> | [T] | 可空列表 |

### 3. 参数自动绑定

通过`@Name`注解绑定参数：

```java
@BizQuery
public User findUsers(
    @Name("keyword") String keyword,
    @Name("status") Integer status,
    @Name("pageNo") Integer pageNo,
    @Name("pageSize") Integer pageSize
) {
    QueryBean query = new QueryBean();
    if (StringHelper.isNotEmpty(keyword)) {
        query.setFilter(FilterBeans.contains("userName", keyword));
    }
    if (status != null) {
        query.setFilter(FilterBeans.eq("status", status));
    }
    return findPage(query, pageNo, pageSize);
}
```

GraphQL查询：

```graphql
query {
  User {
    findUsers(keyword: "test", status: 1, pageNo: 1, pageSize: 20) {
      pageNo
      pageSize
      totalCount
      items {
        userId
        userName
        email
      }
    }
  }
}
```

## 核心组件架构

### 1. Schema构建层 (Schema Builder Layer)

#### SchemaBuilder (Schema构建器)
- **定义**: `nop-graphql/nop-graphql-core`
- **职责**: 扫描BizModel，构建GraphQL Schema

```java
public class SchemaBuilder {
    public GraphQLSchema buildSchema(BeanContainer container) {
        // 1. 扫描所有@BizModel注解的类
        List<IBizModel> bizModels = scanBizModels(container);

        // 2. 构建TypeRegistry
        GraphQLTypeRegistry typeRegistry = buildTypeRegistry(bizModels);

        // 3. 构建Query Type
        GraphQLObjectType queryType = buildQueryType(bizModels);

        // 4. 构建Mutation Type
        GraphQLObjectType mutationType = buildMutationType(bizModels);

        // 5. 构建Schema
        return GraphQLSchema.newSchema()
            .query(queryType)
            .mutation(mutationType)
            .build();
    }
}
```

#### TypeRegistry (类型注册表)
- **职责**: 管理GraphQL类型定义

```java
public class GraphQLTypeRegistry {
    // 类型映射
    private Map<String, GraphQLType> typeMap = new HashMap<>();

    // 注册类型
    public void registerType(String name, GraphQLType type) {
        typeMap.put(name, type);
    }

    // 获取类型
    public GraphQLType getType(String name) {
        return typeMap.get(name);
    }

    // 检查类型是否存在
    public boolean hasType(String name) {
        return typeMap.containsKey(name);
    }
}
```

### 2. 类型转换层 (Type Conversion Layer)

#### TypeConverter (类型转换器)
- **职责**: Java类型与GraphQL类型之间的转换

```java
public interface IGraphQLTypeConverter {
    // Java类型转GraphQL类型
    GraphQLOutputType toGraphQLType(Class<?> javaType);

    // GraphQL类型转Java类型
    Class<?> toJavaType(GraphQLType graphQLType);

    // 输入值转换
    Object convertInput(Object value, Class<?> targetType);

    // 输出值转换
    Object convertOutput(Object value);
}
```

#### Scalar类型处理

内置的Scalar类型：

```java
// DateTime类型
public class DateTimeScalar extends GraphQLScalarType {
    public DateTimeScalar() {
        super("DateTime", "日期时间类型");
        setCoercing(new DateTimeCoercing());
    }
}

class DateTimeCoercing implements Coercing<Date, String> {
    @Override
    public String serialize(Date value) {
        return DateHelper.format(value, "yyyy-MM-dd HH:mm:ss");
    }

    @Override
    public Date parseValue(Object input) {
        return DateHelper.parse(input.toString());
    }
}
```

### 3. 查询执行层 (Query Execution Layer)

#### GraphQLExecutor (查询执行器)
- **定义**: `nop-graphql/nop-graphql-core`
- **职责**: 执行GraphQL查询

```java
public class GraphQLExecutor {
    public ExecutionResult execute(String query, String operationName, Map<String, Object> variables) {
        // 1. 解析查询
        GraphQLDocument document = parseDocument(query);

        // 2. 验证查询
        List<ValidationError> errors = validateDocument(document);
        if (!errors.isEmpty()) {
            return new ExecutionResultImpl(errors);
        }

        // 3. 构建执行上下文
        GraphQLExecutionContext context = buildExecutionContext(document, operationName, variables);

        // 4. 执行查询
        Object result = executeQuery(context);

        // 5. 处理错误
        if (context.getErrors().size() > 0) {
            return new ExecutionResultImpl(context.getErrors());
        }

        // 6. 转换结果
        return new ExecutionResultImpl(result, context.getErrors());
    }
}
```

#### FieldResolver (字段解析器)
- **职责**: 解析GraphQL字段值

```java
public interface IGraphQLFieldResolver {
    // 解析字段
    Object resolve(GraphQLFieldDefinition field, Object source, Map<String, Object> arguments);

    // 是否支持该字段
    boolean supports(GraphQLFieldDefinition field);
}
```

### 4. 数据获取层 (Data Fetching Layer)

#### DataFetcher (数据获取器)
- **职责**: 从数据源获取数据

```java
public interface IGraphQLDataFetcher {
    // 获取数据
    Object get(DataFetchingEnvironment environment);

    // 数据源描述
    String getSourceDescription();
}
```

#### BizModelDataFetcher (BizModel数据获取器)
- **职责**: 调用BizModel方法获取数据

```java
public class BizModelDataFetcher implements IGraphQLDataFetcher {
    private IBizModel bizModel;
    private Method method;

    @Override
    public Object get(DataFetchingEnvironment environment) {
        // 1. 获取参数
        Map<String, Object> arguments = environment.getArguments();

        // 2. 转换参数类型
        Object[] args = convertArguments(method, arguments);

        // 3. 调用BizModel方法
        return method.invoke(bizModel, args);
    }
}
```

### 5. 权限控制层 (Permission Layer)

#### PermissionInterceptor (权限拦截器)
- **职责**: 拦截查询，验证权限

```java
public class GraphQLPermissionInterceptor {
    @Override
    public Object intercept(GraphQLExecutionContext context) {
        // 1. 获取当前用户
        UserInfo user = getCurrentUser();

        // 2. 验证查询权限
        if (!checkPermission(user, context)) {
            throw new NopException(ERR_PERMISSION_DENIED);
        }

        // 3. 继续执行
        return context.proceed();
    }
}
```

### 6. 事务管理层 (Transaction Layer)

#### TransactionInterceptor (事务拦截器)
- **职责**: 管理GraphQL查询的事务边界

```java
public class GraphQLTransactionInterceptor {
    @Override
    public Object intercept(GraphQLExecutionContext context) {
        // 1. 判断是否需要事务
        if (isMutation(context)) {
            // 2. 开启事务
            return transactionTemplate.runInTransaction(() -> {
                return context.proceed();
            });
        } else {
            // 3. 查询操作不使用事务
            return context.proceed();
        }
    }
}
```

## 请求处理流程

### 1. 查询流程

```
1. 客户端发送GraphQL查询
   ↓
2. GraphQL接收请求
   ↓
3. SchemaBuilder验证Schema
   ↓
4. 解析查询（Parse）
   - 转换为AST（抽象语法树）
   ↓
5. 验证查询（Validate）
   - 验证语法
   - 验证类型
   - 验证字段存在性
   ↓
6. 构建执行上下文
   - 绑定参数
   - 创建GraphQL执行环境
   ↓
7. 拦截器链处理
   - 权限验证拦截器
   - 事务管理拦截器
   - 日志拦截器
   ↓
8. 执行查询（Execute）
   - 遍历AST
   - 调用DataFetcher
   - 解析字段值
   ↓
9. 数据转换
   - Java对象转GraphQL响应
   - 处理嵌套查询
   ↓
10. 返回响应
   - 格式化为JSON
   - 包含错误信息
```

### 2. 变更流程

```
1. 客户端发送GraphQL变更
   ↓
2. GraphQL接收请求
   ↓
3. 解析和验证
   ↓
4. 开启事务
   ↓
5. 执行BizModel方法
   - 参数验证
   - 业务逻辑处理
   - 调用DAO操作
   ↓
6. 事务提交/回滚
   ↓
7. 返回变更结果
   - 变更后的对象
   - 错误信息
```

## 查询优化

### 1. 批量查询 (Batch Query)

使用`@DataLoader`实现批量查询，避免N+1问题：

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<NopOrder> {

    @BizQuery
    public List<Order> getOrders(@Name("userId") String userId) {
        QueryBean query = new QueryBean();
        query.setFilter(FilterBeans.eq("userId", userId));
        return findAllByQuery(query);
    }
}

// 使用DataLoader批量加载用户信息
@Component
public class UserDataLoader implements IGraphQLDataLoader {
    @Override
    public List<User> load(List<String> userIds) {
        return userDao.batchGetEntitiesByIds(userIds);
    }
}
```

GraphQL查询：

```graphql
query {
  Order {
    getOrders(userId: "1") {
      orderId
      orderNo
      user {
        userId
        userName  # 使用DataLoader批量加载
      }
    }
  }
}
```

### 2. 字段选择 (Field Selection)

只查询需要的字段：

```graphql
query {
  User {
    getUser(userId: "1") {
      userId    # 只查询需要的字段
      userName
      # email   # 不查询email
    }
  }
}
```

### 3. 分页查询 (Pagination)

使用内置的分页支持：

```java
@BizQuery
public PageBean<User> findUsers(
    @Name("keyword") String keyword,
    @Name("pageNo") Integer pageNo,
    @Name("pageSize") Integer pageSize
) {
    QueryBean query = new QueryBean();
    if (StringHelper.isNotEmpty(keyword)) {
        query.setFilter(FilterBeans.contains("userName", keyword));
    }
    return findPage(query, pageNo, pageSize);
}
```

GraphQL查询：

```graphql
query {
  User {
    findUsers(pageNo: 1, pageSize: 20) {
      pageNo
      pageSize
      totalCount
      items {
        userId
        userName
      }
    }
  }
}
```

### 4. 查询缓存 (Query Cache)

对相同查询结果进行缓存：

```java
@BizQuery
@Cacheable(cacheName = "userQueryCache", timeout = 300)
public User getUser(@Name("userId") String userId) {
    return dao().getEntityById(userId);
}
```

## 订阅 (Subscription)

### 1. 订阅定义

```java
@BizModel("Message")
public class MessageBizModel extends CrudBizModel<NopMessage> {

    @BizSubscription
    public Publisher<Message> onNewMessage(@Name("userId") String userId) {
        return messageStream.filter(msg ->
            msg.getReceiverId().equals(userId)
        );
    }
}
```

GraphQL订阅：

```graphql
subscription {
  Message {
    onNewMessage(userId: "1") {
      messageId
      senderId
      content
      createTime
    }
  }
}
```

### 2. WebSocket连接

GraphQL订阅通过WebSocket建立持久连接：

```java
@Component
public class GraphQLWebSocketHandler {
    @OnMessage
    public void handleMessage(WebSocketSession session, String message) {
        // 处理GraphQL订阅消息
    }

    @OnClose
    public void handleClose(WebSocketSession session) {
        // 清理订阅
    }
}
```

## 错误处理

### 1. 错误类型

| 错误类型 | 说明 | HTTP状态码 |
|---------|------|-----------|
| 语法错误 | GraphQL查询语法错误 | 400 |
| 验证错误 | Schema验证失败 | 400 |
| 权限错误 | 权限不足 | 403 |
| 未找到错误 | 资源不存在 | 404 |
| 业务错误 | 业务逻辑错误 | 200（部分成功） |
| 系统错误 | 系统内部错误 | 500 |

### 2. 错误响应格式

```graphql
{
  "data": {
    "User": {
      "getUser": null
    }
  },
  "errors": [
    {
      "message": "User not found",
      "path": ["User", "getUser"],
      "extensions": {
        "code": "ERR_USER_NOT_FOUND",
        "args": {
          "userId": "1"
        }
      }
    }
  ]
}
```

### 3. 异常转换

```java
public class GraphQLExceptionHandler {
    public GraphQLError toGraphQLError(Throwable ex) {
        if (ex instanceof NopException) {
            NopException nopEx = (NopException) ex;
            return GraphQLError.newBuilder()
                .message(nopEx.getMessage())
                .extensions("code", nopEx.getErrorCode())
                .extensions("args", nopEx.getParams())
                .build();
        } else {
            return GraphQLError.newBuilder()
                .message("Internal server error")
                .build();
        }
    }
}
```

## 最佳实践

### 1. 使用命名查询

```graphql
query GetUser($userId: String!) {
  User {
    getUser(userId: $userId) {
      userId
      userName
      email
    }
  }
}
```

### 2. 分离Query和Mutation

```java
@BizModel("UserQuery")
public class UserQueryBizModel extends CrudBizModel<NopAuthUser> {

    @BizQuery
    public User getUser(String userId) {
        return dao().getEntityById(userId);
    }
}

@BizModel("UserMutation")
public class UserMutationBizModel extends CrudBizModel<NopAuthUser> {

    @BizMutation
    @Transactional
    public User createUser(User user) {
        return save(user);
    }
}
```

### 3. 使用Fragment复用字段

```graphql
fragment UserFields on User {
  userId
  userName
  email
}

query {
  User {
    getUser(userId: "1") {
      ...UserFields
    }
  }
}
```

### 4. 合理使用参数

```java
@BizQuery
public List<User> findUsers(@Name("filter") UserFilter filter) {
    QueryBean query = new QueryBean();
    if (filter.getKeyword() != null) {
        query.setFilter(FilterBeans.contains("userName", filter.getKeyword()));
    }
    return findAllByQuery(query);
}
```

### 5. 避免过度查询

```graphql
# ✅ 推荐：只查询需要的字段
query {
  User {
    getUser(userId: "1") {
      userId
      userName
    }
  }
}

# ❌ 不推荐：查询所有字段
query {
  User {
    getUser(userId: "1") {
      userId
      userName
      email
      phone
      address
      # ...
    }
  }
}
```

## 相关文档

- [GraphQL服务开发指南](../getting-started/api/graphql-guide.md)
- [API架构文档](./api-architecture.md)
- [服务层开发指南](../getting-started/service/service-layer-development.md)
- [事务管理指南](../getting-started/core/transaction-guide.md)
- [异常处理指南](../getting-started/core/exception-guide.md)

## 总结

Nop Platform的GraphQL引擎是一个高性能、易用的GraphQL服务框架，具有以下特点：

1. **Schema自动生成**: 基于BizModel注解自动生成Schema
2. **类型推导**: Java类型自动推导为GraphQL类型
3. **声明式定义**: 通过注解定义查询、变更、订阅
4. **性能优化**: 支持批量查询、字段选择、查询缓存
5. **扩展性**: 提供丰富的扩展点（拦截器、DataFetcher等）

通过合理使用GraphQL特性，可以构建高效、灵活的API服务。

---

**文档版本**: 1.0
**最后更新**: 2025-01-09
**作者**: AI Assistant (Sisyphus)
