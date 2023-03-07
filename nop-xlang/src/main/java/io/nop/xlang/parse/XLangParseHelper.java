/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.parse;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.XLangErrors;
import io.nop.xlang.ast.VariableKind;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.parse.antlr.XLangParser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import static io.nop.antlr4.common.ParseTreeHelper.loc;
import static io.nop.xlang.XLangErrors.ARG_OP;
import static io.nop.xlang.XLangErrors.ARG_TARGET_TYPE;
import static io.nop.xlang.XLangErrors.ERR_XLANG_UNSUPPORTED_OP;

public class XLangParseHelper {

    public static Object literalValue(TerminalNode node) {
        switch (node.getSymbol().getType()) {
            case XLangParser.RegularExpressionLiteral:
                return regexLiteralValue(node);
            case XLangParser.DecimalLiteral:
                return decimalLiteralValue(node);
            case XLangParser.HexIntegerLiteral:
                return hexIntegerLiteralValue(node);
            case XLangParser.BinaryIntegerLiteral:
                return binaryIntegerValue(node);
            case XLangParser.DecimalIntegerLiteral:
                return decimalIntegerLiteralValue(node);
            case XLangParser.StringLiteral:
                return stringLiteralValue(node);
            case XLangParser.TemplateStringLiteral:
                return templateStringLiteralValue(node);
            case XLangParser.NullLiteral:
                return nullLiteralValue(node);
            case XLangParser.BooleanLiteral:
                return booleanLiteralValue(node);
            default:
                throw error(XLangErrors.ERR_XLANG_INVALID_PARSE_TREE, loc(node)).param(XLangErrors.ARG_PARSE_TREE, node);
        }
    }

    public static String regexLiteralValue(TerminalNode node) {
        String text = node.getText();
        int pos = text.lastIndexOf('/');
        // String flags = text.substring(pos + 1);
        text = text.substring(1, pos);

        return text;
    }

    public static boolean booleanLiteralValue(TerminalNode node) {
        boolean value = ConvertHelper.stringToBoolean(node.getText(), NopEvalException::new);
        return value;
    }

    public static Object nullLiteralValue(TerminalNode node) {
        return null;
    }

    public static String stringLiteralValue(TerminalNode node) {
        String text = node.getText();
        String str = StringHelper.unescapeJava(text.substring(1, text.length() - 1));
        return str;
    }

    public static String templateStringLiteralValue(TerminalNode node) {
        return templateStringLiteralValue(node.getSymbol());
    }

    public static String templateStringLiteralValue(Token node) {
        String text = node.getText();
        String str = StringHelper.unquoteDupEscapeString(text);
        return str;
    }

    public static Number decimalIntegerLiteralValue(TerminalNode node) {
        String text = node.getText();
        text = StringHelper.replace(text, "_", "");

        if (text.endsWith("L")) {
            Long value = ConvertHelper.stringToLong(text.substring(0, text.length() - 1), err -> error(err, loc(node)));
            return value;
        }
        Integer value = ConvertHelper.stringToInt(text, err -> error(err, loc(node)));
        return value;
    }

    static NopException error(ErrorCode err, SourceLocation loc) {
        return new NopException(err).loc(loc);
    }

    public static Number hexIntegerLiteralValue(TerminalNode node) {
        String text = node.getText();
        text = text.substring(2);
        text = StringHelper.replace(text, "_", "");

        Number value;
        try {
            if (text.endsWith("L")) {
                value = Long.parseLong(text.substring(0, text.length() - 1), 16);
            } else {
                value = Long.parseLong(text, 16);
            }
        } catch (Exception e) {
            throw error(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, loc(node)).param(ARG_TARGET_TYPE, Long.class).param(ApiErrors.ARG_VALUE, text);
        }
        return value;
    }

    public static Number binaryIntegerValue(TerminalNode node) {
        String text = node.getText();
        text = text.substring(2);
        text = StringHelper.replace(text, "_", "");

        Number value;
        try {
            if (text.endsWith("L")) {
                value = Long.parseUnsignedLong(text.substring(0, text.length() - 1), 2);
            } else {
                value = Long.parseUnsignedLong(text, 2);
            }
        } catch (Exception e) {
            throw error(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, loc(node))
                    .param(ARG_TARGET_TYPE, Long.class).param(ApiErrors.ARG_VALUE, text);
        }
        return value;
    }

