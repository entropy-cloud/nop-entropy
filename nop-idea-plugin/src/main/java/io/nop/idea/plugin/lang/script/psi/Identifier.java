package io.nop.idea.plugin.lang.script.psi;

import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * 标识符
 * <p/>
 * 一般为变量名字或导入的类名
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class Identifier extends LeafPsiElement {

    public Identifier(@NotNull IElementType type, @NotNull CharSequence text) {
        super(type, text);
    }
}
