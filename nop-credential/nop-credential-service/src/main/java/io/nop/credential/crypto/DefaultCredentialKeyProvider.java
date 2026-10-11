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

    /** D3-04 单源：keyId 字符集约束引用 api 模块权威常量（消除本类独立副本）。 */
    private static final Pattern KEY_ID_PATTERN = Pattern.compile(ICredentialKeyProvider.KEY_ID_PATTERN);

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
        // D3-03（A1-audit successor，2026-08-17）：取值先 trim + 小写归一再比较——
        // " Vault "/"LOCAL" 等配置噪声不再误判（错误归因准确化；fail-closed 语义保持）
        String normalizedKeyProvider = keyProvider == null
                ? null : keyProvider.trim().toLowerCase(java.util.Locale.ROOT);
        if (!StringHelper.isEmpty(normalizedKeyProvider) && !"local".equals(normalizedKeyProvider)) {
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
            // R1.8 / P1-check-mcc-172：异常 param 一律 keyId-only 或非材料定位符，
            // 完整 entry（含 passphrase）不再进入任何异常消息（NopException.getMessage
            // 无条件拼接 params 全量，泄漏即主密钥口令入日志）；rejectReason 区分四形态
            if (entry == null || entry.isEmpty()) {
                throw entryInvalid("empty", null, entry);
            }

            int colonIdx = entry.indexOf(':');
            if (colonIdx <= 0 || colonIdx == entry.length() - 1) {
                // 缺少分隔冒号或 passphrase 为空——整串可能是裸 passphrase，
                // 不输出任何 entry 派生字符，仅静态标记 + 长度定位
                throw entryInvalid("no-colon-separator", null, entry);
            }

            String keyId = entry.substring(0, colonIdx);
            String passphrase = entry.substring(colonIdx + 1);

            if (!KEY_ID_PATTERN.matcher(keyId).matches()) {
                throw entryInvalid("key-id-pattern", keyId, entry);
            }

            // D5-06（A1-audit successor，2026-08-17）：passphrase 纯空白拒绝（isBlank）——
            // 空白材料构造的 cipher 形同弱密钥；含空格的非空白 passphrase 保持合法
            if (StringHelper.isBlank(passphrase)) {
                throw entryInvalid("blank-passphrase", keyId, entry);
            }

            if (built.containsKey(keyId)) {
                throw entryInvalid("duplicate-key-id", keyId, entry);
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
     * @deprecated D5-07（A1-audit successor，2026-08-17）：原 public 方法明文返回
     *             {@code keyId:passphrase} 列表（主密钥泄露面反模式，全仓零调用点），
     *             已收窄为包私有——生产代码/测试均无既有调用需要同步。
     */
    @Deprecated
    List<String> getMasterKeys() {
        if (masterKeys == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(masterKeys));
    }

    Map<String, ITextCipher> internalKeyMap() {
        return new HashMap<>(keyMap);
    }
    private static NopException entryInvalid(String reason, String keyId, String entry) {
        NopException e = new NopException(CredentialErrors.ERR_CREDENTIAL_MASTER_KEY_ENTRY_INVALID)
                .param(CredentialErrors.ARG_REJECT_REASON, reason);
        if (keyId != null)
            e.param(CredentialErrors.ARG_MASTER_KEY_ID, keyId);
        // 非 entry 派生字符：仅长度用于运维定位（长度本身无材料价值）
        e.param(CredentialErrors.ARG_MASTER_KEY_ENTRY, "<entry length=" + (entry == null ? -1 : entry.length()) + ">");
        return e;
    }

}
