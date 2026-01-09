# Technical Design: Add Two-Factor Authentication (2FA)

## Architecture Overview

### System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                       Frontend (AMIS)                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────────┐  │
│  │ Login UI │  │ 2FA UI   │  │ Admin Dashboard         │  │
│  └────┬─────┘  └────┬─────┘  └────────────┬─────────────┘  │
└───────┼─────────────┼──────────────────────┼────────────────┘
        │             │                      │
        ▼             ▼                      ▼
┌─────────────────────────────────────────────────────────────┐
│                      GraphQL API Layer                     │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  NopAuth2faConfigBizModel                            │ │
│  │  - enableTotp() (@BizMutation)                        │ │
│  │  - verifyAndEnableTotp() (@BizMutation)               │ │
│  │  - enableSms() (@BizMutation)                         │ │
│  │  - disable2fa() (@BizMutation)                       │ │
│  │  - getRecoveryCodes() (@BizQuery)                     │ │
│  │  - regenerateRecoveryCodes() (@BizMutation)           │ │
│  └─────────────────────┬────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  NopAuthUserBizModel (Extended)                     │ │
│  │  - loginWith2fa() (@BizMutation)                    │ │
│  │  - verify2fa() (@BizMutation)                       │ │
│  └─────────────────────┬────────────────────────────────┘ │
└────────────────────────┼───────────────────────────────────┘
                         │
┌────────────────────────┼───────────────────────────────────┐
│                      Service Layer                        │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  ITotpService (Interface)                           │ │
│  │  - generateSecret()                                  │ │
│  │  - generateQrCodeUrl()                               │ │
│  │  - verifyCode()                                      │ │
│  └──────────┬───────────────────────────────────────────┘ │
│             │                                              │
│  ┌──────────┴───────────────────────────────────────────┐ │
│  │  ISmsCodeService (Interface)                         │ │
│  │  - sendCode()                                       │ │
│  │  - verifyCode()                                     │ │
│  └──────────┬───────────────────────────────────────────┘ │
│             │                                              │
│  ┌──────────┴───────────────────────────────────────────┐ │
│  │  IRecoveryCodeService (Interface)                    │ │
│  │  - generateCodes()                                  │ │
│  │  - verifyCode()                                     │ │
│  │  - markCodeUsed()                                   │ │
│  └───────────────────────────────────────────────────────┘ │
└────────────────────────┼───────────────────────────────────┘
                         │
┌────────────────────────┼───────────────────────────────────┐
│                    Data Access Layer                      │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  IOrmEntityDao<NopAuth2faConfig>                   │ │
│  │  - getEntityById()                                  │ │
│  │  - saveEntity()                                     │ │
│  │  - deleteEntity()                                   │ │
│  └──────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  IOrmEntityDao<NopAuthSmsLog>                       │ │
│  │  - saveEntity()                                     │ │
│  │  - findLatest()                                     │ │
│  └───────────────────────────────────────────────────────┘ │
└────────────────────────┼───────────────────────────────────┘
                         │
┌────────────────────────┼───────────────────────────────────┐
│                   External Services                       │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  ISmsService (Interface)                            │ │
│  │  - AliyunSmsService                                │ │
│  │  - TencentSmsService                               │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## Database Schema Design

### 1. nop_auth_2fa_config Table

```sql
CREATE TABLE nop_auth_2fa_config (
    sid VARCHAR(32) PRIMARY KEY COMMENT '主键ID',
    user_id VARCHAR(32) NOT NULL COMMENT '用户ID',
    two_factor_method VARCHAR(20) NOT NULL COMMENT '2FA方式: totp/sms/none',
    totp_secret VARCHAR(255) COMMENT 'TOTP密钥(加密存储)',
    recovery_codes TEXT COMMENT '恢复码(JSON加密)',
    phone_number VARCHAR(20) COMMENT '手机号码',
    enabled TINYINT(1) DEFAULT 0 COMMENT '是否启用',
    enabled_at DATETIME COMMENT '启用时间',
    last_used_at DATETIME COMMENT '最后使用时间',
    version INTEGER NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_user_id (user_id),
    INDEX idx_enabled (enabled),
    INDEX idx_enabled_at (enabled_at),
    INDEX idx_last_used_at (last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户2FA配置表';
```

### 2. nop_auth_sms_log Table

