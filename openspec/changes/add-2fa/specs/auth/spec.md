# Delta for Authentication

## ADDED Requirements

### Requirement: 双因素认证（2FA）
系统SHALL支持双因素认证增强账户安全性。

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

#### Scenario: 备用恢复码认证
- WHEN 用户无法访问2FA设备
- AND 使用备用恢复码登录
- THEN 每个恢复码仅能使用一次
- AND 使用后恢复码失效
- AND 系统建议用户生成新的恢复码

### Requirement: 2FA配置管理
系统MUST提供用户管理其2FA配置的界面和API。

#### Scenario: 启用TOTP
- WHEN 用户选择启用TOTP
- THEN 系统生成TOTP密钥
- AND 系统生成二维码URL
- AND 用户使用密钥配置TOTP应用
- AND 验证成功后启用TOTP

#### Scenario: 启用短信验证码
- WHEN 用户选择启用短信验证码
- THEN 系统发送验证码到用户手机
- AND 用户输入验证码验证手机号
- AND 验证成功后启用短信验证码

#### Scenario: 生成备用恢复码
- WHEN 用户启用或重新启用2FA
- THEN 系统生成一组备用恢复码（默认10个）
- AND 用户可查看和下载恢复码
- AND 恢复码使用AES加密存储

#### Scenario: 禁用2FA
- WHEN 用户禁用2FA
- THEN 系统要求输入当前密码确认
- OR 系统要求输入2FA验证码
- AND 确认成功后禁用2FA
- AND 清除所有2FA配置和恢复码

### Requirement: 2FA登录流程
系统SHALL在已启用2FA的情况下，要求用户完成两步验证。

#### Scenario: 第一步密码验证
- WHEN 用户提交用户名和密码
- THEN 系统验证用户名和密码
- AND 如已启用2FA，生成临时令牌
- AND 返回临时令牌和可用验证方式
- AND 要求用户进行第二步验证

#### Scenario: 第二步2FA验证
- WHEN 用户提交临时令牌和2FA验证码
- THEN 系统验证临时令牌有效性
- AND 根据验证方式验证2FA码
- AND 验证成功后生成JWT访问令牌
- AND JWT令牌标记2FA已验证

#### Scenario: 临时令牌过期
- WHEN 临时令牌超过有效期（默认5分钟）
- THEN 系统拒绝验证请求
- AND 返回临时令牌过期错误
- AND 要求用户重新从第一步开始

### Requirement: 2FA安全机制
系统MUST实施严格的安全措施保护2FA功能。

#### Scenario: TOTP验证时间窗口
- WHEN 验证TOTP码
- THEN 系统使用±1个时间步的窗口（共3个30秒周期）
- AND 在此窗口内的验证码视为有效
- AND 超出窗口的验证码视为无效

#### Scenario: 短信验证码频率限制
- WHEN 用户请求发送短信验证码
- THEN 系统检查发送频率限制
- AND 1分钟内只能发送1次
- AND 1天内只能发送10次
- AND 超出限制返回错误提示

#### Scenario: 验证码失败锁定
- WHEN 用户连续5次验证失败
- THEN 系统锁定用户的2FA验证功能
- AND 锁定时长30分钟
- AND 系统记录安全事件
- AND 管理员可重置锁定

#### Scenario: 密钥加密存储
- WHEN 系统存储TOTP密钥、恢复码、短信验证码
- THEN 所有敏感数据使用AES-256加密
- AND 加密密钥安全存储（不在代码中）
- AND 日志中不记录明文

### Requirement: 多租户2FA配置
系统SHALL支持租户级别的2FA配置。

#### Scenario: 租户启用2FA
- WHEN 租户管理员启用2FA（租户级别）
- THEN 该租户下所有用户必须启用2FA
- AND 可设置强制启用或推荐启用
- AND 未启用用户登录时提示启用

#### Scenario: 租户选择2FA方式
- WHEN 租户配置可选的2FA方式
- THEN 用户只能从可选方式中选择
- AND 可禁用某些方式（如禁用短信）
- AND 配置生效后立即应用

### Requirement: 2FA审计日志
系统MUST记录所有2FA相关操作用于审计和安全分析。

#### Scenario: 记录2FA启用
- WHEN 用户启用2FA
- THEN 系统记录：
  - 用户ID
  - 启用时间
  - 验证方式（TOTP/短信）
  - IP地址
  - 设备信息

#### Scenario: 记录2FA验证
- WHEN 用户进行2FA验证
- THEN 系统记录：
  - 用户ID
  - 验证时间
  - 验证方式
  - 验证结果（成功/失败）
  - 验证码（失败时记录部分，如前2位）
  - IP地址

#### Scenario: 记录恢复码使用
- WHEN 用户使用恢复码登录
- THEN 系统记录：
  - 用户ID
  - 使用时间
  - 使用的恢复码索引（不记录完整码）
  - IP地址
  - 原因（丢失设备等）

