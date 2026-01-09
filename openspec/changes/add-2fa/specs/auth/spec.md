## MODIFIED Requirements

### Requirement: 双因素认证（2FA）
系统SHALL支持双因素认证增强安全性，包括TOTP、短信验证码和恢复码三种方式，使用GraphQL API和Nop平台的BizModel模式。

#### Scenario: TOTP令牌认证启用（增强）
- WHEN 用户请求启用TOTP认证
- AND 调用enableTotp GraphQL mutation
- AND 系统生成TOTP密钥
- AND 系统生成二维码URL
- THEN 系统使用IOrmEntityDao保存配置到nop_auth_2fa_config表
- AND totp_secret使用AES加密存储
- AND 系统返回密钥和二维码URL

#### Scenario: TOTP令牌认证绑定（增强）
- WHEN 用户扫描二维码并输入TOTP验证码
- AND 调用verifyAndEnableTotp GraphQL mutation
- AND 使用ITotpService验证TOTP码
- AND TOTP验证码验证成功
- THEN 使用IRecoveryCodeService生成10个恢复码
- AND 恢复码使用JSON格式存储，AES加密
- AND 系统启用用户的2FA配置
- AND 更新nop_auth_user表的twoFactorEnabled字段为true
- AND 系统返回成功响应

#### Scenario: TOTP令牌认证验证（增强）
- WHEN 用户启用了TOTP（基于时间的一次性密码）
- AND 在登录时输入6位TOTP码
- AND 调用verify2fa GraphQL mutation
- AND TOTP码在有效时间窗口内（±30秒）
- AND TOTP码验证成功
- THEN 使用ITotpService验证TOTP码有效性
- AND 更新nop_auth_2fa_config表的last_used_at字段
- AND 系统生成JWT访问令牌
- AND 系统记录2FA验证事件

#### Scenario: 短信验证码认证发送（增强）
- WHEN 用户请求启用短信验证码认证
- AND 提供手机号码
- AND 调用enableSms GraphQL mutation
- AND 使用ISmsCodeService检查发送频率限制
- THEN 系统检查发送频率限制（每分钟1次，每天10次）
- AND 使用ISmsService发送6位数字验证码到用户手机
- AND 验证码有效期5分钟
- AND 系统在nop_auth_sms_log表记录短信发送日志
- AND code字段使用AES加密存储

#### Scenario: 短信验证码认证绑定（增强）
- WHEN 用户输入收到的短信验证码
- AND 调用verifyAndEnableSms GraphQL mutation
- AND 验证码在有效期内
- AND 验证码验证成功
- THEN 使用ISmsCodeService验证短信验证码有效性
- AND 使用IRecoveryCodeService生成10个恢复码
- AND 恢复码使用JSON格式存储，AES加密
- AND 系统启用用户的2FA配置
- AND 更新nop_auth_user表的twoFactorEnabled字段为true
- AND 系统返回成功响应

#### Scenario: 短信验证码认证验证（增强）
- WHEN 用户使用短信验证码作为第二因素
- AND 在登录时输入6位短信验证码
- AND 调用verify2fa GraphQL mutation
- AND 验证码在有效期内
- AND 验证码验证成功
- THEN 系统验证短信验证码有效性
- AND 系统生成JWT访问令牌
- AND 验证码使用后立即失效

#### Scenario: 备用恢复码生成（增强）
- WHEN 用户成功启用2FA（TOTP或短信）
- AND 使用NopAuth2faConfigBizModel
- AND 系统调用IRecoveryCodeService
- THEN 系统生成10个8位恢复码
- AND 使用IOrmEntityDao保存到nop_auth_2fa_config表
- AND 恢复码使用JSON格式存储
- AND 使用AES加密整个JSON字符串
- AND 系统返回恢复码列表供用户保存

#### Scenario: 备用恢复码使用（增强）
- WHEN 用户无法访问2FA设备
- AND 用户输入恢复码
- AND 调用verify2fa GraphQL mutation，method=recovery
- AND 恢复码验证成功
- THEN 系统允许用户登录
- AND 使用IRecoveryCodeService.markCodeUsed()标记该恢复码已使用
- AND 已使用的恢复码不能再次使用
- AND 系统提示用户生成新的恢复码

#### Scenario: 备用恢复码重新生成（增强）
- WHEN 用户请求重新生成恢复码
- AND 调用regenerateRecoveryCodes GraphQL mutation
- AND 使用NopAuth2faConfigBizModel
- AND 用户已启用2FA
- THEN 使用IRecoveryCodeService生成新的10个恢复码
- AND 旧的恢复码全部失效
- AND 使用IOrmEntityDao更新nop_auth_2fa_config表
- AND 系统返回新的恢复码列表

