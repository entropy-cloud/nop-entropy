# Nop平台AI编程开发指南

## 1. 快速开始

```bash
# 创建项目
nop-cli create myapp

# 生成代码
cd myapp
mvn clean install

# 运行
java -jar myapp-app/target/myapp-app-*.jar
```

## 2. 核心概念

### 2.1 可逆计算
**公式**: `App = Delta x-extends Generator<DSL>`

- **模型驱动**: 基于XML模型生成代码
- **Delta定制**: 不修改源码的定制机制
- **XLang**: 元编程和面向语言编程

### 2.2 标准架构
```
数据层: IEntityDao, QueryBean
服务层: BizModel, CrudBizModel
API层: GraphQL/REST 自动生成
视图层: XView, AMIS
```

## 3. 关键API

### 3.1 数据访问
```java
// CRUD操作
dao().getEntityById(id);
dao().findAllByExample(example);
dao().findPageByQuery(query);
```

### 3.2 服务层
```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<User> {

    @BizQuery
    public User findUser(String userId) {
        return dao().getEntityById(userId);
    }

    @BizMutation
    @Transactional
    public User createUser(User user) {
        return save(user);
    }
}
```

### 3.3 异常处理
```java
throw new NopException(MyErrors.ERR_USER_NOT_FOUND)
    .param("userId", userId);
```

### 3.4 常用Helper
```java
StringHelper.isEmpty(str);
DateHelper.formatDate(new Date());
BeanTool.getProperty(bean, "name");
```

## 4. 快速链接

### 4.1 开发指南
- [项目结构与代码生成](../../development/module-structure-guide.md) - ⭐
- [数据层开发](./dao/data-layer-development.md)
- [服务层开发](./service/service-layer-development.md)

### 4.2 核心文档
- [完整开发规范](./nop-ai-development.md)
- [IEntityDao使用](./dao/entitydao-usage.md)
- [事务管理](./core/transaction-guide.md)
- [异常处理](./core/exception-guide.md)

### 4.3 最佳实践
- 使用 `CrudBizModel` 内置方法
- 使用 `QueryBean` 和 `FilterBeans` 构建查询
- 使用 `@Transactional` 注解管理事务
- 使用 `NopException` 统一异常处理

---

**详细文档**: [完整开发规范](./nop-ai-development.md)
