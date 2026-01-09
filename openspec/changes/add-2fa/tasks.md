# Implementation Tasks: Add Two-Factor Authentication (2FA)

## 1. 数据库设计和迁移

### 1.1 设计数据库表结构
- [ ] 1.1.1 设计`nop_auth_2fa_config`表结构
- [ ] 1.1.2 设计`nop_auth_sms_log`表结构
- [ ] 1.1.3 设计`nop_auth_user`表的扩展字段
- [ ] 1.1.4 确定索引策略
- [ ] 1.1.5 编写DDL脚本

### 1.2 创建数据库迁移脚本
- [ ] 1.2.1 创建V2.0.1__add_2fa_tables.sql
- [ ] 1.2.2 测试迁移脚本在MySQL上的执行
- [ ] 1.2.3 测试迁移脚本在PostgreSQL上的执行
- [ ] 1.2.4 编写回滚脚本

### 1.3 创建实体类
- [ ] 1.3.1 创建`NopAuth2faConfig`实体类（继承OrmEntity）
- [ ] 1.3.2 创建`NopAuthSmsLog`实体类（继承OrmEntity）
- [ ] 1.3.3 扩展`NopAuthUser`实体类添加2FA字段
- [ ] 1.3.4 添加PROP_NAME常量定义

## 2. 核心服务接口定义

### 2.1 TOTP服务接口
- [ ] 2.1.1 定义`ITotpService`接口
- [ ] 2.1.2 定义`generateSecret()`方法
- [ ] 2.1.3 定义`generateQrCodeUrl()`方法
- [ ] 2.1.4 定义`verifyCode()`方法

### 2.2 短信验证码服务接口
- [ ] 2.2.1 定义`ISmsCodeService`接口
- [ ] 2.2.2 定义`sendCode()`方法
- [ ] 2.2.3 定义`verifyCode()`方法
- [ ] 2.2.4 定义`checkRateLimit()`方法

### 2.3 恢复码服务接口
- [ ] 2.3.1 定义`IRecoveryCodeService`接口
- [ ] 2.3.2 定义`generateCodes()`方法
- [ ] 2.3.3 定义`verifyCode()`方法
- [ ] 2.3.4 定义`getAvailableCodes()`方法
- [ ] 2.3.5 定义`markCodeUsed()`方法

## 3. 核心服务实现

### 3.1 TOTP功能实现
- [ ] 3.1.1 添加TOTP库依赖（Apache Commons Codec）
- [ ] 3.1.2 实现`TotpServiceImpl.generateSecret()`方法
- [ ] 3.1.3 实现`TotpServiceImpl.generateQrCodeUrl()`方法
- [ ] 3.1.4 实现`TotpServiceImpl.verifyCode()`方法
- [ ] 3.1.5 编写TOTP单元测试

### 3.2 短信验证码功能实现
- [ ] 3.2.1 创建短信服务接口`ISmsService`
- [ ] 3.2.2 实现阿里云短信服务`AliyunSmsServiceImpl`
- [ ] 3.2.3 实现腾讯云短信服务`TencentSmsServiceImpl`
- [ ] 3.2.4 实现`SmsCodeServiceImpl.sendCode()`方法
- [ ] 3.2.5 实现`SmsCodeServiceImpl.verifyCode()`方法
- [ ] 3.2.6 实现发送频率限制（使用Redis或内存缓存）
- [ ] 3.2.7 编写短信服务单元测试

### 3.3 恢复码功能实现
- [ ] 3.3.1 实现`RecoveryCodeServiceImpl.generateCodes()`方法
- [ ] 3.3.2 实现`RecoveryCodeServiceImpl.verifyCode()`方法
- [ ] 3.3.3 实现`RecoveryCodeServiceImpl.markCodeUsed()`方法
- [ ] 3.3.4 实现恢复码JSON序列化和反序列化
- [ ] 3.3.5 编写恢复码单元测试

