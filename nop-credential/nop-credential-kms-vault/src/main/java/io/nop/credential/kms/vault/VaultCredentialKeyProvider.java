/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.kms.vault;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.commons.crypto.ITextCipher;
import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.commons.util.StringHelper;
import io.nop.credential.api.crypto.ICredentialKeyProvider;
import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@link ICredentialKeyProvider} 的 HashiCorp Vault KV v2 参照实现（W10-impl）。
 *
 * <p><b>集成模式 = 密钥材料交付（material delivery）</b>：启动期（{@code @PostConstruct}）
 * 经 {@link IHttpClient} 直接调用 Vault KV v2 HTTP API（{@code GET /v1/{mount}/data/{path}} +
 * {@code X-Vault-Token} 头）一次性读取全部配置 keyId 的材料，按一期相同方式构造
 * {@link AESTextCipher} 集合；<b>运行期零托管端调用</b>（{@code getKey} 为纯内存查找）；
 * 材料仅存进程内存。不引入 Vault Java SDK（W10-impl Phase 1 Decision，对设计 §4.1
 * 结论 5 的显式细化偏离，Phase 4 回写设计标注）。
 *
 * <p><b>fail-closed 语义（全部启动期，无本地降级）</b>：托管端不可达/认证失败（401/403）/
 * 配置的 keyId 在托管端缺失（404）/材料非法/配置矛盾（master-keys 残留、迁移残余含
 * active key）均抛 {@link NopException} 使应用拒绝启动。任何托管端故障都不触发本地密钥回退。
 *
 * <p><b>装配</b>：与缺省实现同名 bean id {@code nopCredentialKeyProvider} 覆盖
 * （{@code app-kms-vault.beans.xml}，{@code ioc:condition} 门控
 * {@code nop.credential.key-provider=vault}），不新增第二个
 * {@code ICredentialKeyProvider} 独立 bean id。
 *
 * <p><b>keyId → 材料不变式</b>：同一 keyId 在仍有 {@code cv1:{keyId}} 密文存续期间材料不可变。
 * Vault 侧轮换必须以新 keyId（新 secret 路径）进行；厂商"同名原地升版本"式轮换
 * （如 KV v2 版本删除重建同路径同名）与 {@code cv1:} keyId 路由不兼容，属配置禁区。
 */
public class VaultCredentialKeyProvider implements ICredentialKeyProvider {

    private static final Logger LOG = LoggerFactory.getLogger(VaultCredentialKeyProvider.class);

    /** 与一期 local 实现一致的 keyId 字符集约束（cv1: 按冒号 split 无歧义的前提）。 */
    private static final Pattern KEY_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    /** Vault KV v2 secret 中承载密钥材料的字段名（材料形态与一期 passphrase 同构）。 */
    public static final String MATERIAL_FIELD = "passphrase";

    /** Vault 认证头（KV v2 令牌认证）。 */
    public static final String HEADER_VAULT_TOKEN = "X-Vault-Token";

    /**
     * Vault 服务基础地址（如 {@code http://127.0.0.1:8200}）。由配置项
     * {@code nop.credential.vault.address} 注入。字段为 protected 以兼容 NopIoC 字段注入。
     */
    protected String address;

    /** Vault 访问令牌。由配置项 {@code nop.credential.vault.token} 注入。 */
    protected String token;

    /**
     * keyId → secret 路径映射列表（每条 {@code keyId:{mount}/data/{path}}，路径为
     * {@code /v1/} 之后的部分，如 {@code secret/data/credential/keyA}）。
     */
    protected List<String> keys;

    /** active key 的 keyId。未设置时取映射首项。 */
    protected String activeKeyId;

    /**
     * 迁移残余列表（{@code keyId:passphrase}，与一期 master-keys 条目同构）。
     * 残余 key 只并入解密 keyMap（不进入 active 候选），每次启动输出 WARN 审计。
     */
    protected List<String> migrationKeys;

    /**
     * 一期本地主密钥配置（仅用于来源混合校验：KMS 激活时必须为空，非材料来源）。
     */
    protected List<String> masterKeys;

    /**
     * HTTP 客户端（平台 {@code nopHttpClient} 条件 bean；部署需引入 raw client
     * 实现模块如 nop-http-client-jdk）。字段为 protected 以兼容 NopIoC 字段注入。
     */
    @Inject
    protected IHttpClient httpClient;

