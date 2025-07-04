package io.nop.idea.plugin.lang.script.psi;

import java.util.Map;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import io.nop.idea.plugin.lang.XLangVarDecl;
import org.jetbrains.annotations.NotNull;

/**
 * 函数声明节点
 * <p/>
 * <code>function fn(a, b) { return a + b; }</code>：
 * <pre>
 * FunctionDeclarationNode(functionDeclaration)
 *   PsiElement('function')('function')
 *   PsiWhiteSpace(' ')
 *   IdentifierNode(identifier)
 *     PsiElement(Identifier)('fn')
 *   PsiElement('(')('(')
 *   RuleSpecNode(parameterList_)
 *     FunctionParameterDeclarationNode(parameterDeclaration)
 *       RuleSpecNode(ast_identifierOrPattern)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('a')
 *     PsiElement(',')(',')
 *     PsiWhiteSpace(' ')
 *     FunctionParameterDeclarationNode(parameterDeclaration)
 *       RuleSpecNode(ast_identifierOrPattern)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('b')
 *   PsiElement(')')(')')
 *   PsiWhiteSpace(' ')
 *   BlockStatementNode(blockStatement)
 *     PsiElement('{')('{')
 *     PsiWhiteSpace(' ')
 *     RuleSpecNode(statements_)
 *       StatementNode(statement)
 *         ReturnStatementNode(returnStatement)
 *           PsiElement('return')('return')
 *           PsiWhiteSpace(' ')
 *           ExpressionNode(expression_single)
 *             ExpressionNode(expression_single)
 *               ExpressionNode(expression_single)
 *                 IdentifierNode(identifier)
 *                   PsiElement(Identifier)('a')
 *               PsiWhiteSpace(' ')
 *               PsiElement('+')('+')
 *               PsiWhiteSpace(' ')
 *               ExpressionNode(expression_single)
 *                 IdentifierNode(identifier)
 *                   PsiElement(Identifier)('b')
 *           RuleSpecNode(eos__)
 *             PsiElement(';')(';')
 *     PsiWhiteSpace(' ')
 *     PsiElement('}')('}')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class FunctionDeclarationNode extends RuleSpecNode {
    private PsiClass[] returnType;

    public FunctionDeclarationNode(@NotNull ASTNode node) {
        super(node);
    }

    public IdentifierNode getFunctionNameNode() {
        return findChildByClass(IdentifierNode.class);
    }

    /** 获取函数的返回值类型 */
    public PsiClass getReturnType() {
        // TODO 分析函数的 return 表达式，得到返回类型
        if (returnType == null) {
            returnType = new PsiClass[] {};
        }
        return returnType[0];
    }

    /** 参数列表为函数内可访问的变量 */
    @Override
    public @NotNull Map<String, XLangVarDecl> getVars() {
        // TODO 分析参数列表，得到参数变量及其类型

        return Map.of();
    }
}
