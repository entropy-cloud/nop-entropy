/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

import static io.nop.idea.plugin.lang.script.XLangScriptTokenTypes.RULE_namedTypeNode_annotation;

/**
 * 函数参数定义节点
 * <p/>
 * <pre>
 * FunctionParameterDeclarationNode(parameterDeclaration)
 *   RuleSpecNode(ast_identifierOrPattern)
 *     IdentifierNode(identifier)
 *       PsiElement(Identifier)('a')
 * </pre>
 *
 * <pre>
 * FunctionParameterDeclarationNode(parameterDeclaration)
 *   RuleSpecNode(ast_identifierOrPattern)
 *     IdentifierNode(identifier)
 *       PsiElement(Identifier)('a')
 *   RuleSpecNode(namedTypeNode_annotation)
 *     PsiElement(':')(':')
 *     PsiWhiteSpace(' ')
 *     RuleSpecNode(namedTypeNode)
 *       RuleSpecNode(typeNameNode_predefined)
 *         PsiElement('string')('string')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class FunctionParameterDeclarationNode extends RuleSpecNode {

    public FunctionParameterDeclarationNode(@NotNull ASTNode node) {
        super(node);
    }

    public IdentifierNode getParameterName() {
        return (IdentifierNode) getFirstChild().getFirstChild();
    }

    public PsiClass getParameterType() {
        RuleSpecNode typeNode = findChildByType(RULE_namedTypeNode_annotation);
        if (typeNode == null) {
            return null;
        }

        RuleSpecNode type = (RuleSpecNode) typeNode.getLastChild().getFirstChild();

        if (type instanceof TypeNameNodePredefinedNode tp) {
            return tp.getPredefinedType();
        } //
        else if (type instanceof QualifiedNameRootNode qnr) {
            return qnr.getQualifiedType();
        }
        return null;
    }
}
