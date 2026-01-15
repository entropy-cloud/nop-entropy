# NopAuth 项目示例

## 概述

本文档提供NopAuth项目的完整实现示例，展示如何使用Nop Platform构建用户权限管理系统，包括用户管理、权限控制、角色管理等功能。

## 项目结构

**标准项目结构**: 详见[项目结构与代码生成指南](../development/module-structure-guide.md)

**关键模块**:
- `nop-auth-codegen` - 代码生成模块
- `nop-auth-dao` - 数据访问层（Entity、DAO）
- `nop-auth-service` - 服务层（BizModel）
- `nop-auth-web` - 视图层（XView、页面）
- `nop-auth-meta` - 元数据模块
- `nop-auth-app` - 应用模块

**源模型文件**: `model/nop-auth.orm.xml`

## 数据库模型设计

### 1. 用户表 (nop_auth_user)

```xml
<entity name="NopAuthUser" table="nop_auth_user">
  <field name="userId" type="string" primary="true" length="32" />
  <field name="userName" type="string" required="true" length="100" unique="true" />
  <field name="password" type="string" required="true" length="200" />
  <field name="email" type="string" length="200" />
  <field name="phone" type="string" length="20" />
  <field name="status" type="int" required="true" defaultValue="1" />
  <field name="deptId" type="string" length="32" />
  <field name="createTime" type="datetime" />
  <field name="updateTime" type="datetime" />

  <relation name="roles" type="many-to-many" target="NopAuthRole" junction="NopAuthUserRole" />
  <relation name="dept" type="many-to-one" target="NopAuthDept" />
</entity>
```

### 2. 角色表 (nop_auth_role)

```xml
<entity name="NopAuthRole" table="nop_auth_role">
  <field name="roleId" type="string" primary="true" length="32" />
  <field name="roleName" type="string" required="true" length="100" />
  <field name="roleCode" type="string" required="true" length="50" unique="true" />
  <field name="description" type="string" length="500" />
  <field name="status" type="int" required="true" defaultValue="1" />
  <field name="createTime" type="datetime" />
  <field name="updateTime" type="datetime" />

  <relation name="permissions" type="many-to-many" target="NopAuthPermission" junction="NopAuthRolePermission" />
</entity>
```

### 3. 权限表 (nop_auth_permission)

```xml
<entity name="NopAuthPermission" table="nop_auth_permission">
  <field name="permissionId" type="string" primary="true" length="32" />
  <field name="permissionName" type="string" required="true" length="100" />
  <field name="permissionCode" type="string" required="true" length="100" unique="true" />
  <field name="permissionType" type="string" length="20" />
  <field name="resource" type="string" length="200" />
  <field name="action" type="string" length="50" />
  <field name="status" type="int" required="true" defaultValue="1" />
  <field name="createTime" type="datetime" />
</entity>
```

### 4. 用户角色关系表 (nop_auth_user_role)

```xml
<entity name="NopAuthUserRole" table="nop_auth_user_role">
  <field name="userId" type="string" length="32" />
  <field name="roleId" type="string" length="32" />

  <relation name="user" type="many-to-one" target="NopAuthUser" />
  <relation name="role" type="many-to-one" target="NopAuthRole" />
</entity>
```

## 用户管理实现

### 1. 用户实体类

```java
package io.nop.auth.domain;

import io.nop.api.core.annotations.ioc.Bean;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.support.OrmEntity;

@Entity(table = "nop_auth_user")
@Cacheable(cacheName = "nop_auth_user")
public class NopAuthUser extends OrmEntity {

    public static final String PROP_NAME_userId = "userId";
    public static final String PROP_NAME_userName = "userName";
    public static final String PROP_NAME_password = "password";
    public static final String PROP_NAME_email = "email";
    public static final String PROP_NAME_phone = "phone";
    public static final String PROP_NAME_status = "status";
    public static final String PROP_NAME_deptId = "deptId";
    public static final String PROP_NAME_createTime = "createTime";
    public static final String PROP_NAME_updateTime = "updateTime";

    private String userId;
    private String userName;
    private String password;
    private String email;
    private String phone;
    private Integer status;
    private String deptId;
    private Date createTime;
    private Date updateTime;

    // Getter and Setter
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getDeptId() { return deptId; }
    public void setDeptId(String deptId) { this.deptId = deptId; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
```