### 3.4 加密工具使用
- [ ] 3.4.1 验证Nop平台AES加密功能可用性
- [ ] 3.4.2 实现TOTP密钥加密/解密
- [ ] 3.4.3 实现恢复码加密/解密
- [ ] 3.4.4 实现短信验证码加密/解密
- [ ] 3.4.5 编写加密工具单元测试

## 4. BizModel实现

### 4.1 NopAuth2faConfigBizModel创建
- [ ] 4.1.1 创建`NopAuth2faConfigBizModel`类（继承CrudBizModel）
- [ ] 4.1.2 注入服务依赖（ITotpService, ISmsCodeService, IRecoveryCodeService）
- [ ] 4.1.3 实现构造函数设置实体名称

### 4.2 TOTP管理GraphQL Mutation
- [ ] 4.2.1 实现`enableTotp(@BizMutation)`方法
- [ ] 4.2.2 实现`verifyAndEnableTotp(@BizMutation)`方法
- [ ] 4.2.3 添加必要的验证逻辑
- [ ] 4.2.4 添加事务管理（@Transactional）

### 4.3 短信验证码管理GraphQL Mutation
- [ ] 4.3.1 实现`enableSms(@BizMutation)`方法
- [ ] 4.3.2 实现`verifyAndEnableSms(@BizMutation)`方法
- [ ] 4.3.3 添加必要的验证逻辑
- [ ] 4.3.4 添加事务管理（@Transactional）

### 4.4 恢复码管理GraphQL Mutation和Query
- [ ] 4.4.1 实现`getRecoveryCodes(@BizQuery)`方法
- [ ] 4.4.2 实现`regenerateRecoveryCodes(@BizMutation)`方法
- [ ] 4.4.3 添加必要的验证逻辑
- [ ] 4.4.4 添加事务管理（@Transactional）

### 4.5 通用管理GraphQL Mutation
- [ ] 4.5.1 实现`disable2fa(@BizMutation)`方法
- [ ] 4.5.2 添加必要的验证逻辑
- [ ] 4.5.3 添加事务管理（@Transactional）

### 4.6 BizModel单元测试
- [ ] 4.6.1 编写NopAuth2faConfigBizModel单元测试
- [ ] 4.6.2 测试所有GraphQL方法
- [ ] 4.6.3 测试异常场景

## 5. 登录流程增强

### 5.1 NopAuthUserBizModel扩展
- [ ] 5.1.1 注入2FA相关服务依赖
- [ ] 5.1.2 实现`login(@BizMutation)`方法（第一步）
- [ ] 5.1.3 实现`verify2fa(@BizMutation)`方法（第二步）
- [ ] 5.1.4 实现临时令牌生成和验证逻辑

### 5.2 2FA验证逻辑实现
- [ ] 5.2.1 实现`verifyTotp()`私有方法
- [ ] 5.2.2 实现`verifySmsCode()`私有方法
- [ ] 5.2.3 实现`verifyRecoveryCode()`私有方法
- [ ] 5.2.4 实现`generateTempToken()`私有方法
- [ ] 5.2.5 实现`validateTempToken()`私有方法

### 5.3 登录流程测试
- [ ] 5.3.1 编写未启用2FA登录测试
- [ ] 5.3.2 编写启用TOTP的登录测试
- [ ] 5.3.3 编写启用短信的登录测试
- [ ] 5.3.4 编写使用恢复码的登录测试
- [ ] 5.3.5 编写各种错误场景测试

## 6. 错误码定义

### 6.1 AuthErrors接口扩展
- [ ] 6.1.1 定义2FA配置相关错误码
- [ ] 6.1.2 定义TOTP验证错误码
- [ ] 6.1.3 定义短信验证错误码
- [ ] 6.1.4 定义恢复码错误码
- [ ] 6.1.5 定义登录流程错误码

## 7. 配置管理

### 7.1 配置文件定义
- [ ] 7.1.1 在application.yaml中添加2FA配置项
- [ ] 7.1.2 添加TOTP配置（窗口大小等）
- [ ] 7.1.3 添加短信服务配置
- [ ] 7.1.4 添加验证码有效期配置
- [ ] 7.1.5 添加频率限制配置

