# Nop Platform Code Style Standards

## 概述

本文档定义了Nop Platform的代码风格标准，确保代码的一致性、可读性和可维护性。遵循这些标准将使代码更易于理解和维护。

## 命名规范

### 1. 类名和接口名

**规则**: PascalCase（大驼峰）

```java
// ✅ 正确
public class UserService {
}

public interface IUserDao {
}

public abstract class AbstractUser {
}

// ❌ 错误
public class userService {
}

public interface userdao {
}
```

**特殊情况**:
- 接口以`I`开头
- 抽象类以`Abstract`开头
- 工具类以`Helper`、`Tool`结尾
- 异常类以`Exception`结尾

### 2. 方法名

**规则**: camelCase（小驼峰）

```java
// ✅ 正确
public User getUserById(String id) {
}

public void createUser(User user) {
}

public boolean isUserActive(String id) {
}

// ❌ 错误
public User GetUserById(String id) {
}

public void CreateUser(User user) {
}

public boolean UserActive(String id) {
}
```

**布尔方法命名**:
- `is`前缀：`isActive()`, `isValid()`
- `has`前缀：`hasPermission()`, `hasRole()`
- `can`前缀：`canDelete()`, `canUpdate()`

### 3. 变量名

**规则**: camelCase（小驼峰）

```java
// ✅ 正确
public class User {
    private String userName;
    private Integer userStatus;
    private Date createTime;
}

// ❌ 错误
public class User {
    private String UserName;
    private Integer user_status;
    private Date CreateTime;
}
```

### 4. 常量名

**规则**: UPPER_SNAKE_CASE

```java
// ✅ 正确
public class UserConstants {
    public static final String DEFAULT_ROLE = "USER";
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final long SESSION_TIMEOUT = 1800;
}

// ❌ 错误
public class UserConstants {
    public static final String defaultRole = "USER";
    public static final int maxLoginAttempts = 5;
}
```

### 5. 参数名

**规则**: camelCase（小驼峰）

```java
// ✅ 正确
public User createUser(String userName, String email) {
}

// ❌ 错误
public User CreateUser(String UserName, String Email) {
}
```

### 6. 包名

**规则**: 全小写，使用点分隔

```java
// ✅ 正确
package io.nop.auth.service;
package io.nop.sys.dao;
package io.nop.biz.crud;

// ❌ 错误
package io.nop.Auth.Service;
package io.nop.Sys.Dao;
```

## 代码格式

### 1. 缩进

**规则**: 使用4个空格，不使用Tab

```java
// ✅ 正确
public class User {
    public void method() {
        if (condition) {
            doSomething();
        }
    }
}

// ❌ 错误
public class User {
	public void method() {  // 使用Tab
		if (condition) {
			doSomething();
		}
	}
}
```

### 2. 行长度

**规则**: 单行不超过120字符

```java
// ❌ 错误
User user = userDao.findUserByUserNameAndPasswordAndStatusAndCreateTimeGreaterThan(userName, password, status, createTime);

// ✅ 正确
User user = userDao.findUser(
    userName, password, status, createTime
);

// 或者
QueryBean query = new QueryBean();
query.setFilter(FilterBeans.eq("userName", userName));
User user = dao().findFirstByExample(query);
```

### 3. 大括号

**规则**: 左大括号不换行，右大括号另起一行

```java
// ✅ 正确
public void method() {
    if (condition) {
        doSomething();
    }
}

// ❌ 错误
public void method()
{
    if (condition)
    {
        doSomething();
    }
}
```

### 4. 空格使用

**规则**: 运算符前后加空格

```java
// ✅ 正确
int result = a + b;
if (a > b) {
    doSomething();
}

// ❌ 错误
int result = a+b;
if (a>b) {
    doSomething();
}
```

**例外**: 点操作符前后不加空格

```java
// ✅ 正确
user.getName();
dao().saveEntity(user);

// ❌ 错误
user . getName();
dao () . saveEntity(user);
```

### 5. 空行

**规则**: 方法之间、类之间加一个空行

```java
// ✅ 正确
public class User {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

// ❌ 错误
public class User {

    private String name;
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```

## 导入顺序

### 1. 导入分组

按以下顺序分组：

1. Java标准库（`java.*`）
2. Jakarta标准库（`jakarta.*`）
3. 第三方库（按字母顺序）
4. Nop Platform库（`io.nop.*`）

```java
// ✅ 正确
import java.util.Date;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.Test;

import io.nop.api.core.annotations.core.Description;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.dao.IOrmEntityDao;

// ❌ 错误
import io.nop.api.core.annotations.core.Description;
import java.util.Date;
import org.junit.Test;
import java.util.List;
```

