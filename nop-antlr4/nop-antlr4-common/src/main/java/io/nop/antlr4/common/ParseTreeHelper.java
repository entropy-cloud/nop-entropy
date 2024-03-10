/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.antlr4.common;

import io.nop.api.core.util.SourceLocation;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

public class ParseTreeHelper {

    public static SourceLocation loc(Token token) {
        String path = token.getInputStream().getSourceName();
        int line = token.getLine();
        int col = token.getCharPositionInLine();
        int len = token.getText().length();
        return SourceLocation.fromLine(path, line, col, len);
    }

    public static SourceLocation loc(ParserRuleContext ctx) {
        Token start = ctx.getStart();
        String path = start.getInputStream().getSourceName();
        int line = start.getLine();
        int col = start.getCharPositionInLine();
        return SourceLocation.fromLine(path, line, col);
    }

    public static SourceLocation loc(ParseTree ctx) {
        if (ctx instanceof ParserRuleContext)
            return loc((ParserRuleContext) ctx);
        return loc((TerminalNode) ctx);
    }

    public static SourceLocation loc(TerminalNode node) {
        return loc(node.getSymbol());
    }

    public static TerminalNode terminalNode(ParseTree node) {
        if (node == null)
            return null;
        if (node instanceof TerminalNode)
            return (TerminalNode) node;

        if (node instanceof ParserRuleContext) {
            ParserRuleContext ctx = (ParserRuleContext) node;
            if (ctx.getChildCount() > 0) {
                for (int i = 0, n = ctx.getChildCount(); i < n; i++) {
                    ParseTree child = ctx.getChild(i);
                    if (child instanceof TerminalNode) {
                        return (TerminalNode) child;
                    } else if (child instanceof ParserRuleContext) {
                        return terminalNode(child);
                    }
                }
            }
        }
        return null;
    }

    public static String text(ParseTree node) {
        return node == null ? null : node.getText();
    }

    public static String text(Token token) {
        return token == null ? null : token.getText();
    }

    public static Token token(ParseTree node) {
        if (node instanceof Token)
            return (Token) node;
        TerminalNode terminalNode = terminalNode(node);
        if (terminalNode == null)
            return null;
        return terminalNode.getSymbol();
    }

    public static boolean isToken(TerminalNode node, int type) {
        return node != null && node.getSymbol().getType() == type;
    }

    public static boolean isToken(Token token, int type) {
        return token != null && token.getType() == type;
    }
}