```sql
CREATE TABLE nop_auth_sms_log (
    sid VARCHAR(32) PRIMARY KEY COMMENT '主键ID',
    user_id VARCHAR(32) COMMENT '用户ID',
    phone_number VARCHAR(20) NOT NULL COMMENT '手机号',
    code VARCHAR(100) NOT NULL COMMENT '验证码(加密)',
    purpose VARCHAR(20) NOT NULL COMMENT '用途: login/bind',
    sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    verified_at DATETIME COMMENT '验证时间',
    expired_at DATETIME NOT NULL COMMENT '过期时间',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    version INTEGER NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_user_id (user_id),
    INDEX idx_phone_number (phone_number),
    INDEX idx_sent_at (sent_at),
    INDEX idx_expired_at (expired_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信发送日志表';
```

### 3. nop_auth_user Table Extension

```sql
ALTER TABLE nop_auth_user
ADD COLUMN two_factor_enabled TINYINT(1) DEFAULT 0 COMMENT '是否启用2FA' AFTER status,
ADD INDEX idx_two_factor_enabled (two_factor_enabled);
```

## Entity Model Design

### 1. NopAuth2faConfig Entity

```java
package io.nop.auth.domain;

import io.nop.api.core.annotations.ioc.Bean;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.support.OrmEntity;

@Entity(table = "nop_auth_2fa_config")
@Cacheable(cacheName = "nop_auth_2fa_config")
public class NopAuth2faConfig extends OrmEntity {

    public static final String PROP_NAME_sid = "sid";
    public static final String PROP_NAME_userId = "userId";
    public static final String PROP_NAME_twoFactorMethod = "twoFactorMethod";
    public static final String PROP_NAME_totpSecret = "totpSecret";
    public static final String PROP_NAME_recoveryCodes = "recoveryCodes";
    public static final String PROP_NAME_phoneNumber = "phoneNumber";
    public static final String PROP_NAME_enabled = "enabled";
    public static final String PROP_NAME_enabledAt = "enabledAt";
    public static final String PROP_NAME_lastUsedAt = "lastUsedAt";

    private String sid;
    private String userId;
    private String twoFactorMethod;
    private String totpSecret;
    private String recoveryCodes;
    private String phoneNumber;
    private Boolean enabled;
    private Date enabledAt;
    private Date lastUsedAt;

    // Getters and Setters
    public String getSid() { return sid; }
    public void setSid(String sid) { this.sid = sid; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTwoFactorMethod() { return twoFactorMethod; }
    public void setTwoFactorMethod(String twoFactorMethod) { this.twoFactorMethod = twoFactorMethod; }

    public String getTotpSecret() { return totpSecret; }
    public void setTotpSecret(String totpSecret) { this.totpSecret = totpSecret; }

    public String getRecoveryCodes() { return recoveryCodes; }
    public void setRecoveryCodes(String recoveryCodes) { this.recoveryCodes = recoveryCodes; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Date getEnabledAt() { return enabledAt; }
    public void setEnabledAt(Date enabledAt) { this.enabledAt = enabledAt; }

    public Date getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Date lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
```

### 2. NopAuthSmsLog Entity

```java
package io.nop.auth.domain;

import io.nop.api.core.annotations.ioc.Bean;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.support.OrmEntity;

@Entity(table = "nop_auth_sms_log")
public class NopAuthSmsLog extends OrmEntity {

    public static final String PROP_NAME_sid = "sid";
    public static final String PROP_NAME_userId = "userId";
    public static final String PROP_NAME_phoneNumber = "phoneNumber";
    public static final String PROP_NAME_code = "code";
    public static final String PROP_NAME_purpose = "purpose";
    public static final String PROP_NAME_sentAt = "sentAt";
    public static final String PROP_NAME_verifiedAt = "verifiedAt";
    public static final String PROP_NAME_expiredAt = "expiredAt";
    public static final String PROP_NAME_ipAddress = "ipAddress";

    private String sid;
    private String userId;
    private String phoneNumber;
    private String code;
    private String purpose;
    private Date sentAt;
    private Date verifiedAt;
    private Date expiredAt;
    private String ipAddress;

    // Getters and Setters
    public String getSid() { return sid; }
    public void setSid(String sid) { this.sid = sid; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public Date getSentAt() { return sentAt; }
    public void setSentAt(Date sentAt) { this.sentAt = sentAt; }

    public Date getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Date verifiedAt) { this.verifiedAt = verifiedAt; }

    public Date getExpiredAt() { return expiredAt; }
    public void setExpiredAt(Date expiredAt) { this.expiredAt = expiredAt; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
```

## Service Layer Design

### 1. ITotpService Interface