### 2. 避免通配符导入

```java
// ❌ 错误
import java.util.*;
import io.nop.api.core.*;

// ✅ 正确
import java.util.Date;
import java.util.List;
import io.nop.api.core.annotations.core.Description;
```

## 注释规范

### 1. 类注释

```java
/**
 * 用户服务
 *
 * 提供用户相关的业务逻辑处理，包括用户创建、查询、更新、删除等功能
 *
 * @author Your Name
 * @version 1.0
 */
@BizModel("User")
public class UserBizModel extends CrudBizModel<NopAuthUser> {
    // ...
}
```

### 2. 方法注释

```java
/**
 * 根据用户ID获取用户信息
 *
 * @param userId 用户ID，不能为空
 * @return 用户信息，如果不存在则返回null
 * @throws NopException 当用户ID为空时抛出异常
 */
@BizQuery
public User getUser(@Name("userId") String userId) {
    return dao().getEntityById(userId);
}
```

### 3. 字段注释

```java
public class User {
    /** 用户ID，主键 */
    private String userId;

    /** 用户名，不能为空 */
    private String userName;

    /** 用户状态：1-正常，0-禁用 */
    private Integer status;
}
```

### 4. 行内注释

```java
// ✅ 正确
// 检查用户名是否为空
if (StringHelper.isEmpty(userName)) {
    throw new NopException(ERR_NAME_REQUIRED);
}

// ❌ 错误
if (StringHelper.isEmpty(userName)) { // 检查用户名是否为空
    throw new NopException(ERR_NAME_REQUIRED);
}
```

## 代码结构

### 1. 类结构顺序

```java
public class User {

    // 1. 常量
    public static final String DEFAULT_ROLE = "USER";

    // 2. 静态变量
    private static final Logger LOG = LoggerFactory.getLogger(User.class);

    // 3. 实例变量
    private String userId;
    private String userName;

    // 4. 构造方法
    public User() {
    }

    public User(String userName) {
        this.userName = userName;
    }

    // 5. 公共方法
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    // 6. 私有方法
    private void validateUserName(String userName) {
        // ...
    }

    // 7. 内部类
    private static class UserBuilder {
        // ...
    }
}
```

### 2. 接口定义

```java
/**
 * 用户数据访问接口
 *
 * @author Your Name
 */
public interface IUserDao extends IEntityDao<NopAuthUser> {

    /**
     * 根据用户名查询用户
     *
     * @param userName 用户名
     * @return 用户信息
     */
    User findByUserName(String userName);
}
```

### 3. 枚举定义

```java
/**
 * 用户状态枚举
 */
public enum UserStatus {
    /**
     * 正常
     */
    ACTIVE(1, "正常"),

    /**
     * 禁用
     */
    INACTIVE(0, "禁用");

    private final Integer code;
    private final String description;

    UserStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
```

## 异常处理

### 1. 使用NopException

```java
// ✅ 正确
if (user == null) {
    throw new NopException(ERR_USER_NOT_FOUND)
        .param("userId", userId);
}

// ❌ 错误
if (user == null) {
    throw new RuntimeException("User not found: " + userId);
}
```

### 2. 不捕获通用异常

```java
// ✅ 正确
try {
    doSomething();
} catch (IOException e) {
    log.error("Failed to do something", e);
    throw new NopException(ERR_OPERATION_FAILED, e);
}

// ❌ 错误
try {
    doSomething();
} catch (Exception e) {
    log.error("Failed", e);
}
```

### 3. 不空的catch块

```java
// ✅ 正确
try {
    doSomething();
} catch (IOException e) {
    log.error("Failed to do something", e);
    throw new NopException(ERR_OPERATION_FAILED, e);
}

// ❌ 错误
try {
    doSomething();
} catch (IOException e) {
    // empty catch
}
```

## 业务逻辑规范

### 1. 服务层设计

```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<NopAuthUser> {

    @Inject
    private IUserDao userDao;

    @BizQuery
    public User getUser(String userId) {
        return userDao.getEntityById(userId);
    }

    @BizMutation
    @Transactional
    public User createUser(User user) {
        // 业务规则验证
        validateUser(user);

        // 调用DAO
        return userDao.saveEntity(user);
    }
}
```

### 2. 数据访问层设计

```java
public class UserDao implements IUserDao {

    @Inject
    private IOrmTemplate ormTemplate;

    @Override
    public User saveEntity(User user) {
        // 数据访问逻辑
        return ormTemplate.save(user);
    }
}
```

### 3. 使用内置方法

