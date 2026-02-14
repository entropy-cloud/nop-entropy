/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.messages;

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

    public ChatUsage() {
    }

    public ChatUsage(Integer promptTokens, Integer completionTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = promptTokens + completionTokens;
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

    /**
     * 创建Token使用信息的深拷贝
     */
    public ChatUsage copy() {
        return new ChatUsage(this.promptTokens, this.completionTokens);
    }
}
