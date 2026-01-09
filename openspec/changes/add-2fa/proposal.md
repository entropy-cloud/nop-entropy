# Change Proposal: Add Two-Factor Authentication (2FA)

## Summary

为Nop平台的认证系统添加双因素认证（2FA）支持，增强账户安全性。实现TOTP（基于时间的一次性密码）和短信验证码两种2FA方式，使用Nop平台的BizModel模式和GraphQL API。

## Motivation

当前Nop平台仅支持用户名/密码认证，存在以下安全风险：
- 密码泄露导致账户被盗用
- 无法满足高安全场景要求（如企业内部系统）
- 不符合某些行业的安全合规要求

引入2FA可以：
- 大幅提升账户安全性
- 满足企业级安全要求
- 支持合规性需求（如GDPR、HIPAA）
- 提供灵活的第二因素选择

## Proposed Changes

### 1. 新增功能

#### 1.1 TOTP认证支持
- 支持Google Authenticator等标准TOTP应用
- 用户可绑定TOTP设备
- 登录时输入6位TOTP码验证
- 通过GraphQL API启用和验证

#### 1.2 短信验证码认证支持
- 发送6位数字验证码到用户手机
- 验证码有效期5分钟
- 支持短信服务配置（阿里云、腾讯云等）
- 实现发送频率限制

#### 1.3 备用恢复码
- 用户可生成一组备用恢复码（默认10个）
- 用于丢失2FA设备时登录
- 每个恢复码仅使用一次

#### 1.4 2FA管理GraphQL API
- 用户可启用/禁用2FA
- 查看绑定状态
- 生成和查看备用恢复码
- 重新绑定2FA设备

### 2. 修改现有功能

#### 2.1 登录流程增强
- 扩展`NopAuthUserBizModel`支持两步验证
- 用户登录成功后检查是否启用2FA
- 如已启用，要求输入第二因素验证码
- 验证通过后生成JWT令牌

#### 2.2 用户模型扩展
- 扩展`NopAuthUser`实体，添加2FA相关字段：
  - `twoFactorEnabled`: 是否启用2FA（冗余字段，提高查询性能）

#### 2.3 配置增强
- 添加2FA相关配置项：
  - 是否启用2FA（全局）
  - 可选的2FA方式（TOTP、短信）
  - 短信服务配置
  - 恢复码数量
  - 使用Nop平台的配置模式（`IConfigReference`）

### 3. GraphQL API新增

#### 3.1 TOTP相关Mutation
- `enableTotp(userId: ID!)`: 启用TOTP
  - 返回：密钥和二维码URL
- `verifyAndEnableTotp(userId: ID!, code: String!)`: 验证TOTP并完成绑定

#### 3.2 短信验证码相关Mutation
- `enableSms(userId: ID!, phoneNumber: String!)`: 发送短信验证码
- `verifyAndEnableSms(userId: ID!, code: String!)`: 验证短信验证码并完成绑定

#### 3.3 恢复码相关Mutation和Query
- `getRecoveryCodes(userId: ID!)`: 获取恢复码列表
- `regenerateRecoveryCodes(userId: ID!)`: 重新生成恢复码

#### 3.4 通用Mutation
- `disable2fa(userId: ID!)`: 禁用2FA

#### 3.5 2FA登录Mutation
- `login(username: String!, password: String!)`: 第一步登录
  - 未启用2FA：返回JWT令牌
  - 已启用2FA：返回临时令牌
- `verify2fa(tempToken: String!, code: String!, method: String!)`: 2FA第二步验证
  - 验证通过后返回JWT令牌

### 4. 数据库变更

#### 4.1 新增表
- `nop_auth_2fa_config`: 用户2FA配置
  - `sid`: 主键ID
  - `user_id`: 用户ID
  - `two_factor_method`: 2FA方式（totp/sms/none）
  - `totp_secret`: TOTP密钥（加密）
  - `recovery_codes`: 恢复码（JSON加密）
  - `phone_number`: 手机号码
  - `enabled`: 是否启用
  - `enabled_at`: 启用时间
  - `last_used_at`: 最后使用时间

- `nop_auth_sms_log`: 短信发送日志
  - `sid`: 主键ID
  - `user_id`: 用户ID
  - `phone_number`: 手机号
  - `code`: 验证码（加密）
  - `purpose`: 用途（login/bind）
  - `sent_at`: 发送时间
  - `verified_at`: 验证时间
  - `expired_at`: 过期时间
  - `ip_address`: IP地址

#### 4.2 修改表
- `nop_auth_user`: 添加字段
  - `two_factor_enabled`: 是否启用2FA（冗余字段，提高查询性能）

### 5. 架构设计

#### 5.1 服务层设计
使用Nop平台的BizModel模式：
- 创建`NopAuth2faConfigBizModel`继承`CrudBizModel<NopAuth2faConfig>`
- 扩展`NopAuthUserBizModel`添加2FA登录方法
- 使用`@BizQuery`和`@BizMutation`注解定义GraphQL操作

#### 5.2 数据访问层
使用Nop平台的ORM：
- 使用`IOrmEntityDao<NopAuth2faConfig>`进行数据访问
- 使用`IOrmEntityDao<NopAuthSmsLog>`管理短信日志
- 实体类继承`OrmEntity`

#### 5.3 服务接口
定义独立的服务接口：
- `ITotpService`: TOTP生成和验证
- `ISmsCodeService`: 短信验证码管理
- `IRecoveryCodeService`: 恢复码管理

### 6. 安全考虑

#### 6.1 密钥安全
- TOTP密钥使用AES加密存储（使用Nop平台的加密工具）
- 恢复码使用JSON格式存储，加密整个列表
- 日志中不记录完整的验证码

