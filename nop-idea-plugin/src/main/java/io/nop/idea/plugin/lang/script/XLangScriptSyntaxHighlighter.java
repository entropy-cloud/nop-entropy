package io.nop.idea.plugin.lang.script;

import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import io.nop.xlang.parse.antlr.XLangLexer;
import io.nop.xlang.parse.antlr.XLangParser;
import org.antlr.intellij.adaptor.lexer.PSIElementTypeFactory;
import org.antlr.intellij.adaptor.lexer.TokenIElementType;
import org.jetbrains.annotations.NotNull;

/**
 * 参考：https://github.com/antlr/jetbrains-plugin-sample/blob/master/src/main/java/org/antlr/jetbrains/sample/SampleSyntaxHighlighter.java
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-27
 */
public class XLangScriptSyntaxHighlighter extends SyntaxHighlighterBase {

    static {
        PSIElementTypeFactory.defineLanguageIElementTypes(XLangScriptLanguage.INSTANCE,
                                                          XLangLexer.tokenNames,
                                                          XLangParser.ruleNames);
    }

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new XLangScriptLexerAdaptor();
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        if (!(tokenType instanceof TokenIElementType myType)) {
            return TextAttributesKey.EMPTY_ARRAY;
        }

        TextAttributesKey attrKey = switch (myType.getANTLRTokenType()) {
            case XLangLexer.Identifier -> //
                    DefaultLanguageHighlighterColors.IDENTIFIER;
            case XLangLexer.Const, XLangLexer.Let, XLangLexer.While, //
                 XLangLexer.If, XLangLexer.Else, XLangLexer.Return, //
                 XLangLexer.Import, XLangLexer.Function, XLangLexer.New, //
                 XLangLexer.Boolean, XLangLexer.NullLiteral, XLangLexer.BooleanLiteral, //
                 XLangLexer.AndLiteral, XLangLexer.OrLiteral  //
                    -> //
                    JavaHighlightingColors.KEYWORD;
            case XLangLexer.StringLiteral, XLangLexer.TemplateStringLiteral //
                    -> //
                    JavaHighlightingColors.STRING;
            case XLangLexer.DecimalIntegerLiteral, XLangLexer.HexIntegerLiteral, //
                 XLangLexer.BinaryIntegerLiteral, XLangLexer.DecimalLiteral //
                    -> //
                    JavaHighlightingColors.NUMBER;
            case XLangLexer.OpenBracket, XLangLexer.CloseBracket, XLangLexer.CpExprStart -> //
                    JavaHighlightingColors.BRACKETS;
            case XLangLexer.OpenBrace, XLangLexer.CloseBrace -> //
                    JavaHighlightingColors.BRACES;
            case XLangLexer.OpenParen, XLangLexer.CloseParen -> //
                    JavaHighlightingColors.PARENTHESES;
            case XLangLexer.SemiColon -> //
                    JavaHighlightingColors.JAVA_SEMICOLON;
            case XLangLexer.Comma -> //
                    JavaHighlightingColors.COMMA;
            case XLangLexer.Dot -> //
                    JavaHighlightingColors.DOT;
            case XLangLexer.UnexpectedCharacter -> //
                    JavaHighlightingColors.INVALID_STRING_ESCAPE;
            case XLangLexer.SingleLineComment -> //
                    JavaHighlightingColors.LINE_COMMENT;
            case XLangLexer.MultiLineComment -> //
                    JavaHighlightingColors.JAVA_BLOCK_COMMENT;
            default -> null;
        };

        return attrKey != null ? new TextAttributesKey[] { attrKey } : TextAttributesKey.EMPTY_ARRAY;
    }
}
