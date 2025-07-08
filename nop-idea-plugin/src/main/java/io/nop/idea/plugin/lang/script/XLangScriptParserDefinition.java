package io.nop.idea.plugin.lang.script;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import io.nop.idea.plugin.lang.script.psi.ArrayExpressionNode;
import io.nop.idea.plugin.lang.script.psi.ArrowFunctionBodyNode;
import io.nop.idea.plugin.lang.script.psi.ArrowFunctionNode;
import io.nop.idea.plugin.lang.script.psi.AssignmentExpressionNode;
import io.nop.idea.plugin.lang.script.psi.BlockStatementNode;
import io.nop.idea.plugin.lang.script.psi.CalleeArgumentsNode;
import io.nop.idea.plugin.lang.script.psi.ExpressionNode;
import io.nop.idea.plugin.lang.script.psi.FunctionDeclarationNode;
import io.nop.idea.plugin.lang.script.psi.FunctionParameterDeclarationNode;
import io.nop.idea.plugin.lang.script.psi.FunctionParameterListNode;
import io.nop.idea.plugin.lang.script.psi.IdentifierNode;
import io.nop.idea.plugin.lang.script.psi.ImportDeclarationNode;
import io.nop.idea.plugin.lang.script.psi.ImportSourceNode;
import io.nop.idea.plugin.lang.script.psi.LiteralNode;
import io.nop.idea.plugin.lang.script.psi.ObjectDeclarationNode;
import io.nop.idea.plugin.lang.script.psi.ObjectMemberNode;
import io.nop.idea.plugin.lang.script.psi.ObjectPropertyAssignmentNode;
import io.nop.idea.plugin.lang.script.psi.ObjectPropertyDeclarationNode;
import io.nop.idea.plugin.lang.script.psi.ProgramNode;
import io.nop.idea.plugin.lang.script.psi.QualifiedNameNode;
import io.nop.idea.plugin.lang.script.psi.QualifiedNameRootNode;
import io.nop.idea.plugin.lang.script.psi.ReturnStatementNode;
import io.nop.idea.plugin.lang.script.psi.RuleSpecNode;
import io.nop.idea.plugin.lang.script.psi.StatementNode;
import io.nop.idea.plugin.lang.script.psi.TopLevelStatementNode;
import io.nop.idea.plugin.lang.script.psi.TypeNameNodePredefinedNode;
import io.nop.idea.plugin.lang.script.psi.VariableDeclarationNode;
import io.nop.idea.plugin.lang.script.psi.VariableDeclaratorNode;
import io.nop.idea.plugin.lang.script.psi.VariableDeclaratorsNode;
import io.nop.xlang.parse.antlr.XLangLexer;
import io.nop.xlang.parse.antlr.XLangParser;
import org.antlr.intellij.adaptor.lexer.PSIElementTypeFactory;
import org.antlr.intellij.adaptor.lexer.RuleIElementType;
import org.jetbrains.annotations.NotNull;

import static io.nop.idea.plugin.lang.script.XLangScriptTokenTypes.TOKEN_comment;
import static io.nop.idea.plugin.lang.script.XLangScriptTokenTypes.TOKEN_literal_string;
import static io.nop.idea.plugin.lang.script.XLangScriptTokenTypes.TOKEN_whitespace;

/**
 * 参考 https://github.com/antlr/antlr4-intellij-adaptor/blob/master/src/test/java/issue2/Issue2ParserDefinition.java
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-27
 */
public class XLangScriptParserDefinition implements ParserDefinition {
    public static final IFileElementType FILE = new IFileElementType(XLangScriptLanguage.INSTANCE);

