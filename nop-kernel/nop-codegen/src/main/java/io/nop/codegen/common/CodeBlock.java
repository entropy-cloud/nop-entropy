/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.common;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CodeBlock {
    private final SourceLocation loc;
    private StringBuilder buf;
    private String text;

    private CodeBlock parent;
    private List<CodeBlock> children = Collections.emptyList();

    public CodeBlock(SourceLocation loc) {
        this.loc = loc;
    }

    public CodeBlock(CodeBlock parent, SourceLocation loc) {
        this.parent = parent;
        this.loc = loc;
    }

    public SourceLocation getLoc() {
        return loc;
    }

    public CodeBlock append(String text) {
        if (StringHelper.isEmpty(text))
            return this;

        if (this.text == null) {
            this.text = text;
        } else {
            this.buf = new StringBuilder();
            this.buf.append(this.text);
            this.buf.append(text);
        }
        return this;
    }

    public String getText() {
        if (buf != null)
            return buf.toString();
        return text;
    }

    public String[] getLines() {
        return StringHelper.splitToLines(getText());
    }

    public void addChild(CodeBlock child) {
        if (child != null) {
            if (children.isEmpty()) {
                children = new ArrayList<>();
            }
            children.add(child);
        }
    }

    public CodeBlock beginChild(SourceLocation loc) {
        CodeBlock child = new CodeBlock(this, loc);
        addChild(child);
        return child;
    }

    public CodeBlock endChild() {
        return parent;
    }
}