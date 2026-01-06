# Technical Design: Add Two-Factor Authentication (2FA)

## Architecture Overview

### 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                         Frontend                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────────┐  │
│  │ Login UI │  │ 2FA UI   │  │ Admin Dashboard         │  │
│  └────┬─────┘  └────┬─────┘  └────────────┬─────────────┘  │
└───────┼─────────────┼──────────────────────┼────────────────┘
        │             │                      │
        ▼             ▼                      ▼
┌─────────────────────────────────────────────────────────────┐
│                      REST API / GraphQL                     │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  TwoFactorAuthController                              │ │
│  │  - enableTotp()                                       │ │
│  │  - enableSms()                                        │ │
│  │  - verifyCode()                                       │ │
│  │  - disable()                                          │ │
│  └─────────────────────┬────────────────────────────────┘ │
└────────────────────────┼───────────────────────────────────┘
                         │
┌────────────────────────┼───────────────────────────────────┐
│                        Business Layer                       │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  TwoFactorAuthService                                │ │
│  │  - manage 2FA configuration                          │ │
│  │  - verify 2FA codes                                   │ │
│  │  - handle login flow                                  │ │
│  └──────────┬─────────────────────────┬─────────────────┘ │
│             │                         │                   │
│             ▼                         ▼                   │
│  ┌─────────────────────┐  ┌──────────────────────┐       │
│  │  TotpService        │  │  SmsCodeService       │       │
│  │  - generate secret  │  │  - send code          │       │
│  │  - verify code      │  │  - verify code        │       │
│  └─────────────────────┘  └──────────┬───────────┘       │
│                                     │                      │
│  ┌───────────────────────────────────┴───────────┐       │
│  │  RecoveryCodeService                          │       │
│  │  - generate codes                             │       │
│  │  - verify code                                │       │
│  └───────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────┘
                          │
┌─────────────────────────┼───────────────────────────────────┐
│                      Data Layer                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  TwoFactorConfigDao                                 │ │
│  │  - findByUserId()                                    │ │
│  │  - save()                                             │ │
│  │  - delete()                                           │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  SmsLogDao                                           │ │
│  │  - saveLog()                                          │ │
│  │  - findLatest()                                       │ │
│  │  - cleanExpired()                                     │ │
│  └──────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                          │
┌─────────────────────────┼───────────────────────────────────┐
│                   External Services                        │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  SmsService (Interface)                              │ │
│  │  - AliyunSmsService                                  │ │
│  │  - TencentSmsService                                 │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  Redis (Optional)                                    │ │
│  │  - Cache verification codes                          │ │
│  │  - Store rate limiting counters                      │ │
│  └──────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## Database Schema Design

### 1. t_auth_2fa_config表

```sql
CREATE TABLE t_auth_2fa_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    two_factor_method VARCHAR(20) NOT NULL COMMENT '2FA方式: totp/sms/none',
    totp_secret VARCHAR(255) COMMENT 'TOTP密钥(加密存储)',
    recovery_codes TEXT COMMENT '恢复码(JSON加密)',
    phone_number VARCHAR(20) COMMENT '手机号码',
    enabled_at DATETIME COMMENT '启用时间',
    last_used_at DATETIME COMMENT '最后使用时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version INTEGER NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    UNIQUE KEY uk_user_id (user_id),
    INDEX idx_enabled_at (enabled_at),
    INDEX idx_last_used_at (last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户2FA配置表';
```

### 2. t_auth_sms_log表

```sql
CREATE TABLE t_auth_sms_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    phone_number VARCHAR(20) NOT NULL COMMENT '手机号',
    code VARCHAR(100) NOT NULL COMMENT '验证码(加密)',
    purpose VARCHAR(20) NOT NULL COMMENT '用途: login/bind',
    sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    verified_at DATETIME COMMENT '验证时间',
    expired_at DATETIME NOT NULL COMMENT '过期时间',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_phone_number (phone_number),
    INDEX idx_sent_at (sent_at),
    INDEX idx_expired_at (expired_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信发送日志表';
```

### 3. t_sys_user表扩展

```sql
ALTER TABLE t_sys_user
ADD COLUMN two_factor_enabled TINYINT(1) DEFAULT 0 COMMENT '是否启用2FA' AFTER status,
ADD INDEX idx_two_factor_enabled (two_factor_enabled);
```