### Requirement: 关键操作2FA重新验证
系统SHALL支持对关键操作要求重新验证2FA。

#### Scenario: 修改密码要求2FA
- WHEN 用户修改密码
- AND 用户已启用2FA
- THEN 系统要求重新验证2FA
- AND 验证通过后允许修改密码

#### Scenario: 修改邮箱要求2FA
- WHEN 用户修改绑定的邮箱
- AND 用户已启用2FA
- THEN 系统要求重新验证2FA
- AND 验证通过后允许修改邮箱

#### Scenario: 管理员操作要求2FA
- WHEN 管理员执行敏感操作（如删除用户）
- AND 管理员已启用2FA
- THEN 系统要求重新验证2FA
- AND 验证通过后允许执行操作

## MODIFIED Requirements

### Requirement: 用户登录
系统SHALL支持用户通过用户名和密码登录系统。
**[MODIFIED]** 在已启用2FA的情况下，登录流程需要两步验证。

#### Scenario: 有效凭据登录（修改）
- WHEN 用户提交有效的用户名和密码
- THEN 系统验证凭据成功
- AND [MODIFIED] 检查用户是否启用2FA
- AND [MODIFIED] 如未启用2FA，生成JWT访问令牌并返回
- AND [MODIFIED] 如已启用2FA，生成临时令牌并返回，要求第二步验证
- AND [MODIFIED] 临时令牌有效期5分钟

#### Scenario: 无效凭据登录（不变）
- WHEN 用户提交无效的用户名或密码
- THEN 系统返回认证失败错误
- AND 错误信息不泄露具体原因
- AND 系统记录失败的登录尝试

### Requirement: 会话管理
系统SHALL维护用户的活跃会话信息。
**[MODIFIED]** 会话信息包含2FA验证状态。

#### Scenario: 创建会话（修改）
- WHEN 用户成功登录
- AND 完成所有验证步骤（包括2FA）
- THEN 系统创建新的会话记录
- AND 记录会话ID、用户ID、登录时间、IP地址、设备信息
- AND [MODIFIED] 记录2FA验证方式和验证时间
- AND [MODIFIED] JWT令牌包含2FA验证标识

### Requirement: 安全审计
系统MUST记录所有认证相关操作用于审计。
**[MODIFIED]** 审计日志扩展包含2FA相关事件。

#### Scenario: 登录审计日志（修改）
- WHEN 用户登录（成功或失败）
- THEN 系统记录：
  - 用户ID或用户名（失败时）
  - 登录时间
  - 登录IP地址
  - 设备信息（User-Agent）
  - 登录结果（成功/失败/原因）
  - [MODIFIED] 是否完成2FA验证
  - [MODIFIED] 2FA验证方式
  - [MODIFIED] 令牌ID（成功时）

## REMOVED Requirements

（无删除的需求，仅增加和修改）

## Non-Functional Requirements Updates

### Security Updates

原安全需求添加：

**2FA安全要求**：
- TOTP密钥必须加密存储（AES-256）
- 恢复码必须加密存储（bcrypt或AES-256）
- 短信验证码必须加密存储（AES-256）
- TOTP验证时间窗口：±1个时间步（30秒）
- 短信验证码有效期：5分钟
- 验证失败5次锁定2FA 30分钟
- 短信发送频率限制：1分钟1次，每天10次
- 恢复码数量：默认10个，可配置
- 临时令牌有效期：5分钟

### Performance Updates

性能需求添加：

**2FA性能要求**：
- TOTP验证响应时间 < 50ms（P95）
- 短信发送异步处理，不阻塞登录流程
- 短信验证码验证响应时间 < 100ms（P95）
- 恢复码验证响应时间 < 50ms（P95）
- 2FA验证不显著增加登录总时间（< 1s）

### Availability Updates

可用性需求添加：

**2FA可用性要求**：
- TOTP验证可用性 > 99.9%
- 短信服务可用性 > 99.5%（依赖外部服务商）
- 支持2FA功能的降级模式（配置关闭）
- 恢复码在紧急情况下提供备用登录方式

## Configuration Updates

在原配置部分添加2FA相关配置：

