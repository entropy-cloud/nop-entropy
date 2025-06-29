package io.nop.idea.plugin.lang.script;

import com.intellij.lang.ASTFactory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.antlr.intellij.adaptor.lexer.TokenIElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-28
 */
public class XLangScriptASTFactory extends ASTFactory {

    // Note: 封装 CompositeElement，直接在 XLangScriptParserDefinition#createElement
    // 中创建 PsiElement，避免非必要对象的定义和创建

    /** 为 AST 树的叶子节点创建 {@link com.intellij.psi.PsiElement PsiElement} */
    @Override
    public @Nullable LeafElement createLeaf(@NotNull IElementType type, @NotNull CharSequence text) {
        if (!(type instanceof TokenIElementType token)) {
            return null;
        }

        return new LeafPsiElement(token, text);
    }
}