### 2. 用户业务模型

```java
package io.nop.auth.service;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.dao.IEntityDao;
import io.nop.auth.domain.NopAuthUser;
import io.nop.auth.domain.NopAuthRole;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.Errors;
import io.nop.orm.api.IOrmEntityDao;
import io.nop.orm.support.OrmEntity;
import io.nop.service.crud.CrudBizModel;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;

import static io.nop.auth.AuthErrors.*;

@BizModel("NopAuthUser")
public class NopAuthUserBizModel extends CrudBizModel<NopAuthUser> {

    public NopAuthUserBizModel() {
        setEntityName(NopAuthUser.class.getName());
    }

    @Inject
    private IOrmEntityDao<NopAuthRole> roleDao;

    /**
     * 创建用户
     */
    @BizMutation
    @Transactional
    public NopAuthUser createUser(@Name("user") NopAuthUser user) {
        // 验证用户名唯一性
        if (isUserNameExists(user.getUserName())) {
            throw new NopException(ERR_AUTH_USER_NAME_EXISTS)
                .param(ARG_USER_NAME, user.getUserName());
        }

        // 设置默认值
        if (user.getUserId() == null) {
            user.setUserId(StringHelper.generateUUID());
        }
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());

        // 加密密码
        user.setPassword(encryptPassword(user.getPassword()));

        return save(user);
    }

    /**
     * 更新用户
     */
    @BizMutation
    @Transactional
    public NopAuthUser updateUser(@Name("userId") String userId,
                                   @Name("user") NopAuthUser user) {
        NopAuthUser existing = dao().getEntityById(userId);
        if (existing == null) {
            throw new NopException(ERR_AUTH_USER_NOT_FOUND)
                .param(ARG_USER_ID, userId);
        }

        // 更新字段
        if (user.getUserName() != null) {
            existing.setUserName(user.getUserName());
        }
        if (user.getEmail() != null) {
            existing.setEmail(user.getEmail());
        }
        if (user.getPhone() != null) {
            existing.setPhone(user.getPhone());
        }
        if (user.getStatus() != null) {
            existing.setStatus(user.getStatus());
        }
        if (user.getDeptId() != null) {
            existing.setDeptId(user.getDeptId());
        }
        if (user.getPassword() != null) {
            existing.setPassword(encryptPassword(user.getPassword()));
        }
        existing.setUpdateTime(new Date());

        return dao().updateEntity(existing);
    }

    /**
     * 删除用户
     */
    @BizMutation
    @Transactional
    public void deleteUser(@Name("userId") String userId) {
        NopAuthUser user = dao().getEntityById(userId);
        if (user == null) {
            throw new NopException(ERR_AUTH_USER_NOT_FOUND)
                .param(ARG_USER_ID, userId);
        }

        // 删除用户角色关系
        dao().deleteByFilter(
            NopAuthUserRole.PROP_NAME_userId,
            FilterBean.eq(userId)
        );

        // 删除用户
        dao().deleteEntity(user);
    }

    /**
     * 获取用户角色
     */
    @BizQuery
    public List<NopAuthRole> getUserRoles(@Name("userId") String userId) {
        NopAuthUser user = dao().getEntityById(userId);
        if (user == null) {
            throw new NopException(ERR_AUTH_USER_NOT_FOUND)
                .param(ARG_USER_ID, userId);
        }

        return user.getRoles();
    }

    /**
     * 分配角色
     */
    @BizMutation
    @Transactional
    public void assignRole(@Name("userId") String userId,
                          @Name("roleId") String roleId) {
        // 验证用户存在
        NopAuthUser user = dao().getEntityById(userId);
        if (user == null) {
            throw new NopException(ERR_AUTH_USER_NOT_FOUND)
                .param(ARG_USER_ID, userId);
        }

        // 验证角色存在
        NopAuthRole role = roleDao.getEntityById(roleId);
        if (role == null) {
            throw new NopException(ERR_AUTH_ROLE_NOT_FOUND)
                .param(ARG_ROLE_ID, roleId);
        }

        // 检查是否已分配
        if (isRoleAssigned(userId, roleId)) {
            return;
        }

        // 创建用户角色关系
        NopAuthUserRole userRole = new NopAuthUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        dao().saveEntity(userRole);
    }

    /**
     * 移除角色
     */
    @BizMutation
    @Transactional
    public void removeRole(@Name("userId") String userId,
                          @Name("roleId") String roleId) {
        dao().deleteByFilter(
            NopAuthUserRole.PROP_NAME_userId,
            FilterBean.and(
                FilterBean.eq(NopAuthUserRole.PROP_NAME_userId, userId),
                FilterBean.eq(NopAuthUserRole.PROP_NAME_roleId, roleId)
            )
        );
    }

    /**
     * 检查用户名是否存在
     */
    private boolean isUserNameExists(String userName) {
        return dao().exists(
            FilterBean.eq(NopAuthUser.PROP_NAME_userName, userName)
        );
    }

    /**
     * 检查角色是否已分配
     */
    private boolean isRoleAssigned(String userId, String roleId) {
        return dao().exists(
            FilterBean.and(
                FilterBean.eq(NopAuthUserRole.PROP_NAME_userId, userId),
                FilterBean.eq(NopAuthUserRole.PROP_NAME_roleId, roleId)
            )
        );
    }

    /**
     * 加密密码
     */
    private String encryptPassword(String password) {
        // 实际项目中应该使用BCrypt或其他安全的加密算法
        return "encrypted:" + password;
    }
}
```