    private Map<String, ITextCipher> keyMap = Collections.emptyMap();
    private Set<String> keyIds = Collections.emptySet();

    @InjectValue("@cfg:nop.credential.vault.address|")
    public void setAddress(String address) {
        this.address = address;
    }

    @InjectValue("@cfg:nop.credential.vault.token|")
    public void setToken(String token) {
        this.token = token;
    }

    @InjectValue("@cfg:nop.credential.vault.keys|")
    public void setKeys(List<String> keys) {
        this.keys = keys;
    }

    @InjectValue("@cfg:nop.credential.vault.active-key-id|")
    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    @InjectValue("@cfg:nop.credential.vault.migration-keys|")
    public void setMigrationKeys(List<String> migrationKeys) {
        this.migrationKeys = migrationKeys;
    }

    @InjectValue("@cfg:nop.credential.master-keys|")
    public void setMasterKeys(List<String> masterKeys) {
        this.masterKeys = masterKeys;
    }

    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @PostConstruct
    public void init() {
        if (StringHelper.isEmpty(address) || StringHelper.isEmpty(token)) {
            throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_CONFIG_MISSING)
                    .param(VaultCredentialErrors.ARG_ERROR, "address/token is required");
        }
        if (keys == null || keys.isEmpty()) {
            throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_NO_KEY_CONFIGURED);
        }

        // 密钥来源不混合：KMS 激活时本地 master-keys 必须清空（唯一例外 = 迁移残余列表）
        if (hasNonBlankEntry(masterKeys)) {
            throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_MASTER_KEYS_RESIDUAL);
        }

        // 解析 keyId → secret 路径映射（保持配置顺序，便于 active key 缺省取首项）
        Map<String, String> mappings = new LinkedHashMap<>();
        for (String entry : keys) {
            int colonIdx = entry == null ? -1 : entry.indexOf(':');
            if (colonIdx <= 0 || colonIdx == entry.length() - 1) {
                throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_KEY_MAPPING_INVALID)
                        .param(VaultCredentialErrors.ARG_KEY_MAPPING, String.valueOf(entry));
            }
            String keyId = entry.substring(0, colonIdx);
            String path = entry.substring(colonIdx + 1);
            if (!KEY_ID_PATTERN.matcher(keyId).matches() || StringHelper.isBlank(path)
                    || mappings.containsKey(keyId)) {
                throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_KEY_MAPPING_INVALID)
                        .param(VaultCredentialErrors.ARG_KEY_MAPPING, entry);
            }
            mappings.put(keyId, path);
        }

        // 启动期批量读取托管端材料，构造单钥加密器集合（材料仅存进程内存）
        Map<String, ITextCipher> built = new LinkedHashMap<>();
        for (Map.Entry<String, String> mapping : mappings.entrySet()) {
            String material = fetchMaterial(mapping.getKey(), mapping.getValue());
            built.put(mapping.getKey(), new AESTextCipher().encKey(material));
        }

        // active key：显式指定必须命中 Vault 密钥（不得指向迁移残余 key）；缺省取映射首项
        if (StringHelper.isEmpty(activeKeyId)) {
            this.activeKeyId = mappings.keySet().iterator().next();
        } else if (!built.containsKey(activeKeyId)) {
            throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_UNKNOWN_ACTIVE_KEY)
                    .param(VaultCredentialErrors.ARG_KEY_ID, activeKeyId)
                    .param(VaultCredentialErrors.ARG_AVAILABLE_KEY_IDS, built.keySet());
        }

        // 迁移残余列表：只并入解密 keyMap（不进入 active 候选），WARN 审计提示收尾
        if (migrationKeys != null) {
            for (String entry : migrationKeys) {
                int colonIdx = entry == null ? -1 : entry.indexOf(':');
                if (colonIdx <= 0 || colonIdx == entry.length() - 1
                        || !KEY_ID_PATTERN.matcher(entry.substring(0, colonIdx)).matches()) {
                    throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_MIGRATION_KEY_INVALID)
                            .param(VaultCredentialErrors.ARG_MIGRATION_KEY, String.valueOf(entry));
                }
                String keyId = entry.substring(0, colonIdx);
                String passphrase = entry.substring(colonIdx + 1);
                // 禁含 active key 检查先于 keyId 冲突检查（active 必属 Vault 密钥集，
                // 先查冲突会吞掉更具体的"残余含 active"错误信号）
                if (keyId.equals(activeKeyId)) {
                    throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_MIGRATION_KEY_ACTIVE)
                            .param(VaultCredentialErrors.ARG_KEY_ID, keyId);
                }
                if (built.containsKey(keyId)) {
                    throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_MIGRATION_KEY_INVALID)
                            .param(VaultCredentialErrors.ARG_MIGRATION_KEY, entry);
                }
                built.put(keyId, new AESTextCipher().encKey(passphrase));
                LOG.warn("nop.credential.vault.migration-keys residual key still active: keyId={}, "
                        + "reencryptAll with the new active key then remove it from the migration list", keyId);
            }
        }

        this.keyMap = Collections.unmodifiableMap(built);
        this.keyIds = Collections.unmodifiableSet(new LinkedHashSet<>(built.keySet()));
    }

    @Override
    public String getActiveKeyId() {
        return activeKeyId;
    }

    @Override
    public ITextCipher getKey(String keyId) {
        ITextCipher cipher = keyMap.get(keyId);
        if (cipher == null) {
            throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_UNKNOWN_KEY_ID)
                    .param(VaultCredentialErrors.ARG_KEY_ID, keyId)
                    .param(VaultCredentialErrors.ARG_AVAILABLE_KEY_IDS, keyIds);
        }
        return cipher;
    }

    @Override
    public Set<String> getKeyIds() {
        return keyIds;
    }

    /**
     * 启动期从 Vault KV v2 读取单个 keyId 的密钥材料。
     * 响应结构 {@code {"data":{"data":{"passphrase":"..."},"metadata":{...}}}}。
     */
    private String fetchMaterial(String keyId, String path) {
        HttpRequest request = new HttpRequest();
        request.setMethod(HttpApiConstants.METHOD_GET);
        request.url(address + "/v1/" + path);
        request.header(HEADER_VAULT_TOKEN, token);

        IHttpResponse response;
        try {
            response = httpClient.fetch(request, null);
        } catch (Exception e) {
            throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_UNREACHABLE, e)
                    .param(VaultCredentialErrors.ARG_ADDRESS, address)
                    .param(VaultCredentialErrors.ARG_ERROR, e.getMessage());
        }

        int status = response.getHttpStatus();
        if (status == 401 || status == 403) {
            throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_AUTH_FAILED)
                    .param(VaultCredentialErrors.ARG_PATH, path);
        }
        if (status == 404) {
            throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_KEY_NOT_FOUND)
                    .param(VaultCredentialErrors.ARG_KEY_ID, keyId)
                    .param(VaultCredentialErrors.ARG_PATH, path);
        }
        if (status < 200 || status >= 300) {
            throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_READ_FAILED)
                    .param(VaultCredentialErrors.ARG_PATH, path)
                    .param(VaultCredentialErrors.ARG_STATUS, status);
        }

        String body = response.getBodyAsString();
        Object material = null;
        if (!StringHelper.isEmpty(body)) {
            try {
                Object parsed = JSON.parse(body);
                if (parsed instanceof Map) {
                    Object data = ((Map<?, ?>) parsed).get("data");
                    if (data instanceof Map) {
                        material = ((Map<?, ?>) data).get("data");
                    }
                }
            } catch (RuntimeException e) {
                material = null;
            }
        }
        if (!(material instanceof Map)) {
            throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_MATERIAL_INVALID)
                    .param(VaultCredentialErrors.ARG_KEY_ID, keyId)
                    .param(VaultCredentialErrors.ARG_PATH, path);
        }
        Object passphrase = ((Map<?, ?>) material).get(MATERIAL_FIELD);
        if (!(passphrase instanceof String) || StringHelper.isEmpty((String) passphrase)) {
            throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_MATERIAL_INVALID)
                    .param(VaultCredentialErrors.ARG_KEY_ID, keyId)
                    .param(VaultCredentialErrors.ARG_PATH, path);
        }
        return (String) passphrase;
    }

    private static boolean hasNonBlankEntry(List<String> list) {
        if (list == null) {
            return false;
        }
        for (String entry : list) {
            if (!StringHelper.isBlank(entry)) {
                return true;
            }
        }
        return false;
    }
}