    public static Number decimalLiteralValue(TerminalNode node) {
        String text = node.getText();
        text = StringHelper.replace(text, "_", "");
        Number value = ConvertHelper.stringToDouble(text, err -> error(err, loc(node)));
        return value;
    }

    public static VariableKind variableKind(Token node) {
        int type = node.getType();
        return type == XLangParser.Const ? VariableKind.CONST : VariableKind.LET;
    }

    public static XLangOperator operator(Token token) {
        switch (token.getType()) {
            case XLangParser.Equals_:
                return XLangOperator.EQ;
            case XLangParser.NotEquals:
                return XLangOperator.NE;
            case XLangParser.Not:
                return XLangOperator.NOT;
            case XLangParser.And:
            case XLangParser.AndLiteral:
                return XLangOperator.AND;
            case XLangParser.Or:
            case XLangParser.OrLiteral:
                return XLangOperator.OR;
            case XLangParser.Arrow:
                return XLangOperator.ARROW;
            case XLangParser.Assign:
                return XLangOperator.ASSIGN;
            case XLangParser.Plus:
                return XLangOperator.ADD;
            case XLangParser.Minus:
                return XLangOperator.MINUS;
            case XLangParser.Multiply:
                return XLangOperator.MULTIPLY;
            case XLangParser.Divide:
                return XLangOperator.DIVIDE;
            case XLangParser.Modulus:
                return XLangOperator.MOD;
            case XLangParser.PlusPlus:
                return XLangOperator.SELF_INC;
            case XLangParser.MinusMinus:
                return XLangOperator.SELF_DEC;
            case XLangParser.GreaterThanEquals:
                return XLangOperator.GE;
            case XLangParser.MoreThan:
                return XLangOperator.GT;
            case XLangParser.LessThan:
                return XLangOperator.LT;
            case XLangParser.LessThanEquals:
                return XLangOperator.LE;
            case XLangParser.BitAnd:
                return XLangOperator.BIT_AND;
            case XLangParser.BitOr:
                return XLangOperator.BIT_OR;
            case XLangParser.BitNot:
                return XLangOperator.BIT_NOT;
            case XLangParser.BitXOr:
                return XLangOperator.BIT_XOR;
            case XLangParser.BitAndAssign:
                return XLangOperator.SELF_ASSIGN_BIT_AND;
            case XLangParser.BitOrAssign:
                return XLangOperator.SELF_ASSIGN_BIT_OR;
            case XLangParser.BitXorAssign:
                return XLangOperator.SELF_ASSIGN_BIT_XOR;
            case XLangParser.LeftShiftArithmetic:
                return XLangOperator.BIT_LEFT_SHIFT;
            case XLangParser.RightShiftArithmetic:
                return XLangOperator.BIT_RIGHT_SHIFT;
            case XLangParser.RightShiftLogical:
                return XLangOperator.BIT_UNSIGNED_RIGHT_SHIFT;
            case XLangParser.LeftShiftArithmeticAssign:
                return XLangOperator.SELF_ASSIGN_LEFT_SHIFT;
            case XLangParser.RightShiftArithmeticAssign:
                return XLangOperator.SELF_ASSIGN_RIGHT_SHIFT;
            case XLangParser.RightShiftLogicalAssign:
                return XLangOperator.SELF_ASSIGN_UNSIGNED_RIGHT_SHIFT;
            case XLangParser.NullCoalesce:
                return XLangOperator.NULL_COALESCE;
            case XLangParser.PlusAssign:
                return XLangOperator.SELF_ASSIGN_ADD;
            case XLangParser.MinusAssign:
                return XLangOperator.SELF_ASSIGN_MINUS;
            case XLangParser.MultiplyAssign:
                return XLangOperator.SELF_ASSIGN_MULTI;
            case XLangParser.DivideAssign:
                return XLangOperator.SELF_ASSIGN_DIV;
            case XLangParser.ModulusAssign:
                return XLangOperator.SELF_ASSIGN_MOD;
        }
        throw new NopException(ERR_XLANG_UNSUPPORTED_OP).param(ARG_OP, token.getText()).loc(loc(token));
    }
}
