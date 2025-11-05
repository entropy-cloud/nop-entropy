/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.mermaid.parse;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.mermaid.ast.MermaidDiagramType;
import io.nop.mermaid.ast.MermaidDirection;
import io.nop.mermaid.ast.MermaidEdgeType;
import io.nop.mermaid.ast.MermaidNodeShape;
import io.nop.mermaid.ast.MermaidVisibility;
import io.nop.mermaid.parse.antlr.MermaidParser;
import org.antlr.v4.runtime.Token;

import static io.nop.antlr4.common.ParseTreeHelper.loc;
import static io.nop.mermaid.MermaidErrors.ARG_OP;
import static io.nop.mermaid.MermaidErrors.ERR_MERMAID_UNSUPPORTED_OP;

public class MermaidParseHelper {
    public static String stringLiteralValue(Token node) {
        String text = node.getText();
        String str = StringHelper.unescapeJava(text.substring(1, text.length() - 1));
        return str;
    }

    public static String numberLiteralValue(Token token) {
        return token.getText();
    }

    public static String identifierValue(Token token) {
        return token.getText();
    }

    static NopException error(ErrorCode err, SourceLocation loc) {
        return new NopException(err).loc(loc);
    }

    public static MermaidVisibility visibilityValue(Token token) {
        return MermaidVisibility.fromSymbol(token.getText());
    }

    public static MermaidDiagramType diagramType(Token token) {
        switch (token.getType()) {
            case MermaidParser.FLOWCHART:
                return MermaidDiagramType.FLOWCHART;
            case MermaidParser.SEQUENCE:
                return MermaidDiagramType.SEQUENCE;
            case MermaidParser.CLASS:
                return MermaidDiagramType.CLASS;
            case MermaidParser.STATE:
                return MermaidDiagramType.STATE;
            case MermaidParser.GANTT:
                return MermaidDiagramType.GANTT;
            case MermaidParser.PIE:
                return MermaidDiagramType.PIE;
            case MermaidParser.GIT:
                return MermaidDiagramType.GIT;
            case MermaidParser.ER:
                return MermaidDiagramType.ER;
            case MermaidParser.JOURNEY:
                return MermaidDiagramType.JOURNEY;
            default:
                throw new NopException(ERR_MERMAID_UNSUPPORTED_OP)
                        .param(ARG_OP, token.getText())
                        .loc(loc(token));
        }
    }

    public static MermaidDirection direction(Token token) {
        switch (token.getType()) {
            case MermaidParser.TB:
                return MermaidDirection.TB;
            case MermaidParser.BT:
                return MermaidDirection.BT;
            case MermaidParser.LR:
                return MermaidDirection.LR;
            case MermaidParser.RL:
                return MermaidDirection.RL;
            default:
                throw new NopException(ERR_MERMAID_UNSUPPORTED_OP)
                        .param(ARG_OP, token.getText())
                        .loc(loc(token));
        }
    }

    public static MermaidEdgeType edgeType(Token token) {
        switch (token.getType()) {
            case MermaidParser.ARROW:
                return MermaidEdgeType.ARROW;
            case MermaidParser.OPEN_ARROW:
                return MermaidEdgeType.OPEN_ARROW;
            case MermaidParser.DOTTED:
                return MermaidEdgeType.DOTTED;
            case MermaidParser.THICK:
                return MermaidEdgeType.THICK;
            default:
                throw new NopException(ERR_MERMAID_UNSUPPORTED_OP)
                        .param(ARG_OP, token.getText())
                        .loc(loc(token));
        }
    }

    public static MermaidNodeShape nodeShape(Token token) {
        switch (token.getType()) {
            case MermaidParser.ROUND:
                return MermaidNodeShape.ROUND;
            case MermaidParser.STADIUM:
                return MermaidNodeShape.STADIUM;
            case MermaidParser.SUBROUTINE:
                return MermaidNodeShape.SUBROUTINE;
            case MermaidParser.CYLINDER:
                return MermaidNodeShape.CYLINDER;
            case MermaidParser.CIRCLE:
                return MermaidNodeShape.CIRCLE;
            case MermaidParser.ASYMMETRIC:
                return MermaidNodeShape.ASYMMETRIC;
            case MermaidParser.RHOMBUS:
                return MermaidNodeShape.RHOMBUS;
            case MermaidParser.HEXAGON:
                return MermaidNodeShape.HEXAGON;
            case MermaidParser.PARALLELOGRAM:
                return MermaidNodeShape.PARALLELOGRAM;
            case MermaidParser.TRAPEZOID:
                return MermaidNodeShape.TRAPEZOID;
            case MermaidParser.DOUBLE_CIRCLE:
                return MermaidNodeShape.DOUBLE_CIRCLE;
            default:
                throw new NopException(ERR_MERMAID_UNSUPPORTED_OP)
                        .param(ARG_OP, token.getText())
                        .loc(loc(token));
        }
    }

}