```java
package io.nop.auth.service;

public interface ITotpService {
    /**
     * 生成TOTP密钥
     */
    String generateSecret();

    /**
     * 生成二维码URL
     * @param secret TOTP密钥
     * @param issuer 发行者
     * @param username 用户名
     * @return 二维码URL
     */
    String generateQrCodeUrl(String secret, String issuer, String username);

    /**
     * 验证TOTP码
     * @param secret TOTP密钥
     * @param code TOTP验证码
     * @return 是否验证成功
     */
    boolean verifyCode(String secret, String code);
}
```

### 2. ISmsCodeService Interface

```java
package io.nop.auth.service;

public interface ISmsCodeService {
    /**
     * 发送短信验证码
     * @param userId 用户ID
     * @param phoneNumber 手机号
     * @param purpose 用途(login/bind)
     */
    void sendCode(String userId, String phoneNumber, String purpose);

    /**
     * 验证短信验证码
     * @param userId 用户ID
     * @param code 验证码
     * @return 是否验证成功
     */
    boolean verifyCode(String userId, String code);

    /**
     * 检查发送频率限制
     * @param userId 用户ID
     * @param phoneNumber 手机号
     * @return 是否允许发送
     */
    boolean checkRateLimit(String userId, String phoneNumber);
}
```

### 3. IRecoveryCodeService Interface

```java
package io.nop.auth.service;

import java.util.List;

public interface IRecoveryCodeService {
    /**
     * 生成恢复码
     * @param userId 用户ID
     * @param count 恢复码数量
     * @return 恢复码列表
     */
    List<String> generateCodes(String userId, int count);

    /**
     * 验证恢复码
     * @param userId 用户ID
     * @param code 恢复码
     * @return 是否验证成功
     */
    boolean verifyCode(String userId, String code);

    /**
     * 获取可用恢复码列表
     * @param userId 用户ID
     * @return 恢复码列表
     */
    List<String> getAvailableCodes(String userId);

    /**
     * 标记恢复码已使用
     * @param userId 用户ID
     * @param code 恢复码
     */
    void markCodeUsed(String userId, String code);
}
```

## BizModel Design

### NopAuth2faConfigBizModel

