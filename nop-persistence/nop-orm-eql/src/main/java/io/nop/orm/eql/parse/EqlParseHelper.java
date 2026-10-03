/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.orm.eql.ast.SqlIntervalExpr;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.enums.SqlIntervalUnit;
import io.nop.orm.eql.enums.SqlOperator;
import io.nop.orm.eql.parse.antlr.EqlParser;
import org.antlr.v4.runtime.Token;

import static io.nop.antlr4.common.ParseTreeHelper.loc;
import static io.nop.orm.eql.OrmEqlErrors.ARG_VALUE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_BIT_LITERAL;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_HEX_LITERAL;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_INTERVAL_VALUE;
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
        throw new NopException(ERR_EQL_INVALID_BIT_LITERAL).param(ARG_VALUE, text).loc(loc(token));
    }

    public static String hexLiteralValue(Token node) {
        String text = node.getText();
        if (text.startsWith("0x"))
            return text.substring(2);
        if (text.startsWith("X\'") || text.startsWith("x\'"))
            return text.substring(2, text.length() - 1);
        throw new NopException(ERR_EQL_INVALID_HEX_LITERAL).param(ARG_VALUE, text).loc(loc(node));
    }

    /**
     * WI17: extracts a positive duration in milliseconds from an {@code INTERVAL
     * <n> <unit>} literal (the TUMBLE pseudo-table function's window size). The
     * value must be an integer literal and the unit must have a fixed millis
     * duration: MICROSECOND is accepted only when the value is a whole number of
     * millis; calendar units MONTH/QUARTER/YEAR have no fixed millis duration and
     * fail fast (a stream window size can never depend on the calendar).
     * Non-literal, non-integral, zero and negative values all fail fast with
     * {@code nop.err.eql.invalid-interval-value} — never a silent approximation.
     */
    public static long intervalDurationMillis(SqlIntervalExpr expr) {
        if (expr == null || !(expr.getExpr() instanceof SqlNumberLiteral))
            throw invalidInterval(expr);
        String text = ((SqlNumberLiteral) expr.getExpr()).getValue();
        long value;
        try {
            value = Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            throw invalidInterval(expr);
        }
        if (value <= 0)
            throw invalidInterval(expr);
        SqlIntervalUnit unit = expr.getIntervalUnit();
        if (unit == null)
            throw invalidInterval(expr);
        switch (unit) {
            case MICROSECOND:
                if (value % 1000 != 0)
                    throw invalidInterval(expr);
                return value / 1000;
            case SECOND:
                return value * 1000L;
            case MINUTE:
                return value * 60_000L;
            case HOUR:
                return value * 3_600_000L;
            case DAY:
                return value * 86_400_000L;
            case WEEK:
                return value * 604_800_000L;
            default:
                // MONTH / QUARTER / YEAR: calendar-dependent, no fixed duration
                throw invalidInterval(expr);
        }
    }

    static NopException invalidInterval(SqlIntervalExpr expr) {
        String value;
        if (expr == null) {
            value = "null";
        } else {
            String num = expr.getExpr() instanceof SqlNumberLiteral
                    ? ((SqlNumberLiteral) expr.getExpr()).getValue()
                    : String.valueOf(expr.getExpr());
            value = "INTERVAL " + num + " " + expr.getIntervalUnit();
        }
        return new NopException(ERR_EQL_INVALID_INTERVAL_VALUE).param(ARG_VALUE, value);
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