### 3. 用户GraphQL查询

```graphql
type Query {
  # 获取用户
  getUser(userId: String): NopAuthUser

  # 查询用户列表
  findUserList(query: QueryBean): [NopAuthUser]

  # 分页查询用户
  findUserPage(query: QueryBean, pageNo: Int, pageSize: Int): PageBean

  # 获取用户角色
  getUserRoles(userId: String): [NopAuthRole]
}

type Mutation {
  # 创建用户
  createUser(user: NopAuthUserInput!): NopAuthUser

  # 更新用户
  updateUser(userId: String!, user: NopAuthUserInput!): NopAuthUser

  # 删除用户
  deleteUser(userId: String!): Boolean

  # 分配角色
  assignRole(userId: String!, roleId: String!): Boolean

  # 移除角色
  removeRole(userId: String!, roleId: String!): Boolean
}
```

## 角色管理实现

### 1. 角色业务模型

```java
package io.nop.auth.service;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.dao.IEntityDao;
import io.nop.auth.domain.NopAuthRole;
import io.nop.commons.util.StringHelper;
import io.nop.orm.model.IOrmEntityModel;
import io.nop.service.crud.CrudBizModel;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;

import static io.nop.auth.AuthErrors.*;

@BizModel("NopAuthRole")
public class NopAuthRoleBizModel extends CrudBizModel<NopAuthRole> {

    public NopAuthRoleBizModel() {
        setEntityName(NopAuthRole.class.getName());
    }

    @Inject
    private IOrmEntityDao<NopAuthPermission> permissionDao;

    /**
     * 创建角色
     */
    @BizMutation
    @Transactional
    public NopAuthRole createRole(@Name("role") NopAuthRole role) {
        // 验证角色编码唯一性
        if (isRoleCodeExists(role.getRoleCode())) {
            throw new NopException(ERR_AUTH_ROLE_CODE_EXISTS)
                .param(ARG_ROLE_CODE, role.getRoleCode());
        }

        // 设置默认值
        if (role.getRoleId() == null) {
            role.setRoleId(StringHelper.generateUUID());
        }
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        role.setCreateTime(new Date());
        role.setUpdateTime(new Date());

        return save(role);
    }

    /**
     * 更新角色
     */
    @BizMutation
    @Transactional
    public NopAuthRole updateRole(@Name("roleId") String roleId,
                                   @Name("role") NopAuthRole role) {
        NopAuthRole existing = dao().getEntityById(roleId);
        if (existing == null) {
            throw new NopException(ERR_AUTH_ROLE_NOT_FOUND)
                .param(ARG_ROLE_ID, roleId);
        }

        // 更新字段
        if (role.getRoleName() != null) {
            existing.setRoleName(role.getRoleName());
        }
        if (role.getRoleCode() != null) {
            existing.setRoleCode(role.getRoleCode());
        }
        if (role.getDescription() != null) {
            existing.setDescription(role.getDescription());
        }
        if (role.getStatus() != null) {
            existing.setStatus(role.getStatus());
        }
        existing.setUpdateTime(new Date());

        return dao().updateEntity(existing);
    }

    /**
     * 删除角色
     */
    @BizMutation
    @Transactional
    public void deleteRole(@Name("roleId") String roleId) {
        NopAuthRole role = dao().getEntityById(roleId);
        if (role == null) {
            throw new NopException(ERR_AUTH_ROLE_NOT_FOUND)
                .param(ARG_ROLE_ID, roleId);
        }

        // 删除角色权限关系
        dao().deleteByFilter(
            NopAuthRolePermission.PROP_NAME_roleId,
            FilterBean.eq(roleId)
        );

        // 删除角色用户关系
        dao().deleteByFilter(
            NopAuthUserRole.PROP_NAME_roleId,
            FilterBean.eq(roleId)
        );

        // 删除角色
        dao().deleteEntity(role);
    }

    /**
     * 获取角色权限
     */
    @BizQuery
    public List<NopAuthPermission> getRolePermissions(@Name("roleId") String roleId) {
        NopAuthRole role = dao().getEntityById(roleId);
        if (role == null) {
            throw new NopException(ERR_AUTH_ROLE_NOT_FOUND)
                .param(ARG_ROLE_ID, roleId);
        }

        return role.getPermissions();
    }

    /**
     * 分配权限
     */
    @BizMutation
    @Transactional
    public void assignPermission(@Name("roleId") String roleId,
                                  @Name("permissionId") String permissionId) {
        // 验证角色存在
        NopAuthRole role = dao().getEntityById(roleId);
        if (role == null) {
            throw new NopException(ERR_AUTH_ROLE_NOT_FOUND)
                .param(ARG_ROLE_ID, roleId);
        }

        // 验证权限存在
        NopAuthPermission permission = permissionDao.getEntityById(permissionId);
        if (permission == null) {
            throw new NopException(ERR_AUTH_PERMISSION_NOT_FOUND)
                .param(ARG_PERMISSION_ID, permissionId);
        }

        // 检查是否已分配
        if (isPermissionAssigned(roleId, permissionId)) {
            return;
        }

        // 创建角色权限关系
        NopAuthRolePermission rolePermission = new NopAuthRolePermission();
        rolePermission.setRoleId(roleId);
        rolePermission.setPermissionId(permissionId);
        dao().saveEntity(rolePermission);
    }

    /**
     * 移除权限
     */
    @BizMutation
    @Transactional
    public void removePermission(@Name("roleId") String roleId,
                                  @Name("permissionId") String permissionId) {
        dao().deleteByFilter(
            NopAuthRolePermission.PROP_NAME_roleId,
            FilterBean.and(
                FilterBean.eq(NopAuthRolePermission.PROP_NAME_roleId, roleId),
                FilterBean.eq(NopAuthRolePermission.PROP_NAME_permissionId, permissionId)
            )
        );
    }

    /**
     * 检查角色编码是否存在
     */
    private boolean isRoleCodeExists(String roleCode) {
        return dao().exists(
            FilterBean.eq(NopAuthRole.PROP_NAME_roleCode, roleCode)
        );
    }

    /**
     * 检查权限是否已分配
     */
    private boolean isPermissionAssigned(String roleId, String permissionId) {
        return dao().exists(
            FilterBean.and(
                FilterBean.eq(NopAuthRolePermission.PROP_NAME_roleId, roleId),
                FilterBean.eq(NopAuthRolePermission.PROP_NAME_permissionId, permissionId)
            )
        );
    }
}
```

