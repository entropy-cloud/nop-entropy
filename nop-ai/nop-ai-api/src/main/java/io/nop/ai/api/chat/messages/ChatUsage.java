/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

/**
 * Token使用信息
 */
@DataBean
public class ChatUsage {

    /**
     * 输入的token数量
     */
    private Integer promptTokens;

    /**
     * 完成的token数量
     */
    private Integer completionTokens;

    /**
     * 总token数量
     */
    private Integer totalTokens;

    /**
     * 缓存命中的token数量（用于Prompt Caching）
     */
    private Integer cacheHitTokens;

    /**
     * 缓存创建的token数量（创建 Prompt Cache 时消耗的 token）
     */
    private Integer cacheCreationTokens;

    /**
     * 缓存未命中的token数量（需要重新计算的 token）
     */
    private Integer cacheMissTokens;

    public ChatUsage() {
    }

    /**
     * 便捷构造器（两字段形态）。{@link #totalTokens} 仅在两字段均非 null 时求和——
     * null token 字段是生产合法形态（{@code AbstractLlmDialect.parseUsage} 的路径缺失时
     * {@code getIntByPath} 返回 null），不得因拆箱 NPE。
     */
    public ChatUsage(Integer promptTokens, Integer completionTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = (promptTokens != null && completionTokens != null)
                ? promptTokens + completionTokens : null;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getCacheHitTokens() {
        return cacheHitTokens;
    }

    public void setCacheHitTokens(Integer cacheHitTokens) {
        this.cacheHitTokens = cacheHitTokens;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getCacheCreationTokens() {
        return cacheCreationTokens;
    }

    public void setCacheCreationTokens(Integer cacheCreationTokens) {
        this.cacheCreationTokens = cacheCreationTokens;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getCacheMissTokens() {
        return cacheMissTokens;
    }

    public void setCacheMissTokens(Integer cacheMissTokens) {
        this.cacheMissTokens = cacheMissTokens;
    }

    @JsonIgnore
    public double getCacheHitRate() {
        if (promptTokens == null || promptTokens == 0) return 0;
        return (cacheHitTokens != null ? cacheHitTokens : 0) / (double) promptTokens;
    }

    /**
     * 创建Token使用信息的深拷贝。
     * <p>
     * 逐字段复制：null token 字段 null 保真（不 NPE、不补 0）；{@link #totalTokens} 显式复制
     * provider 上报值而非重算——totalTokens 可能含缓存 token 统计（prompt+completion ≠ total），
     * 重算会丢信息（plan P2-ROUND4-CALL-PATH 裁定 A）。
     */
    public ChatUsage copy() {
        ChatUsage copy = new ChatUsage();
        copy.promptTokens = this.promptTokens;
        copy.completionTokens = this.completionTokens;
        copy.totalTokens = this.totalTokens;
        copy.setCacheHitTokens(this.cacheHitTokens);
        copy.setCacheCreationTokens(this.cacheCreationTokens);
        copy.setCacheMissTokens(this.cacheMissTokens);
        return copy;
    }
}
