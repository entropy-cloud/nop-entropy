package io.nop.idea.plugin.lang.script;

import com.intellij.lang.ASTFactory;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import io.nop.idea.plugin.lang.script.psi.ImportAsDeclarationElement;
import io.nop.idea.plugin.lang.script.psi.ImportSourceElement;
import io.nop.xlang.parse.antlr.XLangParser;
import org.antlr.intellij.adaptor.lexer.RuleIElementType;
import org.antlr.intellij.adaptor.lexer.TokenIElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-28
 */
public class XLangScriptASTFactory extends ASTFactory {

    /** 为 AST 树的父节点创建 {@link CompositePsiElement} */
    @Override
    public CompositeElement createComposite(@NotNull IElementType type) {
        if (!(type instanceof RuleIElementType rule)) {
            return null;
        }

        return switch (rule.getRuleIndex()) {
            case XLangParser.RULE_importAsDeclaration ->   //
                    new ImportAsDeclarationElement(rule);
            case XLangParser.RULE_ast_importSource ->   //
                    new ImportSourceElement(rule);
            default -> null;
        };
    }

    /** 为 AST 树的叶子节点创建 {@link com.intellij.psi.PsiElement PsiElement} */
    @Override
    public @Nullable LeafElement createLeaf(@NotNull IElementType type, @NotNull CharSequence text) {
        if (!(type instanceof TokenIElementType token)) {
            return null;
        }

        return new LeafPsiElement(token, text);
    }
}
