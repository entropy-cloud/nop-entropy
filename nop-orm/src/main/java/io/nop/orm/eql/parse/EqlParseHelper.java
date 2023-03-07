/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.parse;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.orm.eql.enums.SqlOperator;
import io.nop.orm.eql.parse.antlr.EqlParser;
import org.antlr.v4.runtime.Token;

import static io.nop.antlr4.common.ParseTreeHelper.loc;
import static io.nop.xlang.XLangErrors.ARG_OP;
import static io.nop.xlang.XLangErrors.ERR_XLANG_UNSUPPORTED_OP;

public class EqlParseHelper {
    public static String stringLiteralValue(Token node) {
        String text = node.getText();
        String str = StringHelper.unescapeJava(text.substring(1, text.length() - 1));
        return str;
    }

    public static String numberLiteralValue(Token token) {
        return token.getText();
    }

    public static String bitLiteralValue(Token token) {
        String text = token.getText();
        if (text.startsWith("0b"))
            return text.substring(2);
        if (text.startsWith("B\'") || text.startsWith("b\'"))
            return text.substring(2, text.length() - 1);
        throw new IllegalStateException("nop.err.invalid-hex-literal:" + text);
    }

    public static String hexLiteralValue(Token node) {
        String text = node.getText();
        if (text.startsWith("0x"))
            return text.substring(2);
        if (text.startsWith("X\'") || text.startsWith("x\'"))
            return text.substring(2, text.length() - 1);
        throw new IllegalStateException("nop.err.invalid-hex-literal:" + text);
    }

    static NopException error(ErrorCode err, SourceLocation loc) {
        return new NopException(err).loc(loc);
    }

    public static SqlOperator operator(Token token) {
        switch (token.getType()) {
            case EqlParser.EQ_:
                return SqlOperator.EQ;
            case EqlParser.NEQ_:
                return SqlOperator.NE;
            // case EqlParser.NOT_:
            // return SqlOperator.BIT_NOT;
            case EqlParser.AND_:
                return SqlOperator.AND;
            // case EqlParser.OR_:
            // return SqlOperator.OR;
            case EqlParser.PLUS_:
                return SqlOperator.ADD;
            case EqlParser.MINUS_:
                return SqlOperator.MINUS;
            case EqlParser.ASTERISK_:
                return SqlOperator.MULTIPLY;
            case EqlParser.SLASH_:
                return SqlOperator.DIVIDE;
            case EqlParser.MOD_:
                return SqlOperator.MOD;
            case EqlParser.GTE_:
                return SqlOperator.GE;
            case EqlParser.GT_:
                return SqlOperator.GT;
            case EqlParser.LT_:
                return SqlOperator.LT;
            case EqlParser.LTE_:
                return SqlOperator.LE;
            case EqlParser.AMPERSAND_:
                return SqlOperator.BIT_AND;
            case EqlParser.VERTICAL_BAR_:
                return SqlOperator.BIT_OR;
            case EqlParser.TILDE_:
                return SqlOperator.BIT_NOT;
            case EqlParser.CARET_:
                return SqlOperator.BIT_XOR;
            case EqlParser.SIGNED_LEFT_SHIFT_:
                return SqlOperator.BIT_LEFT_SHIFT;
            case EqlParser.SIGNED_RIGHT_SHIFT_:
                return SqlOperator.BIT_RIGHT_SHIFT;
        }
        throw new NopException(ERR_XLANG_UNSUPPORTED_OP).param(ARG_OP, token.getText()).loc(loc(token));
    }
}
