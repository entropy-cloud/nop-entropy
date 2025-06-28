package io.nop.idea.plugin.lang.script;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-28
 */
public class XLangScriptFile extends PsiFileBase {

    protected XLangScriptFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, XLangScriptLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return XLangScriptFileType.INSTANCE;
    }
}
