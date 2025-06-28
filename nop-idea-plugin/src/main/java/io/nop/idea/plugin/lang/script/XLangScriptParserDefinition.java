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
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import io.nop.xlang.parse.antlr.XLangLexer;
import io.nop.xlang.parse.antlr.XLangParser;
import org.antlr.intellij.adaptor.lexer.PSIElementTypeFactory;
import org.antlr.intellij.adaptor.lexer.TokenIElementType;
import org.antlr.intellij.adaptor.psi.ANTLRPsiNode;
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
        IElementType type = node.getElementType();
        if (type instanceof JavaStubElementType) {
            return ((JavaStubElementType<?, ?>) type).createPsi(node);
        }

        //throw new IllegalArgumentException("Not a Java node: " + node + " (" + type + ", " + type.getLanguage() + ")");
        return new ANTLRPsiNode(node);
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
