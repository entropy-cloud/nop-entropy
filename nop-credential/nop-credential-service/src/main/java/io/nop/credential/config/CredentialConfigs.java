/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.config;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import java.util.List;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * nop-credential 模块的配置项引用。配置项可通过 {@code application.yaml} 或
 * 环境变量（Nop 配置支持环境变量到属性名的映射）注入。
 */
public interface CredentialConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(CredentialConfigs.class);

    /**
     * 主密钥列表，每条形如 {@code keyId:passphrase}。keyId 须匹配 {@code [A-Za-z0-9_-]+}。
     * 可通过环境变量 {@code NOP_CREDENTIAL_MASTER_KEYS} 注入。
     */
    @Description("凭证库主密钥列表，每条 keyId:passphrase")
    @SuppressWarnings("unchecked")
    IConfigReference<List<String>> CFG_CREDENTIAL_MASTER_KEYS = (IConfigReference<List<String>>) (IConfigReference<?>) varRef(
            s_loc, "nop.credential.master-keys", List.class, null);

    /**
     * 当前用于加密的 active key 的 keyId。未设置时使用列表中的第一个 keyId。
     */
    @Description("当前用于加密的 active 主密钥 keyId（可选，缺省取列表首项）")
    IConfigReference<String> CFG_CREDENTIAL_ACTIVE_KEY_ID = varRef(
            s_loc, "nop.credential.active-key-id", String.class, null);
}