### 7.2 配置类定义
- [ ] 7.2.1 创建`NopAuthConfigs`接口
- [ ] 7.2.2 使用`IConfigReference`定义配置项
- [ ] 7.2.3 添加`@Description`注解
- [ ] 7.2.4 添加`@Locale("zh-CN")`注解
- [ ] 7.2.5 编写配置单元测试

### 7.3 配置使用
- [ ] 7.3.1 在服务中注入配置引用
- [ ] 7.3.2 使用配置值替代硬编码
- [ ] 7.3.3 测试配置热更新（如需要）

## 8. 安全增强

### 8.1 防暴力破解
- [ ] 8.1.1 实现验证码失败计数器
- [ ] 8.1.2 实现临时锁定机制
- [ ] 8.1.3 实现IP地址级别的频率限制（可选）
- [ ] 8.1.4 记录安全事件到审计日志

### 8.2 会话管理
- [ ] 8.2.1 2FA验证后刷新会话
- [ ] 8.2.2 记录使用的验证方式
- [ ] 8.2.3 实现关键操作重新验证2FA（可选）

## 9. GraphQL Schema定义

### 9.1 Type定义
- [ ] 9.1.1 定义`NopAuth2faConfig` GraphQL类型
- [ ] 9.1.2 定义`NopAuthSmsLog` GraphQL类型
- [ ] 9.1.3 定义`EnableTotpResult` GraphQL类型
- [ ] 9.1.4 定义`LoginResult` GraphQL类型
- [ ] 9.1.5 定义`Verify2faResult` GraphQL类型

### 9.2 Query定义
- [ ] 9.2.1 定义`get2faConfig`查询
- [ ] 9.2.2 定义`getRecoveryCodes`查询
- [ ] 9.2.3 定义`findSmsLogs`查询

### 9.3 Mutation定义
- [ ] 9.3.1 定义`enableTotp` mutation
- [ ] 9.3.2 定义`verifyAndEnableTotp` mutation
- [ ] 9.3.3 定义`enableSms` mutation
- [ ] 9.3.4 定义`verifyAndEnableSms` mutation
- [ ] 9.3.5 定义`disable2fa` mutation
- [ ] 9.3.6 定义`regenerateRecoveryCodes` mutation
- [ ] 9.3.7 定义`login` mutation
- [ ] 9.3.8 定义`verify2fa` mutation

## 10. 测试

### 10.1 单元测试
- [ ] 10.1.1 TOTP服务单元测试（覆盖率>90%）
- [ ] 10.1.2 短信服务单元测试（覆盖率>90%）
- [ ] 10.1.3 恢复码服务单元测试（覆盖率>90%）
- [ ] 10.1.4 加密工具单元测试（覆盖率>90%）
- [ ] 10.1.5 BizModel单元测试（覆盖率>80%）

### 10.2 集成测试
- [ ] 10.2.1 TOTP完整流程测试
- [ ] 10.2.2 短信验证码完整流程测试
- [ ] 10.2.3 恢复码使用测试
- [ ] 10.2.4 2FA登录完整流程测试
- [ ] 10.2.5 各种错误场景测试
- [ ] 10.2.6 性能测试（响应时间<500ms）

### 10.3 E2E测试
- [ ] 10.3.1 用户注册并启用2FA流程
- [ ] 10.3.2 使用2FA登录流程
- [ ] 10.3.3 使用恢复码登录流程
- [ ] 10.3.4 禁用2FA流程
- [ ] 10.3.5 管理员重置用户2FA流程（如支持）

### 10.4 安全测试
- [ ] 10.4.1 暴力破解防护测试
- [ ] 10.4.2 密钥加密验证测试
- [ ] 10.4.3 会话管理安全测试
- [ ] 10.4.4 GraphQL注入防护测试
- [ ] 10.4.5 SQL注入防护测试

## 11. 文档

