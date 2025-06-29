package io.nop.idea.plugin.lang.script.psi;

import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-29
 */
public class ImportAsDeclarationElement extends CompositePsiElement {

    public ImportAsDeclarationElement(@NotNull IElementType type) {
        super(type);
    }
}
