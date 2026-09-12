package io.nop.ai.tools.sequential_thinking.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

/**
 * 2026-09-12 合规审计 AI-22 裁定：保留构造器参数上的 Jackson @JsonProperty——Nop 的
 * BeanModelBuilder（JsonTool/BeanTool Map→bean 构建）消费此注解做构造器参数命名，
 * 本 DTO 无默认构造器，删除后参数名将退化为 arg0/arg1。勿删。
 */
@DataBean
public class TagCount {
    private final String tag;
    private final long count;

    public TagCount(@JsonProperty("tag") String tag,
                    @JsonProperty("count") long count) {
        this.tag = tag;
        this.count = count;
    }

    public String getTag() {
        return tag;
    }

    public long getCount() {
        return count;
    }
}