package io.nop.idea.plugin.lang.script;

import java.util.List;

import com.intellij.psi.tree.TokenSet;
import io.nop.xlang.parse.antlr.XLangLexer;
import io.nop.xlang.parse.antlr.XLangParser;
import org.antlr.intellij.adaptor.lexer.PSIElementTypeFactory;
import org.antlr.intellij.adaptor.lexer.RuleIElementType;
import org.antlr.intellij.adaptor.lexer.TokenIElementType;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-04
 */
public class XLangScriptTokenTypes {
    static {
        PSIElementTypeFactory.defineLanguageIElementTypes(XLangScriptLanguage.INSTANCE,
                                                          XLangLexer.tokenNames,
                                                          XLangParser.ruleNames);
    }

    public static final List<TokenIElementType> TOKEN_ELEMENT_TYPES = //
            PSIElementTypeFactory.getTokenIElementTypes(XLangScriptLanguage.INSTANCE);
    public static final List<RuleIElementType> RULE_ELEMENT_TYPES = //
            PSIElementTypeFactory.getRuleIElementTypes(XLangScriptLanguage.INSTANCE);

    public static final TokenIElementType TOKEN_Identifier = token(XLangLexer.Identifier);

    public static final TokenSet TOKEN_comment = tokenSet(XLangLexer.SingleLineComment, XLangLexer.MultiLineComment);
    public static final TokenSet TOKEN_whitespace = tokenSet(XLangLexer.WhiteSpaces, XLangLexer.LineTerminator);

    public static final TokenSet TOKEN_literal_string = tokenSet(XLangLexer.StringLiteral,
                                                                 XLangLexer.TemplateStringLiteral);

    public static final TokenIElementType TOKEN_literal_boolean = token(XLangLexer.BooleanLiteral);
    public static final TokenIElementType TOKEN_literal_decimal = token(XLangLexer.DecimalLiteral);
    public static final TokenIElementType TOKEN_literal_regex = token(XLangLexer.RegularExpressionLiteral);
    public static final TokenSet TOKEN_literal_integer = tokenSet(XLangLexer.BinaryIntegerLiteral,
                                                                  XLangLexer.DecimalIntegerLiteral,
                                                                  XLangLexer.HexIntegerLiteral);

    public static final RuleIElementType RULE_ast_identifierOrPattern = rule(XLangParser.RULE_ast_identifierOrPattern);
    public static final RuleIElementType RULE_expression_initializer = rule(XLangParser.RULE_expression_initializer);
    public static final RuleIElementType RULE_moduleDeclaration_import
            = rule(XLangParser.RULE_moduleDeclaration_import);
    public static final RuleIElementType RULE_objectProperties = rule(XLangParser.RULE_objectProperties_);

    public static TokenSet tokenSet(int... types) {
        return PSIElementTypeFactory.createTokenSet(XLangScriptLanguage.INSTANCE, types);
    }

    public static TokenIElementType token(int type) {
        return TOKEN_ELEMENT_TYPES.get(type);
    }

    public static RuleIElementType rule(int type) {
        return RULE_ELEMENT_TYPES.get(type);
    }
}
