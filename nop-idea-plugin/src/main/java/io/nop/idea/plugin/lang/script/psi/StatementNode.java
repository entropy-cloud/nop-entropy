package io.nop.idea.plugin.lang.script.psi;

import java.util.Map;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import io.nop.idea.plugin.lang.XLangVarDecl;
import org.jetbrains.annotations.NotNull;

/**
 * 赋值、声明、调用、if/switch 等语句
 * <p/>
 * 定义变量 <code>let b = 3;</code>：
 * <pre>
 * StatementNode(statement)
 *   VariableDeclarationNode(variableDeclaration)
 *     RuleSpecNode(varModifier_)
 *       PsiElement('let')('let')
 *     VariableDeclaratorsNode(variableDeclarators_)
 *       VariableDeclaratorNode(variableDeclarator)
 *         RuleSpecNode(ast_identifierOrPattern)
 *           IdentifierNode(identifier)
 *             PsiElement(Identifier)('b')
 *         RuleSpecNode(expression_initializer)
 *           PsiElement('=')('=')
 *           ExpressionNode(expression_single)
 *             LiteralNode(literal)
 *               RuleSpecNode(literal_numeric)
 *                 PsiElement(DecimalIntegerLiteral)('3')
 *     RuleSpecNode(eos__)
 *       PsiElement(';')(';')
 * </pre>
 *
 * 函数调用 <code>a(1, 2);</code>：
 * <pre>
 * StatementNode(statement)
 *   RuleSpecNode(expressionStatement)
 *     ExpressionNode(expression_single)
 *       ExpressionNode(expression_single)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('a')
 *       CalleeArgumentsNode(arguments_)
 *         PsiElement('(')('(')
 *         ExpressionNode(expression_single)
 *           LiteralNode(literal)
 *             RuleSpecNode(literal_numeric)
 *               PsiElement(DecimalIntegerLiteral)('1')
 *         PsiElement(',')(',')
 *         ExpressionNode(expression_single)
 *           LiteralNode(literal)
 *             RuleSpecNode(literal_numeric)
 *               PsiElement(DecimalIntegerLiteral)('2')
 *         PsiElement(')')(')')
 *     RuleSpecNode(eos__)
 *       PsiElement(';')(';')
 * </pre>
 *
 * 返回语句 <code>return a + b + c;</code>：
 * <pre>
 * StatementNode(statement)
 *   ReturnStatementNode(returnStatement)
 *     PsiElement('return')('return')
 *     ExpressionNode(expression_single)
 *       ExpressionNode(expression_single)
 *         ExpressionNode(expression_single)
 *           IdentifierNode(identifier)
 *             PsiElement(Identifier)('a')
 *         PsiElement('+')('+')
 *         ExpressionNode(expression_single)
 *           IdentifierNode(identifier)
 *             PsiElement(Identifier)('b')
 *       PsiElement('+')('+')
 *       ExpressionNode(expression_single)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('c')
 *     RuleSpecNode(eos__)
 *       PsiElement(';')(';')
 * </pre>
 *
 * 函数定义 <code>function fn2(a, b) { return a + b; }</code>：
 * <pre>
 * StatementNode(statement)
 *   FunctionDeclarationNode(functionDeclaration)
 *     PsiElement('function')('function')
 *     PsiWhiteSpace(' ')
 *     IdentifierNode(identifier)
 *       PsiElement(Identifier)('fn2')
 *     PsiElement('(')('(')
 *     RuleSpecNode(parameterList_)
 *       FunctionParameterDeclarationNode(parameterDeclaration)
 *         RuleSpecNode(ast_identifierOrPattern)
 *           IdentifierNode(identifier)
 *             PsiElement(Identifier)('a')
 *       PsiElement(',')(',')
 *       PsiWhiteSpace(' ')
 *       FunctionParameterDeclarationNode(parameterDeclaration)
 *         RuleSpecNode(ast_identifierOrPattern)
 *           IdentifierNode(identifier)
 *             PsiElement(Identifier)('b')
 *     PsiElement(')')(')')
 *     PsiWhiteSpace(' ')
 *     BlockStatementNode(blockStatement)
 *       PsiElement('{')('{')
 *       PsiWhiteSpace(' ')
 *       RuleSpecNode(statements_)
 *         StatementNode(statement)
 *           ReturnStatementNode(returnStatement)
 *             PsiElement('return')('return')
 *             PsiWhiteSpace(' ')
 *             ExpressionNode(expression_single)
 *               ExpressionNode(expression_single)
 *                 ExpressionNode(expression_single)
 *                   IdentifierNode(identifier)
 *                     PsiElement(Identifier)('a')
 *                 PsiWhiteSpace(' ')
 *                 PsiElement('+')('+')
 *                 PsiWhiteSpace(' ')
 *                 ExpressionNode(expression_single)
 *                   IdentifierNode(identifier)
 *                     PsiElement(Identifier)('b')
 *             RuleSpecNode(eos__)
 *               PsiElement(';')(';')
 *       PsiWhiteSpace(' ')
 *       PsiElement('}')('}')
 * </pre>
 *
 * 赋值语句 <code>xyz = "234";</code>：
 * <pre>
 * StatementNode(statement)
 *   RuleSpecNode(assignmentExpression)
 *     RuleSpecNode(expression_leftHandSide)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('xyz')
 *     PsiWhiteSpace(' ')
 *     RuleSpecNode(assignmentOperator_)
 *       PsiElement('=')('=')
 *     PsiWhiteSpace(' ')
 *     ExpressionNode(expression_single)
 *       LiteralNode(literal)
 *         RuleSpecNode(literal_string)
 *           PsiElement(StringLiteral)('"234"')
 *     RuleSpecNode(eos__)
 *       PsiElement(';')(';')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-03
 */
public class StatementNode extends RuleSpecNode {
    private VariableDeclarationNode[] varDeclaration;

    public StatementNode(@NotNull ASTNode node) {
        super(node);
    }

    /** 声明的变量，或者函数及其返回值类型，均为可访问变量 */
    @Override
    public @NotNull Map<String, XLangVarDecl> getVars() {
        PsiElement firstChild = getFirstChild();

        if (firstChild instanceof VariableDeclarationNode varDecl) {
            return varDecl.getVars();
        } //
        else if (firstChild instanceof FunctionDeclarationNode fnDecl) {
            IdentifierNode varNameNode = fnDecl.getFunctionNameNode();
            String varName = varNameNode.getText();
            PsiClass varType = fnDecl.getReturnType();

            XLangVarDecl varDecl = new XLangVarDecl(varType, varNameNode);

            return Map.of(varName, varDecl);
        } //
        else if (firstChild instanceof AssignmentExpressionNode ass) {
            IdentifierNode varNameNode = ass.getVarNameNode();
            // Note: 对于数组元素的赋值，不做处理
            if (varNameNode != null) {
                String varName = varNameNode.getText();
                PsiClass varType = ass.getVarType();

                XLangVarDecl varDecl = new XLangVarDecl(varType, varNameNode);

                return Map.of(varName, varDecl);
            }
        }

        return Map.of();
    }
}
