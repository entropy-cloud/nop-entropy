package io.nop.idea.plugin.vfs;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import io.nop.idea.plugin.lang.reference.XLangReferenceBase;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import org.jetbrains.annotations.Nullable;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-12
 */
public class NopVirtualFileReference extends XLangReferenceBase {
    private final String path;

    /**
     * @param path
     *         可以为相对路径，最终通过 myElement 确定其绝对路径
     */
    public NopVirtualFileReference(
            PsiElement myElement, TextRange myRangeInElement, //
            String path
    ) {
        super(myElement, myRangeInElement);
        this.path = path;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        String absPath = XmlPsiHelper.getNopVfsAbsolutePath(path, myElement);

        NopVirtualFile target = new NopVirtualFile(myElement, absPath);

        if (target.hasEmptyChildren()) {
            String msg = NopPluginBundle.message("xlang.annotation.reference.vfs-file-not-found", path);
            setUnresolvedMessage(msg);

            return null;
        }
        return target;
    }
}