#### Scenario: 2FA配置查询（增强）
- WHEN 用户调用getRecoveryCodes GraphQL query
- AND 使用NopAuth2faConfigBizModel
- AND 用户已启用2FA
- THEN 系统返回可用的恢复码列表
- AND 系统不显示已使用的恢复码

#### Scenario: 2FA禁用（增强）
- WHEN 用户调用disable2fa GraphQL mutation
- AND 使用NopAuth2faConfigBizModel
- AND 提供用户ID
- THEN 使用IOrmEntityDao删除nop_auth_2fa_config表中的配置
- AND 更新nop_auth_user表的twoFactorEnabled字段为false
- AND 系统记录2FA禁用事件

#### Scenario: 登录流程 - 未启用2FA（修改）
- WHEN 用户提交有效的用户名和密码
- AND 使用NopAuthUserBizModel.login()
- AND 用户未启用2FA
- THEN 系统验证凭据成功
- AND 系统生成JWT访问令牌
- AND 令牌包含用户ID和角色信息
- AND 系统返回令牌和过期时间
- AND 系统记录登录成功事件

#### Scenario: 登录流程 - 第一步（已启用2FA）（修改）
- WHEN 用户提交有效的用户名和密码
- AND 用户已启用2FA
- AND 使用NopAuthUserBizModel.login()
- THEN 系统验证凭据成功
- AND 系统生成临时令牌（5分钟有效期）
- AND 系统返回临时令牌
- AND 系统返回requires2FA=true
- AND 系统返回可用的2FA方式列表（totp, sms, recovery）

#### Scenario: 登录流程 - 第二步（TOTP）（修改）
- WHEN 用户提交临时令牌
- AND 用户提交TOTP验证码
- AND 提交验证方式为totp
- AND 使用NopAuthUserBizModel.verify2fa()
- AND 临时令牌有效
- AND 使用ITotpService验证TOTP码成功
- THEN 系统验证临时令牌有效性
- AND 系统验证TOTP码有效性
- AND 更新nop_auth_2fa_config表的last_used_at字段
- AND 系统生成JWT访问令牌
- AND 系统返回JWT令牌和过期时间

#### Scenario: 登录流程 - 第二步（短信）（修改）
- WHEN 用户提交临时令牌
- AND 用户提交短信验证码
- AND 提交验证方式为sms
- AND 使用NopAuthUserBizModel.verify2fa()
- AND 临时令牌有效
- AND 使用ISmsCodeService验证短信验证码成功
- THEN 系统验证临时令牌有效性
- AND 系统验证短信验证码有效性
- AND 更新nop_auth_2fa_config表的last_used_at字段
- AND 系统生成JWT访问令牌
- AND 系统返回JWT令牌和过期时间

#### Scenario: 登录流程 - 第二步（恢复码）（修改）
- WHEN 用户提交临时令牌
- AND 用户提交恢复码
- AND 提交验证方式为recovery
- AND 使用NopAuthUserBizModel.verify2fa()
- AND 临时令牌有效
- AND 使用IRecoveryCodeService验证恢复码成功
- THEN 系统验证临时令牌有效性
- AND 使用IRecoveryCodeService.markCodeUsed()标记恢复码已使用
- AND 更新nop_auth_2fa_config表的last_used_at字段
- AND 系统生成JWT访问令牌
- AND 系统返回JWT令牌和过期时间
- AND 系统提示用户生成新的恢复码

#### Scenario: 短信发送频率限制（新增）
- WHEN 用户在1分钟内多次请求发送短信验证码
- AND 达到频率限制（每分钟1次）
- AND 使用ISmsCodeService.checkRateLimit()
- THEN 系统拒绝发送短信
- AND 系统返回ERR_AUTH_SMS_RATE_LIMIT_EXCEEDED错误（NopException）
- AND 系统记录尝试事件

#### Scenario: 短信发送日限制（新增）
- WHEN 用户在1天内多次请求发送短信验证码
- AND 达到频率限制（每天10次）
- AND 使用ISmsCodeService.checkRateLimit()
- THEN 系统拒绝发送短信
- AND 系统返回ERR_AUTH_SMS_RATE_LIMIT_EXCEEDED错误（NopException）
- AND 系统记录尝试事件

#### Scenario: TOTP验证失败锁定（新增）
- WHEN 用户连续5次输入错误的TOTP码
- AND 达到失败次数限制
- THEN 系统锁定用户的2FA验证30分钟
- AND 系统记录锁定事件
- AND 用户在锁定期间无法使用TOTP登录

#### Scenario: 短信验证码超时（新增）
- WHEN 用户输入的短信验证码已超过5分钟有效期
- AND 使用ISmsCodeService.verifyCode()
- THEN 系统返回ERR_AUTH_2FA_INVALID_CODE错误（NopException）
- AND 系统记录验证失败事件

