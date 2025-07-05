package io.nop.idea.plugin.lang.script.psi;

import java.util.HashMap;
import java.util.Map;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import io.nop.idea.plugin.lang.XLangVarDecl;
import org.jetbrains.annotations.NotNull;

/**
 * 函数（含箭头函数）参数列表
 * <p/>
 * 参数类型未知 <code>a, b</code>：
 * <pre>
 * FunctionParameterListNode(parameterList_)
 *   FunctionParameterDeclarationNode(parameterDeclaration)
 *     RuleSpecNode(ast_identifierOrPattern)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('a')
 *   PsiElement(',')(',')
 *   PsiWhiteSpace(' ')
 *   FunctionParameterDeclarationNode(parameterDeclaration)
 *     RuleSpecNode(ast_identifierOrPattern)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('b')
 * </pre>
 *
 * 含参数类型 <code>a: string, b: number</code>：
 * <pre>
 * FunctionParameterListNode(parameterList_)
 *   FunctionParameterDeclarationNode(parameterDeclaration)
 *     RuleSpecNode(ast_identifierOrPattern)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('a')
 *     RuleSpecNode(namedTypeNode_annotation)
 *       PsiElement(':')(':')
 *       PsiWhiteSpace(' ')
 *       RuleSpecNode(namedTypeNode)
 *         RuleSpecNode(typeNameNode_predefined)
 *           PsiElement('string')('string')
 *   PsiElement(',')(',')
 *   PsiWhiteSpace(' ')
 *   FunctionParameterDeclarationNode(parameterDeclaration)
 *     RuleSpecNode(ast_identifierOrPattern)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('b')
 *     RuleSpecNode(namedTypeNode_annotation)
 *       PsiElement(':')(':')
 *       PsiWhiteSpace(' ')
 *       RuleSpecNode(namedTypeNode)
 *         RuleSpecNode(typeNameNode_predefined)
 *           PsiElement('number')('number')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-05
 */
public class FunctionParameterListNode extends RuleSpecNode {
    // Note: 在 FunctionParameterDeclarationNode 中构造对参数类型的引用

    public FunctionParameterListNode(@NotNull ASTNode node) {
        super(node);
    }

    /** 参数列表为函数内可访问的变量 */
    @Override
    public @NotNull Map<String, XLangVarDecl> getVars() {
        FunctionParameterDeclarationNode[] paramDecls = findChildrenByClass(FunctionParameterDeclarationNode.class);

        Map<String, XLangVarDecl> vars = new HashMap<>();
        for (FunctionParameterDeclarationNode paramDecl : paramDecls) {
            IdentifierNode paramName = paramDecl.getParameterName();
            PsiClass paramType = paramDecl.getParameterType();

            vars.put(paramName.getText(), new XLangVarDecl(paramType, paramName));
        }

        return vars;
    }
}
