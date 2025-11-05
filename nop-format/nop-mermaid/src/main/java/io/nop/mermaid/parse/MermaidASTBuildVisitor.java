/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.mermaid.parse;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.mermaid.ast.MermaidVisibility;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import static io.nop.antlr4.common.ParseTreeHelper.token;

@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class MermaidASTBuildVisitor extends _MermaidASTBuildVisitor {

    /**
     * rules: mermaidDocument
     */
    public io.nop.mermaid.ast.MermaidDiagramType MermaidDocument_type(ParseTree node) {
        return MermaidParseHelper.diagramType(token(node));
    }

    /**
     * rules: mermaidDirectionStatement
     */
    public io.nop.mermaid.ast.MermaidDirection MermaidDirectionStatement_direction(ParseTree node) {
        return MermaidParseHelper.direction(token(node));
    }

    /**
     * rules: mermaidFlowEdge
     */
    public io.nop.mermaid.ast.MermaidEdgeType MermaidFlowEdge_edgeType(ParseTree node) {
        return MermaidParseHelper.edgeType(token(node));
    }

    /**
     * rules: mermaidSequenceMessage
     */
    public io.nop.mermaid.ast.MermaidEdgeType MermaidSequenceMessage_edgeType(ParseTree node) {
        return MermaidParseHelper.edgeType(token(node));
    }

    /**
     * rules: mermaidFlowNode
     */
    public io.nop.mermaid.ast.MermaidNodeShape MermaidFlowNode_shape(ParseTree node) {
        return MermaidParseHelper.nodeShape(token(node));
    }

    /**
     * rules: mermaidClassMember
     */
    public java.lang.Boolean MermaidClassMember_isStatic(Token token) {
        return token != null;
    }

    /**
     * rules: mermaidPieItem
     */
    public java.lang.Number MermaidPieItem_value(Token token) {
        return ConvertHelper.toNumber(MermaidParseHelper.numberLiteralValue(token), NopException::new);
    }

    /**
     * rules: mermaidClassMember
     */
    public java.lang.String MermaidClassMember_name(Token token) {
        return MermaidParseHelper.identifierValue(token);
    }

    /**
     * rules: mermaidClassMember
     */
    public java.lang.String MermaidClassMember_type(Token token) {
        return MermaidParseHelper.identifierValue(token);
    }

    /**
     * rules: mermaidClassMember
     */
    public MermaidVisibility MermaidClassMember_visibility(Token token) {
        return MermaidParseHelper.visibilityValue(token);
    }

    /**
     * rules: mermaidClassNode
     */
    public java.lang.String MermaidClassNode_className(Token token) {
        return MermaidParseHelper.identifierValue(token);
    }

    /**
     * rules: mermaidComment
     */
    public java.lang.String MermaidComment_content(Token token) {
        return MermaidParseHelper.stringLiteralValue(token);
    }

    /**
     * rules: mermaidFlowEdge
     */
    public java.lang.String MermaidFlowEdge_from(Token token) {
        return MermaidParseHelper.identifierValue(token);
    }

    /**
     * rules: mermaidFlowEdge
     */
    public java.lang.String MermaidFlowEdge_label(Token token) {
        return MermaidParseHelper.stringLiteralValue(token);
    }

    /**
     * rules: mermaidFlowEdge
     */
    public java.lang.String MermaidFlowEdge_to(Token token) {
        return MermaidParseHelper.identifierValue(token);
    }

    /**
     * rules: mermaidFlowNode
     */
    public java.lang.String MermaidFlowNode_id(Token token) {
        return MermaidParseHelper.identifierValue(token);
    }

    /**
     * rules: mermaidFlowNode
     */
    public java.lang.String MermaidFlowNode_text(Token token) {
        return MermaidParseHelper.stringLiteralValue(token);
    }

    /**
     * rules: mermaidFlowSubgraph
     */
    public java.lang.String MermaidFlowSubgraph_id(Token token) {
        return MermaidParseHelper.identifierValue(token);
    }

    /**
     * rules: mermaidFlowSubgraph
     */
    public java.lang.String MermaidFlowSubgraph_title(Token token) {
        return MermaidParseHelper.stringLiteralValue(token);
    }

    /**
     * rules: mermaidGanttTask
     */
    public java.lang.String MermaidGanttTask_duration(Token token) {
        return MermaidParseHelper.stringLiteralValue(token);
    }

    /**
     * rules: mermaidGanttTask
     */
    public java.lang.String MermaidGanttTask_id(Token token) {
        return MermaidParseHelper.identifierValue(token);
    }

    /**
     * rules: mermaidGanttTask
     */
    public java.lang.String MermaidGanttTask_start(Token token) {
        return MermaidParseHelper.stringLiteralValue(token);
    }

    /**
     * rules: mermaidGanttTask
     */
    public java.lang.String MermaidGanttTask_title(Token token) {
        return MermaidParseHelper.stringLiteralValue(token);
    }

    /**
     * rules: mermaidParticipant
     */
    public java.lang.String MermaidParticipant_alias(Token token) {
        return MermaidParseHelper.stringLiteralValue(token);
    }

    /**
     * rules: mermaidParticipant
     */
    public java.lang.String MermaidParticipant_name(Token token) {
        return MermaidParseHelper.identifierValue(token);
    }

    /**
     * rules: mermaidPieItem
     */
    public java.lang.String MermaidPieItem_label(Token token) {
        return MermaidParseHelper.stringLiteralValue(token);
    }

    /**
     * rules: mermaidSequenceMessage
     */
    public java.lang.String MermaidSequenceMessage_from(Token token) {
        return MermaidParseHelper.identifierValue(token);
    }

    /**
     * rules: mermaidSequenceMessage
     */
    public java.lang.String MermaidSequenceMessage_message(Token token) {
        return MermaidParseHelper.stringLiteralValue(token);
    }

    /**
     * rules: mermaidSequenceMessage
     */
    public java.lang.String MermaidSequenceMessage_to(Token token) {
        return MermaidParseHelper.identifierValue(token);
    }

    /**
     * rules: mermaidStateNode
     */
    public java.lang.String MermaidStateNode_description(Token token) {
        return MermaidParseHelper.stringLiteralValue(token);
    }

    /**
     * rules: mermaidStateNode
     */
    public java.lang.String MermaidStateNode_id(Token token) {
        return MermaidParseHelper.identifierValue(token);
    }

    /**
     * rules: mermaidStyleAttribute
     */
    public java.lang.String MermaidStyleAttribute_name(Token token) {
        return MermaidParseHelper.identifierValue(token);
    }

    /**
     * rules: mermaidStyleAttribute
     */
    public java.lang.String MermaidStyleAttribute_value(Token token) {
        return MermaidParseHelper.stringLiteralValue(token);
    }

    /**
     * rules: mermaidStyleStatement
     */
    public java.lang.String MermaidStyleStatement_target(Token token) {
        return MermaidParseHelper.stringLiteralValue(token);
    }

}