### 11.1 用户文档
- [ ] 11.1.1 编写用户2FA启用指南
- [ ] 11.1.2 编写2FA登录教程
- [ ] 11.1.3 编写恢复码使用说明
- [ ] 11.1.4 编写常见问题解答

### 11.2 管理员文档
- [ ] 11.2.1 编写2FA配置指南
- [ ] 11.2.2 编写短信服务配置教程
- [ ] 11.2.3 编写用户2FA管理指南
- [ ] 11.2.4 编写2FA安全策略建议

### 11.3 开发文档
- [ ] 11.3.1 编写GraphQL API文档
- [ ] 11.3.2 编写架构设计文档
- [ ] 11.3.3 编写安全机制说明
- [ ] 11.3.4 编写代码示例

## 12. 部署和运维

### 12.1 部署脚本
- [ ] 12.1.1 编写数据库迁移部署脚本
- [ ] 12.1.2 编写配置模板
- [ ] 12.1.3 编写Docker镜像构建脚本
- [ ] 12.1.4 编写Helm Charts（Kubernetes部署，如适用）

### 12.2 监控和告警
- [ ] 12.2.1 添加2FA相关监控指标
- [ ] 12.2.2 配置短信发送失败告警
- [ ] 12.2.3 配置验证码失败率告警
- [ ] 12.2.4 配置性能指标告警

### 12.3 数据备份和恢复
- [ ] 12.3.1 确保2FA数据包含在备份中
- [ ] 12.3.2 测试数据恢复流程
- [ ] 12.3.3 编写灾难恢复计划

## 13. 代码审查和优化

### 13.1 代码审查
- [ ] 13.1.1 完成所有代码的自查
- [ ] 13.1.2 提交代码审查请求
- [ ] 13.1.3 响应审查意见
- [ ] 13.1.4 修复审查发现的问题
- [ ] 13.1.5 通过所有代码审查

### 13.2 性能优化
- [ ] 13.2.1 分析性能瓶颈
- [ ] 13.2.2 优化数据库查询
- [ ] 13.2.3 添加必要的缓存
- [ ] 13.2.4 优化加密解密性能
- [ ] 13.2.5 进行压力测试

### 13.3 安全审查
- [ ] 13.3.1 进行安全代码审查
- [ ] 13.3.2 运行安全扫描工具
- [ ] 13.3.3 修复安全漏洞
- [ ] 13.3.4 更新安全最佳实践

## 14. 发布准备

### 14.1 质量检查
- [ ] 14.1.1 确保所有测试通过
- [ ] 14.1.2 代码覆盖率达标（>70%）
- [ ] 14.1.3 无静态代码检查警告（LSP diagnostics）
- [ ] 14.1.4 完成安全扫描
- [ ] 14.1.5 完成性能测试

### 14.2 发布检查
- [ ] 14.2.1 更新CHANGELOG.md
- [ ] 14.2.2 更新版本号（如需要）
- [ ] 14.2.3 编写发布说明
- [ ] 14.2.4 准备发布材料
- [ ] 14.2.5 制定发布计划

### 14.3 灰度发布
- [ ] 14.3.1 配置灰度发布规则
- [ ] 14.3.2 监控灰度发布指标
- [ ] 14.3.3 收集用户反馈
- [ ] 14.3.4 处理灰度期问题
- [ ] 14.3.5 准备全量发布

## 总计任务数：150+
## 预计完成时间：3-4周
## 优先级：高

---

**注意事项：**
1. 每个任务完成后请标记为`[x]`
2. 遇到阻塞问题及时更新任务状态
3. 保持任务列表与实际进度同步
4. 定期审查和更新任务优先级
5. 所有GraphQL操作必须使用@BizQuery和@BizMutation注解
6. 所有实体类必须继承OrmEntity
7. 所有DAO必须使用IOrmEntityDao<T>模式
8. 所有配置必须使用IConfigReference接口
9. 所有异常必须使用NopException和ErrorCode
10. 所有依赖注入必须使用@Inject注解
