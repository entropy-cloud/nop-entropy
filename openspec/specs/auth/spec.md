# Authentication Specification

## Purpose

定义Nop平台的认证和会话管理功能，包括用户登录、会话维护、JWT令牌管理、SSO集成等。

## Requirements

### Requirement: 用户登录
系统SHALL支持用户通过用户名和密码登录系统。

#### Scenario: 有效凭据登录
- WHEN 用户提交有效的用户名和密码
- THEN 系统验证凭据成功
- AND 系统生成JWT访问令牌
- AND 令牌包含用户ID和角色信息
- AND 系统返回令牌和过期时间

#### Scenario: 无效凭据登录
- WHEN 用户提交无效的用户名或密码
- THEN 系统返回认证失败错误
- AND 错误信息不泄露具体原因
- AND 系统记录失败的登录尝试

#### Scenario: 账户被锁定登录
- WHEN 用户账户已被锁定（例如：多次登录失败）
- THEN 系统拒绝登录请求
- AND 返回账户锁定错误信息
- AND 提示解锁方式或联系管理员

#### Scenario: 密码过期登录
- WHEN 用户密码已过期
- THEN 系统强制要求修改密码
- AND 不生成访问令牌
- AND 提示用户跳转到密码修改页面

### Requirement: JWT令牌管理
系统MUST使用JWT（JSON Web Token）作为访问令牌。

#### Scenario: 生成访问令牌
- WHEN 用户成功登录
- THEN 系统生成符合RFC 7519标准的JWT令牌
- AND 令牌包含标准声明：iss, sub, exp, iat
- AND 令牌包含自定义声明：userId, roles, tenantId
- AND 令牌使用HS256算法签名
- AND 令牌有效期为2小时

#### Scenario: 验证访问令牌
- WHEN 客户端请求携带JWT令牌
- THEN 系统验证令牌签名
- AND 系统验证令牌未过期
- AND 系统提取用户身份信息
- AND 通过验证后授予访问权限

#### Scenario: 令牌刷新
- WHEN 令牌即将过期（剩余时间< 30分钟）
- THEN 客户端可使用刷新令牌获取新的访问令牌
- AND 系统验证刷新令牌有效性
- AND 生成新的访问令牌
- AND 刷新令牌有效期延长

#### Scenario: 令牌撤销
- WHEN 用户登出
- OR 管理员撤销用户令牌
- THEN 系统将令牌加入黑名单
- AND 系统拒绝该令牌的后续请求
- AND 相关刷新令牌同时失效

### Requirement: 会话管理
系统SHALL维护用户的活跃会话信息。

#### Scenario: 创建会话
- WHEN 用户成功登录
- THEN 系统创建新的会话记录
- AND 记录会话ID、用户ID、登录时间、IP地址、设备信息
- AND 会话ID存储在Redis中（如可用）

#### Scenario: 查询活跃会话
- WHEN 管理员查询用户的活跃会话
- THEN 系统返回该用户的所有活跃会话
- AND 包括会话ID、设备信息、最后活动时间
- AND 支持分页查询

#### Scenario: 终止会话
- WHEN 用户主动登出
- OR 管理员强制用户登出
- THEN 系统终止指定会话
- AND 清除相关令牌
- AND 记录登出时间和原因

#### Scenario: 并发登录限制
- WHEN 用户尝试从新设备登录
- AND 用户已有最大允许的并发会话数
- THEN 根据策略处理：
  - 策略A：拒绝新登录，提示已有活跃会话
  - 策略B：终止最旧的会话，允许新登录
  - 策略C：提示用户选择保留哪个会话

### Requirement: SSO单点登录
系统SHALL支持与外部身份提供商（IdP）集成，实现SSO单点登录。

#### Scenario: OIDC登录流程
- WHEN 用户选择SSO登录（例如：Keycloak）
- THEN 系统重定向到IdP的认证页面
- AND 传递state和nonce参数
- AND IdP认证后回调到系统
- AND 系统验证回调参数
- AND 提取用户信息并创建本地会话

#### Scenario: SAML登录流程
- WHEN 企业使用SAML协议进行SSO
- THEN 系统支持SAML 2.0协议
- AND 解析SAML响应断言
- AND 验证数字签名
- AND 映射外部用户到本地用户

#### Scenario: 用户属性映射
- WHEN SSO用户首次登录
- THEN 系统根据映射规则创建本地用户
- AND 同步用户基本信息（姓名、邮箱等）
- AND 分配默认角色
- AND 支持自定义属性映射规则

### Requirement: 多租户认证
系统MUST支持多租户架构下的认证。

#### Scenario: 租户隔离登录
- WHEN 用户登录
- THEN 系统根据租户标识（域名、子域名或参数）识别租户
- AND 验证用户属于该租户
- AND JWT令牌包含tenantId
- AND 后续请求基于tenantId进行数据隔离

#### Scenario: 租户管理员登录
- WHEN 租户管理员登录
- THEN 系统授予租户管理权限
- AND 管理员只能管理本租户的用户和资源
- AND 无法访问其他租户的数据

#### Scenario: 跨租户用户（超级管理员）
- WHEN 超级管理员登录
- AND 用户被标记为跨租户管理员
- THEN 系统允许切换租户上下文
- AND 可在多个租户间切换管理