```java
package io.nop.auth.service;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.config.IConfigReference;
import io.nop.auth.domain.NopAuth2faConfig;
import io.nop.auth.domain.NopAuthSmsLog;
import io.nop.auth.domain.NopAuthUser;
import io.nop.orm.api.IOrmEntityDao;
import io.nop.service.crud.CrudBizModel;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static io.nop.auth.AuthErrors.*;

@BizModel("NopAuth2faConfig")
public class NopAuth2faConfigBizModel extends CrudBizModel<NopAuth2faConfig> {

    public NopAuth2faConfigBizModel() {
        setEntityName(NopAuth2faConfig.class.getName());
    }

    @Inject
    private ITotpService totpService;

    @Inject
    private ISmsCodeService smsCodeService;

    @Inject
    private IRecoveryCodeService recoveryCodeService;

    @Inject
    private IOrmEntityDao<NopAuthUser> userDao;

    /**
     * 启用TOTP
     * @param userId 用户ID
     * @return TOTP密钥和二维码URL
     */
    @BizMutation
    @Transactional
    public Map<String, String> enableTotp(@Name("userId") String userId) {
        // 验证用户存在
        NopAuthUser user = userDao.getEntityById(userId);
        if (user == null) {
            throw new NopException(ERR_AUTH_USER_NOT_FOUND)
                .param(ARG_USER_ID, userId);
        }

        // 生成TOTP密钥
        String secret = totpService.generateSecret();

        // 生成二维码URL
        String qrCodeUrl = totpService.generateQrCodeUrl(secret, "NopPlatform", user.getUserName());

        // 查找或创建2FA配置
        NopAuth2faConfig config = dao().findFirstByExample(
            FilterBean.eq(NopAuth2faConfig.PROP_NAME_userId, userId)
        );

        if (config == null) {
            config = new NopAuth2faConfig();
            config.setSid(StringHelper.generateUUID());
            config.setUserId(userId);
        }

        config.setTwoFactorMethod("totp");
        config.setTotpSecret(encryptSecret(secret));
        config.setEnabled(false);

        dao().saveEntity(config);

        return Map.of(
            "secret", secret,
            "qrCodeUrl", qrCodeUrl
        );
    }

    /**
     * 验证TOTP码并完成绑定
     * @param userId 用户ID
     * @param code TOTP验证码
     */
    @BizMutation
    @Transactional
    public void verifyAndEnableTotp(@Name("userId") String userId,
                                    @Name("code") String code) {
        NopAuth2faConfig config = dao().findFirstByExample(
            FilterBean.eq(NopAuth2faConfig.PROP_NAME_userId, userId)
        );

        if (config == null) {
            throw new NopException(ERR_AUTH_2FA_CONFIG_NOT_FOUND)
                .param(ARG_USER_ID, userId);
        }

        // 验证TOTP码
        String secret = decryptSecret(config.getTotpSecret());
        if (!totpService.verifyCode(secret, code)) {
            throw new NopException(ERR_AUTH_2FA_INVALID_CODE);
        }

        // 生成恢复码
        List<String> recoveryCodes = recoveryCodeService.generateCodes(userId, 10);

        // 启用2FA
        config.setEnabled(true);
        config.setEnabledAt(new Date());
        config.setRecoveryCodes(encryptRecoveryCodes(recoveryCodes));
        dao().saveEntity(config);

        // 更新用户状态
        NopAuthUser user = userDao.requireEntityById(userId);
        user.setTwoFactorEnabled(true);
        userDao.saveEntity(user);
    }

    /**
     * 启用短信验证码
     * @param userId 用户ID
     * @param phoneNumber 手机号
     */
    @BizMutation
    @Transactional
    public void enableSms(@Name("userId") String userId,
                         @Name("phoneNumber") String phoneNumber) {
        // 验证用户存在
        NopAuthUser user = userDao.requireEntityById(userId);
        if (user == null) {
            throw new NopException(ERR_AUTH_USER_NOT_FOUND)
                .param(ARG_USER_ID, userId);
        }

        // 检查频率限制
        if (!smsCodeService.checkRateLimit(userId, phoneNumber)) {
            throw new NopException(ERR_AUTH_SMS_RATE_LIMIT_EXCEEDED);
        }

        // 发送验证码
        smsCodeService.sendCode(userId, phoneNumber, "bind");

        // 保存配置（待验证）
        NopAuth2faConfig config = dao().findFirstByExample(
            FilterBean.eq(NopAuth2faConfig.PROP_NAME_userId, userId)
        );

        if (config == null) {
            config = new NopAuth2faConfig();
            config.setSid(StringHelper.generateUUID());
            config.setUserId(userId);
        }

        config.setTwoFactorMethod("sms");
        config.setPhoneNumber(phoneNumber);
        config.setEnabled(false);
        dao().saveEntity(config);
    }

    /**
     * 验证短信验证码并完成绑定
     * @param userId 用户ID
     * @param code 验证码
     */
    @BizMutation
    @Transactional
    public void verifyAndEnableSms(@Name("userId") String userId,
                                    @Name("code") String code) {
        NopAuth2faConfig config = dao().requireEntityById(userId);
        if (config == null) {
            throw new NopException(ERR_AUTH_2FA_CONFIG_NOT_FOUND)
                .param(ARG_USER_ID, userId);
        }

        // 验证短信验证码
        if (!smsCodeService.verifyCode(userId, code)) {
            throw new NopException(ERR_AUTH_2FA_INVALID_CODE);
        }

        // 生成恢复码
        List<String> recoveryCodes = recoveryCodeService.generateCodes(userId, 10);

        // 启用2FA
        config.setEnabled(true);
        config.setEnabledAt(new Date());
        config.setRecoveryCodes(encryptRecoveryCodes(recoveryCodes));
        dao().saveEntity(config);

        // 更新用户状态
        NopAuthUser user = userDao.requireEntityById(userId);
        user.setTwoFactorEnabled(true);
        userDao.saveEntity(user);
    }

    /**
     * 禁用2FA
     * @param userId 用户ID
     */
    @BizMutation
    @Transactional
    public void disable2fa(@Name("userId") String userId) {
        NopAuth2faConfig config = dao().findFirstByExample(
            FilterBean.eq(NopAuth2faConfig.PROP_NAME_userId, userId)
        );

        if (config != null) {
            dao().deleteEntity(config);
        }

        // 更新用户状态
        NopAuthUser user = userDao.requireEntityById(userId);
        user.setTwoFactorEnabled(false);
        userDao.saveEntity(user);
    }

    /**
     * 获取恢复码列表
     * @param userId 用户ID
     * @return 恢复码列表
     */
    @BizQuery
    public List<String> getRecoveryCodes(@Name("userId") String userId) {
        NopAuth2faConfig config = dao().requireEntityById(userId);
        if (config == null || !config.getEnabled()) {
            return List.of();
        }

        String encryptedCodes = config.getRecoveryCodes();
        return decryptRecoveryCodes(encryptedCodes);
    }

    /**
     * 重新生成恢复码
     * @param userId 用户ID
     * @return 新的恢复码列表
     */
    @BizMutation
    @Transactional
    public List<String> regenerateRecoveryCodes(@Name("userId") String userId) {
        NopAuth2faConfig config = dao().requireEntityById(userId);
        if (config == null) {
            throw new NopException(ERR_AUTH_2FA_CONFIG_NOT_FOUND)
                .param(ARG_USER_ID, userId);
        }

        // 生成新恢复码
        List<String> newCodes = recoveryCodeService.generateCodes(userId, 10);
        config.setRecoveryCodes(encryptRecoveryCodes(newCodes));
        dao().saveEntity(config);

        return newCodes;
    }

    /**
     * 加密密钥
     */
    private String encryptSecret(String secret) {
        // 使用Nop平台的加密工具
        return CryptoHelper.encryptAES(secret);
    }

    /**
     * 解密密钥
     */
    private String decryptSecret(String encrypted) {
        return CryptoHelper.decryptAES(encrypted);
    }

    /**
     * 加密恢复码
     */
    private String encryptRecoveryCodes(List<String> codes) {
        return JsonTool.instance().beanToJson(codes);
    }

    /**
     * 解密恢复码
     */
    private List<String> decryptRecoveryCodes(String encrypted) {
        return JsonTool.instance().parseListFromText(encrypted, String.class);
    }
}
```

