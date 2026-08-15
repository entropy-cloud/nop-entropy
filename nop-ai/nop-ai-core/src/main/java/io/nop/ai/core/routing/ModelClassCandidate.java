package io.nop.ai.core.routing;

import java.util.Objects;

/**
 * 模型类候选（展开后的运行时视图，plan 2026-08-15-0849-2，设计 §3.3）。
 *
 * <p>由 {@code LlmConfigHelper.resolveModelClassCandidates} 在解析期把模型类声明的候选条目展开为
 * 具体候选：{@code accountRef} 缺省时展开为"主账号 + 该 provider 有序账号链"（主账号在前），
 * {@code accountRef} 配置时只展开为该账号；{@code model} 缺省时取 provider 的 defaultModel。
 *
 * <p><b>下沉语义</b>（供 W6/W7 编排消费）：选中候选可经 {@code ChatOptions} 四字段下沉——
 * {@code provider}/{@code model}/{@code accountKey}/{@code accountBaseUrl}；{@code accountKey}
 * 为 null = 主账号（沿用 {@code resolveApiKey} 凭证链），非 null = 备用账号 apiKey 直配值；
 * {@code accountBaseUrl} 为 null = 用 provider 根 baseUrl，非 null = per-account 覆盖。
 *
 * <p><b>并发上限</b>：{@code concurrencyLimit} 在解析期（候选展开）经
 * {@code LlmConfigHelper.resolveConcurrencyLimit(provider, accountOrNull)} 解析（W3 层级语义：
 * 账号级覆盖 → provider 级缺省 → null = 不限制；显式 0/负数 = 显式不限制不回退）——健康视图
 * 据此判断并发饱和，避免每次健康查询重复解析配置。
 *
 * <p>不可变值类型（value semantics——{@code equals}/{@code hashCode} 基于全部字段，
 * 供游走器"本轮已尝试候选"集合成员判断）。
 */
public final class ModelClassCandidate {
    private final String provider;
    private final String model;
    private final String accountKey;
    private final String accountBaseUrl;
    private final Integer concurrencyLimit;

    public ModelClassCandidate(String provider, String model, String accountKey, String accountBaseUrl,
                               Integer concurrencyLimit) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.model = Objects.requireNonNull(model, "model");
        this.accountKey = accountKey;
        this.accountBaseUrl = accountBaseUrl;
        this.concurrencyLimit = concurrencyLimit;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    /**
     * @return 备用账号 apiKey（直配值）；null = 主账号（沿用 {@code resolveApiKey} 凭证链）
     */
    public String getAccountKey() {
        return accountKey;
    }

    /**
     * @return per-account baseUrl 覆盖；null = 用 provider 根 baseUrl
     */
    public String getAccountBaseUrl() {
        return accountBaseUrl;
    }

    /**
     * @return 该候选的并发上限（解析期已按 W3 层级语义解析）；null/≤0 = 不限制
     */
    public Integer getConcurrencyLimit() {
        return concurrencyLimit;
    }

    /**
     * @return 熔断键 {@code provider:model}（与 {@code ThresholdBreaker} 消费键一致）
     */
    public String getModelKey() {
        return provider + ":" + model;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ModelClassCandidate)) {
            return false;
        }
        ModelClassCandidate that = (ModelClassCandidate) o;
        return provider.equals(that.provider) && model.equals(that.model)
                && Objects.equals(accountKey, that.accountKey)
                && Objects.equals(accountBaseUrl, that.accountBaseUrl)
                && Objects.equals(concurrencyLimit, that.concurrencyLimit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, model, accountKey, accountBaseUrl, concurrencyLimit);
    }

    @Override
    public String toString() {
        return "ModelClassCandidate{provider='" + provider + "', model='" + model
                + "', accountKey=" + (accountKey != null ? "***" : "null") + ", accountBaseUrl=" + accountBaseUrl
                + ", concurrencyLimit=" + concurrencyLimit + '}';
    }
}