### Requirement: 安全审计
系统MUST记录所有认证相关操作用于审计。

#### Scenario: 登录审计日志
- WHEN 用户登录（成功或失败）
- THEN 系统记录：
  - 用户ID或用户名（失败时）
  - 登录时间
  - 登录IP地址
  - 设备信息（User-Agent）
  - 登录结果（成功/失败/原因）
  - 令牌ID（成功时）

#### Scenario: 会话操作审计
- WHEN 创建、查询、终止会话
- THEN 系统记录操作人、时间、原因
- AND 记录受影响的会话ID

#### Scenario: 令牌操作审计
- WHEN 生成、刷新、撤销令牌
- THEN 系统记录令牌操作类型
- AND 记录操作时间和操作人
- AND 不记录令牌完整内容（仅记录ID）

### Requirement: 密码策略
系统SHALL实施密码安全策略。

#### Scenario: 密码复杂度要求
- WHEN 用户设置或修改密码
- THEN 密码必须满足：
  - 最小长度：8个字符
  - 包含大写字母
  - 包含小写字母
  - 包含数字
  - 包含特殊字符（!@#$%^&*等）
  OR 自定义规则（可配置）

#### Scenario: 密码历史限制
- WHEN 用户修改密码
- THEN 新密码不能与最近N次使用的密码重复
- AND N默认为5，可配置

#### Scenario: 密码过期策略
- WHEN 用户密码达到最大使用期限
- THEN 系统强制用户修改密码
- AND 默认期限为90天，可配置
- AND 支持设置永不过期（管理员）

#### Scenario: 密码重置
- WHEN 用户忘记密码
- AND 通过邮箱或手机验证
- THEN 系统允许重置密码
- AND 生成一次性临时密码
- AND 临时密码有效期24小时
- AND 首次使用后强制修改

### Requirement: 双因素认证（2FA）
系统SHALL支持双因素认证增强安全性（可选功能）。

#### Scenario: TOTP令牌认证
- WHEN 用户启用了TOTP（基于时间的一次性密码）
- THEN 登录流程增加第二步验证
- AND 用户输入6位TOTP码
- AND 系统验证TOTP码有效性
- AND 验证通过后生成访问令牌

#### Scenario: 短信验证码认证
- WHEN 用户使用短信验证码作为第二因素
- THEN 系统发送6位数字验证码到用户手机
- AND 验证码有效期5分钟
- AND 验证码使用后立即失效

#### Scenario: 备用恢复码
- WHEN 用户无法访问2FA设备
- AND 启用了备用恢复码
- THEN 用户可使用恢复码登录
- AND 每个恢复码仅能使用一次
- AND 使用后建议生成新的恢复码

## Non-Functional Requirements

### Performance
- 登录请求响应时间 < 500ms（P95）
- 令牌验证响应时间 < 10ms（P95）
- 支持1000 TPS并发登录

### Security
- 密码使用bcrypt加密（cost factor >= 10）
- JWT签名密钥定期轮换（推荐每年）
- 防止暴力破解（登录失败5次锁定30分钟）
- 支持CORS配置（跨域请求）

### Availability
- 认证服务可用性 > 99.9%
- 支持水平扩展（无状态设计）
- 令牌验证无数据库依赖（本地验证）

### Scalability
- 支持100万并发用户会话
- Redis存储会话时支持集群模式
- 令牌验证可独立扩展为微服务

## Dependencies

### External Systems
- **Keycloak**: SSO身份提供商（可选）
- **Redis**: 会话存储（可选，支持内存存储）
- **SMTP服务**: 密码重置邮件

### Internal Modules
- **nop-auth**: 认证核心模块
- **nop-ioc**: 依赖注入
- **nop-xlang**: 脚本语言（用于密码策略配置）
- **nop-config**: 配置中心
- **nop-dao**: 数据访问

## Configuration

### 示例配置（application.yaml）

```yaml
nop:
  auth:
    # JWT配置
    jwt:
      secret: ${JWT_SECRET:your-secret-key}
      expiration: 7200  # 2小时，单位秒
      refresh-expiration: 604800  # 7天
      algorithm: HS256

    # 密码策略
    password:
      min-length: 8
      require-uppercase: true
      require-lowercase: true
      require-digit: true
      require-special: true
      history-limit: 5
      max-age-days: 90

    # 会话管理
    session:
      max-concurrent-sessions: 3
      session-timeout: 3600  # 1小时，单位秒
      strategy: TERMINATE_OLDEST  # DENY, TERMINATE_OLDEST, TERMINATE_ALL

    # 登录限制
    login:
      max-attempts: 5
      lockout-duration: 1800  # 30分钟，单位秒

    # SSO配置
    sso:
      enabled: false
      provider: keycloak
      oidc:
        issuer: https://your-keycloak.com/realms/nop
        client-id: nop-client
        client-secret: ${KEYCLOAK_SECRET}
```

## Related Specs

- [Authorization Specification](./authorization/spec.md) - 授权和权限管理
- [User Management Specification](./user-management/spec.md) - 用户管理
- [Audit Specification](./audit/spec.md) - 审计日志

## Migration Notes

从Nop Platform 1.x迁移到2.x：
- 认证协议从自定义改为标准JWT
- 会话存储从数据库改为Redis
- SSO支持从零开始实现
- 向后兼容旧版本令牌（设置适当的过渡期）