## Login Flow Enhancement

### Extended NopAuthUserBizModel

```java
package io.nop.auth.service;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.domain.NopAuthUser;
import io.nop.commons.util.StringHelper;
import jakarta.inject.Inject;
import java.util.Map;

import static io.nop.auth.AuthErrors.*;

@BizModel("NopAuthUser")
public class NopAuthUserBizModel extends CrudBizModel<NopAuthUser> {

    public NopAuthUserBizModel() {
        setEntityName(NopAuthUser.class.getName());
    }

    @Inject
    private NopAuth2faConfigBizModel twoFactorConfigBizModel;

    @Inject
    private ITotpService totpService;

    @Inject
    private ISmsCodeService smsCodeService;

    @Inject
    private IRecoveryCodeService recoveryCodeService;

    /**
     * 第一步：用户名密码登录
     * @param username 用户名
     * @param password 密码
     * @return 登录结果（包含临时令牌或JWT）
     */
    @BizMutation
    public Map<String, Object> login(@Name("username") String username,
                                     @Name("password") String password) {
        // 1. 验证用户名和密码
        NopAuthUser user = authenticate(username, password);
        if (user == null) {
            throw new NopException(ERR_AUTH_INVALID_CREDENTIALS);
        }

        // 2. 检查是否启用2FA
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            // 生成临时令牌
            String tempToken = generateTempToken(user.getUserId());

            // 获取可用的2FA方式
            NopAuth2faConfig config = twoFactorConfigBizModel.dao().findFirstByExample(
                FilterBean.eq(NopAuth2faConfig.PROP_NAME_userId, user.getUserId())
            );

            List<String> availableMethods = List.of("totp", "sms", "recovery");

            return Map.of(
                "tempToken", tempToken,
                "requires2FA", true,
                "availableMethods", availableMethods,
                "userId", user.getUserId()
            );
        } else {
            // 直接生成JWT令牌
            String jwtToken = generateJwtToken(user);
            return Map.of(
                "token", jwtToken,
                "expiresIn", 7200,
                "userId", user.getUserId()
            );
        }
    }

    /**
     * 第二步：2FA验证
     * @param tempToken 临时令牌
     * @param code 验证码
     * @param method 验证方式(totp/sms/recovery)
     * @return JWT令牌
     */
    @BizMutation
    public Map<String, Object> verify2fa(@Name("tempToken") String tempToken,
                                         @Name("code") String code,
                                         @Name("method") String method) {
        // 1. 验证临时令牌
        String userId = validateTempToken(tempToken);

        // 2. 获取用户
        NopAuthUser user = dao().requireEntityById(userId);

        // 3. 验证2FA码
        boolean verified = switch (method) {
            case "totp" -> verifyTotp(userId, code);
            case "sms" -> verifySmsCode(userId, code);
            case "recovery" -> verifyRecoveryCode(userId, code);
            default -> false;
        };

        if (!verified) {
            throw new NopException(ERR_AUTH_2FA_INVALID_CODE);
        }

        // 4. 更新最后使用时间
        NopAuth2faConfig config = twoFactorConfigBizModel.dao().findFirstByExample(
            FilterBean.eq(NopAuth2faConfig.PROP_NAME_userId, userId)
        );
        if (config != null) {
            config.setLastUsedAt(new Date());
            twoFactorConfigBizModel.dao().saveEntity(config);
        }

        // 5. 生成JWT令牌
        String jwtToken = generateJwtToken(user);

        return Map.of(
            "token", jwtToken,
            "expiresIn", 7200,
            "userId", user.getUserId(),
            "2faVerified", true,
            "2faMethod", method
        );
    }

    /**
     * 验证TOTP
     */
    private boolean verifyTotp(String userId, String code) {
        NopAuth2faConfig config = twoFactorConfigBizModel.dao().findFirstByExample(
            FilterBean.eq(NopAuth2faConfig.PROP_NAME_userId, userId)
        );

        if (config == null || !"totp".equals(config.getTwoFactorMethod())) {
            return false;
        }

        String secret = CryptoHelper.decryptAES(config.getTotpSecret());
        return totpService.verifyCode(secret, code);
    }

    /**
     * 验证短信验证码
     */
    private boolean verifySmsCode(String userId, String code) {
        return smsCodeService.verifyCode(userId, code);
    }

    /**
     * 验证恢复码
     */
    private boolean verifyRecoveryCode(String userId, String code) {
        if (recoveryCodeService.verifyCode(userId, code)) {
            recoveryCodeService.markCodeUsed(userId, code);
            return true;
        }
        return false;
    }

    /**
     * 生成临时令牌
     */
    private String generateTempToken(String userId) {
        // 生成短期有效的临时令牌（5分钟）
        return JwtHelper.generateToken(Map.of("userId", userId), 300);
    }

    /**
     * 验证临时令牌
     */
    private String validateTempToken(String tempToken) {
        try {
            Map<String, Object> claims = JwtHelper.parseToken(tempToken);
            return (String) claims.get("userId");
        } catch (Exception e) {
            throw new NopException(ERR_AUTH_INVALID_TEMP_TOKEN);
        }
    }

    /**
     * 生成JWT令牌
     */
    private String generateJwtToken(NopAuthUser user) {
        // 生成长期有效的JWT令牌（2小时）
        return JwtHelper.generateToken(Map.of(
            "userId", user.getUserId(),
            "userName", user.getUserName(),
            "tenantId", user.getTenantId()
        ), 7200);
    }

    /**
     * 认证用户名和密码
     */
    private NopAuthUser authenticate(String username, String password) {
        NopAuthUser example = new NopAuthUser();
        example.setUserName(username);
        NopAuthUser user = dao().findFirstByExample(example);

        if (user == null) {
            return null;
        }

        // 验证密码
        String encryptedPassword = CryptoHelper.encryptBCrypt(password);
        if (!StringHelper.equals(user.getPassword(), encryptedPassword)) {
            return null;
        }

        return user;
    }
}
```

