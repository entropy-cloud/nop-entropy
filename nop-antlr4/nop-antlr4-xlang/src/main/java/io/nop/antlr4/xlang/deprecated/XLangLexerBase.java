/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 * Use of this source code is governed by the GNU AGPLv3 license that can be found in the LICENSE file.
 */
package io.nop.antlr4.xlang.deprecated;

import io.nop.api.core.util.SourceLocation;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

/**
 * 删除了对strictMode的判断
 * <p>
 * All lexer methods that used in grammar
 * should start with Upper Case Char similar to Lexer rules.
 */
public abstract class XLangLexerBase extends Lexer {

    private SourceLocation baseLoc = SourceLocation.fromClass(XLangLexerBase.class);
    private Token lastToken = null;

    public XLangLexerBase(CharStream input) {
        super(input);
    }

    public void setBaseLocation(SourceLocation loc) {
        this.baseLoc = loc;
    }

//    static final int BRACE = 1;
//    static final int PAREN = 2;
//    static final int BRACKET = 3;
//    static final int XPL_EXPR = 4;
//    static final int CP_EXPR = 5;
//
//    private boolean inXplExpr;
//    private int parenCount;
//
//    private final NestedProcessingState state = new NestedProcessingState(CFG_XLANG_ANTLR_MAX_NESTED_LEVEL.get());
//
//    protected void BeginCpExpr() {
//
//    }

//    protected void BeginXplExpr() {
//        if (inXplExpr) {
//            throw new NopEvalException(ERR_XLANG_XPL_EXPR_NOT_ALLOW_NESTED)
//                    .loc(baseLoc.position(lastToken.getLine(), lastToken.getCharPositionInLine(), lastToken.getText().length()));
//        }
//        inXplExpr = true;
//        pushMode(XLangLexer.XPL);
//    }
//
//    protected void EndXplExpr() {
//        if (!inXplExpr)
//            throw newError(ERR_XLANG_XPL_EXPR_BRACKET_NOT_MATCH);
//        inXplExpr = false;
//        if (_mode != XLangLexer.XPL)
//            throw newError(ERR_XLANG_XPL_EXPR_INVALID_MODE);
//        popMode();
//    }

    // #[ XName(XName=expr, XName=expr)]

//    protected void OnOpenParen() {
//        if (inXplExpr) {
//            parenCount++;
//        }
//    }
//
//    protected void OnCloseParen() {
//        if (inXplExpr) {
//            parenCount--;
//            if (parenCount < 0) {
//                throw newError(ERR_XLANG_XPL_EXPR_BRACKET_NOT_MATCH);
//            }
//            if (parenCount == 0) {
//                if (_mode == DEFAULT_MODE)
//                    popMode();
//            }
//        }
//    }

//    private NopException newError(ErrorCode errorCode) {
//        return new NopEvalException(ERR_XLANG_XPL_EXPR_BRACKET_NOT_MATCH)
//                .loc(baseLoc.position(lastToken.getLine(), lastToken.getCharPositionInLine(), lastToken.getText().length()));
//
//    }
//
//    protected void OnComma() {
//        if (inXplExpr) {
//            if (parenCount == 1) {
//                if (_mode == DEFAULT_MODE) {
//                    popMode();
//                }
//            }
//        }
//    }

    /**
     * Return the next token from the character stream and records this last
     * token in case it resides on the default channel. This recorded token
     * is used to determine when the lexer could possibly match a regex
     * literal. Also changes scopeStrictModes stack if tokenize special
     * string 'use strict';
     *
     * @return the next token from the character stream.
     */
    @Override
    public Token nextToken() {
        Token next = super.nextToken();

        if (next.getChannel() == Token.DEFAULT_CHANNEL) {
            // Keep track of the last token on the default channel.
            this.lastToken = next;
        }

        return next;
    }

    /**
     * Returns {@code true} if the lexer can match a regex literal.
     */
    protected boolean IsRegexPossible() {

        if (this.lastToken == null) {
            // No token has been produced yet: at the start of the input,
            // no division is possible, so a regex literal _is_ possible.
            return true;
        }

        switch (this.lastToken.getType()) {
            case XLangLexer.Identifier:
            case XLangLexer.NullLiteral:
            case XLangLexer.BooleanLiteral:
            case XLangLexer.This:
            case XLangLexer.CloseBracket:
            case XLangLexer.CloseParen:
                // case XLangLexer.OctalIntegerLiteral:
            case XLangLexer.DecimalLiteral:
            case XLangLexer.HexIntegerLiteral:
            case XLangLexer.StringLiteral:
            case XLangLexer.PlusPlus:
            case XLangLexer.MinusMinus:
                // After any of the tokens above, no regex literal can follow.
                return false;
            default:
                // In all other cases, a regex literal _is_ possible.
                return true;
        }
    }
}