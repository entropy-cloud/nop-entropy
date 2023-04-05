/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.expr;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.CellRange;
import io.nop.core.model.table.utils.CellReferenceHelper;
import io.nop.report.core.coordinate.CellCoordinate;
import io.nop.report.core.coordinate.CellLayerCoordinate;
import io.nop.xlang.ast.CustomExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.MemberExpression;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.expr.ExprFeatures;
import io.nop.xlang.expr.simple.SimpleExprParser;

import java.util.ArrayList;
import java.util.List;

import static io.nop.report.core.XptErrors.ARG_CELL_POS;
import static io.nop.report.core.XptErrors.ERR_XPT_INVALID_CELL_RANGE_EXPR;

/**
 * 在XLang表达式的基础上增加CellCoordinate语法的支持。可以支持简单的Excel公式形式，例如 SUM(A3:A5) + C1
 */
public class ReportExpressionParser extends SimpleExprParser {
    public ReportExpressionParser() {
        setUseEvalException(true);
        enableFeatures(ExprFeatures.ALL);
    }

    @Override
    protected Expression varFactorExpr(TextScanner sc) {
        Identifier id = tokenExpr(sc);
        if (CellReferenceHelper.isABString(id.getName())) {
            if (sc.cur == ':') {
                return cellRangeExpr(sc, id);
            }
            return cellCoordinateExpr(sc, id);
        }
        return arrowFuncExpr(sc, id);
    }

    private Expression cellRangeExpr(TextScanner sc, Identifier id) {
        sc.consume(':');
        Identifier end = tokenExpr(sc);
        if (!CellReferenceHelper.isABString(id.getName()))
            throw sc.newError(ERR_XPT_INVALID_CELL_RANGE_EXPR)
                    .param(ARG_CELL_POS, end.getName());

        CellPosition first = CellPosition.fromABString(id.getName());
        CellPosition last = CellPosition.fromABString(end.getName());
        CellRange range = CellRange.fromPosition(first, last);
        SourceLocation loc = id.getLocation();
        return CustomExpression.build(loc, range.toString(), new CellRangeExecutable(loc, range));
    }

    public CellLayerCoordinate parseLayerCoordinate(SourceLocation loc, String text) {
        TextScanner sc = TextScanner.fromString(loc, text);
        sc.skipBlank();
        Identifier id = tokenExpr(sc);
        CellLayerCoordinate layerCoordinate = cellCoordinate(sc, id);
        sc.checkEnd();
        return layerCoordinate;
    }

    private CustomExpression cellCoordinateExpr(TextScanner sc, Identifier id) {
        SourceLocation loc = sc.location();
        CellLayerCoordinate layerCoordinate = cellCoordinate(sc, id);
        return CustomExpression.build(loc, layerCoordinate.toString(), new CellLayerCoordinateExecutable(loc, layerCoordinate));
    }

    private CellLayerCoordinate cellCoordinate(TextScanner sc, Identifier id) {
        //SourceLocation loc = sc.location();
        CellLayerCoordinate layerCoord = new CellLayerCoordinate();
        String cellName = id.getName();
        layerCoord.setCellName(cellName);

        if (sc.tryMatch('[')) {
            List<CellCoordinate> rowCoordinates = parseCellCoordinates(sc);
            List<CellCoordinate> colCoordinates = null;
            if (sc.tryMatch(';')) {
                colCoordinates = parseCellCoordinates(sc);
            }
            layerCoord.setRowCoordinates(rowCoordinates);
            layerCoord.setColCoordinates(colCoordinates);
            sc.match(']');
        }

        return layerCoord;
    }

    private List<CellCoordinate> parseCellCoordinates(TextScanner sc) {
        List<CellCoordinate> ret = new ArrayList<>();
        while (sc.cur != ';' && sc.cur != ']') {
            CellCoordinate coord = new CellCoordinate();
            String cellName = sc.nextJavaVar();
            coord.setCellName(cellName);
            if (sc.tryMatch(':')) {
                if (sc.tryMatch('!')) {
                    coord.setReverse(true);
                }
                if(sc.tryMatch('+')){
                    coord.setRelative(true);
                }
                int pos = sc.nextInt();
                if(pos < 0){
                    coord.setRelative(true);
                }
                coord.setPosition(pos);
            }
            ret.add(coord);
            if (!sc.tryMatch(',')) {
                break;
            }
        }
        return ret.isEmpty() ? null : ret;
    }

    private Expression checkValueExpr(Expression expr) {
        if (expr instanceof CustomExpression) {
            CustomExpression custom = (CustomExpression) expr;
            if (custom.getExecutable() instanceof CellLayerCoordinateExecutable) {
                MemberExpression member = new MemberExpression();
                member.setObject(expr);
                member.setProperty(Identifier.valueOf(null, "value"));
                member.setOptional(true);
                return member;
            }
        }
        return expr;
    }

    @Override
    protected Expression newBinaryExpr(SourceLocation loc, XLangOperator op, Expression x, Expression y) {
        return super.newBinaryExpr(loc, op, checkValueExpr(x), checkValueExpr(y));
    }

    @Override
    protected Expression newLogicExpr(SourceLocation loc, XLangOperator op, List<Expression> exprs, int startIndex) {
        for (int i = 0, n = exprs.size(); i < n; i++) {
            exprs.set(i, checkValueExpr(exprs.get(i)));
        }
        return super.newLogicExpr(loc, op, exprs, startIndex);
    }

    @Override
    protected Expression newUnaryExpr(SourceLocation loc, XLangOperator op, Expression x) {
        return super.newUnaryExpr(loc, op, checkValueExpr(x));
    }
}