## Configuration Management

### Application Configuration (application.yaml)

```yaml
nop:
  auth:
    # JWT配置
    jwt:
      secret: ${JWT_SECRET:your-secret-key-here}
      expiration: 7200  # 2小时，单位秒
      algorithm: HS256

    # 2FA配置
    2fa:
      enabled: true
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
      recovery:
        code-count: 10    # 恢复码数量
        code-length: 8    # 恢复码长度

    # 短信服务配置
    sms:
      aliyun:
        access-key-id: ${ALIYUN_ACCESS_KEY_ID}
        access-key-secret: ${ALIYUN_ACCESS_KEY_SECRET}
        sign-name: NopPlatform
        template-code: ${ALIYUN_SMS_TEMPLATE}
      tencent:
        secret-id: ${TENCENT_SECRET_ID}
        secret-key: ${TENCENT_SECRET_KEY}
        sdk-app-id: ${TENCENT_SDK_APP_ID}
        sign-name: NopPlatform
        template-id: ${TENCENT_SMS_TEMPLATE}

    # 加密配置
    encryption:
      aes-key: ${AES_ENCRYPTION_KEY}
      bcrypt-cost: 12
```

### Config Reference Interface

```java
package io.nop.auth.config;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.SourceLocation;
import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface NopAuthConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(NopAuthConfigs.class);

    @Description("是否启用2FA")
    IConfigReference<Boolean> CFG_2FA_ENABLED = varRef(s_loc,
            "nop.auth.2fa.enabled", Boolean.class, false);

    @Description("可用的2FA方式")
    IConfigReference<String> CFG_2FA_METHODS = varRef(s_loc,
            "nop.auth.2fa.methods", String.class, "totp,sms");

    @Description("TOTP时间窗口")
    IConfigReference<Integer> CFG_TOTP_WINDOW_SIZE = varRef(s_loc,
            "nop.auth.2fa.totp.window-size", Integer.class, 1);

    @Description("TOTP验证码位数")
    IConfigReference<Integer> CFG_TOTP_DIGITS = varRef(s_loc,
            "nop.auth.2fa.totp.digits", Integer.class, 6);

    @Description("短信验证码有效期（秒）")
    IConfigReference<Integer> CFG_SMS_VALIDITY = varRef(s_loc,
            "nop.auth.2fa.sms.validity", Integer.class, 300);

    @Description("短信发送频率限制（每分钟）")
    IConfigReference<Integer> CFG_SMS_RATE_PER_MINUTE = varRef(s_loc,
            "nop.auth.2fa.sms.rate-limit.per-minute", Integer.class, 1);

    @Description("短信发送频率限制（每天）")
    IConfigReference<Integer> CFG_SMS_RATE_PER_DAY = varRef(s_loc,
            "nop.auth.2fa.sms.rate-limit.per-day", Integer.class, 10);

    @Description("恢复码数量")
    IConfigReference<Integer> CFG_RECOVERY_CODE_COUNT = varRef(s_loc,
            "nop.auth.2fa.recovery.code-count", Integer.class, 10);
}
```

