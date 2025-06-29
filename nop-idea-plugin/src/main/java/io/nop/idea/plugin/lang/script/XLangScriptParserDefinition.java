package io.nop.idea.plugin.lang.script;

import java.util.List;

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
import io.nop.idea.plugin.lang.script.psi.ImportAsDeclarationNode;
import io.nop.idea.plugin.lang.script.psi.ImportQualifiedNameNode;
import io.nop.idea.plugin.lang.script.psi.ImportSourceNode;
import io.nop.idea.plugin.lang.script.psi.ProgramNode;
import io.nop.idea.plugin.lang.script.psi.RuleSpecNode;
import io.nop.xlang.parse.antlr.XLangLexer;
import io.nop.xlang.parse.antlr.XLangParser;
import org.antlr.intellij.adaptor.lexer.PSIElementTypeFactory;
import org.antlr.intellij.adaptor.lexer.RuleIElementType;
import org.antlr.intellij.adaptor.lexer.TokenIElementType;
import org.jetbrains.annotations.NotNull;

/**
 * 参考 https://github.com/antlr/antlr4-intellij-adaptor/blob/master/src/test/java/issue2/Issue2ParserDefinition.java
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-27
 */
public class XLangScriptParserDefinition implements ParserDefinition {
    public static final IFileElementType FILE = new IFileElementType(XLangScriptLanguage.INSTANCE);

    public static TokenIElementType ID;

    static {
        PSIElementTypeFactory.defineLanguageIElementTypes(XLangScriptLanguage.INSTANCE,
                                                          XLangLexer.tokenNames,
                                                          XLangParser.ruleNames);
        List<TokenIElementType> tokenIElementTypes
                = PSIElementTypeFactory.getTokenIElementTypes(XLangScriptLanguage.INSTANCE);

        ID = tokenIElementTypes.get(XLangLexer.Identifier);
    }

    public static final TokenSet COMMENTS = PSIElementTypeFactory.createTokenSet(XLangScriptLanguage.INSTANCE,
                                                                                 XLangLexer.SingleLineComment,
                                                                                 XLangLexer.MultiLineComment);

    public static final TokenSet WHITESPACE = PSIElementTypeFactory.createTokenSet(XLangScriptLanguage.INSTANCE,
                                                                                   XLangLexer.WhiteSpaces,
                                                                                   XLangLexer.LineTerminator);

    public static final TokenSet STRING = PSIElementTypeFactory.createTokenSet(XLangScriptLanguage.INSTANCE,
                                                                               XLangLexer.StringLiteral,
                                                                               XLangLexer.TemplateStringLiteral);

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

    @NotNull
    @Override
    public PsiElement createElement(ASTNode node) {
        if (!(node.getElementType() instanceof RuleIElementType rule)) {
            return new RuleSpecNode(node);
        }

        // Note: 只有在 ASTFactory 中未创建 PsiElement 的节点才会调用该接口
        return switch (rule.getRuleIndex()) {
            case XLangParser.RULE_importAsDeclaration ->   //
                    new ImportAsDeclarationNode(node);
            case XLangParser.RULE_ast_importSource ->   //
                    new ImportSourceNode(node);
            case XLangParser.RULE_qualifiedName ->   //
                    new ImportQualifiedNameNode(node);
            case XLangParser.RULE_program ->   //
                    new ProgramNode(node);
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
        return WHITESPACE;
    }

    @NotNull
    @Override
    public TokenSet getCommentTokens() {
        return COMMENTS;
    }

    @NotNull
    @Override
    public TokenSet getStringLiteralElements() {
        return STRING;
    }
}