## Key Components Design

### 1. TOTP Service

#### 类设计

```java
public interface ITotpService {
    String generateSecret();
    String generateQrCodeUrl(String secret, String issuer, String username);
    boolean verifyCode(String secret, String code);
}

@Service
public class TotpService implements ITotpService {

    @Value("${nop.auth.2fa.totp.window-size:1}")
    private int timeWindowSize; // 时间窗口大小

    @Override
    public String generateSecret() {
        // 使用Base32生成密钥
        Key key = new SecretKeySpec(new byte[20], "HmacSHA1");
        return Base32.random().encodeToString(key.getEncoded());
    }

    @Override
    public String generateQrCodeUrl(String secret, String issuer, String username) {
        // otpauth://totp/Issuer:Username?secret=SECRET&issuer=Issuer
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
            issuer, username, secret, issuer);
    }

    @Override
    public boolean verifyCode(String secret, String code) {
        long time = System.currentTimeMillis() / 30000; // 30秒步长
        for (int i = -timeWindowSize; i <= timeWindowSize; i++) {
            String expected = generateTotp(secret, time + i);
            if (expected.equals(code)) {
                return true;
            }
        }
        return false;
    }

    private String generateTotp(String secret, long time) {
        // TOTP算法实现
        // ...
    }
}
```

#### 配置

```yaml
nop:
  auth:
    2fa:
      totp:
        window-size: 1  # 验证时间窗口（±1步）
        digits: 6       # 验证码位数
        period: 30      # 时间步长（秒）
        algorithm: HmacSHA1
```

### 2. SMS Service

#### 接口设计

```java
public interface ISmsService {
    void sendCode(String phoneNumber, String code, String purpose);
    void sendMessage(String phoneNumber, String template, Map<String, Object> params);
}

@Service("aliyunSmsService")
public class AliyunSmsService implements ISmsService {

    @Value("${nop.auth.sms.aliyun.access-key-id}")
    private String accessKeyId;

    @Value("${nop.auth.sms.aliyun.access-key-secret}")
    private String accessKeySecret;

    @Value("${nop.auth.sms.aliyun.sign-name}")
    private String signName;

    @Value("${nop.auth.sms.aliyun.template-code}")
    private String templateCode;

    @Override
    public void sendCode(String phoneNumber, String code, String purpose) {
        // 调用阿里云SMS API发送验证码
        // ...
    }

    @Override
    public void sendMessage(String phoneNumber, String template, Map<String, Object> params) {
        // 发送自定义模板短信
        // ...
    }
}
```

#### 配置

```yaml
nop:
  auth:
    2fa:
      sms:
        provider: aliyun  # aliyun/tencent
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
```

### 3. Two-Factor Auth Service

#### 核心方法