#### Scenario: 临时令牌过期（新增）
- WHEN 用户使用已过期的临时令牌进行第二步验证
- AND 使用NopAuthUserBizModel.verify2fa()
- THEN 系统返回ERR_AUTH_TEMP_TOKEN_INVALID错误（NopException）
- AND 系统记录验证失败事件

#### Scenario: 恢复码已用尽（新增）
- WHEN 用户尝试使用所有恢复码后再次登录
- AND 所有恢复码已被标记为已使用
- AND 使用IRecoveryCodeService.verifyCode()
- THEN 系统拒绝使用恢复码登录
- AND 系统返回ERR_AUTH_RECOVERY_CODE_EXHAUSTED错误（NopException）
- AND 系统提示用户需要联系管理员
- OR 系统提示用户重新生成恢复码

### MODIFIED Requirements

### Requirement: 用户登录
系统SHALL支持用户通过用户名和密码登录系统，并支持双因素认证增强，使用Nop平台的BizModel模式和GraphQL API。

#### Scenario: 双因素认证登录流程（修改）
- WHEN 用户提交有效的用户名和密码
- AND 用户已启用2FA
- AND 使用NopAuthUserBizModel.login() GraphQL mutation
- THEN 系统不立即生成JWT令牌
- AND 系统返回临时令牌（5分钟有效期）
- AND 系统指示需要第二因素验证
- AND 系统返回可用的2FA方式列表

### MODIFIED Requirements

### Requirement: 安全审计
系统MUST记录所有认证相关操作用于审计，包括2FA相关操作，使用NopException和ErrorCode。

#### Scenario: 2FA启用审计日志（修改）
- WHEN 用户成功启用2FA
- AND 使用NopAuth2faConfigBizModel
- THEN 系统记录：
  - 用户ID
  - 2FA方式（totp/sms）
  - 启用时间
  - 操作类型（2FA_ENABLED）
  - IP地址
  - 设备信息

#### Scenario: 2FA禁用审计日志（修改）
- WHEN 用户禁用2FA
- AND 使用NopAuth2faConfigBizModel
- THEN 系统记录：
  - 用户ID
  - 禁用时间
  - 操作类型（2FA_DISABLED）
  - 操作原因（用户禁用/管理员重置）
  - IP地址

#### Scenario: 2FA验证审计日志（修改）
- WHEN 用户完成2FA验证
- AND 使用NopAuthUserBizModel.verify2fa()
- THEN 系统记录：
  - 用户ID
  - 验证方式（totp/sms/recovery）
  - 验证结果（成功/失败）
  - 验证时间
  - IP地址
  - 设备信息

#### Scenario: 短信发送审计日志（修改）
- WHEN 系统发送短信验证码
- AND 使用ISmsCodeService.sendCode()
- THEN 系统记录到nop_auth_sms_log表：
  - 用户ID
  - 手机号码（脱敏）
  - 发送时间
  - 发送结果（成功/失败）
  - 失败原因（如适用）

### MODIFIED Requirements

### Requirement: 数据库设计
系统SHALL使用符合Nop平台规范的数据库表结构存储2FA配置，使用IOrmEntityDao和OrmEntity。

#### Scenario: 2FA配置表结构（修改）
- WHEN 系统创建nop_auth_2fa_config表
- THEN 表包含以下字段：
  - sid: 主键ID（VARCHAR(32)，遵循Nop命名规范）
  - user_id: 用户ID（VARCHAR(32), UNIQUE）
  - two_factor_method: 2FA方式（VARCHAR(20)）
  - totp_secret: TOTP密钥（VARCHAR(255), 加密）
  - recovery_codes: 恢复码（TEXT, JSON加密）
  - phone_number: 手机号码（VARCHAR(20))
  - enabled: 是否启用（TINYINT(1)）
  - enabled_at: 启用时间（DATETIME）
  - last_used_at: 最后使用时间（DATETIME）
  - version: 乐观锁版本号（INTEGER）
  - created_time: 创建时间（DATETIME）
  - updated_time: 更新时间（DATETIME）

#### Scenario: 短信日志表结构（修改）
- WHEN 系统创建nop_auth_sms_log表
- THEN 表包含以下字段：
  - sid: 主键ID（VARCHAR(32)，遵循Nop命名规范）
  - user_id: 用户ID（VARCHAR(32), 可NULL）
  - phone_number: 手机号码（VARCHAR(20)）
  - code: 验证码（VARCHAR(100), 加密）
  - purpose: 用途（VARCHAR(20))
  - sent_at: 发送时间（DATETIME）
  - verified_at: 验证时间（DATETIME, 可NULL）
  - expired_at: 过期时间（DATETIME）
  - created_time: 创建时间（DATETIME）

