/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.antlr4.tool.model;

import io.nop.core.lang.xml.XNode;

public class TerminalNode extends GrammarElement {
    /**
     * 代表性文本。对于正则匹配，这里为一个满足正则规则的最简单的文本
     */
    private String sampleText;

    private String text;

    private String name;

    public String toString() {
        return TerminalNode.class.getSimpleName() + "[" + name + "]";
    }

    public XNode toNode() {
        XNode node = super.toNode();
        node.setAttr("name", name);
        node.setAttr("text", text);
        return node;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSampleText() {
        return sampleText;
    }

    public void setSampleText(String sampleText) {
        this.sampleText = sampleText;
    }

    @Override
    public GrammarElementKind getKind() {
        return GrammarElementKind.TERMINAL;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}