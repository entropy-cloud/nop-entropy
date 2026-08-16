/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.crypto;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.crypto.ITextCipher;
import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.commons.util.StringHelper;
import io.nop.credential.api.crypto.ICredentialKeyProvider;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@link ICredentialKeyProvider} 的默认实现。启动期从 Nop 配置项
 * {@code nop.credential.master-keys}（{@code List<String>}，每条 {@code keyId:passphrase}）
 * 加载主密钥，构造对应的 {@link AESTextCipher}（{@code v1:} 格式）。
 *
 * <p>主密钥在启动期一次性加载（热重载为 Non-Blocking Follow-up）。任何非法条目
 * （keyId 不匹配 {@code [A-Za-z0-9_-]+}、缺少 passphrase、重复 keyId）均在初始化时
 * 抛出 {@link NopException}（fail-closed，不静默跳过）。
 */
public class DefaultCredentialKeyProvider implements ICredentialKeyProvider {

    private static final Pattern KEY_ID_PATTERN = Pattern.compile(CredentialCipher.CV1_KEY_ID_PATTERN);

    /**
     * 主密钥配置条目列表。由 Nop IoC 通过 {@code @cfg:} 解析注入。
     * 字段须为 protected（非 private）以兼容 NopIoC 字段注入限制。
     */
    protected List<String> masterKeys;

    /**
     * active key 的 keyId。由配置项 {@code nop.credential.active-key-id} 指定，
     * 未设置时取列表首项。
     */
    protected String activeKeyId;

    /**
     * 主密钥来源选择。由配置项 {@code nop.credential.key-provider} 指定
     * （缺省 {@code local}）。字段为 protected 以兼容 NopIoC 字段注入。
     */
    protected String keyProvider;

    private Map<String, ITextCipher> keyMap = Collections.emptyMap();
    private Set<String> keyIds = Collections.emptySet();

    @InjectValue("@cfg:nop.credential.master-keys|")
    public void setMasterKeys(List<String> masterKeys) {
        this.masterKeys = masterKeys;
    }

    @InjectValue("@cfg:nop.credential.active-key-id|")
    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    @InjectValue("@cfg:nop.credential.key-provider|local")
    public void setKeyProvider(String keyProvider) {
        this.keyProvider = keyProvider;
    }

    @PostConstruct
    public void init() {
        // W10 default-bean 守卫（修正设计 §4.3 "模块缺失即启动失败"前提的平台机制落地）：
        // NopIoC 的 ioc:default bean 是无条件兜底（$DEFAULT$ 前缀 + missing-bean 条件，
        // 与配置项取值无关）——KMS 模块未部署时本 bean 会照常注册并回退 local，
        // 正是设计要防的假安全。守卫在本 bean 实际构造时拦截：
        // - keyProvider 非 local → 启动失败（配置指向 KMS 而模块缺失）；
        // - 非 local 且 master-keys 非空 → 来源混合，启动失败（本地材料只允许存在于
        //   KMS 实现配置中的迁移残余列表；此检查置于守卫使模块缺失场景下也可达）。
        // key-provider=local/未配置时守卫零触发（一期行为不变）。
        if (!StringHelper.isEmpty(keyProvider) && !"local".equals(keyProvider)) {
            if (masterKeys != null && !masterKeys.isEmpty()) {
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_MASTER_KEYS_RESIDUAL);
            }
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_KEY_PROVIDER_MODULE_MISSING)
                    .param(CredentialErrors.ARG_KEY_PROVIDER, keyProvider);
        }

        if (masterKeys == null || masterKeys.isEmpty()) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_NO_MASTER_KEY_CONFIGURED);
        }

        // LinkedHashMap 保持配置顺序，便于 active key 缺省取首项
        Map<String, ITextCipher> built = new LinkedHashMap<>();
        for (String entry : masterKeys) {
            if (entry == null || entry.isEmpty()) {
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_MASTER_KEY_ENTRY_INVALID)
                        .param(CredentialErrors.ARG_MASTER_KEY_ENTRY, String.valueOf(entry));
            }

            int colonIdx = entry.indexOf(':');
            if (colonIdx <= 0 || colonIdx == entry.length() - 1) {
                // 缺少分隔冒号或 passphrase 为空
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_MASTER_KEY_ENTRY_INVALID)
                        .param(CredentialErrors.ARG_MASTER_KEY_ENTRY, entry);
            }

            String keyId = entry.substring(0, colonIdx);
            String passphrase = entry.substring(colonIdx + 1);

            if (!KEY_ID_PATTERN.matcher(keyId).matches()) {
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_MASTER_KEY_ENTRY_INVALID)
                        .param(CredentialErrors.ARG_MASTER_KEY_ENTRY, entry);
            }

            if (built.containsKey(keyId)) {
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_MASTER_KEY_ENTRY_INVALID)
                        .param(CredentialErrors.ARG_MASTER_KEY_ENTRY, entry);
            }

            ITextCipher cipher = new AESTextCipher().encKey(passphrase);
            built.put(keyId, cipher);
        }

        this.keyMap = Collections.unmodifiableMap(built);
        this.keyIds = Collections.unmodifiableSet(new java.util.LinkedHashSet<>(built.keySet()));

        if (activeKeyId == null || activeKeyId.isEmpty()) {
            // 取配置列表首项作为 active key
            this.activeKeyId = built.keySet().iterator().next();
        } else if (!built.containsKey(activeKeyId)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_UNKNOWN_KEY_ID)
                    .param(CredentialErrors.ARG_KEY_ID, activeKeyId)
                    .param(CredentialErrors.ARG_AVAILABLE_KEY_IDS, built.keySet());
        }
    }

    @Override
    public String getActiveKeyId() {
        return activeKeyId;
    }

    @Override
    public ITextCipher getKey(String keyId) {
        ITextCipher cipher = keyMap.get(keyId);
        if (cipher == null) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_UNKNOWN_KEY_ID)
                    .param(CredentialErrors.ARG_KEY_ID, keyId)
                    .param(CredentialErrors.ARG_AVAILABLE_KEY_IDS, keyIds);
        }
        return cipher;
    }

    @Override
    public Set<String> getKeyIds() {
        return keyIds;
    }

    /**
     * 供测试与诊断使用：返回主密钥条目的只读副本。
     */
    public List<String> getMasterKeys() {
        if (masterKeys == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(masterKeys));
    }

    Map<String, ITextCipher> internalKeyMap() {
        return new HashMap<>(keyMap);
    }
}