## Error Codes Definition

```java
package io.nop.auth;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;
import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface AuthErrors {
    String ARG_USER_ID = "userId";
    String ARG_USER_NAME = "userName";
    String ARG_ROLE_ID = "roleId";
    String ARG_ROLE_CODE = "roleCode";
    String ARG_PERMISSION_ID = "permissionId";

    // 2FA相关错误码
    String ARG_2FA_METHOD = "twoFactorMethod";
    String ARG_PHONE_NUMBER = "phoneNumber";

    ErrorCode ERR_AUTH_USER_NOT_FOUND = define("nop.err.auth.user-not-found",
        "用户[{userId}]不存在", ARG_USER_ID);

    ErrorCode ERR_AUTH_USER_NAME_EXISTS = define("nop.err.auth.user-name-exists",
        "用户名[{userName}]已存在", ARG_USER_NAME);

    ErrorCode ERR_AUTH_INVALID_CREDENTIALS = define("nop.err.auth.invalid-credentials",
        "用户名或密码错误");

    ErrorCode ERR_AUTH_2FA_CONFIG_NOT_FOUND = define("nop.err.auth.2fa-config-not-found",
        "用户[{userId}]的2FA配置不存在", ARG_USER_ID);

    ErrorCode ERR_AUTH_2FA_INVALID_CODE = define("nop.err.auth.2fa-invalid-code",
        "验证码无效或已过期");

    ErrorCode ERR_AUTH_2FA_ALREADY_ENABLED = define("nop.err.auth.2fa-already-enabled",
        "用户[{userId}]已启用2FA", ARG_USER_ID);

    ErrorCode ERR_AUTH_2FA_NOT_ENABLED = define("nop.err.auth.2fa-not-enabled",
        "用户[{userId}]未启用2FA", ARG_USER_ID);

    ErrorCode ERR_AUTH_SMS_RATE_LIMIT_EXCEEDED = define("nop.err.auth.sms-rate-limit-exceeded",
        "短信发送频率超限，请稍后再试");

    ErrorCode ERR_AUTH_SMS_SEND_FAILED = define("nop.err.auth.sms-send-failed",
        "短信发送失败: {reason}");

    ErrorCode ERR_AUTH_RECOVERY_CODE_INVALID = define("nop.err.auth.recovery-code-invalid",
        "恢复码无效或已使用");

    ErrorCode ERR_AUTH_RECOVERY_CODE_EXHAUSTED = define("nop.err.auth.recovery-code-exhausted",
        "恢复码已用尽，请重新生成");

    ErrorCode ERR_AUTH_TEMP_TOKEN_INVALID = define("nop.err.auth.temp-token-invalid",
        "临时令牌无效或已过期");
}
```

## Security Considerations

### 1. Encryption Strategy

- **TOTP密钥**: 使用AES-256加密存储
- **恢复码**: 使用JSON格式存储，加密整个列表
- **短信验证码**: 使用AES-256加密存储
- **密码**: 使用BCrypt加密（cost factor 12）

### 2. Rate Limiting

- **短信发送**: 每分钟1次，每天10次
- **TOTP验证**: 5次失败锁定30分钟
- **恢复码**: 每个恢复码仅能使用一次
- **登录尝试**: 与现有登录策略集成

### 3. Session Management

- 2FA验证通过后刷新会话
- 记录使用的验证方式
- 支持要求关键操作重新验证2FA

## GraphQL Schema

### Type Definitions