```java
@Service
public class TwoFactorAuthService {

    @Autowired
    private ITwoFactorConfigDao twoFactorConfigDao;

    @Autowired
    private ITotpService totpService;

    @Autowired
    private ISmsCodeService smsCodeService;

    @Autowired
    private IRecoveryCodeService recoveryCodeService;

    /**
     * 启用TOTP
     */
    public EnableTotpResult enableTotp(Long userId) {
        // 1. 生成TOTP密钥
        String secret = totpService.generateSecret();

        // 2. 生成二维码URL
        String qrCodeUrl = totpService.generateQrCodeUrl(secret, "NopPlatform", getUserEmail(userId));

        // 3. 保存临时配置（待用户验证）
        TwoFactorConfig config = new TwoFactorConfig();
        config.setUserId(userId);
        config.setTwoFactorMethod("totp");
        config.setTotpSecret(encryptSecret(secret));
        config.setEnabled(false);
        twoFactorConfigDao.save(config);

        return new EnableTotpResult(secret, qrCodeUrl);
    }

    /**
     * 验证TOTP码并完成绑定
     */
    public void verifyAndEnableTotp(Long userId, String code) {
        // 1. 获取配置
        TwoFactorConfig config = twoFactorConfigDao.findByUserId(userId);
        if (config == null) {
            throw new NopException(ERR_AUTH_2FA_CONFIG_NOT_FOUND);
        }

        // 2. 验证TOTP码
        String secret = decryptSecret(config.getTotpSecret());
        if (!totpService.verifyCode(secret, code)) {
            throw new NopException(ERR_AUTH_2FA_INVALID_CODE);
        }

        // 3. 生成恢复码
        List<String> recoveryCodes = recoveryCodeService.generateCodes(userId);

        // 4. 启用2FA
        config.setEnabled(true);
        config.setEnabledAt(new Date());
        twoFactorConfigDao.save(config);

        // 5. 更新用户状态
        userDao.updateTwoFactorEnabled(userId, true);

        // 6. 记录审计日志
        auditService.log(userId, "2FA_TOTP_ENABLED", "TOTP two-factor authentication enabled");
    }

    /**
     * 验证2FA码
     */
    public boolean verifyCode(Long userId, String code, String method) {
        TwoFactorConfig config = twoFactorConfigDao.findByUserId(userId);
        if (config == null || !config.getEnabled()) {
            return false;
        }

        switch (method) {
            case "totp":
                String secret = decryptSecret(config.getTotpSecret());
                return totpService.verifyCode(secret, code);

            case "sms":
                return smsCodeService.verifyCode(userId, code);

            case "recovery":
                return recoveryCodeService.verifyCode(userId, code);

            default:
                return false;
        }
    }

    /**
     * 禁用2FA
     */
    public void disable(Long userId) {
        TwoFactorConfig config = twoFactorConfigDao.findByUserId(userId);
        if (config != null) {
            twoFactorConfigDao.delete(config);
        }

        userDao.updateTwoFactorEnabled(userId, false);

        auditService.log(userId, "2FA_DISABLED", "Two-factor authentication disabled");
    }
}
```

### 4. Login Flow Enhancement

#### 流程图

```
┌──────────┐
│  用户    │
└────┬─────┘
     │
     │ 1. 提交用户名/密码
     ▼
┌─────────────────┐
│ AuthService    │
│ - login()      │
└────┬───────────┘
     │
     │ 2. 验证用户名/密码
     │    失败 → 返回错误
     ▼
┌─────────────────┐
│ 检查是否启用2FA│
└────┬───────────┘
     │
     ├─ 否 → 3. 生成JWT令牌 → 返回
     │
     └─ 是 → 4. 生成临时令牌 → 返回
                │
                │ 5. 提交2FA验证码
                ▼
         ┌─────────────────┐
         │ verify2fa()    │
         └────┬───────────┘
              │
              │ 6. 验证2FA码
              │    失败 → 返回错误
              ▼
         ┌─────────────────┐
         │ 验证临时令牌    │
         └────┬───────────┘
              │
              │ 7. 生成JWT令牌
              ▼
         ┌─────────────────┐
         │ 返回JWT令牌     │
         └─────────────────┘
```

#### API设计

**第一步登录**

```
POST /api/auth/login
Content-Type: application/json

{
  "username": "user@example.com",
  "password": "password123"
}

Response (未启用2FA):
{
  "token": "jwt-token...",
  "expiresIn": 7200
}

Response (已启用2FA):
{
  "tempToken": "temp-token...",
  "requires2FA": true,
  "availableMethods": ["totp", "sms", "recovery"]
}
```

**第二步2FA验证**

```
POST /api/auth/login/2fa
Content-Type: application/json

{
  "tempToken": "temp-token...",
  "code": "123456",
  "method": "totp"
}

Response:
{
  "token": "jwt-token...",
  "expiresIn": 7200,
  "2faVerified": true
}
```

## Security Considerations

### 1. 加密策略

#### TOTP密钥加密
```java
public class TotpSecretEncryption {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int KEY_SIZE = 256;

    @Value("${nop.auth.encryption.key}")
    private String encryptionKey;

    public String encrypt(String plaintext) {
        // 使用AES-256加密
        Key key = generateKey(encryptionKey);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public String decrypt(String ciphertext) {
        // 使用AES-256解密
        // ...
    }
}
```

#### 恢复码加密
```java
public class RecoveryCodeEncryption {
    // 使用bcrypt加密每个恢复码
    public String encrypt(String code) {
        return BCrypt.hashpw(code, BCrypt.gensalt(12));
    }

    public boolean verify(String code, String hashed) {
        return BCrypt.checkpw(code, hashed);
    }
}
```