## 权限控制实现

### 1. 权限拦截器

```java
package io.nop.auth.interceptor;

import io.nop.api.core.annotations.ioc.Bean;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.domain.NopAuthUser;
import io.nop.auth.service.NopAuthService;
import io.nop.graphql.core.IGraphQLInterceptor;
import io.nop.graphql.core.IGraphQLRequest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;

import static io.nop.auth.AuthErrors.*;

public class AuthInterceptor implements IGraphQLInterceptor {

    @Inject
    protected NopAuthService authService;

    @Override
    public Object intercept(Invocation inv) throws Throwable {
        IGraphQLRequest request = inv.getRequest();

        // 获取当前用户
        NopAuthUser currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            throw new NopException(ERR_AUTH_NOT_LOGIN);
        }

        // 检查用户状态
        if (currentUser.getStatus() != 1) {
            throw new NopException(ERR_AUTH_USER_DISABLED)
                .param(ARG_USER_ID, currentUser.getUserId());
        }

        // 检查权限
        String operationName = request.getOperationName();
        if (!hasPermission(currentUser, operationName)) {
            throw new NopException(ERR_AUTH_NO_PERMISSION)
                .param(ARG_OPERATION_NAME, operationName);
        }

        // 继续执行
        return inv.proceed();
    }

    /**
     * 检查用户是否有权限
     */
    private boolean hasPermission(NopAuthUser user, String operationName) {
        // 获取用户所有权限
        Set<String> permissions = authService.getUserPermissions(user.getUserId());

        // 检查是否有访问权限
        return permissions.contains(operationName) ||
               permissions.contains("*"); // 超级管理员
    }
}
```