#### Scenario: 用户表扩展（修改）
- WHEN 系统扩展nop_auth_user表
- THEN 表添加以下字段：
  - two_factor_enabled: 是否启用2FA（TINYINT(1), 默认0）
  - idx_two_factor_enabled: two_factor_enabled字段的索引

## ADDED Non-Functional Requirements

### 2FA Performance Requirements

#### Scenario: TOTP验证响应时间
- WHEN 用户提交TOTP验证码
- AND 使用ITotpService.verifyCode()
- THEN 系统在<100ms内完成验证（P95）

#### Scenario: 短信发送响应时间
- WHEN 用户请求发送短信验证码
- AND 使用ISmsCodeService.sendCode()
- THEN 系统在<500ms内完成请求（P95）
- AND 短信实际发送时间取决于短信服务商

#### Scenario: 2FA启用流程响应时间
- WHEN 用户完成2FA启用流程
- AND 使用NopAuth2faConfigBizModel
- THEN 整个流程在<2秒内完成（P95）

### 2FA Security Requirements

#### Scenario: 密钥加密存储
- WHEN 系统存储TOTP密钥
- AND 使用CryptoHelper.encryptAES()
- THEN 使用AES-256加密
- AND 加密密钥配置在application.yaml中使用IConfigReference
- AND 日志中不记录完整的密钥

#### Scenario: 恢复码安全存储
- WHEN 系统存储恢复码
- AND 使用JsonTool.instance().beanToJson()
- THEN 使用JSON格式存储
- AND 使用AES-256加密整个JSON字符串
- AND 每个恢复码仅能使用一次

#### Scenario: 验证码加密存储
- WHEN 系统存储短信验证码
- AND 使用CryptoHelper.encryptAES()
- THEN 使用AES-256加密
- AND 验证码在数据库和日志中都加密存储

#### Scenario: 防暴力破解
- WHEN 用户多次输入错误的2FA验证码
- THEN 5次失败后锁定30分钟
- AND 锁定期间拒绝所有2FA验证请求
- AND 记录失败尝试到审计日志

### 2FA Availability Requirements

#### Scenario: 2FA服务可用性
- WHEN 系统提供2FA服务
- THEN 2FA服务可用性 > 99.9%
- AND 支持水平扩展（无状态设计）

#### Scenario: 多短信服务商支持
- WHEN 主短信服务商不可用
- AND 使用ISmsService接口
- THEN 系统可快速切换到备用短信服务商
- AND 通过配置指定主要和备用服务商

## Configuration Updates

在原配置部分添加2FA相关配置，使用Nop平台的IConfigReference接口：

```yaml
nop:
  auth:
    # JWT配置（保持不变）
    jwt:
      secret: ${JWT_SECRET:your-secret-key}
      expiration: 7200  # 2小时，单位秒
      algorithm: HS256

    # 2FA配置（新增）
    2fa:
      enabled: true  # 是否启用2FA功能
      methods: totp,sms  # 可用的2FA方式
      totp:
        window-size: 1  # TOTP验证时间窗口（±1步）
        digits: 6       # 验证码位数
        period: 30      # 时间步长（秒）
        algorithm: HmacSHA1
      sms:
        provider: aliyun  # aliyun/tencent
        code-length: 6    # 验证码长度
        validity: 300     # 验证码有效期（秒）
        rate-limit:
          per-minute: 1   # 每分钟发送次数
          per-day: 10     # 每天发送次数
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
        code-count: 10    # 恢复码数量
      temp-token:
        expiry: 300  # 临时令牌有效期（秒）
      security:
        max-failed-attempts: 5  # 最大失败次数
        lockout-duration: 1800  # 锁定时长（秒）
```

## Dependencies Updates

### Internal Dependencies

修改内部依赖：
- **nop-auth**: 扩展NopAuthUserBizModel，添加2FA登录方法
- **nop-auth-2fa**: 新建2FA配置模块（NopAuth2faConfigBizModel）
- 依赖现有模块：nop-ioc、nop-core、nop-orm、nop-service-framework

### External Dependencies

新增外部依赖：
- **Apache Commons Codec**: TOTP算法实现
- **阿里云SMS SDK**: 可选，用于短信服务
- **腾讯云SMS SDK**: 可选，用于短信服务
- Nop平台内置加密工具：CryptoHelper

### Integration Services

新增集成服务：
- **阿里云短信服务**: 可选，通过ISmsService接口
- **腾讯云短信服务**: 可选，通过ISmsService接口

## Related Specs

修改相关规格：
- [Authentication Specification](../../specs/auth/spec.md) - 认证规格，包含2FA需求
