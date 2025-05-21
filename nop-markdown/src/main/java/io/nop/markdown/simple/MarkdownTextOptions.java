package io.nop.markdown.simple;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class MarkdownTextOptions {
    private boolean includeTags;

    public boolean isIncludeTags() {
        return includeTags;
    }

    public void setIncludeTags(boolean includeTags) {
        this.includeTags = includeTags;
    }

    public MarkdownTextOptions includeTags(boolean includeTags) {
        this.includeTags = includeTags;
        return this;
    }
}