## 测试用例

### 1. 用户管理测试

```java
package io.nop.auth.service;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.auth.domain.NopAuthUser;
import io.nop.auth.domain.NopAuthRole;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NopAuthUserBizModelTest extends JunitBaseTestCase {

    @Inject
    private NopAuthUserBizModel userBizModel;

    @Inject
    private NopAuthRoleBizModel roleBizModel;

    @Test
    public void testCreateUser() {
        NopAuthUser user = new NopAuthUser();
        user.setUserName("testuser");
        user.setPassword("password123");
        user.setEmail("test@example.com");

        NopAuthUser created = userBizModel.createUser(user);

        assertNotNull(created);
        assertNotNull(created.getUserId());
        assertEquals("testuser", created.getUserName());
        assertEquals(1, created.getStatus());
    }

    @Test
    public void testAssignRole() {
        // 创建用户
        NopAuthUser user = new NopAuthUser();
        user.setUserName("testuser");
        user.setPassword("password123");
        user = userBizModel.createUser(user);

        // 创建角色
        NopAuthRole role = new NopAuthRole();
        role.setRoleName("测试角色");
        role.setRoleCode("test_role");
        role = roleBizModel.createRole(role);

        // 分配角色
        userBizModel.assignRole(user.getUserId(), role.getRoleId());

        // 验证
        List<NopAuthRole> roles = userBizModel.getUserRoles(user.getUserId());
        assertEquals(1, roles.size());
        assertEquals(role.getRoleId(), roles.get(0).getRoleId());
    }
}
```

## 最佳实践

1. **密码安全**: 使用BCrypt等安全的加密算法加密密码
2. **权限最小化**: 只分配必要的权限，避免过度授权
3. **审计日志**: 记录关键操作的审计日志
4. **事务管理**: 使用@Transactional注解保证数据一致性
5. **异常处理**: 使用NopException统一异常处理
6. **参数验证**: 对输入参数进行严格验证
7. **缓存优化**: 合理使用缓存提高性能

## 总结

NopAuth项目展示了如何使用Nop Platform构建完整的用户权限管理系统：

1. **数据库模型**: 使用XML定义数据模型
2. **实体类**: 自动生成实体类和DAO接口
3. **业务模型**: 使用BizModel封装业务逻辑
4. **GraphQL API**: 自动生成GraphQL查询和变更
5. **权限控制**: 实现基于角色的访问控制

遵循这些模式，可以快速构建安全、可靠的权限管理系统。

## 相关文档

- [服务层开发指南](../getting-started/service/service-layer-development.md)
- [IEntityDao使用指南](../getting-started/dao/entitydao-usage.md)
- [GraphQL服务开发指南](../getting-started/api/graphql-guide.md)
- [事务管理指南](../getting-started/core/transaction-guide.md)
- [异常处理指南](../getting-started/core/exception-guide.md)

---

**文档版本**: 1.0
**最后更新**: 2025-01-09
**作者**: AI Assistant (Sisyphus)
