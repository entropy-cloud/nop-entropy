package io.nop.idea.plugin.lang.script.psi;

import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class IdentifierNode extends LeafPsiElement {

    public IdentifierNode(@NotNull IElementType type, @NotNull CharSequence text) {
        super(type, text);
    }
}
