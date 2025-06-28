package io.nop.idea.plugin.lang.script;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaASTFactory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import io.nop.xlang.parse.antlr.XLangLexer;
import io.nop.xlang.parse.antlr.XLangParser;
import org.antlr.intellij.adaptor.lexer.RuleIElementType;
import org.antlr.intellij.adaptor.lexer.TokenIElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-28
 */
public class XLangScriptASTFactory extends JavaASTFactory {

    @Override
    public CompositeElement createComposite(@NotNull IElementType type) {
        if (!(type instanceof RuleIElementType rule)) {
            return null;
        }

        return switch (rule.getRuleIndex()) {
            case XLangParser.RULE_moduleDeclaration_import ->   //
                    (CompositeElement) JavaStubElementTypes.IMPORT_LIST.createCompositeNode();
            case XLangParser.RULE_importAsDeclaration ->   //
                    (CompositeElement) JavaStubElementTypes.IMPORT_STATEMENT.createCompositeNode();
            case XLangParser.RULE_ast_importSource, XLangParser.RULE_qualifiedName,
                 XLangParser.RULE_qualifiedName_name_, XLangParser.RULE_identifier ->   //
                    new PsiJavaCodeReferenceElementImpl();
            default -> null;
        };
    }

    @Override
    public @Nullable LeafElement createLeaf(@NotNull IElementType type, @NotNull CharSequence text) {
        if (!(type instanceof TokenIElementType token)) {
            return null;
        }

        IElementType javaTokenType = switch (token.getANTLRTokenType()) {
            case XLangLexer.Identifier -> //
                    JavaTokenType.IDENTIFIER;
            case XLangLexer.Import ->   //
                    JavaTokenType.IMPORT_KEYWORD;
            case XLangLexer.Dot -> //
                    JavaTokenType.DOT;
            case XLangLexer.SemiColon -> //
                    JavaTokenType.SEMICOLON;
            default -> null;
        };

        return super.createLeaf(javaTokenType, text);
    }
}