### 2. Rate Limiting

#### 短信发送频率限制
```java
@Service
public class SmsRateLimiter {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public boolean checkRateLimit(String phoneNumber) {
        String key = "sms:rate:" + phoneNumber;

        // 1分钟内只能发送1次
        Long count1Min = redisTemplate.opsForValue().increment(key);
        if (count1Min == 1) {
            redisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }
        if (count1Min > 1) {
            return false;
        }

        // 1天内只能发送10次
        String dailyKey = "sms:daily:" + phoneNumber;
        Long countDaily = redisTemplate.opsForValue().increment(dailyKey);
        if (countDaily == 1) {
            redisTemplate.expire(dailyKey, 24, TimeUnit.HOURS);
        }
        if (countDaily > 10) {
            return false;
        }

        return true;
    }
}
```

### 3. 暴力破解防护

#### 验证码失败计数器
```java
@Service
public class BruteForceProtection {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void recordFailedAttempt(Long userId) {
        String key = "2fa:failed:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, 30, TimeUnit.MINUTES);
        }

        // 5次失败后锁定
        if (count >= 5) {
            lock2fa(userId, 30);
        }
    }

    public void resetFailedAttempts(Long userId) {
        String key = "2fa:failed:" + userId;
        redisTemplate.delete(key);
    }

    public boolean isLocked(Long userId) {
        String key = "2fa:locked:" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private void lock2fa(Long userId, int minutes) {
        String key = "2fa:locked:" + userId;
        redisTemplate.opsForValue().set(key, "true", minutes, TimeUnit.MINUTES);
    }
}
```

## Performance Optimization

### 1. 缓存策略

#### Redis缓存验证码
```java
@Service
public class SmsCodeService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String CODE_PREFIX = "sms:code:";
    private static final int CODE_TTL = 300; // 5分钟

    public void saveCode(Long userId, String code) {
        String key = CODE_PREFIX + userId;
        redisTemplate.opsForValue().set(key, encryptCode(code), CODE_TTL, TimeUnit.SECONDS);
    }

    public String getCode(Long userId) {
        String key = CODE_PREFIX + userId;
        String encryptedCode = redisTemplate.opsForValue().get(key);
        if (encryptedCode != null) {
            return decryptCode(encryptedCode);
        }
        return null;
    }
}
```

### 2. 异步处理

#### 短信发送异步化
```java
@Service
public class AsyncSmsSender {

    @Autowired
    private ISmsService smsService;

    @Async("smsExecutor")
    public void sendSmsAsync(String phoneNumber, String code, String purpose) {
        try {
            smsService.sendCode(phoneNumber, code, purpose);
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", phoneNumber, e.getMessage());
            // 记录失败，可选择重试
        }
    }
}
```

### 3. 数据库优化

#### 索引优化
```sql
-- 用户ID唯一索引
CREATE UNIQUE INDEX uk_user_id ON t_auth_2fa_config(user_id);

-- 启用时间索引（用于统计）
CREATE INDEX idx_enabled_at ON t_auth_2fa_config(enabled_at);

-- 最后使用时间索引（用于清理过期会话）
CREATE INDEX idx_last_used_at ON t_auth_2fa_config(last_used_at);

-- 手机号索引（用于短信发送日志）
CREATE INDEX idx_phone_number ON t_auth_sms_log(phone_number);

-- 发送时间索引（用于清理过期日志）
CREATE INDEX idx_sent_at ON t_auth_sms_log(sent_at);

-- 过期时间索引（用于清理）
CREATE INDEX idx_expired_at ON t_auth_sms_log(expired_at);
```

## Monitoring and Logging

### 1. Metrics

```java
@Service
public class TwoFactorAuthMetrics {

    private final MeterRegistry meterRegistry;

    public void recordTotpVerification(boolean success) {
        meterRegistry.counter("2fa.totp.verification",
            "success", String.valueOf(success)).increment();
    }

    public void recordSmsSent(String phoneNumber) {
        meterRegistry.counter("2fa.sms.sent").increment();
    }

    public void recordSmsVerification(boolean success) {
        meterRegistry.counter("2fa.sms.verification",
            "success", String.valueOf(success)).increment();
    }
}
```

### 2. Audit Logging

