/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

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
            case XLangLexer.StringLiteral, XLangLexer.TemplateStringLiteral -> //
                    JavaHighlightingColors.STRING;
            case XLangLexer.DecimalIntegerLiteral, XLangLexer.HexIntegerLiteral, //
                 XLangLexer.BinaryIntegerLiteral, XLangLexer.DecimalLiteral -> //
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
            case XLangLexer.Break, XLangLexer.Do, XLangLexer.Instanceof, //
                 XLangLexer.Typeof, XLangLexer.Case, XLangLexer.Else, //
                 XLangLexer.New, XLangLexer.Var, XLangLexer.Catch, //
                 XLangLexer.Finally, XLangLexer.Return, XLangLexer.Void, //
                 XLangLexer.Continue, XLangLexer.For, XLangLexer.Switch, //
                 XLangLexer.While, XLangLexer.Debugger, XLangLexer.Function, //
                 XLangLexer.This, XLangLexer.With, XLangLexer.Default, //
                 XLangLexer.If, XLangLexer.Throw, XLangLexer.Delete, //
                 XLangLexer.In, XLangLexer.Try, XLangLexer.As, //
                 XLangLexer.From, XLangLexer.ReadOnly, XLangLexer.Async, //
                 XLangLexer.Await, XLangLexer.Class, XLangLexer.Enum, //
                 XLangLexer.Extends, XLangLexer.Super, XLangLexer.Const, //
                 XLangLexer.Export, XLangLexer.Import, //
                 XLangLexer.Implements, XLangLexer.Let, XLangLexer.Private, //
                 XLangLexer.Public, XLangLexer.Interface, XLangLexer.Package,//
                 XLangLexer.Protected, XLangLexer.Static, //
                 XLangLexer.Any, XLangLexer.Number, XLangLexer.Boolean, //
                 XLangLexer.String, XLangLexer.Symbol, XLangLexer.TypeAlias, //
                 XLangLexer.Constructor, XLangLexer.Abstract, //
                 //
                 XLangLexer.NullLiteral, XLangLexer.BooleanLiteral, //
                 XLangLexer.AndLiteral, XLangLexer.OrLiteral -> //
                    JavaHighlightingColors.KEYWORD;
            default -> null;
        };

        return attrKey != null ? new TextAttributesKey[] { attrKey } : TextAttributesKey.EMPTY_ARRAY;
    }
}
