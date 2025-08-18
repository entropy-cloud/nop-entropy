package io.nop.ai.tools.sequential_thinking.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

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