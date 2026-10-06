/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;

import java.util.Map;

import static io.nop.orm.migration.MigrationErrors.ARG_REASON;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_ENCRYPT_KEY_MISMATCH;

/**
 * PREPARE 密钥一致门禁（设计 §4.6 M4）：v1 约束搬迁前后加密密钥一致，密文比对只在同 key 下有
 * 判定力——不同 key 下密文比对恒真，会把"数据搬对了但全被旧 key 加密"放行为绿灯，因此该约束
 * 必须前置校验而非留白。换 key/重加密归密钥管理工具（设计 Non-Goals §三.7）。
 *
 * <p>校验面：ORM 密码器配置键（{@code nop.crypt.default-enc-key}、{@code nop.orm.enc-assisted-key}）。
 * 同应用实例天然一致（单一配置面）；relocate 跨环境场景由目标覆盖映射显式声明目标侧取值
 * （全名或短名），与本侧不一致即门禁拒绝。密钥值只进长度摘要，绝不进日志/错误明文。</p>
 */
public class EncryptKeyConsistencyChecker {

    static final String CFG_DEFAULT_ENC_KEY = "nop.crypt.default-enc-key";
    static final String CFG_ENC_ASSISTED_KEY = "nop.orm.enc-assisted-key";

    /**
     * PREPARE 密钥一致性校验。
     *
     * @param targetOverrides 目标侧密钥覆盖（relocate 跨环境显式声明；null/空 = 同实例同配置面，
     *                        覆盖值只允许为空串或与源侧一致，否则视为换 key 显式拒绝）
     */
    public void check(MigrationJobConfig config, Map<String, String> targetOverrides) {
        checkKey(CFG_DEFAULT_ENC_KEY, override(targetOverrides, CFG_DEFAULT_ENC_KEY, "default-enc-key"));
        checkKey(CFG_ENC_ASSISTED_KEY, override(targetOverrides, CFG_ENC_ASSISTED_KEY, "enc-assisted-key"));
    }

    private String override(Map<String, String> overrides, String fullKey, String shortKey) {
        if (overrides == null || overrides.isEmpty())
            return null;
        String value = overrides.get(fullKey);
        return value != null ? value : overrides.get(shortKey);
    }

    private void checkKey(String configName, String targetValue) {
        String sourceValue = AppConfig.getConfigProvider().getConfigValue(configName, "");
        if (sourceValue == null)
            sourceValue = "";
        String effectiveTarget = targetValue == null ? sourceValue : targetValue;
        if (!sourceValue.equals(effectiveTarget)) {
            throw new NopException(ERR_MIG_ENCRYPT_KEY_MISMATCH).param(ARG_REASON,
                    "config '" + configName + "' differs between source and target context:"
                            + " source=" + summarize(sourceValue) + ",target=" + summarize(effectiveTarget)
                            + " (v1 requires identical encryption keys across the migration;"
                            + " key rotation is a separate re-encryption tool concern)");
        }
    }

    /** 密钥值只进摘要，不进日志/错误明文。 */
    private static String summarize(String value) {
        if (value == null || value.isEmpty())
            return "<empty>";
        return "len-" + value.length();
    }
}