```java
@Service
public class TwoFactorAuthAudit {

    @Autowired
    private AuditLogService auditLogService;

    public void log2faEnabled(Long userId, String method) {
        auditLogService.log(AuditEvent.builder()
            .userId(userId)
            .eventType("2FA_ENABLED")
            .description(String.format("2FA enabled using method: %s", method))
            .build());
    }

    public void log2faDisabled(Long userId) {
        auditLogService.log(AuditEvent.builder()
            .userId(userId)
            .eventType("2FA_DISABLED")
            .description("2FA disabled")
            .build());
    }

    public void log2faVerification(Long userId, String method, boolean success) {
        auditLogService.log(AuditEvent.builder()
            .userId(userId)
            .eventType("2FA_VERIFICATION")
            .description(String.format("2FA verification attempt: %s, success: %s",
                method, success))
            .build());
    }
}
```

## Testing Strategy

### 1. Unit Tests

```java
@SpringBootTest
class TotpServiceTest {

    @Autowired
    private ITotpService totpService;

    @Test
    void testGenerateSecret() {
        String secret = totpService.generateSecret();
        assertNotNull(secret);
        assertTrue(secret.length() >= 16);
    }

    @Test
    void testVerifyCode() {
        String secret = totpService.generateSecret();
        String code = generateTotpCode(secret); // 生成正确的TOTP码

        assertTrue(totpService.verifyCode(secret, code));
        assertFalse(totpService.verifyCode(secret, "000000"));
    }
}
```

### 2. Integration Tests

```java
@SpringBootTest
@AutoConfigureMockMvc
class TwoFactorAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testEnableTotpFlow() throws Exception {
        // 1. 启用TOTP
        MvcResult result = mockMvc.perform(post("/api/auth/2fa/totp/enable")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

        String response = result.getResponse().getContentAsString();
        JSONObject json = new JSONObject(response);
        String secret = json.getString("secret");

        // 2. 验证TOTP码
        String code = generateTotpCode(secret);
        mockMvc.perform(post("/api/auth/2fa/totp/verify")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + code + "\"}"))
            .andExpect(status().isOk());
    }
}
```

## Deployment Considerations

### 1. Configuration Management

```yaml
# application-dev.yaml
nop:
  auth:
    2fa:
      enabled: true
      methods: totp,sms
      totp:
        window-size: 1
      sms:
        provider: aliyun
        aliyun:
          access-key-id: ${ALIYUN_ACCESS_KEY_ID_DEV}
          access-key-secret: ${ALIYUN_ACCESS_KEY_SECRET_DEV}
```

```yaml
# application-prod.yaml
nop:
  auth:
    2fa:
      enabled: true
      methods: totp,sms
      totp:
        window-size: 1
      sms:
        provider: aliyun
        aliyun:
          access-key-id: ${ALIYUN_ACCESS_KEY_ID_PROD}
          access-key-secret: ${ALIYUN_ACCESS_KEY_SECRET_PROD}
```

### 2. Database Migration

```sql
-- V2.0.1__add_2fa_support.sql
-- See Database Schema Design section above

-- Rollback script
DROP TABLE IF EXISTS t_auth_2fa_config;
DROP TABLE IF EXISTS t_auth_sms_log;
ALTER TABLE t_sys_user DROP COLUMN two_factor_enabled;
```

### 3. Feature Flags

```java
@Component
public class TwoFactorAuthFeature {

    @Value("${nop.auth.2fa.enabled:false}")
    private boolean enabled;

    @Value("${nop.auth.2fa.methods:totp}")
    private List<String> availableMethods;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isMethodAvailable(String method) {
        return availableMethods.contains(method);
    }
}
```

## Conclusion

本设计文档详细描述了2FA功能的实现方案，包括：

1. 系统架构和组件设计
2. 数据库schema设计
3. 核心服务实现
4. 登录流程增强
5. 安全考虑
6. 性能优化
7. 监控和日志
8. 测试策略
9. 部署考虑

该设计遵循Nop平台的设计原则：
- 模块化：各组件职责清晰
- 可扩展：支持多种2FA方式
- 安全优先：加密存储、防暴力破解
- 性能优化：缓存、异步处理
- 可观测性：完整的监控和审计日志

按照此设计实施，可以安全、高效地为Nop平台添加2FA功能。