#### 6.2 防止暴力破解
- TOTP验证码错误5次锁定2FA设备
- 短信验证码限制频率（每分钟1次，每天10次）
- 恢复码使用后立即失效

#### 6.3 会话管理
- 2FA验证通过后刷新会话
- 记录使用的验证方式
- 支持要求关键操作重新验证2FA

### 7. 性能考虑

#### 7.1 TOTP验证
- 使用高效的TOTP库（如Apache Commons Codec）
- 验证时间窗口：±1个时间步（30秒）

#### 7.2 短信发送
- 使用消息队列异步发送
- 缓存验证码减少数据库查询
- 设置合理的重试机制

#### 7.3 数据库优化
- 为`user_id`添加唯一索引
- 为`enabled_at`添加索引（用于统计）
- 为`sent_at`添加索引（清理过期日志）

## Alternatives Considered

### 方案A：仅实现TOTP
**优点**：
- 实现简单，无需外部服务
- 无额外成本
- 标准化协议

**缺点**：
- 需要智能手机和TOTP应用
- 部分用户不熟悉

**结论**：不采用，因为需要支持短信验证码覆盖更广泛的用户群体

### 方案B：仅实现短信验证码
**优点**：
- 用户熟悉度高
- 无需额外应用

**缺点**：
- 产生短信费用
- 依赖短信服务可用性
- 存在SIM卡劫持风险

**结论**：不采用，需要提供TOTP作为低成本方案

### 方案C：同时实现TOTP和短信（已采纳）
**优点**：
- 用户可自由选择
- 满足不同场景需求
- 提供备用恢复码增强可用性

**缺点**：
- 实现复杂度较高
- 需要短信服务配置

**结论**：采用，平衡安全性和可用性

## Implementation Plan

参见 `tasks.md` 文件，包含详细的任务分解。

## Testing Strategy

### 单元测试
- TOTP生成和验证逻辑
- 短信验证码生成和验证逻辑
- 恢复码生成和验证逻辑
- 加密解密功能

### 集成测试
- 完整的2FA启用流程
- 2FA登录流程
- 各种错误场景（验证码错误、超时等）

### 端到端测试
- 用户注册并启用2FA
- 使用2FA登录
- 使用恢复码登录
- 禁用2FA

### 安全测试
- 暴力破解防护
- 密钥加密存储验证
- 会话管理验证

## Migration Path

### 阶段1：功能开发（2周）
- 实现TOTP和短信验证码功能
- 开发管理界面
- 编写测试

### 阶段2：测试和优化（1周）
- 全面测试
- 性能优化
- 文档编写

### 阶段3：灰度发布（2周）
- 对10%用户启用2FA（可选）
- 收集反馈
- 修复问题

### 阶段4：全面推广（持续）
- 全量推广
- 对管理员和高权限用户强制要求2FA
- 对普通用户推荐启用2FA

## Dependencies

### 内部依赖
- `nop-auth`: 认证核心模块
- `nop-sys`: 用户管理模块
- `nop-integration`: 短信服务集成
- `nop-service-framework`: BizModel和CrudBizModel
- `nop-orm`: ORM引擎
- `nop-biz`: 业务模型框架

### 外部依赖
- TOTP库：Apache Commons Codec或类似库
- 短信服务：阿里云SMS、腾讯云SMS等
- 加密工具：Nop平台内置加密工具

## Risks and Mitigations

### 风险1：用户接受度低
**描述**：用户可能不愿意使用2FA，认为太麻烦

**缓解措施**：
- 提供清晰的引导教程
- 对管理员和高权限用户强制要求
- 对普通用户只推荐不强制
- 提供多种方式降低使用门槛

### 风险2：短信服务不稳定
**描述**：短信服务可能出现延迟或故障

**缓解措施**：
- 支持多短信服务商切换
- 提供TOTP作为备用方案
- 监控短信发送成功率
- 设置合理的重试和降级策略

### 风险3：2FA设备丢失
**描述**：用户丢失手机或无法访问2FA设备

**缓解措施**：
- 提供备用恢复码
- 支持管理员重置2FA
- 提供身份验证流程（邮箱验证+人工审核）

## Success Metrics

### 功能指标
- TOTP绑定成功率 > 95%
- 短信验证码发送成功率 > 99%
- 2FA登录成功率 > 98%

### 安全指标
- 账户被盗用事件减少 > 80%
- 暴力破解攻击拦截率 = 100%

### 用户体验指标
- 用户满意度 > 80%
- 2FA启用率（高权限用户）> 90%
- 平均完成启用时间 < 3分钟

## Documentation Requirements

- 用户手册：如何启用和使用2FA
- 管理员指南：如何配置和管理2FA
- GraphQL API文档：2FA相关API接口
- 安全白皮书：2FA的安全机制说明

## Rollback Plan

如果发现严重问题需要回滚：

1. 通过配置禁用2FA功能（`nop.auth.2fa.enabled=false`）
2. 已启用2FA的用户可临时关闭验证
3. 保留数据库表结构，清空2FA配置数据
4. 等待问题修复后重新启用

## Open Questions

1. 是否需要支持硬件安全密钥（YubiKey）？
   - **决定**：暂不支持，作为未来增强项

2. 2FA验证码的有效期应该设置多久？
   - **决定**：TOTP码30秒（标准），短信码5分钟

3. 备用恢复码的数量应该是多少？
   - **决定**：默认10个，可配置

4. 是否需要对特定操作强制重新验证2FA？
   - **决定**：可选功能，通过配置控制

5. 如何处理已有用户的2FA启用？
   - **决定**：不强制，推荐启用，高权限用户强制

## Approval

- [ ] 技术负责人审批
- [ ] 产品负责人审批
- [ ] 安全负责人审批
- [ ] 架构负责人审批

**审批状态**: 待审批

**审批日期**: _____
