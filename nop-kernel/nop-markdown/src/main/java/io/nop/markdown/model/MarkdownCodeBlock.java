/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.markdown.model;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.SourceLocation;

@DataBean
public class MarkdownCodeBlock extends MarkdownNode {
    private String lang;
    private String source;

    public static MarkdownCodeBlock build(SourceLocation loc, String type, String source) {
        MarkdownCodeBlock block = new MarkdownCodeBlock();
        block.setLocation(loc);
        block.setLang(type);
        block.setSource(source);
        return block;
    }

    public String toString(){
        return toText();
    }

    @Override
    protected void buildText(StringBuilder sb, MarkdownTextOptions options) {
        sb.append("```").append(lang).append("\n\n");
        if (source != null)
            sb.append(source);
        sb.append("\n```\n");
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