    // Note: 确保在插件加载时，完成对 PSIElementTypeFactory 的初始化
    static {
        PSIElementTypeFactory.defineLanguageIElementTypes(XLangScriptLanguage.INSTANCE,
                                                          XLangLexer.tokenNames,
                                                          XLangParser.ruleNames);
    }

    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new XLangScriptLexerAdaptor();
    }

    @NotNull
    @Override
    public PsiParser createParser(Project project) {
        return new XLangScriptParserAdaptor();
    }

    /** Note: 只有在 {@link com.intellij.lang.ASTFactory ASTFactory} 中未创建 PsiElement 的节点才会调用该接口 */
    @NotNull
    @Override
    public PsiElement createElement(ASTNode node) {
        if (!(node.getElementType() instanceof RuleIElementType rule)) {
            return new RuleSpecNode(node);
        }

        return switch (rule.getRuleIndex()) {
            case XLangParser.RULE_program ->   //
                    new ProgramNode(node);
            case XLangParser.RULE_ast_topLevelStatement ->   //
                    new TopLevelStatementNode(node);
            case XLangParser.RULE_statement ->   //
                    new StatementNode(node);
            case XLangParser.RULE_identifier ->   //
                    new IdentifierNode(node);
            case XLangParser.RULE_literal ->   //
                    new LiteralNode(node);
            //
            case XLangParser.RULE_importAsDeclaration ->   //
                    new ImportDeclarationNode(node);
            case XLangParser.RULE_ast_importSource ->   //
                    new ImportSourceNode(node);
            case XLangParser.RULE_qualifiedName ->   //
                    new QualifiedNameNode(node);
            //
            case XLangParser.RULE_variableDeclaration ->   //
                    new VariableDeclarationNode(node);
            case XLangParser.RULE_variableDeclarators_ ->   //
                    new VariableDeclaratorsNode(node);
            case XLangParser.RULE_variableDeclarator ->   //
                    new VariableDeclaratorNode(node);
            case XLangParser.RULE_blockStatement ->   //
                    new BlockStatementNode(node);
            case XLangParser.RULE_assignmentExpression ->   //
                    new AssignmentExpressionNode(node);
            case XLangParser.RULE_arrayExpression ->   //
                    new ArrayExpressionNode(node);
            case XLangParser.RULE_typeNameNode_predefined ->   //
                    new TypeNameNodePredefinedNode(node);
            //
            case XLangParser.RULE_expression_single ->   //
                    new ExpressionNode(node);
            case XLangParser.RULE_objectExpression ->   //
                    new ObjectDeclarationNode(node);
            case XLangParser.RULE_ast_objectProperty ->   //
                    new ObjectPropertyDeclarationNode(node);
            case XLangParser.RULE_qualifiedName_ ->   //
                    new QualifiedNameRootNode(node);
            case XLangParser.RULE_arguments_ ->   //
                    new CalleeArgumentsNode(node);
            case XLangParser.RULE_identifier_ex ->   //
                    new ObjectMemberNode(node);
            case XLangParser.RULE_propertyAssignment ->   //
                    new ObjectPropertyAssignmentNode(node);
            //
            case XLangParser.RULE_functionDeclaration ->   //
                    new FunctionDeclarationNode(node);
            case XLangParser.RULE_parameterList_ ->   //
                    new FunctionParameterListNode(node);
            case XLangParser.RULE_parameterDeclaration ->   //
                    new FunctionParameterDeclarationNode(node);
            case XLangParser.RULE_arrowFunctionExpression ->   //
                    new ArrowFunctionNode(node);
            case XLangParser.RULE_expression_functionBody ->   //
                    new ArrowFunctionBodyNode(node);
            case XLangParser.RULE_returnStatement ->   //
                    new ReturnStatementNode(node);
            default -> new RuleSpecNode(node);
        };
    }

    @NotNull
    @Override
    public PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new XLangScriptFile(viewProvider);
    }

    @NotNull
    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }

    @NotNull
    @Override
    public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MAY;
    }

    @NotNull
    @Override
    public TokenSet getWhitespaceTokens() {
        return TOKEN_whitespace;
    }

    @NotNull
    @Override
    public TokenSet getCommentTokens() {
        return TOKEN_comment;
    }

    @NotNull
    @Override
    public TokenSet getStringLiteralElements() {
        return TOKEN_literal_string;
    }
}
