/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.antlr4.common;

import io.nop.api.core.util.SourceLocation;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;

public class ParseTreeResult {
    private final Parser parser;
    private final SourceLocation baseLocation;
    private final ParseTree parseTree;

    public ParseTreeResult(Parser parser, SourceLocation loc, ParseTree parseTree) {
        this.parser = parser;
        this.baseLocation = loc;
        this.parseTree = parseTree;
    }

    public SourceLocation getBaseLocation() {
        return baseLocation;
    }

    public ParseTree getParseTree() {
        return parseTree;
    }

    public String getText() {
        return parseTree.getText();
    }

    public String toStringTree() {
        return parseTree.toStringTree(parser);
    }
}