```yaml
nop:
  auth:
    # JWT配置（保持不变）
    jwt:
      secret: ${JWT_SECRET:your-secret-key}
      expiration: 7200  # 2小时，单位秒
      refresh-expiration: 604800  # 7天
      algorithm: HS256

    # 2FA配置（新增）
    2fa:
      enabled: true  # 是否启用2FA功能
      methods: totp,sms  # 可用的2FA方式
      totp:
        window-size: 1  # 验证时间窗口（±1步）
        digits: 6  # 验证码位数
        period: 30  # 时间步长（秒）
        algorithm: HmacSHA1
      sms:
        provider: aliyun  # aliyun/tencent
        code-length: 6  # 验证码长度
        code-expiry: 300  # 验证码有效期（秒）
        rate-limit:
          per-minute: 1  # 每分钟发送次数
          per-day: 10  # 每天发送次数
        aliyun:
          access-key-id: ${ALIYUN_ACCESS_KEY_ID}
          access-key-secret: ${ALIYUN_ACCESS_KEY_SECRET}
          sign-name: NopPlatform
          template-code: SMS_123456789
        tencent:
          secret-id: ${TENCENT_SECRET_ID}
          secret-key: ${TENCENT_SECRET_KEY}
          sdk-app-id: ${TENCENT_SDK_APP_ID}
          sign-name: NopPlatform
          template-id: 123456
      recovery:
        codes-count: 10  # 恢复码数量
      temp-token:
        expiry: 300  # 临时令牌有效期（秒）
      security:
        max-failed-attempts: 5  # 最大失败次数
        lockout-duration: 1800  # 锁定时长（秒）
    # 其他配置保持不变...
```

## Dependencies Updates

### Internal Dependencies

新增内部依赖：
- **nop-auth-2fa**: 2FA功能模块（新建）
- 依赖现有模块：nop-auth、nop-ioc、nop-core

### External Dependencies

新增外部依赖：
- **Apache Commons Codec**: TOTP算法实现
- **ZXing**: 二维码生成（可选）
- **BCrypt**: 密码加密（用于恢复码）

### Integration Services

新增集成服务：
- **阿里云短信服务**: 可选
- **腾讯云短信服务**: 可选
- **Redis**: 缓存验证码和频率限制（可选）

## Related Specs

新增相关规格：
- [Two-Factor Authentication Implementation Spec](./2fa-implementation/spec.md) - 2FA实现的详细规格（可选，如需要）

## Migration Notes

从当前版本迁移到支持2FA的版本：

1. **数据库迁移**：
   - 执行V2.0.1__add_2fa_tables.sql创建新表
   - 修改t_sys_user表添加two_factor_enabled字段
   - 创建回滚脚本

2. **配置迁移**：
   - 添加2FA配置项到application.yaml
   - 默认2FA功能关闭（`enabled: false`）
   - 配置短信服务（如需使用）

3. **代码迁移**：
   - 修改登录流程支持两步验证
   - 添加2FA验证API
   - 前端添加2FA界面

4. **灰度发布**：
   - 首先启用2FA功能但不强制使用
   - 对管理员和高权限用户推荐启用
   - 收集反馈后决定是否强制要求

5. **用户通知**：
   - 发布用户通知说明2FA功能
   - 提供详细的使用指南
   - 提供技术支持

## Backward Compatibility

向后兼容性说明：

1. **未启用2FA的用户**：
   - 登录流程保持不变
   - 不影响现有功能
   - 可选择启用2FA

2. **已启用2FA的用户**：
   - 必须完成两步验证
   - 临时令牌过期需要重新登录
   - 可随时禁用2FA

3. **API兼容性**：
   - 原有/login API保持兼容
   - 新增/login/2fa API
   - JWT令牌格式兼容

4. **配置兼容性**：
   - 默认2FA关闭，不影响现有配置
   - 添加2FA配置是可选的

## Security Considerations

安全考虑更新：

1. **密钥管理**：
   - TOTP密钥必须使用AES-256加密存储
   - 加密密钥不能硬编码在代码中
   - 使用密钥管理服务（KMS）推荐

2. **日志安全**：
   - 不在日志中记录完整的验证码
   - 记录验证码失败时可记录部分（如前2位）
   - 敏感操作审计日志

3. **传输安全**：
   - 所有API使用HTTPS
   - 临时令牌和JWT令牌通过HTTPS传输
   - 前端不缓存临时令牌

4. **暴力破解防护**：
   - 验证码失败5次锁定
   - 短信发送频率限制
   - IP地址级别的频率限制

## Testing Considerations

测试考虑更新：

1. **单元测试**：
   - TOTP生成和验证逻辑
   - 短信验证码生成和验证逻辑
   - 恢复码生成和验证逻辑
   - 加密解密功能
   - 频率限制逻辑

2. **集成测试**：
   - 完整的2FA启用流程
   - 2FA登录流程
   - 各种错误场景
   - 安全测试

3. **E2E测试**：
   - 用户注册并启用2FA
   - 使用2FA登录
   - 使用恢复码登录
   - 禁用2FA

4. **性能测试**：
   - TOTP验证性能
   - 短信发送异步化
   - 登录流程总时间
   - 并发登录测试

5. **安全测试**：
   - 暴力破解防护
   - 密钥加密存储验证
   - 会话管理验证
   - SQL注入防护