```graphql
type NopAuth2faConfig {
    sid: ID!
    userId: ID!
    twoFactorMethod: String!
    totpSecret: String
    recoveryCodes: String
    phoneNumber: String
    enabled: Boolean!
    enabledAt: DateTime
    lastUsedAt: DateTime
    createdTime: DateTime!
    updatedTime: DateTime!
}

type NopAuthSmsLog {
    sid: ID!
    userId: ID
    phoneNumber: String!
    code: String!
    purpose: String!
    sentAt: DateTime!
    verifiedAt: DateTime
    expiredAt: DateTime!
    ipAddress: String
    createdTime: DateTime!
}
```

### Queries

```graphql
type Query {
    # 获取2FA配置
    get2faConfig(userId: ID!): NopAuth2faConfig

    # 获取恢复码列表
    getRecoveryCodes(userId: ID!): [String!]!

    # 查询短信日志
    findSmsLogs(query: QueryBean): [NopAuthSmsLog!]!
}
```

### Mutations

```graphql
type Mutation {
    # TOTP相关
    enableTotp(userId: ID!): EnableTotpResult!
    verifyAndEnableTotp(userId: ID!, code: String!): Boolean!

    # 短信相关
    enableSms(userId: ID!, phoneNumber: String!): Boolean!
    verifyAndEnableSms(userId: ID!, code: String!): Boolean!

    # 通用
    disable2fa(userId: ID!): Boolean!
    regenerateRecoveryCodes(userId: ID!): [String!]!

    # 登录
    login(username: String!, password: String!): LoginResult!
    verify2fa(tempToken: String!, code: String!, method: String!): Verify2faResult!
}

type EnableTotpResult {
    secret: String!
    qrCodeUrl: String!
}

type LoginResult {
    token: String
    tempToken: String
    requires2FA: Boolean!
    availableMethods: [String!]!
    userId: ID!
    expiresIn: Int
}

type Verify2faResult {
    token: String!
    userId: ID!
    2faVerified: Boolean!
    2faMethod: String!
    expiresIn: Int
}
```

## Implementation Notes

### 1. Service Implementation Patterns

- Implement `ITotpService` using Apache Commons Codec or similar library
- Implement `ISmsService` as interface with provider-specific implementations
- Implement `ISmsCodeService` to handle SMS code generation, validation, and rate limiting
- Implement `IRecoveryCodeService` to manage recovery codes

### 2. External Dependencies

- **TOTP Library**: Apache Commons Codec or Google Authenticator library
- **SMS Service**: Aliyun SMS SDK or Tencent Cloud SMS SDK
- **Encryption**: Nop Platform's built-in crypto utilities

### 3. Integration Points

- Extend `NopAuthUserBizModel` for login flow enhancement
- Create new `NopAuth2faConfigBizModel` for 2FA configuration management
- Integrate with existing authentication and authorization mechanisms
- Extend user entity with `twoFactorEnabled` field

## Testing Strategy

### Unit Tests

- TOTP generation and verification logic
- SMS code generation and validation logic
- Recovery code generation and validation logic
- Encryption and decryption functions

### Integration Tests

- Complete 2FA enablement flow
- 2FA login flow
- Various error scenarios (invalid codes, expired codes, etc.)

### E2E Tests

- User registration and 2FA enablement
- Login with 2FA
- Recovery code usage
- 2FA disablement

## Migration Path

### Phase 1: Development (2 weeks)

- Implement TOTP and SMS verification code functionality
- Develop management interfaces
- Write tests

### Phase 2: Testing (1 week)

- Comprehensive testing
- Performance optimization
- Documentation

### Phase 3: Gradual Rollout (2 weeks)

- Enable 2FA for 10% of users (optional)
- Collect feedback
- Fix issues

### Phase 4: Full Rollout (ongoing)

- Full rollout
- Require 2FA for administrators and high-privilege users
- Recommend 2FA for regular users

## Conclusion

This design document describes a 2FA implementation that follows Nop Platform's architecture and design patterns:

1. **BizModel Pattern**: Uses `CrudBizModel` with `@BizQuery` and `@BizMutation` annotations
2. **GraphQL API**: All operations exposed through GraphQL
3. **ORM Integration**: Uses `IOrmEntityDao` and entity models extending `OrmEntity`
4. **Configuration**: Uses `IConfigReference` for configuration management
5. **Error Handling**: Uses `NopException` with `ErrorCode` definitions
6. **Dependency Injection**: Uses `@Inject` from Jakarta Inject
7. **Transaction Management**: Uses `@Transactional` from Nop platform
8. **Encryption**: Leverages Nop Platform's built-in crypto utilities

Following this design will ensure consistency with Nop Platform's existing architecture and patterns.
