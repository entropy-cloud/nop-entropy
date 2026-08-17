/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.mfa.store.EmailCodeStore;
import io.nop.auth.core.mfa.store.MfaChallengeStore;
import io.nop.auth.core.mfa.store.SmsCodeStore;
import io.nop.auth.service.mfa.store.MfaStoreErrors;

import java.util.Map;

/**
 * MFA 存储装配点（设计 §3.3 / §3.7 {@code nop.auth.mfa.store-type}）。
 * <p>
 * 声明式装配（ai-dev/lessons/15）：store 实现按命名型扩展点前缀注册
 * （{@code nopMfaChallengeStore_}/{@code nopSmsCodeStore_}/{@code nopEmailCodeStore_}），本类经
 * {@code ioc:collect-beans name-prefix} 收集（纯字符串匹配 bean id，<b>不加载类</b>），
 * 按 {@code store-type} 选择对应实现。新增实现只需注册同前缀 bean 即被自动收集，零消费者改动。
 * <p>
 * 选择规则：{@code local}/{@code db}（默认）/ {@code redis}（条件注册）；请求的类型未注册则
 * <b>显式抛异常</b>（fail-closed，不静默回退）。
 * <p>
 * <b>类加载安全</b>：本类零 {@code io.nop.nosql.core.INosqlService} 类型引用。Redis store 的 bean
 * 定义经 {@code ioc:condition}（{@code store-type=redis} + {@code on-class}）条件激活——classpath
 * 无 nosql 时根本不注册、不被收集、类不加载（教训 ai-dev/lessons/15）。
 * <p>
 * W15-impl 裁定（回写设计 §5.3.3）：EmailCodeStore 装配采用<b>同 provider 第三组 map</b>
 * （{@link #setEmailCodeStores}），不另立平行 EmailStoreProvider——三类 store 共享同一
 * {@code store-type} 选择语义（单部署单后端的一致性），改动面最小且语义等价。
 */
public class MfaStoreProvider {

    public static final String STORE_TYPE_LOCAL = "local";
    public static final String STORE_TYPE_DB = "db";
    public static final String STORE_TYPE_REDIS = "redis";

    private String storeType = STORE_TYPE_DB;

    private Map<String, MfaChallengeStore> challengeStores;
    private Map<String, SmsCodeStore> smsCodeStores;
    private Map<String, EmailCodeStore> emailCodeStores;

    public void setStoreType(String storeType) { this.storeType = storeType; }
    public String getStoreType() { return storeType; }
    public void setChallengeStores(Map<String, MfaChallengeStore> m) { this.challengeStores = m; }
    public Map<String, MfaChallengeStore> getChallengeStores() { return challengeStores; }
    public void setSmsCodeStores(Map<String, SmsCodeStore> m) { this.smsCodeStores = m; }
    public Map<String, SmsCodeStore> getSmsCodeStores() { return smsCodeStores; }
    public void setEmailCodeStores(Map<String, EmailCodeStore> m) { this.emailCodeStores = m; }
    public Map<String, EmailCodeStore> getEmailCodeStores() { return emailCodeStores; }

    public MfaChallengeStore getMfaChallengeStore() { return select(challengeStores, "MfaChallengeStore"); }
    public SmsCodeStore getSmsCodeStore() { return select(smsCodeStores, "SmsCodeStore"); }
    public EmailCodeStore getEmailCodeStore() { return select(emailCodeStores, "EmailCodeStore"); }

    private <T> T select(Map<String, T> stores, String storeName) {
        if (stores == null || stores.isEmpty())
            throw failClosed(storeName, "no store implementations registered");
        T store = lookup(stores, storeType);
        if (store == null)
            throw failClosed(storeName, "store-type=" + storeType + " not found among " + stores.keySet());
        return store;
    }

    private <T> T lookup(Map<String, T> stores, String type) {
        if (type == null) return null;
        T s = stores.get(type);
        if (s != null) return s;
        s = stores.get("_" + type);
        if (s != null) return s;
        for (Map.Entry<String, T> e : stores.entrySet()) {
            String key = e.getKey();
            if (key == null) continue;
            String normalized = key.startsWith("_") ? key.substring(1) : key;
            if (type.equalsIgnoreCase(key) || type.equalsIgnoreCase(normalized)) return e.getValue();
        }
        return null;
    }

    private NopException failClosed(String storeName, String reason) {
        return new NopException(MfaStoreErrors.ERR_MFA_STORE_REDIS_BACKEND_NOT_AVAILABLE)
                .param(MfaStoreErrors.ARG_STORE_TYPE, storeType)
                .param(MfaStoreErrors.ARG_REASON, storeName + ": " + reason);
    }
}