```java
// ✅ 正确：使用CrudBizModel内置方法
@BizQuery
public PageBean<User> findUsers(QueryBean query, int pageNo, int pageSize) {
    return findPage(query, pageNo, pageSize);
}

// ❌ 错误：重复实现已有功能
@BizQuery
public PageBean<User> findUsers(QueryBean query, int pageNo, int pageSize) {
    int offset = (pageNo - 1) * pageSize;
    int count = dao().countByQuery(query);
    List<User> list = dao().findAllByQuery(query);
    return new PageBean<>(list, count, pageNo, pageSize);
}
```

## 最佳实践

### 1. 单一职责

每个类和方法只做一件事：

```java
// ✅ 正确
public class UserValidator {
    public void validate(User user) {
        validateName(user.getName());
        validateEmail(user.getEmail());
    }

    private void validateName(String name) {
        // ...
    }

    private void validateEmail(String email) {
        // ...
    }
}

// ❌ 错误
public class UserService {
    public void createUser(User user) {
        // 验证
        validateName(user.getName());
        validateEmail(user.getEmail());

        // 保存
        dao().saveEntity(user);

        // 发送邮件
        sendEmail(user);

        // 记录日志
        logCreate(user);
    }
}
```

### 2. DRY原则（Don't Repeat Yourself）

避免重复代码：

```java
// ✅ 正确
public class UserService {
    public User getUserById(String id) {
        return getUser(id, "User not found: " + id);
    }

    public User getUserByEmail(String email) {
        return getUser(email, "User not found: " + email);
    }

    private User getUser(String key, String errorMessage) {
        User user = dao().findUser(key);
        if (user == null) {
            throw new NopException(ERR_USER_NOT_FOUND)
                .description(errorMessage);
        }
        return user;
    }
}

// ❌ 错误
public class UserService {
    public User getUserById(String id) {
        User user = dao().findUser(id);
        if (user == null) {
            throw new NopException(ERR_USER_NOT_FOUND)
                .description("User not found: " + id);
        }
        return user;
    }

    public User getUserByEmail(String email) {
        User user = dao().findUser(email);
        if (user == null) {
            throw new NopException(ERR_USER_NOT_FOUND)
                .description("User not found: " + email);
        }
        return user;
    }
}
```

### 3. 避免魔法数字

```java
// ✅ 正确
public class UserConstants {
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final long SESSION_TIMEOUT = 1800;
}

public class UserService {
    public boolean canLogin(User user) {
        return user.getLoginAttempts() < UserConstants.MAX_LOGIN_ATTEMPTS;
    }
}

// ❌ 错误
public class UserService {
    public boolean canLogin(User user) {
        return user.getLoginAttempts() < 5; // 魔法数字
    }
}
```

### 4. 字符串比较

```java
// ✅ 正确
if (userName.equals("admin")) {
    // ...
}

// ❌ 错误
if (userName == "admin") {
    // ...
}
```

## 代码检查

### 1. 运行Checkstyle

```bash
# 运行代码风格检查
mvn checkstyle:check

# 生成报告
mvn checkstyle:checkstyle
```

### 2. IDE配置

**IntelliJ IDEA**:
1. 安装Checkstyle插件
2. 配置使用项目的`checkstyle.xml`文件
3. 启用实时检查

**VS Code**:
1. 安装Checkstyle扩展
2. 配置使用项目的`checkstyle.xml`文件
3. 启用实时检查

### 3. 常见问题修复

| 问题 | 正确写法 | 错误写法 |
|------|----------|----------|
| 行长度超过120 | 拆分多行或提取方法 | 一行写完 |
| 使用Tab | 使用4个空格 | 使用Tab字符 |
| 缺少Javadoc | 添加方法注释 | 无注释 |
| 缺少空格 | `a + b` | `a+b` |
| 字符串比较 | `str.equals("abc")` | `str == "abc"` |
| 通用异常捕获 | `catch (IOException e)` | `catch (Exception e)` |
| 空catch块 | 记录日志或转换异常 | `{}` |

## 相关文档

- [Checkstyle配置指南](../../CHECKSTYLE.md)
- [开发规范](../../AGENTS.md)
- [异常处理指南](../getting-started/core/exception-guide.md)
- [服务层开发指南](../getting-started/service/service-layer-development.md)

## 总结

遵循Nop Platform代码风格标准可以：

1. **提高代码可读性**: 一致的命名和格式
2. **减少错误**: 避免常见的编码错误
3. **便于维护**: 清晰的代码结构和注释
4. **团队协作**: 统一的代码风格便于协作

建议在IDE中配置代码风格检查，实时提示不符合标准的代码。

---

**文档版本**: 1.0
**最后更新**: 2025-01-09
**作者**: AI Assistant (Sisyphus)
